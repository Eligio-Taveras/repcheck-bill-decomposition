package com.repcheck.decomposition.evaluation.cluster

import scala.annotation.tailrec

import com.repcheck.decomposition.evaluation.metrics.EmbeddingMetrics

/**
 * Minimal average-linkage agglomerative clustering over cosine distance (DP0-4). This is the DP-0 clustering used to
 * DRAFT gold concept groupings and to run the §10b experiments — the production `ConceptClusterer` (Smile HAC, D3b) is
 * exactly what DP-0 gates, so DP-0 cannot depend on it and rolls its own faithful HAC here. Deterministic: merges the
 * closest pair (by average inter-cluster cosine distance) until that distance exceeds `threshold`, then cuts.
 */
object AgglomerativeClusterer {

  /** cosine distance in [0, 2]; identical → 0, orthogonal → 1, opposite → 2. */
  private def distance(a: Vector[Double], b: Vector[Double]): Double = 1.0 - EmbeddingMetrics.cosine(a, b)

  /**
   * Cluster `vectors` and return a per-item cluster id (aligned by index). `threshold` is the maximum average-linkage
   * cosine distance at which two clusters still merge — larger = fewer, looser clusters. Cluster ids are assigned in
   * first-appearance order, so the labeling is stable.
   */
  def cluster(vectors: IndexedSeq[Vector[Double]], threshold: Double): Vector[Int] = {
    val merged   = merge(vectors.indices.map(Set(_)).toList, vectors, threshold)
    val labelOf  = merged.zipWithIndex.flatMap { case (members, id) => members.toList.map(_ -> id) }.toMap
    val renumber = renumberByFirstAppearance(vectors.indices.map(labelOf))
    renumber
  }

  // a and b are always non-empty (clusters start as singletons and only grow), so pairs is non-empty.
  private def averageLinkage(a: Set[Int], b: Set[Int], vectors: IndexedSeq[Vector[Double]]): Double = {
    val pairs = for { i <- a.toList; j <- b.toList } yield distance(vectors(i), vectors(j))
    pairs.sum / pairs.size
  }

  @tailrec
  private def merge(
    clusters: List[Set[Int]],
    vectors: IndexedSeq[Vector[Double]],
    threshold: Double,
  ): List[Set[Int]] =
    if (clusters.sizeIs < 2) clusters
    else {
      val candidates =
        for {
          i <- clusters.indices
          j <- clusters.indices
          if j > i
        } yield (i, j, averageLinkage(clusters(i), clusters(j), vectors))
      val closest = candidates.foldLeft(Option.empty[(Int, Int, Double)]) {
        case (None, c)                       => Some(c)
        case (Some(acc), c) if c._3 < acc._3 => Some(c)
        case (Some(acc), _)                  => Some(acc)
      }
      closest match {
        case Some((bi, bj, best)) if best <= threshold =>
          val combined = clusters(bi) ++ clusters(bj)
          val rest     = clusters.zipWithIndex.collect { case (c, k) if k != bi && k != bj => c }
          merge(combined :: rest, vectors, threshold)
        case _ => clusters
      }
    }

  private def renumberByFirstAppearance(rawLabels: IndexedSeq[Int]): Vector[Int] = {
    val order = rawLabels.distinct.zipWithIndex.toMap
    rawLabels.map(order).toVector
  }

}
