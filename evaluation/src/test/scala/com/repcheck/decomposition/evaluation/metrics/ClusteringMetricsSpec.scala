package com.repcheck.decomposition.evaluation.metrics

import com.repcheck.decomposition.conformance.ConformanceContract

class ClusteringMetricsSpec extends ConformanceContract {

  private val tol = 1e-4

  "adjustedRandIndex" should "be 1.0 for an identical labeling" in {
    ClusteringMetrics.adjustedRandIndex(Seq(0, 0, 1, 1), Seq(0, 0, 1, 1)) shouldBe 1.0
  }

  it should "be invariant to label renaming (permutation)" in {
    ClusteringMetrics.adjustedRandIndex(Seq(0, 0, 1, 1), Seq("a", "a", "b", "b")) shouldBe 1.0
  }

  it should "match the hand-computed value for a partial-agreement case" in {
    // pred=[0,0,0,1] gold=[0,0,1,1]: index=1, expected=1.0, max=2.5 → (1-1)/(2.5-1)=0
    ClusteringMetrics.adjustedRandIndex(Seq(0, 0, 0, 1), Seq(0, 0, 1, 1)) shouldBe 0.0 +- tol
  }

  it should "be 0.0 when everything is lumped into one cluster" in {
    ClusteringMetrics.adjustedRandIndex(Seq(0, 0, 0, 0), Seq(0, 0, 1, 1)) shouldBe 0.0 +- tol
  }

  it should "be 1.0 for a degenerate (n<2) labeling" in {
    ClusteringMetrics.adjustedRandIndex(Seq(1), Seq(9)) shouldBe 1.0
    ClusteringMetrics.adjustedRandIndex(Seq.empty[Int], Seq.empty[Int]) shouldBe 1.0
  }

  it should "reject misaligned labelings" in {
    an[IllegalArgumentException] should be thrownBy ClusteringMetrics.adjustedRandIndex(Seq(1), Seq(1, 2))
  }

  "v-measure family" should "be 1.0 for an identical labeling" in {
    ClusteringMetrics.homogeneity(Seq(0, 0, 1, 1), Seq(0, 0, 1, 1)) shouldBe 1.0 +- tol
    ClusteringMetrics.completeness(Seq(0, 0, 1, 1), Seq(0, 0, 1, 1)) shouldBe 1.0 +- tol
    ClusteringMetrics.vMeasure(Seq(0, 0, 1, 1), Seq(0, 0, 1, 1)) shouldBe 1.0 +- tol
  }

  it should "match hand-computed homogeneity/completeness/v for the partial case" in {
    val pred = Seq(0, 0, 0, 1)
    val gold = Seq(0, 0, 1, 1)
    ClusteringMetrics.homogeneity(pred, gold) shouldBe 0.311278 +- tol
    ClusteringMetrics.completeness(pred, gold) shouldBe 0.383684 +- tol
    ClusteringMetrics.vMeasure(pred, gold) shouldBe 0.343721 +- tol
  }

  it should "give homogeneity 0, completeness 1, v 0 when all items share one cluster" in {
    val pred = Seq(0, 0, 0, 0)
    val gold = Seq(0, 0, 1, 1)
    ClusteringMetrics.homogeneity(pred, gold) shouldBe 0.0 +- tol
    ClusteringMetrics.completeness(pred, gold) shouldBe 1.0 +- tol
    ClusteringMetrics.vMeasure(pred, gold) shouldBe 0.0 +- tol
  }

  "normalizedMutualInformation" should "equal v-measure (arithmetic normalizer) and be 1.0 for identity" in {
    ClusteringMetrics.normalizedMutualInformation(Seq(0, 0, 1, 1), Seq(0, 0, 1, 1)) shouldBe 1.0 +- tol
    ClusteringMetrics.normalizedMutualInformation(Seq(0, 0, 0, 1), Seq(0, 0, 1, 1)) shouldBe 0.343721 +- tol
  }

  "mutualInformation" should "be 0.0 for an empty labeling" in {
    ClusteringMetrics.mutualInformation(Seq.empty[Int], Seq.empty[Int]) shouldBe 0.0
  }

}
