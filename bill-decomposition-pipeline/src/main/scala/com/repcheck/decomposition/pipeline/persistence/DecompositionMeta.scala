package com.repcheck.decomposition.pipeline.persistence

import doobie.{Get, Put}

import repcheck.shared.models.llm.{Effect, Impact, Scope}

/**
 * Doobie codecs for the decomposition stance enums. effect/impact/scope are stored in TEXT columns (CHECK-constrained
 * to the enum apiValues — migration 052), so they map via the enum's `fromString` (through Doobie's `temap` ERROR
 * channel — a bad DB value surfaces as a read error, never a thrown exception) and `apiValue` on write. The pgvector
 * `Array[Float]` Get/Put come from repcheck-utils (`VectorCodec.{floatArrayGet, floatArrayPut}`); an INSERT of a vector
 * still needs the `::vector` cast in the SQL.
 */
object DecompositionMeta {

  implicit val effectGet: Get[Effect] = Get[String].temap(Effect.fromString)
  implicit val effectPut: Put[Effect] = Put[String].contramap(_.apiValue)

  implicit val impactGet: Get[Impact] = Get[String].temap(Impact.fromString)
  implicit val impactPut: Put[Impact] = Put[String].contramap(_.apiValue)

  implicit val scopeGet: Get[Scope] = Get[String].temap(Scope.fromString)
  implicit val scopePut: Put[Scope] = Put[String].contramap(_.apiValue)
}
