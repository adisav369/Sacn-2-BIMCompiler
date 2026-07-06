# S229 — Browser IFC Export: DB → .ifc Download
# ⚠ DO NOT REMOVE — Scope: Export IFC file from browser DB. Read the log after every run.

## Why This Is Next

BIM mandates (Singapore, UK, Spain, Dubai, Nordic) require IFC submissions. Millions of small builders can't comply — their tools (HDP, SketchUp, Rhino) can't export IFC. With S228, they can DROP an OBJ and get an IFC-structured DB in the browser. This sprint closes the loop:

```
Drop OBJ → auto-classify → wizard confirms (Y/Y/N/Y) → Download .ifc
```

One browser session. Any format in. Valid IFC out. Zero install. BIM mandate compliance for free.

## Prior Art in This Project

- ROADMAP.md §S217 — IFC export spec (6-step STEP text builder from DB)
- Enterprise.md §TODO 2 — custom exporter concept (Python/Bonsai path)
- `import_worker.js` already uses web-ifc (`ifcApi.OpenModel`, `GetFlatMesh`, etc.)
- web-ifc has write APIs: `CreateModel`, `WriteLine`, `SaveModel`, `ExportFileAsIFC`

## Architecture

```
┌─────────────────────────────────┐
│  Browser DB (sql.js)            │
│  elements_meta                  │  ifc_class, guid, storey, discipline
│  element_transforms             │  center_x/y/z, rotation
│  component_geometries           │  vertices BLOB, faces BLOB
│  project_metadata               │  project_name, building_name
└──────────────┬──────────────────┘
               │ SQL queries
               ▼
┌─────────────────────────────────┐
│  ifc_export_worker.js (NEW)     │  Web Worker (same pattern as import)
│                                 │
│  1. Create IFC model (web-ifc)  │
│  2. Build spatial hierarchy     │
│  3. Add elements + geometry     │
│  4. Serialize to STEP text      │
│  5. Return as ArrayBuffer       │
└──────────────┬──────────────────┘
               │ postMessage
               ▼
┌─────────────────────────────────┐
│  UI: "Download IFC" button      │
│  Blob → download link           │
│  file: {building_name}.ifc      │
└─────────────────────────────────┘
```

## IFC Structure to Build (from ROADMAP §S217)

### Step 1 — Project + OwnerHistory + Units

```javascript
// web-ifc API
var modelID = ifcApi.CreateModel({ schema: 'IFC4' });

// IfcProject
ifcApi.WriteLine(modelID, {
  type: IFCPROJECT,
  GlobalId: generateIFCGuid(),
  Name: projectName,           // from project_metadata
  // Units: IfcUnitAssignment (SI, METRE, RADIAN)
});
```

Source: `project_metadata` table → `project_name`, `building_name`

### Step 2 — Spatial Hierarchy

```
IfcProject
  └─ IfcSite
       └─ IfcBuilding (building_name from DB)
            ├─ IfcBuildingStorey "Ground Floor"  (from DISTINCT storey)
            ├─ IfcBuildingStorey "Level 1"
            └─ IfcBuildingStorey "Roof"
```

Source: `SELECT DISTINCT storey FROM elements_meta WHERE storey IS NOT NULL ORDER BY storey`

### Step 3 — Elements with Placement

For each element in `elements_meta`:

```javascript
// IfcLocalPlacement from element_transforms
var placement = createLocalPlacement(cx, cy, cz, rx, ry, rz);

// Create element entity (IfcWall, IfcDoor, etc.)
ifcApi.WriteLine(modelID, {
  type: typeCode,                    // from ifc_class → web-ifc type constant
  GlobalId: guid,                    // from elements_meta.guid
  Name: element_name,
  ObjectPlacement: placement,
  Representation: shapeRepresentation,  // from Step 4
});

// IfcRelContainedInSpatialStructure → link to storey
```

Source: `elements_meta` JOIN `element_transforms` ON guid

### Step 4 — Geometry (IfcTriangulatedFaceSet)

```javascript
// Read BLOBs from component_geometries
var vertices = new Float32Array(verticesBlob.buffer);
var faces = new Int32Array(facesBlob.buffer);

// Build IfcTriangulatedFaceSet
// vertices → IfcCartesianPointList3D
// faces → CoordIndex (1-based triangle indices)
```

Source: `component_geometries` via `element_instances.geometry_hash`

### Step 5 — Materials + Colours

```javascript
// material_rgba → IfcSurfaceStyleRendering
var rgba = element.material_rgba.split(',').map(Number);
// Create IfcColourRgb, IfcSurfaceStyleRendering, IfcStyledItem
```

Source: `elements_meta.material_rgba`

### Step 6 — Serialize + Download

```javascript
var ifcData = ifcApi.SaveModel(modelID);  // returns Uint8Array
// OR: ifcApi.ExportFileAsIFC(modelID) depending on API version

postMessage({ type: 'done', ifcData: ifcData.buffer }, [ifcData.buffer]);
```

Main thread receives ArrayBuffer → Blob → `<a download>` click.

## IFC Class → web-ifc Type Code Mapping

```javascript
var IFC_TYPE_MAP = {
  'IfcWall':                WebIFC.IFCWALL,
  'IfcDoor':                WebIFC.IFCDOOR,
  'IfcWindow':              WebIFC.IFCWINDOW,
  'IfcSlab':                WebIFC.IFCSLAB,
  'IfcRoof':                WebIFC.IFCROOF,
  'IfcColumn':              WebIFC.IFCCOLUMN,
  'IfcBeam':                WebIFC.IFCBEAM,
  'IfcStairFlight':         WebIFC.IFCSTAIRFLIGHT,
  'IfcRailing':             WebIFC.IFCRAILING,
  'IfcCovering':            WebIFC.IFCCOVERING,
  'IfcFooting':             WebIFC.IFCFOOTING,
  'IfcCurtainWall':         WebIFC.IFCCURTAINWALL,
  'IfcFurnishingElement':   WebIFC.IFCFURNISHINGELEMENT,
  'IfcBuildingElementProxy': WebIFC.IFCBUILDINGELEMENTPROXY,
  'IfcPipeSegment':         WebIFC.IFCPIPESEGMENT,
  'IfcDuctSegment':         WebIFC.IFCDUCTSEGMENT,
  'IfcLightFixture':        WebIFC.IFCLIGHTFIXTURE,
  'IfcSanitaryTerminal':    WebIFC.IFCSANITARYTERMINAL,
  // ... extend as needed
};
```

## UI Integration

### Export Triangle on Building Card

Blue corner triangle (top-right) with arrow-out icon. Matches `#4fc3f7` accent. Non-intrusive — doesn't clutter Open/Delete row.

```
┌─────────────────────────────┐
│ ◤ ↗                        │  ← blue triangle, top-right corner
│                             │
│  Engel House                │
│  1,254 elements · ARC  OBJ │
│  ██████████████████████     │  ← discipline bar
│                             │
│  [Open]              [x]   │
└─────────────────────────────┘
```

Click triangle → export chooser (small flyout or inline expand):

```
Export as:
  ● IFC (.ifc)        ← NEW (this sprint)
  ○ SQLite (.db)       ← existing Export DB feature

Filename: [engel-house      ] .ifc

  [Download]   [Cancel]
```

**CSS for triangle:**
```css
.export-triangle {
  position: absolute;
  top: 0; right: 0;
  width: 0; height: 0;
  border-top: 36px solid #4fc3f7;
  border-left: 36px solid transparent;
  cursor: pointer;
  opacity: 0.7;
  transition: opacity 0.15s;
}
.export-triangle:hover { opacity: 1; }
.export-triangle .arrow {
  position: absolute;
  top: -32px; right: 2px;
  color: #1a1a2e;
  font-size: 14px;
  pointer-events: none;
}
```

**Handler:**
```javascript
A.exportBuilding = async function(key) {
  // Show export chooser
  // If IFC selected → send DB to ifc_export_worker.js
  // If DB selected → existing download logic
  // Worker returns .ifc ArrayBuffer → Blob → <a download> click
};
```

## What Gets Exported vs What Doesn't

| Included | Source | IFC Entity |
|----------|--------|------------|
| Element classification | elements_meta.ifc_class | IfcWall, IfcDoor, etc. |
| Element name | elements_meta.element_name | Name attribute |
| GUID | elements_meta.guid | GlobalId |
| Position | element_transforms | IfcLocalPlacement |
| Tessellated geometry | component_geometries | IfcTriangulatedFaceSet |
| Storey assignment | elements_meta.storey | IfcBuildingStorey containment |
| Material colour | elements_meta.material_rgba | IfcSurfaceStyleRendering |
| Building name | project_metadata | IfcBuilding.Name |

| NOT Included | Why |
|-------------|-----|
| Property sets (Pset_*) | Not in DB — future: wizard could add |
| Parametric geometry | DB has tessellated only |
| Type definitions (IfcWallType) | Not tracked — each element is standalone |
| Opening relationships | Not in DB schema |
| MEP connections | Not in DB schema |

The export is **geometry-accurate IFC** — valid for viewing, submission, and quantity takeoff. Not parametric — can't edit walls in Revit. But sufficient for BIM mandate compliance.

## Testing

### Test 1 — Round-trip: IFC → import → export → re-import

```
1. Drop SampleHouse.ifc → import → DB
2. Export IFC → SampleHouse_exported.ifc
3. Drop SampleHouse_exported.ifc → import → DB2
4. Compare: element count, ifc_classes, storey assignments must match
```

### Test 2 — OBJ → classify → export → validate

```
1. Drop seaside-villa.obj → classify
2. Export IFC
3. Open exported .ifc in another IFC viewer (Bonsai, BIMvision, xeokit)
4. Verify: elements visible, classified correctly, storeys present
```

### Test 3 — web-ifc write API spike

Before full implementation, test that web-ifc's write API works in a Web Worker:
```javascript
importScripts('web-ifc-api-iife.js');
var api = new WebIFC.IfcAPI();
await api.Init();
var modelID = api.CreateModel({ schema: 'IFC4' });
// Write one wall, one storey, save → verify valid STEP file
```

## File Manifest

| File | Action |
|------|--------|
| `deploy/dev/ifc_export_worker.js` | NEW — web-ifc write worker |
| `deploy/dev/import.js` | MODIFY — add Export IFC button + handler |
| `deploy/dev/index.html` | MINOR — no new script tags (worker loads own deps) |
| `deploy/dev/test/test_ifc_export.html` | NEW — round-trip test |

## Execution Order

1. **Spike:** web-ifc write API in worker — confirm CreateModel/WriteLine/SaveModel work
2. **Minimal export:** one building, spatial hierarchy + elements with IfcTriangulatedFaceSet
3. **Materials:** IfcSurfaceStyleRendering from material_rgba
4. **UI:** Export button on card + viewer toolbar
5. **Round-trip test:** IFC → import → export → re-import → compare

## Dependencies

- S228c (axis fix) — exported coordinates must be correct IFC Z-up
- S228d (DAE loader) — nice-to-have, not blocking
- S229a guided classification wizard — done (wizard.js)

## DONE — S229b (2026-04-26)

Implemented pure STEP text builder — no web-ifc write API needed. Generates valid ISO-10303-21 IFC4 directly from DB.

**Architecture decision:** Skipped web-ifc's CreateModel/WriteLine/SaveModel API. Pure string builder is simpler, has zero WASM dependency, and produces clean STEP text. The web-ifc write API has stability issues and adds 4MB WASM for write-only use.

**Delivered:**
- `deploy/dev/ifc_export_worker.js` — Web Worker, pure STEP text generation
  - Full spatial hierarchy: IfcProject → IfcSite → IfcBuilding → IfcBuildingStorey
  - IfcTriangulatedFaceSet geometry from DB BLOBs (vertices + faces)
  - IfcSurfaceStyleRendering from material_rgba
  - IfcLocalPlacement from element_transforms
  - 50+ IFC class mappings (IfcWall, IfcDoor, IfcBeam, MEP, etc.)
  - SI units (metre, radian), IFC4 schema
- `deploy/dev/import.js` — IFC export button on import cards
- `deploy/landing2.html` — Export triangle (blue corner, top-right) on My Buildings cards
  - Flyout chooser: IFC (.ifc) or SQLite (.db)
  - Full DB read → worker → Blob → download
- `deploy/dev/test/test_ifc_export.html` — 30+ assertions
  - STEP structure validation, spatial hierarchy, element types
  - DB round-trip (buildImportDBs → SQL read → export → verify)
  - Edge cases (empty, missing geometry)
  - STEP syntax (entity ID uniqueness, no dangling references)

## NEXT SESSION — S238 IndexedDB DB Fetch Optimisation

**Issue:** `boq_charts.html` fetches the building DB from OCI every time. When the user has
already imported an IFC (or the model is in IndexedDB from a previous session), the same data
is available locally — no OCI round-trip needed.

**Scope (boq_charts.html only):**
1. On page load, check IndexedDB (`bim_ootb_models` store, or whichever key `import_db_builder.js` uses) for the building matching the `?db=` URL param (or the last-used building).
2. If found in IDB → load from IDB directly (no fetch).
3. If not found → fall back to current OCI fetch.
4. Add `§CHARTS_DB_SOURCE` log tag: `idb` | `oci`.

**Why this matters:** Variance IFC workflow — user imports IFC, sees diff, opens charts. All
data already in IDB. The OCI re-fetch is redundant and slow on mobile.

**Do NOT change:** rates.js, locale_loader.js, or the chart rendering logic. DB source only.
