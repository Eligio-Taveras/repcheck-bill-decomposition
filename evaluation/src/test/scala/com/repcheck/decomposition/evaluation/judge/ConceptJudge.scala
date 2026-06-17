package com.repcheck.decomposition.evaluation.judge

import scala.jdk.CollectionConverters._

import cats.effect.IO

import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.MessageCreateParams

import com.repcheck.decomposition.evaluation.GoldGroup

/**
 * The DP-0 REFERENCE generator: a strong LLM (Claude) reads a bill's sections and judges which sections share a
 * concept, naming each group. This is the silver "gold" the gate scores against — independent of the pipeline's
 * embed+cluster mechanism, so the comparison is meaningful. The user reviews a readable report, never the bills. Prompt
 * building, response parsing, and partition reconciliation are pure (unit-tested without the API); [[judge]] makes the
 * call.
 */
object ConceptJudge {

  final case class Section(index: Int, label: String, text: String)

  /** circe-decoded shape of the model's reply. */
  final case class JudgeGroup(concept: String, sectionIndices: List[Int]) derives io.circe.Codec.AsObject
  final case class JudgeResponse(groups: List[JudgeGroup]) derives io.circe.Codec.AsObject

  val Model: String           = "claude-sonnet-4-6"
  private val MaxSnippetChars = 1200
  private val MaxOutputTokens = 2048L

  def buildPrompt(sections: List[Section]): String = {
    val body = sections
      .map(s => s"[${s.index}] ${s.label}\n${s.text.take(MaxSnippetChars).trim}")
      .mkString("\n\n")
    s"""You are an expert legislative analyst. Below are the numbered sections of a SINGLE bill. Group the sections by
       |shared concept — sections addressing the same policy mechanism, program, funding stream, or subject belong in the
       |same group. Give each group a short 2-5 word concept name. Prefer a few meaningful groups over many singletons,
       |but keep genuinely distinct concepts apart.
       |
       |Return ONLY a JSON object, no prose, no code fences:
       |{"groups":[{"concept":"<short name>","sectionIndices":[<ints>]}]}
       |
       |Every section index from 0 to ${sections.size - 1} must appear in exactly one group.
       |
       |Sections:
       |$body""".stripMargin
  }

  /** Extract the JSON object from a possibly-fenced reply and decode it. */
  def parse(raw: String): Either[String, JudgeResponse] = {
    val start = raw.indexOf('{')
    val end   = raw.lastIndexOf('}')
    if (start < 0 || end < start) Left(s"no JSON object found in reply: ${raw.take(120)}")
    else io.circe.parser.decode[JudgeResponse](raw.substring(start, end + 1)).left.map(_.getMessage)
  }

  /**
   * Force a clean partition of 0..n-1: first group to claim a section wins; any sections the model dropped become a
   * trailing "ungrouped" group. Group ids are renumbered by position.
   */
  def reconcile(response: JudgeResponse, n: Int): List[GoldGroup] = {
    val (assigned, used) =
      response.groups.foldLeft((List.empty[GoldGroup], Set.empty[Int])) {
        case ((acc, taken), g) =>
          val fresh = g.sectionIndices.filter(i => i >= 0 && i < n).distinct.filterNot(taken.contains)
          if (fresh.isEmpty) (acc, taken)
          else (acc :+ GoldGroup(s"g${acc.size}", fresh.sorted, g.concept.trim, Nil), taken ++ fresh)
      }
    val leftover = (0 until n).filterNot(used.contains).toList
    if (leftover.isEmpty) assigned
    else assigned :+ GoldGroup(s"g${assigned.size}", leftover, "ungrouped", Nil)
  }

  def judge(client: AnthropicClient, sections: List[Section]): IO[List[GoldGroup]] =
    IO.blocking {
      val params = MessageCreateParams
        .builder()
        .model(Model)
        .maxTokens(MaxOutputTokens)
        .addUserMessage(buildPrompt(sections))
        .build()
      client
        .messages()
        .create(params)
        .content()
        .asScala
        .collect { case b if b.isText => b.asText().text() }
        .mkString("\n")
    }.flatMap(raw => IO.fromEither(parse(raw).left.map(m => new RuntimeException(s"judge parse failed: $m"))))
      .map(reconcile(_, sections.size))

}
