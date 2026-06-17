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

## Structure-aware distance (standardize + cut@k): blend cosine with same-Title prior

d = alpha*cosine + (1-alpha)*structural (0 if same parser Title, else 1). alpha=1.0 is the pure-cosine baseline.

| alpha | mean ARI | mean NMI |
|---|---|---|
| 0.0 | 0.160 | 0.623 |
| 0.1 | 0.392 | 0.758 |
| 0.2 | 0.392 | 0.758 |
| 0.3 | 0.383 | 0.752 |
| 0.4 | 0.383 | 0.752 |
| 0.5 | 0.383 | 0.752 |
| 0.6 | 0.383 | 0.752 |
| 0.7 | 0.383 | 0.753 |
| 0.8 | 0.383 | 0.753 |
| 0.9 | 0.380 | 0.749 |
| 1.0 | 0.315 | 0.705 |

**Best alpha = 0.1** (alpha=1.0 = pure cosine).

### Per-bill: pure cosine (alpha=1.0) vs structure-blend (alpha=0.1)

| bill | sections | subjects (k) | ARI cosine | ARI blend | NMI blend |
|---|---|---|---|---|---|
| 415327 | 18 | 10 | 0.395 | 0.458 | 0.866 |
| 150314 | 26 | 11 | 0.170 | 0.343 | 0.763 |
| 189669 | 14 | 6 | 0.494 | 0.494 | 0.767 |
| 148391 | 309 | 54 | 0.297 | 0.285 | 0.692 |
| 375702 | 418 | 67 | 0.364 | 0.413 | 0.765 |
| 244276 | 226 | 54 | 0.219 | 0.406 | 0.763 |
| 150025 | 432 | 28 | 0.264 | 0.347 | 0.689 |

## Subjects as a GUIDE, not exact: robustness to a wrong subject count

The true count is perturbed by a factor (simulating the endpoint over/under-counting). exact = force
k = perturbed count; guided = silhouette picks k in a +/-30% window around it. standardize + structure-blend.

| count factor | exact-k ARI | guided ARI |
|---|---|---|
| 0.50 | 0.251 | 0.240 |
| 0.75 | 0.356 | 0.293 |
| 1.00 | 0.392 | 0.334 |
| 1.50 | 0.404 | 0.440 |
| 2.00 | 0.219 | 0.386 |

## Formulaic cutoff: compute the cut from (section count n, subject count S)

The cutoff is calculated per bill from n and S — not a fixed split. On cosine the lever is the cut height dMax;
on the production blend dMax is a degenerate Title-gap selector, so the lever is the cut count k = f(S).

### standardize + cosine — cut by dMax

Fit: **dMax = -0.181 + 0.410 * ln(n - S) (R^2 = 0.969)**.

| bill | n | S | best dMax | formula dMax | ARI best | ARI formula |
|---|---|---|---|---|---|---|
| 415327 | 18 | 10 | 0.70 | 0.67 | 0.497 | 0.497 |
| 150314 | 26 | 11 | 0.80 | 0.93 | 0.264 | 0.178 |
| 189669 | 14 | 6 | 0.80 | 0.67 | 0.491 | 0.204 |
| 148391 | 309 | 54 | 2.10 | 2.09 | 0.297 | 0.297 |
| 375702 | 418 | 67 | 2.30 | 2.22 | 0.358 | 0.350 |
| 244276 | 226 | 54 | 1.70 | 1.93 | 0.229 | 0.187 |
| 150025 | 432 | 28 | 2.40 | 2.28 | 0.315 | 0.302 |

Mean ARI: oracle (per-bill best dMax) 0.350 vs formula 0.288.

### standardize + cosine, small-n floor — cut by dMax

Fit: **dMax = -0.181 + 0.410 * ln(n - S), floor 0.80 (R^2 = 0.969)**.

| bill | n | S | best dMax | formula dMax | ARI best | ARI formula |
|---|---|---|---|---|---|---|
| 415327 | 18 | 10 | 0.70 | 0.80 | 0.497 | 0.497 |
| 150314 | 26 | 11 | 0.80 | 0.93 | 0.264 | 0.178 |
| 189669 | 14 | 6 | 0.80 | 0.80 | 0.491 | 0.491 |
| 148391 | 309 | 54 | 2.10 | 2.09 | 0.297 | 0.297 |
| 375702 | 418 | 67 | 2.30 | 2.22 | 0.358 | 0.350 |
| 244276 | 226 | 54 | 1.70 | 1.93 | 0.229 | 0.187 |
| 150025 | 432 | 28 | 2.40 | 2.28 | 0.315 | 0.302 |

Mean ARI: oracle (per-bill best dMax) 0.350 vs formula 0.329.

### standardize + structure-blend (alpha=0.1) — cut by dMax

Fit: **dMax = 1.525 + -0.217 * ln(n - S) (R^2 = 0.320)**.

| bill | n | S | best dMax | formula dMax | ARI best | ARI formula |
|---|---|---|---|---|---|---|
| 415327 | 18 | 10 | 1.30 | 1.07 | 0.144 | 0.000 |
| 150314 | 26 | 11 | 1.90 | 0.94 | 0.458 | 0.415 |
| 189669 | 14 | 6 | 0.10 | 1.07 | 0.000 | 0.000 |
| 148391 | 309 | 54 | 0.20 | 0.32 | 0.365 | 0.149 |
| 375702 | 418 | 67 | 0.30 | 0.25 | 0.378 | 0.422 |
| 244276 | 226 | 54 | 0.20 | 0.41 | 0.402 | 0.347 |
| 150025 | 432 | 28 | 0.30 | 0.22 | 0.350 | 0.290 |

Mean ARI: oracle (per-bill best dMax) 0.300 vs formula 0.232.

### standardize + structure-blend (alpha=0.1) — cut by k

Fit: **k = 1.089 * S (through origin; S<=1 => k=1) (R^2 = 0.947)**.

| bill | n | S | best k | formula k | ARI best | ARI formula |
|---|---|---|---|---|---|---|
| 415327 | 18 | 10 | 11.00 | 11.00 | 0.765 | 0.765 |
| 150314 | 26 | 11 | 4.00 | 12.00 | 0.458 | 0.316 |
| 189669 | 14 | 6 | 9.00 | 7.00 | 0.591 | 0.491 |
| 148391 | 309 | 54 | 75.00 | 59.00 | 0.385 | 0.342 |
| 375702 | 418 | 67 | 56.00 | 73.00 | 0.432 | 0.382 |
| 244276 | 226 | 54 | 59.00 | 59.00 | 0.426 | 0.426 |
| 150025 | 432 | 28 | 42.00 | 30.00 | 0.401 | 0.332 |

Mean ARI: oracle (per-bill best k) 0.494 vs formula 0.436.