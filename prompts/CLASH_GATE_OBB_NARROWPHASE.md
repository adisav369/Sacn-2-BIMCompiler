<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# CLASH GATE — OBB narrow-phase upgrade (TOUGH, Fable) — 2026-07-11, strategy session

```
# ⚠ DO NOT REMOVE
SCOPE: bim-ootb `modeller/sdg_gate.js` — the RED/ORANGE conformity gate. Currently AABB-only
(axis-aligned bounding box) for clash/clearance. This is a REAL, named weakness (MANAGER strategy
review, 2026-07-11): AABB over-flags (two boxes overlap, real rotated shapes don't) and under-flags
on rotated elements (AABB is a loose bound on anything not axis-aligned). Read the log after every
run. PUSH PAUSE IN EFFECT (`CLAUDE.md` §⏸ PUSH PAUSE) — commit locally, verify on localhost, do
NOT push, do NOT open a PR, until told otherwise.
```

## Why this is the tough one (read before starting)
This is deliberately the harder of two parallel tasks today — a real computational-geometry
implementation, not a template port. It's also BOUNDED: Separating Axis Theorem (SAT) for
oriented-box pairs is a well-documented, precisely specifiable algorithm — the difficulty is
correctness and performance at scale (Terminal: ~48K elements), not open-ended judgment. You have
everything you need in this doc to do it right; don't improvise the math, follow §2 exactly.

## §1 — Current state (read the real code first)
`modeller/sdg_gate.js`:
- `overlaps(a, b)` — per-axis AABB interval overlap, layout `[minx,maxx,miny,maxy,minz,maxz]`.
- `penetration(a, b)` — min separating translation if all 3 axes overlap, else 0.
- `faceGap(a, b)` — signed gap when separated on exactly one axis.
- `evaluate(before, after, moved, rel, opts)` — the gate entry point; for each moved element vs
  every other, calls `penetration`/`faceGap` on AABBs to flag `clash`/`clearance`/`door-out`/
  `door-crush`/`abuts-realign`.
`element_transforms` (both bim-compiler and bim-ootb DBs) already has everything needed for a real
OBB: `center_x/y/z`, `rotation_x/y/z`, `bbox_x/y/z` (half-extents or full extents — CHECK which,
don't assume; verify against a known element's actual measured geometry before trusting the sign/
scale convention).

## §2 — What to build: two-phase broad/narrow collision (standard architecture, don't reinvent)
**Phase 1 (broad, KEEP AS-IS):** the existing AABB `overlaps()`/`penetration()` stays exactly as
the cheap first filter — do not remove or slow down the common (no-candidate-overlap) case.
**Phase 2 (narrow, NEW):** only for AABB-overlapping pairs, run a real OBB-OBB Separating Axis Test:
1. Build each element's OBB: center (`center_x/y/z`), half-extents from `bbox_x/y/z` (verify
   full-vs-half first, §1), rotation matrix from `rotation_x/y/z` (verify Euler order/units —
   radians vs degrees, same landmine already found once in this project's `dw-rot-units` work,
   don't repeat it blind — check a known-rotated element's actual placement to confirm).
2. SAT for OBB pairs: test the 15 candidate separating axes (3 from box A's local axes, 3 from box
   B's, 9 cross products of A's axes × B's axes). For each axis, project both boxes' half-extents
   and the center-to-center vector onto it; if the projected intervals don't overlap on ANY axis,
   the boxes are disjoint (real, non-clashing) — even if their AABBs overlapped. If they overlap on
   ALL 15 axes, compute the minimum-overlap axis as the real penetration depth/direction (this
   REPLACES the AABB `penetration()` value for that pair, more precisely).
3. **Numerical stability**: near-parallel axes produce near-zero cross products — guard with an
   epsilon (a standard, well-known SAT pitfall, not something to discover the hard way; cite your
   epsilon choice and why).
4. Feed the corrected penetration/gap value into the EXISTING `evaluate()` RED/ORANGE logic — don't
   rewrite the RED/ORANGE decision rules themselves, only the geometry test underneath them.

## §3 — Witness (real proof, not eyeballed)
1. **False-positive fix, proven on real data**: find (or construct from real element geometry) a
   pair of ROTATED elements whose AABBs overlap but whose real OBBs do NOT (a classic SAT teaching
   case — two boxes near-diagonal to each other). Show the OLD code flags it (or a synthetic
   equivalent using real bim-ootb element dimensions/rotations), the NEW code correctly clears it.
2. **No regression**: rerun `witness_sdg_gate.js` (existing) — must stay 11/11 (the axis-aligned
   cases, which are the majority, must produce IDENTICAL results before/after, since AABB and OBB
   agree exactly when rotation=0).
3. **Performance**: measure narrow-phase cost on a real AABB-overlap-heavy building (Terminal or
   Hospital — check `W-DW-CLASH-TE`'s existing clash-candidate counts as your starting point,
   170→3 candidates per the existing witness) — report actual timing, don't assume it's fine.
4. Save the log, read it yourself before claiming pass.

## §4 — Non-goals
- Do not touch the RED/ORANGE decision thresholds (`CLASH_TOL`, `CLEARANCE`) — only the geometry
  test feeding them.
- Do not build a general mesh-boolean (actual triangle-level intersection) — OBB-OBB SAT is the
  right fidelity/cost tradeoff here, a full mesh boolean is a much bigger, separate undertaking.
- Do not touch `door-out`/`abuts-realign`'s existing AABB-based logic beyond feeding it the
  corrected penetration value — their surrounding logic (host-fit checks etc.) stays as-is.

## DONE WHEN
OBB-SAT narrow-phase exists, proven to correct a real AABB false-positive case, zero regression on
existing witness, performance measured and reported honestly (even if it's a real cost, say so —
don't hide a slowdown to claim a clean win).

---

## ✅ RESULT — 2026-07-11 (Fable session): built, witnessed, committed LOCALLY (PUSH PAUSE honoured)
**Branch `feat/clash-obb-narrowphase` @ `1f54cd1`, worktree `/tmp/wt-clash-obb` — NOT pushed, no PR
(per §⏸ PUSH PAUSE). Files: `modeller/sdg_gate.js` (§OBB SAT), `modeller/modeller.html` (`_gateObb()`
live-op-log wiring, all 3 `evaluate()` call sites), new `modeller/tests/witness_sdg_gate_obb.js` +
`witness_sdg_gate_obb_smoke.js`.**

### §1-landmines — VERIFIED against real data before any math (both were live):
- **`bbox_x/y/z` = WORLD-AABB FULL extent, NOT local dims and NOT half-extents.** Proof: the −90° wall
  (`…VyWx4`) stores (0.29, 5.8) vs its base_geometries local mesh (5.80, 0.29) — swapped; the 37°
  furniture (`…VyY1R`, rotZ=0.6458 rad) stores exactly `|L·cosθ|+|W·sinθ|` = (1.477, 1.449) vs local
  (1.116, 0.973) — matches to 3 decimals (witness W4 locks this at <0.01mm). ⇒ OBB local half-extents
  must come from the element's own local mesh box, never `bbox/2` on a rotated element.
- **Rotation = RADIANS** (values are exact ±π/2, π); `obbAxes()` mirrors `bonsai_library.js place()`'s
  two production branches exactly: yaw-only ⇒ Rz(rz); rotX/rotY ⇒ Rx(rx)·Ry(rz)·Rz(−ry) (THREE
  `Euler(rotX, rotZRad, −rotY)` order 'XYZ' — viewer parity, including that branch seam).
- **NEW recon fact (matters for wiring):** the resident `*_ARC.db` flow PRE-BAKES yaw into
  world-oriented real meshes (op `placement.rot=0`) — those elements ARE axis-aligned boxes to the
  op-log and are honestly ABSENT from the OBB map (AABB already exact; nothing invented). The narrow
  phase's live customers: gizmo `GEOM_ROTATE`d elements, the §ARC-3AXIS tilted ones (SH_ARC 3 /
  Hospital 422 / Terminal 325), catalog drops with yaw, and `*_extracted.db` local-file opens (real
  non-90° yaw params). Grid-stretched fids are omitted (world-axis stretch ≠ local-axis op → AABB
  fallback); `GEOM_SCALE` multiplies local h (fold scales local geometry).
- Epsilon: cross axes skipped when |Ai×Bj| < 1e-6 (sin of edge angle ≈ 6e-5°) — Ericson, Real-Time
  Collision Detection §4.4.1; conservative direction (can only keep an AABB-era flag, never fabricate
  a separation).

### §3-witness numbers (logs read, all saved to session scratchpad):
1. **False positive, real data (W-SDG-OBB 11/11 PASS):** W2 — the two real non-90° SampleHouse
   elements (±37°/−35° furniture, measured local h + measured rotations): AABBs interpenetrate
   **0.0428m** (> 2×CLASH_TOL, old gate = RED clash) while real OBBs are disjoint → new gate CLEAR.
   Hand-derived W1/W7 known answers exact (45° pair: AABB 0.2142 vs SAT 0; penetrating pair: AABB
   claims 0.914m, true MTV depth 0.2929m). W5 proves the 9 cross axes are load-bearing (pair
   separable ONLY edge×edge). **Browser, real user path (§OBB-SMOKE 5/5):** gizmo-rotate 45° →
   commitMove → old path phantom RED **0.0224m**, live obb-fed gate clean.
2. **Zero regression:** `witness_sdg_gate.js` **11/11 PASS** (no `opts.obb` ⇒ byte-identical path);
   pre-existing browser `witness_sdg_gate_smoke.js` **6/6 PASS** with the wiring live.
3. **Performance (measured on real DBs, not assumed):** SAT **535 ns/pair** (Terminal) /
   **401 ns/pair** (Hospital). Full census: ALL 41,226 Terminal AABB-overlapping pairs = **22.1ms**;
   Hospital 47,679 pairs = 19.1ms. Realistic gate workload (rotated-side pairs only: 419 TE / 1,494
   HO) = **0.25ms / 0.54ms per full sweep**. Honest cost statement: SAT is ~5× the AABB test per pair
   (4.3ms baseline for the same 41K pairs) — but it only ever runs on broad-phase survivors with a
   rotated side, so a real gate call (~170 candidates, W-DW-CLASH-TE scale) adds well under 0.1ms.
4. `_gateObb()` map build is cached by op-log length (§GATE-OBB log line: `entries=N builtAtOps=M`).

### Follow-ups (named, not hidden):
- `faceGap`/clearance (ORANGE) stays AABB-based per §4 non-goals — a rotated pair's gap remains the
  conservative AABB approximation (stated in the code header).
- Pre-baked-yaw resident elements can't be OBB-refined from the op-log (orientation lost at ARC-db
  bake time); recovering it would need the source `element_transforms` threaded through Open — a
  separate, deliberate substrate decision, not done here.
- PUSH PENDING: when PUSH PAUSE lifts, push `feat/clash-obb-narrowphase` from `/tmp/wt-clash-obb`
  (plain js/html, no LFS content) and open the PR.
