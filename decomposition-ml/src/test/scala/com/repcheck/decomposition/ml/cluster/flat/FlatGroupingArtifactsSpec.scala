package com.repcheck.decomposition.ml.cluster.flat

class FlatGroupingArtifactsSpec extends com.repcheck.decomposition.conformance.ConformanceContract {

  "bundled" should "load the committed artifacts with the expected shapes" in {
    val artifacts = FlatGroupingArtifacts.bundled
    artifacts.affinityModel.weights.size shouldBe FlatGroupingArtifacts.AffinityFeatureCount
    artifacts.mergeStopModel.weights.size shouldBe FlatGroupingArtifacts.MergeStopFeatureCount
    artifacts.topTermCount shouldBe 15
    artifacts.mergeStopThreshold shouldBe 0.6
    artifacts.idf.corpusSectionCount should be > 0
    artifacts.idf.termIdf.size should be > 1000
  }

  "fromResources" should "fail when a resource is missing" in {
    FlatGroupingArtifacts
      .fromResources(
        "flat-grouping/does-not-exist.json",
        FlatGroupingArtifacts.MergeStopResource,
        FlatGroupingArtifacts.IdfResource,
      )
      .isLeft shouldBe true
  }

  it should "report a parse error on malformed json" in {
    FlatGroupingArtifacts.fromResources(
      "flat-grouping/test-not-json.json",
      FlatGroupingArtifacts.MergeStopResource,
      FlatGroupingArtifacts.IdfResource,
    ) match {
      case Left(message) => message should include("invalid affinity json")
      case Right(_)      => fail("expected Left for malformed json")
    }
  }

  it should "report the offending field when a model field is missing" in {
    FlatGroupingArtifacts.fromResources(
      "flat-grouping/test-missing-field.json",
      FlatGroupingArtifacts.MergeStopResource,
      FlatGroupingArtifacts.IdfResource,
    ) match {
      case Left(message) => message should include("w:")
      case Right(_)      => fail("expected Left for a missing field")
    }
  }

}
