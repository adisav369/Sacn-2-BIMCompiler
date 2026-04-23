# Frame Semantics — `frame_semantic.py`

**Spec:** `docs/Enterprise.md` §Appendix: 5M Scale Path
**Drop into:** `bonsai/bim/module/federation/frame_semantic.py`
**Register in:** `federation/__init__.py` (classes list + register/unregister)

## Intent

Extend Blender's native `view_selected` (`.` numpad) with DB-backed semantic framing.
Instead of "zoom to mesh bounding box", the camera frames a context derived from a
SQL query against `elements_rtree` — neighbours, discipline, or the full building.

Five levels, invocable via one operator with an `EnumProperty`:

| Level | SQL source | What gets framed |
|-------|-----------|-----------------|
| BBOX | `elements_rtree` single guid | The selected element only |
| CONTEXT | R-tree radius neighbours | Selected + all within N metres |
| HIERARCHY | `parent_guid` recursive CTE | Selected + all children (see caveat below) |
| DISCIPLINE | `elements_meta.discipline` | All same-discipline elements in model |
| SUPPLY | *(future — schema TBD)* | All elements from same vendor |

---

## Codebase facts the coder must respect

### DB connection
```python
from pathlib import Path   # REQUIRED — DeepSeek omitted this

def get_db_connection():
    props = bpy.context.scene.BIMFederationProperties
    raw = props.federation_database_path          # correct prop name
    db_path = bpy.path.abspath(raw) if raw else None
    if db_path and Path(db_path).exists():
        return sqlite3.connect(db_path)
    return None
```

### GUID on Blender objects
Federation objects carry `obj['guid']` (set in `blend_cache.py`).
Some older helpers also use `obj.get('federation_guid')`.
**Do NOT fall back to `geometry_hash`** — that is a mesh identity key, not a spatial key.

```python
guid = obj.get('guid') or obj.get('federation_guid')
```

### Units — DB is millimetres, Blender is metres
`elements_rtree` stores minX/maxX/minY/maxY/minZ/maxZ **in millimetres**.
All conversions:
- DB → Blender viewport: divide by 1000
- Radius parameter (metres) → SQL comparison: multiply by 1000

### Camera framing — use `view_location` / `view_distance`
Do NOT manipulate `region_3d.view_matrix` directly — fragile and often broken.
Correct Blender API:
```python
region_3d.view_location = center_world   # Vector in metres
region_3d.view_distance = distance_m     # float in metres
```

---

## SQL queries (corrected for our schema)

### BBOX
```python
def get_element_bbox(conn, guid):
    row = conn.execute("""
        SELECT r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
        FROM elements_meta m
        JOIN elements_rtree r ON m.id = r.id
        WHERE m.guid = ?
    """, (guid,)).fetchone()
    if not row:
        return None
    # Convert mm → m
    return {k: v / 1000.0 for k, v in zip(
        ('minX','maxX','minY','maxY','minZ','maxZ'), row)}
```

### CONTEXT (radius in metres, DB in mm)
```python
def get_context_bbox(conn, guid, radius_m=50.0):
    radius_mm = radius_m * 1000.0
    rows = conn.execute("""
        SELECT r2.minX, r2.maxX, r2.minY, r2.maxY, r2.minZ, r2.maxZ
        FROM elements_meta m1
        JOIN elements_rtree r1 ON m1.id = r1.id
        JOIN elements_rtree r2 ON
            r2.minX <= r1.maxX + :radius AND r2.maxX >= r1.minX - :radius
            AND r2.minY <= r1.maxY + :radius AND r2.maxY >= r1.minY - :radius
            AND r2.minZ <= r1.maxZ + :radius AND r2.maxZ >= r1.minZ - :radius
        WHERE m1.guid = :selected
    """, {'selected': guid, 'radius': radius_mm}).fetchall()
    if not rows:
        return None
    return {
        'minX': min(r[0] for r in rows) / 1000.0,
        'maxX': max(r[1] for r in rows) / 1000.0,
        'minY': min(r[2] for r in rows) / 1000.0,
        'maxY': max(r[3] for r in rows) / 1000.0,
        'minZ': min(r[4] for r in rows) / 1000.0,
        'maxZ': max(r[5] for r in rows) / 1000.0,
        'count': len(rows)
    }
```

### DISCIPLINE
```python
def get_discipline_bbox(conn, guid):
    disc_row = conn.execute(
        "SELECT discipline FROM elements_meta WHERE guid = ?", (guid,)
    ).fetchone()
    if not disc_row:
        return None
    discipline = disc_row[0]
    rows = conn.execute("""
        SELECT r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
        FROM elements_meta m
        JOIN elements_rtree r ON m.id = r.id
        WHERE m.discipline = ?
    """, (discipline,)).fetchall()
    if not rows:
        return None
    return {
        'minX': min(r[0] for r in rows) / 1000.0,
        'maxX': max(r[1] for r in rows) / 1000.0,
        'minY': min(r[2] for r in rows) / 1000.0,
        'maxY': max(r[3] for r in rows) / 1000.0,
        'minZ': min(r[4] for r in rows) / 1000.0,
        'maxZ': max(r[5] for r in rows) / 1000.0,
        'discipline': discipline,
        'count': len(rows)
    }
```

### HIERARCHY — caveat
The recursive CTE requires `parent_guid` in `elements_meta`. **This column does not
exist in the current schema.** Either skip this level for now or add the column as a
migration before implementing. If skipped, remove HIERARCHY from the EnumProperty items.

### SUPPLY CHAIN — not yet
`cost_table` and `bom_explosion` do not exist in `extracted.db`. Vendor/BOM data
lives in the ERP DB (`M_BOM`, `M_BOM_Line`). Remove the SUPPLY level from this
first implementation. Leave it as a comment stub.

---

## Camera framing utility (corrected)

```python
from mathutils import Vector

def frame_camera_to_bbox(context, bbox):
    """Frame viewport to an axis-aligned bbox (all values in metres)."""
    region_3d = context.region_data
    if not region_3d or not bbox:
        return False

    center = Vector((
        (bbox['minX'] + bbox['maxX']) / 2,
        (bbox['minY'] + bbox['maxY']) / 2,
        (bbox['minZ'] + bbox['maxZ']) / 2,
    ))
    size = max(
        bbox['maxX'] - bbox['minX'],
        bbox['maxY'] - bbox['minY'],
        bbox['maxZ'] - bbox['minZ'],
    )

    region_3d.view_location = center
    region_3d.view_distance = max(size * 1.2, 0.1)   # avoid zero-distance
    return True
```

---

## Operator skeleton

```python
class BIM_OT_frame_semantic(Operator):
    bl_idname = "bim.frame_semantic"
    bl_label = "Frame Semantic"
    bl_options = {'REGISTER', 'UNDO'}

    level: EnumProperty(
        name="Level",
        items=[
            ('BBOX',       "Element",    "Frame the selected element"),
            ('CONTEXT',    "Context",    "Selected + neighbours within radius"),
            ('DISCIPLINE', "Discipline", "All same-discipline elements"),
            # HIERARCHY and SUPPLY reserved — schema not ready
        ],
        default='CONTEXT'
    )
    radius: bpy.props.FloatProperty(name="Radius (m)", default=50.0, min=1.0)

    def execute(self, context):
        obj = context.active_object
        if not obj:
            self.report({'WARNING'}, "No active object")
            return {'CANCELLED'}

        guid = obj.get('guid') or obj.get('federation_guid')
        if not guid:
            self.report({'WARNING'}, f"{obj.name} has no guid property")
            bpy.ops.view3d.view_selected()
            return {'FINISHED'}

        conn = get_db_connection()
        if not conn:
            self.report({'WARNING'}, "No federation DB connected — using Blender fallback")
            bpy.ops.view3d.view_selected()
            return {'FINISHED'}

        bbox = None
        if self.level == 'BBOX':
            bbox = get_element_bbox(conn, guid)
        elif self.level == 'CONTEXT':
            bbox = get_context_bbox(conn, guid, self.radius)
        elif self.level == 'DISCIPLINE':
            bbox = get_discipline_bbox(conn, guid)
        conn.close()

        if bbox and frame_camera_to_bbox(context, bbox):
            count = bbox.get('count', 1)
            self.report({'INFO'}, f"[FRAME] {self.level} — {count} elements")
            return {'FINISHED'}

        self.report({'WARNING'}, "No bbox data — using Blender fallback")
        bpy.ops.view3d.view_selected()
        return {'FINISHED'}
```

---

## Progressive quick-frame operator

Repeated keypress cycles BBOX → CONTEXT → DISCIPLINE.
Use `bpy.app.timers` or a time-stamped class variable to detect double-press.
Standard Blender pattern — see existing clash operators in
`federation/clash/` for examples of stateful operators.

---

## Registration

In `federation/__init__.py`, add to the `classes` list and import from
`.frame_semantic`. No other files touched.

Suggested keybind: `Shift + .` (replaces or augments numpad `.` view_selected).
Users set this in Blender Preferences > Keymap — do not hardcode it.

---

## Logging

Every execute must print:
```
[FRAME] CONTEXT — 342 elements, radius=50m, bbox=(x1,y1,z1)→(x2,y2,z2)
[FRAME] DISCIPLINE — 1204 ACMV elements
[FRAME] BBOX — element 0e3f1a2b
[FRAME] FALLBACK — no guid on object 'Cube'
```

## Gate

- Select a federation object → `Shift+.` → camera frames context neighbours
- DB missing → fallback to `view_selected`, no crash
- Non-federation object (no guid) → fallback, warning shown
- DISCIPLINE on an ACMV object → all ACMV elements in model framed
