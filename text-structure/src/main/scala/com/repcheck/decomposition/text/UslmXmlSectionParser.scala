package com.repcheck.decomposition.text

import scala.util.control.NonFatal
import scala.xml.{Elem, Node, XML}

/**
 * USLM XML parser — forward-looking (near-zero in the corpus today; grows as D2's prefer-XML lands).
 *
 * Extracts every `<section>` element, reading its `<num>` as the identifier and `<heading>` as the heading, with the
 * element's text as content. Built correctly but not over-engineered vs GPO (§5b).
 */
object UslmXmlSectionParser {

  def parse(content: String): Either[ParseFailure, List[ParsedSection]] =
    if (content.trim.isEmpty) {
      Left(ParseFailure("USLM parse: empty content", None))
    } else {
      loadXml(content).flatMap { root =>
        val sections = (root \\ "section").toList
        if (sections.isEmpty) {
          Left(ParseFailure("USLM parse: no <section> elements found", None))
        } else {
          Right(sections.zipWithIndex.map { case (node, idx) => toSection(node, idx) })
        }
      }
    }

  /**
   * Whole-document plain text (tags stripped, whitespace collapsed). Lets the dispatcher fall back to the shared text
   * parser when the XML has no USLM `<section>` elements (e.g. a resolution).
   */
  def documentText(content: String): Either[ParseFailure, String] =
    loadXml(content).map(root => collapseWhitespace(root.text))

  private def loadXml(content: String): Either[ParseFailure, Elem] =
    try
      Right(XML.loadString(content))
    catch {
      case NonFatal(t) => Left(ParseFailure("USLM parse: malformed XML", Some(t)))
    }

  private def toSection(node: Node, idx: Int): ParsedSection = {
    val identifier = childText(node, "num")
    val heading    = childText(node, "heading")
    val body       = collapseWhitespace(node.text)
    ParsedSection(idx, identifier, heading, body, SectionKind.Section)
  }

  private def childText(node: Node, label: String): Option[String] =
    (node \ label).headOption.map(n => collapseWhitespace(n.text)).filter(_.nonEmpty)

  private def collapseWhitespace(s: String): String = s.trim.replaceAll("\\s+", " ")
}
