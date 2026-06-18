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
 */
final case class ClusteringConfig(
  transform: String = "standardize",
  linkage: String = "average",
  distance: String = "cosine",
  structureWeight: Double = 0.9,
  gradedHierarchy: Boolean = true,
  guidedTolerance: Double = 0.3,
  minK: Int = 1,
) derives ConfigReader
