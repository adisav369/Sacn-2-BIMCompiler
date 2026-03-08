# Schema Quick-Reference (2026-03-09)

## BOM.db — 79 tables (read-only dictionary)
| Table | Rows | Key Columns | Role |
|-------|------|-------------|------|
| m_bom | 58 active / 60 total | bom_id, bom_name, bom_type, bom_category, entity_type | Assembly headers |
| m_bom_line | 1,438 active / 1,463 total | bom_id, child_product_id, dx/dy/dz, rotation_rule, component_type | Child placements |
| M_Product | 201 | product_id, ifc_class, width/depth/height (metres) | Product catalog |
| m_attribute | 499 | bom_line_id, attr_name, attr_value | Leaf product attributes |
| M_BomCategory | 23 | category_code (LI/BD/KT/BT/DN/FR/ST/L1/L2/UN/GF/PR/HU/MP...) | Room/zone types |
| M_BomCategoryLine | 25 | category_id, child_bom_id | Template recipe tree |
| C_DocType | 5 | DocSubType (SH/DX/TB/TE/ST), expected_elements | Building configs |
| C_BPartner | 6 | SH/DX/TB/MY/TE/ST | Building owner scoping |
| C_Campaign | 4 | SCAN/TROP/INST/INDU | Design themes |
| AD_User | 1 | System (ID=100) | Users |

## component_library.db — 79 tables (immutable geometry)
| Table | Rows | Role |
|-------|------|------|
| M_Product_Image | 115 | product_id -> geometry_hash mapping |
| LOD_Object | 110 | Canonical meshes (deduplicated) |
| surface_styles | 80 | Material RGBA colors |
| component_geometries | 23,894 | Raw vertex/face BLOBs |
| lod_parametric_mesh | 2 | Parametric mesh templates |

## Output DB (generated per compile)
| Table | Role |
|-------|------|
| c_order | One row per building compilation |
| c_orderline | Every placed element (WHAT) |
| pp_order_node | Verb audit trail (HOW) |
| co_empty_space / co_empty_space_line | Alignment records (WHERE) |
| elements_meta | Flat element list (guid, discipline, ifc_class, storey) |
| elements_rtree | Spatial R-tree index (metres) |
| base_geometries | Vertex/face BLOBs |
| element_transforms | Position (cx, cy, cz) |

## Cross-DB Bridging
- `ATTACH DATABASE` pattern for cross-DB joins (ComponentLibrary, BuildingInspector, PlacementProver)
- BOM leaf -> mesh: `m_bom_line.child_product_id` -> `M_Product` -> `M_Product_Image` -> `LOD_Object`
- CO_EmptySpace stores mm; output R*Tree stores metres (multiply by 1000.0)
