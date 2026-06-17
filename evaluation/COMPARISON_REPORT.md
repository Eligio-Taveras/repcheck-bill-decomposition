# DP-0 comparison — HAC vs Claude reference: cut at k = subjects

The cut is determined by the subject count (cut the dendrogram at k = subjectCount; the LLM reference group
count stands in for the endpoint's subject count). Compared against the tuned threshold and silhouette cuts,
under each anisotropy transform (§10b).

## Cut at k = subjects (mean over non-trivial bills)

| transform | anisotropy | ARI | NMI |
|---|---|---|---|
| none | 0.385 | 0.356 | 0.699 |
| center | 0.115 | 0.339 | 0.710 |
| standardize | 0.109 | 0.350 | 0.712 |

## For comparison — tuned threshold vs silhouette (omnibus)

| transform | anisotropy | tight dMax | tight ARI | omni dMax | omni ARI (thr) | omni NMI (thr) | omni ARI (sil) | omni NMI (sil) |
|---|---|---|---|---|---|---|---|---|
| none | 0.385 | 0.6 | 0.447 | 1.5 | 0.222 | 0.648 | 0.071 | 0.182 |
| center | 0.115 | 1.0 | 0.358 | 2.0 | 0.231 | 0.693 | 0.117 | 0.346 |
| standardize | 0.109 | 1.0 | 0.332 | 2.2 | 0.255 | 0.632 | 0.161 | 0.379 |

Anisotropy = mean pairwise cosine over a pooled sample (lower = more isotropic). thr = threshold, sil = silhouette.

## Per-bill @ standardize, cut at k = subjects (dMaxAtK = the cut height that yields k clusters)

| bill | branch | sections | subjects (k) | HAC groups | dMaxAtK | ARI | NMI |
|---|---|---|---|---|---|---|---|
| 415327 | tight | 18 | 10 | 10 | 0.96 | 0.395 | 0.829 |
| 150314 | tight | 26 | 11 | 11 | 0.90 | 0.170 | 0.669 |
| 189669 | tight | 14 | 6 | 6 | 0.82 | 0.494 | 0.767 |
| 356142 | trivial | 1 | 1 | 1 | 0.00 | 1.000 | 1.000 |
| 344387 | trivial | 2 | 1 | 1 | 1.05 | 1.000 | 1.000 |
| 330298 | trivial | 1 | 1 | 1 | 0.00 | 1.000 | 1.000 |
| 219039 | trivial | 2 | 1 | 1 | 1.08 | 1.000 | 1.000 |
| 237650 | trivial | 1 | 1 | 1 | 0.00 | 1.000 | 1.000 |
| 148391 | omnibus | 309 | 54 | 54 | 2.07 | 0.303 | 0.666 |
| 375702 | omnibus | 418 | 67 | 67 | 2.27 | 0.368 | 0.732 |
| 244276 | omnibus | 226 | 54 | 54 | 1.75 | 0.214 | 0.686 |
| 150025 | omnibus | 432 | 28 | 28 | 2.61 | 0.279 | 0.581 |
| 177012 | trivial | 1 | 1 | 1 | 0.00 | 1.000 | 1.000 |
| 402717 | trivial | 2 | 1 | 1 | 1.11 | 1.000 | 1.000 |
| 373803 | trivial | 2 | 1 | 1 | 1.04 | 1.000 | 1.000 |
| 408763 | trivial | 3 | 2 | 2 | 0.59 | 1.000 | 1.000 |
| 208332 | trivial | 2 | 2 | 2 | 0.00 | 1.000 | 1.000 |
| 357076 | tight | 40 | 7 | 7 | 1.35 | 0.347 | 0.581 |
| 267921 | trivial | 2 | 1 | 1 | 0.71 | 1.000 | 1.000 |
| 356661 | tight | 40 | 10 | 10 | 1.28 | 0.434 | 0.672 |
| 8966 | tight | 6 | 4 | 4 | 0.93 | -0.154 | 0.652 |
| 129397 | trivial | 2 | 1 | 1 | 0.40 | 1.000 | 1.000 |
| 323852 | tight | 21 | 3 | 3 | 0.85 | 1.000 | 1.000 |
| 272091 | trivial | 1 | 1 | 1 | 0.00 | 1.000 | 1.000 |
| 154389 | trivial | 1 | 1 | 1 | 0.00 | 1.000 | 1.000 |

## Structure-aware distance (standardize + cut@k): blend cosine with same-Title prior

d = alpha*cosine + (1-alpha)*structural (0 if same parser Title, else 1). alpha=1.0 is the pure-cosine baseline.

| alpha | mean ARI | mean NMI |
|---|---|---|
| 0.0 | 0.289 | 0.641 |
| 0.1 | 0.514 | 0.790 |
| 0.2 | 0.514 | 0.790 |
| 0.3 | 0.508 | 0.786 |
| 0.4 | 0.508 | 0.786 |
| 0.5 | 0.508 | 0.786 |
| 0.6 | 0.508 | 0.786 |
| 0.7 | 0.512 | 0.786 |
| 0.8 | 0.518 | 0.805 |
| 0.9 | 0.483 | 0.781 |
| 1.0 | 0.350 | 0.712 |

**Best alpha = 0.8** (alpha=1.0 = pure cosine).

### Per-bill: pure cosine (alpha=1.0) vs structure-blend (alpha=0.8)

| bill | sections | subjects (k) | ARI cosine | ARI blend | NMI blend |
|---|---|---|---|---|---|
| 415327 | 18 | 10 | 0.395 | 0.395 | 0.829 |
| 150314 | 26 | 11 | 0.170 | 0.343 | 0.763 |
| 189669 | 14 | 6 | 0.494 | 0.494 | 0.767 |
| 148391 | 309 | 54 | 0.303 | 0.286 | 0.689 |
| 375702 | 418 | 67 | 0.368 | 0.400 | 0.763 |
| 244276 | 226 | 54 | 0.214 | 0.413 | 0.767 |
| 150025 | 432 | 28 | 0.279 | 0.386 | 0.692 |
| 357076 | 40 | 7 | 0.347 | 0.915 | 0.907 |
| 356661 | 40 | 10 | 0.434 | 0.777 | 0.895 |
| 8966 | 6 | 4 | -0.154 | 0.286 | 0.786 |
| 323852 | 21 | 3 | 1.000 | 1.000 | 1.000 |

## Subjects as a GUIDE, not exact: robustness to a wrong subject count

The true count is perturbed by a factor (simulating the endpoint over/under-counting). exact = force
k = perturbed count; guided = silhouette picks k in a +/-30% window around it. standardize + structure-blend.

| count factor | exact-k ARI | guided ARI |
|---|---|---|
| 0.50 | 0.375 | 0.386 |
| 0.75 | 0.447 | 0.422 |
| 1.00 | 0.514 | 0.443 |
| 1.50 | 0.440 | 0.536 |
| 2.00 | 0.302 | 0.486 |

## Levers 1 & 2: graded hierarchy + lexical cross-reference (no LLM)

Baseline = binary top-Title blend (alpha=0.1, cut@k=subjects). **Lever 1 (graded)** uses the FULL parser
breadcrumb graded by shared-prefix depth instead of only `parents.head`. **Lever 2 (gamma)** multiplies the
distance by `(1 - gamma*lexicalSim)` so shared U.S.C./public-law/Act citations pull sections together.

| structure | gamma (lexical) | mean ARI | mean NMI |
|---|---|---|---|
| binary top-Title | 0.0 | 0.514 | 0.790 |
| binary top-Title | 0.3 | 0.468 | 0.784 |
| binary top-Title | 0.6 | 0.466 | 0.780 |
| graded | 0.0 | 0.607 | 0.841 |
| graded | 0.3 | 0.588 | 0.843 |
| graded | 0.6 | 0.584 | 0.839 |

**Winner: graded hierarchy, gamma=0.0, alpha=0.10** — mean ARI 0.607, NMI 0.841.

### Per-bill: baseline (binary, gamma=0) vs levers (winner)

| bill | sections | subjects (k) | ARI baseline | ARI levers | NMI levers |
|---|---|---|---|---|---|
| 415327 | 18 | 10 | 0.458 | 0.458 | 0.866 |
| 150314 | 26 | 11 | 0.343 | 0.343 | 0.763 |
| 189669 | 14 | 6 | 0.494 | 0.494 | 0.767 |
| 148391 | 309 | 54 | 0.286 | 0.667 | 0.892 |
| 375702 | 418 | 67 | 0.400 | 0.599 | 0.879 |
| 244276 | 226 | 54 | 0.413 | 0.511 | 0.835 |
| 150025 | 432 | 28 | 0.335 | 0.774 | 0.901 |
| 357076 | 40 | 7 | 0.942 | 0.848 | 0.902 |
| 356661 | 40 | 10 | 0.952 | 0.952 | 0.941 |
| 8966 | 6 | 4 | 0.286 | 0.286 | 0.786 |
| 323852 | 21 | 3 | 0.744 | 0.744 | 0.713 |

Mean ARI: baseline 0.514 vs levers 0.607.

## Formulaic cutoff: compute the cut from (section count n, subject count S)

The cutoff is calculated per bill from n and S — not a fixed split. On cosine the lever is the cut height dMax;
on the production blend dMax is a degenerate Title-gap selector, so the lever is the cut count k = f(S).
The k-formula below is fit on the LEVER-WINNING blend.

### standardize + cosine — cut by dMax

Fit: **dMax = 0.225 + 0.321 * ln(n - S) (R^2 = 0.835)**.

| bill | n | S | best dMax | formula dMax | ARI best | ARI formula |
|---|---|---|---|---|---|---|
| 415327 | 18 | 10 | 0.70 | 0.89 | 0.497 | 0.389 |
| 150314 | 26 | 11 | 0.80 | 1.10 | 0.244 | 0.088 |
| 189669 | 14 | 6 | 0.80 | 0.89 | 0.491 | 0.384 |
| 148391 | 309 | 54 | 2.00 | 2.01 | 0.313 | 0.305 |
| 375702 | 418 | 67 | 2.30 | 2.11 | 0.366 | 0.274 |
| 244276 | 226 | 54 | 1.70 | 1.88 | 0.234 | 0.188 |
| 150025 | 432 | 28 | 2.40 | 2.15 | 0.309 | 0.252 |
| 357076 | 40 | 7 | 1.30 | 1.35 | 0.532 | 0.328 |
| 356661 | 40 | 10 | 1.40 | 1.32 | 0.529 | 0.466 |
| 8966 | 6 | 4 | 1.00 | 0.45 | 0.189 | 0.000 |
| 323852 | 21 | 3 | 0.90 | 1.15 | 1.000 | 0.449 |

Mean ARI: oracle (per-bill best dMax) 0.428 vs formula 0.284.

### standardize + cosine, small-n floor — cut by dMax

Fit: **dMax = 0.225 + 0.321 * ln(n - S), floor 0.80 (R^2 = 0.835)**.

| bill | n | S | best dMax | formula dMax | ARI best | ARI formula |
|---|---|---|---|---|---|---|
| 415327 | 18 | 10 | 0.70 | 0.89 | 0.497 | 0.389 |
| 150314 | 26 | 11 | 0.80 | 1.10 | 0.244 | 0.088 |
| 189669 | 14 | 6 | 0.80 | 0.89 | 0.491 | 0.384 |
| 148391 | 309 | 54 | 2.00 | 2.01 | 0.313 | 0.305 |
| 375702 | 418 | 67 | 2.30 | 2.11 | 0.366 | 0.274 |
| 244276 | 226 | 54 | 1.70 | 1.88 | 0.234 | 0.188 |
| 150025 | 432 | 28 | 2.40 | 2.15 | 0.309 | 0.252 |
| 357076 | 40 | 7 | 1.30 | 1.35 | 0.532 | 0.328 |
| 356661 | 40 | 10 | 1.40 | 1.32 | 0.529 | 0.466 |
| 8966 | 6 | 4 | 1.00 | 0.80 | 0.189 | -0.098 |
| 323852 | 21 | 3 | 0.90 | 1.15 | 1.000 | 0.449 |

Mean ARI: oracle (per-bill best dMax) 0.428 vs formula 0.275.

### blend graded=true, gamma=0.0, alpha=0.10 — cut by k

Fit: **k = 0.912 * S (through origin; S<=1 => k=1) (R^2 = 0.945)**.

| bill | n | S | best k | formula k | ARI best | ARI formula |
|---|---|---|---|---|---|---|
| 415327 | 18 | 10 | 11.00 | 9.00 | 0.765 | 0.442 |
| 150314 | 26 | 11 | 4.00 | 10.00 | 0.458 | 0.396 |
| 189669 | 14 | 6 | 8.00 | 5.00 | 0.522 | 0.316 |
| 148391 | 309 | 54 | 67.00 | 49.00 | 0.677 | 0.656 |
| 375702 | 418 | 67 | 50.00 | 61.00 | 0.636 | 0.596 |
| 244276 | 226 | 54 | 43.00 | 49.00 | 0.639 | 0.539 |
| 150025 | 432 | 28 | 31.00 | 26.00 | 0.777 | 0.758 |
| 357076 | 40 | 7 | 5.00 | 6.00 | 0.910 | 0.819 |
| 356661 | 40 | 10 | 10.00 | 9.00 | 0.952 | 0.945 |
| 8966 | 6 | 4 | 4.00 | 4.00 | 0.286 | 0.286 |
| 323852 | 21 | 3 | 4.00 | 3.00 | 0.988 | 0.744 |

Mean ARI: oracle (per-bill best k) 0.692 vs formula 0.591.