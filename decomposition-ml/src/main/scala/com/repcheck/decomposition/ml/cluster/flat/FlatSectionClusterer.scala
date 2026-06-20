package com.repcheck.decomposition.ml.cluster.flat

import scala.annotation.tailrec

import com.repcheck.decomposition.ml.cluster.Cluster
import com.repcheck.decomposition.ml.embed.{StandardizationStats, Vector1024}

/** One section of a flat bill: its position in the bill, its text, and its (raw) embedding. */
final case class FlatSection(index: Int, text: String, embedding: Vector1024)

trait FlatConceptClusterer {
  def cluster(sections: Vector[FlatSection]): List[Cluster]
}

/**
 * Groups a flat bill's sections into concepts with a trained, deterministic pipeline:
 *
 *   1. score every pair of sections with the affinity model ("are these the same concept?"); 2. repeatedly merge the
 *      most-similar clusters (average-linkage agglomeration); 3. but only merge when the merge-stop model endorses it —
 *      this is the cut decision, so no target cluster count is needed.
 *
 * Pure and side-effect free. On the rare oversized bill it falls back to a cheaper merge strategy so the run time stays
 * bounded.
 */
final class FlatSectionClusterer(
  artifacts: FlatGroupingArtifacts,
  embeddingStats: StandardizationStats,
  config: FlatGroupingConfig,
) extends FlatConceptClusterer {

  import FlatSectionClusterer.ClusterPair

  def cluster(sections: Vector[FlatSection]): List[Cluster] = {
    val sectionCount = sections.size
    if (sectionCount == 0) Nil
    else if (sectionCount == 1) List(Cluster(List(0)))
    else {
      val context           = SectionContext.build(sections, artifacts, embeddingStats)
      val singletonClusters = Vector.tabulate(sectionCount)(sectionIndex => List(sectionIndex))
      val groupedClusters =
        if (sectionCount > config.maxVetoedSections) sequentialStop(context, singletonClusters)
        else vetoedAgglomeration(context, singletonClusters)
      groupedClusters.iterator.map(memberIndices => Cluster(memberIndices.sorted)).toList
    }
  }

  /**
   * Keep merging the most-similar cluster pair the merge-stop model still endorses, skipping pairs it rejects, until no
   * pair is endorsed. "Vetoed" = a very-similar pair can be vetoed (because it would fuse two distinct concepts) so a
   * slightly-less-similar but correct merge happens instead.
   */
  @tailrec
  private def vetoedAgglomeration(context: SectionContext, clusters: Vector[List[Int]]): Vector[List[Int]] =
    if (clusters.sizeIs <= 1) clusters
    else {
      val rankedPairs = sortedClusterPairs(context, clusters)
      val endorsedMerge = rankedPairs.indices.iterator.collectFirst {
        case rank if endorsesMerge(context, clusters, rankedPairs, rank) => rankedPairs(rank)
      }
      endorsedMerge match {
        case Some(pair) => vetoedAgglomeration(context, mergeClusters(clusters, pair.leftIndex, pair.rightIndex))
        case None       => clusters
      }
    }

  /** Cheaper fallback for oversized bills: merge in greedy order, stop at the first rejected merge. */
  @tailrec
  private def sequentialStop(context: SectionContext, clusters: Vector[List[Int]]): Vector[List[Int]] =
    if (clusters.sizeIs <= 1) clusters
    else {
      // clusters.size > 1 guarantees sortedClusterPairs is non-empty, so rankedPairs(0) is the most-similar pair.
      val rankedPairs = sortedClusterPairs(context, clusters)
      if (!endorsesMerge(context, clusters, rankedPairs, 0)) clusters
      else sequentialStop(context, mergeClusters(clusters, rankedPairs(0).leftIndex, rankedPairs(0).rightIndex))
    }

  /** All current cluster pairs scored by average-linkage affinity, most-similar first (stable ties). */
  private def sortedClusterPairs(context: SectionContext, clusters: Vector[List[Int]]): Vector[ClusterPair] = {
    val pairs = for {
      leftIndex  <- clusters.indices.toVector
      rightIndex <- (leftIndex + 1) until clusters.size
    } yield ClusterPair(
      ClusterMath.meanCrossValue(context.affinityMatrix, clusters(leftIndex), clusters(rightIndex)),
      leftIndex,
      rightIndex,
    )
    // Descending by affinity, then by indices — reproduces the reference tie-break exactly.
    pairs.sortWith { (a, b) =>
      if (a.affinity != b.affinity) a.affinity > b.affinity
      else if (a.leftIndex != b.leftIndex) a.leftIndex > b.leftIndex
      else a.rightIndex > b.rightIndex
    }
  }

  private def endorsesMerge(
    context: SectionContext,
    clusters: Vector[List[Int]],
    rankedPairs: Vector[ClusterPair],
    rank: Int,
  ): Boolean =
    artifacts.mergeStopModel.predict(
      mergeFeatures(context, clusters, rankedPairs, rank)
    ) >= artifacts.mergeStopThreshold

  private def mergeFeatures(
    context: SectionContext,
    clusters: Vector[List[Int]],
    rankedPairs: Vector[ClusterPair],
    rank: Int,
  ): IndexedSeq[Double] = {
    // `rank` is always a valid index into `rankedPairs` — callers pass a rank from `rankedPairs.indices`
    // (vetoed) or 0 over a non-empty list (sequential), so this and the `rankedPairs(0/1)` reads are total.
    val candidate = rankedPairs(rank)
    // The best alternative merge available right now; a big gap means this merge clearly stands out.
    val nextBestAffinity =
      if (rank > 0) rankedPairs(0).affinity
      else if (rankedPairs.sizeIs > 1) rankedPairs(1).affinity
      else 0.0
    MergeFeatures.describeMerge(
      context,
      clusters(candidate.leftIndex),
      clusters(candidate.rightIndex),
      nextBestAffinity,
    )
  }

  /** Remove the two merged clusters and append their union last (order matches the reference). */
  private def mergeClusters(clusters: Vector[List[Int]], leftIndex: Int, rightIndex: Int): Vector[List[Int]] = {
    val retained = clusters.indices.filterNot(index => index == leftIndex || index == rightIndex).map(clusters).toVector
    retained :+ (clusters(leftIndex) ++ clusters(rightIndex))
  }

}

object FlatSectionClusterer {

  def production: FlatSectionClusterer =
    new FlatSectionClusterer(FlatGroupingArtifacts.bundled, StandardizationStats.bundled, FlatGroupingConfig())

  final private case class ClusterPair(affinity: Double, leftIndex: Int, rightIndex: Int)
}
