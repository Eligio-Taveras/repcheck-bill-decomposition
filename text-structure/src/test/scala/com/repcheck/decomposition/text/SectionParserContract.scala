package com.repcheck.decomposition.text

import com.repcheck.decomposition.conformance.{ConformanceContract, Corpus}

/**
 * Trait-level conformance for any [[SectionParser]] over the canonical corpus (§10c#1): the parse is deterministic,
 * total (never throws), and yields ≥1 section for every substantive bill. Every `SectionParser` impl extends this with
 * its instance, so a refactor or a new parser that breaks the contract fails CI.
 */
abstract class SectionParserContract extends ConformanceContract {

  def parser: SectionParser

  private val corpus: List[(Corpus.Bill, TextFormat)] =
    Corpus.bills.map(b => (b, TextFormat.fromFormatType(b.format)))

  "the parser" should "be deterministic — the same (content, format) yields an identical result" in {
    corpus.foreach {
      case (b, fmt) =>
        withClue(s"version ${b.versionId}: ") {
          parser.parse(b.content, fmt) shouldBe parser.parse(b.content, fmt)
        }
    }
  }

  it should "be total — parse every corpus bill without throwing" in {
    corpus.foreach {
      case (b, fmt) =>
        withClue(s"version ${b.versionId} (${b.format}): ") {
          noException should be thrownBy parser.parse(b.content, fmt)
        }
    }
  }

  it should "yield at least one section for every corpus bill" in {
    corpus.foreach {
      case (b, fmt) =>
        withClue(s"version ${b.versionId} (${b.format}): ") {
          parser.parse(b.content, fmt).sections should not be empty
        }
    }
  }

}
