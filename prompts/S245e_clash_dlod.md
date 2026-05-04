# S245e — Clash DLOD: Lightweight Clash Analysis Mode

## Context
Full 3D scene (48K+ meshes/instances) overwhelms mobile GPU during clash analysis.
The clash workflow doesn't need the full scene — it needs bboxes + nearby detail.
R-tree spatial index is already built at load time (S245c).

## Concept: Clash Mode = DB-Direct Rendering

When user enters clash analysis (clicks clash sphere → matrix), **hide the full scene**
and render a lightweight DB-driven view:

### Level 0 — Bbox Cloud (instant, whole building)
```
SELECT guid, center_x, center_y, center_z, bbox_x, bbox_y, bbox_z
FROM element_transforms
```
- One `InstancedMesh` of `BoxGeometry` wireframes, colour-coded by discipline
- 48K wireframe boxes = 1 draw call per discipline = ~7 draw calls total
- No geometry BLOBs loaded. Pure arithmetic from DB columns.
- This IS the building — disciplines visible, spatial layout clear

### Level 1 — Clash Pairs Highlighted (instant, from clash query)
- Clashing element bboxes get solid fill (red/blue) instead of wireframe
- Non-clashing bboxes stay wireframe at low opacity
- Already have the overlap zone calculation — show it as orange box

### Level 2 — Camera Proximity LOD (R-tree powered)
As camera zooms in, query R-tree for elements near the camera target:
```sql
SELECT id FROM elements_rtree
WHERE minX <= ? AND maxX >= ?  -- camera frustum bounds
  AND minY <= ? AND maxY >= ?
  AND minZ <= ? AND maxZ >= ?
```
- Returns ~50-200 elements in the visible zone
- Load actual mesh geometry ONLY for those (from component_geometries BLOBs)
- Replace their bbox wireframe with real mesh
- As camera moves, unload far meshes back to bbox, load new nearby ones

### Level 3 — Clash Detail (on row click)
- Fly to overlap zone (already implemented)
- Load clipped red/blue meshes (already implemented)
- Nearby ~20 elements rendered as real mesh for spatial context
- Everything else stays as bbox wireframe

## Architecture

```
Full Scene Mode (current)          Clash DLOD Mode (new)
─────────────────────────          ─────────────────────
48K Mesh/InstancedMesh             7 InstancedMesh (bbox wireframes)
GPU: 5K+ draw calls                GPU: ~10 draw calls + nearby LOD
Raycast: traverse all              Raycast: R-tree lookup → O(log N)
Memory: all geometry loaded        Memory: only nearby BLOBs loaded
Mobile: laggy                      Mobile: smooth
```

## Entry/Exit
- **Enter:** Click clash sphere on info card → matrix opens → scene hides → bbox cloud appears
- **Exit:** Close matrix (X) → bbox cloud removed → scene restored
- Toggle: could add a button on the matrix header to switch between full scene and DLOD

## What Already Exists
- R-tree: `elements_rtree` virtual table, built async at load (S245c)
- Bbox data: `element_transforms` table (center + dimensions for every element)
- Disc colours: `elements_meta.discipline` → colour map already in panels.js
- Instanced bbox rendering: `streaming.js` already does this for placeholder boxes during load
- Clash queries: storey-scoped, progressive, all working (S245d)
- Fly-to + clipped meshes: working (S245c)

## What's New
1. `_enterClashMode()` — hide scene group, build bbox InstancedMesh cloud from DB
2. `_exitClashMode()` — remove bbox cloud, show scene group
3. `_updateClashLOD()` — on camera move, R-tree query for nearby, swap bbox→mesh
4. Integration with existing matrix/list/fly-to (should "just work" — they use measureGroup)

## Performance Target
- Bbox cloud build: <500ms (one DB query + one InstancedMesh per discipline)
- R-tree proximity query: <10ms (constant bounds, O(log N))
- LOD swap: <100ms per batch of 20 elements
- Mobile: smooth orbit at 30fps+ with 48K bbox wireframes

## Risk
- Bbox-only view loses visual fidelity — user can't see actual shapes
- Fix: Level 2 LOD brings back real geometry where the camera is looking
- The transition (full scene → bbox cloud) might flash — could fade

## Dependency
- R-tree must be ready before entering clash mode (already async, ~2s)
- If R-tree build fails (no rtree-sql.js), fall back to current mode (no DLOD)
