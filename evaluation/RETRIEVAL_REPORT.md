# DP0-6 retrieval gate — does decomposition beat the baselines?

269 reference-concept queries over the 25-bill corpus. Each method retrieves units, expanded to
sections, scored against the concept's own sections. Standardized embeddings. R-precision = precision@|relevant|.
**mean-pool** ranks units by their centroid cosine; **max-pool** ranks by the unit's best single section (then
still returns the whole unit) — pinpoints like raw sections without losing coverage.

## mean-pool — unit ranking

### All queries (269)

| method | units | MRR | P@5 | R-precision | recall@10 |
|---|---|---|---|---|---|
| Decomposition | 269 | 0.743 | 0.445 | 0.594 | 0.679 |
| B1 whole-bill | 25 | 0.799 | 0.374 | 0.518 | 0.624 |
| B2 raw-sections | 1573 | 0.864 | 0.399 | 0.543 | 0.664 |
| B3 Congress.gov subjects | 712 | 0.833 | 0.389 | 0.528 | 0.639 |

### Multi-section concepts only (210)

| method | units | MRR | P@5 | R-precision | recall@10 |
|---|---|---|---|---|---|
| Decomposition | 269 | 0.778 | 0.532 | 0.604 | 0.665 |
| B1 whole-bill | 25 | 0.821 | 0.432 | 0.482 | 0.561 |
| B2 raw-sections | 1573 | 0.890 | 0.461 | 0.500 | 0.599 |
| B3 Congress.gov subjects | 712 | 0.861 | 0.451 | 0.491 | 0.581 |

## max-pool — unit ranking

### All queries (269)

| method | units | MRR | P@5 | R-precision | recall@10 |
|---|---|---|---|---|---|
| Decomposition | 269 | 0.833 | 0.487 | 0.671 | 0.738 |
| B1 whole-bill | 25 | 0.847 | 0.396 | 0.551 | 0.641 |
| B2 raw-sections | 1573 | 0.864 | 0.399 | 0.543 | 0.664 |
| B3 Congress.gov subjects | 712 | 0.851 | 0.399 | 0.544 | 0.653 |

### Multi-section concepts only (210)

| method | units | MRR | P@5 | R-precision | recall@10 |
|---|---|---|---|---|---|
| Decomposition | 269 | 0.860 | 0.580 | 0.665 | 0.717 |
| B1 whole-bill | 25 | 0.873 | 0.460 | 0.511 | 0.583 |
| B2 raw-sections | 1573 | 0.890 | 0.461 | 0.500 | 0.599 |
| B3 Congress.gov subjects | 712 | 0.880 | 0.463 | 0.507 | 0.593 |