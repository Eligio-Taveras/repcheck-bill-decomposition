package com.repcheck.decomposition.ml.cluster

import com.repcheck.decomposition.conformance.ConformanceContract
import com.repcheck.decomposition.ml.cluster.flat.{FlatConceptClusterer, FlatSection}
import com.repcheck.decomposition.ml.embed.{StandardizationStats, Vector1024}

class ConceptClustererSpec extends ConformanceContract {

  private def v1024(fill: Float): Vector1024 =
    Vector1024.of(Vector.fill(Vector1024.Dim)(fill)).fold(e => fail(e), identity)

  // Four sections; the structural breadcrumb drives the omnibus route (cosine is degenerate on these constant vectors).
  private val embeddings = Vector(v1024(0.10f), v1024(0.11f), v1024(0.90f), v1024(0.91f))

  private val identityStats = StandardizationStats(Vector.fill(Vector1024.Dim)(0.0), Vector.fill(Vector1024.Dim)(1.0))

  // Stub flat clusterer: lumps everything into one group — distinguishable from the omnibus silhouette result, so the
  // tests can prove which path ran without depending on the trained flat model's exact output.
  private val lumpAllFlat: FlatConceptClusterer = new FlatConceptClusterer {
    def cluster(sections: Vector[FlatSection]): List[Cluster] = List(Cluster(sections.indices.toList))
  }

  private def sections(parents: Vector[List[String]]): Vector[SectionInput] =
    embeddings.indices.toVector.map(i => SectionInput(i, s"section $i", embeddings(i), parents(i)))

  private def router(flat: FlatConceptClusterer): ConceptClusterer =
    new RoutingConceptClusterer(
      flat,
      ClusteringConfig(),
      identityStats,
      RoutingConceptClusterer.DefaultFlatCoverageThreshold,
    )

  private val twoTitles = Vector(List("T1"), List("T1"), List("T2"), List("T2"))
  private val noParents = Vector.fill(4)(List.empty[String])

  "RoutingConceptClusterer" should "route an OMNIBUS bill (coverage > 0) to the silhouette-cut path" in {
    // silhouette splits the two Titles; the lump-all flat stub was NOT used.
    val groups = router(lumpAllFlat).cluster(sections(twoTitles))
    groups.map(_.memberIndices.toSet).toSet shouldBe Set(Set(0, 1), Set(2, 3))
  }

  it should "route a FLAT bill (coverage 0) to the logistic-regression clusterer" in {
    // the flat stub lumped all four; the omnibus path would have split into two → proves the flat path ran.
    val groups = router(lumpAllFlat).cluster(sections(noParents))
    groups.map(_.memberIndices) shouldBe List(List(0, 1, 2, 3))
  }

  it should "return a partition of every input section index" in {
    val groups = router(lumpAllFlat).cluster(sections(twoTitles))
    groups.flatMap(_.memberIndices).sorted shouldBe embeddings.indices.toList
  }

  it should "handle the degenerate 0- and 1-section cases" in {
    router(lumpAllFlat).cluster(Vector.empty) shouldBe Nil
    router(lumpAllFlat).cluster(Vector(SectionInput(0, "x", embeddings(0), Nil))) shouldBe List(Cluster(List(0)))
  }

  "RoutingConceptClusterer.production" should
    "cluster an omnibus bill with the bundled models and return a full partition" in {
      val groups = RoutingConceptClusterer.production.cluster(sections(twoTitles))
      groups.flatMap(_.memberIndices).sorted shouldBe embeddings.indices.toList
    }

}
