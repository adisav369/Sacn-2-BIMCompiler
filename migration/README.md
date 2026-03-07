# Migration Scripts

SQL scripts for `library/BOM.db` and `library/component_library.db` changes. Apply in order.

## Legacy (component_library.db)

| Script | Phase | Description |
|---|---|---|
| `108B_furniture_type_routing.sql` | 108B | ad_space_type_furniture (34 rows), ad_space_adjacency (15 rows) |
| `108C_metadata_gap_fixes.sql` | 108C | Fix 4 orphan names, +3 space types, MEP BOM 100%, openings 92% |
| `108D_remaining_coverage.sql` | 108D | Utility openings, transit/exterior dimensions → 100% |
| `108E_external_adjacency.sql` | 108E | External adjacency rules, ad_space_exterior_rule (24 rows) |
| `109A_furniture_bom_recipes.sql` | 109A | BED_SET, LIVING_SET, DINING_SET BOMs with spatial params |
| `109B_house_templates.sql` | 109B | 4 house unit types, 28 room templates, LANDED_1S template, 2BR_A activation |
| `110_consolidation_cleanup.sql` | 110 | Remove 3 orphaned check entries (fire_lift, protected_stairs, stairwell_pressurization) |
| `migration_phase_DE2_geometry_map.sql` | DE-2 | I_Geometry_Map schema (was ad_geometry_map) + IfcRoof component type |
| `migration_phase_DE3_instance_geometry.sql` | DE-3 | Per-instance geometry: adds building_type, storey, ordinal to I_Geometry_Map |

## Normalisation (BOM.db)

| Script | Phase | Description |
|---|---|---|
| `migration_NORM1_M_Product.sql` | NORM-1 | M_Product table (198 rows) |
| `migration_NORM2_child_product_id.sql` | NORM-2 | m_bom_line child_product_id + allocated_*_mm columns |
| `migration_ST1c_template_bom.sql` | ST-1c | Template BOM for ST mode |
| `migration_X1_bug_fixes.sql` | X1 | DX digest bug fixes (float-epsilon sort, class-level) |
| `migration_SH_absolute_furniture.sql` | SH-ABS | SH absolute furniture placement data |

## P0.1 — BOM Extraction & Product Catalog

| Script | Target DB | Description |
|---|---|---|
| `migration_P01_product_catalog.sql` | BOM.db | M_Product (198 rows), M_Product_Category (36 rows) |
| `migration_P01_placement_product_link.sql` | component_library.db | M_Product_ID column on I_Element_Extraction (was ad_element_placement) |
| `migration_P01_BOM_extracted.sql` | BOM.db | EXT_SH + EXT_DX extracted BOMs (all BUY) |
| `migration_P01_BOM_SH_products.sql` | BOM.db | SH-specific M_Product rows (11) |
| `migration_P01_BOM_SH_placement_link.sql` | component_library.db | SH product→placement links |
| `migration_P01_BOM_precision.sql` | BOM.db | Float-epsilon sort fix for digest |
| `migration_M_Product_Category.sql` | BOM.db | M_Product_Category (36 rows: 4 parents + 29 IFC + 3 assembly) |
| `migration_LOD_pair.sql` | component_library.db | M_Product_Image + LOD_Object tables |

## P0.2 — BOM Walk

| Script | Target DB | Description |
|---|---|---|
| `migration_P02_M_Product_Image_rename.sql` | component_library.db | LOD_key → M_Product_Image rename |
| `migration_P02_bom_walk_columns.sql` | BOM.db | m_bom_line instance columns (storey, element_ref, ordinal, orientation, material) |
| `migration_P02_deactivate_sh_dx.sql` | component_library.db | Deactivate SH/DX in I_Element_Extraction (was ad_element_placement) |

## Gap Closure & Forensic Audit (2026-03-06)

| Script | Target DB | Description |
|---|---|---|
| `migration_SH_M_Product_Image.sql` | component_library.db | SH product image rows (11 entries) |
| `migration_material_rgba_backfill.sql` | BOM.db | Material RGBA backfill for m_bom_line |
| `migration_topology_maker_bootstrap.sql` | BOM.db | TopologyMaker bootstrap (ad_typology_template + ad_ubbl_rule + prefab BOMs) |

## Usage

```bash
# Apply a single migration to BOM.db
sqlite3 library/BOM.db < migration/migration_NORM1_M_Product.sql

# Apply a single migration to component_library.db
sqlite3 library/component_library.db < migration/migration_LOD_pair.sql

# All SQL migrations are idempotent (INSERT OR IGNORE / CREATE IF NOT EXISTS)
```
