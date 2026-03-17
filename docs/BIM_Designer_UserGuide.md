# BIM Designer — User Guide

**Version:** 0.1 (2026-03-18, session 16)
**Status:** Draft — updated each session as features are built and tested.

> This guide covers the BIM Designer addon for Blender (Bonsai), the Java
> DesignerServer, and the validation engine. It is written for developers
> and testers setting up the system for the first time.

---

## 1. What Is BIM Designer?

BIM Designer is Item A of the IfcOpenShell Federation Suite. It adds
**compiler-driven building design** to Blender's Bonsai addon:

- **Compile** existing buildings (YAML + BOM → output.db → 3D viewport)
- **Create New** generative buildings (dialog → BOM → validate → compile)
- **Validate** placements against building codes (UBBL, IRC, NDSS, NCC, BCA)

The addon is a thin Python layer. All logic lives in the Java server.

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
│  │ #4 Clash Detection   │  │   A.3 Create New         │  │
│  │ ...                  │  │   A.4 Verb Console       │  │
│  └─────────────────────┘  └───────────┬──────────────┘  │
│                                       │ TCP 9876         │
└───────────────────────────────────────┼─────────────────┘
                                        │
┌───────────────────────────────────────┼─────────────────┐
│  Java DesignerServer                  │                  │
│  ┌────────────────────────────────────┘                  │
│  │                                                      │
│  │  compile → CompilationPipeline → output.db           │
│  │  createNew → BOM generation → validate → compile     │
│  │  verb → VerbRegistry dispatch                        │
│  │  listBuildings → BuildingRegistry query              │
│  │                                                      │
│  │  PlacementValidator ←── validation.db (32 rules)     │
│  │  BomValidator ←── 9 checks + verb fidelity           │
│  └──────────────────────────────────────────────────────┘
│                                                          │
│  Databases:                                              │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐ │
│  │ component_   │ │ {PREFIX}_    │ │ validation.db    │ │
│  │ library.db   │ │ BOM.db       │ │ (rules)          │ │
│  │ (products)   │ │ (recipes)    │ │                  │ │
│  └──────────────┘ └──────────────┘ └──────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

### Four Databases

| DB | What | Size |
|----|------|------|
| `component_library.db` | Product catalog (meshes, materials, dimensions) | ~500 MB |
| `{PREFIX}_BOM.db` | Assembly recipes (BOM hierarchy, placement offsets) | ~10 MB |
| `validation.db` | Building code rules (UBBL, IRC, NDSS, NCC, BCA) | ~50 KB |
| `output.db` | Compiled result (element positions, R-tree index) | varies |

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
mvn compile -q                    # Compile all modules
mvn test -pl BonsaiBIMDesigner    # Run tests (36 tests, all GREEN)
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
| Building Type | TERRACE, SEMI_D, BUNGALOW | House typology |
| Jurisdiction | MY, US, UK, AU, SG | Which building code applies |
| Site Width | 3000–50000 mm | Total site width |
| Site Depth | 3000–50000 mm | Total site depth |
| Bedrooms | 1–6 | Number of bedrooms |
| Bathrooms | 1–4 | Number of bathrooms |
| Generate | — | Creates BOM → validates → compiles |

**Create New workflow:**
1. Fill in building parameters
2. Click Generate
3. Server generates BOM hierarchy (BUILDING → FLOOR → ROOMS)
4. PlacementValidator checks each room against jurisdiction rules
5. If any room fails (e.g., bedroom < 3000mm for MY), server returns BLOCK
6. If all pass, server compiles → output.db ready for viewing

### 5.5 Verb Console (A.4)

| Field | Description |
|-------|-------------|
| Verb Line | BIM COBOL command (e.g., `CHECK BOM BUILDING_SH`) |
| Execute | Sends verb to server for dispatch |
| Result | Shows verb output |

---

## 6. Validation Rules

The system validates placements against building codes from `validation.db`.
Rules are **data, not code** — adding a jurisdiction = SQL INSERTs.

### 6.1 Supported Jurisdictions

| Code | Country | Standard | Rules |
|------|---------|----------|-------|
| MY | Malaysia | UBBL 2012 | 10 |
| US | USA | IRC 2021 | 6 |
| UK | United Kingdom | NDSS 2015 / Building Regs | 4 |
| AU | Australia | NCC 2022 | 4 |
| SG | Singapore | BCA Approved Document | 3 |
| INTL | International | NFPA 13 (sprinkler spacing) | 5 |

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

| File | Lines | What |
|------|-------|------|
| `__init__.py` | 43 | Addon registration (bl_info, register/unregister) |
| `client.py` | 136 | TCP client to Java server (ndjson protocol) |
| `props.py` | ~80 | Blender property groups (connection, building, Create New) |
| `operator.py` | ~120 | 6 operators (connect, disconnect, list, compile, createNew, verb) |
| `panel.py` | ~100 | Panel UI under BIM_PT_tabs (4 sub-sections) |
| `db_loader.py` | 143 | AABB box loader (output.db → Blender mesh objects) |

Location: `BonsaiBIMDesigner/src/main/python/bonsai_bim_designer/`

### 8.2 Java Server Files

| File | What |
|------|------|
| `DesignerServer.java` | TCP socket server (port 9876, ndjson) |
| `DesignerAPI.java` | Interface: compile, createNew, listBuildings, executeVerb |
| `DesignerAPIImpl.java` | Implementation (delegates to pipeline) |
| `CreateNewRequest.java` | Immutable record for generative requests |
| `CompileRequest.java` | Immutable record for compile requests |
| `CompileResponse.java` | Response with success, elementCount, outputDbPath |
| `PlacementValidator.java` | Interface (OSGi-style, verb-aware) |
| `PlacementValidatorImpl.java` | Implementation (reads validation.db, caches rules) |
| `PlacementRequest.java` | Semantic geometry DTO |
| `ValidationVerdict.java` | PASS / BLOCK / ADJUST result |

Location: `BonsaiBIMDesigner/src/main/java/com/bim/designer/`

### 8.3 Database Files

| File | What | Created by |
|------|------|-----------|
| `library/validation.db` | 32 rules, 6 occupancy classes | migration/V001 + V002 |
| `library/DM_BOM.db` | DemoHouse BOM (25 lines) | DemoHouseTest / agent |
| `library/component_library.db` | Product catalog (+ 7 DemoHouse seeds) | ExtractionPopulator + agent |

### 8.4 Test Files

| Test | Witnesses | What |
|------|-----------|------|
| `DesignerServerTest` | W-DS-1 to W-DS-25 | DAO, API, TCP, createNew |
| `NonDisturbanceTest` | W-ND-1 to W-ND-6 | Mined rules vs source buildings |
| `DemoHouseTest` | W-DH-1 to W-DH-6 | BOM structure, UBBL compliance |
| `PlacementValidatorImplTest` | W-PV-1 to W-PV-5 | MY/US validation, BLOCK/PASS |

Run all: `mvn test -pl BonsaiBIMDesigner` → **36/36 GREEN**

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

## 10. What's Next

| Priority | Task | Status |
|----------|------|--------|
| 1 | Wire createNew to real BOM generation (not stub) | Planned |
| 2 | Connect db_loader.py to Federation's Full Load operator | Planned |
| 3 | BlenderBridge delta updates (incremental viewport) | Planned |
| 4 | Room slider constraints from validation.db | Planned |
| 5 | Multi-storey support in Create New | Planned |
| 6 | MEP auto-routing through generative rooms | Planned |

---

*Related docs:
[BIM_Designer.md](BIM_Designer.md) (full spec, §11 Java module, §13 DemoHouse, §16 Federation integration) |
[DocValidate.md](DocValidate.md) (validation engine, AD_Val_Rule schema) |
[BlenderBridge.md](BlenderBridge.md) (incremental viewport updates) |
Federation addon: `/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/`*

---
*Draft v0.1 — 2026-03-18, session 16. Updated as features are built and tested.*
