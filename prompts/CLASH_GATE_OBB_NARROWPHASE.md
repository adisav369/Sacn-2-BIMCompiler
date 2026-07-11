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
