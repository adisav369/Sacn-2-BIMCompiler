# BIM Compiler Contract Architecture Specification

**Version:** 1.0
**Date:** 2026-02-03
**Status:** Foundation Document
**Supplements:** FOSS_DEVELOPER_GUIDE.md, bim-dsl-dictionary.md, ENRICHMENT_PLAN.md

---

## 1. Executive Summary

This document specifies a layered contract architecture for the BIM Intent Compiler that prevents structural bugs at compile-time. The architecture is grounded in IFC standards (ISO 16739), mereotopological theory, and Domain-Driven Design patterns.

**Core Problem Addressed:** Elements that should be SHARED are instead OWNED by multiple assemblies, causing duplicates at boundaries.

**Solution:** Java interface contracts that force every element to explicitly declare its relationships, enabling compile-time enforcement of correct patterns.

---

## 2. Problem Analysis

### 2.1 The Observed Symptom

Visual inspection of compiled models revealed duplicate structural elements at wall corners:

```
OBSERVED:
Corner at (0, 8.5) has 2 studs at identical coordinates
- SOUTH_WALL creates STUD_R at corner
- WEST_WALL creates STUD_L at same corner
```

### 2.2 Root Cause Analysis

| Level | Finding |
|-------|---------|
| **Immediate** | Duplicate geometry at coordinates |
| **Technical** | No deduplication for frame members inside wall assemblies |
| **Architectural** | `WallAssemblySpec` designed as monolithic unit owning all boundaries |
| **Spec-Level** | Missing concept of "shared junction" — assemblies OWN bounds instead of CONNECT TO shared bounds |
| **Theoretical** | `IfcRelConnectsPathElements` pattern documented but not enforced in code |

### 2.3 Pattern Classification

The "owns bounds" anti-pattern manifests in multiple forms:

| Pattern | Example | Risk |
|---------|---------|------|
| **Boundary Ownership** | Wall owns corner studs | Duplicates at shared edges |
| **Merge Without Deduplication** | `columns.addAll()` blind concat | Duplicates at shared corners |
| **Vertical Discontinuity** | Per-storey column creation | Discontinuous spanning elements |
| **Embed vs Reference** | Specs embed copies, not references | Duplicate entities |
| **LOD400 Dimensional** | Component exceeds parent bounds | Clashes, fabrication errors |

### 2.4 What Works vs What Doesn't

| Element Type | Pattern | Result |
|--------------|---------|--------|
| **Columns** | Corner-first via `findCorners()` | WORKS — computes shared points first |
| **MEP** | Stack-first via `StackInfo` | WORKS — fixtures connect TO shared infrastructure |
| **Wall Studs** | Wall-owns-all via `WallAssemblySpec` | FAILS — each wall creates own boundary |

---

## 3. Theoretical Foundations

### 3.1 IFC Standard (ISO 16739-1:2018)

The Industry Foundation Classes standard defines relationship entities that our contracts formalize:

```
IFC RELATIONSHIP ENTITIES

IfcRelConnects                    → Connection relationships
├── IfcRelConnectsPathElements    → Wall-to-wall at junctions
├── IfcRelConnectsPortToElement   → MEP port connections
└── IfcRelConnectsElements        → Generic element connections

IfcRelAggregates                  → Part-whole relationships
├── Building aggregates Storeys
├── Storey aggregates Spaces
└── Assembly aggregates Components

IfcRelContainedInSpatialStructure → Spatial containment
IfcRelDefinesByType               → Type instantiation
IfcRelVoidsElement                → Opening in element
IfcRelFillsElement                → Element filling void
```

**Key Insight:** IFC defines these relationships as first-class entities, not implicit attributes. Our contracts enforce this pattern in Java.

### 3.2 Mereotopology

Formal theory of part-whole relationships combined with spatial topology:

**Mereology (Part-Whole Theory)**
- Origin: Stanisław Leśniewski (1916)
- Application: Spatial reasoning in CAD/BIM
- Key relation: Boundary — the problematic relationship we're addressing

**RCC-8 (Region Connection Calculus)**
- Origin: Cohn et al. (1997)
- Defines 8 exhaustive spatial relationships:

```
DC  - Disconnected           (no contact)
EC  - Externally Connected   (touching, not overlapping)
PO  - Partially Overlapping  (intersection)
EQ  - Equal                  (same region)
TPP - Tangential Proper Part (inside, touching boundary)
NTPP- Non-Tangential Proper Part (inside, not touching)
TPPi- Inverse of TPP
NTPPi-Inverse of NTPP
```

**Application:** Our `ComponentRole.BOUNDARY` maps to RCC-8's EC (Externally Connected) relationship.

### 3.3 Design Patterns

**Gang of Four Patterns:**

| Pattern | Application |
|---------|-------------|
| **Registry** | `SharedElementRegistry` for junction points |
| **Composite** | Assembly contains components |
| **Flyweight** | Shared type definitions |

**Domain-Driven Design (Evans, 2003):**

| Concept | Application |
|---------|-------------|
| **Entity** | Elements with identity (`guid`) |
| **Value Object** | Coordinates, dimensions |
| **Aggregate Root** | Assembly as root for components |
| **Bounded Context** | Unit/Storey/Building as contexts |
| **Repository** | `SharedElementRegistry` |

**Patterns of Enterprise Application Architecture (Fowler):**

| Pattern | Application |
|---------|-------------|
| **Identity Map** | `continuityId()` for spanning elements |
| **Unit of Work** | Compilation transaction |

### 3.4 EXPRESS/STEP (ISO 10303)

The data modeling language underlying IFC provides formal constructs:

```
EXPRESS CONSTRUCT        → OUR CONTRACT EQUIVALENT

ENTITY                   → IBIMEntity interface
SUBTYPE OF               → Interface inheritance chain
INVERSE                  → Bidirectional relationship declarations
UNIQUE                   → uniqueKey() method
WHERE rules              → validate() constraints
DERIVE                   → Computed properties
```

---

## 4. Relationship Taxonomy

### 4.1 Complete Classification

Every possible relationship between BIM elements falls into one of these categories:

```
SPATIAL RELATIONSHIPS
├── BOUNDARY      → Shared edges/surfaces (walls at corners)
├── CONTAINMENT   → A contains B (room contains light)
├── INTERSECTION  → A passes through B (pipe through wall)
├── ADJACENCY     → A next to B (room adjacent to room)
└── OVERLAP       → A and B share volume (clash)

IDENTITY RELATIONSHIPS
├── CONTINUITY    → Same element across contexts (column spans storeys)
├── INSTANCE_OF   → A is instance of type B (door is D1 type)
├── COPY_OF       → A is copy of B (duplicate detection)
└── VERSION_OF    → A replaces B (evolution)

DEPENDENCY RELATIONSHIPS
├── REQUIRES      → A needs B to exist (lintel needs wall)
├── HOSTS         → A provides location for B (wall hosts door)
├── FEEDS         → A supplies B (panel feeds outlet)
└── CONSTRAINS    → A limits B (fire rating constrains material)

OWNERSHIP RELATIONSHIPS
├── OWNS          → A is sole owner of B (assembly owns internal stud)
├── SHARES        → A and B co-own C (walls share corner post)
├── REFERENCES    → A points to B (spec references library component)
└── DELEGATES     → A's authority from B (unit inherits from building)

AGGREGATION RELATIONSHIPS
├── ASSEMBLES     → A composed of B,C,D (wall = studs + cladding)
├── GROUPS        → A collects B,C,D (discipline groups elements)
├── MERGES        → A,B become C (units merge to storey)
└── NETWORKS      → A,B,C form graph (MEP system)
```

### 4.2 Mapping to IFC

| Our Taxonomy | IFC Relationship Entity |
|--------------|-------------------------|
| BOUNDARY | IfcRelConnectsPathElements |
| CONTAINMENT | IfcRelContainedInSpatialStructure |
| INTERSECTION | IfcRelVoidsElement |
| HOSTS | IfcRelFillsElement |
| INSTANCE_OF | IfcRelDefinesByType |
| ASSEMBLES | IfcRelAggregates |
| GROUPS | IfcRelAssignsToGroup |
| NETWORKS | IfcRelConnectsPortToElement |

---

## 5. Contract Architecture

### 5.1 Layer Model

```
┌─────────────────────────────────────────────────────────────┐
│ Layer 5: SEMANTIC CONTRACTS                                 │
│          Domain-specific validation rules                   │
│          "Bathroom must have ventilation"                   │
├─────────────────────────────────────────────────────────────┤
│ Layer 4: AGGREGATION CONTRACTS                              │
│          Merge, compose, deduplicate                        │
│          "When merging, deduplicate by uniqueKey()"         │
├─────────────────────────────────────────────────────────────┤
│ Layer 3: RELATIONSHIP CONTRACTS                             │
│          Connect, host, feed, require                       │
│          "Element must declare what it connectsTo()"        │
├─────────────────────────────────────────────────────────────┤
│ Layer 2: IDENTITY CONTRACTS                                 │
│          Same, different, continues                         │
│          "Element must declare continuityId()"              │
├─────────────────────────────────────────────────────────────┤
│ Layer 1: EXISTENCE CONTRACTS                                │
│          Mandatory attributes                               │
│          "Every element must have GUID, storey, discipline" │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 Interface Definitions

#### Layer 1: Existence Contract

```java
/**
 * Base contract for all BIM entities.
 * Enforces mandatory attributes that every element must have.
 *
 * Maps to: IfcRoot (ISO 16739)
 * Theory: Entity identity (DDD)
 */
public interface IBIMEntity {

    /** Globally unique identifier. Maps to IfcRoot.GlobalId */
    String guid();

    /** Spatial context (storey name). Maps to IfcRelContainedInSpatialStructure */
    String storey();

    /** Discipline classification. Maps to IfcRelAssociatesClassification */
    Discipline discipline();

    /** Spatial extent. Derived from IfcProduct.ObjectPlacement + geometry */
    BoundingBox bounds();
}
```

#### Layer 2: Identity Contract

```java
/**
 * Identity contract for deduplication and continuity.
 * Determines when elements are "the same" within or across contexts.
 *
 * Maps to: IfcRoot.GlobalId + custom continuity
 * Theory: Identity Map pattern (Fowler), Genidentity (philosophy)
 */
public interface IIdentifiable extends IBIMEntity {

    /**
     * Key for deduplication within same context.
     * Elements with same uniqueKey at same location are duplicates.
     *
     * Example: "CORNER_STUD_0.0_8.5_Ground"
     */
    String uniqueKey();

    /**
     * ID that persists across storeys/contexts.
     * Non-null for elements that span (columns, risers).
     *
     * Example: "COL_A1" for column at grid A-1 across all storeys
     */
    String continuityId();

    /**
     * Type definition this is instance of.
     * Maps to IfcRelDefinesByType.
     *
     * Example: "D1_900x2100" for door type
     */
    String typeRef();
}
```

#### Layer 3: Relationship Contract

```java
/**
 * Relationship contract declaring how element connects to others.
 * Forces explicit declaration of all relationships.
 *
 * Maps to: IfcRelConnects* family
 * Theory: RCC-8 topology, Mereology
 */
public interface IRelatable extends IIdentifiable {

    /**
     * Junction points this element connects TO (does not own).
     * Critical for boundary sharing.
     *
     * Maps to: IfcRelConnectsPathElements
     *
     * Example: Wall connects to corner junctions at both ends
     */
    List<JunctionRef> connectsTo();

    /**
     * Element that hosts/contains this one.
     * Maps to: IfcRelContainedInSpatialStructure, IfcRelFillsElement
     *
     * Example: Door hosted by wall, Light hosted by room
     */
    String hostedBy();

    /**
     * Elements this one requires to exist first.
     * Defines construction/compilation order.
     *
     * Maps to: IfcRelSequence (construction)
     *
     * Example: Lintel requires wall, Door requires opening
     */
    List<String> requires();

    /**
     * Elements this one feeds/supplies in a network.
     * Maps to: IfcRelConnectsPortToElement (MEP)
     *
     * Example: Panel feeds outlets, Riser feeds branch pipes
     */
    List<String> feeds();
}

/**
 * Reference to a shared junction point.
 */
public record JunctionRef(
    String junctionId,      // Registry key for shared junction
    ConnectionRole role     // How this element connects
) {}

public enum ConnectionRole {
    START,      // Element starts at this junction
    END,        // Element ends at this junction
    MID,        // Element passes through junction
    TERMINATES  // Element terminates at junction (dead end)
}
```

#### Layer 4: Aggregation Contract

```java
/**
 * Aggregation contract for merge, compose, and deduplicate operations.
 * Controls how elements combine when contexts merge.
 *
 * Maps to: IfcRelAggregates, IfcElementAssembly
 * Theory: Aggregate Root (DDD), Composite pattern (GoF)
 */
public interface IAggregatable extends IRelatable {

    /**
     * Can this be merged with another element of same uniqueKey?
     * False for elements that should never deduplicate.
     */
    boolean isMergeable();

    /**
     * Merge this with another, returning combined result.
     * Called during context merge (e.g., units merging to storey).
     *
     * @param other Element to merge with (same uniqueKey)
     * @return Merged element, or this if not mergeable
     */
    IAggregatable mergeWith(IAggregatable other);

    /**
     * Parent assembly this component belongs to.
     * Maps to: IfcRelAggregates.RelatingObject
     *
     * Example: Stud belongs to wall assembly
     */
    String parentAssembly();

    /**
     * Role of this component within its assembly.
     * Determines ownership vs sharing behavior.
     */
    ComponentRole role();
}

/**
 * Component role within an assembly.
 * Critical for determining boundary behavior.
 */
public enum ComponentRole {
    /** Fully owned by parent, never shared (intermediate stud) */
    INTERNAL,

    /** At assembly boundary, potentially shared (corner stud) */
    BOUNDARY,

    /** Explicitly shared with other assemblies (party wall) */
    SHARED,

    /** Placed by parent but independent entity (hosted door) */
    HOSTED
}
```

#### Layer 5: Semantic Contract

```java
/**
 * Semantic contract for domain-specific validation.
 * Enforces building code, standards, and design rules.
 *
 * Maps to: IfcConstraint, bSDD constraints
 * Theory: Specification pattern (DDD)
 */
public interface IValidatable extends IAggregatable {

    /**
     * Validate against domain rules.
     * Called during compilation and witness generation.
     *
     * @param ctx Validation context with building info
     * @return List of violations (empty if valid)
     */
    List<Violation> validate(ValidationContext ctx);
}

public record Violation(
    ViolationSeverity severity,
    String code,            // e.g., "IRC_R311.7"
    String message,
    String elementGuid
) {}

public enum ViolationSeverity {
    ERROR,      // Must fix, blocks compilation
    WARNING,    // Should fix, generates witness note
    INFO        // Informational only
}
```

### 5.3 LOD400 Dimensional Contracts

LOD400 (fabrication-level) components have additional requirements beyond relationship contracts. They must physically fit within assemblies and connect properly.

**Problem Class:** Different from boundary ownership — this is about dimensional compatibility and tolerances.

| Contract | Purpose | Prevents |
|----------|---------|----------|
| `fitsWithin()` | Component must fit in parent bounds | Oversized components |
| `clearanceTo()` | Required gap to adjacent components | Interference/clashes |
| `connectionPoints()` | Physical attachment locations | Missing connections |

```java
/**
 * Contract for LOD400 physical components.
 * Ensures dimensional compatibility within assemblies.
 *
 * Maps to: IfcShapeRepresentation, IfcMaterialProfile
 * Theory: Interference detection, tolerance analysis
 */
public interface IPhysicalComponent extends IBIMEntity {

    /**
     * Physical dimensions (actual size, not bounding box).
     * For a 90x45mm stud, returns Dimensions(0.09, 0.045, length).
     */
    Dimensions physicalSize();

    /**
     * Required clearance to adjacent component types.
     * From AS 1684: nogging minimum 25mm less than stud depth.
     *
     * @param adjacentType Type of adjacent component
     * @return Required clearance in meters
     */
    double clearanceTo(ComponentType adjacentType);

    /**
     * Physical connection points (nail/bolt locations).
     * For stud-to-plate: 2 connection points per end.
     */
    List<ConnectionPoint> connectionPoints();
}

/**
 * Contract for components that must fit within parent assemblies.
 * Extends IPhysicalComponent with containment requirements.
 */
public interface IAssemblyMember extends IPhysicalComponent {

    /**
     * Parent assembly this component must fit within.
     * Returns assembly ID for bounds checking.
     */
    String fitsWithin();

    /**
     * Fit tolerance — allowed gap between component and parent bounds.
     * Zero means exact fit; positive allows gap.
     *
     * Example: Stud in wall frame allows 2mm gap = 0.002
     */
    double fitTolerance();

    /**
     * Fastener specification for this component.
     * From AS 1684: stud-to-plate = 2× 75mm framing nails.
     */
    FastenerSpec fastenerSpec();
}

public record Dimensions(double width, double depth, double height) {
    public double volume() { return width * depth * height; }
}

public record ConnectionPoint(
    Point3D location,
    ConnectionType type,      // NAIL, BOLT, WELD, ADHESIVE
    String fastenerSpec       // e.g., "75mm_FRAMING_NAIL"
) {}

public record FastenerSpec(
    String type,              // "FRAMING_NAIL", "COACH_SCREW", etc.
    int quantity,             // e.g., 2
    double length,            // e.g., 0.075 (75mm)
    String pattern            // "SKEW", "FACE", "END"
) {}
```

**Validation Example:**

```java
// Verify stud fits within wall frame
IAssemblyMember stud = ...;
IBIMEntity wallFrame = registry.get(stud.fitsWithin());

if (!wallFrame.bounds().contains(stud.bounds(), stud.fitTolerance())) {
    return new Violation(ERROR, "LOD400_FIT",
        "Stud exceeds wall frame bounds by " + overflow + "mm",
        stud.guid());
}
```

### 5.4 Shared Element Registry

```java
/**
 * Central registry for shared elements.
 * Implements Repository pattern (DDD) + Registry pattern (GoF).
 *
 * Elements register here BEFORE being added to assemblies.
 * Assemblies then REFERENCE shared elements, not own them.
 */
public class SharedElementRegistry {

    // Shared junction points (corners, T-junctions, intersections)
    private final Map<String, JunctionPoint> junctions = new ConcurrentHashMap<>();

    // Elements spanning multiple storeys (columns, risers, shafts)
    private final Map<String, ContinuousElement> spanning = new ConcurrentHashMap<>();

    // Shared infrastructure (stacks, mains, risers)
    private final Map<String, SharedInfrastructure> infrastructure = new ConcurrentHashMap<>();

    /**
     * Get or create a junction point at a location.
     * If junction exists within tolerance, returns existing.
     * Otherwise creates new junction and registers it.
     *
     * @param location 3D coordinates
     * @param type Junction type (CORNER, T_JUNCTION, CROSS, etc.)
     * @return Existing or new junction
     */
    public JunctionPoint getOrCreateJunction(Point3D location, JunctionType type) {
        String key = locationKey(location);
        return junctions.computeIfAbsent(key, k ->
            new JunctionPoint(UUID.randomUUID().toString(), location, type)
        );
    }

    /**
     * Get or create a continuous element spanning storeys.
     *
     * @param continuityId Stable ID across storeys (e.g., "COL_A1")
     * @param creator Supplier for new element if not exists
     * @return Existing or new continuous element
     */
    public ContinuousElement getOrCreateContinuous(
            String continuityId,
            Supplier<ContinuousElement> creator) {
        return spanning.computeIfAbsent(continuityId, k -> creator.get());
    }

    /**
     * Register shared infrastructure (called once, referenced many).
     */
    public void registerInfrastructure(String id, SharedInfrastructure infra) {
        infrastructure.put(id, infra);
    }

    /**
     * Generate location key with tolerance.
     * Rounds to nearest millimeter to handle floating point.
     */
    private String locationKey(Point3D p) {
        return String.format("%.3f_%.3f_%.3f", p.x(), p.y(), p.z());
    }
}

/**
 * Shared junction point (corner, T-junction, etc.)
 */
public record JunctionPoint(
    String id,
    Point3D location,
    JunctionType type,
    List<String> connectedElements  // Elements that connect TO this junction
) {
    public JunctionPoint(String id, Point3D location, JunctionType type) {
        this(id, location, type, new ArrayList<>());
    }

    public void addConnection(String elementGuid) {
        connectedElements.add(elementGuid);
    }
}

public enum JunctionType {
    CORNER,         // L-junction (2 walls, 90°)
    T_JUNCTION,     // T-junction (3 walls)
    CROSS,          // Cross junction (4 walls)
    INTERSECTION,   // Pipe/duct intersection
    TERMINATION     // Dead end
}
```

---

## 6. Migration Strategy

### 6.1 Phase 1: Interface Introduction (Non-Breaking)

1. Define interfaces in new package `com.bim.compiler.contract`
2. Existing specs unchanged
3. New code can optionally implement interfaces

### 6.2 Phase 2: Registry Implementation

1. Implement `SharedElementRegistry`
2. Modify `StructuralPlacer` to use registry for corners (already works this way)
3. Add junction registration to `BuildingCompiler`

### 6.3 Phase 3: WallAssemblySpec Migration (Breaking)

1. `WallAssemblySpec` implements `IAggregatable`
2. `ComponentRole.BOUNDARY` for STUD_L, STUD_R
3. `connectsTo()` returns junction references
4. Remove direct stud creation, reference from registry

### 6.4 Phase 4: Merge Deduplication

1. `mergeStoreysAtLevel()` uses `uniqueKey()` for all element types
2. Remove blind `addAll()` concatenation
3. Add deduplication for columns, beams, stairs

### 6.5 Phase 5: Full Contract Enforcement

1. All specs implement `IValidatable`
2. Compile errors for missing contract implementations
3. Witness generation uses contract metadata

---

## 7. Verification

### 7.1 Compile-Time Verification

The Java compiler enforces contracts:

```java
// Compiler error if connectsTo() not implemented
public record NewAssemblySpec(...) implements IAggregatable {
    // ERROR: NewAssemblySpec is not abstract and does not override
    // abstract method connectsTo() in IRelatable
}
```

### 7.2 Runtime Verification

Witness system verifies contract compliance:

```java
// New witness claim
Claim: BOUNDARY_ELEMENTS_SHARED
Evidence:
  - All BOUNDARY role components reference shared junctions
  - No duplicate elements at junction coordinates
  - Junction registry contains all corner/T-junction points
```

### 7.3 Visual Verification

Viewer can highlight contract relationships:
- Color elements by `ComponentRole`
- Show junction points as markers
- Highlight `connectsTo()` relationships as lines

---

## 8. References

### 8.1 Standards

| Standard | Description | Relevance |
|----------|-------------|-----------|
| ISO 16739-1:2018 | Industry Foundation Classes (IFC4) | Relationship entity model |
| ISO 10303-11 | EXPRESS data modeling language | Formal constraint syntax |
| ISO 19650-1:2018 | BIM information management | Federation problem context |

### 8.2 Books

| Reference | Author(s) | Year | Relevance |
|-----------|-----------|------|-----------|
| Domain-Driven Design | Eric Evans | 2003 | Aggregate, Entity, Repository patterns |
| Design Patterns | Gamma, Helm, Johnson, Vlissides | 1994 | Registry, Composite, Flyweight |
| BIM Handbook | Eastman, Teicholz, Sacks, Liston | 2018 | BIM theory and practice |
| Parts and Places | Casati, Varzi | 1999 | Mereotopology theory |
| Patterns of Enterprise Application Architecture | Martin Fowler | 2002 | Identity Map pattern |

### 8.3 Papers

| Title | Authors | Year | Relevance |
|-------|---------|------|-----------|
| Qualitative Spatial Representation and Reasoning with the Region Connection Calculus | Cohn et al. | 1997 | RCC-8 spatial relations |
| Topological Analysis of 3D Building Models | Borrmann, Rank | 2009 | Spatial reasoning in BIM |
| Semantic Web Technologies in AEC Industry | Pauwels et al. | 2017 | Linked data for BIM |
| EXPRESS to OWL for Construction Industry | Pauwels, Terkaj | 2016 | Formal semantics |

### 8.4 Related Project Documents

| Document | Relevance |
|----------|-----------|
| `FOSS_DEVELOPER_GUIDE.md` | Development practices, witness system |
| `bim-dsl-dictionary.md` | DSL syntax and semantics |
| `ENRICHMENT_PLAN.md` | IFC extraction patterns, dependency rules |
| `IFC_research_files/ANALYSIS_FOR_BIM_COMPILER.md` | Extracted relationship patterns |
| `lod400-assemblies-extracted.md` | Assembly structure from TERMINAL |

---

## 9. Glossary

| Term | Definition |
|------|------------|
| **Boundary Ownership** | Anti-pattern where assemblies create their own boundary elements instead of referencing shared ones |
| **Continuity ID** | Identifier that persists across contexts (storeys) for spanning elements |
| **Fit Tolerance** | Allowed gap between LOD400 component and its parent assembly bounds |
| **Junction Point** | Shared point where multiple elements connect (corner, T-junction) |
| **LOD400** | Level of Development 400 — fabrication-level detail with exact dimensions, fasteners, connections |
| **Mereotopology** | Formal theory combining part-whole relations with spatial topology |
| **RCC-8** | Region Connection Calculus defining 8 exhaustive spatial relationships |
| **Unique Key** | Identifier for deduplication within a single context |

---

## 10. Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-03 | Claude + User | Initial specification |

---

*This document establishes the theoretical and practical foundation for compile-time relationship enforcement in the BIM Intent Compiler. Implementation should proceed according to the migration strategy, with each phase verified against the existing witness system.*
