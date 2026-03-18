# BOM-Based Compilation — Construction as Manufacturing

*If you can bill it, you can build it. If you can BOM it, you can compile it.*

> **Core thesis:** A building is a manufactured product. Its Bill of Materials IS the
> building — every wall, door, pipe, and cabinet is a line item with a position.
> A compiler that reads BOM data and produces 3D coordinates is doing the same thing
> an ERP system does when it explodes a manufacturing BOM into work orders.

---

## 1. Why BOM Metadata Solves Construction

Construction software treats buildings as geometry problems — draw walls, place
doors, route pipes. ERP software treats products as data problems — BOMs, work
orders, procurement. This project proves they are the **same problem**.

| Manufacturing concept | Construction equivalent |
|----------------------|----------------------|
| **M_Product** (product catalog) | Building element (wall panel, door, pipe elbow) |
| **M_BOM** (bill of materials) | Assembly recipe (kitchen set, floor plan, building unit) |
| **M_BOM_Line** (BOM child) | Recipe line: product type + qty (or verb formula). NOT a per-instance placement |
| **C_Order** (work order) | Construction project for a specific building |
| **C_DocType** (document type) | Building type configuration (SH, DX, TB, TE) |
| **CO_EmptySpace** (warehouse slot) | Room/floor slot awaiting BOM content |
| **PP_Order_Node** (operation) | Verb execution record (audit trail) |
| **C_Campaign** (marketing) | Design theme (Bali, Scandinavian, Industrial) |
| **EntityType** (D/U/A) | Dictionary=shipped catalog, User=verb-created, Application=custom |

The BOM hierarchy maps directly to the building hierarchy:

```
UNIT  →  FLOOR  →  ROOM  →  SET  →  ITEM
 │         │         │        │        │
 building  storey    room    furniture  leaf product
                              group    (door, pipe, cabinet)
```

**Three BOM dimensions** (iDempiere pattern) govern selection:

1. **Category** (M_BomCategory) — WHAT: kitchen, bedroom, bathroom, structural
2. **Owner** (C_DocType.DocSubType) — WHICH variant: SH, DX, TB, TE
3. **SpaceSize** (AABB on M_BOM_Line) — HOW MUCH: width × depth × height in mm

**Why this is powerful:** Adding a new building type requires zero Java code.
Define new BOM data (M_BomCategory + M_BomCategoryLine + m_bom rows) and the
compiler handles it. The same way an ERP handles a new product — data, not code.

### 1.1 Disciplines Are Metadata, Not Structure

In iDempiere, `C_DocType.DocBaseType` (SOO, API, MOP) classifies documents.
The document engine is generic — it processes all types identically. What
differs per type is the **validation rules** (tax on Invoice, credit check on
Order, BOM explosion on Manufacturing Order). The engine never has a
`switch(docBaseType)` — it fires AD_Val_Rule / ModelValidator / Column Callout
based on metadata.

Disciplines (ARC, STR, FP, ACMV, ELEC, SP, CW, LPG) follow the same pattern:

| iDempiere | BIM Compiler |
|-----------|-------------|
| C_DocType.DocBaseType | m_bom.bom_category (the discipline marker) |
| Same document engine | Same BOM walker (§2.2, §4) |
| AD_Val_Rule per DocType | AD_Val_Rule per bom_category (sprinkler spacing, clearances) |
| Column Callout on field change | Per-discipline product validation |
| Invoice → tax/charge validation | FP BOM → fire code compliance |
| ModelValidator.docValidate() | PlacementValidator per jurisdiction |

The BOM walker does not know what ARC or FP means. It just recurses
(§2.2.1). The YAML `disciplines:` section is a classifier — it maps
`ifc_class → bom_category` — not a structural change. The discipline
label on `m_bom.bom_category` is the hook that AD_Val_Rule uses to fire
the right validation rules (see `DocValidate.md`).

**MEP disciplines** (FP, ACMV, ELEC, SP, CW, LPG) are not structurally
different from ARC. They follow the same tack convention (§4), the same
BUFFER invariant (§4.2), the same validateBOM() check. What differs is
the validation rules that fire after placement — spacing, clearance,
capacity — just as an Invoice has tax rules that fire after line entry.

---

## 2. The Gospel Principle

Reference buildings are treated as **gospel** — authoritative, immutable truth.

```
Extract (IFC source)  →  Commit ({PREFIX}_BOM.db)  →  Reproduce (compile)  →  Verify (gates)
```

**`{PREFIX}_BOM.db` is a pure dictionary** — never written to during compilation. It defines
assembly recipes, product dimensions, building type configuration, and spatial rules.
All extracted from reference buildings and curated as immutable data.

Every element the compiler produces must trace to a real IFC source. If it cannot
be traced, the output is invalid. This is the first principle.

**EntityType enforcement:** Dictionary records (entity_type='D') are read-only at
the PO layer. Verbs create new records as entity_type='U'. The guard is in code
(MBOM.beforeSave / MBOMLine.beforeSave), not documentation.

---

## 2.1. IFC→BOM Stage — Top-Down AABB Decomposition

*Preamble to compilation: how extracted IFC data becomes a BOM tree.*

The IFCtoBOM pipeline reads `I_Element_Extraction` (component_library.db) and a
classification YAML, then produces a `*_BOM.db` with spatial arrangement (m_bom + m_bom_line).
Products are created in component_library.db first (persistent catalog, reused across buildings),
then transitionally copied to BOM DB. 9 BomValidator checks + 2 pre-flight guards enforce data integrity pre-commit.
See [`YAMLGuide.md`](YAMLGuide.md) §Step 5 for the full pipeline table and §Drift Prevention
for the guard list. The decomposition is **top-down** — from the largest AABB to the smallest —
with each layer stopping when it has assigned its children.

### 2.1.1 Decomposition Layers

```
BUILDING AABB (computed from all extracted elements)
  │
  ├─ split by STOREY (storey names from YAML + I_Element_Extraction.storey)
  │    → compute per-storey AABB from elements in that storey
  │
  ├─ split by DISCIPLINE (from YAML disciplines: map + I_Element_Extraction.discipline)
  │    → ARC_DX_L1, PLB_DX_L1, ELC_DX_L1 etc.
  │    → per-discipline AABB from elements in that discipline×storey
  │
  ├─ split by SCOPE SPACE (from YAML floor_rooms: spaces with AABB)
  │    → elements whose centroid falls within scope space AABB
  │    → produces SET BOMs (LIVING_SET, KITCHEN_SET, etc.)
  │    → dedup: identical M_Product entries get qty, not duplicate BOM lines
  │
  └─ LEAF elements + BUFFER space
       → leaves = M_Product refs with dx/dy/dz (parent-relative from extraction)
       → buffer space fills when validateBOM() detects parent AABB ≠ SUM(children)
```

### 2.1.2 Each Layer Stops

| Layer | Input | Output | Stops When |
|-------|-------|--------|-----------|
| BUILDING | all elements | BUILDING BOM + AABB | storey assignment complete |
| STOREY | elements in this storey | FLOOR BOM + AABB | discipline assignment complete |
| DISCIPLINE | elements in this discipline×storey | DISC BOM + AABB | scope space assignment complete |
| SCOPE SPACE | elements in this room scope | SET BOM + AABB | all elements assigned to leaves |
| LEAF | single M_Product | m_bom_line with dx/dy/dz | terminal — no further split |
| BUFFER | remaining AABB gap | PHANTOM m_bom_line | fills parent = SUM(children) invariant |

### 2.1.3 YAML Defines Scope Spaces

The classify YAML provides the spatial grid that the pipeline decomposes into.
The pipeline does not invent scope spaces — it reads them from the YAML and
assigns extracted elements by spatial containment (centroid within AABB).

```yaml
floor_rooms:
  Ground Floor:
    bom_id: FLOOR_SH_GF_STD
    spaces:
      - { name: LIVING,   template_bom: SH_LIVING_SET,  aabb_mm: [9069, 1682, 1170] }
      - { name: KITCHEN,  template_bom: KITCHEN_SET,     aabb_mm: [7100, 600, 900] }
      - { name: BATHROOM, template_bom: TOILET_FIXTURES, aabb_mm: [0, 0, 0] }
```

The `aabb_mm` on each space is the scope — the spatial envelope that the pipeline
uses to assign elements. Elements whose centroid falls within this scope become
children of that SET BOM. Elements not matching any scope become structural
(direct children of the storey discipline BOM).

### 2.1.4 Dedup and Product Registration

Identical elements (same `element_ref` pattern) are deduplicated into a single
`M_Product` entry. The BOM line carries `qty` for replicates instead of one
line per instance. This is the iDempiere pattern: 47 duplex receptacles =
1 M_Product (`DUPLEX_RECEPTACLE`) with `qty=47` on the BOM line.

### 2.1.5 Discipline Layer (schema_version 2)

For multi-discipline buildings (DX, Terminal), the YAML `disciplines:` map
inserts a discipline BOM level between STOREY and SCOPE SPACE. See
`DISC_BOM_DESIGN.md` §2 for the 5-level hierarchy and §5 for YAML schema v2.
See `TerminalAnalysis.md` §ERP Model Architecture for how discipline codes
sit on M_BomCategory with `doc_type='CO'` and how ROUTE/TILE verbs map to
M_AttributeSet/Instance.

Single-discipline buildings (SH, schema_version 1) skip this layer — no
discipline wrapper needed. DocBaseType (RE/CO) on M_BomCategory determines
which L2 axis is used: rooms (RE) or disciplines (CO).

### 2.1.6 Recipe vs Placement — The BOM Contract

**`{PREFIX}_BOM.db` is a recipe.** Each m_bom_line is a **type line** — one row per
unique product within its parent BOM, with a qty count or verb formula reference.
The compiler expands type lines into placement instances at compile time.

**output.db holds placements.** Each row in `c_orderline` / `elements_meta` is one
physical element at its world-space coordinate. The compiler produces these by
expanding BOM recipes through verb formulas (TILE grid, ROUTE path, FRAME bay, etc.)
or flat placement (qty=1 for irregular elements).

```
{PREFIX}_BOM.db (RECIPE — factored)          output.db (PLACEMENT — expanded)
┌─────────────────────────────────┐         ┌──────────────────────────────────┐
│ m_bom_line                      │         │ elements_meta / c_orderline      │
│  product_id=PLATE_500x150       │  ──→    │  PLATE_500x150 @ (92.5, -42, 19)│
│  qty=4410                       │ expand  │  PLATE_500x150 @ (93.0, -42, 19)│
│  verb_ref=TILE(15×294, 495mm)   │         │  PLATE_500x150 @ (93.5, -42, 19)│
│                                 │         │  ... (4,410 rows)               │
└─────────────────────────────────┘         └──────────────────────────────────┘
```

**SH/DX happen to be trivially factored** — most products appear once per parent
BOM (qty=1), so recipe and placement look identical. The distinction only becomes
visible at TE scale where 505 unique products expand to 48,428 placement instances.

**Invariant:** `SUM(m_bom_line.qty)` across all leaf BOMs in `{PREFIX}_BOM.db` equals
the element count in output.db. The BOM is the recipe; the output is the cooked meal.

**TE factorization (done, 2026-03-17):** 48,428 elements → 1,442 recipe lines (34:1).
VerbDetector mines 4 verb patterns from extraction centroids:

| Verb | Instances | Fidelity | What it detects |
|------|-----------|----------|-----------------|
| TILE | 12 | PASS (0.0m) | 2D uniform grid (roof plates) |
| FRAME | 60 | PASS (0.0m) | Grid intersections (structural bays) |
| ROUTE | 533 | advisory | Axis-aligned uniform-step runs (pipes, ducts) |
| SPRAY | 46,712 | advisory | Semi-regular grid, 10% tolerance (sprinklers, MEP) |

Step-uniformity guard (R8): each ROUTE leg's consecutive gaps must be within
±20% of the average step. Non-uniform groups fall through to SPRAY or flat writes.
See [`VerbPatternArchitecture.md`](VerbPatternArchitecture.md) for verb taxonomy,
data flow, and fidelity details.

**Pipeline phases (separated, 2026-03-17):**
1. **Populate** (`IFCtoBOMMain --populate`): reference DB → component_library.db (one-time)
2. **BOM pipeline** (`IFCtoBOMPipeline.run()`): reads component_library.db → writes `{PREFIX}_BOM.db`
3. **Compile** (`DAGCompiler`): reads `{PREFIX}_BOM.db` → produces output.db

Phase 1 is skip-guarded in `run_RosettaStones.sh` — runs only when extraction
count is 0 for the building_type. Phase 2 can re-run freely (`rm *_BOM.db`).

### 2.1.7 What IFCtoBOM Does NOT Do

- Does not invent elements (EXTRACT only — every leaf traces to I_Element_Extraction)
- Does not compute placement rules (layout_strategy, z_rule stay NULL — generative future)
- Does not validate against regulations (see `DocValidate.md`)
- Does not fill missing pipes or correct gaps (WYSIWYG — DX corners without connecting pipes are preserved as-is)

---

## 2.2 BOM Compilation Model — Recursive Placement

### 2.2.1 The Rule

Every `m_bom_line` is a placement instruction: "place this child's LBD at
offset (dx, dy, dz) from my LBD." The compiler walks the BOM tree by one
rule: if `child_product_id` resolves to another `m_bom`, recurse into it.
If it resolves to an `M_Product` in `component_library.db`, it is a leaf —
emit the element at the accumulated world position.

**`component_type` does not exist in compilation.** The iDempiere Manufacturing
BOM column (`BUY/MAKE/PHANTOM`) has no role. The walker decides BOM-vs-leaf
purely by whether `child_product_id` has a matching `m_bom` row. The column
remains in the schema for iDempiere compatibility; code must never branch on it.

### 2.2.2 BOM-to-BOM Recursion

```
BUILDING BOM  (LBD = world origin, stored in m_bom.origin_x/y/z)
  └─ (dx,dy,dz) = floor LBD position in building  →  FLOOR BOM
       └─ (dx,dy,dz) = room LBD position in floor   →  ROOM BOM (e.g. SH_LIVING_SET)
            ├─ (dx,dy,dz) = piano position in room   →  Piano (leaf in library)
            ├─ (dx,dy,dz) = sofa position in room    →  SOFA BOM (has children)
            │    ├─ (dx,dy,dz) = couch position in sofa  →  Couch (leaf)
            │    └─ (dx,dy,dz) = table position in sofa  →  Table (leaf)
            └─ (dx,dy,dz) = dining position in room  →  DINING BOM (has children)
                 └─ ...
```

Every line carries a 3D position **(dx, dy, dz)** — where the child's LBD
corner sits within the parent's bounding box. All three axes matter: a piano
at (7.67, 1.61, 0.59) means 7.67m right, 1.61m back, 0.59m up from the
room's LBD corner. The walker does not know or care about BOM types. It just
recurses until it hits a leaf. The hierarchy depth is unlimited.

### 2.2.3 Library Population (Outside Compilation)

When no suitable leaf product exists in `component_library.db`, it must be
created outside the compilation pipeline:

1. **TopologyMaker** — generates mesh geometry for a new product type
   (parametric wall, slab, beam). Runs standalone, not during compilation.
2. **Mesh2Library** (`Mesh2Library.txt` pipeline) — registers the generated
   mesh into `component_library.db` as a new leaf product.

After population, the product is a leaf like any other. The compiler never
knows whether a product was extracted from IFC or generated — it just reads
`component_library.db` and positions.

---

## 3. Two Compilation Modes

### 3.1 Terms

| Term | iDempiere parallel | Definition |
|------|-------------------|------------|
| **ESLine** | S_Resource (spatial workstation) | `co_empty_space_line` — a spatial slot that receives a child BOM. The parent provides the attachment point (tack_from); the child doesn't know its host. |
| **BUFFER** | Phantom BOM line | Fills the gap between SUM(children AABB) and parent AABB. Ensures parent = SUM(children) invariant. |
| **EN-BLOC** | Single-sourcing | Selection cascade finds exactly one BOM → take it whole. |
| **WALK-THRU** | Multi-sourcing | Multiple candidates → walk M_BomCategoryLine slots, pick best fit per slot. |
| **Tack** | Origin datum | Left-Back-Down (LBD) corner of AABB = (minX, minY, minZ) = (0,0,0) in own frame. All offsets are parent-LBD to child-LBD. |
| **BOM** | Bill of Materials | Any `m_bom` row. If `child_product_id` resolves to an `m_bom`, the walker recurses into it. There is no MAKE/BUY distinction — the walker decides by existence. |
| **Leaf** | Purchased item | A `child_product_id` that resolves to `M_Product` in `component_library.db` (no matching `m_bom`). The compiler emits the element here. |

### 3.2 The ESLine Mechanism — Parent Owns the Attachment Point

**The child BOM does not know its parent.** A child BOM does not know which
parent hosts it. A leaf product does not know which BOM contains it. This is by design —
a child can be reused in any parent that has a slot for it.

**The parent provides the attachment point via ESLine:**

```
Parent BOM (e.g. FLOOR_TE_GF)
  │
  ├─ tack_from = [slot_1_origin, slot_2_origin, ...]   ← parent defines WHERE
  │
  └─ M_BomCategoryLine (template)                       ← parent defines WHAT KIND
       │
       ├─ slot 1: BomCategory=ARC, AABB capacity         ← "I need an ARC assembly here"
       ├─ slot 2: BomCategory=STR, AABB capacity         ← "I need a STR assembly here"
       └─ ...
            │
            ▼
       Selection cascade picks best child BOM             ← child is selected, not self-aware
            │
            ▼
       ESLine receives the child at parent's tack_from    ← parent's slot, child's content
```

**ESLine carries the parent's tack_from value.** When the selection cascade picks
a child BOM for a slot, the ESLine tells the compiler: "place this child's LBD
at this position within the parent's frame." The child's tack_to (its own LBD)
meets the parent's tack_from (the slot origin). The child never looks up.

**Why this matters for generative:** In the BIM Designer, the user fills slots.
The parent BOM defines the slots (M_BomCategoryLine). The user picks which child
goes in each slot (selection). The ESLine records the result. The child BOM is
oblivious — it can be swapped, resized, or replaced without touching any other
BOM. This is the iDempiere S_Resource pattern: a workstation (slot) receives
whatever work order (BOM) is assigned to it.

### 3.3 EN-BLOC — The HelloWorld Test

EN-BLOC answers one question: **do all the BOMs fit when restacked?**

One `C_OrderLine` selects the BUILDING BOM. One `CO_EmptySpaceLine` at
origin (0,0,0). No selection cascade, no slot walking, no tack evaluation.
EN-BLOC sees **singularity**: same AABB, same DocBaseType, same DocSubType
→ exactly one BOM matches → take whole. The entire BOM hierarchy is accepted
as-is (`C_DocType AABB = M_Product AABB`, see `PlacementLoader.java:24`).

EN-BLOC does not walk the tack chain — it trusts that the restacked BOMs
produce correct positions because they were extracted from a verified source.
WALK-THRU (§3.4) is where each tack_from is evaluated against slot AABB
and selection cascade.

SH (55), DX (1099), TE (48,428) all compile EN-BLOC.

**What EN-BLOC proves:**
- **Stacking order:** the BOM hierarchy restacks to reproduce the original
- Verb expansion: factored recipes expand to correct instance count
- The BOM data is complete and self-consistent (G1-G6 gates verify this)

**What EN-BLOC does NOT test:**
- Tack evaluation (each dx/dy/dz tested against slot AABB — that's WALK-THRU)
- Multi-candidate selection (only one BOM matches per singularity)
- Slot walking (no M_BomCategoryLine traversal)
- DocValidate compliance (extracted buildings bypass validation)
- PP_Order routing (no assembly sequence needed for restacking)

### 3.4 WALK-THRU (Progressive Stacking)

When multiple BOMs could fit a slot, the compiler walks slots in sequence:

1. Read `M_BomCategoryLine` entries for the parent's BomCategory, ordered by `seq_no`
2. Each entry defines a slot — BomCategory scope + AABB capacity
3. The ESLine for each slot carries the parent's tack_from (attachment origin)
4. Selection cascade picks the best-fitting child BOM for that slot
5. The child's LBD is placed at the ESLine's origin (parent's tack_from)
6. One `C_OrderLine` per slot records WHAT was placed
7. BUFFER fills remaining capacity after all slots are walked

**Three-concern separation per slot:**

| Concern | Table | What it carries |
|---------|-------|----------------|
| WHAT | C_OrderLine | Which child BOM was selected, qty |
| WHERE | CO_EmptySpaceLine | Parent's tack_from for this slot, AABB capacity |
| HOW | PP_Order_Node | Verb parameters (if the child uses verbs) |

**Multi-candidate WALK-THRU is unproven.** All current Rosetta Stones are
EN-BLOC singularities. WALK-THRU with actual choice (multiple candidates per
slot) will first be exercised by the BIM Designer's generative path (DemoHouse).

### 3.5 Selection Cascade

Two fields drive everything: **DocSubType** and **AABB**. A third — **BomCategory**
— scopes the search.

1. **BomCategory** (scope): restricts to correct functional type
2. **DocBaseType/DocSubType** (filter): matches the parent's document type
3. **AABB fit** (primary): child SpaceSize must fit within the ESLine's capacity
4. **Largest volume** (secondary): maximize space usage
5. **seq_no** (tiebreaker): lower preferred

### 3.6 The Rosetta Stone — Launch Booster

The Rosetta Stone exercise (SH/DX/TE) proves the pipeline is **lossless**:
Extract → BOM → Compile → Verify. If compiled output matches the reference IFC
exactly (G1-G6 GREEN), the compiler faithfully reproduces known buildings.

**The Rosetta Stone is a launch booster — it is abandoned once it has worked.**
Its purpose is to calibrate and prove the compilation machinery (tack convention,
BOM recursion, verb expansion, ESLine placement). Once proven, the same machinery
drives generative compilation where the BOM is authored by a human or Designer,
not extracted from IFC.

**What the Rosetta Stone proves for generative:**
- Tack convention correctly reconstructs world positions (§4)
- BOM-to-BOM recursion reaches LEAF correctly (§2.2)
- Verb formulas expand to correct instances (§2.1.6)
- ESLine/slot machinery places children at parent's tack_from (§3.2)
- The pipeline from recipe to placement is deterministic and verifiable

**What the Rosetta Stone does NOT prove:**
- Multi-candidate selection (all stones are EN-BLOC singularities)
- WALK-THRU with actual choice → first test is DemoHouse via BIM Designer
- DocValidate compliance checking → proven separately (`DocValidate.md`),
  applied in BIM Designer's ambient compliance (§18.4 in `BIM_Designer.md`)

---

## 4. Tack Convention — The Spatial Handshake

### 4.0 The One Rule

Every `m_bom_line` is a **tack instruction**: place the child's bounding box
corner at this offset from the parent's bounding box corner.

- **LBD** = Left-Back-Down = bounding box minimum corner = (minX, minY, minZ)
- **Left** = X minimum, **Back** = Y minimum, **Down** = Z minimum
- **dx/dy/dz** on an `m_bom_line` = the position within the parent where
  the child's LBD corner sits

No centroids. No special cases. One rule for every line at every depth.

**tack_from / tack_to (Lego principle):**

A parent BOM has N children. Each child needs a spot in the parent's space.
The parent's `m_bom_line` defines **tack_from** — the (dx,dy,dz) position
within the parent where the child's corner goes. The child's **tack_to** is
always its own LBD = (0,0,0) in its own frame.

```
SH_LIVING_SET  (parent AABB = 8000 × 2000 × 1200 mm)
  │
  ├─ tack_from=(0.3, 0.1, 0.0) → Piano         tack_to=(0,0,0) = piano's LBD
  ├─ tack_from=(2.5, 0.8, 0.0) → SOFA_BOM      tack_to=(0,0,0) = sofa group's LBD
  ├─ tack_from=(5.8, 0.2, 0.0) → Dining Table   tack_to=(0,0,0) = table's LBD
  └─ BUFFER fills remaining space (§4.2)
```

Each `tack_from` is a named slot in the parent. The child's LBD corner lands
there. The child doesn't know the parent — it can be reused in any parent
that has a slot big enough for it. This is the Lego principle: the stud
pattern is on the parent (tack_from), the child just has a flat bottom (tack_to).

### 4.1 World Coordinate Reconstruction

```
element_LBD = building_origin + tack_from[1] + tack_from[2] + ... + tack_from[N]
centroid    = element_LBD + (width/2, depth/2, height/2)
```

where each `tack_from[i]` is the full 3D position `(dx, dy, dz)` from that
level's m_bom_line. The walker accumulates all three axes through the BOM chain.

Centroid is computed **only at the output stage** — for display, for spatial
digest, for comparison with extraction. Centroid never enters the BOM. The BOM
stores only tack positions (dx/dy/dz) and AABB dimensions.

**Example (SH Living Room):**
```
BUILDING (origin = -9.235, -2.746, -0.470)     ← world LBD stored in m_bom.origin_x/y/z
  tack_from=(0.0, 0.0, 0.0) → FLOOR BOM        ← ground floor LBD = building LBD
    tack_from=(2.2, 5.2, 0.5) → LIVING BOM      ← living room LBD sits here in the floor
      tack_from=(0.3, 0.1, 0.0) → Piano (leaf)  ← piano sits here in the living room
      tack_from=(2.5, 0.8, 0.0) → SOFA BOM      ← sofa group sits here
        tack_from=(0.0, 0.0, 0.0) → Couch        ← couch at sofa group's LBD corner
        tack_from=(1.2, 0.3, 0.0) → Coffee Table  ← table offset from sofa LBD
```

Piano world LBD:
  X = -9.235 + 0.0 + 2.2 + 0.3 = -6.735
  Y = -2.746 + 0.0 + 5.2 + 0.1 = 2.554
  Z = -0.470 + 0.0 + 0.5 + 0.0 = 0.030

Piano centroid = piano_LBD + (piano_width/2, piano_depth/2, piano_height/2)

**Scope box origin (YAML `origin_m`)** is a **containment filter only** — it
determines which elements belong to which room during BOM generation. It is NOT
a spatial reference for offsets. The tack_from on the FLOOR→ROOM line comes from
the room's measured LBD relative to the floor's LBD, not from the scope box origin.

### 4.1.1 validateBOM() — The Spatial Analogue

In iDempiere Manufacturing, `validateBOM()` checks that a BOM is complete and
consistent before it can be used in production. The spatial analogue is:
- Every child's tack_from must place it within the parent's AABB
- Children must not overlap (clash detection)
- The sum of children + BUFFER must account for the parent's full AABB (§4.2)
- All tack_from values are non-negative (child cannot be behind parent's origin)

This validation runs in `BomValidator.java` (9 check methods) and is the spatial
equivalent of the ERP manufacturing BOM integrity check.

### 4.2 BUFFER — The Completeness Invariant

**Definition:** A BUFFER is a phantom m_bom_line that fills the remaining AABB
gap so that the parent's allocated dimensions equal the sum of its children's
allocated dimensions along each axis.

**Invariant:** For every non-leaf BOM:
```
parent.width  = SUM(children.allocated_width)    along tack-X
parent.depth  = SUM(children.allocated_depth)    along tack-Y
parent.height = SUM(children.allocated_height)   along tack-Z
```

Where `allocated_dim` = the child's own AABB dim along that axis (or the
BUFFER's phantom dim for the remainder). The BUFFER absorbs the gap between
the sum of real children and the parent envelope.

**Why BUFFER matters:** Without BUFFER, the compiler cannot prove that a BOM's
spatial claim is fully accounted for. BUFFER is the difference between a recipe
that says "these items go here" and one that says "these items fill exactly
this space." The second form is verifiable; the first is not.

**Witness claims (pending implementation):**
- W-TACK-1: `dx >= prev_sibling.dx + prev_sibling.allocated_width`
- W-BUFFER-1: `SUM(children.allocated_width) == parent.width` (per axis)
- W-WALKTHRU-DIFFERS-1: WALK-THRU output differs from EN-BLOC for multi-candidate slots

### 4.3 Centroid Drift — Historical Note (FIXING)

**Status:** DisciplineBomBuilder (CO path: TE) now uses tack offsets correctly (fixed session 18).
ScopeBomBuilder (RE path: SH, DX) still uses centroid-relative offsets — **next fix**.

**Root cause:** Commit `1399128` (2026-03-10) introduced centroid-floorMin as
"parent-relative" — it passed EN-BLOC tests because centroid offsets round-trip
correctly when there is no stacking. ScopeBomBuilder (session 15) inherited the
same centroid pattern. The +halfW recovery formula added in session 18 exposed
the inconsistency by shifting furniture elements that still store centroid offsets.

**Why centroid breaks:** Centroid offsets cannot tile. If child A is 1m wide and
starts at parent LBD, child B should start at dx=1.0. With centroids, child A's
offset is 0.5 (its center), and child B's offset must account for A's width plus
its own half-width — the formula becomes context-dependent. LBD offsets are always
`child_minX − parent_minX`, regardless of sibling dimensions.

**Code to fix:** `ScopeBomBuilder.java` (SH/DX scope-based rooms) and
`FloorRoomBomBuilder.java` (room BOM MAKE lines need proper LBD offsets,
currently all dx=0).

---

## 5. The 9-Stage Pipeline

| # | Stage | What it does |
|---|-------|-------------|
| 1 | **Metadata** | Referential integrity checks against `{PREFIX}_BOM.db` |
| 2 | **Parse** | Reads `.bim` DSL text into records |
| 3 | **Compile** | Produces `BuildingSpec` from BOM hierarchy |
| 4 | **Template** | ST-mode: walks M_BomCategoryLine slots |
| 5 | **Write** | Emits SQLite output DB |
| 6 | **Verb** | BIM COBOL script hook → PP_Order_Node audit trail |
| 7 | **Digest** | Per-element SHA256 spatial fingerprint |
| 8 | **Geometry** | Mesh integrity validation |
| 9 | **Prove** | Mathematical placement proofs |

Both compilation modes follow the same data flow: element positions are read from
m_bom_line (tack offsets per §4) and accumulated through the BOM chain into
world coordinates. EN-BLOC takes the BUILDING BOM as-is; WALK THRU recalculates
by re-walking each BOM layer.

---

## 6. BIM COBOL — Verb-Driven BOM Mutation

The GUI emits BIM COBOL verbs, never direct SQL. 63 verbs in 5 tiers:

| Tier | Verbs | Purpose |
|------|-------|---------|
| P0 Primitive | CREATE BOM, ADD LINE, SET TACK, SET ROTATION, SET DIMENSIONS, REMOVE LINE, DELETE BOM, SET LINE PROPERTY | BOM CRUD atoms |
| Utility | VALIDATE AABB, SNAP TO GRID, EXTRACT AABB | Validation + transform |
| L1 Convenience | CREATE ROOM, FURNISH ROOM, RESIZE ROOM, STRIP ROOM | Room-level composed verbs |
| Data | SELECT, LIST, DESCRIBE, COUNT, AGGREGATE, EXPORT, CLONE, SUMMARIZE BOM | Query + export |
| Original | PLACE BOM, EN BLOC, WIRE LIGHTING, ROUTE SPRINKLERS, TILE SURFACE, CHECK BOM, ... | Geometry + inspection |

**Layered composition:** L1 verbs call P0 primitives. L2 (floor-level) will call
L1. Never skip layers. Each verb = one file, one keyword, one payload record.

Full grammar spec: [`docs/BIM_COBOL.md`](BIM_COBOL.md)

---

## 7. Verification: The Rosetta Stone Gate

**Maths that proves visuals without cheating.**

> **Verification gates:** See [`TestArchitecture.md`](TestArchitecture.md) §Verification for the complete G1-G6 gate specification, tamper rules (T1-T16), and the 4-layer defense model.

All 6 gates GREEN for SH (55 elements) and DX (1099 elements).

---

## 8. What This Is Not

| It is NOT | Because |
|-----------|---------|
| Revit / ArchiCAD | Those are authoring tools. This is a reproducer from committed data. |
| Rule-based AI | No heuristics, no ML. Selection cascade is deterministic. |
| Parametric design | Parameters come from extracted BOM data, not design exploration. |
| Approximate | AABB matching is 3D exact. Digest is SHA256. No tolerance. |
| Interactive | Batch compilation. Like COBOL/ERP — process the order, produce the output. |

---

## 9. The End State

The compiler runs without human assistance. Given a `.bim` DSL file and two source
databases (`{PREFIX}_BOM.db` + component_library.db), it produces a complete, verified output.

The cycle: **Extract → Commit → Compile → Verify → Fix → Repeat** until all gates pass.

Adding a new building = adding BOM data. The compiler is the constant.

---

---

## 10. Dynamic Building Registration

Adding a building type in the Java compiler requires zero code changes — `BuildingRegistry.loadActive()`
reads C_DocType from `{PREFIX}_BOM.db` and compiles every active row. But the Python and shell tooling that
*populates* `{PREFIX}_BOM.db` hardcodes building names, storey mappings, output paths, and expected element
counts in four files. This is like hardcoding part numbers into the MRP engine instead of reading
the BOM. When extraction counts change or a new building appears, the scripts drift from the data.

This section specifies how to close that gap.

### 10.1 The Problem: Hardcoded Registration

The Java side is clean — `BuildingRegistry` (line 70) issues one SQL query with zero building
names in source code. The Python/shell side is not:

| # | File | Lines | What is hardcoded |
|---|------|-------|-------------------|
| 1 | `scripts/RosettaStoneExtract.py` | 37–60 | `BUILDINGS` dict: prefixes (`SH`, `DX`), doc_sub_type, building_bom_id, storey name→code mappings, roles, seq_no |
| 2 | `scripts/RosettaStoneToBOM.py` | 48–55 | `C_DOCTYPE` list: doc_type_id, DocBaseType, DocSubType, output/reference paths, ExpectedElements (55, 1099, 139, 51088) |
| 3 | `scripts/RosettaStoneToBOM.py` | 345–439 | `M_BOM` / `M_BOM_LINE`: BUILDING BOM headers with AABB, TACK children with dx/dy/dz offsets |
| 4 | `scripts/run_RosettaStones.sh` | 39–40, 418–435 | `SH_BASE` / `DX_BASE` path variables, `case` dispatch with literal `"SH"` / `"DX"` |
| 5 | `scripts/run_tests.sh` | 125–136 | `run_preflight "Ifc4_SampleHouse"` / `"Ifc2x3_Duplex"` — literal building names |
| 6 | `scripts/run_tests.sh` | 145–183 | Expected pass/fail counts (191/1, 34/2, 19/0, 179/7) and intentional-failure offsets |

**Consequence:** Every time an extraction changes element counts, a developer must hand-edit
expected values in at least two files. Adding a third EXTRACTED building requires touching all four.
The Java compiler handles it with one C_DocType row. The tooling should too.

### 10.2 Building Manifest (Source of Truth)

A YAML configuration file `scripts/construction_manifest.yaml` declares each building's identity
and structure. The manifest is an index into extraction truth — it names things, never measures them.

```yaml
# construction_manifest.yaml — declarative building registration
# Rule: storey names, roles, paths.  Never counts or dimensions.

buildings:
  Ifc4_SampleHouse:
    prefix: SH
    doc_sub_type: SH
    doc_base_type: RE
    provenance: EXTRACTED
    output_path: DAGCompiler/lib/output/ifc4_samplehouse.db
    reference_path: DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db
    seq_no: 10
    storeys:
      Ground Floor: { code: GF, bom_category: GF, role: GROUND_FLOOR, seq: 1010 }
      Roof:         { code: ROOF, bom_category: RF, role: ROOF, seq: 1020 }
      Unknown:      { code: CW, bom_category: CW, role: CURTAIN_WALL, seq: 1030 }

  Ifc2x3_Duplex:
    prefix: DX
    doc_sub_type: DX
    doc_base_type: RE
    provenance: EXTRACTED
    output_path: DAGCompiler/lib/output/ifc2x3_duplex.db
    reference_path: DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db
    seq_no: 20
    geometry_fail_threshold: 5
    storeys:
      Level 1: { code: L1, bom_category: L1, role: LEVEL_1, seq: 1010 }
      Level 2: { code: L2, bom_category: L2, role: LEVEL_2, seq: 1020 }
      Roof:    { code: ROOF, bom_category: RF, role: ROOF, seq: 1030 }
      T/FDN:   { code: FDN, bom_category: FN, role: FOUNDATION, seq: 1040 }
      Unknown: { code: MISC, bom_category: MS, role: MISC, seq: 1050 }
```

**iDempiere pattern:** The manifest plays the same role as `AD_Table` — it defines metadata
that scripts consume, so no script contains application logic about what buildings exist.
One file is the single source of registration truth. Scripts import it; they never duplicate it.

### 10.3 Derived Values (Never Hardcode)

Some values currently hardcoded in the scripts are not properties of the building's identity.
They are measurements that can — and must — be computed from extraction data.

| Value | Current source | Correct source | Why |
|-------|---------------|----------------|-----|
| `ExpectedElements` (55, 1099) | Hardcoded in `C_DOCTYPE` tuple | `COUNT(*)` from extraction DB | Extraction is the measurement |
| `AABB` on BUILDING BOM | Hardcoded in `M_BOM` list | Computed from extraction envelope | Gospel Principle: extraction = truth |
| Floor AABB dimensions | Hardcoded in `M_BOM` list | Aggregated from storey extraction | Same principle at floor level |
| `M_BOM_LINE` offsets (dx/dy/dz) | Hardcoded in builder tuples | Computed from extraction storey origins | Offsets are measured, not declared |
| DSL content | Generated inline | Derived from manifest + extraction | Content follows structure |
| Spatial digest reference | Implicit in gate thresholds | SHA256 of compiled output | Digest is a function of data |

**Rule:** If a value can be derived from extraction data, it MUST be derived, not declared.
The manifest declares identity (name, role, path). Extraction measures reality (count, envelope,
offset). Hardcoding a derived value violates the Gospel Principle — it replaces measurement with
opinion.

### 10.4 Target Workflow: Add a New Building

After migration, adding a new building follows the same pattern as adding a new product to an
ERP system — one BOM entry, zero code changes:

1. **Manifest entry** — add one YAML block to `construction_manifest.yaml` (name, prefix,
   doc_sub_type, storeys, paths)
2. **Extract IFC** — run extraction pipeline; reference DB appears at the declared path
3. **Regenerate BOM dictionary** — IFCtoBOM Java pipeline (SH/DX) or `RosettaStoneToBOM.py` (TE)
   reads classification YAML + extraction, writes M_BOM, M_BOM_Line with derived counts and dimensions
4. **Verify** — `./scripts/run_RosettaStones.sh classify_XX.yaml` compiles and gates the new building

Zero Python edits. Zero shell edits. Zero Java edits. The manifest is the only touch point.

### 10.5 Script Migration

Each script transitions from hardcoded values to manifest-driven or `{PREFIX}_BOM.db`-driven lookups:

| Script | Reads now | Reads after | Change scope |
|--------|-----------|-------------|-------------|
| `RosettaStoneExtract.py` | `BUILDINGS` dict (lines 37–60): prefixes, storey maps, BOM IDs | `construction_manifest.yaml`: same data, external file | Replace dict literal with `yaml.safe_load()`. Storey mappings move verbatim. |
| `RosettaStoneToBOM.py` | `C_DOCTYPE` list (lines 48–55): paths, ExpectedElements. `M_BOM` / `M_BOM_LINE` (lines 345–439): AABB, offsets | Manifest for identity; extraction DBs for counts, AABB, offsets | Biggest change. Builder loops derive dimensions from extraction instead of literals. |
| `run_RosettaStones.sh` | `SH_BASE` / `DX_BASE` variables, `case` dispatch (lines 39–40, 418–435) | Query `SELECT DocSubType, output_path FROM C_DocType WHERE IsActive=1` from `{PREFIX}_BOM.db` | Shell reads `{PREFIX}_BOM.db` via `sqlite3`. Loop replaces case statement. |
| `run_tests.sh` | Literal `"Ifc4_SampleHouse"` / `"Ifc2x3_Duplex"` in preflight calls (lines 125–136); hardcoded expected counts (lines 145–183) | Preflight: query C_DocType for active EXTRACTED buildings. Counts: read from `{PREFIX}_BOM.db` or test output. | Preflight becomes a loop. Expected counts derived, not literal. |

### 10.6 Invariants

Six rules govern the boundary between declaration and derivation:

1. **Manifest is declarative** — storey names, roles, paths, prefix. Never counts, dimensions,
   or coordinates.
2. **Extraction is measurement** — element counts, AABB envelopes, storey origins. These are
   facts read from IFC reference data, not human assertions.
3. **`{PREFIX}_BOM.db` is artifact** — produced by IFCtoBOM Java pipeline (SH/DX) or
   `RosettaStoneToBOM.py` (TE) from classification YAML + extraction.
   It is never hand-edited. Regeneration is idempotent.
4. **Java reads `{PREFIX}_BOM.db` only** — `BuildingRegistry.loadActive()` queries C_DocType. It never
   reads the manifest, never reads extraction DBs, never contains building names.
5. **Shell reads `{PREFIX}_BOM.db` or manifest only** — no script contains a building name as a string
   literal (except in log messages). Control flow is driven by query results.
6. **Drift resistance** — if extraction changes element count from 1099 to 1102, the only
   action is re-running the IFCtoBOM pipeline. No file is hand-edited. No test threshold
   is manually adjusted.

### 10.7 Migration Path

Migration proceeds in five phases, each self-contained and independently verifiable.

| Phase | Scope | Risk | Status |
|-------|-------|------|--------|
| **A** | Create `construction_manifest.yaml` | None | **DONE** — manifest created with SH, DX, TE, ST entries |
| **B** | `RosettaStoneExtract.py` reads manifest | Low | **DONE** — `BUILDINGS` dict replaced with `yaml.safe_load()` |
| **C** | `RosettaStoneToBOM.py` reads manifest | Medium | **DONE** — `C_DOCTYPE` list replaced with `_build_c_doctype()` |
| **D** | `run_RosettaStones.sh` queries `{PREFIX}_BOM.db` | Low | **DONE** — loop replaces case statement, summary dynamic |
| **E** | `run_tests.sh` derives expected counts | Low | Deferred — not a Rosetta Stone concern |

**Gate between phases:** `./scripts/run_RosettaStones.sh all` must produce identical output
after each phase. All phases A–D gated GREEN: SH=55, DX=1099, 0 geometry divergences.

**Note:** TB-LKTN removed from manifest — generative case will be approached fresh once
the EXTRACTED registration pipeline is stable. Terminal (CO_TE) registered with TE-1
storey normalisation DONE (48,428 active elements, 7 storeys, 8 disciplines).

---

*Detailed architecture: [`ConstructionAsERP.md`](ConstructionAsERP.md) |
BOM dimensions: [`BIMasBOMConcept.md`](BIMasBOMConcept.md) |
Assembly hierarchy: [`PREFAB_ARCHITECTURE.md`](PREFAB_ARCHITECTURE.md) |
Terminal ERP model: [`TerminalAnalysis.md`](TerminalAnalysis.md) §ERP Model Architecture |
Action roadmap: [`ACTION_ROADMAP.md`](ACTION_ROADMAP.md)*
