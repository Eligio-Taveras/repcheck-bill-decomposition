package com.repcheck.decomposition.text

/**
 * Expands oversize sections into overlapping embeddable sub-units (O6): windows of `maxTokens` words stepping by
 * `maxTokens - overlap`. Within-limit sections pass through unchanged; token count ≈ whitespace words.
 */
object SubSplitter {

  def split(sections: List[ParsedSection], maxTokens: Int, overlap: Int): List[EmbeddableUnit] =
    sections.flatMap(s => splitOne(s, maxTokens, overlap))

  private def splitOne(section: ParsedSection, maxTokens: Int, overlap: Int): List[EmbeddableUnit] = {
    val words = section.content.split("\\s+").toList.filter(_.nonEmpty)
    if (maxTokens <= 0 || words.sizeIs <= maxTokens) {
      List(EmbeddableUnit(section.sectionIndex, 0, section.sectionIdentifier, section.content))
    } else {
      val step    = math.max(1, maxTokens - math.max(0, overlap))
      val windows = words.sliding(maxTokens, step).toList
      windows.zipWithIndex.map {
        case (window, subIndex) =>
          EmbeddableUnit(section.sectionIndex, subIndex, section.sectionIdentifier, window.mkString(" "))
      }
    }
  }

}
