# Decomposition Clustering — DP-0-Validated Production Config (D3b)

**Status:** validated against the 25-bill gold (DP-0, 2026-06-17). This is the config the production `ConceptClusterer`
(D3b) must use to reproduce the tested behavior. It is encoded in
[`ClusteringConfig`](../../evaluation/src/main/scala/com/repcheck/decomposition/evaluation/cluster/ClusteringConfig.scala)
and implemented by
[`SmileHacClusterer.cluster`](../../evaluation/src/main/scala/com/repcheck/decomposition/evaluation/cluster/SmileHacClusterer.scala).

> This **refines** the original plan (`session_notes/bill-decomposition/2026-06-01_…-plan.md` §7/§7b, D11/D15). The plan
> specified pure-cosine HAC with a `D_max`-constrained silhouette cut and a >15-subjects branch switch. DP-0 found two
> material improvements and one simplification — see **What DP-0 changed** below.

## The production pipeline

Given one bill's section embeddings (1024-dim, Ollama), their parser breadcrumbs, and the bill's subject count:

1. **Standardize** the embeddings (per-dimension mean-center + unit-variance). Cuts embedding anisotropy 0.385 → 0.11
   and is the precondition that makes the rest work. The eval used **pooled corpus** mean/std; production clusters one
   bill at a time, so it carries a **fixed global mean/std artifact** — computed once over the **full 425k-chunk
   `raw_bill_text.embedding` universe** (233k bills) and bundled as `standardization-stats-v1.json` (mirrored to GCS).
   Validated to reproduce the pooled-stats ARI on the gold (mean-vector cosine 0.944, ARI delta −0.002 — chunk-vs-section
   granularity is immaterial). Applied inside `HacConceptClusterer`.
2. **Distance** = `(1−w)·cosine + w·graded-hierarchy`, base `w = 0.9`. The graded-hierarchy term uses the **full parser
   breadcrumb** (`TITLE I / Subtitle A / PART 2`) graded by **shared-prefix depth** (0 = identical path, 1 = nothing
   shared) — the single biggest non-LLM lever. **No lexical/citation blending** (tested, net-negative). **Adaptive
   structure (`adaptiveStructure=true`):** `w` is scaled by the bill's **structural coverage** (fraction of sections with
   a non-empty breadcrumb). A **flat** bill (short resolution / simple act, no Title hierarchy) has coverage 0 → the inert
   structural term is dropped and the cut runs on cosine, which lets the silhouette pick a better `k`. Hierarchical bills
   (coverage ≈1) are unchanged.
3. **HAC**, average linkage (UPGMA).
4. **Cut at `k = subjectCount`**, but as a **guide, not an exact split**: silhouette picks the natural `k` within
   **±30%** of the subject count. **Single-concept guard:** `subjectCount ≤ 1 → one group` (a single-subject bill is not
   decomposed). **Adaptive cut (`adaptiveCut=true`):** on a **flat** bill (coverage 0) the silhouette is unreliable (it
   decreases monotonically with `k`, so the guided search under-segments), so cut at **exactly `subjectCount`** instead.
   Hierarchical bills keep the guided cut.

## `ClusteringConfig` (the tuned values)

| knob | value | meaning |
|---|---|---|
| `transform` | `standardize` | anisotropy correction (applied upstream, see Open items) |
| `distance` | `cosine` | base section metric |
| `structureWeight` | `0.9` | base weight on graded-hierarchy (= 1−α; α=0.1 cosine) |
| `gradedHierarchy` | `true` | full breadcrumb, shared-prefix-depth |
| `adaptiveStructure` | `true` | scale `structureWeight` by structural coverage — flat bills fall back to cosine (+0.064 mean ARI on the gold, zero regression on hierarchical) |
| `linkage` | `average` | UPGMA |
| `guidedTolerance` | `0.3` | silhouette window = ±30% of the subject count |
| `minK` | `1` | single-concept guard (`subjectCount ≤ minK` → one group) |
| `adaptiveCut` | `true` | on flat bills (coverage ≤ `flatCutCoverage`) cut at exactly `subjectCount` instead of the silhouette window (+0.031 non-trivial / +0.086 flat mean ARI, zero regression elsewhere) |
| `flatCutCoverage` | `0.0` | coverage threshold for "flat" under `adaptiveCut` (0.0 = only fully-flat bills; mid-coverage cut not yet gold-validated) |

## Evidence

- **DP0-5b (intrinsic, vs the Claude reference grouping):** mean **ARI 0.607**, NMI 0.84 over 11 non-trivial bills
  (omnibus 0.51–0.77). Lever attribution: standardize → graded-hierarchy blend lifted mean ARI **0.392 → 0.607**.
  Report: `evaluation/COMPARISON_REPORT.md`.
- **DP0-6 (extrinsic §10b retrieval gate):** over 269 reference-concept queries, decomposition **beats** B1 whole-bill,
  B2 raw-sections, and B3 Congress.gov-subject buckets on coverage (P@5, R-precision +33%, recall@10) and ties raw
  sections on first-hit MRR (max-pool ranking). Decomposition is the best method at retrieving a coherent concept.
  Report: `evaluation/RETRIEVAL_REPORT.md`.

## What DP-0 changed vs the original plan

| plan (§7/§7b, D11/D15) | DP-0-validated | why |
|---|---|---|
| pure **cosine** distance | `0.1·cosine + 0.9·graded-hierarchy` | structure is the biggest non-LLM lever (+40% ARI); cosine alone under-segments omnibus Titles |
| cut = silhouette **constrained to merge-height ≤ `D_max`**, `D_max` tuned globally | cut at **`k = subjectCount`**, guided silhouette in a **±30% window** | on the structure blend, `D_max` is a degenerate Title-gap selector; the subject count is the real lever |
| **>15 subjects → silhouette; ≤15 → `D_max`-only** branch switch | single mechanism: **guided cut@k=subjects** for all; `S ≤ 1 → 1 group` | no need for a branch — the guided window adapts; one code path |
| (no embedding transform) | **standardize** (anisotropy 0.385→0.11) | precondition for the blend/cut to work |
| Congress.gov subjects feed only the **cut count** | unchanged — subjects drive `k` (guide), not the concept vocabulary | confirmed |

## Open items for the official D3b implementation

- **Standardization stats artifact — DONE.** Computed the **global** per-dim mean/std once over the full 425k-chunk
  `raw_bill_text.embedding` universe (233k bills), bundled as `standardization-stats-v1.json` (→ GCS
  `repcheck-decomposition-config`), applied per bill inside `HacConceptClusterer`. Validated to reproduce the
  pooled-stats ARI on the gold (delta −0.002). The effectful GCS-with-fallback loader lands with the pipeline module.
- **Adaptive structure — DONE, default on.** `adaptiveStructure=true` scales `structureWeight` by structural coverage so
  flat bills fall back to cosine: +0.064 mean ARI on the gold (323852 +0.53, 415327 +0.18), zero regression on
  hierarchical bills. *Caveat:* the gold only exercises coverage extremes (≈0 or ≥0.92); the linear interpolation for
  **mid-coverage** bills is reasonable but not yet gold-validated.
- **Adaptive cut — DONE, default on.** `adaptiveCut=true`: on flat bills the silhouette under-segments (the oracle-best
  `k` sits in the window but the monotonic silhouette grabs the smallest `k`), so cut at exactly `subjectCount`. +0.031
  non-trivial / +0.086 flat mean ARI, stacks on top of the blend (flat-4 mean 0.261 → 0.439 → 0.525), zero regression on
  trivial/hierarchical bills. *Caveat:* exact-cut trusts the subject count fully (no guided window), so it trades
  robustness-to-a-wrong-count for the flat-bill gain. Validated with the **reference** concept count; production uses the
  Congress.gov **legislative-subject count**, which should be re-checked on flat bills with real counts.
- **Subject count source — DONE (data-ingestion #149).** Production reads the Congress.gov **`legislative-subject
  count`** from `bill_subjects` (now ingested; DP-0 used the Claude reference group count as the stand-in).
- **Exact-vs-guided cut.** Cutting at exactly `k = S` scored mean ARI **0.607**; the smoothed k-formula `k = 0.912·S`
  scored **0.591** (both on the graded blend — exact slightly wins when the count is accurate). The **guided**
  silhouette-in-window cut is the production choice because it degrades far more gracefully to a *wrong* subject count —
  robustness sweep: at a 2× overstated count it holds **0.49 vs exact-k's 0.30**. Ship **guided**.
- **Determinism.** HAC on a fixed proximity matrix is deterministic (required for the idempotent re-cluster in plan
  stage 0). `SmileHacClusterer` uses first-appearance label renumbering so labels are stable.
