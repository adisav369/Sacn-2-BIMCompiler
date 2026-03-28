# BIM Designer — User Guide
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>Set up and use the BIM Designer addon in Blender/Bonsai.</b> Covers the DesignerServer, validation engine, and two-tier architecture with [Back Office](BackOfficeUserGuide.md).
</div>

**Version:** 1.0 (2026-03-23, session 60)
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
- **BOM Drop** — pick a building template, explode BOM tree, navigate/swap/compile (§5.11)
- **Design Mode** — edit draft bboxes with snap, save/recall, and ambient compliance
- **BOM Chooser** — search-first product browser with container fit check
- **Validate** placements against building codes (UBBL, IRC, NDSS, NCC, BCA)
- **Assembly Builder** — layer-by-layer wall/floor construction with U-value calculation
- **Infrastructure** — terrain-following placement for roads, bridges, railways
- **Reports** — 4D schedule, 5D cost, 6D carbon, 7D facility management (via Back Office)

The addon is a thin Python layer. All logic lives in the Java server.
**35 Rosetta Stone buildings** prove the pipeline: SH (55), FK (82), IN (699), DX (1099), TE (48,428 elements). 19 ALL GREEN. 408+ tests GREEN.

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
│  │ #5 Structural Works  │  │     Section Chooser      │  │
│  │ #6 4D Scheduling  ⚡ │  │     BOM Chooser          │  │
│  │ #7 5D Cost Mgmt   ⚡ │  │     Snap/Save/Promote    │  │
│  │ #8 6D/7D DigiTwin ⚡ │  │     Status Strip         │  │
│  │ #9 NLP Query         │  │   A.4 Verb Console       │  │
│  │ #10 Viz Settings     │  │                          │  │
│  │ #11 River Equipment  │  │                          │  │
│  │ #12 PDF Terrain      │  │                          │  │
│  │ #13 BOM Outliner  ✓  │──│   designer_bridge.py     │  │
│  └─────────────────────┘  └───────────┬──────────────┘  │
│  ⚡ = local Python (migration pending)│ TCP 9876         │
│  ✓ = bridged to DesignerServer        │                  │
└───────────────────────────────────────┼─────────────────┘
                                        │
┌───────────────────────────────────────┼─────────────────┐
│  Java DesignerServer                  │                  │
│  ┌────────────────────────────────────┘                  │
│  │                                                      │
│  │  47+ wire actions (compile, createNew, snap, save,    │
│  │  browseItems, placeItem, bomDrop, listOrderLines,    │
│  │  approve, promote, costBreakdown, schedule, ...)     │
│  │                                                      │
│  │  PlacementValidator ←── ERP.db (63 rules)│
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
│  │ component_   │ │ {PREFIX}_    │ │ ERP  │ │
│  │ library.db   │ │ BOM.db       │ │ .db (63 rules)   │ │
│  │ (800 prods)  │ │ (recipes)    │ │                  │ │
│  └──────────────┘ └──────────────┘ └──────────────────┘ │
│  ┌──────────────┐                                       │
│  │ output.db    │  ← Compile DB + audit trail             │
│  │              │    (C_Order, bim_changelog)             │
│  └──────────────┘                                       │
└──────────────────────────────────────────────────────────┘
```

### Four Databases

| DB | What | Size |
|----|------|------|
| `component_library.db` | Product catalog (800 products, meshes, materials, thermal properties) | ~500 MB |
| `{PREFIX}_BOM.db` | Assembly recipes per building (BOM hierarchy, tack offsets, verb patterns) | ~10 MB |
| `ERP.db` | Validation rules (63 rules: residential + infrastructure bridge/road/rail) | ~100 KB |
| `output.db` | Compile DB: compiled result + design persistence (C_Order, C_OrderLine, bim_changelog, R-tree, QTO) | per-building |
| `AD_PrintFormat` | Print configurator (in output.db — which output tables to include) | per-building |

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
mvn test -pl BonsaiBIMDesigner    # Designer tests (408 tests)
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
ls -la library/ERP.db          # Validation rules (32 rules)
ls -la library/DM_BOM.db              # DemoHouse BOM (generative POC)
ls -la library/SH_BOM.db              # SampleHouse BOM (Rosetta Stone)
```

If `ERP.db` is missing:
```bash
sqlite3 library/ERP.db < migration/V001_validation_schema.sql
sqlite3 library/ERP.db < migration/V002_validation_seed.sql
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

The BIM Designer has two panel locations:

1. **Properties → Scene → A. BIM Designer** (standalone addon, panels A.1–A.4)
2. **Properties → Scene → 13. BOM Outliner** (inside Federation addon, S55)

Panel A sits alongside the Federation addon's numbered panels (#1–#12).
The BOM Outliner (#13) is a Federation panel that bridges to the same
DesignerServer via `designer_bridge.py` (TCP NDJSON on port 9876).

**Panel numbering and bridge status:**

| # | Panel | Status |
|---|-------|--------|
| **1D** | BIM Designer (BOM Drop, compile, validate) | Bridged to DesignerServer (44 wire actions) |
| 2D | Import/Export | Pending |
| **3D** | Federation (IFC Extract / IFCtoBOM / Preview / Full Load) | Local Python |
| **4D** | Scheduling | Local Python — migration pending (server has `constructionSchedule`) |
| **5D** | Costing | Local Python — migration pending (server has `costBreakdown`) |
| **6D** | Asset Maintenance | Local Python — migration pending (server has `carbonFootprint`, `maintenanceSchedule`) |
| **7D** | Tandem IoT | Local Python |
| 8 | ERP Reporting | Pending |
| **9** | NLP Query | Local Python |
| **10** | Color Studio | Local Python |

**Future:** Data/control panels (1D, 4D-9) will migrate to a **Web UI** frontend
talking directly to DesignerServer. Bonsai stays as viewport-only (3D, Preview/Full Load).

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

### 5.7 Flywheel Advisories (FL-2)

The **Flywheel Advisory Panel** surfaces findings from the Data Flywheel
(mined dimension ranges and building profiles) directly in the Design Mode UI.

| Severity | Icon | Meaning |
|----------|------|---------|
| INFO | Blue info | Observation from mined data ("8 IFC classes detected") |
| WARNING | Amber error | Element dimension outside observed range |
| SUGGESTION | Green light | System can suggest a correction — click element to review |

**Three advisory layers:**

| Layer | Source | Example |
|-------|--------|---------|
| DIMENSION | DimensionRangeValidator (DV010, 415 rules from 20 buildings) | "IfcWall W=95mm outside typical [800–10269]mm" |
| PROFILE | BuildingProfileValidator (DV011, 36 building profiles) | "NOVEL_CLASS: IfcZzz never seen in 36 buildings" |
| SHAPE | ShapeAdvisoryWriter (FL-5/EYES) | "IfcWall is COMPACT — shaped like furniture, not a wall" |

**Usage:**
1. Click **Refresh** in the Flywheel Advisories section (or advisories auto-load after compilation).
2. Review colour-coded list. Click an element-level advisory to focus it in the viewport.
3. SUGGESTION advisories include a recommended value — review and apply as needed.

**Wire protocol:** `{"action":"listAdvisories","buildingId":"..."}` → `ListAdvisoriesResponse`

### 5.8 Save / Recall / Promote

| Action | Wire protocol | What it does |
|--------|--------------|-------------|
| Save | `save` | Stores current bboxes to `output.db` (compile DB) as C_Order + C_OrderLine. Persists to .blend file. Cheap, frequent. |
| Recall | `recall` | Restores a previous variant. Non-destructive — originals never overwritten. |
| List Variants | `listVariants` | Shows all saved variants (most recent first) with label and line count. |
| Approve | `approve` | Compliance gate — validates all rooms, checks dangles, transitions master C_Order IP → AP. |
| Promote | `promote` | Governance gate — creates m_bom + m_bom_line in BOM.db. Requires AP status. Freezes master C_Order AP → CO. |

**Save creates:**
- Sub-C_Order (child of master order for the building)
- C_OrderLine per bbox (with tack dx/dy/dz in mm)
- W_Variant pointer (label, is_active, line count)

**Round-trip fidelity:** Save stores mm, recall restores mm. No precision loss.

**Promote flow (G-10):**
1. Click **Approve** — validates placement rules, checks for dangling product references. If all pass, master C_Order transitions IP → AP.
2. Click **Promote to BOM** — requires AP status (button greyed out otherwise).
   - Runs dangle check on ITEM bboxes. If unresolved products exist, returns the dangle list and blocks.
   - Writes `m_bom` rows (entity_type='U', provenance='GENERATIVE') for each BUILDING/FLOOR/ROOM.
   - Writes `m_bom_line` rows with parent-relative offsets and AABB dims.
   - Freezes master C_Order AP → CO. Design mode is now read-only for this building.
3. Promoted BOM entries are immediately available as templates for new buildings.

### 5.9 BOM Chooser — Search-First Product Browser (§17.18)

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

### 5.10 Verb Console (A.4)

| Field | Description |
|-------|-------------|
| Verb Line | BIM COBOL command (e.g., `CHECK BOM BUILDING_SH`) |
| Execute | Sends verb to server for dispatch |
| Result | Shows verb output |

---

## 6. Validation Rules

The system validates placements against building codes from `ERP.db`.
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
  → Loads 10 UBBL rules from ERP.db

User creates BEDROOM 2800×3500mm
  → PlacementRequest(productCategory="BEDROOM", widthMm=2800, depthMm=3500, ...)
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
| `WorkOutputDAO.java` | output.db (compile DB) persistence (save/recall/listVariants) |
| `AssemblyAPI.java` | Assembly builder interface (G-7) |
| `PlacementValidator.java` | Interface (dual-mode: building + infrastructure) |
| `PlacementValidatorImpl.java` | Implementation (reads ERP.db, FacilityType routing) |
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
| `library/ERP.db` | 32 rules, 6 occupancy classes | migration/V001 + V002 |
| `library/DM_BOM.db` | DemoHouse BOM (25 lines) | DemoHouseTest / agent |
| `library/component_library.db` | Product catalog (608 products) | ExtractionPopulator + agent |
| `output.db` | Compile DB + design persistence (per-building) | WorkOutputDAO.initSchema() |

### 8.4 Test Files

**BonsaiBIMDesigner** (408 tests, 42 test classes):

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
- Check `ERP.db` exists and has rules: `sqlite3 library/ERP.db "SELECT COUNT(*) FROM AD_Val_Rule"`
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

## 13. Web UI Frontend (S56, updated S60)

### Architecture Split

Blender's sidebar is too constrained for data-heavy workflows (BOM trees, Excel-like
grids, product choosers). S55 concluded: **Web UI for data/control, Bonsai for viewport only.**

```
┌──────────────────────────────────────────────────────────────────┐
│  Web Browser (HTML/JS)        http://localhost:9878               │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ Header: [BIM Designer] [Building ▼] [Launch Bonsai] [Search]││
│  │ Tab bar: 1D Order │ 2D Spatial │ 3D Geometry │ 4D │ 5D │ ...││
│  ├──────────────────────────────────────────────────────────────┤│
│  │  (active tab: BOM tree, order lines, validation, data grid) ││
│  ├──────────────────────────────────────────────────────────────┤│
│  │ Footer: [BIM Designer] [building] [compliance] [API] [v1.0] ││
│  └──────────────────────────────────────────────────────────────┘│
│         │ HTTP POST /api                                         │
│         │ SSE /events (server → browser push)                    │
└─────────┼────────────────────────────────────────────────────────┘
          │
┌─────────┼────────────────────────────────────────────────────────┐
│  WebUIServer (port 9878)        Java DesignerServer (port 9876)  │
│         │ dispatch to DesignerServer + scanLibrary/pollCommands   │
│         │                                                        │
│         ├──→ BOM Drop: bomDrop → C_Order + C_OrderLine tree      │
│         ├──→ Save:     prepareIt → validate, persist output.db   │
│         ├──→ Approve:  compliance gate IP→AP                     │
│         ├──→ Complete:  completeIt → compile → output.db         │
│         ├──→ Compile:   compile → output.db + push COMPLETE      │
│         │                          │                              │
└─────────┼──────────────────────────┼─────────────────────────────┘
          │ TCP 9876 (ndjson)        │ pollCommands (HTTP)
┌─────────┼──────────────────────────┼─────────────────────────────┐
│  Blender (Bonsai) — viewport only  ▼                              │
│  30 operators → client.py → DesignerServer                        │
│  Poll timer (2s) picks up loadOutput/applyScheme from Web UI      │
│  db_loader renders compiled output.db into viewport               │
└───────────────────────────────────────────────────────────────────┘
```

### Tab Layout (§30.3 — 10 nD panels, aligned S60)

| Tab | Name | Content | Status |
|-----|------|---------|--------|
| **1D** | **Order** | BOM Drop tree + Order Lines (editable) + ASI panel + DocAction (Save/Approve/Complete/Promote) | **LIVE** |
| **2D** | **Spatial** | Storey browser + containment tree + Import/Export (IFC import, BOM/Order/IFC export) | Storey: wired. Import: stub |
| **3D** | **Geometry** | Federation status + building list + Preview/Full Load (Bonsai push) + Compile | **LIVE** |
| **4D** | **Schedule** | Construction schedule — discipline phases, dependencies, duration | Placeholder (ScheduleDAO) |
| **5D** | **Cost** | Material + labour + equipment breakdown with totals | Placeholder (CostDAO) |
| **6D** | **Sustain** | Carbon footprint — CO2e, mass, intensity per element | Placeholder (SustainabilityDAO) |
| **7D** | **Facility** | Maintenance schedule — asset lifecycle, intervals, recurring costs | Placeholder (FacilityMgmtDAO) |
| **8** | **Validate** | Compliance summary cards + advisory table + portfolio + balanced scorecard | Advisories: wired. Portfolio: stub |
| **9** | **BOM** | BOM Outliner (mirrors 1D tree) + NLP Query (10 presets) + Product Catalog Search | **LIVE** |
| **10** | **Colour** | Bonsai selection sync (SSE) + 4 palettes (realistic/phase/safety/discipline) + apply to Bonsai | **LIVE** |

### 1D Order Tab — DocAction Flow (iDempiere pattern)

| Step | Button | Action | What happens |
|------|--------|--------|-------------|
| 1 | **BOM Drop** | `bomDrop` | Pick template → explode BOM → C_Order + C_OrderLine tree. Tree renders in left pane. |
| 2 | **Save** | `save` | Persist bboxes to output.db (compile DB) + .blend file. Status: IP (In Progress). |
| 3 | **Approve** | `approve` | Compliance validation gate. Shows blockers/dangles. Status: IP→AP. |
| 4 | **Complete** | `prepareIt` + `completeIt` | Validate → compile → output.db. Status: CO. Push to Bonsai. |
| 5 | **Promote** | `promote` | Write M_BOM entries to BOM.db. Governance gate (requires AP). |

### Show in Bonsai — Dual-Screen Workflow (S60)

After BOM Drop, the **"Show in Bonsai"** button pushes lightweight wireframe bboxes to the
active Bonsai viewport. No compile — instant preview. Designed for dual-monitor setups:
left screen = Web UI (BOM configurator, order lines, ASI), right screen = Bonsai viewport.

```
Web UI (Screen 1)                     Bonsai (Screen 2)
┌──────────────────────┐              ┌──────────────────────┐
│ [BOM Drop] → tree    │              │  ┌──┐               │
│ [Show in Bonsai] ────┼─ bboxes ───→│  │  │ ┌──┐ wireframe│
│ [Save] [Approve]     │  ~2s poll    │  └──┘ │  │ preview  │
│ Order Lines table    │              │       └──┘          │
│                      │              │  [Full Load] for geo │
└──────────────────────┘              └──────────────────────┘
```

**Lightweight preview flow (no compile) — PROVEN S60:**
1. User clicks "Show in Bonsai" → sends `applyScheme(schemeName:'previewBBoxes', guid:buildingId)`
2. Java WebUIServer queues command (whitelisted fields: schemeName, objectName, guid)
3. Bonsai poll timer (0.5s) picks up command via `pollCommands`
4. `_apply_scheme_command` checks `schemeName == 'previewBBoxes'` → routes to handler
5. Handler calls `listOrderLines(buildingId)` via HTTP → gets W/D/H per line
6. Converts to bboxes → creates wireframe cubes (`display_type='WIRE'`) in viewport
7. Category-coloured wireframe boxes appear in `BIM_Preview_BBoxes` collection

**Proven:** 257 wireframe cubes (MO building) rendered in Bonsai viewport from Web UI BOM Drop.
Screenshot: `~/Pictures/Screenshots/Screenshot from 2026-03-23 06-15-37.png`

**Known limitation — geometry at origin:** All bboxes currently stack at (0,0,0) because
BOM Drop order lines have `dx=dy=dz=0` — offsets are computed by the compiler during
spatial placement, not at BOM Drop time. Fix: populate `dx/dy/dz` from the BOM tree's
tack offsets during BOM Drop, or run a lightweight placement pass before preview.

**Full geometry when ready:**
- User clicks **Full Load** in Bonsai panel → loads compiled output.db with meshes
- Or clicks **Complete** in Web UI → compiles + auto-pushes full geometry

**Why wireframe first:**
- Instant feedback — no compile wait (0s vs 2-4s for DemoHouse, minutes for large buildings)
- Same visual language as Design Mode (§17.3) — category colours, grey context, vivid focus
- Edits in Web UI (add room, swap product, change dimensions) push updated bboxes immediately
- Full geometry only needed for final review, not during configuration

**Industry precedent — browser-to-viewport push:**

| Product | Web→Viewport | Preview? | Cloud? |
|---------|-------------|----------|--------|
| **Autodesk Forma** | Web massing → Revit via cloud bridge | Yes (massing boxes) | Yes |
| **Hypar** | Web configurator → Rhino/Revit plugin | Yes (parametric preview) | Yes |
| **Arkio** | VR/web → Revit/ArchiCAD live link | Yes (low-poly sync) | Yes |
| **Rhino.Compute / ShapeDiver** | Web → server-side Grasshopper → viewport | Mesh only | Yes |
| **Trimble Connect** | Web dashboard → SketchUp/Tekla | Model sync | Yes |
| **Finch3D** | AI optimizer (web) → Revit | Floor plan preview | Yes |
| **BIM Designer** | Web UI → local TCP poll → Bonsai | **Wireframe bbox** | **No** (offline) |

None of these projects use Bonsai/Blender as their viewport. BIM Designer is the only
open-source implementation of this pattern, and the only one that works entirely offline.
The local TCP + HTTP poll architecture has zero cloud dependency — the same dual-screen
experience as Forma or Hypar, but running on `localhost`.

### Complete → Bonsai Bridge

Same mechanism as "Show in Bonsai" but triggered by the Complete button:
1. Browser sends `completeIt` → WebUIServer → DesignerServer
2. Server compiles, writes output.db, returns `{elementCount, compileTimeMs}`
3. WebUIServer SSE-broadcasts `compileComplete` to browser
4. WebUIServer queues `loadOutput` command with `outputDbPath`
5. Bonsai poll timer (2s) calls `pollCommands()` → receives `loadOutput`
6. `db_loader.load_from_db(outputDbPath)` → geometry appears in Blender viewport

### Bidirectional Sync — Bonsai ↔ Web UI (S57)

| Direction | Mechanism | Events |
|-----------|-----------|--------|
| Server → Browser | SSE `/events` | `selectionChanged`, `compileComplete`, `schemeApplied` |
| Browser → Server | `POST /api` | All 47+ actions via `send(action, params)` |
| Browser → Bonsai | `POST /api applyScheme` | Queue command → Bonsai polls via `pollCommands` |
| Bonsai → Server | TCP ndjson (port 9876) | 30 operators via `client.py._send()` |
| Bonsai → Browser | TCP push | `COMPILE_COMPLETE` → SSE relay to browser |

### Tech Stack

- **Static HTML/JS** — vanilla JS, no framework. SPA with tab routing (`#tab=4d`)
- **HTTP POST** — `fetch()` API, no WebSocket dependency
- **SSE** — Server-Sent Events for real-time push (selection, compile, scheme)
- **CSS Grid + Flexbox** — responsive dark theme (#1a1a2e bg, #e94560 accent)
- **`<details>/<summary>`** — BOM tree with expand/collapse
- **Split pane** — BOM tree (left) + ASI panel (right) in 1D tab

### Implementation

| File | Path | Purpose |
|------|------|---------|
| WebUIServer.java | `BonsaiBIMDesigner/.../api/` | JDK HttpServer — static files + `POST /api` + `GET /events` SSE |
| index.html | `webui/` | SPA shell — 10 tabs (§30.3 aligned), header search, status bar |
| app.js | `webui/` | 30+ functions, `fetch()` API, SSE listener, 4 palettes, BOM tree renderer |
| style.css | `webui/` | Dark theme, cards, data tables, BOM tree, split pane, sync indicators |

**Two HTTP endpoints:**
- `GET /*` → static files from `webui/`
- `POST /api` → JSON dispatch → `DesignerServer.dispatch()` + `scanLibrary` + `pollCommands`
- `GET /events` → SSE stream (selectionChanged, compileComplete, schemeApplied)

**Standalone operation:** `./scripts/run_webui.sh` starts the server. No Bonsai needed.
If port 9878 is already in use (stale instance), the script warns and exits — no duplicate
server, no extra browser tab. Browser opens only after server confirms startup.
User opens http://localhost:9878, selects a building, and works with BOM/schedule/cost data.
Server auto-shuts down after 4 hours idle (no API requests) to prevent stale instances.

**Global search:** Header search bar routes to NLP Query (questions) or Product Catalog (keywords).

**Bonsai integration:** All 10 Bonsai panels have "Open in Web UI" button (`BIM_OT_launch_web_ui`
→ `webbrowser.open("http://localhost:9878/#tab=4d")`). Poll timer syncs compile output + colour schemes.

**Witnesses:** W-WS-1 (scanLibrary API), W-WS-2 (listBuildings dispatch), W-WS-3 (static HTML).
DesignerServerTest: 21/21 GREEN. SRS: BIM_Designer_SRS.md §29. Spec: S60_UI_ALIGNMENT_SPEC.md.

### Bonsai 10-Item Client Flow (verified S60)

All 30 operators in `operator.py` → `client.py` → DesignerServer. Core 10:

| # | Operation | Client Method | Operator |
|---|-----------|--------------|----------|
| 1 | Create New | `create_new()` | `BIM_OT_designer_create_new` |
| 2 | Snap/Validate | `snap()` | `BIM_OT_designer_snap` |
| 3 | Save | `save()` | `BIM_OT_designer_save` |
| 4 | Order Lines | `list_order_lines()` | `BIM_OT_designer_list_order_lines` |
| 5 | BOM Tree | `get_bom_tree()` | `BIM_OT_designer_get_bom_tree` |
| 6 | BOM Drop | `bom_drop()` | `BIM_OT_designer_bom_drop` |
| 7 | Approve | `approve()` | `BIM_OT_designer_approve` |
| 8 | Promote | `promote()` | `BIM_OT_designer_promote` |
| 9 | Compile | `compile()` | `BIM_OT_designer_compile` |
| 10 | Sync | `poll_commands()` | `_poll_commands_timer()` (2s) |

Full verification table: [S60_UI_ALIGNMENT_SPEC.md](archive/S60_UI_ALIGNMENT_SPEC.md) §4.

### Federation Module Gap (S60 finding)

The IfcOpenShell federation module (`/home/red1/IfcOpenShell/.../federation/`) implements
**70+ operators** including clash detection (28 ops), 4D schedule generation, 5D BOQ,
6D digital twin, 7D IoT, and BCF export. These are currently Python-local in Bonsai.

**HIGH priority items to migrate to Java backend** (enabling Web UI access):
- Clash detection → Tab 8 Validate
- Schedule generation → Tab 4D
- BOQ generation → Tab 5D
- BCF export → Tab 8 Validate
- Asset import → Tab 6D/7D

Full gap analysis: [S60_UI_ALIGNMENT_SPEC.md](archive/S60_UI_ALIGNMENT_SPEC.md) §3.

### Why Not Blender?

- Blender's `invoke_popup()` max ~600px, no `template_list` in popups, no round buttons
- Properties sidebar is 300px — too narrow for BOM trees and data grids
- Bonsai doesn't block this — Blender's own UI toolkit is the limit
- Web UI gives: round buttons, Excel grids, drag-and-drop trees, responsive layout, keyboard shortcuts

---

## 14. What's Next

### Beta Readiness Checklist

The engine is proven (408+ tests GREEN, 35 Rosetta Stone buildings, 19 ALL GREEN).
The Work Order path compiles end-to-end (W-WO-1). ERP alignment complete (S60–S79).

| Priority | Task | Status | Effort |
|----------|------|--------|--------|
| **1** | **S60 ERP Model Alignment** — compiler walks C_OrderLine, not C_DocType | **DONE** (S60–S79) | [S60_ERP_ALIGNMENT.md](archive/S60_ERP_ALIGNMENT.md) |
| **2** | **Wire 4D-7D tabs to real DAO data** — ScheduleDAO/CostDAO/etc. already exist | NOT DONE | 1 session |
| **3** | **Migrate federation operators to Java** — clash detection, BOQ, schedule | NOT DONE | Multi-session |
| **4** | **DemoHouse UAT** — §30.4 end-to-end from either surface | NOT DONE | 1 session |
| 5 | First-time user walkthrough vs SRS Journey 1 ("3 minutes to first building") | NOT DONE | 1 session |

### Feature Gates

| Gate | What | Status |
|------|------|--------|
| G-1 | BonsaiBIMDesigner module | **DONE** (s15, 14 tests) |
| G-2 | DocValidate + DemoHouse | **DONE** (s16, 43 tests) |
| G-3 | Design Mode + bbox renderer | **DONE** (s17, 44 tests) |
| G-4 | output.db Save/Recall | **DONE** (s26, 57 tests) |
| G-5 | BOM Chooser + Place + Inference | **DONE** (s27, 87 tests) |
| G-6 | Compile Bridge (real pipeline) | **DONE** (s29) |
| G-7 | Assembly Builder (MAKE path) | **DONE** (s35, 16 witnesses) |
| G-8 | Click-to-Place (interactive placement) | **DONE** (s45, 15 witnesses) |
| G-9 | ORDER View + BOM Outliner | **DONE** (s46, 7 witnesses) |
| G-10 | Promote to BOM (governance gate) | wired (promote API) |
| G-11 | Web UI Frontend (10 tabs) | **DONE** (s56, 21 tests) |
| G-12 | BOM Drop + Work Order path | **DONE** (s59, W-WO-1 6/6 GREEN) |
| G-13 | Bidirectional Sync (Bonsai ↔ HTML) | **DONE** (s57, SSE + pollCommands) |
| G-14 | ERP Model Alignment (C_OrderLine path) | **DONE** (S60–S79) |
| BO-1 | Back Office print configurator | **DONE** (s39d, 5 witnesses) |
| BO-2 | BIMEyes advisory + EYES integration | **DONE** (s50, 28 proofs) |
| I-1..4 | Infrastructure Designer | **DONE** (snap, terrain, cut-fill) |

---

*Related docs:
[BIM_Designer.md](BIM_Designer.md) (full spec) |
[BackOfficeUserGuide.md](BackOfficeUserGuide.md) (multi-project ERP guide) |
[BACK_OFFICE_SRS.md](BACK_OFFICE_SRS.md) (Back Office SRS) |
[G4_SRS.md](G4_SRS.md) (output.db, compile DB) |
[ASSEMBLY_BUILDER_SRS.md](ASSEMBLY_BUILDER_SRS.md) (G-7 assembly) |
[INFRA_DESIGNER_SRS.md](INFRA_DESIGNER_SRS.md) (infrastructure) |
[BIM_Designer_SRS.md](BIM_Designer_SRS.md) (UX requirements) |
[ACTION_ROADMAP.md](ACTION_ROADMAP.md) (scale research, report engine, compliance) |
[S60_ERP_ALIGNMENT.md](archive/S60_ERP_ALIGNMENT.md) (ERP model alignment) |
[S60_UI_ALIGNMENT_SPEC.md](archive/S60_UI_ALIGNMENT_SPEC.md) (UI + federation gap analysis) |
Federation addon: `/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/`*

---
*v1.0 — 2026-03-23, session 60. Tabs aligned to §30.3, BOM tree fixed, Bonsai 10-item flow verified, federation gap analysis completed.*
