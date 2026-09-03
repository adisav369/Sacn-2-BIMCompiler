# DONE — [d1b60e20](https://github.com/red1oon/BIMCompiler/commit/d1b60e20)
# VerbStage: BOM-Driven Verb Execution + P107 Verification

**Spec:** BIM_COBOL.md, BOMBasedCompilation.md §verbs, DISC_VALIDATION_DB_SRS §10.4.11
**Prereq:** P107 DONE (`b7ddce20`). RouteStage wired (P105 `933888f8`). SPI classpath fixed (P105b `1b942f2b`).

You are a coder for bim-compiler. Two tasks: investigate + verify.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Read the specs. Read the code. Report findings. Do not invent.

## Read first

1. `PROGRESS.md` §Current State
2. `docs/BIM_COBOL.md` — full verb spec, design intent
3. `docs/BOMBasedCompilation.md` — how verbs relate to BOM lines
4. `DAGCompiler/src/main/java/com/bim/compiler/dsl/VerbStage.java` — current skip logic (line 36: checks for `scripts/<buildingId>.bimcobol`)
5. `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java` line 407 — RouteStage inner class
6. `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/PlacementCollectorVisitor.java` — where verb breakdown is produced
7. `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationContext.java` — what data flows between stages
8. `scripts/*.bimcobol` — all 4 existing scripts (SH, DM, TB_LKTN, F5)
9. `BIM_COBOL/src/main/java/com/bim/cobol/BimCobolVerbExecutor.java` — SPI implementation
10. `BIM_COBOL/src/main/java/com/bim/cobol/VerbRegistry.java` — verb registration

## Task 1 — Verb Architecture Investigation

The BOM walk already produces a verb breakdown per building:
```
SH:  22 PLACE, 4 CLUSTER, 0 ROUTE
TE:  1163 PLACE, 345 CLUSTER, 2 ROUTE, 2 FRAME
```

Verbs are on the BOM lines. The Java verb classes (PlaceBomVerb, TrimWallsToRoofVerb, etc.) are polymorphic and reusable. Yet VerbStage currently requires a hand-written `.bimcobol` file per building — if no file, it SKIPs entirely.

**Question:** Should VerbStage derive its recipe from the BOM walk (BOM-driven) rather than requiring a hand-authored script (script-driven)?

Investigate and report:

1. **Where do verbs originate?** — What field on M_BOMLine (or equivalent) carries the verb? How does the walker collect them?
2. **Does CompilationContext carry the verb breakdown?** — Can VerbStage read it, or is it lost after CompileStage?
3. **What do .bimcobol scripts add beyond what the BOM already knows?** — Compare TB_LKTN.bimcobol (has room-level WIRE LIGHTING, ROUTE SPRINKLERS with SPACING param) against the BOM walk output. Is there information in the script that ISN'T on the BOM line?
4. **What does the spec say?** — Does BIM_COBOL.md or BBC.md envision automatic verb execution or manual scripting?
5. **Proposal:** Based on findings, write a concrete recommendation:
   - (A) BOM-driven: VerbStage reads verb breakdown from ctx, no .bimcobol needed
   - (B) Hybrid: BOM provides defaults, .bimcobol overrides for building-specific params
   - (C) Script-driven (status quo): .bimcobol is the interface, must be authored per building

Do NOT write code. Write findings to the appendix below.

## Task 2 — P107 Verification (Terminal RosettaStone)

Three items unverified from P107 — no pipeline has run since RouteStage was wired (P105).

Run:
```bash
./scripts/run_RosettaStones.sh classify_te.yaml
```

Then verify from the FINE log:

| Check | What to look for | Expected |
|-------|-----------------|----------|
| RouteStage fires | `DISCIPLINE ROUTE` step appears in log (not SKIP) | Step 4 with discipline list |
| system_edges > 0 | `RouteStage done: N routes, N segments, N edges` | edges > 0 |
| P17 fires | `P17` or `SystemConnectedProof` in FINE log | fires (not SKIP) |
| TE gate | TE 6/7+WARN (C9 pre-existing) | no regression |

If RouteStage SKIPs or edges = 0, diagnose why and report — do NOT fix.

## Gate

- Task 1: Written findings with concrete recommendation (A/B/C)
- Task 2: TE RosettaStone PASS post-P107, three items verified or diagnosed

## What NOT to do

- Do NOT modify VerbStage.java or any Java code
- Do NOT modify existing migration files
- Do NOT fix issues found — report only
- Do NOT chase issues outside scope

## Commit

```bash
git add PROGRESS.md
git commit -m "[S100-p108] VerbStage architecture investigation + P107 TE verification"
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- Task 1: verb origin chain, ctx availability, script vs BOM gap, spec intent, recommendation
- Task 2: RouteStage status, system_edges count, P17 status, TE gate result
- Any surprises — document, do NOT fix

---

## Findings — Task 1: Verb Architecture Investigation

### 1. Where do verbs originate?

Two distinct verb mechanisms exist in the codebase:

**A. BOM-line `verb_ref` (placement expansion patterns)**
- Field: `m_bom_line.verb_ref` column (TEXT), accessed via `MBOMLine.getVerbRef()` (`X_M_BOMLine.java:186`)
- Set during extraction by `VerbFactorizer` — encodes spatial patterns as compact strings
- Prefixes: `TILE:nx:ny:stepX:stepY`, `CLUSTER:dx,dy,dz,w,d,h;...`, `ROUTE:X:step:n|Y:step:n`, `FRAME:x1,x2|y1,y2`, `SPRAY:stepX:stepY`
- Consumed by `PlacementCollectorVisitor.expandVerb()` (line 404) during CompileStage BOM walk
- Purpose: **placement expansion** — turns qty>1 BOM lines into per-instance positions

**B. `.bimcobol` script verbs (BIM COBOL commands)**
- 77 registered verbs in `VerbRegistry.createDefault()` — polymorphic `Verb<T>` implementations
- Dispatched by `ScriptRunner` → `VerbRegistry.dispatch()` via longest-prefix keyword match
- Executed by `VerbStage` (Step 6) which reads `scripts/<buildingId>.bimcobol`
- Purpose: **higher-level operations** — MEP installation, compliance checking, BOM mutation, reporting

These are fundamentally different mechanisms. BOM-line `verb_ref` is a data encoding for spatial patterns; `.bimcobol` verbs are executable commands.

### 2. Does CompilationContext carry the verb breakdown?

**No.** `CompilationContext` stores `walkedPlacements` (List<Placement>) via `setWalkedPlacements()` but does NOT carry the verb breakdown. The breakdown string is produced by `PlacementCollectorVisitor.getVerbBreakdown()` and logged at FINE level in `CompileStage` (`CompilationPipeline.java:400-401`) but never set on the context. VerbStage cannot read it.

The verb counters (placeCount, clusterCount, tileCount, routeCount, frameCount, sprayCount) are instance fields on the visitor — they die when the visitor goes out of scope.

### 3. What do .bimcobol scripts add beyond what the BOM knows?

Comparison of TB_LKTN.bimcobol vs BOM walk output:

| Script verb | On BOM? | What it adds |
|------------|---------|--------------|
| `CHECK BOM TB_LKTN` | No | Structural integrity validation — read-only query |
| `WIRE LIGHTING TB_LKTN "Ground Floor" bilik_utama` | No | Room-level MEP: fixture grid + conduit + lux — needs room name, storey, building args |
| `WIRE LIGHTING` (×3 rooms) | No | Per-room parameterization — the BOM has no concept of "wire this room" |
| `ROUTE SPRINKLERS TB_LKTN "Ground Floor" SPACING 3000` | No | MEP routing with SPACING parameter — sprinkler grid computation, not just placement |
| `CHECK PLACEMENT TB_LKTN "Ground Floor"` | No | P01-P04 geometric proofs on compiled output |
| `CHECK CLASH TB_LKTN "Ground Floor"` | No | MEP vs structural bbox overlap detection |
| `VERIFY PLACEMENT TB_LKTN "Ground Floor"` | No | Cross-DB placement fidelity check |

**Gap summary:** Scripts add three categories the BOM cannot express:
1. **MEP installation** (WIRE LIGHTING, ROUTE SPRINKLERS) — these need room geometry context, spacing parameters, and building code references
2. **Post-compilation verification** (CHECK PLACEMENT, CHECK CLASH, VERIFY PLACEMENT) — these operate on output.db, which doesn't exist during BOM walk
3. **Post-compilation mutation** (TRIM WALLS TO ROOF) — modifies geometry after compilation

The BOM-line `verb_ref` handles only spatial expansion of already-known elements. The script verbs CREATE new elements (MEP fixtures, pipes) and VERIFY the result.

### 4. What does the spec say?

**BBC.md §6:** "The GUI emits BIM COBOL verbs, never direct SQL. 76 verbs in 5 tiers." — Verbs are the interface between the BIM Designer GUI and the compiler. The GUI will emit verb lines; the compiler executes them. This is explicitly script-driven (the GUI writes the script).

**BIM_COBOL.md §1:** "BIM COBOL is the COBOL of construction. It is a high-level, domain-specific language where construction intent compiles down to IFC geometry + procurement BOM + compliance witnesses." — Verbs are intended as a human-readable DSL, not auto-generated from data.

**BIM_COBOL.md §2.2:** The compilation pipeline positions VerbStage AFTER WriteStage (Step 6). This is deliberate — verbs operate on compiled output, not on raw BOM data. The BOM walk happens in CompileStage (Step 3).

**Design intent:** The spec envisions `.bimcobol` as the authored recipe — analogous to COBOL source code. The BOM provides WHAT to build; the script provides HOW to build it (MEP installation order, verification sequence, post-processing).

### 5. Recommendation: (B) Hybrid

**Rationale:**

- **(A) BOM-driven is insufficient.** The BOM walk produces placement patterns (TILE/CLUSTER/ROUTE/FRAME/SPRAY) — these are already consumed during CompileStage. VerbStage handles a different class of operations (MEP installation, verification, mutation) that require runtime context the BOM doesn't carry (room geometry, output.db state, building code params).

- **(C) Script-driven (status quo) won't scale.** 34 buildings × hand-authored scripts = 34 scripts to maintain. Most buildings need the same basic recipe: CHECK BOM → (discipline-specific MEP) → CHECK PLACEMENT → CHECK CLASH. Only the MEP verbs vary.

- **(B) Hybrid is the natural fit:**
  - **BOM-driven defaults:** VerbStage reads the discipline list from `c_orderline` (already available post-RouteStage) and auto-generates the standard recipe: CHECK BOM, per-discipline MEP verbs, CHECK PLACEMENT, CHECK CLASH.
  - **Script overrides:** When `scripts/<id>.bimcobol` exists, it overrides the default recipe entirely (current behavior). This handles building-specific params like TB_LKTN's room-level WIRE LIGHTING with custom spacing.
  - **Implementation path:** Add a `defaultVerbRecipe(CompilationContext)` method to VerbStage that generates verb lines from ctx. Fall through to SPI execution. Only ~20 lines of code. No spec change needed — BBC §6 already says "The GUI emits BIM COBOL verbs" which implies programmatic generation is valid.

**Key constraint:** The verb breakdown from CompileStage (placeCount, clusterCount, etc.) is lost. To enable (B), either persist the breakdown to CompilationContext or derive the default recipe from `c_orderline` discipline list (simpler, already available).

---

## Findings — Task 2: P107 Verification (Terminal RosettaStone)

### RouteStage fires: YES ✓

```
STEP 4: DISCIPLINE ROUTE — starting
Callout: 0 discipline OrderLines inserted (category=CO, whitelist=null)
Disciplines for routing: [ACMV, CW, ELEC, FP, LPG, SP]
RouteDocEvent: fireAll for 6 disciplines
...
RouteDocEvent: fireAll done — 6 routes, 591 total segments, 126 total fittings
RouteExecutorImpl: 6 routes, 711 edges, 717 nodes
RouteStage done: 6 routes, 591 segments, 711 edges
Stage 4 (DISCIPLINE ROUTE) completed in 69ms
```

RouteStage fires as Step 4, processes all 6 disciplines (ACMV, CW, ELEC, FP, LPG, SP). 591 segments, 126 fittings, 711 edges, 717 nodes computed. SPI discovery works — `RouteExecutorImpl` found via ServiceLoader.

### system_edges > 0: COMPUTED but NOT PERSISTED ✗

**711 edges computed, 0 written to output.db.**

Root cause: **schema conflict** between two DDL definitions:

1. `BuildingWriter.java:259` creates `system_edges` during WriteStage (Step 5) with columns: `(edge_id, system_id, from_node_id, to_node_id, edge_type, properties_json)` — the old MEPWriter schema.

2. `CompilationPipeline.java:1015` attempts `CREATE TABLE IF NOT EXISTS system_edges` with columns: `(discipline, from_index, to_index, from_xyz, to_xyz, edge_type)` — the new RouteStage schema.

Since BuildingWriter runs BEFORE the edge persistence code, the table already exists with the old schema. `CREATE TABLE IF NOT EXISTS` is a no-op. The subsequent INSERT fails:

```
[WARN] WRITE  system_edges/system_nodes INSERT failed: [SQLITE_ERROR] SQL error or missing database (table system_edges has no column named discipline)
```

**Fix needed (not applied per instructions):** Either (a) BuildingWriter should use the new schema, or (b) the persistence code should `DROP TABLE IF EXISTS` before `CREATE TABLE`, or (c) BuildingWriter should not create `system_edges` at all (let the RouteStage persistence code own it).

### P17 fires: NO ✗

P15, P16, P17 (`SystemConnectedProof`) do NOT appear anywhere in the FINE log. ProveStage runs only the standard BOM proofs (P-PARENT, P-SIBLING, P-QTY, P-TACK). No `SystemConnected` or `P17` mention.

Likely cause: ProveStage's EYES proof gate checks `hasRelationalData OR hasSystemEdges()`. Since system_edges has 0 rows (INSERT failed — see above), `hasSystemEdges()` returns false. And `hasRelationalData` is false for TE (no `ad_room_boundary` rows). So the EYES proofs (P15/P16/P17) are gated out.

**Chain:** BuildingWriter old schema → edge INSERT fails → 0 rows → hasSystemEdges=false → P17 skipped.

### TE gate: 6/7+WARN (C9 pre-existing) ✓

No regression. C9 reports 60 axis mismatches (rank-match artifact from library mesh orientation — same as baseline). All other gates PASS.

### Surprise: Callout inserted 0 discipline OrderLines

```
Callout: 0 discipline OrderLines inserted (category=CO, whitelist=null)
```

Yet `Disciplines for routing: [ACMV, CW, ELEC, FP, LPG, SP]` — 6 disciplines found. This means the discipline OrderLines already existed from a prior run (P104/P105). The callout is idempotent — it skips if lines exist. `whitelist=null` means no `mep_disciplines` in `ad_sysconfig` for TE — the callout used the existing data. Not a bug, but worth noting: TE lacks the `mep_disciplines` sysconfig entry that SH has.

### Summary Table

| Check | Expected | Actual | Status |
|-------|----------|--------|--------|
| RouteStage fires | Step 4 with discipline list | Step 4, 6 disciplines, 69ms | ✓ PASS |
| system_edges > 0 | edges > 0 | 711 computed, 0 persisted (schema conflict) | ✗ FAIL |
| P17 fires | fires (not SKIP) | skipped (gated by system_edges=0) | ✗ FAIL (blocked by above) |
| TE gate | 6/7+WARN (C9) | 6/7+WARN (C9) | ✓ PASS (no regression) |
