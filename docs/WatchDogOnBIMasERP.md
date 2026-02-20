# BIM as ERP: Watchdog Assessment and Constructive Vision

**Author:** Architectural Watchdog (Claude Sonnet 4.6)
**Date:** 2026-02-21
**Status:** Living document — update after each Phase BOM milestone
**Purpose:** Unfiltered architectural review of the BIM Intent Compiler's MRP BOM Drop pattern,
with watchdog concerns, scenario context, and constructive ideas from ERP/MRP/MFG practice.

---

## 0. The Paradigm Shift in One Sentence

> **Product + Schedule → Construction + Spaces**
> The assembly line doesn't change. The vocabulary does.

In iDempiere: a Sales Order triggers a Work Order which triggers MRP to net requirements
against inventory and produce a Production Order. BOM Drop copies BOM lines to Order lines.
User edits, confirms, execution begins.

In this compiler: a building DSL triggers a compilation pipeline which triggers a BOM Drop
to net room slots against room boundaries and produce element rules. User edits anchor rows,
confirms by recompiling, elements appear.

The machinery is identical. This is not emulation — it is the correct application of a
proven pattern to a new domain.

---

## 1. Watchdog Concerns

### 1.1 `family_ref` Dual-Use — MEDIUM RISK

**Current state:** `ad_element_rule.family_ref` carries two semantically different values:
- For BOM anchors: the `bom_id` string (e.g., `BED_SET`)
- For legacy Revit rows: the Revit family string (e.g., `M_Single-Flush:0762 x 2032mm`)

`RelationalResolver` detects BOM anchors by checking `family_ref` against the `bomIds` set
loaded from `ad_bom`. This works today, but silently fails if a Revit family string happens
to match a `bom_id` (unlikely but possible as the catalog grows).

**Mitigation (Phase BOM-2):** Split into `bom_ref` (nullable FK to `ad_bom.bom_id`) and
retain `family_ref` for catalog product lookup. Detection logic becomes
`rule.bomRef != null` instead of `ctx.bomIds().contains(rule.familyRef)`.

### 1.2 GGF Layer Missing — LOW RISK NOW, HIGH RISK AT SCALE

**Current state:** The BOM hierarchy has three working levels (Room BOM → Set BOM → Leaf).
The top two levels — GGF (complete building: `UNIT_DUPLEX_STD`) and GF (floor assembly:
`FLOOR_1_STD`) — are not yet defined in `ad_bom`.

**What this means today:** A new Triplex can be authored by specifying its rooms in
`ad_room_boundary` and relying on the BOM Drop. This works. But the user cannot write:

```dsl
BUILDING "Triplex_A" INHERITS UNIT_TRIPLEX_STD
```

and have the room layout auto-generated from a floor template. They must manually specify
each room boundary. The GGF layer is what makes floor plate reuse possible.

**Mitigation:** Phase BOM-2 defines `UNIT_DUPLEX_STD` and `FLOOR_1_STD` in `ad_bom`
with children pointing at room-type slots. A Triplex then reuses Duplex floor templates
with a `FURNISH_OVERRIDE` for the extra bedroom.

### 1.3 Three BOMs With 0 or Thin Children — DATA DEBT

| BOM | Children | Risk |
|-----|----------|------|
| `WARDROBE_SET` | 0 | Anchor rows drop silently — 3 rooms get nothing |
| `BATHROOM_VANITY_SET` | 2 | Only vanity + mirror; toilet and drain not included |
| `STUDY_SET` | 1 | Only desk; no chair, no shelving |

**These are correctness gaps, not design gaps.** The architecture is right. The catalog
is incomplete. Each needs `ad_bom_child` rows populated with products from `ad_product_dim`.
Gate: run `SELECT bom_id FROM ad_bom WHERE bom_id NOT IN (SELECT DISTINCT bom_id FROM ad_bom_child WHERE is_active=1)` before any Phase BOM milestone.

### 1.4 DX GeometryValidator 21 Pre-Existing Failures — KNOWN, NOT INTRODUCED

Duplex is a metadata building. `storey.walls()` is always empty (walls bypass BuildingSpec
for metadata buildings). All 20 DX rooms fail the "room fully enclosed" check. The pipeline
logs FAIL but does not throw. This is pre-existing — Phase BOM-1 did not introduce it.

**Mitigation:** Gate check is element count, not GeometryValidator. If DX ever moves to
a fully metadata-driven wall spec, the enclosure check will auto-pass. Track as Known Debt.

### 1.5 BOM Child Ordinal Collision Risk — LOW RISK

`computeBomAnchor()` generates ordinals as `baseId * 1000 + childIdx`. If a room has
more than 1000 BOM children, ordinals collide. Current max is `KITCHEN_CABINET_SET` at 12.
Safe for residential. Commercial kitchens or large assembly rooms could hit this.

**Mitigation:** Switch to UUID-based `element_ref` for BOM children, or use a global
sequence counter within the building compilation run.

### 1.6 Terminal No Room Boundaries — ROADBLOCK BEFORE BOM-1 CAN FIRE

Terminal has ~51K elements but no `ad_room_boundary` data. PlacementProver is currently
skipped for Terminal to avoid noise violations. Phase BOM-1 cannot fire for Terminal
until room boundaries are extracted.

**Mitigation:** Terminal room boundary extraction is a prerequisite for Terminal BOM Drop.
Extract from IFC spatial structure (IfcSpace + bounding geometry) before Phase BOM-3 begins.

---

## 2. What Scales Right

### 2.1 New Building = One SQL INSERT

Proven with four buildings (SH, DX, TB-LKTN, Terminal). A Triplex requires:
1. One INSERT in `ad_building_registry` (DSL content inline)
2. `ad_room_boundary` rows (extracted from drawings or declared in DSL)
3. Zero new Java files

BOM Drop fires automatically. All existing BOMs (BED_SET, LIVING_SET, KITCHEN_CABINET_SET)
apply to the new building's rooms by room_type match. This is the factory model working.

### 2.2 Parts Reuse Without Redefinition

SH and DX are the two proven "cars." TB-LKTN reuses their parts:
- `BED_SET` (proven in DX) → TB-LKTN bilik_utama, bilik_2, bilik_3
- `KITCHEN_CABINET_SET` (proven in DX) → TB-LKTN common room
- `DINING_SET` (proven in DX) → TB-LKTN common room

A Quadruplex reuses the same parts. A Studio apartment reuses a subset. The catalog is
additive — each new building type can contribute new BOMs to the library for reuse by others.

### 2.3 Mesh2Library as Genuine Lego Blocks

The sealed `ParametricMesh` interface enforces the Lego metaphor at the type level:

```java
sealed interface ParametricMesh
    permits GableRoofMesh, HipRoofMesh, FlatRoofMesh, CylindricalTankMesh, StairFlightMesh
```

Adding a butterfly roof or a folded plate canopy = new `permits` entry + new class + two
SQL INSERTs. The `ad_roof_preset` table (region × building_type → mesh_type) provides
automatic routing. Malaysian residential always gets GABLE_ROOF_MY. UK residential gets
HIP_ROOF_UK when that row is added. The CompilerContractTest blocks Python mesh scripts
from re-entering via any side door.

The connection to BOM is clean: a fabricated mesh leaf node uses `ad_bom_child_param`
for position and `ad_product_dim` for bounding box (generated by the mesh itself).
Same three-table authority rule. No special casing.

### 2.4 The Outliner Becomes the BOM Tree

Currently IFC viewers (including Bonsai) show a flat class tree:
```
IfcFurnishingElement
  ├── IfcFurnishingElement_23  (what is this?)
  ├── IfcFurnishingElement_24
  └── ...
```

The output DB already has `assembly_components` with parent-child relationships.
The IFC model can write `IfcRelAggregates` grouping BOM children under their assembly parent:

```
FURN
  └── BED_SET / bilik_utama
        ├── Bed_Queen (Bed_Queen_1)
        ├── Bedside_Table / left (Bedside_Table_1)
        └── Bedside_Table / right (Bedside_Table_2)
  └── LIVING_SET / common
        ├── Sofa_3Seat (Sofa_1)
        ├── Coffee_Table (Coffee_Table_1)
        └── TV_Cabinet (TV_Cabinet_1)
```

This is the IFC Outliner as a BOM tree. The `family_ref` = product catalog ID gives a
consistent, human-readable name regardless of which building is open. Every Duplex, Triplex,
or Quadruplex has the same Outliner structure for the same room types — because the BOM is
the same. This is the "same design and construction" fidelity the user asks for.

---

## 3. Scenario Applications

### 3.1 Social Housing Replication

Same building DSL, N different sites. Each site has its own `ad_room_boundary` coordinates
(different orientation, different grid origin). BOM Drop fires identically for all sites.
Differences come from `ad_element_rule` overrides per site (swap DINING_SET for CANTEEN_SET
in a shared housing block, for example).

In MRP terms: same Production BOM, different Work Order site. The BOM doesn't know about
site — the order (building) carries the site-specific overrides.

### 3.2 Renovation Variant

Building A is baseline. Building A_RENO has the same `ad_room_boundary` but with
`ad_element_rule` overrides:
- Deactivate old KITCHEN_CABINET_SET anchor row
- Insert new KITCHEN_CABINET_SET_PREMIUM anchor row
- Recompile → only the kitchen changes

In MRP terms: Engineering Change Order (ECO) on the Production BOM. Only affected work
orders (rooms) regenerate. Spatial digest changes only for the kitchen storey.

### 3.3 Regional Variants (My vs UK vs SG)

`ad_room_slot` has a `profile` column (Malaysian_Institutional already populated).
Malaysian residential gets TOILET_BACK_WALL_MY. UK residential gets TOILET_CISTERN_UK.
Same BOM structure, different leaf components per region.

`ad_roof_preset` routes GABLE_ROOF_MY for Malaysia, HIP_ROOF_UK for UK. Zero DSL change.
The building DSL declares `REGION MY` and the routing table does the rest.

### 3.4 Complex from Parts: Terminal

Terminal is a commercial/institutional truck on the same factory line. Once room boundaries
are extracted:
- `ad_room_slot` Malaysian_Institutional rows fire for OFFICE, LOBBY, TOILET_BLOCK
- MEP BOMs (FP_PIPE_ASSEMBLY, MEP_ROOM) already in catalog
- New commercial-specific BOMs (RECEPTION_SET, CANTEEN_SET already exists) added once

The incremental cost of Terminal as a building type is: room boundary extraction + any
missing commercial BOMs. The Java engine is unchanged.

### 3.5 Continuous Refactoring via SpatialDigest

Every compilation produces a `spatial_digest` (SHA256 of all element bounding boxes).
A BOM change (new child, updated offset) immediately changes the digest. CI can track:
- Did the digest change unexpectedly? → flag for review
- Did a known-bad room improve? → update Known Debt table
- Are all four buildings still passing 58/58? → green gate

This is the iDempiere change log concept: every BOM modification is auditable, every
compile produces a deterministic fingerprint.

---

## 4. Constructive Ideas from ERP/MRP/MFG Practice

### 4.1 BOQ as BOM Cost Roll-up (5D BIM)

Manufacturing BOM cost roll-up: leaf component costs × quantities → sub-assembly costs →
parent BOM cost → Work Order total.

BIM equivalent:
```
Leaf:    Bed_Queen unit cost × 1     = MYR 2,400
Set:     BED_SET material cost       = MYR 4,100  (bed + side tables + lamp)
Room:    bilik_utama installation     = MYR 4,800  (set + labour + MEP)
Floor:   Level 1 total               = MYR 38,000
Building: TB-LKTN complete           = MYR 185,000
```

The `cd_product_price` table (currently `ad_product_dim` + a price extension) carries
unit costs per region. `simple_qto` in the output DB already has element counts.
The cost roll-up is a JOIN away. This is 5D BIM (geometry + time + cost) from a BOM.

### 4.2 MRP Netting → Procurement Schedule

Manufacturing MRP nets demand (Sales Orders) against supply (inventory) to produce
purchase orders for what's short.

BIM MRP equivalent:
- Demand: `ad_element_rule` BOM anchor rows × `FurnitureBOMResolver` child counts
- Supply: `ad_product_dim` catalog (what exists to be specified)
- Net requirement: what's missing in the catalog but demanded by the building
- Output: procurement list for the QS (Quantity Surveyor)

Running `SELECT family_ref FROM element_instances WHERE family_ref NOT IN (SELECT product_id FROM ad_product_dim)` on the output DB produces the net requirement list. Products not in catalog = items to procure or add to the library.

### 4.3 Phantom BOM for Floor Plate Templates

Phantom BOM = a grouping BOM with no physical geometry itself; only its children materialise.

In manufacturing: `TYPICAL_FLOOR_TEMPLATE` is a phantom. It doesn't produce a physical item.
It just groups the floor's children for cost attribution and scheduling.

BIM application: Define `FLOOR_1_STD` as a phantom BOM grouping rooms:
```sql
INSERT INTO ad_bom VALUES ('FLOOR_1_STD', 'Floor 1 Standard', 'PHANTOM', NULL, 1, 'EXTRACTED_DX');
INSERT INTO ad_bom_child VALUES (NULL, 'FLOOR_1_STD', NULL, 'BEDROOM_SLOT', 'BEDROOM', 1, 1, 'EXTRACTED_DX');
INSERT INTO ad_bom_child VALUES (NULL, 'FLOOR_1_STD', NULL, 'LIVING_SLOT',  'LIVING',  2, 1, 'EXTRACTED_DX');
```

The phantom carries no geometry. `FurnitureBOMResolver` skips phantom parents and resolves
their children directly. New floor type = new phantom BOM + new children. Zero Java change.

### 4.4 Variant BOM → `BEDROOM_DELUXE` vs `BEDROOM_STANDARD`

Libero Manufacturing variant configuration: same parent BOM, different children depending
on the variant selected.

BIM equivalent: `BED_SET` is standard. `BED_SET_MASTER` is the deluxe variant (already in
catalog with 4 children vs 5). The DSL `FURNISH SET BED_SET_QUEEN` override maps to
`bt_room_slot_override` in the target data model.

Scale to full variant configuration: a `BEDROOM_PENTHOUSE` variant adds walk-in wardrobe,
en-suite layout override, and premium fixtures — all from a variant BOM row, no Java change.

### 4.5 Serial Number Tracking → IFC GUID Traceability

Manufacturing: every Work Order output gets a serial number for traceability.

BIM: every element_ref is a serial number. The compilation `spatial_digest` is the
production lot number. For a delivered building:
- `element_ref` = which catalog component was specified at which location
- `family_ref` = which product it is (the serial number's product code)
- `spatial_digest` at RELEASED status = the as-built fingerprint

If a product recall is issued for FURN_BED_DOUBLE, a query against `element_instances`
finds every building that contains it. This is manufacturing traceability applied to BIM.

### 4.6 Engineering Change Order → BOM Versioning

When a product is updated (new mattress depth, new wardrobe standard size), the BOM change
should be traceable:

```sql
ALTER TABLE ad_bom ADD COLUMN version INTEGER DEFAULT 1;
ALTER TABLE ad_bom_child ADD COLUMN effective_from DATE;
ALTER TABLE ad_bom_child ADD COLUMN effective_to DATE;
```

Buildings compiled before the ECO retain the old BOM version in their compile record.
Buildings compiled after use the new version. `sys_changelog` records the transition.
This prevents retroactive changes from invalidating delivered buildings.

Not needed today — but the hook for it is `spatial_digest`. If a re-compile of a
RELEASED building produces a different digest, an ECO has changed the BOM.

### 4.7 Capacity Planning → Space Utilisation Reports

Manufacturing capacity planning: can the factory floor produce N units given its
machine hours and worker capacity?

BIM spatial equivalent:
- Each room has a `max_occupancy` from `rd_code_constraint` (UBBL Part III)
- BOM Drop assigns MEP based on occupancy (rd_mep_sizing fixture_units)
- `placeHVAC(ctx, includeDucted)` already differentiates residential vs commercial
- The next step: `ad_space_dim` (6 rows, currently unused) carries dimensional constraints
  per room type — wire it to validation to flag under-sized rooms before compile

This makes the compiler a compliance pre-checker: before any geometry is written,
the BOM knows whether the rooms can legally receive the BOM's assemblies.

### 4.8 The Bonsai BOM Drop Editor — The Killer Application

Bonsai (Blender IFC addon) visualises IFC geometry. The natural next layer:

**Tab 1: Building (the Order)**
- Drop-down: select building from `ad_building_registry`
- Status: DRAFT / VALIDATED / RELEASED
- Action button: Compile (triggers `CompilationPipeline`)

**Tab 2: Rooms (the Order Lines)**
- List of rooms from `ad_room_boundary`
- For each room: room_type, area, assigned BOM assemblies from `ad_element_rule`
- Edit: change room_type → BOM Drop re-fires for that room only

**Tab 3: Slot Overrides (the Line Edits)**
- For each BOM anchor row: assembly assigned (BED_SET, LIVING_SET, etc.)
- Override: swap assembly from catalog dropdown
- Remove: deactivate anchor row (room gets no furniture of that type)
- Add: insert new anchor row (extra set, e.g., STUDY_SET in a large bedroom)

**Tab 4: Preview (live compile)**
- Click Compile → IFC updates in Bonsai viewport
- Outliner shows BOM tree (FURN > BED_SET/bilik_utama > children)
- Prover results show P01-P22 status inline

This editor doesn't require any architectural knowledge. The user sees:
*"Bedroom: BED_SET (5 items) — VALID"*
Not: *"IfcFurnishingElement_23 at (3.2, 1.8, 0.0)"*

The paradigm shift is complete: the editor is an order management system, not a CAD tool.

---

## 5. Roadmap Confidence Assessment

| Milestone | Confidence | Blocker |
|-----------|-----------|---------|
| Phase BOM-2: GGF+GF parent BOMs | High | `family_ref` split + `ad_bom` parent rows |
| Phase BOM-2: family_ref normalisation for 261 DX rows | High | Mapping Revit strings → product IDs |
| Terminal BOM Drop | Medium | Requires room boundary extraction first |
| New complex (Triplex, quadruplex) from existing parts | High | Zero new Java needed |
| Regional variant (UK/SG residential) | High | `ad_room_slot` profile + `ad_roof_preset` |
| Mesh2Library new shapes (hip, flat, barrel) | High | Sealed interface extension |
| Bonsai BOM Drop Editor (Tab 1-3) | Medium | Phase F after typed domain records |
| BOQ 5D cost roll-up | High | `cd_product_price` rows + roll-up query |
| BOM versioning + ECO tracking | Low (future) | Not needed until first delivered building |

---

## 6. Comparison with Existing BIM Tools

All major BIM authoring tools are **recorders** — they capture what the user manually places.
The BIM Intent Compiler is a **compiler** — it derives placement from rules. That gap is the
key differentiator.

| Tool | BOM concept | Room-level auto-dispatch | New building cost | Intent vs Record |
|------|-------------|--------------------------|-------------------|------------------|
| **Autodesk Revit** | Schedules (query, not explosion) | None — manual family placement | New project + manual layout | Recorder |
| **ArchiCAD** | Object parameters, no assembly hierarchy | None — GDL objects placed manually | New project + manual | Recorder |
| **Tekla Structures** | Fabrication BOM for steel assemblies | Structural only, not architectural | New model + manual | Partial compiler (structural) |
| **Vectorworks** | Record formats per object | None | New file + manual | Recorder |
| **Rhino + Grasshopper** | Parametric definitions, no catalog | Script-driven, brittle | New script per project | Scripted recorder |
| **Bonsai (BlenderBIM)** | IFC-native, no BOM layer | None — direct IFC editing | New IFC file | Recorder |
| **Allplan** | Some prefab assembly support | None at room level | New project | Recorder |
| **Speckle / BIMcollab** | Data streaming / issue tracking | Not applicable | Not applicable | Infrastructure, not authoring |
| **This compiler** | Full MRP BOM hierarchy, 5 levels | `ad_room_slot` auto-dispatch | One SQL INSERT, zero Java | **Compiler** |

**Key observations:**

- **Revit Schedules are queries, not BOM explosion.** A Revit schedule counts what exists.
  The BOM Drop creates what should exist from rules. These are opposite directions.

- **Tekla is the closest relative** — it has genuine fabrication BOMs for structural steel
  and generates CNC fabrication data from them. The BIM Intent Compiler applies the same
  concept to all disciplines (architectural, MEP, furniture) with a room-slot dispatch layer
  that Tekla does not have.

- **Grasshopper scripts are the common workaround** in Revit/Rhino practices for parametric
  placement. They are project-specific, brittle, and not catalog-backed. The Mesh2Library
  sealed interface and `ad_parametric_mesh_param` table replace ad-hoc scripts with a typed,
  provenance-tracked, compiler-enforced equivalent.

- **No existing tool has the concept of `ad_room_slot`** — a standing rule that says
  "every BEDROOM gets BED_SET, every LIVING room ≥6m² gets DINING_SET." This is the
  MRP planning rule applied to construction. In Revit, this knowledge lives in the
  architect's head and is re-executed manually on every project.

- **The Bonsai BOM Drop Editor** described in §4.8 would be the first tool in this space
  to present BIM authoring as order management. The user configures what they want; the
  compiler determines where it goes. This inverts the current industry workflow where the
  user must know both intent and placement to author a valid model.

---

## 7. The Verdict

The architecture is not merely emulating iDempiere. It is applying the correct pattern
from a proven domain (manufacturing ERP) to a new domain (construction compilation).
The key validation: a new building type requires one SQL INSERT and zero Java files.
That is the exact test iDempiere uses to prove its AD architecture works.

The risks are data completeness risks (WARDROBE_SET has 0 children, Terminal has no room
boundaries), not architectural risks. The machinery is correct. The catalog is being built.

When TB-LKTN and Terminal both compile seamlessly under the same BOM Drop flow, the
Bonsai editor becomes the natural next unlock. The factory floor is ready. The trucks just
need their parts lists completed.

> *"A building is an order. Space is the product. The BOM is the construction knowledge.
> The DSL is the order entry form. The compiler is the production line."*

---

*Watchdog sign-off: Architecture approved for continuation. Phase BOM-2 is the critical path.*
