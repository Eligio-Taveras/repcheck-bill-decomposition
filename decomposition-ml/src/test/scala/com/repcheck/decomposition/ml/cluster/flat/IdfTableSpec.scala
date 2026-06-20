package com.repcheck.decomposition.ml.cluster.flat

class IdfTableSpec extends com.repcheck.decomposition.conformance.ConformanceContract {

  private val idf = IdfTable(10, Map("federal" -> 2.0, "reserve" -> 3.0, "federal_reserve" -> 1.0))

  "tfidf" should "weight terms by log-damped frequency times IDF" in {
    val weights = idf.tfidf("federal federal reserve")
    math.abs(weights.getOrElse("federal", 0.0) - (1.0 + math.log(2.0)) * 2.0) should be < 1e-9
    weights.getOrElse("reserve", 0.0) shouldBe 3.0 // (1 + log 1) * 3
  }

  it should "give zero weight to terms absent from the IDF table" in {
    val weights = idf.tfidf("federal federal reserve")
    weights.getOrElse("federal_federal", -1.0) shouldBe 0.0 // present as a token but unseen in the corpus
  }

  "topTerms" should "return the highest-weighted terms" in {
    idf.topTerms("federal federal reserve", 2) shouldBe Set("federal", "reserve")
  }

}
