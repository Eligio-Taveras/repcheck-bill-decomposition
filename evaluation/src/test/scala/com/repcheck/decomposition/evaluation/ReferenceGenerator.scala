package com.repcheck.decomposition.evaluation

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import cats.effect.unsafe.implicits.global

import io.circe.Printer
import io.circe.syntax._

import com.anthropic.client.okhttp.AnthropicOkHttpClient

import com.repcheck.decomposition.conformance.{ConformanceContract, Corpus}
import com.repcheck.decomposition.evaluation.judge.ConceptJudge
import com.repcheck.decomposition.text.{DefaultSectionParser, ParsedSection, TextFormat}
import com.repcheck.utils.tags.E2ETest

/**
 * Builds the DP-0 REFERENCE gold by asking Claude to group each pilot bill's sections, and emits a human-readable
 * `GOLD_REVIEW.md` so you spot-check the groupings without opening JSON or the bills. E2ETest (calls the Claude API) +
 * forked update gate (needs ANTHROPIC_API_KEY):
 *
 * sbt -Dupdate.gold=true "evaluation/testOnly *ReferenceGenerator -- -n com.repcheck.tags.E2ETest"
 *
 * Cost: one Claude call per pilot bill (~8 short bills). Cancels if the key is absent.
 */
class ReferenceGenerator extends ConformanceContract {

  private val canonical    = Printer.spaces2.copy(sortKeys = true)
  private val parser       = new DefaultSectionParser
  private def updateMode   = sys.props.get("update.gold").contains("true")
  private val SnippetChars = 220

  private def label(s: ParsedSection): String =
    s.heading
      .orElse(s.sectionIdentifier.map(id => s"Sec. $id"))
      .getOrElse(s.kind.toString)

  private def snippet(s: ParsedSection): String =
    s.content.replaceAll("\\s+", " ").trim.take(SnippetChars)

  "the Claude concept judge" should "label the pilot gold and emit GOLD_REVIEW.md" taggedAs E2ETest in {
    assume(updateMode, "run forked with -Dupdate.gold=true to regenerate")
    val apiKey = sys.env.getOrElse("ANTHROPIC_API_KEY", cancel("ANTHROPIC_API_KEY not set"))
    val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()

    val perBill = GoldPilot.versionIds.map { vid =>
      val bill   = Corpus.bills.find(_.versionId == vid).getOrElse(fail(s"$vid not in corpus"))
      val parsed = parser.parse(bill.content, TextFormat.fromFormatType(bill.format))
      val sections =
        parsed.sections.map(s => ConceptJudge.Section(s.sectionIndex, label(s), s.content))
      val groups = ConceptJudge.judge(client, sections).unsafeRunSync()

      val gold = GoldBill(
        versionId = vid,
        billType = bill.billType,
        format = bill.format,
        labelStatus = "llm-judged",
        parserUsed = parsed.parserUsed.toString,
        sections = parsed.sections.map(s =>
          GoldSection(s.sectionIndex, s.sectionIdentifier, s.heading, s.kind.toString, s.content.length)
        ),
        groups = groups,
      )
      writeGold(vid, gold)
      info(s"$vid: ${parsed.sections.size} sections → ${groups.size} concept groups")
      reportSection(bill.billType, vid, parsed, groups)
    }

    writeReport(perBill.mkString("\n"))
    succeed
  }

  private def writeGold(vid: String, gold: GoldBill): Unit = {
    val dir = Paths.get("src", "main", "resources", "gold")
    val _   = Files.createDirectories(dir)
    val _   = Files.write(dir.resolve(s"$vid.json"), canonical.print(gold.asJson).getBytes(StandardCharsets.UTF_8))
  }

  private def reportSection(
    billType: String,
    vid: String,
    parsed: com.repcheck.decomposition.text.SectionParseResult,
    groups: List[GoldGroup],
  ): String = {
    val byIndex = parsed.sections.map(s => s.sectionIndex -> s).toMap
    val header =
      s"## $billType $vid — ${parsed.sections.size} sections → ${groups.size} groups (parser: ${parsed.parserUsed})\n"
    val body = groups
      .map { g =>
        val items = g.sectionIndices
          .flatMap(byIndex.get)
          .map(s => s"- **[${s.sectionIndex}] ${label(s)}** — ${snippet(s)}")
          .mkString("\n")
        s"### ${g.conceptLabel}\n$items"
      }
      .mkString("\n\n")
    s"$header\n$body\n"
  }

  private def writeReport(content: String): Unit = {
    val intro =
      "# DP-0 gold review — Claude-judged concept groupings\n\n" +
        "Each bill's sections grouped by concept (the reference the gate scores against). Skim for obvious mistakes; " +
        "to correct, edit the matching `evaluation/src/main/resources/gold/<versionId>.json` and set `labelStatus` to " +
        "`reviewed-groups`.\n\n"
    val _ = Files.write(Paths.get("GOLD_REVIEW.md"), (intro + content).getBytes(StandardCharsets.UTF_8))
  }

}
