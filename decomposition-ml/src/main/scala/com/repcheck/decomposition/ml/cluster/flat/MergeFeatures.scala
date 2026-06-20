package com.repcheck.decomposition.ml.cluster.flat

/**
 * Builds the 13 numbers describing a candidate merge of two clusters, in the exact order the merge-stop model expects.
 * They capture how similar the clusters are, how clearly this is the best available merge, how big they are, whether
 * the sections sit next to each other in the bill, how internally coherent each cluster is, and how far merging has
 * progressed. Pure and stateless.
 */
object MergeFeatures {

  def describeMerge(
    context: SectionContext,
    left: List[Int],
    right: List[Int],
    nextBestAffinity: Double,
  ): IndexedSeq[Double] = {
    val sectionCount    = context.sectionCount
    val crossAffinities = for { l <- left; r <- right } yield context.affinityMatrix(l)(r)
    val meanAffinity    = crossAffinities.sum / crossAffinities.size.toDouble
    val minAffinity     = crossAffinities.foldLeft(Double.PositiveInfinity)(math.min) // the weakest link
    val affinityStdev   = ClusterMath.populationStdev(crossAffinities)                // how uniform they are
    val positionGaps    = for { l <- left; r <- right } yield math.abs(l - r)
    val minPositionGap  = positionGaps.foldLeft(Int.MaxValue)(math.min)
    val meanPositionGap = positionGaps.sum.toDouble / positionGaps.size.toDouble
    val leftCohesion    = ClusterMath.cohesion(context.affinityMatrix, left)
    val rightCohesion   = ClusterMath.cohesion(context.affinityMatrix, right)
    val weakerCohesion  = math.min(leftCohesion, rightCohesion)
    Vector(
      meanAffinity,                                                        // 0  average similarity of the merge
      meanAffinity - nextBestAffinity,                                     // 1  margin over the next-best merge
      math.min(left.size, right.size).toDouble / sectionCount,             // 2  smaller cluster size (fraction)
      (left.size + right.size).toDouble / sectionCount,                    // 3  combined size (fraction)
      if (minPositionGap == 1) 1.0 else 0.0,                               // 4  sections are adjacent in the bill
      meanPositionGap / sectionCount,                                      // 5  average positional distance
      weakerCohesion,                                                      // 6  cohesion of the looser cluster
      (sectionCount - left.size - right.size + 2).toDouble / sectionCount, // 7  how many clusters remain
      minAffinity,                                                         // 8  weakest-link similarity
      affinityStdev,                                                       // 9  spread of cross-affinities
      ClusterMath.meanCrossValue(context.embeddingCosine, left, right),    // 10 pure topic similarity
      ClusterMath.meanCrossValue(context.tfidfCosine, left, right),        // 11 lexical similarity
      meanAffinity - weakerCohesion,                                       // 12 merge vs. internal cohesion
    )
  }

}
