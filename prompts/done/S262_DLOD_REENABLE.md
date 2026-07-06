# ⚠ DO NOT REMOVE — Scope: S262 DLOD Re-enable + TM Integration
# Read the log after every run. No inventions.

# S262: Re-enable DLOD + Fix Time Machine Integration

## Context

DLOD (Dynamic Level of Detail) was fully coded in S260/S261 but disabled with:
```js
A._useDlodPath = false; // §S260d: DISABLED — bbox-only breaks pick until S261 promotion done
```
at `deploy/dev/streaming.js` lines 109 and 158.

Analysis (2026-05-19) concluded:
- **Picking works on bbox geometry** — batchId resolves to guid correctly via `_batchMeta`
- **Highlight works** — picking.js line 335 already queries DB for per-element bbox, not geometry-derived
- **The "breaks pick" comment is overstated** — reduced accuracy on thin elements >50m away during first 5-10s, acceptable trade-off
- **All guard code is in place**: GPU budget (8M verts), reservation tiers, fallback to individual meshes

## Task 1: Re-enable DLOD

In `deploy/dev/streaming.js`, change `_useDlodPath = false` to enable DLOD on desktop for buildings >= 5K elements:

```js
A._useDlodPath = !A._isMobile && rows.length >= 5000;
```

Both locations (line ~109 range stream path, line ~158 sync DB path).

**Verify with §-tagged logs:**
- `§S261_DEFER_BBOX buckets=N` — single-instance elements deferred to bbox path
- `§DLOD_FLUSH slots=N budget=N` — bbox BatchedMesh created with reserved capacity
- `§DLOD_PROMOTE hash=X dist=Nm` — real geometry swapped in when camera is close
- `§DLOD_DEMOTE hash=X dist=Nm` — bbox restored when camera moves away

**Test buildings:** Terminal (48K elements, split-DB), HITOS (2K, single-DB — should NOT use DLOD), LTU_AHouse (122K, split-DB — should use DLOD)

## Task 2: Fix Time Machine DLOD Interaction

Current TM code (`deploy/dev/time_machine.js` ~line 2843):
```js
if (app._dlodPaused !== undefined) app._dlodPaused = true;
if (app.dlodDemoteAll) app.dlodDemoteAll();  // reset geometry to bbox
```

This is wrong. DLOD is a **scene-level abstraction** — it operates on camera distance alone, completely unaware of what made an element visible. It is not TM's concern to manage DLOD, just as DLOD is not aware of TM's construction phases.

- **TM** controls element **visibility** (show/hide by construction phase)
- **DLOD** controls element **geometry detail** (bbox vs real mesh by camera distance)
- They are **independent systems that naturally cooperate**: TM plays its construction script, DLOD silently assists in the background by giving real geometry to whatever is visible and near the camera

TM should NOT pause DLOD or demote all. DLOD just keeps running. When TM reveals a phase, elements near the camera get promoted to real geometry automatically. Far elements stay as lightweight bbox. When TM hides a phase, `dlodTick` skips those elements (already checks `getVisibleAt`) — no wasted GPU work, no special wiring.

**Fix:** Remove the DLOD pause/demote from TM activate, and the resume from TM deactivate (~line 2891). No replacement code needed — DLOD's existing distance-based tick handles everything.

**Verify:**
- Open Terminal in viewer
- Start Time Machine playback
- Camera near elements → should show real geometry (not all bbox)
- Camera far → should show bbox
- TM phase hides elements → hidden elements should NOT get promoted
- `§DLOD_PROMOTE` and `§DLOD_DEMOTE` logs should appear during TM playback

## Task 3: Add markDirty to DLOD promote/demote

In `deploy/dev/dlod.js`, after promote and demote operations, call `A.markDirty()` to trigger re-render. Without this, geometry swaps only show when the user drags the camera (same bug we fixed for xray/pick in this session).

## Whitebox Regression

Run `node deploy/dev/tests/whitebox_regression.js` before and after. All 21 tests must pass. The existing `§WB_DLOD_BBOX`, `§WB_DLOD_BUDGET`, `§WB_DLOD_TIERS` tests already cover DLOD prerequisites.

Consider adding a whitebox test to the SAME suite:
- `§WB_DLOD_ENABLE` — verify `_useDlodPath` would be true for Terminal (48K > 5K threshold) and false for HITOS (2K < 5K)

## Deploy

After whitebox passes: bump sw.js CACHE_VERSION, match index.html `sw.js?v=`, deploy streaming.js + dlod.js + time_machine.js + sw.js + index.html to both `bim-ootb-live` and `bim-ootb-dev` buckets. Verify with curl.

## Task 4: Mobile r160 Review — Stop Re-rendering What Hasn't Changed

Mobile path (`A._isMobile`) skips DLOD but may still be doing unnecessary work with r160 capabilities. Audit these:

**BatchedMesh visibility vs re-build:**
Time Machine currently toggles element visibility. Check whether TM is using `bm.setVisibleAt(slotId, visible)` (cheap GPU flag, no re-render) or rebuilding/removing meshes (expensive). r160 BatchedMesh supports per-instance visibility natively — TM should use it, not `scene.remove/add`.

**InstancedMesh.setColorAt:**
r160 supports per-instance colour without rebuilding. Check if storey/discipline filter changes are rebuilding InstancedMesh or just toggling visibility. Mobile should never rebuild what it can hide.

**Frustum culling:**
r160 BatchedMesh supports per-instance frustum culling (`bm.setVisibleAt` + `frustumCulled = true`). Check if mobile is rendering off-screen elements. For TM playback on mobile, elements outside camera frustum should be skipped entirely.

**§-tagged audit logs to add:**
- `§MOBILE_TM_TOGGLE method=setVisibleAt|rebuild count=N` — which path TM uses
- `§MOBILE_FRUSTUM culled=N visible=N total=N` — how many elements are off-screen
- `§MOBILE_INSTANCED rebuilds=N toggles=N` — rebuild vs visibility toggle ratio

**Goal:** Mobile TM playback should be smooth scrubbing, not a re-render storm. Every visibility change should be a flag flip, not a geometry operation.

## Files to Modify

| File | Change |
|---|---|
| `deploy/dev/streaming.js` | `_useDlodPath = !A._isMobile && rows.length >= 5000` (2 locations) |
| `deploy/dev/time_machine.js` | Remove DLOD pause/demote on TM activate, remove resume on TM deactivate |
| `deploy/dev/dlod.js` | Add `A.markDirty()` after promote and demote operations |
| `deploy/dev/time_machine.js` | Use `setVisibleAt` instead of mesh rebuild for phase toggles (if not already) |
| `deploy/dev/tests/whitebox_regression.js` | Add `§WB_DLOD_ENABLE` threshold test |
