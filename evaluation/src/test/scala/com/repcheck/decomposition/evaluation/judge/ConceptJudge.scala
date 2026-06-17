package com.repcheck.decomposition.evaluation.judge

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

import cats.effect.IO
import cats.syntax.all._

import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.MessageCreateParams

import com.repcheck.decomposition.evaluation.GoldGroup

/**
 * The DP-0 REFERENCE generator (map-reduce so large omnibus bills stay within the model's context window):
 *
 *   1. describe (BATCHED, multiple requests): one short description per section, in batches sized to a char budget 2.
 *      summarize + group (one request over the COMPACT descriptions, which fit even for an 80-section bill)
 *
 * The descriptions + bill summary make the groupings reviewable without reading the bill. The grouping is the silver
 * "gold" the gate scores against — independent of the pipeline's embed+cluster mechanism. Batching / prompt / parse /
 * reconcile are pure (unit-tested without the API); the `*Live` methods make the calls.
 */
object ConceptJudge {

  final case class Section(index: Int, label: String, text: String)
  final case class DescribedSection(index: Int, label: String, description: String)
  final case class Judged(summary: String, descriptions: Map[Int, String], groups: List[GoldGroup])

  // wire shapes
  final case class SectionDesc(index: Int, description: String) derives io.circe.Codec.AsObject
  final case class DescribeResponse(descriptions: List[SectionDesc]) derives io.circe.Codec.AsObject
  final case class JudgeGroup(concept: String, sectionIndices: List[Int]) derives io.circe.Codec.AsObject
  final case class SummaryGroups(summary: String, groups: List[JudgeGroup]) derives io.circe.Codec.AsObject

  val Model: String                = "claude-sonnet-4-6"
  private val DescribeSnippetChars = 2500
  private val BatchCharBudget      = 60000
  private val MaxSectionsPerBatch  = 15 // bounds the describe RESPONSE size so it never truncates at the output limit
  private val MaxOutputTokens      = 8192L

  private def cost(s: Section): Int = math.min(s.text.length, DescribeSnippetChars) + s.label.length + 16

  /**
   * Greedy split so each describe request stays within BOTH the input context (`budget` chars) AND a section cap
   * (`maxSections`, which bounds the response so it can't truncate at the output-token limit). A big bill becomes
   * several requests.
   */
  def batch(
    sections: List[Section],
    budget: Int = BatchCharBudget,
    maxSections: Int = MaxSectionsPerBatch,
  ): List[List[Section]] = {
    @tailrec
    def loop(rem: List[Section], cur: List[Section], size: Int, acc: List[List[Section]]): List[List[Section]] =
      rem match {
        case Nil => (if (cur.isEmpty) acc else cur.reverse :: acc).reverse
        case s :: rest =>
          val c = cost(s)
          if (cur.nonEmpty && (size + c > budget || cur.sizeIs >= maxSections))
            loop(rest, List(s), c, cur.reverse :: acc)
          else loop(rest, s :: cur, size + c, acc)
      }
    loop(sections, Nil, 0, Nil)
  }

  def describePrompt(sections: List[Section]): String = {
    val body = sections.map(s => s"[${s.index}] ${s.label}\n${s.text.take(DescribeSnippetChars).trim}").mkString("\n\n")
    s"""For each numbered section of this bill, write ONE plain-English sentence describing what it does. Return ONLY
       |JSON, no prose, no code fences: {"descriptions":[{"index":<int>,"description":"<one sentence>"}]}
       |
       |Sections:
       |$body""".stripMargin
  }

  def groupPrompt(described: List[DescribedSection]): String = {
    val body = described.map(d => s"[${d.index}] ${d.label}: ${d.description}").mkString("\n")
    s"""Below are one-line descriptions of every section of a SINGLE bill.
       |(1) Write a 2-3 sentence plain-English summary of what the whole bill is about.
       |(2) Group the sections by shared concept — sections on the same policy mechanism, program, funding stream, or
       |    subject belong together. Give each group a short 2-5 word concept name. Every section index from 0 to
       |    ${described.size - 1} must appear in exactly one group.
       |
       |Return ONLY JSON, no prose, no code fences:
       |{"summary":"<2-3 sentences>","groups":[{"concept":"<short name>","sectionIndices":[<ints>]}]}
       |
       |Sections:
       |$body""".stripMargin
  }

  /**
   * Extract the first balanced JSON object, ignoring code fences / trailing prose the model sometimes adds. Tracks
   * string literals + escapes so braces inside strings don't confuse the depth count.
   */
  private[judge] def jsonSlice(raw: String): Either[String, String] = {
    val start = raw.indexOf('{')
    @tailrec
    def scan(i: Int, depth: Int, inStr: Boolean, esc: Boolean): Int =
      if (i >= raw.length) -1
      else {
        val c = raw.charAt(i)
        if (inStr) {
          if (esc) scan(i + 1, depth, inStr = true, esc = false)
          else if (c == '\\') scan(i + 1, depth, inStr = true, esc = true)
          else scan(i + 1, depth, inStr = c != '"', esc = false)
        } else
          c match {
            case '"' => scan(i + 1, depth, inStr = true, esc = false)
            case '{' => scan(i + 1, depth + 1, inStr = false, esc = false)
            case '}' => if (depth == 1) i else scan(i + 1, depth - 1, inStr = false, esc = false)
            case _   => scan(i + 1, depth, inStr = false, esc = false)
          }
      }
    if (start < 0) Left(s"no JSON object in reply: ${raw.take(120)}")
    else {
      val end = scan(start, 0, inStr = false, esc = false)
      if (end < 0) Left(s"unbalanced JSON in reply: ${raw.take(120)}") else Right(raw.substring(start, end + 1))
    }
  }

  def parseDescribe(raw: String): Either[String, DescribeResponse] =
    jsonSlice(raw).flatMap(io.circe.parser.decode[DescribeResponse](_).left.map(_.getMessage))

  def parseSummaryGroups(raw: String): Either[String, SummaryGroups] =
    jsonSlice(raw).flatMap(io.circe.parser.decode[SummaryGroups](_).left.map(_.getMessage))

  /**
   * Force a clean partition of 0..n-1: first group to claim a section wins; dropped sections become a trailing
   * "ungrouped" group. Ids renumbered by position.
   */
  def reconcile(groups: List[JudgeGroup], n: Int): List[GoldGroup] = {
    val (assigned, used) =
      groups.foldLeft((List.empty[GoldGroup], Set.empty[Int])) {
        case ((acc, taken), g) =>
          val fresh = g.sectionIndices.filter(i => i >= 0 && i < n).distinct.filterNot(taken.contains)
          if (fresh.isEmpty) (acc, taken)
          else (acc :+ GoldGroup(s"g${acc.size}", fresh.sorted, g.concept.trim, Nil), taken ++ fresh)
      }
    val leftover = (0 until n).filterNot(used.contains).toList
    if (leftover.isEmpty) assigned else assigned :+ GoldGroup(s"g${assigned.size}", leftover, "ungrouped", Nil)
  }

  private def callModel(client: AnthropicClient, prompt: String): IO[String] =
    IO.blocking {
      val params = MessageCreateParams.builder().model(Model).maxTokens(MaxOutputTokens).addUserMessage(prompt).build()
      client
        .messages()
        .create(params)
        .content()
        .asScala
        .collect { case b if b.isText => b.asText().text() }
        .mkString("\n")
    }

  def describeAll(client: AnthropicClient, sections: List[Section]): IO[Map[Int, String]] =
    batch(sections)
      .traverse(b =>
        callModel(client, describePrompt(b))
          .flatMap(raw => IO.fromEither(parseDescribe(raw).left.map(m => new RuntimeException(s"describe parse: $m"))))
      )
      .map(_.flatMap(_.descriptions).map(d => d.index -> d.description).toMap)

  def judge(client: AnthropicClient, sections: List[Section]): IO[Judged] =
    for {
      descMap <- describeAll(client, sections)
      // Complete map: the model occasionally drops a section in a describe batch; fall back to its label so every
      // section has a non-empty description (both for the group prompt and the committed gold / review report).
      described = sections.map(s => DescribedSection(s.index, s.label, descMap.getOrElse(s.index, s.label)))
      raw <- callModel(client, groupPrompt(described))
      sg  <- IO.fromEither(parseSummaryGroups(raw).left.map(m => new RuntimeException(s"group parse: $m")))
    } yield Judged(
      sg.summary.trim,
      described.map(d => d.index -> d.description).toMap,
      reconcile(sg.groups, sections.size),
    )

}
