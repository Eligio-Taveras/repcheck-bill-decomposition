-- Export a stratified real-bill corpus for LiveCorpusValidationSpec (E2E).
--
-- Pulls ~28 bills spanning all 8 measure types and all 3 format_types, reassembles each version's text from
-- raw_bill_text chunks (in chunk_index order), and base64-encodes it (newlines stripped) so the text survives a
-- one-line-per-bill TSV faithfully. Columns: version_id, bill_type, format_type, base64(text).
--
-- Run against the live AlloyDB and write the TSV the spec reads (VALIDATION_TSV, default C:/Temp/validation-corpus.tsv):
--   docker exec -i <alloydb-container> psql -U repcheck -d repcheck -A -t -F $'\t' \
--     -f /dev/stdin < scripts/export-validation-corpus.sql > C:/Temp/validation-corpus.tsv
-- then:  sbt "textStructure/testOnly -- -n com.repcheck.tags.E2ETest"

WITH picked AS (
  SELECT r.version_id, b.bill_type, v.format_type,
         row_number() OVER (PARTITION BY b.bill_type, v.format_type ORDER BY r.version_id) AS rn
  FROM (SELECT DISTINCT version_id, bill_id FROM raw_bill_text WHERE version_id IS NOT NULL) r
  JOIN bill_text_versions v ON v.id = r.version_id
  JOIN bills b ON b.id = r.bill_id
),
sample AS (
  SELECT version_id, bill_type, format_type FROM picked
  WHERE (format_type = 'Formatted Text' AND (
            (bill_type IN ('hr','s') AND rn <= 3) OR
            (bill_type IN ('hjres','sjres','hconres','sconres','hres','sres') AND rn <= 2)))
     OR (format_type = 'PDF' AND (
            (bill_type IN ('hr','s') AND rn <= 3) OR
            (bill_type IN ('hjres','sjres') AND rn <= 1)))
     OR (format_type = 'Formatted XML' AND rn <= 2)
)
SELECT s.version_id, s.bill_type, s.format_type,
       replace(encode(convert_to(string_agg(r.content, '' ORDER BY r.chunk_index), 'UTF8'), 'base64'), E'\n', '') AS b64
FROM sample s
JOIN raw_bill_text r ON r.version_id = s.version_id
GROUP BY s.version_id, s.bill_type, s.format_type
ORDER BY s.format_type, s.bill_type, s.version_id;
