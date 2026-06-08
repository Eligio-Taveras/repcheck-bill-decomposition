package com.repcheck.decomposition.text

/**
 * A logical section of a bill. `parents` is the enclosing structural breadcrumb (outermost first, e.g. `List("TITLE
 * I")`), or a resolution clause's `Resolved, That` lead; empty at top level.
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

final case class SectionParseResult(sections: List[ParsedSection], parserUsed: ParserKind)

enum SectionKind {
  case Section, Title, Subtitle, Fallback
}

enum ParserKind {
  case GpoXml, GpoText, Fallback
}
