package com.repcheck.decomposition.ml.cluster.flat

import scala.io.Source
import scala.util.Try

import cats.syntax.either._

import io.circe.parser.{parse => parseJson}
import io.circe.{ACursor, Decoder, Json}

/** All trained artifacts the flat-bill grouper needs, loaded from bundled JSON resources. */
final case class FlatGroupingArtifacts(
  affinityModel: LogisticModel,  // scores "are these two sections the same concept?"
  mergeStopModel: LogisticModel, // scores "should this cluster merge happen?"
  idf: IdfTable,
  topTermCount: Int,         // top-N TF-IDF terms feeding the affinity model's topterm-overlap feature
  mergeStopThreshold: Double,// a merge proceeds only when the merge-stop score reaches this
)

object FlatGroupingArtifacts {
  val AffinityResource      = "flat-grouping/flat-affinity-model-v1.json"
  val MergeStopResource     = "flat-grouping/flat-mergestop-model-v1.json"
  val IdfResource           = "flat-grouping/flat-idf-v1.json"
  val AffinityFeatureCount  = 5
  val MergeStopFeatureCount = 13

  private def readResource(name: String): Either[String, String] =
    Try {
      val source = Source.fromResource(name)
      try source.mkString
      finally source.close()
    }.toEither.leftMap(error => s"resource $name: ${error.getMessage}")

  // Two helpers keep the error-message shape consistent and in one place (no per-field duplication).
  private def parseDocument(json: String, label: String): Either[String, Json] =
    parseJson(json).leftMap(error => s"invalid $label json: ${error.getMessage}")

  private def decodeField[A: Decoder](cursor: ACursor, name: String): Either[String, A] =
    cursor.downField(name).as[A].leftMap(error => s"$name: ${error.getMessage}")

  private def parseLogistic(cursor: ACursor, expectedFeatureCount: Int): Either[String, LogisticModel] =
    for {
      featureNames  <- decodeField[Vector[String]](cursor, "featureNames")
      weights       <- decodeField[Vector[Double]](cursor, "w")
      bias          <- decodeField[Double](cursor, "b")
      featureMeans  <- decodeField[Vector[Double]](cursor, "mu")
      featureStdevs <- decodeField[Vector[Double]](cursor, "sd")
      model         <- LogisticModel.of(featureNames, weights, bias, featureMeans, featureStdevs, expectedFeatureCount)
    } yield model

  private def parseAffinity(json: String): Either[String, (LogisticModel, Int)] =
    for {
      document     <- parseDocument(json, "affinity")
      model        <- parseLogistic(document.hcursor, AffinityFeatureCount)
      topTermCount <- decodeField[Int](document.hcursor, "topTermCount")
    } yield (model, topTermCount)

  private def parseMergeStop(json: String): Either[String, (LogisticModel, Double)] =
    for {
      document  <- parseDocument(json, "merge-stop")
      model     <- parseLogistic(document.hcursor, MergeStopFeatureCount)
      threshold <- decodeField[Double](document.hcursor, "tau")
    } yield (model, threshold)

  private def parseIdf(json: String): Either[String, IdfTable] =
    for {
      document           <- parseDocument(json, "idf")
      corpusSectionCount <- decodeField[Int](document.hcursor, "n")
      termIdf            <- decodeField[Map[String, Double]](document.hcursor, "idf")
    } yield IdfTable(corpusSectionCount, termIdf)

  def fromResources(
    affinityResource: String,
    mergeStopResource: String,
    idfResource: String,
  ): Either[String, FlatGroupingArtifacts] =
    for {
      affinityJson  <- readResource(affinityResource)
      mergeStopJson <- readResource(mergeStopResource)
      idfJson       <- readResource(idfResource)
      affinity      <- parseAffinity(affinityJson)
      mergeStop     <- parseMergeStop(mergeStopJson)
      idf           <- parseIdf(idfJson)
    } yield FlatGroupingArtifacts(affinity._1, mergeStop._1, idf, affinity._2, mergeStop._2)

  /** Unwrap a load result or fail hard (a packaging invariant). Package-private so the failure path is testable. */
  private[flat] def orThrow(result: Either[String, FlatGroupingArtifacts]): FlatGroupingArtifacts =
    result.valueOr(error => sys.error(s"flat-grouping artifacts unusable: $error"))

  /** Load the committed artifacts; fails hard if a bundled resource is missing or malformed. */
  def bundled: FlatGroupingArtifacts =
    orThrow(fromResources(AffinityResource, MergeStopResource, IdfResource))

}
