<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# X-RAY REVEAL — fixture/structure misclassification on SampleCastle (2026-07-13)

```
# ⚠ DO NOT REMOVE
SCOPE: bim-ootb `modeller/modeller.html` — `_fixtureColorMap()` (~line 525) + `xrayReveal()` (~line 530).
Fixes which elements X-ray treats as a "glowing fixture" vs "near-transparent glass structure". Does NOT
touch disc_walker.js's placement logic, arc_editable.js's PALETTE colouring, or anything else — those are
correct as they are; this is purely about xrayReveal()'s own classification signal being wrong.
PRIMARY TEST BUILDING = SampleCastle (user's explicit instruction, 2026-07-13: "more challenging standard
and empty of any MEP and not the same source as DX" — SampleCastle is architecturally distinct from
Duplex, a better generalization test than reusing the same building every time). Duplex is regression-only
— it must not break, but do not tune the fix to Duplex's data and call it done. Read the log after every
run. Commit locally — the standing PUSH PAUSE applies (`CLAUDE.md` §⏸), do not push/PR without checking
whether it's been lifted.
```

## §GIVEN — measured, do not re-derive

- **G1 — the defect, live:** ran a real `discWalk('ELEC', {building:'SampleCastle'})` (325 fixtures placed,
  8/8 witness pass, not a refuse) then `window.__xrayReveal(true)`. Screenshot shows tall solid yellow
  spikes shooting up through the roof — these are SampleCastle's `IfcWall` elements (36 of them, per the
  Outliner), not fixtures. The roof/slab correctly went near-transparent glass. On Duplex, the same
  sequence (102 fixtures, `discWalk('ELEC',{building:'Duplex'})`) visually looks correct (walls read as
  ghosted, only small fixture dots glow) — but see G3, this may be Duplex-data luck, not a working
  classifier.
- **G2 — the classification code, exact:** `_fixtureColorMap()` (`modeller.html:525-529`) builds `{fid:
  color}` from every `GEOM_INSERT` op whose `parameters.color != null` — **no discipline check, no
  fixture-vs-structure check at all.** `xrayReveal()` (`modeller.html:530+`) then treats ANY mesh whose fid
  is in that map as a "glow" (opacity 0.97 + emissive, i.e. rendered near-opaque in its own color) and
  everything else as "glass" (opacity 0.06, near-transparent).
- **G3 — why structure elements are IN that map at all:** `arc_editable.js:191` stamps
  `params.color = colorFor(cls)` (the cosmetic PALETTE colour, `colorFor` at `arc_editable.js:25`) on
  **every** authored ARC element regardless of class — walls, slabs, roofs, doors, all of it — and
  `arc_editable.js:244` pushes each as `op_type: 'GEOM_INSERT'`. Walked MEP fixtures are ALSO committed as
  `GEOM_INSERT` (confirmed: `witness_e2e_walk.js`'s own W4 assertion checks `op_type` grew by
  `GEOM_INSERT` count) — so **op_type cannot distinguish "real ARC wall" from "walked ELEC fixture,"** and
  neither party's colour value is ever null. `_fixtureColorMap()`'s `!= null` filter therefore always
  matches BOTH. Not yet measured (do this first, §POC below): why this doesn't visibly break on Duplex —
  plausible explanation is Duplex's `IfcWall` PALETTE colour happens to be close enough to the "near-white
  ghosted" look that opacity 0.97 + its own colour is hard to tell apart from opacity 0.06 + white, purely
  by coincidence of that one building's PALETTE value, not because the classifier is actually discriminating
  correctly. Confirm or refute before assuming Duplex is a clean control.
- **G4 — the real, already-existing discriminator:** walked-discipline meshes carry `userData.dwDisc` (or
  `userData.dwAsm`) — set at render time (`modeller.html:3694,3719,3753` and others; grep `dwDisc` for the
  full set of call sites) and already used elsewhere in this file to reliably tell "this mesh came from a
  disc-walker" from "this mesh is an authored ARC element" (visibility toggles, disc-clear, disc-walk
  status queries all key off it today). This tag does not exist on ARC-seeded meshes. It is the correct
  signal `xrayReveal()` should use instead of "does the op have a colour."

## POC GATE (first, before touching xrayReveal() itself)

1. On SampleCastle post-walk, for a sample of ~10 wall fids and ~10 walked-ELEC fids, log
   `§XRAYPOC fid=<n> hasColorParam=<bool> hasDwDisc=<bool> class=<ifc_class or dwDisc value>`. Confirm
   walls all have `hasColorParam=true, hasDwDisc=false` and fixtures all have `hasDwDisc=true` — this is
   the fix's whole premise, verify it numerically before rewriting the classifier.
2. Repeat on Duplex — log the SAME numbers, and additionally log each wall's actual resolved PALETTE
   colour hex next to its rendered appearance, to settle G3 (coincidence vs correct classification) rather
   than guessing.
3. If either measurement contradicts G2-G4 above, STOP and report the numbers — do not proceed to
   implementation on an unconfirmed premise.

## Implementation (after the gate passes)

- `_fixtureColorMap()` (or its caller in `xrayReveal()`) gains a `mesh.userData.dwDisc != null` check —
  only meshes carrying a real walked-discipline tag are eligible for the "glow" treatment; everything else
  (every authored ARC element, regardless of whether its op happens to carry a colour) goes to "glass."
  This is a narrowing of an existing over-broad filter, not new architecture.
- Keep the existing fixture colour source (`_fixtureColorMap()`'s `parameters.color` lookup) for the
  glow colour itself once an element passes the `dwDisc` gate — only the ELIGIBILITY test changes.
- No change to `arc_editable.js`'s PALETTE stamping — that colour is legitimately used elsewhere
  (cosmetic rendering) and is correct as-is; the bug is xrayReveal() over-trusting its presence as a
  fixture signal, not the stamping itself.

## WITNESS PLAN

- **W-XRAY-POC**: the gate numbers above, both buildings.
- **W-XRAY-SC-LIVE** (primary, per user instruction): real `discWalk('ELEC',{building:'SampleCastle'})` →
  `xrayReveal(true)` → assert via `g.children` that every mesh with `opacity≈0.97` (glow) has
  `userData.dwDisc` set, and every mesh without it has `opacity≈0.06` (glass) — no screenshot judgment
  needed, a real in-scene assertion. THEN take the guide-quality screenshot as human-eyeballed
  confirmation (per this project's own "log proves the numbers, eyes confirm the frame" standard) —
  no more yellow spikes through the roof.
- **W-XRAY-DUPLEX-REGRESSION**: same assertion on Duplex — confirm it still looks correct (or, if G3's
  "coincidence" theory is confirmed, confirm it now looks correct FOR THE RIGHT REASON, not by luck).
- Append a dated `# DONE` with quoted §-lines to THIS file; commit here too.

## DONE WHEN

Gate numbers confirm G2-G4; `xrayReveal()` keys glow-eligibility off `dwDisc`/`dwAsm`, not colour
presence; W-XRAY-SC-LIVE shows a clean SampleCastle X-ray (structure ghosted, only real walked fixtures
glow, no spikes); Duplex regression-checked; both witnesses green. Once done, the ModellerGuide.md
`walk-fixtures.png` X-ray demo can be redone on SampleCastle (325 fixtures across 7 storeys — the dramatic
depiction originally asked for) instead of Duplex — that guide-image swap is a natural follow-up once this
lands, not part of this spec's own DONE bar.
