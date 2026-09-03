# RESUME — STRICT: modeller drop must reproduce the Java's EXACT spatial relationships (2026-06-22)

## ⚠⚠⚠ SESSION-2 FINDING (2026-06-22 PM) — THE WITNESS WAS SELF-REFERENTIAL; THE DROP DOES *NOT* REPRODUCE SH
The user pushed: "are you using the RosettaStone comparison method?" Answer was **NO** — and applying it broke the
green open.
- **`witness_drop_vs_java.js` S1 is a TAUTOLOGY.** It compares `dropLeaves` (host) to an `oracle()` *transcribed in
  the same file*, both built from the SAME `dagevu_catalog.json`. `0.0000mm` = catalog agreeing with itself. The
  `toFixed(4)→toFixed(7)` precision fix (shipped PR #487, sw v701) is real+harmless but proves NOTHING about fidelity.
- **Against the REAL Java GEO log** (`evidence/SH_GEO_proof_20260330.log` — GUID'd LBD, `geo_verify`-proven ==
  extraction to 0.002mm), the SH drop is **NOT congruent**. Correspondence-free, frame-free, rotation-free metric
  (sorted all-pairs LBD distance multiset): **off 1.12m over all 55**; per-class intra-shape: **WALL 7.44m**,
  MEMBER 1.55m, PLATE 1.23m, DOOR 0.91m, FURN 0.87m, SLAB 0.86m, WINDOW 0mm ✓. So the drop reproduces the *catalog*
  exactly; the catalog ≠ the real SampleHouse by ~1m (structure several m). My earlier "reproduces Java-attested
  spatial relationships exactly" was WRONG.
- **SMALL-BOM REPRO (user's tell):** `SH_DINING_SET` chairs all face one way, not inward to the table. Confirmed in
  `library/archive/BOM.db`: 6 chairs ringed around the table by dx/dy BUT every line `rotation_rule=0`,
  `anchor_face=BACK` → orientation neither stored nor derived-from-position. Minimal repro of the LBD/rotation gap.
- **ROOT-CAUSE HYPOTHESIS (not yet proven):** `library/SH_BOM.db` is **0 bytes (empty)** → the bake falls back to
  the generic `archive/BOM.db`, whose template sets ("dx=dy=0 → synthesised into a straight ROW", no rotation) are
  NOT the real per-building layout. DX has a real `DX_BOM.db` (282KB) → that's why DX "matched." NOT confirmed: (a)
  whether DX is actually congruent to ITS ground truth, (b) whether the gap is the empty BOM vs a bake layout-pass
  transform, (c) whether expandAssembly's world-axis +half (vs rotated-half) also contributes.

### PLAN (carry forward — strict, NON-INVENT)
1. **Flip S1 to the REAL oracle:** compare the drop to the GUID'd Java GEO log / `geo_verify` (or run
   `scripts/run_RosettaStones.sh classify_sh.yaml`). ⚠ NEVER hand-roll a reconstruct yardstick (2026-06-21 lesson).
   It WILL go RED for SH (~1m) — that is the honest, falsifiable state. Down-grade the shipped GREEN claim.
2. **Use the dining-set as the unit repro** (chairs-face-table) — fix rotation derivation/application there first,
   then re-verify SH at building scale.
3. **Decide the layout source:** re-bake SH from a REAL per-building BOM (populate `SH_BOM.db`, like `DX_BOM.db`) or
   confirm/derive chair rotation from position. Read the Java placer + the real BOM FIRST. [[feedback_read_java_spec_first]]
4. **Then verify DX the same way** to scope SH-specific vs general.

```
# ⚠⚠⚠ NEW SESSION STARTS HERE. This is a DEEP one — the user warned "from experience something WILL give way,
#   it needs truly deep tackling." Carry the STRICT whitebox conduct forward (the user: "be strict with the
#   whitebox; it must land EXACT spatial relationship/offsets"). NON-INVENT, §-log is the proof, NOT the eye.
#
# WHERE WE ARE (all LIVE on red1oon.github.io/bim-ootb/viewer/modeller.html):
#   • Basic BOM drop landing-off-cursor (up to 22m) — FIXED (PR #483 dropOrigin → superseded).
#   • Rotated drop scatter (45m on MIRROR buildings) — FIXED (PR #484 dropLeaves, sw v700, bonsai_library.js?v=13).
#     ROOT (from reading PlacementCollectorVisitor.java:347-374): the Java compiler has NO external drop yaw —
#     cumRot/cumMirror are the building's INTRINSIC transforms (MIRROR:X = rot=π). The drop yaw is an EXTERNAL
#     rigid rotation about the cursor; dropLeaves expands the CANONICAL building (rot=0 = proven path, expandAssembly
#     UNTOUCHED, W-MODELLER-DROP host==oracle 0.000mm) then rigid-rotates it. W-BOM-DROP-CENTER: 57 asm × 5 yaws
#     incl 2 mirror buildings = 0.07mm on the cursor.
#
# ⛔ THE STRICT GAPS THIS SESSION OWES (witness scripts/witness_drop_vs_java.js = W-DROP-VS-JAVA, currently RED):
#   Java attested (LAST_MILE_PROBLEM.md §7 / geo_verify.py): SH 58 elements, 1653 pairs, worst 0.002mm;
#                                                             DX 1099 elements (179 GUID-matched), 0.004mm.
#   Our drop currently:                                       SH 55 elements, 1485 pairs, worst 0.1mm.
#
#   GAP-1 PRECISION (0.1mm vs 0.002mm): expandAssembly + dropLeaves quantize positions with `.toFixed(4)` (= 0.1mm
#     on metres). Relative offsets are STRUCTURALLY exact (rigid-invariant, == the Java-faithful oracle to the
#     rounding floor) but 50× coarser than the Java bar. FIX = carry full float precision through the drop chain
#     (drop/raise the toFixed in bonsai_library.js expandAssembly leaf push + dropLeaves), then S1 < 0.01mm.
#     ⚠ expandAssembly is the proven path — verify W-MODELLER-DROP stays 0.000mm and W-GEO-SUMMARY stays green
#     after touching the rounding (it should: less rounding = more precise, not different).
#
#   GAP-2 ELEMENT COUNT (55 vs 58 for SH): BUILDING_SH_STD expands to 55 leaves; Java geo_verify attested 58.
#     DX matches exactly (1099 == 1099) — so this is SH-specific. This is where "something will give way":
#     reconcile the catalog bake (scripts/extract_dagevu_catalog.py) against the REAL extraction
#     (deploy/buildings/SampleHouse_extracted.db) BY IFC GUID — the geo_verify join (scripts/geo_verify.js is the
#     JS port). Find which 3 SH elements the bake dropped (or whether Java counted container/duplicate nodes).
#     NON-INVENT: never fabricate 3 elements — find them in the source or prove the 55 is the honest set and the
#     Java 58 counted something the BOM legitimately folds.
#
#   THE REAL STRICT BAR (do NOT settle for oracle-vs-host; that can agree while both drift from truth — the user's
#   lesson): prove the drop against the REAL building extraction by GUID (geo_verify all-pairs), the SAME ground
#   truth the Java RosettaStoneGateTest G1-G6 use. scripts/rosetta_canvas_sh.js already did SH 55/55 + DX
#   1085/1085 0.000mm vs raw-IFC extraction via the VIEWER path — bridge that proof to the DROP/dropLeaves path so
#   the modeller drop is held to the Java's micron bar, not just internal self-consistency.
#   ⚠ DO NOT hand-roll a reconstruct yardstick (2026-06-21 lesson: a self-invented corner/centre+bbox heuristic
#   gave a FALSE 0.70m drift on tested-perfect code). Use the Java Rosetta gate / geo_verify, never a guess.
#
# RUN: node scripts/witness_drop_vs_java.js   (RED now: S1 0.1mm, S2 55≠58 → drive both GREEN)
#      node scripts/witness_modeller_drop.js + witness_modeller_geo_summary.js + witness_bom_drop_center.js (keep green)
# DEPLOY: worktree off FRESH origin/main (editing ~/bim-ootb hook-BLOCKED) → PR → auto-merge → verify LIVE +
#   re-run the witness against the LIVE-fetched bonsai_library.js (the end-to-end proof).
```

## Method (carry the strict conduct)
1. Read the Java FIRST for any placement question (PlacementCollectorVisitor.java / geo_verify.py / RosettaStoneGateTest) — never reinvent. [[feedback_read_java_spec_first]]
2. Witness must be able to FAIL and the §-log must SHOW the failure before you believe a pass. [[feedback_stop_on_invent_not_instruct]]
3. Prove against the REAL extraction (geo_verify by GUID), not host-vs-own-oracle. [[feedback_rosetta_proof_real_building]] [[feedback_whitebox_deduce_not_browser]]
4. NON-INVENT — every element and offset traces to source. Find the missing 3, don't fabricate them.
