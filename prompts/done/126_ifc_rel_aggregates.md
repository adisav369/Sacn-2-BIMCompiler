# DONE — [48d14537](https://github.com/red1oon/BIMCompiler/commit/48d14537)
# IFC IfcRelAggregates Extraction — Assembly BOMs from Parent-Child Decomposition

**Spec:** DISC_VALIDATION_DB_SRS §10.4.13 (continued)
**Prereq:** P125 DONE (IFC-driven ScopeBomBuilder)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** The IFC decomposition already exists. Read it. No invention.

## Read first

1. `PROGRESS.md` §Current State
2. `scripts/RosettaStoneExtract.py` — current extraction tables
3. `DAGCompiler/lib/input/SampleHouse_extracted.db` — check what tables exist
4. SH IFC file: how many IfcRelAggregates relationships?
   - Curtain wall = 1 IfcCurtainWall parent with 26 children (20 IfcMember + 6 IfcPlate)
   - This is NOT extracted today — RosettaStoneExtract.py ignores IfcRelAggregates

## Problem

The extraction flattens IFC assemblies to individual elements. A curtain wall
with 20 mullions + 6 glazing panels becomes 26 flat leaves in extraction.
The BOM correctly groups them via CLUSTER verb, but the parent-child
relationship from the IFC is lost.

`IfcRelAggregates` in the IFC file records which elements are children of
which assembly. Extracting this into a `rel_aggregates` table would let
IFCtoBOM auto-create assembly BOMs (curtain wall, stair assembly, etc.)
from the IFC structure — without heuristic grouping.

## Fix

### 1. RosettaStoneExtract.py — add rel_aggregates table

```sql
CREATE TABLE rel_aggregates (
    parent_guid TEXT NOT NULL,
    child_guid TEXT NOT NULL,
    PRIMARY KEY (parent_guid, child_guid)
);
```

Populate from `IfcRelAggregates` relationships in the IFC file.

### 2. Verify on SH and DX

Query the new table to see what assemblies exist:
```sql
SELECT m.ifc_class, m.element_name, COUNT(ra.child_guid)
FROM elements_meta m
JOIN rel_aggregates ra ON m.guid = ra.parent_guid
GROUP BY m.guid
ORDER BY COUNT(ra.child_guid) DESC;
```

### 3. Do NOT change IFCtoBOM Java yet

This prompt extracts the data only. The Java pipeline change to read
rel_aggregates and create assembly BOMs is a follow-up prompt.

## BIM.properties

Set before running gate:
```properties
bim.log.level=INFO
bim.geo.debug=true
```

## Gate

- Re-extract SH: `rm DAGCompiler/lib/input/SampleHouse_extracted.db && python3 scripts/RosettaStoneExtract.py ...`
- `SELECT COUNT(*) FROM rel_aggregates` > 0
- SH pipeline: 7/7 PASS (no regression — Java doesn't read the new table yet)

## What NOT to do

- Do NOT modify Java code (IFCtoBOM, DAGCompiler)
- Do NOT modify existing extraction tables
- Do NOT modify migration files
- **All logging via BIMLogger — no System.out.println**

## Spec citation

```python
# Implementing DISC_VALIDATION_DB_SRS §10.4.13 — IfcRelAggregates extraction
# Parent-child decomposition for assembly BOMs
```

## Commit

```bash
git add scripts/RosettaStoneExtract.py PROGRESS.md
git commit -m "[S100-p126] IfcRelAggregates extraction: parent-child assembly decomposition"
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- SH rel_aggregates row count
- DX rel_aggregates row count
- Top 5 assemblies by child count (ifc_class, element_name, child_count)
- SH 7/7 PASS?
- Any surprises — document, do NOT fix

---

## Findings

**SH rel_aggregates:** 34 rows. Top assemblies: 2 parents with 13 children each (curtain wall halves), 1 with 3 children, 2 with 2 children, 2 singletons.

**DX rel_aggregates:** 38 rows. Top assemblies: 2 parents with 10 children each (stair assemblies), 2 with 5 children, 1 with 4, 3 singletons.

**Top 5 assemblies (SH):** Parent GUIDs not in elements_meta (IfcCurtainWall/IfcStair not in extraction class list). Children are IfcMember + IfcPlate. Assembly structure visible only via rel_aggregates join to elements_meta on child_guid.

**SH 7/7 PASS** — Java doesn't read the new table yet.

**Surprise:** Parent GUIDs have no entry in elements_meta — IfcCurtainWall and IfcStair are not extracted as elements. P129 will need to handle "phantom parents" (assembly parent exists only in rel_aggregates, not in elements_meta).
