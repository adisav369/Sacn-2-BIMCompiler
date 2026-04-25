# S206 — Cinematic Building Tour

## Goal
Replace the basic walk-through with a cinematic tour that showcases each building
like a real-estate video. Multi-building city support.

## Foundation (S205 delivered)
- Action-based walk engine: `moveTo`, `lookAround`, `rise`, `pause`
- `buildTour()` generates tour from DB data
- IfcSpace extraction + walk_graph + stair positions in extracted DBs
- Proven: stair climb camY=-0.09 to 3.02, Level 2 rooms visited
- File: `deploy/rtree_browser_demo.html` (walkTick action processor)

## Part A: Cinematic Tour Sequence

For each building in the scene:

### Phase 1 — Aerial Sweep
New action: `orbit` — orbit the building once at 40deg downward angle.
```
{type:'orbit', cx, cy, cz, radius, tiltDeg:40, revolutions:1}
```
- `cx,cy,cz` = building centre (from `buildingCentres[]`)
- `radius` = building envelope * 1.5
- Camera orbits at `tiltDeg` angle, looking at centre
- Duration: ~10 seconds

### Phase 2 — Descend to Ground
New action: `descend` — smooth descent from orbit height to eye level.
```
{type:'descend', targetY, name:'Descending'}
```
- Keep XZ position, lower Y smoothly
- Camera tilts from 40deg down to horizontal during descent

### Phase 3 — Find Entrance
Use existing data: pick the "best" door:
1. Exterior door at largest IfcSpace (most contained elements)
2. Or: door with most clear space around it (fewest nearby walls)
3. Fallback: first door on ground storey

### Phase 4 — Enter + Room Tour
- `moveTo` door position
- `moveTo` room centre (0.5m inside door, not ON the door)
- `lookAround` 360deg
- Move to next room through connecting door (walk_graph)

### Phase 5 — Find Stairs
When all ground-floor rooms visited:
- `moveTo` nearest stair position
- `rise` action (existing, proven working)
- Visit upper storey rooms same pattern

### Phase 6 — Bird's Eye Finale
New action: `riseAndTilt` — rise straight up while tilting camera downward.
```
{type:'riseAndTilt', targetY, tiltDeg:80, name:'Birds eye'}
```
- Rise 20m above building
- Camera tilts to look almost straight down
- Pause 3 seconds — user sees floor plan from above

### Phase 7 — Next Building (City Mode)
- Fly to next building in `buildingsRendered[]`
- Repeat phases 1-6

## Part B: Wall Avoidance

### The Problem
Camera path cuts parallel to walls — viewport is half inside geometry.
This is worse than cutting THROUGH a wall (which is brief).

### The Fix
Offset all waypoints 0.5m from the nearest wall:
1. For each `moveTo` waypoint, query nearby walls:
```sql
SELECT center_x, center_y FROM elements_meta m
JOIN element_transforms t ON m.guid = t.guid
WHERE m.ifc_class LIKE '%Wall%' AND m.storey = ?
```
2. If waypoint is within 0.5m of a wall centre-line, push it 0.5m perpendicular away
3. Room centres (for lookAround) should be the centroid of contained elements, NOT the door position

### Simple heuristic (v1)
- Don't pathfind around walls — just offset the destination
- If camera must cross a wall, that's OK (brief cut-through)
- The key: never LINGER at or parallel to a wall surface

## Part C: Room Ranking

Biggest room = most interesting to visit first.

```sql
SELECT r.space_guid, s.name, COUNT(*) as element_count
FROM rel_contained_in_space r
JOIN spatial_structure s ON r.space_guid = s.guid
GROUP BY r.space_guid
ORDER BY element_count DESC
```

Visit rooms in descending size order within each storey.

## Files to modify
| File | Change |
|------|--------|
| `deploy/rtree_browser_demo.html` | Add orbit/descend/riseAndTilt actions, wall offset, room ranking, city sequencing |

## Test buildings
| Building | Storeys | Spaces | Stairs | Notes |
|----------|---------|--------|--------|-------|
| Duplex | 2 | 21 | 2 | Two units, full walk_graph |
| SampleHouse | 1 | 4 | 0 | Single storey, simple test |
| Hospital | 4+ | 0 | 30 | Door-only fallback, many stairs |
| LTU AHouse | 2 | ? | ? | OCI deployed, browser test |

## Success criteria
- [ ] Aerial orbit at 40deg for 10s before entering
- [ ] Smooth descent to ground level
- [ ] Enters through largest/best door
- [ ] 360deg pan in each room
- [ ] Camera never lingers within 0.5m parallel to a wall
- [ ] Stairs: walks to base, rises vertically (proven in S205)
- [ ] Bird's eye finale: rise + downward tilt
- [ ] City mode: sequences through multiple buildings
- [ ] Pause/resume preserved (✈ toggle)

## DO — Testing & Logging

All test output to `deploy/dev/tests/log/`.

### Existing coverage
- **Playwright 01-viewer `1.9 Fly-around toggle`**: toggles fly, verifies camera moves. PASS.
- But this only tests `toggleFlyAround` — not cinematic tour, orbit, or room walk.

### Gaps to fill in a dedicated session

| Test | Where | What | §-tag |
|------|-------|------|-------|
| Tour builds from DB | NEW or extend 01 | `buildTour()` returns non-empty action array | `§PW_TOUR_BUILD` |
| Orbit action moves camera | NEW | Start tour, wait 3s, verify camera position changed | `§PW_TOUR_ORBIT` |
| Pause/resume | 01-viewer extend | Toggle fly during tour, verify camera stops/resumes | `§PW_TOUR_PAUSE` |

### test_all.js
```javascript
// Tour wiring
const tourSrc = fs.readFileSync(path.join(DIR, 'tour.js'), 'utf8');
ok('tour.js has setupTour', tourSrc.includes('function setupTour'));
ok('tour.js loaded by viewer', html.includes('tour.js'));
ok('tour has orbit action', tourSrc.includes('orbit'));
```

## DO NOT
- Do not modify `deploy/sandbox/` — production
