# BIM INTENT COMPILER - GLOSSARY

**Version:** 0.39.0
**Updated:** January 2025

---

## Core Concepts

| Term | Definition |
|------|------------|
| **BIM Intent Compiler** | System that translates natural language building descriptions into mathematically verified IFC-conformant BIM models |
| **Ground Truth Methodology** | Discipline of extracting patterns from validated reference models (TERMINAL) rather than inventing them |
| **PRIME RULE** | "Extract, don't imagine" - all patterns must come from validated sources |
| **Witness System** | Framework that generates mathematical proofs of building correctness |

---

## Architecture

| Term | Definition |
|------|------------|
| **Federated Model** | SQLite spatial database serving as single source of truth for geometry and relationships |
| **TERMINAL** | Reference IFC model (51,723 LOD400 elements) from which patterns are extracted |
| **Pure Core** | The unchanging engine (parser, compiler, graph algorithms) |
| **Dynamic Vocabulary** | Configuration that grows (spacetypes.yaml, profiles, component library) |

---

## SPACE Abstraction

| Term | Definition |
|------|------------|
| **SPACE** | Universal primitive - all building elements either bound, connect, serve, or occupy spaces (analogous to Document in ERP) |
| **SpaceType** | Classifier that determines SPACE behavior (BEDROOM, BATHROOM, OPEN_PLAN, etc.) |
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

---

## MEP System Graph (Phase 35+)

| Term | Definition |
|------|------------|
| **MEPSystem** | Directed graph representing connected building system with nodes and edges |
| **SystemType** | Type of system: PLUMBING_WASTE, PLUMBING_VENT, PLUMBING_SUPPLY, ELECTRICAL, HVAC_SUPPLY, HVAC_RETURN, FIRE_SUPPRESSION |
| **SystemNode** | Element participating in a system with role and connections |
| **SystemEdge** | Connection between nodes with type and properties |
| **NodeRole** | Role in system: SOURCE, DISTRIBUTION, TERMINAL, CONNECTOR |
| **EdgeType** | Connection type: FEEDS, DRAINS_TO, VENTS_TO, SUPPLIES, RETURNS |

### NodeRole Values

| Role | Description | Examples |
|------|-------------|----------|
| **SOURCE** | Origin of system | DB panel, water meter, MH, vent termination |
| **DISTRIBUTION** | Mid-path element | Riser, circuit, trunk line |
| **TERMINAL** | End-point | Fixture, outlet, diffuser |
| **CONNECTOR** | Junction | Fitting, tee, elbow |

### EdgeType Values

| Type | Description | Direction |
|------|-------------|-----------|
| **FEEDS** | Electrical power flow | Panel → outlet |
| **DRAINS_TO** | Waste water flow | Fixture → riser → MH |
| **VENTS_TO** | Vent air flow | Trap → vent stack → atmosphere |
| **SUPPLIES** | Water/air supply | Source → terminal |
| **RETURNS** | Return flow | Terminal → return trunk |
| **CONNECTS_VERTICAL** | Cross-storey stack connection | Upper riser → lower riser (Phase 38) |

### Graph Operations

| Operation | Description |
|-----------|-------------|
| **isConnected()** | All terminals can reach/be reached from source |
| **isComplete()** | Every terminal has valid path |
| **getPath(from, to)** | Returns node sequence between two points |
| **getOrphanedTerminals()** | Terminals with no path to source |

---

## Witness System

| Term | Definition |
|------|------------|
| **Witness** | Mathematical proof that a claim about the building is true |
| **Witness Claim** | Specific assertion (e.g., "all fixtures drain to septic") |
| **Witness Status** | PROVEN, FAILED, SKIPPED, UNPROVABLE |
| **Witness Certificate** | JSON file containing all claims and proofs |

### Current Witness Claims (15)

| Claim | Proves |
|-------|--------|
| `FOUNDATION_GROUNDED` | Foundation top at Z=0 |
| `ENTRY_EXISTS` | Door from exterior to interior |
| `ALL_ROOMS_REACHABLE` | Path exists from entry to every room |
| `WINDOWS_ON_EXTERIOR` | All windows on exterior walls |
| `ROOF_COVERS_ALL` | Roof polygon contains all room corners |
| `ROOMS_ENCLOSED` | Each room forms closed polygon |
| `ROOMS_IN_ENVELOPE` | All rooms inside building bbox |
| `ELECTRICAL_IN_SPACES` | Electrical elements within room bounds |
| `FIXTURES_ATTACHED_TO_HOSTS` | Lights attached to ceiling (0mm gap) |
| `PLUMBING_PIPES_VALID` | Pipe dimensions and orientation correct |
| `PLUMBING_WASTE_COMPLETE` | All fixtures drain to MH |
| `PLUMBING_VENT_COMPLETE` | All traps vent to atmosphere |
| `PLUMBING_SUPPLY_COMPLETE` | Water meter supplies all fixtures |
| `STOREYS_VERTICALLY_CONSISTENT` | Stack alignment across storeys (5mm tolerance) |
| `ALL_OUTLETS_ON_CIRCUIT` | All outlets connected to DB panel via circuits (Phase 39) |

---

## BIM Correctness Levels

| Level | Name | Description |
|-------|------|-------------|
| **L0** | Geometric | Valid shapes, vertices, faces |
| **L1** | Spatial | Correct locations, containment |
| **L2** | Topological | Correct connections (doors, MEP) |
| **L3** | Systemic | Systems function as wholes |
| **L4** | Constructible | Can be built in sequence |
| **L5** | Compliant | Passes code inspection |
| **L6** | Operable | Can be maintained/operated |

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
| `mep_systems` | System metadata (Phase 35+) |
| `system_nodes` | Nodes in system graph (Phase 35+) |
| `system_edges` | Edges in system graph (Phase 35+) |

---

## Extracted Patterns (G1-G12)

| Pattern | Description | Value |
|---------|-------------|-------|
| **G1** | Placement Anchor | CENTER |
| **G2** | Orientation | Infer from bbox thin axis |
| **G3** | Routing Zones | Wall: 87-229mm, Ceiling: 440-727mm |
| **G4** | Connection Logic | Walls overlap 133mm |
| **G5** | Extrusion Direction | Columns/doors Z-up |
| **G6** | Profile Uniqueness | Plates 4.2× reuse |
| **G7** | Opening-Wall Ratio | 55% width, 63.5% height |
| **G8** | Floor-to-Floor | 4m intervals |
| **G9** | MEP Zone | 830-916mm plenum |
| **G10** | Termination | 0.82 fittings/pipe |
| **G11** | Instancing | Plates max 17 instances |
| **G12** | Boundary | 21 elements at edge |

---

## Standards Alignment

| Standard | Use |
|----------|-----|
| **IFC4** (ISO 16739) | BIM data exchange format |
| **LOD** (BIM Forum) | Level of Development: 100-500 |
| **OmniClass** (Table 13) | Space classification |
| **IRC 2021** | US residential code |
| **UBBL 1984** | Malaysian building code |
| **NFPA 13** | Sprinkler spacing |
| **MS IEC 60364** | Malaysian electrical |

---

## Key Constants

| Constant | Value | Source |
|----------|-------|--------|
| `TOLERANCE` | 5mm (0.005m) | TERMINAL |
| `WALL_INTERIOR` | 100mm | Malaysian standard |
| `WALL_EXTERIOR` | 150mm | Malaysian standard |
| `OUTLET_HEIGHT` | 300mm AFF | IRC |
| `SWITCH_HEIGHT` | 1200mm AFF | IRC |
| `DOOR_HEIGHT` | 2100mm | Malaysian standard |
| `CEILING_OFFSET` | 20mm | MEP mounting |
| `SPRINKLER_SPACING_MAX` | 4.6m | NFPA 13 |
| `VENT_ABOVE_ROOF` | 300mm | IRC |

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

## Inspiration Sources

| Source | Pattern Borrowed |
|--------|------------------|
| **iDempiere** | Document/DocType → SPACE/SpaceType |
| **HL7 FHIR** | Profiles for regional variants |
| **DITA** | Specialization (type hierarchy) |
| **CityGML** | LOD levels |
| **STEP** | Application Protocols |

---

*Glossary v0.39.0 - Updated for Electrical Circuits Graph*
