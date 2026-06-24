package com.repcheck.decomposition.pipeline.config

import pureconfig.ConfigSource

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DecompositionPipelineConfigSpec extends AnyFlatSpec with Matchers {

  private val config: DecompositionPipelineConfig =
    ConfigSource.default
      .at("decomposition-pipeline")
      .load[DecompositionPipelineConfig]
      .fold(failures => fail(failures.prettyPrint()), identity)

  "reference.conf" should "load the decomposition-pipeline config with the validated production defaults" in {
    config.claude.model shouldBe "claude-haiku-4-5" // production summarizer = Haiku (DP-7 validated to hold retrieval)
    config.ollama.embeddingModel shouldBe "qwen3-embedding:0.6b" // DP-7 validated embedder
    config.decompositionSnapshotVersion shouldBe "v1"
    config.maxSectionsPerBill shouldBe 500
    config.pubsub.completedTopicId shouldBe "bill-decomposition-completed"
  }

  it should "carry sane concurrency + connection defaults" in {
    config.parallelism should be > 0
    config.simplificationParallelism should be > 0
    config.ollama.embedParallelism should be > 0
    config.database.port shouldBe 5432
    config.ollama.baseUri shouldBe "http://localhost:11434"
  }

}
