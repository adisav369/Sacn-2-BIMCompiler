# ⚠ DO NOT REMOVE — Read the log after every run

## S260c: IDB Cache, Time Machine Cinematic, Split DB Polish

### Context
S260b deployed IDB cache with LRU eviction, split-DB streaming, BatchedMesh performance,
and Time Machine cinematic Eye. This session resolves remaining issues.

### OPEN BUG 1: Drop IFC — DB may not open after extraction
- User reports that Drop IFC extraction sometimes produces a DB that the viewer cannot open.
- Need to verify: is it a corrupt SQLite file? Missing tables? Or an extraction error silently swallowed?
- Check `import_db_builder.js` export path — does it verify the DB is valid before handing off?
- Test with: drop a small IFC, check console for `§DB_BUILD` → `§DB_EXPORT` → verify `db.exec("SELECT COUNT(*) FROM elements_meta")` works on the result.

### OPEN BUG 2: Large IFC sets (>15K elements) need split DB
- When a multi-IFC import produces >15K total elements, the viewer should automatically
  produce split files (meta.db + geo.db + positions.bin) after extraction.
- Currently `import_db_builder.js` has a threshold of 20K for skipping split. Should be 15K.
- The split output should use the same streaming path as OCI-hosted split DBs.
- Script exists: `scripts/split_db.sh` (for Node.js/CLI extraction).
- For browser-extracted DBs: need a JS-side split that produces the three files and stores
  them in IDB or offers download.
- Opening a local split set (e.g. from `import://`) should detect meta/geo/positions and
  stream identically to the OCI split path.

### OPEN BUG 3: Ground plane / shadow ground still not on real floor
- Terminal is perfect. Other buildings still hovering.
- `tools.js` was rewritten (S260c user edit) with storey-name matching (Step 1) + largest-above-grade
  fallback (Step 2). Current version: `tools.js?v=16`.
- The shadow ground plane must use the SAME Y as the visual ground mesh.
- **Still failing:** check `§GROUND_Y` log on LTU/Hospital — does `src=gf-storey-slab(...)` fire?
  If not, LTU's storey names may not match the hardcoded list.
- **Action:** Query LTU: `SELECT DISTINCT storey FROM elements_meta` — find actual GF storey name.
  Add it to the `gfNames` list in tools.js. The shadow light target must also sit at ground Y.

### OPEN BUG 4: TM Eye icon — wrong icon, too small
- Eye button should use the transparent Drone PNG the user downloaded (check `~/Pictures/Screenshots/`
  or `deploy/dev/icons/` for a drone icon). Currently using a generic eye icon.
- Sun/Night toggle icon too small. User wants a **half-moon** icon (half white / half black globe).
  Check if user has downloaded one, otherwise use a CSS-drawn half-circle or emoji placeholder.
  Make it larger (at least 28x28px tap target).

### OPEN BUG 5: Construction sequence still wrong — upper elements first
- Despite SEQUENCE_RULES reorder (walls seq=5 before MEP seq=7), upper storeys' elements
  still appear before lower storeys finish.
- Root cause: the storey-band sorting uses `storeyMinZ` from elements. If an upper storey
  has a few elements at low Z (e.g. a column extending down), its `minZ` is low, putting it
  in an early band.
- **Fix:** Use storey **median** Z (or **mode** Z = most common slab Z) instead of `min Z` for
  band ranking. Or better: use the storey's slab Z specifically (the slab defines the floor level).
- Also check: are `start_ts` values correct? Log first 20 ops: `§GANTT_OPS_FIRST20` with
  storey, band, seq, cz — verify sorting is bottom-up.

### OPEN BUG 6: Time Machine Cinematic Eye — not zooming as planned
**Current state:** Code exists in `time_machine.js` with density-seeking + 3 beats (closeup/
pullback/transit). But Chrome testing shows it does NOT produce the dramatic cinematic zoom
described. The camera stays distant or jumps around instead of flowing.

**Root cause:** The current implementation picks zones per-tick reactively. It needs to be
a **pre-planned storyboard** computed upfront from the full ops timeline.

**What's needed — "Film Studio" experience:**

1. **On Eye press:** Show `'Film Studio processing...'` status. Compute full storyboard from
   ALL ops (not just 25 ticks ahead). This is the kernel_ops superpower — we KNOW the future.

2. **Storyboard = array of scenes.** Each scene is a spatially dense cluster of consecutive ops.
   Cluster by: scan ops in chronological order, group consecutive ops whose positions are within
   15m of each other into one scene. When the centroid drifts >15m from the cluster, start a new scene.

3. **Each scene plays as:**
   - **Approach:** Camera glides in from current position to dramatic 3/4 angle, 4-8m away.
     Use ease-in-out curve. Camera should look SLIGHTLY AHEAD of where items will appear
     (leading the action, not chasing it).
   - **Watch:** Stay close. Track the centroid as items install one-by-one. Slow orbit (0.003 rad/tick).
     The camera follows the forming LINE of sprinklers/pipes/columns — not the centroid of all.
     For linear sequences, dolly along the line direction.
   - **Duration:** Stay for the FULL scene (all ops in this cluster complete). Never leave mid-series.

4. **Between scenes: continuous crane travel** — NEVER static or jump-cut.
   - Arc path (rise slightly during transit, descend on arrival).
   - While travelling, keep looking at the building (not the destination).
   - Speed: ~12 ticks for transit. Eased.

5. **Establishing shots every 3-4 scenes:**
   - Pull back to 80-120m, orbit the full building at 0.01 rad/tick.
   - During this wide shot, accelerate sun cycle — shadows visibly sweep across facades.
   - Lasts 20-25 ticks. Gives viewer spatial context ("oh, that was the east wing").

6. **Never static:** Even during "wait" moments (e.g. gap between ops), the camera should be
   slowly dollying forward toward the next scene's location. The film never pauses.

7. **Implementation:**
```js
// On Eye press:
viewerStatus('Film Studio processing...');
var storyboard = buildStoryboard(_ops, _guidPosMap);
// storyboard = [{center, direction, count, opStartIdx, opEndIdx, camAngle}, ...]
// direction = normalized vector along the line of installation (for linear series tracking)
// camAngle = pre-computed approach azimuth (away from walls — raycast check optional)

var _storyIdx = 0;
var _storyPhase = 'approach'; // 'approach' | 'watch' | 'transit' | 'establishing'
// Playback advances _storyIdx when cursor passes storyboard[_storyIdx].opEndIdx
```

8. **Dramatic angles:** For each scene, compute camera position as:
   - Azimuth: perpendicular to the `direction` vector (shows the line forming in profile)
   - Elevation: 25-40° above horizon (heroic angle)
   - Distance: 4-8m for tight series, 12-20m for spread events

### Deploy checklist (new session)
- After ANY code change: bump `?v=` in index.html for changed files
- Bump `CACHE_VERSION` in sw.js AND `sw.js?v=` in index.html registration — MUST MATCH
- Current: sw.js v349, tools?v=16, time_machine?v=10
- Upload ALL changed files to ootb-dev bucket
- Verify with `curl` that new code is served

### DONE (previous S260b sessions)
- ✅ IDB cache with LRU (80 entries), quota detection, auto-nuke on full
- ✅ Landing "Clear Cache" purges SW Cache API + IDB + unregisters SW
- ✅ SW registration version bump synced with CACHE_VERSION
- ✅ Dead httpvfs range code removed from streaming.js
- ✅ BatchedMesh frustumCulled=true, matrixAutoUpdate=false, BVH
- ✅ Orbit DPR reduction (1x during drag, full on release)
- ✅ Split detection works for plain `.db` names (hospital.db → hospital_meta.db)
- ✅ Bbox recolor after meta.db loads (discipline colors)
- ✅ Bboxes stay until streaming fully complete (dramatic reveal)
- ✅ TM sun button — removed shadow requirement
- ✅ TM TICK_MS dynamic (200ms for large buildings)
- ✅ Cost donut — uses op2.end_ts, proportional accrual
- ✅ SEQUENCE_RULES reordered: foundations→columns→beams→slabs→walls→MEP→doors→roof→MEP final→finishes
- ✅ Clinic extraction: 5 IFCs → 16K elements → split DBs
- ✅ All split DBs uploaded to ootb-dev + ootb-live
- ✅ Clash matrix diagnostic logs

### Files Modified
- `deploy/dev/scene.js` — IDB v2 (timestamps store), LRU eviction, quota diagnostics
- `deploy/dev/streaming.js` — split detect for plain .db, bbox recolor, bbox keep-until-done, matrixAutoUpdate, BVH
- `deploy/dev/time_machine.js` — cinematic director, sun gate removed, TICK_MS dynamic, cost donut fix
- `deploy/dev/tools.js` — ground Y: largest slab, prefer above-ground
- `deploy/dev/rates.js` — SEQUENCE_RULES reordered for construction logic
- `deploy/dev/main.js` — orbit DPR reduction
- `deploy/dev/measure.js` — clash matrix §-log
- `deploy/dev/landing.html` — Clear Cache purges SW Cache API
- `deploy/dev/index.html` — version bumps (scene?v=18, streaming?v=28, measure?v=42, main?v=26, tm?v=9, sw?v=348)
- `deploy/dev/sw.js` — v348
- `scripts/extract_clinic.sh` — multi-IFC extraction + merge + split

### Key Learnings
- SW registration `?v=` in index.html MUST match CACHE_VERSION in sw.js — otherwise SW never updates
- Firefox lacks WEBGL_multi_draw — BatchedMesh falls back to individual draw calls (14K vs 200)
- IDB quota of 10GB can fill from accumulated SW Cache API versions that never got purged
- `navigator.storage.estimate()` reports total origin storage (Cache API + IDB combined)
- Split detection must handle both `_extracted.db` suffix AND plain `.db` names
- positions.bin has no discipline data — bboxes drawn from it are monochrome until meta.db loads
