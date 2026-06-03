-- Header-mining scan — the REAL section-heading vocabulary, from the corpus (AlloyDB Omni).
-- Run:  docker exec -i <alloydb-container> psql -U repcheck -d repcheck < scripts/mine-bill-headers.sql
--
-- KEY FINDINGS (measured 2026-06-03 on 419K versions / 434K raw_bill_text chunks). These drive
-- GpoTextSectionParser; re-run after corpus growth to confirm they still hold:
--
--  * Stored text is WHITESPACE-COLLAPSED — one continuous stream, NO line breaks. So headings must
--    be matched INLINE (line-anchored `^` matching returns ~nothing).
--  * Headings are UPPERCASE (`SECTION N.`, `SEC. N.`); lowercase `section 101` is a U.S. Code
--    CITATION, not a heading. Match CASE-SENSITIVELY to exclude citations.
--  * Section numbers may carry a letter suffix: `SEC. 102B.`, … up to `…U.`.
--  * Coverage by bill_type — bills use sections, resolutions mostly do not:
--        hr 3.7% no-heading, s 3.8%   |   hres/sres ~94%, hconres 88%, sconres 84%, hjres 80%, sjres 74%
--  * format_type: Formatted Text 404,166 | PDF 15,241 | Formatted XML 2.
--    PDF rows are ~99.7% ALREADY extracted text (parse as text); only ~41 are raw PDF binary
--    (corrupted in the TEXT column → need clean bytes re-fetched + PDFBox at the reader layer).
--  * Large bills carry TITLE I/II… + Subtitle A/B… hierarchy above the section level.

\pset pager off

-- A. Heading coverage per bill_type (inline, case-sensitive). % with NO heading => fallback rate.
WITH ver AS (
  SELECT r.version_id, b.bill_type,
         bool_or(r.content ~ '(SECTION|SEC\.) +[0-9]+[A-Za-z]?\.') AS has_heading
  FROM raw_bill_text r JOIN bills b ON b.id = r.bill_id
  WHERE r.version_id IS NOT NULL
  GROUP BY r.version_id, b.bill_type
)
SELECT bill_type, count(*) AS versions,
       count(*) FILTER (WHERE NOT has_heading) AS no_heading,
       round(100.0 * count(*) FILTER (WHERE NOT has_heading) / count(*), 1) AS pct_no_heading
FROM ver GROUP BY bill_type ORDER BY versions DESC;

-- B. Inline heading SHAPES (5% sample, case-sensitive, number normalized) — variant catalog.
SELECT regexp_replace(m[1], '[0-9]+', 'N') AS shape, count(*) AS n, min(m[1]) AS example
FROM (SELECT content FROM raw_bill_text TABLESAMPLE SYSTEM (5)) r
CROSS JOIN LATERAL regexp_matches(r.content, '((SECTION|SEC\.) +[0-9]+[A-Za-z]?\.)', 'g') AS m
GROUP BY 1 ORDER BY n DESC LIMIT 25;

-- C. Hierarchy markers above section level (5% sample, structural forms only).
SELECT m[1] AS marker, count(*) AS n
FROM (SELECT content FROM raw_bill_text TABLESAMPLE SYSTEM (5)) r
CROSS JOIN LATERAL regexp_matches(
  r.content, '(TITLE [IVXLC]+|Subtitle [A-Z]|PART [IVX0-9]+|DIVISION [A-Z0-9]+)[ —-]', 'g') AS m
GROUP BY 1 ORDER BY n DESC LIMIT 15;

-- D. PDF-format reality: how many rows are raw PDF binary vs already-extracted text.
SELECT count(*) AS pdf_chunk0_rows,
       count(*) FILTER (WHERE content LIKE '%PDF%')                AS raw_pdf_binary,
       count(*) FILTER (WHERE position(chr(65533) IN content) > 0) AS corrupted
FROM raw_bill_text r JOIN bill_text_versions v ON v.id = r.version_id
WHERE v.format_type = 'PDF' AND r.chunk_index = 0;
