package com.repcheck.decomposition.evaluation.cluster

import pureconfig.ConfigReader

/**
 * Tunable knobs for [[SmileHacClusterer]] — config, not hardcoded, so DP0-5 can sweep them and the winning values carry
 * over to the production `ConceptClusterer` (D3b). Defaults are starting points; the real values come from the DP0-5
 * tuning against the gold.
 *
 * @param dMax
 *   threshold-cut height (Smile UPGMA units) for the tight-bill branch
 * @param minK
 *   silhouette search lower bound (clusters)
 * @param maxK
 *   silhouette search upper bound (clusters) for the omnibus branch
 * @param linkage
 *   agglomerative linkage: "average" (UPGMA — production), "complete", "single", or "ward"
 * @param distance
 *   pairwise distance metric: "cosine" (D6/D10 production default) or "euclidean"
 */
final case class ClusteringConfig(
  dMax: Double = 0.9,
  minK: Int = 2,
  maxK: Int = 50,
  linkage: String = "average",
  distance: String = "cosine",
) derives ConfigReader
