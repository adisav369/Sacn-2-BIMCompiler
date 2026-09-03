# S169 — Thin .blend Save + Progressive Mesh Bake on Open

## DO NOT REMOVE
Scope: Save .blend WITHOUT mesh BLOBs (~15 MB). On open, fetch meshes from
component_library.db and bake into viewport. User never waits for save.
Read the log after every run.

You are a coder. One bounded task.

---

## Problem

Today, `create_cache_gn_instances()` bakes ~108K template meshes into
`bpy.data.meshes` and saves them in the .blend file. Result:

```
sandbox_1M_full.blend = 400-600 MB  (all meshes embedded)
save time = 30-60s (Blender serialises every mesh)
crash risk = high (Blender OOM during save of 1M-element scene)
```

The user cannot safely Ctrl+S after a Full Load. They must abort Blender
to avoid a crash-on-save.

## Architecture After S169

```
┌─────────────────────────────────────────────────────┐
│ .blend file (~15 MB, NO mesh BLOBs)                 │
│  - GN node trees (per discipline)                   │
│  - Point meshes with attributes (instance_index,    │
│    rotation, position)                               │
│  - Template OBJECT shells (name + empty mesh ref)   │
│  - Materials (PBR definitions, no textures)         │
│  - Collection hierarchy (15 disciplines + templates)│
│  - Custom property: database_path, library_path     │
│                                                     │
│  NOT saved: vertex data in template meshes          │
└─────────────────────────────────────────────────────┘
         │ save_pre: strip meshes
         │ load_post: re-bake meshes
         ▼
┌─────────────────────────────────────────────────────┐
│ component_library.db (686 MB, shared, on disk)      │
│  - component_geometries: 152K mesh BLOBs by hash    │
│  - I_Geometry_Map: element → hash mapping           │
│  - M_Product: product definitions                   │
└─────────────────────────────────────────────────────┘
```

---

## Lifecycle

### First Load (no .blend exists)
1. User clicks "Load with Geometry Instancing" on sandbox_1M.db
2. `create_cache_gn_instances()` runs as today:
   - Fetches meshes from component_library.db
   - Bakes template meshes into `bpy.data.meshes`
   - Builds GN node trees, point meshes, materials
   - Links to scene
3. **NEW:** Does NOT auto-save. Viewport is ready immediately.
4. User sees 15 discipline collections in Outliner, ARC visible by default.

### Save (Ctrl+S or auto-save)
1. `save_pre` handler fires:
   - For each mesh in `_GN_Templates` collection:
     - Store `geometry_hash` in mesh custom property (already in name `T_{hash}`)
     - Clear mesh data: `mesh.clear_geometry()`
   - Result: template objects exist but meshes are empty shells
2. Blender writes .blend — only point meshes + empty templates + GN trees
3. `save_post` handler fires:
   - Re-bake all template meshes from component_library.db
   - Viewport returns to full-fidelity immediately
4. **Net effect:** save is instant (~15 MB), viewport never flickers
   (save_pre strips → write → save_post restores, sub-second)

### Open (.blend exists)
1. Blender loads .blend — gets empty template meshes + GN trees
2. `load_post` handler fires:
   - Reads `database_path` and `library_path` from root collection properties
   - Finds `_GN_Templates` collection
   - For each template object with empty mesh:
     - Extract hash from mesh name (`T_{hash}` → hash)
     - Fetch vertices/faces from component_library.db
     - `mesh.from_pydata(verts, [], faces); mesh.update()`
   - GN instances automatically update (they reference the mesh objects)
3. Viewport shows full geometry. Progressive: meshes appear as they load.

---

## Implementation

### File: `blend_cache.py`

#### Change 1: Remove auto-save from `create_cache_gn_instances()`
```python
# Phase 7: REMOVED — no auto-save. Viewport ready, user saves when ready.
# The save_pre handler will strip meshes before any save.
log(f"VIEWPORT      ready — {total_instances:,} instances. "
    f"Save is safe (meshes stripped automatically).")
```

#### Change 2: Store library_path in root collection
```python
root_coll['library_path'] = lib_path  # alongside existing database_path
```

#### Change 3: Add mesh strip/restore functions
```python
def _strip_template_meshes(scene):
    """save_pre: clear geometry from GN template meshes."""
    templates = bpy.data.collections.get('_GN_Templates')
    if not templates:
        return
    for obj in templates.objects:
        if obj.type == 'MESH' and obj.data:
            obj.data.clear_geometry()

def _restore_template_meshes(scene):
    """save_post / load_post: re-bake meshes from component_library.db."""
    templates = bpy.data.collections.get('_GN_Templates')
    if not templates:
        return

    # Find library path from root collection
    root = bpy.data.collections.get('Federation_Cached')
    if not root:
        return
    lib_path = root.get('library_path')
    if not lib_path or not os.path.exists(lib_path):
        print(f"[S169] Cannot restore meshes: library not found at {lib_path}")
        return

    # Collect hashes that need geometry
    needs_restore = []
    for obj in templates.objects:
        if obj.type == 'MESH' and obj.data and len(obj.data.vertices) == 0:
            # Extract hash from mesh name: T_{hash[:8]} → need full hash
            needs_restore.append(obj)

    if not needs_restore:
        return

    # Batch-fetch from library
    lib_conn = sqlite3.connect(lib_path)
    hash_to_geo = {}
    # Template mesh names are T_{hash[:8]}, but we stored full hash
    # in mesh custom property or can match by prefix
    hashes = []
    for obj in needs_restore:
        h = obj.data.name.replace('T_', '')
        hashes.append(h)

    # Fetch by prefix match (hash[:8])
    BATCH = 5000
    for i in range(0, len(hashes), BATCH):
        batch = hashes[i:i+BATCH]
        placeholders = ','.join('?' * len(batch))
        rows = lib_conn.execute(
            f"SELECT SUBSTR(geometry_hash,1,8), vertices, faces "
            f"FROM component_geometries "
            f"WHERE SUBSTR(geometry_hash,1,8) IN ({placeholders})",
            batch).fetchall()
        for prefix, vblob, fblob in rows:
            hash_to_geo[prefix] = (vblob, fblob)
    lib_conn.close()

    # Restore mesh data
    restored = 0
    for obj in needs_restore:
        h = obj.data.name.replace('T_', '')
        geo = hash_to_geo.get(h)
        if geo:
            verts = unpack_vertices(geo[0])
            faces = unpack_faces(geo[1])
            obj.data.from_pydata(verts, [], faces)
            obj.data.update()
            restored += 1

    print(f"[S169] Restored {restored}/{len(needs_restore)} template meshes from library")
```

### File: `__init__.py`

#### Change 4: Register save/load handlers
```python
@persistent
def federation_save_pre(dummy):
    blend_cache._strip_template_meshes(bpy.context.scene)

@persistent
def federation_save_post(dummy):
    blend_cache._restore_template_meshes(bpy.context.scene)

@persistent
def federation_load_post_meshes(dummy):
    blend_cache._restore_template_meshes(bpy.context.scene)

def register():
    ...
    bpy.app.handlers.save_pre.append(federation_save_pre)
    bpy.app.handlers.save_post.append(federation_save_post)
    bpy.app.handlers.load_post.append(federation_load_post_meshes)

def unregister():
    ...
    # remove all three handlers
```

---

## Hash Resolution Issue

Template mesh names are `T_{hash[:8]}` (8-char prefix). The library uses
full 16-char hashes. Two options:

**Option A (recommended):** Store full hash as mesh custom property:
```python
mesh['geometry_hash'] = geom_hash  # at bake time
```
Then restore reads `obj.data['geometry_hash']` for exact library lookup.

**Option B:** Use `LIKE '{prefix}%'` query — risk of hash collision with 152K entries.

→ Use Option A. One custom property per mesh. Survives save/load.

---

## What the user sees

| Action | Before S169 | After S169 |
|--------|------------|------------|
| Full Load | 5 min bake + 30-60s save (crash risk) | 5 min bake, no save |
| Ctrl+S | 400-600 MB, 30-60s, may crash | 15 MB, <1s, safe |
| Re-open | Loads .blend instantly (meshes embedded) | Loads .blend + 30s mesh restore |
| R-tree preview | Works (bbox only) | Works (unchanged) |
| Abort after load | Must abort to avoid save | Safe to save anytime |

---

## Test plan

1. Load sandbox_1M.db → Full Load → verify viewport has geometry
2. Ctrl+S → verify .blend size < 30 MB
3. Verify viewport still has geometry after save (save_post restored)
4. Close Blender → re-open .blend → verify load_post restores meshes
5. Verify R-tree preview still works independently
6. Verify non-GN path (< 50K elements) still works (no strip for direct objects)

## Files changed

- EDIT: `federation/blend_cache.py` — strip/restore functions, remove auto-save, store library_path
- EDIT: `federation/__init__.py` — register save_pre/save_post/load_post handlers
- KEEP: `component_library.db` — read-only mesh source

## DO NOT

- Save meshes in the .blend file
- Break R-tree preview (it doesn't use template meshes)
- Strip meshes from non-federation scenes (guard on `_GN_Templates` collection)
- Auto-save after Full Load (let user decide when to save)
