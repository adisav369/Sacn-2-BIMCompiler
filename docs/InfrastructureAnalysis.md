# Infrastructure IFC Analysis

**Date:** 2026-03-16
**Source files:** `reference/infrastructure/` (9 IFC4X3_ADD2 files, ~7.5 MB)
**Purpose:** Understand how infrastructure IFCs map to the existing pipeline abstractions

---

## The Core Insight

The pipeline already has the right abstractions. Infrastructure is not a new problem —
it is the **same hierarchy with different vocabulary**:

| Building (today) | Infrastructure (future) | Pipeline abstraction |
|-------------------|------------------------|---------------------|
| IfcBuilding | IfcRoad / IfcBridge / IfcRailway | **FACILITY** |
| IfcBuildingStorey | IfcRoadPart / IfcBridgePart / IfcRailwayPart | **SEGMENT** |
| IfcSpace (rooms) | Lanes / Spans / Track sections | **SCOPE** |
| Walls, doors, slabs | Courses, rails, pavement, piers | **ELEMENT** |

The BOM hierarchy `BUILDING → FLOOR → LEAF` is already generic in structure:
parent BOM with MAKE children → child BOM with LEAF elements → dx/dy/dz offsets.
Renaming BUILDING to FACILITY and FLOOR to SEGMENT changes labels, not logic.

---

## What the Infrastructure Files Reveal

### Spatial hierarchy (IFC4X3)

```
IfcProject
  └─ IfcSite (environment)
       └─ IfcSite (domain)
            ├─ IfcRoad → IfcRoadPart (26 parts: pavement, surface, kerb)
            ├─ IfcBridge → IfcBridgePart (18 parts: abutment, pier, deck, span)
            └─ IfcRailway → IfcRailwayPart (3 parts: track, platform, signals)
```

Key difference: **no storeys, no rooms**. Segmentation is by structural function
(pier vs deck vs abutment), not by elevation (ground floor vs first floor).

### Entity types not in current pipeline

| Domain | New IFC classes |
|--------|----------------|
| Road | IfcCourse, IfcSurfaceFeature, IfcPavement, IfcKerb, IfcEarthworksFill |
| Bridge | IfcBearing, IfcTendon, IfcTendonAnchor, IfcDeepFoundation |
| Rail | IfcTrackElement, IfcRail, IfcSignal |
| Shared | IfcGeographicElement, IfcSign, IfcPipeSegment (utility networks) |

### Coordinate systems

Buildings use local Cartesian. Infrastructure uses **georeferenced coordinates**
(EPSG:32760, UTM zone 60S) with IfcMapConversion transforms. Offsets can be
hundreds of kilometres from origin. The current 500m world-coord guard in
BomValidator would reject every infrastructure element.

### Scale

| File | Elements | Geometric complexity |
|------|----------|---------------------|
| Infra-Bridge | ~100 | 54 triangulated facesets, 18 bridge parts |
| Infra-Road | ~60 | 38 triangulated facesets, 26 road parts |
| Infra-Rail | ~70 | 66 track elements, 3 railway parts |
| Infra-Landscaping | ~150 | 76 geographic elements, multi-domain |
| Building files (4) | ~200 | Standard building elements for context |

---

## How It Maps to the Pipeline

### What already works (no change needed)

1. **BOM hierarchy** — parent-relative dx/dy/dz offsets are domain-agnostic
2. **YAML as invention boundary** — a `classify_bridge.yaml` with `facility_parts:`
   instead of `storeys:` is the same pattern
3. **Product catalog** — `component_library.db` stores geometry by hash, domain-agnostic
4. **BOMWalker** — walks any BOM tree regardless of what BUILDING/FLOOR means
5. **Compilation pipeline** — reads BOM, emits elements, doesn't care about domain

### What needs vocabulary generalization

| Current term | Generalized term | Where it appears |
|-------------|-----------------|-----------------|
| `storeys:` (YAML) | `segments:` or `facility_parts:` | ClassificationYaml.java |
| `bom_type = 'BUILDING'` | `bom_type = 'FACILITY'` | StructuralBomBuilder, BomValidator |
| `bom_type = 'FLOOR'` | `bom_type = 'SEGMENT'` | StructuralBomBuilder, BomValidator |
| `floor_rooms:` (YAML) | `scope_spaces:` (already generic) | ClassificationYaml.java |
| `IfcBuildingStorey` (extraction) | Any IfcSpatialElement subtype | extract.py, ExtractionPopulator |

### What is conceptually different

1. **Linear referencing** — roads/railways position elements by chainage along a curve,
   not by Cartesian offset from a box corner. The BOM's dx/dy/dz model assumes
   Cartesian. Linear elements would need chainage → Cartesian conversion at extraction
   time (before they enter the BOM).

2. **Layered composition** — a road section is not an assembly of discrete objects but
   a stack of material layers (subgrade → base → binder → surface). This maps to a
   vertical BOM (parent = road section, children = layers with dz offsets) but the
   semantics are "material stacking", not "component placement".

3. **No rooms** — scope spaces for infrastructure are functional zones (lane, shoulder,
   median, pier cap) not enclosed rooms. The centroid-in-AABB scope assignment still
   works geometrically but the YAML vocabulary changes.

---

## Backward Compatibility Strategy

### The extraction boundary

The **single point of adaptation** is the extraction layer (`tools/extract.py` +
`ExtractionPopulator.java`). If extraction produces the same schema — `elements_meta`
with a `storey` column containing segment names, `elements_rtree` with AABBs — then
every downstream component works unchanged.

```
Infrastructure IFC                    Building IFC
       │                                    │
       ▼                                    ▼
  IfcFacilityPart → storey="Pier_1"    IfcBuildingStorey → storey="Ground Floor"
       │                                    │
       ▼                                    ▼
  ┌─────────────────────────────────────────────┐
  │  I_Element_Extraction (same schema)         │
  │  ExtractionReader.readByStorey() (same)     │
  │  StructuralBomBuilder (same)                │
  │  BOMWalker (same)                           │
  │  CompilationPipeline (same)                 │
  └─────────────────────────────────────────────┘
```

### What must NOT change

- `m_bom` / `m_bom_line` schema — the BOM is already abstract
- BOMWalker traversal logic — tree-walk is domain-agnostic
- Product catalog architecture — geometry-hash keyed, domain-agnostic
- YAML as sole invention boundary — same pattern, different vocabulary
- Gate tests (G1-G6) — count, volume, digest, provenance are universal

### What must be guarded

1. **BomValidator "BUILDING count == 1"** — generalize to "root facility count == 1"
2. **BomValidator 500m world-coord guard** — infrastructure uses georeferenced coords;
   guard must apply to parent-relative offsets, not absolute positions (it already does,
   but needs verification with real infrastructure data)
3. **Python extractor `get_storey_for_element()`** — must traverse IfcFacilityPart
   hierarchy, not just IfcBuildingStorey
4. **Python extractor REFERENCE_CLASSES list** — must include IFC4X3 element classes
5. **BIMConstants** — building-only; infrastructure constants live in authority_data.db
   when needed (road design standards, bridge clearances)

---

## Risk to Existing Pipeline

**Low.** Infrastructure support is an **additive** change:

- Extraction layer learns new spatial containers → same output schema
- YAML learns `facility_parts:` synonym for `storeys:` → backward compatible
- BOM types gain FACILITY/SEGMENT synonyms → BUILDING/FLOOR still work
- No existing code path is removed or altered

The previous corruption was caused by infrastructure files being processed through
the building-only extraction path, producing degenerate data (all elements in
"Unknown" storey → UNIQUE constraint violations → cascading failures). The fix is
not to change the pipeline, but to **gate infrastructure files at the extraction
boundary** until the extractor knows how to read IfcFacilityPart.

### Pre-emptive guard

Until infrastructure extraction is implemented, the extractor should **FAIL early**
on IFC4X3 files that contain IfcFacility subtypes without IfcBuildingStorey:

```
if has_facility_parts and not has_building_storeys:
    FAIL "Infrastructure IFC detected — extraction not yet supported"
```

This prevents the silent corruption that previously broke the pipeline.

---

## Summary

| Question | Answer |
|----------|--------|
| Is the BOM model sufficient? | Yes — parent-relative offsets are domain-agnostic |
| Is the YAML model sufficient? | Yes — `facility_parts:` is isomorphic to `storeys:` |
| What is the single adaptation point? | Extraction layer (Python + ExtractionPopulator) |
| Does it break existing buildings? | No — additive vocabulary, existing paths unchanged |
| What caused previous corruption? | Infra files hitting building-only extractor → "Unknown" storey |
| Pre-emptive guard? | FAIL early on IfcFacility without IfcBuildingStorey |

See also: [`YAMLGuide.md`](YAMLGuide.md) §Invention Boundary,
[`DATA_MODEL.md`](DATA_MODEL.md) §Reference DB,
[`LAST_MILE_PROBLEM.md`](LAST_MILE_PROBLEM.md) §Gap 4 (spec sources).
