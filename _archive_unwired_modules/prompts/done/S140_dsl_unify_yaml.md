# S140 — DSL/YAML Unification + component_type Spec + IFC Aggregate Verb Gap

**Priority: ARCHITECTURE — no hidden intent sources**
**Spec foundation:** `docs/BOMBasedCompilation.md` (BBC.md) §2.1.7, §2.2.1, §2.2.2, §3.5
**Companion:** `docs/BIM_COBOL.md` §Verb Taxonomy, `docs/WorkOrderGuide.md` §YAML field dictionary

## PRIME RULE

Read before writing. Every change requires a pre-flight citation:
`// Implementing BBC.md §X.Y — Witness: W-NAME`

---

## Context (from S139)

Two issues surfaced:

1. **`.bim` DSL files are vestigial in the extracted pipeline.** `IFCtoBOM/src/main/resources/dsl_*.bim` are loaded, parsed by `ParseStage`, but `ctx.definition()` is never consumed by any subsequent stage. YAML already carries all operational parameters. The S138/S139 note claiming "DSL drives compilation orchestration" was **incorrect** — the BOM walk path ignores the parsed `BuildingDefinition` entirely.

2. **BBC §2.2.1 / CheckBomVerb tension.** BBC §2.2.1 states: *"code must never branch on `component_type`."* `CheckBomVerb` (VerbStage default recipe) explicitly branches on it (BUY/MAKE/PHANTOM). The LEAF→MAKE fix at `BomHierarchyBuilder.java:100` is correct data, but the spec needs a clarifying sentence before the fix lands.

---

## Task 0 — Investigate: IFC Aggregate Verb Gap (read-only, findings to BBC.md)

### Background

`VerbDetector` is geometry-only. It receives a flat `List<ExtractionElement>` with AABB
coordinates and tries TILE → ROUTE → FRAME → CLUSTER. It is blind to IFC's own grouping
intent already captured in the extraction DB.

`extractIFCtoDB.py` already reads and persists:
- `IfcRelAggregates` (parent→child decomposition: IfcCurtainWall → IfcMember[])
- `IfcMappedItem` (explicit instanced copies — IFC's native array mechanism)
- `object_type` TEXT column in elements table

`ExtractionReader.ExtractionElement` (the Java record) carries: AABB, `ifcClass`, `guid`,
`hostElementRef` — **no aggregate parent, no mapped-item flag.**

### What to investigate

**0a. Confirm aggregate table exists in extracted DBs:**
```sql
-- In IFCtoBOM/src/main/resources/SH_extracted.db or DX_extracted.db
SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name LIKE '%agg%';
-- OR
SELECT name FROM sqlite_master WHERE type='table';
```

**0b. Curtain wall case: does the SH extracted DB have IfcCurtainWall→IfcMember aggregate rows?**
```sql
SELECT parent_type, child_type, COUNT(*) FROM element_aggregates
GROUP BY parent_type, child_type ORDER BY COUNT(*) DESC;
```
(Table name may differ — check `sqlite_master`.)

**0c. Trace: how many SH CLUSTER lines would collapse to IFC-declared groups?**
For each CLUSTER group in SH_BOM.db, check if all members share an `IfcRelAggregates`
parent in the extraction DB. Report: N groups that ARE IFC-declared vs M groups that are
purely geometric.

**0d. IfcMappedItem: does SH/DX have mapped items?**
```bash
grep -n "IfcMappedItem\|mapped_item\|is_mapped" DAGCompiler/python/extractIFCtoDB.py
```
If yes, what product types use them? Are any currently falling to CLUSTER?

### What to write (BBC.md appendix only — no code)

Append to `docs/BOMBasedCompilation.md` under a new `## Verb Gap — IFC Aggregate Path`:

1. How many CLUSTER groups in SH/DX are IFC-declared aggregates (could be pre-grouped as sub-BOM)
2. What new detection Phase 0 (IFC_AGGREGATE) would look like conceptually
3. Whether `ExtractionElement` needs an `aggregateParentRef` field
4. Spec decision: should IfcCurtainWall become its own BOM level (parent BOM → member children)?
   Cite BBC §2.2.2 BOM-to-BOM recursion as the enabling mechanism.

**Do NOT code. Findings only. S141 decides implementation path.**

---

## Task 1 — Spec Clarification: component_type Branch Rule

**Prerequisite for the LEAF→MAKE fix. Do this first.**

BBC §2.2.1 currently says:
> "`component_type` does not exist in compilation. …Code must never branch on it."

This is correct for the **walker** (placement engine). But `CheckBomVerb` is a *validation verb*, not the placement engine. The rule needs one sentence of scope:

**In `docs/BOMBasedCompilation.md` §2.2.1, append (do not modify existing text):**

> *Exception — validation verbs:* `CHECK BOM` may inspect `component_type` as an iDempiere
> well-formedness signal (valid values: BUY / MAKE / PHANTOM). The placement walker still
> never branches on it. Any value outside {BUY, MAKE, PHANTOM} is a BOM authoring error,
> not a compilation input.

**Commit:**
```bash
git add docs/BOMBasedCompilation.md
git commit -m "[S140] BBC §2.2.1: clarify component_type branch rule scope (walker vs validation)"
```

---

## Task 2 — Fix BomHierarchyBuilder LEAF → MAKE

After Task 1 is committed:

In `IFCtoBOM/src/main/java/com/bim/ifctobom/BomHierarchyBuilder.java:100`:

```java
// BEFORE (bug — LEAF is not a valid iDempiere BOM component_type)
.componentType("LEAF")

// AFTER (correct — DX_ROOM_L1/L2 are sub-BOMs with children)
// Implementing BBC.md §2.2.1 — Witness: W-DX-LEAF-FIX
.componentType("MAKE")
```

**Verify:** Re-run DX VerbStage. CHECK BOM must show 0 errors. CHECK PLACEMENT may still
show violations (separate issue, see Task 4).

```bash
./scripts/run_RosettaStones.sh classify_dx.yaml
```

Expected: G1-G6 PASS (no regression), VerbStage CHECK BOM = 0 errors.

**Commit:**
```bash
git add IFCtoBOM/src/main/java/com/bim/ifctobom/BomHierarchyBuilder.java
git commit -m "[S140] BomHierarchyBuilder: LEAF→MAKE for DX floor-scope room containers"
```

---

## Task 3 — DSL → YAML Unification (extracted buildings only)

### Strategy: delete first, let breakage speak

Do NOT grep for consumers first. Remove the code outright, then run `mvn compile` and
`./scripts/run_RosettaStones.sh` — any hidden consumer surfaces as a compile error or
test failure immediately. Grepping is slower and risks false confidence from missed
patterns. Staleness is the enemy; breakage is the oracle.

### 3a. Delete the extracted-building DSL files

```bash
rm IFCtoBOM/src/main/resources/dsl_*.bim
```

Do this first. If any Java code tries to load a file by path and fails silently (no
exception), the test fleet will catch it via element count regression (G1 COUNT).

### 3b. Remove DSL loading from IFCtoBOMPipeline

In `IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java`:
- Remove lines 376–382 (DSL file read, `dslContent` local var)
- In the PreparedStatement at line 384+: set `dsl_content = NULL` (or remove column from UPDATE if schema allows)
- Remove `dsl_file` field parsing from `ClassificationYaml.java:325` and `BuildingConfig` record

**Pre-flight:** `grep -n "dslFile\|dsl_file\|dslContent\|dsl_content" IFCtoBOM/src/main/java/` — find all references, remove all.

### 3c. Remove ParseStage from CompilationPipeline

In `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java`:
- Remove `new ParseStage()` from the stage list (line 71)
- Delete the `ParseStage` inner class (lines 337–352)
- `BuildingParser` may still be used by the generative path (BIMDesigner) — do NOT delete it, only remove its call from CompilationPipeline

### 3d. Remove DSL seeding from run_RosettaStones.sh

In `scripts/run_RosettaStones.sh:181–186`: remove the DSL seeding block:
```bash
# DELETE this block:
DSL_FILE="IFCtoBOM/src/main/resources/dsl_${PREFIX,,}.bim"
if [ -f "$DSL_FILE" ] && [ -f "$BOM_DB" ]; then
    DSL_CONTENT=$(cat "$DSL_FILE")
    sqlite3 "$BOM_DB" "UPDATE C_DocType SET DSLContent = ...
fi
```

### 3e. Remove dead DSL load from run.sh

In `scripts/run.sh:92–94`: remove the dead load:
```bash
# DELETE these lines:
DSL_FILE=$(mktemp)
sqlite3 "$BOM_DB" "SELECT DSLContent FROM C_DocType WHERE C_DocType_ID='${DOC_TYPE}'" > "$DSL_FILE" 2>/dev/null
# and line 141: rm -f "$DSL_FILE"
```

### 3f. Remove dsl_file from all classify_*.yaml

For each active `IFCtoBOM/src/main/resources/classify_*.yaml`:
- Remove the `dsl_file: dsl_XX.bim` line
- Update the YAML header comment if it references DSL

### 3g. Delete the extracted-building DSL files

```bash
rm IFCtoBOM/src/main/resources/dsl_*.bim
```

### 3h. Drop DSLContent columns via migration

Schema columns that only held dead data should be removed too. Write a new migration
`migration/W0XX_drop_dsl_content.sql` (append-only, next W-number):

```sql
-- W0XX: Remove DSL content columns — dsl_*.bim files deleted; YAML is sole intent source
-- SQLite does not support DROP COLUMN before 3.35. Use recreate pattern if needed.
-- For ERP.db targets: ALTER TABLE m_bom DROP COLUMN dsl_content;
-- For C_DocType: ALTER TABLE C_DocType DROP COLUMN DSLContent;
-- For c_order: ALTER TABLE c_order DROP COLUMN DSLContent;
```

Run `mvn compile` after — any compile error from a removed column reference is a hidden
consumer surfaced. Fix the reference before committing the migration.

**If SQLite version < 3.35:** do NOT use the recreate pattern. Leave column, mark it
deprecated in the migration comment, and add a TODO for when SQLite upgrades.
Check: `sqlite3 --version`

### 3i. Verify fleet

```bash
./scripts/run_RosettaStones.sh classify_sh.yaml
./scripts/run_RosettaStones.sh classify_dx.yaml
./scripts/run_RosettaStones.sh classify_te.yaml
```

G1-G6 must all PASS (no regression from DSL removal).

**Commit after full fleet green:**
```bash
git add -u
git commit -m "[S140] Remove extracted-building DSL files; YAML is sole intent source"
```

---

## Task 4 — Audit Generative-Path .bim Files (investigate only, no deletion)

The following `.bim` files serve the **generative** compilation path (BIMDesigner, tests)
and are NOT the same as the extracted-building DSLs:

- `DAGCompiler/lib/input/dsl/SampleHouse.bim`
- `DAGCompiler/lib/input/dsl/Duplex.bim`
- `DAGCompiler/lib/input/dsl/Terminal.bim`
- `examples/*.bim` (test cases for BIMDesigner)

**What to investigate:**
1. Which Maven tests load these files directly? Run:
   ```bash
   grep -rn "SampleHouse\.bim\|Duplex\.bim\|lib/input/dsl\|examples/" \
     DAGCompiler/src/test/ IFCtoBOM/src/test/
   ```
2. Does `BuildingParser.parse()` get called in any test using these files? Cite class + line.
3. Is there a YAML equivalent for each? (i.e. `classify_sh.yaml` exists alongside `SampleHouse.bim`)
4. Are these files used by the BIMDesigner flow, or only referenced as dead fixtures?

**Report only. Do not delete. S141 decides based on findings.**

---

## Task 5 — Fleet Gate Table: Add VerbStage Column

Per S139 Finding C: a building can show G1-G6 PASS while VerbStage silently fails (DocStatus=VO).

In `PROGRESS.md` gate table, add a `VerbStage` column for DX, SH, TE:

```
| VerbStage | ? | ? | ? | ? | ? | — |
```

Run DX and SH and fill in actual DocStatus from VerbStage output:
```bash
./scripts/run_RosettaStones.sh classify_dx.yaml 2>&1 | grep -E "VERB STAGE|PASS|FAIL|DocStatus"
./scripts/run_RosettaStones.sh classify_sh.yaml 2>&1 | grep -E "VERB STAGE|PASS|FAIL|DocStatus"
```

---

## Session Gate

This session ends when you can answer:

1. **How many SH/DX CLUSTER groups are IFC-declared aggregates?** (Task 0c result)
2. **Is `c_order.DSLContent` read by any downstream consumer?** (Task 3a grep result)
3. **Does `CheckBomVerb` spec conflict with BBC §2.2.1 after Task 1 clarification?** (cite line)
4. **DX VerbStage after LEAF→MAKE: CHECK BOM = 0 errors?** (Task 2 run output)
5. **How many generative-path tests load `.bim` files directly?** (Task 4 count)
6. **Fleet G1-G6 after DSL removal: all still green?** (Task 3i run output)

---

## Commit sequence

```
T1: [S140] BBC §2.2.1 clarification → docs/BOMBasedCompilation.md
T2: [S140] BomHierarchyBuilder LEAF→MAKE → IFCtoBOM
T3: [S140] Remove extracted-building DSL: dsl_*.bim + pipeline code + scripts
T4: [S140] PROGRESS.md VerbStage column + fleet results
```

## Sequence

```
S140 (this)  — IFC aggregate findings + spec fix + LEAF→MAKE + DSL removal + generative audit
S141         — Decision: IFC_AGGREGATE Phase 0 impl + generative .bim → YAML migration
S142         — CHECK PLACEMENT 148 violations: FINE logging + root cause
```
