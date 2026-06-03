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
        textPath(content)
      // PDF-format content is ALREADY extracted text for ~99.7% of versions (ingestion ran PDFBox),
      // so parse it as text — NOT excluded. The rare raw-PDF-binary tail is not parseable here: it
      // needs clean bytes re-fetched + PdfTextExtractor at the reader layer; degrade to a single
      // fallback section rather than emit binary garbage.
      case TextFormat.Pdf =>
        if (PdfTextExtractor.looksLikePdfBinary(content)) fallback(content) else textPath(content)
      case TextFormat.Other =>
        fallback(content)
    }

  // One GPO parser for all measure types — it splits bills on SECTION/SEC. and resolutions on
  // Resolved-clauses internally; degrade to a single fallback section only if it finds neither.
  private def textPath(content: String): SectionParseResult =
    GpoTextSectionParser.parse(content) match {
      case Right(sections) if sections.nonEmpty => SectionParseResult(sections, ParserKind.GpoText)
      case _                                    => fallback(content)
    }

  private def fallback(content: String): SectionParseResult =
    SectionParseResult(FallbackSectionParser.parse(content), ParserKind.Fallback)

}
