package com.repcheck.decomposition.evaluation

import com.repcheck.decomposition.conformance.{ConformanceContract, Corpus}

/**
 * Integrity laws over the loaded pilot gold set. Boundary laws apply now; the grouping laws are ready for when DP0-4 +
 * review fill `groups` (vacuously true while groups are empty).
 */
class GoldSetSpec extends ConformanceContract {

  private val gold      = GoldSet.pilot
  private val corpusIds = Corpus.bills.map(_.versionId).toSet

  "the pilot gold set" should "cover exactly the 8 selected bills" in {
    gold.bills.map(_.versionId).sorted shouldBe GoldPilot.versionIds.sorted
  }

  it should "reference only bills present in the shared corpus" in {
    gold.bills.foreach(b => corpusIds should contain(b.versionId))
  }

  it should "have contiguous, ordered, unique section indices per bill" in {
    gold.bills.foreach(b => withClue(s"${b.versionId}: ")(b.sections.map(_.index) shouldBe b.sections.indices.toList))
  }

  it should "have at least one section with positive length per bill" in {
    gold.bills.foreach { b =>
      withClue(s"${b.versionId}: ") {
        b.sections should not be empty
        b.sections.foreach(s => withClue(s"section ${s.index}: ")(s.charLength should be > 0))
      }
    }
  }

  it should "use a known label status" in {
    gold.bills.foreach(b => withClue(s"${b.versionId}: ")(GoldSet.LabelStatuses should contain(b.labelStatus)))
  }

  it should "have draft groups that partition every section once" in {
    gold.bills.foreach { b =>
      withClue(s"${b.versionId}: ") {
        b.groups should not be empty
        b.groups.flatMap(_.sectionIndices).sorted shouldBe b.sections.map(_.index)
      }
    }
  }

  it should "have well-formed groups whenever groups are present" in {
    gold.bills.foreach { b =>
      val sectionIdx  = b.sections.map(_.index).toSet
      val allAssigned = b.groups.flatMap(_.sectionIndices)
      withClue(s"${b.versionId} groups: ") {
        b.groups.foreach(g => g.sectionIndices.foreach(i => sectionIdx should contain(i)))
        allAssigned.distinct.size shouldBe allAssigned.size // sections belong to at most one group
        b.groups.map(_.groupId).distinct.size shouldBe b.groups.size
      }
    }
  }

}
