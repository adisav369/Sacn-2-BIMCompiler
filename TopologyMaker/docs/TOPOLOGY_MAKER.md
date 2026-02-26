# TopologyMaker — Site Brief to Compilable Topology

**Version:** 2.0
**Date:** 2026-02-23
**Status:** GOVERNING — defines the batch process that turns a site brief into compiler-ready rows
**Authors:** red1 (architect) + Claude Watchdog (reviewer)
**Supplements:** PREFAB_ARCHITECTURE.md, VIEW_CONTRACTS.md, METADATA_DRIVEN_ARCHITECTURE.md
**iDempiere analogues:** AD_Process (batch), C_Order/C_OrderLine lifecycle, MRP BOM explosion

**v2.0 changes from v1.0:** §3 module structure updated with `po/` package; §12 TopologyWriter
reflects PO delegation; §15 test count updated to 15/15; §16 PO Layer section added;
§17 What Remains updated.

---

## 1. Problem Statement

The BIM compiler requires two things to compile a new building:

1. **`ad_room_boundary` rows** — spatial footprints that tell the compiler where each room sits
2. **C_Order row** — the DSL manifest that tells the compiler the building exists

Both have historically been authored by hand. For a single terrace house that is a one-time cost.
For a site with forty terrace units, it is forty times the same calculation with arithmetic
variation. That is not a scaling strategy — it is a data-entry problem dressed as architecture.

TopologyMaker solves this by treating a site brief as an order. A typology template is the
product catalogue. The batch process is the MRP run. The output is a set of rows that the
compiler already knows how to consume without any code change.

---

## 2. iDempiere Analogy

```
iDempiere AD_Process          TopologyMaker equivalent
─────────────────────────────────────────────────────────
AD_Process                    TopologyBatchProcess.complete()
Process Parameters            TopologyOrder (orderId, typologyId, SiteEnvelope)
Process Result                TopologyResult (DocStatus, row counts, violation log)
M_Product catalogue           ad_typology_pattern (typology templates) ← M_AdTypologyPattern PO
C_OrderLine validation        UbblValidator (UBBL spatial rules vs RoomCell list)
MRP BOM explosion             PrefabBom builder → FLOOR + UNIT rows in ad_bom
DocStatus DR→IP→CO            DocStatus enum + MOrder.completeIt()
Rollback on failure           TopologyWriter single transaction, rollback on any SQLException
PO persistence layer          BasePO + X_/M_ classes → typed DB objects (Phase TM-PO)
```

**Key insight:** the compiler does not know TopologyMaker exists. It reads
`v_verified_room_boundary` and C_Order as it always has. TopologyMaker
is purely a row producer — a supply-side concern, not a demand-side one.

---

## 3. Module Structure

```
TopologyMaker/                                    Maven module: topology-maker
├── pom.xml                                       sqlite-jdbc + gson + junit only
├── docs/
│   ├── TOPOLOGY_MAKER.md                         this file
│   └── TOPOLOGY_PO_LAYER_SPEC.md                 PO layer implementation reference
└── src/
    ├── main/java/com/bim/compiler/topologymaker/
    │   ├── DocStatus.java                        enum DR|IP|CO|VO
    │   ├── SiteEnvelope.java                     record: widthMm, depthMm, numBedrooms, hasPorch
    │   ├── RoomCell.java                         record: roomType, zoneName, min/maxX/Y in mm
    │   ├── TopologyOrder.java                    record: orderId, typologyId, site, status
    │   ├── TopologyResult.java                   record: status, roomsWritten, bomsWritten, violations, log
    │   ├── TopologyBatchProcess.java             orchestrator — the AD_Process equivalent
    │   ├── grid/
    │   │   ├── GridStrategy.java                 interface: subdivide(SiteEnvelope, zoneJson)
    │   │   └── StripZoneStrategy.java            STRIP_ZONES: fraction-based vertical strips
    │   ├── rule/
    │   │   └── UbblValidator.java                AREA + MIN_DIM checks vs List<UbblRule>
    │   ├── db/
    │   │   ├── TopologyAccessLayer.java           reads ad_typology_pattern (via M_) + ad_spatial_rule
    │   │   └── TopologyWriter.java                writes via M_ PO + raw JDBC for ad_bom
    │   │       └── PrefabBom (inner class)        FLOOR/UNIT BOM builder
    │   └── po/                                   ← PO layer (Phase TM-PO, 2026-02-23)
    │       ├── BasePO.java                        load/save/delete + dirty flags + isNewRecord
    │       ├── ModelQuery.java                    fluent OQL builder (where/join/orderBy/list)
    │       ├── X_AdTypologyPattern.java           COLUMNNAME constants + typed getters/setters
    │       ├── X_AdRoomBoundary.java              COLUMNNAME constants + typed getters/setters
    │       ├── X_C_Order.java          COLUMNNAME constants + typed getters/setters
    │       ├── M_AdTypologyPattern.java           get() factory + beforeSave() + toPattern()
    │       ├── M_AdRoomBoundary.java              fromCell() factory + DERIVED_MM guard
    │       └── MOrder.java          completeIt() + voidIt() + beforeSave() defaults
    └── test/java/com/bim/compiler/topologymaker/
        ├── TopologyBatchProcessTest.java         T6-1 through T6-7 — all GREEN
        └── BasePOTest.java                       T-PO-1 through T-PO-8 — all GREEN (new)
```

**Isolation guarantee:** TopologyMaker has no dependency on `dag-compiler`. The root
`pom.xml` lists it as a sibling module alongside DAGCompiler and 2D_Layout. Adding or
removing it does not affect any existing compilation path.

---

## 4. Data Flow

```
INPUTS                    PROCESS                       OUTPUTS
──────────────────────────────────────────────────────────────────────────────
TopologyOrder             TopologyBatchProcess          ad_room_boundary
  orderId                                               (7 rows, DERIVED_MM)
  typologyId ──────┐       ┌── TopologyAccessLayer
  SiteEnvelope     │       │   M_AdTypologyPattern.get() ← ad_typology_pattern
    widthMm        │       │   reads ad_spatial_rule (raw JDBC)
    depthMm        │       │
    numBedrooms    │       │   TypologyPattern ──────► StripZoneStrategy
    hasPorch       │       │   (zoneJson, fractions)    subdivide(site)
                   │       │                             │
                   └───────┘                             ▼
                                                   List<RoomCell>
                                                         │
                                                         ├──► UbblValidator
                                                         │    AREA check (≥9290mm²)
                                                         │    MIN_DIM check (≥2700mm)
                                                         │    → violations list
                                                         │
                                                         └──► TopologyWriter
                                                              M_AdRoomBoundary.fromCell() × N
                                                              writeBom(FLOOR)        ad_bom/child
                                                              writeBom(UNIT)         ad_bom/child
                                                              MOrder   c_order
                                                              commit()
                                                                │
                                                                ▼
                                                          TopologyResult
                                                            status = CO
                                                            roomsWritten = 7
                                                            bomsWritten  = 2
                                                            violations   = []
                                                            log          = [...]
```

---

## 5. Three-Table Authority Compliance

TopologyMaker writes to exactly three tables and never crosses their authority boundaries:

| Table | Authority | What TopologyMaker writes | What it never writes |
|---|---|---|---|
| `ad_room_boundary` | Spatial footprints | min/max X/Y in mm, `coordinate_frame=DERIVED_MM` | Rotation, orientation, placement fractions |
| `ad_bom` / `ad_bom_child` | Assembly hierarchy | FLOOR + UNIT BOMs per order; catalog prefabs pre-seeded by migration | Product dimensions, element rules |
| `c_order` | Building manifest | One GENERATIVE row per order; DSL content auto-generated | Geometry, spatial digest |

**Never written:** C_OrderLine (Construction Order Details), `ad_product_dim`, `ad_element_placement`, `ad_geometry_map`.

The catalog (Layers 1–3 in §8) is seeded once by migration and never touched by the batch
process. The batch process only produces the per-order FLOOR + UNIT layers (4–5).

**PO enforcement:** `M_AdRoomBoundary.beforeSave()` rejects any `coordinate_frame` value
other than `DERIVED_MM` — the THREE-TABLE AUTHORITY rule is encoded into the object, not
just documented.

---

## 6. Coordinate Frame

All `ad_room_boundary` rows produced by TopologyMaker use `coordinate_frame = 'DERIVED_MM'`.

This value is in the `v_verified_room_boundary` CHECK constraint alongside
`IFC_GLOBAL_MM`, `LOCAL_MM`, `DRAWING_MM`, and `CONSTRAINT_SOLVED`. The compiler's
`ViewAccessLayer.getRoomBoundary()` call hits this view — it sees DERIVED_MM rows
immediately with no migration needed on the compiler side.

**Origin convention:** site south-west corner = (0, 0). X increases eastward.
Y increases northward. All fractions in `zone_json` are applied to site `widthMm` (X)
and `depthMm` (Y).

```
                     North / back of site
   ┌─────────────────────────────────────────────┐  maxYMm = depthMm
   │  WET_BATH  │         COMMON          │ BED_R2│
   │            │                         │       │
   ├────────────┤                         │       │
   │  WET_TOI   │                         │ BED_R1│
   │            │                         │       │
   ├────────────┼────────────────────────────────┤  y = porch_frac × depthMm
   │            │         BED_L           │       │
   │            │                         │       │
   └────────────┴─────────────────────────┴───────┘  minYMm = 0
   0                                            widthMm
   ←─── x_to 13% ─→←────── x_to 69% ──────→←──31%─→
   (wet strip)       (spine + common)          (sleeping)

   PORCH zone occupies x_from 13% to x_to 69%, y 0 to depth_frac (27%)
```

This is the TERRACE_MY_1S layout extracted from TB-LKTN. The fractions are stored in
`ad_typology_pattern.zone_json` — not hardcoded in Java.

---

## 7. Typology Template — ad_typology_pattern

```sql
CREATE TABLE IF NOT EXISTS ad_typology_pattern (
    typology_id   TEXT PRIMARY KEY,
    typology_name TEXT NOT NULL,
    grid_strategy TEXT NOT NULL CHECK(grid_strategy IN ('STRIP_ZONES','COURTYARD','LINEAR')),
    ubbl_class    TEXT NOT NULL,         -- links to jurisdiction (RESIDENTIAL_TYPE_C etc.)
    description   TEXT,
    base_width_mm INTEGER NOT NULL,      -- reference site dimensions
    base_depth_mm INTEGER NOT NULL,
    zone_json     TEXT NOT NULL,         -- JSON array — see §7.1
    is_active     INTEGER NOT NULL DEFAULT 1
);
```

PO representation: `M_AdTypologyPattern` — loaded via `M_AdTypologyPattern.get(conn, id)`.
The `TypologyPattern` record in `TopologyAccessLayer` is the outbound DTO; the M_ object
is internal to the `po/` package.

### 7.1 zone_json Schema

```json
{
  "zone":       "BED_R1",
  "room_type":  "BEDROOM",
  "x_from":     0.687,
  "x_to":       1.0,
  "y_from":     0.271,
  "y_to":       0.635,
  "depth_frac": 0.271
}
```

`StripZoneStrategy.subdivide()` reads fractions, multiplies by site envelope dimensions,
rounds to integer mm, emits one `RoomCell` per zone.

### 7.2 numBedrooms gating

| numBedrooms | BED_L | BED_R1 | BED_R2 |
|---|---|---|---|
| 1 | included | omitted | omitted |
| 2 | included | included | omitted |
| 3 | included | included | included |

`hasPorch=false` drops the PORCH zone and extends all other zones to `y_from=0`.

---

## 8. Prefab Layer Stack

TopologyMaker completes a five-layer prefab hierarchy. Layers 1–3 are catalog items
seeded by migration. Layers 4–5 are generated per order.

```
LAYER 5 — UNIT   (ad_bom bom_type=UNIT)    GENERATED per order
  TERRACE_001_UNIT    → TERRACE_001_GF (GF)

LAYER 4 — FLOOR  (ad_bom bom_type=FLOOR)   GENERATED per order
  TERRACE_001_GF      → PORCH_MODULE_MY, BEDROOM_PREFAB_MY_3100 ×3,
                         LIVING_PREFAB_MY, BATHROOM_PREFAB_MY, BATHROOM_PREFAB_MY

LAYER 3 — ROOM   (ad_bom bom_type=ROOM)    SEEDED by migration
  BEDROOM_PREFAB_MY_3100   = WALL_EXT_MY_150_WIN_STD + DOOR_D2 + BED_SET_MASTER + ELEC_LIGHT
  LIVING_PREFAB_MY         = WALL_EXT_MY_150_WIN_STD + LIVING_SET + ELEC_LIGHT ×2
  BATHROOM_PREFAB_MY       = TOILET_BLOCK_FIXTURES + ELEC_LIGHT
  PORCH_MODULE_MY          = WALL_EXT_MY_150_SOLID + ELEC_LIGHT

LAYER 2 — SET    (ad_bom bom_type=SET)     SEEDED by migration
  WALL_EXT_MY_150_WIN_STD  = WALL_BODY + WINDOW_W1 (sill 900mm, FACE_OUTSIDE)
  WALL_EXT_MY_150_WIN_WIDE = WALL_BODY + WINDOW_W2 (sill 900mm, FACE_OUTSIDE)
  WALL_EXT_MY_150_SOLID    = WALL_BODY (no opening)
  BED_SET_MASTER, LIVING_SET, TOILET_BLOCK_FIXTURES  [EXISTING catalog]

LAYER 1 — ITEM   (ad_product_dim)          EXIST in catalog
  WINDOW_W1, WINDOW_W2, DOOR_D2, ELEC_LIGHT, FURN_BED_KING, ...
```

---

## 9. UBBL Validation

`UbblValidator` checks each `RoomCell` against `ad_spatial_rule` before any DB writes.
Violations → `DocStatus.IP`, writes nothing.

### 9.1 Rules seeded (Malaysian UBBL 1984)

| rule_id | room_type | constraint | min_value | UBBL ref |
|---|---|---|---|---|
| UBBL_BED_AREA | BEDROOM | AREA | 9,290 mm² | UBBL 1984 §51 |
| UBBL_BATH_AREA | BATHROOM | AREA | 2,500 mm² | UBBL 1984 §56 |
| UBBL_TOI_AREA | TOILET | AREA | 1,500 mm² | UBBL 1984 §56 |
| UBBL_BED_DIM | BEDROOM | MIN_DIM | 2,700 mm | UBBL 1984 §51 |
| UBBL_CEIL | (all) | CEILING_MM | 2,400 mm | — (skipped in validator) |

### 9.2 TERRACE_MY_1S proof (9900×8500mm, 3BR, porch)

| Zone | Width mm | Depth mm | Area mm² | Min dim mm | UBBL_BED_AREA | UBBL_BED_DIM |
|---|---|---|---|---|---|---|
| BED_L | 3100 | 6120 | 18,972,000 | 3100 | PASS | PASS |
| BED_R1 | 3069 | 3098 | 9,507,762 | 3069 | PASS | PASS |
| BED_R2 | 3069 | 3102 | 9,519,738 | 3069 | PASS | PASS |

All five active rules pass → `DocStatus.CO`.

---

## 10. DocStatus Lifecycle

```
   TopologyOrder created
         │
         ▼
   TopologyBatchProcess.complete(order)
         │
         ├── typology not found ──────────────────────────► IP
         ├── strategy not found ──────────────────────────► IP
         ├── UBBL violations ─────────────────────────────► IP
         ├── DB write error ──────────────────────────────► IP (rollback)
         └── all steps pass ──────────────────────────────► CO
                                                             roomsWritten = N
                                                             bomsWritten  = 2
                                                             violations   = []
```

`TopologyResult.isComplete()` returns `true` only for `DocStatus.CO`.

The C_Order row is written with `doc_status='DR'`. The compiler promotes
it to `CO` when `mvn test` succeeds. This mirrors the C_Order → posted pattern:
the batch creates the order; a successful compilation pass posts it.

`MOrder.completeIt()` implements the DR/IP → CO transition.
`MOrder.voidIt()` implements the → VO + is_active=false transition.

---

## 11. GridStrategy — Extension Point

`GridStrategy` is an interface. Adding a new strategy:
1. Implement `GridStrategy` in `grid/`
2. Add one entry to the `STRATEGIES` map in `TopologyBatchProcess`
3. Add the value to the CHECK constraint in `ad_typology_pattern`
4. Seed a typology row with the new strategy ID

| strategy_id | Class | Description |
|---|---|---|
| `STRIP_ZONES` | `StripZoneStrategy` | Vertical strips + horizontal bands — terrace, semi-D |
| `COURTYARD` | *(pending)* | Perimeter rooms around a central void — shophouse |
| `LINEAR` | *(pending)* | Single corridor spine — apartment block |

---

## 12. TopologyWriter — Transaction Guarantee and PO Delegation

All writes for one order are in a single JDBC transaction. The connection is opened by
TopologyWriter and passed to M_ PO objects — BasePO does not own or commit it.

```
conn.setAutoCommit(false)
  writeRoomBoundaries()  — M_AdRoomBoundary.fromCell() × N; each b.save() → INSERT OR IGNORE
  writeBom(floor)        — raw JDBC INSERT OR IGNORE (ad_bom out of PO scope)
  writeBom(unit)         — raw JDBC INSERT OR IGNORE
  registerBuilding()     — MOrder with typed setters; .save() → INSERT OR IGNORE
conn.commit()            — atomic; BasePO never commits
```

On any `SQLException`, `rollback()` is called. `DocStatus.IP` returned. Partial writes
never persist.

`INSERT OR IGNORE` on all primary keys makes the process idempotent. Running the same
order twice produces the same DB state.

**PO delegation pattern:**
```java
// writeRoomBoundaries() — typed, validated
for (RoomCell cell : cells) {
    M_AdRoomBoundary b = M_AdRoomBoundary.fromCell(conn, buildingType, typologyId, cell);
    b.save();  // beforeSave() enforces DERIVED_MM; INSERT OR IGNORE; isNewRecord=false after
    written++;
}

// registerBuilding() — lifecycle-aware
MOrder reg = new MOrder(conn);
reg.setBuildingId(orderId);          // TEXT PK — sets isNewRecord flag correctly
reg.setDslContent(generateDsl(...));
reg.setDocStatus("DR");
reg.save();  // beforeSave() defaults provenance; INSERT OR IGNORE
```

---

## 13. Migration

`migration/migration_topology_maker_bootstrap.sql` is ADD-ONLY. It:
- Creates `ad_typology_pattern` and `ad_spatial_rule` (CREATE TABLE IF NOT EXISTS)
- Seeds TERRACE_MY_1S typology and 5 UBBL rules (INSERT OR IGNORE)
- Seeds 3 wall prefab BOMs and 7 wall BOM children (INSERT OR IGNORE)
- Seeds 4 room prefab BOMs and 16 room BOM children (INSERT OR IGNORE)

**Not yet applied to canonical DB.** SpatialDigests are unchanged.

```bash
sqlite3 library/component_library.db < migration/migration_topology_maker_bootstrap.sql
```

---

## 14. Usage

```java
TopologyOrder order = new TopologyOrder(
    "TERRACE_001",
    "TERRACE_MY_1S",
    new SiteEnvelope(9900, 8500, 3, true),
    DocStatus.IP
);

TopologyResult result = new TopologyBatchProcess("library/component_library.db")
    .complete(order);

if (result.isComplete()) {
    System.out.println("Ready: " + result.roomsWritten() + " rooms written");
} else {
    System.out.println("Blocked: " + result.violations());
    System.out.println("Log: " + result.log());
}
```

---

## 15. Test Coverage — 15/15 GREEN

### T6 series (TopologyBatchProcessTest)

| # | Assertion | What it proves |
|---|---|---|
| T6-1 | `result.isComplete() == true` | Full pipeline CO for standard 3BR site |
| T6-2 | `roomsWritten == 7` | PORCH + 3×BEDROOM + COMMON + BATHROOM + TOILET |
| T6-3 | All `coordinate_frame = 'DERIVED_MM'` | View contract honoured |
| T6-4 | `violations.isEmpty()` | TERRACE_MY_1S passes Malaysian UBBL 1984 |
| T6-5 | 3 wall prefab BOMs exist | Migration seeded catalog correctly |
| T6-6 | `{orderId}_GF` (FLOOR) + `{orderId}_UNIT` (UNIT) in ad_bom | Layer 4–5 generation |
| T6-7 | Building in C_Order with `provenance='GENERATIVE'` | Registry row written |

### T-PO series (BasePOTest)

| # | Assertion | What it proves |
|---|---|---|
| T-PO-1 | `load()` populates columns via COLUMNNAME constants | load + getter contract |
| T-PO-2 | `set_Value()` marks dirty; unset columns clean | dirty flag tracking |
| T-PO-3 | dirty set empty after `load()` | no spurious UPDATE on read |
| T-PO-4 | wrong `coordinate_frame` throws `IllegalStateException` | M_AdRoomBoundary guard |
| T-PO-5 | `completeIt()` transitions DR → CO | MOrder lifecycle |
| T-PO-6 | `voidIt()` transitions CO → VO, sets `is_active=false` | MOrder lifecycle |
| T-PO-7 | blank `zone_json` throws `IllegalStateException` | M_AdTypologyPattern validation |
| T-PO-8 | `save()` inserts; `load()` by INTEGER PK retrieves correctly | INTEGER PK roundtrip |

Both test classes use a temporary copy of the library DB. The canonical DB is never
modified by the test suite.

---

## 16. PO Layer Architecture

The `po/` package implements the iDempiere Persistent Object pattern for the three
TopologyMaker tables. Full reference: `TopologyMaker/docs/TOPOLOGY_PO_LAYER_SPEC.md`.

```
ad_typology_pattern → X_AdTypologyPattern → M_AdTypologyPattern
                                              get(conn, id) — load + active check
                                              toPattern()   — bridge to TypologyPattern DTO
                                              beforeSave()  — zone_json + dimension check

ad_room_boundary    → X_AdRoomBoundary    → M_AdRoomBoundary
                                              fromCell(conn, buildingType, typologyId, cell)
                                              beforeSave()  — DERIVED_MM enforcement
                                              areaMm2()     — floor area helper

c_order → X_C_Order → MOrder
                                              completeIt()  — DR/IP → CO
                                              voidIt()      — → VO + is_active=false
                                              beforeSave()  — default DR + GENERATIVE
```

**Critical implementation note:** `BasePO.isNew()` uses an explicit `isNewRecord`
flag, NOT PK value presence. A TEXT PK like `"TERRACE_007"` is set before `save()`
but the row does not yet exist in the DB. Deriving `isNew()` from PK blankness causes
a silent 0-row UPDATE instead of INSERT. See `TOPOLOGY_PO_LAYER_SPEC.md §15.5`.

**ModelQuery** is the OQL layer. Use COLUMNNAME constants from X_ classes in all
query expressions. `ModelQuery` is for `ad_*` tables only — `v_*` views continue to
use `ViewAccessLayer`.

---

## 17. What Remains

| Item | Description | Phase |
|---|---|---|
| Apply migration to canonical DB | `sqlite3 library/component_library.db < migration/migration_topology_maker_bootstrap.sql` | Immediate |
| Run real TERRACE generation | Verify end-to-end with canonical DB | After migration |
| COURTYARD strategy | Perimeter-room typology for shophouses | TopologyMaker T7 |
| AD Events wiring | `SpatialRuleValidator`, `CalloutCascadeValidator` using `ad_spatial_rule` | AD Events phase |
| G8 calibration | `ad_room_boundary` SH LIVING room X-range (IFC global frame) | DAGCompiler |
| Phase PO-1 | `ad_bom` + `ad_bom_child` PO classes in DAGCompiler | After TM-PO proved |
| Phase PO-GEN | Code-generate X_ from `sqlite_master PRAGMA table_info()` | Future tooling |
