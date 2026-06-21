package com.repcheck.decomposition.ml.cluster

import pureconfig.ConfigReader

/**
 * Production clustering config for the OMNIBUS path ([[SmileHacClusterer.cluster]]). These are the **DP-0-validated**
 * values (not starting points): discovered by tuning [[SmileHacClusterer]] against the gold (DP0-5b; DP0-6 retrieval
 * gate). FLAT bills are clustered by the logistic-regression `FlatSectionClusterer` and do not use this config.
 *
 * Pipeline:
 *   1. standardize the section embeddings (anisotropy 0.385 → 0.11) — applied UPSTREAM with corpus/global stats, since
 *      a single bill has no pooled statistics; `transform` records the requirement. 2. distance = `(1 -
 *      structureWeight) * cosine + structureWeight * graded-hierarchy` — the graded term uses the FULL parser
 *      breadcrumb graded by shared-prefix depth (the single biggest non-LLM lever; lexical blending was net negative
 *      and is intentionally absent). 3. HAC with `linkage`. 4. cut at the silhouette-optimal k
 *      ([[SmileHacClusterer.silhouetteCut]]) — no external count; the silhouette finds the cut from the structure.
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
 * @param adaptiveStructure
 *   scale the effective `structureWeight` by the bill's structural COVERAGE (fraction of sections with a non-empty
 *   parser breadcrumb). On the omnibus path coverage is > 0 by construction; this keeps the blend well-behaved on
 *   partially-nested bills (it shifts weight off the constant structural term toward cosine as coverage drops).
 */
final case class ClusteringConfig(
  transform: String = "standardize",
  linkage: String = "average",
  distance: String = "cosine",
  structureWeight: Double = 0.9,
  gradedHierarchy: Boolean = true,
  adaptiveStructure: Boolean = true,
) derives ConfigReader
