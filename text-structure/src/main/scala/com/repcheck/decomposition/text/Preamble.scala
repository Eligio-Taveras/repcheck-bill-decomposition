package com.repcheck.decomposition.text

/**
 * The lead-in before the first structural marker (header / sponsors / enacting clause / `Whereas` preamble), preserved
 * as a leading `Fallback` section so the parse is lossless. Empty when absent.
 */
private[text] object Preamble {

  def before(content: String, firstStart: Int): List[ParsedSection] = {
    val lead = content.substring(0, firstStart).trim
    if (lead.nonEmpty) List(ParsedSection(0, None, None, lead, SectionKind.Fallback)) else Nil
  }

}
