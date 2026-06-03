package com.repcheck.decomposition.text

import scala.util.matching.Regex

/**
 * Parser for resolutions — the ~30% of the corpus (hres, sres, *conres, many *jres) that carry no `SEC.` headings. From
 * the corpus: 100% contain `Resolved, That …`; most simple/concurrent ones have a `Whereas …` preamble; the operative
 * content is the numbered resolving clauses.
 *
 * Decomposition (user decision): keep the `Whereas`/header preamble as ONE context unit (`Fallback`), and split the
 * operative `Resolved, That …` block into its numbered resolving clauses — each a unit whose `parents` carry the
 * `Resolved, That …` lead for context. A resolution with a single resolving statement collapses to one operative unit.
 *
 * Returns `Left` when there is no `Resolved` clause (so the dispatcher falls back to single-section).
 */
object ResolutionParser {

  private val Resolved: Regex = """(?<![A-Za-z])Resolved""".r
  private val Clause: Regex   = """(?<![A-Za-z0-9])\((\d+)\)""".r

  def parse(content: String): Either[ParseFailure, List[ParsedSection]] =
    if (content.trim.isEmpty) {
      Left(ParseFailure("Resolution parse: empty content", None))
    } else {
      Resolved.findFirstMatchIn(content) match {
        case None     => Left(ParseFailure("Resolution parse: no 'Resolved' clause found", None))
        case Some(rm) => Right(buildUnits(content, rm.start))
      }
    }

  private def buildUnits(content: String, resolvedStart: Int): List[ParsedSection] = {
    val preamble: List[ParsedSection] = {
      val lead = content.substring(0, resolvedStart).trim // header + Whereas clauses
      if (lead.nonEmpty) List(ParsedSection(0, None, None, lead, SectionKind.Fallback)) else Nil
    }

    val resolving     = content.substring(resolvedStart)
    val clauseMatches = Clause.findAllMatchIn(resolving).toList

    if (clauseMatches.isEmpty) {
      // Single resolving statement — one operative unit.
      preamble :+ ParsedSection(preamble.size, None, None, resolving.trim, SectionKind.Section)
    } else {
      val starts     = clauseMatches.map(_.start)
      val firstStart = starts.headOption.getOrElse(0)
      val lead       = resolving.substring(0, firstStart).trim // "Resolved, That the Senate—"
      val parents    = if (lead.nonEmpty) List(lead) else Nil
      val ends       = starts.drop(1).appended(resolving.length)
      val clauses = clauseMatches.zip(starts.zip(ends)).zipWithIndex.map {
        case ((m, (start, end)), i) =>
          val id = Option(m.group(1)).map(_.trim).filter(_.nonEmpty)
          ParsedSection(preamble.size + i, id, None, resolving.substring(start, end).trim, SectionKind.Section, parents)
      }
      preamble ::: clauses
    }
  }

}
