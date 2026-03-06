# Column Migration Map — c_order → C_DocType + c_orderline DROPPED

**Date:** 2026-03-04
**Migration SQL:** `migration/migration_c_order_idempiere_naming.sql` (phase 1: renames)
                    `migration/migration_c_order_to_c_doctype.sql` (phase 2: merge + drop)
**Principle:** C_DocType = constant domain config (BOM.db). C_Order = transactional (output.db only).
              c_orderline = redundant with M_BOM + M_Product (dropped from BOM.db).

---

## c_order → ABSORBED INTO C_DocType (BOM.db) + C_Order (output.db)

The `c_order` table was **dropped from BOM.db**. Its columns split by domain:

### Domain config → C_DocType (BOM.db, constant)

| Old c_order Column | C_DocType Column | Notes |
|--------------------|-----------------|-------|
| `building_id` / `C_Order_ID` | `ProjectName` | Building instance name |
| `building_name` / `Name` | `Name` | Already existed on C_DocType |
| `dsl_content` / `DSLContent` | `DSLContent` | DSL template text |
| `output_db_path` / `OutputDbPath` | `OutputDbPath` | Output path |
| `reference_db_path` / `ReferenceDbPath` | `ReferenceDbPath` | Reference DB |
| `expected_elements` / `ExpectedElements` | `ExpectedElements` | Std element count |
| `provenance` / `Provenance` | `Provenance` | EXTRACTED / GENERATIVE |
| `geometry_fail_threshold` / `GeometryFailThreshold` | `GeometryFailThreshold` | Fail threshold |
| `seq_no` / `SeqNo` | `SeqNo` | Compilation ordering |
| `aabb_*_mm` / `AabbWidthMm` etc. | `AabbWidthMm` etc. | Std domain AABB |
| `is_active` / `IsActive` | `IsActive` | Already existed |
| `description` / `Description` | `Description` | Already existed |
| `building_type` | **DROPPED** | Redundant with C_DocType.DocBaseType |

### Transactional → C_Order (output.db, created fresh each compile)

| Old c_order Column | C_Order Column | Notes |
|--------------------|---------------|-------|
| `doc_status` / `DocStatus` | `DocStatus` | DR→IP→CO lifecycle |
| `spatial_digest` / `SpatialDigest` | `SpatialDigest` | Computed post-compile |
| `empty_space_checksum` / `EmptySpaceChecksum` | `EmptySpaceChecksum` | Computed post-compile |
| `c_bpartner` / `C_BPartner_ID` | — | Dropped (future: real customer on C_Order) |
| — | `CompiledAt` | New: compile timestamp |
| — | `CompilerVersion` | New: compiler version |
| — | `C_DocType_ID` | FK → C_DocType (cross-DB) |

---

## c_orderline — DROPPED FROM BOM.db (redundant)

The `c_orderline` table (1330 rows) was **dropped from BOM.db**. The data is
derivable from M_BOM + M_BOM_Line + M_Product + component_library.

C_OrderLine is generated at compile time in **output.db** from BOM explosion.

### WHAT columns (were on c_orderline, now output.db only)

| Old Name | Output.db Column | Notes |
|----------|-----------------|-------|
| `id` | `C_OrderLine_ID` | PK |
| `building_type` | `C_Order_ID` | FK to c_order |
| `storey` | `Storey` | Storey identifier |
| `element_ref` | `Name` | Element instance name |
| `ifc_class` | `IfcClass` | IFC entity type |
| `discipline` | `Discipline` | ARC/STR/MEP/FURN |
| `family_ref` | `M_Product_ID` | FK to M_Product |
| `is_active` | `IsActive` | Active flag |
| `building_id` | `AD_Building_ID` | FK to ad_building |

### Placement columns (were on c_orderline, now PP_Order_Node)

| Old Name | Target | Notes |
|----------|--------|-------|
| `host_type` | `PP_Order_Node` | WALL, ROOM, GRID, BUILDING, SLAB |
| `host_ref` | `PP_Order_Node` | Wall face key, room name |
| `position_rule` | `PP_Order_Node` | CENTER, FRACTION, OFFSET |
| `position_value` ×3 | `PP_Order_NodeProduct` | Structured params |
| `height_mm` | `PP_Order_NodeProduct` | Height above host |
| `orientation` | `PP_Order_NodeProduct` | ALONG_HOST, NS, EW |

### Material columns (were on c_orderline, now M_Product)

| Old Name | Target | Notes |
|----------|--------|-------|
| `width_mm` | `M_Product.width` | Meters, not mm |
| `height_extent_mm` | `M_Product.height` | Meters, not mm |
| `depth_mm` | `M_Product.depth` | Meters, not mm |
| `geometry_hash` | `lod_geometry_map` | In component_library.db |
| `material_name` | `M_Product` | TBD column |
| `material_rgba` | `M_Product` | TBD column |

---

## Search Guide

If you find code referencing dropped tables or columns:

- **`c_order` (BOM.db)** → `C_DocType` (domain config) + `c_order` in output.db (transactional)
- **`c_orderline` (BOM.db)** → DROPPED (redundant). M_BOM + M_Product + component_library
- **`host_type`** → `PP_Order_Node` (production operation)
- **`host_ref`** → `PP_Order_Node` (placement target)
- **`position_rule`** → `PP_Order_Node` (placement algorithm)
- **`orientation`** → `PP_Order_NodeProduct` (structured param)
- **`width_mm`** → `M_Product.width` (meters)
- **`depth_mm`** → `M_Product.depth` (meters)
- **`height_extent_mm`** → `M_Product.height` (meters)
- **`geometry_hash`** → `lod_geometry_map` in component_library.db
- **`family_ref`** → `M_Product_ID` (output.db c_orderline, generated at compile time)
- **`element_ref`** → `Name` (output.db c_orderline, generated at compile time)
- **`building_type` (c_order)** → `C_DocType.DocBaseType`
- **`c_bpartner` / `C_BPartner_ID`** → `C_DocType.DocSubType` (for scoping)
- **`building_id` (c_order PK)** → `C_DocType.ProjectName`

---

## Recent Migrations (2026-03-05 to 2026-03-06)

### P0.1 — BOM Extraction & Product Catalog

| Migration | Target DB | Description |
|-----------|-----------|-------------|
| `migration_P01_product_catalog.sql` | BOM.db | M_Product (198 rows), M_Product_Category (36 rows) |
| `migration_P01_placement_product_link.sql` | component_library.db | M_Product_ID column on ad_element_placement |
| `migration_P01_BOM_extracted.sql` | BOM.db | EXT_SH + EXT_DX extracted BOMs (all BUY) |
| `migration_P01_BOM_SH_products.sql` | BOM.db | SH-specific M_Product rows |
| `migration_P01_BOM_SH_placement_link.sql` | component_library.db | SH product→placement links |
| `migration_P01_BOM_precision.sql` | BOM.db | Float-epsilon sort fix for digest |
| `migration_M_Product_Category.sql` | BOM.db | M_Product_Category (36 rows: 4 parents + 29 IFC leaves + 3 assembly) |
| `migration_LOD_pair.sql` | component_library.db | M_Product_Image + LOD_Object tables |

### P0.2 — BOM Walk

| Migration | Target DB | Description |
|-----------|-----------|-------------|
| `migration_P02_M_Product_Image_rename.sql` | component_library.db | LOD_key → M_Product_Image rename |
| `migration_P02_bom_walk_columns.sql` | BOM.db | m_bom_line instance columns (storey, element_ref, ordinal, orientation, material_name, material_rgba) |
| `migration_P02_deactivate_sh_dx.sql` | component_library.db | Deactivate SH/DX rows in ad_element_placement |

### Forensic Audit (2026-03-06)

| Migration | Target DB | Description |
|-----------|-----------|-------------|
| `migration_SH_M_Product_Image.sql` | component_library.db | SH product image rows (11 M_Product_Image entries) |
| `migration_material_rgba_backfill.sql` | BOM.db | Material RGBA backfill for m_bom_line |
