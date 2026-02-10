# Migration Scripts

SQL scripts for `library/component_library.db` changes. Apply in order.

| Script | Phase | Description |
|---|---|---|
| `108B_furniture_type_routing.sql` | 108B | ad_space_type_furniture (34 rows), ad_space_adjacency (15 rows) |
| `108C_metadata_gap_fixes.sql` | 108C | Fix 4 orphan names, +3 space types, MEP BOM 100%, openings 92% |
| `108D_remaining_coverage.sql` | 108D | Utility openings, transit/exterior dimensions → 100% |
| `108E_external_adjacency.sql` | 108E | External adjacency rules, ad_space_exterior_rule (24 rows) |
| `109A_furniture_bom_recipes.sql` | 109A | BED_SET, LIVING_SET, DINING_SET BOMs with spatial params |
| `109B_house_templates.sql` | 109B | 4 house unit types, 28 room templates, LANDED_1S template, 2BR_A activation |
| `110_consolidation_cleanup.sql` | 110 | Remove 3 orphaned check entries (fire_lift, protected_stairs, stairwell_pressurization) |

## Usage

```bash
# Apply a single migration
sqlite3 library/component_library.db < migration/108B_furniture_type_routing.sql

# All migrations are idempotent (INSERT OR IGNORE / INSERT OR REPLACE)
```
