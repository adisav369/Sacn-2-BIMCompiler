# DONE
# T3.1 + T3.4 — Pipeline Wiring: RouteDocEvent + RE Subset + Edge Persistence

**Spec:** DISC_VALIDATION_DB_SRS §10.4.11 T3.1 Implementation + T3.4 Implementation
**Prereq commits:**
- `7237a737` P101: 6 MEP RouteBuilders + DisciplineRouteRegistry + RouteDocEvent
- `a18ac379` P104: Verification — 4 blockers diagnosed (B1–B4)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Wire existing RouteBuilders into CompilationPipeline
per §10.4.11 T3.1 Implementation. No new routing logic. All 6 builders already
work (DisciplineRouteBuilderTest 15/15).

## Read first

1. `PROGRESS.md` §Current State
2. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.11 — T3.1 Implementation + T3.4 Implementation (the spec for this prompt)
3. `prompts/104_t31_t34_integration.md` §Findings — 4 blockers (B1–B4), TE verification data
4. `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java` — STAGES list (line 60), CompileStage (line 344), WriteStage (line 400)
5. `BIM_COBOL/src/main/java/com/bim/cobol/geometry/RouteDocEvent.java` — fire/fireAll
6. `BIM_COBOL/src/main/java/com/bim/cobol/geometry/SqlBuildingGeometry.java` — reads c_orderline
7. `DAGCompiler/src/main/java/com/bim/compiler/callout/OrderLineProductCallout.java` — onProductChanged + expandDisciplineLines

## Deliverables (all spec'd in §10.4.11)

### B1 — Wire callout + RouteDocEvent into pipeline (§10.4.11 T3.1 Implementation)

Pipeline sequence: `CompileStage → [callout + RouteDocEvent.fireAll()] → WriteStage`

### B2 — Edge persistence: system_edges + system_nodes (§10.4.11 T3.1 Implementation)

Table schemas spec'd in §10.4.11. Populated from RouteResult.

### B3 — RE subset: mep_disciplines YAML key (§10.4.11 T3.4 Implementation)

`classify_sh.yaml`: add `mep_disciplines: [ELEC, SP]`. Callout reads whitelist.

### B4 — BIMEyes gate relaxation (§10.4.11 T3.1 Implementation)

`hasRelationalData OR system_edges > 0` → P16/P17 fire.

### Housekeeping — CW stale refs (3 sessions overdue)

| File | Line | Fix |
|------|------|-----|
| `Discipline.java` | 17 | `// Curtain Wall services` → `// Cold Water (potable)` |
| `IfcLabelMapper.java` | 97 | `"Curtain Wall"` → `"Cold Water"` |
| `RosettaStoneToBOM.py` | ~113 | `'Curtain Wall'` → `'Cold Water'` |
| `SHPipelineTest.java` | ~77 | `"Curtain Wall STR"` → `"Cold Water STR"` |

## Gate

- SH 7/7 PASS with 2 discipline OrderLines (ELEC + SP)
- TE 6/7+WARN (C9 pre-existing) with system_edges > 0
- BIMEyes P17 fires (not SKIP) on TE
- DisciplineRouteBuilderTest 15/15 PASS (regression)
- CrawlRouterTest 18/18 PASS (regression)
- CW stale refs: 0 remaining

## What NOT to do

- Do NOT modify any RouteBuilder — proven (P101, 15/15)
- Do NOT modify CrawlRouter or CrawlOps — proven (P100, 18/18)
- Do NOT modify existing migration files (sacred — append only)
- Do NOT modify ARC/STR compilation path
- Do NOT fix C9 axis warnings — pre-existing
- Do NOT debug issues outside scope — log them via FINE and report in findings

## Spec citations

- `// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T3.1 Implementation — Witness: W-ROUTE-STAGE-1`
- `// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T3.4 Implementation — Witness: W-RE-SUBSET-1`

## Commit

Commit your work, then run TE + SH. Let the FINE log speak — do not chase
issues outside scope. Report what the log says.

```bash
git add <files touched>
git commit -m "[S100-p105] RouteStage pipeline wiring + RE subset + edge persistence (T3.1+T3.4)"
```

Then verify:
```bash
./scripts/run_RosettaStones.sh classify_sh.yaml
./scripts/run_RosettaStones.sh classify_te.yaml
```

## When Done

Prepend `# DONE` to this file's first line.

Append findings below the `---` line. The watchdog reads these — be honest:
- Pipeline stage position and wiring
- system_edges / system_nodes row counts for TE and SH
- SH discipline OrderLine verification (ELEC + SP)
- TE FINE log: discipline breakdown, RouteDocEvent output, any WARNs
- P17 proof status (fires / SKIP)
- CW stale refs fixed (list files touched)
- Any blockers or surprises — document, do NOT fix outside scope

---

## Findings (S100-p105, 2026-03-29)

**Commit:** [933888f8](https://github.com/red1oon/BIMCompiler/commit/933888f8)

### Pipeline stage position and wiring

RouteStage wired as **Step 4** (between CompileStage and WriteStage) in 12-step pipeline.
Uses **SPI pattern** (ServiceLoader) to break circular dependency: `RouteExecutor` interface
in DAGCompiler, `RouteExecutorImpl` in BIM_COBOL. Same pattern as VerbStage/VerbExecutor.

Pipeline sequence: `CompileStage → RouteStage [callout + RouteDocEvent.fireAll()] → TemplateStage → WriteStage`

### system_edges / system_nodes row counts

**Tables created in output.db** (DDL works): both SH and TE have `system_edges` and `system_nodes` tables.
**Row counts: 0 for both.** RouteExecutor SPI in BIM_COBOL not on BuildingRegistryTest classpath
(DAGCompiler-only test). Logs: "No RouteExecutor on classpath — routing deferred".
When compilation includes BIM_COBOL on classpath, edges will be populated.

### SH discipline OrderLine verification (ELEC + SP)

2 DISCIPLINE OrderLines created: ELEC (AD_Org=4, seq=40) + SP (AD_Org=7, seq=70).
Whitelist `[ELEC, SP]` from ad_sysconfig (stored during IFCtoBOM from classify_sh.yaml).
FP, ACMV, CW, LPG filtered. Parasitic qty: 0 (no ERP.db children for ELEC/SP yet).

### TE FINE log

All 6 discipline OrderLines already existed (P98 idempotent). Callout returned 0.
Disciplines found: `[ACMV, CW, ELEC, FP, LPG, SP]`. SPI not loaded — routing deferred.
Breakdown: ARC=471 SP=417 CW=187 ACMV=143 FP=109 ELEC=106 STR=60 LPG=25.
C9: 60 axis mismatches (pre-existing).

### P17 proof status

**NOT FIRING.** Gate relaxed (`hasRelationalData OR system_edges > 0`) but both false.
Will fire when RouteExecutorImpl populates system_edges.

### CW stale refs fixed

- `Discipline.java:17` — `// Cold Water (potable)`
- `IfcLabelMapper.java:97` — `"Cold Water"`
- `RosettaStoneToBOM.py:113` — `'Cold Water'`
- `SHPipelineTest.java:77` — `"Cold Water STR"`

### Blockers / surprises

1. **Circular dependency** — DAGCompiler→BIM_COBOL impossible. Solved via SPI.
2. **SPI not loaded in BuildingRegistryTest** — system_edges=0 until integration test includes BIM_COBOL jar.
3. **Parasitic qty 0 for SH** — ELEC/SP shared BOMs in ERP.db have no M_BOM_Line children yet.

---

## P105b — SPI classpath fix (same session)

**Watchdog finding:** `run_RosettaStones.sh` line 225 runs `mvn test -pl DAGCompiler`.
BIM_COBOL is not a dependency of DAGCompiler. ServiceLoader never finds
`RouteExecutorImpl` or `BimCobolVerbExecutor`. This means:
- **RouteStage** — "No RouteExecutor on classpath" → system_edges = 0
- **VerbStage** — "No VerbExecutor on classpath — log-only mode" → W_Verb_Node = 0
- **All 77 verbs** have never fired during script compilation. Only in BIM_COBOL unit tests.

This is pre-existing (since VerbStage was introduced), not a P105 regression.

### Fix

Add `bim-cobol` as a **test-scope** dependency in `DAGCompiler/pom.xml`:

```xml
<dependency>
    <groupId>com.bim</groupId>
    <artifactId>bim-cobol</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

No compile-time coupling — DAGCompiler still only sees the SPI interfaces.
At test time (when the script runs `mvn test -pl DAGCompiler`), ServiceLoader
finds both `BimCobolVerbExecutor` and `RouteExecutorImpl`.

### Verify

After adding the dependency:

```bash
./scripts/run_RosettaStones.sh classify_sh.yaml
./scripts/run_RosettaStones.sh classify_te.yaml
```

Report:
- Does VerbStage now fire? (PLACE BOM, TRIM WALLS TO ROOF in SH log)
- Does RouteStage now fire? (system_edges > 0)
- W_Verb_Node row count for SH and TE
- system_edges row count for SH and TE
- P17 SystemConnectedProof status (fires / SKIP)
- SH 7/7, TE 6/7+WARN (no regression)
- Any verb failures — document, do NOT fix outside scope

### Results

**Commit:** [1b942f2b](https://github.com/red1oon/BIMCompiler/commit/1b942f2b)

**Approach:** Cannot use `<dependency>` (Maven reactor cycle). Used surefire
`additionalClasspathElements` to add `BIM_COBOL/target/classes` at test time.
Added POI as `<scope>test</scope>` dependency for transitive coverage.

**SH 7/7 PASS. TE 6/7+WARN (C9 pre-existing). Zero regression.**

| Metric | SH | TE |
|--------|----|----|
| RouteExecutor SPI loaded | YES | YES |
| RouteDocEvent.fireAll() fired | YES (ELEC, SP) | YES (all 6) |
| Disciplines routed | 2 | 6 |
| floors found by SqlBuildingGeometry | 0 | 0 |
| CrawlRouter ops executed | 0 | 0 |
| system_edges | **0** | **0** |
| system_nodes | **0** | **0** |
| P17 SystemConnectedProof | NOT FIRING | NOT FIRING |

**Root cause of system_edges=0:** `SqlBuildingGeometry.floors()` queries
`C_OrderLine WHERE host_type = 'IfcBuildingStorey'`. Neither SH nor TE has
`host_type='IfcBuildingStorey'` in c_orderline — the BOM tree uses `BUILDING`,
`FLOOR`, `ROOM`, `LEAF`, `DISCIPLINE` host types, not IFC class names.
The `floors()` method returns 0 floors → 0 rooms → 0 CrawlOps → 0 edges.

**P17 status:** Gate relaxation works (`hasRelationalData OR system_edges > 0`),
but both conditions remain false. P17 will fire once `SqlBuildingGeometry.floors()`
is aligned to query BOM host_type='FLOOR' instead of IFC 'IfcBuildingStorey'.

**This is the next blocker:** SqlBuildingGeometry needs to map BOM hierarchy
host_types (FLOOR, ROOM) instead of IFC class names (IfcBuildingStorey, IfcSpace).
Not fixed here — outside prompt scope.
