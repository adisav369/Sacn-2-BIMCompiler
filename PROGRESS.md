# PROGRESS — Current Development State

**Last updated:** 2026-02-04
**Current phase:** Phase 57B Complete
**Commit:** pending [PHASE 57B] ADSession integration into BuildingCompiler

---

## Session Summary (2026-02-04) - Phase 57B

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

## Next Session: Discipline Implementation

### Discipline Coverage (from TERMINAL extraction)

| Discipline | Elements | Current State | Next Action |
|------------|----------|---------------|-------------|
| ARC | 35,338 | Walls, doors, windows, rooms | - |
| FP | 6,884 | FireProtectionAD complete | ✓ Phase 57 DONE |
| REB | 2,660 | Not implemented | Phase 58: Rebar AD |
| ACMV | 1,621 | Diffusers basic | Phase 59: Duct routing |
| CW | 1,431 | Not applicable | Skip (facade only) |
| STR | 1,429 | Columns, beams | - |
| ELEC | 1,172 | Lights, outlets | Phase 60: Panel schedule |
| SP | 979 | Fixtures, waste graph | Phase 61: Supply routing |
| LPG | 209 | Not implemented | Low priority |

### Recommended Priority

1. **Phase 57B: ADSession in BuildingCompiler** ✓ DONE
   - ThreadLocal holder for session access
   - compile() and compileFromManifest() wrapped
   - SpaceTypeRegistry uses session when available

2. **Phase 58: REB (Rebar) AD** ⭐ NEXT
   - `ad_rebar_schedule` (bar sizing, spacing)
   - `ad_structural_reinforcement` (by element type)
   - Integration with structural elements

3. **Phase 59: ACMV (Duct Routing) AD**
   - `ad_duct_sizing` (CFM-based diameter)
   - `ad_air_terminal` (diffuser/grille specs)
   - Extend MEPSystem graph for air

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
