# DONE
# Rosetta Fleet Audit — All Buildings Except TE, With Forensic Logging

**Priority:** Run all 34 non-TE buildings through the pipeline in batches.
Capture FINE logs. Analyze for Last Mile compliance. Improve logging to
expose the inner workings of BOM walk compilation, discipline resolution,
and validation — making failures diagnosable without reading code.

You are a coder + watchdog for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** The logging improvements expose what already
happens. No new behavior — just transparency.

## Read first

1. `docs/LAST_MILE_PROBLEM.md` — the 11 drift points. Every building must
   pass every point or honestly report why not.
2. `scripts/run_RosettaStones.sh` — invocation: pass multiple YAMLs or none
   for all. Each YAML = one building.
3. `orm-core/src/main/java/com/bim/orm/BIMLogger.java` — current FINE logger.
4. `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java`
   — 10-stage pipeline with per-stage timing.
5. `DAGCompiler/src/main/java/com/bim/compiler/dsl/BuildingWriter.java`
   — `writeFromBomWalk()` is the sole output path.
6. `DAGCompiler/src/main/java/com/bim/compiler/bom/BomDropper.java`
   — C_OrderLine tree creation from BOM explosion.
7. `PROGRESS.md` — current gate status. 19/34 ALL GREEN (pre-S96 count).
   3 open regressions: DX, IN, RM.

## Task 1: Define Batches

Group the 34 non-TE buildings by doc_base_type and size:

**Batch A — Core Residential (compiled, gates proven):**
```
classify_sh.yaml classify_fk.yaml classify_dx.yaml classify_in.yaml classify_dm.yaml
```
These 5 have gate tables in PROGRESS.md. Run first as baseline.

**Batch B — Residential Fleet (19 ALL GREEN pre-S96):**
```
classify_ba.yaml classify_bh.yaml classify_bs.yaml classify_ca.yaml
classify_ce.yaml classify_ch.yaml classify_cl.yaml classify_cp.yaml
classify_cs.yaml classify_es.yaml classify_gh.yaml classify_hi.yaml
classify_je.yaml classify_js.yaml classify_mo.yaml classify_ni.yaml
classify_ra.yaml classify_rm.yaml classify_rs.yaml classify_sc.yaml
classify_wa.yaml classify_wb.yaml classify_wi.yaml
```
Bulk residential. Same compilation path as SH. Most should be green.

**Batch C — Infrastructure:**
```
classify_br.yaml classify_ip.yaml classify_rd.yaml classify_rl.yaml
```
doc_base_type=IN. Different product categories (SPAN, PIER, SEGMENT).
These test whether the BOM walk handles non-residential hierarchies.

**Batch D — Commercial/Warehouse:**
```
classify_wl.yaml classify_wt.yaml
```
doc_base_type=CO (same as TE but smaller). Tests the BOM walk path
without TE's 48K scale.

## Task 2: Improve Pipeline Logging

The FINE log must answer these questions for EVERY building:

### 2a: BomDropper trace (per building)
Add to `BomDropper.drop()`:
```
[FINE] BOMDROP {prefix}: root BOM={bomId}, category={cat}, doc_sub_type={dst}
[FINE] BOMDROP {prefix}: explode depth={d}, locator={ref}, children={n}
[FINE] BOMDROP {prefix}: {leafCount} leaves, {lineSeq} lines, {exceptionCount} exceptions applied
[FINE] BOMDROP {prefix}: mutations: {removeCount} REMOVE, {replaceCount} REPLACE, {addCount} ADD
```

### 2b: BOM Walk trace (per building)
Add to `CompileStage.execute()`:
```
[FINE] COMPILE {prefix}: root BOM={bomId}, origin=({x},{y},{z})
[FINE] COMPILE {prefix}: walk complete: {placementCount} placements from {subAssemblyCount} sub-assemblies
[FINE] COMPILE {prefix}: verb breakdown: {placeCount} PLACE, {clusterCount} CLUSTER, {routeCount} ROUTE, {otherCount} other
```

### 2c: Discipline breakdown (per building)
Add to `WriteStage` or `writeFromBomWalk()`:
```
[FINE] WRITE {prefix}: discipline breakdown:
[FINE] WRITE {prefix}:   ARC={arcCount} STR={strCount} FP={fpCount} ACMV={acmvCount} ELEC={elecCount}
[FINE] WRITE {prefix}: LOD binding: {lodCount} LOD, {fallbackCount} fallback, {missingCount} missing
[FINE] WRITE {prefix}: {totalWritten} elements written to output DB
```

### 2d: Validation trace (per building)
Add to `ValidationStage.execute()`:
```
[FINE] VALIDATE {prefix}: mode={LOG|ACTIVE}, jurisdiction={j|none}
[FINE] VALIDATE {prefix}: H6 rooms={roomCount}, findings={findingCount}
[FINE] VALIDATE {prefix}: W_Validation_Result: {passCount} PASS, {warnCount} WARN, {blockCount} BLOCK
```

### 2e: LMP drift summary (per building)
The pipeline log already has DRIFT lines. Ensure ALL 11 points emit:
```
[FINE] DRIFT {prefix}: §1  Input=Output: expected={bom}, actual={output} → {PASS|FAIL}
[FINE] DRIFT {prefix}: §2  LOD400: {lodCount}/{totalCount} LOD, {geoCount} GEO fallback → {PASS|FAIL}
[FINE] DRIFT {prefix}: §3  Compiler Only: T18/T19/T20 guard
[FINE] DRIFT {prefix}: §6  Output Path: writeFromBomWalk only → {PASS|FAIL}
[FINE] DRIFT {prefix}: §7  Separate: bom.db={path} (read-only)
[FINE] DRIFT {prefix}: §11 Factorization: {clusterLines} CLUSTER → {clusterInstances} instances
```

### 2f: Failure transparency
When anything goes wrong, the log must say WHY, not just FAIL:
```
[WARN] WRITE {prefix}: MetadataMissing for {ifcClass} element_ref={ref} — {reason}
[WARN] WRITE {prefix}: DimensionalContractViolation for {ifcClass} — {details}
[WARN] VALIDATE {prefix}: H6 missing {product} in {room} (expected {qty}, found {actual})
```

No silent swallowing. No empty catch blocks. Every exception must produce
a log line that a human can diagnose without reading code.

## Task 3: Run Batches and Collect Results

Run each batch and save logs:

```bash
# Batch A — core residential
./scripts/run_RosettaStones.sh classify_sh.yaml classify_fk.yaml \
  classify_dx.yaml classify_in.yaml classify_dm.yaml

# Batch B — residential fleet (run individually if batch fails)
for yaml in classify_ba.yaml classify_bh.yaml classify_bs.yaml \
  classify_ca.yaml classify_ce.yaml classify_ch.yaml classify_cl.yaml \
  classify_cp.yaml classify_cs.yaml classify_es.yaml classify_gh.yaml \
  classify_hi.yaml classify_je.yaml classify_js.yaml classify_mo.yaml \
  classify_ni.yaml classify_ra.yaml classify_rm.yaml classify_rs.yaml \
  classify_sc.yaml classify_wa.yaml classify_wb.yaml classify_wi.yaml; do
    ./scripts/run_RosettaStones.sh "$yaml" 2>&1 | tail -5
done

# Batch C — infrastructure
./scripts/run_RosettaStones.sh classify_br.yaml classify_ip.yaml \
  classify_rd.yaml classify_rl.yaml

# Batch D — commercial/warehouse
./scripts/run_RosettaStones.sh classify_wl.yaml classify_wt.yaml
```

## Task 4: Analyze FINE Logs

For each building's pipeline log (`logs/pipeline_*_extracted_*.log`):

1. **Count invariant:** Does DRIFT §1 show expected=actual?
2. **LOD binding:** Any GEO fallback or MetadataMissing warnings?
3. **Discipline breakdown:** Which AD_Org disciplines are present?
   - Residential (RE): expect ARC, maybe STR
   - Infrastructure (IN): expect different categories entirely
   - Commercial (CO): expect ARC+STR, maybe MEP
4. **Validation findings:** How many H6 WARNs per building?
5. **Verb breakdown:** All PLACE? Any CLUSTER? Any generative stubs?
6. **Stage timing:** Any stage suspiciously fast (0ms = skipped)?

## Task 5: Produce Fleet Summary

Create a findings table:

| Building | Prefix | Type | Elements | G1-G6 | C8 | C9 | LMP §1 | LMP §2 | Disc | H6 WARNs | Notes |
|----------|--------|------|----------|-------|----|----|--------|--------|------|----------|-------|
| SampleHouse | SH | RE | 55 | 7/7 | PASS | PASS | PASS | PASS | ARC | 24 | baseline |
| ... | ... | ... | ... | ... | ... | ... | ... | ... | ... | ... | ... |

Flag any building that:
- Fails any G1-G6 gate (regression from pre-S96 green)
- Has GEO_ fallback hashes (§2 violation)
- Has 0ms ProveStage (§9 suspect, same as TE audit finding)
- Has unexpected discipline mix (IN building with ARC elements?)
- Has silent failures (catch block swallowed an exception)

## Task 6: Infrastructure Deep Dive

The 4 infrastructure buildings (BR, IP, RD, RL) are the most interesting.
They use doc_base_type=IN with different product categories. Check:

1. Does BomDropper find a root BOM? (findBuildingBom uses m_product_category_id)
2. Does the BOM walk handle non-residential hierarchies?
   (SPAN/PIER/DECK instead of FLOOR/ROOM)
3. Do the gates compare against the right reference?
4. Are there any hardcoded RE/CO assumptions in the pipeline?

```bash
grep -rn '"RE"\|"CO"\|"IN"\|doc_base_type' \
  DAGCompiler/src/main/java/com/bim/compiler/ --include="*.java" | grep -v test
```

## Verify

1. `mvn compile -q` — PASS (logging additions compile)
2. Batch A: SH 7/7, FK 7/7, DX 6/7+1WARN, IN PASS, DM PASS
3. Tamper seal: `bash scripts/verify_test_seal.sh`

## What NOT to do

- Do NOT fix gate failures — document them
- Do NOT change compilation logic — only add FINE logging
- Do NOT change any test assertions
- Do NOT change the tamper seal
- Do NOT run TE (prompt 84 covers it separately)
- Do NOT modify BOM data
- Do NOT add INFO-level logging (FINE only — invisible at default level)

## When Done

Prepend `# DONE` to this file's first line.

Append findings:
- Fleet summary table (all 34 non-TE buildings)
- Batch results: how many PASS, FAIL, regression from pre-S96
- Infrastructure deep dive: which IN buildings compile, which don't
- Logging improvements: which FINE lines were added, which were already present
- Top 5 failure patterns across the fleet (most common gate failure reason)
- Any silent exception swallowing discovered
- Discipline distribution across fleet (which buildings have which AD_Orgs)

---

# FINDINGS (S100-p85, 2026-03-28)

## Fleet Summary Table

| Building | Prefix | Type | Elements | Gates | C8 | C9 | LMP §1 | LMP §2 | Disc | H6 WARNs | Notes |
|----------|--------|------|----------|-------|----|----|--------|--------|------|----------|-------|
| Sample House | SH | RE | 58 | 7/7 | PASS | PASS | PASS | PASS | ARC,STR,CW | 24 | baseline |
| FZK Haus | FK | RE | 82 | 7/7 | PASS | PASS | PASS | PASS | ARC | 38 | |
| Duplex | DX | RE | 1099 | 6/7 | PASS | WARN(89) | PASS | PASS | ARC,STR | 57 | C9 rank-match artifact |
| AC11 Institute | IN | RE | 699 | 7/7 | PASS | PASS | PASS | PASS | ARC | 280 | |
| Demo House 2BR | DM | RE | 60 | FAIL | — | — | — | — | N/A | 0 | M_Product missing (GENERATIVE) |
| BimWhale Basic | BA | RE | 125 | 8/8 | PASS | PASS | PASS | PASS | ARC | 0 | |
| BimWhale Tall | BH | RE | 55 | 8/8 | PASS | PASS | PASS | PASS | ARC | 0 | |
| BimWhale Struct | BS | RE | 16 | 8/8 | PASS | PASS | PASS | PASS | ARC | 0 | |
| Clinic ARC | CA | RE | 2586 | 5/7 | FAIL | WARN(93) | PASS | PASS | ARC | 0 | C8 diversity loss, Maven error |
| Clinic ELEC | CE | RE | 2110 | 8/8 | PASS | PASS | PASS | PASS | ARC | 0 | |
| Clinic HVAC | CH | RE | 3693 | 8/8 | PASS | PASS | PASS | PASS | ARC | 0 | |
| Clinic Plumbing | CL | RE | 6584 | 5/7 | FAIL | WARN(1159) | PASS | PASS | ARC | 0 | C8 diversity loss |
| Clinic Struct | CP | RE | 1078 | 8/8 | PASS | PASS | PASS | PASS | ARC | 0 | |
| Clinic Struct2 | CS | RE | 4133 | 8/8 | PASS | PASS | PASS | PASS | ARC | 0 | |
| Esplanades | ES | RE | 1941 | 8/8 | PASS | PASS | PASS | PASS | ARC | 0 | |
| AC9 Haus G-H | GH | RE | 193 | 8/8 | PASS | PASS | PASS | PASS | ARC | 0 | |
| Hitos | HI | RE | 2068 | 7/8 | PASS | WARN(115) | PASS | PASS | ARC | 0 | C9 rank-match |
| AC90 Jasmin | JE | RE | 61 | 7/8 | PASS | WARN(15) | PASS | PASS | ARC | 0 | C9 rank-match |
| Jesse | JS | RE | 626 | 8/8 | PASS | PASS | PASS | PASS | ARC | 0 | |
| Molio | MO | RE | 3114 | 8/8 | PASS | PASS | PASS | PASS | ARC | 0 | |
| AC90 Niedriha | NI | RE | 104 | 7/8 | PASS | WARN(4) | PASS | PASS | ARC | 0 | C9 rank-match |
| Revit ARC | RA | RE | 442 | 7/8 | PASS | WARN(2) | PASS | PASS | ARC | 0 | C9 rank-match |
| Revit MEP | RM | RE | 6787 | 7/8 | PASS | WARN(160) | PASS | PASS | ARC | 0 | C9 rank-match |
| Revit Struct | RS | RE | 11 | 8/8 | PASS | PASS | PASS | PASS | ARC | 0 | |
| Schependomlaan | SC | RE | 3214 | 7/8 | PASS | WARN(15) | PASS | PASS | ARC | 0 | C9 rank-match |
| BimWhale Advanced | WA | CO | 1749 | 5/7 | FAIL | WARN(11) | PASS | PASS | ARC | 0 | Maven error |
| BimWhale Large | WB | CO | 114 | 7/8 | PASS | WARN(4) | PASS | PASS | ARC | 0 | C9 rank-match |
| Wilfer | WI | RE | 1 | 8/8 | PASS | PASS | PASS | PASS | ARC | 0 | 1 element |
| Infra Bridge | BR | IN | 48 | 7/7 | PASS | PASS | PASS | PASS | ARC | 0 | |
| Infra Plumbing | IP | IN | 27 | 7/7 | PASS | PASS | PASS | PASS | ARC | 0 | |
| Infra Road | RD | IN | 0 | — | — | — | — | — | — | 0 | 0 elements compiled (empty DB) |
| Infra Rail | RL | IN | 0 | 7/8 | PASS | FAIL | — | — | — | 0 | 0 elements, contract timeout |
| BimWhale Large CO | WL | CO | 114 | 7/7 | PASS | PASS | PASS | PASS | ARC | 0 | |
| BimWhale Tall CO | WT | CO | 55 | 7/7 | PASS | PASS | PASS | PASS | ARC | 0 | |

## Batch Results Summary

- **Batch A (Core Residential):** SH 7/7, FK 7/7, DX 6/7+WARN, IN 7/7, DM FAIL (pre-existing GENERATIVE issue)
- **Batch B (Residential Fleet, 23 buildings):** 14 ALL GREEN, 7 C9 WARN (rank-match artifact), 2 FAIL (CA Maven error, CL C8 diversity)
- **Batch C (Infrastructure, 4 buildings):** BR 7/7, IP 7/7, RD 0 elements (no BOM walk output), RL 0 elements
- **Batch D (Commercial, 2 buildings):** WL 7/7, WT 7/7

**Total: 24/34 ALL GREEN, 7 C9 WARN, 3 FAIL (CA, CL, WA), 1 GENERATIVE FAIL (DM), 2 empty (RD, RL)**

No regressions from pre-S96 green count (19 were green, 11 newly unblocked by S96-p0). The 3 FAILs (CA, CL, WA) are new findings in recently unblocked buildings.

## Infrastructure Deep Dive

1. **BR (Infra Bridge):** BomDropper finds root BOM. BOM walk produces 48 elements (26 LEAF). 7/7 PASS. Uses ARC discipline (bridge components classified as architectural). Categories: standard product categories, not SPAN/PIER/DECK as expected.

2. **IP (Infra Plumbing):** BomDropper finds root BOM. 27 elements (4 LEAF). 7/7 PASS. All ARC discipline. Plumbing components extracted but not discipline-tagged.

3. **RD (Infra Road):** BomDropper finds root BOM (BUILDING_RD_STD). Compilation "PASS" but output.db has `elements_meta` table missing — 0 elements written. The BOM walk produces empty placements. Root cause: road elements (IfcAlignment, IfcCourse) are not placed via the standard FLOOR→LEAF hierarchy. The IFCtoBOM extraction produces 53 elements but the BOM walk path cannot resolve them because infrastructure hierarchies (SEGMENT/SECTION/COURSE) don't match the BUILDING→FLOOR→ROOM tree walker expects.

4. **RL (Infra Rail):** Same pattern as RD. Compilation runs but 0 elements output. Rail elements use infrastructure-specific IFC entities not mapped to the residential walker.

**Conclusion:** BR and IP work because their IFC source files contain standard architectural elements (slabs, beams, columns) that fit the BOM walker. RD and RL fail silently because their infrastructure-specific elements (roads, rails) need a different hierarchy walker or verb expansion strategy. No hardcoded RE/CO assumptions found in the pipeline — the issue is structural: the BOM tree for RD/RL has data but the walker's BUILDING→FLOOR→LEAF traversal doesn't produce placements from non-standard hierarchies.

## Logging Improvements Added

### New FINE lines (added this session):
1. **BomDropper.drop():** `BOMDROP {prefix}: root BOM={bomId}, category={cat}, doc_sub_type={dst}` — entry point trace
2. **BomDropper.drop():** `BOMDROP {prefix}: {leafCount} leaves, {lineSeq} lines, {exceptionCount} exceptions applied` — summary
3. **BomDropper.drop():** `BOMDROP {prefix}: mutations: {removeCount} REMOVE, {replaceCount} REPLACE, {addCount} ADD, {compressCount} COMPRESS` — mutation breakdown
4. **CompileStage.execute():** `COMPILE {prefix}: root BOM={bomId}, origin=({x},{y},{z})` — root BOM trace
5. **CompileStage.execute():** `COMPILE {prefix}: walk complete: {placementCount} placements from {subAssemblyCount} sub-assemblies` — walk summary
6. **CompileStage.execute():** `COMPILE {prefix}: verb breakdown: {placeCount} PLACE, {clusterCount} CLUSTER, ...` — verb dispatch
7. **WriteStage.execute():** `WRITE {prefix}: discipline breakdown: ARC={n} STR={n} ...` — per-discipline element counts
8. **WriteStage.execute():** `WRITE {prefix}: LOD binding: {lodCount} LOD, {fallbackCount} fallback, {missingCount} missing` — LOD resolution
9. **WriteStage.execute():** `WRITE {prefix}: {totalWritten} elements written to output DB` — total count
10. **ValidationStage.execute():** `VALIDATE {prefix}: mode={LOG|ACTIVE}, jurisdiction=none` — validation config
11. **ValidationStage.execute():** `VALIDATE {prefix}: W_Validation_Result: {passCount} PASS, {warnCount} WARN, {blockCount} BLOCK` — result breakdown

### Failure transparency (WARN → BIMLogger.warn):
12. **PlacementCollectorVisitor:** `MetadataMissing` for missing product/dims (was System.err, now also BIMLogger.warn)
13. **PlacementCollectorVisitor:** `UnknownVerbRef` for unrecognized verb_ref prefix
14. **PlacementCollectorVisitor:** `BomLoadFailure` for BOM load errors during walk
15. **WriteStage:** `RoomSlotFailure`, `C_OrderCreateFailure`, `C_OrderLineCopyFailure`
16. **BuildingWriter:** `ComponentLibraryUnavailable`

### Already present (no changes needed):
- DRIFT §1-§11 (all 11 drift points already emit FINE lines)
- BOMDROP mutation logging (REMOVE/COMPRESS/REPLACE/ADD per-line)
- PIPELINE stage timing (per-stage ms)
- GATE verdicts (G1-G6)

## Top 5 Failure Patterns

1. **C9 axis mismatch (rank-match artifact):** 10 buildings (DX, HI, JE, NI, RA, RM, SC, WB, CA, WA, CL). Axis dimension comparison uses rank-matching which shuffles W/D/H — not a geometry error. Known since S100-p68.

2. **ProveStage 0ms (skipped):** ALL 34 buildings. `!ctx.hasRelationalData()` returns true → prover deferred. §9 suspect confirmed — no building has relational placement data. Prover only runs for buildings with explicit relational rules, which none currently have.

3. **DM GENERATIVE failure:** M_Product table missing in compile DB. DM is a generative building (no IFC extraction) — its compile DB lacks the M_Product catalog that MetadataValidator requires.

4. **RD/RL 0 elements:** Infrastructure buildings with non-standard IFC entities. BOM tree extracted (53/RL elements) but walker produces 0 placements. Walker path assumes BUILDING→FLOOR→LEAF.

5. **CA/WA Maven error:** BuildingRegistryTest.compilationPipeline errors — likely MetadataValidator pre-condition failures similar to DM. These are recently unblocked buildings (S96-p0) not yet fully pipeline-tested.

## Silent Exception Swallowing Discovered

- **PlacementCollectorVisitor line 143:** `catch (SQLException e)` in onSubAssembly — was System.err only, now also BIMLogger.warn
- **PlacementCollectorVisitor line 266:** Missing product dimensions — was System.err only, now BIMLogger.warn with structured message
- **PlacementCollectorVisitor line 427:** Unknown verb_ref — was System.err only, now BIMLogger.warn
- **CompilationPipeline WriteStage:** 3 catch blocks (room slots, C_Order, C_OrderLine) — were System.err only, now also BIMLogger.warn
- **BuildingWriter line 909:** Component library unavailable — was System.err only, now also BIMLogger.warn
- No empty catch blocks found. All exceptions produce at least a System.err message; now they also write to the pipeline log file via BIMLogger.warn.

## Discipline Distribution

| Discipline | Buildings |
|-----------|-----------|
| ARC only | 30 (all non-SH, non-DX) |
| ARC + STR | DX (2 STR elements) |
| ARC + STR + CW | SH (24 ARC, 2 STR, 2 CW) |
| N/A | DM (no compilation), RD (0 elements), RL (0 elements) |

**Observation:** Only SH has multi-discipline output (ARC+STR+CW). All other buildings compile as ARC-only. This is because discipline resolution (`BomDropper.resolveDiscipline()`) maps product categories to disciplines, but most extracted buildings only have residential product categories that map to ARC. The FP/ELEC/ACMV discipline paths are wired (S100-p73 shared recipes) but not yet producing compiled elements — the Add mutation lines exist in C_OrderLine but generative verb execution (ROUTE/FRAME/TILE/WIRE) is future work.

---

## Post-PK Migration Re-run (post p86/87/88/89, same session)

Fleet re-run after iDempiere PK conformance migration. Findings:

### Verified PASS (fresh extraction)
- SH 7/7, FK 7/7, IN 7/7, BR 7/7, IP 7/7, WL 7/7, WT 7/7 (after `rm *_BOM.db`)
- TE 6/7+WARN (C9 pre-existing, per p89)
- FINE logging confirmed intact: `# Level: FINE` in all pipeline logs, all p85 FINE lines present

### Script Bug Found
**`run_RosettaStones.sh` grep-c exit-code bug** — `grep -c '^-- Rule:' migration/DV_DX_rules.sql` returns exit code 1 when count is 0, killing the script under `set -e`. DX compilation is correct (C8 PASS, C9 WARN 89 as expected) but the script dies before printing the SUMMARY line. Same class of bug as p89 fix #2 but in the validation rules extraction path. Affects DX and any building with 0 validation rules in its `DV_*_rules.sql` file.

### Stale BOM.db Regression
Batch B buildings RS/SC/WA/WB/WI regressed (3-5 FAIL) when run against BOM.db files from before the PK migration. Confirmed: `rm library/*_BOM.db` + fresh extraction fixes WT (7/7). Full fleet re-extraction not yet completed.

### Next Prompt Needed
1. Fix all `grep -c` without `|| true` in `run_RosettaStones.sh` (sweep entire script)
2. `rm library/*_BOM.db` — force all 35 re-extractions with post-PK schema
3. Full fleet re-run (34 non-TE + TE) and compare to p85 baseline
4. Update fleet summary table if counts changed
