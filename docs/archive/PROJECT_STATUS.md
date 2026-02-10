# BIM INTENT COMPILER - PROJECT STATUS

**Version:** 0.80.0
**Date:** February 2025
**Witnesses:** 21/24 (proven)
**Status:** Active Development

---

## Executive Summary

The BIM Intent Compiler translates natural language building intent into mathematically verified IFC-conformant BIM models. The system generates geometry AND proves its correctness through a witness system—an industry first.

```
"3 bedroom house with ensuite" → Valid IFC + Witness Certificate (21 proofs)
```

**Key Innovation (Phase 57-62):** Authority Data (AD) tables drive all code compliance, BOM quantities, and room sizing—no hardcoded values in Java.

---

## Current Capabilities

### What Works Now

| Capability | Status | Witness Proof |
|------------|--------|---------------|
| DSL → Building geometry | ✅ | ROOMS_ENCLOSED |
| Room connectivity (doors) | ✅ | ALL_ROOMS_REACHABLE |
| Window placement | ✅ | WINDOWS_ON_EXTERIOR |
| Roof generation | ✅ | ROOF_COVERS_ALL |
| Foundation | ✅ | FOUNDATION_GROUNDED |
| Electrical (lights, outlets, switches) | ✅ | ELECTRICAL_IN_SPACES |
| Plumbing (waste, vent, supply) | ✅ | PLUMBING_WASTE_COMPLETE |
| MEP system graphs | ✅ | Connectivity paths proven |
| BOM export | ✅ | Assembly → components |
| Costed BOM (MYR) | ✅ | iDempiere integration |
| **LOD400 Library (doors, windows, stairs)** | ✅ | Phase 57-58 |
| **Fire Protection AD** | ✅ | Phase 57 |
| **Variable BOM Resolution** | ✅ | Phase 60-62 |
| **Room Sizing Resolution** | ✅ | Phase 61 |
| **Furniture Placement (LOD400)** | ✅ | Phase 59 |
| **Fire Suppression Piping** | ✅ | Phase 80 |

### BIM Correctness Levels

```
L0 GEOMETRIC:     ████████████████████ 100%  ✅
L1 SPATIAL:       ████████████████████ 100%  ✅
L2 TOPOLOGICAL:   ████████████████████ 100%  ✅ (MEP connectivity complete)
L3 SYSTEMIC:      ████████████████░░░░  80%  ⚠️ (FP + electrical + plumbing done)
L4 CONSTRUCTIBLE: ████░░░░░░░░░░░░░░░░  20%  ⚠️ (BOM resolution started)
L5 COMPLIANT:     ████████████░░░░░░░░  60%  ⚠️ (AD-driven compliance)
L6 OPERABLE:      ░░░░░░░░░░░░░░░░░░░░   0%  ❌
```

---

## Technical Architecture

### Core Abstractions

| Abstraction | Purpose | Status |
|-------------|---------|--------|
| **SPACE** | Universal primitive (like ERP Document) | ✅ Stable |
| **SpaceType** | Behavior driver (like DocType) | ✅ Stable |
| **Authority Data (AD)** | Code-backed configuration tables | ✅ NEW Phase 55-62 |
| **ADSession** | Single-connection session for AD queries | ✅ NEW Phase 57B |
| **BOMResolver** | Variable quantity calculation | ✅ NEW Phase 60-62 |
| **MEPSystem** | Graph for connectivity proofs | ✅ Stable |
| **Witness** | Mathematical proof of correctness | ✅ Stable |
| **Federated DB** | TERMINAL-conformant schema | ✅ Stable |

### Authority Data Tables

| Table | Purpose | Entries |
|-------|---------|---------|
| `ad_spacetype` | Space type definitions | 21 |
| `ad_spacetype_alias` | Malaysian/regional aliases | 30+ |
| `ad_fp_trigger` | Fire protection requirements | 12 |
| `ad_fire_riser_requirement` | Riser sizing by building | 9 |
| `ad_fire_compartment` | Compartment area limits | 6 |
| `ad_bom_rule` | BOM calculation rules | 24 |
| `ad_room_sizing` | Room dimension constraints | 15 |
| `ad_placement_rule` | Element placement rules | 8 |
| `ad_vc_stair` | Stair configuration | 6 |

### Key Design Principles

1. **PRIME RULE:** Extract from TERMINAL, don't imagine
2. **Authority Data:** All rules from database, not hardcoded
3. **Witness-first:** New capability = new witness claim
4. **SPACE as root:** Everything relates to SPACE
5. **Mathematical proof:** Numbers, not visual inspection

---

## Phase History

### Foundation (Phases 0-11)

| Phase | Deliverable |
|-------|-------------|
| 0-4 | TERMINAL extraction, 12 patterns (G1-G12) |
| 5-7 | Validators calibrated against TERMINAL |
| 8 | DSL language, component library (8,700 LOD400) |
| 9-11 | Shed POC, assembly structure, BOM |

### Intent Compiler (Phases 12-29)

| Phase | Deliverable |
|-------|-------------|
| 12-14 | Multi-storey DSL, iDempiere integration |
| 15-17 | Constraint solver, vertical alignment |
| 18-21 | Consolidation, Intent resolver (NLP) |
| 22-27 | DSL Dictionary v2.0, profiles, protocols |
| 28-29 | LOD400 reconnection, assembly verification |

### Witness System (Phases 30-46)

| Phase | Deliverable | Witnesses |
|-------|-------------|-----------|
| 30-32 | Witness framework, 7 core claims | 7/7 |
| 33 | Electrical MEP (lights, outlets, switches) | 9/9 |
| 34-37 | Plumbing (waste, vent, supply) | 13/13 |
| 38-39 | Multi-storey, electrical circuits | 15/15 |
| 40-46 | School DSL, HVAC, structural | 18/18 |

### Authority Data Migration (Phases 50-62)

| Phase | Deliverable | Status |
|-------|-------------|--------|
| 50-54 | School end-to-end, SpaceType AD | ✅ |
| 55-56 | Vertical Circulation AD, CORE blocks | ✅ |
| 57 | Fire Protection AD (triggers, risers, compartments) | ✅ |
| 57B | ADSession factory pattern | ✅ |
| 57C | LOD400 window library integration | ✅ |
| 58 | Stair library integration | ✅ |
| 59 | Furniture placement (LOBBY, CANTEEN, OFFICE) | ✅ |
| 60 | BOM Rule AD (24 rules) | ✅ |
| 61 | Room Sizing AD (15 rules) | ✅ |
| 62 | BOMResolver integration in BuildingCompiler | ✅ |
| 62C | All formulas in DB metadata | ✅ |

---

## Current Development

### Just Completed: Phase 62C

- All BOM formulas now in database metadata
- No hardcoded values in Java
- Outlier logging when BOM exceeds room capacity
- Regression tests passing (TB-LKTN 4/4, School 5/5)

### BOM Resolution (Phase 60-62)

| Rule | Formula | Code Reference |
|------|---------|----------------|
| PER_AREA | `ceil(area / base)` | NFPA 13 (sprinklers) |
| PER_LUX | `ceil(area × lux / lumens)` | MS 1525 (lighting) |
| PER_CFM | `ceil(cfm / base)` | ASHRAE 62.1 (diffusers) |
| PER_OCCUPANT | `ceil(occupancy / base)` | IBC (furniture) |
| FIXED | `base` | IPC 403.1 (fixtures) |

### LOD400 Library Usage (Phase 57-59)

| Element | Library | Parametric | Coverage |
|---------|---------|------------|----------|
| Doors | 46 | 0 | 100% |
| Windows | 58 | 8 | 88% |
| Stairs | 1 | 0 | 100% |
| Furniture | 11 | 0 | 100% |
| Lights | 85 | 0 | 100% |

### Next Phase: 63

| Option | Deliverable | Value |
|--------|-------------|-------|
| A | RoomSizingResolver integration | L4 progress |
| B | Diffuser library integration | L3 → 90% |
| C | Updated USER_GUIDE and GLOSSARY | Documentation |

---

## Reference Models

### TB-LKTN (Malaysian Affordable Housing)

| Metric | Value |
|--------|-------|
| Storeys | 1 |
| Spaces | 7 |
| Elements | 125+ |
| Witness | 21/24 PROVEN |

### Sekolah (Malaysian School)

| Metric | Value |
|--------|-------|
| Storeys | 2 |
| Spaces | 24 |
| Elements | 500+ |
| Witness | 21/24 PROVEN |

---

## Ground Truth

### TERMINAL Reference

- **Source:** Terminal Jetty Complex IFC
- **Elements:** 51,723 at LOD400
- **Disciplines:** 9 (ARC, STR, ACMV, ELEC, FP, SP, CW, LPG, REB)
- **Database:** `enhanced_federation_GI.db` (234MB)

### Component Library

- **Location:** `library/component_library.db` (126 MB)
- **Components:** 8,701 definitions across 18 IFC classes
- **Coverage:** Doors, windows, stairs, railings, furniture, fixtures, MEP

---

## Quick Commands

```bash
# Build
cd ~/bim-compiler && mvn compile

# Run TB-LKTN compilation
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q

# Run School compilation
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.SchoolEndToEndTest" -q

# BOM Resolution test
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.BOMRuleAD" -q

# View witness report
cat output/tb_lktn_witness.json | jq '.summary'
```

---

## Documentation Index

| Document | Purpose | Status |
|----------|---------|--------|
| `PROGRESS.md` | Active development state | ✅ Current |
| `PROJECT_STATUS.md` | This file - overview | ✅ Current |
| `GLOSSARY.md` | Term definitions | ✅ Updated v0.62 |
| `USER_GUIDE.md` | User documentation | ✅ Updated v0.62 |
| `METADATA_DRIVEN_ARCHITECTURE.md` | AD architecture | ✅ Current |
| `BUILDING_AS_BOM_CONCEPT.md` | BOM resolution | ✅ Current |
| `witness-system-specification.md` | Witness design | ✅ Reference |

---

## Core Innovation

**Authority Data + Witness System** - No other BIM tool provides:
1. Code-backed configuration (all rules from database)
2. Mathematical proof of correctness

```
Traditional BIM: "The model looks correct"
BIM Intent Compiler: "Here are 21 proofs that the model IS correct,
                      using rules from NFPA, IBC, UBBL, ASHRAE"
```

**This is auditable, code-compliant BIM.**

---

*Single source of truth for BIM Intent Compiler project status.*
*Updated: February 2025, Phase 62C complete.*
