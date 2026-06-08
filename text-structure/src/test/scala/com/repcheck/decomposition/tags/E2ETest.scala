package com.repcheck.decomposition.tags

import org.scalatest.Tag

/**
 * Tag for tests that need live data (AlloyDB export / network). Excluded from `sbt test`; run explicitly via `sbt
 * "textStructure/testOnly -- -n com.repcheck.tags.E2ETest"`.
 */
object E2ETest extends Tag("com.repcheck.tags.E2ETest")
