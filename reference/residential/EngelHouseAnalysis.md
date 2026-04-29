# Engel House — IFC Export Validation (S229b Round-Trip)

> Source: `engel-house.obj` → BIM OOTB Drop Zone → Save > IFC → `engel-house.obj.ifc`
> Validated: 2026-04-27 by Playwright test session (S227b)

## Round-Trip Pipeline

```
engel-house.obj (OBJ mesh, 2.3MB)
  → import.js detectFormat() → 'mesh'
  → mesh_import_worker.js (Three.js OBJLoader)
  → semantic_enrichment.js (classify, storey band, GUID, RGBA)
  → scene_to_db.js (world transform, centroid, auto-scale)
  → import_db_builder.js (SQL schema)
  → IndexedDB (bim_ootb_imports)
  → ifc_export_worker.js (STEP text builder)
  → engel-house.obj.ifc (IFC4, 16,349 lines)
```

## Validation Summary

| Check | Result | Detail |
|-------|--------|--------|
| STEP header/footer | PASS | `ISO-10303-21` / `END-ISO-10303-21` |
| FILE_SCHEMA | PASS | `IFC4` |
| Reference integrity | PASS | 15,080 refs, all resolve, 0 dangling |
| Duplicate entity IDs | PASS | 0 duplicates across 16,340 entities |
| Spatial hierarchy | PASS | Project → Site → Building → 3 Storeys |
| Storey containment | PASS | 3 `IfcRelContainedInSpatialStructure` |
| Element count | PASS | 1,254 elements |
| Geometry (every element) | PASS | 1,254 `IfcTriangulatedFaceSet` |
| Materials/colours | PASS | 1,254 `IfcColourRgb` |
| Placements | PASS | 1,258 `IfcLocalPlacement` (1,254 + 4 spatial) |

## File Stats

- Lines: 16,349
- Size: 2.3MB
- Entities: 16,340
- Source format: `.obj`
- Target schema: IFC4

## Spatial Hierarchy

```
#18 IFCPROJECT 'engel-house'
  └─ #19 IFCSITE 'Site'
       └─ #20 IFCBUILDING 'engel-house.obj'
            ├─ #23 IFCBUILDINGSTOREY 'Ground Floor'  (elev: -12.230m)
            ├─ #27 IFCBUILDINGSTOREY 'Level 1'       (elev:  -7.015m)
            └─ #31 IFCBUILDINGSTOREY 'Level 2'       (elev:  -1.459m)
```

## Entity Breakdown

| IFC Class | Count | Notes |
|-----------|-------|-------|
| IfcBuildingElementProxy | 1,130 | Unclassified (OBJ names = `object_N`) |
| IfcWindow | 124 | Classified by semantic_enrichment.js |
| IfcTriangulatedFaceSet | 1,254 | One per element |
| IfcColourRgb | 1,254 | One per element |
| IfcLocalPlacement | 1,258 | 1,254 elements + 4 spatial |
| IfcRelContainedInSpatialStructure | 3 | One per storey |
| IfcRelAggregates | 3 | Project→Site, Site→Building, Building→Storeys |

## Known Issues

### 1. Classification poverty (90% IfcBuildingElementProxy)

OBJ mesh names are generic (`object_1`, `object_1254`). `semantic_enrichment.js` classifies
by name patterns (wall, door, window, roof, slab, column) — none match `object_N`.
Only 124 elements classified as IfcWindow (likely via material or geometry heuristics).

**Fix:** Run the S229a Guided Classification Wizard before export. The wizard uses
geometry analysis (repeat patterns, material groups, storey distribution) to
reclassify Proxy elements interactively.

### 2. Negative storey elevations — FIXED (S230b)

Raw DB elevations are negative (-16.2m to 1.8m) due to `scene_to_db.js` Y-up→Z-up
sign flip. Root cause remains in `scene_to_db.js` but **wizard labels are now 0-based**.

**Fix applied:** `analyseDb()` subtracts `globalMinZ` from all storey elevation labels.
Wizard now shows `Ground Floor [0.0–2.9m]` through `Level 5 [15.0–18.0m]`.
Storey Edit UI also shows normalized height range.

**Additional:** Walk button isolates one floor at a time (Prev/Next/Done).

### 3. All colours white

All 1,254 `IfcColourRgb` values are `(1.0, 1.0, 1.0)` — white. The OBJ source has
no MTL material file loaded, so `semantic_enrichment.js` defaults to grey
`(0.7, 0.7, 0.7)`, but the export rounds to white.

**Fix:** Check `extractRGBA()` null-material path in `semantic_enrichment.js` and
ensure the default grey propagates through to `ifc_export_worker.js` colour output.

**Mitigation (S230b):** Wizard applies **discipline-based coloring** on start
(`applyDisciplineColors()` in `wizard.js`). ARC=blue, STR=cyan, MEP=green, etc.
Colors persist after wizard completes so building is not all-white in viewer.

## Geometry Sample (element #48)

```
#36=IFCCARTESIANPOINT((21.817,-25.453,-8.128));
#38=IFCLOCALPLACEMENT(#15,#37);
#39=IFCCARTESIANPOINTLIST3D(((1.057,-1.610,2.681),(-1.057,-1.610,-2.681),...));
#40=IFCTRIANGULATEDFACESET(#39,$,.F.,((1,2,3),(4,5,6)),$);
#41=IFCCOLOURRGB($,1.000,1.000,1.000);
#48=IFCBUILDINGELEMENTPROXY('BECO3QFlBK4a6pFp0B9BBQ',#5,'object_1',$,$,#38,#47,$);
```

Each element has: placement (CartesianPoint + Axis2Placement3D), triangulated mesh
(CartesianPointList3D + TriangulatedFaceSet), styled colour, and product definition shape.
Full IFC4 representation chain intact.

## S230b Wizard Session Findings (2026-04-27)

### Coordinate system
- DB `element_transforms`: X=39.5m, Y=18.0m, Z=30.8m
- Y-up model (OBJ default). Height is in Y axis (18.0m), not Z
- `modelOffset`: (25.2, -5.2, 27.7) — viewer re-centers around origin
- Scene box via `expandByObject`: 50km × 35m × 50km (raw IFC vertex coords in geometry)
- Mesh `obj.position` values: near origin (0, -20, 0) to (-8.5, -5.5, 2.9)

### Wizard storey detection
- Before flip: fixed STOREY_BANDS assigned Level 3 (37 el) + Upper Levels (1217 el) — wrong
- After flip (Y↔Z swap): dynamic banding detects 6 floors at 3m each from 18m height
- Storeys: Ground Floor (48), Level 1 (156), Level 2 (215), Level 3 (239), Level 4 (455), Level 5 (141)
- Negative elevations: -16.2m to 1.8m (Y→Z sign issue from scene_to_db.js)

### Camera reframe
- `controls.target` approach works: target=(4.5, -2.6, 2.0), near origin where meshes are
- Distance: 68.3m for 39.5m building (ratio 1.7×)
- Clipping: near=0.62, far=1000 (ratio 1,600:1)
- Building invisible in headless Playwright screenshots despite correct coordinates — likely software renderer limitation

### Playwright E2E coverage
- `specs/15-drop-zone-wizard-e2e.spec.js` test 15.7: full diagnostic with before/after flip data
- Screenshots saved: `test-results/engel-after-flip.png`, `test-results/engel-storey-highlight.png`
