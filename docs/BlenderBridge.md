# BlenderBridge — Thin Pipe Between Compiler and Viewport
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>Jakarta Velocity for BIM — simple API over complex bpy internals.</b> Incremental viewport updates without reloading 48,000 elements, via Java/Python TCP bridge.
</div>

**Version:** 1.0 (2026-03-18)
**Depends on:** [BIM_Designer.md](BIM_Designer.md) §11-§12, IfcOpenShell Federation addon

> **Design principle:** Jakarta Velocity gave web devs `$customer.name`
> instead of `((HttpServletRequest)request).getSession().getAttribute(...)`.
> BlenderBridge gives BIM addon devs `bridge.place("wall", 5000, 200, 2800)`
> instead of 15 lines of `bpy.data.meshes.new()` / `bmesh` / materials.
> The thin pipe. Don't make Claude (or devs) overthink bpy.

> **What already exists:** The IfcOpenShell Federation addon provides **Full
> Load** — read entire output.db into Blender viewport. BlenderBridge does
> NOT replace this. It adds the **incremental update** layer on top: when
> the compiler patches 3 elements, the bridge patches 3 objects in the
> viewport without reloading 48,000.

---

## 1. What Federation Already Provides

The Federation addon (Item 1: Preview / Full Load) already solves:

| Capability | How | BlenderBridge touches it? |
|-----------|-----|--------------------------|
| DB → Blender mesh creation | Reads `elements_meta` + `base_geometries` + `element_instances` → creates Blender objects | **NO** — uses as-is |
| Material assignment | Reads `material_name` / `material_rgba` → creates `bpy.data.materials` | **NO** — uses as-is |
| Spatial indexing | `elements_rtree` for spatial queries | **NO** — uses as-is |
| Collection hierarchy | Storey → discipline → ifc_class tree | **NO** — uses as-is |
| IFC property display | Click element → properties panel | **NO** — uses as-is |

**BlenderBridge does NOT rewrite any of this.** Federation's full load is the
foundation. BlenderBridge adds two things on top:

1. **Incremental viewport update** — patch changed elements without full reload
2. **BIM verb shortcuts** — thin Python API for addon devs who don't know bpy

---

## 2. The Incremental Update Problem

### 2.1 Why Full Reload Is Too Slow

| Building | Elements | Full Load | Acceptable for edit cycle? |
|----------|----------|-----------|---------------------------|
| SH | 55 | <1s | Yes — full reload is fine |
| DX | 1,099 | ~3s | Marginal |
| TE | 48,428 | ~30s | No — user moved one wall |

At TE scale, the user edits a BOM line (move a wall 500mm), the compiler
recompiles in ~2s (WriteStage only), but Federation's full reload takes 30s
to recreate 48K Blender objects. The bottleneck is Blender, not the compiler.

### 2.2 Delta Application — The Thin Pipe

After incremental compile, the server pushes a `COMPILE_COMPLETE` message
with a **change manifest** — which elements changed, which are new, which
were removed.

```
← {"type":"COMPILE_COMPLETE","buildingId":"Terminal_KLIA",
    "outputDbPath":"...","elementCount":48428,
    "delta":{"added":[],"modified":["guid_1234","guid_5678"],"removed":[]}}
```

BlenderBridge applies the delta:

```python
def apply_delta(delta, db_path):
    """Patch viewport — don't rebuild everything."""
    conn = sqlite3.connect(db_path)

    # Remove deleted objects
    for guid in delta["removed"]:
        obj = bpy.data.objects.get(guid)
        if obj:
            bpy.data.objects.remove(obj, do_unlink=True)

    # Update modified objects (re-read geometry + position)
    for guid in delta["modified"]:
        obj = bpy.data.objects.get(guid)
        if obj:
            row = query_element(conn, guid)
            update_object(obj, row)       # position, scale, material

    # Add new objects (same path as Federation full load, but one at a time)
    for guid in delta["added"]:
        row = query_element(conn, guid)
        create_object(row)                # Federation's mesh creation logic

    conn.close()
```

**Key:** `update_object()` is ~5 lines (set location, scale, material).
`create_object()` reuses Federation's existing mesh creation code.
The delta is typically 1-50 elements, not 48K. Viewport update: <0.5s.

---

## 3. BIM Verb Shortcuts — The Developer Thin Pipe

### 3.1 The Problem for Addon Devs

Raw bpy for a simple BIM operation:

```python
# Place a wall — 15 lines of bpy boilerplate
import bpy, bmesh
mesh = bpy.data.meshes.new("wall_north")
bm = bmesh.new()
bmesh.ops.create_cube(bm, size=1.0)
bm.to_mesh(mesh)
bm.free()
obj = bpy.data.objects.new("wall_north", mesh)
obj.dimensions = (5.0, 0.2, 2.8)
obj.location = (2.5, 0.1, 1.4)
mat = bpy.data.materials.new("Brick")
mat.use_nodes = True
mat.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.6, 0.3, 0.15, 1.0)
obj.data.materials.append(mat)
bpy.context.scene.collection.objects.link(obj)
```

### 3.2 The Thin Pipe — Java Smart, Python Dumb

**Architecture rule:** The Python layer is a dumb executor. All logic —
what delta to apply, which elements changed, which materials to use — lives
in Java (DesignerServer), where it's DAO-queryable, testable in Eclipse, and
follows the same PO/ModelQuery patterns as the rest of the codebase.

```
Java (smart):  DesignerServer computes delta via DAO queries
               → sends {"update":"guid_123","x":5000,"material":"Brick"}

Python (dumb): bridge.update_object("guid_123", x=5000, material="Brick")
               → 3 lines of bpy
```

This means:
- **Eclipse devs** maintain the logic (Java DAO, BIM COBOL verbs, delta computation)
- **Python devs** maintain the bpy wrappers (~200 lines, each method 3-10 lines)
- No BIM logic in Python. No bpy in Java. Clean separation.

The Java server can also expose delta computation as BIM COBOL verbs:

```
DIFF OUTPUT "terminal_klia.db" SINCE "2026-03-18T01:00:00"
  → DiffPayload: {added: [], modified: ["guid_1234"], removed: []}
```

This makes delta computation auditable via W_Verb_Node, same as any
other verb execution.

### 3.3 BlenderBridge API — ~10 Verbs

Python-side API. Each method is 3-10 lines of bpy. No BIM logic.

| Verb | What it does | bpy calls it wraps |
|------|-------------|-------------------|
| `place_box(name, w, d, h, x, y, z, ...)` | Create box mesh at position with material | mesh.new, bmesh, object.new, materials, collection.link |
| `place_from_db(guid, db_path)` | Read element from output.db, create object | Federation's full element creation path |
| `update_position(name, x, y, z)` | Move existing object | obj.location = (...) |
| `update_dimensions(name, w, d, h)` | Resize existing object | obj.dimensions = (...) |
| `set_material(name, material, rgba)` | Set or create material on object | materials.new, nodes, assign |
| `remove(name)` | Delete object from scene | objects.remove, do_unlink |
| `create_collection(name, parent)` | Create collection hierarchy | collection.children.link |
| `select_by_ref(element_ref)` | Select object by BOM element_ref | objects[ref].select_set(True) |
| `add_dimension(obj_a, obj_b)` | Add dimension annotation between two objects | annotations, gpencil |
| `section_cut(plane, axis, offset)` | Create section cut at position | clip_start/end on camera or boolean modifier |
| `reload_from_db(db_path)` | Full reload via Federation path | Federation's existing full load |
| `apply_delta(delta, db_path)` | Incremental update (§2.2) | remove + update + create per delta |

**Java-side verbs** (in DesignerServer, exposed via ndjson):

| Action | Java DAO query | Returns |
|--------|---------------|---------|
| `"action":"diff"` | Compare output.db timestamps / spatial digest | Delta manifest (added/modified/removed GUIDs) |
| `"action":"elementDetail"` | Query `elements_meta` + `base_geometries` by GUID | Full element data for Python to create/update |
| `"action":"materials"` | Query distinct `material_name` / `material_rgba` | Material catalog for cache priming |

### 3.4 Thread Safety

Blender's Python API is **not thread-safe**. All bpy calls must run on the
main thread. The DesignerServer pushes compile results on a background
`threading.Thread`. BlenderBridge uses `bpy.app.timers` to schedule viewport
updates on the main thread:

```python
import bpy

class BlenderBridge:
    def __init__(self):
        self._pending_deltas = []

    def schedule_delta(self, delta, db_path):
        """Called from background thread. Queues for main thread."""
        self._pending_deltas.append((delta, db_path))
        bpy.app.timers.register(self._process_pending, first_interval=0.0)

    def _process_pending(self):
        """Runs on Blender main thread via timer."""
        while self._pending_deltas:
            delta, db_path = self._pending_deltas.pop(0)
            self.apply_delta(delta, db_path)
        return None  # don't repeat
```

This is the same pattern used by Bonsai's existing IFC import — background
parse, main-thread object creation.

---

## 4. Material Cache — Avoid Redundant Creation

Buildings reuse materials heavily (Brick appears on 200 walls, Concrete on
50 slabs). BlenderBridge caches materials by name:

```python
_material_cache = {}

def get_or_create_material(name, rgba=None):
    if name in _material_cache:
        return _material_cache[name]
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    if rgba:
        bsdf = mat.node_tree.nodes["Principled BSDF"]
        bsdf.inputs["Base Color"].default_value = rgba
    _material_cache[name] = mat
    return mat
```

On full load: 48K elements but only ~30 unique materials. Cache avoids
creating 48K material objects.

---

## 5. Mesh Instancing — Blender's Secret Weapon

Blender has native mesh instancing: 500 identical chairs share one mesh
data block. The compiler's `base_geometries` table already deduplicates
by `geometry_hash`. BlenderBridge maps this directly:

```python
_mesh_cache = {}  # geometry_hash → bpy.types.Mesh

def get_or_create_mesh(geometry_hash, vertices, faces):
    if geometry_hash in _mesh_cache:
        return _mesh_cache[geometry_hash]
    mesh = bpy.data.meshes.new(geometry_hash)
    mesh.from_pydata(vertices, [], faces)
    mesh.update()
    _mesh_cache[geometry_hash] = mesh
    return mesh
```

**Impact at TE scale:** 48K elements but ~2,500 unique geometries.
Without instancing: 48K mesh objects (~4GB RAM).
With instancing: 2,500 mesh data blocks + 48K lightweight object wrappers (~400MB RAM).

This is why the compiler's `base_geometries` + `element_instances` split
exists — it maps 1:1 to Blender's mesh instancing model.

---

## 6. Relationship to Existing Modules

```
DesignerServer (Java, TCP 9876)
    │
    ├── compile → CompilationPipeline → output.db
    │
    └── push COMPILE_COMPLETE + delta
            │
            ▼
        client.py (Python, background thread)
            │
            ├── full load? → Federation addon (existing)
            │
            └── incremental? → BlenderBridge.apply_delta()
                                    │
                                    ├── remove_object()    ← bpy thin pipe
                                    ├── update_object()    ← bpy thin pipe
                                    └── create_object()    ← Federation reuse
```

**BlenderBridge is NOT a replacement for Federation.** It is:
- A delta applicator (incremental updates)
- A thin API for addon devs (BIM verbs over bpy)
- A cache manager (materials + meshes)

Federation handles the heavy lift. BlenderBridge handles the fast path.

---

## 7. What BlenderBridge Does NOT Do

- Does NOT parse IFC files (Federation does this)
- Does NOT create output.db (compiler does this)
- Does NOT implement chooser panels (BonsaiBIMDesigner addon does this)
- Does NOT run compilation (DesignerServer does this)
- Does NOT validate geometry (DocValidate does this)
- Does NOT manage TCP connection (client.py does this)

It is **only** the bpy thin pipe + delta applicator. ~200 lines of Python.

---

*Related docs:
[BIM_Designer.md](BIM_Designer.md) §11 (BonsaiBIMDesigner module), §12 (versatility), §16 (Federation integration) |
[DocValidate.md](DocValidate.md) (validation engine) |
Federation addon: `/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/` |
Federation loader: `federation/loader.py` (3-stage progressive: wireframe → semantic → detail) |
Federation viewport ops: `bim.load_full_federation_viewport_gi` (Full Load from DB)*
