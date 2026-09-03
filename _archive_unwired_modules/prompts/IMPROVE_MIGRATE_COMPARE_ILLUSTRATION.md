# ⚠ DO NOT REMOVE — prompt preamble
**Scope:** Improve ONE thing — the **side-by-side compare illustration in the middle** of
`docs/MigrateComparisonPaper.md`: the `## How it differs — the architecture` block (the `mc-cmp` two-column
**Legacy ERP "server of record"** vs **Ours "the browser is the server"** mermaid diagrams, ~lines 103–127).
VISUAL only. Text/numbers are fine — don't rewrite prose. ONLY this illustration (+ its CSS/assets).
**Prime directive:** non-invent — the picture must say exactly what the text already says, nothing new.
**Log mandate:** preview at localhost and READ the build output before deploying. Honour this block until DONE.

---

## What the illustration is now (2026-06-09)
Two stacked mermaid `flowchart TB` panels side by side under a `<div class="mc-cmp">`:
- **Legacy:** user →HTTP→ app server →SQL→ database(owns truth) → posting/validation -.rendered row.-> user.
- **Ours:** user → op → local WASM kernel(commit·hash-chain·sign) → replay/fold(SQLite-WASM) → paint·0 network;
  kernel -.later async.-> dumb facilitator (disposable host).
- The thesis it must make *obvious at a glance*: **legacy = a network round-trip per interaction, DB owns the
  truth**; **ours = 0 network on the read/fold path, the signed log is the truth, host is disposable.**

Live: `https://red1oon.github.io/BIMCompiler/MigrateComparisonPaper/` → "How it differs" section.

## The task — make the contrast hit instantly
The user is visual; the current diagrams are functional but flat. Make the *difference* the star. Ideas (pick
what genuinely lifts it; preview every change at localhost first):
- **Visual asymmetry that encodes the point:** e.g. the legacy "network" hop drawn as the heavy/red bottleneck
  (round-trip per gesture), the "ours" read/fold path drawn as a tight local loop with the network demoted to a
  faint async side-arrow. The eye should see "many crossings vs none" without reading.
- **Align the two columns** so equivalent stages sit at the same height (gesture row, truth row, paint row) —
  a clean A/B read. Equal widths, equal card framing, clear "LEGACY ▸ ◂ OURS" headers.
- **Mark the truth-owner** distinctly in each (DB-owns-truth vs log-is-truth) and the **round-trip count**
  (legacy ≥1 per interaction · ours 0) as a small badge on each side — using the numbers already in the text.
- **Color/dark-light:** must read in both MkDocs-material schemes and on a phone (the `mc-cmp` columns must
  stack gracefully when narrow, not overflow).
- Keep it **static** (mermaid + CSS/SVG only — nothing that needs a server or breaks offline).

## Constraints
- No new claims/numbers; mirror lines 129–132 + the §no-server diagram exactly. `mc-cmp` CSS lives in the doc /
  `mkdocs.yml` extra CSS — find it before restyling.
- Deploy = `mkdocs gh-deploy --force` from repo root (rebuilds whole site, force-pushes `gh-pages`). Preview:
  `scripts/serve_docs.sh` → `localhost:8000/MigrateComparisonPaper/`. Ship it when it looks right — no ceremony.

## Done =
The middle compare illustration makes "round-trip-per-interaction vs 0-network local fold" obvious at a glance,
columns align A/B, renders clean dark/light + mobile, builds clean, and is **deployed live** (verify with a
cache-busted fetch). Report in a few lines: what changed visually + the live URL.
