package com.repcheck.decomposition.pipeline.app

import cats.effect.{IO, IOApp}
import cats.syntax.all._

import pureconfig.ConfigSource

import org.typelevel.log4cats.slf4j.Slf4jLogger

import com.repcheck.decomposition.pipeline.config.DecompositionPipelineConfig

/**
 * Cloud Run Job entry point for the bill-decomposition orchestrator (Component 10.4 — the DP-0/DP-7-validated design,
 * which supersedes the stale votr 10.4 spec; see `docs/architecture/adr/ADR-001-...`). Pure wiring: `run` loads config,
 * builds a logger, and delegates to [[runPipeline]] (the testable seam).
 *
 * Slice 1 brings the App up as a compiling shell that loads + reports its config. Slices 4–8 replace the placeholder
 * body with the resource graph (transactor, Ollama client, Claude provider, prompt engine, persister, publisher) and
 * the Pub/Sub subscriber loop.
 */
object BillDecompositionPipelineApp extends IOApp.Simple {

  def run: IO[Unit] = runPipeline(loadConfig())

  /**
   * Load + validate config from the `decomposition-pipeline` namespace; fail fast with a readable message. The `source`
   * is injectable so the failure path is testable (production passes `ConfigSource.default`).
   */
  private[app] def loadConfig(source: ConfigSource = ConfigSource.default): IO[DecompositionPipelineConfig] =
    IO.fromEither(
      source
        .at("decomposition-pipeline")
        .load[DecompositionPipelineConfig]
        .leftMap(failures =>
          new IllegalArgumentException(s"invalid decomposition-pipeline config:\n${failures.prettyPrint()}")
        )
    )

  /**
   * The pipeline body. Slice 1: load config + log readiness. Slices 4–8 wire the resource graph + Pub/Sub subscriber
   * loop here.
   */
  private[app] def runPipeline(configIo: IO[DecompositionPipelineConfig]): IO[Unit] =
    for {
      logger <- Slf4jLogger.create[IO]
      config <- configIo
      _ <- logger.info(
        s"bill-decomposition-pipeline starting — snapshot=${config.decompositionSnapshotVersion}, summarizer=${config.claude.model}, embedder=${config.ollama.embeddingModel}, parallelism=${config.parallelism}"
      )
      _ <- logger.info("scaffold only (slice 1) — orchestration wiring lands in slices 4–8")
    } yield ()

}
