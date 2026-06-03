package com.repcheck.decomposition.text

import scala.util.matching.Regex

/**
 * GPO "Formatted Text" parser — THE parser (~96% of the live corpus, plan §5b).
 *
 * Splits the document on `SECTION n.` / `SEC. n.` headings, each becoming a [[ParsedSection]] whose content runs to the
 * next heading. Order-preserving and lossless across the matched span.
 *
 * First-cut heading recognition; refined against the real-bill corpus + property tests (the DP gate).
 */
object GpoTextSectionParser {

  // Heading at line start: "SECTION 1." or "SEC. 2A." optionally followed by the heading text.
  private val SectionHeading: Regex =
    """(?m)^[ \t]*(SECTION|SEC\.)[ \t]+(\d+[A-Za-z]?)\.[ \t]*(.*)$""".r

  def parse(content: String): Either[ParseFailure, List[ParsedSection]] =
    if (content.trim.isEmpty) {
      Left(ParseFailure("GPO parse: empty content", None))
    } else {
      val headings = SectionHeading.findAllMatchIn(content).toList
      if (headings.isEmpty) {
        Left(ParseFailure("GPO parse: no SECTION/SEC. headings found", None))
      } else {
        Right(sliceSections(content, headings))
      }
    }

  /** Cut the document at each heading offset; each slice spans [thisStart, nextStart). */
  private def sliceSections(content: String, headings: List[Regex.Match]): List[ParsedSection] = {
    val starts = headings.map(_.start)
    val ends   = starts.drop(1).appended(content.length)
    headings.zip(starts.zip(ends)).zipWithIndex.map {
      case ((m, (start, end)), idx) =>
        val identifier = Option(m.group(2)).map(_.trim).filter(_.nonEmpty)
        val heading    = Option(m.group(3)).map(_.trim).filter(_.nonEmpty)
        val body       = content.substring(start, end).trim
        ParsedSection(idx, identifier, heading, body, SectionKind.Section)
    }
  }

}
