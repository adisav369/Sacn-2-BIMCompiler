# Vocabulary Gap Strategy — Expanding the Rosetta Stone Dictionary

**Date:** 2026-03-30
**Author:** SpecsPerson (multi-agent research)
**Status:** Research complete, actionable

---

## Executive Summary

The BIM Compiler's architecture is domain-agnostic — it compiles anything expressed as a BOM. The limitation is vocabulary: 2,475 products across 13 product types, covering residential, institutional, terminal, and infrastructure. To fulfil the "craft any IFC-ready compliance building" vision, the dictionary needs expansion into healthcare, industrial, high-rise, data centre, and marine domains.

This document maps: (1) available IFC files per domain, (2) product catalogs that can seed component_library.db, (3) non-IFC input paths (2D plans, OCX, point clouds), and (4) a prioritized gap-filling strategy.

---

## 1. Domain Gap Matrix

| Domain | IFC Files Available? | Product Catalog Sources | Compliance Rules | Priority |
|--------|---------------------|------------------------|-----------------|----------|
| **Data Centre** | No public IFC | Kingspan NBS, Legrand BIMobject, Rittal | TIA-942, EN 12825 | **1st** — smallest gap |
| **High-Rise** | Limited (Schependomlaan apartments) | DigiPara IFC (elevators), Schuco NBS (curtain wall) | EN 81-20, EN 13830 | **2nd** — signature systems |
| **Industrial** | WBDG warehouse (COBie) | BIMobject racking (IFC), DIN 536 crane rails | AS 4084, CMAA 70 | **3rd** — fast to seed |
| **Healthcare** | WBDG clinic (IFC 2x3) | Herman Miller RFA, STERIS Revit, SEPS2BIM | NFPA 99, HTM 02-01 | **4th** — medium effort |
| **Marine** | None (IFC has no hull entities) | EN 10067 bulb flats (PDF tables), JFE catalog | DNV Rules, IACS CSR | **5th** — needs OCX path |

---

## 2. Available IFC Files for New Rosetta Stones

### Immediate (download and extract)

| File | Domain | Schema | Source | Est. Elements |
|------|--------|--------|--------|--------------|
| WBDG Office | Commercial office | IFC 2x3 | wbdg.org/bim/cobie | ~500-1000 |
| WBDG Clinic | Healthcare clinic | IFC 2x3 | wbdg.org/bim/cobie | ~300-500 |
| WBDG Barracks | Military residential | IFC 2x3 | wbdg.org/bim/cobie | ~400-800 |
| Schependomlaan | Multi-unit apartments | IFC 2x3 | github.com/openBIMstandards | ~2000+ |
| KIT datasets | Various (KIT built FZK Haus) | IFC4 | ifcwiki.org/Examples | varies |
| buildingSMART validation | Mixed building types | IFC4 | github.com/buildingSMART | small |
| BIM Whale samples | Basic house, office | IFC 2x3/4 | github.com/andrewisen | small |

### Near-term (conversion needed)

| Source | Domain | Format | Conversion Path |
|--------|--------|--------|----------------|
| ResPlan dataset | 17,000 residential plans | Vector SVG + graph | Script: vector coords → _extracted.db |
| Swiss Dwellings (MSD) | 5,300 apartments | Vector | Script: similar to ResPlan |
| Cloud2BIM pipeline | Any existing building | Point cloud → IFC | LiDAR → Cloud2BIM → IFC → existing pipeline |
| DXF/AIA layer files | Any architectural DXF | DXF | ezdxf parser → _extracted.db |

### Marine-specific (no IFC path)

| Source | Domain | Format | Conversion Path |
|--------|--------|--------|----------------|
| OCX files from NAPA/Cadmatic | Ship hull structure | XML (Apache 2.0 schema) | extractOCXtoDB.py → _extracted.db |
| FREEship/DELFTship | Hull form geometry | STEP/IGES/DXF | Test hull generation → BOM |
| GrabCAD ship models | Various vessels | STEP/IGES | Proof-of-concept only |

---

## 3. Product Catalog Sources for component_library.db

### Tier 1 — Machine-readable, IFC-compatible

| Source | Domain | Products | Format | Effort |
|--------|--------|----------|--------|--------|
| **BIMobject.com** racking | Industrial | ~50 | IFC download | 1 session |
| **DigiPara** elevators | High-rise | ~30 | IFC 4.0 + COBie | 1 session |
| **Kingspan/Tate** raised floors | Data centre | ~20 | NBS Source (structured) | 1 session |
| **Legrand** cable trays | Data centre | ~30 | BIMobject (RFA/IFC) | 1 session |
| **Schuco** curtain wall | High-rise | ~25 | NBS Source | 1 session |
| **Rittal** server racks | Data centre | ~15 | BIMobject | 1 session |

### Tier 2 — Extractable from Revit/RFA files

| Source | Domain | Products | Effort |
|--------|--------|----------|--------|
| Herman Miller/Nemschoff | Healthcare furniture | ~40 | pyRevit parameter extraction |
| Schindler/ThyssenKrupp | Elevators | ~20 | RFA download + extraction |
| Kawneer facade systems | High-rise | ~15 | RFA download + extraction |

### Tier 3 — Manual entry from standards/PDFs

| Source | Domain | Products | Effort |
|--------|--------|----------|--------|
| DIN 536 crane rails | Industrial | 8 profiles | 30 minutes |
| EN 10067 bulb flats | Marine | 25 profiles | 1 hour |
| JFE shipbuilding catalog | Marine | ~40 profiles | 2 hours |
| NFPA 99 medical gas outlets | Healthcare | ~30 SKUs | 2 hours |

---

## 4. Marine / ShipYard Strategy

### The OCX Path (recommended)

IFC has zero hull entities. **OCX (Open Class 3D Exchange)** is the marine equivalent:
- Apache 2.0 licensed, public XSD schema (v3.1.0, May 2025)
- Structural: panels (plates with thickness), stiffeners (profile refs), brackets, seams
- Hierarchy: vessel → sections → panels → plates/stiffeners
- Python parser exists: github.com/OCXStandard/ocx-schema-parser
- Exported by NAPA Steel and Cadmatic (the two major shipbuilding CAD tools)
- Consortium: DNV, AVEVA, Hexagon, Siemens, NAPA, Kongsberg

**Implementation:** `extractOCXtoDB.py` parsing OCX XML → `_extracted.db` tables. Structurally identical to what `extractIFCtoDB.py` does for IFC. The XSD maps:
- OCX `Panel` → `elements_meta` (plate element)
- OCX `Stiffener` → `elements_meta` (stiffener element with profile ref)
- OCX `Bracket` → `elements_meta`
- OCX structural hierarchy → `spatial_structure` (vessel → section → panel)
- OCX seams → `rel_aggregates` (panel-to-plate assembly)

### Marine Product Library (small, well-defined)

| Category | Count | Source |
|----------|-------|--------|
| Bulb flat profiles HP80-HP430 | ~25 | EN 10067 |
| Unequal leg angles | ~20 | JFE catalog |
| Flat bar stiffeners | ~15 | EN 10058 |
| Shell plating gauges | ~12 | DNV Rules |
| Marine doors/hatches | ~25 | Manufacturer catalogs |
| **Total structural** | **~100** | |

Outfitting (piping, HVAC, electrical) reuses standard MEP products already in the library.

### Classification Rules → AD_Val_Rule

No classification society publishes rules in machine-readable format. All PDF-only (DNV, Lloyd's, BV, ABS). Rules would need manual authoring, same as UBBL for buildings. However:
- DNV integrates with NAPA/Cadmatic via proprietary interfaces
- The rule set is smaller and more formulaic than building codes (scantling calculations, section modulus requirements)
- IACS Common Structural Rules (CSR) for bulk carriers/tankers are the most standardized

---

## 5. Non-IFC Input Paths

### ResPlan → Mass Rosetta Stone Generation

**Best ROI finding.** ResPlan (arxiv.org/abs/2508.14006) provides 17,000 vector floor plans with:
- Wall segments, doors, windows, balconies as vector geometry
- **Graph-based room connectivity** (rooms = nodes, connections = typed edges)
- Room type + area attributes per node

The graph structure is isomorphic to a BOM tree. A converter script (~400 LOC) could:
1. Parse ResPlan vector data
2. Map walls → IfcWall products, doors → IfcDoor, windows → IfcWindow
3. Compute AABB from coordinates + assumed storey height
4. Generate `_extracted.db` per plan

This could produce thousands of Rosetta Stones from one dataset download.

### Cloud2BIM → Real Buildings Without IFC

Cloud2BIM (github.com/VaclavNezerka/Cloud2BIM, GPL, March 2025):
- Point cloud (LAS/LAZ/PLY) → IFC with walls, slabs, openings, rooms
- 7x faster than competing tools, handles non-orthogonal geometry
- Output IFC feeds directly into existing `extractIFCtoDB.py`

Pipeline: LiDAR scan → Cloud2BIM → IFC → existing extraction → Rosetta Stone

### DXF/AIA Layer Parsing

Architectural DXF files with AIA layer conventions (A-WALL-FULL, A-DOOR, A-GLAZ) can be parsed with `ezdxf` Python library (~300 LOC):
- Layers encode element types directly
- Polylines give wall centerlines and lengths
- Block insertions give door/window positions
- Extrude 2D to assumed storey height for AABB

---

## 6. Recommended Execution Order

### Phase A — Quick Wins (seed 4 domains, ~5 sessions)

1. Download WBDG Office + Clinic IFC files → extract → new Rosetta Stones
2. Seed data centre: Kingspan raised floors + Legrand cable trays from BIMobject/NBS
3. Seed high-rise: DigiPara elevators (IFC) + Schuco curtain wall (NBS)
4. Seed industrial: BIMobject pallet racking + DIN 536 crane rails (8 manual entries)
5. Seed marine: EN 10067 bulb flat profiles (25 manual entries)

### Phase B — Conversion Tools (~3-5 sessions)

6. ResPlan converter script (vector → _extracted.db) — potentially 17,000 stones
7. DXF/AIA parser script (ezdxf → _extracted.db) — for practice DXFs
8. Cloud2BIM integration script (shell wrapper for point cloud → IFC → extract)

### Phase C — Marine OCX Path (~3-5 sessions)

9. extractOCXtoDB.py — parse OCX XML to _extracted.db schema
10. FREEship test hull generation — proof-of-concept Rosetta Stone
11. Marine AD_Val_Rule authoring from DNV/IACS rules (manual)

### Phase D — Scale (ongoing)

12. Healthcare medical gas products (manual from NFPA 99)
13. Herman Miller RFA extraction for healthcare furniture
14. Community product catalog contributions (Phase 7 roadmap)

---

## 7. Impact on Moat

Each domain extension:
- Adds proven BOM vocabulary to the Rosetta Dictionary
- Adds compliance rules (AD_Val_Rule) for new jurisdictions/domains
- Widens the gap with competitors (none have multi-domain BOM compilation)
- Compounds: marine hull plates + building curtain walls + bridge decks = same engine, same proofs

The OCX path for marine is particularly strategic — it would make BIM Compiler the first tool to compile both buildings AND ships from the same BOM engine. No competitor even attempts this.
