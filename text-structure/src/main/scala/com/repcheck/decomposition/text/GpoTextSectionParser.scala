package com.repcheck.decomposition.text

import scala.util.matching.Regex

/**
 * Parses GPO Formatted Text for all measure types: bills split on uppercase SECTION/SEC. ([[BillSectionParser]]),
 * resolutions on a `Resolved` clause ([[ResolutionClauseParser]]). Case-sensitive — lowercase `section 101` is a
 * citation, not a heading.
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
