# DAGCompiler — Progress

**Last updated:** 2026-02-17
**Current state:** Source migrated, lib consolidated, all 3 Rosetta Stones at ~100%

---

## Layout

```
DAGCompiler/
  src/main/java/com/bim/compiler/   — compiler source (moved from root src/)
  lib/
    input/    — IFC sources + Rosetta Stone extracted reference DBs
      Ifc4_SampleHouse.ifc                 Stone 1 IFC source (2.2M)
      Ifc4_SampleHouse_extracted.db        Stone 1 reference (55 elements)
      Ifc2x3_Duplex_Architecture.ifc       Stone 2 ARC IFC source (2.3M)
      Ifc2x3_Duplex_MEP.ifc               Stone 2 MEP IFC source (18M)
      Ifc2x3_Duplex_extracted.db           Stone 2 reference (1,085 elements)
      Terminal_Extracted.db                Stone 3 reference (51,723 elements)
    output/   — compiled output DBs (generated, not committed)
      ifc4_sample_house.db
      ifc2x3_duplex.db
      sjtii_terminal.db
  tools/
    material_extractor.py              Material/colour extraction from IFC
  pom.xml     — dag-compiler module (parent pom at project root)
```

## Current Scores (carried from Phase DE-4)

| Stone | Recall | Precision | F1 | Output | Ref | Ratio |
|-------|--------|-----------|------|--------|------|-------|
| SampleHouse | **100%** (55/55) | **100%** | **100%** | 55 | 55 | **1.00x** |
| Duplex | **100%** (1085/1085) | **100%** | **100%** | 1085 | 1085 | **1.00x** |
| Terminal | **~100%** (51719/51723) | **100%** | **~100%** | 51719 | 51723 | **1.00x** |

## Migration Log

### 2026-02-17 — Consolidation
- Source moved from `src/main/java/` to `DAGCompiler/src/main/java/` (multi-module Maven)
- Reference DBs moved from `reference/rosetta/` to `DAGCompiler/lib/input/`
- Output DBs moved from `output/` to `DAGCompiler/lib/output/`
- E2E test paths updated (SampleHouse, Duplex, Terminal)
- `docs/TheRosettaStoneStrategy.txt` updated with new paths and PROJECT LAYOUT section
- `workingDirectory` in pom.xml remains `${project.parent.basedir}` (paths relative to root)

### 2026-02-17 — Phase MAT: Material/Color Extraction Pipeline
- Created `DAGCompiler/tools/material_extractor.py` — extracts material_name + material_rgba from IFC source files
- Enriched reference DBs from IFC sources:
  - SampleHouse: 55/55 material names, 51/55 RGBA (from `Ifc4_SampleHouse.ifc`)
  - Duplex: 85/195 ARC material names, 139/195 ARC RGBA (from `Ifc2x3_Duplex_Architecture.ifc`; MEP IFC has no materials)
  - Terminal: already had material data in reference DB
- Added `material_name TEXT, material_rgba TEXT` columns to `ad_element_placement` in component_library.db
- Populated placement material columns: SH 46, DX 129, Terminal 47,769
- Java pipeline wiring:
  - `PlacementAD.Placement` record: added materialName, materialRgba fields
  - `ElementPersistence.writeElementMeta()`: extended to accept + write material params (10 columns)
  - `BuildingWriter`: output schema extended, global emission passes material, roof overrides pass material
  - `BuildingSpecs.SlabSpec/FixtureSpec`: added material fields with backwards-compat constructors
  - `StoreyCompiler.applyPlacementOverrides()`: passes material from placement to specs
  - `MEPWriter.writeFixture()`: passes material to output
- Material coverage in output DBs:
  - SampleHouse: 55/55 material_name, 51/55 material_rgba (Glass panels: alpha=0.100)
  - Duplex: 77/1085 material_name, 124/1085 material_rgba
  - Terminal: 41,148/51,719 material_name, 41,613/51,719 material_rgba
- All 3 scores unchanged — zero regression
- E2E tests now require `-pl DAGCompiler` flag: `mvn exec:java -pl DAGCompiler -Dexec.mainClass=...`

### 2026-02-17 — IFC Source Files + Documentation
- Copied original IFC source files to `lib/input/`:
  - `Ifc4_SampleHouse.ifc` (2.2M) — Stone 1 IFC source
  - `Ifc2x3_Duplex_Architecture.ifc` (2.3M) — Stone 2 ARC IFC source
  - `Ifc2x3_Duplex_MEP.ifc` (18M) — Stone 2 MEP IFC source
  - Terminal: no single IFC (merged from 7 IFCs into federation DB → Terminal_Extracted.db)
- Full pipeline now self-contained: IFC → extract → reference DB → compile → output DB
- Updated documentation:
  - Root `README.md` — project structure, pipeline diagram, material fidelity, updated scores
  - `DAGCompiler/README.md` (new) — module layout, tools, build/run, material extractor usage
  - `docs/DEVELOPER_GUIDE.md` — key files, DAG pipeline, material pipeline section, updated paths
  - `docs/USER_GUIDE.md` — build commands, output schema, material/colour data section
  - `library/README.md` — ad_element_placement material columns documented below

## What's Next
- Awaiting instructions
