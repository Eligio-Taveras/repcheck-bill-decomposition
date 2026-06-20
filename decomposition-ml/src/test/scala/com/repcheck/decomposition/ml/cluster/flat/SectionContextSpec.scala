package com.repcheck.decomposition.ml.cluster.flat

import com.repcheck.decomposition.ml.embed.{StandardizationStats, Vector1024}

class SectionContextSpec extends com.repcheck.decomposition.conformance.ConformanceContract {

  private val artifacts = FlatGroupingArtifacts.bundled
  private val stats     = StandardizationStats.bundled

  private def section(index: Int, text: String): FlatSection = {
    val embedding = Vector1024
      .of(Vector.fill(Vector1024.Dim)((index + 1) * 0.01f))
      .toOption
      .getOrElse(fail("embedding should build"))
    FlatSection(index, text, embedding)
  }

  "build" should "produce square, section-sized matrices with unit diagonals" in {
    val sections =
      Vector(section(0, "federal reserve banks"), section(1, "clean air emissions"), section(2, "tax credit rules"))
    val context = SectionContext.build(sections, artifacts, stats)
    context.sectionCount shouldBe 3
    context.affinityMatrix.size shouldBe 3
    context.affinityMatrix.foreach(row => row.size shouldBe 3)
    (0 until 3).foreach { i =>
      context.affinityMatrix(i)(i) shouldBe 1.0
      context.embeddingCosine(i)(i) shouldBe 1.0
      context.tfidfCosine(i)(i) shouldBe 1.0
    }
  }

}
