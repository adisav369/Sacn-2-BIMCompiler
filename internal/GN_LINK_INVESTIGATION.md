# S175 — GN Mode: What It Is, What Broke, What To Investigate
> **Resolution:** Progressive `make_local()` — see [DLOD_SPEC §14](DLOD_SPEC.md) and [StressTest_1M](../docs/StressTest_1M.md)

## What We Have (before S175)

**Library button** loads a building from `library.blend`:
- 23,000 pre-baked mesh shapes in library.blend (shared across all buildings)
- Each element gets its own Blender object (Hospital = 63,917 objects)
- Uses `link=False` (append) — copies meshes into memory, writable, colors work
- Result: full IFC colors, every element selectable and named in Outliner
- Downside: Outliner has 63,917 items, .blend saves are large, no DLOD

## What S175 Adds

**GN button** loads the same building from the same `library.blend`, but displays it differently:
- Same 23,000 mesh shapes from library.blend
- Instead of 63,917 objects → creates **6 objects** (one per discipline: ARC, MEP, STR, etc.)
- Each object is a GN point cloud — every point = one building element
- GN "Instance on Points" picks the right mesh shape per point via `instance_index`
- Templates (the 23,000 shapes) sit in a hidden collection
- Result: 6 Outliner items, small .blend saves, DLOD works

## What Is DLOD

Distance Level of Detail. A camera handler that runs every frame:
- Far elements → swap to bbox proxy (cheap 8-vertex box)
- Near elements → show real mesh
- Swaps the `instance_index` attribute on GN points — no mesh creation/deletion
- Only works with GN mode (per-element objects don't have instance_index)
- Already written (666 lines, self-test PASS), activates automatically when GN loads

## What Broke

When loading the 23,000 template meshes from library.blend, there are two options:

**Append (`link=False`):** Blender copies each mesh into memory.
- Load time: ~37 seconds (for 7K meshes, Terminal building)
- Viewport: smooth — GN resolves local meshes via direct memory pointers
- Materials: writable — can assign colors
- This is what the Library (per-element) button already uses successfully

**Link (`link=True`):** Blender creates a read-only reference back to library.blend.
- Load time: ~1.7 seconds (20× faster)
- Viewport: **frozen** — "geometry hell"
- Materials: read-only — cannot assign colors

S175 used `link=True` for speed. That caused two problems:
1. GN viewport froze (geometry hell)
2. Materials couldn't be assigned (read-only meshes)

## Why GN + link=True = Geometry Hell

GN "Collection Info" node evaluates every frame. It walks every object in the template collection to build an indexable list of meshes.

- **Local mesh (link=False):** Direct memory pointer. Instant lookup.
- **Linked mesh (link=True):** Library dereference through Blender's file system. Slow lookup.

With 7,000 templates × 60 fps = 420,000 library lookups per second → viewport freezes.

The old GN path in `blend_cache.py` works fine with thousands of templates because it uses local meshes (built from BLOBs via `from_pydata()`). Same GN setup, same template count — the only difference is local vs linked.

## The Fix (known working)

Change one line: `link=True` → `link=False`. GN works. DLOD works. Materials work. Costs ~37 seconds extra on first load. Meshes persist in saved .blend — reopening is instant.

## The Investigation (for future optimization)

The 37-second append cost scales with unique mesh count:
- Terminal (7K meshes): 37s
- Hospital (23K meshes): ~2 min
- Sandbox 1M (63K meshes): ~5-6 min

For a showcase with 1M elements, 5-6 minutes is not ideal. We want the 1.7-second link speed with the smooth viewport of append.

### Candidate Solutions to Test

**1. Link then make_local()**
```python
# Step 1: link=True (fast, 1.7s)
with bpy.data.libraries.load("library.blend", link=True) as (data_from, data_to):
    data_to.meshes = needed_meshes

# Step 2: convert to local (does this avoid the full append cost?)
for mesh in data_to.meshes:
    if mesh:
        mesh.make_local()
```
Question: Is `make_local()` faster than appending from scratch? Or does it do the same copy internally?

**2. Library overrides**
```python
# Link, then create override (lighter than full copy?)
obj.override_library_create()
```
Question: Do library overrides give local-equivalent GN performance without full mesh copy?

**3. Progressive append**
- Link all meshes first (1.7s — user sees model immediately via GN)
- Append meshes in background batches (1000 at a time)
- Swap linked → local per template as each batch completes
- User sees the model instantly, viewport smooths out over ~30 seconds

**4. Freeze GN evaluation**
- Only re-evaluate GN when camera moves (not every frame)
- Linked meshes would still be slow per evaluation, but evaluations drop from 60/s to ~2/s
- May be possible via depsgraph handler that toggles modifier visibility

### Questions for Blender/GN Experts

1. Does GN Collection Info re-resolve linked library references every evaluation, or is there an internal cache?
2. Is `make_local()` faster than a fresh append from the source .blend?
3. Can GN evaluation be throttled to only run on camera change?
4. Is this a known limitation? Any Blender developer discussion on linked instances + GN performance?

## Measured Numbers (Terminal building, 48K elements, 7K unique meshes)

| Metric | link=True | link=False |
|--------|-----------|------------|
| Mesh load | 1.74s | 37.05s |
| GN point cloud build | 0.05s | 0.05s |
| Total load | 2.19s | ~37s |
| Viewport FPS | <1 (frozen) | 60 (smooth) |
| Materials | Cannot assign | Full IFC colors |
| Outliner items | 6 | 6 |
| DLOD | Yes (if viewport worked) | Yes |
| .blend save size | Small (references only) | Larger (full mesh copies) |

## Reproduction

```python
import bpy

# ── link=True (fast load, broken viewport) ──
with bpy.data.libraries.load("library.blend", link=True) as (data_from, data_to):
    data_to.meshes = data_from.meshes[:7000]

col = bpy.data.collections.new("_Templates")
bpy.context.scene.collection.children.link(col)
for mesh in data_to.meshes:
    if mesh:
        obj = bpy.data.objects.new(mesh.name, mesh)
        col.objects.link(obj)

# Create GN: Collection Info → Instance on Points (standard setup)
# Result: viewport frozen

# ── link=False (slower load, working viewport) ──
# Same code but link=False — viewport smooth
```

## Environment

- Blender 5.0 (Python 3.11)
- GN: Instance on Points + Collection Info (Separate Children = True)
- Template count: 7,000–63,000 unique meshes
- library.blend: 89.5 MB, 38,306 meshes (shared across all buildings)
- OS: Linux 6.17
