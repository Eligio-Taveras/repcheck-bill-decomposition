package com.repcheck.decomposition.evaluation.compare

import com.repcheck.decomposition.conformance.ConformanceContract

class GroupingComparisonSpec extends ConformanceContract {

  "labelsFromGroups" should "map each section to its group ordinal" in {
    GroupingComparison.labelsFromGroups(List(List(0, 2), List(1)), 3) shouldBe Vector(0, 1, 0)
  }

  it should "give an uncovered section its own singleton label" in {
    // section 2 not in any group → distinct label, so it never accidentally joins group 0
    val labels = GroupingComparison.labelsFromGroups(List(List(0, 1)), 3)
    labels(0) shouldBe labels(1)
    labels(2) should not be labels(0)
  }

  "compare" should "be perfect agreement for identical labelings" in {
    val r = GroupingComparison.compare(Vector(0, 0, 1, 1), Vector(0, 0, 1, 1))
    r.ari shouldBe 1.0 +- 1e-9
    r.vMeasure shouldBe 1.0 +- 1e-9
    r.predGroups shouldBe 2
    r.refGroups shouldBe 2
  }

  it should "be invariant to label renaming" in {
    GroupingComparison.compare(Vector(0, 0, 1, 1), Vector(1, 1, 0, 0)).ari shouldBe 1.0 +- 1e-9
  }

  it should "report the hand-computed partial-agreement ARI of 0" in {
    GroupingComparison.compare(Vector(0, 0, 0, 1), Vector(0, 0, 1, 1)).ari shouldBe 0.0 +- 1e-4
  }

}
