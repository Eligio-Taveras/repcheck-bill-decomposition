package com.repcheck.decomposition.text

/**
 * Single-section fallback — NEVER fails. The whole document becomes one `Fallback` section. Used for PDF/Other formats
 * and when a format-specific parser cannot find structure.
 */
object FallbackSectionParser {

  def parse(content: String): List[ParsedSection] =
    List(ParsedSection(0, None, None, content, SectionKind.Fallback))

}
