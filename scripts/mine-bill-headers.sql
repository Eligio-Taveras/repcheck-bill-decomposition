-- Header-mining scan — derive the REAL section-heading vocabulary from the corpus.
--
-- Purpose: replace the GPO parser's *guessed* "SECTION/SEC." assumption with evidence.
-- Feeds: the DP gate (parser refinement + property-test corpus) and the §765 coverage metric.
--
-- WHERE THE TEXT IS (important): migration 026 dropped `content` from `bill_text_versions`
-- (now metadata-only) and moved raw text into `raw_bill_text` — CHUNKED, with naive byte-split
-- OVERLAP, ordered by `chunk_index`. Heading lines are short and survive chunk boundaries, so
-- mining over chunks is valid. (Full-document REASSEMBLY is a separate decomposition concern —
-- overlap must be de-duplicated; see plan §13 note.)
--
-- Run against the corpus DB (dev Cloud SQL / AlloyDB), e.g.:
--   psql "$REPCHECK_PG_URL" -f scripts/mine-bill-headers.sql
-- For a fast first pass on large corpora, prepend a sample CTE (see SAMPLING note at bottom).

\timing on

-- A. Leading-token frequency — discovers the heading vocabulary with zero assumptions.
--    Reveals how often SECTION/SEC/TITLE/PART/DIVISION/Resolved/Whereas/etc. actually lead lines.
SELECT lower(m[1]) AS leading_token, count(*) AS occurrences
FROM raw_bill_text r
CROSS JOIN LATERAL regexp_matches(r.content, '(?m)^[ \t]*([A-Za-z]{2,})[ \t.]', 'g') AS m
GROUP BY 1
ORDER BY occurrences DESC
LIMIT 60;

-- B. Structural-keyword tally (case-insensitive, line-start) — measures our hypotheses directly.
SELECT lower(m[1]) AS heading_keyword, count(*) AS occurrences
FROM raw_bill_text r
CROSS JOIN LATERAL regexp_matches(
  r.content,
  '(?mi)^[ \t]*(SECTION|SEC\.|TITLE|Subtitle|PART|DIVISION|CHAPTER|Article|Resolved|Whereas)\b',
  'g') AS m
GROUP BY 1
ORDER BY occurrences DESC;

-- C. Per bill_type FALLBACK RATE — % of versions whose text has NO SECTION/SEC. heading
--    (these under-segment to a single Fallback section under the current parser). The number
--    that matters for "are we sure all bills use SECTION/SEC?".
WITH ver AS (
  SELECT r.version_id,
         b.bill_type,
         bool_or(r.content ~* '(?m)^[ \t]*(SECTION|SEC\.)[ \t]') AS has_sec
  FROM raw_bill_text r
  JOIN bills b ON b.id = r.bill_id
  WHERE r.version_id IS NOT NULL
  GROUP BY r.version_id, b.bill_type
)
SELECT bill_type,
       count(*)                                         AS versions,
       count(*) FILTER (WHERE NOT has_sec)              AS no_sec_versions,
       round(100.0 * count(*) FILTER (WHERE NOT has_sec) / count(*), 1) AS pct_fallback
FROM ver
GROUP BY bill_type
ORDER BY versions DESC;

-- D. Distinct SECTION/SEC heading SHAPES (number normalized to N) + a real example + count.
--    Surfaces casing/period/spacing variants the regex must tolerate (e.g. "Sec.", no-period).
SELECT regexp_replace(m[1], '[0-9]+', 'N') AS heading_shape,
       count(*)                            AS occurrences,
       min(m[1])                           AS example
FROM raw_bill_text r
CROSS JOIN LATERAL regexp_matches(
  r.content, '(?mi)^[ \t]*((SECTION|SEC\.|Sec\.)[ \t]+[0-9A-Za-z]+\.?)', 'g') AS m
GROUP BY 1
ORDER BY occurrences DESC
LIMIT 40;

-- E. Hierarchy markers above the section level — do bills use TITLE/Subtitle/PART/DIVISION?
--    Tells us whether the parser must recognize structure above SEC (SectionKind.Title/Subtitle).
SELECT lower(m[1]) AS hierarchy_marker, count(DISTINCT r.version_id) AS versions_using
FROM raw_bill_text r
CROSS JOIN LATERAL regexp_matches(
  r.content, '(?mi)^[ \t]*(TITLE|Subtitle|PART|DIVISION|CHAPTER)[ \t]+[IVXLC0-9A-Z]', 'g') AS m
WHERE r.version_id IS NOT NULL
GROUP BY 1
ORDER BY versions_using DESC;

-- SAMPLING (large corpora): to bound cost on the full ~419K-version corpus, wrap each scan's
-- `raw_bill_text r` with a sampled source, e.g.:
--   FROM (SELECT * FROM raw_bill_text TABLESAMPLE SYSTEM (2)) r        -- ~2% of rows
-- Run sampled first to sanity-check, then full for the authoritative coverage numbers.
