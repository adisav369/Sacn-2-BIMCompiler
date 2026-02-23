# TopologyMaker PO Layer — Spec for Implementation Session

**Version:** 1.0
**Date:** 2026-02-23
**Status:** SPEC — ready for implementation session
**Scope:** TopologyMaker module only. Zero DAGCompiler touch.
**Prerequisite:** TOPOLOGY_MAKER.md (module already built, 7/7 tests GREEN)

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
│  I_ layer  (import)   — NOT in scope this session   │
│  CSV/JSON ingest path. Add later if batch imports    │
│  from external site briefs are needed.              │
├─────────────────────────────────────────────────────┤
│  X_ layer  (generated structure)                    │
│  Hand-written for now; future: generated from       │
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
│    - Domain helpers (e.g. getRoomCells(), validate())│
└─────────────────────────────────────────────────────┘
```

---

## 3. BasePO — The Thin Foundation

**File:** `TopologyMaker/src/main/java/com/bim/compiler/topologymaker/po/BasePO.java`

```java
/**
 * Lightweight Persistent Object base class — SQLite edition.
 * Replaces raw JDBC in TopologyAccessLayer + TopologyWriter.
 *
 * Responsibilities:
 *   - Hold a JDBC Connection (injected, not owned — caller manages lifecycle)
 *   - load(id): SELECT * FROM tableName WHERE pk = id → populate column map
 *   - save(): INSERT OR REPLACE INTO tableName (cols) VALUES (?) — uses dirty flags
 *   - delete(): DELETE FROM tableName WHERE pk = id
 *
 * Column values stored in LinkedHashMap<String, Object> — key = COLUMNNAME constant.
 * Dirty flags in Set<String> — only dirty columns written on save().
 *
 * Primary key strategy: INTEGER AUTOINCREMENT columns → let DB assign, read back
 * via Statement.getGeneratedKeys(). TEXT PKs → set explicitly before save().
 */
public abstract class BasePO {
    protected final Connection conn;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Set<String> dirty = new LinkedHashSet<>();

    protected BasePO(Connection conn) { this.conn = conn; }

    protected abstract String getTableName();
    protected abstract String getPKColumnName();

    // get/set for subclasses
    protected Object get_Value(String columnName) { return values.get(columnName); }
    protected String get_ValueAsString(String columnName) { ... }
    protected int    get_ValueAsInt(String columnName)    { ... }
    protected double get_ValueAsDouble(String columnName) { ... }
    protected boolean get_ValueAsBoolean(String columnName) { ... }

    protected void set_Value(String columnName, Object value) {
        values.put(columnName, value);
        dirty.add(columnName);
    }

    /** Load by TEXT primary key. Returns true if row found. */
    public boolean load(String id) throws SQLException { ... }

    /** Load by INTEGER primary key. Returns true if row found. */
    public boolean load(int id) throws SQLException { ... }

    /**
     * Save to DB. Calls beforeSave() first — throw IllegalStateException to abort.
     * On INSERT: reads back generated key and stores in PK column.
     * On UPDATE: writes only dirty columns.
     * Calls afterSave() on success.
     */
    public boolean save() throws SQLException {
        beforeSave(isNew());
        // ... INSERT OR REPLACE or UPDATE dirty cols ...
        afterSave(isNew());
        dirty.clear();
        return true;
    }

    /** Override in M_ layer for pre-write validation. Throw to abort save(). */
    protected void beforeSave(boolean newRecord) {}

    /** Override in M_ layer for post-write side effects. */
    protected void afterSave(boolean newRecord) {}

    /** True if this object has not yet been saved (no PK value set by DB). */
    public boolean isNew() { ... }
}
```

**Key design choice:** BasePO does NOT own the connection. The caller opens a
connection, passes it to the constructor, and closes it. This keeps transactions
in the hands of `TopologyBatchProcess` — exactly where they belong.

---

## 4. X_ Classes to Implement

### 4.1 X_AdTypologyPattern

**File:** `...topologymaker/po/X_AdTypologyPattern.java`
**Table:** `ad_typology_pattern`

```java
public class X_AdTypologyPattern extends BasePO {

    public static final String Table_Name          = "ad_typology_pattern";
    public static final String COLUMNNAME_typology_id   = "typology_id";
    public static final String COLUMNNAME_typology_name = "typology_name";
    public static final String COLUMNNAME_grid_strategy = "grid_strategy";
    public static final String COLUMNNAME_ubbl_class    = "ubbl_class";
    public static final String COLUMNNAME_description   = "description";
    public static final String COLUMNNAME_base_width_mm = "base_width_mm";
    public static final String COLUMNNAME_base_depth_mm = "base_depth_mm";
    public static final String COLUMNNAME_zone_json     = "zone_json";
    public static final String COLUMNNAME_is_active     = "is_active";

    public X_AdTypologyPattern(Connection conn) { super(conn); }

    @Override protected String getTableName()    { return Table_Name; }
    @Override protected String getPKColumnName() { return COLUMNNAME_typology_id; }

    public String getTypologyId()   { return get_ValueAsString(COLUMNNAME_typology_id); }
    public void   setTypologyId(String v) { set_Value(COLUMNNAME_typology_id, v); }

    public String getGridStrategy() { return get_ValueAsString(COLUMNNAME_grid_strategy); }
    public void   setGridStrategy(String v) { set_Value(COLUMNNAME_grid_strategy, v); }

    public String getUbblClass()    { return get_ValueAsString(COLUMNNAME_ubbl_class); }
    public int    getBaseWidthMm()  { return get_ValueAsInt(COLUMNNAME_base_width_mm); }
    public int    getBaseDepthMm()  { return get_ValueAsInt(COLUMNNAME_base_depth_mm); }
    public String getZoneJson()     { return get_ValueAsString(COLUMNNAME_zone_json); }
    public boolean isActive()       { return get_ValueAsBoolean(COLUMNNAME_is_active); }

    // ... full set of getters/setters for all columns
}
```

### 4.2 X_AdRoomBoundary

**File:** `...topologymaker/po/X_AdRoomBoundary.java`
**Table:** `ad_room_boundary`

Key columns: `building_type`, `storey`, `room_name`, `room_type`,
`min_x_mm`, `max_x_mm`, `min_y_mm`, `max_y_mm`,
`coordinate_frame`, `extracted_from`, `is_active`.

PK is `id` (INTEGER AUTOINCREMENT — BasePO reads back generated key).

### 4.3 X_AdBuildingRegistry

**File:** `...topologymaker/po/X_AdBuildingRegistry.java`
**Table:** `ad_building_registry`

Key columns: `building_id`, `building_name`, `building_type`, `dsl_content`,
`output_db_path`, `provenance`, `doc_status`, `is_active`.

PK is `building_id` (TEXT — set explicitly before save()).

---

## 5. M_ Classes to Implement

### 5.1 M_AdTypologyPattern

**File:** `...topologymaker/po/M_AdTypologyPattern.java`

```java
public class M_AdTypologyPattern extends X_AdTypologyPattern {

    public M_AdTypologyPattern(Connection conn) { super(conn); }

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
     * Parse zone_json and return as a TypologyPattern record for use by GridStrategy.
     * This is the bridge between the PO layer and the strategy layer.
     */
    public TopologyAccessLayer.TypologyPattern toPattern() { ... }
}
```

### 5.2 M_AdRoomBoundary

**File:** `...topologymaker/po/M_AdRoomBoundary.java`

```java
public class M_AdRoomBoundary extends X_AdRoomBoundary {

    /** Forced coordinate frame — no caller can set anything else. */
    private static final String COORDINATE_FRAME = "DERIVED_MM";

    public M_AdRoomBoundary(Connection conn) { super(conn); }

    /** Factory: create a new unsaved boundary from a RoomCell. */
    public static M_AdRoomBoundary fromCell(Connection conn,
                                            String buildingType,
                                            String typologyId,
                                            RoomCell cell) {
        M_AdRoomBoundary b = new M_AdRoomBoundary(conn);
        b.setBuildingType(buildingType);
        b.setStorey("Ground Floor");
        b.setRoomName(cell.zoneName());
        b.setRoomType(cell.roomType());
        b.setMinXMm(cell.minXMm());
        b.setMaxXMm(cell.maxXMm());
        b.setMinYMm(cell.minYMm());
        b.setMaxYMm(cell.maxYMm());
        b.setCoordinateFrame(COORDINATE_FRAME);        // enforced here, not in caller
        b.setExtractedFrom("TOPOLOGY_MAKER:" + typologyId);
        b.setIsActive(true);
        return b;
    }

    @Override
    protected void beforeSave(boolean newRecord) {
        // Enforce THREE-TABLE AUTHORITY at the object level
        if (!COORDINATE_FRAME.equals(getCoordinateFrame()))
            throw new IllegalStateException(
                "M_AdRoomBoundary coordinate_frame must be DERIVED_MM");
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

**Key benefit here:** `setCoordinateFrame()` still exists on X_ (callers could call
it), but `beforeSave()` on M_ rejects any value other than DERIVED_MM. The
THREE-TABLE AUTHORITY rule is encoded into the object, not just documented.

### 5.3 M_AdBuildingRegistry

**File:** `...topologymaker/po/M_AdBuildingRegistry.java`

```java
public class M_AdBuildingRegistry extends X_AdBuildingRegistry {

    public M_AdBuildingRegistry(Connection conn) { super(conn); }

    /** DocStatus transitions — mirrors iDempiere C_Order pattern. */
    public String completeIt() {
        if (!"IP".equals(getDocStatus()) && !"DR".equals(getDocStatus()))
            return "Cannot complete: current status is " + getDocStatus();
        setDocStatus("CO");
        return null;  // null = success (iDempiere convention)
    }

    public String voidIt() {
        if ("VO".equals(getDocStatus()))
            return "Already voided";
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
        // doc_status CHECK constraint: DR|IP|CO|VO
        String ds = getDocStatus();
        if (!List.of("DR","IP","CO","VO").contains(ds))
            throw new IllegalStateException("Invalid doc_status: " + ds);
    }
}
```

---

## 6. Refactoring TopologyWriter to Use PO Layer

**Current TopologyWriter** uses raw JDBC with string literals:

```java
// BEFORE — raw JDBC, string literals
String sql = "INSERT OR IGNORE INTO ad_room_boundary " +
             "(building_type, storey, room_name, ...) VALUES (?,?,?,...)";
stmt.setString(1, buildingType);
stmt.setString(2, STOREY_LABEL);
...
```

**After — PO layer:**

```java
// AFTER — typed, lifecycle-aware
public int writeRoomBoundaries(String buildingType, String typologyId,
                                List<RoomCell> cells) throws SQLException {
    int written = 0;
    for (RoomCell cell : cells) {
        M_AdRoomBoundary b = M_AdRoomBoundary.fromCell(conn, buildingType, typologyId, cell);
        b.save();  // beforeSave() validates; INSERT OR IGNORE handled by BasePO
        written++;
    }
    return written;
}
```

The transaction boundary stays in `TopologyBatchProcess` — it passes its
connection to the writer, calls `commit()` or `rollback()` there.

---

## 7. Refactoring TopologyAccessLayer to Use PO Layer

```java
// BEFORE
public Optional<TypologyPattern> getTypology(String typologyId) {
    String sql = "SELECT typology_id, grid_strategy, ubbl_class, ... " +
                 "FROM ad_typology_pattern WHERE typology_id = ? AND is_active = 1";
    // ... rs.getString("typology_id"), rs.getString("grid_strategy"), ...
}

// AFTER
public Optional<TopologyAccessLayer.TypologyPattern> getTypology(String typologyId)
        throws SQLException {
    M_AdTypologyPattern m = M_AdTypologyPattern.get(conn, typologyId);
    return m != null ? Optional.of(m.toPattern()) : Optional.empty();
}
```

The `TypologyPattern` record still exists as the outbound DTO — the M_ object is
internal to the db/ package.

---

## 8. What Does NOT Change

These stay exactly as-is:

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
| `TopologyBatchProcess` | Orchestrator stays; uses M_ objects internally |

Records (immutable, no-inheritance) are the right tool for value objects.
PO (mutable, lifecycle) is the right tool for DB-backed entities. They coexist.

---

## 9. Package Layout After Refactor

```
topologymaker/
├── DocStatus.java                 (unchanged)
├── SiteEnvelope.java              (unchanged)
├── RoomCell.java                  (unchanged)
├── TopologyOrder.java             (unchanged)
├── TopologyResult.java            (unchanged)
├── TopologyBatchProcess.java      (updated — uses M_ objects)
├── grid/
│   ├── GridStrategy.java          (unchanged)
│   └── StripZoneStrategy.java     (unchanged)
├── rule/
│   └── UbblValidator.java         (unchanged)
├── db/
│   ├── TopologyAccessLayer.java   (updated — delegates to M_)
│   └── TopologyWriter.java        (updated — delegates to M_)
└── po/                            ← NEW package
    ├── BasePO.java                ← NEW
    ├── X_AdTypologyPattern.java   ← NEW
    ├── X_AdRoomBoundary.java      ← NEW
    ├── X_AdBuildingRegistry.java  ← NEW
    ├── M_AdTypologyPattern.java   ← NEW
    ├── M_AdRoomBoundary.java      ← NEW
    └── M_AdBuildingRegistry.java  ← NEW
```

---

## 10. Test Strategy

**All 7 existing tests must stay GREEN** — they test behaviour, not implementation.
Since `TopologyBatchProcess.complete()` signature is unchanged and all outputs
(ad_room_boundary rows, DocStatus.CO, roomsWritten counts) are identical, the
refactor is transparent to the test suite.

Add one new test class: `BasePOTest`

```java
// BasePOTest assertions:
// 1. load() populates all columns by COLUMNNAME constants (no magic strings in test)
// 2. set_Value() marks column dirty; clean columns not included in UPDATE
// 3. save() on new M_AdRoomBoundary with wrong coordinate_frame throws IllegalStateException
// 4. M_AdBuildingRegistry.completeIt() transitions DR → CO
// 5. M_AdBuildingRegistry.voidIt() transitions CO → VO, sets is_active=false
// 6. M_AdTypologyPattern.beforeSave() rejects blank zone_json
```

---

## 11. Implementation Order for the Session

| Step | Deliverable | Risk |
|---|---|---|
| 1 | `BasePO.java` — load() + save() + dirty flags | Medium — get JDBC INSERT OR REPLACE right for TEXT vs INT PK |
| 2 | `X_AdTypologyPattern` — constants + getters | Low |
| 3 | `X_AdRoomBoundary` — constants + getters | Low |
| 4 | `X_AdBuildingRegistry` — constants + getters | Low |
| 5 | `M_AdTypologyPattern` — beforeSave(), get() factory, toPattern() | Low |
| 6 | `M_AdRoomBoundary` — fromCell() factory, beforeSave() DERIVED_MM guard | Low |
| 7 | `M_AdBuildingRegistry` — completeIt(), voidIt(), beforeSave() | Low |
| 8 | Refactor TopologyWriter to use M_ | Low — same SQL, different caller |
| 9 | Refactor TopologyAccessLayer to use M_ | Low |
| 10 | `BasePOTest` — 6 assertions | Low |
| 11 | Run full test suite — all 7 + 6 new = 13 GREEN | Verification |

---

## 12. What the Session Does NOT Do

- No PO layer for DAGCompiler tables (ad_element_rule, ad_bom, etc.) — future session
- No X_ code generation from sqlite_master — hand-written for now; spec a generator later
- No I_ import layer — no CSV ingest path planned yet
- No connection pooling, no Ctx object — TopologyMaker is single-threaded batch
- No AD_Sequence table — SQLite AUTOINCREMENT is sufficient

---

## 13. Strategic Value

If this proof-of-concept works cleanly on TopologyMaker's 3 tables, the pattern
scales to the full library DB:

```
Phase TM-PO  (this session)    3 tables in TopologyMaker
Phase PO-1   (future)          ad_bom, ad_bom_child — the BOM domain
Phase PO-2   (future)          ad_element_rule, ad_room_boundary — placement domain
Phase PO-3   (future)          ad_building_registry, ad_building_storey — registry domain
Phase PO-GEN (future)          Code-generate X_ from sqlite_master PRAGMA table_info()
                                → schema change = regenerate, not hand-edit
```

At Phase PO-GEN, a schema migration that adds a column to `ad_room_boundary` triggers
a regeneration of `X_AdRoomBoundary` — the new column appears as a typed getter
automatically. Every caller that needs it uses the getter; every caller that doesn't
is unaffected. No more hunting for `rs.getString("new_column_name")` across 12 files.
