package com.repcheck.decomposition.text

/**
 * A logical section of a bill, emitted by a parser. PURE — no I/O, no F[_]. (05-DP, plan §5b)
 *
 * `parents` is the enclosing structural breadcrumb (outermost first), e.g. `List("TITLE I—...", "Subtitle A—...")` for
 * a section nested under a title hierarchy, or the `Resolved, That` lead for a resolution's clauses. Empty for
 * top-level sections and the preamble.
 */
final case class ParsedSection(
  sectionIndex: Int,
  sectionIdentifier: Option[String],
  heading: Option[String],
  content: String,
  kind: SectionKind,
  parents: List[String] = Nil,
)

/** An embeddable unit: a section, or one overlapping sub-part of an oversize section (O6). */
final case class EmbeddableUnit(
  sectionIndex: Int,
  subIndex: Int,
  sectionIdentifier: Option[String],
  content: String,
)

/** Output of the dispatching parser: the sections plus which parser produced them. */
final case class SectionParseResult(sections: List[ParsedSection], parserUsed: ParserKind)

enum SectionKind {
  case Section, Title, Subtitle, Fallback
}

enum ParserKind {
  case UslmXml, GpoText, Resolution, Fallback
}

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

/** Flat, unique parse failure (no sealed hierarchy — project convention). */
final case class ParseFailure(message: String, cause: Option[Throwable]) extends Exception(message)
