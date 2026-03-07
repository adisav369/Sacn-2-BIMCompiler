# BOM-Based Compilation — The Compiled Construction Method

*Deterministic reproduction of known buildings from committed BOM gospel*

> **This document describes the author's original contribution:** a compiler that
> learns from known buildings and reproduces them deterministically. Not an authoring
> tool. Not rule-based AI. A faithful reproducer driven by committed data.

---

## 1. What This Is

This project is a **compiler**, not an authoring tool. The distinction matters:

- **Authoring tool** (Revit, ArchiCAD): the user draws geometry, the software records it.
- **This compiler**: reference buildings are extracted, committed as BOM data, and
  reproduced deterministically from that data. No drawing. No invention.

The input is a set of **Rosetta Stone** buildings — real IFC models that have been
decomposed into a BOM (Bill of Materials) dictionary. The output is a compiled
SQLite database containing every element at its exact 3D position, traceable back
to the reference source.

**Every element the compiler produces must trace to a real IFC source.** If an
element cannot be traced, the extraction-to-compilation chain is broken and the
output is invalid. This is the first principle. (See [ConstructionAsERP.md §11.30](ConstructionAsERP.md).)

---

## 2. The Gospel Principle

Reference buildings are treated as **gospel** — authoritative, immutable truth.

Why? Because creating accurate BIM geometry from scratch (in Blender, a viewport,
or any modeller) without exact source data is impractical. Real IFC models contain
thousands of precisely dimensioned, precisely placed elements authored by
professional architects and engineers. Reproducing that fidelity by hand is
neither feasible nor desirable.

The Rosetta Stones ARE the training data. The cycle is:

```
Extract  →  Commit  →  Reproduce  →  Verify
  │            │            │            │
  │            │            │            └── SpatialDigest: output = input proof
  │            │            └── Compiler reads BOM.db + component_library.db
  │            └── BOM.db = the committed gospel (read-only dictionary)
  └── Python extraction from IFC → component_library.db + BOM.db
```

**BOM.db is a pure dictionary** — a model dictionary, not a transactional store.
It is never written to during compilation. It defines assembly recipes
(m_bom + m_bom_line), product dimensions (M_Product), building type
configuration (C_DocType), and spatial rules — all extracted from reference
buildings and curated as immutable data. C_Order and C_OrderLine are compile-time
output that lives in output.db, never in BOM.db. Nothing in the compilation
pipeline contaminates the dictionary.

Each finished product LOD (Level of Detail) is stored with proper semantics in
component_library.db so that future users need not recreate it. It is like
inventing a new screw design and keeping it in case another similar spec comes
along. The library grows monotonically — components are added, never removed.

For the full 3-DB architecture (BOM.db, component_library.db, output.db), see
[ConstructionAsERP.md §1](ConstructionAsERP.md).

---

## 3. Two Compilation Modes

The compiler has exactly two modes of operation. The choice between them is a
mathematical result, not a configuration option.

### 3.1 EN-BLOC (Singularity)

When the compiler's selection cascade narrows to **exactly one BOM**, the result
is a mathematical singularity — the answer is unique, so the compiler takes it
whole. One C_OrderLine in the output, referencing the entire BOM tree.

**How it works:**

1. DocSubType + AABB narrows the BOM catalog to candidates
2. DSL specs (room type, constraints) further narrow
3. If exactly one remains → **singularity** → EN-BLOC

`CompilationPipeline` triggers the decision. It generates C_OrderLines (if no
further user specs) and CO_EmptySpaceLines on the fly, saves them, then proceeds
to compilation. Since we currently only have SH and DX as complete BOM stacks,
they fit perfectly and singularity occurs — this is the basic "can this plane
even take off?" regression test.

The compiler copies the entire BOM tree as-is — like transporting a prefab room
to the right spot facing correctly. Individual element positions are computed from
parent-relative offsets (m_bom_line dx/dy/dz per tack convention §3.4) plus the
ESLine origin (tack_from), and written to elements_meta. EN-BLOC means **total
trust**: if the BOM layout is wrong, that is a BOM data error (GIGO somewhere in
the extraction chain), not a compilation bug. The digest verifies by checking
output.db element positions directly.

**Example:** SH living room. DocSubType='SH' + room AABB → one matching
LIVING_SET BOM → taken wholesale. Piano, sofa, side tables — all placed from
BOM offsets. One C_OrderLine, sparse CO_EmptySpaceLine (one per structural tier).

**Same AABB, different DocSubType = EXPLODE.** ST_SH has SH's AABB but
DocSubType='ST' — no match in the SH BOM scope, so the compiler walks.

**Bonsai GUI interaction (future):** In the GUI, a singularity produces one
orderline. The user can click on any part of the en-bloc model and modify it —
add a bath, add a balcony, increase room size — each edit results in more
orderlines as they are DSL decisions. CO_EmptySpaceLines are generated with
default positioning spots that the user can also edit. During the pure IFC era,
everything was flat-coordinate positioned. Here we introduce **design semantics**
— relationships replace raw coordinates.

As the catalog grows, multiple BOMs will share the same AABB. Richer DSL input
from C_OrderLines becomes the discriminant. The Bonsai GUI user always has final
say by editing Orders/Lines. The compiler proposes; the user disposes.
(See [ConstructionAsERP.md §11.1](ConstructionAsERP.md).)

### 3.2 EXPLODE (Progressive Stacking)

When no single BOM matches, the compiler walks `M_BomCategoryLine` slots in
sequence, fitting the best candidate into each slot.

**How it works:**

1. Load the template's M_BomCategoryLine children (ordered by Sequence)
2. For each slot: find best-fit BOM via selection cascade (below)
3. Place it, advance cursor by its AABB
4. Generate one C_OrderLine per slot used

Dense CO_EmptySpaceLine — one per slot consumed. EXPLODE-generated C_OrderLines
are transactional instance data written to output.db.

**Room-level EXPLODE example — furniture priority:**

Consider a TB-LKTN living room whose AABB does not match any parent BOM in
BOM.db. The compiler spawns separate orderlines for each furniture set, placed
in priority order defined by `M_BomCategoryLine.Sequence` (user-defined defaults):

1. **Dining set** — first priority. ESLine assigns a good central or best spot.
2. **Sofa set** — second. ESLine provides a calculated remaining spot that
   won't clash with the dining set's AABB.
3. **Piano** — third. If it can't fit, try other sets; ESLine gives the
   remaining non-clashing position.

Each set gets its own C_OrderLine and CO_EmptySpaceLine. The buffer filler
concept applies: habitable rooms must have explicit empty space. Furniture
cannot be crammed in — items are arranged in corners or evenly central. If
the arrangement is satisfactory, it can be committed as a new BOM to BOM.db
(with buffer fillers added between items), becoming available for future
EN-BLOC singularity matching.

**Contrast with EN-BLOC:** In SH, the living room's parent AABB fits exactly,
so the compiler takes all contents wholesale — dining, sofa, piano sets with
their buffer fillers. One orderline, one ESLine at room level. In TB-LKTN,
no fit → three orderlines, three ESLines, progressive fill.

**BOMCategory is the EXPLODE driver.** When DocSubType has no matching
building BOM (e.g. ST mode — a standard template, not a specific SH or DX),
BomTemplateComposer walks `M_BomCategoryLine` to determine WHAT rooms the
building needs, their layering, and which BOMs to source for each slot. This
is the full EXPLODE mechanism: M_BomCategoryLine provides the structural
grammar (slots), the selection cascade (§3.3) fills each slot with the
best-fit BOM, and C_OrderLines are generated per slot consumed.

For the HelloWorld POC (SH, DX), the exploded (_e) path walks pre-built
structured UNIT BOMs directly — the hierarchy already exists, so
BomTemplateComposer is bypassed. This is by design: the POC proves the
BOMWalker produces correct output from a known hierarchy. When the same
walker is driven by BomTemplateComposer-generated selections (ST mode), it
produces the same elements through the same code path — the only difference
is how the root BOMs were chosen.

(See [ConstructionAsERP.md §11.2](ConstructionAsERP.md) for the
M_BomCategoryLine → C_OrderLine generation mechanism.)

### 3.3 Selection Cascade

Two fields drive everything: **DocSubType** and **AABB**. A third —
**BomCategory** — scopes the search to the correct functional domain.

The cascade for selecting a BOM to fill a slot:

1. **BomCategory** (scope): restricts candidates to the correct functional type.
   BomCategory codes are analogous to UPC/EAN material management codes — they
   qualify a wall with openings, a fitting category as 'curtains', its priority
   or prerequisites. This prevents cross-domain matches: an AABB that fits a
   vehicle will not match a residential living room because categories differ.
2. **AABB fit** (primary): SpaceSize must fit within the slot's allocated AABB
3. **Largest volume** (secondary): maximize space usage among fitting candidates
4. **seq_no** (tiebreaker): lower preferred. Owner-specific BOMs (seq_no=10)
   naturally win over generic BOMs (seq_no=20)

AABB comparison is **3D exact** — no tolerance. This is an engineering field
where precision cannot be second-guessed. If dimensions don't match exactly,
that is a data error in the extraction-to-BOM chain, not an ambiguity to resolve.
The flow is clear: `IFC → component_library.db (extraction) → BOM.db (exact AABB
copy) → output.db`. If any step introduces drift, that step is broken — investigate
and fix. High precision is the hallmark.
(See [ConstructionAsERP.md §11.25, §11.31](ConstructionAsERP.md).)

### 3.4 Tack Convention — BOM Spatial Handshake

Every BOM and every element has a **tack point**: the Left-Front-Down corner of
its bounding box. This is (0, 0, 0) in the element's own coordinate frame.

- **Left** = X minimum
- **Front** = Y minimum
- **Up** = Z positive (Down = Z minimum)

All dx/dy/dz offsets in m_bom_line are measured from the parent's tack point to
the child's tack point. Both are the Left-Front-Down corner of their respective
bounding boxes. All values are positive — a child cannot be behind its parent's
origin.

**tack_to / tack_from (Lego principle):** At every BOM level, two connection
points define how pieces join:

- **tack_to** — "I attach to my parent at this point on myself" (the child's
  anchor, like a Lego tube underneath)
- **tack_from** — "my children attach to me at these points" (the parent's
  slots, like Lego studs on top)

The ESLine provides the world-space position of the tack_from slot. The BOM
child's tack_to meets it. This handshake is uniform at every level: building
on site, storey in building, room in storey, element in room.

For EXTRACTED buildings, the tack point is computed once at extraction time by
subtracting the building's AABB minimum corner from all element centroids. For
generative buildings, the designer declares it. Same mechanism, same columns.

**Test:** any dx < 0 or dy < 0 or dz < 0 in m_bom_line = broken tack = REJECT.

#### Implementation

**m_bom (parent BOM):** add `origin_x`, `origin_y`, `origin_z` (REAL, metres).
The world-space position of this BOM's tack point (Left-Front-Down corner).
For EXTRACTED BOMs, computed once from `MIN(min_x)`, `MIN(min_y)`, `MIN(min_z)`
of all child elements. For generative BOMs, set by the designer or defaulted
to (0, 0, 0). This is the factor that transforms parent-relative ↔ world.

**m_bom_line (child offset):** dx/dy/dz are parent-relative (already exist).
After the origin fix, all values ≥ 0. Enforced by `X_M_BOMLine.setDx()` which
rejects negative values at the Java PO layer.

**Extraction fix** (one-time migration on BOM.db):

```sql
-- Compute origin per BOM (Left-Front-Down corner of all children)
-- Then subtract it from every child's centroid
WITH origins AS (
    SELECT bom_id,
           MIN(dx - allocated_width_mm/2000.0) AS ox,
           MIN(dy - allocated_depth_mm/2000.0) AS oy,
           MIN(dz - allocated_height_mm/2000.0) AS oz
    FROM m_bom_line
    WHERE bom_id IN ('EXT_SH','EXT_DX') AND is_active = 1
    GROUP BY bom_id
)
UPDATE m_bom_line SET
    dx = dx - (SELECT ox FROM origins WHERE origins.bom_id = m_bom_line.bom_id),
    dy = dy - (SELECT oy FROM origins WHERE origins.bom_id = m_bom_line.bom_id),
    dz = dz - (SELECT oz FROM origins WHERE origins.bom_id = m_bom_line.bom_id)
WHERE bom_id IN ('EXT_SH','EXT_DX') AND is_active = 1;

-- Store origin on m_bom for PlacementLoader to add back at emit time
UPDATE m_bom SET origin_x = ..., origin_y = ..., origin_z = ...
WHERE bom_id IN ('EXT_SH','EXT_DX');
```

**Compilation (PlacementLoader.loadFromBOM):**

```
world_min_x = origin_x + (dx - allocated_width_mm / 2000.0)
world_max_x = origin_x + (dx + allocated_width_mm / 2000.0)
```

The BOM stores building instructions. The origin translates to world at emit
time. The origin lives on m_bom (the parent), not on each child line.

**Future (multi-level):** nested BOMs accumulate origins. A room BOM's origin
is relative to its storey. A storey BOM's origin is relative to the building.
The compiler walks the tree, summing origins: `world = building_origin +
storey_origin + room_origin + element_offset`. Same tack handshake at every
level.

---

## 4. The 9-Stage Pipeline

The compiler runs as a deterministic DAG (directed acyclic graph) of stages.
Every stage reads from the two source databases (BOM.db + component_library.db)
via JDBC. No stage invents values.

| # | Stage | Class | What it does |
|---|-------|-------|-------------|
| 1 | **Metadata** | `MetadataValidator` | Referential integrity checks against BOM.db (building grid, room boundaries, wall faces) |
| 2 | **Parse** | `ParseStage` | Reads `.bim` DSL text into `BuildingDefinition` records |
| 3 | **Compile** | `CompileStage` | `BuildingCompiler` + `StoreyCompiler` produce `BuildingSpec` (room geometries, walls, openings per storey). Multi-unit merging (DX party walls) happens here. |
| 4 | **Template** | `TemplateStage` | ST-mode only: `BomTemplateComposer` walks M_BomCategoryLine to select best-fit BOMs per slot |
| 5 | **Write** | `WriteStage` | `BuildingWriter` emits SQLite output DB. Creates C_Order from C_DocType. Populates CO_EmptySpace (L0/L1/L2). For EXTRACTED buildings: placement via `PlacementLoader` (see §4.1) |
| 6 | **Verb** | `VerbStage` | BIM COBOL script hook — executes verbs → PP_Order_Node. Skipped if no `.bimcobol` file |
| 7 | **Digest** | `DigestStage` | `SpatialDigest` computes per-element SHA256. Updates output.db C_Order with digest + element count + checksum |
| 8 | **Geometry** | `GeometryStage` | `GeometryIntegrityChecker` validates mesh integrity against reference DB |
| 9 | **Prove** | `ProveStage` | `PlacementProver` mathematical proofs gate. Promotes CO_EmptySpace IP→CO (proven) or IP→RE (violated). Skipped for ST mode |

`CompilationPipeline` orchestrates all stages as a typed `CompilerStage` chain.
`BuildingRegistry` reads C_DocType from BOM.db to know which buildings to compile.
Each compilation creates a fresh output.db with C_Order + C_OrderLine + elements +
CO_EmptySpace.

For implementation details, see [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md).

### 4.1 Compiled Building Data Flow — Step by Step

Both singular (_s) and exploded (_e) compilations follow the same data flow.
Element positions are read from m_bom_line in BOM.db — parent-relative offsets
per the tack convention (§3.4). The `bom.mode` system property selects which
BOMs to walk:

- `EXTRACTED` (default): walks flat EXT_SH / EXT_DX BOMs — EN-BLOC singular compilation
- `STRUCTURED`: walks hierarchical UNIT_SH_STD / UNIT_DUPLEX_STD BOMs — exploded compilation

Both modes use the same BOMWalker + PlacementCollectorVisitor code — only the
root BOM selection differs. Both produce compiled output DBs.

```
BOM.db                                          output.db
  m_bom (origin_x/y/z)                           elements_meta
  m_bom_line (dx/dy/dz, allocated_*_mm)           elements_rtree
       │                                          element_instances
       ▼                                          base_geometries
  BOMWalker.walkSelf(bomId)
       │
       ▼
  PlacementCollectorVisitor
       │  onMake: push anchor (parent + offset + child origin)
       │  onBuy:  emit Placement (anchor + offset ± half-extents)
       ▼
  PlacementLoader.cache["Ifc4_SampleHouse"] = List<Placement>[55]
       │
       ▼
  StoreyCompiler.applyPlacementOverrides()
       │  clears compiled walls/doors/windows for this storey
       │  marks consumed: PlacementLoader.markConsumed(building, elementRef)
       ▼
  BuildingWriter.emitGlobalPlacementElements()
       │  for each Placement:
       │    if isConsumed → skip (already cleared by RELATIONAL path)
       │    else → MeshBinder.bind(placement) → writeBoundElement()
       ▼
  output.db written
```

#### Step 1: PlacementLoader.loadFromBOM()

Called once at pipeline start. Reads `bom.mode` system property. Fetches root BOMs:

| bom.mode   | BOM source | Root selection |
|------------|-----------|----------------|
| EXTRACTED  | `MBOM.getByCategory("EXTRACTED")` | EXT_SH, EXT_DX — flat, all BUY |
| STRUCTURED | `MBOM.getByCategory("UN")` | UNIT_SH_STD, UNIT_DUPLEX_STD — hierarchical |

For each root BOM, looks up `C_DocType.ProjectName` via `doc_sub_type` to get the
building type string (e.g., "Ifc4_SampleHouse"). Creates a `PlacementCollectorVisitor`
and calls `BOMWalker.walkSelf(bomId, [visitor], buildingType)`.

#### Step 2: PlacementCollectorVisitor — BOM Walk Events

The visitor receives BOMWalker events and accumulates world coordinates:

**onMake(NodeContext)** — fired when entering a MAKE node (hierarchy level):
1. Read child BOM origin: `m_bom.origin_x/y/z`
2. Read line offset: `m_bom_line.dx/dy/dz` (parent-relative, always ≥ 0)
3. Compute new anchor: `parent_anchor + line_offset + child_origin`
4. Push anchor onto stack
5. If child BOM is FLOOR-level, push storey name onto storey stack

**onBuy(NodeContext)** — fired at each BUY leaf (one per element):
1. Get accumulated world anchor from stack
2. Compute world centre: `cx = anchor[0] + line.dx`, `cy = anchor[1] + line.dy`, `cz = anchor[2] + line.dz`
3. Resolve half-extents from `m_bom_line.allocated_{width,depth,height}_mm` (÷ 2000.0 for metres)
4. Resolve IFC class: `line.role` → `product.ifc_class` → `child_product_id` → `"Unknown"`
5. Resolve storey: `line.storey` → storey stack → `"Unknown"`
6. Resolve material: `line.material_name/rgba` → product fallback
7. Build Placement record with full AABB (centre ± half-extents), add to list

**For EXTRACTED BOMs (flat):** Only one onMake (root) with origin ≈ (0,0,0). Each
onBuy directly uses line dx/dy/dz as the world centre. The tack convention still
applies but the single-level hierarchy makes it trivially `world = origin + offset`.

**For STRUCTURED BOMs (hierarchical):** Multiple onMake calls (UNIT → FLOOR → SET).
Anchors accumulate at each level. onBuy at leaf uses the deepest accumulated anchor.

#### Step 3: Dual-Source Contract — RELATIONAL vs FLAT

EXTRACTED buildings use two emission paths with explicit deduplication:

**RELATIONAL path** (`StoreyCompiler.applyPlacementOverrides`):
- For each storey, fetches metadata placements by building + storey + IFC class
- **Clears** the compiled element lists (walls, doors, windows, columns, etc.)
- **Marks elements consumed** via `PlacementLoader.markConsumed(buildingType, elementRef)`
- Does NOT write elements — only prevents the compiled path from inventing them

**FLAT path** (`BuildingWriter.emitGlobalPlacementElements`):
- Iterates ALL PlacementLoader placements for the building
- **Skips consumed** elements: `if (isConsumed(buildingType, elementRef)) continue`
- For non-consumed elements: calls `MeshBinder.bind(placement)` → writes to output.db
- This is where elements_meta, elements_rtree, element_instances, base_geometries are written

**Deduplication registry:** `PlacementLoader.consumed` = Set of `buildingType + NUL + elementRef`.
Set by RELATIONAL path, checked by FLAT path. Prevents double-write.

**Net result:** Every element is written exactly once. The RELATIONAL path suppresses
compiled parametric geometry. The FLAT path emits BOM-sourced geometry. No element
is written by both paths.

#### Step 4: MeshBinder — Geometry Resolution Chain

For each Placement, `MeshBinder.bind()` resolves library geometry:

1. **Product-level** (canonical): `library.resolveByProduct(placement.productId())`
   → queries `M_Product_Image.geometry_hash` by `M_Product_ID`
   → returns LOD_Object hash for the canonical mesh
2. **Instance-level** (Terminal fallback): `library.resolveGeometryByInstance(...)`
   → queries `I_Geometry_Map` by building + class + storey + ordinal
   → deprecated path, used only for Terminal building (51K elements)
3. **Null** → element excluded from output (logged as warning)

After resolving, MeshBinder:
- Reads the library mesh from `LOD_Object` (vertices, faces, normals)
- Computes scale factors: `scaleX = (placement_width / mesh_width)` etc.
- Validates scales within [0.3, 3.0] (dimensional contract)
- If outside range and `closestFit` enabled: searches for better-fitting mesh
- Transforms mesh (scale → rotate if NS → translate to world position)
- Writes to `base_geometries` + `element_instances` in output.db

#### Step 5: Post-Write Integrity

After element emission, `CompilationPipeline` runs three post-write fixups:
- `normalizeStoreyNames()` — adds missing storeys from elements_meta (Gap #8)
- `emitIfcSpaceFromL2()` — creates IfcSpace records from L2 ESLines (Gap #5)
- `populateSpaceContainment()` — assigns elements to rooms/storeys via centroid-in-AABB (Gap #6)

These are metadata enrichment steps, not coordinate changes. No element positions
are modified after emission.

### 4.2 Drift Risk Inventory

Every value that could be **computed rather than read** is a drift risk. The
following fallback chains exist in the code. For EXTRACTED BOMs, all primary
sources are fully populated (verified by data checks below), so no fallback
fires in practice. But the fallbacks exist and must not silently activate.

| Field | Primary source | Fallback | Risk if fallback fires |
|-------|---------------|----------|----------------------|
| AABB half-extents | `m_bom_line.allocated_{w,d,h}_mm` | `M_Product` intrinsic dims | Wrong dimensions — may differ from IFC |
| IFC class | `m_bom_line.role` (starts with "Ifc") | `product.ifc_class` → `child_product_id` → "Unknown" | Wrong discipline classification |
| Storey | `m_bom_line.storey` | FLOOR ancestor from BOM hierarchy → "Unknown" | Element on wrong storey |
| Material | `m_bom_line.material_name/rgba` | `M_Product.material_*` | Wrong surface appearance |
| Geometry hash | `M_Product_Image.geometry_hash` via `child_product_id` | `I_Geometry_Map` instance lookup (Terminal only) → null | Wrong mesh or element excluded |
| Scale factors | Placement AABB ÷ library mesh AABB | `closestFit` alternative mesh search | Wrong geometry substituted |

**Data integrity checks (all PASS for EXTRACTED BOMs as of 2026-03-07):**

```sql
-- Zero NULL allocated dims (fallback to M_Product would fire)
SELECT COUNT(*) FROM m_bom_line
WHERE bom_id LIKE 'EXT_%' AND is_active=1
  AND (allocated_width_mm IS NULL OR allocated_depth_mm IS NULL
       OR allocated_height_mm IS NULL);
-- Result: 0

-- Zero NULL storeys (fallback to FLOOR ancestor would fire)
SELECT COUNT(*) FROM m_bom_line
WHERE bom_id LIKE 'EXT_%' AND is_active=1 AND storey IS NULL;
-- Result: 0

-- Zero orphan products (MeshBinder.bind would return null)
-- All 96 distinct child_product_id values in EXTRACTED BOMs have
-- corresponding M_Product_Image entries in component_library.db.
-- M_Product_Image: 115 rows (96 EXTRACTED + 19 other).
```

### 4.3 The ABSOLUTE Anti-Pattern

**Bypassing the compilation method — baking coordinates into c_orderline or
elements_meta directly — violates the extraction-to-compilation chain.** This is
the ABSOLUTE anti-pattern. It breaks:

- **Provenance:** element positions can no longer be traced to BOM offsets
- **Determinism:** the same BOM input no longer guarantees the same output
- **Verification:** SpatialDigest comparison becomes meaningless

The `RelationalResolver` was deleted (2026-03-05) because it read stale
c_orderline coordinates — a form of this anti-pattern. Coordinate data must
flow through the compilation method (BOM offsets → tack accumulation →
world coordinates), never around it.

---

## 5. BOM Explosion Chain (Worked Example)

The BOM hierarchy for residential buildings follows five levels:

```
UNIT  →  FLOOR  →  ROOM  →  SET  →  ITEM
```

### SH (SampleHouse) — Structured BOM (Actual State)

The structured hierarchy exists but covers **furniture only** (21 BUY leaves).
The EXTRACTED BOM (EXT_SH) has 55 elements — the 34-element gap is entirely
structural elements (walls, doors, windows, curtain wall, slabs, roof elements)
that were never added to the structured hierarchy.

```
UNIT_SH_STD (UN, doc_sub_type=SH)
├── FLOOR_SH_GF_STD (MAKE, GROUND_FLOOR)
│   ├── SH_LIVING_SET (MAKE, LIVING)
│   │   ├── SOFA_AREA (MAKE, SOFA)
│   │   │   ├── IfcFurniture (BUY, COFFEE_TABLE)    ← generic product ID
│   │   │   ├── IfcFurniture (BUY, SIDE_TABLE_A)    ← generic product ID
│   │   │   └── IfcFurniture (BUY, SIDE_TABLE_B)    ← generic product ID
│   │   ├── IfcFurniture (BUY, SOFA_B)              ← generic product ID
│   │   ├── IfcFurniture (BUY, PIANO)               ← generic product ID
│   │   └── BUFFER × 2 (PHANTOM)
│   ├── SH_DINING_SET (MAKE, DINING)
│   │   ├── IfcFurniture (BUY, TABLE)               ← generic product ID
│   │   ├── IfcFurniture × 6 (BUY, CHAIR_A..F)      ← generic product ID
│   │   └── BUFFER × 2 (PHANTOM)
│   ├── SH_BED_SET (MAKE, MASTER)
│   │   ├── IfcFurniture (BUY, BED)                 ← generic product ID
│   │   ├── IfcFurniture (BUY, DESK)                ← generic product ID
│   │   └── BUFFER (PHANTOM)
│   └── TOILET_BLOCK_FIXTURES (MAKE, BATHROOM)
│       ├── IfcSanitaryTerminal × 5 (BUY)           ← correct IFC class, generic ID
│       ├── IfcFlowTerminal (BUY, EXHAUST_FAN)      ← correct IFC class, generic ID
│       └── BUFFER (PHANTOM)
├── FLOOR_SLAB_GF (MAKE, GROUND_SLAB)               ← empty (no children)
└── ROOF_ASSEMBLY (MAKE, ROOF)
    ├── IfcRoof (BUY)                                ← generic product ID
    ├── ROOF_STRUCTURE (MAKE)                        ← empty (no children)
    └── ROOF_COVERING (MAKE)                         ← empty (no children)
```

**Two gaps prevent exploded (_e) from matching the reference (55 elements):**

1. **Generic product IDs** — BUY leaves use IFC class names ("IfcFurniture",
   "IfcRoof") as `child_product_id` instead of real M_Product entries
   (e.g., "Dining_Chair", "FURN_PIANO"). Without M_Product → M_Product_Image,
   MeshBinder cannot resolve geometry → elements skipped. All 21 furniture
   slots are affected.

2. **Structural layer missing** — no BOM representation for:
   - IfcWall (walls)
   - IfcDoor (doors)
   - IfcWindow (windows)
   - IfcCurtainWall / IfcMember / IfcPlate (curtain wall assembly)
   - IfcSlab (slabs)
   - IfcRailing (railings)
   These 34 elements exist in EXT_SH but have no structured BOM lines.

**Result:** exploded (_e) produces 0 elements for SH (vs 55 in reference).

### DX (Duplex) — Structured BOM (Actual State)

The DX structured hierarchy is more developed (176 BUY leaves) but still
missing 946 elements vs the EXTRACTED BOM (1,099 total).

```
UNIT_DUPLEX_STD (UN, doc_sub_type=DX)
├── FLOOR_DX_L1_STD (MAKE, LEVEL_1)
│   ├── DINING_SET (MAKE) — 7 BUY + 1 PHANTOM
│   │   └── Dining_Table_With_Chairs, Dining_Chair × 6   ← real product IDs
│   ├── TOILET_BLOCK_FIXTURES (MAKE) — shared with SH
│   ├── KITCHEN_CABINET_SET_DX_A (MAKE) — 15 BUY + 1 PHANTOM
│   │   └── Base_Cabinet, Upper_Cabinet, Counter_Top      ← real product IDs
│   └── KITCHEN_CABINET_SET_DX_B (MAKE) — 14 BUY + 1 PHANTOM
├── FLOOR_DX_L2_STD (MAKE, LEVEL_2)
│   ├── WARDROBE_SET (MAKE) — 2 BUY + 1 PHANTOM
│   ├── TOILET_BLOCK_FIXTURES (MAKE) — shared
│   └── KITCHEN_CABINET_SET (MAKE) — 12 BUY + 1 PHANTOM
├── FLOOR_SLAB_GF (MAKE) — empty
├── FLOOR_SLAB_L2 (MAKE) — empty
├── ROOF_ASSEMBLY (MAKE) — shared with SH
└── DUPLEX_SET_STD (MAKE, PAIR)
    └── DUPLEX_SINGLE_UNIT_STD × 2 (MAKE, UNIT_A/B)
        ├── LIVING_SET, DINING_SET, KITCHEN_CABINET_SET (MAKE)
        ├── DUPLEX_BATHROOM_SET × 2, BED_SET, BED_SET_MASTER (MAKE)
        └── WARDROBE_SET (MAKE)
```

DX has a **mix** of real product IDs (Base_Cabinet, Dining_Chair, Counter_Top)
and generic IFC class names (IfcFurniture in bathroom/living sets). The 153
elements that compile in STRUCTURED mode come from the sets with real product IDs.

**Same two gaps apply:**
1. Generic product IDs on some furniture leaves (living, bedroom, bathroom sets)
2. Structural layer entirely missing (walls, doors, windows, slabs, MEP piping)

**Result:** exploded (_e) produces 153 elements for DX (vs 1,099 in reference).

### EXTRACTED BOMs — The Flat Reference

EXTRACTED BOMs (EXT_SH, EXT_DX) are flat — one BUY line per element instance,
no hierarchy. All 55 SH and 1,099 DX elements have:
- Real `child_product_id` → M_Product with M_Product_Image
- Backfilled `allocated_{width,depth,height}_mm` from IFC extraction
- Backfilled `storey`, `element_ref`, `material_name`, `material_rgba`

These BOMs are the **compilation gospel** — they reproduce the reference IFC
at 100% fidelity. The structured BOMs are the **design target** — when the
exploded (_e) compilation matches the same reference, the hierarchy is complete.

**Verification principle:** Both _s and _e must independently match the
reference extracted DB (the input/extraction ground truth). They are never
compared to each other — each is verified against the reference. When both
match the reference, delta between them is zero as a mathematical consequence.
Current gap: SH _e = 0 elements (vs 55 in reference), DX _e = 153 (vs 1,099).

### CO_EmptySpaceLine tracking

CO_EmptySpaceLine is a measurement aid, analogous to SAP's WMS EmptyStorage. It
tags each orderline with "this is where you put it" — keeping the WHERE concern
separate from the WHAT on C_OrderLine.

Each placement writes a `co_empty_space_line` record:
- **EN-BLOC**: sparse — one per structural tier (room-level). The parent AABB
  fits, so the entire room is one placement unit.
- **EXPLODE**: dense — one per slot consumed. Each furniture set gets its own
  ESLine with a calculated position that avoids clashing with previously placed
  items.

Three concerns, three tables:
- **C_OrderLine** = WHAT (intent — "I want a living room set in this room")
- **CO_EmptySpaceLine** = WHERE (spatial measurement — "this BOM box sits at
  (x,y,z) facing north")
- **PP_Order_Node** = HOW (verb operations — production steps targeting ESLines)

---

## 6. Verification: The Rosetta Stone Gate

The governing verification principle: **maths that proves visuals without cheating.**

Visually inspecting compiled buildings (launching Bonsai, loading output, rotating
the viewport) takes countless cycles to spot drift. Instead, the compiler demands
mathematical proof: output = input, 100% GIGO, without cheating. No relaxation of
comparison. No semantic equivalence. **Element identity** — every compiled element
must match its reference counterpart in position, class, and dimensions.

The issues mostly occur at wall/opening/furniture orientation, rotation, and
placement. A BBox vertex hash catches all such drifts: if something broke the
placement, the maths flags it immediately without manual inspection. Another class
of error is non-LOD elements (BBox-only, no material, invented geometry) — the
provenance gate catches those.

MEP is **excluded** from the Rosetta Stone digest. SH has zero MEP elements; the
digest is for structural/furniture visual confirmation only.

### The Six Gates

Implemented in `RosettaStoneGateTest.java`, permanent in Maven surefire stage 2.

| Gate | What it checks | Catches |
|------|---------------|---------|
| **G1-COUNT** | Element count: reference input = compiled output | Missing or invented elements |
| **G2-VOLUME** | Total AABB volume: reference ≈ compiled (±0.1%) | Dimensional drift, scale errors |
| **G3-DIGEST** | Per-element spatial SHA256: reference vs compiled | Position drift, class mismatches, orientation errors |
| **G4-TAMPER** | Self-inspection: git history + source regex (12 rules) | @Disabled tests, stubs, non-determinism, hardcoded coords |
| **G5-PROVENANCE** | Every output element traced to library | Missing material_rgba, orphan geometries, unknown IFC classes |
| **G6-ISOLATION** | Output scoped to building; no cross-contamination | Unused surface styles, missing storeys, no IfcSpace, empty containment |

**G3-DIGEST** is the mathematical proof. It compares elements_meta + RTREE
positions directly between extracted DB and compiled DB. For each element class:
sort by (ifc_class, storey, minX, minY, minZ), hash the sorted BBox vertex
tuples. Any mismatch = compilation error, report which element drifted. For
structural decomposition (IfcWall → column+beam+plate), hash the union BBox of
the composed assembly and compare against the monolithic reference element.

**G4-TAMPER** is extensible via declarative rules — add entries to the
`TAMPER_RULES` list, not code changes. Rules scan both recent git diffs (T1–T5)
and current source files (T6–T12).

**Source:** `DAGCompiler/src/test/java/com/bim/compiler/contract/RosettaStoneGateTest.java`

### First run results (2026-03-04)

```
G1-COUNT      RE_SH PASS  RE_DX PASS  CO_TE FAIL (delta=-4)
G2-VOLUME     RE_SH FAIL (-4.54%)  RE_DX FAIL (-30.69%)  CO_TE PASS
G3-DIGEST     RE_SH FAIL (class name drift)  RE_DX FAIL  CO_TE FAIL
G4-TAMPER     FAIL (11 violations: 1 @Disabled, 2 stubs, 8 TODOs)
G5-PROVENANCE ALL FAIL (material_rgba not propagated to output)
```

These failures were **expected baselines** at first run. As of 2026-03-07,
all 6 gates are GREEN for SH and DX:

```
G1-COUNT      RE_SH PASS (55)  RE_DX PASS (1099)
G2-VOLUME     RE_SH PASS (+0.00%)  RE_DX PASS (+0.00%)
G3-DIGEST     RE_SH PASS  RE_DX PASS
G4-TAMPER     PASS
G5-PROVENANCE RE_SH PASS  RE_DX PASS
G6-ISOLATION  RE_SH PASS  RE_DX PASS
```

The gates exist to prevent regression and track convergence.

### What the gates enforce

- **Rosetta Stone buildings** (SH, DX, Terminal): expected_elements is truth from IFC.
  Fixed. The compiler must match it exactly.
- **GENERATIVE buildings** (ST_SH, TB-LKTN): the compiler determines the count.
  expected_elements is auto-calculated after each successful compilation and
  written to output.db.
- **Drift at Rosetta Stone stage is catastrophic downstream.** If the extraction-
  to-compilation chain allows drift on known buildings, everything built on top
  is unreliable. Fix drift at the source, never tolerate it.

For the detailed Rosetta Stone score history and Terminal recomposition roadmap,
see [TheRosettaStoneStrategy.txt](TheRosettaStoneStrategy.txt).

### Dual Output: _s and _e (run_RosettaStones.sh)

The RosettaStone run stage compiles each building **twice**, producing two
output DBs:

| Suffix | Mode | What it does |
|--------|------|-------------|
| `_s` | Singular (EN-BLOC) | Takes one flat BOM whole — hello-world POC |
| `_e` | Exploded (EXPLODE) | Walks the structured BOM hierarchy |

**Verification:** Each output is compared independently against the reference
extracted DB (the IFC ground truth). They are never compared to each other.
When both match the reference, they are necessarily identical — but that is a
**consequence**, not the test itself. The test is: does this compilation mode
reproduce the reference?

**Why two modes?** Singular is the POC proof — "can this plane even take off?"
It takes a known-correct flat BOM and compiles it. Exploded is the production
target — it walks the hierarchical BOM that will eventually be generated by
BomTemplateComposer (§3.2). When the exploded path matches the reference, the
structured BOM data is proven complete.

**Script:** `scripts/run_RosettaStones.sh` — compiles SH and DX in both modes,
reports per-class element counts, flags any gaps against the reference.

---

## 7. Autonomous Operation — The End State

The compiler must run without human or AI assistance. Given a `.bim` DSL file and
the two source databases, it produces a complete output database — deterministically,
repeatably, verifiably.

**Compilation is batch/process-based**, like COBOL and ERP systems. The user makes
changes (BOM edits, DSL updates, component additions), saves, and the compiler
processes the full pipeline and refreshes results. Not interactive real-time design
— deliberate batch runs with full verification at each cycle.

The evolving framework around the compiler — BIM COBOL verbs, Java pipeline stages,
witness tests, BOM layering — is supporting infrastructure. The compilation method
itself is fixed:

1. Read BOM gospel from BOM.db
2. Read geometry from component_library.db
3. Select BOMs via DocSubType + AABB cascade
4. EN-BLOC or EXPLODE based on singularity
5. Write fresh output.db with full provenance
6. Verify via Rosetta Stone Gate

**Adding a new building = SQL INSERT + BOM data, zero Java.** The building type
goes into C_DocType. The BOM recipes go into m_bom + m_bom_line. The product
dimensions go into M_Product. The compiler handles the rest.

### The BIM Development Cycle

The team must get all components ready in the library before compilation can
succeed. Every wall, mesh, object — each finished product LOD is stored with
proper semantics in component_library.db so that future users need not recreate
it. The `INVENTION STOP` rule means the compiler halts if a required component
is missing from the library. The cycle is:

1. **Extract** LOD meshes from IFC sources (Python extractors)
2. **Curate** BOM recipes linking products into assemblies (SQL in BOM.db)
3. **Compile** — the compiler reads both databases and produces output
4. **Verify** — Rosetta Stone Gate checks output against reference
5. **Fix** — any drift is traced to its source and corrected
6. Repeat until all gates pass

BIM COBOL is the evolving higher-level language — like COBOL replacing assembler.
Architects cannot understand Java code, but they can understand structured
English-like verb commands. The language gradually takes over hardcoded Java
methods, verb by verb, without changing the compilation method.
(See [BIM_COBOL.md](BIM_COBOL.md).)

---

## 8. What This Is Not

| It is NOT | Because |
|-----------|---------|
| Revit / ArchiCAD | Those are authoring tools — the user draws. This compiler reproduces from committed data. |
| Rule-based AI placement | No heuristics, no optimisation, no machine learning. Selection cascade is deterministic. |
| Parametric design | Parameters come from extracted BOM data, not user-driven design exploration. |
| Approximate | AABB matching is 3D exact. Digest verification is SHA256. No tolerance, no "close enough". |
| Interactive | Batch compilation. The Bonsai GUI is for reviewing and editing orders, not for real-time design. |

It **is** deterministic reproduction from committed gospel. Extract → Commit →
Reproduce → Verify. The same input always produces the same output. If it doesn't,
that's a bug.

What it IS becoming: a **Semantic IFC/BIM** — analogous to what XML did to HTML,
or what Tim Berners-Lee's Semantic Web did to the document web. Every component
has a passport (BomCategory), every assembly has a recipe (M_BOM), every
relationship is explicit (m_bom_line with offsets and spatial rules). The flat
coordinate model of traditional IFC is replaced by a relational model where
meaning — not just position — is encoded.

---

## 9. The BIM Designer — Tack-Based Visual Editor

The tack convention (§3.4) completes the placement theory for a visual editor.
Every BOM is a box with a known corner. Every child snaps to a slot in the
parent. The GUI editor becomes a BOM arranger, not an element placer:

**The design cycle:**

1. User sees the room's empty box (CO_EmptySpaceLine = the slot)
2. Drags a BOM (sofa set, kitchen cabinet set) into the room
3. It snaps to a corner (tack_to meets tack_from)
4. User slides it along a wall (dx changes)
5. User rotates it (rotation_rule changes)
6. Auto-filler inserts BUFFER phantoms in the gaps
7. "Save as BOM" → new M_BOM committed to BOM.db

**Why this works without placing 50,000 elements:**

- AABB auto-set — each BOM already knows its size (allocated_*_mm)
- Children auto-cataloged — each M_Product already in the library
- Selection cascade (§3.3) picks matching BOMs by AABB fit
- Every saved arrangement grows the library for future EN-BLOC reuse
- The designer arranges 5-10 BOMs per room, not thousands of elements

**What the GUI helpers do:**

- Snap: tack_to aligns to tack_from (corner-to-slot)
- Slide: constrained dx/dy movement along parent edges
- Rotate: 0°/90°/180°/270° (rotation_rule update)
- Fill: auto-insert BUFFER phantoms between placed BOMs
- Save: commit the arrangement as a new M_BOM

**The compounding effect:** Every design decision becomes a reusable recipe.
A designer in Kedah saves a living room layout. A developer in Johor compiles
a building with the same room dimensions — the compiler finds the saved BOM by
AABB match and takes it EN-BLOC. The library grows monotonically. Eventually
most rooms are in the catalog. New buildings compile instantly from existing
recipes.

Revit cannot do this because it has no BOM concept — every project starts from
scratch. Here, every design compounds into the library.

See [BIM_Designer.md §7.8](BIM_Designer.md) for the tack-based GUI primitive and
[ACTION_ROADMAP.md Phase G](ACTION_ROADMAP.md) for implementation tasks.
