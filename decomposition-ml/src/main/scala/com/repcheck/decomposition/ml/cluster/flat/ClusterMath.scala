package com.repcheck.decomposition.ml.cluster.flat

/**
 * Numeric aggregations over clusters of section indices, given a section-by-section value matrix (affinity, embedding
 * cosine, or TF-IDF cosine). Shared by the agglomeration (for ranking pairs) and by the merge-feature descriptor. Pure
 * and stateless.
 */
object ClusterMath {

  /** Average value between every section of one cluster and every section of the other. */
  def meanCrossValue(matrix: Vector[Vector[Double]], left: List[Int], right: List[Int]): Double = {
    val total = left.foldLeft(0.0)((sum, l) => sum + right.foldLeft(0.0)((rowSum, r) => rowSum + matrix(l)(r)))
    total / (left.size.toDouble * right.size.toDouble)
  }

  /** How tightly a cluster's own sections cohere; a lone section is perfectly coherent (1.0). */
  def cohesion(matrix: Vector[Vector[Double]], members: List[Int]): Double =
    if (members.sizeIs < 2) 1.0
    else {
      val memberVector = members.toVector
      val pairValues = for {
        i <- memberVector.indices
        j <- (i + 1) until memberVector.size
      } yield matrix(memberVector(i))(memberVector(j))
      pairValues.sum / pairValues.size.toDouble
    }

  /** Population standard deviation; 0 for fewer than two values. */
  def populationStdev(values: Seq[Double]): Double =
    if (values.sizeIs < 2) 0.0
    else {
      val mean = values.sum / values.size.toDouble
      math.sqrt(values.foldLeft(0.0)((sum, value) => sum + (value - mean) * (value - mean)) / values.size.toDouble)
    }

}
