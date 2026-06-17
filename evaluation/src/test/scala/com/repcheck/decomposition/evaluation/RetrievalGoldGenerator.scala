package com.repcheck.decomposition.evaluation

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import cats.effect.unsafe.implicits.global

import io.circe.Printer
import io.circe.syntax._

import com.anthropic.client.okhttp.AnthropicOkHttpClient

import com.repcheck.decomposition.conformance.ConformanceContract
import com.repcheck.decomposition.evaluation.judge.QueryJudge
import com.repcheck.utils.tags.E2ETest

/**
 * DP0-6a: builds the retrieval gold — one LLM-phrased citizen query per Claude reference concept across the 25-bill
 * gold, with each query's relevance set = that concept's own sections. Emits `retrieval-gold.json` (the gate input) and
 * a human-readable `RETRIEVAL_GOLD_REVIEW.md`. E2ETest (Claude API) + forked update gate (needs ANTHROPIC_API_KEY):
 *
 * sbt -Dupdate.gold=true "evaluation/testOnly *RetrievalGoldGenerator -- -n com.repcheck.tags.E2ETest"
 */
class RetrievalGoldGenerator extends ConformanceContract {

  private val canonical  = Printer.spaces2.copy(sortKeys = true)
  private def updateMode = sys.props.get("update.gold").contains("true")

  "the retrieval query generator" should "build retrieval-gold.json from the reference concepts" taggedAs E2ETest in {
    assume(updateMode, "run forked with -Dupdate.gold=true to regenerate")
    val apiKey = sys.env.getOrElse("ANTHROPIC_API_KEY", cancel("ANTHROPIC_API_KEY not set"))
    val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()

    val queries = GoldSet.pilot.bills.flatMap { bill =>
      val descOf = bill.sections.map(s => s.index -> s.description.getOrElse("")).toMap
      val groups = bill.groups.filterNot(_.conceptLabel.trim.equalsIgnoreCase("ungrouped"))
      val inputs = groups.map(g =>
        QueryJudge.ConceptInput(g.conceptLabel, g.sectionIndices.flatMap(descOf.get).filter(_.nonEmpty))
      )
      val texts = QueryJudge.generate(client, inputs).unsafeRunSync()
      info(s"${bill.versionId}: ${groups.size} queries")
      groups.zip(texts).map {
        case (g, qtext) =>
          RetrievalQuery(
            queryId = s"${bill.versionId}-${g.groupId}",
            text = qtext,
            versionId = bill.versionId,
            conceptLabel = g.conceptLabel,
            relevant = g.sectionIndices.map(i => SectionRef(bill.versionId, i)),
          )
      }
    }

    writeGold(queries)
    writeReview(queries)
    info(s"total queries: ${queries.size}")
    succeed
  }

  private def writeGold(queries: List[RetrievalQuery]): Unit = {
    val dir = Paths.get("src", "main", "resources")
    val _   = Files.createDirectories(dir)
    val _ =
      Files.write(dir.resolve("retrieval-gold.json"), canonical.print(queries.asJson).getBytes(StandardCharsets.UTF_8))
  }

  private def writeReview(queries: List[RetrievalQuery]): Unit = {
    val intro =
      "# DP0-6 retrieval gold review\n\nOne search query per reference concept; **relevant** = the sections that " +
        "query should retrieve. Skim to confirm the queries read like real user interests and target the right " +
        "concept.\n\n"
    val body = queries
      .groupBy(_.versionId)
      .toList
      .sortBy(_._1)
      .map {
        case (vid, qs) =>
          val rows = qs
            .map(q => s"- **${q.conceptLabel}** (${q.relevant.size} sec) → _${q.text}_")
            .mkString("\n")
          s"## $vid — ${qs.size} queries\n$rows\n"
      }
      .mkString("\n")
    val _ = Files.write(Paths.get("RETRIEVAL_GOLD_REVIEW.md"), (intro + body).getBytes(StandardCharsets.UTF_8))
  }

}
