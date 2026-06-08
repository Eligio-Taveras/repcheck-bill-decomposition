package com.repcheck.decomposition.text

import java.nio.charset.StandardCharsets
import java.util.Base64

import scala.io.Source
import scala.util.matching.Regex

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import com.repcheck.decomposition.tags.E2ETest

/**
 * E2E validation: parse real bills exported from the live AlloyDB and assert the parser's correctness signals (no text
 * loss, ordering, non-empty units) while printing a per-bill report. Tagged [[E2ETest]] so it is excluded from `sbt
 * test` / CI and run on demand:
 *
 * sbt "textStructure/testOnly -- -n com.repcheck.tags.E2ETest"
 *
 * Reads a TSV (version_id, bill_type, format_type, base64(text)) from $VALIDATION_TSV (default
 * C:/Temp/validation-corpus.tsv); cancels (not fails) when the corpus is absent. Regenerate the corpus with
 * scripts/export-validation-corpus.sql against the live DB.
 */
class LiveCorpusValidationSpec extends AnyFlatSpec with Matchers {

  final private case class Bill(versionId: String, billType: String, formatType: String, text: String)

  private val Heading: Regex    = """(?<![A-Za-z])(SECTION|SEC\.)\s+(\d+[A-Za-z]?)\.""".r
  private val LeadingNum: Regex = """^(\d+)""".r

  private val tsvPath: String = sys.env.getOrElse("VALIDATION_TSV", "C:/Temp/validation-corpus.tsv")

  private lazy val bills: List[Bill] = {
    val file = new java.io.File(tsvPath)
    if (!file.exists) {
      Nil
    } else {
      val src = Source.fromFile(file, "UTF-8")
      try
        src
          .getLines()
          .toList
          .flatMap(line =>
            line.split("\t", -1).toList match {
              case vid :: bt :: ft :: b64 :: Nil if b64.nonEmpty =>
                List(Bill(vid, bt, ft, new String(Base64.getDecoder.decode(b64), StandardCharsets.UTF_8)))
              case _ => Nil
            }
          )
      finally src.close()
    }
  }

  private def charFreq(s: String): Map[Char, Int] =
    s.filterNot(_.isWhitespace).groupBy(identity).view.mapValues(_.size).toMap

  private def noLoss(original: String, sections: List[ParsedSection]): Boolean = {
    val produced = charFreq(sections.map(_.content).mkString + sections.flatMap(_.parents).mkString)
    charFreq(original).forall { case (c, n) => produced.getOrElse(c, 0) >= n }
  }

  private def ordered(sections: List[ParsedSection]): Boolean =
    sections.map(_.sectionIndex) == sections.indices.toList

  private def allNonEmpty(sections: List[ParsedSection]): Boolean =
    sections.forall(_.content.trim.nonEmpty)

  // Of the SECTION/SEC. units, what fraction actually begin at a heading token (correct slice boundary)?
  private def boundaryAligned(sections: List[ParsedSection]): Option[Double] = {
    val secs = sections.filter(_.kind == SectionKind.Section)
    if (secs.isEmpty) None
    else
      Some(
        secs.count(s => s.content.trim.startsWith("SECTION") || s.content.trim.startsWith("SEC.")).toDouble / secs.size
      )
  }

  // Bills number sections from 1; resolutions/appropriations need not — informational only.
  private def idSequence(sections: List[ParsedSection]): String = {
    val nums =
      sections.flatMap(_.sectionIdentifier).flatMap(id => LeadingNum.findFirstMatchIn(id).map(_.group(1).toInt))
    if (nums.isEmpty) "—"
    else {
      val ok = nums == (1 to nums.size).toList
      s"${nums.minOption.getOrElse(0)}..${nums.maxOption
          .getOrElse(0)} (${if (ok) "contiguous" else "non-1.. : " + nums.mkString(",")})"
    }
  }

  private val parser = new DefaultSectionParser
  private val pad    = (s: String, n: Int) => s.padTo(n, ' ').take(n)

  "the GPO parser" should "extract sections from the live-exported corpus with no text loss" taggedAs E2ETest in {
    if (bills.isEmpty) {
      cancel(
        s"no corpus at $tsvPath — export from AlloyDB (scripts/export-validation-corpus.sql) or set VALIDATION_TSV"
      )
    }

    println(s"\n=== PER-BILL VALIDATION (${bills.size} bills) ===")
    println(
      s"${pad("version", 9)} ${pad("type", 8)} ${pad("format", 14)} ${pad("parser", 8)} ${pad("units", 6)} ${pad(
          "Sec",
          5,
        )} ${pad("hdrs", 5)} ${pad("noLoss", 7)} ${pad("order", 6)} ${pad("nonEmpty", 9)} ${pad("align", 6)} idSeq"
    )

    val results = bills.map { b =>
      val r     = parser.parse(b.text, TextFormat.fromFormatType(b.formatType))
      val secs  = r.sections
      val nl    = noLoss(b.text, secs)
      val align = boundaryAligned(secs).map(d => f"${d * 100}%.0f%%").getOrElse("n/a")
      println(
        s"${pad(b.versionId, 9)} ${pad(b.billType, 8)} ${pad(b.formatType, 14)} ${pad(r.parserUsed.toString, 8)} ${pad(
            secs.size.toString,
            6,
          )} ${pad(secs.count(_.kind == SectionKind.Section).toString, 5)} ${pad(Heading.findAllMatchIn(b.text).size.toString, 5)} ${pad(
            nl.toString,
            7,
          )} ${pad(ordered(secs).toString, 6)} ${pad(allNonEmpty(secs).toString, 9)} ${pad(align, 6)} ${idSequence(secs)}"
      )
      (b, r, nl)
    }

    println("\n=== AGGREGATE ===")
    println(
      "parser used: " + results
        .groupBy(_._2.parserUsed)
        .view
        .mapValues(_.size)
        .toMap
        .map { case (k, v) => s"$k=$v" }
        .mkString(", ")
    )
    val totalUnits = results.map(_._2.sections.size).sum
    println(s"total units extracted: $totalUnits across ${results.size} bills")
    val structured = results.count(_._2.sections.size > 1)
    println(s"structured (>1 unit): $structured/${results.size}")
    val lossy = results.filterNot(_._3).map(_._1.versionId)
    println("noLoss failures: " + (if (lossy.isEmpty) "NONE" else lossy.mkString(", ")))

    results.foreach {
      case (b, _, _) =>
        withClue(s"ordering ${b.versionId}: ")(
          ordered(parser.parse(b.text, TextFormat.fromFormatType(b.formatType)).sections) shouldBe true
        )
    }
    lossy shouldBe empty
  }

}
