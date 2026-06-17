package com.repcheck.decomposition.evaluation.wiring

import scala.concurrent.duration._

import cats.effect.{IO, Resource}

import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder

import com.repcheck.embedding.{OllamaConfig, OllamaEmbeddingClient, SemanticEmbeddingService}
import com.repcheck.utils.errors.{RetryConfig, RetryWrapper}

/**
 * DP0-3 throwaway wiring: a live Ollama-backed F3 [[SemanticEmbeddingService]] for the DP-0 experiments. Local-only —
 * the only thing exercising it is the E2ETest-tagged smoke spec. The embedding-model A/B (qwen3-embedding:0.6b vs the
 * 7.6B model) is a DP0-5 experiment; this wires the F3 default (1024-dim).
 */
object EmbeddingHarness {

  val OllamaBaseUri  = "http://localhost:11434"
  val EmbeddingModel = "qwen3-embedding:0.6b"
  val Dimension      = 1024

  def resource(
    baseUri: String = OllamaBaseUri,
    model: String = EmbeddingModel,
  ): Resource[IO, SemanticEmbeddingService[IO]] =
    EmberClientBuilder.default[IO].build.map { client =>
      val config = OllamaConfig(
        baseUri = Uri.unsafeFromString(baseUri),
        model = model,
        expectedDimension = Dimension,
        keepAlive = None,
        requestTimeout = 60.seconds,
        retry = RetryConfig(),
      )
      val retry = new RetryWrapper[IO]((_, _, _, _, _, _) => IO.unit)
      new OllamaEmbeddingClient[IO](client, config, retry)
    }

}
