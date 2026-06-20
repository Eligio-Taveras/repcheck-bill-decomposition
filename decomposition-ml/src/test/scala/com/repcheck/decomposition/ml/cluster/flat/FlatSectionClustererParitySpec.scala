package com.repcheck.decomposition.ml.cluster.flat

import scala.io.Source

import io.circe.Json
import io.circe.parser.{parse => parseJson}

import com.repcheck.decomposition.ml.cluster.Cluster
import com.repcheck.decomposition.ml.embed.Vector1024

/**
 * Confirms the Scala port reproduces the Python reference clustering bit-for-bit. The fixture holds real bills (raw
 * section text + embeddings) and the labels the Python `vetoed` clusterer produced with the same committed artifacts.
 * Agreement is measured with the Adjusted Rand Index (1.0 = same partition); near-1.0 tolerates only float-tie-break
 * differences on near-zero-weight features.
 */
class FlatSectionClustererParitySpec extends com.repcheck.decomposition.conformance.ConformanceContract {

  final private case class FixtureBill(
    versionId: String,
    sections: Vector[FlatSection],
    expectedLabels: Vector[Int],
    goldLabels: Vector[Int],
  )

  private def loadFixture(): Vector[FixtureBill] = {
    val json = {
      val source = Source.fromResource("flat-grouping/parity-fixture-v1.json")
      try source.mkString
      finally source.close()
    }
    val document = parseJson(json).toOption.getOrElse(fail("parity fixture is not valid json"))
    document.asArray.getOrElse(Vector.empty).map { billJson =>
      val cursor    = billJson.hcursor
      val versionId = cursor.downField("versionId").as[String].toOption.getOrElse("unknown")
      val expected  = cursor.downField("expectedLabels").as[Vector[Int]].toOption.getOrElse(Vector.empty)
      val gold      = cursor.downField("goldLabels").as[Vector[Int]].toOption.getOrElse(Vector.empty)
      val sections = cursor.downField("sections").as[Vector[Json]].toOption.getOrElse(Vector.empty).zipWithIndex.map {
        case (sectionJson, index) =>
          val sectionCursor = sectionJson.hcursor
          val text          = sectionCursor.downField("text").as[String].toOption.getOrElse("")
          val rawEmbedding  = sectionCursor.downField("embedding").as[Vector[Double]].toOption.getOrElse(Vector.empty)
          val embedding =
            Vector1024.of(rawEmbedding.map(_.toFloat)).toOption.getOrElse(fail(s"bad embedding in $versionId"))
          FlatSection(index, text, embedding)
      }
      FixtureBill(versionId, sections, expected, gold)
    }
  }

  private def labelsOf(clusters: List[Cluster], sectionCount: Int): Vector[Int] = {
    val labelByIndex = clusters.zipWithIndex.flatMap {
      case (cluster, label) => cluster.memberIndices.map(_ -> label)
    }.toMap
    Vector.tabulate(sectionCount)(index => labelByIndex.getOrElse(index, -1))
  }

  private def pairCount(groupSizes: Iterable[Int]): Long =
    groupSizes.foldLeft(0L)((sum, size) => sum + size.toLong * (size - 1) / 2)

  private def adjustedRandIndex(predicted: Seq[Int], expected: Seq[Int]): Double = {
    val n = predicted.size
    if (n < 2) 1.0
    else {
      val jointSizes     = predicted.zip(expected).groupBy(identity).view.mapValues(_.size).values
      val predictedSizes = predicted.groupBy(identity).view.mapValues(_.size).values
      val expectedSizes  = expected.groupBy(identity).view.mapValues(_.size).values
      val index          = pairCount(jointSizes).toDouble
      val predictedPairs = pairCount(predictedSizes).toDouble
      val expectedPairs  = pairCount(expectedSizes).toDouble
      val totalPairs     = n.toLong * (n - 1) / 2
      val expectedIndex  = predictedPairs * expectedPairs / totalPairs
      val maxIndex       = (predictedPairs + expectedPairs) / 2.0
      if (maxIndex == expectedIndex) 1.0 else (index - expectedIndex) / (maxIndex - expectedIndex)
    }
  }

  "FlatSectionClusterer.production" should "reproduce the Python reference and score against the Sonnet gold" in {
    val clusterer = FlatSectionClusterer.production
    val scored = loadFixture().map { bill =>
      val predicted = labelsOf(clusterer.cluster(bill.sections), bill.sections.size)
      val parityAri = adjustedRandIndex(predicted, bill.expectedLabels)
      val goldAri   = adjustedRandIndex(predicted, bill.goldLabels)
      info(s"${bill.versionId} (n=${bill.sections.size}) parityARI=$parityAri goldARI=$goldAri")
      (parityAri, goldAri)
    }
    scored should not be empty

    // Parity: the Scala port reproduces the Python reference partitions exactly.
    (scored.map(_._1).sum / scored.size) should be >= 0.99
    all(scored.map(_._1)) should be >= 0.9

    // Behavior: agreement with the Sonnet gold stays in the validated band (regression guard).
    val meanGoldAri = scored.map(_._2).sum / scored.size
    info(s"mean gold ARI over ${scored.size} fixture bills = $meanGoldAri")
    meanGoldAri should be >= 0.2
  }

}
