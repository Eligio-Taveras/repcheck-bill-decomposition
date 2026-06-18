package com.repcheck.decomposition.ml.cluster

import smile.clustering.HierarchicalClustering
import smile.clustering.linkage.{CompleteLinkage, Linkage, SingleLinkage, UPGMALinkage, WardLinkage}

import com.repcheck.decomposition.ml.metrics.EmbeddingMetrics

/**
 * Production-faithful clustering for bill decomposition (D3b's `ConceptClusterer`). The DP-0-validated pipeline lives
 * in [[cluster]]: blend a base section distance with the graded parser-hierarchy, run Smile HAC, and cut at the subject
 * count guided by silhouette. Every knob comes from [[ClusteringConfig]] (the tuned production values). The lower-level
 * [[fit]] / [[Fitted]] primitives remain for the DP0 evaluation sweeps.
 */
object SmileHacClusterer {

  private def pointDistance(name: String, a: Vector[Double], b: Vector[Double]): Double =
    name.toLowerCase match {
      case "euclidean" => math.sqrt(a.lazyZip(b).map((x, y) => (x - y) * (x - y)).sum)
      case _           => 1.0 - EmbeddingMetrics.cosine(a, b) // cosine distance — production default
    }

  /**
   * Graded hierarchy distance: 0 when two sections share the full parser breadcrumb, 1 when they share nothing, graded
   * by shared-prefix depth in between (the DP0-5b winner — the single biggest non-LLM lever, esp. on omnibus bills).
   * `graded=false` reproduces the legacy top-level-Title binary distance.
   */
  def structuralDistance(a: List[String], b: List[String], graded: Boolean): Double =
    if (graded) {
      val shared = a.zip(b).takeWhile { case (x, y) => x == y }.size
      val depth  = math.max(a.size, b.size)
      if (depth == 0) 1.0 else 1.0 - shared.toDouble / depth
    } else
      (a.headOption, b.headOption) match {
        case (Some(x), Some(y)) if x == y => 0.0
        case _                            => 1.0
      }

  private def proximity(vectors: IndexedSeq[Vector[Double]], distance: String): Array[Array[Double]] =
    Array.tabulate(vectors.size, vectors.size)((i, j) =>
      if (i == j) 0.0 else pointDistance(distance, vectors(i), vectors(j))
    )

  /**
   * Structural coverage: the fraction of sections carrying a non-empty parser breadcrumb. 0.0 for a fully FLAT bill (no
   * hierarchy → the graded-hierarchy term is a constant and carries no signal), 1.0 when every section is nested.
   */
  def structuralCoverage(parents: IndexedSeq[List[String]]): Double =
    if (parents.isEmpty) 0.0 else parents.count(_.nonEmpty).toDouble / parents.size

  /**
   * The structure weight actually applied: scaled by coverage when `adaptiveStructure`, else the configured constant.
   */
  def effectiveStructureWeight(parents: IndexedSeq[List[String]], config: ClusteringConfig): Double =
    if (config.adaptiveStructure) config.structureWeight * structuralCoverage(parents) else config.structureWeight

  /**
   * Production blended proximity: `(1 - w) * baseDistance + w * gradedHierarchy`, where `w` is the
   * [[effectiveStructureWeight]] (coverage-scaled under `adaptiveStructure`). Section embeddings must already be
   * transformed per `config.transform` (standardization needs corpus/global stats, applied upstream — a single bill has
   * no pooled statistics).
   */
  def blendedProximity(
    embeddings: IndexedSeq[Vector[Double]],
    parents: IndexedSeq[List[String]],
    config: ClusteringConfig,
  ): Array[Array[Double]] = {
    require(parents.sizeIs == embeddings.size, s"parents (${parents.size}) must match embeddings (${embeddings.size})")
    val w = effectiveStructureWeight(parents, config)
    Array.tabulate(embeddings.size, embeddings.size)((i, j) =>
      if (i == j) 0.0
      else {
        val base = pointDistance(config.distance, embeddings(i), embeddings(j))
        val str  = structuralDistance(parents(i), parents(j), config.gradedHierarchy)
        (1.0 - w) * base + w * str
      }
    )
  }

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
   * cheap, so an evaluation sweep re-cuts the same tree instead of refitting. Built via [[fit]] / [[fitFromProximity]].
   */
  final class Fitted private[SmileHacClusterer] (
    hc: HierarchicalClustering,
    dist: Array[Array[Double]],
    val n: Int,
  ) {
    private val heights: Array[Double] = hc.height()

    /** Cluster count implied by cutting at merge height `h` (the height-ceiling count). */
    def kThreshold(h: Double): Int = n - heights.count(_ <= h)

    /** Partition into k clusters. k ≤ 1 → one cluster; k ≥ n → all singletons (both reject Smile's partition(int)). */
    def cut(k: Int): Vector[Int] =
      if (k <= 1) Vector.fill(n)(0)
      else if (k >= n) (0 until n).toVector
      else renumber(hc.partition(k).toIndexedSeq)

    /** Mean silhouette of the k-cluster partition (height-independent, so a sweep computes each k at most once). */
    def silhouetteAt(k: Int): Double = silhouette(dist, hc.partition(k).toIndexedSeq)

    /** The cut height that yields k clusters: the largest merge height retained. */
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

  /** Fit from a precomputed (e.g. blended) distance matrix. The matrix is the silhouette distance too. */
  def fitFromProximity(dist: Array[Array[Double]], linkage: String): Fitted = {
    val hc = HierarchicalClustering.fit(linkageOf(linkage, dist.map(_.clone)))
    new Fitted(hc, dist, dist.length)
  }

  /**
   * Guided cut — the subject count is a GUIDE, not an exact split. Search k within ±`tolerance` of `guideK` and let the
   * silhouette pick the natural k (a window keeps it from collapsing to k=2 and degrades gracefully when the count is
   * over/under-stated). `guideK <= minK` collapses to one group (the single-concept guard).
   */
  def guidedCut(fitted: Fitted, guideK: Int, tolerance: Double, minK: Int): Vector[Int] =
    if (guideK <= minK) fitted.cut(1)
    else {
      val lo = math.max(2, math.round(guideK * (1.0 - tolerance)).toInt)
      val hi = math.min(fitted.n - 1, math.round(guideK * (1.0 + tolerance)).toInt)
      if (lo > hi) fitted.cut(guideK)
      else
        fitted.cut(
          (lo to hi)
            .foldLeft((Double.NegativeInfinity, lo)) {
              case ((bestS, bestK), k) =>
                val s = fitted.silhouetteAt(k)
                if (s > bestS) (s, k) else (bestS, bestK)
            }
            ._2
        )
    }

  /**
   * The PRODUCTION entry point (D3b). Standardized section embeddings + their parser breadcrumbs + the bill's subject
   * count → a concept-group label per section. `subjectCount <= config.minK` (single-concept bill) returns one group;
   * otherwise blend → HAC → cut. The cut is silhouette-guided around the subject count, EXCEPT on a flat bill under
   * `adaptiveCut` (coverage ≤ `flatCutCoverage`), where the silhouette is unreliable so we cut at the subject count
   * directly. Embeddings must already be transformed per `config.transform`.
   */
  def cluster(
    embeddings: IndexedSeq[Vector[Double]],
    parents: IndexedSeq[List[String]],
    subjectCount: Int,
    config: ClusteringConfig,
  ): Vector[Int] = {
    val n = embeddings.size
    if (n < 2) (0 until n).map(_ => 0).toVector
    else if (subjectCount <= config.minK) Vector.fill(n)(0)
    else {
      val fitted = fitFromProximity(blendedProximity(embeddings, parents, config), config.linkage)
      if (config.adaptiveCut && structuralCoverage(parents) <= config.flatCutCoverage)
        fitted.cut(
          subjectCount
        ) // flat bill: trust the subject count, skip the silhouette (cut() clamps k≥n → singletons)
      else guidedCut(fitted, subjectCount, config.guidedTolerance, config.minK)
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
