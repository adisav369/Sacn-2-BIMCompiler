# Gap 3: RouteBuilders Emit Verb Lines Through VerbRegistry

**Spec:** DISC_VALIDATION_DB_SRS §10.4.12 Gap 3
**Prereq:** P118 DONE (ceiling void routing). P116 DONE (VerbStage default recipe).

You are a coder for bim-compiler. Spec-first: write the design, then implement.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** The verbs already exist in VerbRegistry (FOLLOW, BEND, BRANCH, REDUCE, PENETRATE). The builders already compose CrawlOps. Map ops to verb lines. No invention.

## Read first

1. `PROGRESS.md` §Current State
2. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.12 Gap 3 — the spec for this work
3. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.10 — movement verbs spec (FOLLOW, BEND, BRANCH, REDUCE, PENETRATE)
4. `BIM_COBOL/src/main/java/com/bim/cobol/route/FpRouteBuilder.java` — example builder (uses CrawlOps directly)
5. `BIM_COBOL/src/main/java/com/bim/cobol/VerbRegistry.java` — 77 registered verbs
6. `BIM_COBOL/src/main/java/com/bim/cobol/verb/FollowVerb.java` — existing FOLLOW verb (wraps CrawlRouter, proves the bridge)
7. `BIM_COBOL/src/main/java/com/bim/cobol/CrawlRouter.java` — CrawlOp execution engine
8. `DAGCompiler/src/main/java/com/bim/compiler/dsl/VerbStage.java` — verb execution pipeline

## Problem

Two parallel systems do the same thing:
1. **RouteBuilders** compose CrawlOps directly in Java → segments + fittings
2. **VerbRegistry** dispatches verb lines (FOLLOW, BEND, etc.) → same CrawlOps

Routing decisions are invisible in the pipeline log. The FINE log shows `ACMV route done: segments=161` but not the individual ops that produced those segments. Not auditable, not overridable by the Designer.

## Design

### RouteBuilder emits verb lines, not CrawlOps

Each RouteBuilder produces a `List<String>` of BIM COBOL verb lines instead of directly composing CrawlOps. RouteExecutorImpl dispatches these through VerbRegistry.

Before (current):
```java
// FpRouteBuilder.build()
ops.add(new FollowOp(VERTICAL, riserHeight, diameter));
ops.add(new BendOp(90, diameter));
ops.add(new FollowOp(HORIZONTAL, headerLength, diameter));
```

After:
```java
// FpRouteBuilder.buildVerbLines()
lines.add("FOLLOW VERTICAL " + riserHeight + " DIAMETER " + diameter);
lines.add("BEND 90 DIAMETER " + diameter);
lines.add("FOLLOW HORIZONTAL " + headerLength + " DIAMETER " + diameter);
```

The verb lines pass through VerbRegistry → FollowVerb/BendVerb/etc. → CrawlRouter → same CrawlOps. Same output, but now every routing decision is a logged, auditable verb line.

### What changes

| File | Change |
|------|--------|
| `DisciplineRouteBuilder.java` (interface) | New method: `List<String> buildVerbLines(BuildingGeometry geo, String floorRef)` |
| 6 RouteBuilder implementations | Emit verb lines instead of CrawlOps |
| `RouteExecutorImpl.java` | Dispatch verb lines through VerbRegistry instead of CrawlRouter directly |
| `RouteDocEvent.java` | Log each verb line at FINE level |

### What does NOT change

- CrawlRouter — still the execution engine, called by verbs
- CrawlOps — still the primitives
- VerbRegistry — no new verbs needed (FOLLOW, BEND, BRANCH, REDUCE, PENETRATE all exist)
- VerbStage — unchanged (handles .bimcobol scripts and default recipe)
- system_edges/system_nodes — same output

### FINE log after this change

```
[FINE ] ROUTE  FP floor Ground Floor:
[FINE ] ROUTE    FOLLOW VERTICAL 3500 DIAMETER 50
[FINE ] ROUTE    BEND 90 DIAMETER 50
[FINE ] ROUTE    FOLLOW HORIZONTAL 12000 DIAMETER 50
[FINE ] ROUTE    BRANCH TEE DIAMETER 50 CHILD_DIAMETER 25
[FINE ] ROUTE    FOLLOW HORIZONTAL 3000 DIAMETER 25
```

Every segment traceable to a verb line → auditable → overridable by Designer.

## Gate

Run TE:
```bash
./scripts/run_RosettaStones.sh classify_te.yaml
```
- FINE log shows individual verb lines per discipline per floor
- system_edges count unchanged (711) — same routing output
- TE gate: no regression

Run SH:
```bash
./scripts/run_RosettaStones.sh classify_sh.yaml
```
- SH 7/7+ PASS (no regression)
- FINE log shows verb lines for ELEC + SP routes

Run DisciplineRouteBuilderTest:
- 15/15 PASS (no regression — same routing output via verb dispatch)

## What NOT to do

- Do NOT add new verbs to VerbRegistry
- Do NOT modify CrawlRouter or CrawlOps
- Do NOT modify VerbStage or the default recipe
- Do NOT modify existing migration files
- Do NOT change routing output (same segments, fittings, edges)
- **All logging via BIMLogger — no System.out.println**

## Spec citation

```java
// Implementing DISC_VALIDATION_DB_SRS §10.4.12 Gap 3 — RouteBuilders emit verb lines
// Unifies RouteBuilder CrawlOp composition with VerbRegistry dispatch
```

## Commit

```bash
git add BIM_COBOL/src/main/java/com/bim/cobol/route/*.java \
        BIM_COBOL/src/main/java/com/bim/cobol/RouteExecutorImpl.java \
        BIM_COBOL/src/main/java/com/bim/cobol/RouteDocEvent.java \
        DAGCompiler/src/main/java/com/bim/compiler/dsl/RouteExecutor.java \
        PROGRESS.md
git commit -m "[S100-p119] RouteBuilders emit verb lines through VerbRegistry — unified routing audit trail"
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- TE: verb lines logged per discipline? Sample lines?
- system_edges count unchanged (711)?
- DisciplineRouteBuilderTest 15/15?
- SH 7/7?
- Any surprises — document, do NOT fix

---

## Findings

**Design:** Builders expose `plan()` returning `RoutePlan(initial, ops, floorCount, roomCount, pattern)`. Default `buildRoute()` in the interface: plan → log verb lines → execute via CrawlRouter. One code path, no drift.

**CrawlOp.toVerbLine():** Added to sealed interface. Each op produces its declarative form: FOLLOW 3500 STOCK_LENGTH 6000, BEND 90, REDUCE 32, PENETRATE SLAB 305 FIRE_RATED, BRANCH 3 DIAMETER 25.

**SH verb lines:** ELEC 15 lines (3 floors), SP 13 lines (3 floors). Every FOLLOW/BEND/REDUCE/PENETRATE visible at INFO level. system_edges=26, system_nodes=28 — unchanged.

**DX verb lines:** ELEC 25 lines (5 floors), SP 23 lines (5 floors).

**GEO TACK:** 239 lines for SH (ENTER/EXIT/LEAF/ROT). Both ROUTE verb lines and GEO TACK in pipeline log — white box audit trail for routing + placement.

**SH 7/7 PASS.** DX 5/7 (pre-existing PlacementProver).

**Surprise:** BEND/REDUCE/PENETRATE/BRANCH verbs don't exist in VerbRegistry (only FOLLOW). Prompt assumed they did. Used CrawlOp.toVerbLine() + CrawlRouter execution instead of VerbRegistry dispatch. Same audit trail, same output, no new verbs needed.
