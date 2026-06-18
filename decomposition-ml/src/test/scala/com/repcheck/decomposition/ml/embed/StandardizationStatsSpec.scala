package com.repcheck.decomposition.ml.embed

class StandardizationStatsSpec extends com.repcheck.decomposition.conformance.ConformanceContract {

  private def arr(n: Int, v: Double): String = Vector.fill(n)(v.toString).mkString("[", ",", "]")

  "parse" should "accept a well-formed 1024-dim artifact" in {
    val json = s"""{"mean": ${arr(StandardizationStats.Dim, 0.1)}, "std": ${arr(StandardizationStats.Dim, 0.2)}}"""
    StandardizationStats.parse(json).map(_.mean.size) shouldBe Right(StandardizationStats.Dim)
  }

  it should "reject a wrong mean dimension" in {
    val json = s"""{"mean": ${arr(3, 0.1)}, "std": ${arr(StandardizationStats.Dim, 0.2)}}"""
    StandardizationStats.parse(json) match {
      case Left(msg) => msg should (include(StandardizationStats.Dim.toString) and include("got 3"))
      case Right(_)  => fail("expected Left for a 3-dim mean")
    }
  }

  it should "reject a wrong std dimension" in {
    val json = s"""{"mean": ${arr(StandardizationStats.Dim, 0.1)}, "std": ${arr(5, 0.2)}}"""
    StandardizationStats.parse(json).isLeft shouldBe true
  }

  it should "reject malformed json" in {
    StandardizationStats.parse("not json").isLeft shouldBe true
  }

  it should "reject json missing the mean field" in {
    StandardizationStats.parse(s"""{"std": ${arr(StandardizationStats.Dim, 0.2)}}""").isLeft shouldBe true
  }

  "fromResource" should "fail-soft to Left on a missing resource" in {
    StandardizationStats.fromResource("standardization/does-not-exist.json").isLeft shouldBe true
  }

  "bundled" should "load the committed artifact with 1024-dim mean and std" in {
    val stats = StandardizationStats.bundled
    stats.mean.size shouldBe StandardizationStats.Dim
    stats.std.size shouldBe StandardizationStats.Dim
  }

  it should "carry the real population values (large anisotropy, no zero-variance dims)" in {
    val stats  = StandardizationStats.bundled
    val meanL2 = math.sqrt(stats.mean.map(m => m * m).sum)
    meanL2 should be > 0.5 // unit-norm embeddings + a strong common direction → standardization is justified
    stats.std.forall(_ > 0.0) shouldBe true // no zero-variance dims → standardize never divides by zero
  }

}
