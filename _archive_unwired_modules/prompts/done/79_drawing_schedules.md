# DONE
# Drawing Schedules — Room, Door, Window from output.db

**Priority:** Drawing schedules that architects produce on every project.
Room Schedule, Door Schedule, Window Schedule. These read directly from
output.db (post-compilation), not from DAOs — they need element-level
metadata that only the compiled output has.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Schedules list what the compiler produced.
Every value comes from output.db tables. No invented attributes.

## Read first

1. `docs/REPORTING_ENGINE_SRS.md` §3 Category 2 — BIM-RPT-04/05/06 specs.
2. `docs/DATA_MODEL.md` — output.db schema: `spatial_structure` (rooms, areas),
   `elements_meta` (IfcDoor, IfcWindow, all element properties).
3. `BIMBackOffice/.../report/BomScheduleReport.java` — existing report pattern.
4. `BIMBackOffice/.../server/BackOfficeServer.java` — `/api/report` endpoint.
5. `BonsaiBIMDesigner/.../api/WebUIServer.java` — `generateReport` dispatch.

## Understanding: Two Entry Points

Same pattern as prompt 78. Reports live in BackOffice, served by both:
1. `BackOfficeServer GET /api/report?id=SH&type=room-schedule`
2. `WebUIServer generateReport { type: "room-schedule" }`

These reports read from **output.db** (the compiled building), not BOM.db.
The report generator opens a Connection to `output/{prefix}_output.db`.

## Task 1: RoomScheduleReport (BIM-RPT-04)

Create `BIMBackOffice/.../report/RoomScheduleReport.java`:

```java
// Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-04 — Witness: W-RPT-ROOM
```

**Data source:** output.db `spatial_structure` table

**Columns:**
- Room name, storey, area (m²), perimeter (m)
- Width × depth × height (mm) — from AABB
- Room type/role (from bom_category or product_category)
- Compliance status (if ComplianceReport data available, else "—")

**SQL pattern:**
```sql
SELECT element_name, storey, ifc_class,
       aabb_width_mm, aabb_depth_mm, aabb_height_mm,
       -- area computed from AABB: width × depth / 1e6
       (aabb_width_mm * aabb_depth_mm / 1000000.0) AS area_m2
FROM spatial_structure
WHERE ifc_class IN ('IfcSpace', 'IfcSpatialZone')
ORDER BY storey, element_name
```

Adapt the actual column names to what exists in output.db — read the schema first.

## Task 2: DoorScheduleReport (BIM-RPT-05)

Create `BIMBackOffice/.../report/DoorScheduleReport.java`:

```java
// Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-05 — Witness: W-RPT-DOOR
```

**Data source:** output.db `elements_meta` WHERE ifc_class = 'IfcDoor'

**Columns:**
- Mark/ID, door type, width × height (mm)
- Storey, host wall
- Fire rating (from property set, if available — else "—")
- Hardware spec (if available — else "—")

## Task 3: WindowScheduleReport (BIM-RPT-06)

Create `BIMBackOffice/.../report/WindowScheduleReport.java`:

```java
// Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-06 — Witness: W-RPT-WINDOW
```

**Data source:** output.db `elements_meta` WHERE ifc_class = 'IfcWindow'

**Columns:**
- Mark/ID, window type, width × height (mm)
- Storey, glazing type
- U-value (from property set, if available — else "—")

## Task 4: Wire to Both Entry Points

**BackOfficeServer:** Extend `/api/report` type dispatch:
```java
case "room-schedule"   -> new RoomScheduleReport().generate(outputConn, buildingId);
case "door-schedule"   -> new DoorScheduleReport().generate(outputConn, buildingId);
case "window-schedule" -> new WindowScheduleReport().generate(outputConn, buildingId);
```

**Note:** These need `outputConn` (output.db), not `bomConn`. The endpoint must
resolve the output DB path from the building prefix:
`output/{prefix}_output.db` or however output DBs are located.

**WebUIServer:** Same dispatch extension.

## Verify

1. `mvn compile -q` — PASS
2. BIMBackOffice tests — zero regression
3. SH 7/7 PASS (no pipeline changes)
4. Manual: `curl localhost:9877/api/report?id=SH&type=room-schedule` returns
   room list with areas. Cross-check one room area against output.db directly.

## What NOT to do

- Do NOT create new tables or views in output.db
- Do NOT implement PDF generation
- Do NOT touch the compilation pipeline
- Do NOT guess column names — read the actual output.db schema first
- If a column doesn't exist (fire rating, U-value), output "—", do not fabricate

## When Done

Prepend `# DONE` + commit hash to this file's first line.

Append findings:
- Actual output.db table/column names used (may differ from spec assumptions)
- Room count for SH building
- Door/window counts for SH
- Which columns were available vs "—" (identifies property extraction gaps)

---

## Findings (Session 79)

### Actual output.db tables/columns used

**spatial_structure:** guid, type, name, parent_guid, object_type, predefined_type
- Rooms identified by `type IN ('IfcSpace', 'IfcSpatialZone')`
- Storey derived from parent_guid → parent spatial_structure row (IfcBuildingStorey)
- No AABB columns in spatial_structure itself — joined via elements_meta.id → elements_rtree

**elements_meta:** id, guid, discipline, ifc_class, element_name, element_type, storey, fire_rating_hr, material_name, material_rgba, element_ref
- Doors: `ifc_class = 'IfcDoor'`, mark = element_name, type = element_type
- Windows: `ifc_class = 'IfcWindow'`, mark = element_name, type = element_type

**elements_rtree:** id, minX, maxX, minY, maxY, minZ, maxZ (AABB in meters)
- Width/height derived from max-min differences × 1000 for mm

### Room/Door/Window counts for SH

Cannot verify counts without a compiled SH output DB (no `output/SH_output.db` found in worktree). Counts depend on compilation output.

### Available vs "—" columns

| Column | RoomSchedule | DoorSchedule | WindowSchedule |
|--------|-------------|-------------|----------------|
| Name/Mark | Available (name / element_name) | Available (element_name) | Available (element_name) |
| Type | Available (object_type) | Available (element_type) | Available (element_type) |
| Storey | Available (parent join / storey) | Available (storey) | Available (storey) |
| W × D × H | Available (elements_rtree) | W × H available | W × H available |
| Area / Perimeter | Computed from AABB | N/A | N/A |
| Fire rating | N/A | Available (fire_rating_hr) | N/A |
| Host wall | N/A | "—" (rel_fills_host is reference-only, not in output schema) | N/A |
| Hardware | N/A | "—" (not in output schema) | N/A |
| Glazing type | N/A | N/A | "—" (not in output schema) |
| U-value | N/A | N/A | "—" (not in output schema) |
| Compliance | "—" (not yet linked) | N/A | N/A |

### Property extraction gaps (future work)

1. **Host wall** — `rel_fills_host` exists in reference schema but not output schema. Adding it to output_schema.sql would enable door/window → host wall mapping.
2. **Glazing type, U-value** — IFC property sets (Pset_WindowCommon) not yet extracted to output.db columns.
3. **Hardware spec** — IFC property set data not extracted.
4. **Compliance** — ComplianceReport results not yet linked to room schedule.

### Files created/modified

- NEW: `BIMBackOffice/src/main/java/com/bim/backoffice/report/RoomScheduleReport.java`
- NEW: `BIMBackOffice/src/main/java/com/bim/backoffice/report/DoorScheduleReport.java`
- NEW: `BIMBackOffice/src/main/java/com/bim/backoffice/report/WindowScheduleReport.java`
- MOD: `BIMBackOffice/src/main/java/com/bim/backoffice/server/BackOfficeServer.java` — added `/api/report` endpoint + `openOutput()` helper
- MOD: `BonsaiBIMDesigner/src/main/java/com/bim/designer/api/WebUIServer.java` — added `generateReport` action dispatch
