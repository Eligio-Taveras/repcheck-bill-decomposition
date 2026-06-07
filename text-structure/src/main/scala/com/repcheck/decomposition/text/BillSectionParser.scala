package com.repcheck.decomposition.text

import scala.util.matching.Regex

/**
 * Splits a bill on its SECTION/SEC. headings, attaching the enclosing hierarchy as each section's `parents`. Only
 * section markers slice content, so nothing is lost; hierarchy only labels. Takes (first, rest) so the "at least one
 * heading" precondition is explicit.
 */
private[text] object BillSectionParser {

  def parse(content: String, first: Regex.Match, rest: List[Regex.Match]): List[ParsedSection] = {
    val headings  = first :: rest
    val starts    = headings.map(_.start)
    val ends      = starts.drop(1).appended(content.length)
    val preamble  = Preamble.before(content, first.start)
    val hierarchy = HierarchyBreadcrumb.markersIn(content)

    val sections = headings.zip(starts.zip(ends)).zipWithIndex.map {
      case ((m, (start, end)), i) =>
        val identifier = Option(m.group(2)).map(_.trim).filter(_.nonEmpty)
        ParsedSection(
          preamble.size + i,
          identifier,
          None,
          content.substring(start, end).trim,
          SectionKind.Section,
          HierarchyBreadcrumb.before(hierarchy, start),
        )
    }

    preamble ::: sections
  }

}
