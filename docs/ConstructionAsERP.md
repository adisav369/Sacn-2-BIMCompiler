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
| `m_bom` | M_Product + M_BOM | Assembly definition: BOMCategory (WHAT), C_BPartner (WHO) |
| `m_bom_line` | M_BOM_Line | Child placement: dx/dy/dz, rotation_rule, locator_ref, SpaceSize |
| `m_attribute` | M_Attribute | Leaf attributes: ports, clearances, UBBL rules |
| `M_BomCategory` | M_Product_Category | Functional type: LI, BD, KT, FR, ST, L1, L2, UN |

**What it is:** An assembly manual. "A Duplex Unit contains Level 1 + Level 2.
Level 1 contains Living Room + Kitchen + Bathroom. Living Room contains Piano +
Sofa Set + Buffer Space." Every construct carries its AABB so the parent=SUM(children)
invariant holds.

**Buffer space (BOMCategory='ST') is part of the BOM construct.** Buffer children
are explicit M_BOM_Lines in BOM.db — not computed at compile time, not inferred from
gaps. They exist as named M_BOM_Line records with variable SpaceSize. Without them
the parent's AABB cannot equal the sum of its children. The BOM is incomplete
without its buffers, just as a bill of materials is incomplete without its spacers.

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

**Table prefix rule — never use `ad_` for construction models:**

| Prefix | Domain | Database | Examples |
|--------|--------|----------|----------|
| `ad_*` | Application Dictionary — system config, product catalog, placement rules | component_library.db | ad_product_dim, ad_element_rule, ad_building_registry |
| `m_*` | Master data — BOM assembly recipes, attributes, categories | BOM.db | m_bom, m_bom_line, m_attribute, M_BomCategory |
| `co_*` | Construction output — compiled spatial resolution | output.db | co_empty_space, co_empty_space_line |

The `ad_` prefix is iDempiere's system dictionary namespace. Using it for working
construction data (BOM trees, spatial output) conflates configuration with runtime
state. Historical mistake (`ad_bom`, `ad_bom_child`, `ad_bom_child_param`) corrected
in the BOM Dimension migration to `m_bom`, `m_bom_line`, `m_attribute`.

---

## 2. C_Order — the Construction Order

The building project IS a C_Order. Not BIM, not DSL — **C_Order** directly.

```
C_Order (= ad_building_registry)
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
├── Tab: C_OrderLine (= ad_element_rule)
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
    │   FK: C_Order_ID → ad_building_registry
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

| Concern | iDempiere | BIM |
|---------|-----------|-----|
| "I want to build a Duplex" | Raise C_Order | INSERT ad_building_registry |
| "Use DX vendor's catalog" | Set C_BPartner | SET C_BPartner = 'DX' |
| "How big is the site?" | Set dimensions | SET aabb_width/depth/height_mm |
| "Include this BOM" | Add C_OrderLine | INSERT ad_element_rule |
| "What fits where?" | Check WMS availability | Query CO_EmptySpace/Line |
| "Build it" | Process Order | `./scripts/run_tests.sh` (compile) |
| "Edit the spec" | Modify C_OrderLine | UPDATE ad_element_rule |

**The simplest possible building definition is two fields on C_Order:**
`C_BPartner` (WHO) + `AABB` (HOW BIG). Every downstream decision cascades from
these. A C_Order with only these two fields populated is sufficient to compile —
the BOM explosion engine selects the right UNIT, the right floors, the right
rooms, the right furniture, all from `BOMCategory + SpaceSize ≤ AABB`.

### 2.2 C_OrderLine — what gets built

Each C_OrderLine (ad_element_rule) selects an M_BOM from BOM.db:

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

**Distinction:** The C_Order's AABB (`ad_building_registry.aabb_*_mm`) is the
**pre-compile input** — "I want to build in this envelope." CO_EmptySpace's AABB
is the **post-compile output** — "the compiler produced elements filling this
envelope." For owner-matched builds (SH/DX), these are identical. For ST-mode
builds, the output AABB may be smaller than the input (not all space consumed).

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

A new CO_EmptySpaceLine record is created only at a **decision point** — when the
BOM construct requires spatial guidance to fit into the available space. For 1:1
extracted buildings (SH, DX), there may be as few as ONE record accepting the
top-level BOM. For variant-driven buildings (TB-LKTN), there may be many records —
one per selection/conflict resolution.

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
- **mep_ref** — MEP connection point. Separate from the BOM leaf item.
  The BOM leaf stays pure product data; the MEP spatial reference is a
  construction concern tracked on the EmptySpaceLine.

### 3.5 Translation to Output DB — When Coordinate Work Happens

The BOM tab on C_OrderLine holds the WHAT — all children, buffers, SpaceSize, intact
from BOM.db. CO_EmptySpaceLine holds the WHERE — alignment and orientation in
construction space. **The actual coordinate translation happens when the compiler
writes to the output DB** (elements_meta, element_instances) for Blender viewport
or IFC export:

```
BOM.db construct (abstract)
  M_BOM: LIVING_SET
    M_BOM_Line: Piano     dx=0  dy=0   rotation_rule=PARALLEL_TO_WALL
    M_BOM_Line: Sofa      dx=1500 dy=0 rotation_rule=FACE_INTO_ROOM
    M_BOM_Line: Buffer_NW  (variable, fills remainder)

CO_EmptySpaceLine (alignment)
  Line #7: LIVING_SET box → origin=(208, -5246, 0)  orient=π  (north wall room)

Translation to output DB (concrete world coordinates)
  Piano:      world_xyz = origin + rotated(dx=0, dy=0)     = (208, -5246, 0)     orient=π
  Sofa:       world_xyz = origin + rotated(dx=1500, dy=0)  = (-1292, -5246, 0)   orient=π
  Buffer_NW:  (no geometry — spatial placeholder, but space accounted for)
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
**two fields** on the C_Order (`ad_building_registry`):

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
| `C_BPartner='ST'` | `ad_building_registry` (C_Order) | **Standard mode** — generic, owner-agnostic construction |
| `BOMCategory='ST'` | `M_BomCategory` (BOM.db) | **Buffer/spacer** — empty space child within a BOM assembly |

Different concepts, same abbreviation. `C_BPartner='ST'` is a compilation mode.
`BOMCategory='ST'` is a spatial placeholder. They coexist: an ST-mode
compilation will encounter ST-category buffer children during BOM explosion.

#### 3.7.1 Implementation Gaps (TODO)

Seven concrete gaps between the current compiler and full ST mode:

**TODO-ST-1: Add AABB to `ad_building_registry`** — CONFIRMED ARCHITECTURAL DECISION

The AABB on C_Order IS the governing definition of a building. The simplest
possible construction order: WHO (C_BPartner) + HOW BIG (AABB). Everything
else cascades.

- **Gap:** C_Order has NO pre-compile AABB dimensions. Currently computed
  POST-compile from `elements_rtree`.
- **Fix:** `ALTER TABLE ad_building_registry ADD COLUMN aabb_width_mm REAL;
  ...aabb_depth_mm; ...aabb_height_mm`
- **Seed:** For SH/DX/TB/TE, backfill from existing compiled output R*Tree.
  For new ST buildings, user-provided.
- **File:** `CompilationPipeline.java` — change AABB source from R*Tree to
  registry for ST mode.
- **Migration:** New script `migration/migration_st_aabb_registry.sql`

**Code/model impact** (see full list at end of §3.7.1):

**TODO-ST-2: ST `C_BPartner` selection logic**

- **Gap:** Current query `C_BPartner = ? AND BOMCategory = 'UN'` finds nothing
  for `'ST'` because no BOM rows have `C_BPartner='ST'`.
- **Fix:** When `C_BPartner='ST'`, fall back to:
  `BOMCategory = 'UN' AND C_BPartner IS NULL AND space_width_mm <= ?
  AND space_depth_mm <= ? ORDER BY (space_width_mm * space_depth_mm) DESC LIMIT 1`
- **Decision needed:** Should ST see ALL BOMs (including owner-specific) or only
  NULL-owner shared BOMs?
- **POC approach:** Create NULL-owner copies of UNIT_SH_STD / UNIT_DUPLEX_STD,
  OR relax the query to include all owners.
- **File:** `CompilationPipeline.java`

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

**TODO-ST-5: SpaceSize-based BOM variant selection**

- **Gap:** `findNextFitSpace()` exists only as pseudo-code in Appendix A.5.
  Not implemented.
- **Fix:** Query `m_bom JOIN m_bom_line` where SpaceSize fits available AABB,
  select largest fit.
- **Note:** SpaceSize columns are on `m_bom_line` (child-level), not `m_bom`
  (parent-level). Need parent AABB either as computed aggregate or dedicated
  columns on `m_bom`.
- **File:** New method in `CompilationPipeline.java` or new `SpaceFitSelector.java`

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

- **Gap:** `ad_product_dim` has width/depth/height but no up-vector,
  front-vector, alignment-to-host.
- **Assessment:** NOT a schema gap. Rotation resolves from `rotation_rule`
  (`m_bom_line`) + semantic rules (`m_attribute`). For ST mode, these must be
  present on ALL BOM children — no owner-specific defaults allowed.
- **Fix:** Documentation only — document the invariant that every `m_bom_line`
  must have a resolvable `rotation_rule` for owner-agnostic mode.

#### 3.7.2 AABB on C_Order — Code/Model Impact Inventory

Adding `aabb_width_mm`, `aabb_depth_mm`, `aabb_height_mm` to `ad_building_registry`
touches every layer that reads the registry. Full impact list:

**Schema (1 migration script):**

| Change | File |
|--------|------|
| `ALTER TABLE ad_building_registry ADD COLUMN aabb_width_mm REAL` (×3) | `migration/migration_st_aabb_registry.sql` |
| Backfill from compiled output: `UPDATE ... SET aabb_width_mm = (SELECT aabb_width_mm FROM co_empty_space WHERE c_order_id = building_id)` | Same migration |

**PO classes (2 modules, 2 files each = 4 files):**

| File | Changes |
|------|---------|
| `ORMSandbox/.../po/X_AdBuildingRegistry.java` | +3 COLUMNNAME constants, +3 getters, +3 setters |
| `ORMSandbox/.../po/M_AdBuildingRegistry.java` | Inherit new accessors (no logic change) |
| `TopologyMaker/.../po/X_AdBuildingRegistry.java` | Same 3+3+3 |
| `TopologyMaker/.../po/M_AdBuildingRegistry.java` | Inherit |

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

---

## 4. BOM Explosion Process

### 4.1 The trigger

User raises a C_Order (ad_building_registry) with `C_BPartner = 'DX'`.
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
  CO_EmptySpace: AABB = 8383×17384×6000mm, is_available = Y

After explosion + translation to output DB (normal mode):
  CO_EmptySpaceLine #1: UNIT_DUPLEX_STD  level=0  box aligned to full AABB, orient=0
  (all children translate deterministically — no further lines needed)

After tests GREEN:
  CO_EmptySpace: is_available = N  (confirmed: space consumed, output proven correct)

After tests FAIL (or reprocess):
  CO_EmptySpace: is_available = Y  (space not confirmed — build needs attention)
```

**Why only one line?** At every BOM level there is exactly ONE candidate:
- One UNIT_DUPLEX_STD → no variant to compare
- One FLOOR_SLAB_GF, one FLOOR_SLAB_L2 → no slab variant
- One FLOOR_DX_L1_STD, one FLOOR_DX_L2_STD → no peer conflict
- One ROOF_ASSEMBLY → no roof variant
- One LIVING_SET for Rm_Living_1 → no space-fit decision
- One Piano, one Sofa → no alternative

The BOM tree unfolds deterministically. The translation from BOM.db's abstract
offsets (dx/dy/dz, rotation_rule) to construction coordinates is a pure function
of the single accepted top-level BOM. No branching, no fallthrough, no iteration.

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
| MEP connections | `mep_ref` | Riser point, pipe junction coordinates |
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
  Verify: Every leaf product_ref → valid ad_product_dim

Layer 3: component_library.db (ad_product_dim width/depth/height)
  Intrinsic product geometry in meters.
  Verify: Dimensions match extracted IFC bounding boxes.

Layer 4: ad_building_registry (C_Order: C_BPartner + AABB)
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
│ m_bom                   │  Assembly: BOMCategory + C_BPartner + SpaceSize
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
│ C_Order                 │  ad_building_registry (C_BPartner scopes M_BOM access)
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
 1. User:     INSERT ad_building_registry (C_Order) with C_BPartner='DX'
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
| `FLOOR_SH_GF_STD` | L1 | SH | SH Ground Floor |
| `FLOOR_TBLKTN_GF_STD` | L1 | TB | TB-LKTN Ground Floor |

A C_Order with `C_BPartner='DX'` sees `UNIT_DUPLEX_STD` and its descendants.
A C_Order with `C_BPartner='TB'` sees `UNIT_TBLKTN_STD` — and can also
see generic BOMs (C_BPartner IS NULL) like `TOILET_BLOCK_FIXTURES`.

---

## Appendix A — Code Advice

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
    //     AND space_width_mm  <= ?
    //     AND space_depth_mm  <= ?
    //     AND space_height_mm <= ?
    //   ORDER BY (space_width_mm * space_depth_mm * space_height_mm) DESC
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
        // 1. Read building footprint from ad_building_registry
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
        // 1. For each C_OrderLine (ad_element_rule) with family_ref:
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
| `co_empty_space` | **NEW** | EmptySpaceStage + ValidateStage |
| `co_empty_space_line` | **NEW** | CompileStage (resolver decision points) |

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
| 4 | `C_BPartner` column on ad_building_registry | **DONE** |
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

The data model is complete. The compiler pipeline works end-to-end for
owner-matched builds (170 PASS / 1 intentional RED / 3 SKIP). Remaining
work targets ST mode (owner-agnostic compilation). See §3.7.1 for the
7 concrete TODO items (TODO-ST-1 through TODO-ST-7).

Summary of pipeline gaps for ST mode:

| Gap | What | Phase |
|-----|------|-------|
| C_Order AABB columns | `ad_building_registry` needs `aabb_*_mm` | TODO-ST-1 (§3.7.2 has full impact inventory) |
| ST C_BPartner selection | Query fallback when `C_BPartner='ST'` | TODO-ST-2 |
| CO_EmptySpaceLine L2–L3 | Room-level + item-level spatial records | TODO-ST-3 |
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
- `X_M_BomCategory` / `MBomCategory` — functional type lookup (Table_Name = "M_BomCategory")
- `X_CO_EmptySpace` / `M_CO_EmptySpace` — construction site header
- `X_CO_EmptySpaceLine` / `M_CO_EmptySpaceLine` — spatial alignment record

Pattern: `docs/DEVELOPER_GUIDE.md` — DAO Pattern section.
Working example: `BOMTierResolver.resolveForRoom()` (Phase G-1).

---

## Cross-references

- **METADATA_DRIVEN_ARCHITECTURE.md** — domain architecture, phase roadmap, abstract compilation engine vision
- **BIMasBOMConcept.md** — the three-dimension model (Category + Owner + SpaceSize)
- **PREFAB_ARCHITECTURE.md** — 6-level assembly hierarchy + MRP BOM Drop chain
- **TheLocatorBIMConcept.md** — Locator/GPD walk mechanics
- **RELATIONAL_PLACEMENT_SPEC.md** — ad_element_rule (C_OrderLine) placement rules
