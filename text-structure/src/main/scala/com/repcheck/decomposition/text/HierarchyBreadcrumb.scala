package com.repcheck.decomposition.text

import scala.util.matching.Regex

/**
 * Tracks the `TITLE`/`Subtitle`/`PART`/`DIVISION`/`CHAPTER` hierarchy above the section level and yields the enclosing
 * breadcrumb labels for a position. Uppercase keywords only (titlecase forms are tables-of-contents / citations);
 * numbering is a single token (roman | arabic | single letter) so prose like `PART OF` can't match. Hierarchy
 * contributes labels only — it never slices content.
 */
private[text] object HierarchyBreadcrumb {

  private val Marker: Regex =
    """(?<![A-Za-z])(DIVISION\s+(?:[IVXLC]+|[0-9]+|[A-Z])|TITLE\s+(?:[IVXLC]+|[0-9]+)|Subtitle\s+[A-Z]|PART\s+(?:[IVXLC]+|[0-9]+|[A-Z])|CHAPTER\s+(?:[IVXLC]+|[0-9]+|[A-Z]))""".r

  /** (start, nestingLevel, shortLabel) for every hierarchy marker, in document order. */
  def markersIn(content: String): List[(Int, Int, String)] =
    Marker.findAllMatchIn(content).toList.map(m => (m.start, nestingLevelOf(m.group(1)), m.group(1).trim))

  /** Labels in effect just before `pos` (outermost first); deeper levels cleared by a newer same/shallower one. */
  def before(markers: List[(Int, Int, String)], pos: Int): List[String] = {
    val levels = markers.filter(_._1 < pos).foldLeft(Map.empty[Int, String]) {
      case (acc, (_, level, label)) =>
        acc.filter { case (k, _) => k < level } + (level -> label)
    }
    levels.toList.sortBy(_._1).map(_._2)
  }

  /** Nesting order: DIVISION ⊃ TITLE ⊃ Subtitle ⊃ PART ⊃ CHAPTER. */
  def nestingLevelOf(marker: String): Int =
    if (marker.startsWith("DIVISION")) { 0 }
    else if (marker.startsWith("TITLE")) { 1 }
    else if (marker.startsWith("Subtitle")) { 2 }
    else if (marker.startsWith("PART")) { 3 }
    else { 4 } // CHAPTER

}
