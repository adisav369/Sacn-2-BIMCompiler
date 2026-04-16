# Stress Test 1M — Results

**Date:** 2026-04-10
**Spec:** `docs/StressTest_1M.md`
**Script:** `scripts/stress_loader.py`
**Seed:** 250 products × 4,000 rooms = 1,000,000 objects from `library/component_library.db`

---

## Scope clarification (what this test actually measures)

The stress loader measured **SQLite I/O only** — writing and reading records from
`sandbox.db`. Blender was never involved. This is a pre-condition test, not the
real viewport test.

The real tests are two **Blender viewport load paths**:

| Test | Path | What loads | Baseline known |
|------|------|-----------|---------------|
| **Viewport R-tree** | Stage 1 / GPU bbox overlay | Coloured wireframe boxes from `elements_rtree`, NO meshes | LTU A-House 125,997 objects → **2s** |
| **Full Load** | Stage 2 GPU instancing | LOD meshes, GPU-instanced by `geometry_hash` | LTU A-House → minutes, then smooth |

The SQLite I/O results below are useful only as a **DB read cost sub-component**
of those two paths — not as the stall point answer.

---

## SQLite I/O baseline (sandbox.db, 1M rows)

| Phase | Time | Notes |
|-------|------|-------|
| SEED query (250 rows from library.db) | 0.001s | Negligible |
| COMPILE — 1M Python dicts | 0.633s | 689 MB RAM |
| INSERT elements_meta (1M rows) | 1.606s | Plain table |
| INSERT elements_rtree (1M rows) | 4.538s | Virtual R-tree index |
| Total DB write | 6.144s | 188 MB DB |
| JSON manifest write (1M objects) | 6.098s | 151 MB — same cost as DB write |
| Peak RAM | 1,165 MB | |

### Discovery: JSON = same cost as R-tree insert
At 1M objects the JSON scene graph write (6.1s) costs identically to the R-tree
INSERT (6.1s). The plain table INSERT is 4× faster (1.6s). If S162 writes the
scene graph as a SQLite table instead of JSON, the handoff cost drops from 6s → 1.6s.

### Mesh BLOBs: no OOM because of instancing
Only 250 unique `geometry_hash` values exist across 1M objects.
`base_geometries` is keyed by hash → only 250 BLOBs written (0.033s, +17 MB).
**GPU instancing means mesh count ≠ object count.** The library-based generative
path is safe at any object count — mesh RAM is bounded by unique product count,
not total placement count.

---

## What the viewport test needs (not yet run — needs Blender)

### Test A — Viewport R-tree (no mesh)

Run `stage1_wireframes.create_wireframe_boxes()` against `sandbox.db` from inside
Blender. Measure time to first frame.

Known baseline: **125,997 objects → 2s** (LTU A-House, validated).
Expected at 1M: ~16s (linear extrapolation from 44K→0.5s and 126K→2s).

This is the stall point for the navigation-mode path. Above ~300K objects,
Stage 1 wireframe creation becomes the bottleneck because each object is a real
`bpy.data.objects` entry (8 verts, 12 edges). At 1M that is 1M Outliner entries.

### Test B — Full Load (GPU instanced meshes)

Run Stage 2 GPU instancing against `sandbox.db`. 250 unique meshes × 1M instances.
GPU instancing means Blender holds 250 mesh blocks regardless of instance count.
The cost is: 250 mesh builds + 1M transform assignments.
Expected: mesh build fast (250 only), transform assignment is the unknown wall.

---

## Performance bands (extrapolated from LTU A-House baseline + SQLite I/O)

| Scale | Stage 1 (viewport bbox) | Stage 2 (full mesh) | Verdict |
|-------|------------------------|---------------------|---------|
| 10K | ~0.1s | fast | ✅ Instant |
| 50K | ~0.5s | moderate | ✅ Fast |
| 126K | **2s** ← measured | minutes | ✅ Acceptable |
| 300K | ~5s | — | 🟡 Sluggish begins |
| 500K | ~8s | — | 🟠 Noticeable |
| 1M | ~16s | — | 🔴 Painful for Stage 1 |
| 1M (GPU overlay only) | sub-second | — | ✅ If no bpy.data.objects |

**The real threshold question:** above ~300K, Stage 1 (wireframe objects) becomes
the stall. The fix is the navigation mode from Enterprise.md Appendix — skip
`bpy.data.objects` entirely above the threshold, use pure GPU draw handler from
`bbox_visualization.py`. That path has no per-object Blender overhead.

---

## Summary

The SQLite DB is not the bottleneck. At 1M objects:
- DB read/write: 6s (measurable, improvable)
- JSON handoff: 6s (fixable → 1.6s with SQLite table)
- Blender object creation (Stage 1): ~16s estimated (the real wall)
- GPU instanced mesh load (Stage 2): unknown — needs Blender run

**Next test needed:** run `stage1_wireframes.py` against `sandbox.db` from inside
Blender and log time to first rendered frame at 1M objects.

---

## S176 Session Analysis — Library Load Bottleneck & make_local() Ownership

> **Date:** 2026-04-12
> **Evidence:** log from `DAGCompiler/lib/input/Ifc4_Revit_extracted.db` load +
> `scripts/gn_cache_log.txt` + code review of `stage2_library_linker.py`,
> `blend_cache.py`, `dlod_handler.py`, `bake_library_blend.py`
> **Context:** read alongside `internal/GN_LINK_INVESTIGATION.md` and `internal/DLOD_SPEC.md §14`

### What the log showed

Small building (11,505 elements, 6,303 unique hashes):
```
[2/6] CACHE+LOCAL MESHES from library.blend (6,303 needed, link=True+make_local)
  §PROOF LINK_TIME 8.115s for 6,303 meshes (777/s)
  §PROOF LOAD_TIME 10.55s total (read=0.05s link=8.12s instance=1.92s)
```
Step 2/6 is 77% of total load time. Rate: 777 meshes/s.

Sandbox 1M (108,440 unique hashes): stuck at step 2/6.
Extrapolated: 108,440 / 777 ≈ **140 seconds** with no log output — looks frozen,
is not crashed.

### Root cause: S176 regression in `load_library_linked`

`load_library_linked` (Full Load per-element path) added a `make_local()` loop
in S176 at step 2/6 (`stage2_library_linker.py` lines 396–403):

```python
for mesh in data_to.meshes:
    if mesh is not None and mesh.library:
        mesh.make_local()   # called on ALL N meshes, synchronously, no progress log
```

This was added to prevent GN geometry hell. **But `load_library_linked` does not
use GN.** It creates one `bpy.data.objects` per element. Per-element objects
resolve linked mesh refs once at `obj.data` assignment — not 60×/second.
The make_local() is unnecessary here and is the direct cause of the hang.

**Correct owner of make_local():** `dlod_handler.py` `_make_local_promoted()` —
called lazily on LOD-0→LOD-1 promotion, only for near-camera templates.
The Full Load path should never call make_local().

### Dead code also found in step 2/6 (lines 443–444)

```python
if len(mesh.materials) == 0:
    mesh.materials.append(None)   # fails on linked mesh (read-only)
```

`bake_library_blend.py` line 73 already does `mesh.materials.append(None)` for
every mesh baked into library.blend. Every linked mesh already has one material
slot. This guard never fires. Safe to remove.

### The bake is a compile step, not incremental — intentionally

`bake_library_blend.py` line 39: `bpy.ops.wm.read_factory_settings(use_empty=True)`
— full scene wipe, then rebuild from all hashes in `component_library.db`.

`component_library.db` IS the incremental store (geometry_extractor.py uses
`INSERT WHERE NOT EXISTS geometry_hash`). The bake flattens current DB state
to `.blend`. Relationship: `component_library.db` : `library.blend` = source : compiled binary.
Rebuilding from scratch is always correct. Incremental bake is a future speed
optimisation only — not needed for correctness.

### Memory model clarification

Both "RAM cache" and ".blend private memory" are RAM. The distinction is access path:

```
link=True  →  Blender Library Arena (RAM)
               └── library handle table: hash → pointer   ← 2-step lookup via handle

make_local()  →  Scene Arena (RAM)
                  └── vertex data (direct C pointer)       ← 1-step lookup
```

GN Collection Info evaluates every frame. 2-step vs 1-step × N templates × 60fps
is the geometry hell cost. For per-element objects (Full Load): mesh resolves once
at assignment, render uses cached GPU buffer — handle overhead is negligible.

### How S176 chunking changes the make_local() calculus

Pre-S176 (one flat collection, 108K objects):
```
GN Collection Info → walks ALL 108K objects per frame
108K × library dereference × 60fps = frozen
```

Post-S176 (CHUNK_SIZE=100):
```
GN Collection Info → walks ≤101 objects per sub-collection per frame
101 × library dereference × 60fps ≈ 0.6ms → within 16ms budget
```

The geometry hell was a **scale problem**, not a linked-vs-local problem.
Chunking caps the walk. At 101 objects per chunk, linked meshes may be fast
enough without make_local() at all — needs an FPS proof run.

GN_LINK_INVESTIGATION.md measured frozen viewport at 7K templates in one collection.
With chunks of 100, that 7K becomes 70 chunks × 101 objects = same geometry,
fraction of the per-frame cost.

### DLOD progressive LOD is unaffected

The DLOD core mechanism (distance → bucket partition → `instance_index` swap via
`foreach_set`) does not depend on whether template meshes are linked or local.
Whether a promoted template's mesh is linked or local only affects the per-frame
GN eval cost for that chunk — bounded at 101 objects either way.

DLOD still does:
- LOD-0 (>100m): bbox proxy, 8 verts, always LOCAL (created at init)
- LOD-1/2 (<100m): swap `instance_index` to real template → GN picks from chunk
- make_local() on promotion: micro-optimisation, not correctness requirement post-chunking

### Recommended actions for DLOD session

**P1 — Fix the regression (unblocks 1M sandbox):**
Remove the `make_local()` loop from `load_library_linked` step 2/6
(`stage2_library_linker.py` lines 396–403). Remove dead slot-fix at lines 443–444.
Expected result: step 2/6 drops from 140s to <5s for 108K hashes (link=True only,
no per-mesh copy loop).

**P2 — Test GN+chunk FPS with linked meshes (no make_local at all):**
Load Hospital (23K hashes → 231 chunks) with all templates linked.
Measure depsgraph eval and viewport FPS.
Proof tag: `§PROOF CHUNK_LINKED_FPS budget=16ms result=Xms`

**P3 — Decision on make_local() in DLOD promotion:**
- If P2 passes: remove `_make_local_promoted` from DLOD tick entirely.
  Project `.blend` holds zero mesh data. No strip/restore needed. Simplest.
- If P2 fails: keep `_make_local_promoted` in DLOD tick. It is the correct owner.
  But revisit whether the "disable GN during load + initial batch" steps from
  DLOD_SPEC §14 are still needed (those were written for the pre-chunk world).

**P4 — Update specs after P2/P3:**
- `docs/StressTest_1M.md` §3: chunking is now the primary geometry hell guard,
  not make_local(). Update the explanation and the status table.
- `DLOD_SPEC.md §14`: steps 3–5 (disable GN, initial batch) may be removable
  post-chunking. Confirm after P2.

---

## S176 Session Response — Regression Fixed + Chunked GN Implemented

> **Date:** 2026-04-12 (same day, DLOD session)
> **Actor:** S176 session (chunked GN + halt mode)
> **Files changed:** `stage2_library_linker.py`, `dlod_handler.py`

### P1 DONE — make_local() regression fixed

Removed the `make_local()` loop from `load_library_linked()` step 2/6.
Also removed the dead material slot fix (`mesh.materials.append(None)` — fails
on linked meshes, and `bake_library_blend.py` already adds slots at bake time).

**Current state of `load_library_linked()` step 2/6:**
- `link=True` (cache refs only, no copy)
- NO `make_local()` — per-element objects resolve linked mesh refs once at
  `obj.data` assignment. GPU buffer is cached after first render.
- NO material slot fix — linked meshes are read-only; bake adds the slot.
- Reuse loop for existing meshes simplified: no `.copy()` on linked meshes
  (they work as-is for per-element objects).

**Expected step 2/6 time:** near-instant for any hash count (was 140s for 108K).

### Chunked GN implemented (addresses the analysis §"How S176 chunking changes the make_local() calculus")

`load_library_gn()` fully rewritten with chunked sub-collections:

```
_LibGN_Templates/                    (parent, not used by GN directly)
  _LibGN_Chunk_000/                  !bbox_000 + ≤100 Tpl_* objects
  _LibGN_Chunk_001/                  !bbox_001 + ≤100 Tpl_* objects
  ...
```

- **CHUNK_SIZE = 100** — GN Collection Info walks ≤101 objects per eval
- Per-(discipline, chunk) GN objects — each has its own point mesh + modifier
- Chunk-local `instance_index` (0..100 range, 0 = bbox per chunk)
- Hospital: 231 chunks, Terminal: 72 chunks, Sandbox 1M: 1085 chunks
- Chunk-aware streamer: groups batch by chunk_id, pauses/resumes only affected
  chunk's GN modifiers. Each re-eval: ≤101 objects (~2ms, was 1-2s).

### Halt mode implemented (camera debounce in `dlod_handler.py`)

- Camera moves → mark orbiting, skip LOD computation (smooth navigation)
- Camera stops for 200ms → fire LOD with 4× batch limit (burst swap)
- New state fields: `_last_move_time`, `_halt_delay`, `_is_orbiting`,
  `_halt_batch_multiplier`, `_pre_halt_limit`
- DLOD self-test 3/3 PASS (unchanged — halt logic is in tick, not self-test)

### GN streamer still calls make_local() — P2 needed

The chunk-aware streamer (`_stream_tick()`) still does `mesh.make_local()`
before `tpl_obj.data = mesh`. Per the analysis, at CHUNK_SIZE=100 this may be
unnecessary — linked meshes may be fast enough. But removing it without an FPS
proof is risky. **P2 still needed.**

### What NOT to change in the other session

- **Do NOT add make_local() back to `load_library_linked()`** — it is not
  needed for per-element objects and causes the 140s regression.
- **Do NOT modify the chunking constants** (CHUNK_SIZE=100, `!bbox_NNN` naming)
  without rerunning `test_gn_chunk_proof.py` — the alphabetical sort assumption
  is load-bearing (`!` < `T` for slot 0 guarantee).
- **Do NOT flatten chunks back to one collection** — the GN Collection Info
  scale limit at 7K+ objects is the root cause of the viewport hang.

### Proofs

| Proof | Result | Script |
|-------|--------|--------|
| CHUNK_PARTITION (3 buildings) | 15/15 PASS | `test_gn_chunk_proof.py` |
| HALT_SUPPRESS + HALT_FIRE + HALT_IDLE | 3/3 PASS | `test_gn_chunk_proof.py` |
| DLOD_DIST + DLOD_BUCKET + DLOD_BATCH + DLOD_CHUNK | 4/4 PASS | `dlod_handler.py` self-test |
| GN_INDEX (original flat-collection) | 15/15 PASS | `test_gn_index_proof.py` |

### File locations

| File | What changed |
|------|-------------|
| `federation/loading/stage2_library_linker.py` | P1 fix + chunked `load_library_gn()` + chunk-aware streamer |
| `federation/dlod_handler.py` | Halt mode + chunk-aware DLOD swap + `dlod_init_chunked()` |
| `scripts/test_gn_chunk_proof.py` | NEW — standalone chunk + halt proof tests |

---

## S176 Update 2 — DLOD Chunk-Aware Refactoring

> **Date:** 2026-04-12 (same session, after reading this analysis)

### Why DLOD needed refactoring

The pre-S176 DLOD handler assumed a **flat collection** architecture:
- One `Fed_{disc}` GN object per discipline → `_resolve_disc_meshes()` finds it by name
- Global `instance_index` values (0-23K range) → `_cache_to_col` lookup table
- One `instance_index` attribute array per discipline mesh

Post-S176 chunking breaks all three assumptions:
- Objects named `Fed_{disc}_{chunk:03d}` → old resolver finds nothing
- `instance_index` is chunk-LOCAL (0-100 range) → global index 15000 is garbage
- Per-(disc, chunk) meshes → DLOD must write to the correct mesh

### What was done

**1. `dlod_init_chunked()`** — new init function for chunked mode.
- Accepts `disc_chunk_elements` (same structure as `load_library_gn()` step 4)
- Builds flat distance-computation arrays (same vectorized einsum)
- Stores chunk-local indices in `state.elem_local_indices`
- Stores (disc, chunk_id) → (flat_offset, count) mapping
- Sets `state._chunked = True`

**2. `_resolve_disc_meshes()`** — updated for both modes.
- Chunked: parses `Fed_{disc}_{chunk:03d}` names → `disc_chunk_meshes[(disc, chunk_id)]`
- Flat: unchanged (finds `Fed_{disc}`)

**3. `_apply_swaps_chunked()`** — new swap function.
- Iterates `disc_chunk_slices` (not `disc_slices`)
- Reads/writes per-(disc, chunk) mesh `instance_index` attribute
- LOD-0 → index 0 (!bbox per chunk), LOD-1/2 → `elem_local_indices` (already chunk-local)
- No `_cache_to_col` lookup table needed — indices are already correct
- No `_make_local_promoted` needed — chunk Collection Info walks ≤101 objects

**4. `_apply_swaps_flat()`** — extracted legacy flat swap (unchanged logic).

**5. Self-test** — added `§PROOF DLOD_CHUNK` (4/4 PASS).

### Why chunked DLOD is better

| Metric | Pre-chunk (flat) | Post-chunk |
|--------|-----------------|------------|
| Collection Info walk per re-eval | 7K-23K objects | ≤101 objects |
| `instance_index` swap cost | foreach_set on full disc array | foreach_set on small chunk array |
| GN re-eval per swap | touches ALL disc GN objects | touches only affected chunk modifiers |
| `_cache_to_col` lookup table | needed (cache→global remap) | eliminated (chunk-local = final) |
| `_make_local_promoted` | needed (geometry hell guard) | not needed (≤101 objects = fast) |

The chunked architecture makes DLOD **simpler** (no remap table) and **faster**
(each swap touches ≤101 objects instead of 7K-23K).

### Wiring DLOD to load_library_gn()

`load_library_gn()` returns `disc_chunk_elements`, `hash_to_chunk`, `hash_to_local`,
`hash_to_index` — all the data `dlod_init_chunked()` needs. The operator that calls
`load_library_gn()` should call `dlod_init_chunked()` afterwards and then
`register_handler()`.

Example wiring (in operator.py after load_library_gn returns):
```python
from ..dlod_handler import dlod_init_chunked, register_handler
dlod_init_chunked(
    disc_chunk_elements=...,  # rebuilt from return data
    hash_to_chunk=stats['hash_to_chunk'],
    hash_to_local=stats['hash_to_local'],
    hash_to_index=stats['hash_to_index'],
    db_path=db_path,
)
register_handler()
```

**UPDATE:** DLOD wiring is now DONE. `load_library_gn()` step 8/8 calls
`dlod_init_chunked()` + `register_handler()` directly. The `disc_chunk_elements`
dict is available locally (not in return dict — used in-function only).
Wrapped in try/except so streaming works even if DLOD import fails.

---

## S176 Load Session — Forensic Logging Added to step 2/6

> **Date:** 2026-04-12
> **Actor:** Library load analysis session
> **File changed:** `federation/loading/stage2_library_linker.py`

`load_library_linked()` step 2/6 now logs:
- `§LINK opening` — library path, file size (MB), hash count, RAM before link
- `§LINK done` — refs created, pure `link=True` elapsed, RAM delta (confirms no vertex copy)
- `§PROOF LINK_TIME` — split: `link=True` cost alone vs total step cost
- `§LINK mesh_by_hash` — entry count, null count, zero-vertex count
- `§LINK_SAMPLE` — 3 spot-checks: hash, verts, mat_slots, `linked=True` (confirms not copied)

These lines go to both console and the persistent log file (flush-per-line, survives freeze).
`§LINK opening` is the breadcrumb that confirms the step actually started — was previously
silent for the full duration, making 140s look like a crash.

Also added `_get_ram_mb()` helper (RSS via `resource` module) to this file — was
previously only available in `blend_cache.py`.

👍 Library load path: analysis complete, regression fixed, forensic logging in place. Ready to test.

---

## S176 Blender Test Results — Two Runs

> **Date:** 2026-04-12 14:25 and 14:57
> **Machine:** i5-13500HX 20T, 30GB RAM, RTX 4060 8GB, NVMe SSD
> **DB:** sandbox_1M_extracted.db (1,061,736 elements, 108,440 hashes)

### Run 1: CHUNK_SIZE=100

```
TOTAL: 478.42s
GN objects: 3,458 (3,458 disc×chunk pairs)
Chunks: 1,085
Step 2 (library parse): 365s
Step 4 (GN build): 87.10s ← bottleneck: 3,458 bpy objects + modifiers
Step 5 (GN enable): 4.999s
.blend size: 18MB
Viewport lag: 10+ seconds per interaction
Geo hell: NO
```

### Run 2: CHUNK_SIZE=2000

```
TOTAL: 392.04s (86s faster)
GN objects: 258 (258 disc×chunk pairs) — 13x fewer
Chunks: 55
Step 2 (library parse): 361s (same)
Step 4 (GN build): 8.27s ← 10.5x faster
Step 5 (GN enable): 5.338s
Viewport lag: ~5 seconds (2x improvement)
Geo hell: YES — wrong meshes at wrong positions
Slowdown over time: YES — streamer doesn't pause during orbit
```

### Saved .blend files (forensic reference)
- `DAGCompiler/lib/input/1.blend` — 18MB, CHUNK_SIZE=100, 3,458 GN objects, clean (no geo hell)
- `DAGCompiler/lib/input/Untitled.blend` — 15MB, CHUNK_SIZE=2000, 258 GN objects, HAS geo hell

### Screenshots
- `~/Pictures/Screenshots/Screenshot from 2026-04-12 15-10-51.png` — geo hell (orange meshes)
- `~/Pictures/Screenshots/Screenshot from 2026-04-12 15-07-00.png` — normal city view

### Analysis

1. **CHUNK_SIZE=2000 is 13x fewer GN objects** and viewport is 2x faster.
   But geo hell is back. Likely cause: `Tpl_{hash[:12]}` name collisions at 2000
   objects per collection. Two hashes sharing the same 12-char prefix get `.001`
   suffix → alphabetical sort shifts → wrong index.

2. **Streamer doesn't respect halt mode.** It fires every 0.5s regardless of camera
   state, triggering GN re-evals during orbit. This causes progressive slowdown
   as real meshes (many verts) replace bbox proxies (8 verts).

3. **.blend size is excellent.** 18MB for 1M elements. Linked meshes = zero mesh data.

### Open items for S178
- ~~P0: Fix geo hell at CHUNK_SIZE=2000 (name collision → use full hash)~~ DONE
- ~~P0: Streamer halt mode (pause during orbit)~~ DONE
- P1: Validate viewport <3s after fixes — **partially done, see Run 3**
- P2: FPS proof linked meshes without make_local() (S177)
- P3: Reopen test (linked mesh persistence)
- Prompt: `prompts/S178_chunk_debug_outliner.md`

### Run 3: CHUNK_SIZE=2000, T_{full_hash} fix + streamer halt (cold run)

```
TOTAL: 304.15s (174s faster than Run 1)
GN objects: 258 (258 disc×chunk pairs)
Chunks: 55
Step 2 (library parse): 285s (cold run — real improvement over 365s)
Step 4 (GN build): 5.50s
Step 5 (GN enable): 3.541s
.blend: not yet saved
Viewport lag: slightly better than Run 2 (~5s), streamer halt working
Geo hell: MOSTLY FIXED — sparse wrong meshes during slow streaming
Streamer halt: WORKING — §PROOF STREAM_HALT skipped=4 ticks during orbit
```

Screenshot: `~/Pictures/Screenshots/Screenshot from 2026-04-12 15-50-25.png`
— proper city layout, boxes where not streamed yet, a few wrong meshes sparse.

**Sparse geo hell analysis:** The `T_{full_hash}` naming fix eliminated the
bulk geo hell (name collision). The remaining sparse wrong meshes appear during
slow streaming — likely streamer and DLOD both writing `instance_index` on the
same mesh concurrently. The streamer sets chunk-local real index while DLOD
may reset to bbox (0). Race condition between two writers on the same attribute.

**KeyError bug:** `operator.py` line 1858 references `stats['link_time']` but
`load_library_gn()` returns `cache_time`. Non-fatal (try/except in operator).

### Summary across all 3 runs

| Metric | Run 1 (CS=100) | Run 2 (CS=2000 broken) | Run 3 (CS=2000 fixed) |
|--------|---------------|----------------------|---------------------|
| Total | 478s | 392s | **304s** |
| GN objects | 3,458 | 258 | 258 |
| GN build | 87s | 8.3s | 5.5s |
| GN enable | 5.0s | 5.3s | 3.5s |
| Cache parse | 365s | 361s | 285s |
| Viewport lag | 10+ sec | ~5 sec | ~5 sec |
| Geo hell | Clean | Full | Sparse |
| Streamer halt | No | No | **Yes** |
| .blend size | 18MB | 15MB | ~15MB est. |

---

## S186 — Overnight Loader & Per-Batch Linking Findings (2026-04-14)

### Per-discipline pre-warm is too expensive
- `prewarm_discipline('PLB')` on LTU: linked 13,999/13,999 unique hashes in **22,212ms** (22s freeze)
- `prewarm_discipline('VENT')` on LTU: linked 7,299/7,473 hashes in **16,524ms** (16s freeze)
- Root cause: single `bpy.data.libraries.load()` call links all hashes — one file open but O(n) mesh linking
- Per-discipline pre-warm removed from manual +DISC buttons

### Per-batch linking (no pre-warm) — viable for manual buttons
- Each +DISC press: query 100 elements → ~30 unique hashes → open library.blend → link 30 → ~0.5s
- Subsequent presses: cache fills, fewer new hashes → ~0.3s, eventually link=0ms
- No freeze, user stays in control. Trade-off: ~0.5s per press vs 0ms after full warm

### Overnight modal loader — best of both worlds
- Modal timer: 200 elements per tick, 0.1s interval
- First 20 ticks (~4K elements): per-batch linking, smooth, ~0.3s per tick
- Tick 20: full building pre-warm fires (`_prewarm_building_meshes`)
  - LTU (125K elements, ~20K unique hashes): ~30s warm
  - After warm: every tick is link=0ms, pure object creation
- Controls: Space=pause/resume, ESC=cancel, progress bar in N-panel
- Auto-resumes across sessions (objects persist in .blend, `_loaded_guids` dedup)

### Dynamic disciplines
- Old code hardcoded 5 disciplines (ARC/STR/MEP/ELEC/FP)
- LTU has 7: PLB, HEAT, HVAC, VENT, SAN, ARC, STR
- Bars, buttons, merge regex, count queries all now dynamic from `_building_disc_counts`

### Single-building DB support
- DBs without `building` column (e.g. `Duplex_extracted.db`) were completely broken
- All queries now guard with `_has_building_column` flag, detected at load time
- Synthetic building name derived from DB filename

### Open: storey-click freeze on large buildings
- `FedRTreeFlyToStorey` on LTU (125K): multi-second freeze
- Query: rtree join for storey bbox + 10 elements over full table
- Fix for S186 Session 2: pre-compute storey bboxes at `count_building` time

---

## S189 — BLOB Tessellation: library.blend Bypass (2026-04-16)

### Architecture change

All live mesh creation (Overnight modal, LOAD MESH click, bake subprocess) now
reads geometry BLOBs from `component_library.db` via SQLite indexed lookup instead
of opening `library.blend` (305MB) via `bpy.data.libraries.load()`.

| Data source | Format | Access pattern | Per-batch cost |
|-------------|--------|----------------|----------------|
| library.blend (S188) | Blender native | Open 305MB, scan catalog, copy meshes | **1-2s** |
| component_library.db (S189) | SQLite + binary BLOBs | Indexed lookup by geometry_hash | **<1ms** |

Both contain the same pre-tessellated vertices/faces. The difference is container
format: monolithic `.blend` file vs indexed database. `from_pydata()` adds ~5ms per
unique mesh, but this is paid once (mesh cache) and eliminates the 1-2s file-open cost
per batch.

### What library.blend is still used for

1. **Fat .blend reopen** — baked files contain meshes inline (Blender native format → instant GPU load)
2. **Lean distribution** — linked meshes reference library.blend (see `docs/PackageDistro.md`)
3. **Fallback** — if `component_library.db` is missing, code falls back to library.blend automatically

### Chunk-parallel bake (new)

Buildings ≥50K elements are split into up to 4 equal chunks, each baked by a
separate `blob_tessellate_worker.py` subprocess. Each chunk produces a fat `.blend`.
No merge-back — user reopens baked file directly ("BACKEND DONE. Don't Save. Reopen.").

| Building | Elements | Chunks | Expected wall time | Before (S188) |
|----------|----------|--------|--------------------|---------------|
| Duplex | 1,169 | 1 | ~3s | 6s |
| Clinic | ~14K | 1 | ~15s | ~30s |
| Hospital | 63,917 | 4 | ~30-40s | 160s |
| LTU | 125,698 | 4 | ~60s | ~5min |

### ETA formula change

S188 used library.blend-based estimate: `unique/7 × 0.005 + total × 0.001`.
S189 BLOB path is faster: `unique/7 × 0.0015 + total × 0.0005`.

### Proof tags (all timestamped HH:MM:SS.mmm)

| Tag | Fires when | Content |
|-----|-----------|---------|
| `§CACHE library_db=` | Preview load | component_library.db resolved |
| `§BLOB_SPAWN` | Worker launched | building, chunk N/M, PID |
| `§BLOB_COMPLETE` | Worker exit 0 | elapsed, elements, file size |
| `§BLOB_ALL_DONE` | All chunks done | total time, total elements |
| `§BLOB_APPEND` | Chunk linked back | merge time, object count |
| `§BAKE_POLL` | Every 30s | Progress, ETA |

### Test results

> **Populate after running tests — log timestamps are the evidence.**

| Test | Building | Elements | §BLOB_SPAWN | §BLOB_COMPLETE | §BLOB_ALL_DONE | Wall time | Notes |
|------|----------|----------|-------------|----------------|----------------|-----------|-------|
| Small bake | | | | | | | |
| Overnight | | | | | | | |
| LOAD MESH | | | | | | | |
| Hospital bake | | | | | | | |
| 1M sandbox | | | | | | | |
