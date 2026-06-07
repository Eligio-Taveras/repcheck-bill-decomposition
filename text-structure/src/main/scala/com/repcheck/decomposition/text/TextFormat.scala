package com.repcheck.decomposition.text

/** The Congress.gov `format_type` dispatch key. */
enum TextFormat {
  case FormattedXml, FormattedText, Pdf, Other
}

object TextFormat {

  def fromFormatType(s: String): TextFormat =
    s.trim.toLowerCase match {
      case "formatted xml"  => FormattedXml
      case "formatted text" => FormattedText
      case "pdf"            => Pdf
      case _                => Other
    }

}
