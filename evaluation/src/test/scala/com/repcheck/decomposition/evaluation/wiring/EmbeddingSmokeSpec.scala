package com.repcheck.decomposition.evaluation.wiring

import cats.effect.unsafe.implicits.global

import com.repcheck.decomposition.conformance.ConformanceContract
import com.repcheck.decomposition.ml.metrics.EmbeddingMetrics
import com.repcheck.utils.tags.E2ETest

/**
 * Live F3 wiring smoke (DP0-3). E2ETest — excluded from `sbt test` / CI; run on demand against local Ollama:
 *
 * sbt "evaluation/testOnly *EmbeddingSmokeSpec -- -n com.repcheck.tags.E2ETest"
 *
 * Proves the wiring end-to-end and that DP0-2's cosine separates real same-text from different-text vectors. Cancels
 * (not fails) when Ollama is unreachable.
 */
class EmbeddingSmokeSpec extends ConformanceContract {

  private val same1 = "The Secretary shall establish a grant program for rural broadband."
  private val same2 = "The Secretary shall establish a grant program for rural broadband."
  private val other = "Appropriations for the fiscal year 2025 are hereby made available."

  private def toVec(a: Array[Float]): Vector[Double] = a.iterator.map(_.toDouble).toVector

  "the live Ollama embedding service" should "return 1024-dim vectors and separate same-text from different-text" taggedAs E2ETest in {
    val result =
      EmbeddingHarness.resource().use(_.embedBatch(List(same1, same2, other))).attempt.unsafeRunSync()

    result match {
      case Left(err) =>
        cancel(s"Ollama embedding unreachable at ${EmbeddingHarness.OllamaBaseUri} (${err.getMessage})")
      case Right(vectors) =>
        vectors should have size 3
        vectors.foreach(v => v.length shouldBe EmbeddingHarness.Dimension)
        val vecs     = vectors.map(toVec)
        val sameCos  = EmbeddingMetrics.cosine(vecs(0), vecs(1))
        val otherCos = EmbeddingMetrics.cosine(vecs(0), vecs(2))
        withClue(s"same-text cosine $sameCos vs different-text cosine $otherCos: ") {
          sameCos should be >= 0.995
          otherCos should be < sameCos
        }
    }
  }

}
