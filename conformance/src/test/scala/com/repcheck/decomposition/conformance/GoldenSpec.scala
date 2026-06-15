package com.repcheck.decomposition.conformance

import io.circe.Encoder
import io.circe.syntax._

class GoldenSpec extends ConformanceContract {

  final private case class Demo(name: String, ids: List[Int])
  private given Encoder[Demo] = Encoder.forProduct2("name", "ids")(d => (d.name, d.ids))

  private val demo = Demo("widget", List(2, 1, 3))

  "Golden" should "match a committed golden for an unchanged artifact" in {
    Golden.diff("demo", demo.asJson) shouldBe None
  }

  it should "report a diff when the artifact changes" in {
    assume(!sys.props.get("update.goldens").contains("true"), "skipped while re-baselining goldens")
    Golden.diff("demo", demo.copy(ids = List(9)).asJson) match {
      case Some(msg) => msg should include("mismatch")
      case None      => fail("expected a golden mismatch")
    }
  }

}
