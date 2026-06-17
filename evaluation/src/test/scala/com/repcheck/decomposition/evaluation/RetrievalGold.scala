package com.repcheck.decomposition.evaluation

import scala.io.{Codec, Source}

import io.circe.parser.decode

/**
 * DP0-6 retrieval gold: one query per Claude REFERENCE concept across the corpus. `relevant` is that concept's own
 * sections (the answer key) — so the gate measures, per query, whether a retrieval method surfaces exactly those
 * sections. Built by [[RetrievalGoldGenerator]] (LLM-phrased queries) into `retrieval-gold.json`.
 */
final case class SectionRef(versionId: String, sectionIndex: Int) derives io.circe.Codec.AsObject

final case class RetrievalQuery(
  queryId: String,
  text: String,
  versionId: String,
  conceptLabel: String,
  relevant: List[SectionRef],
) derives io.circe.Codec.AsObject

final case class RetrievalGold(queries: List[RetrievalQuery])

object RetrievalGold {

  lazy val load: RetrievalGold =
    decode[List[RetrievalQuery]](readResource("retrieval-gold.json")).fold(
      err => sys.error(s"retrieval-gold.json failed to decode: ${err.getMessage}"),
      RetrievalGold(_),
    )

  private def readResource(path: String): String =
    Option(getClass.getClassLoader.getResourceAsStream(path)) match {
      case Some(is) =>
        try Source.fromInputStream(is)(using Codec.UTF8).mkString
        finally is.close()
      case None => sys.error(s"retrieval gold resource not found on classpath: $path")
    }

}
