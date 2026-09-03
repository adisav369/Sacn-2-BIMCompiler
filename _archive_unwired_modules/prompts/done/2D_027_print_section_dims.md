# 2D_027: Grid Dim Accuracy + Section Views + A3 Print + Smart Consolidation

# ⚠ DO NOT REMOVE — Read the log after every run. Scope: deploy/dev/ ONLY.

## Session Isolation — READ BEFORE STARTING

**Category:** Browser JS — zero Java, zero live/ edits, zero new files unless named here.

**Prefix:** `S2D27-`

**Files OWNED by this session (create or edit):**
- `deploy/dev/grid_dims.js` — fix snapGrids distortion, fix wall-fallback accuracy
- `deploy/dev/grid_dim_chains.js` — fix dim label precision, add storey label
- `deploy/dev/print_sheet.js` — auto orientation + corporate.json header + editable preview
- `deploy/dev/section_cut.js` — expose saved cut list API (read only, no cut logic change)
- `deploy/dev/grid_overlay.js` — wire section cut view names into view selector
- `deploy/dev/grid_scissors.js` — smart consolidation UI (merge preview only, no auto-override)
- `deploy/dev/grid_door_arcs.js` — stair symbol + opening tag for GF + L1

**Files READ-ONLY (do not edit):**
- `deploy/dev/grid_views.js` — VIEW_DEFS source of truth
- `deploy/dev/tools.js` — section slider logic (clipping planes)
- `deploy/dev/main.js` — app entry, ifc2three() available
- `deploy/live/*` — PRODUCTION, never touch

**New files:** none — extend existing JS only.

**Pre-flight citation:** Every changed block must start with:
```
// Implementing 2D_027 §X.Y — Witness: W-2D27
```

---

## §1  Issue: Grid Dimension Markings at Wrong Values

### §1.1  Root Cause Analysis

`grid_dims.js → snapGrids()` snaps bay widths cumulatively to nearest 300mm module
anchored at the first grid line. Error accumulates: a 3,650mm bay becomes 3,600mm
and the next bay shifts 50mm further from its actual wall. By bay 5–6 a 300mm
drift is possible.

`grid_dim_chains.js → build()` labels dimensions from the snapped positions.
Labels are therefore **wrong against the real building** whenever the building
doesn't use exact 300mm modules.

Wall-fallback in `detectGrids()` uses `center_x / center_y` (centroid of the full
wall element). A wall running in X has its centroid at the wall midpoint — not at
a grid face. This clusters meaningless points and produces false grid lines.

### §1.2  Fix — grid_dims.js

**Rule: keep raw positions for dimensions; snap only for clean visual alignment.**

1. Before calling `snapGrids`, save the raw positions as `rawPosition` on each line entry.
2. In `snapGrids`: retain `rawPosition` through the snap — only `position` (display) gets snapped.
3. In `generateDimensions`: compute `distance` from **rawPosition** differences, not snapped position.
   Label = `formatDim(rawDist)`. Grid line is drawn at snapped position but labelled with actual.
4. Wall fallback: query `MIN(t.center_x), MAX(t.center_x)` grouped by wall orientation (not center).
   Prefer wall edge positions: for walls long in Y direction (depth > width), their grid contribution
   is their `center_x ± half_width`. For the grid fallback, cluster wall **face positions**:
   ```sql
   SELECT m.guid, t.center_x - t.size_x/2 AS x_lo, t.center_x + t.size_x/2 AS x_hi,
          t.center_y - t.size_y/2 AS y_lo, t.center_y + t.size_y/2 AS y_hi
   FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid
   WHERE m.ifc_class IN ('IfcWall','IfcWallStandardCase')
   ```
   Cluster both `x_lo` and `x_hi` as X candidates; both `y_lo` and `y_hi` as Y candidates.
   This yields true grid faces regardless of wall thickness.

5. Log tags to add:
   - `§GD_SNAP_DELTA axis=X idx=N raw=RRR snapped=SSS delta=DDD`  (per-line, only if delta > 1mm)
   - `§GD_DIM_RAW dist_raw=RRRRR dist_snapped=SSSSS label=LLLLL`  (per dimension)

### §1.3  Fix — grid_dim_chains.js

`build()` already computes `(distX * 1000).toFixed(0)` from `xLines[i+1].position − xLines[i].position`.
Change to read `rawPosition` if present, fallback to `position`:

```js
var rawA = xLines[i].rawPosition !== undefined ? xLines[i].rawPosition : xLines[i].position;
var rawB = xLines[i+1].rawPosition !== undefined ? xLines[i+1].rawPosition : xLines[i+1].position;
var distX = Math.abs(rawB - rawA);
var label = (distX * 1000).toFixed(0);
```

Same pattern for Y-axis and overall dimension.

Add storey label to the overall dimension (the tier-2 chain):
- near-side overall: suffix storey abbrev e.g. `"12 450  GF"` where `GF` comes from current grid mode.

---

## §2  Section Cuts as Named Views in Save/View List

### §2.1  Requirement

When the user has performed section cuts (the 3-axis section slider in `tools.js`),
those cut positions should be **saveable as named views** alongside the orthographic
presets (floor, floor1, front, rear, left, right, roof).

Target: view selector (the panel in `grid_overlay.js`) gains a "Saved Cuts" group:
`SectionCut1`, `SectionCut2`, … derived from stored cut snapshots.

### §2.2  Data Model

In `section_cut.js` expose a new API:
```js
SectionCut.savedCuts          // [] array — list of { name, axis, constant, label }
SectionCut.saveCut(axis, constant)  // push a new cut, auto-name SectionCutN
SectionCut.removeCut(name)          // remove by name
SectionCut.restoreCut(APP, name)    // re-apply axis + constant to APP.sectionPlane
```

`savedCuts` is persisted to `localStorage` keyed on `APP.activeBuilding + ':sectionCuts'`.

### §2.3  Wiring in tools.js (save button)

When `A.sectionOn === true`, the existing screenshot/save button should offer:
**"Save this cut"** — calls `SectionCut.saveCut(A.sectionAxis, A.sectionPlane.constant)`.
A toast: `"Saved as SectionCut3"`.

Do NOT auto-save on slider move — only on explicit Save button action.

### §2.4  Wiring in grid_overlay.js (view panel)

After the standard VIEW_DEFS buttons, add a `<div class="cut-views-group">` that lists
`SectionCut.savedCuts` buttons. Clicking one calls `SectionCut.restoreCut(APP, name)`
and sets `A.sectionOn = true`.

Log tag: `§GRID_CUT_VIEW name=SectionCutN axis=A constant=C`

### §2.5  For print_sheet.js

`queryBuildingInfo()` checks `SectionCut.savedCuts`: if current view is a named cut,
use `name` (e.g. `"SectionCut2 — Y axis @ 14.5m"`) as the storey/view label in the title block.

---

## §3  A3 Print: Auto Orientation + Corporate Header + Editable Preview

### §3.1  Auto Orientation

Currently `print_sheet.js` always uses A3 landscape (2480 × 1754 px).

**Rule:** auto-detect orientation from the current orthographic camera frustum:
```js
var cam = APP.camera; // must be ortho
var visW = cam.right - cam.left;
var visH = cam.top - cam.bottom;
var useLandscape = visW >= visH;
// Landscape: A3W × A3H  |  Portrait: A3H × A3W
```

Expose constant:
```js
var A3_LONG  = Math.round(420 * 150 / 25.4);  // 2480 px
var A3_SHORT = Math.round(297 * 150 / 25.4);  // 1754 px
var sheetW = useLandscape ? A3_LONG : A3_SHORT;
var sheetH = useLandscape ? A3_SHORT : A3_LONG;
```

Log tag: `§PRINT_SHEET orient=landscape|portrait visW=W visH=H`

### §3.2  Corporate.json Header

Add `corporate.json` to `deploy/dev/` with this schema:
```json
{
  "firmName": "BIM OOTB",
  "tagline": "Frictionless BIM",
  "logoText": "BIM OOTB",
  "subtitle": "Two DBs. One browser. Zero install.",
  "defaultProjectRef": "",
  "defaultDrawingRef": "DR-001",
  "defaultRevision": "P1",
  "defaultPreparedBy": ""
}
```

`PrintSheet.capture(APP)` fetches `corporate.json` lazily (cache after first fetch).
Title block right panel uses `firmName` / `tagline` / `logoText` / `subtitle`.

### §3.3  Editable Preview Mode

Before downloading, show a **print preview modal** (`<div id="print-preview-modal">`):
- Full-width canvas showing the composed sheet (scaled to fit 80% of viewport).
- Editable header fields below the canvas (inputs pre-filled from `corporate.json`):
  - **Drawing Title** (= building name, editable)
  - **Project Ref** (= `defaultProjectRef`)
  - **Drawing Ref** (= `defaultDrawingRef`)
  - **Revision** (= `defaultRevision`)
  - **Prepared By** (= `defaultPreparedBy`)
  - **Notes** (free text, 2 lines)
- **Regenerate** button — re-renders canvas with updated fields.
- **Download** button — triggers the PNG download.
- **Cancel** button — closes modal, no download.

Preview re-uses the existing `capture()` internals; pass an `opts` object:
```js
PrintSheet.capture(APP, {
  preview: true,
  overrides: { title, projectRef, drawingRef, revision, preparedBy, notes }
})
```

No server round-trip — purely browser-side DOM canvas.

Log tag: `§PRINT_PREVIEW rendered orient=L|P fields=N`

### §3.4  Sunglasses Reverse for Print

Print always uses **white background** regardless of current theme (`APP.lightTheme`).
After `renderer.render()` captures the Three.js canvas, composite onto a white canvas.
The title block is already white (#f8f8f8). Grid dim labels should be dark (#333) for print.

Add to capture:
```js
ctx.globalCompositeOperation = 'destination-over';
ctx.fillStyle = '#ffffff';
ctx.fillRect(0, 0, sheetW, sheetH);
ctx.globalCompositeOperation = 'source-over';
```

Log tag: `§PRINT_SHEET bg=white forced`

---

## §4  Section Cut Smart Consolidation

### §4.1  Requirement

Multiple section cuts may have overlapping or adjacent ranges that the user wants to
combine into a single contiguous slice (e.g. two Y-cuts that together show a full room).
The user must approve any merge — never auto-override.

### §4.2  Consolidation UI (grid_scissors.js extension)

Add `GridScissors.consolidateUI(APP)` — opens a consolidation panel:

```
┌─────────────────────────────────────────────────────┐
│  Section Cut Consolidation                       [×] │
├─────────────────────────────────────────────────────┤
│  □  SectionCut1  Y @ 12.3m                          │
│  □  SectionCut2  Y @ 14.5m                          │
│  □  SectionCut3  Y @ 14.8m  ← adjacent (<0.5m gap) │
├─────────────────────────────────────────────────────┤
│  Adjacent cuts (gap < 0.5m) shown with ←            │
│  Select 2+ cuts from SAME axis to merge             │
│                                                     │
│  [Preview Merge]   [Confirm Merge]   [Cancel]       │
└─────────────────────────────────────────────────────┘
```

**Preview Merge:** shows a ghost overlay of the merged cut range on the 3D scene
(use a semi-transparent grey clipping slab). Does NOT write any data.

**Confirm Merge:** replaces the selected cuts with one new `SectionCut_merged_N`
entry covering `min(constants)` to `max(constants)`. The merged cut stores
`{ axis, constant: min, to: max, label: 'merged' }`. Removes the originals from
`SectionCut.savedCuts`.

**Void fill:** If the gap between two cuts is > 0.5m but user still selects them
for merge, show a warning: `"Gap of Xm will be filled in merged view — confirm?"`.
The merged cut simply spans the full range.

**No cut allowed for merged view** — display only. User cannot further section-cut
a merged view (that's a screenshot snap — see §4.3).

### §4.3  Screenshot Snap (read-only)

After confirming a merge, offer: **"Save as Image Snap"** — calls
`PrintSheet.capture(APP, { preview: false })` immediately, naming the file
`SectionCut_merged_N_snap.png`. This is the only output path — no DB write.

Log tag: `§SC_CONSOLIDATE merged=N from=[cut1,cut2] axis=Y range=[lo,hi]`

---

## §5  Door Arcs, Stairs, Openings — GF + Level 1

### §5.1  Current State

`grid_door_arcs.js` has `extractLeafAxis()` and `findHinge()` for doors.
Missing: stairs symbol, window opening dashes, opening width label.

### §5.2  Stair Symbol

In `grid_door_arcs.js` add `DoorArcs.generateStairSymbol(stairElement)`:

A stair section cut shows as a series of parallel tread lines. Detect by:
- `ifc_class === 'IfcStair'`
- contour bbox has aspect ratio > 2:1 (stairs are elongated)

Symbol: draw tread lines as short horizontal dashes across the stair width,
spaced `riser_depth` apart. Estimate riser depth = `bbox_height / Math.ceil(bbox_height / 0.18)`.
(Standard riser ≈ 0.15–0.21m). Draw 3–8 tread lines maximum.

Also draw a diagonal arrow from bottom-left to top-right of stair bbox
(convention = direction of ascent).

Log tag: `§DOOR_ARC_STAIR guid=G treads=N riserEst=R`

### §5.3  Window Opening

For `IfcWindow` contours: draw opening dashes (2 parallel lines with gap between),
plus width label centred above the opening.

`DoorArcs.generateWindowOpening(windowElement)`:
- Opening width = long axis of window bbox
- Two short dash lines perpendicular to wall face at each jamb
- Width label: `(width * 1000).toFixed(0) + " W"` — grey, 9px screen

Log tag: `§DOOR_ARC_WINDOW guid=G width=W`

### §5.4  Storey Filter

Both GF and Level 1 section cuts must trigger arc/stair/opening generation.
In `grid_overlay.js`, the section cut invocation currently calls
`DoorArcs.generateArcs(doorElements, wallElements)`. Extend to:
```js
DoorArcs.generateArcs(doorElements, wallElements)
DoorArcs.generateStairSymbol(stairElements)     // add
DoorArcs.generateWindowOpenings(windowElements) // add
```

Filter `stairElements` and `windowElements` from the same `SectionCut.sectionCut()` result
by `ifc_class`:
```js
var stairElements  = cutResult.filter(function(e) { return e.ifcClass === 'IfcStair'; });
var windowElements = cutResult.filter(function(e) { return e.ifcClass === 'IfcWindow'; });
```

Both floor (GF) and floor1 (L1) modes must invoke this — the mode check is already in
`grid_overlay.js`; confirm both branches call the extended arc generation.

Log tag: `§DOOR_ARC_STOREY mode=floor|floor1 doors=D stairs=S windows=W`

---

## §6  Playwright Tests (post-implementation)

Run `node deploy/dev/tests/audit_specs.js` — must exit 0 after adding any spec files.

Add `deploy/dev/tests/specs/27-print-section-dims.spec.js`:

| Test | Issue proven/disproven |
|------|------------------------|
| Grid dim labels ≠ snapped values when bay not 300mm-modular | §1 accuracy fix |
| Section cut save → appears in view panel | §2 view list |
| Section cut restore → sectionPlane.constant matches saved value | §2 restore |
| PrintSheet auto-landscape when visW ≥ visH | §3.1 orientation |
| PrintSheet auto-portrait when visH > visW | §3.1 orientation |
| Print preview modal shows editable fields | §3.3 preview |
| Consolidate: merge 2 cuts → single merged entry in savedCuts | §4 merge |
| Stair symbol: IfcStair produces tread line objects | §5.2 stair |
| Window opening: IfcWindow produces opening dash + label | §5.3 window |

---

## §7  Pre-flight Checklist (before any edit)

- [ ] Read `docs/TestArchitecture.md` §Anti-Drift — no phantom elements added
- [ ] Read MEMORY.md feedback entries: deployment, browser-viewer, branch-policy
- [ ] Confirm `deploy/live/` is NOT touched
- [ ] Confirm `component_library.db` is NOT touched
- [ ] Confirm no `corporate.json` invented values — use the schema in §3.2 exactly

---

## §10  Floor-Level Section Cut Height (Bug Fix — all buildings)

### §10.1  Root Cause

`getBuildingEnvelopeIFC()` in `grid_overlay.js` computes `zMin` as
`MIN(center_z)` over ALL `element_transforms`. Buildings with subterranean
elements (footings, piles, foundations) produce very low `zMin` values —
e.g. Duplex: zMin = -1.55m. This places the floor plan cutZ at
-1.55 + 1.0 = **-0.55m**, BELOW the actual floor slab at 0.0m.
Result: no walls, doors, or windows intersect the cut → no contours, no arcs.

This is why door arcs and wall contours only appear on SampleHouse (zMin ≈ 0)
but not on multi-storey buildings with foundations.

### §10.2  Fix — grid_overlay.js: `renderContoursForView`

Do NOT change `getBuildingEnvelopeIFC` (it is used for grid line extent which
correctly spans the full building height). Instead, compute cutZ separately
from the floor slab top:

```js
// Implementing 2D_027 §10.2 — Witness: W-2D27-FLOORZ
function getFloorCutZ(db, envFallbackZMin) {
  // Try lowest IfcSlab top surface first (= floor level)
  try {
    var r = db.exec(
      "SELECT MIN(t.center_z + t.bbox_z * 0.5) " +
      "FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid " +
      "WHERE m.ifc_class = 'IfcSlab'"
    );
    if (r.length && r[0].values[0][0] != null) {
      var slabTop = Number(r[0].values[0][0]);
      console.log('§GRID_FLOORCUTZ source=slab top=' + slabTop.toFixed(3));
      return slabTop;
    }
  } catch (e) {}
  // Fallback: lowest ARC wall center_z (excludes structural footings)
  try {
    var r2 = db.exec(
      "SELECT MIN(t.center_z) " +
      "FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid " +
      "WHERE m.ifc_class IN ('IfcWall','IfcWallStandardCase')"
    );
    if (r2.length && r2[0].values[0][0] != null) {
      var wallMin = Number(r2[0].values[0][0]);
      console.log('§GRID_FLOORCUTZ source=wall min=' + wallMin.toFixed(3));
      return wallMin - 0.5; // walls start ~0.5m above their center
    }
  } catch (e) {}
  console.log('§GRID_FLOORCUTZ source=fallback zMin=' + envFallbackZMin.toFixed(3));
  return envFallbackZMin;
}
```

In `renderContoursForView`, replace:
```js
cutZ = envCache.zMin + ((clipCfg && clipCfg.offset_m) || 1.0);
```
with:
```js
cutZ = getFloorCutZ(A.db, envCache.zMin) + ((clipCfg && clipCfg.offset_m) || 1.0);
```

The `offset_ratio` path (for floor1 / Level 2) still uses `envCache.zMin` — no change
(ratio-based cut is already relative to building height, not absolute floor).

Log tags:
- `§GRID_FLOORCUTZ source=slab|wall|fallback top=Z` — where floor was detected
- `§GRID_CUTZ_RESOLVED cutZ=Z mode=floor` — final cut height used

---

## §11  Constant Screen-Space Bubble Scale (Bug Fix — all buildings)

### §11.1  Root Cause

`clampBubbleScales()` sets `obj.scale.set(s, s, 1)` where `s = bubbleScale`
(a fixed world-space constant derived from `maxDim * 0.035`).

Three.js sprites in an orthographic camera: as `cam.zoom` increases
(user zooms in), the world appears magnified — sprites at constant world size
grow proportionally on screen. Zoomed in 2×: bubbles appear 2× larger.

This is wrong. Grid bubbles in CAD drawings maintain constant screen size.

### §11.2  Fix — grid_overlay.js: `clampBubbleScales`

Replace the fixed `s = bubbleScale` with a zoom-adaptive calculation:

```js
// Implementing 2D_027 §11.2 — Witness: W-2D27-BUBBLESCALE
var s;
if (cam.isOrthographicCamera) {
  // Visible world height shrinks as zoom increases — keep screen size constant
  var visH = (cam.top - cam.bottom) / (cam.zoom || 1);
  // Target: bubble = 3% of visible height (= constant ~22px at typical zoom)
  s = visH * 0.03;
  // Clamp: prevent extreme sizes at very low/high zoom
  var minS = bubbleScale * 0.2;
  var maxS = bubbleScale * 4.0;
  s = Math.max(minS, Math.min(maxS, s));
} else {
  // Perspective: scale by distance to target
  var dist = cam.position.distanceTo(A.controls.target);
  s = dist * 0.04;
  s = Math.max(bubbleScale * 0.2, Math.min(bubbleScale * 4.0, s));
}
```

This replaces the existing `var s = bubbleScale;` line only. All downstream
usage of `s` (density filter, `obj.scale.set(s, s, 1)`) stays unchanged.

`bubbleScale` (computed from `maxDim * 0.035`) remains as the world-space
reference for clamping — it still sets the range, but no longer the fixed value.

Log tag: `§BUBBLE_SCALE s=S visH=V zoom=Z` (only when scale changes by >5%)

---

## §8  Deploy Flow (deploy/dev/ ONLY)

Edit → `node --check deploy/dev/grid_dims.js` (syntax) → verify §-tags present →
upload to dev bucket → smoke-test viewer URL → fetch back → confirm loaded.
ONE flow, never stop partway.

## §9  Session Closeout

Update PROGRESS.md:
- Witness counts changed
- Which §-log lines were produced
- Run space contract check before committing
