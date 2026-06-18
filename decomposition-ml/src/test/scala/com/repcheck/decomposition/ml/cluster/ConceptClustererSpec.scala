package com.repcheck.decomposition.ml.cluster

import com.repcheck.decomposition.conformance.ConformanceContract
import com.repcheck.decomposition.ml.embed.Vector1024

class ConceptClustererSpec extends ConformanceContract {

  private def v1024(fill: Float): Vector1024 =
    Vector1024.of(Vector.fill(Vector1024.Dim)(fill)).fold(e => fail(e), identity)

  // Two structurally distinct groups (graded-hierarchy distance dominates at structureWeight=0.9):
  //   indices 0,1 under Title 1; indices 2,3 under Title 2.
  private val vectors: Vector[Vector1024] =
    Vector(v1024(0.10f), v1024(0.11f), v1024(0.90f), v1024(0.91f))

  private val parents: Vector[List[String]] =
    Vector(List("T1"), List("T1"), List("T2"), List("T2"))

  private val clusterer: ConceptClusterer = HacConceptClusterer.production

  "HacConceptClusterer" should "split two distinct groups at subjectCount = 2" in {
    val groups = clusterer.cluster(vectors, parents, subjectCount = 2)
    groups.map(_.memberIndices.toSet).toSet shouldBe Set(Set(0, 1), Set(2, 3))
  }

  it should "collapse to a single group when subjectCount <= 1" in {
    val groups = clusterer.cluster(vectors, parents, subjectCount = 1)
    groups.map(_.memberIndices) shouldBe List(List(0, 1, 2, 3))
  }

  it should "return a partition of every input index" in {
    val groups  = clusterer.cluster(vectors, parents, subjectCount = 2)
    val members = groups.flatMap(_.memberIndices)
    members.sorted shouldBe vectors.indices.toList // every index appears exactly once
  }

  it should "order member indices ascending within each group" in {
    val groups = clusterer.cluster(vectors, parents, subjectCount = 2)
    groups.foreach(g => g.memberIndices shouldBe g.memberIndices.sorted)
  }

}
