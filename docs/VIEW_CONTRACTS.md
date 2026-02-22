# View Contracts — The Compiler's Data Access Layer

**Version:** 1.9
**Date:** 2026-02-23
**Status:** GOVERNING — defines the data contract between base AD tables and the compiler
**Authors:** red1 (architect) + Claude Watchdog (reviewer)
**Supplements:** PREFAB_ARCHITECTURE.md, METADATA_DRIVEN_ARCHITECTURE.md
**iDempiere analogues:** C_Order/C_OrderLine lifecycle, M_Product catalog, MRP BOM explosion

**Changes from v1.8:**
- §5.4 v_proven_geometry: corrected to live SQL — removed broken `JOIN ad_geometry_map ON
  gm.element_ref = cd.name` (namespace mismatch: Revit family:type vs library component name).
  Now filters directly on `cd.vertex_count > 8` and `cd.geometry_hash IS NOT NULL`.
  `ifc_class` sourced via correlated subquery on `gm.geometry_hash = cd.geometry_hash`.
- §5.6 v_component_leaf: same correction — `ad_geometry_map` join dropped; `cd.vertex_count`
  and `cd.geometry_hash` used directly. Live view retains `ifc_class` column and `pd.height > 0`.
- §8: View Execution Results updated — v_proven_geometry 0→22,013; v_component_leaf 0→28.
- Root cause recorded: original join assumed `gm.element_ref` and `cd.name` share a namespace
  — they do not. `ad_geometry_map.element_ref` = Revit family:type (building elements);
  `component_definitions.name` = library component names.

**Changes from v1.7:**
- §4.3, §4.4: TARGET STATE disclaimer added — BEDROOM_STD/DINING_SET vocabulary does not
  exist in DB; FLOOR_*/UNIT_* are the actual highest-tier bom_ids. §4.3/§4.4 describe the
  intended data model, not current DB state.
- §5.1: Phase 4 join resolution flagged as watchdog call — product_ref FK vs bbox path
  is an architectural decision, not a Code-alone decision.
- §10: Phase 4 row updated with explicit "WATCHDOG CALL FIRST" gate.

**Changes from v1.6:**
- §5.1, §5.2, §5.4, §5.6: execution notes added — zero-row causes confirmed from DB
- §8: new section — View Execution Results from this session
- §10 Implementation Sequence: Phase 3g (TB-LKTN seed), 3h (extracted_from seed), Phase 4 gap noted
- §4.7: migration execution record added

**Changes from v1.5:**
- §2.3 added: coordinate_frame confidence hierarchy — DERIVED_MM and CONSTRAINT_SOLVED
  are legitimate compilation inputs; GRID_DERIVED_MM is never served by any view
- v_verified_room_boundary WHERE IN clause extended to include DERIVED_MM and CONSTRAINT_SOLVED
- §4.6 added: CHECK constraint extension for ad_room_boundary.coordinate_frame
- Rule 7 updated to reflect extended valid coordinate_frame set

**Changes from v1.4:**
- §4.1 migration block replaced with intent guidelines — Code guards against current schema state
- §4.2 migration block replaced with intent guidelines — DEFAULT/CHECK consistency enforced by Code
- §4.2 ad_geometry_map ALTER TABLE removed — column provenance already exists (DEFAULT 'LIBRARY')
- Version bumped to 1.5

**Changes from v1.3:**
- §2 Table-type mapping confirmed against actual DB schema
- ad_room_boundary: doc_status removed — M_ table, quality gate is coordinate_frame
- ad_element_rule: doc_status kept — confirmed C_OrderLine by BUILDING_DSL provenance
- ad_building_registry: DocStatus lifecycle documented, execution deferred
- Rename mapping (ad_ → C_/M_) recorded as authoritative, execution deferred to
  REFACTOR session — 35 migration files + 10 Java files for ad_element_rule alone
- v_verified_room_boundary: doc_status filter replaced by coordinate_frame gate

---

> *"The base tables are the workshop. The views are the showroom.
> The compiler only walks through the showroom."*

---

## 1. iDempiere Table-Type Mapping — Authoritative

This mapping is confirmed against the actual DB schema. It governs all naming decisions,
DocStatus assignments, and view filter choices. Renames are deferred; the mapping is not.

```
iDempiere          BIM table (current name)     Future name          Type confirmed by
─────────────────────────────────────────────────────────────────────────────────────────
C_Order            ad_building_registry         C_Building_Order     dsl_content +
                                                                      spatial_digest +
                                                                      output_db_path
                                                                      = full order header

C_OrderLine        ad_element_rule              C_Element_Rule       provenance DEFAULT
                                                                      'BUILDING_DSL' —
                                                                      the table named
                                                                      itself

Qty × UOM          ad_room_boundary             M_Room_Boundary      extracted_from +
(spatial quantity)                                                    coordinate_frame =
                                                                      measurement record,
                                                                      not a document

M_Product          ad_bom / component_          (no rename needed)   catalog tables,
                   definitions                                        is_active only
```

### 1.1 DocStatus Assignment — Final

| Table | Type | DocStatus | Gate used in views |
|---|---|---|---|
| `ad_building_registry` | C_Order header | Deferred (future session) | is_active for now |
| `ad_element_rule` | C_OrderLine | **YES — kept** | `doc_status = 'CO'` |
| `ad_room_boundary` | M_ spatial qty | **NO — removed** | `coordinate_frame NOT IN ('GRID_DERIVED_MM')` |
| `ad_bom` | M_BOM | NO | `is_active = 1` |
| `ad_bom_child` | M_BOMLine | NO | `is_active = 1` |
| `component_definitions` | M_Product leaf | NO | `extracted_from NOT LIKE '%PENDING%'` |
| `ad_product_dim` | M_Product attr | NO | `extracted_from NOT LIKE '%PENDING%'` |
| `ad_geometry_map` | M_Product geom | NO | `provenance NOT LIKE '%PENDING%'` |

### 1.2 ad_building_registry — Future DocStatus Lifecycle

When DocStatus is added (future session), the lifecycle is:

```
DR  — DSL being authored, not yet submitted for compilation
IP  — Compilation in progress (IFC being generated)
CO  — IFC issued, SpatialDigest confirmed and recorded
VO  — Superseded by a newer building order for the same project
```

This mirrors C_Order exactly. No action this session.

### 1.3 Rename Scope — Recorded, Execution Deferred

Confirmed by DB analysis. No FK dependents on any of the three tables — renames carry
zero cascade risk. Execution deferred because ad_element_rule touches 35 migration files
and 10 Java source files — a dedicated REFACTOR session, not mixed into this data layer
session.

| Current name | Target name | Java files | SQL files |
|---|---|---|---|
| `ad_building_registry` | `C_Building_Order` | 5 files, 10 refs | 6 files, 16 refs |
| `ad_element_rule` | `C_Element_Rule` | 10 files, 46 refs | 35 files, 131 refs |
| `ad_room_boundary` | `M_Room_Boundary` | 11 files, 34 refs | 11 files, 49 refs |

---

## 2. Why This Document Exists

### 2.1 The Problem

The compiler has been deciding validity at runtime:
- Java resolver methods checking for null and throwing MetadataMissingException
- Silent fallbacks that mask missing data (dims=null → 0.5×0.5×1.0m BBox)
- No clear signal distinguishing "data missing" from "data not ready"

**The view contract layer solves this permanently.** A compiler query returning zero rows
means the data is not construction-ready — not an error to handle, simply a clean signal
to skip. No exception. No fallback. No silent failure.

### 2.2 The Three-Layer Stack

```
┌─────────────────────────────────────────────────────┐
│  COMPILER (Java)                                    │
│  Reads ONLY from views — never base tables          │
├─────────────────────────────────────────────────────┤
│  VIEW CONTRACTS (SQL Views)       ← THIS DOCUMENT   │
│  Stateless — all state in caller                    │
│  Filter: doc_status=CO, provenance≠PENDING,         │
│          coordinate_frame≠GRID_DERIVED, dims>0      │
├─────────────────────────────────────────────────────┤
│  BASE TABLES (ad_* SQLite)                          │
│  Workshop — partial data, migrations in progress    │
│  Write path: migrations and extractions only        │
└─────────────────────────────────────────────────────┘
```

### 2.3 Coordinate Frame Confidence Hierarchy

The `coordinate_frame` column in `ad_room_boundary` is the **sole quality gate** for spatial
data. It ranks coordinate provenance from highest fidelity to excluded. The view
`v_verified_room_boundary` serves rows where `coordinate_frame` is one of the five valid values;
`GRID_DERIVED_MM` is never served.

```
coordinate_frame         Source                                     Served by view?
────────────────────────────────────────────────────────────────────────────────────
IFC_GLOBAL_MM            Extracted from real IFC (highest)          YES
LOCAL_MM                 Verified local coordinate system           YES
DRAWING_MM               From construction drawings                 YES
CONSTRAINT_SOLVED        Computed by SpaceSolver from UBBL +        YES
                         adjacency rules
DERIVED_MM               Scaled/transformed from Rosetta Stone      YES
                         reference (Template Topology Path)
────────────────────────────────────────────────────────────────────────────────────
GRID_DERIVED_MM          Hand-approximated grid (excluded)          NO
```

**Notes:**
- `DERIVED_MM` and `CONSTRAINT_SOLVED` are legitimate compilation inputs. They arrive from
  the Template Topology Path or SpaceSolver respectively, not from hand entry.
  See `space_solver_research.md` for the Template Topology algorithm.
- `GRID_DERIVED_MM` is **never served by any view**. It signals that a room boundary was
  approximated by hand from a sketch or grid — suitable for exploration, never for compilation.
- Any new `coordinate_frame` value defaults to excluded. Only the five listed values above
  are valid. See §4.6 for the CHECK constraint extension migration.

---

## 3. BOM Type Cascade — The Compiler's Core Strategy

### 3.1 Governing Principle

A BOM is a Product. DINING_SET is a product. BEDROOM_STD is a product. The same cascade
logic applies at every nesting level — only the space envelope and bom_type bind parameter
change between levels. This is the GardenWorld Patio Furniture Set model applied to
buildings.

**Tier order:**
```
ROOM  → room-level assemblies  (BEDROOM_STD, LIVING_STD, KITCHEN_STD)
SET   → furniture/fixture sets (DINING_SET, SOFA_SET, BED_SET)
ITEM  → leaf products          (Piano, Side_Table, Chair_Dining)
```

**Critical rule: never drop a tier while the current tier still has fitting BOMs.**
Only when no ROOM-type BOM fits the remaining space does the compiler cascade to SET.
This prevents individual chairs filling a house before room assemblies are placed.

Within a tier: `ORDER BY fit_priority ASC, width_mm DESC` — essential items first,
largest fitting item first. Closest fit reflects real contractor behaviour.

### 3.2 Two Operating Modes

**Mode A — Rosetta Stone (SH, DX, TB-LKTN)**
Room boundaries are extracted and stored in `ad_room_boundary`. The compiler reads
`MIN(width_mm, depth_mm)` from `v_verified_room_boundary` as `available_space_mm`.

**Mode B — Generative (DSL-only, no reference IFC)**
The DSL Orderline carries `qty`. The compiler derives space per unit:
```
space_per_unit_mm² = floor_area_mm² / qty
available_space_mm = SQRT(space_per_unit_mm²)
```
Qty on the Orderline is the division key — directly analogous to how MRP uses
Orderline quantity to drive BOM explosion quantity.

### 3.3 Recursive Application

The cascade applies at every nesting level. Each placed BOM's interior becomes the
envelope for the next cascade invocation:

```
Floor (120m²)
  → ROOM: BEDROOM_STD placed, remaining floor recalculated → recurse
  → ROOM: LIVING_STD placed,  remaining floor recalculated → recurse
  → ROOM: nothing fits remainder → cascade to SET at floor level

    BEDROOM_STD interior (3100×3100mm)
      → SET: BED_SET placed,   remaining recalculated
      → SET: WARDROBE placed,  remaining recalculated
      → SET: nothing fits      → cascade to ITEM
        → ITEM: Side_Table placed → stop

    LIVING_STD interior (5000×5000mm)
      → SET: DINING_SET placed, remaining recalculated
      → SET: SOFA_SET placed,   remaining recalculated
      → SET: COFFEE_TABLE placed (min_space_mm=0)
      → SET: nothing fits       → stop
```

One view. One cascade. One stateless mechanism. Applied recursively at every level.

### 3.4 User as Final Arbiter

The compiler produces a first-pass set of Placement Orderlines. The Bonsai GUI editor
lets the user inspect every line, remove items, swap sets, or add a piano manually.
The compiler's job is a **sane, non-embarrassing starting state**. The user makes it right.

---

## 4. Schema Migrations (Run in Order Before Creating Views)

### 4.1 Phase 1 — Correct doc_status Assignments

```sql
-- INTENT: ad_element_rule must have doc_status = DR/CO/VO
-- INTENT: ad_room_boundary, ad_bom_child, component_definitions must NOT have doc_status
-- Guard all ADD COLUMN and DROP COLUMN against current schema state before executing.
-- Verify after: exactly one table (ad_element_rule) carries doc_status.
```

### 4.2 Phase 1b — Add Provenance Columns

```sql
-- INTENT: component_definitions and ad_product_dim need an extracted_from column
-- that gates out unproven data. DEFAULT and CHECK must be consistent —
-- the DEFAULT value must pass the CHECK. Code to confirm existing defaults
-- on both tables and align accordingly.
--
-- INTENT: ad_geometry_map already has provenance TEXT DEFAULT 'LIBRARY' (confirmed).
-- No migration needed. Views use gm.provenance as-is.
-- Guard all ADD COLUMN operations against current schema state.
```

### 4.3 Phase 1c — Add bom_type to ad_bom

> **TARGET STATE — not current DB vocabulary.**
> The bom_ids listed below (BEDROOM_STD, LIVING_STD, DINING_SET etc.) are the
> intended assembly vocabulary for the generative BOM cascade. They do not yet exist
> in `ad_bom`. The actual highest-tier assemblies in the DB are `FLOOR_*/UNIT_*`
> (confirmed in §4.7 migration record). These sections describe the data model target —
> the SQL below is correct as a future seed, not a rerunnable migration against current data.

```sql
ALTER TABLE ad_bom
ADD COLUMN bom_type TEXT NOT NULL DEFAULT 'SET'
    CHECK(bom_type IN ('ROOM', 'SET', 'ITEM'));

UPDATE ad_bom SET bom_type = 'ROOM'
    WHERE bom_id IN ('BEDROOM_STD','LIVING_STD','KITCHEN_STD',
                     'BATHROOM_STD','CORE_STD');

UPDATE ad_bom SET bom_type = 'SET'
    WHERE bom_id IN ('DINING_SET','SOFA_SET','BED_SET','BED_SET_MASTER',
                     'WARDROBE_PAIR','WARDROBE_SET','TOILET_BLOCK_FIXTURES',
                     'KITCHEN_COUNTER_SET','LIVING_SET');

UPDATE ad_bom SET bom_type = 'ITEM'
    WHERE bom_id IN ('Piano','Side_Table','Chair_Dining','Bed_King',
                     'Bed_Queen','Sofa_3Seat','Coffee_Table','Armchair');
```

### 4.4 Phase 1d — Add BOM Spatial Qualification Columns

> **TARGET STATE — not current DB vocabulary.**
> The role names in the seed UPDATEs below (DINING_SET, BED_KING, WARDROBE_PAIR etc.)
> are the intended spatial qualification vocabulary. They do not exist in `ad_bom_child.role`
> in the current DB — confirmed in §4.7 migration record (only COFFEE_TABLE matched).
> `ad_bom_child.role` is a semantic placement label namespace, not the same as `ad_bom.bom_id`.
> The ADD COLUMN migrations have been executed; the seed UPDATEs are target data pending
> the BOM vocabulary build-out in Phase 4.

```sql
ALTER TABLE ad_bom_child
ADD COLUMN fit_priority INTEGER NOT NULL DEFAULT 20
    CHECK(fit_priority IN (10, 20, 30));
-- 10 = essential, 20 = standard, 30 = optional

ALTER TABLE ad_bom_child
ADD COLUMN min_space_mm INTEGER NOT NULL DEFAULT 0
    CHECK(min_space_mm >= 0);
-- Caller checks: min_space_mm <= available_space_mm

-- Seed values [RESEARCHED from PREFAB_ARCHITECTURE.md]
-- NOTE: roles below are TARGET vocabulary — not yet in ad_bom_child.role
UPDATE ad_bom_child SET fit_priority=10, min_space_mm=2400 WHERE role='DINING_SET';
UPDATE ad_bom_child SET fit_priority=20, min_space_mm=3000 WHERE role='SOFA_SET';
UPDATE ad_bom_child SET fit_priority=30, min_space_mm=0    WHERE role='COFFEE_TABLE';
UPDATE ad_bom_child SET fit_priority=10, min_space_mm=1800 WHERE role='BED_KING';
UPDATE ad_bom_child SET fit_priority=10, min_space_mm=1400 WHERE role='BED_QUEEN';
UPDATE ad_bom_child SET fit_priority=20, min_space_mm=2400 WHERE role='WARDROBE_PAIR';
UPDATE ad_bom_child SET fit_priority=20, min_space_mm=1200 WHERE role='WARDROBE_SINGLE';
UPDATE ad_bom_child SET fit_priority=30, min_space_mm=0    WHERE role='BEDSIDE_TABLE';
```

### 4.5 Phase 2 — Seed CO for Verified Element Rules

```sql
-- ad_element_rule is the only C_ table with doc_status this session
UPDATE ad_element_rule
SET doc_status = 'CO'
WHERE family_ref IS NOT NULL
  AND provenance IN ('BUILDING_DSL', 'EXTRACTED');
```

### 4.6 Phase 1e — Extend coordinate_frame CHECK Constraint

SQLite does not support ALTER TABLE … MODIFY COLUMN. To extend the CHECK constraint on
`ad_room_boundary.coordinate_frame`, recreate the table in a migration:

```sql
-- INTENT: Add DERIVED_MM and CONSTRAINT_SOLVED as valid coordinate_frame values.
-- These are legitimate compiler inputs produced by the Template Topology Path
-- (space_solver_research.md) and SpaceSolver respectively.
-- GRID_DERIVED_MM remains valid as a storage value but is excluded by the view filter.
--
-- In SQLite, CHECK constraints cannot be altered in-place.
-- Recreate the table preserving all data:

BEGIN TRANSACTION;

CREATE TABLE ad_room_boundary_new (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type    TEXT NOT NULL,
    room_type        TEXT NOT NULL,
    storey           TEXT,
    min_x_mm         REAL NOT NULL,
    max_x_mm         REAL NOT NULL,
    min_y_mm         REAL NOT NULL,
    max_y_mm         REAL NOT NULL,
    extracted_from   TEXT NOT NULL DEFAULT 'PENDING',
    coordinate_frame TEXT NOT NULL DEFAULT 'GRID_DERIVED_MM'
        CHECK(coordinate_frame IN (
            'IFC_GLOBAL_MM',
            'LOCAL_MM',
            'DRAWING_MM',
            'CONSTRAINT_SOLVED',
            'DERIVED_MM',
            'GRID_DERIVED_MM'
        ))
    -- add any other columns from the live table here
);

INSERT INTO ad_room_boundary_new SELECT * FROM ad_room_boundary;
DROP TABLE ad_room_boundary;
ALTER TABLE ad_room_boundary_new RENAME TO ad_room_boundary;

COMMIT;

-- Verify digests unchanged after migration:
-- SH=1f325a98, DX=d3c779b9, TB=dd4345f4, Terminal=301b42b1
```

**SpatialDigest check after every phase:**
```
SH=1f325a98, DX=d3c779b9, TB=dd4345f4, Terminal=301b42b1
```

### 4.7 Migration Execution Record (2026-02-23)

All Phase 1→2 migrations applied and verified:

```
Phase 1:  doc_status on exactly one table confirmed — ad_element_rule only ✓
Phase 1b: extracted_from added to component_definitions and ad_product_dim ✓
          ad_geometry_map.provenance already existed — no migration applied ✓
Phase 1c: bom_type added to ad_bom
          Seeded: 9 ROOM-tier (FLOOR_*/UNIT_*), 26 SET-tier (all others)
          NOTE: document seed list (BEDROOM_STD, LIVING_STD etc.) does not match
          actual bom_ids — those names do not exist in ad_bom. FLOOR_*/UNIT_*
          are the actual highest-tier assemblies present.
Phase 1d: fit_priority and min_space_mm added to ad_bom_child
          Seed result: only COFFEE_TABLE matched role='COFFEE_TABLE'
          Other roles in seed script (DINING_SET, BED_KING, WARDROBE_PAIR etc.)
          do not exist in ad_bom_child.role — those names are in ad_bom.bom_id,
          not in ad_bom_child.role. Role namespace confirmed as semantic labels.
Phase 2:  1263 ad_element_rule rows seeded CO
          Ifc2x3_Duplex: 1115 | Ifc4_SampleHouse: 62 | TB_LKTN: 86
Phases 3a-3f: all six views created in DB ✓
SpatialDigests throughout: SH=1f325a98, DX=d3c779b9, TB=dd4345f4, Terminal=301b42b1
mvn test: 119 tests, 2 failures (pre-existing G8 calibration — intentional RED) ✓
```

---

## 5. Six Core Views

### 5.1 v_qualified_bom

Central view. Returns BOM products that are active, typed, and carry proven dimensions.
Caller supplies `bom_type` and `available_space_mm` as bind parameters per cascade step.
Never called without both — a query without `bom_type` returns a mixed-tier result, which
is a caller bug.

```sql
CREATE VIEW v_qualified_bom AS
SELECT
    b.bom_id,
    b.bom_name,
    b.bom_type,
    bc.bom_child_id,
    bc.role,
    bc.sequence,
    bc.fit_priority,
    bc.min_space_mm,
    bc.child_name_pattern,
    pd.width            AS width_mm,
    pd.depth            AS depth_mm,
    pd.height           AS height_mm,
    pd.extracted_from
FROM ad_bom b
JOIN ad_bom_child bc
    ON bc.bom_id = b.bom_id
    AND bc.is_active = 1
JOIN ad_product_dim pd
    ON pd.product_id = bc.role
    AND pd.width > 0
    AND pd.depth > 0
    AND pd.height > 0
    AND pd.extracted_from NOT LIKE '%PENDING%'
WHERE b.is_active = 1;

-- Caller always adds:
--   WHERE bom_type = ?              -- one tier per query
--   AND min_space_mm <= ?           -- current available_space_mm
--   ORDER BY fit_priority ASC, width_mm DESC
```

> **Execution note (2026-02-23): 0 rows.** Join key gap confirmed by DB query.
> `bc.role` is a semantic placement label (`BED`, `CHAIR_A`, `BATHROOM`) — it never
> intersects `ad_product_dim.product_id` (`FURN_BED_SINGLE`, `DOOR_D1`). These are
> different namespaces. Two resolution paths for Phase 4:
> (a) Add a `product_ref` FK column to `ad_bom_child` pointing to `ad_product_dim`
>     — one extra column, explicit link, no LIKE join.
> (b) Source dimensions from `component_definitions` bounding box columns
>     (`local_max_x - local_min_x` etc.) via the existing `child_name_pattern` LIKE join.
>
> **⚑ WATCHDOG CALL REQUIRED before Phase 4 begins.**
> This decision has architectural weight — it determines how product identity propagates
> through the BOM cascade and whether the view layer becomes FK-typed or pattern-matched.
> Code must not resolve this alone. Convene watchdog review, present both options with
> trade-offs, obtain decision before writing any Phase 4 Java or migration.
> The view SQL above remains as the contract to satisfy — whichever join path is chosen
> must produce equivalent rows through this view definition.

### 5.2 v_verified_room_boundary

Spatial quantity records with proven coordinate provenance. GRID_DERIVED_MM is the
sentinel for "not yet extracted from real IFC" — excluded by the coordinate_frame filter.
No DocStatus needed — this is an M_ table, quality gate is provenance not lifecycle.

```sql
CREATE VIEW v_verified_room_boundary AS
SELECT
    rb.id                           AS room_id,
    rb.building_type,
    rb.room_type,
    rb.min_x_mm,
    rb.max_x_mm,
    rb.min_y_mm,
    rb.max_y_mm,
    rb.storey                       AS storey_id,
    rb.extracted_from,
    rb.coordinate_frame,
    (rb.max_x_mm - rb.min_x_mm)    AS width_mm,
    (rb.max_y_mm - rb.min_y_mm)    AS depth_mm
FROM ad_room_boundary rb
WHERE rb.coordinate_frame IN (
        'IFC_GLOBAL_MM',       -- extracted from real IFC (highest confidence)
        'LOCAL_MM',            -- verified local coordinate system
        'DRAWING_MM',          -- from construction drawings
        'CONSTRAINT_SOLVED',   -- computed by SpaceSolver from UBBL + adjacency rules
        'DERIVED_MM'           -- scaled/transformed from Rosetta Stone reference
    )
    AND rb.extracted_from NOT IN ('PENDING','','TODO','UNKNOWN','GRID_DERIVED');
```

`width_mm` and `depth_mm` are derived here. Caller computes:
`available_space_mm = MIN(width_mm, depth_mm)` and passes to `v_qualified_bom`.
No arithmetic in compiler Java.

**What this eliminates:**
- GRID_DERIVED coordinates reaching compilation (the living-room-9m-east class)
- Hand-crafted coordinates without a declared frame (unknown frame = invisible)

> **Execution note (2026-02-23): 0 rows.** All 51 `ad_room_boundary` rows carry
> `extracted_from = 'GRID_DERIVED'` — correctly excluded by the view gate.
> SH/DX exclusion is correct (wrong coordinates, calibration deferred).
> TB-LKTN exclusion is a provenance label issue: coordinates are correct by design
> authority but were loaded with `GRID_DERIVED`. Watchdog-approved remediation:
> ```sql
> UPDATE ad_room_boundary
> SET extracted_from = 'TB_LKTN_DSL'
> WHERE building_type = 'TB_LKTN';
> ```
> Execute in next session (Phase 3g). After this UPDATE, TB-LKTN rooms will be
> visible to the view. Verify SpatialDigests stable after execution.

### 5.3 v_compilable_element_rule

Placement Orderlines (C_OrderLine) where every referenced product exists and is verified.
This view is the compiler's read path for confirmed placement instances.

```sql
CREATE VIEW v_compilable_element_rule AS
SELECT
    er.element_ref,
    er.ifc_class,
    er.family_ref,
    er.building_type,
    er.storey                       AS storey_id,
    er.position_rule,
    er.position_value               AS position_value_1,
    er.position_value_2,
    er.position_value_3,
    er.orientation,
    pd.width                        AS width_mm,
    pd.depth                        AS depth_mm,
    pd.height                       AS height_mm
FROM ad_element_rule er
JOIN ad_product_dim pd
    ON pd.product_id = er.family_ref
    AND pd.width > 0
WHERE er.position_value_3 >= 0
    AND er.doc_status = 'CO'
    AND er.family_ref IS NOT NULL;
```

### 5.4 v_proven_geometry

Components with geometry extracted from a real reference. BBox placeholders (exactly 8
vertices) are excluded. `vertex_count` is sourced directly from `component_definitions`
(no join to `component_geometries` needed). `ifc_class` retrieved via correlated subquery
on `geometry_hash` — the only bridge between component library and geometry map namespaces.

```sql
CREATE VIEW v_proven_geometry AS
SELECT
    cd.id,
    cd.name,
    (SELECT MIN(gm.ifc_class)
     FROM ad_geometry_map gm
     WHERE gm.geometry_hash = cd.geometry_hash) AS ifc_class,
    cd.geometry_hash,
    cd.vertex_count,
    cd.extracted_from               AS geometry_source
FROM component_definitions cd
WHERE cd.vertex_count > 8
  AND cd.extracted_from NOT LIKE '%PENDING%'
  AND cd.geometry_hash IS NOT NULL;
```

**Design note — why not `JOIN ad_geometry_map ON gm.element_ref = cd.name`:**
`ad_geometry_map.element_ref` is keyed on Revit family:type strings (e.g.,
`"Doors_IntSgl:810x2110mm"`). `component_definitions.name` is keyed on library
component names (e.g., `"Base_Cabinet"`, `"jkrME18_spr_sprinkler head_pendent"`).
These namespaces never intersect. The geometry_hash bridge is the correct path.
`MIN()` deduplicates where multiple `ad_geometry_map` rows share a hash (~2.8× avg).

**What this eliminates:**
- BBox placeholders in IFC output (vertex_count > 8)
- The LodGeometryXRayTest X1-X4 detection class (prevention replaces detection)

> **Execution note (2026-02-23): LIVE — 22,013 rows** after Phase 3h seed.
> Root cause of earlier zero-row result: `ad_geometry_map JOIN ON element_ref = cd.name`
> was a namespace mismatch — zero hits. Corrected view + `extracted_from` seed together
> unblocked the view. Verified: `SELECT COUNT(*) FROM v_proven_geometry` → 22,013.

### 5.5 v_active_bom_assembly

Complete BOM assemblies where every child is active. Compiler uses this to confirm a
BOM is fully populated before attempting expansion.

```sql
CREATE VIEW v_active_bom_assembly AS
SELECT
    b.bom_id,
    b.bom_name,
    b.bom_type,
    COUNT(bc.bom_child_id)                              AS child_count,
    SUM(CASE WHEN bc.is_active = 1 THEN 1 ELSE 0 END)  AS active_children
FROM ad_bom b
JOIN ad_bom_child bc ON bc.bom_id = b.bom_id
WHERE b.is_active = 1
GROUP BY b.bom_id, b.bom_name, b.bom_type
HAVING child_count = active_children;
```

### 5.6 v_component_leaf

Individual items at the bottom of any expansion — not a BOM parent, directly placeable.
Used when the cascade reaches bom_type = 'ITEM'. Geometry existence confirmed via
`cd.vertex_count > 8` and `cd.geometry_hash IS NOT NULL` — no join to `component_geometries`
needed. `ifc_class` via same correlated subquery as v_proven_geometry.

```sql
CREATE VIEW v_component_leaf AS
SELECT
    cd.id,
    cd.name,
    (SELECT MIN(gm.ifc_class)
     FROM ad_geometry_map gm
     WHERE gm.geometry_hash = cd.geometry_hash) AS ifc_class,
    pd.width                        AS width_mm,
    pd.depth                        AS depth_mm,
    pd.height                       AS height_mm,
    cd.geometry_hash,
    cd.extracted_from               AS geometry_source
FROM component_definitions cd
JOIN ad_product_dim pd
    ON pd.product_id = cd.name
    AND pd.width  > 0
    AND pd.depth  > 0
    AND pd.height > 0
    AND pd.extracted_from NOT LIKE '%PENDING%'
WHERE cd.vertex_count > 8
  AND cd.extracted_from NOT LIKE '%PENDING%'
  AND cd.geometry_hash IS NOT NULL
  AND cd.name NOT IN (SELECT bom_id FROM ad_bom WHERE is_active = 1);
```

**Design note:** same namespace correction as §5.4 — `ad_geometry_map JOIN ON
element_ref = cd.name` was wrong (zero hits). Dimensions come from `ad_product_dim`
via `pd.product_id = cd.name` which is the correct shared namespace (28 hits confirmed).

> **Execution note (2026-02-23): LIVE — 28 rows** after Phase 3h seed + view SQL correction.
> Blockers resolved: (1) view SQL corrected to drop broken `gm.element_ref = cd.name` join;
> (2) `UPDATE ad_product_dim SET extracted_from = provenance` seeded all 52 rows.
> Verified: `SELECT COUNT(*) FROM v_component_leaf` → 28.

---

## 6. Compiler Caller Contract

Views are stateless. All cascade state lives in the caller.

```
Input:  space_envelope (width_mm, depth_mm)
        available_space_mm = MIN(width_mm, depth_mm)

FOR tier IN ('ROOM', 'SET', 'ITEM'):
    rows = query(v_qualified_bom,
                 bom_type = tier,
                 min_space_mm <= available_space_mm,
                 ORDER BY fit_priority ASC, width_mm DESC)

    placed_any = false
    FOR each row IN rows:
        IF row.min_space_mm <= remaining_space_mm:
            place(row)                        → emit C_Element_Rule Orderline
            remaining_space_mm -= row.width_mm
            placed_any = true
            recurse(row.bom_id,               → same cascade, row interior as envelope
                    row.width_mm, row.depth_mm)
        IF remaining_space_mm <= 0: BREAK

    IF placed_any: STOP  -- do not drop tier while current tier placed anything

Output: List of Placement Orderlines — editable by user in Bonsai GUI
```

---

## 7. Java Access Layer (Phase 4 — Next Session)

```java
/**
 * ViewAccessLayer — the compiler's only data access path.
 * Stateless. All cascade state managed by BomTierResolver.
 * No MetadataMissingException. No null fallbacks. Empty = not ready.
 */
public final class ViewAccessLayer {

    private static final String QUALIFIED_BOM =
        "SELECT * FROM v_qualified_bom " +
        "WHERE bom_type = ? AND min_space_mm <= ? " +
        "ORDER BY fit_priority ASC, width_mm DESC";

    private static final String ROOM_BOUNDARY =
        "SELECT * FROM v_verified_room_boundary " +
        "WHERE building_type = ? AND room_type = ?";

    private static final String ELEMENT_RULE =
        "SELECT * FROM v_compilable_element_rule " +
        "WHERE element_ref = ? AND building_type = ?";

    private static final String PROVEN_GEOMETRY =
        "SELECT * FROM v_proven_geometry WHERE name LIKE ?";

    public List<QualifiedBom> getQualifiedBoms(String bomType, int availableSpaceMm) {
        return query(QUALIFIED_BOM, bomType, availableSpaceMm, QualifiedBom::from);
    }

    public Optional<VerifiedRoomBoundary> getRoomBoundary(
            String buildingType, String roomType) {
        return queryOne(ROOM_BOUNDARY, buildingType, roomType,
            VerifiedRoomBoundary::from);
    }
}
```

**What this removes from Java:**

| Current Java | Replaced by |
|---|---|
| `MetadataMissingException` in hot paths | Empty list from view |
| `dims == null` → BBox fallback | Child absent from v_qualified_bom |
| Manual tier-switching conditionals | Caller loop over ('ROOM','SET','ITEM') |
| `if (geometry == null) throw...` | Empty Optional from v_proven_geometry |
| BOM expansion mixed with spatial logic | View gates, caller places |

---

## 8. View Execution Results — 2026-02-23

All six views created in `library/component_library.db`. Compiler not yet redirected —
views are data contracts only. SpatialDigests stable throughout.

```
View                         Rows   Status (as of Phase 3h close)
─────────────────────────────────────────────────────────────────────────────
v_qualified_bom                 0   join key gap — bc.role ≠ pd.product_id namespace.
                                    Phase 4 watchdog decision required (§5.1 note).
v_verified_room_boundary        7   LIVE — TB-LKTN 7 rooms (Phase 3g seed).
                                    SH/DX excluded: calibration debt, G8 RED expected.
v_compilable_element_rule      95   LIVE — DX:61, SH:14, TB:20
v_proven_geometry           22013   LIVE — Phase 3h seed + view SQL correction.
v_active_bom_assembly          29   LIVE — ROOM:9, SET:20
v_component_leaf               28   LIVE — Phase 3h seed + view SQL correction.
─────────────────────────────────────────────────────────────────────────────
```

Five of six views are live. v_qualified_bom awaits Phase 4 join resolution (watchdog gate).

**Phase 3h root cause note:** both v_proven_geometry and v_component_leaf returned zero rows
because the original view SQL joined `ad_geometry_map ON gm.element_ref = cd.name` —
a namespace mismatch. `ad_geometry_map.element_ref` uses Revit family:type strings;
`component_definitions.name` uses library component names. Zero intersection.
Fix: filter on `cd.vertex_count > 8` and `cd.geometry_hash IS NOT NULL` directly;
`ifc_class` retrieved via correlated subquery on `gm.geometry_hash = cd.geometry_hash`.
See §5.4 and §5.6 for corrected view SQL.

---

## 9. View Maintenance Rules — Immutable

**Rule 1 — Views are read-only to the compiler.** No INSERT, UPDATE, DELETE via views.

**Rule 2 — A view filter change is an architecture change.** Any WHERE clause modification requires watchdog review. Loosening a filter allows incomplete data into compilation.

**Rule 3 — New tables need views before compiler use.** No compiler code references a base table directly.

**Rule 4 — SpatialDigest stability proves view correctness.** After every view creation, all four building digests must match the baseline.

**Rule 5 — Never add OR doc_status = 'DR' to any view.** Zero rows = data not ready. That is correct behaviour, not a bug.

**Rule 6 — Never mix tiers in one query.** A result set containing ROOM and SET rows is a caller bug. The view is correct — the caller forgot the bom_type bind parameter.

**Rule 7 — ad_room_boundary has no DocStatus.** It is an M_ spatial quantity. Its gate is
`coordinate_frame IN ('IFC_GLOBAL_MM', 'LOCAL_MM', 'DRAWING_MM', 'CONSTRAINT_SOLVED', 'DERIVED_MM')`.
`GRID_DERIVED_MM` rows exist in the base table but are never served by any view.
Any proposal to add DocStatus to ad_room_boundary is rejected. See §2.3 for the full hierarchy.

---

## 10. Implementation Sequence

| Phase | Action | Status |
|---|---|---|
| 1 | Correct doc_status: keep on ad_element_rule, DROP from ad_room_boundary + ad_bom_child + component_definitions | **DONE** ✓ |
| 1b | Add extracted_from to component_definitions, ad_product_dim | **DONE** ✓ |
| 1c | Add bom_type to ad_bom; seed ROOM/SET from actual bom_ids | **DONE** ✓ |
| 1d | Add fit_priority, min_space_mm to ad_bom_child; seed known roles | **DONE** ✓ |
| 1e | Extend coordinate_frame CHECK to add DERIVED_MM, CONSTRAINT_SOLVED | Pending |
| 2 | Seed CO for verified ad_element_rule rows (1263 rows) | **DONE** ✓ |
| 3a–3f | CREATE all six views | **DONE** ✓ — see §8 for row counts |
| 3g | `UPDATE ad_room_boundary SET extracted_from='TB_LKTN_DSL' WHERE building_type='TB_LKTN'` → v_verified_room_boundary 0→7 | **DONE** ✓ |
| 3h | `UPDATE component_definitions SET extracted_from='LIBRARY'` + `UPDATE ad_product_dim SET extracted_from=provenance` + view SQL corrections (§5.4/§5.6) → v_proven_geometry 0→22,013; v_component_leaf 0→28 | **DONE** ✓ |
| 4 | **WATCHDOG CALL FIRST** — resolve v_qualified_bom join (product_ref FK vs bbox path). Then: add chosen key to `ad_bom_child` → v_qualified_bom goes live; ViewAccessLayer.java; BomTierResolver.java; ArchUnit gate | **PHASE 4 SESSION — watchdog gate** |
| R | Rename: ad_element_rule → C_Element_Rule, ad_room_boundary → M_Room_Boundary, ad_building_registry → C_Building_Order | **REFACTOR SESSION** |

**Phases 1 through 3h complete. 5 of 6 views live. Next session starts at Phase 4 — watchdog call first.**

---

## 11. Required Indexes

Create before views — view performance depends entirely on base table indexes.

```sql
CREATE INDEX IF NOT EXISTS idx_bom_bom_type           ON ad_bom(bom_type);
CREATE INDEX IF NOT EXISTS idx_bom_child_bom_id       ON ad_bom_child(bom_id);
CREATE INDEX IF NOT EXISTS idx_bom_child_role         ON ad_bom_child(role);
CREATE INDEX IF NOT EXISTS idx_geometry_map_ref       ON ad_geometry_map(element_ref);
CREATE INDEX IF NOT EXISTS idx_product_dim_id         ON ad_product_dim(product_id);
CREATE INDEX IF NOT EXISTS idx_element_rule_ref       ON ad_element_rule(element_ref, building_type);
CREATE INDEX IF NOT EXISTS idx_room_boundary_type     ON ad_room_boundary(building_type, room_type);
```

---

*BIM Intent Compiler | View Contracts — Compiler Data Access Layer | v1.9 | February 2026*

*"The base tables are the workshop. The views are the showroom. The compiler only walks through the showroom."*
