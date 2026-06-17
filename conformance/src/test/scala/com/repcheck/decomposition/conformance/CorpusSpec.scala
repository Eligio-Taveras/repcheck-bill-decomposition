package com.repcheck.decomposition.conformance

class CorpusSpec extends ConformanceContract {

  "the conformance corpus" should "load the pinned 25 bills with non-empty content" in {
    val _ = Corpus.bills.size shouldBe 25
    all(Corpus.bills.map(_.content.length)) should be > 0
  }

  it should "span all three format paths" in {
    Corpus.bills.map(_.format).distinct.sorted shouldBe List("Formatted Text", "Formatted XML", "PDF")
  }

  it should "cover all eight bill types" in {
    Corpus.bills.map(_.billType).distinct.sorted shouldBe
      List("hconres", "hjres", "hr", "hres", "s", "sconres", "sjres", "sres")
  }

}
