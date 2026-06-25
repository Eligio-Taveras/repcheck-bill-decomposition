package com.repcheck.decomposition.pipeline.app

import cats.effect.testing.scalatest.AsyncIOSpec

import pureconfig.ConfigSource

import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

class BillDecompositionPipelineAppSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  "run" - {
    "loads config + completes without error (the IOApp entry point)" in {
      BillDecompositionPipelineApp.run.assertNoException
    }
  }

  "runPipeline" - {
    "completes for the default config (scaffold readiness)" in {
      BillDecompositionPipelineApp.runPipeline(BillDecompositionPipelineApp.loadConfig()).assertNoException
    }
  }

  "loadConfig" - {
    "resolves the production summarizer + embedder from reference.conf" in {
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
