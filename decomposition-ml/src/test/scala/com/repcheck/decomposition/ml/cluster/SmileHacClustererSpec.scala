package com.repcheck.decomposition.ml.cluster

import com.repcheck.decomposition.conformance.ConformanceContract

class SmileHacClustererSpec extends ConformanceContract {

  private val va                                          = Vector(1.0, 0.0)
  private val vb                                          = Vector(0.0, 1.0)
  private val vc                                          = Vector(-1.0, 0.0)
  private def noParents(n: Int): IndexedSeq[List[String]] = IndexedSeq.fill(n)(Nil)

  "structuralDistance" should "grade by shared-prefix depth when graded" in {
    SmileHacClusterer.structuralDistance(List("T1", "SubA"), List("T1", "SubA"), graded = true) shouldBe 0.0
    SmileHacClusterer.structuralDistance(List("T1", "SubA"), List("T1", "SubB"), graded = true) shouldBe 0.5 +- 1e-9
    SmileHacClusterer.structuralDistance(List("T1"), List("T2"), graded = true) shouldBe 1.0
  }

  it should "fall back to top-Title binary when not graded" in {
    SmileHacClusterer.structuralDistance(List("T1", "SubA"), List("T1", "SubB"), graded = false) shouldBe 0.0
    SmileHacClusterer.structuralDistance(List("T1"), List("T2"), graded = false) shouldBe 1.0
    SmileHacClusterer.structuralDistance(Nil, Nil, graded = false) shouldBe 1.0
  }

  "blendedProximity" should "mix base distance and structure by structureWeight" in {
    val cfg = ClusteringConfig(structureWeight = 0.9, gradedHierarchy = true)
    val d   = SmileHacClusterer.blendedProximity(IndexedSeq(va, vb), IndexedSeq(List("T1"), List("T2")), cfg)
    d(0)(1) shouldBe 1.0 +- 1e-9 // 0.1*cosineDist(1.0) + 0.9*structural(1.0)
    d(0)(0) shouldBe 0.0
  }

  it should "reject a parents/embeddings size mismatch" in {
    an[IllegalArgumentException] should be thrownBy
      SmileHacClusterer.blendedProximity(IndexedSeq(va, vb), IndexedSeq(Nil), ClusteringConfig())
  }

  "fitFromProximity" should "cluster from a precomputed distance matrix" in {
    val d = Array(
      Array(0.0, 0.1, 1.0, 1.0),
      Array(0.1, 0.0, 1.0, 1.0),
      Array(1.0, 1.0, 0.0, 0.1),
      Array(1.0, 1.0, 0.1, 0.0),
    )
    SmileHacClusterer.fitFromProximity(d, "average").cut(2) shouldBe Vector(0, 0, 1, 1)
  }

  "a fitted dendrogram" should "cut at k and report the cut height for k clusters" in {
    val f = SmileHacClusterer.fit(IndexedSeq(va, va, vb, vb), ClusteringConfig())
    f.cut(2) shouldBe Vector(0, 0, 1, 1)
    f.heightForK(1) should be > f.heightForK(2) // one cluster needs a higher cut than two
  }

  "guidedCut" should "pick the k that maximizes silhouette within the window" in {
    val f = SmileHacClusterer.fit(IndexedSeq(va, va, vb, vb, vc, vc), ClusteringConfig())
    SmileHacClusterer.guidedCut(f, guideK = 3, tolerance = 0.5, minK = 1).distinct.size shouldBe 3
  }

  it should "collapse to one group when the guide count is at or below minK" in {
    val f = SmileHacClusterer.fit(IndexedSeq(va, va, vb, vb), ClusteringConfig())
    SmileHacClusterer.guidedCut(f, guideK = 1, tolerance = 0.3, minK = 1) shouldBe Vector(0, 0, 0, 0)
  }

  "silhouetteCut" should "cut at the silhouette-optimal k (three clear groups → k=3)" in {
    val f = SmileHacClusterer.fit(IndexedSeq(va, va, vb, vb, vc, vc), ClusteringConfig())
    SmileHacClusterer.silhouetteCut(f) shouldBe Vector(0, 0, 1, 1, 2, 2)
  }

  it should "split the two distinct sections of an n=2 bill" in {
    val f = SmileHacClusterer.fit(IndexedSeq(va, vb), ClusteringConfig())
    SmileHacClusterer.silhouetteCut(f) shouldBe Vector(0, 1)
  }

  "cluster" should "separate concept groups via the omnibus silhouette-cut pipeline" in {
    val embs    = IndexedSeq(va, va, vb, vb)
    val parents = IndexedSeq(List("T1"), List("T1"), List("T2"), List("T2"))
    SmileHacClusterer.cluster(embs, parents, ClusteringConfig()) shouldBe Vector(0, 0, 1, 1)
  }

  it should "handle the degenerate n<2 cases" in {
    SmileHacClusterer.cluster(IndexedSeq.empty, IndexedSeq.empty, ClusteringConfig()) shouldBe Vector.empty[Int]
    SmileHacClusterer.cluster(IndexedSeq(va), noParents(1), ClusteringConfig()) shouldBe Vector(0)
  }

  "structuralCoverage" should "be the fraction of sections with a non-empty breadcrumb" in {
    SmileHacClusterer.structuralCoverage(noParents(4)) shouldBe 0.0
    SmileHacClusterer.structuralCoverage(IndexedSeq(List("T1"), List("T2"))) shouldBe 1.0
    SmileHacClusterer.structuralCoverage(IndexedSeq(Nil, List("T1"), Nil, List("T2"))) shouldBe 0.5
    SmileHacClusterer.structuralCoverage(IndexedSeq.empty) shouldBe 0.0
  }

  "effectiveStructureWeight" should "return the configured weight when adaptiveStructure is off" in {
    val off = ClusteringConfig(structureWeight = 0.9, adaptiveStructure = false)
    SmileHacClusterer.effectiveStructureWeight(noParents(3), off) shouldBe 0.9
  }

  it should "scale the weight by coverage when adaptiveStructure is on" in {
    val cfg = ClusteringConfig(structureWeight = 0.9, adaptiveStructure = true)
    SmileHacClusterer.effectiveStructureWeight(noParents(3), cfg) shouldBe 0.0 +- 1e-9 // flat → cosine
    SmileHacClusterer.effectiveStructureWeight(IndexedSeq(List("T1"), List("T2")), cfg) shouldBe 0.9 +- 1e-9
    SmileHacClusterer.effectiveStructureWeight(IndexedSeq(Nil, List("T1")), cfg) shouldBe 0.45 +- 1e-9 // half coverage
  }

  it should "make a flat bill's blended matrix the pure cosine distance when adaptive" in {
    val embs = IndexedSeq(va, Vector(1.0, 1.0)) // cosine = 1/√2 → cosine distance ≈ 0.29289
    SmileHacClusterer.blendedProximity(embs, noParents(2), ClusteringConfig(adaptiveStructure = true))(0)(1) shouldBe
      0.29289322 +- 1e-6
    // off: the constant structural term dominates the same flat pair
    SmileHacClusterer.blendedProximity(embs, noParents(2), ClusteringConfig(adaptiveStructure = false))(0)(1) shouldBe
      (0.1 * 0.29289322 + 0.9) +- 1e-6
  }

}
