package com.repcheck.decomposition.evaluation

class RetrievalGoldSpec extends com.repcheck.decomposition.conformance.ConformanceContract {

  private val gold         = RetrievalGold.load
  private val sectionCount = GoldSet.pilot.bills.map(b => b.versionId -> b.sections.size).toMap

  "the retrieval gold" should "have one query per non-ungrouped reference concept" in {
    val expected = GoldSet.pilot.bills.flatMap(_.groups).count(!_.conceptLabel.trim.equalsIgnoreCase("ungrouped"))
    gold.queries.size shouldBe expected
  }

  it should "have unique query ids" in {
    gold.queries.map(_.queryId).distinct.size shouldBe gold.queries.size
  }

  it should "carry non-empty query text and at least one relevant section each" in {
    gold.queries.foreach { q =>
      withClue(s"${q.queryId}: ") {
        q.text.trim should not be empty
        q.relevant should not be empty
      }
    }
    succeed
  }

  it should "reference only pilot bills with in-range section indices" in {
    gold.queries.foreach { q =>
      withClue(s"${q.queryId}: ") {
        GoldPilot.versionIds should contain(q.versionId)
        q.relevant.foreach { r =>
          r.versionId shouldBe q.versionId
          r.sectionIndex should be >= 0
          r.sectionIndex should be < sectionCount(q.versionId)
        }
      }
    }
    succeed
  }

}
