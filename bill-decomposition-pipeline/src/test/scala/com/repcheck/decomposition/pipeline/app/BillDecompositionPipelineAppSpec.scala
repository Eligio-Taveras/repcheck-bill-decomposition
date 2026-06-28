package com.repcheck.decomposition.pipeline.app

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{ExitCode, IO}

import pureconfig.ConfigSource

import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

class BillDecompositionPipelineAppSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  private val validArgs = List("{}", "1", "0")

  "run" - {
    "parses the 3 launcher args, loads config, and exits success" in {
      BillDecompositionPipelineApp.run(validArgs).asserting(_ shouldBe ExitCode.Success)
    }

    "fails fast when runId (args(1)) is non-numeric" in {
      BillDecompositionPipelineApp.run(List("{}", "abc", "0")).attempt.asserting(_.isLeft shouldBe true)
    }

    "fails fast when stepRunId (args(2)) is non-numeric" in {
      BillDecompositionPipelineApp.run(List("{}", "1", "abc")).attempt.asserting(_.isLeft shouldBe true)
    }
  }

  "parseRunContext" - {
    "parses runId + stepRunId as Longs" in {
      IO(BillDecompositionPipelineApp.parseRunContext(List("{}", "7", "42")))
        .asserting(_ shouldBe Right(BillDecompositionPipelineApp.RunContext(7L, 42L)))
    }
    "fails when runId is missing" in {
      IO(BillDecompositionPipelineApp.parseRunContext(List("{}"))).asserting(_.isLeft shouldBe true)
    }
    "fails when stepRunId is missing" in {
      IO(BillDecompositionPipelineApp.parseRunContext(List("{}", "1"))).asserting(_.isLeft shouldBe true)
    }
  }

  "runPipeline" - {
    "completes for the default config + a valid run context (scaffold readiness)" in {
      BillDecompositionPipelineApp
        .runPipeline(
          BillDecompositionPipelineApp.loadConfig(validArgs),
          IO.pure(BillDecompositionPipelineApp.RunContext(1L, 0L)),
        )
        .assertNoException
    }
  }

  "loadConfig" - {
    "resolves the production summarizer + embedder from application.conf when args(0) is empty" in {
      BillDecompositionPipelineApp.loadConfig(List.empty).asserting { config =>
        config.claude.model shouldBe "claude-haiku-4-5"
        config.ollama.embeddingModel shouldBe "qwen3-embedding:0.6b"
        config.parallelism shouldBe 4
      }
    }

    "layers the args(0) JSON/HOCON blob over the defaults at highest precedence" in {
      BillDecompositionPipelineApp.loadConfig(List("{ parallelism = 99 }")).asserting { config =>
        config.parallelism shouldBe 99                  // overridden
        config.claude.model shouldBe "claude-haiku-4-5" // untouched default still resolves
      }
    }

    "fails fast on a base config missing required fields" in {
      BillDecompositionPipelineApp
        .loadConfig(List.empty, ConfigSource.string("parallelism = 1"))
        .attempt
        .asserting(_.isLeft shouldBe true)
    }
  }

}
