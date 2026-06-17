package com.repcheck.decomposition.evaluation

import scala.io.{Codec, Source}

import io.circe.parser.decode

/**
 * (a) section boundaries · (b) concept groupings · (c) taxonomy assignments per group — the DP-0 gold ground truth
 * (master §10b) over the shared corpus. Boundaries start machine-DRAFTED from the parser; `groups` (with their taxonomy
 * node ids) are filled by the DP0-4 draft pass + human review. `labelStatus` tracks the stage.
 */
final case class GoldSection(
  index: Int,
  identifier: Option[String],
  heading: Option[String],
  kind: String,
  charLength: Int,
  description: Option[String] = None, // one-line LLM description of what the section does (for review)
) derives io.circe.Codec.AsObject

final case class GoldGroup(
  groupId: String,
  sectionIndices: List[Int],
  conceptLabel: String,
  taxonomyNodeIds: List[String],
) derives io.circe.Codec.AsObject

final case class GoldBill(
  versionId: String,
  billType: String,
  format: String,
  labelStatus: String,
  parserUsed: String,
  summary: Option[String] = None, // 2-3 sentence LLM summary of the whole bill (for review)
  sections: List[GoldSection],
  groups: List[GoldGroup],
) derives io.circe.Codec.AsObject

final case class GoldSet(bills: List[GoldBill])

object GoldSet {

  /**
   * draft-boundaries: parser-drafted, awaiting wiring + review · reviewed-boundaries: boundaries human-confirmed ·
   * reviewed-groups: groupings confirmed · complete: groupings + taxonomy assignments confirmed.
   */
  val LabelStatuses: Set[String] =
    Set("draft-boundaries", "llm-judged", "reviewed-groups", "complete")

  /**
   * Load the pilot gold set: one `gold/<versionId>.json` per bill in [[GoldPilot]] (the single source of truth for the
   * pilot membership — no separate manifest to drift).
   */
  lazy val pilot: GoldSet =
    GoldSet(GoldPilot.versionIds.map { versionId =>
      decode[GoldBill](readResource(s"gold/$versionId.json")).fold(
        err => sys.error(s"gold/$versionId.json failed to decode: ${err.getMessage}"),
        identity,
      )
    })

  private def readResource(path: String): String =
    Option(getClass.getClassLoader.getResourceAsStream(path)) match {
      case Some(is) =>
        try Source.fromInputStream(is)(using Codec.UTF8).mkString
        finally is.close()
      case None => sys.error(s"gold resource not found on classpath: $path")
    }

}
