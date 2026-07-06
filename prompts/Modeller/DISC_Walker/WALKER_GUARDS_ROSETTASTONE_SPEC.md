# Walker Guards + RosettaStone Walk-Back + Calibrated Confidence — spec (handoff for a new session)

```
# ⚠ DO NOT REMOVE
SCOPE: A UNIVERSAL guard layer every discipline walker passes through + a RosettaStone walk-back harness that
post-process-checks each discipline walks back correctly + a CALIBRATED confidence marker. This answers
"will the walk generalise to all disciplines / all ARC buildings?" — NOT by making the walk always correct
(unprovable), but by: generate within HARD guards → measure against the RosettaStone where ground truth exists →
REFUSE-and-flag where it doesn't → NEVER hide uncertainty. Oracle to CALIBRATE, guards to GENERALISE.
ANCHORS (read first): prompts/Modeller/DISC_Walker/STR_ROUTEWALKING_SPEC.md (the first walker — its §2A/§2B handlers are lifted here),
prompts/SPATIAL_DEPENDENCY_GRAPH.md, prompts/RESUME_GRAPH_MODELLER_INTEGRATION.md §VISION-LOCK (every non-ARC
discipline is a WALKER; the Outliner Disc-tab shows the walked followers + their confidence).
INVARIANTS: signed ops (kernel_ops) · NON-INVENT (measured/cited, never guessed; REFUSE beats fabricate) ·
ABSTRACT, never per-class/per-discipline (the door doctrine) · oracle = pristine extracted.db, NEVER cooked
output.db · CONFIDENCE MUST BE EARNED (calibrated on the RosettaStone), never asserted. WITNESS-FIRST. Read the §-log.
```

## §0 THE THESIS (why this exists)
The STR walker (proven this arc on Terminal + SC) showed the walk is *promising on rectilinear, clean buildings* but
gave no guarantee for **rotated/radial/free-form plans, messy IFC, other disciplines, or blank buildings**. The fix is
NOT "make the walk universally correct" — it is to **change the contract**:
1. **Generate within HARD GUARDS** — physical/regulatory invariants that no walked element may violate.
2. **Measure against the RosettaStone** — where a real extracted.db exists (Terminal), walk each discipline back and
   score it element-for-element. This CALIBRATES the walk and the confidence.
3. **REFUSE-and-flag, never fabricate** — if no placement satisfies the guards, emit a GAP, not a wrong element.
4. **Surface uncertainty** — every walked element carries a CALIBRATED confidence; low confidence is highlighted.
**Oracle CALIBRATES (where truth is known); GUARDS GENERALISE the safety to blank/novel buildings (where it isn't).**
The residual risk becomes **guard coverage** + **confidence calibration** — both testable on Terminal — instead of
"is the walk correct everywhere?" (unanswerable).

## §1 WHERE IT SITS (above the walkers; a thin universal pass — NOT a rebuild)
Every discipline walker (STR `str_walker.js`; MEP `routewalker.js`/`RouteWalker.java`; future ELEC/FP/ACMV) emits
candidate placements. They ALL flow through ONE guard pass before becoming signed ops. This LIFTS the STR walker's
already-built handlers (`STR_ROUTEWALKING_SPEC §2A` spatial fit-within-ARC/clash, `§2B` regulatory RED/ORANGE, the
`null`-on-empty refuse) into a discipline-agnostic layer. Reuse, don't rebuild:
| Already exists | Becomes (here) |
|---|---|
| STR `swCheckGirder` RED/ORANGE/GREEN | the confidence/refuse signal of the guard pass |
| STR `swDeriveTessellation`→null on no plates | the REFUSE-to-place safety valve, generalised |
| MEP RW ARC-clash gate (`RouteWalker.java` A4) | the Containment + Mutual-clash guards |
| MEP RW anchor pairing / gradient | Source-connectivity + Orientation guards |
| `kernel_ops` signed ops | every PLACE and every REFUSE(gap) is a signed op |

## §2 THE GUARD LAYER (the user's list, formalised — two families + a contract)
Each guard: a MEASURED predicate over a candidate placement + the already-placed set; returns `pass | soft | hard`.

### §2A SPATIAL guards (WHERE)
1. **Containment** — element AABB ⊂ building envelope (ARC bounds, with a small tol). HARD if outside.
   *Catches the smeared/rotated-grid failure: a mis-derived datum that throws an element outside is refused.*
2. **Surface adherence** — element sits ON its host surface (slab/wall/roof) within tol; not floating. HARD if detached
   beyond tol; SOFT if within a small drift (snap + flag).
3. **Orientation** — not facing up / wrong way: captured yaw or host-surface normal; HARD on impossible orientation
   (e.g. a door normal pointing +Z). *This is the door-facing saga, generalised — measure, never whitelist a class.*
4. **Mutual clash** — AABB (or swept volume) does not penetrate an already-placed element beyond tol (shared CLASH BUS
   accumulates ARC→STR→MEP). HARD on real penetration; SOFT on touch.

### §2B CONNECTIVITY / REGULATORY guards (WHETHER)
5. **Source connectivity** — a routed element (MEP) traces back to a MAIN/riser/meter; a structural member traces a
   continuous LOAD PATH to ground. HARD if it starts in mid-air / has no path.
6. **Priority placement order** — walks run in dependency order **ARC → STR → MEP/CW/SP → FP/ELEC/ACMV** (mains before
   branches within a discipline); later walks read the accumulated clash bus, so they respect earlier placements.
7. **Code/refuse** — regulatory minimums (the STR span/depth, UBBL/Eurocode mins, MEP clearances). If NO candidate
   satisfies the guards within tolerance → **REFUSE-TO-PLACE**: emit a `GAP` record (location + reason), never a wrong
   element. (Generalises STR's `null`-on-no-plates.)

### §2C THE GUARD-PASS CONTRACT (the output)
For each candidate the pass returns one of:
- **PLACE (GREEN)** — all guards pass with margin → signed PLACE op, high confidence.
- **PLACE-BUT-FLAG (ORANGE)** — soft violation, snapped/adjusted → signed PLACE op + flag, mid confidence.
- **REFUSE (RED/GAP)** — a HARD guard fails and no fix → signed `WALKER_GAP` op (NOT a placement), zero confidence.
Priority order (§2B-6) is the orchestration; the clash bus is the shared state; every outcome is a signed op so the
whole walk is deterministic-replay + scrub-able like any feature.

## §3 ROSETTASTONE WALK-BACK HARNESS (the post-process correctness check)
**Terminal = the multi-discipline RosettaStone** (measured this session, real `Terminal_extracted.db`):
`STR 34,356 · MEP 9,733 · ARC 2,222 · FP 995 · ELEC 833 · ACMV 289`. It is the ONE building with real ground truth
for every discipline — so it can check that each discipline **walks back** from the ARC correctly.

**The walk-back, per discipline D:** drop ARC ONLY → run walker(D) under the guard pass → compare the generated set
`W` to the extracted set `R` (oracle) **element-for-element** (NOT the coarse alignment-fraction the STR spike used).

**THE METRIC (decision flagged — confirm before building):** propose element-level matching with spatial+size tol:
- a walked `w` MATCHES a real `r` iff same class ∧ ‖pos(w)−pos(r)‖ ≤ posTol ∧ |size(w)−size(r)| ≤ sizeTol ∧ orient within tol;
- **recall** = matched(R)/|R| (did we place the real structure?), **precision** = matched(W)/|W| (did we avoid fabricating?),
- **walk-back score** = F1 + position-RMSE on matched pairs, reported PER CLASS (so trusses/secondary don't hide in an average).
- **Pass-bar (proposed, lenient to start, then tighten per calibration):** recall ≥ 0.70, precision ≥ 0.60, RMSE ≤ posTol.
  ⚠ The new session OWNS this bar — start by MEASURING the baseline on Terminal per discipline, then set bars from data
  (the way STR thresholds were swept, never invented). REFUSE(gap) elements count as honest non-placements, not errors.

## §4 CALIBRATED CONFIDENCE MARKER (earned, never asserted)
Each placed element carries **confidence ∈ [0,1] = f(guard margins, + oracle agreement when available)**. The marker is
the user-facing honest gauge (low confidence HIGHLIGHTED in the Outliner Disc-tab / 3D).
**CALIBRATION (decision flagged):** confidence is only meaningful if it PREDICTS correctness. On Terminal, bin walked
elements by raw confidence (deciles), measure the ACTUAL walk-back match-rate per bin → a reliability curve. Calibrated
iff |predicted − actual| ≤ tol per bin (Expected Calibration Error ≤ threshold). If not, fit a MONOTONIC recalibration
map (isotonic) so "0.8" means ~80% right. ⚠ **Never display an uncalibrated number** — that is the "looks-dead /
fake-gauge" trap this project already learned (see memory: System Monitor honest gauge). Tie the marker to the SDG
ML-defences framing (don't over-trust a model output) in `SPATIAL_DEPENDENCY_GRAPH.md §DOCTRINE`.

## §5 WITNESS CLAIMS (spec-first; oracle = pristine extracted.db, NEVER output.db)
Guards (synthetic perturbations on a real building — each names the failure it catches):
**ALL ✅ DONE 2026-06-26 (W-GUARD-* 8/8)** — `deploy/dev/walker_guards.js` + `scripts/witness_walker_guards.js`,
synthetic perturbations on REAL Terminal geometry:
- **W-GUARD-CONTAINMENT ✅** — a candidate pushed +50m past the envelope → HARD → REFUSE(gap), not placed.
- **W-GUARD-SURFACE ✅** — float 1m off host plane → HARD(refuse); 0.15m drift → SOFT snap+flag (seat snapped onto plane).
- **W-GUARD-ORIENT ✅** — facing forced to +Z off a horizontal host normal → HARD (90° MEASURED, no class whitelist).
- **W-GUARD-CLASH ✅** — a 2nd candidate overlapping a placed one → HARD (pen measured vs the placed id; clash bus works).
- **W-GUARD-SOURCE ✅** — a candidate requiring a source with an empty path → HARD (mid-air); 3-hop path → pass.
- **W-GUARD-REFUSE ✅** — a HARD guard → a signed `WALKER_GAP` op, confidence 0, ZERO fabricated placement.
- **W-PRIORITY-ORDER ✅** — lower-`priority` candidate placed first into the bus; the higher reads it → REFUSED on clash.
- **W-GUARD-ABSTRACT ✅** — the comment-stripped guard LOGIC greps to ZERO IFC-class/discipline/role tokens (door doctrine).
Walk-back (Terminal RosettaStone):
- **W-WALKBACK-STR ✅ DONE 2026-06-26 (5/5)** — `scripts/witness_walkback_str.js`. Element-for-element on REAL Terminal
  (oracle=extracted.db). MEASURED D1 baseline (NOT invented): **column** recall/prec **1.000**, RMSE **0.104m**
  (near-tautological — validates the grid residual, stated); **beam** PRECISION **0.907** = the don't-fabricate gate
  (asserted), recall **0.227** REPORTED as a COVERAGE gap (98/432 — only primary girders walked; joists/secondary/
  trusses unwalked, parallel to the member gap) — ⚠ corrects the spec's "71.5%" which was beam-ON-grid, a DIFFERENT
  measure; **plate** count-reconstruct **1.29%** (generative — element-match REFUSED on principle); **member** recall 0
  = honest coverage gap, zero fabrication. Metric (D1 PINNED): greedy 1-to-1 planar nearest within posTol (swept,
  plateau-reported); precision = don't-fabricate gate; recall = coverage (reported, not always a pass/fail).
- **W-WALKBACK-MEP ✅ UNBLOCKED 2026-06-29** (`scripts/witness_walkback_mep.js`, **8/8**) — the original ⛔ (2026-06-26)
  was the OLD `routewalker.js`/`ad_mep_anchor` path emitting **0 pipe segments** for two reasons: (1) the mined anchor
  topology had only `FIXTURE`+`VALVE`, no `JUNCTION`; (2) anchor frame local (~20m) vs site extracted (~671m). **BOTH
  dissolve on the newer `disc_walker.routeChains` engine reading a REAL MEP-bearing extracted.db directly:** the
  endpoint classes (IfcPipeFitting/Segment, IfcDuctFitting/Segment, IfcFlowFitting/Segment) are present, and candidates
  + oracle share ONE frame BY CONSTRUCTION (no anchor-mining step → reason 2 cannot arise). The §8E walk emitted 0 only
  because the modeller passed the LAID ARC-ONLY fixture as the building db. **0→N proven** on TWO substrates: Terminal
  5317 segs (PLB+ACMV), Duplex MEP 358 segs (PLB), ARC-only 0. Scored against a NON-INVENT geometric touch oracle
  (point-to-3D-segment ≤ touchTol — the IFCs carry no IfcRelConnectsPorts, so geometric touch IS the available ground
  truth): PRECISION (don't-fabricate gate, per-rule) Terminal/PLB **0.896**, Duplex/PLB **0.969** @0.15m; recall ~0.40
  reported as junction coverage. FINDING: ACMV ducts looser (0.269); M7 opt-in route-to-FACE (`{toFace:true}`, point-to-
  LINE, default OFF → live `dwWalk` byte-identical) lifts 0.269→0.332 (partial — ducts stay harder), reported not faked.
  The walk-back harness/metric/guards were already
  discipline-agnostic and scored MEP unchanged the moment a walker emitted candidates in the oracle frame, as foretold.
Confidence:
Generality (the rotated/free-form test — spec §7-5):
- **W-GUARD-ROTATED ✅ DONE 2026-06-26 (5/5)** — `scripts/witness_guard_rotated.js` + the new `wgFit` guard in
  `walker_guards.js` (snap residual vs tol = the walker's quantization error → REFUSE-to-place, generalises STR
  null-on-no-plates). Synthetically rotate the REAL Terminal column cloud 30°: the axis-aligned grid DEGRADES
  (compression collapses 28→90 gridlines ×3.2, mean snap residual 0.05→0.19m ×3.6) and the guard/confidence layer
  SURFACES it — WALKER_GAP refusals 0→15, mean confidence 0.90→0.74, low-confidence columns 11→28 highlighted; every
  refusal a traced gap, refused+placed=158, ZERO fabrication; same guard fns, only new number = θ. Proves the thesis:
  guards make the walk SAFE (refuse-and-flag) on a rotated plan where it is no longer CORRECT.
Confidence:
- **W-CONFIDENCE-CALIBRATED ✅ DONE 2026-06-26 (5/5)** — `deploy/dev/walker_confidence.js` + `scripts/witness_walker_confidence.js`.
  On REAL Terminal: raw confidence = f(guard margin × regulatory margin) is MISCALIBRATED (ECE **0.157** — guard/rule
  margins measure geometric/code comfort, NOT oracle-match probability) ⇒ must NOT be shown raw; isotonic/PAV
  recalibration against the extracted oracle, scored on a **HELD-OUT** split, drops ECE to **0.034 ≤ 0.05** (in-sample
  0.000 is the fake-gauge trap, explicitly not the evidence); the **negative control** — an inverted confidence,
  ECE **0.810** — FAILS the bar (the test bites). **D2 PINNED:** method = isotonic/PAV, ECE tol = 0.05, held-out.
  **+ C6 SHIPPED-MAP (6/6):** the calibration map is fitted ONCE on Terminal and SHIPPED as `WC_CALIBRATION` (3 PAV
  blocks) + `wcCalibrated()` — a DERIVED artifact (the witness regenerates it from Terminal and asserts it reproduces;
  functional diff only at 2 boundary samples = 6dp rounding), so it applies LIVE where there is no oracle. The bridge's
  `swbTabData` (W-STR-BRIDGE C6, 6/6) now carries a per-girder CALIBRATED confidence + low-confidence flag/count for the
  Outliner highlight — the EARNED gauge, never the raw number. **Engine half of the Outliner highlight (4b) is DONE;
  only the bim-ootb UI render + deploy remains.**

## §6 NON-INVENT BOUNDARY + THE TWO OPEN DECISIONS
- REFUSE(gap) is the safety valve — fabricating to fill a gap is the cardinal sin; a gap is an HONEST output.
- Every guard predicate is MEASURED (geometry/relations) or CITED (code); never a guessed constant.
- Confidence is CALIBRATED on the RosettaStone before it is ever shown.
- **DECISIONS — BOTH PINNED 2026-06-26 from MEASURED Terminal data (not invented):**
  (D1) ✅ walk-back **metric** = greedy 1-to-1 planar nearest within a swept posTol; **precision = the don't-fabricate
  pass-gate**, **recall = coverage** (reported; a low recall is an honest gap, not always a fail); generative classes
  scored by count/coverage, uncovered classes reported as zero-fabrication gaps. (D2) ✅ confidence **calibration =
  isotonic/PAV** fitted on the oracle and scored **held-out**; **ECE tol = 0.05**.

## §7 DEPENDENCIES / REUSE + NEXT (in order)
- `str_walker.js` (§2A/§2B handlers + RED/ORANGE/GREEN + null-refuse), `routewalker.js`/`RouteWalker.java` (ARC-clash +
  anchor connectivity), `Terminal_extracted.db` (the multi-discipline RosettaStone), `kernel_ops.js` (signed ops),
  the Outliner Disc-tab (`str_walker_outliner.js` pattern) for the confidence highlight.
- NEXT: (1) ✅ **DONE** — measured the Terminal STR walk-back baseline (D1 pinned, `witness_walkback_str.js`).
  (2) ✅ **DONE** — guard pass module `walker_guards.js` (containment/surface/orient/clash/source/refuse + priority
  + contract), W-GUARD-* 8/8, abstract grep-clean. (3a) ✅ **DONE** — STR walk-back harness on Terminal (W-WALKBACK-STR
  5/5). (3b) ✅ **DONE 2026-06-29** — MEP walk-back UNBLOCKED via `disc_walker.routeChains` on a real MEP-bearing
  extracted.db (W-WALKBACK-MEP 7/7, Terminal 5317 + Duplex 358 segs, precision PLB 0.87–0.97); the old 0-segment
  RouteWalker block dissolved (endpoints present, single frame). See §5 W-WALKBACK-MEP. (4a) ✅ **DONE** —
  calibrated confidence `walker_confidence.js` (isotonic/PAV, held-out ECE 0.034, negative control fails), D2 pinned.
  **(4b) Outliner Disc-tab confidence highlight: ENGINE ✅ DONE** (shipped `WC_CALIBRATION` map + `swbTabData` per-girder
  calibrated confidence + low-conf flag, W-STR-BRIDGE C6) — **only the bim-ootb UI render + OCI deploy remain** (extend
  `str_walker_outliner.js` to show the confidence column + highlight `lowConfidence` rows; consume `tab.elements`/
  `tab.lowConfidence`; deploy `walker_confidence.js` + bump sw). **(5) ✅ DONE — W-GUARD-ROTATED 5/5** (rotated
  Terminal degrades the walk; the fit guard + confidence SURFACE it; see §5). The FIT guard is the one that bites on a
  mis-derived rotated datum (not containment — snapped intersections stay inside the cloud bbox; the residual is the
  signal).
  **NOTE the walk-back harness, guard pass, and confidence calibration are already discipline-agnostic — the moment a
  MEP walker emits candidates in the oracle frame, W-WALKBACK-MEP scores with the SAME `witness_walkback_str.js` metric.**
- Companion: this is the safety/measurement layer the STR walker (`Modeller/DISC_Walker/STR_ROUTEWALKING_SPEC.md`) and the §VISION-LOCK
  walker-followers ride on. It does not change the walks — it guards, scores, and confidence-marks them.
```

## §8 THE TE EXECUTION PROTOCOL (added 2026-06-29 — the "lay TE, walk it, prove it green" run)
Settled in a long design dialogue. Five additions that turn the guard/walk-back machinery above into an executable
TE run with an honest acceptance bar. Spec-first; each item names what it proves. Oracle = pristine `Terminal_extracted.db`.

### §8A EXACTNESS OWED vs TOLERANCE OWED (the per-layer acceptance bar — do NOT promise "all exact")
Rules-alone do **not** bit-reproduce TE. The only 0.000 is **ARC — because it is DROPPED verbatim from the geo-stream,
not walked.** Everything *walked* is approximate, and the bar differs by layer (measured, not hoped):
| Layer | How produced | Bar | Evidence already measured |
|---|---|---|---|
| **ARC** | dropped verbatim (geo-stream, real mesh) | **EXACT 0.000** (count/centre/extent + yaw-by-vertex) | n/a — it's copied, not generated |
| **STR skeleton** (columns on grid-nodes, beams span datums) | walked, deterministic f(grid) | **TIGHT** — cm-level RMSE, not 0 | column recall 1.0 **RMSE 0.104m**; beam precision 0.907, recall a COVERAGE gap |
| **STR systems / density trades** (space-frame, PLB/ELEC/FP/ACMV) | walked, generative | **TOLERANCE** — count + cadence + nearest-neighbour within band; NOT per-element exact | plate count-reconstruct 1.29% (element-match refused on principle) |
| **MEP** | nn touch-pair vs geometric oracle | ✅ **UNBLOCKED 2026-06-29** (W-WALKBACK-MEP 7/7) — precision (don't-fabricate) PLB 0.87–0.97 @0.15m; recall reported as junction coverage; ACMV duct finding | Terminal 5317 + Duplex 358 segs, ARC-only 0 (real MEP substrate, single frame) |
The Stage-1 acceptance is therefore PER-DISCIPLINE: exact for the skeleton, tolerance for the generative, precision-gated for MEP.

### §8B TWO-STAGE ABLATION (clash-OFF baseline → clash-ON delta — isolate one variable)
The DB already has this shape: `rule_placement`+`rule_routing` are MINED (Stage 1); `rule_avoidance` is **EMPTY** (Stage 2).
- **Stage 1 — clash OFF:** walk each discipline from placement/routing rules ALONE (no avoidance), then walk-back vs the
  oracle. This isolates the *pure placement rules'* accuracy — the baseline/ceiling. ⚠ Expect Stage-1 to sit *worse*
  vs the oracle in spots: the real TE was already clash-resolved by the engineer; raw rules ignore that.
- **Stage 2 — clash ON (in tandem):** add the avoidance rules (clash bus). Any movement from Stage 1 is attributable
  ONLY to clash-avoidance. The real test of Stage 2 is **"does our avoidance match the engineer's?"** — clashes drop
  *and* nearest-neighbour distance to the oracle SHRINKS → rules right; distance GROWS → avoidance rule wrong.
- Without the split a deviation is ambiguous: a **wrong rule** is indistinguishable from a **legit clash dodge**.
- Final walker form = **(1) lay disc rules → (2) lay clash rules in tandem** (the two rule families above).
- **INTERIM RECORDS (before/after — mandatory):** snapshot the FULL intrinsic green-check set (§8C-1: joint gaps,
  connectivity/components, anchor adherence, gradient, terminals) AND the oracle-deviation **before** clash (Stage 1)
  and **after** clash (Stage 2), per element. Clash-avoidance is a PERTURBATION that can *throw off joints/assembly*:
  a dodge that moves a segment but **opens a joint gap, fragments the run, or detaches an anchor** is a BAD dodge even
  if the clash count fell. **Stage 2 is GREEN only if clashes DROP *and* the intrinsic integrity does NOT regress**
  (joint gaps stay ~0, components stay 1, anchors stay seated) *and* oracle-deviation shrinks (matches the engineer).
  Report the Δ-table: `clashes ↓`, `joint-gap Δ`, `component Δ`, `anchor-drift Δ`, `NN-to-oracle Δ` — per discipline.
  A clash that can only be cleared by breaking a joint is itself a signed RED `WALKER_GAP` (refuse), not a silent move.

### §8C THE TWO LOG LAYERS (intrinsic green = oracle-free; RosettaStone = TE-only calibration)
Two different kinds of "correct," proven by two different logs:
1. **INTRINSIC WELL-FORMEDNESS (oracle-free — green on EVERY building, the deliverable proof):** the whitebox §-log
   asserts the walk is internally valid with NO oracle. Per route/member:
   - **terminals** — starts at a source/main/riser/meter, ends at a fixture; both endpoints resolve to real anchors.
   - **continuity / no gaps** — segment end == next start within tol (C0); zero floating segments; breaks=0.
   - **connectivity** — one connected run source→fixture; orphans=0.
   - **anchor/shim** — each segment seats on a real surface within snap tol; standoff == pattern `offset_rule`.
   - **gradient/direction** — drainage falls monotonically to stack at ≥ code-min slope; supply direction correct.
   - **clearance + clash** — spacing ≥ standard; hard-clash=0 (avoidance only by design, signed).
   - **sizing** — member/duct size resolved from load/flow/span.
   These ARE the §2 guards. Oracle-free at RUNTIME — but their tolerances/offsets were CALIBRATED on TE (calibrate-once-
   apply-blind, NOT a contradiction). Caveat: intrinsic = WELL-FORMED, **not** REALISTIC (a route can be joined+anchored
   yet routed absurdly).
2. **ROSETTASTONE (extrinsic — TE-only, run ONCE, the lab):** catches "well-formed but unrealistic" by comparing to a
   real as-built. Used to CALIBRATE the confidence + tolerances the intrinsic checks then use blind elsewhere. On a NEW
   building you do NOT apply RosettaStone (nothing to compare) — the intrinsic log carries it. RS may surprise us either
   way; using the test data IS pristine research here.
**The peek rule (anti-collusion):** the oracle is peeked at **MINING/calibration time** (derive rules, fit tol/confidence)
— never at PLACEMENT time. The walk runs **blind** at inference; the RosettaStone then judges independently. To bias
toward the engineer's *style* among rule-valid routes, use **held-out / cross-validation** (mine on part, test on a
held-out part) — never fit the specific answer being graded. Placement-time peek = circular = proves nothing
(the `witness_drop_vs_java` self-collusion lesson).

### §8D THE GREEN REPORT (the acceptance artifact shown to the authors — inspectable)
The deliverable is not "we reproduced TE" — it is a layout where **every walked element = one signed op + one cited
rule/code + one measured deviation + one calibrated confidence.** GREEN ⇔ all of:
- rule-compliant (zero HARD guard violations), and
- no accidental clash (every overlap is avoidance-resolved or a flagged signed coordination decision; silent pen = RED), and
- bounded deviation (nearest-neighbour to as-built within the per-layer band, offset along-route not teleport; p95 inside band), and
- topological agreement (count/cadence/anchors/connectivity match), and
- calibrated confidence (low ECE), gaps listed not hidden.
Per-element line, click-to-inspect: e.g. *"column @grid X3/Y7 (`derived:grid`); deviation 6cm within one bay; conf 0.94; op#…"*
**"More correct" is earned, not bragged:** run the SAME guards over the as-built — green-on-ours + RED-on-theirs (a real
clash/code-violation in TE the authors missed) is the defensible claim. Auditing, not "smarter than the engineer."

### §8E THE TE SUITE (execute in sections, study the §-log, fix, sight start/mid/end, baseline progressively)
- **§8E-0 LAY TE ARC** (GAP 1/GAP 2) — ✅ **DONE 2026-06-29** (bim-ootb `lane/arc-mesh-readpixels`, commit baafada).
  `arc_editable` emits LOD-300 hash-ops rendering real `component_geometries` (instanced; box = logged geo-absent
  fallback). **Proof A** (node, all 35,552): vertices LOCAL (centre/diag 0.0117) + extent==bbox **0.0000**; found+fixed
  a latent box-proxy displacement on asymmetric elements. **Proof B** (node, all 35,552): modeller `place()` ==
  Viewer placement to **1.78e-15m** (params {cx,cy,cz+local_zmin,rot}). **W-ARC-MESH-READPIXELS 6/6** (headless,
  1557-elem real shell): mesh=1557 box=0, 824 instanced, 305,713 tris (16.4× all-box baseline), in-frustum 1557/1557,
  **A/B-isolated readPixels ARC paints 9.8% of canvas** (naive whole-frame nonBg=100% would have false-passed — A/B
  diff isolates the ARC footprint). NOTE yaw-by-vertex MOOT on TE ARC (rx=ry=rz=0, all axis-aligned) — owed to walkers.
  DEFERRED: full-SCALE browser render of the 261MB geo (33K roof plates) via range-stream (prompt defers scale).
- **§8E-1 STR clash-OFF** (Stage 1) — gate-assumptions ✅ (D: clash data-gated by `rule_avoidance`, currently EMPTY =
  clash-OFF default; E: grid recoverable 158 cols→18×10; FRAME: STR x∈[90,150] shares ARC x∈[86,154] site frame, MEP
  blocker absent). **W-WALKBACK-STR baseline REPRODUCED 5/5 2026-06-29**: column recall/prec 1.000 RMSE 0.104m (tight),
  beam prec 0.907 recall 0.227 (coverage gap), plate 1.29% (generative), member 0/442 (zero fabrication).
  ✅ **STR-INTO-ARC integration DONE 2026-06-29** (commit a003c05) — **W-STR-INTO-ARC 6/6**: swWalkSkeleton 158 cols
  → 18×10 grid; 158/158 walked columns land WITHIN the laid ARC footprint (same frame); residual RMS 0.094m ON grid;
  1715 scene meshes (1557 ARC + 158 STR) in-frustum; A/B-isolated readPixels STR=7714px (occluded by ARC shell, honest).
  NOTE one extraction outlier (a sink below grade among piling) — user-flagged ignore; a candidate for the future
  below-grade intrinsic green-check (§8C-1).
  ✅ **GIRDER RENDER DONE 2026-06-29** (§8E-1b) — lifted the STR render out of the witness into reusable bridge verb
  `swbRenderOps` + production `_seedStrWalk` (overlays the laid ARC). **W-STR-INTO-ARC now 11/11**: G1 108 girders
  rendered (= `swWalkGirders`); G2 108/108 endpoints sit ON a walked-column intersection (no floating girder); G3
  cross-section == MEASURED IfcBeam median 0.500×0.750m (0-tol, n=432; length = derived bay span, non-invent); G4
  A/B-isolated readPixels girders paint 7281px; G5 derived bay median span 7.79m within band of real beam length 1.69m
  (tolerance/coverage — the recall gap is expected). Regulatory handler honestly flags 8 over-span (>18m) girders RED.
  Fixture rebuilt with STR IfcBeam (measured section in-fixture). REMAINING in §8E-1: **plates = the GENERATIVE
  space-frame tessellation render** (the verbatim 33K-plate 261MB stream stays the DEFERRED range-stream; the
  bounded generative canopy is the next slice, naturally folding into §8E-2 density trades).
- **§8E-2 density trades clash-OFF** (PLB/ELEC/FP/ACMV): count + cadence + nearest-neighbour vs oracle (tolerance bar).
  - ✅ **§8E-2a STR SPACE-FRAME CANOPY DONE 2026-06-29** (bim-ootb `lane/arc-mesh-readpixels` 01c9312; spec §8E-2a).
    `swbCanopyOps` renders the 33K-plate roof as a BOUNDED generative tessellation into the laid ARC. **W-STR-CANOPY
    8/8**: count err 1.38% (predictedN 33784 vs extractedN 33324); unit==measured modal 0.5×0.1×0.11m (98.1%, 0-tol);
    domain x/y match; NN cadence gen 0.082m vs real 0.150m (within band, no collusion); readPixels canopy 8511px;
    §DW-CAP placed=2374 of 33232 (count proven numerically, not drawn); falsifier (empty→0 ops). Fixture
    `Terminal_plates_proof.db` (IfcPlate transforms only). DEFERRED: verbatim 33K mesh = 261MB range-stream.
  - ✅ **§8E-2b MEP-FAMILY DENSITY TRADES DONE 2026-06-29** (bim-ootb 6879595 + bim-compiler cb49ca8f; spec §8E-2b).
    **W-DW-DENSITY-TE 8/8**: PLB/ELEC/FP/ACMV walk + render into the laid ARC (`__dwPixelProbe` readPixels), 100%
    envelope, ACMV +7% / FP +13% tight, PLB routed→honest no-network refusal, no fidelity field. FINDING (not hidden):
    ELEC 2.4× over (density-transfer drift). Engine fix: single-placement now envelope-bound (build §DWD/§DWG/§DXG
    green). NEXT MEP gap = the ROUTED network (PLB/ACMV nn-chains) needs endpoints — ✅ now done in §8E-3 on a real MEP-bearing
    substrate. → §8E-4 Stage-2 clash.
- **§8E-3 MEP routed network** — ✅ **UNBLOCKED 2026-06-29** (`scripts/witness_walkback_mep.js`, **W-WALKBACK-MEP
  7/7**). The §5 block dissolved on a real MEP-BEARING substrate: `disc_walker.routeChains` reads endpoints DIRECTLY
  from the extracted.db, so candidates and oracle share ONE frame by construction (no anchor-mining local↔site split)
  and the endpoint classes are present. §8E emitted 0 only because the modeller passed the LAID ARC-ONLY fixture as the
  building db — substrate gap, not engine fault. **0→N proven:** Terminal (terminal_rules) **5317 segs** (PLB pipes +
  ACMV ducts), Duplex MEP (duplex_rules) **358 segs** (PLB), ARC-only SampleHouse **0** (the §8E-3 condition). NON-INVENT:
  all 5675 segments join two REAL guids at REAL positions, every gap ≤ measured bound, 0 fabricated. Walk-back vs a
  GEOMETRIC touch oracle (fitting↔pipe-run point-to-3D-segment ≤ touchTol, the §ABUTS measured-touch principle — the
  IFCs carry NO IfcRelConnectsPorts so recorded topology genuinely does not exist): **PRECISION (don't-fabricate gate,
  per-rule) Terminal/PLB 0.896, Duplex/PLB 0.969 @0.15m**; recall (0.40–0.42) reported as junction-degree coverage (nn
  recovers one leg of a multi-leg junction). HONEST FINDING (not gated): **ACMV ducts route looser (precision 0.269
  @0.15m)** — nn-to-CENTRE is far from the connection face on large ducts. **M7 ROUTE-TO-FACE** (opt-in
  `routeChains(disc,bdb,{toFace:true})`, point-to-LINE pairing, default OFF → live `dwWalk` byte-identical) PARTIALLY
  lifts it **0.269→0.332**, no PLB regression, 0 fabricated — ducts stay genuinely harder, reported not faked.
- **§8E-4 clash-ON** (Stage 2): ✅ **DONE 2026-06-29** (bim-ootb 6b9b8b6; spec §8E-4). `rule_avoidance` was already
  MINED (10 pairs, p05) — so this was the Δ proof. **W-DW-CLASH-TE 10/10**: clashes 170→3 (−98.2%) via 164 yields, count
  Δ=0, xy-drift=0 (envelope preserved), no below-grade, 3 flagged RED, top-priority never yields, residual rate 0.06% ≤
  real-TE p05 tail 2.17% (no worse than the engineer). Position GENERATED → judged by clash RATE, not per-element NN.
- **§8E-5 GREEN report** (§8D) over the whole TE walk; run the guards over the as-built for the red-on-theirs claim.
  ✅ **DONE 2026-06-29** (bim-ootb b7553ef; spec §8E-5). **W-GREEN-REPORT-TE 7/7** — one harness runs the whole walk →
  emits `modeller/tests/TE_GREEN_REPORT.md`. Per-layer: ARC 0.000 / STR cols RMSE 0.094m (8 over-span RED) / canopy
  1.38% / MEP ACMV+7% FP+13% tight, ELEC 2.39× finding, PLB ⛔ / clash 170→3. **Red-on-theirs: 994 real cross-disc
  pairs <0.10m, 57 <0.05m** in the as-built (green-on-ours residual 0.06% + red-on-theirs = auditing). Conf ECE 0.034.
Each section: run → save log → read log → fix faults → re-run → sight one start/mid/end screenshot only where geometry
needs an eyeball SANITY (readPixels is the proof, the shot is a sight-check). Baseline is established progressively,
section by section — never one big-bang.
```

### §8E-1b GIRDER RENDER — finish the STR skeleton into the laid ARC (2026-06-29)
Columns render (§8E-1 W-STR-INTO-ARC 6/6) but **only in the witness, inline; production seeds ARC only** — the walked
STR skeleton never reaches the scene. This slice (a) lifts the render out of the witness into a reusable bridge verb
`swbRenderOps`, (b) wires it into the production open flow (`_seedStrWalk`, mirror of `_seedArcEditable`), (c) renders the
**girders** `swWalkGirders` already computes. NON-INVENT bar (the load-bearing constraint):
- **Girder LENGTH = the derived bay span** (`|toDatum − fromDatum|`) — a MEASURED grid gap, never a constant.
- **Girder CROSS-SECTION = the MEASURED median of REAL source `IfcBeam`** (Terminal: width 0.500m × depth 0.750m; the
  1.855m median is *length*, which varies → replaced by the per-girder span). A building with no source beams → no
  measured section → render the girder as a thin line-proxy + honest `§STRWALK-RENDER no measured beam section` log,
  never a hand-picked box (the W-DW-PRIM doctrine: measure the size, never invent it).
- **Girder ELEVATION = top-of-column** = mean(walked column z) + column-half-height (median col dz/2), a representative
  SINGLE-LEVEL skeleton (the multi-storey girder lattice is a later slice; consistent with the measured beam **recall
  0.227 coverage gap** in W-WALKBACK-STR — the derived girders are the bay grid, not every storey's beam).
- **Orientation** is axis-aligned (the TE grid is orthogonal, rx=ry=rz=0) — girder long axis follows its gridline; the
  yaw-by-vertex check stays MOOT here (owed to a rotated building, not TE). Provenance `derived:str-walk`.
- **Plates / space-frame** are NOT this slice — they are the GENERATIVE tolerance layer (§8A row 3) and the verbatim
  33K-plate mesh is the DEFERRED 261MB range-stream. The skeleton = columns + girders only; plates stay a labelled gap.
**Fixture:** rebuild `Terminal_arcstr_proof.db` to add STR `IfcBeam` transforms — so the cross-section is measured
IN-fixture and the real beams are a render-coherence oracle (count/span distribution), not just a remote claim.
**Witness (extend `witness_str_into_arc.js`, W-STR-INTO-ARC):** drive the PRODUCTION `swbRenderOps` path (no inline
duplicate). New checks: G1 girders rendered = `swWalkGirders` count, each a GEOM_INSERT in the scene; G2 every girder
endpoint coincides with a walked column intersection (sits ON the grid, no floating girder); G3 cross-section ==
measured beam median (0-tol), length == bay span; G4 §READPIXELS A/B-isolated — girders rasterize over the ARC (> noise
floor); G5 derived-girder span distribution within band of the real `IfcBeam` spans (tolerance, generative-coverage,
NOT per-element match). Keep I1–I6 green (regression).

### §8E-2a CANOPY TESSELLATION RENDER — the STR space-frame generative system (2026-06-29)
The §8E-2 generative TOLERANCE layer (§8A row 3). The Terminal roof = **33,324 `IfcPlate`** = ONE measured unit
repeated over a measured surface domain = `instanced-by n` (SPATIAL_DEPENDENCY_GRAPH §SPANS / §SHELL-N-ZSPAN). The
walker `swDeriveTessellation`/`swWalkTessellation` (already in `str_walker.js`) reconstructs **count + cadence +
coverage**, NEVER bit-exact positions — graded against the real plate cloud as ORACLE. This slice renders a **bounded
representative canopy** into the laid ARC and proves the tolerance bar numerically over ALL real plates.
**NON-INVENT boundary (load-bearing):**
- **Unit** = the MEASURED modal plate bbox (Terminal: 0.5×0.1×0.11m, 98.1% modal share) — never a chosen cell.
- **Domain / surface** = MEASURED bbox of real plate centres + the measured z=f(x) band profile (the canopy sits at
  z∈[18.4,27.1] over x∈[91,153] = the ARC frame). Positions are a REGULAR reconstruction of the distribution, NOT a
  claim to match extracted positions (element-match REFUSED on principle — the `witness_drop_vs_java` collusion lesson).
- **Count** = `predictedN` = edge-trimmed interior band-density × nBands (a measured areal predictor INDEPENDENT of the
  raw total) — graded vs `extractedN` (expect ~1.3%, the W-WALKBACK-STR plate figure). This is the ONE confirmable
  thing about a generated set (THE MEASUREMENT DOCTRINE) → make it the headline.
- **Render is CAPPED** (`§DW-CAP placed=N of M`) — the bounded canopy proves the render path + readPixels; the COUNT
  claim is proven NUMERICALLY over all plates (never by rendering 33K boxes). Boxes of the unit bbox, NOT real meshes —
  the verbatim 33K-plate mesh stays the DEFERRED 261MB range-stream (we render the reconstruction, not the drop).
**Fixture:** `Terminal_plates_proof.db` = `IfcPlate` transforms only (centres+bbox, no geo) — small + focused.
**Bridge verb `swbCanopyOps(plates, opts)`** (NEW, edits nothing): measure tessellation → generate min(n, cap) unit
placements → emit GEOM_INSERT box ops (distinct canopy colour) + `§DW-CAP` + `§STRWALK-CANOPY` count/cadence log.
**Witness `witness_str_canopy.js` (W-STR-CANOPY):** P1 count `|predictedN−extractedN|/extractedN ≤ 5%` (tolerance);
P2 unit == measured modal bbox (0-tol) + modal share reported; P3 generated domain ≈ real domain (same frame/footprint
within a unit); P4 NN cadence — mean nearest-neighbour spacing of the GENERATED cloud ≈ that of a real-plate sample
(within band; cadence match, NOT generated-to-real element match = no collusion); P5 §READPIXELS A/B-isolated — the
bounded canopy rasterizes over the laid ARC (> noise floor) at z above the shell; P6 `§DW-CAP` logged (render bounded,
count not); P7 no LOAD_FAIL/pageerror. NON-INVENT falsifier: an empty plate set → `swDeriveTessellation` returns null →
zero ops, no fabricated canopy.
**Scope note:** §8E-2 also names the MEP-family density trades (PLB/ELEC/FP/ACMV) — those run on the SEPARATE
`disc_walker.js` engine + `terminal_rules.db` (the §PRIM / envelope-bound work), a distinct integration into the laid
ARC = §8E-2b below. This slice = the STR space-frame only (the direct continuation of the skeleton).

### §8E-2b MEP-FAMILY DENSITY TRADES — walk PLB/ELEC/FP/ACMV into the laid ARC (2026-06-29) — ✅ DONE (W-DW-DENSITY-TE 8/8)
VISION-LOCK sentence 4: every non-ARC discipline is a WALKER that FILLS the ARC. The engine (`disc_walker.js`:
Placer/Router/Gate) + render (`_renderDiscWalk`, §PRIM measured-bbox boxes) + op-log (`_commitDiscWalk`) ALREADY ship
and have build-side witnesses. The §8E gap = the PROOF that the 4 MEP discs walk over the **laid TE ARC** and fill it,
with an HONEST per-discipline count verdict vs the oracle (`Terminal_meta.db` real counts). This is the Stage-1
clash-OFF baseline — the spec EXPECTS it to sit worse in spots; we MEASURE, never force-green.
**The honest finding (calibrated 2026-06-29, terminal_rules over Terminal_arcstr_proof shell):**
- **ACMV** walked 1680 vs real 1570 (**+7%**) and **FP** 1117 vs 989 (**+13%**) — genuinely area-distributed terminals
  RECONSTRUCT count tightly (≤20%).
- **ELEC** 1988 vs 833 (**2.4×**) — density-transfer drift: density mined per source storey × the ARC shell's per-storey
  footprint OVER-predicts (ARC footprint ≠ the disc's real coverage area). Bounded (no explosion), REPORTED as a
  finding, NOT hidden.
- **PLB** 28 vs 8175 — a ROUTED discipline (pipe network). An ARC-only building has NO pipe endpoints → `routeChains`
  honestly returns `no-endpoints` (chains=0); only datum singles place. The count claim DOES NOT apply to a routed disc
  on an ARC-only substrate → assert HONEST refusal (zero fabricated network), don't dress 28 as a match.
**NON-INVENT boundary:** position = plausible (`placed:array-density`/`shim:host-wall`/`placed:single`) — never a
fidelity claim (THE MEASUREMENT DOCTRINE: a generated set's only confirmable thing is COUNT; never print rmse/cover).
SIZE = §PRIM measured class bbox. Every fixture lands in the ARC occupancy envelope (no void fixtures).
**Probe:** `window.__dwPixelProbe(disc)` (NEW in `modeller.html`) — A/B-isolates the `_dwRoot` disc-walk group (mirror of
`__arcPixelProbe`) so the MEP layer's own rasterized footprint is measured, immune to bg/grid/ARC.
**Witness `witness_disc_density.js` (W-DW-DENSITY-TE):** lay ARC → `dwInit(terminal_rules)` → walk+`_renderDiscWalk` each
disc. D1 RENDER each disc's fixtures added to the scene + in-frustum; D2 §READPIXELS `__dwPixelProbe` the MEP layer
rasterizes over the laid ARC (> noise); D3 ENVELOPE every placement inside an INDEPENDENTLY-recomputed ARC occupancy
envelope (no void fixtures); D4 COUNT — area-distributed discs same-order bounded vs oracle [0.3×,3×], ACMV+FP tight
≤20% (reported per disc); D5 PLB routed → `no-endpoints` honest refusal (chains=0, no fabricated network); D6 LABEL no
placement carries an rmse/fidelity field (prov ∈ generated set); D7 no LOAD_FAIL/pageerror. Findings (ELEC over-count)
listed not hidden (§8D). NON-INVENT falsifier: a disc absent from the rules → empty walk, zero fixtures.

### §8E-4 STAGE-2 CLASH (clash-ON) — the avoidance Δ on the laid ARC (2026-06-29) — ✅ DONE (W-DW-CLASH-TE 10/10)
Stage-1 walked each disc from placement rules ALONE; Stage-2 adds the clash bus. **`rule_avoidance` is NOT empty (the
spec §8B text was stale): it is already MINED** — 10 disc-pairs, `measured:terminal/global-p05` (min_clear + yields +
n_measured, the re-mine the DX-MEP session did, self-flag 37.5%→4.8%). So §8E-4 = the **Δ proof**, not a mine. The
gate (`disc_walker.gate`) already implements iterate-yield-into-plenum + flag-irreducible-RED reading those rules.
**The two-stage ablation (§8B), measured:** walk all 4 MEP discs into the laid ARC → ONE combined set. **Stage-1**
clash count = lower-priority placements within `min_clear` of a higher-priority one, on RAW positions (gate OFF).
**Stage-2** = run `gate()` (the lower-priority disc yields by dropping `min_clear`, iterated, never below the measured
floor) → residual = what STILL clashes (flagged RED, never silently cleaned). **GREEN ⇔ clashes DROP *and* intrinsic
integrity does NOT regress:** count unchanged (no fixture dropped), **xy unchanged** (the gate only moves z → the ARC
envelope is preserved), **z ≥ measured floor** (no below-grade fabrication), residual flagged not hidden. Report the
Δ-table: `clashes ↓`, `count Δ=0`, `xy-drift=0`, `yields`, `residual flagged`, `iterations`.
**The oracle (§8B "matches the engineer"):** position is GENERATED so there is no per-element NN-to-oracle (the
measurement doctrine) — instead the avoidance is judged by the **residual clash RATE vs the real Terminal's own
rule-admitted tail**: apply the SAME `min_clear` to a sample of REAL Terminal MEP positions = the engineer's coordinated
baseline (a p05 rule admits ~5% by construction). Stage-2's residual rate must be **≤ that baseline (no worse than the
engineer under the same rule)** — that is the defensible "our avoidance matches/beats theirs," not a position claim.
**Witness `witness_disc_clash.js` (W-DW-CLASH-TE):** S1 Stage-1 has clashes (something to resolve); S2 Stage-2 clashes
DROP ≥50%; S3 count Δ=0; S4 xy-drift=0 (envelope preserved); S5 z ≥ floor (no below-grade); S6 residual==flagged RED
(no-handwave); S7 only lower-priority disc moved (priority respected); S8 oracle — residual rate ≤ real-Terminal
rule-admitted rate; S9 §READPIXELS gated layout (incl RED) still rasterizes; S10 no LOAD_FAIL. Δ-table logged (§8B
interim records). NON-INVENT: the only motion is z by a MEASURED `min_clear`; nothing teleports, nothing buried.
**REMAINING after §8E-4:** ~~§8E-3 routed MEP network ⛔~~ ✅ UNBLOCKED 2026-06-29 (W-WALKBACK-MEP 7/7 on real MEP-bearing
substrate); §8E-5 GREEN report + red-on-theirs over the whole TE walk; DEFERRED 261MB plate range-stream.

### §8E-5 GREEN REPORT — the acceptance artifact over the whole TE walk (2026-06-29) — ✅ DONE (W-GREEN-REPORT-TE)
The §8D capstone: not "we reproduced TE" but a layout where **every walked layer = produced-how + cited bar + measured
deviation + calibrated confidence + verdict**, with gaps listed and the **red-on-theirs** audit. ONE harness runs the
WHOLE TE walk (ARC drop → STR skeleton → canopy → 4 MEP discs → gate) in one scene, collects each layer's already-proven
metric, runs the as-built guards, and EMITS an inspectable artifact `modeller/tests/TE_GREEN_REPORT.md`.
**Per-layer roll-up (the §8A bars, measured):** ARC = EXACT 0.000 (dropped verbatim, W-ARC-MESH-READPIXELS); STR
skeleton = TIGHT (column RMSE 0.104m, girders on-grid + 8 over-span flagged RED, W-STR-INTO-ARC); canopy = TOLERANCE
(count 1.38%, W-STR-CANOPY); MEP density = TOLERANCE (ACMV +7% / FP +13% tight, ELEC over-count FINDING, PLB routed ⛔
honest, W-DW-DENSITY-TE); clash = RESOLVED (170→3, residual 0.06% ≤ engineer 2.17%, W-DW-CLASH-TE). Calibrated
confidence cited from the shipped Terminal isotonic map (W-CONFIDENCE-CALIBRATED, ECE 0.034).
**Red-on-theirs (auditing the as-built, the defensible "more correct"):** run the SAME clash scan over the REAL Terminal
MEP — a spatial-hash cross-discipline nn finds element pairs PHYSICALLY interfering (centre separation < a HARD
threshold, e.g. 0.10m) = real coordination clashes the as-built CONTAINS (consistent with `rule_avoidance` raw_min as
low as 0.013m). green-on-ours (gate residual 0.06%) + RED-on-theirs (≥1 real hard clash) = auditing, not bragging.
**Witness `witness_green_report.js` (W-GREEN-REPORT-TE):** GR1 report covers every walked layer (ARC/STR/canopy/MEP/
clash); GR2 each layer verdict TRACES to its measured number (no claim without a figure); GR3 red-on-theirs ≥1 real hard
clash in the as-built (the audit lands); GR4 gaps LISTED not hidden (PLB ⛔→since UNBLOCKED §8E-3/W-WALKBACK-MEP, ELEC
over-count, 261MB deferred); GR5 confidence criterion present (ECE cited); GR6 artifact written + non-empty; GR7 no LOAD_FAIL. The report
is the deliverable; the witness proves it is well-formed and honest. NON-INVENT: every number comes from a live run.
