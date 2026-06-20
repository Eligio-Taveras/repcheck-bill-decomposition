# Flat-bill grouping research (2026-06)

Experiment scripts behind the **committed flat-bill grouping strategy**: a deterministic,
supervised clusterer that groups a flat bill's sections into concepts at **held-out ARI 0.407 ≈
the LLM inter-judge ceiling** (Haiku↔Sonnet ≈ 0.41), at zero inference cost.

These are throwaway research scripts (plain Python, no deps beyond the stdlib + a local Ollama
for embeddings + the Anthropic API for labeling). They are kept for provenance and to retrain the
models. They read their data from the gold archive (see **Data** below), not from this repo.

## The committed pipeline (what to port to Scala)
1. **Affinity** — 5-feature logistic regression over section pairs `[emb_cos, tfidf_cos, top-term
   jaccard, citation jaccard, position]` → P(same concept).
2. **Dendrogram** — greedy average-linkage agglomeration on the affinity.
3. **Supervised vetoed merge-stop** — 13-feature logistic regression scores each candidate merge
   (label: the two clusters are within one gold concept); *vetoed* agglomeration takes the
   highest-similarity merge whose score ≥ tau (~0.6), skipping rejected ones, stopping when none
   pass. This is the cut selector — no K needed.

Both models retrain deterministically from the gold. The MLP variant was tested and lost (linear
+ good features wins) — do not bother with a nonlinear model.

## Result ladder (held-out 99 flat bills)
| stage | ARI |
|---|---|
| unsupervised embedding clustering | ~0.15 |
| supervised affinity, oracle-K cut | 0.345 |
| supervised affinity, static threshold | 0.292 |
| + supervised merge-stop (v7) | 0.384 |
| + richer merge features (v8) | **0.407** |
| Haiku (LLM reference / ceiling) | 0.411 |

## Scripts
- `metric_learn.py` — first pass (97-bill, 5-fold CV).
- `metric_learn_v2.py` — train 297 / held-out 100 split.
- `metric_learn_v3.py` — K-free affinity threshold, trained on train.
- `metric_learn_v4.py` — per-bill T\* ceiling + length-aware threshold regression + embedding-free ablation.
- `metric_learn_v5.py` — silhouette / modularity cut selectors (negative result: unsupervised cut can't find it).
- `metric_learn_v6.py` — enriched affinity features: interactions + heading + lexical (negative: features aren't the lever).
- `metric_learn_v7.py` — **supervised merge-stop** (sequential + vetoed). The breakthrough.
- `metric_learn_v8.py` — **enriched merge-stop** (affinity-distribution + topic-shift feats) + MLP ablation. Final 0.407.
- `perbill.py` — per-bill ARI breakdown + failure-mode split (affinity-failure vs cut-missed).
- `bakeoff.py`, `review_bill.py`, `ollama_grouper.py` — concept-extraction bake-off (Haiku vs local Ollama, describe-then-group).
- `tfidf_embed_cluster.py` — TF-IDF vs whole-section vs TF-IDF-terms→embed clustering.
- `cutfeat.py`, `cutfit.py`, `cutsearch.py`, `fitcut.py` — cut-count formula exploration (`n^0.75`); retired in favor of the supervised merge-stop.
- `omni_section_test.py` — omnibus sprawl / section=concept test.
- `export_models.py` — **productionization**: retrains the shipped affinity + merge-stop models on all
  405 flat bills and writes the JSON artifacts consumed by the Scala port
  (`decomposition-ml/src/main/resources/flat-grouping/*-v1.json`) plus the parity fixture used by
  `FlatSectionClustererParitySpec`. Re-run this to regenerate the artifacts.

## Production port
The validated pipeline is implemented in Scala under
`decomposition-ml/src/main/scala/com/repcheck/decomposition/ml/cluster/flat/` (`FlatSectionClusterer`
+ single-responsibility helpers). It loads the bundled artifacts and reproduces the Python reference
clustering exactly (parity ARI 1.0 in `FlatSectionClustererParitySpec`).

## Data
The 406-bill Sonnet-labeled gold + parsed sections + raw corpus + cached embeddings live in GCS
(costly to regenerate, ~600 Sonnet calls):

    gs://repcheck-decomposition-gold/repcheck-decomposition-gold-v1.zip   (project: repcheck-dev)

The scripts expect it unzipped at `C:/Temp/expansion/` (gold/, sections/, corpus/, emb/, manifest.tsv,
train_add.txt, validation.txt). See the README inside the archive for layout.
