# Hardcoded Values Audit — DAGCompiler Java Sources
# Date: 2026-02-17

## Categories

- **A**: Metadata EXISTS and is READ, but code has hardcoded FALLBACK (remove fallback)
- **B**: Metadata EXISTS but is NEVER READ (wire the query)
- **C**: NO metadata table exists (create table, populate, wire)

---

## Category A: Metadata Exists, Hardcode is Redundant Fallback

| # | File:Line | Hardcoded Value | Metadata Table | Fix |
|---|-----------|-----------------|----------------|-----|
| A1 | StoreyCompiler:149-164 | Slab thicknesses 0.305, 0.165, 0.127, 0.470 per profile | `ad_floor_type` / `ad_slab_spec` | Query by profile+role |
| A2 | StoreyCompiler:481-482 | Door 0.9m, Window 1.2m, Height 2.1m | `ad_opening_family` (68 rows) | Use family defaults |
| A3 | StoreyCompiler:518, 1180 | Sill height 0.9m / 900mm | `ad_opening_family.sill_height` | Query from family |
| A4 | StoreyCompiler:836,841,976,1109,2633 | "Metal Deck", "Glass Curtain Wall" | `ad_wall_type.ifc_name` | Query by wall type |
| A5 | StoreyCompiler:2661-2724 | "RHS 150x100" frame section + 0.15, 0.1 dims | `ad_wall_type` frame spec | Derive from wall type |
| A6 | MultiUnitCompiler:1372,1515,1598 | Party wall thickness 0.250 × 3 | `ad_wall_type_rule` PARTY | Already queries, just fix fallback |
| A7 | MultiUnitCompiler:1424,1527,1568 | FireRating FRL_60_60_60 × 4 | `ad_wall_type.fire_rating` | Query for PARTY type |
| A8 | MultiUnitCompiler:1408 | "FIRE_RATED_GYPSUM" material | `ad_wall_type_rule` PARTY | Query material from rule |
| A9 | BuildingCompiler:55, MultiUnitCompiler:1243 | Separating slab 0.20 × 2 | `ad_slab_spec` SEPARATING | Query by slab role |
| A10 | StoreyCompiler:1313,1864,2053,2062 | "pendant", "surface" fixture types | `ad_room_slot.fixture_type` | Read from room slot |
| A11 | StoreyCompiler:1196 | Window spacing 0.5m gap | `ad_space_type_opening` | Derive from schedule |
| A12 | StoreyCompiler:1377,1381,1411 | "TOILET", "WC", "KITCHEN" room type strings | `ad_space_type` (37 rows) | Already in SpaceTypeRegistry |

---

## Category B: Metadata Exists, Never Wired

| # | Table | Rows | Purpose | Where It Should Be Used |
|---|-------|------|---------|------------------------|
| B1 | `ad_space_exterior_rule` | 24 | Which rooms need exterior walls | StoreyCompiler: window placement, wall type selection |
| B2 | `ad_space_adjacency` | 22 | Room-to-room adjacency rules | MultiUnitCompiler: room layout validation |
| B3 | `ad_column_type` | 5 | Column section definitions | StoreyCompiler: column generation |
| B4 | `ad_column_type_rule` | 8 | Context → column type | StoreyCompiler: column type selection |
| B5 | `ad_check_applicability` | 33 | Compliance check registry | Future: compliance layer (Gap 1) |
| B6 | `ad_check_threshold` | 31 | Numeric thresholds per check | Future: compliance layer (Gap 1) |
| B7 | `ad_assembly_connector` | 10 | Physical connector points | Future: assembly system |
| B8 | `ad_assembly_manifest` | 37 | Assembly interface declarations | Future: assembly system |
| B9 | `ad_reference` | 5 | Reference data categories | Low priority, just labels |

**Note:** B5-B9 are for future features (compliance layer, assembly system). B1-B4 are relevant NOW for relational migration.

---

## Category C: No Metadata Table Exists

| # | Hardcoded Value | File:Line | New Table Needed | Rows Est. |
|---|-----------------|-----------|------------------|-----------|
| C1 | 18 CIDB cost rates (RM/unit) | BuildingWriter:1198-1212 | `ad_cost_rates(ifc_class, jurisdiction, year, rate)` | ~50 |
| C2 | 20+ furniture name→component patterns | StoreyCompiler:2222-2288 | `ad_furniture_alias(ifc_pattern, component_name)` | ~30 |
| C3 | Occupancy from building name substrings | BuildingCompiler:1253-1259 | Add `occupancy` field to `ad_building_template` | 0 (alter) |
| C4 | Elevator clearances 0.4, 0.5, 0.3 | StoreyCompiler:642-659 | `ad_elevator_spec` or extend `ad_elevator_requirement` | ~5 |
| C5 | Toilet stall dims 1.3×1.2×1.8 | StoreyCompiler:1422-1426 | Extend `ad_room_slot` with stall dimensions | ~5 |
| C6 | Ceiling offset 0.05, landing 0.15, bay slab 0.200 | StoreyCompiler:120,689,803 | `BIMConstants` centralization or `ad_storey_config` | ~10 |
| C7 | Light-column clearance 0.35, margins | StoreyCompiler:3009,3021 | `ad_placement_rule` extension | ~5 |
| C8 | Preferred stair tread 0.267 | StoreyCompiler:2755,2824 | Extend `ad_stair_requirement` with preferred values | ~3 |

---

## Other Issues

### Duplicated Logic
- Discipline→GUID prefix map: BuildingWriter:788 AND ElementPersistence (two copies)
- Occupancy inference: BuildingCompiler:1253 AND :1382 (identical code, two places)
- Profile string matching: 6 sites use `.contains("US_Residential")` etc.

### Path Literal
- `"library/component_library.db"` appears in **33 sites** across all files
- Should be single constant in `CompilerConfig.DB_PATH` or `BIMConstants`

---

## Priority for Relational Migration (SH + Duplex + TB-LKTN)

### Must Fix (directly affects wall/opening/slab placement):
- A1: Slab thicknesses → query `ad_slab_spec`
- A2-A3: Opening dimensions/sill → query `ad_opening_family`
- A4-A5: Wall materials/frame → query `ad_wall_type`
- A6-A9: Party wall/separating slab → query rules
- B1: `ad_space_exterior_rule` → wire for window placement
- B3-B4: `ad_column_type` → wire for column generation

### Defer (non-ARC, future features):
- C1: Cost rates (output formatting, not placement)
- C2: Furniture alias (cosmetic naming, not position)
- C3: Occupancy (fire protection, not ARC placement)
- B5-B8: Compliance/assembly (future phases)

---

*Audit conducted 2026-02-17. To be addressed during Phase RM-1 (Relational Migration).*
