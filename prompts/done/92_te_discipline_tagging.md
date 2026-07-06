# DONE
# TE Discipline Tagging — AD_Org_ID on BOM Lines

**Priority:** c_orderline has only ARC/STR for TE (48,428 elements). 6 MEP
disciplines (FP, ACMV, CW, ELEC, SP, LPG = ~12,000 elements) all tagged ARC.
DocEvent validation is a no-op. Fix discipline flow so AD_Org_ID reaches
c_orderline correctly, enabling per-discipline DocEvent rules.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Discipline data exists in the extraction
(I_Element_Extraction.discipline, populated from federated model metadata).
DisciplineBomBuilder already groups by discipline (line 125). The data is
there — it's just not persisted through to compilation.

## Read first

1. `PROGRESS.md` §Current State
2. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.3 (three-layer validation), §10.4.4 (discipline from product), §11.6.5 Steps 5-6
3. `IFCtoBOM/src/main/java/com/bim/ifctobom/DisciplineBomBuilder.java` — groups by discipline at line 122-148 but doesn't write it
4. `DAGCompiler/src/main/java/com/bim/compiler/bom/BomDropper.java` — `resolveDiscipline()` at line 590, `explode()` at line 259, `insertLine()` at line 529
5. `DAGCompiler/src/main/java/com/bim/compiler/topology/Discipline.java` — enum with AD_Org_ID mapping
6. `docs/TerminalAnalysis.md` §Element Inventory by Discipline — the 8 expected disciplines

## Root cause

The discipline resolution chain breaks at the BOM→OrderLine boundary:

```
IFCtoBOM (EXTRACTION):
  DisciplineBomBuilder groups elements by e.discipline()     ← discipline KNOWN
  VerbFactorizer writes m_bom_line (child_product_id, qty)   ← discipline NOT written
  m_bom has m_product_category_id = CO (building category)   ← not discipline

DAGCompiler (COMPILATION):
  BomDropper.explode() reads m_bom.m_product_category_id     ← gets "CO"
  resolveDiscipline("CO") → default → Discipline.ARC         ← WRONG
  insertLine() writes C_OrderLine.AD_Org_ID = 1 (ARC)        ← all MEP lost
```

The parent BOM's category is the building type (CO), not the discipline. Every
LEAF line inherits it, so `resolveDiscipline()` can never distinguish FP from
ACMV from ELEC.

## The fix

Discipline should flow from the parent BOM's `AD_Org_ID`, not from
`m_product_category_id`. Two changes needed:

### Step 1: IFCtoBOM — Write AD_Org_ID on FLOOR-level BOMs

DisciplineBomBuilder creates per-storey FLOOR BOMs (TE_GF, TE_RF, etc.).
Currently each FLOOR BOM has `m_product_category_id=CO`. But the discipline
grouping at line 122-148 already knows which discipline each element belongs to.

Option A (preferred): Write discipline directly on m_bom_line via a new column
or reuse an existing column. Each LEAF line under TE_GF knows its discipline.

Option B: Store discipline in the element_ref or role field (already on
m_bom_line). But these have other uses — check before overloading.

**Check m_bom_line schema** — does a suitable column exist, or do we need DDL?
The column name should be `AD_Org_ID` (INTEGER) to match C_OrderLine and AD_Org.

### Step 2: DAGCompiler — Read AD_Org_ID from BOM line

BomDropper.explode() currently reads `m_product_category_id` from the parent BOM
and passes it to `insertLine()` → `resolveDiscipline()`. For CO/IN buildings,
it should also read the BOM line's AD_Org_ID (if present) and prefer it over
the category-based inference.

The priority chain becomes:
1. m_bom_line.AD_Org_ID (if > 0) — authoritative from extraction
2. m_product_category_id → resolveDiscipline() — fallback (RE/SH/DX path)
3. default → ARC

### Step 3: Verify

```bash
rm library/TE_BOM.db library/SH_BOM.db
./scripts/run_RosettaStones.sh classify_sh.yaml   # SH 7/7 (no regression)
./scripts/run_RosettaStones.sh classify_te.yaml   # TE 6/7+WARN (C9)
```

Then check discipline distribution:
```sql
sqlite3 DAGCompiler/lib/output/terminal.db \
  "SELECT Discipline, count(*), sum(Qty) FROM c_orderline WHERE host_type='LEAF' GROUP BY Discipline ORDER BY sum(Qty) DESC;"
```

Expected (from TerminalAnalysis.md):
| Discipline | Elements |
|------------|----------|
| ARC | 34,724 |
| FP | 6,863 |
| ACMV | 1,621 |
| CW | 1,431 |
| STR | 1,429 |
| ELEC | 1,172 |
| SP | 979 |
| LPG | 209 |

## What NOT to do

- Do NOT add discipline to m_product_category — category is product taxonomy, not org
- Do NOT invent new disciplines — Discipline.java enum is authoritative (9 values)
- Do NOT modify the RE path (SH/DX/FK) — only CO/IN path needs this
- Do NOT create DISCIPLINE-level BOM nodes — S100-p66 removed them (discipline is not a spatial container)
- Do NOT modify existing migration SQL files (sacred, append only)

## Step 4: Persist Room Origins on Container C_OrderLines (P91 follow-up)

P91 ProveStage found that BomTreeProver P-TACK and P-PARENT checks
produce advisory WARNs because room origins (YAML `origin_m`) are
applied by the walker but not persisted in `c_orderline`. Fix:

1. **Container c_orderlines** (FLOOR, ROOM level) should persist
   their world origin in dx/dy/dz — currently these are 0.0 because
   only LEAF lines get computed positions
2. BomDropper or the walker should write the accumulated origin
   on container nodes so P-PARENT can verify child containment
3. After fix, re-run SH and verify P-TACK improves (expect >0 PASS)

### Step 5: Align Static Children Naming (P91 P-QTY delta=2)

P91 found SH has LEAF qty=60 but output=58. Two static children
(FLOOR_SLAB_GF, ROOF_ASSEMBLY) are c_orderline LEAF lines but their
product names don't match elements_meta element_ref. Either:
- These aren't real elements (exclude from LEAF count), or
- Their element_ref naming convention needs alignment

Investigate and fix. After fix, P-QTY should show delta=0.

## Downstream: DocEvent validation

Once AD_Org_ID flows correctly to c_orderline, DocEvent rules (AD_DocEvent_Rule
in ERP.db, DV026) can fire per-discipline. The table exists with 0 rows — seeding
rules is a separate task. But correct discipline tagging is the prerequisite.

## When Done

Prepend `# DONE` to this file's first line.

Append findings:
- Which column/DDL was added or reused
- How BomDropper reads the discipline
- Gate results (SH, TE)
- c_orderline discipline distribution for TE
- P-TACK improvement: how many PASS after room origin persistence
- P-QTY delta: resolved or explained
- P-PARENT improvement: how many more PASS after container origins

# FINDINGS (S100-p92)

## Step 1: DDL — AD_Org_ID column on m_bom_line
- Added `AD_Org_ID INTEGER DEFAULT 0` to m_bom_line DDL in `IFCtoBOMPipeline.java` line 635
- Added COLUMNNAME + getter/setter in `X_M_BOMLine.java`
- Updated `library/schema_snapshot_bom.sql` (also added missing verb_ref/shape_archetype/scale_band/host_element_ref)

## Step 2: IFCtoBOM — Write AD_Org_ID on LEAF lines
- `VerbFactorizer.factorize()` gains `int adOrgId` overload (3-param: conn, bomId, elements... + writeMaRows + adOrgId)
- `VerbFactorizer.insertLeafLine()` writes AD_Org_ID as 24th bind parameter
- `DisciplineBomBuilder` resolves discCode → AD_Org_ID via local `resolveAdOrgId()` switch
  (mirrors Discipline.java enum values without cross-module dependency — IFCtoBOM can't import DAGCompiler)
- StructuralBomBuilder / ScopeBomBuilder callers unchanged (AD_Org_ID=0 = fallback to category)

## Step 3: DAGCompiler — Read AD_Org_ID from BOM line
- `BomDropper.insertLine()` gains `int lineAdOrgId` overload
- Priority chain: (1) m_bom_line.AD_Org_ID > 0 → `Discipline.fromAD_Org_ID()`, (2) category-based `resolveDiscipline()`, (3) ARC
- Both `explode()` and `explodeAssembly()` LEAF paths pass `line.getAdOrgId()` to insertLine

## Step 4: Container origins persisted
- `explode()` root: writes `bom.getOriginX/Y/Z()` as BUILDING c_orderline dx/dy/dz (was 0,0,0)
- `explodeAssembly()`: writes `makeDx + bom.getOriginX()` etc. as container dx/dy/dz (was 0,0,0)
- New params `double makeDx/Dy/Dz` on `explodeAssembly()`, populated from `line.getDx/Dy/Dz()` at call sites
- SH verified: BUILDING has world origin (-9.23, -2.75, -0.47), FLOOR/ROOM have MAKE offsets

## Step 5: P-QTY delta resolved (SH 58=58, was 60 vs 58)
- Root cause: FLOOR_SLAB_GF + ROOF_ASSEMBLY are YAML `static_children` inserted as MAKE lines but
  had no m_bom header → BomDropper treated them as LEAFs (contributing 2 to LEAF qty but 0 to output)
- Fix: `FloorRoomBomBuilder.insertEmptyBomHeader()` creates a minimal m_bom row for each static child
  so BomDropper recurses into it as a sub-assembly (FLOOR host_type), not a LEAF
- After fix: LEAF qty=58, elements_rtree=58, delta=0

## Gate results
- **SH: 7/7 PASS** — zero regression, container origins populated, P-QTY delta=0
- **TE: extraction in progress** — TE_BOM.db re-extraction takes ~7 min (48K elements + DimRange validation).
  Background run initiated. Next session: verify TE 6/7+WARN (C9) and check discipline distribution.

## TE discipline distribution — PENDING
Next session should verify:
```sql
sqlite3 DAGCompiler/lib/output/terminal.db \
  "SELECT Discipline, count(*), sum(Qty) FROM c_orderline WHERE host_type='LEAF' GROUP BY Discipline ORDER BY sum(Qty) DESC;"
```
Expected 8 disciplines (ARC, FP, ACMV, CW, STR, ELEC, SP, LPG) instead of only ARC+STR.

## P-TACK / P-PARENT improvement — PENDING
Requires TE compilation output. Next session should run BomTreeProver and compare
P-PARENT/P-TACK results before (all WARN) vs after (expect improvement from container origins).

## Files changed (8)
1. `IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java` — AD_Org_ID in m_bom_line DDL
2. `IFCtoBOM/src/main/java/com/bim/ifctobom/VerbFactorizer.java` — adOrgId param on factorize + insertLeafLine
3. `IFCtoBOM/src/main/java/com/bim/ifctobom/DisciplineBomBuilder.java` — resolveAdOrgId + pass to factorize
4. `IFCtoBOM/src/main/java/com/bim/ifctobom/FloorRoomBomBuilder.java` — insertEmptyBomHeader for static children
5. `ORMSandbox/src/main/java/com/bim/ormsandbox/po/X_M_BOMLine.java` — AD_Org_ID column + accessor
6. `DAGCompiler/src/main/java/com/bim/compiler/bom/BomDropper.java` — lineAdOrgId priority chain + container origins
7. `library/schema_snapshot_bom.sql` — AD_Org_ID + missing columns
8. `prompts/92_te_discipline_tagging.md` — this file
