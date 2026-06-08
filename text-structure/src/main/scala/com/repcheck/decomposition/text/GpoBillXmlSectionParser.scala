package com.repcheck.decomposition.text

import javax.xml.parsers.SAXParserFactory

import scala.util.control.NonFatal
import scala.xml.{Elem, Node, NodeSeq, XML}

/**
 * Parses GPO legislative XML (`bill.dtd` / `res.dtd`, the format Congress.gov actually serves — NOT USLM). A bill
 * `<section>` carries `<enum>` (the number), `<header>` (the heading) and `<text>`; TITLE/Subtitle/PART/DIVISION/
 * CHAPTER containers wrap sections and contribute a `parents` breadcrumb. `<quoted-block>` sections are amendatory text
 * quoting *other* law — they are never split out; their text stays inside the enclosing bill section, so nothing is
 * lost. The `<form>` block (sponsors, official title) becomes a leading `Fallback` preamble, mirroring the text parser.
 */
object GpoBillXmlSectionParser {

  private val Containers = Set("title", "subtitle", "part", "division", "chapter")

  def parse(content: String): Either[ParseFailure, List[ParsedSection]] =
    if (content.trim.isEmpty) {
      Left(ParseFailure("XML: empty content", None))
    } else {
      loadXml(content).flatMap { root =>
        collectSections(bodyOf(root), Nil) match {
          case Nil      => Left(ParseFailure("XML: no section elements", None))
          case sections => Right(reindex(preambleOf(root) ::: sections))
        }
      }
    }

  /** Whole-document plain text — lets the dispatcher run the text parser on tag-stripped XML. */
  def extractPlainText(content: String): Either[ParseFailure, String] =
    loadXml(content).map(root => collapseWhitespace(root.text))

  // Real GPO bills declare <!DOCTYPE bill SYSTEM "bill.dtd"> — never fetch that external DTD (no network, and we
  // don't validate), and disable external entities (XXE-safe).
  private val parserFactory: SAXParserFactory = {
    val f = SAXParserFactory.newInstance()
    f.setNamespaceAware(false)
    f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    f.setFeature("http://xml.org/sax/features/external-general-entities", false)
    f.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    f
  }

  private def loadXml(content: String): Either[ParseFailure, Elem] =
    try Right(XML.withSAXParser(parserFactory.newSAXParser()).loadString(content))
    catch { case NonFatal(t) => Left(ParseFailure("XML: malformed XML", Some(t))) }

  private def bodyOf(root: Elem): NodeSeq =
    (root \ "legis-body") ++ (root \ "resolution-body")

  // Gather bill <section> nodes in document order. Recurse into hierarchy containers (accumulating their enum+header
  // as a parents label) and through structural wrappers; on a <section>, take it whole and stop — so an amendatory
  // <quoted-block><section> nested inside stays as content and is never split into its own unit.
  private def collectSections(nodes: NodeSeq, parents: List[String]): List[ParsedSection] =
    nodes.toList.flatMap { n =>
      n.label match {
        case "section"                       => List(toSection(n, parents))
        case lbl if Containers.contains(lbl) => collectSections(n.child, parents :+ containerLabel(n, lbl))
        case _                               => collectSections(n.child, parents)
      }
    }

  private def toSection(n: Node, parents: List[String]): ParsedSection =
    ParsedSection(
      sectionIndex = 0, // assigned by reindex
      sectionIdentifier = childText(n, "enum").map(_.stripSuffix(".").trim).filter(_.nonEmpty),
      heading = childText(n, "header"),
      content = collapseWhitespace(n.text),
      kind = SectionKind.Section,
      parents = parents,
    )

  private def containerLabel(n: Node, label: String): String =
    collapseWhitespace(
      s"${label.toUpperCase} ${childText(n, "enum").getOrElse("")} ${childText(n, "header").getOrElse("")}"
    )

  private def preambleOf(root: Elem): List[ParsedSection] = {
    val formText = collapseWhitespace((root \ "form").text)
    if (formText.nonEmpty) List(ParsedSection(0, None, None, formText, SectionKind.Fallback)) else Nil
  }

  private def reindex(sections: List[ParsedSection]): List[ParsedSection] =
    sections.zipWithIndex.map { case (s, i) => s.copy(sectionIndex = i) }

  private def childText(n: Node, label: String): Option[String] =
    (n \ label).headOption.map(c => collapseWhitespace(c.text)).filter(_.nonEmpty)

  private def collapseWhitespace(s: String): String = s.trim.replaceAll("\\s+", " ")
}
