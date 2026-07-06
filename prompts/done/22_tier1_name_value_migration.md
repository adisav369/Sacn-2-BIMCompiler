# DONE — Tier 1: Name/Value columns on INTEGER PK tables
> Commit: 5f94b0ee [S83-tier1]

Tier 1: Add Name and Value (SearchKey) columns to tables that already have
INTEGER PKs but are missing one or both. This is the low-risk first step
toward iDempiere _ID/Name/Value conformance.

Reference: docs/ID_NAME_VALUE_STUDY.md §3.1 (table list), §3.2 (Value gap)

Rules:
- One migration file per DB: DV021 (ERP.db), W011 (output template),
  CL_001 (component_library.db), BOM migration TBD (per-building BOM.db)
- ALTER TABLE ADD COLUMN only — no PK changes, no FK changes, no renames
- For tables that already have a semantic equivalent (e.g. rule_name, config_key,
  element_ref), ADD the standard column AND backfill from the existing column:
  `ALTER TABLE x ADD COLUMN Name TEXT; UPDATE x SET Name = rule_name;`
- For Value/SearchKey: if no natural business key exists, leave Value NULL
  for now. If there IS a natural key (e.g. config_key, building_type),
  backfill it.
- Do NOT rename or drop the original semantic columns — they stay for
  backward compat. The new Name/Value columns run alongside.
- Skip co_empty_space and co_empty_space_line — deprecated (S74), tables
  dropped in W008.
- Skip M_AttributeInstance and AD_Org — already conformant.
- Skip PP_Order_NodeProduct and AD_Val_Rule_Param — already conformant.

After migrations:
1. Run `mvn compile -q` to verify no breakage
2. Query each DB to confirm new columns exist and backfill is correct
3. Report: which tables now have Name, which have Value, which still need
   manual Value assignment

Do NOT modify Java source files. Migration SQL only.
Commit message prefix: [S83-tier1].

# DONE

## Migrations Created
| File | Target DB | Tables Affected |
|------|-----------|-----------------|
| migration/DV021_name_value_erp.sql | library/ERP.db | 10 (W_Calibration_Result, W_Validation_Advisory, ad_assembly_connector, ad_assembly_manifest, ad_building_profile, ad_element_mep_alias, ad_room_slot, ad_val_rule, ad_wall_face, placement_rules) |
| migration/W011_name_value_output.sql | library/output_template.db | 4 (PP_Order_Node, c_orderline, elements_meta, simple_qto) |
| migration/CL002_name_value_component_library.sql | library/component_library.db | 20 (I_Geometry_Map, ad_beam_type_rule, ad_building, ad_building_bom, ad_building_grid, ad_column_type_rule, ad_compiler_config, ad_element_dependency, ad_element_placement, ad_element_rule, ad_floor_type_rule, ad_room_boundary, ad_room_slot, ad_wall_face, ad_wall_type_rule, component_definitions, component_types, placement_rules, ad_assembly_connector, ad_assembly_manifest) |
| migration/BOM001_name_value_bom.sql | per-building BOM DBs | 3 (M_AttributeSetInstance, ad_sysconfig, m_bom_line) |
| migration/V015_name_value_validation.sql | library/validation.db | 6 (AD_Clash_Rule, AD_Occupancy_Class, AD_Val_Rule, AD_Val_Rule_Exception, AD_Validation_Result, ad_pattern_rule) |

## Verification
- All 5 migrations applied cleanly (zero errors)
- `mvn compile -q` passes
- Backfill spot-checked: ad_room_slot, ad_wall_face, m_bom_line, component_types, AD_Val_Rule — correct

## Column Counts After Migration
| DB | Tables w/ Name | Tables w/ Value |
|----|---------------|-----------------|
| ERP.db | 13 (was 3) | 12 (was 2) |
| output_template.db | 7 (was 5) | 4 (was 1) |
| component_library.db | 24 (was 4) | 22 (was 1) |
| SH_BOM.db | 6 (was 4) | 4 (was 1) |
| validation.db | 8 (was 3) | 7 (was 1) |

## Tables Still Needing Manual Value Assignment
- W_Calibration_Result.Value, W_Validation_Advisory.Value — runtime data, assign at insert time
- placement_rules.Value — sparse config table
- Several ad_*_rule tables where Value left NULL (no natural business key)

## Not Touched (Tier 2/3 Scope)
- TEXT PK tables: M_Product, m_bom, c_order, C_DocType, M_Product_Category, ~40 ad_* config tables
- Require PK type change (TEXT→INTEGER) + FK cascade — multi-session effort

## BOM001 Note
Applied to SH_BOM.db as proof. Must be applied to remaining 33 per-building BOM DBs.
Cleanest path: regenerate via run_RosettaStones.sh (code produces data).

## WATCHDOG REVIEW (2026-03-26)
- 5 migrations, 43 tables affected, backfills verified. Clean.
- BOM001 applied to SH only — remaining 33 buildings need re-extract run.

## COMMIT NOW

```bash
git add migration/ library/output_template.db library/component_library.db prompts/22_tier1_name_value_migration.md docs/ID_NAME_VALUE_STUDY.md && git commit -m "[S83-tier1] Name/Value columns on INTEGER PK tables (5 migrations, 43 tables)"
```
