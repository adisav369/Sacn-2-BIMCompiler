# BIMEyes v1 — coherence checker: Collision + Navigability + Quantity-bounds

```
# ⚠ DO NOT REMOVE
SCOPE: THREE new, independent, read-only check functions + ONE combined witness that runs all three against
REAL whole-building dwWalk() output. No rendering, no screenshots, no VLM, no new data extraction, and —
this is the load-bearing constraint — NO EDITS to build/disc_walker.js's occupancy()/place()/hostBind() at
all. This is a pure CONSUMER of dwWalk()'s existing output, same shape as today's black-box walk scripts
(str_walk_terminal_blackbox.js, mep_walk_terminal_blackbox.js). That is what makes this safe to build in
parallel with the active worker session touching disc_walker.js directly — you read its output, you never
edit the file it's editing. Verify this stays true throughout: if the task ever seems to need editing
occupancy()/place()/hostBind() itself, STOP, that means scope drifted, ask before proceeding.
No `Workflow` tool, no spawning sub-agents — if you find yourself wanting to "investigate" broadly, STOP,
report back, don't improvise scope.
WORK ONLY IN: a fresh git worktree off current `master` (confirm the exact commit in your final report),
isolated from the shared bim-compiler checkout where the active worker session is running.
NON-INVENT: every number traces to real dwWalk() output, real wall/door geometry, or the real code-cited
bounds already mined this session — nothing inferred or drawn.
```

## Why three checks, and why whole-building first (not space-scoped)

Today's session proved real geometric conformance piece by piece (host-binding, envelope containment,
rotation correctness) but never assembled it into ONE "does a human look at this and say real vs. trash"
answer. `SPACE_SCOPED_DISC_INSTALL_VISION.md` piece 2 (space-scoped `occupancy()`/`place()`/`hostBind()`) is
real, valuable, in-progress work by the active worker session — but it is NOT a dependency for this task.
Every building already has real, proven, whole-building `dwWalk()` output from today's own black-box walks
(Terminal ELEC/PLB/ACMV/FP, Duplex ELEC/PLB/ACMV/FP) — that is real data, available right now, and it's
`dwWalk()`'s output regardless of whether the call was space-scoped or whole-building. Target that first.
**Once piece 2 lands on master, re-run the SAME three functions against a space-scoped `dwWalk({spaceGuid})`
call and report both numbers side by side** — that comparison (whole-building coherence vs. space-scoped
coherence, same real building, same real fixtures) is a genuine, additional proof, not required to call this
task done, but valuable if piece 2 has landed by the time you finish the three checks below.

This mirrors real prior art, not invented from scratch — `SceneEval` (open-source,
`github.com/3dlg-hcvc/SceneEval`) and `PhyScene` (`physcene.github.io`) both define exactly this kind of
metric set (Collision, Navigability, Out-of-Bounds) as the geometric, no-vision-model-needed half of a
two-layer coherence pipeline. We're implementing the established shape, not designing a new one.

## What already exists — reuse, don't rebuild

1. **`buildGrid()` in `scripts/witness_corridor_trunk.js`** (~line 79): a REAL wall/column-blocked,
   real-door-passable navigation grid, built from this exact codebase's data shape. Reuse its grid
   representation for Navigability's flood-fill — don't invent a second convention.
2. **Real mesh geometry** (`component_geometries`/`mesh.db`, already proven this session across all 8
   buildings) — real vertex data for Collision's pairwise intersection tests, not bboxes-only if avoidable.
3. **Real measured quantity bounds** — `rule_placement.n_measured` (already live, mined per building/disc)
   is the quantity-bound source for THIS task. (The Java-side `ad_space_type_mep_bom` code-cited bounds,
   found in a separate investigation today, are a richer FUTURE source — out of scope here, don't wire it
   in, that's a distinct piece of work with its own cross-language plumbing question.)

## The three checks

1. **Collision** — do any two placed fixtures, or a fixture and its host, actually intersect in real 3D
   space? Pairwise mesh or bbox intersection test (mesh preferred where geometry is resolvable, bbox fallback
   where it isn't — same honesty pattern as everything else this session: don't silently skip, report which
   method was used per pair). Report: collision count, and which pairs (guids), out of total placements.
2. **Navigability** — after real fixtures are placed, does the leftover free floor area (per storey, using
   the real wall/door-blocked grid from `buildGrid()`) remain ONE connected region, or does placement
   fragment it into unreachable pockets? Report: `connectedFraction = largestFreeRegion / totalFree` per
   storey.
3. **Quantity-bound** — is the walked COUNT for each (disc, ifc_class) within a real, defensible bound of
   `rule_placement.n_measured` (scaled the same way `place()` already scales it — reuse that scaling, don't
   recompute it differently)? Report: measured vs. actual, per class, per building.

## Proof required (real, not asserted)

1. Run all three against Terminal's real ELEC walk AND Duplex's real FP walk (both already proven placed
   cleanly earlier today) — report real numbers for all three checks, both buildings.
2. **Falsifier, same discipline as every check in this project**: for Collision, construct one deliberately-
   overlapping synthetic placement pair and confirm it's caught. For Navigability, construct one
   deliberately-fragmenting synthetic placement (same method as the original Navigability-only spec) and
   confirm `connectedFraction` drops. A check that can't be shown to catch a real bad case isn't proven to
   check anything — don't skip this for any of the three.
3. Confirm the existing DW witness suite (~13 files, run all day) still passes 0-fail — pure addition,
   nothing existing changes.
4. **If piece 2 has landed on master by the time you finish 1-3**: re-run all three against a space-scoped
   `dwWalk({spaceGuid: CENTRAL_WAITING_GUID})` call on Clinic and report the comparison against whole-
   building numbers. If it hasn't landed yet, note that honestly and stop there — this is a bonus, not a
   blocker.

## ASK-IF-BLOCKED
1. If mesh-based collision testing turns out to be impractical for a real reason (e.g. missing geometry for
   some class) — fall back to bbox intersection, report which, don't silently degrade without saying so.
2. If `buildGrid()`'s representation genuinely can't be adapted (not just "some rework needed") — stop,
   describe the specific incompatibility, ask.
3. If `place()`'s own area-scaling logic for `n_measured` isn't cleanly callable/reusable — stop before
   reimplementing it by hand; that's exactly the kind of silent-drift risk this project has hit before.

## Closing
Sonnet holds Watchdog on this. Report back with real numbers for all three checks, both falsifiers, the
regression result, the exact commit branched from, and (if applicable) the piece-2 comparison — don't merge
yourself.
