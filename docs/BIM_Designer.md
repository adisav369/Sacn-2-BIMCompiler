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

**BOM factorization note:** The current TE BOM has 48,428 LEAF placement
lines for 505 unique products (factorization ratio 95.9×). Each line is one
element instance with its own dx/dy/dz — an unfactored EN-BLOC extraction.
A properly factored BOM would express repeating patterns as formulas with
quantities (e.g. "20 plates at 600mm spacing"), reducing 34K lines to ~20
entries. This factorization (TE-6 TILE SURFACE compression) is a prerequisite
for the designer — you cannot visually edit 48K individual lines, but you can
edit 20 pattern formulas. See [ConstructionAsERP.md](ConstructionAsERP.md)
§11 for the BOM dimension model.

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

Phase G tasks: addon scaffold, typology dropdown, DSL template generation,
compiler invocation + DB refresh, parameter sliders with constraint ranges,
block swap via viewport selection, site terrain panel, budget tier + witness
status display.

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

*Related docs:
[BOMBasedCompilation.md](BOMBasedCompilation.md) (compilation method, tack convention) |
[ConstructionAsERP.md](ConstructionAsERP.md) (3-DB architecture, three-concern lock) |
[BIM_COBOL.md](BIM_COBOL.md) (verb language spec) |
[DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) (pipeline, DAO pattern, EntityType) |
[InfrastructureAnalysis.md](InfrastructureAnalysis.md) (bridge/road/rail domain mapping)*
