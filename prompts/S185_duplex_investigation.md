# ⚠ DO NOT REMOVE
# Scope: S185-DX — Duplex (et al) geometry corruption in library.blend
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: INVESTIGATE — dedicated session.

## Problem

Duplex and other small houses (Jasmin suspected) show blocky/flat walls in both
Full Load AND RTree+MESH. Door and window opening cutouts are not visible.
Terminal, Hospital, SampleHouse are fine with the same library.blend.

**The IFC and extraction are correct.** User confirmed Duplex was pristine in
earlier sessions. This is a library.blend bake or fill pipeline regression.

**Reference:** `docs/DuplexAnalysis.md` §S184

## Evidence (S184)

- `Duplex_extracted_original.db` — backup of pre-S184 DB (648 hashes)
- 648/648 hashes found in `component_library.db` — 100% coverage
- BLOB byte lengths match expected `vertex_count * 3 * 4` — no truncation
- Wall hashes show 24-48 vertices in DB (proper cutouts, not 8-vertex boxes)
- Yet library.blend renders them as flat boxes in viewport
- Same blocky appearance in both Full Load and sandbox RTree+MESH
- Terminal, Hospital, SampleHouse all render correctly with same library.blend

## Investigation Plan

### Step 1 — Verify in-scene mesh vs DB mesh
Load Duplex via Full Load. Select a wall object. In Blender Python console:
```python
obj = bpy.context.active_object
print(f"mesh={obj.data.name} verts={len(obj.data.vertices)} faces={len(obj.data.polygons)}")
```
Compare against DB:
```sql
SELECT vertex_count, face_count FROM component_geometries
WHERE geometry_hash = '<obj.data.name>';
```
If viewport mesh has fewer vertices → bake or link corrupted the mesh.
If same count → it's a rendering/normals/material issue.

### Step 2 — Direct from_pydata comparison
Bypass library.blend. Load one Duplex wall directly from base_geometries:
```python
import sqlite3, struct, numpy as np
conn = sqlite3.connect('DAGCompiler/lib/input/Duplex_extracted_original.db')
row = conn.execute("SELECT vertices, faces FROM base_geometries WHERE geometry_hash='5d99b5f1efe4eefe'").fetchone()
verts = np.frombuffer(row[0], dtype=np.float32).reshape(-1, 3).tolist()
faces = np.frombuffer(row[1], dtype=np.int32).reshape(-1, 3).tolist()
mesh = bpy.data.meshes.new("DX_TEST_WALL")
mesh.from_pydata(verts, [], faces)
mesh.update()
obj = bpy.data.objects.new("DX_TEST_WALL", mesh)
bpy.context.scene.collection.objects.link(obj)
```
If this mesh has proper cutouts → library.blend bake is the problem.
If this mesh is also blocky → extraction produced blocky geometry.

### Step 3 — Compare library.blend bake dates
Check when library.blend was last baked vs when Duplex was last seen pristine.
Was there a bake between those dates that degraded it?

### Step 4 — Test with standby re-bake
`library/library_s184_test.blend` may be available (bake started in S184 with
121,441 meshes including re-extracted Duplex). Swap it in and test:
```bash
mv library/library.blend library/library_pre_test.blend
mv library/library_s184_test.blend library/library.blend
```

### Step 5 — Check other affected buildings
Test these individually via Full Load to identify full scope:
- Jasmin (`AC90_Jasmin_extracted.db`)
- Niedriha (`AC90_Niedriha_extracted.db`)
- HausGH (`AC9_HausGH_extracted.db`)
- BimWhale variants
- Jesse, Molio, Schependomlaan, SampleCastle

## What NOT to change

- RTree/stingy loader code — working, proven in S184
- Terminal, Hospital, SampleHouse DBs — these work fine
- Sandbox DB — leave as is for demo

## Files

| File | Role |
|------|------|
| `docs/DuplexAnalysis.md` §S184 | Problem documentation |
| `DAGCompiler/lib/input/Duplex_extracted_original.db` | Pre-S184 backup |
| `library/component_library.db` | Geometry BLOB source for bake |
| `library/library.blend` | Current bake (Apr 12, 120K meshes) |
| `library/library_s184_test.blend` | Standby re-bake (if completed) |
| `scripts/bake_library_blend.py` | Bake script to inspect |
