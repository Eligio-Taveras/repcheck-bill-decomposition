package com.repcheck.decomposition.evaluation.cluster

import com.repcheck.decomposition.conformance.ConformanceContract

class AgglomerativeClustererSpec extends ConformanceContract {

  private val va = Vector(1.0, 0.0)
  private val vb = Vector(0.0, 1.0)

  "cluster" should "return no labels for no vectors" in {
    AgglomerativeClusterer.cluster(IndexedSeq.empty, 0.5) shouldBe Vector.empty[Int]
  }

  it should "put a single vector in cluster 0" in {
    AgglomerativeClusterer.cluster(IndexedSeq(va), 0.5) shouldBe Vector(0)
  }

  it should "separate two tight, well-separated clusters" in {
    // same-cluster cosine distance 0; cross-cluster 1.0 > threshold 0.5 → two clusters
    AgglomerativeClusterer.cluster(IndexedSeq(va, va, vb, vb), 0.5) shouldBe Vector(0, 0, 1, 1)
  }

  it should "merge everything under a high threshold" in {
    AgglomerativeClusterer.cluster(IndexedSeq(va, va, vb, vb), 1.5) shouldBe Vector(0, 0, 0, 0)
  }

  it should "leave mutually distant vectors as singletons" in {
    val pts = IndexedSeq(Vector(1.0, 0.0), Vector(0.0, 1.0), Vector(-1.0, 0.0), Vector(0.0, -1.0))
    AgglomerativeClusterer.cluster(pts, 0.5) shouldBe Vector(0, 1, 2, 3)
  }

  it should "number clusters by first appearance (stable labeling)" in {
    AgglomerativeClusterer.cluster(IndexedSeq(vb, va, vb, va), 0.5) shouldBe Vector(0, 1, 0, 1)
  }

}
