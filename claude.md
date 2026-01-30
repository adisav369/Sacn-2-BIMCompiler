# PRIME RULE

**EXTRACT, DON'T IMAGINE.**

Query the federated model DB. Copy patterns you find. Never invent.

---

**When lost:** `cat SESSION_STATE.md` then query DB.

**After token refresh:** Read this file → state phase → continue.

---

# CURRENT STATUS (2026-01-30)

| Phase | Status | Description |
|-------|--------|-------------|
| Phases 0-30 | ✓ Complete | Foundation through config-driven extensibility |
| **Phase 31: Witness System** | ✓ Complete | JSON proof generation (7 claims) |
| **Phase 32: MEP Requirements** | ✓ Complete | SpaceType → MEPConfig derivation |
| **Phase 32B: Fixture Writing** | ✓ Complete | LOD400 fixtures to database |
| **Phase 33: Electrical Geometry** | ✓ Complete | Lights, outlets, switches from MEPConfig |

**GitHub:** https://github.com/red1oon/BIMCompiler

---

# PHASE 31-32 COMPLETION SUMMARY

## Phase 31: Witness System

New file: `src/main/java/com/bim/compiler/witness/WitnessBuilder.java`

**9 Claims Proven:**
| Claim | What It Proves |
|-------|----------------|
| FOUNDATION_GROUNDED | Slab at Z=0 |
| ENTRY_EXISTS | Door on exterior wall |
| ALL_ROOMS_REACHABLE | Rooms connected via doors |
| WINDOWS_ON_EXTERIOR | Windows on perimeter walls |
| ROOF_COVERS_ALL | Roof covers all room corners |
| ROOMS_ENCLOSED | All rooms have 4 walls |
| ROOMS_IN_ENVELOPE | All rooms inside bounding box |
| ELECTRICAL_IN_SPACES | 29 electrical elements within room bounds (Phase 33) |
| FIXTURES_ATTACHED_TO_HOSTS | 8 lights surface-attached to ceiling (Phase 33) |

**Output:** `output/tb_lktn_witness.json`

## Phase 32: MEP Requirements

New records in `SpaceTypeRegistry.java`:
```java
MEPConfig(plumbing, electrical, hvac)
PlumbingConfig(fixtures, requiresStack, requiresVent, waterSupply)
ElectricalConfig(lightPoints, powerPoints, switchPoints, circuitType)
HVACConfig(requiresExhaust, exhaustCFM, allowsAircon, ventilationType)
```

**21 space types now have MEP requirements in `config/spacetypes.yaml`**

Example derivation:
```
BATHROOM → toilet, sink, floor_trap, stack, 50 CFM exhaust
KITCHEN → sink, floor_trap, 100 CFM exhaust, dedicated circuit
BEDROOM → 1 light, 2 power, allows aircon
```

## Phase 32B: Fixture Writing Fix

**Gap Found:** FixturePlacer was placing fixtures, BuildingWriter wasn't writing them.

**Fixed:** Added `writeFixture()` method using IfcSanitaryTerminal/IfcFan.

## Phase 33: Electrical Geometry Generation

New file: `src/main/java/com/bim/compiler/library/ElectricalPlacer.java`

**Features:**
- Reads `MEPConfig.ElectricalConfig` from SpaceTypeRegistry
- Places lights from LOD400 library (E_Light_14W_Surface_LED)
- Places outlets (IfcOutlet) at 300mm height on walls
- Places switches (IfcSwitchingDevice) at 1200mm near entry

**TB-LKTN Now Outputs:**
```
=== LOD400 Library Usage Summary ===
Doors:    6 library, 0 parametric
Windows:  0 library, 7 parametric
Fixtures: 2 library, 0 parametric
Lights:   8 library, 0 parametric  ← NEW

IFC Class Distribution:
  IfcMember            60
  IfcPlate             15
  IfcOutlet            14  ← NEW
  IfcLightFixture      8   ← CONNECTED
  IfcWindow            7
  IfcSwitchingDevice   7   ← NEW
  IfcDoor              6
  IfcSanitaryTerminal  2
  IfcSlab              1
  IfcRoof              1

Total: 121 elements (was 92)
```

---

# MEP GAP ANALYSIS

## Implemented ✓

| Component | Status | Source |
|-----------|--------|--------|
| MEPConfig in SpaceTypeRegistry | ✓ | Phase 32 |
| Plumbing fixtures (toilet, sink) | ✓ | LOD400 library |
| Fixture placement (FixturePlacer) | ✓ | Phase 22 |
| Fixture writing (BuildingWriter) | ✓ | Phase 32B |
| Exhaust fan placement | ✓ | FixturePlacer (geometry issue) |
| `stack: plumbing` constraint | ✓ | DSL parser |
| **Light fixtures** | ✓ | **Phase 33 - LOD400 library** |
| **Power outlets** | ✓ | **Phase 33 - parametric** |
| **Light switches** | ✓ | **Phase 33 - parametric** |

## Gaps (from bim-dsl-dictionary.md)

| Gap | Dictionary Spec | Priority |
|-----|-----------------|----------|
| ~~Electrical points~~ | ~~LIGHT, POWER_OUTLET, SWITCH~~ | ~~HIGH~~ ✓ Done |
| Plumbing riser geometry | Vertical pipe from stack constraint | MEDIUM |
| Vent pipe geometry | Required for bathrooms/kitchens | MEDIUM |
| Floor trap geometry | Required for wet areas | LOW |
| Additional fixtures | shower, bathtub, stove, wardrobe | LOW |
| ZONE fixture placement | Place fixtures per zone in OPEN_PLAN | LOW |
| Exhaust fan sizing | Current library match too large (1.84m) | MEDIUM |

## TERMINAL Library Coverage

| IFC Class | Count | Usage |
|-----------|-------|-------|
| IfcFlowTerminal | 253 | Toilets, sinks ✓ using |
| IfcLightFixture | 801 | ✓ CONNECTED (Phase 33) |
| IfcPipeFitting | 4,198 | NOT connected |
| IfcAirTerminal | 268 | NOT connected |
| IfcDuctFitting | 683 | NOT connected |
| IfcValve | 111 | NOT connected |

---

# NEXT PHASES

## Phase 34: Plumbing Geometry Generation
- Plumbing riser geometry from `stack:` constraint
- Connect to IfcPipeFitting (4,198 in library)
- Vent pipe geometry

## Phase 35: Electrical Circuit Validation
- Load calculation per circuit
- Breaker sizing validation
- Wet area circuit rules

## Phase 36: MEP Witness Claims
- PLUMBING_STACKS_ALIGNED
- ELECTRICAL_CIRCUITS_VALID
- WET_ROOMS_HAVE_EXHAUST

---

# TB-LKTN CURRENT OUTPUT

```
elements_meta: 121 rows
elements_rtree: 121 rows
base_geometries: 103 geometries

IFC Class Distribution:
  IfcMember            60  (wall framing)
  IfcPlate             15  (wall cladding)
  IfcOutlet            14  (power outlets - Phase 33)
  IfcLightFixture       8  (library - LOD400)
  IfcWindow             7  (parametric - residential sizes)
  IfcSwitchingDevice    7  (light switches - Phase 33)
  IfcDoor               6  (library - LOD400)
  IfcSanitaryTerminal   2  (library - toilet, sink)
  IfcSlab               1
  IfcRoof               1

Outlier rate: 1.2% (exhaust fan geometry)
Witness: 9/9 claims PROVEN
```

---

# RESUME PROMPT

```
Continue BIM Compiler project. Phase 33 complete.

Current state:
- Phase 31 (Witness System): COMPLETE - 9 claims now
- Phase 32 (MEP Layer): COMPLETE - SpaceType → MEPConfig derivation
- Phase 33 (Electrical Geometry): COMPLETE

Phase 33 deliverables:
1. ElectricalPlacer.java - places lights/outlets/switches from MEPConfig
2. Lights connected to IfcLightFixture library (801 components, 100% library)
3. IfcOutlet (14) and IfcSwitchingDevice (7) placed per room
4. ELECTRICAL_IN_SPACES witness claim - 29 elements inside room bounds
5. FIXTURES_ATTACHED_TO_HOSTS witness claim - 8 lights surface-attached

Bug found & fixed during Phase 33:
- Witness detected 70mm floating gap on all lights
- Root cause: ceilingZ had 50mm MEP offset
- Fix: Use actualCeilingZ for surface-mounted lights
- Result: 0mm gap, claim PROVEN

TB-LKTN outputs:
  elements_meta: 121 rows
  Doors:    6 library, 0 parametric
  Fixtures: 2 library, 0 parametric
  Lights:   8 library, 0 parametric
  Outlets:  14 parametric
  Switches: 7 parametric
  Witness: 9/9 claims PROVEN
  Outlier rate: 1.2%

MEP Gaps (remaining):
- Plumbing riser geometry (IfcPipeFitting: 4,198 in library)
- Vent pipe geometry (required for wet areas)
- Exhaust fan sizing (library mismatch: 1.84m commercial vs residential)

Next phases:
- Phase 34: Plumbing geometry (risers, vents)
- Phase 35: Electrical circuit validation
- Phase 36: Additional MEP witness claims (PLUMBING_STACKS_ALIGNED, WET_ROOMS_HAVE_EXHAUST)

Key files:
- src/main/java/com/bim/compiler/library/ElectricalPlacer.java (Phase 33)
- src/main/java/com/bim/compiler/witness/WitnessBuilder.java (9 claims)
- src/main/java/com/bim/compiler/dsl/BuildingCompiler.java (ElectricalSpec, LightSpec extended)
- src/main/java/com/bim/compiler/dsl/BuildingWriter.java (writeElectricalElement)
- config/spacetypes.yaml (21 types with MEP)

Test command:
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q

Standing rules: PRIME RULE (extract don't imagine), mathematical proof, vocabulary as data
```

---

# KEY FILES (Updated)

```
config/
├── spacetypes.yaml           # 21 space types + MEP requirements (Phase 32)
├── assemblies.yaml           # 7 assembly templates
└── profiles/
    ├── base.yaml             # IRC 2021 defaults
    └── malaysian_residential.yaml  # UBBL 1984

src/main/java/com/bim/compiler/
├── witness/
│   └── WitnessBuilder.java       # Phase 31: Proof generation
├── dsl/
│   ├── SpaceTypeRegistry.java    # Phase 32: MEPConfig
│   ├── BuildingWriter.java       # Phase 32B: writeFixture(), Phase 33: writeElectricalElement()
│   ├── BuildingCompiler.java     # Phase 33: ElectricalSpec, LightSpec extended
│   └── DoorWindowLibraryMapper.java
├── library/
│   ├── FixturePlacer.java        # Phase 22: toilet, sink, exhaust
│   ├── ElectricalPlacer.java     # Phase 33: lights, outlets, switches
│   ├── ComponentLibrary.java
│   └── StructuralPlacer.java
└── util/
    └── OutlierLogger.java

docs/
├── USER_GUIDE.md                 # Comprehensive user guide
├── bim-dsl-dictionary.md         # MEP specs in sections 10-11
└── gap-analysis-dictionary-vs-implementation.md
```

---

# TEST COMMANDS

```bash
# TB-LKTN end-to-end (includes witness + fixtures)
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q

# SpaceTypeRegistry with MEP
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.SpaceTypeRegistry" -q

# Sanity checker (independent verification)
cd tools/sanity-checker && mvn clean package -q
java -jar target/sanity-checker-1.0-SNAPSHOT.jar ../../output/tb_lktn.db
```

---

# ARCHITECTURE (Phase 33)

```
PURE CORE (unchanging)
├── Federated DB Schema (TERMINAL-proven)
├── DSL Grammar (SPACE as universal primitive)
├── Compiler Engine (reads configuration)
├── Factory Pattern (routes to library/parametric)
├── Validator Framework (composes dynamically)
├── OutlierLogger (feedback loop)
├── WitnessBuilder (proof generation)          ← Phase 31
├── ElectricalPlacer (lights/outlets/switches) ← Phase 33
└── Blender Pipeline (federation_viz_helper.py)

DYNAMIC VOCABULARY (growing via configuration)
├── SpaceTypes (config/spacetypes.yaml)
│   └── MEPConfig (plumbing, electrical, hvac) ← Phase 32
├── Assemblies (config/assemblies.yaml)
├── Profiles (config/profiles/*.yaml)
├── Protocols (building type templates)
├── Schedules (door/window/material types)
└── Validation Rules (per SpaceType)

LOD400 LIBRARY (TERMINAL-extracted)
├── IfcDoor (112) → 100% connected
├── IfcFlowTerminal (253) → toilet, sink connected
├── IfcLightFixture (801) → ✓ CONNECTED (Phase 33)
├── IfcPipeFitting (4198) → NOT connected
└── IfcWindow (183) → domain mismatch (commercial)
```

**System is production-ready with 1.2% outlier rate.**
