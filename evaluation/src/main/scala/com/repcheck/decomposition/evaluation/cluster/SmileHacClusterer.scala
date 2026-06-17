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
   * A fitted dendrogram — the expensive part (proximity matrix + linkage + Smile fit) done ONCE per bill. Cuts are
   * cheap, so a dMax sweep re-cuts the same tree instead of refitting. Built via [[fit]].
   */
  final class Fitted private[SmileHacClusterer] (
    hc: HierarchicalClustering,
    dist: Array[Array[Double]],
    val n: Int,
  ) {
    private val heights: Array[Double] = hc.height()

    /** Cluster count implied by cutting at height `dMax` (the dMax-ceiling count). */
    def kThreshold(dMax: Double): Int = n - heights.count(_ <= dMax)

    /** Partition into k clusters. k ≤ 1 → one cluster; k ≥ n → all singletons (both reject Smile's partition(int)). */
    def cut(k: Int): Vector[Int] =
      if (k <= 1) Vector.fill(n)(0)
      else if (k >= n) (0 until n).toVector
      else renumber(hc.partition(k).toIndexedSeq)

    /** Mean silhouette of the k-cluster partition (dMax-independent, so a sweep computes each k at most once). */
    def silhouetteAt(k: Int): Double = silhouette(dist, hc.partition(k).toIndexedSeq)

    /**
     * The cut height (dMax) that yields k clusters: the largest merge height retained. Lets us read off the subjects →
     * dMax mapping when we cut directly at k = subjectCount.
     */
    def heightForK(k: Int): Double =
      if (k <= 1) if (heights.isEmpty) 0.0 else heights(heights.length - 1)
      else if (k >= n) 0.0
      else heights(n - k - 1)

  }

  /** Build the dendrogram once: one proximity matrix (cloned for the linkage, which mutates in place) + Smile fit. */
  def fit(vectors: IndexedSeq[Vector[Double]], config: ClusteringConfig): Fitted = {
    val dist = proximity(vectors, config.distance)
    val hc   = HierarchicalClustering.fit(linkageOf(config.linkage, dist.map(_.clone)))
    new Fitted(hc, dist, vectors.size)
  }

  /** Fit from a precomputed (e.g. structure-blended) distance matrix. The matrix is the silhouette distance too. */
  def fitFromProximity(dist: Array[Array[Double]], linkage: String): Fitted = {
    val hc = HierarchicalClustering.fit(linkageOf(linkage, dist.map(_.clone)))
    new Fitted(hc, dist, dist.length)
  }

  /**
   * Cut the dendrogram at height `config.dMax`: sections only merge while their linkage distance ≤ `dMax`; anything
   * farther stays its own cluster (the "singletons above D_max" rule).
   */
  def clusterAtThreshold(vectors: IndexedSeq[Vector[Double]], config: ClusteringConfig): Vector[Int] =
    if (vectors.sizeIs < 2) vectors.indices.toVector
    else {
      val f = fit(vectors, config)
      f.cut(f.kThreshold(config.dMax))
    }

  /**
   * Cut at the k that maximizes the mean silhouette, but never merge across `config.dMax`: k is floored at
   * `kThreshold(dMax)`, so sections farther than dMax stay split -- the production "singletons above D_max" rule on the
   * omnibus branch too. If dMax forces more clusters than maxK allows, the dMax cut wins.
   */
  def clusterBySilhouette(vectors: IndexedSeq[Vector[Double]], config: ClusteringConfig): Vector[Int] = {
    val n = vectors.size
    if (n < 3) clusterAtThreshold(vectors, config.copy(dMax = Double.MaxValue))
    else {
      val f     = fit(vectors, config)
      val lower = math.max(math.max(2, config.minK), f.kThreshold(config.dMax))
      val upper = math.min(config.maxK, n - 1)
      if (lower > upper) f.cut(f.kThreshold(config.dMax)) // dMax forces a finer cut than maxK allows
      else {
        val bestK = (lower to upper)
          .foldLeft((Double.NegativeInfinity, lower)) {
            case ((bestS, bestK), k) =>
              val s = f.silhouetteAt(k)
              if (s > bestS) (s, k) else (bestS, bestK)
          }
          ._2
        f.cut(bestK)
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
