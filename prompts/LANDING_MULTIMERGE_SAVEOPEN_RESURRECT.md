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
| merge-or-new PROMPT, already written | `archive/gallery.html:1045` `showMergeModal()` — "Merge X into Y?" / Merge / New, dropdown when >1 target, Enter=merge Esc=new; called at `:1369` | **works, just orphaned in archive/** |
| shared frame | `project_metadata.georef_offset_x/y/z`; multi-file drops already pin one frame from the first file — `import.js:308` `§GEOREF_SESSION frame pinned by …` | **works** |

> **⚠ CITATIONS RE-VERIFIED 2026-07-30 against `origin/main` — the original spec's were stale.**
> There is **no `viewer/import_own.js`** in this repo; the file is **`viewer/import.js`**
> (`importMultiIFC` at `:267`, `_parseOneIFC` at `:388`). Corrected throughout this section. Every
> `city.js` and `import_db_builder.js` line above was checked and is correct as written.

### §SM-2 The ONLY blocker
`scene.js:758`, inside `A._openDbBytes` (declared `scene.js:747`) — **line numbers verified against
`origin/main` 2026-07-30; they drift every week, so GREP THE SYMBOL, never trust the number**:
```js
location.assign('viewer.html?db=' + encodeURIComponent(dbUrl) + '&lib=' + … + '&ghost=1');
```
File Open **navigates the page**. The scene is destroyed and rebuilt. That is the whole reason
opening a second file resets the canvas — not a data limit, not a memory limit.

### §SM-3 What to build (wiring only)
1. In `A._openDbBytes` (`scene.js:747`): when a building is **already open**, show the prompt instead
   of navigating. Reuse `showMergeModal()` from `archive/gallery.html:1045` — port it, don't rewrite it.
   Note there are **two** call sites to cover — `scene.js:770` (drag-drop) and `:779` (file picker).
2. **Replace** → today's `location.assign` path, unchanged.
3. **Merge** → do exactly what City mode does:
   - register the new DB: `A.cityBuildingDbs[newName] = { db, libDb }` (or the same shape under a
     non-city key — naming is the only new decision here)
   - `A.db = …; A.libDb = …;` then `A.streamBuilding(newName)`
   - the new building appears **alongside** the current one; `buildingsRendered` keeps both
4. **Frame:** the ALREADY-OPEN building's `project_metadata.georef_offset_*` is the pin. The incoming
   DB rebases to it — the same rule `import.js:299-310` already applies to a multi-file drop
   (`sessionGeorefOffset`, pinned by the first file that reports a non-zero offset), applied to an
   existing scene instead of the first file of a batch.
5. **Open source IFC in the same door:** the picker currently accepts `.db,.sqlite` only
   (`scene.js:774` `input.accept = '.db,.sqlite'`). Widen to `.ifc`, route to the existing
   `A.importMultiIFC` (`import.js:267`), then feed the produced DB into step 3. No new import path.

### §SM-4 Witness (name the issue, per project rule)
**W-SCENE-MERGE** — open building A, then Open→Merge building B:
- `§CENTRES` / `A.buildingCentres` has **2** keys, not 1
- `guidMap` total = A + B minus shared GUIDs (dedup is `INSERT OR IGNORE`, already proven)
- **no page navigation** — same `§HIST_SESSION` id before and after (today's reload mints a new one)
- both buildings' element counts non-zero in one `§CONTRACT_CHECK`

### §SM-5 Named risks (flagged, not solved)
- **Memory.** Two 485MB DBs resident = ~1GB of sql.js heap in one tab. City mode's DBs are much
  smaller. Needs a real measurement before promising N-way merge, not an assumption.
- **Variants vs federation are different things.** ⚠ **`§VERSION_MERGE` no longer exists** — grepped
  2026-07-30 across `viewer/`, `common/`, `archive/`: zero hits for `VERSION_MERGE` or
  `_rec.versions.push`. It lived in the gallery/landing code that is now in `archive/`. The *warning*
  still stands as doctrine — do not build scene-merge on a versioning path that OVERWRITES
  (`_rec.metaDb = …`) instead of accumulating — but **do not go looking for that code, it is gone.**
  A variant SWITCHER (show v1 or v2) remains a third, separate feature; don't fold it in without a
  decision.
- **The 4 GB wasm ceiling caps "Open source IFC" (step 5), and it is measured, not theoretical.**
  See `IFC_LARGE_PRIVATE_STRESS_TEST.md` §KUL009: a 2,045 MB IFC consumes ~3.4 GB of the wasm32
  4 GB budget on *parse alone*, and geometry building dies at element #3,956 of 66,214. Each file
  does get a fresh Worker (`import.js:391` `new Worker(...)` + `terminate()` on done), so merging N
  files does not compound wasm memory — but any single source file near ~1 GB will silently import
  partial. Step 5 should surface that, not hide it behind a merge prompt.
- **Cross-check §KUL008_CACHE** in `prompts/IFC_LARGE_PRIVATE_STRESS_TEST.md` before testing any
  edit to a precached `viewer/*.js` — bump `sw.js CACHE_VERSION` + the file's `?v=`, reload twice,
  or the change is invisible and looks like a code failure.

---

## §SM-6 SESSION BRIEF — build §SCENE_MERGE (added 2026-07-30, ready to assign)
```
# ⚠ DO NOT REMOVE
SCOPE: implement §SM-3 only — a merge-or-replace prompt on File Open, so a second IFC/DB joins the
CURRENT canvas instead of navigating the page away. Wiring, not new machinery. Read the log after
every run. Every DONE claim needs a § log line. Do NOT touch §SM-1..§SM-5 conclusions — they are
verified. Do NOT widen into version/variant switching (§SM-5).
```

**All file:line below re-verified against `origin/main` on 2026-07-30. Trust them; don't re-derive.**

### The driving use case (real, from the user, not hypothetical)
KUL070 is delivered as multiple IFCs and **the ARCH/building-envelope package arrives later, separately.**
Today the user must re-drop every file together to see them as one building; opening the ARCH addition
into the loaded MEP model **replaces** it. That is the whole point of this work.

Concrete proof of why it matters, measured 2026-07-30 on `KUL070-...-OVERALL_complete.db`:
`doors=0  stairs=0  IfcSpace=0  walls=8  slabs=1` → `buildTour()` returns 0 actions → **Fly Tour (L)
never starts and the timeline scrubber never appears** (`tour.js:364` `A._scrubShow()` is only reached
from `_startFlyTour`). `ensureRooms` cannot rescue it — `navigate_find.js:1080` compiles rooms from
walls/doors and "refuses honestly (roomsWritten=0) if the building lacks them — never invents rooms."
**Merging the ARCH package in is what makes Fly/rooms/scrubber work on this building at all.**

### What already works — do not rebuild it
| capability | where | status |
|---|---|---|
| N IFCs → ONE building DB | `import.js:267` `A.importMultiIFC` | works, but only within a SINGLE drop |
| fresh Worker per file, terminated after | `import.js:391` + `worker.terminate()` | works — merging N files does NOT compound wasm memory |
| one shared frame across the drop | `import.js:299-310` `sessionGeorefOffset` → `§GEOREF_SESSION` | works |
| GUID dedup on insert | `import_db_builder.js:45` `INSERT OR IGNORE INTO elements_meta` | works |
| `building` + `project_metadata` written | `import_db_builder.js:28,38,48` (browser) and, as of 2026-07-30, `extractIFCtoDB.py` (CLI) | works both paths |
| N DBs in ONE canvas | `city.js:11` `A.cityBuildingDbs`, `:701`, `:707-708` → `A.streamBuilding()` | works — City mode uses it |
| the merge modal itself | `archive/gallery.html:1045` `showMergeModal()`, called `:1369` | written, orphaned in `archive/` — PORT it |

### The blocker, exactly
`scene.js:747` `A._openDbBytes` → `scene.js:758`
`location.assign('viewer.html?db=' + … + '&ghost=1')` — a full page navigation. Two call sites feed it:
`scene.js:770` (drag-drop) and `scene.js:779` (file picker, `input.accept = '.db,.sqlite'`).

> ⚠ **GREP THE SYMBOL, NOT THE LINE.** Every number in this file was re-verified against `origin/main`
> on 2026-07-30 — and two earlier passes had them wrong precisely because they were read from a stale
> local checkout (`~/bim-ootb` was 113 commits behind, and a `/tmp/wt-*` worktree 61 behind). `main`
> moves daily. Always `git show origin/main:viewer/scene.js | grep -n '_openDbBytes'` first.

### Build order
1. In `A._openDbBytes`, when a building is already loaded, show the ported `showMergeModal()` instead
   of navigating. Cover BOTH call sites (735 and 744).
2. **Replace** → today's `location.assign` path, byte-for-byte unchanged.
3. **Merge** → do what City mode does: `A.cityBuildingDbs[newName] = { db, libDb }`, then
   `A.db = …; A.libDb = …;` then `A.streamBuilding(newName)`. Naming the non-city key is the only
   genuinely new decision.
4. Frame: the ALREADY-OPEN building's `project_metadata.georef_offset_*` is the pin; the incoming DB
   rebases to it — the same rule `import.js:299-310` applies within a drop, applied to a live scene.
5. Widen `scene.js:774` `input.accept` to include `.ifc` and route to `A.importMultiIFC`, then feed the
   produced DB into step 3. No new import path.

### Witness — W-SCENE-MERGE (name the issue, per project rule)
Open A, then Open→Merge B:
- `A.buildingCentres` has **2** keys, not 1
- element total = A + B minus shared GUIDs (`INSERT OR IGNORE`, already proven)
- **no page navigation** — same `§HIST_SESSION` id before and after (today's reload mints a new one)
- both buildings non-zero in one `§CONTRACT_CHECK`
- **the real one for this use case:** merge the ARCH package into KUL070-OVERALL and assert
  `doors>0 AND IfcSpace>0` in the merged scene, then that `buildTour()` returns ≥1 action and
  `§SCRUB_PREPARE` appears on L. That is the user-visible payoff; a centres count alone doesn't prove it.

### Landmines specific to this task — all measured, none speculative
- **`import://` cannot be fetched.** An opened local file gets a `import://<name>/v0` DB_URL, so any
  code deriving a URL from it fails: witnessed `Fetch API cannot load import://…/patches/v0.sql —
  URL scheme "import" is not supported` → `§PATCH_APPLY_FAIL`. Harmless for the patch (opened files
  have no curated patch) but **do not build the merge on any DB_URL-derived fetch.**
- **Memory is the real ceiling, and it is per-DB not per-wasm.** KUL070's DB alone is 311 MB resident
  in sql.js. Two of these in one tab is ~620 MB before geometry. City-mode DBs are far smaller.
  **Measure before promising N-way merge** — and note `§QUOTA available=11816MB used=1576MB` is
  IndexedDB, not heap.
- **Every edit re-exports the WHOLE DB.** `kernel_ops.js:125` debounces 2 s then
  `db.export()` → IDB put. At 311 MB per building that is already heavy; a merged 620 MB scene makes
  it heavier. Not a blocker for this task, but do not add a per-merge persist on top of it.
- **`§VERSION_MERGE` no longer exists** (grepped 2026-07-30: zero hits in `viewer/`, `common/`,
  `archive/`). Its doctrine still stands — do not build on a path that OVERWRITES `_rec.metaDb`
  instead of accumulating — but the code is gone, don't hunt for it.
- **Precache: bump `sw.js CACHE_VERSION` + the file's own `?v=`, then reload TWICE**, or the edit is
  invisible and looks like a code failure. Currently `v884`. See
  `IFC_LARGE_PRIVATE_STRESS_TEST.md §KUL008_CACHE`.
- **Envelope-dependent UI will change behaviour once ARCH merges in.** KUL070's envelope is 9 of
  87,333 (0.01%), so `§BBOX_GHOST_ALL` currently fires for Alt+Z. After an ARCH merge it won't — that
  is correct, not a regression. Don't "fix" it.
