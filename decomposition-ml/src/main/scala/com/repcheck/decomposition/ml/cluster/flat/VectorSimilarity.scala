package com.repcheck.decomposition.ml.cluster.flat

/**
 * Similarity measures over sets and vectors. Pure, stateless, generic math.
 *
 * Reuse candidate: domain-agnostic — extract to `repcheck-utils` once that shared library exists.
 */
object VectorSimilarity {

  /** Overlap of two sets: shared elements divided by total distinct elements (0 when both are empty). */
  def jaccard[A](left: Set[A], right: Set[A]): Double =
    if (left.isEmpty && right.isEmpty) 0.0
    else left.intersect(right).size.toDouble / left.union(right).size.toDouble

  /** Cosine similarity of two equal-length dense vectors (0 if either is all zeros). */
  def cosine(left: IndexedSeq[Double], right: IndexedSeq[Double]): Double = {
    val dotProduct = left.indices.foldLeft(0.0)((sum, i) => sum + left(i) * right(i))
    val leftNorm   = math.sqrt(left.foldLeft(0.0)((sum, value) => sum + value * value))
    val rightNorm  = math.sqrt(right.foldLeft(0.0)((sum, value) => sum + value * value))
    if (leftNorm * rightNorm == 0.0) 0.0 else dotProduct / (leftNorm * rightNorm)
  }

  /** Cosine similarity of two sparse term→weight maps (e.g. TF-IDF vectors). */
  def cosineSparse(left: Map[String, Double], right: Map[String, Double]): Double = {
    val dotProduct = left.foldLeft(0.0) { case (sum, (term, weight)) => sum + weight * right.getOrElse(term, 0.0) }
    val leftNorm   = math.sqrt(left.valuesIterator.foldLeft(0.0)((sum, weight) => sum + weight * weight))
    val rightNorm  = math.sqrt(right.valuesIterator.foldLeft(0.0)((sum, weight) => sum + weight * weight))
    if (leftNorm * rightNorm == 0.0) 0.0 else dotProduct / (leftNorm * rightNorm)
  }

}
