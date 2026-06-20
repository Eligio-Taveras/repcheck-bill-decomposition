package com.repcheck.decomposition.ml.cluster.flat

import com.repcheck.decomposition.ml.embed.{EmbeddingTransform, StandardizationStats}

/**
 * Per-bill pairwise matrices, computed once up front. Every entry compares two sections of the same bill:
 *   - `embeddingCosine` — standardized-embedding cosine ("pure topic" similarity),
 *   - `tfidfCosine` — TF-IDF cosine (lexical / word-overlap similarity),
 *   - `affinityMatrix` — the affinity model's probability the two sections share a concept.
 */
final case class SectionContext(
  embeddingCosine: Vector[Vector[Double]],
  tfidfCosine: Vector[Vector[Double]],
  affinityMatrix: Vector[Vector[Double]],
  sectionCount: Int,
)

object SectionContext {

  /** Precompute all three matrices for a bill's sections using the trained artifacts. */
  def build(
    sections: Vector[FlatSection],
    artifacts: FlatGroupingArtifacts,
    embeddingStats: StandardizationStats,
  ): SectionContext = {
    val sectionCount = sections.size
    val standardizedEmbeddings =
      sections.map(section =>
        EmbeddingTransform.standardize(section.embedding.toDoubles, embeddingStats.mean, embeddingStats.std)
      )
    val tfidfBySection     = sections.map(section => artifacts.idf.tfidf(section.text))
    val topTermsBySection  = sections.map(section => artifacts.idf.topTerms(section.text, artifacts.topTermCount))
    val citationsBySection = sections.map(section => TextTokenizer.citations(section.text))

    val embeddingCosine = Vector.tabulate(sectionCount, sectionCount) { (i, j) =>
      if (i == j) 1.0 else VectorSimilarity.cosine(standardizedEmbeddings(i), standardizedEmbeddings(j))
    }
    val tfidfCosine = Vector.tabulate(sectionCount, sectionCount) { (i, j) =>
      if (i == j) 1.0 else VectorSimilarity.cosineSparse(tfidfBySection(i), tfidfBySection(j))
    }
    // The affinity model scores each section pair from five features, in the order it was trained on.
    val affinityMatrix = Vector.tabulate(sectionCount, sectionCount) { (i, j) =>
      if (i == j) 1.0
      else
        artifacts.affinityModel.predict(
          Vector(
            embeddingCosine(i)(j),                                                  // emb_cos
            tfidfCosine(i)(j),                                                      // tfidf_cos
            VectorSimilarity.jaccard(topTermsBySection(i), topTermsBySection(j)),   // topterm_jaccard
            VectorSimilarity.jaccard(citationsBySection(i), citationsBySection(j)), // cite_jaccard
            1.0 - math.abs(i - j).toDouble / sectionCount,                          // position (adjacency)
          )
        )
    }
    SectionContext(embeddingCosine, tfidfCosine, affinityMatrix, sectionCount)
  }

}
