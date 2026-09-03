# Schema Quick-Reference (2026-03-26)

## {PREFIX}_BOM.db — 10 tables (read-only dictionary, per building)
| Table | Rows (SH) | Key Columns | Role |
|-------|-----------|-------------|------|
| m_bom | 9 active | bom_id, bom_name, bom_type, m_product_category_id, AD_Org_ID, entity_type | Assembly headers |
| m_bom_line | 39 active | bom_id, child_product_id, dx/dy/dz, rotation_rule, component_type | Child placements (recipe type lines) |
| M_Product | 11 | product_id, ifc_class, width/depth/height (metres) | Product catalog (transitional copy) |
| M_AttributeSet | varies | attribute_set_id, name | ASI template |
| M_AttributeSetInstance | varies | M_AttributeSetInstance_ID, M_AttributeSet_ID | Per-instance header |
| M_AttributeInstance | varies | M_AttributeSetInstance_ID, attr_name, attr_value | Per-instance values |
| C_DocType | 1 | C_DocType_ID, ExpectedElements, Provenance | Building config |
| ad_sysconfig | 2 | config_key, config_value | Integrity hash, expected elements |
| m_bom_line_ma | varies | bom_line_id, lot_no | Attribute tracking |

## component_library.db — 77 tables (product LOD catalog + extraction archive)
| Table | Rows | Role |
|-------|------|------|
| M_Product | 2,475 | Master product catalog (persistent, reused across buildings) |
| M_Product_Image | 2,472 | product_id → geometry_hash mapping |
| I_Element_Extraction | varies | IFC extraction archive (world-space AABB per element) |
| component_definitions | 24,004 | LOD attachments (orientation, attachment_face, product_category) |
| component_geometries | 51,673 | Raw vertex/face BLOBs |
| component_types | 55 | Product type classification |
| surface_styles | 80 | Material RGBA colors |

## ERP.db — 32 tables (shared discipline metadata)
| Table | Rows | Role |
|-------|------|------|
| M_Product_Category | 117 | Flat classification (RE, CO, GF, LIVING, ARC, STR...) |
| AD_Org | 16 | Discipline definitions (ARC, STR, FP, ELEC, ACMV...) |
| ad_val_rule | 415 | Validation rules (dimensional, compliance, relational) |
| ad_space_type | varies | Space type definitions |
| ad_element_mep | varies | MEP element types |
| placement_rules | varies | Placement constraint rules |

## output.db (generated per compile)
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
- BOM leaf → mesh: `m_bom_line.child_product_id` → `M_Product` → `M_Product_Image` → geometry
- CO_EmptySpace stores mm; output R*Tree stores metres (multiply by 1000.0)
- Discipline routing: `AD_Org_ID` (integer FK to AD_Org) on m_bom and C_OrderLine
