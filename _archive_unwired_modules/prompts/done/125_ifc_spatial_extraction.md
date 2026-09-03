# DONE — [3e056227](https://github.com/red1oon/BIMCompiler/commit/3e056227) + [903ec0cc](https://github.com/red1oon/BIMCompiler/commit/903ec0cc) + [f0b6c900](https://github.com/red1oon/BIMCompiler/commit/f0b6c900)
# IFC-Driven Extraction — Replace YAML Scope Boxes with IFC Spatial Containment

**Spec:** DISC_VALIDATION_DB_SRS §10.4.13
**Prereq:** None (extraction rewrite, independent of compilation)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** The IFC spatial data already exists in the extraction DB. Read it. No invention.

## Read first

1. `PROGRESS.md` §Current State
2. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.13 — the full spec for this work
3. `IFCtoBOM/src/main/java/com/bim/ifctobom/ScopeBomBuilder.java` — current scope box logic (lines 86-108 centroid containment)
4. `IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java` — RE path (line 279 calls ScopeBomBuilder)
5. `IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java` — parses YAML spaces with origin_m/aabb_mm
6. `DAGCompiler/lib/input/SampleHouse_extracted.db` — run these queries to see what IFC gives:

```sql
-- Spatial structure
SELECT type, name, parent_guid FROM spatial_structure ORDER BY type, name;

-- Elements per space
SELECT ss.name, COUNT(*) FROM rel_contained_in_space rc
JOIN spatial_structure ss ON rc.space_guid = ss.guid
GROUP BY ss.name;

-- Orphans (not in any space)
SELECT COUNT(*) FROM elements_meta
WHERE guid NOT IN (SELECT element_guid FROM rel_contained_in_space);

-- Host relationships
SELECT COUNT(*) FROM rel_fills_host;
```

SH result: 14 elements in spaces, 44 orphans (structural), 7 host relationships.

## Problem

`ScopeBomBuilder` assigns elements to SET BOMs using YAML scope boxes —
human-authored rectangular volumes (`origin_m`, `aabb_mm`). The human must
measure coordinates from the IFC file and type them into the YAML.

The IFC file already knows which elements are in which room via
`IfcRelContainedInSpatialStructure` → `rel_contained_in_space` table in
the extraction DB. This data is extracted by `RosettaStoneExtract.py` but
never used by the Java pipeline.

## Fix — Three Changes

### 1. ClassificationYaml: support `ifc_space` key alongside scope boxes

Add an optional `ifc_space` field to SpaceConfig. When present, it
replaces `origin_m`/`aabb_mm` as the containment mechanism:

```yaml
# New format (IFC-driven):
- { ifc_space: "1 - Living room", template_bom: SH_LIVING_SET, role: LIVING, seq: 10 }

# Old format (scope box, still supported as fallback):
- { name: LIVING, template_bom: SH_LIVING_SET, role: LIVING, seq: 10,
    aabb_mm: [8000, 2000, 1200], origin_m: [-7.0, 2.5, 0.0] }
```

Both formats work. `ifc_space` takes priority. Scope box is fallback for:
- Sub-room zones (dining zone within Living room)
- Buildings without IfcSpace data
- Infrastructure (no IfcSpace concept)

### 2. ScopeBomBuilder: read `rel_contained_in_space` when `ifc_space` is present

Replace centroid-in-box test (lines 93-108) with IFC containment lookup:

```java
if (space.ifcSpace() != null) {
    // IFC containment: read rel_contained_in_space from extraction DB
    assigned = loadElementsInSpace(extractionDb, space.ifcSpace(), elems);
} else {
    // Fallback: scope box containment (existing logic)
    assigned = filterByScopeBox(elems, space);
}
```

`loadElementsInSpace()` queries:
```sql
SELECT rc.element_guid FROM rel_contained_in_space rc
JOIN spatial_structure ss ON rc.space_guid = ss.guid
WHERE ss.name = ?
```

Then matches against the extraction element list by GUID.

**Connection:** ScopeBomBuilder currently receives a Connection to BOM DB
(`bomConn`). It needs a second connection to the extraction DB to read
`rel_contained_in_space`. Pass it from the pipeline:
`ScopeBomBuilder.build(bomConn, extractionConn, config, storeyElements, catLookup)`

### 3. Update SH YAML as proof of concept

Replace SH's scope box spaces with `ifc_space` references:

```yaml
# Before:
spaces:
  - { name: LIVING, template_bom: SH_LIVING_SET, role: LIVING, seq: 10,
      aabb_mm: [8000, 2000, 1200], origin_m: [-7.0, 2.5, 0.0] }
  - { name: DINING, template_bom: SH_DINING_SET, role: DINING, seq: 20,
      aabb_mm: [2500, 1500, 1300], origin_m: [-6.5, -0.3, 0.0] }
  - { name: MASTER, template_bom: SH_BED_SET, role: MASTER, seq: 30,
      aabb_mm: [2200, 3500, 800], origin_m: [4.0, 1.5, 0.0] }
  - { name: BATHROOM, template_bom: TOILET_BLOCK_FIXTURES, role: BATHROOM, seq: 40,
      aabb_mm: [0, 0, 0] }

# After:
spaces:
  - { ifc_space: "1 - Living room", template_bom: SH_LIVING_SET, role: LIVING, seq: 10 }
  - { ifc_space: "2 - Bedroom", template_bom: SH_BED_SET, role: MASTER, seq: 30 }
```

Note: SH's "Living room" IfcSpace contains BOTH dining and living furniture
(12 elements in one space). With `ifc_space`, all 12 go to SH_LIVING_SET.
The separate DINING scope box is lost — this is a trade-off. Document in
findings whether this matters for BOM structure.

BATHROOM has 0 elements (no sanitary in SH IFC) — can be omitted.
Entrance hall has 0 elements — omitted.

### What NOT to change

- `StructuralBomBuilder` — unchanged, still picks up orphans
- `CompositionBomBuilder` — unchanged, still handles DX mirror
- `DisciplineBomBuilder` — unchanged, CO path doesn't use scope boxes
- `VerbDetector` — unchanged, still groups within each SET
- `FloorRoomBomBuilder` — unchanged, still creates room hierarchy
- Compilation pipeline — unchanged, reads from BOM.db as before
- **Do NOT remove scope box support** — keep as fallback

## Gate

Run SH with IFC-driven extraction:
```bash
rm library/SH_BOM.db
./scripts/run_RosettaStones.sh classify_sh.yaml
```
- SH 7/7 PASS (no regression)
- BOM structure: how many SET BOMs? How many lines per SET?
- Compare: IFC-driven vs scope-box counts — same elements assigned?
- FINE log: "IFC containment: space='1 - Living room' → 12 elements"

Run FK (check if FK IFC has IfcSpace data):
```bash
rm library/FK_BOM.db
./scripts/run_RosettaStones.sh classify_fk.yaml
```
- If FK has IfcSpace → test IFC-driven path
- If FK has no IfcSpace → scope box fallback works
- FK 7/7 PASS

## What NOT to do

- Do NOT remove scope box support from ClassificationYaml
- Do NOT modify the compilation pipeline (BOMWalker, PlacementCollectorVisitor)
- Do NOT modify existing migration files
- Do NOT modify StructuralBomBuilder or DisciplineBomBuilder
- Do NOT modify VerbDetector — CLUSTER will still appear for same-product groups
- Do NOT force IFC-driven mode on buildings that lack IfcSpace
- **If `rel_contained_in_space` is empty, fall back to scope boxes silently**

## Spec citation

```java
// Implementing DISC_VALIDATION_DB_SRS §10.4.13 — IFC-driven extraction
// IfcRelContainedInSpatialStructure replaces YAML scope boxes
```

## Commit

```bash
git add IFCtoBOM/src/main/java/com/bim/ifctobom/ScopeBomBuilder.java \
        IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java \
        IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java \
        IFCtoBOM/src/main/resources/classify_sh.yaml \
        PROGRESS.md
git commit -m "[S100-p125] IFC-driven extraction: rel_contained_in_space replaces YAML scope boxes"
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- SH IFC-driven: how many elements assigned by IfcSpace vs scope box?
- BOM structure: SET BOMs, lines per SET, CLUSTER % before/after
- FK: does IFC have IfcSpace data?
- Which buildings in the fleet have IfcSpace data? (quick check of extraction DBs)
- Trade-off: SH Living room merges dining+living — acceptable?
- Any surprises — document, do NOT fix

---

# FINDINGS — S100-p125

## SH IFC-driven extraction

- Living room (IfcSpace "1 - Living room"): 12 elements assigned
- Bedroom (IfcSpace "2 - Bedroom"): 2 elements assigned
- 44 orphans → StructuralBomBuilder (unchanged)
- Total: 14 IFC-assigned + 44 structural = 58 elements (matches extraction count)

## BOM structure comparison

| Metric | Scope box (before) | IFC-driven (after) |
|--------|-------------------|-------------------|
| SET BOMs | 4 (LIVING, DINING, MASTER, BATHROOM) | 2 (LIVING, MASTER) |
| Living lines | 5 lines (8 instances) | 7 lines (12 instances) |
| Dining lines | 4 lines (4 instances) | merged into Living |
| Bedroom lines | 2 lines (2 instances) | 2 lines (2 instances) |
| Bathroom lines | 0 (empty SET) | removed (0 IFC elements) |

## FK: no IfcSpace data

FK extraction DB has 0 rows in rel_contained_in_space. FK YAML has no floor_rooms section, so ScopeBomBuilder is not called. No change. FK 6/7 (compile test failure pre-existing from p122).

## Fleet IfcSpace coverage

19 of 45 extraction DBs have rel_contained_in_space data:
AC90_Jasmin (14), BimWhale_Advanced (1749), BimWhale_Large (14), Building_Architecture (1), Clinic_Architecture (219), Clinic_Electrical (2082), Clinic_HVAC (2103), Esplanades (1941), HITOS (2068), Ifc2x3_AC11Institute (253), Duplex (61), SampleHouse (14), Jesse (626), Molio (3114), Revit_ARC (442), HospitalAuckland (6787), Revit_STR (4133), SampleCastle (12), Schependomlaan (3214).

## Trade-off: Living room merges dining+living

SH's IFC model has one IfcSpace ("1 - Living room") containing both dining and living furniture (12 elements). With scope boxes, these were split into LIVING (8) and DINING (4). With IFC-driven, all 12 go to SH_LIVING_SET.

Acceptable: the IFC author modeled it as one room. The YAML scope box was the human invention — the IFC data is authoritative. If sub-room zones are needed later, scope box remains as fallback.

## Surprises

1. SH IfcSpace data is sparse: only 14/58 elements have spatial containment. The 44 structural elements (walls, floors, roof) are NOT in any IfcSpace — they go to StructuralBomBuilder as before. This is correct: IfcSpace contains furnishings/openings, not the structure itself.

2. No code changes needed in ScopeBomBuilder's AABB/PHANTOM computation. The assigned elements list is the same shape regardless of containment method — downstream processing (AABB, factorization, PHANTOM) works unchanged.

## P125b — Fully IFC-driven ScopeBomBuilder (903ec0cc)

ScopeBomBuilder rewritten to auto-discover IfcSpaces from extraction DB. No YAML floor_rooms iteration. Scans spatial_structure for IfcSpace entries, joins rel_contained_in_space for element GUIDs, resolves parent storey via parent_guid. SET BOM IDs auto-generated: {PREFIX}_{SPACE}_SET.

SH YAML: floor_rooms section removed entirely. IFC containment is sole source.
- SH_1_LIVING_ROOM_SET: 7 lines, 12 instances
- SH_2_BEDROOM_SET: 2 lines, 2 instances
- SH_ROOM_GF: auto-generated intermediate parent BOM

## P125c — FloorRoomBomBuilder → BomHierarchyBuilder (f0b6c900)

Renamed to generic parent→child linker. No level-specific names (no "Floor", no "Room" in class name). Links any parent BOM to children with LBD-to-LBD tack offsets.

Critical fix: storeyLbdWorld was computed from config.floorRooms() (YAML-dependent, empty for IFC-driven buildings). Changed to compute from storeyElements (all storeys with extraction data). This fixed (0,0,0) intermediate offsets that caused 13m GEO drift.

SH 7/7 PASS, GEO 58 elements 1653 pairs worst=0.000mm DRIFT=0.

## Fleet BOM health (SpecsPerson audit, 31 buildings)

| Status | Buildings | Notes |
|--------|-----------|-------|
| All QA PASS | 18 | BS, BH, BA, BR, SC, MO, WT, WI, IP, ES, CS, JE, RA, GH, WA, WB, WL, RS |
| W-BUFFER-1 FAIL | 4 | CA (57/76), CE, CH, Clinic_HVAC — SET BOMs no AABB dims (expected, no YAML) |
| W-TACK-1 FAIL | 1 | CE — 4/1131 lines overshoot parent AABB |
| Duplicate positions | 3 | HospitalAuckland (1), HITOS (4), Infra_Road (8) |
| Extraction delta | 1 | SampleCastle (-6 elements lost) |
| EMPTY (0 bytes) | 4 | CL, CP, DX, TE — mid-rebuild |

BOMs well-formed across fleet. Failures are downstream (compilation proofs, not BOM structure). YAML is no longer source for IFCtoBOM spatial assignment.
