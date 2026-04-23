# S175 Blocker: GN Template Index Mapping

## The Problem

GN "Instance on Points" picks templates by **position in the collection** (index 0, 1, 2...).

At load time we assign `instance_index` values to each point (element). But the template collection is **dynamic** — starts with 101 objects (1 bbox + 100 near), grows as NEAR promotes more templates.

When NEAR adds template #101, all existing instance_index values that pointed at slot 101+ are now wrong — they either point at nothing or at the wrong template.

## Why This Didn't Happen Before

The old `blend_cache` GN path creates ALL templates upfront (7K objects in collection). Index 0 = first template, index 6999 = last template. Fixed forever. No dynamic adds.

We can't do that because putting 23K linked objects in the collection causes geometry hell (GN walks all of them per frame).

## Three Candidate Solutions

### A. Pre-allocate empty slots (simplest)

Create 23K template objects with **empty local meshes** (0 vertices) in the collection upfront. GN walks 23K objects per frame, but they're all local empty meshes — no library dereference. Index mapping is stable because all slots exist from the start.

On promotion: `make_local()` on the cached mesh, then `template_object.data = local_mesh` (swap the empty mesh for the real one).

**Cost:** GN walks 23K empty objects per frame. Is this fast enough? The old blend_cache does exactly this (S170 lazy templates — empty meshes filled on demand). It works at 100K+ scale. So probably yes.

**Test:** Create 23K objects with empty meshes in a collection, add GN Instance on Points → measure viewport FPS. If smooth, this is the answer.

### B. Index remap per frame (complex)

Maintain a mapping: `original_index → current_collection_position`. On every NEAR promotion, update the mapping and rewrite instance_index attributes on all GN point meshes.

**Cost:** Rewriting 63K int32 attributes per promotion event. ~1ms. Manageable but adds complexity. Risk of off-by-one errors.

### C. Named Attribute lookup (Blender 4.0+)

Instead of collection index, use a string or hash attribute to pick templates. GN has "Realize Instances" and "Instance on Points" with custom attribute matching.

**Cost:** Unknown. May not exist as a native GN node. Would need custom node group or Python-driven approach.

## Recommendation

**Option A.** It's what blend_cache already does. Empty local meshes are cheap — 0 vertices, 0 faces, just a Blender mesh datablock header (~200 bytes each). 23K × 200 bytes = 4.6 MB. GN walks 23K local pointers per frame — fast.

The pattern:
```python
# At load time: create 23K empty template objects (local, stable indices)
for i, (ghash, _) in enumerate(mesh_by_hash.items()):
    empty_mesh = bpy.data.meshes.new(f"Tpl_{ghash[:12]}")
    empty_mesh['geometry_hash'] = ghash
    tpl_obj = bpy.data.objects.new(f"Tpl_{ghash[:12]}", empty_mesh)
    templates_col.objects.link(tpl_obj)
    # hash_to_index[ghash] = i  (already set)

# instance_index values use hash_to_index directly — stable forever

# On NEAR promotion:
mesh = bpy.data.meshes.get(ghash)  # linked, in cache
mesh.make_local()                   # cache → .blend memory
template_objects[idx].data = mesh   # swap empty → real mesh
```

No index remapping. No dynamic collection changes. GN sees the same 23K objects always — most are empty (invisible), promoted ones have real geometry.

## Test Plan

1. Hospital (23K unique meshes): create 23K empty-mesh objects in collection → measure viewport FPS
2. If FPS > 30: implement Option A
3. If FPS < 10: need Option B (dynamic remap) or rethink architecture

## Reference

- `blend_cache.py` Phase 2 (S170): creates empty templates, fills lazily — same pattern
- `lod_manager.py`: manages which templates are filled — exactly the NEAR role
