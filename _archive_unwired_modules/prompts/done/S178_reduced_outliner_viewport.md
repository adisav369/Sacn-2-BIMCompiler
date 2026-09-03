# ⚠ DO NOT REMOVE
# Scope: S178 — Reduced Outliner + viewport responsiveness at 1M scale
# Read the log after every run. No claims without §PROOF log lines.

## Context (from S176 Blender test)

S176 loaded sandbox_1M (1,061,736 elements) via GN+chunk successfully:
- 18MB .blend (was 329MB) — linked meshes, zero mesh data in file
- DLOD wired, streaming active, 6 disciplines
- **BUT:** 3,458 GN objects (per disc×chunk at CHUNK_SIZE=100) → 10+ second
  viewport lag on every interaction. Depsgraph evaluates all 3,458 modifiers.

**Fix applied this session:** CHUNK_SIZE bumped from 100 to 2000.
- Sandbox: 55 chunks × 6 disciplines ≈ 170 GN objects (was 3,458)
- Hospital: 12 chunks × ~4 disciplines ≈ 40 GN objects
- Collection Info walks ≤2001 objects (hang threshold was 7K+)
- Proofs: 15/15 PASS at CHUNK_SIZE=2000, DLOD 4/4 PASS

## Task 1: Validate CHUNK_SIZE=2000 in Blender

1. Load sandbox_1M via GN button (new session, don't reopen old 1.blend)
2. Watch logs for:
   - `[3/7] BUILD CHUNKED COLLECTIONS` — should show ~55 chunks
   - `[4/7] BUILD PER-(DISC,CHUNK) GN OBJECTS` — should show ~170 objects (was 3,458)
   - `[5/7] ENABLE GN MODIFIERS` — timing (should be <1s, was 5s at 3,458 objects)
3. Navigate viewport — orbit, zoom, pan
4. Log: `§PROOF VIEWPORT_LAG budget=2s result=Xs (CHUNK_SIZE=2000, N GN objects)`
5. Compare to S176 baseline: 10+ seconds at CHUNK_SIZE=100

**Pass:** viewport responds in <3s during orbit. If still slow, try CHUNK_SIZE=5000.

## Task 2: Reduce Outliner to ~6 discipline entries

Current: Outliner shows all ~170 `Fed_{disc}_{chunk:03d}` objects.
Target: Outliner shows only 6 discipline collections (ARC, STR, MEP, ELEC, FP, ACMV),
each containing its chunk objects collapsed.

The per-(disc, chunk) objects are already grouped under discipline collections.
The fix is purely Outliner presentation:
- Ensure chunk GN objects are **inside** their discipline collection (already done)
- Collapse discipline collections by default
- Verify discipline visibility toggle (eye icon) hides/shows all chunks for that discipline

Log: `§PROOF OUTLINER disc_count=6 visible_top_level=N`

## Task 3: Reopen test (linked mesh persistence)

1. Save the loaded scene as `input/sandbox_1M_chunked.blend`
2. Close Blender
3. Reopen the .blend
4. Check: do linked meshes auto-resolve from library.blend?
5. Check: do GN modifiers still work (viewport shows geometry)?
6. Check: file size — should be <25MB
7. Log: `§PROOF REOPEN_LINKED meshes=N resolved=Y/N viewport=Y/N size=XMB`

If linked meshes don't resolve: the library path in the .blend may be absolute.
Fix: use relative path (`//../../library/library.blend`).

## Task 4: Streamer + DLOD integration test

After load completes and streaming is running:
1. Orbit camera continuously for 5 seconds → should be smooth (halt mode)
2. Stop camera → watch console for `[DLOD] TICK HALT` burst
3. Verify near-camera elements show real meshes (not bbox cubes)
4. Log: `§PROOF DLOD_HALT swaps=N time=Xms`
5. Log: `§PROOF STREAM_PROGRESS` — streamer % updates every 10%

## Debugging checklist

If viewport is still slow at CHUNK_SIZE=2000:
- Count actual GN objects: `len([o for o in bpy.data.objects if any(m.type=='NODES' for m in o.modifiers)])`
- Check depsgraph eval time: `bpy.app.timers.register(lambda: print(f"EVAL {time.time()}"))`
- Try CHUNK_SIZE=5000 (22 chunks, ~70 GN objects for sandbox)
- Last resort: single GN object per discipline, no chunking, accept slow initial eval

## Standing rules
- Read the log after every run
- Do NOT flatten chunks back to one collection
- Do NOT add make_local() back to load_library_linked
- `!bbox_NNN` naming is load-bearing (! < T for slot 0 guarantee)
- DLOD wiring is in step 8/8 of load_library_gn() — do not remove

## Files
- `federation/loading/stage2_library_linker.py` — CHUNK_SIZE constant, load_library_gn()
- `federation/dlod_handler.py` — halt mode, chunked swap, dlod_init_chunked()
- `scripts/test_gn_chunk_proof.py` — standalone proofs (run at new CHUNK_SIZE)
- `internal/StressTest_1M_Results.md` — full analysis trail
- Saved .blend: `DAGCompiler/lib/input/1.blend` (18MB, CHUNK_SIZE=100 session)
