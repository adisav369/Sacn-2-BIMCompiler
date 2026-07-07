# ⚠ DO NOT REMOVE — Session resume: watchdog role, 2026-07-07 (Modeller/Bonsai authoring + MEP RosettaStone)

**Read this doc, then go straight to `§NEXT SESSION'S JOB`.** This session ran a long batch of parallel
dispatched builds (7 background sessions) touching the Bonsai kernel and RouteWalker/disc_walker MEP
placement. Everything landed is real, merged, and cross-referenced below by file — this doc is a pointer,
not a restated summary; read the dated sections it points to, don't trust this doc's own prose as the
whole story.

## §LESSONS LEARNT THIS SESSION — don't re-litigate, follow these

1. **RosettaStoneStrategy (extract, don't compute) is now empirically validated, not just doctrine.**
   A same-day bisector-computed MEP fitting rotation was proven ~135° wrong on real data the moment it
   was checked against a real extracted reference. Don't let a future session re-introduce "compute it
   from vectors" as a first resort — check for a real extractable source FIRST (`docs/internal/
   WalkerDoctrine.md §7`).
2. **One shared gate beats N point-fixes.** The same hardcoded-constant violation showed up at 6+ call
   sites across two files today. `docs/internal/WalkerDoctrine.md §10`'s `resolveRealPlacement()` gate
   (hard-fails on missing real data, never silently substitutes) is now the pattern — route NEW leaf
   placement code through it, don't hand-roll another lookup.
3. **`git stash` is UNSAFE bare across `/tmp/wt-*` worktrees of the same repo** — the stash stack is
   repo-shared, not worktree-local, and it bit two different sessions today. See
   `feedback_git_stash_shared_across_worktrees.md` (memory). Use a WIP commit instead if you need to shelve
   changes mid-task.
3b. **Don't spin up a separate research/verification agent in front of every build dispatch** — a build
   agent does its own grounding as part of building competently; a SEPARATE agent re-verifying the same
   ground first is usually pure duplicated cost. Reserve pre-dispatch research for when the answer changes
   WHETHER/HOW to dispatch at all. See `feedback_calibrate_research_depth_to_scope.md` (memory).
4. **Hard-fail-no-fallback has a real, visible cost, not just a correctness win.** After today's fixes,
   the walked MEP network on Duplex shows FP only — CW/SP/ACMV honestly render nothing (0/204 segments)
   because no real catalog product exists for them yet. This is doctrine working correctly, but it LOOKS
   like a coverage regression to anyone comparing before/after without this context — say so plainly when
   demoing, don't let it be mistaken for a bug.
5. **One canonical file per topic, checked again this session** — `BONSAI_ARRAY_PATTERN_SPEC.md` almost
   got a sibling `BONSAI_LOFT_SPEC.md` created before being caught and merged back in. Grep before creating
   any new `prompts/*.md`.

## §NEXT SESSION'S JOB — Tier 2 onward, per user's explicit priority

**User directive verbatim: finish the Modeller in full (Tier 2 onwards), with emphasis on real user
experience review — not headless-witness-passing alone — and care not to trash third-party-sourced code
(`planegcs`, `occt-wasm`, the Chili3D/ifc5cad study references) without UI coherence.** Read
`prompts/BONSAI_KERNEL_RESEARCH.md §GAP-TO-COMPETITIVE` in full before starting — it has the real tiering,
what Tier 1 already closed (all 6 ops, PR #691), and exactly what's left:

1. **Tier 2 — planegcs constraint richness** (only ~5 of ~60 real constraints wired: `parallel`/
   `perpendicular_ll`/`equal_length`/`p2p_symmetric_ppp`/H-V). This is the actual gap between "constraint-
   solving on fixed hand-drawn geometry" and Grasshopper/Dynamo-class "geometry as a function of
   parameters" — not the array/loft formula evaluator, which is a narrower, already-shipped thing.
   **UX review requirement — CORRECTED same day, don't repeat the lapse:** actually drive a dimension edit
   with real Playwright mouse/keyboard (click→type→Enter — not calling `solve()` from a harness) — that
   part is right. But the CHECK after is a `§`-tagged numeric assertion, NOT a screenshot/visual
   comparison — a screenshot only proves "looks plausible to a human eye," which is weaker than a numeric
   check, not a substitute for one (it can't catch an exact-position bug a human eye would miss). Log the
   EXACT position of every affected point/edge before and after the edit; assert the edited dimension
   matches the typed value exactly, every solver-moved point/edge lands at a HAND-COMPUTED expected
   position (same rigor as M5's 90°-bend-to-135.0000°-yaw proof, `WalkerDoctrine.md §7` — not eyeballed),
   and untouched points/edges stay exactly fixed. This IS "the real acceptance bar" — a passing headless
   witness with no live interaction is still not enough, but neither is a screenshot; it's exact numbers
   driven by a real interaction, both halves required.
   **✅ FIRST INCREMENT DONE 2026-07-07 (`p2p_distance`, width only)** — real Playwright click+type+Enter
   in the running Modeller, then re-verified per the corrected bar above (not the first, screenshot-leaning
   pass): `witness_e2e_sketch_dims.js` 10/10, anchor bit-identical, perpendicularity/parallelism via
   normalized dot/cross ≈1e-16, bbox == hand-computed bbox of the solved points. Found+fixed a bigger,
   pre-existing bug live: every toolbar `dim-*` field (not just the new ones) was unfocusable/invisible
   under the Outliner (`pointer-events` inheritance + no real screen position) — shared-gate CSS fix, not a
   point-fix. Full detail: `BONSAI_KERNEL_RESEARCH.md §GAP-TO-COMPETITIVE`. **Not done:** height's own
   independent proof, `tangent`/`p2p_angle`/`circle_radius`/`p2p_coincident` (4 of the 5 named constraints
   still unwired).
2. **Boolean robustness** (OCCT BOP mangles coincident/misaligned cuts — FreeCAD's own tracked issue
   #17705). Real risk at scale (every opening is a cut). Mitigation path already scoped: fuzzy-tolerance
   BOP, then a Manifold-mesh fallback — neither built yet.
3. **Direct-manipulation UI** (`prompts/MODELLER_DIRECT_MANIPULATION.md`) — axis-drag MOVE gizmo+snap,
   marquee multi-select, snap-to-geometry, rotate/scale handles. Its own card says "START WITH §0 SURVEY"
   — research the UI-facing area first, benchmark against named competitors, THEN build. Do not skip
   straight to build-first on this one; it's explicitly the one tier where "reference" means studying real
   shipped tools' observable UX (Chili3D/ifc5cad, real commercial tools), not extracting from an IFC file —
   a different kind of grounding than every other tier, treat it that way.
4. **IFC write coverage gaps** — array/loft export currently only handles `GEOM_EXTRUDE_POLY` parents;
   array-of-sweep/array-of-insert export is a disclosed, not-yet-built follow-up (`BONSAI_ARRAY_PATTERN_
   SPEC.md`).

## §NEW — ARC LOD-mesh box-fallback, SampleCastle (user-sighted, confirmed real, not yet fixed)

User visually confirmed SampleCastle's ARC render "has bbxes all round" despite the building silhouette
looking right overall. **Checked directly, not assumed:** `deploy/buildings/SampleCastle_extracted.db`'s
source data is 100% complete — all 3,504 `element_instances` resolve to a real `component_geometries` row,
zero unresolved hashes. So this is NOT a missing-data gap (unlike everything else found today) — it's a
render-path bug: something chooses the 12-tri box proxy even though the real mesh is correctly linked.
Not yet traced to root cause (Modeller-side, Viewer-side, an LOD-tier default, or an element-count-
triggered downgrade) — that's the first step for whoever picks this up.

**The standard test to extend, not invent:** `modeller/tests/witness_e2e_mv_parity.js`'s `M3 LOD400
(boxFallback=0, triExact=n/n)` assertion (line ~292) is exactly the "no fallback from a real LOD component"
rule, already expressed as a hard witness check — `boxFallback` = count of elements whose rendered tri
count is exactly 12 (the box-proxy signature) when the DB says otherwise. **It is hardcoded to Duplex
throughout** (file paths, panel selectors, log labels, and even the geometry table name — `base_geometries`
for Duplex vs `component_geometries` for SampleCastle, a real schema difference already confirmed today).
Generalizing it to take a building parameter and running it against SampleCastle is the concrete next
step — gate hard on `boxFallback === 0`, per the same hard-fail-no-fallback doctrine as `WalkerDoctrine.md
§8-10`, don't leave it informational. This witness itself lives in the still-unmerged worktree
`/tmp/wt-arc-source-parity` (branch `lane/modeller-ifc-open`, 52 commits ahead of `main`) — confirm that
worktree still exists before starting, and check whether merging it first is a prerequisite.

**✅ Independently re-run and CONFIRMED fresh, 2026-07-07 (not just trusted from the file's own header
comments)** — `12/12 PASS`, real numbers reproduced almost to the last digit: `maxDC=18.0301` (claimed
"18.03m pre-fix") · `residualMax=1.441e-5` across 215 shared elements (claimed "1.44e-5m") ·
`triExact=253/253 boxFallback=0` (the M3 claim, literally confirmed). Full log:
`/tmp/wt-arc-source-parity/mv_parity_fresh_run.log`. One environment gap found+fixed to get it running: a
missing local symlink (`viewer/buildings/Duplex_extracted.db` → `deploy/buildings/Duplex_extracted.db`,
gitignored, present in the canonical dev checkouts but not this fresh worktree) — a fixture gap, not a
witness-logic change. **This witness is a trustworthy, working baseline to extend for SampleCastle** — the
generalization task above is now building on verified-solid ground, not an unverified claim.

**⚠ First real investigation pass, 2026-07-07 — did NOT reproduce "boxes," found something more severe on
the Modeller side, real repro likely lives on the Viewer side instead. Diagnostic scripts left in place
(untracked, not committed — this is someone else's active lane) at `/tmp/wt-arc-source-parity/modeller/
tests/investigate_samplecastle_boxfallback.js` + `investigate_samplecastle_viewer.js`:**
- SampleCastle is **not a registered Modeller resident** (`modeller.html:3702`'s own comment: "NOT yet:
  SampleCastle/Hospital/Clinic/LTU/Terminal"). Opened it instead via the Modeller's REAL local-file path
  (`pickLocal()` → `_openStrDb()`, Puppeteer `elementHandle.uploadFile()` on `#m-open-file` — no OS dialog,
  same code a user's drag-drop uses). The DB loads correctly (`§BOMTREE seeded "SampleCastle" elements=3621`,
  `§STRWALK-INIT column-framed: columns=23 grid=8×9 girders=13` — real numbers, not stalled) but
  **`window.Bonsai.group()` never gets a single mesh child — 0 rendered, not 12-tri boxes.** Waited up to
  60s (`children.length > 100` never true). This is a DIFFERENT, more severe symptom than "boxes render" —
  possibly `_openStrDb()`'s local-open path has a real gap vs `_openResident()` (which Duplex uses) in
  actually instantiating ARC meshes, separate from the box-fallback bug reported.
- Given SampleCastle IS a registered **Viewer** resident (`viewer/city_bench.html` lists it, `viewer/
  panels.js` has SampleCastle-specific BOM-name mapping) unlike the Modeller, **the user's actual sighting
  almost certainly happened via the Viewer, not the Modeller** — redirect the next attempt there, not
  back into Leg M's Modeller-side generalization the way this note originally scoped it.
- Tried the Viewer next (`viewer.html?db=buildings/SampleCastle_extracted.db#bld=SampleCastle`, DB copied
  from `deploy/buildings/` since this worktree's `viewer/buildings/` didn't have it). Loaded for real
  (`§KERNEL_OP committed ... type=BUILDING_OPEN params={"name":"Ifc2x3_SampleCastle","count":3504}`) but my
  first census attempt used the WRONG global (`window.A`, the Modeller's convention) — the Viewer uses
  `APP` (`viewer/main.js`), so `ready:false` was my own script bug, not a finding. **Also chased then
  correctly ruled out a false lead:** `§RENDER_LOOP start total=4` looked like "only 4 things rendered" but
  is actually just a re-entrancy guard counter (`main.js:657-667`, counts how many times the render loop's
  own start function fired, unrelated to mesh/geometry count) — confirmed by reading the source before
  trusting the number, not after.
- **Genuine next step, not yet done:** re-run the Viewer census with the correct `window.APP` global to
  get a real mesh/triangle count and see if boxFallback actually reproduces there. This is real, unfinished
  work — flagging it honestly rather than either faking a fix or quietly dropping the thread.

**✅ RESOLVED (reframed), same day 2026-07-07 — corrected census (`window.APP`, reusing Leg V2's exact
`extractViewer`/`openViewer` from `witness_e2e_mv_parity.js` instead of a hand-rolled traverse) against the
REAL Viewer path, using the ACTUAL dev-deployed file (`deploy/dev/buildings/SampleCastle_extracted.db` —
confirmed byte-different from `deploy/buildings/SampleCastle_extracted.db`, different md5/size/mtime,
checked directly since a stale-vs-live file mismatch was the live hypothesis):**
- Steady-state (fully streamed, `A.streaming===false`) census on BOTH file copies: **`rendered=3504
  joined=3504 triExact=3504 boxFallback=0 placeholders=0 batchedUnresolved=0`** — a clean, fully-resolved
  render on the ACTUAL live-served file. This is NOT a permanent wrong-LOD-choice bug — it does not exist
  in the settled state.
- **Root cause found by sampling mid-load instead of only at rest:** at t=8s into a fresh open,
  `streaming=true streamedCount=2500 placeholders=3504` — **every single element is a box placeholder
  simultaneously**, even though 2500/3504 have already streamed by DB-fetch count (the per-element bbox
  placeholder layer isn't cleared/thinned in step with streaming progress, all-or-nothing). By t=12s:
  `streaming=false streamedCount=3504 placeholders=0` — fully resolved. **This is exactly "silhouette right,
  boxes all round"** — a scene of only bbox placeholders reproduces the true building silhouette (each box
  approximates its element's real footprint) while carrying none of the real mesh detail yet.
- **So the real finding is UX/performance, not a render-path defect:** SampleCastle (3504 elements, ~13×
  Duplex's 265) takes long enough to progressively stream (8-12s locally on instant-localhost swiftshader —
  plausibly much longer on a real network/weaker device) that a user looking at it mid-load sees a scene
  that is ENTIRELY box placeholders for several seconds, with no strong visual cue that this is transient.
  A real status-bar progress indicator already exists (`streaming.js` §S280: `"{building} — {streamed}/
  {total} ({pct}%)"`) but is easy to miss while the 3D view itself looks like nothing is happening (all
  boxes, silhouette static). **Genuinely open, smaller-scoped follow-up (not done, correctly scoped down
  from "fix a broken LOD choice" to a real but narrower UX gap):** either make the streaming progress more
  visually prominent than a small status-bar string, or thin the placeholder layer progressively in step
  with `streamedCount` instead of all-or-nothing, so a large building never reads as "fully stuck on boxes."
  If the user's original sighting was of a session that ACTUALLY stalled forever (never reaching
  `streaming=false`) rather than just a slow-but-completing load, that would be a different, real hang bug
  — not reproduced in this pass; worth them re-checking with the status-bar percentage visible to tell
  the two apart.

## §ALSO OPEN — carried forward, not urgent but don't lose them

- **Coaxial MEP diameter-transition detection** — investigated and found genuinely bigger than a quick
  fix (`prompts/Modeller/DISC_Walker/DISC_ROSETTASTONE_MEP_MINISET.md`'s coaxial-detection follow-up
  section): needs threading real per-instance diameter through `routeChains()` and reopening a deliberate
  prior architectural separation from the pattern-bridge network. A real design decision before anyone
  builds it, not a bug to just fix.
- **Real per-discipline MEP product survey (CW/SP/ACMV/ELEC)** — discussed this session, not yet
  dispatched: a research/survey pass (not a build) across large-complex reference buildings
  (`HospitalAuckland` first — already the proven Java `RouteWalker.java` reference building per
  `RouteWalkerTest.java`/PR #450/#456 — then Clinic/Hospital/Hospital_3), looking for real, clean, reusable
  product data the way `FP_Drop_Pipe` was found for FP. **Filter/dedup by real product signature (name +
  dimension), not by instance** — a hospital-scale building will have dozens of near-identical fixture
  groups; extract one representative mini-BOM per distinct real pattern, not one per instance. Scope to
  the "complex" building class only (`WalkerDoctrine.md §1`) — residential already proved it has no clean
  products (0/204 on Duplex), no need to re-check.
- **`viewer/routewalker.js` has a duplicate, untouched copy of the fixture-box bug** `§9`/`§10` just fixed
  in `modeller/routewalker.js` — disclosed by the gate-build session, not yet fixed.
- **Resident `.db` building files lack an `elements_meta.building` column** — breaks room-detection on
  those exact files, unrelated pre-existing gap, disclosed by the same session.
- **`witness_bend_fitting.js` is now stale** (21/22 — the one failure asserts an assumption `§8`'s fix
  correctly invalidated: CW/SP tube rows are SUPPOSED to be 0 on Duplex now). Needs updating to assert the
  new correct behavior, not treated as a regression to chase.

## §STATE — what's real and merged as of session close (bim-ootb)

PR #685 (`GEOM_ARRAY`) · #688 (`GEOM_LOFT`) · #689 (M5 fitting placement) · #690 (real MEP mini-BOM
rotation lookup) · #691 (Tier 1: REVOLVE/SHELL/OFFSET/FILLET_VARIABLE/CHAMFER_DIST_ANGLE/DRAFT) · #692
(real pipe cross-section, FP only) · #693 (shared `resolveRealPlacement` gate + real fixture dims) — all
merged onto `main`, all independently re-verified this session (not just trusted from agent reports),
one real CI lint failure caught and fixed post-hoc on #693 before it merged. Full detail + real witness
numbers for each lives in the dated sections of `docs/internal/WalkerDoctrine.md` (§7-§10),
`prompts/BONSAI_ARRAY_PATTERN_SPEC.md`, `prompts/BONSAI_KERNEL_RESEARCH.md §GAP-TO-COMPETITIVE`, and
`prompts/Modeller/DISC_Walker/DISC_ROSETTASTONE_MEP_MINISET.md` — read those, not just this pointer.
