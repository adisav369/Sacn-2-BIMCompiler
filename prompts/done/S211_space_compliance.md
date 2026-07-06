# S211 — Space Compliance Checker: Walk In → Auto-Check

## Status: READY

## Goal
Walk into a room during Drive-Thru → phone detects which IfcSpace → instantly shows compliance: area vs code, door width vs minimum, ceiling height. Red/green pass/fail.

## What Already Works
- Drive-Thru walk mode with position tracking (S208)
- Element picking and DB queries
- Storey levels cached (walk.js cacheStoreyLevels)
- IFC coordinate ↔ Three.js conversion (ifc2three)

## Spec

### Space Detection
```sql
-- Find which IfcSpace contains the camera position
SELECT m.guid, m.element_name, m.storey,
       t.center_x, t.center_y, t.center_z
FROM elements_meta m
JOIN element_transforms t ON m.guid = t.guid
WHERE m.ifc_class IN ('IfcSpace', 'IfcSpaceType')
  AND ABS(t.center_x - ?) < 3.0
  AND ABS(t.center_y - ?) < 3.0
ORDER BY ABS(t.center_x - ?) + ABS(t.center_y - ?)
LIMIT 1
```

### Compliance Panel (overlay during walk)
- Room name, storey
- Area: computed vs required (from IfcSpace properties if available)
- Door widths: query IfcDoor in same storey within room bounds
- Ceiling height: difference between slab above and slab below
- Status: ✅ / ❌ per check

### Files
- New `compliance.js` — space detection, compliance rules, overlay
- `walk.js` — call compliance check on position change
- `index2.html` — compliance overlay panel

## Anti-Drift
- Query only, no orientation changes
- Compliance check throttled (every 5 steps, not every frame)
