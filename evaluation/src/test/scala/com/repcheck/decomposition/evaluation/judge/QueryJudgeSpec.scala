package com.repcheck.decomposition.evaluation.judge

import com.repcheck.decomposition.conformance.ConformanceContract

class QueryJudgeSpec extends ConformanceContract {

  private def ci(label: String): QueryJudge.ConceptInput = QueryJudge.ConceptInput(label, List(s"$label does a thing"))

  "batch" should "split concepts by the count cap" in {
    val concepts = (0 until 45).toList.map(i => (i, ci(s"c$i")))
    val batches  = QueryJudge.batch(concepts, maxConcepts = 20)
    batches.map(_.size) shouldBe List(20, 20, 5)
  }

  it should "keep everything in one batch when under the cap" in {
    QueryJudge.batch(List((0, ci("a")), (1, ci("b"))), maxConcepts = 20).size shouldBe 1
  }

  "prompt" should "number each concept by its global index" in {
    val p = QueryJudge.prompt(List((3, ci("Veterans Care")), (7, ci("Border Security"))))
    p should include("[3] Veterans Care")
    p should include("[7] Border Security")
  }

  "parse" should "decode a queries reply" in {
    val raw =
      """{"queries":[{"index":0,"query":"how is COVID money spent"},{"index":1,"query":"veterans mental health"}]}"""
    QueryJudge.parse(raw).map(_.queries.map(_.index)) shouldBe Right(List(0, 1))
  }

  it should "extract the object despite code fences and trailing prose" in {
    val raw = "Sure!\n```json\n{\"queries\":[{\"index\":0,\"query\":\"q\"}]}\n```\nHope that helps"
    QueryJudge.parse(raw).map(_.queries.map(_.query)) shouldBe Right(List("q"))
  }

  it should "fail on a reply with no JSON object" in {
    QueryJudge.parse("no json here").isLeft shouldBe true
  }

}
