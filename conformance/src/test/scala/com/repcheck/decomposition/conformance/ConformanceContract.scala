package com.repcheck.decomposition.conformance

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Base for every per-trait conformance suite. An impl plugs in by extending an abstract `*Contract extends
 * ConformanceContract` (e.g. `SectionParserContract`) and supplying the impl under test; a provider swap or refactor
 * that breaks the contract then fails CI (§10c#1).
 */
abstract class ConformanceContract extends AnyFlatSpec with Matchers
