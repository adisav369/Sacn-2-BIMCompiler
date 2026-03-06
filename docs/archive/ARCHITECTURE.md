# BIM Compiler Architecture

**Version:** 3.0
**Date:** 2026-02-08
**Status:** Historical — redirect to `METADATA_DRIVEN_ARCHITECTURE.md` for current architecture

**Supersedes:**
- `bim-compiler-architecture-evolution.md` (Jan 2025 — factory pattern proposal)
- `BUILDING_AS_BOM_CONCEPT.md` (Feb 2026 — BOM POC, now integrated)

> **Staleness note (2026-02-26):** This document predates Phase G-1 (type-blind BOM).
> Class references like `FurnitureBOMResolver`, `FixturePlacer`, `FurnitureTypeResolver`
> refer to **deleted** code. Current equivalents: `BOMTierResolver` (unified resolver),
> `FurnitureWorker` (BundleWorker impl), `BOMTreeLoader` (tree infrastructure).
> `ad_room_slot` is deprecated by `bom_category` on M_BOM.
>
> **Canonical references:**
> - **`docs/ConstructionAsERP.md`** — C_Order model, 1D Intent (§3.7), geometry chain (§5.5), ST mode TODOs
> - **`docs/METADATA_DRIVEN_ARCHITECTURE.md`** — domain architecture, phase roadmap (§12), abstract engine vision (§13)

**Founding reference:** `docs/ARCHIVE_intent_compiler_method.md` (READ-ONLY)

---

## 1. Founding Principles

### 1.1 EXTRACT OR COMPILE ONLY

The compiler's source of truth is the **federated model database** — a real building, measured, baked, and stored in SQLite. Every type, relationship, dimension, and placement rule must trace back to extracted data or be computed by the compiler (via BIM COBOL verbs). Never invent.

### 1.2 Construction is a Compilation Problem

```
Intent Document → Compiler → BIM Database → Viewer
```

This mirrors: Source Code → Compiler → Bytecode → VM. The DSL is the source language. The compiler enforces construction logic. The output database is the bytecode. The viewer is the debugger.

### 1.3 DSL Selects. BOM Parameterises. Java Resolves.

This is the governing separation of concerns, proven in Phase 85:

| Layer | Who Edits | Responsibility | Changes by |
|-------|-----------|---------------|------------|
| **DSL** | Layman end-user | Selects from catalog — an **OSGI-style MANIFEST** | User edits `.bim` file |
| **BOM metadata** | Hobbyist expert | Defines all detailing (BOM recipes, wall types, room slots) | SQL editor on BOM.db |
| **Java** | Developer | Resolves final coordinates (`resolveZ()`) | Code change (rare) |

Change a parameter in the database, recompile, all instances update. No code change.

#### The DSL is a Catalog Selector (Phase 117)

The DSL is **intentionally simple** — a layman picks from a catalog, like selecting items from a shopping list. All detailing lives in the metadata DB, curated by domain experts. The DSL **never invents** new types, dimensions, or assemblies — it only references things that already exist in the catalog.

**OSGI MANIFEST analogy:**

| OSGI Concept | BIM Compiler Equivalent |
|---|---|
| `Bundle-SymbolicName` | `BUILDING "name"` |
| `Require-Bundle` | `floor_bom:TYPICAL_CONDO_FLOOR` |
| `Import-Package` | Room types (`BEDROOM`, `KITCHEN`, etc.) |
| `Bundle-Version` | `profile:Malaysian_Residential` |
| `Export-Package` | Output DB (compiled building) |

**Enforcement:** `CatalogValidator` (Phase 117) checks every DSL reference against `BOM.db` at parse time. Unresolved references produce warnings — the compiler never silently invents what the catalog doesn't have. See `com.bim.compiler.contract.CatalogContract`.

---

## 2. The Application Dictionary Pattern

### 2.1 Origin: iDempiere ERP

The Application Dictionary (AD) is the core architecture of the iDempiere ERP system: metadata tables define windows, fields, validation rules, and workflows. The application reads AD tables at runtime — adding a new business object requires SQL inserts, not Java code.

**iDempiere parallel:**

| iDempiere | BIM Compiler |
|-----------|-------------|
| `AD_Table` | `ad_space_type`, `ad_element_type` |
| `AD_Column` | `ad_space_type_mep`, `m_attribute` |
| `AD_Val_Rule` | `ad_check_threshold`, `ad_placement_rule` |
| `M_BOM` / `M_Product` | `m_bom`, `m_bom_line` |
| `M_BOM_Component` | `m_bom_line` with role and sequence |

### 2.2 Why AD Works for BIM

**Table-driven compilation** has 50+ years of precedent (LL/LR parser tables). The BIM compiler extends this:

- **Parser tables** → `ad_space_type_alias` (52 room name aliases)
- **Type tables** → `ad_space_type` (26 space types with categories and rules)
- **Placement tables** → `m_attribute` (Z-rules, spacing, diameter)
- **Validation tables** → `ad_check_threshold` (36 code-driven thresholds)

The compiler is a table-driven system where the tables happen to encode construction knowledge.

### 2.3 AD Table Inventory (34 tables)

**Actively consumed (13 tables, 38%):**

| Table | Rows | Consumer | Domain |
|-------|------|----------|--------|
| `m_bom` | 9 | BOMAssemblerAD | Assembly definitions |
| `m_bom_line` | 38 | BOMAssemblerAD | Component rules |
| `m_attribute` | 10 | BOMRuleAD | Placement params |
| `ad_space_type` | 26 | SpaceTypeRegistry | Room types |
| `ad_space_type_alias` | 54 | MEPBOMResolver | Room name resolution |
| `ad_space_type_mep` | 22 | MEPAD | MEP requirements |
| `ad_space_type_mep_bom` | 89 | MEPBOMResolver | Ceiling MEP quantity rules |
| `ad_opening_family` | 10 | OpeningBomAD | Door/window families |
| `ad_space_type_opening` | 21 | OpeningBomAD | Opening defaults |
| `ad_check_applicability` | 35 | SanityCheckAD | Check selection |
| `ad_check_threshold` | 36 | SanityCheckAD | Code thresholds |
| `ad_placement_rule` | 21 | PlacementRuleAD | MEP placement |
| `ad_mep_profile` | 3 | MEPBomAD | Budget/Standard/Premium |

**Populated but not yet consumed (22 tables, 65%):**

| Table | Rows | Future Domain |
|-------|------|---------------|
| `ad_building_code` | 14 | Multi-jurisdiction compliance |
| `ad_code_requirement` | 23 | Outlet spacing, GFCI rules |
| `ad_jurisdiction_codes` | 17 | Malaysia/USA code mapping |
| `ad_fire_compartment` | 6 | Compartment area limits |
| `ad_fp_trigger` | 12 | Sprinkler/alarm triggers |
| `ad_fp_coverage` | 4 | Sprinkler coverage rules |
| `ad_elevator_requirement` | 9 | Elevator sizing |
| `ad_stair_requirement` | 7 | Stair dimensions |
| `ad_vert_circ_trigger` | 11 | Stairs/elevator triggers |
| `ad_egress_travel` | 14 | Travel distance limits |
| `ad_pressurization_trigger` | 7 | Pressurisation rules |
| `ad_fire_riser_requirement` | 9 | Wet riser sizing |
| `ad_building_bom` | 8 | Building-level templates |
| `ad_building_template` | 8 | Building type profiles |
| `ad_floor_type` | 12 | Typical floor definitions |
| `ad_unit_type` | 7 | Unit templates |
| `ad_space_type_alias` | 54 | Room name resolution (also in MEPBOMResolver) |
| `ad_product_dim` | 16 | Product dimension catalog |
| `ad_space_dim` | 6 | Room dimension constraints |
| `ad_element_mep` | 12 | Element-specific MEP |
| `ad_ref_list` | 26 | Reference data |
| `ad_reference` | 5 | Reference definitions |

The 22 unused tables represent the project's **future runway** — construction knowledge already encoded, awaiting Java consumers.

---

## 3. The BOM Pattern

### 3.1 Building as Bill of Materials

A building is a hierarchical BOM, inspired by the iDempiere M_BOM / M_Product hierarchy:

```
BUILDING
├── HEAD (basement, ground floor)
├── STANDARD (typical floors × N)
└── TAIL (penthouse, roof)

Each floor is a sub-assembly:
FLOOR
├── ROOMS (units)
│   └── Each room contains PRODUCTS (fixtures, MEP, furniture)
├── CORRIDORS
└── STRUCTURE (slabs, beams, columns)
```

### 3.2 BOM Assembly Output (Proven)

The condo_mid.db output demonstrates working BOM assemblies:

| Assembly Type | Count | Components | Roles |
|--------------|-------|------------|-------|
| WALL_PANEL | 269 | Frame + cladding + openings | FRAME, CLADDING, OPENING |
| DOOR_ASSEMBLY | 85 | Leaf + frame + hardware | LEAF, FRAME, HARDWARE |
| FP_PIPE_ASSEMBLY | 18 | T-assembly + pipes | HEAD, MAIN, BRANCH, RISER, TEE, DROP |
| MEP_ROOM | 18 | Lights + sprinklers + fans | LIGHT, SPRINKLER, DIFFUSER, FAN |
| FLOOR_STRUCTURAL | 18 | Slab + beam + column | SLAB, BEAM, COLUMN |
| STAIR_COMPLETE | 3 | Flight + landing + railing | FLIGHT, LANDING, RAILING |
| ROOF_ASSEMBLY | 1 | Complete roof structure | RAFTER, RIDGE, PURLIN |
| **Total** | **414** | **3,884 component links** | |

### 3.3 Nested BOM (2-Level)

BOM children can themselves be assemblies:

```
SPRINKLER_PENDANT_ASSEMBLY
├── FP_Sprinkler_Head_Pendent (leaf)
└── T_CONNECTOR_ASSEMBLY (nested)
    ├── FP_Tee_Threaded
    ├── FP_Transition_Fitting
    └── FP_Drop_Pipe
```

This is the standard ERP BOM pattern — M_BOM containing M_BOM_Component which references another M_BOM.

### 3.4 BOM Recipe in AD

```sql
-- m_bom defines assembly types
SELECT bom_id, target_ifc_class, group_by FROM m_bom;
-- FP_PIPE_ASSEMBLY, IfcElementAssembly, ROOM

-- m_bom_line defines components
SELECT role, child_ifc_class, z_rule FROM m_bom_line WHERE bom_id = 'FP_PIPE_ASSEMBLY';
-- HEAD, IfcFireSuppressionTerminal, BELOW_SLAB
-- MAIN, IfcPipeSegment,            BELOW_SLAB
-- RISER, IfcPipeSegment,           BETWEEN_FLOORS

-- m_attribute carries placement knowledge
SELECT param_key, param_value FROM m_attribute WHERE bom_child_id = 12;
-- spacing, 4.3
-- diameter, 0.027
-- z_offset, 0.1
```

### 3.5 Furniture Assembly BOM

**Phase BOM-1 DONE (2026-02-21).** `ad_room_slot x ad_room_boundary` JOIN produces BOM anchor rows as C_OrderLines. `RelationalResolver` detects `family_ref` in `bomIds` set -> calls `FurnitureBOMResolver.resolveForRoom()` -> N child Placements. SH=63, DX=1197, TB-LKTN=138 elements confirmed. The MRP BOM Drop pattern is live for the bottom three hierarchy levels (Parent/Room BOM -> Child/Set BOM -> Leaf/Item).

**Remaining (Phase BOM-2):** GGF layer (`UNIT_DUPLEX_STD`, `UNIT_SH_STD`) + GF floor assemblies (`FLOOR_1_STD`, `FLOOR_2_STD`). `family_ref` normalisation for 261 DX ABSOLUTE rows (Revit strings → catalog product IDs). Read Technical Guide §0.1 before starting BOM-2.

Furniture is placed with relative offsets resolved recursively — the same pattern as `T_CONNECTOR_ASSEMBLY`.

```
OFFICE_SEATING_SET                          <- phantom (grouping, not physical)
├── WORKSTATION_ASSEMBLY          seq=1     <- phantom
│   ├── Office_Desk               seq=1     role=DESK       offset=(0, 0, 0)
│   ├── Office_Chair              seq=2     role=USER_CHAIR  offset=(0, -0.6, 0) rot=π
│   └── iMac_27                   seq=3     role=MONITOR     offset=(-0.59, 0, +deskH)
├── VISITOR_TABLE                 seq=2     role=TABLE       offset=(0, +2.0, 0)
└── VISITOR_SEATING_PAIR          seq=3     <- phantom
    ├── Visitor_Chair_A           seq=1     role=GUEST       offset=(0, -0.3, 0) rot=0
    └── Visitor_Chair_B           seq=2     role=GUEST       offset=(0, +0.3, 0) rot=π
```

The `child_bom_id` column on `m_bom_line` already supports nesting. Spatial offsets use `m_attribute` with keys `x_offset`, `y_offset`, `z_offset`, `rotation`. No schema change needed — just new rows.

**Placement rules per space type:**
- **Office**: workstation against longest free wall, visitor seating against opposite wall, both avoiding openings
- **Big room** (area >= 80m²): two sets at mirrored positions (NW + SE corners, second set rotation=π)
- **Small room**: workstation only, no visitor zone

**Java pattern — Composite + recursive resolve:**
```java
List<PlacedComponent> resolveBOM(String bomId, double anchorX, double anchorY,
                                  double anchorZ, double parentRotation) {
    List<PlacedComponent> result = new ArrayList<>();
    for (BOMChild child : getBOMChildren(bomId)) {
        // Rotate child offset by parent rotation
        double cx = anchorX + rotate(child.xOffset, child.yOffset, parentRotation).x;
        double cy = anchorY + rotate(child.xOffset, child.yOffset, parentRotation).y;
        double cz = anchorZ + child.zOffset;
        double cr = parentRotation + child.rotation;

        if (child.nestedBomId != null) {
            result.addAll(resolveBOM(child.nestedBomId, cx, cy, cz, cr)); // recurse
        } else {
            result.add(new PlacedComponent(child.name, cx, cy, cz, cr));
        }
    }
    return result;
}
```

Big room mirroring comes free: resolve the same BOM at two anchors with rotation 0 and π — all child offsets flip automatically.

### 3.6 Tower-Level BOM Hierarchy (Phase 94+ Vision)

The BOM pattern extends beyond rooms to the full building hierarchy. Currently the compiler thinks in **storeys with rooms**. The target: **assemblies all the way up**.

```
TOWER_BOM                                    <- top-level
├── GROUND_FLOOR_TEMPLATE       x1           <- variant: ground
│   ├── ENTRANCE_LOBBY          role=LOBBY
│   ├── MANAGEMENT_OFFICE       role=OFFICE
│   ├── STAIR_ENCLOSURE_A       role=STAIR
│   └── UTILITY_ROOMS           role=SERVICE
├── TYPICAL_FLOOR_TEMPLATE      x16          <- variant: typical (repeated)
│   ├── LIFT_LOBBY              role=LOBBY
│   ├── CORRIDOR                role=CIRCULATION
│   ├── UNIT_1BR x4             role=UNIT
│   ├── TOILET_BLOCK            role=SERVICE
│   ├── STAIR_A                 role=VERTICAL
│   └── ELEVATOR_SHAFT          role=VERTICAL
├── ROOF_FLOOR_TEMPLATE         x1           <- variant: roof
└── VERTICAL_SERVICES                        <- spans all floors
    ├── RISER_STACK             role=PLUMBING
    ├── ELEVATOR_CAR            role=TRANSPORT
    └── STAIR_FLIGHTS           role=EGRESS
```

**iDempiere Libero Manufacturing BOM concepts that map here:**

| Libero Concept | BIM Compiler Equivalent |
|---|---|
| Phantom BOM | FLOOR_TEMPLATE — resolves to children, not physical |
| BOM Type = Standard | Fixed recipe (every typical floor identical) |
| BOM Type = Variant | Ground vs Typical vs Roof — same parent, different children |
| Feature/Selection | DSL user picks: `floor_type: TYPICAL with TOILET_BLOCK_A` |
| qty_type = VARIABLE | Floor count from DSL: `TYPICAL_FLOOR x16` |
| Optional component | Elevator: present in high-rise, absent in walkup |

**DSL evolution (future):**
```bim
TOWER condo_mid {
  GROUND  template:GROUND_LOBBY
  TYPICAL template:TYPICAL_4UNIT  floors:2-17  height:3.4
  ROOF    template:ROOF_PLANT
}
```

Where `TYPICAL_4UNIT` is a BOM in the library. User can override:
```bim
TYPICAL template:TYPICAL_4UNIT floors:2-17 {
  option TOILET_BLOCK: VARIANT_B
  option STAIR: PRESSURIZED
  remove UNIT_1BR slot:4
  add    UNIT_STUDIO slot:4
}
```

**Schema additions needed (minimal):**
```sql
ALTER TABLE m_bom_line ADD COLUMN bom_type TEXT DEFAULT 'STANDARD';
-- Values: STANDARD, PHANTOM, VARIANT, OPTIONAL

CREATE TABLE m_bom_variant (
    variant_id TEXT PRIMARY KEY, bom_id TEXT,
    variant_name TEXT, is_default INTEGER DEFAULT 0);

CREATE TABLE m_bom_feature (
    feature_id TEXT PRIMARY KEY, bom_id TEXT,
    feature_name TEXT, required INTEGER DEFAULT 1);
```

The existing `ad_building_template` (8 rows), `ad_floor_type` (12 rows), and `ad_unit_type` (7 rows) tables are already populated — they await Java consumers.

> **See also:** [PREFAB_ARCHITECTURE.md](PREFAB_ARCHITECTURE.md) — Phase 115 concretizes this vision with a prefab assembly catalog (`prefab_product`, `prefab_bom`, `prefab_interface`) that replaces runtime spatial resolution with DAG expansion of pre-computed assemblies.

### 3.7 Floor Plate as Spatial BOM (Phase 95 Target)

**The problem:** The DSL currently specifies exact grid bounds for every room (`bounds:C2-D4`). When you add a toilet, you manually shrink the lobby. When you remove a corridor, you manually expand the units. The compiler has no spatial reasoning — it trusts the user's manual layout.

**The insight:** A floor plate is a BOM with **spatial resolution rules**. Instead of explicit bounds, each child declares its spatial role. A `FloorPlateBOMResolver` computes the bounds.

```
Current DSL (explicit bounds — fragile):
  LIFT_LOBBY bounds:C2-D4          // user computes coordinates
  TOILET_BLOCK bounds:D3-E4        // user computes coordinates
  UNIT "E1" bounds:D1-F3           // user adjusts when toilet moves

BOM DSL (spatial rules — resilient):
  CORE {
      STAIR_A at:north                    // resolver: first cell of core
      LIFT_LOBBY fill:center              // resolver: whatever remains between stairs
      TOILET carve:1cell from:LIFT_LOBBY  // resolver: shrinks lobby, places toilet
      STAIR_B at:south                    // resolver: last cell of core
  }
  CORRIDOR side:west width:1bay           // resolver: adjacent to core, one grid bay
  UNITS side:west fill:remaining          // resolver: everything west of corridor
  UNITS side:east fill:remaining          // resolver: everything east of core
```

**Spatial rule vocabulary** (from `m_attribute`):

| Rule | Meaning | Example |
|------|---------|---------|
| `at:north` | Place at northernmost available cell | Stairs |
| `at:south` | Place at southernmost available cell | Stairs |
| `fill:center` | Fill remaining cells between fixed children | Lobby |
| `fill:remaining` | Fill all unassigned cells | Units |
| `carve:Ncell from:PARENT` | Shrink parent, create child in freed cell | Toilet from lobby |
| `side:west` | Allocate adjacent bay on west side | Corridor |
| `width:Nbay` | Width in grid bays | Corridor (1 bay = 2m) |

**Resolution algorithm:**
1. Parse zone declarations (CORE, CORRIDOR, UNITS) with spatial rules
2. Allocate fixed children first (`at:north`, `at:south`) — these claim specific cells
3. Resolve `fill:center` — lobby takes whatever's left in the core
4. Resolve `carve:` — shrink parent, assign freed cells to child
5. Resolve `side:` — corridor bay adjacent to core
6. Resolve `fill:remaining` — units take everything else
7. Output: same grid bounds as before, but computed not manual

**Why this works:** The spatial rules use the same `m_attribute` table and the same recursive resolver pattern as furniture BOM. The difference is the resolver operates on **grid cells** instead of **metric offsets**. A `FloorPlateBOMResolver` is structurally identical to `FurnitureBOMResolver` — load BOM tree from AD, walk children, resolve positions.

**Key benefit:** Adding a toilet doesn't require touching unit bounds. The resolver automatically shrinks the lobby and adjusts units. Removing a corridor → units expand. Changing from 2 stairs to 3 → lobby shrinks. The DSL declares *what*, the BOM resolves *where*.

**AD tables already populated:**
- `ad_building_template` (8 rows) — building type profiles
- `ad_floor_type` (12 rows) — typical floor definitions
- `ad_unit_type` (7 rows) — unit templates
- These await Java consumers — the `FloorPlateBOMResolver` would read them.

### 3.8 Evolution Path

The BOM hierarchy builds incrementally:

| Phase | Scope | Status | Visual Impact |
|-------|-------|--------|---------------|
| 93 | Furniture BOM assemblies (nested offsets) | **DONE** | Coordinated workstation + visitor seating |
| 94 | Toilet blocks, floor plate manual layout | **DONE** | Single toilet block, east unit expansion |
| BOM-1 | Room slot dispatch + BOM expansion wired (SH/DX/TB-LKTN) | **DONE (2026-02-21)** | Furniture generated from `ad_room_slot × ad_room_boundary` — SH=15, DX=66, TB-LKTN=138 elements |
| BOM-2a/b | GGF/GF catalog entries + ROOM spacing facts as C_OrderLines | **DONE (2026-02-21)** | Five-hop BOM chain data complete |
| Phase 4b | Floor orientation cascade — DX L2 = π rotation, floorZOffsets map | **DONE (2026-02-24)** | DX upper furniture at correct Z=3.0m + 180° bearing |
| Phase 4c | GPD dispatch (locator_ref/layout_strategy) + sub-BOM recursion (child_bom_id) | **PARTIAL (2026-02-25)** | NORTH_WALL linear placement; SOFA_AREA sub-BOM proves child_bom_id pattern |
| BOM-2c | UNIT/FLOOR Orderlines as C_OrderLines | **NEXT** | Full 5-level relational cascade — closes top two hops |
| BOMCascadeResolver | Unify BomTierResolver + FurnitureBOMResolver into single recursive walker | **Planned** | All levels handled by one engine (see PREFAB_ARCHITECTURE.md §9) |
| 95 | Floor plate as spatial BOM (`FloorPlateBOMResolver`) | Future | Auto-resolve room bounds from zone rules |
| 96 | `bom_type` + `m_bom_variant`, floor templates as BOM | Future | Floor template reuse |
| 97 | Vertical services as tower-level BOM children | Future | Complete building hierarchy |
| 98+ | DSL `option`/`remove`/`add` syntax (Libero selection) | Future | User-selectable building variants |

---

## 4. Compilation Pipeline

### 4.1 Five-Stage DAG

```
PARSE → RESOLVE → COMPILE → PLACE → WRITE
```

| Stage | Input | Output | AD Tables Used |
|-------|-------|--------|---------------|
| **Parse** | `.bim` DSL file | BuildingDefinition | — |
| **Resolve** | BuildingDefinition | Room sizes, BOM rules | ad_space_type, ad_room_sizing |
| **Compile** | Resolved rooms | Walls, slabs, structure | m_bom, m_bom_line |
| **Place** | Compiled structure | MEP, furniture, openings | ad_space_type_mep, ad_opening_family |
| **Write** | All elements | SQLite output DB | m_bom (assembly creation) |

### 4.2 Override Chain

DSL declarations override BOM defaults override hardcoded fallbacks:

```
DSL explicit  >  opens_to connection  >  BOM default  >  hardcoded fallback
     ↑                  ↑                     ↑                ↑
User specifies    Room adjacency       ad_space_type_*     Last resort
```

---

## 5. Correctness Dimensions

### 5.1 The Seven Levels (Complementary to LOD)

LOD (Level of Development) measures **detail richness** — how precisely geometry is modelled. These correctness dimensions measure **validity** — whether elements form a constructible building.

| Level | Name | Question | Current Status |
|-------|------|----------|----------------|
| L0 | **Geometric** | Do elements have valid shapes? | 100% |
| L1 | **Spatial** | Are elements in correct locations? | 100% |
| L2 | **Topological** | Do elements connect correctly? | 40% |
| L3 | **Systemic** | Do systems function as wholes? | 10% |
| L4 | **Constructible** | Can this be built in sequence? | 0% |
| L5 | **Compliant** | Does it pass inspection? | 40% |
| L6 | **Operable** | Can it be maintained? | 0% |

Each level is **independently provable** through the witness system.

### 5.2 Relationship to LOD

```
LOD 100 (Conceptual)    → L0 Geometric only
LOD 200 (Schematic)     → L0 + L1 Spatial
LOD 300 (Design)        → L0 + L1 + L2 Topological
LOD 350 (Coordination)  → L0-L2 + L3 Systemic
LOD 400 (Fabrication)   → L0-L3 + L4 Constructible + L5 Compliant
LOD 500 (As-Built)      → L0-L5 + L6 Operable
```

The BIM compiler currently targets **LOD 400 geometry** with **L0-L1 correctness** and partial L2/L5. Full AD utilisation would bring L2-L5 to completion.

---

## 6. Witness System

### 6.1 Proof-Carrying Compilation

Every compiled building produces a witness file (`*_witness.json`) with validation claims. Each claim is either **PROVEN** (with numerical proof data), **SKIPPED** (not applicable), or **UNPROVABLE** (proof system deficiency — must never appear in production).

The witness system solves the known weakness of metadata-driven architectures: **debugging difficulty**. When behaviour is defined in data rather than code, errors are hard to trace. Witnesses make metadata-driven decisions auditable.

### 6.2 Current Witness Count

24 witness claims covering geometric, spatial, structural, and MEP correctness. See `docs/witness-system-specification.md` for the full specification.

### 6.3 Sanity Checks

32 sanity checks (separate from witnesses) validate the output database against construction codes. Checks are registered via `ad_check_applicability` — the check selection itself is metadata-driven.

---

## 7. Current Migration State

### 7.1 What's AD-Driven (Working)

| Domain | Mechanism | Lines of Java Eliminated |
|--------|-----------|-------------------------|
| BOM assembly creation | m_bom + m_bom_line → BOMAssemblerAD | Assembly logic is generic |
| FP Z-positioning | m_attribute → BOMRuleAD.resolveZ() | 70 slab overlaps → 0 |
| Opening defaults | ad_opening_family + ad_space_type_opening → OpeningBomAD | BOM-driven doors/windows |
| Sanity check selection | ad_check_applicability → SanityCheckAD | Data-driven check registration |
| Space type definitions | ad_space_type → SpaceTypeRegistry | 26 types from metadata |

### 7.2 What's Still Hardcoded (Migration Targets)

| Domain | Current Location | Target AD Table | Priority |
|--------|-----------------|----------------|----------|
| ~~MEP guarantee loop~~ | ~~BuildingCompiler~~ | ~~ad_space_type_mep_bom~~ | ~~Done (92D)~~ |
| Furniture placement offsets | FurniturePlacer (hardcoded) | m_bom_line + m_attribute | **P1** (Phase 93) |
| Room type string matching | BuildingCompiler:3152-3159 | ad_space_type_alias | **P1** |
| Double-set threshold (80m²) | BuildingCompiler:3702 | ad_space_type_mep | **P2** |
| High-rise detection (18m) | FireProtectionResolver | ad_fp_trigger | **P2** |
| Floor templates | DSL `copies` keyword | ad_building_template + ad_floor_type | **P2** (Phase 94) |
| Outlet/switch placement | ElectricalPlacer | ad_code_requirement | **P3** |
| Unit interiors | skipKeywords blocks it | ad_unit_type | **P4** |

### 7.3 Migration Discipline

Proven in Phase 85:

1. **One parameter at a time.** Move one constant from Java to `m_attribute`.
2. **Audit all consumers.** Shared variables are the #1 trap (Phase 85: ceilingZ served sprinklers + fans + diffusers).
3. **Verify numerically.** Witness proofs must pass before and after.
4. **Don't let two sources of truth coexist.** If BOM has `spacing=4.3` and Java has `SPACING=4.3`, one must go.

---

## 8. Known Risks and Mitigations

| Risk | Severity | Mitigation |
|------|----------|------------|
| **Schema complexity** (34 tables) | High | This document + AD table inventory |
| **Debugging difficulty** | High | Witness system (proof-carrying compilation) |
| **Schema evolution** | Medium | Manual today; consider Flyway if tables grow |
| **Performance** (DB lookups) | Low | Static loading + caching at compile start |
| **Two parallel systems** | High | Phase-by-phase migration (Section 7.2) |

---

## 9. Key Files (Phase 114+)

### Metadata Layer

`library/BOM.db` — unified working database (~73 tables: `ad_*` config + `m_*` BOM).
`library/component_library.db` (127MB, Git LFS) — LOD geometry store (~12 tables: `lod_*`).
- Migration scripts in `migration/` — idempotent, run in order

### Java Layer (`src/main/java/com/bim/compiler/dsl/`)

| File | Role | ~Lines |
|------|------|--------|
| `BuildingSpecs.java` | 26 record types (specs) | 700 |
| `BuildingCompiler.java` | Entry points, validation, orchestration | 1,630 |
| `MultiUnitCompiler.java` | Multi-unit layout, party walls | 1,450 |
| `StoreyCompiler.java` | compileStorey, walls, openings, stairs | 2,300 |
| `WitnessGenerator.java` | All witness generation | 1,000 |
| `BuildingWriter.java` | Schema, write(), QTO, BOM orchestrator | 900 |

Sub-writers: `ElementPersistence`, `StructuralWriter`, `StairWriter`, `OpeningWriter`, `MEPWriter`

### Library Layer (`src/main/java/com/bim/compiler/library/`)

| File | Role |
|------|------|
| `FurnitureBOMResolver.java` | Data-driven furniture from m_bom |
| `ManifestResolver.java` | Assembly MANIFEST face clearances (Phase 115B) |
| `FixturePlacer.java` | Bathroom/kitchen fixture placement |
| `ComponentLibrary.java` | LOD400 component lookup |

### Contract Layer (`src/main/java/com/bim/compiler/contract/`)

| File | Role |
|------|------|
| `BundleWorker.java` | OSGi-inspired worker interface (Phase 118) |
| `CatalogContract.java` | DSL-as-Catalog-Selector enforcement (Phase 117) |

### Verification Layer (`src/main/java/com/bim/compiler/validation/`)

| File | Role |
|------|------|
| `SpatialDigest.java` | SHA256 deterministic regression fingerprint (Phase 118) |
| `CatalogValidator.java` | Checks DSL refs against component_library.db (Phase 117) |

> **See also:** [BUNDLE_WORKER_FRAMEWORK.md](BUNDLE_WORKER_FRAMEWORK.md) — OSGi bundle worker vision, dispatch protocol, SpatialDigest, progressive migration path.

### Output Layer

`output/*.db` — SQLite with `elements_meta`, `base_geometries`, `elements_rtree`, `assembly_components`, `simple_qto`, `mep_systems`

---

## Appendix A: IFC Naming Convention

Pattern: `{Category}_{Variant}_{Room}_{Storey}`

| Segment | Examples |
|---------|----------|
| Category | `Door`, `Light`, `Alarm`, `Water_Tank` |
| Variant | `D1_900x2100`, `Downlight`, `Smoke` |
| Room | `Living`, `Kitchen`, `Corridor` |
| Storey | `G`, `F2`, `Roof` |

Rules: Category first (Outliner grouping). Underscores only. No JKR prefixes, no Revit IDs, no GUIDs. Standard abbreviations: `WC`, `CW`, `HW`, `FP`, `Ext`.

---

## Appendix B: Source Consolidation

**`library/BOM.db`** + **`library/component_library.db`** = source of truth (see `ConstructionAsERP.md` for 3-DB architecture).

| Archive | Location | Status |
|---------|----------|--------|
| Source IFC files (23) | `archive/IFC_source_files/` | Extraction complete |
| Federation DB | `database/enhanced_federation_GI.db` | Reference only |
| Duplex reference | `database/Stacked_Duplex.db` | Reference only |

---

## Appendix C: Lessons Learned (100 Phases)

**Engine evolution:** Hardcoded (1-50) → BOM-driven (50-85) → Standards-as-data (85+). Each boundary asks: "Can this be a table row instead of a code change?"

**Critical traps:**
- Orientation axis swap: `orientationMatched=false` swaps X/Y extents → protrusion failures
- Z double-correction: pass raw Z, let writer handle
- `skipKeywords`: UNIT/CORRIDOR create room objects but no geometry (intentional)
- Grid axes vs spacing: trim to `spacing.size() + 1`
- CladdingSpec bounds NOT normalized on west/south walls
- Stale `__pycache__/*.pyc` after editing Python

**Principles that held:** Java records for immutable specs. World-space geometry (Pattern B, zero transforms). Single JDBC connection per compilation. Witness-first development.

**Next time:** Start with BOM tables from Phase 1. Smaller commits. Extract classes at 2000 lines. Federation-first for all geometry. Standards tables before features.

---

## §9. Spatial Storage Model — Building as SpaceSize AABB Hierarchy

> *"The whole building is a large storage Location whose bounds are an AABB in mm."*

The M_BOM SpaceSize model (AABB bounding box in mm) maps exactly onto the building's
spatial hierarchy. Each level — building, storey, room, zone — carries a SpaceSize AABB
that bounds its physical extent. This is not an analogy — it is the same data structure
applied to cubic space instead of storage volume. The compiler IS a spatial placement engine.

### 9.1 SpaceSize AABB Hierarchy

| SpaceSize Level | BIM Layer | Table | Unit |
|---|---|---|---|
| Building AABB | Building | C_Order (Construction Order) | **mm** |
| Storey AABB | Storey / Grid axis intersection | `ad_building_grid` | **mm** |
| Room AABB | Room | `ad_room_boundary` | **mm** |
| Zone AABB / M_Locator | Position within room — grid cell in mm (NORTH_WALL, CENTRE...) | CO_EmptySpaceLine / PhantomLayout | **mm** |

All physical coordinates at every SpaceSize level are in **mm**.
`ad_building_grid` stores grid line positions as mm offsets from the building origin.
The `M_Locator` X/Y/Z labels in iDempiere correspond to these grid line mm values:
X = grid axis (storey mm), Y = room position (room mm), Z = storey elevation (level mm).

Every placed element has a fully qualified SpaceSize address:
```
Location : Ifc4_SampleHouse
Aisle    : Level_1  →  grid_x=0mm, grid_y=0mm, z_offset=0mm   (storey / grid intersection in mm)
Bin      : LIVING_ROOM  →  min_x=1620mm, max_x=6265mm, min_y=−1246mm, max_y=4558mm
Lot      : NORTH_WALL   →  zone depth 700mm from wall face
Sequence : 3            →  position in zone sequence (hostAxis mm)
```

### 9.2 mm Cube is the Physical Truth — at Every SpaceSize Level

SpaceSize labels at every level — storey names, room names, zone names — are **human labels**:
convenience aliases for mm coordinates. The physical truth is always a mm value:

```
Aisle  "Level_1"   resolves to:  z_offset=0mm  (storey elevation above datum)
Bin    "LIVING_ROOM" resolves to: min_x=1620mm, max_x=6265mm,
                                  min_y=−1246mm, max_y=4558mm,
                                  min_z=0mm,     max_z=3000mm
Lot    "NORTH_WALL"  resolves to: min_x=1620mm, max_x=6265mm,
                                  min_y=−1246mm, max_y=−546mm  (700mm zone depth from wall)
                                  min_z=0mm,     max_z=2800mm
```

In iDempiere, `M_Locator` stores X/Y/Z as alphanumeric labels (e.g. "A", "01", "1").
Here they carry mm values directly — there is no label-to-coordinate lookup layer needed
because the grid line positions are the coordinates:

```
M_Locator.X  =  ad_building_grid.grid_x_mm   (Aisle — distance along X axis in mm)
M_Locator.Y  =  ad_building_grid.grid_y_mm   (Bin   — distance along Y axis in mm)
M_Locator.Z  =  c_orderline.position_value_3  (Level — storey Z elevation in mm)  -- C_OrderLine
```

The label is for navigation. The mm value is the address. The compiler always
operates on mm coordinates — all SpaceSize labels dissolve to mm at resolution time.

### 9.3 EmptyStorage Overlay

Two parallel records share the same SpaceSize address:

```
SpaceSize: SampleHouse / Level_1 / LIVING_ROOM / NORTH_WALL

PlacedStock   → Piano(1200) + Sofa(2200) + Dining(1800) = 5200mm
EmptyStorage  → remaining=1300mm, nextAnchor=(4.75, 0.5, 0.0)

PlacedStock + EmptyStorage = room extent   ← always balances
```

The EmptyStorage record is the **PhantomLayout** — a transient (non-persisted) working
state computed during BOM resolution. See `PREFAB_ARCHITECTURE.md §8` for the full
PhantomLayout / Place / GPD specification.

A contractor navigates by SpaceSize address to find available space — no BIM knowledge needed:

```
Bin: LIVING_ROOM  /  Lot: NORTH_WALL
EmptyStorage: 1300mm available, nextAnchor: (4.75, 0.5, 0.0)
→ any item ≤ 1300mm wide fits here
```

### 9.4 Full ERP ↔ BIM Mapping

| iDempiere Entity | BIM Compiler |
|---|---|
| `M_Warehouse` | Building — C_Order (Construction Order) |
| `M_Locator` X/Y/Z labels | Grid line mm values (`ad_building_grid`) + room label + zone label |
| `M_Locator` physical coordinates | mm cube — from `ad_building_grid` + `ad_room_boundary` |
| `M_Storage` / `M_StorageOnHand` | Placed elements (`elements_meta`) |
| Empty capacity at locator | CO_EmptySpaceLine |
| Placement next coordinate | `PhantomLayout.nextAnchor` |
| `M_Product_BOM` | `m_bom` + `m_bom_line` |
| Space / Placement Rule | `Place` descriptor (resolved from C_OrderLine + `m_attribute`) |
| Placement strategy | ADJACENT / OPPOSITE / FLOAT |
| Placement session (transient) | `PhantomLayout` (not persisted) |
| Resolved placement line | Resolved `PlacedFurniture` |
| Bin capacity check | `variance ≥ 0` |
| Bin overflow alert | `variance < 0` → GIC violation |

**Note:** The spatial concept is the `Place` descriptor: the resolved position rule for an
element. CO_EmptySpaceLine tracks available capacity per room zone.

### 9.5 Cross-references

- `PREFAB_ARCHITECTURE.md §8` — Place descriptor, GPD, PhantomLayout, variance child
- `docs/DEVELOPER_GUIDE.md` — pipeline stages, build commands, tooling
- `ad_room_boundary` — Bin registry (room extents in mm)
- `ad_building_grid` — Aisle system (grid axis coordinates in mm — the M_Locator X/Y values)
- `ad_room_slot` — Bin stock declaration (which BOMs fit which room type)

---

## Appendix D: iDempiere Lineage

The AD pattern traces to iDempiere/ADempiere ERP: Application Dictionary → metadata tables driving behaviour. `M_BOM / M_Product` → hierarchical BOM. Configuration over code → SQL INSERT, no Java change. The adaptation to BIM compilation is novel — the synthesis of table-driven compilation + manufacturing BOM + metadata-driven architecture applied to a construction compiler.

---

## Appendix E: Superseded Documents

All preserved in `docs/archive/` for provenance:

| Document | What survives in this doc |
|----------|--------------------------|
| `ARCHIVE_intent_compiler_method.md` | Section 1.1-1.2 (founding principles) |
| `bim-compiler-architecture-evolution.md` | Section 5 (L0-L6 framework) |
| `METADATA_DRIVEN_ARCHITECTURE.md` | Section 2 (AD pattern) |
| `BUILDING_AS_BOM_CONCEPT.md` | Section 3 (BOM pattern) |
| `DSL_AS_CATALOG_SELECTOR.md` | Section 1.3 (DSL selects) |
| `IFC_NAMING_CONVENTION.md` | Appendix A |
| `SOURCE_CONSOLIDATION.md` | Appendix B |
| `LESSONS_LEARNED.md` | Appendix C |
| `GLOSSARY.md` | Terms absorbed into sections 1-8 |
| `DSL_EXTENSION_GUIDE.md` | Outdated (references SpaceSolver pre-Phase 114) |

---

*Architecture Document v3.1 — "DSL Selects. BOM Parameterises. Java Resolves."*
