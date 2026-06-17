package com.repcheck.decomposition.evaluation.judge

import com.repcheck.decomposition.conformance.ConformanceContract
import com.repcheck.decomposition.evaluation.judge.ConceptJudge.{JudgeGroup, JudgeResponse}

class ConceptJudgeSpec extends ConformanceContract {

  "parse" should "decode a bare JSON object" in {
    val raw = """{"groups":[{"concept":"Funding","sectionIndices":[0,1]}]}"""
    ConceptJudge.parse(raw) shouldBe Right(JudgeResponse(List(JudgeGroup("Funding", List(0, 1)))))
  }

  it should "tolerate code fences and surrounding prose" in {
    val raw = "Here you go:\n```json\n{\"groups\":[{\"concept\":\"X\",\"sectionIndices\":[2]}]}\n```\n"
    ConceptJudge.parse(raw) shouldBe Right(JudgeResponse(List(JudgeGroup("X", List(2)))))
  }

  it should "fail on a reply with no JSON object" in {
    ConceptJudge.parse("I could not produce groups.").isLeft shouldBe true
  }

  "reconcile" should "keep a clean partition as-is and trim concept names" in {
    val resp = JudgeResponse(List(JudgeGroup(" Funding ", List(0, 2)), JudgeGroup("Oversight", List(1))))
    ConceptJudge.reconcile(resp, 3) should contain theSameElementsInOrderAs List(
      com.repcheck.decomposition.evaluation.GoldGroup("g0", List(0, 2), "Funding", Nil),
      com.repcheck.decomposition.evaluation.GoldGroup("g1", List(1), "Oversight", Nil),
    )
  }

  it should "let the first group win overlapping sections" in {
    val resp = JudgeResponse(List(JudgeGroup("A", List(0, 1)), JudgeGroup("B", List(1, 2))))
    val out  = ConceptJudge.reconcile(resp, 3)
    out.map(_.sectionIndices) shouldBe List(List(0, 1), List(2))
  }

  it should "sweep dropped sections into a trailing ungrouped group" in {
    val resp = JudgeResponse(List(JudgeGroup("A", List(0))))
    val out  = ConceptJudge.reconcile(resp, 3)
    out.map(_.conceptLabel) should contain("ungrouped")
    out.flatMap(_.sectionIndices).sorted shouldBe List(0, 1, 2)
  }

  it should "drop out-of-range indices" in {
    val resp = JudgeResponse(List(JudgeGroup("A", List(0, 9))))
    ConceptJudge.reconcile(resp, 2).flatMap(_.sectionIndices).sorted shouldBe List(0, 1)
  }

}
