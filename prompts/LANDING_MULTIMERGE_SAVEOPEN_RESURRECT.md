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

---

## §SCENE_MERGE — "Open → merge into the current scene" (SPEC, 2026-07-29, NOT started)

**User's ask, their framing:** *"when we open a fresh IFC/DB in File Open it asks if we wish to merge
into the same scene/canvas"* — and the merge/variant options should live in the Open door itself,
including Open-source-IFC.

**Hard constraint from the user, same session: NO INVENTION.** Every part below already exists and
works today; this spec is wiring, not new machinery. Each claim carries its file:line — verify, don't
re-derive.

### §SM-1 What exists today (recon done — do NOT redo)
| piece | where | status |
|---|---|---|
| N buildings in ONE canvas, each with its OWN db | `city.js:11` `A.cityBuildingDbs = {}`; `:701` `= { db, libDb }` | **works — City mode uses it** |
| swap the active DB then draw | `city.js:707-708` (also `:796-797`, `:950`) `A.db = …[k].db; A.libDb = …[k].libDb` → `A.streamBuilding(name)` | **works** |
| track what's already drawn | `A.buildingCentres`, `A.buildingsRendered`, `A.savedStreams` (`streaming.js:39-60`) | **works** |
| dedup on merge | `import_db_builder.js:45` `INSERT OR IGNORE INTO elements_meta` — same GUID dropped twice collapses (proved live: CONTAINMENT dropped twice → 21,009 not 42,018) | **works** |
| merge-or-new PROMPT, already written | `archive/gallery.html:1044` `showMergeModal()` — "Merge X into Y?" / Merge / New, dropdown when >1 target, Enter=merge Esc=new | **works, just orphaned in archive/** |
| shared frame | `project_metadata.georef_offset_x/y/z`; multi-file drops already pin one frame from the first file — `import_own.js:657` `§GEOREF_SESSION frame pinned by …` | **works** |

### §SM-2 The ONLY blocker
`scene.js:663`, in `_openDbBytes`:
```js
location.assign('viewer.html?db=' + encodeURIComponent(dbUrl) + '&lib=' + … + '&ghost=1');
```
File Open **navigates the page**. The scene is destroyed and rebuilt. That is the whole reason
opening a second file resets the canvas — not a data limit, not a memory limit.

### §SM-3 What to build (wiring only)
1. In `A.openModelDb` / `_openDbBytes`: when a building is **already open**, show the prompt instead
   of navigating. Reuse `showMergeModal()` from `archive/gallery.html:1044` — port it, don't rewrite it.
2. **Replace** → today's `location.assign` path, unchanged.
3. **Merge** → do exactly what City mode does:
   - register the new DB: `A.cityBuildingDbs[newName] = { db, libDb }` (or the same shape under a
     non-city key — naming is the only new decision here)
   - `A.db = …; A.libDb = …;` then `A.streamBuilding(newName)`
   - the new building appears **alongside** the current one; `buildingsRendered` keeps both
4. **Frame:** the ALREADY-OPEN building's `project_metadata.georef_offset_*` is the pin. The incoming
   DB rebases to it — same rule `import_own.js:657` already applies to a multi-file drop, applied to
   an existing scene instead of the first file of a batch.
5. **Open source IFC in the same door:** the picker currently accepts `.db,.sqlite` only
   (`scene.js:669-671`). Widen to `.ifc`, route to the existing `importIFC`/`importMultiIFC`, then
   feed the produced DB into step 3. No new import path.

### §SM-4 Witness (name the issue, per project rule)
**W-SCENE-MERGE** — open building A, then Open→Merge building B:
- `§CENTRES` / `A.buildingCentres` has **2** keys, not 1
- `guidMap` total = A + B minus shared GUIDs (dedup is `INSERT OR IGNORE`, already proven)
- **no page navigation** — same `§HIST_SESSION` id before and after (today's reload mints a new one)
- both buildings' element counts non-zero in one `§CONTRACT_CHECK`

### §SM-5 Named risks (flagged, not solved)
- **Memory.** Two 485MB DBs resident = ~1GB of sql.js heap in one tab. City mode's DBs are much
  smaller. Needs a real measurement before promising N-way merge, not an assumption.
- **Variants vs federation are different things.** Today's `§VERSION_MERGE` (`import_own.js:697`) is
  VERSIONING — `_rec.versions.push()` then `_rec.metaDb = …` OVERWRITES. Do not build scene-merge on
  that path or it will replace instead of accumulate. A variant SWITCHER (show v1 or v2) is a third,
  separate feature — do not fold it in without a decision.
- **Cross-check §KUL008_CACHE** in `prompts/IFC_LARGE_PRIVATE_STRESS_TEST.md` before testing any
  edit to a precached `viewer/*.js` — bump `sw.js CACHE_VERSION` + the file's `?v=`, reload twice,
  or the change is invisible and looks like a code failure.
