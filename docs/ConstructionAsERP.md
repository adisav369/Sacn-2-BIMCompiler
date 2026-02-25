# Construction as ERP

*How BIM compilation maps to iDempiere C_Order → BOM explosion → spatial resolution*

> **Governing principle:** A construction project is a C_Order. The BOM catalog defines
> WHAT can be built. The C_OrderLine selects WHICH BOMs to use. CO_EmptySpace tracks
> WHERE things sit in construction space. Three databases, three concerns, no overlap.

---

## 1. Three Databases — Separation of Concerns

### 1.1 component_library.db — the Product Catalog (M_Product)

Pure LOD mesh + intrinsic geometry. No relationships, no assembly logic.

| Table | iDempiere | Content |
|-------|-----------|---------|
| `ad_product_dim` | M_Product | LOD mesh ref, top/front bbox, width, depth, height, weight |
| `ad_parametric_mesh` | M_Product (parametric) | Generator class + params for procedural geometry |
| `ad_parametric_mesh_param` | AD_Parm | Shape generator parameters |
| `surface_styles` | M_Product_Acct (material) | Material name, RGBA colour per product |

**What it is:** A parts warehouse. Every item has a shape, a size, and a colour.
Nothing here knows about assemblies, buildings, or placement.

### 1.2 BOM.db — the Assembly Catalog (M_BOM)

Relationships between products. How parts combine into assemblies.
Rich spatial info: SpaceSize (AABB), orientation rules, locator references.

| Table | iDempiere | Content |
|-------|-----------|---------|
| `m_bom` | M_Product + M_BOM | Assembly definition: bom_category (WHAT), bom_owner (WHO) |
| `m_bom_line` | M_BOM_Line | Child placement: dx/dy/dz, rotation_rule, locator_ref, SpaceSize |
| `m_attribute` | M_Attribute | Leaf attributes: ports, clearances, UBBL rules |
| `M_BomCategory` | M_Product_Category | Functional type: LI, BD, KT, FR, ST, L1, L2, UN |

**What it is:** An assembly manual. "A Duplex Unit contains Level 1 + Level 2.
Level 1 contains Living Room + Kitchen + Bathroom. Living Room contains Piano +
Sofa Set + Buffer Space." Every construct carries its AABB so the parent=SUM(children)
invariant holds.

**Relationship to component_library.db:** Leaf M_BOM items (no M_BOM_Line children)
reference `ad_product_dim.product_id` for their physical geometry. The BOM is the
recipe; the product catalog has the ingredients.

### 1.3 output.db — the Compiled Result (C_Order output)

The work order's compiled output. IFC-compatible elements with world coordinates.

| Table | Content |
|-------|---------|
| `elements_meta` | Compiled elements (guid, ifc_class, storey, world xyz) |
| `element_instances` | Geometry instances (transform matrix, material) |
| `element_assemblies` | Assembly grouping (parent-child in output) |
| `co_empty_space` | Construction space header (per C_Order) |
| `co_empty_space_line` | Spatial resolution per BOMLine (before/next, orientation) |

---

## 2. C_Order — the Construction Order

The building project IS a C_Order. Not BIM, not DSL — **C_Order** directly.

```
C_Order (= ad_building_registry)
│   C_Order_ID     = building_type ('Ifc2x3_Duplex')
│   BOM_Vendor     = bom_owner ('DX')          ← C_BPartner
│   Description    = 'Duplex residential unit'
│   DocStatus      = 'DR' → 'CO'
│
├── Tab: C_OrderLine (= ad_element_rule)
│   │   Selects M_BOMs from BOM.db and places them
│   │
│   └── Sub-tab: BOM (read-only copy from BOM.db)
│       │   The selected M_BOM tree, copied verbatim
│       │   Acts as reference during compilation
│       │
│       └── Sub-tab: BOMLine
│           Roof, L1, L2, Floor — expanded children
│           Each child is itself an M_BOM with its own BOMLines
│
└── Tab: CO_EmptySpace
    │   Construction site information
    │   FK: C_Order_ID → ad_building_registry
    │   real_world_location, origin_spot
    │   AABB of whole intended construction space
    │   IsAvailable = Y (start) → N (when first BOMLine placed)
    │
    └── Sub-tab: CO_EmptySpaceLine
        One record per expanded BOMLine
        Holds before/next spatial connecting info + orientation
        Translation layer: BOM.db construct → construction space coordinates
        Can hold MEP spatial refs separately from BOM leaf items
```

### 2.1 Why C_Order?

| Concern | iDempiere | BIM |
|---------|-----------|-----|
| "I want to build a Duplex" | Raise C_Order | INSERT ad_building_registry |
| "Use DX vendor's catalog" | Set C_BPartner | SET bom_owner = 'DX' |
| "Include this BOM" | Add C_OrderLine | INSERT ad_element_rule |
| "What fits where?" | Check WMS availability | Query CO_EmptySpace/Line |
| "Build it" | Process Order | `./scripts/run_tests.sh` (compile) |
| "Edit the spec" | Modify C_OrderLine | UPDATE ad_element_rule |

### 2.2 C_OrderLine — what gets built

Each C_OrderLine (ad_element_rule) selects an M_BOM from BOM.db:

```
C_OrderLine #1:  family_ref = 'UNIT_DUPLEX_STD'    host_type = BUILDING
C_OrderLine #2:  family_ref = 'FLOOR_DX_L1_STD'    host_type = BUILDING
C_OrderLine #3:  family_ref = 'LIVING_SET'          host_type = ROOM, room_ref = 'Rm_Living_1'
C_OrderLine #4:  family_ref = 'BED_SET_MASTER'      host_type = ROOM, room_ref = 'Rm_Bedroom_1'
```

The **BOM sub-tab** on each C_OrderLine shows the M_BOM tree copied verbatim from
BOM.db. This is the product spec — immutable reference. The compiler reads this,
not BOM.db directly, so the scope is locked to what was ordered.

The user edits C_OrderLines: swap a sofa set, remove the piano, add a dining chair.
The compiler reads the final C_OrderLines and resolves.

---

## 3. CO_EmptySpace — Construction Space Tracking

### 3.1 CO_EmptySpace (header)

One record per C_Order. The construction site envelope.

```sql
CREATE TABLE co_empty_space (
    co_emptyspace_id    INTEGER PRIMARY KEY AUTOINCREMENT,
    c_order_id          TEXT NOT NULL,       -- FK → ad_building_registry
    origin_x_mm         REAL NOT NULL DEFAULT 0,
    origin_y_mm         REAL NOT NULL DEFAULT 0,
    origin_z_mm         REAL NOT NULL DEFAULT 0,
    aabb_width_mm       REAL NOT NULL,       -- total construction space X
    aabb_depth_mm       REAL NOT NULL,       -- total construction space Y
    aabb_height_mm      REAL NOT NULL,       -- total construction space Z
    is_available        INTEGER NOT NULL DEFAULT 1,  -- Y at start, N when first BOMLine placed
    doc_status          TEXT NOT NULL DEFAULT 'DR',
    created             TEXT NOT NULL DEFAULT (datetime('now')),
    updated             TEXT NOT NULL DEFAULT (datetime('now'))
);
```

**At project start:** `is_available = 1`, AABB = full intended construction space.
**When first BOMLine appears:** `is_available = 0` — space is being consumed.

### 3.2 CO_EmptySpaceLine (detail)

**A decision log, not a BOMLine mirror.** A new CO_EmptySpaceLine record is created
only at a **decision point** — when the BOM construct requires spatial guidance to
fit into the available space. For 1:1 extracted buildings (SH, DX), there may be
as few as ONE record accepting the top-level BOM. For variant-driven buildings
(TB-LKTN), there may be many records — one per selection/conflict resolution.

```sql
CREATE TABLE co_empty_space_line (
    line_id             INTEGER PRIMARY KEY AUTOINCREMENT,
    co_emptyspace_id    INTEGER NOT NULL,    -- FK → co_empty_space
    bom_line_seq        INTEGER NOT NULL,    -- sequence from M_BOM_Line
    bom_id              TEXT NOT NULL,        -- which M_BOM this line accepts
    bom_line_role       TEXT,                 -- role from M_BOM_Line (WALL_EXT, FURNITURE, etc.)
    bom_level           INTEGER DEFAULT 0,   -- depth in BOM tree (0=top, 1=floor, 2=room, etc.)

    -- Spatial translation: BOM.db construct → construction space
    before_x_mm         REAL,    -- anchor point BEFORE this item (connecting to previous)
    before_y_mm         REAL,
    before_z_mm         REAL,
    next_x_mm           REAL,    -- anchor point AFTER this item (connecting to next)
    next_y_mm           REAL,
    next_z_mm           REAL,
    orientation_rad     REAL DEFAULT 0,      -- resolved orientation in radians

    -- Space accounting
    capacity_mm         REAL,    -- locator extent (from room boundary)
    filled_mm           REAL DEFAULT 0,
    remaining_mm        REAL,    -- available space at this locator

    -- Locator reference
    storey              TEXT,
    room_name           TEXT,
    locator_ref         TEXT,                 -- NORTH_WALL, CENTRE, FLOAT...

    -- Extensible: MEP spatial refs, 7D IoT refs, etc.
    mep_ref             TEXT,                 -- MEP connection point (future)

    doc_status          TEXT NOT NULL DEFAULT 'DR',
    created             TEXT NOT NULL DEFAULT (datetime('now')),
    updated             TEXT NOT NULL DEFAULT (datetime('now')),

    FOREIGN KEY (co_emptyspace_id) REFERENCES co_empty_space(co_emptyspace_id)
);
```

### 3.3 When a CO_EmptySpaceLine is created

A new line is spawned at a **decision point** — when the translation engine encounters
a BOM level that requires spatial guidance:

| Trigger | What happens | Example |
|---------|--------------|---------|
| **Acceptance** | BOM fits the available space; record the translation | DX: UNIT_DUPLEX_STD accepted into full AABB |
| **Variant selection** | Multiple M_BOMs compete; record which one won | TB-LKTN: choose smaller LIVING_SET variant |
| **Space conflict** | BOM peer competes for same zone; record partition | Two furniture sets for one room |
| **Orientation change** | Room shape differs from BOM assumption; record resolved radians | Rotated L2 rooms (180° vs L1) |

**For SH/DX, lines are sparse.** There is only ONE variant at every BOM level.
One UNIT, one FLOOR, one LIVING_SET, one BED_SET. No variant to compare, no space
conflict. The BOM tree unfolds deterministically from the single top-level acceptance.
Everything below it translates 1:1 because it was extracted from the exact geometry.

In normal mode, DX may produce as few as **one CO_EmptySpaceLine** — accepting
UNIT_DUPLEX_STD into the full construction AABB. All children translate
deterministically from that single acceptance. Nothing should be amiss.

**For TB-LKTN, lines are dense.** TopologyMaker creates room variants. The furniture
sets need SpaceSize matching. Each selection decision spawns a CO_EmptySpaceLine,
recording which variant won, what orientation was resolved, what space remains.

### 3.4 What CO_EmptySpaceLine holds

**The BOMLine tab is the WHAT (copied from BOM.db, unchanged).**
**CO_EmptySpaceLine is the WHERE + HOW (spatial translation for this construction).**

Each line records the guidance that translates abstract BOM info into concrete
construction coordinates:

- **before/next** — the GPD anchor chain in mm. Each item's `next` is the next item's
  `before`. This is the spatial connecting info that turns abstract dx/dy/dz offsets
  into construction-space positions.
- **orientation_rad** — the resolved orientation. BOM.db stores abstract rules
  (`FACE_INTO_ROOM`, `PARALLEL_TO_WALL`); the line stores the concrete radians
  for this particular room shape.
- **remaining_mm** — buffer space still available. Visible for fit queries
  ("can a lampshade fit here?").
- **mep_ref** — MEP connection point. Separate from the BOM leaf item.
  The BOM leaf stays pure product data; the MEP spatial reference is a
  construction concern tracked on the EmptySpaceLine.

### 3.5 Reprocess Mode

A compile flag `--reprocess-all` forces the full layer-by-layer walk even for
SH/DX. In this mode, a CO_EmptySpaceLine is written for **every BOM level and
every child** — tedious but systematic:

```
Reprocess mode — DX (verbose, one line per BOM level):
  Line #1: UNIT_DUPLEX_STD       level=0  accepted into full AABB
  Line #2: FLOOR_DX_L1_STD       level=1  before=(0,0,0) next=(0,0,3000)
  Line #3: FLOOR_DX_L2_STD       level=1  before=(0,0,3000) next=(0,0,6000)
  Line #4: Rm_Living_1/LIVING    level=2  before=(208,-5246,0) orient=0
  Line #5: Rm_Dining_1/DINING    level=2  before=(208,-8554,0) orient=0
  ...
  Line #12: Piano                level=3  before=(1620,3308,0) next=(3120,3308,0) orient=π
  Line #13: Buffer_NW            level=3  remaining=254mm
  Line #14: Sofa_3Seat           level=3  before=(3374,3308,0) next=(5374,3308,0) orient=π
```

**Why this matters:**
- For SH/DX it is pure verification — if any CO_EmptySpaceLine shows a translation
  error (wrong orientation, misaligned before/next), the bug is pinpointed to that
  exact BOM level.
- For TB-LKTN it is the actual working mode — every level has real decisions.
- The same code path handles both. The difference is only in how many decisions
  are non-trivial (zero for SH/DX, many for TB-LKTN).

---

## 4. BOM Explosion Process

### 4.1 The trigger

User raises a C_Order (ad_building_registry) with `bom_owner = 'DX'`.
Process button (DAGCompiler / `run_tests.sh`) fires the explosion.

### 4.2 The chain — DX example

```
Step 1: C_Order selects top-level M_BOM
        M_BOM = UNIT_DUPLEX_STD (bom_category='UN', bom_owner='DX')

Step 2: Explode BOMLines (first generation)
        UNIT_DUPLEX_STD → M_BOM_Lines:
          seq=1  FLOOR_DX_L1_STD  (bom_category='L1')  dZ=0
          seq=2  FLOOR_DX_L2_STD  (bom_category='L2')  dZ=3000mm
          seq=3  ROOF_ASSEMBLY    (bom_category='RF')

Step 3: Explode each child (second generation)
        FLOOR_DX_L1_STD → M_BOM_Lines:
          seq=1  LIVING_SET              → Rm_Living_1    (bom_category='LI')
          seq=2  DINING_SET              → Rm_Dining_1    (bom_category='DN')
          seq=3  KITCHEN_CABINET_SET     → Rm_Kitchen_1   (bom_category='KT')
          seq=4  TOILET_BLOCK_FIXTURES   → Rm_Bath_L1     (bom_category='BT')

Step 4: Explode room sets (third generation)
        LIVING_SET → M_BOM_Lines:
          seq=1  Piano         (bom_category='FR', leaf)     space=1500×600mm
          seq=2  SOFA_AREA     (bom_category='FR', sub-BOM)  space=2000×800mm
          seq=3  Loveseat      (bom_category='FR', leaf)     space=1600×800mm
          seq=4  Buffer_NW     (bom_category='ST', variable)
          seq=5  Buffer_NE     (bom_category='ST', variable)

Step 5: Explode sub-BOMs (fourth generation)
        SOFA_AREA → M_BOM_Lines:
          seq=1  Sofa_3Seat     (leaf)
          seq=2  Coffee_Table   (leaf)
          seq=3  Side_Tables    (leaf)
```

### 4.3 Normal mode — SH/DX (sparse CO_EmptySpaceLine)

```
Before explosion:
  CO_EmptySpace: AABB = 8383×17384×6000mm, is_available = Y

After explosion (normal mode):
  CO_EmptySpace: is_available = N
  CO_EmptySpaceLine #1: UNIT_DUPLEX_STD  level=0  accepted into full AABB
  (all children translate deterministically — no further lines needed)
```

**Why only one line?** At every BOM level there is exactly ONE candidate:
- One UNIT_DUPLEX_STD → no variant to compare
- One FLOOR_DX_L1_STD, one FLOOR_DX_L2_STD → no peer conflict
- One LIVING_SET for Rm_Living_1 → no space-fit decision
- One Piano, one Sofa → no alternative

The BOM tree unfolds deterministically. The translation from BOM.db's abstract
offsets (dx/dy/dz, rotation_rule) to construction coordinates is a pure function
of the single accepted top-level BOM. No branching, no fallthrough, no iteration.

**Nothing should be amiss for SH/DX.** If placement errors occur, the bug is in
the translation function itself (BOM offset → world coordinate), not in variant
selection or space fitting. Reprocess mode (§3.5) pinpoints these.

### 4.4 Why extracted buildings always fit

For extracted buildings (DX, SH), there is only **one** record at each BOM layer.
One roof BOM. One L1. One L2. In L1, one of each room. In each room, one set of
items. The whole construction equals the original extracted model — it fits by
construction because it was extracted from a model that already fit.

The reason they all fit: the original Duplex or SampleHouse IFC file had exactly
these elements at exactly these positions. The BOM in BOM.db was extracted from
that geometry. Replaying the BOM into an identically-sized CO_EmptySpace AABB
produces the original result. There is no spatial conflict because the source
had no spatial conflict.

### 4.5 Variant mode — TB-LKTN (dense CO_EmptySpaceLine)

TB-LKTN has no complete topology set pre-extracted. TopologyMaker creates room
variants. The BOM explosion must iterate:

```
CO_EmptySpace: AABB = 9900×8500×3000mm (TERRACE_MY_1S), is_available = Y

Explosion with variant selection:
  CO_EmptySpaceLine #1: UNIT_TBLKTN_STD        level=0  accepted
  CO_EmptySpaceLine #2: FLOOR_TBLKTN_GF_STD    level=1  accepted (only variant)
  CO_EmptySpaceLine #3: BEDROOM zone → ?       level=2  SpaceSize match:
      candidate: BEDROOM_PREFAB_MY_3100 (3100×3100mm) — fits zone 3134×3105mm ✓
  CO_EmptySpaceLine #4: COMMON zone → ?        level=2  SpaceSize match:
      candidate: LIVING_PREFAB_MY — fits zone 3700×6195mm ✓
  CO_EmptySpaceLine #5: BATHROOM zone → ?      level=2  SpaceSize match:
      candidate: BATHROOM_PREFAB_MY — fits zone 1307×2125mm ✓
  ...
```

**Each line records a real decision.** If the CO_EmptySpace AABB is larger or smaller
than the extracted template:

- Larger → look for bigger variant BOMs
- Smaller → look for smaller variant BOMs (SpaceSize fallthrough)
- No variant exists → TopologyMaker must create it first

TB-LKTN cannot fulfil its construction unless Topology exists for its unit and
floor levels. TopologyMaker creates those constructs. The unit itself can be
another BOM_Category (e.g. an 'SH' type unit reused in a different construction).

For furniture, BOM.db already has all sorts of smaller furnishing constructs —
TB-LKTN can reuse them. Roof, porch, and outer perimeter are also done.
What remains is parametric items (mesh floor, etc.).

---

## 5. Analysis — Translation and Error Diagnosis

### 5.1 The two classes of error

| Error class | Cause | Where it shows | Buildings affected |
|-------------|-------|----------------|-------------------|
| **Translation bug** | BOM offset → world coordinate math is wrong | Reprocess mode: CO_EmptySpaceLine has wrong before/next/orient | SH, DX (and all others) |
| **Variant selection bug** | Wrong M_BOM chosen for available space | Normal mode: CO_EmptySpaceLine records a candidate that doesn't fit | TB-LKTN only |

**For SH/DX, only translation bugs are possible.** There is no variant selection.
The BOM tree is deterministic. If a piano ends up in the wrong place, the
translation function (BOM.db abstract offset → construction mm) is broken. The
BOM itself is correct (extracted from a model that worked).

**For TB-LKTN, both classes are possible.** A wrong variant selection (too-large
BED_SET for a small room) shows up as an overflow in CO_EmptySpaceLine.remaining_mm.
A translation bug shows up as misaligned before/next coordinates.

### 5.2 The recurring placement errors — root cause

The stubborn placement errors we've seen at furniture level (Piano off-centre,
Sofa rotated wrong) are **translation bugs**. They will replicate upward when
unit and floor levels enter the stack — same math, different scale.

CO_EmptySpaceLine in reprocess mode pinpoints exactly where the translation breaks:

```
Expected:  Line #12: Piano  before=(1620,3308,0) orient=π
Actual:    Line #12: Piano  before=(1620,3308,0) orient=0    ← rotation not translated
```

The BOM.db data says `rotation_rule=PARALLEL_TO_WALL`. The CO_EmptySpaceLine
should show `orientation_rad=π` (north wall). If it shows `0`, the translation
function failed to resolve the wall rule.

### 5.3 CO_EmptySpaceLine as the single translation checkpoint

The BOM tab (on C_OrderLine) is unchanged — all of BOM.db copied verbatim.
CO_EmptySpaceLine is the ONLY place where abstract BOM info becomes concrete
construction coordinates. Every orientation error, every misaligned position,
every space overflow is visible in this one table.

**Diagnosis pattern:**
1. Run in reprocess mode (`--reprocess-all`)
2. Query `co_empty_space_line` for the failing element
3. Compare `before/next/orientation_rad` against expected values
4. The delta IS the bug — trace back to the translation function

### 5.4 MEP and future extensions

CO_EmptySpaceLine can hold additional spatial references per function:

| Extension | CO_EmptySpaceLine field | Purpose |
|-----------|------------------------|---------|
| MEP connections | `mep_ref` | Riser point, pipe junction coordinates |
| UBBL clearance | (via M_Attribute on leaf) | Minimum distances from walls/openings |
| 7D IoT | `iot_ref` (future) | Sensor placement, conduit routing |

Each is a separate spatial concern tracked on the EmptySpaceLine. The BOM leaf
stays clean — it holds WHAT the item is. The EmptySpaceLine holds WHERE it goes
in this particular construction and WHAT connects to it. This separation means
future spatial concerns (IoT sensors, conduit routing) can be added as new columns
on CO_EmptySpaceLine without touching the BOM catalog.

---

## 6. ad_room_slot Deprecation

`ad_room_slot` mapped `room_type → assembly_id` (BOM dispatch per room type).
With `bom_category` on M_BOM, this dispatch becomes implicit:

| Old (ad_room_slot) | New (bom_category) |
|--------------------|--------------------|
| `room_type=BEDROOM` → `assembly_id=BED_SET_MASTER` | M_BOM WHERE `bom_category='BD'` AND `bom_owner=C_Order.bom_owner` |
| `room_type=BATHROOM` → `assembly_id=BATHROOM_SET` | M_BOM WHERE `bom_category='BT'` AND `bom_owner=C_Order.bom_owner` |

The BOM_Category + Owner scoping replaces the explicit slot dispatch.
`ad_room_slot` remains in the database but is no longer the primary dispatch
mechanism — it can serve as a compatibility/override layer during migration.

---

## 7. Availability Query — "Can a Lampshade Fit?"

```sql
CREATE VIEW v_co_available_space AS
SELECT
    esl.line_id,
    es.c_order_id,
    esl.storey,
    esl.room_name,
    esl.locator_ref,
    esl.remaining_mm,
    esl.next_x_mm,
    esl.next_y_mm,
    esl.next_z_mm,
    esl.orientation_rad
FROM co_empty_space_line esl
JOIN co_empty_space es ON esl.co_emptyspace_id = es.co_emptyspace_id
WHERE es.doc_status = 'CO'
  AND esl.remaining_mm > 0;
```

Query: "Find space for a 300mm-wide lampshade in the Living Room"
```sql
SELECT * FROM v_co_available_space
WHERE c_order_id = 'Ifc2x3_Duplex'
  AND room_name = 'Rm_Living_1'
  AND remaining_mm >= 300
ORDER BY remaining_mm ASC;  -- tightest fit first
```

This finds both:
- Buffer lines (bom_category='ST') with leftover space
- Locator lines where fixed items didn't fill the wall

---

## 8. Complete ERD

```
component_library.db (Product Catalog)
┌─────────────────────────┐
│ ad_product_dim          │  LOD mesh + bbox + weight
│ ad_parametric_mesh      │  Procedural shape generators
│ surface_styles          │  Material colours
└─────────────────────────┘
          │
          │ product_id FK (leaf M_BOM → ad_product_dim)
          ▼
BOM.db (Assembly Catalog)
┌─────────────────────────┐
│ m_bom                   │  Assembly: bom_category + bom_owner + SpaceSize
│   └── m_bom_line        │  Children: dx/dy/dz, rotation, locator, space_*_mm
│       └── m_bom (child) │  Recursive: M_BOM_Line.child_bom_id → M_BOM
│ m_attribute             │  Leaf attributes: ports, clearances
│ M_BomCategory           │  Lookup: LI, BD, KT, FR, ST, L1, L2, UN
└─────────────────────────┘
          │
          │ family_ref FK (C_OrderLine → M_BOM.bom_id)
          ▼
output.db (Compiled Construction)
┌─────────────────────────┐
│ C_Order                 │  ad_building_registry (bom_owner scopes M_BOM access)
│   ├── C_OrderLine       │  ad_element_rule (selects M_BOM, places in room)
│   │   └── BOM tab       │  M_BOM tree copied from BOM.db (immutable reference)
│   │       └── BOMLine   │  Expanded children at each generation
│   │
│   └── CO_EmptySpace     │  Construction space header (AABB, IsAvailable)
│       └── CO_EmptySpace │  Spatial translation per BOMLine
│            Line          │  (before/next, orient, remaining, MEP ref)
│                          │
│ elements_meta           │  Final IFC elements (guid, class, xyz)
│ element_instances       │  Geometry transforms + materials
│ element_assemblies      │  Parent-child grouping in output
└─────────────────────────┘
```

---

## 9. Process Summary

```
 1. User:     INSERT ad_building_registry (C_Order) with bom_owner='DX'
 2. User:     Optionally edit C_OrderLines (add/remove/swap BOMs)
 3. Compiler: Read C_Order → C_OrderLines → M_BOM trees from BOM.db
 4. Compiler: Create CO_EmptySpace (AABB from building footprint, is_available=Y)
 5. Compiler: Accept top-level M_BOM into CO_EmptySpace
              → Write CO_EmptySpaceLine #1 (decision: accept UNIT_DUPLEX_STD)
              → Set is_available=N
 6. Compiler: Explode M_BOM recursively: UNIT → FLOOR → ROOM → SET → ITEM
              Normal mode:  children translate deterministically from acceptance
                            (no further CO_EmptySpaceLines unless decision point)
              Reprocess:    write CO_EmptySpaceLine at EVERY level (verbose audit)
              Variant mode: write CO_EmptySpaceLine at each selection/conflict
 7. Compiler: At each decision point:
              a. Read SpaceSize from M_BOM_Line (BOM.db)
              b. If multiple candidates: select by SpaceSize fit (fallthrough)
              c. Translate abstract BOM info → construction coordinates
              d. Write CO_EmptySpaceLine (before/next/orient/remaining)
              e. Check: remaining >= 0? (overflow = GIC violation)
 8. Compiler: Write elements_meta + element_instances (IFC output)
 9. Compiler: Complete CO_EmptySpace (DocStatus DR → CO)
10. User:     Query v_co_available_space for remaining capacity
```

---

## 10. First-Level BOMs in BOM.db (Residential Catalog)

These are the top-level M_BOMs — the "cars on the lot" that a C_Order can select:

| bom_id | bom_category | bom_owner | Description |
|--------|--------------|-----------|-------------|
| `UNIT_DUPLEX_STD` | UN | DX | Duplex residential unit (2 floors) |
| `UNIT_SH_STD` | UN | SH | Sample House unit (1 floor) |
| `UNIT_TBLKTN_STD` | UN | TB | TB-LKTN terrace unit (1 floor) |
| `FLOOR_DX_L1_STD` | L1 | DX | Duplex Level 1 |
| `FLOOR_DX_L2_STD` | L2 | DX | Duplex Level 2 |
| `FLOOR_SH_GF_STD` | L1 | SH | SH Ground Floor |
| `FLOOR_TBLKTN_GF_STD` | L1 | TB | TB-LKTN Ground Floor |

A C_Order with `bom_owner='DX'` sees `UNIT_DUPLEX_STD` and its descendants.
A C_Order with `bom_owner='TB'` sees `UNIT_TBLKTN_STD` — and can also
see generic BOMs (bom_owner IS NULL) like `TOILET_BLOCK_FIXTURES`.

---

## Cross-references

- **BIMasBOMConcept.md** — the three-dimension model (Category + Owner + SpaceSize)
- **PREFAB_ARCHITECTURE.md** — 6-level assembly hierarchy + MRP BOM Drop chain
- **TheLocatorBIMConcept.md** — Locator/GPD walk mechanics
- **RELATIONAL_PLACEMENT_SPEC.md** — ad_element_rule (C_OrderLine) placement rules
