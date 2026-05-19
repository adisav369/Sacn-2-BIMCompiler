# DLOD Post-Mortem (S260-S262)

## Timeline

### S260-S261: Full DLOD Coded, Then Disabled

Designed a geometry-swap DLOD: start elements as bbox cubes (8 verts each), swap to real geometry when camera is close. All guard code was in place:
- GPU budget (8M verts), reservation tiers, fallback
- Per-slot `setGeometryAt()` promote/demote
- Frustum + storey visibility culling
- Time Machine cooperation

Disabled with `_useDlodPath = false` — "bbox-only breaks pick until S261 promotion done."

### S262 Attempt 1: Re-enable with Tiered Reservation

**What happened:** Enabled DLOD for buildings >= 5K elements. Immediate geometry hell:
- `MAX_RESERVED_VERTS=512` was too low — 54% of single-instance elements (4094/7535) exceeded the cap
- Fallback: each oversized element became its own `new THREE.Mesh()` — 4094 individual draw calls
- DLOD path skipped progressive flush — user stared at bbox cubes for 49 seconds during 249MB geo download
- Total draw calls: ~6000 vs ~2000 before

**Root cause:** Tiered reservation caps assumed small elements. Real IFC elements commonly have 1000-5000+ vertices.

### S262 Attempt 2: Exact Reservation + Progressive Flush

Removed tiered caps, reserved exact geometry size per slot. Re-enabled progressive flush. Deployed.

**What happened:** Instanced meshes appeared progressively (good), but DLOD bbox BatchedMesh started all elements as bbox cubes. From 87m away (default camera), nothing promoted — 40% of the building stayed as cubes. The `PROMOTE_DIST=50m` threshold meant the entire building looked wrong until you zoomed in.

### S262 Attempt 3: Start with Real Geometry

Flipped approach: start slots with real geometry (`promoted=true`), let DLOD demote far elements to bbox later.

**What happened:** Building looked correct initially, but DLOD would eventually demote far elements to bbox cubes. User sees cubes appear as they orbit away. Unacceptable.

### S262 Final: Visibility-Only DLOD

**Insight:** Bbox cubes are invented geometry. They don't exist in the IFC. Showing them violates extract-only.

**Solution:** Strip geometry swap entirely. DLOD does visibility culling only:
- Frustum cull individual meshes (hide behind camera)
- `setVisibleAt()` for BatchedMesh/InstancedMesh
- No storey culling (too aggressive — hides visible floors on head-on views)
- Threshold 100K+ (only LTU and larger)

## Lessons Learned

### 1. Never Invent Geometry

Bbox cubes looked like a clean abstraction — lightweight proxies for real elements. In practice:
- Scaled bbox cubes have wrong proportions (unit cube * bbox scale != real shape)
- At any distance, some elements are visible as cubes to the user
- The viewer's purpose is to show IFC as-is, not a simplified version

**Rule: DLOD must only control visibility (show/hide), never replace geometry.**

### 2. Storey Culling is Too Aggressive

Storey-based visibility culling hides floors the user is looking at:
- Head-on view shows all floors, but orbit target is on one floor — others get hidden
- LTU has 19 storey names for 4 physical levels (VAN 1, VANING 1, Storey 1, Plan 1 = same floor) — `STOREY_RANGE=3` showed 7 names = ~2 physical levels
- When camera is "close" to one floor, other floors disappear — but user can see them

**Rule: Visibility culling must only hide what's geometrically off-screen (frustum), never what's structurally "far" (storey distance).**

### 3. Stuck-Hidden Bug Pattern

When a visibility culler skips processing (because visStoreys=null = "show all"), previously hidden elements stay hidden:
```js
// BUG: when visStoreys is null, this block is skipped entirely
if (obj.isBatchedMesh && A._batchMeta[obj.id] && visStoreys) { ... }
```
Fix: always enter the block, restore hidden elements when visStoreys=null.

### 4. Cache Version Discipline

Every OCI deploy must bump SW CACHE_VERSION. Old cached streaming.js served stale code with old bugs. Multiple test iterations were wasted debugging already-fixed code.

## Current State (Post-S262)

| What | Status |
|------|--------|
| Geometry swap | Removed. `_useDlodPath = false` always |
| Frustum culling | Active for 100K+ buildings (LTU) |
| Storey culling | Disabled (`_visibleStoreys()` returns null) |
| TM cooperation | Decoupled (no pause/resume) |
| Progressive flush | Active for all paths |
| Bbox placeholders | Still used during initial streaming (wireframe, disappear when real geometry arrives) |
| Draw calls | LTU: ~15K (progressive BatchedMesh), Terminal: ~2K |

## Files

| File | Change |
|------|--------|
| `deploy/dev/streaming.js` | `_useDlodPath=false`, progressive flush for all, exact reservation (dead code kept) |
| `deploy/dev/dlod.js` | Visibility-only, no `_promotePass()`, 100K threshold, storey culling disabled |
| `deploy/dev/time_machine.js` | DLOD pause/resume removed |
| `deploy/dev/tests/whitebox_regression.js` | `§WB_DLOD_VIS` test replaces tier/threshold tests |
