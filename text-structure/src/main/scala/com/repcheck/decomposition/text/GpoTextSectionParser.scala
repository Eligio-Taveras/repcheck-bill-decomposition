package com.repcheck.decomposition.text

import scala.util.matching.Regex

/**
 * GPO "Formatted Text" parser — THE parser (~96% of bills; plan §5b).
 *
 * Derived from the real corpus (not guessed): stored text is whitespace-collapsed into one continuous stream (no line
 * breaks), so structural markers are matched **inline** and **case-sensitively** — uppercase `SECTION N.` / `SEC. N.`
 * are headings; lowercase `section 101` is a U.S. Code citation (noise) and must NOT split. Numbers may carry a letter
 * suffix (`SEC. 102B.`).
 *
 * Hierarchy above the section level (`TITLE I`, `Subtitle A`, `PART I`, `DIVISION A` — ~9% of bills) is NOT emitted as
 * its own unit; it is tracked and attached to each section as a `parents` breadcrumb (outermost first), so the SECTION
 * stays the clustering unit while keeping its context.
 *
 * Lossless: lead-in before the first marker (header/sponsors/enacting clause) is preserved as a leading `Fallback`
 * section. Returns `Left` when there are no `SECTION`/`SEC.` headings — the dispatcher then tries the resolution
 * matcher, else single-section fallback.
 */
object GpoTextSectionParser {

  private val Section: Regex =
    """(?<![A-Za-z])(SECTION|SEC\.)\s+(\d+[A-Za-z]?)\.""".r

  private val Hierarchy: Regex =
    """(?<![A-Za-z])(DIVISION\s+[A-Z0-9]+|TITLE\s+[IVXLC]+|Subtitle\s+[A-Z]|PART\s+[IVXLC0-9]+)""".r

  def parse(content: String): Either[ParseFailure, List[ParsedSection]] =
    if (content.trim.isEmpty) {
      Left(ParseFailure("GPO parse: empty content", None))
    } else {
      val sectionMatches = Section.findAllMatchIn(content).toList
      if (sectionMatches.isEmpty) {
        Left(ParseFailure("GPO parse: no inline SECTION/SEC. headings found", None))
      } else {
        Right(buildSections(content, sectionMatches))
      }
    }

  // (start, isSection, sectionId, hierarchyLevel) — level only meaningful for hierarchy markers.
  private type Marker = (Int, Boolean, Option[String], Int)

  private def buildSections(content: String, sectionMatches: List[Regex.Match]): List[ParsedSection] = {
    val sectionMarkers: List[Marker] =
      sectionMatches.map(m => (m.start, true, Option(m.group(2)).map(_.trim).filter(_.nonEmpty), -1))
    val hierarchyMarkers: List[Marker] =
      Hierarchy.findAllMatchIn(content).toList.map(m => (m.start, false, None, levelOf(m.group(1))))

    val markers = (sectionMarkers ++ hierarchyMarkers).sortBy(_._1)
    val starts  = markers.map(_._1)
    val ends    = starts.drop(1).appended(content.length)

    val firstStart = starts.headOption.getOrElse(0)
    val preamble: List[ParsedSection] = {
      val lead = content.substring(0, firstStart).trim
      if (lead.nonEmpty) List(ParsedSection(0, None, None, lead, SectionKind.Fallback)) else Nil
    }

    val walked = markers.zip(ends).foldLeft((Map.empty[Int, String], List.empty[ParsedSection], preamble.size)) {
      case ((levels, acc, idx), ((start, isSection, sectionId, level), end)) =>
        val slice = content.substring(start, end).trim
        if (isSection) {
          val parents = levels.toList.sortBy(_._1).map(_._2)
          (levels, ParsedSection(idx, sectionId, None, slice, SectionKind.Section, parents) :: acc, idx + 1)
        } else {
          // Update the hierarchy stack: drop this level and anything deeper, then set it.
          (levels.filter { case (k, _) => k < level } + (level -> slice), acc, idx)
        }
    }

    preamble ::: walked._2.reverse
  }

  /** Nesting order: DIVISION ⊃ TITLE ⊃ Subtitle ⊃ PART. */
  private def levelOf(marker: String): Int =
    if (marker.startsWith("DIVISION")) { 0 }
    else if (marker.startsWith("TITLE")) { 1 }
    else if (marker.startsWith("Subtitle")) { 2 }
    else { 3 } // PART

}
