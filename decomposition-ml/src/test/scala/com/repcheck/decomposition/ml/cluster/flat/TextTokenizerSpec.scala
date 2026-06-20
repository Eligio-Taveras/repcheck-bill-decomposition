package com.repcheck.decomposition.ml.cluster.flat

class TextTokenizerSpec extends com.repcheck.decomposition.conformance.ConformanceContract {

  "tokens" should "lower-case, drop stopwords and short words, and append consecutive bigrams" in {
    TextTokenizer.tokens("The Federal Reserve shall regulate banks") shouldBe
      Vector("federal", "reserve", "regulate", "banks", "federal_reserve", "reserve_regulate", "regulate_banks")
  }

  it should "drop words shorter than three letters" in {
    TextTokenizer.tokens("an ox is very big") should (contain("very").and(contain("big")).and(not(contain("ox"))))
  }

  it should "return nothing for text with no qualifying words" in {
    TextTokenizer.tokens("a an is of") shouldBe Vector.empty
  }

  "citations" should "extract U.S.C., Public Law, and named-Act references in normalized form" in {
    val found = TextTokenizer.citations("Under 12 U.S.C. 1841 and Public Law 117-2 the Clean Air Act of 1970 applies")
    found should (contain("usc:12:1841").and(contain("pl:117-2")).and(contain("act:clean air")))
  }

  it should "return an empty set when there are no citations" in {
    TextTokenizer.citations("plain language with no references") shouldBe empty
  }

}
