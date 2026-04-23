# 2D Drawing Engine — Java Migration Study

> **Status:** Study only. No migration in this session.
> **Date:** 2026-04-17
> **Current codebase:** `2D_Layout/python/drawing_writer.py` (2,614 lines) +
> `drawing_writer_dxf.py` (4,269 lines) = ~6,900 lines Python.

## 1. Motivation

| Factor | Detail |
|--------|--------|
| Unified codebase | DAGCompiler, IFCtoBOM, BuildingCompiler all Java. 2D is the only Python subsystem. |
| 2D→3D round-trip | `BIM_Designer_SRS.md` §28 specs click-to-place workflow. Java backend is the natural host for round-trip state. |
| Type safety | Python dicts (`tpl['elevation']['level_zone_width_mm']`) have no compile-time checks. Java records/POJOs catch typos at build. |
| Error handling | `DrawingInventionError` (added this session) is runtime-only. Java checked exceptions make hard-fail rules enforceable at compile. |
| Growth path | Drawing engine grows with the compiler (MEP plans, sections, schedules). Single-language build simplifies CI. |

## 2. Arguments Against

| Factor | Detail |
|--------|--------|
| ezdxf maturity | Python `ezdxf` is the best open-source DXF library. 20+ years of DXF spec coverage. |
| Java DXF libraries | Limited options. `jDXF` (kabeja fork) unmaintained. Apache POI has no DXF support. |
| Iteration speed | Drawing layout is visual — Python's REPL + SVG preview cycle is fast. Java compile-run cycle is slower. |
| Working code | 6,900 lines already producing correct output for 2 buildings, 8 conformity checks passing. |

## 3. Java DXF Library Options

| Library | Status | DXF Version | Notes |
|---------|--------|-------------|-------|
| **kabeja** (fork) | Abandoned ~2018 | DXF R12-R14 | Read-only. No write support. |
| **jDXF** | Minimal | DXF R12 | Write-only, no LWPOLYLINE, no XDATA, no layers. |
| **Custom writer** | Viable | DXF R2010+ | DXF is ASCII text. Write entities directly. |
| **ezdxf via Jython/subprocess** | Hybrid | Full | Keep ezdxf as subprocess, Java orchestrates. |

**Recommendation:** Custom DXF writer. DXF entity format is well-documented ASCII.
We only use: LWPOLYLINE, LINE, TEXT, MTEXT, CIRCLE, ARC, INSERT (blocks), XDATA.
A focused Java writer covering these 8 entity types is ~800 lines.

## 4. Proposed Class Hierarchy

```
com.bim.compiler.drawing2d
├── DrawingEngine.java          // Orchestrator (replaces drawing_writer_dxf.py main)
├── model/
│   ├── Element.java            // Building element (wall/door/window/roof/slab)
│   ├── GridLine.java           // Derived grid
│   ├── DimString.java          // Dimension annotation
│   ├── Level.java              // Elevation level (FFL, GRD, SILL, etc.)
│   └── DrawingSheet.java       // Paper, margins, title block, scale
├── projection/
│   ├── FloorPlanProjector.java // Section cut at 1.2m
│   ├── ElevationProjector.java // Face projection + roof silhouette
│   ├── RoofPlanProjector.java  // XY hull of roof mesh
│   └── MepPlanProjector.java   // MEP element footprints
├── layout/
│   ├── ZoneCalculator.java     // §12a zones (level, content, height, title block)
│   ├── GridDeriver.java        // Wall alignment → grid positions
│   ├── DimensionGenerator.java // Bay dims, height dims, tier layout
│   └── LevelMarkerRenderer.java
├── dxf/
│   ├── DxfWriter.java          // Low-level DXF entity output
│   ├── DxfLayer.java           // Layer definitions (A-WALL, A-GLAZ, etc.)
│   └── DxfBlock.java           // Block INSERT for repeated symbols
├── template/
│   ├── DrawingTemplate.java    // JSON template loader (line weights, fonts, etc.)
│   └── DrawingStyle.java       // 2d_drawing_style DB rows
└── validation/
    ├── DrawingInventionError.java  // §1 R1 hard-fail
    └── ConformityChecker.java      // 8 conformity checks
```

## 5. 2D→3D Round-Trip Interface

The BIM Designer (Blender addon) needs to read 2D coordinates and write them back:

```java
// Read: 2D drawing → element positions
DrawingEngine.exportElementPositions(buildingDb, "FLOOR_PLAN")
  → List<ElementPosition>  // guid, x, y, layer, style

// Write: user drag in 3D → update 2D
DrawingEngine.updateElementPosition(guid, newX, newY, newZ)
  → regenerates affected views (floor plan + relevant elevation)
```

This interface lives naturally in Java where the CompilationPipeline can
invoke it. Python subprocess adds serialization overhead and error surface.

## 6. Migration Path (phased)

| Phase | Scope | Effort | Risk |
|-------|-------|--------|------|
| **0** | Custom DxfWriter.java (8 entity types) | 2 sessions | Low — ASCII format, well-defined |
| **1** | Element/GridLine/DimString data model in Java | 1 session | Low — direct port from Python dataclasses |
| **2** | FloorPlanProjector (section cut + grid derivation) | 2 sessions | Medium — section_cut.py geometry is subtle |
| **3** | ElevationProjector (face projection + roof envelope) | 2 sessions | Medium — roof_silhouette has edge cases |
| **4** | ZoneCalculator + layout (margins, centering, clamping) | 1 session | Low — arithmetic |
| **5** | DrawingTemplate loader + DrawingStyle from 2D.db | 1 session | Low — JSON parse + SQL |
| **6** | ConformityChecker port | 1 session | Low — string matching |
| **7** | Wire into CompilationPipeline, retire Python | 1 session | Medium — integration |

**Total estimate:** ~11 sessions. Python code stays as reference/fallback throughout.

## 7. Status (2026-04-17)

**Phase 0 DONE.** `DxfWriter.java` produces valid DXF R2010 output. Proof: 15 entities,
0 ezdxf audit errors, XDATA preserved, all 8 entity types working.

**Phase 1 DONE.** Data model classes: Element, GridLine, DimString, Level, DrawingSheet.
DrawingInventionError exception ported.

**Next:** Phase 2 (FloorPlanProjector) when 2D→3D round-trip becomes active work.

## 8. DXF Round-Trip Metadata Architecture

Industry research finding: **true 2D↔3D geometric round-trip via DXF is not standard
industry practice.** The dominant patterns are live views (Revit) or one-way export.
No published standard exists for DXF-as-BIM-round-trip.

**Our pragmatic approach — three layers:**

### Layer 1: XDATA per entity (BIMSRC AppID)
Already implemented in Python. Each semantic entity carries:
```
1001 BIMSRC
1000 type=WALL
1000 guid=2O2Fr$t4X7Zf8NOew3FLOH
1000 ifc_class=IfcWallStandardCase
```
- Survives copy-paste, file splitting, entity-level operations
- 16KB per-entity limit (fine for ID + type + a few properties)
- Self-contained — any single entity can be traced to its IFC source

### Layer 2: XRecord dictionary (BIM_VIEW_CONTEXT)
View metadata in the OBJECTS section — not per-entity:
```
DICTIONARY: BIM_VIEW_CONTEXT
  VIEW_DEFINITION → XRecord
    VIEW_TYPE=FLOOR_PLAN
    CUT_PLANE_HEIGHT=1.200
    STOREY_NAME=Ground Floor
    SCALE=0.01
    SOURCE_MODEL=Hospital.ifc
    COORDINATE_ORIGIN=SHARED
    EXTRACTION_TIMESTAMP=2026-04-17T10:30:00Z
```

### Layer 3: Element property table (BIM_ELEMENT_MAP)
Keyed by IFC GUID, linking to all DXF entities that represent that element:
```
DICTIONARY: BIM_ELEMENT_MAP
  <GUID> → XRecord
    1000: IfcWallStandardCase
    1000: Ground Floor
    1005: <handle of outline polyline>
    1005: <handle of hatch entity>
    1005: <handle of dimension>
```

### Edit propagation rules
1. **Property edits** (room name text, equipment tag) → propagate automatically
2. **Simple relocations** (furniture moved as group) → compute 2D delta, apply as 3D translation
3. **Geometric edits** (wall moved/resized) → generate BCF issue for human review, never auto-edit
4. **Structural edits** → reject, flag as "requires BIM tool"

### DxfWriter.java support
The Java DxfWriter already supports:
- `registerAppId()` + `addXDataToLast()` → Layer 1
- XRecord/Dictionary writing → extend `writeObjects()` for Layers 2-3
