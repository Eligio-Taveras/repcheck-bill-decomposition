package com.repcheck.decomposition.ml.cluster.flat

class LogisticModelSpec extends com.repcheck.decomposition.conformance.ConformanceContract {

  "of" should "reject vectors whose dimensions do not all match the expected feature count" in {
    LogisticModel.of(Vector("a"), Vector(1.0, 2.0), 0.0, Vector(0.0), Vector(1.0), 1) match {
      case Left(message) => message should include("1")
      case Right(_)      => fail("expected Left for mismatched dimensions")
    }
  }

  it should "build a model when every vector matches the expected feature count" in {
    LogisticModel.of(Vector("a"), Vector(1.0), 0.0, Vector(0.0), Vector(1.0), 1).isRight shouldBe true
  }

  "predict" should "standardize features, apply weights and bias, then squash" in {
    val model = LogisticModel
      .of(Vector("x"), Vector(2.0), 0.0, Vector(0.0), Vector(1.0), 1)
      .toOption
      .getOrElse(fail("model should build"))
    // raw 1.0 → standardized 1.0 → logit 2.0 → logistic(2.0)
    math.abs(model.predict(Vector(1.0)) - LogisticModel.logistic(2.0)) should be < 1e-12
  }

  "logistic" should "be 0.5 at zero and saturate at the extremes" in {
    LogisticModel.logistic(0.0) shouldBe 0.5
    LogisticModel.logistic(1000.0) should be > 0.999999
    LogisticModel.logistic(-1000.0) should be < 0.000001
  }

  it should "treat a zero-variance feature as contributing nothing" in {
    val model = LogisticModel
      .of(Vector("x"), Vector(2.0), 0.0, Vector(0.0), Vector(0.0), 1)
      .toOption
      .getOrElse(fail("model should build"))
    model.predict(Vector(5.0)) shouldBe 0.5 // sd == 0 → standardized 0 → logit 0 → logistic(0) = 0.5
  }

}
