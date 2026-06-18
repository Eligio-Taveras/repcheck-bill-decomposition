package com.repcheck.decomposition.ml.metrics

/**
 * Intrinsic embedding-quality metrics (master §10b, validates the D10 model choice). Operate on section vectors + their
 * gold concept labels, aligned by index. The question: do same-concept section pairs sit closer (higher cosine) than
 * cross-concept pairs, and how anisotropic is the space?
 *
 *   - meanCosineGap: mean cosine of same-label pairs minus mean cosine of different-label pairs (higher = better
 *     separation)
 *   - separationAuc: P(a random same-label pair scores higher than a random different-label pair); 0.5 = no signal, 1 =
 *     perfect
 *   - anisotropy: mean cosine over ALL pairs; near 1 = vectors collapse to a cone (a single global D_max is then
 *     suspect)
 */
object EmbeddingMetrics {

  def cosine(a: Vector[Double], b: Vector[Double]): Double = {
    require(a.size == b.size, s"cosine: dimension mismatch ${a.size} != ${b.size}")
    val dot = a.lazyZip(b).map(_ * _).sum
    val na  = math.sqrt(a.map(x => x * x).sum)
    val nb  = math.sqrt(b.map(x => x * x).sum)
    if (na == 0.0 || nb == 0.0) 0.0 else dot / (na * nb)
  }

  private def pairs[A](vectors: Seq[Vector[Double]], labels: Seq[A]): Seq[(Double, Boolean)] = {
    require(vectors.size == labels.size, s"vectors/labels must align: ${vectors.size} != ${labels.size}")
    val idx = vectors.indices
    for {
      i <- idx
      j <- idx
      if j > i
    } yield (cosine(vectors(i), vectors(j)), labels(i) == labels(j))
  }

  def meanCosineGap[A](vectors: Seq[Vector[Double]], labels: Seq[A]): Double = {
    val scored = pairs(vectors, labels)
    val same   = scored.collect { case (c, true) => c }
    val diff   = scored.collect { case (c, false) => c }
    if (same.isEmpty || diff.isEmpty) 0.0 else same.sum / same.size - diff.sum / diff.size
  }

  def separationAuc[A](vectors: Seq[Vector[Double]], labels: Seq[A]): Double = {
    val scored = pairs(vectors, labels)
    val nPos   = scored.count(_._2)
    val nNeg   = scored.size - nPos
    if (nPos == 0 || nNeg == 0) 0.5
    else {
      // Mann-Whitney U via rank sum of the positive (same-label) class; ordinal ranks (exact when scores are distinct).
      val rankSumPos = scored.sortBy(_._1).zipWithIndex.collect { case ((_, true), r) => (r + 1).toDouble }.sum
      (rankSumPos - nPos.toDouble * (nPos + 1) / 2.0) / (nPos.toDouble * nNeg.toDouble)
    }
  }

  def anisotropy(vectors: Seq[Vector[Double]]): Double = {
    val idx = vectors.indices
    val cs = for {
      i <- idx
      j <- idx
      if j > i
    } yield cosine(vectors(i), vectors(j))
    if (cs.isEmpty) 0.0 else cs.sum / cs.size
  }

}
