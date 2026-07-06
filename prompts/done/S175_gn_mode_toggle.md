# S175 — GN + NEAR: 1M Elements in 3 Seconds

## Summary

Load 1M BIM elements into Blender viewport in ~3 seconds using:
1. **Cache** (`link=True`) — bulk-load library.blend into shared memory (2s)
2. **GN** (Instance on Points) — 13 objects instead of 1M
3. **NEAR** (progressive `make_local()`) — copy only camera-near meshes to .blend memory

Same pattern as game engines: bake offline, cache on load, stream by distance.

## Bake vs Runtime (Gaming Analogy)

| Phase | Gaming | Us |
|-------|--------|----|
| Bake (offline, once) | Asset pipeline → .pak | `bake_library_blend.py` → library.blend |
| Load (runtime, 2s) | Engine loads .pak → GPU cache | `link=True` → shared cache |
| Stream (runtime, per frame) | LOD by camera distance | NEAR `make_local()` by camera distance |

## The 3-Second Sequence

```
0.0s  Press Library button
      ├── link=True: 63K meshes from library.blend → cache (2s)
      ├── GN point clouds created (13 objects, 1M points)
      ├── GN modifiers DISABLED (prevents geometry hell)
      ├── Bbox proxies created as LOCAL meshes (8 verts each)
      └── User sees nothing yet (or R-tree wireframes if loaded)

2.0s  Cache complete
      ├── Compute initial camera position
      ├── NEAR: make_local() on ~200-500 templates near camera
      └── RAM-to-RAM copy, ~0.1ms per mesh, ~50ms total

2.5s  Enable GN modifiers
      ├── Near camera: real geometry (local meshes, fast pointers)
      ├── Far from camera: bbox proxies (local, 8 verts)
      └── Viewport is smooth from the first frame

3.0s  Interactive
      ├── Camera moves → NEAR promotes more templates via make_local()
      ├── First visit to new area: slight lag (~50ms)
      ├── Revisit same area: instant (already in .blend memory)
      └── Save .blend: local meshes persist, reopen = instant
```

## Why "Geometry Hell" Happened (and the Fix)

**Cache** meshes sit behind a library lookup gate. GN asks for mesh #4237,
Blender checks the library system — slow. 63K lookups × 60fps = frozen.

**make_local()** copies mesh from cache to .blend memory — direct pointer.
GN asks for mesh #4237, Blender says "here" — instant.

**The fix:** disable GN during cache load, make near meshes local first,
then enable GN. GN never touches cache addresses. No geometry hell.

## What NEAR Does (formerly DLOD)

Camera-distance handler running every frame:

| Distance | Tier | Geometry | Action on promotion |
|----------|------|----------|-------------------|
| >100m | LOD-0 (far) | 8-vert bbox proxy (local) | None — already local |
| 10-100m | LOD-1 (mid) | Real mesh, discipline color | `make_local()` on template |
| <10m | LOD-2 (near) | Real mesh, full materials | Same local mesh, material swap |

Transition = swap `instance_index` on GN point. Max 500 swaps per frame.
`make_local()` only called when a template is promoted for the first time.
After that, it stays local forever (even across save/reopen).

## Prerequisites

- `library/library.blend` — pre-baked meshes (38,306 meshes, 89.5 MB)
- `*_extracted.db` — meshless DBs with transforms + R-tree + hash refs
- `dlod_handler.py` — NEAR handler (written, 3/3 self-test PASS)
- `stage2_library_linker.py` — GN mode loader (written, needs link=True fix)

## Tasks

### 1. Fix GN Loader — Cache + Delayed GN Enable
- Keep `link=True` in `load_library_linked_gn()`
- Create bbox proxies as LOCAL meshes (not linked)
- Disable GN modifiers before cache load
- After cache: compute near templates, `make_local()` on them
- Enable GN modifiers — viewport smooth from first frame

### 2. Add make_local() to NEAR Handler
- In `dlod_handler.py` LOD-0 → LOD-1 promotion:
  - Check if template mesh is still linked (`mesh.library is not None`)
  - If linked: `mesh.make_local()` before swapping instance_index
  - Log: `§FINE NEAR make_local {hash} ({time}ms)`

### 3. Test on Sandbox 1M
- Load `sandbox_1M.db` (1,065,130 elements) with GN + NEAR
- Measure: cache time, make_local time, GN enable time, viewport FPS
- Log: `§PROOF NEAR_LOAD cache={X}s makelocal={Y}s total={Z}s`
- Save .blend → measure file size
- Reopen → measure load time (should be instant, meshes already local)

### 4. Test Persistence
- Load Hospital, navigate around (NEAR promotes ~500 templates)
- Save .blend
- Reopen → verify near meshes are still local, viewport smooth immediately
- Log: `§PROOF NEAR_REOPEN local={N} linked={M} viewport=smooth`

### 5. Verify Colors
- LOD-1: discipline colors (from template material)
- LOD-2: full IFC colors (material swap on near elements)
- Log: `§PROOF COLOR_VISIBLE`

## Standing Rules
- FINE logging on every operation
- `diffuse_color` trap: always set both `mat.diffuse_color` AND `bsdf.inputs["Base Color"]`
- Backward compatible: Hospital/Clinic/Terminal must work
- Read the log after every run

## Reference
- [StressTest_1M](../docs/StressTest_1M.md) — the full 1M story
- [DLOD Spec §14](../internal/DLOD_SPEC.md) — make_local() lifecycle
- [GN Link Investigation](../internal/GN_LINK_INVESTIGATION.md) — why link=True + GN froze
- [Full Loader 2 SRS](../internal/FULL_LOADER2_SRS.md) — master loader spec
