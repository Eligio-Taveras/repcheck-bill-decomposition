package com.repcheck.decomposition.evaluation.cluster

import smile.clustering.HierarchicalClustering
import smile.clustering.linkage.{CompleteLinkage, Linkage, SingleLinkage, UPGMALinkage, WardLinkage}

import com.repcheck.decomposition.evaluation.metrics.EmbeddingMetrics

/**
 * Production-faithful clustering for the DP0-5 PREDICTION: Smile's hierarchical agglomerative clustering over cosine
 * distance — exactly what the production `ConceptClusterer` (D3b) will use, so the [[ClusteringConfig]] tuned here
 * transfers literally. Every knob (linkage, `D_max`, the silhouette k-range) comes from [[ClusteringConfig]]; nothing
 * is hardcoded. Two cut strategies mirror the production switch: a fixed-height cut (`D_max`, tight bills) and a
 * silhouette-maximizing cut (omnibus bills).
 */
object SmileHacClusterer {

  private def pointDistance(name: String, a: Vector[Double], b: Vector[Double]): Double =
    name.toLowerCase match {
      case "euclidean" => math.sqrt(a.lazyZip(b).map((x, y) => (x - y) * (x - y)).sum)
      case _           => 1.0 - EmbeddingMetrics.cosine(a, b) // cosine distance — D6/D10 default
    }

  private def proximity(vectors: IndexedSeq[Vector[Double]], distance: String): Array[Array[Double]] =
    Array.tabulate(vectors.size, vectors.size)((i, j) =>
      if (i == j) 0.0 else pointDistance(distance, vectors(i), vectors(j))
    )

  private def linkageOf(name: String, prox: Array[Array[Double]]): Linkage =
    name.toLowerCase match {
      case "complete" => CompleteLinkage.of(prox)
      case "single"   => SingleLinkage.of(prox)
      case "ward"     => WardLinkage.of(prox)
      case _          => UPGMALinkage.of(prox) // average (UPGMA) — the production default
    }

  /** First-appearance cluster numbering, so labels are stable regardless of Smile's internal ids. */
  private def renumber(raw: IndexedSeq[Int]): Vector[Int] = {
    val order = raw.distinct.zipWithIndex.toMap
    raw.map(order).toVector
  }

  /**
   * Cut the dendrogram at height `config.dMax`: sections only merge while their linkage cosine distance ≤ `dMax`;
   * anything farther stays its own cluster (the "singletons above D_max" rule). Derived from the merge heights (each
   * merge with height ≤ dMax reduces the cluster count by one) rather than Smile's `partition(double)`, whose cut
   * semantics differ.
   */
  def clusterAtThreshold(vectors: IndexedSeq[Vector[Double]], config: ClusteringConfig): Vector[Int] =
    if (vectors.sizeIs < 2) vectors.indices.toVector
    else {
      val hc     = HierarchicalClustering.fit(linkageOf(config.linkage, proximity(vectors, config.distance)))
      val merged = hc.height().count(_ <= config.dMax)
      val k      = vectors.size - merged
      // Smile's partition(int) rejects k < 2 and k = n; k <= 1 → one cluster, k >= n → all singletons.
      if (k <= 1) Vector.fill(vectors.size)(0)
      else if (k >= vectors.size) vectors.indices.toVector
      else renumber(hc.partition(k).toIndexedSeq)
    }

  /**
   * Cut at the k that maximizes the mean silhouette, but never merge across `config.dMax`: k is floored at `kThresh`
   * (the cluster count the dMax cut implies, from the same dendrogram heights), so sections farther than dMax stay
   * split -- the production "singletons above D_max" rule applied to the omnibus branch too. If dMax forces more
   * clusters than maxK allows, the dMax cut wins.
   */
  def clusterBySilhouette(vectors: IndexedSeq[Vector[Double]], config: ClusteringConfig): Vector[Int] = {
    val n = vectors.size
    if (n < 3) clusterAtThreshold(vectors, config.copy(dMax = Double.MaxValue))
    else {
      val dist = proximity(vectors, config.distance) // unmutated for silhouette; linkage `.of` mutates its own copy
      val hc   = HierarchicalClustering.fit(linkageOf(config.linkage, proximity(vectors, config.distance)))
      val kThresh = vectors.size - hc.height().count(_ <= config.dMax) // dMax ceiling -> minimum cluster count
      val lower   = math.max(math.max(2, config.minK), kThresh)
      val upper   = math.min(config.maxK, n - 1)
      if (lower > upper) clusterAtThreshold(vectors, config) // dMax forces a finer cut than maxK allows
      else {
        val best = (lower to upper).foldLeft((Double.NegativeInfinity, Vector.fill(n)(0))) {
          case ((bestS, bestLabels), k) =>
            val labels = hc.partition(k).toIndexedSeq
            val s      = silhouette(dist, labels)
            if (s > bestS) (s, renumber(labels)) else (bestS, bestLabels)
        }
        best._2
      }
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
