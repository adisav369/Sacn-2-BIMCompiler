# Hardcoded Values Audit — DAGCompiler Java Sources
# Date: 2026-02-17 | Updated: 2026-02-18

## Categories

- **A**: Metadata EXISTS and is READ, but code has hardcoded FALLBACK (remove fallback)
- **B**: Metadata EXISTS but is NEVER READ (wire the query)
- **C**: NO metadata table exists (create table, populate, wire)

---

## Category A: Metadata Exists, Hardcode is Redundant Fallback

| # | Status | Hardcoded Value | Fix Applied |
|---|--------|-----------------|-------------|
| A1 | ✅ DONE | Slab thicknesses 0.305, 0.165, 0.127, 0.470 per profile | `resolveSlabThickness(buildingName)` queries `SlabSpecAD` by building name. Bay slab thickness also resolved. Fallback: `BIMConstants.STANDARD_SLAB_THICKNESS` |
| A2 | ✅ DONE | Door 0.9m, Window 1.2m, Height 2.1m | Fallback uses `BIMConstants.STANDARD_DOOR_WIDTH/HEIGHT`, `STANDARD_WINDOW_WIDTH/HEIGHT` |
| A3 | ✅ DONE | Sill height 0.9m / 900mm | Fallback uses `BIMConstants.STANDARD_SILL_HEIGHT` |
| A4 | ✅ DONE | "Metal Deck", "Glass Curtain Wall" strings × 5 sites | `WallTypeResolver.DEFAULT_WALL_MATERIAL` / `GLASS_CURTAIN_MATERIAL` constants |
| A5 | ✅ DONE | "RHS 150x100" frame section + 0.15, 0.1 dims | `WallTypeResolver.DEFAULT_FRAME_SECTION` / `DEFAULT_FRAME_RAIL_HEIGHT` / `DEFAULT_FRAME_STUD_WIDTH` |
| A6 | ✅ DONE | Party wall thickness 0.250 × 3 sites | Full `WallTypeResolver.resolve("PARTY")` → `ad_wall_type` PARTY_CMU (493mm). Fallback: `BIMConstants.STANDARD_WALL_THICKNESS` |
| A7 | ✅ DONE | FireRating FRL_60_60_60 × 4 sites | Resolved from `ad_wall_type.fire_rating` for PARTY type |
| A8 | ✅ DONE | "FIRE_RATED_GYPSUM" material | Resolved from `ad_wall_type.material_primary` for PARTY type |
| A9 | ✅ DONE | Separating slab 0.20 × 2 sites | `BIMConstants.SEPARATING_SLAB_THICKNESS` (centralized from BuildingCompiler local) |
| A10 | ✅ DONE | "pendant", "surface" fixture types | `BIMConstants.SPRINKLER_FIXTURE_TYPE` / `LIGHT_FIXTURE_TYPE` |
| A11 | ✅ DONE | Window spacing 0.5m gap | `BIMConstants.WINDOW_PANEL_GAP` |
| A12 | ✅ DONE | "TOILET", "WC", "KITCHEN" room type strings | Replaced by `ExteriorRuleAD.requiresExterior()` (data-driven via B1) |

**All 12 Category A findings resolved.**

---

## Category B: Metadata Exists, Never Wired

| # | Status | Table | Fix Applied |
|---|--------|-------|-------------|
| B1 | ✅ DONE | `ad_space_exterior_rule` (24 rows) | New `ExteriorRuleAD.java` — replaces hardcoded TOILET/KITCHEN checks with `requiresExterior(roomType)` query |
| B2 | ⏳ DEFER | `ad_space_adjacency` (22 rows) | Future: room layout validation |
| B3 | ✅ ALREADY WIRED | `ad_column_type` (5 rows) | `ColumnTypeResolver` (Phase 122D) loads and resolves via `StructuralPlacer` |
| B4 | ✅ ALREADY WIRED | `ad_column_type_rule` (8 rows) | `ColumnTypeResolver` (Phase 122D) profile-aware 2-pass resolution |
| B5 | ⏳ DEFER | `ad_check_applicability` (33 rows) | Future: compliance layer |
| B6 | ⏳ DEFER | `ad_check_threshold` (31 rows) | Future: compliance layer |
| B7 | ⏳ DEFER | `ad_assembly_connector` (10 rows) | Future: assembly system |
| B8 | ⏳ DEFER | `ad_assembly_manifest` (37 rows) | Future: assembly system |
| B9 | ⏳ DEFER | `ad_reference` (5 rows) | Low priority labels |

**B1, B3, B4 resolved. B2, B5-B9 deferred (future features).**

---

## Category C: No Metadata Table Exists

| # | Status | Hardcoded Value | Fix Applied |
|---|--------|-----------------|-------------|
| C1 | ⏳ DEFER | 18 CIDB cost rates | Non-ARC (output formatting) |
| C2 | ⏳ DEFER | 20+ furniture name→component patterns | Cosmetic naming |
| C3 | ⏳ DEFER | Occupancy from building name substrings | Fire protection, not ARC |
| C4 | ⏳ DEFER | Elevator clearances 0.4, 0.5, 0.3 | Future |
| C5 | ⏳ DEFER | Toilet stall dims 1.3×1.2×1.8 | Future |
| C6 | ✅ PARTIAL | Ceiling offset 0.05, landing 0.15, bay slab 0.200 | Landing → `BIMConstants.STANDARD_LANDING_THICKNESS`. Bay slab → `resolveSlabThickness()`. Ceiling offset already `BIMConstants` |
| C7 | ⏳ DEFER | Light-column clearance 0.35, margins | Future |
| C8 | ✅ DONE | Preferred stair tread 0.267 | `BIMConstants.PREFERRED_TREAD_DEPTH` |

---

## Other Issues (unchanged)

### Duplicated Logic
- Discipline→GUID prefix map: BuildingWriter:788 AND ElementPersistence (two copies)
- Occupancy inference: BuildingCompiler:1253 AND :1382 (identical code, two places)
- Profile string matching: 6 sites use `.contains("US_Residential")` etc.

### Path Literal
- `"library/component_library.db"` appears in **33 sites** across all files
- Should be single constant in `CompilerConfig.DB_PATH` or `BIMConstants`

---

## Completion Summary (2026-02-18)

### Files Modified
- `StoreyCompiler.java` — A1-A5, A10-A12, B1, C6, C8
- `MultiUnitCompiler.java` — A6-A8, A9
- `BuildingCompiler.java` — A9
- `BIMConstants.java` — A9, A10, A11, C8 (new constants)
- `WallTypeResolver.java` — A4, A5 (new constants)
- `ExteriorRuleAD.java` — B1 (new file)

### X-ray Scores (post-audit, 2026-02-18)
| Stone | Recall | Precision | F1 |
|-------|--------|-----------|------|
| SampleHouse | **100%** (55/55) | **100%** | **100%** |
| Duplex | **100%** (1085/1085) | **100%** | **100%** |
| Terminal | **100%** (51088) | **100%** | **100%** |

### What's Resolved
- **15 of 33** findings fixed (12A + 1B-new + 2B-already-wired + 2C-bonus)
- Zero inline magic numbers remain for wall/opening/slab placement
- All fallbacks route through `BIMConstants` or metadata resolvers

### What Remains
- **B2, B5-B9**: Future features (compliance, assembly, adjacency)
- **C1-C5, C7**: Non-ARC concerns or need new tables
- **Path literal**: 33 sites with `"library/component_library.db"` → future centralization
- **Duplicated logic**: 3 patterns → future refactor

---

## Phase RM-1 + RM-2 (2026-02-18)

### RM-1: Relational Tables Created + Populated
5 new tables in component_library.db:
- `ad_building_grid` (75 rows) — structural grid lines from wall clustering
- `ad_room_boundary` (44 rows) — rooms with exact coords + grid labels
- `ad_wall_face` (176 rows) — room boundary faces with wall type + adjacency
- `ad_element_rule` (1,140 rows) — element placement rules (host + position + family)
- `ad_element_dependency` (750 rows) — parent-child cascade chain

Schema: `migration/migration_RM1_relational_tables.sql`
Data: `DAGCompiler/python/relational_extractor.py --building all` (idempotent)

### RM-2: RelationalResolver Shadow Validation — PASS
- `RelationalResolver.java` — reads 5 relational tables, computes placements
- `relational_shadow_validator.py` — Python validation (used during development)
- Shadow validation wired into SH + DX E2E tests (Step 6 / Step 9)
- **SampleHouse: 55/55 matched (100.0%), max_err=0.001mm**
- **Duplex: 1085/1085 matched (100.0%), max_err=0.001mm**
- Terminal: deferred (51K elements include MEP/rebar — needs specialized models)
- E2E scores unchanged: SH 5/5, DX 8/8, Terminal 4/4

### What's Next
- **Phase RM-3**: Cutover — compiler reads computed placement (`ad_sysconfig.placement_mode = RELATIONAL`)
- **Phase RM-4**: TB-LKTN from intent — metadata only, no IFC, no Stone

*Phase RM-2 completed 2026-02-18.*
