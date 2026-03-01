# Construction as ERP

*How BIM compilation maps to iDempiere C_Order → BOM explosion → spatial resolution*

> **Governing principle:** A construction project is a C_Order. The BOM catalog defines
> WHAT can be built. The C_OrderLine selects WHICH BOMs to use. CO_EmptySpace tracks
> WHERE things sit in construction space. Three databases, three concerns, no overlap.

---

## 1. Three Databases — Separation of Concerns

### 1.1 component_library.db — LOD Geometry Store

LOD mesh geometry + materials + intrinsic component orientation. No BOM assembly
logic, no config. Since Phase E, all `ad_*` working tables moved to BOM.db.
Tables staying here use the `lod_` prefix to signal their geometry role.

| Table | iDempiere | Content |
|-------|-----------|---------|
| `component_geometries` | — | Vertex/face geometry BLOBs (deduplicated by hash) |
| `component_definitions` | — | Component metadata + local bounds + up/forward axis + attachment face |
| `component_types` | — | IFC class taxonomy |
| `placement_rules` | — | Host-relative placement: host type, offset, spacing, clearance |
| `lod_geometry_map` | — | Element → geometry hash mapping |
| `lod_element_placement` | — | Compiled LOD element instances (with orientation NS/EW/POINT) |
| `lod_parametric_mesh` | M_Product (parametric) | Generator class + params for procedural geometry |
| `lod_parametric_mesh_param` | AD_Parm | Shape generator parameters |
| `lod_roof_preset` | — | Roof presets |
| `surface_styles` | M_Product_Acct (material) | Material name, RGBA colour per product |
| `material_layers` | — | Layer compositions |

**What it is:** A geometry warehouse with intrinsic orientation. Every mesh has
vertices, faces, a colour, and knows its own up-axis, forward-axis, and
attachment face. `placement_rules` records host-relative constraints (ceiling
vs wall vs floor, offset, clearance). Nothing here knows about assemblies,
buildings, or BOM-level placement — that lives in BOM.db.

### 1.2 BOM.db — Unified Working Database (M_BOM + AD Config)

All working tables: BOM assembly recipes, config rules, product catalog, placement rules.
Since Phase E, this is the primary database (~73 tables). Rich spatial info: SpaceSize (AABB),
orientation rules, locator references.

| Table | iDempiere | Content |
|-------|-----------|---------|
| `m_bom` | M_Product + M_BOM | Assembly definition: BOMCategory (WHAT), C_BPartner (WHO) |
| `m_bom_line` | M_BOM_Line | Child placement: dx/dy/dz, rotation_rule, locator_ref, allocated_*_mm |
| `m_attribute` | M_Attribute | Leaf attributes: ports, clearances, UBBL rules |
| `M_BomCategory` | M_Product_Category | Functional type: LI, BD, KT, FR, ST, L1, L2, UN |
| `M_Product` | M_Product | Intrinsic geometry: width, depth, height (meters) |
| `C_OrderLine` | C_OrderLine (Construction Order Details) | Placement rules per element |
| `C_Order` | C_Order (Construction Order) | Building registrations (5 active) |
| `ad_*` (60+ tables) | AD config | Space types, wall types, opening families, MEP, structural, etc. |

**What it is:** An assembly manual. "A Duplex Unit contains Level 1 + Level 2.
Level 1 contains Living Room + Kitchen + Bathroom. Living Room contains Piano +
Sofa Set + Buffer Space." Every construct carries its AABB so the parent=SUM(children)
invariant holds.

**Buffer space (BOMCategory='ST') is part of the BOM construct.** Buffer children
are explicit M_BOM_Lines in BOM.db — not computed at compile time, not inferred from
gaps. They exist as named M_BOM_Line records with variable SpaceSize. Without them
the parent's AABB cannot equal the sum of its children. The BOM is incomplete
without its buffers, just as a bill of materials is incomplete without its spacers.

**Relationship to component_library.db:** Leaf M_BOM items reference M_Product (NORM-1 renamed
from `ad_product_dim`, now with `component_id` FK to component_definitions) for intrinsic
dimensions. LOD geometry is resolved via
`lod_geometry_map` in component_library.db. The BOM is the recipe; the LOD store has the meshes.

### 1.3 output.db — the Compiled Result (C_Order output)

The work order's compiled output. IFC-compatible elements with world coordinates.

| Table | Content |
|-------|---------|
| `elements_meta` | Compiled elements (guid, ifc_class, storey, world xyz) |
| `element_instances` | Geometry instances (transform matrix, material) |
| `element_assemblies` | Assembly grouping (parent-child in output) |
| `co_empty_space` | Construction space header (per C_Order); `is_available` = quality gate |
| `co_empty_space_line` | Spatial resolution per BOMLine (before/next, orientation); `c_orderline_id` → BOM.db `c_orderline` (logical FK, NORM-0b) |

**Table prefix rule — never use `ad_` for construction models:**

| Prefix | Domain | Database | Examples |
|--------|--------|----------|----------|
| `ad_*` | Application Dictionary — system config, product catalog, placement rules | BOM.db | M_Product (NORM-1 renamed from ad_product_dim), c_orderline (C_OrderLine), c_order (C_Order — Construction Order) |
| `m_*` | Master data — BOM assembly recipes, attributes, categories | BOM.db | m_bom, m_bom_line, m_attribute, M_BomCategory |
| `lod_*` | LOD geometry — extracted meshes, element placement, parametric meshes | component_library.db | lod_geometry_map, lod_element_placement, lod_parametric_mesh |
| `co_*` | Construction output — compiled spatial resolution | output.db | co_empty_space, co_empty_space_line |

The `ad_` prefix is iDempiere's system dictionary namespace. Using it for working
construction data (BOM trees, spatial output) conflates configuration with runtime
state. Historical mistake (`ad_bom`, `ad_bom_child`, `ad_bom_child_param`) corrected
in the BOM Dimension migration to `m_bom`, `m_bom_line`, `m_attribute`.

---

## 2. C_Order — the Construction Order

The building project IS a C_Order. Not BIM, not DSL — **C_Order** directly.

```
C_Order (= Construction Order)
│   C_Order_ID     = building_id ('Ifc2x3_Duplex')
│   C_BPartner     = 'DX'                        ← WHO  (Construction Building Pattern)
│   Site_AABB      = aabb_width/depth/height_mm ← HOW BIG (construction envelope)
│   Description    = 'Duplex residential unit'
│   DocStatus      = 'DR' → 'CO'
│
│   These two fields — C_BPartner + AABB — ARE the building definition.
│   Everything else on C_Order is administrative (paths, lifecycle, audit).
│   The entire BOM explosion tree derives from WHO + HOW BIG.
│
├── Tab: C_OrderLine (= Construction Order Details)
│   │   Selects M_BOMs from BOM.db and places them
│   │
│   └── Sub-tab: BOM (read-only copy from BOM.db)
│       │   The selected M_BOM tree, copied VERBATIM from BOM.db
│       │   ALL children intact: fixed items, sub-BOMs, AND buffer (ST) children
│       │   SpaceSize, dx/dy/dz, rotation_rule — everything transfers
│       │   Acts as immutable reference during compilation
│       │
│       └── Sub-tab: BOMLine
│           Roof, Slab, L1, L2, rooms, furniture, buffers — expanded children
│           Each child is itself an M_BOM with its own BOMLines
│           Buffer children included — the BOM construct is complete as-is
│
└── Tab: CO_EmptySpace
    │   Construction site information
    │   FK: C_Order_ID → C_Order
    │   real_world_location, origin_spot
    │   AABB of whole intended construction space
    │   IsAvailable = Y (start + during processing + on reprocess)
    │               → N (only after translation to output DB + tests GREEN)
    │
    └── Sub-tab: CO_EmptySpaceLine
        Alignment record: WHERE the BOM box sits + orientation
        Does NOT repeat the BOM (that's intact on C_OrderLine.BOM.BOMLine)
        Says: "this BOM construct goes HERE, facing THIS way"
        Translation to output DB uses this alignment + BOM offsets → world coords
        Can hold MEP spatial refs separately from BOM leaf items
```

### 2.1 Why C_Order?

| Concern | iDempiere | BIM (Construction Order) |
|---------|-----------|--------------------------|
| "I want to build a Duplex" | Raise C_Order | INSERT C_Order (Construction Order) |
| "Use DX vendor's catalog" | Set C_BPartner | SET C_BPartner = 'DX' |
| "How big is the site?" | Set dimensions | SET aabb_width/depth/height_mm |
| "Include this BOM" | Add C_OrderLine | INSERT C_OrderLine (Construction Details) |
| "What fits where?" | Check availability | Query CO_EmptySpace/Line |
| "Build it" | Process Order | `./scripts/run_tests.sh` (compile) |
| "Edit the spec" | Modify C_OrderLine | UPDATE C_OrderLine (Construction Details) |

**The simplest possible building definition is two fields on C_Order:**
`C_BPartner` (WHO) + `AABB` (HOW BIG). Every downstream decision cascades from
these. A C_Order with only these two fields populated is sufficient to compile —
the BOM explosion engine selects the right UNIT, the right floors, the right
rooms, the right furniture, all from `BOMCategory + SpaceSize ≤ AABB`.

### 2.2 C_OrderLine — what gets built

Each C_OrderLine (Construction Order Details) selects an M_BOM from BOM.db:

```
C_OrderLine #1:  family_ref = 'UNIT_DUPLEX_STD'    host_type = BUILDING
C_OrderLine #2:  family_ref = 'FLOOR_DX_L1_STD'    host_type = BUILDING
C_OrderLine #3:  family_ref = 'LIVING_SET'          host_type = ROOM, room_ref = 'Rm_Living_1'
C_OrderLine #4:  family_ref = 'BED_SET_MASTER'      host_type = ROOM, room_ref = 'Rm_Bedroom_1'
```

The **BOM sub-tab** on each C_OrderLine shows the M_BOM tree copied verbatim from
BOM.db. This is the product spec — immutable reference. **All information transfers
intact:** fixed children with their SpaceSize, sub-BOMs with their recursive trees,
AND buffer children (BOMCategory='ST') with their variable SpaceSize. The BOM
construct in BOM.db is complete — it includes every spacer, every gap, every
arrangement relationship. The copy to C_OrderLine.BOM preserves this completeness.
The compiler reads this reference, not BOM.db directly, so the scope is locked to
what was ordered.

The user edits C_OrderLines: swap a sofa set, remove the piano, add a dining chair.
The compiler reads the final C_OrderLines and resolves.

---

## 3. CO_EmptySpace — Construction Space Tracking

### 3.1 CO_EmptySpace (header)

One record per C_Order. The **post-compile** construction site envelope.

**Distinction:** The C_Order's AABB (`aabb_*_mm` on C_Order) is the
**pre-compile input** — "I want to build in this envelope." CO_EmptySpace's AABB
is the **post-compile output** — "the compiler measured this envelope from the
compiled R*Tree." For owner-matched builds (SH/DX), these are numerically equal.
For ST-mode builds, the output AABB may be smaller (not all space consumed).

**Design decision (NORM-3b, 2026-02-28):** CO_EmptySpace is deliberately kept as
a separate table in output.db. Collapse into C_Order was assessed and rejected:
- `origin_x/y/z_mm` (RTREE-measured actual origin) is compile-time data; it does
  not belong in C_Order (design-time). IFC models are not always at world origin.
- The quality-gate state machine (`is_available`, `doc_status`) is written by
  `ProveStage` using the output.db connection. Moving it to BOM.db would require
  a cross-DB write at prove time — more complex, not simpler.
- `co_empty_space_line.co_emptyspace_id` is a clean local SQLite FK within
  output.db. Collapse turns it into a logical cross-DB reference — weaker.
- `EmptySpaceChecksum` reads only `co_empty_space_line`, not the header; witness
  complexity does not decrease if the header is removed.

```sql
CREATE TABLE co_empty_space (
    co_emptyspace_id    INTEGER PRIMARY KEY AUTOINCREMENT,
    c_order_id          TEXT NOT NULL,       -- FK → Construction Order
    origin_x_mm         REAL NOT NULL DEFAULT 0,
    origin_y_mm         REAL NOT NULL DEFAULT 0,
    origin_z_mm         REAL NOT NULL DEFAULT 0,
    aabb_width_mm       REAL NOT NULL,       -- total construction space X
    aabb_depth_mm       REAL NOT NULL,       -- total construction space Y
    aabb_height_mm      REAL NOT NULL,       -- total construction space Z
    is_available        INTEGER NOT NULL DEFAULT 1,  -- Y=available/unproven, N=consumed+tests GREEN
    doc_status          TEXT NOT NULL DEFAULT 'DR',
    created             TEXT NOT NULL DEFAULT (datetime('now')),
    updated             TEXT NOT NULL DEFAULT (datetime('now'))
);
```

**IsAvailable lifecycle:**
1. **Project start:** `is_available = 1` (Y), AABB = full intended construction space.
2. **During processing:** stays `is_available = 1`. The compiler translates the BOM
   construct into the output DB (Blender viewport / IFC export).
3. **After clean output + tests GREEN:** `is_available = 0` (N). The space is confirmed
   consumed — the translation passed all witness gates.
4. **Reprocess:** `is_available` reset to `1` (Y). Processing begins again from scratch.
5. **If `is_available` remains `1` after processing completes:** the build **did not pass**.
   The space was not successfully consumed. Tests failed or translation aborted.

**The flag is a quality gate, not a progress marker.** It only goes to N when the
output is proven correct. A CO_EmptySpace stuck at `is_available = 1` after
processing means the construction did not compile clean.

### 3.2 CO_EmptySpaceLine (detail)

**An alignment record — where the whole box model sits.** CO_EmptySpaceLine tells
the compiler WHERE the BOM construct (the entire box, including its buffers) is
aligned in construction space and at what orientation. It does not repeat the BOM —
that is already intact on C_OrderLine.BOM.BOMLine. It says: "this BOM box goes HERE,
facing THIS way."

A CO_EmptySpaceLine record is created at each **structural tier** — unit, slab,
floor, roof, pair container. For 1:1 extracted buildings (SH, DX), this produces
a sparse ledger: SH has 4 lines, DX has 7 lines (see Appendix E). For
variant-driven buildings (TB-LKTN), additional lines record selection decisions.

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

    -- Extensible (not yet in schema): MEP spatial refs, 7D IoT refs, etc.
    -- mep_ref          TEXT,                 -- MEP connection point (future)

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

**For SH/DX, lines are sparse.** The pipeline writes one line per structural tier:
SH produces 4 lines (UNIT, GROUND_SLAB, GROUND_FLOOR, ROOF); DX produces 7 lines
(UNIT, GROUND_SLAB, LEVEL_1, UPPER_SLAB, LEVEL_2, ROOF, PAIR). There is only ONE
variant at every BOM level — no variant to compare, no space conflict. The BOM tree
unfolds deterministically. Everything translates 1:1 because it was extracted from
the exact geometry. See Appendix E for the full ledger dumps.

**For TB-LKTN, lines are dense.** TopologyMaker creates room variants. The furniture
sets need SpaceSize matching. Each selection decision spawns a CO_EmptySpaceLine,
recording which variant won, what orientation was resolved, what space remains.

**Furniture-set level — one unified consultation process:**

All buildings go through the same BOMCategory lookup. There is no exclusive
treatment. The difference in outcome is purely a consequence of match cardinality:

| Outcome | Condition | ESLines written | C_OrderLines written |
|---------|-----------|-----------------|----------------------|
| **Single-match** | C_BPartner matches + exactly one BOM fits → whole assembly taken in toto | ONE ESLine for the whole set | None — existing C_OrderLine sufficient |
| **Walk** | C_BPartner does not match → BOMCategory slot walk by Sequence; if AABB fits exactly, arrives at same result | One ESLine per slot placed | One new C_OrderLine per found BOM (written to **output.db** — transactional instance data) |

**Single-match (SH/DX):** `C_Order.C_BPartner='SH'` — BOM.C_BPartner='SH' → exactly
one match. The compiler takes the whole LIVING_SET assembly in one unit: ONE ESLine
records origin + orientation; the BOM's `dx/dy/dz` values reproduce exact item
positions from the reference IFC. No further walking, no new C_OrderLines. Spatial
digest is stable: same BOM offsets → same world coordinates every compile.

**Walk (ST mode):** `C_Order.C_BPartner='ST'` → no C_BPartner match. The compiler
walks M_BomCategoryLine slots in Sequence order (Dining=10, Sofa=20, Piano=30),
placing each against the remaining room AABB. When the C_Order AABB matches SH's
exactly, the walk finds the same BOMs that SH's direct match finds — and the result
is identical. This is not a coincidence: it is the architecture working correctly.
The same room dimensions can only accept the same furniture set. The process
naturally converges to the same placement. A new C_OrderLine + ESLine is written
for each slot; for a slot that does not fit, it is skipped.

**DX AABB is its own discriminant.** DX's living room (3332×3943mm) is uniquely
sized — only DX BOMs fit that space. The walk finds them because nothing else
matches. The two-unit (dual-tenant) structure is handled at the higher structural
tier (PR path in M_BomCategoryLine `num_units=2`) and produces the DUPLEX_SET_STD
pair container as one of the 7 structural ESLines. For Rosetta Stone testing, AABB
alone is sufficient to ensure DX BOMs and no others are selected.

**Rosetta Stone proof — the only efficient "visual" verification:**
The spatial digest hash comparison is the only systematic way to prove that the
correct furniture sits in the correct positions. Without it there is no efficient
visual proof — only manual inspection of coordinates. Both the direct-match path
and the walk path must produce the **same spatial digest hash total GREEN**:

```
SpatialDigest(SH)     == SpatialDigest(ST_SH)   ← same AABB, walk finds same BOMs
SpatialDigest(DX)     == SpatialDigest(ST_DX)   ← same AABB, walk finds same BOMs
```

Test sequence (Rosetta Stone only — SH and DX are the reference stones):
1. SH and DX direct C_BPartner match → spatial digest GREEN
2. ST_SH (C_BPartner='ST', SH AABB) and ST_DX (C_BPartner='ST', DX AABB) → walk
   → same digest GREEN
When both pass, the placement process is proven robust and correct.

**Rosetta Stone digest filter (Q&A1, 2026-03-02):** SH EXTRACTED has 56 elements;
ST_SH GENERATIVE currently produces 123. The 67 extra elements are compilation
artifacts (structural stubs, PHANTOM buffers, props). The SpatialDigest comparison
must filter to **visible, geometry-bearing elements only** — same IFC classes on
both sides. Investigation required to identify exactly which element classes to
include/exclude. The filter is a prerequisite for the Rosetta Stone gate.

**TB-LKTN is NOT a Rosetta Stone.** TB-LKTN has no reference IFC to compare a
spatial digest against. Its reference is a 2D layout (building grid lines set by
the user). Furniture placement for TB-LKTN is "last-mile" — the BOMCategory walk
places items into real rooms not pre-matched to any template. Test criteria when
implemented:
- No furniture strewn or sunken (items sit within room boundaries, valid Z)
- At minimum a dining set fits in the living room (smallest viable placement)
- A small bedroom gets a bed — same walk, result driven by what fits in that AABB

**Invention stops (critical for TB-LKTN):** The main failure mode for TB-LKTN
is *invention* — the compiler generating walls, openings, or furniture for which
no LOD mesh exists in component_library. The code must **STOP with an explicit
error** when a component_library lookup returns nothing. It must not create a
placeholder geometry. The user must:
1. Create the mesh/LOD in component_library
2. Link it to M_Product
3. Re-run the test

An earlier TB-LKTN attempt with 2D layout (building grid lines) produced correct
room placements but caused invention for objects not yet in component_library.
This led to the **Rosetta Stone Strategy** — prove the placement process on SH/DX
where the expected output is fully known, then apply to TB-LKTN with confidence.
**Once SH/DX Rosetta tests pass, TB-LKTN has no reason to fail.** The same
unified process applies — only the reference validation method differs.

### 3.4 What CO_EmptySpaceLine holds

**The BOMLine tab is the WHAT (complete BOM construct copied from BOM.db — items,
sub-BOMs, buffers, SpaceSize — all intact and unchanged).**
**CO_EmptySpaceLine is the WHERE (alignment: box origin + orientation in construction space).**

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
- **c_orderline_id** (NORM-0b) — logical FK → BOM.db `c_orderline(id)`.
  The iDempiere fulfillment link: C_OrderLine = what was requested;
  CO_EmptySpaceLine = where it was delivered. Cross-DB (output.db → BOM.db),
  not enforced by SQLite. NULL for all current rows — `c_orderline` currently
  holds element-level fulfillment refs (IfcDoor, IfcWall, etc.); BOM-assembly-level
  C_OrderLine entries do not yet exist.
- **mep_ref** (future column, not yet in schema) — MEP connection point.
  Separate from the BOM leaf item. The BOM leaf stays pure product data;
  the MEP spatial reference is a construction concern tracked on the
  EmptySpaceLine.

### 3.5 Translation to Output DB — When Coordinate Work Happens

The BOM tab on C_OrderLine holds the WHAT — all children, buffers, SpaceSize, intact
from BOM.db. CO_EmptySpaceLine holds the WHERE — alignment and orientation in
construction space. **The actual coordinate translation happens when the compiler
writes to the output DB** (elements_meta, element_instances) for Blender viewport
or IFC export:

**EN-BLOC mode (SH/DX — exact AABB match → ONE ESLine for whole furniture set):**

```
BOM.db construct (abstract)
  M_BOM: LIVING_SET
    M_BOM_Line: Piano     dx=0    dy=0   rotation_rule=PARALLEL_TO_WALL
    M_BOM_Line: Sofa      dx=1500 dy=0   rotation_rule=FACE_INTO_ROOM
    M_BOM_Line: Buffer_NW  (variable, fills remainder)

CO_EmptySpaceLine (alignment) — ONE line for the whole set
  Line #7: LIVING_SET box → origin=(208, -5246, 0)  orient=π  (north wall room)

Translation to output DB (concrete world coordinates)
  Piano:      world_xyz = origin + rotated(dx=0, dy=0)     = (208, -5246, 0)     orient=π
  Sofa:       world_xyz = origin + rotated(dx=1500, dy=0)  = (-1292, -5246, 0)   orient=π
  Buffer_NW:  (no geometry — spatial placeholder, but space accounted for)
```

**EXPLODE mode (TB-LKTN / new buildings — no exact AABB match → one ESLine per slot):**

```
M_BomCategoryLine slots (from M_BomCategory for this room AABB):
  Seq=10  Child=DN (Dining)   slot_aabb=2000×1000mm
  Seq=20  Child=FR (Sofa)     slot_aabb=2500×900mm
  Seq=30  Child=FR (Piano)    slot_aabb=1371×600mm

For each slot that fits (priority order):
  New C_OrderLine #N:  family_ref = 'DINING_SET'   host_type=ROOM  room_ref=Rm_Living
  CO_EmptySpaceLine #N:  DINING_SET → before=(x0,y0) next=(x0+2000, y0)  orient=0
  New C_OrderLine #M:  family_ref = 'SOFA_SET'     host_type=ROOM  room_ref=Rm_Living
  CO_EmptySpaceLine #M:  SOFA_SET   → before=(x1,y1) next=(x1+2500, y1)  orient=0
  [Piano slot: fits? if yes → same pattern; if not → slot skipped]
```

The CO_EmptySpaceLine gives the room-level anchor. The BOM.db dx/dy/dz offsets —
including buffer gaps — are then rotated and translated relative to that anchor.
This is the step where abstract BOM offsets become world coordinates.

**After this translation, tests run.** If all witness gates pass (G8 centroids, F4
edges, W-SPACESIZE-1, etc.), the CO_EmptySpace.is_available is set to N — confirmed
consumed. If tests fail, is_available stays Y — the space was not successfully filled.

### 3.6 Reprocess Mode

A compile flag `--reprocess-all` forces the full layer-by-layer walk even for
SH/DX. In this mode, a CO_EmptySpaceLine is written for **every BOM level and
every child** — tedious but systematic:

```
Reprocess mode — DX (verbose, one line per BOM level):
  Line #1:  UNIT_DUPLEX_STD       level=0  accepted into full AABB
  Line #2:  FLOOR_SLAB_GF         level=1  before=(0,0,0) next=(0,0,0)          ← ground slab
  Line #3:  FLOOR_DX_L1_STD       level=1  before=(0,0,0) next=(0,0,3000)       ← L1 contents
  Line #4:  FLOOR_SLAB_L2         level=1  before=(0,0,3000) next=(0,0,3000)    ← upper slab
  Line #5:  FLOOR_DX_L2_STD       level=1  before=(0,0,3000) next=(0,0,6000)    ← L2 contents
  Line #6:  ROOF_ASSEMBLY         level=1  before=(0,0,6000)                     ← roof
  Line #7:  Rm_Living_1/LIVING    level=2  before=(208,-5246,0) orient=0
  Line #8:  Rm_Dining_1/DINING    level=2  before=(208,-8554,0) orient=0
  ...
  Line #15: Piano                 level=3  before=(1620,3308,0) next=(3120,3308,0) orient=π
  Line #16: Buffer_NW             level=3  remaining=254mm
  Line #17: Sofa_3Seat            level=3  before=(3374,3308,0) next=(5374,3308,0) orient=π
```

**Why this matters:**
- For SH/DX it is pure verification — if any CO_EmptySpaceLine shows a translation
  error (wrong orientation, misaligned before/next), the bug is pinpointed to that
  exact BOM level.
- For TB-LKTN it is the actual working mode — every level has real decisions.
- The same code path handles both. The difference is only in how many decisions
  are non-trivial (zero for SH/DX, many for TB-LKTN).

### 3.7 The 1D Intent — Two Fields Drive Everything

The DSL (formerly a complex building description language) collapses to exactly
**two fields** on the C_Order (Construction Order):

| Field | Column | Meaning |
|-------|--------|---------|
| **WHO** | `C_BPartner` | Construction Building Pattern — which BOM trees are visible (SH/DX/TB/TE/ST) |
| **HOW BIG** | AABB (width × depth × height mm) | Construction site envelope dimensions |

These two fields are the root of the entire BOM explosion tree. Every downstream
decision — which UNIT BOM, which floor template, which room set, which furniture
leaf, which buffer gap — **derives from `C_BPartner` + `AABB`**. Nothing else on
the C_Order influences compilation output.

**Current mode (owner-matched):** SH/DX/TB/TE each have an exact `C_BPartner`
value that maps to exactly one UNIT BOM. The compilation is deterministic — no
spatial selection is needed. Think of it as a completed Lego set placed on the
board exactly where it is marked.

**Standard mode (`C_BPartner='ST'`):** When `C_BPartner='ST'`, the compiler has no
pre-matched BOM set. It must:

1. Use the C_Order AABB as the construction envelope
2. At each BOM level, select the best-fitting BOM by `BOMCategory` + `SpaceSize ≤ available AABB`
3. Write a `co_empty_space_line` at EVERY level (= Reprocess Mode as primary mode)
4. Each line's `before/next` coordinates ARE the spatial audit trail

This is the layer-by-layer BOM selection engine: the compiler walks the BOM tree
top-down, and at each node asks "what fits in the remaining space?" instead of
"what does this owner's catalog say?"

**POC strategy:** Create `ST_SH` and `ST_DX` registry entries that compile the
same buildings through the full layer-by-layer selection process. Success
criterion: `SpatialDigest(ST_SH) == SpatialDigest(SH)`. Stay within the
RosettaStone confine — systematic POC on known-good buildings before unlocking
TB-LKTN, where the answer is not yet known.

**Why not TB-LKTN yet:** TB-LKTN has no complete topology set pre-extracted. The
layer-by-layer engine must first be proven on RosettaStone buildings (SH, DX)
where the expected output is already known and can be compared via SpatialDigest.

**ST vs ST disambiguation:**

| Abbreviation | Context | Meaning |
|---|---|---|
| `C_BPartner='ST'` | C_Order (Construction Order) | **Standard mode** — generic, owner-agnostic construction |
| `BOMCategory='ST'` | `M_BomCategory` (BOM.db) | **Buffer/spacer** — empty space child within a BOM assembly |

Different concepts, same abbreviation. `C_BPartner='ST'` is a compilation mode.
`BOMCategory='ST'` is a spatial placeholder. They coexist: an ST-mode
compilation will encounter ST-category buffer children during BOM explosion.

#### 3.7.1 Implementation Gaps (TODO)

Seven concrete gaps between the current compiler and full ST mode:

**~~TODO-ST-1: Add AABB to C_Order (Construction Order)~~ IMPLEMENTED** (Phase ST-0)

AABB columns (`aabb_width_mm`, `aabb_depth_mm`, `aabb_height_mm`) added to
`c_order`. Backfilled from compiled output for SH/DX/TB/TE. X_C_Order PO
classes updated in ORMSandbox + TopologyMaker.

**Code/model impact** (see full list at end of §3.7.1):

**TODO-ST-2: ST `C_BPartner` selection logic** — PARTIALLY IMPLEMENTED

- **Decision resolved:** ST mode sees ALL BOMs (all owners). The composition
  proof (Phase ST-1b) demonstrates this: `MBOM.findBestFitAnyOwner()` queries
  all active BOMs in a category with no c_bpartner filter. AABB constraint +
  template branching drive selection — owner-specific BOMs self-select when
  they are the only candidate in their category.
- **Remaining:** Wire `findBestFitAnyOwner` into `CompilationPipeline.java`
  for actual ST-mode compilation (currently only used in composition proof).
- **File:** `CompilationPipeline.java`, `MBOM.java`

**TODO-ST-3: `co_empty_space_line` L2–L3 population**

- **Gap:** Current code writes L0 (UNIT acceptance) + L1 (per-storey) only.
  No L2 (rooms) or L3 (items).
- **Fix:** Recursive walk from L1 floor children → L2 room lines → L3 item lines.
- **Each line records:** before/next anchor, orientation_rad, capacity_mm,
  filled_mm, remaining_mm, storey, room_name.
- **Buffer (ST) children:** Create lines with `remaining_mm > 0`, no geometry
  output.
- **File:** `CompilationPipeline.java` — extend the L1 loop to recurse into
  children.

**TODO-ST-4: Document sequencing on `co_empty_space_line`**

- **Gap:** Table has spatial before/next but no document sequence fields
  (prefix/suffix/nextID).
- **Evaluate:** The before/next xyz columns already provide spatial chaining.
  Document sequencing may map to `bom_line_seq` + `bom_level` ordering rather
  than explicit `next_line_id` FK.
- **File:** `BuildingWriter.java` (DDL), `M_CO_EmptySpaceLine.java` (PO)

**~~TODO-ST-5: SpaceSize-based BOM variant selection~~ IMPLEMENTED**

- **Implemented:** `MBOM.findNextFitSpace()` (Phase F3) — queries m_bom by
  category + c_bpartner scope, filters by AABB fit, returns largest volume match.
- **Pre-compilation gate:** `BomTemplateContract.check()` (Phase ST-1a) validates
  BOM catalog completeness against M_BomCategoryLine template with MinQty/MaxQty.
- **File:** `ORMSandbox/.../MBOM.java`, `ORMSandbox/.../BomTemplateContract.java`

**TODO-ST-6: Structured translation logging → `co_empty_space_line`**

- **Gap:** `[TRANSLATE]` printf at `BOMTierResolver.java` goes to stdout only.
  Not queryable.
- **Fix:** Each BOM child expansion writes a `co_empty_space_line` L3 record
  with the exact anchor→world translation.
- **Post-compile query:**
  `SELECT * FROM co_empty_space_line WHERE room_name = ? ORDER BY bom_level, bom_line_seq`
- **File:** `BOMTierResolver.java` — pass Connection to `expandBOMNode`, write
  L3 lines.

**TODO-ST-7: Product orientation invariants**

- **Gap:** M_Product has width/depth/height but no up-vector,
  front-vector, alignment-to-host.
- **Assessment:** NOT a schema gap. Rotation resolves from `rotation_rule`
  (`m_bom_line`) + semantic rules (`m_attribute`). For ST mode, these must be
  present on ALL BOM children — no owner-specific defaults allowed.
- **Fix:** Documentation only — document the invariant that every `m_bom_line`
  must have a resolvable `rotation_rule` for owner-agnostic mode.

#### 3.7.2 AABB on C_Order — Code/Model Impact Inventory (DONE, Phase ST-0)

AABB columns added to C_Order in Phase ST-0. Impact list (all completed):

**Schema (1 migration script):**

| Change | File |
|--------|------|
| `ALTER TABLE C_Order ADD COLUMN aabb_width_mm REAL` (×3) | `migration/migration_st_aabb_registry.sql` |
| Backfill from compiled output: `UPDATE ... SET aabb_width_mm = (SELECT aabb_width_mm FROM co_empty_space WHERE c_order_id = building_id)` | Same migration |

**PO classes (2 modules, 2 files each = 4 files):**

| File | Changes |
|------|---------|
| `ORMSandbox/.../po/X_C_Order.java` | +3 COLUMNNAME constants, +3 getters, +3 setters |
| `ORMSandbox/.../po/MOrder.java` | Inherit new accessors (no logic change) |
| `TopologyMaker/.../po/X_C_Order.java` | Same 3+3+3 |
| `TopologyMaker/.../po/MOrder.java` | Inherit |

**Registry reader (1 file):**

| File | Change |
|------|--------|
| `DAGCompiler/.../dsl/BuildingRegistry.java:18-31` | Add 3 fields to `BuildingEntry` record |
| `DAGCompiler/.../dsl/BuildingRegistry.java:98-102` | Add 3 columns to SELECT query |

**Compilation pipeline (1 file, 2 sites):**

| File:Site | Change |
|-----------|--------|
| `CompilationPipeline.java` — UNIT BOM selection | For ST mode: use registry AABB as envelope constraint instead of R*Tree post-hoc |
| `CompilationPipeline.java` — `populateCoEmptySpace()` | For owner-matched: continue computing from R*Tree. For ST: use registry AABB as authoritative input |

**BuildingInspector preflight (1 file):**

| File | Change |
|------|--------|
| `ORMSandbox/.../BuildingInspector.java` | New check: AABB present and non-zero for ST-mode buildings. Optional warning for owner-matched buildings with NULL AABB. |

**Tests (witness additions):**

| Test | Assertion |
|------|-----------|
| `BuildingRegistryTest` | AABB columns populated for SH/DX after backfill migration |
| `CompilerContractTest` | ST-mode POC: `SpatialDigest(ST_SH) == SpatialDigest(SH)` (future) |

**NO impact on:**
- BOMTierResolver (reads BOM, not registry)
- FurnitureWorker (dispatches to BOMTierResolver, not registry)
- FloorPlateBOMResolver (reads BOM tree, not registry)
- StoreyCompiler (receives BuildingEntry, but doesn't use AABB yet — future ST mode)
- MEPWriter, BuildingWriter, StructuralWriter (downstream of placement)
- TopologyBatchProcess (writes registry, would need to SET AABB on new entries)

**Summary: 1 migration + 4 PO files + 2 Java files + 1 inspector check + 2 tests.**
The AABB columns are NULL-safe — owner-matched builds (SH/DX/TB/TE) continue to
work unchanged with NULL AABB. ST-mode compilation requires non-NULL AABB.

### 3.8 Template-Driven Decomposition (ST Mode)

**Phase ST-0** adds the schema foundation for Standard Mode — a template-driven
compilation path where no pre-built BOM tree exists for the building.

#### C_BPartner Lookup Table

New lookup table tracks building pattern owners:

| C_BPartner_ID | Value | Name |
|--------------|-------|------|
| SH | SampleHouse | Sample House |
| DX | Duplex | Duplex |
| TB | TerraceBlock | Terrace Block |
| MY | Malaysian | Malaysian Residential |
| TE | Terminal | Terminal |
| ST | Standard | Standard Mode |

The column `bom_owner` was renamed to `c_bpartner` in both `m_bom` and `c_order`
to align with iDempiere naming. All Java PO classes, DAGCompiler SQL, and test
witnesses updated accordingly.

#### M_BomCategoryLine — Recursive Decomposition Recipe

New master-detail table on `M_BomCategory`. Each line maps a parent category to
a child category, forming a recursive template tree. Three aspect columns
enable parametric branching:

| Column | Semantics |
|--------|-----------|
| `num_units` | 0=universal (always active), 1=single-household (GF path), 2=dual-household (PR path) |
| `storey_count` | Informational: how many storeys this subtree spans |
| `mirroring_rule` | 'NONE' or 'PARTY_WALL_PI' (mirrored pair injection) |

```
RE (Residential Template, C_BPartner='ST')
├── SL (Floor Slab)          seq=10  num_units=0  ← universal
├── PR (Duplex Pair)         seq=15  num_units=2  ← DX path (2-storey body)
│   ├── HU (Unit A)          seq=10  mirror=NONE
│   │   ├── L1 (Ground)      Z=0.0–0.5
│   │   │   ├── LI (min=1), DN, KT, BT
│   │   └── L2 (Upper)       Z=0.5–1.0
│   │       ├── BD (min=1, max=3), KT, BT
│   └── HU (Unit B)          seq=20  mirror=PARTY_WALL_PI
│       └── [same L1/L2 structure]
├── GF (Ground Floor Body)   seq=20  num_units=1  ← SH/MY/TB path
│   ├── LI (min=1), BD (min=1), DN, KT, BT
└── RF (Roof Assembly)       seq=30  num_units=0  ← universal
```

`Z_Offset_Ratio` and `Z_Extent_Ratio` encode vertical proportions as fractions
of the parent AABB height (e.g. 0.836 = 83.6% of total height for the body).
`MinQty`/`MaxQty` constrain required vs optional categories per template level.

#### Template-Driven ESL Flow (Phase ST-1, not yet implemented)

```
C_Order.c_bpartner='ST' → No owner-matched M_BOM
→ Look up M_BomCategory WHERE C_BPartner_ID='ST' → finds RE
→ Load M_BomCategoryLine children: SL(10), GF(20), RF(30)
→ Create 3 CO_EmptySpaceLines from template (Z from ratios × AABB height)
→ For each ESL, find best-fit M_BOM via MBOM.findNextFitSpace()
→ Recurse: GF has sub-lines → create room-level ESLs → find SET BOMs
→ Leaf BOMs: walk BOM children as furniture items (template stops, BOM takes over)
```

#### POC Strategy

`ST_SH` is a dormant C_Order (`is_active=0`) with SH's exact AABB (16867.5 ×
8667.5 × 3945.2 mm). When Phase ST-1 adds the template walker, it must select
SH's BOMs and produce `SpatialDigest(ST_SH) == SpatialDigest(SH)`.

#### DAO Classes

| Class | Table | Purpose |
|-------|-------|---------|
| `X_CBPartner` / `MCBPartner` | C_BPartner | Building pattern owner lookup |
| `X_MBomCategoryLine` / `MBomCategoryLine` | M_BomCategoryLine | Template decomposition recipe |
| `X_M_BomCategory` (updated) | M_BomCategory | +Value, +C_BPartner_ID columns |

---

### 3.9 M_BomCategory — AABB Template Registry

`M_BomCategory` is a **universal construct dictionary** — a semantic type
descriptor for ANY building construct, not just rooms. It has **no `C_BPartner`**
and no building identity. It is indexed by functional type + AABB dimensions.

**Scope (Q&A1, 2026-03-02):** BomCategory covers everything — rooms (LI, BD,
KT, BT, DN), structural tiers (SL, L1, L2, UN, GF, RF, PR, HU, MP), and
eventually walls, MEP runs, roof assemblies, openings. Every construct type in
`component_library.db` gets a BomCategory "passport" — its semantic identity.
This is the Semantic IFC/BIM vision: if a shape has no BomCategory definition,
it does not exist in the compiler's vocabulary. Like XML to HTML — adding
structure and meaning to raw geometry.

**Why no C_BPartner on M_BomCategory?** Because the template is not owned by a
building. It is a geometric category: "a living room of these dimensions." The
user sets a room's category (`LI`, `BD`, `KT`) in the Bonsai Editor when
configuring C_OrderLines. The compiler then looks up M_BomCategory by functional
type + AABB to find the matching template. Which SH or DX that room belongs to
is irrelevant at this lookup step — only the dimensions matter.

**Current templates (Living Room):**

| M_BomCategory_ID | Name | AABB (W×D mm) | Source |
|-----------------|------|----------------|--------|
| `LI_SH` | Living Room SH | 8869 × 4690 | SH reference IFC, ROOM_Ground_Floor_1 |
| `LI_DX` | Living Room DX | 3332 × 3943 | DX reference IFC, ROOM_A102/B102 |

Two templates because the AABB differs — different rooms, different slot sets.
Named descriptively (room type + distinguishing suffix), not by building.

#### M_BomCategoryLine — Slot Descriptors

`M_BomCategoryLine` is the **slot list** for a given M_BomCategory. Each line is
a placeholder — it says "this room type at this AABB has a slot for this
furniture-set type, of this size, at this priority." No BOM identity, no
building identity. Purely: what slot, what AABB, what sequence.

| Column | Semantics |
|--------|-----------|
| `M_BomCategory_ID` | Parent template (e.g. LI_SH) |
| `Child_BomCategory_ID` | Functional type of the expected child (DN, FR, etc.) |
| `Sequence` | Priority — lower = placed first (Dining=10, Sofa=20, Piano=30) |
| `aabb_width_mm` | Slot space width for this furniture set |
| `aabb_depth_mm` | Slot space depth |
| `aabb_height_mm` | Slot space height |

**What M_BomCategoryLine does NOT hold:**
- Buffer/filler space — buffers live as `BOMCategory='ST'` PHANTOM entries in
  `m_bom_line` within the BOM tree itself. They are part of the WHAT, not the
  slot template. M_BomCategoryLine is only slot holders.
- BOM identity — no `family_ref`, no FK to `m_bom`. The compiler looks up the
  actual BOM via `MBOM.findNextFitSpace()` at runtime.
- Building scoping — no SH/DX references. Same slot list applies to any room
  that matches the parent M_BomCategory AABB.

**Example — LI_SH slot list (Sequence order):**

```
M_BomCategory: LI_SH  (8869×4690mm)
  Seq=10  Child=DN  slot=2000×1000mm  ← dining set fits here first
  Seq=20  Child=FR  slot=2500×900mm   ← sofa set second
  Seq=30  Child=FR  slot=1371×600mm   ← piano third (dropped if room too small)
```

**How the compiler uses this (unified process):**

1. Room C_OrderLine carries `BomCategory_ID='LI'` (set by user in Bonsai)
2. Compiler queries BOMCategory for BOM.C_BPartner = C_Order.C_BPartner
   - If exactly one BOM found → take it in toto, ONE ESLine, done (SH/DX case)
   - If no match → continue to AABB-based walk (ST case)
3. (ST/walk only) Compiler reads room AABB from `ad_room_boundary` (or R*Tree)
4. (ST/walk only) Looks up M_BomCategory WHERE type='LI' AND aabb ≈ room AABB
5. (ST/walk only) Reads M_BomCategoryLine slots in Sequence order
6. (ST/walk only) For each slot: find best-fit BOM via `MBOM.findNextFitSpace()`,
   write new C_OrderLine + ESLine, advance cursor by slot AABB
7. Acceptance criterion: spatial digest == reference building's digest (Rosetta Stone)

**Key distinction:** M_BomCategory/Line are *just lists of holders, not a BOM
tree.* They describe what slots exist and at what priority. The actual BOMs (with
their children, buffers, dx/dy/dz offsets) are found in BOM.db separately during
compilation. The BomCategory/Line is the *recipe template*; the BOM is the *assembly*.

---

## 4. BOM Explosion Process

### 4.1 The trigger

User raises a C_Order (Construction Order) with `C_BPartner = 'DX'`.
Process button (DAGCompiler / `run_tests.sh`) fires the explosion.

### 4.2 The chain — DX example

```
Step 1: C_Order selects top-level M_BOM
        M_BOM = UNIT_DUPLEX_STD (BOMCategory='UN', C_BPartner='DX')

Step 2: Explode BOMLines (first generation — the unit's direct children)
        UNIT_DUPLEX_STD → M_BOM_Lines:
          seq=1  FLOOR_SLAB_GF    (BOMCategory='SL')  dZ=0         ← ground floor slab
          seq=2  FLOOR_DX_L1_STD  (BOMCategory='L1')  dZ=0         ← Level 1 contents
          seq=3  FLOOR_SLAB_L2    (BOMCategory='SL')  dZ=3000mm    ← upper floor slab
          seq=4  FLOOR_DX_L2_STD  (BOMCategory='L2')  dZ=3000mm    ← Level 2 contents
          seq=5  ROOF_ASSEMBLY    (BOMCategory='RF')  dZ=6000mm    ← roof

        Every physical layer is explicit: slab, contents, slab, contents, roof.
        Nothing implied. The unit IS its slabs + floors + roof.

Step 3: Explode each child (second generation)
        FLOOR_DX_L1_STD → M_BOM_Lines:
          seq=1  LIVING_SET              → Rm_Living_1    (BOMCategory='LI')
          seq=2  DINING_SET              → Rm_Dining_1    (BOMCategory='DN')
          seq=3  KITCHEN_CABINET_SET     → Rm_Kitchen_1   (BOMCategory='KT')
          seq=4  TOILET_BLOCK_FIXTURES   → Rm_Bath_L1     (BOMCategory='BT')

        ROOF_ASSEMBLY → M_BOM_Lines:
          seq=1  ROOF_STRUCTURE   (BOMCategory='FR', leaf — trusses/rafters)
          seq=2  ROOF_COVERING    (BOMCategory='FR', leaf — tiles/membrane)

Step 4: Explode room sets (third generation)
        LIVING_SET → M_BOM_Lines:
          seq=1  Piano         (BOMCategory='FR', leaf)     space=1500×600mm
          seq=2  SOFA_AREA     (BOMCategory='FR', sub-BOM)  space=2000×800mm
          seq=3  Loveseat      (BOMCategory='FR', leaf)     space=1600×800mm
          seq=4  Buffer_NW     (BOMCategory='ST', variable)
          seq=5  Buffer_NE     (BOMCategory='ST', variable)

Step 5: Explode sub-BOMs (fourth generation)
        SOFA_AREA → M_BOM_Lines:
          seq=1  Sofa_3Seat     (leaf)
          seq=2  Coffee_Table   (leaf)
          seq=3  Side_Tables    (leaf)
```

### 4.3 Normal mode — SH/DX (sparse CO_EmptySpaceLine, IsAvailable as quality gate)

```
Before explosion:
  CO_EmptySpace: AABB = 12372×26730×7884mm, is_available = Y

After explosion + translation to output DB (normal mode):
  CO_EmptySpaceLine #1: UNIT_DUPLEX_STD   level=0  full AABB
  CO_EmptySpaceLine #2: FLOOR_SLAB_GF     level=1  ground slab plane
  CO_EmptySpaceLine #3: FLOOR_DX_L1_STD   level=1  Level 1 body
  CO_EmptySpaceLine #4: FLOOR_SLAB_L2     level=1  upper slab plane
  CO_EmptySpaceLine #5: FLOOR_DX_L2_STD   level=1  Level 2 body
  CO_EmptySpaceLine #6: ROOF_ASSEMBLY     level=1  roof plane
  CO_EmptySpaceLine #7: DUPLEX_SET_STD    level=1  pair container

After tests GREEN:
  CO_EmptySpace: is_available = N  (confirmed: space consumed, output proven correct)

After tests FAIL (or reprocess):
  CO_EmptySpace: is_available = Y  (space not confirmed — build needs attention)
```

**Why only structural-tier lines?** At every BOM level there is exactly ONE
candidate — no variant selection needed. The lines track structural capacity
(unit, slabs, floors, roof, pair), not individual furniture items. SH produces
4 lines; DX produces 7 (see Appendix E for full dumps).

The BOM tree unfolds deterministically. The translation from BOM.db's abstract
offsets (dx/dy/dz, rotation_rule) to construction coordinates is a pure function
of the accepted structural tiers. No branching, no fallthrough, no iteration.

**Furniture sets use EN-BLOC (§3.3) — no extra ESLines for SH/DX.** When the
compiler reaches a room-level BOM (LIVING_SET, BED_SET, etc.), the room AABB
matches an M_BomCategory template exactly (e.g. `LI_SH` = 8869×4690mm for SH
Living Room). The compiler places the entire set as one block — ONE additional
ESLine — and uses the BOM's dx/dy/dz values to position each item. No new
C_OrderLines are written, no EXPLODE traversal occurs. This is why the SH/DX
spatial digest is stable: the same BOM offsets produce the same world coordinates
on every compile.

**Nothing should be amiss for SH/DX.** If placement errors occur, the bug is in
the translation function itself (BOM offset → world coordinate), not in variant
selection or space fitting. Reprocess mode (§3.6) pinpoints these.

### 4.4 Why extracted buildings always fit

For extracted buildings (DX, SH), there is only **one** record at each BOM layer.
One ground slab. One L1. One upper slab. One L2. One roof. In L1, one of each
room. In each room, one set of items. The whole construction — slabs, floors,
roof, rooms, furniture — equals the original extracted model. It fits by
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
  CO_EmptySpaceLine #2: FLOOR_SLAB_MY          level=1  accepted (slab — one variant)
  CO_EmptySpaceLine #3: FLOOR_TBLKTN_GF_STD    level=1  accepted (floor contents)
  CO_EmptySpaceLine #4: ROOF_PORCH_MY          level=1  accepted (porch roof variant)
  CO_EmptySpaceLine #5: BEDROOM zone → ?       level=2  SpaceSize match:
      candidate: BEDROOM_PREFAB_MY_3100 (3100×3100mm) — fits zone 3134×3105mm ✓
  CO_EmptySpaceLine #6: COMMON zone → ?        level=2  SpaceSize match:
      candidate: LIVING_PREFAB_MY — fits zone 3700×6195mm ✓
  CO_EmptySpaceLine #7: BATHROOM zone → ?      level=2  SpaceSize match:
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

The BOM tab (on C_OrderLine) is unchanged — all of BOM.db copied verbatim,
buffers included. CO_EmptySpaceLine is the ONLY place where the compiler records
alignment decisions. The translation to output DB (world coordinates for Blender
viewport / IFC export) uses BOM offsets + CO_EmptySpaceLine alignment. Every
orientation error, every misaligned position, every space overflow traces back to
either the BOM data (intact reference) or the alignment record (CO_EmptySpaceLine).

**The IsAvailable gate confirms the translation.** If CO_EmptySpace.is_available
remains Y after processing, the output did not pass tests — the translation failed
somewhere. Reprocess mode (§3.6) then writes verbose CO_EmptySpaceLines at every
level to pinpoint exactly where.

**Diagnosis pattern:**
1. Run in reprocess mode (`--reprocess-all`)
2. Query `co_empty_space_line` for the failing element
3. Compare `before/next/orientation_rad` against expected values
4. The delta IS the bug — trace back to the translation function

### 5.4 MEP and future extensions

CO_EmptySpaceLine can hold additional spatial references per function:

| Extension | CO_EmptySpaceLine field | Purpose |
|-----------|------------------------|---------|
| MEP connections | `mep_ref` (future) | Riser point, pipe junction coordinates |
| UBBL clearance | (via M_Attribute on leaf) | Minimum distances from walls/openings |
| 7D IoT | `iot_ref` (future) | Sensor placement, conduit routing |

Each is a separate spatial concern tracked on the EmptySpaceLine. The BOM leaf
stays clean — it holds WHAT the item is. The EmptySpaceLine holds WHERE it goes
in this particular construction and WHAT connects to it. This separation means
future spatial concerns (IoT sensors, conduit routing) can be added as new columns
on CO_EmptySpaceLine without touching the BOM catalog.

### 5.5 The 6-Layer Geometry Verification Chain

The full data chain from reference IFC to output world coordinates has exactly
six layers. The ST POC must prove correctness at every layer.

```
Layer 1: Extracted IFC (input/extracted.db)
  Source of truth. Pristine geometry from Bonsai/BlenderBIM export.
  Verify: Bonsai viewport visual match.

Layer 2: BOM.db (m_bom_line dx/dy/dz)
  Relative spatial arrangement between siblings.
  Verify: W-SPACESIZE-1 (children SUM ≤ parent AABB)
  Verify: Every leaf child_product_id → valid M_Product

Layer 3: BOM.db — M_Product (width/depth/height)
  Intrinsic product geometry in meters.
  Verify: Dimensions match extracted IFC bounding boxes.

Layer 4: C_Order (Construction Order): C_BPartner + AABB
  The 1D Intent. Two fields drive everything (see §3.7).
  Verify: AABB ≥ UNIT BOM SpaceSize (site fits building)

Layer 5: CO_EmptySpace/Line (output.db)
  Spatial anchoring at each decision point.
  Verify: before/next chain continuity (Line N.next = Line N+1.before)
  Verify: orientation_rad matches wall assignment

Layer 6: elements_meta / elements_rtree (output.db)
  Final world coordinates. One math operation:
    world_xyz = anchor + rotate(dx, dy, dz, orient)
  Defined in LocalCoord.toWorld() — the ONLY WorldCoord constructor.
  Verify: G8 centroid < 500mm, F4 edge < 10mm, SpatialDigest stable
```

**Key insight:** The geometry math is trivially correct — it is a single
rotate+translate in `LocalCoord.toWorld()`, enforced by the D8 ArchUnit gate.
When placement is wrong, **walk the chain backwards:**

- Layer 6 wrong → check Layer 5 anchor (is the anchor at the right wall face?)
- Layer 5 wrong → check Layer 2 offsets (are dx/dy/dz correct in the BOM?)
- Layer 2 wrong → check Layer 1 reference (does the extracted IFC match?)

Errors are always **data** (Layers 2–5), never math (Layer 6). This is why the
PRIME RULE is "EXTRACT, DON'T IMAGINE" — if the data chain is correct, the
geometry is correct by construction.

---

## 6. ad_room_slot Deprecation

`ad_room_slot` mapped `room_type → assembly_id` (BOM dispatch per room type).
With `BOMCategory` on M_BOM, this dispatch becomes implicit:

| Old (ad_room_slot) | New (BOMCategory) |
|--------------------|--------------------|
| `room_type=BEDROOM` → `assembly_id=BED_SET_MASTER` | M_BOM WHERE `BOMCategory='BD'` AND `C_BPartner=C_Order.C_BPartner` |
| `room_type=BATHROOM` → `assembly_id=BATHROOM_SET` | M_BOM WHERE `BOMCategory='BT'` AND `C_BPartner=C_Order.C_BPartner` |

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
- Buffer lines (BOMCategory='ST') with leftover space
- Locator lines where fixed items didn't fill the wall

---

## 8. Complete ERD

```
component_library.db (LOD Geometry Store)
┌─────────────────────────┐
│ component_geometries    │  Vertex/face BLOBs
│ lod_geometry_map        │  Element → geometry hash
│ lod_parametric_mesh     │  Procedural shape generators
│ surface_styles          │  Material colours
└─────────────────────────┘
          │
          │ geometry_hash FK (lod_geometry_map → component_geometries)
          │
BOM.db (Unified Working Database)
┌─────────────────────────┐
│ M_Product               │  intrinsic dims (m) + component_id + bom_id (NORM-1)
│ C_OrderLine             │  Construction Order Details: placement rules
│ C_Order                 │  Construction Order: building registrations
│ ad_* (60+ tables)       │  Config, rules, spatial, MEP
└─────────────────────────┘
          │
          │ product_id FK (leaf M_BOM → M_Product)
          ▼
BOM.db (Assembly Catalog — same database)
┌─────────────────────────┐
│ m_bom                   │  Assembly: BOMCategory + C_BPartner + SpaceSize
│   └── m_bom_line        │  Children: dx/dy/dz, rotation, locator, allocated_*_mm
│       └── m_bom (child) │  Recursive: M_BOM_Line.child_product_id → M_Product (MAKE → bom_id)
│ m_attribute             │  Leaf attributes: ports, clearances
│ M_BomCategory           │  Lookup: LI, BD, KT, FR, ST, L1, L2, UN
└─────────────────────────┘
          │
          │ family_ref FK (C_OrderLine → M_BOM.bom_id)
          ▼
output.db (Compiled Construction)
┌─────────────────────────┐
│ C_Order                 │  Construction Order (C_BPartner scopes M_BOM access)
│   ├── C_OrderLine       │  Construction Order Details (selects M_BOM, places in room)
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
 1. User:     Raise C_Order (Construction Order) with C_BPartner='DX'
 2. User:     Optionally edit C_OrderLines (add/remove/swap BOMs)
 3. Compiler: Read C_Order → C_OrderLines → M_BOM trees from BOM.db
              The BOM copy is COMPLETE: fixed items, sub-BOMs, AND buffer (ST) children
              with all SpaceSize, dx/dy/dz, rotation_rule intact as reference
 4. Compiler: Create CO_EmptySpace (AABB from building footprint, is_available=Y)
 5. Compiler: Accept top-level M_BOM into CO_EmptySpace
              → Write CO_EmptySpaceLine #1 (alignment: UNIT box → full AABB, orient=0)
              → is_available stays Y (not yet proven)
 6. Compiler: Explode M_BOM recursively: UNIT → SLAB → FLOOR → ROOM → SET → ITEM
              Buffer children walk with their parents — the BOM construct is complete
              Normal mode:  children translate deterministically from acceptance
                            (no further CO_EmptySpaceLines unless decision point)
              Reprocess:    write CO_EmptySpaceLine at EVERY level (verbose audit)
              Variant mode: write CO_EmptySpaceLine at each selection/conflict
 7. Compiler: At each decision point:
              a. Read SpaceSize from M_BOM_Line (including buffer SpaceSize)
              b. If multiple candidates: select by SpaceSize fit (fallthrough)
              c. Translate abstract BOM info → construction coordinates
              d. Write CO_EmptySpaceLine (alignment: box origin + orient)
              e. Check: remaining >= 0? (overflow = GIC violation)
 8. Compiler: Translate to output DB — BOM offsets + CO_EmptySpaceLine alignment
              → world coordinates (elements_meta, element_instances for Blender/IFC)
              This is where abstract BOM becomes concrete geometry
 9. Compiler: Run tests (G8 centroids, F4 edges, W-SPACESIZE-1, etc.)
              Tests GREEN → set is_available=N, DocStatus DR→CO (confirmed consumed)
              Tests FAIL  → is_available stays Y (space not successfully filled)
              Reprocess   → reset is_available=Y, process again from step 5
10. User:     Query v_co_available_space for remaining capacity
              is_available=Y after processing = build did NOT pass
```

---

## 10. First-Level BOMs in BOM.db (Residential Catalog)

These are the top-level M_BOMs — the "cars on the lot" that a C_Order can select:

| bom_id | BOMCategory | C_BPartner | Description |
|--------|--------------|-----------|-------------|
| `UNIT_DUPLEX_STD` | UN | DX | Duplex residential unit (2 floors) |
| `UNIT_SH_STD` | UN | SH | Sample House unit (1 floor) |
| `UNIT_TBLKTN_STD` | UN | TB | TB-LKTN terrace unit (1 floor) |
| `FLOOR_DX_L1_STD` | L1 | DX | Duplex Level 1 |
| `FLOOR_DX_L2_STD` | L2 | DX | Duplex Level 2 |
| `FLOOR_SH_GF_STD` | GF | SH | SH Ground Floor |
| `FLOOR_TBLKTN_GF_STD` | L1 | TB | TB-LKTN Ground Floor |

A C_Order with `C_BPartner='DX'` sees `UNIT_DUPLEX_STD` and its descendants.
A C_Order with `C_BPartner='TB'` sees `UNIT_TBLKTN_STD` — and can also
see generic BOMs (C_BPartner IS NULL) like `TOILET_BLOCK_FIXTURES`.

---

## Appendix A — Code Advice

> **Note:** Appendix B (Compiler Pipeline Changes), Appendix C (Migration State),
> and Appendix D (Assessment) follow. Appendix E contains the live CO_EmptySpace
> ledger dumps for SH and DX.

### A.1 DocStatus Lifecycle on CO_EmptySpace

CO_EmptySpace.doc_status follows iDempiere document lifecycle:

| Status | Meaning | When |
|--------|---------|------|
| `DR` | Draft | Project created, is_available=Y |
| `IP` | In Process | Compiler running, translation in progress |
| `CO` | Complete | Tests GREEN, is_available=N — construction confirmed |
| `RE` | Rejected | BOM falls outside CO_EmptySpace AABB — size mismatch, no fit |

```java
// On MCOEmptySpace
public void startProcessing()   { setDocStatus("IP"); setIsAvailable(1); }
public void confirmConsumed()   { setDocStatus("CO"); setIsAvailable(0); }
public void reject(String reason) { setDocStatus("RE"); setIsAvailable(1); /* log reason */ }
public void resetForReprocess() { setDocStatus("DR"); setIsAvailable(1); }

public boolean isPassing()      { return "CO".equals(getDocStatus()) && getIsAvailable() == 0; }
public boolean isRejected()     { return "RE".equals(getDocStatus()); }
```

**DR → IP → CO** is the happy path. **DR → IP → RE** means the BOM construct does not
fit the construction AABB no matter how the CO_EmptySpaceLines are arranged. The user
must either resize the CO_EmptySpace or select different BOMs.

### A.2 IsSpaceSizeValid — Per-Locator-Strip Invariant (W-SPACESIZE-1)

BOM.db has the full chaining info on `m_bom_line`: dx/dy/dz, rotation_rule,
locator_ref, AND SpaceSize. The invariant check is per-locator-strip, not a naive
global SUM — because children are grouped by locator (wall/zone) and arranged
linearly within each strip (GPD walk).

```java
// On MBOM — validates the BOM construct in BOM.db
public boolean isSpaceSizeValid() {
    List<MBOMLine> children = getLines();
    if (children.isEmpty()) return true;  // leaf — no children to sum

    // Group by locator_ref (NORTH_WALL, EAST_WALL, CENTRE, FLOAT...)
    Map<String, List<MBOMLine>> strips = children.stream()
        .collect(Collectors.groupingBy(MBOMLine::getLocatorRef));

    for (var entry : strips.entrySet()) {
        String locator = entry.getKey();
        List<MBOMLine> strip = entry.getValue();

        // Along each strip's primary axis: SUM(child.space) must equal strip length
        int stripAxisLength = getStripLength(locator);  // from parent AABB + locator
        int sumAlongAxis = strip.stream()
            .mapToInt(line -> line.getSpaceAlongAxis(locator))
            .sum();
        // SUM includes buffer (ST) children — they fill the gap
        if (sumAlongAxis != stripAxisLength) return false;
    }
    return true;
}
```

**This check runs in BOM.db** — it validates the assembly design itself. The same
data is then copied verbatim to C_OrderLine.BOM.BOMLine, so the invariant holds
there too without re-checking.

### A.3 IsConstructionValid — BOM Tree vs CO_EmptySpace AABB (W-CONSTRUCT-1)

After CO_EmptySpaceLine alignment, walk the BOM tree level-by-level and verify that
every resolved position stays within the CO_EmptySpace parent AABB. This is the
construction-level witness — it catches translation errors that the BOM-level
invariant (A.2) cannot see (because BOM.db knows nothing about the construction site).

```java
// On MCOEmptySpace — validates the construction output
public boolean isConstructionValid() {
    AABB site = getAABB();  // the CO_EmptySpace envelope

    // Walk BOM tree from top-level acceptance
    for (MCOEmptySpaceLine line : getLines()) {
        MBOM bom = line.getBOM();
        // Resolve world position: CO_EmptySpaceLine alignment + BOM dx/dy/dz
        AABB resolved = resolveWorldAABB(line, bom);

        if (!site.contains(resolved)) {
            // BOM construct falls outside construction AABB → reject
            reject("BOM " + bom.getBomId() + " at level " + line.getBomLevel()
                 + " extends beyond site AABB");
            return false;
        }
    }
    return true;
}
```

**CO_EmptySpaceLine traversal:** when there is only ONE CO_EmptySpaceLine (normal
mode for SH/DX), the check optimistically traverses the entire BOM tree from that
single alignment record. No other CO_EmptySpaceLine to consult — it walks level by
level using the BOM's own dx/dy/dz chaining, checking each resolved position against
the site AABB. If any child falls out → `RE` (Rejected).

In reprocess mode, there is a CO_EmptySpaceLine at every level — each can be checked
individually against the site AABB.

### A.4 fillSpaceBufferChildren — Buffer Computation per Strip

```java
// On MBOM — compute buffer SpaceSize for each locator strip
public void fillSpaceBufferChildren() {
    Map<String, List<MBOMLine>> strips = getLines().stream()
        .collect(Collectors.groupingBy(MBOMLine::getLocatorRef));

    for (var entry : strips.entrySet()) {
        String locator = entry.getKey();
        List<MBOMLine> strip = entry.getValue();
        int stripLength = getStripLength(locator);

        // Sum fixed children along strip axis
        int fixedSum = strip.stream()
            .filter(line -> !"ST".equals(line.getBomCategory()))
            .mapToInt(line -> line.getSpaceAlongAxis(locator))
            .sum();

        // Distribute remaining space to buffer (ST) children
        List<MBOMLine> buffers = strip.stream()
            .filter(line -> "ST".equals(line.getBomCategory()))
            .toList();

        if (!buffers.isEmpty()) {
            int remaining = stripLength - fixedSum;
            int perBuffer = remaining / buffers.size();  // equal split
            for (MBOMLine buf : buffers) {
                buf.setSpaceAlongAxis(locator, perBuffer);
                // depth + height = same as parent (buffer fills the full cross-section)
                buf.setSpaceDepthMm(getSpaceDepthMm());
                buf.setSpaceHeightMm(getSpaceHeightMm());
            }
        }
    }
}
```

### A.5 findNextFitSpace — Variant Selection (TB-LKTN)

```java
// Select M_BOM from BOM.db that fits available SpaceSize (fallthrough to smaller)
public MBOM findNextFitSpace(int widthMm, int depthMm, int heightMm,
                             String bomCategory, String bomOwner) {
    // SELECT FROM m_bom
    //   WHERE BOMCategory = ?
    //     AND (C_BPartner = ? OR C_BPartner IS NULL)
    //     AND allocated_width_mm  <= ?
    //     AND allocated_depth_mm  <= ?
    //     AND allocated_height_mm <= ?
    //   ORDER BY (allocated_width_mm * allocated_depth_mm * allocated_height_mm) DESC
    //   LIMIT 1
    // → largest BOM that fits the available space
}
```

### A.6 findAvailableSubSpace — Ad-hoc Item Placement

For placing an ad-hoc item (potted plant, lampshade) into remaining space within
an already-compiled room:

```java
// Find available sub-space within a room for an item's AABB
public Optional<MCOEmptySpaceLine> findAvailableSubSpace(
        String cOrderId, String roomName,
        int itemWidthMm, int itemDepthMm, int itemHeightMm) {
    // Query co_empty_space_line for this room's locator strips
    // WHERE remaining_mm >= itemWidthMm (along strip axis)
    //   AND parent depth >= itemDepthMm
    //   AND parent height >= itemHeightMm
    // ORDER BY remaining_mm ASC  -- tightest fit first
    // Returns the CO_EmptySpaceLine where the item can be placed
    // The item gets a new CO_EmptySpaceLine record (decision: ad-hoc placement)
    // Buffer in that strip shrinks accordingly
}
```

This enables the "can a potted plant fit in that corner?" query. The buffer children
in the BOM construct are the available sub-spaces. After placement, the buffer's
SpaceSize shrinks (or a new buffer is created for the remainder), and a new
CO_EmptySpaceLine records the decision.

### A.7 Test Gates — Witness Registry

| Gate | Level | What it checks | Pass condition |
|------|-------|----------------|----------------|
| **W-SPACESIZE-1** | BOM.db | Per-locator-strip: SUM(children) = strip length | Zero violations across all active M_BOMs |
| **W-CONSTRUCT-1** | CO_EmptySpace | BOM tree walk stays within site AABB | Every resolved child inside CO_EmptySpace envelope |
| **W-PHANTOM-1** | EmptySpace | capacity - used = remaining, no overflow | Already in EmptySpaceTest (3 tests) |
| **W-OWNER-1** | C_Order→M_BOM | No C_Order references BOM with wrong C_BPartner | Zero cross-owner refs (unless C_BPartner IS NULL) |
| **W-CATEGORY-1** | M_BOM | BOMCategory is functional (LI/BD/KT), never building (SH/DX) | Zero building codes in BOMCategory column |
| **W-ISAVAIL-1** | CO_EmptySpace | After full compile, is_available=N for every C_Order | Zero is_available=Y after successful processing |
| **W-VERBATIM-1** | C_OrderLine→BOM.db | BOMLine copy matches BOM.db source | Hash/checksum match on all copied BOM trees |
| **W-DOCSTATUS-1** | CO_EmptySpace | DocStatus consistent with is_available | CO→is_available=0, RE→is_available=1, no contradictions |
| **G8** | Output DB | Centroid proximity vs reference | < 500mm per element (RosettaPlacementTest) |
| **F4** | Output DB | Edge-level bbox proof | Edges match IFC reference within 10mm |
| **F5** | Output DB | Glass transparency + staircase Z-span | alpha < 0.5, staircase spans floor-to-floor |

---

## Appendix B — Compiler Pipeline Changes (BOM + EmptySpace → output.db)

The current compiler goes straight from BOM → PlacedElement → output DB with **no
CO_EmptySpace involvement**. The pipeline must change to route through CO_EmptySpace
alignment and track the IsAvailable/DocStatus quality gate.

### B.1 Current Pipeline (7 steps, no EmptySpace)

```
CompilationPipeline.java — 7 stages:
  1. MetadataValidator
  2. ParseStage       → BuildingParser.parse()        → BuildingDefinition
  3. CompileStage     → BuildingCompiler.compileWithValidation() → BuildingSpec
  4. WriteStage       → BuildingWriter.initSchema() + write(spec)
  5. DigestStage      → SpatialDigest.computeWithReport()
  6. GeometryStage    → GeometryIntegrityChecker.check()
  7. ProveStage       → PlacementProver.proveFromDB()
```

**BOM resolution path** (inside CompileStage, post-G-1):
```
StoreyCompiler.placeFixturesAndFurniture(ctx)         [line 1333]
  → WorkerRegistry → FurnitureWorker.execute(envelope, placementCtx)
    → BOMTierResolver.resolveForRoom()                [three-way dispatch]
      → walks m_bom → m_bom_line recursively (fixture params / GPD / FLOAT)
      → returns List<PlacedFurniture> with world xyz + rotation radians
  → addPlacedElementsToCtx(ctx, roomName, elements)
    → PlacedElement → FixtureSpec(x, y, z, rotation, geoHash, w, d, h)
```

**Output write path** (inside WriteStage):
```
BuildingWriter.write(spec)
  → MEPWriter.writeFixture(fixture, storeyName)        [line 548]
    → compute rotated bbox (halfW*|cos|+halfD*|sin|)   [line 569-581]
    → get/generate geometry (LOD400 mesh or fallback box)
    → ElementPersistence.writeElementMeta(guid, bbox, material)  [→ elements_meta + elements_rtree]
    → ElementPersistence.writeInstance(guid, geoHash)            [→ element_instances]
```

**Current state (post-Phase 4):** CO_EmptySpace + CO_EmptySpaceLine written at
L0+L1 levels. IsAvailable quality gate operational. `wm_empty_storage_line`
deprecated — superseded by `co_empty_space_line`.

### B.2 New Pipeline (9 steps, EmptySpace integrated)

```
CompilationPipeline.java — 9 stages:
  1. MetadataValidator
  2. ParseStage         → BuildingParser.parse() → BuildingDefinition
  3. EmptySpaceStage    → NEW: create CO_EmptySpace (AABB from building footprint)
                          Set is_available=Y, doc_status='DR'
  4. BOMCopyStage       → NEW: copy M_BOM tree verbatim from BOM.db to C_OrderLine.BOM
                          ALL children intact: fixed items, sub-BOMs, AND buffers (ST)
                          SpaceSize, dx/dy/dz, rotation_rule — everything transfers
  5. CompileStage       → CHANGED: resolve through CO_EmptySpaceLine alignment
                          Set doc_status='IP'
  6. WriteStage         → BuildingWriter writes elements_meta + element_instances
                          AND writes co_empty_space + co_empty_space_line to output.db
  7. ValidateStage      → NEW: isConstructionValid() — walk BOM tree vs site AABB
                          Tests GREEN → set is_available=N, doc_status='CO'
                          Tests FAIL  → is_available stays Y, doc_status='RE' if outside AABB
  8. DigestStage        → SpatialDigest (unchanged)
  9. GeometryStage      → GeometryIntegrityChecker (unchanged)
```

### B.3 Stage 3 — EmptySpaceStage (NEW)

```java
// CompilationPipeline — new stage between Parse and Compile
class EmptySpaceStage implements PipelineStage {
    void execute(PipelineContext ctx) {
        // 1. Read building footprint from C_Order
        //    AABB = building envelope (width × depth × height in mm)
        // 2. Create CO_EmptySpace record in output.db
        //    origin = (0,0,0), AABB from footprint
        //    is_available = 1, doc_status = 'DR'
        // 3. Store co_emptyspace_id in ctx for downstream stages
    }
}
```

**Output.db schema addition:**
```sql
-- co_empty_space and co_empty_space_line tables created in output.db
-- (DDL already specified in §3.1 and §3.2 of this document)
```

### B.4 Stage 4 — BOMCopyStage (NEW)

```java
// Copy M_BOM tree from BOM.db to C_OrderLine.BOM (verbatim)
class BOMCopyStage implements PipelineStage {
    void execute(PipelineContext ctx) {
        // 1. For each C_OrderLine (Construction Order Details) with family_ref:
        //    a. Load M_BOM tree from BOM.db (m_bom → m_bom_line, recursive)
        //    b. Copy verbatim to C_OrderLine.BOM tab in output.db
        //       Including ALL buffer (ST) children + SpaceSize
        //    c. Store checksum for W-VERBATIM-1 verification
        // 2. The compiler reads from this copy, not BOM.db directly
        //    Scope is locked to what was ordered
    }
}
```

### B.5 Stage 5 — CompileStage (CHANGED)

The BOM resolution path changes from direct world-coordinate computation to
**CO_EmptySpaceLine-mediated alignment**:

```
CURRENT (post-G-1):
  BOMTierResolver.resolveForRoom(room, bomId)
    → walks m_bom_line recursively (three-way dispatch)
    → computes world xyz directly (room anchor + dx/dy/dz + rotation)
    → returns PlacedFurniture(worldX, worldY, worldZ, rotation)

NEW (ST mode):
  BOMTierResolver.resolveForRoom(room, bomId, coEmptySpaceId)
    → walks m_bom_line from C_OrderLine.BOM copy (not BOM.db)
    → at decision points: writes CO_EmptySpaceLine
        (alignment: box origin + orientation in construction space)
    → translates: BOM dx/dy/dz + CO_EmptySpaceLine alignment → world coords
    → buffer (ST) children: no geometry, but space tracked in CO_EmptySpaceLine.remaining_mm
    → returns PlacedFurniture(worldX, worldY, worldZ, rotation)
```

**Specific method changes:**

| Method | File:Line | Current | New |
|--------|-----------|---------|-----|
| `placeFixturesAndFurniture` | StoreyCompiler:1333 | No EmptySpace | Accept `coEmptySpaceId`, pass to workers |
| `worker.execute` | BundleWorker | Returns PlacedElement directly | Also writes CO_EmptySpaceLine at decision points |
| `resolveForRoom` | BOMTierResolver | Reads m_bom_line from library DB | Reads from C_OrderLine.BOM copy in output.db |
| `computeBomAnchorForRoom` | BOMTierResolver | Computes anchor from room bounds | Uses CO_EmptySpaceLine alignment as anchor |
| `expandBOMNode` | BOMTierResolver | Walks m_bom_line, skips buffers | Walks m_bom_line, tracks buffer space in CO_EmptySpaceLine |

**CO_EmptySpaceLine write points (normal mode):**
```
For SH/DX:
  1 line: top-level BOM accepted into full AABB
  (all children translate deterministically — same as current code, just routed through alignment)

For TB-LKTN (or --reprocess-all):
  1 line per decision point: variant selection, space conflict, orientation change
  Buffer space tracked via remaining_mm on each line
```

**Buffer handling in resolver:**
```java
// In expandBOMNode or resolveWithGPD:
for (MBOMLine child : bomLines) {
    if ("ST".equals(child.getBomCategory())) {
        // Buffer child — no geometry, no PlacedElement
        // But track in CO_EmptySpaceLine: remaining_mm -= 0 (buffer IS the remaining)
        continue;  // skip geometry output
    }
    // Fixed child — resolve position, generate PlacedElement
    // Track: remaining_mm -= child.getSpaceAlongAxis()
}
```

### B.6 Stage 6 — WriteStage (CHANGED)

In addition to current elements_meta + element_instances writes:

```java
// BuildingWriter.write(spec) — additional writes
//   1. Write co_empty_space record (from EmptySpaceStage)
//   2. Write all co_empty_space_line records (from CompileStage)
//   3. Set doc_status = 'IP' on co_empty_space (processing complete, awaiting validation)
```

**ElementPersistence additions:**
```java
public void writeCOEmptySpace(MCOEmptySpace es) {
    // INSERT INTO co_empty_space VALUES (...)
}
public void writeCOEmptySpaceLine(MCOEmptySpaceLine line) {
    // INSERT INTO co_empty_space_line VALUES (...)
}
```

### B.7 Stage 7 — ValidateStage (NEW)

```java
class ValidateStage implements PipelineStage {
    void execute(PipelineContext ctx) {
        MCOEmptySpace es = ctx.getEmptySpace();

        // 1. isConstructionValid() — walk BOM tree vs site AABB (W-CONSTRUCT-1)
        if (!es.isConstructionValid()) {
            es.reject("BOM construct falls outside site AABB");
            return;  // doc_status='RE', is_available stays Y
        }

        // 2. isSpaceSizeValid() — per-locator-strip check on BOM copy (W-SPACESIZE-1)
        //    (validates the copied BOM, not BOM.db — should be identical)

        // 3. Run existing gates: G8 centroids, F4 edges, F5 glass
        //    PlacementProver.proveFromDB()
        //    GeometryIntegrityChecker.check()

        // 4. ALL GREEN → confirm consumed
        es.confirmConsumed();  // doc_status='CO', is_available=N
    }
}
```

### B.8 Reprocess Mode (--reprocess-all flag)

```java
// CompilationPipeline — accept CLI flag
boolean reprocessAll = args.contains("--reprocess-all");

// In CompileStage:
if (reprocessAll) {
    // Reset: co_empty_space.is_available = Y, doc_status = 'DR'
    // Delete existing co_empty_space_line records
    // Re-resolve: write CO_EmptySpaceLine at EVERY BOM level (verbose audit)
    // For SH/DX: same result, more lines (pure verification)
    // For TB-LKTN: actual working mode (real decisions at each level)
}
```

### B.9 World Coordinate Flow — Before vs After

```
BEFORE (current, post-G-1):
  m_bom_line (BOM.db) → BOMTierResolver → world xyz directly
                         (room anchor + dx/dy + rotation around centroid)
                       → PlacedFurniture(worldX, worldY, worldZ, rot)
                       → FixtureSpec → MEPWriter → elements_meta

AFTER (ST mode):
  C_OrderLine.BOM copy → BOMTierResolver → CO_EmptySpaceLine (alignment: origin + orient)
                                      → BOM dx/dy/dz + alignment → world xyz
                                      → PlacedFurniture(worldX, worldY, worldZ, rot)
                        → FixtureSpec → MEPWriter → elements_meta
                                                  + co_empty_space_line (output.db)
```

**Key difference:** the resolver reads from the C_OrderLine.BOM copy (not BOM.db
directly), and the alignment step is explicit via CO_EmptySpaceLine. The world
coordinate computation is the same math — but the intermediate alignment record
makes the translation auditable.

### B.10 Output.db Schema Summary (after changes)

| Table | Status | Written by |
|-------|--------|------------|
| `elements_meta` | Existing | ElementPersistence.writeElementMeta() |
| `element_instances` | Existing | ElementPersistence.writeInstance() |
| `base_geometries` | Existing | ElementPersistence.writeGeometry() |
| `elements_rtree` | Existing | ElementPersistence.writeElementMeta() |
| `element_transforms` | Existing | ElementPersistence (spatial index) |
| `co_empty_space` | Existing (Phase 4) | CompilationPipeline + ValidateStage |
| `co_empty_space_line` | Existing (Phase 4) | CompilationPipeline (structural tiers) |

---

## Appendix C — Migration State & Remaining Work

### C.1 Migration State (updated 2026-02-26)

`migration_bom_dimension_model.sql` (8 parts) + Phase 1 records/SpaceSize scripts — **ALL COMPLETE.**

| Part | What | Status |
|------|------|--------|
| 0 | Table renames (ad_bom→m_bom, etc.) | **DONE** |
| 1 | M_BomCategory lookup (LI/BD/KT/FR/ST/L1/L2/UN + 6 more) | **DONE** |
| 2 | `C_BPartner` column on m_bom | **DONE** |
| 3 | `space_width/depth/height_mm` on m_bom_line | **DONE** |
| 4 | `C_BPartner` column on C_Order (Construction Order) | **DONE** |
| 5 | Seed C_BPartner on buildings (SH/DX/TB/TE) | **DONE** |
| 6 | Copy old BOMCategory → C_BPartner | **DONE** |
| 7 | Repurpose BOMCategory to functional codes | **DONE** |

Additional BOM Dimension Phase 1 migrations (2026-02-25):
- `migration_bom_dimension_phase1_records.sql` — 14 BOMCategory codes, UNIT/FLOOR/ROOM BOM trees for DX with slab + roof children
- `migration_bom_dimension_phase1_spacesize.sql` — SpaceSize seeded on all m_bom_line from ad_product_dim + computed aggregates

### C.2 BOM Data Completeness (updated 2026-02-26)

| Item | Status |
|------|--------|
| FLOOR_SLAB_GF / FLOOR_SLAB_L2 BOMs | **DONE** — UNIT children with dZ offsets |
| ROOF_ASSEMBLY children | **DONE** — structural + covering children |
| SpaceSize on all m_bom_line | **DONE** — seeded from product dims |
| Functional BOMCategory (LI/BD/KT etc.) | **DONE** — 14 codes |
| C_BPartner scoping on BOMs | **DONE** — SH/DX/TB/TE |
| Buffer (ST) children on room BOMs | **PARTIAL** — schema ready, records pending for some rooms |

### C.3 Remaining Work — Phase ST

**Phase ST-1b complete** (2026-02-27): Schema foundation (ST-0), BOM template
contract with MinQty/MaxQty (ST-1a), aspect columns + DX composition proof
(ST-1b). Test gate: 207 PASS / 1 intentional RED / 1 SKIP.

Completed:
- `bom_owner→c_bpartner` rename, C_BPartner lookup, M_BomCategoryLine template
- AABB on c_order, ST_SH dormant entry
- `BomTemplateContract.check()` — catalog completeness validation
- Aspect columns (`num_units`, `storey_count`, `mirroring_rule`) on M_BomCategoryLine
- DX template branch (RE→PR→HU→{L1,L2}→rooms)
- `BomTemplateComposer.compose()` — composition proof (W-COMPOSE-DX)
- `MBOM.findBestFitAnyOwner()` — catalog-wide BOM selection

Remaining pipeline gaps for ST mode:

| Gap | What | Phase |
|-----|------|-------|
| Template-driven compilation | Wire composer into CompilationPipeline, create ESLs | **ST-1c** |
| CO_EmptySpaceLine L2–L3 | Room-level + item-level spatial records | ST-1c |
| BOMCopyStage | Verbatim copy M_BOM tree to C_OrderLine.BOM | Appendix B.4 design |
| ValidateStage | isConstructionValid + IsAvailable quality gate | Appendix B.7 design |
| Reprocess mode flag | `--reprocess-all` verbose audit | Appendix B.8 design |

**POC gate:** `SpatialDigest(ST_SH) == SpatialDigest(SH)` — proves the engine
before unlocking TB-LKTN.

### C.4 DAO ORM — Operational

All resolver code uses DAO pattern (`ModelQuery<X_M_BOMLine>`, `X_M_BOM`, etc.).
Raw JDBC only for single-consumer AD tables (ad_building_grid, ad_wall_face,
ad_room_slot). The X_/M_ classes reference the renamed tables.

Key DAO classes:
- `X_M_BOM` / `MBOM` — assembly definition (Table_Name = "m_bom")
- `X_M_BOMLine` / `MBOMLine` — child placement + SpaceSize (Table_Name = "m_bom_line")
- `X_M_Attribute` / `MAttribute` — leaf attributes (Table_Name = "m_attribute")
- `X_M_BomCategory` / `MBomCategory` — functional type lookup (+Value, +C_BPartner_ID)
- `X_CBPartner` / `MCBPartner` — building pattern owner lookup (NEW in ST-0)
- `X_MBomCategoryLine` / `MBomCategoryLine` — template decomposition recipe (NEW in ST-0)
- `X_CO_EmptySpace` / `M_CO_EmptySpace` — construction site header
- `X_CO_EmptySpaceLine` / `M_CO_EmptySpaceLine` — spatial alignment record

Pattern: `docs/DEVELOPER_GUIDE.md` — DAO Pattern section.
Working example: `BOMTierResolver.resolveForRoom()` (Phase G-1).

---

## Appendix D — Assessment of Concept

### D.1 Highlights

**1. The 1D Intent — radical simplification.**
The entire building definition collapses to two fields: `C_BPartner` (WHO) +
`AABB` (HOW BIG). Every downstream decision — which unit, which floor, which
room set, which furniture, which buffer — derives from these two roots. This is
a genuinely novel framing: a building is not a geometric model but a
construction order with a bill of materials. The DSL, the grid, the room
boundaries — all become implementation details of the BOM explosion, not
first-class inputs.

**2. ERP as the domain model, not a bolt-on.**
By mapping directly onto iDempiere entities (C_Order, C_OrderLine,
M_BOM/M_BOM_Line, CO_EmptySpace), the system inherits a battle-tested
transactional framework. DocStatus lifecycle (DR→IP→CO→VO), IsAvailable
quality gates, and reprocess semantics come for free. The domain language is
procurement and logistics, not geometry — which turns out to be the right
abstraction for prefab construction where the question is "what fits where?"
not "what shape is this?"

**3. Three orthogonal dimensions.**
Category (WHAT) × C_BPartner (WHO) × SpaceSize (HOW MUCH) give a clean
factorization of the BOM catalog. A bedroom set is a bedroom set regardless of
which building pattern owns it (category). The same building pattern can have
multiple room variants (SpaceSize). Vendor-neutral selection (ST mode) falls
out naturally by relaxing the WHO constraint.

**4. CO_EmptySpace as spatial audit trail.**
Every BOM placement gets a before/next coordinate record. This is not geometry
— it is a ledger entry. The translation from BOM offsets to world coordinates
happens exactly once, in one place, and the CO_EmptySpaceLine records are the
single checkpoint. Debugging spatial errors reduces to reading a table, not
replaying a geometric algorithm.

**5. Deterministic replay from extracted buildings.**
Extracted buildings (SH, DX) fit by construction — the BOM was reverse-
engineered from a model that already fit. This gives a ground-truth baseline
for any algorithmic changes: SpatialDigest comparison proves the engine has not
regressed. The Rosetta Stone discipline (prove on known-good before attempting
unknown) is a strong engineering methodology.

**6. Template-driven decomposition (M_BomCategoryLine).**
The recursive category template (RE→{SL,GF,RF}, GF→{LI,BD,...}) separates the
decomposition recipe from the BOM catalog. New building patterns can be defined
by adding template rows — no Java code changes. The Z-ratio encoding
(Z_Offset_Ratio, Z_Extent_Ratio) keeps vertical proportions data-driven.

### D.2 Potential Shortcomings

**1. 1D strip packing is a simplification, not reality.**
The axis model (Width=SUM, Depth=MAX, Height=MAX) reduces 3D spatial
arrangement to 1D strip packing along the width axis. Real rooms are not
arranged in a single strip — they tile in 2D. A living room beside a bedroom
beside a kitchen is a 2D floor plan, not a 1D sequence. The current model works
for SH (3 rooms in a row) and DX (rooms in L-shaped floors), but will hit
limits for complex floor plans with corridors, T-junctions, or irregular
footprints. The 2D tiling problem is fundamentally harder than strip packing.

**2. AABB is a coarse envelope.**
Real buildings have L-shaped, T-shaped, or irregular footprints. An
axis-aligned bounding box wastes space on non-rectangular plans and provides no
mechanism for concavities. The gap between AABB and actual usable floor area
grows with plan complexity. For POC with rectangular SH this is fine; for
real-world buildings it may become a blocking limitation.

**3. Template granularity vs. real diversity.**
The M_BomCategoryLine template assumes a fixed decomposition recipe: every
residential building has exactly {slab, ground floor, roof}, and every ground
floor has exactly {living, bedroom, dining, kitchen, bathroom}. Real buildings
vary: a studio apartment has no separate bedroom; a 4-bedroom house has
multiple bedrooms with different sizes; a split-level house has fractional
storeys. The template must either enumerate all variants (combinatorial
explosion) or accept that some buildings don't fit the template (requiring
manual override or new templates).

**4. Best-fit selection assumes a populated catalog.**
`findNextFitSpace()` selects the largest BOM that fits within available space.
This requires a catalog of pre-built room BOMs at various sizes. If the catalog
has only one bedroom size and the available space is significantly larger or
smaller, the selection either wastes space or fails. Catalog density directly
limits the utility of ST mode. Generating BOM variants automatically (parametric
rooms) is not yet addressed.

**5. No rotation or orientation algebra.**
The current model stores orientation as a scalar (radians) and rotation_rule as
a string. There is no formal algebra for composing rotations across BOM levels,
handling mirroring (DX Unit B), or resolving orientation conflicts when
template-selected BOMs face different directions than the parent expects. The DX
duplex already requires π rotation for the mirrored unit — this is handled as a
special case, not a general mechanism.

**6. CO_EmptySpaceLine count explosion in ST mode.**
Owner-matched builds need ~1 CO_EmptySpaceLine (deterministic, single path). ST
mode needs one line per BOM node at every level — potentially hundreds for a
moderately complex building. The current schema handles this, but query
performance, debugging clarity, and reprocess cost scale linearly with line
count. The conceptual elegance of "one ledger entry per placement" becomes a
practical burden if the tree is deep and wide.

**7. Two-database coordination.**
BOM.db holds the master data; output.db holds the compiled result. The
compilation pipeline reads from one and writes to the other, with no
transactional guarantee across the two SQLite databases. A crash mid-pipeline
can leave output.db in an inconsistent state. The reprocess mechanism (§3.6)
mitigates this but does not eliminate it. A single-database design with
views/triggers would be more robust but would conflate master data with output.

**8. Gap between ERP metaphor and construction reality.**
The iDempiere mapping is intellectually elegant but can confuse domain experts.
A structural engineer thinks in beams and columns, not in C_Orders and
M_BOM_Lines. The abstraction helps the developer but hinders communication with
the construction industry. Documentation must bridge this gap — the concept is
sound but the vocabulary barrier is real.

### D.3 Overall Assessment

The Construction-as-ERP concept is a strong architectural foundation. The 1D
Intent (WHO + HOW BIG) is a genuine insight — most BIM systems over-specify the
input when two fields suffice. The three-dimension BOM model, CO_EmptySpace
audit trail, and template decomposition are well-designed and data-driven.

The primary risk is the gap between the 1D strip model and real 2D/3D spatial
arrangement. The POC strategy (prove on SH, then DX, then TB-LKTN) is the
right approach — each building type stress-tests a progressively harder spatial
constraint. If ST_SH reproduces SH's SpatialDigest, the engine is sound for
rectangular single-storey buildings. The harder tests (multi-unit, multi-storey,
irregular plans) will reveal whether the AABB/strip model generalizes or needs
extension to 2D bin packing.

The ERP metaphor is not a limitation — it is a discipline. By forcing every
placement decision through a ledger (CO_EmptySpaceLine), the system gains
auditability that pure-geometry BIM systems lack. The question is not whether
the metaphor holds, but whether the spatial model beneath it is rich enough for
the target building types.

### D.4 Catalog Cart Model & Aspect Injection

**The 2D→3D lesson.** Extracting structure from 2D drawings is lossy and
labour-intensive. The alternative: a catalog of ready-made BOM artifacts that
already encode correct IFC geometry. Selection replaces extraction.

**Standard mould.** The catalog provides a validated framework of prefab BOMs.
A future Bonsai GUI enables *constraint editing* — swap room variants, adjust
quantities — not geometry redrawing. The user fills a cart from the catalog;
the compiler assembles the building.

**Aspect injection.** Three columns on `M_BomCategoryLine` parametrically
branch the template tree:

| Column | Semantics |
|--------|-----------|
| `num_units` | 0=universal (SL, RF, room-level), 1=single-household (GF), 2=dual-household (PR) |
| `storey_count` | Informational: how many storeys this subtree spans |
| `mirroring_rule` | 'NONE' or 'PARTY_WALL_PI' — aspect injection for mirrored pairs |

When `num_units=2`, the RE template activates the PR→HU→{L1,L2}→rooms branch
and skips GF (single-household). When `num_units=1`, the reverse. Universal
nodes (`num_units=0`) like SL (slab) and RF (roof) appear in all configurations.

**Composition proof.** Pass DX's AABB (12372×26730×7884) + `num_units=2` using
generic residential mode. The system walks the RE template, branches to the
duplex path, and at each leaf finds the best-fitting BOM from the *entire
catalog* (all owners). Since only DX owns PR and HU category BOMs, those
self-select without ever specifying `c_bpartner='DX'`. Room-level BOMs (LI,
BD, KT, BT, DN) select from generic NULL-owner BOMs — shared parts.

The AABB constraint + template branching naturally produces DX structure without
ever saying "build a duplex." This proves the catalog cart mechanism.

**Forward challenges.** L-shaped rooms, adjacency constraints, structural grid
alignment, MEP proximity — each becomes a template constraint or AD rule, not a
drawing operation. The existing AD infrastructure (`ad_typology_template`,
`ad_unit_type`, `ad_spatial_rule`, `ad_check_threshold`) provides injection
points for future enrichment without changing the composition engine.

---

## Appendix E: CO_EmptySpace Ledger — SH vs DX

The CO_EmptySpace model is the **audit trail of compilation**. Every BOM
placement is a ledger line — an ERP sales-order line that tracks what was
placed, where, and how much capacity was consumed.

The pipeline does NOT descend to furniture level in CO_EmptySpaceLines.
Each line represents a **structural tier** (unit, slab, floor, roof, pair).
The furniture placement happens inside the compiler when it walks the
floor BOM's children (room SETs → furniture items), but those individual
items are written to `c_orderline`, not to CO_EmptySpaceLine.

### Data flow

```
c_order (BOM.db)           — WHO + HOW BIG (input order)
  └─ c_orderline (BOM.db)  — per-element placement rules (1,263 total)
       │                      SH: 62 rules, DX: 1,115 rules
       │
       ▼  [Compiler runs]
       │
co_empty_space (output DB)  — envelope AABB (1 per building)
  └─ co_empty_space_line    — structural tier ledger
       │                      SH: 4 lines, DX: 7 lines
       │
       ▼  [Compiler writes elements]
       │
element_instances (output DB) — final IFC elements
                                SH: 56, DX: 1,089
```

### A.1 SH — Ifc4_SampleHouse (single-storey, 4 lines)

**c_order:**

| Field | Value |
|-------|-------|
| building_id | Ifc4_SampleHouse |
| building_name | IFC4 Sample House |
| building_type | RESIDENTIAL |
| c_bpartner | SH |
| aabb_width_mm | 16867.5 |
| aabb_depth_mm | 8667.5 |
| aabb_height_mm | 3945.2 |
| doc_status | CO |

**co_empty_space** (1 row — the envelope):

| Field | Value |
|-------|-------|
| origin_x/y/z_mm | −9234.9 / −2746.4 / −470.0 |
| aabb_width/depth/height_mm | 16867.5 / 8667.5 / 3945.2 |
| is_available | 0 (fully consumed) |
| doc_status | CO |

**co_empty_space_line** (4 rows):

| seq | bom_id | role | level | storey | Z range (mm) |
|-----|--------|------|-------|--------|--------------|
| 0 | UNIT_SH_STD | UNIT | 0 | — | −470 → 3475 |
| 5 | FLOOR_SLAB_GF | GROUND_SLAB | 1 | — | −470 (slab plane) |
| 10 | FLOOR_SH_GF_STD | GROUND_FLOOR | 1 | Ground Floor | −470 → 2830 |
| 15 | ROOF_ASSEMBLY | ROOF | 1 | — | 2530 (roof plane) |

4 lines. Single-storey: one slab, one floor, one roof, one unit container.
The FLOOR_SH_GF_STD line is where the compiler walks into room SETs
(LI→LIVING_SET, BD→BED_SET, etc.) and writes furniture to c_orderline.

### A.2 DX — Ifc2x3_Duplex (two-storey duplex, 7 lines)

**c_order:**

| Field | Value |
|-------|-------|
| building_id | Ifc2x3_Duplex |
| building_name | IFC2x3 Duplex |
| building_type | RESIDENTIAL |
| c_bpartner | DX |
| aabb_width_mm | 12372.7 |
| aabb_depth_mm | 26730.8 |
| aabb_height_mm | 7884.8 |
| doc_status | CO |

**co_empty_space** (1 row — the envelope):

| Field | Value |
|-------|-------|
| origin_x/y/z_mm | −3147.7 / −22182.7 / −1250.0 |
| aabb_width/depth/height_mm | 12372.7 / 26730.8 / 7884.8 |
| is_available | 0 (fully consumed) |
| doc_status | CO |

**co_empty_space_line** (7 rows):

| seq | bom_id | role | level | storey | Z range (mm) |
|-----|--------|------|-------|--------|--------------|
| 0 | UNIT_DUPLEX_STD | UNIT | 0 | — | −1250 → 6635 |
| 5 | FLOOR_SLAB_GF | GROUND_SLAB | 1 | — | −1250 (slab plane) |
| 10 | FLOOR_DX_L1_STD | LEVEL_1 | 1 | Ground | −1250 → 1850 |
| 15 | FLOOR_SLAB_L2 | UPPER_SLAB | 1 | — | 1750 (slab plane) |
| 20 | FLOOR_DX_L2_STD | LEVEL_2 | 1 | Upper | 1750 → 4650 |
| 25 | ROOF_ASSEMBLY | ROOF | 1 | — | 4750 (roof plane) |
| 100 | DUPLEX_SET_STD | PAIR | 1 | — | 4750 (pair container) |

7 lines. Two-storey: two slabs (GF + L2 interfloor), two floors (L1 + L2),
one roof, one unit, one pair container. The DUPLEX_SET_STD (PAIR) line
is the mirrored half-unit structure (Unit A + Unit B at π rotation).

### A.3 Why not furniture-level lines?

CO_EmptySpaceLine records **structural capacity** — each line is an
AABB reservation that the compiler fills with BOM children. The furniture
items (individual IfcFurnishingElements) are too numerous (56 for SH,
1,089 for DX) and belong to the **element output** layer, not the
capacity-tracking ledger.

The layered process:

1. **CO_EmptySpaceLine** — structural tiers (4–7 lines per building)
2. **c_orderline** — per-element placement rules (62–1,115 per building)
3. **element_instances** — final IFC geometry output

A flat single-tier building (like SH) needs only 4 ledger lines because
there is one of everything: one slab, one floor body, one roof. A
multi-storey building (like DX) adds lines for each additional slab,
floor, and structural container (PAIR). The line count scales with
**structural complexity**, not element count.

---

## 11. Design Decisions — Q&A1 Consolidation (2026-03-02)

Decisions confirmed through structured Q&A. Each resolves a model ambiguity.

### 11.1 EN-BLOC = Singularity (mathematical result, not optimisation)

EN-BLOC occurs when `C_Order.C_BPartner` matches `M_BOM.C_BPartner` AND exactly
one BOM exists. This is a mathematical singularity — the answer is unique, so
the compiler takes it whole. The moment there is choice (different C_BPartner,
multiple candidates, user edits), EXPLODE occurs.

- **Same AABB, different C_BPartner = EXPLODE.** ST_SH has SH's AABB but
  C_BPartner='ST' — no match, so the compiler walks.
- **Future Bonsai GUI:** EN-BLOC gives one orderline. User clicks any part to
  modify (add bath, resize room, add balcony) — each change spawns new
  C_OrderLines, switching to EXPLODE. Design semantics introduced at edit time.
- **Who triggers:** CompilationPipeline. It generates C_OrderLines (if no user
  DSL specs) and ESLines on the fly, saves them, then proceeds to compilation.

### 11.2 EXPLODE writes C_OrderLines to output.db

EXPLODE-generated C_OrderLines are **transactional instance data** — they go to
`output.db`, not BOM.db. BOM.db holds only the user's pre-existing design-time
specs. The compiler generates new C_OrderLines during EXPLODE walk (one per
BomCategoryLine slot found) and writes them alongside CO_EmptySpaceLines.

- **BOM.db c_orderline** = what the user specified (design-time, editable)
- **output.db c_orderline** = what the compiler decided (compile-time, generated)
- The 61 DX furniture c_orderlines currently in BOM.db are the EXPLODE scenario's
  output, not hand-crafted input. In EN-BLOC (singularity), DX has ONE top
  orderline — the first basic regression test ("can this plane take off?").

### 11.3 CO_EmptySpaceLine = WHERE (measurement), C_OrderLine = WHAT (intent)

Clean separation confirmed:
- **C_OrderLine** = "I want a living room set in this room" (WHAT the user ordered)
- **CO_EmptySpaceLine** = "this BOM box sits at (x,y,z) facing north" (WHERE it goes)
- **L2 ESLines** = available room space (design-time AABB from ad_room_boundary),
  not occupied space. Buffer filler concept: habitable rooms must have explicit
  empty space. Furniture cannot be crammed; items are arranged in corners or
  evenly central.

### 11.4 Rosetta Stone: zero tolerance, filter required

Deterministic DAG compiler. First principle. No relaxation of digest comparison.

The 123 vs 56 gap (ST_SH vs SH) is because ST_SH generates phantom/stub/prop
elements that SH's extraction does not include. The extracted DB is faithful to
the IFC — those 56 are the real visible elements. The 67 extras are compilation
artifacts (structural stubs, PHANTOM buffers, props).

**Action:** Investigate which element classes comprise the 67 extras. Build a
digest filter that compares only geometry-bearing visible elements. Both sides
must hash the same classes. This is prerequisite for the Rosetta Stone gate.

### 11.5 BomCategory = universal semantic dictionary

Not just rooms. **Everything** gets a BomCategory passport. Walls, slabs, MEP
runs, roof assemblies, openings — if it exists in component_library.db, it has
a BomCategory definition. Without one, the construct does not exist in the
compiler's vocabulary.

Vision: Semantic IFC/BIM. Like XML to HTML — adding structure and meaning to
raw geometry. The library will have many components, all with their semantic
"passports" in BomCategory.

### 11.6 TB-LKTN: rule-following, enrichable

No Rosetta Stone possible (no reference IFC). Acceptance = architect expectations
against 2D grid layout + UBBL rules. The expected_elements count (currently 139)
is enrichable as more components are added to the library.

**INVENTION STOP** is part of the BIM development cycle: team populates library
with LOD meshes and M_Product entries. Compiler halts with explicit error on
missing component. User creates mesh (Mesh2Library), links to M_Product, re-runs.
May need BIM COBOL 2D verbs for layout compliance.

### 11.7 VerbStage: direct integration, evolving language

BIM COBOL should replace hardcoded MEP placement (placeMEPSprinklers, placeHVAC,
placeElectrical) — COBOL over assembler. It is part of the compilation pipeline,
not an external tool. The language evolves continuously like component_library —
team work.

**Integration pattern needed:** Break circular dependency (DAGCompiler cannot
depend on BIM_COBOL) while allowing direct execution. SPI/plugin pattern or
verb interface in DAGCompiler with BIM_COBOL as runtime provider. TBD.

### 11.8 Terminal: third Rosetta Stone, same pipeline

Already fully extracted. Same pipeline handles 50K commercial + 56 element house.
BOM entries either extracted from Terminal IFC or hand-crafted — similar process
to SH/DX. The component_library already has all Terminal components (constant,
fixed). The work is BOM modelling — defining the semantic relationships.

Expect multiple BOMs: Hall, seating arrangement, restrooms, stack of floors,
dome roof, walls with awnings. Perhaps a separate Terminal_BOM.db. Bottom-up
grouping from IFC spatial proximity is the natural starting point since the
IFC already has the truth.

---

## Cross-references

- **METADATA_DRIVEN_ARCHITECTURE.md** — domain architecture, phase roadmap, abstract compilation engine vision
- **BIMasBOMConcept.md** — the three-dimension model (Category + Owner + SpaceSize)
- **PREFAB_ARCHITECTURE.md** — 6-level assembly hierarchy + MRP BOM Drop chain
- **RELATIONAL_PLACEMENT_SPEC.md** — C_OrderLine placement rules
