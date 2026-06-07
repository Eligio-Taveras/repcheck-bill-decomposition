package com.repcheck.decomposition.text

import scala.util.control.NonFatal
import scala.xml.{Elem, Node, XML}

/**
 * USLM XML parser — forward-looking (near-zero in the corpus today). Extracts each `<section>` (`<num>`→id,
 * `<heading>`→heading, text→content).
 */
object UslmXmlSectionParser {

  def parse(content: String): Either[ParseFailure, List[ParsedSection]] =
    if (content.trim.isEmpty) {
      Left(ParseFailure("USLM: empty content", None))
    } else {
      loadXml(content).flatMap { root =>
        val sections = (root \\ "section").toList
        if (sections.isEmpty) {
          Left(ParseFailure("USLM: no section elements", None))
        } else {
          Right(sections.zipWithIndex.map { case (node, idx) => toSection(node, idx) })
        }
      }
    }

  /** Whole-document plain text — lets the dispatcher run the text parser on section-less XML. */
  def extractPlainText(content: String): Either[ParseFailure, String] =
    loadXml(content).map(root => collapseWhitespace(root.text))

  private def loadXml(content: String): Either[ParseFailure, Elem] =
    try
      Right(XML.loadString(content))
    catch {
      case NonFatal(t) => Left(ParseFailure("USLM: malformed XML", Some(t)))
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
