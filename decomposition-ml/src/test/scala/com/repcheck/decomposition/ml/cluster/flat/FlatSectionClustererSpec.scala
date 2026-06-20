package com.repcheck.decomposition.ml.cluster.flat

import com.repcheck.decomposition.ml.cluster.Cluster
import com.repcheck.decomposition.ml.embed.{StandardizationStats, Vector1024}

class FlatSectionClustererSpec extends com.repcheck.decomposition.conformance.ConformanceContract {

  private val clusterer = FlatSectionClusterer.production

  private def section(index: Int, text: String): FlatSection = {
    val embedding = Vector1024
      .of(Vector.fill(Vector1024.Dim)((index + 1) * 0.01f))
      .toOption
      .getOrElse(fail("embedding should build"))
    FlatSection(index, text, embedding)
  }

  "cluster" should "return no clusters for a bill with no sections" in {
    clusterer.cluster(Vector.empty) shouldBe Nil
  }

  it should "return a single cluster for a one-section bill" in {
    clusterer.cluster(Vector(section(0, "only section"))) shouldBe List(Cluster(List(0)))
  }

  it should "partition every section into exactly one cluster" in {
    val sections = Vector.tabulate(5)(i => section(i, s"section $i about a distinct policy topic"))
    val clusters = clusterer.cluster(sections)
    clusters.flatMap(_.memberIndices).sorted shouldBe (0 until 5).toList
  }

  it should "use the sequential fallback above maxVetoedSections and still produce a full partition" in {
    val cappedClusterer =
      new FlatSectionClusterer(
        FlatGroupingArtifacts.bundled,
        StandardizationStats.bundled,
        FlatGroupingConfig(maxVetoedSections = 2),
      )
    val sections = Vector.tabulate(5)(i => section(i, s"section $i text"))
    val clusters = cappedClusterer.cluster(sections)
    clusters.flatMap(_.memberIndices).sorted shouldBe (0 until 5).toList
  }

}
