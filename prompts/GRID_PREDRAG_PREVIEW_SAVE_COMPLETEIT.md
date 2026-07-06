# MASTER DESIGN DIALOGUE (reference only) — Grid pre-drag Green/Orange preview + Save/CompleteIt + UBBL triage

```
# ⚠ DO NOT REMOVE
STATUS: pure design-dialogue record, 2026-07-05. NOTHING in this doc is built. This is the direct answer to
RESUME_SESSION_2026-07-04_GATE_BACKPROP.md §OPEN item 2 ("accept/ignore UI for ORANGE suggestions") — resolved
NOT by building an accept button, but by moving the decision point BEFORE the drag commits (§A). Don't
re-litigate item 2 as still-open.

**SPLIT INTO 5 SESSION-ASSIGNABLE SPECS (2026-07-05) — assign work from THOSE, not this file:**
1. `prompts/LANDING_MULTIMERGE_SAVEOPEN_RESURRECT.md` — land the stale 2026-06-19 multimerge/Save-Open branch.
2. `prompts/LANDING_VERSION_MERGE_PROMPT.md` — name-similarity merge popup (depends on #1).
3. `prompts/GRID_PREDRAG_GREENORANGE_PREVIEW.md` — this doc's §A, standalone, independent of #4.
4. `prompts/MODELLER_SAVE_COMPLETEIT.md` — this doc's §B, depends on #1's fold logic being landed.
5. `prompts/UBBL_RULES_RECON.md` — recon-only, this doc's §C, no build until recon lands.
This file stays as the FULL reasoning trail (why each decision was made) — read it when a split spec references
"per the design dialogue," but track actual work status in the 5 files above + `FRONTEND_LANE_MASTER.md
§NEW BACKLOG`.
```

## §A — Pre-drag / live-drag preview: default-orange, opt-out-green

**Semantics (user's exact words, don't drift from this):**
- **Orange = default.** The whole wall being dragged and everything inside its span follows the drag
  proportionately. Openings (doors/windows) shift position to stay well-spaced along the stretch but their OWN
  dimensions stay intact — this is not new behavior to build, it's already shipped: `sdg_cascade.js:37`
  `stretchRide()` already keeps a hosted opening's real width/height fixed while repositioning it; the
  `door-crush` RED (`DOOR_WIDTH_CRUSH_GATE.md`) already exists purely to catch the edge case where the host
  shrinks past the opening's own fixed size (a real impossibility), not a distortion of the opening itself.
- **Green = "I am good, not following your drag" — opt-out, non-default.** User ctrl+clicks an orange element
  to flip it green (excluded from this drag's movement); ctrl+click again toggles it back to orange. Bidirectional,
  live, per-element, scoped to the current drag session only.
- **Future, explicitly deferred:** a config/preference for which polarity is DEFAULT per user (default-green
  vs default-orange). Not built now — hardcode default-orange.

**Why this answers §OPEN item 2:** `abuts-realign` ORANGE (`SDG_BACKPROP_ABUTS_REALIGN.md`) only fires when the
engine's proportional-follow math pulls a real touching pair apart UNEXPECTEDLY. If the user pre-declares intent
(green vs orange) before the drag commits, the mismatch that generates that suggestion mostly doesn't occur —
replacing "detect the surprise after, offer a fix" with "no surprise, because the user chose." This does **NOT**
retire generic `clearance` ORANGE (proximity between elements with no pre-existing `abuts` edge) or RED clash —
those still need §B's Save-time validation as the real safety net.

**Existing building blocks (verified in `~/bim-ootb`, 2026-07-05 — cite before reusing, don't re-derive):**
- `modeller/grid_kinematics.js:70-101` `attachGridToElements()` + `_governed` map — classification (governed
  ATTACH/EDGE_LEFT/EDGE_RIGHT/SPAN vs interior/bay-proportional) is delta-independent, so it CAN run before a
  drag commits, not just during/after.
- `modeller/modeller.html:1495-1512` `gmTint(commands)` — a live tint preview ALREADY ships during drag (blue
  = TRANSLATE, orange = SCALE), fed by `bonsai_gridmove.js:35-44` `computeCommands()`. **Known gap:** `stretchRide`
  (the hosted-opening override) is applied only in `commit()` (`bonsai_gridmove.js:68-74`), NOT in this live
  preview — so today a door/window can show the WRONG tint mid-drag. Fix this as part of building §A, before
  adding the new green state, or the opening's live color will lie.
- `modeller/modeller.html:752` `_emis(mesh, hex)` — generic highlight, takes any hex. Adding GREEN is direct reuse.

**Net-new work for §A:**
1. Fix the `stretchRide`-in-live-preview gap above.
2. Add a GREEN tint state to the live preview (currently only blue/orange exist).
3. A per-drag-session override set (element id → excluded boolean), seeded empty (all orange by default),
   toggled by ctrl+click, consumed by `computeCommands()`/`gmTint()` to skip movement for excluded elements.
4. `commitGridMove()` must honor the same override set at commit time — the preview and the actual commit must
   agree, or the preview lies.

## §B — Save → CompleteIt (validated snapshot promotion)

**Two-tier persistence — do not conflate these:**
- **IndexedDB** — the granular signed op-log (`kernel_ops`), unconditional, per-gesture, already shipped. Every
  `GEOM_MOVE`/`GEOM_INSERT`/delete lands here immediately regardless of validation state. This is undo/history —
  UNTOUCHED by anything in this spec.
- **Physical DB (on disk)** — a NEW, gated artifact. Written only when the user invokes Save, only if validation
  passes. This is what "Save" actually persists — distinct from IndexedDB, which already has everything.

**Save's behavior, per the user's spec:**
1. Run clash analysis (existing `sdg_gate.js` RED/ORANGE) + UBBL analysis (§C, NOT YET BUILT) over the current
   state.
2. **Auto-heal pass first** (agreed guardrails from the design dialogue, don't drop these when building):
   - For every ORANGE with a verified `proposedDelta` (currently: `abuts-realign`, `clearance`) — auto-commit it
     as a signed `GEOM_MOVE`, then RE-VERIFY (re-run `faceGap`/the relevant check) before accepting the fix —
     mirrors the existing "never trust a propagated Δ without re-measuring" discipline from the abuts-realign
     witness. Do not fire-and-forget.
   - **One-hop only, do not chase chains.** If auto-fixing item A opens a NEW issue at neighbor B, that is a
     new item to surface, not something to recursively auto-fix — matches the doctrine's existing one-hop
     constraint (`SPATIAL_DEPENDENCY_GRAPH.md` Phase 3: cycles have no unique fixed point).
   - RED (clash, door-out, door-crush) and any UBBL infringement are NEVER auto-resolved — these require a human
     decision (delete / resize / redesign), not a mechanical correction.
3. After auto-heal, re-evaluate. If anything RED (or unresolved UBBL) remains → signal **'Error - <what and
   where>'**, block the physical-DB write, leave IndexedDB history exactly as-is (user keeps working, drags more,
   Saves again later). If clean → signal **'Clean, saving'** and write the physical-DB snapshot.

**Naming — "Save," not "Process" or "Check":** settled in dialogue. "Process"/"Check" were considered and
rejected: "Check" undersells the auto-heal mutation (it's not read-only), and once the action actually creates
a NEW artifact (the physical-DB snapshot) that didn't exist before, "Save" is the more literal, ERP-consistent
name — not because it borrows ERP's document-completion weight, but because it genuinely IS a save (new
persisted artifact), which "Process"/"Check" are not.

**DocAction/CompleteIt reuse — VERIFIED, this is a real, already-built ERP-side pattern, not aspirational:**
`erp/ad_docfsm.js` already ports iDempiere's `DocumentEngine.getValidActions`/`processIt` with a real
DR→IP→CO→AP DocStatus lifecycle (`ad_docfsm.js:42` — CO status transition is literally commented `// completeIt`).
Given the project's own ONE-iDempiere-base doctrine (`project_erp_one_base_doctrine`), Save/CompleteIt on the
BIM side should be evaluated for literal reuse of (or contract-parity with) `ad_docfsm.js`'s DocAction state
machine, rather than inventing a parallel DR/IP/CO/AP-shaped thing from scratch. **Open design question, not
answered here:** does Modeller's Save actually CALL into `ad_docfsm.js`, or just mirror its status-transition
contract for consistency? Needs a decision before implementation, not assumed either way.

**Versioning (DB.v1 → DB.v2 → ...) — FOUND, but on an UNMERGED, stale branch — resurrect, don't reinvent:**
Correction to an earlier pass in this same spec doc (first grep missed it — "variant" isn't the string used in
code, "multimerge"/"save/open" are). Real, dated, tested work exists on
`origin/feat/landing-multimerge-viewer-saveopen` (last touched 2026-06-19 — matches the user's "2+ weeks ago,
when Matrix art was introduced" timeline exactly). Branch is 253 commits behind current `main` but only carries
**1 substantive commit** (`525eb18`, "restore IFC multimerge + Save/Open pills") over 6 files — a tractable
reconcile, not a rewrite. What it actually contains:
1. **`import_own.js` `importMultiIFC()`** — drop 2+ IFC files on the landing hub → parses each, concatenates
   elements/geometries/transforms into ONE merged building record, auto-opens the viewer. This is a
   **cross-file MERGE**, not a same-building version-history feature — different from what "variant" implied.
2. **A real `versions[]` array + `latestVersion` index already in the landing page's own project record shape**
   (`newRecord = { meta, versions: [{key, importDate, db}], latestVersion: 0 }`) — this IS the closest existing
   precedent for DB.v1→v2, but it's a landing-page IndexedDB catalog bookkeeping structure, not physical
   multi-file snapshots.
3. **`viewer/panels.js`/`pill_builder.js` — real "Save Building"/"Open Building" pills**, `Ctrl+S`/`Ctrl+O`,
   wired to `A.saveModelDb()`/`A.openModelDb()`: a **native OS Save-As/Open dialog** exporting/importing a real
   `.db` file to/from disk — this is Viewer-side (not Modeller), and is the closest existing "write a physical
   DB file" mechanism in the whole codebase.
4. **`viewer/tests/witness_save_fold.js` (W-SAVE-FOLD)** — proves `A._exportBuildingDb` folds a SPLIT
   (meta db + geo db) building into ONE monolith `.db` with zero geometry loss on round-trip. This is the actual
   fold logic behind `saveModelDb` — directly reusable as the "write the validated snapshot" step in §B, rather
   than writing a new exporter from scratch.

**Action before building §B's physical-DB write:** reconcile this branch onto current `main` (fetch + merge,
per the project's own concurrent-branch doctrine — it's stale, not dead) and evaluate literal reuse of
`saveModelDb`/`_exportBuildingDb`'s fold logic + the `versions[]`/`latestVersion` shape, rather than building a
parallel DB.v1/v2 mechanism. Do not silently let this branch keep rotting — it predates and substantially
overlaps this spec's Save/CompleteIt work.

## §C — UBBL / regulatory rules — separate, later, systematic task (NOT this build)

Confirmed 2026-07-05: zero real UBBL (Uniform Building By-Laws) references anywhere in either repo (grep hit
was a false positive on "bubble"). This is a **net-new rule engine**, not a wire-in — likely larger scope than
the existing geometry-only gate (setback, plot ratio, egress width, corridor/stair minimums, etc.), deserves its
own spec pass, not a fifth check bolted onto `sdg_gate.js`.

**Two candidate triage UX ideas, captured for whenever that work starts — NOT decided between, same open-choice
shape as the abuts-realign accept/ignore question:**
1. **Pop-up blurb** anchored to the clash/infringement in the 3D view, citing the specific rule violated (e.g.
   "UBBL §xx.x — minimum corridor width 1.2m, this is 0.9m").
2. **Dedicated panel** listing all open infringements; selecting one shows/zooms the camera to the offending
   element (mirrors the "dedicated panel" option already floated for gate ORANGE items in general).

## Open questions carried forward (not this session's to resolve alone)
1. Does Save/CompleteIt literally call `ad_docfsm.js`, or just mirror its contract? (§B)
2. What concrete "variant" mechanism did the user mean — confirm before building DB.v1/v2 on an assumed
   precedent that wasn't found in code. (§B)
3. UBBL rule source/format — not scoped at all yet; needs its own recon pass before a spec can be written for §C.
4. Pop-up-blurb vs panel (or both) for UBBL triage — pick when §C is actually scheduled.
