package com.repcheck.decomposition.evaluation

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import scala.io.Source

import cats.effect.unsafe.implicits.global
import cats.syntax.all._

import com.repcheck.decomposition.conformance.{ConformanceContract, Corpus}
import com.repcheck.decomposition.evaluation.compare.GroupingComparison
import com.repcheck.decomposition.evaluation.metrics.ClusteringMetrics
import com.repcheck.decomposition.evaluation.wiring.EmbeddingHarness
import com.repcheck.decomposition.ml.cluster.{ClusteringConfig, SmileHacClusterer}
import com.repcheck.decomposition.ml.embed.{EmbeddingTransform, StandardizationStats}
import com.repcheck.decomposition.ml.metrics.EmbeddingMetrics
import com.repcheck.decomposition.text.{DefaultSectionParser, TextFormat}
import com.repcheck.utils.tags.E2ETest

/**
 * Validates the global standardization mean/std (computed over the full 425k-chunk `raw_bill_text.embedding` universe,
 * `evaluation/.../standardization/global-stats-v1.json`) against the DP-0 baseline, which standardized with stats
 * POOLED from the 25-bill gold's own SECTION embeddings. Production clusters one bill at a time, so it must carry a
 * fixed GLOBAL mean/std; the only difference from the validated transform is granularity (DB = 12k-char chunks, gold =
 * parser sections). If the DB-global stats reproduce the pooled-stats ARI on the same gold, granularity is immaterial
 * and the artifact is production-ready. Cuts at the reference concept count via the [[cutAt]] primitive (production
 * cuts at the silhouette-optimal k; pinning k here isolates the stats-artifact concern from the cut policy).
 *
 * E2ETest (live Ollama, regenerates the section embed-cache): sbt -Dupdate.gold=true "evaluation/testOnly
 * *StandardizationStatsValidationSpec -- -n com.repcheck.tags.E2ETest"
 */
class StandardizationStatsValidationSpec extends ConformanceContract {

  private val parser        = new DefaultSectionParser
  private def updateMode    = sys.props.get("update.gold").contains("true")
  private val EmbedChunk    = 64
  private val EmbedMaxChars = 4000
  private val TrivialMaxSec = 3
  private val CacheDir      = Paths.get("target", "embed-cache")
  private val cfg           = ClusteringConfig()
  private val Tolerance     = 0.02

  /** Cut the graded-hierarchy dendrogram at an explicit k (the eval primitive — production cuts at the silhouette). */
  private def cutAt(
    std: IndexedSeq[Vector[Double]],
    paths: IndexedSeq[List[String]],
    k: Int,
    c: ClusteringConfig,
  ): Vector[Int] =
    SmileHacClusterer.fitFromProximity(SmileHacClusterer.blendedProximity(std, paths, c), c.linkage).cut(k)

  private def embedAll(texts: List[String]): List[Array[Float]] =
    EmbeddingHarness
      .resource()
      .use(svc => texts.grouped(EmbedChunk).toList.traverse(chunk => svc.embedBatch(chunk.map(_.take(EmbedMaxChars)))))
      .map(_.flatten)
      .unsafeRunSync()

  /** Same on-disk cache + key as ComparisonReport, so a populated cache is reused across both specs. */
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

  final private case class B(
    vid: String,
    sec: Int,
    raw: IndexedSeq[Vector[Double]],
    ref: Vector[Int],
    paths: IndexedSeq[List[String]],
  )

  "DB-global standardization stats" should
    "reproduce the pooled-stats ARI on the 25-bill gold (granularity immaterial)" taggedAs E2ETest in {
      assume(updateMode, "run forked with -Dupdate.gold=true (live Ollama)")

      val bills = GoldSet.pilot.bills.map { gold =>
        val bill = Corpus.bills.find(_.versionId == gold.versionId).getOrElse(fail(s"${gold.versionId} not in corpus"))
        val parsed = parser.parse(bill.content, TextFormat.fromFormatType(bill.format))
        val n      = parsed.sections.size
        val raw    = embedCached(gold.versionId, parsed.sections.map(_.content))
        val ref    = GroupingComparison.labelsFromGroups(gold.groups.map(_.sectionIndices), n)
        val paths  = parsed.sections.map(_.parents).toIndexedSeq
        B(gold.versionId, n, raw, ref, paths)
      }

      val pooled            = bills.flatMap(_.raw)
      val gMeanP            = EmbeddingTransform.mean(pooled)
      val gStdP             = EmbeddingTransform.std(pooled, gMeanP)
      val dbStats           = StandardizationStats.bundled
      val (gMeanDb, gStdDb) = (dbStats.mean, dbStats.std)

      // Direct granularity diagnostic, independent of clustering: how close are the two transforms themselves?
      val meanCos   = EmbeddingMetrics.cosine(gMeanP, gMeanDb)
      val stdRatios = gStdP.zip(gStdDb).collect { case (p, d) if d != 0.0 => p / d }.sorted
      val medRatio  = stdRatios.lift(stdRatios.size / 2).getOrElse(1.0)
      info(
        f"transform proximity: mean-vector cosine(pooled,db)=$meanCos%.4f  median std ratio(pooled/db)=$medRatio%.3f"
      )

      // Pin adaptiveStructure=OFF here: this test isolates the STATS-ARTIFACT concern (db-global reproduces pooled).
      // The adaptive lever is cosine-dominated on flat bills and so stats-sensitive (covered by the adaptive
      // experiment, not this gate). Under the structure-dominated base config the two transforms are equivalent.
      val cfgGate = ClusteringConfig(adaptiveStructure = false)
      def labelsUnder(b: B, m: Vector[Double], sd: Vector[Double]): Vector[Int] =
        cutAt(b.raw.map(v => EmbeddingTransform.standardize(v, m, sd)), b.paths, b.ref.distinct.size, cfgGate)

      // FULL 25-bill breakdown: the GLOBAL-stats automated clustering vs the Claude (LLM) reference grouping.
      // llmConcepts = concepts Claude identified; produced = clusters the global method emitted; ARI/NMI/homog/compl =
      // agreement with the LLM reference. ariPooled is the DP-0 baseline (kept only for the no-regression gate).
      info("vid       sec  llmConcepts produced  ARI    NMI    homog  compl   ariPooled  class")
      val rows = bills.sortBy(_.sec).map { b =>
        val labDb    = labelsUnder(b, gMeanDb, gStdDb)
        val ari      = ClusteringMetrics.adjustedRandIndex(labDb, b.ref)
        val nmi      = ClusteringMetrics.normalizedMutualInformation(labDb, b.ref)
        val homog    = ClusteringMetrics.homogeneity(labDb, b.ref)
        val compl    = ClusteringMetrics.completeness(labDb, b.ref)
        val produced = labDb.distinct.size
        val ariPool  = ClusteringMetrics.adjustedRandIndex(labelsUnder(b, gMeanP, gStdP), b.ref)
        val cls      = if (b.sec > TrivialMaxSec) "nontrivial" else "trivial"
        info(
          f"${b.vid}%-8s ${b.sec}%-4d ${b.ref.distinct.size}%-11d $produced%-9d $ari%.3f  $nmi%.3f  $homog%.3f  $compl%.3f   $ariPool%.3f      $cls"
        )
        (b, ari, nmi, ariPool)
      }
      def mean(xs: List[Double]): Double = if (xs.isEmpty) 0.0 else xs.sum / xs.size
      val nt                             = rows.filter(_._1.sec > TrivialMaxSec)
      val meanDb                         = mean(nt.map(_._2))
      val meanP                          = mean(nt.map(_._4))
      info(
        f"MEAN vs LLM (non-trivial n=${nt.size}): ARI=$meanDb%.3f  NMI=${mean(nt.map(_._3))}%.3f   [pooled-baseline ARI=$meanP%.3f]"
      )
      info(f"MEAN vs LLM (all 25):                 ARI=${mean(rows.map(_._2))}%.3f  NMI=${mean(
          rows.map(_._3)
        )}%.3f   [pooled-baseline ARI=${mean(rows.map(_._4))}%.3f]")

      meanDb shouldBe meanP +- Tolerance
    }

  "global-vs-LLM section alignment" should
    "print per-section produced-cluster vs LLM-concept for the low-ARI bills" taggedAs E2ETest in {
      assume(updateMode, "run forked with -Dupdate.gold=true (live Ollama)")
      val targets = Set("8966", "189669", "415327")
      val dbStats = StandardizationStats.bundled
      GoldSet.pilot.bills.filter(g => targets.contains(g.versionId)).foreach { gold =>
        val bill = Corpus.bills.find(_.versionId == gold.versionId).getOrElse(fail(s"${gold.versionId} not in corpus"))
        val parsed = parser.parse(bill.content, TextFormat.fromFormatType(bill.format))
        val n      = parsed.sections.size
        val raw    = embedCached(gold.versionId, parsed.sections.map(_.content))
        val paths  = parsed.sections.map(_.parents).toIndexedSeq
        val k      = gold.groups.size
        val produced =
          cutAt(raw.map(v => EmbeddingTransform.standardize(v, dbStats.mean, dbStats.std)), paths, k, cfg)
        val concept = gold.groups.flatMap(g => g.sectionIndices.map(i => i -> g.conceptLabel)).toMap
        info(s"=== ${gold.versionId}: $n sections, LLM=$k concepts, produced=${produced.distinct.size} clusters ===")
        (0 until n).foreach { i =>
          val depth = paths.lift(i).map(_.size).getOrElse(0)
          val crumb = paths.lift(i).map(_.mkString(" > ")).filter(_.nonEmpty).getOrElse("(flat)")
          info(f"  s$i%-2d producedC=${produced(i)}%-2d depth=$depth  llm='${concept.getOrElse(i, "?")}%-30s' [$crumb]")
        }
      }
      succeed
    }

  "adaptive-structure blend" should
    "be measured against the fixed blend on the non-trivial gold (the flat-bill lever)" taggedAs E2ETest in {
      assume(updateMode, "run forked with -Dupdate.gold=true (live Ollama)")
      val dbStats     = StandardizationStats.bundled
      val cfgFixed    = ClusteringConfig(adaptiveStructure = false)
      val cfgAdaptive = ClusteringConfig(adaptiveStructure = true) // isolate the BLEND lever
      val flat        = Set("8966", "189669", "415327", "323852")  // all 4 fully-flat non-trivial bills

      val rows = GoldSet.pilot.bills.map { gold =>
        val bill = Corpus.bills.find(_.versionId == gold.versionId).getOrElse(fail(s"${gold.versionId} not in corpus"))
        val parsed = parser.parse(bill.content, TextFormat.fromFormatType(bill.format))
        val n      = parsed.sections.size
        val raw    = embedCached(gold.versionId, parsed.sections.map(_.content))
        val paths  = parsed.sections.map(_.parents).toIndexedSeq
        val ref    = GroupingComparison.labelsFromGroups(gold.groups.map(_.sectionIndices), n)
        val std    = raw.map(v => EmbeddingTransform.standardize(v, dbStats.mean, dbStats.std))
        val k      = ref.distinct.size
        val cov    = SmileHacClusterer.structuralCoverage(paths)
        val aFix   = ClusteringMetrics.adjustedRandIndex(cutAt(std, paths, k, cfgFixed), ref)
        val aAdapt = ClusteringMetrics.adjustedRandIndex(cutAt(std, paths, k, cfgAdaptive), ref)
        (gold.versionId, n, cov, aFix, aAdapt)
      }

      info("vid       sec  coverage  ARI_fixed  ARI_adaptive  delta   flat?")
      rows.filter(_._2 > TrivialMaxSec).sortBy(_._2).foreach {
        case (vid, n, cov, aFix, aAdapt) =>
          val tag = if (flat.contains(vid)) "FLAT" else ""
          info(f"$vid%-8s $n%-4d $cov%.2f      $aFix%.3f      $aAdapt%.3f       ${aAdapt - aFix}%+.3f  $tag")
      }
      def mean(xs: List[Double]): Double = if (xs.isEmpty) 0.0 else xs.sum / xs.size
      val nt                             = rows.filter(_._2 > TrivialMaxSec)
      val fRow                           = nt.filter(r => flat.contains(r._1))
      info(f"MEAN non-trivial (n=${nt.size}): fixed=${mean(nt.map(_._4))}%.3f  adaptive=${mean(
          nt.map(_._5)
        )}%.3f  delta=${mean(nt.map(_._5)) - mean(nt.map(_._4))}%+.4f")
      info(f"MEAN flat-3:               fixed=${mean(fRow.map(_._4))}%.3f  adaptive=${mean(
          fRow.map(_._5)
        )}%.3f  delta=${mean(fRow.map(_._5)) - mean(fRow.map(_._4))}%+.4f")
      succeed // experiment: report only; production default stays adaptiveStructure=false until this proves out
    }

}
