package com.repcheck.decomposition.ml.cluster

import com.repcheck.decomposition.ml.embed.{EmbeddingTransform, StandardizationStats, Vector1024}

/** A concept group: indices into the input section-vector list (the clustering granularity — one entry per section). */
final case class Cluster(memberIndices: List[Int])

/**
 * Production concept clustering (D3b): group a bill's section embeddings into concept groups. PURE + deterministic.
 *
 * The DP-0-validated pipeline (standardize upstream → distance = 0.1·cosine + 0.9·graded-hierarchy → HAC average
 * linkage → cut at the subject count, guided by silhouette in a window; `subjectCount <= 1` → one group) lives in
 * [[SmileHacClusterer.cluster]]; this trait is the production-facing surface driven by the validated
 * [[ClusteringConfig]].
 */
trait ConceptClusterer {

  /**
   * @param vectors
   *   one already-standardized 1024-dim embedding per section, in document order
   * @param parents
   *   each section's parser breadcrumb (outermost-first), aligned with `vectors` — drives the graded-hierarchy distance
   * @param subjectCount
   *   the Congress.gov legislative-subject count — the cut GUIDE (silhouette picks k within a window around it)
   * @return
   *   one [[Cluster]] per concept group, ordered deterministically; member indices reference positions in `vectors`
   */
  def cluster(vectors: Vector[Vector1024], parents: Vector[List[String]], subjectCount: Int): List[Cluster]
}

/**
 * Smile HAC implementation over the consolidated [[SmileHacClusterer.cluster]]. Owns the FULL validated transform: it
 * standardizes each section vector with the global [[StandardizationStats]] (the anisotropy correction) before the cut.
 * Callers pass raw 1024-dim embeddings — they cannot forget the standardization step, which is the footgun the global
 * stats exist to prevent.
 */
final class HacConceptClusterer(config: ClusteringConfig, stats: StandardizationStats) extends ConceptClusterer {

  def cluster(vectors: Vector[Vector1024], parents: Vector[List[String]], subjectCount: Int): List[Cluster] = {
    val embeddings = vectors.map(v => EmbeddingTransform.standardize(v.toDoubles, stats.mean, stats.std))
    SmileHacClusterer
      .cluster(embeddings, parents, subjectCount, config)
      .zipWithIndex
      .groupBy(_._1)
      .toList
      .sortBy(_._1) // deterministic group ordering by first-appearance label
      .map { case (_, members) => Cluster(members.map(_._2).toList) }
  }

}

object HacConceptClusterer {

  /** Build with the DP-0-validated production defaults ([[ClusteringConfig]]) + the bundled global stats. */
  def production: HacConceptClusterer = new HacConceptClusterer(ClusteringConfig(), StandardizationStats.bundled)

}
