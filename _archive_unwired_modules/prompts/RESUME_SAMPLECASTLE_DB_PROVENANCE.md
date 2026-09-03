<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# ✅ CLOSED 2026-07-02 — superseded by `prompts/RESUME_MODELLER_LOD400_REAL_GEOMETRY.md`

**This file's question is resolved — do not re-open or re-derive.** The "which SampleCastle DB version is
more correct" question below turned out to be the wrong question: neither version's *data* was the real
problem. The actual finding (2026-07-02): the Modeller was rendering 100% fake 12-triangle box proxies for
EVERY element regardless of which DB version was loaded — a rendering bug, not a data-provenance bug. Once
real per-element geometry rendering was built and verified (see the new resume file), bim-compiler's
verbatim `deploy/buildings/SampleCastle_extracted.db` was confirmed as the correct one-source-of-truth
(matches the settled `feedback_modeller_gh_vs_viewer_oci_data.md` doctrine) and is now what the
SampleCastle-ARC diagnostic resident + the merged rotation fix (bim-ootb PR #595) both use. The deep-link/
orange-highlight visual-proof ask below was superseded by something more direct: real geometry rendering
itself, so nothing needs a special-case highlight to look "real" anymore.

**Continue this work at `prompts/RESUME_MODELLER_LOD400_REAL_GEOMETRY.md`.** Everything below this line is
kept only as the historical record of how the investigation got here — do not act on it directly.

---

## Settled facts (verified, do not re-derive)
- **bim-ootb is the strategic direction going forward.** OCI stays only as the Viewer's current (legacy) deploy
  transport — its long URLs are undesired long-term. Do not fetch from or compare against OCI for Modeller work.
- **Compare DB content (row counts via SQL), never raw file checksums** — SQLite internal page layout can differ
  (VACUUM, insert order) while the data is identical. A checksum mismatch does NOT mean the data differs.
- **ARC-seed 3-axis rotation fix is DONE and correct** (bim-ootb `fix/arc-rotation-full-axes` @ `b06e64b`,
  worktree `/tmp/wt-arc-rot-fix`, committed not pushed). Reuses `window.THREE.Euler`/`Quaternion` (same vendored
  build the Viewer loads) to rotate tilted ARC elements about their own centre, matching `viewer/streaming.js`'s
  `compose(pos,quat,scale)` exactly. Untilted elements are byte-identical to before. Do not touch this code again
  unless a NEW regression is found — it is verified against real ground-truth IFC data (see below).
- **Outliner Components-category paint stall is DONE** (bim-ootb `fix/modeller-outliner-components-stall` @
  `4907a81`, worktree `/tmp/wt-outliner-stall`, committed not pushed). Memoized `_geomOps()` in `bonsai_oplog.js`,
  invalidated at the actual DB-write sites (`commit()`, `commitSeedGroup()`, `_setUndone()`) — NOT in `_emit()`
  alone, which is too late for `commitSeedGroup`'s own `_foldUpto()` call and produces empty folds (caught by the
  full E2E witness suite, 12/12 green after the fix). Original regression (176ms→41,576ms) reproduced flat at
  ~200ms max after the fix, real click-path, real ELEC walk on SampleCastle.

## ⛔ OPEN — SampleCastle's OWN Modeller DB copy: which version is more correct?
Two versions of `modeller/SampleCastle_extracted.db` exist in bim-ootb's history:
1. **PR #543's "enhanced extractor" version** (bim-ootb commit `1e8658b`) — 3317 ARC rows, 497 with real
   `rotation_x`/`rotation_y` tilt data, added room/storey semantics.
2. **bim-compiler's `deploy/buildings/SampleCastle_extracted.db`** — flatter, 3225 ARC rows (of 3621 total),
   ZERO tilted elements anywhere.

**Ground-truth check (done, verified, do not repeat):** read the ORIGINAL source IFC directly via `ifcopenshell`
(`internal/sources/Ifc2x3_SampleCastle.ifc`, guid `2pFYENFv91ygvyAeZOYi93`, an `IfcWindow`). Its real world
rotation matrix is `[[0,-1,0],[0,0,1],[-1,0,0]]` — a genuine 90° compound rotation, NOT upright. Version #1
(PR #543) captured this correctly. Version #2 (bim-compiler) silently reports it as upright (`rotation_x=
rotation_y=0`). **On this one data point, version #1 (bim-ootb's own extraction) is MORE accurate than
bim-compiler's.** Also checked: the "duplicate-coordinate cluster" in version #1 that looked like fabrication
(a slab+windows+railings+coverings all at the exact same point) is REAL IFC structure (verified against the same
source file) — multiple elements sharing one placement node is a valid, common IFC pattern, not corruption.

**A commit already exists that swapped the Modeller from version #1 to version #2** (`901bb08` on
`fix/arc-rotation-full-axes`, "one source of truth, no independent re-extraction") — made BEFORE the ground-truth
check above ran. Given bim-ootb is the strategic direction, this swap may have been the WRONG direction (it
regressed the Modeller from more-accurate data to less-accurate data). **Do not revert or re-decide this without
the visual proof the user asked for below — that is the actual next step, not more DB archaeology.**

## §RESUME — do this first, before any DB decision
The user wants to SEE this, not read another analysis. Two concrete asks:

1. **A deep-link URL that flies the Viewer's camera straight to the tilted window**, the same way clash-pair
   zoom already works. Pattern exists: `viewer/main.js:840` documents `#clash=guidA~guidB&cam=x,y,z&tgt=tx,ty,tz&
   tol=mm` (a URL hash the Viewer reads on load — see `viewer/streaming.js:1755` for where it's consumed). Build
   the analogous single-element version, e.g. `#zoom=<guid>&cam=...&tgt=...`, targeting guid
   `2pFYENFv91ygvyAeZOYi93` (world centre `5.295, 19.067, 4.375` — from `element_transforms`, meters). Reuse the
   existing camera-fly code the clash deep-link already uses — don't write new fly logic.
2. **An orange highlight outline over the WHOLE SampleCastle model** showing every element with non-zero
   `rotation_x`/`rotation_y` (the 497-row set from PR #543's version), so the user can see with their own eyes
   whether this is an isolated single window or a widespread pattern, before deciding which DB version is right.
   This directly answers "is it an isolated case" for both the SampleCastle rotation question AND generalizes to
   checking the SH/Duplex MEP-row-count question if needed later.

**Only after the user has seen this visually** — decide: keep `901bb08`'s swap (bim-compiler's flatter data), or
revert to PR #543's richer version (497 real tilts, verified more accurate), or some third option the visual
check surfaces. Do not decide from data analysis alone again on this question.

## Not yet investigated (explicitly deferred, not forgotten)
- SampleHouse: bim-ootb Modeller copy (39 ARC/26 STR) vs bim-compiler (40 ARC/20 STR) — small drift, not looked
  at closely. Low priority per user ("leave alone" was the leaning, pending the SC visual check first).
- Duplex: bim-ootb Modeller copy is ARC+STR only (253/12, no MEP) vs bim-compiler's raw extraction (199 ARC/904
  MEP/19 STR). This shape difference is likely BY DESIGN — this repo's own BOM doctrine keeps MEP out of the
  ARC/STR substrate (walked in live by the disc-walker, not baked into the building extraction) — but this was
  not verified against ground truth the way SampleCastle was. Don't assume; check if it becomes relevant.

## Non-invent / process notes for whoever picks this up
- Don't re-run the full DB archaeology (checksums across 7+ candidate files, OCI fetch, etc.) — it's done, the
  facts above are the distilled result. Re-deriving it burns a session for no new information.
- The rotation-fix code and Outliner-stall fix are DONE, verified, and independent of this open DB question —
  don't touch them while resolving this.
- Branches are LOCAL, NOT PUSHED: `fix/arc-rotation-full-axes` (worktree `/tmp/wt-arc-rot-fix`) and
  `fix/modeller-outliner-components-stall` (worktree `/tmp/wt-outliner-stall`). Push + open PRs once the DB
  question above is settled — don't ship the rotation fix silently bundled with an unresolved data regression.
