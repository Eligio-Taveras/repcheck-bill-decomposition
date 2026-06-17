package com.repcheck.decomposition.evaluation.embed

import com.repcheck.decomposition.conformance.ConformanceContract

class EmbeddingTransformSpec extends ConformanceContract {

  private val vs = Seq(Vector(1.0, 10.0), Vector(3.0, 30.0), Vector(2.0, 20.0))

  "mean" should "be the elementwise average" in {
    EmbeddingTransform.mean(vs) shouldBe Vector(2.0, 20.0)
  }

  "std" should "be the elementwise population stddev" in {
    // var dim0 = ((1-2)^2+(3-2)^2+(2-2)^2)/3 = 2/3 → sd ~0.8165; dim1 = 100x → ~8.165
    val sd = EmbeddingTransform.std(vs, EmbeddingTransform.mean(vs))
    sd(0) shouldBe math.sqrt(2.0 / 3.0) +- 1e-9
    sd(1) shouldBe math.sqrt(200.0 / 3.0) +- 1e-9
  }

  "center" should "subtract the mean" in {
    EmbeddingTransform.center(Vector(3.0, 30.0), Vector(2.0, 20.0)) shouldBe Vector(1.0, 10.0)
  }

  "standardize" should "produce zero-mean unit-variance dims and guard zero variance" in {
    val out = EmbeddingTransform.apply("standardize", vs)
    EmbeddingTransform.mean(out).foreach(_ shouldBe 0.0 +- 1e-9)
    EmbeddingTransform.std(out, EmbeddingTransform.mean(out)).foreach(_ shouldBe 1.0 +- 1e-9)
  }

  it should "zero out a constant (zero-variance) dimension" in {
    val constDim = Seq(Vector(5.0, 1.0), Vector(5.0, 2.0))
    EmbeddingTransform.apply("standardize", constDim).foreach(v => v(0) shouldBe 0.0)
  }

  "apply" should "pass vectors through unchanged for an unknown/none transform" in {
    EmbeddingTransform.apply("none", vs) shouldBe vs.toVector
  }

}
