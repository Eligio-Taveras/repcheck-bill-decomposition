package com.repcheck.decomposition.text

import scala.io.Source

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * DP gate — property tests over a real-bill corpus fixture (`corpus.tsv`: 16 bills, 2 per bill_type, reassembled from
 * the live AlloyDB `raw_bill_text`). Asserts the parser invariants on real text: determinism, ordering, non-empty
 * units, no-loss, and structural coverage. Self-contained — no DB needed at test time; re-export the fixture via
 * `scripts/mine-bill-headers.sql`-style COPY when the corpus should be refreshed.
 */
class CorpusPropertySpec extends AnyFlatSpec with Matchers {

  final private case class Bill(versionId: String, billType: String, text: String)

  private val corpus: List[Bill] = {
    val src = Source.fromResource("corpus.tsv")
    try
      src
        .getLines()
        .toList
        .flatMap(line =>
          line.split("\t", 3) match {
            case Array(id, bt, txt) if txt.trim.nonEmpty => List(Bill(id, bt, txt))
            case _                                       => Nil
          }
        )
    finally src.close()
  }

  private def charFreq(s: String): Map[Char, Int] =
    s.filterNot(_.isWhitespace).groupBy(identity).view.mapValues(_.size).toMap

  private val parser = new DefaultSectionParser

  private def parse(b: Bill): SectionParseResult =
    parser.parse(b.text, TextFormat.FormattedText)

  "the real-bill corpus fixture" should "cover all 8 measure types" in {
    corpus.map(_.billType).distinct.size shouldBe 8
    corpus.size should be >= 16
  }

  it should "parse every bill deterministically" in {
    corpus.foreach(b => withClue(s"version ${b.versionId}: ")(parse(b) shouldBe parse(b)))
  }

  it should "emit only non-empty units, indexed 0..n-1 in document order" in {
    corpus.foreach { b =>
      val r = parse(b)
      withClue(s"version ${b.versionId}: ") {
        r.sections should not be empty
        r.sections.foreach(s => s.content.trim should not be empty)
        r.sections.map(_.sectionIndex) shouldBe r.sections.indices.toList
      }
    }
  }

  it should "lose no characters — every original character survives in a unit's content or its parents" in {
    corpus.foreach { b =>
      val r        = parse(b)
      val produced = charFreq(r.sections.map(_.content).mkString + r.sections.flatMap(_.parents).mkString)
      val original = charFreq(b.text)
      val missing  = original.filter { case (c, n) => produced.getOrElse(c, 0) < n }
      withClue(s"version ${b.versionId} under-covers chars ${missing.keys.take(8).toList}: ") {
        missing shouldBe empty
      }
    }
  }

  it should "structure (>1 unit) the large majority of bills" in {
    val structured = corpus.count(b => parse(b).sections.size > 1)
    withClue(s"$structured/${corpus.size} structured: ") {
      (structured.toDouble / corpus.size) should be >= 0.75
    }
  }

}
