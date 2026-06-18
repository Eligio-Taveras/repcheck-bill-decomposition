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
   and is the precondition that makes the rest work. *Open item:* in the eval this used **pooled corpus** mean/std;
   production clusters one bill at a time, so it needs a **global mean/std artifact** computed once from a representative
   corpus and shipped with the model (NOT per-bill stats). See **Open items**.
2. **Distance** = `0.1·cosine + 0.9·graded-hierarchy`. The graded-hierarchy term uses the **full parser breadcrumb**
   (`TITLE I / Subtitle A / PART 2`) graded by **shared-prefix depth** (0 = identical path, 1 = nothing shared). This is
   the single biggest non-LLM lever. **No lexical/citation blending** — it was tested and was net-negative.
3. **HAC**, average linkage (UPGMA).
4. **Cut at `k = subjectCount`**, but as a **guide, not an exact split**: silhouette picks the natural `k` within
   **±30%** of the subject count. **Single-concept guard:** `subjectCount ≤ 1 → one group` (a single-subject bill is not
   decomposed).

## `ClusteringConfig` (the tuned values)

| knob | value | meaning |
|---|---|---|
| `transform` | `standardize` | anisotropy correction (applied upstream, see Open items) |
| `distance` | `cosine` | base section metric |
| `structureWeight` | `0.9` | weight on graded-hierarchy (= 1−α; α=0.1 cosine) |
| `gradedHierarchy` | `true` | full breadcrumb, shared-prefix-depth |
| `linkage` | `average` | UPGMA |
| `guidedTolerance` | `0.3` | silhouette window = ±30% of the subject count |
| `minK` | `1` | single-concept guard (`subjectCount ≤ minK` → one group) |

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

- **Standardization stats artifact.** The validated numbers used pooled-corpus mean/std. Production must compute a
  **global** mean/std once (from a representative corpus) and apply it per bill. Per-bill standardization was not
  validated and is risky on small bills.
- **Subject count source.** DP-0 used the Claude reference group count as the subject-count stand-in. Production reads
  the Congress.gov **`legislative-subject count`** (`bill_subjects`, currently un-ingested — data dep §9.2 of the plan).
- **Exact-vs-guided cut.** Cutting at exactly `k = S` scored mean ARI **0.607**; the smoothed k-formula `k = 0.912·S`
  scored **0.591** (both on the graded blend — exact slightly wins when the count is accurate). The **guided**
  silhouette-in-window cut is the production choice because it degrades far more gracefully to a *wrong* subject count —
  robustness sweep: at a 2× overstated count it holds **0.49 vs exact-k's 0.30**. Ship **guided**.
- **Determinism.** HAC on a fixed proximity matrix is deterministic (required for the idempotent re-cluster in plan
  stage 0). `SmileHacClusterer` uses first-appearance label renumbering so labels are stable.
