package com.repcheck.decomposition.evaluation

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import scala.io.Source

import cats.effect.unsafe.implicits.global
import cats.syntax.all._

import com.repcheck.decomposition.conformance.{ConformanceContract, Corpus}
import com.repcheck.decomposition.evaluation.cluster.{ClusteringConfig, SmileHacClusterer}
import com.repcheck.decomposition.evaluation.compare.GroupingComparison
import com.repcheck.decomposition.evaluation.embed.EmbeddingTransform
import com.repcheck.decomposition.evaluation.metrics.{ClusteringMetrics, EmbeddingMetrics}
import com.repcheck.decomposition.evaluation.wiring.EmbeddingHarness
import com.repcheck.decomposition.text.{DefaultSectionParser, TextFormat}
import com.repcheck.utils.tags.E2ETest

/**
 * DP0-5b: scores the production HAC PREDICTION against the Claude REFERENCE, testing whether embedding anisotropy
 * correction (none / center / standardize, §10b) lets the omnibus silhouette recover from collapsing to k=2. Branches
 * tuned separately (tight: threshold dMax sweep; omnibus: silhouette maxK sweep). Embeddings are computed once and
 * cached on disk; all transforms are post-processing. Emits COMPARISON_REPORT.md. E2ETest (live Ollama) + update gate:
 *
 * sbt -Dupdate.gold=true "evaluation/testOnly *ComparisonReport -- -n com.repcheck.tags.E2ETest"
 */
class ComparisonReport extends ConformanceContract {

  private val parser        = new DefaultSectionParser
  private def updateMode    = sys.props.get("update.gold").contains("true")
  private val EmbedChunk    = 64
  private val EmbedMaxChars = 4000
  private val TrivialMaxSec = 3
  private val OmnibusMinSec = 50
  private val DMaxGrid      = (1 to 30).map(_ * 0.1).toList // 0.1 .. 3.0 (omnibus bills want a high cut)
  private val MaxKGrid      = List(10, 20, 30, 40, 60, 80, 120)
  private val ProfileMaxK   = MaxKGrid.foldLeft(0)((a, b) => math.max(a, b))
  private val Transforms    = List("none", "center", "standardize")
  private val CacheDir      = Paths.get("target", "embed-cache")
  private val AnisoSample   = 250

  final private case class BillData(
    versionId: String,
    sections: Int,
    raw: IndexedSeq[Vector[Double]],
    ref: Vector[Int],
  ) {
    def isOmnibus: Boolean      = sections > OmnibusMinSec
    def isTunableTight: Boolean = !isOmnibus && sections > TrivialMaxSec
  }

  private def embedAll(texts: List[String]): List[Array[Float]] =
    EmbeddingHarness
      .resource()
      .use(svc => texts.grouped(EmbedChunk).toList.traverse(chunk => svc.embedBatch(chunk.map(_.take(EmbedMaxChars)))))
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

  private def bestSilhouetteK(
    profile: IndexedSeq[(Int, Double)],
    maxK: Int,
    fitted: SmileHacClusterer.Fitted,
  ): Vector[Int] = {
    val candidates = profile.filter(_._1 <= maxK)
    if (candidates.isEmpty) fitted.cut(fitted.kThreshold(Double.MaxValue))
    else
      fitted.cut(
        candidates
          .foldLeft((Double.NegativeInfinity, 2)) { case ((bs, bk), (k, s)) => if (s > bs) (s, k) else (bs, bk) }
          ._2
      )
  }

  "DP0-5b comparison" should "compare HAC vs reference under anisotropy transforms and emit a report" taggedAs E2ETest in {
    assume(updateMode, "run forked with -Dupdate.gold=true (live Ollama)")

    val bills = GoldSet.pilot.bills.map { gold =>
      val bill   = Corpus.bills.find(_.versionId == gold.versionId).getOrElse(fail(s"${gold.versionId} not in corpus"))
      val parsed = parser.parse(bill.content, TextFormat.fromFormatType(bill.format))
      val n      = parsed.sections.size
      val raw    = embedCached(gold.versionId, parsed.sections.map(_.content))
      val ref    = GroupingComparison.labelsFromGroups(gold.groups.map(_.sectionIndices), n)
      info(s"${gold.versionId}: $n sections")
      BillData(gold.versionId, n, raw, ref)
    }
    val pooled = bills.flatMap(_.raw)

    val analyses = Transforms.map(t => analyze(t, bills, pooled))
    writeReport(analyses)
    info("done")
    succeed
  }

  final private case class Analysis(
    transform: String,
    anisotropy: Double,
    bestDMaxTight: Double,
    tightAriThreshold: Double,
    bestDMaxOmni: Double,
    omniAriThreshold: Double,
    omniNmiThreshold: Double,
    bestMaxK: Int,
    omniAriSilhouette: Double,
    omniNmiSilhouette: Double,
    perBill: List[(String, String, Int, Int, Int, Double, Double)], // vid, branch, sec, hacK, refK, ari, nmi
  )

  private def analyze(transform: String, bills: List[BillData], pooled: Seq[Vector[Double]]): Analysis = {
    val gMean = EmbeddingTransform.mean(pooled)
    val gStd  = EmbeddingTransform.std(pooled, gMean)
    def tf(v: Vector[Double]): Vector[Double] = transform.toLowerCase match {
      case "center"      => EmbeddingTransform.center(v, gMean)
      case "standardize" => EmbeddingTransform.standardize(v, gMean, gStd)
      case _             => v
    }
    val aniso = EmbeddingMetrics.anisotropy(pooled.iterator.take(AnisoSample).map(tf).toVector)

    final case class Fit(b: BillData, fitted: SmileHacClusterer.Fitted, profile: IndexedSeq[(Int, Double)])
    val fits = bills.map { b =>
      val vecs   = b.raw.map(tf)
      val fitted = SmileHacClusterer.fit(vecs, ClusteringConfig())
      val prof =
        if (b.isOmnibus) (2 to math.min(ProfileMaxK, b.sections - 1)).map(k => k -> fitted.silhouetteAt(k))
        else IndexedSeq.empty
      Fit(b, fitted, prof)
    }
    val tight                              = fits.filter(_.b.isTunableTight)
    val omni                               = fits.filter(_.b.isOmnibus)
    def thr(d: Double): Fit => Vector[Int] = f => f.fitted.cut(f.fitted.kThreshold(d))
    def sil(k: Int): Fit => Vector[Int]    = f => bestSilhouetteK(f.profile, k, f.fitted)

    def meanAri(fs: List[Fit], labels: Fit => Vector[Int]): Double =
      if (fs.isEmpty) 0.0 else fs.map(f => ClusteringMetrics.adjustedRandIndex(labels(f), f.b.ref)).sum / fs.size
    def meanNmi(fs: List[Fit], labels: Fit => Vector[Int]): Double =
      if (fs.isEmpty) 0.0
      else fs.map(f => ClusteringMetrics.normalizedMutualInformation(labels(f), f.b.ref)).sum / fs.size

    def bestDMaxOver(fs: List[Fit]): Double =
      DMaxGrid
        .map(d => d -> meanAri(fs, thr(d)))
        .foldLeft((Double.NaN, Double.NegativeInfinity)) { case ((bd, bs), (d, s)) => if (s > bs) (d, s) else (bd, bs) }
        ._1

    // threshold dMax tuned SEPARATELY per branch (they need different cut heights); silhouette maxK for omnibus
    val bestDMaxTight = bestDMaxOver(tight)
    val bestDMaxOmni  = bestDMaxOver(omni)
    val bestMaxK = MaxKGrid
      .map(k => k -> meanAri(omni, sil(k)))
      .foldLeft((0, Double.NegativeInfinity)) { case ((bk, bs), (k, s)) => if (s > bs) (k, s) else (bk, bs) }
      ._1

    def branch(f: Fit): String = if (f.b.isOmnibus) "omnibus" else if (f.b.isTunableTight) "tight" else "trivial"
    // per-bill under the threshold cut at the branch's own best dMax (the candidate production config)
    val perBill = fits.map { f =>
      val d      = if (f.b.isOmnibus) bestDMaxOmni else bestDMaxTight
      val labels = thr(d)(f)
      (
        f.b.versionId,
        branch(f),
        f.b.sections,
        labels.distinct.size,
        f.b.ref.distinct.size,
        ClusteringMetrics.adjustedRandIndex(labels, f.b.ref),
        ClusteringMetrics.normalizedMutualInformation(labels, f.b.ref),
      )
    }
    Analysis(
      transform,
      aniso,
      bestDMaxTight,
      meanAri(tight, thr(bestDMaxTight)),
      bestDMaxOmni,
      meanAri(omni, thr(bestDMaxOmni)),
      meanNmi(omni, thr(bestDMaxOmni)),
      bestMaxK,
      meanAri(omni, sil(bestMaxK)),
      meanNmi(omni, sil(bestMaxK)),
      perBill,
    )
  }

  private def writeReport(analyses: List[Analysis]): Unit = {
    val summary = analyses.map(a =>
      f"| ${a.transform} | ${a.anisotropy}%.3f | ${a.bestDMaxTight}%.1f | ${a.tightAriThreshold}%.3f | ${a.bestDMaxOmni}%.1f | ${a.omniAriThreshold}%.3f | ${a.omniNmiThreshold}%.3f | ${a.omniAriSilhouette}%.3f | ${a.omniNmiSilhouette}%.3f |"
    )
    val best          = analyses.find(_.transform == "standardize").orElse(analyses.headOption)
    val bestDMaxTight = best.map(_.bestDMaxTight).getOrElse(0.0)
    val bestDMaxOmni  = best.map(_.bestDMaxOmni).getOrElse(0.0)
    val billRows = best.toList.flatMap(_.perBill).map {
      case (vid, br, sec, hacK, refK, ari, nmi) =>
        f"| $vid | $br | $sec | $hacK | $refK | $ari%.3f | $nmi%.3f |"
    }
    val lines =
      List(
        "# DP-0 comparison — HAC vs Claude reference: threshold vs silhouette, with anisotropy correction",
        "",
        "For omnibus bills, the threshold cut (dMax tuned SEPARATELY per branch) is compared head-to-head against the",
        "silhouette cut, under each embedding transform (§10b anisotropy correction).",
        "",
        "## Omnibus: threshold (own dMax) vs silhouette",
        "",
        "| transform | anisotropy | tight dMax | tight ARI | omni dMax | omni ARI (thr) | omni NMI (thr) | omni ARI (sil) | omni NMI (sil) |",
        "|---|---|---|---|---|---|---|---|---|",
      ) ++ summary ++
        List(
          "",
          "Anisotropy = mean pairwise cosine over a pooled sample (lower = more isotropic). thr = threshold cut, sil = silhouette.",
          "",
          f"## Per-bill, threshold cut @ standardize (tight dMax=$bestDMaxTight%.1f, omni dMax=$bestDMaxOmni%.1f)",
          "",
          "| bill | branch | sections | HAC groups | ref groups | ARI | NMI |",
          "|---|---|---|---|---|---|---|",
        ) ++ billRows
    val _ = Files.write(Paths.get("COMPARISON_REPORT.md"), lines.mkString("\n").getBytes(StandardCharsets.UTF_8))
  }

}
