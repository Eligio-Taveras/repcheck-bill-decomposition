package com.repcheck.decomposition.ml.cluster.flat

class ClusterMathSpec extends com.repcheck.decomposition.conformance.ConformanceContract {

  private val matrix = Vector(
    Vector(1.0, 0.8, 0.2),
    Vector(0.8, 1.0, 0.4),
    Vector(0.2, 0.4, 1.0),
  )

  "meanCrossValue" should "average every cross pair between the two clusters" in {
    ClusterMath.meanCrossValue(matrix, List(0), List(1, 2)) shouldBe (0.8 + 0.2) / 2.0
  }

  "cohesion" should "be 1 for a single-section cluster" in {
    ClusterMath.cohesion(matrix, List(0)) shouldBe 1.0
  }

  it should "average the internal pairs of a multi-section cluster" in {
    ClusterMath.cohesion(matrix, List(0, 1)) shouldBe 0.8
  }

  "populationStdev" should "be 0 for fewer than two values" in {
    ClusterMath.populationStdev(Seq(5.0)) shouldBe 0.0
  }

  it should "compute the population standard deviation" in {
    math.abs(ClusterMath.populationStdev(Seq(2.0, 4.0)) - 1.0) should be < 1e-9
  }

}
