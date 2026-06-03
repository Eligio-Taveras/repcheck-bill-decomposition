package com.repcheck.decomposition.text

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SubSplitterSpec extends AnyFlatSpec with Matchers {

  private def section(content: String): ParsedSection =
    ParsedSection(0, Some("1"), Some("H"), content, SectionKind.Section)

  "SubSplitter" should "pass a within-limit section through as one unit (subIndex 0)" in {
    SubSplitter.split(List(section("one two three")), maxTokens = 10, overlap = 2) match {
      case EmbeddableUnit(si, sub, id, content) :: Nil =>
        si shouldBe 0
        sub shouldBe 0
        id shouldBe Some("1")
        content shouldBe "one two three"
      case other => fail(s"expected one unit, got $other")
    }
  }

  it should "split an oversize section into overlapping windows with incrementing subIndex" in {
    val text  = (1 to 10).map(i => s"w$i").mkString(" ")
    val units = SubSplitter.split(List(section(text)), maxTokens = 4, overlap = 1)
    units.size should be > 1
    units.map(_.subIndex) shouldBe units.indices.toList
    units.map(_.sectionIndex).distinct shouldBe List(0)
  }

  it should "lose no words across the windows (coverage invariant)" in {
    val words   = (1 to 10).map(i => s"w$i").toList
    val units   = SubSplitter.split(List(section(words.mkString(" "))), maxTokens = 4, overlap = 1)
    val covered = units.flatMap(_.content.split("\\s+").toList).distinct.sorted
    covered shouldBe words.sorted
  }

  it should "treat maxTokens <= 0 as no split" in {
    SubSplitter.split(List(section("a b c d e")), maxTokens = 0, overlap = 0).size shouldBe 1
  }

}
