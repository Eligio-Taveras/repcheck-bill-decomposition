package com.repcheck.decomposition.evaluation.embed

/**
 * Embedding-space anisotropy corrections (master §10b): text embeddings sit in a narrow cone (high mean pairwise
 * cosine), which collapses silhouette-based clustering. Centering removes the common direction; standardizing also
 * scales each dimension to unit variance. Stats are estimated over a pooled set (the corpus), then applied per vector —
 * the same transform production would carry. Pure.
 */
object EmbeddingTransform {

  /** Elementwise mean over a pooled set of equal-length vectors. */
  def mean(vs: Seq[Vector[Double]]): Vector[Double] =
    vs.headOption match {
      case None => Vector.empty
      case Some(h) =>
        vs.foldLeft(Vector.fill(h.size)(0.0))((acc, v) => acc.lazyZip(v).map(_ + _)).map(_ / vs.size)
    }

  /** Elementwise population standard deviation, given the mean. */
  def std(vs: Seq[Vector[Double]], m: Vector[Double]): Vector[Double] =
    vs.headOption match {
      case None => Vector.empty
      case Some(h) =>
        vs.foldLeft(Vector.fill(h.size)(0.0)) { (acc, v) =>
          acc.lazyZip(v.lazyZip(m).map(_ - _)).map((a, d) => a + d * d)
        }.map(s => math.sqrt(s / vs.size))
    }

  /** Subtract the (corpus) mean — removes the dominant common direction. */
  def center(v: Vector[Double], m: Vector[Double]): Vector[Double] = v.lazyZip(m).map(_ - _)

  /** Center and scale each dimension to unit variance (zero-variance dims → 0). */
  def standardize(v: Vector[Double], m: Vector[Double], sd: Vector[Double]): Vector[Double] =
    v.lazyZip(m).map(_ - _).lazyZip(sd).map((d, s) => if (s == 0.0) 0.0 else d / s)

  /**
   * Apply a named transform to every vector, estimating stats over the pooled input. "center"/"standardize"/else none.
   */
  def apply(name: String, vs: Seq[Vector[Double]]): Vector[Vector[Double]] =
    name.toLowerCase match {
      case "center" =>
        val m = mean(vs)
        vs.iterator.map(center(_, m)).toVector
      case "standardize" =>
        val m  = mean(vs)
        val sd = std(vs, m)
        vs.iterator.map(standardize(_, m, sd)).toVector
      case _ => vs.toVector
    }

}
