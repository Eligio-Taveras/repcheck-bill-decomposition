package com.repcheck.decomposition.text

import scala.util.matching.Regex

/**
 * Splits a resolution (no `SEC.` headings). The `Whereas`/header preamble is one context unit; each operative clause is
 * its own unit. Normal resolutions split on numbered clauses `(1) (2) …`; constitutional-amendment proposals split on
 * the proposed amendment's titlecase `Section N.` — scoped to texts that propose an amendment, so Constitution
 * citations (`Article I, section 8`) can't trigger it.
 */
private[text] object ResolutionClauseParser {

  private val Clause: Regex            = """(?<![A-Za-z0-9])\((\d+)\)""".r
  private val AmendmentProposal: Regex = """(?i)amendment to the Constitution""".r
  private val AmendmentSection: Regex  = """(?<![A-Za-z])Section\s+(\d+[A-Za-z]?)\.""".r

  def parse(content: String, resolvedStart: Int): List[ParsedSection] = {
    val preamble  = Preamble.before(content, resolvedStart) // header + Whereas clauses
    val resolving = content.substring(resolvedStart)

    val markers =
      if (AmendmentProposal.findFirstMatchIn(content).isDefined) {
        AmendmentSection.findAllMatchIn(resolving).toList
      } else {
        Clause.findAllMatchIn(resolving).toList
      }

    markers match {
      case Nil   => preamble :+ ParsedSection(preamble.size, None, None, resolving.trim, SectionKind.Section)
      case marks => splitIntoClauses(resolving, marks, preamble)
    }
  }

  /**
   * Split the resolving block at each marker; the lead before the first marker becomes the units' `parents` context
   * (e.g. "Resolved, That the Senate—" or "…the following article is proposed:").
   */
  private def splitIntoClauses(
    resolving: String,
    markers: List[Regex.Match],
    preamble: List[ParsedSection],
  ): List[ParsedSection] = {
    val starts  = markers.map(_.start)
    val lead    = resolving.substring(0, starts.headOption.getOrElse(0)).trim
    val parents = if (lead.nonEmpty) List(lead) else Nil
    val ends    = starts.drop(1).appended(resolving.length)
    val clauses = markers.zip(starts.zip(ends)).zipWithIndex.map {
      case ((m, (start, end)), i) =>
        val identifier = Option(m.group(1)).map(_.trim).filter(_.nonEmpty)
        ParsedSection(
          preamble.size + i,
          identifier,
          None,
          resolving.substring(start, end).trim,
          SectionKind.Section,
          parents,
        )
    }
    preamble ::: clauses
  }

}
