# ⚠ DO NOT REMOVE
# Scope: S184 — GN/DLOD geo hell fix: check-before-make_local + lod_manager library.blend path
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: SPEC — implement at session start.

## Context

RTree GPU path is production-ready (S183 DONE). GN Mode (LOD-1/2 near-camera) has two
blocking issues that must be fixed before it is usable. Fix both in this session.

---

## Blocker 1 — lod_manager BLOB path violates prebake contract

### The violation
`lod_manager.py bake_meshes_into_templates()` fetches geometry BLOBs from
`component_library.db` and calls `from_pydata()` at runtime.

This violates two rules simultaneously:
- **DLOD_SPEC §15:** "No `from_pydata()` at runtime"
- **`component_library.db` is read-only** — the S173 prebake of `library.blend` was done
  specifically to eliminate all runtime BLOB access

`FedRTreeLoadMesh` (S180) already demonstrates the correct pattern:
```python
with bpy.data.libraries.load(lib_path, link=True) as (data_from, data_to):
    available = set(data_from.meshes)
    data_to.meshes = [h for h in wanted_hashes if h in available]
```

### Fix
Replace `bake_meshes_into_templates` BLOB fetch with `library.blend` link:

```python
def bake_meshes_into_templates(self, geometry_hashes, lib_path):
    """Load template meshes from library.blend (not component_library.db BLOBs)."""
    import bpy
    already = {m.name for m in bpy.data.meshes}
    to_link = [h for h in geometry_hashes if h not in already]
    if to_link:
        with bpy.data.libraries.load(lib_path, link=True) as (df, dt):
            available = set(df.meshes)
            dt.meshes = [h for h in to_link if h in available]
    # Return dict: hash → mesh object (newly linked or already present)
    result = {}
    for h in geometry_hashes:
        m = bpy.data.meshes.get(h)
        if m:
            result[h] = m
        else:
            print(f"[S184] §WARN_MISSING_HASH {h[:12]} not in library.blend")
    return result
```

Also: move `geometry_hash_redirect` resolution to BEFORE the blend lookup.
The redirect currently lives inside `fetch_blobs_from_library` — once BLOB fetch
is gone, redirect must happen earlier (in the caller, before building the hash list).

Log: `§PROOF BAKE_FROM_BLEND hashes_requested=N linked=M reused=K missing=J`

---

## Blocker 2 — GN geo hell: check-before-make_local

### Why geo hell happens in GN mode
When DLOD promotes an element from LOD-0 (bbox proxy) to LOD-1 (real mesh), it calls
`mesh.make_local()` to copy the linked mesh from library arena into scene memory so GN
gets a direct pointer (not a library dereference evaluated at 60fps).

If `make_local()` is called on a mesh whose name already exists in scene memory
(from a previous promotion cycle), Blender renames the incoming copy to `hash.001`.
The `instance_index` table was built using the original hash name → lookup fails →
element appears at wrong mesh or origin. Geo hash hell.

### Fix — check before promote
In `dlod_handler.py`, the LOD-0→LOD-1 promotion loop:

```python
def _promote_to_lod1(self, hash_list):
    """Promote template meshes from linked → local. Safe on repeated calls."""
    for h in hash_list:
        existing = bpy.data.meshes.get(h)
        if existing and existing.library is None:
            # Already local from a previous promotion — reuse, skip make_local()
            continue
        linked = bpy.data.meshes.get(h)
        if linked is None:
            print(f"[S184] §WARN_NOT_LINKED {h[:12]} — not in bpy.data.meshes")
            continue
        linked.make_local()
        # Post-promote assertion — catch silent rename immediately
        local = bpy.data.meshes.get(h)
        if local is None or local.library is not None:
            print(f"[S184] §GEO_HASH_HELL {h[:12]} — make_local() renamed or failed")
        else:
            pass  # clean promotion
```

**Why this works:**
- DLOD only promotes ~200-500 near-camera templates at a time → small local pool
- Once local, stays local for the session → no repeated promote/demote churn
- Check-before-promote eliminates the only collision vector (same name promoted twice)
- Assertion fires immediately if Blender renamed anything → detectable before placement

Log: `§PROOF PROMOTE_LOD1 promoted=N reused=K geo_hell=J`

---

## Files to change

| File | Change |
|------|--------|
| `federation/loading/stage2_library_linker.py` or `lod_manager.py` | Replace BLOB fetch with `library.blend` link (Blocker 1) |
| `federation/dlod_handler.py` | Add `_promote_to_lod1` check-before-make_local (Blocker 2) |

Read both files fully before changing anything.

---

## Verification sequence

1. Run `bpy.ops.bim.fed_preview()` → RTree loads (must still be <13s, unchanged)
2. Move camera within 100m of a building → GN kicks in → check log for `§PROOF PROMOTE_LOD1`
3. Move camera away and back → verify reused=N (not re-promoted)
4. Check for any `§GEO_HASH_HELL` lines → must be zero
5. Check for any `§WARN_MISSING_HASH` → investigate if non-zero

---

## What NOT to change

- `bbox_visualization.py` RTree GPU path — untouched
- `FedRTreeLoadMesh` stingy loader — untouched (already correct pattern)
- `component_library.db` — never write, never read BLOBs from here
- GN chunk structure (CHUNK_SIZE=2000, sub-collections) — untouched
- Halt mode in `dlod_handler.py` — untouched

---

## Standing rules

- Spec before code — this file is the spec
- Read the log after every run
- No `from_pydata()` at runtime — ever
- No BLOB reads from `component_library.db` at runtime — ever
- Witnesses prove; assertions are mandatory on every promote
