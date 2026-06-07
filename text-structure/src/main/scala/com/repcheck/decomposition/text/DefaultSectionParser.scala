package com.repcheck.decomposition.text

/**
 * The dispatcher (05-DP). `Formatted XML` → USLM `<section>` extraction, falling back to the shared text parser for
 * section-less XML (e.g. resolutions); `Formatted Text` and already-extracted PDF text → the GPO text parser (which
 * handles bills AND resolutions); raw-PDF-binary and `Other` → single-section fallback. Never throws — a `Left` or
 * empty result degrades to the fallback.
 */
object DefaultSectionParser extends SectionParser {

  def parse(content: String, format: TextFormat): SectionParseResult =
    format match {
      case TextFormat.FormattedXml =>
        UslmXmlSectionParser.parse(content) match {
          case Right(sections) if sections.nonEmpty => SectionParseResult(sections, ParserKind.UslmXml)
          case _                                    =>
            // No USLM <section> (e.g. a resolution): reduce the XML to text and run the shared text
            // parser so XML resolutions are handled too.
            UslmXmlSectionParser.extractPlainText(content) match {
              case Right(text) => parseFormattedText(text)
              case Left(_)     => fallback(content)
            }
        }
      case TextFormat.FormattedText =>
        parseFormattedText(content)
      case TextFormat.Pdf =>
        // PDF-format content is already extracted text for ~99.7% of versions; parse it as text.
        // Raw PDF binary isn't parseable here — that needs clean bytes + PdfTextExtractor at the
        // reader layer; degrade to a single fallback section rather than emit binary garbage.
        if (PdfTextExtractor.looksLikePdfBinary(content)) fallback(content) else parseFormattedText(content)
      case TextFormat.Other =>
        fallback(content)
    }

  private def parseFormattedText(content: String): SectionParseResult =
    GpoTextSectionParser.parse(content) match {
      case Right(sections) if sections.nonEmpty => SectionParseResult(sections, ParserKind.GpoText)
      case _                                    => fallback(content)
    }

  private def fallback(content: String): SectionParseResult =
    SectionParseResult(FallbackSectionParser.parse(content), ParserKind.Fallback)

}
