package com.repcheck.decomposition.text

/**
 * Text before the first marker (header/sponsors/enacting clause/Whereas), kept as a leading `Fallback` section for
 * losslessness. Empty when absent.
 */
private[text] object Preamble {

  def before(content: String, firstStart: Int): List[ParsedSection] = {
    val lead = content.substring(0, firstStart).trim
    if (lead.nonEmpty) List(ParsedSection(0, None, None, lead, SectionKind.Fallback)) else Nil
  }

}
