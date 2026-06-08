package com.repcheck.decomposition.text

import scala.io.Source
import scala.xml.XML

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Validates the GPO XML parser against real bills/resolutions downloaded from govinfo bulk data (committed under test
 * resources). Deterministic and self-contained — runs in normal CI. Asserts the parser's invariants on real markup:
 * sections extracted, ordering, non-empty units, no text loss, quoted-block not over-split, TITLE hierarchy.
 */
class GpoBillXmlValidationSpec extends AnyFlatSpec with Matchers {

  private val fixtures = List(
    "BILLS-119hr23ih.xml",
    "BILLS-119hr29ih.xml",
    "BILLS-119hr4457ih.xml",
    "BILLS-119sres100is.xml",
    "BILLS-119hres100ih.xml",
    "BILLS-119hjres1ih.xml",
  )

  private def load(name: String): String = {
    val src = Source.fromResource(s"xml/$name")
    try src.mkString
    finally src.close()
  }

  private def charFreq(s: String): Map[Char, Int] =
    s.filterNot(_.isWhitespace).groupBy(identity).view.mapValues(_.size).toMap

  // The legislative content the parser must fully retain: <form> preamble + body text.
  // Strip the DOCTYPE so this independent check doesn't try to fetch the external bill.dtd.
  private def formAndBodyText(xml: String): String = {
    val root = XML.loadString(xml.replaceAll("(?s)<!DOCTYPE[^>]*>", ""))
    val body = (root \ "legis-body") ++ (root \ "resolution-body")
    ((root \ "form").text + body.text).trim.replaceAll("\\s+", " ")
  }

  private def sectionsOf(name: String): List[ParsedSection] =
    GpoBillXmlSectionParser.parse(load(name)) match {
      case Right(s) => s
      case Left(f)  => fail(s"$name: expected sections, got Left(${f.message})")
    }

  "GpoBillXmlSectionParser" should "structure every real fixture with no text loss" in {
    fixtures.foreach { name =>
      val sections = sectionsOf(name)
      withClue(s"$name: ") {
        sections should not be empty
        sections.map(_.sectionIndex) shouldBe sections.indices.toList
        sections.foreach(s => s.content.trim should not be empty)
        val produced = charFreq(sections.map(_.content).mkString + sections.flatMap(_.parents).mkString)
        val missing  = charFreq(formAndBodyText(load(name))).filter { case (c, n) => produced.getOrElse(c, 0) < n }
        withClue(s"under-covers chars ${missing.keys.take(10).toList}: ")(missing shouldBe empty)
      }
      val secCount = sections.count(_.kind == SectionKind.Section)
      val parents  = sections.flatMap(_.parents).distinct.size
      println(f"$name%-26s -> ${sections.size}%3d units, $secCount%3d sections, $parents%2d distinct parents")
    }
  }

  it should "split the flat bill hr23 into sections 1..5 with no hierarchy" in {
    val secs = sectionsOf("BILLS-119hr23ih.xml").filter(_.kind == SectionKind.Section)
    secs.flatMap(_.sectionIdentifier) shouldBe List("1", "2", "3", "4", "5")
    secs.flatMap(_.parents) shouldBe Nil
  }

  it should "attach TITLE parents to title-nested sections of hr4457 (top-level SEC 1 has none)" in {
    val secs       = sectionsOf("BILLS-119hr4457ih.xml").filter(_.kind == SectionKind.Section)
    val withParent = secs.filter(_.parents.nonEmpty)
    withParent should not be empty
    withParent.flatMap(_.parents.headOption).foreach(_ should startWith("TITLE"))
    secs.find(_.sectionIdentifier.contains("1")).map(_.parents) shouldBe Some(Nil)
  }

  it should "keep amendatory quoted-block sections inline (hr29), not as their own units" in {
    val secs = sectionsOf("BILLS-119hr29ih.xml").filter(_.kind == SectionKind.Section)
    secs.exists(_.heading.exists(_.equalsIgnoreCase("Quoted"))) shouldBe false
    println("hr29 real sections: " + secs.flatMap(_.sectionIdentifier).mkString(","))
  }

}
