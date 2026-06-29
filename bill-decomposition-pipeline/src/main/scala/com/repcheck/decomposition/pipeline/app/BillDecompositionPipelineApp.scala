package com.repcheck.decomposition.pipeline.app

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all._

import pureconfig.{ConfigObjectSource, ConfigSource}

import org.typelevel.log4cats.slf4j.Slf4jLogger

import com.repcheck.decomposition.pipeline.config.DecompositionPipelineConfig

/**
 * Cloud Run Job entry point for the bill-decomposition orchestrator (Component 10.4 — the DP-0/DP-7-validated design,
 * which supersedes the stale votr 10.4 spec; see `docs/architecture/adr/ADR-001-...`). Pure wiring: `run` parses the
 * launcher args + loads config, then delegates to [[runPipeline]] (the testable seam).
 *
 * ==Launcher contract (positional, uniform across every RepCheck pipeline)==
 *   - `args(0)` — JSON/HOCON config-override blob, layered with highest precedence over `application.conf` defaults +
 *     `${?ENV_VAR}` overrides. Pass `{}` for no override. (This is how config is injected per-invocation.)
 *   - `args(1)` — `runId`: `workflow_runs.id` (Long, required + parseable). Provenance FK written to
 *     `bill_decomposition_runs` once the persist slice lands (slice 7).
 *   - `args(2)` — `stepRunId`: `workflow_run_steps.id` (Long, required + parseable).
 *
 * Strict by design: a missing/non-numeric `runId` or `stepRunId` indicates a broken launcher contract and fails the run
 * fast rather than proceeding with a silent placeholder.
 *
 * Slice 1 brings the App up as a compiling shell that parses args, loads config, and reports readiness. Slices 4–8
 * replace the placeholder body with the resource graph (transactor, Ollama client, Claude provider, prompt engine,
 * persister, publisher) and the Pub/Sub subscriber loop.
 */
object BillDecompositionPipelineApp extends IOApp {

  /** Workflow-run provenance parsed from the launcher args; threaded into `bill_decomposition_runs` in slice 7. */
  final private[app] case class RunContext(runId: Long, stepRunId: Long)

  def run(args: List[String]): IO[ExitCode] =
    runPipeline(loadConfig(args), IO.fromEither(parseRunContext(args))).as(ExitCode.Success)

  private[app] def parseRunContext(args: List[String]): Either[Throwable, RunContext] =
    for {
      runId     <- extractLongArg(args, 1, raw => RunIdInvalid(raw))
      stepRunId <- extractLongArg(args, 2, raw => StepRunIdInvalid(raw))
    } yield RunContext(runId, stepRunId)

  /** Positional Long arg at `idx`; required + parseable. */
  private[app] def extractLongArg(args: List[String], idx: Int, onError: String => Throwable): Either[Throwable, Long] =
    args.lift(idx).map(_.trim).filter(_.nonEmpty) match {
      case Some(raw) => raw.toLongOption.toRight(onError(raw))
      case None      => Left(onError("<missing>"))
    }

  /**
   * Load + validate config: `application.conf` (defaults baked into the jar; every environment-specific value is
   * overridden by a `${?ENV_VAR}` set on the Cloud Run Job, with secrets from Secret Manager — see the header of
   * `application.conf`), with the optional `args(0)` JSON/HOCON blob layered on top at highest precedence. Fails fast
   * with a readable message. The base `source` is injectable so the failure path is testable.
   */
  private[app] def loadConfig(
    args: List[String],
    source: ConfigObjectSource = ConfigSource.default,
  ): IO[DecompositionPipelineConfig] = {
    val merged = args.lift(0).map(_.trim).filter(_.nonEmpty) match {
      case Some(jsonOverride) => ConfigSource.string(jsonOverride).withFallback(source)
      case None               => source
    }
    IO.fromEither(
      merged
        .load[DecompositionPipelineConfig]
        .leftMap(failures =>
          new IllegalArgumentException(s"invalid decomposition-pipeline config:\n${failures.prettyPrint()}")
        )
    )
  }

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
          s"summarizer=${config.claude.model}, " +
          s"embedder=${config.ollama.embeddingModel}, parallelism=${config.parallelism}"
      )
      _ <- logger.info("scaffold only (slice 1) — orchestration wiring lands in slices 4–8")
    } yield ()

}

/** Flat, unique exception per failure case (no sealed hierarchy) — a broken launcher contract. */
final private[app] case class RunIdInvalid(raw: String)
    extends RuntimeException(s"runId (args(1)) missing or non-numeric: '$raw'")

final private[app] case class StepRunIdInvalid(raw: String)
    extends RuntimeException(s"stepRunId (args(2)) missing or non-numeric: '$raw'")
