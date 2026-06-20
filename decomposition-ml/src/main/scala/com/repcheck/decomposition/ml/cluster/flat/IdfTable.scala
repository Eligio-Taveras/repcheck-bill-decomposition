package com.repcheck.decomposition.ml.cluster.flat

/**
 * Inverse-document-frequency table computed over the training corpus, used to turn a section's text into TF-IDF term
 * weights. Rare-across-the-corpus terms carry more weight; terms never seen during training contribute nothing.
 */
final case class IdfTable(corpusSectionCount: Int, termIdf: Map[String, Double]) {

  /** TF-IDF weights for one section: log-dampened term frequency times the term's trained IDF. */
  def tfidf(text: String): Map[String, Double] = {
    val termCounts = TextTokenizer.tokens(text).groupMapReduce(identity)(_ => 1)(_ + _)
    termCounts.map { case (term, count) => term -> (1.0 + math.log(count.toDouble)) * termIdf.getOrElse(term, 0.0) }
  }

  /** The `howMany` highest-weighted TF-IDF terms in the text. */
  def topTerms(text: String, howMany: Int): Set[String] =
    tfidf(text).toVector.sortBy { case (_, weight) => -weight }.take(howMany).iterator.map(_._1).toSet

}
