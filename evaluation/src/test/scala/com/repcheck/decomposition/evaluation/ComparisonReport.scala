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
  private val DMaxGrid      = (1 to 30).map(_ * 0.1).toList  // 0.1 .. 3.0 (omnibus bills want a high cut)
  private val MaxKGrid      = List(10, 20, 30, 40, 60, 80, 120)
  private val ProfileMaxK   = MaxKGrid.foldLeft(0)((a, b) => math.max(a, b))
  private val Transforms    = List("none", "center", "standardize")
  private val CacheDir      = Paths.get("target", "embed-cache")
  private val AnisoSample   = 250
  private val AlphaGrid     = (0 to 10).map(_ * 0.1).toList  // structure blend: 0.0 (pure Title) .. 1.0 (pure cosine)
  private val GammaGrid     = List(0.0, 0.3, 0.6)            // lexical (citation/Act) discount weight
  private val AlphaProbe    = List(0.05, 0.1, 0.2, 0.3, 0.5) // alpha re-sweep for the winning lever variant

  // High-precision legislative cross-references — the "same statute" signal embeddings miss (lever 2).
  private val UscRe   = """(\d{1,2})\s+U\.?\s?S\.?\s?C\.?\s?(?:§+\s?)?(\d+[A-Za-z0-9]*)""".r
  private val PubLRe  = """[Pp]ub(?:lic)?\.?\s?L(?:aw)?\.?\s?(\d{1,3}[-–]\d{1,4})""".r
  private val ActYrRe = """([A-Z][A-Za-z']+(?:\s+[A-Z][A-Za-z']+){0,5})\s+Act\s+of\s+(\d{4})""".r

  final private case class BillData(
    versionId: String,
    sections: Int,
    raw: IndexedSeq[Vector[Double]],
    ref: Vector[Int],
    paths: IndexedSeq[List[String]], // FULL parser breadcrumb per section, outermost-first (lever 1)
    refs: IndexedSeq[Set[String]],   // cross-reference tokens per section (lever 2)
  ) {
    def isOmnibus: Boolean      = sections > OmnibusMinSec
    def isTunableTight: Boolean = !isOmnibus && sections > TrivialMaxSec
    def isNonTrivial: Boolean   = isOmnibus || isTunableTight
  }

  /**
   * Lever 1 — graded hierarchy distance. The blend used only `parents.head` (top Title) binary; this uses the FULL
   * breadcrumb and grades by shared-prefix depth, so two sections in the same Title but different Subtitles repel each
   * other (the resolution omnibus bills need). `graded=false` reproduces the old top-Title binary behaviour exactly.
   */
  private def structuralDistance(a: List[String], b: List[String], graded: Boolean): Double =
    if (graded) {
      val shared = a.zip(b).takeWhile { case (x, y) => x == y }.size
      val depth  = math.max(a.size, b.size)
      if (depth == 0) 1.0 else 1.0 - shared.toDouble / depth
    } else
      (a.headOption, b.headOption) match {
        case (Some(x), Some(y)) if x == y => 0.0
        case _                            => 1.0
      }

  /** Lever 2 — extract high-precision cross-references (U.S.C. cites, public laws, named Acts) as a token set. */
  private def extractRefs(content: String): Set[String] = {
    val usc  = UscRe.findAllMatchIn(content).map(m => s"usc:${m.group(1)}:${m.group(2)}").toSet
    val publ = PubLRe.findAllMatchIn(content).map(m => s"pl:${m.group(1).replace('–', '-')}").toSet
    val acts = ActYrRe.findAllMatchIn(content).map(m => s"act:${m.group(1).toLowerCase}:${m.group(2)}").toSet
    usc ++ publ ++ acts
  }

  /**
   * Jaccard overlap of two reference sets; 0 (neutral) when either side has no references — absence is not evidence.
   */
  private def lexicalSim(a: Set[String], b: Set[String]): Double =
    if (a.isEmpty || b.isEmpty) 0.0
    else {
      val union = a.union(b).size.toDouble
      if (union == 0.0) 0.0 else a.intersect(b).size.toDouble / union
    }

  /**
   * Production blend: `d = (alpha*cosine + (1-alpha)*structural) * (1 - gamma*lexicalSim)`. The lexical term is a
   * multiplicative DISCOUNT — shared cross-references only ever pull sections closer, never push them apart, so
   * citation-free pairs are unaffected.
   */
  private def blendMatrix(
    b: BillData,
    vecs: IndexedSeq[Vector[Double]],
    alpha: Double,
    graded: Boolean,
    gamma: Double,
  ): Array[Array[Double]] =
    Array.tabulate(vecs.size, vecs.size)((i, j) =>
      if (i == j) 0.0
      else {
        val cos  = 1.0 - EmbeddingMetrics.cosine(vecs(i), vecs(j))
        val str  = structuralDistance(b.paths(i), b.paths(j), graded)
        val base = alpha * cos + (1.0 - alpha) * str
        base * (1.0 - gamma * lexicalSim(b.refs(i), b.refs(j)))
      }
    )

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
      val paths  = parsed.sections.map(_.parents).toIndexedSeq
      val refs   = parsed.sections.map(s => extractRefs(s.content)).toIndexedSeq
      info(s"${gold.versionId}: $n sections")
      BillData(gold.versionId, n, raw, ref, paths, refs)
    }
    val pooled = bills.flatMap(_.raw)

    val analyses   = Transforms.map(t => analyze(t, bills, pooled))
    val structure  = structureSweep(bills, pooled)
    val robustness = robustnessSweep(bills, pooled)
    val cFits      = cosineFits(bills, pooled)
    val levers     = leverSweep(bills, pooled)
    val kLabel = f"blend graded=${levers.best.graded}, gamma=${levers.best.gamma}%.1f, alpha=${levers.best.alpha}%.2f"
    val formulas = List(
      dMaxFormula("standardize + cosine", cFits, 0.0),
      dMaxFormula("standardize + cosine, small-n floor", cFits, 0.8),
      kFormula(kLabel, levers.bestFits),
    )
    writeReport(analyses, structure, robustness, formulas, levers)
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
        if (i == j) 0.0 else structuralDistance(b.paths(i), b.paths(j), graded = false)
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

  final private case class LeverVariant(graded: Boolean, gamma: Double, alpha: Double, ari: Double, nmi: Double)

  final private case class LeverBill(
    vid: String,
    sec: Int,
    k: Int,
    ariBaseline: Double,
    ariLevers: Double,
    nmiLevers: Double,
  )

  final private case class LeverResult(
    variants: List[LeverVariant],
    best: LeverVariant,
    perBill: List[LeverBill],
    bestFits: List[(BillData, SmileHacClusterer.Fitted)],
  )

  /**
   * Levers 1 & 2: at the production cut (k = subjects), compare the binary top-Title blend (baseline) against the
   * graded hierarchy and the lexical discount, across graded in {false, true} x gamma in GammaGrid (fixed alpha=0.1).
   * Re-sweep alpha for the winning variant, then return per-bill baseline-vs-levers and the fitted dendrograms for the
   * winner so the k-formula is built on the improved distance.
   */
  private def leverSweep(bills: List[BillData], pooled: Seq[Vector[Double]]): LeverResult = {
    val gMean      = EmbeddingTransform.mean(pooled)
    val gStd       = EmbeddingTransform.std(pooled, gMean)
    val FixedAlpha = 0.1
    val prepared =
      bills.filter(_.isNonTrivial).map(b => (b, b.raw.map(v => EmbeddingTransform.standardize(v, gMean, gStd))))
    def meanAt(graded: Boolean, gamma: Double, alpha: Double): (Double, Double) = {
      val pairs = prepared.map {
        case (b, vecs) =>
          val labels = SmileHacClusterer
            .fitFromProximity(blendMatrix(b, vecs, alpha, graded, gamma), "average")
            .cut(b.ref.distinct.size)
          (
            ClusteringMetrics.adjustedRandIndex(labels, b.ref),
            ClusteringMetrics.normalizedMutualInformation(labels, b.ref),
          )
      }
      (pairs.map(_._1).sum / pairs.size, pairs.map(_._2).sum / pairs.size)
    }
    val variants = for { graded <- List(false, true); gamma <- GammaGrid } yield {
      val (ari, nmi) = meanAt(graded, gamma, FixedAlpha)
      LeverVariant(graded, gamma, FixedAlpha, ari, nmi)
    }
    val bestFixed = variants.foldLeft(LeverVariant(false, 0.0, FixedAlpha, Double.NegativeInfinity, 0.0)) { (acc, v) =>
      if (v.ari > acc.ari) v else acc
    }
    val best = AlphaProbe.foldLeft(bestFixed) { (acc, a) =>
      val (ari, nmi) = meanAt(bestFixed.graded, bestFixed.gamma, a)
      if (ari > acc.ari) LeverVariant(bestFixed.graded, bestFixed.gamma, a, ari, nmi) else acc
    }
    val bestFits = prepared.map {
      case (b, vecs) =>
        (b, SmileHacClusterer.fitFromProximity(blendMatrix(b, vecs, best.alpha, best.graded, best.gamma), "average"))
    }
    val perBill = prepared.lazyZip(bestFits).map {
      case ((b, vecs), (_, f)) =>
        val k = b.ref.distinct.size
        val baseline = SmileHacClusterer
          .fitFromProximity(blendMatrix(b, vecs, FixedAlpha, graded = false, gamma = 0.0), "average")
          .cut(k)
        val levers = f.cut(k)
        LeverBill(
          b.versionId,
          b.sections,
          k,
          ClusteringMetrics.adjustedRandIndex(baseline, b.ref),
          ClusteringMetrics.adjustedRandIndex(levers, b.ref),
          ClusteringMetrics.normalizedMutualInformation(levers, b.ref),
        )
    }
    LeverResult(variants, best, perBill, bestFits)
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

  private def linreg(xs: List[Double], ys: List[Double]): (Double, Double, Double) = {
    val n   = xs.size.toDouble
    val mx  = xs.sum / n
    val my  = ys.sum / n
    val sxx = xs.map(x => (x - mx) * (x - mx)).sum
    val sxy = xs.lazyZip(ys).map((x, y) => (x - mx) * (y - my)).sum
    val syy = ys.map(y => (y - my) * (y - my)).sum
    val b   = if (sxx == 0.0) 0.0 else sxy / sxx
    val a   = my - b * mx
    val r2  = if (sxx == 0.0 || syy == 0.0) 0.0 else (sxy * sxy) / (sxx * syy)
    (a, b, r2)
  }

  /** Least-squares slope with the intercept forced to 0 (model y = slope*x). Returns (slope, uncentered R^2). */
  private def linregThroughOrigin(xs: List[Double], ys: List[Double]): (Double, Double) = {
    val sxx   = xs.map(x => x * x).sum
    val sxy   = xs.lazyZip(ys).map((x, y) => x * y).sum
    val slope = if (sxx == 0.0) 0.0 else sxy / sxx
    val ssTot = ys.map(y => y * y).sum
    val ssRes = xs.lazyZip(ys).map((x, y) => (y - slope * x) * (y - slope * x)).sum
    val r2    = if (ssTot == 0.0) 0.0 else 1.0 - ssRes / ssTot
    (slope, r2)
  }

  final private case class FormulaBill(
    vid: String,
    n: Int,
    s: Int,
    bestCut: Double,
    predCut: Double,
    ariBest: Double,
    ariPred: Double,
  )

  final private case class FormulaResult(
    label: String,
    cutLabel: String,
    formula: String,
    r2: Double,
    perBill: List[FormulaBill],
  ) {
    def meanBest: Double = perBill.map(_.ariBest).sum / perBill.size
    def meanPred: Double = perBill.map(_.ariPred).sum / perBill.size
  }

  /** Standardized embeddings, cosine distance — the clean dMax space. */
  private def cosineFits(
    bills: List[BillData],
    pooled: Seq[Vector[Double]],
  ): List[(BillData, SmileHacClusterer.Fitted)] = {
    val gMean = EmbeddingTransform.mean(pooled)
    val gStd  = EmbeddingTransform.std(pooled, gMean)
    bills.filter(_.isNonTrivial).map { b =>
      (b, SmileHacClusterer.fit(b.raw.map(v => EmbeddingTransform.standardize(v, gMean, gStd)), ClusteringConfig()))
    }
  }

  /**
   * Formulaic cutoff on the cut HEIGHT: per bill find the dMax that best matches the reference, regress dMax = a +
   * b*ln(n - S) on (section count n, subject count S), then apply it (floored at `floor` for the small-n regime where a
   * tiny height error swings ARI). The subject count drives an adaptive, smooth cutoff — no fixed split.
   */
  private def dMaxFormula(
    label: String,
    fits: List[(BillData, SmileHacClusterer.Fitted)],
    floor: Double,
  ): FormulaResult = {
    val data = fits.map {
      case (b, f) =>
        val best = DMaxGrid.foldLeft((Double.NaN, Double.NegativeInfinity)) {
          case ((bd, bs), d) =>
            val ari = ClusteringMetrics.adjustedRandIndex(f.cut(f.kThreshold(d)), b.ref)
            if (ari > bs) (d, ari) else (bd, bs)
        }
        (b, f, b.ref.distinct.size, best._1, best._2)
    }
    val xs         = data.map { case (b, _, s, _, _) => math.log(math.max(1, b.sections - s).toDouble) }
    val ys         = data.map(_._4)
    val (a, b, r2) = linreg(xs, ys)
    val perBill = data.map {
      case (bill, f, s, bestDMax, ariBest) =>
        val pred    = math.max(floor, a + b * math.log(math.max(1, bill.sections - s).toDouble))
        val ariPred = ClusteringMetrics.adjustedRandIndex(f.cut(f.kThreshold(pred)), bill.ref)
        FormulaBill(bill.versionId, bill.sections, s, bestDMax, pred, ariBest, ariPred)
    }
    val flo = if (floor > 0.0) f", floor $floor%.2f" else ""
    FormulaResult(label, "dMax", f"dMax = $a%.3f + $b%.3f * ln(n - S)$flo (R^2 = $r2%.3f)", r2, perBill)
  }

  /**
   * Formulaic cutoff on the cut COUNT: on the production blend, dMax sits in a wide Title "gap" and is a degenerate
   * selector, so k is the real lever. Per bill find the k that best matches the reference, regress k = slope*S THROUGH
   * THE ORIGIN (no intercept — an intercept made a single-subject bill predict ~3 groups instead of 1), then apply it
   * with the production guard S <= 1 => k = 1 (a single-concept bill must not be decomposed). The endpoint subject
   * count directly drives how many groups we cut, anchored so S=1 maps to one group.
   */
  private def kFormula(label: String, fits: List[(BillData, SmileHacClusterer.Fitted)]): FormulaResult = {
    val data = fits.map {
      case (b, f) =>
        val upper = math.min(b.sections - 1, 80)
        val best = (2 to math.max(2, upper)).foldLeft((Double.NaN, Double.NegativeInfinity)) {
          case ((bk, bs), k) =>
            val ari = ClusteringMetrics.adjustedRandIndex(f.cut(k), b.ref)
            if (ari > bs) (k.toDouble, ari) else (bk, bs)
        }
        (b, f, b.ref.distinct.size, best._1, best._2)
    }
    val xs                   = data.map(_._3.toDouble)
    val ys                   = data.map(_._4)
    val (slope, r2)          = linregThroughOrigin(xs, ys)
    def predict(s: Int): Int = if (s <= 1) 1 else math.max(1, math.round(slope * s).toInt)
    val perBill = data.map {
      case (bill, f, s, bestK, ariBest) =>
        val predK   = predict(s)
        val ariPred = ClusteringMetrics.adjustedRandIndex(f.cut(predK), bill.ref)
        FormulaBill(bill.versionId, bill.sections, s, bestK, predK.toDouble, ariBest, ariPred)
    }
    FormulaResult(label, "k", f"k = $slope%.3f * S (through origin; S<=1 => k=1) (R^2 = $r2%.3f)", r2, perBill)
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
      (b, SmileHacClusterer.fitFromProximity(blendMatrix(b, vecs, Alpha, graded = false, gamma = 0.0), "average"))
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
    formulas: List[FormulaResult],
    levers: LeverResult,
  ): Unit = {
    val leverVariantRows = levers.variants.map(v =>
      f"| ${if (v.graded) "graded" else "binary top-Title"} | ${v.gamma}%.1f | ${v.ari}%.3f | ${v.nmi}%.3f |"
    )
    val leverBillRows = levers.perBill.map(b =>
      f"| ${b.vid} | ${b.sec} | ${b.k} | ${b.ariBaseline}%.3f | ${b.ariLevers}%.3f | ${b.nmiLevers}%.3f |"
    )
    val leverBaselineMean = levers.perBill.map(_.ariBaseline).sum / levers.perBill.size
    val leverBestMean     = levers.perBill.map(_.ariLevers).sum / levers.perBill.size
    val formulaLines = formulas.flatMap { fr =>
      val rows = fr.perBill.map(fb =>
        f"| ${fb.vid} | ${fb.n} | ${fb.s} | ${fb.bestCut}%.2f | ${fb.predCut}%.2f | ${fb.ariBest}%.3f | ${fb.ariPred}%.3f |"
      )
      List(
        "",
        s"### ${fr.label} — cut by ${fr.cutLabel}",
        "",
        s"Fit: **${fr.formula}**.",
        "",
        s"| bill | n | S | best ${fr.cutLabel} | formula ${fr.cutLabel} | ARI best | ARI formula |",
        "|---|---|---|---|---|---|---|",
      ) ++ rows ++ List(
        "",
        f"Mean ARI: oracle (per-bill best ${fr.cutLabel}) ${fr.meanBest}%.3f vs formula ${fr.meanPred}%.3f.",
      )
    }
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
        ) ++ robustRows ++
        List(
          "",
          "## Levers 1 & 2: graded hierarchy + lexical cross-reference (no LLM)",
          "",
          "Baseline = binary top-Title blend (alpha=0.1, cut@k=subjects). **Lever 1 (graded)** uses the FULL parser",
          "breadcrumb graded by shared-prefix depth instead of only `parents.head`. **Lever 2 (gamma)** multiplies the",
          "distance by `(1 - gamma*lexicalSim)` so shared U.S.C./public-law/Act citations pull sections together.",
          "",
          "| structure | gamma (lexical) | mean ARI | mean NMI |",
          "|---|---|---|---|",
        ) ++ leverVariantRows ++
        List(
          "",
          f"**Winner: ${
              if (levers.best.graded) "graded" else "binary"
            } hierarchy, gamma=${levers.best.gamma}%.1f, alpha=${levers.best.alpha}%.2f** — mean ARI ${levers.best.ari}%.3f, NMI ${levers.best.nmi}%.3f.",
          "",
          "### Per-bill: baseline (binary, gamma=0) vs levers (winner)",
          "",
          "| bill | sections | subjects (k) | ARI baseline | ARI levers | NMI levers |",
          "|---|---|---|---|---|---|",
        ) ++ leverBillRows ++
        List(
          "",
          f"Mean ARI: baseline $leverBaselineMean%.3f vs levers $leverBestMean%.3f.",
          "",
          "## Formulaic cutoff: compute the cut from (section count n, subject count S)",
          "",
          "The cutoff is calculated per bill from n and S — not a fixed split. On cosine the lever is the cut height dMax;",
          "on the production blend dMax is a degenerate Title-gap selector, so the lever is the cut count k = f(S).",
          "The k-formula below is fit on the LEVER-WINNING blend.",
        ) ++ formulaLines
    val _ = Files.write(Paths.get("COMPARISON_REPORT.md"), lines.mkString("\n").getBytes(StandardCharsets.UTF_8))
  }

}
