# 2D_026: RouteWalker JS + Compliance + Material Schemes

# !! DO NOT REMOVE — read the log after every run
# Scope: Browser-side room filling, MEP placement, compliance checking, material themes
# Depends on: 2D_025 Phase 2 (saved sections with room contours)

## ⚠ SESSION ISOLATION — READ BEFORE STARTING

This session is **browser JS only**. Zero Java changes. Zero existing file edits (until final wiring).

- GUID prefix: `RW2D-` (reserved for browser-side RouteWalker output)
- Files CREATED (new, owned by this session):
  - `deploy/dev/routewalker.js` — room fill algorithm
  - `deploy/dev/compliance.js` — rule checker
  - `deploy/dev/material_schemes.js` — finish variant picker
  - `deploy/dev/compliance_rules.json` — rule definitions
  - `deploy/dev/material_schemes.json` — scheme definitions
- Files READ-ONLY (do not edit):
  - `deploy/dev/boq_charts.html` — refactoring session owns this
  - `deploy/dev/mep_report.html` — refactoring session owns this
  - `deploy/dev/rates.js` — refactoring session owns this
  - `deploy/dev/measure.js` — clash detection, never touch
  - `component_library.db` — read BOM recipes, never write
  - `RouteWalker.java` — MEP Sprint 4 owns this
- Files EDIT LAST (after refactoring session complete):
  - `deploy/dev/index.html` — add `<script src="routewalker.js">` etc.
- DB writes: INSERT only, `RW2D-` prefix, into `elements_meta` + `element_transforms`
- New table: `rooms` (room_id, storey, room_type, polygon_json, area_m2)
- **Parallel sessions safe:** MEP Sprint 4 (Java RouteWalker) uses prefix `RW-`.
  Both produce same schema, browser reads both without knowing the source.
- **Refactoring safe:** All files are NEW. No edits to existing code until final `<script>` wiring.
- **Trigger:** This session fires AFTER user edits grid lines in 2D view.
  Grid drag changes wall positions → room polygons change → RouteWalker re-fills → compliance re-validates.
  But this session does NOT depend on grid drag being complete — it reads whatever contours exist in DB.

## Vision

Migrate RouteWalker from Java to JS. Combined saved section cuts define room
boundaries. RouteWalker fills rooms from BOM recipes. Compliance validation
runs as SQL queries against the DB. Flag → fix (grid drag) → revalidate.
Material scheme picker applies finish variants across the building.

All in-browser. No server. The DB is the engine.

## Prerequisites

- 2D_025 Phase 2: saved sections with contour data in DB
- grid_drag.js: position modification + cascade
- grid_rules.json: clearance rules (extend for compliance)
- component_library.db: BOM recipes for room types (READ ONLY)

## Pipeline

```
2D_025 saved sections (contour data per element)
    ↓
Room detection: assemble individual wall contours into closed room polygons
    ↓
Room classification: user tags or area-based inference
    ↓
RouteWalker: fill each room from its BOM recipe
    ├── Furniture placement (bed, desk, chair — clearance rules)
    ├── MEP devices (outlet, switch, sensor — wall offset rules)
    ├── Fixtures (sink, toilet — plumbing zone rules)
    └── Accessibility (door width, turning radius, grab rail)
    ↓
Compliance validation (SQL queries against filled DB)
    ↓
Flag violations → user fixes via grid drag → revalidate
    ↓
Material scheme picker (optional — apply finish variants)
    ↓
Export: modified DB → IFC
```

## Room Detection (the hard part)

### Problem
Section cut contours are **per-element** — each wall produces its own
contour polyline. A room is bounded by multiple walls, doors, and windows.
Assembling "which walls bound which room?" is a topology problem.

### Approach
1. Collect all wall contours from a saved horizontal section
2. Build a 2D arrangement: find all intersection points between contours
3. Flood-fill the bounded regions → each region is a candidate room
4. Filter out exterior regions (largest area, or regions touching bbox edge)
5. Each interior region = one room polygon

**Simpler alternative:** use the section cut's "BELOW" category elements.
Elements below the cut within a room's bbox are that room's contents.
Query DB: `SELECT DISTINCT storey FROM elements_meta` → each storey is
pre-labelled. Room boundaries = convex hull of elements sharing same storey
+ same spatial cluster.

**Simplest alternative:** skip automatic room detection. User clicks inside
a room polygon in the 2D view → system flood-fills from click point to
nearest wall contours → that's the room. User-driven, no inference needed.

### Recommendation
Start with user-click flood-fill (simplest, most reliable). Add automatic
detection later if the click-based approach is too tedious for large buildings.

## Room Classification

### Problem
A 20m² room on L1-North could be a patient room, store, office, or toilet.
The system needs to know which BOM recipe to apply.

### Approach
1. **User tagging (primary):** after clicking a room, user selects from
   dropdown: "Patient Room", "Nurse Station", "Store", "Corridor", etc.
   Room types come from component_library.db BOM categories.
2. **Area-based suggestion:** system suggests based on area range:
   - < 4m² → "Toilet/Store"
   - 4-12m² → "Office/Single Room"
   - 12-25m² → "Patient Room/Double"
   - 25-50m² → "Ward/Meeting"
   - > 50m² → "Corridor/Lobby"
3. **Storey-based inference:** rooms on "Ground Floor" more likely to be
   lobby/reception; rooms on clinical floors more likely patient rooms.

User always confirms — system suggests, never auto-assigns.

## RouteWalker JS Core Algorithm

1. Input: room polygon (from click-flood-fill) + BOM recipe (from user tag)
2. Identify wall segments: which edges of the room polygon are walls vs doors
3. Place fixed items first:
   - Door → mark swing arc zone (from grid_door_arcs.js)
   - Window → mark sill zone
4. Walk perimeter: place wall-mounted items (outlets, switches) per offset rules
   - Electrical outlet: every 3m along wall, 300mm from floor
   - Light switch: 150mm from door frame, 1200mm height
   - Data point: per room type (1 per bed in patient room)
5. Fill interior: place furniture per clearance grid
   - Bed: head at wall, 900mm clearance both sides
   - Desk: near window, 600mm chair clearance behind
   - Wardrobe: adjacent to bed, 450mm clearance
6. Validate: check every placed item against clearance rules
7. Output: element positions → INSERT into element_transforms + elements_meta

## Compliance Validation

### Rule Format (JSON, user-editable)

```json
{
  "jurisdiction": "MS1184:2014",
  "rules": [
    {
      "id": "PAT-001",
      "scope": "Patient Room",
      "check_type": "distance",
      "source": "IfcElectricalAppliance:emergency_outlet",
      "target": "IfcFurniture:bed",
      "max_distance_m": 1.5,
      "severity": "critical",
      "fix_hint": "Move outlet closer to bed head wall"
    },
    {
      "id": "COR-001",
      "scope": "Corridor",
      "check_type": "dimension",
      "measurement": "room_width",
      "min_m": 2.4,
      "severity": "critical",
      "fix_hint": "Widen corridor — drag grid lines"
    },
    {
      "id": "DOOR-001",
      "scope": "Exit",
      "check_type": "attribute",
      "element": "IfcDoor",
      "attribute": "swing_direction",
      "required_value": "outward",
      "severity": "critical",
      "fix_hint": "Reverse door swing direction"
    },
    {
      "id": "FIRE-001",
      "scope": "All",
      "check_type": "coverage",
      "element": "IfcFlowTerminal:fire_extinguisher",
      "max_walking_distance_m": 15.0,
      "severity": "critical",
      "fix_hint": "Add fire extinguisher — walking distance exceeds 15m"
    }
  ]
}
```

### Execution

Each rule translates to a SQL query:
```sql
-- PAT-001: emergency outlet distance from bed
SELECT r.name, e1.guid AS outlet, e2.guid AS bed,
  SQRT(POW(t1.center_x - t2.center_x, 2) +
       POW(t1.center_y - t2.center_y, 2)) AS dist
FROM rooms r
JOIN elements_meta e1 ON e1.room_id = r.id AND e1.ifc_class = 'IfcElectricalAppliance'
JOIN elements_meta e2 ON e2.room_id = r.id AND e2.ifc_class = 'IfcFurniture'
JOIN element_transforms t1 ON t1.guid = e1.guid
JOIN element_transforms t2 ON t2.guid = e2.guid
WHERE dist > 1.5
```

Results rendered as:
- Red highlight on violating elements in 3D view
- Violation list panel with fix hints
- Click violation → camera flies to element

## Material / Finish Scheme Picker

### Concept
Like a colour theme picker, but for IFC materials:
- "Scheme A: marble floor, timber cladding, glass partitions"
- "Scheme B: vinyl floor, plaster walls, aluminium frames"
- "Scheme C: polished concrete, exposed brick, steel frames"

### Implementation
Scheme stored as JSON:
```json
{
  "name": "Premium",
  "materials": {
    "IfcSlab:floor": { "name": "Marble", "rgba": "240,235,220,255" },
    "IfcWall:partition": { "name": "Timber Panel", "rgba": "180,140,100,255" },
    "IfcWindow:frame": { "name": "Aluminium", "rgba": "180,180,185,255" }
  }
}
```

Apply: `UPDATE elements_meta SET material_name = ?, material_rgba = ?
WHERE ifc_class = ? AND storey = ?`

Three.js materials refresh from updated DB values. Instant visual feedback.

### UX
- Dropdown in tools panel: "Material Scheme: [Default ▼]"
- User selects scheme → building updates visually
- Can create custom schemes by picking materials per element type

## Macro Surgery: DB-Level Operations

Since everything is in SQLite, bulk operations are just SQL:
- "Move all beds 200mm from wall" → `UPDATE element_transforms SET center_y = center_y + 0.2 WHERE guid IN (SELECT guid FROM elements_meta WHERE element_name LIKE '%bed%')`
- "Swap all single rooms to double" → DELETE single BOM, INSERT double BOM
- "Add fire extinguisher every 15m along corridor" → INSERT with computed positions
- "Flag all doors < 900mm width" → `SELECT guid FROM element_transforms WHERE bbox_x < 0.9`

No geometry kernel. Just SQL + BOM recipes + clearance maths.

## Dependencies

| Component | Source | Status |
|-----------|--------|--------|
| Room contours | 2D_025 saved sections | Phase 2 |
| BOM recipes | component_library.db | Exists |
| Clearance rules | grid_rules.json | Exists, extend |
| Grid drag | grid_drag.js | Exists |
| Section cut | section_cut.js | Exists |
| Compliance rules | New JSON file | To create |
| Material schemes | New JSON file | To create |

## Open Questions

1. Room detection: user-click flood-fill or automatic? → Start with click
2. RouteWalker pathfinding: simple grid walk or A* on room polygon? → Grid walk
3. Compliance rules: ship defaults per jurisdiction, or user-supplied only? → Both
4. Multi-storey: validate vertical relationships (stacked rooms, riser alignment)? → Phase 2
5. Export format: modified SQLite DB, or full IFC reconstruction? → SQLite first
6. Material schemes: pre-built library or user-defined only? → Ship 3 defaults + custom
7. How does RouteWalker handle irregularly shaped rooms? → Decompose into convex sub-regions

## ────────────────────────────────────────────────────────────
## ARCHWalker — Architectural Validation on Grid Change
## ────────────────────────────────────────────────────────────

### Problem

When the user drags a grid line in the 2D view, walls move. But what about
the elements attached to those walls — windows, doors, openings? The system
must decide for each element:

1. **Extend** — window widens because the wall it sits in got wider
2. **Duplicate** — wall got longer, a second window instance is warranted
3. **Remove** — wall got shorter, window no longer fits
4. **Keep** — element is unaffected by this particular grid change

Getting this wrong = "geometry hell" (orphaned windows floating in space,
overlapping doors, openings larger than walls).

### Design: ARCHWalker (runs BEFORE RouteWalker)

```
Grid drag event
    ↓
ARCHWalker validates ARC elements against new wall positions
    ├── Check each window/door: does it still fit in its host wall?
    ├── If wall wider: extend or duplicate (rule-driven)
    ├── If wall shorter: shrink or remove (rule-driven)
    └── If wall deleted: cascade-remove hosted elements
    ↓
RouteWalker re-validates MEP against updated architecture
    ↓
Compliance re-checks against final state
```

### Rules (JSON, same pattern as compliance_rules.json)

```json
{
  "arch_rules": [
    {
      "id": "WIN-EXTEND",
      "element": "IfcWindow",
      "trigger": "host_wall_wider",
      "condition": "wall_new_width - wall_old_width < element_width",
      "action": "keep",
      "note": "Wall grew but less than window width — window stays, no change"
    },
    {
      "id": "WIN-DUP",
      "element": "IfcWindow",
      "trigger": "host_wall_wider",
      "condition": "wall_new_width >= wall_old_width + element_width + min_spacing",
      "action": "duplicate",
      "params": { "min_spacing_m": 1.2, "max_per_wall": 4 },
      "note": "Wall grew enough to fit another window — add copy at even spacing"
    },
    {
      "id": "WIN-SHRINK",
      "element": "IfcWindow",
      "trigger": "host_wall_shorter",
      "condition": "wall_new_width < element_width + 0.2",
      "action": "remove",
      "note": "Wall too short for window — remove element"
    },
    {
      "id": "DOOR-KEEP",
      "element": "IfcDoor",
      "trigger": "host_wall_wider",
      "action": "keep",
      "note": "Doors never duplicate — one per opening, always"
    },
    {
      "id": "DOOR-SHRINK",
      "element": "IfcDoor",
      "trigger": "host_wall_shorter",
      "condition": "wall_new_width < element_width",
      "action": "flag",
      "severity": "critical",
      "note": "Wall shorter than door — cannot remove, flag for user decision"
    }
  ]
}
```

### Safe Copy Protocol (avoiding geometry hell)

When `action = "duplicate"`:
1. Read original element's row from `elements_meta` + `element_transforms`
2. Generate new GUID: `ARCH-{original_guid_short}-DUP-{N}`
3. Copy ALL fields from original (class, name, material, bbox)
4. Compute new `center_x/y/z` = evenly spaced along new wall length
5. INSERT new row — original untouched
6. Log: `§ARCH_DUP element={cls} from={guid} to={new_guid} wall={wall_guid}`

When `action = "remove"`:
1. Do NOT DELETE — set `elements_meta.discipline = 'REMOVED'` (soft delete)
2. Log: `§ARCH_REMOVE element={cls} guid={guid} reason={rule_id}`
3. Viewer hides `discipline='REMOVED'` elements (same as existing discipline filter)
4. User can undo: reset discipline to original value

When `action = "flag"`:
1. Add to violation list (same UI as compliance violations)
2. Element highlighted orange in 3D
3. User decides: resize door, widen wall back, or accept the violation

### Cascade Order

```
1. ARCHWalker  — validate/fix ARC elements (windows, doors, openings)
2. RouteWalker — re-validate MEP routes against updated ARC envelope
3. Compliance  — re-check all rules against final state
```

Each walker reads the DB as left by the previous one. No walker modifies
another walker's output — they only modify their own GUID prefix:
- ARCHWalker: `ARCH-` prefix for duplicated elements
- RouteWalker: `RW2D-` prefix for MEP elements (existing contract)
- Original IFC elements: no prefix (never modified, only soft-deleted)

### Files

- `deploy/dev/archwalker.js` — NEW (owned by this session)
- `deploy/dev/arch_rules.json` — NEW (rule definitions)
- Uses same compliance panel for flagged violations

### Witness Claims

- `§ARCH_VALIDATE walls=N windows=M doors=K` — validation started
- `§ARCH_DUP element=IfcWindow from=X to=ARCH-X-DUP-1` — safe duplication
- `§ARCH_REMOVE element=IfcWindow guid=X reason=WIN-SHRINK` — soft removal
- `§ARCH_FLAG element=IfcDoor guid=X reason=DOOR-SHRINK` — needs user decision
- `§ARCH_CASCADE → RouteWalker → Compliance` — cascade triggered

## ────────────────────────────────────────────────────────────
## RouteWalker Java → JS Migration
## ────────────────────────────────────────────────────────────

### Why Migrate

The Java `RouteWalker.java` (395 lines) is pure SQL + coordinate maths:
- Reads `ad_mep_anchor` (anchor positions) + `ad_mep_pattern` (topology steps)
- Pairs anchors by nearest-neighbour XY distance per storey
- Checks AABB clash against ARC envelope
- Emits pipe segment rows with dx/dy/dz + bbox dimensions

No Java-specific libraries. No graph algorithms. No fluid dynamics.
This is **exactly** the kind of code that runs identically in sql.js + browser JS.

### Migration Benefits

1. **Pure browser** — no Java install, no JDK, no compile step
2. **Instant feedback** — user drags grid line → RouteWalker re-runs in-page
3. **Same DB** — sql.js reads `ad_mep_anchor` + `ad_mep_pattern` same as JDBC did
4. **Testable** — `§` witness claims in console, Playwright can verify
5. **Consistent with 2D_026** — routewalker.js IS the migration target

### What Changes

| Java (current) | JS (migrated) |
|----------------|---------------|
| `Connection compileDb` | `db.exec()` via sql.js |
| `PreparedStatement` | `db.prepare()` / `db.run()` |
| `ResultSet` iteration | `stmt.getAsObject()` loop |
| `BIMLogger.info()` | `console.log('§RW2D_...')` |
| `record WalkResult` | `{ cwLines, spLines, clashSkipped }` |
| `List<PatternStep>` | plain array of objects |
| `Math.sqrt(dx*dx + dy*dy)` | identical |
| `aabbOverlap()` | identical |
| Writes to `c_orderline` | Writes to `elements_meta` + `element_transforms` |

### The Key Schema Difference

Java RouteWalker writes to `c_orderline` (compile DB, iDempiere schema).
JS RouteWalker writes to `elements_meta` + `element_transforms` (extracted DB, browser schema).

This is the right change — the browser schema is what the viewer, MEP report,
clash detection, and all analytics already query. No sync step needed.

### Migration Steps

1. Port `loadPatternSteps()` → JS function, sql.js query against `ad_mep_pattern`
2. Port `loadAnchors()` → JS function, sql.js query against `ad_mep_anchor`
3. Port `loadArcEnvelope()` → JS function, query `elements_meta WHERE discipline='ARC'`
4. Port `applyPattern()` → JS function, nearest-neighbour pairing + AABB clash check
5. Change output: INSERT into `elements_meta` + `element_transforms` with `RW2D-` prefix
6. The anchor/pattern tables need to be in the extracted DB (or a sidecar `mep_patterns.db`)
7. Test: SampleHouse → run JS RouteWalker → MEP BOQ shows pipe data

### Anchor/Pattern Data Source

Java reads from `erpDb` (iDempiere). Browser has no ERP connection.
Options:
1. **Bundle patterns in rate template JSON** — each country's rate template includes
   default MEP patterns for common building types. Lightweight, no extra DB.
2. **Sidecar DB** — `mep_patterns.db` loaded alongside building DB. Contains
   `ad_mep_anchor` + `ad_mep_pattern` tables. Heavier but more flexible.
3. **Generate anchors from ARC geometry** — detect plumbing zones (toilets, kitchens)
   from room classification, auto-place anchors. No pre-existing anchor data needed.

Recommendation: Start with option 3 (auto-generate from rooms) since 2D_026
already does room detection + classification. The room type drives anchor placement:
- Toilet → FIXTURE anchors at basin, WC, floor trap positions
- Kitchen → FIXTURE anchors at sink, dishwasher positions
- Any room → METER anchor at entry point (nearest to riser/corridor)

This makes the Java anchor tables unnecessary for the browser path.

### Witness Claims

- `§RW2D_MIGRATE fn=loadPatternSteps lines=N` — function ported
- `§RW2D_WALK building=SampleHouse cw=N sp=M clash=K` — walk complete
- `§RW2D_ANCHOR_GEN room=Toilet anchors=3` — auto-generated anchors from room type
