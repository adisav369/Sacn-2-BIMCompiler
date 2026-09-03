# RESUME — Modeller walk substrate, cache-first Open, calibrated-confidence (handoff for a new session)

## ⚠ STANDING AGENDA — invoked by the phrase "Modeller's 2nd principle" (2026-07-06)

Companion to `prompts/Modeller/DISC_Walker/ARC_GEO_FETCH_SPEC.md`'s "Modeller's first principle" (that one audits ARC INGESTION —
does DDB-open and IFC-open land on the same geometry, does the disc walker see it identically). **This one
picks up where that leaves off: POST-ARC PLACEMENT** — once ARC is loaded and trusted, is the WALK of the
other disciplines onto it (STR first, then MEP) actually deterministic/verified truth, or claimed-but-unproven,
same standing skepticism, same stance: "it is all maths, a fine-enough whitebox log should be able to say it
exactly." Lives at the TOP of THIS file — this file is the existing orchestration anchor for "drop ARC → walk
STR (lead) → layer MEP," not a new file (per `feedback_prompt_file_organization.md` rule 8). Single page,
dated sections in order, latest at the bottom, git-linked throughout.

**The check every invocation must perform, in order:**
1. **Don't trust a PASS.** Same as principle 1 — read the actual assertion code behind any STR/MEP witness
   before accepting it; confirm it would actually FAIL on a real placement defect, not just "ran clean."
2. **STR-then-MEP joint verification, not two isolated claims.** STR is declared the LEAD discipline (derives
   its own grid straight from the ARC substrate) and MEP is declared ANCHOR-DEPENDENT (its ceiling — good on
   mined buildings with hand-seeded anchors, 0 segments on Terminal for lack of JUNCTION/STACK anchors). The
   identified fix — derive MEP anchors FROM ARC (mains/risers from cores → branches to fixtures), mirroring how
   STR derives its grid from ARC walls — was flagged as the next build, not confirmed shipped. Check: has it
   actually been built since? Is there ONE building where STR walks first, THEN MEP walks using STR/ARC-derived
   anchors, verified together — or are STR's witnesses and MEP's witnesses run on separate, never-joint setups?
3. **Priority placement order (ARC→STR→MEP/CW/SP→FP/ELEC/ACMV) — enforced or just prose?**
   `Modeller/DISC_Walker/WALKER_GUARDS_ROSETTASTONE_SPEC.md` §2B rule 6 states this ordering as doctrine. Check whether any guard
   would actually FAIL a walk that violated the order (e.g. MEP walked before STR/ARC), or whether it's
   descriptive text nobody encodes as a check.

## ⚑ THE CAMPAIGN — real MEP walked into the scene, verified, animated (2026-07-07, user: "handled long run till end, or in a series")

Not a re-audit — a BUILD, in sequenced milestones. Each is its own session, folds its outcome into a dated
`## ▶` section below with a real commit link, then hands off — don't attempt all of it in one sitting even
with ample context (regression risk on live walk paths is the reason, not context size). Mark each `✅ DONE
(witness, <commit>)` in place here as it lands; that's the checklist state, don't restate it in prose elsewhere.

- [x] **M1 — ✅ DONE (witness, bim-ootb `03fa2f3`, PR #683, branch `feat/mep-seedtrunk-network`) — bridged to
  `routewalker.js`, did not reinvent generation.** See `## ▶ 2026-07-07 M1 BUILT` section below for the full
  outcome; checklist state only here per this file's own rule.
  <!-- original REDIRECTED framing kept below for context, not restated as open -->
  Traced both sides (Java + JS) this pass: `DAGCompiler/.../mep/RouteWalker.java`'s "MEP_RECIPE
  architecture" (typed anchor pairs `ad_mep_anchor` + sequenced topology pattern `ad_mep_pattern`:
  METER→FIXTURE→VALVE→JUNCTION→STACK) IS the real abstract DAG-placement-of-a-typical-set mechanism the user
  built — and it WAS ported to JS verbatim as `modeller/routewalker.js` ("Port of RouteWalker.java", still
  wired live in `modeller.html`), proven by `W-ROUTEWALKER-MEP 7/7` against a real building (Java side:
  `RouteWalkerTest.java`, `HospitalAuckland`, PR #450/#456). It is NOT dead, NOT unwired — it's real and
  standing. Its ceiling (why it returns 0 on Terminal) is that it needs PRE-MINED `ad_mep_anchor`/
  `ad_mep_pattern` rows for a specific building — it never generalizes to an arbitrary ARC-only building.
  `disc_walker.js`/`seed_trunk.js` is a SEPARATE, later engine built to solve exactly that generalization
  problem, but it reinvented its own simpler generation (one seed + nearest-neighbor chain) instead of feeding
  ARC-derived anchors into the already-proven `routewalker.js` pattern engine.
  **M1, correctly scoped: derive a real ARC-grounded anchor SET (mains/risers from cores, mirroring
  `defaultSeed()`'s door/stair-from-elements_meta technique) and feed it into `routewalker.js`'s existing
  `ad_mep_pattern` topology walk — inherit its real 7/7 proof, don't build a second, less-tested generation
  path in `disc_walker.js`/`seed_trunk.js`.** If a session already started the old "extend seed_trunk.js"
  framing before this correction landed, STOP and re-read this section before continuing further.
  Discipline-agnostic check still applies once bridged: confirm the SAME bridge also unblocks
  `routeChains`/RouteWalker's pattern-walk for FP/ELEC/ACMV, not just MEP — measure it, don't assume it from
  the parameterization alone.
- [x] **M2 — ✅ DONE (witness, bim-compiler `scripts/witness_str_mep_clash_gate.js`) — joint STR+MEP quality
  witness.** See `## ▶ 2026-07-07 M2+M3 BUILT` section below for the full outcome.
- [x] **M3 — ✅ DONE (same witness, see below) — priority-order guard wired into a real live call.**
- [x] **M4 — ✅ DONE (witness, bim-ootb `feat/mep-seed-reveal`, PR #686) — construction reveal wired over M1's
  PLB network.** See `## ▶ 2026-07-07 M4 BUILT` section below for the full outcome, incl. a real ordering-design
  correction (BFS tried, measured wrong, replaced) and a self-cancel bug found+fixed while proving it live.
- [ ] **M5 — NEW 2026-07-07 (found while answering a direct question, not yet scoped as code): elbow/fitting
  orientation.** Checked `_rwPairSegments()` (`routewalker.js`) directly — it produces straight `{from, to,
  len}` line segments only. There is NO discrete elbow-fitting mesh anywhere, and NO rotation computed to
  match two adjoining segment directions at a bend — a direction change today is just two straight segments
  meeting at a shared anchor point, rendered as one continuous tube. Real device DOCKING (a sprinkler's
  orientation onto its host) is already handled correctly by a separate, pre-existing mechanism —
  `disc_walker.js`'s `hostBind()` for its CENTER/TOP/BOTTOM branch (computes BOTTOM/TOP/CENTER mount offset
  off the host's real measured bbox height) — that part is NOT this gap. **Correction to an earlier claim in
  this thread:** `hostBind()`'s SIDE-mount branch is NOT immune to M6 below — it uses the identical
  dominant-AABB-axis heuristic (`horiz = w.bx >= w.by_ ? 0 : 1`) as `_rwPairSegments`/`_segLine`, so wall-face
  device mounting shares the same π/2-rotation assumption, not a separately-safe mechanism as stated before.
  This gap (M5) is specifically: placing a real elbow/tee fitting mesh AT a bend, rotated to match both
  adjoining runs. Not scoped into a build plan yet — flag for the user before starting, this may or may not
  matter depending on whether the product needs fitting-level fidelity or just a correct continuous path.
- [ ] **M6 — NEW 2026-07-07: run-axis inference assumes π/2-rotation (Manhattan) buildings only — a real,
  disclosed scope limit, not yet tested against a counter-example.** Both `_rwPairSegments`/`_segLine`
  (`routewalker.js`) and `hostBind()`'s SIDE branch (`disc_walker.js`) infer a host/run's orientation from
  WHICH AABB AXIS IS LONGER, never from actual rotation data — the code's own comment admits it:
  *"rotations are π/2-multiples so the AABB long axis IS the run axis"* (`routewalker.js:501`). At any other
  angle (a wall at 30°, say) the AABB approaches square and this heuristic misidentifies the run direction —
  this is DIFFERENT from and BROADER than M5 (it affects pairing/mount correctness on any future
  non-orthogonal building, not just fitting cosmetics). Not the same problem `LibraryFactory.
  calculateWorldCenter()` (Java) solves either — Java never infers orientation from a bbox shape at all, it
  offsets from an ALREADY-KNOWN attachment point + rotation supplied by the placement spec, so it was never
  exposed to this failure mode. Real fix is likely narrower than "port Java": `element_transforms` already
  has a real `rotation_z` column (used elsewhere in `disc_walker.js`) — prefer reading it directly over
  inferring axis from bbox shape, WHERE a building's convention makes it trustworthy. **Caveat, not yet
  resolved:** some extractors (confirmed on HHS_Office earlier this week) bake rotation into vertex positions
  and always report `rotation_z=0` — so this fix needs a way to tell which convention a given building uses
  BEFORE trusting `rotation_z`, not a blind switch-over. Every building this campaign has tested against so
  far (SampleHouse/Duplex/SampleCastle) is orthogonal, so M1-M3's results are unaffected by this — this is a
  forward-looking gap for the first non-orthogonal building this pipeline meets, not a retroactive bug.
- [x] **FOLLOW-UP — ✅ DONE (witness, bim-ootb `1185f20`, PR #684, branch `fix/mep-str-clash-gate`) — the
  2.6cm STR-column penetration is closed.** See `## ▶ 2026-07-07 FOLLOW-UP FIXED` section below for the
  full outcome (a deeper pre-existing box-orientation bug, not just a missing STR envelope).

**Capability note:** each milestone is well-specified extension of already-proven, already-working code (not
a novel-insight problem) — Sonnet can build these. The risk is regression on live walk paths across several
files, which is why this is milestones + fold, not one unbroken session.

## ▶ 2026-07-06 — "Modeller's 2nd principle" invoked: 1st pass overclaimed (checked `origin/main` only) — corrected below, per rules 9-10

**CORRECTION (this file's own §0/STATE above is STALE — dated 2026-06-26, superseded by `#599` 2026-07-02;
don't relay its "MEP=RouteWalker, anchor-dependent ceiling" framing as current).** First investigation pass
checked only `origin/main` and only `modeller/routewalker.js`, and reported "MEP anchor-from-ARC never built,
no joint STR+MEP witness." Both claims were wrong or overbroad — real work exists that pass missed:

- **`RouteWalker.java`/`routewalker.js` is SUPERSEDED**, per `bim-ootb` `5292ee1` (**PR #599**, 2026-07-02,
  "Walk ALL Disciplines"): *"doctrine says DiscWalker supersedes it for the MEP family."* Current MEP path is
  `modeller/disc_walker.js`, not the retired `routewalker.js`.
- **A real ARC-derived MEP entry point DOES exist and ships**: `disc_walker.js` `defaultSeed()`
  (W-SEED-TRUNK/W-SEED-DEFAULT) picks the most-external door/stair on the lowest storey straight from real
  `elements_meta`/`element_transforms` (human-confirms, never fabricated) → feeds `seed_trunk.js`'s corridor
  trunk. Shipped + closed: `prompts/Modeller/DISC_Walker/RESUME_SEED_TRUNK.md` ("✅ DONE+LIVE 2026-06-30," bim-ootb `#580`/`#582`/
  `#583`). Still true, unchanged: `routeChains()` itself (pipe-to-pipe chaining) is "real on MEP-rich
  buildings, 0 on residents" per its own code comment — full from-scratch MEP generation on an ARC-only
  building is still not proven, just less-absent than first reported.
- **A joint ALL-disciplines walk DOES exist**: `#599` adds `discWalkAll(ctx)`, looping every discipline
  sequentially through one shared `_discWalkOne`, witnessed `W-E2E-WALK-ALL 10/10` + Terminal-scale hardened
  in `810ff94` (**PR #606**, `§WALKALL-TERMINAL-SCALE`). **Not yet confirmed:** whether that witness checks
  MEP's placement QUALITY jointly with STR's, or only that the loop completes without erroring — this
  specific distinction is still open, not re-verified this pass.
- Also found, not yet folded into a claim either way: `19721ff` (**PR #638**, "port §8E-1b/§8E-2a STR-into-ARC
  + canopy render") — relevant to STR/ARC integration, not yet read in detail against this file's STR claims.

**Step 3 (priority placement order, `wgRunPass`) finding UNCHANGED, re-confirmed real:** `deploy/dev/
walker_guards.js:179-198` is called from exactly one place, its own test — no live walk path invokes it, so
an out-of-order placement would not be caught. Not re-checked against `--all` branches this pass; flag as
still-open, not re-verified with the corrected (rule 10) method yet.

**TASK ASSIGNED for a fresh session (build/re-verify, don't re-diagnose from scratch — start from THIS
corrected state, not the pre-#599 framing above):**
1. Re-run this file's §0/STATE section against current `disc_walker.js` reality — the MEP "ceiling" framing
   needs a full rewrite, not a patch, now that DiscWalker (not RouteWalker) is the live path.
2. Confirm/deny whether `W-E2E-WALK-ALL` actually checks MEP placement quality jointly with STR, or only loop
   completion — if only the latter, that joint-quality witness still needs building.
3. Either wire `wgRunPass` into live walk paths (checked across `--all` branches first, per rule 10, in case
   this was already done elsewhere) or downgrade §2B rule 6 in `Modeller/DISC_Walker/WALKER_GUARDS_ROSETTASTONE_SPEC.md` from
   "doctrine" to "not yet enforced." Update this section with the real commit link once resolved — append
   the outcome, don't re-litigate the agenda text above.

```
# ⚠ DO NOT REMOVE
SCOPE: Continue the MODELLER track — drop an ARC(-only) job, walk the disciplines, fold signed ops over a
PRISTINE bbox substrate, guarded + with RosettaStone-calibrated confidence. This is the GENERATIVE WALK track,
NOT the 0.000mm exact-reconstruction mission (that is separate + still simple-set-first; do NOT conflate).
INVARIANTS: non-invent (measure/cite, refuse beats fabricate) · oracle = pristine extraction NEVER cooked
output.db · confidence CALIBRATED before shown · WITNESS-FIRST · reuse the Viewer's machinery, don't rebuild.
ANCHORS: prompts/Modeller/DISC_Walker/WALKER_GUARDS_ROSETTASTONE_SPEC.md · prompts/Modeller/DISC_Walker/STR_ROUTEWALKING_SPEC.md ·
prompts/SPATIAL_DEPENDENCY_GRAPH.md · memory project_walker_guards_rosettastone + project_modeller_vision_lock.
Read the §-log after every run. prompts/ is gitignored — this doc is local; code+witnesses are committed.
```

## ✅ SESSION 2026-06-26c — rooms/storeys SOLVED + isolated GH residents + cross-edge slice 1 (read FIRST)
**bim-ootb PR #542 MERGED (auto-merge on CI green), sw v735.** The modeller now has its OWN isolated GH
playground (zero OCI) AND real rooms/storeys:
- **ISOLATION:** resident loader reads `../modeller/<db>` (GH Pages), `_ociBase`/`buildings/` REMOVED →
  `_modellerBase()`. SampleCastle replaces the mislabeled Schependomlaan. **W-MODELLER-GH-RESIDENTS 18/18.**
- **Terminal FULLY on GH (LFS works — the prompt's "can't" was WRONG, user was right):** Terminal_meta.db
  (19MB bbox substrate) = regular blob; Terminal_geo.db (250MB meshes) = **Git LFS**. LIVE-VERIFIED serving:
  GH Pages + raw.githubusercontent serve the 134-byte **pointer**, BUT `media.githubusercontent.com/media/
  red1oon/bim-ootb/main/modeller/Terminal_geo.db` serves the **full 261,349,376 bytes WITH `accept-ranges:
  bytes`** (streamable). ⇒ when meshes get wired, fetch LFS files from the **media endpoint**, regular blobs
  from Pages. `.gitattributes` tracks `modeller/*_geo.db`.
- **ROOMS/STOREYS SOLVED** (were synthetic "≈R1" clustering, foundation miscounted): re-extracted SH+DX →
  REAL IfcSpace rooms; `bom_tree.seedFromDb` QUALIFIES by **OPENING (door) + habitable-AABB**: storey w/ ≥1
  door = habitable, door-less = **LAYER ("· layer")**; room = IfcSpace on a habitable storey w/ height ≥1.8m
  (thin roof void excluded by BOTH legs). Empty-but-real rooms materialized. **W-ROOM-STOREY-QUALIFY 5/5**
  (SH = 3 rooms [Living/Bedroom/Entrance] + Roof layer; DX = 20 rooms + T/FDN & Roof layers). USER DESIGN
  ANSWERED: room = IFC-type(IfcSpace) + door + habitable-AABB; spaces have NO geometry natively → had to
  EXTEND the extractor.
- **EXTRACTOR ENHANCED (bim-compiler 24fa09da):** `extractIFCtoDB.py` — IfcSpace footprint AABB into
  `spatial_structure` (center+size, tessellate the space solid once) + native `element_transforms.bbox_xyz`
  (was backfilled post-hoc; now self-contained). Normalized w/ the element frame.
- **CROSS-EDGES slice 1 (the GRAPH half):** `cross_edges.js` derives the typed **`abuts` (face-touch)** edge
  ON-THE-FLY from the bbox substrate (sweep-and-prune, scales to 48k) — faithful JS port of the witnessed
  Python `_face_touch`. **W-CROSS-EDGES-ABUTS 5/5** (JS == Python EDGE-FOR-EDGE, 15,462 edges on 3 real
  buildings). Derived on resident Open + §-logged (`window.swXEdges`); NOT yet RENDERED on the backbone.
- **⚠ ORPHAN TRAP HIT + RECOVERED:** #542 auto-merged (squash) on commit 1 (isolation+LFS); commit 2 (v2
  SH/DX residents + room/storey qualify + cross_edges) pushed AFTER the squash → ORPHANED (exactly CLAUDE.md's
  warning). Recovered by cherry-pick into **PR #543** (+ SampleCastle). **LESSON: when the bot auto-merges,
  push EVERYTHING before the PR triggers; never push a 2nd commit to an auto-merge-armed branch.**
- **ALL 3 SMALL RESIDENTS NOW HAVE REAL ROOMS (PR #543):** SampleCastle re-extracted from
  `internal/sources/Ifc2x3_SampleCastle.ifc` (= Schependomlaan) → 4 habitable storeys / ~99 rooms / fundering+dak
  layers, 7x9/23/14. Residents gain a `v` field → `?v=N` IDB cache-bust. sw v736. W-ROOM-STOREY-QUALIFY 6/6.
- **IFC LIBRARY copied local:** `internal/UNMERGED/` (38 IFCs, 1.3GB) + `internal/sources/` (SampleCastle,
  Schependomlaan, SampleHouse, Duplex). `internal/` is gitignored. Source of truth =
  `/home/red1/Projects/bim-compiler/DAGCompiler/lib/input/IFC/` (a DIFFERENT checkout — has ~80 IFCs incl OOTB
  TerminalMerged 567M + the bSI/PCERT infra set).
- **⛔ OPEN / NEXT:** (1) verify PR #543 MERGED + live (Pages rebuild → v2 residents w/ real rooms). (2) RENDER
  the cross-edges Find-style ON the containment backbone (engine + derive done, VISUAL render pending = the
  remaining "graph half"). (3) cross-edges: 4 more (anchored-to/spans/hosted-by/instanced-by-n) — now BAKED in
  the re-extracted edge tables, so consume-baked vs JS-derive is a choice. (4) Terminal rooms: Terminal_meta is
  a projection w/o space AABB; re-extract w/ the enhanced extractor if its rooms need the new qualification.
  (5) per-room door PROXIMITY (door AABB ∈ space AABB) now possible where spaces tessellate (SH yes, Schep 2D).

## ⚠ SESSION 2026-06-26b HANDOFF — (GH migration + ISOLATION + room/storey design)
**Shipped + merged this session (all witnessed):** #534 walker ARC-only port (sw v730), #536 guided-Open residents
(v731), #537 mo_<key> editable instance (v732), #538 STR-edit replay on reopen (v733, W-STR-REPLAY 5/5), #539
bom-graph tab Building→Storey→Room→disc→class→element (v734, W-BOM-GRAPH 16/16). Witnesses in bim-compiler scripts/:
witness_resident_open / witness_mo_instance / witness_str_replay / witness_bom_graph (+ make_resident_meta.sh).

**THEN a costly OCI/GH detour — lessons locked (see memory [[feedback_trace_consumer_before_publish]]):**
- I uploaded bare bbox `*_meta.db` for SMALL buildings to OCI → BROKE them in the Viewer. ROOT: `viewer/city.js`
  HEADs `<bldg>_meta.db`; a 200 → split-meta-geo mode expecting `_geo.db`; a lone meta strands meshes.
  `deploy/OCI_UPLOAD.md`: small=single `_extracted.db`, large=split `_meta`+`_geo`. **Deleted the 3 stray meta.db;
  all `_extracted.db` were untouched (ADDs, not overwrites); user cleared browser IDB cache → buildings back.**
- **TRACE THE CONSUMER + honor the convention BEFORE writing to OCI / GH / shared tree. Refer to the user when you
  hit a rule. The repo AUTO-MERGES PRs (bot) once CI passes — mark draft if you don't want a merge (I learned via #540).**
- **GH migration verdict (homework done, verified):** GitHub Pages CANNOT serve Git LFS (docs.github.com: "Git LFS
  cannot be used with GitHub Pages sites") → LFS = pointer files = broken. GH 100MB/file hard limit. So Terminal_geo
  (261MB) CANNOT go to GH → meshes stay OCI (lazy, healed by `db_resolve.js` W-DB-404-OCI-RETRY). **bboxes/small-DBs
  on GH + meshes on OCI = the scalable shape.** Viewer already heals `buildings/<f>` 404→OCI.

**DECISION (user): the modeller gets its OWN PLAYGROUND, fully ISOLATED from the viewer (101 Drift Law).**
- PR #541 (auto-merge armed) MOVES SampleHouse/Duplex/SampleCastle `_extracted.db` → repo-root **`modeller/`** dir on
  GH (NOT `viewer/buildings/` — that disturbs the viewer; #540 wrongly did that, #541 reverts the location). Viewer
  stays 100% on `viewer/buildings → OCI`, untouched.
- **OPEN ISOLATION WORK (do NOT let modeller read viewer/OCI — advise user before ANY share/copy):** shared drift
  vectors loaded by BOTH viewer.html + modeller.html = `kernel_ops.js, connect_scene.js, grid_kinematics.js,
  routewalker.js` (+ shared `viewer/sw.js` precaches modeller files + shared CACHE_VERSION). My modeller JS
  (str_walker*, bom_tree*, bonsai_oplog, walker_confidence) is modeller-ONLY (viewer doesn't load it) = no viewer
  impact. NEXT: (a) point the resident loader at GH `modeller/` (relative), NOT OCI `buildings/`; (b) resident list =
  SampleHouse, Duplex, **SampleCastle** (NOT Schependomlaan), Terminal; (c) decide modeller self-containment for the
  4 shared libs.
- **SC = SampleCastle** (good material coloring, 30 materials, column-framed 23 STR cols), **NOT Schependomlaan** —
  corrects the stale [[project_sc_naming_and_disc_routing]] memory for the modeller-residents context.

**ROOM/STOREY CLASSIFICATION DESIGN (user, for the bom-graph — the real task-4 refinement):**
- **A room = a HABITABLE IfcSpace that has a DOOR.** SampleHouse re-extract proves it: 4 IfcSpaces = "1 Living room",
  "2 Bedroom", "3 Entrance hall" (all on the Ground storey, 3 doors) + "4 - Roof" (on the Roof level, no door) → **3
  rooms**, matching the user. No `IfcRelSpaceBoundary` in SH; the door↔room link = doors live on the storey the rooms
  are on. **Proper storey = a band with doors (habitable); slab/LAYER = roof/foundation (thin, no doors) → LABEL as
  layer, don't count as storey.** SH = 1 storey + Roof(slab); SC has 4+attic. "Unknown" = unassigned, label as such.
- **KEY ENABLER:** the CURRENT `DAGCompiler/python/extractIFCtoDB.py` ALREADY emits `spatial_structure` + `IfcSpace`
  + ALL 6 SDG edge tables (`rel_adjacency/rel_anchored/rel_spans/rel_fills_host/rel_aggregates/rel_contained_in_space`)
  — verified by re-extracting `Ifc4_SampleHouse.ifc` → SH_new.db (in scratchpad). The hosted SH/DX/SC `_extracted.db`
  are OLD extractions LACKING these. **So: RE-EXTRACT the residents with the current extractor → rooms + the typed
  cross-edges come for free** (resolves the task-4 rooms gap AND the cross-edges half in one move). Source IFCs are in
  `reference/residential/` (SampleHouse, Duplex); Schependomlaan/SampleCastle source — locate before re-extract.

## §0 LAY IT OUT (start the new session HERE — strategic frame, answered 2026-06-26)
- **TE_BOM.db? No.** The RouteWalk track works off the **pristine substrate (meta.db: bboxes + discipline + placement)**
  + a **DERIVED abstract graph** (containment from spatial nesting + the 6 typed cross-edges already in
  extractIFCtoDB.py, witnessed house+bridge). The BOM is a secondary recipe/cross-check, NOT the trunk
  (BOM-dropping was cart-before-horse). ⚖ OPEN: where the graph lives at runtime — extractor writes edge tables into
  meta.db, OR the modeller derives them on the fly from bboxes (LEAN toward derive-on-the-fly; keep meta.db pristine).
- **STR = the lead discipline, healthy.** Skeleton (cols + PRIMARY girders) + tessellation generate + are witnessed;
  guards/walk-back/calibrated-confidence/ARC-only auto-pick done. **No correctness showstopper** (refuse-beats-fabricate).
  **Gap = COVERAGE:** joists/secondary/trusses/members NOT generated (beam recall ~0.23); wall-bearing yields only the
  grid datum (no members yet). Next STR work = completeness (joist-infill, secondary, wall-bearing sizing).
- **Java/JS MEP RouteWalker = good but ANCHOR-DEPENDENT (its ceiling).** Works on mined buildings (Duplex, Revit_MEP);
  **0 segments on Terminal** (anchors FIXTURE+VALVE only — no JUNCTION/STACK; local~20m vs site~671m frame). STR
  surpassed it because **STR derives anchors from the ARC grid; MEP hand-seeds them.** The fix is NOT "debug RouteWalker"
  — it's **derive MEP anchors from ARC** (mains/risers from cores → branches to fixtures), like STR. Then it generalises.
- **Terminal too complex for the LIVE start — keep it as the ORACLE only.** 33k-plate space-frame + 48k elements + MEP
  frame mismatch. **Lay the modeller out on a SIMPLE building (SC / Duplex / SampleHouse — wall-bearing or small column
  frame); Terminal stays the dev walk-back oracle behind it.** (Also the mission's "simple set first".)
- **So the new session's opening move:** pick a SIMPLE building → drop ARC → walk STR (lead) over meta.db + derived graph
  → guarded + calibrated confidence in the Outliner DISC/ARC tab. Then layer MEP (ARC-derived anchors). Tasks 1-6 below.

## §0.1 CONTEMPLATE (user, 2026-06-26 — decide these as you lay it out)
- **The 33k roof plates = cladding, NOT structural skeleton.** STR ("Structural") = the load path: columns → beams →
  space-frame STRUTS (IfcMember). The 33,324 IfcPlates are the roof SURFACE/skin clad on the lattice. **Treat the plating
  as ONE roof surface (envelope/cladding piece with an area); explode to per-plate (`instanced-by n`) ON DEMAND only**
  (cladding quantities/5D). ⇒ This DE-COMPLEXIFIES Terminal: not 48k structural things, but ~1k skeleton/struts + 1 roof.
  The currently-under-walked **IfcMember struts (442) ARE the structural gap to close** (not the plates). Walk struts;
  surface the roof as one piece. (Lay out on a simple building first regardless.)
- **Open a building → ARC drops into the OUTLINER as a branching graph/BOM** (Building→Storey→Room→elements + typed
  cross-edges) = the bom-graph tab under DISC/ARC; the 3D canvas is the spatial mirror. The Outliner is the PRIMARY drop surface.
- **Storey/Room ≈ ARC in disguise.** IfcBuildingStorey + IfcSpace are the architectural SPATIAL STRUCTURE — the
  containment scaffolding; the **"contains" edge (BOM-tree backbone) IS the Storey/Room decomposition** = the first two
  branch levels of the Outliner tree. DERIVE them (storey from Z-bands/storey field; room from IfcSpace) — ⚠ caveat: some
  buildings have NO IfcSpace (rooms = weak handle) → need a spatial-clustering fallback for rooms. Not a separate discipline.

## STATE (2026-06-26 — what's DONE, all witnessed, all pushed)
- **bim-compiler `lane/benchmark-clash-resolution`** (pushed): walker_guards.js (W-GUARD-* 8/8), walker_confidence.js
  (W-CONFIDENCE-CALIBRATED 6/6, shipped `WC_CALIBRATION` map), witness_walkback_str.js (W-WALKBACK-STR 5/5, D1),
  witness_guard_rotated.js + `wgFit` (W-GUARD-ROTATED 5/5), str_walker_bridge.js swbInit AUTO-PICK column-framed vs
  wall-bearing (W-STR-BRIDGE-ARCONLY 5/5) + per-girder calibrated confidence feed (W-STR-BRIDGE 6/6).
- **bim-ootb PR #533 MERGED → LIVE on GH Pages** (sw v729): Outliner confidence highlight render + walker_confidence.js
  + `Terminal ▸` loader. ⚠ It merged carrying the WRONG DB (27MB extracted.db, not meta.db) → task 1 is now a LIVE
  correction, not a pre-merge swap. Verify live: `red1oon.github.io/bim-ootb/viewer/modeller.html?strwalk` → 🏗/Terminal ▸.
- D1 (walk-back metric) + D2 (isotonic/PAV, ECE 0.05, held-out) pinned. ✅ **W-WALKBACK-MEP UNBLOCKED 2026-06-29**
  (`scripts/witness_walkback_mep.js` 8/8): the ⛔ was a substrate gap — `routeChains` on a REAL MEP-bearing extracted.db
  (Terminal/Duplex-MEP) emits 0→N (5317/358 segs), one frame by construction. The old RouteWalker JUNCTION/frame block
  doesn't apply to this engine. See WALKER_GUARDS §5 + [[reference_erp_db_pattern_store]].

## THE ARCHITECTURE (decided this session — see memory project_walker_guards_rosettastone §ARCHITECTURE)
Modeller deploys via **GitHub Pages** (merge to main = deploy; OCI sandbox = the VIEWER, different app).
Substrate = **pristine meta.db (bboxes)**, NOT the TE_BOM. Structure DERIVED at runtime like the Find panel; the
**bom-graph tab under DISC/ARC** = the SDG (containment from spatial nesting + typed cross-edges) over that substrate;
disciplines are walker tabs. **Cache-first Modeller.Open** over IndexedDB keyed by building IDENTITY (dynamic), with a
**`mo_<key>` editable instance** folding the op-log while the loaded **reference stays pristine**. Render = bbox-instant
+ mesh-lazy + Alt-X focus-LOD (reuse Viewer DLOD). Dev/user split: users walk ARC-only with NO oracle (guards +
shipped calibration keep it honest); we (dev) have the Terminal RosettaStone to walk-back-check + fit calibration.

## NEXT (bounded tasks, in rough order — one per session, witness-first)
1. **~~Swap the GH-hosted seed to meta.db.~~ ✅ MOOT (resolved 2026-06-26).** The merged #533 ships NO hosted Terminal
   seed — the live STR Walker opens via a **`<input type=file>`** (`str_walker_outliner.js openStrDb`); NO `Terminal_*.db`
   is git-tracked, so nothing is served on GH Pages. The substrate concern (meta.db vs cooked extracted.db) reduces to
   "the user picks the file"; can't enforce meta-vs-extracted on a manual pick. Doctrine stands — meta.db is the pristine
   truth — but there is no hosted seed to swap. (If we later add a hosted `Terminal ▸` button, point it at meta.db then.)
2. **~~Live-port the bridge to bim-ootb.~~ ✅ DONE 2026-06-26 — bim-ootb PR #534 (auto-merge SQUASH armed, sw v729→v730).**
   ootb `str_walker.js`/`str_walker_bridge.js` PREDATED `swDeriveSemiGrid` + the swbInit auto-pick → a wall-bearing
   ARC-only open would THROW (swDeriveSemiGrid undefined). Refreshed both from witnessed bim-compiler deploy/dev
   (byte-identical); `str_walker_outliner.js` (#533 UI) + `walker_confidence.js` untouched. swbInit now auto-picks
   column-framed (STR cols) vs wall-bearing (no cols → swDeriveSemiGrid from ARC walls, no frame fabricated). Witnessed:
   W-STR-BRIDGE-ARCONLY 5/5, W-STR-GENERAL-SC 6/6, W-STR-BRIDGE 6/6 (identical source) + live-wiring smoke (window-loaded
   swbInit on a no-STR-columns stub → wall-bearing 2×2 semi-grid, swbTabData no throw). ?v bumps v1→2 / v2→3.
2.5 **✅ DONE 2026-06-26 — 4 permanent residents + guided Open (bim-ootb PR #536, auto-merge SQUASH, sw v730→v731).**
   The STR Walker is now a GUIDED tool: a "▾ Open building…" picker lists SampleHouse/Duplex (wall-bearing),
   Schependomlaan/Terminal (column-framed) → on pick FETCHES the pristine meta.db from the OCI building bucket
   (`buildings/<X>_meta.db`) → caches it in `bim_ootb_cache`/`dbs` (PR #517 pattern, creates store via version+1 if
   absent) so next Open is LOCAL → walks it (auto-pick). 🏗 local-file open kept. **Hosting reality: building DBs live
   on OCI bucket `bim-ootb` (PROD_BASE), NOT GH Pages — `*.db` is gitignored; db_resolve heals `buildings/<file>`→OCI.**
   Produced 3 meta.db via `scripts/make_resident_meta.sh` (projection from extracted.db: keep substrate, DROP m_bom +
   mesh blobs) + uploaded to OCI (Terminal_meta already there, served gzip→auto-inflate). Witnessed **W-RESIDENT-OPEN
   13/13** (bim-compiler, `scripts/witness_resident_open.js`) against the LIVE bucket: all 4 fetch+open+walk with right
   system/grid (SH 2x3 / DX 9x6 / SC 8x9·23col·13gird / TE 18x10·158col·108gird), pristine, outliner lists all 4.
   ⚖ NOTE: component_library.db MEP/FP mesh audit (user ask) — FP/SP/ELEC/MEP all HIGH quality (0 box proxies; FP avg
   434v, SP avg 2267v); FP `IfcPipeSegment_SJTII_Terminal_*` (3787) + SP (442) genuinely Terminal-sourced; ELEC/MEP/ACMV
   from JKR reference libs (jkrME18/jkrME/E_Light), not Terminal, but also high quality.
3. **✅ DONE 2026-06-26 — `mo_<key>` editable instance (bim-ootb PR #537, sw v731→v732).** Opening a resident forks a
   per-building editable instance: `bonsai_oplog.js setModelKey('mo_<building>')` persists each resident's signed op-log
   under its OWN localStorage key (was a single global `bonsai_model_v1`), so each carries its own edit history while the
   loaded meta.db REFERENCE (IndexedDB `bim_ootb_cache`) is never mutated — separate stores ⇒ structurally pristine.
   `str_walker_outliner.js openResident → _forkEditable`. Witnessed **W-MO-INSTANCE 8/8** (`scripts/witness_mo_instance.js`
   drives the SHIPPED OpLog+kernel_ops in node): fork→signed-edit→isolated-switch→restore-own-history→separate-stores→
   reference writes only mo_*/default keys. **✅ Follow-on DONE 2026-06-26 (bim-ootb PR #538, sw v732→v733): VISUAL replay
   on reopen.** Bridge `swbReplay(edits)` re-applies recorded `{axis,datum,delta}` via the SAME snap+swReWalk as the live
   path but NO commit; a grid move now persists a compact `STR_WALK_EDIT` op; `_forkEditable→_replayEdits` reads those rows
   after setModelKey loads the mo_ log → re-folds the walk so prior edits re-appear. Witnessed **W-STR-REPLAY 5/5**
   (`scripts/witness_str_replay.js`, real Schependomlaan: replay reproduces the live-edited swbTabData BIT-FOR-BIT, no
   re-commit). swbInit/swbOnGridMove unchanged (W-STR-BRIDGE/-ARCONLY/-GENERAL-SC still green). **Task-3 ARC COMPLETE:
   cloud→local residents → per-building editable instance → edits survive reopen, reference pristine throughout.**
   [legacy task-3 note] Open enumerates `bim_ootb_cache` by building identity (NOT fetch-URL);
   serves the folded DB; forks `mo_<key>` for edits; reference untouched. Reuse kernel_ops._persistToIdb (already folds).
   Remote (short meta.db URL) = cold seed only. Witness: drop→cache→open serves same entry; mo_ edit leaves reference clean.
4. **The bom-graph tab under DISC/ARC** — ✅ CONTAINMENT BACKBONE DONE 2026-06-26 (bim-ootb PR #539, sw v733→v734).
   Opening a resident seeds the Outliner bom-graph tab = branching containment tree building→storey→ROOM→disc→class→
   element, DERIVED from the same meta.db. REUSED the witnessed `bom_tree.js` engine (+ its signed BOM_REPARENT) — added
   `seedFromDb(db)` (room level from the spatial `contains` edge rel_contained_in_space⋈spatial_structure; room MEASURED
   class-agnostically = a container WITH A VOLUME (size_x present) — no IFC literal, grep-clean holds) + `loadFromDb`
   shared by 📂 Open and the guided resident Open; `?strwalk` registers it alongside the STR Walker. Witnessed
   **W-BOM-GRAPH 16/16** (`scripts/witness_bom_graph.js`, 4 real residents: lossless leaf==elements_meta, rooms
   real+complete Duplex 11/Terminal 42, graceful 0-room SH/SC, nothing invented); W-BOM-TREE-EDIT 15/15 unchanged.
   ⛔ FOLLOW-ON (next slice): **the typed CROSS-EDGES** (hosted-by/abuts/anchored-to/spans/instanced-by-n — the 6 SDG
   edges, already derived in extractIFCtoDB.py + witnessed house+bridge in bim-compiler) DERIVED in JS over meta.db,
   rendered Find-style ON the containment backbone. That is the "graph" half (this slice shipped the tree half).
5. (lower) RTree over bboxes for instant clash/adjacency at 48k (optional perf; `CREATE VIRTUAL TABLE … USING rtree`).
6. ~~(blocked) W-WALKBACK-MEP~~ — ✅ UNBLOCKED 2026-06-29 (see line 145 above / `Modeller/DISC_Walker/WALKER_GUARDS_ROSETTASTONE_SPEC.md`
   §5) — stale duplicate of this same file's own line 145, left unfixed until 2026-07-02. Don't re-list as blocked
   again. Remaining piece is render/deploy only: wire `routeChains` MEP net into modeller §8E-3 + `__dwPixelProbe`.

## ▶ 2026-07-07 M1 BUILT — bridged to routewalker.js's pattern engine (bim-ootb `03fa2f3`, PR #683,
`feat/mep-seedtrunk-network`) — ✅ DONE (witness)

Built exactly the REDIRECTED scope above, not the earlier "extend seed_trunk.js" framing (a fresh clarifying
question to the user went unanswered after 60s — proceeded on the file's own explicit redirect text, which was
unambiguous and load-bearing enough to act on).

**What shipped:** `disc_walker.js` gained `routePattern(disc, bdb, opts)` — derives METER (defaultSeed's door),
STACK (defaultSeed's stair), FIXTURE (place()'s own measured output), JUNCTION (SeedTrunk.planTrunk's corridor
backbone, sampled every 3m into waypoints) LIVE from an opened building's real substrate, then hands them to
routewalker.js's own `_rwLoadPatternSteps`/`_rwPairSegments` — the SAME pairing/gradient/clash-skip code the
Java-side `RouteWalkerTest.java` W-PATTERN-CW/W-PATTERN-SP tests exercise (verified: 7 `@Test`s, HospitalAuckland
fixture, real). Wired as `dwWalk`'s fallback when `routeChains` returns 0. `_discWalkOne` (modeller.html)
lazy-loads `mep_rw.db`'s pattern table (`rwInit`, gated to `disc==='PLB'` only) before the walk.

**Measured, not assumed, scope (per the redirect's own "measure it, don't assume" instruction):** queried
`ad_mep_pattern` directly — it carries rows for exactly TWO disciplines, `CW` (pressurised cold-water supply)
and `SP` (gravity soil/waste drain), both PLUMBING sub-networks (routewalker.js's own `RW_DISC_TO_COORD` maps
CW/SP/PLB all to DWATER/DRAIN). So the bridge covers disc_walker's `PLB` discipline (a CW pass + an SP pass).
ELEC/ACMV have ZERO `ad_mep_pattern` rows — the bridge HONESTLY REFUSES for them (`routePattern('ELEC',…).refused
=== true`, reason states no coverage) — verified live, not claimed from the parameterization. Covering them
would need someone to mine+author their own pattern rows first (a data task, not a code generalization).

**Two pre-existing latent bugs in `routewalker.js` found + fixed this pass** (not introduced by the bridge —
just the first thing that ever exercised this code path in a context where `mep_rw.db` can 404, since the
isolated modeller currently only ships `viewer/mep_rw.db`, not `modeller/mep_rw.db` — confirmed live 404 on
`red1oon.github.io/bim-ootb/modeller/mep_rw.db`, a SEPARATE, still-open regression from the modeller/viewer
isolation work, flagged here for a future session, not fixed — witnesses fixture-serve it locally):
1. `_rwFetchCached`'s `idb.transaction('dbs',…)` threw an UNCATCHABLE pageerror (bypasses every `.catch()` in
   the chain — it's a DOM event-handler throw, not a promise rejection) when `bim_ootb_cache` already existed
   without a `'dbs'` store — real regression confirmed on a fresh IFC-open session (`W-E2E-WALK-IFCOPEN` dropped
   to 12/18). Fixed: guarded in try/catch, falls back to a bare fetch.
2. `rwInit` set `_rwReady = true` BEFORE validating the fetched DB via a real query — a failed/404 fetch left
   `_rwReady` wrongly `true` over a dead handle, so the bridge's own readiness guard passed and then crashed
   deep inside `_rwLoadPatternSteps`. Fixed: flip `_rwReady` only after the validating query succeeds.
3. (minor, same fix) the two fallback fetch branches never checked `resp.ok` — a 404 body was silently accepted
   as "the database" (confirmed: literal `"404"` bytes reached `new SQL.Database()`, throwing "file is not a
   database"). Now guarded like the primary path always was.

**Witnessed:** `witness_route_pattern_bridge.js` (new, bim-ootb `modeller/tests/`, 10/10) — on ARC-only Duplex
(zero real MEP elements, confirmed): `routeChains('PLB',bdb)` stays 0 (the honest premise), the bridge flips
`dwWalk`'s `chainSegs` 0→55 (13 CW + 42 SP), anchors are real (door guid, stair guid, 108 corridor junctions),
ELEC/ACMV honestly refuse, the REAL production path (`window.discWalk`) renders the chain as tubes, commits,
verifies, and reverses cleanly. **Regression, all re-run against the branch:** `W-E2E-WALK` 8/8, `W-E2E-WALK-ALL`
10/10, `W-E2E-WALK-IFCOPEN` 18/18 (was 12/18 before the two routewalker.js fixes — traced to source, not just
patched around), `W-SEED-TRUNK-RENDER` 8/8. `W-TERMINAL-WALKALL-PERF` (puppeteer DOM-detach on the Outliner row)
fails IDENTICALLY on unpatched `origin/main` — confirmed pre-existing, unrelated, not this branch's fault.

**NEXT (M2, a fresh session):** the joint STR+MEP quality witness — walk STR then this M1 PLB network on ONE
building, check no clash against STR members, reusing `walker_guards.js`'s clash-gate. Also open: (a) the
`modeller/mep_rw.db` 404 regression noted above (separate, pre-existing, not blocking M1/M2), (b) `assemble()`
(catalog-part instantiation) was NOT wired to consume the bridge's segments — routePattern's segs use synthetic
`from_kind`/`to_kind` ('RW_CW'/'RW_SP'), not real IFC classes, so `assemble()` would find no catalog match today;
not attempted this session (M1 was scoped to the network existing, not full catalog-part instantiation).

## ▶ 2026-07-07 M2+M3 BUILT — joint STR+MEP clash witness, priority-order guard wired live (bim-compiler
`scripts/witness_str_mep_clash_gate.js`, new, 6/6) — ✅ DONE (witness)

Built on M1 (bim-ootb `03fa2f3`/PR #683, merged to main as `a5a514c`). Building: SampleCastle (column-framed,
duplex_rules.db → M1's PLB bridge applies, 23 REAL IfcColumn queried directly from `elements_meta`/
`element_transforms`, same query `str_walker_bridge.js`'s own `_readColumns` uses).

**Mechanism:** `walker_guards.js` (bim-compiler `deploy/dev/`) is NOT ported into bim-ootb's browser — it only
ever existed as a bim-compiler node/browser dual-export file with its own witness suite. Rather than port it
into modeller.html (a larger, separate architectural step this scope didn't ask for), the witness drives
puppeteer against a real bim-ootb checkout to extract REAL STR columns + M1's real PLB `chainSegs`, then
`require`s `deploy/dev/walker_guards.js` directly in Node and runs `wgClash`/`wgRunPass` on that real data.

**First attempt was noisy — found + fixed a witness-design bug before trusting the numbers:** feeding all 63
MEP corridor segments into ONE combined `wgRunPass` bus (STR then MEP) reported 31/63 MEP segments "hard
clashed" — but reading the actual `withId` partners showed they were almost all MEP-vs-MEP (adjacent corridor
segments sharing a joint — expected connectivity, not a clash; `wgClash`'s single-worst-partner report can't
tell "touches its own network neighbour" from "touches real structure"). Fixed by calling `wgClash` directly
against an **STR-only bus** for the MEP-vs-STR question (still walker_guards.js's own clash-gate function, just
not routed through the mixed bus for this specific measurement) — this is exactly the "don't trust a PASS /
verify the assertion code" discipline the STANDING AGENDA demands, applied to my own witness before reporting it.

**Real finding, measured not assumed:** 1 of 63 M1-bridged PLB segments genuinely penetrates a real STR column
by 2.6cm (`MEP-14` vs `STR-15`) — confirming M1's own bridge clash-gates ONLY against ARC walls/slabs/roof/
covering (`_rwLoadArcEnvelope`'s WHERE clause), never STR members. This is a real, small, honestly-reported
gap — NOT fixed in this session (a follow-up: extend the pattern-bridge's ARC-envelope query to also include
STR columns/beams, or feed the real STR bus into `routePattern`'s own clash check — a bounded, separate task).

**Order-matters proof (closes step-3's "prose not enforced" gap):** the SAME real overlap, checked with
priority order REVERSED (MEP=10 "walked first", STR=60 "walked after"), refuses the REAL STR COLUMN instead of
the generated MEP segment — a concrete, measurable, wrong outcome. Priority order is not decorative: getting it
backwards makes the guard blame ground-truth structure for a generated route's overlap.

**`wgRunPass` (the orchestration wrapper, not just `wgClash`) is now genuinely called** on real combined
STR+MEP candidates, completes deterministically (86 candidates in, 52 placed + 34 refused, bus consistent) —
this witness IS the first live call M3 found missing.

**Witnessed:** 6/6 — C1 real STR members, C2 real M1 network (routeChains bare stays 0), C3 clean clash
measurement (1/63, honest), C4 order-matters (symmetric 1/23 the wrong way), C5 wgRunPass live end-to-end, C6
no-error. No bim-ootb code change needed (pure measurement witness); nothing pushed to bim-ootb for M2.

**NEXT (a fresh session, not attempted here):** close the found gap — extend the pattern-bridge's clash check
to include real STR members (not just ARC envelope), re-run this witness expecting 0/63. M4 (the construction
animation over M1's network, reusing `seed_trunk.js`'s T2 reveal mechanism) is next per the campaign's own order.

## ▶ 2026-07-07 FOLLOW-UP FIXED — the 2.6cm STR-column penetration is closed (bim-ootb `1185f20`, PR #684,
`fix/mep-str-clash-gate`) — ✅ DONE (witness), done BEFORE M4/M5 per instruction (don't animate a network
with a known real clash in it)

Started from the obvious fix (feed the same STR bus M2 already isolates into M1's own clash-gate check) —
added `_strEnvelope(bdb)` to `disc_walker.js` (real columns/beams/struts, same box shape as routewalker.js's
own ARC query) and concatenated it into the envelope `routePattern` passes to `_rwPairSegments`. **That alone
did NOT change the segment count** — re-ran `witness_route_pattern_bridge.js`/`witness_str_mep_clash_gate.js`
and the 2.6cm penetration was still there. Didn't stop at "I added the fix, should be done" — traced further
(the STANDING AGENDA's own "don't trust a PASS" applies to my own fix, not just witnesses) and found the REAL,
deeper, pre-existing cause: `routewalker.js`'s own `_rwPairSegments` clash-skip calls `_rwClashesWithArc` with
a box shaped `[crossSection, crossSection, LENGTH]` — it ALWAYS treats the THIRD (Z) axis as the pipe's long
axis, regardless of whether the run is actually horizontal (the common case for a corridor/pattern run). A
horizontal run's clash box is therefore a thin 0.075m column at its midpoint that never reaches sideways
along the run's real path — the missing STR envelope was never the actual blocker.

**Fix, NOT inside routewalker.js** (a separate file with its own witness suite, out of scope to widen into
further this session): `disc_walker.js` gained `_envelopeClash(from, to, envelope, halfWidth)` — a correctly
axis-aligned AABB check (safe because every `ad_mep_pattern` step declares an explicit `direction_axis`
X/Y/Z — these runs are axis-aligned BY CONSTRUCTION, no OBB/rotation math needed) — applied as an
authoritative POST-filter on `_rwPairSegments`' output inside `routePattern`, using routewalker.js's own
measured pipe cross-section constant (`RW_PIPE_CROSS/1000/2`), not an invented tolerance.

**Consequence, honestly reported, not hidden:** the corrected gate also catches real ARC-wall penetrations the
SAME pre-existing box-orientation bug was hiding — the JUNCTION-waypoint nearest-neighbour pairing (dense
corridor samples, not path-following) produces straight-line shortcuts that can cut through walls, not just
the one STR column M2 found. Result is a STRICTER, SMALLER, but now genuinely non-clashing network (SampleCastle
PLB: 63→40 segments; Duplex PLB: 55→34). This is a correctness improvement (refuse-beats-fabricate), not a
regression — a smaller guaranteed-clean network beats a larger one with real penetrations in it.

**Witnessed:** re-ran `witness_str_mep_clash_gate.js` (also fixed a durability bug in it — the modeller root
was hardcoded to an ephemeral `/tmp` worktree; now `BIM_OOTB_ROOT`-overridable, defaults to `~/bim-ootb`) —
MEP-vs-STR clash is now **0/40** (was 1/63). Regression: `witness_route_pattern_bridge.js` 10/10, `W-E2E-WALK`
8/8, `W-E2E-WALK-ALL` 10/10, `W-E2E-WALK-IFCOPEN` 18/18, `W-SEED-TRUNK-RENDER` 8/8 — all green.

**NEXT:** M4 (the animation) can now proceed — the network it will reveal is verified clash-clean against both
ARC and STR. M5 (elbow/fitting orientation) and the newly-noted non-orthogonal-building rotation gap remain
open, separately scoped, not blockers for M4.

## ▶ 2026-07-07 M4 BUILT — construction reveal wired over M1's PLB network (bim-ootb `feat/mep-seed-reveal`,
PR #686) — ✅ DONE (witness), a real design correction found+applied inline, not shipped wrong

Scope per the campaign: reuse `seed_trunk.js`'s T1/T2 reveal mechanism (`_renderSeedTrunk`'s `drawRange`
animation, seed-outward ordered, `prefers-reduced-motion` safe, ends EXACTLY on the proven geometry) over M1's
now clash-clean PLB pattern-bridge network (modeller.html `_renderDiscChains`) — not a second animation system.
`InstancedMesh.count` is the instancing analogue of `drawRange` (both are a draw-count the renderer respects;
verified live, not assumed).

**Ordering design corrected mid-session, measured not assumed (this is the "don't trust a PASS" discipline
applied to my OWN first design, per the STANDING AGENDA):** first attempt ported `_renderSeedTrunk`'s own
technique verbatim — a BFS over the segment graph (nodes = endpoint coords, edges = the segs) rooted at
routePattern's real METER(CW)/STACK(SP) anchor. Running the new witness against a REAL Duplex PLB walk found
**100% of segments (34/34) unreachable from the anchor** — not a coding bug (key-matching was correct), a real
structural fact about this network: the FOLLOW-UP fix's own `_envelopeClash` post-filter (closes the STR/wall
penetration gap) removes the anchor-ADJACENT pairs on this building (10/13 CW, 9/40 SP candidates dropped), and
the survivors turn out to be **mostly-disconnected short fragments** (measured: CW's 3 segs = 3 isolated pairs;
SP's 31 segs reach only 4/56 nodes from an arbitrary start) — nothing like SeedTrunk's guaranteed-connected
corridor polylines. A graph BFS has nothing to walk on data shaped like that. **Corrected `_seedOutwardChainOrder`
to order by straight-line distance from each segment's own real anchor instead** — still "invents no order
beyond distances the pattern-bridge's own topology encodes" (the invariant that mattered), just measured
point-to-point rather than assuming a walkable backbone this network doesn't have. Documented the wrong-then-
right path directly in the code comment, not silently swapped.

**Self-cancel bug found + fixed while proving this live (not a hypothetical — the first live run showed
`chainTubes=0`):** the SAME walk flow that starts the reveal also `await`s `_commitDiscChains` right after,
which folds the op-log and calls `_redrawAllDiscWalks()` — that redraw rebuilds every chain's InstancedMesh
fresh (necessary in general: `commitSeedGroup` replaces the editable THREE group), which was either tearing
down the just-started animation (an early "skip if animating" attempt left the disc unrendered entirely — worse)
or restarting it from 0. Fixed by keying the ease curve off a **stored wall-clock start timestamp**
(`window.__dwChainRevealMeta[disc] = {start, gen}`) instead of a local closure `t0` — a fold-triggered rebuild
mid-reveal now seeds the fresh InstancedMesh's `.count` at the correct current progress and continues the SAME
timeline (no flash-to-0, no early full-reveal); a generation token lets any now-orphaned rAF loop from a
superseded render call detect it and quietly stop instead of touching a detached mesh.

**Witnessed:** `witness_seed_outward_reveal.js` (new, bim-ootb `modeller/tests/`, 9/9) — real Duplex PLB walk via
the production `window.discWalk` path: R1 every segment has a real anchor (unreached=0), R2 independent
recompute confirms distance-from-anchor is non-decreasing along the returned order (not a re-run of the same
function's internals), R3 animated reveal (mid-flight count < full, ends EXACT==net, >1 frame), R4
reduced-motion instant fallback, R5 `opts.instant` fallback (the redraw-after-scrub path), R6 a real nn-chain
network (no patternBridge) renders full count immediately with no reveal engaged — proves the change is scoped
to the pattern-bridged network only. Regression, all re-run against the branch: `witness_route_pattern_bridge.js`
10/10, `W-E2E-WALK` 8/8, `W-E2E-WALK-ALL` 10/10, `W-E2E-WALK-IFCOPEN` 18/18, `W-SEED-TRUNK-RENDER` 8/8 — all
green (confirmed the reveal engages ONLY for PLB's pattern-bridge network; Terminal's real MEP nn-chains and
seed_trunk's own trunk render are untouched). `witness_modeller_router_nnchain.js` NOT re-run this pass — this
environment is missing the `playwright` module it requires (pre-existing tooling gap, unrelated to this change;
it targets Terminal's real MEP chains, which structurally cannot engage the reveal per R6's own proof).

**NEXT:** M5 (elbow/fitting orientation) and the non-orthogonal-building rotation gap (M6) remain open,
separately scoped. A secondary, honestly-flagged limitation from this session: during a "Walk ALL Disciplines"
run, a LATER discipline's own commit still calls `_redrawAllDiscWalks()` for an EARLIER discipline's in-flight
PLB reveal — the resume-fix handles this correctly (same timeline continues on the rebuilt mesh), so it is not
a correctness bug, just not separately witnessed in a multi-discipline sequence this session (single-discipline
walk — the primary "MEP genuinely walking into the scene" moment — is what R1-R8 above prove).

## ▶ M5 SPEC — elbow/tee fitting placement at bends (2026-07-07, user decision: build real fitting meshes,
not just a continuous tube — dispatched as its own session, kept in THIS file per one-topic-one-file convention)

Grounded against the real code before writing this (non-invent), not assumed:

1. **Fitting meshes already exist but are gated off.** `viewer/dagevu_catalog.json` (loaded by
   `bonsai_library.js:34-42`) has real `IfcPipeFitting` entries with real mesh geometry in
   `dagevu_geometries.json` — `FITTING_ELBOW_GENERIC`, `FITTING_TEE_GENERIC`, `FITTING_BEND_PVC_DWV`,
   `CONDUIT_ELBOW_STEEL`, each with ~10-15 size variants. **All carry `asmOnly: true`**
   (`bonsai_library.js:38`) and `asmOnly` has NO consumer anywhere except BOM-assembly child roles
   (`expandAssembly()`, `bonsai_library.js:157-190`) — today they're unreachable by a general
   "place at this computed point" call. `ad_mep_pattern.piece_type` (driving RouteWalker) has only
   ever been seen as `'PIPE_STRAIGHT'` in real data — confirmed by direct query, no elbow/tee rows
   exist in the walked pattern data itself.
2. **Placement mechanism to reuse, not invent:** `Bonsai.library.foldInsert(op, mv, gridCmds)`
   (`bonsai_library.js:395`) reads `op.parameters.placement = {x,y,z,rot}` and renders via `place()`
   (`bonsai_library.js:67-99`, yaw-about-+Z in degrees, or full quaternion when `rotX/rotY` present).
   Real call-site shape to mirror: `{op_type:'GEOM_INSERT', params:{hash, placement:{x,y,z,rot}},
   outputGuid}` (`arc_editable.js:229-231`). A fitting placement is a normal `GEOM_INSERT` — no new
   placement path needed, only a new caller.
3. **Bend detection does not exist yet — real new logic.** `_rwPairSegments()`
   (`routewalker.js:426-475`) → `rwSweepOps()` (`routewalker.js:251-266`) maps EACH segment
   independently to its own `GEOM_SWEEP`, with no adjacency/merge logic between segments today. A
   bend-finder must walk `rwRouteSegments()`'s (`routewalker.js:213-243`) segment list and group
   entries whose `from`/`to` COINCIDE (shared anchor) but whose `axis`/direction differ — that
   grouping is new code, not a wire-up.
4. **Fitting rotation math is genuinely new — the real risk item, not a copy job.** Searched for
   existing bisector/miter/`atan2` two-vector-to-rotation logic anywhere in this project
   (`disc_walker.js`'s SIDE/TOP/BOTTOM mount branches, `arc_editable.js`'s rotation handling) — NONE
   found; every existing rotation in this codebase is either a stored single `rotation_z` or a
   dominant-AABB-axis pick (0 or π/2 only, the same M6 heuristic). **This task must write real
   angle-bisector trig between two adjoining run direction vectors** — this is the one part of M5
   that is actual new engineering, not reuse; scope and test it as such, don't assume it's as cheap
   as the placement/detection halves.

**Task breakdown:**
1. Bend-finder: group `rwRouteSegments()` output by shared anchor + differing axis (item 3).
2. Fitting-type pick: 2-way bend → elbow, 3-way junction → tee, matched against real catalog hashes
   from item 1 (lift or bypass the `asmOnly` gate — decide explicitly which, don't silently ignore it;
   bypassing needs a documented reason since the gate exists for BOM-assembly integrity elsewhere).
3. Rotation: real bisector math between the two (or three) adjoining direction vectors (item 4) →
   feed into the same `{x,y,z,rot}` placement shape as any other `GEOM_INSERT` (item 2).
4. Commit as signed `GEOM_INSERT` ops riding the same op-log as every other component placement —
   no new persistence mechanism.
5. Witness: at minimum — a 90°-bend Duplex/SampleCastle case places a real elbow mesh oriented to
   BOTH adjoining runs (checked against hand-calculated bisector angle, not eyeballed); a 3-way
   junction places a tee; straight-through (non-bend) anchors get NO fitting (regression: M4's
   continuous-tube rendering must be unaffected where there's no real bend); tamper/determinism
   follow the same signed-op-log discipline as every other op in this project.

**Non-invent guardrails:** fitting mesh choice comes from the real catalog (item 1), never a
placeholder box; rotation is computed from the real adjoining segment vectors, never a fixed/assumed
angle; the `asmOnly` bypass decision (if taken) is stated with its reasoning, not silently done.

**Status: SPEC WRITTEN, dispatched 2026-07-07** — background session, isolated worktree, to be
created same pattern as M1-M4 (fresh worktree off current `origin/main`, PR back to bim-ootb on
completion).

## ▶ RESUME HERE (2026-07-10, NEW bounded follow-up — independent of the disc_walker MEP-schedule branch;
do NOT block that branch's Watchdog sign-off on this, the two lanes don't intersect)

**Origin: surfaced as a byproduct of Watchdogging `fable/bimeyes-coherence-checker`, not this file's own
thread.** Re-running that branch's 25-file DW regression tally, this file's own `scripts/witness_resident_open.js`
(W-RESIDENT-OPEN, §2.5 above, originally 13/13 on 2026-06-26) came back **3 PASS / 4 FAIL** —
SampleHouse/Duplex/Schependomlaan `_meta.db` all now **404 on the live OCI bucket**; only Terminal still
fetches+walks clean. Confirmed unrelated to the disc_walker branch (it never touches `deploy/dev/str_walker*.js`)
— standalone production drift, found only because the branch's sweep happened to run it.

**Two separate items here, different urgency — don't conflate them into one task:**
1. **⛔ LIVE-BROKEN, higher priority — 3 of 4 STR-Walker resident buttons are dead in prod today.** A real user
   opening the STR Walker's "▾ Open building…" picker and choosing SampleHouse/Duplex/Schependomlaan gets a
   404, not a building. Root cause not yet diagnosed (bucket path change? object deleted? — check the OCI
   bucket directly, don't assume). Regression against the PR #536 baseline (13/13 → 3/4) — needs its own
   bounded fix-and-reverify pass.
2. **Design debt, lower priority, no rush — the STR-Walker resident picker still cloud-fetches 4 residents
   one-by-one, never reconciled with the embedded 8-building `mesh.db` substrate this project already shipped**
   (`EMBED_8_ARC_BUILDINGS_MESH_DB.md`, DONE). User direction (2026-07-10): end state is NOT "keep 4, make them
   embedded" — it should converge with the already-embedded 8 (SH/DX/SampleCastle/Terminal/Clinic/HHS/Garage/+1),
   and eventually carry DISC (MEP) meshes alongside the STR/ARC meshes already in `mesh.db`. **Naming collision
   to watch for:** this witness's "SC" = Schependomlaan (column-framed steel case) — NOT SampleCastle, which is
   what "SC" means everywhere in the disc_walker lane. If the two resident lists ever merge, that needs an
   explicit rename, not a silent overwrite.

**Scope for whoever picks this up:** bounded to items 1+2, both live in `str_walker*.js`/OCI-bucket territory,
NOT `disc_walker.js` — does not touch or gate the `fable/bimeyes-coherence-checker` branch, which proceeds to
Watchdog sign-off independently. Fix item 1 first (it's a live regression, not just debt); item 2 can follow in
the same session or a later one.

## ▶ 2026-07-10 RESOLVED — item 1 was a FALSE ALARM (stale witness, not a live break); item 2's premise
was also stale — both corrected, not just re-diagnosed

**Item 1, real root cause found (checked the OCI bucket directly, per the file's own instruction — did NOT
assume):** `oci os object list` confirmed `SampleHouse_meta.db`/`Duplex_meta.db`/`Schependomlaan_meta.db`
genuinely don't exist in `bim-ootb`'s `buildings/` prefix (bucket versioning is Disabled, no recovery trail).
**But this never mattered to a real user** — `~/bim-ootb` synced to `origin/main` and `modeller/
str_walker_outliner.js` read directly: `openResident()` fetches `_modellerBase() + res.db` where
`_modellerBase() = './'` — a SAME-DIR GH-Pages fetch, `OCI_BASE` is never referenced anywhere in the live
code. Verified live: `curl` on all 4 real GH-Pages URLs (`red1oon.github.io/bim-ootb/modeller/
{SampleHouse,Duplex,SampleCastle,Terminal_meta}...`) → **200 on all 4**, today. The "▾ Open building…"
picker was never broken. `witness_resident_open.js` was testing a RETIRED architecture end-to-end: OCI
`buildings/*_meta.db` fetch + a resident literally named "Schependomlaan" — both superseded THE SAME DAY
they were built, by the "SESSION 2026-06-26b" isolation decision documented earlier in this very file (GH-
Pages `modeller/` playground, zero OCI, SampleCastle replacing Schependomlaan). The witness just never got
updated to track that pivot, so when the 3 orphaned OCI objects later 404'd (unrelated bucket drift, cause
not chased further — nothing live depends on them) it read as "3/4 residents dead in prod."

**Fixed the actual defect — the witness, not the (already-working) product:** rewrote
`scripts/witness_resident_open.js` to fetch from the real `red1oon.github.io/bim-ootb/modeller/` base
against the CURRENT 4 residents (SampleHouse/Duplex/SampleCastle/Terminal, all `_extracted.db` or
`_meta.db` per the live `RESIDENTS` array), and dropped the "0 cooked tables" pristine assertion (C4) —
these GH-embedded files are legitimately self-contained bundles (`m_bom`/`component_geometries` by design,
confirmed by direct query on `SampleCastle_extracted.db`), not the split pristine-meta convention that
assertion was written for.

**A second, real (not invented) finding surfaced while re-deriving C3's expected values, then corrected
before landing — exactly the "don't trust your own first pass" discipline this file's STANDING AGENDA
demands of every session:** first attempt kept "column-framed / 8x9 / 23 cols / 13 girders" for SampleCastle,
assuming it inherited Schependomlaan's numbers (per a memory note calling them "the same source building").
Direct query proved that assumption WRONG — `SampleCastle_extracted.db`'s `elements_meta` is 100% `ARC`
discipline, 0 STR/MEP rows, confirmed by `git log` to be **deliberate**: bim-ootb `b93ca13` (PR #712,
2026-07-08, "strip all 4 residents to ARC-only") cascade-deleted every non-ARC row from ALL FOUR Modeller
residents — SampleCastle and Terminal used to carry real STR (column-framed), but as of #712 they legitimately
auto-pick **wall-bearing** now too (0 STR columns → `swDeriveSemiGrid` from ARC walls, no frame imposed),
same as SampleHouse/Duplex always did. This is the dev/user-split doctrine working as designed (users get
ARC-only, no oracle; STR/MEP are DERIVED live by the walker) — not a bug. Updated the witness's expectations
to the MEASURED post-#712 reality (SampleCastle wall-bearing/13x12, Terminal wall-bearing/38x32) instead of
the pre-#712 numbers a stale memory note nearly caused to be re-baked in. Re-ran: **13/13 PASS.**

**Item 2's premise also corrected while investigating item 1 (same evidence, no extra work):** "still
cloud-fetches... never reconciled with the embedded 8" was already wrong on its own first clause — the
picker does not cloud-fetch OCI at all (see above). The real state of the EMBED_8 convergence: per
`EMBED_8_ARC_BUILDINGS_MESH_DB.md`'s own text, the 8-building/`mesh.db` consolidation is verified-complete
but **NOT pushed** — it lives only in an abandoned/parked worktree (`/tmp/wt-embed-8-arc`, branch
`feat/embed-8-arc-buildings` off `origin/main`), so calling it "already shipped" was premature. Still lower
priority / no rush per the user's own 2026-07-10 framing — not picked up this pass, just corrected so the
next session doesn't start from "it's live" and get confused when `RESIDENTS` still shows the old 4-6 entries.

**Housekeeping:** the 3 orphaned OCI objects were harmlessly restored from the still-good local
`deploy/buildings/*_meta.db` artifacts (dated 2026-06-26, pristine, byte-sane) before this root cause was
found — doesn't help or hurt production (nothing reads them), left in place since re-deleting them adds risk
for zero benefit. `scripts/witness_resident_open.js` fix is uncommitted (working-tree only) — commit is the
user's call per this project's git policy.

## DON'T
- Don't rebuild the Viewer's DLOD/Alt-X/cache — wire it into the modeller.
- Don't claim Terminal reconstruction — Terminal is the walk-back ORACLE for the generative walk.
- Don't bake/host the cooked output.db or the m_bom-carrying extracted.db as the substrate — meta.db is the pristine truth.
- Don't fabricate to fill a walk gap — REFUSE (WALKER_GAP) + flag + low confidence.
```
