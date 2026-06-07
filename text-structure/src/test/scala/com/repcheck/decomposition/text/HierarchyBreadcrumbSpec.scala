package com.repcheck.decomposition.text

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Targeted tests for the extracted pure helper. End-to-end hierarchy behaviour is covered via `GpoTextSectionParser` in
 * `SectionParsersSpec`; these pin the helper's contract directly.
 */
class HierarchyBreadcrumbSpec extends AnyFlatSpec with Matchers {

  "nestingLevelOf" should "order DIVISION < TITLE < Subtitle < PART < CHAPTER" in {
    val levels =
      List("DIVISION A", "TITLE I", "Subtitle A", "PART A", "CHAPTER 1").map(HierarchyBreadcrumb.nestingLevelOf)
    levels shouldBe levels.sorted
    levels.distinct.size shouldBe 5
  }

  "before" should "return enclosing labels, clearing deeper levels when a new same-level marker appears" in {
    val text    = "X TITLE I a PART A b TITLE II c SEC. 1. here"
    val markers = HierarchyBreadcrumb.markersIn(text)
    val pos     = text.indexOf("SEC. 1.")
    // TITLE II replaces TITLE I (level 1) and clears the deeper PART A — only TITLE II remains.
    HierarchyBreadcrumb.before(markers, pos) shouldBe List("TITLE II")
  }

  it should "be empty when no hierarchy markers precede the position" in {
    HierarchyBreadcrumb.before(HierarchyBreadcrumb.markersIn("SEC. 1. text only"), 0) shouldBe Nil
  }

}
