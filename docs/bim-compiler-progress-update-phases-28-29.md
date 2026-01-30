# BIM COMPILER PROGRESS UPDATE - PHASES 28-29

**Covers:** Phases 28-29 (Dictionary v2.0, LOD400 Reconnection, Assembly Verification)  
**Date:** January 2025  
**Status:** Pure core achieved, configuration-driven extensibility in progress

---

## Executive Summary

Achieved **architectural maturity**: the system now has a pure, unchanging core with dynamic vocabulary that grows through configuration, not code changes. Mathematical verification covers room-level geometry down to individual frame member alignment.

```
"3 bedroom house with ensuite" → Valid IFC + Costed BOM (verified at 105 geometric checks)
```

---

## Phases Completed

| Phase | Deliverable | Status |
|-------|-------------|--------|
| 28A | SCHEDULE Registry (D1→900×2100) | ✓ Complete |
| 28B | GRID cumulative positions | ✓ Complete |
| 28C | Wall Rules (ENCLOSED, PERIMETER_ONLY, NONE) | ✓ Complete |
| 28D | ENVELOPE (Foundation, Roof, Drainage correlation) | ✓ Complete |
| 28E | PROFILE/PROTOCOL validation | ✓ Complete |
| 28F | ValidatorFactory (dynamic composition) | ✓ Complete |
| 28G | TB-LKTN TERMINAL conformance | ✓ Complete |
| 29A | LOD400 Door/Window reconnection | ✓ Complete |
| 29B | AssemblyGeometryValidator (105 checks) | ✓ Complete |
| 29C | BIMConstants refactoring (35+ constants) | ✓ Complete |
| 29D | Priority 2 Abstracting | In Progress |

---

## Architecture: Pure Core + Dynamic Vocabulary

### The Constitution

```
PURE CORE (unchanging)
├── Federated DB Schema (TERMINAL-proven)
├── DSL Grammar (SPACE as universal primitive)
├── Compiler Engine (reads configuration)
├── Factory Pattern (routes to library/parametric)
├── Validator Framework (composes dynamically)
└── Blender Pipeline (federation_viz_helper.py)

DYNAMIC VOCABULARY (growing via configuration)
├── Profiles (regional codes: Malaysian, US_IRC, UK_Regs)
├── Protocols (building types: Residential, Commercial)
├── SpaceTypes (room types with rules)
├── Specializations (MASTER_BEDROOM extends BEDROOM)
├── Schedules (door/window/material types)
├── Assembly Templates (WALL_PANEL, DOOR_ASSEMBLY)
└── Validation Rules (per SpaceType)
```

### Key Principle

> The system core is pure and frozen; the vocabulary is dynamic and grows with each new IFC reference.

This follows the **iDempiere pattern**: stable engine, extensible through metadata tables (AD_*).

---

## DSL Dictionary v2.0

### Modern Standards Alignment

| Feature | Inspired By | Purpose |
|---------|-------------|---------|
| **Profile** | HL7 FHIR | Regional/code variants |
| **Protocol** | STEP AP | Building type templates |
| **Specialization** | DITA | Type hierarchy (child extends parent) |
| **LOD Levels** | CityGML/BIM Forum | Detail progression (100→500) |
| **SPACE primitive** | iDempiere Document | Universal typed container |

### DSL Syntax Achieved

```
BUILDING "TB-LKTN" 
    profile: "Malaysian_Residential"
    protocol: "Residential_Single_Storey"
    lod: 300
{
    GRID {
        axes: A, B, C, D, E / 1, 2, 3, 4, 5
        spacing: 1.3, 1.8, 3.7, 3.1 / 2.3, 3.1, 1.5, 1.6
    }
    
    SCHEDULE doors {
        D1: 900x2100 "Metal frame solid timber"
        D2: 750x2100 "Flush door gloss paint"
    }
    
    SCHEDULE windows {
        W1: 1800x1000 "Aluminium 3 panel"
        W3: 600x500 "Aluminium top hung"
    }
    
    ENVELOPE {
        FOUNDATION type:SLAB_ON_GRADE depth:150mm
        ROOF pitch:25deg overhang:600mm type:GABLE
        PERIMETER_DRAIN offset:600mm
    }
    
    STOREY "Ground" level:0 height:2.8m {
        
        OPEN_PLAN "common" bounds:B2-D5 {
            exterior: south
            ZONE "living" position:south
            ZONE "dining" position:center
            ZONE "kitchen" position:north
        }
        
        BEDROOM "bilik_utama" bounds:A2-C3 {
            exterior: west
            opens_to: common
        }
        
        // ... more rooms
    }
}
```

---

## LOD400 Integration

### Component Library Status

```
component_library.db: 8,698 LOD400 definitions

Connected to TB-LKTN:
├── Doors: 112 library components → DOOR_ASSEMBLY
├── Windows: 183 library components → WINDOW_ASSEMBLY  
├── Walls: WALL_PANEL assemblies (FRAME + CLADDING)
└── Hardware: Hinges, handles (BOM-only, optional=true)
```

### Assembly Structure

```
DOOR_ASSEMBLY
├── LEAF: IfcDoor (452 vertices - real LOD400 geometry)
├── HINGE_100MM: 3x (BOM-only, optional=true)
└── HANDLE_SET: 1x (BOM-only, optional=true)

WALL_PANEL
├── FRAME: IfcMember (studs, plates)
└── CLADDING: IfcPlate

vs. Parametric (before reconnection):
├── Door: 8 vertices (simple box)
└── No hardware tracking
```

### Geometry Proof

```
Library door: 452 vertices, 872 faces (real architectural detail)
Parametric box: 8 vertices, 12 faces (placeholder)

LOD400 = fabrication-ready geometry from TERMINAL extraction
```

---

## Validation Framework

### Dynamic Composition

```
ValidatorFactory.create(BuildingDefinition)
    │
    ├── Reads: profile → ProfileValidator
    ├── Reads: protocol → ProtocolValidator  
    ├── Reads: lod → LODValidator
    │
    └── Returns: ValidatorChain (composed at runtime)
            ├── GeometryValidator (always)
            ├── HabitabilityValidator (always)
            ├── AssemblyGeometryValidator (always)
            ├── ProfileValidator (from registry)
            ├── ProtocolValidator (from registry)
            └── LODValidator (from registry)
```

### Verification Counts

| Validator | Checks | Status |
|-----------|--------|--------|
| GeometryValidator | Room connectivity, enclosure, overlaps | ✓ PASS |
| HabitabilityValidator | Light, egress, dimensions | ✓ PASS |
| AssemblyGeometryValidator | 105 piece-level checks | ✓ PASS |
| ProfileValidator | Regional code compliance | ✓ PASS |
| ProtocolValidator | Building type requirements | ✓ PASS |

### Mathematical Proof Chain

```
Layer 0: Geometry
├── Room coordinates from GRID ✓
├── Wall connectivity (0.0mm gaps) ✓
├── Room enclosure ✓
├── No overlaps ✓
└── Roof footprint ✓

Layer 1: Assembly
├── Door containment in wall ✓
├── Window containment in wall ✓
├── Frame member alignment ✓
└── Component positions valid ✓

Layer 2: Validation
├── Natural light (IRC R303.1) ✓
├── Egress requirements ✓
├── Min dimensions per SpaceType ✓
└── Profile/Protocol compliance ✓

Layer 3: Conformance
└── TERMINAL schema match ✓
```

---

## Files Created/Modified (Phase 28-29)

### New Java Files

```
src/main/java/com/bim/compiler/
├── constants/
│   └── BIMConstants.java (35+ extracted constants)
├── validation/
│   ├── ValidatorFactory.java
│   ├── ProfileValidatorRegistry.java
│   ├── ProtocolValidatorRegistry.java
│   ├── LODValidatorRegistry.java
│   └── AssemblyGeometryValidator.java
├── dsl/
│   ├── WallRule.java (enum)
│   ├── GridDef.java (cumulative positions)
│   └── ScheduleDef.java (door/window types)
└── library/
    └── DoorWindowLibraryMatcher.java
```

### Documentation Files

```
docs/
├── bim-dsl-dictionary.md (v2.0 - 2100+ lines)
├── bim-compiler-glossary.md (terminology)
├── lod400-integration-status.md
├── lod400-assemblies-extracted.md
├── lod400-assemblies-residential-research.md
├── refactoring-report-priority1.md
└── lod400-verification-summary.md
```

### Test Files

```
src/main/java/com/bim/compiler/dsl/
├── TBLKTNCompleteTest.java (9/9)
├── TBLKTNProofTest.java (mathematical verification)
├── TBLKTNEndToEndTest.java (DB conformance)
├── ScheduleRegistryTest.java (5/5)
├── WallRulesTest.java (7/7)
├── EnvelopeTest.java (7/7)
├── ProfileProtocolTest.java (10/10)
├── RoofGenerationTest.java (6/6)
└── ValidationIntegrationTest.java (5/5)
```

---

## Ground Truth Methodology

### PRIME RULE

> **EXTRACT, DON'T IMAGINE.**  
> Query the database. Copy patterns you find. Never invent.

### Status Markers

| Marker | Meaning |
|--------|---------|
| ✓ EXTRACTED | From TERMINAL or validated IFC |
| ◐ RESEARCHED | From published standards (AS 1684, IRC, NFPA) |
| ○ PENDING | Needs residential IFC for extraction |

### Patterns Extracted (G1-G12)

All from TERMINAL (51,723 elements):

| Pattern | Value | Status |
|---------|-------|--------|
| Coordinate tolerance | 5mm | ✓ EXTRACTED |
| Wall thicknesses | 150, 230, 250, 300mm only | ✓ EXTRACTED |
| Placement anchor | CENTER | ✓ EXTRACTED |
| MEP ceiling zone | 440-727mm | ✓ EXTRACTED |
| Sprinkler spacing | 4.6m max | ✓ EXTRACTED + NFPA 13 |

---

## Key File Types

| Extension | Purpose | Example |
|-----------|---------|---------|
| `.bim` | DSL source file | `tb-lktn.bim` |
| `.db` | SQLite federated model | `tb_lktn.db` |
| `.java` | Compiler source | `BuildingCompiler.java` |
| `.py` | Blender bake scripts | `federation_viz_helper.py` |
| `.md` | Documentation, vocabulary | `bim-dsl-dictionary.md` |
| `.csv` | BOM for ERP | `tb_lktn_bom.csv` |
| `.yaml` | Configuration (target) | `config/spacetypes.yaml` |

---

## Continuous Hardening Roadmap

### Priority 1: Refactoring ✓ COMPLETE
- BIMConstants.java (35+ constants)
- 6 files refactored
- Tests passing (105/105)

### Priority 2: Abstracting (IN PROGRESS)
- SpaceType as loadable YAML
- Validation rules from configuration
- Assembly templates as configuration
- Profiles as loadable files

### Priority 3: Extending with Research
- Window assembly completion
- Roof assembly research
- Foundation assembly research
- MEP expansion (electrical, plumbing)
- Fastener/hardware expansion

### Priority 4: Hardening Tests
- Edge case tests
- Failure mode tests
- Round-trip tests
- Performance tests
- Regression suite

### Priority 5: Documentation Sync
- Dictionary matches implementation
- Glossary updated
- CHANGELOG maintained

---

## Standing Rules for Development

```
1. PRIME RULE: Extract, don't imagine
   - New constants → query TERMINAL or cite standard
   - New patterns → extract from reference IFC
   
2. Mathematical proof over visual
   - Every geometry change → add verification test
   - No "it looks right" - numbers must match
   
3. Mark uncertainty honestly
   - ✓ EXTRACTED (from data)
   - ◐ RESEARCHED (from standards)
   - ○ PENDING (needs source)
   
4. Vocabulary as data
   - Prefer configuration over code
   - New type = dictionary entry, not Java change
   
5. TERMINAL conformance
   - Output must match proven schema
   - Same queries work on TERMINAL and generated DB
```

---

## Session Continuity

### When Starting New Thread

Reference these documents in order:

1. `bim-compiler-project-knowledge.md` (Phases 0-8, foundation)
2. `bim-compiler-progress-update.md` (Phases 8-11, shed POC)
3. `bim-compiler-progress-update-phases-12-21.md` (Phases 12-21, intent compiler)
4. `bim-compiler-progress-update-phases-28-29.md` (this document)
5. `bim-dsl-dictionary.md` (Building Vocabulary v2.0)
6. `bim-compiler-glossary.md` (terminology)

### Current State Summary

```
Phase: 29 (Assembly Verification complete)
Next: Priority 2 Abstracting (configuration-driven extensibility)

Tests passing:
- TB-LKTN: 9/9
- Assembly: 105/105
- Total validators: 5 integrated

Architecture:
- Pure core ✓
- Dynamic vocabulary ✓
- LOD400 connected ✓
- Mathematical proof chain ✓
- TERMINAL conformance ✓
```

### Resume Prompt

```
Continue BIM Compiler project. Phase 29 complete.

Current state:
- Pure core + dynamic vocabulary architecture achieved
- LOD400 doors/windows reconnected (452 vertices vs 8)
- AssemblyGeometryValidator: 105/105 checks passing
- BIMConstants refactored (35+ constants)
- ValidatorFactory composes chain dynamically

Next task: Priority 2 Abstracting
- SpaceType as loadable YAML
- Validation rules from configuration
- Assembly templates as configuration
- Profiles as loadable files

Reference: bim-compiler-progress-update-phases-28-29.md
Standing rules: PRIME RULE, mathematical proof, vocabulary as data
```

---

## Acknowledgments

This phase achieved architectural maturity through disciplined application of:

- **Ground Truth Methodology** - Extract, don't imagine
- **iDempiere patterns** - Document/DocType → SPACE/SpaceType
- **Modern standards** - FHIR Profiles, DITA Specialization, CityGML LOD, STEP AP
- **Mathematical verification** - 105 geometric checks, not visual inspection

The system now embodies: **Pure core, dynamic vocabulary, proven pipeline.**

---

*Progress Update v3.0 - January 2025*
*Phases 28-29: Dictionary v2.0, LOD400 Reconnection, Assembly Verification*
