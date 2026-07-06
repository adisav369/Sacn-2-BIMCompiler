# DONE
# T0.4 — FOLLOW Verb POC

**Spec:** DISC_VALIDATION_DB_SRS §10.4.10 (movement verbs), §10.4.11 Task T0.4, BBC §3.6
**Prereq:** T0.1–T0.3 DONE. P94 BomWriter DONE. ForgeVerb + PipeBendForge exist (S99).

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** FOLLOW traces along a surface and produces
PipeSegment × N where N = ceil(length / stock_size). No fittings — straight run.
Qty computation from BOM recipe. No invention.

## Read first

1. `PROGRESS.md` §Current State
2. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.10 (movement verb catalogue — FOLLOW row)
3. `docs/BOMBasedCompilation.md` §3.6.3 FP trace (riser → header → branches → heads)
4. `BIM_COBOL/src/main/java/com/bim/cobol/verb/RouteSprinklersVerb.java` — existing routing verb, uses PipeRouter + SprinklerGrid
5. `BIM_COBOL/src/main/java/com/bim/cobol/verb/ForgeVerb.java` — verb 76, forge dispatch
6. `BIM_COBOL/src/main/java/com/bim/cobol/forge/PipeBendForge.java` — PIPE_BEND geometry
7. `BIM_COBOL/src/main/java/com/bim/cobol/VerbRegistry.java` — current verb count
8. `DAGCompiler/src/main/java/com/bim/compiler/bom/BomWriter.java` — single write path (P94)

## What FOLLOW does (§10.4.10)

```
FOLLOW CEILING SPACING 4500 → PipeSegment × N
```

- Trace along a surface (ceiling, wall, beam)
- Produce N segments of stock pipe length
- N = ceil(run_length / stock_pipe_length)
- No joint fitting — straight run only
- Each segment = one BOM line with qty=1, product=PipeSegment

## Deliverable

New verb: `FollowVerb.java` in `BIM_COBOL/src/main/java/com/bim/cobol/verb/`

```
FOLLOW <surface_type> <room_ref> [STOCK_LENGTH <mm>] [DIAMETER <mm>]
```

**Inputs:**
- `surface_type`: CEILING, WALL, BEAM (determines which dimension = run length)
- `room_ref`: room locator from c_orderline (to get room dimensions)
- `STOCK_LENGTH`: pipe stock size in mm (default 6000mm — standard pipe length)
- `DIAMETER`: pipe diameter in mm (default 25mm)

**Logic:**
1. Query room dimensions from c_orderline (dx/dy/dz of the room container)
2. For CEILING: run_length = max(room_width, room_depth) — longest ceiling run
3. N = ceil(run_length_mm / stock_length_mm)
4. Produce N BOM lines: product=PipeSegment, qty=1 each, tacked along the surface

**Output:** `VerbResult` with segment count and total length.

**No DB writes** — same pattern as RouteSprinklersVerb (read-only computation,
BomWriter handles persistence).

## Register in VerbRegistry

Add FOLLOW to VerbRegistry. Update verb count in docs/BIM_COBOL.md if it changes.

## Witness

`W-FOLLOW-1`: FOLLOW ceiling in SH living room → produces PipeSegment × ceil(room_length / stock_pipe_length). Fitting count = 0 (straight run).

Test in `BIM_COBOL/src/test/java/com/bim/cobol/verb/` — new `FollowVerbTest.java`.

Verify:
- SH living room ~6m long, stock pipe 6000mm → 1 segment
- SH living room ~6m long, stock pipe 3000mm → 2 segments
- Zero fittings produced (FOLLOW = straight only)

## Gate

- `mvn compile -q` PASS
- W-FOLLOW-1 test PASS
- SH 7/7 (no regression)

## What NOT to do

- Do NOT implement BEND, BRANCH, REDUCE, PENETRATE — those are Phase 1 (T1.1–T1.4)
- Do NOT write to the database from the verb — BomWriter handles writes
- Do NOT modify RouteSprinklersVerb — FOLLOW is a separate verb
- Do NOT modify existing migration files
- Do NOT change the compilation pipeline flow

## Spec citations

- `// Implementing DISC_VALIDATION_DB_SRS §10.4.10 FOLLOW — Witness: W-FOLLOW-1`
- `// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T0.4`

## When Done

Prepend `# DONE` to this file's first line.

Append findings:
- Verb number (77th?)
- FollowVerb: inputs, outputs, segment count formula
- W-FOLLOW-1 test result
- SH 7/7 (no regression)
- VerbRegistry count update

---

## Findings

- **Verb number:** 77 (registered after ForgeVerb in VerbRegistry)
- **FollowVerb:** FOLLOW <surface_type> <room_ref> [STOCK_LENGTH <mm>] [DIAMETER <mm>]
  - Inputs: surface_type (CEILING/WALL/BEAM), room_ref (m_bom.Value), stock_length (default 6000mm), diameter (default 25mm)
  - Output: VerbResult<FollowPayload> with segmentCount, runLengthMm, totalLengthMm, fittingCount=0
  - Formula: N = ceil(run_length_mm / stock_length_mm); run_length = max(width, depth) for CEILING/WALL
  - Room dimensions sourced from m_bom AABB (aabb_width_mm, aabb_depth_mm, aabb_height_mm)
- **W-FOLLOW-1 test:** 8/8 PASS (FollowVerbTest.java, in-memory SQLite with seeded rooms)
  - 1a: 6000mm room, 6000mm stock → 1 segment
  - 1b: 6000mm room, 3000mm stock → 2 segments
  - 1c: SH living room (8868mm), 6000mm stock → 2 segments
  - 1d: SH living room (8868mm), 3000mm stock → 3 segments
  - 1e: unknown room → FAIL
  - 1f: zero-dimension room → FAIL
  - 1g: invalid surface type → FAIL
  - 1h: missing args → usage message
- **SH 7/7 PASS** (zero regression)
- **VerbRegistry count:** 77 (updated in VerbRegistry.java comment + docs/BIM_COBOL.md)
- **Commit:** [1bc9840a](https://github.com/red1oon/BIMCompiler/commit/1bc9840a)
