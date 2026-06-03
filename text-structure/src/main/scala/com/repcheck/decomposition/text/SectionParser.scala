package com.repcheck.decomposition.text

/** Deterministic section parser. PURE — pass `content` + its `format`, get sections back. */
trait SectionParser {
  def parse(content: String, format: TextFormat): SectionParseResult
}

/**
 * The dispatcher (05-DP): XML to USLM, Text to GPO, PDF/Other to fallback. Never throws — a Left or empty result from a
 * specific parser degrades to the single-section fallback.
 */
object DefaultSectionParser extends SectionParser {

  def parse(content: String, format: TextFormat): SectionParseResult =
    format match {
      case TextFormat.FormattedXml =>
        UslmXmlSectionParser.parse(content) match {
          case Right(sections) if sections.nonEmpty => SectionParseResult(sections, ParserKind.UslmXml)
          case _                                    => fallback(content)
        }
      case TextFormat.FormattedText =>
        GpoTextSectionParser.parse(content) match {
          case Right(sections) if sections.nonEmpty => SectionParseResult(sections, ParserKind.GpoText)
          case _                                    => fallback(content)
        }
      case TextFormat.Pdf | TextFormat.Other =>
        fallback(content)
    }

  private def fallback(content: String): SectionParseResult =
    SectionParseResult(FallbackSectionParser.parse(content), ParserKind.Fallback)

}
