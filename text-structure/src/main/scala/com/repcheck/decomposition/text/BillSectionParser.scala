package com.repcheck.decomposition.text

import scala.util.matching.Regex

/**
 * Splits a GPO bill on its `SECTION`/`SEC.` headings. Only section markers slice content (so every character lands in
 * the preamble or a section — lossless); the enclosing `TITLE`/`Subtitle`/`PART`/ `DIVISION`/`CHAPTER` hierarchy is
 * attached to each section as a `parents` breadcrumb via [[HierarchyBreadcrumb]] (sections stay the unit).
 */
private[text] object BillSectionParser {

  /** @param first the first section heading — makes the "at least one heading" precondition explicit. */
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
