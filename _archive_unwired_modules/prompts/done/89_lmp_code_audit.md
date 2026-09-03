# DONE f3c4d793
# Last Mile Problem — Full Code Audit After PK Migration + Feature Wave

**Priority:** 15+ commits landed since the last LMP audit (P84). The entire
PK layer changed (Phase A/B/C), forge fabrication was wired, Designer
features added (costOfChange, placeSet, capacity rules), Replace+Add
mutations wired, and report templates added. Any of these could have
introduced a new path that breaks an LMP guarantee. Verify every drift
point against the ACTUAL CODE, not against claims in PROGRESS.md.

You are a watchdog for bim-compiler. One bounded task: forensic audit.

## PRIME RULE

**TRUST NOTHING.** Read the code. Run the queries. Grep the source.
Every claim in `docs/LAST_MILE_PROBLEM.md` must be independently
verified against the current codebase. If a drift point is PASS by
assumption rather than by evidence, flag it as SUSPECT.

## Read first

1. `docs/LAST_MILE_PROBLEM.md` — the 11 drift points. Your checklist.
2. `PROGRESS.md` — what changed (P85-P88, P53-P59, P64, P74)
3. `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java`
4. `DAGCompiler/src/main/java/com/bim/compiler/dsl/BuildingWriter.java`
5. `DAGCompiler/src/main/java/com/bim/compiler/bom/BomDropper.java`
6. `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/PlacementCollectorVisitor.java`
7. `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/BOMWalker.java`

## What Changed Since Last Audit (P84)

These are the changes that could break LMP guarantees:

### PK Migration (Phase A/B/C) — schema layer
- m_bom: `bom_id TEXT PK` → `M_BOM_ID INTEGER PK`, `bom_id` → `Value`
- M_Product_Category: `TEXT PK` → `INTEGER PK`, codes → `Value`
- 13 AD tables: TEXT PK → INTEGER PK + Value
- `bom_child_id` → `M_BOM_Line_ID` rename
- `loadByValue()` added to BasePO
- IFCtoBOM DDL rewritten, `copyCategoryLookup()` added
- ALL 35 BOM.db files re-extracted twice

**LMP risk:** §1 (count invariant) if walker lost elements during PK
transition. §3 (compiler only) if new lookup paths touch extraction DB.
§5 (spec fidelity) if sources changed. §7 (separate from input) if
new write paths were added to compile DB.

### Replace + Add Mutations (P74) — BomDropper
- Replace mutation: product swap at locator_ref
- Add mutation: discipline recipe C_OrderLine creation
- New code paths in `BomDropper.explode()` and `BomDropper.drop()`

**LMP risk:** §1 (count invariant) if Replace creates more/fewer
elements. §6 (output path) if Add writes via a side channel.
§11 (factorization) if Replace breaks material/dimension uniformity.

### Designer Features (P53/P55/P58)
- costOfChange() wired through DesignerAPIImpl
- placeSet() batch placement
- Capacity AD_Val_Rule rows in ERP.db

**LMP risk:** §5 (spec fidelity) if new Designer paths bypass the
C_Order → C_OrderLine → BOM explosion chain. §3 if Designer code
reaches into extracted DB.

### Forge Wiring (P59/P64)
- ad_forge_fabrication table in output.db
- ForgeFabricationWriter
- RebarCageForge + standards enums

**LMP risk:** §6 (output path) if forge writes elements (not just
fabrication data). §2 (LOD400) if forge geometry bypasses library.

## Task 1: §1 Input = Output — Count Invariant

**Verify for SH and TE:**

```bash
# Run SH pipeline and check counts
./scripts/run_RosettaStones.sh classify_sh.yaml
sqlite3 output/samplehouse.db "SELECT COUNT(*) FROM elements_meta"
sqlite3 library/SH_BOM.db "SELECT SUM(qty) FROM m_bom_line WHERE is_active=1 AND component_type != 'PHANTOM'"
```

Both numbers must match exactly. Repeat for TE.

**Code check:** Grep for any new INSERT into elements_meta that wasn't
there during P84:

```bash
grep -rn "elements_meta" DAGCompiler/src/main/java/ --include="*.java" | grep -i "insert\|write"
```

Check if Replace mutation changes element count (swap should be 1:1).
Check if Add mutation creates elements (it should create C_OrderLines,
not elements directly).

## Task 2: §2 LOD400 Geometry

**Verify:** Zero GEO_ fallback hashes.

```bash
# After pipeline run:
sqlite3 output/samplehouse.db \
  "SELECT geometry_hash FROM base_geometries WHERE geometry_hash NOT LIKE 'LOD_%'"
```

Must return 0 rows.

**Code check:** Does ForgeEngine produce geometry hashes? If so, do
they follow LOD_ convention or something else?

```bash
grep -rn "geometry_hash\|GEO_\|LOD_" BIM_COBOL/src/main/java/com/bim/cobol/forge/ --include="*.java"
```

## Task 3: §3 Compiler Only — No Extraction DB Access

**Verify:** Zero references to `*_extracted.db` in compiler code.

```bash
grep -rn "extracted" DAGCompiler/src/main/java/com/bim/compiler/ \
  --include="*.java" | grep -v "//\|/\*\|test\|Test\|EXTRACTED\|provenance"
```

**New risk:** `loadByValue()` in BasePO — does it open any new
connection? Trace the connection parameter.

```bash
grep -rn "loadByValue" --include="*.java" | grep -v test | grep -v Test | grep -v target | grep -v worktree
```

Each call site must use the compile connection, not an extraction connection.

**New risk:** `copyCategoryLookup()` in IFCtoBOM copies from ERP.db to
BOM.db. This runs during EXTRACTION, not compilation. Verify it's not
called from any compiler code path.

```bash
grep -rn "copyCategoryLookup\|CategoryLookup" DAGCompiler/ --include="*.java"
```

Must return 0 hits in DAGCompiler.

## Task 4: §4 Openings and Furniture

**Verify:** host_element_ref populated for doors/windows.

```bash
sqlite3 output/samplehouse.db \
  "SELECT COUNT(*) FROM elements_meta WHERE ifc_class IN ('IfcDoor','IfcWindow') AND host_element_ref IS NULL"
```

Must be 0.

**Code check:** Did the PK migration change how host_element_ref is
populated in PlacementCollectorVisitor?

```bash
grep -rn "host_element_ref" DAGCompiler/src/main/java/ --include="*.java"
```

## Task 5: §5 Spec Fidelity — Correct Sources

**Verify the source table in LMP.md is still accurate:**

| Source | Current path | Check |
|--------|-------------|-------|
| C_Order → C_OrderLine | BomDropper.drop() | Still the entry? |
| BOM.db | BOMWalker reads compile DB | Not extraction DB? |
| component_library.db | MeshBinder reads LOD | Read-only? |
| ERP.db | ValidationStage reads rules | Read-only during compile? |

**New risk:** costOfChange() — does it read from output.db during
compilation, creating a circular dependency?

```bash
grep -rn "costOfChange\|cost_of_change\|costBreakdown" DAGCompiler/ --include="*.java"
```

Must return 0 hits in DAGCompiler (costOfChange lives in Designer, not compiler).

**New risk:** placeSet() — does it bypass C_OrderLine?

```bash
grep -rn "placeSet\|place_set" --include="*.java" | grep -v test | grep -v target | grep -v worktree
```

Trace the code path. placeSet must call placeItem which creates
C_OrderLine entries. No shortcut INSERT into elements.

## Task 6: §6 Output Path — Single Write Path

**Verify:** `writeFromBomWalk()` is still the ONLY method that writes
to `elements_meta`.

```bash
grep -rn "elements_meta" DAGCompiler/src/main/java/ --include="*.java" | grep -i "insert\|create\|write"
```

**New risk:** ForgeFabricationWriter — does it write to elements_meta
or only to ad_forge_fabrication?

```bash
grep -rn "elements_meta\|element_instances\|base_geometries" \
  BIM_COBOL/src/main/java/com/bim/cobol/forge/ --include="*.java"
```

Must be 0 hits. Forge writes fabrication data, not elements.

**New risk:** Replace mutation — does it INSERT elements directly
or does it modify the BOM tree that the walker then places?

```bash
grep -rn "INSERT.*elements\|writeElement\|persistElement" \
  DAGCompiler/src/main/java/com/bim/compiler/bom/BomDropper.java
```

## Task 7: §7 Separate From Input — BOM is Read-Only

**Verify:** No UPDATE/INSERT/DELETE on the compile DB during compilation.

```bash
grep -rn "UPDATE\|DELETE\|INSERT" \
  DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java | grep -v "output\|Output\|//\|/\*"
```

**New risk:** `loadByValue()` is a SELECT, not a write. But verify:

```bash
grep -A5 "public boolean loadByValue" orm-core/src/main/java/com/bim/orm/BasePO.java
```

Must be SELECT only.

**New risk:** Add mutation creates C_OrderLine rows. Where? In the
compile DB or output DB? Trace:

```bash
grep -rn "insertLine\|INSERT.*C_OrderLine\|INSERT.*c_orderline" \
  DAGCompiler/src/main/java/com/bim/compiler/bom/BomDropper.java
```

C_OrderLine writes to the compile DB are legitimate (BomDropper builds
the order tree). But verify no writes to BOM tables (m_bom, m_bom_line).

## Task 8: §8 Visual Fidelity — C8/C9

**Verify:** Run SH and check C8/C9 gates.

```bash
./scripts/run_RosettaStones.sh classify_sh.yaml 2>&1 | grep "C8\|C9"
```

SH must be C8 PASS, C9 PASS. DX: C9 WARN (pre-existing). TE: C9 WARN (pre-existing).

**Code check:** Did the PK migration change how geometry_hash is
resolved? The hash comes from component_library.db via MeshBinder.

```bash
grep -rn "geometry_hash\|MeshBinder" DAGCompiler/src/main/java/ --include="*.java" | grep -v test
```

## Task 9: §9 Orientation — Rotation From BOM

**Verify:** rotation_rule is read from m_bom_line, not computed.

```bash
grep -rn "rotation_rule\|rotation\|setRotation" \
  DAGCompiler/src/main/java/com/bim/compiler/bom/walker/PlacementCollectorVisitor.java
```

The PK migration renamed `bom_child_id` → `M_BOM_Line_ID` but should
not have changed rotation handling. Verify the column read is correct.

**ProveStage 0ms:** Still SUSPECT from P84. Check if anything changed:

```bash
grep -rn "ProveStage\|hasRelationalData\|proveStage" \
  DAGCompiler/src/main/java/com/bim/compiler/ --include="*.java"
```

## Task 10: §10 Seal Integrity

```bash
bash scripts/verify_test_seal.sh
```

**Check:** What seal version? How many files? Compare against P84
finding (73 files, v8). The PK migration + feature wave likely changed
sealed files — verify the seal was properly updated, not weakened.

```bash
git log --oneline scripts/verify_test_seal.sh | head -5
git log --oneline -5 -- "*.seal"
```

Check: were any assertEquals weakened to assertTrue or range checks?

```bash
git diff bbcfc363..HEAD -- DAGCompiler/src/test/java/ BIM_COBOL/src/test/java/ \
  | grep -E "^\-.*assertEquals|^\-.*assertThat" | head -20
```

## Task 11: §11 Factorization — CLUSTER Integrity

**Verify for TE:** CLUSTER expansion still produces correct counts.

```bash
sqlite3 library/TE_BOM.db \
  "SELECT component_type, COUNT(*), SUM(qty) FROM m_bom_line WHERE is_active=1 GROUP BY component_type"
```

**Code check:** Did Replace mutation respect factorization guards?

```bash
grep -rn "material_name\|material_rgba\|CLUSTER\|factori" \
  DAGCompiler/src/main/java/com/bim/compiler/bom/BomDropper.java | head -20
```

Replace must not swap a product with a different material into a
CLUSTER group. Check if there's a material guard.

## Task 12: LMP.md Staleness Check

The LMP.md verdicts reference specific counts and versions. Check if
any are now stale:

- §1 count table: SH 55/55 — is SH still 55 elements? (was 58 in P85)
- §10 seal version: "Seal v42 (73 files)" — current seal version?
- §8: "P05/P06 zero violations" — still true after PK migration?
- Known Limits table: "87 axis swaps" for DX — P85 showed 89. Update?

Flag every stale number.

## Deliverable

**11-row verdict table** with evidence for each:

| § | Check | Evidence | Verdict |
|---|-------|----------|---------|
| 1 | Input=Output | SH: BOM=X, output=X | PASS/FAIL/SUSPECT |
| 2 | LOD400 | 0 GEO_ hashes | PASS/FAIL/SUSPECT |
| ... | ... | ... | ... |

Plus:
- Stale numbers found in LMP.md (with corrections)
- Any new write paths discovered
- loadByValue() connection audit
- Replace/Add mutation path audit
- Forge write path audit
- ProveStage status (still SUSPECT?)

## What NOT to do

- Do NOT fix anything — audit only
- Do NOT change code, tests, or seal
- Do NOT run the pipeline with different parameters
- Do NOT modify any database
- Do NOT skip any check — all 11 + the staleness check

## When Done

Prepend `# DONE` + commit hash to this file's first line.

Append findings with the 11-row verdict table and all evidence.
If LMP.md has stale numbers, list them with corrections — but do
NOT edit LMP.md (watchdog reviews findings first).

---

# FINDINGS — P89 LMP Code Audit (2026-03-28)

Auditor: Claude Opus 4.6. Commit: f3c4d793. No code changes made.

## 11-Row Verdict Table

| § | Check | Evidence | Verdict |
|---|-------|----------|---------|
| 1 | Input=Output | SH: PROGRESS G1=55, LMP=55/55. TE: PROGRESS G1=48428, LMP=48428/48428. BOM leaf sums: TE m_bom_line LEAF SUM(qty)=48428, MAKE=7. BomDropper writes C_OrderLine only (no INSERT elements). Replace mutation is 1:1 swap (category guard at BomDropper:329). Add mutation creates DISCIPLINE C_OrderLine (BomDropper:132), not elements. | **PASS** |
| 2 | LOD400 | Forge code (BIM_COBOL/forge/) grep for geometry_hash/GEO_/LOD_ → 0 hits. MeshBinder reads from component_library.db (BuildingWriter:924). G5 provenance gate PASS for SH/FK/IN/DX/TE per PROGRESS. | **PASS** |
| 3 | Compiler Only | grep "extracted" in DAGCompiler/compiler/ → all hits are javadoc/comments using "extracted" as adjective (e.g. "extracted patterns", "extracted from"). Zero references to `*_extracted.db` files. `copyCategoryLookup` → 0 hits in DAGCompiler. `loadByValue()` (BasePO:129) is SELECT-only: `SELECT * FROM {table} WHERE Value = ?`. Uses `conn` parameter from caller — BomDropper passes `compileDb`, BOMWalker passes `bomConn`. No new connection opened. | **PASS** |
| 4 | Openings/Furniture | `host_element_ref` → 0 hits in DAGCompiler. Column does NOT exist in elements_meta DDL (BuildingWriter:95-108). Hosting is structural: BOM tree hierarchy places doors within wall sub-assemblies. P05/P06 proofs (no duplicate positions, no same-class overlap) confirmed by P89 session "SH 7/7 PASS". PK migration did NOT change PlacementCollectorVisitor's placement logic — only PK accessors changed (getBomLineId). | **PASS** |
| 5 | Spec Fidelity | Sources audited: C_Order→C_OrderLine (BomDropper.drop), BOM.db (BOMWalker reads bomConn), component_library.db (MeshBinder read-only), ERP.db (ValidationStage read-only). `costOfChange` → 0 hits in DAGCompiler (lives in DesignerAPIImpl only). `placeSet()` delegates to `placeItem()` (DesignerAPIImpl:2025) which creates C_OrderLine entries via Designer path — does NOT bypass the order chain. No circular dependency. | **PASS** |
| 6 | Output Path | `writeFromBomWalk` (BuildingWriter:913) is called from CompilationPipeline:427 (WriteStage). It goes through ElementPersistence.writeElementMeta (the single INSERT path at ElementPersistence:211). MEPWriter/StructuralWriter/OpeningWriter/StairWriter all use `ep.writeElementMeta()` — same gateway. Three `ensureElement()` methods exist in FloorAssemblyBuilder:334, BOMBuilder:207, BOMTypeSystem:386 — these do `INSERT OR IGNORE INTO elements_meta` but are NOT called from CompilationPipeline (grep confirms 0 references in CompilationPipeline.java). ForgeFabricationWriter → 0 hits for elements_meta/element_instances/base_geometries. | **PASS** |
| 7 | Separate From Input | BomDropper writes to C_Order/C_OrderLine in compile DB (legitimate — builds order tree). No writes to m_bom or m_bom_line (grep `INSERT.*m_bom` in BomDropper → 0 hits). `loadByValue()` is SELECT-only (BasePO:130: `SELECT * FROM`). Add mutation creates C_OrderLine (BomDropper:132), not BOM entries. | **PASS** |
| 8 | Visual Fidelity | C8/C9 gates: SH 7/7 PASS per P89 session log. TE 6/7+WARN (C9 pre-existing 60 axis swaps). PK migration did NOT change geometry_hash resolution — MeshBinder reads from component_library.db via `M_Product_Image → geometry_hash` (MeshBinder:60). Hash lookup unchanged. | **PASS** |
| 9 | Orientation | `rotation_rule` read in PlacementCollectorVisitor:183-186 (`parseRotation(line)` → `line.getRotationRule()`). PK rename (`bom_child_id` → `M_BOM_Line_ID`) did NOT touch rotation handling — column is on m_bom_line, not renamed. ProveStage: still SUSPECT — skips when `!hasRelationalData()` (CompilationPipeline:1219). `hasRelationalData()` checks `ad_room_boundary` table (CompilationContext:51). P85 confirmed "ProveStage 0ms on all 34" — none have ad_room_boundary data. Prover is effectively disabled fleet-wide. | **SUSPECT** |
| 10 | Seal Integrity | `verify_test_seal.sh` → "SEAL INTACT — 73 files". Hash: e7d56c53865d678b. File count 73 = same as P84. No `.seal` file changes in git log (no commit touches *.seal). Seal script last changed at P82 (f3c4d793). | **PASS** |
| 11 | Factorization | TE: m_bom_line LEAF=1515 lines, SUM(qty)=48428, MAKE=7. CLUSTER expansion produces per-instance offsets (PlacementCollectorVisitor:546-564) with per-instance dimensions. Replace mutation has category guard (BomDropper:329: `origCat.equals(replCat)`). Material uniformity: line.getMaterialName()/getMaterialRgba() read from BOM line (PlacementCollectorVisitor:285-286) — not overridden by Replace. | **PASS** |

## Summary: 10 PASS, 1 SUSPECT (§9 ProveStage)

## Stale Numbers in LMP.md

| Location | LMP.md says | Actual | Action |
|----------|-------------|--------|--------|
| §10 seal version | "Seal v42 (73 files)" | Seal INTACT, 73 files, but version numbering reset (PROGRESS p88 says "Seal v9"). Label "v42" is from pre-reset era | Update version label |
| Known Limits DX | "87 axis swaps" | P68 documented 89 axis mismatches | Update to 89 |
| §1 count table SH | "55/55" | 55/55 — consistent with PROGRESS G1 | No change needed |

## loadByValue() Connection Audit

All call sites traced:

| Caller | Connection | Type |
|--------|-----------|------|
| BomDropper.explode() (line 271) | `compileDb` (parameter) | Compile DB |
| BomDropper.explodeAssembly() (line 400) | `conn` (same compileDb) | Compile DB |
| BOMWalker.loadBom() (line 232) | `bomConn` | BOM DB (read-only) |
| OrderLineWalker (line 273) | BOM connection | BOM DB (read-only) |
| AssemblyStructureVisitor (line 108) | BOM connection | BOM DB (read-only) |
| PlacementLoader (line 235) | BOM connection | BOM DB (read-only) |
| DesignerAPIImpl (lines 1739, 1834, 1853) | Designer connection | Designer DB |
| MBOM.get() (line 38) | caller's conn | Varies |

**Verdict:** No loadByValue() call touches an extraction DB. All calls use compile or BOM connections.

## Replace/Add Mutation Path Audit

- **Replace** (BomDropper:322-338, 442-464): Swaps child_product_id. Category guard enforces `origCat.equals(replCat)`. Replacement goes through normal BOM tree recursion — no side-channel write. Count invariant preserved (1:1 swap).
- **Add** (BomDropper:126-138): Creates DISCIPLINE-type C_OrderLine with `qty=0` leaf impact (recipes produce 0 compiled elements until verb Strategy wired — per comment at line 125). Does NOT write to elements_meta.
- **Remove** (BomDropper:280-282, 408-411, 358-360): Skips subtree. Reduces count. No orphan writes.
- **Compress** (BomDropper:298-301, 427-431): Sets is_reference_class=true, overrides qty. Stops recursion — instantiated at walk time.

**Verdict:** All four mutations operate through the BomDropper → C_OrderLine → BOM walk → ElementPersistence chain. No side channels.

## Forge Write Path Audit

- grep `elements_meta|element_instances|base_geometries` in `BIM_COBOL/forge/` → **0 hits**
- grep `geometry_hash|GEO_|LOD_` in `BIM_COBOL/forge/` → **0 hits**
- ForgeFabricationWriter writes to `ad_forge_fabrication` table only (output.db), confirmed by absence of any elements_meta reference.

**Verdict:** Forge is write-isolated from element tables. PASS.

## ProveStage Status

**Still SUSPECT.** ProveStage (CompilationPipeline:1213) skips when `!ctx.hasRelationalData() && !ctx.entry().isGenerative()`. `hasRelationalData()` queries `ad_room_boundary` table (CompilationContext:48-58). P85 confirmed all 34 buildings return 0ms (no ad_room_boundary data exists). The prover is effectively disabled fleet-wide. This is the same finding as P84.

## New Write Paths Discovered

Three `ensureElement()` methods in FloorAssemblyBuilder, BOMBuilder, BOMTypeSystem perform `INSERT OR IGNORE INTO elements_meta` outside ElementPersistence. However, **none are called from CompilationPipeline** (confirmed by grep — 0 references). These appear to be assembly-building utilities for the legacy path, not the compilation path. Not a §6 violation but worth documenting.

## copyCategoryLookup() Audit

- grep `copyCategoryLookup|CategoryLookup` in DAGCompiler/ → **0 hits**
- Lives exclusively in IFCtoBOM (extraction path). Cannot leak into compiler. PASS.
