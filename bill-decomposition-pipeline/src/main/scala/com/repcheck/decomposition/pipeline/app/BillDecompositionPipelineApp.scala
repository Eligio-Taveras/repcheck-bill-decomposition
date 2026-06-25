package com.repcheck.decomposition.pipeline.app

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all._

import pureconfig.ConfigSource

import org.typelevel.log4cats.slf4j.Slf4jLogger

import com.repcheck.decomposition.pipeline.config.DecompositionPipelineConfig

/**
 * Cloud Run Job entry point for the bill-decomposition orchestrator (Component 10.4 — the DP-0/DP-7-validated design,
 * which supersedes the stale votr 10.4 spec; see `docs/architecture/adr/ADR-001-...`). Pure wiring: `run` parses the
 * launcher args + loads config, then delegates to [[runPipeline]] (the testable seam).
 *
 * ==Launcher contract (positional, matches the data-ingestion pipelines)==
 *   - `args(0)` — config-JSON placeholder (currently unused; config loads from `application.conf`).
 *   - `args(1)` — `runId`: `workflow_runs.id` (string, required + non-blank). Provenance FK written to
 *     `bill_decomposition_runs` once the persist slice lands (slice 7).
 *   - `args(2)` — `stepRunId`: `workflow_run_steps.id` (Long, required + parseable).
 *
 * Strict by design: a missing/blank `runId` or missing/non-numeric `stepRunId` indicates a broken launcher contract and
 * fails the run fast rather than proceeding with a silent placeholder.
 *
 * Slice 1 brings the App up as a compiling shell that parses args, loads config, and reports readiness. Slices 4–8
 * replace the placeholder body with the resource graph (transactor, Ollama client, Claude provider, prompt engine,
 * persister, publisher) and the Pub/Sub subscriber loop.
 */
object BillDecompositionPipelineApp extends IOApp {

  /** Workflow-run provenance parsed from the launcher args; threaded into `bill_decomposition_runs` in slice 7. */
  final private[app] case class RunContext(runId: String, stepRunId: Long)

  def run(args: List[String]): IO[ExitCode] =
    runPipeline(loadConfig(), IO.fromEither(parseRunContext(args))).as(ExitCode.Success)

  private[app] def parseRunContext(args: List[String]): Either[Throwable, RunContext] =
    for {
      runId     <- extractRunId(args)
      stepRunId <- extractStepRunId(args)
    } yield RunContext(runId, stepRunId)

  /** `args(1)` — `workflow_runs.id`; required + non-blank. */
  private[app] def extractRunId(args: List[String]): Either[Throwable, String] = {
    val raw = args.lift(1).getOrElse("")
    Either.cond(raw.trim.nonEmpty, raw.trim, RunIdMissing(if (args.lengthIs > 1) raw else "<missing>"))
  }

  /** `args(2)` — `workflow_run_steps.id`; required + parseable Long. */
  private[app] def extractStepRunId(args: List[String]): Either[Throwable, Long] =
    args.lift(2).map(_.trim).filter(_.nonEmpty) match {
      case Some(raw) => raw.toLongOption.toRight(StepRunIdInvalid(raw))
      case None      => Left(StepRunIdInvalid("<missing>"))
    }

  /**
   * Load + validate config from the classpath `application.conf` (defaults baked into the jar; every
   * environment-specific value is overridden by a `${?ENV_VAR}` set on the Cloud Run Job, with secrets from Secret
   * Manager — see the header of `application.conf`). Fails fast with a readable message. The `source` is injectable so
   * the failure path is testable (production passes `ConfigSource.default`).
   */
  private[app] def loadConfig(source: ConfigSource = ConfigSource.default): IO[DecompositionPipelineConfig] =
    IO.fromEither(
      source
        .load[DecompositionPipelineConfig]
        .leftMap(failures =>
          new IllegalArgumentException(s"invalid decomposition-pipeline config:\n${failures.prettyPrint()}")
        )
    )

  /**
   * The pipeline body. Slice 1: log readiness (run context + config). Slices 4–8 wire the resource graph + Pub/Sub
   * subscriber loop here, and persist the run keyed by `runContext.runId`.
   */
  private[app] def runPipeline(configIo: IO[DecompositionPipelineConfig], runContextIo: IO[RunContext]): IO[Unit] =
    for {
      logger     <- Slf4jLogger.create[IO]
      config     <- configIo
      runContext <- runContextIo
      _ <- logger.info(
        s"bill-decomposition-pipeline starting — runId=${runContext.runId}, stepRunId=${runContext.stepRunId}, " +
          s"snapshot=${config.decompositionSnapshotVersion}, summarizer=${config.claude.model}, " +
          s"embedder=${config.ollama.embeddingModel}, parallelism=${config.parallelism}"
      )
      _ <- logger.info("scaffold only (slice 1) — orchestration wiring lands in slices 4–8")
    } yield ()

}

/** Flat, unique exception per failure case (no sealed hierarchy) — a broken launcher contract. */
final private[app] case class RunIdMissing(arg: String)
    extends RuntimeException(s"runId (args(1)) missing or blank: '$arg'")

final private[app] case class StepRunIdInvalid(raw: String)
    extends RuntimeException(s"stepRunId (args(2)) missing or non-numeric: '$raw'")
