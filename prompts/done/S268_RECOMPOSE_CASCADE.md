# ⚠ DO NOT REMOVE — S268 Recompose Cascade
# Scope: Verb re-expansion + parent-child cascade on grid drag. Read the log after every run.

## Goal
When a grid line moves, elements reposition correctly — not by dumb delta-shift,
but by verb re-expansion and parent-child cascade. The BOM tree IS the constraint graph.

## Why (from S267 live testing, 2026-05-22)
- S267 proved: BOM phases work (20 phases on SC, 119→454 elements shown)
- S267 proved: grid drag shifts visible meshes (557 elements moved)
- S267 limitation: all elements get nearest-grid-delta, ignoring BOM relationships
- Result: roof slides instead of extending, walls translate instead of stretching,
  openings don't follow their host wall, tiles don't recount

## What S267 Delivered (foundation for this work)
- `verb_expand.js`: 7 verb expanders (TILE, ROUTE, FRAME, CLUSTER, SPRAY, LINE, LINE_MULTI)
- `bom_walker.js`: tree traversal via sql.js on BOM.db merged into extracted.db
- `_loadPhases`: BOM tree → phases (Structure → Openings → Finishes → Infill)
- `_buildEnvelope`: BOM root AABB envelope
- `recomposeAfterGridDrag`: nearest-delta to visible meshes (works but naive)
- BOM data merged into extracted DBs (SH, DX, SC, HI, TE)
- 106/106 tests, 5 BOM.db files in OCI

## What S268 Must Fix

### 1. Verb Re-Expansion (replace delta-shift with math)

When grid line at position X=12.0 moves to X=15.0 (delta=+3.0):

**FRAME elements** (columns at grid intersections):
```
Old: FRAME:0,5.4,10.8,12.0|0,4.8  → 8 columns
New: FRAME:0,5.4,10.8,15.0|0,4.8  → 8 columns at new X positions
```
The column at X=12.0 moves to X=15.0. Others stay. Re-expand verb with modified grid coords.

**CLUSTER elements** (walls, furniture):
```
Each entry: dx,dy,dz,w,d,h
Entries with dx near 12.0 → shift by +3.0
Entries between 10.8 and 12.0 → proportional shift (if bay-internal)
Entries outside moved range → no change
```

**TILE elements** (floor tiles, covering):
```
Old: TILE:6:4:2.0:1.5  → 24 tiles in bay [0, 12.0] × [0, 6.0]
New bay width = 15.0 → nx = ceil(15.0 / 2.0) = 8 → TILE:8:4:2.0:1.5 → 32 tiles
```
Tile COUNT changes. Need to add/remove InstancedMesh instances.

### 2. Parent-Child Cascade

BOM tree: wall → opening (IfcRelVoidsElement → IfcRelFillsElement)
When wall moves, its openings move with it. NOT by nearest-grid-delta.

**Source:** `bom_tree` table in extracted DB (from import_worker.js IfcRelVoids/Fills/Aggregates).
Also: `host_element_ref` on m_bom_line in BOM.db.

**Logic:**
```
grid_move(A, +3m)
  → wall_at_A moves +3m (CLUSTER entry near A shifts)
  → door_in_wall follows parent (bom_tree: wall→opening→door)
  → slab spanning A-B extends (AABB recalculated from new grid coords)
```

### 3. Element-to-BOM Mapping (the missing bridge)

S267 phases use GUIDs from extracted DB (correct). But recomposition needs to map
those GUIDs back to BOM lines to re-expand verbs.

**Option A:** CLUSTER verb with 7-field entries has embedded GUIDs. Only some buildings have these.
**Option B:** Position-match: verb-expanded position → nearest extracted element with same ifc_class.
**Option C:** `m_bom_line_ma` table (Material Allocation) maps bom_id+sequence+qi → GUID. Available in newer BOM.db files.

Best approach: B as default, A/C as enhancement when data available.

### 4. Roof/Slab Extension

Slabs and roofs span the full bay or envelope. When grid changes:
- Query slab's AABB from element_transforms
- Slab touches grid line A on one edge → extend that edge by delta
- This is geometry modification (change mesh scale), not just translation

For S268: scale the mesh's X or Z dimension to match new bay width.
Not pixel-perfect but visually correct.

## Files to Modify

| File | Change |
|------|--------|
| `deploy/dev/doc_canvas.js` | Replace `_recomposeIFCDrop` delegation with verb re-expansion |
| `deploy/dev/verb_expand.js` | Add `expandVerbWithGridDelta(verbRef, gridDeltas)` |
| `deploy/dev/bom_extract.js` | Query `bom_tree` table for parent-child if available |
| `deploy/dev/tests/test_s268_recompose.js` | Test verb re-expansion with grid delta on real BOM data |

## Verification
- SampleCastle: drag grid line → columns at grid intersection reposition (CLUSTER re-expand)
- SampleCastle: drag grid → doors follow their host wall (bom_tree cascade)
- Terminal: drag grid → FRAME verb re-expands → columns at new grid positions
- SampleHouse: drag grid → tile count changes (TILE verb recalculation)
- §-tagged logs prove: `§RECOMPOSE verb=FRAME old_x=12.0 new_x=15.0 delta=+3.0`

## Mathematical Foundation: `internal/RedPill.txt`

The recomposition rules follow the Parallel Time Machine maths:
- **kernel_ops log IS a causal DAG** — nodes are ops, edges are dependency
- **"Reapply, not Replay"** — grid drag applies delta to cached base, not full re-expand
- **BOM tree IS the causal graph** — wall depends on grid, opening depends on wall
- **O(delta) not O(elements)** — walk BOM once at activation, cache positions, apply deltas

The position cache built at activation:
```
guid → { baseX, baseY, baseZ, parentGuid, gridLine, tier }
```
On grid drag: traverse causal DAG from moved grid line → dependent elements only.
No full BOM re-walk. No verb re-expansion unless tile count changes.

## Session Startup
1. Read this prompt
2. Read `internal/RedPill.txt` (the mathematical framework — event sourcing + causal DAG)
3. Read `docs/NEW_FROM_REFERENCE.md` §17.10 + §17.10.1 (S267 delivery + recomposition rules)
3. Read `deploy/dev/doc_canvas.js` — the `_recomposeOOTB` function (currently bypassed)
4. Read `deploy/dev/verb_expand.js` — all 7 verb expanders
5. Read `deploy/dev/bom_walker.js` — collectLeaves, walk
6. Query: `sqlite3 library/SC_BOM.db "SELECT verb_ref, role FROM m_bom_line WHERE verb_ref LIKE 'CLUSTER:%' LIMIT 5"`

## Session Focus (user directive 2026-05-22)
**One visible, material change: grid lines attach to elements and elements follow.**
No cascade, no tile recount, no route regeneration. Just: drag grid → meshes move
correctly with that grid line. Proven by §-tagged whitebox logs, not user testing.

The S267 nearest-delta already moves meshes. The S268 improvement:
- Grid line knows which elements it governs (attach relationship, not proximity)
- Only governed elements move (not everything within 2m)
- Log proves: `§RECOMPOSE_ATTACH grid=A elements=[guid1,guid2,...] delta=+3.0`

## Out of Scope
- New geometry generation (wall polygon from boundary lines)
- IFC export
- Diagonal grids
- GPU throttling
- Save/recall sessions
- Timeline tied to Time Machine (noted as spec update, not S268 work)
- Tile recount, roof extension, route regeneration (S269+)
