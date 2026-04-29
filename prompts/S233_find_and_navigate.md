# S233 — Find & Navigate: Indoor Wayfinding from Door to Element
# ⚠ DO NOT REMOVE — Scope: wayfinding search + turn-by-turn walk. Read the log after every run.

## Spec

### Problem
User wants to find a specific element (e.g. "fire pump on Level 2") and walk to it from the
main entrance with Google Maps–style turn-by-turn cues (left, straight, right, up stairs).
Today: NLP search highlights elements in 3D, Walk mode starts at the nearest door, but there
is no path between them.

### User Flow

```
1. User types "Find fire pump" in NLP search box
   (keyword "Find" triggers wayfinding mode instead of count/cost mode)

2. Amber panel opens — "Find Element"
   ┌─────────────────────────────────────┐
   │  FIND ELEMENT                       │
   │                                     │
   │  Type: [IfcFlowMovingDevice ▼]      │
   │  Storey: [Level 2           ▼]      │
   │  Name contains: [fire pump     ]    │
   │                                     │
   │  Results: 3 matches                 │
   │   ● FP-201 Fire Pump (Level 2)     │
   │   ● FP-202 Fire Pump (Level 2)     │
   │   ● FP-301 Fire Pump (Level 3)     │
   │                                     │
   │  [Navigate ▶]  [Zoom Only]  [✕]    │
   └─────────────────────────────────────┘

3. User taps an element in the list — camera zooms to it, element highlighted yellow.
   If multiple results: swipeable horizontal card list (mobile-friendly).
   Swipe left/right to browse, each card shows name + storey + discipline.
   Camera follows — each swipe zooms to that element. Active card has amber border.

4. User taps [Navigate ▶] on the active card:
   - Camera enters Walk mode (same Walk button, top-right) — reuses existing first-person controls
   - Camera snaps to main door (ground floor, nearest exterior door — findNearestDoorPosition)
   - First-person view, eye height 1.6m
   - Status bar shows: "Navigate to FP-201 — 47m, ~1 min"
   - Arrow overlay shows direction to next waypoint
   - User retains full phone rotation (deviceOrientation) to look around the model freely

5. User taps Walk arrow (same button, each tap = next waypoint):
   - Camera advances one segment (~3m per tap)
   - At each turn: "Turn left", "Turn right", "Go straight" overlay
   - At stairs: "Go up to Level 2" with camera rising
   - Distance countdown: "32m remaining"
   - No new buttons — Walk arrow IS the next-step control during navigation

6. Arrival: camera stops at target element, yellow highlight pulse, info panel opens
```

### NLP Search Hint

The search input (`nlp-input`, nlp.js line 433) placeholder text guides the user:

**Current:** `count doors, floor 1 walls, total cost...`
**Updated:** `count doors, floor 1 walls, total cost, Find fire pump...`

The "Find" keyword is the trigger. Light placeholder text teaches by example — no help
button needed. Same pattern on mobile (voice: user says "Find fire pump" into mic).

#### Voice Modality Rule — Match Input to Output

| Input method | Output method |
|-------------|--------------|
| Typed "Find fire pump" | HUD text only — no voice |
| Mic "Find fire pump" | HUD text **plus** voice — `SpeechSynthesis` speaks each cue |

Both modes always show visual HUD (arrows, distance, step counter). Voice **adds to**
the visual — never replaces it. Screen is always the primary channel.

Implementation: `inputWasVoice` boolean set by `SpeechRecognition.onresult`, carried
through the navigation session. Direction cues call `speechSynthesis.speak()` only when
`inputWasVoice === true`. No settings toggle — modality match is intuitive.

### Future: ERP-Driven Navigation (Warehouse Picking/Putaway)

Same navigation engine, different trigger. Instead of user typing "Find X", an ERP
pushes a picking list or putaway instruction to the mobile device:

```
ERP push → { action: "putaway", sku: "VALVE-304", location: "Aisle 3, Rack B, Level 2" }
         → navigate.js resolves location to element_transforms position
         → same turn-by-turn path from current position to target
         → worker follows arrows, confirms arrival, next item auto-loads
```

**Design constraint for S233:** Keep the navigation engine decoupled from the trigger
source. `startNavigation(targetGuid, startPosition)` takes any origin — Find panel,
ERP WebSocket, QR scan, or GPS proximity alert. The wayfinding core is trigger-agnostic.

### Architecture

#### A. Find Panel (amber, same style as wizard.js)

Triggered by NLP input starting with "Find " or "find ".

**Query the DB:**
```sql
-- Populate Type dropdown
SELECT DISTINCT ifc_class FROM elements_meta
WHERE building = ? ORDER BY ifc_class

-- Populate Storey dropdown
SELECT DISTINCT storey FROM elements_meta
WHERE building = ? ORDER BY storey

-- Search with filters
SELECT m.guid, m.ifc_class, m.element_name, m.storey, m.discipline,
       t.center_x, t.center_y, t.center_z
FROM elements_meta m
JOIN element_transforms t ON m.guid = t.guid
WHERE m.building = ?
  AND (? IS NULL OR m.ifc_class = ?)
  AND (? IS NULL OR m.storey = ?)
  AND (? IS NULL OR m.element_name LIKE '%' || ? || '%')
ORDER BY m.storey, m.ifc_class, m.element_name
LIMIT 50
```

No new tables needed — `elements_meta` + `element_transforms` have everything.

#### B. Path Graph — Grid-First, Door Shortcuts

**No IfcSpace needed.** Grid-first approach works for all building types — offices,
hospitals, warehouses, open-plan. Door graph is an optimisation on top, not the primary.

##### B1. Occupancy Grid (primary — works everywhere)

Per storey, from DB only (no meshes needed):

```sql
-- Wall/column positions for occupancy grid
SELECT t.center_x, t.center_y, t.center_z,
       t.size_x, t.size_y, t.size_z, m.ifc_class
FROM elements_meta m
JOIN element_transforms t ON m.guid = t.guid
WHERE m.building = ? AND m.storey = ?
  AND m.ifc_class IN ('IfcWall','IfcWallStandardCase','IfcColumn',
                       'IfcCurtainWall','IfcRailing')
```

1. Compute storey bounding box from all elements on that floor
2. Divide into **2m × 2m cells** (~5,000 cells for a 200m × 100m building)
3. Mark cells **occupied** if any wall/column bbox overlaps them
4. All other cells = **walkable**
5. A* pathfinding on walkable cells from start to target

**Performance:** A* on 5K nodes is <10ms in browser. Grid built once per storey, cached.

##### B2. Door Shortcuts (optimisation — reduces path length)

Where IfcDoor exists, doors act as **pre-validated corridor waypoints** overlaid on the grid:

1. Query all door positions per storey
2. For each door, mark its grid cell as a **preferred waypoint** (lower A* cost)
3. Effect: A* naturally routes through doorways instead of zigzagging around walls

Not a separate graph — just weighted cells in the same grid. No door graph = no problem,
grid still finds a path. This is why warehouses (few/no doors) work out of the box.

##### B3. Vertical Transport — Stairs & Lifts

Stairs often have `storey = "Unknown"` in IFC (Clinic: 7 stairs, all Unknown).
**Detect by Z coordinate**, not storey field:

```sql
SELECT guid, ifc_class, center_x, center_y, center_z
FROM elements_meta m JOIN element_transforms t ON m.guid = t.guid
WHERE m.building = ?
  AND m.ifc_class IN ('IfcStair','IfcStairFlight','IfcTransportElement')
```

Match to storeys using `walkStoreyLevels` (cached floor elevations from walk.js):
- If stair `center_z` is between storey N floor and storey N+1 floor → connects N↔N+1
- IfcTransportElement (elevator) with large Z-range → connects all storeys it spans

Edge weight: stairs = horizontal dist + 3m/storey, lift = horizontal dist + 1m/storey.

##### B4. Raycaster Validation (desktop only, post-stream)

On **desktop** (non-merged meshes), after streaming completes for the building:
- Validate candidate A* path segments against loaded meshes
- Reject segments that pass through walls the grid missed (small walls < 2m cell size)
- `new THREE.Raycaster(pointA, direction).intersectObjects(wallMeshes, false)`

On **mobile** (merged meshes, no individual wall picking): **skip raycaster**.
Grid-only is sufficient — the 2m cell size catches all standard walls (≥100mm thick).

##### B5. Floor/Slab Awareness

Camera elevation locked to storey floor level + 1.6m eye height.
Storey elevations from `walkStoreyLevels` (walk.js `cacheStoreyLevels()`).
No floating through voids. On storey change, camera lerps to new floor + 1.6m.

##### B6. Path Graph Timing

| Action | Requires streaming? | When available |
|--------|---------------------|----------------|
| Find panel (DB query) | No | Immediate |
| Occupancy grid (DB query) | No | Immediate |
| A* path | No | Immediate after grid built |
| Raycaster validation | Yes (desktop only) | After building stream complete |

Key insight: **[Navigate] works immediately** — grid + A* use DB data only.
Raycaster refinement is a post-stream polish step, not a gate.

#### C. Turn-by-Turn Navigation

Once path is computed (ordered list of waypoints):

1. **Segment:** Each pair of consecutive waypoints = one navigation segment
2. **Direction cue:** Compare bearing of current segment vs next segment
   - Bearing change < 30°: "Go straight"
   - Bearing change 30°–150° left: "Turn left"
   - Bearing change 30°–150° right: "Turn right"
   - Storey change: "Go up/down to {storey name}"
3. **Camera movement:** Walk arrow tap lerps camera to next **waypoint position**
   - Smooth lerp over 0.5s
   - Eye height = storey floor elevation + 1.6m (from `walkStoreyLevels`)
   - Camera `lookAt` direction set toward next waypoint after lerp
   - User retains full phone rotation (deviceOrientation) to look around freely
   - Looking around does NOT change the path — only the walk arrow advances position
4. **HUD overlay:**
   - Arrow icon (↑ ← →) showing next turn direction
   - Distance remaining (sum of remaining segments)
   - Current step: "3 of 12"
   - Target name at top

##### C1. Walk Button Override (critical integration)

**Problem:** `advanceWalkStep()` moves camera in **camera direction** (where user is
looking). Navigation needs to move toward **next waypoint** regardless of look direction.

**Solution:** When `A.navActive === true`:
- **Tap** the drive-thru button → lerp to next waypoint (not `advanceWalkStep()`)
- **Hold** disabled (prevent overshoot — each tap = exactly one waypoint)
- **Phone rotation** still works (deviceOrientation controls look, not movement)

```javascript
// In navigate.js — override drive-thru tap during navigation
const origStartDrive = startDrive;  // save reference
function navStartDrive(e) {
  if (A.navActive) {
    e.preventDefault();
    A.advanceNavStep();  // lerp to next waypoint
    return;
  }
  origStartDrive(e);    // normal walk behaviour
}
```

On navigation exit: restore original `startDrive`, re-enable hold-to-walk.

#### D. Integration Points

| Existing module | What it provides | What S233 hooks into | Risk |
|----------------|-----------------|---------------------|------|
| `nlp.js` | Text input, `A._nlpExecute`, existing "find" pattern (line 222) | **Replace** existing find handler → open amber panel instead of toast. Wrap `A._nlpExecute` before `parseQuery()`. Voice flag from `SpeechRecognition.onresult` | Low — clean intercept |
| `walk.js` | `findNearestDoorPosition()`, drive-thru button, `advanceWalkStep()`, `walkStoreyLevels`, deviceOrientation | **Override** drive-thru tap (waypoint lerp, disable hold). Reuse `cacheStoreyLevels()` for floor heights. Camera enters walk mode via existing `toggleWalkMode()` | Medium — button swap |
| `picking.js` | Raycaster, info panel, yellow highlight | Raycaster for desktop path validation (post-stream only). Yellow highlight on target element. Info panel on arrival | Low — read-only use |
| `panels.js` | Storey/disc data, amber panel style | Find panel UI matches wizard.js amber style | Low |
| `streaming.js` | `meshCache`, `guidMap`, `_instanceMeta` | Target element position from DB (not mesh). Desktop raycaster needs `A.scene` meshes | Low — DB is primary |

**NLP intercept detail:** nlp.js line 222 already has `/^(?:find|search|search for)\s+(.+)$/i`.
Current handler runs SQL + shows toast. S233 replaces this handler's `fn` to call
`navigate.openFindPanel(searchTerm)` instead. No duplicate pattern — same regex, new action.

#### E. New File

`deploy/dev/navigate.js` — Find panel + path graph + turn-by-turn engine.
Loaded after walk.js in index.html. Calls into walk.js for camera positioning.

### Implementation Order

1. **NLP intercept** — replace existing "find" handler in nlp.js, voice flag `inputWasVoice`
2. **Find panel UI** — amber panel, type/storey/name filters, result list, zoom-to on click
3. **Occupancy grid** — DB query for walls/columns, 2m cells, door-weighted cells
4. **A* pathfinding** — grid-based shortest path, stair/lift Z-detection for cross-storey
5. **Walk button override** — tap=waypoint lerp, hold disabled, phone rotation preserved
6. **Turn-by-turn engine** — segment list, direction cues, walk arrow advances
7. **HUD overlay** — arrow icons, distance countdown, step counter, voice cues if mic input
8. **Desktop raycaster polish** — post-stream path validation on non-merged meshes

### Extracted Example — Clinic (16,070 elements)

```
=== Doors: 254 total ===
  First Floor  | 151 doors | bbox starts (909.9, 19.8)
  Second Floor |  96 doors | bbox starts (914.8, 31.1)
  TOF Footing  |   2 doors
  Unknown      |   5 doors

=== Stairs: 7 (storey "Unknown" — near stair wells) ===

=== IFC classes (top 5 searchable) ===
  IfcFlowFitting    4,908   ← MEP pipe/duct fittings
  IfcFlowSegment    4,441   ← MEP pipe/duct runs
  IfcFlowTerminal   2,742   ← sprinklers, outlets, fixtures
  IfcWallStandardCase 1,091
  IfcDoor             254

=== Sample door with position ===
  guid: T0_Clinic_3qpE7fVkr8dRZ2wfCc6tWA
  name: "M_Toilet Partition:0865 x 1500mm"
  storey: First Floor
  position: (945.8, 70.1, 0.25)
```

**Example navigation:** User types "Find flow terminal" → filters to Second Floor →
selects a sprinkler head → [Navigate] → camera snaps to ground floor main door
(nearest exterior IfcDoor) → 8 waypoints through door graph → camera rises at stair
to Second Floor → arrives at sprinkler, highlight pulse, info panel opens.

### Turn-by-Turn HUD — Google Maps Style

Large translucent amber arrows centred on screen, overlaying the 3D view:

```
┌─────────────────────────────────────────┐
│                                         │
│              ┌───────┐                  │
│              │  ↑    │  ← amber, 50%    │
│              │  GO   │    opacity,       │
│              │STRAIGHT│   ~120px         │
│              └───────┘                  │
│                                         │
│  ┌──────────────────────────────────┐   │
│  │ 🔶 FP-201 Fire Pump  32m  4/12  │   │ ← bottom bar
│  └──────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

**Arrow types (CSS, no images):**
- `↑` GO STRAIGHT — bearing change < 30°
- `←` TURN LEFT — bearing change 30°–150° counter-clockwise
- `→` TURN RIGHT — bearing change 30°–150° clockwise
- `↗` SLIGHT RIGHT — bearing change 15°–30°
- `↰` U-TURN — bearing change > 150°
- `⬆` GO UP — storey change upward (with storey name)
- `⬇` GO DOWN — storey change downward

**Arrow div:** `position: fixed; top: 30%; left: 50%; transform: translate(-50%, -50%);`
`background: rgba(255, 170, 0, 0.5); border-radius: 16px; font-size: 64px; padding: 20px;`
Fades in 0.3s on each step, fades out after 2s.

**Bottom bar:** target name, distance remaining, step N of M.
Same amber translucent. `position: fixed; bottom: 60px;`

### Navigation Controls

#### Mobile (touch + deviceOrientation)

| Action | What happens |
|--------|-------------|
| **Walk arrow tap** | Advance to next waypoint, show direction cue |
| **Swipe down / tap back** | Go back one waypoint |
| **Long press Walk arrow** | Disabled during nav (prevents overshoot) |
| **Phone rotation** | Look around freely (deviceOrientation) — does NOT change path |
| **✕ button** | Exit navigation, restore normal camera |

#### Desktop (keyboard + mouse)

| Action | What happens |
|--------|-------------|
| **↑ / W / Enter / Space / click** | Advance to next waypoint |
| **↓ / S** | Go back one waypoint |
| **Home / double-press ↓** | Reset to main door (start over) |
| **Mouse move** | Pointer lock — look around freely (FPS style) |
| **ESC** | Exit navigation (also releases pointer lock) |
| **Scroll wheel** | No effect during navigation (prevents zoom-out of first-person) |

#### Desktop First-Person View

Desktop navigation uses **pointer lock** (`element.requestPointerLock()`) for mouse look:
- Mouse movement rotates camera (FPS-style, not orbit)
- OrbitControls disabled (`A.controls.enabled = false` — already done by walk mode)
- Click = advance to next waypoint (not pick element)
- Camera locked to floor + 1.6m (same as mobile)

On exit: pointer lock released, OrbitControls re-enabled, camera rotation order restored
to default XYZ (walk.js sets YXZ but never reverts — navigate.js must clean up).

If user orbits away (ESC then re-enter), the bottom bar shows "Press ↑ to resume"
and next advance key lerps camera back to current waypoint.

### What This Does NOT Do

- No real-time GPS tracking (that's existing walk.js with device sensors)
- No 3D path rendering (no line drawn on floor — just camera movement + HUD cues)
- No always-on voice — voice cues only when input was via mic (modality match rule)
- No obstacle avoidance during walk (path is pre-computed, camera follows waypoints)

### Test Plan

Manual verification:
- [ ] "Find door" returns all IfcDoor elements with correct storey/name
- [ ] "Find fire pump" opens amber panel with filtered results
- [ ] Click result → camera zooms to element, yellow highlight
- [ ] [Navigate] → camera snaps to main door, first-person view
- [ ] Walk arrow × N → camera advances through waypoints to target
- [ ] Direction cues correct at each turn (verified against element positions)
- [ ] Cross-storey navigation works (stair detected, camera rises)
- [ ] Arrival → info panel opens on target element
- [ ] Voice input → voice cues + visual HUD. Typed input → visual HUD only.

Playwright: `deploy/dev/tests/specs/17-find-navigate.spec.js`
- 17.1 "Find door" triggers find panel (amber panel visible, result count > 0)
- 17.2 Result click zooms camera (camera position changes toward element)
- 17.3 Navigate starts walk mode (walk controls visible, camera at door height)
- 17.4 Walk arrow advances waypoint (camera position changes, step counter increments)
- 17.5 Direction cue appears (HUD arrow element visible with correct class)
- 17.6 Arrival triggers info panel (target element highlighted, info panel open)
- 17.7 Voice flag set on mic input (inputWasVoice === true after SpeechRecognition)
- 17.8 ESC exits navigation (find panel closed, normal camera restored)

### S233 Session 2 (2026-04-27) — Issues Fixed + Desktop Voice

**Status:** 14/14 Playwright PASS (desktop). Audit: 112 tests, 246 expects, ratio 2.20.
Files: `navigate.js` (v5), `nlp.js` (v2), `main.js` (v5), `index.html`.
Deployed to OCI dev bucket (`bim-ootb-dev`). Navigate.js needs re-adding to `index.html`.

**Issues FIXED this session:**
- ~~Issue 1~~ FIXED: `openFindPanel()` exits walk mode before reset — camera always starts from main entrance
- ~~Issue 2~~ FIXED: Dropdown `onchange` now calls `populateDropdowns() + runSearch()` for cross-update
- ~~Issue 3~~ CLOSED (previous session)
- ~~Issue 4~~ FIXED: Touch start/end Y tracking — tap vs scroll discrimination (10px threshold)
- ~~Issue 5~~ FIXED: Camera offset scales with building radius (0.4 × radius, clamped 15–80m)
- ~~Issue 6~~ FIXED: Same as Issue 1 — `openFindPanel()` fully resets walk mode

**New features this session:**
- Auto-select first result after search — Navigate button works immediately
- Step coalescing — A* waypoints < 4m merged so each tap moves meaningful distance
- Desktop pointer lock skip — `lookAt` not called during pointer lock (prevents freeze)
- Desktop voice confirmed working — SpeechRecognition + SpeechSynthesis both available

**New Playwright tests (4 added → 14 total):**
- 17.9: Storey filter narrows results, click sets active, Navigate button updates
- 17.10: Type filter cross-updates storey dropdown
- 17.11: Navigate advances camera without freeze
- 17.12: Re-search after arrival works fresh
- 17.13: Desktop voice flag triggers spoken cues
- 17.14: Mic button visible on desktop

### S233 Session 3 (2026-04-28) — Desktop Fixes + Route Template + UI Overhaul

**Status:** 26/26 Playwright PASS. Audit: 155 tests, 376 expects, ratio 2.43.
Files: `navigate.js` (v7), `nlp.js` (v4), `index.html`, `main.js` (v6).
Deployed to OCI dev (`bim-ootb-dev`). **User reports: still not working on OCI dev.**

#### Issues FIXED this session (code + Playwright proven):

1. **`defer` on navigate.js** — `setupNavigate` undefined on OCI (60KB deferred, `main.js`
   ran first). Fix: removed `defer`. Root cause: deferred scripts execute after non-deferred.

2. **🎤 button invisible on desktop** — `display:none` inline, no JS ever showed it.
   Fix: `display:inline-block`, top-center (`left:50%; transform:translateX(-50%)`).

3. **Navigate jumped to target (2 steps)** — A* grid fallback returned `[start, end]`.
   Fix: `interpolateLine()` produces ~4m steps. Route template (graph A*) now primary.

4. **ESC didn't exit walk mode** — `stopNavigation()` now fully exits: `walkModeActive=false`,
   controls enabled, rotation XYZ, walk button deactivated.

5. **No free orbit during navigation** — OrbitControls stay enabled. Walk button = orderly
   advance; mouse/pinch = free camera. Pointer lock disabled.

6. **Off-path repath** — >8m from waypoint → recalculates from camera position.

7. **Click jumped camera to element** — `selectResult()` now highlight-only, no camera zoom.
   Navigate button handles the walk-to experience.

8. **Amber panel blocked scene (centered)** — moved to right-mid (`right:16px; top:50%;
   translateY(-50%); width:320px`). Spotlight-style: search icon + inline input,
   compact filter chips, icon per IFC class, truncated names.

9. **Mobile mic too large** — inline `min-height:44px` → `min-height:0`. Mobile CSS
   `!important` overrides to `padding:6px 10px; font-size:14px; min-height:32px`.

10. **No way to quit navigation** — closing NLP bar (🎤 button toggle) now calls
    `A.closeFindPanel()` which stops navigation + closes panel + restores orbit.
    `closeFindPanel` exposed on APP object for nlp.js integration.

#### Route Template — Implemented

Auto-generates corridor graph from occupancy grid:
- **Nodes**: doors (from grid door cells) + junctions (≥3 walkable neighbours) + endpoints
- **Edges**: BFS from each node, bidirectional, MAX_BFS=80 cells
- **Orphan fix**: wall-embedded doors auto-linked to nearest connected node (Euclidean)
- **Graph A***: runs on ~15 nodes instead of 5K grid cells
- **Named waypoints**: labels from IfcSpace/IfcRoom if available, else type ("Door", "Junction")
- **Direction cues**: "Turn left at Door" instead of just "Turn left"
- **Path priority**: route template → grid A* → interpolated straight line
- Exposed: `A.buildRouteTemplate(storey)`, `A.getRouteTemplate(storey)`, `A._nav`

Proven on Duplex: 15 nodes, 43 edges, 3 graph nodes in path, 6 waypoints with labels.

#### Playwright White-Box Tests Added (6 new → 26 total):

| Test | What it proves | Key evidence |
|------|---------------|--------------|
| 17.21 | Mobile mic ≤40px height | `height:32, fontSize:14px` |
| 17.22 | Desktop mic centered, ≤40px | `centerX=640=viewportCenter` |
| 17.23 | Panel right-mid, ≤50% width | `rightEdge=16, centerY=360` |
| 17.24 | Click = no camera jump | `moved=0.00m, active=1` |
| 17.25 | Navigate from entrance, ≥3 wp, steps advance | `wp=6, step 3/6, movedToEntrance=99.3m` |
| 17.26 | Close NLP bar exits everything | `nav=false, walk=false, controls=true` |

#### Lessons Learned

1. **Version bumps are essential.** OCI + browser cache means changes without `?v=N` bump
   are invisible. Always bump version in `index.html` script tags after any JS change.

2. **Other sessions modify shared files.** `index.html`, `nlp.js`, `main.js` are edited
   by multiple concurrent sessions. Read before edit. Don't assume your changes survived.

3. **Playwright white-box > manual testing.** Debug logging in code + `page.evaluate` +
   console capture reveals exactly what's happening. Don't guess — instrument and test.

4. **Headless lerp timing.** `lerpCamera()` uses `requestAnimationFrame` — in headless
   Chromium the render loop runs but timing differs. Wait ≥1200ms after ArrowUp for
   500ms lerp to complete. Check step counter, not camera position.

5. **Orphan graph nodes.** BFS edge building with `nj > ni` optimization can leave
   nodes unreachable if they're behind other nodes. Fix: BFS from every node (bidirectional),
   then connect orphans to nearest connected node by Euclidean distance.

### S233 Open Issues — NOT WORKING ON OCI DEV

**Status:** 26/26 Playwright PASS on localhost. OCI dev deployed with v7/v4 bumps.
User reports "still not working" on OCI dev. Root cause unknown.

#### Issue: OCI dev not reflecting changes

Despite version bumps (`navigate.js?v=7`, `nlp.js?v=4`) and confirmed upload via `curl`,
the user sees no change. Possible causes to investigate next session:

1. **CDN/edge cache** — OCI Object Storage may cache at edge. Check `Cache-Control` headers.
   Try adding `?_v=timestamp` to the viewer URL to bypass all caches.
2. **Service Worker** — `sw.js` is in the bucket. If it caches aggressively, it will
   serve stale JS regardless of version bumps. Check `sw.js` cache strategy.
3. **Browser cache** — despite version bump, the HTML itself (`sandbox/index.html`) may
   be cached. The URL doesn't change even though content does.
4. **Other session overwrote** — check `last-modified` timestamps on all files after
   confirming the issue. Another session may have deployed older versions.

#### Debugging steps for next session:

```bash
# 1. Check what's actually live
curl -s "https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-dev/o/sandbox/index.html" | grep "navigate\.js\|nlp\.js"

# 2. Check headers for caching
curl -sI "https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-dev/o/sandbox/navigate.js?v=7"

# 3. Check sw.js cache strategy
curl -s "https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-dev/o/sandbox/sw.js"

# 4. Check timestamps
for f in index.html navigate.js nlp.js main.js; do
  echo "$f: $(oci os object head --bucket-name bim-ootb-dev --name sandbox/$f 2>&1 | grep last-modified)"
done
```

### Resume Checklist (next session)

1. Read this §Open Issues section — OCI dev not reflecting changes
2. Run debugging steps above to identify cache/overwrite issue
3. Fix OCI caching (sw.js, Cache-Control headers, or URL-based cache bust)
4. Verify on phone: Find → select → Navigate → walk → pinch → close bar quits
5. Test on larger building (Clinic: 254 doors) — route template should produce rich graph
6. Deploy to OCI live (`bim-ootb-live`) when confirmed working
