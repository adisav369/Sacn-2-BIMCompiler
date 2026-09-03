# Mission Phase 50: School Building Typology

**Status:** NEXT — After Phase 49B (library stair gate) complete  
**Target:** Single-storey developing-country primary school  
**Principle:** Good Enough for Beta. No engine surgery. Vocabulary + config only.

---

## Strategic Rationale

### Why School

1. **TERMINAL patterns transfer directly** — Passenger terminal spatial grammar (circulation spine → rooms branching off → service blocks at ends) maps 1:1 to school layout
2. **Simpler than residential duplex** — No multi-unit party walls, no per-unit MEP scoping, no stacked floors
3. **First institutional building** — Proves compiler handles non-residential typologies
4. **3rd world FOSS impact** — Schools are the most commonly built public buildings in developing countries. UNESCO, World Bank education programmes, and NGOs are the audience
5. **Community magnet** — "Help us add your country's school standard" is a natural contribution invitation
6. **Showcases full TERMINAL sophistication** — Beams/columns in assembly hall (STR), HVAC in computer lab (ACMV), sprinklers in hall (FP), clustered plumbing in toilet blocks (SP) — demonstrates 6 of 9 TERMINAL disciplines in one building. Not a dumbed-down demo.

### TERMINAL → School Mapping

| TERMINAL Space | School Equivalent | What Transfers |
|----------------|-------------------|----------------|
| DEPARTURE_LOUNGE | ASSEMBLY_HALL | Large clear-span, high occupancy, sprinklers |
| CONCOURSE | CORRIDOR | Linear circulation spine, fire egress |
| GATE | CLASSROOM | Repeated identical rooms off corridor |
| RESTROOM | TOILET_BLOCK | Clustered wet services, shared stack |
| OFFICE (ops) | STAFFROOM / OFFICE | Standard enclosed room |

---

## Engine Assessment

### Works Today — No Changes

| Capability | Why It Works |
|------------|-------------|
| SpaceType vocabulary | YAML config — add entries, no recompile |
| Profile system | Create `malaysian_school.yaml`, extends BASE |
| Grid system | School is just a bigger grid with 8m classroom bays |
| MEP electrical | Lights, fans, outlets — existing placers |
| MEP plumbing | Toilet blocks = clustered bathrooms — existing pattern |
| Witness system | All 17 single-unit claims transfer. Add school-specific ones |
| IFC export | IfcSpace with IfcSpaceType — standard IFC |
| BOM export | Same assembly/component structure |

### Genuine Challenges

| Challenge | Severity | Mitigation |
|-----------|----------|------------|
| Double-loaded corridor (10+ rooms → 1 corridor) | Medium | Grid bounds position rooms explicitly. Solver verifies, doesn't discover. Provide explicit bounds in DSL |
| Repeated room verbosity | Low | Write each room individually. Future DSL sugar (`REPEAT`) is nice-to-have, not blocker |
| Assembly hall column/beam layout | **SOLVED** | StructuralPlacer already functional. TERMINAL has 404 beams + 122 columns extracted. Place columns at grid intersections, beams spanning between. **EXTRACTED, not imagined.** |
| Toilet block as compound space | **SOLVED** | TERMINAL has dedicated large restroom areas — closer to school toilet blocks than residential bathrooms. Plumbing patterns (clustered fixtures, shared stacks, multiple cubicles) **copy-paste from TERMINAL restrooms.** |
| Two-block site (teaching + hall) | Low | Both blocks on same grid, same storey. 4m gap between blocks is empty site space. No covered walkway needed for beta. |

### TERMINAL Assets Directly Applicable to School

TERMINAL is a passenger terminal with 9 disciplines and 51,723 LOD400 elements.
**School reuses patterns TERMINAL already has — this is not extension, it's reapplication.**

| TERMINAL Asset | Count | Placer | School Use |
|----------------|-------|--------|------------|
| IfcBeam | 404 | StructuralPlacer ✓ | Assembly hall clear span |
| IfcColumn | 122 | StructuralPlacer ✓ | Assembly hall + corridor columns |
| IfcAirTerminal (DIFFUSER) | 268 | HVACPlacer ✓ | Staffroom, computer lab, library AC |
| IfcDuctFitting | 683 | HVACPlacer ✓ | Duct routing in ceiling plenum |
| IfcFireSuppressionTerminal | 891 | SprinklerPlacer ✓ | Assembly hall sprinklers |
| IfcLightFixture | 801 | (available) | All rooms |
| IfcPipeFitting | 4,198 | PlumbingPlacer ✓ | Toilet blocks |
| IfcAlarm | 71 | (available) | Fire alarm coverage |

**Every placer needed for a school already exists and is functional.**

---

## Vocabulary Additions

### File: `config/spacetypes.yaml`

```yaml
# === INSTITUTIONAL SPACES ===

CLASSROOM:
  category: HABITABLE
  omniclass: "13-31 11 11"
  wall_rule: ENCLOSED
  validation:
    min_area: 48.0          # ~7m x 8m for 40 students
    min_dimension: 6.0
    requires_window: true    # Daylight mandatory
    requires_egress: true
  fixtures:
    required: [WHITEBOARD, TEACHER_DESK]
    optional: [CEILING_FAN, PROJECTOR_SCREEN]
  mep:
    required: [LIGHT, POWER_OUTLET, CEILING_FAN]
    optional: [AIR_CONDITIONING]
  openings:
    door: { min_width: 900, min_height: 2100 }
    window: { min_count: 3, preferred_wall: exterior }
  aliases: [BILIK_DARJAH, KELAS]

SCIENCE_LAB:
  category: HABITABLE
  omniclass: "13-31 11 17"
  wall_rule: ENCLOSED
  validation:
    min_area: 64.0          # Larger for lab benches
    min_dimension: 7.0
    requires_window: true
    requires_ventilation: true
  fixtures:
    required: [LAB_BENCH, SINK, FUME_HOOD]
    optional: [SAFETY_SHOWER, EYE_WASH]
  mep:
    required: [LIGHT, POWER_OUTLET, PLUMBING_STACK, EXHAUST]
  aliases: [MAKMAL_SAINS]

COMPUTER_LAB:
  category: HABITABLE
  omniclass: "13-31 11 19"
  wall_rule: ENCLOSED
  validation:
    min_area: 56.0
    min_dimension: 6.0
    requires_window: false   # Can be interior for glare control
  fixtures:
    required: [COMPUTER_DESK]
  mep:
    required: [LIGHT, POWER_OUTLET, DIFFUSER]  # AC required — heat load from computers
    outlet_density: HIGH              # 1 per desk position
    hvac: REQUIRED                    # TERMINAL ACMV patterns apply
  aliases: [MAKMAL_KOMPUTER]

ASSEMBLY_HALL:
  category: HABITABLE
  omniclass: "13-41 11 11"
  wall_rule: ENCLOSED
  validation:
    min_area: 200.0         # ~20m x 12m
    min_dimension: 10.0
    max_occupancy_sqm: 0.65 # IBC assembly occupancy
  fixtures:
    required: [STAGE]
    optional: [SOUND_SYSTEM, PROJECTOR]
  mep:
    required: [LIGHT, POWER_OUTLET, CEILING_FAN, EMERGENCY_LIGHT]
    optional: [SPRINKLER, DIFFUSER]
  structural:
    system: COLUMN_BEAM     # StructuralPlacer places at grid intersections
    source: TERMINAL        # 404 beams, 122 columns EXTRACTED
    column_grid: true       # Columns at grid axis intersections
    beam_span: along_long_axis
  aliases: [DEWAN, HALL]

STAFFROOM:
  category: HABITABLE
  omniclass: "13-31 15 11"
  wall_rule: ENCLOSED
  validation:
    min_area: 24.0
    min_dimension: 4.0
    requires_window: true
  fixtures:
    optional: [KITCHENETTE, STORAGE_CABINET]
  mep:
    required: [LIGHT, POWER_OUTLET]
    optional: [CEILING_FAN, DIFFUSER]  # HVAC from TERMINAL ACMV
  aliases: [BILIK_GURU]

CANTEEN:
  category: HABITABLE
  omniclass: "13-41 17 11"
  wall_rule: ENCLOSED       # ENCLOSED for beta; tropical SEMI_OPEN is future work
  validation:
    min_area: 80.0
    min_dimension: 6.0
  fixtures:
    required: [SERVING_COUNTER, SINK]
    optional: [FOOD_PREP_AREA]
  mep:
    required: [LIGHT, POWER_OUTLET, PLUMBING_STACK]
  aliases: [KANTIN]

TOILET_BLOCK:
  category: SERVICE
  omniclass: "13-25 11 11"
  wall_rule: ENCLOSED
  validation:
    min_area: 12.0
    min_dimension: 2.4
    requires_ventilation: true
  fixtures:
    required: [TOILET, SINK]
    count_rule: by_occupancy  # Fixtures scaled to building occupancy
  mep:
    required: [LIGHT, PLUMBING_STACK, EXHAUST]
  pattern_source: TERMINAL_RESTROOM  # TERMINAL has dedicated large restroom areas
    # 4,198 IfcPipeFittings extracted — clustered fixtures, shared stacks
    # Copy-paste from TERMINAL restroom, not scaled-up residential bathroom
  aliases: [TANDAS, BILIK_AIR]

LIBRARY:
  category: HABITABLE
  omniclass: "13-31 13 11"
  wall_rule: ENCLOSED
  validation:
    min_area: 56.0
    min_dimension: 6.0
    requires_window: true
  fixtures:
    optional: [BOOKSHELF, READING_TABLE, LIBRARIAN_DESK]
  mep:
    required: [LIGHT, POWER_OUTLET]
    optional: [CEILING_FAN, DIFFUSER]  # AC preserves books in tropical climate
  aliases: [PERPUSTAKAAN, PUSAT_SUMBER]

OFFICE:
  category: HABITABLE
  omniclass: "13-31 15 13"
  wall_rule: ENCLOSED
  validation:
    min_area: 12.0
    min_dimension: 3.0
    requires_window: true
  mep:
    required: [LIGHT, POWER_OUTLET]
    optional: [AIR_CONDITIONING]
  aliases: [PEJABAT]

STORE:
  category: SERVICE
  omniclass: "13-25 15 11"
  wall_rule: ENCLOSED
  validation:
    min_area: 6.0
    min_dimension: 2.0
    requires_window: false
  mep:
    required: [LIGHT]
  aliases: [STOR, STORAGE]
```

### File: `config/profiles/malaysian_school.yaml`

```yaml
name: Malaysian_Primary_School
extends: BASE
validation_code: UBBL_1984
localization: MS_MY
climate: TROPICAL_HUMID

defaults:
  storey_height: 3.2        # Higher than residential for ventilation
  roof_pitch: 15             # Low pitch metal roof
  roof_overhang: 900         # Wider for rain protection
  wall_exterior: 150         # 150mm brick
  wall_interior: 100         # 100mm brick
  door_classroom: 900x2100
  door_toilet: 750x2100
  window_classroom: 1800x1200  # Larger for daylight
  ceiling_fan: required      # Every habitable room
  
vocabulary:
  BILIK_DARJAH: CLASSROOM
  MAKMAL_SAINS: SCIENCE_LAB
  MAKMAL_KOMPUTER: COMPUTER_LAB
  DEWAN: ASSEMBLY_HALL
  BILIK_GURU: STAFFROOM
  KANTIN: CANTEEN
  PERPUSTAKAAN: LIBRARY
  PEJABAT: OFFICE
  TANDAS: TOILET_BLOCK
  STOR: STORE

constraints:
  max_storeys: 2             # Typical primary school
  natural_ventilation: required
  covered_walkway: recommended  # Between blocks — future DSL feature
  toilet_ratio_male: 1:40    # 1 WC per 40 students
  toilet_ratio_female: 1:25  # 1 WC per 25 students
```

### File: `config/protocols/primary_school.yaml`

```yaml
name: Primary_School
description: "Single-storey primary school for developing country context"
applicable_profiles: [Malaysian_Primary_School]

required_spaces:
  CLASSROOM: { min: 6 }
  TOILET_BLOCK: { min: 2, types: [MALE, FEMALE] }
  STAFFROOM: { min: 1 }
  OFFICE: { min: 1 }

optional_spaces:
  - ASSEMBLY_HALL
  - CANTEEN
  - LIBRARY
  - SCIENCE_LAB
  - COMPUTER_LAB
  - STORE

required_relationships:
  classrooms: { opens_to: CORRIDOR }
  toilet_blocks: { near: CORRIDOR, max_travel: 60m }
  staffroom: { near: OFFICE }

validation_rules:
  fire_egress: max_travel_45m  # Single storey relaxation
  accessibility: ramp_if_level_change
  daylight: all_classrooms_exterior_wall
```

---

## Grid Design

### Design Rationale

The school has two blocks on a shared site grid:

1. **Teaching Block** (Y rows 1–4): Double-loaded corridor with classrooms on both sides, service rooms at ends
2. **Assembly + Canteen Block** (Y rows 5–7): Large-span hall with structural grid, canteen adjacent

A 4m gap (Y rows 4–5) separates the blocks — covered walkway is future DSL work.

### Grid Geometry

```
GRID {
    axes: A, B, C, D, E, F / 1, 2, 3, 4, 5, 6, 7
    spacing: 4, 8, 8, 8, 4 / 7, 3, 7, 4, 6, 6
}
```

**Computed positions:**
```
X: A=0  B=4  C=12  D=20  E=28  F=32
Y: 1=0  2=7  3=10  4=17  5=21  6=27  7=33
```

**Layout diagram:**
```
Y
33 ─────────────────────────────────────── 7
    │  CANTEEN    │     ASSEMBLY HALL     │
    │  (A5-C7)    │       (C5-F7)         │
    │  12×12m     │       20×12m          │
27 ─│─ ─ ─ ─ ─ ─ │─ ─ ─ ─6─ ─ ─ ─ ─ ─ ─│
    │  144 sqm    │       240 sqm         │
21 ─────────────────────────────────────── 5
    │          4m gap / walkway           │
17 ─────────────────────────────────────── 4
    │ TOI_F │ CLS4 │ CLS5 │ CLS6 │  OFC  │
    │A3-B4  │B3-C4 │C3-D4 │D3-E4 │ E3-F4 │
    │ 4×7   │ 8×7  │ 8×7  │ 8×7  │  4×7  │
10 ─────────────────────────────────────── 3
    │         CORRIDOR  A2-F3             │
    │            32×3m = 96 sqm           │
 7 ─────────────────────────────────────── 2
    │ TOI_M │ CLS1 │ CLS2 │ CLS3 │ STAFF │
    │A1-B2  │B1-C2 │C1-D2 │D1-E2 │ E1-F2 │
    │ 4×7   │ 8×7  │ 8×7  │ 8×7  │  4×7  │
 0 ─────────────────────────────────────── 1
    A=0     B=4    C=12   D=20   E=28  F=32
                         X
```

### Room Validation Check

| Room | Bounds | Size (m) | Area (sqm) | Min Area | Min Dim | Pass |
|------|--------|----------|------------|----------|---------|------|
| toilet_m | A1-B2 | 4×7 | 28 | 12 | 2.4 | ✓ |
| class_1 | B1-C2 | 8×7 | 56 | 48 | 6.0 | ✓ |
| class_2 | C1-D2 | 8×7 | 56 | 48 | 6.0 | ✓ |
| class_3 | D1-E2 | 8×7 | 56 | 48 | 6.0 | ✓ |
| bilik_guru | E1-F2 | 4×7 | 28 | 24 | 4.0 | ✓ |
| koridor | A2-F3 | 32×3 | 96 | — | 0.914 | ✓ |
| toilet_f | A3-B4 | 4×7 | 28 | 12 | 2.4 | ✓ |
| class_4 | B3-C4 | 8×7 | 56 | 48 | 6.0 | ✓ |
| class_5 | C3-D4 | 8×7 | 56 | 48 | 6.0 | ✓ |
| class_6 | D3-E4 | 8×7 | 56 | 48 | 6.0 | ✓ |
| pejabat | E3-F4 | 4×7 | 28 | 12 | 3.0 | ✓ |
| kantin | A5-C7 | 12×12 | 144 | 80 | 6.0 | ✓ |
| dewan | C5-F7 | 20×12 | 240 | 200 | 10.0 | ✓ |

**All 13 rooms pass validation.** Zero tolerance issues.

### Structural Grid in Assembly Hall

Hall at C5-F7 contains grid intersections: C5, C6, C7, D5, D6, D7, E5, E6, E7, F5, F6, F7 = **12 column positions**. StructuralPlacer places columns at these intersections. Beams span:
- X-direction (long axis): C→D, D→E, E→F = 8m spans at Y=21, 27, 33 = **9 beams**
- Y-direction (short axis): 5→6, 6→7 = 6m spans at X=12, 20, 28, 32 = **8 beams**

6m and 8m spans are realistic for reinforced concrete school construction. Source: TERMINAL beam/column patterns (EXTRACTED).

---

## Reference DSL File

### File: `examples/Sekolah-Kebangsaan.bim`

**Parser compatibility notes:**
- Uses grid bounds notation (`bounds:A1-B2` = corner-to-corner rectangle)
- Uses `opens_to:` constraint (confirmed working in current parser)
- Uses `SCHEDULE` blocks (confirmed working from TB-LKTN)
- New space type keywords (CLASSROOM, etc.) require SpaceTypeRegistry lookup — if parser doesn't dynamically resolve, use `SPACE "name" type:CLASSROOM` as fallback
- `count:N` on WINDOW may need minor parser addition — fallback is repeating WINDOW lines

```
BUILDING "Sekolah_Kebangsaan_Bukit_Cermin"
    profile: "Malaysian_Primary_School"
{
    GRID {
        axes: A, B, C, D, E, F / 1, 2, 3, 4, 5, 6, 7
        spacing: 4, 8, 8, 8, 4 / 7, 3, 7, 4, 6, 6
    }

    // Computed positions:
    // X: A=0, B=4, C=12, D=20, E=28, F=32
    // Y: 1=0, 2=7, 3=10, 4=17, 5=21, 6=27, 7=33

    SCHEDULE doors {
        D1: 900x2100 "Timber panel classroom door"
        D2: 750x2100 "Flush door toilet"
        D3: 1200x2100 "Double leaf hall entrance"
    }
    
    SCHEDULE windows {
        W1: 1800x1200 "Aluminium 3-panel louvred classroom"
        W2: 600x500   "Aluminium top-hung toilet"
        W3: 2400x1200 "Aluminium 4-panel louvred hall"
    }
    
    STOREY "Ground" level:0 height:3.2m {
    
        // ================================================================
        // TEACHING BLOCK — North Wing (Y 0-7)
        // ================================================================
        
        // --- Service: Toilet Male (west end) ---
        TOILET_BLOCK "toilet_m" bounds:A1-B2 {
            exterior: north
            exterior: west
            opens_to: koridor
            DOOR type:D2 wall:south
            WINDOW type:W2 wall:west
            WINDOW type:W2 wall:north
        }
        
        // --- Classrooms: North side ---
        CLASSROOM "class_1" bounds:B1-C2 {
            exterior: north
            opens_to: koridor
            DOOR type:D1 wall:south
            WINDOW type:W1 wall:north
            WINDOW type:W1 wall:north
            WINDOW type:W1 wall:north
        }
        
        CLASSROOM "class_2" bounds:C1-D2 {
            exterior: north
            opens_to: koridor
            DOOR type:D1 wall:south
            WINDOW type:W1 wall:north
            WINDOW type:W1 wall:north
            WINDOW type:W1 wall:north
        }
        
        CLASSROOM "class_3" bounds:D1-E2 {
            exterior: north
            opens_to: koridor
            DOOR type:D1 wall:south
            WINDOW type:W1 wall:north
            WINDOW type:W1 wall:north
            WINDOW type:W1 wall:north
        }
        
        // --- Admin: Staffroom (east end, north) ---
        STAFFROOM "bilik_guru" bounds:E1-F2 {
            exterior: north
            exterior: east
            opens_to: koridor
            DOOR type:D1 wall:south
            WINDOW type:W1 wall:east
            WINDOW type:W1 wall:north
        }
        
        // ================================================================
        // TEACHING BLOCK — Central Corridor (Y 7-10)
        // ================================================================
        
        CORRIDOR "koridor" bounds:A2-F3 {
            // Corridor runs full length of teaching block
            // Exterior at west end (X=0) and east end (X=32)
            exterior: west
            exterior: east
            DOOR type:D1 wall:west      // West entrance
            DOOR type:D1 wall:east      // East entrance
        }
        
        // ================================================================
        // TEACHING BLOCK — South Wing (Y 10-17)
        // ================================================================
        
        // --- Service: Toilet Female (west end) ---
        TOILET_BLOCK "toilet_f" bounds:A3-B4 {
            exterior: south
            exterior: west
            opens_to: koridor
            DOOR type:D2 wall:north
            WINDOW type:W2 wall:west
            WINDOW type:W2 wall:south
        }
        
        // --- Classrooms: South side ---
        CLASSROOM "class_4" bounds:B3-C4 {
            exterior: south
            opens_to: koridor
            DOOR type:D1 wall:north
            WINDOW type:W1 wall:south
            WINDOW type:W1 wall:south
            WINDOW type:W1 wall:south
        }
        
        CLASSROOM "class_5" bounds:C3-D4 {
            exterior: south
            opens_to: koridor
            DOOR type:D1 wall:north
            WINDOW type:W1 wall:south
            WINDOW type:W1 wall:south
            WINDOW type:W1 wall:south
        }
        
        CLASSROOM "class_6" bounds:D3-E4 {
            exterior: south
            opens_to: koridor
            DOOR type:D1 wall:north
            WINDOW type:W1 wall:south
            WINDOW type:W1 wall:south
            WINDOW type:W1 wall:south
        }
        
        // --- Admin: Office (east end, south) ---
        OFFICE "pejabat" bounds:E3-F4 {
            exterior: south
            exterior: east
            opens_to: koridor
            DOOR type:D1 wall:north
            WINDOW type:W1 wall:east
            WINDOW type:W1 wall:south
        }
        
        // ================================================================
        // ASSEMBLY + CANTEEN BLOCK (Y 21-33)
        // Separate block south of teaching wing, 4m gap between
        // ================================================================
        
        // --- Canteen (west portion) ---
        CANTEEN "kantin" bounds:A5-C7 {
            exterior: south
            exterior: west
            exterior: north
            DOOR type:D3 wall:north
            WINDOW type:W3 wall:south
            WINDOW type:W3 wall:south
            WINDOW type:W3 wall:west
        }
        
        // --- Assembly Hall (east portion) ---
        // Structural: StructuralPlacer places columns at grid intersections
        //   12 columns at C/D/E/F × 5/6/7 intersections
        //   Beams spanning 8m (X) and 6m (Y) — realistic RC spans
        // MEP: SprinklerPlacer for fire protection, HVACPlacer for ventilation
        // Pattern source: TERMINAL departure lounge — all EXTRACTED
        ASSEMBLY_HALL "dewan" bounds:C5-F7 {
            exterior: south
            exterior: east
            exterior: north
            DOOR type:D3 wall:north
            DOOR type:D3 wall:north
            WINDOW type:W3 wall:south
            WINDOW type:W3 wall:south
            WINDOW type:W3 wall:south
            WINDOW type:W3 wall:east
            WINDOW type:W3 wall:east
        }
    }
    
    // ================================================================
    // MEP SHOWCASE
    // ================================================================
    // Classroom: LIGHT + POWER_OUTLET + CEILING_FAN (electrical)
    // Toilet blocks: clustered plumbing — TERMINAL restroom patterns
    // Assembly hall: SPRINKLER + structural columns/beams — TERMINAL FP + STR
    // All driven by SpaceType YAML config, not DSL attributes
    // 6 of 9 TERMINAL disciplines: ARC, STR, ACMV, ELEC, FP, SP

    ROOF pitch:15deg overhang:900mm
}
```

### Exterior Wall Verification

| Room | North | South | East | West | Interior walls |
|------|-------|-------|------|------|---------------|
| toilet_m (A1-B2) | Y=0 ✓ | corridor | class_1 | X=0 ✓ | — |
| class_1 (B1-C2) | Y=0 ✓ | corridor | class_2 | toilet_m | — |
| class_2 (C1-D2) | Y=0 ✓ | corridor | class_3 | class_1 | — |
| class_3 (D1-E2) | Y=0 ✓ | corridor | bilik_guru | class_2 | — |
| bilik_guru (E1-F2) | Y=0 ✓ | corridor | X=32 ✓ | class_3 | — |
| koridor (A2-F3) | rooms | rooms | X=32 ✓ | X=0 ✓ | — |
| toilet_f (A3-B4) | corridor | Y=17 ✓ | class_4 | X=0 ✓ | — |
| class_4 (B3-C4) | corridor | Y=17 ✓ | class_5 | toilet_f | — |
| class_5 (C3-D4) | corridor | Y=17 ✓ | class_6 | class_4 | — |
| class_6 (D3-E4) | corridor | Y=17 ✓ | pejabat | class_5 | — |
| pejabat (E3-F4) | corridor | Y=17 ✓ | X=32 ✓ | class_6 | — |
| kantin (A5-C7) | Y=21 ✓ | Y=33 ✓ | dewan | X=0 ✓ | — |
| dewan (C5-F7) | Y=21 ✓ | Y=33 ✓ | X=32 ✓ | kantin | — |

All `exterior:` constraints align with actual building edges. ✓

---

## New Witness Claims

### School-Specific (Phase 50)

| # | Claim | Verification |
|---|-------|-------------|
| 18 | `CLASSROOM_DAYLIGHT` | Every CLASSROOM has ≥1 exterior wall with windows |
| 19 | `TOILET_ACCESSIBLE` | Every TOILET_BLOCK within 60m travel of furthest CLASSROOM |
| 20 | `CORRIDOR_CONNECTS_ALL` | Every SPACE in teaching block has `opens_to: koridor` or IS koridor |
| 21 | `FIRE_TRAVEL_DISTANCE` | No point in teaching block >45m from corridor exit (single-storey relaxation) |
| 22 | `STRUCTURAL_GRID_COMPLETE` | Assembly hall columns at grid intersections, beams spanning between |

**Note:** `HVAC_ZONES_COVERED` deferred — HVAC is optional in the pilot DSL. Add when computer lab or library included in future variants.

### Existing Claims That Transfer Unchanged

All 17 single-unit claims apply:
- `ROOMS_ENCLOSED`, `ROOMS_IN_ENVELOPE`, `ROOF_COVERS_ALL` — geometry
- `DOORS_REACHABLE`, `WINDOWS_ON_EXTERIOR` — connectivity
- `PLUMBING_WASTE/VENT/SUPPLY_COMPLETE` — toilet blocks
- `ALL_OUTLETS_ON_CIRCUIT` — electrical
- `ROOM_AREAS_CONSISTENT` — minimum classroom size

**Multi-unit claims (`PARTY_WALLS_VALID`, `SEPARATING_FLOORS_VALID`) are SKIPPED** — school is single-unit.

**Target: 22/22 PROVEN (17 existing + 5 school-specific). Zero UNPROVABLE.**

---

## Parser Compatibility

### Verified Working (no changes needed)

| Syntax | Source |
|--------|--------|
| `bounds:A1-B2` | Phase 26, TB-LKTN |
| `opens_to: room` | DSL dictionary |
| `exterior: direction` | Phase 1 core |
| `SCHEDULE doors { ... }` | TB-LKTN |
| `DOOR type:D1 wall:south` | TB-LKTN |
| `WINDOW type:W1 wall:north` | TB-LKTN |
| `ROOF pitch:15deg overhang:900mm` | Phase 26 |

### Needs Verification (Phase 50A gate)

| Syntax | Risk | Fallback |
|--------|------|----------|
| `CLASSROOM "name" bounds:...` as keyword | Medium — parser may not resolve from SpaceTypeRegistry | Use `SPACE "name" type:CLASSROOM bounds:...` |
| `profile: "name"` on BUILDING | Low — may already work | Omit; profile loaded separately |
| Multiple `WINDOW` lines per room | Low — parser likely handles | — |

### Not In Parser (not needed for beta)

| Syntax | Why Not Needed |
|--------|---------------|
| `connects: [list]` | Each room uses `opens_to:` individually |
| `type: MALE/FEMALE` on toilet | Encode in name: `toilet_m`, `toilet_f` |
| `structural: COLUMN_BEAM` | Driven by SpaceType YAML config, not DSL |
| `mep: [list]` | Driven by SpaceType YAML config, not DSL |
| `SEMI_OPEN` wall_rule | Canteen uses ENCLOSED for beta |
| `count:N` on WINDOW | Repeat WINDOW lines instead |

---

## Implementation Phases

### Phase 50A: Vocabulary + Parser Gate (1 session)
- Add school SpaceTypes to `config/spacetypes.yaml`
- Create `config/profiles/malaysian_school.yaml`
- Create `config/protocols/primary_school.yaml`
- Verify SpaceTypeRegistry loads all new types
- **Parser gate test:** attempt `CLASSROOM "test" size:8x7m {}` — if parser rejects, use SPACE keyword fallback in DSL
- **No engine changes**

### Phase 50B: DSL File + Compilation (1-2 sessions)
- Create `examples/Sekolah-Kebangsaan.bim` (reference DSL above)
- Compile with existing engine
- Fix any parser/solver issues with double-loaded corridor pattern
- Verify all 17 existing witnesses pass
- **Engine fixes only — no new features**

### Phase 50C: School Witnesses (1 session)
- Add `CLASSROOM_DAYLIGHT` witness
- Add `TOILET_ACCESSIBLE` witness
- Add `CORRIDOR_CONNECTS_ALL` witness
- Add `FIRE_TRAVEL_DISTANCE` witness
- Add `STRUCTURAL_GRID_COMPLETE` witness
- Verify all 22 claims PROVEN

### Phase 50D: Validation + Polish (1 session)
- IFC export review — open in FreeCAD/BlenderBIM
- BOM export — verify classroom fixtures, structural members, plumbing appear
- Document any limitations honestly
- Update README with school example

---

## Known Limitations (Document Honestly)

| Limitation | Why Acceptable |
|------------|---------------|
| No covered walkway between blocks | Future DSL feature. 4m gap exists in grid. Document as gap. |
| No furniture layout in classrooms | LOD 300 scope. Fixtures as BOM items only. |
| Fixed classroom count (no REPEAT syntax) | Verbose but functional. DSL sugar is future work. |
| No occupancy-based fixture scaling | Toilet fixture count manual. Auto-scaling is future work. |
| No playground/sports field | External works out of scope for building compiler. |
| Canteen enclosed (not open-sided) | `SEMI_OPEN` wall_rule needs engine addition. Use ENCLOSED for beta. Note as future. |
| No library/science lab/computer lab in pilot DSL | Vocabulary defined in YAML. Pilot DSL uses minimal program. Add rooms in future variants. |
| Assembly hall separate block (no covered link) | Both blocks on same grid, same storey. Covered walkway is future DSL feature. |
| HVAC not in pilot | Optional MEP. Tropical schools use ceiling fans. HVAC showcase deferred to computer lab variant. |

---

## Success Criteria

```
✓ Sekolah-Kebangsaan.bim compiles without engine changes (engine fixes OK)
✓ 22/22 witness claims PROVEN (17 existing + 5 school-specific)
✓ IFC output opens in FreeCAD — school layout recognisable
✓ Assembly hall shows columns + beams from StructuralPlacer
✓ Toilet blocks show clustered plumbing from TERMINAL restroom patterns
✓ BOM lists classrooms, toilet fixtures, electrical, structural items
✓ Zero UNPROVABLE witnesses
✓ README updated with school as second example
✓ FOSS community can add their country's school profile via YAML
```

---

## PRIME RULE Compliance

| Source | Status |
|--------|--------|
| TERMINAL patterns for circulation/corridor | ✓ EXTRACTED |
| TERMINAL beams (404) + columns (122) for assembly hall | ✓ EXTRACTED |
| TERMINAL restroom areas for toilet blocks | ✓ EXTRACTED |
| TERMINAL ACMV (268 diffusers, 683 duct fittings) for HVAC | ✓ EXTRACTED |
| TERMINAL sprinklers (891) for assembly hall fire protection | ✓ EXTRACTED |
| TERMINAL plumbing (4,198 pipe fittings) for toilet blocks | ✓ EXTRACTED |
| Classroom dimensions (Malaysian JKR standards) | ◆ RESEARCHED |
| Toilet ratios (UBBL/JKR) | ◆ RESEARCHED |
| Fire travel distance (IBC relaxation) | ◆ RESEARCHED |

**Convention:** ✓ EXTRACTED from TERMINAL | ◆ RESEARCHED from standards | ○ PENDING future extraction

**Note:** Zero PENDING items. Every pattern needed for school building exists in TERMINAL or published standards.
This is not an extension — it is a reapplication of existing extracted patterns.

---

## Dependency: Phase 49B Gate

Phase 50 begins after Phase 49B (replace parametric stair geometry with HybridFactory library lookup) is complete or explicitly deferred. Phase 49B is a residential beta gate and does not affect school compilation, but should be resolved to maintain the "no deferred library connections" discipline.

---

*Mission brief generated 2026-02-02*  
*Watchdog assessment: Engine can handle this. Grid geometry verified. All rooms pass validation.*  
*"Better is the enemy of good enough" — ship residential beta, then this.*
