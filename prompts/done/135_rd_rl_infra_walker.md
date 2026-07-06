# RD/RL Infrastructure Walker — IfcRoadPart + IfcRailwayPart Hierarchy

**Spec:** ACTION_ROADMAP.md §Phase 1, TestArchitecture.md §Rosetta Stone Coverage
**Prereq:** P127 DONE (SpatialContainerConfig auto-discovery)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** The IFC hierarchy has the structure. Read it. No invention.

## Read first

1. `PROGRESS.md` §Current State — RD/RL stall (0 elements)
2. `IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java` — how storeyElements is built
3. `IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionPopulator.java` — element grouping
4. Extraction DBs:
   ```sql
   -- In Infra_Road_extracted.db
   SELECT type, name, COUNT(*) FROM spatial_structure GROUP BY type;
   SELECT ss.type, ss.name, COUNT(em.id) FROM spatial_structure ss
     LEFT JOIN elements_meta em ON em.storey = ss.name GROUP BY ss.type, ss.name;
   ```
5. Same queries on Infra_Rail_extracted.db
6. IFC4X3 entities: IfcRoad → IfcRoadPart, IfcRailway → IfcRailwayPart (not IfcBuildingStorey)
7. New IFC files: `bSI_Earthworks_Road_IFC4X3.ifc`, `bSI_Railway_Track_IFC4X3.ifc`, `bSI_IF_Rail_Sleepers_IFC4X3.ifc`

## Problem

RD and RL produce 0 compiled elements. The extraction has elements but the
IFCtoBOM pipeline groups by `storey` (IfcBuildingStorey name). Infrastructure
files use IfcRoadPart / IfcRailwayPart / IfcFacilityPart instead — these
map to `spatial_structure.type` but the pipeline doesn't recognize them as
spatial containers.

P127's SpatialContainerConfig.discover() auto-discovers from `storeyElements`
keys — but if ExtractionPopulator doesn't populate `storeyElements` for
infrastructure types, discovery finds nothing.

## Fix

Extend ExtractionPopulator to recognize infrastructure spatial containers:
- `IfcRoadPart` → spatial container (same as IfcBuildingStorey)
- `IfcRailwayPart` → spatial container
- `IfcFacilityPart` → spatial container (generic for marine, tunnel)

The fix should be in ExtractionPopulator where elements are grouped by storey.
The grouping key should include ANY spatial_structure parent, not just
IfcBuildingStorey.

## Gate

```bash
rm library/RD_BOM.db library/RL_BOM.db
./scripts/run_RosettaStones.sh classify_rd.yaml
./scripts/run_RosettaStones.sh classify_rl.yaml
```
- RD: element count > 0, gates run
- RL: element count > 0, gates run
- SH: 7/7 PASS (regression check)

## What NOT to do

- Do NOT modify StructuralBomBuilder or ScopeBomBuilder
- Do NOT modify existing migration files
- Do NOT hardcode infrastructure type names — use spatial_structure.type generically
- **All logging via BIMLogger — no System.out.println**

## Commit

```bash
git add IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionPopulator.java PROGRESS.md
git commit -m "[S101-p135] Infrastructure walker: IfcRoadPart/RailwayPart/FacilityPart as spatial containers"
```

## When Done

Prepend `# DONE — [commit_hash]` to this file's first line.

Append findings below `---`:
- RD: element count, gate results
- RL: element count, gate results
- What spatial_structure types found in each extraction DB?
- SH regression check

---
