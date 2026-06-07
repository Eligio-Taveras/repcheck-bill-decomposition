package com.repcheck.decomposition.text

/**
 * A logical section of a bill, emitted by a parser. PURE — no I/O, no F[_]. (05-DP, plan §5b)
 *
 * `parents` is the enclosing structural breadcrumb (outermost first), e.g. `List("TITLE I")` for a section nested under
 * a title, or the `Resolved, That` lead for a resolution's clauses. Empty for top-level sections and the preamble.
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

/** What kind of structural unit a [[ParsedSection]] is. */
enum SectionKind {
  case Section, Title, Subtitle, Fallback
}

/** Which parser strategy produced a [[SectionParseResult]]. */
enum ParserKind {
  case UslmXml, GpoText, Fallback
}
