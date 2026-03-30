# Standards-Driven Spatial Compilation — The Pattern Beyond BIM

**Date:** 2026-03-30
**Status:** Strategic analysis — the next defining moment

---

## The Pattern

Three things converged in P125-P132 that reveal something bigger than BIM:

```
IFCtoBOM                    DISC_VALIDATION              COMPILATION
─────────                   ─────────────────            ───────────
Extract structure           Validate against standard    Compile + prove
from domain input           via abstract rules           with GEO evidence
                ↘               ↓                    ↙
                    ABSTRACT BOM (m_bom + m_bom_line)
                    with tack offsets + product refs
                    + jurisdiction-scoped rules
```

**The insight:** This is not a BIM pipeline. It is a **standards-driven spatial compilation engine.** The three components are domain-agnostic:

1. **Extract** — Decompose a real thing into a recursive BOM with spatial offsets (IFC, OCX, DXF, point cloud — format doesn't matter)
2. **Validate** — Check every product, placement, and relationship against a rule set (AD_Val_Rule with jurisdiction scope — the standard doesn't matter)
3. **Compile + Prove** — Produce verified spatial output with a machine-checkable proof chain (GEO CHAIN/DIMS/CONTAIN/DRIFT — the domain doesn't matter)

The engine has no concept of "building." It has concepts of:
- **Parent-child assembly** (m_bom → m_bom_line)
- **Spatial placement** (dx/dy/dz tack offsets)
- **Product catalog** (M_Product with dimensions + geometry)
- **Compliance rule** (AD_Val_Rule with jurisdiction + threshold)
- **Proof** (GEO evidence chain, 6 mathematical gates)

Any domain that has **manufactured assemblies + governing standards + spatial placement** fits this pattern.

---

## Where Standards Exist But Spatial Compilation Doesn't

Every professional engineering domain has:
- A standard body (ISO, NFPA, DNV, ASME, FDA, NRC...)
- A catalog of products with governed dimensions
- Assembly rules (what connects to what, minimum clearances, material compatibility)
- Inspection requirements (prove the assembly meets the standard)

None of them have a **compilation engine** that takes a BOM and produces verified spatial output. They all do it manually or with domain-locked tools.

### Domain Map

| Domain | Standard Body | Assembly Type | Products | Spatial Rules | Current Tools |
|--------|--------------|---------------|----------|--------------|---------------|
| **Construction** | UBBL, IBC, Eurocode | Building → floor → room → element | Walls, slabs, doors, MEP | Min room size, fire separation, egress width | Revit, ArchiCAD (visual, no BOM) |
| **Marine** | DNV, Lloyd's, BV, IACS | Vessel → section → panel → plate/stiffener | Hull plates, bulb flats, brackets | Min scantling, section modulus, corrosion margin | NAPA, Cadmatic (domain-locked) |
| **Pharma/GMP** | FDA 21 CFR, EU GMP Annex 1 | Facility → zone → room → equipment | Clean rooms, HVAC, pass-throughs, autoclaves | Air pressure cascade, particle count, room classification | No standard tool — manual |
| **Nuclear** | NRC 10 CFR, IAEA | Plant → building → containment → system | Reactor vessel, piping, shielding, ventilation | Radiation shielding thickness, seismic qualification, redundancy | Custom per plant — no reuse |
| **Aerospace interior** | FAA 14 CFR 25, EASA CS-25 | Aircraft → cabin zone → section → seat/galley/lavatory | Seats, monuments, hatbins, PSU, lighting | Seat pitch, aisle width, emergency exit proximity, fire rating | Dassault/CATIA (domain-locked) |
| **Data centre** | TIA-942, ASHRAE TC 9.9, EN 50600 | Facility → hall → row → rack → device | Racks, raised floors, PDUs, cooling, cable trays | Power density W/m2, cooling capacity, redundancy tier | DCIM tools (no spatial BOM) |
| **Mining/tunnel** | AS 4825, NFPA 502, EN 13501 | Tunnel → segment → ring → panel | Precast rings, ventilation, fire doors, refuge chambers | Ventilation velocity, fire separation, escape distance | No standard tool |
| **Oil & gas** | API 650/620, ASME B31.3, NFPA 30 | Facility → unit → equipment → piping | Vessels, exchangers, pumps, pipe runs | Spacing (API 2510), blast radius, fire zones | SmartPlant (domain-locked) |
| **Rail infrastructure** | EN 13848, UIC 719, EN 13674 | Line → segment → track → component | Rail, sleepers, fasteners, switches, signals | Gauge, cant, alignment radius, signal spacing | Bentley OpenRail (domain-locked) |
| **Modular construction** | ANSI/MBI 202, EN 14992 | Building → module → room → element | Volumetric modules, corridor connections, service risers | Module dimensions, lifting points, transport clearance | No standard tool |

### What They All Share

Every row in that table has the same three needs:
1. **"Give me a recipe"** → BOM with parent-child + spatial offsets
2. **"Check it against the rules"** → AD_Val_Rule with jurisdiction scope
3. **"Prove every piece is in the right place"** → GEO evidence chain

And none of them have a tool that does all three. They have:
- CAD tools that place geometry without proof chains
- Compliance tools that check finished models without compilation
- ERP tools that manage materials without spatial placement

The BIM Compiler is the only engine that combines all three in a single pipeline.

---

## The iDempiere Model as Universal Manufacturing Pattern

The key architectural decision that enables domain expansion is the iDempiere ERP model:

| iDempiere Concept | BIM Usage | Universal Meaning |
|-------------------|-----------|-------------------|
| C_Order | Construction order (the building) | A work order for any manufactured assembly |
| C_OrderLine | What to build (room, discipline) | A line item in any work order |
| M_Product | A construction product (wall, pipe) | Any manufactured component with dimensions |
| M_Product_Category | IFC class (IfcWall, IfcBeam) | Any product taxonomy |
| M_BOM | Assembly recipe (floor plan) | Any recursive bill of materials |
| M_BOM_Line | Child with dx/dy/dz offset | Any component placement in an assembly |
| AD_Val_Rule | UBBL room size rule | Any compliance rule from any standard |
| AD_Org | Discipline (ARC, STR, FP) | Any organizational division or trade |
| PP_Order_Node | Verb execution (TILE, ROUTE) | Any manufacturing operation |
| DocAction | DR→IP→CO lifecycle | Any workflow state machine |

This is not a metaphor. The M_BOM table does not know it's describing a building. It describes a parent with children at spatial offsets. A ship hull section is the same data structure. A pharmaceutical clean room is the same data structure. A nuclear containment vessel is the same data structure.

The engine compiles M_BOMs. The domain is just data in M_Product_Category.

---

## The Standards Integration Pattern

DISC_VALIDATION_DB_SRS §10.4 established the pattern for discipline-specific validation:

```
AD_Val_Rule (jurisdiction-scoped)
  ├── rule_key: "MIN_ROOM_AREA"
  ├── jurisdiction: "MY" (UBBL) / "US" (IBC) / "UK" (ADB)
  ├── threshold: 3000 (mm)
  ├── comparator: ">="
  ├── error_level: "BLOCK"
  └── citation: "UBBL 1984 §39(1)"
```

This exact pattern works for any standard:

```
AD_Val_Rule
  ├── rule_key: "MIN_PLATE_THICKNESS"
  ├── jurisdiction: "DNV" (class rules)
  ├── threshold: 8.0 (mm)
  ├── comparator: ">="
  ├── error_level: "BLOCK"
  └── citation: "DNV Rules Pt.3 Ch.1 §3.2.1"
```

```
AD_Val_Rule
  ├── rule_key: "CLEAN_ROOM_PRESSURE_CASCADE"
  ├── jurisdiction: "FDA" (21 CFR)
  ├── threshold: 15.0 (Pa positive)
  ├── comparator: ">="
  ├── error_level: "BLOCK"
  └── citation: "FDA Guidance for Industry, Sterile Drugs §V.B"
```

```
AD_Val_Rule
  ├── rule_key: "SEAT_PITCH_MIN"
  ├── jurisdiction: "FAA" (14 CFR 25)
  ├── threshold: 787 (mm, 31 inches)
  ├── comparator: ">="
  ├── error_level: "BLOCK"
  └── citation: "14 CFR §25.785"
```

**The same ComplianceStage, the same proof chain, the same AD_Val_Rule table.** New domains are data, not code.

---

## The GEO Proof Chain as Universal Audit

The white-box GEO logging (P123, GEO commit, P132) is domain-agnostic:

```
[GEO] TACK CHAIN  VESSEL→HULL_SECTION_3→FRAME_27→PLATE_P1
[GEO] TACK DIMS   PLATE_P1: W=2400 D=12 H=1800mm (allocated: panel_width × thickness × panel_height)
[GEO] TACK CONTAIN offset=(0,0,0) from anchor=(14200,0,5400) — OK (within frame)
[GEO] TACK LEAF   PLATE_P1: anchor=(14200,0,5400) + half=(1200,6,900) → centroid=(15400,6,6300)
```

An auditor — whether a building inspector, a classification society surveyor, a pharma GMP auditor, or an FAA certifier — can read the same log format and verify:
- **CHAIN:** Is the assembly hierarchy correct?
- **DIMS:** Are the dimensions within standard tolerances?
- **CONTAIN:** Does the component fit within its parent?
- **DRIFT:** Does the output match the design intent?

No domain-specific code needed. The proof is arithmetic.

---

## Strategic Position

### What exists today
- 12-stage pipeline, 77 verbs, 6 mathematical gates
- Construction (35 buildings), infrastructure (bridge, road, rail)
- Malaysia jurisdiction (UBBL), extensible to any

### What the pattern enables (data, not code)
- Marine: OCX extraction → hull BOMs → DNV/Lloyd's rules → GEO proof
- Pharma: GMP facility BOMs → FDA/EU rules → clean room proof
- Data centre: Rack/floor BOMs → TIA-942 rules → cooling/power proof
- Modular: Module BOMs → transport/lifting rules → assembly proof
- Nuclear: Containment BOMs → NRC rules → shielding proof

### The defining moment

The BIM industry thinks in terms of geometry: "model the building, check it later." The defining moment is the realization that **compilation from a BOM with embedded standards is a universal pattern** for any manufactured spatial assembly. The geometry is OUTPUT, not INPUT. The standard is EMBEDDED in the compilation, not APPLIED after.

This is what no other tool does. Revit places geometry. Solibri checks it. The BIM Compiler compiles it from a recipe and proves it during compilation. That pattern works for buildings, ships, clean rooms, aircraft cabins, nuclear plants, and anything else where standards govern the assembly of products in space.

**The moat is not BIM expertise. The moat is the pattern: extract → validate → compile → prove. That pattern has no competitor in any domain.**

---

## Next Steps

1. **Prove it on marine** — OCX extraction → hull BOM → DNV rule seed → GEO proof. One proof-of-concept hull section closes the argument.
2. **Publish the pattern** — The SPATIAL_COMPILATION_PAPER.md targets Automation in Construction. A companion paper on "Standards-Driven Spatial Compilation" targeting a broader engineering journal (ASME, IEEE) would position the pattern as cross-domain.
3. **Seek domain partners** — Each new domain needs a domain expert (not an engine expert). A naval architect, a pharma engineer, a nuclear safety engineer. They bring the standards; the engine is ready.
4. **Name it** — "BIM Compiler" limits perception to construction. The engine is a **Spatial MRP** (Manufacturing Resource Planning with spatial verification). The name should reflect the universal pattern.
