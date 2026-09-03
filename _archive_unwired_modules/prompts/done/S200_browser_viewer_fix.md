# ⚠ DO NOT REMOVE
# Scope: S200 — Browser RTree Viewer fixes (Three.js)
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: TROUBLESHOOT

## Context

S192 produced a working browser-based BIM viewer (`deploy/rtree_browser_demo.html`)
using Three.js + sql.js (SQLite in WASM). It loads sandbox_1M_extracted.db (579MB)
and component_library.db (456MB), renders building bboxes as wireframes, and
streams real geometry from BLOBs via `from_pydata()` equivalent.

Small buildings (SampleCastle, Jesse) render correctly. Large buildings (Hospital,
Terminal) have rotation/placement issues. Camera orbit is restricted.

Serve locally: `cd deploy && python3 -m http.server 8080`
Open: `http://localhost:8080/rtree_browser_demo.html`

## Known Issues

### Issue 1: Wall rotation — large buildings

**Symptom:** Walls in Hospital/Terminal all face one direction — they come out
straight, aligned to one axis, instead of following the building's L-shaped plan.

**Root cause (likely):** Extraction stores per-element rotation in `element_transforms`
(rotation_x, rotation_y, rotation_z as Euler angles). The browser viewer applies
only `center_x/y/z` translation but **ignores rotation entirely**.

**Evidence:** SampleCastle works because it's a simple rectangular plan where most
walls share the same orientation. Hospital has wings at different angles — without
rotation, all walls face the same way.

**Where in code:**
- `deploy/rtree_browser_demo.html` `streamTick()` function — mesh placement
  only does `mesh.position.set(pos.x, pos.y, pos.z)`, no rotation applied
- DB column: `element_transforms.rotation_x/y/z` (Euler radians, IFC convention)
- Blender equivalent: `mesh_utils.py` `apply_transform()` applies Euler rotation

**Fix needed:**
```javascript
// In streamTick(), after mesh.position.set():
// Query also includes rotation_x, rotation_y, rotation_z
// IFC Euler → Three.js Euler (swap Y/Z axes like position)
mesh.rotation.set(rx, rz, -ry);  // or use Euler with order 'XZY'
```

Must also update the SQL query in `streamBuilding()` to SELECT rotation columns:
```sql
SELECT m.guid, i.geometry_hash, m.material_rgba, m.discipline,
       t.center_x, t.center_y, t.center_z,
       t.rotation_x, t.rotation_y, t.rotation_z
```

### Issue 2: Camera cannot orbit above buildings

**Symptom:** Trying to view from above (bird's eye) causes the scene to
disappear. Camera feels locked to a near-horizontal angle.

**Root cause:** Two problems:
1. `controls.maxPolarAngle = Math.PI * 0.48` — prevents going above ~5° from
   horizontal. Should allow looking straight down.
2. Camera far clip or fog may be clipping at steep angles.

**Fix needed:**
```javascript
controls.maxPolarAngle = Math.PI * 0.85;  // allow nearly top-down
controls.minPolarAngle = Math.PI * 0.05;  // prevent going underneath
```

Also check:
- `camera.far = 50000` — should be enough but verify at top-down angles
- `scene.fog` — may hide distant objects when camera is high. Consider
  disabling fog or making it adaptive based on camera distance.
- Ground plane at Y=0 may be occluding if camera is below building base

### Issue 3: Coordinate convention (IFC → Three.js)

**Current mapping** (in `ifc2three()` and `blobToGeometry()`):
```
IFC (X=east, Y=north, Z=up) → Three.js (X=east, Y=up, Z=-north)
Position: ifc2three(ix, iy, iz) = { x: ix-off, y: iz-off, z: -(iy-off) }
Vertices: vArr[x, y, z] → positions[x, z, -y]
```

This should be correct but needs verification against Blender's convention.
In Blender's Direct Stream (`direct_stream.py`), the model_offset is subtracted
and IFC coords map to Blender (X, Y, Z) directly (Blender also uses Z-up).
Three.js uses Y-up, hence the swap.

**Verification approach:**
1. Pick one Hospital element with known rotation (e.g. an L-shaped wall)
2. Print its centre + rotation from DB
3. Render it standalone in Three.js and compare with Blender viewport
4. Check if rotation Euler order matches (IFC uses XYZ, Three.js default is XYZ)

## Source Files

| File | Role |
|------|------|
| `deploy/rtree_browser_demo.html` | Browser viewer (Three.js + sql.js) |
| `federation/mesh_utils.py` | Blender equivalent — `apply_transform()` reference |
| `federation/direct_stream.py` | Blender streaming — coordinate handling reference |
| `scripts/extractIFCtoDB_open.py` | How transforms are extracted from IFC |

## Debug Approach

1. Open browser console (F12) — all log lines use `[S192] §TAG` format
2. Key log lines to check:
   - `§BLOB_FETCH` — are meshes loading?
   - `§DS_START` / `§DS_QUEUED` — which building, how many elements?
   - `§FLY_TO` — camera position after fly
3. Add rotation logging: after mesh placement, log first few elements'
   rotation values to verify they're non-zero for Hospital
4. Test with a single Hospital element before batch streaming

## Exit Criteria

1. Hospital renders with walls at correct angles (L-shaped plan visible)
2. Camera can orbit from horizontal to near-top-down without clipping
3. SampleCastle still renders correctly (regression check)
4. Console shows `§PROOF` lines for rotation applied
