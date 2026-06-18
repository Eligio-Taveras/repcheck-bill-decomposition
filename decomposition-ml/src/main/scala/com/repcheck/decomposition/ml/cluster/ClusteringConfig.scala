package com.repcheck.decomposition.ml.cluster

import pureconfig.ConfigReader

/**
 * Production clustering config for the D3b `ConceptClusterer`. These are the **DP-0-validated** values (not starting
 * points): discovered by tuning [[SmileHacClusterer]] against the 25-bill gold (DP0-5b mean ARI 0.607 vs the Claude
 * reference; DP0-6 retrieval gate — decomposition beats the whole-bill / raw-section / Congress.gov-subject baselines).
 *
 * Production pipeline ([[SmileHacClusterer.cluster]]):
 *   1. standardize the section embeddings (anisotropy 0.385 → 0.11) — applied UPSTREAM with corpus/global stats, since
 *      a single bill has no pooled statistics; `transform` records the requirement. 2. distance = `(1 -
 *      structureWeight) * cosine + structureWeight * graded-hierarchy` — the graded term uses the FULL parser
 *      breadcrumb graded by shared-prefix depth (the single biggest non-LLM lever; lexical blending was net negative
 *      and is intentionally absent). 3. HAC with `linkage`. 4. cut at `k = subjectCount`, guided by silhouette within
 *      ±`guidedTolerance` (the count is a GUIDE, not an exact split — robust to the endpoint over/under-counting), with
 *      the single-concept guard `subjectCount <= minK`.
 *
 * @param transform
 *   embedding pre-transform applied upstream: "standardize" (anisotropy correction, production) or "none"
 * @param linkage
 *   agglomerative linkage: "average" (UPGMA — production), "complete", "single", or "ward"
 * @param distance
 *   base pairwise section metric: "cosine" (production) or "euclidean"
 * @param structureWeight
 *   weight on the graded-hierarchy term (= 1 − α); 0.9 means α = 0.1 cosine (the DP0-5b winner)
 * @param gradedHierarchy
 *   true (production): full parser breadcrumb graded by shared-prefix depth; false: top-level Title binary
 * @param guidedTolerance
 *   silhouette searches k within ±this fraction of the subject count (0.3 = ±30%)
 * @param minK
 *   single-concept guard: `subjectCount <= minK` collapses to one group (a single-subject bill must not be decomposed)
 * @param adaptiveStructure
 *   scale the effective `structureWeight` by the bill's structural COVERAGE (fraction of sections with a non-empty
 *   parser breadcrumb). A FLAT bill (short resolution / simple act — no Title/Subtitle hierarchy) carries no usable
 *   structural signal, so the graded-hierarchy term is inert; this shifts its weight back onto cosine instead of
 *   leaving 90% of the blend on a constant — which lets the silhouette-guided cut pick a better k. Hierarchical bills
 *   (full coverage) are unaffected. Default true: on the 25-bill gold it adds +0.064 mean ARI (driven by flat bills
 *   323852 +0.53, 415327 +0.18) with zero regression on hierarchical bills (max |Δ| 0.002).
 * @param adaptiveCut
 *   on a FLAT bill (coverage ≤ `flatCutCoverage`) cut at exactly `subjectCount` instead of the silhouette-guided
 *   window. On flat/cosine-only bills the silhouette decreases monotonically with k, so the guided search
 *   systematically under-segments (grabs the smallest k in the window); the subject count is the better cut. Oracle
 *   sweep: the best-ARI k sits inside the window but the silhouette misses it. Hierarchical bills (coverage >
 *   threshold) keep the guided cut. Default true: on the gold it adds +0.031 mean ARI on non-trivial bills (+0.086 on
 *   the 4 flat bills: 189669 +0.22, 8966 +0.10) with zero regression on trivial/hierarchical bills. Trades the guided
 *   window's robustness to a wrong subject count for a better cut on flat bills (where the silhouette is unreliable
 *   anyway).
 * @param flatCutCoverage
 *   the structural-coverage threshold at/below which `adaptiveCut` treats a bill as flat. Default 0.0 (only FULLY flat
 *   bills — the validated case; mid-coverage cut behavior is not yet gold-validated).
 */
final case class ClusteringConfig(
  transform: String = "standardize",
  linkage: String = "average",
  distance: String = "cosine",
  structureWeight: Double = 0.9,
  gradedHierarchy: Boolean = true,
  guidedTolerance: Double = 0.3,
  minK: Int = 1,
  adaptiveStructure: Boolean = true,
  adaptiveCut: Boolean = true,
  flatCutCoverage: Double = 0.0,
) derives ConfigReader
