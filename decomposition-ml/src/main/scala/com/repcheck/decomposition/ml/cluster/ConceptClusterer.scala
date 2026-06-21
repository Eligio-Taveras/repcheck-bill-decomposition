package com.repcheck.decomposition.ml.cluster

import com.repcheck.decomposition.ml.cluster.flat.{FlatConceptClusterer, FlatSection, FlatSectionClusterer}
import com.repcheck.decomposition.ml.embed.{EmbeddingTransform, StandardizationStats, Vector1024}

/** A concept group: section `index` values grouped together (one entry per section in the input). */
final case class Cluster(memberIndices: List[Int])

/**
 * One bill section for clustering: its position in the bill, its text (for the flat lexical models), its raw embedding,
 * and its parser breadcrumb (outermost-first). The breadcrumb drives both the flat/omnibus route (via structural
 * coverage) and the omnibus graded-hierarchy distance.
 */
final case class SectionInput(index: Int, text: String, embedding: Vector1024, parents: List[String])

/**
 * Production concept clustering (D3b): group a bill's sections into concept groups. PURE + deterministic. Routes by
 * structure — see [[RoutingConceptClusterer]].
 */
trait ConceptClusterer {

  /**
   * @param sections
   *   the bill's sections (text + raw embedding + breadcrumb), in any order (sorted by `index` internally)
   * @return
   *   one [[Cluster]] per concept group, ordered deterministically; member indices are section `index` values
   */
  def cluster(sections: Vector[SectionInput]): List[Cluster]
}

/**
 * The production surface: two validated strategies, dispatched by structural coverage (the fraction of sections
 * carrying a parser breadcrumb).
 *
 *   - FLAT bills (`coverage <= flatCoverageThreshold` — no Title/Subtitle hierarchy): the trained, deterministic
 *     logistic-regression pipeline (pairwise affinity + vetoed merge-stop) in [[FlatSectionClusterer]]. Held-out ARI
 *     ~0.41 (the LLM inter-judge ceiling). The flat clusterer owns its own embedding standardization, so raw embeddings
 *     are handed straight through.
 *   - OMNIBUS / hierarchical bills (`coverage > flatCoverageThreshold`): the graded-hierarchy HAC cut at the
 *     silhouette-optimal k in [[SmileHacClusterer]]. Held-out ARI ~0.53. Embeddings are standardized here with the
 *     global [[StandardizationStats]] (the anisotropy correction) before the cut — the footgun the global stats exist
 *     to prevent.
 *
 * Default threshold 0.0: only fully-flat bills (no hierarchy at all) take the flat path; any nesting → the omnibus
 * path.
 */
final class RoutingConceptClusterer(
  flat: FlatConceptClusterer,
  config: ClusteringConfig,
  stats: StandardizationStats,
  flatCoverageThreshold: Double,
) extends ConceptClusterer {

  def cluster(sections: Vector[SectionInput]): List[Cluster] = {
    val ordered = sections.sortBy(_.index)
    if (ordered.sizeIs < 2) ordered.map(section => Cluster(List(section.index))).toList
    else {
      val parents = ordered.map(_.parents)
      if (SmileHacClusterer.structuralCoverage(parents) <= flatCoverageThreshold)
        relabel(ordered, flat.cluster(ordered.map(s => FlatSection(s.index, s.text, s.embedding))))
      else omnibusClusters(ordered, parents)
    }
  }

  /** The omnibus path: standardize, blend with the graded hierarchy, HAC, and cut at the silhouette-optimal k. */
  private def omnibusClusters(ordered: Vector[SectionInput], parents: IndexedSeq[List[String]]): List[Cluster] = {
    val embeddings = ordered.map(s => EmbeddingTransform.standardize(s.embedding.toDoubles, stats.mean, stats.std))
    SmileHacClusterer
      .cluster(embeddings, parents, config)
      .zipWithIndex
      .groupBy { case (label, _) => label }
      .toList
      .sortBy { case (label, _) => label } // deterministic group ordering by first-appearance label
      .map { case (_, members) => Cluster(members.map { case (_, position) => ordered(position).index }.toList.sorted) }
  }

  /** Map a delegate clusterer's positional member indices back to the input section `index` values. */
  private def relabel(ordered: Vector[SectionInput], clusters: List[Cluster]): List[Cluster] =
    clusters.map(cluster => Cluster(cluster.memberIndices.map(position => ordered(position).index).sorted))

}

object RoutingConceptClusterer {

  /** Coverage at/below which a bill is treated as FLAT (→ logistic regression). 0.0: only fully-flat bills. */
  val DefaultFlatCoverageThreshold: Double = 0.0

  /** Build with the DP-0-validated production defaults + the bundled flat models and global stats. */
  def production: RoutingConceptClusterer =
    new RoutingConceptClusterer(
      FlatSectionClusterer.production,
      ClusteringConfig(),
      StandardizationStats.bundled,
      DefaultFlatCoverageThreshold,
    )

}
