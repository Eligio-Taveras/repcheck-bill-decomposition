package com.repcheck.decomposition.pipeline.persistence

import repcheck.shared.models.llm.{Effect, Impact, Scope}

/** A clustering-unit section to persist (no id — assigned by BIGSERIAL on insert). */
final case class SectionRow(
  sectionIndex: Int,
  subIndex: Int,
  sectionIdentifier: Option[String],
  heading: Option[String],
  content: String,
  embedding: Option[Array[Float]],
)

/** A stance-tagged topic of a concept (no id). */
final case class TopicRow(
  phrase: String,
  topic: String,
  effect: Effect,
  entity: String,
  impact: Impact,
  scope: Scope,
  topicEmbedding: Option[Array[Float]],
)

/** A concept group to persist (no id), with its member section ids + stance topics. */
final case class ConceptGroupRow(
  label: String,
  conceptSummary: String,
  embedding: Option[Array[Float]],
  memberSectionIds: List[Long],
  topics: List[TopicRow],
)

/** Provenance for one decomposition sweep (no id). */
final case class DecompositionRunRow(
  snapshotVersion: Int,
  orchestratorVersion: String,
  embedderVersion: String,
  clustererVersion: String,
  promptVersion: String,
  workflowRunId: Option[Long],
)

/** A section read back with its assigned id + embedding. */
final case class PersistedSection(
  sectionId: Long,
  sectionIndex: Int,
  subIndex: Int,
  content: String,
  embedding: Option[Array[Float]],
)

/**
 * Persistence boundary for the decomposition pipeline (D5). Outputs are reproducible per `(versionId,
 * snapshotVersion)`; the single-transaction [[persistDecomposition]] makes a bill's concept groups + topics
 * all-or-nothing, so the [[existsForVersion]] reuse-check is a sound idempotency key.
 */
trait DecompositionPersister[F[_]] {

  /** Reuse-check / idempotency key — has this version already been decomposed under this snapshot? */
  def existsForVersion(versionId: Long, snapshotVersion: Int): F[Boolean]

  /** Insert the run-provenance row (once per sweep); returns its `run_id`. */
  def startRun(run: DecompositionRunRow): F[Long]

  /** Mark a run's terminal status (`completed` | `completed_with_errors` | `failed`). */
  def completeRun(runId: Long, status: String): F[Unit]

  /**
   * Persist a bill's clustering units (idempotent upsert on `(version_id, section_index, sub_index)`); returns the
   * assigned section ids in input order.
   */
  def persistSections(versionId: Long, billId: Long, sections: List[SectionRow]): F[List[Long]]

  /** Read a version's persisted sections (+ embeddings) — for reuse / re-embed. */
  def loadSectionsWithEmbeddings(versionId: Long): F[List[PersistedSection]]

  /**
   * Persist a bill's concept groups + their topics + group↔section junctions in ONE transaction, each group tagged with
   * `snapshotVersion` + `runId`. All-or-nothing per bill.
   */
  def persistDecomposition(
    versionId: Long,
    billId: Long,
    snapshotVersion: Int,
    runId: Long,
    groups: List[ConceptGroupRow],
  ): F[Unit]

}
