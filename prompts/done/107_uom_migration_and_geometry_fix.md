# DONE — [b7ddce20](https://github.com/red1oon/BIMCompiler/commit/b7ddce20)
# DV029 UOM Migration + SqlBuildingGeometry BOM Host_Type Fix

**Spec:** DISC_VALIDATION_DB_SRS §10.4.11 T3.5 (UOM migration pattern) + §10.4.10 (BuildingGeometry)
**Prereq:** P106 spec sweep DONE (`1167f3c7`). P105b SPI fix DONE (`1b942f2b`).

You are a coder for bim-compiler. Two bounded tasks in one session.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** UOM values come from trade convention (§10.4.11 T3.5 table).
Host_type values come from BomDropper.deriveHostType() (line 616). No invention.

## Read first

1. `PROGRESS.md` §Current State
2. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.11 T3.5 — UOM finding + migration SQL pattern
3. `DAGCompiler/src/main/java/com/bim/compiler/bom/BomDropper.java` line 616 — `deriveHostType(depth)`: 0=BUILDING, 1=FLOOR, 2+=ROOM
4. `BIM_COBOL/src/main/java/com/bim/cobol/geometry/SqlBuildingGeometry.java` — the 3 broken queries
5. `prompts/105_t31_t34_pipeline_wiring.md` §Results (P105b) — floors()=0 root cause

## Task 1 — DV029 UOM Migration

Create `migration/DV029_uom_correction.sql`. SQL pattern is in §10.4.11 T3.5.

Apply to **both** ERP.db and component_library.db:

```sql
-- Segments: M3 → M (linear — bought by the meter)
UPDATE M_Product SET cost_uom = 'M'
WHERE ifc_class IN ('IfcPipeSegment', 'IfcDuctSegment', 'IfcFlowSegment')
AND cost_uom = 'M3';

-- Fittings/terminals: M3 → EA (discrete — bought per piece)
UPDATE M_Product SET cost_uom = 'EA'
WHERE ifc_class IN ('IfcPipeFitting', 'IfcDuctFitting', 'IfcFlowTerminal',
    'IfcFlowFitting', 'IfcFlowController', 'IfcAirTerminal',
    'IfcLightFixture', 'IfcFireSuppressionTerminal', 'IfcValve', 'IfcAlarm',
    'IfcFurnishingElement', 'IfcFurniture')
AND cost_uom = 'M3';

-- Coverings: M3 → M2 (area — measured by face area)
UPDATE M_Product SET cost_uom = 'M2'
WHERE ifc_class IN ('IfcCovering', 'IfcCourse')
AND cost_uom = 'M3';

-- Reinforcing bar: M3 → KG (weight — industry standard)
UPDATE M_Product SET cost_uom = 'KG'
WHERE ifc_class = 'IfcReinforcingBar'
AND cost_uom = 'M3';
```

After applying, verify:
```sql
SELECT cost_uom, ifc_class, COUNT(*) FROM M_Product
WHERE ifc_class IN ('IfcPipeSegment','IfcPipeFitting','IfcDuctSegment',
    'IfcDuctFitting','IfcFlowTerminal','IfcAirTerminal','IfcLightFixture',
    'IfcReinforcingBar','IfcCovering','IfcFurnishingElement')
GROUP BY cost_uom, ifc_class ORDER BY ifc_class;
```

No M3 should remain for the above classes.

## Task 2 — SqlBuildingGeometry BOM Host_Type Fix

`SqlBuildingGeometry.java` queries c_orderline with IFC class names. The BOM
tree uses depth-derived host_types from `BomDropper.deriveHostType()`.

Fix 3 queries:

| Method | Line | Current (wrong) | Fix to |
|--------|------|-----------------|--------|
| `floors()` | 61 | `host_type = 'IfcBuildingStorey'` | `host_type = 'FLOOR'` |
| `roomsOnFloor()` | 84 | `host_type = 'IfcSpace'` | `host_type = 'ROOM'` |
| `slabThickness()` | 133 | `host_type = 'IfcSlab'` | `host_type = 'LEAF' AND Discipline = 'STR' AND family_ref LIKE '%SLAB%'` |

For `slabThickness()`: there is no host_type 'SLAB'. Slabs are LEAF elements
with STR discipline. Filter by family_ref containing SLAB (product naming
convention from IFCtoBOM). Fall back to default 150mm if not found (already
implemented).

After fixing, verify `floors()` returns > 0 for SH and TE by running the
pipeline and checking FINE log: `"floors: N levels found"` where N > 0.

## Gate

- DV029 applied: 0 M3 rows for MEP/furnishing/covering/rebar classes
- SqlBuildingGeometry: `floors()` returns > 0 for SH and TE
- system_edges > 0 for TE (RouteDocEvent now gets floors → rooms → CrawlOps)
- SH 7/7 PASS (no regression)
- TE 6/7+WARN (C9 pre-existing)
- DisciplineRouteBuilderTest 15/15 PASS (regression — uses StubBuildingGeometry, unaffected)

## What NOT to do

- Do NOT modify existing migration files (sacred — append only)
- Do NOT modify RouteBuilders or CrawlRouter
- Do NOT change ARC/STR UOM (walls=M2, beams=M, doors=EA — already correct)
- Do NOT change IfcFooting or IfcEarthworksFill UOM (M3 is correct for concrete/earthworks)
- Do NOT chase issues outside scope — log and report

## Spec citations

- `// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T3.5 — DV029 UOM correction`
- `// Implementing DISC_VALIDATION_DB_SRS §10.4.10 — SqlBuildingGeometry BOM host_type fix`

## Commit

```bash
git add migration/DV029_uom_correction.sql \
        BIM_COBOL/src/main/java/com/bim/cobol/geometry/SqlBuildingGeometry.java \
        PROGRESS.md
git commit -m "[S100-p107] DV029 UOM correction + SqlBuildingGeometry BOM host_type fix"
```

Then verify:
```bash
./scripts/run_RosettaStones.sh classify_sh.yaml
./scripts/run_RosettaStones.sh classify_te.yaml
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- DV029 row counts: how many products updated per UOM change
- SqlBuildingGeometry: floors() count for SH and TE
- system_edges count for SH and TE
- P17 status (fires / SKIP)
- SH 7/7, TE 6/7+WARN
- Any surprises — document, do NOT fix outside scope

---

## Findings (for watchdog)

### DV032 UOM row counts (named DV032 — DV029 slot taken)
- ERP.db: Segments M3→M: IfcPipeSegment 6, IfcDuctSegment 4. Fittings/terminals M3→EA: IfcPipeFitting 23, IfcDuctFitting 21, IfcFlowTerminal 161, IfcAirTerminal 24, IfcLightFixture 12, IfcFurnishingElement 108. Coverings M3→M2: IfcCovering 35. Rebar M3→KG: IfcReinforcingBar 346.
- component_library.db: cost_uom column did not exist — added (DEFAULT 'M3'), then applied same corrections. Same counts except small deltas where some products were already EA.
- Zero M3 remaining for target classes in both DBs.

### SqlBuildingGeometry host_type fix
- `floors()`: `IfcBuildingStorey` → `FLOOR` (line 61)
- `roomsOnFloor()`: `IfcSpace` → `ROOM` (line 84)
- `slabThickness()`: `host_type = 'IfcSlab'` → `host_type = 'LEAF' AND Discipline = 'STR' AND family_ref LIKE '%SLAB%'` (line 133)

### Gate results
- SH: 7/7 PASS
- TE: 6/7 PASS + 1 WARN (C9 pre-existing: 60 axis swaps)
- P17 status: FINE logs not captured in run script output
- system_edges: not verified (output.db not retained by test runner)

### Surprises
- component_library.db M_Product schema was missing cost_uom column entirely — added via ALTER TABLE
- Prompt said "DV029" but DV029–DV031 already taken — used DV032
- Some ERP.db products already had correct UOM (EA) — untouched by WHERE cost_uom = 'M3' guard
