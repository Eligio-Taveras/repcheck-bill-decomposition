package com.repcheck.decomposition.evaluation.cluster

import smile.clustering.HierarchicalClustering
import smile.clustering.linkage.UPGMALinkage

import com.repcheck.decomposition.evaluation.metrics.EmbeddingMetrics

/**
 * Production-faithful clustering for the DP0-5 PREDICTION: Smile's hierarchical agglomerative clustering with average
 * linkage (UPGMA) over cosine distance — exactly what the production `ConceptClusterer` (D3b) will use, so the `D_max`
 * / cut config tuned here transfers literally. Two cut strategies mirror the production switch: a fixed-height cut
 * (`D_max`, for tight bills) and a silhouette-maximizing cut (for omnibus bills).
 */
object SmileHacClusterer {

  private def proximity(vectors: IndexedSeq[Vector[Double]]): Array[Array[Double]] =
    Array.tabulate(vectors.size, vectors.size)((i, j) =>
      if (i == j) 0.0 else 1.0 - EmbeddingMetrics.cosine(vectors(i), vectors(j))
    )

  /** First-appearance cluster numbering, so labels are stable regardless of Smile's internal ids. */
  private def renumber(raw: IndexedSeq[Int]): Vector[Int] = {
    val order = raw.distinct.zipWithIndex.toMap
    raw.map(order).toVector
  }

  /**
   * Cut the dendrogram at height `dMax`: sections only merge while their average-linkage cosine distance ≤ `dMax`;
   * anything farther stays its own cluster (the "singletons above D_max" rule). Derived from the merge heights (each
   * merge with height ≤ dMax reduces the cluster count by one) rather than Smile's `partition(double)`, whose cut
   * semantics differ.
   */
  def clusterAtThreshold(vectors: IndexedSeq[Vector[Double]], dMax: Double): Vector[Int] =
    if (vectors.sizeIs < 2) vectors.indices.toVector
    else {
      val hc     = HierarchicalClustering.fit(UPGMALinkage.of(proximity(vectors)))
      val merged = hc.height().count(_ <= dMax)
      val k      = vectors.size - merged
      // Smile's partition(int) rejects k < 2; k <= 1 means everything merged below dMax → one cluster.
      if (k <= 1) Vector.fill(vectors.size)(0) else renumber(hc.partition(k).toIndexedSeq)
    }

  /**
   * Cut at the number of clusters k in [2, min(kMax, n-1)] that maximizes the mean silhouette (the production omnibus
   * branch — lets the data choose the cluster count instead of a fixed height).
   */
  def clusterBySilhouette(vectors: IndexedSeq[Vector[Double]], kMax: Int): Vector[Int] = {
    val n = vectors.size
    if (n < 3) clusterAtThreshold(vectors, Double.MaxValue)
    else {
      val dist  = proximity(vectors) // kept unmutated for silhouette; UPGMALinkage.of mutates its own copy
      val hc    = HierarchicalClustering.fit(UPGMALinkage.of(proximity(vectors)))
      val upper = math.min(kMax, n - 1)
      val best = (2 to upper).foldLeft((Double.NegativeInfinity, Vector.fill(n)(0))) {
        case ((bestS, bestLabels), k) =>
          val labels = hc.partition(k).toIndexedSeq
          val s      = silhouette(dist, labels)
          if (s > bestS) (s, renumber(labels)) else (bestS, bestLabels)
      }
      best._2
    }
  }

  /** Mean silhouette coefficient of `labels` under the precomputed distance matrix `dist`. */
  private def silhouette(dist: Array[Array[Double]], labels: IndexedSeq[Int]): Double = {
    val byCluster = labels.indices.groupBy(labels)
    val scores = labels.indices.map { i =>
      val same = byCluster(labels(i)).filter(_ != i)
      if (same.isEmpty) 0.0
      else {
        val a = same.map(j => dist(i)(j)).sum / same.size
        val b = (byCluster - labels(i)).valuesIterator.foldLeft(Double.PositiveInfinity) { (acc, idxs) =>
          val mean = idxs.map(j => dist(i)(j)).sum / idxs.size
          if (mean < acc) mean else acc
        }
        val denom = math.max(a, b)
        if (denom == 0.0) 0.0 else (b - a) / denom
      }
    }
    if (scores.isEmpty) 0.0 else scores.sum / scores.size
  }

}
