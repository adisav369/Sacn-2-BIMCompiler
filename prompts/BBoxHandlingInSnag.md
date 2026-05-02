# ⚠ DO NOT REMOVE — Scope: BBox extraction, highlight, snag in walk mode. Read the log after every run.

# BBox Handling in Snag

## Status: BROWSER PATH DONE / JAVA EXTRACTOR PENDING

---

## DONE — Browser IFC Drop Path

### Geometry dedup (import_worker.js)
- `geomHash` was `el.guid` (every element unique). Now FNV-1a content hash of `vertices + indices`.
- Terminal: 48,428 → 9,394 unique hashes. DB: 481MB → 268MB (45% smaller).
- InstancedMesh batching now works (39K elements share geometry → draw calls drop from 48K to ~9K).

### IFC BoundingBox extraction (import_worker.js)
- `GetFlatMesh` returns multiple geometry entries per element including IfcBoundingBox (8 verts, 36 indices).
- Previously merged into body mesh → oversized highlight boxes on small elements.
- Now: extract bbox dimensions (`el._bboxX/Y/Z`) from the box geometry, then skip it from mesh.
- If no box representation exists, compute bbox from tessellated vertices.
- Stored in `element_transforms` as `bbox_x, bbox_y, bbox_z`.

### DB schema (import_db_builder.js)
- `element_transforms` now has 10 columns: `guid, center_x/y/z, rotation_x/y/z, bbox_x/y/z`.
- Viewer reads `bbox_x/y/z` at pick time — no `computeBoundingBox()` needed for new drops.

### Pick highlight (picking.js)
- Tries `SELECT bbox_x, bbox_y, bbox_z` first (IFC-extracted, exact).
- Falls back to `computeBoundingBox()` for older DBs without bbox columns.
- Highlight positioned via `localToWorld(localCenter)` for individual Mesh, instance matrix for InstancedMesh.
- Geometry + material disposed on every new pick (was leaking GPU objects).

### Merged mesh pick (picking.js, mobile)
- Merged mesh hit → proximity query finds nearest element by `(storey, discipline)` in DB.
- Returns real GUID → real IFC info. Snag button works. No "Merged group" shown.
- Re-stream approach tried and reverted (caused GPU stall on mobile with 40K+ individual meshes).

### Walk mode exit (walk.js)
- `stopWalkMode()` calls `flyTo(activeBuilding)` to restore camera to building overview.
- `flyTo()` guards `!A.libDb` (race condition on first load) and `buildingsRendered.has()` (no re-stream).
- Snag button hidden on walk exit.

### Streaming (streaming.js)
- Per-element disc-coloured bbox placeholders on load (max 5000 sampled for mobile performance).
- `drawBuildingBoxes()` retired (was drawing per-discipline grouped boxes).
- `clearStreamed()` disposes meshCache BLOBs + pick highlight + all materials.
- Shared geometry in bbox placeholders disposed once (was double-dispose bug causing WebGL corruption).

---

## DONE — Node.js Extractor (`scripts/extractIFC2DB.js`)

### Solution: Replace Python with Node.js/web-ifc
The Python/IfcOpenShell extractor was legacy — different tessellation engine, different hash
algorithm, recalculated bbox from vertices instead of reading IFC-native IfcBoundingBox.

New extractor: `scripts/extractIFC2DB.js` — uses web-ifc (same WASM engine as browser Drop).
Produces **identical** output to browser Drop IFC (verified: Terminal 48,428 elements, 9,394
unique hashes, 268MB — bit-for-bit match).

### What it does
- web-ifc `GetFlatMesh` → tessellates geometry
- Detects IfcBoundingBox (8 verts, 36 indices) → extracts dims, skips from body mesh
- FNV-1a content hash of centred vertices + indices → geometry dedup
- Y-up → Z-up transform, re-centre at origin, store world centre
- Skip near-white materials (>0.95 all channels) → viewer uses discipline colors
- Resolve `IfcRelAssociatesMaterial` chain for real material colors
- 4D extraction: IfcWorkSchedule, IfcTask, IfcRelSequence, IfcRelAssignsToProcess
- Auto-scale heuristic: if coords > 500m, assume mm → divide by 1000
- Output: single SQLite DB (same as browser Drop save format)

### Usage
```bash
node scripts/extractIFC2DB.js --ifc path/to/file.ifc -o output.db
```

### Large buildings (>200MB IFC, >100K elements)
web-ifc/Node.js OOMs on very large merged IFCs (e.g. LTU 425MB, 173K elements).
For these, use the Python per-discipline extractor (`scripts/extract_merge_disciplines.py`)
which processes one discipline file at a time, then add bbox columns from R-tree.

### Java path (BuildingWriter.java)
Also updated: 10-column `element_transforms` with `bbox_x/y/z` from `Placement.dx()/dy()/dz()`.
Consistent schema across all three paths (Node.js, Python, Java DAGCompiler).

### Key learnings
1. **Geometry has world-space baked into vertices (old Python path)** — center_x/y/z were 0.
   New extraction re-centres geometry at origin and stores world centre. Don't mix.
2. **IfcBoundingBox pollutes body mesh** — if merged into body, small elements get oversized
   highlight boxes. Must detect (8 verts, 36 indices) and extract separately.
3. **Near-white materials are useless** — web-ifc `geo.color` returns default surface style
   (0.969,0.969,0.969) for 85% of elements. Skip these, let viewer use discipline colors.
4. **Camera envelope from re-centred DBs** — MIN/MAX of center_x/y/z gives ~0. Use
   buildingCentres (from GROUP BY building) for camera target instead.
5. **Single DB vs two-DB split** — Drop IFC produces one DB (all tables). For OCI, same
   file served as both `_extracted.db` and `_library.db`. No split needed for single buildings.

---

## Witnesses

- `§BBOX_PLACEHOLDERS total=N shown=M step=S discs=D` — placeholder cubes drawn at stream start
- `§BBOX_CLEARED` — placeholders removed when real meshes arrive
- `§PICK merged→resolved guid=X` — merged mesh resolved to individual element
- `§FLY_TO_EARLY bld=X libDb not ready` — early click guard
- `§PICK_HL merged→element` — highlight at tap point for merged pick
- `§DB_BUILD` — import DB built with bbox columns

## Anti-Drift

- Do NOT backfill bbox by computing from vertices — extract from IFC representation
- Do NOT modify center_x/y/z when adding bbox to old DBs — geometry has baked world coords
- Do NOT remove the `computeBoundingBox()` fallback until ALL buildings have bbox columns
- Do NOT change the mobile merge logic (S232) — proximity query handles it
- Do NOT re-stream on walk mode entry — causes GPU stall on large buildings
- Do NOT set x-ray `transparent:true` without saving original — causes elements to vanish on restore
- Do NOT assume center_x/y/z MIN/MAX gives building envelope — re-centred DBs have near-zero centres
- Do NOT use Python/IfcOpenShell for new extractions — use Node.js/web-ifc (`extractIFC2DB.js`)
- For large IFCs (>200MB): use Python per-discipline path, NOT merged single-pass
