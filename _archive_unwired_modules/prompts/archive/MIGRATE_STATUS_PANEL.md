# ⚠ DO NOT REMOVE — MIGRATE STATUS PANEL (single session card)
# Paste-to-start: `proceed with prompts/MIGRATE_STATUS_PANEL.md`
# Scope: update the migration-honesty status panel so every number traces to a source, the
#   missing coverage table is resolved, and the "delivery" half of the thesis is actually shown.
# READ THE LOG / verify counts against the cited sources before editing — NON-INVENT, every output traces.
# Prime Directive: Deterministic · Non-invent · Extract. No guessed numbers on an "honesty panel".

## TARGET FILE — ⚠ UPDATED 2026-06-13
`docs/migrate_status_panel.html` — **this is now the CANONICAL single source.** It is served by mkdocs
(local: http://127.0.0.1:8000/migrate_status_panel.html ; published: red1oon.github.io/BIMCompiler/migrate_status_panel.html)
and the MigrateComparison paper LINKS to it ("What is Done and Pending →"). Edit the four
`<section class="band green|amber|red|blue">` blocks here — they are the 🟢 done / 🟠 extraction gap /
🔴 fold gap / 🔵 not-applicable states. Page is titled "What is Done & Pending — Migration Status Map".
⚠ The old mockup `build/erp/preview_staging/migrate_status_panel.html` (port 8137) is **SUPERSEDED — do
not edit it**; it has already diverged from the canonical copy. If it publishes, `mkdocs gh-deploy` from
bim-compiler (NOT bim-ootb).

## CONTEXT (what just shipped 2026-06-13, why it touches this panel)
UI/UX lane Track C live-fixes (bim-ootb PR #290, viewer sw v651/wh_walk.js?v=5) + Track D POS
minimalist (PR #293, erp sw v668/pos_lens.js?v=7) are LIVE on GH Pages. Both are **presentation only
(newVerbs=[], no engine/fold changes)** → they do **NOT** add any fold surface. ⚠ Do NOT bump any
🟢/🟠/🔴/🔵 band COUNT on account of the UI work. Their relevance is ONLY as **delivery evidence**
(item C below). See `prompts/UI_UX_LANE.md` ✅ RESUME DONE block.

## SOURCES TO RECONCILE AGAINST (do not invent — extract from these)
- `docs/ERP_COVERAGE_MATRIX.md` — the AD-interpretability axis: **7✅ / 32🟡 / 3⛔ of 42** (3⛔ all
  n/a-in-seed; 22/476 procs dispatched, 454 named-deferred). DIFFERENT denominator from the panel.
- `prompts/HARDEN_MATRIX.md` + `docs/ERP_MODEL_ARCHETYPE.md` — the **oracle-equivalence** axis ("N of 40
  oracle-equivalent", maxDiff=0c). The panel's "43 = 16 cent-exact · 6 declarative · 21 model-layer" is
  THIS axis, not the coverage matrix. Memory has drifted across sessions (41 vs 43 vs "12 of ~40") —
  pin to the live scoreboard, state which axis each number is.
- Live Odoo 17 `search_count` + GardenWorld seed + `MIGRATE_INSTALL_TENANT.md §RESUME` — for the 🟠 row
  figures.

## WORK ITEMS (priority order)
- **A — the referenced table is MISSING (credibility hole, do FIRST).** The lead says counts "trace to
  the coverage-by-capability table below" with a dead `href="#"`, but the HTML ends after the 4 bands.
  Either EMBED the real table (extract from ERP_COVERAGE_MATRIX.md / the oracle-equiv scoreboard) or
  remove the claim. Every count on the panel must click through to its source row.
- **B — pin the headline "43 surfaces" (16/6/21) to a source + name the axis.** It's the oracle-
  equivalence tally, NOT the matrix's 7/32/3. Verify the 16/6/21 split against the live scoreboard;
  if it moved, update + cite. Make the two axes visibly distinct so a skeptic can't read them as one.
- **C — add the DELIVERY evidence (the thesis half that's empty).** All 4 bands are substrate (fold
  capability); the lead claims delivery too. Add a band/strip citing the LIVE delivery proof — zero-
  install browser POS posting to fact_acct to the cent (`W-POS-LIVE §POS-CENT maxDiff=0c`, on a phone),
  the WH×POS spatial pick loop (`W-WH-POS-PICK-LIVE`), consumer-grade surface (erp sw v668 / viewer
  v651, live now). This is where the just-shipped UI work belongs — as DELIVERY, not a fold-count bump.
  Screenshots in `~/Pictures/Screenshots/pos_panel_*.png` if a visual is wanted.
- **D — spot-check the 🟠/🔴 figures** against live Odoo 17 search_count + seed: "26 of 27 SOs", "13
  POs", "3 payments", "43 on-hand quants", master-data counts; "64 server actions", "454/476 procs".

## NON-NEGOTIABLE
NON-INVENT (every number cites its source row) · this panel has ALREADY GRADUATED into docs/mkdocs
(`docs/migrate_status_panel.html`, linked from the paper) — edits here publish, so they must be
source-traceable · propose the band/table layout before editing if it's a structural change
(feedback_propose_before_editing_docs) · publish with `mkdocs gh-deploy` from bim-compiler (NOT bim-ootb).

## STATUS (resolved 2026-06-13)
The earlier open question — "graduate to mkdocs or stay a staging mockup?" — is **DECIDED: graduated.**
The panel lives at `docs/migrate_status_panel.html` and the MigrateComparison paper links to it instead
of embedding the bands. So items A/B/C/D below are fixed-and-published in that one file.
