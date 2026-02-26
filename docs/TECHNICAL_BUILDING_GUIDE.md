# Technical Building Guide
**BIM Intent Compiler — Developer Reference**
*Date: 2026-02-23 | Replaces: 2026-02-21 edition*

> **Staleness note (2026-02-26):** References to `FurnitureBOMResolver` in §6–§7
> now refer to `BOMTierResolver` (Phase G-1 rename + unification). `ad_room_slot`
> dispatch (§6) is deprecated by `bom_category` on M_BOM. BOM tables renamed:
> `ad_bom` → `m_bom`, `ad_bom_child` → `m_bom_line`, `ad_bom_child_param` → `m_attribute`
> (all in `library/BOM.db`, separate from `component_library.db`).
>
> **Canonical references:** `docs/ConstructionAsERP.md`, `docs/METADATA_DRIVEN_ARCHITECTURE.md`

---

## 0. The 7-Step Pipeline

One engine compiles N buildings. `BuildingRegistry` loads all active rows from
`ad_building_registry`. `CompilationPipeline.run(entry)` executes this chain for each:

```
Step 1 — MetadataValidator   Referential integrity check before any data is consumed
Step 2 — ParseStage          DSL text → BuildingDefinition (rooms, grid, roof spec)
Step 3 — CompileStage        BuildingDefinition → BuildingSpec (world-coord placement records)
Step 4 — WriteStage          BuildingSpec → output.db (IFC entities, geometry, topology)
Step 5 — DigestStage         SpatialDigest — SHA-256 of all placed geometry hashes
Step 6 — GeometryStage       GeometryIntegrityChecker — LOD/GEO checks, vertex counts
Step 7 — ProveStage          PlacementProver — P-series spatial proofs (P01-P23)
```

`PipelineResult` carries elementCount, spatialDigest, proofReport, and geometryReport.
The caller (test class or CLI) decides pass/fail — the pipeline never calls `System.exit`.

**Zero Java per new building.** Add one row to `ad_building_registry`; the pipeline
discovers and runs it automatically.

---

## 1. The Three-Layer Stack — The "Showroom" Principle

```
┌──────────────────────────────────────────────────────┐
│  COMPILER (Java)                                     │
│  Reads ONLY from views — never base tables           │
├──────────────────────────────────────────────────────┤
│  VIEW CONTRACTS (SQL Views)    ← the showroom        │
│  Filter: doc_status=CO, provenance≠PENDING,          │
│          coordinate_frame≠GRID_DERIVED, dims>0       │
├──────────────────────────────────────────────────────┤
│  BASE TABLES (ad_* SQLite)     ← the workshop        │
│  Partial data, migrations in progress                │
│  Write path: migrations + extractions only           │
└──────────────────────────────────────────────────────┘
```

*"The base tables are the workshop. The views are the showroom.
The compiler only walks through the showroom."*

### 1.1 The Six Views — All Live (2026-02-23)

| View | Rows | Gate |
|---|---|---|
| `v_compilable_element_rule` | 95 | `doc_status='CO'`, `family_ref IS NOT NULL`, `dims>0` |
| `v_verified_room_boundary` | 7 | `coordinate_frame IN (IFC_GLOBAL_MM, LOCAL_MM, DRAWING_MM, CONSTRAINT_SOLVED, DERIVED_MM)` |
| `v_active_bom_assembly` | 29 | `child_count = active_children` (no partial assemblies) |
| `v_qualified_bom` | 10 | FK join `bc.product_ref = pd.product_id`, `dims>0` |
| `v_proven_geometry` | 22,013 | `vertex_count > 8`, `geometry_hash IS NOT NULL` |
| `v_component_leaf` | 28 | proven geometry + dims + not a BOM parent |

**Critical contract — Rule 5:** Never add `OR doc_status='DR'` to any view filter.
Zero rows from a view means the data is not construction-ready — not an error to handle.
No fallback. No exception. Empty = skip.

**Critical contract — Rule 2:** Any WHERE clause modification is an architecture change.
Requires watchdog review. Loosening a filter allows incomplete data into compilation.

### 1.2 The iDempiere Table-Type Mapping

| iDempiere | BIM table (current name) | Future name | Type confirmed by |
|---|---|---|---|
| C_Order | `ad_building_registry` | `C_Building_Order` | dsl_content + spatial_digest + output_db_path = full order header |
| C_OrderLine | `ad_element_rule` | `C_Element_Rule` | `provenance DEFAULT 'BUILDING_DSL'` |
| Qty × UOM (spatial) | `ad_room_boundary` | `M_Room_Boundary` | `extracted_from` + `coordinate_frame` = measurement record, not a document |
| M_Product | `ad_product_dim` + `component_definitions` | (no rename) | catalog tables, is_active gate only |
| M_BOM_Line (template) | `ad_room_slot` | — | room-type slots |
| C_BOM_Line (instance) | `ad_bom_child` + `ad_bom_child_param` | — | assembly child offsets |

**DocStatus assignment (final):**
- `ad_element_rule`: `doc_status='CO'` — the only C_ table with lifecycle this version
- `ad_room_boundary`: NO DocStatus — M_ table; quality gate is `coordinate_frame`
- `ad_bom`, `ad_bom_child`: NO DocStatus — `is_active=1` only

---

## 2. Three Compilation Modes

Every building compiles in one of three modes determined by `ad_room_boundary.coordinate_frame`:

**Mode A — Rosetta Stone (target for SH, DX)**
Room boundaries extracted from real IFC → `coordinate_frame='IFC_GLOBAL_MM'`.
The compiler positions every element in the same global frame as the reference building.
G8 gate (RosettaPlacementTest) validates against the extracted reference DB.
*Current state: SH and DX are calibration debt — 2 intentional RED tests. See §11.*

**Mode B-semi — Mixed Provenance (TB-LKTN)**
Room boundaries loaded from DSL design authority → `coordinate_frame='LOCAL_MM'`.
`v_verified_room_boundary` serves these (LOCAL_MM is a valid frame). No reference IFC.
Furniture and fixtures placed relationally from room bounds.

**Mode B-pure — Generative (no reference, no extracted boundaries)**
`v_verified_room_boundary` returns zero rows for this building.
Compiler falls back to DSL-qty generative mode:
```
space_per_unit_mm² = floor_area_mm² / qty
available_space_mm = SQRT(space_per_unit_mm²)
```
Not yet exercised for any current building — documented in `space_solver_research.md`.

**Template Topology Path (future — between B-semi and B-pure):**
Find nearest Rosetta Stone room by type + L1 dimension distance. Apply scale transform.
Write result as `coordinate_frame='DERIVED_MM'` — visible to `v_verified_room_boundary`.
Requires Phase 1e migration (extending coordinate_frame CHECK) before DERIVED_MM rows can INSERT.

---

## 3. Level 1 — C_Order: The DSL

### What the DSL is

`ad_building_registry.dsl_content` is the **order header**. It declares:
- Building identity and profile
- Structural grid (axes + spacing → room boundary coordinates)
- Room types, their grid bounds, exterior faces, adjacencies
- Roof spec

**The DSL does NOT declare furniture, fixtures, openings, MEP, or positions.**
Those are C_OrderLines in `ad_element_rule`, read through `v_compilable_element_rule`.

### DSL Example — TB-LKTN (Mode B-semi)

```
BUILDING "TB_LKTN"
    profile:  "Malaysian_Residential"
    protocol: "Residential_Single_Storey"
    lod:      400
{
    GRID {
        axes:    A, B, C, D, E / 1, 2, 3, 4, 5
        spacing: 1.3, 1.8, 3.7, 3.1 / 2.3, 3.1, 1.6, 1.5
    }

    STOREY "Ground Floor" level:0 height:3.0m {

        BEDROOM "bilik_utama" bounds:A2-C3 {
            exterior: south
            exterior: west
            opens_to: common
        }

        OPEN_PLAN "common" bounds:C2-D5 {
            zones: LIVING, DINING, KITCHEN
            exterior: south
            exterior: north
        }
    }

    ROOF pitch:25deg overhang:700mm
}
```

### Room keyword → slot dispatch mapping

| DSL Keyword | room_type | Slot assemblies dispatched |
|---|---|---|
| `BEDROOM` | BEDROOM | BED_SET, WARDROBE_SET, CEILING_MEP |
| `BATHROOM` | BATHROOM | TOILET_BLOCK_FIXTURES, BATHROOM_VANITY_SET, EXHAUST, FLOOR_TRAP |
| `LIVING` | LIVING | LIVING_SET, DINING_SET (if area≥6m²), CEILING_MEP |
| `OPEN_PLAN` | COMMON | LIVING_SET (area≥15m²), DINING_SET (area≥8m²), KITCHEN_CABINET_SET |
| `KITCHEN` | KITCHEN | KITCHEN_CABINET_SET, CEILING_MEP |
| `PORCH` | — | no slots; attached canopy only |
| `CORRIDOR` | — | no slots; walkway only |

**COMMON room** is the Malaysian terrace house hybrid (LIVING+DINING+KITCHEN in one open space).
TB-LKTN 'common' = 22.94m² → qualifies for ALL three COMMON slots.

### Adding a new building — registry entry

```sql
INSERT INTO ad_building_registry
    (building_id, building_name, building_type, provenance,
     dsl_content, output_db_path, reference_db_path,
     is_active, seq_no, expected_elements, geometry_fail_threshold)
VALUES
    ('MY_HOUSE', 'My House Type D', 'RESIDENTIAL', 'GENERATIVE',
     '...dsl...', 'DAGCompiler/lib/output/MY_HOUSE.db',
     NULL, 1, 5, 0, 0);
```

`building_type` = category (`RESIDENTIAL` / `COMMERCIAL`), NOT the building ID.
`provenance = 'GENERATIVE'` → no reference IFC. `'EXTRACTED'` → Rosetta Stone path.

---

## 4. Level 2 — C_OrderLine: Element Rules

### What an element rule is

One row = one element instance in one building. The compiler reads via
`v_compilable_element_rule` (NOT `ad_element_rule` directly). The view filters:
- `doc_status = 'CO'` — confirmed orderlines only
- `family_ref IS NOT NULL` — no orphan rules
- `pd.width > 0` — product exists in catalog with valid dims

`RelationalResolver.resolve()` produces `PlacementAD.Placement` records (world-coordinate
bounding boxes) that the writers emit as IFC entities.

### Column reference

```sql
element_ref        TEXT  -- "IfcDoor_1", "TOILET_bilik_mandi_1"
ifc_class          TEXT  -- "IfcDoor", "IfcFurnishingElement", "IfcSanitaryTerminal"
storey             TEXT  -- Must match STOREY name in DSL: "Ground Floor"
discipline         TEXT  -- ARC | STR | MEP | FURN
doc_status         TEXT  -- DR | CO | VO  (CO = confirmed, served by view)
is_active          INT   -- 1 = active, 0 = disabled (never delete rows)

-- Positioning
host_type          TEXT  -- WALL | ROOM | BUILDING
host_ref           TEXT  -- "WALL_bilik_utama_SOUTH" | "bilik_utama" | "BUILDING"
position_rule      TEXT  -- FRACTION | BOUNDARY | ABSOLUTE
position_value     REAL  -- fractionX along wall/room; or absolute cx in mm
position_value_2   REAL  -- fractionY; or perp offset mm
position_value_3   REAL  -- dZ above floor in mm (storey offset)

-- Sizing
width_mm           REAL  -- X-axis extent in mm
depth_mm           REAL  -- Y-axis extent in mm
height_extent_mm   REAL  -- Z-axis extent in mm — MUST be non-zero (P01/P03 CRITICAL)
height_mm          REAL  -- mounting height above floor (sill height for windows)

-- Identity
family_ref         TEXT  -- catalog product ID → ad_product_dim.product_id
                          -- EXTRACTED buildings may carry verbatim Revit family strings

-- Rotation
orientation        TEXT  -- radians literal OR semantic (NS | EW | ALONG_HOST)
                          -- Resolved by RelationalResolver.resolveOrientation()
                          -- via ad_product_dim.conn_points → facing direction

-- Material
material_name      TEXT  -- "Concrete - Cast-in-Place"
material_rgba      TEXT  -- "#RRGGBBAA"
```

### Three position modes

**FRACTION on WALL** — place on a specific wall face at a fraction along its length:
```sql
host_type='WALL', host_ref='WALL_bilik_mandi_NORTH',
position_rule='FRACTION', position_value=0.5,
position_value_2=0.0,
orientation='FACE_INTO_ROOM'
```
Wall ref: `WALL_{room_name}_{NORTH|SOUTH|EAST|WEST}`.

**FRACTION on ROOM** — fractional position within room bounds:
```sql
host_type='ROOM', host_ref='bilik_utama',
position_rule='FRACTION', position_value=0.5,   -- fractionX
position_value_2=0.85                            -- fractionY (0.85 = near north wall)
```
fractionY ≥ 0.5 → north side; fractionX < 0.5 → west side.

**ABSOLUTE** — hardcoded world centroid. Only acceptable for Rosetta Stone replication
(EXTRACTED buildings) where the IFC position is the source of truth:
```sql
host_type='BUILDING', host_ref='BUILDING',
position_rule='ABSOLUTE', position_value=-5059.012, position_value_2=-67.78
```

**Rule:** `height_extent_mm` MUST be non-zero. Zero → dz=0 → P01/P03 CRITICAL fail.

---

## 5. Level 3 — M_Product: The Catalog (`ad_product_dim`)

One row = one product type with intrinsic geometry. Standard parts — a toilet is
400×700mm regardless of building.

```sql
product_id     TEXT   -- "FIXTURE_TOILET" — referenced by ad_element_rule.family_ref
product_type   TEXT   -- DOOR | WINDOW | FIXTURE | FURNITURE | STRUCTURAL
width          REAL   -- metres (X-axis)
depth          REAL   -- metres (Y-axis)
height         REAL   -- metres (Z-axis)
extracted_from TEXT   -- provenance; 'PENDING' rows excluded by views
conn_points    TEXT   -- JSON: '[{"face":"BACK","type":"WASTE"}]'
                       -- BACK/WALL face → placed against wall → FACE_INTO_ROOM rotation
```

**Units are metres.** FURN_DINING_CHAIR = 0.45m. No `/1000` in placement math.

**conn_points drives orientation.** `RelationalResolver.resolveOrientation()` reads
conn_points for the product and computes rotation from the host wall face:
north wall → π, south → 0, east → π/2, west → −π/2.

**THREE-TABLE AUTHORITY** (inviolable):
```
ad_product_dim      — intrinsic geometry ONLY (width, depth, height). NEVER rotation/position.
ad_bom_child_param  — assembly-relative offset + rotation ONLY. NEVER absolute coords.
ad_element_rule     — room-relative placement ONLY. NEVER product dims stored here as truth.
```

### product_ref FK (Phase 4a — 2026-02-23)

`ad_bom_child` now carries a `product_ref TEXT REFERENCES ad_product_dim(product_id)`
nullable FK. This is the C_BOM_Line.M_Product_ID analogue — the explicit catalog link
from assembly member to product master.

`v_qualified_bom` joins via `bc.product_ref` (NOT `bc.role`). `bc.role` is a semantic
placement label ("VANITY", "TALL_CABINET_A") — different namespace from `product_id`.
Zero rows from the view for NULL product_ref = data not ready, not an error.

---

## 6. Level 4 — The BOM Cascade

### The Five Tiers — DSL to Leaf Item

The cascade has **five tiers**, not three. The `bom_type` column on `ad_bom` must carry
one of five values. Each tier is a BOM of the level below:

```
UNIT   — the complete building unit (GGF: UNIT_SH_STD, UNIT_DUPLEX_STD, UNIT_TBLKTN_STD)
  └─ FLOOR  — one floor plate (FLOOR_SH_GF_STD, FLOOR_DX_L1_STD, FLOOR_DX_L2_STD)
       └─ ROOM   — one room assembly (BEDROOM_STD, LIVING_STD, KITCHEN_STD — TARGET vocabulary)
            └─ SET    — furniture/fixture set (BED_SET, LIVING_SET, TOILET_BLOCK_FIXTURES)
                 └─ ITEM   — individual leaf product (Piano, Side_Table, Chair_Dining)
```

**Current DB state (2026-02-23):**

| bom_type | Count | Examples |
|---|---|---|
| UNIT | 3 | UNIT_DUPLEX_STD, UNIT_SH_STD, UNIT_TBLKTN_STD |
| FLOOR | 6 | FLOOR_DX_L1/L2_STD, FLOOR_SH_GF_STD, FLOOR_TBLKTN_GF_STD, FLOOR_STRUCTURAL, TYPICAL_CONDO_FLOOR |
| ROOM | 0 | BEDROOM_STD, LIVING_STD etc. — TARGET vocabulary, not yet in DB |
| SET | 25 | BED_SET, BED_SET_MASTER, LIVING_SET, DINING_SET, TOILET_BLOCK_FIXTURES ... |
| ITEM | 0 | Piano, Side_Table — not yet defined as standalone BOM entries |

**UNIT_*, FLOOR_* are the top of the tree.** They are not referenced as child_bom_id
by any other assembly — they are the unparented roots. The DSL `BUILDING` declaration
maps to the matching UNIT assembly via building profile.

**ROOM-tier assemblies (BEDROOM_STD etc.) do not yet exist in the DB.** They are the
target vocabulary documented in VIEW_CONTRACTS.md §4.3. Currently the compiler jumps
from FLOOR directly to SET via `ad_room_slot` slot dispatch. The ROOM tier is the gap
that BomTierResolver.java (Phase 4c) will fill.

**Schema debt (pre-Phase 4b):** The old CHECK constraint was `bom_type IN ('ROOM','SET','ITEM')`.
UNIT and FLOOR assemblies were incorrectly forced into `bom_type='ROOM'`. This has been
corrected — see the migration task in Phase 4b notes.

### Room Slots (`ad_room_slot`) — the template layer

```sql
room_type     TEXT   -- "BATHROOM", "BEDROOM", "COMMON"
slot_name     TEXT   -- "SANITARY", "FURNITURE", "EXHAUST"
assembly_id   TEXT   -- "TOILET_BLOCK_FIXTURES", "BED_SET" → references ad_bom.bom_id
slot_priority INT    -- lower = placed first
is_required   INT    -- 1 = compilation fails if slot cannot be filled
min_area      REAL   -- m² threshold
```

`min_area` gate: TB-LKTN bilik_utama = 9.61m² → dispatches BED_SET (min_area=0),
not BED_SET_MASTER (min_area=12). Malaysian affordable terrace correctly gets the
standard bed set, not the master suite.

### BOM Assemblies (`ad_bom` + `ad_bom_child`)

```
bom_id       TEXT   -- "UNIT_TBLKTN_STD", "BED_SET"
bom_type     TEXT   -- UNIT | FLOOR | ROOM | SET | ITEM
is_active    INT    -- 1

ad_bom_child:
bom_child_id       INT
bom_id             TEXT   -- parent assembly
role               TEXT   -- semantic label: "TALL_CABINET_A", "VANITY"
child_name_pattern TEXT   -- LIKE pattern: "Tall_Cabinet%"
product_ref        TEXT   -- FK to ad_product_dim.product_id (Phase 4a, nullable)
fit_priority       INT    -- 10=essential, 20=standard, 30=optional
min_space_mm       INT    -- available_space_mm must be >= this
is_active          INT
```

### The Full 5-Tier Cascade (target — BomTierResolver.java Phase 4c)

```
available_space_mm = MIN(width_mm, depth_mm)   -- from v_verified_room_boundary

FOR tier IN ('UNIT', 'FLOOR', 'ROOM', 'SET', 'ITEM'):
    rows = v_qualified_bom WHERE bom_type=tier
             AND min_space_mm <= available_space_mm
             ORDER BY fit_priority ASC, width_mm DESC

    placed_any = false
    FOR each row:
        IF row.min_space_mm <= remaining_space_mm:
            place(row)
            remaining_space_mm -= row.width_mm
            recurse(row.bom_id, row.width_mm, row.depth_mm)
            placed_any = true
        IF remaining_space_mm <= 0: BREAK

    IF placed_any: STOP   -- never drop tier while current tier placed anything
```

**Critical rule:** never drop to the next tier while the current tier still placed anything.

**Current state:** `v_qualified_bom` LIVE (10 rows, SET-tier). Cascade currently runs
only SET tier via `FurnitureBOMResolver`. `BomTierResolver.java` (Phase 4c) will
implement the full 5-tier walk. `ViewAccessLayer.java` (Phase 4b) gates all view access.

---

## 7. Level 5 — BOM Expansion (`FurnitureBOMResolver`)

`FurnitureBOMResolver` takes a BOM anchor placement (world X, Y, Z, rotation) and expands
it to N child placements using `ad_bom_child` + `ad_bom_child_param`.

### Data loaded (from base tables — view migration in Phase 4b)

```java
// loadBOMTree() — loads all active BOMs at startup
SELECT bc.bom_child_id, bc.bom_id, bc.role, bc.child_bom_id,
       bc.child_name_pattern, bc.sequence, bc.product_ref
FROM ad_bom_child bc
JOIN ad_bom b ON bc.bom_id = b.bom_id
WHERE b.is_active = 1

// expandBOMNode() — loads params per child
SELECT param_key, param_value FROM ad_bom_child_param
WHERE bom_child_id = ?
```

### BOMChild record fields

```java
record BOMChild(int id, String role, String childBomId, String namePattern,
                double xOffset, double yOffset, double zOffset, double rotation,
                String zone, String wallRule, double wallOffset,
                boolean backToWall, String productRef)
```

`wallRule = 'OPPOSITE_WORK'` triggers re-anchoring to the opposite wall via
`rotationToWall(anchor.rotation())` + `oppositeWall()`. Handles wardrobes placed
on the wall opposite the bed.

### dim key resolution (Phase 4a)

```java
// RelationalResolver L498, L602:
String dimKey = pf.productRef() != null ? pf.productRef() : pf.namePattern();
double[] dims = ctx.productDims().get(dimKey);
double w = (dims != null) ? dims[0] : 0.5;  // fallback: 0.5m bbox
```

`productRef` is FK-exact ("Tall_Cabinet"). `namePattern` had % suffix ("Tall_Cabinet%")
— always missed productDims map → silent bbox fallback. Phase 4a fixed 10 seeded children.
NULL product_ref children (sub-assembly dispatch) use namePattern fallback unchanged.

---

## 8. The Writers Pipeline

`WriteStage` orchestrates four writers in sequence. Each writes to the output SQLite DB:

```
BuildingWriter   — structural elements: walls (IfcPlate), slabs, stairs, beams, roof
MEPWriter        — fixtures (IfcSanitaryTerminal), furniture (IfcFurniture),
                   HVAC (IfcAirTerminal), lighting (IfcLightFixture)
OpeningWriter    — doors (IfcDoor), windows (IfcWindow)
MeshBinder       — binds library mesh geometry to FURN/ARC/MEP elements
```

### Geometry path by discipline

```
ARC/STR element, no geometry → GEN-BOX path:
    writeBoxGeometry() + ep.writeElementMeta/writeInstance + emitted++ + continue
    Logged as [GEN-BOX] — expected for walls, columns

FURN element, no geometry → MetadataMissingException (build fails — fix ad_geometry_map)

FURN/MEP with library geometry → MeshBinder.bind():
    1. Load canonical mesh from component_definitions (library coords)
    2. Compute scale: scaleX = bbox.width / mesh.width (etc.)
    3. Validate: scale factors in [0.3, 3.0] (exception: opening depth axis)
    4. Apply: GeometryEngine.scale(mesh, sx, sy, sz)
    5. Rotate: GeometryEngine.rotateZ(mesh, angle)
    6. Translate: GeometryEngine.translate(mesh, tx, ty, tz)
    7. Write LOD_-prefixed geometry hash to output DB

Openings (door/window) → DoorWindowLibraryMapper:
    transformAndWriteGeometryScaled(hash, tx, ty, tz, rotZ, scaleX, scaleY, scaleZ)
    Order: Scale → Rotate → Translate (always — order is contractual)
```

**D2 gate (DriftGuard):** `bindParametric()` must NEVER be called outside `MeshBinder`.
GEN-BOX path explicitly avoids it. ArchUnit enforces this.

### MEPWriter special cases

- `writeLight()` — Z: `minZ = ceilingZ - height`, `maxZ = ceilingZ` (fixture hangs DOWN)
- `writeDiffuser()` — size IS the half-width (not full diameter); bounds need +10mm margin
- `placeHVAC(ctx, includeDucted)` — RESIDENTIAL: `includeDucted=false` (no supply/return IfcAirTerminal)
- `writeFixture()` IfcFurniture — uses `transformAndWriteGeometryScaled()` (scaled path)

---

## 9. ParametricMesh Architecture

Five parametric mesh types registered in `ad_parametric_mesh` (2026-02-23):

| mesh_type | generator_class | Source |
|---|---|---|
| GABLE_ROOF_MY | GableRoofMesh | PREFAB_ARCHITECTURE.md specs |
| GABLE_CANOPY_MY | GableRoofMesh | TB-LKTN porch canopy |
| GABLE_PORCH_MY | GableRoofMesh | TBLKTN_HOUSE.pdf (ridge_axis=Y; Y-branch has known bug) |
| HIP_ROOF_MY | HipRoofMesh | TBLKTN_HOUSE.pdf extracted |
| DRAIN_HALFROUND_MY | HalfRoundDrainMesh | TBLKTN_HOUSE.pdf (diameter=230mm, segments_n=16) |

### Mesh2Library 5-step contract (mandatory for all new mesh types)

1. **DB record** — INSERT into `ad_parametric_mesh` (mesh_type, generator_class)
2. **Parameters** — INSERT into `ad_parametric_mesh_param` (all shape generator inputs)
3. **Product dim** — INSERT into `ad_product_dim` (intrinsic: width, depth, height in metres)
4. **Java generator** — implement `ParametricMesh` sealed interface, generate vertices from params
5. **Wire** — `MeshBinder` or `BuildingWriter` calls generator; GIC validates `GEO_`-prefixed hash

**Never register a mesh type without all 5 steps.** Scripts that insert DB rows but have
no Java generator are incomplete — the mesh does not exist until the generator compiles.

`ParametricMesh` is a **sealed interface**. Only these implement it: `GableRoofMesh`,
`HipRoofMesh`, `HalfRoundDrainMesh`. Adding a new mesh type requires extending the seal.

### ad_parametric_mesh_param

Shape parameters loaded by the generator at runtime:
```sql
param_key   TEXT  -- "pitch_deg", "overhang_mm", "ridge_axis", "segments_n"
param_value TEXT  -- always stored as string; generator parses
```

`GableRoofMesh` produces 6-vertex prism (4 eave + 2 ridge).
`HipRoofMesh` produces hip-end pyramid geometry (11 parameters).
`HalfRoundDrainMesh` produces U-channel via arc segments (segments_n=16 for LOD400).

---

## 10. Sealed Coordinate Types

All position arithmetic uses typed coordinates. Raw `double[]` is banned.

```java
// com.bim.compiler.coordinate package (sealed)
sealed interface Coordinate permits LocalCoord, StoreyCoord, WorldCoord {}

record LocalCoord(double x, double y, double z, double rotation) {
    WorldCoord toWorld(StoreyCoord storeyAnchor) { ... }  // ONLY path to WorldCoord
}

record StoreyCoord(double x, double y, double z, double rotation) {
    WorldCoord asWorld() { ... }  // package-private — not for general use
}

record WorldCoord(double x, double y, double z, double rotation) {}
```

**D8 ArchUnit gate:** `WorldCoord` may only be constructed via `LocalCoord.toWorld(StoreyCoord)`
or `StoreyCoord.asWorld()`. Any other construction path fails the gate.

**In `FurnitureBOMResolver.expandBOMNode()`:**
```java
StoreyCoord anchor = computeZoneAnchor(...);   // returns typed StoreyCoord
LocalCoord offset  = new LocalCoord(child.xOffset, child.yOffset, child.zOffset, childRotation);
WorldCoord world   = offset.toWorld(anchor);   // the only legal construction path
```

---

## 11. Validation Pipeline

### GeometryIntegrityChecker (GIC)

Runs at Step 6. Checks geometry hashes in output DB:

- `GEO_` prefix → parametric geometry (generated by ParametricMesh). Must have vertex_count ≥ LOD threshold.
- `LOD_` prefix → library geometry (placed by MeshBinder from world coords). Must have vertex_count > 8.
- BBox-only geometry (exactly 8 vertices) → ADVISORY for ARC/STR; FAIL for FURN.

### PlacementProver — P-series gates (Step 7)

| ID | Gate | Severity |
|---|---|---|
| P01 | Element height > 0 (height_extent_mm non-zero) | CRITICAL |
| P02 | Element within storey Z bounds | CRITICAL |
| P03 | Element within room XY boundary | CRITICAL |
| P16/P17 | Toilet/sink has waste connection point | CRITICAL |
| P22 | Opening mesh vertex containment | CRITICAL |
| P04–P15, P18–P21, P23 | MEP flow, clearance, alignment | ADVISORY |

`proverSkipped=true` when no `ad_room_boundary` data exists for the building
(avoids 58K+ noise violations for Terminal commercial building).

**Known ADVISORY violations (non-blocking):**
- DX: 364 P23 violations (MEP flow fittings, not drains — expected noise)
- TB-LKTN: 6 P23 violations (drain U-shape defect — deferred to CRD phase)

### SpatialDigest

SHA-256 of all geometry hashes in `element_instances`, ordered deterministically.
Changes when ANY geometry vertex is added, removed, or repositioned.
Stable across data migrations and view changes (covers vertex data, not placement bounds).

**Baseline (2026-02-22, post-BOM-2d — stable):**
```
SH       = 1f325a98537e7a54e7d12471e78aadce0471e0ab7ddef62084123043f2ce0b6f
DX       = d3c779b963eaf5643d84d96651da32ae6fb2a593c17020e329cafeae3792b749
TB-LKTN  = dd4345f4db1072c8082535efbe9148405bb5df90dc8f094c36e322d561b6e3b9
Terminal = 301b42b103eba6bce2e451729ba0781233c51eaf906fc5ae42ea094dc74e4683
```

### G8 Gate — Rosetta Placement Test

`RosettaPlacementTest` compares compiled `IfcFurniture` centroids against reference
`IfcFurnishingElement` centroids extracted into `*_extracted.db`.

Matching is **purely positional** (3D centroid nearest-neighbour) — names differ between
compiled output and Revit IFC. Distance threshold: 500mm.

```
G8-SH: 16/17 FAIL — LIVING room boundary at X(1.62, 6.27) vs reference X(−7.5, −0.01).
        Fix: re-extract SH room boundaries from reference IFC → coordinate_frame='IFC_GLOBAL_MM'
G8-DX: 139/173 FAIL — DX has 44 artificial grid cells, no relation to actual IfcSpace extents.
        Fix: extract 11 real IFC rooms from Ifc2x3_Duplex_extracted.db → replace grid cells.
```

Both RED tests are **intentional calibration debt**. The view layer (`v_verified_room_boundary`)
makes the gate explicit — once real IFC_GLOBAL_MM coordinates are loaded, G8 passes.

### TopologyMaker — PO Layer (Phase TM-PO, 2026-02-23)

`TopologyMaker/` is a sibling Maven module (no DAGCompiler dependency) that generates
compiler-ready rows from a site brief. The PO layer (`po/` package) gives each DB-backed
table a typed, lifecycle-aware Java object:

```
BasePO          — load/save/delete, dirty flags, explicit isNewRecord flag
ModelQuery<T>   — fluent OQL builder (where/join/orderBy/list/first/count)
X_*             — COLUMNNAME constants + typed getters/setters (no logic)
M_*             — beforeSave() validation, factory methods, lifecycle hooks

Tables covered:
  ad_typology_pattern  → M_AdTypologyPattern  (get() factory, toPattern() DTO bridge)
  ad_room_boundary     → M_AdRoomBoundary     (fromCell() factory, DERIVED_MM guard)
  ad_building_registry → M_AdBuildingRegistry (completeIt() DR→CO, voidIt() →VO)
```

**DERIVED_MM enforcement:** `M_AdRoomBoundary.beforeSave()` rejects any `coordinate_frame`
other than `DERIVED_MM`. The THREE-TABLE AUTHORITY rule is encoded in the object.

**Critical trap — TEXT PK `isNew()`:** `BasePO.isNew()` uses an explicit `isNewRecord` flag.
A TEXT PK (`"TERRACE_007"`) is non-blank before `save()` but the row does not yet exist
in DB. Deriving `isNew()` from PK blankness → silent 0-row UPDATE instead of INSERT.

**Test count:** 15/15 GREEN (7 original T6 + 8 new BasePO assertions).
**Docs:** `TopologyMaker/docs/TOPOLOGY_MAKER.md` (v2.0), `TopologyMaker/docs/TOPOLOGY_PO_LAYER_SPEC.md`.

---

### AD Events Schema (DriftGuard reactive layer)

Four tables in `library/component_library.db`:

```
ad_spatial_rule      — spatial constraint rules (clearance, adjacency)
ad_callout_rule      — annotation and callout trigger rules
ad_spatial_pattern   — pattern matching for spatial configurations
ad_typology_pattern  — building typology recognition patterns
```

`CompilerValidator` interface + `CompilerValidationException` in `contract` package.
M14 test verifies all four tables exist. Reactive wiring (`SpatialRuleValidator`,
`CalloutCascadeValidator`) is a future phase.

### DriftGuard — ArchUnit gates

| Gate | Rule |
|---|---|
| D1 | No `getOrDefault` calls in `..bom..` package (replaced with explicit null-check) |
| D2 | `bindParametric()` only called inside `MeshBinder` package |
| D5 | Building-ID name pattern excludes enum constants (`TERMINAL`=MEP role, `DUPLEX`=unit type) |
| D8 | `WorldCoord` only constructible via `LocalCoord.toWorld()` or `StoreyCoord.asWorld()` |

---

## 12. Current Building Inventory (2026-02-23)

| Building | ID | Elements | Mode | Digest |
|---|---|---|---|---|
| Sample House | `Ifc4_SampleHouse` | 58 | B-semi (calibration target: A) | 1f325a98 |
| Duplex | `Ifc2x3_Duplex` | 1197 | B-semi (calibration target: A) | d3c779b9 |
| Laketown | `TB_LKTN` | 138 | B-semi (LOCAL_MM) | dd4345f4 |
| Terminal | (commercial) | ~51,088 | B-semi | 301b42b1 |

**ad_element_rule rows:** DX=1,115 | SH=62 | TB-LKTN=86 (of 1,263 total CO rows).
**component_definitions:** 23,888 rows with geometry. **v_proven_geometry:** 22,013 rows.

### Position quality by building

| Building | ABSOLUTE (active) | Relational | BOM anchors | Furniture ABSOLUTE |
|---|---|---|---|---|
| SH (58) | 3 (windows — Revit corner windows) | 46 (73%) | 9 | 0 ✅ |
| DX (1197) | 261 (MEP/struct/windows — Revit extracted) | 795 (66%) | 27 | 0 ✅ |
| TB-LKTN (138) | 0 | 86 (100%) | 52 | 0 ✅ |

Furniture layer: fully relational for all three buildings.
MEP/structural ABSOLUTE rows (DX 261 rows) = future relational migration workstream.

---

## 13. Adding a New Building — End to End

**Step 1: Registry entry**
```sql
INSERT INTO ad_building_registry
    (building_id, building_name, building_type, provenance,
     dsl_content, output_db_path, is_active, seq_no)
VALUES ('MY_HOUSE', 'My House Type D', 'RESIDENTIAL', 'GENERATIVE',
        '...dsl...', 'DAGCompiler/lib/output/MY_HOUSE.db', 1, 6);
```

**Step 2: Room boundaries** (required for relational placement)
```sql
INSERT INTO ad_room_boundary
    (building_type, room_type, storey,
     min_x_mm, max_x_mm, min_y_mm, max_y_mm,
     extracted_from, coordinate_frame)
VALUES
    ('MY_HOUSE', 'BEDROOM', 'Ground Floor',
     0, 3500, 0, 4000,
     'MY_HOUSE_DSL',    -- provenance label
     'LOCAL_MM');        -- validated frame: served by v_verified_room_boundary
```
`extracted_from` must NOT be `'PENDING'` or `'GRID_DERIVED'` — view excludes them.
`coordinate_frame` must be one of the five valid values (see §1.1).

**Step 3: Element rules** (one per element — set `doc_status='CO'`)
```sql
-- Door on south wall
INSERT INTO ad_element_rule
    (building_type, storey, element_ref, ifc_class, discipline, doc_status,
     host_type, host_ref, position_rule, position_value,
     family_ref, width_mm, height_extent_mm, depth_mm, orientation, is_active)
VALUES ('MY_HOUSE', 'Ground Floor', 'IfcDoor_1', 'IfcDoor', 'ARC', 'CO',
        'WALL', 'WALL_master_bedroom_SOUTH', 'FRACTION', 0.5,
        'DOOR_D1', 900.0, 2100.0, 45.0, 'ALONG_HOST', 1);

-- Toilet: orientation NULL → resolved from conn_points BACK face
INSERT INTO ad_element_rule
    (building_type, storey, element_ref, ifc_class, discipline, doc_status,
     host_type, host_ref, position_rule, position_value,
     family_ref, width_mm, height_extent_mm, depth_mm, orientation, is_active)
VALUES ('MY_HOUSE', 'Ground Floor', 'TOILET_bath_1', 'IfcSanitaryTerminal', 'MEP', 'CO',
        'WALL', 'WALL_bathroom_NORTH', 'FRACTION', 0.5,
        'FIXTURE_TOILET', 400.0, 400.0, 700.0, NULL, 1);
```

**Step 4: Run**
```bash
mvn test -pl DAGCompiler
```
Output: `PIPELINE COMPLETE: My House Type D — N elements`

**Step 5: Record expected_elements**
```sql
UPDATE ad_building_registry SET expected_elements = N WHERE building_id = 'MY_HOUSE';
```

---

## 14. Gap List (Current as of 2026-02-23)

| Capability | Status | Path |
|---|---|---|
| TopologyMaker PO layer (BasePO + X_/M_ + ModelQuery) | ✅ Phase TM-PO | `TopologyMaker/docs/TOPOLOGY_PO_LAYER_SPEC.md` — 15/15 GREEN |
| `ViewAccessLayer.java` — compiler reads views only | ✗ Phase 4b | VIEW_CONTRACTS.md §7 — signatures specified |
| `BomTierResolver.java` — ROOM→SET→ITEM cascade state machine | ✗ Phase 4c | VIEW_CONTRACTS.md §6 — caller contract specified |
| ArchUnit gate — no base table SQL outside ViewAccessLayer | ✗ Phase 4d | Documents debt; does not require migrating all callers |
| `ad_building_registry.doc_status` — C_Order lifecycle DR/IP/CO/VO | ✅ Phase TM-PO (TopologyMaker) | `M_AdBuildingRegistry.completeIt()/voidIt()` implemented; DAGCompiler wiring = Phase 4e |
| G8-SH: room boundary calibration (16/17 FAIL) | ✗ Calibration debt | Re-extract from Ifc4_SampleHouse_extracted.db |
| G8-DX: room boundary calibration (139/173 FAIL) | ✗ Calibration debt | Extract 11 real rooms from Ifc2x3_Duplex_extracted.db |
| Phase 1e: coordinate_frame CHECK extension (DERIVED_MM, CONSTRAINT_SOLVED) | ✗ Pending | VIEW_CONTRACTS.md §4.6 — table recreation SQL specified |
| `StandardsResolver.java:245` hardcoded fallback dims | ✗ PRIME RULE violation | Replace with MetadataMissingException |
| 26 silent exception catches in MEPWriter/BuildingWriter | ✗ Advisory | Add WARN logging to all data-path catches |
| GABLE_PORCH_MY ridge_axis=Y bug in GableRoofMesh | ✗ Known defect | Java generator Y-branch positions ridge at spanM/2 not 0 |
| SH/DX conn_points orientation | ✗ Not firing | family_ref is Revit string, not catalog ID → no conn_points lookup |
| MEP/structural ABSOLUTE row migration (DX: 261 rows) | ✗ Future workstream | Convert to relational rules |
| Clear_front enforcement (door swing, toilet approach) | ✗ Future | CRD phase |
| ProvenElement gate | ✗ Future | Proof-before-write |
| Template Topology Path | ✗ Design only | `space_solver_research.md` — requires Phase 1e first |
| REFACTOR: ad_element_rule → C_Element_Rule (10 Java + 35 SQL files) | ✗ Dedicated session | Zero FK cascade risk confirmed |
| REFACTOR: ad_room_boundary → M_Room_Boundary (11 Java + 11 SQL files) | ✗ Dedicated session | — |
| REFACTOR: ad_building_registry → C_Building_Order (5 Java + 6 SQL files) | ✗ Dedicated session | — |

---

## 15. Key File Locations

```
Compiler entry:   DAGCompiler/src/main/java/com/bim/compiler/dsl/
Pipeline:         CompilationPipeline.java, BuildingRegistry.java
Resolver:         RelationalResolver.java
BOM:              library/FurnitureBOMResolver.java
Writers:          dsl/BuildingWriter.java, dsl/MEPWriter.java
Opening:          dsl/OpeningWriter.java, dsl/DoorWindowLibraryMapper.java
Mesh:             mesh/ (ParametricMesh, GableRoofMesh, HipRoofMesh, HalfRoundDrainMesh)
Coordinates:      coordinate/ (Coordinate, LocalCoord, StoreyCoord, WorldCoord)
Validation:       validation/ (GeometryIntegrityChecker, PlacementProver, SpatialDigest)
Tests:            DAGCompiler/src/test/java/com/bim/compiler/contract/
Library DB:       library/component_library.db  (57 ad_* tables + 6 views)
Output DBs:       DAGCompiler/lib/output/
Reference DBs:    DAGCompiler/lib/input/

Run all tests:    mvn test -pl DAGCompiler
TopologyMaker:    mvn test -pl TopologyMaker
Spatial check:    python3 DAGCompiler/python/spatial_checker.py <out.db> <ref.db> --discipline ARC
```

---

*For phase history: `PROGRESS.md`*
*For view contract rules: `docs/VIEW_CONTRACTS.md` (v2.0)*
*For prefab assembly hierarchy: `docs/PREFAB_ARCHITECTURE.md`*
*For Rosetta Stone strategy: `docs/TheRosettaStoneStrategy.txt`*
*For space solver path: `docs/space_solver_research.md`*
*For TopologyMaker module: `TopologyMaker/docs/TOPOLOGY_MAKER.md` (v2.0)*
*For PO layer implementation: `TopologyMaker/docs/TOPOLOGY_PO_LAYER_SPEC.md`*
