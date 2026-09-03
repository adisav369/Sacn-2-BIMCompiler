# DONE — [7745affd](https://github.com/red1oon/BIMCompiler/commit/7745affd)
# Spatial Container Auto-Discovery — Unbuckle from YAML

**Spec:** DISC_VALIDATION_DB_SRS §10.4.13 (continued)
**Prereq:** P125 DONE (IFC-driven ScopeBomBuilder, BomHierarchyBuilder), P126 DONE (rel_aggregates)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** The IFC spatial structure already has containers. Read them. No invention.

## Design Principle

**All concrete definitions are metadata — code remains abstract.**
YAML is just the root BOM OrderLine (building identity, prefix, product_category).
Spatial containers (storeys for buildings, segments for infra) are IFC data,
auto-discovered from the extraction. The compiler fetches them or reports back.
DisciplineBomBuilder (CO path) is triggered by Callout — separate concern.

## Read first

1. `PROGRESS.md` §Current State
2. `IFCtoBOM/src/main/java/com/bim/ifctobom/StructuralBomBuilder.java` — iterates `config.storeys()`
3. `IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java` — storeyElements from ExtractionPopulator
4. `IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java` — StoreyConfig record
5. `IFCtoBOM/src/main/java/com/bim/ifctobom/BomHierarchyBuilder.java` — uses config.storeys()
6. `IFCtoBOM/src/main/java/com/bim/ifctobom/DisciplineBomBuilder.java` — uses config.storeys()

## Problem

The pipeline is buckled to YAML `storeys:` section. Four builders read
`config.storeys()` for metadata (code, productCategory, role, seq). But:

1. The extraction DB already groups elements by spatial container name
2. The `storeyElements` map already has container names as keys
3. `StoreyConfig` is a concrete name — infra has segments, not storeys
4. All four metadata fields are derivable from the data

## Fix

### Phase A: Rename StoreyConfig → SpatialContainerConfig

Abstract naming for buildings + infrastructure:

- `ClassificationYaml.StoreyConfig` → `SpatialContainerConfig`
- `BuildingConfig.storeys()` → `BuildingConfig.spatialContainers()`
- YAML parsing unchanged (still reads `storeys:` / `segments:` keys)
- Update all 4 consumers: StructuralBomBuilder, DisciplineBomBuilder, BomHierarchyBuilder, IFCtoBOMPipeline

### Phase B: Auto-discover from extraction data

Add `SpatialContainerConfig.discover(storeyElements)`:
- Sort containers by min Z of their elements (seq from position: 1010, 1020, ...)
- `code` = generic abbreviation from name (first letter of each word, uppercase)
- `productCategory` = code
- `role` = sanitized name (spaces → underscores, uppercase)
- No hardcoded name→code mapping — algorithm works for any building

### Phase C: Pipeline wiring

In `IFCtoBOMPipeline.run()`:
- If `config.spatialContainers()` is empty → auto-discover from `storeyElements`
- If present → use as Order override (backward compat)
- Replace the unmapped-storey pre-flight FAIL with auto-discovery
- Pass resolved containers to all builders (not `config`)

### Phase D: Fix println violations

- `StructuralBomBuilder.java:140` `System.out.printf` → `BIMLogger.fine()`
- `StructuralBomBuilder.java:169,174` `System.err.printf` → `BIMLogger.warn()`
- `ScopeBomBuilder.java:157` `System.out.printf` → `BIMLogger.fine()`

## BIM.properties

Already set (P126):
```properties
bim.log.level=INFO
bim.geo.debug=true
```

## Gate

- SH with `storeys:` removed from YAML → 7/7 PASS
- DX with `storeys:` removed → same result as with storeys
- `mvn compile -q` PASS (no compilation errors from rename)
- SH GEO DRIFT=0

## What NOT to do

- Do NOT remove `storeys:` / `segments:` YAML parsing (keep as Order override)
- Do NOT modify compilation pipeline (DAGCompiler)
- Do NOT modify migration files
- Do NOT hardcode name→code mappings ("Ground Floor"→"GF")
- **All logging via BIMLogger — no System.out.println**

## Spec citation

```java
// Implementing DISC_VALIDATION_DB_SRS §10.4.13 — spatial container auto-discovery
// IFC spatial structure replaces YAML storeys/segments dependency
```

## Commit

```bash
git add IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java \
        IFCtoBOM/src/main/java/com/bim/ifctobom/StructuralBomBuilder.java \
        IFCtoBOM/src/main/java/com/bim/ifctobom/DisciplineBomBuilder.java \
        IFCtoBOM/src/main/java/com/bim/ifctobom/BomHierarchyBuilder.java \
        IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java \
        IFCtoBOM/src/main/java/com/bim/ifctobom/ScopeBomBuilder.java \
        PROGRESS.md
git commit -m "[S100-p127] SpatialContainerConfig: auto-discover from IFC, unbuckle from YAML storeys"
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- SH: how many containers auto-discovered? Code derivation correct?
- DX: container count + Z ordering correct?
- ScopeBomBuilder/StructuralBomBuilder printf → BIMLogger fix confirmed?
- SH 7/7 PASS? GEO DRIFT=0?
- Any surprises — document, do NOT fix

---

## Findings

**SH auto-discovery:** 3 containers from extraction: Ground Floor→GF (seq 1010), Roof→RO (1020), Unknown→UN (1030). Z-ordered correctly. SH 7/7 PASS, GEO DRIFT=0.

**DX auto-discovery:** 5 containers: T/FDN→TF (1010), Level 1→L1 (1020), Unknown→UN (1030), Level 2→L2 (1040), Roof→RO (1050). Z-ordered correctly. DX reconciliation FAIL (delta=+50) is PRE-EXISTING — identical with YAML storeys.

**Code changes:**
- `StoreyConfig` → `SpatialContainerConfig` (rename across 6 files)
- `config.storeys()` → `config.spatialContainers()` (BuildingConfig record field)
- Auto-discovery via `SpatialContainerConfig.discover()` — Z-ordered, generic abbreviation
- Pipeline resolves containers: auto-discover if YAML empty, warn if YAML override drops elements
- StructuralBomBuilder/DisciplineBomBuilder/BomHierarchyBuilder accept resolved `containers` map

**Printf → BIMLogger fixes:**
- StructuralBomBuilder:140 `System.out.printf` → `BIMLogger.fine("STR", ...)`
- StructuralBomBuilder:169,174 `System.err.printf` unmapped storey warnings → REMOVED (no longer needed with auto-discovery)
- ScopeBomBuilder:157 `System.out.printf` → `BIMLogger.fine("SCOPE", ...)`

**Abbreviation algorithm:** first letter of each word, uppercase. Single words take 2 chars. Digits pass through. No hardcoded mappings. "Ground Floor"→GF, "Level 1"→L1, "Roof"→RO, "T/FDN"→TF, "Unknown"→UN.

**Surprise:** BOM IDs changed (SH_ROOF_STR → SH_RO_STR, SH_CW_STR → SH_UN_STR). This is expected — auto-derived codes differ from hand-crafted YAML codes. No functional impact (downstream uses bom_id as opaque key).
