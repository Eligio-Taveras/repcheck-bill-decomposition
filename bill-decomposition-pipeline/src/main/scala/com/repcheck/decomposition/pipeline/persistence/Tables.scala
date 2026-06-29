package com.repcheck.decomposition.pipeline.persistence

/** Table-name constants for the decomposition storage — no hardcoded table strings in SQL. */
object Tables {
  val BillDecompositionRuns: String    = "bill_decomposition_runs"
  val BillConceptGroups: String        = "bill_concept_groups"
  val BillConceptGroupSections: String = "bill_concept_group_sections"
  val BillConceptTopics: String        = "bill_concept_topics"
  val BillTextSections: String         = "bill_text_sections"
}
