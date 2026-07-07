# ⚠ DO NOT REMOVE — Session resume: watchdog role, 2026-07-07 CLOSE (Modeller/Bonsai authoring + MEP RosettaStone)

**Read this doc in full before touching code — it's short on purpose, everything DONE compacts to a link,
not a restated story.** This was a long session (10 merged PRs, several investigation threads, two real
self-corrections) — the value is in the lessons and the open items below, not in re-reading the day.

## §LESSONS — durable, apply every session, don't re-litigate

1. **RosettaStoneStrategy (extract, don't compute) is empirically validated, not just doctrine.** A
   same-day bisector-computed MEP fitting rotation was proven ~135° wrong on real data the moment it was
   checked against a real extracted reference. Check for a real extractable source FIRST, always.
   (`docs/internal/WalkerDoctrine.md §7`.)
2. **One shared gate beats N point-fixes.** The same hardcoded-constant violation showed up at 6+ call
   sites across two files in one day. `resolveRealPlacement()` (`WalkerDoctrine.md §10`) hard-fails on
   missing real data instead of silently substituting — route new leaf-placement code through it.
3. **A risk cited in multiple docs is not a risk verified once.** "OCCT BOP mangles coincident cuts" was
   repeated in three docs (by me, among others) as settled fact — never tested against this project's own
   data. When actually tested (5 targeted real-Duplex reproductions), it didn't reproduce. Check before
   repeating a citation, even (especially) one that sounds authoritative because it's been said before.
4. **`git stash` is repo-shared across `/tmp/wt-*` worktrees, not worktree-local** — bit two sessions the
   same day. Use a WIP commit to shelve changes instead. (`feedback_git_stash_shared_across_worktrees.md`.)
5. **Don't spin up a separate research/verification agent in front of every build dispatch** — a build
   agent grounds itself competently as part of building; a second agent re-verifying the same ground first
   is usually pure duplicated cost. Reserve pre-dispatch research for when the answer changes WHETHER/HOW
   to dispatch. (`feedback_calibrate_research_depth_to_scope.md`.)
6. **Hard-fail-no-fallback has a real, visible cost — say so plainly.** The walked MEP network on Duplex
   now shows FP only; CW/SP/ACMV render nothing because no real catalog product exists for them yet. Real
   coverage drop, correct per doctrine, will read as a regression to anyone without this context.
7. **One canonical file per topic — checked again, still worth checking every time.** A sibling
   `BONSAI_LOFT_SPEC.md` almost got created before being caught and merged back into `BONSAI_ARRAY_
   PATTERN_SPEC.md`. Grep before creating any new `prompts/*.md`.
8. **A screenshot is weaker than a numeric assertion, not a substitute for one.** Caught mid-session: "looks
   right in a screenshot" was briefly accepted as a UX-verification bar; corrected to exact hand-computed
   position assertions (same rigor as the M5 90°→135.0000° proof) before it shipped that way.
9. **Verify a background task's real result, not its exit code.** Exit code 0 was misleading twice today
   (a docs-deploy guard abort, and a `mkdocs gh-deploy` push failure) — the actual result only showed up in
   the tool's own log / an independent re-check (`git log origin/gh-pages`, fetched page content).

## §DONE TODAY — compact, full detail lives at the linked file, not here

| Area | What shipped | Where the real detail lives |
|---|---|---|
| Bonsai kernel breadth | `GEOM_ARRAY`, `GEOM_LOFT`, Tier 1 (6 ops: REVOLVE/SHELL/OFFSET/FILLET_VARIABLE/CHAMFER_DIST_ANGLE/DRAFT) | `BONSAI_ARRAY_PATTERN_SPEC.md`, `BONSAI_KERNEL_RESEARCH.md §GAP-TO-COMPETITIVE` |
| MEP fitting rotation | Real extraction (RosettaStone mini-BOM) replaces bisector math where a real reference exists | `WalkerDoctrine.md §7`, `DISC_ROSETTASTONE_MEP_MINISET.md` |
| MEP pipe cross-section | Real product-derived, FP only; CW/SP/ACMV/ELEC honestly refused (no real product yet) | `WalkerDoctrine.md §8` |
| Drift audit + fixture boxes | Full-codebase sweep for the same violation class; shared `resolveRealPlacement()` gate built | `WalkerDoctrine.md §9-§10` |
| Cross-app trust | `W-MV-PARITY` independently re-run fresh: 12/12, 1.44e-5m residual across 215 real elements | `WalkerDoctrine.md §7`, `/tmp/wt-arc-source-parity/mv_parity_fresh_run.log` |
| Tier 2 constraint richness | 3 real increments shipped (`p2p_distance`, `l2l_angle_ll`, `p2p_coincident`), each with a real bug caught before shipping | `BONSAI_KERNEL_RESEARCH.md §GAP-TO-COMPETITIVE` |
| Boolean robustness | Investigated, stood down — theoretical risk didn't reproduce; real fix blocked on inaccessible WASM source | `BONSAI_KERNEL_RESEARCH.md §4#2` |
| SampleCastle "boxes" | Resolved — not a render bug, a slow streaming window with no strong mid-load visual cue | `BONSAI_KERNEL_RESEARCH.md` / see git log `676c04991` |
| Deploy pipeline | Real bug fixed in `scripts/safe_gh_deploy.sh` (missing `.nojekyll`, would've blocked every future deploy); `StrategicIndustryPositioning.md` updated + deployed live | commit `e8a0f570d`, `c20cc9faa` |
| Circle + Arc primitives | `#699` circle (center+radius, real `makeCircleEdge` cylinder extrude, not tessellated) · `#701` arc (FreeCAD "Arc by center" 3-click, sector-closure disclosed in PR body, K4/K6 independently proved radius-ignored-on-click-3 and CCW sweep empirically). Both same-recipe builds once the kernel was checked (`makeCircleEdge`/`makeArcEdge` already existed — re-scoped smaller than the original spec feared) | `BONSAI_KERNEL_RESEARCH.md §GAP-TO-COMPETITIVE`, PRs #699/#701 |
| In-app + public User Guide | In-app overlay (`#700`): added Move & Manipulate + sketch-dims/weld/Circle mentions (was missing an entire shipped capability). Public `docs/ModellerGuide.md`: same new sections (with real captured screenshots incl. 2 newly-driven ones for snap-to-geometry + multi-select, previously undocumented at all) + replaced 4 STALE/malformed screenshots from an old build (518-feature ghost count, one with genuinely broken cut-off Outliner text) | bim-ootb PR #700; bim-compiler commits `7a5b61b27`/`2702e530f`, live at `https://red1oon.github.io/BIMCompiler/ModellerGuide/` |
| Direct-manipulation status CORRECTED | The `MODELLER_DIRECT_MANIPULATION.md` STATUS line had gone stale 3+ weeks past P1 and was nearly repeated as "not started" — grepped `bim-ootb main` directly instead: P3 multi-select, H2 snap-to-geometry, H3 rotate/scale, M1 grid-undo-fix are ALL real and already shipped. Only H1's named sub-item ("Z-handle from non-top views") is genuinely unconfirmed either way | `MODELLER_DIRECT_MANIPULATION.md` (corrected in place) |

## §FABLE-SUITED — small, decisive, dispatch directly (`Agent`, `model: "fable"`)

**`viewer/routewalker.js` has a duplicate, untouched copy of the fixture-box bug already fixed in
`modeller/routewalker.js` (PR #693, `resolveRealPlacement()` gate).** Real, live, disclosed bug — same
class as the invented-diameter/invented-fixture-box violations found and fixed everywhere else today
(hardcoded `0.15×0.15×0.15` boxes standing in for real product dimensions). The fix pattern is 100%
proven already: port the identical `resolveRealPlacement()` wiring from `modeller/routewalker.js` to its
`viewer/` twin, same real-dims-or-honest-refuse behavior, same witness shape. This is exactly the
"repeat an already-proven recipe in a new location" shape Fable is for — no open design question, no new
primitive, no new architecture, just apply a known-good patch to a sibling file and prove it the same way.
Don't hand it anything from the numbered list below — those all carry a real open decision or need a
survey first.

## §OPEN — NEXT SESSION'S JOB, prioritized

**User directive standing: finish the Modeller in full, real UX review as the acceptance bar (not
headless-witness-passing alone), care not to trash third-party-sourced code (`planegcs`, `occt-wasm`,
Chili3D/ifc5cad study references) without UI coherence.**

1. **✅ DONE — Circle/arc primitive** shipped (#699/#701, see table above). **Next call, already made —
   build `tangent`, NOT `circle_radius`:** `circle_radius`'s practical value (type an exact radius) is
   ALREADY delivered by the direct radius-set mechanism #699 shipped — a real planegcs `circle_radius`
   CONSTRAINT only earns its keep once a circle must solve simultaneously against something else, which
   doesn't exist yet. `tangent` unlocks genuinely NEW capability instead (circle-to-line / circle-to-circle
   touching — a column against a wall face, two circles kissing). Same "pick the one that's actually new"
   logic as the circle-before-arc call. If a session is mid-flight on this when you read this, don't
   re-litigate — the call is made, just verify the finish.
2. **Direct-manipulation UI — mostly ✅ DONE, corrected same day (see table above).** Do NOT restart a
   fresh §0 survey — P1-P3/H2-H3/M1 are real and shipped. The one genuinely open thread: confirm H1
   ("Z-handle visibility from non-top camera angles") is fine or fix it, then do a SHORT fresh "be the
   user" walk (not a from-scratch competitor survey) to see if anything reads as friction now, several
   features later. Small, hour-scale, not a multi-session lane anymore.
3. **SampleCastle streaming UX** — genuinely open, smaller-scoped than originally feared: either make the
   loading-progress indicator more visually prominent, or thin the box-placeholder layer progressively
   with `streamedCount` instead of all-or-nothing. Not urgent, real when picked up.
4. **ARC LOD-mesh witness generalization** — `witness_e2e_mv_parity.js`'s `M3 boxFallback=0` check is
   hardcoded to Duplex; SampleCastle's real finding (above) makes this lower-priority than originally
   scoped, but generalizing it to any building is still a real, reusable win.
5. **Real per-discipline MEP product survey (CW/SP/ACMV/ELEC)** — a research pass, not a build, across
   large-complex reference buildings (`HospitalAuckland` first — already the proven Java `RouteWalker`
   reference building). Filter/dedup by real product signature, not by instance. Scope to "complex"
   building class only — residential already proved it has no clean products (0/204 on Duplex).
6. **IFC write coverage gaps** — array/loft export only handles `GEOM_EXTRUDE_POLY` parents currently.
7. **Coaxial MEP diameter-transition detection** — investigated, found genuinely bigger than a quick fix
   (needs reopening a deliberate prior architecture separation). Real design decision before building.
8. **Small, disclosed, not urgent:** `viewer/routewalker.js` has a duplicate untouched copy of the
   fixture-box bug already fixed in `modeller/`; resident `.db` files lack `elements_meta.building`
   (breaks room-detection); `witness_bend_fitting.js` is stale (asserts an assumption `§8` correctly
   invalidated).

## §STATE — bim-ootb PRs merged this session, all independently re-verified (not just trusted)

#685 `GEOM_ARRAY` · #688 `GEOM_LOFT` · #689 M5 fitting placement · #690 real MEP rotation lookup · #691
Tier 1 (6 ops) · #692 real pipe cross-section · #693 shared placement gate · #694/#695/#696 Tier 2
constraints (`p2p_distance`/`l2l_angle_ll`/`p2p_coincident`) · #697 viewer routewalker fixture-box port ·
#699 circle primitive · #700 in-app User Guide update · #701 arc primitive. bim-compiler `master`:
docs/deploy fixes, `StrategicIndustryPositioning.md` live, plus this session's `BONSAI_KERNEL_RESEARCH.md`/
`MODELLER_DIRECT_MANIPULATION.md` corrections and 2 `docs/ModellerGuide.md` deploys (new sections +
4-screenshot stale-image fix, commits `7a5b61b27`/`2702e530f`) — live at
`https://red1oon.github.io/BIMCompiler/ModellerGuide/`, independently content-verified (Last-Modified +
image references checked post-CDN-propagation, not just HTTP 200).
