package com.repcheck.decomposition.pipeline.config

import pureconfig.ConfigReader

/**
 * Configuration for the bill-decomposition orchestrator (Component 10.4). Loaded from `reference.conf` under the
 * `decomposition-pipeline` namespace via PureConfig (kebab-case HOCON → camelCase fields). The orchestration /
 * persistence / subscriber wiring that consumes these fields lands in slices 4–8; this slice fixes the shape +
 * defaults.
 *
 * @param parallelism
 *   FS2 concurrency over inbound `bill.text.ingested` events (one bill decomposed per stream element)
 * @param simplificationParallelism
 *   bounded `parEvalMap` width for the per-concept Haiku summarize+stance call (the LLM hot path)
 * @param maxSectionsPerBill
 *   safety cap — bills above this are processed but the cap bounds the worst-case fan-out
 * @param decompositionSnapshotVersion
 *   the snapshot the run writes under; idempotency key is `(versionId, decompositionSnapshotVersion)`
 */
final case class DecompositionPipelineConfig(
  parallelism: Int,
  simplificationParallelism: Int,
  maxSectionsPerBill: Int,
  decompositionSnapshotVersion: String,
  ollama: OllamaConfig,
  claude: ClaudeConfig,
  database: DatabaseConfig,
  pubsub: PubSubConfig,
) derives ConfigReader

/** Ollama qwen3 embedding sidecar (raw-section + summary + topic-noun embeddings, all 1024-dim). */
final case class OllamaConfig(
  baseUri: String,
  embeddingModel: String,
  embedParallelism: Int,
) derives ConfigReader

/**
 * Claude provider for per-concept summarization. Production model is HAIKU (validated to hold DP-7 retrieval; ~3× the
 * cost of nothing vs Sonnet's cost-prohibitive scale).
 */
final case class ClaudeConfig(
  model: String,
  maxTokens: Int,
) derives ConfigReader

/**
 * AlloyDB / Cloud SQL Postgres connection (decomposition reads bill_text_versions, writes the decomposition tables).
 */
final case class DatabaseConfig(
  host: String,
  port: Int,
  database: String,
  user: String,
  password: String,
) derives ConfigReader

/** Pub/Sub: subscribe to `bill.text.ingested`, publish `bill.decomposition.completed`. */
final case class PubSubConfig(
  projectId: String,
  subscriptionId: String,
  completedTopicId: String,
) derives ConfigReader
