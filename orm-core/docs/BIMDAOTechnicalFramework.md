# BIM DAO Technical Framework
## orm-core — iDempiere-style Persistence Layer for BIMCompiler

**Version:** 1.0
**Date:** 2026-02-23
**Modules:** `orm-core`, `ORMSandbox`, `TopologyMaker`
**Author:** BIMCompiler Project

---

## 1. Motivation

### 1.1 The Problem This Solves

The BIMCompiler main engine (`DAGCompiler`) uses raw JDBC throughout — string SQL
literals scattered across `RelationalResolver.java`, `FurnitureBOMResolver.java`,
and the MEP/ARC writers. This approach worked during early rapid development but
produced a recurring class of defects:

| Bug | Root Cause | Cost |
|-----|-----------|------|
| `element_guid` vs `guid` | Raw string column reference | Multi-session debug |
| `width_mm` vs `width` | No compile-time column check | Runtime NPE in BOM lookup |
| `label` vs `grid_label` | SQL written from memory | Query returns empty set silently |
| `min_area_m2` vs `min_area` | Column name guessed wrong | Schema error at runtime |
| `namePattern() == null` vs `""` | No typed entity, raw ResultSet | 9-element count discrepancy in DX |

Beyond column-name bugs, debugging the BIM construct required reading Java source,
manually constructing SQL queries, and running them in `sqlite3` — a slow, error-prone
loop. Investigating the G8 frame-of-reference bug (SH furniture placed at wrong IFC
coordinates) required multiple sessions before the cause was isolated.

### 1.2 Why Not a Standard ORM (Hibernate / jOOQ)?

Three reasons specific to this project:

**SQLite constraints.** Most Java ORMs have weak SQLite support. This project relies
on SQLite-specific features: R\*Tree spatial indexes, `INSERT OR IGNORE` semantics,
`PRAGMA table_info`, and six view contracts (`v_qualified_bom`,
`v_verified_room_boundary`, etc.). A framework that fights the database is worse than
raw JDBC.

**The SQL IS the domain logic.** The view contracts encode BIM correctness rules —
`coordinate_frame IN ('IFC_GLOBAL_MM','LOCAL_MM','DRAWING_MM','CONSTRAINT_SOLVED',
'DERIVED_MM')` is a semantic contract, not a query optimisation. An ORM that
auto-generates SQL from annotations would obscure this.

**The compilation hotpath is batch-oriented.** `RelationalResolver.loadRooms()` loads
all rooms in one query. An ORM's per-entity `findById()` pattern would introduce
N+1 problems at DX scale (1,197 elements, 40 rooms, 12 KITCHEN BOMs each).

### 1.3 Why an iDempiere-style DAO Layer IS Right

The entire project data model is already iDempiere-inspired:

- Table names: `ad_*` (Application Dictionary prefix)
- Column conventions: `is_active`, `group_by`, `bom_id` as TEXT PK
- Business concepts: `ad_bom` ≅ `C_BOM`, `ad_product_dim` ≅ `M_Product`,
  `ad_room_slot` ≅ `C_BOM_Line`, `c_orderline` ≅ `C_OrderLine` (Construction Order Details)

The iDempiere `PO` (Persistent Object) pattern — explicit two-layer `X_`/`M_`
classes, dirty tracking, caller-managed transactions, lifecycle hooks — is the
natural expression of what was already designed. It adds type safety without
changing the idiom the project already uses.

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        orm-core                             │
│                                                             │
│   BasePO                        ModelQuery<T>               │
│   ─────────────────────         ──────────────────────────  │
│   • LinkedHashMap values        • Fluent WHERE / JOIN       │
│   • LinkedHashSet dirty         • list() / first() / count()│
│   • load(id) / save() / delete()• POFactory lambda          │
│   • beforeSave / afterSave hooks• COLUMNNAME constants only  │
│   • Caller manages transaction  • Never for v_* views       │
└────────────────────┬────────────────────────────────────────┘
                     │ depends on
        ┌────────────┴────────────────────────────┐
        │                                         │
┌───────▼──────────┐                   ┌──────────▼──────────┐
│   ORMSandbox     │                   │   TopologyMaker      │
│                  │                   │                      │
│ 8 entity pairs   │                   │ X_AdTypologyPattern  │
│ X_/M_ classes    │                   │ M_AdTypologyPattern  │
│                  │                   │ X_AdSpatialRule      │
│ BuildingInspector│                   │ M_AdSpatialRule      │
│ CLI (6 commands) │                   │ TopologyBatchProcess │
│                  │                   │ (owns transaction)   │
│ 6 smoke tests    │                   │                      │
└──────────────────┘                   └──────────────────────┘

        ↕ read-only access to:
┌────────────────────────────────────────────────────────────┐
│              library/component_library.db                  │
│  ad_bom  ad_bom_child  ad_room_boundary  c_orderline   │
│  ad_product_dim  ad_room_slot  c_order        │
│  ad_typology_pattern  ad_spatial_rule  (+ 47 more tables)  │
└────────────────────────────────────────────────────────────┘
```

**The main compiler (`DAGCompiler`) does NOT depend on `orm-core`.** The two
systems share only the SQLite database file. This boundary is intentional and
must not be blurred.

---

## 3. Core Framework — `orm-core`

### 3.1 `BasePO` — Persistent Object Base

`BasePO` is the abstract root for all entity classes. Every concrete entity
inherits from it via the X_ layer.

**Key design decisions:**

**Dirty tracking via `LinkedHashSet<String>`.**
Only modified columns are written on `UPDATE`. A load followed by a single
`setCoordinateFrame("IFC_GLOBAL_MM")` produces `UPDATE ad_room_boundary SET
coordinate_frame = ? WHERE id = ?` — not a full-column overwrite.

**Explicit `isNewRecord` flag — not derived from PK.**
TEXT primary keys (e.g. `bom_id = 'BED_SET_MASTER'`) are set before `save()`.
Deriving new-record status from PK presence would incorrectly treat these as
existing rows. `isNewRecord` starts `true`, becomes `false` after `INSERT` or
after `loadFromResultSet()`.

**`INSERT OR IGNORE` semantics.**
`save()` on a new record emits `INSERT OR IGNORE` — matching the migration
convention used throughout this project. Re-seeding the same typology pattern
twice is safe.

**INTEGER AUTOINCREMENT key read-back.**
When the PK is not in the dirty set (AUTOINCREMENT columns), `save()` reads
the generated key via `Statement.RETURN_GENERATED_KEYS` and stores it in the
value map.

**Caller-managed transactions.**
`BasePO` never calls `commit()`. The orchestrator (`TopologyBatchProcess` or a
test) owns the transaction boundary. This matches the iDempiere PO contract.

**Lifecycle hooks.**
```java
protected void beforeSave(boolean newRecord) {}   // override to validate
protected void afterSave(boolean newRecord)  {}   // override for side effects
```
`M_AdBom` uses `beforeSave` to enforce `group_by IS NOT NULL`, catching a real
schema constraint before the DB does.

### 3.2 `ModelQuery<T>` — Fluent Query Builder

`ModelQuery` is analogous to `org.compiere.model.Query` in iDempiere. It builds
parameterised SQL from `COLUMNNAME_*` constants — eliminating raw string column
references from query code.

**Fluent API:**

```java
// Single table
List<M_AdBom> unitBoms =
    new ModelQuery<>(conn, M_AdBom::new, X_AdBom.Table_Name)
        .where(X_AdBom.COLUMNNAME_bom_type + " = ?", "UNIT")
        .andWhere(X_AdBom.COLUMNNAME_is_active + " = ?", 1)
        .orderBy(X_AdBom.COLUMNNAME_bom_id)
        .list();

// With JOIN
List<M_AdRoomBoundary> rooms =
    new ModelQuery<>(conn, M_AdRoomBoundary::new,
                     X_AdRoomBoundary.Table_Name + " rb")
        .addJoin(X_C_Order.Table_Name + " br",
            "br." + X_C_Order.COLUMNNAME_building_id +
            " = rb." + X_AdRoomBoundary.COLUMNNAME_building_type)
        .where("rb." + X_AdRoomBoundary.COLUMNNAME_building_type + " = ?",
               "Ifc4_SampleHouse")
        .list();

// Existence check
Optional<M_AdRoomBoundary> living =
    new ModelQuery<>(conn, M_AdRoomBoundary::new, X_AdRoomBoundary.Table_Name)
        .where(X_AdRoomBoundary.COLUMNNAME_room_name + " = ?", "ROOM_Ground_Floor_1")
        .first();

// Count without loading PO objects
int kitchenRooms =
    new ModelQuery<>(conn, M_AdRoomBoundary::new, X_AdRoomBoundary.Table_Name)
        .where(X_AdRoomBoundary.COLUMNNAME_room_type + " = ?", "KITCHEN")
        .count();
```

**`POFactory` lambda pattern:**
Each `ModelQuery` receives a factory `M_AdBom::new` (a method reference to the
single-argument `Connection` constructor). `list()` calls `factory.create(conn)`
for each row and calls `loadFromResultSet()` — no second SELECT per object.

---

## 4. Entity Layer — X_ / M_ Pattern

Each `ad_*` table has exactly two Java classes:

### 4.1 X_ Classes — Structure Layer

`X_` classes hold:
- `Table_Name` constant (`"ad_bom"`)
- `COLUMNNAME_*` constants for every mapped column
- Typed getters (`getBomType()`, `getGroupBy()`, `isActive()`)
- Typed setters (`setBomType(String v)`, `setIsActive(int v)`)
- No business logic, no queries

```java
public class X_AdBom extends BasePO {
    public static final String Table_Name           = "ad_bom";
    public static final String COLUMNNAME_bom_id    = "bom_id";
    public static final String COLUMNNAME_bom_type  = "bom_type";
    public static final String COLUMNNAME_is_active = "is_active";
    // ...

    public String getBomId()   { return get_ValueAsString(COLUMNNAME_bom_id); }
    public String getBomType() { return get_ValueAsString(COLUMNNAME_bom_type); }
    public boolean isActive()  { return get_ValueAsBoolean(COLUMNNAME_is_active); }
    public void setBomType(String v) { set_Value(COLUMNNAME_bom_type, v); }
}
```

### 4.2 M_ Classes — Model / Business Layer

`M_` classes add:
- Static factory methods (`get()`, `getByType()`, `getByBuilding()`)
- `beforeSave()` / `afterSave()` validation overrides
- Domain helper methods (`centroidXMm()`, `areaMm2()`, `isNestedBom()`,
  `describeOffset()`)
- ModelQuery construction — the only place query logic lives

```java
public class M_AdBom extends X_AdBom {

    public M_AdBom(Connection conn) { super(conn); }

    public static M_AdBom get(Connection conn, String bomId) throws SQLException {
        M_AdBom bom = new M_AdBom(conn);
        return bom.load(bomId) && bom.isActive() ? bom : null;
    }

    public static List<M_AdBom> getByType(Connection conn, String bomType)
            throws SQLException {
        return new ModelQuery<>(conn, M_AdBom::new, Table_Name)
            .where(COLUMNNAME_bom_type + " = ?", bomType)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_bom_id)
            .list();
    }

    @Override
    protected void beforeSave(boolean newRecord) {
        if (getGroupBy() == null)
            throw new IllegalStateException(
                "group_by must not be null (ad_bom NOT NULL constraint)");
    }
}
```

### 4.3 Current Entity Coverage

| Table | X_ Class | M_ Class | Domain Role |
|-------|----------|----------|-------------|
| `ad_bom` | `X_AdBom` | `M_AdBom` | BOM hierarchy (UNIT→FLOOR→ROOM→SET→ITEM) |
| `ad_bom_child` | `X_AdBomChild` | `M_AdBomChild` | Assembly children, dx/dy/dz offsets, rotation_rule |
| `ad_bom_child_param` | `X_AdBomChildParam` | `M_AdBomChildParam` | Parameter overrides (key=value) |
| `c_order` | `X_C_Order` | `MOrder` | 4 registered buildings, expected_elements |
| `c_orderline` | `X_C_OrderLine` | `MOrderLine` | Per-building element placement (doors, MEP, furniture) |
| `ad_product_dim` | `X_AdProductDim` | `M_AdProductDim` | Product dimensions in **meters** (not mm) |
| `ad_room_boundary` | `X_AdRoomBoundary` | `M_AdRoomBoundary` | Room bounding boxes, coordinate_frame, G8 calibration |
| `ad_room_slot` | `X_AdRoomSlot` | `M_AdRoomSlot` | BOM dispatch rules per room type |
| `ad_typology_pattern` | `X_AdTypologyPattern` | `M_AdTypologyPattern` | TopologyMaker: terrace/courtyard/linear typologies |
| `ad_spatial_rule` | `X_AdSpatialRule` | `M_AdSpatialRule` | TopologyMaker: UBBL constraints (AREA, MIN_DIM, CEILING_MM) |

---

## 5. BuildingInspector — Debug CLI

`BuildingInspector` is the primary day-to-day debugging tool. It navigates the
full BIM construct via typed PO objects and prints structured human-readable
reports. It is read-only — no writes, no side effects.

### 5.1 Commands

```bash
# From project root (library DB resolves via relative path)
mvn -pl ORMSandbox exec:java \
    -Dexec.mainClass="com.bim.ormsandbox.BuildingInspector" \
    -Dexec.args="library/component_library.db <command> [arg]"
```

| Command | Argument | Output |
|---------|----------|--------|
| `buildings` | — | All 4 registered buildings: id, name, type, doc_status, expected_elements |
| `bom` | `<bomId>` | Full recursive BOM tree with dx/dy/dz offsets, rotation_rule, product dims |
| `rooms` | `<buildingType>` | Room boundaries: X/Y min/max, centroid, area, coordinate_frame |
| `rules` | `<buildingType>` | Element rules: host_ref, ifc_class, discipline, position_rule, height |
| `slots` | `<roomType>` | Room slot dispatch: assembly_id, slot_name, priority, required |
| `product` | `<productId>` | Product dimensions (W/D/H in meters) + clearances |

### 5.2 Real Debugging Example — G8 Frame-of-Reference Bug

The G8 bug (SH furniture placed at wrong IFC coordinates) was diagnosed using:

```bash
# Step 1: Check what the DB actually has for SH room boundaries
mvn -pl ORMSandbox exec:java ... -Dexec.args="library/component_library.db rooms Ifc4_SampleHouse"

# Output showed:
# [42] ROOM_Ground_Floor_1  type=LIVING  frame=IFC_GLOBAL_MM
#      X: [-7510, 1359]  centX=-3075
#      Y: [-281, 4409]   centY=2064
#      area=39.2 m²

# Step 2: Check what slots dispatch for LIVING
mvn -pl ORMSandbox exec:java ... -Dexec.args="library/component_library.db slots LIVING"

# Step 3: Cross-reference with compiled output → furniture at X(1620, 6270)
# Diagnosis: compiler applies LOCAL→GLOBAL transform to already-global IFC_GLOBAL_MM coords
```

Without `BuildingInspector`, this investigation required reading Java source code,
constructing SQL manually, and running `sqlite3` queries. With it: three commands,
under a minute.

---

## 6. TopologyMaker Integration

TopologyMaker is the first production module built on `orm-core`. It manages
the generation of building typologies from the `ad_typology_pattern` and
`ad_spatial_rule` tables seeded by `migration_topology_maker_bootstrap.sql`.

**Seeded typology:** `TERRACE_MY_1S` — Malaysian single-storey terrace
- Grid strategy: `STRIP_ZONES` (wet strip 13%, spine 37%, sleeping 50%)
- Base footprint: 9,900 × 8,500mm
- UBBL compliance: bedroom ≥9,290mm², bathroom ≥2,500mm², ceiling ≥2,400mm

**Seeded spatial rules:** 5 UBBL constraints from Malaysian Uniform Building
By-Laws 1984 (`UBBL_BED_AREA`, `UBBL_BATH_AREA`, `UBBL_TOI_AREA`,
`UBBL_BED_DIM`, `UBBL_CEIL`).

**Module structure:**
```
TopologyMaker/
  src/main/java/com/bim/topology/
    po/
      X_AdTypologyPattern.java     ← column constants + accessors
      M_AdTypologyPattern.java     ← getActive(), getByStrategy()
      X_AdSpatialRule.java
      M_AdSpatialRule.java
    TopologyBatchProcess.java      ← owns transaction, calls save()
```

`TopologyBatchProcess` is the only class that calls `conn.commit()` — it owns
the transaction boundary exactly as `BasePO` requires.

---

## 7. Guardrails — What This Framework Must NOT Do

These rules exist to protect the compilation hotpath and maintain the project's
architectural clarity.

### Guardrail 1: Never use `ModelQuery` against `v_*` views

**Rule:** `ModelQuery` is for `ad_*` tables with a PK and a `save()` lifecycle.
Views (`v_qualified_bom`, `v_verified_room_boundary`, `v_compilable_element_rule`,
etc.) are correctness contracts — query them with raw JDBC in a dedicated
`ViewAccessLayer`. ModelQuery's `SELECT alias.*` aliasing breaks on views.

```java
// WRONG — do not do this
new ModelQuery<>(conn, M_AdBom::new, "v_qualified_bom").list();

// RIGHT — raw JDBC for views
PreparedStatement stmt = conn.prepareStatement(
    "SELECT * FROM v_qualified_bom WHERE bom_type = ?");
```

### Guardrail 2: Never introduce orm-core as a dependency in DAGCompiler

**Rule:** The main compiler (`DAGCompiler`) must not depend on `orm-core`.
The two systems communicate only through the SQLite file. Merging them would:
- Force the compilation hotpath through entity-load overhead
- Couple the type-safe debug layer to the raw-SQL batch layer
- Risk N+1 query patterns at Terminal scale (~51,000 elements)

If the main compiler needs a column constant, define it locally — do not import
from `orm-core`.

### Guardrail 3: BasePO never commits

**Rule:** No `BasePO` subclass, X_ class, or M_ class may call `conn.commit()`,
`conn.rollback()`, or `conn.setAutoCommit()`. Transaction ownership belongs
exclusively to the orchestrator (`TopologyBatchProcess`, a test `@BeforeEach`,
or an explicit migration runner).

### Guardrail 4: Empty string ≠ null in entity getters

**Rule:** `get_ValueAsString()` returns `null` when the DB value is SQL NULL,
but returns `""` (empty string) when the DB stores an empty string. Java checks
`== null` will not catch empty-string values. Always use:

```java
// WRONG
if (child.getChildNamePattern() == null) continue;

// RIGHT
if (child.getChildNamePattern() == null
    || child.getChildNamePattern().isEmpty()) continue;
```

This is the exact defect that caused 9 extra elements in the DX element count —
`ad_bom_child.child_name_pattern` stored `""` (empty string), `pf.namePattern() == null`
evaluated `false`, and the child was dispatched with bbox-fallback dimensions.

### Guardrail 5: ad_product_dim units are meters, not millimetres

**Rule:** `M_AdProductDim.getWidth()`, `.getDepth()`, `.getHeight()` return
values in **meters**. `FURN_DINING_CHAIR` = 0.45m, not 450. The smoke test
`S-ORM-3` enforces this (asserts `< 5.0`). Never divide by 1,000 in entity
code — the DB stores meters by design.

### Guardrail 6: Compilation hotpath uses batch reads, not entity loading

**Rule:** `RelationalResolver.loadRooms()`, `loadRules()`, `loadBomIds()` load
all relevant rows in a single query. This bulk pattern must not be replaced with
`M_AdRoomBoundary.getByBuilding()` calls inside a per-room loop. Use the ORM
entities for inspection and debugging; keep batch reads in the compiler.

### Guardrail 7: New tables get entity pairs only when stable

**Rule:** Do not write X_/M_ classes for a table that is still changing schema.
The COLUMNNAME constants become stale if the schema changes and the X_ class is
not updated in sync. Write entity classes once the table schema is confirmed
by at least one committed migration.

---

## 8. Benefits Summary

### 8.1 Compile-Time Bug Prevention

Every column reference through `COLUMNNAME_*` constants is verified at compile
time. The historical column-name bugs (`element_guid`, `width_mm`, `label`,
`min_area_m2`) become compilation errors rather than runtime surprises.

### 8.2 Debugging Velocity

`BuildingInspector` turns a multi-step investigation into a single command.
The G8 frame-of-reference bug diagnosis — which previously required multiple
sessions — was completed in one session using `rooms Ifc4_SampleHouse` and
`bom BED_SET_MASTER`. This is the highest-value output of the framework.

### 8.3 Domain Coherence

The iDempiere `X_`/`M_` pattern is not a foreign idiom imposed on the project —
it is the natural completion of an already iDempiere-aligned data model. New
developers who know iDempiere read the entity code immediately. The mental model
is consistent from DB schema through Java entity to compiler logic.

### 8.4 Safe Extension Point for New Domains

`TopologyMaker` attached to `orm-core` without touching `DAGCompiler`. New
domains (`SpaceSolver`, `CalloutCascade`, future AD Events modules) follow the
same pattern: define tables in a migration, write X_/M_ pairs, attach to
`orm-core`. Zero changes to the main compiler required.

### 8.5 Zero External Dependencies

`orm-core` depends only on `org.xerial:sqlite-jdbc` (already in every module)
and JUnit for tests. No Hibernate, no jOOQ, no connection pooling frameworks.
The entire framework is 424 lines across two files. It is auditable, forkable,
and has no version-drift risk.

### 8.6 Lifecycle Safety

`beforeSave()` hooks enforce constraints before the DB does, giving meaningful
error messages. `M_AdBom.beforeSave()` catching `group_by IS NULL` is more
useful than a raw `SQLiteException: NOT NULL constraint failed: ad_bom.group_by`.

---

## 9. Module Dependencies

```
bim-compiler-parent (root pom)
├── orm-core                  ← BasePO + ModelQuery (no compile dependency on DAGCompiler)
├── ORMSandbox                ← depends on orm-core (debug/inspect tool)
├── TopologyMaker             ← depends on orm-core (production module)
└── DAGCompiler               ← NO dependency on orm-core (compilation hotpath)
```

`orm-core` version is inherited from `bim-compiler-parent`. All modules that
depend on it get the same version automatically — no per-module version pinning
needed.

---

## 10. Adding a New Entity

When a new `ad_*` table is stable and needs entity coverage:

1. **Write `X_AdFoo.java`** — extend `BasePO`, define `Table_Name` and all
   `COLUMNNAME_*` constants, add typed getters and setters.

2. **Write `M_AdFoo.java`** — extend `X_AdFoo`, add factory methods using
   `ModelQuery`, add `beforeSave()` validation for any `NOT NULL` constraints.

3. **Add to `BuildingInspector`** if the entity needs a debug dump command.

4. **Write a smoke test** in `BuildingInspectorTest` following the `S-ORM-N`
   naming convention. At minimum: load one known row and assert non-null PK.

5. **Do not** write entity classes until the migration that creates the table
   has been committed and applied to `library/component_library.db`.

---

## 11. Smoke Tests

Six tests in `BuildingInspectorTest` validate the PO layer against the live
`library/component_library.db`:

| ID | Name | What It Proves |
|----|------|----------------|
| S-ORM-1 | `allBuildingsLoaded` | `MOrder.getAll()` returns ≥4 buildings, all with non-null IDs |
| S-ORM-2 | `bedSetMasterBomChain` | `BED_SET_MASTER` BOM loads with ≥1 child; `group_by` non-null |
| S-ORM-3 | `productDimInMeters` | `FURN_DINING_CHAIR` width < 5.0 — confirms meter units, not mm |
| S-ORM-4 | `elementRulesLoadForSH` | `MOrderLine.getByBuilding()` returns typed rules with non-null ifc_class |
| S-ORM-5 | `roomBoundaryCentroid` | `centroidXMm()` = `(minX + maxX) / 2.0` to 0.001mm precision |
| S-ORM-6 | `inspectorDumpBuildings` | `BuildingInspector.dumpBuildings()` completes without exception |

Run with:
```bash
mvn test -pl ORMSandbox
```

---

*This document describes the orm-core framework as implemented at commit
`a2c4b21` (2026-02-23). Update guardrails and entity coverage table when new
X_/M_ pairs are added.*
