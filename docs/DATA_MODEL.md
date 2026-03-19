# Data Model — Per-Building BOM Dictionary from Rosetta Stones

**This specification governs the creation of `{PREFIX}_BOM.db` dictionaries.**
Each building has its own BOM dictionary (`SH_BOM.db`, `DX_BOM.db`), reproduced from fresh
by the IFCtoBOM Java pipeline: `./scripts/run_RosettaStones.sh classify_sh.yaml`.
No hand-editing. No patching. Code produces data.

**Source authority:**
- `TheRosettaStoneStrategy.txt` §Stage 2: IFCtoBOM pipeline writes m_bom + m_bom_line per building
- `ConstructionAsERP.md` §1.2 Rule 8 (Cheating Maxim): dx/dy/dz MUST be parent-relative, NEVER world-space centroids
- `BOMBasedCompilation.md` §4: Tack convention — dx/dy/dz = where child's LBD sits in parent (the geometric foundation)

**Updated:** 2026-03-16
**Principle:** 3-DB split. `component_library.db` = master product catalog + geometry (source of truth for products, geometry, orientation). `{PREFIX}_BOM.db` = per-building spatial arrangement (m_bom + m_bom_line with dx/dy/dz). output.db = transactional (fresh each compile). At compile time, `run_RosettaStones.sh` creates `library/_SH_compile.db` (or `_DX_compile.db`) — a temp copy of `{PREFIX}_BOM.db` enriched with shared schema + C_DocType. Java reads via `-Dbom.db=library/_SH_compile.db`. **Note:** M_Product is transitionally copied to BOM DB for BOMWalker; target: read from library only.

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

### 1.2 Tack Convention

**See `BOMBasedCompilation.md` §4 for the full specification.**

Every `m_bom_line` carries **(dx, dy, dz)** — the 3D position within the parent
where the child's LBD corner sits. LBD = Left-Back-Down = (minX, minY, minZ).
Centroid is never stored in the BOM — it is computed at the output stage only.

World coordinate reconstruction (all 3 axes accumulated):
```
element_LBD = building_origin + tack_from[1] + tack_from[2] + ... + tack_from[N]
centroid    = element_LBD + (width/2, depth/2, height/2)
```

**Invariant:** All dx >= 0, dy >= 0, dz >= 0. A child's LBD is always
to the right/front/above its parent's LBD.

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
| origin_x/y/z | Building world LBD (only BUILDING BOM); (0,0,0) for children — see §4 in BOMBasedCompilation.md | Extraction min corner |
| aabb_width/depth/height_mm | Building envelope | (max - min) × 1000 |
| entity_type | D | Dictionary (read-only) |

World position lives in CO_EmptySpace.origin (output.db), populated at compile
time from I_Element_Extraction. `{PREFIX}_BOM.db` is a pure dictionary — no world coords.

**Children:** Each child BOM is linked via a line carrying the tack offset:
`dx = position where child's LBD sits within parent` (always >= 0, BOMBasedCompilation.md §4).
Child BOMs have origin=(0,0,0) — only BUILDING carries world origin.

**Element lines** within each FLOOR/DISCIPLINE BOM:

| Field | Source |
|-------|--------|
| child_product_id | I_Element_Extraction.M_Product_ID |
| role | I_Element_Extraction.ifc_class |
| dx/dy/dz | tack offset (child LBD position within parent, §4) from parent BOM (BOMBasedCompilation.md §4) |
| allocated_width/depth/height_mm | Element AABB × 1000 |

Instance metadata (storey, element_ref, ordinal, orientation, material_name,
material_rgba) is **not stored** in `{PREFIX}_BOM.db` dictionary. It remains in
I_Element_Extraction (component_library.db) and is written to output.db at
compile time.

> **Recipe vs Placement:** Each m_bom_line is a **type line** — one row per unique
> product type within its parent BOM. For formula-driven elements (TILE, ROUTE, WIRE),
> a single type line carries qty=N and the compiler expands to N placement instances.
> For irregular elements, qty=1 (one line = one instance). In a properly factored BOM,
> `COUNT(m_bom_line)` << element count. See `BOMBasedCompilation.md` §2.1.6 for the
> full recipe-vs-placement contract.
>
> **Known debt (TE):** `TE_BOM.db` currently stores **unfactored instance placements**
> (48,485 rows with per-element dx/dy/dz) instead of factored type lines (~943 rows).
> This was an EN-BLOC extraction shortcut. SH/DX are trivially factored (most products
> appear once). TE-6/TE-7 verb compression will fix this.

### 1.5 Integrity Hash

SHA-256 of sorted `(bom_id, child_product_id, round(dx,6), round(dy,6), round(dz,6))`
for all extracted BOM lines. Stored in `ad_sysconfig(config_key='RSTB_INTEGRITY_HASH')`.
Detects external tampering of m_bom_line data.

### 1.6 Per-Building BOM Population — Reproducible from Fresh

Each building has its own BOM dictionary, regenerated deterministically:

**SH / DX (IFCtoBOM Java pipeline — current):**
```bash
./scripts/run_RosettaStones.sh classify_sh.yaml   # → SH_BOM.db
./scripts/run_RosettaStones.sh classify_dx.yaml   # → DX_BOM.db
```

The IFCtoBOM pipeline (`IFCtoBOMPipeline.java`) — see [`YAMLGuide.md`](YAMLGuide.md) §Step 5 for full table:
1. Load classification YAML + extract to `I_Element_Extraction` (`ExtractionPopulator`)
2. **Pre-flight:** FAIL if extraction has storeys not in YAML; FAIL on NULL M_Product_ID
3. `ProductRegistrar.ensureProductCatalog()`: M_Product → component_library.db (persistent, reusable)
4. `ProductRegistrar.ensureProductImages()`: geometry link (INSERT OR IGNORE)
5. **Pre-flight:** FAIL if any product has no geometry_hash
6. `ProductRegistrar.ensureProducts()`: copy to BOM DB (transitional for BOMWalker)
7. `ScopeBomBuilder` / `CompositionBomBuilder` / `StructuralBomBuilder` / `FloorRoomBomBuilder`
8. `BomValidator`: 9 check methods pre-commit (counts, normalization, offsets, AABB, tack I/O, element refs, product normalization, extraction reconciliation — any FAIL = rollback)
9. `IntegrityHash`: computes SHA-256 fingerprint (§1.5)

**At compile time:** `run_RosettaStones.sh` creates `library/_SH_compile.db` (or `_DX_compile.db`) —
a temp copy of `{PREFIX}_BOM.db` enriched with `library/schema_snapshot_bom.sql` + C_DocType rows.
Java reads via `-Dbom.db=library/_SH_compile.db` (`System.getProperty("bom.db")`) — no hardcoded paths.
Compile DBs are auto-cleaned; only `{PREFIX}_BOM.db` persists.

**Migrations:** `migration/archive/*.sql` — historical only, never executed.
`migration/migration_P02_SH_product_link.sql` — dead code (replaced by `ExtractionPopulator.java`).

---

## 2. `{PREFIX}_BOM.db` — Schema Reference

Read-only at compile time. Each per-building dictionary contains domain config, BOM hierarchy, and product catalog.

### C_DocType (6 rows — domain config)

Building type classification. Prime Rule three-key match: DocBaseType + DocSubType + AABB.

> **RESOLVED (session 30, R27):** IFCtoBOM now writes C_DocType into `{PREFIX}_BOM.db`
> during extraction. Shell injection removed from `run_RosettaStones.sh`.
> DSL content read from YAML-adjacent DSL file. StubDataSeeder workaround retained
> for unit tests (in-memory DBs have no BOM pipeline).

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
| origin_x, origin_y, origin_z | REAL | BUILDING BOM: world LBD; all others: (0,0,0). See BOMBasedCompilation.md §4. |
| aabb_width_mm, aabb_depth_mm, aabb_height_mm | INTEGER | Envelope dimensions |
| group_by | TEXT | Grouping key |
| entity_type | TEXT | D=Dictionary, U=User, A=Application |
| is_active | INTEGER | |

### m_bom_line (BOM children — recipe type lines, NOT instance placements)

Each row is a **type line**: one unique product within its parent BOM. The compiler
expands type lines into placement instances via verb formulas (TILE, ROUTE, FRAME)
or flat qty. Instance-level data (per-element coordinates) belongs in output.db.
The walker decides BOM-vs-leaf by whether `child_product_id` resolves to an
`m_bom` row — `component_type` plays no role (BOMBasedCompilation.md §2.2.1).

| Column | Type | Notes |
|--------|------|-------|
| bom_child_id | INTEGER PK | Auto-increment |
| bom_id | TEXT FK | → m_bom.bom_id |
| child_product_id | TEXT | → M_Product or m_bom.bom_id (walker decides by existence) |
| component_type | TEXT | iDempiere compat only — compilation ignores this column |
| role | TEXT | IFC class (extracted) or functional role |
| dx, dy, dz | REAL | **tack offset (child LBD position within parent, §4) from parent BOM (metres, BOMBasedCompilation.md §4)** |
| allocated_width_mm, allocated_depth_mm, allocated_height_mm | INTEGER | Per-instance AABB (mm) |
| storey | TEXT | NULL in `{PREFIX}_BOM.db` dictionary — output.db only (from I_Element_Extraction) |
| element_ref | TEXT | NULL in `{PREFIX}_BOM.db` dictionary — output.db only (from I_Element_Extraction) |
| ordinal | INTEGER | NULL in `{PREFIX}_BOM.db` dictionary — output.db only (from I_Element_Extraction) |
| orientation | TEXT | NULL in `{PREFIX}_BOM.db` dictionary — output.db only (from I_Element_Extraction) |
| material_name, material_rgba | TEXT | NULL in `{PREFIX}_BOM.db` dictionary — material lives on M_Product |
| entity_type | TEXT | D=Dictionary |
| sequence | INTEGER | Sort order |
| is_active | INTEGER | |
| rotation_rule | TEXT | Rotation encoding |
| fit_priority | INTEGER | Tiebreaker (default=20) |

### M_Product (transitional copy — master in component_library.db §3)

Each distinct M_Product_ID in I_Element_Extraction becomes an M_Product row.
BOM assembly stubs (MAKE references) get sentinel dims (0.001).
**This is a transitional copy.** The master catalog lives in component_library.db
(see §3 M_Product). Target: BOMWalker reads from component_library.db directly.

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

## 3. component_library.db — Master Product Catalog + Geometry

Source of truth for product definitions, geometry, orientation, and extraction archive.
`M_Product` is the persistent product catalog (created by `ProductRegistrar.ensureProductCatalog()`,
INSERT OR IGNORE = reused across buildings). `M_Product_Image` links products to geometry.
See [`YAMLGuide.md`](YAMLGuide.md) §"Drift Prevention" for enforced guards.

### I_Element_Extraction (IFC extraction archive)

The source of truth for extracted building element positions.
Read by the IFCtoBOM Java pipeline (SH/DX) or `RosettaStoneToBOM.py` (TE legacy) to generate m_bom + m_bom_line.

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
| M_Product_ID | TEXT | FK → M_Product (same DB — master catalog) |

### M_Product (persistent product catalog)

Master product catalog. Created by `ProductRegistrar.ensureProductCatalog()` from
extraction data. INSERT OR IGNORE = products reused across buildings. Transitionally
copied to `{PREFIX}_BOM.db` for BOMWalker compatibility.

| Column | Type | Notes |
|--------|------|-------|
| product_id | TEXT PK | = element_ref (deterministic) |
| product_type | TEXT | Derived from IFC class (WALL, DOOR, etc.) |
| width, depth, height | REAL | Intrinsic dimensions in metres |
| ifc_class | TEXT | Source IFC entity type |
| extracted_from | TEXT | Always 'IFC_EXTRACTION' |
| building_type | TEXT | Source building |

### M_Product_Image (product → geometry mapping)

Auto-created by `ProductRegistrar.ensureProductImages()` — deterministic join of
I_Element_Extraction × I_Geometry_Map. INSERT OR IGNORE. Never manually migrated.

| Column | Type | Notes |
|--------|------|-------|
| M_Product_ID | TEXT | FK → M_Product (same DB) |
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
{PREFIX}_BOM.db                       component_library.db
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
| c_order.C_DocType_ID | C_DocType.C_DocType_ID | `{PREFIX}_BOM.db` |
| c_orderline.M_Product_ID | M_Product.product_id | `{PREFIX}_BOM.db` (transitional copy; master in component_library.db) |
| element_instances.geometry_hash | LOD_Object.geometry_hash | component_library.db |
| co_empty_space_line.bom_id | m_bom.bom_id | `{PREFIX}_BOM.db` |

---

*Authoritative reference. See `ConstructionAsERP.md` for design rationale,
`TheRosettaStoneStrategy.txt` for verification strategy,
`BOMBasedCompilation.md` for tack convention and pipeline stages.*
*Previous version: `docs/DATA_MODEL (Copy).md`*
