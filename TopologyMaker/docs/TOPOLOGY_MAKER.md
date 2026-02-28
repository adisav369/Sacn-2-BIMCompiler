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

---

## 18. Defining the Future — Compiled Construction

*Moved from concept-paper-compliance-gui.md Part 10 (v0.7)*

### 18.1 The Category: Compiled Construction

"Compiled Construction" is the idea that a building specification can be:

1. Written as structured intent (not drawn as geometry)
2. Compiled against rules (not checked after the fact)
3. Proven correct with mathematical witnesses (not assumed correct)
4. Output as both geometry AND procurement BOM (not translated between separate systems)

This is a new category. It does not yet have an industry name. The closest analogy: what `gcc` is to C programming, this compiler is to building design. The user doesn't need to understand constraint propagation; the compiler enforces it. The user doesn't need to know UBBL §33(1); the compiler cites it when rejecting a non-compliant bedroom.

### 18.2 Three Competing Paradigms

| Paradigm | Example | Strength | Ceiling |
|---|---|---|---|
| Manual modelling | Revit, ArchiCAD, Bonsai | Full creative control | Expert-only. Cannot scale to mass housing. |
| AI generative | Autodesk GenDesign, startups | Explores option space | Non-deterministic. Cannot prove compliance. Cannot submit for permits. |
| Compiled from intent | This project | Deterministic, provable, local | Unproven at market scale (yet). |

The AI generative approach will hit a **trust ceiling**: non-deterministic buildings cannot be submitted for a building permit. No authority will accept "the AI says it's compliant" without a machine-readable proof chain.

The manual modelling approach will hit a **cost ceiling**: mass housing in the Global South cannot staff enough Revit-trained professionals at Autodesk subscription prices.

Compiled Construction sits in the gap between those two ceilings.

### 18.3 Three Converging Forces

**Force 1: Digital building permits.** Singapore's CORENET X, EU e-permitting pilots, Dubai's automated permit system. When authorities accept machine-readable compliance proofs instead of human-reviewed drawings, the paradigm shifts. The `witness.json` — a structured proof that UBBL §33(1) bedroom minimum 9.2m² is met with actual 9.61m² — is exactly what digital permits need. No current commercial tool produces this.

**Force 2: IBS/prefab industrialisation.** Malaysia's CIDB mandates IBS scores. Prefab factories need digital BOM inputs, not 2D drawings. The C_Order → M_Production → C_InvoiceLine chain is how manufacturing ERP works. This compiler's BOM output IS the factory input. No other BIM tool speaks this language natively.

**Force 3: The cost constraint.** The Revit/Navisworks/Solibri stack costs more per month than a construction worker earns in the Global South. Cloud AI adds API costs. The only tools that serve this market are free, local, and offline-capable. SQLite on a laptop, no subscription, no internet required.

### 18.4 What Would Make It Defining

**1. An undeniable demo.** A cooperative housing developer picks "Terrace 2-Storey 3BR" from a dropdown, clicks Compile, sees a 3D building + compliance certificate + BOM for procurement. On a laptop. In under 5 seconds. No Revit, no cloud, no training. That single demo proves the entire thesis.

**2. A name and a paper.** "Compiled Construction: A Deterministic Alternative to Generative BIM" — submitted to Automation in Construction or a buildingSMART conference. Once the concept has a citable paper, it exists as a category others can build on and extend.

**3. One government adoption.** If CIDB Malaysia or BCA Singapore accepts `witness.json` as part of a digital permit pilot — even for a single affordable housing project — the proof point moves from "possible" to "accepted."

### 18.5 The Honest Risk

Being architecturally correct is necessary but not sufficient. A VC-funded startup could build a cloud-based "AI BIM compiler" that is technically inferior (non-deterministic, no proof system) but has a sales team, marketing, and enterprise contracts. By the time the market discovers the proofs don't hold up, the startup owns the category name.

The counter: compiled construction produces something AI cannot — a **machine-verifiable proof chain** from intent to compliance to procurement. When digital permits require this chain, marketing alone won't substitute for mathematical witnesses. The question is whether the regulatory timeline aligns with the development timeline.

### 18.6 The Position

This project does not define the future of intelligent BIM by itself. But it defines a **category** — Compiled Construction — that could become the future for the 80% of global construction that is repetitive, code-governed, and procurement-driven. The foundation is correct. The timing may be right. The execution path is the 5-level maturity roadmap. The first undeniable demo (Level 3) is the proof point that makes everything else possible.

---

## 19. Geometric Proof for Generated Buildings — The Synthetic Stone Problem

*Moved from concept-paper-compliance-gui.md Part 11 (v0.7)*

### 19.1 The Core Problem

For extracted buildings (SH, DX, Terminal), proof is straightforward: compare compiled output against the reference IFC. The SpatialDigest hash-total — borrowed from COBOL batch verification of financial records — catches any dimensional change, position shift, or element count difference. Same hash = same building. Different hash = something moved.

**For generated buildings, there IS no reference.** The building never existed before. You cannot compare output against a reference that doesn't exist. The hash-total mechanism is useless for first-generation proof.

This is not a theoretical concern. The TB-LKTN T-shaped roof demonstrated it concretely: AI cannot reliably reconstruct a compound pitched roof (main ridge perpendicular to porch ridge). It generates mesh code that looks plausible, but the geometry is wrong in ways that are invisible without either (a) human visual inspection — which we refuse to rely on — or (b) mathematical proof that doesn't require a reference.

### 19.2 What We Already Have — The Reference-Free Proof Inventory

The compiler already has 23 named proofs (P01-P23) plus mesh validation, witness claims, space contracts, and UBBL rules. Not all require a reference. Here is the complete classification:

#### Proofs That Work Without a Reference (Invariants and Axioms)

| Category | Proofs | Mathematical Basis |
|---|---|---|
| **Element arithmetic** | P01 positive extent, P02 finite coords, P03 min dimension | Every element has positive, finite, non-degenerate AABB |
| **Pairwise uniqueness** | P05 no duplicate centroid, P06 no same-class overlap | No two walls occupy identical space |
| **Perimeter closure** | P10 exterior walls form closed polygon | Graph theory: every vertex has even degree |
| **Physics** | P16 waste flows downhill, P18 vent above roof | Gravity is an axiom |
| **Connectivity** | P17 drainage system connected (BFS), P12 every room has a door | Graph reachability |
| **Orientation** | P20 wall NS/EW consistency (NS: dx < dy) | Geometric constraint from declared orientation |
| **Mesh topology** | hasValidIndices, isManifold, hasNoDegenerateFaces, hasConsistentWinding | Discrete topology: closed manifold, no degenerate faces |
| **Mesh-bbox consistency** | P22 opening mesh within stored rtree bbox | Cross-check within one database |
| **Space contracts** | AREA >= min, DIMENSION >= min, PROPORTION <= max, OPENINGS >= count, PRODUCTS present | Rules from ad_space_dim, applied to compiled output |
| **UBBL rules** | AREA, MIN_DIM per room type | Rules from ad_ubbl_rule |
| **Witness claims** | FOUNDATION_GROUNDED, ROOMS_ENCLOSED, ROOF_COVERS_ALL, ENTRY_EXISTS, ALL_ROOMS_REACHABLE, etc. | Computed from compiled geometry |
| **Drain alignment** | P23 corner gap <= 5mm between orthogonal pipes | Geometric adjacency within output DB |

#### Proofs That REQUIRE a Reference

| Proof | What it compares |
|---|---|
| SpatialDigest hash match | Two databases — compiled vs reference |
| StructuralCrossCheckTest | Per-class element count + bbox digest vs reference DB |
| GeometryIntegrityChecker Check 2 | Vertex/face count vs reference DB |

### 19.3 The Gap: What Reference-Free Proofs Cannot Catch

The invariant proofs catch violations of physics and topology. They do NOT catch:

1. **Wrong-but-valid geometry.** A wall that is 3.0m instead of 3.1m satisfies all invariants (positive extent, finite coords, perimeter closed, orientation correct). But it's the wrong wall. No invariant proof catches "correct but not what was intended."

2. **Plausible-but-wrong mesh.** The T-shaped roof mesh has valid indices, no degenerate faces, consistent winding. But the ridge intersection is in the wrong place. Every mesh topology check passes. The geometry is internally consistent but externally wrong.

3. **Missing elements.** A bedroom without a wardrobe passes all proofs — nothing in the invariant system says a bedroom MUST have a wardrobe. The space contract checks PRODUCTS for required elements, but only if the contract specifies them.

### 19.4 The Solution: The Synthetic Stone

For generated buildings, the **2D layout IS the reference.** TopologyMaker produces room boundaries (DERIVED_MM coordinates) from the typology template's `zone_json` fractions. These boundaries are the **synthetic Rosetta Stone** — the authoritative truth that the compiled output must match.

The proof chain for a generated building:

```
ad_typology_template.zone_json (fractions)
    │
    ▼ TopologyMaker.subdivide()
Room boundaries in mm (DERIVED_MM)     ← THIS IS THE SYNTHETIC STONE
    │
    ▼ Compiler (8-stage pipeline)
Compiled output.db
    │
    ▼ Synthetic Stone Proofs
Output walls enclose exactly these rooms?
Output doors are on exactly these walls?
Output furniture is in exactly these rooms?
Output MEP is in exactly these rooms?
```

**The synthetic stone is not the compiled output. It is the room boundaries that TopologyMaker produces BEFORE compilation.** The compiler's job is to turn those boundaries into walls, doors, furniture, and MEP. The proof checks that the compiler's output is faithful to the boundaries it was given.

### 19.5 Five Synthetic Stone Proofs (SS01–SS05)

These proofs verify a generated building against its own synthetic stone (the TopologyMaker room boundaries), not against any external reference.

#### SS01 ROOM_BOUNDARY_FIDELITY

For every room in the synthetic stone (`ad_room_boundary WHERE coordinate_frame='DERIVED_MM'`), the compiled output must contain walls that enclose a region matching that room's AABB within tolerance.

```
Input:  BILIK_2 boundary = (6800, 2300) to (9900, 5400)
Check:  Find compiled IfcWall elements whose segments bound this region
Proof:  Wall segment endpoints form a closed polygon containing the room AABB
        Tolerance: 50mm (BIMConstants.PLANE_TOLERANCE)
Pass:   Room is enclosed by walls matching its synthetic boundary
Fail:   Room has gaps > 50mm, or walls enclose a different region
```

This is P10 (perimeter closure) applied per-room against the synthetic stone, not just globally. It catches: wrong wall positions, missing wall segments, walls enclosing the wrong region.

#### SS02 AREA_CONSERVATION

The sum of all room areas from the synthetic stone must equal the total enclosed floor area in the compiled output.

```
Input:  SUM(room.areaMm2()) for all rooms in synthetic stone
Check:  SUM(slab.area()) for compiled ground-level IfcSlab elements
Proof:  |synthetic_area - compiled_area| / synthetic_area < 2%
Pass:   No space is lost or gained during compilation
Fail:   Rooms leaked area (walls too thick) or gained area (walls missing)
```

This is a conservation law — the 2D analog of mass conservation. The synthetic stone declares how much space exists. The compiled output must contain exactly that much space. This catches: systematically wrong wall thicknesses, missing rooms, phantom rooms.

#### SS03 OPENING_HOST_MATCH

Every door/window in the compiled output must be hosted on a wall that bounds the room specified in the synthetic stone's opening schedule.

```
Input:  ad_space_type_opening says BILIK_2 has 1× W1 casement on FRONT wall
Check:  Find compiled IfcWindow with host wall on south face of BILIK_2
Proof:  Window exists, is on correct wall face, width matches opening family
Pass:   Opening schedule is faithfully compiled
Fail:   Window on wrong wall, wrong room, or missing
```

This catches the exact problem that X1-SH-GAP exposes: doors/windows placed at wrong coordinates. For extracted buildings, the reference catches it by hash comparison. For generated buildings, the opening schedule IS the reference.

#### SS04 FURNITURE_IN_ROOM_BOUNDS

Every BUY-type element assigned to a room (via BOM line's parent room BOM) must have its compiled centroid inside that room's synthetic stone boundary.

```
Input:  BED_SET assigned to BILIK_2 via m_bom_line
Check:  All BED_SET elements have XY centroid within BILIK_2 boundary
Proof:  centroid.x ∈ [room.minX - 50mm, room.maxX + 50mm]
        centroid.y ∈ [room.minY - 50mm, room.maxY + 50mm]
Pass:   Furniture is in the room it was assigned to
Fail:   Bed ended up in the kitchen
```

This is P08 (furniture in room) made specific: not just "furniture is in SOME room" but "furniture is in THE room the BOM specified." The synthetic stone provides the room identity that the generic proof lacks.

#### SS05 PARAMETRIC_MESH_DIMENSIONS

For every parametric mesh element (structural walls, slabs, roof from `lod_parametric_mesh`), the compiled geometry must match the dimensions derivable from the synthetic stone.

```
Input:  Room BILIK_2 = 3100mm wide on the X-axis
Check:  South-facing wall of BILIK_2 has compiled width = 3100mm ± 50mm
Proof:  wall.maxX - wall.minX ≈ room.maxX - room.minX

Input:  Roof covers footprint 9900 × 8500mm with 700mm overhang
Check:  Compiled IfcRoof has AABB width ≈ 9900 + 2×700 = 11300mm ± 100mm
Proof:  roof.maxX - roof.minX ≈ building.width + 2×overhang
```

This is the mesh proof that doesn't require human eyes. The T-shaped TB-LKTN roof has a computable AABB: `(building_width + 2×overhang) × (building_depth + 2×overhang)`. If the compiled mesh has that AABB within tolerance, the outer dimensions are correct. Combined with mesh topology checks (manifold, no degenerate faces, consistent winding), this proves the mesh is both topologically valid AND dimensionally correct — without looking at it.

**For compound shapes (the T-roof):** Decompose into sub-volumes. Main ridge: `width × depth_main × pitch_height`. Porch ridge: `width_porch × depth_porch × pitch_height_porch`. Total roof AABB = bounding box of both sub-volumes. Each sub-volume's dimensions are derivable from the synthetic stone's room boundaries + `lod_roof_preset` parameters. The mesh volume must equal the geometric volume of the computed roof form within tolerance.

### 19.6 The Proof Hierarchy for Generated Buildings

```
Level 0: Mesh topology (reference-free, always runs)
         hasValidIndices, isManifold, hasNoDegenerateFaces, hasConsistentWinding
         "The geometry is not broken"

Level 1: Physical axioms (reference-free, always runs)
         P01-P03 (positive/finite/min-dim), P16 (waste downhill),
         P17 (drainage connected), P10 (perimeter closed)
         "The building obeys physics"

Level 2: Code compliance (reference-free, requires ad_code_constraint)
         UBBL area/dimension rules, space contracts, witness claims
         "The building is legal"

Level 3: Synthetic stone fidelity (requires TopologyMaker output)
         SS01-SS05: room boundaries, area conservation, opening hosts,
         furniture placement, parametric mesh dimensions
         "The compiled output matches the design intent"

Level 4: Reference comparison (requires Rosetta Stone)
         SpatialDigest hash, StructuralCrossCheckTest
         "The compiled output matches a known reference"
         NOT APPLICABLE to generated buildings — by definition
```

For extracted buildings (SH, DX, Terminal): all 5 levels apply. Level 4 is the ultimate gate.

For generated buildings (TopologyMaker output): Levels 0-3 apply. Level 3 replaces Level 4 — the synthetic stone IS the reference. Level 4 is structurally impossible and not needed.

**The key insight: a generated building doesn't need to match an external reference. It needs to match its own specification.** The TopologyMaker's room boundaries ARE that specification. The proofs verify fidelity to specification, not fidelity to a pre-existing building.

### 19.7 The COBOL Hash-Total Principle Applied

The SpatialDigest hash-total (SHA-256 of CLASS + COUNT + sorted bboxes) was borrowed from COBOL batch processing: hash the financial records, store the hash, recompute later to detect tampering. Same principle, different domain.

For generated buildings, the hash-total applies at a different level:

```
EXTRACTED buildings:
  Hash(compiled output) == Hash(reference IFC)    → same building

GENERATED buildings:
  Hash(compiled walls)  matches Hash(synthetic room boundaries)  → faithful compilation
  Hash(compiled MEP)    matches Hash(MEP schedule from ad_space_type_mep)  → complete MEP
  Hash(compiled struct) matches Hash(structural grid from ad_building_grid) → correct structure
```

Each domain (walls, MEP, structural) gets its own hash-total, computed from the synthetic stone's specification for that domain. The combined hash is the **compilation fingerprint** — not "is this the same building as before?" but "did the compiler faithfully execute the specification?"

Once the compilation fingerprint is established for a generated building, it BECOMES the reference for regression testing. Run the same TopologyMaker input tomorrow → same fingerprint must result. The synthetic stone hardens into a permanent stone.

### 19.8 What This Means for the Maturity Roadmap

The SS01-SS05 proofs must be implemented as part of Level 2 (Generative From Catalog) in the maturity model. Without them, a generated building has no quality gate — the compiler could produce garbage and ProveStage would not catch it (since the reference-comparison proofs don't apply).

Implementation sequence:
1. **SS02 (area conservation)** — simplest, highest value. Catches missing rooms, phantom space.
2. **SS01 (room boundary fidelity)** — the wall-enclosure proof. Catches wrong wall positions.
3. **SS04 (furniture in room bounds)** — catches placement errors in the BOM resolver.
4. **SS05 (parametric mesh dimensions)** — catches the T-roof problem. Requires decomposition into sub-volumes.
5. **SS03 (opening host match)** — catches door/window misplacement. Requires opening schedule in BOM.

SS02 and SS01 together prove the building's spatial structure is correct. SS04 proves the contents are in the right rooms. SS05 proves the generated meshes have correct dimensions. SS03 proves the openings are where the schedule says they should be. All five together = the synthetic stone is a complete proof framework for generated buildings.
