# BIM COMPILER ARCHITECTURE EVOLUTION

> **SUPERSEDED** by `docs/ARCHITECTURE.md` (v3.0, 2026-02-08).
> The L0-L6 correctness framework survives in Section 5 of the new document.
> The factory pattern proposal (Part 8) is replaced by the AD/BOM pattern.
> Retained for historical context.

## Beyond Geometry: A Theory of Everything for Construction

**Version:** 2.0
**Date:** January 2025
**Status:** ~~Strategic Architecture Document~~ Historical (superseded)

---

## Executive Summary

The BIM Intent Compiler has achieved **geometric correctness** (10/10 witness claims). The next frontier is **construction correctness** - proving not just that elements exist and attach, but that they form valid systems that can be built, inspected, and operated.

This document defines the abstraction layers needed to evolve from:
```
"Generate valid IFC geometry" → "Generate constructible, permit-ready, operable buildings"
```

---

## Part 1: The Construction Hierarchy

### 1.1 The Seven Levels of BIM Correctness

| Level | Name | Question Answered | Current Status |
|-------|------|-------------------|----------------|
| L0 | **Geometric** | Do elements have valid shapes? | ✅ Proven |
| L1 | **Spatial** | Are elements in correct locations? | ✅ Proven |
| L2 | **Topological** | Do elements connect correctly? | ⚠️ Partial |
| L3 | **Systemic** | Do systems function as wholes? | ❌ Not yet |
| L4 | **Constructible** | Can this be built in sequence? | ❌ Not yet |
| L5 | **Compliant** | Does it pass inspection? | ⚠️ Partial |
| L6 | **Operable** | Can it be maintained/operated? | ❌ Not yet |

### 1.2 Current Coverage Map

```
LEVEL 0: GEOMETRIC ████████████████████ 100%
  - Vertices, faces, bounding boxes
  - Manifold solids
  - Dimension validation

LEVEL 1: SPATIAL █████████████████████ 100%  
  - Room containment (ELECTRICAL_IN_SPACES)
  - Host attachment (FIXTURES_ATTACHED_TO_HOSTS)
  - Envelope containment (ROOMS_IN_ENVELOPE)

LEVEL 2: TOPOLOGICAL ████████░░░░░░░░░░ 40%
  - Door connectivity (ALL_ROOMS_REACHABLE) ✅
  - Wall enclosure (ROOMS_ENCLOSED) ✅
  - MEP connectivity ❌
  - Structural load paths ❌

LEVEL 3: SYSTEMIC ██░░░░░░░░░░░░░░░░░░ 10%
  - Plumbing stack concept ✅
  - Electrical circuits ❌
  - HVAC zones ❌
  - Fire compartments ❌

LEVEL 4: CONSTRUCTIBLE ░░░░░░░░░░░░░░░░░░░░ 0%
  - Construction sequence ❌
  - Trade coordination ❌
  - Access/clearance ❌

LEVEL 5: COMPLIANT ████████░░░░░░░░░░░░ 40%
  - Room dimensions (IRC) ✅
  - Window requirements ✅
  - MEP code compliance ⚠️ Partial
  - Accessibility ❌

LEVEL 6: OPERABLE ░░░░░░░░░░░░░░░░░░░░ 0%
  - Maintenance access ❌
  - Asset tagging ❌
  - Lifecycle data ❌
```

---

## Part 2: Core Abstraction Framework

### 2.1 The Universal Element Model

Every BIM element, regardless of discipline, shares common concerns:

```
┌─────────────────────────────────────────────────────────────────┐
│                      UNIVERSAL ELEMENT                          │
├─────────────────────────────────────────────────────────────────┤
│  IDENTITY                                                       │
│  ├─ guid: UUID                                                  │
│  ├─ ifcClass: IFC4 type                                        │
│  ├─ name: Human-readable                                        │
│  └─ classification: OmniClass/UniFormat                         │
├─────────────────────────────────────────────────────────────────┤
│  GEOMETRY                                                       │
│  ├─ strategy: LIBRARY | PARAMETRIC                              │
│  ├─ geometryHash: Reference to base_geometries                  │
│  ├─ transform: Position, rotation, scale                        │
│  └─ lod: 100 | 200 | 300 | 350 | 400 | 500                     │
├─────────────────────────────────────────────────────────────────┤
│  SPATIAL                                                        │
│  ├─ containedIn: Space reference                                │
│  ├─ attachedTo: Host element reference                          │
│  ├─ boundedBy: Bounding surfaces                                │
│  └─ zone: Functional zone membership                            │
├─────────────────────────────────────────────────────────────────┤
│  SYSTEMIC                                                       │
│  ├─ system: MEPSystem reference                                 │
│  ├─ upstream: Elements feeding this                             │
│  ├─ downstream: Elements fed by this                            │
│  └─ role: SOURCE | DISTRIBUTION | TERMINAL | CONNECTOR          │
├─────────────────────────────────────────────────────────────────┤
│  CONSTRUCTION                                                   │
│  ├─ sequence: Construction phase                                │
│  ├─ trade: Responsible trade                                    │
│  ├─ predecessors: Must be installed first                       │
│  ├─ clearance: Access requirements                              │
│  └─ tolerance: Installation tolerance                           │
├─────────────────────────────────────────────────────────────────┤
│  COMPLIANCE                                                     │
│  ├─ codeRefs: Applicable code sections                          │
│  ├─ inspectionPoints: Required inspections                      │
│  ├─ certifications: Required certs (electrical, gas)            │
│  └─ fireRating: If applicable                                   │
├─────────────────────────────────────────────────────────────────┤
│  LIFECYCLE                                                      │
│  ├─ manufacturer: Product source                                │
│  ├─ model: Specific product                                     │
│  ├─ warranty: Warranty period                                   │
│  ├─ maintenance: Maintenance schedule                           │
│  └─ replacement: Expected lifespan                              │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Abstract Factory Hierarchy

```
                        ┌─────────────────────┐
                        │  ElementFactory     │
                        │  (abstract)         │
                        └──────────┬──────────┘
                                   │
        ┌──────────────────────────┼──────────────────────────┐
        │                          │                          │
        ▼                          ▼                          ▼
┌───────────────┐        ┌─────────────────┐        ┌────────────────┐
│StructuralFactory│      │ArchitecturalFactory│    │ MEPFactory     │
└───────┬───────┘        └────────┬────────┘        └───────┬────────┘
        │                         │                          │
   ┌────┴────┐              ┌─────┴─────┐            ┌───────┴───────┐
   │         │              │           │            │       │       │
   ▼         ▼              ▼           ▼            ▼       ▼       ▼
Column    Beam           Wall        Opening     Electrical Plumbing HVAC
Slab      Foundation     Roof        Stair       Fire       
```

### 2.3 The System Graph Model

MEP and structural systems are **graphs**, not just collections:

```java
/**
 * Abstract system that all building systems extend.
 * Systems are directed graphs with typed nodes and edges.
 */
abstract class BuildingSystem {
    String systemId;
    SystemType type;
    
    // Graph structure
    List<SystemNode> nodes;
    List<SystemEdge> edges;
    
    // Validation
    abstract boolean isComplete();      // All required nodes present
    abstract boolean isConnected();     // Graph connectivity
    abstract boolean isBalanced();      // Flow/load balance (where applicable)
    abstract List<String> validate();   // Code compliance
    
    // Queries
    SystemNode getRoot();               // Source node
    List<SystemNode> getTerminals();    // End nodes
    List<SystemNode> getPath(SystemNode from, SystemNode to);
}

/**
 * Node in a building system graph
 */
class SystemNode {
    String elementId;           // References UniversalElement
    NodeRole role;              // SOURCE, DISTRIBUTION, TERMINAL, CONNECTOR
    Map<String, Object> properties;  // System-specific properties
    
    // Connections
    List<SystemEdge> incoming;
    List<SystemEdge> outgoing;
}

/**
 * Edge connecting nodes in a system
 */
class SystemEdge {
    String edgeId;
    SystemNode from;
    SystemNode to;
    EdgeType type;              // FEEDS, RETURNS, VENTS, DRAINS, SUPPORTS
    Map<String, Object> properties;  // Flow rate, cable size, pipe diameter
}
```

---

## Part 3: System-Specific Models

### 3.1 Electrical System Model

```
ELECTRICAL SYSTEM
├─ SOURCE: Distribution Board (DB)
│   ├─ Properties: phases, main_breaker_amps, location
│   └─ Compliance: MS IEC 60364 / NEC
│
├─ DISTRIBUTION: Circuits
│   ├─ Properties: circuit_number, breaker_amps, cable_size, cable_type
│   ├─ Constraints: max_points_per_circuit, voltage_drop_max
│   └─ EdgeType: FEEDS
│
└─ TERMINALS: Points
    ├─ IfcLightFixture: watts, lumens, control_group
    ├─ IfcOutlet: socket_type, amps, dedicated
    ├─ IfcSwitchingDevice: switch_type, controls[], dimmer
    └─ IfcElectricAppliance: load_amps, dedicated_circuit

WITNESS CLAIMS:
├─ ELECTRICAL_CIRCUITS_COMPLETE
│   └─ Every terminal has path to DB
├─ ELECTRICAL_LOAD_BALANCED  
│   └─ No circuit exceeds breaker rating
├─ ELECTRICAL_CABLE_SIZED
│   └─ Cable AWG appropriate for load + distance
└─ ELECTRICAL_GROUNDING_COMPLETE
    └─ All circuits have ground path
```

### 3.2 Plumbing System Model

```
PLUMBING SYSTEM
├─ SOURCE: Water Main / Septic
│   ├─ Water: pressure_psi, meter_location
│   └─ Waste: MH_location, septic_capacity
│
├─ DISTRIBUTION: Pipes
│   ├─ Supply: hot/cold, diameter, material, insulation
│   ├─ Waste: diameter, slope, vented
│   ├─ Vent: diameter, terminates_above_roof
│   └─ EdgeType: FEEDS (supply), DRAINS_TO (waste), VENTS_TO (vent)
│
└─ TERMINALS: Fixtures
    ├─ IfcSanitaryTerminal: fixture_type, trap_size, water_demand
    ├─ IfcFlowTerminal: gpm, connection_sizes
    └─ IfcWasteTerminal: drain_size

WITNESS CLAIMS:
├─ PLUMBING_SUPPLY_COMPLETE
│   └─ Every fixture has path to water main
├─ PLUMBING_WASTE_COMPLETE
│   └─ Every fixture drains to septic/sewer
├─ PLUMBING_VENTED
│   └─ Every trap has vent path to atmosphere
├─ PLUMBING_SLOPE_VALID
│   └─ Waste pipes slope ≥ 1% toward drain
└─ PLUMBING_STACK_ALIGNED
    └─ Vertical stacks are plumb across storeys
```

### 3.3 HVAC System Model

```
HVAC SYSTEM
├─ SOURCE: AHU / Condensing Unit / Fan
│   ├─ Properties: capacity_kw, cfm, refrigerant
│   └─ Location: plant_room, outdoor
│
├─ DISTRIBUTION: Ducts / Pipes
│   ├─ Supply: size, insulation, cfm
│   ├─ Return: size, cfm
│   ├─ Refrigerant: diameter, type
│   └─ EdgeType: SUPPLIES, RETURNS
│
├─ ZONES: Thermal Zones
│   ├─ Properties: setpoint, humidity, occupancy
│   ├─ Spaces: List<Space>
│   └─ Control: thermostat_location
│
└─ TERMINALS: Diffusers / FCUs
    ├─ IfcAirTerminal: cfm, throw, type
    └─ IfcUnitaryEquipment: capacity, control

WITNESS CLAIMS:
├─ HVAC_ZONES_COVERED
│   └─ Every habitable space in a thermal zone
├─ HVAC_SUPPLY_BALANCED
│   └─ Supply CFM = Return CFM per zone
├─ HVAC_CAPACITY_ADEQUATE
│   └─ Equipment sized for load calculation
└─ HVAC_DUCT_SIZED
    └─ Duct velocity within limits
```

### 3.4 Structural System Model

```
STRUCTURAL SYSTEM
├─ FOUNDATION
│   ├─ Type: SLAB, STRIP, PAD, PILE
│   ├─ Properties: depth, reinforcement, bearing_capacity
│   └─ Loads: DEAD, LIVE, transferred from above
│
├─ VERTICAL: Columns / Walls
│   ├─ Properties: material, section, height
│   ├─ Loads: axial, moment, shear
│   └─ EdgeType: SUPPORTS
│
├─ HORIZONTAL: Beams / Slabs
│   ├─ Properties: material, section, span
│   ├─ Loads: distributed, point
│   └─ EdgeType: SPANS_TO
│
└─ LATERAL: Bracing / Shear Walls
    ├─ Properties: type, capacity
    └─ Purpose: wind, seismic

WITNESS CLAIMS:
├─ STRUCTURAL_LOAD_PATH_COMPLETE
│   └─ Every load has path to foundation
├─ STRUCTURAL_MEMBERS_SIZED
│   └─ Member capacity > applied load
├─ STRUCTURAL_CONNECTIONS_VALID
│   └─ Connection capacity adequate
└─ STRUCTURAL_LATERAL_COMPLETE
    └─ Lateral system in both directions
```

### 3.5 Fire Protection System Model

```
FIRE PROTECTION SYSTEM
├─ COMPARTMENTS
│   ├─ Properties: area, occupancy, rating_required
│   ├─ Boundaries: fire_rated_walls, fire_doors
│   └─ Compliance: max_compartment_area
│
├─ DETECTION
│   ├─ IfcAlarm: smoke_detector, heat_detector
│   ├─ Coverage: max_spacing, ceiling_height_adjustment
│   └─ Zones: detection_zones, panel_connection
│
├─ SUPPRESSION
│   ├─ IfcFireSuppressionTerminal: sprinkler_type, k_factor
│   ├─ Coverage: max_spacing (4.6m NFPA 13)
│   ├─ Supply: water_main, pump, tank
│   └─ Hydraulic: pressure, flow_rate
│
└─ EGRESS
    ├─ Routes: exit_paths, travel_distance
    ├─ Exits: exit_doors, exit_stairs
    ├─ Signage: exit_signs, emergency_lights
    └─ Compliance: max_travel_distance, exit_width

WITNESS CLAIMS:
├─ FIRE_COMPARTMENTS_COMPLETE
│   └─ All boundaries have required rating
├─ FIRE_DETECTION_COVERAGE
│   └─ Detectors within max spacing
├─ FIRE_SPRINKLER_COVERAGE
│   └─ Sprinklers cover all required areas
├─ FIRE_EGRESS_COMPLIANT
│   └─ Travel distance within limits
└─ FIRE_EXITS_ADEQUATE
    └─ Exit capacity for occupant load
```

---

## Part 4: Construction Sequencing Model

### 4.1 The Construction Graph

Buildings are not just assembled—they're **constructed in sequence**:

```java
/**
 * Construction phase with dependencies
 */
class ConstructionPhase {
    String phaseId;
    String name;
    Trade responsibleTrade;
    
    List<UniversalElement> elements;     // Elements installed in this phase
    List<ConstructionPhase> predecessors; // Must complete before this
    List<ConstructionPhase> successors;   // Can start after this
    
    Duration estimatedDuration;
    List<Inspection> requiredInspections;
}

/**
 * Trade responsible for work
 */
enum Trade {
    CIVIL,          // Earthworks, foundations
    STRUCTURAL,     // Frame, slabs
    MASONRY,        // Walls
    ROOFING,        // Roof structure and cover
    PLUMBING_ROUGH, // Pipes before close-in
    ELECTRICAL_ROUGH,
    HVAC_ROUGH,
    INSULATION,
    DRYWALL,
    PLUMBING_FINISH, // Fixtures after finishes
    ELECTRICAL_FINISH,
    HVAC_FINISH,
    FINISHES,       // Paint, flooring, trim
    LANDSCAPING
}

/**
 * Inspection checkpoint
 */
class Inspection {
    String inspectionId;
    InspectionType type;
    Trade trade;
    List<String> codeReferences;
    List<UniversalElement> elementsInspected;
    
    enum InspectionType {
        FOUNDATION,
        FRAMING,
        ROUGH_PLUMBING,
        ROUGH_ELECTRICAL,
        INSULATION,
        FINAL_PLUMBING,
        FINAL_ELECTRICAL,
        FINAL_BUILDING,
        CERTIFICATE_OF_OCCUPANCY
    }
}
```

### 4.2 Standard Residential Sequence

```
CONSTRUCTION SEQUENCE (Single-Storey Residential)
═══════════════════════════════════════════════════════════════

PHASE 1: SITE & FOUNDATION
├─ Elements: site_clearing, excavation, foundation_slab
├─ Trade: CIVIL
├─ Inspection: FOUNDATION
└─ Duration: 2 weeks

PHASE 2: STRUCTURE
├─ Elements: columns, beams, roof_structure
├─ Trade: STRUCTURAL
├─ Predecessors: [PHASE_1]
├─ Inspection: FRAMING
└─ Duration: 2 weeks

PHASE 3: ENVELOPE
├─ Elements: external_walls, windows, doors, roofing
├─ Trade: MASONRY, ROOFING
├─ Predecessors: [PHASE_2]
└─ Duration: 3 weeks

PHASE 4: ROUGH-IN (Parallel)
├─ PHASE_4A: PLUMBING_ROUGH
│   ├─ Elements: waste_pipes, supply_pipes, vent_pipes
│   ├─ Trade: PLUMBING_ROUGH
│   └─ Inspection: ROUGH_PLUMBING
├─ PHASE_4B: ELECTRICAL_ROUGH
│   ├─ Elements: conduits, cables, boxes
│   ├─ Trade: ELECTRICAL_ROUGH
│   └─ Inspection: ROUGH_ELECTRICAL
└─ Predecessors: [PHASE_3]

PHASE 5: CLOSE-IN
├─ Elements: internal_walls, insulation, ceiling_frames
├─ Trade: MASONRY, INSULATION
├─ Predecessors: [PHASE_4A, PHASE_4B]
├─ Inspection: INSULATION
└─ Duration: 2 weeks

PHASE 6: FINISHES
├─ Elements: wall_finishes, floor_finishes, ceiling_finishes
├─ Trade: FINISHES
├─ Predecessors: [PHASE_5]
└─ Duration: 3 weeks

PHASE 7: FIT-OUT (Parallel)
├─ PHASE_7A: PLUMBING_FINISH
│   ├─ Elements: toilets, sinks, taps, water_heater
│   ├─ Trade: PLUMBING_FINISH
│   └─ Inspection: FINAL_PLUMBING
├─ PHASE_7B: ELECTRICAL_FINISH
│   ├─ Elements: lights, outlets, switches, DB
│   ├─ Trade: ELECTRICAL_FINISH
│   └─ Inspection: FINAL_ELECTRICAL
└─ Predecessors: [PHASE_6]

PHASE 8: COMPLETION
├─ Elements: external_works, landscaping
├─ Trade: LANDSCAPING
├─ Predecessors: [PHASE_7A, PHASE_7B]
├─ Inspection: FINAL_BUILDING, CERTIFICATE_OF_OCCUPANCY
└─ Duration: 2 weeks
```

### 4.3 Witness Claims for Constructibility

```json
"CONSTRUCTION_SEQUENCE_VALID": {
  "status": "PROVEN",
  "witness": {
    "phases": 8,
    "critical_path_weeks": 14,
    "parallel_phases": ["4A/4B", "7A/7B"],
    "no_circular_dependencies": true,
    "all_elements_assigned": true,
    "inspections_scheduled": 7
  }
}

"TRADE_COORDINATION_CLEAR": {
  "status": "PROVEN",
  "witness": {
    "no_conflicts": true,
    "MEP_clearances_verified": true,
    "access_paths_available": true
  }
}

"INSPECTION_POINTS_IDENTIFIED": {
  "status": "PROVEN",
  "witness": {
    "inspections": [
      {"type": "FOUNDATION", "phase": 1, "elements": 3},
      {"type": "ROUGH_PLUMBING", "phase": "4A", "elements": 4},
      {"type": "ROUGH_ELECTRICAL", "phase": "4B", "elements": 29}
    ]
  }
}
```

---

## Part 5: Compliance Framework

### 5.1 Multi-Jurisdiction Code Model

```java
/**
 * Building code abstraction - supports multiple jurisdictions
 */
abstract class BuildingCode {
    String codeId;          // "IRC_2021", "UBBL_1984", "IBC_2021"
    String jurisdiction;    // "US", "MY", "UK"
    String version;
    
    abstract List<CodeCheck> getChecksFor(UniversalElement element);
    abstract List<CodeCheck> getChecksFor(BuildingSystem system);
    abstract List<CodeCheck> getChecksFor(Building building);
}

/**
 * Individual code requirement
 */
class CodeCheck {
    String checkId;
    String codeSection;     // "IRC R304.1", "UBBL 33(1)"
    String description;
    Severity severity;      // MANDATORY, ADVISORY
    
    Predicate<Object> check; // The actual validation logic
    String failureMessage;
    String guidanceUrl;
}

/**
 * Code check result
 */
class CodeCheckResult {
    CodeCheck check;
    boolean passed;
    String actualValue;
    String requiredValue;
    List<String> affectedElements;
}
```

### 5.2 Profile-Based Code Selection

```yaml
# profiles/malaysian_residential.yaml
profile:
  name: Malaysian_Residential
  extends: BASE
  
  codes:
    building: UBBL_1984
    electrical: MS_IEC_60364
    plumbing: MS_1228
    fire: BOMBA_Guidelines
  
  overrides:
    min_storey_height: 2.8m    # vs IRC 2.4m
    min_bedroom_area: 9.3m²    # vs IRC 6.5m²
    roof_overhang: 600mm       # Tropical climate
    
  inspections:
    - CCC_Application          # Certificate of Completion and Compliance
    - Bomba_Inspection         # Fire department
    - TNB_Inspection           # Electrical utility
    - IWK_Inspection           # Sewerage
```

### 5.3 Compliance Witness Claims

```json
"CODE_COMPLIANCE_BUILDING": {
  "status": "PROVEN",
  "witness": {
    "code": "UBBL_1984",
    "checks_run": 47,
    "passed": 47,
    "failed": 0,
    "sections_verified": [
      "33(1) - Minimum room dimensions",
      "34 - Natural lighting",
      "39 - Ventilation",
      "44 - Sanitary facilities"
    ]
  }
}

"CODE_COMPLIANCE_ELECTRICAL": {
  "status": "PROVEN",
  "witness": {
    "code": "MS_IEC_60364",
    "checks_run": 23,
    "passed": 23,
    "circuit_analysis": "included"
  }
}

"CODE_COMPLIANCE_FIRE": {
  "status": "PROVEN",
  "witness": {
    "code": "BOMBA_Guidelines",
    "compartment_check": "PASS",
    "egress_check": "PASS",
    "detection_check": "PASS"
  }
}
```

---

## Part 6: Lifecycle & Operations Model

### 6.1 Asset Information Model

```java
/**
 * Lifecycle data attached to elements
 */
class AssetInfo {
    String assetId;           // Facility management tag
    String elementId;         // Links to UniversalElement
    
    // Procurement
    String manufacturer;
    String modelNumber;
    String supplier;
    Money unitCost;
    LocalDate procurementDate;
    
    // Installation
    LocalDate installationDate;
    String installedBy;
    String serialNumber;
    
    // Warranty
    Period warrantyPeriod;
    LocalDate warrantyExpiry;
    String warrantyTerms;
    
    // Maintenance
    MaintenanceSchedule schedule;
    List<MaintenanceRecord> history;
    
    // Replacement
    Period expectedLifespan;
    LocalDate projectedReplacement;
    Money replacementCost;
}

/**
 * Maintenance schedule
 */
class MaintenanceSchedule {
    String scheduleId;
    Period frequency;         // Monthly, quarterly, annually
    List<MaintenanceTask> tasks;
    Trade responsibleTrade;
}

/**
 * COBie export support
 */
class COBieExporter {
    void exportFacility(Building building);
    void exportFloors(List<Storey> storeys);
    void exportSpaces(List<Space> spaces);
    void exportTypes(List<ComponentType> types);
    void exportComponents(List<UniversalElement> elements);
    void exportSystems(List<BuildingSystem> systems);
    void exportJobs(List<MaintenanceSchedule> schedules);
}
```

### 6.2 Handover Package Model

```
BUILDING HANDOVER PACKAGE
═══════════════════════════════════════════════════════════════

LEGAL DOCUMENTS
├─ Certificate of Completion and Compliance (CCC)
├─ Certificate of Fitness (CF) - if applicable
├─ Insurance certificates
└─ Warranty documents

AS-BUILT DOCUMENTATION
├─ IFC model (LOD 500 - as-built)
├─ PDF drawings
├─ Specifications
└─ Deviation reports

SYSTEM DOCUMENTATION
├─ Electrical
│   ├─ Single-line diagram
│   ├─ Circuit schedule
│   ├─ Test certificates
│   └─ Equipment manuals
├─ Plumbing
│   ├─ Drainage layout
│   ├─ Water reticulation
│   ├─ Test certificates
│   └─ Equipment manuals
└─ HVAC (if applicable)
    ├─ Duct layout
    ├─ Commissioning report
    └─ Equipment manuals

ASSET REGISTER
├─ COBie spreadsheet
├─ Maintenance schedules
├─ Spare parts list
└─ Emergency contacts

OPERATIONS MANUAL
├─ Building description
├─ Operating procedures
├─ Emergency procedures
└─ Contact directory
```

---

## Part 7: Implementation Roadmap

### 7.1 Phased Implementation

```
CURRENT STATE (Phase 34)
├─ Geometric correctness ✅
├─ Spatial correctness ✅
├─ Basic MEP elements ✅
└─ 10/10 witness claims ✅

PHASE 35-40: SYSTEMIC COMPLETENESS
├─ Phase 35: MEPSystem graph model
├─ Phase 36: Electrical system connectivity
├─ Phase 37: Plumbing system connectivity  
├─ Phase 38: System witness claims
├─ Phase 39: Cross-system coordination
└─ Phase 40: System documentation export

PHASE 41-45: CONSTRUCTIBILITY
├─ Phase 41: Construction phase model
├─ Phase 42: Trade assignment
├─ Phase 43: Sequence validation
├─ Phase 44: Inspection point identification
└─ Phase 45: Construction schedule export

PHASE 46-50: COMPLIANCE
├─ Phase 46: Code abstraction framework
├─ Phase 47: IRC/UBBL implementation
├─ Phase 48: Electrical code checks
├─ Phase 49: Fire code checks
└─ Phase 50: Compliance report generation

PHASE 51-55: LIFECYCLE
├─ Phase 51: Asset information model
├─ Phase 52: Maintenance schedule generation
├─ Phase 53: COBie export
├─ Phase 54: Handover package assembly
└─ Phase 55: Operations manual template
```

### 7.2 Priority Matrix

| Capability | Business Value | Technical Complexity | Priority |
|------------|---------------|---------------------|----------|
| MEP System Connectivity | HIGH | MEDIUM | **P1** |
| Construction Sequencing | HIGH | MEDIUM | **P1** |
| Multi-Code Compliance | HIGH | HIGH | **P2** |
| BOM with Pricing | HIGH | LOW | **P1** |
| COBie Export | MEDIUM | LOW | **P2** |
| Maintenance Schedules | MEDIUM | LOW | **P3** |
| 4D Scheduling | MEDIUM | HIGH | **P3** |

### 7.3 Quick Wins (Immediate Value)

1. **MEPSystem graph** - Enables circuit/pipe connectivity proofs
2. **Inspection checklist export** - Practical value for permit process
3. **Enhanced BOM** - Add manufacturer, model, unit cost columns
4. **Code section references** - Annotate witness claims with code sections

---

## Part 8: Factory Pattern Refactoring

### 8.1 Current vs Target Architecture

```
CURRENT (Scattered)
══════════════════════════════════════════════════════════════

BuildingCompiler
├─ Direct calls to ElectricalPlacer
├─ Direct calls to PlumbingPlacer
├─ Direct calls to FixturePlacer
└─ Inline geometry decisions

BuildingWriter
├─ writeLight() - library aware
├─ writePipeSegment() - inline cylinder
├─ writeOutlet() - parametric
└─ Mixed concerns


TARGET (Unified Factory)
══════════════════════════════════════════════════════════════

                    ┌─────────────────────┐
                    │  BuildingCompiler   │
                    │  (orchestrator)     │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │  ElementFactory     │
                    │  (abstract)         │
                    └──────────┬──────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
        ▼                      ▼                      ▼
┌───────────────┐    ┌─────────────────┐    ┌────────────────┐
│StructuralFactory│  │ ArchFactory     │    │ MEPFactory     │
│ getFoundation()│   │ getWall()       │    │ getElectrical()│
│ getColumn()    │   │ getRoof()       │    │ getPlumbing()  │
│ getBeam()      │   │ getOpening()    │    │ getHVAC()      │
└───────────────┘    └─────────────────┘    └────────────────┘
        │                      │                      │
        ▼                      ▼                      ▼
┌───────────────┐    ┌─────────────────┐    ┌────────────────┐
│GeometryStrategy│   │ GeometryStrategy│    │GeometryStrategy│
│ LIBRARY or     │   │ LIBRARY or      │    │ LIBRARY or     │
│ PARAMETRIC     │   │ PARAMETRIC      │    │ PARAMETRIC     │
└───────────────┘    └─────────────────┘    └────────────────┘
        │                      │                      │
        └──────────────────────┼──────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │   ElementWriter     │
                    │   (unified)         │
                    └─────────────────────┘
```

### 8.2 Interface Definitions

```java
/**
 * Root factory interface
 */
interface ElementFactory {
    UniversalElement create(ElementSpec spec, BuildingContext context);
    boolean canCreate(ElementSpec spec);
    GeometryStrategy getGeometryStrategy(ElementSpec spec);
}

/**
 * Geometry generation strategy
 */
interface GeometryStrategy {
    GeometryResult generate(ElementSpec spec, BuildingContext context);
    
    static GeometryStrategy library(ComponentLibrary lib) {
        return new LibraryGeometryStrategy(lib);
    }
    
    static GeometryStrategy parametric(Builder builder) {
        return new ParametricGeometryStrategy(builder);
    }
}

/**
 * MEP-specific factory
 */
interface MEPFactory extends ElementFactory {
    MEPSystem createSystem(SystemSpec spec, BuildingContext context);
    List<SystemNode> placeTerminals(Space space, MEPConfig config);
    List<SystemEdge> routeConnections(List<SystemNode> nodes);
}

/**
 * Host resolution for attachments
 */
interface HostResolver {
    Optional<HostSurface> findHost(ElementSpec element, Space space);
    AttachmentPoint calculateAttachment(ElementSpec element, HostSurface host);
}

/**
 * Witness claim generation
 */
interface WitnessClaim {
    String getClaimId();
    ClaimResult prove(BuildingSpec building, BuildingContext context);
}

/**
 * Witness registry for extensibility
 */
class WitnessRegistry {
    private List<WitnessClaim> claims = new ArrayList<>();
    
    void register(WitnessClaim claim) { claims.add(claim); }
    
    WitnessReport proveAll(BuildingSpec building, BuildingContext context) {
        return claims.stream()
            .map(c -> c.prove(building, context))
            .collect(toWitnessReport());
    }
}
```

---

## Part 9: Data Model Summary

### 9.1 Entity Relationship Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           BUILDING                                       │
│  ├─ profile: Profile                                                    │
│  ├─ protocol: Protocol                                                  │
│  └─ lod: LODLevel                                                       │
└────────────────────────────────────────┬────────────────────────────────┘
                                         │ 1:N
                    ┌────────────────────┴────────────────────┐
                    │                                          │
                    ▼                                          ▼
           ┌───────────────┐                          ┌───────────────┐
           │    STOREY     │                          │    SYSTEM     │
           │  ├─ level     │                          │  ├─ type      │
           │  └─ height    │                          │  ├─ nodes[]   │
           └───────┬───────┘                          │  └─ edges[]   │
                   │ 1:N                              └───────────────┘
                   ▼                                          ▲
           ┌───────────────┐                                  │
           │    SPACE      │                                  │
           │  ├─ spaceType │                                  │
           │  ├─ bounds    │                                  │
           │  └─ zones[]   │                                  │
           └───────┬───────┘                                  │
                   │ 1:N                                      │
                   ▼                                          │
           ┌───────────────┐          ┌───────────────┐      │
           │   ELEMENT     │──────────│  SYSTEM_NODE  │──────┘
           │  ├─ identity  │   N:1    │  ├─ role      │
           │  ├─ geometry  │          │  └─ edges[]   │
           │  ├─ spatial   │          └───────────────┘
           │  ├─ systemic  │
           │  ├─ construct │
           │  ├─ comply    │
           │  └─ lifecycle │
           └───────────────┘
```

### 9.2 Key Relationships

| Relationship | Type | Description |
|--------------|------|-------------|
| Building → Storey | 1:N | Building contains storeys |
| Storey → Space | 1:N | Storey contains spaces |
| Space → Element | 1:N | Space contains elements |
| Element → System | N:1 | Element belongs to system |
| System → SystemNode | 1:N | System has nodes |
| SystemNode → Element | 1:1 | Node references element |
| SystemNode → SystemEdge | 1:N | Node has connections |
| Element → Host | N:1 | Element attaches to host |
| Element → Phase | N:1 | Element built in phase |
| Phase → Inspection | 1:N | Phase requires inspections |

---

## Part 10: Success Metrics

### 10.1 Completeness Metrics

| Metric | Current | Target | Measurement |
|--------|---------|--------|-------------|
| Witness Claims | 10 | 25+ | Automated count |
| System Types | 2 (partial) | 5 | Electrical, Plumbing, HVAC, Fire, Structural |
| Code Coverage | 40% | 90% | Checks implemented / checks required |
| LOD Levels | 300-400 | 100-500 | Full spectrum support |
| Export Formats | 2 (IFC, CSV) | 5 | +COBie, PDF, 4D |

### 10.2 Quality Metrics

| Metric | Current | Target | Measurement |
|--------|---------|--------|-------------|
| Outlier Rate | 1.2% | <1% | Outliers / total elements |
| Library Usage | 60% | 80% | Library / (Library + Parametric) |
| Test Coverage | ~70% | 90% | Lines covered |
| Witness Pass Rate | 100% | 100% | Must maintain |

### 10.3 Business Metrics

| Metric | Current | Target | Measurement |
|--------|---------|--------|-------------|
| Time to IFC | Seconds | Seconds | Maintain performance |
| Permit Readiness | Partial | Complete | Checklist completion |
| BOM Accuracy | Good | Verified | Comparison to manual takeoff |
| User Adoption | Internal | External | Number of projects |

---

## Appendix A: Glossary Additions

| Term | Definition |
|------|------------|
| **System Graph** | Directed graph representing connected building system |
| **SystemNode** | Element participating in a system with role and connections |
| **SystemEdge** | Connection between nodes with type and properties |
| **Construction Phase** | Sequenced work package with trade and inspection |
| **Inspection Point** | Required verification before proceeding |
| **Code Check** | Individual compliance requirement with pass/fail |
| **Asset Info** | Lifecycle data attached to element |
| **COBie** | Construction Operations Building information exchange |
| **Handover Package** | Complete documentation set for building delivery |

---

## Appendix B: References

| Standard | Purpose | URL |
|----------|---------|-----|
| IFC4 | BIM data model | buildingsmart.org |
| COBie | Facility handover | nibs.org |
| OmniClass | Classification | csiresources.org |
| NFPA 13 | Sprinkler systems | nfpa.org |
| IRC 2021 | Residential code | iccsafe.org |
| UBBL 1984 | Malaysian building code | kpkt.gov.my |
| MS IEC 60364 | Electrical installations | jsm.gov.my |

---

*Architecture Evolution Document v2.0*  
*"From Geometry to Operations: A Complete Theory of BIM Construction"*

*Prepared for: BIM Intent Compiler Project*  
*Date: January 2025*
