package com.repcheck.decomposition.pipeline.app

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{ExitCode, IO}

import pureconfig.ConfigSource

import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

class BillDecompositionPipelineAppSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  private val validArgs = List("cfg-unused", "run-1", "0")

  "run" - {
    "parses the 3 launcher args, loads config, and exits success" in {
      BillDecompositionPipelineApp.run(validArgs).asserting(_ shouldBe ExitCode.Success)
    }

    "fails fast when stepRunId (args(2)) is non-numeric" in {
      BillDecompositionPipelineApp.run(List("cfg-unused", "run-1", "abc")).attempt.asserting(_.isLeft shouldBe true)
    }
  }

  "runPipeline" - {
    "completes for the default config + a valid run context (scaffold readiness)" in {
      BillDecompositionPipelineApp
        .runPipeline(
          BillDecompositionPipelineApp.loadConfig(),
          IO.pure(BillDecompositionPipelineApp.RunContext("run-1", 0L)),
        )
        .assertNoException
    }
  }

  "extractRunId" - {
    "returns the trimmed args(1) when present + non-blank" in {
      IO(BillDecompositionPipelineApp.extractRunId(List("cfg", " run-1 ", "0"))).asserting(_ shouldBe Right("run-1"))
    }
    "fails when args(1) is blank" in {
      IO(BillDecompositionPipelineApp.extractRunId(List("cfg", "   "))).asserting(_.isLeft shouldBe true)
    }
    "fails when args(1) is missing" in {
      IO(BillDecompositionPipelineApp.extractRunId(List("cfg"))).asserting(_.isLeft shouldBe true)
    }
  }

  "extractStepRunId" - {
    "parses args(2) as a Long" in {
      IO(BillDecompositionPipelineApp.extractStepRunId(List("cfg", "run-1", "42"))).asserting(_ shouldBe Right(42L))
    }
    "fails when args(2) is non-numeric" in {
      IO(BillDecompositionPipelineApp.extractStepRunId(List("cfg", "run-1", "abc")))
        .asserting(_ shouldBe Left(StepRunIdInvalid("abc")))
    }
    "fails when args(2) is missing" in {
      IO(BillDecompositionPipelineApp.extractStepRunId(List("cfg", "run-1"))).asserting(_.isLeft shouldBe true)
    }
  }

  "loadConfig" - {
    "resolves the production summarizer + embedder from application.conf" in {
      BillDecompositionPipelineApp.loadConfig().asserting { config =>
        config.claude.model shouldBe "claude-haiku-4-5"
        config.ollama.embeddingModel shouldBe "qwen3-embedding:0.6b"
      }
    }

    "fails fast on a config missing required fields" in {
      val incomplete = ConfigSource.string("parallelism = 1")
      BillDecompositionPipelineApp.loadConfig(incomplete).attempt.asserting(_.isLeft shouldBe true)
    }
  }

}
