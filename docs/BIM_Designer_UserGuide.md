# BIM Designer — User Guide
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [ConstructionAsERP](ConstructionAsERP.md) · [TestArchitecture](TestArchitecture.md)

**Version:** 0.8 (2026-03-20, session 39d)
**Status:** Draft — updated each session as features are built and tested.

> This guide covers the BIM Designer addon for Blender (Bonsai), the Java
> DesignerServer, and the validation engine. It is written for developers
> and testers setting up the system for the first time.
>
> **Two-tier architecture:** The Bonsai addon is the **single-user, single-project**
> design client. The [BIM Back Office](BackOfficeUserGuide.md) is the **multi-user,
> multi-project** ERP layer (reports, print config, portfolio). Both share the same
> databases. The Bonsai client calls back-office APIs via the BlenderBridge.

---

## 1. What Is BIM Designer?

BIM Designer is Item A of the IfcOpenShell Federation Suite. It adds
**compiler-driven building design** to Blender's Bonsai addon:

- **Compile** existing buildings (YAML + BOM → output.db → 3D viewport)
- **Create New** generative buildings (dialog → BOM → validate → compile)
- **Design Mode** — edit draft bboxes with snap, save/recall, and ambient compliance
- **BOM Chooser** — search-first product browser with container fit check
- **Validate** placements against building codes (UBBL, IRC, NDSS, NCC, BCA)
- **Assembly Builder** — layer-by-layer wall/floor construction with U-value calculation
- **Infrastructure** — terrain-following placement for roads, bridges, railways
- **Reports** — 4D schedule, 5D cost, 6D carbon, 7D facility management (via Back Office)

The addon is a thin Python layer. All logic lives in the Java server.
**5 Rosetta Stone buildings** prove the pipeline: SH (55), FK (82), IN (699), DX (1099), TE (48,428 elements).

```
User clicks button in Blender
  → Python operator sends JSON over TCP
  → Java DesignerServer processes (compile, validate, generate BOM)
  → Response sent back
  → Python loads result into Blender viewport
```

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│  Blender (Bonsai)                                       │
│  ┌─────────────────────┐  ┌──────────────────────────┐  │
│  │ Federation Addon     │  │ BIM Designer Addon       │  │
│  │ (IfcOpenShell repo)  │  │ (bim-compiler repo)      │  │
│  │                      │  │                          │  │
│  │ #1 Federation Setup  │  │ A. BIM Designer          │  │
│  │ #2 Visualization     │←─│   A.1 Connection         │  │
│  │ #3 MEP Coordination  │  │   A.2 Building Selector  │  │
│  │ #4 Clash Detection   │  │   A.3 Create New/Design  │  │
│  │ ...                  │  │     Section Chooser      │  │
│  │                      │  │     BOM Chooser          │  │
│  │                      │  │     Snap/Save/Promote    │  │
│  │                      │  │     Status Strip         │  │
│  │                      │  │   A.4 Verb Console       │  │
│  └─────────────────────┘  └───────────┬──────────────┘  │
│                                       │ TCP 9876         │
└───────────────────────────────────────┼─────────────────┘
                                        │
┌───────────────────────────────────────┼─────────────────┐
│  Java DesignerServer                  │                  │
│  ┌────────────────────────────────────┘                  │
│  │                                                      │
│  │  34 wire actions (compile, createNew, snap, save,    │
│  │  browseItems, placeItem, addRoom, carbonFootprint,   │
│  │  costBreakdown, constructionSchedule, portfolio...)   │
│  │                                                      │
│  │  PlacementValidator ←── disc_validation.db (63 rules)│
│  │  BomValidator ←── 9 checks + verb fidelity           │
│  │  AssemblyBuilder ←── thermal U-value calculation     │
│  └─────────────────────────┬────────────────────────────┘
│                             │ depends on                  │
│  ┌──────────────────────────▼───────────────────────────┐ │
│  │  BIMBackOffice (multi-user, multi-project)           │ │
│  │  ReportDAO (4D-7D) │ PrintConfig │ PortfolioDAO     │ │
│  │  ChangelogDAO      │ CostDAO     │ SustainabilityDAO│ │
│  └──────────────────────────────────────────────────────┘ │
│                                                          │
│  Databases:                                              │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐ │
│  │ component_   │ │ {PREFIX}_    │ │ disc_validation  │ │
│  │ library.db   │ │ BOM.db       │ │ .db (63 rules)   │ │
│  │ (800 prods)  │ │ (recipes)    │ │                  │ │
│  └──────────────┘ └──────────────┘ └──────────────────┘ │
│  ┌──────────────┐                                       │
│  │ work_output  │  ← Design Mode + audit trail          │
│  │ .db          │    (C_Order, W_Variant, bim_changelog) │
│  └──────────────┘                                       │
└──────────────────────────────────────────────────────────┘
```

### Six Databases

| DB | What | Size |
|----|------|------|
| `component_library.db` | Product catalog (800 products, meshes, materials, thermal properties) | ~500 MB |
| `{PREFIX}_BOM.db` | Assembly recipes per building (BOM hierarchy, tack offsets, verb patterns) | ~10 MB |
| `disc_validation.db` | Validation rules (63 rules: residential + infrastructure bridge/road/rail) | ~100 KB |
| `output_*.db` | Compiled result (elements, R-tree index, QTO, spatial structure) | varies |
| `work_output.db` | Design Mode persistence (C_Order, C_OrderLine, W_Variant, bim_changelog) | per-building |
| `AD_PrintFormat` | Print configurator (in work_output.db — which output tables to include) | per-building |

---

## 3. Setup

### 3.1 Prerequisites

- Java 17+ (for DesignerServer)
- Maven 3.8+ (for building)
- Blender 3.6+ with Bonsai addon
- Python 3.10+ (Blender's bundled Python)
- SQLite 3

### 3.2 Build the Java Server

```bash
cd /home/red1/bim-compiler
mvn compile -q                    # Compile all modules (9 modules)
mvn test -pl BIMBackOffice        # Back Office tests (5 tests)
mvn test -pl BonsaiBIMDesigner    # Designer tests (248 tests)
```

### 3.3 Install the Blender Addon

Copy the addon directory into Blender's addon path:

```bash
# Option 1: Symlink (for development)
ln -s /home/red1/bim-compiler/BonsaiBIMDesigner/src/main/python/bonsai_bim_designer \
      ~/.config/blender/3.6/scripts/addons/bonsai_bim_designer

# Option 2: Copy
cp -r BonsaiBIMDesigner/src/main/python/bonsai_bim_designer \
      ~/.config/blender/3.6/scripts/addons/
```

Enable in Blender: Edit → Preferences → Add-ons → search "BIM Designer" → enable.

### 3.4 Verify Databases Exist

```bash
ls -la library/component_library.db   # Product catalog
ls -la library/validation.db          # Validation rules (32 rules)
ls -la library/DM_BOM.db              # DemoHouse BOM (generative POC)
ls -la library/SH_BOM.db              # SampleHouse BOM (Rosetta Stone)
```

If `validation.db` is missing:
```bash
sqlite3 library/validation.db < migration/V001_validation_schema.sql
sqlite3 library/validation.db < migration/V002_validation_seed.sql
```

---

## 4. Starting the Server

```bash
cd /home/red1/bim-compiler

# Option 1: Run via Maven (development)
mvn exec:java -pl BonsaiBIMDesigner \
  -Dexec.mainClass="com.bim.designer.api.DesignerServer"

# Option 2: Run the test suite (includes server start/stop)
mvn test -pl BonsaiBIMDesigner -Dtest="DesignerServerTest"
```

The server listens on **TCP port 9876** (configurable). It accepts
newline-delimited JSON (ndjson) — one JSON object per line.

### 4.1 Test the Connection

```bash
# Send a listBuildings request
echo '{"action":"listBuildings"}' | nc localhost 9876
```

Expected response: JSON array of building types.

---

## 5. Using the Blender Addon

### 5.1 Panel Location

The BIM Designer panel appears in:
**Properties → Scene → A. BIM Designer**

It sits alongside the Federation addon's numbered panels (1-10).

### 5.2 Connection (A.1)

| Field | Default | Description |
|-------|---------|-------------|
| Host | 127.0.0.1 | Java server address |
| Port | 9876 | Java server port |
| Connect / Disconnect | — | Toggle connection |

All other buttons are disabled until connected.

### 5.3 Building Selector (A.2)

| Field | Description |
|-------|-------------|
| Building ID | Name of the building to compile (e.g., `Ifc4_SampleHouse`) |
| BOM DB Path | Path to the BOM database (e.g., `library/SH_BOM.db`) |
| Output DB Path | Where the compiled output goes |
| List Buildings | Queries server for available building types |
| Compile | Triggers full compilation → output.db |

**Compile workflow:**
1. Select a building ID
2. Set BOM DB path
3. Click Compile
4. Status shows "Compiled: 55 elements in 847ms"
5. Output.db ready for Full Load via Federation #2

### 5.4 Create New (A.3)

| Field | Options | Description |
|-------|---------|-------------|
| Building Name | Free text | Name for the new building |
| Building Type | DETACHED, SEMI_D, TERRACE, APARTMENT | House typology |
| Jurisdiction | MY, US, UK, AU, SG | Which building code applies |
| Site Width | 3000–50000 mm | Total site width |
| Site Depth | 3000–50000 mm | Total site depth |
| Bedrooms | 1–6 | Number of bedrooms |
| Bathrooms | 1–4 | Number of bathrooms |
| Storeys | 1–5 | Number of storeys |
| Generate | — | Creates layout → enters Design Mode |

**Create New workflow:**
1. Fill in building parameters
2. Click Generate Building
3. Server generates room layout (BUILDING → FLOOR → ROOMS) as bounding boxes
4. Blender enters Design Mode with coloured bboxes
5. Section chooser shows clickable BOM tree (floor headers + room cards)
6. Use Snap/Save/BOM Chooser to refine (see §5.5–5.8)

### 5.5 Design Mode

Design Mode shows the building as coloured bounding boxes — one per room,
floor, and building. The user edits layout in this mode, then saves variants.

| Control | Description |
|---------|-------------|
| DESIGN / REAL toggle | Switch between draft bboxes and Federation view |
| Section Chooser | Click a floor or room to focus it (vivid colour, others grey) |
| Active Section | Highlighted bbox; BOM Chooser uses it for container fit |

**Visual states:**
- Canvas (no focus): all bboxes at category colour
- Focused: selected bbox vivid, siblings grey-out
- Committed: pulsing alpha animation after Save

### 5.6 Snap + Compliance (UX-F-18/19)

| Action | What it does |
|--------|-------------|
| Snap | Aligns room dimensions to grid (default 250mm) + validates against jurisdiction rules |

After Snap, the **Status Strip** shows per-rule compliance:
- Green check: rule passes
- Red X with delta: "ROOM_BD: 2800mm < 3000mm (need +200mm)"
- Grid adjustments: "width: 2850 → 3000mm (grid)"

### 5.7 Save / Recall / Promote

| Action | Wire protocol | What it does |
|--------|--------------|-------------|
| Save | `save` | Stores current bboxes to `work_output.db` as a new variant (C_Order + C_OrderLine + W_Variant). Cheap, frequent. |
| Recall | `recall` | Restores a previous variant. Non-destructive — originals never overwritten. |
| List Variants | `listVariants` | Shows all saved variants (most recent first) with label and line count. |
| Promote | `promote` | Governance gate — creates m_bom + m_bom_line in BOM.db. Requires compliance pass. |

**Save creates:**
- Sub-C_Order (child of master order for the building)
- C_OrderLine per bbox (with tack dx/dy/dz in mm)
- W_Variant pointer (label, is_active, line count)

**Round-trip fidelity:** Save stores mm, recall restores mm. No precision loss.

### 5.8 BOM Chooser — Search-First Product Browser (§17.18)

The BOM Chooser is a search-first product browser for adding items to a room.

| Control | Description |
|---------|-------------|
| Search bar | Type keywords to search M_Product by name (SQL LIKE) |
| Category tabs | Filter by product type (ELEMENT, DOOR, WINDOW, WALL, etc.) with fits/total counts |
| Results list | Each item shows name, dimensions, and fit status icon |
| Pagination | Prev/Next for 20-item pages when results exceed one page |

**Container fit check (real-time):**

When a room is focused (active section), every item is checked against
that room's AABB:

| Status | Meaning | Icon |
|--------|---------|------|
| FITS | Item fits with ≥100mm clearance on all axes | Green check |
| TIGHT | Fits but <100mm clearance on one axis | Yellow warning |
| TOO_WIDE | Exceeds room width | Red X |
| TOO_DEEP | Exceeds room depth | Red X |
| TOO_TALL | Exceeds room height | Red X |

Items that don't fit are **shown, not hidden** — the user might want to
resize the room to accommodate them.

**Wire protocol:**
```json
{"action": "browseItems",
 "search": "queen bed",
 "category": "ELEMENT",
 "buildingType": "Ifc4_SampleHouse",
 "containerWidthMm": 3100, "containerDepthMm": 3100, "containerHeightMm": 3000,
 "offset": 0, "limit": 20}
```

Response includes `items` (with `fitStatus`), `totalCount`, and `categories`
(with `count` and `fitsCount` per category).

### 5.9 Verb Console (A.4)

| Field | Description |
|-------|-------------|
| Verb Line | BIM COBOL command (e.g., `CHECK BOM BUILDING_SH`) |
| Execute | Sends verb to server for dispatch |
| Result | Shows verb output |

---

## 6. Validation Rules

The system validates placements against building codes from `disc_validation.db`.
Rules are **data, not code** — adding a jurisdiction = SQL INSERTs.

### 6.1 Supported Jurisdictions

| Code | Country | Standard | Rules |
|------|---------|----------|-------|
| MY | Malaysia | UBBL 2012 | 10 |
| US | USA | IRC 2021 | 6 |
| UK | United Kingdom | NDSS 2015 / Building Regs | 4 |
| AU | Australia | NCC 2022 | 4 |
| SG | Singapore | BCA Approved Document | 3 |
| INTL | International | NFPA 13 (sprinkler spacing) | 6 |
| Infra_Bridge | Infrastructure | Bridge structural rules | 13 |
| Infra_Road | Infrastructure | Road layer stacking | 10 |
| Infra_Rail | Infrastructure | Rail geometry + sleeper spacing | 7 |

**Total: 63 rules** (33 residential + 30 infrastructure).

### 6.2 Malaysian Rules (UBBL 2012)

| Rule | What it checks | Minimum | Standard |
|------|---------------|---------|----------|
| UBBL_BEDROOM_MIN_AREA | Bedroom floor area | 9.2 m² | s33(1) |
| UBBL_BEDROOM_MIN_DIM | Bedroom smallest dimension | 3000 mm | s33(1) |
| UBBL_KITCHEN_MIN_AREA | Kitchen floor area | 4.5 m² | s33(2) |
| UBBL_KITCHEN_MIN_DIM | Kitchen smallest dimension | 1500 mm | s33(2) |
| UBBL_BATHROOM_MIN_AREA | Bathroom floor area | 1.5 m² | s33(3) |
| UBBL_LIVING_MIN_AREA | Living room floor area | 12.0 m² | s33(4) |
| UBBL_CEILING_MIN_HEIGHT | Ceiling height | 2600 mm | s36 |
| UBBL_CORRIDOR_MIN_WIDTH | Corridor width | 900 mm | s40 |
| UBBL_DOOR_MIN_WIDTH | Door clear opening | 750 mm | s41 |
| UBBL_WINDOW_MIN_AREA_RATIO | Window-to-floor area ratio | 10% | s39 |

### 6.3 How Validation Works

```
User sets jurisdiction to MY (Malaysia)
  → PlacementValidator.activate("MY", valConn)
  → Loads 10 UBBL rules from validation.db

User creates BEDROOM 2800×3500mm
  → PlacementRequest(bomCategory="BEDROOM", widthMm=2800, depthMm=3500, ...)
  → Validator checks:
      min_dim_mm: MIN(2800, 3500) = 2800 < 3000 → BLOCK
  → Returns: "BLOCK: BEDROOM min dimension 2800mm < 3000mm [UBBL 2012 s33(1)]"
  → Room rejected — user must increase to 3000mm+

User changes to 3100×3500mm
  → min_dim_mm: 3100 >= 3000 → PASS
  → min_area_m2: 10.85 >= 9.2 → PASS
  → Returns: PASS
```

Validation is **per-jurisdiction, per-instance**. Changing jurisdiction
re-evaluates all rooms against the new code.

### 6.4 Extracted vs Generative

| Building type | Validation | Why |
|--------------|-----------|-----|
| Extracted (RosettaStone) | SKIPPED | Original engineer's design is ground truth |
| Generative (Create New) | ENFORCED | No engineer reviewed — rules are the gate |

---

## 7. DemoHouse_2BR — The First Generative Building

### 7.1 What It Is

A minimal 2-bedroom Malaysian house proving the generative path works.
No IFC extraction — entirely generated from BOM parameters.

- **Name:** DemoHouse_2BR
- **Jurisdiction:** MY (UBBL 2012)
- **Envelope:** 9000 × 7000 × 3000 mm
- **Provenance:** GENERATIVE

### 7.2 Room Layout

| Room | Category | Width | Depth | Height | Area | UBBL Verdict |
|------|----------|-------|-------|--------|------|-------------|
| ruang_tamu | LIVING | 4000 | 3500 | 2800 | 14.0 m² | PASS (≥12.0) |
| dapur | KITCHEN | 4000 | 3500 | 2800 | 14.0 m² | PASS (≥4.5) |
| bilik_1 | BEDROOM | 5000 | 3500 | 2800 | 17.5 m² | PASS (≥9.2, dim≥3000) |
| bilik_2 | BEDROOM | 5000 | 3500 | 2800 | 17.5 m² | PASS (≥9.2, dim≥3000) |
| bilik_mandi | BATHROOM | 2000 | 1500 | 2800 | 3.0 m² | PASS (≥1.5) |

### 7.3 BOM Structure

```
BUILDING_DEMO_2BR (BUILDING)
└── FLOOR_DEMO_GF (FLOOR)
    ├── ROOM_DEMO_LI (LIVING)  → 5 leaves (2 walls, window, door, slab)
    ├── ROOM_DEMO_KT (KITCHEN) → 3 leaves (wall, window, slab)
    ├── ROOM_DEMO_BD1 (BEDROOM) → 4 leaves (wall, window, door, slab)
    ├── ROOM_DEMO_BD2 (BEDROOM) → 5 leaves (2 walls, window, door, slab)
    └── ROOM_DEMO_BT (BATHROOM) → 2 leaves (door, slab)
Total: 25 BOM lines, 19 leaf elements
```

### 7.4 Seed Products

7 products seeded in `component_library.db`:

| Product | Type | Dimensions | Material |
|---------|------|-----------|----------|
| WALL_EXT_200 | WALL | parametric × 200mm × parametric | Brick |
| SLAB_150 | SLAB | parametric × parametric × 150mm | Concrete |
| WINDOW_STD | WINDOW | 1200 × 200 × 1000mm | Glass |
| DOOR_D1 | DOOR | 900 × 100 × 2100mm | Timber |
| DOOR_D2 | DOOR | 750 × 100 × 2100mm | Timber |
| DOOR_D3 | DOOR | 750 × 100 × 2100mm | PVC |
| ROOF_TILE | ROOF | parametric × parametric × 25mm | Clay |

---

## 8. File Reference

### 8.1 Python Addon Files

| File | What |
|------|------|
| `__init__.py` | Addon registration (bl_info, register/unregister) |
| `client.py` | TCP client to Java server (ndjson protocol, 20 actions) |
| `props.py` | Blender property groups (connection, building, design mode, browse, sliders) |
| `operator.py` | 21 operators (connect, compile, createNew, toggle_mode, focus, snap, save, promote, browse, place_item, add_room, remove_room, add_storey, auto_fix, set_jurisdiction, update_room_dims, verb, etc.) |
| `panel.py` | Panel UI: A.1–A.4, section chooser, BOM Chooser, status strip, dimension sliders, jurisdiction switch, layout editing, click-to-fix |
| `design_bbox.py` | GPU batch renderer for Design Mode bboxes (enable/disable/focus/commit) |
| `db_loader.py` | AABB box loader (output.db → Blender mesh objects) |

Location: `BonsaiBIMDesigner/src/main/python/bonsai_bim_designer/`

### 8.2 Java Server Files (BonsaiBIMDesigner)

| File | What |
|------|------|
| `DesignerServer.java` | TCP socket server (port 9876, ndjson, **34 action dispatch**) |
| `DesignerAPI.java` | Interface: 34 actions + records |
| `DesignerAPIImpl.java` | Implementation: orchestrates DAOs + validators + reports |
| `InferenceEngine.java` | Dependency-ordered rule evaluation, topological sort, proof tree |
| `DesignerDAO.java` | BOM.db + M_Product queries (building types, categories, browse) |
| `WorkOutputDAO.java` | work_output.db persistence (save/recall/listVariants) |
| `AssemblyAPI.java` | Assembly builder interface (G-7) |
| `PlacementValidator.java` | Interface (dual-mode: building + infrastructure) |
| `PlacementValidatorImpl.java` | Implementation (reads disc_validation.db, FacilityType routing) |
| `JsonProtocol.java` | Gson codec for ndjson wire format |

Location: `BonsaiBIMDesigner/src/main/java/com/bim/designer/`

### 8.3 Java Back Office Files (BIMBackOffice)

| File | What |
|------|------|
| `ReportDAO.java` | Interface: 8 report methods (4D-7D + KPI + compliance) |
| `CostDAO.java` | 5D: BOM cost rollup (material + labour + equipment) |
| `ScheduleDAO.java` | 4D: CIDB construction sequence → Gantt tasks |
| `SustainabilityDAO.java` | 6D: Embodied carbon per product × qty |
| `FacilityMgmtDAO.java` | 7D: Asset register + maintenance scheduling |
| `PortfolioDAO.java` | Cross-project: portfolio table, Kanban board, balanced scorecard |
| `ChangelogDAO.java` | Audit trail: per-field change history with undo |
| `PrintConfig.java` | Print configurator: AD_PrintFormat + table chooser |
| `DesignBBox.java` | Shared domain model: 13-field bbox record |

Location: `BIMBackOffice/src/main/java/com/bim/backoffice/`

### 8.3 Database Files

| File | What | Created by |
|------|------|-----------|
| `library/validation.db` | 32 rules, 6 occupancy classes | migration/V001 + V002 |
| `library/DM_BOM.db` | DemoHouse BOM (25 lines) | DemoHouseTest / agent |
| `library/component_library.db` | Product catalog (608 products) | ExtractionPopulator + agent |
| `work_output_{building}.db` | Design persistence (per-building) | WorkOutputDAO.initSchema() |

### 8.4 Test Files

**BonsaiBIMDesigner** (248 tests, 25 test classes):

| Test | Witnesses | What |
|------|-----------|------|
| `DesignerServerTest` | W-DS-1..26 (18) | DAO, API, TCP, createNew, bbox geometry |
| `AssemblyBuilderTest` | W-ASM-1..16 (16) | Layer TACK, U-value, swap, template browse |
| `Tier1Test` | W-AUDIT/CARBON/MAINT (14) | 6D/7D DAOs + changelog audit trail |
| `InferenceEngineTest` | W-INF-DEP/TOPO/CYCLE (12) | Dependency order, SKIP, cycle, approve gate |
| `Schedule5DCostTest` | W-SCHED/COST (11) | 4D Gantt + 5D cost 3-component breakdown |
| `BrowseItemsTest` | W-BROWSE-1..11 (11) | Product search, fit check, pagination |
| `InfraRulesTest` | W-ROAD/RAIL/INFRA (8) | Road layer, rail gauge, infra scoping |
| `InfraUIFilterTest` | W-INFRA-FILTER (7) | FacilityType mode switching, listFacilityTypes |
| `DesignEditingTest` | W-PLACE/LAYOUT (7) | Place item, add/remove room, add storey |
| `WorkOutputDAOTest` | W-WO-DAO (7) | Schema init, save/recall round-trip |
| `PlacementValidatorImplTest` | W-PV/SNAP (11) | MY/US validation, infra snap, terrain snap |
| `HelloWorldJourneyTest` | W-JOURNEY (6) | YAML++ end-to-end: create→snap→save→recall |
| `PortfolioTest` | W-PORTFOLIO (6) | Multi-project, Kanban, balanced scorecard |
| `BridgeRulesTest` | W-BRIDGE (5) | Bridge structural rules, cross-validation |
| `CutFillTerrainSnapTest` | (13) | CutFill, GradingStrategy, terrain-aware snap |

Run: `mvn test -pl BonsaiBIMDesigner` → **248/248 GREEN**

**BIMBackOffice** (5 tests, 1 test class):

| Test | Witnesses | What |
|------|-----------|------|
| `PrintConfigTest` | W-PRINT-1..5 (5) | Discover, defaults, save/load, list, update |

Run: `mvn test -pl BIMBackOffice` → **5/5 GREEN**

**Total: 253 tests across both modules.**

---

## 9. Troubleshooting

### Server won't start
- Check port 9876 is free: `lsof -i :9876`
- Check Java 17+: `java --version`
- Check build: `mvn compile -pl BonsaiBIMDesigner -q`

### Addon not visible in Blender
- Check addon is in `~/.config/blender/3.6/scripts/addons/bonsai_bim_designer/`
- Check `__init__.py` has `bl_info` dict
- Check Blender console for import errors (Window → Toggle System Console)
- Federation addon must also be enabled (provides `BIM_PT_tabs` parent panel)

### Compile returns error
- Check BOM DB exists: `ls library/{PREFIX}_BOM.db`
- Check component_library.db exists: `ls library/component_library.db`
- Check server log for stack trace

### Validation always returns PASS
- Check `validation.db` exists and has rules: `sqlite3 library/validation.db "SELECT COUNT(*) FROM AD_Val_Rule"`
- Check jurisdiction is set (validator must be activated)
- Extracted buildings skip validation by design

### DemoHouse BOM missing
- Regenerate: `mvn test -pl BonsaiBIMDesigner -Dtest="DemoHouseTest"`
- Check: `sqlite3 library/DM_BOM.db "SELECT COUNT(*) FROM m_bom_line"` → should be 25

---

## 10. Assembly Builder (G-7)

Build wall/floor/roof assemblies layer-by-layer with thermal performance:

| Action | What | Wire action |
|--------|------|-------------|
| List templates | Browse pre-defined assembly types (external wall, internal wall, flat roof...) | `listAssemblyTemplates` |
| View detail | See layers with materials, thicknesses, thermal conductivity | `getAssemblyDetail` |
| Swap layer | Replace a material layer (e.g., mineral wool → PIR foam) | `swapLayer` |
| U-value | Automatic BS EN ISO 6946 calculation after each change | included in response |

29 materials with thermal properties seeded in `component_library.db`.

---

## 11. Infrastructure Designer

Design roads, bridges, and railways with terrain-following placement:

| Feature | Status | What |
|---------|--------|------|
| Facility types | DONE | BUILDING / BRIDGE / ROAD / RAILWAY mode switching |
| Infra snap | DONE | Elements snap to terrain Z via contour/straight/blend grading |
| Cut-and-fill | DONE | Volume calculation: cut vs fill for terrain vs design level |
| Terrain context | DONE | 689-point real survey data, AlignmentContext + TerrainSnap |
| Infra rules | DONE | 30 rules: 13 bridge + 10 road + 7 rail |
| BlenderBridge terrain | PLANNED | Wire terrain context to Blender viewport |

---

## 12. Reports (via Back Office)

The Bonsai client calls Back Office report APIs for the active project:

| Wire action | Report | What you get |
|-------------|--------|-------------|
| `constructionSchedule` | 4D Schedule | Gantt tasks with CIDB phase ordering |
| `costBreakdown` | 5D Cost | Material + labour + equipment per discipline |
| `carbonFootprint` | 6D Carbon | Embodied carbon per element, material passport |
| `maintenanceSchedule` | 7D Maintenance | Asset register + replacement intervals |
| `lifecycleCost` | 7D Lifecycle | Whole-life cost with replacement cycles |
| `portfolio` | Portfolio | All projects at a glance (multi-project) |
| `kanban` | Kanban board | Projects by DocStatus (Draft→IP→CO→AP) |
| `balancedScorecard` | Scorecard | Financial / Client / Process / Learning KPIs |

See [BackOfficeUserGuide.md](BackOfficeUserGuide.md) for the full multi-project view.

---

## 13. What's Next

### Beta Readiness Checklist

The engine is proven (253 tests GREEN, 5 Rosetta Stone buildings). The GUI
needs Blender integration testing before beta release.

| Priority | Task | Status | Effort |
|----------|------|--------|--------|
| **1** | **Blender visual test** — install addon, screenshot every panel state | NOT DONE | 1 session |
| **2** | **Wire compile to real pipeline** (createNew → DM_BOM.db → compile → output.db) | NOT DONE | Medium |
| **3** | **Federation integration test** (Design Mode + Full Load after compile) | NOT DONE | 1 session |
| **4** | **Back Office UI** — web dashboard for portfolio/reports | NOT DONE | Multi-session |
| 5 | First-time user walkthrough vs SRS Journey 1 ("3 minutes to first building") | NOT DONE | 1 session |

### Feature Gates

| Gate | What | Status |
|------|------|--------|
| G-1 | BonsaiBIMDesigner module | **DONE** (s15, 14 tests) |
| G-2 | DocValidate + DemoHouse | **DONE** (s16, 43 tests) |
| G-3 | Design Mode + bbox renderer | **DONE** (s17, 44 tests) |
| G-4 | work_output.db Save/Recall | **DONE** (s26, 57 tests) |
| G-5 | BOM Chooser + Place + Inference | **DONE** (s27, 87 tests) |
| G-6 | Compile Bridge (real pipeline) | **DONE** (s29) |
| G-7 | Assembly Builder (MAKE path) | **DONE** (s35, 16 witnesses) |
| G-8 | BlenderBridge pipe (Snap + incremental) | planned |
| G-9 | ORDER View + BOM Outliner | planned |
| G-10 | Promote to BOM (governance gate) | planned |
| G-11 | ParametricMesh UI | planned |
| G-12 | Text Mode (search + NL) | planned |
| G-13 | Click-to-Place (interactive placement) | planned |
| BO-1 | Back Office print configurator | **DONE** (s39d, 5 witnesses) |
| BO-2 | AD_Process report execution queue | planned |
| I-1..4 | Infrastructure Designer | **DONE** (snap, terrain, cut-fill) |
| I-5 | BlenderBridge terrain viewport | planned |

---

*Related docs:
[BIM_Designer.md](BIM_Designer.md) (full spec) |
[BackOfficeUserGuide.md](BackOfficeUserGuide.md) (multi-project ERP guide) |
[BACK_OFFICE_SRS.md](BACK_OFFICE_SRS.md) (Back Office SRS) |
[G4_SRS.md](G4_SRS.md) (work_output.db) |
[ASSEMBLY_BUILDER_SRS.md](ASSEMBLY_BUILDER_SRS.md) (G-7 assembly) |
[INFRA_DESIGNER_SRS.md](INFRA_DESIGNER_SRS.md) (infrastructure) |
[BIM_Designer_SRS.md](BIM_Designer_SRS.md) (UX requirements) |
[CORE_SRS.md](CORE_SRS.md) (scale research, report engine, compliance) |
Federation addon: `/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/`*

---
*v0.8 — 2026-03-20, session 39d. Updated as features are built and tested.*
