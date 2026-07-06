# ⚠ DO NOT REMOVE — S280d Verify Revert
# Scope: verify that streaming.js revert restored pre-S280b behavior
# Read the log after every run.

## Context

S280b/c/d (2026-05-27) introduced 4 breaking changes to `streaming.js`:
1. `_useMerge` variable referenced but never declared → ReferenceError every frame → browser hang
2. `_useMerge` always true (because `_hasMultiDraw` never defined) → all ≤5 elements routed to MergedMesh → GUIDs lost → Time Machine broken (sceneMeshGUIDs=0, no construction playback)
3. Threshold changed `===1` → `<=5` → more elements in wrong path
4. Consolidation call added `_consolidateBatched()` → extra scene.traverse at stream-end → lag spike
5. BVH changed from batch-500/setTimeout(0) to 8ms/requestIdleCallback → 36s completion vs <1s

All reverted in commits d46a450→68bd9a7→d397bc4 on bim-ootb main. sw v502, streaming.js?v=51.

## What was reverted (streaming.js now matches 2d6ea96 except harmless object reuse)
- Threshold: `===1` (single-instance → BatchedMesh, 2+ → InstancedMesh)
- Merge guard: `A._isMobile` (MergedMesh only on mobile)
- `_useMerge` variable: removed entirely
- Consolidation call: removed
- BVH: batch-500 + setTimeout(0) restored

## What was kept
- Object reuse (`_flushM4` etc.) — saves GC, no behavior change
- EffectComposer (S277 SSAO/Outline) — untouched, in scene.js not streaming.js

## Verification Steps

### Step 1: Hard refresh GH Pages
Close all tabs, reopen. Console should show sw v502 activating.

### Step 2: Load LTU_AHouse — check streaming
Expected logs:
```
§BATCHED_FLUSH instanced=N batched=M drawCalls=X
```
- `batched` should be >0 (BatchedMesh being created for single-instance hashes)
- No `_useMerge` errors
- No `§CONSOLIDATE` lines

### Step 3: Time Machine
1. Press T to open TM
2. `§GANTT injected=122667 sceneMeshGUIDs=0` — 0 is expected (GUIDs in _batchMeta/_instanceMeta, not userData)
3. Press Play (▶) — construction should build from footings upward
4. Press T to close TM — should restore instantly, no multi-second lag

### Step 4: Effects
1. Press H — SSAO + shadows on. Smooth orbit.
2. Press N — night mode. Lights from IfcFlowTerminal.
3. Press L — cinematic fly. Smooth during streaming + after.

### Step 5: Compare draw calls with pre-S280b baseline
Pre-S280b (2d6ea96): ~2500-3000 draw calls for LTU 122K elements
Current: should match ±10%

## DONE criteria
- No ReferenceError in console
- TM plays construction from start (elements appear progressively)
- TM close = instant restore, no lag
- Streaming completes without hang
- SSAO/shadows/night mode work
- Draw call count matches pre-S280b baseline

## Root cause summary
S280b/c/d was a performance session that drifted into streaming path changes:
- Changed threshold without verifying TM dependency on GUID paths
- Added `_useMerge` without defining `_hasMultiDraw`
- Added consolidation traverse without measuring its cost
- Changed BVH scheduling without measuring completion time impact

## Lesson
Never change streaming element routing (threshold, bucket targets) without testing:
1. Time Machine playback (depends on _batchMeta/_instanceMeta GUIDs)
2. Pick/raycasting (depends on guidMap entries)
3. Storey/disc filtering (depends on _batchStoreyMap/_batchDiscMap)

## S280d Contract Lockdown (2026-05-27, commit c8181463)

Revert verified working. Three layers added to prevent recurrence:

### Layer 1: Runtime contract assertion
`§CONTRACT_CHECK` / `§CONTRACT_FAIL` fires at final flush in `_flushInstanced`.
Checks:
- Non-zero metadata entries on desktop (batch + instanced > 0)
- guidMap count >= registered metadata count (no orphans)
- Every InstancedMesh has >= 2 instances (single-instance = wrong path)

### Layer 2: Extracted shared metadata functions
- `A._registerBatchSlot(bm, el, slotId)` — populates guidMap + _batchStoreyMap + _batchDiscMap
- `A._registerInstanceSlot(iMesh, el, instanceIndex)` — populates guidMap + _instanceGuids
- Used by BOTH `_flushInstanced` AND `_flushBboxBatched` — eliminates duplication
- Contract comment block documents the 6 data structures, routing rule, and 16 consumer files

### Layer 3: Whitebox regression tests
- **3.20 `streaming_contract_routing`** — verifies from source code:
  - Threshold is `=== 1` (not `<=5` or `== 1`)
  - `_registerBatchSlot` / `_registerInstanceSlot` exist and populate the right structures
  - `§CONTRACT_CHECK/FAIL` assertion present
  - Both flush paths use the shared functions (not inline duplication)
- **3.21 `streaming_contract_hash_distribution`** — verifies LTU has both single-instance (70K) and multi-instance (13K) hashes, exercising both routing paths

### OCI ootb-dev note
Bucket uses `viewer/` prefix, not `sandbox/`. Deploy with `--name viewer/{file}.js`.
