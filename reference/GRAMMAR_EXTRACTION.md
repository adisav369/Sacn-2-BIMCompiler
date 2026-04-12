# Grammar Extraction — IFC Rosetta Stone Decode

**Phase 116** — Linguistic mapping from reference IFC corpus to metadata grammar.

The reference IFC files are a **corpus** — a body of real language (building descriptions)
from which we extract formal grammar rules. The metadata tables are the **grammar** that
enables the compiler to construct any sentence (building) in that language.

---

## 1. The Dewey Decimal Principle

The Sample House IFC (screenshot: flat outliner, no discipline grouping) reveals a
fundamental problem: IFC files list elements as a flat bag. Our grammar must impose
**taxonomic order** — like the Dewey Decimal System classifies all human knowledge,
our metadata classifies all building elements.

### Classification Hierarchy

```
000  SPATIAL           — storeys, spaces, zones
100  STRUCTURAL         — slabs, beams, columns, foundations
200  ENVELOPE           — exterior walls, roof, cladding
300  INTERIOR           — partition walls, ceilings, floor finishes
400  OPENINGS           — doors, windows, skylights
500  FURNITURE          — fixtures, fittings, furnishing elements
600  MEP-MECHANICAL     — HVAC ducts, fans, diffusers
700  MEP-PLUMBING       — pipes, fittings, sanitary terminals
800  MEP-ELECTRICAL     — conduit, outlets, switches, lights
900  FIRE PROTECTION    — sprinklers, risers, fire suppression
```

Each element in the compiled output maps to exactly one category. This is how
`ad_space_type` + `component_types` + `ad_bom_child` work together: the space
provides context (000), the component type provides the Dewey class (100-900),
and the BOM recipe provides the assembly structure within that class.

### Grammar Table → Dewey Mapping

| Dewey | Grammar Table(s) | Status |
|-------|-------------------|--------|
| 000 SPATIAL | `ad_space_type`, `ad_unit_type_room` | 37 types, 5 unit layouts |
| 100 STRUCTURAL | `ad_bom_child` (FLOOR_STRUCTURAL) | Slab+Beam+Column recipe |
| 200 ENVELOPE | `ad_wall_type` + `ad_wall_type_rule` | 8 types, 8 rules |
| 300 INTERIOR | `ad_wall_type` (INTERIOR_*) | 4 interior wall types |
| 400 OPENINGS | `ad_opening_family`, `ad_product_dim` | 20 families (10D+10W) |
| 500 FURNITURE | `ad_bom_child` (7 furniture BOMs), `ad_room_slot` | 8 room types slotted |
| 600 MEP-MECH | `ad_bom_child` (MEP_ROOM: DIFFUSER, FAN) | Per-room MEP |
| 700 MEP-PLUMB | `ad_bom_child` (TOILET_BLOCK, DUPLEX_BATHROOM) | Fixture sets |
| 800 MEP-ELEC | `ad_bom_child` (MEP_ROOM: OUTLET, SWITCH, LIGHT) | Per-room MEP |
| 900 FIRE PROT | `ad_bom_child` (FP_PIPE_ASSEMBLY, SPRINKLER_*) | Full sprinkler assembly |

---

## 2. Duplex IFC Decode — The Complete Inventory

Source: `reference/residential/Duplex_Architecture.ifc` (218 arch entities)
+ `reference/residential/Duplex_MEP.ifc` (~900 MEP entities)
Extracted to: `database/Stacked_Duplex.db` (1,085 total elements)

### 2.1 Spatial Grammar (Dewey 000)

**4 storeys:**
| IFC Storey | Compiler Mapping |
|------------|-----------------|
| T/FDN | Foundation level (below grade) |
| Level 1 | Ground floor |
| Level 2 | First floor |
| Roof | Roof level |

**21 rooms** in 2 mirrored units (A + B) × 2 levels + roof:
```
Unit A: A101 A102 A103 A104 A105 (Level 1)
        A201 A202 A203 A204 A205 (Level 2)
Unit B: B101 B102 B103 B104 B105 (Level 1)
        B201 B202 B203 B204 B205 (Level 2)
Roof:   R301
```

**Grammar coverage:** `ad_space_type` has LIVING, KITCHEN, BEDROOM, MASTER_BEDROOM,
BATHROOM, DINING, STAIR — all Duplex room functions are expressible.

### 2.2 Wall Grammar (Dewey 200/300)

**57 walls, 8 construction types** — extracted to `ad_wall_type`:

| IFC Wall Type | Grammar ID | Category | Construction | Total mm |
|---------------|-----------|----------|-------------|----------|
| Exterior - Brick on Block | EXTERIOR_BRICK | EXTERIOR | BRICK_BLOCK | 417 |
| Interior - Partition (92mm) | INTERIOR_PARTITION_92 | INTERIOR | STUD_FRAME | 124 |
| Interior - Furring (38mm) | INTERIOR_FURRING_38 | INTERIOR | FURRING | 54 |
| Interior - Furring (152mm) | INTERIOR_FURRING_152 | INTERIOR | FURRING | 152 |
| Interior - Plumbing (152mm) | INTERIOR_PLUMBING_152 | INTERIOR | STUD_FRAME | 184 |
| Party Wall - CMU | PARTY_CMU | PARTY | CMU | 493 |
| Foundation Concrete (417) | FOUNDATION_417 | FOUNDATION | CONCRETE | 417 |
| Foundation Concrete (435) | FOUNDATION_435 | FOUNDATION | CONCRETE | 435 |

**Resolution rules** (`ad_wall_type_rule`) — wall type = f(context, adjacent spaces):
```
EXTERIOR + any           → EXTERIOR_BRICK        (priority 100)
INTERIOR + BATHROOM      → INTERIOR_PLUMBING_152 (priority 50)
INTERIOR + KITCHEN       → INTERIOR_FURRING_152  (priority 60)
INTERIOR + STAIR         → INTERIOR_FURRING_152  (priority 60)
INTERIOR + generic       → INTERIOR_PARTITION_92 (priority 100)
PARTY + any              → PARTY_CMU             (priority 100)
FOUNDATION + any         → FOUNDATION_417/435    (priority 80/100)
```

### 2.3 Opening Grammar (Dewey 400)

**14 doors, 4 types** — mapped to `ad_opening_family`:

| IFC Door | Grammar Family | W×H (mm) |
|----------|---------------|-----------|
| M_Single-Flush 0762×2032 | Duplex Interior 762 | 762×2032 |
| M_Single-Flush 0864×2032 | Duplex Interior 864 | 864×2032 |
| M_Single-Flush 1250×2010 | Duplex Entry Door | 1250×2010 |
| M_Single-Glass 0813×2420 | Duplex Glass Door | 813×2420 |

**24 windows, 6 types** — mapped to `ad_opening_family`:

| IFC Window | Grammar Family | W×H (mm) |
|------------|---------------|-----------|
| M_Casement 819×759 | Duplex Casement Window | 819×759 |
| M_Fixed 2800×2410 | Duplex Bedroom Window | 2800×2410 |
| M_Fixed 4835×2420 | Duplex Living Room Window | 4835×2420 |
| M_Fixed 750×2200 | Duplex Narrow Tall Window | 750×2200 |
| M_Fixed 819×759 | Duplex Small Fixed Window | 819×759 |
| M_Skylight 1180×1170 | Duplex Skylight | 1180×1170 |

**Grammar status:** All 10 Duplex opening types have `ad_opening_family` entries
with exact IFC dimensions. Product dimensions registered in `ad_product_dim`.

### 2.4 Furniture Grammar (Dewey 500)

**61 furnishing elements, 12 types** — mapped to BOM recipes:

| IFC Furniture | Count | BOM Recipe | Role |
|---------------|-------|-----------|------|
| Base Cabinet (1000mm) | 16 | KITCHEN_CABINET_SET | BASE_CABINET |
| Upper Cabinet (1000mm) | 8 | KITCHEN_CABINET_SET | UPPER_CABINET |
| Counter Top w Sink | 2 | KITCHEN_CABINET_SET | SINK (counter) |
| Counter Top | 4 | KITCHEN_CABINET_SET | COUNTER |
| Bed Queen (1525×2007) | 2 | BED_SET | BED |
| Bed King (1981×2032) | 2 | BED_SET_MASTER | BED |
| Tall Cabinet (800mm) | 8 | BED_SET/BED_SET_MASTER | TALL_CABINET |
| Sofa (1830mm) | 4 | LIVING_SET | SOFA |
| Coffee Table (610³) | 8 | LIVING_SET | COFFEE_TABLE |
| Coffee Table (915×1830) | 2 | LIVING_SET | SIDE_TABLE |
| Vanity Cabinet (650×450) | 4 | BATHROOM_VANITY_SET | VANITY |
| Vanity Cabinet (450×450) | 1 | BATHROOM_VANITY_SET | VANITY (small) |

**Grammar status:** 4 furniture BOMs (KITCHEN_CABINET_SET, BED_SET, BED_SET_MASTER,
LIVING_SET) cover 58/61 furnishing elements. BATHROOM_VANITY_SET covers remaining 5.

### 2.5 Slab Grammar (Dewey 100)

**21 slabs, 6 types:**

| IFC Slab | Count | Grammar Coverage |
|----------|-------|-----------------|
| Finish Floor - Wood | 8 | BOM: FLOOR_STRUCTURAL (SLAB role) |
| Finish Floor - Ceramic Tile | 6 | BOM: FLOOR_STRUCTURAL (SLAB role) |
| 127mm Slab on Grade | 2 | BOM: FLOOR_STRUCTURAL |
| 150mm Exterior Slab on Grade | 2 | BOM: FLOOR_STRUCTURAL |
| Wood Joist w Subflooring | 2 | BOM: FLOOR_STRUCTURAL |
| Live Roof | 1 | BOM: ROOF_ASSEMBLY |

**Gap:** Floor finish type (wood vs ceramic tile) not yet in grammar — need
`ad_floor_finish` or slab variant parameter.

### 2.6 MEP Grammar (Dewey 600-900)

**890 MEP elements** from Duplex MEP IFC:

| Dewey | IFC Class | Count | Grammar Coverage |
|-------|-----------|-------|-----------------|
| 700 | IfcFlowSegment (Pipe) | 407 | `component_types`: PIPE_SEGMENT |
| 700 | IfcFlowFitting | 358 | `component_types`: PIPE_FITTING |
| 800 | IfcFlowSegment (Conduit) | 18 | Partial (PIPE_SEGMENT type) |
| 800 | IfcFlowTerminal (Receptacle) | 47 | BOM: MEP_ROOM OUTLET |
| 800 | IfcFlowTerminal (Switch) | 14 | BOM: MEP_ROOM SWITCH |
| 800 | IfcFlowTerminal (Light) | 14 | BOM: MEP_ROOM LIGHT |
| 800 | IfcFlowTerminal (Telephone) | 4 | No grammar yet |
| 700 | IfcFlowTerminal (Sanitary) | 16 | BOM: TOILET_BLOCK/DUPLEX_BATHROOM |
| 600 | Round Duct (Taps) | 2 | `component_types`: DUCT_FITTING |

**Gap:** Conduit/electrical raceways share PIPE_SEGMENT type — need IfcCableCarrierSegment
or conduit-specific component type. Telephone outlets have no grammar entry.

### 2.7 Structural Grammar (Dewey 100)

| IFC Class | Count | Grammar Coverage |
|-----------|-------|-----------------|
| IfcBeam | 8 | `component_types`: BEAM, BOM: FLOOR_STRUCTURAL |
| IfcMember | 4 (stringers) | `component_types`: MEMBER |
| IfcRailing | 4 | `component_types`: RAILING, BOM: STAIR_COMPLETE |
| IfcStairFlight | 2 | `component_types`: STAIR, BOM: STAIR_COMPLETE |

**Gap:** No `ad_beam_type` or `ad_footing_type` tables. Footing elements (7 in IFC)
have no grammar — currently the compiler generates foundations as wall extensions.

### 2.8 Ceiling/Covering Grammar (Dewey 300)

The Duplex IFC has **13 ceiling elements** (IfcCovering). These have no grammar table.

**Gap:** Need `ad_ceiling_type` or extend slab grammar for ceiling-side finishing.

---

## 3. MANIFEST Face Contracts — The Click Grammar

The MANIFEST system (PREFAB_ARCHITECTURE.md) defines how assemblies connect.
Each assembly declares what it needs on each of its 6 faces.

### Current Face Vocabulary

| Interface Type | Meaning | Used By |
|---------------|---------|---------|
| WALL_BACK | Must touch a wall | 9 assemblies (furniture against walls) |
| CLEARANCE | Needs open space for access | All assemblies (human circulation) |
| JOINABLE | Can abut another assembly | KITCHEN, LIVING, WORKSTATION, BED |
| MAIN_HOOKUP | Connects to pipe/duct main | SPRINKLER, T_CONNECTOR |
| ELEC_IN | Needs electrical feed from below | WORKSTATION |

### Duplex Room → Assembly → Face Mapping

```
KITCHEN (A103/B103)
  └─ KITCHEN_CABINET_SET  [BACK:WALL_BACK  FRONT:CLEARANCE  LEFT:JOINABLE  RIGHT:JOINABLE]
     ├─ BASE_CABINET      → against wall, counter runs L→R
     ├─ UPPER_CABINET     → above base, same wall
     ├─ COUNTER           → on top of base
     └─ SINK              → in counter

LIVING (A101/B101)
  └─ LIVING_SET           [BACK:WALL_BACK  FRONT:CLEARANCE  LEFT:JOINABLE  RIGHT:JOINABLE]
     ├─ SOFA              → against wall
     ├─ COFFEE_TABLE      → in front of sofa (CLEARANCE face)
     ├─ SOFA_B            → secondary seating
     ├─ SIDE_TABLE_A/B    → flanking sofa (JOINABLE faces)

BEDROOM (A104-A105/B104-B105)
  └─ BED_SET              [BACK:WALL_BACK  FRONT:CLEARANCE  LEFT:CLEARANCE  RIGHT:JOINABLE]
     ├─ BED               → headboard against wall
     ├─ SIDE_TABLE         → nightstand (RIGHT:JOINABLE)
     └─ TALL_CABINET_A/B  → wardrobes flanking

MASTER_BEDROOM (A204/B204)
  └─ BED_SET_MASTER       [BACK:WALL_BACK  FRONT:CLEARANCE  LEFT:CLEARANCE  RIGHT:CLEARANCE]
     ├─ BED               → king, centered on wall
     ├─ SIDE_TABLE         → nightstand
     └─ TALL_CABINET_A/B  → full-height wardrobes

BATHROOM (A102/B102, A202/B202)
  ├─ TOILET_BLOCK_FIXTURES [BACK:WALL_BACK  FRONT:CLEARANCE  LEFT:CLEARANCE  RIGHT:CLEARANCE]
  │  ├─ TOILET            → against plumbing wall
  │  ├─ HAND_BIDET        → beside toilet
  │  ├─ SINK              → vanity area
  │  └─ FLOOR_TRAP        → at low point
  └─ BATHROOM_VANITY_SET  [BACK:WALL_BACK  FRONT:CLEARANCE]
     └─ VANITY_A/B        → double vanity against wall
```

---

## 4. Grammar Completeness Matrix

Cross-referencing Duplex IFC entities against grammar tables:

| Entity Class | IFC Count | Grammar Tables | Coverage |
|-------------|-----------|----------------|----------|
| IfcWall | 57 | ad_wall_type, ad_wall_type_rule | COMPLETE |
| IfcDoor | 14 | ad_opening_family, ad_product_dim | COMPLETE |
| IfcWindow | 24 | ad_opening_family, ad_product_dim | COMPLETE |
| IfcFurnishingElement | 61 | ad_bom_child, ad_room_slot, ad_assembly_manifest | COMPLETE |
| IfcSlab | 21 | ad_bom_child (FLOOR_STRUCTURAL) | PARTIAL — no finish type |
| IfcStairFlight | 2 | ad_bom_child (STAIR_COMPLETE) | COMPLETE |
| IfcRailing | 4 | ad_bom_child (STAIR_COMPLETE) | COMPLETE |
| IfcBeam | 8 | component_types (BEAM) | PARTIAL — no beam type table |
| IfcMember | 4 | component_types (MEMBER) | PARTIAL — no stringer type |
| IfcFlowSegment | 427 | component_types (PIPE_SEGMENT) | PARTIAL — conduit conflated |
| IfcFlowFitting | 358 | component_types (PIPE_FITTING) | COMPLETE (generic) |
| IfcFlowTerminal | 105 | ad_bom_child (MEP_ROOM, TOILET) | PARTIAL — telephone gap |
| IfcCovering | 13 | — | NONE |

**Score: 7/13 COMPLETE, 5/13 PARTIAL, 1/13 NONE = ~73% grammar coverage**

### Priority Gaps for Next Phases

1. **ad_ceiling_type** — 13 IfcCovering entities have no grammar (Dewey 300)
2. **ad_floor_finish** — wood vs ceramic tile distinction (Dewey 100 detail)
3. **ad_beam_type** — 8 beams + 7 footings need structural grammar (Dewey 100)
4. **Conduit separation** — IfcCableCarrierSegment distinct from pipe (Dewey 800)
5. **Telephone/data outlets** — 4 entities, no grammar entry (Dewey 800)

---

## 5. Sample House — Next Corpus Entry

`reference/residential/SampleHouse.ifc` — single-storey house, simplest grammar test.
Maps directly to TB-LKTN building template. Extraction pending → will produce
`database/SampleHouse.db` reference for grammar validation.

Expected grammar exercise: confirm that the **same grammar tables** used for Duplex
can describe a single-storey house. If yes → grammar is generative (can produce
novel buildings from learned rules). If gaps → grammar needs extension.

---

## 6. Infrastructure IFC — Future Grammar Extension

The 9 PCERT IFC 4.3.2.0 samples in `reference/infrastructure/` contain entity classes
not yet in our grammar:

- IfcAlignment, IfcAlignmentHorizontal (roads, rail)
- IfcBridge, IfcBridgePart (civil structures)
- IfcGeographicElement (landscaping)
- IfcDistributionSystem (MEP systems as first-class objects)

These extend the Dewey system beyond 900, but the **same grammar pattern** applies:
extract entity types → map to AD tables → fill gaps → compile → compare.

---

## 7. The Linguistic Model

```
IFC Files (Corpus)     →  AD Tables (Grammar)      →  Compiler (Parser/Generator)
─────────────────          ──────────────────           ────────────────────────────
Real building data         Formal rules                 Produces new buildings
"Here is a wall"           "Walls have types"           "Generate walls of type X"
"This door is 864mm"       "Doors belong to families"   "Place family Y at position Z"
"Toilet needs plumbing"    "Room slots have priority"   "Fill slots in priority order"

        DECODE                    ENCODE                      CONSTRUCT
   (grammar extraction)      (metadata entry)            (DSL compilation)
```

The grammar is **complete** when every entity in the reference corpus can be reproduced
from metadata selection alone — zero hardcoded geometry, zero invented dimensions.
The IFC files are our Rosetta Stone; the grammar is our dictionary; the compiler
is our translator.
