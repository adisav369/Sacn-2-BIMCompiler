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

## DON'T
- Don't rebuild the Viewer's DLOD/Alt-X/cache — wire it into the modeller.
- Don't claim Terminal reconstruction — Terminal is the walk-back ORACLE for the generative walk.
- Don't bake/host the cooked output.db or the m_bom-carrying extracted.db as the substrate — meta.db is the pristine truth.
- Don't fabricate to fill a walk gap — REFUSE (WALKER_GAP) + flag + low confidence.
```
