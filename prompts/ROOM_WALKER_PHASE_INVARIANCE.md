<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# ROOM WALKER PHASE INVARIANCE — same walls in, same rooms out, any coordinate frame (2026-07-17)

```
# ⚠ DO NOT REMOVE
SCOPE: make the room compiler translation-invariant. The SAME building geometry, differing only
by a constant coordinate translation, currently compiles DIFFERENT room sets (proven A/B below).
Fix the mechanism in BOTH scripts/compile_rooms.py (canonical, bim-compiler) and its verbatim JS
port build/room_walker.js (bim-compiler), keeping their parity; refresh the deployed copy
bim-ootb viewer/lib/room_walker.js afterwards. Read the log after every run — exit code is not
evidence. Commit locally in each repo, verify by witnesses below, do NOT git push and do NOT
open a PR — report back with §-evidence; the manager session handles push/review.
Never commit a .db binary anywhere (unconditional rule). Every DONE claim needs a §-log line.
User intent (verbatim): "Data is data. Come on, i know about GIGO" + "the whole inject mechanism
is to cure it" → a cure that depends on which coordinate frame the same geometry arrives in is
the bug. User: "Go, fix in new session".
```

## §GIVEN — measured 2026-07-17 (FLY_TOUR_CORRIDOR_GRAPH.md §WALKER-PHASE-SENSITIVITY), do not re-derive
- **G1 — the A/B pair (both on this disk, read-only inputs):**
  - Extracted frame: `~/bim-ootb/buildings/Terminal_extracted.db` (strip its IfcSpace rows for
    the run — copy it to /tmp first, e.g. the same DELETE used for Terminal_norooms:
    `DELETE FROM spatial_structure WHERE type='IfcSpace'; DELETE FROM rel_contained_in_space;`).
  - Imported frame: `/tmp/walker_ab/TerminalMerged_imported_meta.db` (exported from the browser
    import of TerminalMerged.ifc, spatial tables already dropped; regenerable via the scratchpad
    diag_import.js harness if lost).
- **G2 — the data is equivalent**: doors 135 (63/29/27/9/7 per storey), walls 333, windows 236,
  columns 158, bbox stats identical to 2dp, same Unknown-storey rows, hallway backbone identical
  (buckets=52 joined=18 chains=10). Only difference: constant translation Δ≈(−545.6, −51.1,
  −14.7), no mirror, identical spans.
- **G3 — the divergence**: same `room_walker.js` → extracted frame **51 rooms / 79 rects → 26 E1
  door-edges (orphan 36)**; imported frame **45 rooms / 75 rects → 16 E1 edges (orphan 50)**.
  ~6 pockets fail to enclose only in the translated frame; their doors orphan; the occupant
  graph goes thin; the Fly tour rejects (§FLY_ROUTE_REJECT thin-path — user's live report).
- **G4 — algorithm home**: `scripts/compile_rooms.py` is the source of truth;
  `build/room_walker.js` is the verbatim port (dual-mode Node/browser; existing 6/6 parity
  witness convention — find and re-run it). bim-ootb `viewer/lib/room_walker.js` is a deployed
  copy (text file, committable there).
- **G5 — prime suspects (verify, don't assume)**: raster grid origin/extent derivation (does the
  grid snap to absolute coords or to data min? float `floor((x−x0)/RES)` rounding at cell
  boundaries), SEAL dilation interacting with grid phase, and any use of absolute coordinates in
  door-adjacency buffers. The walker runs headless in Node (`require` + sql.js) — iterate there,
  no browser needed.

## SPEC
- **S1 — mechanism first.** Instrument (or offline-dump) both compiles per storey: pocket count,
  each room's rect set, which §DOOR-RESCUE/§DOOR-PARTITION decisions differ. Align frames by the
  measured Δ and name the exact step where the same wall set rasterizes differently. No fix
  before the mechanism is named with numbers (§-log it).
- **S2 — the fix.** Make rasterization translation-invariant, derived from the mechanism found —
  e.g. quantize the grid origin to the data (origin = RES-multiple relative to the storey's own
  min bound, plus an epsilon treatment justified by S1), not a guessed offset. EXTRACT/COMPUTE
  only; no fitted constants beyond what S1 proves necessary. Apply identically to
  compile_rooms.py AND room_walker.js (parity is part of the task, not a follow-up).
- **S3 — witnesses (all must be in the DONE report with §-lines):**
  - **W-FRAME-EQ**: walker output on G1's two inputs is EQUAL after translation alignment —
    same room count, same rect count, rects matching within one grid cell (RES=0.20), and the
    downstream door-edge count equal (26 == 26, orphan 36 == 36). Node script, log the diff table.
  - **W-PY-JS-PARITY**: the existing compile_rooms.py↔room_walker.js parity witness re-run green
    on its standard corpus.
  - **W-NO-REGRESSION**: BEFORE touching anything, capture current walker output (room count /
    rect count / per-room rects hash) for Duplex_extracted, Clinic_extracted, HHS_Office_
    Federated_extracted (all in ~/bim-ootb/buildings/, read-only copies to /tmp). AFTER the fix,
    outputs unchanged OR every change is explained by the named mechanism and improves door
    binding (log both counts). No silent drift on healthy buildings.
  - **W-TOUR (integration, optional but preferred)**: with the fixed walker copied to a bim-ootb
    worktree's viewer/lib/, the scratchpad fly-witness on the imported DB produces
    `§FLY_ROUTE` accepted (no thin-path reject).
- **S4 — deliverable**: commits in a bim-compiler `/tmp/wt-*` worktree (compile_rooms.py +
  build/room_walker.js + this file's DONE section) and a bim-ootb `/tmp/wt-*` worktree
  (viewer/lib/room_walker.js refresh + cache-bust bump). NO push, NO PR. DONE appendix here with
  every witness's §-line.

## Constraints
- `git worktree list` FIRST in both repos; reuse a suitable existing worktree before creating
  one; never edit ~/bim-ootb directly (hook) and prefer a worktree for bim-compiler too (shared
  tree, concurrent sessions).
- LFS caution: don't checkout branches whose LFS blobs aren't local (modeller/mesh.db etc.);
  worktrees off current main are safe.
- Sacred: migration/*.sql untouched; no .db binaries in any commit.
```
