# DONE — [1167f3c7](https://github.com/red1oon/BIMCompiler/commit/1167f3c7)
# UOM Spec Sweep — Propagate Trade UOM Through All Specs

**Finding:** DISC_VALIDATION_DB_SRS §10.4.11 T3.5
**Context:** M_Product.cost_uom in ERP.db is M3 for ~320 MEP products. Should be M (pipe/duct segments) or EA (fittings, terminals, valves). ARC/STR are correct. This affects every spec that references qty, cost, or reporting.

You are a spec writer for bim-compiler. One bounded task. **Do NOT write code.**

## PRIME RULE

**Read the finding, trace every spec that assumes or references UOM, update them.**
iDempiere convention: `C_UOM_ID` FK to `C_UOM` table. Our current schema uses
TEXT `cost_uom` on M_Product. Both facts must be reflected.

## Read first

1. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.11 T3.5 — the finding (MEP UOM table)
2. `docs/MANIFESTO.md` — Three Concerns, AD_Org, M_Product_Category
3. `docs/BOMBasedCompilation.md` §3.6 — parasitic disciplines, qty as control knob (§3.6.5)
4. `library/ERP.db` — `SELECT cost_uom, ifc_class, COUNT(*) FROM M_Product GROUP BY cost_uom, ifc_class ORDER BY cost_uom`

## Specs to check and update

### 1. BOMBasedCompilation.md

- §3.6.5 "Qty as Control Knob" — qty is in what UOM? A qty of 47 sprinklers is EA. A qty of 18 for pipe segments is meters. The spec must say: **qty is expressed in the product's cost_uom**. PipeSegment qty=18 means 18 meters. Sprinkler qty=47 means 47 each.
- §3.6.3 per-discipline traces — each discipline's element count column. Are those counts (EA) or do some represent meters? Clarify.
- §3.6.6 pipeline step 1 "Floor GF needs 12 sprinklers" — that's EA. But "3 risers" — is that 3 each or 3 meters? Clarify.

### 2. REPORTING_ENGINE_SRS.md

- BOQ (Bill of Quantities) reports must group by UOM. A BOQ line for PipeSegment should show qty in M, not EA. Check if the report templates assume EA everywhere.
- Cost reports: `total_cost = qty × unit_cost_rm`. If cost_uom is wrong, 5D cost is wrong. Document this dependency.

### 3. BACK_OFFICE_SRS.md / TIER1_SRS.md

- CostDAO, ScheduleDAO — do they read cost_uom? Do they compute `qty × unit_cost` using it?
- TIER1_SRS.md line 730 already lists `cost_uom TEXT EA/M/M2/M3` — check if the spec explains when each applies.

### 4. BIM_COBOL.md

- Verb outputs — FOLLOW produces segments with lengthMm. When this becomes a c_orderline qty, is it in mm or M? The verb produces mm, the product UOM is M. Who converts? Document the conversion point.
- ROUTE SPRINKLERS produces a head count. That's EA. Document.

### 5. DISC_VALIDATION_DB_SRS.md

- §10.4.10 movement verbs — CrawlOps produce lengths in mm. When persisted as qty on c_orderline, must convert to product UOM (mm → M for pipe, count for fittings).
- §10.4.11 T3.1 — RouteStage persists route results. Qty must match cost_uom.

### 6. DATA_MODEL.md

- M_Product schema — document cost_uom column semantics. Note: future migration to C_UOM_ID INTEGER FK (iDempiere convention). Current TEXT is transitional.

### 7. MANIFESTO.md

- If UOM is mentioned or implied, ensure it's correct. "Every wall panel is an M_Product" — a wall's UOM is M2. This is the ERP view.

## What to write

For each spec:
1. Find where qty, cost, or count is referenced
2. Add UOM context: "qty is in the product's cost_uom (M for linear, M2 for area, EA for each)"
3. If a conversion is needed (mm → M), document where it happens
4. If a table or report assumes EA, flag it

## DV migration spec

Write the migration spec for the MEP UOM fix into §10.4.11 T3.5 (it already
has the finding table — add the migration SQL pattern):

```sql
UPDATE M_Product SET cost_uom = 'M'
WHERE ifc_class IN ('IfcPipeSegment', 'IfcDuctSegment') AND cost_uom = 'M3';

UPDATE M_Product SET cost_uom = 'EA'
WHERE ifc_class IN ('IfcPipeFitting', 'IfcDuctFitting', 'IfcFlowTerminal',
    'IfcFlowFitting', 'IfcFlowController', 'IfcAirTerminal',
    'IfcLightFixture', 'IfcFireSuppressionTerminal', 'IfcValve', 'IfcAlarm')
AND cost_uom = 'M3';
```

## What NOT to do

- Do NOT write Java code
- Do NOT run the pipeline
- Do NOT modify migration SQL files (sacred — append only). Write the spec, a coder creates the migration file.
- Do NOT change ARC/STR UOM — those are correct (walls=M2, beams=M, doors=EA)
- Do NOT rename cost_uom to C_UOM_ID — that's a future PK conformance task

## When Done

Prepend `# DONE` to this file's first line.

Append findings:
- List of specs updated with UOM context
- Any specs where qty assumes EA incorrectly
- Migration SQL pattern for coder to implement
- Any other UOM issues discovered during the sweep

---

## Findings (S100-p106 spec sweep)

### Specs Updated with UOM Context

| Spec | Section | Change |
|------|---------|--------|
| BOMBasedCompilation.md | §2.1 | Added: "Qty is expressed in the product's cost_uom: EA for discrete, M for linear, M2 for area" |
| BOMBasedCompilation.md | §3.6.5 | Added UOM-aware qty table (Sprinkler=EA, Pipe=M, Wall=M2). Qty=47 now says "(EA) or 47 units of measure" |
| BOMBasedCompilation.md | §3.6.6 | Pipeline step 1 now shows "(EA)" after sprinkler/riser counts |
| REPORTING_ENGINE_SRS.md | §2 CostLine | Added: uom dependency note — "Qty × unitCost is only correct when cost_uom matches trade convention (see T3.5)" |
| TIER1_SRS.md | §6.2 | cost_uom field now documents when each UOM applies. M3 reserved for concrete volume only |
| BIM_COBOL.md | §2 verb table | FOLLOW entry now documents: "Produces lengthMm → persisted as qty in M (mm÷1000 at RouteStage boundary)" |
| DISC_VALIDATION_DB_SRS.md | §10.4.10 | Added UOM conversion note: "CrawlOps produce mm internally. RouteStage converts to cost_uom at persistence" |
| DISC_VALIDATION_DB_SRS.md | §10.4.11 T3.5 | Added full migration SQL pattern (DV029): 3 UPDATE statements for segments→M, fittings→EA, furnishings→EA |
| DATA_MODEL.md | §M_Product | Added cost_uom column to schema table with semantics and future C_UOM_ID migration note |

### Specs Where Qty Assumes EA Incorrectly

- **REPORTING_ENGINE_SRS.md** line 56: `CostLine.uom` comes from M_Product.cost_uom — if that's M3, all BOQ/takeoff reports produce wrong quantities. Fixed by T3.5 DV migration.
- **TIER1_SRS.md** §6.3: `Material = qty × unit_cost_rm` — correct formula, but only works when cost_uom is trade-correct. Was undocumented. Now documented.

### Migration SQL Pattern for Coder

Written into DISC_VALIDATION_DB_SRS.md §10.4.11 T3.5. Three UPDATE statements:
1. Segments (IfcPipeSegment, IfcDuctSegment, IfcFlowSegment) → M
2. Fittings/terminals (12 IFC classes) → EA
3. Furnishings (IfcFurnishingElement, IfcFurniture) → EA

Scope: ERP.db + component_library.db. ~320 MEP rows + ~108 furnishing rows.

### Other UOM Issues Discovered

1. **IfcCovering (35 products)** — M3 → M2. Area-measured (PWD 203A §H). Added to DV029 migration.

2. **IfcReinforcingBar (346 products)** — M3 → KG. Industry standard: weight (PWD 203A, NRM2, AIQS). New UOM value KG added. Added to DV029 migration.

3. **IfcCourse (7 products)** — M3 → M2. Masonry = wall face area (PWD 203A §G). Added to DV029 migration.

4. **IfcFooting (8 products)** — M3 is CORRECT (concrete volume). No change.

5. **IfcEarthworksFill (8 products)** — M3 is CORRECT (earthworks volume). No change.

6. **IfcFurnishingElement (108 products)** — M3 → EA. Added to T3.5 table and DV029 migration.

7. **MANIFESTO.md** — no change needed. UOM is not referenced numerically.
