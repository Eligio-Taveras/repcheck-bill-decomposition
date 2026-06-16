package com.repcheck.decomposition.evaluation

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import io.circe.Printer
import io.circe.syntax._

import com.repcheck.decomposition.conformance.{ConformanceContract, Corpus}
import com.repcheck.decomposition.text.{DefaultSectionParser, TextFormat}

/**
 * Regenerates the DRAFT boundary gold for the 8 pilot bills from the deterministic parser. Run FORKED with
 * `-Dupdate.gold=true` (so cwd is the module dir) to (re)write `src/main/resources/gold/<versionId>.json`; otherwise
 * each case is cancelled. `groups` stay empty here — concept groupings + taxonomy assignments are drafted by DP0-4 and
 * confirmed by human review, never by this generator.
 *
 * sbt -Dupdate.gold=true "evaluation/testOnly *GoldDraftGenerator" (with Test/fork := true)
 */
class GoldDraftGenerator extends ConformanceContract {

  private val canonical           = Printer.spaces2.copy(sortKeys = true)
  private val parser              = new DefaultSectionParser
  private def updateMode: Boolean = sys.props.get("update.gold").contains("true")

  GoldPilot.versionIds.foreach { vid =>
    s"gold draft for $vid" should "be regenerated from the parser" in {
      assume(updateMode, "run forked with -Dupdate.gold=true to regenerate")
      val bill = Corpus.bills.find(_.versionId == vid).getOrElse(fail(s"$vid not in corpus"))
      val res  = parser.parse(bill.content, TextFormat.fromFormatType(bill.format))
      val gold = GoldBill(
        versionId = vid,
        billType = bill.billType,
        format = bill.format,
        labelStatus = "draft-boundaries",
        parserUsed = res.parserUsed.toString,
        sections = res.sections.map(s =>
          GoldSection(s.sectionIndex, s.sectionIdentifier, s.heading, s.kind.toString, s.content.length)
        ),
        groups = Nil,
      )
      val dir = Paths.get("src", "main", "resources", "gold")
      val _   = Files.createDirectories(dir)
      val _   = Files.write(dir.resolve(s"$vid.json"), canonical.print(gold.asJson).getBytes(StandardCharsets.UTF_8))
      succeed
    }
  }

}
