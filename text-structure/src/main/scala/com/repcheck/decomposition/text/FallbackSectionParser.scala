package com.repcheck.decomposition.text

/** Single-section fallback — never fails; the whole document becomes one `Fallback` section. */
object FallbackSectionParser {

  def parse(content: String): List[ParsedSection] =
    List(ParsedSection(0, None, None, content, SectionKind.Fallback))

}
