# TopologyMaker — Site Brief to Compilable Topology

**Version:** 1.0
**Date:** 2026-02-23
**Status:** GOVERNING — defines the batch process that turns a site brief into compiler-ready rows
**Authors:** red1 (architect) + Claude Watchdog (reviewer)
**Supplements:** PREFAB_ARCHITECTURE.md, VIEW_CONTRACTS.md, METADATA_DRIVEN_ARCHITECTURE.md
**iDempiere analogues:** AD_Process (batch), C_Order/C_OrderLine lifecycle, MRP BOM explosion

---

## 1. Problem Statement

The BIM compiler requires two things to compile a new building:

1. **`ad_room_boundary` rows** — spatial footprints that tell the compiler where each room sits
2. **`ad_building_registry` row** — the DSL manifest that tells the compiler the building exists

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
M_Product catalogue           ad_typology_pattern (typology templates)
C_OrderLine validation        UbblValidator (UBBL spatial rules vs RoomCell list)
MRP BOM explosion             PrefabBom builder → FLOOR + UNIT rows in ad_bom
DocStatus DR→IP→CO            DocStatus enum propagated through TopologyResult
Rollback on failure           TopologyWriter single transaction, rollback on any SQLException
```

**Key insight:** the compiler does not know TopologyMaker exists. It reads
`v_verified_room_boundary` and `ad_building_registry` as it always has. TopologyMaker
is purely a row producer — a supply-side concern, not a demand-side one.

---

## 3. Module Structure

```
TopologyMaker/                                    Maven module: topology-maker
├── pom.xml                                       sqlite-jdbc + gson + junit only
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
    │   └── db/
    │       ├── TopologyAccessLayer.java           reads ad_typology_pattern + ad_spatial_rule
    │       └── TopologyWriter.java                writes ad_room_boundary + ad_bom + ad_building_registry
    │           └── PrefabBom (inner class)        FLOOR/UNIT BOM builder
    └── test/java/com/bim/compiler/topologymaker/
        └── TopologyBatchProcessTest.java         7 assertions — all GREEN
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
  SiteEnvelope     │       │   reads ad_typology_pattern
    widthMm        │       │   reads ad_spatial_rule
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
                                                              writeRoomBoundaries()
                                                              writeBom(FLOOR)        ad_bom/child
                                                              writeBom(UNIT)         ad_bom/child
                                                              registerBuilding()     ad_building_registry
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
| `ad_room_boundary` | Spatial footprints | min/max X/Y in mm, coordinate_frame=DERIVED_MM | Rotation, orientation, placement fractions |
| `ad_bom` / `ad_bom_child` | Assembly hierarchy | FLOOR + UNIT BOMs per order; catalog prefabs pre-seeded by migration | Product dimensions, element rules |
| `ad_building_registry` | Building manifest | One GENERATIVE row per order; DSL content auto-generated | Geometry, spatial digest |

**Never written:** `ad_element_rule`, `ad_product_dim`, `ad_element_placement`, `ad_geometry_map`.

The catalog (Layers 1–3 in §8) is seeded once by migration and never touched by the batch
process. The batch process only produces the per-order FLOOR + UNIT layers (4–5).

---

## 6. Coordinate Frame

All `ad_room_boundary` rows produced by TopologyMaker use `coordinate_frame = 'DERIVED_MM'`.

This value is already in the `v_verified_room_boundary` CHECK constraint alongside
`IFC_GLOBAL_MM`, `LOCAL_MM`, `DRAWING_MM`, and `CONSTRAINT_SOLVED`. The compiler's
`ViewAccessLayer.getRoomBoundary()` call hits this view — it sees DERIVED_MM rows
immediately with no migration needed on the compiler side.

**Origin convention:** site south-west corner = (0, 0). X increases eastward (across
frontage). Y increases northward (street to back). All fractions in zone_json are
applied to site widthMm (X axis) and depthMm (Y axis).

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

### 7.1 zone_json Schema

Each element in the array describes one spatial zone:

```json
{
  "zone":       "BED_R1",       // unique zone identifier
  "room_type":  "BEDROOM",      // → ad_space_type, → ad_room_slot dispatch
  "x_from":     0.687,          // fraction of site widthMm (omit = 0.0)
  "x_to":       1.0,            // fraction of site widthMm (omit = 1.0)
  "y_from":     0.271,          // fraction of site depthMm (omit = 0.0)
  "y_to":       0.635,          // fraction of site depthMm (omit = 1.0)
  "depth_frac": 0.271           // PORCH only: overrides y_to
}
```

`StripZoneStrategy.subdivide()` reads these fractions, multiplies by the site envelope
dimensions, rounds to integer mm, and emits one `RoomCell` per zone. Zones with
`maxXMm == minXMm` or `maxYMm == minYMm` after rounding are silently dropped.

### 7.2 numBedrooms gating

The typology template declares the maximum bedroom configuration. At runtime:

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
  TERRACE_001_UNIT    → TERRACE_001_GF (GF), ROOF_ASSEMBLY (ROOF)

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
  BED_SET_MASTER           = FURN_BED_KING + SIDE_TABLE + TALL_CABINET ×2  [EXISTING]
  LIVING_SET               = sofa + coffee table + dining set               [EXISTING]
  TOILET_BLOCK_FIXTURES    = TOILET + SINK_BASIN + SHOWER_TRAY              [EXISTING]

LAYER 1 — ITEM   (ad_product_dim)          EXIST in catalog
  WINDOW_W1, WINDOW_W2, DOOR_D2, ELEC_LIGHT, FURN_BED_KING,
  FURN_WARDROBE, FURN_SOFA, FURN_COFFEE_TABLE, FIXTURE_TOILET, ...
```

**New vs existing at each layer:**
- Layer 5: `{orderId}_UNIT` — new per run
- Layer 4: `{orderId}_GF` — new per run
- Layers 1–3: all pre-seeded, never touched by the batch process

---

## 9. UBBL Validation

`UbblValidator` checks each `RoomCell` against `ad_spatial_rule` before any DB writes.
If any violation is found, the process returns `DocStatus.IP` and writes nothing.

### 9.1 Rules seeded (Malaysian UBBL 1984)

| rule_id | room_type | constraint | min_value | UBBL ref |
|---|---|---|---|---|
| UBBL_BED_AREA | BEDROOM | AREA | 9,290 mm² | UBBL 1984 §51 |
| UBBL_BATH_AREA | BATHROOM | AREA | 2,500 mm² | UBBL 1984 §56 |
| UBBL_TOI_AREA | TOILET | AREA | 1,500 mm² | UBBL 1984 §56 |
| UBBL_BED_DIM | BEDROOM | MIN_DIM | 2,700 mm | UBBL 1984 §51 |
| UBBL_CEIL | (all) | CEILING_MM | 2,400 mm | — |

### 9.2 Constraint evaluation

`AREA` — `RoomCell.areaMm2()` = `(maxXMm − minXMm) × (maxYMm − minYMm)`.
Compared directly against `min_value_mm` (both in mm²).

`MIN_DIM` — `RoomCell.minDimMm()` = `min(width, depth)`. Compared against
`min_value_mm` (mm).

`CEILING_MM` and `NOT_ADJACENT` — skipped at validation time (3D/adjacency concerns
outside the scope of plan-level topology generation).

### 9.3 TERRACE_MY_1S proof

For a 9900×8500mm site, 3 bedrooms, porch:

| Zone | Width mm | Depth mm | Area mm² | Min dim mm | UBBL_BED_AREA (9290) | UBBL_BED_DIM (2700) |
|---|---|---|---|---|---|---|
| BED_1 (BED_L) | 3100 | 6120 | 18,972,000 | 3100 | PASS | PASS |
| BED_2 (BED_R1) | 3069 | 3098 | 9,507,762 | 3069 | PASS | PASS |
| BED_3 (BED_R2) | 3069 | 3102 | 9,519,738 | 3069 | PASS | PASS |
| BATHROOM | 1307 | 1513 | 1,977,491 | 1307 | — | — |
| TOILET | 1307 | 1601 | 2,092,507 | 1307 | — | — |

All five active UBBL rules pass. `TopologyResult.violations()` is empty. Process
advances to `DocStatus.CO`.

---

## 10. DocStatus Lifecycle

```
   TopologyOrder created with status=DR or IP
         │
         ▼
   TopologyBatchProcess.complete(order)
         │
         ├── typology not found ──────────────────────────► IP (log: "typology not found")
         │
         ├── strategy not found ──────────────────────────► IP (log: "no GridStrategy for ...")
         │
         ├── UBBL violations ─────────────────────────────► IP (violations list populated)
         │
         ├── DB write error ──────────────────────────────► IP (rollback, log: error message)
         │
         └── all steps pass ──────────────────────────────► CO
                                                             roomsWritten = N
                                                             bomsWritten  = 2
                                                             violations   = []
```

`TopologyResult.isComplete()` returns `true` only for `DocStatus.CO`. Callers must
check this before treating the result as production-ready.

The `ad_building_registry` row is written with `doc_status = 'DR'`. The compiler
promotes it to `CO` when `mvn test` succeeds and all assertions pass. This mirrors
the C_Order → posted pattern in iDempiere: the batch creates the order; approval
(compilation pass) posts it.

---

## 11. GridStrategy — Extension Point

`GridStrategy` is an interface. The `STRATEGIES` map in `TopologyBatchProcess` is the
registry. Adding a new strategy requires:

1. Implement `GridStrategy` in the `grid/` package
2. Add one entry to the `STRATEGIES` map in `TopologyBatchProcess`
3. Add the grid_strategy value to the CHECK constraint in `ad_typology_pattern`
4. Seed a typology row using the new strategy ID

No other code changes. The orchestrator does not need to know which strategy it is
calling — it calls `strategy.subdivide(site, zoneJson)` uniformly.

| strategy_id | Class | Description |
|---|---|---|
| `STRIP_ZONES` | `StripZoneStrategy` | Vertical strips divided into horizontal bands — terrace house, semi-D |
| `COURTYARD` | *(pending)* | Perimeter rooms around a central void — shophouse, courtyard villa |
| `LINEAR` | *(pending)* | Single corridor spine — apartment block, row house |

---

## 12. TopologyWriter — Transaction Guarantee

All writes for one order are in a single JDBC transaction. The sequence is:

```
conn.setAutoCommit(false)
  writeRoomBoundaries()  — N × INSERT OR IGNORE into ad_room_boundary
  writeBom(floor)        — INSERT OR IGNORE into ad_bom + ad_bom_child
  writeBom(unit)         — INSERT OR IGNORE into ad_bom + ad_bom_child
  registerBuilding()     — INSERT OR IGNORE into ad_building_registry
conn.commit()            — atomic
```

On any `SQLException`, `rollback()` is called and the process returns `DocStatus.IP`.
Partial writes never persist.

`INSERT OR IGNORE` on all primary keys means the process is idempotent. Running the
same order twice produces the same DB state. This supports re-running after a
previously failed partial write without cleanup.

---

## 13. Migration

`migration/migration_topology_maker_bootstrap.sql` is ADD-ONLY. It:

- Creates `ad_typology_pattern` and `ad_spatial_rule` (CREATE TABLE IF NOT EXISTS)
- Seeds TERRACE_MY_1S typology and 5 UBBL rules (INSERT OR IGNORE)
- Seeds 3 wall prefab BOMs and 7 wall BOM children (INSERT OR IGNORE)
- Fills WARDROBE_SET children (previously empty)
- Seeds 4 room prefab BOMs and 16 room BOM children (INSERT OR IGNORE)

**Never modifies existing rows.** SpatialDigests are unchanged. The existing DAGCompiler
test suite is unaffected.

To apply to the canonical library DB:

```bash
sqlite3 library/component_library.db < migration/migration_topology_maker_bootstrap.sql
```

After applying, `mvn test -pl TopologyMaker` runs 7/7 GREEN using a temp copy of the
migrated DB. The canonical DB migration only needs to be applied once before the first
production topology generation.

---

## 14. Usage

```java
// Create an order
TopologyOrder order = new TopologyOrder(
    "TERRACE_001",              // orderId — becomes building_id in registry
    "TERRACE_MY_1S",            // typologyId — FK to ad_typology_pattern
    new SiteEnvelope(
        9900, 8500,             // widthMm × depthMm
        3,                      // numBedrooms
        true                    // hasPorch
    ),
    DocStatus.IP                // initial status (DR or IP)
);

// Run the batch process
TopologyResult result = new TopologyBatchProcess("library/component_library.db")
    .complete(order);

// Check result
if (result.isComplete()) {
    System.out.println("Ready: " + result.roomsWritten() + " rooms written");
    // → compiler can now compile TERRACE_001
} else {
    System.out.println("Blocked: " + result.violations());
    System.out.println("Log: " + result.log());
}
```

After a successful run, the compiler picks up the new building on the next `mvn test`:

```bash
mvn test -pl DAGCompiler
# TERRACE_001 now appears in BuildingRegistryTest output
```

---

## 15. Test Coverage

`TopologyBatchProcessTest.java` — 7 assertions, all GREEN:

| # | Assertion | What it proves |
|---|---|---|
| T6-1 | `result.isComplete() == true` | Full pipeline CO for standard 3BR site |
| T6-2 | `roomsWritten == 7` | PORCH + 3×BEDROOM + COMMON + BATHROOM + TOILET |
| T6-3 | All `coordinate_frame = 'DERIVED_MM'` | View contract honoured |
| T6-4 | `violations.isEmpty()` | TERRACE_MY_1S passes Malaysian UBBL 1984 |
| T6-5 | 3 wall prefab BOMs exist | Migration seeded catalog correctly |
| T6-6 | `{orderId}_GF` (FLOOR) + `{orderId}_UNIT` (UNIT) in ad_bom | Layer 4–5 generation |
| T6-7 | Building in `ad_building_registry` with `provenance='GENERATIVE'` | Registry row written |

Tests use a temporary copy of the library DB so the canonical DB is never modified by
the test suite. Migration SQL is applied to the temp copy in `@BeforeAll`.

---

## 16. What Remains

| Item | Description | Phase |
|---|---|---|
| Apply migration to canonical DB | `sqlite3 library/component_library.db < migration/migration_topology_maker_bootstrap.sql` | Immediate |
| COURTYARD strategy | Perimeter-room typology for shophouses / courtyard villas | TopologyMaker T7 |
| AD Events wiring | `SpatialRuleValidator`, `CalloutCascadeValidator` using the same `ad_spatial_rule` table | AD Events phase |
| G8 calibration | `ad_room_boundary` SH LIVING room X-range (IFC global frame) | DAGCompiler |
| TB-LKTN X5/X6 X-Ray gates | UBBL area + placement checks for TB-LKTN rooms | DAGCompiler |
