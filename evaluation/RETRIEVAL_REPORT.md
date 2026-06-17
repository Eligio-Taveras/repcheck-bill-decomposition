# DP0-6 retrieval gate — does decomposition beat the baselines?

269 reference-concept queries over the 25-bill corpus. Each method retrieves units (ranked by
query↔unit cosine, expanded to sections); scored against the concept's own sections. Standardized embeddings.
R-precision = precision@|relevant| (the natural granularity metric).

## All queries

| method | units | MRR | P@5 | R-precision | recall@10 |
|---|---|---|---|---|---|
| Decomposition | 269 | 0.743 | 0.445 | 0.594 | 0.679 |
| B1 whole-bill | 25 | 0.799 | 0.374 | 0.518 | 0.624 |
| B2 raw-sections | 1573 | 0.864 | 0.399 | 0.543 | 0.664 |
| B3 Congress.gov subjects | 712 | 0.833 | 0.389 | 0.528 | 0.639 |

## Multi-section concepts only (210 queries — where granularity matters)

| method | units | MRR | P@5 | R-precision | recall@10 |
|---|---|---|---|---|---|
| Decomposition | 269 | 0.778 | 0.532 | 0.604 | 0.665 |
| B1 whole-bill | 25 | 0.821 | 0.432 | 0.482 | 0.561 |
| B2 raw-sections | 1573 | 0.890 | 0.461 | 0.500 | 0.599 |
| B3 Congress.gov subjects | 712 | 0.861 | 0.451 | 0.491 | 0.581 |