package com.repcheck.decomposition.text

/** The `format_type` dispatch key. Corpus today (plan §5b): Formatted Text 96.4%, PDF 3.6%, Formatted XML ~0%. */
enum TextFormat {
  case FormattedXml, FormattedText, Pdf, Other
}

object TextFormat {

  /** Map a Congress.gov `format_type` string to the dispatch key. Unknown values to Other. */
  def fromFormatType(s: String): TextFormat =
    s.trim.toLowerCase match {
      case "formatted xml"  => FormattedXml
      case "formatted text" => FormattedText
      case "pdf"            => Pdf
      case _                => Other
    }

}
