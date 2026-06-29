package com.repcheck.decomposition.pipeline.persistence

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all._

import doobie._
import doobie.implicits._

import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import repcheck.shared.models.llm.{Effect, Impact, Scope}

import com.repcheck.decomposition.pipeline.config.DatabaseConfig
import com.repcheck.decomposition.pipeline.persistence.DecompositionMeta._
import com.repcheck.utils.tags.E2ETest

/**
 * Round-trips a full decomposition through a REAL Postgres (pgvector) — proving the enum `Meta`, the pgvector codec,
 * the reuse-check, and the single-transaction `persistDecomposition` against the live schema. Tagged `E2ETest`
 * (excluded from plain `sbt test`); runs in the DB-backed coverage CI step + locally against `pgvector/pgvector:pg16`
 * on :5432. Self-creates its schema so it depends only on a reachable Postgres, and cancels cleanly when none is
 * present.
 */
class DoobieDecompositionPersisterSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private val url  = sys.env.getOrElse("DECOMP_JDBC_URL", "jdbc:postgresql://localhost:5432/repcheck")
  private val user = sys.env.getOrElse("DECOMP_JDBC_USER", "repcheck")
  private val pass = sys.env.getOrElse("DECOMP_JDBC_PASSWORD", "repcheck")

  private val xa: Transactor[IO] =
    Transactor.fromDriverManager[IO]("org.postgresql.Driver", url, user, pass, None)

  private val dbReachable: Boolean =
    sql"SELECT 1".query[Int].unique.transact(xa).attempt.unsafeRunSync().isRight

  private val emb: Array[Float] = Array.tabulate(1024)(i => (i % 7).toFloat)

  override def beforeAll(): Unit =
    if (dbReachable) { val _ = (dropDdl *> createDdl).transact(xa).unsafeRunSync() }

  override def afterAll(): Unit =
    if (dbReachable) { val _ = dropDdl.transact(xa).unsafeRunSync() }

  private def onDb(body: => Any): Any =
    if (dbReachable) body else cancel(s"no Postgres at $url — start pgvector/pgvector:pg16 on :5432")

  "DoobieDecompositionPersister" should "round-trip a full decomposition through real Postgres" taggedAs E2ETest in onDb {
    val persister = new DoobieDecompositionPersister[IO](xa)
    val versionId = 1001L
    val billId    = 1L

    val program = for {
      before <- persister.existsForVersion(versionId)
      runId <- persister.startRun(
        DecompositionRunRow("orch-1", "qwen3-0.6b", "routing-2", "sum-1", Some(99L))
      )
      secIds <- persister.persistSections(
        versionId,
        billId,
        List(
          SectionRow(0, 0, Some("SEC. 1"), Some("Short Title"), "the short title", None),
          SectionRow(1, 0, None, None, "the operative provision", Some(emb)),
        ),
      )
      _ <- persister.persistDecomposition(
        versionId,
        billId,
        runId,
        List(
          ConceptGroupRow(
            label = "Healthcare access",
            conceptSummary = "Expands coverage and restricts an eligibility carve-out.",
            embedding = Some(emb),
            memberSectionIds = secIds,
            topics = List(
              TopicRow(
                "expanding coverage",
                "healthcare access",
                Effect.Expands,
                "patients",
                Impact.Positive,
                Scope.Major,
                Some(emb),
              ),
              TopicRow(
                "restricting an eligibility carve-out",
                "healthcare access",
                Effect.Restricts,
                "insurers",
                Impact.Negative,
                Scope.Minor,
                None,
              ),
            ),
          )
        ),
      )
      after  <- persister.existsForVersion(versionId)
      loaded <- persister.loadSectionsWithEmbeddings(versionId)
      _      <- persister.completeRun(runId, "completed")
      stance <- sql"SELECT effect, impact, scope FROM bill_concept_topics ORDER BY id"
        .query[(Effect, Impact, Scope)]
        .to[List]
        .transact(xa)
      junctionCount <- sql"SELECT count(*) FROM bill_concept_group_sections".query[Long].unique.transact(xa)
    } yield (before, after, secIds, loaded, stance, junctionCount)

    val (before, after, secIds, loaded, stance, junctionCount) = program.unsafeRunSync()

    val _ = before shouldBe false     // reuse-check: nothing yet
    val _ = after shouldBe true       // reuse-check: groups now exist
    val _ = secIds should have size 2
    val _ = junctionCount shouldBe 2L // both member sections linked
    val _ = stance shouldBe List( // enum Meta round-trip through TEXT columns
      (Effect.Expands, Impact.Positive, Scope.Major),
      (Effect.Restricts, Impact.Negative, Scope.Minor),
    )
    // pgvector codec round-trip: section 1 carries the 1024-d embedding, section 0 is NULL
    loaded.find(_.sectionIndex == 1).flatMap(_.embedding).map(_.toList) shouldBe Some(emb.toList)
  }

  it should "build a usable HikariCP transactor from config" taggedAs E2ETest in onDb {
    val cfg = DatabaseConfig("localhost", 5432, "repcheck", "repcheck", "repcheck")
    val one = AlloyDbTransactor.make[IO](cfg).use(tx => sql"SELECT 1".query[Int].unique.transact(tx)).unsafeRunSync()
    one shouldBe 1
  }

  // ---- self-created decomposition schema (the spec depends only on a reachable Postgres) ----

  // Each statement runs on its own — Postgres PreparedStatement executes a single statement.
  private val createDdl: ConnectionIO[Unit] =
    List(
      sql"CREATE EXTENSION IF NOT EXISTS vector",
      sql"""CREATE TABLE bill_decomposition_runs (
              id BIGSERIAL PRIMARY KEY,
              orchestrator_version TEXT NOT NULL, embedder_version TEXT NOT NULL,
              clusterer_version TEXT NOT NULL, prompt_version TEXT NOT NULL,
              status TEXT NOT NULL CHECK (status IN ('running','completed','completed_with_errors','failed')),
              started_at TIMESTAMPTZ NOT NULL DEFAULT now(), completed_at TIMESTAMPTZ, workflow_run_id BIGINT)""",
      sql"""CREATE TABLE bill_text_sections (
              id BIGSERIAL PRIMARY KEY, version_id BIGINT NOT NULL, bill_id BIGINT NOT NULL,
              section_index INT NOT NULL, sub_index INT NOT NULL DEFAULT 0,
              section_identifier TEXT, heading TEXT, content TEXT NOT NULL, embedding vector(1024),
              created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
              UNIQUE (version_id, section_index, sub_index))""",
      sql"""CREATE TABLE bill_concept_groups (
              id BIGSERIAL PRIMARY KEY, version_id BIGINT NOT NULL, bill_id BIGINT NOT NULL,
              label TEXT NOT NULL, concept_summary TEXT NOT NULL, embedding vector(1024), taxonomy_version INT,
              created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
              run_id BIGINT REFERENCES bill_decomposition_runs(id))""",
      sql"""CREATE TABLE bill_concept_topics (
              id BIGSERIAL PRIMARY KEY,
              concept_group_id BIGINT NOT NULL REFERENCES bill_concept_groups(id) ON DELETE CASCADE,
              phrase TEXT NOT NULL, topic TEXT NOT NULL,
              effect TEXT NOT NULL CHECK (effect IN ('EXPANDS','RESTRICTS','CREATES','ELIMINATES','MODIFIES','REPORTS')),
              entity TEXT NOT NULL,
              impact TEXT NOT NULL CHECK (impact IN ('POSITIVE','NEGATIVE','MIXED','NEUTRAL')),
              scope TEXT NOT NULL CHECK (scope IN ('MAJOR','MODERATE','MINOR')),
              topic_embedding vector(1024), created_at TIMESTAMPTZ NOT NULL DEFAULT now())""",
      sql"""CREATE TABLE bill_concept_group_sections (
              concept_group_id BIGINT NOT NULL REFERENCES bill_concept_groups(id) ON DELETE CASCADE,
              section_id BIGINT NOT NULL REFERENCES bill_text_sections(id) ON DELETE CASCADE,
              PRIMARY KEY (concept_group_id, section_id))""",
    ).traverse_(_.update.run)

  private val dropDdl: ConnectionIO[Unit] =
    List(
      "bill_concept_group_sections",
      "bill_concept_topics",
      "bill_concept_groups",
      "bill_text_sections",
      "bill_decomposition_runs",
    ).traverse_(t => (fr"DROP TABLE IF EXISTS" ++ Fragment.const(t) ++ fr"CASCADE").update.run)

}
