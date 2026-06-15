package com.repcheck.decomposition.conformance

import repcheck.shared.models.llm.output.ClusterConceptOutput

/**
 * Self-test of the [[StructuredCodecLaws]] harness against a real codec the decomposition pipeline consumes
 * (`ClusterConceptOutput`, the D4 classify output). Proves the law-runner end-to-end; the type's own ownership of the
 * law stays in shared-models.
 */
class ClusterConceptOutputCodecLawsSpec extends StructuredCodecLaws[ClusterConceptOutput]("ClusterConceptOutput")
