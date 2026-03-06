# Data Model — Authoritative 3-DB Schema Reference

**Updated:** 2026-03-06
**Principle:** 3-DB split. BOM.db = dictionary (read-only config). component_library.db = geometry catalog. output.db = transactional (fresh each compile).

Schema snapshots:
- `library/schema_snapshot_bom.sql` — regen: `sqlite3 library/BOM.db .schema > library/schema_snapshot_bom.sql`
- `library/schema_snapshot_component.sql` — regen: `sqlite3 library/component_library.db .schema > library/schema_snapshot_component.sql`
- `library/output_template.db` — browsable blank output schema + `_schema_guide` table

---

## 1. BOM.db — Dictionary & Configuration

Read-only at compile time. Contains domain config, BOM hierarchy, and product catalog.

### C_DocType (5 rows — domain config)

Constant building type classification. Replaces dropped `c_order` table.

| Column | Type | Notes |
|--------|------|-------|
| C_DocType_ID | TEXT PK | RE_SH, RE_DX, RE_TB, CO_TE, RE_ST |
| Name | TEXT | Display name |
| DocBaseType | TEXT | RE (Residential), CO (Commercial), IN (Institutional) |
| DocSubType | TEXT | SH, DX, TB, TE, ST — drives BOM scoping |
| ProjectName | TEXT | Building instance name |
| DSLContent | TEXT | DSL template text |
| OutputDbPath | TEXT | Output DB path |
| ReferenceDbPath | TEXT | Reference DB path |
| ExpectedElements | INTEGER | Standard element count |
| Provenance | TEXT | EXTRACTED / GENERATIVE |
| GeometryFailThreshold | REAL | Fail threshold |
| SeqNo | INTEGER | Compilation ordering |
| AabbWidthMm, AabbDepthMm, AabbHeightMm | REAL | Standard domain AABB |
| IsActive | INTEGER | Active flag |
| Description | TEXT | Description |

### m_bom (BOM headers)

BOM definitions. 3 dimensions: Category (bom_category) + Owner (doc_sub_type) + SpaceSize (AABB fit).

| Column | Type | Notes |
|--------|------|-------|
| bom_id | TEXT PK | e.g. BED_SET, EXT_SH, LIVING_SET |
| bom_name | TEXT | Display name |
| description | TEXT | |
| target_ifc_class | TEXT | Target IFC entity type |
| group_by | TEXT | Grouping key |
| is_active | INTEGER | |
| bom_level | INTEGER | Hierarchy depth |
| bom_type | TEXT | CHECK: UNIT/FLOOR/ROOM/SET/ITEM |
| bom_category | TEXT | NULL=global, SH/DX/TB/MY/TL=scoped |
| doc_sub_type | TEXT | Owner scope (SH, DX, etc.) |
| seq_no | INTEGER | Owner-specific=10, generic=20 |

### m_bom_line (BOM children)

BOM line items. EXTRACTED BOMs use component_type=BUY exclusively.

| Column | Type | Notes |
|--------|------|-------|
| bom_child_id | INTEGER PK | Auto-increment |
| bom_id | TEXT FK | → m_bom.bom_id |
| child_product_id | TEXT | → M_Product.product_id |
| child_element_type | TEXT | IFC class of child |
| child_name_pattern | TEXT | Name pattern for matching |
| role | TEXT | BOM role (e.g. TOILET, HAND_BIDET) |
| qty_type | TEXT | Quantity type |
| sequence | INTEGER | Sort order |
| is_active | INTEGER | |
| z_rule | TEXT | Z placement rule |
| dx, dy, dz | REAL | Placement offsets (metres) |
| rotation_rule | TEXT | Rotation rule |
| fit_priority | INTEGER | Tiebreaker (default=20) |
| min_space_mm | REAL | Minimum space required |
| locator_ref | TEXT | NORTH_WALL, CENTRE, FLOAT |
| is_variance | INTEGER | SPACER_VAR flag |
| anchor_face | TEXT | Attachment face |
| layout_strategy | TEXT | LINEAR / SURROUND / FLOAT |
| allocated_width_mm | REAL | Allocated width |
| allocated_depth_mm | REAL | Allocated depth |
| allocated_height_mm | REAL | Allocated height |
| component_type | TEXT | MAKE / BUY |
| storey | TEXT | Instance column (P0.2 backfill) |
| element_ref | TEXT | Instance column (P0.2 backfill) |
| ordinal | INTEGER | Instance column (P0.2 backfill) |
| orientation | TEXT | Instance column (P0.2 backfill) |
| material_name | TEXT | Instance column (P0.2 backfill) |
| material_rgba | TEXT | Instance column (P0.2 backfill) |

### M_Product (198 rows — product catalog)

122 original + 65 P0.1-DEDUP + 11 SH products. 79 DX + 11 SH.

| Column | Type | Notes |
|--------|------|-------|
| product_id | TEXT PK | Product identifier |
| product_type | TEXT | Product type |
| width, depth, height | REAL | Dimensions (metres) |
| clear_front/back/left/right/above/below | REAL | Clearance zones |
| fits_in | TEXT | Space fit constraint |
| requires_host | TEXT | Host requirement |
| conn_points | TEXT | Connection points (JSON) |
| code_ref | TEXT | Code reference |
| is_active | INTEGER | |
| extracted_from | TEXT | Source building |
| material_name | TEXT | Material name |
| material_rgba | TEXT | Material RGBA |
| ifc_class | TEXT | IFC entity type |
| M_AttributeSet_ID | TEXT FK | → M_AttributeSet |
| Name | TEXT | iDempiere-style name |
| Description | TEXT | |
| M_Product_Category_ID | TEXT FK | → M_Product_Category |

### M_Product_Category (36 rows — IFC classification)

4 parent disciplines (STR/MEP/ARC/ASM) + 29 IFC class leaves + 3 assembly types.

| Column | Type | Notes |
|--------|------|-------|
| M_Product_Category_ID | TEXT PK | |
| Name | TEXT | Category name (IFC class name for leaves) |
| Description | TEXT | |
| Parent_Category_ID | TEXT FK | Self-referencing hierarchy |
| IFC_Class | TEXT | IFC class identifier |
| SeqNo | INTEGER | Sort order |
| IsActive | INTEGER | |

### M_AttributeSet (5 rows)

BIM_Pipe, BIM_Conduit, BIM_Wall, BIM_Slab, BIM_Component.

| Column | Type | Notes |
|--------|------|-------|
| M_AttributeSet_ID | TEXT PK | |
| Name | TEXT | Attribute set name |
| IsInstanceAttribute | INTEGER | Instance vs class level |
| Description | TEXT | |

### Supporting Tables (ad_* — extraction metadata archive)

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| ad_building | Building definitions | id, building_type, name, width/depth/height_mm, num_storeys |
| ad_building_storey | Storey definitions | building_type, storey_name, storey_level, height_m |
| ad_room_boundary | Room bounds | building_type, storey, room_name, room_type, min/max_x/y_mm |
| ad_element_dependency | Element dependencies | parent/child relationships |
| ad_room_slot | Room slot dispatch (deprecated by bom_category) | room_type, bom_id, building_type |
| ad_space_type | Space type definitions | room types + constraints |
| ad_space_type_mep_bom | MEP BOM per space type | room_type → MEP BOM mapping |
| ad_sysconfig | System configuration | config_key/config_value pairs |
| ad_ref_list | Reference data lists | name/value pairs |

---

## 2. component_library.db — Geometry Catalog

Geometry meshes, product-to-geometry mapping, and extraction archive.

### M_Product_Image (87 rows — product→geometry mapping)

Renamed from LOD_key (P0.2). Maps products to canonical geometry.

| Column | Type | Notes |
|--------|------|-------|
| M_Product_ID | TEXT | FK → M_Product (BOM.db, cross-DB) |
| geometry_hash | TEXT | FK → LOD_Object.geometry_hash |
| up_axis | TEXT | Orientation up axis |
| forward_axis | TEXT | Orientation forward axis |
| attachment_face | TEXT | Attachment face |

### LOD_Object (86 rows — canonical meshes)

Deduplicated geometry meshes.

| Column | Type | Notes |
|--------|------|-------|
| geometry_hash | TEXT PK | SHA-256 hash of geometry |
| vertices | BLOB | Vertex data |
| faces | BLOB | Face indices |
| normals | BLOB | Normal vectors |
| vertex_count | INTEGER | |
| face_count | INTEGER | |

### lod_product_geometry (VIEW)

`M_Product_Image JOIN LOD_Object` — convenience view for geometry lookup by product.

### ad_element_placement (extraction archive)

SH/DX deactivated (P0.2). Terminal 51K rows still active.

| Column | Type | Notes |
|--------|------|-------|
| placement_id | INTEGER PK | |
| building_type | TEXT | Source building |
| storey | TEXT | Storey name |
| ifc_class | TEXT | IFC entity type |
| element_ref | TEXT | Element reference |
| ordinal | INTEGER | Element ordinal |
| min_x, max_x, min_y, max_y, min_z, max_z | REAL | AABB |
| orientation | TEXT | Orientation |
| discipline | TEXT | ARC/STR/MEP/FURN |
| source | TEXT | Extraction source |
| is_active | INTEGER | SH/DX=0, Terminal=1 |
| material_name | TEXT | Material name |
| material_rgba | TEXT | Material RGBA |
| building_id | TEXT | Building identifier |
| M_Product_ID | TEXT | FK → M_Product (BOM.db, cross-DB) |

### Other Tables

| Table | Purpose |
|-------|---------|
| ad_geometry_map | Element → geometry hash mapping (type-level + instance) |
| component_geometries | Legacy mesh storage (23,888 meshes) |
| component_definitions | Legacy mesh definitions |
| surface_styles | Material RGBA colors (80 rows) |
| material_layers | Layer compositions (60 rows) |
| lod_parametric_mesh | Parametric mesh generators (5 rows) |
| lod_parametric_mesh_param | Mesh parameters (41 rows) |
| lod_roof_preset | Roof presets (4 rows) |

---

## 3. output.db — Transactional (Fresh Each Compile)

Created by `CompilationPipeline`. Self-contained — C_Order created from C_DocType at compile time.

### WHAT Layer — Transaction

#### c_order

| Column | Type | Notes |
|--------|------|-------|
| C_Order_ID | TEXT PK | Building instance ID |
| Name | TEXT | Building name |
| DSLContent | TEXT | DSL source |
| OutputDbPath | TEXT | Output path |
| ReferenceDbPath | TEXT | Reference DB |
| IsActive | INTEGER | |
| SeqNo | INTEGER | Compilation order |
| ExpectedElements | INTEGER | Expected count |
| SpatialDigest | TEXT | SHA-256 of element positions |
| Provenance | TEXT | EXTRACTED / GENERATIVE |
| DocStatus | TEXT | DR→IP→CO lifecycle |
| AabbWidthMm, AabbDepthMm, AabbHeightMm | REAL | Domain AABB |
| EmptySpaceChecksum | TEXT | CO_EmptySpace checksum |
| CompiledAt | TEXT | Compile timestamp |
| CompilerVersion | TEXT | Compiler version |
| C_DocType_ID | TEXT | FK → C_DocType (BOM.db, cross-DB) |

#### c_orderline

| Column | Type | Notes |
|--------|------|-------|
| C_OrderLine_ID | INTEGER PK | |
| C_Order_ID | TEXT FK | → c_order |
| Storey | TEXT | Storey identifier |
| Name | TEXT | Element instance name |
| IfcClass | TEXT | IFC entity type |
| Discipline | TEXT | ARC/STR/MEP/FURN |
| M_Product_ID | TEXT | FK → M_Product (BOM.db, cross-DB) |
| IsActive | INTEGER | |
| AD_Building_ID | TEXT | Building identifier |

### WHERE Layer — Spatial

#### elements_meta

| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER | Row ID |
| guid | TEXT | Element GUID (PK-like) |
| discipline | TEXT | ARC/STR/MEP/FURN |
| ifc_class | TEXT | IFC entity type |
| element_name | TEXT | Element name |
| element_type | TEXT | Element type |
| storey | TEXT | Storey name |
| fire_rating_hr | REAL | Fire rating (hours) |
| material_name | TEXT | Material name |
| material_rgba | TEXT | Material RGBA |

#### elements_rtree (VIRTUAL — R-Tree spatial index)

| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER | → elements_meta.id |
| minX, maxX | REAL | X bounds |
| minY, maxY | REAL | Y bounds |
| minZ, maxZ | REAL | Z bounds |

#### spatial_structure

| Column | Type | Notes |
|--------|------|-------|
| guid | TEXT PK | Element GUID |
| type | TEXT | IfcSite/IfcBuilding/IfcBuildingStorey/IfcSpace |
| name | TEXT | Structure name |
| parent_guid | TEXT FK | Parent in hierarchy |
| object_type | TEXT | Object type |
| predefined_type | TEXT | IFC predefined type |

#### rel_contained_in_space

| Column | Type | Notes |
|--------|------|-------|
| element_guid | TEXT | → elements_meta.guid |
| space_guid | TEXT | → spatial_structure.guid (IfcSpace/IfcBuildingStorey) |

#### co_empty_space

| Column | Type | Notes |
|--------|------|-------|
| co_emptyspace_id | TEXT PK | |
| c_order_id | TEXT FK | → c_order |
| origin_x/y/z_mm | REAL | Compile-time measured origin |
| aabb_width/depth/height_mm | REAL | AABB dimensions |
| is_available | INTEGER | Availability flag |
| doc_status | TEXT | Status |
| created, updated | TEXT | Timestamps |

#### co_empty_space_line

| Column | Type | Notes |
|--------|------|-------|
| line_id | TEXT PK | |
| co_emptyspace_id | TEXT FK | → co_empty_space |
| bom_line_seq | INTEGER | BOM line sequence |
| bom_id | TEXT | BOM identifier |
| bom_line_role | TEXT | BOM role |
| bom_level | INTEGER | BOM level |
| before_x/y/z_mm | REAL | Position before placement |
| next_x/y/z_mm | REAL | Position after placement |
| orientation_rad | REAL | Orientation (radians) |
| capacity/filled/remaining_mm | REAL | Space utilisation |
| storey | TEXT | Storey name |
| room_name | TEXT | Room name |
| locator_ref | TEXT | Locator reference |
| c_orderline_id | INTEGER | → c_orderline |
| doc_status | TEXT | Status |

### HOW Layer — Process Planning (VerbStage output)

#### PP_Order_Node

| Column | Type | Notes |
|--------|------|-------|
| PP_Order_Node_ID | TEXT PK | |
| C_Order_ID | TEXT FK | → c_order |
| SeqNo | INTEGER | Execution order |
| Name | TEXT | Verb name |
| Description | TEXT | Verb description |
| S_Resource_ID | TEXT | Resource reference |
| M_Product_ID | TEXT | Product reference |
| IsActive | INTEGER | |
| DocStatus | TEXT | Mirrors c_order lifecycle |
| last_result | TEXT | Last execution result |
| element_count | INTEGER | Elements affected |
| Created, Updated | TEXT | Timestamps |

#### PP_Order_NodeProduct

| Column | Type | Notes |
|--------|------|-------|
| PP_Order_NodeProduct_ID | TEXT PK | |
| PP_Order_Node_ID | TEXT FK | → PP_Order_Node |
| Name | TEXT | Parameter name |
| Value | TEXT | Parameter value |
| ValueType | TEXT | Type hint |

### Geometry Layer

#### base_geometries

| Column | Type | Notes |
|--------|------|-------|
| geometry_hash | TEXT PK | SHA-256 of geometry |
| vertices | BLOB | Vertex data |
| faces | BLOB | Face indices |
| vertex_count | INTEGER | |
| face_count | INTEGER | |

#### element_instances

| Column | Type | Notes |
|--------|------|-------|
| guid | TEXT PK | → elements_meta.guid |
| geometry_hash | TEXT FK | → base_geometries |

#### element_transforms

| Column | Type | Notes |
|--------|------|-------|
| guid | TEXT PK | → elements_meta.guid |
| center_x, center_y, center_z | REAL | Centroid position |
| transform_source | TEXT | Source of transform |

### Assembly Layer

#### element_assemblies

| Column | Type | Notes |
|--------|------|-------|
| assembly_guid | TEXT PK | Assembly GUID |
| assembly_type | TEXT | Assembly type |
| ifc_class | TEXT | IFC class |
| name | TEXT | Assembly name |
| total_width, total_depth, total_height | REAL | Overall dimensions |
| storey | TEXT | Storey name |

#### assembly_components

| Column | Type | Notes |
|--------|------|-------|
| assembly_guid | TEXT FK | → element_assemblies |
| component_guid | TEXT FK | → elements_meta.guid |
| role | TEXT | Component role |
| local_x, local_y, local_z | REAL | Local position |
| sequence | INTEGER | Sort order |
| optional | INTEGER | Optional flag |

### Material Layer

#### surface_styles

| Column | Type | Notes |
|--------|------|-------|
| style_name | TEXT PK | Style identifier |
| surface_r, surface_g, surface_b | REAL | Surface RGB [0..1] |
| transparency | REAL | Transparency [0..1] |
| specular_r, specular_g, specular_b | REAL | Specular RGB |
| specular_ratio | REAL | Specular ratio |
| specular_exponent | REAL | Specular exponent |
| reflectance_method | TEXT | Reflectance method |
| side | TEXT | Surface side |
| source | TEXT | Source building |

#### material_layers

| Column | Type | Notes |
|--------|------|-------|
| layer_set_name | TEXT | Layer set identifier |
| sequence | INTEGER | Layer order |
| material_name | TEXT | Material name |
| thickness_m | REAL | Layer thickness (metres) |
| is_ventilated | INTEGER | Ventilated flag |

### Views (Analytics)

| View | Purpose |
|------|---------|
| room_areas | Aggregates element positions by room |
| area_by_storey | Aggregates areas by storey level |
| area_by_type | Aggregates areas by IFC class |
| building_summary | Top-level stats (total volume, element counts, materials) |

---

## 4. Cross-DB FK Map

Output.db columns that reference BOM.db or component_library.db concepts.
These are logical FKs (not enforced — separate SQLite files).

| output.db Column | References | Target DB |
|------------------|------------|-----------|
| c_order.C_DocType_ID | C_DocType.C_DocType_ID | BOM.db |
| c_orderline.M_Product_ID | M_Product.product_id | BOM.db |
| c_orderline.C_Order_ID | c_order.C_Order_ID | output.db (same) |
| element_instances.geometry_hash | base_geometries.geometry_hash | output.db (same) |
| element_instances.geometry_hash | LOD_Object.geometry_hash | component_library.db (source) |
| PP_Order_Node.M_Product_ID | M_Product.product_id | BOM.db |
| co_empty_space_line.bom_id | m_bom.bom_id | BOM.db |
| co_empty_space_line.c_orderline_id | c_orderline.C_OrderLine_ID | output.db (same) |

### Data Flow at Compile Time

```
BOM.db (read-only)                    component_library.db (read-only)
  C_DocType ──→ BuildingRegistry        M_Product_Image ──→ MeshBinder
  m_bom + m_bom_line ──→ BOMWalker      LOD_Object ──→ base_geometries (copied)
  M_Product ──→ c_orderline             ad_element_placement ──→ PlacementAD (Terminal)
  M_Product_Category ──→ IfcClass
           │                                     │
           └──────────────┬──────────────────────┘
                          ↓
                   CompilationPipeline
                          ↓
                    output.db (written)
                      c_order (from C_DocType)
                      c_orderline (from BOM explosion)
                      elements_meta + elements_rtree
                      spatial_structure + rel_contained_in_space
                      base_geometries + element_instances + element_transforms
                      PP_Order_Node (from VerbStage)
                      co_empty_space + co_empty_space_line
```

---

*Authoritative reference. See `ConstructionAsERP.md` for design rationale, `DEVELOPER_GUIDE.md` for pipeline stages.*
