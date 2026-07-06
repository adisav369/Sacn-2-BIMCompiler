# SPEC — Resurrect `feat/landing-multimerge-viewer-saveopen` onto current main

```
# ⚠ DO NOT REMOVE
SCOPE: reconcile a REAL, stale, unmerged bim-ootb branch onto current main — NOT new-feature work. Read the log
after every run. Full origin story / why this branch matters: prompts/GRID_PREDRAG_PREVIEW_SAVE_COMPLETEIT.md
§B "Versioning". This spec is Watchdog-tracked (see closing note) — every DONE claim needs a § log line.
```

## WHY (don't re-derive — confirmed 2026-07-05)
`origin/feat/landing-multimerge-viewer-saveopen`, last touched **2026-06-19** (1 substantive commit `525eb18`,
253 commits behind current `main` but only 6 files touched), contains real, tested work that was never landed —
apparently dropped amid a later landing-page redesign, not deliberately rejected (its own code already follows
today's no-card convention — see the constraint below). It contains:
1. `import_own.js` `importMultiIFC()` — drop 2+ IFC files → merges into ONE building record (concatenated
   elements/geometries/transforms) → auto-opens the Viewer.
2. Real **"Save Building"/"Open Building" pills** (`Ctrl+S`/`Ctrl+O`) in `viewer/panels.js`/`pill_builder.js`,
   wired to `A.saveModelDb()`/`A.openModelDb()` — native OS Save-As/Open dialog for a real `.db` file.
3. `viewer/tests/witness_save_fold.js` (W-SAVE-FOLD) — proves `A._exportBuildingDb` folds a SPLIT (meta+geo)
   building into ONE monolith `.db`, zero geometry loss on round-trip.
4. A `versions[]` array + `latestVersion` index already in the landing page's per-project record shape.
5. `index2.html` (4-line diff) + `viewer/scene.js` (110-line diff, NOT yet reviewed in this recon — read it
   before reconciling, don't assume it's benign).

## ⚠ HARD CONSTRAINT — do not reintroduce a card/list UI
User confirmed 2026-07-05: an earlier landing-page "card" pattern (browsable list of past-imported buildings)
was deliberately dropped for a **security-perception concern** — showing a list of the user's own prior imports
felt like exposing private data. Current/correct UX: auto-launch straight into the Viewer, let the user Save
via the native OS dialog. This branch's own `importMultiIFC()` comment already says *"NO merge modal, NO
card"* — it already complies. **Do not let any card/list surface reappear while reconciling.** If `viewer/scene.js`'s
110-line diff or anything else in the branch shows card/list UI, strip it before landing — flag it as a finding,
don't silently keep it because "it was already there."

## STEPS
1. Read `viewer/scene.js`'s diff in full (110 lines, unreviewed) before touching anything — confirm no card/list
   surface, confirm it doesn't fight anything already shipped on main in the 253 commits since fork.
2. Work in a fresh `/tmp/wt-*` worktree off `origin/main` (never the shared `~/bim-ootb` checkout — hook-blocked).
   `git fetch && git checkout -b lane/landing-multimerge-resurrect origin/main`, then cherry-pick or manually
   port `525eb18`'s diff (do NOT merge the whole stale branch — its history is 253 commits removed from main;
   port the one substantive commit's changes onto fresh main instead).
3. Re-run `viewer/tests/witness_save_fold.js` against current main's actual DB schema — 253 commits of drift
   could have changed geometry/transform table shapes since this test was written. Fix if it fails; that's a
   real regression check, not a formality.
4. Confirm the fate of the `redpill`/"Doc Mode" pill: this commit's diff REPLACES it wholesale with Save/Open
   in the `_actions`/`_defaultOrder` arrays. Check whether Doc Mode independently evolved on main since
   2026-06-19 — if so, reconcile (don't blindly delete something that grew new dependents), don't just take the
   branch's version verbatim.
5. Confirm the `pill_builder.js` "outside-tap-to-close removed, only ⋯ press closes" decision (this commit's
   own comment: *"User decree: outside-tap-to-close was too easy to trigger by accident"*) still matches
   current pill UX — check for a conflicting decision made independently on main in the interim.
6. Live-verify (headless or real browser): drop 2+ IFC files on the landing hub → merges → Viewer opens with
   the combined building; `Ctrl+S` opens a real Save-As dialog; `Ctrl+O` opens a real file picker and correctly
   replaces the scene. Log every assertion with a `§` tag — this is exactly the class of claim the Watchdog
   Protocol requires evidence for.
7. PR to main. No card. No modal. No list. Just auto-launch + native Save/Open, as today's UX already commits to.

## DONE WHEN
1. `525eb18`'s content is live on current `main` (fresh commit, not a stale-history merge).
2. `witness_save_fold.js` green against current main.
3. Multimerge + Save + Open all live-verified with `§`-tagged evidence, zero card/list UI anywhere.
4. Doc Mode pill's fate is a deliberate reconciled decision, not an accidental silent overwrite either way.

## WATCHDOG NOTE (for whichever session closes this)
This item is tracked from `prompts/FRONTEND_LANE_MASTER.md §NEW BACKLOG`. Per the project's Watchdog Protocol:
the closing session's `# DONE` appendix needs a `§` log line for EVERY claim above — "verified live" without a
`§` line is not done, it's an assertion. Flag anything unproven rather than accepting it on trust.
