# Production Readiness Backlog

> Scoping session, 2026-09-07, **decisions resolved same day.** Updated 2026-09-08: D4→D1
> (licensed-data isolation, prove a real DeKH scan through the chain) is DONE, and it surfaced
> **A9** — compiled placement systematically wrong on every building — which is now ALSO
> **FIXED and verified on all 3 real buildings** (DeKH Building A 498/498, SampleHousePC
> 70/70, Sample House 59/59, all up from 0). Next up: item 2, multi-storey. This is the
> itemized answer to "when is
> this a production-ready tool" — not a calendar estimate (this project's own Prime Rule is
> "never invent a number without a real source," and a date here would be exactly that). What
> it is instead: every known gap, sized by what's actually knowable about it today. The four
> decisions that determine real sizing (`## Decisions` below) are RESOLVED; `## SEQUENCED
> SESSION PLAN` converts them (plus A9) into a concrete, ordered starting point per item —
> read that section first if you're picking this up fresh.
>
> Cross-reference: `CLAUDE.md`'s `## NEXT SESSION PLAN` section carries a condensed version of
> the sequenced plan for quick reference; this file is the full picture and the place to
> update when any item's status changes.

## Sizing rubric (grounded in this project's own history, not abstract T-shirt sizes)

| Size | Meaning | Real examples from this project |
|---|---|---|
| **S** | Single focused session, mechanism already understood, low rejection risk | The M_Product read/write-split fix, the UTF-8 console guard, the confidence partition — each landed in one session because the fix was concrete once found |
| **M** | Multiple sessions, bounded scope, needs a design menu + validation but the mechanism is known | The Phase 6 wall-recovery fixes (wall-starvation, round-budget exhaustion, multi-candidate accept) — several sessions, each measurable, none required inventing a new kind of approach |
| **L** | Investigation-required — the mechanism itself is contested or unknown; real risk some attempts fail | Openings and `IfcColumn` as they stand right now: **0-for-3 combined** across this session's honest attempts. Sizing "L" here is itself an estimate with real uncertainty, not a promise |
| **XL** | Not sizeable yet — requirements haven't been written down | Multi-storey detection, cross-floor stitching, generalization beyond the 4 tested scenes, all deployment/tooling items — none of these has ever had a design menu, so there's nothing to size |

---

## A. Core model accuracy (does the output represent the real building)

| # | Item | Size | Status |
|---|---|---|---|
| A1 | Openings (doors/windows), 0% recall on real scans | **L — priority 3, ONE bounded attempt** | 2 RGB extraction designs rejected on measured false-positive rate (~2% both). Priority + explicit stop condition DECIDED 2026-09-07 — see `SEQUENCED SESSION PLAN` §3. |
| A2 | `IfcColumn`, complete class gap (0/9, 0/7) | **L — priority 4, deferred** | Investigated 2026-09-07; found to be an extraction problem (no coherent segment exists to classify), not the classification problem it was picked as. 3 unimplemented options logged. Deferred behind items 1–3. |
| A3 | Wall-face reunification (a wall's inner/outer face treated as 2 elements) | **L — not prioritized this round** | One design (distance-based merge) implemented, measured, and **rejected** — harmful fusions were the majority outcome at every threshold tested. 2 untried signals logged: in-plane footprint match, empty-cavity check between faces. |
| A4 | Multi-storey detection (currently hardcoded to 1 storey per building) | **re-scoped 2026-09-07 — priority 2, real first step identified** | Investigated (not just labeled XL) — 3 specific front-end chokepoints found, 1 is a silent-wrong-answer landmine, and a real in-hand precedent (Building A's 2 separately-scanned floors) shrinks the realistic first step well below "auto-detect floors in one continuous scan." Full detail + the first concrete action in `SEQUENCED SESSION PLAN` §2. |
| A9 | ~~Compiled placement is systematically wrong on EVERY point-cloud building~~ **FIXED 2026-09-08, verified on all 3 real buildings.** | **DONE** | Flat-BOM-tree hypothesis FALSIFIED (compiling IFC-authored Sample House directly showed the identical `P-PARENT 0/59` failure despite real assembly nesting — not a point-cloud or nesting-depth problem). Real cause, traced through the write path: `c_orderline.dx/dy/dz` is written once at BOM Drop and never updated (confirmed: zero `UPDATE` statements touch those columns anywhere); comparing it against the real walked `element_transforms` positions for the same elements showed both live in one consistent MIN-CORNER-relative local frame, never offset by the root `BUILDING` node's own `dx/dy/dz`. `StructuralBomBuilder.java`'s own comments confirm the convention explicitly ("building origin (LBD corner) from all elements"; a child offset "always >= 0"). `BomTreeProver`'s old `computeWorldPosition` summed the root's real-world placement seed (used elsewhere in `CompilationPipeline` to seed the walk — a different coordinate frame) into every containment check, AND checked center-relative when the confirmed convention is corner-relative. Fix: `LEAF dx/dy/dz` checked as `[0, parent_extent]` against its immediate parent only, no accumulation; `computeWorldPosition` (wrong and unnecessary) removed. Verified twice: simulated in Python against real `c_orderline` data from all 3 buildings first (100% each), then via the actual JUnit test — DeKH Building A 498/498, SampleHousePC 70/70, Sample House 59/59, all up from 0. `P-SIBLING` confirmed unregressed (100% throughout). Two unrelated pre-existing issues found and left alone: Sample House's own `.bimcobol` script independently fails on a missing `ProjectName` column (confirmed present before this fix too); `BuildingRegistryTest`'s `-Dbom.db` is JVM-global while its `@TestFactory` runs one dynamic test per `GATE_SCOPE` building, so pointing it at one building's compile-db while other registered buildings are also in scope produces an unrelated element-count mismatch on their dynamic tests — pre-existing (matches how `run_RosettaStones.sh` has always invoked this test), not part of A9. |
| A5 | Cross-floor stitching (a wall spanning 2 independently-segmented floors can't unify) | **XL** | Known gap (Building A's last unmatched wall traced to exactly this). Related to but distinct from A4's first step (§2(a) doesn't attempt this); still unscoped. |
| A6 | Output-volume growth from `MULTI_CANDIDATE_K=5` (~4.8x more predicted elements at Building A's scale) | **M** | Real, honestly-reported trade-off from the round-budget fix. Candidate approaches named (consolidation in `merge_instances.py`, or a smaller K for cluttered scenes) but not designed. |
| A7 | Wall recovery ceiling (87–97%, not 100%, across 3 real buildings) | **DECIDED 2026-09-07 — CLOSED, not on the backlog** | Accepted as a legitimate ceiling; every remaining gap traced to occlusion/scoring edge cases, not pipeline bugs. Not pursuing further. |
| A8 | Phase 2 cluster-purity gap (30% of raw DBSCAN clusters mix points from >1 real element) | **M/L** | Real, unfixed, discovered while validating a later phase. Doesn't currently block anything measured, but it's real debt in the earliest stage of the pipeline. |

## B. Generalization (untested outside this project's own 4 scenes)

| # | Item | Size | Status |
|---|---|---|---|
| B1 | ~~Every accuracy number in this project comes from 4 real scenes...~~ **OFF THE NEAR-TERM BACKLOG — decision #2, 2026-09-07: DeKH already IS the target category (institutional/commercial, hospitals/offices).** Generalizing beyond it is explicitly deferred until that category is solid. | — | Was flagged as the single biggest hidden unknown; superseded by scoping the target deliberately rather than chasing breadth. Revisit only after the pilot bar (decision #1) is met within DeKH's own category. |
| B2 | No documented fallback when a scan has no RGB/intensity | **S/M** | The pipeline never required color before this session; nothing breaks without it today, but there's no explicit tested policy either. |
| B3 | Tilted scans not handled (`rotation_x`/`rotation_y` assumed ≈0) | **M** | Documented open item in the LAS/LAZ ingestion section. Not attempted. |
| B4 | Material thickness/depth not measurable from a single-sided scan | **not a bug — a physics limit** | Flagged explicitly so no future session tries to "fix" this with a geometric heuristic. Real fix needs either a second scan pass or a catalog-supplied nominal thickness (extract-or-compile-only still applies — never invent one). |

## C. Already decided — NOT gaps, don't re-open these

Confirmed via source, not assumption, back in the original schema-spec phase — listing them here so the scoping session doesn't accidentally treat settled decisions as open items:

- **Multi-layer wall/slab slicing** — zero Java consumers confirmed; v1 ships single-envelope geometry per element, same LOD tradeoff the IFC pipeline already makes.
- **Room/space segmentation** — optional for v1 per spec; confirmed live (not just in theory) that `ScopeBomBuilder` degrades gracefully without it.
- **MEP element classification** — explicitly deferred per the original roadmap; embedded/occluded MEP scans poorly by nature.
- **`rel_aggregates`, `rel_adjacency`, `datum_plane`, `rel_anchored`, `rel_spans`, `port_elements`, `port_connections`** — zero Java consumers; don't build a point-cloud equivalent until something in the kept pipeline actually asks for one.

## D. Infrastructure / pipeline debt

| # | Item | Size | Status |
|---|---|---|---|
| D1 | ~~No real DeKH scene has been through compile/gates since the M_Product fix~~ **DONE 2026-09-08 — and it surfaced A9.** A real DeKH scan (Building A 1st floor, 498 elements) went through populate → classify → all 18 QA gates clean, fully isolated. Compile itself ran for real too (see D4) — and failed a genuine internal check (`CHECK PLACEMENT`), now tracked as **A9**, priority 1. | **DONE (verification), A9 spun out as new priority-1 finding** | Also corrected a standing claim: `BuildingRegistryTest`'s `GATE_SCOPE` allowlist didn't include `RE_DKAP` OR `RE_SHPC` — compile had been silently SKIPPING (assumeTrue → exit 0, indistinguishable from a pass at the mvn-exit-code level), not passing. The earlier "SampleHousePC 9/9 green" claim from 2026-09-07 almost certainly never actually verified compile. `GATE_SCOPE` fixed (both added) so this can't recur silently. |
| D2 | `extractIFCtoDB.py --library` mode's stale pre-S168 `M_Product` assumption | **M** | Used by `bake_all_sandbox.sh` and `pipeline_library.sh` across many buildings; changing it needs its own session per `CLAUDE.md`. |
| D3 | `library/ERP.db` / `disc_patterns.db` rename half-applied on Windows | **S/M** | Two independent files exist on this machine; code reads `ERP.db`. Untangling is a contained task. |
| D4 | ~~Licensed-data isolation approach for running DeKH through the Java chain~~ **DONE 2026-09-08.** Added `--erp-db` (mirroring the existing `--comp-db`) to `IFCtoBOMMain`/`IFCtoBOMPipeline` — `ERP.db`'s path was hardcoded with no override until now, an incomplete isolation story. `populate`/`classify` now fully isolatable via scratch copies of both DBs. `compile` itself still has no CLI override (hardcoded in ~10 `DAGCompiler` files) — proven via the swap real-file→run→restore→verify-checksum pattern instead, which the backlog always named as an equally valid mechanism. Real tracked library verified byte-identical to HEAD after every run. | **DONE** | — |
| D5 | `library/schema_snapshot_component_library.sql` is stale | **S** | Declares `M_Product`, predates `Value`/`source_element_ref`. Regenerate or annotate. |

## E. Tooling / deployment maturity — every item here is XL until the decision below is made

| # | Item |
|---|---|
| E1 | No end-user CLI/UX — this is a developer running Python scripts by hand |
| E2 | No CI — only ad-hoc validation scripts (`validate_*.py`), which are real regression coverage but not automated |
| E3 | No packaging or deployment story |
| E4 | No documented scale ceiling beyond "the biggest thing tested was 437M raw points" |
| E5 | No systematic error-handling/graceful-degradation policy for pathological inputs |

---

## Decisions — RESOLVED 2026-09-07 (product owner)

1. **What does "production" mean operationally? DECIDED: pilot-ready for a real client's
   building.** Reliable enough to hand a real client a genuinely useful, mostly-correct BIM,
   with a human review/touch-up pass expected on the known-weak parts (openings, columns) —
   not zero human involvement, not "sell broadly, fully autonomous." This is the near-term bar
   for a first pilot, consistent with the project's own stated arc (company plan, DeKH
   validation, a planned Revit-plugin phase) — not a toy, not a fantasy of full autonomy.
2. **What building types/verticals? DECIDED: institutional/commercial only — hospitals,
   offices, the exact category DeKH already validates.** Residential, heritage, and industrial
   are explicitly out of scope for now. Don't chase generality before this one category is
   solid — that's how projects stall. **This means B1 (generalization beyond DeKH) drops off
   the near-term backlog** — DeKH already IS the target category; the open question is depth
   within it, not breadth across others.
3. **Is 100% wall recovery a real goal? DECIDED: no.** 87–97%, with every remaining gap traced
   to occlusion or genuine scan-coverage limits (not pipeline bugs), is accepted as a
   legitimate, honest ceiling. **A7 is closed — not on the backlog.** Effort belongs on
   openings/storeys/columns, which currently deliver zero, not on marginal wall percentage
   points.
4. **Priority among A1 (openings) / A2 (`IfcColumn`) / A4 (multi-storey)? DECIDED, and it
   overturns the assumed ordering:** multi-storey outranks openings. Reasoning: multi-storey
   is a structural gap — most real institutional buildings are multi-floor, and the pipeline
   currently cannot represent that at all, which is a harder ceiling than "walls have no doors
   drawn on them yet." Openings matters for usability but already has an honest 0-for-2 record
   tonight — it earns exactly one more bounded attempt with an explicit stop condition, not
   open-ended chasing. `IfcColumn` is real but lower-value than either — deferred behind both.

   **Superseded in ORDER, not reasoning, 2026-09-08:** A9 (found during D1 verification —
   compiled placement is wrong on every point-cloud building) now sits at priority 1, ahead of
   all three. The reasoning above for multi-storey > openings > `IfcColumn` still holds among
   themselves; A9 is a different kind of item (existing output being wrong, not a missing
   capability) and is addressed on its own terms in `SEQUENCED SESSION PLAN` §1.

**See `## SEQUENCED SESSION PLAN` below** — these decisions are now converted into a concrete,
ordered starting point for each item, the same way D4→D1 already had one.

## SEQUENCED SESSION PLAN (updated 2026-09-08) — what the next session(s) actually do, in order

Not calendar time — sized in sessions, same discipline as everywhere else in this project.
Each item below is scoped to a **first concrete action**, not a re-statement of its L/XL label.

### 0. ~~D4 → D1~~ DONE 2026-09-08 — see A9 below for what it found
Both are DONE (see their rows in section D above): `--erp-db` isolation added and working,
and a real DeKH scan (498 elements) verified genuinely clean through populate/classify/all 18
QA gates. Compile itself also now genuinely runs (a real bug — `GATE_SCOPE` was silently
skipping it — is fixed) and surfaced a real, systematic defect. That defect is **A9** below,
now priority 1, superseding this item rather than following it.

### 1. ~~A9 — compiled placement is systematically wrong on every point-cloud building~~
**FIXED 2026-09-08**
Found while closing out D1; the flat-BOM-tree hypothesis floated at discovery time was
investigated and **falsified** — compiling IFC-authored Sample House directly showed the
identical `P-PARENT 0/59` failure despite real assembly nesting (4 `ASSEMBLY`-type BOMs), so
it was never a point-cloud or nesting-depth problem.

**Real cause, traced through the actual write path, not guessed further:** grepped the whole
compiler for any `UPDATE` touching `C_OrderLine`'s `dx/dy/dz` — none exist anywhere; those
columns are written once at BOM Drop and never touched again. Comparing `c_orderline`'s LEAF
values against the real walked positions in `element_transforms` for the same elements showed
both live in one consistent MIN-CORNER-relative local frame (e.g. DeKH: both range ~0–35m),
never offset by the root `BUILDING` node's own `dx/dy/dz`. `StructuralBomBuilder.java`'s own
comments confirm this convention explicitly, not inferred: the building root's origin is
literally commented `// Compute building origin (LBD corner) from all elements`, and a child's
own placement offset is commented `MAKE dx/dy/dz = floor-to-building offset (always >= 0)`.
`BomTreeProver`'s old `computeWorldPosition` accumulated dx/dy/dz through EVERY ancestor
including the root — summing two incompatible coordinate frames, since the root's own
`dx/dy/dz` is a *different* value (`bom.getOriginX/Y/Z()`, its real-world placement seed, used
elsewhere in `CompilationPipeline` to seed the actual placement walk) — and additionally
checked containment as center-relative (`|child − parent| ≤ extent/2`) when the confirmed
convention is corner-relative (`0 ≤ child ≤ extent`). Two compounding defects, not one.

**Fix:** `LEAF dx/dy/dz` is corner-relative to its own immediate parent only, no accumulation
up the tree. `computeWorldPosition` (now provably wrong and unnecessary) removed;
`BomTreeProver.proveParent` checks `child ∈ [0, parent_extent]` directly.

**Verified twice, not assumed:** (1) simulated the exact corrected formula offline in Python
against the real `c_orderline` rows of all three buildings — 498/498, 70/70, 59/59, all 100% —
*before* touching any Java; (2) ran the actual JUnit test against all three for real, matching
the simulation exactly: **DeKH Building A 498/498, SampleHousePC 70/70, Sample House 59/59**
(all were 0 before). `P-SIBLING` confirmed unregressed at 100% throughout.

Two things found along the way, explicitly not part of this fix and left alone: Sample House's
own `SampleHouse.bimcobol` script independently fails on an unrelated pre-existing bug
(`no such column: ProjectName` — confirmed present before this fix too). And
`BuildingRegistryTest`'s `-Dbom.db` is a JVM-global property while its `@TestFactory` runs a
dynamic sub-test per `GATE_SCOPE`-listed building — pointing it at one building's compile-db
still runs every other registered building's own dynamic test against that same file, which
correctly fails their own element-count assertions. Pre-existing (this is exactly how
`run_RosettaStones.sh` has always invoked this test), unrelated to A9.

### 2. Multi-storey (re-scoped 2026-09-07 from XL down to a real first step)
**Investigated before proposing a design, per this project's own standing practice — findings,
not a re-statement of "this is unscoped":**

- **The compile back end already has real, working multi-storey support** — confirmed live,
  not assumed: the IFC-authored `SampleHouse_extracted.db` carries 2 real `IfcBuildingStorey`
  rows (`Ground Floor` elev=0.0, `Roof` elev=2.5) and compiles clean through the shared back
  end (13 Java files reference `spatial_structure`/`IfcBuildingStorey`: `StoreyCompiler`,
  `SpatialStructureBuilder`, `FloorAssemblyBuilder`, `BuildingCompiler`, others). **The gap is
  entirely in the point-cloud front end — consistent with the `MODULES — KEEP AS-IS` boundary;
  no Java work anticipated.**
- **Three specific front-end chokepoints, found by grep not guessed:**
  1. `segment.py::_finish_segmentation` labels the single LOWEST horizontal plane "floor" and
     every other horizontal plane "ceiling," full stop — on a real 2-floor scan fed through in
     one piece, a 2nd floor's real floor slab would be silently mislabeled "ceiling."
  2. `floor_z` (computed identically in `classify.py`, `run_scan_to_bom.py`,
     `run_dekh_staged.py`) is the MEAN of every floor-oriented segment's centroid — on a real
     multi-floor scan this would silently average two real floor heights into one meaningless
     number, corrupting door/window floor-proximity classification on every floor at once.
     This is a **live landmine**, not just a missing feature: it produces a wrong answer
     silently rather than erroring.
  3. `write_reference_db.py` hardcodes exactly one `IfcBuildingStorey` row (`PC_STOREY_1`) and
     tags every element's `storey` column to the literal string `"Level 1"`, unconditionally.
- **Real, already-in-hand precedent that changes the shape of the first step:** Building A
  genuinely IS a 2-floor building, and it was scanned as **two separate per-floor `.laz`
  files** — not one continuous multi-floor scan. Today's "handling" of that is a per-floor-
  independent architecture: each floor is segmented and classified completely separately, and
  the two floors' predictions are combined **only for scoring**, in a one-off Python script —
  never through `write_reference_db.py` as one coherent multi-storey model. So there are two
  genuinely different sub-problems bundled under "multi-storey," and they are NOT the same
  size:
  - **(a) Formalize the existing multi-run-combine into a real multi-storey `write_reference_db`
    output** — route Building A's two already-segmented, already-classified floors through
    ONE reference DB write with 2 real `IfcBuildingStorey` rows, each element correctly
    tagged. Does not touch `_finish_segmentation`'s floor/ceiling logic at all (each floor is
    still segmented independently, so "lowest horizontal = floor" stays correct per-floor).
    Uses real data already in hand — no new scan needed. **This is the realistic first step.**
  - **(b) Auto-detect floor separations from ONE continuous scan spanning multiple floors** —
    would require actually fixing chokepoint #1 above. Larger, unvalidated, and only needed if
    a real client's scan ever arrives as one continuous multi-floor capture rather than
    per-floor files (LiDAR practice for multi-floor commercial buildings commonly IS
    floor-by-floor, for scanner-range and occlusion reasons — matching the DeKH precedent —
    so (a) may be the practically correct scope, not just the cheaper one). Do not start this
    without checking real client-scan practice first, and don't conflate it with (a).
- **First concrete action for the next session:** implement (a) only. Fix chokepoint #2 as
  part of it (each floor keeps its own `floor_z`, never averaged across floors — this closes
  the silent-wrong-answer landmine even for the current single-run-per-floor workflow, which
  is real value independent of the rest). Verify against Building A's real 2-floor GT the same
  way every fix tonight was verified: does storey assignment match reality, does compile still
  gate clean, does per-floor door/window classification improve now that `floor_z` isn't
  cross-contaminated. (b) stays logged, not started, pending real client-scan-practice
  information.

### 3. Openings — ONE more bounded attempt, explicit stop condition agreed before starting
Two untried ideas from the RGB investigation, sequenced by cost and how directly each targets
the diagnosed root cause (not tried in the order they were originally listed):

1. **Cheap pre-check first: what do the dataset's own `.npy` semantic labels actually encode?**
   Near-zero cost — this project has used the `.npy` array only as an "unnamed grouping
   signal" for purity scoring so far, and has never checked whether its label values actually
   distinguish door-vs-wall semantically or only group by surface identity. Read label values
   at known GT door locations vs. known GT wall locations in B_ICU; if they don't separate,
   this idea is a real non-starter and is eliminated in under an hour, not guessed away.
2. **If (1) doesn't resolve it: per-wall vertical-gradient detrending.** The most targeted
   remaining idea, because it directly attacks the diagnosed root cause rather than a symptom
   — regress RGB against height (z) per wall (low-order polynomial, grounded in wall #229's
   own measured smooth gradient shape before picking an order), then run the SAME grid +
   connected-component anomaly detector from tonight's two attempts on the RESIDUALS instead
   of raw color. Reuses the tested methodology; changes only what's being thresholded.

**Stop condition, agreed now, not after seeing the result:** re-run the exact same
false-positive check used twice tonight (all 321 real predicted `IfcWall` segments in B_ICU,
TP = majority of a region's points inside a real GT door AABB). **Continue toward
implementation only if precision clears a real, order-of-magnitude bar — not "better than 2%,"
which is still unusable, but into a range (rough target: ~20%+, or a population that is finally
shape-separable from FP where a filter could plausibly clean the rest) that makes extraction
plausible.** If the result lands back in the same ~1–5% range with TP/FP shape-inseparable,
same as both attempts tonight — **that is 3 honest rejections in this problem family, and
openings closes for the pilot bar (production bar #1 above explicitly allows a human
touch-up pass on this exact gap).** Do not start a 4th design in this family without a
genuinely new data source, not a 3rd variation on anomaly-detection-over-existing-segments.

### 4. `IfcColumn` — deferred behind items 1–3 above
Stays exactly as characterized in section A2. Not started until 1–3 are done or explicitly
reprioritized.
