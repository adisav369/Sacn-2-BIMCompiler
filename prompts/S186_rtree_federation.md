# ⚠ DO NOT REMOVE
# Scope: S186 — RTree federated architecture + hierarchical drill-down
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: SPEC — start at session open.

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
