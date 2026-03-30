# IFC Assembly BOMs — Curtain Walls, Stairs, Structural Groups

**Spec:** BBC.md §2 (BOM = recipe), DISC_VALIDATION_DB_SRS §10.4.13
**Prereq:** P126 DONE (IfcRelAggregates extracted), P127 DONE (SpatialContainerConfig)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** The IFC aggregation defines the assemblies. Read it. No invention.

## Context from P127

- `StoreyConfig` renamed to `SpatialContainerConfig` (P127)
- `StructuralBomBuilder.build()` signature now:
  ```java
  build(Connection bomConn, BuildingConfig config,
        Map<String, SpatialContainerConfig> containers,
        Map<String, List<ExtractionElement>> storeyElements,
        Map<String, Set<String>> excludeByStorey,
        CategoryLookup catLookup)
  ```
- Containers are auto-discovered or YAML override — passed by pipeline
- BIM.properties already set: `bim.log.level=INFO`, `bim.geo.debug=true`

## Read first

1. `PROGRESS.md` §Current State
2. Extraction DB: `SELECT * FROM rel_aggregates` (from P126)
3. `IFCtoBOM/src/main/java/com/bim/ifctobom/StructuralBomBuilder.java`
4. SH curtain wall: 1 IfcCurtainWall → 20 IfcMember + 6 IfcPlate
   (parent GUID not in elements_meta — IfcCurtainWall not in extraction class list)

## Problem

Structural orphans (walls, slabs, beams, curtain walls) are currently
dumped flat into storey-level FLOOR BOMs. But the IFC has assembly
structure: a curtain wall is one parent with N children.

P126 extracts `rel_aggregates` (parent_guid → child_guid). This prompt
makes StructuralBomBuilder read that table and create assembly BOMs:

```
SH_GF_STR (FLOOR — envelope)
├── SH_GF_ASM_1 (ASSEMBLY) → 26 children (curtain wall)
├── Wall elements (flat LEAF)
├── Door/Window elements (flat LEAF)
└── ...
```

Each IFC assembly becomes its own BOM with a MAKE line in the parent.
Elements not in any assembly stay as flat leaves (existing behavior).

## Fix

### 1. StructuralBomBuilder — read rel_aggregates from extraction DB

Add `Connection extractionConn` parameter (same pattern as ScopeBomBuilder).
For each container's structural elements:
- Query `rel_aggregates` for child_guids that match element GUIDs
- Group by parent_guid
- For each assembly with >1 child: create an ASSEMBLY BOM, move children into it,
  add a MAKE line to the container FLOOR BOM
- Elements not in any assembly stay as flat leaves (existing behavior)

### 2. Wire extraction connection in IFCtoBOMPipeline

Pass `extractionConn` to `StructuralBomBuilder.build()` in the RE path
(same pattern as ScopeBomBuilder — `extractionConn` already opened).

### 3. Assembly naming

Assembly BOM IDs: `{prefix}_{containerCode}_ASM_{N}` (e.g. `SH_GF_ASM_1`).
No concrete names like "CURTAINWALL" — the assembly type comes from the
parent element's IFC class in rel_aggregates, not from hardcoded names.

## Gate

- SH: curtain wall assembly BOM created (2 assemblies, 13 children each)
- SH 7/7 PASS, GEO DRIFT=0
- DX: any IFC assemblies auto-discovered? (stair assemblies: 2 × 5 children)

## What NOT to do

- Do NOT modify ScopeBomBuilder or BomHierarchyBuilder
- Do NOT modify the compilation pipeline (DAGCompiler)
- Do NOT modify migration files
- Do NOT force assembly BOMs on elements without IfcRelAggregates
- Do NOT use concrete assembly type names in BOM IDs
- **All logging via BIMLogger — no System.out.println**

## Spec citation

```java
// Implementing BBC.md §2 + DISC_VALIDATION_DB_SRS §10.4.13 — IFC assembly BOMs
// IfcRelAggregates defines parent-child structure, no heuristic grouping
```

## Commit

```bash
git add IFCtoBOM/src/main/java/com/bim/ifctobom/StructuralBomBuilder.java \
        IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java \
        PROGRESS.md
git commit -m "[S100-p129] IFC assembly BOMs: structural groups from IfcRelAggregates"
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- SH: assembly BOM count? Child count per assembly?
- SH 7/7 PASS? GEO DRIFT=0?
- DX: any assemblies auto-discovered?
- BOM line count change (before/after)?
- Any surprises — document, do NOT fix

---

## Findings

**SH:** 2 curtain wall assembly BOMs (SH_UN_ASM_1, SH_UN_ASM_2). Each: 10 mullions (factorized qty=10) + 3 glazing panels = 4 BOM lines. SH 7/7 PASS, GEO DRIFT=0.

**DX:** 2 stair assembly BOMs (DX_UN_ASM_1, DX_UN_ASM_2). Each: 3 children (2 IfcMember stringers + 1 IfcStairFlight). Railings (2 per stair) excluded by composition pairing — they're in the half-unit, not structural. Reconciliation delta=+0.

**FK/IN:** No rel_aggregates matches (0 extraction children in assembly table). Zero regression — assemblies only fire when data exists.

**BOM line count change:** SH: flat 26 curtain wall LEAFs → 2 MAKE lines + 8 assembly LEAFs (net -16). Factorization compressed 10 identical mullions into qty=10 rows.

**Surprise:** Both DX stair assemblies land on "Unknown" storey (not Level 1 or Level 2). The IFC has stairs as IfcRelAggregates children but doesn't assign them to a specific IfcBuildingStorey via IfcRelContainedInSpatialStructure. This is correct IFC semantics — stairs span storeys.
