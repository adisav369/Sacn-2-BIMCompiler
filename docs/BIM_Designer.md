# BIM Designer — GUI Architecture from Existing Infrastructure

**Version:** 1.1 (2026-03-17)
**Depends on:** [BOMBasedCompilation.md](BOMBasedCompilation.md), [ConstructionAsERP.md](ConstructionAsERP.md), [BIM_COBOL.md](BIM_COBOL.md)

> The compiler already knows how to build. The GUI is a parameter chooser that
> triggers compilation and shows the result. Every concept the designer needs
> already exists in the codebase — tack convention, BOM selection cascade,
> CO_EmptySpace slots, BIM COBOL verbs, EntityType governance.

---

## Phase G Preamble — Proven Artifacts the Designer Builds On

The BIM Designer (Phase G) does not start from scratch. Three Rosetta Stone
buildings — SH (55), DX (1,099), TE (48,428 elements) — have proven the
full pipeline end-to-end: IFC extraction → component library → BOM dictionary
→ 9-stage compilation → verified output. All gates GREEN (G1–G6).

The designer works on these **already-proven artifacts:**

| Artifact | What it is | Where | Designer touches it |
|----------|-----------|-------|-------------------|
| `classify_*.yaml` | Building identity + storey/discipline map | `IFCtoBOM/src/main/resources/` | Drafts new building definitions |
| `{PREFIX}_BOM.db` | Spatial arrangement — m_bom + m_bom_line | `library/` | Sets attributes, quantities, tack offsets |
| `component_library.db` | Product catalog + geometry meshes | `library/` | Snaps correct geometry to products |
| `dsl_*.bim` | Building definition language | `IFCtoBOM/src/main/resources/` | Grid, rooms, openings, construction system |
| `*.bimcobol` | Verb scripts | `DAGCompiler/lib/input/` | Post-compile actions (route, wire, place) |
| Output `.db` | Compiled spatial DB | `DAGCompiler/lib/output/` | Read-only — Bonsai renders this |

**The two-DB split is critical:** The designer edits the BOM (spatial
arrangement) and the library (product catalog) — never the output. The output
is always a fresh compilation from those sources. This is the same separation
as ERP: you edit the Bill of Materials and the Product Master, not the
finished goods inventory.

**BOM vs output — the instancing boundary:** The BOM is a recipe, not an
inventory. It should contain **unique product models with quantities and
pattern rules** — the compiler then expands those into placed instances in
the output DB. The correct split:

```
BOM (recipe):     505 products × pattern formulas  →  ~200 lines
                  "20 plates at 600mm spacing along roof grid"

Output (result):  48,428 placed element instances
                  each with world-coordinate position + library geometry
```

The current TE BOM has 48,428 unfactored placement rows — one per element
with its own dx/dy/dz. This proved the pipeline round-trip (7/7 GREEN) but
conflates BOM recipe with compiled output. A proper factored BOM compresses
repeating patterns into formulas with quantities; the compiler expands them
to output instances. This factorization (TE-6 TILE SURFACE compression: 34K
ARC plates → ~20 formulas) is a prerequisite for the designer — you edit
pattern rules, not 48K individual placements. See
[ConstructionAsERP.md](ConstructionAsERP.md) §11 for the BOM dimension model.

### Federation Menu — Item 0: Compile / Item 1: Preview / Item 2: Create New

The Bonsai addon's Federation menu has three entry points:

| Item | Action | Data source | Use case |
|------|--------|-------------|----------|
| **0. Compile** | Runs 9-stage pipeline → fresh output.db | YAML + BOM DB + component library | Design iteration: edit → compile → view |
| **1. Preview / Full Load** | Loads extracted reference DB directly | `*_extracted.db` (raw IFC data) | Inspection: view the reference as-is |
| **2. Create New** | Spawns settings dialog → generates BOM → compiles | User choices + component library | Generative: design a new building from scratch |

Item 0 is the **designer path** — it produces output.db from source artifacts.
Item 1 is the **review path** — it loads existing IFC extraction for comparison.
Item 2 is the **generative path** — it creates source artifacts from user intent.
The three never mix data sources.

### Item 2: Create New — The Generative Entry Point

"Create New" spawns a settings dialog that collects the minimum inputs needed
to generate a building from scratch. No IFC file, no extraction — pure intent.

**Dialog fields (iDempiere DocType + C_Order pattern):**

| Field | Source | iDempiere analogy |
|-------|--------|-------------------|
| Building name | User input | C_Order.DocumentNo |
| Building type | Dropdown from C_DocType (DocSubType) | C_DocType selector |
| Jurisdiction | Dropdown: MY, US, UK, AU, SG | C_Order.C_Country_ID |
| Rooms | Checklist: LIVING, KITCHEN, BEDROOM×n, BATHROOM×n | C_OrderLine product selection |
| Site dimensions | Width × depth (mm) | AABB envelope |
| Storeys | Count: 1, 2, 3 | BOM tree depth |

**What happens on "Create":**

```
1. Generate classify_*.yaml (building identity + storey map)
2. Generate C_DocType entry (Provenance='GENERATIVE')
3. Generate m_bom hierarchy (BUILDING → FLOOR → ROOM)
4. DocValidate fires: check each room against AD_Val_Rule for jurisdiction
   → BLOCK if bedroom < 3000mm (UBBL), < 2134mm (IRC), etc.
5. CompilationPipeline.run() → output.db
6. Bonsai reloads viewport → user sees their building
```

**Building codes drive the choosers.** The jurisdiction selection activates
the matching AD_Val_Rule set ([DocValidate.md](DocValidate.md) §11). This
constrains slider ranges (bedroom min = code minimum, max = site envelope),
filters product compatibility (door min width = 750mm MY / 813mm US), and
sets ceiling heights. The codes are not a post-hoc check — they are the
component chooser data.

| Jurisdiction | Bedroom min dim | Ceiling height | Door min width |
|-------------|----------------|----------------|----------------|
| MY (UBBL) | 3000mm | 2600mm | 750mm |
| US (IRC) | 2134mm (7 ft) | 2134mm (7 ft) | 813mm (32") |
| UK (NDSS) | 2150mm | 2300mm | 750mm |
| AU (NCC) | — | 2400mm | 820mm |
| SG (BCA) | — | 2400mm | 850mm |

### WYSIWYG Editing — Move in Output Space, Write to Source Artifacts

The user's experience is WYSIWYG: they move elements in the 3D viewport
(output.db space) and see the result immediately. But the actual writes go
to the **source artifacts**, not to output.db — because output.db is always
recompiled from sources.

```
User drags element in Bonsai viewport (output.db coordinates)
  │
  ├─ Addon detects: which BOM line? which product? which storey?
  │  (reverse lookup: output GUID → element_ref → m_bom_line.bom_child_id)
  │
  ├─ Writes to source artifact:
  │  ├─ YAML change?      → classify_*.yaml (storey, discipline scope)
  │  ├─ BOM change?       → m_bom_line dx/dy/dz in {PREFIX}_BOM.db
  │  ├─ Attribute change?  → M_AttributeSetInstance (per-instance params)
  │  └─ Rule override?    → C_OrderLine ASI (guarded by Val_Rule)
  │
  ├─ Recompile (9-stage pipeline)
  │
  └─ Bonsai reloads output.db → viewport updates
```

**Guard rails:** Every write to a source artifact is validated by the same
rules that guard compilation:
- **Val_Rule** on C_OrderLine — fire protection spacing, structural clearance
- **EntityType** guard — D (dictionary) vs U (user) vs A (application)
- **BomValidator** QA — runs before BOM commit, rejects broken data

The user moves freely in output space. The system translates their intent
into source artifact writes. If a move violates a rule, the compiler flags
it at recompile — the user sees the violation in the viewport, not in a log.

---

## 1. Architecture: Compiler-Driven Editing

The GUI does not create geometry. The compiler does. The GUI edits BOM
parameters and triggers batch compilation — the same way iDempiere processes
a document: Draft → Process → Complete.

```
┌─ Bonsai (Blender) ─────────────────────────────────┐
│                                                      │
│  Viewport ←── reads ←── FederatedModel spatial DB ─┐ │
│     ↑                         ↑                    │ │
│  refresh                   writes                  │ │
│     │                         │                    │ │
│  Addon Panel ──→ subprocess ──→ Java Compiler      │ │
│  (Python)                       (9-stage pipeline) │ │
│                                                    │ │
│  choosers/sliders → BIM COBOL verbs → compile → DB │ │
│                                                    │ │
│  witness.json ← read ← compliance status display   │ │
└────────────────────────────────────────────────────┘
```

**Data flow:** No file handoff during editing. The compiler writes to the
spatial DB. Bonsai reads it. IFC export is a final step for permit submission.

**Batch model:** The user edits parameters, clicks "Process" (iDempiere
DocAction pattern). The 9-stage pipeline runs end-to-end. Results appear in
the viewport. No keystroke-triggered recompile.

**What Bonsai provides free:** LOD400 3D rendering, section cuts, element
selection, property inspection, dimensioning, Blender's addon panel framework.
The only addition is a thin Python addon with chooser panels and subprocess
compiler invocation.

---

## 2. Existing Concepts That Enable the Designer

Every concept below is already implemented and tested. The GUI wires them
together — it does not invent new abstractions.

### 2.1 Tack Convention → Drag-and-Drop Placement

The tack convention ([BOMBasedCompilation.md §4](BOMBasedCompilation.md))
makes placement purely algebraic:

- **Left-Front-Down corner = (0,0,0)** in every BOM's coordinate frame
- **dx/dy/dz always >= 0** (enforced at schema level by `X_M_BOMLine.setDx()`)
- **tack_to/tack_from** = Lego-style connection points
- **rotation_rule** on m_bom_line handles orientation

GUI actions map directly to BOM operations:

| GUI action | BOM operation |
|-----------|--------------|
| Drag BOM into room | `ADD LINE` — dx/dy/dz = drop position |
| Slide along wall | Update dx or dy (constrained axis) |
| Rotate 90° | Update rotation_rule |
| Fill gaps | Insert BUFFER PHANTOM lines |
| "Save as BOM" | `CREATE BOM` + `ADD LINE` per child |

The tack handshake is uniform at every level: building on site, storey in
building, room in storey, furniture in room. Same snap/slide/rotate interaction
at every zoom level.

### 2.2 BIM COBOL Verbs → Editor Action Vocabulary

The GUI emits BIM COBOL verbs ([BIM_COBOL.md](BIM_COBOL.md)), never direct
SQL. 38 verbs in 5 tiers provide the editor's action vocabulary. The GUI is
the *editor*. BIM COBOL is the *source code*. The compiler is the *build
system*. The user never sees BIM COBOL directly — just as most programmers
never see assembler.

**Verb-to-GUI action mapping** (BIM_COBOL.md §18.12):

| User Action in Bonsai | Verb Emitted | Level |
|---|---|---|
| Draw building envelope | `EXTRACT AABB` + `SNAP TO GRID` | Util |
| "New Building" wizard | `COMPOSE BUILDING` | 3 |
| Drag room into floor | `ADD ROOM` | 2 |
| Resize room handle | `RESIZE ROOM` | 1 |
| Drop furniture into room | `FURNISH ROOM` | 1 |
| Remove room | `REMOVE ROOM` | 2 |
| Mirror/rotate unit toggle | `SET ROTATION` (primitive) | 0 |
| Move element | `SET TACK` (primitive) | 0 |
| Create empty room | `CREATE ROOM ... EMPTY` | 1 |
| Clear room contents | `STRIP ROOM` | 1 |
| Check room fits | `VALIDATE AABB` | Util |
| Preview floor layout | `PARTITION AABB` | Util |
| Create variant | `VARY BUILDING` | 5 |
| Clone a design | `CLONE BOM` | Data |

**Layered composition:** L1 convenience verbs call P0 primitives. L2
(floor-level) will call L1. The GUI always targets the highest available verb
tier. Each verb produces an audit trail via PP_Order_Node.

**Verb chaining** (BIM_COBOL.md §18.11) — the output payload of one verb
feeds as input to the next. This is how the GUI builds a building
interactively:

```
Step 1: User draws a box in Bonsai
  EXTRACT AABB FROM POINTS (0,0,0) (12.0,10.0,6.0)
    → AabbPayload: WIDTH 12000 DEPTH 10000 HEIGHT 6000

Step 2: Snap to structural grid
  SNAP TO GRID AABB 12000 10000 SPACING 1000
    → GridSnapPayload: WIDTH 12000 DEPTH 10000

Step 3: Preview the spatial partition
  PARTITION AABB 12000 10000 3000 INTO ROOMS LI DN KT BD BT
    → PartitionPayload: 5 slots with positions
    -- GUI shows the partition overlay on the drawn box
    -- User approves or adjusts room positions

Step 4: Compose the building
  COMPOSE BUILDING RESIDENTIAL 12000 10000 6000 UNITS 2
    → ComposeBuildingPayload: 12 BOMs, 47 lines

Step 5: User clicks on kitchen, drags to resize
  RESIZE ROOM KITCHEN_3500x2500 TO 4000 3000 2800
    → ResizePayload: 2 cabinets added, 0 dropped

Step 6: Compile and preview
  PLACE BOM → output.db → Bonsai viewport
```

Each step is an atomic verb with a typed payload. The GUI can checkpoint
after any step, undo by reversing the `{PREFIX}_BOM.db` writes, and resume from any
point.

**Round-trip editing:** If the user moves an element manually in Bonsai (drag
in 3D viewport), the system detects the change and updates the BIM COBOL
representation via override annotations:

```
ROUTE SPRINKLERS IN "departure" SPACING 3000mm BELOW_CEILING 150mm {
    OVERRIDE SPR_017 AT (45200, 23100)  -- user manually repositioned
    OVERRIDE SPR_023 OMIT              -- user deleted (column obstruction)
}
```

Overrides are first-class syntax. The compiler honours them, re-routes around
them, and re-proves compliance including the overrides. If an override
violates a code rule, the compiler flags it.

**BOM namespace discipline** (BIM_COBOL.md §18.13):

| Prefix | Source | GUI access |
|--------|--------|------------|
| BUILDING_* | Top-level building BOMs (bom_type=BUILDING) | Read-only reference |
| SY_* | Synthetic (created by verbs) | Fully mutable |

The GUI creates and modifies SY_* BOMs only. EntityType='D' guards protect
dictionary records at the PO layer. *(Historical: EB_/WT_ prefixes removed 2026-03-10, replaced by BUILDING_*_STD naming.)*

### 2.3 CO_EmptySpaceLine → Visual Slots

CO_EmptySpaceLine records are the spatial containers that verbs target — the
iDempiere `S_Resource` (production workstation) equivalent:

- Created at structural tiers: unit, slab, floor, room
- Hold: origin coordinates, orientation (radians), remaining space (mm)
- Each line = a named locator (storey, room_name, wall face)
- The `v_co_available_space` view answers "can this item fit here?"

The GUI presents these as visual room/wall slots. The user drags a BOM into
a slot; the system checks fit via the selection cascade.

### 2.4 Selection Cascade → Auto-Fit

When the user places a room or furniture set, the BOM selection cascade
([BOMBasedCompilation.md §3](BOMBasedCompilation.md)) finds the best match:

1. **BomCategory** (scope) — restricts to correct functional type
2. **AABB fit** (primary) — product must fit in the available space
3. **Largest volume** (secondary) — maximize space usage
4. **seq_no** (tiebreaker) — lower preferred

If exactly one BOM matches = **EN-BLOC** (taken whole). If multiple match =
**WALK THRU** (compiler walks slots in sequence, fitting best candidate per
slot). The user sees the result and can override via verb edits.

### 2.5 EntityType → Data Governance

EntityType ([DEVELOPER_GUIDE.md §EntityType](DEVELOPER_GUIDE.md)) guards the
boundary between shipped catalog and user work:

| Type | Meaning | GUI rule |
|------|---------|----------|
| D (Dictionary) | Shipped catalog — read-only | User can browse and select, never modify |
| U (User) | Verb-created — fully mutable | User's own designs, free to edit |
| A (Application) | Custom industry extensions | Per-deployment customization |

Guards enforce this at the PO layer (MBOM.beforeSave, MBOMLine.beforeSave).
The GUI presents Dictionary BOMs as templates to clone from, User BOMs as
editable designs. `GodMode.txt` override exists for developers.

### 2.6 Three BOM Dimensions → Chooser Panels

The 3 BOM dimensions ([BIMasBOMConcept.md](BIMasBOMConcept.md)) map directly
to GUI chooser panels:

| Dimension | Drives | GUI panel |
|-----------|--------|-----------|
| **Category** (M_BomCategory) — WHAT | Room type: kitchen, bedroom, bathroom | Typology chooser |
| **Owner** (C_DocType.DocSubType) — WHICH | Building variant: SH, DX, TB | Building type selector |
| **SpaceSize** (AABB on M_BOM_Line) — HOW MUCH | Spatial fit in mm | Dimension sliders |
| **Theme** (C_Campaign) — STYLE | Design palette: Bali, Scandinavian | Theme chooser (planned) |

Adding a new building type = adding BOM data. The GUI discovers available
options from the catalog automatically.

---

## 3. Three-Concern Lock → GUI Safety

The three-concern lock ([ConstructionAsERP.md §11.9](ConstructionAsERP.md))
prevents the GUI from corrupting spatial data:

| Table | Concern | GUI access |
|-------|---------|------------|
| **C_OrderLine** | WHAT (building type, discipline, element ref) | Read-only — no position setters exist |
| **PP_Order_Node** | HOW (verb keyword, COBOL source, parameters) | GUI edits verb params via PP_Order_NodeProduct |
| **CO_EmptySpaceLine** | WHERE (origin, orientation, remaining space) | Read-only — verbs write, GUI reads |

The Java PO interface for C_OrderLine has **no position setters** by
construction. The GUI cannot directly place elements — it must go through
verbs, which the compiler executes. This architectural guard prevents
flat-data shortcuts.

**PP_Order_NodeProduct** holds structured parameters (origin_x, grid_nx,
spacing_mm) as form-editable name/value pairs. The Bonsai addon reads these
and presents them as sliders and spinners. The user edits params; the compiler
resolves geometry.

---

## 4. Gap 1 — Compliance as Compilation Constraint

> **Full specification:** [`DocValidate.md`](DocValidate.md) — iDempiere
> DocValidate/ModelValidator architecture, AD_Val_Rule schema, multi-jurisdiction
> seed data (UBBL, IRC, UK NDSS, AU NCC, SG BCA), and OSGi-style activation.
> The chooser panels below (§5.1 Panel 3) populate from DocValidate's jurisdiction
> rule sets.

Building codes embedded as compilation constraints, not post-hoc checks. The
compiler refuses to produce non-compliant geometry, citing the violated code
section. The witness file becomes the compliance certificate.

### 4.1 The ad_code_constraint Table

A metadata table in `{PREFIX}_BOM.db` following the iDempiere `AD_Val_Rule` pattern:

```sql
CREATE TABLE ad_code_constraint (
    id              INTEGER PRIMARY KEY,
    code_id         TEXT NOT NULL,     -- 'UBBL_2012', 'IRC_2021'
    code_section    TEXT NOT NULL,     -- '33(1)', 'R304.1'
    element_type    TEXT NOT NULL,     -- 'BEDROOM', 'BATHROOM', 'CORRIDOR'
    parameter       TEXT NOT NULL,     -- 'min_area', 'min_dim', 'max_travel_dist'
    min_value       REAL,
    max_value       REAL,
    unit            TEXT NOT NULL,     -- 'm', 'm2', 'mm', '%', 'count'
    profile         TEXT,              -- NULL = all, or specific jurisdiction
    severity        TEXT DEFAULT 'MANDATORY',
    provenance      TEXT NOT NULL,     -- '[RESEARCHED: UBBL 2012 Table 5.1]'
    notes           TEXT
);
```

Adding a new jurisdiction = SQL INSERTs. No Java change. The `profile` column
enables jurisdiction stacking: Malaysian buildings check UBBL rows; US
buildings check IRC rows. Same resolver, different data.

### 4.2 Resolver Constraint Injection

Existing resolvers gain one additional query step:

```
DSL declares BEDROOM → resolver reads BOM category → picks BOM (3100x3100)
                      → resolver reads ad_code_constraint for profile
                      → checks: 3100mm >= 3000mm min_dim PASS
                      → checks: 9.61m2 >= 9.2m2 min_area PASS
                      → proceeds with placement

FAILURE:
DSL declares BEDROOM size:2.8x2.8m → resolver reads constraint
                      → checks: 2800mm < 3000mm min_dim FAIL
                      → COMPILE ERROR: BEDROOM min dimension 2800mm violates
                        UBBL 2012 s33(1) minimum 3000mm
                        [profile: Malaysian_Residential]
```

The check is a simple bounds test. Complexity is in populating the data
correctly, not in the check logic. Mirrors iDempiere's
`ModelValidator.beforeSave()` pattern.

### 4.3 Compliance Witness Claims

Extend the existing witness system. The witness file becomes a
**machine-readable compliance certificate**:

```json
{
  "CODE_UBBL_SPATIAL": {
    "status": "PROVEN",
    "witness": {
      "code": "UBBL_2012",
      "profile": "Malaysian_Residential",
      "checks": [
        { "section": "33(1)", "element": "BEDROOM master",
          "parameter": "min_area", "required": "9.2 m2",
          "actual": "9.61 m2", "result": "PASS" }
      ],
      "summary": { "checked": 47, "passed": 47, "failed": 0 }
    }
  }
}
```

An authority receiving the IFC + witness.json can verify compliance without
reading drawings.

### 4.4 GUI Integration

Compliance data drives the GUI's slider ranges:

- **Lower bound** = code minimum from ad_code_constraint
- **Upper bound** = prefab catalog maximum from M_BOM allocated dimensions
- The GUI never offers an illegal dimension

The Code/Jurisdiction chooser panel swaps the active profile. Recompilation
shows red/green indicators with code citations. Round-trip: if the user edits
geometry directly in Bonsai, clicking "Verify" runs the witness against the
modified DB and reports violations.

---

## 5. Gap 2 — Bonsai Addon

A Python addon within Bonsai (BlenderBIM) that wraps the Java compiler engine.
The addon presents chooser panels, generates BIM COBOL verb sequences, invokes
the compiler via subprocess, and refreshes the Bonsai viewport from the shared
spatial DB.

### 5.1 Five Chooser Panels

All panels are standard `bpy.types.Panel` implementations. Each modifies
parameters and triggers recompilation.

**Panel 1: Typology Chooser** — EnumProperty dropdown populated from catalog.
Adding a new typology = adding BOM data + DSL template. The addon discovers
available templates automatically.

**Panel 2: Site Chooser** — Terrain CSV import, orientation dial, setback
inputs. Compiler adjusts slab levels, foundation type, drainage fall.

**Panel 3: Code/Jurisdiction Chooser** — Swaps active code_id set. Recompiles.
Violations show red indicators with code citations.

**Panel 4: Budget/Material Chooser** — Tier selector (ECONOMY / STANDARD /
PREMIUM) swaps BOM material profiles. Same geometry, different materials and
cost.

**Panel 5: Customise (Block Editor)** — Bonsai element selection → addon reads
assembly_id → shows compatible replacements and dimension sliders.

### 5.2 Dimension Sliders

```
User clicks BEDROOM → addon shows:
  Width:  [===|========] 3.1m (code min) ... 5.0m (catalog max)
  Depth:  [===|========] 3.1m (code min) ... 7.0m (catalog max)

Drag width to 4.0m → addon emits: RESIZE ROOM SY_BD TO 4000 3000 2800
  → recompile → furniture auto-refits → MEP auto-adjusts → BOM updates
  → visible in viewport within seconds
```

### 5.3 Block Swap

```
User clicks BATHROOM → addon queries catalog:
  BATHROOM_STD     (1500 x 2400mm) — current
  BATHROOM_ACCESS  (2000 x 2400mm) — wheelchair
  BATHROOM_COMPACT (1200 x 1800mm) — code minimum

Select replacement → AABB fit check → recompile → viewport updates
```

### 5.4 Compiler Invocation

```python
import subprocess, json
result = subprocess.run(
    ['java', '-jar', 'bim-compiler.jar', dsl_path, output_dir],
    capture_output=True, text=True
)
# Parse witness.json for status display
# Trigger Bonsai viewport refresh from spatial DB
bpy.ops.bim.reload_spatial_db()
```

The addon is a thin parameter layer. The compiler is the engine. This is
why no custom 3D viewer is needed — Bonsai provides full LOD400 rendering,
and the project already contributed the FederatedModel spatial DB schema to
Bonsai.

---

## 6. Intent-Driven Construction — From Rooms to Elements

The pipeline reverses for new buildings: instead of extracting from IFC, the
user describes intent and workers generate placement data.

```
OLD:  IFC file → Extract → Metadata → Compile → Output
NEW:  Intent → GUI → BIM COBOL verbs → BOM data → Compile → Output
                                          ↑
                                    Same compiler.
                                    Same {PREFIX}_BOM.db.
                                    Same output DB.
```

### 6.1 The Cascade

User intent cascades through the existing infrastructure:

```
INTENT: "3-bedroom terrace house, 9900x8500"
    │
    ├─ CREATE ROOM for each room (emits CREATE BOM + ADD LINE per child)
    │   Selection cascade picks best-fit BOM per room category + AABB
    │
    ├─ FURNISH ROOM per room (adds products from component_library.db)
    │   Tack offsets auto-computed along strip model
    │
    ├─ Structural generation (walls, slabs from room boundaries)
    │   BOM category + AABB determines wall type, slab thickness
    │
    ├─ MEP distribution (per-room rules from ad_space_type_mep)
    │   Outlets, lights, switches placed per spacing rules
    │
    └─ Compile → 9-stage pipeline → output.db → Bonsai viewport
```

### 6.2 The Compounding Effect

Every saved arrangement becomes a reusable recipe:

1. User creates a kitchen layout via FURNISH ROOM
2. System auto-sets AABB from children
3. Children auto-cataloged as M_Products
4. Selection cascade picks this BOM when a matching AABB is requested
5. The library grows monotonically

Eventually most rooms are already in the catalog — new buildings compile
instantly from existing recipes. This is the compound enrichment model:
each building makes the next building easier.

---

## 7. Roadmap

The GUI is Phase G in the [ACTION_ROADMAP.md](ACTION_ROADMAP.md). Prerequisites:

| Prerequisite | Status |
|-------------|--------|
| 9-stage compilation pipeline | DONE |
| BOM selection cascade | DONE |
| Tack convention + placement algebra | DONE |
| BIM COBOL verbs (38 verbs, 111 witnesses) | DONE |
| EntityType governance (D/U/A) | DONE |
| CO_EmptySpace spatial slots | DONE |
| 3 BOM dimensions + C_Campaign theme | Partial (C_Campaign planned) |
| Compliance layer (ad_code_constraint) | Planned (Phase H0) |
| Bonsai addon scaffold | Planned (Phase G) |
| **BonsaiBIMDesigner module (G-1)** | **DONE — Java server + Python addon scaffold** |

Phase G tasks: addon scaffold, typology dropdown, DSL template generation,
compiler invocation + DB refresh, parameter sliders with constraint ranges,
block swap via viewport selection, site terrain panel, budget tier + witness
status display.

**Phase G-1 (BonsaiBIMDesigner)** is the concrete Java module + Bonsai addon
bridge that implements Items 0/1 from the Federation Menu. It wraps the
existing pipeline — no new compilation logic — and exposes it over TCP
(ndjson protocol) to a thin Python addon inside Blender. See §11.

Full dependency graph: [`ACTION_ROADMAP.md`](ACTION_ROADMAP.md)

---

## 8. AttributeSetInstance → Spatial Parameter Overrides

In iDempiere, `M_AttributeSetInstance` (ASI) captures per-instance parameter
variations on an otherwise standard product. The same pattern applies here:
a catalog BOM defines the default; an ASI on the `C_OrderLine` captures the
user's specific override — width stretched, depth reduced, material swapped.

### 8.1 How Stretching Works

```
Catalog Product: WALL_EXT_150 (width=150mm, length=default, height=storey)
    │
    ├─ User grabs wall in Bonsai, stretches to 12.5m
    │
    └─ C_OrderLine for this wall instance gets:
         M_Product_ID = WALL_EXT_150        (catalog product — unchanged)
         M_AttributeSetInstance_ID → {
             length_mm: 12500,              -- override: stretched
             material_name: "BrickPlaster"  -- override: user chose material
         }
```

The compiler reads the ASI and applies it to the LEAF product at compilation
time. The catalog product stays generic. The ASI captures the user's intent.
The output.db blends the catalog geometry with the ASI dimensions.

### 8.2 ASI on BOM Lines

At the BOM level, ASI attaches to `m_bom_line` via the existing
`M_AttributeSetInstance_ID` column pattern:

| BOM line field | Source | ASI override |
|----------------|--------|--------------|
| `allocated_width_mm` | Catalog default | ASI `width_mm` if set |
| `allocated_depth_mm` | Catalog default | ASI `depth_mm` if set |
| `allocated_height_mm` | Catalog default | ASI `height_mm` if set |
| `rotation_rule` | Catalog default | ASI `rotation` if set |
| `material_name` | Catalog default | ASI `material` if set |
| `dx/dy/dz` | Tack-computed | Recalculated after ASI resize |

The compiler resolves: `effective_dimension = ASI_override ?? catalog_default`.
This is the iDempiere `getAttributeInstance().getValue()` pattern applied to
spatial parameters.

---

## 9. Container Constraints (AD_Val_Rule Pattern)

### 9.1 Child Must Not Exceed Parent

When the user stretches a room, the compiler must enforce that no child exceeds
its parent container. This follows the iDempiere `AD_Val_Rule` validation pattern:

```
AD_Rule_Val: CONTAINER_BOUND
  rule:    child.allocated_width_mm <= parent.aabb_width_mm
  scope:   all LEAF and MAKE children of any BOM
  on_fail: BLOCK + message "Room width 6000mm exceeds floor width 5500mm"
```

**Cascade:** When the user stretches a floor, every room in that floor is
re-validated. When a room is stretched, every furniture item is re-validated.
The constraint propagates DOWN the BOM tree automatically.

**Upward pressure:** If the user stretches a room beyond its floor, the GUI
can offer: "Extend floor to fit?" → extends the parent, which re-validates
against the building envelope, which re-validates against the site boundary.
Each level applies the same rule.

### 9.2 Constraint Table

```sql
CREATE TABLE ad_container_rule (
    id              INTEGER PRIMARY KEY,
    rule_name       TEXT NOT NULL,           -- 'CHILD_WITHIN_PARENT'
    parent_bom_type TEXT,                    -- NULL = all, or 'FLOOR', 'ROOM'
    axis            TEXT NOT NULL,           -- 'WIDTH', 'DEPTH', 'HEIGHT', 'ALL'
    operator        TEXT DEFAULT '<=',       -- '<=', '<', '=='
    margin_mm       REAL DEFAULT 0,          -- allow N mm tolerance
    on_exceed       TEXT DEFAULT 'BLOCK',    -- 'BLOCK', 'WARN', 'AUTO_EXTEND'
    notes           TEXT
);
```

This is data, not code. Adding a new constraint = SQL INSERT. The resolver
reads the table and applies bounds checks before compilation proceeds.

---

## 10. Pattern Multiplication (Repeating Elements)

### 10.1 The Spacing Rule

The most natural user experience for repetitive elements: "a window every 3
metres", "a beam every 4 metres", "a bridge support every 20 metres." The user
declares the **rule**, not the instances. The compiler generates the instances.

```
User in Bonsai: selects north wall (12.5m long)
  → "Add windows" → spacing slider: every 2500mm
  → Compiler: floor(12500 / 2500) = 5 windows
  → Places 5 × WINDOW_STD at dx = 1250, 3750, 6250, 8750, 11250
  → Each with ASI inheriting from the pattern rule
```

### 10.2 Pattern Rule Table

```sql
CREATE TABLE ad_pattern_rule (
    id              INTEGER PRIMARY KEY,
    rule_name       TEXT NOT NULL,           -- 'WINDOW_SPACING_NORTH'
    parent_bom_id   TEXT,                    -- scope: which BOM this applies to
    child_product_id TEXT NOT NULL,          -- what to repeat
    axis            TEXT NOT NULL,           -- 'X', 'Y', 'Z'
    spacing_mm      REAL NOT NULL,           -- every N mm
    margin_start_mm REAL DEFAULT 0,          -- offset from parent origin
    margin_end_mm   REAL DEFAULT 0,          -- stop before parent end
    min_count       INTEGER DEFAULT 1,
    max_count       INTEGER,                 -- NULL = unlimited
    alignment       TEXT DEFAULT 'CENTER',   -- 'CENTER', 'START', 'END'
    on_remainder    TEXT DEFAULT 'SKIP',     -- 'SKIP', 'FILL', 'STRETCH_LAST'
    notes           TEXT
);
```

### 10.3 Domain Examples

| Domain | Rule | Effect |
|--------|------|--------|
| Building | Window every 2.5m along wall | `n = floor(wall_length / spacing)` windows |
| Building | Beam every 4m along floor span | `n` beams at regular intervals |
| Building | Light fixture every 3m × 3m grid | `nx × ny` fixtures in ceiling plane |
| Bridge | Support pier every 20m along deck | `n` piers from abutment to abutment |
| Road | Street light every 30m along kerb | `n` lights following road alignment |
| Rail | Sleeper every 600mm along track | `n` sleepers between rail joints |

### 10.4 How It Compiles

The pattern rule generates **virtual BOM lines** at compilation time:

```
ad_pattern_rule: BEAM_SPACING
  parent_bom_id: FLOOR_GF_STR
  child_product_id: BEAM_UB150
  axis: X
  spacing_mm: 4000
  margin_start_mm: 200
  margin_end_mm: 200

Parent AABB width: 12000mm
Effective span: 12000 - 200 - 200 = 11600mm
Count: floor(11600 / 4000) + 1 = 3 + 1 = 4 beams
Positions: dx = 200, 4200, 8200, 11800 (adjusted for margin_end)
```

Each generated line inherits the pattern rule's child_product_id and gets
an ASI recording which pattern produced it. The user sees 4 beams in the
viewport. Changing the spacing slider to 3000mm → 5 beams. The compiler
regenerates; the viewport updates.

### 10.5 Interaction with Container Constraints

Pattern multiplication respects container bounds (§9):

- Generated children must fit within parent AABB
- If `spacing_mm` is too small, `max_count` caps the quantity
- If parent is resized, pattern recalculates automatically
- Container constraint prevents pattern from overflowing parent

This is the compound interaction: the user stretches a floor (§8 ASI override),
container rules validate the stretch (§9 AD_Val_Rule), and pattern rules
regenerate the beams/windows/lights at the new spacing (§10 pattern
multiplication). Three rules, one recompile, correct output.

---

## 11. Java Module — BonsaiBIMDesigner (Item A: IfcOpenShell Federation Suite)

**Maven coordinates:** `com.bim:bonsai-bim-designer:1.0-SNAPSHOT`
**Depends on:** `dag-compiler`, `bim-cobol`, `orm-sandbox`
**Module directory:** `BonsaiBIMDesigner/`

This is the concrete implementation of Phase G — the bridge between the
Java compiler engine and the Bonsai (BlenderBIM) addon. It does NOT invent
new compilation logic. It wraps the existing proven pipeline and exposes it
to Blender over a simple TCP protocol.

### 11.1 Package Structure

```
com.bim.designer/
    api/                             -- stable facade (Item A contract)
        DesignerAPI.java             -- interface: compile, listBuildingTypes, executeVerb
        CompileRequest.java          -- immutable request record
        CompileResponse.java         -- immutable response record
        DesignerServer.java          -- TCP socket server (ndjson protocol, port 9876)
    compile/
        IncrementalCompiler.java     -- scope-limited recompile (delegates to pipeline)
        CompileScopeDetector.java    -- change type → stage mapping
        ChangeSet.java               -- typed change descriptor (YAML, BOM_LINE, PRODUCT, etc.)
    watch/
        ArtifactWatcher.java         -- file mtime polling on source artifacts
        ChangeEvent.java             -- typed notification
    protocol/
        JsonProtocol.java            -- Gson codec for ndjson wire format
        StatusMessage.java           -- async push to Bonsai (COMPILE_COMPLETE, ERROR)
```

### 11.2 DesignerAPI — The Stable Contract

The key design: **DesignerAPI never reads YAML content.** It delegates to
`BuildingRegistry` (which reads `C_DocType`) for building discovery and
`VerbRegistry` for verb dispatch. This makes it immune to Rosetta Stone
YAML restructuring — the YAML format can change without touching this module.

```java
public interface DesignerAPI {
    CompileResponse compile(CompileRequest request);
    CompileResponse compileIncremental(CompileRequest request, ChangeSet changes);
    List<BuildingTypeInfo> listBuildingTypes();
    List<CategoryInfo> listCategories(String docSubType);
    VerbResponse executeVerb(String buildingId, String verbLine);
}
```

### 11.3 How It Rides on the Existing Framework

Every capability wraps an existing proven component — no new compilation
logic, no new data model, no new governance. The module is a protocol
adapter, not a compiler.

| Capability | Rides on | How |
|-----------|----------|-----|
| **Compilation** | `CompilationPipeline.run(BuildingEntry)` | Wraps the 9-stage pipeline via `BuildingRegistry.loadById()` → `CompilationPipeline.run()`. Same code path as `run_RosettaStones.sh` |
| **Building discovery** | `BuildingRegistry.loadActive()` | Reads `C_DocType` from BOM.db — YAML-opaque. Adding a building type = adding BOM data, not code |
| **Verb dispatch** | `VerbRegistry.createDefault().dispatch(ctx, line)` | 63 verbs via longest-prefix match. GUI emits verb lines, server dispatches them |
| **Product catalog** | `component_library.db` via existing `compConn` pattern | Same connection pooling as the pipeline. No new DB access layer |
| **Output writing** | `WriteStage` → `FederatedModel` schema | Same output.db schema that Bonsai's FederatedDBReader already reads |
| **Data governance** | `EntityType` guards on PO layer | D (dictionary) = read-only, U (user) = mutable. Same guards as pipeline |
| **QA validation** | `BomValidator` (9 checks + verb fidelity) | Runs before BOM commit — broken data never reaches disk |

### 11.4 Server Protocol — ndjson over TCP

TCP socket (default port 9876), newline-delimited JSON. No HTTP framework
dependency. One JSON object per line.

**Request/response examples:**

```
→ {"action":"compile","buildingId":"Ifc4_SampleHouse","bomDbPath":"library/_SH_compile.db"}
← {"success":true,"elementCount":55,"compileTimeMs":847,"outputDbPath":"..."}

→ {"action":"verb","buildingId":"...","verbLine":"CHECK BOM BUILDING_SH"}
← {"success":true,"verb":"CHECK BOM","summary":"7 lines, 218.4m3"}

→ {"action":"listBuildings"}
← [{"docTypeId":"...","name":"Ifc4_SampleHouse","docSubType":"SH","expectedElements":55,...}]
```

**Async push** (after ArtifactWatcher detects change + auto-recompile):
```
← {"type":"COMPILE_COMPLETE","buildingId":"...","outputDbPath":"...","elementCount":55}
```

### 11.5 How It Rides on Blender

The Python addon is a thin layer (~6 files, ~400 lines). It does NOT
create geometry — it configures parameters and triggers compilation.
Blender provides everything else for free.

| Blender capability (free) | How the addon uses it |
|--------------------------|----------------------|
| **3D viewport + LOD400 rendering** | `db_loader.py` reads output.db → creates Blender mesh objects. Blender renders them at full quality with materials, lighting, shadows |
| **Section cuts** | Blender's native clipping planes. No addon code needed |
| **Element selection** | Click element → addon reads `element_ref` → reverse-lookup to `m_bom_line.bom_child_id` → shows editable properties |
| **Property inspection** | Blender's Properties panel. Addon adds custom properties from BOM/ASI data |
| **Dimensioning** | Blender's annotation tools. Addon can auto-create dimension annotations from AABB data |
| **Panel framework** | 5 chooser panels as standard `bpy.types.Panel` — Typology, Site, Code/Jurisdiction, Budget, Customise |
| **Operator framework** | `bpy.types.Operator` for compile/reload actions — standard Blender button/shortcut binding |
| **Thread-safe callbacks** | `bpy.app.timers` receives async COMPILE_COMPLETE from Java server on background thread, schedules viewport reload on main thread |

**Python addon structure:**

```
bonsai_bim_designer/
    __init__.py     -- bl_info, register/unregister
    operator.py     -- Blender operators (BIM_OT_compile, BIM_OT_reload)
    panel.py        -- 5 chooser panels (bpy.types.Panel)
    client.py       -- TCP client to Java server (ndjson protocol)
    db_loader.py    -- reads output.db → Blender mesh objects
    props.py        -- Blender property groups (building_id, bom_path, etc.)
```

### 11.6 Realtime Compilation — Scope-Limited Recompile

**Verdict:** Sub-2s for BOM line edits on SH/DX. 5-8s for room edits on
TE-scale. True keystroke reactivity not feasible without violating Prime
Rule ("compile only").

**Batch model is correct:** BIM_Designer.md §1 already specifies "click
Process, see result" (iDempiere DocAction pattern). The server adds:
auto-detect change → auto-compile → push notification → Bonsai reloads.
This feels realtime to the user without breaking the deterministic pipeline.

**Scope table (CompileScopeDetector):**

| Change | Stages rerun | Expected time (TE-scale) |
|--------|-------------|--------------------------|
| BOM line dx/dy/dz only | WriteStage (5) only | ~2s |
| Room furniture swap | Stages 3-9, one storey | ~5s |
| Storey-level change | Stages 3-9, storey + neighbors | ~8s |
| YAML or structural | Full pipeline (1-9) | ~30s |

For SH/DX: full recompile is already <3s, so scope limiting is
unnecessary. IncrementalCompiler currently falls back to full compile;
stage-level entry points are a post-G-1 enhancement.

**Auto-recompile flow:**

```
ArtifactWatcher detects mtime change on source file
  → classifies change type (YAML, BOM_LINE, PRODUCT, etc.)
  → CompileScopeDetector determines minimal stage mask
  → IncrementalCompiler runs (or falls back to full compile)
  → StatusMessage.COMPILE_COMPLETE pushed to connected Bonsai clients
  → Python addon receives via threading.Thread
  → Dispatches to Blender main thread via bpy.app.timers
  → db_loader.py reloads output.db → viewport updates
```

### 11.7 What This Module Does NOT Do

- Does NOT parse or read YAML files (delegates to BuildingRegistry)
- Does NOT create new pipeline stages (wraps existing CompilationPipeline)
- Does NOT define new verb semantics (dispatches to existing VerbRegistry)
- Does NOT write new DB schemas (uses existing BOM.db + FederatedModel)
- Does NOT implement 3D rendering (Blender does this)
- Does NOT replace subprocess invocation (§5.4) — the server IS the long-running JVM that subprocess would start; it just stays alive between compiles

---

---

## 12. Versatility — Best of Both Worlds

> **Bridge spec:** [`BlenderBridge.md`](BlenderBridge.md) — thin pipe between
> compiler output and Blender viewport. Incremental delta updates (don't
> reload 48K objects when 3 changed), BIM verb shortcuts over bpy, material/mesh
> caching. Rides on Federation's existing Full Load — adds the fast path.

### 12.1 What the Compiler Brings

The Java compiler is a **deterministic manufacturing engine**. It guarantees:

| Capability | How | Why it matters for design |
|-----------|-----|--------------------------|
| **Repeatability** | Same BOM + library → same output, always | Undo = recompile from last known-good BOM |
| **Compliance gating** | DocValidate fires before output.db | Designer cannot produce illegal geometry |
| **BOM cascade** | BUILDING→FLOOR→ROOM→LEAF automatic | One slider change ripples correctly through entire tree |
| **Verb audit trail** | PP_Order_Node records every action | Full undo history, no mystery state |
| **Multi-scale** | SH (55) to TE (48K) same pipeline | Works for a cottage or an airport |
| **ERP integration** | C_Order/C_OrderLine/CO_EmptySpace | Costing, scheduling, procurement ready from day 1 |

### 12.2 What Blender Brings

Blender is a **professional 3D content creation tool**. It provides for free:

| Capability | How | Why the compiler can't do this |
|-----------|-----|-------------------------------|
| **LOD400 rendering** | Cycles/EEVEE render engine | Photorealistic visualization, walkthroughs, client presentations |
| **Section cuts** | Native clipping planes | Architectural section drawings without any code |
| **Mesh editing** | Full polygon modeling | Custom geometry for non-standard components |
| **Materials** | PBR shader system | Realistic brick, glass, timber, concrete |
| **Animation** | Timeline + keyframes | Construction sequence visualization (4D) |
| **Dimensioning** | Annotation tools | Automated dimension callouts from AABB data |
| **Selection + inspection** | Click → properties panel | Navigate the BOM tree spatially |
| **Addon ecosystem** | Python scripting + bpy API | Extend with IFC export, sun studies, structural analysis |
| **Cross-platform** | Linux/Mac/Windows | No proprietary lock-in |

### 12.3 The Compound Effect

Neither system alone is sufficient. Together they compound:

```
Compiler alone:    Correct but invisible. Output.db is numbers in a database.
Blender alone:     Beautiful but arbitrary. No BOM, no compliance, no repeatability.
Together:          Correct AND visible. Edit parameters → see compliant 3D result.

The compiler is the engine.  Blender is the cockpit.
The compiler guarantees.     Blender communicates.
The compiler is the ERP.     Blender is the CAD.
```

**The key insight:** The compiler handles the hard problems (BOM cascade,
compliance, placement algebra, multi-discipline coordination) while Blender
handles what 3D tools are built for (rendering, interaction, visualization).
Neither tries to do the other's job.

---

## 13. Demo House — Generative POC Specification

### 13.1 Purpose

Prove the generative path works end-to-end: "Create New" → settings dialog →
BOM generation → compile → view in Bonsai. This house is NOT a RosettaStone
(no IFC extraction). It is entirely generative — `Provenance='GENERATIVE'`.

### 13.2 Demo House Definition

**Name:** `DemoHouse_2BR`
**Type:** Single-storey, 2-bedroom residential
**Jurisdiction:** MY (UBBL 2012)
**Envelope:** 9000 × 7000 × 3000mm (single storey + roof)

```
GRID {
    axes: A, B, C / 1, 2, 3
    spacing: 4.0, 5.0 / 3.5, 3.5
}

STOREY "Ground" level:0 height:2.8m {
    LIVING "ruang_tamu" bounds:A1-B2 {
        exterior: west, south
        WINDOW wall:west
        DOOR type:D1 wall:south    -- main entry
    }
    KITCHEN "dapur" bounds:A2-B3 {
        exterior: west
        WINDOW wall:west
        adjacent: ruang_tamu
    }
    BEDROOM "bilik_1" bounds:B1-C2 {
        exterior: east
        WINDOW wall:east
        DOOR type:D2
    }
    BEDROOM "bilik_2" bounds:B2-C3 {
        exterior: east, north
        WINDOW wall:east
        DOOR type:D2
    }
    BATHROOM "bilik_mandi" bounds:B3-C3 {
        -- nested within bilik_2 area, adjusted bounds
        stack: plumbing
        DOOR type:D3
    }
}

ROOF pitch:15deg overhang:600mm
```

### 13.3 Room Compliance (UBBL 2012)

| Room | Bounds | Area | UBBL Min | Min Dim | Actual Min | Verdict |
|------|--------|------|----------|---------|------------|---------|
| ruang_tamu (living) | A1-B2 | 14.0m² | 12.0m² | — | 3500mm | PASS |
| dapur (kitchen) | A2-B3 | 14.0m² | 4.5m² | 1500mm | 3500mm | PASS |
| bilik_1 | B1-C2 | 17.5m² | 9.2m² | 3000mm | 3500mm | PASS |
| bilik_2 | B2-C3 | 17.5m² | 9.2m² | 3000mm | 3500mm | PASS |
| bilik_mandi | (within C3) | ~3.0m² | 1.5m² | — | — | PASS |

**Total: ~66m²** — modest Malaysian residential.

### 13.4 BOM Structure (what "Create New" generates)

```
BUILDING_DEMO_2BR (BUILDING, RE, DM)
├── FLOOR_DEMO_GF (FLOOR, seq=10)
│   ├── ROOM_DEMO_LI (ROOM, LIVING, 4000×3500×2800)
│   │   ├── WALL_EXT_200 (BUY, west wall)
│   │   ├── WALL_EXT_200 (BUY, south wall)
│   │   ├── WINDOW_STD (BUY, west)
│   │   ├── DOOR_D1 (BUY, south — main entry)
│   │   └── SLAB_150 (BUY, floor)
│   ├── ROOM_DEMO_KT (ROOM, KITCHEN, 4000×3500×2800)
│   │   ├── WALL_EXT_200 (BUY, west wall)
│   │   ├── WINDOW_STD (BUY, west)
│   │   └── SLAB_150 (BUY, floor)
│   ├── ROOM_DEMO_BD1 (ROOM, BEDROOM, 5000×3500×2800)
│   │   ├── WALL_EXT_200 (BUY, east wall)
│   │   ├── WINDOW_STD (BUY, east)
│   │   ├── DOOR_D2 (BUY, internal)
│   │   └── SLAB_150 (BUY, floor)
│   ├── ROOM_DEMO_BD2 (ROOM, BEDROOM, 5000×3500×2800)
│   │   ├── WALL_EXT_200 (BUY, east wall)
│   │   ├── WALL_EXT_200 (BUY, north wall)
│   │   ├── WINDOW_STD (BUY, east)
│   │   ├── DOOR_D2 (BUY, internal)
│   │   └── SLAB_150 (BUY, floor)
│   └── ROOM_DEMO_BT (ROOM, BATHROOM, ~2000×1500×2800)
│       ├── DOOR_D3 (BUY, internal)
│       └── SLAB_150 (BUY, floor)
└── ROOF_DEMO (ASSEMBLY, seq=20)
```

### 13.5 Products Required (to seed in component_library.db)

| Product ID | Type | Width | Depth | Height | Material |
|-----------|------|-------|-------|--------|----------|
| WALL_EXT_200 | WALL | parametric | 200mm | parametric | Brick |
| SLAB_150 | SLAB | parametric | parametric | 150mm | Concrete |
| WINDOW_STD | WINDOW | 1200mm | 200mm | 1000mm | Glass |
| DOOR_D1 | DOOR | 900mm | 100mm | 2100mm | Timber |
| DOOR_D2 | DOOR | 750mm | 100mm | 2100mm | Timber |
| DOOR_D3 | DOOR | 750mm | 100mm | 2100mm | PVC |
| ROOF_TILE | ROOF | parametric | parametric | 25mm | Clay |

**Parametric** = dimensions computed from parent AABB at compile time.
These are minimal seed products — enough to prove the generative path.
Real projects would use the full component_library.db catalog.

### 13.6 Success Criteria

1. "Create New" dialog produces valid C_DocType + m_bom + m_bom_line
2. DocValidate checks all rooms against UBBL → all PASS
3. CompilationPipeline.run() produces output.db with element instances
4. Bonsai loads output.db → visible 3D house in viewport
5. Change bedroom width to 2800mm → DocValidate BLOCKS (below UBBL 3000mm min)
6. No RosettaStone data used — entirely generative

---

*Related docs:
[BOMBasedCompilation.md](BOMBasedCompilation.md) (compilation method, tack convention) |
[ConstructionAsERP.md](ConstructionAsERP.md) (3-DB architecture, three-concern lock) |
[BIM_COBOL.md](BIM_COBOL.md) (verb language spec) |
[DocValidate.md](DocValidate.md) (validation engine, building codes, jurisdiction) |
[DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) (pipeline, DAO pattern, EntityType) |
[InfrastructureAnalysis.md](InfrastructureAnalysis.md) (bridge/road/rail domain mapping)*

<!-- DeepSeek analysis reviewed 2026-03-18. Useful content absorbed into
     DocValidate.md: AD_Validation_Result schema (§3.1), AD_Val_Rule_Exception
     schema (§3.1), BomValidator integration (§3.4), ProjectContext/jurisdiction
     on C_Order (§3.4), R-tree performance note (§3.4). Remainder written off:
     R-tree already exists (elements_rtree in BuildingWriter), DX element count
     wrong (1,099 not 1.1M), JavaScript/HTTP API doesn't match our ndjson/TCP
     architecture. -->
     
============================================================================================
DEEPSEEK USER EXPERIENCE SCENARIO 

# BIM Designer User Experience — The Compounding Advantage

## The Core Experience Difference

**Autodesk experience:** You fight the tool.  
**Your experience:** The tool fights for you.

Every interaction compounds knowledge. Every building teaches the system. Every click gets smarter.

---

## 1. Starting a New Building — The Zero-Click Advantage

### Autodesk World
```
New Project → Select Template → Set Up Levels → Draw Grid → Import Families →
Place Walls → Place Doors → Place Windows → ... 40 hours before you see a complete building
```

### Your World (Create New)
```
Click "Create New" (Federation Menu → Item 2)
↓
Dialog appears with:
  - Jurisdiction: [MY ▼]  (pre-filled from project context)
  - Building type: [Terrace House ▼]
  - Rooms: [☑ Living] [☑ Kitchen] [☑ 3 Bedroom] [☑ Bathroom]
  - Site width/depth: [9900] [8500] (auto-calculates from typology)
↓
Click "Generate"
↓
<5 seconds later: full 3D house in viewport, fully compliant, fully furnished
```

**The difference:** You didn't place a single wall. You expressed **intent**, and the compiler built it from proven patterns.

---

## 2. Editing — Sliders Not Surgery

### Autodesk World
Want to change a room size?
- Select walls (shift+click 4 walls)
- Drag each wall (hope they move together)
- Fix the windows that now float in space
- Fix the door that's now in the corner
- Fix the roof that no longer aligns
- 15 minutes of manual cleanup

### Your World
```
Click room → Sliders appear:
  Width:  [===|====] 3.5m (min 3.0m)
  Depth:  [===|====] 5.0m (min 3.0m)
  Height: [===|====] 2.8m (min 2.6m)

Drag width to 4.0m
↓
<2 seconds later: room resized, windows repositioned, furniture refitted,
                  MEP adjusted, roof recalculated, still compliant
```

**The difference:** The compiler handles the cascade. You just set parameters.

---

## 3. Jurisdiction Switching — Instant Compliance

### Autodesk World
Project moves from Malaysia to Singapore:
- Manually check every room against BCA rules
- Find 47 violations
- Spend 3 weeks redesigning
- Bill client for "international compliance services"

### Your World
```
Click Jurisdiction dropdown:
  [MY ▼] → [SG ▼]
↓
<5 seconds later: viewport updates
                  Red highlights on non-compliant elements
                  Tooltip: "Bedroom 2: 2.8m width → BCA requires 3.0m"
                  One-click: "Auto-fit to compliance"
```

**The difference:** Codes are data, not expertise. The tool knows what's legal everywhere.

---

## 4. The Learning Curve — No Learning Curve

### Autodesk World
- 40 hours of training to be productive
- 200 hours to be proficient
- 1000 hours to be expert
- Certifications cost $2000+
- Every new version: re-learn the UI

### Your World
```
First time user:
  Click "Create New" → house appears
  Drag slider → house updates
  Click "Add window" → window appears on wall at legal spacing
  Click "Change jurisdiction" → house rechecks against different codes
  Total time: 3 minutes to first building
```

**The difference:** The tool matches how you think about buildings (rooms, walls, windows), not how Revit organizes its data model.

---

## 5. Compound Knowledge — The Building That Teaches

### Autodesk World
You finish a hotel project. What does the software learn?
- Nothing. Next hotel starts from default templates.
- Your 2000 hours of work vanish.
- Next team repeats your mistakes.

### Your World
You finish a hotel project. What does the system learn?
```
Extracted patterns:
  - Typical room width: 4.2m (from 127 rooms)
  - Corridor width: 1.8m (from 3 floors)
  - MEP riser locations: 2 per floor at (x=12.5m, 37.2m)
  - Window spacing: every 3.2m on exterior walls
  - Fire sprinkler coverage: 4.6m spacing (Light Hazard)

These become new BOM templates.
Next hotel: these are the defaults.
```

**The difference:** Your firm's intellectual property lives in the system, not in PDFs or in people's heads. Each project makes the next one faster.

---

## 6. The Witness File — Zero-Effort Compliance

### Autodesk World
Submit for permit:
- Print 47 sheets of drawings
- Highlight all code compliance manually
- Write 20-page compliance report
- Wait 6 weeks for plan check
- Fix 15 things they caught
- Resubmit, wait 4 more weeks

### Your World
```
Click "Generate Compliance Certificate"
↓
witness.json created with:
  - 347 checks performed
  - 347 passed
  - Citations to UBBL sections for each check
  - 3D viewer link to verify any point

Submit JSON + IFC export
↓
Plan checker opens in viewer, clicks any room, sees:
  "Bedroom 1: 9.61m² ≥ 9.2m² required [UBBL 33(1)]"
↓
Permit approved in 3 days
```

**The difference:** Compliance is machine-verifiable, not human-interpreted. The building proves itself.

---

## 7. The Undo That Works

### Autodesk World
Undo: Ctrl+Z works for last action.  
Undo yesterday's work? Hope you saved versions.  
Undo last week's redesign? Restore from backup, lose a day's work.

### Your World
```
Every edit is a BIM COBOL verb.
Every verb is stored in PP_Order_Node.
Time travel:
  Click "History" → timeline of every change
  Select any point → "Restore to this state"
  <5 seconds later: building exactly as it was that day
  (because you're just recompiling from that BOM snapshot)
```

**The difference:** The BOM is source code. You have version control built in. "Undo" works across days, weeks, projects.

---

## 8. Multi-Discipline Coordination — No Clash Detection Needed

### Autodesk World
- Architect designs
- Structural engineers model over it
- MEP engineers model over that
- Run clash detection → 1500 clashes
- 3 months of coordination meetings
- 40% still clash in field

### Your World
```
MEP engineer selects "Route conduits through Floor 2"
↓
Compiler knows:
  - Structural beams at y=3.5m, 7.0m
  - Fire-rated walls at grid lines 2,4
  - Plenum depth 450mm
↓
Routes generated:
  - Conduits avoid beams (reroute automatically)
  - Penetrations through fire walls get fire stop collars inserted
  - Clearance from plumbing maintained
  - Zero clashes in output.db
```

**The difference:** Clash detection is proactive, not reactive. The compiler routes around known constraints. The building is coordinated before anyone sees it.

---

## 9. The Preview That's the Real Thing

### Autodesk World
- Design model (LOD300) ≠ construction model (LOD400) ≠ fabrication model (LOD500)
- Three separate models, three separate teams, three separate budgets
- Coordination between them is manual

### Your World
```
You design in the same model that gets built.
LOD300 slider? There is no LOD slider.
What you see in Bonsai:
  - Actual wall assemblies (brick + plaster + paint)
  - Actual MEP components (valves, hangers, fire dampers)
  - Actual reinforcement in concrete
  - Because the compiler places real products from library

Click "Export to fabrication" → same model, just different view filters.
```

**The difference:** There's only one model. The design model *is* the construction model *is* the fabrication model. Different views of the same database.

---

## 10. The "I Don't Know How" Test

### Autodesk World
User: "I need to design a building with 3% glazing ratio, Malaysian code, timber structure, and passive cooling."

Result: User Googles "Revit timber structure tutorial" → watches 20 minutes → tries → fails → hires consultant.

### Your World
```
User: Same requirements.
System: 
  "Malaysian jurisdiction selected. 
   Timber structure: 3 options available (BALSA, OAK, GLULAM).
   Passive cooling: applying stack effect rules from UBBL Appendix C.
   3% glazing ratio: calculating window areas...
   Recommended window sizes: 1.2m × 1.2m (3 per room)."

User: Clicks "Accept" → building appears.
```

**The difference:** The system knows building science. You don't need to know how — you just need to know what you want.

---

## The Summary Experience

**Autodesk user:** "I spent 6 months modeling this building. Now I need to do the next one. I hope I remember how."

**Your user:** "I spent 6 months designing this building. The system learned 347 patterns from it. The next one will take 3 weeks."

The experience isn't just faster. It's **compounding**. Every project makes the tool smarter. Every user makes the system better for the next user. The software learns from the entire community's work.

That's the experience no one else can offer — because no one else has built the learning loop.

============================== DEEP SEEK ENABLING FRAMEWORK ====================

# BIM Designer — Data Model & Code Architecture for Scale

## The Core Insight

The rich experience described isn't magic — it's the result of **five interlocking data models** that handle complexity at different scales. Each model addresses a specific concern, and together they enable the compounding effect.

---

## 1. The Five Data Models

### 1.1 Product Model (`component_library.db`)
```sql
-- What exists, independent of where it goes
CREATE TABLE m_product (
    m_product_id        INTEGER PRIMARY KEY,
    value               TEXT NOT NULL,           -- 'WALL_EXT_200'
    name                TEXT NOT NULL,
    product_type        TEXT NOT NULL,           -- 'WALL', 'SLAB', 'WINDOW', 'DOOR'
    bom_category        TEXT,                     -- 'STRUCTURAL', 'ARCH', 'MEP'
    -- Geometry (static)
    geometry_blob       BLOB,                     -- Compressed mesh
    aabb_width_mm       REAL,                      -- Default width (parametric if 0)
    aabb_depth_mm       REAL,
    aabb_height_mm      REAL,
    -- Material
    material_id         INTEGER REFERENCES m_material,
    -- Behavior
    rotation_rule       TEXT DEFAULT 'NONE',      -- 'SYMMETRIC', 'DIRECTIONAL', 'STACK'
    tack_points         TEXT,                      -- JSON array of connection points
    -- Governance
    entity_type         TEXT DEFAULT 'D',          -- 'D'=dictionary, 'U'=user
    created             TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Scale enabler:** Products are parametric (0 = derived from parent). A single product serves infinite variations. TE's 48K elements come from ~500 products, not 48K unique models.

### 1.2 Recipe Model (`{PREFIX}_BOM.db`)
```sql
-- How things go together (the template layer)
CREATE TABLE m_bom (
    m_bom_id            INTEGER PRIMARY KEY,
    value               TEXT NOT NULL,           -- 'BEDROOM_3100x3100'
    name                TEXT,
    bom_type            TEXT NOT NULL,           -- 'BUILDING', 'FLOOR', 'ROOM', 'ASSEMBLY'
    bom_category        TEXT NOT NULL,           -- 'LIVING', 'KITCHEN', 'BEDROOM'
    doc_type_id         INTEGER REFERENCES c_doctype,
    -- Dimensions (template defaults)
    allocated_width_mm  REAL,
    allocated_depth_mm  REAL,
    allocated_height_mm REAL,
    -- Pattern rules (JSON)
    pattern_rules       TEXT,                      -- Window spacing, beam spacing, etc.
    -- Governance
    entity_type         TEXT DEFAULT 'D'
);

CREATE TABLE m_bom_line (
    m_bom_line_id       INTEGER PRIMARY KEY,
    m_bom_id            INTEGER NOT NULL REFERENCES m_bom,
    line_no             INTEGER NOT NULL,
    child_bom_id        INTEGER REFERENCES m_bom,   -- For assemblies
    child_product_id    INTEGER REFERENCES m_product, -- For leaf nodes
    -- Placement (relative to parent origin)
    dx_mm               REAL DEFAULT 0,
    dy_mm               REAL DEFAULT 0,
    dz_mm               REAL DEFAULT 0,
    rotation_rule        TEXT,                       -- Override product default
    -- Quantity pattern (for repeating elements)
    qty_formula         TEXT,                       -- 'SPACING 3000', 'GRID 3x3', 'FILL'
    -- Constraints (what children must obey)
    constraint_rules    TEXT,                       -- JSON: max_count, min_spacing
    -- Governance
    entity_type         TEXT DEFAULT 'D'
);
```

**Scale enabler:** BOMs are templates, not instances. One BEDROOM template generates infinite variations through ASI overrides. Pattern rules (qty_formula) compress 1000 window instances into one BOM line with "SPACING 3000".

### 1.3 Instance Model (`output.db` + `C_OrderLine`)
```sql
-- The actual building (compiled result)
-- C_OrderLine = each placed element
CREATE TABLE c_orderline (
    c_orderline_id      INTEGER PRIMARY KEY,
    c_order_id          INTEGER NOT NULL REFERENCES c_order,
    line_no             INTEGER NOT NULL,
    m_product_id        INTEGER NOT NULL REFERENCES m_product,
    m_attributesetinstance_id INTEGER REFERENCES m_attributesetinstance,
    -- Position (world coordinates)
    placement_x_mm      REAL NOT NULL,
    placement_y_mm      REAL NOT NULL,
    placement_z_mm      REAL NOT NULL,
    rotation_rad         REAL DEFAULT 0,
    -- What generated this
    source_bom_id       INTEGER,                    -- Which BOM line created this
    source_pattern_id   INTEGER,                     -- Which pattern rule (if any)
    -- Validation
    validation_status   TEXT,                        -- 'PASS', 'WARN', 'BLOCK'
    validation_rule_ref TEXT,                        -- Which rule checked it
    -- Governance
    entity_type         TEXT DEFAULT 'U'
);

-- Per-instance overrides (the secret sauce)
CREATE TABLE m_attributesetinstance (
    m_attributesetinstance_id INTEGER PRIMARY KEY,
    -- Key-value pairs for this specific instance
    -- 'width_mm', 'depth_mm', 'height_mm', 'material', 'color'
    -- Values override product defaults
    attributes          TEXT NOT NULL                 -- JSON object
);

-- Spatial index (for performance)
CREATE VIRTUAL TABLE elements_rtree USING rtree(
    id,                    -- c_orderline_id
    minX, maxX, minY, maxY, minZ, maxZ
);
```

**Scale enabler:** ASI captures differences without duplicating products. One product + 100 ASI records = 100 unique instances. R-tree makes clash detection O(n log n) instead of O(n²).

### 1.4 Rule Model (`validation.db`)
```sql
-- What's allowed (AD_Val_Rule pattern)
CREATE TABLE ad_val_rule (
    ad_val_rule_id      INTEGER PRIMARY KEY,
    rule_name           TEXT NOT NULL,
    rule_type           TEXT NOT NULL,           -- 'COMPLIANCE', 'CLASH', 'CLEARANCE'
    jurisdiction        TEXT NOT NULL,           -- 'MY', 'US', 'UK', 'AU', 'SG'
    code_edition        TEXT,                     -- 'UBBL 2012', 'IRC 2021'
    discipline          TEXT,                      -- 'ARC', 'STR', 'MEP', NULL=any
    -- What to check
    element_type_a      TEXT,                      -- 'BEDROOM', 'WALL', 'PIPE'
    element_type_b      TEXT,                      -- For clash rules
    parameter           TEXT,                      -- 'min_area_m2', 'min_spacing_mm'
    -- The rule itself
    min_value           REAL,
    max_value           REAL,
    condition_expr      TEXT,                      -- SQL condition for when rule applies
    verdict             TEXT DEFAULT 'BLOCK',      -- 'BLOCK', 'WARN', 'ALLOW_IF'
    resolution_guide    TEXT,                      -- What to do when blocked
    -- Metadata
    standard_ref        TEXT,                      -- 'UBBL 33(1)', 'NFPA 13 §8.6'
    provenance          TEXT,                      -- 'MINED:Terminal', 'RESEARCHED'
    valid_from          DATE,
    valid_to            DATE,
    is_active           INTEGER DEFAULT 1
);

-- Rule parameters (for complex rules)
CREATE TABLE ad_val_rule_param (
    ad_val_rule_param_id INTEGER PRIMARY KEY,
    ad_val_rule_id      INTEGER REFERENCES ad_val_rule,
    param_name          TEXT NOT NULL,
    param_value         TEXT NOT NULL,
    condition_expr      TEXT                        -- When this parameter applies
);

-- Occupancy classifications (drives which rules apply)
CREATE TABLE ad_occupancy_class (
    ad_occupancy_class_id INTEGER PRIMARY KEY,
    code                TEXT NOT NULL,           -- 'LH' (Light Hazard), 'OH1'
    name                TEXT NOT NULL,
    standard_ref        TEXT
);

-- Link rules to occupancy classes
CREATE TABLE ad_val_rule_occupancy (
    ad_val_rule_id      INTEGER REFERENCES ad_val_rule,
    ad_occupancy_class_id INTEGER REFERENCES ad_occupancy_class,
    PRIMARY KEY (ad_val_rule_id, ad_occupancy_class_id)
);

-- Validation results (audit trail)
CREATE TABLE ad_validation_result (
    ad_validation_result_id INTEGER PRIMARY KEY,
    c_orderline_id      INTEGER NOT NULL,        -- Which instance
    ad_val_rule_id      INTEGER NOT NULL,        -- Which rule checked
    result              TEXT NOT NULL,           -- 'PASS', 'WARN', 'BLOCK'
    actual_value        REAL,                      -- Measured (e.g., 2800mm)
    required_value      REAL,                      -- Required (3000mm)
    message             TEXT,                      -- Human-readable
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Documented exceptions (for extracted buildings)
CREATE TABLE ad_val_rule_exception (
    ad_val_rule_exception_id INTEGER PRIMARY KEY,
    building_id          INTEGER NOT NULL,        -- Which building (DX)
    ad_val_rule_id       INTEGER NOT NULL,        -- Which rule violated
    element_ref          TEXT,                      -- Specific elements
    count                INTEGER,
    approved_by          TEXT,
    reason               TEXT,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Scale enabler:** Rules are data, not code. Adding Malaysia's 2026 code update = INSERT statements, not Java changes. 50 jurisdictions × 200 rules = 10K rows, still < 1MB. The engine scales by query, not by compilation.

### 1.5 Audit Model (`PP_Order_Node`)
```sql
-- Every change ever (the undo/redo/history system)
CREATE TABLE pp_order_node (
    pp_order_node_id    INTEGER PRIMARY KEY,
    pp_order_id         INTEGER NOT NULL,        -- Which building
    node_no             INTEGER NOT NULL,        -- Sequence
    verb                TEXT NOT NULL,           -- 'RESIZE_ROOM', 'ADD_WINDOW'
    -- Before state (JSON snapshot)
    before_state        TEXT,
    -- After state (JSON snapshot)
    after_state         TEXT,
    -- Parameters (for replay)
    parameters          TEXT,                      -- JSON of verb arguments
    -- Who did it
    created_by          INTEGER REFERENCES ad_user,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- Git-style reference
    commit_hash         TEXT                        -- For version control
);
```

**Scale enabler:** Full history without storing full copies. "Undo 6 months" = recompile from BOM snapshot at that node. Node_no + before_state = infinite undo depth with fixed storage.

---

## 2. The Code Architecture That Makes It Scale

### 2.1 The Compiler Pipeline (9 Stages)
```
Stage 1: Parse        (YAML → in-memory objects)
Stage 2: Validate     (BOMValidator: 9 checks)
Stage 3: Resolve      (Selection cascade: find best BOM for each slot)
Stage 4: Expand       (Pattern rules → individual placements)
Stage 5: Place        (Tack algebra: compute world coordinates)
Stage 6: Route        (MEP routing around obstacles)
Stage 7: Validate     (DocValidate: check against AD_Val_Rule)
Stage 8: Write        (output.db + elements_rtree)
Stage 9: Prove        (Generate witness.json)
```

**Scale pattern:** Each stage is a separate Java class with a single responsibility. Stages communicate via immutable DTOs. This enables:
- Parallel execution (stages 3-7 can run on different threads)
- Checkpoint/resume (save stage 3 output, restart from there)
- Incremental compilation (skip stages 1-2 if YAML unchanged)

### 2.2 The Incremental Compiler
```java
public class IncrementalCompiler {
    private final ArtifactWatcher watcher;
    private final Map<ChangeType, StageMask> stageMap;
    
    public CompileResponse compileIncremental(CompileRequest req, ChangeSet changes) {
        // Determine minimal stages to rerun
        StageMask mask = StageMask.NONE;
        for (Change change : changes) {
            mask = mask.or(stageMap.get(change.getType()));
        }
        
        // Load from last checkpoint
        BuildingState state = checkpointStore.load(req.getBuildingId(), mask.getStartStage());
        
        // Run only needed stages
        for (int stage = mask.getStartStage(); stage <= 9; stage++) {
            state = pipeline.runStage(stage, state);
        }
        
        // Save new checkpoint
        checkpointStore.save(req.getBuildingId(), state);
        
        return CompileResponse.success(state);
    }
}
```

**Scale pattern:** 80% of edits affect only Stage 5-8 (placement changes). Recompiling from Stage 5 takes 2 seconds vs 30 seconds full compile.

### 2.3 The Selection Cascade (O(log n) Resolution)
```sql
-- The query that makes "drag and drop" work
SELECT b.* FROM m_bom b
WHERE b.bom_category = ?               -- 'BEDROOM'
  AND b.allocated_width_mm <= ?        -- Must fit available width
  AND b.allocated_depth_mm <= ?        -- Must fit available depth
  AND b.allocated_height_mm <= ?       -- Must fit available height
  AND b.entity_type IN ('D', 'U')      -- Dictionary or User BOMs
ORDER BY 
  -- Largest that fits (primary)
  (b.allocated_width_mm * b.allocated_depth_mm) DESC,
  -- Smallest gap (secondary)
  ABS(? - b.allocated_width_mm) + ABS(? - b.allocated_depth_mm),
  -- Preferred sequence (tiebreaker)
  b.seq_no
LIMIT 1;
```

**Scale pattern:** This single query replaces thousands of lines of procedural "design intelligence". Adding a new BOM = INSERT. No code change.

### 2.4 The Rule Engine (OO(n) Validation)
```java
public class ValidationEngine {
    private final Connection valConn;
    private final Map<String, List<ValRule>> ruleCache;
    
    public ValidationResult validate(OrderLineInstance instance) {
        // Cache rules by jurisdiction + element type
        List<ValRule> rules = ruleCache.computeIfAbsent(
            instance.getJurisdiction() + ":" + instance.getElementType(),
            k -> loadRules(instance)
        );
        
        // Check each rule (early exit on first BLOCK)
        for (ValRule rule : rules) {
            double actual = measure(instance, rule.getParameter());
            if (actual < rule.getMinValue()) {
                return ValidationResult.block(
                    rule, actual, 
                    String.format("%s: %.1f < minimum %.1f %s",
                        rule.getStandardRef(), actual, 
                        rule.getMinValue(), rule.getUnit())
                );
            }
        }
        return ValidationResult.pass();
    }
    
    private List<ValRule> loadRules(OrderLineInstance instance) {
        // Single query gets all applicable rules
        String sql = """
            SELECT r.*, p.param_name, p.param_value
            FROM ad_val_rule r
            LEFT JOIN ad_val_rule_param p ON r.ad_val_rule_id = p.ad_val_rule_id
            WHERE r.jurisdiction = ?
              AND (r.element_type_a = ? OR r.element_type_a IS NULL)
              AND r.is_active = 1
              AND (r.valid_from <= ? OR r.valid_from IS NULL)
              AND (r.valid_to >= ? OR r.valid_to IS NULL)
            """;
        // ... execute and build rules
    }
}
```

**Scale pattern:** Rules are loaded once per session and cached. Validation is O(number of rules per element type), typically < 50 checks. 50K elements × 50 checks = 2.5M operations, done in < 1 second.

### 2.5 The History System (Git for Buildings)
```java
public class HistoryManager {
    private final Connection conn;
    
    public void recordChange(String buildingId, String verb, 
                             Object before, Object after, Map<String, Object> params) {
        // Take JSON snapshots
        String beforeJson = before != null ? gson.toJson(before) : null;
        String afterJson = after != null ? gson.toJson(after) : null;
        String paramsJson = gson.toJson(params);
        
        // Insert node
        String sql = """
            INSERT INTO pp_order_node (
                pp_order_id, node_no, verb, before_state, 
                after_state, parameters, created_by
            ) VALUES (
                (SELECT pp_order_id FROM pp_order WHERE building_id = ?),
                (SELECT COALESCE(MAX(node_no), 0) + 1 FROM pp_order_node 
                 WHERE pp_order_id = (SELECT pp_order_id FROM pp_order WHERE building_id = ?)),
                ?, ?, ?, ?, ?
            )
            """;
        // ... execute
        
        // Generate commit hash (for external version control)
        String hash = generateCommitHash(buildingId, beforeJson, afterJson, paramsJson);
        updateCommitHash(buildingId, hash);
    }
    
    public BuildingState restore(String buildingId, int nodeNo) {
        // Get snapshot at that node
        String sql = """
            SELECT after_state FROM pp_order_node n
            JOIN pp_order o ON n.pp_order_id = o.pp_order_id
            WHERE o.building_id = ? AND n.node_no <= ?
            ORDER BY n.node_no DESC
            LIMIT 1
            """;
        // ... execute
        
        // Recompile from that snapshot
        return recompileFromSnapshot(buildingId, snapshotJson);
    }
}
```

**Scale pattern:** History is stored as deltas, not full copies. 10,000 changes might add 50MB total. "Restore to 6 months ago" is just recompiling from that node's snapshot.

---

## 3. The Network Protocol (ndjson over TCP)

```java
public class DesignerServer {
    private final ServerSocket serverSocket;
    private final ExecutorService threadPool;
    private final Map<String, ClientHandler> clients;
    
    public void start() {
        while (true) {
            Socket client = serverSocket.accept();
            threadPool.submit(new ClientHandler(client));
        }
    }
    
    private class ClientHandler implements Runnable {
        private final BufferedReader in;
        private final PrintWriter out;
        
        public void run() {
            String line;
            while ((line = in.readLine()) != null) {
                Request req = json.fromJson(line, Request.class);
                
                switch (req.getAction()) {
                    case "compile":
                        CompileResponse resp = compiler.compile(req);
                        out.println(json.toJson(resp));
                        out.flush();
                        break;
                        
                    case "watch":
                        // Register for auto-recompile notifications
                        watcher.register(req.getBuildingId(), this);
                        break;
                }
            }
        }
        
        public void notify(StatusMessage msg) {
            out.println(json.toJson(msg));
            out.flush();
        }
    }
}
```

**Scale pattern:** One server handles multiple Blender clients. Each client gets its own thread. Auto-recompile pushes updates to all watching clients. No polling, no HTTP overhead.

---

## 4. The Python Bridge (Thin Client)

```python
# bonsai_bim_designer/client.py
import socket
import json
import threading
from queue import Queue

class BIMDesignerClient:
    def __init__(self, host='localhost', port=9876):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.connect((host, port))
        self.reader = self.sock.makefile('r')
        self.writer = self.sock.makefile('w')
        self.queue = Queue()
        
        # Start listener thread
        threading.Thread(target=self._listen, daemon=True).start()
    
    def _listen(self):
        while True:
            line = self.reader.readline()
            if not line:
                break
            msg = json.loads(line)
            self.queue.put(msg)
    
    def compile(self, building_id, changes=None):
        req = {
            'action': 'compile',
            'buildingId': building_id,
            'changes': changes
        }
        self.writer.write(json.dumps(req) + '\n')
        self.writer.flush()
        
        # Wait for response (with timeout)
        return self.queue.get(timeout=30)
    
    def watch(self, building_id, callback):
        req = {
            'action': 'watch',
            'buildingId': building_id
        }
        self.writer.write(json.dumps(req) + '\n')
        self.writer.flush()
        
        # Start callback thread
        def _watch_loop():
            while True:
                msg = self.queue.get()
                if msg.get('type') == 'COMPILE_COMPLETE':
                    callback(msg)
        
        threading.Thread(target=_watch_loop, daemon=True).start()
```

**Scale pattern:** 400 lines of Python. Everything else is in Blender's existing API or the Java server. The client is just a parameter collector and result displayer.

---

## 5. The Data That Makes It All Work

### 5.1 By the Numbers

| Data Store | Size (TE-scale) | Queries per Compile | Growth Rate |
|------------|-----------------|---------------------|-------------|
| `component_library.db` | 500 MB (meshes) | Read: many, Write: none | Slow (new products) |
| `{PREFIX}_BOM.db` | 10 MB | Read: 1000s, Write: occasional | Medium (new templates) |
| `output.db` | 200 MB | Read: 0 (Bonsai reads), Write: 1 | Per compile |
| `validation.db` | 1 MB | Read: 1000s, Write: none | Slow (code updates) |
| `PP_Order_Node` | 50 MB | Read: 1 per undo, Write: 1 per action | Linear with edits |

### 5.2 The Compounding Effect

The magic isn't in any single table — it's in how they interact:

```
User edits bedroom width (ASI on C_OrderLine)
  → Validation checks against AD_Val_Rule (UBBL 33(1))
  → Pattern rules recompute window positions (ad_pattern_rule)
  → MEP reroutes around new window positions (Stage 6)
  → New output.db written with R-tree index
  → PP_Order_Node records the change
  → Bonsai receives COMPILE_COMPLETE via TCP
  → Viewport updates in <2 seconds

Next user with similar bedroom:
  → Selection cascade finds this ASI as a template
  → Library grows without new products
```

The system learns without being explicitly programmed. That's the scale.

---

## Summary: What Makes It Scale

| Concern | Solution | Why It Scales |
|---------|----------|---------------|
| **Product variety** | ASI overrides | 1 product = infinite variations |
| **Design repetition** | Pattern rules | 1 BOM line = 1000 instances |
| **Spatial queries** | R-tree index | O(n log n) instead of O(n²) |
| **Compliance** | Rules as data | Add jurisdiction = INSERT, not code |
| **History** | Delta snapshots | Unlimited undo with fixed storage |
| **Performance** | Incremental compile | 80% of edits take 2 seconds |
| **Network** | TCP + ndjson | One server serves many clients |
| **Learning** | Mining queries | Each building teaches the next |

This isn't just a BIM tool. It's a **learning system** that compounds knowledge across projects, jurisdictions, and users. That's the scale no one else can match.

