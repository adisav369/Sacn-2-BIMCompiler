# ⚠ DO NOT REMOVE
# Scope: S177 — P2 FPS proof: GN + chunked linked meshes, no make_local()
# Read the log after every run. No claims without §PROOF log lines.

## Context

S176 introduced chunked sub-collections (CHUNK_SIZE=100) into `load_library_gn()`.
This caps GN Collection Info walk at ≤101 objects per chunk per frame evaluation.

The geometry hell in pre-S176 was caused by 7K–108K objects in one flat collection
× library dereference × 60fps. Chunking fixes the scale problem.

**Open question (P2):** With chunks of 100, are linked meshes (no make_local()) fast
enough for smooth viewport? Or does the library dereference cost per object still
add up even at 101 objects?

Measured so far:
- Terminal 7K, one flat collection, link=True → frozen (<1 FPS)
- Hospital 23K → 231 chunks of 100, link=True + make_local() on promotion → smooth
- Hypothesis: 101 × dereference × 60fps ≈ 0.6ms → within 16ms budget

Read before starting:
- `internal/StressTest_1M_Results.md` §S176 Session Analysis — full context
- `internal/DLOD_SPEC.md` §14 — current make_local() lifecycle
- `internal/GN_LINK_INVESTIGATION.md` — original geometry hell root cause
- `scripts/test_gn_chunk_proof.py` — chunk partition proofs (15/15 PASS)

## Task

### P2 — FPS proof: GN + chunked linked meshes, NO make_local()

**Goal:** Confirm whether `make_local()` can be removed from the GN streamer
(`_stream_tick()` in `stage2_library_linker.py`) and DLOD promotion
(`_make_local_promoted()` in `dlod_handler.py`).

**Method:**
1. Load Hospital via the GN button
2. Temporarily disable `make_local()` in `_stream_tick()` (comment out, not delete)
3. Navigate the viewport — orbit, zoom in/out
4. Measure depsgraph eval time and viewport FPS
5. Log: `§PROOF CHUNK_LINKED_FPS budget=16ms result=Xms fps=Y`

**Pass criteria:** depsgraph handler consistently <10ms, viewport ≥30 FPS while navigating.

**If PASS:**
- Remove `make_local()` from `_stream_tick()` permanently
- Remove `_make_local_promoted()` from `dlod_handler.py`
- Remove `strip_template_meshes()` / `restore_template_meshes()` for `_LibGN_Templates`
  path in `blend_cache.py` — no longer needed (linked meshes auto-restore on open)
- Project `.blend` saves with zero mesh data, auto-re-links on open
- Log: `§PROOF BLEND_SIZE_GN linked=Xmb (no mesh data)`

**If FAIL:**
- Keep `make_local()` in `_stream_tick()` — it is the correct owner
- Document the measured dereference cost per chunk for future reference
- Consider whether DLOD_SPEC §14 steps 3–5 (disable GN during load, initial batch)
  are still needed or can be removed now that chunks are small
- Log: `§PROOF CHUNK_LINKED_FPS FAIL cost=Xms threshold=16ms`

### P3 — Full Load + GN toggle on same scene

Verify that loading Full Load first, then GN, and toggling between them works:
1. Load Full Load (Library button) — confirm `§PROOF LINK_TIME` is fast (no make_local)
2. Load GN (GN button) — `Federation_Library` should be hidden automatically
3. Toggle back: `bim.toggle_federation_gn_mode` — `Federation_Library_GN` hides, Full Load shows
4. Log: `§PROOF TOGGLE Full→GN→Full no crash, both collections intact`

### P4 — library.blend sharding (spec only, no implementation)

Measured: `link=True` for 108K meshes = 292s, +2.5GB RAM. This scales with
vertex data size, not just handle count. For single buildings (Hospital 23K/120K = 19%
of library), link=True is ~55s estimated — still slow.

Draft a sharding spec:
- Split library.blend by first hex char of geometry_hash → 16 shards (~17MB each)
- For a given building: open only shards containing its hashes
- Hospital 23K hashes → spans ~16 shards but each shard only partially needed
- Expected: per-shard parse ~1s × 16 shards sequential = ~16s, or parallel = ~2s

Write spec to `internal/LibrarySharding_SPEC.md`. No implementation yet.

## Standing rules
- Witnesses prove. No claim without a §PROOF log line.
- Read the log after every run.
- Do NOT flatten chunks back to one collection.
- Do NOT add make_local() back to `load_library_linked`.
