package com.repcheck.decomposition.evaluation

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import cats.effect.unsafe.implicits.global

import io.circe.Printer
import io.circe.syntax._

import com.anthropic.client.okhttp.AnthropicOkHttpClient

import com.repcheck.decomposition.conformance.{ConformanceContract, Corpus}
import com.repcheck.decomposition.evaluation.judge.ConceptJudge
import com.repcheck.decomposition.text.{DefaultSectionParser, ParsedSection, SectionParseResult, TextFormat}
import com.repcheck.utils.tags.E2ETest

/**
 * Builds the DP-0 REFERENCE gold via Claude (map-reduce judge — batched section descriptions then one summary+grouping
 * call, so large omnibus bills stay within context) and emits a human-readable `GOLD_REVIEW.md`: per bill a summary,
 * then each concept group with its sections DESCRIBED (not raw-truncated). Review the groupings without opening JSON or
 * the bills. E2ETest (Claude API) + forked update gate (needs ANTHROPIC_API_KEY):
 *
 * sbt -Dupdate.gold=true "evaluation/testOnly *ReferenceGenerator -- -n com.repcheck.tags.E2ETest"
 */
class ReferenceGenerator extends ConformanceContract {

  private val canonical  = Printer.spaces2.copy(sortKeys = true)
  private val parser     = new DefaultSectionParser
  private def updateMode = sys.props.get("update.gold").contains("true")

  private def label(s: ParsedSection): String =
    s.heading.orElse(s.sectionIdentifier.map(id => s"Sec. $id")).getOrElse(s.kind.toString)

  "the Claude concept judge" should "label the pilot gold and emit GOLD_REVIEW.md" taggedAs E2ETest in {
    assume(updateMode, "run forked with -Dupdate.gold=true to regenerate")
    val apiKey = sys.env.getOrElse("ANTHROPIC_API_KEY", cancel("ANTHROPIC_API_KEY not set"))
    val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()

    val perBill = GoldPilot.versionIds.map { vid =>
      val bill   = Corpus.bills.find(_.versionId == vid).getOrElse(fail(s"$vid not in corpus"))
      val parsed = parser.parse(bill.content, TextFormat.fromFormatType(bill.format))
      val sections =
        parsed.sections.map(s => ConceptJudge.Section(s.sectionIndex, label(s), s.content))
      val judged = ConceptJudge.judge(client, sections).unsafeRunSync()

      val gold = GoldBill(
        versionId = vid,
        billType = bill.billType,
        format = bill.format,
        labelStatus = "llm-judged",
        parserUsed = parsed.parserUsed.toString,
        summary = Some(judged.summary),
        sections = parsed.sections.map(s =>
          GoldSection(
            s.sectionIndex,
            s.sectionIdentifier,
            s.heading,
            s.kind.toString,
            s.content.length,
            judged.descriptions.get(s.sectionIndex),
          )
        ),
        groups = judged.groups,
      )
      writeGold(vid, gold)
      info(s"$vid: ${parsed.sections.size} sections → ${judged.groups.size} groups")
      report(bill.billType, vid, parsed, gold)
    }

    writeReport(perBill.mkString("\n"))
    succeed
  }

  private def writeGold(vid: String, gold: GoldBill): Unit = {
    val dir = Paths.get("src", "main", "resources", "gold")
    val _   = Files.createDirectories(dir)
    val _   = Files.write(dir.resolve(s"$vid.json"), canonical.print(gold.asJson).getBytes(StandardCharsets.UTF_8))
  }

  private def report(billType: String, vid: String, parsed: SectionParseResult, gold: GoldBill): String = {
    val descOf  = gold.sections.map(s => s.index -> s.description.getOrElse("")).toMap
    val labelOf = parsed.sections.map(s => s.sectionIndex -> label(s)).toMap
    val header =
      s"## $billType $vid — ${gold.sections.size} sections → ${gold.groups.size} groups (parser: ${gold.parserUsed})\n"
    val summary = s"**Summary:** ${gold.summary.getOrElse("")}\n"
    val body = gold.groups
      .map { g =>
        val items = g.sectionIndices
          .map(i => s"- **[$i] ${labelOf.getOrElse(i, "")}** — ${descOf.getOrElse(i, "")}")
          .mkString("\n")
        s"### ${g.conceptLabel}\n$items"
      }
      .mkString("\n\n")
    s"$header\n$summary\n$body\n"
  }

  private def writeReport(content: String): Unit = {
    val intro =
      "# DP-0 gold review — Claude-judged concept groupings\n\n" +
        "For each bill: a summary, then each concept group with its sections described. Skim to confirm the groupings " +
        "make sense. To correct one, edit `evaluation/src/main/resources/gold/<versionId>.json` and set `labelStatus` " +
        "to `reviewed-groups`.\n\n"
    val _ = Files.write(Paths.get("GOLD_REVIEW.md"), (intro + content).getBytes(StandardCharsets.UTF_8))
  }

}
