# ✅ 2026-07-04 DONE — bim-ootb PR #646 (`lane/door-width-crush-gate`, commit `cc7c1ba`), open for review
# `sdg_gate.js` door-crush check shipped exactly per §THE FIX below. `witness_sdg_gate.js` A7/A7c 8/8. No
# regression: witness_sdg_cascade 7/7, witness_stretch_ride 9/9, witness_e2e_stretch_ride 9/9 (RED count
# unchanged at 8 on the real legitimate-stretch path — confirms the new check doesn't false-fire there),
# witness_e2e_gridstretch 7/7. Below this point is the ORIGINAL spec, kept for history.

# ⚠ DO NOT REMOVE — Scope guard
# SCOPE: close the one named gap in §STRETCH-RIDE (prompts/archive/RESUME_CASCADE_INTO_STRETCH.md, PR #604,
# MERGED) and prompts/RESUME_GRAPH_MODELLER_INTEGRATION.md's "THE USEFUL DIFF #1": the door-out RED only checks
# the filling's CENTRE point; it never checks whether the filling still FITS. Read the §-log after every run.

## THE GAP (one sentence)
`sdg_gate.js`'s only opening-safety check is `door-out` — a filling's CENTRE leaving its host's XY footprint —
so a grid-stretch that shrinks a wall below the hosted door's own real width produces **zero RED**, because the
door (which never scales — `sdg_cascade.js stretchRide` keeps its extent fixed and only rides proportionally)
can have its centre still nominally "inside" a host that is now too narrow to actually contain its full width.

## RECON (done before writing this spec — don't re-derive)
Probed real SampleHouse geometry (`ArcEditable.seedArc` + `Library.foldInsert`, the same pipeline
`witness_stretch_ride.js`/`witness_sdg_gate.js` already use) for every `rel_fills_host` pair's host/door AABB
containment, per axis, at the SAME tol (0.05) `door-out` already uses:

```
host=3  door=13  containedXYZ=[false,false,false]
host=1  door=12  containedXYZ=[true, false,false]
host=1  door=11  containedXYZ=[true, false,false]
host=5  door=8   containedXYZ=[true, false,true]
host=5  door=9   containedXYZ=[true, false,true]
host=3  door=7   containedXYZ=[true, false,true]
host=1  door=10  containedXYZ=[false,false,false]
```

**Finding that shapes the fix:** the Y-axis (wall thickness) is `false` for every single pair, always — real door
frames commonly sit slightly proud of the wall face even in the pristine as-extracted geometry. **A naive
3-axis "is the filling's whole box inside the host's whole box" containment check would NEVER be true before
the edit, for any real door, ever** — the check would be permanently dead (never fires, false confidence), not
just occasionally wrong. This is exactly the kind of real-data surprise CLAUDE.md's non-invent rule exists to
catch before writing the check, not after.

## THE FIX — contained-on-the-CHANGED-axis only, not all 3
Restrict the containment check to the axis (or axes) where the **host's own AABB extent actually changed**
between `before` and `after` — i.e. the axis that was stretched. Two real walls in the recon above (host=3/
door=7, host=5/door=8, host=5/door=9, host=1/door=12, host=1/door=11) are genuinely `true` on X (their run
axis) pre-edit, which is the only axis a grid-stretch on that wall ever changes. Y/Z (thickness/height) are
never touched by a horizontal grid-stretch, so excluding them from the check is not a tolerance hack — it is
the geometrically correct scope (the gate is PURE geometry over the passed AABBs, no new inputs needed: comparing
`before[host]` vs `after[host]` per-axis width already tells us which axis moved, with zero added parameters).

In `modeller/sdg_gate.js`'s `evaluate()`, alongside the existing `door-out` loop over `hostOf`, add:

```js
// (3) DOOR CRUSH — a hosted filling's own footprint no longer fits the host's on the axis the edit actually
// changed (host stretched narrower than the filling's real measured width), even though the filling's CENTRE
// may still sit inside the host (door-out only tests the centre point). Restricted to the axis whose HOST
// extent changed — NOT all 3 (a real filling's AABB commonly overhangs the host's OTHER axes — frame/casing
// beyond wall thickness — even pre-edit; see recon above. A naive 3-axis check would never fire, ever).
Object.keys(hostOf).forEach(function (fStr) {
  var f = +fStr, h = hostOf[f];
  if (!movedSet[f] && !movedSet[h]) return;
  if (!after[f] || !after[h] || !before[f] || !before[h]) return;
  for (var k = 0; k < 3; k++) {
    var w0 = before[h][2 * k + 1] - before[h][2 * k], w1 = after[h][2 * k + 1] - after[h][2 * k];
    if (Math.abs(w1 - w0) < 1e-6) continue;                          // host didn't change extent on this axis
    var fitBefore = before[f][2 * k] >= before[h][2 * k] - 0.05 && before[f][2 * k + 1] <= before[h][2 * k + 1] + 0.05;
    var fitAfter  = after[f][2 * k]  >= after[h][2 * k]  - 0.05 && after[f][2 * k + 1]  <= after[h][2 * k + 1]  + 0.05;
    if (fitBefore && !fitAfter) { red.push({ kind: 'door-crush', a: f, b: h, axis: 'xyz'[k] }); break; }
  }
});
```

Delta-honest by construction (same convention as `door-out`/`clash`): a pair that never fit on the stretch
axis to begin with (host=3/door=13 above) can't trigger it (`fitBefore` is false), matching the gate's existing
"only flag what the edit broke" invariant.

## WITNESS PLAN
Extend `modeller/tests/witness_sdg_gate.js` (same file, same `evaluate()` under test, same real SampleHouse
`base`/`rel` fixtures already built for A1-A6) with:
- **A7 RED door-crush** — pick the same real host/door pair the file already validates (reuse A4's `fe`/`door`/
  `host`), shrink the host (anchored-min SCALE mapping, the identical formula `sdg_cascade.js` already uses in
  production) to 60% of the door's own real measured width on the wall's run axis → assert
  `red` contains `door-crush{door,host}`.
- **A7c control** — the SAME host shrunk only to 150% of the door's width (still fits) → assert NO `door-crush`
  fires. Proves this is not a blanket "any shrink is RED" — only an actual crush.
- No new e2e/headless witness needed: `sdg_gate.evaluate()` is pure and already called from the SAME production
  call site (`bonsai_gridmove.js` → `commitGridMove`) that `witness_e2e_gridstretch.js`/`witness_e2e_stretch_ride.js`
  already exercise end-to-end; this change adds a branch inside an already-wired function, not a new call site.

## DONE WHEN
1. `sdg_gate.js` ships the `door-crush` check above.
2. `witness_sdg_gate.js` A1-A6 unchanged/still green + new A7/A7c green.
3. No regression: `witness_sdg_gate_smoke.js`, `witness_sdg_cascade(+smoke)`, `witness_stretch_ride.js`,
   `witness_e2e_stretch_ride.js`, `witness_e2e_gridstretch.js`.
4. Push to a fresh branch off `origin/main` (mirror `GRID_CLEAR_STATE_LEAK_FIX.md`'s PR #644 pattern) — PR opened.
