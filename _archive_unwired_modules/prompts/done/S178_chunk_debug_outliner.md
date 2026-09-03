# ⚠ DO NOT REMOVE
# Scope: S178 — Debug geo hell at CHUNK_SIZE=2000 + viewport optimisation
# Read the log after every run. No claims without §PROOF log lines.

## Context

S176 implemented chunked GN sub-collections and tested at two chunk sizes:

| Run | CHUNK_SIZE | GN objects | Load time | Viewport lag | Geo hell? |
|-----|-----------|------------|-----------|-------------|-----------|
| Run 1 | 100 | 3,458 | 478s | 10+ sec | No |
| Run 2 | 2000 | 258 | 392s | ~5 sec | **Yes** |

CHUNK_SIZE=2000 gives 13x fewer GN objects and 2x faster viewport — but
introduces geo hell (wrong meshes at wrong positions). See screenshots:
`~/Pictures/Screenshots/Screenshot from 2026-04-12 15-10-51.png` (geo hell, orange)
`~/Pictures/Screenshots/Screenshot from 2026-04-12 15-07-00.png` (normal view)

CHUNK_SIZE=100 was clean. The bug is in the chunk index mapping at larger sizes.

## Read before starting
- `internal/StressTest_1M_Results.md` — full S176 analysis trail + response
- `federation/loading/stage2_library_linker.py` — `load_library_gn()` (the code)
- `federation/dlod_handler.py` — chunked DLOD + halt mode
- `scripts/test_gn_chunk_proof.py` — standalone proofs (15/15 PASS at 2000, but runtime fails)
- `prompts/S177_gn_linked_fps_proof.md` — P2 FPS proof (do AFTER geo hell is fixed)

## Task 1 (P0): Debug geo hell at CHUNK_SIZE=2000

### Root cause hypothesis

The standalone proof `test_gn_chunk_proof.py` passes at CHUNK_SIZE=2000 because
it simulates the ALPHABETICAL sort correctly. But in Blender, GN Collection Info
may sort differently when a collection has 2001 objects (1 bbox + 2000 templates).

Possible causes:
1. **Name collision at Tpl_{hash[:12]}** — with 2000 objects per chunk, two hashes
   with the same 12-char prefix get `.001` suffix from Blender → alphabetical order
   shifts → index mismatch. At 100 objects this was unlikely; at 2000 it may trigger.
2. **GN Collection Info walk order differs from `sorted([o.name])`** at large counts.
3. **Streamer swaps mesh into wrong chunk's template** — hash_to_chunk mapping wrong.

### Debug steps

1. **Check name collisions:** In Blender console after load:
   ```python
   import bpy
   from collections import Counter
   for col in bpy.data.collections:
       if col.name.startswith('_LibGN_Chunk_'):
           names = [o.name for o in col.objects]
           dupes = [n for n, c in Counter(names).items() if c > 1]
           if dupes:
               print(f"COLLISION {col.name}: {dupes[:5]}")
   ```
   If collisions found → fix by using full geometry_hash as object name (max 40 chars,
   under Blender's 63-char limit).

2. **Verify index mapping matches GN order:** Pick a chunk, compare Python
   `sorted([o.name])` vs actual GN instance placement. Place camera at known
   element, check which mesh appears.

3. **Binary search chunk size:** Try CHUNK_SIZE=500 and CHUNK_SIZE=1000.
   If 500 is clean but 1000 has geo hell → the threshold tells us the cause
   (name collision probability vs GN internal limit).

4. **Log proof:** `§PROOF GEO_HELL_ROOT cause=X chunk_size=Y`

### Fix

If cause is name collision:
```python
# Current (12 chars — collision risk at 2000 objects):
tpl_obj = bpy.data.objects.new(f"Tpl_{gh[:12]}", bbox_mesh)

# Fix (full hash — no collision possible):
tpl_obj = bpy.data.objects.new(f"T_{gh}", bbox_mesh)
```
Note: `T_` instead of `Tpl_` saves 2 chars. Full SHA-like hash is ~40 chars.
`T_` + 40 = 42 chars, well under 63-char Blender limit.
Must still sort after `!bbox_NNN` (T > ! ✓).

Also update `test_gn_chunk_proof.py` to use same naming convention.

## Task 1B (P0): Streamer must respect halt mode

**Observed:** Viewport slows down over time. The streamer timer fires every 0.5s
regardless of camera state, swapping 200 meshes per tick. Each swap triggers
GN re-eval on affected chunks. As real meshes replace bbox (8 verts → hundreds),
re-eval cost grows. During orbit, these re-evals compete with viewport rendering.

**Fix:** The streamer should pause during camera orbit (same as DLOD halt mode).
Only swap meshes when camera is stationary for 200ms.

In `_stream_tick()`, check camera position before processing:
```python
# At top of _stream_tick():
cam_pos = None
try:
    for area in _bpy.context.screen.areas:
        if area.type == 'VIEW_3D':
            rv3d = area.spaces[0].region_3d
            pos = rv3d.view_matrix.inverted().translation
            cam_pos = (pos.x, pos.y, pos.z)
            break
except: pass

if cam_pos and st.get('last_cam_pos'):
    dx = sum((a-b)**2 for a,b in zip(cam_pos, st['last_cam_pos']))
    if dx > 1.0:  # camera moving — skip this tick
        st['last_cam_pos'] = cam_pos
        return STREAM_INTERVAL  # come back in 0.5s
st['last_cam_pos'] = cam_pos
```

Log: `§PROOF STREAM_HALT skipped=N ticks during orbit`

## Status after Run 3 (2026-04-12 15:41)

Tasks 1 and 1B are DONE (T_{full_hash} naming, streamer halt). Run 3 results:
- 304s total (was 478s). 258 GN objects. Streamer halt working (4 skips logged).
- **Geo hell 95% fixed.** Sparse wrong meshes remain during slow streaming.
- Likely cause: streamer and DLOD both write `instance_index` on the same
  (disc, chunk) mesh. Streamer sets chunk-local real index, DLOD may reset
  to bbox (0). Race condition between two concurrent writers.
- **KeyError:** `operator.py:1858` references `stats['link_time']` but
  `load_library_gn()` returns `cache_time`. Fix: change key or add to return dict.
- Screenshot: `~/Pictures/Screenshots/Screenshot from 2026-04-12 15-50-25.png`
- Log: `DAGCompiler/lib/input/library_link_20260412_154115.log`

## Task 2: Fix sparse geo hell (streamer/DLOD race)

The remaining sparse wrong meshes appear because:
- Streamer timer (every 0.5s): swaps `tpl_obj.data` (mesh content) in a chunk
- DLOD handler (depsgraph_update_post): writes `instance_index` attribute
- Both touch the same chunk meshes. If DLOD fires mid-stream-tick, it reads
  stale indices that the streamer just changed.

Fix options:
1. **Disable DLOD until streaming completes.** Don't call `register_handler()`
   in step 8/8. Instead, the streamer's `§DONE` callback registers DLOD.
   During streaming, all elements show their chunk-local real mesh or bbox —
   no DLOD transitions needed yet.
2. **Lock guard.** Streamer sets a flag before swap, DLOD checks it.

Option 1 is simpler and correct — DLOD is for runtime camera-distance LOD,
not useful during initial streaming when everything is being populated.

Log: `§PROOF GEO_HELL_CLEAR no wrong meshes after 60s orbit`

Also fix: `operator.py:1858` — change `stats['link_time']` to `stats['cache_time']`.

## Task 3: Validate viewport responsiveness

After race fix:
1. Load sandbox_1M via GN button
2. Navigate viewport — orbit, zoom, pan
3. Log: `§PROOF VIEWPORT_LAG budget=3s result=Xs (CHUNK_SIZE=N, M GN objects)`

## Task 3: Reduce Outliner to ~6 discipline entries

Current: Outliner shows all ~258 `Fed_{disc}_{chunk:03d}` objects.
Target: Outliner shows only 6 discipline collections, chunks collapsed inside.

The per-(disc, chunk) objects are already grouped under discipline collections.
Verify discipline visibility toggle (eye icon) hides/shows all chunks.

Log: `§PROOF OUTLINER disc_count=6 top_level_entries=N`

## Task 4 (from S177): P2 FPS proof — linked meshes without make_local()

**Do this AFTER geo hell is fixed.**

See `prompts/S177_gn_linked_fps_proof.md` for full spec. Summary:
1. Comment out `mesh.make_local()` in `_stream_tick()`
2. Load Hospital via GN button
3. Measure viewport FPS
4. Log: `§PROOF CHUNK_LINKED_FPS budget=16ms result=Xms fps=Y`

If PASS: remove make_local() from streamer permanently.
If FAIL: keep make_local(), document cost.

## Task 5: Reopen test

1. Save loaded scene
2. Close and reopen Blender
3. Check linked meshes resolve, viewport works
4. Log: `§PROOF REOPEN_LINKED size=XMB meshes=N resolved=Y/N`

## Standing rules
- Read the log after every run
- Do NOT flatten chunks back to one collection
- Do NOT add make_local() back to load_library_linked
- `!bbox_NNN` naming is load-bearing (! < T for slot 0 guarantee)
- DLOD wiring is in step 8/8 of load_library_gn() — do not remove
- CHUNK_SIZE constant is in load_library_gn() function body (line ~852)

## Files
- `federation/loading/stage2_library_linker.py` — CHUNK_SIZE, load_library_gn()
- `federation/dlod_handler.py` — halt mode, chunked swap, dlod_init_chunked()
- `scripts/test_gn_chunk_proof.py` — standalone proofs
- `internal/StressTest_1M_Results.md` — analysis trail
- Logs: `DAGCompiler/lib/input/library_link_20260412_14*.log`
- Screenshots: `~/Pictures/Screenshots/Screenshot from 2026-04-12 15-*.png`
