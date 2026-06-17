package com.repcheck.decomposition.evaluation.cluster

import com.repcheck.decomposition.conformance.ConformanceContract

class SmileHacClustererSpec extends ConformanceContract {

  private val va = Vector(1.0, 0.0)
  private val vb = Vector(0.0, 1.0)

  "clusterAtThreshold" should "separate two tight, well-separated clusters" in {
    // same-cluster cosine distance 0; cross-cluster 1.0 → its Smile merge height (2.0) > dMax 0.5 → two clusters
    SmileHacClusterer.clusterAtThreshold(IndexedSeq(va, va, vb, vb), ClusteringConfig(dMax = 0.5)) shouldBe
      Vector(0, 0, 1, 1)
  }

  it should "merge everything under a high threshold (above Smile's top merge height)" in {
    SmileHacClusterer.clusterAtThreshold(IndexedSeq(va, va, vb, vb), ClusteringConfig(dMax = 2.5)) shouldBe
      Vector(0, 0, 0, 0)
  }

  it should "honor every configured linkage" in {
    Seq("average", "complete", "single", "ward").foreach { lk =>
      withClue(s"linkage=$lk: ") {
        SmileHacClusterer
          .clusterAtThreshold(IndexedSeq(va, va, vb, vb), ClusteringConfig(dMax = 0.5, linkage = lk))
          .distinct
          .size shouldBe 2
      }
    }
  }

  it should "separate clusters under euclidean distance too" in {
    SmileHacClusterer.clusterAtThreshold(
      IndexedSeq(va, va, vb, vb),
      ClusteringConfig(dMax = 0.5, distance = "euclidean"),
    ) shouldBe
      Vector(0, 0, 1, 1)
  }

  it should "leave every section a singleton when dMax is below all merge heights" in {
    SmileHacClusterer.clusterAtThreshold(IndexedSeq(va, va, vb, vb), ClusteringConfig(dMax = -1.0)) shouldBe
      Vector(0, 1, 2, 3)
  }

  it should "handle the degenerate n<2 cases" in {
    SmileHacClusterer.clusterAtThreshold(IndexedSeq.empty, ClusteringConfig()) shouldBe Vector.empty[Int]
    SmileHacClusterer.clusterAtThreshold(IndexedSeq(va), ClusteringConfig()) shouldBe Vector(0)
  }

  "clusterBySilhouette" should "recover two well-separated clusters" in {
    SmileHacClusterer.clusterBySilhouette(IndexedSeq(va, va, vb, vb), ClusteringConfig(maxK = 3)) shouldBe
      Vector(0, 0, 1, 1)
  }

  it should "recover three well-separated clusters" in {
    val vc  = Vector(-1.0, 0.0)
    val pts = IndexedSeq(va, va, vb, vb, vc, vc)
    val out = SmileHacClusterer.clusterBySilhouette(pts, ClusteringConfig(maxK = 5))
    out.distinct.size shouldBe 3
    out shouldBe Vector(0, 0, 1, 1, 2, 2)
  }

  it should "fall back to one cluster for fewer than three vectors" in {
    SmileHacClusterer.clusterBySilhouette(IndexedSeq(va, vb), ClusteringConfig(maxK = 3)) shouldBe Vector(0, 0)
  }

  it should "not merge across dMax even when silhouette would (singletons above D_max)" in {
    // dMax below every merge height forces kThresh = n > maxK → the dMax cut wins, all singletons
    val pts = IndexedSeq(va, va, vb, vb, Vector(-1.0, 0.0), Vector(-1.0, 0.0))
    SmileHacClusterer.clusterBySilhouette(pts, ClusteringConfig(dMax = -1.0, maxK = 5)) shouldBe Vector(0, 1, 2, 3, 4,
      5)
  }

  it should "score partitions that contain a singleton cluster" in {
    // {va,va,va} + lone vb — at k=2 the vb is a singleton, exercising the singleton silhouette branch
    val out = SmileHacClusterer.clusterBySilhouette(IndexedSeq(va, va, va, vb), ClusteringConfig(maxK = 3))
    out.distinct.size should be >= 2
    out.groupBy(identity).values.map(_.size).toList should contain(1)
  }

}
