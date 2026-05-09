# 2D_028: Storey Band Filter + Opportunity-Driven Grid Detection

# ⚠ DO NOT REMOVE — Read the log after every run. Scope: deploy/dev/ ONLY.

## Status: REFACTORED (2026-05-09) — grid alignment fixed, §9/§10 remain

**What is done:**
- `grid_rules.json` extended with `floor_plan` + `grid_detection` blocks
- `grid_dims.js`: `detectOpportunityGrids()` — two SQL queries, no blobs, weighted votes
- `section_cut.js`: storey band filter at `§SC_BAND_FILTER`, class exclusion + rtree Z-bounds
- `grid_overlay.js` + `grid_drag.js`: `window._gridRules` shared; rules passed to both consumers
- `28-storey-band.spec.js`: 15 wiring tests, audit passes
- Committed: `53c0cffa`

**REFACTOR (2026-05-09) — §3.1 face-vote spec was WRONG — fixed:**
- §3.1 said "face votes at center_x ± bbox_x/2". This caused two grid lines per wall when
  wall thickness > faceTol (0.3m). A 0.35m wall → two clusters 0.35m apart → two lines.
- `snapToNearestFace` compounded the problem: pulled merged face-pair means back to raw faces.
- Opening votes on BOTH axes: 10 doors along one wall → 10 spurious lines on wrong axis.

**Actual correct algorithm (now in code):**
1. Walls vote CENTER, not faces. Weight×2 compensates for collapsing two face votes into one.
   One cluster per wall → one grid line per wall.
2. Openings vote ONE axis only, determined by bbox aspect ratio:
   - bbox_y > bbox_x×1.2 → portrait (in Y-running wall) → vote X only
   - bbox_x > bbox_y×1.2 → landscape (in X-running wall) → vote Y only
   SQL updated to fetch bbox_x, bbox_y for openings.
3. snapToNearestFace calls removed — centers are already correct positions.

**§3.1 spec below is superseded. The implementation is the spec.**

**performance fix (same session):**
- `sectionCut` was loading geometry BLOBs for ALL sliceable elements to estimate Z-range.
  Now uses `bbox_z` from element_transforms first; geometry loaded only for confirmed CUT.
  Both SQL queries now include `bbox_z` as column 14.

**GF storey fix:**
- `computeStoreyAwareCutZ` now queries door counts per storey; ranks by doors-desc, floorZ-asc.
  Prevents DX sub-grade cluster (elementCount≥5 but no doors) from being picked as GF.

**scissors fix:**
- `onViewBtnClick` now turns off scissors before entering floor plan mode (def.clip=true).

**What to verify in browser:**
- `§GD_WALL_WEIGHT axis=X center=N.NNN` — walls voting center, not face pair
- `§GD_OPP_OPEN rows=N xVotes=A yVotes=B` — A ≠ B (axis-aware split)
- `§GD_OPP_CLUSTER axis=X clusters=M` — M ≈ expected structural bay count
- `§GRID_STOREY GF ... doors=N` — GF storey has N > 0 doors
- `§SC_PERF` in logs absent (no spurious geo loads)

---

## Session Isolation

**Category:** Browser JS — zero Java, zero live/ edits, zero new files unless named here.

**Prefix:** `S2D28-`

**Files OWNED by this session (create or edit):**
- `deploy/dev/grid_rules.json` — extend with `floor_plan` + `grid_detection` keys ✓ DONE
- `deploy/dev/section_cut.js` — storey band filter using rules ✓ DONE
- `deploy/dev/grid_dims.js` — opportunity-vote algorithm ✓ DONE
- `deploy/dev/grid_overlay.js` — rules threading ✓ DONE
- `deploy/dev/grid_drag.js` — window._gridRules shared cache ✓ DONE

**Files READ-ONLY:**
- `deploy/dev/grid_views.js` — VIEW_DEFS source of truth
- `deploy/dev/grid_contours.js` — sectionCut consumer (read to understand data contract)
- `deploy/live/*` — PRODUCTION, never touch

**New files:** none — extend existing JS and JSON only.

**Pre-flight citation:** Every changed block must start with:
```
// Implementing 2D_028 §X.Y — Witness: W-2D28
```

---

## Background — Why Both Issues Share One Root

Issue 1 (roof bleed in GF) and Issue 2 (no grids detected) are the same problem:
both lack a **storey-aware spatial band**. The section cut fires at one Z plane — it has
no concept of "I am drawing the Ground Floor, so only show GF elements."

A floor plan is not a knife cut. It is a **window into a storey band**:

```
  roof  ──────────────────────
  L1 slab (top)  ─────────────  ← nextFloorZ
  [  storey band  ]             ← only elements that live here are included
  GF slab (top)  ─────────────  ← floorZ
  foundation  ─────────────────
```

The cut plane (for door/window arcs) sits at `floorZ + cut_offset_m` inside the band.
The **band** is what excludes roof, upper-floor slabs, foundation piles.

The **same band** defines which elements are structural candidates for grid detection:
a wall that spans the full storey height (Z-span ≥ `min_structural_span_m`) is structural.
A ceiling panel that lives only at `nextFloorZ - 50mm` is not.

Both fixes are driven by the same set of rules from `grid_rules.json`.

---

## §1  Extend grid_rules.json — Rules Template

Add two new top-level keys to `deploy/dev/grid_rules.json`.
ALL constants that were previously hardcoded in `section_cut.js` and `grid_dims.js`
must come from here. Never hardcode.

```json
{
  "floor_plan": {
    "cut_offset_m":         1.0,
    "band_min_above_floor": 0.05,
    "band_max_below_next":  0.10,
    "slab_snap":            true,
    "opening_z_range":      [0.50, 2.40],
    "band_fallback_height": 3.5,
    "exclude_above_band": [
      "IfcRoof", "IfcRoofing", "IfcCovering",
      "IfcRampFlight", "IfcStairFlight"
    ],
    "exclude_below_band": [
      "IfcFoundation", "IfcPile", "IfcFooting",
      "IfcReinforcingElement", "IfcReinforcingMesh"
    ]
  },
  "grid_detection": {
    "min_structural_span_m": 1.80,
    "face_cluster_tol_m":    0.30,
    "opening_cluster_tol_m": 0.15,
    "min_votes":             2,
    "min_bay_m":             0.80,
    "opportunity_classes": [
      "IfcDoor", "IfcWindow"
    ],
    "structural_classes": [
      "IfcWall", "IfcWallStandardCase",
      "IfcColumn", "IfcBeam", "IfcMember"
    ],
    "space_classes": ["IfcSpace"]
  }
}
```

### Key definitions

| Key | Meaning |
|-----|---------|
| `cut_offset_m` | Cut plane = floorZ + this. Replaces hardcoded `DEFAULT_CUT_OFFSET = 1.0`. |
| `band_min_above_floor` | Minimum element top to be included: `elemMaxZ ≥ floorZ + 0.05`. Excludes floor tiles flush with slab. |
| `band_max_below_next` | Maximum element bottom to be included: `elemMinZ ≤ nextFloorZ - 0.10`. Excludes upper-floor slab undersides. |
| `slab_snap` | If true, detect floorZ from `MIN(IfcSlab top)` per storey (already done in §10). |
| `opening_z_range` | Z range (relative to floorZ) in which doors and windows must sit. [0.50, 2.40] = standard door/window band. |
| `band_fallback_height` | If next storey is not detected, assume band = floorZ + this. |
| `exclude_above_band` | IFC classes unconditionally excluded if `elemMinZ > nextFloorZ`. |
| `exclude_below_band` | IFC classes unconditionally excluded if `elemMaxZ < floorZ`. |
| `min_structural_span_m` | Z-span (rtree maxZ − minZ) an element must cover to be a structural grid candidate. Roof panels: 0.05m span → excluded. Walls: 2.4–4m span → included. |
| `face_cluster_tol_m` | Tolerance for clustering wall face positions into grid lines. |
| `opening_cluster_tol_m` | Tighter tolerance for door/window centre clustering (openings sit precisely at grid). |
| `min_votes` | Minimum number of elements contributing to a cluster for it to become a grid line. Eliminates isolated geometry noise. |
| `opportunity_classes` | IFC classes whose centres are "opportunities" — always contribute to grid candidates. |
| `structural_classes` | IFC classes whose face positions contribute after Z-span filter. |

---

## §2  Storey Band Filter in section_cut.js

### §2.1  detectStoreys — add nextFloorZ

`detectStoreys(db)` currently returns `[{name, floorZ, elementCount}]`.

Extend to compute `nextFloorZ` per storey:

```js
// Implementing 2D_028 §2.1 — Witness: W-2D28
// Sort storeys by floorZ ascending. nextFloorZ = next storey's floorZ.
// Last storey: nextFloorZ = floorZ + rules.floor_plan.band_fallback_height (default 3.5).
storeys.sort(function(a,b){ return a.floorZ - b.floorZ; });
for (var si = 0; si < storeys.length; si++) {
  storeys[si].nextFloorZ = (si < storeys.length - 1)
    ? storeys[si+1].floorZ
    : storeys[si].floorZ + (rules.floor_plan.band_fallback_height || 3.5);
  storeys[si].bandMin = storeys[si].floorZ + (rules.floor_plan.band_min_above_floor || 0.05);
  storeys[si].bandMax = storeys[si].nextFloorZ - (rules.floor_plan.band_max_below_next || 0.10);
}
console.log('§SC_STOREYS count=' + storeys.length + ' bands=' +
  storeys.map(function(s){ return s.name+'['+s.floorZ.toFixed(2)+'-'+s.nextFloorZ.toFixed(2)+']'; }).join(' '));
```

### §2.2  sectionCut — band filter before slice loop

`sectionCut(db, libDb, cutZ, storeyName, options)` already determines which storey is active
and which cutZ to use. Add band filtering as a pre-loop step using **rtree Z-bounds**:

```js
// Implementing 2D_028 §2.2 — Witness: W-2D28
// Band filter: exclude elements outside the storey height band.
// Uses rtree minZ/maxZ (already loaded) — zero geometry blob reads.
var bandMin = activeStorey ? activeStorey.bandMin : cutZ - 0.5;
var bandMax = activeStorey ? activeStorey.bandMax : cutZ + 2.5;
var fp = (rules && rules.floor_plan) || {};
var excAbove = fp.exclude_above_band || [];
var excBelow = fp.exclude_below_band || [];

var bandFiltered = [];
var bandExcluded = 0;
for (var bi = 0; bi < allRows.length; bi++) {
  var row = allRows[bi];
  var cls = row[1] || '';
  var elemMinZ = Number(row[9]);   // rtree minZ (or 0 without rtree)
  var elemMaxZ = Number(row[10]);  // rtree maxZ (or 0 without rtree)

  // Hard-exclude by class + position
  if (excAbove.indexOf(cls) >= 0 && elemMinZ > bandMax) { bandExcluded++; continue; }
  if (excBelow.indexOf(cls) >= 0 && elemMaxZ < bandMin) { bandExcluded++; continue; }

  // Soft-exclude: element entirely outside band
  if (useRtree) {
    if (elemMaxZ < bandMin || elemMinZ > bandMax) { bandExcluded++; continue; }
  }

  bandFiltered.push(row);
}
console.log('§SC_BAND_FILTER bandMin=' + bandMin.toFixed(2) + ' bandMax=' + bandMax.toFixed(2) +
            ' in=' + bandFiltered.length + ' excluded=' + bandExcluded);
allRows = bandFiltered;
```

This replaces the current `clipBox` logic (which filters XY, not Z).
Keep `clipBox` for large-building XY clipping — add Z-band as an orthogonal filter.

### §2.3  rules parameter threading

`sectionCut` must accept `options.rules` (the parsed `grid_rules.json` object).
`grid_overlay.js` fetches `grid_rules.json` once (already does so for `grid_drag.js`)
and passes it in:

```js
// In grid_overlay.js — Implementing 2D_028 §2.3 — Witness: W-2D28
var cutResult = SectionCut.sectionCut(A.db, null, cutZ, null, {
  rules: window._gridRules   // set by grid_drag.js loadRules() or fetched inline
});
```

If `window._gridRules` is not yet set, `sectionCut` falls back to defaults.
Add to `grid_overlay.js`: after `GridDrag.loadRules(json)` callback, store to
`window._gridRules = json` so all consumers share one loaded copy.

Log: `§SC_RULES loaded=true|fallback` inside sectionCut.

---

## §3  Opportunity-Driven Grid Detection in grid_dims.js

This replaces the column → wall-centroid fallback chain with a multi-source
**opportunity vote** algorithm. All parameters come from `rules.grid_detection`.

### §3.1  The Algorithm

A grid line position is a candidate if it receives ≥ `min_votes` from:

**Source A — Structural wall faces** (Z-span filter applies):
- For each wall/column/beam whose rtree `(maxZ - minZ) ≥ min_structural_span_m`:
  - If wall runs in Y (`bbox_y > 2 * bbox_x`): contributes face votes at `center_x - bbox_x/2` and `center_x + bbox_x/2` to X-candidates.
  - If wall runs in X (`bbox_x > 2 * bbox_y`): contributes face votes at `center_y - bbox_y/2` and `center_y + bbox_y/2` to Y-candidates.
  - If roughly square (column): contributes `center_x` to X and `center_y` to Y.

**Source B — Openings as opportunities** (the insight):
- `IfcDoor`, `IfcWindow` whose `center_z` falls within `opening_z_range` of floorZ:
  - `center_x` → X-candidate vote
  - `center_y` → Y-candidate vote
- These have weight 2 (openings sit precisely ON grid faces, not approximately).

**Source C — Space corners** (IfcSpace bbox faces):
- Each `IfcSpace`: contributes `center_x ± bbox_x/2` to X, `center_y ± bbox_y/2` to Y.
- Weight 1.

**Vote aggregation:**
1. Collect all candidates `{pos, weight}` per axis.
2. Cluster within `face_cluster_tol_m` (structural) or `opening_cluster_tol_m` (openings).
3. A cluster becomes a grid line only if `sum(weights) ≥ min_votes`.
4. `rawPosition = weighted mean of cluster`, `position = rawPosition` (snap applied downstream by `snapGrids`).

```js
// Implementing 2D_028 §3.1 — Witness: W-2D28
function detectGridsFromOpportunities(db, rules, floorZ) {
  var gd = (rules && rules.grid_detection) || {};
  var minSpan    = gd.min_structural_span_m  || 1.80;
  var faceTol    = gd.face_cluster_tol_m     || 0.30;
  var openTol    = gd.opening_cluster_tol_m  || 0.15;
  var minVotes   = gd.min_votes              || 2;
  var fp         = (rules && rules.floor_plan) || {};
  var openZLo    = floorZ + (fp.opening_z_range ? fp.opening_z_range[0] : 0.5);
  var openZHi    = floorZ + (fp.opening_z_range ? fp.opening_z_range[1] : 2.4);
  var structCls  = gd.structural_classes  || ['IfcWall','IfcWallStandardCase','IfcColumn','IfcBeam'];
  var openCls    = gd.opportunity_classes || ['IfcDoor','IfcWindow'];
  var spaceCls   = gd.space_classes       || ['IfcSpace'];

  var xVotes = [], yVotes = [];  // [{pos, weight}]

  // Source A: structural walls/columns — Z-span filter
  var structSet = "'" + structCls.join("','") + "'";
  var structSql = hasRtree(db)
    ? "SELECT m.ifc_class, t.center_x, t.center_y, t.bbox_x, t.bbox_y, " +
      "  (r.maxZ - r.minZ) AS zspan " +
      "FROM elements_meta m " +
      "JOIN element_transforms t ON m.guid = t.guid " +
      "JOIN elements_rtree r ON m.id = r.id " +
      "WHERE m.ifc_class IN (" + structSet + ")"
    : "SELECT m.ifc_class, t.center_x, t.center_y, t.bbox_x, t.bbox_y, t.bbox_z AS zspan " +
      "FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid " +
      "WHERE m.ifc_class IN (" + structSet + ")";

  // ... (query, then for each row: zspan filter, orientation classify, add votes)
  // See §3.2 for query execution pattern.

  // Source B: openings within opening_z_range
  var openSet = "'" + openCls.join("','") + "'";
  var openSql = "SELECT t.center_x, t.center_y, t.center_z " +
    "FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid " +
    "WHERE m.ifc_class IN (" + openSet + ")";

  // Source C: spaces
  var spaceSql = "SELECT t.center_x, t.center_y, t.bbox_x, t.bbox_y " +
    "FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid " +
    "WHERE m.ifc_class IN ('" + spaceCls.join("','") + "')";

  // ... aggregate, cluster, return {xLines, yLines}
}
```

### §3.2  Query execution pattern

Follow the existing `detectGridsAtPlane` pattern exactly — `db.exec(sql)`, handle errors,
iterate `result[0].values`. Never invent data.

For the Z-span filter without rtree: use `bbox_z` from `element_transforms` as a proxy.
Log when rtree is used vs bbox fallback: `§GD_SPAN_SOURCE rtree=true|false`.

### §3.3  Vote cluster function

```js
// Implementing 2D_028 §3.3 — Witness: W-2D28
function clusterVotes(votes, tolerance) {
  if (!votes.length) return [];
  votes.sort(function(a,b){ return a.pos - b.pos; });
  var clusters = [{sum: votes[0].pos * votes[0].weight, weight: votes[0].weight, count: 1}];
  for (var i = 1; i < votes.length; i++) {
    var last = clusters[clusters.length - 1];
    var mean = last.sum / last.weight;
    if (Math.abs(votes[i].pos - mean) < tolerance) {
      last.sum    += votes[i].pos * votes[i].weight;
      last.weight += votes[i].weight;
      last.count++;
    } else {
      clusters.push({sum: votes[i].pos * votes[i].weight, weight: votes[i].weight, count: 1});
    }
  }
  return clusters
    .filter(function(c){ return c.weight >= minVotes; })
    .map(function(c){ return { position: c.sum / c.weight, rawPosition: c.sum / c.weight, weight: c.weight }; });
}
```

Log: `§GD_OPP_CLUSTER axis=X candidates=N clusters=M voted=K` (K = clusters that pass min_votes).

### §3.4  Wire into detectGrids and detectGridsAtPlane

Replace the existing column → wall-centroid fallback in **both** functions with a call to
`detectGridsFromOpportunities(db, rules, floorZ)` when columns are not found.

Add `rules` and `floorZ` as optional parameters to `detectGrids(db, tolerance, rules, floorZ)`.

`detectGridsAtPlane` already receives `cutZ` — derive `floorZ` from it: pass it through.

Log: `§GD_SOURCE columns=N opport=true|false` — which path was taken.

---

## §4  The Drag-Sweep Insight — Z-Span as Structural Persistence

The user's insight deserves its own log so we can verify it working:

As the section cut plane sweeps down through a storey, structural elements (walls, columns)
appear at every Z within their range. A door appears only within [sill, head]. Roof structure
appears only near the top. The **Z-span of an element in the rtree** is exactly this
persistence metric — no sweep needed, the answer is already in the DB.

`(rtree.maxZ - rtree.minZ) ≥ min_structural_span_m` IS the sweep consistency filter.
An element that "persists" for ≥ 1.8m of vertical travel is structural.

This is the same condition we apply for Source A votes in §3.1.

**Log tag** to confirm: `§GD_STRUCTURAL_SPAN guid=G span=S class=C votes=V` (per element
that passes the span filter and contributes votes). Sample 5 elements per run.

The secondary check — "if it just hit too small a length then it is not a positive" — is
`min_votes ≥ 2`: a position that only one element contributes is noise.
Two or more elements aligning to within `face_cluster_tol_m` on the same face = grid line.

---

## §5  grid_overlay.js — Rules Loading + Threading

### §5.1  Single rules load

`grid_drag.js` already fetches `grid_rules.json` and calls `GridDrag.loadRules(json)`.
Store to `window._gridRules` in that callback so all modules share one copy.

In `grid_overlay.js`, add at the top of `renderContoursForView`:
```js
// Implementing 2D_028 §5.1 — Witness: W-2D28
var rules = window._gridRules || {};
```

Pass `rules` to:
- `SectionCut.sectionCut(A.db, null, cutZ, null, { rules: rules })`
- `GridDims.detectGridsAtPlane(A.db, cutZ, rules.grid_detection && rules.grid_detection.face_cluster_tol_m || 0.3, rules, cutZ)`

### §5.2  Log the storey band being used

```js
console.log('§GRID_OVERLAY_BAND mode=' + mode + ' cutZ=' + cutZ.toFixed(2) +
            ' band=[' + (rules.floor_plan ? (cutZ-1).toFixed(2) : '?') + ']');
```

---

## §6  Playwright Tests

Add `deploy/dev/tests/specs/28-storey-band.spec.js`:

| Test | Issue proven/disproven |
|------|------------------------|
| `grid_rules.json` has `floor_plan` key | §1 rules template loaded |
| `grid_rules.json` has `grid_detection` key | §1 detection rules loaded |
| `SectionCut.detectStoreys` returns `nextFloorZ` per storey | §2.1 band computed |
| `sectionCut` accepts `options.rules` without error | §2.3 threading |
| `detectGrids` signature accepts `rules` param | §3.4 wiring |
| `detectGridsFromOpportunities` function present in grid_dims.js | §3.1 algorithm present |
| `§SC_BAND_FILTER` log tag present in section_cut.js | §2.2 band filter deployed |
| `§GD_OPP_CLUSTER` log tag present in grid_dims.js | §3.3 vote cluster deployed |
| `§GD_STRUCTURAL_SPAN` log tag present in grid_dims.js | §4 span-as-persistence deployed |

Run `node deploy/dev/tests/audit_specs.js` — must exit 0.

---

## §7  Deploy Flow

Edit → `node --check` → verify §-tags exist → upload to dev bucket → smoke-test → fetch back.

Files to upload:
```bash
# Rules
oci os object put --bucket-name bim-ootb-dev \
  --file deploy/dev/grid_rules.json --name sandbox/grid_rules.json \
  --content-type "application/json" --force

# Logic
for f in section_cut grid_dims grid_overlay; do
  oci os object put --bucket-name bim-ootb-dev \
    --file "deploy/dev/${f}.js" --name "sandbox/${f}.js" \
    --content-type "application/javascript" --force
done
```

---

## §8  Session Closeout Checklist

- [ ] `§SC_BAND_FILTER` log appears in browser console when GF is rendered
- [ ] `§GD_OPP_CLUSTER` log appears showing opportunity votes counted
- [ ] SimpleCastle GF: roof bleed eliminated (no IfcRoof/IfcCovering in contours)
- [ ] SimpleCastle GF: door arcs visible (IfcDoor in cut band)
- [ ] Dim chains appear after grid lines detected from opportunities
- [ ] Update PROGRESS.md
- [ ] `node deploy/dev/tests/audit_specs.js` exits 0

---

## §9  Opening Callout Labels (Discussion — implement next)

### The Principle

Real architectural 2D drawings have TWO separate annotation layers:

1. **Grid dim chain** (major): structural bay widths between grid lines — `3600 / 7200 / 3600`
2. **Opening callout** (minor): door/window clear widths stuck directly to the element — `900W`, `D1`, `W3`

The grid dim chain carries structural module. Opening callouts carry element identity.
These never share the same annotation line. Freeing the grid from sub-bay dimensions is correct practice.

### What to implement in grid_door_arcs.js

For each `IfcDoor`/`IfcWindow` in the section cut result:

1. **Width label**: `(bbox_x or bbox_y, whichever is the opening axis) * 1000 + "W"` — e.g. `"900W"`
   Positioned as a small sprite offset perpendicular to the wall face, above the opening.

2. **Type tag**: read `element_name` from the element record — `element_name.split(':')[0]`
   gives the Revit family name or IFC type name (e.g. `"Single-Flush"`, `"Casement"`).
   Render as a grey 9px tag below the width label.

3. **IFC class tag**: if element_name is empty, fall back to `ifcClass` (`"DOOR"`, `"WINDOW"`).
   This is the ARC IFC text the user mentioned — always available, never null.

4. **Side dimension**: two short tick lines at jamb positions (already in `generateWindowOpenings`)
   with a dimension number between them, rendered at `opening_cluster_tol_m` offset from wall.

Log tag: `§DOOR_ARC_LABEL guid=G width=NNNmm tag=TYPENAME`

### Rules extension

Add to `grid_rules.json → floor_plan`:
```json
"opening_label_offset_m": 0.15,
"opening_label_font_px":  9
```

---

## §10  3D Grid Lines on Live Scene — Adjust Grid Phase (New Feature Idea)

### The Concept

During **Adjust Grid** mode, the user selects a saved section view or cut. Currently this only
affects the 2D flat overlay. The new idea:

> The tagged cut/view's grid lines are projected into the **live 3D scene** — the full unlocked
> building remains visible in 3D, but the selected section's grid lines float as 3D planes through it.
> The user can drag those grid lines in 3D space, and as they do:
> 1. The section contours recompute live at the new position
> 2. The cost panel updates (BOQ changes with the spatial scope)

This is fundamentally different from the scissors cut: the scissors hides geometry. This overlays
the grid lines as **3D annotation planes** on the unclipped building — like construction lines
floating in space, showing where the floor plan grid intersects real 3D geometry.

### Why This Is Architecturally Sound

- Grid lines in `GridDims` are already in IFC world coordinates (meters)
- `APP.ifc2three()` already converts them to Three.js world space
- A grid line at X=4.2m → a `THREE.Plane` at `x=ifc2three(4.2, 0, 0).x` in world space
- Rendering it in 3D: a semi-transparent `PlaneGeometry` spanning the building height and depth
- The plane uses `THREE.DoubleSide`, low opacity (0.15), colour by axis (X=red, Y=blue)
- Dragging = pointer on the plane → `GridDrag` callback → updates position → recomputes vote cluster

### Cost Panel Integration

When grid lines change position, the spatial bounding boxes of rooms change.
`SectionCut.sectionCut()` with a new cutZ or bbox → updated element list → re-run BOQ query:

```sql
SELECT m.element_name, COUNT(*) AS qty, SUM(t.bbox_x * t.bbox_y) AS area_m2
FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid
WHERE t.center_x BETWEEN gridX1 AND gridX2
  AND t.center_y BETWEEN gridY1 AND gridY2
GROUP BY m.ifc_class
```

This is `boq_charts.html` territory — a live cost panel responding to spatial drag.
As the user drags a grid line, the bay area changes → material quantities change → cost updates.

**This is the killer**: drag a grid line in 3D, watch the cost panel update in real time.
The building is not a static object — it is a live query against a cost model.

### Implementation outline (next prompt: 2D_029)

Files:
- `deploy/dev/grid_overlay.js` — add `renderGridPlanesIn3D(APP, grids, mode)` function
- `deploy/dev/grid_drag.js` — extend drag to work in 3D perspective (pointer → world ray → plane intersection)
- `deploy/dev/boq_charts.html` or a new `cost_panel.js` — live BOQ query responding to grid position

Log tags:
- `§GRID_3D_PLANES count=N mode=section|adjust` — when planes are injected into scene
- `§GRID_3D_DRAG axis=X pos=P` — on drag update
- `§GRID_3D_BOQ elements=N area=A cost=C` — cost panel refresh

Rules extension in `grid_rules.json → grid_move`:
```json
"plane_opacity": 0.12,
"plane_color_x": "#ff4444",
"plane_color_y": "#4444ff",
"live_boq":      true
```

### Saved Cut → Filtered 3D View

A saved cut, when restored, also applies the storey band filter to the 3D scene:
- Elements outside `[bandMin, bandMax]` get `mesh.visible = false` temporarily
- This is NOT the scissors cut (which uses a clipping plane) — it is a visibility filter
- Roofs, upper floors, foundations disappear from view
- What remains is a "see-through floor plate" — the GF plan in context of the 3D building
- User sees the cut is clean: only GF elements visible, grid lines floating through them in 3D

Log tag: `§GRID_3D_BAND_VIS mode=GF shown=N hidden=N`
