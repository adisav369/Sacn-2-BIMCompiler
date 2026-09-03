# ⚠ DO NOT REMOVE
# Scope: S179 — 6-modifier architecture + Realize Instances for <1s viewport
# Read the log after every run. No claims without §PROOF log lines.

## Context

S178 fixed geo hell (full hash naming) and streamer halt. DLOD deferred until
streaming completes. Current state (Run 3):

- 258 GN modifiers (one per disc×chunk pair)
- ~5s viewport response — every depsgraph update evaluates ALL 258 modifiers
- GeoScatter uses 1-5 modifiers for 500K instances → buttery smooth
- Target: <1s viewport response at 1M elements

## Root cause of 5s lag

Blender evaluates ALL GN modifiers on every depsgraph_update_post, even if
nothing changed in that modifier's data. 258 modifiers × per-modifier overhead
= 5s per frame. This is a Blender architectural limitation (see T88332).

## Task 1: Collapse to 6 GN objects (one per discipline)

### Architecture

Current (258 objects):
```
Federation_Library_GN/
  ARC/
    Fed_ARC_000 (GN modifier → _LibGN_Chunk_000)  ← 55 objects
    Fed_ARC_001 (GN modifier → _LibGN_Chunk_001)
    ...
  STR/
    Fed_STR_000 (GN modifier → _LibGN_Chunk_000)  ← 55 objects
    ...
```

Target (6 objects):
```
Federation_Library_GN/
  ARC/
    Fed_ARC (ONE GN modifier, ONE point mesh with ALL ARC elements)
  STR/
    Fed_STR (ONE GN modifier, ONE point mesh with ALL STR elements)
  ...
```

### The Collection Info problem

ONE modifier per discipline means ONE Collection Info node reading ONE collection.
If that collection has all 23K ARC templates → 23K object walk per frame → hang.

### Solution: Realize Instances after streaming

Two-phase approach:

**Phase 1 — During streaming (bbox → real mesh swap):**
Keep the current 258-modifier architecture. Streaming needs per-chunk control
(pause/resume individual chunk modifiers). Accept ~5s lag during streaming
(user sees progress, not interacting heavily).

**Phase 2 — After streaming completes (§DONE):**
Collapse to 6 objects via Realize Instances:

```python
# In _stream_tick() §DONE branch, after DLOD registration:
import bpy

for disc_coll in parent_collection.children:
    if disc_coll.name.startswith('_LibGN'):
        continue

    # Collect all chunk objects for this discipline
    chunk_objs = [o for o in disc_coll.objects
                  if o.type == 'MESH' and any(m.type == 'NODES' for m in o.modifiers)]

    if not chunk_objs:
        continue

    # Select all chunk objects, join them
    bpy.ops.object.select_all(action='DESELECT')
    for obj in chunk_objs:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = chunk_objs[0]

    # Apply GN modifiers → realize instances into mesh
    for obj in chunk_objs:
        for mod in obj.modifiers:
            if mod.type == 'NODES':
                bpy.ops.object.modifier_apply(modifier=mod.name)

    # Join all realized meshes into one object per discipline
    bpy.ops.object.join()
    merged = bpy.context.active_object
    merged.name = f"Fed_{disc_coll.name}"
```

After realization:
- 6 mesh objects, zero GN modifiers
- Viewport response: <0.1s (plain mesh, no per-frame GN eval)
- Per-element selectability: lost (single merged mesh per discipline)
- DLOD: disabled (no instance_index to swap — everything is baked geometry)

### Alternative: Apply + keep separate (no join)

Instead of joining, just apply each GN modifier:
```python
for obj in chunk_objs:
    for mod in list(obj.modifiers):
        if mod.type == 'NODES':
            bpy.ops.object.modifier_apply(modifier=mod.name)
```

This gives 258 realized mesh objects (no GN modifiers). Depsgraph evaluates
plain meshes = fast. Each object is still separate = some selectability.
But 258 mesh objects with full geometry in memory = high RAM.

### Recommended: Hybrid — realize per discipline, keep GN for inspection

```
Viewport mode:   6 realized discipline meshes (fast orbit, <0.1s)
Inspection mode:  Re-enable GN on ONE discipline (click to inspect elements)
```

Toggle via a button: "Realize for Speed" / "Restore GN for Inspection"

## Task 2: Implement Realize on §DONE

In `_stream_tick()` §DONE branch (after DLOD deferred registration):

```python
# Phase 2: Realize Instances → 6 discipline meshes
print(f"[S179] Realizing instances for viewport speed...")
t_realize = time.time()

import bpy as _bpy
parent = _bpy.data.collections.get('Federation_Library_GN')
if parent:
    realized = 0
    for disc_coll in parent.children:
        if disc_coll.name.startswith('_LibGN'):
            continue
        for obj in disc_coll.objects:
            if obj.type != 'MESH':
                continue
            has_gn = False
            for mod in list(obj.modifiers):
                if mod.type == 'NODES':
                    has_gn = True
                    try:
                        _bpy.context.view_layer.objects.active = obj
                        obj.select_set(True)
                        _bpy.ops.object.modifier_apply(modifier=mod.name)
                        obj.select_set(False)
                        realized += 1
                    except Exception as e:
                        print(f"[S179] SKIP realize {obj.name}: {e}")

    elapsed = time.time() - t_realize
    print(f"[S179] §PROOF REALIZE {realized} modifiers applied in {elapsed:.1f}s")
    print(f"[S179] Viewport now pure mesh — <0.1s response expected")
```

**Risk:** `modifier_apply` on GN with 100K+ instances may be slow or OOM.
Test with Hospital first (64K elements), then sandbox (1M).
If OOM: skip realize, keep 258 modifiers (5s lag is acceptable fallback).

## Task 3: Measure viewport response after realize

1. Load sandbox_1M, wait for streaming §DONE + realize
2. Orbit viewport
3. Log: `§PROOF VIEWPORT_REALIZED budget=1s result=Xs`
4. Check RAM: realized meshes may use more RAM than GN instances

## Task 4 (S177 P2): make_local() removal test

Do AFTER realize works. Comment out `mesh.make_local()` in `_stream_tick()`.
If linked meshes realize correctly → remove permanently.

## Standing rules
- Read the log after every run
- Do NOT flatten chunks back to one collection
- Do NOT add make_local() back to load_library_linked
- Realize is a POST-STREAMING step — do not realize during streaming
- DLOD is deferred until streaming §DONE — do not change this
- If realize OOMs, fall back to 258-modifier architecture (still works, just 5s lag)

## Files
- `federation/loading/stage2_library_linker.py` — _stream_tick() §DONE branch
- `federation/dlod_handler.py` — DLOD deferred registration (already done)
- `federation/operator.py` — KeyError fixes (already done)
- `scripts/test_gn_chunk_proof.py` — 21/21 PASS at CHUNK_SIZE=2000

## Performance targets

| Phase | Modifiers | Response | When |
|-------|-----------|----------|------|
| Loading (steps 1-7) | 0 (disabled) | N/A | ~300s |
| Streaming | 258 (chunked) | ~5s | ~270s (108K hashes × 200/tick × 0.5s) |
| Post-realize | 0 | **<0.1s** | After §DONE |
| Inspection mode | 1 (single disc) | <1s | On demand |
