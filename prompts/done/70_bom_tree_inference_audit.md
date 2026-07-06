# DONE
# BOM Tree Inference Audit — Retire bom_type Strings + Stale Terms

**Priority:** Spec alignment. The project has moved to pure BOM tree inference
(DISC_VALIDATION_DB_SRS.md §10.4.5) but docs and code still reference bom_type
strings, `component_type='MAKE'`, and `building_id` patterns that conflict with
the new model.

You are a watchdog for bim-compiler. Audit and flag — no production code changes.

## PRIME RULE

**READ AND REPORT ONLY.** Flag every conflict. Fix only documentation.
Code changes go into prompt 71.

## Read first

1. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.5 — the authoritative model:
   - Root = BOM with no parent m_bom_line pointing to it
   - Tier = M_Product_Category at each level
   - Leaf = child product with IsBOM=N (no m_bom row)
   - bom_type is legacy label, code must not branch on it

2. `docs/BOMBasedCompilation.md` §2.2.1 — "component_type does not exist in
   compilation. The walker decides BOM-vs-leaf purely by whether child_product_id
   has a matching m_bom row. The column remains for iDempiere compatibility;
   code must never branch on it."

3. `docs/MANIFESTO.md` — the universal BOM model (recent S100 edits).
   Check for any remaining references to bom_type, MAKE-as-relationship,
   building_id as identifier.

## The Three Stale Patterns

### Pattern 1: `bom_type` string matching

**Wrong:** `WHERE bom_type = 'BUILDING'` to find root.
**Correct:** Root is the BOM with no parent line — `WHERE NOT EXISTS
(SELECT 1 FROM m_bom_line WHERE child_product_id = b.bom_id)`.
Or pragmatically: `MBOM.getRoots(conn)`.

**Wrong:** `WHERE bom_type = 'FLOOR'` to find children.
**Correct:** Children are m_bom_line rows under the root BOM.
Category (M_Product_Category) determines what kind of child.

### Pattern 2: `component_type = 'MAKE'` as relationship term

**Wrong:** "no parent MAKE line" (in §10.4.5 itself).
**Correct:** "no parent m_bom_line" — the relationship IS the line.
component_type is iDempiere manufacturing legacy (MAKE/BUY/PHANTOM).
BBC §2.2.1 says: never branch on it. The walker checks m_bom existence.

The verb_ref column carries the placement strategy (PLACE, ROUTE, FRAME,
TILE, WIRE). This is the §10.4.1 Strategy pattern. verb_ref and
component_type are separate concerns:
- component_type: manufacturing (ignored by compiler)
- verb_ref: placement strategy (used by compiler)

### Pattern 3: building_id as loose identifier

MANIFESTO §AD_ChangeLog has `building_id` as a changelog column. This is
fine for the changelog table itself. But audit whether any code uses
`building_id` when it should use the BOM root's `bom_id` or `M_Product_ID`.

## Task 1: Audit all docs for stale patterns

Search every `docs/*.md` file for:

```
grep -rn 'bom_type' docs/
grep -rn 'getByType' docs/
grep -rn "component_type.*MAKE\|MAKE.*line\|parent MAKE" docs/
grep -rn 'building_id' docs/
```

For each hit, classify as:
- **CONFLICT** — contradicts §10.4.5 / BBC §2.2.1
- **LEGACY NOTE** — correctly marked as legacy/deprecated
- **STALE** — outdated but not directly contradictory

## Task 2: Audit Java code for bom_type branching

Search all Java files for:

```
grep -rn 'bom_type' --include='*.java' .
grep -rn 'getByType' --include='*.java' .
grep -rn '"BUILDING"\|"FLOOR"\|"ROOM"\|"SET"\|"ITEM"' --include='*.java' .
```

For each hit, classify as:
- **BRANCH** — code branches on bom_type (must change in prompt 71)
- **DISPLAY** — used for logging/display only (cosmetic, low priority)
- **WRITE** — code writes bom_type value (must change in prompt 71)
- **TEST** — test assertion on bom_type (must update with code)

## Task 3: Fix §10.4.5 language

In `docs/DISC_VALIDATION_DB_SRS.md` §10.4.5, replace "no parent MAKE line"
with "no parent m_bom_line" (or "no parent BOM line"). The section itself
should not use the term it's deprecating.

Same for any other §10.4.x references to MAKE as a relationship term.

## Task 4: Fix MANIFESTO stale references

Update any MANIFESTO.md references that conflict with the tree-inference
model. The MANIFESTO should be the cleanest expression of the architecture.

## Task 5: Produce the conflict register

Write a summary table in this prompt file (after `# DONE`):

```
| File | Line | Pattern | Classification | Suggested Fix |
|------|------|---------|---------------|---------------|
| MANIFESTO.md | 436 | building_id | LEGACY NOTE | OK — changelog column |
| BomValidator.java | 135 | bom_type = 'BUILDING' | BRANCH | → getRoots() |
| ... | ... | ... | ... | ... |
```

This register becomes the work order for prompt 71.

## What NOT to do

- Do NOT change Java production code (that's prompt 71)
- Do NOT change test code
- Do NOT weaken any existing specs
- Do NOT modify sacred files

## When Done

Prepend `# DONE` to this file's first line.
Append the conflict register after `# DONE`.

---

# Conflict Register — BOM Tree Inference Audit

## Doc fixes applied this session

| File | Line | Old | New | Status |
|------|------|-----|-----|--------|
| DISC_VALIDATION_DB_SRS.md | 595 | "no parent MAKE line" | "no parent m_bom_line" | **FIXED** |
| DISC_VALIDATION_DB_SRS.md | 607 | "BOM with no parent MAKE line" | "BOM with no parent m_bom_line" | **FIXED** |
| DISC_VALIDATION_DB_SRS.md | 608 | "MAKE lines under root" | "m_bom_line rows under root" | **FIXED** |
| DATA_MODEL.md | 100 | "no parent MAKE line" | "no parent m_bom_line" | **FIXED** |
| DATA_MODEL.md | 213 | "no parent MAKE line" | "no parent m_bom_line" | **FIXED** |

## Pattern 1: bom_type string matching — Docs

| File | Line | Pattern | Classification | Suggested Fix |
|------|------|---------|---------------|---------------|
| DISC_VALIDATION_DB_SRS.md | 590-626 | §10.4.5 bom_type section | LEGACY NOTE | OK — this IS the deprecation spec |
| DATA_MODEL.md | 100,213 | bom_type column description | LEGACY NOTE | OK — marked as legacy label |
| InfrastructureAnalysis.md | 100 | `bom_type = 'BUILDING'` | LEGACY NOTE | OK — marked RESOLVED with §10.4.5 link |
| InfrastructureAnalysis.md | 200 | "Should bom_type be FACILITY/SEGMENT" | STALE | Decision resolved: use M_Product_Category |
| InfrastructureAnalysis.md | 466 | "keep BUILDING/FLOOR as abstract bom_type" | STALE | Recommend: append note that §10.4.5 settled this |
| VIEW_CONTRACTS.md | 306-867 | bom_type bind parameter throughout | CONFLICT | v_qualified_bom uses bom_type as tier selector. Must migrate to M_Product_Category. Major refactor for prompt 71 |
| CALIBRATION_SRS.md | 215 | `m_bom WHERE bom_type='FLOOR'` | STALE | Should reference M_Product_Category or BOM tree |
| USER_GUIDE.md | 300 | INSERT INTO m_bom (bom_type...) | LEGACY NOTE | OK — column exists, INSERT is valid |
| BIM_Designer.md | 308 | `bom_type=BUILDING` | STALE | Should say "root BOMs" |
| BIM_Designer.md | 322 | `bom_type = 'FILLER'` | CONFLICT | FILLER not in CHECK constraint. Invalid value |
| BIM_Designer.md | 789 | `parent_bom_type TEXT` | STALE | Should use M_Product_Category |
| BIM_Designer_SRS.md | 2201 | `m_bom WHERE bom_type='BUILDING'` | CONFLICT | → use getRoots() or tree query |
| BIM_Designer_SRS.md | 2480 | `m_bom.bom_type has no 'ASSEMBLY'` | LEGACY NOTE | OK — documenting absence |
| SYSTEMS_INSTALLER_GUIDE.md | 105 | `SELECT bom_type` | LEGACY NOTE | OK — read for display |
| BIM_COBOL.md | 1348,1644 | `bom_type=SET`, `bom_type=FLOOR` | LEGACY NOTE | OK — BIM COBOL verb creates rows |
| DocValidate.md | 1752 | `host_type = bom_type` | STALE | Should use M_Product_Category |
| TODO.txt | 16 | `bom_type tier` | STALE | Outdated note |
| DuplexAnalysis.md | 214 | `bom_type=SET` | LEGACY NOTE | OK — describing extraction output |
| TerminalAnalysis.md | 1527 | `bom_type='BUILDING'` | LEGACY NOTE | OK — describes QA exclusion |
| SyntheticRosettaStone.txt | 515 | `WHERE bom_type = ?` | STALE | Synthetic example should use tree query |
| ID_NAME_VALUE_STUDY.md | 302 | BOMTypeSystem references | LEGACY NOTE | OK — documenting existing code |

## Pattern 1: bom_type string matching — Java code

| File | Line | Pattern | Classification | Suggested Fix |
|------|------|---------|---------------|---------------|
| **MBOM.java** (ORM) | 42-66 | `getByType(conn, bomType)` WHERE bom_type=? | **BRANCH** | → getRoots(), getChildren(parentBomId), getByCategory() |
| **CompilationPipeline.java** | 587 | `MBOM.getByType(conn, "FLOOR")` | **BRANCH** | → get children of root by tree walk |
| **PlacementLoader.java** | 259 | `MBOM.getByType(conn, "BUILDING")` | **BRANCH** | → MBOM.getRoots(conn) |
| **BomDropper.java** | 146 | `WHERE bom_type = 'BUILDING'` | **BRANCH** | → tree root query |
| **BuildingRegistry.java** | 144 | `b.bom_type = 'BUILDING'` | **BRANCH** | → tree root query |
| **PortfolioDAO.java** | 268 | `b.bom_type = 'BUILDING'` | **BRANCH** | → tree root query |
| **SustainabilityDAO.java** | 213 | `WHERE bom_type = 'FLOOR'` | **BRANCH** | → children of root |
| **DesignerDAO.java** | 63,99,128,142,167,175,203,228,240,471 | Multiple bom_type queries | **BRANCH** | → tree/category queries |
| **DesignerAPIImpl.java** | 218 | `b.bom_type = 'BUILDING'` | **BRANCH** | → tree root query |
| **WebUIServer.java** | 327,540 | `GROUP BY bom_type`, `bom_type = 'BUILDING'` | **BRANCH** (327) / **BRANCH** (540) | → category grouping / root query |
| **CalibrationDAO.java** | 153,179 | `bom_type = 'FLOOR'`, `bom_type = 'SET'` | **BRANCH** | → category queries |
| **ViewAccessLayer.java** | 20 | `WHERE bom_type = ?` | **BRANCH** | → M_Product_Category bind |
| **QualifiedBomCascade.java** | 12 | "bom_type bind is mandatory" | DISPLAY | → update comment to M_Product_Category |
| **BomValidator.java** | 109 | `GROUP BY bom_type` | DISPLAY | OK — summary logging |
| **BomValidator.java** | 135,227,235,252,260,298,307,465,701 | `WHERE bom_type = 'BUILDING'/'FLOOR'/'SET'` | **BRANCH** | → tree/category queries |
| **HelloWorldVerb.java** | 84 | `WHERE bom_type = 'BUILDING'` | **BRANCH** | → tree root query |
| **BuildSpatialStructureVerb.java** | 94 | `WHERE bom_type = 'BUILDING'` | **BRANCH** | → tree root query |
| **VerifyPlacementVerb.java** | 87,93 | `WHERE bom_type = 'BUILDING'` | **BRANCH** | → tree root query |
| **ListBomVerb.java** | 44,48 | `MBOM.getByType(conn, "BUILDING")` + loop | **BRANCH** | → tree walk |
| **ReportBomCatalogVerb.java** | 53 | `MBOM.getByType(conn, type)` | **BRANCH** | → category query |
| **ComposePrefabBomVerb.java** | 79 | INSERT with bom_type column | **WRITE** | Column must still be written; value OK |
| **CreateBomVerb.java** | 17,74 | Validates bom_type against VALID_TYPES | **BRANCH** | → remove validation or keep as legacy label guard |
| **MCDocType.java** | 59 | `WHERE bom_type = 'BUILDING'` | **BRANCH** | → tree root query |
| **TopologyAccessLayer.java** | 137,140 | `WHERE bom_type = ?` count | **BRANCH** | → category count |
| **TopologyBatchProcess.java** | 183 | passes "FLOOR" string | **WRITE** | Column write — keep but note legacy |
| **ShapeIdentityProof.java** | 115,123,126 | SELECT/format bom_type | DISPLAY | OK — logging/proof output |
| **BOMRuleAD.java** | 158,175 | SELECT bom_type from rules table | **BRANCH** | Rules table uses bom_type for tier matching |
| **PlacementCollectorVisitor.java** | 116 | Comment: "FLOOR-level BOMs (bom_type)" | DISPLAY | → update comment |
| **X_M_BOM.java** | 26,55,107,122 | Column constant + getter/setter | DISPLAY | OK — ORM maps the column |
| **StubDataSeeder.java** | 71,179-231 | DDL + INSERT with bom_type | **WRITE** | Column write — keep but note legacy |

## Pattern 1: bom_type — Java tests

| File | Line | Pattern | Classification | Suggested Fix |
|------|------|---------|---------------|---------------|
| IFCtoBOMGateTest.java | 103,104,119,223,226 | assertEquals bom_type | **TEST** | Must update with production code |
| SHPipelineTest.java | 87,93,105,146,148 | assertEquals bom_type | **TEST** | Must update with production code |
| DXPipelineTest.java | 92,94,104,106,160 | assertEquals bom_type | **TEST** | Must update with production code |
| PrimeRuleWitnessTest.java | 65,98,122,147,190,239 | WHERE bom_type='BUILDING' | **TEST** | Must update with production code |
| TopologyBatchProcessTest.java | 133,139 | SELECT bom_type | **TEST** | Must update with production code |
| ComposePrefabBomVerbTest.java | 66,71 | assertEquals "FLOOR" bom_type | **TEST** | Must update with production code |
| FillBuffersVerbTest.java | 42,128 | INSERT bom_type | **TEST** | Schema setup — keep |
| ClearVarianceVerbTest.java | 42 | INSERT bom_type | **TEST** | Schema setup — keep |
| BuildingInspectorTest.java | 65 | assertNotNull bom_type | **TEST** | Must update with production code |
| SelectionCascadeTest.java | 63,87,89,91,97 | DDL + bom_type queries | **TEST** | Must update with production code |
| OrderInheritanceTest.java | 208,304,311,318 | DDL + INSERT bom_type | **TEST** | Schema setup — keep |
| RemoveCompressTest.java | 326,422,430,438 | DDL + INSERT bom_type | **TEST** | Schema setup — keep |
| BomDropperOrderIdTest.java | 96,184 | DDL + INSERT bom_type | **TEST** | Schema setup — keep |
| Schedule5DCostTest.java | 37 | DDL bom_type | **TEST** | Schema setup — keep |
| ASIAuthoringTest.java | 345 | DDL bom_type | **TEST** | Schema setup — keep |
| DemoHouseTest.java | 429 | DDL bom_type | **TEST** | Schema setup — keep |
| OrderConfiguratorTest.java | 428 | DDL bom_type | **TEST** | Schema setup — keep |
| Tier1Test.java | 44 | DDL bom_type | **TEST** | Schema setup — keep |

## Pattern 2: getByType — Java code

| File | Line | Pattern | Classification | Suggested Fix |
|------|------|---------|---------------|---------------|
| MBOM.java | 42 | `getByType(conn, bomType)` definition | **BRANCH** | → deprecate, add getRoots() / getByCategory() |
| ListBomVerb.java | 44,48 | `MBOM.getByType(conn, "BUILDING")` | **BRANCH** | → MBOM.getRoots() |
| ReportBomCatalogVerb.java | 53 | `MBOM.getByType(conn, type)` | **BRANCH** | → getByCategory() |
| CompilationPipeline.java | 587 | `MBOM.getByType(bomConn, "FLOOR")` | **BRANCH** | → tree children query |
| PlacementLoader.java | 259 | `MBOM.getByType(conn, "BUILDING")` | **BRANCH** | → MBOM.getRoots() |
| M_AdOpeningFamily.java | 46 | `getByType(conn, openingType)` | LEGACY NOTE | OK — different entity, not BOM |
| MProduct.java | 31 | `getByType(conn, productType)` | LEGACY NOTE | OK — different entity |
| CatalogValidator.java | 244 | `M_AdOpeningFamily.getByType` | LEGACY NOTE | OK — opening families, not BOMs |

## Pattern 2: component_type MAKE — Docs

| File | Line | Pattern | Classification | Suggested Fix |
|------|------|---------|---------------|---------------|
| DISC_VALIDATION_DB_SRS.md | 595,607,608 | "parent MAKE line" | **FIXED** | → "parent m_bom_line" (this session) |
| DATA_MODEL.md | 100,213 | "parent MAKE line" | **FIXED** | → "parent m_bom_line" (this session) |
| TestArchitecture.md | 84 | "component_type ignored" | LEGACY NOTE | OK — documents the drift guard |
| ASSEMBLY_BUILDER_SRS.md | 62 | `component_type BUY/MAKE` | LEGACY NOTE | OK — iDempiere column reference |
| BIM_COBOL.md | 1316 | `component_type (BUY/MAKE/PHANTOM)` | LEGACY NOTE | OK — iDempiere mapping reference |
| BIM_Designer.md | 2848 | "Assembly MAKE" | STALE | Should say "sub-BOM" not "MAKE" |

## Pattern 3: building_id — Docs

| File | Line | Pattern | Classification | Suggested Fix |
|------|------|---------|---------------|---------------|
| MANIFESTO.md | 436 | `building_id` column | LEGACY NOTE | OK — bim_changelog column |
| CompilationAudit.txt | 20,59,86,421,491,497 | `building_id` in c_order | LEGACY NOTE | OK — c_order has this column |
| TIER1_SRS.md | 156,315,401,417 | `building_id` in changelog/queries | LEGACY NOTE | OK — bim_changelog column |
| STANDARDS_COMPLIANCE_SRS.md | 176 | `building_id` in compliance table | LEGACY NOTE | OK — table column |
| BIM_Designer.md | 1083,1521 | `building_id` Blender prop | LEGACY NOTE | OK — UI property |
| DocValidate.md | 439 | `building_id` in changelog | LEGACY NOTE | OK — bim_changelog column |
| G4_SRS.md | 69 | `building_id` = C_Order_ID | LEGACY NOTE | OK — order identifier |
| ID_NAME_VALUE_STUDY.md | 97,101,239,358,361 | `building_id` in registry tables | LEGACY NOTE | OK — ad_building tables |
| BIM_COBOL.md | 1039 | `REFERENCES c_order(building_id)` | LEGACY NOTE | OK — FK reference |
| Q&A1.txt | 171,172 | `building_id` discussion | LEGACY NOTE | OK — historical Q&A |
| BACK_OFFICE_SRS.md | 54 | `building_id` in print format | LEGACY NOTE | OK — table column |

## Pattern 3: building_id — Java code

No Java code uses `building_id` where it should use `bom_id` or `M_Product_ID`. All uses are legitimate c_order/changelog references. **No conflicts.**

## BOMTypeSystem.java — separate concern

| File | Line | Pattern | Classification | Suggested Fix |
|------|------|---------|---------------|---------------|
| BOMTypeSystem.java | 20-342 | `bom_types`, `bom_type_components` tables | LEGACY NOTE | OK — different tables (type templates), not m_bom.bom_type column. No conflict |
| BOMVariantSystem.java | 164,179 | reads from bom_types/bom_type_components | LEGACY NOTE | OK — same, variant system |

## Summary

| Category | Count | Action |
|----------|-------|--------|
| **BRANCH** (code branches on bom_type) | ~30 sites across 15 Java files | Prompt 71 — migrate to tree/category queries |
| **WRITE** (code writes bom_type value) | ~5 sites (builders + stub seeder) | Prompt 71 — keep column writes, remove branching |
| **TEST** (test asserts on bom_type) | ~20 sites across 12 test files | Update with production code in prompt 71 |
| **DISPLAY** (logging/comments only) | ~8 sites | Low priority, cosmetic |
| **CONFLICT** (doc contradicts §10.4.5) | 3 docs (VIEW_CONTRACTS, BIM_Designer, BIM_Designer_SRS) | Fix in prompt 71 or separate doc pass |
| **STALE** (outdated but not contradictory) | ~8 doc sites | Fix opportunistically |
| **LEGACY NOTE** (correctly marked) | ~40 sites | OK — no action needed |
| **FIXED** (this session) | 5 sites (DISC_VALIDATION_DB_SRS + DATA_MODEL) | Done |

## Anti-pattern: shouldSkip() — flagged for removal

**CompilationPipeline.CompileStage.shouldSkip()** is a structural cheat.
Whether it checks `"CO".equals(category)` or `hasGenerativeVerbs()`, the
result is the same: an empty BuildingSpec and a fall-through to a separate
emit path (`emitGlobalPlacementElements`). This is two paths, not one.

**Correct:** ONE walker, verb-dispatched. The walker always walks the BOM.
PLACE verb → emit at tack offset. ROUTE/FRAME/TILE/WIRE → generate from
rules. No skip, no empty spec, no separate path.

See DISC_VALIDATION_DB_SRS.md §10.4.1 anti-pattern note.

## Key refactoring targets for prompt 71

1. **MBOM.java** — add `getRoots(conn)`, `getByCategory(conn, categoryId)`, deprecate `getByType()`
2. **BomValidator.java** — 9 bom_type branches, heaviest user
3. **DesignerDAO.java** — 10 bom_type queries, all BRANCH
4. **CompilationPipeline.java** — `getByType("FLOOR")` → tree children. **Remove shouldSkip() entirely** — one walker, verb-dispatched
5. **VIEW_CONTRACTS.md** — v_qualified_bom spec needs bom_type→category migration plan
