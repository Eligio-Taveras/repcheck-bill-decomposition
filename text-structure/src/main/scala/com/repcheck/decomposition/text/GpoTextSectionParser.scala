package com.repcheck.decomposition.text

import scala.util.matching.Regex

/**
 * GPO "Formatted Text" parser — THE parser for all measure types (bills AND resolutions; ~96% of the corpus is this
 * format). They share one whitespace-collapsed format and differ only in internal structure, so one parser handles
 * both, choosing a strategy from the content:
 *
 *   1. **Sections** (bills) — uppercase `SECTION N.` / `SEC. N.` matched inline + case-sensitively (lowercase `section
 *      101` is a U.S. Code citation, NOT a heading). Numbers may carry a letter suffix (`SEC. 102B.`).
 *      `TITLE`/`Subtitle`/`PART`/`DIVISION` hierarchy is tracked and attached to each section as a `parents` breadcrumb
 *      (sections stay the unit). 2. **Resolutions** (no `SEC.`) — split on `Resolved, That …`: the `Whereas`/header
 *      preamble is one context unit (`Fallback`), each numbered resolving clause is its own unit (the `Resolved, That`
 *      lead carried in `parents`); a single resolving statement collapses to one unit.
 *
 * Lossless: lead-in before the first marker is preserved. Returns `Left` only when there are neither section headings
 * nor a `Resolved` clause — the dispatcher then degrades to a single-section fallback.
 */
object GpoTextSectionParser {

  private val Section: Regex = """(?<![A-Za-z])(SECTION|SEC\.)\s+(\d+[A-Za-z]?)\.""".r

  // Uppercase keywords only (titlecase "Title"/"Chapter" are TOC/citations, like "Sec." — excluded).
  // Numbering is a single token (roman | arabic | single letter) so prose like "PART OF" can't match.
  private val Hierarchy: Regex =
    """(?<![A-Za-z])(DIVISION\s+(?:[IVXLC]+|[0-9]+|[A-Z])|TITLE\s+(?:[IVXLC]+|[0-9]+)|Subtitle\s+[A-Z]|PART\s+(?:[IVXLC]+|[0-9]+|[A-Z])|CHAPTER\s+(?:[IVXLC]+|[0-9]+|[A-Z]))""".r

  private val Resolved: Regex = """(?<![A-Za-z])Resolved""".r
  private val Clause: Regex   = """(?<![A-Za-z0-9])\((\d+)\)""".r
  // Constitutional-amendment joint resolutions: the proposed amendment's units are titlecase
  // "Section N." (lowercase "section 8" is a Constitution citation). Only applied when the text
  // actually proposes an amendment, so citations like "Article I, section 8" can't trigger it.
  private val AmendmentProposal: Regex = """(?i)amendment to the Constitution""".r
  private val AmendmentSection: Regex  = """(?<![A-Za-z])Section\s+(\d+[A-Za-z]?)\.""".r

  def parse(content: String): Either[ParseFailure, List[ParsedSection]] =
    if (content.trim.isEmpty) {
      Left(ParseFailure("GPO parse: empty content", None))
    } else {
      val sectionMatches = Section.findAllMatchIn(content).toList
      if (sectionMatches.nonEmpty) {
        Right(buildSections(content, sectionMatches)) // bill: split on sections
      } else {
        Resolved.findFirstMatchIn(content) match {
          case Some(rm) => Right(buildResolution(content, rm.start)) // resolution: split on clauses
          case None     => Left(ParseFailure("GPO parse: no SECTION/SEC. headings and no Resolved clause", None))
        }
      }
    }

  // ── bill / section strategy ────────────────────────────────────────────────────────────────────

  // (start, nestingLevel, shortLabel) — e.g. (1234, 1, "TITLE I").
  private type HierarchyMarker = (Int, Int, String)

  private def buildSections(content: String, sectionMatches: List[Regex.Match]): List[ParsedSection] = {
    val starts     = sectionMatches.map(_.start)
    val firstStart = starts.headOption.getOrElse(0)
    val preamble   = leadIn(content, firstStart)
    val ends       = starts.drop(1).appended(content.length)

    // Hierarchy markers contribute only a short breadcrumb LABEL ("TITLE I") to each section's
    // `parents`. They do NOT slice content — only SECTION markers do — so every character lands in
    // the preamble or a section and no text is ever lost (the title's name stays in section text).
    val hierarchy: List[HierarchyMarker] =
      Hierarchy.findAllMatchIn(content).toList.map(m => (m.start, levelOf(m.group(1)), m.group(1).trim))

    val sections = sectionMatches.zip(starts.zip(ends)).zipWithIndex.map {
      case ((m, (start, end)), i) =>
        val id = Option(m.group(2)).map(_.trim).filter(_.nonEmpty)
        ParsedSection(
          preamble.size + i,
          id,
          None,
          content.substring(start, end).trim,
          SectionKind.Section,
          parentsAt(hierarchy, start),
        )
    }

    preamble ::: sections
  }

  /** Hierarchy labels in effect just before `pos` (outermost first); deeper levels cleared by newer ones. */
  private def parentsAt(hierarchy: List[HierarchyMarker], pos: Int): List[String] = {
    val levels = hierarchy.filter(_._1 < pos).foldLeft(Map.empty[Int, String]) {
      case (acc, (_, level, label)) =>
        acc.filter { case (k, _) => k < level } + (level -> label)
    }
    levels.toList.sortBy(_._1).map(_._2)
  }

  /** Nesting order: DIVISION ⊃ TITLE ⊃ Subtitle ⊃ PART ⊃ CHAPTER. */
  private def levelOf(marker: String): Int =
    if (marker.startsWith("DIVISION")) { 0 }
    else if (marker.startsWith("TITLE")) { 1 }
    else if (marker.startsWith("Subtitle")) { 2 }
    else if (marker.startsWith("PART")) { 3 }
    else { 4 } // CHAPTER

  // ── resolution strategy ────────────────────────────────────────────────────────────────────────

  private def buildResolution(content: String, resolvedStart: Int): List[ParsedSection] = {
    val preamble  = leadIn(content, resolvedStart) // header + Whereas clauses
    val resolving = content.substring(resolvedStart)

    // A constitutional-amendment proposal splits on the proposed amendment's titlecase Section N.;
    // every other resolution splits on its numbered resolving clauses (1) (2) ….
    val markers =
      if (AmendmentProposal.findFirstMatchIn(content).isDefined) {
        AmendmentSection.findAllMatchIn(resolving).toList
      } else {
        Clause.findAllMatchIn(resolving).toList
      }

    if (markers.isEmpty) {
      preamble :+ ParsedSection(preamble.size, None, None, resolving.trim, SectionKind.Section)
    } else {
      clausesFrom(resolving, markers, preamble)
    }
  }

  /**
   * Split a resolving block at each marker; the lead before the first marker becomes the units' `parents` context (e.g.
   * "Resolved, That the Senate—" or "…the following article is proposed:").
   */
  private def clausesFrom(
    resolving: String,
    markers: List[Regex.Match],
    preamble: List[ParsedSection],
  ): List[ParsedSection] = {
    val starts     = markers.map(_.start)
    val firstStart = starts.headOption.getOrElse(0)
    val lead       = resolving.substring(0, firstStart).trim
    val parents    = if (lead.nonEmpty) List(lead) else Nil
    val ends       = starts.drop(1).appended(resolving.length)
    val clauses = markers.zip(starts.zip(ends)).zipWithIndex.map {
      case ((m, (start, end)), i) =>
        val id = Option(m.group(1)).map(_.trim).filter(_.nonEmpty)
        ParsedSection(preamble.size + i, id, None, resolving.substring(start, end).trim, SectionKind.Section, parents)
    }
    preamble ::: clauses
  }

  /**
   * The text before the first marker (header / sponsors / enacting clause / Whereas preamble), preserved as a leading
   * `Fallback` section for no-loss. Empty list if there is none.
   */
  private def leadIn(content: String, firstStart: Int): List[ParsedSection] = {
    val lead = content.substring(0, firstStart).trim
    if (lead.nonEmpty) List(ParsedSection(0, None, None, lead, SectionKind.Fallback)) else Nil
  }

}
