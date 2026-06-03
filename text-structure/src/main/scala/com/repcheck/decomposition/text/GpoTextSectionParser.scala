package com.repcheck.decomposition.text

import scala.util.matching.Regex

/**
 * GPO "Formatted Text" parser — THE parser (~96% of bills; plan §5b).
 *
 * Derived from the real corpus (not guessed): the stored text is whitespace-collapsed into one continuous stream (no
 * line breaks), so headings are matched **inline**, and **case-sensitively** — uppercase `SECTION N.` / `SEC. N.` are
 * headings, lowercase `section 101` is a U.S. Code citation (noise) and must NOT split. Section numbers may carry a
 * letter suffix (`SEC. 102B.`).
 *
 * Splitting is lossless: any lead-in before the first heading (bill header, sponsors, enacting clause) is preserved as
 * a leading `Fallback` section; each heading begins a new section running to the next heading. Heading TITLE text is
 * left in the section body (inline extraction of the caps title is unreliable and unnecessary — D14/D16 derive concepts
 * from the body).
 */
object GpoTextSectionParser {

  // Inline, case-sensitive. Not preceded by a letter (so SUBSECTION etc. don't match).
  private val Heading: Regex =
    """(?<![A-Za-z])(SECTION|SEC\.)\s+(\d+[A-Za-z]?)\.""".r

  def parse(content: String): Either[ParseFailure, List[ParsedSection]] =
    if (content.trim.isEmpty) {
      Left(ParseFailure("GPO parse: empty content", None))
    } else {
      val headings = Heading.findAllMatchIn(content).toList
      if (headings.isEmpty) {
        Left(ParseFailure("GPO parse: no inline SECTION/SEC. headings found", None))
      } else {
        Right(buildSections(content, headings))
      }
    }

  private def buildSections(content: String, headings: List[Regex.Match]): List[ParsedSection] = {
    val starts     = headings.map(_.start)
    val firstStart = starts.headOption.getOrElse(0)

    // Lead-in before the first heading (header/sponsors/enacting clause) — kept for no-loss.
    val preamble: List[ParsedSection] = {
      val lead = content.substring(0, firstStart).trim
      if (lead.nonEmpty) List(ParsedSection(0, None, None, lead, SectionKind.Fallback)) else Nil
    }

    val ends = starts.drop(1).appended(content.length)
    val sections = headings.zip(starts.zip(ends)).zipWithIndex.map {
      case ((m, (start, end)), i) =>
        val identifier = Option(m.group(2)).map(_.trim).filter(_.nonEmpty)
        ParsedSection(preamble.size + i, identifier, None, content.substring(start, end).trim, SectionKind.Section)
    }

    preamble ::: sections
  }

}
