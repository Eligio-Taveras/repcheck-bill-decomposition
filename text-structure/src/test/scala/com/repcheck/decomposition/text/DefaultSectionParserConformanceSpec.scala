package com.repcheck.decomposition.text

/** The production [[DefaultSectionParser]] must satisfy the [[SectionParserContract]] over the canonical corpus. */
class DefaultSectionParserConformanceSpec extends SectionParserContract {
  def parser: SectionParser = new DefaultSectionParser
}
