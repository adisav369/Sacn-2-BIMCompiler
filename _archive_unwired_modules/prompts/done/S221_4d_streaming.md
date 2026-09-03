# S221 — 4D Construction Streaming (phase-ordered element loading)

## Concept
No separate "play" button. The existing element streaming IS the 4D animation.
Sort the stream queue by construction phase order. The HUD shows phase names
instead of element counts. Building assembles itself as it loads.

## How it works

### Stream order (SQL sort)
The stream query in `streaming.js` currently has no ORDER BY. Add a CASE sort
using the `4D_phases.json` sequence mapping:

```sql
SELECT m.guid, i.geometry_hash, m.material_rgba, m.discipline,
       t.center_x, t.center_y, t.center_z,
       t.rotation_x, t.rotation_y, t.rotation_z,
       m.storey, m.ifc_class
FROM elements_meta m
JOIN element_instances i ON m.guid = i.guid
JOIN element_transforms t ON t.guid = m.guid
WHERE m.building = ? AND i.geometry_hash IS NOT NULL
  AND m.ifc_class != 'IfcOpeningElement'
ORDER BY
  CASE m.ifc_class
    WHEN 'IfcFooting' THEN 1
    WHEN 'IfcPile' THEN 1
    WHEN 'IfcReinforcingBar' THEN 1
    WHEN 'IfcColumn' THEN 2
    WHEN 'IfcBeam' THEN 3
    WHEN 'IfcSlab' THEN 4
    WHEN 'IfcMember' THEN 3
    WHEN 'IfcPlate' THEN 3
    WHEN 'IfcDuct' THEN 5
    WHEN 'IfcDuctSegment' THEN 5
    WHEN 'IfcDuctFitting' THEN 5
    WHEN 'IfcPipe' THEN 5
    WHEN 'IfcPipeSegment' THEN 5
    WHEN 'IfcPipeFitting' THEN 5
    WHEN 'IfcCableCarrier' THEN 5
    WHEN 'IfcCableCarrierSegment' THEN 5
    WHEN 'IfcFlowSegment' THEN 5
    WHEN 'IfcFlowFitting' THEN 5
    WHEN 'IfcEnergyConversionDevice' THEN 5
    WHEN 'IfcFlowTreatmentDevice' THEN 5
    WHEN 'IfcFlowMovingDevice' THEN 5
    WHEN 'IfcFlowStorageDevice' THEN 5
    WHEN 'IfcWall' THEN 6
    WHEN 'IfcWallStandardCase' THEN 6
    WHEN 'IfcCurtainWall' THEN 6
    WHEN 'IfcDoor' THEN 7
    WHEN 'IfcWindow' THEN 7
    WHEN 'IfcStairFlight' THEN 7
    WHEN 'IfcStair' THEN 7
    WHEN 'IfcRoof' THEN 8
    WHEN 'IfcRailing' THEN 8
    WHEN 'IfcFlowController' THEN 9
    WHEN 'IfcFlowTerminal' THEN 9
    WHEN 'IfcSanitaryTerminal' THEN 9
    WHEN 'IfcLightFixture' THEN 10
    WHEN 'IfcFurniture' THEN 10
    WHEN 'IfcCovering' THEN 10
    ELSE 6
  END,
  m.storey,
  m.ifc_class
```

Secondary sort by `storey` means: within each phase, ground floor builds before upper floors.

### HUD phase label
During streaming, detect phase transitions by checking the ifc_class of current
element against the phase map. When phase changes:
- Update HUD status: "SUBSTRUCTURE → 47 elements" ... "MEP ROUGH-IN → 312 elements"
- Brief pause (200ms) between phases for visual separation
- Phase label colour matches discipline colour

### JS phase map (embed in streaming.js or config.js)
```js
const PHASE_MAP = {
  IfcFooting: 'SUBSTRUCTURE', IfcPile: 'SUBSTRUCTURE', IfcReinforcingBar: 'SUBSTRUCTURE',
  IfcColumn: 'SUPERSTRUCTURE', IfcBeam: 'SUPERSTRUCTURE', IfcSlab: 'SUPERSTRUCTURE',
  IfcMember: 'SUPERSTRUCTURE', IfcPlate: 'SUPERSTRUCTURE',
  IfcDuct: 'MEP ROUGH-IN', IfcDuctSegment: 'MEP ROUGH-IN', IfcPipe: 'MEP ROUGH-IN',
  IfcPipeSegment: 'MEP ROUGH-IN', IfcCableCarrier: 'MEP ROUGH-IN',
  IfcWall: 'ARCHITECTURE', IfcWallStandardCase: 'ARCHITECTURE', IfcDoor: 'ARCHITECTURE',
  IfcWindow: 'ARCHITECTURE', IfcStair: 'ARCHITECTURE', IfcRoof: 'ARCHITECTURE',
  IfcRailing: 'ARCHITECTURE', IfcCurtainWall: 'ARCHITECTURE',
  IfcFlowController: 'MEP FINAL', IfcFlowTerminal: 'MEP FINAL',
  IfcSanitaryTerminal: 'MEP FINAL',
  IfcLightFixture: 'FINISHES', IfcFurniture: 'FINISHES', IfcCovering: 'FINISHES',
};
const PHASE_DEFAULT = 'ARCHITECTURE';
```

### Building card button
On the landing page building cards, change:
  `Open 3D Viewer ⚡` → `3D View ⚡`
Keep same button. The viewer always streams in 4D order — no toggle needed.
The 4D-ordered streaming is the default, not a mode.

### City mode
Same sort applies in `city.js cityLoadBuilding()` — the query there also has
no ORDER BY. Add the same CASE sort. City buildings assemble phase by phase.

## What the user sees
1. Click building card → viewer opens
2. Footings appear first, then columns rise, slabs pour, MEP rough-in threads through,
   walls go up, doors/windows slot in, finishes arrive
3. HUD reads: "SUBSTRUCTURE — 47/1,169 elements" → "SUPERSTRUCTURE — 198/1,169" → ...
4. Building assembles itself like a time-lapse — no play button, no timeline slider needed

## What this replaces
- Synchro ($25K/yr) — phase playback
- Navisworks TimeLiner ($8K/yr) — construction simulation
- Custom Gantt chart tools — phase visualization

## Files
- `deploy/dev/streaming.js` — add ORDER BY CASE to stream query, phase detection in streamTick
- `deploy/dev/city.js` — same ORDER BY in cityLoadBuilding query
- `templates/4D_phases.json` — source of truth for sequence (already exists)

## Future (not now)
- Play/pause/speed controls (overlay on streaming, setInterval throttle)
- Running cost counter ticking alongside phase
- MediaRecorder export to video
- Timeline scrub slider (rewind to any phase)

## Acceptance
- Hospital (63K elements) streams phase by phase: footings → columns → slabs → MEP → walls → doors → roof → finishes
- HUD shows current phase name, not just element count
- No new buttons or UI — just smarter sort order

## DO — Testing & Logging (implement when code lands)

All test output to `deploy/dev/tests/log/`.

**STATUS: NOT BUILT YET.** streaming.js has 0 phase references. Tests below are the spec
for the session that implements this.

### Playwright — extend `01-viewer-load.spec.js`
| Test | What | §-tag |
|------|------|-------|
| 1.NEW | Stream order follows phase sequence (footings before roof) | `§PW_4D_ORDER` |
| 1.NEW | HUD shows phase name during streaming | `§PW_4D_PHASE_HUD` |

### test_all.js
```javascript
// 4D phase ordering
const streamSrc = fs.readFileSync(path.join(DIR, 'streaming.js'), 'utf8');
ok('streaming has phase ORDER BY', streamSrc.includes('phase') || streamSrc.includes('SEQUENCE'));
```

## DO NOT
- Do not modify `deploy/sandbox/` — production
- Do not add play/pause UI in this session — just sort order
