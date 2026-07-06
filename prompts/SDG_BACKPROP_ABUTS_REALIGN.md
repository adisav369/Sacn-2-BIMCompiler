# ✅ 2026-07-04 DONE — bim-ootb PR #647 (`lane/abuts-realign-orange`, commit `0ae4a44`), open for review
# `sdg_gate.js` abuts-realign ORANGE check shipped per §THE FIX below, with one recon-driven correction: touch
# detection had to become SIGN-AGNOSTIC (see §RECON-2 below) — the doc's original faceGap-based design would
# have silently never fired on a flush real pair. `witness_sdg_gate.js` A8/A8b/A8c 11/11. No regression:
# witness_sdg_cascade 7/7, witness_stretch_ride 9/9, witness_e2e_stretch_ride 9/9, witness_e2e_gridstretch 7/7.
#
# §RECON-2 (found DURING implementation, not caught by the original recon above): SampleHouse's 2 real seeded
# abuts pairs are NOT within ABUTS_TOL when measured on the seeded/foldInsert boxes the gate actually uses —
# `cross_edges.js` derives them from raw `element_transforms` (a different coordinate representation than the
# ARC-seeded scene geometry `Library.foldInsert` produces for the SAME elements). Witnessed instead with a real
# wall + a constructed flush neighbour (A5's own precedent). This frame/consistency gap is NOT fixed here — it
# may also affect the EXISTING (already-shipped) `related()` clash-exclusion use of the same abuts edges; flag
# for a future audit, don't assume it's benign just because nothing broke visibly yet.
#
# Below this point is the ORIGINAL spec, kept for history.

# ⚠ DO NOT REMOVE — Scope guard
# SCOPE: the first slice of W-SDG-BACKPROP (SPATIAL_DEPENDENCY_GRAPH.md Phase 3), confirmed unbuilt — zero hits
# anywhere in either repo, any branch, any commit message (checked 2026-07-04). Read the §-log after every run.

## THE GAP
`sdg_gate.js` already ships RED (clash, door-out, door-crush) and ORANGE (clearance) — but ORANGE today is
generic proximity, not edge-aware. The doctrine's `abuts` edge (SPATIAL_DEPENDENCY_GRAPH.md's typed-edge
contract table) has a specific backward signal that is NOT built: **"neighbor pulled away → gap → ORANGE
realign."** `cross_edges.js` already derives real `abuts` (face-touch) edges and stashes them on
`window.swXEdges.abuts` — but they are currently consumed ONLY to EXCLUDE abutting pairs from the clash check
(`_gateRel().related`), never to detect that an edit pulled two REAL abutting elements apart, and never to
propose the Δ that would put them back.

## RECON (done before writing this spec — don't re-derive)
Probed real SampleHouse `element_transforms` via the SAME §ARC-1 seed pipeline the other gate witnesses use:
`window.swXEdges.abuts` has **169 edges total** (over the WHOLE building's substrate), but the ARC seed only
makes **39 elements EDITABLE** (editable BOM = ARC ONLY, per VISION-LOCK). Of those 169, only **2 edges connect
two seeded/editable elements**:

```
a=1 (IfcWall)             b=30 (IfcFurniture)   axis=Y  gap_mm=19.0
a=5 (IfcWallStandardCase) b=32 (IfcFurniture)   axis=X  gap_mm=8.4
```

**Finding that shapes the fix:** there is currently no real wall↔wall corner-abuts pair among SampleHouse's
seeded elements to test against — the doctrine's canonical illustration ("room A abuts B abuts C") doesn't have
an editable instance here. The two REAL pairs available are wall↔furniture. This is still a genuine,
non-invented `derived:face-touch` edge between two elements a grid-stretch's bay-proportional shift can
plausibly separate (`GridKinematicEngine._classifyInterior`/`_computeBayProportional` already moves
unattached furniture proportionally when a bounding gridline drags) — it is the correct real-data fixture for
the witness, just not the "two walls at a corner" mental picture. Scope the spec generally (any two seeded
elements with a real abuts edge), not wall-specific.

## THE FIX — one-hop, gated, non-invent (per SPATIAL_DEPENDENCY_GRAPH.md's own constraints)
Per the doctrine: **continuous-Δ only** (a grid move/stretch, not a discrete choice), **one-hop** (no chained
relaxation — "we never run an autonomous relaxation chasing a global fixed point"), **reports only** (the Δ is
a *proposal*, never auto-applied — matches `sdg_gate.js`'s existing REPORTS-ONLY contract for clash/door-out/
door-crush; accepting a suggestion is a SEPARATE future signed op, not built here), **delta-honest** (a
pre-existing gap is not flagged — only a gap the EDIT worsened).

In `modeller/sdg_gate.js`'s `evaluate()`, add a 4th check reading a new `rel.abuts` input (fid pairs, resolved
by the caller through the same §ARC-1 bridge `hostOf` already uses):

```js
// (4) ABUTS REALIGN — a neighbour PULLED AWAY from a real face-touch partner during THIS edit (one side moved,
// the other didn't) → propose an ORANGE Δ that would restore the touch (SPATIAL_DEPENDENCY_GRAPH.md's `abuts`
// backward signal: "neighbor pulled away → gap → ORANGE realign"). REPORTS ONLY — the proposedDelta is a
// suggestion; applying it is a future accept-gated op, not this check's concern (mirrors clash/door-out/
// door-crush: this function never mutates, only flags).
var ABUTS_TOL = 0.03;   // m — SAME touch tolerance cross_edges.js already uses to derive the edge (non-invent reuse)
(rel.abuts || []).forEach(function (pr) {
  var a = pr.a, b = pr.b;
  if (!after[a] || !after[b] || !before[a] || !before[b]) return;
  var movedA = !!movedSet[a], movedB = !!movedSet[b];
  if (movedA === movedB) return;                                    // both or neither moved — nothing pulled away
  var nb = movedA ? b : a, mv = movedA ? a : b;                      // nb = neighbour (unmoved), mv = the moved side
  var gapBefore = faceGap(before[a], before[b]);
  if (gapBefore == null || gapBefore > ABUTS_TOL) return;            // wasn't genuinely touching before this edit
  var ovAfter = overlaps(after[a], after[b]), k = 0;
  for (var ai = 1; ai < 3; ai++) if (Math.abs(ovAfter[ai]) < Math.abs(ovAfter[k])) k = ai;
  var gapAfter = ovAfter[k] < 0 ? -ovAfter[k] : 0;
  if (gapAfter <= ABUTS_TOL) return;                                 // still touching (within tol) — no realign needed
  var dir = centre(after[mv])[k] - centre(after[nb])[k] >= 0 ? 1 : -1;
  var delta = [0, 0, 0]; delta[k] = dir * gapAfter;
  orange.push({ kind: 'abuts-realign', a: nb, b: mv, gap: +gapAfter.toFixed(4), axis: 'xyz'[k], proposedDelta: delta });
});
```

**Wiring (`modeller/modeller.html`):** `_gateRel()` already builds `hostOf` from `X.fills` through the
`__arcFidByGuid` bridge — add the mirror for `X.abuts`: `abuts: X.abuts.map(e => ({a: fbg[e.a], b: fbg[e.b]}))
.filter(p => p.a != null && p.b != null)`. This one function feeds BOTH `commitMove` (point-drag) and
`commitGridMove` (grid-stretch) — zero new call sites, the existing `_runGate` plumbing already reruns on every
edit. **Toast text fix (in scope, one line):** `_runGate`'s orange branch is hardcoded `'tight clearance'`
regardless of kind — genericize to list kinds (mirror the RED branch's `res.red.map(r => r.kind).join(', ')`)
so `abuts-realign` doesn't get mislabeled as a clearance issue.

## WHAT THIS SLICE DELIBERATELY DOES NOT BUILD (flagged, not silently dropped)
- **No accept/ignore UI.** Matches the EXISTING pattern: `clearance` ORANGE has shipped since §GATE-1 with zero
  accept-button UI — it is REPORTS-ONLY (toast + emissive highlight + `orange[]` in the return value), same as
  this. Wiring a click-to-accept (which would commit `proposedDelta` as one more signed `GEOM_MOVE`) is a
  separate follow-on slice once this engine is witnessed — consistent with how `§FORWARD` shipped its engine
  before `§FORWARD UI-WIRING` did, and how STRETCH-RIDE preceded door-crush.
- **No chained/cyclic relaxation.** One hop only (the doctrine's own constraint — cycles have no unique
  fixed point, so this is a design boundary, not a shortcut).
- **No wall-wall real fixture.** The 2 real seeded abuts edges on SampleHouse are wall↔furniture (see RECON).
  The witness targets the pair that exists; it is not weaker evidence for it — the check is generic over any
  two elements with a real `abuts` edge, not wall-specific by construction.

## WITNESS PLAN
Extend `modeller/tests/witness_sdg_gate.js` with:
- **A8 RED-free ORANGE realign** — using the real fid=1(wall)/fid=30(furniture) abuts pair (gap_mm=19, axis Y):
  move ONLY fid=30 away on the Y axis by enough to open a gap past `ABUTS_TOL` → assert `orange` contains
  `abuts-realign{30,1}` with a `proposedDelta` that, when APPLIED to fid=30's box, closes the gap back to ≈0
  (verify by re-running `faceGap` on the proposed after-state — the "gradient-checking" defence the doctrine
  names: never trust a propagated Δ without re-measuring).
- **A8b control** — a move small enough to stay within `ABUTS_TOL` → no `abuts-realign` (not a blanket
  every-move-is-orange).
- **A8c delta-honest** — the pair is ALREADY separated beyond tol in `before` (a synthetic pre-existing gap,
  mirroring A3's delta-honest pattern) → moving fid=30 further does NOT fire `abuts-realign` for this pair
  (the edit didn't cause it — matches `gapBefore <= ABUTS_TOL` guard).

## DONE WHEN
1. `sdg_gate.js` ships the `abuts-realign` ORANGE check + `modeller.html`'s `_gateRel()` feeds it real edges +
   the toast-text genericization.
2. `witness_sdg_gate.js` A1-A7c unchanged/still green + new A8/A8b/A8c green.
3. No regression: `witness_sdg_cascade.js`, `witness_stretch_ride.js`, `witness_e2e_stretch_ride.js`,
   `witness_e2e_gridstretch.js` (playwright-gated smokes noted as a pre-existing environment gap, not re-checked
   here unless the environment changes).
4. Push to a fresh branch off `origin/main` (mirror PR #644/#646's pattern) — PR opened, left for review.
