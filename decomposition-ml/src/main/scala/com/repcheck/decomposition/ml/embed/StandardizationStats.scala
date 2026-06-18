package com.repcheck.decomposition.ml.embed

import scala.io.Source
import scala.util.Try

import cats.syntax.either._

import io.circe.parser.{parse => parseJson}

/**
 * Per-dimension standardization statistics for the section embeddings — the GLOBAL mean/std the production clusterer
 * applies before HAC. Computed once over the FULL embedded corpus (every `raw_bill_text.embedding`, 425k chunks across
 * 233k bills), NOT per bill: production clusters one bill at a time, so it cannot estimate corpus stats on the fly and
 * must carry a fixed global transform. Validated to reproduce the DP-0 pooled-stats ARI on the 25-bill gold
 * (granularity — DB chunks vs parser sections — proven immaterial: mean-vector cosine 0.944, ARI delta −0.002).
 *
 * Pure data. The canonical artifact ships to GCS (version-pinned); a byte-identical copy is bundled on the classpath as
 * [[StandardizationStats.bundled]] so the jar is self-sufficient (the GCS-with-fallback loader lands with the pipeline
 * module).
 */
final case class StandardizationStats(mean: Vector[Double], std: Vector[Double])

object StandardizationStats {

  val Dim: Int        = 1024
  val BundledResource = "standardization/standardization-stats-v1.json"

  /** Parse + validate the artifact JSON ({ "mean": [..1024..], "std": [..1024..], ... }). */
  def parse(json: String): Either[String, StandardizationStats] =
    for {
      doc  <- parseJson(json).leftMap(e => s"invalid json: ${e.getMessage}")
      mean <- doc.hcursor.downField("mean").as[Vector[Double]].leftMap(e => s"mean: ${e.getMessage}")
      std  <- doc.hcursor.downField("std").as[Vector[Double]].leftMap(e => s"std: ${e.getMessage}")
      _    <- Either.cond(mean.sizeIs == Dim, (), s"mean must be $Dim dims, got ${mean.size.toString}")
      _    <- Either.cond(std.sizeIs == Dim, (), s"std must be $Dim dims, got ${std.size.toString}")
    } yield StandardizationStats(mean, std)

  /** Load + parse a classpath resource (fail-soft to `Left` on a missing/unreadable resource). */
  def fromResource(name: String): Either[String, StandardizationStats] =
    Try {
      val src = Source.fromResource(name)
      try src.mkString
      finally src.close()
    }.toEither.leftMap(e => s"resource $name: ${e.getMessage}").flatMap(parse)

  /** The byte-identical-to-GCS copy bundled in the jar. A load failure is a packaging invariant violation. */
  def bundled: StandardizationStats =
    fromResource(BundledResource).valueOr(e => sys.error(s"bundled standardization stats unusable: $e"))

}
