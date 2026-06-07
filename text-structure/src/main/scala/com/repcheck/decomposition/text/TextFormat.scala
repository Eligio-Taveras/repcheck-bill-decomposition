package com.repcheck.decomposition.text

/** The Congress.gov `format_type` dispatch key. */
enum TextFormat {
  case FormattedXml, FormattedText, Pdf, Other
}

object TextFormat {

  // Canonical Congress.gov `format_type` values, exactly as persisted to bill_text_versions.format_type.
  // Authoritative producer: gov-apis FormatType enum in repcheck-data-ingestion (the same three are the
  // only values the API emits). Matched case-insensitively; any other value maps to Other.
  val FormattedXmlLabel  = "Formatted XML"
  val FormattedTextLabel = "Formatted Text"
  val PdfLabel           = "PDF"

  def fromFormatType(s: String): TextFormat = {
    val v = s.trim
    if (v.equalsIgnoreCase(FormattedXmlLabel)) FormattedXml
    else if (v.equalsIgnoreCase(FormattedTextLabel)) FormattedText
    else if (v.equalsIgnoreCase(PdfLabel)) Pdf
    else Other
  }

}
