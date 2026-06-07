package com.repcheck.decomposition.text

import scala.util.matching.Regex

/**
 * GPO "Formatted Text" parser — THE parser for all measure types (bills AND resolutions; ~96% of the corpus is this
 * format). They share one whitespace-collapsed format and differ only in internal structure, so this single entry point
 * chooses a strategy from the content:
 *
 *   - **bills** — uppercase `SECTION N.` / `SEC. N.` headings → [[BillSectionParser]] (with
 *     `TITLE`/`Subtitle`/`PART`/`DIVISION`/`CHAPTER` hierarchy attached as a `parents` breadcrumb);
 *   - **resolutions** — no `SEC.` but a `Resolved` clause → [[ResolutionClauseParser]].
 *
 * Returns `Left` only when there are neither section headings nor a `Resolved` clause — the dispatcher then degrades to
 * a single-section fallback. Matching is inline + case-sensitive: lowercase `section 101` is a U.S. Code citation, not
 * a heading.
 */
object GpoTextSectionParser {

  private val Section: Regex  = """(?<![A-Za-z])(SECTION|SEC\.)\s+(\d+[A-Za-z]?)\.""".r
  private val Resolved: Regex = """(?<![A-Za-z])Resolved""".r

  def parse(content: String): Either[ParseFailure, List[ParsedSection]] =
    if (content.trim.isEmpty) {
      Left(ParseFailure("GPO: empty content", None))
    } else {
      Section.findAllMatchIn(content).toList match {
        case first :: rest => Right(BillSectionParser.parse(content, first, rest))
        case Nil =>
          Resolved.findFirstMatchIn(content) match {
            case Some(rm) => Right(ResolutionClauseParser.parse(content, rm.start))
            case None     => Left(ParseFailure("GPO: no SECTION/SEC. heading and no Resolved clause", None))
          }
      }
    }

}
