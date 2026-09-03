# S205 — Indoor Walk-Through Fly + Walk Mode

## Goal
Replace the external orbit fly-around with an **indoor walk-through** that follows
the door-to-door route inside the building at eye height (1.6m). Also prototype
Walk Mode (GPS blue dot) for site navigation.

## Proof Data (verified S204 session)

Duplex IFC has full walkable graph:
- 21 `IfcSpace` (Foyer, Living Room, Kitchen, Bathroom, Hallway, Bedroom...)
- 14 `IfcDoor` (connections between rooms)
- 2 `IfcStair` (vertical connections between storeys)
- 113 `IfcWall` (boundaries)
- TrueNorth: 0° (grid Y = north)

SampleHouse: 4 spaces, 3 doors, 7 walls.

Source IFCs: `reference/residential/Ifc2x3_Duplex_Architecture.ifc`, `reference/residential/Ifc4_SampleHouse.ifc`

## Part A: Extract IfcSpace + Walkable Graph

### A.1 Add IfcSpace to extraction

File: `scripts/extractIFCtoDB_open.py`

Currently `IfcSpace` is filtered out (no geometry needed). Add extraction of space
metadata and containment:

```python
# After elements_meta population:
for space in ifc_file.by_type('IfcSpace'):
    # Insert into spatial_structure (table already exists)
    # guid, type='IfcSpace', name=space.Name, parent_guid=storey_guid
    # Also get centroid from space boundary or placement
```

Populate `rel_contained_in_space`:
```python
for rel in ifc_file.by_type('IfcRelContainedInSpatialStructure'):
    if rel.RelatingStructure.is_a('IfcSpace'):
        for elem in rel.RelatedElements:
            # INSERT (element_guid, space_guid)
```

### A.2 Build walk graph table

New table in extracted DB:
```sql
CREATE TABLE walk_graph (
    from_space_guid TEXT,
    to_space_guid TEXT,
    via_door_guid TEXT,
    door_x REAL, door_y REAL, door_z REAL,
    storey TEXT,
    PRIMARY KEY (from_space_guid, to_space_guid)
);
```

Populated by finding which two spaces each door connects (nearest IfcSpace centroids
on either side of the door position).

### A.3 Add TrueNorth extraction

Already coded in S204 (in `extractIFCtoDB_open.py`). Stores `true_north_angle` in
`project_metadata`. Verified: Duplex = 0°, SampleHouse ≈ 0°.

## Part B: Indoor Walk-Through Fly

### B.1 Query walk path from DB

```sql
-- All spaces with centroids, ordered by storey then position
SELECT s.guid, s.name, s.type, t.center_x, t.center_y, t.center_z, m.storey
FROM spatial_structure s
JOIN element_transforms t ON s.guid = t.guid
JOIN elements_meta m ON s.guid = m.guid
WHERE s.type = 'IfcSpace'
ORDER BY m.storey, t.center_x, t.center_y
```

If no IfcSpace in DB, fall back to door-to-door path:
```sql
SELECT guid, center_x, center_y, center_z, storey
FROM elements_meta JOIN element_transforms USING(guid)
WHERE ifc_class = 'IfcDoor'
ORDER BY storey, center_x, center_y
```

### B.2 Build ordered path

1. Start at entrance (first door on ground storey, or lowest IfcSpace)
2. Traverse walk_graph: nearest unvisited connected space
3. When all spaces on storey visited, take IfcStair to next storey
4. Repeat until all storeys visited

If walk_graph not available, use nearest-neighbour through door positions.

### B.3 Animate camera

Replace the orbit fly in `toggleFlyAround()` with indoor mode:

```javascript
// Existing fly: orbit outside
// New: walk inside

const EYE_HEIGHT = 1.6; // metres above slab
const WALK_SPEED = 2.0; // metres per second

// Path: array of {x, y, z} waypoints (door positions at eye height)
// Camera position interpolates along path
// Camera lookAt = next waypoint
// Smooth bezier curves through waypoints for cinematic feel

let walkPath = [];  // populated from DB
let walkT = 0;      // 0..1 progress along path

function updateWalkFly(dt) {
    walkT += (WALK_SPEED * dt) / totalPathLength;
    if (walkT >= 1) { walkT = 0; } // loop

    const pos = interpolatePath(walkPath, walkT);
    const lookAhead = interpolatePath(walkPath, Math.min(walkT + 0.02, 1));

    camera.position.set(pos.x, pos.y + EYE_HEIGHT, pos.z);
    camera.lookAt(lookAhead.x, lookAhead.y + EYE_HEIGHT, lookAhead.z);
}
```

### B.4 UI

- Reuse the existing fly button (✈)
- If building has IfcSpace/IfcDoor data → indoor walk-through
- If not → exterior orbit (current behaviour)
- Status bar shows: "Walking: Kitchen → Hallway → Bedroom"
- Speed control: tap to cycle 1x / 2x / 4x

## Part C: Walk Mode (GPS Blue Dot)

### C.1 Anchor point

New button: "Walk Mode" (mobile only, like Site button)

1. User taps "Walk Mode"
2. Prompt: "Stand at a known location and tap SET"
3. Records current GPS as anchor
4. Maps anchor GPS to nearest IfcDoor position (building entrance)
5. Blue sphere appears at that position in the model

### C.2 GPS tracking

```javascript
navigator.geolocation.watchPosition(pos => {
    // Delta from anchor GPS
    const dLat = pos.coords.latitude - anchorGPS.lat;
    const dLng = pos.coords.longitude - anchorGPS.lng;

    // Convert to metres (1° lat ≈ 111,320m, 1° lng ≈ 111,320m × cos(lat))
    const dx = dLng * 111320 * Math.cos(anchorGPS.lat * Math.PI / 180);
    const dy = dLat * 111320;

    // Rotate by TrueNorth to align with model
    const angle = window._trueNorthAngle * Math.PI / 180;
    const mx = dx * Math.cos(angle) - dy * Math.sin(angle);
    const my = dx * Math.sin(angle) + dy * Math.cos(angle);

    // IFC position = anchor IFC + delta
    blueDot.position.x = anchorIFC.x + mx;
    blueDot.position.z = anchorIFC.y + my; // IFC Y → Three.js Z

    // Snap to nearest walkable position
    snapToNearestDoor(blueDot.position);
}, { enableHighAccuracy: true });
```

### C.3 Camera follow

```javascript
// Camera follows blue dot, offset behind based on compass heading
const heading = (_camHeading - window._trueNorthAngle) * Math.PI / 180;
const followDist = 5; // metres behind
camera.position.x = blueDot.position.x - followDist * Math.sin(heading);
camera.position.z = blueDot.position.z - followDist * Math.cos(heading);
camera.position.y = blueDot.position.y + EYE_HEIGHT;
camera.lookAt(blueDot.position);
```

### C.4 Floor detection

```javascript
// Barometer or GPS altitude change → snap to storey
// Each storey has known Z from element_transforms
const storeyLevels = db.exec(`
    SELECT DISTINCT storey, MIN(center_z) as floor_z
    FROM elements_meta JOIN element_transforms USING(guid)
    GROUP BY storey ORDER BY floor_z
`);
// Find nearest storey to current altitude
```

## Part D: "What's Behind This Wall"

When in Walk Mode, tap a wall:

```sql
SELECT m.guid, m.ifc_class, m.element_name, m.discipline,
       t.center_x, t.center_y, t.center_z
FROM elements_meta m
JOIN element_transforms t ON m.guid = t.guid
WHERE m.discipline IN ('MEP','ELEC','PLB','ACMV','FP','HVAC')
  AND m.storey = '{same_storey}'
  AND ABS((t.center_x - {wall_x}) * {wall_nx}
        + (t.center_y - {wall_y}) * {wall_ny}) < 0.5
```

Highlight matching MEP elements in bright green/orange. X-ray the wall (opacity 0.15).
Now you see pipes/ducts through the wall.

## Files to modify

| File | Change |
|------|--------|
| `scripts/extractIFCtoDB_open.py` | Add IfcSpace extraction, rel_contained_in_space, walk_graph |
| `deploy/rtree_browser_demo.html` | Indoor fly path, Walk Mode button, blue dot, GPS tracking |
| `docs/MOBILE_DEPLOY.md` | Already spec'd as Phase F (S204 session) |

## Test buildings

| Building | Spaces | Doors | Stairs | Verdict |
|----------|--------|-------|--------|---------|
| Duplex | 21 | 14 | 2 | Full walk-through |
| SampleHouse | 4 | 3 | 0 | Single-storey walk |
| Hospital | 0 (not extracted) | 440 | 30 | Door-only fallback |

## Success criteria

- [ ] IfcSpace extracted to spatial_structure for Duplex (21 spaces)
- [ ] walk_graph table populated (door connects two spaces)
- [ ] Indoor fly: camera walks Foyer → Living Room → Kitchen → stairs → upstairs
- [ ] Status bar shows current room name
- [ ] Walk Mode: blue dot at anchor, moves with GPS
- [ ] Walk Mode: camera follows dot, compass-aligned
- [ ] "What's behind this wall": tap wall → MEP highlighted
- [ ] Falls back to exterior orbit if no IfcSpace data
