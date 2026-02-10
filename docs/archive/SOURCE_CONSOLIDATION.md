# Source Consolidation — Single Reference

Everything lives in one place. This document tells you where.

## Single Source of Truth

**`library/component_library.db`** (127MB, Git LFS) contains:
- 8,449+ component definitions (LOD400 geometry + metadata)
- 35 AD tables (space types, BOM recipes, building templates, MEP rules, fire protection)
- 8,755+ unique geometries (vertices/faces BLOBs)
- All construction knowledge extracted from IFC sources

### Restoring the Library

The library DB is NOT pushed to GitHub (too large for repeated pushes). To restore from scratch:

1. Start with a base `component_library.db` (from initial Git LFS checkout or team share)
2. Run migration scripts in order:
   ```bash
   for f in migration/migration_*.sql; do sqlite3 library/component_library.db < "$f"; done
   ```
3. Run geometry copy scripts if needed:
   ```bash
   python3 scripts/migrate_tank_geometry.py
   ```

Migration scripts are idempotent (INSERT OR IGNORE) — safe to re-run.

## Reference Archives (read-only, no need to re-extract)

| What | Where | Size | Status |
|------|-------|------|--------|
| Source IFC files (23 files) | `archive/IFC_source_files/` | 155MB | Archived |
| Federation DB (terminal SJTII) | `database/enhanced_federation_GI.db` | 235MB | Reference only |
| Duplex spatial structure | `database/Stacked_Duplex.db` | — | Reference only |

## What Was Extracted from Each Source

### Terminal SJTII Federation (8 disciplines, 49,059 elements)

LOD400 components extracted:
- Sprinklers: 892
- Lights: 801
- Pipe fittings: 4,200
- Duct fittings: 683
- Doors: 112
- Windows: 183
- Furniture: 174
- Columns: 122
- Beams: 404
- Structural members: 382
- Valves: 111
- Alarms: 71
- Diffusers: 268
- Fixtures: 253
- Appliances: 19
- Controllers: 6
- Railings: 34
- Stairs: 32
- Water tanks: 3 FRP variants (Phase 113)
- Property sets: 88 (Pset_* and Qto_*)
- Spatial structure: 22 storeys across terminal + observatory

### IFC Research Files (4 collections, 23 files)

| Collection | Key extractions |
|------------|----------------|
| youshengCode | Duplex house (architecture + MEP), Castle, Revit samples → doors, stairs, residential fixtures |
| buildingSMART | PCERT certification samples → MEP system patterns, structural coordination |
| steptools | KIT FZK-Haus, furniture catalog → residential furniture, house geometry |
| bim_whale | BasicHouse (51MB) → comprehensive house component set |

## Extraction Scripts (for reference, not re-run)

| Script | Purpose |
|--------|---------|
| `scripts/extract_all_components.py` | Federation → library |
| `scripts/import_ifc_furniture.py` | IFC → library (via ifcopenshell) |
| `scripts/create_ad_*.py` (8 scripts) | AD table creation |
| `migration/*.sql` (10 scripts, phases 108B–113) | Incremental metadata additions |
| `scripts/migrate_tank_geometry.py` | Cross-DB geometry blob copy |

## AD Tables Summary

35 total tables, 30 actively consumed by Java:

| Table | Purpose | Consumed |
|-------|---------|----------|
| `ad_space_type` | Room/space definitions | Yes |
| `ad_space_type_mep_bom` | MEP equipment per room | Yes |
| `ad_mep_profile` | MEP system parameters | Yes |
| `ad_opening_family` | Door/window families | Yes |
| `ad_space_type_opening` | Openings per room type | Yes |
| `ad_building_template` | Building type definitions | Yes |
| `ad_building_bom` | Floor composition per building | Yes |
| `ad_floor_type` | Floor type definitions | Yes |
| `ad_unit_type` | Unit type definitions | Yes |
| `ad_unit_type_room` | Room layouts per unit | Yes |
| `ad_bom` | BOM recipe headers | Yes |
| `ad_bom_child` | BOM recipe children | Yes |
| `ad_bom_child_param` | BOM child parameters | Yes |
| `ad_fp_trigger` | Fire protection triggers | Yes |
| `ad_fp_coverage` | FP coverage patterns | Yes |
| `ad_dimensions` | Standard dimensions | Yes |
| ... | (15 more) | Yes |
| `ad_check_applicability` | Dynamic check loading | **No** (orphaned) |
| `ad_check_threshold` | Check threshold config | **No** (orphaned) |
| `ad_reference` | External references | **No** (orphaned) |
| `ad_space_adjacency` | Room adjacency rules | **No** (future) |
| `ad_space_exterior_rule` | Exterior wall rules | **No** (orphaned) |

## Component Library Structure

```
component_types (21 types)
  └── component_definitions (8,449+ definitions)
        └── component_geometries (8,755+ unique meshes)
              ├── vertices BLOB (float32 arrays)
              ├── faces BLOB (int32 arrays)
              └── normals BLOB (float32 arrays, optional)
```

Types span: sprinklers, lights, diffusers, pipe fittings, duct fittings, columns, beams, members, furniture, valves, alarms, appliances, sensors, controllers, doors, windows, stairs, railings, fixtures, pipe segments, water tanks.
