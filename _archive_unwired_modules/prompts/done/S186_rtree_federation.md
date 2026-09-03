# ⚠ DO NOT REMOVE
# Scope: S186 — RTree federated architecture + hierarchical drill-down
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: S186 SESSION 2 DONE — storey freeze fixed, bake scripts validated, dedup diagnostics improved.
# TOP ISSUE: NONE — storey-click freeze resolved (pre-computed bboxes). Bake scripts proven (Duplex + Hospital + city).
# REMAINING: ARC dedup bug — improved diagnostics (§DEDUP_SKIP db_disc/query_disc), likely progressive MESH preloading ARC.
# NEXT: S187 — visual validation of baked .blend in interactive Blender, overnight 1M per-building warm strategy.

## Context

S185 DONE: RTree speed (pre-warm all meshes, link=0ms), per-element placement,
discipline merge in Outliner, bar graph progress, auto clip_end, back-to-front
ordering, rotation/offset correction for redirected meshes, wait cursor.

GN mode remains HALTED. RTree is the primary viewer.

## Part A — Hierarchical Drill-Down in RTree Inspector

### Current behaviour (S185 fix applied)

Search returns a flat list of **buildings** matching the term. User clicks a
building → cockpit shows discipline counts → MESH/DISC/SHRED buttons appear.
S185 fixed: MESH ACTIONS now shows whenever a building is active, regardless
of whether element search has results (was hidden when searching by building
name e.g. "Duplex" which doesn't match element names).

This works for city-level search but is too flat for large buildings.

### Target behaviour

Three-level drill-down, context-sensitive:

| Level | When | Search results show | Actions |
|-------|------|---------------------|---------|
| L0 — City | No building selected | Buildings (current) | Fly-to only, NO Mesh/Disc/Shred |
| L1 — Building | Building selected, has storeys | Storeys within building | Mesh/Disc/Shred scoped to storey |
| L1 — Building | Building selected, no storeys | Elements (current L2) | Mesh/Disc/Shred scoped to building |
| L2 — Storey | Storey selected | Rooms within storey (if any), else elements | Mesh/Disc/Shred scoped to storey+room |

### Key changes

1. **L0 — City level**: when search results show buildings (no building drilled into),
   hide the MESH ACTIONS box entirely. User must drill into a building first.
   The N-panel should show building list + fly-to buttons only.

2. **L1 — Building level**: when user drills into a large building (has storeys),
   show storey list instead of element list. Each storey row = fly-to + count.
   The storey filter (`props.rtree_storey`) becomes a clickable list, not a text field.
   Discipline bars and MESH/DISC/SHRED appear, scoped to the selected storey.

3. **L1 — Small building**: buildings with no storeys (e.g. Duplex, SampleHouse)
   go straight to element list as today. MESH/DISC/SHRED appear immediately.

4. **L2 — Storey level**: clicking a storey drills into it. Shows rooms if the
   building has rooms in that storey, otherwise shows elements. MESH loads
   only elements in that storey (existing `storey` filter in `_query_viewport`).

### Implementation notes

- `bv._building_storeys` already populated by `FedRTreeCountBuilding`
- Storey filter already in `_query_viewport` (`WHERE m.storey = ?`)
- Room data available in `elements_meta.storey` (some buildings encode room in storey)
- The drill-down state needs a new variable: `bv._active_storey` (default empty = all)
- Fly-to-storey: query `elements_rtree` for bbox of all elements in that storey,
  fly to centroid

### UI layout (BIM_PT_rtree_inspector)

```
[SEARCH BAR]  [▶]

BUILDINGS — 'door'                    ← L0: city level
  ┌─ Hospital (12,345)    [fly]
  ├─ Terminal (8,901)     [fly]
  └─ Duplex (45)          [fly]
                                      ← NO Mesh/Disc/Shred at L0

─── after clicking Hospital ───

Hospital                              ← L1: building level
  Floor: [storey list, clickable]
  ┌─ Level 00 (3,456)    [fly]
  ├─ Level 01 (4,567)    [fly]
  └─ Level 02 (4,322)    [fly]

  ARC ████████▒▒▒▒  2,100/8,901      ← discipline bars
  STR ██▒▒▒▒▒▒▒▒▒▒  500/3,444
  ...

  MESH ACTIONS                        ← appears after building drill-in
  [+ARC] [+STR] [+MEP] [+ELEC] [+FP]
  [SHRED SELECTED ✂]

─── after clicking Level 01 ───

Hospital > Level 01                   ← L2: storey level
  ELEMENTS — top 10
  ┌─ IfcWall · Basic Wall · L01
  ├─ IfcDoor · Single Panel · L01
  ...
  MESH ACTIONS (storey-scoped)
```

## Part B — Per-Building Baked .blend (federated architecture)

### Concept

Pre-bake one .blend per building with all objects placed and mesh refs linked
to library.blend. City file links all building .blends.

```
city.blend
  ├── Link: Hospital_baked.blend  → library.blend
  ├── Link: Terminal_baked.blend  → library.blend
  └── ...
```

### Bake script

`scripts/bake_building_blend.py` — offline, run per building:

1. Read `{building}_extracted.db` (elements_meta, element_transforms, element_instances)
2. Link all unique geometry hashes from `library.blend` (one open)
3. Create one object per element: name = element_name, mesh = linked ref,
   matrix_basis from transforms, material from material_rgba
4. Organise into discipline collections (ARC, STR, MEP, ELEC, FP)
5. Apply rotation/offset corrections from `component_library.db` redirect table
6. Save as `baked/{building}_baked.blend`

```bash
python3 scripts/bake_building_blend.py Hospital    # ~2 min for 64K elements
python3 scripts/bake_building_blend.py --all       # all buildings
```

### City assembly

`scripts/assemble_city_blend.py`:

1. Create empty `city.blend`
2. For each `baked/*_baked.blend`, link its root collection
3. Apply building offset (from `site_context` table or sandbox layout)
4. Save

### User workflow

- **Explore city**: open fresh .blend → Preview sandbox DB → RTree wireframes
- **Work on building**: open `Hospital_baked.blend` directly (~5s)
- **See updated city**: open `city.blend` → all linked buildings at latest state
- **After re-extraction**: re-run `bake_building_blend.py Hospital` → city.blend
  auto-updates on next open

### What NOT to change

- RTree viewer code (S185) — stays as-is for interactive city exploration
- library.blend — untouched, mesh source only
- Extracted DBs — read-only, never modified
- component_library.db redirect table — read at bake time, same corrections

## Part C — Speed Documentation (S185 results)

Already documented in `docs/RTree.md` §S185 Performance.

## Standing rules

- Spec before code — this file is the spec
- Read the log after every run
- L0 search: NO Mesh/Disc/Shred buttons
- Bake script: offline only, never called from Blender UI
- Camera IS the selector for interactive mode (RTree)
- Baked mode: everything pre-placed, no camera selection needed

## Source files

See `docs/RTree.md` §Files — Technical Section for full path listing.

## S186 Session 1 — DONE (2026-04-14)

### Implemented
- Part A: Hierarchical drill-down (L0 city → L1 building/storeys → L2 storey/elements)
- Part A: Search suggestions ("Try: Wall, Door, ARC, *") + wildcard `*` search
- Part A: Dynamic disciplines (read from DB, not hardcoded ARC/STR/MEP/ELEC/FP)
- Part A: Single-building DB support (_has_building_column guard on all queries)
- Part A: Project name in Outliner (`{project}_RTree` instead of `Federation_RTree`)
- Part B: `scripts/bake_building_blend.py` + `scripts/assemble_city_blend.py`
- OVERNIGHT modal loader: Space=pause, ESC=cancel, progress bar, per-batch linking
- Pre-warm after 20 ticks (smooth start, then warm, then link=0ms forever)
- Per-batch mesh linking for manual +DISC buttons (no upfront pre-warm)

### Open issues for Session 2
1. **Storey-click freeze** — `FedRTreeFlyToStorey` on large buildings (LTU 125K) freezes
   for many seconds. Root cause: `fly_to_storey` queries rtree for storey bbox + 10 elements
   on 125K rows. Fix: pre-compute storey bboxes + counts at `count_building` time, cache
   in `_building_storey_bboxes`. Click just reads cache — instant.

2. **ARC "already loaded" bug** — on Duplex, pressing +ARC after MEP+STR reports all 253
   ARC guids as "already loaded" despite no guid overlap. Diagnostic logging added
   (§DEDUP lines). Needs investigation with fresh session + log evidence.

3. **Bake scripts untested** — `bake_building_blend.py` and `assemble_city_blend.py` written
   but not run. Need Blender CLI test with Duplex (small, fast) then Hospital (large).

4. **Overnight 1M sandbox** — overnight pre-warm for 108K hashes across all buildings would
   be very long. Need per-building warm strategy or skip warm for sandbox.

## Part D — Smart Overnight: Auto-Detect + Offline Bake Handoff (S186 Session 2)

### Motivation

Overnight modal loader degrades on large buildings due to Blender's O(n) scene graph:
- Hospital 63K: starts at 2.4s/batch, reaches 6.3s/batch at 12K, trending to 3.4h
- Offline `bake_building_blend.py` does the same building in 332s (5.5 min) because
  it works in a fresh empty scene with one `libraries.load()` call.

### Behaviour

#### Small buildings (< 5K elements)
No change. Modal overnight completes in seconds. No pop-up, no subprocess.

#### Large buildings (≥ 5K elements)
1. User clicks OVERNIGHT — modal starts normally (progressive, elements appear live)
2. After 2,000 elements (~40 ticks at batch=50), enough ETA data exists
3. Calculate: `online_eta` from rolling batch average, `offline_eta` = total_elements / 10,000 * 60s
4. If `online_eta > 5 × offline_eta` → show offer in N-panel:

```
  ┌─────────────────────────────────────────────┐
  │  ⏱ At this rate: ~1.8h                      │
  │  ⚡ Offline bake: ~6 min                     │
  │                                              │
  │  [SWITCH TO OFFLINE]     [KEEP GOING]        │
  └─────────────────────────────────────────────┘
```

5. **KEEP GOING** → modal continues as today, offer dismissed
6. **SWITCH TO OFFLINE** →
   a. Modal overnight halts (partial objects stay visible for context)
   b. Subprocess launches: `nice -n 10 blender --background --python bake_building_blend.py -- --db {db} --library {lib} --building {building}`
   c. Building greyed in panel: "⏳ Baking... ~6 min"
   d. Timer polls `subprocess.poll()` every 2s (microseconds, no UI impact)
   e. When subprocess exits 0:
      - Shred `Loaded_{building}_*` collections (partial overnight objects)
      - `bpy.data.libraries.load(baked/{building}_baked.blend, link=True)` → link root collection
      - Tag redraw, update discipline bars to 100%
      - Auto-save current .blend
      - Panel shows "✓ {building} complete — {N} elements ({elapsed})"
   f. If subprocess exits non-zero → panel shows error, partial objects kept

### Backward compatibility

- Small buildings (< 5K): zero code path change. Modal overnight untouched.
- User declines offer (KEEP GOING): modal continues as before. Offer not shown again.
- No offer shown if `bake_building_blend.py` is missing (graceful fallback).
- No offer shown if `library.blend` path not resolved.
- Existing `_loaded_guids`, `_loaded_collections`, `_load_progress` cleared on shred.
- Manual `+DISC` buttons unaffected — they never enter the overnight code path.
- Other buildings in the session untouched — only the baking building's collections are shredded.

### Panel greying (while subprocess runs)

For the building in `_baking_buildings`:
- +DISC buttons: disabled (greyed)
- OVERNIGHT button: replaced by "⏳ Baking... ~X min" + [CANCEL BAKE]
- Storey drill-down: disabled (building-level view only)
- Fly-to: **still works** (RTree wireframes, no objects needed)
- Pick element: **still works** (DB query, no objects needed)
- Search: **still works**

For all other buildings: fully interactive, no change.

### Bake script changes (linked refs)

`bake_building_blend.py` currently calls `m.make_local()` on every linked mesh.
Change to keep meshes linked. Result:
- Baked file: ~2-3MB (transforms + collections only) instead of 43MB
- Mesh data stays in library.blend (276MB, shared)
- Chain: session.blend → baked.blend → library.blend

### State variables (bbox_visualization.py)

```python
_baking_buildings = {}    # building_name → {process, start_time, total, offline_eta, baked_path}
_bake_offer_shown = set() # buildings that already saw the offer (don't re-show)
```

### New operators

| Operator | bl_idname | Trigger |
|---|---|---|
| FedRTreeSwitchOffline | bim.fed_rtree_switch_offline | User clicks SWITCH TO OFFLINE |
| FedRTreeCancelBake | bim.fed_rtree_cancel_bake | User clicks CANCEL BAKE |
| FedRTreeKeepGoing | bim.fed_rtree_keep_going | User clicks KEEP GOING |

### Timer: _poll_bake_subprocess

Registered when subprocess starts. Runs every 2s. Checks:
1. `process.poll()` — None = still running, 0 = done, other = error
2. If done: link baked .blend, shred partial, update panel, save .blend
3. If error: report error, keep partial objects, remove from `_baking_buildings`
4. Unregisters itself when complete.

### Log lines (§PROOF)

```
[S186] §BAKE_OFFER bld=T0_Hospital online_eta=6480s offline_eta=384s factor=16.9x
[S186] §BAKE_SWITCH bld=T0_Hospital partial=2000 cmd='nice -n 10 blender ...'
[S186] §BAKE_POLL bld=T0_Hospital pid=12345 running elapsed=120s
[S186] §BAKE_DONE bld=T0_Hospital pid=12345 elapsed=332s baked=baked/T0_Hospital_baked.blend
[S186] §BAKE_SHRED bld=T0_Hospital collections=3 objects=2000
[S186] §BAKE_LINK bld=T0_Hospital file=T0_Hospital_baked.blend collections_linked=1
[S186] §BAKE_SAVED file=sandbox.blend
[S186] §BAKE_CANCEL bld=T0_Hospital pid=12345 (user cancelled)
[S186] §BAKE_ERROR bld=T0_Hospital pid=12345 exitcode=1
```

### What NOT to change

- RTree wireframe drawing — untouched
- `+DISC` manual load (FedRTreeLoadMesh) — untouched
- `_query_viewport` camera-scoped query — untouched
- `bake_all_sandbox.sh` — untouched (CLI tool, not related)
- `assemble_city_blend.py` — untouched (separate workflow)
- `library.blend` — read-only, never modified
- Extracted DBs — read-only, never modified
