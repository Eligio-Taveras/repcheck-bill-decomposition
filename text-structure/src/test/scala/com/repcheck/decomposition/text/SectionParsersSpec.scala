package com.repcheck.decomposition.text

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Behaviour derived from real corpus samples (AlloyDB scan): stored bill text is whitespace-collapsed (no line breaks),
 * so GPO headings are matched INLINE and CASE-SENSITIVELY.
 */
class SectionParsersSpec extends AnyFlatSpec with Matchers {

  private val defaultParser = new DefaultSectionParser

  // Mirrors a real HR bill: header/sponsors preamble, inline UPPERCASE headings, and a lowercase
  // `section 101` CITATION inside section 2 that must NOT be treated as a heading.
  private val gpo =
    "[Congressional Bills 119th Congress] H. R. 1 A BILL To do things. " +
      "Be it enacted by the Senate and House of Representatives, " +
      "SECTION 1. SHORT TITLE. This Act may be cited as the Test Act. " +
      "SEC. 2. FINDINGS. Congress finds that section 101 of title 5 applies."

  // Real GPO bill.dtd shape: <enum>/<header>/<text>, a <form> preamble, a TITLE container, and an amendatory
  // <quoted-block> whose nested <section> must NOT become its own unit.
  private val billXml =
    """<bill>
      |  <form><legis-num>H. R. 100</legis-num><official-title>A bill to do things.</official-title></form>
      |  <legis-body>
      |    <section><enum>1.</enum><header>Short title</header><text>This Act may be cited as the Test Act.</text></section>
      |    <title id="t1"><enum>I</enum><header>Funding</header>
      |      <section><enum>101.</enum><header>Authorization</header><text>There is authorized
      |        <quoted-block><section><enum>9.</enum><header>Quoted</header><text>amendatory text</text></section></quoted-block>
      |        such sums.</text></section>
      |    </title>
      |  </legis-body>
      |</bill>""".stripMargin

  "FallbackSectionParser" should "return exactly one Fallback section holding the whole content" in {
    FallbackSectionParser.parse("anything at all") match {
      case ParsedSection(idx, id, heading, content, kind, parents) :: Nil =>
        idx shouldBe 0
        id shouldBe None
        heading shouldBe None
        content shouldBe "anything at all"
        kind shouldBe SectionKind.Fallback
        parents shouldBe Nil
      case other => fail(s"expected one section, got $other")
    }
  }

  "GpoTextSectionParser" should "split inline headings, keep the preamble, and ignore lowercase citations" in {
    GpoTextSectionParser.parse(gpo) match {
      case Right(pre :: s1 :: s2 :: Nil) =>
        pre.kind shouldBe SectionKind.Fallback // header/enacting-clause lead-in preserved
        pre.sectionIdentifier shouldBe None
        s1.kind shouldBe SectionKind.Section
        s1.sectionIdentifier shouldBe Some("1")
        s1.content should startWith("SECTION 1.")
        s2.sectionIdentifier shouldBe Some("2")
        s2.content should include("section 101") // the citation stayed in s2, did NOT create a section
      case other => fail(s"expected preamble + 2 sections, got $other")
    }
  }

  it should "capture lettered section numbers (SEC. 2A.)" in {
    GpoTextSectionParser.parse("Lead. SECTION 1. First. SEC. 2A. Second.") match {
      case Right(sections) => sections.flatMap(_.sectionIdentifier) shouldBe List("1", "2A")
      case Left(f)         => fail(s"expected Right, got $f")
    }
  }

  it should "parse a resolution (no SEC.) into a Whereas preamble + a unit per resolving clause" in {
    val res =
      "S. RES. 1 Celebrating X. Whereas A is so; Whereas B is so; " +
        "Resolved, That the Senate (1) celebrates X; (2) encourages Y."
    GpoTextSectionParser.parse(res) match {
      case Right(pre :: c1 :: c2 :: Nil) =>
        pre.kind shouldBe SectionKind.Fallback
        pre.content should include("Whereas")
        c1.sectionIdentifier shouldBe Some("1")
        c1.content should include("celebrates")
        c1.parents.headOption.exists(_.contains("Resolved")) shouldBe true
        c2.sectionIdentifier shouldBe Some("2")
      case other => fail(s"expected preamble + 2 clauses, got $other")
    }
  }

  it should "treat a single resolving statement as one unit" in {
    GpoTextSectionParser.parse("H. RES. 2 Honoring Y. Resolved, That the House honors Y for service.") match {
      case Right(pre :: one :: Nil) =>
        one.sectionIdentifier shouldBe None
        one.content should startWith("Resolved")
      case other => fail(s"expected preamble + 1 unit, got $other")
    }
  }

  it should "split a constitutional-amendment joint resolution on its proposed Article Sections" in {
    val amend =
      "H. J. RES. 1 Proposing an amendment to the Constitution relating to term limits. " +
        "Resolved by the Senate and House, That the following article is proposed: Article -- " +
        "Section 1. No person shall serve more than 12 years. " +
        "Section 2. This article shall be inoperative unless ratified within 7 years."
    GpoTextSectionParser.parse(amend) match {
      case Right(sections) =>
        val secs = sections.filter(_.kind == SectionKind.Section)
        secs.map(_.sectionIdentifier) shouldBe List(Some("1"), Some("2"))
        secs.map(_.parents).forall(_.headOption.exists(_.contains("article is proposed"))) shouldBe true
      case Left(f) => fail(s"expected Right, got $f")
    }
  }

  it should "NOT split on Article citations like 'Article I, section 8' in a normal resolution" in {
    // No "amendment to the Constitution" trigger -> Article/section here are citations, split on (N).
    val res =
      "S. RES. 9 Affirming X. Resolved, That the Senate, citing Article I, section 8, (1) affirms X; (2) notes Y."
    GpoTextSectionParser.parse(res) match {
      case Right(sections) =>
        sections.filter(_.kind == SectionKind.Section).map(_.sectionIdentifier) shouldBe List(Some("1"), Some("2"))
      case Left(f) => fail(s"expected Right, got $f")
    }
  }

  it should "fail (Left) with neither uppercase headings nor a Resolved clause" in {
    GpoTextSectionParser.parse("just prose mentioning section 5 of some act") match {
      case Left(f)  => f.message should include("no SECTION")
      case Right(r) => fail(s"expected Left, got $r")
    }
  }

  it should "attach TITLE hierarchy as each section's parents breadcrumb (not as its own unit)" in {
    val titled = "Preamble. TITLE I FUNDING SEC. 101. FIRST. body one. TITLE II RULES SEC. 201. SECOND. body two."
    GpoTextSectionParser.parse(titled) match {
      case Right(sections) =>
        val secs = sections.filter(_.kind == SectionKind.Section)
        secs.map(_.sectionIdentifier) shouldBe List(Some("101"), Some("201"))
        secs.map(_.parents) shouldBe List(List("TITLE I"), List("TITLE II"))
      case Left(f) => fail(s"expected Right, got $f")
    }
  }

  it should "track uppercase hierarchy variants — arabic TITLE, lettered PART, CHAPTER — as parents" in {
    val txt = "Pre. TITLE 1 FUNDS PART A RULES CHAPTER 2 ITEMS SEC. 305. THE PROVISION. body text."
    GpoTextSectionParser.parse(txt) match {
      case Right(sections) =>
        sections.filter(_.kind == SectionKind.Section).flatMap(_.parents) shouldBe
          List("TITLE 1", "PART A", "CHAPTER 2")
      case Left(f) => fail(s"expected Right, got $f")
    }
  }

  it should "not match prose like 'PART OF' as a hierarchy marker" in {
    // "PART OF THE" must not be treated as a PART marker (single-token numbering only).
    GpoTextSectionParser.parse("Intro. SEC. 1. This is PART OF THE plan and works.") match {
      case Right(sections) =>
        sections.filter(_.kind == SectionKind.Section).flatMap(_.parents) shouldBe Nil
      case Left(f) => fail(s"expected Right, got $f")
    }
  }

  "GpoBillXmlSectionParser" should "extract enum/header, attach TITLE parents, and keep quoted-block text inline" in {
    GpoBillXmlSectionParser.parse(billXml) match {
      case Right(pre :: s1 :: s101 :: Nil) =>
        pre.kind shouldBe SectionKind.Fallback // <form> preamble
        pre.content should include("H. R. 100")
        s1.kind shouldBe SectionKind.Section
        s1.sectionIdentifier shouldBe Some("1") // trailing dot stripped
        s1.heading shouldBe Some("Short title")
        s1.parents shouldBe Nil
        s101.sectionIdentifier shouldBe Some("101")
        s101.heading shouldBe Some("Authorization")
        s101.parents shouldBe List("TITLE I Funding")
        s101.content should include("amendatory text") // quoted-block stayed inside, not split into its own unit
      case other => fail(s"expected preamble + 2 sections, got $other")
    }
  }

  it should "index units 0..n-1 in document order" in {
    GpoBillXmlSectionParser.parse(billXml) match {
      case Right(sections) => sections.map(_.sectionIndex) shouldBe sections.indices.toList
      case Left(f)         => fail(s"expected Right, got $f")
    }
  }

  it should "fail (Left) on malformed XML" in {
    GpoBillXmlSectionParser.parse("<bill><section></bill>") match {
      case Left(f)  => f.message should include("malformed")
      case Right(r) => fail(s"expected Left, got $r")
    }
  }

  it should "fail (Left) when there are no section elements" in {
    GpoBillXmlSectionParser.parse("<bill><form>x</form><legis-body></legis-body></bill>") match {
      case Left(f)  => f.message should include("no section")
      case Right(r) => fail(s"expected Left, got $r")
    }
  }

  "DefaultSectionParser" should "dispatch Formatted Text to the GPO parser" in {
    defaultParser.parse(gpo, TextFormat.FormattedText).parserUsed shouldBe ParserKind.GpoText
  }

  it should "dispatch Formatted XML to the GPO XML parser" in {
    defaultParser.parse(billXml, TextFormat.FormattedXml).parserUsed shouldBe ParserKind.GpoXml
  }

  it should "parse already-extracted PDF text as text (PDF is NOT excluded)" in {
    defaultParser.parse(gpo, TextFormat.Pdf).parserUsed shouldBe ParserKind.GpoText
  }

  it should "fall back (not emit garbage) when PDF content is still raw binary" in {
    defaultParser.parse("%PDF-1.5   binary", TextFormat.Pdf).parserUsed shouldBe ParserKind.Fallback
  }

  it should "fall back for PDF text that has no sections (e.g. appropriations)" in {
    val r = defaultParser.parse("An Act making appropriations. TITLE I MULTILATERAL ASSISTANCE.", TextFormat.Pdf)
    r.parserUsed shouldBe ParserKind.Fallback
    r.sections.size shouldBe 1
  }

  it should "route a resolution (no SEC.) through the GPO parser, not a single fallback" in {
    val resolution =
      "S. RES. 1 Celebrating X. Whereas A is so; Whereas B is so; Resolved, That the Senate (1) celebrates X; (2) encourages Y."
    val r = defaultParser.parse(resolution, TextFormat.FormattedText)
    r.parserUsed shouldBe ParserKind.GpoText
    r.sections.size should be > 1
  }

  it should "resolve resolutions in PDF-format text too" in {
    val resolution = "S. RES. 1 Celebrating X. Whereas A; Resolved, That the Senate (1) celebrates X; (2) encourages Y."
    val r          = defaultParser.parse(resolution, TextFormat.Pdf)
    r.parserUsed shouldBe ParserKind.GpoText
    r.sections.size should be > 1
  }

  it should "resolve resolutions in section-less XML too (USLM falls back to the text parser)" in {
    val xmlRes =
      "<resolution><preamble>Whereas A is so;</preamble>" +
        "<resolution-body>Resolved, That the Senate (1) celebrates X; (2) encourages Y.</resolution-body></resolution>"
    val r = defaultParser.parse(xmlRes, TextFormat.FormattedXml)
    r.sections.size should be > 1
    r.sections.exists(_.sectionIdentifier.contains("1")) shouldBe true
  }

  it should "fall back for Other formats" in {
    defaultParser.parse("whatever", TextFormat.Other).parserUsed shouldBe ParserKind.Fallback
  }

  "TextFormat.fromFormatType" should "map the live format_type strings" in {
    TextFormat.fromFormatType("Formatted Text") shouldBe TextFormat.FormattedText
    TextFormat.fromFormatType("PDF") shouldBe TextFormat.Pdf
    TextFormat.fromFormatType("Formatted XML") shouldBe TextFormat.FormattedXml
    TextFormat.fromFormatType("something else") shouldBe TextFormat.Other
  }

}
