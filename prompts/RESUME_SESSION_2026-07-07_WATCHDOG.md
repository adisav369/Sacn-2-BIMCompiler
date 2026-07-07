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
   **UX review requirement:** don't just wire a constraint and witness it headlessly — actually drag a
   dimension in the running Modeller and confirm dependent geometry updates the way a user would expect;
   that's the real acceptance bar per the user's directive, not a passing assertion alone.
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
