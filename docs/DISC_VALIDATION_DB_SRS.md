# DiscValidation.db SRS — Discipline Validation Database

**Version:** 1.0 (2026-03-19)
**Depends on:** [DISC_VALIDATE_SRS.md](DISC_VALIDATE_SRS.md) §9-10, [DocAction_SRS.md](DocAction_SRS.md) §1.3, [CALIBRATION_SRS.md](CALIBRATION_SRS.md)

---

## 1. Problem — Three Databases, Confused Boundaries

Current state mixes three concerns into two databases:

```
component_library.db (23,888 component_definitions + 23,901 geometries)
├── LOD concern:       M_Product, component_definitions, component_geometries  ← CORRECT
├── Discipline concern: ad_space_type_mep_bom, ad_element_mep, ad_fp_coverage  ← WRONG DB
├── Space concern:     ad_space_type, ad_wall_face, placement_rules            ← WRONG DB
└── Assembly concern:  ad_assembly_connector, ad_assembly_manifest             ← WRONG DB

validation.db (AD_Val_Rule + params)
├── Rules concern:     AD_Val_Rule, AD_Val_Rule_Param, AD_Clash_Rule           ← CORRECT
└── Results concern:   AD_Validation_Result                                     ← CORRECT
```

**Problem:** `component_library.db` is 23,901 geometry rows — it's a product
catalog. Discipline metadata (what MEP goes where, how many, what spacing,
what connects to what) is NOT product geometry. Mixing them means:

1. Querying "how many sprinklers in a bedroom?" loads 23K geometry rows into the connection
2. Updating discipline rules risks touching the LOD catalog
3. No clean separation between "what the product looks like" (LOD) and
   "where the product goes" (discipline placement)

---

## 2. Solution — DiscValidation.db (Third Database)

```
component_library.db — WHAT things look like (LOD catalog)
├── M_Product (608 products: dimensions, ifc_class)
├── component_definitions (23,888 LOD attachments)
├── component_geometries (23,901 mesh data)
└── surface_styles, material_layers

DiscValidation.db — WHERE things go + HOW they connect (discipline metadata)  ← NEW
├── Discipline schedule:  ad_space_type_mep_bom (186 rows)
├── MEP element types:    ad_element_mep (12 rows)
├── FP coverage rules:    ad_fp_coverage (4 hazard classes)
├── Space types:          ad_space_type (41 types)
├── Assembly connectors:  ad_assembly_connector (10 rows)
├── Placement rules:      placement_rules (4,801 rows)
├── Wall faces:           ad_wall_face (204 rows)
├── Space adjacency:      ad_space_adjacency
├── FP triggers:          ad_fp_trigger
├── Code requirements:    ad_code_requirement
└── Calibration results:  W_Calibration_Result (NEW — from CalibrationTest)

validation.db — RULES + VERDICTS (compliance engine)
├── AD_Val_Rule + AD_Val_Rule_Param (thresholds)
├── AD_Clash_Rule (cross-discipline pairs)
├── AD_Occupancy_Class (occupancy classification)
└── AD_Validation_Result (pass/warn/block verdicts)
```

### 2.1 Three Databases, Three Concerns

| Database | Concern | Opened By | Read/Write | Size |
|----------|---------|-----------|------------|------|
| `component_library.db` | Product LOD (geometry, dimensions) | Compile pipeline, Designer LOD fetch | Read-only at runtime | ~5 MB (23K geometries) |
| `disc_validation.db` | Discipline metadata (schedules, types, connectors) | DocEvent engine, CalibrationTest | Read at runtime, write at seed/migrate | ~50 KB (5K rows) |
| `validation.db` | Compliance rules + verdicts | PlacementValidator, InferenceEngine | Read rules, write results | ~20 KB |

### 2.2 Reference Pointers — No LOD Copies

DiscValidation.db references component_library.db products by **name**, not
by FK or by copying LOD data:

```
disc_validation.db                          component_library.db
┌─────────────────────────┐                ┌──────────────────────┐
│ ad_element_mep          │                │ M_Product            │
│   element_type: SPRINKLER──── name ────▶│   name: SPRINKLER    │
│   ifc_class: IfcFire... │                │   width, depth, height│
│   discipline: FP        │                │   ifc_class          │
│   ports: [{"IN":0.015}] │                └──────────┬───────────┘
└─────────────────────────┘                           │
                                                      ▼
┌─────────────────────────┐                ┌──────────────────────┐
│ ad_space_type_mep_bom   │                │ component_definitions│
│   mep_product_id: SPRINKLER── name ────▶│   name LIKE '%sprink%│
│   qty_normal: 0         │                │   geometry_hash      │
│   per_area_normal: 0.07 │                │   attachment_face    │
│   placement_rule: GRID  │                └──────────┬───────────┘
└─────────────────────────┘                           │
                                                      ▼
                                           ┌──────────────────────┐
                                           │ component_geometries │
                                           │   vertices, faces    │
                                           └──────────────────────┘
```

**The join is at runtime, in Java**, not via SQL FK. When DocEvent places a
SPRINKLER, it:
1. Reads discipline metadata from `disc_validation.db` (how many, where)
2. Reads product dimensions + LOD from `component_library.db` (what it looks like)
3. The link is `ad_element_mep.element_type` = `ad_space_type_mep_bom.mep_product_id`
   → at LOD fetch time, resolves to `M_Product` by `ifc_class` match

**No geometry in disc_validation.db. No discipline metadata in component_library.db.**

---

## 3. Tables — What Moves, What Stays, What's New

### 3.1 Tables Moving FROM component_library.db TO disc_validation.db

| Table | Rows | Why It Moves |
|-------|------|-------------|
| `ad_space_type_mep_bom` | 186 | Discipline schedule — not product geometry |
| `ad_element_mep` | 12 | MEP element type definitions — not LODs |
| `ad_fp_coverage` | 4 | FP hazard class thresholds — rule data |
| `ad_space_type` | 41 | Space type taxonomy — not product data |
| `ad_space_adjacency` | ~20 | Space relationship rules |
| `ad_assembly_connector` | 10 | Connection topology — not geometry |
| `ad_assembly_manifest` | ~5 | Assembly composition |
| `ad_wall_face` | 204 | Room boundary faces — spatial, not LOD |
| `placement_rules` | 4,801 | Placement strategy rules |
| `ad_fp_trigger` | ~10 | FP trigger conditions |
| `ad_code_requirement` | ~20 | Building code refs |
| `ad_room_slot` | ~50 | Room slot definitions |
| `ad_space_dim` | ~30 | Space dimension rules |
| `ad_space_exterior_rule` | ~10 | Exterior exposure rules |
| `ad_space_type_opening` | ~20 | Opening requirements per space |
| `ad_space_type_furniture` | ~20 | Furniture schedule per space |
| `ad_space_type_mep` | ~20 | MEP services per space |

### 3.2 Tables STAYING in component_library.db

| Table | Rows | Why It Stays |
|-------|------|-------------|
| `M_Product` | 608 | Product catalog — dimensions, ifc_class |
| `component_definitions` | 23,888 | LOD mesh attachments |
| `component_geometries` | 23,901 | Actual mesh data (vertices, faces, normals) |
| `surface_styles` | ~50 | Material appearance |
| `material_layers` | ~20 | Wall/slab layer composition |
| `I_Geometry_Map` | ~200 | Extraction geometry mapping |
| `M_Product_Image` | ~10 | Product thumbnails |

### 3.3 Tables STAYING in validation.db

| Table | Rows | Why It Stays |
|-------|------|-------------|
| `AD_Val_Rule` | ~30 | Compliance rule definitions |
| `AD_Val_Rule_Param` | ~100 | Rule threshold parameters |
| `AD_Clash_Rule` | ~10 | Cross-discipline clash pairs |
| `AD_Occupancy_Class` | 6 | Occupancy classifications |
| `AD_Val_Rule_Occupancy` | ~15 | Rule-occupancy links |
| `AD_Val_Rule_Exception` | ~5 | Documented exceptions |
| `AD_Val_Rule_Mining_Source` | ~15 | Mining provenance |
| `AD_Validation_Result` | writes | Runtime validation results |

### 3.4 NEW Tables in disc_validation.db

| Table | Purpose | Source |
|-------|---------|--------|
| `W_Calibration_Result` | Calibration test results (DocEvent vs Terminal) | CalibrationTest.java |
| `AD_SysConfig` | Schema version tracking | Standard |

---

## 4. Schema — disc_validation.db (DV001)

### 4.1 Migration: `migration/DV001_disc_validation_schema.sql`

```sql
-- DV001: Discipline Validation database schema
-- Separates discipline metadata from product LOD catalog
-- References component_library.db products by name (no FK, no LOD copies)

-- ── Space types ──────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ad_space_type (
    space_type_id     TEXT PRIMARY KEY,
    category          TEXT NOT NULL,
    omniclass_code    TEXT NOT NULL,
    wall_rule         TEXT NOT NULL,
    is_sleeping_room  INTEGER DEFAULT 0,
    is_open_plan      INTEGER DEFAULT 0,
    is_exterior       INTEGER DEFAULT 0,
    min_area          REAL DEFAULT 0,
    min_dimension     REAL DEFAULT 0,
    requires_window   INTEGER DEFAULT 0,
    requires_egress   INTEGER DEFAULT 0,
    structural_grid   INTEGER DEFAULT 0,
    beam_max_span     REAL DEFAULT 8.0,
    code_reference    TEXT,
    is_active         INTEGER DEFAULT 1
);

-- ── MEP element type definitions ─────────────────────────────────────
-- Reference pointer: element_type → M_Product.name in component_library.db

CREATE TABLE IF NOT EXISTS ad_element_mep (
    element_type      TEXT PRIMARY KEY,
    ifc_class         TEXT NOT NULL,
    discipline        TEXT NOT NULL,
    mep_system        TEXT,
    host_type         TEXT,
    dist_role         TEXT,
    circuit_type      TEXT,
    mount_height      REAL,
    clearance         TEXT,       -- JSON
    ports             TEXT,       -- JSON
    properties        TEXT,       -- JSON
    code_ref          TEXT,
    is_active         INTEGER DEFAULT 1,
    width             REAL,
    depth             REAL,
    height            REAL
);

-- ── Discipline schedule per space type ───────────────────────────────
-- Reference pointer: mep_product_id → ad_element_mep.element_type
--                                    → M_Product by ifc_class match at runtime

CREATE TABLE IF NOT EXISTS ad_space_type_mep_bom (
    space_type_id     TEXT NOT NULL REFERENCES ad_space_type(space_type_id),
    mep_product_id    TEXT NOT NULL REFERENCES ad_element_mep(element_type),
    qty_min           INTEGER DEFAULT 0,
    qty_normal        INTEGER DEFAULT 1,
    qty_max           INTEGER DEFAULT 99,
    per_area_min      REAL DEFAULT 0,
    per_area_normal   REAL DEFAULT 0,
    per_area_max      REAL DEFAULT 0,
    placement_rule    TEXT DEFAULT 'AUTO',
    host_surface      TEXT DEFAULT 'WALL',
    building_code     TEXT,
    code_clause       TEXT,
    conduit_min       REAL DEFAULT 0,
    conduit_normal    REAL DEFAULT 0,
    conduit_max       REAL DEFAULT 0,
    PRIMARY KEY (space_type_id, mep_product_id)
);

-- ── FP coverage by hazard class ──────────────────────────────────────

CREATE TABLE IF NOT EXISTS ad_fp_coverage (
    hazard_class      TEXT PRIMARY KEY,
    max_coverage_m2   REAL NOT NULL,
    max_spacing_m     REAL NOT NULL,
    min_spacing_m     REAL NOT NULL,
    wall_distance_m   REAL,
    k_factor          REAL,
    code_ref          TEXT DEFAULT 'NFPA 13',
    is_active         INTEGER DEFAULT 1
);

-- ── Assembly connectors ──────────────────────────────────────────────
-- Reference pointer: assembly_id → M_Product.name in component_library.db

CREATE TABLE IF NOT EXISTS ad_assembly_connector (
    connector_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    assembly_id       TEXT NOT NULL,
    version           TEXT NOT NULL DEFAULT '1.0.0',
    face              TEXT NOT NULL,
    connector_type    TEXT NOT NULL,
    position_x        REAL DEFAULT 0,
    position_y        REAL DEFAULT 0,
    position_z        REAL DEFAULT 0,
    diameter_mm       REAL,
    connects_to       TEXT,
    UNIQUE(assembly_id, version, face, connector_type)
);

-- ── Wall faces ───────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ad_wall_face (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type     TEXT NOT NULL,
    building_id       INTEGER,
    storey            TEXT NOT NULL,
    room_name         TEXT NOT NULL,
    face              TEXT NOT NULL,
    wall_type_id      TEXT NOT NULL,
    is_exterior       INTEGER NOT NULL,
    adjacent_room     TEXT,
    is_active         INTEGER DEFAULT 1,
    UNIQUE(building_type, storey, room_name, face)
);

-- ── Placement rules ──────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS placement_rules (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type     TEXT NOT NULL,
    storey            TEXT,
    room_name         TEXT,
    element_name      TEXT NOT NULL,
    placement_rule    TEXT NOT NULL,
    host_surface      TEXT,
    offset_x          REAL DEFAULT 0,
    offset_y          REAL DEFAULT 0,
    offset_z          REAL DEFAULT 0,
    is_active         INTEGER DEFAULT 1
);

-- ── Space adjacency ──────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ad_space_adjacency (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    space_type_a      TEXT NOT NULL,
    space_type_b      TEXT NOT NULL,
    adjacency_type    TEXT NOT NULL DEFAULT 'ADJACENT',
    is_required       INTEGER DEFAULT 0,
    code_ref          TEXT,
    UNIQUE(space_type_a, space_type_b)
);

-- ── Calibration results (CalibrationTest output) ─────────────────────

CREATE TABLE IF NOT EXISTS W_Calibration_Result (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    discipline        TEXT NOT NULL,
    storey            TEXT NOT NULL,
    te_count          INTEGER,
    te_floor_area_m2  REAL,
    te_density        REAL,
    docevent_qty      INTEGER,
    docevent_density  REAL,
    density_ratio     REAL,
    te_nn_median_mm   REAL,
    docevent_pitch_mm REAL,
    spacing_delta_mm  REAL,
    verdict           TEXT CHECK(verdict IN ('CALIBRATED','DRIFT','UNCALIBRATED','NO_SEED_DATA')),
    run_date          TEXT NOT NULL DEFAULT (datetime('now'))
);

-- ── Schema version ───────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS AD_SysConfig (
    Name              TEXT PRIMARY KEY,
    Value             TEXT NOT NULL,
    Description       TEXT,
    updated           TEXT NOT NULL DEFAULT (datetime('now'))
);

INSERT OR IGNORE INTO AD_SysConfig (Name, Value, Description)
VALUES ('SCHEMA_VERSION', 'DV001', 'disc_validation.db schema version');
```

---

## 5. Reference Pointer Pattern — No FK Across Databases

SQLite does not support cross-database foreign keys. The reference is by
**name convention** — same pattern as iDempiere's `AD_Reference` lookups:

| disc_validation.db column | Resolves to | Resolution method |
|--------------------------|-------------|-------------------|
| `ad_element_mep.element_type` | `M_Product` by `ifc_class` match | Java: `SELECT * FROM M_Product WHERE ifc_class = ?` |
| `ad_space_type_mep_bom.mep_product_id` | `ad_element_mep.element_type` | SQL within disc_validation.db (same DB) |
| `ad_assembly_connector.assembly_id` | `M_Product.name` | Java: `SELECT * FROM M_Product WHERE name = ?` |
| `placement_rules.element_name` | `M_Product.name` or `m_bom.bom_id` | Java: lookup by name |

**The Java DAO joins across databases.** Each method receives the connections
it needs:

```java
// DocEvent placement: reads disc_validation.db + component_library.db
void placeElements(Connection discConn, Connection compConn, ...)

// Calibration: reads disc_validation.db + validation.db + TE reference DB
void calibrate(Connection discConn, Connection valConn, Connection teConn, ...)

// LOD fetch: reads component_library.db only
Geometry fetchLOD(Connection compConn, String productName)
```

---

## 6. Migration Plan — Phased, Non-Destructive

### Phase 1: Create disc_validation.db (DV001)
1. Run `DV001_disc_validation_schema.sql` to create empty disc_validation.db
2. Copy seed data from component_library.db tables into disc_validation.db
3. Verify row counts match

### Phase 2: Update Java code — dual-read
1. Code reads from disc_validation.db (new) with fallback to component_library.db (old)
2. Both databases have the same tables temporarily
3. All tests pass against both

### Phase 3: Remove tables from component_library.db
1. Drop moved tables from component_library.db
2. Remove fallback code
3. Update schema_snapshot_component.sql

**Phase 1 is safe and independent.** Phases 2-3 are future sessions.

---

## 7. Connection Map — Who Opens What

### Current (2 DBs)
```
CompilationPipeline     → component_library.db (LOD + discipline metadata)
PlacementValidator      → validation.db (rules)
CalibrationDAO          → component_library.db + validation.db + TE_BOM.db
DocEvent (future)       → component_library.db (LOD + discipline metadata)
```

### Target (3 DBs)
```
CompilationPipeline     → component_library.db (LOD only)
PlacementValidator      → validation.db (rules)
DocEvent engine         → disc_validation.db (schedules) + component_library.db (LOD fetch)
CalibrationDAO          → disc_validation.db + validation.db + TE_BOM.db
Handler cascade H1-H6  → disc_validation.db (connectors, schedules) + validation.db (rules)
```

### Connection parameter naming convention
```java
Connection compConn;   // component_library.db — LOD catalog
Connection discConn;   // disc_validation.db   — discipline metadata
Connection valConn;    // validation.db         — compliance rules
Connection bomConn;    // {prefix}_BOM.db       — building BOM
Connection workConn;   // work_output.db        — design workspace
Connection teConn;     // TE reference DB       — Terminal oracle (tests only)
```

---

## 8. File Location

```
library/
├── component_library.db     ← LOD catalog (M_Product, geometries)
├── disc_validation.db       ← NEW: discipline metadata (schedules, types, connectors)
├── validation.db            ← compliance rules (AD_Val_Rule)
├── work_*.db                ← per-building design workspaces
├── SH_BOM.db                ← Sample House BOM
├── DX_BOM.db                ← Duplex BOM
└── TE_BOM.db                ← Terminal BOM

migration/
├── DV001_disc_validation_schema.sql    ← NEW: schema DDL
├── DV002_seed_from_component.sql       ← NEW: copy seed data
├── V001..V006                          ← existing migrations
```

---

## 9. Traceability

| Witness | What it Proves | Test |
|---------|---------------|------|
| W-DV-DB-SCHEMA | DV001 creates all required tables | DiscValidationDBTest |
| W-DV-DB-SEED | Seed data matches component_library.db source counts | DiscValidationDBTest |
| W-DV-DB-REF | Reference pointers resolve across databases | DiscValidationDBTest |
| W-DV-DB-ND | Migration does not disturb component_library.db | NonDisturbanceTest |

---

*References:
[DISC_VALIDATE_SRS.md](DISC_VALIDATE_SRS.md) §9 (5-table LOD chain) |
[DocAction_SRS.md](DocAction_SRS.md) §1.3 (processIt DocEvent) |
[CALIBRATION_SRS.md](CALIBRATION_SRS.md) (DocEvent vs Terminal) |
[G4_SRS.md](G4_SRS.md) §2 (work_output.db pattern)*
