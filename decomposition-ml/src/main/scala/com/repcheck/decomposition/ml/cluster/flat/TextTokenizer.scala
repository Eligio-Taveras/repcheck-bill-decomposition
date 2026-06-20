package com.repcheck.decomposition.ml.cluster.flat

import scala.util.matching.Regex

/**
 * Turns a section's raw text into the tokens and statutory citations the affinity features need. Mirrors the Python
 * research pipeline exactly so the shipped models reproduce their trained behavior. Pure and stateless.
 */
object TextTokenizer {

  /** Common legislative boilerplate words ignored during tokenization (identical to the trained set). */
  val Stopwords: Set[String] = Set(
    "the",
    "a",
    "an",
    "and",
    "or",
    "of",
    "to",
    "in",
    "for",
    "on",
    "by",
    "with",
    "as",
    "at",
    "from",
    "is",
    "are",
    "be",
    "shall",
    "may",
    "not",
    "this",
    "that",
    "such",
    "any",
    "section",
    "sec",
    "subsection",
    "paragraph",
    "subparagraph",
    "clause",
    "act",
    "code",
    "title",
    "united",
    "states",
    "public",
    "law",
    "amended",
    "amend",
    "following",
    "under",
    "pursuant",
    "chapter",
    "part",
    "subtitle",
    "provided",
    "including",
    "include",
    "means",
    "term",
    "house",
    "senate",
    "congress",
    "bill",
    "resolution",
    "date",
    "effective",
    "whoever",
    "person",
    "rule",
  )

  private val WordPattern: Regex = "[a-z]{3,}".r
  // Statutory-citation patterns: "12 U.S.C. 1841", "Public Law 117-1", "Clean Air Act of 1970".
  private val UsCodePattern: Regex    = """(\d{1,2})\s*U\.?\s?S\.?\s?C\.?\s*(?:§+\s?)?(\d+[A-Za-z0-9]*)""".r
  private val PublicLawPattern: Regex = """[Pp]ub(?:lic)?\.?\s?L(?:aw)?\.?\s?(\d{1,3}[-–]\d{1,4})""".r
  private val NamedActPattern: Regex  = """([A-Z][A-Za-z']+(?:\s+[A-Z][A-Za-z']+){0,5})\s+Act\s+of\s+(\d{4})""".r

  /**
   * Tokens for one section: lower-cased words of 3+ letters with stopwords removed (the "unigrams"), followed by every
   * adjacent pair of those words joined with "_" (the "bigrams"). Bigrams let the model notice two-word phrases, not
   * just isolated words.
   */
  def tokens(text: String): Vector[String] = {
    val unigrams = WordPattern.findAllIn(text.toLowerCase).toVector.filterNot(Stopwords.contains)
    val bigrams  = unigrams.iterator.sliding(2).withPartial(false).map(pair => s"${pair(0)}_${pair(1)}").toVector
    unigrams ++ bigrams
  }

  /** Normalized statutory citations found in the text, e.g. "usc:12:1841", "pl:117-1", "act:clean air". */
  def citations(text: String): Set[String] = {
    val usCode    = UsCodePattern.findAllMatchIn(text).map(m => s"usc:${m.group(1)}:${m.group(2)}").toSet
    val publicLaw = PublicLawPattern.findAllMatchIn(text).map(m => s"pl:${m.group(1)}").toSet
    val namedAct  = NamedActPattern.findAllMatchIn(text).map(m => s"act:${m.group(1).toLowerCase}").toSet
    usCode ++ publicLaw ++ namedAct
  }

}
