package com.repcheck.decomposition.evaluation.metrics

import com.repcheck.decomposition.conformance.ConformanceContract

class RetrievalMetricsSpec extends ConformanceContract {

  private val tol = 1e-9

  "precisionAtK" should "divide relevant hits in the top-k by k" in {
    val ranked   = Seq("a", "b", "c", "d")
    val relevant = Set("a", "c")
    RetrievalMetrics.precisionAtK(ranked, relevant, 2) shouldBe 0.5 +- tol
    RetrievalMetrics.precisionAtK(ranked, relevant, 3) shouldBe (2.0 / 3.0) +- tol
    RetrievalMetrics.precisionAtK(ranked, relevant, 4) shouldBe 0.5 +- tol
  }

  it should "penalize a list shorter than k by dividing by k" in {
    RetrievalMetrics.precisionAtK(Seq("a"), Set("a"), 5) shouldBe 0.2 +- tol
  }

  it should "reject a non-positive k" in {
    an[IllegalArgumentException] should be thrownBy RetrievalMetrics.precisionAtK(Seq("a"), Set("a"), 0)
  }

  "reciprocalRank" should "be 1/(rank of first relevant)" in {
    RetrievalMetrics.reciprocalRank(Seq("x", "a", "b"), Set("a")) shouldBe 0.5 +- tol
    RetrievalMetrics.reciprocalRank(Seq("a", "b"), Set("a")) shouldBe 1.0 +- tol
  }

  it should "be 0.0 when nothing relevant is retrieved" in {
    RetrievalMetrics.reciprocalRank(Seq("x", "y"), Set("a")) shouldBe 0.0
  }

  "meanReciprocalRank" should "average reciprocal ranks over queries" in {
    val queries = Seq(
      (Seq("x", "a"), Set("a")), // rr = 0.5
      (Seq("b", "c"), Set("b")), // rr = 1.0
    )
    RetrievalMetrics.meanReciprocalRank(queries) shouldBe 0.75 +- tol
  }

  it should "be 0.0 for no queries" in {
    RetrievalMetrics.meanReciprocalRank(Seq.empty) shouldBe 0.0
  }

}
