package com.repcheck.decomposition.text

/**
 * Deterministic section parser. PURE — pass `content` + its `format`, get sections back. The production implementation
 * is [[DefaultSectionParser]].
 */
trait SectionParser {
  def parse(content: String, format: TextFormat): SectionParseResult
}
