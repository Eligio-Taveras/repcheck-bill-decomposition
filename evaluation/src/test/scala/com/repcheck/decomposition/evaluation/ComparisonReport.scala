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
  private val AlphaGrid     = (0 to 10).map(_ * 0.1).toList // structure blend: 0.0 (pure Title) .. 1.0 (pure cosine)

  final private case class BillData(
    versionId: String,
    sections: Int,
    raw: IndexedSeq[Vector[Double]],
    ref: Vector[Int],
    structuralKeys: IndexedSeq[String], // top-level parent Title per section (parser hierarchy); unique if none
  ) {
    def isOmnibus: Boolean      = sections > OmnibusMinSec
    def isTunableTight: Boolean = !isOmnibus && sections > TrivialMaxSec
    def isNonTrivial: Boolean   = isOmnibus || isTunableTight
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
      val keys   = parsed.sections.map(s => s.parents.headOption.getOrElse(s"_uniq_${s.sectionIndex}")).toIndexedSeq
      info(s"${gold.versionId}: $n sections")
      BillData(gold.versionId, n, raw, ref, keys)
    }
    val pooled = bills.flatMap(_.raw)

    val analyses   = Transforms.map(t => analyze(t, bills, pooled))
    val structure  = structureSweep(bills, pooled)
    val robustness = robustnessSweep(bills, pooled)
    writeReport(analyses, structure, robustness)
    info("done")
    succeed
  }

  final private case class StructPoint(alpha: Double, ari: Double, nmi: Double)

  final private case class StructBill(
    vid: String,
    sec: Int,
    k: Int,
    ariCosine: Double,
    ariBlend: Double,
    nmiBlend: Double,
  )

  /**
   * Structure-aware sweep on STANDARDIZED embeddings: blend cosine distance with a same-Title (0/1) distance, d =
   * alpha*cosine + (1-alpha)*structural, cut at k = subjects. Sweep alpha (1.0 = pure cosine baseline). The cosine
   * matrix is precomputed once per bill so the alpha sweep is cheap.
   */
  private def structureSweep(
    bills: List[BillData],
    pooled: Seq[Vector[Double]],
  ): (List[StructPoint], List[StructBill]) = {
    val gMean = EmbeddingTransform.mean(pooled)
    val gStd  = EmbeddingTransform.std(pooled, gMean)
    val prepared = bills.filter(_.isNonTrivial).map { b =>
      val vecs = b.raw.map(v => EmbeddingTransform.standardize(v, gMean, gStd))
      val cosD = Array.tabulate(vecs.size, vecs.size)((i, j) =>
        if (i == j) 0.0 else 1.0 - EmbeddingMetrics.cosine(vecs(i), vecs(j))
      )
      val strD = Array.tabulate(vecs.size, vecs.size)((i, j) =>
        if (i == j) 0.0 else if (b.structuralKeys(i) == b.structuralKeys(j)) 0.0 else 1.0
      )
      (b, cosD, strD)
    }
    def blend(cosD: Array[Array[Double]], strD: Array[Array[Double]], alpha: Double): Array[Array[Double]] =
      Array.tabulate(cosD.length, cosD.length)((i, j) => alpha * cosD(i)(j) + (1.0 - alpha) * strD(i)(j))
    def labelsAt(cosD: Array[Array[Double]], strD: Array[Array[Double]], k: Int, alpha: Double): Vector[Int] =
      SmileHacClusterer.fitFromProximity(blend(cosD, strD, alpha), "average").cut(k)

    val sweep = AlphaGrid.map { a =>
      val pairs = prepared.map {
        case (b, cosD, strD) =>
          val labels = labelsAt(cosD, strD, b.ref.distinct.size, a)
          (
            ClusteringMetrics.adjustedRandIndex(labels, b.ref),
            ClusteringMetrics.normalizedMutualInformation(labels, b.ref),
          )
      }
      StructPoint(a, pairs.map(_._1).sum / pairs.size, pairs.map(_._2).sum / pairs.size)
    }
    val bestAlpha = sweep
      .foldLeft((1.0, Double.NegativeInfinity)) { case ((ba, bs), p) => if (p.ari > bs) (p.alpha, p.ari) else (ba, bs) }
      ._1
    val perBill = prepared.map {
      case (b, cosD, strD) =>
        val k         = b.ref.distinct.size
        val cosLabels = labelsAt(cosD, strD, k, 1.0)
        val blLabels  = labelsAt(cosD, strD, k, bestAlpha)
        StructBill(
          b.versionId,
          b.sections,
          k,
          ClusteringMetrics.adjustedRandIndex(cosLabels, b.ref),
          ClusteringMetrics.adjustedRandIndex(blLabels, b.ref),
          ClusteringMetrics.normalizedMutualInformation(blLabels, b.ref),
        )
    }
    (sweep, perBill)
  }

  /**
   * Guided cut: the subject count is a GUIDE, not an exact split. Search k in a +/- tol window around `guideK` and let
   * the silhouette pick the natural k (a window keeps it from collapsing to k=2). The data decides; the subject count
   * only sets the neighborhood. (A merge-height gap criterion was tried and underperformed silhouette in the window.)
   */
  private def guidedCut(fitted: SmileHacClusterer.Fitted, guideK: Int, tol: Double): Vector[Int] = {
    val lo = math.max(2, math.round(guideK * (1.0 - tol)).toInt)
    val hi = math.min(fitted.n - 1, math.round(guideK * (1.0 + tol)).toInt)
    if (lo > hi) fitted.cut(guideK)
    else {
      val bestK = (lo to hi)
        .foldLeft((Double.NegativeInfinity, lo)) {
          case ((bs, bk), k) =>
            val s = fitted.silhouetteAt(k)
            if (s > bs) (s, k) else (bs, bk)
        }
        ._2
      fitted.cut(bestK)
    }
  }

  final private case class RobustPoint(factor: Double, exactAri: Double, guidedAri: Double)

  /**
   * Robustness: treat the subject count as imperfect (the endpoint will over/under-count). Perturb the true count by a
   * factor and compare forcing k = perturbed (exact) vs the guided cut, on the standardize + structure-blend
   * (alpha=0.1) config. The guided cut should degrade gracefully where exact-k falls apart.
   */
  private def robustnessSweep(bills: List[BillData], pooled: Seq[Vector[Double]]): List[RobustPoint] = {
    val gMean   = EmbeddingTransform.mean(pooled)
    val gStd    = EmbeddingTransform.std(pooled, gMean)
    val Alpha   = 0.1
    val Tol     = 0.3
    val Factors = List(0.5, 0.75, 1.0, 1.5, 2.0)
    val fits = bills.filter(_.isNonTrivial).map { b =>
      val vecs = b.raw.map(v => EmbeddingTransform.standardize(v, gMean, gStd))
      val blended = Array.tabulate(vecs.size, vecs.size)((i, j) =>
        if (i == j) 0.0
        else {
          val cos = 1.0 - EmbeddingMetrics.cosine(vecs(i), vecs(j))
          val str = if (b.structuralKeys(i) == b.structuralKeys(j)) 0.0 else 1.0
          Alpha * cos + (1.0 - Alpha) * str
        }
      )
      (b, SmileHacClusterer.fitFromProximity(blended, "average"))
    }
    Factors.map { factor =>
      val pairs = fits.map {
        case (b, f) =>
          val guideK = math.max(1, math.round(b.ref.distinct.size * factor).toInt)
          (
            ClusteringMetrics.adjustedRandIndex(f.cut(guideK), b.ref),
            ClusteringMetrics.adjustedRandIndex(guidedCut(f, guideK, Tol), b.ref),
          )
      }
      RobustPoint(factor, pairs.map(_._1).sum / pairs.size, pairs.map(_._2).sum / pairs.size)
    }
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
    cutKAri: Double, // cut at k = subjects, mean over non-trivial bills
    cutKNmi: Double,
    perBill: List[
      (String, String, Int, Int, Int, Double, Double, Double)
    ], // vid, branch, sec, hacK, subjects, ari, nmi, dMaxAtK
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

    // cut directly at k = the LLM subject count (= reference group count); the robust production mechanism
    def cutK(f: Fit): Vector[Int] = f.fitted.cut(f.b.ref.distinct.size)
    val nonTrivial                = tight ++ omni

    def branch(f: Fit): String = if (f.b.isOmnibus) "omnibus" else if (f.b.isTunableTight) "tight" else "trivial"
    // per-bill under cut@k=subjects, with the resulting cut height (the subjects → dMax mapping)
    val perBill = fits.map { f =>
      val kSubj  = f.b.ref.distinct.size
      val labels = f.fitted.cut(kSubj)
      (
        f.b.versionId,
        branch(f),
        f.b.sections,
        labels.distinct.size,
        kSubj,
        ClusteringMetrics.adjustedRandIndex(labels, f.b.ref),
        ClusteringMetrics.normalizedMutualInformation(labels, f.b.ref),
        f.fitted.heightForK(kSubj),
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
      meanAri(nonTrivial, cutK),
      meanNmi(nonTrivial, cutK),
      perBill,
    )
  }

  private def writeReport(
    analyses: List[Analysis],
    structure: (List[StructPoint], List[StructBill]),
    robustness: List[RobustPoint],
  ): Unit = {
    val (alphaSweep, structBills) = structure
    val alphaRows                 = alphaSweep.map(p => f"| ${p.alpha}%.1f | ${p.ari}%.3f | ${p.nmi}%.3f |")
    val bestAlpha = alphaSweep
      .foldLeft((1.0, Double.NegativeInfinity)) { case ((ba, bs), p) => if (p.ari > bs) (p.alpha, p.ari) else (ba, bs) }
      ._1
    val structRows = structBills.map(b =>
      f"| ${b.vid} | ${b.sec} | ${b.k} | ${b.ariCosine}%.3f | ${b.ariBlend}%.3f | ${b.nmiBlend}%.3f |"
    )
    val robustRows = robustness.map(r => f"| ${r.factor}%.2f | ${r.exactAri}%.3f | ${r.guidedAri}%.3f |")
    val summary = analyses.map(a =>
      f"| ${a.transform} | ${a.anisotropy}%.3f | ${a.bestDMaxTight}%.1f | ${a.tightAriThreshold}%.3f | ${a.bestDMaxOmni}%.1f | ${a.omniAriThreshold}%.3f | ${a.omniNmiThreshold}%.3f | ${a.omniAriSilhouette}%.3f | ${a.omniNmiSilhouette}%.3f |"
    )
    val cutKTable = analyses.map(a => f"| ${a.transform} | ${a.anisotropy}%.3f | ${a.cutKAri}%.3f | ${a.cutKNmi}%.3f |")
    val best      = analyses.find(_.transform == "standardize").orElse(analyses.headOption)
    val billRows = best.toList.flatMap(_.perBill).map {
      case (vid, br, sec, hacK, subjects, ari, nmi, dMaxAtK) =>
        f"| $vid | $br | $sec | $subjects | $hacK | $dMaxAtK%.2f | $ari%.3f | $nmi%.3f |"
    }
    val lines =
      List(
        "# DP-0 comparison — HAC vs Claude reference: cut at k = subjects",
        "",
        "The cut is determined by the subject count (cut the dendrogram at k = subjectCount; the LLM reference group",
        "count stands in for the endpoint's subject count). Compared against the tuned threshold and silhouette cuts,",
        "under each anisotropy transform (§10b).",
        "",
        "## Cut at k = subjects (mean over non-trivial bills)",
        "",
        "| transform | anisotropy | ARI | NMI |",
        "|---|---|---|---|",
      ) ++ cutKTable ++
        List(
          "",
          "## For comparison — tuned threshold vs silhouette (omnibus)",
          "",
          "| transform | anisotropy | tight dMax | tight ARI | omni dMax | omni ARI (thr) | omni NMI (thr) | omni ARI (sil) | omni NMI (sil) |",
          "|---|---|---|---|---|---|---|---|---|",
        ) ++ summary ++
        List(
          "",
          "Anisotropy = mean pairwise cosine over a pooled sample (lower = more isotropic). thr = threshold, sil = silhouette.",
          "",
          "## Per-bill @ standardize, cut at k = subjects (dMaxAtK = the cut height that yields k clusters)",
          "",
          "| bill | branch | sections | subjects (k) | HAC groups | dMaxAtK | ARI | NMI |",
          "|---|---|---|---|---|---|---|---|",
        ) ++ billRows ++
        List(
          "",
          "## Structure-aware distance (standardize + cut@k): blend cosine with same-Title prior",
          "",
          "d = alpha*cosine + (1-alpha)*structural (0 if same parser Title, else 1). alpha=1.0 is the pure-cosine baseline.",
          "",
          "| alpha | mean ARI | mean NMI |",
          "|---|---|---|",
        ) ++ alphaRows ++
        List(
          "",
          f"**Best alpha = $bestAlpha%.1f** (alpha=1.0 = pure cosine).",
          "",
          f"### Per-bill: pure cosine (alpha=1.0) vs structure-blend (alpha=$bestAlpha%.1f)",
          "",
          "| bill | sections | subjects (k) | ARI cosine | ARI blend | NMI blend |",
          "|---|---|---|---|---|---|",
        ) ++ structRows ++
        List(
          "",
          "## Subjects as a GUIDE, not exact: robustness to a wrong subject count",
          "",
          "The true count is perturbed by a factor (simulating the endpoint over/under-counting). exact = force",
          "k = perturbed count; guided = silhouette picks k in a +/-30% window around it. standardize + structure-blend.",
          "",
          "| count factor | exact-k ARI | guided ARI |",
          "|---|---|---|",
        ) ++ robustRows
    val _ = Files.write(Paths.get("COMPARISON_REPORT.md"), lines.mkString("\n").getBytes(StandardCharsets.UTF_8))
  }

}
