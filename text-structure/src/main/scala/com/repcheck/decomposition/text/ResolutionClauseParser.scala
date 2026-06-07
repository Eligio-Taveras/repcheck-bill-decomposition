package com.repcheck.decomposition.text

import scala.util.matching.Regex

/**
 * Splits a resolution: the Whereas/header preamble is one unit, each operative clause another. Normal resolutions split
 * on numbered `(1) (2)`; amendment proposals on titlecase `Section N.`, gated by [[AmendmentProposal]] so Constitution
 * citations can't trigger it.
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

  /** Split at each marker; the lead before the first marker becomes the units' `parents` context. */
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
