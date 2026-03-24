# Data Model — Per-Building BOM Dictionary from Rosetta Stones

> **Foundation:** [BBC](BOMBasedCompilation.md) · [BIM_COBOL](BIM_COBOL.md) · [SystemContract](SystemContract.md) · [TestArchitecture](TestArchitecture.md) · [ACTION_ROADMAP](ACTION_ROADMAP.md) · [SourceCodeGuide](SourceCodeGuide.md)

**This specification governs the creation of `{PREFIX}_BOM.db` dictionaries.**
Each building has its own BOM dictionary (`SH_BOM.db`, `DX_BOM.db`), reproduced from fresh
by the IFCtoBOM Java pipeline: `./scripts/run_RosettaStones.sh classify_sh.yaml`.
No hand-editing. No patching. Code produces data.

**Source authority:**
- `TheRosettaStoneStrategy.md` §Stage 2: IFCtoBOM pipeline writes m_bom + m_bom_line per building
- `BOMBasedCompilation.md` §4 Rule 8 (Cheating Maxim): dx/dy/dz MUST be parent-relative, NEVER world-space centroids
- `BOMBasedCompilation.md` §4: Tack convention — dx/dy/dz = where child's LBD sits in parent (the geometric foundation)

**Updated:** 2026-03-19
**Principle:** 4-DB split.
- `component_library.db` = product LOD catalog (M_Product, geometry, orientation)
- `disc_validation.db` = discipline metadata (schedules, types, placement rules, alias cascade) — see [DISC_VALIDATION_DB_SRS.md](DISC_VALIDATION_DB_SRS.md)
- `{PREFIX}_BOM.db` = per-building spatial arrangement (m_bom + m_bom_line with dx/dy/dz)
- `output.db` = transactional (fresh each compile)

At compile time, `run_RosettaStones.sh` creates `library/_SH_compile.db` (or `_DX_compile.db`) — a temp copy of `{PREFIX}_BOM.db` enriched with shared schema + C_DocType. Java reads via `-Dbom.db=library/_SH_compile.db`. **Note:** M_Product is transitionally copied to BOM DB for BOMWalker; target: read from library only.

**Note:** Discipline metadata migration complete (session 41). All discipline tables
(ad_space_type, ad_element_mep, ad_wall_face, placement_rules, etc.) now live exclusively
in disc_validation.db. Java code (MEPAD, MEPBOMResolver, ManifestResolver, CalibrationDAO)
reads from disc_validation.db. component_library.db reduced from 81→21 tables (LOD catalog only).
See DISC_VALIDATION_DB_SRS.md §6.

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

### 1.3 Segment-to-Floor BOM Mapping

Each distinct segment in `I_Element_Extraction` maps to one FLOOR-type `m_bom` header.
For buildings, a segment is a storey (`IfcBuildingStorey`). For infrastructure, a segment
is a facility part (`IfcBridgePart`, `IfcRoadPart`, `IfcRailwayPart`). The `storey`
column in `elements_meta` stores the segment name regardless of domain.
See [`InfrastructureAnalysis.md`](InfrastructureAnalysis.md) §4 for the hierarchy mapping.

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
| bom_type | BUILDING | Fixed (abstract root — used for both buildings and infrastructure facilities) |
| doc_base_type | RE / CO / IN | **Deprecated** — redundant with m_product_category_id (see §7 migration plan) |
| doc_sub_type | SH / DX / BR / RD | **Deprecated** — redundant with building prefix/bom_id (see §7 migration plan) |
| m_product_category_id | RE / CO / IN | Top-level M_Product_Category — classification lives here |
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
> **TE factorization DONE (sessions 8-11):** `TE_BOM.db` stores **1,131 factored
> recipe lines** (505 products × verb formulas) expanding to 48,428 placement
> instances at compile time. CLUSTER/TILE/FRAME/ROUTE verbs encode 97.6% of
> the BOM. See `TerminalAnalysis.md` §BOM Factorization for details.

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

The IFCtoBOM pipeline (`IFCtoBOMPipeline.java`) — see [`WorkOrderGuide.md`](WorkOrderGuide.md) §Step 5 for full table:
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

"Construction Order" — one document type. Classification lives on M_Product_Category, not here. Legacy columns (DocBaseType, DocSubType) exist but are deprecated (see §7 migration plan).

> **RESOLVED (session 30, R27):** IFCtoBOM now writes C_DocType into `{PREFIX}_BOM.db`
> during extraction. Shell injection removed from `run_RosettaStones.sh`.
> DSL content read from YAML-adjacent DSL file. StubDataSeeder workaround retained
> for unit tests (in-memory DBs have no BOM pipeline).

| Column | Type | Notes |
|--------|------|-------|
| C_DocType_ID | TEXT PK | RE_SH, RE_DX, RE_TB, CO_TE, ST_SH, ST_DX |
| Name | TEXT | Display name |
| DocBaseType | TEXT | **Deprecated** — RE/CO/IN. Redundant with M_Product_Category (see §7) |
| DocSubType | TEXT | **Deprecated** — SH/DX/TB/TE/ST. Redundant with building prefix (see §7) |
| ProjectName | TEXT | Building instance name |
| DSLContent | TEXT | DSL template text |
| OutputDbPath | TEXT | Output DB path |
| ReferenceDbPath | TEXT | Reference DB path |
| ExpectedElements | INTEGER | Standard element count |
| Provenance | TEXT | EXTRACTED / GENERATIVE |
| AabbWidthMm, AabbDepthMm, AabbHeightMm | REAL | Standard domain AABB |
| IsActive | INTEGER | Active flag |

### m_bom (BOM headers)

2 selection dimensions: M_Product_Category + SpaceSize (AABB fit). Legacy columns doc_base_type/doc_sub_type exist but are deprecated (see §7).

| Column | Type | Notes |
|--------|------|-------|
| bom_id | TEXT PK | e.g. BUILDING_SH_STD, SH_GF_STR, BED_SET |
| bom_name | TEXT | Display name |
| bom_type | TEXT | CHECK: BUILDING/FLOOR/ROOM/SET/ITEM |
| bom_category | TEXT | Functional role — FK → M_Product_Category |
| doc_base_type | TEXT | **Deprecated** — RE/CO/IN. Redundant with m_product_category_id (see §7) |
| doc_sub_type | TEXT | **Deprecated** — SH/DX/TB/TE. Redundant with bom_id prefix (see §7) |
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

### M_Product_Category (flat classification — aligned with iDempiere naming)

| M_Product_Category_ID | Name | Purpose |
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

## 3. component_library.db — Product LOD Catalog + Geometry

Source of truth for product definitions, geometry, orientation, and extraction archive.
`M_Product` is the persistent product catalog (created by `ProductRegistrar.ensureProductCatalog()`,
INSERT OR IGNORE = reused across buildings). `M_Product_Image` links products to geometry.
See [`WorkOrderGuide.md`](WorkOrderGuide.md) §"Drift Prevention" for enforced guards.

**Discipline metadata (ad_space_type, ad_element_mep, ad_wall_face, placement_rules,
etc.) is migrating to disc_validation.db** — see [DISC_VALIDATION_DB_SRS.md](DISC_VALIDATION_DB_SRS.md).
Tables remain here temporarily (Phase 2 pending).

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

## 6. AD Data Placement — Towards a Shared ERP.db

> **Status:** Investigation only (S69). No schema changes proposed.

### 6.1 Current State — Where AD Tables Live

| Table | disc_validation.db | component_library.db | {PREFIX}_BOM.db | Purpose |
|-------|:--:|:--:|:--:|---------|
| **AD_Org** | 16 rows | — | — | Discipline definitions (ARC, STR, FP...) |
| **M_Product_Category** | IFC→discipline map | IFC→discipline map | 0 rows (DM only) | Product classification |
| **AD_SysConfig** | 8 rows (schema versions) | — | 2 rows (integrity hash, expected elements) | Different schema, different purpose |
| **ad_val_rule + param** | 63 + N rows | — | — | Validation rules |
| **C_DocType** | — | — | 1 row per building | Building type classification |
| **M_Product** | 2,477 rows | 2,475 rows (master) | 2–93 rows (transitional copy) | Product catalog |
| **M_Attribute*** | — | — | per-building | Instance customization |
| **m_bom + m_bom_line** | — | — | per-building | Spatial BOM (the core per-building data) |

### 6.2 Duplication Analysis

**M_Product** is the most duplicated table:
- **component_library.db** is the master catalog (created by `ProductRegistrar.ensureProductCatalog()`)
- **disc_validation.db** has a near-identical copy (2,477 vs 2,475) used for validation queries
- **{PREFIX}_BOM.db** has small transitional copies for BOMWalker compatibility (target: remove)

**M_Product_Category** exists in both disc_validation.db and component_library.db with
identical IFC class mappings. Only one authoritative copy is needed.

**AD_SysConfig** is NOT truly duplicated — different schemas serve different purposes:
- disc_validation.db: `Name/Value` pairs tracking schema migration versions (DV001–DV015)
- BOM databases: `config_key/config_value` pairs tracking per-building integrity hashes

**C_DocType** lives only in BOM databases (1 row each, e.g., RE_SH, RE_DX). In iDempiere,
C_DocType is shared system configuration — it defines all document types centrally. Currently
written by `IFCtoBOMPipeline` per-building; at compile time, `schema_snapshot_bom.sql` enriches
the compile DB with additional C_DocType rows.

### 6.3 The ERP.db Concept

In iDempiere, AD tables (Application Dictionary) are shared across all organisations.
They live once, centrally — not per-product or per-order. The current `disc_validation.db`
already serves this role for most AD data but has a misleading name and incomplete coverage.

**Proposed consolidation** (rename disc_validation.db → ERP.db or create as superset):

| Layer | Database | Contains |
|-------|----------|----------|
| **ERP shared** | `ERP.db` (was disc_validation.db) | AD_Org, M_Product_Category, C_DocType (master), AD_SysConfig (schema versions), ad_val_rule, all ad_* discipline metadata |
| **Product catalog** | `component_library.db` | M_Product (master), M_Product_Image, LOD_Object, I_Element_Extraction, geometry |
| **Per-building BOM** | `{PREFIX}_BOM.db` | m_bom, m_bom_line, M_Attribute*, ad_sysconfig (per-building integrity), C_DocType (compile-time copy) |
| **Transactional** | `output.db` | c_order, c_orderline, elements_meta, PP_Order_Node, co_empty_space* |

**What changes:**
- C_DocType master definitions move to ERP.db (all 6+ rows). BOM databases get a copy at compile time (already happens via schema_snapshot_bom.sql).
- M_Product_Category: single authoritative copy in ERP.db. Remove from component_library.db (or make it a compile-time copy).
- M_Product in disc_validation.db: evaluate whether validation queries can read from component_library.db directly, eliminating the copy.

**What stays the same:**
- BOM databases keep their per-building ad_sysconfig (different schema, per-building data)
- BOM databases keep transitional M_Product copies until BOMWalker migration completes
- The 4-DB split principle remains — ERP.db is a rename/consolidation, not a new database

### 6.4 Benefits

1. **Single source of truth** for AD tables — no more wondering which DB has the authoritative M_Product_Category
2. **iDempiere alignment** — AD tables centralised, just like iDempiere's dictionary
3. **Clearer naming** — "ERP.db" immediately tells a new developer this is shared configuration
4. **Simpler pipeline** — C_DocType written once to ERP.db, not re-created per building

### 6.5 Risks and Open Questions

- **Migration complexity:** Renaming disc_validation.db affects every script that references it (Java properties, shell scripts, SQL paths). Needs careful grep + rename pass.
- **Backward compatibility:** Existing BOM databases expect schema_snapshot_bom.sql enrichment at compile time. This mechanism continues regardless.
- **M_Product consolidation:** Removing the disc_validation.db copy requires verifying all validation queries can use component_library.db. Some validation SQL may JOIN with AD_Org or ad_val_rule in the same DB connection — cross-DB JOINs in SQLite require ATTACH.

> **Decision:** No action this session. This section documents the investigation.
> Implementation would be a separate bounded task with its own migration plan.

---

## 7. DocBaseType → M_Product_Category Alignment — S70 Findings

> **Status:** Analysis complete (S70). MANIFESTO.md corrected. Code + doc propagation pending.
> **Pre-flight:** `// Implementing DATA_MODEL.md §7 — DocBaseType → M_Product_Category alignment`

### 7.1 The Problem

`doc_base_type` and `doc_sub_type` on `m_bom` are redundant with `m_product_category_id` and
`bom_id`. The BUILDING BOM has `doc_base_type=RE, doc_sub_type=SH` but NULL `m_product_category_id`.
Child BOMs have `m_product_category_id` set (GF, RF, LIVING) but NULL `doc_base_type`. Both carry
classification — on different columns at different levels. The correct model: M_Product_Category
carries classification at every level. C_DocType = ONE "Construction Order" (not per-building-type).

### 7.2 Docs Needing Correction

| Doc | What to fix |
|-----|------------|
| **BOMBasedCompilation.md** | L44-55: C_DocType.DocBaseType analogy. L297: "DocBaseType on M_Product_Category". L1149-1186: YAML schema refs |
| **SystemContract.md** | L63: C_DocType = "Building type classification" → "Construction Order". L434: "per DocBaseType + DocSubType" |
| **DATA_MODEL.md** | §1.4: doc_base_type/doc_sub_type on m_bom. §2 C_DocType/m_bom schema. This section (§7) is the correction anchor |
| **TerminalAnalysis.md** | L661-697: entire "DocBaseType — Real Semantic Work" section. M_Product_Category scoped by doc_type column |
| **SourceCodeGuide.md** | L292: "DocBaseType=CO" skip logic. L589: C_DocType identity definition |
| **ProjectOrderBlueprint.md** | CLEAN — no changes needed |
| **DocAction_SRS.md** | CLEAN — no changes needed |
| **DISC_VALIDATION_DB_SRS.md** | CLEAN — no changes needed |

### 7.3 Java Files Routing on DocBaseType/DocSubType

**Source files (19) — routing logic that must migrate to M_Product_Category:**

| Module | File | What it does |
|--------|------|-------------|
| DAGCompiler | `BomDropper.java` | `findBuildingBom()` WHERE doc_base_type = ? AND doc_sub_type = ? |
| DAGCompiler | `BuildingRegistry.java` | `loadByDocBaseType()`, JOIN doc_base_type = DocBaseType |
| DAGCompiler | `CompilationPipeline.java` | Three-key match, ST dispatch, CO dispatch |
| BIM_COBOL | `ComposeBuildingVerb.java` | COMPOSE BUILDING \<docBaseType\> |
| ORMSandbox | `MCDocType.java` | `getByDocBaseType()`, switch on docBaseType |
| ORMSandbox | `X_C_DocType.java` | Column definitions (DocBaseType, DocSubType) |
| ORMSandbox | `X_M_BOM.java` | doc_base_type column accessor |
| ORMSandbox | `BomTemplateContract.java` | Template derivation from docSubType → docBaseType |
| ORMSandbox | `BomTemplateComposer.java` | Three-key match (AABB + DocBaseType + DocSubType) |
| ORMSandbox | `MBomCategory.java` | docType parameter matching DocBaseType |
| ORMSandbox | `MBomCategoryLine.java` | docType parameter matching DocBaseType |
| ORMSandbox | `X_CCampaign.java` | DocBaseType reference |
| BonsaiBIMDesigner | `DesignerDAO.java` | JOIN doc_base_type = DocBaseType |
| BonsaiBIMDesigner | `DesignerAPIImpl.java` | `deriveFacilityType()` routing, JOIN |
| BonsaiBIMDesigner | `StubDataSeeder.java` | Schema creation with DocBaseType |
| BonsaiBIMDesigner | `WorkOutputDAO.java` | doc_base_type in schema |
| IFCtoBOM | `IFCtoBOMPipeline.java` | Dispatch on doc_base_type, C_DocType creation |
| IFCtoBOM | `StructuralBomBuilder.java` | INSERT with doc_base_type |
| IFCtoBOM | `DisciplineBomBuilder.java` | INSERT with doc_base_type |
| IFCtoBOM | `ClassificationYaml.java` | Record with docBaseType field |
| IFCtoBOM | `BomValidator.java` | Validates doc_base_type on BUILDING BOM |
| BIMBackOffice | `PortfolioDAO.java` | SELECT DocBaseType |

**Test files (12):** PrimeRuleWitnessTest, BuildingRegistryTest, DataIntegrityTest,
RemoveCompressTest, OrderInheritanceTest, BomDropperOrderIdTest, BomDropCompileTest,
BomDropConfigureTest, CompileBridgeTest, ASIAuthoringTest, DemoHouseTest, SelectionCascadeTest

### 7.4 M_Product_Category Hierarchy — Current vs Target

**Current (disc_validation.db, 46 rows):** IFC element classification only (IFC_WALL→STR,
IFC_DOOR→ARC, etc.) + 4 parent groups (STR, MEP, ARC, ASM) + assembly types.

**Missing for cascade model (need migration to add):**
- Top-level: RE (Residential), IN (Infrastructure), CO (Commercial), IP (Industrial Plant)
- Floor-level: GF, L1, L2, L3, L4, L5, RF, FN, MS
- Room-level: LI (Living), KT (Kitchen), BD (Bedroom), BT (Bathroom), DN (Dining), FR (Furniture), etc.
- Infra segments: SUP, DCK, ABT, TRK, ROAD, RAIL

These values already exist as `bom_category` strings on `m_bom` rows in BOM databases — they just
aren't registered as M_Product_Category rows in disc_validation.db. Migration: INSERT OR IGNORE
from the existing bom_category vocabulary.

### 7.5 ERP.db Rename — Touchpoint Count

Renaming `disc_validation.db` → `ERP.db` affects:
- Java system property references: `disc.validation.db` (grep needed for exact count)
- Shell scripts: `run_RosettaStones.sh`, `run_tests.sh`, extraction scripts
- Python scripts: `RosettaStoneToBOM.py`, `RosettaStoneExtract.py`
- Doc references across ~10 specs
- Estimated total touchpoints: 40–60 (bounded task, separate session)

### 7.6 Migration Plan (follow-up sessions)

1. **Session N+1:** Add missing M_Product_Category rows to disc_validation.db (DV018 migration)
2. **Session N+2:** Populate `m_product_category_id` on BUILDING BOMs (currently NULL where doc_base_type is set)
3. **Session N+3:** Migrate Java routing from doc_base_type → m_product_category_id (BomDropper, BuildingRegistry, CompilationPipeline first)
4. **Session N+4:** Remove doc_base_type/doc_sub_type columns from m_bom schema (or mark deprecated)
5. **Session N+5:** Rename disc_validation.db → ERP.db + propagate all touchpoints
6. **Each session:** Propagate doc corrections to 1–2 specs from §7.2 list

---

*Authoritative reference. See `BOMBasedCompilation.md` for tack convention and pipeline stages,
`SystemContract.md` for the three-concern model (WHAT/HOW/WHERE),
`TheRosettaStoneStrategy.md` for verification strategy.*
