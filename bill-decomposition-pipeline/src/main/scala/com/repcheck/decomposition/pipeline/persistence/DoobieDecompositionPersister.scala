package com.repcheck.decomposition.pipeline.persistence

import cats.effect.MonadCancelThrow
import cats.syntax.all._

import doobie._
import doobie.implicits._

import com.repcheck.decomposition.pipeline.persistence.DecompositionMeta._
import com.repcheck.utils.doobie.VectorCodec.{floatArrayGet, floatArrayPut}

/**
 * Doobie [[DecompositionPersister]] over AlloyDB / Cloud SQL Postgres. Each method composes a `ConnectionIO` and
 * transacts once; [[persistDecomposition]] composes the full multi-table write into a SINGLE transaction so a bill's
 * concept groups + topics + junctions are all-or-nothing. Embeddings bind via the repcheck-utils vector Get/Put plus an
 * explicit `::vector` cast; effect/impact/scope bind via [[DecompositionMeta]] (TEXT columns). Column lists are
 * explicit and in DO order (no `SELECT *`).
 */
final class DoobieDecompositionPersister[F[_]: MonadCancelThrow](xa: Transactor[F]) extends DecompositionPersister[F] {

  override def existsForVersion(versionId: Long): F[Boolean] =
    sql"""SELECT EXISTS (
            SELECT 1 FROM ${Fragment.const(Tables.BillConceptGroups)}
            WHERE version_id = $versionId
          )""".query[Boolean].unique.transact(xa)

  override def startRun(run: DecompositionRunRow): F[Long] =
    sql"""INSERT INTO ${Fragment.const(Tables.BillDecompositionRuns)}
            (orchestrator_version, embedder_version, clusterer_version, prompt_version,
             status, workflow_run_id)
          VALUES (${run.orchestratorVersion}, ${run.embedderVersion},
                  ${run.clustererVersion}, ${run.promptVersion}, 'running', ${run.workflowRunId})""".update
      .withUniqueGeneratedKeys[Long]("id")
      .transact(xa)

  override def completeRun(runId: Long, status: String): F[Unit] =
    sql"""UPDATE ${Fragment.const(Tables.BillDecompositionRuns)}
          SET status = $status, completed_at = now()
          WHERE id = $runId""".update.run.transact(xa).void

  override def persistSections(versionId: Long, billId: Long, sections: List[SectionRow]): F[List[Long]] =
    sections.traverse(insertSection(versionId, billId, _)).transact(xa)

  override def loadSectionsWithEmbeddings(versionId: Long): F[List[PersistedSection]] =
    sql"""SELECT id, section_index, sub_index, content, embedding
          FROM ${Fragment.const(Tables.BillTextSections)}
          WHERE version_id = $versionId
          ORDER BY section_index, sub_index""".query[PersistedSection].to[List].transact(xa)

  override def persistDecomposition(
    versionId: Long,
    billId: Long,
    runId: Long,
    groups: List[ConceptGroupRow],
  ): F[Unit] =
    groups.traverse_(persistGroup(versionId, billId, runId, _)).transact(xa)

  // ---- ConnectionIO building blocks (composed inside one transaction by persistDecomposition) ----

  private def insertSection(versionId: Long, billId: Long, s: SectionRow): ConnectionIO[Long] =
    sql"""INSERT INTO ${Fragment.const(Tables.BillTextSections)}
            (version_id, bill_id, section_index, sub_index, section_identifier, heading, content, embedding)
          VALUES ($versionId, $billId, ${s.sectionIndex}, ${s.subIndex}, ${s.sectionIdentifier}, ${s.heading},
                  ${s.content}, ${s.embedding}::vector)
          ON CONFLICT (version_id, section_index, sub_index)
            DO UPDATE SET content = EXCLUDED.content, embedding = EXCLUDED.embedding, updated_at = now()""".update
      .withUniqueGeneratedKeys[Long]("id")

  private def persistGroup(
    versionId: Long,
    billId: Long,
    runId: Long,
    g: ConceptGroupRow,
  ): ConnectionIO[Unit] =
    for {
      groupId <- insertGroup(versionId, billId, runId, g)
      _       <- g.topics.traverse_(insertTopic(groupId, _))
      _       <- g.memberSectionIds.traverse_(insertGroupSection(groupId, _))
    } yield ()

  private def insertGroup(
    versionId: Long,
    billId: Long,
    runId: Long,
    g: ConceptGroupRow,
  ): ConnectionIO[Long] =
    sql"""INSERT INTO ${Fragment.const(Tables.BillConceptGroups)}
            (version_id, bill_id, label, concept_summary, embedding, run_id)
          VALUES ($versionId, $billId, ${g.label}, ${g.conceptSummary}, ${g.embedding}::vector,
                  $runId)""".update.withUniqueGeneratedKeys[Long]("id")

  private def insertTopic(groupId: Long, t: TopicRow): ConnectionIO[Int] =
    sql"""INSERT INTO ${Fragment.const(Tables.BillConceptTopics)}
            (concept_group_id, phrase, topic, effect, entity, impact, scope, topic_embedding)
          VALUES ($groupId, ${t.phrase}, ${t.topic}, ${t.effect}, ${t.entity}, ${t.impact}, ${t.scope},
                  ${t.topicEmbedding}::vector)""".update.run

  private def insertGroupSection(groupId: Long, sectionId: Long): ConnectionIO[Int] =
    sql"""INSERT INTO ${Fragment.const(Tables.BillConceptGroupSections)} (concept_group_id, section_id)
          VALUES ($groupId, $sectionId)
          ON CONFLICT (concept_group_id, section_id) DO NOTHING""".update.run

}
