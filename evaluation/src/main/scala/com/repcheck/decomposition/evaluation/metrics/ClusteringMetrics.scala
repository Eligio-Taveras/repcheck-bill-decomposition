package com.repcheck.decomposition.evaluation.metrics

/**
 * External clustering-comparison metrics (master §10b) — produced groups vs the gold groupings, both as a per-item
 * labeling aligned by index (item i's predicted label and gold label). Label types are free (gold concept labels are
 * strings, predicted cluster ids are ints) — only equality matters. All are invariant to label renaming.
 *
 *   - adjustedRandIndex: chance-corrected pair agreement (1 identical, ~0 independent, <0 worse than chance)
 *   - normalizedMutualInformation: shared information, arithmetic-mean normalized (== vMeasure)
 *   - homogeneity / completeness / vMeasure: each cluster pure / each class intact / their harmonic mean
 */
object ClusteringMetrics {

  private def choose2(n: Long): Long = n * (n - 1) / 2

  private def counts[A](labels: Seq[A]): Map[A, Int] =
    labels.groupMapReduce(identity)(_ => 1)(_ + _)

  private def contingency[A, B](pred: Seq[A], gold: Seq[B]): Map[(A, B), Int] =
    pred.zip(gold).groupMapReduce(identity)(_ => 1)(_ + _)

  private def aligned[A, B](pred: Seq[A], gold: Seq[B]): Unit =
    require(pred.size == gold.size, s"labelings must align by index: ${pred.size} != ${gold.size}")

  def adjustedRandIndex[A, B](pred: Seq[A], gold: Seq[B]): Double = {
    aligned(pred, gold)
    val n = pred.size.toLong
    if (n < 2) 1.0
    else {
      val index    = contingency(pred, gold).values.map(c => choose2(c.toLong)).sum.toDouble
      val sumA     = counts(pred).values.map(c => choose2(c.toLong)).sum.toDouble
      val sumB     = counts(gold).values.map(c => choose2(c.toLong)).sum.toDouble
      val expected = sumA * sumB / choose2(n).toDouble
      val max      = (sumA + sumB) / 2.0
      if (max == expected) 1.0 else (index - expected) / (max - expected)
    }
  }

  private def entropy(classCounts: Iterable[Int], n: Double): Double =
    classCounts.foldLeft(0.0)((acc, c) => if (c <= 0) acc else acc - (c / n) * math.log(c / n))

  def mutualInformation[A, B](pred: Seq[A], gold: Seq[B]): Double = {
    aligned(pred, gold)
    val n = pred.size.toDouble
    if (n == 0.0) 0.0
    else {
      val ai = counts(pred)
      val bj = counts(gold)
      contingency(pred, gold).foldLeft(0.0) {
        case (acc, ((p, g), nij)) =>
          acc + (nij / n) * math.log((n * nij) / (ai(p).toDouble * bj(g).toDouble))
      }
    }
  }

  def normalizedMutualInformation[A, B](pred: Seq[A], gold: Seq[B]): Double = {
    val n     = pred.size.toDouble
    val mi    = mutualInformation(pred, gold)
    val denom = (entropy(counts(pred).values, n) + entropy(counts(gold).values, n)) / 2.0
    if (denom == 0.0) 1.0 else mi / denom
  }

  /** Each predicted cluster contains members of a single gold class. MI / H(gold). */
  def homogeneity[A, B](pred: Seq[A], gold: Seq[B]): Double = {
    val hGold = entropy(counts(gold).values, pred.size.toDouble)
    if (hGold == 0.0) 1.0 else mutualInformation(pred, gold) / hGold
  }

  /** Each gold class is fully contained in a single predicted cluster. MI / H(pred). */
  def completeness[A, B](pred: Seq[A], gold: Seq[B]): Double = {
    val hPred = entropy(counts(pred).values, pred.size.toDouble)
    if (hPred == 0.0) 1.0 else mutualInformation(pred, gold) / hPred
  }

  def vMeasure[A, B](pred: Seq[A], gold: Seq[B]): Double = {
    val h = homogeneity(pred, gold)
    val c = completeness(pred, gold)
    if (h + c == 0.0) 0.0 else 2.0 * h * c / (h + c)
  }

}
