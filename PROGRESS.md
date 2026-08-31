# PROGRESS — Current Development State

## Current State — 2026-09-01 (LTU + Clinic shipped to OCI; Clinic glass root-caused; one new TM bug open)
**Lane file for all of it: `prompts/LTU_TERMINAL_CLINIC_RENDER_CORRUPTION.md` — read §Z first
(resume handoff: live state, open items, next measurements).**

**Shipped and verified end-to-end from the live landing page, not just uploaded:**
- **LTU_AHouse** — the user's client-side 9-discipline import (122,330 elements) split and live on
  OCI, `positions.bin` added (was 404). §V / §V.4. Costs recorded honestly: bbox-first got faster
  (21.0→19.4 MB) but the mesh stream is 3.8× heavier (24.1→91.3 MB gz) because web-ifc bakes
  rotation into vertices, halving instancing (59,917→104,340 geometries).
- **Clinic** — the user's own `Downloads/Clinic.db`, **byte-identical, single file, no split, no
  patch, nothing transplanted** (16,071 elements; below `split_db.sh`'s own >20,000 threshold).
  Stale `Clinic_meta.db`/`Clinic_geo.db` DELETED — while they existed `streaming.js:2495` derived
  and HEAD-probed them and loaded the pair regardless of the landing link. Landing repointed,
  bim-ootb PR #1589. §Z.1.

**Four bim-ootb PRs merged this session:**
| PR | what |
|---|---|
| #1578 | merge→save→reopen lost the merged building — geo fold died on a table-name mismatch (`base_geometries` vs `component_geometries`); + unguarded `GROUP BY m.building` on a DB with no such column |
| #1585 | **Clinic glass**: transparent IFC materials were given their class's opaque METAL preset. `STD_MAT`/`TRIPLANAR_MAT` are keyed on `ifc_class` alone; §S265c's "trust IFC data" was only ever wired to COLOUR. 167 of 225 glass panels rendered as steel |
| #1589 | landing → single `Clinic.db` |
| (#1576 verified merged) | split meta/geo pair open fix from the prior session |

**Witnesses added, all measured red→green, never green-only:**
`W-MERGE-SAVE-ROUNDTRIP` 6/14→14/14 · `W-GLASS-NOT-METAL` 6/9→9/9 · `§LTU_LANDING` 7/7 ·
`§CLINIC_FINAL` 5/5 · `§CLINIC_GLASS` (X-ray mechanism, GH+OCI) PASS.

**⛔ OPEN — carried into the next session (all detailed in §Z):**
1. **§Z.3 NEW — Clinic TM ground-floor slab appears late, then persists on scrub-back.** Measured
   offline from the shipped DB (schedule is baked in): the 4 `Slab on Grade` elements are in
   `TASK_Substructure_TOF_Footing`, the FIRST task, data clean (0 orphans/nulls/unmapped). **The
   baked schedule contradicts the symptom** — two named, unverified hypotheses (runtime re-authoring
   through the broken band derivation; or the non-timeline ground plane from `_calcGroundY`) with the
   measurement that falsifies each. Read `4D_MODEL_INTEGRITY.md` §I ownership table first.
2. **§Z.2 — the OCI sandbox viewer is a far older build than GH Pages** (`effects.js` absent
   entirely, `scene.js` 7 KB vs 191 KB). It has no patch self-heal and cannot receive the glass fix.
   Decide whether it is still a supported front before spending a deploy on it.
3. **§T.4 — client-side import schema gaps** (`spatial_structure`, openings, material-layer names).
   Elements themselves are complete: web-ifc is a strict SUPERSET of the server extraction, 0 missed
   on both files tested (§T).

**Process lessons banked to memory this session** (three real corrections from the user):
`feedback_ship_the_file_as_given.md` (send the file AS GIVEN — no transplant, no split, no patch) ·
`feedback_verify_the_end_of_the_chain.md` (uploaded ≠ live, merged ≠ deployed, fixed-mechanism-A ≠
symptom-solved) · an addition to `feedback_measure_compute_matchback.md` (find an ORACLE INSIDE THE
DATA — every wrong turn this session was a self-chosen proxy).

## Older session log
Archived 2026-09-01 to **`prompts/archive/PROGRESS_sessions_archived_2026-09-01.md`** (690 lines,
2026-08-29 back to 2026-08-14 — nothing deleted, only moved) because this file had reached 717 lines
against its 80-line budget. Per-lane detail lives in the `prompts/*.md` file for each lane; that
archive is a historical trail, not a live worklist.

⚠ Two ⛔ items from the 2026-08-29 state that are still live and were NOT closed this session:
- **DAY-0 headline (`§W_D0`) is STALE** against `origin/main` — reproduces `PASS=4 FAIL=5` on a fresh
  cache, not the `14 PASS/0 FAIL` the old text claimed. Re-baseline before trusting it.
- **5D cost/rate policy still hardcoded in 11 places** (`4D_GANTT_TM_REFACTOR.md` §FUTURE-5A) —
  inventory written, not actioned. Sequencing/duration/crew policy belongs in JSON, not JS.
