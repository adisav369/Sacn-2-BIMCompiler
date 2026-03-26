# iDempiere _ID / Name / Value Convention — Impact Study

> **Analysis only. No code changes.**
> Reference: https://wiki.idempiere.org/en/Columns#Standard_Columns
> Date: 2026-03-26

## Summary

iDempiere convention: every table has three identity columns:
- `_ID` (INTEGER PRIMARY KEY) — surrogate key, referenced by other tables as `_FK`
- `Name` (TEXT NOT NULL) — human-readable label
- `Value` (TEXT NOT NULL) — SearchKey, unique business identifier

Our 4-DB split uses a mix of conventions. Most tables use **TEXT primary keys** (business identifiers as PK) and lack a separate `_ID` surrogate. Only a few tables follow iDempiere convention closely.

---

## 1. Column Inventory by Database

### 1.1 BOM DB (`{PREFIX}_BOM.db` — e.g., SH_BOM.db)

| Table | Has _ID | Has Name | Has Value | Current PK | PK Type |
|-------|---------|----------|-----------|------------|---------|
| C_DocType | No | Yes | No | C_DocType_ID (TEXT) | TEXT |
| M_AttributeInstance | Yes (INTEGER) | Yes | Yes | M_AttributeInstance_ID | INTEGER |
| M_AttributeSet | No | Yes | No | M_AttributeSet_ID (TEXT) | TEXT |
| M_AttributeSetInstance | Yes (INTEGER) | No | No | M_AttributeSetInstance_ID | INTEGER |
| M_Product | No | No | No | product_id (TEXT) | TEXT |
| ad_sysconfig | Yes (INTEGER) | No | No | id | INTEGER |
| m_bom | No | Yes (bom_name) | No | bom_id (TEXT) | TEXT |
| m_bom_line | Yes (INTEGER) | No | No | bom_child_id | INTEGER |
| m_bom_line_ma | No | No | No | (bom_id, sequence, qi) | COMPOSITE |

**Notes:**
- `M_AttributeInstance` is the only table with all three (_ID, Name, Value) ✓
- `M_Product` uses `product_id TEXT` — divergent from iDempiere `M_Product_ID INTEGER`
- `m_bom` uses `bom_id TEXT` — divergent from iDempiere `M_BOM_ID INTEGER`

### 1.2 ERP DB (`library/ERP.db`)

| Table | Has _ID | Has Name | Has Value | Current PK | PK Type |
|-------|---------|----------|-----------|------------|---------|
| AD_Org | Yes (INTEGER) | Yes | Yes | AD_Org_ID | INTEGER ✓ |
| AD_SysConfig | No | Yes (as PK) | Yes | Name (TEXT) | TEXT |
| M_Product | No | No | No | product_id (TEXT) | TEXT |
| M_Product_Category | No | Yes | No | M_Product_Category_ID (TEXT) | TEXT |
| W_Calibration_Result | Yes (INTEGER) | No | No | id | INTEGER |
| W_Validation_Advisory | Yes (INTEGER) | No | No | advisory_id | INTEGER |
| ad_assembly_connector | Yes (INTEGER) | No | No | connector_id | INTEGER |
| ad_assembly_manifest | Yes (INTEGER) | No | No | manifest_id | INTEGER |
| ad_building_profile | Yes (INTEGER) | No | No | ad_building_profile_id | INTEGER |
| ad_code_requirement | No | No | No | (code_id, clause, element_type, space_type) | COMPOSITE |
| ad_element_mep | No | No | No | element_type (TEXT) | TEXT |
| ad_element_mep_alias | Yes (INTEGER) | No | No | alias_id | INTEGER |
| ad_fp_coverage | No | No | No | hazard_class (TEXT) | TEXT |
| ad_fp_trigger | No | No | No | trigger_id (TEXT) | TEXT |
| ad_ifc_class_map | No | No | No | ifc_class (TEXT) | TEXT |
| ad_room_slot | Yes (INTEGER) | No | No | slot_id | INTEGER |
| ad_space_adjacency | No | No | No | (space_type_a, space_type_b) | COMPOSITE |
| ad_space_dim | No | No | No | space_type (TEXT) | TEXT |
| ad_space_exterior_rule | No | No | No | space_type_id (TEXT) | TEXT |
| ad_space_type | No | No | No | space_type_id (TEXT) | TEXT |
| ad_space_type_mep | No | No | No | space_type_id (TEXT) | TEXT |
| ad_space_type_mep_bom | No | No | No | (space_type_id, mep_product_id) | COMPOSITE |
| ad_space_type_opening | No | No | No | (space_type_id, opening_role, family_id) | COMPOSITE |
| ad_val_rule | Yes (INTEGER) | No | No | ad_val_rule_id | INTEGER |
| ad_val_rule_param | Yes (INTEGER) | No | No | ad_val_rule_param_id | INTEGER |
| ad_wall_face | Yes (INTEGER) | No | No | id | INTEGER |
| bad_discipline_priority | No | No | No | (higher_discipline, lower_discipline) | COMPOSITE |
| bad_rule | No | Yes (rule_name) | No | rule_id (TEXT) | TEXT |
| bad_rule_category | No | Yes (category_name) | No | category_id (TEXT) | TEXT |
| bad_rule_param | No | No | No | (rule_id, param_key) | COMPOSITE |
| placement_rules | Yes (INTEGER) | No | No | id | INTEGER |

**Notes:**
- `AD_Org` is the only table fully conformant (_ID INTEGER, Name, Value) ✓
- `AD_SysConfig` uses Name as PK and has Value — similar to iDempiere but inverted (iDempiere uses AD_SysConfig_ID as PK)
- Most `ad_*` tables use TEXT business-key PKs

### 1.3 Component Library (`library/component_library.db`)

| Table | Has _ID | Has Name | Has Value | Current PK | PK Type |
|-------|---------|----------|-----------|------------|---------|
| I_Geometry_Map | Yes (INTEGER) | No | No | id | INTEGER |
| M_Product | No | No | No | product_id (TEXT) | TEXT |
| M_Product_Category | No | Yes | No | M_Product_Category_ID (TEXT) | TEXT |
| M_Product_Image | No | No | No | M_Product_ID (TEXT) | TEXT |
| ad_assembly_connector | Yes (INTEGER) | No | No | connector_id | INTEGER |
| ad_assembly_manifest | Yes (INTEGER) | No | No | manifest_id | INTEGER |
| ad_beam_type | No | No | No | beam_type_id (TEXT) | TEXT |
| ad_beam_type_rule | Yes (INTEGER) | No | No | rule_id | INTEGER |
| ad_building | Yes (INTEGER) | Yes | No | id | INTEGER |
| ad_building_assertions | No | No | No | (building_id, assertion_id) | COMPOSITE |
| ad_building_bom | Yes (INTEGER) | No | No | bom_id | INTEGER |
| ad_building_code | No | Yes (code_name) | No | code_id (TEXT) | TEXT |
| ad_building_grid | Yes (INTEGER) | No | No | id | INTEGER |
| ad_building_registry | No | Yes (building_name) | No | building_id (TEXT) | TEXT |
| ad_building_storey | No | No | No | (building_type, storey_name) | COMPOSITE |
| ad_building_template | No | Yes (template_name) | No | template_id (TEXT) | TEXT |
| ad_check_applicability | No | Yes (check_name) | No | check_id (TEXT) | TEXT |
| ad_check_threshold | No | Yes (threshold_name) | No | threshold_id (TEXT) | TEXT |
| ad_code_requirement | No | No | No | (code_id, clause, element_type, space_type) | COMPOSITE |
| ad_column_type | No | No | No | column_type_id (TEXT) | TEXT |
| ad_column_type_rule | Yes (INTEGER) | No | No | rule_id | INTEGER |
| ad_compiler_config | Yes (INTEGER) | No | No | id | INTEGER |
| ad_covering_type | No | Yes (covering_name) | No | covering_type_id (TEXT) | TEXT |
| ad_egress_travel | No | No | No | travel_id (TEXT) | TEXT |
| ad_element_dependency | Yes (INTEGER) | No | No | id | INTEGER |
| ad_element_mep | No | No | No | element_type (TEXT) | TEXT |
| ad_element_placement | Yes (INTEGER) | No | No | placement_id | INTEGER |
| ad_element_rule | Yes (INTEGER) | No | No | id | INTEGER |
| ad_elevator_requirement | No | No | No | req_id (TEXT) | TEXT |
| ad_fire_compartment | No | No | No | compartment_id (TEXT) | TEXT |
| ad_fire_riser_requirement | No | No | No | req_id (TEXT) | TEXT |
| ad_floor_type | No | Yes (floor_name) | No | floor_type_id (TEXT) | TEXT |
| ad_floor_type_rule | Yes (INTEGER) | No | No | rule_id | INTEGER |
| ad_fp_coverage | No | No | No | hazard_class (TEXT) | TEXT |
| ad_fp_trigger | No | No | No | trigger_id (TEXT) | TEXT |
| ad_jurisdiction_codes | No | No | No | (jurisdiction, code_id) | COMPOSITE |
| ad_material_thermal | No | No | No | material_name (TEXT) | TEXT |
| ad_mep_profile | No | No | No | profile_id (TEXT) | TEXT |
| ad_opening_family | No | Yes (family_name) | No | family_id (TEXT) | TEXT |
| ad_parametric_mesh | No | No | No | mesh_type (TEXT) | TEXT |
| ad_parametric_mesh_param | No | No | No | (mesh_type, param_key) | COMPOSITE |
| ad_placement_rule | No | No | No | rule_id (TEXT) | TEXT |
| ad_pressurization_trigger | No | No | No | trigger_id (TEXT) | TEXT |
| ad_railing_type | No | Yes (railing_name) | No | railing_type_id (TEXT) | TEXT |
| ad_ref_value | No | Yes (value_name) | No | (ref_id, value_id) | COMPOSITE |
| ad_reference | No | Yes (ref_name) | No | ref_id (TEXT) | TEXT |
| ad_roof_preset | No | No | No | preset_id (TEXT) | TEXT |
| ad_room_boundary | Yes (INTEGER) | No | No | id | INTEGER |
| ad_room_slot | Yes (INTEGER) | No | No | slot_id | INTEGER |
| ad_slab_spec | No | No | No | (building_type, slab_role) | COMPOSITE |
| ad_space_adjacency | No | No | No | (space_type_a, space_type_b) | COMPOSITE |
| ad_space_dim | No | No | No | space_type (TEXT) | TEXT |
| ad_space_exterior_rule | No | No | No | space_type_id (TEXT) | TEXT |
| ad_space_type | No | No | No | space_type_id (TEXT) | TEXT |
| ad_space_type_alias | No | No | No | alias (TEXT) | TEXT |
| ad_space_type_furniture | No | No | No | space_type_id (TEXT) | TEXT |
| ad_space_type_mep | No | No | No | space_type_id (TEXT) | TEXT |
| ad_space_type_mep_bom | No | No | No | (space_type_id, mep_product_id) | COMPOSITE |
| ad_space_type_opening | No | No | No | (space_type_id, opening_role, family_id) | COMPOSITE |
| ad_stair_requirement | No | No | No | req_id (TEXT) | TEXT |
| ad_unit_type | No | Yes (unit_name) | No | unit_type_id (TEXT) | TEXT |
| ad_unit_type_room | No | No | No | (unit_type_id, room_key) | COMPOSITE |
| ad_vert_circ_trigger | No | No | No | trigger_id (TEXT) | TEXT |
| ad_wall_face | Yes (INTEGER) | No | No | id | INTEGER |
| ad_wall_type | No | No | No | wall_type_id (TEXT) | TEXT |
| ad_wall_type_rule | Yes (INTEGER) | No | No | rule_id | INTEGER |
| bad_discipline_priority | No | No | No | (higher_discipline, lower_discipline) | COMPOSITE |
| bad_rule | No | Yes (rule_name) | No | rule_id (TEXT) | TEXT |
| bad_rule_category | No | Yes (category_name) | No | category_id (TEXT) | TEXT |
| bad_rule_param | No | No | No | (rule_id, param_key) | COMPOSITE |
| component_definitions | Yes (INTEGER) | Yes | No | id | INTEGER |
| component_geometries | No | No | No | geometry_hash (TEXT) | TEXT |
| component_types | Yes (INTEGER) | No | No | id | INTEGER |
| material_layers | No | No | No | (layer_set_name, sequence) | COMPOSITE |
| placement_rules | Yes (INTEGER) | No | No | id | INTEGER |
| surface_styles | No | No | No | style_name (TEXT) | TEXT |

### 1.4 Output DB (`library/output_template.db`)

| Table | Has _ID | Has Name | Has Value | Current PK | PK Type |
|-------|---------|----------|-----------|------------|---------|
| W_Verb_Node | Yes (INTEGER) | Yes | No | W_Verb_Node_ID | INTEGER |
| W_Verb_NodeProduct | Yes (INTEGER) | Yes | Yes | W_Verb_NodeProduct_ID | INTEGER ✓ |
| c_order | No | Yes | No | C_Order_ID (TEXT) | TEXT |
| c_orderline | Yes (INTEGER) | Yes | No | C_OrderLine_ID | INTEGER |
| co_empty_space | *(removed S74 — W008)* | — | — | — | — |
| co_empty_space_line | *(removed S74 — W008)* | — | — | — | — |
| element_assemblies | No | Yes | No | assembly_guid (TEXT) | TEXT |
| element_instances | No | No | No | guid (TEXT) | TEXT |
| element_properties | No | No | No | (guid, pset_name, property_name) | IMPLICIT |
| element_transforms | No | No | No | guid (TEXT) | TEXT |
| elements_meta | Yes (INTEGER) | No | No | id | INTEGER |
| elements_rtree | No | No | No | id (INT) | INTEGER |
| material_layers | No | No | No | (layer_set_name, sequence) | COMPOSITE |
| mep_systems | No | No | No | system_id (TEXT) | TEXT |
| rel_contained_in_space | No | No | No | element_guid (TEXT) | TEXT |
| simple_qto | Yes (INTEGER) | No | No | id | INTEGER |
| spatial_structure | No | Yes | No | guid (TEXT) | TEXT |
| surface_styles | No | No | No | style_name (TEXT) | TEXT |
| system_edges | No | No | No | edge_id (TEXT) | TEXT |
| system_nodes | No | Yes | No | node_id (TEXT) | TEXT |
| assembly_components | No | No | No | (assembly_guid, component_guid) | IMPLICIT |
| base_geometries | No | No | No | geometry_hash (TEXT) | TEXT |
| _schema_guide | No | No | No | (table_name) | TEXT |

**Notes:**
- `W_Verb_NodeProduct` is fully conformant (_ID INTEGER, Name, Value) ✓
- `c_order` uses `C_Order_ID TEXT` — divergent from iDempiere `C_Order_ID INTEGER`

### 1.5 Validation DB (`library/validation.db`)

| Table | Has _ID | Has Name | Has Value | Current PK | PK Type |
|-------|---------|----------|-----------|------------|---------|
| AD_Clash_Rule | Yes (INTEGER) | No | No | ad_clash_rule_id | INTEGER |
| AD_Occupancy_Class | Yes (INTEGER) | Yes | No | ad_occupancy_class_id | INTEGER |
| AD_Val_Rule | Yes (INTEGER) | Yes | No | ad_val_rule_id | INTEGER |
| AD_Val_Rule_Exception | Yes (INTEGER) | No | No | ad_val_rule_exception_id | INTEGER |
| AD_Val_Rule_Mining_Source | No | No | No | (ad_val_rule_id, building_type) | COMPOSITE |
| AD_Val_Rule_Occupancy | No | No | No | (ad_val_rule_id, ad_occupancy_class_id) | COMPOSITE |
| AD_Val_Rule_Param | Yes (INTEGER) | Yes | Yes | ad_val_rule_param_id | INTEGER |
| AD_Validation_Result | Yes (INTEGER) | No | No | ad_validation_result_id | INTEGER |
| ad_pattern_rule | Yes (INTEGER) | No | No | id | INTEGER |

**Notes:**
- `AD_Val_Rule_Param` has _ID, name, value — fully conformant ✓
- Validation tables are the most iDempiere-aligned overall

---

## 2. Tables with Non-Integer PKs (Most Divergent)

These tables use TEXT as PK — the most divergent from iDempiere convention:

### Critical (high-traffic, many FK references)

| Table | DB | Current PK | Referenced by | Java file count |
|-------|----|-----------|---------------|-----------------|
| **M_Product** | BOM, ERP, CL | `product_id TEXT` | m_bom_line.child_product_id, c_orderline.M_Product_ID, etc. | 29 |
| **m_bom** | BOM | `bom_id TEXT` | m_bom_line.bom_id, m_bom_line_ma.bom_id | 37 |
| **c_order** | Output | `C_Order_ID TEXT` | c_orderline.C_Order_ID, W_Verb_Node.C_Order_ID | 19 |
| **C_DocType** | BOM | `C_DocType_ID TEXT` | c_order.C_DocType_ID | 14 |

### Medium (referenced but fewer dependents)

| Table | DB | Current PK |
|-------|----|-----------|
| M_Product_Category | ERP, CL | `M_Product_Category_ID TEXT` |
| M_AttributeSet | BOM | `M_AttributeSet_ID TEXT` |
| ad_space_type | ERP, CL | `space_type_id TEXT` |
| ad_wall_type | CL | `wall_type_id TEXT` |
| ad_element_mep | ERP, CL | `element_type TEXT` |
| ad_building_registry | CL | `building_id TEXT` |
| ad_building_template | CL | `template_id TEXT` |
| ad_opening_family | CL | `family_id TEXT` |
| ad_building_code | CL | `code_id TEXT` |

### Low (reference/config tables, rarely joined)

All remaining `ad_*` tables with TEXT PKs: `ad_beam_type`, `ad_column_type`, `ad_covering_type`, `ad_floor_type`, `ad_unit_type`, `ad_railing_type`, `ad_fp_coverage`, `ad_fp_trigger`, `ad_parametric_mesh`, `ad_placement_rule`, `ad_material_thermal`, `ad_mep_profile`, `ad_roof_preset`, `ad_egress_travel`, `ad_elevator_requirement`, `ad_fire_compartment`, `ad_fire_riser_requirement`, `ad_pressurization_trigger`, `ad_stair_requirement`, `ad_vert_circ_trigger`, `bad_rule`, `bad_rule_category`, `component_geometries`, `surface_styles`, etc.

---

## 3. Impact Assessment — Adding Missing Name/Value Columns

### 3.1 Tables That Would Need `Name` Column Added

Tables with INTEGER PK but no `Name`:

| Table | DB | Impact |
|-------|----|--------|
| M_AttributeSetInstance | BOM | Low — Description serves as label |
| ad_sysconfig (BOM) | BOM | Low — config_key serves as Name |
| W_Calibration_Result | ERP | Low — discipline+storey serves as label |
| W_Validation_Advisory | ERP | Low — message serves as label |
| ad_building_profile | ERP | Low — building_type serves |
| ad_element_mep_alias | ERP | Low — canonical_type+match_value serves |
| ad_val_rule / param | ERP | Already has rule_name / param_name |
| ad_wall_face | ERP, CL | room_name+face serves |
| placement_rules | ERP, CL | Low — host_type serves |
| I_Geometry_Map | CL | Low — element_ref serves |
| ad_building_bom | CL | Low — template_id+floor_type_id serves |
| ad_building_grid | CL | grid_label serves |
| ad_compiler_config | CL | config_key serves |
| ad_element_placement | CL | element_ref serves |
| ad_element_rule | CL | element_ref serves |
| component_types | CL | ifc_class+category serves |
| co_empty_space | *(removed S74 — W008)* | — |
| co_empty_space_line | *(removed S74 — W008)* | — |
| elements_meta | Output | element_name serves |
| simple_qto | Output | ifc_class+storey serves |
| AD_Clash_Rule | Val | Low |
| AD_Validation_Result | Val | Low |
| ad_pattern_rule | Val | rule_name serves |

**Assessment:** Most tables have a semantic name column (e.g., `rule_name`, `element_ref`, `config_key`) that serves the Name role. Strict iDempiere conformance would rename these to `Name`, but this is a cosmetic change with massive blast radius.

### 3.2 Tables That Would Need `Value` (SearchKey) Column Added

Almost no tables have `Value` as a SearchKey. Only 3 conform:
- `AD_Org` ✓
- `M_AttributeInstance` ✓ (though this is attribute value, not SearchKey)
- `W_Verb_NodeProduct` ✓

**Assessment:** Adding Value/SearchKey to all tables is the largest gap. Most tables use the TEXT PK itself as the business identifier (e.g., `bom_id = 'SH-BUILDING'`), making the PK serve double duty as both surrogate and SearchKey.

### 3.3 Java PO Classes That Would Need Changes

No formal iDempiere-style PO (Persistent Object) classes exist. The project uses:
- Direct JDBC with SQL strings in:
  - `BomDropper.java` — C_Order, C_OrderLine INSERTs
  - `ElementPersistence.java` — elements_meta, base_geometries, element_instances, etc.
  - `BuildingWriter.java` — surface_styles, material_layers, simple_qto, element_properties
  - `BOMAssembly.java` — element_assemblies, assembly_components
  - `BOMBuilder.java` — elements_meta, assembly_variants
  - `BOMTypeSystem.java` — bom_types, bom_type_components, bom_instances
  - `FloorStructuralAssembler.java` — element_assemblies, assembly_components
  - `FloorAssemblyBuilder.java` — elements_meta, floor_templates
  - `StairWriter.java` / `StairLibraryMapper.java` — element_instances, base_geometries
  - `SpatialStructureBuilder.java` — spatial_structure, rel_contained_in_space
  - `IDempiereExporter.java` — idempiere_product_map, idempiere_bom_map

The iDempiere-named tables (`M_Product`, `m_bom`, `m_bom_line`, `C_Order`, `C_OrderLine`, `C_DocType`, `M_AttributeSet*`, `W_Verb_Node*`) are the ones that would be migrated. The `ad_*` and output tables use their own convention.

### 3.4 Migrations Needed

If TEXT PKs were converted to INTEGER `_ID` + `Value TEXT` SearchKey:

| Table | Migration | Risk |
|-------|-----------|------|
| M_Product | Add `M_Product_ID INTEGER`, keep `product_id` as `Value` | **EXTREME** — 29 Java files, all BOM DBs, all output DBs |
| m_bom | Add `M_BOM_ID INTEGER`, keep `bom_id` as `Value` | **EXTREME** — 37 Java files, all BOM DBs |
| m_bom_line | Already has `bom_child_id INTEGER` PK | Rename to `M_BOM_Line_ID` only |
| c_order | Change `C_Order_ID TEXT` → `C_Order_ID INTEGER` + `Value TEXT` | **HIGH** — 19 Java files |
| C_DocType | Change `C_DocType_ID TEXT` → `C_DocType_ID INTEGER` + `Value TEXT` | **HIGH** — 14 Java files |
| M_Product_Category | Add `_ID INTEGER`, keep current as `Value` | Medium — 6 files |
| M_AttributeSet | Change TEXT → INTEGER | Low — 4 files |

### 3.5 Test Assertions at Risk

Tests that embed schema assumptions:
- `RemoveCompressTest.java` — hardcoded INSERT SQL for m_bom, m_bom_line
- `BomDropperOrderIdTest.java` — hardcoded INSERT SQL for m_bom, m_bom_line
- `OrderInheritanceTest.java` — hardcoded INSERT SQL for m_bom, m_bom_line, C_Order, C_OrderLine
- `RosettaStoneGateTest.java` — G1-G6 gate definitions (Sacred File)

---

## 4. Name-Based FK Joins (Should Be _ID-Based)

### 4.1 TEXT-to-TEXT FK References (Name-Based Joins)

These REFERENCES clauses join on TEXT columns rather than INTEGER _ID:

**In BOM DB:**
- `m_bom_line.bom_id TEXT → m_bom.bom_id TEXT`
- `m_bom_line_ma.bom_id TEXT → m_bom.bom_id TEXT`
- `M_AttributeSetInstance.M_AttributeSet_ID TEXT → M_AttributeSet.M_AttributeSet_ID TEXT`

**In ERP DB:**
- `M_Product.M_Product_Category_ID TEXT → M_Product_Category.M_Product_Category_ID TEXT`
- `ad_element_mep_alias.canonical_type TEXT → ad_element_mep.element_type TEXT`
- `ad_val_rule_param.ad_val_rule_id INTEGER → ad_val_rule.ad_val_rule_id INTEGER` ✓ (correct)
- `bad_rule.category_id TEXT → bad_rule_category TEXT`
- `bad_rule_param.rule_id TEXT → bad_rule TEXT`
- `bad_rule.override_rule_id TEXT → bad_rule TEXT`

**In Component Library:**
- `I_Geometry_Map.geometry_hash TEXT → component_geometries.geometry_hash TEXT`
- `M_Product_Category.Parent_Category_ID TEXT → M_Product_Category.M_Product_Category_ID TEXT`
- `ad_beam_type_rule.beam_type_id TEXT → ad_beam_type TEXT`
- `ad_building_assertions.building_id TEXT → ad_building_registry.building_id TEXT`
- `ad_building_bom.template_id TEXT → ad_building_template.template_id TEXT`
- `ad_building_bom.floor_type_id TEXT → ad_floor_type.floor_type_id TEXT`
- `ad_building_grid.building_id INTEGER → ad_building.id INTEGER` ✓ (correct)
- `ad_check_threshold.check_id TEXT → ad_check_applicability.check_id TEXT`
- `ad_code_requirement.code_id TEXT → ad_building_code.code_id TEXT`
- `ad_column_type_rule.column_type_id TEXT → ad_column_type TEXT`
- `ad_jurisdiction_codes.code_id TEXT → ad_building_code.code_id TEXT`
- `ad_parametric_mesh_param.mesh_type TEXT → ad_parametric_mesh TEXT`
- `ad_roof_preset.mesh_type TEXT → ad_parametric_mesh TEXT`
- `ad_space_type_alias.space_type_id TEXT → ad_space_type.space_type_id TEXT`
- `ad_space_type_mep.space_type_id TEXT → ad_space_type.space_type_id TEXT`
- `ad_space_type_opening.family_id TEXT → ad_opening_family.family_id TEXT`
- `component_definitions.type_id INTEGER → component_types.id INTEGER` ✓ (correct)
- `placement_rules.component_id INTEGER → component_definitions.id INTEGER` ✓ (correct)

**In Output DB:**
- `W_Verb_Node.C_Order_ID TEXT → c_order.C_Order_ID TEXT`
- *(co_empty_space tables removed S74 — W008)*
- `element_instances.geometry_hash TEXT → base_geometries.geometry_hash TEXT`
- `element_transforms.guid TEXT → elements_meta.guid TEXT`
- `rel_contained_in_space.space_guid TEXT → spatial_structure.guid TEXT`
- `system_nodes.system_id TEXT → mep_systems.system_id TEXT`
- `system_edges.system_id TEXT → mep_systems.system_id TEXT`
- `system_edges.from_node_id TEXT → system_nodes.node_id TEXT`
- `system_edges.to_node_id TEXT → system_nodes.node_id TEXT`
- `W_Verb_NodeProduct.W_Verb_Node_ID INTEGER → W_Verb_Node.W_Verb_Node_ID INTEGER` ✓

### 4.2 Cross-DB Implicit Joins (No Declared FK)

These are joined via shared TEXT keys across databases at runtime:
- `m_bom_line.child_product_id TEXT` → `M_Product.product_id TEXT` (BOM → CL/ERP)
- `c_orderline.M_Product_ID TEXT` → `M_Product.product_id TEXT` (Output → CL/ERP)
- `c_order.C_DocType_ID TEXT` → `C_DocType.C_DocType_ID TEXT` (Output → BOM)
- `ad_wall_face.wall_type_id TEXT` → `ad_wall_type.wall_type_id TEXT` (ERP → CL)
- Various `building_type TEXT` joins across all DBs (implicit, no FK declared)

---

## 5. Scope Estimate

### Total counts

| Metric | Count |
|--------|-------|
| **Tables across all DBs** | ~120 unique (deduplicated across shared schemas) |
| **Tables with TEXT PK (non-integer)** | ~70 |
| **Tables fully conformant (_ID + Name + Value)** | 4 (AD_Org, M_AttributeInstance, W_Verb_NodeProduct, AD_Val_Rule_Param) |
| **Tables with INTEGER PK but missing Name/Value** | ~30 |
| **TEXT-to-TEXT FK references** | ~40 |
| **Java files with INSERT statements** | ~20 |
| **Java files referencing key tables** | ~50+ (many overlap) |
| **Per-building BOM DBs affected** | 34 (all Rosetta Stones) |
| **Migration SQL files needed** | ~15-20 (ALTER TABLE + data migration) |
| **Test files with hardcoded schema** | 3-4 contract tests + RosettaStoneGateTest |

### Migration tiers

**Tier 1 — Quick wins (Name/Value on tables that already have INTEGER PKs):**
- Add `Name TEXT` and `Value TEXT` where missing on INTEGER PK tables
- ~30 ALTER TABLE statements
- Low risk, no FK changes

**Tier 2 — Core ERP tables (TEXT PK → INTEGER PK):**
- `M_Product`, `m_bom`, `c_order`, `C_DocType`, `M_Product_Category`
- Requires FK cascade changes across all 4 DBs + 34 BOM DBs
- ~50+ Java files to update
- **HIGH RISK** — touches Sacred Files (X_M_BOM.java, X_M_BOMLine.java)

**Tier 3 — Config tables (ad_* TEXT PK → INTEGER PK):**
- ~40 `ad_*` tables
- Lower Java impact (mostly read-only from Java)
- Medium risk — mostly migration SQL + seed data updates

### Recommendation

The current TEXT-PK convention is **functional but non-standard**. The TEXT PKs act as both surrogate key and SearchKey, which is simpler but prevents integer-based FK joins and violates iDempiere conventions.

A full migration would be a multi-session effort touching 50+ Java files, 34+ BOM databases, and all migration SQL. The safest approach would be Tier 1 first (add Name/Value to existing INTEGER PK tables), then Tier 2 for core tables in a dedicated branch with comprehensive test coverage.

---

## Appendix: Conformance Scorecard

| Convention | Conformant | Partial | Non-conformant |
|------------|-----------|---------|----------------|
| INTEGER PK | 4 tables (AD_Org, M_Attr*, PP_*) | ~30 (INTEGER PK, missing Name/Value) | ~70 (TEXT PK) |
| Name column | ~20 tables | — | ~100 tables |
| Value/SearchKey | 4 tables | — | ~116 tables |
| INTEGER FK joins | ~8 FK refs | — | ~40 FK refs |
