package com.repcheck.decomposition.ml.embed

import com.repcheck.decomposition.conformance.ConformanceContract

class Vector1024Spec extends ConformanceContract {

  "Vector1024.of" should "accept a 1024-dim vector and round-trip its values" in {
    val raw = Vector.tabulate(Vector1024.Dim)(_.toFloat)
    Vector1024.of(raw).map(_.values) shouldBe Right(raw)
  }

  it should "reject a wrong-dimension vector with a descriptive message" in {
    val tooShort = Vector.fill(3)(0.0f)
    Vector1024.of(tooShort) match {
      case Left(msg) => msg should (include("1024") and include("got 3"))
      case Right(_)  => fail("expected Left for a 3-dim vector")
    }
  }

  "toDoubles" should "widen each float to a double" in {
    val raw = Vector.fill(Vector1024.Dim)(1.5f)
    Vector1024.of(raw).map(_.toDoubles) shouldBe Right(Vector.fill(Vector1024.Dim)(1.5d))
  }

  "Dim" should "be 1024" in {
    Vector1024.Dim shouldBe 1024
  }

}
