# BIM INTENT COMPILER - GLOSSARY OF TERMS

## System Identity

| Term | Definition | Format |
|------|------------|--------|
| **BIM Intent Compiler** | The complete system that translates natural language building intent into valid IFC-conformant BIM models | Java application |
| **Ground Truth Methodology** | The discipline of extracting patterns from validated reference models (TERMINAL) rather than AI invention | Methodology |
| **PRIME RULE** | "Extract, don't imagine" - the governing principle that all patterns must come from validated sources | Principle |

---

## Core Architecture

| Term | Definition | Format |
|------|------------|--------|
| **Federated Model** | The SQLite spatial database that serves as single source of truth for all geometry and relationships | `.db` (SQLite) |
| **TERMINAL** | The reference IFC model (51,723 LOD400 elements) from which all patterns are extracted | `.ifc` → `.db` |
| **Ground Truth** | Validated reference data (TERMINAL, TB-LKTN) that provides proven patterns | `.db` |

---

## DSL Components

| Term | Definition | Format |
|------|------------|--------|
| **DSL** | Domain Specific Language - the human-readable syntax for describing buildings | `.bim` (text) |
| **Building Vocabulary** | The complete set of valid terms, types, and syntax the DSL accepts | `.md` (Markdown) |
| **SPACE** | The universal primitive - all building elements either bound, connect, serve, or occupy spaces (analogous to Document in ERP) | DSL keyword |
| **SpaceType** | The classifier that determines SPACE behavior (BEDROOM, BATHROOM, OPEN_PLAN, etc.) | `.java` enum |
| **GRID** | The structural/spatial reference system with named axes and spacing | DSL block |
| **ENVELOPE** | Building shell elements (foundation, roof, drainage) | DSL block |
| **SCHEDULE** | Registry of reusable component types (doors, windows, materials) | DSL block |
| **STOREY** | Horizontal division of building (floor level) | DSL block |
| **ZONE** | Logical sub-area within OPEN_PLAN without physical walls | DSL block |

---

## Vocabulary Framework

| Term | Definition | Format |
|------|------------|--------|
| **Building Vocabulary** | The master dictionary defining all valid DSL terms, types, rules, and relationships | `bim-dsl-dictionary.md` |
| **Profile** | Regional/code variant that adapts vocabulary defaults and validation (Malaysian_Residential, US_IRC, UK_Regs) | `ProfileRegistry.java` |
| **Protocol** | Building type template defining required/optional/excluded spaces (Residential_Single_Storey, Commercial_Office) | `ProtocolValidator.java` |
| **Specialization** | Type hierarchy where child types inherit and extend parent types (MASTER_BEDROOM specializes BEDROOM) | Dictionary entry |
| **LOD** | Level of Detail/Development - controls content depth (LOD100 conceptual → LOD500 as-built) | `LODValidatorRegistry.java` |

---

## Compiler Components

| Term | Definition | Format |
|------|------------|--------|
| **Parser** | Converts DSL text into BuildingDefinition | `BuildingParser.java` |
| **Compiler** | Converts BuildingDefinition into BuildingSpec with geometry | `BuildingCompiler.java` |
| **Resolver** | Components that look up types from registries | `*Resolver.java` |
| **Factory** | Pattern for creating typed objects from vocabulary | `*Factory.java` |
| **Builder** | Parametric geometry generators | `*Builder.java` |
| **Placer** | Component placement logic | `*Placer.java` |
| **Writer** | Writes BuildingSpec to Federated DB | `BuildingWriter.java` |

---

## Validation Framework

| Term | Definition | Format |
|------|------------|--------|
| **ValidatorChain** | Composite of validators executed in sequence | `ValidatorChain.java` |
| **ValidatorFactory** | Creates dynamic ValidatorChain based on Profile/Protocol/LOD | `ValidatorFactory.java` |
| **GeometryValidator** | Checks wall connectivity, gaps, overlaps, manifolds | `GeometryValidator.java` |
| **HabitabilityValidator** | Checks natural light, egress, minimum dimensions | `HabitabilityValidator.java` |
| **ProfileValidator** | Checks regional code compliance | `ProfileValidatorRegistry.java` |
| **ProtocolValidator** | Checks building type requirements | `ProtocolValidatorRegistry.java` |
| **LODValidator** | Checks content completeness for LOD level | `LODValidatorRegistry.java` |

---

## Database Schema (SQLite `.db`)

| Term | Definition | Table |
|------|------------|-------|
| **elements_meta** | Identity table (guid, ifc_class, name, discipline) | `elements_meta` |
| **elements_rtree** | Spatial index table (bounding boxes for fast queries) | `elements_rtree` |
| **base_geometries** | Geometry storage (vertices[], faces[], geometry_hash) | `base_geometries` |
| **element_instances** | Transform and instancing (position, rotation, geometry reference) | `element_instances` |
| **spatial_structure** | Hierarchy table (building → storey → space) | `spatial_structure` |
| **element_assemblies** | BOM parent structures | `element_assemblies` |
| **assembly_components** | BOM child relationships | `assembly_components` |

---

## Patterns (G1-G12)

| Term | Definition | Source |
|------|------------|--------|
| **G1 Placement Anchor** | All types use CENTER anchor | TERMINAL extraction |
| **G2 Orientation** | Infer from bbox thin axis | TERMINAL extraction |
| **G3 Routing Rules** | Wall: 87-229mm, Ceiling: 440-727mm | TERMINAL extraction |
| **G4 Connection Logic** | Walls overlap 133mm, pipes insert 14-42mm | TERMINAL extraction |
| **G5 Extrusion Direction** | Columns/doors Z-up, plates X-axis | TERMINAL extraction |
| **G6 Profile Uniqueness** | Plates 4.2x reuse, pipes 1.0x | TERMINAL extraction |
| **G7 Opening-Wall Ratio** | 55% width, 63.5% height typical | TERMINAL extraction |
| **G8 Floor-to-Floor** | 4m intervals, slabs overlap 175-250mm | TERMINAL extraction |
| **G9 MEP Zone** | 830-916mm typical plenum | TERMINAL extraction |
| **G10 Termination** | FP 1.18 fittings/pipe, SP 0.82 | TERMINAL extraction |
| **G11 Instancing** | Plates max 17 instances, pipes unique | TERMINAL extraction |
| **G12 Boundary** | Only 21 elements at edge | TERMINAL extraction |

---

## Constraints (DSL keywords)

| Term | Definition | Syntax |
|------|------------|--------|
| **exterior:** | Space has exterior wall on specified side | `exterior: south` |
| **opens_to:** | Space has door connection to named space | `opens_to: common` |
| **adjacent:** | Space must share wall with named space | `adjacent: kitchen` |
| **not_adjacent:** | Space must NOT share wall | `not_adjacent: garage` |
| **stack:** | Vertical alignment group (plumbing, structure) | `stack: plumbing` |
| **above:** | Directly above named space (implies stack) | `above: living` |
| **below:** | Directly below named space (implies stack) | `below: bedroom` |
| **aligns:** | Same X,Y position across storeys | `aligns: bathroom` |

---

## Wall Rules (Java enum)

| Term | Definition | Enum value |
|------|------------|------------|
| **ENCLOSED** | Fully walled room (4 walls) | `WallRule.ENCLOSED` |
| **PERIMETER_ONLY** | Walls at building edge only (OPEN_PLAN) | `WallRule.PERIMETER_ONLY` |
| **NONE** | No walls (PORCH, open structures) | `WallRule.NONE` |
| **AS_REQUIRED** | Context-dependent wall generation | `WallRule.AS_REQUIRED` |

---

## Output Pipeline

| Term | Definition | Format |
|------|------------|--------|
| **BuildingSpec** | In-memory compiled building model | Java object |
| **Federated DB** | SQLite database in TERMINAL-conformant schema | `.db` (SQLite) |
| **IFC Export** | Optional - converts DB to IFC file (redundant if using Blender bake) | `.ifc` |
| **Blender Bake** | Renders DB directly in Blender | `federation_viz_helper.py` |
| **BOM** | Bill of Materials - quantities and costs for ERP integration | `.csv` |

---

## Quality Assurance

| Term | Definition | Format |
|------|------------|--------|
| **Mathematical Proof** | Numerical verification of geometry (not visual inspection) | `*Test.java` |
| **TERMINAL Conformance** | Output matches TERMINAL schema and patterns | SQL queries |
| **Outlier** | Input that doesn't fit current vocabulary (logged for future expansion) | `OutlierLogger.java` |
| **Graceful Degradation** | System handles unknowns without crashing (fallback + log) | Pattern |

---

## Standards Alignment

| Term | Definition | Reference |
|------|------------|-----------|
| **IFC** | ISO 16739 - Industry Foundation Classes (BIM standard) | `.ifc` files |
| **LOD** | BIM Forum Level of Development specification | LOD 100-500 |
| **OmniClass** | Construction classification system (Table 13 for spaces) | Table 13 codes |
| **IRC** | International Residential Code (US) | 2021 edition |
| **IBC** | International Building Code (US commercial) | 2021 edition |
| **UBBL** | Uniform Building By-Laws (Malaysia) | 1984 edition |
| **NFPA** | National Fire Protection Association (sprinkler spacing) | NFPA 13 |

---

## Inspiration Sources

| Term | Definition | Pattern Borrowed |
|------|------------|------------------|
| **iDempiere** | ERP system whose Document/DocType pattern inspired SPACE/SpaceType | AD_* tables, typed entities |
| **HL7 FHIR** | Healthcare standard whose Profiles concept we adopted | Resource Profiles |
| **DITA** | Documentation standard whose Specialization concept we adopted | Topic specialization |
| **CityGML** | GIS standard whose LOD levels we adopted | LOD 0-4 |
| **STEP** | Manufacturing standard whose Application Protocols we adopted | AP214, AP242 |

---

## Key File Types Summary

| Extension | Purpose | Example |
|-----------|---------|---------|
| `.bim` | DSL source file | `tb-lktn.bim` |
| `.db` | SQLite federated model | `tb_lktn.db`, `terminal.db` |
| `.ifc` | IFC exchange file (optional) | `terminal.ifc` |
| `.java` | Compiler source code | `BuildingCompiler.java` |
| `.py` | Python scripts (export, bake) | `federation_viz_helper.py` |
| `.md` | Documentation, vocabulary | `bim-dsl-dictionary.md` |
| `.csv` | BOM export for ERP | `tb_lktn_bom.csv` |
| `.blend` | Blender output | `tb_lktn.blend` |

---

## Key Principles

| Principle | Meaning |
|-----------|---------|
| **SPACE as Universal Primitive** | All elements relate to SPACE (like Document in ERP) |
| **Vocabulary as Data** | Dictionary entries, not hardcoded logic |
| **Pure Core, Dynamic Vocabulary** | Engine unchanging, vocabulary grows |
| **Extract, Don't Imagine** | Patterns from reference, not invention |
| **Mathematical Verification** | Numbers prove correctness, not visuals |
| **Factory Pattern** | Runtime composition from registry |
| **TERMINAL Conformance** | Output matches proven schema |

---

## Usage Examples

```
"The SPACE primitive with SpaceType BEDROOM..."
"Add a new Profile for Singapore HDB..."
"The Building Vocabulary defines OPEN_PLAN as..."
"ValidatorFactory composes chain from Profile and Protocol..."
"Write to Federated DB for Blender bake..."
"Outlier logged - unknown SpaceType, using fallback..."
"TERMINAL conformance verified - schema matches..."
```

---

*Version 1.0 - January 2025*
