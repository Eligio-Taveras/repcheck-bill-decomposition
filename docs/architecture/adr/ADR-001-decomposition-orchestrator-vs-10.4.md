# ADR-001 — The decomposition orchestrator follows the DP-0/DP-7-validated design, not the stale votr 10.4 spec

- **Status:** Accepted (2026-06-23)
- **Context:** Building the `bill-decomposition-pipeline` (Component 10.4) orchestrator.
- **Supersedes (on conflict):** `docs/architecture/acceptance-criteria/10-llm-analysis/10.4-decomposition-orchestrator.md`, `10.5-in-process-ml.md`, `10.8-embedding-generation-service.md`, and the votr master copies of the same.

## Decision

The orchestrator is built to the design **empirically validated across DP-0, DP-1, and DP-7** (PRs #19, #21–#25, and the DP-7 searchability/Haiku experiments). Where the votr 10.4/10.5/10.8 acceptance criteria conflict with that validated design, **this ADR and the validated design win**. The acceptance-criteria docs predate the empirical work and describe a first-principles design that the evidence overturned; they are to be updated in lockstep (tracked below).

## The 6 drift items (spec → validated reality)

| # | 10.4/10.5/10.8 spec says | Validated reality (what we build) | Evidence |
|---|---|---|---|
| 1 | **Ollama LLM** parses bill text into sections | **Deterministic** GPO/XML/plain-text parser (`text-structure`, shipped, 100% cov) | DP-0; parser is byte-deterministic + free |
| 2 | DJL/ONNX **MiniLM 384-dim** in-process embeddings | **qwen3-embedding:0.6b 1024-dim** via Ollama | DP-0 (the live corpus is 1024-dim qwen3); DP-7 retrieval validated on it |
| 3 | **DBSCAN** clustering; effectful `ConceptClusterer[F]` | **Pure** `RoutingConceptClusterer` — flat→logistic-regression, omnibus→silhouette-cut HAC | PRs #24/#25; DBSCAN epsilon brittle at 1024-dim; clusterer is pure (no `F[_]`) |
| 4 | `SimplifiedConceptOutput(simplifiedText, keyTopics: List[String])` | `{summary, topics:[{phrase, topic, effect, entity, impact, scope}]}` — searchable summary + **stance-tagged** topics | DP-7; stance metadata is load-bearing for alignment scoring (Component 11) + UI annotations |
| 5 | **3 separate** DB transactions per bill (and the spec is internally inconsistent about it) | **1 transaction** per bill, with chunked batch inserts | atomic decomposition; clean idempotency + rollback |
| 6 | DOs key on `versionId: Long` | inbound `BillTextIngestedEvent.versionId: UUID` | `fetchBillText` resolves UUID→internal id; verify column before slice 4 |

## NOT drift (the spec was right)

- **Haiku for production summarization** (10.4 `ModelTier.Haiku` default). Sonnet was the **gold-builder** only — used to validate retrieval (DP-7) + tune the non-LLM steps; cost-prohibitive at corpus scale. Haiku validated to hold retrieval: **P@5 0.677 vs Sonnet 0.689 (Δ−0.012), MRR identical, nDCG 0.901 vs 0.916** under the production RRF 4-way pipeline (2026-06-23).

## Retrieval substrate (DP-7, for downstream Component 10.6+ context)

RRF 4-way (k=60): topic-noun vec + summary vec + raw-section vec + BM25-on-summary. P@5 0.689 / nDCG@10 0.916 (Sonnet gold), ~equivalent on Haiku. Stance metadata persisted per topic but **not** used as primary retrieval vectors (verbose stance phrases diluted cosine — DP-7).

## Consequences

- The full design + 8-slice plan lives in `session_notes/bill-decomposition/2026-06-01_...-plan.md` (§10b-2, post-DP-7 reconciliation).
- **Tracked:** update votr 10.4/10.5/10.8 acceptance criteria + their `.compressed.md` agent-doc copies to the validated design, so future agents don't re-import the pre-DP-0 plan (Ollama-parser / MiniLM / DBSCAN / `simplifiedText`). Interleave with the slices; do not defer past slice 8.
