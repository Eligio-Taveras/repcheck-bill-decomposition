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

  /** Load the pilot gold set: `gold/manifest.tsv` (one versionId per row) + one `gold/<versionId>.json` per bill. */
  lazy val pilot: GoldSet = {
    val rows = readResource("gold/manifest.tsv").linesIterator.drop(1).filter(_.nonEmpty).toList
    val bills = rows.map { line =>
      line.split("\t", -1).toList match {
        case versionId :: _ =>
          decode[GoldBill](readResource(s"gold/$versionId.json")).fold(
            err => sys.error(s"gold/$versionId.json failed to decode: ${err.getMessage}"),
            identity,
          )
        case other => sys.error(s"gold manifest row malformed: $other")
      }
    }
    GoldSet(bills)
  }

  private def readResource(path: String): String =
    Option(getClass.getClassLoader.getResourceAsStream(path)) match {
      case Some(is) =>
        try Source.fromInputStream(is)(using Codec.UTF8).mkString
        finally is.close()
      case None => sys.error(s"gold resource not found on classpath: $path")
    }

}
