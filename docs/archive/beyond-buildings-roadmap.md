# Beyond Buildings: Domain Extension Roadmap

**Version:** 1.0  
**Date:** February 2026  
**Status:** Strategic Guidance  
**Companion to:** `compound-enrichment-model.md`, `ARCHITECTURE.md`  
**Trigger:** Phase 95B proved floor plate layout is BOM configuration, not code  
**Principle:** Pure Core, Dynamic Vocabulary → Domain-Agnostic Compilation

---

## Thesis

The BIM Intent Compiler is not a building compiler. It is an **intent-to-geometry compiler** whose current vocabulary happens to be buildings. Phase 95B proved the critical claim: even spatial layout — the most "building-specific" concern — is resolvable from BOM metadata. This means the core engine (DAG pipeline, witness system, contract architecture, BOM resolver) can serve any domain where:

1. A structured intent language can express what is wanted
2. Component placement follows rules derivable from reference data
3. Correctness is provable through mathematical witnesses
4. Output is an industry-standard data model (IFC, STEP, CityGML)

This document provides actionable guidance for extending the compiler into infrastructure, industrial, and mechanical domains — with honest assessment of what transfers, what needs new primitives, and what reference data must be acquired.

---

## The Generalization: From SPACE to ELEMENT-in-CONTEXT

### What SPACE Actually Is

SPACE is not "a room." SPACE is a **typed container with constraints, components, and services**. The iDempiere Document parallel holds:

```
SPACE (typed container)
    ├── SpaceType (determines behavior)
    ├── Components (contents — walls, fixtures, MEP)
    ├── Constraints (relationships to other SPACEs)
    └── Events (downstream: BOM, IFC, witness claims)
```

This pattern transfers to any domain by substituting the primitive:

| Domain | Primitive | Equivalent to SpaceType | Constraint Grammar |
|--------|-----------|-------------------------|-------------------|
| Buildings | SPACE | BEDROOM, CORRIDOR, SHAFT | Adjacency, exterior, stacking |
| Bridges | SEGMENT | DECK, PIER, ABUTMENT, BEARING | Sequence, span limits, clearance |
| Roads | SECTION | CARRIAGEWAY, SHOULDER, MEDIAN | Alignment, superelevation, drainage |
| Process Plant | PROCESS_ZONE | REACTOR_BAY, PIPE_RACK, CONTROL_ROOM | Flow, clearance, hazard class |
| Machinery | ASSEMBLY | GEARBOX, FRAME, SHAFT, HOUSING | Kinematic chain, tolerance stack |
| Landscape | ZONE | PLANTING, HARDSCAPE, DRAINAGE | Slope, grading, ecology |
| Marine | COMPARTMENT | CARGO_HOLD, ENGINE_ROOM, BRIDGE | Stability, fire zone, escape route |

### The Unifying Abstraction

All domains share this compilation pattern:

```
Intent DSL → Parse → Resolve Constraints → Compile Geometry → Place Components → Verify → Output
     ↑           ↑              ↑                  ↑                 ↑              ↑
  domain      grammar      solver type         geometry         BOM metadata    witness
  vocab        rules        (spatial,          primitive          params        claims
  (YAML)                    linear,           (volume,                        (domain-
                            flow)              profile,                        specific)
                                               swept)
```

The **bold items** change per domain. The pipeline itself does not.

---

## Domain Ladder: Sequenced Extension Plan

### Tier 0 — Already Proven (Buildings)

**Status:** Operational  
**Primitives:** SPACE, GRID, STOREY, ENVELOPE  
**Reference data:** TERMINAL (51K+ LOD400 elements)  
**Witness library:** 16+ proven claims  

Near-term building typologies (clinic, office, warehouse, mosque) are **vocabulary exercises** per the compound enrichment model. No roadmap action required — follow the addon framework.

---

### Tier 1 — Vocabulary Extension Only (No New Primitives)

These domains use SPACE as-is with new SpaceTypes and constraint patterns.

#### 1a. Multi-Building Campus / Master Planning

**New vocabulary:** SITE_ZONE, SETBACK, PARKING_LOT, ACCESS_ROAD, UTILITY_CORRIDOR  
**Constraint type:** Site-level adjacency + regulatory setbacks  
**BOM resolves:** Building footprint placement, parking layout, utility routing  
**Witness claims:** Plot ratio compliance, parking provision ratio, fire engine access  

**What's needed:**
- [ ] SITE primitive (container of BUILDINGs, analogous to STOREY containing SPACEs)
- [ ] Site-level constraint solver (setbacks from boundary, building separation)
- [ ] Malaysian planning standards: [RESEARCHED: Garis Panduan Perancangan — JPBD]
- [ ] Reference data: PENDING — need a site plan IFC or surveyed campus layout

**Effort estimate:** Medium — solver extension, no geometry engine change

#### 1b. Interior Fit-Out / Renovation

**New vocabulary:** PARTITION (non-structural), RAISED_FLOOR, SUSPENDED_CEILING, FURNITURE_ZONE  
**Constraint type:** Same adjacency grammar, lighter structural rules  
**BOM resolves:** Partition placement, ceiling grid, furniture layout  
**Witness claims:** Occupancy load, means of escape, accessibility compliance  

**What's needed:**
- [ ] Non-structural wall type (demountable partition vs permanent)
- [ ] Ceiling grid BOM (analogous to floor plate BOM from Phase 95B)
- [ ] Furniture addon vocabulary (WORKSTATION, MEETING_TABLE — placement rules from BIFMA standards)
- [ ] Reference data: Any furnished IFC model with LOD300+ furniture

**Effort estimate:** Low — almost entirely vocabulary and BOM configuration

---

### Tier 2 — New Constraint Solver Required

These domains require extending the constraint resolution beyond 2D adjacency.

#### 2a. Simple Bridges (Beam / Precast)

**New primitive:** SEGMENT (linear element in a sequence)  
**Grammar:** Linear chain: ABUTMENT → SPAN → PIER → SPAN → ABUTMENT  
**BOM resolves:** Girder sizing, bearing pads, deck reinforcement, expansion joints  
**Witness claims:** Span/depth ratio, clearance envelope, bearing capacity, deflection limit  

**What's needed:**
- [ ] SEGMENT primitive with `sequence:` constraint (ordered, not adjacency)
- [ ] Linear constraint solver (span ≤ max for girder type, pier spacing)
- [ ] Bridge cross-section BOM (analogous to floor plate BOM)
  - `COMPOSITE_DECK_BOM`: steel girders + shear studs + concrete slab + waterproofing + wearing surface
  - Each child has spatial params: depth, cover, layer thickness
- [ ] Profile: Malaysian_Bridge based on JKR Bridge Design Standards (Arahan Teknik (Jalan) 11/87)
- [ ] **Reference IFC:** Need a bridge model in IFC 4.3 with IfcBridge entities
  - **Source options:**
    - buildingSMART IFC 4.3 Bridge sample files (check buildingsmart.org/ifc-bridge/)
    - Nordic bridge projects (Sweden/Norway have published IFC bridge pilots)
    - French CEREMA bridge IFC pilot data
    - Korean IFC bridge implementations (KBIMS project)
  - **PRIME RULE applies:** No reference model → no extraction → mark ALL bridge constants as [PENDING]

**Effort estimate:** High — new solver type + geometry primitive, but BOM pattern transfers directly

#### 2b. Culverts and Retaining Walls

**New primitive:** Reuse SEGMENT (linear, simpler than bridge)  
**Grammar:** Culvert: HEADWALL → BARREL → HEADWALL. Retaining: FOOTING → STEM → optional COUNTERFORT  
**BOM resolves:** Precast unit selection, reinforcement, joint sealant  
**Witness claims:** Hydraulic capacity (culvert), sliding/overturning stability (retaining wall)  

**What's needed:**
- [ ] Precast culvert BOM with size variants (600mm, 900mm, 1200mm box sections)
- [ ] Retaining wall BOM: L-wall, T-wall, counterfort variants
- [ ] Standards: JKR Retaining Wall Design Manual, Malaysian drainage standards
- [ ] **Reference IFC:** Simpler than bridge — a precast culvert is essentially a linear BOM
  - Manufacturer product data (Hume Concrete, Rocla) provides dimensions and reinforcement
  - [PENDING: No IFC reference model known for Malaysian culverts]
  - **Workaround:** Compose from TERMINAL structural patterns — a culvert barrel is geometrically similar to a structural frame segment. Extract wall thickness and reinforcement patterns, adapt.

**Effort estimate:** Medium — piggbacks on bridge SEGMENT primitive

---

### Tier 3 — New Geometry Engine Required

These domains need fundamentally different geometric operations.

#### 3a. Roads and Highways

**New primitive:** ALIGNMENT + CROSS_SECTION (swept profile along 3D curve)  
**Grammar:** Horizontal alignment (straights + curves + spirals) × Vertical profile (grades + curves) × Cross-section template (lanes, shoulders, drainage, pavement layers)  
**BOM resolves:** Pavement layer thicknesses, drainage components, road furniture (signs, barriers, markings)  
**Witness claims:** Sight distance, superelevation, drainage gradient, pavement structural number  

**What's needed:**
- [ ] **Alignment primitive** — this is the critical missing capability
  - Horizontal: clothoid spirals, circular curves
  - Vertical: parabolic curves
  - Combined: superelevation, widening on curves
  - This is non-trivial engineering geometry. Bentley OpenRoads and Civil 3D have separate engines for this.
- [ ] Cross-section template system (analogous to floor plate BOM but 2D profile swept along path)
- [ ] Terrain model interaction: cut/fill earthworks calculation
- [ ] Standards: JKR Arahan Teknik (Jalan) series — road geometry, pavement design (JKR/HPU)
- [ ] **Reference IFC:** IFC 4.3 includes IfcAlignment
  - **Source options:**
    - IFC Infra project sample files (buildingsmart.org/standards/rooms/infrastructure/)
    - Finnish Transport Infrastructure Agency (Väylävirasto) IFC road pilots
    - Korean LandXML/IFC road conversion projects
    - FHWA (US) OpenBridge/OpenRoads IFC export samples
  - LandXML is an alternative/complementary format widely used in road design

**Effort estimate:** Very High — alignment geometry is a separate engineering discipline. Consider whether to build or integrate (e.g., use LandXML import and focus on BOM/witness value-add).

**Strategic note:** The compiler's unique value for roads is NOT the geometry (Civil 3D does this). It's the **BOM integration + witness verification**. A road where every pavement layer traces to a verified BOM, with structural number proofs and drainage witnesses, is valuable even if the alignment geometry is imported.

#### 3b. Process / Industrial Plant

**New primitive:** PROCESS_ZONE + EQUIPMENT + PIPE_RUN  
**Grammar:** Process flow graph (not spatial adjacency). Material enters → transforms through equipment → exits. Piping connects equipment. Zones classify hazard areas.  
**BOM resolves:** Equipment specification, piping material/diameter, instrumentation, structural supports  
**Witness claims:** Mass balance, pressure drop, hazardous area extent (ATEX/IEC zones), escape route from classified areas  

**What's needed:**
- [ ] Process flow graph as constraint system (fundamentally different from spatial/linear)
- [ ] Equipment placement with clearance envelopes (maintenance access, crane reach)
- [ ] Piping routing (this alone is a major engineering challenge — see AVEVA, SmartPlant 3D)
- [ ] Hazardous area classification zones (concentric volumes around leak sources)
- [ ] Standards: DOSH (Malaysia) Process Safety Management, IEC 60079 (hazardous areas)
- [ ] **Reference IFC:** Process plant IFC models are rare
  - **Source options:**
    - CFIHOS (Capital Facilities Information Handover Specification) sample data
    - ISO 15926 reference data (process plant lifecycle)
    - Open-source P&ID data from academic process engineering projects
    - AVEVA/Hexagon sample plant exports (if available in IFC format)
  - **Honest assessment:** Process plant is the hardest domain. The DSL would need to express process intent (flowsheet) not just spatial intent. This is closer to process simulation (HYSYS, ASPEN) than BIM.

**Effort estimate:** Very High — essentially a parallel compiler for process intent. Consider partnership with process engineering domain experts.

#### 3c. Marine Structures

**New primitive:** COMPARTMENT (3D volumetric, not floor-based)  
**Grammar:** Hull subdivision into watertight compartments. Fire zones. Escape routes in 3D (not just horizontal).  
**BOM resolves:** Steel plate/stiffener schedules, pipe penetration fire stops, equipment foundations  
**Witness claims:** Damage stability, fire zone integrity, escape route compliance (SOLAS)  

**What's needed:**
- [ ] 3D volumetric space definition (buildings are 2.5D — storeys with heights. Ships are true 3D)
- [ ] Hull form geometry (NURBS surfaces — different from planar BIM geometry)
- [ ] Standards: IMO SOLAS, classification society rules (Lloyd's, DNV, BV)
- [ ] **Reference data:** Ship design uses its own formats (IGES, STEP AP216/AP218)
  - **Honest assessment:** Marine is the furthest from current architecture. Ship structural design tools (NAPA, AVEVA Marine) are highly specialized. The compiler's value here would be limited without deep domain partnership.

**Effort estimate:** Extreme — new geometry, new standards, new industry. Not recommended near-term.

---

### Tier 4 — Mechanical / Manufacturing (Non-AEC)

#### 4a. Modular Construction Components

**New primitive:** Reuse ASSEMBLY (already exists as BOM concept)  
**Grammar:** Mechanical assembly tree: FRAME → PANEL → CONNECTOR → SEAL  
**BOM resolves:** Already the native strength — this IS a BOM  
**Witness claims:** Tolerance stack-up, structural capacity of module, lifting point verification  

**What's needed:**
- [ ] Parametric component library (bolt patterns, connection details)
- [ ] Tolerance analysis witness (cumulative dimensional error ≤ allowable)
- [ ] Standards: Modular construction standards (e.g., Modular Building Institute, PPVC guidelines Singapore/HK)
- [ ] **Reference data:** Manufacturer DfMA (Design for Manufacture and Assembly) catalogues
  - Steel connection details from SCI (Steel Construction Institute) publications
  - Precast concrete connection details from PCI (Precast/Prestressed Concrete Institute)

**Effort estimate:** Medium — strong alignment with existing BOM architecture. Natural extension from building → building components → prefab modules.

**Strategic note:** This is possibly the highest-value near-term extension. DfMA/PPVC (Prefabricated Prefinished Volumetric Construction) is mandated in Singapore and gaining traction in Malaysia. A compiler that produces verified modular construction BOMs with tolerance proofs fills an immediate market need.

#### 4b. Production Line / Factory Layout

**New primitive:** WORKSTATION + MATERIAL_FLOW  
**Grammar:** Process routing: Raw material → Station A → Station B → QC → Pack → Ship  
**BOM resolves:** Equipment placement, conveyor routing, utility connections  
**Witness claims:** Throughput capacity, ergonomic reach zones, safety clearances  

**What's needed:**
- [ ] Material flow graph (similar to process plant but discrete manufacturing)
- [ ] Equipment clearance envelopes (similar to PROCESS_ZONE)
- [ ] Standards: OSHA/DOSH workplace safety, ISO 45001
- [ ] **Reference data:** Factory layout CAD models — more common than process plant IFC
  - AutoCAD factory layouts widely available as DWG/DXF

**Effort estimate:** High — flow graph solver needed, but simpler than continuous process plant

---

## Reference IFC Acquisition Strategy

### The PRIME RULE for New Domains

**No reference model → No extraction → No constants → No compilation**

Every domain extension MUST begin with acquiring validated reference data. The TERMINAL dataset took the building domain from zero to operational. Each new domain needs its own "TERMINAL moment."

### Acquisition Priority Matrix

| Priority | Domain | Reference Format | Known Sources | Difficulty |
|----------|--------|-----------------|---------------|------------|
| **1** | Modular Construction | IFC + shop drawings | DfMA manufacturers, PPVC projects | Low — industry moving to digital |
| **2** | Simple Bridges | IFC 4.3 + structural drawings | Nordic bridge pilots, KBIMS Korea | Medium — IFC 4.3 adoption growing |
| **3** | Culverts/Retaining | Manufacturer data + standards | Hume, Rocla catalogues; JKR manuals | Low — parametric, few variants |
| **4** | Roads | IFC 4.3 / LandXML | Finnish/Nordic infra pilots | Medium — format maturity varies |
| **5** | Campus/Site | Site plan IFC or GIS | Local authority submissions | Medium — format standardisation low |
| **6** | Process Plant | ISO 15926 / CFIHOS | Academic + AVEVA samples | High — proprietary data dominant |
| **7** | Marine | STEP AP218 / IGES | Classification society archives | Very High — niche industry |

### Extraction Protocol per Domain

For each new reference model:

1. **Inventory:** Count elements by type, discipline, LOD level (as done for TERMINAL: 51K elements, 9 disciplines)
2. **Extract patterns:** Component relationships, typical assemblies, dimension rules
3. **Cross-reference standards:** Every extracted dimension must match published code/standard
4. **Document provenance:** Every constant tagged [EXTRACTED: {source model}] or [RESEARCHED: {standard}]
5. **Build test case:** One simple instance compiled and verified before scaling

---

## Solver Evolution Path

### Current: 2D Spatial Adjacency (Choco CSP)

Works for buildings. Rooms placed on integer grid with adjacency/exterior constraints.

### Next: Linear Sequencing

For bridges and culverts. Elements placed along a 1D axis with spacing and span constraints. Simpler than 2D — could be a degenerate case of the existing solver.

```
Solver type: LINEAR
Constraints:
  span: max_length for element type
  sequence: A before B (ordered chain)
  clearance: min_gap between elements
  support: pier_at every N segments
```

### Future: Alignment-Following

For roads. Elements placed along a 3D alignment curve. Cross-section resolved per station. This requires:

- Alignment geometry library (clothoid, parabola, circular arc)
- Station-based query: "what is the cross-section at chainage 1+250?"
- Superelevation interpolation between tangent and curve

### Speculative: Flow Graph

For process/manufacturing. Not spatial placement but topological ordering with spatial constraints (equipment footprint + clearance). This is a hybrid: graph topology constrains what connects to what, spatial solver places physical equipment.

---

## Witness System Extension by Domain

### Universal Claims (Transfer Directly)

These witness claim patterns apply across ALL domains:

| Claim Pattern | Building Example | Bridge Example | Road Example |
|--------------|-----------------|----------------|--------------|
| DIMENSIONAL_COMPLIANCE | Room ≥ min area | Span ≤ max for type | Lane ≥ min width |
| MATERIAL_CONTINUITY | Wall thickness consistent | Deck depth continuous | Pavement layer continuous |
| CONNECTION_COMPLETE | All doors connect spaces | All bearings seat on supports | All drainage reaches outfall |
| BOM_RECONCILIATION | Component count matches | Girder count matches | Layer thickness matches |
| CLEARANCE_VERIFIED | Headroom ≥ 2100mm | Navigation clearance ≥ required | Vertical clearance ≥ 4.5m |

### Domain-Specific Claims (New Per Domain)

| Domain | New Witness Claims | Verification Method |
|--------|-------------------|-------------------|
| Bridge | SPAN_DEPTH_RATIO, BEARING_CAPACITY, SEISMIC_RESTRAINT | Structural calculation |
| Road | SIGHT_DISTANCE, SUPERELEVATION, STRUCTURAL_NUMBER | Geometric + pavement analysis |
| Plant | MASS_BALANCE, HAZARD_ZONE_EXTENT, ESCAPE_FROM_ZONE | Process + safety calculation |
| Modular | TOLERANCE_STACK, LIFTING_CAPACITY, CONNECTION_FORCE | Mechanical calculation |

### Witness Taxonomy Evolution

```
WitnessClaim
├── UNIVERSAL (all domains)
│   ├── DIMENSIONAL_COMPLIANCE
│   ├── MATERIAL_CONTINUITY
│   ├── CONNECTION_COMPLETE
│   └── BOM_RECONCILIATION
│
├── DOMAIN_SPECIFIC
│   ├── BUILDING (current)
│   │   ├── FIRE_TRAVEL_DISTANCE
│   │   ├── PLUMBING_COMPLETE
│   │   └── STRUCTURAL_GRID_VALID
│   │
│   ├── BRIDGE (Tier 2a)
│   │   ├── SPAN_DEPTH_RATIO
│   │   └── CLEARANCE_ENVELOPE
│   │
│   ├── ROAD (Tier 3a)
│   │   ├── SIGHT_DISTANCE
│   │   └── DRAINAGE_GRADIENT
│   │
│   └── MODULAR (Tier 4a)
│       ├── TOLERANCE_STACK
│       └── LIFTING_POINT_CAPACITY
│
└── CONFIGURATION_SPECIFIC
    ├── per profile (Malaysian vs IBC)
    └── per protocol (primary school vs secondary)
```

---

## IFC Schema Coverage by Domain

### What IFC 4.3 Provides

| Domain | IFC Entities | Schema Maturity |
|--------|-------------|-----------------|
| Buildings | IfcWall, IfcSlab, IfcSpace, IfcBeam... | Mature (IFC 2x3+) |
| Bridges | IfcBridge, IfcBridgePart, IfcTendon | New in 4.3 — limited tooling |
| Roads | IfcRoad, IfcCourse, IfcPavement | New in 4.3 — limited tooling |
| Railways | IfcRailway, IfcRail, IfcTrackElement | New in 4.3 — limited tooling |
| Marine | IfcMarineFacility, IfcMarinePart | New in 4.3 — very limited |
| Process Plant | Not in IFC (ISO 15926 domain) | N/A |
| Machinery | IfcMechanicalFastener (limited) | Minimal — STEP AP203/AP214 better |

### Output Strategy by Domain

| Domain | Primary Output | Secondary Output |
|--------|---------------|-----------------|
| Buildings | IFC 2x3/4.0 | SQLite BOM DB |
| Bridges | IFC 4.3 | SQLite BOM DB + structural calc report |
| Roads | IFC 4.3 + LandXML | SQLite BOM DB + earthworks quantities |
| Process Plant | SQLite BOM DB | P&ID data (ISO 15926) |
| Modular/Mechanical | IFC + STEP AP214 | SQLite BOM DB + tolerance report |

---

## Implementation Phases

### Phase A: Consolidate Building Domain (Current → Q3 2026)

- [ ] Complete building typology ladder: clinic, office, warehouse
- [ ] Prove compound enrichment model: 6th building type as YAML-only exercise
- [ ] Mature addon framework to production quality
- [ ] Stabilise witness claim taxonomy
- **Gate:** 5+ building types with full witness coverage before extending to new domains

### Phase B: Modular Construction Extension (Recommended First Non-Building Domain)

- [ ] Acquire DfMA reference data from PPVC manufacturer
- [ ] Define ASSEMBLY primitive (or confirm BOM tree is sufficient)
- [ ] Implement tolerance stack witness
- [ ] Create MODULE_VOLUMETRIC BOM template
- [ ] Profile: Malaysian_PPVC based on CIDB IBS guidelines
- **Gate:** One modular building compiled with connection detail and tolerance proof

### Phase C: Simple Bridge Extension

- [ ] Acquire bridge reference IFC (Nordic or Korean pilot data)
- [ ] Implement SEGMENT primitive + linear constraint solver
- [ ] Create bridge cross-section BOM templates (composite deck, precast prestressed)
- [ ] Implement span/depth ratio and clearance witnesses
- [ ] Profile: Malaysian_Bridge based on JKR Arahan Teknik
- **Gate:** One beam bridge compiled with structural witnesses and BOM

### Phase D: Infrastructure Foundations (Culvert + Retaining Wall)

- [ ] Compose from bridge SEGMENT primitive
- [ ] Culvert BOM from precast manufacturer data
- [ ] Retaining wall BOM from standard design charts
- [ ] Hydraulic capacity witness (culvert), stability witness (retaining wall)
- **Gate:** Standard culvert and retaining wall compiled with engineering proofs

### Phase E: Road Geometry (Requires Alignment Engine)

- [ ] Evaluate: build alignment engine vs import LandXML/IFC alignment
- [ ] Implement cross-section template BOM system
- [ ] Pavement structural number witness
- [ ] Drainage and superelevation witnesses
- **Gate:** Simple road segment with verified pavement layers and drainage

### Phase F: Site/Campus (Requires Site-Level Solver)

- [ ] Site boundary and setback constraints
- [ ] Building footprint placement on site grid
- [ ] Parking and access road layout from BOM
- [ ] Planning compliance witnesses
- **Gate:** Simple campus with 3+ buildings, parking, and site services

---

## Decision Framework: Build vs Import vs Partner

For each domain extension, evaluate:

| Question | Build | Import | Partner |
|----------|-------|--------|---------|
| Does reference data exist in extractable format? | Yes → Build | Partial → Import + Enrich | No → Partner for data |
| Is the geometry within current engine capability? | 2D/2.5D → Build | Needs alignment → Import geometry, add BOM/witness | Needs NURBS/freeform → Partner |
| Does the compiler add unique value? | BOM + Witness → Build | BOM only → Import geometry, add value layer | Neither → Don't extend |
| Is there a Malaysian/ASEAN market need? | Immediate → Build | Growing → Plan | Niche → Deprioritize |

### The Value Test

The compiler's unique contribution is **not geometry generation** (CAD tools do this). It is:

1. **Intent-to-BOM compilation** — from what you want to what you need to buy
2. **Mathematical proof of correctness** — witness claims, not visual inspection
3. **Deterministic reproducibility** — same intent always produces same output
4. **Accessibility** — DSL is readable; no CAD expertise required

If a domain extension doesn't deliver at least two of these four, it's not worth pursuing.

---

## Risk Register

| Risk | Impact | Mitigation |
|------|--------|------------|
| No reference IFC for target domain | Cannot extract → blocks extension | Acquire before any code work. No TERMINAL, no go. |
| Alignment geometry too complex to build | Road extension stalls | Import alignment from LandXML, add BOM/witness value on top |
| Domain standards fragmented across countries | Profile system explodes | Start with ONE jurisdiction (Malaysia/JKR) per domain, expand later |
| Process plant requires different paradigm | Wasted effort on wrong architecture | Evaluate separately — may need companion compiler, not extension |
| Scope creep into geometry engine work | Core building domain neglected | Gate: 5+ building types before ANY non-building work |

---

## Success Metrics

| Milestone | Metric | Target |
|-----------|--------|--------|
| Building maturity | Typologies with full witness coverage | ≥ 5 by Q3 2026 |
| First non-building domain | Modular construction BOM + tolerance witness | Q4 2026 |
| Infrastructure entry | Simple bridge compiled with structural proof | H1 2027 |
| Domain vocabulary | Total SpaceType equivalents across all domains | ≥ 50 |
| Compound enrichment | Effort for Nth domain type vs (N-1)th | Measurably decreasing |
| Reference data library | Extracted reference models with provenance | ≥ 3 domains |

---

## Conclusion

Phase 95B didn't just solve floor plate layout. It proved that the most geometry-specific concern in the compiler is actually a **metadata configuration problem**. If floor plates are BOMs, then bridge cross-sections are BOMs. Road pavements are BOMs. Equipment layouts are BOMs.

The architecture was built for this. SPACE generalizes to ELEMENT-in-CONTEXT. The constraint solver generalizes from adjacency to sequencing to flow. The witness system generalizes from fire travel distance to span/depth ratio to tolerance stack-up. The BOM resolver generalizes from floor plates to cross-sections to assembly trees.

The barriers are not architectural. They are:

1. **Reference data** — acquire a TERMINAL-equivalent per domain
2. **Solver variants** — linear and flow solvers alongside spatial
3. **Domain witnesses** — engineering calculations, not just geometric proofs
4. **Standards knowledge** — JKR road standards alongside UBBL building codes

The sequencing is clear: consolidate buildings first (5+ types), then modular construction (closest to current BOM strength), then simple bridges (linear BOM with structural witnesses), then infrastructure (alignment geometry). Each step enriches the next through compound vocabulary accumulation.

The PRIME RULE governs all of it: **extract, don't imagine.** No reference model, no extraction, no constants, no compilation. This constraint is the quality guarantee that makes the system trustworthy across any domain it enters.

---

*"Pure core, dynamic vocabulary" is not just a building architecture pattern. It is a domain-agnostic compilation model.*

---

*Beyond Buildings Roadmap v1.0*  
*Companion to: Compound Enrichment Model v1.0, Architecture v3.0*  
*Date: February 2026*  
*Watchdog-reviewed*
