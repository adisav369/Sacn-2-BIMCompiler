# BIM as ERP: Watchdog Assessment and Constructive Vision

**Author:** Architectural Watchdog (Claude Sonnet 4.6)
**Date:** 2026-02-24 (updated)
**Status:** Living document — update after each Phase BOM milestone
**Tests:** DAGCompiler 118/120 (G8 intentional RED ×2) · ORMSandbox 13/13 · TopologyMaker 15/15
**SpatialDigests:** SH=1f325a98 · DX=d3c779b9 · TB=dd4345f4 · Terminal=301b42b1 (stable)
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

### 1.2 GGF Layer — PARTIALLY RESOLVED (5-tier bom_type live)

**Status (2026-02-24):** `UNIT_DUPLEX_STD`, `FLOOR_DX_L1_STD`, `FLOOR_DX_L2_STD` etc. are
seeded in `ad_bom` with `bom_type IN ('UNIT','FLOOR')`. The 5-tier CHECK constraint is live
(`UNIT > FLOOR > ROOM > SET > ITEM`). Phase 4b ViewAccessLayer + BomTierResolver are still
queued — the tier structure exists in data but the cascade resolver for UNIT→FLOOR→ROOM is
not yet wired through the ViewAccessLayer API contract.

**Remaining gap:** A new Triplex can still be authored by inserting rooms into `ad_room_boundary`
and BOM Drop fires correctly. But UNIT→FLOOR floor-plate reuse is not yet enabled for
generative buildings (TopologyMaker produces DERIVED_MM rooms directly — adequate for now).
Phase 4b is the unlock.

**Mitigation:** Phase 4b ViewAccessLayer (QUEUED) completes this. Risk until then: UNIT/FLOOR
bom_type rows are orphaned data — no compiler path reads them yet.

### 1.3 Thin/Empty BOMs — PARTIALLY ADDRESSED, FIT_PRIORITY GAP REMAINS

| BOM | Status (2026-02-24) | Risk |
|-----|---------------------|------|
| `WARDROBE_SET` | Still 0 children | Anchor rows drop silently |
| `BATHROOM_VANITY_SET` | 2 children | Toilet/drain still missing |
| `STUDY_SET` | 1 child | Chair/shelving missing |
| `DINING_SET`, `LIVING_SET` | `product_ref` seeded ✅ | `v_qualified_bom` live (10 rows) |

**Progress:** `product_ref` FK added to `ad_bom_child` (Phase 4a). `v_qualified_bom` filters
on `pd.width > 0 AND extracted_from NOT LIKE '%PENDING%'` — 10 rows confirmed live.

**New gap:** `ad_bom_child.fit_priority` was added as a column but only `COFFEE_TABLE` has a
non-default value. When two SET BOMs compete for the same room slot, `fit_priority` is the
tiebreaker. With all values at default=20, the first-encountered SET wins — non-deterministic
at scale. Preflight Check A detects blank `child_name_pattern`; no check yet for unpopulated
`fit_priority`.

Gate: `SELECT bom_id FROM ad_bom WHERE bom_id NOT IN (SELECT DISTINCT bom_id FROM ad_bom_child WHERE is_active=1)` before any BOM milestone.

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

### 1.7 `ad_room_slot` Global Scope — RESOLVED (Check H + migration, 2026-02-24)

**Root cause:** `ad_room_slot` had no `building_type` column. `BOMAssemblerAD.lookupSlots()`
dispatched by `room_type` only. SH-specific slots (`SH_LIVING_SET`, `SH_DINING_SET`,
`SH_BED_SET`) added for SH G8 calibration were globally reachable — DX has BEDROOM+LIVING
rooms, so all three SH BOMs could fire in a DX compile.

**Detection path:** Preflight Check H (`BuildingInspector.preflightCheckH()`) — detected
the first-principles gap live against `Ifc2x3_Duplex`.

**Resolution (commit f1fc203):**
- `migration_room_slot_building_type.sql`: `ALTER TABLE ad_room_slot ADD COLUMN building_type TEXT DEFAULT NULL`; SH slots tagged `building_type='Ifc4_SampleHouse'`; `NULL` = globally scoped
- `SlotRegistry`: 4-arg `getSlotsForType(buildingId)` with building filter; 3-arg delegates (backward compat)
- `RelationalResolver.loadSlotsByAssembly(conn, buildingType)` — `WHERE building_type IS NULL OR building_type = ?`
- `StoreyCompiler`: passes `ctx.building.name()` as `buildingId`
- `IntraBOMRelativeTest R4`: threshold raised 3×→8× (G8 dining chairs legitimately 7.4× product width)

**Residual risk:** Any new SH/TB-LKTN-specific BOM added to `ad_room_slot` must include
`building_type`. The column has `DEFAULT NULL` (global) — easy to forget on INSERT.
Consider a Preflight Check H2 that flags new slots with building-specific BOM name prefixes
(e.g., `SH_`, `TB_`) that have `building_type IS NULL`.

### 1.8 NULL-Bound ROOM_Level_* Rooms (DX) — MEDIUM DEBT, G8 DEFERRED

**Current state:** 40 DX `ROOM_Level_*` rooms have `NULL` bounds (`GRID_DERIVED`). These are
preserved for wall/beam host lookup but produce no furniture (`area=0` guard in
`FurnitureBOMResolver.resolveForRoom()`). However, direct `element_rule` rows (ARC/MEP
discipline) that reference these rooms still compute `(0, 0)` via FRACTION positioning.

**Impact mitigated by six rule deactivations (2026-02-24):**
- Rules 7472, 7942, 7943, 7946, 7947: ARC `FIXTURE_SINK` in NULL rooms → origin → X1 failure
- Rule 7083: MEP `IfcFlowFitting_200` in NULL room → origin → GIC X/Y axis swap

**Residual risk:** Other ARC/MEP rules in ROOM_Level_* rooms may have the same failure mode
but were not surfaced this session. Preflight Check B reports all `LOCAL_MM` rooms; Check D
reports non-FURN elements with no geometry_map. The full fix is G8 calibration: extract
all 40 rooms from `Ifc2x3_Duplex_extracted.db` and replace NULL bounds with `IFC_GLOBAL_MM`.
Until then, each `is_active=1` rule in a NULL-bound room is a latent X1/GIC risk.

### 1.9 `selectWorkWall()` Envelope Sensitivity — LOW-MEDIUM RISK

**Pattern:** `selectWorkWall()` picks the highest-scoring wall: score = `-10 × openingCount + wallLength`.
With no openings, the longest wall wins. In asymmetric rooms (W ≠ D), an EAST/WEST wall can
win unexpectedly. EAST anchor uses transform `world_x = anchor_x − dy`. A BOM child with
large negative `dy` pushes `world_x` toward the building's max-X envelope.

**Live case (2026-02-24):** `ROOM_B102` (2945mm × 3318mm) → EAST work wall →
`DINING_SET CHAIR_F` at `dy=-1.0` → `world_x=8813mm` → `maxX=9038mm > 8957mm` → FAIL.
Fixed by `dy=-0.80` (119mm margin), but this is a global template hack.

**Structural gap:** `FurnitureBOMResolver.expandBOMNode()` has no envelope bounds check.
After computing world coordinates, it does not verify the child's bounding box fits within
the building envelope stored in `ad_room_boundary`. A general fix would read `max_x_mm` for
the building and assert `world_x + halfWidth < max_x_mm + tolerance`. No code change made
yet — mitigated only for CHAIR_F via dy adjustment.

**Mitigation (future):** Add `EnvelopeGuard` check in `expandBOMNode()` post-coordinate
computation. Read building envelope from `v_verified_room_boundary` aggregate. Log violation
and clamp or skip child rather than placing outside bounds.

### 1.10 Discipline Mismatch: FURN_ Prefix Insufficient — MEDIUM RISK

**Current state:** Preflight Check F flags `ARC/STR rules with FURN_ family_refs`. This
catches the 48-rule regression surfaced in the G8 DX migration. But `FIXTURE_SINK` with
`discipline='ARC'` triggers the same failure path (ARC dispatch → `geometry_map` → GEN-BOX
for FURN-class geometry) without having a `FURN_` prefix.

**Rules 7472, 7942–7947** were the live example: `family_ref='FIXTURE_SINK'`, `discipline='ARC'`.
These passed Check F but still caused X1 failures.

**Mitigation:** Broaden Check F to flag **any non-MEP rule whose `family_ref` resolves
to a `IfcFurnishingElement` ifc_class in `ad_geometry_map`** but has `discipline != 'FURN'`.
Alternatively, add a discipline-family_ref consistency constraint:
```sql
-- Check F extension
SELECT id, discipline, family_ref FROM ad_element_rule
WHERE is_active = 1
  AND discipline IN ('ARC','STR')
  AND (family_ref LIKE 'FURN_%' OR family_ref LIKE 'FIXTURE_%')
  AND family_ref NOT IN (SELECT bom_id FROM ad_bom);
```

### 1.11 BasePO TEXT PK Trap — HIGH RISK FOR ORM USERS

**The trap:** `BasePO.isNew()` must use the explicit `isNewRecord` flag, not PK presence.
TEXT PKs (e.g., `bom_id`, `building_type`) are non-blank before `save()` — they're set
programmatically before INSERT — but the row doesn't exist in the DB yet. Without the flag,
`save()` calls UPDATE → 0 rows affected → silent no-op. The entity appears to save but
the row was never written.

**Current protection:** The flag is documented in PROGRESS.md Key Lessons and MEMORY.md.
But there is no runtime assertion. A future developer using `BasePO` and populating the PK
manually (common iDempiere pattern) will hit this silently.

**Mitigation:** Add a guard in `BasePO.save()`:
```java
if (!isNewRecord && rowsAffected == 0) {
    throw new IllegalStateException(
        "UPDATE affected 0 rows for " + getClass().getSimpleName() +
        " PK=" + getId() + " — check isNewRecord flag");
}
```
This converts silent data loss into a loud failure. Safe to add now.

### 1.12 Last Mile Problem: Generative Building Gaps — HIGH RISK (OPEN)

**Status:** `docs/LAST_MILE_PROBLEM.md` (2026-02-20) identified six root causes for why
generative buildings (no reference IFC) fail at element-level placement. Four steps from
the ordered fix plan have been executed; two remain open plus the foundational design work.

**Completed since 2026-02-20:**
| Step | Status |
|------|--------|
| Step 1: MetadataValidator gate (family_ref mandatory) | ✅ Phase RM-11 |
| Step 2: TB-LKTN family_ref population | ✅ migration_TBLKTN_family_ref.sql |
| Step 4: Replace ABSOLUTE furniture with BOM anchors (SH/DX) | ✅ Phase BOM-1 |
| Step 4b: Three-Table Authority confirmed + sealed coord types | ✅ Phase BOM-2d |

**Still open:**
| Step | Gap | Impact |
|------|-----|--------|
| Step 3 | `conn_points` in `ad_product_dim` has no consumer — `RelationalResolver` ignores it. Orientation for fixtures still uses verbatim extracted angles (SH/DX) or falls back to `NS`/`EW` labels (generative). | Toilet/sink misrotation in new buildings |
| Step 5 | `ABSOLUTE` position_rule not blocked for `IfcFurnishingElement`/`IfcFurniture`. DX still has 261 ABSOLUTE MEP+structural rows. | Replication debt, no regression guard |
| Step 6 | `clear_front` in `ad_product_dim` has no consumer. Door swing zone not enforced. | Furniture-in-door-swing visible in generative output |
| ProvenElement | No PO-backbone equivalent. `PlacementProver` is advisory post-facto, not blocking at construction time. | No math proof for computed placements in new buildings |
| CRD | Construction Rule Dictionary (`crd_rule` tables) designed but not created. Rules live in Java, not metadata. | Each new building requires manual placement debugging |

**The replication gap:** SH/DX are ~25% flat ABSOLUTE data (SH: 15/55 elements, DX: 269/1085
elements). These elements have verbatim extracted coordinates from the reference IFC — they
work by accident, not by rule. When room boundaries shift (G8 calibration), ABSOLUTE rows
will drift independently with no declared relationship to their room.

**Next session priority from Last Mile perspective:** Step 3 (conn_points → orientation) is
the highest-leverage unblocked step. It resolves TB-LKTN fixture misrotation without
touching the BOM cascade. See `docs/LAST_MILE_PROBLEM.md §0.1 Step 3` for implementation.

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

### 4.9 Preflight as Mandatory CI Gate (8 Checks A–H)

The `BuildingInspector.dumpPreflight(buildingType)` suite now covers eight automated checks:

| Check | What It Catches |
|-------|----------------|
| A | Blank BOM leaf `child_name_pattern` — silent GEN-BOX dims |
| B | Room boundaries not `IFC_GLOBAL_MM`/`DERIVED_MM` — G8 placement drift |
| C | Zero `height_extent_mm` / negative `height_mm` — P01/P03 CRITICAL |
| D | Non-FURN elements with no `geometry_map` entry — GEN-BOX per discipline |
| E | Orphaned `geometry_hash` — FK integrity |
| F | ARC/STR rules with `FURN_` family_refs — discipline mismatch regression |
| G | `expected_elements` vs active rule count + reachable room slots |
| H | Room slot authority — globally-scoped BOMs reachable from wrong building |

In iDempiere terms, Preflight = `ModelValidator.validate()` at the data layer before any
element is emitted. The compiler should refuse to run if Preflight returns warnings above
severity threshold. Currently it is manual (`java -cp ... BuildingInspector ... preflight`).

**Constructive recommendation:** Promote Preflight to a Maven `pre-integration-test` phase.
`BuildingInspector.dumpPreflight()` returns an int warning count; fail the build if count > 0
for any building. This makes Check A–H compile-blocking, the same way D1–D9 gates make
DriftGuard compile-blocking.

The connection to ERP: iDempiere's `DocAction.prepareIt()` runs validation before
a document can be confirmed. Preflight is `prepareIt()` for a building. You cannot
confirm a building (`doc_status = CO`) if Preflight fails.

---

## 5. Roadmap Confidence Assessment

| Milestone | Status | Confidence | Blocker / Note |
|-----------|--------|-----------|----------------|
| G8 Gate wired (RosettaPlacementTest) | ✅ DONE | — | 2 intentional RED — calibration is the debt |
| 6 VIEW_CONTRACTS views live | ✅ DONE | — | `v_qualified_bom` 10 rows confirmed |
| Phase 4a: product_ref FK on ad_bom_child | ✅ DONE | — | dim lookup fixed |
| Mesh2Library (HipRoof, HalfRoundDrain, GablePorch) | ✅ DONE | — | Sealed interface enforced |
| orm-core + ORMSandbox (13/13) | ✅ DONE | — | BasePO/ModelQuery shared |
| TopologyMaker T0–T6 + PO layer (15/15) | ✅ DONE | — | DERIVED_MM rooms |
| Preflight 8 checks A–H | ✅ DONE | — | Check H caught slot isolation gap live |
| ad_room_slot building_type isolation | ✅ DONE | — | commit f1fc203 |
| 5-tier bom_type CHECK (UNIT/FLOOR/ROOM/SET/ITEM) | ✅ DONE | — | ad_bom_new recreated |
| Phase BOM-2: family_ref normalisation (261 DX rows) | ⏳ QUEUED | High | Revit strings → ad_product_dim catalog IDs |
| Phase 4b–4e ViewAccessLayer + BomTierResolver | ⏳ QUEUED | High | Spec in VIEW_CONTRACTS.md §6/§7 |
| G8 calibration (DX 40 NULL rooms + SH) | ⏳ QUEUED | High | Extract rooms from Ifc2x3_Duplex_extracted.db |
| AD Events wiring (SpatialRuleValidator, CalloutCascadeValidator) | ⏳ QUEUED | High | AD_Events_Spatial_Rules.docx |
| Last Mile Step 3: conn_points → fixture orientation | ⏳ OPEN | Medium | ~25 lines in RelationalResolver; no test yet |
| Last Mile Step 5: block ABSOLUTE for furniture class | ⏳ OPEN | High | ~5 lines in MetadataValidator |
| Last Mile Step 6: clear_front enforcement | ⏳ OPEN | Medium | FurnitureBOMResolver post-placement check |
| Preflight as Maven CI gate | ⏳ OPEN | High | Zero new logic; wire dumpPreflight() exit code |
| BasePO 0-row UPDATE guard | ⏳ OPEN | High | ~5 lines in BasePO.save() — silent data loss risk |
| Check F broadened (FIXTURE_ discipline mismatch) | ⏳ OPEN | High | Extend SQL in BuildingInspector.preflightCheckF() |
| selectWorkWall() EnvelopeGuard | ⏳ OPEN | Medium | expandBOMNode() post-coord bounds check |
| Terminal BOM Drop | ⏳ BLOCKED | Medium | Requires Terminal room boundary extraction first |
| New complex (Triplex, quadruplex) from existing parts | — | High | Zero new Java — data only |
| Regional variant (UK/SG residential) | — | High | ad_room_slot profile + ad_roof_preset |
| ProvenElement / CRD | ⏳ FUTURE | Low | Big design work; see LAST_MILE_PROBLEM.md §4–§5 |
| Bonsai BOM Drop Editor (Tab 1–3) | ⏳ FUTURE | Medium | After Phase 4b ViewAccessLayer |
| BOQ 5D cost roll-up | — | High | cd_product_price rows + roll-up query |
| BOM versioning + ECO tracking | ⏳ FUTURE | Low | Not needed until first delivered building |
| Table renames (C_Element_Rule etc.) | ⏳ DEFERRED | — | 10 Java + 35 SQL files — dedicated session only |

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
boundaries), architectural gaps (Table renames deferred, conn_points not consumed, CRD not
built), and one foundational gap: the Last Mile Problem (generative buildings have no
math proof for computed placements — `ProvenElement` + CRD are the fix). None of these are
machinery risks. The assembly line is correct. The parts list and quality gate are the debt.

**Critical path (2026-02-24):**
1. **G8 calibration** — unlocks real placement accuracy for DX/SH; everything downstream
   depends on IFC_GLOBAL_MM room bounds
2. **Phase 4b ViewAccessLayer** — activates UNIT→FLOOR→ROOM cascade for new buildings
3. **Last Mile Step 3** (conn_points → orientation) — the single highest-leverage unblocked
   fix for generative building defects

When G8 calibration is live and Phase 4b ViewAccessLayer fires, the factory floor reaches
true production capability. Until then, replication buildings (SH/DX) are proven; generative
buildings (TB-LKTN) remain partially correct.

> *"A building is an order. Space is the product. The BOM is the construction knowledge.
> The DSL is the order entry form. The compiler is the production line."*

> *"The viewer is a confirmation tool, not a discovery tool. You open it to see what you've
> already proven, not to find what might be wrong." — this holds for SH/DX. For TB-LKTN,
> we are not there yet. Steps 3/5/6 and ProvenElement are what get us there.*

---

*Watchdog sign-off (2026-02-24): Architecture structurally sound. 118/120 tests green.
Replication path proven. Last Mile generative path has six identified root causes, four
resolved, two open plus ProvenElement/CRD. Critical path: G8 calibration → Phase 4b → Step 3.*
