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

  // A clusterer whose merge-stop model always endorses (bias +100) and whose affinity model returns a
  // uniform 0.5 for every pair (all-zero weights). Uniform affinity forces the sort tie-breaks; always
  // endorsing drives the agglomeration all the way down to a single concept.
  private def alwaysMergeClusterer(maxVetoed: Int): FlatSectionClusterer = {
    val affinity = LogisticModel
      .of(Vector.fill(5)("f"), Vector.fill(5)(0.0), 0.0, Vector.fill(5)(0.0), Vector.fill(5)(1.0), 5)
      .toOption
      .getOrElse(fail("affinity model"))
    val mergeStop = LogisticModel
      .of(Vector.fill(13)("f"), Vector.fill(13)(0.0), 100.0, Vector.fill(13)(0.0), Vector.fill(13)(1.0), 13)
      .toOption
      .getOrElse(fail("merge-stop model"))
    val artifacts = FlatGroupingArtifacts(affinity, mergeStop, IdfTable(1, Map.empty[String, Double]), 15, 0.6)
    new FlatSectionClusterer(artifacts, StandardizationStats.bundled, FlatGroupingConfig(maxVetoedSections = maxVetoed))
  }

  it should "merge everything into one concept when every merge is endorsed (vetoed path)" in {
    val sections = Vector.tabulate(3)(i => section(i, "identical text"))
    alwaysMergeClusterer(maxVetoed = 100).cluster(sections) shouldBe List(Cluster(List(0, 1, 2)))
  }

  it should "merge everything into one concept via the sequential fallback" in {
    val sections = Vector.tabulate(3)(i => section(i, "identical text"))
    alwaysMergeClusterer(maxVetoed = 0).cluster(sections) shouldBe List(Cluster(List(0, 1, 2)))
  }

}
