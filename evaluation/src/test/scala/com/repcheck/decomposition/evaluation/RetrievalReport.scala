package com.repcheck.decomposition.evaluation

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import scala.io.{Codec, Source}

import cats.effect.unsafe.implicits.global
import cats.syntax.all._

import com.repcheck.decomposition.conformance.{ConformanceContract, Corpus}
import com.repcheck.decomposition.evaluation.cluster.{ClusteringConfig, SmileHacClusterer}
import com.repcheck.decomposition.evaluation.embed.EmbeddingTransform
import com.repcheck.decomposition.evaluation.metrics.{EmbeddingMetrics, RetrievalMetrics}
import com.repcheck.decomposition.evaluation.wiring.EmbeddingHarness
import com.repcheck.decomposition.text.{DefaultSectionParser, TextFormat}
import com.repcheck.utils.tags.E2ETest

/**
 * DP0-6b: the §10b retrieval GATE. For each of the 269 reference-concept queries, four methods retrieve units (ranked
 * by query↔unit cosine, expanded to sections) and we score precision@k / R-precision / recall / MRR against the
 * concept's own sections. The question: does DECOMPOSITION (concept groups) beat B1 whole-bill, B2 raw-sections, and B3
 * Congress.gov-subject buckets? Decomposition should win on granularity — one group retrieval covers a multi-section
 * concept cleanly (precision AND recall), where whole-bill/subject buckets are too coarse and raw sections fragment it.
 * Embeddings cached on disk; standardized (the production transform). E2ETest (live Ollama) + update gate.
 */
class RetrievalReport extends ConformanceContract {

  private val parser        = new DefaultSectionParser
  private def updateMode    = sys.props.get("update.gold").contains("true")
  private val EmbedChunk    = 64
  private val EmbedMaxChars = 4000
  private val CacheDir      = Paths.get("target", "embed-cache")
  private val ProdConfig = ClusteringConfig() // standardize-upstream + 0.1*cosine + 0.9*graded-hierarchy, avg linkage

  final private case class Sec(ref: SectionRef, emb: Vector[Double], parents: List[String])
  final private case class Bucket(sections: List[SectionRef], emb: Vector[Double])
  final private case class Scores(mrr: Double, p5: Double, rPrec: Double, recall10: Double)

  // ---- embedding (cached) ----
  private def embedAll(texts: List[String]): List[Array[Float]] =
    EmbeddingHarness
      .resource()
      .use(svc => texts.grouped(EmbedChunk).toList.traverse(c => svc.embedBatch(c.map(_.take(EmbedMaxChars)))))
      .map(_.flatten)
      .unsafeRunSync()

  private def embedCached(vid: String, texts: List[String]): IndexedSeq[Vector[Double]] = {
    val f = CacheDir.resolve(s"$vid-${texts.size}.txt")
    if (Files.exists(f)) {
      val src = Source.fromFile(f.toFile, "UTF-8")
      try src.getLines().filter(_.nonEmpty).map(_.split(' ').iterator.map(_.toDouble).toVector).toIndexedSeq
      finally src.close()
    } else {
      val vecs = embedAll(texts).map(a => a.iterator.map(_.toDouble).toVector).toIndexedSeq
      val _    = Files.createDirectories(CacheDir)
      val _    = Files.write(f, vecs.map(_.mkString(" ")).mkString("\n").getBytes(StandardCharsets.UTF_8))
      vecs
    }
  }

  private def readResource(path: String): String =
    Option(getClass.getClassLoader.getResourceAsStream(path)) match {
      case Some(is) =>
        try Source.fromInputStream(is)(using Codec.UTF8).mkString
        finally is.close()
      case None => sys.error(s"resource not found: $path")
    }

  final private case class SubjectsDoc(subjects: List[String]) derives io.circe.Codec.AsObject

  private def subjectsOf(vid: String): List[String] =
    io.circe.parser.decode[SubjectsDoc](readResource(s"subjects/$vid.json")).fold(_ => Nil, _.subjects)

  // Form the decomposition groups via the consolidated PRODUCTION pipeline (blendedProximity = 0.1*cosine +
  // 0.9*graded-hierarchy), but cut at EXACTLY k = subject count — the gate scores the production grouping at the
  // reference granularity (guidedCut's robustness window is a separate, already-validated property).
  private def decompose(secs: IndexedSeq[Sec], k: Int): List[Bucket] =
    if (secs.sizeIs < 2) List(Bucket(secs.map(_.ref).toList, mean(secs.map(_.emb).toList)))
    else {
      val blended = SmileHacClusterer.blendedProximity(secs.map(_.emb), secs.map(_.parents), ProdConfig)
      val labels  = SmileHacClusterer.fitFromProximity(blended, ProdConfig.linkage).cut(math.max(1, k))
      labels.zipWithIndex
        .groupBy(_._1)
        .toList
        .map { case (_, idxs) => idxs.map(_._2) }
        .map(is => Bucket(is.map(i => secs(i).ref).toList, mean(is.map(i => secs(i).emb).toList)))
    }

  private def mean(vs: List[Vector[Double]]): Vector[Double] = {
    val dim = vs.headOption.map(_.size).getOrElse(0)
    if (vs.isEmpty) Vector.fill(dim)(0.0)
    else {
      val sum = vs.foldLeft(Vector.fill(dim)(0.0))((acc, v) => acc.lazyZip(v).map(_ + _))
      sum.map(_ / vs.size.toDouble)
    }
  }

  /**
   * Rank a method's units, expand each to its sections (best-cosine first), dedup. Unit ranking is either mean-pool
   * (cosine to the unit centroid) or max-pool (the unit's best single section — pinpoints like raw sections while still
   * returning the whole unit, lifting MRR without losing coverage).
   */
  private def rankedSections(
    units: List[Bucket],
    qEmb: Vector[Double],
    secScore: SectionRef => Double,
    maxPool: Boolean,
  ): List[SectionRef] =
    units
      .map(u =>
        (
          u,
          if (maxPool) u.sections.foldLeft(Double.NegativeInfinity)((m, s) => math.max(m, secScore(s)))
          else EmbeddingMetrics.cosine(qEmb, u.emb),
        )
      )
      .sortBy(-_._2)
      .flatMap(_._1.sections.sortBy(s => -secScore(s)))
      .distinct

  private def score(ranked: List[SectionRef], relevant: Set[SectionRef]): Scores = {
    val recall10 =
      if (relevant.isEmpty) 0.0 else ranked.take(10).count(relevant.contains).toDouble / relevant.size.toDouble
    Scores(
      RetrievalMetrics.reciprocalRank(ranked, relevant),
      RetrievalMetrics.precisionAtK(ranked, relevant, 5),
      RetrievalMetrics.precisionAtK(ranked, relevant, math.max(1, relevant.size)),
      recall10,
    )
  }

  "DP0-6 retrieval gate" should "score decomposition vs B1/B2/B3 baselines and emit a report" taggedAs E2ETest in {
    assume(updateMode, "run forked with -Dupdate.gold=true (live Ollama)")

    // sections across the whole corpus, standardized
    val perBill = GoldSet.pilot.bills.map { gold =>
      val bill   = Corpus.bills.find(_.versionId == gold.versionId).getOrElse(fail(s"${gold.versionId} not in corpus"))
      val parsed = parser.parse(bill.content, TextFormat.fromFormatType(bill.format))
      val raw    = embedCached(gold.versionId, parsed.sections.map(_.content))
      (gold.versionId, parsed, raw, gold.groups.count(!_.conceptLabel.trim.equalsIgnoreCase("ungrouped")))
    }
    val pooled = perBill.flatMap(_._3)
    val gMean  = EmbeddingTransform.mean(pooled)
    val gStd   = EmbeddingTransform.std(pooled, gMean)

    val billSecs: Map[String, IndexedSeq[Sec]] = perBill.map {
      case (vid, parsed, raw, _) =>
        vid -> parsed.sections.toIndexedSeq.zip(raw).map {
          case (s, e) => Sec(SectionRef(vid, s.sectionIndex), EmbeddingTransform.standardize(e, gMean, gStd), s.parents)
        }
    }.toMap
    val secEmb: Map[SectionRef, Vector[Double]] = billSecs.values.flatten.map(s => s.ref -> s.emb).toMap

    // ---- build the four methods' units (corpus-wide) ----
    val decompUnits = perBill.flatMap { case (vid, _, _, k) => decompose(billSecs(vid), k) }
    val billUnits = perBill.map {
      case (vid, _, _, _) => Bucket(billSecs(vid).map(_.ref).toList, mean(billSecs(vid).map(_.emb).toList))
    }
    val sectionUnits = billSecs.values.flatten.toList.map(s => Bucket(List(s.ref), s.emb))
    val subjectUnits = perBill
      .flatMap { case (vid, _, _, _) => subjectsOf(vid).map(subj => subj -> billSecs(vid).map(_.ref).toList) }
      .groupBy(_._1)
      .toList
      .map { case (_, entries) => entries.flatMap(_._2) }
      .map(refs => Bucket(refs, mean(refs.map(secEmb))))
    val methods = List(
      ("Decomposition", decompUnits),
      ("B1 whole-bill", billUnits),
      ("B2 raw-sections", sectionUnits),
      ("B3 Congress.gov subjects", subjectUnits),
    )
    methods.foreach { case (name, us) => info(s"$name: ${us.size} units") }

    // embed all queries once, standardized
    val queries = RetrievalGold.load.queries
    val qEmbs = embedAll(queries.map(_.text))
      .map(a => EmbeddingTransform.standardize(a.iterator.map(_.toDouble).toVector, gMean, gStd))
    info(s"queries: ${queries.size}")

    // score every query against every method under both unit-ranking poolings; the per-section query cosine is
    // computed ONCE per query and reused across methods + poolings + within-unit ordering (B3 units overlap heavily).
    val poolings = List(("mean-pool", false), ("max-pool", true))
    val perQuery: List[Map[(String, String), Scores]] = queries.zip(qEmbs).map {
      case (q, qe) =>
        val secScoreMap = secEmb.iterator.map { case (ref, e) => ref -> EmbeddingMetrics.cosine(qe, e) }.toMap
        val secScore: SectionRef => Double = s => secScoreMap.getOrElse(s, -1.0)
        val rel                            = q.relevant.toSet
        (for {
          (pname, maxPool) <- poolings
          (name, units)    <- methods
        } yield (pname, name) -> score(rankedSections(units, qe, secScore, maxPool), rel)).toMap
    }
    val results: Map[(String, String), List[Scores]] =
      (for { (pname, _) <- poolings; (name, _) <- methods } yield (pname, name) -> perQuery.map(_((pname, name)))).toMap

    writeReport(
      poolings.map(_._1),
      methods.map(_._1),
      results,
      queries,
      methods.map { case (n, u) => n -> u.size }.toMap,
    )
    info("done")
    succeed
  }

  private def writeReport(
    poolings: List[String],
    order: List[String],
    results: Map[(String, String), List[Scores]],
    queries: List[RetrievalQuery],
    unitCounts: Map[String, Int],
  ): Unit = {
    def avg(xs: List[Double]): Double = if (xs.isEmpty) 0.0 else xs.sum / xs.size.toDouble
    // multi-section concepts are where granularity matters; split them out
    val multiIdx = queries.zipWithIndex.filter(_._1.relevant.sizeIs > 1).map(_._2).toSet
    def rows(pooling: String, filter: Int => Boolean): List[String] =
      order.map { m =>
        val s = results((pooling, m)).zipWithIndex.filter { case (_, i) => filter(i) }.map(_._1)
        f"| $m | ${unitCounts(m)} | ${avg(s.map(_.mrr))}%.3f | ${avg(s.map(_.p5))}%.3f | ${avg(s.map(_.rPrec))}%.3f | ${avg(s.map(_.recall10))}%.3f |"
      }
    val header = List(
      "| method | units | MRR | P@5 | R-precision | recall@10 |",
      "|---|---|---|---|---|---|",
    )
    def section(pooling: String): List[String] =
      List(
        "",
        s"## $pooling — unit ranking",
        "",
        s"### All queries (${queries.size})",
        "",
      ) ++ header ++ rows(pooling, _ => true) ++
        List(
          "",
          s"### Multi-section concepts only (${multiIdx.size})",
          "",
        ) ++ header ++ rows(pooling, multiIdx.contains)
    val lines =
      List(
        "# DP0-6 retrieval gate — does decomposition beat the baselines?",
        "",
        s"${queries.size} reference-concept queries over the 25-bill corpus. Each method retrieves units, expanded to",
        "sections, scored against the concept's own sections. Standardized embeddings. R-precision = precision@|relevant|.",
        "**mean-pool** ranks units by their centroid cosine; **max-pool** ranks by the unit's best single section (then",
        "still returns the whole unit) — pinpoints like raw sections without losing coverage.",
      ) ++ poolings.flatMap(section)
    val _ = Files.write(Paths.get("RETRIEVAL_REPORT.md"), lines.mkString("\n").getBytes(StandardCharsets.UTF_8))
  }

}
