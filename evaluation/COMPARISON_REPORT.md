# DP-0 comparison — HAC vs Claude reference: cut at k = subjects

The cut is determined by the subject count (cut the dendrogram at k = subjectCount; the LLM reference group
count stands in for the endpoint's subject count). Compared against the tuned threshold and silhouette cuts,
under each anisotropy transform (§10b).

## Cut at k = subjects (mean over non-trivial bills)

| transform | anisotropy | ARI | NMI |
|---|---|---|---|
| none | 0.385 | 0.296 | 0.692 |
| center | 0.114 | 0.300 | 0.704 |
| standardize | 0.108 | 0.315 | 0.705 |

## For comparison — tuned threshold vs silhouette (omnibus)

| transform | anisotropy | tight dMax | tight ARI | omni dMax | omni ARI (thr) | omni NMI (thr) | omni ARI (sil) | omni NMI (sil) |
|---|---|---|---|---|---|---|---|---|
| none | 0.385 | 0.6 | 0.388 | 1.5 | 0.222 | 0.648 | 0.071 | 0.182 |
| center | 0.114 | 0.8 | 0.424 | 2.1 | 0.229 | 0.662 | 0.114 | 0.295 |
| standardize | 0.108 | 0.8 | 0.417 | 2.3 | 0.252 | 0.610 | 0.172 | 0.387 |

Anisotropy = mean pairwise cosine over a pooled sample (lower = more isotropic). thr = threshold, sil = silhouette.

## Per-bill @ standardize, cut at k = subjects (dMaxAtK = the cut height that yields k clusters)

| bill | branch | sections | subjects (k) | HAC groups | dMaxAtK | ARI | NMI |
|---|---|---|---|---|---|---|---|
| 415327 | tight | 18 | 10 | 10 | 0.93 | 0.395 | 0.829 |
| 150314 | tight | 26 | 11 | 11 | 0.93 | 0.170 | 0.669 |
| 189669 | tight | 14 | 6 | 6 | 0.80 | 0.494 | 0.767 |
| 356142 | trivial | 1 | 1 | 1 | 0.00 | 1.000 | 1.000 |
| 344387 | trivial | 2 | 1 | 1 | 1.03 | 1.000 | 1.000 |
| 330298 | trivial | 1 | 1 | 1 | 0.00 | 1.000 | 1.000 |
| 219039 | trivial | 2 | 1 | 1 | 1.08 | 1.000 | 1.000 |
| 237650 | trivial | 1 | 1 | 1 | 0.00 | 1.000 | 1.000 |
| 148391 | omnibus | 309 | 54 | 54 | 2.09 | 0.297 | 0.664 |
| 375702 | omnibus | 418 | 67 | 67 | 2.26 | 0.364 | 0.733 |
| 244276 | omnibus | 226 | 54 | 54 | 1.75 | 0.219 | 0.692 |
| 150025 | omnibus | 432 | 28 | 28 | 2.63 | 0.264 | 0.578 |