package com.repcheck.decomposition.conformance

/**
 * Numeric tolerance for the comparisons §10c#2 keeps OUT of goldens. Embeddings are not bit-identical across calls (F3:
 * same-text cosine ~0.9998), so vectors compare by a cosine floor; scores compare within a band.
 */
object Tolerance {

  /** The F3-measured floor — same-text embeddings sit well above this; different-text well below. */
  val DefaultVectorFloor: Double = 0.995

  def cosine(a: Vector[Double], b: Vector[Double]): Double = {
    require(a.length == b.length, s"cosine: dimension mismatch ${a.length} != ${b.length}")
    val dot = a.lazyZip(b).map(_ * _).sum
    val na  = math.sqrt(a.map(x => x * x).sum)
    val nb  = math.sqrt(b.map(x => x * x).sum)
    if (na == 0.0 || nb == 0.0) 0.0 else dot / (na * nb)
  }

  def cosineAtLeast(a: Vector[Double], b: Vector[Double], floor: Double = DefaultVectorFloor): Boolean =
    cosine(a, b) >= floor

  def withinBand(expected: Double, actual: Double, tol: Double): Boolean =
    math.abs(expected - actual) <= tol

}
