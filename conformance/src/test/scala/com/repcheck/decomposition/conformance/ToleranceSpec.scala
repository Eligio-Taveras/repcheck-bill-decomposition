package com.repcheck.decomposition.conformance

class ToleranceSpec extends ConformanceContract {

  "cosineAtLeast" should "accept a near-identical (jittered) vector pair" in {
    val a = Vector(1.0, 2.0, 3.0, 4.0)
    val b = a.map(_ + 0.001)
    Tolerance.cosineAtLeast(a, b) shouldBe true
  }

  it should "reject an orthogonal pair" in {
    Tolerance.cosineAtLeast(Vector(1.0, 0.0), Vector(0.0, 1.0)) shouldBe false
  }

  "withinBand" should "accept inside the band and reject outside" in {
    val _ = Tolerance.withinBand(0.80, 0.802, 0.01) shouldBe true
    Tolerance.withinBand(0.80, 0.83, 0.01) shouldBe false
  }

}
