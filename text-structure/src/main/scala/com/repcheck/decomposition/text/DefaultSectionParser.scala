package com.repcheck.decomposition.text

/** Routes by format to the right parser; an empty or failed parse degrades to a single-section fallback. */
object DefaultSectionParser extends SectionParser {

  def parse(content: String, format: TextFormat): SectionParseResult =
    format match {
      case TextFormat.FormattedXml =>
        UslmXmlSectionParser.parse(content) match {
          case Right(sections) if sections.nonEmpty => SectionParseResult(sections, ParserKind.UslmXml)
          // section-less XML (e.g. a resolution): run the text parser on the stripped XML text
          case _ =>
            UslmXmlSectionParser.extractPlainText(content) match {
              case Right(text) => parseFormattedText(text)
              case Left(_)     => fallback(content)
            }
        }
      case TextFormat.FormattedText =>
        parseFormattedText(content)
      // PDF content is already extracted text for ~99.7% of versions; raw binary needs the reader layer
      case TextFormat.Pdf =>
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
