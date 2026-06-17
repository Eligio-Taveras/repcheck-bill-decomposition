package com.repcheck.decomposition.evaluation.compare

import com.repcheck.decomposition.evaluation.metrics.ClusteringMetrics

/** Agreement of one section grouping against another, by index-aligned labelings. */
final case class ComparisonResult(
  ari: Double,
  nmi: Double,
  vMeasure: Double,
  homogeneity: Double,
  completeness: Double,
  predGroups: Int,
  refGroups: Int,
)

/**
 * Scores a HAC PREDICTION grouping against the Claude REFERENCE grouping (DP0-5). Both are expressed as a per-section
 * label aligned by section index; the external metrics (DP0-2) do the work. Pure — the live embed/cluster/Claude calls
 * live in the harness.
 */
object GroupingComparison {

  /**
   * Per-section group label (group ordinal) from a list of section-index groups (e.g. the gold reference's groups),
   * aligned to 0..n-1. A section not covered by any group gets a distinct singleton label.
   */
  def labelsFromGroups(groups: List[List[Int]], n: Int): Vector[Int] = {
    val assigned = groups.zipWithIndex.flatMap { case (members, g) => members.map(_ -> g) }.toMap
    val base     = groups.size
    (0 until n).map(i => assigned.getOrElse(i, base + i)).toVector
  }

  def compare(prediction: Vector[Int], reference: Vector[Int]): ComparisonResult =
    ComparisonResult(
      ari = ClusteringMetrics.adjustedRandIndex(prediction, reference),
      nmi = ClusteringMetrics.normalizedMutualInformation(prediction, reference),
      vMeasure = ClusteringMetrics.vMeasure(prediction, reference),
      homogeneity = ClusteringMetrics.homogeneity(prediction, reference),
      completeness = ClusteringMetrics.completeness(prediction, reference),
      predGroups = prediction.distinct.size,
      refGroups = reference.distinct.size,
    )

}
