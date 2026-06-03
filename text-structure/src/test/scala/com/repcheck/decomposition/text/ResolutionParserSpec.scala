package com.repcheck.decomposition.text

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Resolution structure verified against the corpus: header + `Whereas` preamble, then a `Resolved, That …` block whose
 * numbered points are the operative units.
 */
class ResolutionParserSpec extends AnyFlatSpec with Matchers {

  "ResolutionParser" should "split into a Whereas-preamble context unit + one unit per resolving clause" in {
    val res =
      "S. RES. 1 Celebrating X. Whereas A is so; Whereas B is so; " +
        "Resolved, That the Senate (1) celebrates X; (2) encourages Y."
    ResolutionParser.parse(res) match {
      case Right(pre :: c1 :: c2 :: Nil) =>
        pre.kind shouldBe SectionKind.Fallback
        pre.content should include("Whereas")
        c1.kind shouldBe SectionKind.Section
        c1.sectionIdentifier shouldBe Some("1")
        c1.content should include("celebrates")
        c1.parents.headOption.exists(_.contains("Resolved")) shouldBe true
        c2.sectionIdentifier shouldBe Some("2")
        c2.content should include("encourages")
      case other => fail(s"expected preamble + 2 clauses, got $other")
    }
  }

  it should "treat a single resolving statement as one operative unit" in {
    val single = "H. RES. 2 Honoring Y. Resolved, That the House honors Y for service."
    ResolutionParser.parse(single) match {
      case Right(pre :: one :: Nil) =>
        pre.content should include("Honoring")
        one.kind shouldBe SectionKind.Section
        one.sectionIdentifier shouldBe None
        one.content should startWith("Resolved")
      case other => fail(s"expected preamble + 1 unit, got $other")
    }
  }

  it should "fail (Left) when there is no Resolved clause" in {
    ResolutionParser.parse("Just some prose with no operative clause at all") match {
      case Left(f)  => f.message should include("no 'Resolved'")
      case Right(r) => fail(s"expected Left, got $r")
    }
  }

}
