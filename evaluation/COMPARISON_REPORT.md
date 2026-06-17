# DP-0 comparison — HAC vs Claude reference: threshold vs silhouette, with anisotropy correction

For omnibus bills, the threshold cut (dMax tuned SEPARATELY per branch) is compared head-to-head against the
silhouette cut, under each embedding transform (§10b anisotropy correction).

## Omnibus: threshold (own dMax) vs silhouette

| transform | anisotropy | tight dMax | tight ARI | omni dMax | omni ARI (thr) | omni NMI (thr) | omni ARI (sil) | omni NMI (sil) |
|---|---|---|---|---|---|---|---|---|
| none | 0.385 | 0.6 | 0.388 | 1.5 | 0.222 | 0.648 | 0.071 | 0.182 |
| center | 0.114 | 0.8 | 0.424 | 2.1 | 0.229 | 0.662 | 0.114 | 0.295 |
| standardize | 0.108 | 0.8 | 0.417 | 2.3 | 0.252 | 0.610 | 0.172 | 0.387 |

Anisotropy = mean pairwise cosine over a pooled sample (lower = more isotropic). thr = threshold cut, sil = silhouette.

## Per-bill, threshold cut @ standardize (tight dMax=0.8, omni dMax=2.3)

| bill | branch | sections | HAC groups | ref groups | ARI | NMI |
|---|---|---|---|---|---|---|
| 415327 | tight | 18 | 13 | 10 | 0.497 | 0.894 |
| 150314 | tight | 26 | 14 | 11 | 0.264 | 0.744 |
| 189669 | tight | 14 | 7 | 6 | 0.491 | 0.767 |
| 356142 | trivial | 1 | 1 | 1 | 1.000 | 1.000 |
| 344387 | trivial | 2 | 2 | 1 | 0.000 | 0.000 |
| 330298 | trivial | 1 | 1 | 1 | 1.000 | 1.000 |
| 219039 | trivial | 2 | 2 | 1 | 0.000 | 0.000 |
| 237650 | trivial | 1 | 1 | 1 | 1.000 | 1.000 |
| 148391 | omnibus | 309 | 29 | 54 | 0.266 | 0.604 |
| 375702 | omnibus | 418 | 61 | 67 | 0.358 | 0.725 |
| 244276 | omnibus | 226 | 10 | 54 | 0.082 | 0.428 |
| 150025 | omnibus | 432 | 70 | 28 | 0.303 | 0.684 |