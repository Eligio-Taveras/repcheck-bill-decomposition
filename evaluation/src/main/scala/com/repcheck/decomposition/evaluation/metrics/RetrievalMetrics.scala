package com.repcheck.decomposition.evaluation.metrics

/**
 * Extrinsic retrieval-proxy metrics (master §10b — "the one that actually matters", until the scoring layer exists).
 * Given a topic/preference query, a ranked list of retrieved items, and the set of relevant items, does the right
 * section/group surface near the top?
 *
 *   - precisionAtK: fraction of the top-k that are relevant (divides by k, so a short list is penalized)
 *   - reciprocalRank: 1 / rank of the first relevant item (0 if none retrieved)
 *   - meanReciprocalRank: mean reciprocalRank over a query set
 */
object RetrievalMetrics {

  def precisionAtK[A](ranked: Seq[A], relevant: Set[A], k: Int): Double = {
    require(k > 0, s"k must be positive: $k")
    ranked.take(k).count(relevant.contains).toDouble / k.toDouble
  }

  def reciprocalRank[A](ranked: Seq[A], relevant: Set[A]): Double =
    ranked.indexWhere(relevant.contains) match {
      case -1 => 0.0
      case i  => 1.0 / (i + 1).toDouble
    }

  def meanReciprocalRank[A](queries: Seq[(Seq[A], Set[A])]): Double =
    if (queries.isEmpty) 0.0
    else queries.map { case (ranked, relevant) => reciprocalRank(ranked, relevant) }.sum / queries.size.toDouble

}
