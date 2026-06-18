package com.repcheck.decomposition.ml.metrics

import com.repcheck.decomposition.conformance.ConformanceContract

class EmbeddingMetricsSpec extends ConformanceContract {

  private val tol = 1e-9

  // two tight clusters: "a" near (1,0), "b" near (0,1)
  private val vectors = Seq(Vector(1.0, 0.0), Vector(1.0, 0.0), Vector(0.0, 1.0), Vector(0.0, 1.0))
  private val labels  = Seq("a", "a", "b", "b")

  "cosine" should "be 1.0 for identical and 0.0 for orthogonal / zero vectors" in {
    EmbeddingMetrics.cosine(Vector(1.0, 2.0), Vector(1.0, 2.0)) shouldBe 1.0 +- tol
    EmbeddingMetrics.cosine(Vector(1.0, 0.0), Vector(0.0, 1.0)) shouldBe 0.0 +- tol
    EmbeddingMetrics.cosine(Vector(0.0, 0.0), Vector(1.0, 1.0)) shouldBe 0.0
  }

  it should "reject a dimension mismatch" in {
    an[IllegalArgumentException] should be thrownBy EmbeddingMetrics.cosine(Vector(1.0), Vector(1.0, 2.0))
  }

  "meanCosineGap" should "be 1.0 when same-label pairs are identical and cross-label pairs orthogonal" in {
    EmbeddingMetrics.meanCosineGap(vectors, labels) shouldBe 1.0 +- tol
  }

  it should "be 0.0 when there are no cross-label pairs" in {
    EmbeddingMetrics.meanCosineGap(vectors, Seq("a", "a", "a", "a")) shouldBe 0.0
  }

  "separationAuc" should "be 1.0 when every same-label pair outscores every cross-label pair" in {
    EmbeddingMetrics.separationAuc(vectors, labels) shouldBe 1.0 +- tol
  }

  it should "be 0.5 with no negative (cross-label) pairs" in {
    EmbeddingMetrics.separationAuc(vectors, Seq("a", "a", "a", "a")) shouldBe 0.5
  }

  it should "reject vectors/labels of different length" in {
    an[IllegalArgumentException] should be thrownBy EmbeddingMetrics.separationAuc(vectors, Seq("a"))
  }

  "anisotropy" should "be the mean pairwise cosine (2 of 6 pairs identical, 4 orthogonal → 1/3)" in {
    EmbeddingMetrics.anisotropy(vectors) shouldBe (1.0 / 3.0) +- 1e-9
  }

  it should "be 0.0 with fewer than two vectors" in {
    EmbeddingMetrics.anisotropy(Seq(Vector(1.0, 0.0))) shouldBe 0.0
  }

}
