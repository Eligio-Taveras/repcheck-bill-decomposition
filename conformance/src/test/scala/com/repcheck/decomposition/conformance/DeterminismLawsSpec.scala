package com.repcheck.decomposition.conformance

import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class DeterminismLawsSpec extends ConformanceContract with ScalaCheckPropertyChecks {

  "isDeterministic" should "hold for a pure function" in {
    forAll((s: String) => DeterminismLaws.isDeterministic[String, Int](_.length)(s) shouldBe true)
  }

  "isIdempotent" should "hold for sorting" in {
    forAll((xs: List[Int]) => DeterminismLaws.isIdempotent[List[Int]](_.sorted)(xs) shouldBe true)
  }

  "orderInvariant" should "hold for a key derived from sorted members" in {
    forAll { (xs: List[Int]) =>
      DeterminismLaws.orderInvariant[Int, String](_.sorted.mkString("-"))(xs, xs.reverse) shouldBe true
    }
  }

}
