package com.repcheck.decomposition.evaluation.judge

import com.repcheck.decomposition.conformance.ConformanceContract
import com.repcheck.decomposition.evaluation.GoldGroup
import com.repcheck.decomposition.evaluation.judge.ConceptJudge.{JudgeGroup, Section}

class ConceptJudgeSpec extends ConformanceContract {

  private def sec(i: Int, len: Int): Section = Section(i, s"Sec. $i", "x" * len)

  "batch" should "keep everything in one batch when under budget" in {
    val secs = List(sec(0, 100), sec(1, 100), sec(2, 100))
    ConceptJudge.batch(secs, budget = 10000) shouldBe List(secs)
  }

  it should "split into multiple batches (multiple requests) when over budget" in {
    val secs = List(sec(0, 500), sec(1, 500), sec(2, 500), sec(3, 500))
    val out  = ConceptJudge.batch(secs, budget = 1100) // ~516 cost each → ~2 per batch
    out.size should be > 1
    out.flatten.map(_.index) shouldBe List(0, 1, 2, 3) // order preserved, nothing dropped
  }

  it should "give every oversized section its own batch" in {
    val secs = List(sec(0, 9000), sec(1, 9000))
    ConceptJudge.batch(secs, budget = 1000).size shouldBe 2
  }

  it should "cap a batch by section count so the response can't truncate" in {
    val secs = (0 until 40).toList.map(i => sec(i, 50)) // tiny by chars, but 40 sections
    val out  = ConceptJudge.batch(secs, budget = 1000000, maxSections = 15)
    all(out.map(_.size)) should be <= 15
    out.flatten.map(_.index) shouldBe (0 until 40).toList
  }

  "parseDescribe" should "decode a fenced descriptions reply" in {
    val raw = "```json\n{\"descriptions\":[{\"index\":0,\"description\":\"Defines terms.\"}]}\n```"
    ConceptJudge.parseDescribe(raw).map(_.descriptions.map(_.index)) shouldBe Right(List(0))
  }

  "parseSummaryGroups" should "decode summary + groups" in {
    val raw = """{"summary":"A budget bill.","groups":[{"concept":"Funding","sectionIndices":[0,1]}]}"""
    ConceptJudge.parseSummaryGroups(raw).map(_.summary) shouldBe Right("A budget bill.")
  }

  it should "extract the object despite trailing code fences and prose" in {
    val raw =
      "```json\n{\"summary\":\"A bill with a } brace in text.\",\"groups\":[{\"concept\":\"X\",\"sectionIndices\":[0]}]}\n```\n\nHope that helps!"
    ConceptJudge.parseSummaryGroups(raw).map(_.groups.map(_.concept)) shouldBe Right(List("X"))
  }

  it should "fail on a reply with no JSON object" in {
    ConceptJudge.parseSummaryGroups("sorry, no json").isLeft shouldBe true
  }

  "reconcile" should "keep a clean partition, trimming concept names" in {
    val groups = List(JudgeGroup(" Funding ", List(0, 2)), JudgeGroup("Oversight", List(1)))
    ConceptJudge.reconcile(groups, 3) should contain theSameElementsInOrderAs List(
      GoldGroup("g0", List(0, 2), "Funding", Nil),
      GoldGroup("g1", List(1), "Oversight", Nil),
    )
  }

  it should "let the first group win overlapping sections" in {
    val groups = List(JudgeGroup("A", List(0, 1)), JudgeGroup("B", List(1, 2)))
    ConceptJudge.reconcile(groups, 3).map(_.sectionIndices) shouldBe List(List(0, 1), List(2))
  }

  it should "sweep dropped sections into a trailing ungrouped group" in {
    val out = ConceptJudge.reconcile(List(JudgeGroup("A", List(0))), 3)
    out.map(_.conceptLabel) should contain("ungrouped")
    out.flatMap(_.sectionIndices).sorted shouldBe List(0, 1, 2)
  }

  it should "drop out-of-range indices" in {
    ConceptJudge.reconcile(List(JudgeGroup("A", List(0, 9))), 2).flatMap(_.sectionIndices).sorted shouldBe List(0, 1)
  }

}
