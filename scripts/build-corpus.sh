#!/usr/bin/env bash
# Build the conformance corpus fixture from the local AlloyDB (DockerRequired).
#
# The decomposition pipeline's real input is the reassembled `raw_bill_text` chunks (§13), so the conformance corpus is
# constructed the same way: for a pinned, type/size/format-diverse set of `version_id`s, this reassembles each bill's
# verbatim chunks in `chunk_index` order — identical to `ChunkReassembler` (ordered concat of contiguous 0..n-1 slices)
# — and writes one text file per bill plus a manifest. The fixture is COMMITTED; tests read it (no DB at test time).
#
# Re-run to refresh the corpus from the DB. Requires the data-ingestion local stack up:
#   docker compose -p suspicious-sammet-c8e5c2 -f <data-ingestion>/docker-compose.local.yml up -d alloydb
set -euo pipefail

CONTAINER="${ALLOYDB_CONTAINER:-suspicious-sammet-c8e5c2-alloydb-1}"
OUT="$(cd "$(dirname "$0")/.." && pwd)/conformance/src/main/resources/corpus"
psql() { docker exec "$CONTAINER" psql -U repcheck -d repcheck -tA "$@"; }

# Pinned sample (8 bill types x text/xml/pdf x small->large; see DC-1 design).
# Plus 4 mid-omnibus bills (~750K chars / ~60-80 sections each) added for DP-0: large enough to plausibly carry >15
# legislative subjects, so the production cut-switch (>15 subjects -> silhouette) can be exercised on the gold set.
IDS=(208332 357076 402717 189669 356142 415327 344387 8966 177012 150314 \
     267921 356661 373803 408763 129397 323852 330298 154389 219039 272091 237650 \
     148391 375702 244276 150025)

mkdir -p "$OUT"
IN="$(IFS=,; echo "${IDS[*]}")"

printf 'version_id\tbill_id\tbill_type\tformat_type\tchunks\tchars\tcontiguous\n' > "$OUT/manifest.tsv"
psql -F $'\t' -c "
  select r.version_id, r.bill_id, b.bill_type, btv.format_type,
         count(*), sum(length(r.content)),
         (count(*) = max(r.chunk_index) + 1 and min(r.chunk_index) = 0)
  from raw_bill_text r
  join bills b on b.id = r.bill_id
  join bill_text_versions btv on btv.id = r.version_id
  where r.version_id in ($IN)
  group by r.version_id, r.bill_id, b.bill_type, btv.format_type
  order by btv.format_type, b.bill_type, sum(length(r.content));
" >> "$OUT/manifest.tsv"

# Fail loudly if any pinned bill has a non-contiguous chunk set (a dropped/dup row) — ChunkReassembler's hard contract.
noncontig="$(awk -F'\t' 'NR>1 && $7!="t"' "$OUT/manifest.tsv")"
if [ -n "$noncontig" ]; then
  echo "ERROR: non-contiguous chunk set among the pinned corpus bills:" >&2
  echo "$noncontig" >&2
  exit 1
fi

for id in "${IDS[@]}"; do
  psql -c "select string_agg(content, '' order by chunk_index) from raw_bill_text where version_id = $id" > "$OUT/$id.txt"
done

echo "Wrote ${#IDS[@]} bills + manifest to $OUT"
wc -c "$OUT"/*.txt | tail -1
