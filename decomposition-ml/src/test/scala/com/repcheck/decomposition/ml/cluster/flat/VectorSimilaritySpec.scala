package com.repcheck.decomposition.ml.cluster.flat

class VectorSimilaritySpec extends com.repcheck.decomposition.conformance.ConformanceContract {

  "jaccard" should "be the intersection over the union" in {
    VectorSimilarity.jaccard(Set(1, 2, 3), Set(2, 3, 4)) shouldBe 2.0 / 4.0
  }

  it should "be 0 when both sets are empty" in {
    VectorSimilarity.jaccard(Set.empty[Int], Set.empty[Int]) shouldBe 0.0
  }

  "cosine" should "be 1 for identical vectors" in {
    math.abs(VectorSimilarity.cosine(Vector(1.0, 2.0, 3.0), Vector(1.0, 2.0, 3.0)) - 1.0) should be < 1e-9
  }

  it should "be 0 for orthogonal vectors" in {
    math.abs(VectorSimilarity.cosine(Vector(1.0, 0.0), Vector(0.0, 1.0))) should be < 1e-9
  }

  it should "be 0 when either vector is all zeros" in {
    VectorSimilarity.cosine(Vector(0.0, 0.0), Vector(1.0, 1.0)) shouldBe 0.0
  }

  "cosineSparse" should "be 1 for identical sparse vectors" in {
    math.abs(
      VectorSimilarity.cosineSparse(Map("a" -> 1.0, "b" -> 2.0), Map("a" -> 1.0, "b" -> 2.0)) - 1.0
    ) should be < 1e-9
  }

  it should "be 0 with no shared keys" in {
    VectorSimilarity.cosineSparse(Map("a" -> 1.0), Map("b" -> 1.0)) shouldBe 0.0
  }

  it should "be 0 when one sparse vector is empty (zero norm)" in {
    VectorSimilarity.cosineSparse(Map.empty[String, Double], Map("a" -> 1.0)) shouldBe 0.0
  }

}
