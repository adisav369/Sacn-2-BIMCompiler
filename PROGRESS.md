# PROGRESS — Current Development State

**Last updated:** 2026-02-04
**Current phase:** Phase 62C Complete
**Commit:** ba5b8ae [PHASE 62C] Move all BOM formulas to database metadata

---

## Session Summary (2026-02-04) - Phase 62 BOM Integration

### Completed

| Task | Status |
|------|--------|
| `ad_bom_rule` table (24 rules) | DONE |
| `ad_room_sizing` table (15 rules) | DONE |
| `BOMRuleAD.java` + `BOMResolver.java` | DONE |
| `RoomSizingResolver.java` | DONE |
| Either/or input mode (BY_AREA / BY_DIMENSIONS) | DONE |
| Layout fitting with feedback | DONE |
| **BOMResolver in BuildingCompiler** | DONE |
| **FurniturePlacer with target qty** | DONE |
| **Outlier logging for BOM capacity** | DONE |
| **All formulas in DB metadata** | DONE |
| Regression tests | PASS (TB-LKTN 4/4, School 5/5) |

### Phase 62C: Formula Metadata in DB

Schema additions to `ad_bom_rule`:

| Column | Type | Purpose |
|--------|------|---------|
| `calc_occupancy_density` | REAL | m²/person for PER_OCCUPANT |
| `calc_cfm_density` | REAL | CFM/m² for PER_CFM |
| `calc_formula` | TEXT | Human-readable formula for audit |

**No hardcoded values in Java** - all formula parameters read from DB.

### Audit Output Example

```
=== BOM: CANTEEN "kantin" (84.0m²) ===
  7× pendent         [VARIABLE] ceil(area_m2 / 12.1) = 7 NFPA_13 8.6.2.1
  9× Canteen Table   [VARIABLE] ceil((area_m2 / 2.5 m²/person) / 4 seats) = 9 IBC 1004.5
```

### Outlier Examples (after calibration)

```
[OUTLIER] canteen_table | CANTEEN "kantin" (12.0m x 12.0m)
  → BOM wants 15 tables but only 9 fit (grid 3x3)

[OUTLIER] office_desk | OFFICE "pejabat" (4.0m x 7.0m)
  → BOM wants 2 workstations but only 1 fit
```

### BOM Types

| Type | Description | Example |
|------|-------------|---------|
| MANDATORY | Always required | Toilet in BATHROOM |
| OPTIONAL | User-specified in DSL | Bidet (future) |
| VARIABLE | Calculated from room properties | Sprinklers, Lights, Tables |

### Calculation Rules

| Rule | Formula | Code Reference |
|------|---------|----------------|
| PER_AREA | `ceil(area / base)` | NFPA 13 (sprinklers) |
| PER_LUX | `ceil(area × lux / lumens)` | MS 1525 (lighting) |
| PER_CFM | `ceil(cfm / base)` | ASHRAE 62.1 (diffusers) |
| PER_OCCUPANT | `ceil(occupancy / base)` | Furniture density |
| FIXED | `base` | IPC 403.1 (fixtures) |

### Sample Resolution

```
ROOM "kantin" [CANTEEN] 84.0m²
    7× pendent         ceil(84.0m² / 12.1) = 7 (NFPA_13 8.6.2.1)
    2× Supply Diffuser ceil(CFM / 600) = 2 (ASHRAE_62_1)
    6× Downlight       ceil(84.0m² × 200 lux / 3000 lm) = 6 (MS_1525)
   14× Canteen Table   ceil(occupancy / 4) = 14
```

### Files Changed

| File | Change |
|------|--------|
| `create_ad_bom_rule.py` | NEW - Schema script for ad_bom_rule table |
| `BOMRuleAD.java` | NEW - AD queries for BOM rules |
| `BOMResolver.java` | NEW - Resolution stage in DAG compiler |

### Room Sizing Resolution (Phase 61)

**Either/Or Input Pattern** (like Libero Mfg BOM):

```dsl
# Option A: BY_AREA - user specifies area
CLASSROOM "class_1" area:56m²
  → RESOLVED: 6.83m × 8.20m (aspect 1.2:1)

# Option B: BY_DIMENSIONS - user specifies size
CLASSROOM "class_2" size:7m×8m
  → RESOLVED: area=56m², valid

# Invalid dimensions get warnings:
CLASSROOM "bad" size:3m×15m
  → WARNING: Width 3.0m < code min 6.0m (UBBL)
  → WARNING: Aspect 5.0:1 > max 1.8:1
```

**Layout Fitting with Feedback**:
```
Requested: 4 classrooms @ 56m² in 15m × 12m
Resolved: 2 (only 2 fit)
Feedback: "Suggest reduce to 38.3m²/room for all 4"
```

### Architecture

```
Parse → Resolve → [Room Sizing] → [BOM Resolve] → Compile → Place → Write
                       ↓                ↓
                 ad_room_sizing    ad_bom_rule
                       ↓                ↓
                 ResolvedRoom      RoomBOM {
                   width, depth      quantities
                   warnings          audit trail
                   feedback       }
```

---

## Previous Session (2026-02-04) - Phase 59

### Completed

| Task | Status |
|------|--------|
| `FurniturePlacer.java` created | DONE |
| CANTEEN room furniture (Canteen Table) | DONE |
| OFFICE room furniture (Desk + Chair) | DONE |
| LOBBY room furniture (Waiting Room Seat) | DONE |
| `writeFixture` updated for LOD400 geometry | DONE |
| `ComponentLibrary.findByName()` added | DONE |
| Regression tests | PASS (TB-LKTN 4/4, School 5/5) |

### Files Changed

| File | Change |
|------|--------|
| `FurniturePlacer.java` | NEW - Places LOBBY/CANTEEN/OFFICE furniture from library |
| `BuildingWriter.java` | Updated writeFixture for LOD400 library geometry |
| `BuildingCompiler.java` | Added furniture placement for LOBBY/CANTEEN/OFFICE rooms |
| `ComponentLibrary.java` | Added findByName() for partial name matching |

### Results

```
Before: Fixtures: 0 library (box geometry)
After:  Fixtures: 11 library (LOD400 geometry)

School furniture placement:
- 9 Canteen Tables in CANTEEN room
- 1 Desk + 1 Chair in OFFICE (bilik_guru)
- All using LOD400 library geometry with transform
```

### Library Usage Summary (School)

```
=== LOD400 Library Usage Summary ===
Doors:    46 library, 0 parametric
Windows:  58 library, 8 parametric
Stairs:   1 library, 0 parametric
Fixtures: 11 library, 0 parametric   <-- NEW (furniture)
Lights:   85 library, 0 parametric
Status: CONNECTED (using LOD400 geometry)
```

---

## Previous Session (2026-02-04) - Phase 58

### Completed

| Task | Status |
|------|--------|
| `StairLibraryMapper.java` created | DONE |
| Stair matching by width/rise/run | DONE |
| Scaling support (0.7x-1.85x rise) | DONE |
| Integration in BuildingWriter | DONE |
| `writeLibraryStairFlight()` method | DONE |

### Files Changed

| File | Change |
|------|--------|
| `StairLibraryMapper.java` | NEW - Maps stairs to LOD400 library StairFlight |
| `BuildingWriter.java` | Added stairLibraryMapper, writeLibraryStairFlight() |

---

## Previous Session (2026-02-04) - Phase 57C

### Completed

| Task | Status |
|------|--------|
| Window library matching with scaling | DONE |
| `MappingResult` extended with scaleX/Y/Z | DONE |
| `transformAndWriteGeometryScaled()` method | DONE |
| `writeWindowFromLibrary()` with transform | DONE |
| Library usage: 0→58 windows (88%) | DONE |

### Files Changed

| File | Change |
|------|--------|
| `DoorWindowLibraryMapper.java` | Increased tolerance, added scaling support, new scaled transform method |
| `BuildingWriter.java` | Added libraryWindowCount, writeWindowFromLibrary with transform |

### Results

```
Before: Windows: 0 library, 66 parametric
After:  Windows: 58 library, 8 parametric

School building now uses 88% LOD400 library windows
- Uses A_Window_Glass_2100x2500_Aluminium_V1 scaled to match sizes
- Scale factors: 0.72x for 1800mm, 0.48x for 1200mm
```

### Library Assets Discovered for BOM Units

| Asset | Instances | Dimensions | Use Case |
|-------|-----------|------------|----------|
| Waiting_Room_Seat_4St_1Tbl | 108 | 3000x600mm | Lobby seating |
| Canteen Table | 15 | 1500x1250mm | Canteen/cafeteria |
| Supply Diffuser 600x600 | 134 | - | HVAC |
| Exhaust Diffuser 600x600 | 32 | - | HVAC |
| StairFlight | 32 | ~1400x3000mm | Stairs |

---

## Previous Session (2026-02-04) - Phase 57B

### Completed

| Task | Status |
|------|--------|
| ADSession ThreadLocal holder in BuildingCompiler | DONE |
| compile() wrapped with try-with-resources ADSession | DONE |
| compileFromManifest() wrapped with ADSession | DONE |
| SpaceTypeRegistry updated to use session when available | DONE |
| FireProtectionResolver uses session FP + MEP facades | DONE |
| VerticalCirculationValidator uses session delegate | DONE |
| ADSession exposes VC delegate via getDelegate() | DONE |
| Regression tests | PASS (TB-LKTN 4/4, School 5/5) |

### Files Changed

| File | Change |
|------|--------|
| `BuildingCompiler.java` | Added ADSession integration via ThreadLocal, wrapped compile methods |
| `SpaceTypeRegistry.java` | Updated to use ADSession.spaceType() facade when session active |
| `FireProtectionResolver.java` | Uses session.fireProtection() and session.mep() when available |
| `VerticalCirculationValidator.java` | Uses session.verticalCirculation().getDelegate() when available |
| `ADSession.java` | Added getDelegate() to VerticalCirculationADFacade |

### Architecture

```
compile(def)
  └─ ADSession.open()          // Single connection for entire compilation
      └─ currentSession.set()  // ThreadLocal for nested access
          └─ compileWithSession()
              └─ compileStorey()
                  └─ SpaceTypeRegistry.get()
                      └─ BuildingCompiler.getSession()  // Access via ThreadLocal
                          └─ session.spaceType().resolveAlias()
                          └─ session.spaceType().isValid()
          └─ currentSession.remove()
      └─ session.close()       // Connection released
```

### Benefits

1. **Single connection per compile** - No connection churn
2. **Cross-AD cache sharing** - Queries cached in session
3. **Clean fallback** - Works without session (individual AD calls)
4. **No signature changes** - ThreadLocal allows nested access

---

## Previous Session (2026-02-04) - Phase 57

### Completed

| Task | Status |
|------|--------|
| `ad_fp_trigger` table (12 entries) | DONE |
| `ad_fire_riser_requirement` table (9 entries) | DONE |
| `ad_fire_compartment` table (6 entries) | DONE |
| `FireProtectionAD.java` class | DONE |
| `create_ad_fire_protection.py` schema script | DONE |
| Regression tests | PASS |

### Files Changed

| File | Change |
|------|--------|
| `FireProtectionAD.java` | NEW - AD query class for FP triggers, risers, compartments |
| `create_ad_fire_protection.py` | NEW - Schema script for FP AD tables |
| `FireProtectionIntegrationTest.java` | NEW - Maths proofs (9/9 PASS) |
| `FireProtectionCheck.java` | NEW - SanityChecker with MATHS validation |
| `HouseSanityChecker.java` | Added FireProtectionCheck to check list |
| `IFireProtected.java` | NEW - Contract interface for FP enforcement |
| `FireProtectionResolver.java` | NEW - Auto-fulfillment from AD triggers |
| `ComplianceOptions.java` | NEW - DSL config for `compliance:AUTO_FP` |
| `ComplianceEnforcementTest.java` | NEW - Full flow test (4/4 PASS) |

### AD Tables Created

```sql
-- ad_fp_trigger: when is fire protection required
trigger_id, trigger_type, element_type, min_storeys, min_height_m,
min_floor_area_m2, occupancy_group, code_id, clause, jurisdiction

-- ad_fire_riser_requirement: riser sizing by building parameters
req_id, riser_type, hazard_class, min/max_storeys, min/max_height_m,
pipe_diameter_mm, branch_diameter_mm, min_flow_lpm, min_pressure_bar,
pump_required, tank_capacity_l, valve_type, code_id, jurisdiction

-- ad_fire_compartment: compartment area limits
compartment_id, occupancy_group, space_type, max_area_m2,
max_area_sprink_m2, min_fire_rating_hr, code_id, jurisdiction
```

### Test Results

**FireProtectionIntegrationTest (MATHS PROOFS):**
```
TEST 1: Sekolah (1056m²) → SPRINKLER_TRIGGER: 1056 > 1000 ✓
  - COVERAGE: ceil(1056/18.6) = 57 heads ✓
  - SPACING: sqrt(18.6) = 4.31m <= 4.6m ✓

TEST 2: TB-LKTN-2S (60m²) → NO SPRINKLER: 5.6m < 18m, 60m² < 1000m² ✓
TEST 3: TB-LKTN (80m²) → NO SPRINKLER: 2.8m < 18m ✓
TEST 4: CONDO-MID (54m, 1500m²) → SPRINKLER + PUMP: 54m > 30m gravity ✓

9/9 MATHS PROOFS PASS
```

**SanityChecker FireProtectionCheck:**
```
TB-LKTN:   PASS - Sprinklers not required (4.9m < 18m, 88m² < 1000m²)
Sekolah:   FAIL - Sprinklers REQUIRED but none (1472m² > 1000m², expect 80 heads)
```

**ComplianceEnforcementTest (AUTO_FP flow):**
```
TEST 1: School compliance:AUTO_FP → Trigger fires, 14 heads generated
TEST 2: House (no AUTO_FP) → No trigger (correct)
TEST 3: High-rise FULL_COMPLIANCE → Trigger + STRICT mode
TEST 4: DSL parsing → AUTO_FP, AUTO_FP,STRICT, FULL all parse correctly

4/4 PASS - Architecture flow validated
```

---

## Previous Session (Phase 56B)

| Task | Status |
|------|--------|
| CORE block parsing at building level | DONE |
| Storey `repeat:2-17` expansion | DONE |
| CORE element compilation (stairs, elevators, shafts) | DONE |
| AD migration (shaft_clearance_m, min_spacing_m) | DONE |
| `PlacementRuleAD.java` class | DONE |

---

## Next Session: LOD400 Library & BOM Units

### LOD400 Library Assets Available

| Asset | Count | Dimensions | Status |
|-------|-------|------------|--------|
| Waiting_Room_Seat_4St_1Tbl | 108 | 3000x600mm | Ready for lobby BOM |
| Canteen Table | 15 | 1500x1250mm | Ready for canteen BOM |
| Supply Diffuser 600x600 | 134 | - | Use for HVAC |
| Exhaust Diffuser 600x600 | 32 | - | Use for HVAC |
| StairFlight | 32 | ~1400x3000mm | ✓ Phase 58 integrated |
| Desk_with_return | 2 | - | Office workstation |
| Chair - Desk | 4 | - | Office seating |

### Recommended Priority

1. ~~**Phase 58: Stair Library Integration**~~ ✓ DONE
   - StairLibraryMapper created, integrated in BuildingWriter
   - 32 LOD400 stair geometries available with scaling

2. ~~**Phase 59: Furniture BOM Units**~~ ✓ DONE
   - FurniturePlacer for LOBBY/CANTEEN/OFFICE
   - LOD400 geometry with transforms

3. ~~**Phase 60: Variable BOM Resolution**~~ ✓ DONE
   - BOMRuleAD + BOMResolver for code-backed quantities
   - PER_AREA, PER_LUX, PER_CFM, PER_OCCUPANT calculations

4. ~~**Phase 61: Room Sizing Resolution**~~ ✓ DONE
   - RoomSizingResolver with either/or input pattern
   - Layout fitting with feedback

5. ~~**Phase 62: Integrate BOM into Compile Stage**~~ ✓ DONE
   - BOMResolver integrated in BuildingCompiler
   - FurniturePlacer accepts resolved quantities
   - Outlier logging when BOM exceeds room capacity

6. **Phase 63: RoomSizingResolver Integration** ⭐ NEXT
   - Integrate RoomSizingResolver into compile stage
   - Validate room dimensions against code minimums

### Missing from Library (Future Import)

- Desktop PC / Monitor
- Awnings / Shading devices
- Residential-scale windows (using scaled commercial workaround)
- Ceiling fans

### Discipline Coverage

| Discipline | Elements | Current State | Next Action |
|------------|----------|---------------|-------------|
| ARC | 35,338 | Windows 88% library | ✓ Phase 57C |
| FP | 6,884 | FireProtectionAD complete | ✓ Phase 57 |
| REB | 2,660 | Not implemented | Low priority |
| ACMV | 1,621 | Diffusers basic | Phase 60: Library |
| STR | 1,429 | Stairs now LOD400 | ✓ Phase 58 |

---

## Foundation Compliance Check

| Principle | Status |
|-----------|--------|
| EXTRACT, DON'T IMAGINE | ✓ All new constants traced to AD tables |
| 5-Stage DAG | ✓ Parse → Resolve → Compile → Place → Write |
| Witness System | ✓ Existing witnesses still pass (21/24) |
| Configuration over Code | ✓ PlacementRuleAD enables runtime customization |
| Deterministic | ✓ Same input → same output |

---

## Test Commands

```bash
# Fire Protection AD test
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.FireProtectionAD" -q

# CONDO-MID (high-rise)
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.CondoMidParseTest" -q

# Regressions
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q   # 4/4 PASS
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.SchoolEndToEndTest" -q   # 5/5 PASS
```

---

## Known Issues

1. **CONDO-MID DSL overlap**: `water_tank` / `lift_mr` at Roof level (51m² overlap)
2. **Unknown space types**: STAIR_ENCLOSURE, TNB_ROOM, PUMP_ROOM, GENSET_ROOM, TANK_ROOM, MACHINE_ROOM

---

## Archive

Previous session logs: `docs/PROGRESS_ARCHIVE_2026-02-04.md`
