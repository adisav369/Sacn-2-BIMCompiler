# BIM INTENT COMPILER - GLOSSARY

**Version:** 0.80.0
**Updated:** February 2025

---

## Core Concepts

| Term | Definition |
|------|------------|
| **BIM Intent Compiler** | System that translates natural language building descriptions into mathematically verified IFC-conformant BIM models |
| **Ground Truth Methodology** | Discipline of extracting patterns from validated reference models (TERMINAL) rather than inventing them |
| **PRIME RULE** | "Extract, don't imagine" - all patterns must come from validated sources |
| **Witness System** | Framework that generates mathematical proofs of building correctness |
| **Authority Data (AD)** | Database tables containing code-backed rules, no hardcoded values in Java |

---

## Authority Data (AD) System (Phase 55-62)

| Term | Definition |
|------|------------|
| **AD Table** | SQLite table prefixed with `ad_` containing code-backed configuration |
| **ADSession** | Single-connection session for efficient AD queries during compilation |
| **AD Facade** | Type-safe Java interface wrapping AD table queries |
| **SpaceTypeAD** | AD for room type definitions and aliases |
| **FireProtectionAD** | AD for FP triggers, risers, and compartments |
| **BOMRuleAD** | AD for Bill of Materials calculation rules |
| **RoomSizingAD** | AD for room dimension constraints |
| **PlacementRuleAD** | AD for element placement constraints |
| **VerticalCirculationAD** | AD for stair and elevator configuration |

### AD Tables

| Table | Purpose |
|-------|---------|
| `ad_spacetype` | Space type definitions (21 types) |
| `ad_spacetype_alias` | Regional aliases (Malaysian, etc.) |
| `ad_fp_trigger` | When fire protection is required |
| `ad_fire_riser_requirement` | Riser sizing by building parameters |
| `ad_fire_compartment` | Compartment area limits by occupancy |
| `ad_bom_rule` | BOM calculation formulas |
| `ad_room_sizing` | Room dimension constraints |
| `ad_placement_rule` | Element placement constraints |
| `ad_vc_stair` | Stair configuration parameters |

---

## BOM Resolution (Phase 60-62)

| Term | Definition |
|------|------------|
| **BOM (Bill of Materials)** | List of components with quantities for a building |
| **BOMResolver** | Stage in DAG compiler that calculates quantities from room properties |
| **BOMRule** | Database record defining how to calculate quantity for an element type |
| **BOMType** | Category: MANDATORY (always 1+), OPTIONAL (user-specified), VARIABLE (calculated) |
| **ResolvedBOMItem** | BOM rule with calculated quantity for a specific room |
| **RoomBOM** | Collection of resolved BOM items for a room |

### Calculation Rules

| Rule | Formula | Example |
|------|---------|---------|
| **PER_AREA** | `ceil(area / base)` | Sprinklers: ceil(84m² / 12.1) = 7 |
| **PER_LUX** | `ceil(area × lux / lumens)` | Lights: ceil(84m² × 200 / 3000) = 6 |
| **PER_CFM** | `ceil(cfm / base)` | Diffusers: ceil(160 CFM / 600) = 1 |
| **PER_OCCUPANT** | `ceil(occupancy / seats)` | Tables: ceil(34 / 4) = 9 |
| **PER_LINEAR** | `ceil(perimeter / spacing)` | Edge lighting |
| **FIXED** | `base` | Toilet: 1 per bathroom |

### Formula Metadata

| Field | Purpose |
|-------|---------|
| `calc_formula` | Human-readable formula for audit trail |
| `calc_occupancy_density` | m²/person when occupancy not provided |
| `calc_cfm_density` | CFM/m² when CFM not provided |

---

## Room Sizing Resolution (Phase 61)

| Term | Definition |
|------|------------|
| **RoomSizingResolver** | Resolves room dimensions from area or validates user-specified dimensions |
| **BY_AREA** | Input mode where user specifies target area, resolver calculates dimensions |
| **BY_DIMENSIONS** | Input mode where user specifies width×depth, resolver validates |
| **Aspect Ratio** | Width:depth ratio, typically max 2.0:1 to avoid bowling alley rooms |
| **Layout Fitting** | Algorithm to fit N rooms of size S into available bounds |

### Constraints

| Constraint | Example |
|------------|---------|
| `min_area_m2` | CLASSROOM min 46.5m² (UBBL) |
| `min_width_m` | CLASSROOM min 6.0m |
| `max_aspect_ratio` | CLASSROOM max 1.8:1 |
| `area_per_occupant_m2` | CLASSROOM 1.85m²/student |

---

## LOD400 Library (Phase 57-59)

| Term | Definition |
|------|------------|
| **LOD400** | Level of Development 400 - fabrication-ready geometry |
| **Component Library** | SQLite database with 8,701 LOD400 component definitions |
| **ComponentDefinition** | Record with geometry hash, bounds, attachment convention |
| **DoorWindowLibraryMapper** | Maps DSL door/window specs to library components |
| **StairLibraryMapper** | Maps stair specs to library StairFlight components |
| **FurniturePlacer** | Places furniture from library based on room type and BOM |

### Library Categories

| Category | IFC Class | Count |
|----------|-----------|-------|
| DOOR | IfcDoor | 112 |
| WINDOW | IfcWindow | 183 |
| STAIR | IfcStairFlight | 32 |
| RAILING | IfcRailing | 34 |
| FURNITURE | IfcFurniture | 131 |
| SPRINKLER | IfcFireSuppressionTerminal | 891 |
| LIGHT | IfcLightFixture | 801 |
| DIFFUSER | IfcAirTerminal | 268 |

---

## Architecture

| Term | Definition |
|------|------------|
| **Federated Model** | SQLite spatial database serving as single source of truth |
| **TERMINAL** | Reference IFC model (51,723 LOD400 elements) from which patterns are extracted |
| **Pure Core** | The unchanging engine (parser, compiler, graph algorithms) |
| **Dynamic Vocabulary** | Configuration that grows (spacetypes, profiles, AD tables) |
| **DAG Pipeline** | Parse → Resolve → [Room Sizing] → [BOM Resolve] → Compile → Place → Write |

---

## SPACE Abstraction

| Term | Definition |
|------|------------|
| **SPACE** | Universal primitive - all building elements either bound, connect, serve, or occupy spaces |
| **SpaceType** | Classifier that determines SPACE behavior (BEDROOM, BATHROOM, CANTEEN, etc.) |
| **SpaceType Category** | Grouping: HABITABLE, SERVICE, CIRCULATION, EXTERIOR, VEHICLE |
| **Wall Rule** | How walls are generated: ENCLOSED, PERIMETER_ONLY, NONE, AS_REQUIRED |

---

## DSL Elements

| Term | Definition |
|------|------------|
| **DSL** | Domain Specific Language - human-readable syntax for describing buildings |
| **BUILDING** | Root container for all elements |
| **STOREY** | Horizontal division of building (floor level) |
| **GRID** | Structural/spatial reference system with named axes and spacing |
| **ENVELOPE** | Building shell: foundation, roof, drainage |
| **SCHEDULE** | Registry of reusable types (doors, windows, materials) |
| **ZONE** | Logical sub-area within OPEN_PLAN without physical walls |
| **CORE** | Vertical circulation block (stairs, elevators, shafts) |

---

## Constraints

| Term | Definition |
|------|------------|
| **exterior:** | Space has exterior wall on specified side |
| **opens_to:** | Space has door connection to named space |
| **adjacent:** | Space must share wall with named space |
| **not_adjacent:** | Space must NOT share wall |
| **stack:** | Named vertical alignment group (plumbing, structure) |
| **above:** | Directly above named space (implies stack) |
| **below:** | Directly below named space (implies stack) |
| **compliance:** | Compliance mode: AUTO_FP, FULL_COMPLIANCE |

---

## MEP System Graph

| Term | Definition |
|------|------------|
| **MEPSystem** | Directed graph representing connected building system |
| **SystemType** | PLUMBING_WASTE, PLUMBING_VENT, PLUMBING_SUPPLY, ELECTRICAL, HVAC_*, FIRE_SUPPRESSION |
| **SystemNode** | Element participating in a system with role and connections |
| **SystemEdge** | Connection between nodes with type and properties |
| **NodeRole** | SOURCE, DISTRIBUTION, TERMINAL, CONNECTOR |
| **EdgeType** | FEEDS, DRAINS_TO, VENTS_TO, SUPPLIES, RETURNS, CONNECTS_VERTICAL |

---

## Fire Protection (Phase 57)

| Term | Definition |
|------|------------|
| **FP Trigger** | Condition that requires fire protection (height, area, occupancy) |
| **Riser Requirement** | Pipe sizing based on building parameters |
| **Fire Compartment** | Area limits by occupancy group |
| **Hazard Class** | LIGHT, ORDINARY_1, ORDINARY_2, HIGH per NFPA 13 |
| **Coverage Area** | m² per sprinkler head (18.6m² light hazard) |
| **AUTO_FP** | Compliance mode that auto-generates sprinklers when triggered |

---

## Fire Suppression Piping (Phase 80)

| Term | Definition |
|------|------------|
| **FireSuppressionPlacer** | Generator for FP piping connecting sprinkler heads |
| **FPPipeSpec** | Specification for a fire protection pipe segment |
| **FPPipeType** | Pipe category: RISER, MAIN, BRANCH |
| **FP_RISER** | Vertical pipe from pump room (100mm diameter) |
| **FP_MAIN** | Horizontal distribution pipe along ceiling (65mm diameter) |
| **FP_BRANCH** | Short connection from main to sprinkler head (25mm diameter) |
| **FPAssembly** | Group of pipes for BOM procurement (en-bloc) |
| **BOM Set Approach** | Grouping pipes into assemblies for easier procurement |

### FP Pipe Types

| Type | Diameter | NFPA 13 | Purpose |
|------|----------|---------|---------|
| RISER | 100mm (4") | Schedule 40 | Vertical from pump room per storey |
| MAIN | 65mm (2.5") | Schedule 40 | Horizontal along ceiling |
| BRANCH | 25mm (1") | Schedule 40 | Connection to sprinkler head |

### FP Assembly Types

| Assembly | Contents |
|----------|----------|
| `FP_Ground_RISER` | Riser pipe + floor tee |
| `FP_Ground_MAIN` | Main pipe + branch tees |
| `FP_Ground_LIVING_BRANCH` | Branch pipes for one room |

---

## Witness System

| Term | Definition |
|------|------------|
| **Witness** | Mathematical proof that a claim about the building is true |
| **Witness Claim** | Specific assertion (e.g., "all fixtures drain to septic") |
| **Witness Status** | PROVEN, FAILED, SKIPPED, UNPROVABLE |
| **Witness Certificate** | JSON file containing all claims and proofs |

### Current Witness Claims (21)

| # | Claim | Proves |
|---|-------|--------|
| 1 | `FOUNDATION_GROUNDED` | Foundation at Z=0 |
| 2 | `ENTRY_EXISTS` | Door from exterior |
| 3 | `ALL_ROOMS_REACHABLE` | Path exists to every room |
| 4 | `WINDOWS_ON_EXTERIOR` | All windows on exterior walls |
| 5 | `ROOF_COVERS_ALL` | Roof covers footprint |
| 6 | `ROOMS_ENCLOSED` | Each room forms closed polygon |
| 7 | `ROOMS_IN_ENVELOPE` | All rooms inside building bbox |
| 8 | `ELECTRICAL_IN_SPACES` | Electrical within room bounds |
| 9 | `FIXTURES_ATTACHED_TO_HOSTS` | Lights attached to ceiling |
| 10 | `PLUMBING_PIPES_VALID` | Pipe dimensions correct |
| 11 | `PLUMBING_WASTE_COMPLETE` | All fixtures drain to MH |
| 12 | `PLUMBING_VENT_COMPLETE` | All traps vent to atmosphere |
| 13 | `PLUMBING_SUPPLY_COMPLETE` | Water meter supplies all fixtures |
| 14 | `STOREYS_VERTICALLY_CONSISTENT` | Stack alignment across storeys |
| 15 | `ALL_OUTLETS_ON_CIRCUIT` | All outlets connected to DB panel |
| 16-21 | Additional claims | See witness-system-specification.md |

---

## Database Schema

| Table | Purpose |
|-------|---------|
| `spatial_structure` | Hierarchy: Project → Site → Building → Storey |
| `elements_meta` | Element identity: guid, ifc_class, name, discipline |
| `elements_rtree` | Spatial index: bounding boxes |
| `base_geometries` | Geometry storage: vertices, faces, hash |
| `element_instances` | Transforms: position, rotation |
| `element_assemblies` | BOM parent structures |
| `assembly_components` | BOM child relationships |
| `mep_systems` | System metadata |
| `system_nodes` | Nodes in system graph |
| `system_edges` | Edges in system graph |

---

## Standards Alignment

| Standard | Use |
|----------|-----|
| **IFC4** (ISO 16739) | BIM data exchange format |
| **LOD** (BIM Forum) | Level of Development: 100-500 |
| **OmniClass** (Table 13) | Space classification |
| **NFPA 13** | Sprinkler spacing and coverage |
| **ASHRAE 62.1** | Ventilation rates |
| **MS 1525** | Malaysian lighting standards |
| **IBC 2021** | International Building Code |
| **UBBL 1984** | Malaysian building code |
| **IRC 2021** | US residential code |
| **IPC** | International Plumbing Code |
| **MS IEC 60364** | Malaysian electrical |

---

## Key Constants

| Constant | Value | Source |
|----------|-------|--------|
| `TOLERANCE` | 5mm (0.005m) | TERMINAL |
| `WALL_INTERIOR` | 100mm | Malaysian standard |
| `WALL_EXTERIOR` | 150mm | Malaysian standard |
| `SPRINKLER_COVERAGE_LIGHT` | 18.6m² | NFPA 13 8.5.2.1 |
| `SPRINKLER_COVERAGE_ORDINARY` | 12.1m² | NFPA 13 8.6.2.1 |
| `SPRINKLER_SPACING_MAX` | 4.6m | NFPA 13 |
| `CLASSROOM_OCCUPANCY_DENSITY` | 1.85m²/person | UBBL Table 3 |
| `CANTEEN_OCCUPANCY_DENSITY` | 2.5m²/person | IBC 1004.5 |

---

## File Types

| Extension | Purpose |
|-----------|---------|
| `.bim` | DSL source file |
| `.db` | SQLite federated model |
| `.ifc` | IFC exchange file |
| `.json` | Witness certificate |
| `.csv` | BOM export |
| `.yaml` | Configuration (spacetypes, profiles) |

---

## Outlier Handling (Phase 25)

| Term | Definition |
|------|------------|
| **OutlierLogger** | Central utility for logging unusual conditions |
| **UNKNOWN_SPACETYPE** | Unrecognized room type, falls back to GENERIC |
| **MISSING_COMPONENT** | Library component not found |
| **GEOMETRY_IMPOSSIBLE** | Element won't fit in room |
| **UNSATISFIABLE** | Constraints cannot be satisfied |
| **Fallback Cascade** | Exact → Partial → Category → GENERIC |

---

*Glossary v0.80.0 - Updated for Authority Data, BOM Resolution, and Fire Suppression Piping*
