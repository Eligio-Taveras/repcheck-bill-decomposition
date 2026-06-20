package com.repcheck.decomposition.ml.cluster.flat

class MergeFeaturesSpec extends com.repcheck.decomposition.conformance.ConformanceContract {

  private val matrix = Vector(
    Vector(1.0, 0.9, 0.2, 0.1),
    Vector(0.9, 1.0, 0.3, 0.2),
    Vector(0.2, 0.3, 1.0, 0.8),
    Vector(0.1, 0.2, 0.8, 1.0),
  )

  // The same matrix stands in for all three views; only relative values matter for these checks.
  private val context = SectionContext(matrix, matrix, matrix, sectionCount = 4)

  "describeMerge" should "produce thirteen features in the documented order" in {
    val features = MergeFeatures.describeMerge(context, List(0), List(1), nextBestAffinity = 0.5)
    features.size shouldBe 13
    math.abs(features(0) - 0.9) should be < 1e-9          // 0  mean cross affinity = matrix(0)(1)
    math.abs(features(1) - (0.9 - 0.5)) should be < 1e-9  // 1  margin over next-best
    features(2) shouldBe 0.25                             // 2  min size / n
    features(3) shouldBe 0.5                              // 3  total size / n
    features(4) shouldBe 1.0                              // 4  adjacent (|0-1| == 1)
    math.abs(features(8) - 0.9) should be < 1e-9          // 8  weakest-link affinity
    math.abs(features(12) - (0.9 - 1.0)) should be < 1e-9 // 12 mean affinity minus cohesion of singletons (1.0)
  }

  it should "flag non-adjacent merges" in {
    val features = MergeFeatures.describeMerge(context, List(0), List(2), nextBestAffinity = 0.0)
    features(4) shouldBe 0.0 // sections 0 and 2 are not adjacent
  }

}
