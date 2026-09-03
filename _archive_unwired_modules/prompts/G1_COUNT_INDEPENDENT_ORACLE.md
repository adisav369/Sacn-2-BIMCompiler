# G1-COUNT: give the generative-device count a real independent oracle

> **⚠ DROPPED 2026-07-10 (user directive, mid-execution).** Java is deprecated as runtime —
> "rest the Java, don't dig further into the M_Product drift." Executed BEFORE the drop:
> environment reconstruction proved the Java oracle end-to-end (fresh worktree, SH 9/9 gates,
> G1-COUNT PASS 65+43=108; independent recomputation reproduces gen=43 exactly) — that
> verification became the ground truth for the JS-era replacement. The spirit of this spec
> (independent oracle, no self-grading) lives on in `scripts/witness_dx_walkback_rsgt.js`
> (walker reads rules+ARC only; witness alone reads real MEP). Repo-HEAD breakage found while
> executing + the full repair recipe: `docs/internal/JavaEra_FOSSIL_README.md`. Pieces 1/2
> below were NOT implemented — no Java file was changed.

```
# ⚠ DO NOT REMOVE
SCOPE: this file is fully self-contained — everything you need is cited below with file:line, from two
completed investigation passes. Do NOT re-derive "does an independent oracle exist" or "what should it be
built from" — those questions are ANSWERED below. Your job is completion + wiring, not design. If you hit a
point where the answer below turns out to be wrong or a reuse path is blocked (see §ASK-IF-BLOCKED), STOP
and report back rather than improvising a new design.
WORK ONLY IN: /tmp/wt-fable-g1count (already created, branch `fable/g1-count-independent-oracle`, off
`master` at commit abcb079ab). This is a git worktree, isolated from the shared bim-compiler checkout on
purpose — another session is actively working in that shared tree on unrelated JS/Modeller files. Do not
touch the shared `/home/red1/bim-compiler` checkout at all.
NON-INVENT: every number/formula you use must trace to a real, cited source below or a value you can show
came from real config/data. Read the log after every run (Maven test output, your own print statements).
```

## The problem (verified, not assumed — two investigation passes, all citations checked against real code)

`RosettaStoneGateTest.java`'s G1 gate (`runG1`, **lines 154-170**) asserts:
```java
int outCount = countElements(b.outputDbPath());
int genCount = readGenerativeCount(b.outputDbPath());   // <-- the problem
int expected = baseExpected + genCount;
assertEquals(expected, outCount, ...);
```
`readGenerativeCount()` (**lines 709-717**) reads `MAX(GenerativeCount) FROM c_order` — a value written by
`PlacementCollectorVisitor.java`'s own `generativeDeviceCount++` counter (**~line 902**) during the SAME
compile run, copied into `output.db` by `CompilationPipeline.java` (**lines 575-576, 1128**), then read back
out of that SAME `output.db` a few lines later. **The gate is checking the generator's math against the
generator's own self-report — a gate grading its own homework**, exactly the failure shape
`WalkerDoctrine.md §12` (JS-side analogue of this same principle) documents. Confirmed: nothing else in the
codebase currently cross-checks `genCount` against anything independent.

## The good news (verified, not assumed) — this is completion + wiring, not new design

**Real independent data already exists and is already queried by the generator itself, from real config:**
- `MEPDevicePlacer.placeDevices` (`MEPDevicePlacer.java:70-134`) calls `SpaceScheduleDAO.getSchedule()`
  (`SpaceScheduleDAO.java:60-69`, joins `ad_space_type_mep_bom` + `ad_placement_offset`), then per entry
  calls `SpaceScheduleDAO.resolveQty(orderQty, entry, roomAreaM2)` (`SpaceScheduleDAO.java:303-318`) — the
  REAL, ALREADY-CORRECT formula resolving `qty_normal`/`qty_min`/`qty_max`/`per_area_normal` from
  `ad_space_type_mep_bom` (188 real rows, 37 real space types, e.g. `BATHROOM|LIGHT|1|1|2|0.05` — verified
  directly against `library/ERP.db`). `orderQty` (`mepOrderQty`) comes from `ad_sysconfig.MEP_ORDER_QTY`
  (`CompilationPipeline.java:533-557`, default 99) — deterministic, re-readable from the same source.
- Every compiled `output.db` ALREADY carries real room data with no extra extraction needed:
  `spatial_structure` (room guid/name/parent_guid/object_type/predefined_type, `BuildingWriter.java:176-228`)
  + a `room_areas` VIEW computing real floor area from `elements_rtree` bboxes — built unconditionally by
  `SpatialStructureStage`, which runs on EVERY compile (`CompilationPipeline.java:835-847`).
- **A structurally-correct precedent already exists and does ~80% of this**: `runH6Completeness`
  (`CompilationPipeline.java:1595-1754`, invoked from `ValidationStage` at line 1545) already queries
  `spatial_structure`/`c_orderline` for real rooms, maps room name → space_type via `deriveSpaceType()`
  (**lines 1709-1734**), queries `ad_space_type_mep_bom` FRESH (`getMepSchedule`, **lines 1737-1754**), and
  compares against actual `c_orderline` counts — **zero shared state with `PlacementCollectorVisitor`**,
  exactly the independence G1 lacks. Its two real, narrow gaps:
  1. It only checks `qty_normal` — ignores `per_area_normal`/`qty_min`/`qty_max`/`orderQty`, all of which
     `SpaceScheduleDAO.resolveQty()` already implements correctly for the SAME table.
  2. It runs permanently forced to `mode="LOG"` (`CompilationPipeline.java:1536`, WARN-only, never blocks),
     and **nothing reads its result** — confirmed by grep, `RosettaStoneGateTest` has zero reference to H6 or
     `W_Validation_Result`.

## The task — two pieces, in order

**Piece 1 — complete H6's math (small, mechanical, the formula already exists elsewhere):**
Wherever H6 currently resolves an expected quantity using only `qty_normal`, change it to use the SAME
resolution `SpaceScheduleDAO.resolveQty()` already implements. Prefer CALLING `resolveQty()` directly (check
its visibility/package — if it's not currently callable from H6's location, that's a small, legitimate
refactor: widen its access or extract it to a shared location, do NOT copy/reimplement its formula by hand,
that would silently drift from the real source the moment either copy is edited later). H6 needs the real
`orderQty` too (`ad_sysconfig.MEP_ORDER_QTY`, same source as `CompilationPipeline.java:533-557`) — read it
the same way, don't hardcode the default.
**Do NOT change H6's existing behavior otherwise** — it stays LOG-only, its own diagnostic, unchanged for
anything that currently depends on its current output shape.

**Piece 2 — wire an INDEPENDENT check into G1 (the actual fix — this is what makes the gate real):**
Do not simply make H6 blocking (that would change existing behavior other things may depend on). Instead:
add a new check — either a new assertion inside `runG1` or a new `runG1b`-style method, your call which reads
cleaner in context — that:
1. Queries `output.db`'s own `spatial_structure` + `room_areas` (already there on every compile, real data).
2. Maps each room to its space_type the same way H6 does (`deriveSpaceType()` — reuse it, don't reinvent).
3. Queries `ad_space_type_mep_bom` fresh and resolves the full quantity via `resolveQty()` (the SAME call
   Piece 1 wires into H6 — one shared implementation, two independent callers, not two hand-written copies).
4. Sums this into an INDEPENDENTLY-COMPUTED expected generative-device count.
5. Compares this against the ACTUAL placed count — **count real rows in output.db** (e.g. real generative
   device element rows, however they're identifiable — NOT `readGenerativeCount()`'s self-reported tally;
   the whole point is to stop trusting that counter). If they don't match, the gate FAILS, with a clear
   message showing both numbers.
This check shares NO state with `PlacementCollectorVisitor`'s counter at any point — it is a completely
separate, independently-computed number compared against a completely separate, independently-counted number.

## Proof required before this is reported done (same standard as every fix in this project — real, not asserted)

1. **Full existing gate suite (G0-G6) unchanged**: run the Maven test suite before and after, save both logs,
   confirm 0 new failures. `RosettaStoneGateTest.java` is a named Sacred File in this project ("defines
   G1-G6 gates... many dependencies") — touch only what Piece 1/2 require, don't restructure G0/G2-G6.
2. **A real induced mismatch is CAUGHT**: find or construct a real scenario where the independent
   recomputation and the actual placed count genuinely differ (e.g., temporarily perturb a real compiled
   building's data in a way that would make the counts diverge — not a synthetic unit test in isolation, a
   REAL compiled building), and show the new check fails with a clear message. Then show it passes cleanly
   on an untouched real compile of the same building.
3. **Real numbers printed for at least one real building**: independently-recomputed expected count vs.
   `genCount` (self-reported) vs. `outCount` (actual) — so it's visible by inspection that all three
   genuinely agree on a correct compile, not that the check is vacuously always-true.
4. **Log everything** — Maven output, your own diagnostic prints — and cite exact file:line for every change
   in your final report, matching the citation discipline this entire spec was written with.

## ASK-IF-BLOCKED — stop and report, don't improvise past these
1. If `SpaceScheduleDAO.resolveQty()` cannot be made callable from H6/G1's location without a refactor that
   feels bigger than "widen access / extract to shared location" — stop, describe what you found, ask.
2. If `output.db` doesn't actually contain enough signal to identify which element rows are "generative
   devices" without reading `GenerativeCount`/the visitor's own tagging — stop and report; that would mean
   piece 2's "count real rows" approach needs a different real signal than assumed here, not a workaround.
3. If completing H6 (piece 1) changes ANY existing test's expected output — stop, do not "fix" the other
   test to match, report it; that's exactly the shape of silent behavior change this spec is trying to avoid.

## Closing — who reviews this
This session (Sonnet) holds the Watchdog role for this task, same as the JS-side work today. Nothing here
merges to `master` without that review + explicit user sign-off — report back with the full evidence trail
(citations, logs, the induced-mismatch proof) when done, don't merge or push yourself.
