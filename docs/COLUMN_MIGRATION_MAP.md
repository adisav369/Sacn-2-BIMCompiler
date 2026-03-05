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
