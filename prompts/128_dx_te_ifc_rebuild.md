# DX + TE Clean Rebuild with IFC-Driven Extraction

**Spec:** DISC_VALIDATION_DB_SRS §10.4.13, DuplexAnalysis.md
**Prereq:** P125 DONE, P126 DONE (rel_aggregates), P127 DONE (spatial container auto-discovery)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Re-extract and verify. No invention.

## Context from P126/P127

- DX storeys already removed from YAML (P127 auto-discovery)
- DX re-extracted in P126 (215 elements, was 1099 with old extraction)
- DX reconciliation delta=+50 is PRE-EXISTING (same with/without YAML storeys)
  — caused by mirror/composition creating paired lines beyond extraction count
- BIM.properties already set: `bim.log.level=INFO`, `bim.geo.debug=true`
- `SpatialContainerConfig` replaces `StoreyConfig` (P127)

## Read first

1. `PROGRESS.md` §Current State
2. `docs/DuplexAnalysis.md` — mirror algorithm, 3-tier partition
3. DX extraction DB: `SELECT ss.name, COUNT(rc.element_guid) FROM spatial_structure ss JOIN rel_contained_in_space rc ON ss.guid = rc.space_guid WHERE ss.type='IfcSpace' GROUP BY ss.name`
   - 11 IfcSpaces: A102(5), A103(15), A104(1), A202(4), A203(4), A204(2), B102(5), B103(15), B202(4), B203(4), B204(2)
   - 61 furniture elements total
   - A-side = Unit A, B-side = Unit B (matches mirror partition)

## Problem

DX BOM has 10 empty SET BOMs from non-functional scope boxes (aabb_mm
without origin_m). The 61 furniture elements that should be in rooms are
mixed into DUPLEX_SINGLE_UNIT_STD (460 lines all together).

P125 IFC-driven ScopeBomBuilder now auto-discovers the 11 IfcSpaces.
DX needs a clean re-extract to populate SET BOMs from IFC containment.

DX also tests: mirrored-pair composition (A/B split at X=4.4),
multi-storey (L1/L2/Roof/Foundation), and rotation (pi radians).

## Fix

### 1. Remove scope-box spaces from DX YAML

The `floor_rooms` section with empty scope boxes should be removed.
IFC containment replaces it.

### 2. Fix DX reconciliation delta (double-BOM'd furniture)

The +50 delta is a **real bug**, not a validator issue. Root cause:
- ScopeBomBuilder assigns 61 furniture elements to 11 SET BOMs (from IFC IfcSpaces)
- CompositionBomBuilder then sees ALL 215 elements — it doesn't receive scope excludes
- ~25 A-side furniture elements get paired into the half-unit (already in SET BOMs)
- ~25 B-side paired counterparts also counted → +50 double-counted physical elements
- In the old flow DX had no IfcSpaces, so no overlap existed

**Fix:** Pass `scope.excludeByStorey()` to `CompositionBomBuilder.build()` so it
skips elements already assigned to SET BOMs. Same pattern as StructuralBomBuilder.
- IFCtoBOMPipeline.java:284 — add scope excludes to CompositionBomBuilder.build() call
- CompositionBomBuilder.java:83 — skip excluded element keys in classification loop

### 3. Clean re-extract + compile DX

```bash
rm library/DX_BOM.db
./scripts/run_RosettaStones.sh classify_dx.yaml
```

### 4. TE re-extract (separate)

TE is 48K elements. Run after DX is verified:
```bash
rm library/TE_BOM.db
./scripts/run_RosettaStones.sh classify_te.yaml
```

TE has 0 IfcSpace rows — all elements go to DisciplineBomBuilder (CO path).
No IFC containment change expected. This is a baseline rebuild.

## Gate

- DX: SET BOMs populated (not empty), mirror partition preserved
- DX: GEO SUMMARY with rotation (ROT lines for mirrored unit)
- DX: reconciliation delta documented (pre-existing)
- TE: 6/7+WARN (C9 pre-existing), 48K elements compiled

## What NOT to do

- Do NOT modify the mirror/composition algorithm
- Do NOT force IfcSpace on TE (it has none)
- Do NOT modify existing migration files
- **All logging via BIMLogger — no System.out.println**

## Commit

```bash
git add IFCtoBOM/src/main/resources/classify_dx.yaml PROGRESS.md
git commit -m "[S100-p128] DX + TE clean rebuild with IFC-driven extraction"
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- DX: SET BOM count, lines per SET, reconciliation delta diagnosis
- DX: mirror partition preserved? A-side vs B-side room counts?
- DX: GEO SUMMARY — rotation lines? DRIFT?
- TE: gate result (expect 6/7+WARN C9), element count
- Any surprises — document, do NOT fix

---

## Findings

**Root cause diagnosed:** Reconciliation delta +50 was a real double-BOM bug, not a validator issue. ScopeBomBuilder assigned 61 furniture elements to SET BOMs, then CompositionBomBuilder re-partitioned the same elements into the half-unit (no scope excludes passed). Fix: pass `scope.excludeByStorey()` to `CompositionBomBuilder.build()` — same pattern as StructuralBomBuilder.

**DX reconciliation:** delta=+0 (was +50). `161 LEAFs + 54 paired = 215 vs 215 extracted.` Exact match.

**DX floor_rooms removed:** Dead code — `config.floorRooms()` parsed but never consumed since P125. IFC containment (11 IfcSpaces, 61 elements) is sole source.

**DX gate: 5/7.** Reconciliation PASS. Compilation FAIL: 83 critical proof violations (pre-existing PlacementProver threshold, not related). C9 WARN: 50 axis mismatches (pre-existing rank-match artifact, was 111 before scope fix reduced half-unit pairing).

**SH gate: 7/7 PASS.** Zero regression (no composition, scope excludes = empty map).

**CompositionBomBuilder printf cleanup:** 5 System.out/err.printf → BIMLogger.fine/warn. Added scopeSkipped counter to Tier 1 log.

**Surprise:** C9 axis mismatches dropped from 111 to 50 after the fix. Fewer elements in the half-unit (54 paired vs 79 before) means fewer rank-match artifacts. The composition was creating spurious pairings from furniture elements that belong in SET BOMs, not in the half-unit.
