# PROGRESS — Current Development State

**Last updated:** 2026-02-14
**Current phase:** Phase 122I — Wall Segmentation + Proxy Discipline Fix
**Baseline:** 8 E2E PASS, 3 Rosetta X-ray pairs, SampleHouse 62%, Duplex 55%, Terminal 22%

---

## Session Summary — Phase 122H-I: Perimeter Columns + Proxy Fix + Wall Segmentation

### What Was Done

**1. Perimeter grid column classification** (StructuralPlacer + StoreyCompiler):
- Added PERIMETER to ColumnType enum
- Grid intersections on building perimeter now get PERIMETER type (600×300mm for institutional)
  instead of being skipped or treated as INTERMEDIATE (800×450mm)
- Terminal columns: 89 → 127 (+38 perimeter)

**2. IfcBuildingElementProxy discipline fix** (spatial_checker.py):
- P2 investigation: 486 IfcBuildingElementProxy in Terminal ref — 339 ELEC, 61 ARC, 51 ACMV, 31 FP, 4 CW
- All 486 were defaulting to ARC discipline in spatial_checker (inflating denominator)
- Fix: use DB discipline field for IfcBuildingElementProxy, map ELEC/FP/ACMV→MEP
- X-ray denominator: 3818→3393 (-425 phantom ARC elements)
- Terminal: 18%→20% (honest denominator, no actual element changes)

**3. Wall segmentation at openings/columns** (StoreyCompiler):
- New post-processing step: `segmentWallsAtOpenings()` runs after structural placement
- Splits walls at door/window edges and column faces into shorter segments
- Only for buildings with structural grids (>= 6 bays) — residential walls preserved
- Terminal walls: 120 → 948 segments (matching reference pattern of short wall pieces)
- Terminal X-ray: 435→528 (+93 near-matches)
- Terminal: 20%→22%

**4. Office window migration attempted and reverted** (no net change):
- Fallback 833mm windows already matching 36 reference windows
- **Lesson: never replace a working fallback unless replacement matches MORE elements**

### Score Impact

| Pair | Overall (was) | Overall (now) | X-ray near (was) | X-ray near (now) |
|------|--------------|---------------|-------------------|-------------------|
| SampleHouse | 62% | **62%** | 18/35 (51%) | 18/35 (51%) |
| Duplex | 55% | **55%** | 86/183 (46%) | 86/183 (46%) |
| Terminal | 18% | **22%** | 404/3818 (10%) | **528/3393 (15%)** |

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| All 8 E2E tests | PASS |
| SampleHouse ARC | **62%** (unchanged) |
| Duplex ARC | **55%** (unchanged) |
| Terminal ARC | **22%** (was 18%) |

### Files Changed

| File | Action |
|------|--------|
| `src/.../library/StructuralPlacer.java` | MODIFIED — PERIMETER enum + new placeGridColumns overload |
| `src/.../dsl/StoreyCompiler.java` | MODIFIED — column position collection + wall segmentation |
| `tools/spatial_checker.py` | MODIFIED — IfcBuildingElementProxy discipline-aware filtering |
| `migration/migration_122H_perimeter_columns.sql` | NEW — PERIMETER column type rule |
| `docs/TheRosettaStoneStrategy.txt` | UPDATED — score history + baselines |

### What's Next — Phase 122J (Reconciliation Punchlist)

**P1/P2 DONE this session.** Wall segmentation (+93 X-ray), proxy discipline fix (-425 phantom denominator).

**Wall Variance Analysis Results** (P1.5 diagnostic, completed):
- 109/330 ref walls are multi-storey (8-22m) — unmatchable by per-storey model (33%)
- 74 walls fail on height: 3850mm vs ref 4000mm — fix coded (institutional wallMaxZ = full height), needs score verification
- 24 walls missing thickness: 230mm (6), 250mm (15), 300mm (3) — types exist but no dispatch rules
- 149 walls length mismatch — different partition layouts between ref and output
- 25 walls in storeys above our Z=16m (Aras 04/Bumbung)

**Next session quick wins:**
1. **Verify wall height fix** — already coded, run all E2E + scores
2. **Add 250mm/230mm wall dispatch rules** — SQL migration, 21 walls recoverable
3. **P5: Implement --shortage-report** in spatial_checker.py (documented, not coded)
4. **P3: Curtain wall height variants** — 48 ref windows at 3974/4349/4849mm

**Strategy doc rectified** — `docs/TheRosettaStoneStrategy.txt` now frames convergence as reconciliation audit with 8 finite compose primitives and ~11 working day estimate.

**Standing Rule:** Score is arbiter. Never replace a working fallback unless replacement matches MORE. Run checker before AND after every change. Variance analysis before new code.

---

## Session Summary — Phase 122G: Window Scaling + Fire Doors + Door Depth Fix

### What Was Done

**1. Wall-length window scaling** (StoreyCompiler):
- PER_EXTERIOR_WALL qty rule now computes qty = floor(wallLen / (winW + 0.5m))
- Terminal windows: 57 → 316 (+259). 225/236 reference windows now matched.
- Opening sizes: 25% → 49% (nearly doubled)

**2. Institutional hasDoorOnWall relaxation** (StoreyCompiler):
- Curtain wall panels now coexist with entrance doors on same facade
- Lobby south wall (with 3 doors) now also gets curtain windows

**3. Fire door families** (migration_122G):
- 4 new families: INST_FIRE_DOOR_750 (FD1), INST_FIRE_DOOR_DOUBLE (FD4 1500mm),
  INST_FIRE_DOOR_WIDE (FD2 1800mm), INST_DOOR_1050
- FIRE_EXIT role added for 7 institutional room types
- Terminal doors: 39 → 84 (+45)

**4. BOM auto-door wall distribution** (StoreyCompiler):
- Track used walls so ENTRY and FIRE_EXIT doors go on different walls
- Fixed: all BOM doors landing on same wall

**5. Door depth resolution bug fix** (StoreyCompiler):
- Bug: depth always picked bomDoors.get(0) (ENTRY family), giving wrong depth
  to FIRE_EXIT doors (200mm instead of 150/147/177mm)
- Fix: match family by width to resolve correct depth per door role
- FD1 750mm: 200→150mm, FD2 1800mm: 200→177mm (+34 X-ray near-matches)

**6. Additional door families** (migration_122G2):
- INST_FIRE_DOOR_900 (FD1 at 900mm/150mm depth)
- INST_DOOR_1050 assigned to DINING EGRESS
- INST_FIRE_DOOR_900 assigned to LOBBY/OPEN_PLAN EGRESS

**7. DSL cleanup** (SJTII_Terminal.bim):
- Removed redundant explicit WINDOW on exterior walls
- Auto-windows now handle all exterior walls via metadata
- Kept explicit WINDOW only on non-exterior walls (canteen, food_court, etc.)

### Score Impact

| Pair | Overall (was) | Overall (now) | X-ray near (was) | X-ray near (now) |
|------|--------------|---------------|-------------------|-------------------|
| SampleHouse | 62% | **62%** | 18/35 (51%) | 18/35 (51%) |
| Duplex | 53% | **55%** | 84/183 (45%) | — |
| Terminal | 14% | **18%** | 307/3818 (8%) | **404/3818 (10%)** |

### Terminal Score Breakdown (18%)

| Check | Match | Total | Score |
|-------|-------|-------|-------|
| Wall thickness | 306 | 333 | 91% |
| Opening sizes | 184 | 371 | 49% |
| Slab/Roof sizes | 12 | 415 | 2% |
| X-ray near | 404 | 3818 | 10% |
| **OVERALL** | **906** | **4937** | **18%** |

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| All 8 E2E tests | PASS |
| SampleHouse ARC | **62%** (unchanged) |
| Duplex ARC | **55%** (was 53%) |
| Terminal ARC | **18%** (was 14%) |

### Files Changed

| File | Action |
|------|--------|
| `src/.../dsl/StoreyCompiler.java` | MODIFIED — wall-length scaling, door depth fix, wall distribution |
| `examples/SJTII_Terminal.bim` | MODIFIED — removed redundant explicit windows |
| `migration/migration_122G_fire_doors.sql` | NEW — 4 fire door families + FIRE_EXIT role |
| `migration/migration_122G2_door_depth_families.sql` | NEW — FD1 900mm + INST_DOOR_1050 assignments |

### What's Next — Phase 122H+ Strategy

**Remaining Terminal gaps (at 18%):**
- WALL: 120 out vs 333 ref (29 near) — need more internal wall segments
- COLUMN: 89 out vs 158 ref (29 near) — cross-section mismatch (800×450 vs ref 600×300)
- FURNITURE: 578 out vs 340 ref (42 matched) — position mismatch
- SLAB: 712 out vs 413 ref (89 matched) — bay slabs mostly near, need exact dims
- DOOR: 84 out vs 135 ref — size match good, position match poor (0 X-ray near)
- BEAM: 649 out vs 152 ref — massive over-production

**Highest-impact next moves:**
1. Column cross-section: add 600×300mm variant → could match 54 additional ref columns
2. Wall count: investigate why ref has 333 vs our 120 — multi-layer? more partitions?
3. Curtain wall height variants: ref has 3974, 4349, 4849mm heights → 48 unmatched windows
4. Standard office windows: 1250mm-wide family for office rooms
5. SampleHouse/Duplex slab gap still at 0% match

---

## Session Summary — Phase 122E-F: Grid-Bay Slabs + Canteen Scaling + Reference Cleanup

### What Was Done

**1. Grid-bay slab generation for RC frame buildings** (StoreyCompiler + BuildingWriter + BuildingSpecs):
- Added `List<SlabSpec> baySlabs` field to `StoreySpec` record (additive, backward-compatible)
- In `compileSlabAndPerimeter()`: when building has a structural grid (>= 6 bays), generates quarter-bay slabs at beam mid-spans instead of a single monolithic slab
- Quarter-bay logic: each full bay (X-spacing × Y-spacing) is divided into 4 panels at (X/2 × Y/2)
- BuildingWriter: writes bay slabs instead of envelope slab when available
- Institutional RC slab thickness: 200mm (matching Terminal reference)
- Structural grid threshold: >= 6 bays prevents small room-layout grids (SampleHouse 2×2=4) from triggering bay slabs
- Terminal: 168 quarter-bay slabs per storey × 4 storeys = 672 bay slabs
- Sizes: 5000×4000 (336), 6000×4000 (224), 4000×4000 (112) @ 200mm

**2. Reference DB cleanup** (Terminal):
- Reclassified 236 piles (300×300×30000mm) from IfcSlab → IfcPile
- Reclassified 56 pad footings (2100×2100×750mm) from IfcSlab → IfcFooting
- Honest IfcSlab count: 705 → 413 real floor slabs

**3. Canteen table area-based grid placement** (FurnitureBOMResolver):
- CENTER-placed BOMs in rooms >= 20m² now replicate on an area-based grid
- ~13m² per table set (matching Terminal: 60 ref tables in ~780m² total dining area)
- Terminal canteen: 7×5 grid = 29 sets per room × 2 rooms = 58 tables (ref: 60)
- Each set = 1 table + 4 chairs → 5 items × 58 = 290 additional furniture items
- FURNITURE near-matches: 25 → **79** (+54 from canteen tables)

**4. Temporal Hard-Specs documented** (TheRosettaStoneStrategy.txt):
- POC method: hard-code metadata per stone, reverse-engineer DSL later
- Metadata Resolution Hierarchy: Building (silhouette) → Room (features) → BOM (texture)
- Building level = coarsest, most powerful variance point

### Score Impact

| Pair | Overall (was) | Overall (now) | X-ray near (was) | X-ray near (now) |
|------|--------------|---------------|-------------------|-------------------|
| SampleHouse | 62% | **62%** | 18/35 (51%) | 18/35 (51%) |
| Duplex | 53% | **53%** | 84/183 (45%) | 84/183 (45%) |
| Terminal | 11% | **14%** | 176/3818 (4%) | **307/3818 (8%)** |

**Terminal gains breakdown:**
- Bay slabs: +77 near-matches (12 exact + 77 near from 672 quarter-bay slabs)
- Ref cleanup: +~30 to overall denominator reduction (5229→4937)
- Canteen tables: +54 near-matches (58 output tables vs 60 reference)
- FURNITURE near: 25 → 79 (+54)
- SLAB near: 0 → 77 (+77)

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| All 8 E2E tests | PASS |
| SampleHouse ARC overall | **62%** (unchanged) |
| Duplex ARC overall | **53%** (unchanged) |
| Terminal ARC overall | **14%** (was 11%) |

### Files

| File | Action |
|------|--------|
| `src/.../dsl/BuildingSpecs.java` | MODIFIED — added `baySlabs` field to StoreySpec |
| `src/.../dsl/StoreyCompiler.java` | MODIFIED — grid-bay slab generation |
| `src/.../dsl/BuildingWriter.java` | MODIFIED — writes bay slabs when available |
| `src/.../dsl/BuildingCompiler.java` | MODIFIED — passes baySlabs through constructors |
| `src/.../dsl/MultiUnitCompiler.java` | MODIFIED — passes baySlabs through constructors |
| `src/.../library/FurnitureBOMResolver.java` | MODIFIED — CENTER grid area-based replication |
| `reference/rosetta/SJTII_Terminal_extracted.db` | MODIFIED — reclassified piles/footings |
| `docs/TheRosettaStoneStrategy.txt` | MODIFIED — temporal hard-specs + metadata hierarchy |

### What's Next — Phase 122G+ Strategy

**Terminal gap analysis (updated at 14%):**
- WINDOW: 57 output, 236 ref — need 4× more windows (multiple per exterior wall)
- DOOR: 39 output, 135 ref — need 3× more doors
- WALL: 28/333 near — dimensions correct but lengths/heights mismatch
- SLAB: 77/413 near — bay slabs matching, many smaller panels still unmatched
- COLUMN: 26/158 near — positions don't overlap reference

**SampleHouse/Duplex slab gap:**
- SampleHouse: 0/3 slab match. Footprint and thickness both off.
- Duplex: 0/21 slab match. Per-unit structural slabs at varying thicknesses (127-457mm). Need multi-unit slab logic.

**Highest-impact next moves:**
1. **Wall dimension alignment** — Terminal 29/333 near, 0 exact. SampleHouse/Duplex walls also near but not exact.
2. **Canteen table qty scaling** — 4 output vs 60 reference. Per-room area-based qty.
3. **More furniture library matches** — Terminal FURNITURE 25 output near 176 ref.
4. **Curtain wall window qty** — 28 output vs 65 reference. DSL room count limits.
5. **Score targets**: SampleHouse 62%→75%, Duplex 53%→65%, Terminal 12%→20%

---

## Session Summary — Phase 122E: Institutional Opening Families + Depth Fix

### What Was Done

Two changes targeting Terminal opening convergence:

**1. Institutional Opening Families** (migration — pure metadata):
- Created 3 new `ad_opening_family` entries:
  - `INST_CURTAIN_WINDOW` — 1310×3450×223mm (curtain wall glass panel, matches 65 reference windows)
  - `INST_DOOR_900` — 900×2175×200mm (institutional standard door, matches 27 reference doors)
  - `INST_DOOR_950` — 950×2175×200mm (institutional office door, matches 15 reference doors)
- Created 10 profile-specific `ad_space_type_opening` entries for `Malaysian_Institutional`:
  - LOBBY, OPEN_PLAN, DINING, CORRIDOR → NATURAL_LIGHT → INST_CURTAIN_WINDOW (sill=0, full-height)
  - LOBBY, OPEN_PLAN, DINING, TOILET_BLOCK → ENTRY → INST_DOOR_900
  - OFFICE, STAFFROOM → ENTRY → INST_DOOR_950
- Two-pass profile resolution works: generic fallback preserved for uncovered roles

**2. Opening Depth + Sill Fix** (StoreyCompiler.java — bug fix):
- **Depth bug**: BOM auto-generated doors had width/height pre-set, so the depth resolution block (`if (width == 0 || height == 0)`) was skipped → depth fell to 100mm DOOR_THICKNESS instead of the family's depth_mm.
- **Fix**: Depth resolution now runs unconditionally — always looks up BOM family depth regardless of whether width/height need resolution.
- **Sill fix**: Explicit `WINDOW wall` declarations used hardcoded 900mm sill. Now reads BOM sill_height_mm (0mm for curtain walls = floor-to-ceiling).
- Impact: Terminal doors went from 3 correct-depth doors to 35. Corridor windows went from 100×1200×1200mm fallback to 223×1310×3450mm curtain wall.

### Score Impact

| Pair | Overall (was) | Overall (now) | X-ray near (was) | X-ray near (now) |
|------|--------------|---------------|-------------------|-------------------|
| SampleHouse | 62% | **62%** | 18/35 (51%) | 18/35 (51%) |
| Duplex | 53% | **53%** | 84/183 (45%) | 84/183 (45%) |
| Terminal | 9% | **11%** | 119/3818 (3%) | **176/3818 (4%)** |

**Terminal per-category X-ray near-matches:**
| Category | Exact | Near | Out | Ref |
|----------|-------|------|-----|-----|
| COLUMN | 26 | 26 | 89 | 158 |
| FURNITURE | 25 | 25 | 308 | 176 |
| WINDOW | 49 | 49 | 57 | 236 |
| WALL | 0 | 29 | 120 | 333 |
| DOOR | 37 | 37 | 39 | 135 |

**Terminal opening gains detail:**
| Signature | Was | Now | Reference |
|-----------|-----|-----|-----------|
| WINDOW 3450×1310×223 | 0 | **28** | 65 |
| DOOR 900×2175×200 | 3 | **18** | 27 |
| DOOR 950×2175×200 | 0 | **17** | 15 |

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| All 8 E2E tests | PASS |
| SampleHouse ARC overall | **62%** (unchanged) |
| Duplex ARC overall | **53%** (unchanged) |
| Terminal ARC overall | **11%** (was 9%) |

### Files

| File | Action |
|------|--------|
| `migration/migration_122E_openings.sql` | NEW — 3 opening families + 10 profile-specific entries |
| `src/.../dsl/StoreyCompiler.java` | MODIFIED — depth resolution unconditional, BOM sill for explicit windows |

### What's Next — Phase 122F+ Strategy

**Terminal score plateau analysis (updated):**
- Current 11% (576/5229) — X-ray 176/3818 near
- Opening sizes improved: 94/371 (25%, was 15%)
- SLAB: 0/707 — biggest single gap (705 reference slabs, 0 matches)
- WALL: 29/333 near — wall dimensions correct but positions/lengths don't overlap
- Curtain wall gap: 28/65 = 43% coverage — DSL room count limits further gains

**Highest-impact next moves:**
1. **Per-room floor slabs** — 705 reference, 44 output (0 match). Dominant ref size: 6×4m. Need room-based slab sizing
2. **Curtain wall window qty** — 28 output vs 65 reference. DSL has fewer rooms than actual building
3. **IfcFurnishingElement class fix** — output uses IfcFurniture (correct for Terminal), but SampleHouse/Duplex ref uses IfcFurnishingElement (semantic only, no score impact)
4. **Wall dimension alignment** — 29 near but 0 exact. Wall lengths/heights mismatch reference
5. **Canteen table qty** — 4 output vs 60 reference. Needs per-room area-based CANTEEN_SET qty

---

## Session Summary — Phase 122D: Terminal Column/Furniture/Window Convergence

### What Was Done

Three steps targeting Terminal ARC score convergence:

**1. Column Sizing — ColumnTypeResolver** (new file + migration):
- Created `ColumnTypeResolver.java` following BeamTypeResolver pattern exactly
- Added `ad_column_type_rule` table with profile-specific rules
- Malaysian_Institutional: CORNER/T_JUNCTION → RC_600x300, INTERMEDIATE → RC_800x450
- Generic fallback → RC_300x300 (preserves residential behavior)
- Modified `StructuralPlacer.placeColumns()` and `placeGridColumns()` to accept profile
- Result: 26 column near-matches (was 0)

**2. Furniture BOM — Profile-Aware Slot Dispatch** (migration + SlotRegistry change):
- Added `profile` column to `ad_room_slot` (recreated table with updated UNIQUE constraint)
- Created CANTEEN_SET BOM (Canteen Table + 4 Dining Chairs, CENTER placement)
- Created LOBBY_SEAT_SET BOM (Waiting_Room_Seat only, no workstation/visitor overproduction)
- Profile-specific slots: DINING+Malaysian_Institutional → CANTEEN_SET, LOBBY → LOBBY_SEAT_SET
- SlotRegistry now does two-pass resolution: profile-specific first, then generic
- Result: 25 furniture near-matches

**3. Auto-Window Fix — Per-Wall Skip for Institutional**:
- Changed room-level window skip to per-wall skip for institutional profiles
- Rooms with explicit windows on some walls now get auto-windows on remaining exterior walls
- Residential behavior unchanged (full room skip preserved)

**4. Column Discipline Fix** (bonus):
- Columns now tagged as ARC discipline (was STR), matching Rosetta reference convention
- Fixed `inferDiscipline()` in ElementPersistence: COLUMN_ guid prefix → ARC
- Updated spatial_checker.py: removed IfcColumn from STR-only mapping

### Score Impact

| Pair | Overall (was) | Overall (now) | X-ray near (was) | X-ray near (now) |
|------|--------------|---------------|-------------------|-------------------|
| SampleHouse | 62% | **62%** | 18/35 (51%) | 18/35 (51%) |
| Duplex | 53% | **53%** | 84/183 (45%) | 84/183 (45%) |
| Terminal | 8% | **9%** | 93/3660 (2%) | **119/3818 (3%)** |

**Terminal per-category X-ray near-matches:**
| Category | Exact | Near | Out | Ref |
|----------|-------|------|-----|-----|
| COLUMN | 26 | 26 | 89 | 158 |
| FURNITURE | 25 | 25 | 308 | 176 |
| WINDOW | 32 | 36 | 57 | 236 |
| WALL | 0 | 29 | 120 | 333 |
| DOOR | 3 | 3 | 39 | 135 |

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| All 8 E2E tests | PASS |
| SampleHouse ARC overall | **62%** (unchanged) |
| Duplex ARC overall | **53%** (unchanged) |
| Terminal ARC overall | **9%** (was 8%) |

### Files

| File | Action |
|------|--------|
| `migration/migration_122D_columns.sql` | NEW — ad_column_type_rule table + RC_600x300/RC_800x450 |
| `migration/migration_122D_furniture.sql` | NEW — CANTEEN_SET/LOBBY_SEAT_SET BOMs, profile-aware slots |
| `src/.../library/ColumnTypeResolver.java` | NEW — lazy singleton, profile-aware column sizing |
| `src/.../library/StructuralPlacer.java` | MODIFIED — profile-aware placeColumns/placeGridColumns |
| `src/.../library/SlotRegistry.java` | MODIFIED — profile-aware getSlotsForType/getFurnitureAssemblyId |
| `src/.../dsl/StoreyCompiler.java` | MODIFIED — pass profile to columns/slots/windows |
| `src/.../dsl/BuildingSpecs.java` | MODIFIED — ColumnSpec discipline → ARC |
| `src/.../dsl/ElementPersistence.java` | MODIFIED — COLUMN_ guid prefix → ARC discipline |
| `tools/spatial_checker.py` | MODIFIED — IfcColumn as ARC in discipline mapping |

### What's Next — Phase 122E+ Strategy

**Terminal score plateau analysis:**
- Current 9% (481/5229) — gains capped by position mismatch (columns at right dimensions but wrong XY)
- 158 reference ARC columns, only 26 spatially overlapping → XY position alignment needed
- 60 canteen tables in reference vs 4 output → needs more DINING rooms or per-room qty scaling
- 236 reference windows vs 57 output → many large curtain wall windows (1310x3450mm) not producible with current families

**Highest-impact next moves:**
1. **Window family expansion** — reference has 65 × 1310x3450mm windows (curtain wall). Need new ad_opening_family entry
2. **Door size alignment** — reference has 900x2100 (13), 950x2180 (15) sizes not in current families
3. **Slab generation** — 705 reference slabs, 44 output → floor slab dimensions need per-room sizing
4. **Canteen table qty** — scale CANTEEN_SET qty by room area (60 tables need ~15 dining areas)
5. **IfcFurniture vs IfcFurnishingElement** — output uses IfcFurnishingElement (308), reference uses IfcFurniture (176)

---

## Session Summary — Phase 122C: Kitchen Cabinets, Dining Fix, Bathroom Vanity

### What Was Done

Three targeted improvements following Rosetta Trainer gap analysis:

**1. CENTER wall_rule for dining tables** (`FurnitureBOMResolver.java`):
- New `CENTER` wall_rule: places anchor at room center instead of wall-anchored
- Dining tables need chairs on all sides — wall-anchored placement left 3/6 chairs outside room bounds
- DINING_SET TABLE now uses CENTER placement via migration
- SampleHouse chairs: 3/6 → **6/6** (+3 X-ray matches)

**2. Kitchen cabinet run** (`migration/migration_122C_kitchen_cabinets.sql`):
- Expanded KITCHEN_CABINET_SET from 4 items to 12 (7 Base_Cabinet + 3 Upper_Cabinet + Counter + Sink)
- Cabinet roles placed along wall at 1m intervals with dx offsets
- Each Duplex kitchen now produces 7 base + 3 upper cabinets = 10 items × 2 kitchens = 20
- Duplex furniture: 22/61 → **46/61** (+24 matches)

**3. Bathroom vanity cabinets** (`migration/migration_122C_kitchen_cabinets.sql`):
- Added BATHROOM_VANITY_SET to ad_bom table (was missing → BOM tree didn't load it)
- Added name_pattern and offsets for VANITY_A/VANITY_B roles
- Fixed area threshold from 6.0m² to 2.0m² (bathrooms are 3-5m²)
- 8 vanity cabinets now placed across 4 bathrooms (+4 near-matches)

**4. BOM tolerance increase** (`FurnitureBOMResolver.java`):
- expandBOMNode bounds tolerance: 0.1m → 0.5m (BOM children may legitimately extend past room edge)
- Required for dining chairs with offsets that extend beyond anchor position

### Score Impact

| Pair | Overall (was) | Overall (now) | X-ray (was) | X-ray (now) |
|------|--------------|---------------|-------------|-------------|
| SampleHouse | 53% | **62%** | 42% (15/35) | **51%** (18/35) |
| Duplex | 42% | **53%** | 34% (64/183) | **45%** (84/183) |
| Terminal | 8% | 8% | 2% | 2% |

**SampleHouse improvement:** Furniture 50% → **71%** (10/14)
**Duplex improvement:** Furniture 36% → **75%** (46/61)

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| All 8 E2E tests | PASS |
| SampleHouse ARC overall | **62%** (was 53%) |
| Duplex ARC overall | **53%** (was 42%) |

### Files

| File | Action |
|------|--------|
| `migration/migration_122C_dining_center.sql` | NEW — CENTER wall_rule for DINING_SET TABLE |
| `migration/migration_122C_kitchen_cabinets.sql` | NEW — kitchen cabinet run + bathroom vanity |
| `src/.../library/FurnitureBOMResolver.java` | MODIFIED — CENTER wall_rule, area threshold 2.0m², tolerance 0.5m |
| `src/.../library/FurniturePlacer.java` | MODIFIED — cabinet/vanity/counter role mappings |

### What's Next — Phase 122D+ Strategy

**Priority 1: Terminal (biggest score reservoir — 1243 ARC misses):**
- Run `python3 tools/rosetta_trainer.py --pair 3` for full gap analysis
- 65+24 windows (W1/W2/W3 EXACT in library) → +89 matches
- 60 canteen tables + 46+16 waiting seats (EXACT in library) → +122 matches
- 35+19 columns (M_Rectangular Column EXACT) → +54 matches
- 27 doors (D2 EXACT) → +27 matches
- **Potential: 8% → ~30% ARC** from library-matched items alone

**Priority 2: Slab alignment (0% on both SampleHouse and Duplex):**
- SampleHouse: 0/3 (foundation slab 16870x8670x470, floor slab 13970x5770x170, roof)
- Duplex: 0/21 match — investigate dimension mismatch
- Slabs are structural elements, not furniture — different writer path

**Priority 3: Profile-specific BOM (SampleHouse 62% → 75%):**
- UK bed (Bed_King 1800x2007x482 vs current Queen 1524x2007x635)
- UK coffee table (Coffee_Table_Rect_1200 vs current Large 1830x915x457)
- Need profile column on ad_bom_child_param or profile-specific BOM IDs

**Priority 4: Multi-discipline scoring:**
- Terminal has 8 disciplines: FP 6863, ACMV 1621, CW 1431, STR 1429, ARC 1400
- Current focus: ARC only. MEP/STR disciplines are untapped
- `--discipline ALL` blended score still useful as secondary metric

---

## Session Summary — Phase 122B: Rosetta Stone Convergence

### What Was Done

Four targeted fixes following the Linguist's Method (Dictionary → Thesaurus → Grammar):

**1. Interior adjacency door resolution** (`StoreyCompiler.java`):
- New `resolveAdjacencyDoor()` method: ADJACENT role > interior-placement ENTRY > any ENTRY
- UK_Residential CORRIDOR/LIVING rooms now get 810mm interior doors for room-to-room connections
- Was: all adjacency doors used room's ENTRY family (UK_EXT_DBL = 1860mm exterior double)
- SampleHouse doors: 1/3 → **3/3 match** (opening size 71% → **100%**)

**2. Multi-slot dispatch** (`StoreyCompiler.java`):
- Replaced `getFurnitureAssemblyId()` (FURNITURE slot only) with `getSlotsForType()` iteration
- LIVING rooms now dispatch both FURNITURE → LIVING_SET and DINING → DINING_SET
- Adds dining table + dining chairs to living rooms automatically

**3. Furniture BOM improvements** (`migration/migration_122B_interior_doors.sql`):
- Fixed Dining_Table name_pattern: `Dining_Table` → `Dining_Table_With_Chairs` (2000x1000x750)
- Added DESK to BED_SET BOM (1563x819x762 = exact SampleHouse ref match)
- Added PIANO to LIVING_SET BOM (1372x600x1170 = exact SampleHouse ref match)
- Added CHAIR_E/F role mappings in FurniturePlacer

**4. Component library exact-match preference** (`ComponentLibrary.java`):
- `getByName()` now prefers exact name matches over largest-footprint LIKE matches
- Fixes "Desk" matching "26_Desk_with_return_" (2660mm) instead of "Desk" (1563mm)

**5. Spatial checker fix** (`tools/spatial_checker.py`):
- Slab/Roof bucketing changed from 100mm to 10mm (was hiding 19mm finish slabs as 0mm)

### Score Impact

| Pair | ARC Overall (was) | ARC Overall (now) | X-ray (was) | X-ray (now) |
|------|-------------------|-------------------|-------------|-------------|
| SampleHouse | **28%** | **53%** | 20% (7/35) | **42%** (15/35) |
| Duplex | **40%** | **42%** | 31% (57/183) | **34%** (64/183) |
| Terminal | 8% | 8% | 2% | 2% |

**SampleHouse breakdown:**
- Wall thickness: 5/5 (100%) — unchanged
- Opening sizes: **7/7 (100%)** — was 5/7 (71%)
- Furniture sizes: **7/14 (50%)** — was 1/14 (7%)
- Slab/Roof sizes: 0/3 (0%) — unchanged
- X-ray near-match: **15/35 (42%)** — was 7/35 (20%)

**SampleHouse furniture matched (7/14):**
- Sofa (2290x980x960), Piano (1370x1170x600), Desk (1560x820x760)
- Dining Table (2000x1000x750), Dining Chair x3 (1230x440x430)

**SampleHouse furniture missed (7/14):**
- Bed (Queen 1530x2010x640 vs ref King 1800x2010x480) — need profile-specific BOM
- Coffee table (Large 1830x920x460 vs ref Small 1200x550x450) — need profile-specific BOM
- 2x Armchair (1430x1400x920) — no library component at this size
- 3x Dining chair (6 ref, only 3 placed — room constraint)

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| CondoMidEndToEndTest | PASS |
| SampleHouseEndToEndTest | PASS (114 elements, was 108) |
| TBLKTNDuplexEndToEndTest | PASS (549 elements) |
| TerminalEndToEndTest | PASS |
| SchoolEndToEndTest | PASS |
| TBLKTNEndToEndTest | PASS |
| TBLKTN2SEndToEndTest | PASS |
| TBLKTNCompactEndToEndTest | PASS |
| SampleHouse ARC X-ray | **53%** (was 28%) |
| Duplex ARC X-ray | **42%** (was 40%) |

### Files

| File | Action |
|------|--------|
| `migration/migration_122B_interior_doors.sql` | NEW — adjacency doors, dining table, desk, piano BOMs |
| `src/.../dsl/StoreyCompiler.java` | MODIFIED — resolveAdjacencyDoor(), multi-slot dispatch |
| `src/.../library/FurniturePlacer.java` | MODIFIED — CHAIR_E/F, SOFA_B, SIDE_TABLE_A/B, PIANO roles |
| `src/.../library/ComponentLibrary.java` | MODIFIED — exact name match preference in getByName() |
| `tools/spatial_checker.py` | MODIFIED — slab bucketing 100mm→10mm |

### What's Next — Phase 122C+

**SampleHouse remaining gaps (53% → 100%):**
- Profile-specific BOM: UK bed (Bed_King), UK coffee table (Coffee_Table_Rect_1200)
- Armchair library component (1430x1400x920) — needs IFC extraction or parametric
- Dining chair count (3 placed vs 6 ref) — FurniturePlacer room constraint
- Curtain wall panels (6 IfcPlate glazed panels on west face)
- Wall dimensions (7 compiled vs 11 ref = 4 missing curtain wall panels)
- Slab/Roof dimensions (foundation slab + roof volume)

**Duplex remaining gaps (42% → 100%):**
- Wall near→exact matching (12 near, 0 exact) — investigate dimension mismatch
- Slab dimension alignment (2/21 match)
- Furniture gaps (22/61 match)
- Railing generation (0/4)

**Architectural: Rosetta Trainer Tool:**
- Auto-analyze spatial_checker output for all pairs
- Generate draft migration SQL from reference DB queries
- Automate the "epoch" loop: gap identification → migration → score measurement

---

## Session Summary — Phase 122A: STR Grammar Fix + DB Documentation

### What Was Done

Two structural defects fixed + DB schema documentation added:

**1. BeamTypeResolver** (`library/BeamTypeResolver.java`):
- New lazy singleton resolver following WallTypeResolver pattern
- Loads `ad_beam_type` + `ad_beam_type_rule` from component_library.db
- Profile-aware: profile-specific rules first, generic fallback
- Span-range matching: rule applies when span in [span_min_m, span_max_m]
- Returns null for profiles without rules → caller skips beam generation

**2. Migration 122** (`migration/migration_122_structural_grammar.sql`):
- `ad_beam_type` table: 6 beam types (4 FLOOR + 2 LINTEL, all RC)
- `ad_beam_type_rule` table: 5 rules for `Malaysian_Institutional` profile
- `ad_column_type` table: 3 column types (750/400/300mm)
- No rules for UK/US/MY_Residential → residential buildings get no grid beams

**3. StoreyCompiler beam generation fix** (`StoreyCompiler.java`):
- **Path 2 (per-room grid beams) REMOVED** — was generating beams for every room with `structuralGrid=true`, overlapping with building-wide frame
- **Path 3 (building-wide frame) made conditional** — only generated if profile has FLOOR beam rules (testEntry != null)
- Frame beams now use resolved section dimensions (e.g., 300x600mm, 300x750mm)
- Net effect: residential = lintels only; institutional = properly-sized RC frame

**4. StructuralPlacer parametrized** (`StructuralPlacer.java`):
- New `placeGridBeams()` overload accepting `beamWidthM`, `beamDepthM` parameters
- Replaces hardcoded `LINTEL_DEPTH` (200mm) and `0.3` (300mm)
- Old 5-arg overload delegates to new 7-arg with legacy defaults

**5. DB README documentation**:
- `library/README.md` — component_library.db schema (48 tables, query patterns)
- `output/README.md` — compiled output DB schema (R-tree trap documented)
- `reference/rosetta/README.md` — reference DB schema + tools

### Beam Count Impact

| Building | Profile | Beams Before | Beams After | Change |
|----------|---------|-------------|-------------|--------|
| Duplex | US_Residential | ~154 | **20** (lintels) | -134 |
| SampleHouse | UK_Residential | ~20 | **7** (lintels) | -13 |
| Terminal | Malaysian_Institutional | ~350 | **350** (92 lintels + 258 frame 300x750mm) | 0 |
| Condo | (none) | 477 | **477** (lintels) | 0 |

### Element Counts (Phase 122A)

| Building | Elements Before | Elements After | Change |
|----------|----------------|----------------|--------|
| CONDO-MID | 10,978 | **10,124** | -854 (removed per-room grid beams) |
| SampleHouse | 121 | **108** | -13 |
| Duplex | 549 | **549** | 0 |
| Terminal | 2,726 | **2,726** | 0 |

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| CondoMidEndToEndTest | PASS (10,124 elements) |
| SampleHouseEndToEndTest | PASS (108 elements) |
| TBLKTNDuplexEndToEndTest | PASS (549 elements) |
| TerminalEndToEndTest | PASS (2,726 elements) |
| SchoolEndToEndTest | PASS |
| TBLKTNEndToEndTest | PASS |
| TBLKTN2SEndToEndTest | PASS |
| TBLKTNCompactEndToEndTest | PASS |
| Duplex ARC X-ray | 40% (unchanged — beams not ARC) |

### Files

| File | Action |
|------|--------|
| `migration/migration_122_structural_grammar.sql` | NEW — ad_beam_type, ad_beam_type_rule, ad_column_type |
| `src/.../library/BeamTypeResolver.java` | NEW — lazy singleton resolver (~175 lines) |
| `src/.../library/StructuralPlacer.java` | MODIFIED — new placeGridBeams overload with beam dimensions |
| `src/.../dsl/StoreyCompiler.java` | MODIFIED — removed Path 2, conditional Path 3 via resolver |
| `library/README.md` | NEW — component_library.db schema documentation |
| `output/README.md` | NEW — output DB schema documentation |
| `reference/rosetta/README.md` | NEW — reference DB schema + tools |

### What's Next — Phase 122B+

**Priority 1 — Rosetta Stone 100% replication** (SampleHouse + Duplex):
- SampleHouse curtain wall (6 IfcPlate glazed panels)
- UK door schedule: interior 810x2110, exterior 1810x2110
- Duplex slab dimension alignment (room-specific)
- Duplex wall near→exact matching (12 near-match, 0 exact)

**Priority 2 — Federation convergence** (Terminal):
- Terminal opening count gap (39 doors vs 135 ref, 53 windows vs 236 ref)
- Building template expansion (ad_building_template + ad_floor_template)

---

## Session Summary — Phase 121: Score Convergence — Finish Slabs, Wall Height, Axis Fix

### What Was Done

Three targeted changes to improve Rosetta X-ray scores:

**1. Per-room finish slabs** (`BuildingWriter.java`):
- Grammar Rule 6: SLAB = structural + finish — now implemented
- Wet rooms (BATHROOM, TOILET, TOILET_BLOCK, KITCHEN) → ceramic tile 13mm
- Dry rooms (BEDROOM, LIVING, CORRIDOR, OFFICE, LOBBY, DINING, etc.) → wood 19mm
- Written as `IfcSlab` type "FINISH" with per-room bounding box
- Duplex: +17 finish slabs (20 total IfcSlab, was 3), 8 near-match reference

**2. Wall height fix** (`StoreyCompiler.java`):
- Added `wallMaxZ` to StoreyBuildContext: `storeyHeight - slabThickness` for non-top floors
- Top floor walls retain full storey height (no slab above)
- Perimeter walls, interior walls, and partition walls all use `wallMaxZ`
- Prevents wall-slab overlap at floor boundaries
- Duplex ground floor walls: 2850mm (was 3000mm), upper floor: full 3000mm

**3. SampleHouse axis correction** (`examples/Ifc4_SampleHouse.bim`):
- Switched from `size:WxD` (stacking along Y) to `GRID bounds:` layout
- Grid: A=0, B=9.3, C=13.8 (X) / 1=0, 2=2.0, 3=5.5 (Y)
- Living=A1-B3 (west), Entrance=B1-C2 (SE), Bedroom=B2-C3 (NE)
- Building now 15.4m×7.1m long along X (was 9.3m×16.0m long along Y)
- Matches reference orientation: 16.87m×8.67m long along X
- Windows: 4/4 exact match (was 5/7 at 71%)

### X-ray Scores (ARC discipline)

| Pair | ARC X-ray (was) | ARC X-ray (now) | ARC Overall (was) | ARC Overall (now) |
|------|-----------------|-----------------|-------------------|-------------------|
| SampleHouse | 17% (6/35) | **20%** (7/35) | 26% | **28%** |
| Duplex | 26% (49/183) | **31%** (57/183) | 37% | **40%** |
| Terminal | 2% (85/3660) | **2%** (85/3660) | 8% | **8%** |

**Duplex breakdown:**
- Wall thickness: 48/57 (84%) — unchanged
- Opening sizes: 15/38 (39%) — unchanged
- Furniture sizes: 22/61 (36%) — unchanged
- Slab/Roof sizes: **2/21 (9%)** — was 0/21 (0%)
- X-ray near-match: **57/183 (31%)** — was 49/183 (26%)

**SampleHouse breakdown:**
- Wall thickness: 5/5 (100%) — unchanged
- Opening sizes: 5/7 (71%) — unchanged
- Windows: **4/4 exact** (100%) — axis fix enabled matching
- Doors: 3 compiled = 3 reference (count aligned)

### Spatial Digests (Phase 121)

| Building | Elements | SHA256 Digest |
|----------|----------|--------------|
| CONDO-MID | 10,978 | *(changed — finish slabs + wall height)* |
| SampleHouse | 121 | `6d7d8cbf8b2f12eeeef2189c9014a594ece7305a3df6b4216112ac4f54608f49` |
| Duplex | 549 | *(changed — finish slabs + wall height)* |
| Terminal | 2,726 | *(changed — finish slabs + wall height)* |

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| CondoMidEndToEndTest | PASS |
| SampleHouseEndToEndTest | PASS (121 elements, was 109) |
| TBLKTNDuplexEndToEndTest | PASS (549 elements, was 533) |
| TerminalEndToEndTest | PASS |
| SchoolEndToEndTest | PASS |
| TBLKTNEndToEndTest | PASS |
| TBLKTN2SEndToEndTest | PASS |
| TBLKTNCompactEndToEndTest | PASS |

### Files

| File | Action |
|------|--------|
| `src/.../dsl/BuildingWriter.java` | MODIFIED — per-room finish slab generation + `getFinishSlabThickness()` |
| `src/.../dsl/StoreyCompiler.java` | MODIFIED — `wallMaxZ` field, wall height = storeyHeight - slabThickness |
| `examples/Ifc4_SampleHouse.bim` | MODIFIED — GRID layout (axis correction) |
| `PROGRESS.md` | MODIFIED — session closeout |

### What's Next — Phase 122

**Priority 1 — SampleHouse wall dimension matching** (28% → 35%+):
- Wall X-ray shows 0 exact, 1 near-match (7 compiled vs 11 reference)
- Reference has 6 IfcPlate curtain wall panels — compiler doesn't generate these
- Interior door size mismatch (880×2150 ref vs 1860×2110 compiled)
- Need UK_Residential door schedule: interior 810x2110, exterior 1810x2110

**Priority 2 — Duplex slab dimension alignment** (9% → 30%+):
- 17 finish slabs generated but dimensions don't match reference room sizes
- Reference has per-zone slabs at specific dimensions (e.g., 6200×3700, 5800×2200)
- Need room dimensions to match reference more closely

**Priority 3 — Duplex wall near→exact promotion**:
- 12 wall near-matches, 0 exact matches
- Wall height now 2850mm; reference varies 288-3388mm
- Need storey-specific wall heights from DSL

---

## Session Summary — Phase 120: Thesaurus → AD Tables → X-ray Validation

### What Was Done

Applied the Thesaurus cross-stone analysis to fix AD table data, added Malaysian
institutional wall types, and established the 3rd Rosetta pair (SJTII Terminal).

**1. Migration 120** (`migration/migration_120_thesaurus_alignment.sql`):
- **Fixed US BEDROOM window**: US_CASEMENT_819 (819×759) → US_FIXED_2800 (2800×2410)
- **Fixed US KITCHEN window**: US_CASEMENT_819 → US_FIXED_750 (750×2200)
- **Added 54mm furring rule**: BATHROOM→BATHROOM interior wall = INTERIOR_FURRING_38
- **Added 550mm party wall**: PARTY_CMU_550 type + US_Residential rule
- **Added Malaysian wall types**: EXTERIOR_MY_BRICK_150 (150mm), EXTERIOR_MY_BRICK_250 (250mm),
  INTERIOR_MY_AHU_230 (230mm), COPING_MY_300 (300mm)
- **Added Malaysian wall rules**: EXTERIOR + INTERIOR for Malaysian_Institutional profile
- **Added BEDROOM WARDROBE slot**: WARDROBE_SET BOM + ad_room_slot entry

**2. Terminal DSL** (`examples/SJTII_Terminal.bim`):
- 3rd Rosetta pair — Malaysian institutional airport terminal
- Grid layout (10-12m X / 8m Y spacing — from column grid analysis)
- 4 storeys, 37 rooms: LOBBY, OPEN_PLAN, OFFICE, TOILET_BLOCK, DINING, CORRIDOR, STAFFROOM
- Profile `Malaysian_Institutional` → 150mm BrickPlaster walls (91% match!)

**3. Terminal E2E test** (`TerminalEndToEndTest.java`):
- Compiles SJTII_Terminal.bim → output/sjtii_terminal.db
- 2689 elements across 22 IFC classes
- 8th E2E test, all pass

### X-ray Scores (ARC discipline)

| Pair | Wall Match | Opening Match | X-ray Near | ARC Overall |
|------|-----------|---------------|------------|-------------|
| SampleHouse | 5/5 (100%) | 5/7 (71%) | 6/35 (17%) | 17/64 (26%) |
| Duplex | 48/57 (84%) | 15/38 (39%) | 49/183 (26%) | 134/360 (37%) |
| **Terminal** | **306/333 (91%)** | 52/371 (14%) | 85/3660 (2%) | 445/5071 (8%) |

**Terminal wall 91%** validates Grammar Rule 1 across 3 construction traditions:
UK (290mm), US (417mm), MY (150mm). Same rule, different layers.

### Spatial Digests (Phase 120)

| Building | Elements | SHA256 Digest |
|----------|----------|--------------|
| CONDO-MID | 10,516 | `406d7b96f01157fcb6927c45c57cce5e8c6f37bd6be91fb6b1456974d3e1aa5c` |
| SampleHouse | 109 | `90130860921943e8b3dff44c9f28368a54708cdc46d9422fbc4f56cb6f92ca24` |
| Duplex | 533 | `f4ddf9996df30844b71fa3766b6d93916426caa533204e71958973c3a95a4f9c` |
| Terminal | 2,689 | `a9401920b80419d9fac07f088ab5e382f1b7b2d3d83cdf3cef88069d4d31255a` |

### Verification

| Check | Result |
|-------|--------|
| Migration applied | 5 wall types, 4 rules, 2 window fixes, 1 slot, 1 BOM |
| `mvn compile -q` | PASS |
| CondoMidEndToEndTest | PASS (10516 elements) |
| SampleHouseEndToEndTest | PASS (109 elements) |
| TBLKTNDuplexEndToEndTest | PASS (533 elements) |
| **TerminalEndToEndTest** | **PASS (2689 elements)** |
| SchoolEndToEndTest | PASS |
| TBLKTNEndToEndTest | PASS |
| TBLKTN2SEndToEndTest | PASS |
| TBLKTNCompactEndToEndTest | PASS |

### Files

| File | Action |
|------|--------|
| `migration/migration_120_thesaurus_alignment.sql` | NEW — AD table corrections from Thesaurus |
| `examples/SJTII_Terminal.bim` | NEW — 3rd Rosetta pair DSL |
| `src/.../TerminalEndToEndTest.java` | NEW — 8th E2E test |
| `reference/rosetta/GRAMMAR.md` | MODIFIED — score history + Phase 120 detail |
| `PROGRESS.md` | MODIFIED — session closeout |

### Architectural Insight — DSL as True Catalog Selector

The Terminal DSL has 37 manually-defined rooms — this violates the catalog-selector
principle. Individual room instances (`OFFICE "admin_1"`, `OFFICE "admin_2"`) should
NOT appear in the DSL. Instead:

```
BUILDING "SJTII_Terminal" type:TERMINAL_4F profile:"Malaysian_Institutional"
```

The metadata expands this via:
- `ad_building_template` → floor types per level
- `ad_floor_template` → room counts and assemblies per floor
- `ad_room_slot` → room contents per room type

The highest level defines everything. No individual instances. This is the priority
for Phase 121: building-level template selection where metadata handles all repetition.

### What's Next — Phase 121

**Priority 1 — Building Template Tables (architectural)**:
- `ad_building_template` table: building_type → floor_template_id per level
- `ad_floor_template` table: template_id → room_type counts + grid positions
- Parser + compiler support for `type:TERMINAL_4F` expansion
- Terminal DSL reduces from 80 lines to ~5 lines

**Priority 2 — Terminal element count gap**:
- Terminal produces 2689 elements; reference has 15K (incl. MEP)
- ARC only: 120 walls vs 330 ref, 39 doors vs 135 ref, 53 windows vs 236 ref
- Building template expansion will multiply room count → element count

**Priority 3 — Remaining Duplex gaps**:
- Wall near→exact matching (14/57 near, 0 exact)
- Finish floor slabs (ref has 21, compiler has 4)
- Structural overcount (154 beams vs 8 ref)

---

## Session Summary — Phase 119F: Multi-Discipline Thesaurus from Federation DB

### What Was Done

Applied the Linguist's Method to the 3rd Rosetta Stone (SJTII Terminal — Malaysian airport
terminal, 15,104 elements, 8 disciplines). First non-residential stone; first with real
multi-discipline overlap data.

**1. Dictionary extraction with discipline filter** (`tools/rosetta_dictionary.py`):
- Added `--discipline` flag (comma-separated: ARC,STR,FP,ACMV,CW,ELEC,SP,LPG)
- Generated per-discipline dictionaries: ARC (1790 lines), STR (1545), FP (106), ACMV (99)
- Saved to `reference/rosetta/Terminal_dictionary_*.txt`

**2. Cross-discipline overlap tool** (`tools/cross_discipline_checker.py`):
- New single-purpose tool: 9 analysis sections using rtree bbox overlap queries
- Sections: pipe-in-wall, duct-slab, column-wall, beam inventory, sprinkler-ceiling,
  storey mapping, wall finish types, column grid regularity, discipline overlap matrix
- Saved analysis to `reference/rosetta/Terminal_cross_discipline.txt`

**3. Terminal Thesaurus** (`reference/rosetta/TERMINAL_THESAURUS.md`):
- 6-section cross-reference: Walls, Openings, Furniture, Structural, Cross-Discipline, Storey Convention
- 3rd dialect column added (MY institutional alongside UK residential and US residential)
- Key discoveries documented with evidence counts

**4. Grammar expanded** (`reference/rosetta/GRAMMAR.md`):
- Rule 1 updated with Malaysian dialect (150mm BrickPlaster — verified across 3 stones)
- Rule 7 upgraded from "unverified" to "partially verified" (3rd stone confirms template_grid)
- 6 new cross-discipline rules (Rules 9-14) — all evidence-based

### Key Discoveries

| Finding | Evidence | Grammar Rule |
|---------|----------|-------------|
| Pipes fit inside walls | 766 overlaps, 99.3% pipe.dia < wall.thick | Rule 9 |
| Ceramic finish = wet room | 70/330 walls (21%), co-located with SP/CW | Rule 10 |
| Columns at wall junctions | 45/59 positions (76%) at ≥2 wall intersections | Rule 11 |
| RC beam D/S ratio | 248 beams, D/S = 0.10-0.15 | Rule 12 |
| Ceiling void ~1m | 4 storeys consistent ~900-1000mm | Rule 13 |
| Sprinkler drop 0.5-1.2m | 909 terminals measured | Rule 14 |

**Malaysian wall simplification**: 150mm single-type for 93% of walls (no insulation
cavity in tropical climate). Grammar Rule 1 still holds — just simpler layers.

**Dual storey naming**: Malay (Aras 01-04, Aras Tanah, Aras Bumbung) vs English
(GROUND FLOOR LEVEL, FIRST-FOURTH FLOOR LEVEL). Not an error — reflects different
discipline perspectives on "which floor": ARC/STR = structural level, MEP = service zone.

### Files

| File | Action |
|------|--------|
| `tools/rosetta_dictionary.py` | MODIFIED — added `--discipline` filter via argparse |
| `tools/cross_discipline_checker.py` | NEW — 9-section bbox overlap analysis tool |
| `reference/rosetta/Terminal_dictionary_ARC.txt` | NEW — ARC discipline dictionary (1790 lines) |
| `reference/rosetta/Terminal_dictionary_STR.txt` | NEW — STR discipline dictionary (1545 lines) |
| `reference/rosetta/Terminal_dictionary_FP.txt` | NEW — FP discipline dictionary (106 lines) |
| `reference/rosetta/Terminal_dictionary_ACMV.txt` | NEW — ACMV discipline dictionary (99 lines) |
| `reference/rosetta/Terminal_cross_discipline.txt` | NEW — cross-discipline analysis output |
| `reference/rosetta/TERMINAL_THESAURUS.md` | NEW — 3rd stone thesaurus (6 sections) |
| `reference/rosetta/GRAMMAR.md` | MODIFIED — Rules 9-14 added, Rule 1+7 updated |
| `PROGRESS.md` | MODIFIED — session closeout |

### What's Next — Phase 119G

From the ARC-focused action list (ordered by X-ray impact):
1. Add missing Duplex window types — 750x2200 (4 ref), 2800x2410 (4 ref)
2. Wall near→exact — Duplex 14/57 near-match; 0 exact
3. Add finish floor slabs — Duplex ref has 21 slabs (structural+finish per zone)
4. Reduce structural overcount — SH: 26→20 members, Duplex: 154→4 members

From cross-discipline grammar (new from 119F):
5. Add Malaysian wall types to `ad_wall_type` — 150mm BrickPlaster, 250mm
6. Add wall finish → room function mapping to `ad_wall_type`
7. Column grid resolver for institutional/commercial templates

---

## Session Addendum — Phase 119E: Unified Extract Tool + Federation as 3rd Stone

### Unified `tools/extract.py`
Single tool replaces 5 bespoke scripts:
- `extract_all_components.py`, `import_ifc_furniture.py`, `extract_duplex_components.py` (Layer 1)
- `populate_sample_house_db.py`, `populate_duplex_db.py` (Layer 3)

Handles both `.ifc` (tessellates via ifcopenshell) and `.db` (copies pre-tessellated data).
Modes: `--to library` (LOD400) or `--to reference` (Rosetta). Filters: `--classes`, `--exclude`, `--discipline`.

### Federation DB as 3rd Rosetta Stone
Extracted `database/enhanced_federation_GI.db` → `reference/rosetta/SJTII_Terminal_extracted.db`:
- **15,104 elements** across 8 disciplines (excluded IfcPlate 33K, IfcOpeningElement 631, IfcReinforcingBar 2660)
- ARC: 1,400 elements (330 walls, 236 windows, 176 furniture, 135 doors, 91 slabs)
- FP: 6,863 | ACMV: 1,621 | CW: 1,431 | STR: 1,429 | ELEC: 1,172 | SP: 979 | LPG: 209

### Developer Guide Updated
- Full "Data Provenance" section with 3-layer diagram (Geometry / Metadata / Rosetta)
- Extraction tool documentation
- Rosetta Stone pairs table (all 3 active)

---

## Session Summary — Phase 119E: Opening Count Alignment

### Problem
The compiler generated too many openings vs reference IFC models — the #1 action by ARC X-ray impact. Two mechanisms double-generated doors, and auto-windows fired on walls that shouldn't have them.

### Fixes Applied

1. **BOM Auto-Door Duplicate** (`StoreyCompiler.java`):
   - Pre-compute `adjacencyParticipants` set (rooms with intra-storey `adjacent:` constraints)
   - BOM auto-door now skipped for rooms that get their door from the adjacency handler
   - Condo unaffected (no `adjacent:` constraints → empty set)

2. **Auto-Window Overcount** (`StoreyCompiler.java`):
   - If room has ANY explicit WINDOW declarations → skip all auto-windows for that room
   - If a wall already has a DOOR → skip auto-window on that wall
   - Prevents phantom windows on entrance walls and rooms with deliberate window specs

3. **SampleHouse DSL Fix** (`examples/Ifc4_SampleHouse.bim`):
   - Bedroom `WINDOW south` → `WINDOW north` (south is interior toward entrance)

### Score Impact

| Pair | ARC X-ray Before | ARC X-ray After | ARC Overall Before | ARC Overall After |
|------|-----------------|-----------------|-------------------|-------------------|
| SampleHouse | 17% (6/35) | **17%** (6/35) | 28% | **26%** |
| Duplex | 27% (51/183) | **27%** (51/183) | 37% | **36%** |

**Opening count alignment** (the real win):

| Pair | Metric | Before | After | Reference |
|------|--------|--------|-------|-----------|
| SampleHouse | Doors | 5 | **3** | 3 (EXACT) |
| SampleHouse | Windows | 7 | **4** | 4 (EXACT) |
| SampleHouse | Opening match | — | **71%** (5/7) | — |
| Duplex | Doors | ~26 | **12** | 14 (0.86x) |
| Duplex | Windows | ~10 | **8** | 24 (0.33x) |
| Duplex | Opening match | — | **39%** (15/38) | — |

### Element Counts

| Building | Before | After | Change |
|----------|--------|-------|--------|
| CONDO-MID | 10,516 | **10,516** | 0 (safe) |
| SampleHouse | 116 | **109** | -7 |
| Duplex | 559 | **533** | -26 |

### New Spatial Digests

| Building | Elements | SHA256 Digest |
|----------|----------|--------------|
| CONDO-MID | 10,516 | `406d7b96f01157fcb6927c45c57cce5e8c6f37bd6be91fb6b1456974d3e1aa5c` |
| SampleHouse | 109 | `90130860921943e8b3dff44c9f28368a54708cdc46d9422fbc4f56cb6f92ca24` |
| Duplex | 533 | `f4ddf9996df30844b71fa3766b6d93916426caa533204e71958973c3a95a4f9c` |

### All 7 E2E Tests Pass
Condo, SampleHouse, Duplex, TB-LKTN, TB-LKTN-2S, TB-LKTN-Compact, School

### What's Next — Phase 119F
From the ARC-focused action list (ordered by X-ray impact):
1. ~~Prune opening overcount~~ ✅ DONE (Phase 119E)
2. Add missing Duplex window types — 750x2200 (4 ref), 2800x2410 (4 ref)
3. Wall near→exact — Duplex 14/57 near-match; 0 exact. Close but dims not bucketizing
4. Add finish floor slabs — Duplex ref has 21 slabs (structural+finish per zone)
5. Reduce structural overcount — SH: 26→20 members, Duplex: 154→4 members

---

## Session Summary — Phase 119D: Linguist's Method Epistemology

### Approach: Dictionary → Thesaurus → Grammar

**Step 1 (Dictionary)**: Extracted all spatial facts from both reference stones.
- Tool: `tools/rosetta_dictionary.py` — 11-section spatial skeleton extractor
- Saved: `reference/rosetta/SampleHouse_dictionary.txt`, `reference/rosetta/Duplex_dictionary.txt`
- Key insight: read both stones completely before writing any rules

**Step 2 (Thesaurus)**: Mapped equivalent concepts across UK/US dialects.
- Document: `reference/rosetta/THESAURUS.md`
- Key discovery: Duplex window thin = wall thickness (417mm); opening mesh depth ≠ spatial depth
- Cross-stone equivalences: wall=Σ(layers), opening.thin=frame_depth, furniture=catalog(profile,room)

**Step 3 (Grammar)**: Formalized 8 rules from first principles.
- Document: `reference/rosetta/GRAMMAR.md`
- 6 verified rules (common to both stones), 2 unverified (need more data)

### Concrete Fixes Applied

1. **Migration 119D** (`migration/migration_119D_opening_depth_alignment.sql`):
   - Fixed US window depth: 100→417mm (fill wall void)
   - Fixed US skylight depth: 100→178mm
   - Fixed Duplex door depths: entry=467mm, glass=467mm, interior=174mm
   - Updated generic family depths to reasonable frame values

2. **OpeningWriter.java** — Family depth overrides mesh depth in bbox:
   - Library door path: `physicalDepth = door.depth()` when family depth available
   - Library window path: same override for windows
   - LOD400 mesh geometry unchanged — only spatial envelope (bbox) corrected

### Score Impact

| Pair | X-ray Before | X-ray After | Overall Before | Overall After |
|------|-------------|-------------|----------------|---------------|
| SampleHouse | 2% | **10%** | 15% | **21%** |
| Duplex | 5% | **7%** | 12% | **13%** |

**ARC-only scores** (with `--discipline ARC` filter):

| Pair | ARC X-ray | ARC Overall | Notes |
|------|----------|-------------|-------|
| SampleHouse | **17%** | **28%** | 6/35 near-matched |
| Duplex | **27%** | **37%** | 51/183 near-matched |

MEP-only Duplex: 3% (32/890) — tracked separately.

### Key Rosetta Documents
- Strategy: `docs/TheRosettaStoneStrategy.txt` (updated with idioms + discipline separation)
- Dictionary: `reference/rosetta/SampleHouse_dictionary.txt`, `Duplex_dictionary.txt`
- Thesaurus: `reference/rosetta/THESAURUS.md`
- Grammar: `reference/rosetta/GRAMMAR.md`
- Spatial checker: `python3 tools/spatial_checker.py [out] [ref] --discipline ARC|MEP|STR`

### What's Next — Phase 119E: Cross-Discipline Grammar + Federation DB

**Key insight**: all construction needs ARC+MEP+STR working together. Previous low scores came from treating them independently. The grammar must derive CROSS-DISCIPLINE phrases:

**Cross-discipline correlations to extract:**
- **Wall↔MEP**: Plumbing walls (184mm) exist BECAUSE of pipes inside. MEP routing drives wall type classification. Grammar: `WET_ROOM adjacent → PLUMBING_WALL (not PARTITION)`
- **Slab↔Room function**: Finish floor type depends on room (wet=ceramic 13mm, dry=wood 19mm). Grammar: `ROOM.function → SLAB.finish_type`
- **Structure↔ARC**: Beam placement follows room span. Column grid follows room layout. Grammar: `ROOM.span > threshold → BEAM at midspan`
- **Opening↔MEP**: Ventilation windows in wet rooms. Electrical outlets near doors. Grammar: `WET_ROOM + exterior → VENTILATION window`

**Federation DB as 3rd+ Rosetta Stone:**
`database/enhanced_federation_GI.db` contains many building types beyond residential (offices, commercial, institutional). Adding it to Rosetta gives:
- More stones = more grammar coverage + cross-type validation
- MEP patterns differ by building type (office HVAC vs residential plumbing)
- Structural patterns (steel frame office vs timber residential)
- The dictionary extractor (`tools/rosetta_dictionary.py`) already works on any DB

**ARC-focused actions (ordered by X-ray impact):**
1. Prune opening overcount — SH: 5→3 doors, 7→4 windows. Duplex: 26→14 doors
2. Add missing Duplex window types — 750x2200 (4 ref), 2800x2410 (4 ref)
3. Wall near→exact — Duplex 14/57 near-match; 0 exact. Close but dims not bucketizing
4. Add finish floor slabs — Duplex ref has 21 slabs (structural+finish per zone)
5. Reduce structural overcount — SH: 26→20 members, Duplex: 154→4 members

---

## Session Summary — Phase 118C: SlotRegistry + WorkerRegistry + Rosetta Stone Strategy

### Rosetta Stone Setup

**Strategy**: Use real IFC files as fossil truth to measure compiler fidelity.
See `docs/TheRosettaStoneStrategy.txt` for full rationale.

| Pair | Source IFC | Reference DB | DSL | Output DB | X-ray Baseline |
|------|-----------|-------------|-----|-----------|----------------|
| SampleHouse | `Ifc4_SampleHouse.ifc` | `reference/rosetta/Ifc4_SampleHouse_extracted.db` (55 elem) | `examples/Ifc4_SampleHouse.bim` | `output/ifc4_sample_house.db` (116 elem) | **2%** |
| Duplex | `Ifc2x3_Duplex_Architecture.ifc` | `reference/rosetta/Ifc2x3_Duplex_extracted.db` (1085 elem) | `examples/Ifc2x3_Duplex.bim` | `output/ifc2x3_duplex.db` (559 elem) | **5%** |

Run spatial checker:
```
python3 tools/spatial_checker.py  # defaults to Duplex
python3 tools/spatial_checker.py output/ifc4_sample_house.db reference/rosetta/Ifc4_SampleHouse_extracted.db
```

### SlotRegistry + WorkerRegistry

### What Was Done

**Replaced ad_space_type_furniture lookup with ad_room_slot as single source of truth for furniture dispatch — zero behavior change:**

1. **`migration/migration_118C_slot_registry.sql`** — Aligned ad_room_slot FURNITURE slots with ad_space_type_furniture:
   - UPDATE OFFICE assembly_id: WORKSTATION_SET → ROOM_FURNITURE (was a mismatch)
   - INSERT 3 missing FURNITURE slots: LOBBY, OPEN_PLAN, STAFFROOM (all ROOM_FURNITURE)
   - Now 8 FURNITURE slots match exactly the 8 bomId entries in ad_space_type_furniture

2. **`SlotRegistry.java`** — Lazy singleton reads ad_room_slot (follows WallTypeResolver pattern):
   - `getFurnitureAssemblyId(roomType)` → returns assembly_id for FURNITURE slot, or null
   - `getSlotsForType(roomType)` → returns all slots ordered by priority (for Phase 118D)
   - Graceful degradation if table missing

3. **`WorkerRegistry.java`** — Maps assembly_id → BundleWorker with caching:
   - `registerDefault(factory)` — catch-all factory (118C: all → FurnitureWorker)
   - `getWorker(assemblyId)` → cached BundleWorker instance
   - Replaces `Map<String, FurnitureWorker> workerCache` from 118B

4. **`StoreyCompiler.java`** — Rewired furniture routing:
   - Primary path: `SlotRegistry.getFurnitureAssemblyId()` → `WorkerRegistry.getWorker()` → `execute()`
   - Fallback path: `FurnitureTypeResolver` kept only for CANTEEN/SEATING/WORKSTATION (no ad_room_slot entry)
   - Same behavior for all 8 BOM-driven room types + 3 fallback types

### Golden Digests (Phase 118C — Unchanged)

| Building | Elements | SHA256 Digest |
|----------|----------|--------------|
| CONDO-MID | 10,516 | `18b3062356ecef701d6c80bb979f9f02668e69fa202976925b297b88405b44c6` |
| TB-LKTN-DUPLEX | 559 | `f0ff9b03dfcb3006de4ea8dbb1169b383da06b4384f8e8474e3fefd06679a180` |

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| CondoMidEndToEndTest | PASS (10516 elements, digest unchanged) |
| SchoolEndToEndTest | PASS |
| TBLKTNEndToEndTest | PASS |
| TBLKTN2SEndToEndTest | PASS |
| TBLKTNCompactEndToEndTest | PASS |
| TBLKTNDuplexEndToEndTest | PASS (559 elements, digest unchanged) |
| Sanity (condo_mid.db) | 23 PASS, 4 FAIL, 6 WARN (identical to baseline) |

### Files

| File | Action |
|------|--------|
| `migration/migration_118C_slot_registry.sql` | NEW — align ad_room_slot with ad_space_type_furniture |
| `src/main/java/com/bim/compiler/library/SlotRegistry.java` | NEW — lazy singleton reads ad_room_slot |
| `src/main/java/com/bim/compiler/library/WorkerRegistry.java` | NEW — assembly_id → BundleWorker cache |
| `src/main/java/com/bim/compiler/dsl/StoreyCompiler.java` | MODIFIED — SlotRegistry+WorkerRegistry replaces FurnitureTypeResolver+workerCache |
| `examples/Ifc2x3_Duplex.bim` | NEW — Rosetta Stone DSL for Duplex (renamed from TB-LKTN-DUPLEX) |
| `examples/Ifc4_SampleHouse.bim` | NEW — Rosetta Stone DSL for SampleHouse |
| `src/main/java/com/bim/compiler/dsl/SampleHouseEndToEndTest.java` | NEW — E2E test for SampleHouse |
| `src/main/java/com/bim/compiler/dsl/TBLKTNDuplexEndToEndTest.java` | MODIFIED — points to Ifc2x3_Duplex.bim, output/ifc2x3_duplex.db |
| `reference/rosetta/Ifc2x3_Duplex_extracted.db` | NEW — extracted ground truth from Duplex IFC |
| `reference/rosetta/Ifc4_SampleHouse_extracted.db` | NEW — extracted ground truth from SampleHouse IFC |
| `scripts/populate_sample_house_db.py` | NEW — IFC extraction for SampleHouse |
| `tools/spatial_checker.py` | MODIFIED — defaults updated to new paths |
| `docs/TheRosettaStoneStrategy.txt` | NEW — strategy document |

## Session Summary — Phase 119 Step 1: Wall Thickness Alignment + Spatial Checker Overhaul

### What Was Done

**1. Profile-aware wall thickness resolution — all walls now use `ad_wall_type_rule` with profile matching:**

- **`migration/migration_119_wall_alignment.sql`** — Added `profile` column to `ad_wall_type_rule`, UK residential wall types (290mm exterior brick, 95mm partition), and profile-specific rules (priority 50 beats generic 100).
- **`WallTypeResolver.java`** — `profile` field on `WallTypeRule`, 5-arg `resolveThickness` overload with two-pass resolution: profile-specific first, generic fallback. Graceful degradation if `profile` column missing.
- **`StoreyCompiler.java`** — Perimeter walls resolve exterior thickness via profile. Interior walls AND partition walls (uncovered edges) also pass profile. New `compilePerimeterWall` overload with thickness+cladding.
- **`MultiUnitCompiler.java`** — Profile threaded through wall classification chain. **Fixed `createUnitBuildingDefinition`/`createSharedBuildingDefinition`** which dropped profile/protocol/lod/facade from unit-level BuildingDefinition.

**2. Spatial Checker overhaul — broader and smarter comparison:**

- **IFC class dictionary**: `IfcPlate→WALL` (was PANEL), so compiled walls can match reference `IfcWall` in X-ray
- **Slab/Roof comparison** (Section 7): new section comparing slab and roof bounding box signatures
- **Two-tier X-ray matching**: exact (10mm bucket) + near-match (thin dim ±5mm, long dims ±10%)
- **Near-match summary**: OVERALL score now uses near-match, giving credit for elements with correct thickness but different lengths

### Rosetta Stone Scores

| Pair | Wall Match | Overall (was) | Overall (now) |
|------|-----------|---------------|---------------|
| SampleHouse | **5/5 (100%)** | 2% | **8%** |
| Duplex | **42/57 (73%)** | 5% | **8%** |

Duplex X-ray near-matches: WALL 14, PIPE 14, FITTING 14, FURNITURE 12, TERMINAL 4 = 58/1085

### Golden Digests (Phase 119)

| Building | Elements | SHA256 Digest |
|----------|----------|--------------|
| CONDO-MID | 10,516 | `eabe90b1c919d49a27f9a42c51ead9da2e87486343123be21a560e24ed5c94c3` |
| SampleHouse | 116 | `81c78863dc36773445c2b45a56e06dcc2198b24d34da1bbbbca709f9f58080f1` |
| Duplex | 559 | `a7da6e84a8645df750a20deb3abd607c82bf61133b2c42eed50682443c0e7669` |

All digests changed from Phase 118C baseline (partition walls now properly resolved via ad_wall_type).

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| CondoMidEndToEndTest | PASS (10516 elements) |
| SampleHouseEndToEndTest | PASS (116 elements) |
| TBLKTNDuplexEndToEndTest | PASS (559 elements) |
| SchoolEndToEndTest | PASS |
| TBLKTNEndToEndTest | PASS |
| TBLKTN2SEndToEndTest | PASS |

### Files

| File | Action |
|------|--------|
| `migration/migration_119_wall_alignment.sql` | NEW — profile column + UK wall types + rules |
| `src/.../library/WallTypeResolver.java` | MODIFIED — profile-aware 5-arg overload, two-pass resolution |
| `src/.../dsl/StoreyCompiler.java` | MODIFIED — all wall paths use resolver with profile |
| `src/.../dsl/MultiUnitCompiler.java` | MODIFIED — profile threaded through wall classification + unit def creation |
| `tools/spatial_checker.py` | MODIFIED — IfcPlate→WALL mapping, slab/roof section, near-match X-ray |

### What's Next — Phase 119 Steps 2-6: Rosetta Stone Convergence

Current scores (Phase 119 Step 1 baseline):

| Check | SampleHouse | Duplex |
|-------|------------|--------|
| Wall thickness | **5/5 (100%)** | **42/57 (73%)** |
| Opening sizes | 0/7 (0%) | 0/38 (0%) |
| Furniture sizes | 1/14 (7%) | 12/61 (19%) |
| Slab/Roof sizes | 0/3 (0%) | 0/21 (0%) |
| X-ray near-match | 1/55 (1%) | 58/1085 (5%) |
| **OVERALL** | **7/84 (8%)** | **112/1262 (8%)** |

---

#### Step 0: Name Dictionary (extraction-time mapping)

**Problem**: Spatial checker matches by bounding-box buckets, but cannot do 1:1 element pairing. Reference element names (e.g. `Doors_ExtDbl_Flush:1810x2110mm`) encode semantic info the checker ignores.

**Solution**: Add `element_dictionary` table to reference DBs during IFC extraction.

```sql
CREATE TABLE element_dictionary (
    ref_ifc_class TEXT,      -- IfcDoor, IfcWindow, IfcFurnishingElement, etc.
    ref_name_pattern TEXT,   -- e.g. "Doors_ExtDbl_Flush:1810x2110mm"
    compiler_category TEXT,  -- DOOR, WINDOW, FURNITURE, WALL, SLAB, ROOF
    compiler_subtype TEXT,   -- D_EXT_DBL, W_SGL_1810x1210, BED, COUCH, etc.
    width_mm INTEGER,        -- nominal width from IFC name (1810)
    height_mm INTEGER,       -- nominal height from IFC name (2110)
    depth_mm INTEGER,        -- nominal depth/thickness (199)
    notes TEXT
);
```

Extraction script changes (`populate_sample_house_db.py`, `populate_duplex_db.py`):
- Parse element_name for dimensional tokens (e.g. `1810x2110mm`)
- Map IFC class → compiler_category using existing CATEGORY_MAP
- Map element type names → compiler_subtype (e.g. `Wall-Ext_102Bwk...` → `EXTERIOR_UK_BRICK_290`)
- Output counts per mapping for validation

Spatial checker changes:
- Load dictionary when available
- Use dictionary for targeted 1:1 matching alongside bucket-based matching
- Report "dictionary coverage" as additional metric

**Files**: `scripts/populate_sample_house_db.py`, `scripts/populate_duplex_db.py`, `tools/spatial_checker.py`

---

#### Step 2: Opening Size Alignment (SampleHouse 0/7 → 7/7, Duplex 0/38 → ~20/38)

**Root cause**: No `ad_opening_schedule` table exists. Compiler uses `BIMConstants.STANDARD_DOOR_WIDTH=0.9m`, `STANDARD_DOOR_HEIGHT=2.1m`, `STANDARD_WINDOW_WIDTH=1.2m`, `STANDARD_WINDOW_HEIGHT=1.2m` for all openings. DSL has no `type:` schedule ref on most openings.

**Reference opening data (SampleHouse)**:
| Type | Name | Width (mm) | Height (mm) | Count |
|------|------|-----------|-------------|-------|
| IfcDoor | IntSgl:810x2110mm | 880 (bbox) | 2145 | 2 |
| IfcDoor | ExtDbl:1810x2110mm | 1860 (bbox) | 2110 | 1 |
| IfcWindow | Sgl_Plain:1810x1210mm | 1860 (bbox) | 1210 | 4 |

**Reference opening data (Duplex)** — 14 doors, 24 windows:
| Type | Nominal | Bbox dims | Count |
|------|---------|-----------|-------|
| IfcDoor | 0864x2032mm | 1016x174x2108 | 6 |
| IfcDoor | 0762x2032mm | 914x174x2108 | 4 |
| IfcDoor | 1250x2010mm | 1402x467x2086 | 2 |
| IfcDoor | 0813x2420mm | 965x467x2496 | 2 |
| IfcWindow | 750x2200mm | 750x417x2200 | 4 |
| IfcWindow | 819x759mm | 819x417x759 | 12 |
| IfcWindow | 2800x2410mm | 2800x417x2410 | 4 |
| IfcWindow | 4835x2420mm | 4835x417x2420 | 2 |
| IfcWindow | Skylight 1180x1170mm | 1173x1225x178 | 2 |

**Compiler output (SampleHouse)** — all wrong:
- Doors: 750x150x2100 (2), 900x150x2100 (3) — should be 880x178x2145, 1860x199x2110
- Windows: 150x1250x1200 (1), 833x150x1200 (1), 1200x150x1000 (4), 1200x150x1200 (1) — should be 1860x353x1210

**Plan**:
1. **Create `ad_opening_schedule` table** in migration:
   ```sql
   CREATE TABLE ad_opening_schedule (
       schedule_id TEXT PRIMARY KEY,
       category TEXT NOT NULL,        -- DOOR or WINDOW
       width_mm INTEGER NOT NULL,
       height_mm INTEGER NOT NULL,
       depth_mm INTEGER DEFAULT 150,
       profile TEXT,                  -- UK_Residential, Malaysian_Residential, etc.
       description TEXT
   );
   ```
2. **Populate with reference-matched entries**:
   - `D_INT_SGL_810` → 810x2110mm (UK), `D_EXT_DBL_1810` → 1810x2110mm (UK)
   - `D_INT_864` → 864x2032mm (US/MY), `D_INT_762` → 762x2032mm (US/MY)
   - `W_SGL_1810x1210` → 1810x1210mm (UK), etc.
3. **Add profile-based default resolution in BuildingCompiler** — when DSL has no `type:` ref on a DOOR/WINDOW, resolve from `ad_opening_schedule` using building profile:
   - UK_Residential: ext door → 1810x2110, int door → 810x2110, window → 1810x1210
   - Malaysian_Residential: ext door → 864x2032, int door → 762x2032, window → varies
4. **Wire DSL `DOOR south type:D_EXT_DBL`** → look up schedule_id `D_EXT_DBL` in `ad_opening_schedule`

**Files**: `migration/migration_119B_opening_schedule.sql`, `BuildingCompiler.java`, `OpeningWriter.java`

---

#### Step 3: Slab/Roof Alignment (SampleHouse 0/3 → 2/3, Duplex 0/21 → ~5/21)

**Root cause**: Compiler produces ONE slab per storey spanning entire building footprint. Reference models have per-room slabs with different thicknesses and finishes.

**Reference slab data (SampleHouse)**:
| Type | Name | Dims (m) |
|------|------|----------|
| IfcSlab | Ground floor (suspended) | 16.87 x 8.67 x 0.47 |
| IfcSlab | Upper floor (simple) | 13.97 x 5.77 x 0.17 |
| IfcRoof | Flat roof (4-felt) | 14.84 x 7.29 x 1.73 |

**Compiler output**: Foundation slab 8.1x14.8x0.15, Roof 9.3x16.0x0.0 (flat degenerate)

**Reference slab data (Duplex)** — 21 slabs at 3 categories:
- Structural: 150mm ext slab-on-grade (2), 127mm slab-on-grade (2), 305mm wood joist (2), 457mm roof joist (1)
- Finish floor: ceramic tile 13mm (5), wood floor 19mm (5)
- Foundation: 2 slabs at 150mm, 2 at 127mm

**Plan**:
1. **Profile-based slab thickness** — `ad_slab_type` table (like `ad_wall_type`):
   - UK_Residential: foundation 470mm, floor 165mm
   - Malaysian_Residential: foundation 150mm, floor 127mm
2. **Roof volume** — compiler currently writes zero-height roof; need actual roof volume from profile:
   - UK_Residential flat: 150mm concrete + insulation = ~200mm min
   - Pitched: calculate volume from pitch angle and overhang
3. **Slab footprint** — compiler slab extends beyond building with SLAB_OVERLAP; reference slab matches exact building footprint. Consider making overlap profile-specific.

**Files**: `migration/migration_119C_slab_types.sql`, `StoreyCompiler.java` (slab compilation), `BuildingWriter.java` (roof writing)

---

#### Step 4: Furniture Tuning (SampleHouse 1/14 → 8/14, Duplex 12/61 → 30/61)

**Current match**: Only items where library component_definitions bounding box falls within 10% tolerance of reference.

**SampleHouse misses** (13/14):
| Ref item | Ref dims (m) | Compiler produces | Gap |
|----------|-------------|-------------------|-----|
| 6x Dining Chair | 0.44 x 0.43 x 1.23 | Not generated (no DINING BOM recipe child) | Need DINING_SET with 6 chairs |
| Bed (Queen) | 2.01 x 1.80 x 0.48 | ~1.9x0.9 (single) | Need queen bed in BED_SET |
| 2x Armchair | 1.43 x 1.40 x 0.92 | Not generated | Need armchair in LIVING_SET |
| Desk | 1.56 x 0.82 x 0.76 | Not generated | Need desk in BEDROOM or STUDY BOM |
| Piano | 1.37 x 0.60 x 1.17 | Not generated (no piano component) | Low priority |
| Coffee Table | 1.20 x 0.55 x 0.45 | Not generated | Need in LIVING_SET |
| Dining Table | 2.00 x 1.00 x 0.75 | Not generated | Need DINING_SET with table |

**Plan**:
1. **Add missing furniture to BOM recipes**: DINING_SET (table + 6 chairs), LIVING_SET add armchair + coffee table
2. **Check/add component_definitions** for queen bed, armchair, coffee table, dining chair, dining table
3. **Profile-specific BOM variants** — SampleHouse BED_SET → queen bed (not single)

**Files**: `migration/migration_119D_furniture_bom.sql`, BOM recipes in component_library.db

---

#### Step 5: Remaining Wall Gaps (Duplex 42/57 → 50/57)

**Missing Duplex wall types**:
| Thickness | Count | Type | Status |
|-----------|-------|------|--------|
| 54mm | 8 | Furring (drywall on stud) | `INTERIOR_FURRING_38` exists (54mm total) — need rule mapping |
| 435mm | 2 | Foundation wall | `FOUNDATION_435` exists — need rule for foundation context |
| 493mm | 2 | Party/CMU wall | `PARTY_CMU` exists — need rule for party wall context |
| 550mm | 2 | Not in DB | Need new wall type (possibly thick foundation + waterproofing) |

**Plan**: Add `ad_wall_type_rule` entries mapping these types to the correct contexts for `Malaysian_Residential` profile. Add 550mm wall type.

**Files**: `migration/migration_119_wall_alignment.sql` (append) or new `migration_119E_wall_rules.sql`

---

#### Step 6: Structural Members — Curtain Wall (SampleHouse +26 elements)

**Reference**: SampleHouse west face has 20 IfcMember (curtain wall mullions, 30mm) + 6 IfcPlate (glazing panels, 25mm). These form a curtain wall grid of vertical/horizontal mullions + glass infill.

**Plan**: This is a larger feature (curtain wall assembly type). Defer until Steps 2-5 deliver >30% overall score.

---

#### Priority Order

| Step | Effort | Expected Score Gain |
|------|--------|-------------------|
| Step 0: Name Dictionary | 1 session | Enables smarter matching (no score change directly) |
| Step 2: Opening Schedule | 1 session | +7 SampleHouse, +20 Duplex |
| Step 3: Slab/Roof | 1 session | +2 SampleHouse, +5 Duplex |
| Step 4: Furniture BOM | 1-2 sessions | +7 SampleHouse, +18 Duplex |
| Step 5: Wall rules | 0.5 session | +8 Duplex |
| Step 6: Curtain wall | 2+ sessions | +26 SampleHouse |

**Target after Steps 0-5**: SampleHouse ~23/84 (27%), Duplex ~205/1262 (16%)

After convergence on these two fossils → language proven → apply to new metadata sets.

---

## Session Summary — Phase 118B: FurnitureWorker — First BundleWorker Adapter

### What Was Done

**Wrapped existing FurniturePlacer pipeline behind BundleWorker interface — zero behavior change:**

1. **`BundleWorker.java` modified** — Added `OpeningInfo` record and `openings` field to `RoomEnvelope`:
   - `record OpeningInfo(String type, String wall, double width)` — decoupled from OpeningSpec
   - `List<OpeningInfo> openings` field added between `maxZ` and `reservedZones`
   - No existing code constructs RoomEnvelope yet, so no breakage

2. **`FurnitureWorker.java` created** — First concrete `BundleWorker` adapter:
   - `implements BundleWorker`, constructor takes `(String bomId, ComponentLibrary library)`
   - `themeId()` returns dynamic bomId (BED_SET, WORKSTATION_SET, LIVING_SET, etc.)
   - `anchorFace()` returns `"BACK"` per framework convention
   - `execute()` converts `BundleWorker.OpeningInfo` → `FurnitureBOMResolver.OpeningInfo`,
     delegates to `FurniturePlacer.placeUniversalFurniture()`,
     converts `FurnitureInstance` → `PlacedElement`
   - Identity chain preserved: `fi.type().name()` → `PlacedElement.role()` → downstream `.toLowerCase()`

3. **`StoreyCompiler.java` rewired** — BOM-driven furniture path uses FurnitureWorker:
   - Added `Map<String, FurnitureWorker> workerCache` (keyed by bomId, avoids duplicate BOM loads)
   - BOM branch builds `RoomEnvelope` + `PlacementContext`, calls `worker.execute()`
   - New `addPlacedElementsToCtx()` converts `PlacedElement` → `FixtureSpec`
   - Fallback paths (CANTEEN, SEATING, WORKSTATION) remain unchanged

### Golden Digests (Phase 118B — Unchanged)

| Building | Elements | SHA256 Digest |
|----------|----------|--------------|
| CONDO-MID | 10,516 | `18b3062356ecef701d6c80bb979f9f02668e69fa202976925b297b88405b44c6` |
| TB-LKTN-DUPLEX | 559 | `f0ff9b03dfcb3006de4ea8dbb1169b383da06b4384f8e8474e3fefd06679a180` |

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| CondoMidEndToEndTest | PASS (10516 elements, digest unchanged) |
| SchoolEndToEndTest | PASS |
| TBLKTNEndToEndTest | PASS |
| TBLKTN2SEndToEndTest | PASS |
| TBLKTNCompactEndToEndTest | PASS |
| TBLKTNDuplexEndToEndTest | PASS (559 elements, digest unchanged) |
| Sanity (condo_mid.db) | 23 PASS, 4 FAIL, 6 WARN (identical to baseline) |

### Files

| File | Action |
|------|--------|
| `src/main/java/com/bim/compiler/contract/BundleWorker.java` | MODIFIED — added OpeningInfo record + openings field to RoomEnvelope |
| `src/main/java/com/bim/compiler/library/FurnitureWorker.java` | NEW — BundleWorker adapter wrapping FurniturePlacer |
| `src/main/java/com/bim/compiler/dsl/StoreyCompiler.java` | MODIFIED — BOM branch uses FurnitureWorker + addPlacedElementsToCtx |

### What's Next (Progressive Path)

1. **Phase 118C**: WorkerRegistry + SlotDispatcher loop in StoreyCompiler
2. **Phase 118D**: Adapt remaining 7 placers to BundleWorker
3. **Phase 118E**: Spatial reservation (zero-clash guarantee)
4. **Fire compartment walls** — address 1241m² violation
5. **Furniture for interior rooms** — 256 rooms >6m² have none

---

## Session Summary — Phase 118: BundleWorker Framework + SpatialDigest

### What Was Done

**OSGi-inspired construction worker pattern + deterministic spatial testing:**

1. **`docs/BUNDLE_WORKER_FRAMEWORK.md`** — Framework vision document:
   - Construction site metaphor (workers arrive, read blueprint, do job, leave)
   - BundleWorker interface design (themeId, anchorFace, execute, reservedEnvelope)
   - RoomEnvelope, PlacementContext, PlacedElement records
   - Current vs target dispatch pattern (hardcoded → slot-dispatched)
   - Face-aligned placement contract
   - SpatialDigest concept (two birds, one stone)
   - Progressive migration path (118A→118E phases)
   - OSGi lineage mapping table

2. **`BundleWorker.java`** — Interface in `contract/` package:
   - `themeId()` — matching ad_room_slot.assembly_id
   - `anchorFace()` — BACK/FRONT/LEFT/RIGHT/TOP/BOTTOM
   - `execute(RoomEnvelope, PlacementContext)` → `List<PlacedElement>`
   - Nested records: RoomEnvelope, PlacementContext, PlacedElement

3. **`SpatialDigest.java`** — Deterministic verification:
   - SHA256 of all element bounding boxes (sorted, 1mm precision)
   - `compute(dbPath)` → 64-char hex digest
   - `computeWithReport(dbPath)` → digest + element count + class breakdown
   - `matches(dbA, dbB)` → boolean spatial identity check
   - Two birds: sizing verification + regression testing

4. **E2E tests wired** — SpatialDigest added to CondoMidEndToEndTest and TBLKTNDuplexEndToEndTest

### Golden Digests (Phase 118 Baseline)

| Building | Elements | SHA256 Digest |
|----------|----------|--------------|
| CONDO-MID | 10,516 | `18b3062356ecef701d6c80bb979f9f02668e69fa202976925b297b88405b44c6` |
| TB-LKTN-DUPLEX | 559 | `f0ff9b03dfcb3006de4ea8dbb1169b383da06b4384f8e8474e3fefd06679a180` |

Verified deterministic: two consecutive runs produce identical digest.

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| CondoMidEndToEndTest | PASS (10516 elements, digest stable) |
| SchoolEndToEndTest | PASS |
| TBLKTNEndToEndTest | PASS |
| TBLKTN2SEndToEndTest | PASS |
| TBLKTNCompactEndToEndTest | PASS |
| TBLKTNDuplexEndToEndTest | PASS (559 elements, digest stable) |
| Sanity (condo_mid.db) | 23 PASS, 4 FAIL, 6 WARN (identical to baseline) |

### Files

| File | Action |
|------|--------|
| `docs/BUNDLE_WORKER_FRAMEWORK.md` | NEW — OSGi worker vision, dispatch protocol, migration path |
| `docs/ARCHITECTURE.md` | MODIFIED — added Contract Layer + Verification Layer sections |
| `src/main/java/com/bim/compiler/contract/BundleWorker.java` | NEW — worker interface + 3 nested records |
| `src/main/java/com/bim/compiler/validation/SpatialDigest.java` | NEW — SHA256 deterministic fingerprint |
| `src/main/java/com/bim/compiler/dsl/CondoMidEndToEndTest.java` | MODIFIED — added SpatialDigest |
| `src/main/java/com/bim/compiler/dsl/TBLKTNDuplexEndToEndTest.java` | MODIFIED — added SpatialDigest |

### What's Next (Progressive Path)

1. **Phase 118B**: Adapt FurnitureBOMResolver → FurnitureWorker implementing BundleWorker
2. **Phase 118C**: WorkerRegistry + SlotDispatcher loop in StoreyCompiler
3. **Phase 118D**: Adapt remaining 7 placers to BundleWorker
4. **Phase 118E**: Spatial reservation (zero-clash guarantee)
5. **Add SpatialDigest assertions**: Lock golden digests as regression guards

---

## Session Summary — Phase 117: CatalogContract Enforcement

### What Was Done

**Java contract + validation enforcing DSL-as-Catalog-Selector principle:**

1. **`CatalogContract.java`** — interface in `contract/` package:
   - Defines the enforcement contract: every DSL reference must resolve to an existing catalog entry
   - `CatalogViolation` record: referenceType, referenceValue, table, message
   - `CatalogResult` record: violations list, resolved count, total count, coverage %
   - Javadoc documents OSGI MANIFEST analogy and separation of concerns

2. **`CatalogValidator.java`** — implementation in `validation/building/`:
   - Checks 4 reference types against component_library.db:
     - `floor_bom:` → ad_bom.bom_id
     - Room type keywords → ad_space_type.space_type_id + ad_space_type_alias
     - Door schedule entries → ad_opening_family (type='DOOR')
     - Window schedule entries → ad_opening_family (type='WINDOW')
   - Graceful degradation: if DB missing or tables don't exist, returns clean result
   - `toValidationResult()` bridges to existing `BuildingValidationResult` for chain integration

3. **ValidatorFactory updated** — CatalogValidator wired into validation chain:
   - Runs as advisory (non-blocking) BuildingValidator
   - Pre-computed from BuildingDefinition, wrapped for chain integration
   - Reports warnings for unresolved references; silent when clean

4. **ARCHITECTURE.md §1.3 strengthened** — catalog selector principle:
   - Added "Who Edits" column (Layman / Hobbyist expert / Developer)
   - Added OSGI MANIFEST analogy table
   - Added enforcement section referencing CatalogContract

### Verification

| Check | Result |
|-------|--------|
| `mvn compile -q` | PASS |
| CondoMidEndToEndTest | PASS (10516 elements, unchanged) |
| SchoolEndToEndTest | PASS |
| TBLKTNEndToEndTest | PASS |
| TBLKTN2SEndToEndTest | PASS |
| TBLKTNCompactEndToEndTest | PASS |
| TBLKTNDuplexEndToEndTest | PASS |
| Sanity (condo_mid.db) | 23 PASS, 4 FAIL, 6 WARN (identical to baseline) |
| CatalogValidator (condo) | Clean — all DSL references resolve to catalog |

### Files

| File | Action |
|------|--------|
| `src/main/java/com/bim/compiler/contract/CatalogContract.java` | NEW — interface + CatalogViolation + CatalogResult records |
| `src/main/java/com/bim/compiler/validation/building/CatalogValidator.java` | NEW — checks DSL refs against component_library.db |
| `src/main/java/com/bim/compiler/validation/building/ValidatorFactory.java` | MODIFIED — wired CatalogValidator into chain |
| `docs/ARCHITECTURE.md` | MODIFIED — §1.3 strengthened with OSGI analogy + enforcement |

### What's Next

1. **Wire `ad_room_slot` into FurnitureBOMResolver** — unified slot dispatch (PREFAB_ARCHITECTURE.md Room Slot Protocol)
2. **Duplex-exact BOM assembly set** — metadata-only reproduction of Stacked_Duplex.db
3. **Fire compartment walls** — address 1241m² violation
4. **Furniture for interior rooms** — 256 rooms >6m² have none
5. **Extend CatalogValidator** — add unit_type → ad_unit_type, wall_type → ad_wall_type checks

---

## Session Summary — Phase 116: Grammar Extraction from IFC Rosetta Stone

### What Was Done

**Reference corpus organization + metadata grammar extraction from Duplex IFC:**

1. **Reference corpus reorganized** — `reference/` directory:
   - `reference/residential/` — 4 IFC files (Duplex Arch, Duplex MEP, SampleHouse, WallElementedCase)
   - `reference/infrastructure/` — 9 PCERT IFC 4.3.2.0 certification samples
   - `reference/README.md` — corpus documentation with extraction process
   - Removed stale `prefabs/*.bim` files, moved `archive/IFC_source_files/` to `reference/`

2. **Migration 116** — `migration/migration_116_grammar_extraction.sql`:
   - `ad_wall_type` table — 8 wall types extracted from Duplex IFC geometry truth
   - `ad_wall_type_rule` table — 8 adjacency-based resolution rules (context × space → type)
   - 10 Duplex-exact opening families in `ad_opening_family` with `ad_product_dim` entries
   - BOM recipe fixes: LIVING_SET (deactivated TV/LOUNGE_CHAIR, added SOFA_B/SIDE_TABLE), BED_SET/BED_SET_MASTER (added TALL_CABINET_A/B), new BATHROOM_VANITY_SET
   - Room slot update: BATHROOM BASIN → BATHROOM_VANITY_SET

3. **Grammar extraction document** — `reference/GRAMMAR_EXTRACTION.md`:
   - Dewey Decimal classification (000-900) for building element taxonomy
   - Complete Duplex IFC inventory mapped to grammar tables (7/13 COMPLETE, 5/13 PARTIAL, 1/13 NONE = 73%)
   - MANIFEST face contracts mapped to Duplex rooms
   - 5 priority gaps identified (ceiling, floor finish, beam type, conduit, telephone)

4. **WallTypeResolver.java** — lazy singleton resolver for `ad_wall_type` + `ad_wall_type_rule`:
   - Loads 8 wall types and 8 resolution rules on first access
   - Resolves wall thickness from context (INTERIOR/EXTERIOR/PARTY/FOUNDATION) + adjacent room types
   - Wired into StoreyCompiler (interior walls) and MultiUnitCompiler (party walls, classified walls)
   - Graceful degradation: falls back to WallType enum defaults if tables missing
   - Bathroom walls now get 184mm (plumbing), kitchen/stair walls get 152mm (furring)

5. **Spatial verification tools** — `tools/spatial_checker.py` + `tools/grammar_checker.py`:
   - `spatial_checker.py`: X-ray comparison of output DB vs reference DB spatial signatures
     - Dimension fingerprinting: (category, L_mm, W_mm, H_mm) buckets, ignores names
     - Self-comparison = 100%, cross-building = shows exact overlap %
   - `grammar_checker.py`: Grammar coverage audit (reference IFC vs ad_* tables)
     - Walls: 55/57 EXACT (96%), Openings: 38/38 EXACT (100%), Furniture: 32/61 (52%)
   - Linguistic model: Corpus → Grammar → Compiler (decode → encode → construct)

### Verification

| Check | Result |
|-------|--------|
| Migration applied | 8 wall types + 8 rules + 10 openings + BOM fixes |
| `mvn compile -q` | PASS |
| CondoMidEndToEndTest | PASS (10516 elements, +2 from resolver) |
| SchoolEndToEndTest | PASS |
| TBLKTNEndToEndTest | PASS |
| TBLKTN2SEndToEndTest | PASS |
| TBLKTNCompactEndToEndTest | PASS |
| TBLKTNDuplexEndToEndTest | PASS |
| Sanity (condo_mid.db) | 23 PASS, 4 FAIL, 6 WARN (identical to baseline) |
| WallTypeResolver | Loaded 8 types, 8 rules — bathroom→184mm, kitchen→152mm |
| Grammar checker | Walls 96%, Openings 100%, Furniture 52% |
| Spatial X-ray (self) | 100% signature match |

### Files

| File | Action |
|------|--------|
| `migration/migration_116_grammar_extraction.sql` | NEW — ad_wall_type, ad_wall_type_rule, opening families, BOM fixes |
| `reference/README.md` | NEW — corpus documentation |
| `reference/GRAMMAR_EXTRACTION.md` | NEW — linguistic mapping, Dewey classification, completeness matrix |
| `reference/residential/` | NEW — 4 IFC reference files |
| `reference/infrastructure/` | NEW — 9 IFC reference files |
| `prefabs/` | DELETED — stale .bim files replaced by reference corpus |
| `src/main/java/.../library/WallTypeResolver.java` | NEW — lazy singleton, 8 types + 8 rules |
| `src/main/java/.../dsl/StoreyCompiler.java` | MODIFIED — interior walls use resolver |
| `src/main/java/.../dsl/MultiUnitCompiler.java` | MODIFIED — party/classified walls use resolver |
| `tools/spatial_checker.py` | NEW — X-ray spatial fidelity comparison |
| `tools/grammar_checker.py` | NEW — grammar coverage audit |

### What's Next

1. **Wire `ad_wall_type` into compiler** — StoreyCompiler/MultiUnitCompiler currently hardcode wall thickness; need WallTypeResolver
2. **Wire `ad_room_slot` into FurnitureBOMResolver** — unified slot dispatch (PREFAB_ARCHITECTURE.md Room Slot Protocol)
3. **Extract SampleHouse.ifc** → `database/SampleHouse.db` — validate grammar generativity on single-storey
4. **Fill grammar gaps** — ad_ceiling_type (13 elements), ad_floor_finish (wood vs tile), ad_beam_type
5. **Fire compartment walls** — address 1241m² violation
6. **Furniture for interior rooms** — 256 rooms >6m² have none

---

## Session Summary — Phase 115B: Assembly MANIFEST Tables + ManifestResolver Wiring

### What Was Done

**Database tables + Java resolver for assembly MANIFEST contracts:**

1. **Migration script** — `migration/migration_115B_assembly_manifest.sql`:
   - 3 new tables: `ad_assembly_manifest`, `ad_assembly_connector`, `ad_room_slot`
   - 35 manifest face entries for 11 BOMs (WORKSTATION_SET through SPRINKLER_PENDANT_ASSEMBLY)
   - 10 MEP connector entries for 5 BOMs (pipe/duct diameter + target system)
   - 21 room slot entries for 8 room types (priority-ordered, slot_face anchored)

2. **ManifestResolver.java** — lazy singleton, direct JDBC to component_library.db:
   - Loads all `ad_assembly_manifest` rows (version='1.0.0') into `Map<String, List<ManifestEntry>>`
   - Key method: `getClearance(assemblyId, face, defaultValue)` — CLEARANCE or WALL_BACK types
   - Graceful degradation: if table missing, catches SQLException, returns defaults

3. **FixturePlacer wired to ManifestResolver** — 4 constants → accessor methods:
   - `TOILET_SIDE_CLEARANCE` → `getToiletSideClearance()` reads LEFT face (default 0.38)
   - `TOILET_FRONT_CLEARANCE` → `getToiletFrontClearance()` reads FRONT face (default 0.533)
   - `SINK_FRONT_CLEARANCE` → `getSinkFrontClearance()` reads FRONT face (default 0.533)
   - `WALL_OFFSET` → `getWallOffset()` reads BACK face (default 0.05)
   - All 16+ usage sites updated; original constants kept as `DEFAULT_*` fallbacks
   - **NOT changed:** FurnitureBOMResolver.WALL_OFFSET (0.5 = placement inset, not code clearance)

### Verification

| Check | Result |
|-------|--------|
| Migration applied | 35 manifest + 10 connector + 21 room_slot = 66 rows |
| `mvn compile -q` | PASS |
| CondoMidEndToEndTest | PASS (10514 elements) |
| SchoolEndToEndTest | PASS |
| TBLKTNEndToEndTest | PASS |
| TBLKTN2SEndToEndTest | PASS |
| TBLKTNCompactEndToEndTest | PASS |
| TBLKTNDuplexEndToEndTest | PASS |
| Sanity (condo_mid.db) | 23 PASS, 4 FAIL, 6 WARN (identical to baseline) |
| Baseline comparison | Stashed changes, re-ran — same results → **zero behavior change** |

### Files

| File | Action |
|------|--------|
| `migration/migration_115B_assembly_manifest.sql` | NEW — 3 tables + 66 data rows |
| `src/main/java/com/bim/compiler/library/ManifestResolver.java` | NEW — lazy singleton resolver |
| `src/main/java/com/bim/compiler/library/FixturePlacer.java` | MODIFIED — 4 constants → accessor methods |

### What's Next

1. **BOM variant for 12m tower units** — units share Y-sections with core
2. **Fire compartment walls** — address 1241m² violation
3. **Furniture for interior rooms** — 256 rooms >6m² have none
4. **Wire ad_room_slot** into FurnitureBOMResolver as unified slot dispatch
5. **Remaining unit type room layouts** — 6 types still need ad_unit_type_room entries

---

## Session Summary — Phase 115A: MANIFEST Contracts & Fixture Lego Pieces

### What Was Done

**Documentation-only phase** — extended `docs/PREFAB_ARCHITECTURE.md` with bottom-up fixture contracts.

1. **Extended hierarchy to 6 levels** (was 4): Level -1 FIXTURE ARRANGEMENT through Level 4 BUILDING
2. **MANIFEST contract specification**: 6-face interface model, interface types, MEP connector types
3. **6 concrete fixture arrangements** with full MANIFEST contracts
4. **Room Slot Protocol** — unified resolution replacing 3-way split
5. **3 new database table schemas** designed (implemented in 115B)

### Files Modified

| File | Action |
|------|--------|
| `docs/PREFAB_ARCHITECTURE.md` | EXTENDED — 6-level hierarchy, MANIFEST contracts, fixture arrangements, room slots |

---

## Session Summary — Phase 114: Duplex Extraction + Record Refactoring

### What Was Done

1. **MEP component extraction** — 11 new unique components from Duplex IFC
2. **Kitchen + Bathroom BOM recipes** — KITCHEN_CABINET_SET (4 children), DUPLEX_BATHROOM_SET (4 children)
3. **Stacked_Duplex.db populated** — 1,085 elements, queryable reference
4. **BuildingSpecs.java refactored** — all 26 record types extracted from BuildingCompiler (~700 lines)
5. **Import cleanup** — 48 files updated with `import BuildingSpecs.*`

### Files

| File | Action |
|------|--------|
| `BuildingSpecs.java` | NEW — 26 record types (~700 lines) |
| `BuildingCompiler.java` | REDUCED by ~700 lines |
| `migration/migration_114_duplex_bom.sql` | NEW — kitchen/bathroom BOM recipes |
| 48 files | Import updates for BuildingSpecs |

---

## Session Summary — Phase 113: Baseline Consolidation

### What Was Done

1. **Water tank migration** — 3 FRP tanks migrated to component_library.db
2. **IFC naming convention** — `docs/IFC_NAMING_CONVENTION.md`
3. **Architectural commitment** — `docs/DSL_AS_CATALOG_SELECTOR.md`
4. **Source consolidation** — `docs/SOURCE_CONSOLIDATION.md`
5. **Cleanup** — Removed dead files

---

## Consolidated Phase History (90A–112)

| Phase | Date | Key Achievement |
|-------|------|-----------------|
| 112 | 2026-02-10 | Per-storey slab boundary, floor plate envelope, UnitInteriorResolver activated |
| 111 | 2026-02-10 | TB-LKTN-DUPLEX full compilation (2 storeys, 555 elements) |
| 110 | 2026-02-10 | Consolidation: 2 new E2E tests, orphaned check cleanup, Git LFS |
| 109B | 2026-02-10 | House design templates + variants POC (LANDED_1S, compact, duplex) |
| 108C/D | 2026-02-10 | Metadata gap closure (ad_product_dim, ad_system_type) |
| 108B | 2026-02-10 | Furniture type routing + metadata (FurnitureTypeResolver) |
| 108A | 2026-02-09 | Attempted + reverted (FurniturePlacer approach abandoned) |
| 104–107 | 2026-02-09 | Codebase simplification + completeness layers |
| 103 | 2026-02-09 | BOM prefab fixes + BuildingWriter split into sub-writers |
| 102A+B | 2026-02-09 | Infrastructure fixes + AD-driven compilation |
| 100 | 2026-02-09 | Standards resolution engine + lessons learned |
| 99 | 2026-02-09 | Structural integrity + toilet + glass facade |
| 98 | 2026-02-09 | Floor plate rethink + stall dividers |
| 97 | 2026-02-09 | Window fix + LOD400 stairs + sink fix |
| 96B | 2026-02-09 | Toilet north end + BOM-driven fixture placement |
| 96 | 2026-02-09 | Toilet relocation + roof fix |
| 95A/B | 2026-02-09 | FloorPlateBOMResolver integration |
| 94A–C | 2026-02-09 | Toilet blocks + condo corridor rework |
| 93/93B | 2026-02-08 | Furniture assembly BOM + bus factor refactoring |
| 92B/C | 2026-02-07 | LOD400 ceiling fan + full-floor MEP + unified BOM bounding boxes |
| 91 | 2026-02-07 | Full MEP set + universal furniture + FP pipe cleanup |
| 90A | 2026-02-07 | Ground floor access + MEP + NLP fixes (first "recognizably a house") |

*Detailed logs: `docs/archive/PROGRESS_ARCHIVE_phases90-112.md`, `docs/archive/PROGRESS_ARCHIVE_phases62-89.md`, `docs/archive/PROGRESS_ARCHIVE_2026-02-04.md`.*

---

## Foundation Compliance Check

| Principle | Status |
|-----------|--------|
| EXTRACT, DON'T IMAGINE | ✓ All constants traced to AD tables or code standards |
| 5-Stage DAG | ✓ Parse → Resolve → Compile → Place → Write |
| Witness System | ✓ 18/22 applicable witnesses proven |
| Configuration over Code | ✓ ManifestResolver enables runtime clearance customization |
| Deterministic | ✓ Same input → same output |

---

## Test Commands

```bash
# CONDO-MID (high-rise)
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.CondoMidEndToEndTest" -q

# School (2-storey)
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.SchoolEndToEndTest" -q

# Landed houses
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTN2SEndToEndTest" -q
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNCompactEndToEndTest" -q
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNDuplexEndToEndTest" -q

# Sanity checker
mvn -f tools/sanity-checker/pom.xml exec:java -Dexec.mainClass="com.bim.tools.sanity.HouseSanityChecker" -Dexec.args="output/condo_mid.db" -q
```

---

## Known Issues

### 1. Compartment FAIL on typical floors
1241m² > 1000m² limit — needs fire compartment walls. Real building code issue, not a code bug.

### 2. Stacked-Duplex exhaust fan geometry
Small bathroom can't fit exhaust fan. 2.9% outlier rate (acceptable).

### 3. hall_b/master_b overlap 0.5 m²
Integer-grid solver rounds 3.7m→4m. Adjacent rooms can have slight overlaps when mapped to physical coordinates. Non-fatal.

---

## Compiled Outputs

| DSL | Output | Status |
|-----|--------|--------|
| `CONDO-MID.bim` | `condo_mid.db` | ✓ PASS |
| `Sekolah-Kebangsaan.bim` | `sekolah_kebangsaan.db` | ✓ PASS |
| `TB-LKTN` (tests) | `tb_lktn.db` | ✓ PASS |
| `TB-LKTN-2S.bim` | `tb_lktn_2s.db` | ✓ PASS |
| `TB-LKTN-COMPACT.bim` | — | ✓ PASS |
| `TB-LKTN-DUPLEX.bim` | `tb_lktn_duplex.db` | ✓ PASS |
