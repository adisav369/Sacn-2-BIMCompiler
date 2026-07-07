# DAGeVu Modeller — Direct-Manipulation Lane (select · move-on-axis · multi-select · snap)

```
# ⚠ DO NOT REMOVE
SCOPE: Give the modeller the DIRECT-MANIPULATION core every competitive modeller (BlenderBIM/Bonsai,
SketchUp, Revit) has and DAGeVu lacks: a proper picker, drag-a-selected-object-along-an-axis (transform
gizmo) with SNAP, and lasso/marquee MULTI-select. Today there is a SELECT but no MANIPULATE — you can pick a
placed object and cannot move it. User decree 2026-06-19 ("there is no proper picker, lasso, drag along axis,
snap to etc feature" + "when will we look at the UI entirely, to be competitive against other modellers").
NON-INVENT: every move is a SIGNED op-log entry (GEOM_MOVE), re-folded deterministically; reuse the existing
select seam + grid.snap + kernel_ops. No fabricated transforms — the gizmo edits the placement, the op-log records it.
WITNESS-FIRST: claim per leg before code; whitebox §-log is the proof (à la W-BONSAI-*), Playwright for wiring only.
LOG MANDATE: read the witness log before conclusions. Spec-first.
STATUS 2026-06-19 (superseded — see 2026-07-07 correction below): §0 SURVEY ✅ DONE (ranked punch-list in
§0-RESULTS). **P1 GEOM_MOVE ✅ DONE — W-BONSAI-MOVE 12/12 PASS headless; PR #423**, merged, live on main.
Designed via a 3-proposal judge panel (verdict: HYBRID fold + delta op + custom axis-handle gizmo, NOT-LEAF,
zero-edit undo) + adversarial review (no fold bug; 2 cosmetic UI risks fixed). P1 also delivered the P2
numeric-entry seam (typed step + arrow-nudge).

**⚠ CORRECTION 2026-07-07 — this STATUS line went stale for 3+ weeks and was nearly repeated as current fact
without re-checking (the exact "verify before repeating a citation" lesson from this same day's Boolean-
robustness investigation).** Grepped current `bim-ootb main` directly rather than trusting this card's own
"NEXT" line: **P3, H2, H3, and M1 are ALL already shipped too**, not still-open as this stale status implied —
- **P3 MULTI-SELECT** — real marquee-drag + shift-add, `selectedIds` a real `Set` (`modeller.html:774-823`,
  tagged `W-BONSAI-MULTISELECT` in the code itself), witness `bonsai_multiselect_live.js` exists.
- **H2 SNAP-TO-GEOMETRY** — real, tagged `W-BONSAI-SNAPGEOM` in the code (`modeller.html:1886-2146`), marker
  + commit log (`§SNAPGEOM commit kind=... marker=...`).
- **H3 ROTATE/SCALE HANDLES** — real rotate ring (Shift=free, else 15° snap) + edge-anchored scale cubes,
  `GEOM_SCALE`/`GEOM_ROTATE` folding on the correct local axes (`modeller.html:1974-2416`).
- **M1 GRID-UNDO BUG** — fixed (`bonsai_grid.js:41-42`: grid coords now re-derive from the op-log on every
  change instead of being imperatively mutated, so undo/redo/scrub revert them deterministically).

**What's NOT yet confirmed either way (not verified DONE, not verified still-open — genuinely unchecked this
pass):** H1's specific named polish item, "Z-handle visibility/usability from non-top camera angles." No
code reference to that specific fix was found, but the base gizmo clearly works (P1's witness is real and
green) — this is a narrower question than "is direct manipulation built," and the only real open item found.
**Bottom line: the direct-manipulation lane is NOT the untouched, hold-for-a-survey item it had been
carried as — nearly the whole original punch-list already shipped.** What's left is verifying H1's one
narrow sub-item and then a fresh "be the user" walk to see if anything ELSE now reads as friction 3+ weeks
and several features later — not a from-scratch survey.
Parent roadmap: prompts/BONSAI_KERNEL_RESEARCH.md §OPERABILITY v5 (this is the missing "manipulation" half of v5;
vertex/edge SNAP-TO-GEOMETRY is already noted there as v5-advanced — leg 3 here subsumes it).
```

## §0 — SURVEY FIRST (do this BEFORE any build; user decree 2026-06-19) ⭐
**Conduct a FRESH, GROUNDED SURVEY of the whole UI-FACING area — exactly as the prior ENGINE survey was done.**
The engine got a "code-grounded audit (4 Explore agents) → ~30 distinct operability gaps (6 blockers)" that
then drove v1–v4 leg-by-leg (see `BONSAI_KERNEL_RESEARCH.md §OPERABILITY`). The UI side has had NO equivalent
holistic study — it has grown feature-by-feature. This lane must be "well-researched and thought through"
FIRST, not built ad-hoc. **Deliverable of the survey session: a ranked UI punch-list (blockers → nice-to-haves),
witness-able, that drives the build legs.** The L1–L4 legs below are a STARTING hypothesis the survey may
re-order, expand, or supersede — do NOT treat them as the final plan until the survey confirms.

**SURVEY METHOD (mirror the engine audit — multi-modal fan-out, then synthesize):**
1. **Inward (code-grounded, non-invent):** audit the ACTUAL modeller UI — every interaction surface
   (`viewer/modeller.html`, `bonsai_*.js`, the pill rail, Outliner, history bar, insert panel, sketch/route/
   grid modes). What verbs exist, what's half-wired, what's missing, what's clunky. Cite file:line. (This card's
   CURRENT-STATE table is a seed, NOT the survey — go wider: cursors, modifier keys, hover affordances, undo
   coverage, keyboard map, touch/mobile, empty-states, discoverability, error feedback.)
2. **Outward (competitor benchmark, named):** study the direct-manipulation + authoring UX of real modellers —
   **BlenderBIM/Bonsai, SketchUp, Revit, FreeCAD, Speckle, Rhino/Grasshopper** — for the patterns users expect
   (gizmo conventions, snap families + visual markers, multi-select + groups, inference engines, modal vs
   modeless tools, measurement/dimensioning, view nav). Record what's table-stakes vs differentiator.
3. **"Be the user" walk:** drive DAGeVu as a real modeller on a real task (place + arrange a room) and log every
   friction point — same as the ERP user-journey lane. The lived punch-list outranks any feature checklist.
4. **Synthesize:** ONE ranked punch-list with blockers flagged, each item tied to a witness it would satisfy.
   THEN build leg-by-leg, witness-first, deploy-per-leg (the engine-audit cadence).

> May be run as a multi-agent fan-out (Explore inward × competitor-research outward × be-the-user) IF the user
> opts into orchestration — otherwise scout inline. Either way: SURVEY → ranked list → build. Not build-first.

## §0-RESULTS — RANKED UI PUNCH-LIST (survey deliverable, 2026-06-19) ⭐
Method ran as the engine audit did: **inward** = 2 Explore code-audits (modeller.html core loop + bonsai_*.js
modules/panels, every claim file:line); **outward** = competitor benchmark (BlenderBIM/Bonsai, SketchUp, Revit,
FreeCAD, Speckle, Rhino — table-stakes vs differentiator, sourced); **be-the-user** = a code-traced "place +
arrange a room" walk. All three converge on the SAME top-3. Each item carries the witness it must satisfy.

**TIER 0 — BLOCKERS (disqualifying vs any competitor; build first, in this order):**
- ✅ **P1 · MOVE A PLACED OBJECT (`GEOM_MOVE`) — DONE 2026-06-19 (PR #423, W-BONSAI-MOVE 12/12).** Delta op
  `{parent,dx,dy,dz}`; HYBRID fold (worker `kernel.translate` PATH A for solids + host `foldInsert` re-place
  PATH B for inserts); custom XYZ axis-handle gizmo (controls-off ground-plane drag + grid snap + arrow-nudge +
  typed step); NOT-LEAF re-fold; undo/delete via existing `parent=` semantics. Witness covers drag→snap→one signed
  move→placement update→pure host re-place→Outliner→deterministic replay→undo-restores + a kernel move-then-cut
  cross-check. P2 numeric-entry seam shipped alongside (typed step drives arrow-nudge).
- ~~**P1 · MOVE A PLACED OBJECT (`GEOM_MOVE`).**~~ (superseded by the ✅ above) No verb exists to reposition a committed `GEOM_INSERT`/
  `GEOM_EXTRUDE_POLY` — only delete+re-author or grid-recompose (which *scales* the axis, not moves one object).
  Op-log confirms NO `GEOM_MOVE` op-type (bonsai_oplog.js LEAF whitelist line 115; worker recognizes only
  EXTRUDE/SWEEP/OPENING, bonsai_kernel_worker.js:28-94). Fold is mechanically straightforward: a translate+yaw
  transform on the existing solid (proven by GEOM_GRID_MOVE's TRANSLATE→`kernel.translate` :159), referencing
  featureId + new placement, added to the leaf whitelist. **W-BONSAI-MOVE** (card §LEGS L1).
- **P2 · TYPED NUMERIC ENTRY during move/draw (VCB pattern).** Outward survey's #1 "highest-leverage, absence
  reads as toy" lever — every benchmarked tool has "type a number mid-action to set exact delta" (SketchUp VCB,
  Blender `G X 2.5↵`). DAGeVu has only a static elevation text box (modeller.html ins-elev), nothing during a
  move/draw. Build coupled with P1 (the gizmo drag accepts a typed delta) + sketch (type edge length). Was a
  sub-bullet of L1; survey ELEVATES it to a blocker. **W-BONSAI-NUMERIC** (NEW claim — add to §LEGS).
- **P3 · MULTI-SELECT (marquee + shift-add) & group transform.** `selectedId` is scalar (modeller.html:482);
  single-pick only; cannot select/move/delete N objects together. Table-stakes (window-vs-crossing box, shift-add,
  group move about a shared pivot). **W-BONSAI-MULTISELECT** (card §LEGS L2).

**TIER 1 — HIGH (table-stakes polish; build after blockers):**
- **H1 · Transform gizmo, color-coded X=red/Y=green/Z=blue + planar handle.** The visible affordance for P1; the
  de-facto standard widget. Card §NON-INVENT already flags "thin custom axis-handle vs THREE TransformControls —
  decide with a spike." **W-BONSAI-GIZMO** (folds into L1/L4).
- **H2 · SNAP-TO-GEOMETRY + visual markers.** Snap is grid-only (bonsai_grid.js:71-78), silent (no marker until
  after click). Add endpoint/midpoint/edge/face-center snap read from real feature bboxes (non-invent) with
  distinct markers; SketchUp-style colored inference/alignment guides are the top "feels professional" upgrade.
  **W-BONSAI-SNAPGEOM** (card §LEGS L3).
- **H3 · FREE ROTATE (not 90° discrete) + rotate/scale handles.** Insert R-key cycles 90° only (:1045/854-858);
  no fine angle, no post-placement rotate/scale UI. **W-BONSAI-ROTATE / -SCALE** (card §LEGS L4).

**TIER 2 — MEDIUM (discoverability / correctness / scale; schedule after Tier 1):**
- **M1 · GRID-UNDO CORRECTNESS BUG (flagged, orthogonal):** GEOM_GRID_MOVE mutates `grid.xs/ys` (bonsai_gridmove.js
  :51-52) but undo (soft-delete) does NOT revert the grid coords → next snap uses stale position. **W-GRIDMOVE-UNDO.**
- **M2 · SKETCH/ROUTE points are transient state** (not op-log; :441/558/1076-77) — a stray Escape loses all points.
- **M3 · HOVER pre-pick highlight** — raycast is silent; no glow/cursor before click (modeller.html:483-513).
- **M4 · CURSOR per mode/tool** — canvas cursor never changes (no grab/crosshair/move).
- **M5 · LIVE dimension readout / tape-measure tool** (table-stakes measure; none today).
- **M6 · KEYBOARD map sparse & scattered**, help panel opt-in, no per-mode legend (:1045-1078/1143-56).
- **M7 · ASSEMBLY drop not previewed** — only aabb footprint ghost, not the N child placements (modeller.html:843).
- **M8 · OUTLINER full rebuild on every op-log change** (bonsai_outliner.js:61) — jank at 100+ features.
- **M9 · TOUCH/MOBILE unhandled** (pointer events may map, but no pinch/gesture/sizing).
- **M10 · ERROR feedback only in tiny 11px status bar**, transient, overwritten by next action; no toast.

**MINIMUM BAR to "be taken seriously" (outward, ranked):** ① typed numeric entry during move/draw ② XYZ gizmo
③ core snap family with markers ④ shift-add multi-select + group transform ⑤ orbit/pan/zoom + zoom-to-cursor
⑥ tape/measure. DAGeVu today clears NONE of ①②③④ for *placed objects* — hence P1–P3 are the spine.

## WHY THIS IS THE UI-COMPETITIVE SPINE
The BOM-assembly drop is now placement-faithful (W-BOM-SPATIAL, PR #411). But a modeller is judged on its
direct-manipulation loop: **select → move/rotate on an axis with snap → multi-select → edit**. DAGeVu has the
*select* and *insert/cut/sweep/grid-drag* verbs but **cannot move a placed object** — the disqualifying gap vs
any competitor. Orientation-normalize (50/134 furniture meshes off-Z) is PARKED below it: real but lower-impact
and shakier (product dims unreliable for ~half — dim-matching alone won't fix it).

## CURRENT STATE (code-grounded audit, viewer/modeller.html, 2026-06-19) — non-invent
| Capability | State | Where |
|---|---|---|
| Pick-select (click → emissive highlight) | ✅ | `highlight()` / `Bonsai.select(fid)` raycast vs group meshes (modeller.html:479-513) |
| Delete / Cut-opening / Fit-to-selection on selection | ✅ | `b-del`, `b-cut`, `b-fit` |
| Grid snap | ⚠️ sketch-clicks + gridline-drag ONLY | `Bonsai.grid.snap(x,y)` (bonsai_grid.js:71), used in sketch pointerdown — NOT for moving objects |
| Move-Grid (drag gridline → walls recompose) | ✅ | `bonsai_gridmove.js` → signed `GEOM_GRID_MOVE` (a DIFFERENT verb — moves the grid, not an object) |
| **Drag a selected object along an axis** | ❌ NONE | only `OrbitControls` + gridline-drag; no transform gizmo |
| **Lasso / marquee MULTI-select** | ❌ NONE | single-click pick only; `selectedId` is scalar |
| **Snap-to while moving** (grid + geometry) | ❌ NONE | — |
| **Rotate / scale handles on selection** | ❌ NONE | placement carries `rotDeg-about-Z` but no handle UI |

Signed op-log facts (reuse): a placement is `GEOM_INSERT.parameters.placement{x,y,z,rotDeg}`; the chain is
append-only + verifiable; LOD upgrade already re-folds a feature in place. A MOVE must therefore be a NEW signed
op (`GEOM_MOVE` referencing the featureId, new placement) that the host fold applies — NOT an in-place edit of
the immutable insert row (mirror the GEOM_CUT/GRID_MOVE leaf-detection whitelist in oplog).

## LEGS (prioritized; witness-first — spec each claim in this card before code)
1. **L1 — AXIS-DRAG MOVE + GRID SNAP (the #1 verb).** Selected object shows a move gizmo (3 axis handles +
   planar); pointer-drag along an axis translates the feature, snapping to the grid via `Bonsai.grid.snap`;
   release commits ONE signed `GEOM_MOVE`. Controls off during grab (mirror Move-Grid). Live ghost during drag,
   nothing commits till release. Numeric nudge (arrow keys / typed delta) too.
   **W-BONSAI-MOVE (claim):** select an inserted feature → drag +1.0m on X with grid active → snaps to the
   nearest gridline → exactly ONE signed `GEOM_MOVE`, placement.x updates to the snapped coord, chain verifies,
   replay deterministic, Outliner reflects it, undo restores prior placement. §-log the before/after placement.
2. **L2 — MARQUEE / LASSO MULTI-SELECT.** Drag an empty-space box (or shift-lasso) → select all features whose
   screen-projected bbox falls inside; `selectedId` → a `selectedIds` set; highlight all; gizmo + delete + move
   act on the SET (group transform about the set centroid). Shift-click adds/removes.
   **W-BONSAI-MULTISELECT (claim):** marquee over N inserts selects exactly those N (others untouched); a group
   move commits N signed `GEOM_MOVE` (or one batched group op) all by the same delta; chain verifies; deselect clears.
3. **L3 — SNAP-TO-GEOMETRY (subsumes v5 vertex/edge snap).** While moving, snap the dragged handle to nearby
   vertices / edge-midpoints / face-centers of OTHER features (not just the grid); show a snap marker.
   **W-BONSAI-SNAPGEOM (claim):** drag feature A near feature B's corner → A's handle snaps to B's vertex within
   tol → committed placement equals B's vertex coord; snap candidates are READ from real feature bboxes, non-invent.
4. **L4 — ROTATE / SCALE HANDLES.** Rotation ring (about Z first; placement already carries rotDeg) + scale
   handles where the kernel supports it. Witness W-BONSAI-ROTATE / -SCALE.

## NON-INVENT / REUSE
- Selection: extend `highlight()`/`Bonsai.select` (scalar → set); keep the Connect 'selection' broadcast.
- Snap: `Bonsai.grid.snap` (already correct); geometry snap reads real feature bboxes from the group.
- Signed move: a `GEOM_MOVE` op-type folded host-side (like GEOM_INSERT placement), added to the oplog leaf
  whitelist; verifyChain unchanged. NO new invented geometry — only the placement transform changes.
- Gizmo: prefer a thin custom axis-handle over THREE TransformControls if the latter fights OrbitControls/Z-up;
  decide with a spike. Controls-off-during-grab pattern already proven in Move-Grid.

## RELATED
[[project_bonsai_kernel]] · prompts/BONSAI_KERNEL_RESEARCH.md §OPERABILITY v5 · prompts/CONNECT_SCENE_SPEC.md
(selection bus) · prompts/MODELLER_BOM_CATALOG_SPEC.md (placement, DONE) · the orientation-normalize leg is
PARKED in MODELLER_BOM_CATALOG_SPEC.md §ALSO QUEUED (lower priority than this lane).
```
