# SPEC — bonsai_kernel_worker.js: harden GEOM_GRID_MOVE's SCALE fold against a rotated shape (defensive, dormant-on-dormant)

```
# ⚠ DO NOT REMOVE
SCOPE: EXACTLY these 2 files — modeller/bonsai_kernel_worker.js, one new modeller/tests/witness_grid_scale_yaw_hardening.js.
Sibling/follow-on to prompts/GRID_ROTATION_GUARD.md (DONE, unpushed, /tmp/wt-grid-rotation-guard commit 0f8786d) —
read that file first, this one assumes its §0/§1 findings. TIER: Fable-suitable (mechanical, well-specified,
low regression risk) — NOT the harder open question named in §3 below, which stays a Sonnet/user design
conversation, not an execution task for anyone yet.
```

## §0 — What this is, and what it deliberately is NOT (read before writing a line)

**The investigation that produced this spec (2026-07-10, Sonnet, verified against live `~/bim-ootb/modeller`
source, not assumed):** `GEOM_GRID_MOVE`'s `SCALE` branch in `bonsai_kernel_worker.js` (~line 478-497) builds
a WORLD-axis-aligned non-uniform-scale matrix —

```js
const M = c.axis === 'x' ? [f,0,0,tx, 0,1,0,0, 0,0,1,0]
        : c.axis === 'y' ? [1,0,0,0, 0,f,0,tx, 0,0,1,0]
        :                  [1,0,0,0, 0,1,0,0, 0,0,f,tx];
out = kernel.generalTransform(pe.shape, M);
```

— and applies it directly to the shape's WORLD-space B-rep, regardless of the element's own yaw. For a wall
rotated off a 90°-multiple, a world-axis stretch does not correspond to any local axis of the wall (its own
length/thickness directions) — it SHEARS the solid instead of stretching it cleanly along its own run. **This
is a real, correctly-diagnosed defect in the math**, not a hypothetical.

**But: verified, not assumed — this code path is CURRENTLY UNREACHABLE for any rotated element, and will STAY
unreachable after this task, by design:**
- `SCALE` commands only originate from 2 sites in `grid_kinematics.js`: the ATTACH/EDGE-driven horizontal scale
  (~line 508, `axis` = the classification axis, x or y in-plan) and the `WALL_HEIGHT_SCALE` cascade (~line 556,
  `axis: 'y'` = WORLD-VERTICAL, orthogonal to any Z-axis yaw — height-scale is yaw-safe by construction, not a
  concern here).
- The horizontal one is reached ONLY via `_findBestGrid`'s ATTACH/EDGE/SPAN classification — and
  `GRID_ROTATION_GUARD.md`'s already-built (unpushed) fix makes that classification SKIP any element whose
  `yawRad` is off a 90°-multiple, falling it through to `_classifyInterior`'s bay-proportional path instead.
- `_classifyInterior`/`_bayProportionalDelta` (~line 596-598) **only ever emits `action: 'TRANSLATE'`, never
  `SCALE`** — confirmed by direct grep of every `action:` literal in the file. Translation doesn't need
  local-axis correction (shifting a rotated wall by a world delta is geometrically correct regardless of its
  yaw — only non-uniform STRETCH needs the local frame).
- **Conclusion: today, and after the rotation-guard branch merges, a rotated element can structurally never
  reach the buggy `SCALE` matrix.** This is dormant-on-top-of-dormant — a real bug in code that a real,
  already-verified guard makes unreachable. Do not describe this task as "fixing an active bug" — it is
  defense in depth, matching the honesty standard `GRID_ROTATION_GUARD.md` itself set for its own dormancy claim.

**Why bother, then — this is not busywork, it's insurance:** the guard's own §4 correction already found ONE
expiration condition (the `§ARC-3AXIS` tilted-placement shape it doesn't read). If a FUTURE change ever lets a
rotated element reach `_findBestGrid`'s classification again (a bug, a new caller, a deliberate future feature —
see §3), this dead SCALE-matrix bug would silently reactivate with no guard of its own — a second, independent
line of defense at the fold layer costs little and closes that blast radius permanently, the same "insurance,
not urgency" framing the rotation guard itself used.

## §1 — THE FIX (fully designed, implement exactly this)

Add the SAME yaw-tolerance guard `bonsai_kernel_worker.js` already lacks, mirroring
`GRID_ROTATION_GUARD.md §1`'s tolerance constant (`0.01` radians, ~0.57° from the nearest 90°-multiple — reuse
this exact number for consistency, it is not arbitrary either place):

1. `GEOM_GRID_MOVE`'s command shape (`P.commands[i]`) gains an OPTIONAL field `yawRad` (radians), threaded
   from `bonsai_gridmove.js`'s existing `_buildClassByFid`-style pass (same `placement.rot` degrees→radians
   read the rotation guard already added there — reuse it, do not re-query `kernel_ops` a second time).
2. In the `SCALE` branch, BEFORE building `M`: if `c.yawRad` is defined and its distance to the nearest
   multiple of `Math.PI/2` exceeds `0.01` — do NOT build the naive world-axis matrix. Compose the FULL
   rotate→scale→rotate-back transform as ONE combined 3x4 matrix instead (R(yawRad) · S_local · R(-yawRad),
   expressed as the same 12-number `generalTransform` format) and call `kernel.generalTransform` exactly ONCE
   with the combined matrix — **do not chain 3 separate kernel calls** (rotate, then scale, then rotate back)
   even though that looks simpler; the file's own `GEOM_SCALE` comment (~line 553-561) already documents a
   real `generalTransform`-aliasing hazard on Copy=false shapes when a solid is transformed more than once in
   sequence — a single composed-matrix call sidesteps that hazard entirely rather than reproducing it.
3. If `c.yawRad` is undefined (every existing caller/fixture, and every caller until §3 is separately
   designed) — behavior is BYTE-IDENTICAL to today. This is purely additive insurance, never a behavior change
   for anything reachable today.

**Verify, don't assume, before implementing:** confirm `generalTransform`'s 12-number input is read as the
row-major 3×4 affine matrix the existing unrotated branches already assume (the existing code's own 3 literal
matrices are the ground truth for the format — match their convention exactly, do not re-derive from generic
OCCT docs).

## §2 — GUARDRAILS

- File allowlist absolute: `modeller/bonsai_kernel_worker.js`, `modeller/tests/witness_grid_scale_yaw_hardening.js`.
  Do not touch `grid_kinematics.js` or `bonsai_gridmove.js` beyond the one additive `yawRad` thread-through in
  §1.1 (and even that only if it isn't already present after `GRID_ROTATION_GUARD.md` lands — check first).
- No browser, no puppeteer, no e2e. Pure node, same style as `witness_grid_kinematics_pure.js` (hand-computed
  fixture, exact numbers worked out in comments before the assertion).
- No push, no PR, no merge, no deploy. Commit locally on a fresh branch off current `origin/main`, stop.
- Do not attempt §3 below under this task. If implementing this reveals a THIRD `SCALE`-emission site not
  named in §0, or any other axis-alignment assumption nearby that looks wrong — name it in the final report,
  do not fix it here. Scope creep here defeats the point of a narrow insurance patch.
- STOP AND REPORT (do not improvise past): if `generalTransform`'s matrix convention doesn't match what §1
  assumes when actually tested, or if the composed single-matrix result doesn't match a hand-computed
  rotate-scale-rotate applied as 3 sequential (verification-only, not shipped) transforms within floating-point
  tolerance — stop, report the exact discrepancy, do not force a witness green.

## §3 — THE GENUINELY OPEN QUESTION (NOT a task, do not execute — Sonnet/user design conversation only)

Named here so it isn't lost, per this project's own "name the follow-up, don't fix it under the wrong task"
discipline: **should an oblique (non-90°-aligned) element ever be allowed to ATTACH/EDGE/SPAN to an
axis-aligned grid line at all?** Tentative read from this investigation, NOT a decided answer: ATTACH/EDGE
semantics mean "this element's own straight edge runs along this grid line" — which is geometrically
ill-defined for a wall whose edge is not parallel to any world axis the grid runs on. Under that read, the
rotation guard's "skip to interior/bay-proportional" behavior may be the CORRECT permanent answer, not a
stopgap — in which case §1's hardening is genuinely just insurance, not a stepping-stone to a bigger feature.
The alternative (a real feature, not yet requested or specced anywhere in this project): **oblique grids** —
letting a grid axis itself run at a building's own oblique wing angle, so elements parallel to THAT axis can
validly ATTACH/EDGE/SPAN along it. That is a materially bigger authoring-tooling feature (new grid-definition
UI, a coordinate-frame concept per grid-family, cascading into `grid_kinematics.js`'s classification from the
ground up) — genuinely "hard," genuinely a breakthrough if built, and genuinely not scoped by anyone yet.
**Do not start building this. Bring it back for a scoping conversation before anything is written.**

## §4 — ✅ DONE (2026-07-10) — built + witnessed; MERGED 2026-07-10 as bim-ootb PR #721 (`c32692e`)

PR #721 merged directly into `origin/main` (`c32692e`) — verified via `gh pr view 721` (`state: MERGED`) and
`git log origin/main`, not just the PR author's report. **⚠ This task's own dormancy claim (§0) rests
entirely on `GRID_ROTATION_GUARD.md`'s guard actually blocking oblique elements from reaching `_findBestGrid`'s
classification — and that guard's own dormancy premise has since been found FALSIFIED on current shipped
data** (293 real tilted `SampleCastle_ARC.db` elements now exist post-embed-8-merge, vs. 0 when both guards
were authored/witnessed — see `GRID_ROTATION_GUARD.md`'s TRIGGERED correction, same date). Same open
verification task applies here transitively: confirm whether any of those 293 elements are actually
reachable via grid-drag classification before treating either PR's dormancy claim as still current.

Built in `/tmp/wt-grid-scale-yaw` (branch `fix/grid-scale-yaw-hardening`, off fresh `origin/main` — which now
INCLUDES the merged `feat/embed-8-arc-buildings`), commit `cdc4010`. Allowlist held: `bonsai_kernel_worker.js`
+ new `tests/witness_grid_scale_yaw_hardening.js` + the §1.1-only thread-through in `bonsai_gridmove.js`
(whose `_buildInsertMaps` hunk is BYTE-IDENTICAL to the unpushed `fix/grid-rotation-guard` branch's, so the
two branches merge cleanly in either order). **W-GRID-SCALE-YAW-HARDENING 21/21** (real worker file + real
occt-wasm in pure node, `witness_gridmove_fold_pure.mjs`'s harness technique; no-shear proven by matching
every tessellated vertex against 8 hand-computed corners of the cleanly-stretched 30°-rotated wall — AABB
alone can't distinguish shear from stretch). Regressions all green, logs read: W-GRIDKINEMATICS-PURE 98/98,
W-GRIDMOVE-ADAPTER-FAKES 4/4, W-GRIDKINEMATICS-REAL-DUPLEX 8/8, W-GRIDMOVE-FOLD-PURE 12/12.

§1's pre-flight verifications both held: `generalTransform` IS row-major 3×4 (`lib/kernel/index.js`'s own
docstring: `[r00,r01,r02,tx, r10,r11,r12,ty, r20,r21,r22,tz]`), and the composed single matrix matches the
3-step rotate→scale→rotate-back to 8.9e-16 (witness A9) — neither §2 stop condition fired. Design choices
made where §1 was silent, documented in the code: anchor = world-bbox CENTRE (the file's own `GEOM_ROTATE`
rotated-op convention; the naive branch's min-EDGE anchor is a world-AABB construct with no local analog
readable from a world AABB), `translateDelta` stays a WORLD-axis shift (same semantics as TRANSLATE's
world delta, which §0 itself notes is yaw-correct), and the guard engages only on IN-PLAN axes ('x'/'y') —
an axis-'z' stretch is yaw-safe as-is (yaw is about Z in the worker frame, verified: extrudes sweep
`(0,0,depth)`, `GEOM_ROTATE` spins about `{0,0,1}`) so it stays byte-naive even with oblique yawRad.

**Named per §2, NOT fixed here (report-only):**
1. **A SECOND live SCALE fold site exists outside §0's list: `bonsai_library.js` `foldInsert` §STRETCH-1
   (~line 486-491)** — inserts skip the worker fold entirely (`solids.get` miss → `continue`) and get their
   GRID_MOVE commands applied HOST-side to world-space positions with the SAME world-min-anchored world-axis
   scale. For an insert with oblique `placement.rot`, that is the SAME shear defect — in the one fold path
   that actually processes the elements that HAVE `placement.rot`. Unguarded by this task (file out of
   allowlist); candidate sibling follow-up.
2. **Coverage gap in the §1.1 thread itself (spec-shaped, not a deviation):** the worker's SCALE branch only
   ever folds AUTHORED B-rep solids, whose yaw comes from `GEOM_ROTATE` ops — the `placement.rot`-sourced
   map has entries only for GEOM_INSERT fids, which that branch never processes. So the threaded `yawRad`
   is `undefined` for exactly the solids the worker folds today; the kernel-side guard is the real insurance
   (any future caller passing `c.yawRad` gets correct math). Full coverage would accumulate net yaw from
   `GEOM_ROTATE` ops per fid — a design question (compose with §1's map? what about multiple rotates?), not
   done under this narrow task.
3. Spec nit for the record: §0's "axis:'y' = WORLD-VERTICAL" reading of `WALL_HEIGHT_SCALE` doesn't match
   the worker frame (Z-up; 'y' is in-plan there). Harmless either way: the guard now covers 'y' as an
   in-plan axis, and 'z' (the actual vertical) is yaw-safe by commutation — the "height-scale is safe"
   conclusion survives under both readings.

Not pushed, no PR — stays local until the Watchdog reviews (it also owns reconciling this with the sibling
`fix/grid-rotation-guard` branch, same review pass).

## §5 — ⚠ VERIFIED LIVE, 2026-07-10 — this task's own dormancy premise (§0) is FALSIFIED on real shipped data

Per the transitively-applicable verification named in this file's own TRIGGERED block: a real headless-Chrome
session against the live shipped `SampleCastle_ARC.db` (full details in `GRID_ROTATION_GUARD.md` §5) found
**3 of the 293 tilted elements DO reach `_findBestGrid` ATTACH classification today** (fids 933/1291/2514), plus
a 4th confirmed reachable on one gridline placement — `yawSkipCount=0` throughout, the rotation-guard never
engages (`yawRadByFid` undefined for all of them, the §ARC-3AXIS placement-shape gap). Since §0's "dormant"
claim rests entirely on the rotation-guard actually blocking these elements from classification, and it does
not, this file's SCALE-fold hardening is (by its own §0 logic) no longer proven unreachable either — an ATTACH
hit on ANY of these elements is one step from a horizontal `SCALE` command if that wall/railing is later
stretched via grid-drag. **Mitigating factor found by the same probe:** `rotation_x`/`rotation_y` are not
currently wired into the rendered mesh transform at all (`mesh.matrixWorld` identity for every sampled
element) — so even if `GEOM_GRID_MOVE`'s SCALE branch fires for one of these fids today, the shape it folds is
NOT actually rendered-tilted, so the shear defect this task's §1 guards against has no live geometry to act on
yet either. Both this file's insurance AND its "unreachable" framing are therefore in the same state: not
actively producing wrong output today, but for a coincidental reason (render-layer tilt not wired up) neither
guard was built to rely on. **Not fixed here — report-only, same fix decision named in `GRID_ROTATION_GUARD.md`
§5:** whether to extend guard coverage to the roll/pitch axes, or to fix the render-layer tilt gap first (which
would make the current guards' Z-yaw-only scope newly load-bearing in a way it isn't today).

## §6 — ✅ DONE (2026-07-10) — SCALE fold now refuses on tilt too; see `GRID_ROTATION_GUARD.md` §6 for full detail

Decision taken: extend coverage to the roll/pitch axes (not fix the render-layer gap, which stays a separate,
bigger, un-scoped task). Built alongside `GRID_ROTATION_GUARD.md`'s §6 fix, same commit (`98cf709`,
`/tmp/wt-grid-tilt-guard`, branch `fix/grid-tilt-guard`) — this file's own allowlisted `bonsai_kernel_worker.js`
gained the mirror fix: `GEOM_GRID_MOVE`'s SCALE branch now REFUSES (leaves the shape unchanged, no shear, no
scale) when a command carries a non-negligible `tiltXRad`/`tiltYRad`, threaded from the same `_buildInsertMaps()`
read this file's §1.1 already established. No composed rotate-scale-rotate for X/Y tilt was attempted — that
stays the genuinely open, un-scoped question (this file's own §3, still not touched).

**Witness: W-GRID-TILT-GUARD 28/28** (shared with `GRID_ROTATION_GUARD.md` §6) includes a real-occt-wasm check
(W1-W3) proving the worker SCALE branch refuses cleanly on `tiltYRad=π/2` (output byte-identical to the
unscaled fixture), stays byte-identical when tilt is undefined, and does not over-fire below the 0.01 rad
tolerance. `W-GRID-SCALE-YAW-HARDENING` re-run clean at its pre-existing 19/21 (2 failures confirmed present
BEFORE this change too, on the unmodified merged code — not introduced, not touched, out of this task's scope).

Not pushed, no PR — stays local until the Watchdog reviews (same reconciliation pass as the sibling branches).

## §7 — ✅ WATCHDOG VERIFIED, 2026-07-10 — reproduced clean; PUSHED (not merged)

Full independent verification (diff, witness re-run, live-DB re-verification, regression stash-equivalent
check, and the axis-convention investigation) is logged in `GRID_ROTATION_GUARD.md` §7 — this file's own §6
fix (the `bonsai_kernel_worker.js` SCALE-fold REFUSE-on-tilt branch) shares the same commit (`98cf709`) and
same `W-GRID-TILT-GUARD` 28/28 witness (its W1-W3 assertions are specifically this file's SCALE-fold code:
confirmed real occt-wasm, output byte-identical to an unscaled fixture when tilt is refused). `git diff
origin/main..98cf709 -- modeller/bonsai_kernel_worker.js` re-read directly: the `tilted` check short-circuits
BEFORE `M` is built and before `kernel.generalTransform` is called (`out = pe.shape` no-op) — matches the
"refuse, no shear attempt" description exactly, no rotate-scale-rotate-back was attempted (correctly out of
scope per this file's own §3). `W-GRID-SCALE-YAW-HARDENING` reproduced at its own pre-existing 19/21 (2
failures), confirmed present identically on unmodified `origin/main` via a separate worktree at `98cf709^` —
not introduced by this or the tilt-guard commit. Pushed `fix/grid-tilt-guard` to `origin` (bim-ootb) —
no PR, no merge.

## §8 — ✅ VERIFIED (2026-07-10) — this file's own SCALE-fold needed NO code change; the 19/21 gap is now CLOSED

Full detail in `GRID_ROTATION_GUARD.md` §8 — that file's `_classifyElement` axis-scope fix (commit `c485560`,
same branch as `98cf709`) is the one that actually changed. This file's own allowlisted `bonsai_kernel_worker.js`
was independently re-derived, not assumed: traced where axis:'y' `GEOM_GRID_MOVE` `SCALE` commands actually
originate for the Modeller (the general EDGE/ATTACH `_computeScaleCommand` path, which passes through
whichever axis the classification fired on) and confirmed a real axis:'y' command IS a genuine horizontal
plan-axis stretch here — exactly the case this file's own yaw-guard already exists to protect. Checked via
`git show c32692e -- modeller/bonsai_kernel_worker.js` (the untouched PR #721 commit, before any of this
lineage's later fixes): the composed-matrix branch's condition was **already** `oblique && (c.axis === 'x' ||
c.axis === 'y')` from day one — correctly Z-up-aware ("yaw is about Z in this worker's frame," the file's own
inline comment), not the Y-up assumption this file's own §0 prose describes for the SEPARATE, unrelated
`WALL_HEIGHT_SCALE` cascade origin (which IS hardcoded `axis:'y'`-as-vertical, confirmed a real latent issue if
it ever fires — but confirmed dead code for the Modeller today, `grid_kinematics.js`'s `isRoof` can never be
true here). **Net: §0's own text was more careful than a first read suggests — it already called the general
horizontal-scale origin "x or y in-plan," and only the cascade-specific claim needed the caveat now recorded
in `grid_kinematics.js`. Zero lines changed in this file's own `bonsai_kernel_worker.js` for this task.**

**This file's own pre-existing 19/21 gap in `W-GRID-SCALE-YAW-HARDENING` is now 21/21** — resolved as a side
effect of `GRID_ROTATION_GUARD.md`'s axis-scope fix, not touched directly here. Root cause was the OTHER
file's bug (an obliquely-yawed wall wrongly ATTACHing on y, marking it "governed" and starving it of the
bay-proportional fallback it needed) — this file's own SCALE-fold code was never the problem. Full regression:
206/206 across all 8 witnesses (see `GRID_ROTATION_GUARD.md` §8 for the complete list).

Not pushed, no PR — stays local on `fix/grid-tilt-guard` (commit `c485560`) until the Watchdog reviews.

## §9 — ✅ WATCHDOG VERIFIED, 2026-07-10 — `c485560`'s "no code change needed" claim confirmed correct; PUSHED

Full independent re-derivation logged in `GRID_ROTATION_GUARD.md` §9 (diff read in full, R6/G1/G4/G5 flip
reasoned from first principles, live-DB numbers reproduced, causal chain for the 19/21→21/21 bonus fix traced
line-by-line through `dragGrid`/`_classifyElement`/`_classifyInterior`, full 206/206 regression re-run with logs
read). For this file specifically: confirmed with my own eyes (not by trusting the commit message's citation)
that `git show c32692e -- modeller/bonsai_kernel_worker.js` shows the composed-matrix branch's
`oblique && (c.axis === 'x' || c.axis === 'y')` condition present, unchanged, since PR #721's original merge —
`c485560` genuinely makes zero changes to this file, confirmed both by `git show --stat c485560` (file absent
from the changed-file list) and by reading the SCALE branch directly. §8's claim stands as verified, not merely
asserted. Also independently reproduced the 19/21→21/21 transition's causal mechanism (not just the pass count):
swapped in the pre-fix `grid_kinematics.js`, reproduced 19/21, isolated the failure to `B2`'s `fid1` (a
30°-yawed wall whose y-center 0.1 sits inside `ATTACH_TOL` of a y-gridline at 0), traced why the spurious
pre-fix y-ATTACH silently zeroed its drag command, and confirmed the post-fix code correctly produces a
bay-proportional TRANSLATE for it instead. Pushed `fix/grid-tilt-guard` (c485560) to origin/bim-ootb — no PR,
no merge, per instruction.
