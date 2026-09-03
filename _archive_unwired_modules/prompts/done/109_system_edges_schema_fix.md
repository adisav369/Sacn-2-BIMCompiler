# DONE — [41da7f57](https://github.com/red1oon/BIMCompiler/commit/41da7f57)
# Fix system_edges Schema Conflict — Edge Persistence Unblocked

**Spec:** DISC_VALIDATION_DB_SRS §10.4.11 B2 (system_edges), B4 (BIMEyes gate)
**Prereq:** P108 DONE (`d1b60e20`). RouteStage computes 711 edges for TE but INSERT fails due to schema mismatch.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** The correct schema is in DISC_VALIDATION_DB_SRS §10.4.11 B2. Copy it. No invention.

## Read first

1. `PROGRESS.md` §Current State
2. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.11 B2 — system_edges/system_nodes spec
3. `prompts/108_verbstage_bom_driven.md` §Findings Task 2 — root cause diagnosis
4. `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java` line 1015 — new schema (correct, per spec)
5. `DAGCompiler/src/main/java/com/bim/compiler/bom/writer/BuildingWriter.java` line 259 — old schema (wrong, pre-RouteStage)

## Problem

Two DDL definitions for `system_edges`:

| Location | Schema | Origin |
|----------|--------|--------|
| `BuildingWriter.java:259` | `(edge_id, system_id, from_node_id, to_node_id, edge_type, properties_json)` | Old MEPWriter — pre-RouteStage |
| `CompilationPipeline.java:1015` | `(discipline, from_index, to_index, from_xyz, to_xyz, edge_type)` | New RouteStage — per DV SRS spec |

BuildingWriter runs first (Step 5), creates old schema. CompilationPipeline's `CREATE TABLE IF NOT EXISTS` is a no-op. INSERT fails:
```
[WARN] WRITE  system_edges/system_nodes INSERT failed: table system_edges has no column named discipline
```

Result: 711 edges computed, 0 persisted. P17 gated out.

## Fix

**Option (c) from P108 diagnosis:** BuildingWriter should NOT create `system_edges` or `system_nodes`. The RouteStage persistence code in CompilationPipeline owns these tables.

1. Remove the `system_edges` and `system_nodes` DDL from `BuildingWriter.java` (the old MEPWriter schema)
2. Verify the DDL in `CompilationPipeline.java` matches the DV SRS spec table exactly
3. Verify the INSERT code in CompilationPipeline matches the new schema columns

Do NOT change the RouteStage logic, RouteExecutor, or any other pipeline stages.

## Gate

Run:
```bash
./scripts/run_RosettaStones.sh classify_te.yaml
```

Verify from FINE log:
- `system_edges/system_nodes INSERT` succeeds (no WARN)
- `system_edges` count > 0 (expect ~711)
- P17 (SystemConnectedProof) fires (not SKIP)
- TE 6/7+WARN (C9 pre-existing, no regression)

Then run SH to confirm no regression:
```bash
./scripts/run_RosettaStones.sh classify_sh.yaml
```
- SH 7/7 PASS (SH has no routes — system_edges = 0 is expected, P17 should still be gated for SH)

## What NOT to do

- Do NOT modify CompilationPipeline.java RouteStage logic
- Do NOT modify RouteExecutor or RouteExecutorImpl
- Do NOT modify existing migration files
- Do NOT add new migration files (this is a code fix, not a schema migration)
- Do NOT chase issues outside scope

## Spec citation

```java
// Implementing DISC_VALIDATION_DB_SRS §10.4.11 B2 — system_edges schema owned by RouteStage
```

## Commit

```bash
git add DAGCompiler/src/main/java/com/bim/compiler/bom/writer/BuildingWriter.java \
        PROGRESS.md
git commit -m "[S100-p109] Remove old system_edges DDL from BuildingWriter — RouteStage owns edge schema"
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- system_edges row count for TE
- P17 status (fires / SKIP)
- SH 7/7 (no regression)
- Any surprises — document, do NOT fix

---

## Findings

- **system_edges**: 711 rows persisted (was 0). Schema: `(discipline, from_index, to_index, from_xyz, to_xyz, edge_type)` — matches DV SRS spec.
- **system_nodes**: 717 rows persisted (was 0). Schema matches spec.
- **No INSERT WARN** — schema conflict resolved. The old DDL in BuildingWriter.java (`edge_id/system_id/from_node_id/to_node_id`) removed. CompilationPipeline.persistRouteEdges now owns both tables.
- **SH 8/8 PASS** — zero regression.
- **TE 5/7 PASS, 1 FAIL** — regression from 6/7+WARN.
  - **Root cause:** With system_edges=711, `hasSystemEdges()` returns true at CompilationPipeline.java:1444. PlacementProver.proveFromDB() now fires for the first time on TE. It finds **51625 critical proof violations** — pre-existing placement issues invisible when prover was gated out (system_edges=0 → empty report at line 1459).
  - **This is P17 doing its job.** The violations were always there; edges persisting just made them visible.
  - **Fix needed (out of scope):** Triage the 51625 violations, or set a `criticalThreshold` for TE in BuildingRegistryTest. P110+ concern.
- **P17 (SystemConnectedProof):** fires (not SKIP) — prompt goal achieved.
- **C9 WARN:** 60 axis mismatches — unchanged, pre-existing.
- **Note:** Prompt referenced path `bom/writer/BuildingWriter.java` but actual path is `dsl/BuildingWriter.java`.
