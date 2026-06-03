package com.repcheck.decomposition.text

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Deterministic-logic tests on synthetic inputs. The real-bill corpus property tests (no-loss / order / determinism /
 * coverage = the DP gate) are added once the AlloyDB corpus lands.
 */
class SectionParsersSpec extends AnyFlatSpec with Matchers {

  private val gpo =
    """A BILL
      |
      |SECTION 1. SHORT TITLE.
      |    This Act may be cited as the Test Act.
      |
      |SEC. 2. FINDINGS.
      |    Congress finds the following.
      |""".stripMargin

  private val uslm =
    """<bill>
      |  <section><num>1.</num><heading>Short Title</heading><text>This Act may be cited.</text></section>
      |  <section><num>2.</num><heading>Findings</heading><text>Congress finds.</text></section>
      |</bill>""".stripMargin

  "FallbackSectionParser" should "return exactly one Fallback section holding the whole content" in {
    FallbackSectionParser.parse("anything at all") match {
      case ParsedSection(idx, id, heading, content, kind) :: Nil =>
        idx shouldBe 0
        id shouldBe None
        heading shouldBe None
        content shouldBe "anything at all"
        kind shouldBe SectionKind.Fallback
      case other => fail(s"expected one section, got $other")
    }
  }

  "GpoTextSectionParser" should "split on SECTION/SEC. headings, preserving order + identifiers" in {
    GpoTextSectionParser.parse(gpo) match {
      case Right(s1 :: s2 :: Nil) =>
        s1.sectionIndex shouldBe 0
        s1.sectionIdentifier shouldBe Some("1")
        s1.heading shouldBe Some("SHORT TITLE.")
        s1.content should startWith("SECTION 1.")
        s2.sectionIdentifier shouldBe Some("2")
        s2.heading shouldBe Some("FINDINGS.")
      case other => fail(s"expected two sections, got $other")
    }
  }

  it should "fail (Left) when there are no headings" in {
    GpoTextSectionParser.parse("just prose, no sections here") match {
      case Left(f)  => f.message should include("no SECTION")
      case Right(r) => fail(s"expected Left, got $r")
    }
  }

  "UslmXmlSectionParser" should "extract <section> num/heading in order" in {
    UslmXmlSectionParser.parse(uslm) match {
      case Right(s1 :: s2 :: Nil) =>
        s1.sectionIdentifier shouldBe Some("1.")
        s1.heading shouldBe Some("Short Title")
        s2.sectionIdentifier shouldBe Some("2.")
        s2.heading shouldBe Some("Findings")
      case other => fail(s"expected two sections, got $other")
    }
  }

  it should "fail (Left) on malformed XML" in {
    UslmXmlSectionParser.parse("<bill><section></bill>") match {
      case Left(f)  => f.message should include("malformed")
      case Right(r) => fail(s"expected Left, got $r")
    }
  }

  "DefaultSectionParser" should "dispatch Formatted Text to the GPO parser" in {
    val r = DefaultSectionParser.parse(gpo, TextFormat.FormattedText)
    r.parserUsed shouldBe ParserKind.GpoText
    r.sections.size shouldBe 2
  }

  it should "dispatch Formatted XML to the USLM parser" in {
    DefaultSectionParser.parse(uslm, TextFormat.FormattedXml).parserUsed shouldBe ParserKind.UslmXml
  }

  it should "degrade to the single-section fallback for PDF" in {
    val r = DefaultSectionParser.parse("scanned text", TextFormat.Pdf)
    r.parserUsed shouldBe ParserKind.Fallback
    r.sections.size shouldBe 1
  }

  it should "degrade to fallback when the format-specific parser finds nothing" in {
    DefaultSectionParser.parse("no headings", TextFormat.FormattedText).parserUsed shouldBe ParserKind.Fallback
  }

  it should "degrade to fallback on malformed XML rather than throw" in {
    DefaultSectionParser
      .parse("<bill><section></bill>", TextFormat.FormattedXml)
      .parserUsed shouldBe ParserKind.Fallback
  }

  "TextFormat.fromFormatType" should "map the live format_type strings" in {
    TextFormat.fromFormatType("Formatted Text") shouldBe TextFormat.FormattedText
    TextFormat.fromFormatType("PDF") shouldBe TextFormat.Pdf
    TextFormat.fromFormatType("Formatted XML") shouldBe TextFormat.FormattedXml
    TextFormat.fromFormatType("something else") shouldBe TextFormat.Other
  }

}
