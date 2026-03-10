# Data Model — BOM.db Creation from Rosetta Stones

**This specification governs the creation of BOM.db.**
BOM.db is reproducible from fresh: run `python scripts/RosettaStoneToBOM.py`, get a correct DB.
No hand-editing. No patching. No migration scripts. One script, one DB.

**Source authority:**
- `TheRosettaStoneStrategy.txt` §Stage 2: RosettaStoneToBOM.py = sole script writing m_bom + m_bom_line
- `ConstructionAsERP.md` §1.2 Rule 8 (Cheating Maxim): dx/dy/dz MUST be parent-relative, NEVER world-space centroids
- `BOMBasedCompilation.md` §4: Tack convention — Left-Front-Down = (0,0,0), all offsets positive

**Updated:** 2026-03-10
**Principle:** 3-DB split. BOM.db = dictionary (read-only). component_library.db = geometry catalog. output.db = transactional (fresh each compile).

---

## 1. Extraction Pipeline — I_Element_Extraction → m_bom + m_bom_line

### 1.1 Data Source

`I_Element_Extraction` in `component_library.db` is the authoritative extraction archive.
Each row is one IFC element with its world-space AABB (min_x/max_x, min_y/max_y, min_z/max_z)
and a resolved `M_Product_ID` linking to the product catalog.

| Building | building_type | Elements | Storeys |
|----------|--------------|----------|---------|
| Sample House | Ifc4_SampleHouse | 55 | Ground Floor(27), Roof(2), Unknown(26) |
| Duplex | Ifc2x3_Duplex | 1099 | Level 1(571), Level 2(485), Roof(11), T/FDN(7), Unknown(25) |

### 1.2 LFD Offset Formula (Parent-Relative, Centroid-Based)

The Java pipeline (`PlacementCollectorVisitor.onBuy()`) computes world coordinates as:

```
cx = anchor[0] + line.getDx()        // centroid X
minX = cx - halfW                    // AABB min
maxX = cx + halfW                    // AABB max
```

Therefore **dx/dy/dz in m_bom_line = centroid offset from parent anchor**.

**Formula for extraction (floor-relative):**

```
building_origin   = (min_x, min_y, min_z) across ALL building elements    [LFD corner]
floor_origin      = (min_x, min_y, min_z) across floor's elements         [floor LFD corner]
element_centroid  = ((min_x + max_x)/2, (min_y + max_y)/2, (min_z + max_z)/2)

# Element BUY lines — offset from floor origin (parent-relative)
element_dx        = element_centroid_x - floor_origin_x                    [always >= 0]
allocated_w_mm    = (max_x - min_x) * 1000                                [element AABB]

# MAKE children (floor sub-BOMs) — offset from building origin
make_dx           = floor_origin_x - building_origin_x                     [always >= 0]
```

**Proof (world coordinate reconstruction):**

```
world_centroid = CO_EmptySpace.origin + MAKE.dx + element.dx
               = building_origin + (floor_origin - building_origin) + (centroid - floor_origin)
               = centroid  ✓

output_minX    = world_centroid - halfW
               = centroid - (max - min)/2
               = (min + max)/2 - (max - min)/2
               = min  ✓
```

**Invariant:** All dx >= 0, dy >= 0, dz >= 0. An element centroid is always
to the right/front/above its parent floor's LFD corner. MAKE children carry
the floor-to-building offset (also always >= 0).

### 1.3 Storey-to-Floor BOM Mapping

Each distinct storey in `I_Element_Extraction` maps to one FLOOR-type `m_bom` header.

**SH (Ifc4_SampleHouse):**

| Storey | Floor BOM ID | bom_category | Elements |
|--------|-------------|-------------|----------|
| Ground Floor | SH_GF_STR | GF | 27 |
| Roof | SH_ROOF_STR | RF | 2 |
| Unknown | SH_CW_STR | CW | 26 |

**DX (Ifc2x3_Duplex):**

| Storey | Floor BOM ID | bom_category | Elements |
|--------|-------------|-------------|----------|
| Level 1 | DX_L1_STR | L1 | 571 |
| Level 2 | DX_L2_STR | L2 | 485 |
| Roof | DX_ROOF_STR | RF | 11 |
| T/FDN | DX_FDN_STR | FN | 7 |
| Unknown | DX_MISC_STR | MS | 25 |

### 1.4 BUILDING BOM Structure

Each extracted building produces one BUILDING-type `m_bom` with:

| Field | Value | Source |
|-------|-------|--------|
| bom_id | BUILDING_SH_STD / BUILDING_DX_STD | Convention |
| bom_type | BUILDING | Fixed |
| doc_base_type | RE | Residential |
| doc_sub_type | SH / DX | Building variant |
| bom_category | RE | Residential template |
| origin_x/y/z | Always (0,0,0) — tack convention self-origin | Fixed |
| aabb_width/depth/height_mm | Building envelope | (max - min) × 1000 |
| entity_type | D | Dictionary (read-only) |

World position lives in CO_EmptySpace.origin (output.db), populated at compile
time from I_Element_Extraction. BOM.db is a pure dictionary — no world coords.

**Children:** Each FLOOR BOM is linked as a MAKE line carrying the floor-to-building
offset: `dx = floor_origin_x - building_origin_x` (always >= 0, §1.2).
Floor BOMs have origin=(0,0,0).

**Element lines** within each FLOOR BOM:

| Field | Source |
|-------|--------|
| child_product_id | I_Element_Extraction.M_Product_ID |
| component_type | BUY (all extracted elements) |
| role | I_Element_Extraction.ifc_class |
| dx/dy/dz | Centroid offset from floor origin (parent-relative, §1.2) |
| allocated_width/depth/height_mm | Element AABB × 1000 |

Instance metadata (storey, element_ref, ordinal, orientation, material_name,
material_rgba) is **not stored** in BOM.db dictionary. It remains in
I_Element_Extraction (component_library.db) and is written to output.db at
compile time.

### 1.5 Integrity Hash

SHA-256 of sorted `(bom_id, child_product_id, round(dx,6), round(dy,6), round(dz,6))`
for all extracted BOM lines. Stored in `ad_sysconfig(config_key='RSTB_INTEGRITY_HASH')`.
Detects external tampering of m_bom_line data.

### 1.6 BOM.db Population — Reproducible from Fresh

BOM.db is regenerated from one script, never hand-edited:

```bash
python scripts/RosettaStoneToBOM.py    # creates schema + all content
```

The script:
1. Creates schema from `library/schema_snapshot_bom.sql`
2. Populates reference data: C_DocType, M_Product, M_BomCategory, M_Product_Category
3. Generates ROOM BOMs (MY terrace style — existing)
4. Generates extracted BOMs (SH + DX from I_Element_Extraction — §1.1–§1.4)
5. Runs `create_ad_*.py` scripts for AD metadata
6. Computes integrity hash (§1.5)

**Legacy:** `migration/archive/*.sql` — historical only, never executed.

---

## 2. BOM.db — Schema Reference

Read-only at compile time. Contains domain config, BOM hierarchy, and product catalog.

### C_DocType (6 rows — domain config)

Building type classification. Prime Rule three-key match: DocBaseType + DocSubType + AABB.

| Column | Type | Notes |
|--------|------|-------|
| C_DocType_ID | TEXT PK | RE_SH, RE_DX, RE_TB, CO_TE, ST_SH, ST_DX |
| Name | TEXT | Display name |
| DocBaseType | TEXT | RE (Residential), CO (Commercial), IN (Industrial) |
| DocSubType | TEXT | SH, DX, TB, TE, ST — drives BOM scoping. ST = template path |
| ProjectName | TEXT | Building instance name |
| DSLContent | TEXT | DSL template text |
| OutputDbPath | TEXT | Output DB path |
| ReferenceDbPath | TEXT | Reference DB path |
| ExpectedElements | INTEGER | Standard element count |
| Provenance | TEXT | EXTRACTED / GENERATIVE |
| AabbWidthMm, AabbDepthMm, AabbHeightMm | REAL | Standard domain AABB |
| IsActive | INTEGER | Active flag |

### m_bom (BOM headers)

4 dimensions: DocType + DocSubType + Category + SpaceSize (AABB fit).

| Column | Type | Notes |
|--------|------|-------|
| bom_id | TEXT PK | e.g. BUILDING_SH_STD, SH_GF_STR, BED_SET |
| bom_name | TEXT | Display name |
| bom_type | TEXT | CHECK: BUILDING/FLOOR/ROOM/SET/ITEM |
| bom_category | TEXT | Functional role — FK → M_BomCategory |
| doc_base_type | TEXT | RE/CO/IN — Prime Rule key |
| doc_sub_type | TEXT | SH/DX/TB/TE — variant scope |
| origin_x, origin_y, origin_z | REAL | Always (0,0,0) — tack convention self-origin. World position → CO_EmptySpace (output.db) |
| aabb_width_mm, aabb_depth_mm, aabb_height_mm | INTEGER | Envelope dimensions |
| group_by | TEXT | Grouping key |
| entity_type | TEXT | D=Dictionary, U=User, A=Application |
| is_active | INTEGER | |

### m_bom_line (BOM children)

EXTRACTED BOMs use component_type=BUY exclusively.

| Column | Type | Notes |
|--------|------|-------|
| bom_child_id | INTEGER PK | Auto-increment |
| bom_id | TEXT FK | → m_bom.bom_id |
| child_product_id | TEXT | → M_Product.product_id |
| component_type | TEXT | BUY / MAKE / PHANTOM |
| role | TEXT | IFC class (extracted) or functional role |
| dx, dy, dz | REAL | **Centroid offset from parent anchor (metres, parent-relative §1.2)** |
| allocated_width_mm, allocated_depth_mm, allocated_height_mm | INTEGER | Per-instance AABB (mm) |
| storey | TEXT | NULL in BOM.db dictionary — output.db only (from I_Element_Extraction) |
| element_ref | TEXT | NULL in BOM.db dictionary — output.db only (from I_Element_Extraction) |
| ordinal | INTEGER | NULL in BOM.db dictionary — output.db only (from I_Element_Extraction) |
| orientation | TEXT | NULL in BOM.db dictionary — output.db only (from I_Element_Extraction) |
| material_name, material_rgba | TEXT | NULL in BOM.db dictionary — material lives on M_Product |
| entity_type | TEXT | D=Dictionary |
| sequence | INTEGER | Sort order |
| is_active | INTEGER | |
| rotation_rule | TEXT | Rotation encoding |
| fit_priority | INTEGER | Tiebreaker (default=20) |

### M_Product (product catalog)

Each distinct M_Product_ID in I_Element_Extraction becomes an M_Product row.
BOM assembly stubs (MAKE references) get sentinel dims (0.001).

| Column | Type | Notes |
|--------|------|-------|
| product_id | TEXT PK | Product identifier |
| product_type | TEXT | DOOR/WALL/FURNITURE/SET/FLOOR/etc. |
| width, depth, height | REAL | Dimensions (metres) |
| ifc_class | TEXT | IFC entity type |
| extracted_from | TEXT | Source building |
| material_name, material_rgba | TEXT | Catalog material |
| M_Product_Category_ID | TEXT FK | → M_Product_Category |
| is_active | INTEGER | |

### M_BomCategory (functional classification)

| M_BomCategory_ID | Name | Purpose |
|-------------------|------|---------|
| RE | Residential Template | Standard residential decomposition |
| GF | Ground Floor | Habitable body |
| L1 | Level 1 | Ground floor assembly |
| L2 | Level 2 | Upper floor assembly |
| RF | Roof | Roof assemblies |
| SL | Slab | Floor slab assemblies |
| LI | Living | Living room settings |
| BD | Bedroom | Bedroom settings |
| KT | Kitchen | Kitchen settings |
| BT | Bathroom | Bathroom/toilet settings |
| DN | Dining | Dining settings |
| FR | Furniture | Leaf furniture items |
| WL | Wall | Wall assemblies |
| PH | Porch | Porch modules |
| PR | Pair | Duplex pair container |
| HU | Half-Unit | Duplex half-unit |
| MP | MEP | MEP trunk/service group |
| CW | Curtain Wall | Curtain wall structure |
| FN | Foundation | Foundation/transfer structure |
| MS | Miscellaneous | Stair, railing, misc |

---

## 3. component_library.db — Geometry Catalog

Geometry meshes, product-to-geometry mapping, and extraction archive.

### I_Element_Extraction (IFC extraction archive)

The source of truth for extracted building element positions.
Read by `RosettaStoneToBOM.py` to generate m_bom + m_bom_line.

| Column | Type | Notes |
|--------|------|-------|
| placement_id | INTEGER PK | |
| building_type | TEXT | Ifc4_SampleHouse / Ifc2x3_Duplex |
| storey | TEXT | Storey name |
| ifc_class | TEXT | IFC entity type |
| element_ref | TEXT | Element reference |
| ordinal | INTEGER | Position order |
| min_x, max_x, min_y, max_y, min_z, max_z | REAL | World-space AABB |
| orientation | TEXT | NS/EW/POINT |
| material_name, material_rgba | TEXT | Material |
| M_Product_ID | TEXT | FK → M_Product (BOM.db, cross-DB) |

### M_Product_Image (product → geometry mapping)

| Column | Type | Notes |
|--------|------|-------|
| M_Product_ID | TEXT | FK → M_Product (BOM.db) |
| geometry_hash | TEXT | FK → LOD_Object |

### LOD_Object (canonical meshes)

| Column | Type | Notes |
|--------|------|-------|
| geometry_hash | TEXT PK | SHA-256 of geometry |
| vertices | BLOB | Vertex data |
| faces | BLOB | Face indices |
| vertex_count, face_count | INTEGER | |

---

## 4. output.db — Transactional (Fresh Each Compile)

Created by CompilationPipeline. C_Order created from C_DocType at compile time.

### Coordinate Flow (compilation)

```
BOM.db                                component_library.db
  m_bom.origin_x/y/z  ──┐              M_Product_Image ──→ MeshBinder
  m_bom_line.dx/dy/dz ──┤
  m_bom_line.allocated ──┤
                         ↓
              PlacementCollectorVisitor
                anchor = origin + Σ(line.dx + child.origin)
                cx = anchor + dx
                minX = cx - halfW
                         ↓
              output.db
                elements_meta + elements_rtree (world AABB)
                c_orderline (WHAT)
                co_empty_space_line (WHERE)
                PP_Order_Node (HOW)
```

### Key Tables

| Table | Purpose |
|-------|---------|
| c_order | Building instance (from C_DocType) |
| c_orderline | Element instances (from BOM explosion) |
| elements_meta | Element metadata (guid, ifc_class, storey) |
| elements_rtree | Spatial index (world AABB) |
| co_empty_space | Construction space header |
| co_empty_space_line | Spatial resolution per BOM line |
| PP_Order_Node | Verb execution audit trail |
| base_geometries | Geometry meshes (copied from component_library) |
| element_instances | Element → geometry mapping |

---

## 5. Cross-DB FK Map

| output.db Column | References | Target DB |
|------------------|------------|-----------|
| c_order.C_DocType_ID | C_DocType.C_DocType_ID | BOM.db |
| c_orderline.M_Product_ID | M_Product.product_id | BOM.db |
| element_instances.geometry_hash | LOD_Object.geometry_hash | component_library.db |
| co_empty_space_line.bom_id | m_bom.bom_id | BOM.db |

---

*Authoritative reference. See `ConstructionAsERP.md` for design rationale,
`TheRosettaStoneStrategy.txt` for verification strategy,
`BOMBasedCompilation.md` for tack convention and pipeline stages.*
*Previous version: `docs/DATA_MODEL (Copy).md`*
