package com.repcheck.decomposition.evaluation.judge

import scala.jdk.CollectionConverters._

import cats.effect.IO
import cats.syntax.all._

import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.MessageCreateParams

/**
 * DP0-6a: turns each Claude REFERENCE concept (its label + the one-line descriptions of its sections) into a realistic
 * citizen-style SEARCH QUERY — the input to the retrieval gate. One query per concept; relevance (which sections the
 * query should retrieve) is the concept's own sections, taken from the reference, so no fresh relevance judgement is
 * needed. Batched by concept count so a 67-concept omnibus bill stays within the output-token limit. Pure
 * batch/prompt/parse (unit-tested); `generate` makes the calls.
 */
object QueryJudge {

  final case class ConceptInput(conceptLabel: String, descriptions: List[String])

  final case class QueryItem(index: Int, query: String) derives io.circe.Codec.AsObject
  final case class QueryResponse(queries: List[QueryItem]) derives io.circe.Codec.AsObject

  val Model: String               = "claude-sonnet-4-6"
  private val MaxConceptsPerBatch = 20
  private val MaxDescPerConcept   = 6
  private val MaxOutputTokens     = 8192L

  /** Greedy split by concept count so each request's response can't truncate at the output-token limit. */
  def batch(
    concepts: List[(Int, ConceptInput)],
    maxConcepts: Int = MaxConceptsPerBatch,
  ): List[List[(Int, ConceptInput)]] =
    concepts.grouped(math.max(1, maxConcepts)).toList

  def prompt(batch: List[(Int, ConceptInput)]): String = {
    val body = batch
      .map {
        case (i, c) =>
          val ds = c.descriptions.take(MaxDescPerConcept).map(d => s"  - $d").mkString("\n")
          s"[$i] ${c.conceptLabel}\n$ds"
      }
      .mkString("\n\n")
    s"""For each numbered concept from a bill below, write ONE natural-language search query that a citizen interested in
       |this topic might type to find this part of the legislation. Write it the way a real person would phrase an
       |interest (a topic phrase or question) — NOT as a section title and NOT just repeating the concept name. Ground it
       |in what the sections actually do. Return ONLY JSON, no prose, no code fences:
       |{"queries":[{"index":<int>,"query":"<search query>"}]}
       |
       |Concepts:
       |$body""".stripMargin
  }

  def parse(raw: String): Either[String, QueryResponse] =
    ConceptJudge.jsonSlice(raw).flatMap(io.circe.parser.decode[QueryResponse](_).left.map(_.getMessage))

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

  /**
   * One query per concept, in input order. A concept the model drops falls back to its label (always a valid query).
   */
  def generate(client: AnthropicClient, concepts: List[ConceptInput]): IO[List[String]] = {
    val indexed = concepts.zipWithIndex.map { case (c, i) => (i, c) }
    batch(indexed)
      .traverse(b =>
        callModel(client, prompt(b))
          .flatMap(raw => IO.fromEither(parse(raw).left.map(m => new RuntimeException(s"query parse: $m"))))
      )
      .map { responses =>
        val byIndex = responses.flatMap(_.queries).map(q => q.index -> q.query.trim).toMap
        indexed.map { case (i, c) => byIndex.get(i).filter(_.nonEmpty).getOrElse(c.conceptLabel) }
      }
  }

}
