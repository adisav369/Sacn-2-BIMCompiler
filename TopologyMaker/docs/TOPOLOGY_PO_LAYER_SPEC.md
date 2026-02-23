# TopologyMaker PO Layer — Implementation Reference

**Version:** 2.0
**Date:** 2026-02-23
**Status:** IMPLEMENTED — 15/15 tests GREEN
**Scope:** TopologyMaker module only. Zero DAGCompiler touch.
**Source:** `TopologyMaker/src/main/java/com/bim/compiler/topologymaker/po/`

---

## Implementation Summary

| Deliverable | File | Status |
|---|---|---|
| `BasePO` | `po/BasePO.java` | ✅ DONE |
| `ModelQuery` | `po/ModelQuery.java` | ✅ DONE |
| `X_AdTypologyPattern` | `po/X_AdTypologyPattern.java` | ✅ DONE |
| `X_AdRoomBoundary` | `po/X_AdRoomBoundary.java` | ✅ DONE |
| `X_AdBuildingRegistry` | `po/X_AdBuildingRegistry.java` | ✅ DONE |
| `M_AdTypologyPattern` | `po/M_AdTypologyPattern.java` | ✅ DONE |
| `M_AdRoomBoundary` | `po/M_AdRoomBoundary.java` | ✅ DONE |
| `M_AdBuildingRegistry` | `po/M_AdBuildingRegistry.java` | ✅ DONE |
| TopologyWriter refactor | `db/TopologyWriter.java` | ✅ DONE |
| TopologyAccessLayer refactor | `db/TopologyAccessLayer.java` | ✅ DONE |
| `BasePOTest` — 8 assertions | `BasePOTest.java` | ✅ DONE |
| All existing T6-1 through T6-7 still GREEN | — | ✅ DONE |

**Critical trap discovered during implementation:** See §15.5.

---

## 1. What This Is

iDempiere's Persistent Object (PO) pattern applied to TopologyMaker's three DB-backed
tables. The goal is to eliminate raw JDBC string literals and give each table a typed,
lifecycle-aware Java object.

This is NOT a full PO port. No `AD_Sequence`, no `Ctx` object, no connection pooling.
It is the structural pattern — the layer separation — applied pragmatically to SQLite.

---

## 2. The Three-Layer Pattern

```
┌─────────────────────────────────────────────────────┐
│  I_ layer  (import)   — NOT in scope                │
│  CSV/JSON ingest path. Add if batch imports from    │
│  external site briefs are needed.                   │
├─────────────────────────────────────────────────────┤
│  X_ layer  (generated structure)                    │
│  Hand-written; future: generated from               │
│  sqlite_master PRAGMA table_info().                 │
│                                                     │
│  Contains ONLY:                                     │
│    - COLUMNNAME_* constants (String)                │
│    - Typed getters + setters per column             │
│    - load(id) and save() delegating to BasePO       │
│  Contains NOTHING:                                  │
│    - No business logic                              │
│    - No validation                                  │
│    - No cross-table calls                           │
├─────────────────────────────────────────────────────┤
│  M_ layer  (model + business logic)                 │
│  extends X_                                         │
│                                                     │
│  Contains:                                          │
│    - beforeSave() — pre-write validation            │
│    - afterSave()  — post-write side effects         │
│    - completeIt() — DR/IP → CO transition           │
│    - voidIt()     — CO → VO transition              │
│    - Domain helpers (factory methods, toPattern())  │
└─────────────────────────────────────────────────────┘
```

---

## 3. BasePO — The Thin Foundation

**File:** `po/BasePO.java`

```java
/**
 * Lightweight Persistent Object base class — SQLite edition.
 *
 * Responsibilities:
 *   - Hold a JDBC Connection (injected, not owned — caller manages lifecycle)
 *   - load(id): SELECT * FROM tableName WHERE pk = id → populate column map
 *   - save(): INSERT OR IGNORE (new) or UPDATE dirty cols (existing)
 *   - delete(): DELETE FROM tableName WHERE pk = id
 *
 * Column values stored in LinkedHashMap<String, Object> — key = COLUMNNAME constant.
 * Dirty flags in LinkedHashSet<String> — only dirty columns written on UPDATE.
 *
 * Primary key strategy:
 *   INTEGER AUTOINCREMENT — PK excluded from INSERT; DB assigns; read back via
 *       Statement.getGeneratedKeys().
 *   TEXT PKs — caller sets explicitly; included in dirty set; written in INSERT.
 *
 * isNew() uses an EXPLICIT FLAG (isNewRecord), not PK value presence — see §15.5.
 */
public abstract class BasePO {
    protected final Connection conn;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Set<String> dirty = new LinkedHashSet<>();
    private boolean isNewRecord = true;   // cleared by load() and save() INSERT

    protected BasePO(Connection conn) { this.conn = conn; }

    protected abstract String getTableName();
    protected abstract String getPKColumnName();

    // get/set for subclasses
    protected Object  get_Value(String columnName) { return values.get(columnName); }
    protected String  get_ValueAsString(String columnName) { ... }
    protected int     get_ValueAsInt(String columnName)    { ... }
    protected double  get_ValueAsDouble(String columnName) { ... }
    protected boolean get_ValueAsBoolean(String columnName) { ... }

    protected void set_Value(String columnName, Object value) {
        values.put(columnName, value);
        dirty.add(columnName);
    }

    public boolean load(String id) throws SQLException { ... }  // clears dirty + isNewRecord=false
    public boolean load(int id)    throws SQLException { ... }  // clears dirty + isNewRecord=false

    public boolean save() throws SQLException {
        beforeSave(isNew());
        if (isNew()) {
            // INSERT OR IGNORE — PK included only if explicitly set (TEXT PKs)
            // INTEGER AUTOINCREMENT PKs: not in dirty → DB assigns → read back via getGeneratedKeys()
            isNewRecord = false;
        } else {
            // UPDATE dirty columns only
        }
        afterSave(isNew());
        dirty.clear();
        return true;
    }

    public boolean delete() throws SQLException { ... }

    protected void beforeSave(boolean newRecord) {}  // override in M_
    protected void afterSave(boolean newRecord) {}   // override in M_

    public boolean isNew() { return isNewRecord; }   // explicit flag — NOT derived from PK
    public int getDirtyColumnCount() { return dirty.size(); }

    // Package-private — called only by ModelQuery
    void loadFromResultSet(ResultSet rs) throws SQLException {
        // Populates values map; clears dirty; sets isNewRecord=false
    }
}
```

**Key design choices:**
- BasePO does NOT own the connection. The caller (TopologyWriter, TopologyAccessLayer)
  opens and closes it. Transactions stay in the orchestrator (TopologyBatchProcess).
- BasePO never calls `commit()`. Ever.
- `getDirtyColumnCount()` is public for test assertions (Caution 4 from §15).

---

## 4. X_ Classes

### 4.1 X_AdTypologyPattern

**Table:** `ad_typology_pattern` | **PK type:** TEXT (`typology_id`)

Constants: `Table_Name`, `COLUMNNAME_typology_id`, `COLUMNNAME_typology_name`,
`COLUMNNAME_grid_strategy`, `COLUMNNAME_ubbl_class`, `COLUMNNAME_description`,
`COLUMNNAME_base_width_mm`, `COLUMNNAME_base_depth_mm`, `COLUMNNAME_zone_json`,
`COLUMNNAME_is_active`.

All columns have typed getters and setters. No logic.

### 4.2 X_AdRoomBoundary

**Table:** `ad_room_boundary` | **PK type:** INTEGER AUTOINCREMENT (`id`)

Constants cover all 18 columns including `grid_min_x/y`, `grid_max_x/y`,
`z_offset_mm`, `building_id`, `extracted_from`, `coordinate_frame`.

The INTEGER AUTOINCREMENT PK means `id` is never set by the caller — BasePO reads
it back from `getGeneratedKeys()` after INSERT.

### 4.3 X_AdBuildingRegistry

**Table:** `ad_building_registry` | **PK type:** TEXT (`building_id`)

Constants cover all 14 columns including `provenance`, `doc_status`,
`geometry_fail_threshold`, `spatial_digest`, `reference_db_path`.

---

## 5. M_ Classes

### 5.1 M_AdTypologyPattern

```java
public class M_AdTypologyPattern extends X_AdTypologyPattern {

    /** Factory: load by typology_id. Returns null if not found or inactive. */
    public static M_AdTypologyPattern get(Connection conn, String typologyId)
            throws SQLException {
        M_AdTypologyPattern p = new M_AdTypologyPattern(conn);
        return p.load(typologyId) && p.isActive() ? p : null;
    }

    @Override
    protected void beforeSave(boolean newRecord) {
        if (getZoneJson() == null || getZoneJson().isBlank())
            throw new IllegalStateException("zone_json must not be blank");
        if (getBaseWidthMm() <= 0)
            throw new IllegalStateException("base_width_mm must be positive");
        if (getBaseDepthMm() <= 0)
            throw new IllegalStateException("base_depth_mm must be positive");
    }

    /**
     * Bridge to TopologyAccessLayer.TypologyPattern DTO.
     * Called by TopologyAccessLayer.getTypology() — the M_ object stays internal.
     */
    public TopologyAccessLayer.TypologyPattern toPattern() { ... }
}
```

### 5.2 M_AdRoomBoundary

```java
public class M_AdRoomBoundary extends X_AdRoomBoundary {

    private static final String COORDINATE_FRAME = "DERIVED_MM";
    private static final String STOREY_LABEL = "Ground Floor";

    /** Factory: create unsaved boundary from RoomCell. Sets all required columns. */
    public static M_AdRoomBoundary fromCell(Connection conn,
                                            String buildingType,
                                            String typologyId,
                                            RoomCell cell) { ... }

    @Override
    protected void beforeSave(boolean newRecord) {
        // THREE-TABLE AUTHORITY encoded at the object level
        if (!COORDINATE_FRAME.equals(getCoordinateFrame()))
            throw new IllegalStateException("coordinate_frame must be DERIVED_MM");
        if (getMaxXMm() <= getMinXMm())
            throw new IllegalStateException("maxXMm must be > minXMm");
        if (getMaxYMm() <= getMinYMm())
            throw new IllegalStateException("maxYMm must be > minYMm");
    }

    public long areaMm2() {
        return (long)(getMaxXMm() - getMinXMm()) * (getMaxYMm() - getMinYMm());
    }
}
```

**Key benefit:** `setCoordinateFrame()` exists on X_ (callers can call it), but
`beforeSave()` rejects any value other than DERIVED_MM. The THREE-TABLE AUTHORITY
rule is enforced by the object, not just documented.

### 5.3 M_AdBuildingRegistry

```java
public class M_AdBuildingRegistry extends X_AdBuildingRegistry {

    /** DR/IP → CO. Returns null on success; error string on failure. */
    public String completeIt() {
        if (!"IP".equals(getDocStatus()) && !"DR".equals(getDocStatus()))
            return "Cannot complete: current status is " + getDocStatus();
        setDocStatus("CO");
        return null;
    }

    /** → VO + is_active=false. Returns null on success. */
    public String voidIt() {
        if ("VO".equals(getDocStatus())) return "Already voided";
        setDocStatus("VO");
        setIsActive(false);
        return null;
    }

    @Override
    protected void beforeSave(boolean newRecord) {
        if (newRecord) {
            if (getDocStatus() == null) setDocStatus("DR");
            if (getProvenance() == null) setProvenance("GENERATIVE");
        }
        String ds = getDocStatus();
        if (!List.of("DR","IP","CO","VO").contains(ds))
            throw new IllegalStateException("Invalid doc_status: " + ds);
    }
}
```

---

## 6. ModelQuery — The OQL Layer

**File:** `po/ModelQuery.java`

Fluent query builder analogous to `org.compiere.model.Query` in iDempiere. Makes
table names and column names compile-time constants.

```java
// Single table — typed column references
List<M_AdTypologyPattern> active =
    new ModelQuery<>(conn, M_AdTypologyPattern::new,
                     X_AdTypologyPattern.Table_Name)
        .where(X_AdTypologyPattern.COLUMNNAME_is_active + " = ?", 1)
        .orderBy(X_AdTypologyPattern.COLUMNNAME_typology_name)
        .list();

// With JOIN
List<M_AdRoomBoundary> rooms =
    new ModelQuery<>(conn, M_AdRoomBoundary::new,
                     X_AdRoomBoundary.Table_Name + " rb")
        .addJoin(X_AdBuildingRegistry.Table_Name + " br",
            "br." + X_AdBuildingRegistry.COLUMNNAME_building_id +
            " = rb." + X_AdRoomBoundary.COLUMNNAME_building_type)
        .where("rb." + X_AdRoomBoundary.COLUMNNAME_building_type + " = ?", buildingType)
        .list();
```

**API surface:** `where()`, `andWhere()`, `addJoin()`, `addLeftJoin()`, `orderBy()`,
`setLimit()`, `list()`, `first()`, `count()`.

**SQL assembly:** `SELECT alias.*` for aliased tables; `SELECT table.*` for plain names.
`buildSQL()` appends JOINs → WHERE → ORDER BY → LIMIT in that order.

**`loadFromResultSet(rs)`** is package-private on BasePO — called by `ModelQuery.list()`
to populate a freshly constructed PO from a result set row without a second SELECT.
`ResultSetMetaData.getColumnName()` returns the actual DB column name, which matches
COLUMNNAME constants exactly.

---

## 7. Refactoring Applied

### TopologyWriter (refactored)

**`writeRoomBoundaries()`** — was: 15-line raw JDBC loop with positional `setString()`.
Now:
```java
for (RoomCell cell : cells) {
    M_AdRoomBoundary b = M_AdRoomBoundary.fromCell(conn, buildingType, typologyId, cell);
    b.save();  // beforeSave() validates DERIVED_MM + dimensions
    written++;
}
```
UNIQUE(building_type, storey, room_name) conflict handled by INSERT OR IGNORE in BasePO.

**`registerBuilding()`** — was: raw JDBC INSERT OR IGNORE with hardcoded column list.
Now delegates to `M_AdBuildingRegistry` with typed setters. `beforeSave()` defaults
`doc_status='DR'` and `provenance='GENERATIVE'` if not explicitly set.

**`writeBom()`** — unchanged. `ad_bom` and `ad_bom_child` are outside the current PO
phase scope. Raw JDBC retained.

### TopologyAccessLayer (refactored)

**`getTypology()`** — was: raw SQL with `rs.getString("column_name")` literals.
Now:
```java
M_AdTypologyPattern m = M_AdTypologyPattern.get(conn, typologyId);
return m != null ? Optional.of(m.toPattern()) : Optional.empty();
```

**`getUbblRules()`**, **`bomExists()`**, **`countBomsByType()`** — unchanged. `ad_spatial_rule`
and `ad_bom` are outside the current PO phase scope. Raw JDBC retained.

---

## 8. What Does NOT Change

| Class | Reason |
|---|---|
| `DocStatus` enum | Pure Java enum, not DB-backed |
| `SiteEnvelope` record | Input value object, no DB row |
| `RoomCell` record | Computed value object, not persisted directly |
| `TopologyOrder` record | Order descriptor, not stored in DB by TopologyMaker |
| `TopologyResult` record | Output value object |
| `GridStrategy` interface | Pure computation, no DB |
| `StripZoneStrategy` | Pure computation, no DB |
| `UbblValidator` | Pure computation, no DB |
| `TopologyBatchProcess` | Orchestrator; calls M_ objects internally but signature unchanged |

Records (immutable, no-inheritance) are the right tool for value objects.
PO (mutable, lifecycle) is the right tool for DB-backed entities. They coexist.

---

## 9. Package Layout (Implemented)

```
topologymaker/
├── DocStatus.java                 (unchanged)
├── SiteEnvelope.java              (unchanged)
├── RoomCell.java                  (unchanged)
├── TopologyOrder.java             (unchanged)
├── TopologyResult.java            (unchanged)
├── TopologyBatchProcess.java      (unchanged — uses M_ objects indirectly via writer)
├── grid/
│   ├── GridStrategy.java          (unchanged)
│   └── StripZoneStrategy.java     (unchanged)
├── rule/
│   └── UbblValidator.java         (unchanged)
├── db/
│   ├── TopologyAccessLayer.java   (refactored — getTypology delegates to M_)
│   └── TopologyWriter.java        (refactored — writeRoomBoundaries + registerBuilding delegate to M_)
└── po/                            ← implemented this session
    ├── BasePO.java
    ├── ModelQuery.java
    ├── X_AdTypologyPattern.java
    ├── X_AdRoomBoundary.java
    ├── X_AdBuildingRegistry.java
    ├── M_AdTypologyPattern.java
    ├── M_AdRoomBoundary.java
    └── M_AdBuildingRegistry.java
```

---

## 10. Test Coverage

**15/15 GREEN** — 7 existing (T6-1 through T6-7) + 8 new (BasePOTest T-PO-1 through T-PO-8).

`BasePOTest` assertions:

| # | Assertion | What it proves |
|---|---|---|
| T-PO-1 | `load()` populates all columns by COLUMNNAME constants | load + getter contract |
| T-PO-2 | `set_Value()` marks column dirty; unset columns not dirty | dirty flag tracking |
| T-PO-3 | dirty set is empty after `load()` | no spurious UPDATE on read (Caution 4) |
| T-PO-4 | `save()` with wrong `coordinate_frame` throws `IllegalStateException` | M_AdRoomBoundary guard |
| T-PO-5 | `completeIt()` transitions DR → CO | M_AdBuildingRegistry lifecycle |
| T-PO-6 | `voidIt()` transitions CO → VO, sets `is_active=false` | M_AdBuildingRegistry lifecycle |
| T-PO-7 | `beforeSave()` rejects blank `zone_json` | M_AdTypologyPattern validation |
| T-PO-8 | `save()` inserts row; `load()` by INTEGER PK retrieves same values | full roundtrip |

---

## 11. Implementation Order (Completed)

| Step | Deliverable | Outcome |
|---|---|---|
| 1 | `BasePO.java` | ✅ load/save/delete with dirty flags; explicit isNewRecord flag |
| 2 | `X_AdTypologyPattern` | ✅ 9 COLUMNNAME constants + getters/setters |
| 3 | `X_AdRoomBoundary` | ✅ 18 COLUMNNAME constants + getters/setters |
| 4 | `X_AdBuildingRegistry` | ✅ 14 COLUMNNAME constants + getters/setters |
| 5 | `M_AdTypologyPattern` | ✅ get() factory, beforeSave(), toPattern() |
| 6 | `M_AdRoomBoundary` | ✅ fromCell() factory, DERIVED_MM guard, areaMm2() |
| 7 | `M_AdBuildingRegistry` | ✅ completeIt(), voidIt(), beforeSave() defaults |
| 8 | Refactor TopologyWriter | ✅ writeRoomBoundaries + registerBuilding delegated |
| 9 | Refactor TopologyAccessLayer | ✅ getTypology delegated |
| 10 | `BasePOTest` | ✅ 8 assertions, in-memory SQLite |
| 11 | Full test suite | ✅ 15/15 GREEN |

---

## 12. What This Session Did NOT Do

- No PO layer for DAGCompiler tables (`ad_element_rule`, `ad_bom`, etc.) — future phases
- No X_ code generation from `sqlite_master` — hand-written; generator deferred to Phase PO-GEN
- No I_ import layer — no CSV ingest path
- No connection pooling, no `Ctx` object — single-threaded batch, connection injected by caller
- No `AD_Sequence` table — SQLite AUTOINCREMENT is sufficient
- `writeBom()` in TopologyWriter stays raw JDBC — `ad_bom` / `ad_bom_child` not in scope

---

## 13. Strategic Value

Proof-of-concept on 3 tables proves the pattern. Full porting roadmap:

```
Phase TM-PO  (DONE)   3 tables in TopologyMaker — BasePO + ModelQuery proven
Phase PO-1   (future) ad_bom, ad_bom_child — BOM domain; primary writer BOMAssemblerAD
Phase PO-2   (future) ad_building_registry in DAGCompiler — completeIt()/voidIt() useful
Phase PO-3   (future) ad_element_rule — most complex; RelationalResolver multi-table JOINs
Phase PO-GEN (future) Code-generate X_ from sqlite_master PRAGMA table_info()
```

**Critical boundary — views vs tables:**
```
PO + ModelQuery pattern     → ad_* tables with write lifecycle and PK
ViewAccessLayer pattern     → v_* views (read-only, no PK, no save())

NEVER apply PO to a view.
NEVER apply ViewAccessLayer to a table that needs lifecycle management.
```

---

## 14. ModelQuery — Porting Roadmap to DAGCompiler

When PO phase reaches DAGCompiler, `RelationalResolver` multi-table reads become:

```java
// BEFORE — raw SQL strings (current state in RelationalResolver)
"SELECT er.element_ref, er.wall_face, rb.min_x_mm ..."

// AFTER — with ModelQuery + COLUMNNAME constants
List<M_AdElementRule> rules =
    new ModelQuery<>(conn, M_AdElementRule::new,
                     X_AdElementRule.Table_Name + " er")
        .addJoin(X_AdRoomBoundary.Table_Name + " rb",
            "rb." + X_AdRoomBoundary.COLUMNNAME_building_type +
            " = er." + X_AdElementRule.COLUMNNAME_building_type)
        .where("er." + X_AdElementRule.COLUMNNAME_building_type + " = ?", buildingType)
        .andWhere("er." + X_AdElementRule.COLUMNNAME_is_active + " = ?", 1)
        .orderBy("er." + X_AdElementRule.COLUMNNAME_sequence)
        .list();
```

**Caution (PO-3):** `ModelQuery.buildSQL()` selects `alias.*` — only primary table columns
returned. For multi-table reads that need columns from joined tables, prefer separate
`ModelQuery` calls (load primary, then load related by FK) over wide JOINs with column
ambiguity risk.

**ModelQuery is NOT for views:**
```
ModelQuery → ad_* tables only
ViewAccessLayer → v_* views only  ← stays as-is, forever
```

`v_*` views have no PK and no `save()` path. ViewAccessLayer's `query()` / `queryOne()`
helpers are already the correct pattern for view reads.

---

## 15. Cautions and Traps

### 15.1 View Boundary

```
RULE: Never apply PO/ModelQuery to a view.
SYMPTOM if violated: ResultSetMetaData.getColumnName() returns view aliases,
not table column names. COLUMNNAME constants won't match. Values silently null.
```

### 15.2 BOM Chain is Load-Bearing (Phase PO-1)

The BOM expansion path drives all four buildings. A wrong `save()` that uses INSERT
instead of INSERT OR IGNORE, or a missing dirty flag, will silently corrupt the BOM chain.

Gate: run `mvn test -pl DAGCompiler` after every class refactored in PO-1. One class,
one commit, one test run.

### 15.3 RelationalResolver JOINs (Phase PO-3)

`SELECT alias.*` in `buildSQL()` only selects columns from the primary table. For
columns from joined tables needed in the result: prefer separate queries over wide JOINs.
`M_AdElementRule.getRoomBoundary()` loads the related `M_AdRoomBoundary` by FK — two
SQL calls, zero column-name ambiguity.

### 15.4 Dirty Flag on Load

When `loadFromResultSet()` populates values, the dirty set must stay empty. The
implementation clears `dirty` in `loadFromResultSet()` and sets `isNewRecord=false`.
Verified by T-PO-3.

### 15.5 TEXT vs INTEGER Primary Keys — CRITICAL TRAP (discovered in this session)

> **`isNew()` must use an explicit `isNewRecord` flag, NOT PK value presence.**

**The trap:** `isNew()` cannot be implemented as `return getPKValue().isBlank()` for TEXT
PKs. A TEXT PK like `"TERRACE_007"` is non-blank BEFORE save() — the caller sets it
explicitly — but the row does not yet exist in the DB.

If `isNew()` returns `false` (PK is non-blank), `save()` issues an UPDATE for a row
that doesn't exist yet. The UPDATE affects 0 rows. The INSERT never fires. The row is
silently never created.

**The fix:** `BasePO` carries an explicit `private boolean isNewRecord = true` flag:
- Starts `true` for every new object (regardless of PK type or value)
- Set to `false` after `loadFromResultSet()` is called (object came from DB)
- Set to `false` after a successful INSERT in `save()`

This mirrors iDempiere's `PO.m_isNew` exactly — it is set in the `PO(ctx, id, trxName)`
constructor (`id == 0` → new) or in `PO.load()` (loaded from DB → not new).

**Never revert this to PK-value-based detection.** T6-7 (buildingRegistered) will silently
fail if you do — no exception, just a row that was never written.

### 15.6 Transaction Ownership Stays in the Orchestrator

BasePO does not call `commit()`. The orchestrator (TopologyBatchProcess → TopologyWriter)
owns the transaction boundary. Adding `conn.commit()` inside `save()` or `afterSave()`
makes partial writes permanent and breaks the rollback guarantee.

### 15.7 What Never Gets the PO Treatment

| Component | Why |
|---|---|
| `ViewAccessLayer` | Reads views, no PK, no lifecycle. Already correct pattern. |
| `SpatialDigest` | One-shot hash computation over a result set. |
| `GeometryIntegrityChecker` | Audit queries, no lifecycle management. |
| `PlacementProver` | Audit assertions, not persistent objects. |
| `MeshBinder` | Geometry computation, not DB management. |
| Migration SQL files | DDL stays as SQL files. |
