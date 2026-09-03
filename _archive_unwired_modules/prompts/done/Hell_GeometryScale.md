# Hell_GeometryScale — Fix Vertex Scale Bug in extract_merge_disciplines.py

## What Went Wrong (Root Cause)

`extractIFCtoDB.py` (PRISTINE — never touch) uses:
```python
settings.set(settings.USE_WORLD_COORDS, True)
```
This makes ifcopenshell return vertices in **IFC native units** — which is **millimetres** for
IFC2x3 files authored in Swedish/German/etc. conventions (LTU A-House, FZK Haus, etc.).

At geometry extraction (lines 678–687 of extractIFCtoDB.py):
```python
verts = np.array(geo.verts, dtype=np.float64).reshape(-1, 3)   # in mm
center = (minXYZ + maxXYZ) / 2.0                                # in mm
v_centered = (verts - center).astype(np.float32)                # still mm
vblob = v_centered.tobytes()                                     # stored as mm
```

The element_transforms table stores `center_x/y/z` also in mm.

### Post-hoc ×0.001 fix only solves HALF the problem

The S148 correction applied ×0.001 to `element_transforms` and `elements_rtree`:
- `element_transforms.center_x/y/z` → now in metres ✓
- `elements_rtree` bbox → now in metres ✓
- `base_geometries.vertices` blob → **still in mm** ✗

The stage2 tessellation loader reconstructs world positions as:
```
world_pos = vertex_blob + element_center
```
= mm + metres = 1000x discrepancy → all geometry collapses to/near origin = **geometry hell**.

Preview Mode (bbox_visualization.py) works fine because it only reads `elements_rtree` (fixed).
Full mesh load fails because it reads `base_geometries` vertex blobs (not fixed).

---

## Fix Specification

**Location:** `scripts/extract_merge_disciplines.py`
**Rule:** NEVER modify `DAGCompiler/python/extractIFCtoDB.py` — it is pristine.

### Step 1 — Add `fix_vertex_scale()` function

After extracting each `tmp_db` (before merging), read the IFC unit scale and
apply it to ALL numeric data in the tmp_db.

```python
import ifcopenshell
import numpy as np
import struct

def fix_vertex_scale(tmp_db_path: Path, ifc_path: Path):
    """
    If the IFC is in mm (unit_scale = 0.001), scale all geometry
    and position data in tmp_db from mm → metres BEFORE merging.

    This fixes:
      - base_geometries.vertices (float32 blob, centred, in native IFC units)
      - element_transforms.center_x/y/z
      - elements_rtree.minX/maxX/minY/maxY/minZ/maxZ

    extractIFCtoDB.py is pristine — this correction happens post-extraction,
    in the tmp_db only, before merge_db() is called.
    """
    ifc = ifcopenshell.open(str(ifc_path))
    unit_scale = ifcopenshell.util.unit.calculate_unit_scale(ifc, "LENGTHUNIT")
    # unit_scale == 1.0 → metres (no-op)
    # unit_scale == 0.001 → millimetres (multiply by 0.001 to convert to metres)

    if abs(unit_scale - 1.0) < 1e-9:
        return  # Already in metres, nothing to do

    print(f"  → unit_scale={unit_scale} ({ifc_path.name}): scaling geometry to metres")

    conn = sqlite3.connect(str(tmp_db_path))

    # 1. Scale vertex blobs in base_geometries
    rows = conn.execute(
        "SELECT geometry_hash, vertices, vertex_count FROM base_geometries"
    ).fetchall()
    for ghash, vblob, vcount in rows:
        if vblob is None or vcount == 0:
            continue
        verts = np.frombuffer(vblob, dtype=np.float32).reshape(-1, 3)
        verts_scaled = (verts * unit_scale).astype(np.float32)
        conn.execute(
            "UPDATE base_geometries SET vertices=? WHERE geometry_hash=?",
            (verts_scaled.tobytes(), ghash)
        )

    # 2. Scale element_transforms centers
    conn.execute("""
        UPDATE element_transforms
        SET center_x = center_x * ?,
            center_y = center_y * ?,
            center_z = center_z * ?
    """, (unit_scale, unit_scale, unit_scale))

    # 3. Scale elements_rtree bbox
    conn.execute("""
        UPDATE elements_rtree
        SET minX = minX * ?, maxX = maxX * ?,
            minY = minY * ?, maxY = maxY * ?,
            minZ = minZ * ?, maxZ = maxZ * ?
    """, (unit_scale, unit_scale, unit_scale, unit_scale, unit_scale, unit_scale))

    conn.commit()
    conn.close()
    print(f"  → geometry scale fix applied ({len(rows)} geometries × {unit_scale})")
```

### Step 2 — Call it in main() after extraction, before merge

```python
# In main() loop, after result.returncode check, before override / merge_db():
fix_vertex_scale(tmp_db, ifc)   # <-- add this line

# Apply discipline override (if any) — stays unchanged
override = disc_map.get(ifc.stem)
...
merge_db(tmp_db, dst, disc)
tmp_db.unlink()
```

### Step 3 — Remove the post-hoc SQL comment from LTUAHouseAnalysis.md

The manual `×0.001 SQL` runbook step (Step 3 in LTUAHouseAnalysis.md) was a workaround
for the missing vertex fix. Once this function is in place, the ×0.001 SQL is no longer
needed for new extractions.

**Keep the runbook section** for existing DBs that were extracted before this fix:
label it "Legacy fix for DBs extracted before Hell_GeometryScale fix".

---

## Verification — How to confirm the fix worked

After extraction, check one wall's geometry:

```bash
python3 - <<'EOF'
import sqlite3, numpy as np
db = sqlite3.connect("DAGCompiler/lib/input/LTU_AHouse_extracted.db")
# Pick a wall
row = db.execute("""
    SELECT bg.geometry_hash, bg.vertices, et.center_x, et.center_y, et.center_z
    FROM elements_meta em
    JOIN element_instances ei ON ei.guid = em.guid
    JOIN base_geometries bg ON bg.geometry_hash = ei.geometry_hash
    JOIN element_transforms et ON et.guid = em.guid
    WHERE em.ifc_class = 'IfcWall' LIMIT 1
""").fetchone()
if row:
    ghash, vblob, cx, cy, cz = row
    verts = np.frombuffer(vblob, dtype=np.float32).reshape(-1,3)
    print(f"center: ({cx:.4f}, {cy:.4f}, {cz:.4f}) m")
    print(f"vertex extent: {verts.min(axis=0)} → {verts.max(axis=0)}")
    world = verts + np.array([cx, cy, cz])
    print(f"world extent: {world.min(axis=0)} → {world.max(axis=0)}")
    # ✓ Pass: world extent in 0–50m range (typical building)
    # ✗ Fail: world extent in 0–50,000 range → still mm scale
EOF
```

Expected output (after fix):
```
center: (2.14, 1.83, 1.20) m
vertex extent: [-0.1 -0.15 -1.2] → [0.1 0.15 1.2]
world extent:  [2.04  1.68  0.0] → [2.24 1.98 2.4]
```

Failure signature (geometry hell):
```
center: (2.14, 1.83, 1.20) m
vertex extent: [-100. -150. -1200.] → [100. 150. 1200.]  ← mm scale!
world extent:  [-97.86 -148.17 -1198.8] → [102.14 151.83 1201.2]
```

---

## Why NOT fix it in extractIFCtoDB.py

`extractIFCtoDB.py` is the **pristine original**. Its behaviour is:
- Correct for metre-unit IFC files (IFC4, most IFC2x3)
- Consistent with its own element_transforms (both in native units)
- Used as a learning reference and cross-check baseline

A copy that writes scale-corrected output would diverge from the original, making
cross-comparison impossible. The merger script is the right integration point:
it already reads the tmp_db before merging, so it can apply the fix without
touching the extraction logic.

---

## Imports to add at top of extract_merge_disciplines.py

```python
import numpy as np
import ifcopenshell
import ifcopenshell.util.unit
```

(ifcopenshell is already available in the pipeline environment.)

---

## LTU A-House: Re-extraction Required

The existing `DAGCompiler/lib/input/LTU_AHouse_extracted.db` has geometry hell.
It needs to be **deleted and re-extracted** after this fix is implemented:

```bash
rm DAGCompiler/lib/input/LTU_AHouse_extracted.db

python3 scripts/extract_merge_disciplines.py \
    --ifc-dir DAGCompiler/lib/input/IFC/UNMERGED \
    --pattern "LTU_AHouse_*.ifc" \
    --output DAGCompiler/lib/input/LTU_AHouse_extracted.db \
    --disc-map \
        LTU_AHouse_AIR=VENT  LTU_AHouse_DUCT=VENT \
        LTU_AHouse_COOL=HVAC LTU_AHouse_HEAT=HEAT \
        LTU_AHouse_PLB=PLB   LTU_AHouse_SAN=SAN \
        LTU_AHouse_ARC=ARC   LTU_AHouse_STR=STR \
        LTU_AHouse_VOID=VOID
```

Expected runtime: ~20 minutes. No post-hoc SQL needed after this fix.
After extraction, run the verification query above to confirm geometry is in metres.
