# PROGRESS — Current Development State

> **Rule:** PROGRESS.md is a thin status file. No specs here — specs live in `docs/` and `prompts/`. Keep this file under 80 lines.

## Session 2026-08-02 — 4D ordering CONFIRMED live, movie-maker batch shipped
User confirmed on a real Hospital bake: no roof before walls, no upper deck before lower, to Day 282.
Ordering half of the 4D lane is CLOSED (§4D_BAND_MONOTONIC #1129 + §4D_ROOF_LOAD_PATH #1120 + cache #1123).

**Shipped to `origin/main`:** §CPE_DAY_COUNTER_POS (#1130, sw v914) — Day # counter now visible in Preview
(hooks were never wired). §CPE_GAZE_ACQUIRE (#1131, sw v915) — gaze settles in 0.90s vs the flat cap's
2.00s, bit-identical; G-SH-4 witness now reads the shipped curve, not a literal 45 (#1132).

**NOT merged** (`feat/element-cpm`) — see OPEN §4D SUPPORT INVARIANT below: ruling given, root cause
measured, next step named.

**Open, specced, not built:** §CPE_ROOM_TITLE_MULTI (`prompts/CINEMA_PATH_EDITOR.md`) — caption several
rooms in view with a level prefix instead of one ray-picked room.

## Session 2026-08-03 — Offline install doc shipped, docs-publish blocked
`docs/OFFLINE_INSTALL_GUIDE.md` added (Viewer + iDempiere/ERP only, Modeller deliberately excluded —
its mesh.db LFS bug is unrelated), linked from `USER_GUIDE.md`, committed+pushed to
`fable/meshdb-livewire` (`3b0afe3d2`). ⛔ **Live docs-site publish still BLOCKED**: `scripts/safe_gh_deploy.sh`
aborted on a 13-file merge conflict vs `origin/master` (`PROGRESS.md`, `extractIFCtoDB.py`,
several `prompts/*.md`, a witness file) — unrelated to this doc, guard correctly refused to auto-pick a
side. Needs a human merge call before the new page (or any docs change) goes live.

## Current State
**Gate:** `./scripts/run_RosettaStones.sh` — S190 fleet: 116/157 PASS, 4 ALL GREEN (BR,MO,RL,WI). 21 buildings. 9-gate system.
| PFX | EL | GATES | Notes |
|-----|----|-------|-------|
| BR·MO·RL·WI | 33·2791·1·1 | 9/9 | ALL GREEN |
| DX | 1169 | 8/9 | MetadataMissing (IfcOpeningElement) |
| SH | 65 | 8/9 | MetadataMissing (generative MEP) |
| TE | 48428 | 8/10 | C8 mesh diversity, GEO no pairs (federated) |

**Pipeline:** 11 stages. 77 verbs. 7403 products (ERP.db). 4-DB architecture.
▶ **PUSH PAUSE LIFTED (2026-07-17)** — push freely; `CLAUDE.md` §⏸ PUSH PAUSE.

⚠ `~/bim-ootb` main checkout is stale + conflicts on `merge origin/main` (tried+aborted 07-26); its local
commits are NOT unique so nothing is at risk — **never measure from it**, use a fresh `origin/main` worktree.

▶ **In-flight work is NOT listed here — read it from git; every hand-written copy has been wrong.**
`gh pr list --state open` · unmerged-no-PR: `for b in $(git for-each-ref --format='%(refname:short)'
refs/heads/); do n=$(git rev-list --count origin/main..$b); [ "$n" -gt 0 ] && echo "$n $b"; done | sort -rn`.
0 commits only-on-this-disk (both repos, re-verified 07-30). Undelivered: `lane/hr-overlay`, `lane/teams-overlay`.

## OPEN — to be assigned to sessions (user dispatches from this list, check before starting cold)
- ▶▶ **4D SUPPORT INVARIANT** — `prompts/GANTT_ACCURACY.md` §ELEMENT_CPM. Ruling 2026-08-02: support
  wins but NOT merged (support 6,778→0, floating 0, but band regresses 29,824→34,595 — band is
  user-confirmed live). Root cause: storey-LABEL ladder wrong in 1,735/81,722 support edges (2.1%), by
  elevation **zero**. 4 engine shapes already measured and rejected — don't retry. NEXT: move BOTH the
  trade gate and the band gate onto the elevation key (band alone isn't enough — trade still keys on
  label, 23,121 elements sit in two groupings, barrier deadlocks). Engine/support-extraction/crew-pool
  built and reusable.
- ▶ **ROOM PATHING** — `prompts/Modeller/DISC_Walker/VIEWER_FIND_PANEL_ROOM_ACCURACY.md`, read `ROOM_PATHING_SUBSTRATE.md` FIRST (new: concept,
  invariants, every failed trial, prior art, §0 index of all 30 lane docs), then resume at §21.44. ⚠ Everything before §21.33 is SUPERSEDED/disproven — don't
  re-derive. Three defects found and fixed 2026-08-02: §PRECARVE (retention 84%/43%→100%/100%), the
  unhosted-void admission (default `W:3.0`), and the door pierce (6*RES→10*RES). **LTU stranded
  107→18/277, unroutable 87.7%→18.4% — beats the room-graph baseline 32.4% AND survives the
  phantom-adjacency cap test (share 104%→20%).** Standing 4-witness gate all green: §T1–T5 (retention
  100%/100%), §O3, §SC3 breaks 11/34→9/14, §CB5 sealed suites 9/23→7/8.
  ⛔ **Clinic still short: 50/186 @ 49.3%.** §21.43/§21.44 (2026-08-02) — resume at **§21.44**, three
  things now SETTLED, do not re-derive: (a) §21.41's doorway-merge **falsified before coding** (it
  separates — 0.0% of >10 m² pockets misclassify — but reaches 1 of 8 far-end groups; §C40c's "41 far
  ends" were 41 records over 8 groups); (b) §21.41's root cause **retracted** — doorway pockets carry
  2–5 door-matched openings each, the graph does not die in them; (c) the `rel_contained_in_space`
  "free win" **retracted, it is circular** — written by our own `compile_rooms.py:1295`, 100% `RM_*`/`≈`
  rows, 1 space per door. This lane still has NO independent oracle. **NEW ROOT CAUSE (§21.43): the
  void carve is TRANSPOSED** — `_rasterizeSpine` max/min-normalises every void long-along-world-x, so
  46% of Clinic's doors and 57% of LTU's are carved 90° wrong (rotation can't correct it: the
  `COALESCE(t.rotation_z,0)` column is selected with no alias, so 0 of 3,167 voids and 0 of 4,979 walls
  ever carry it — harmless only because the fixtures store world AABBs). **The correct fix makes every
  metric worse** (§O3 phantom 20% PASS→94% FAIL, LTU 18.4%→23.0%, Clinic 49.3%→50.4%): the wrong carve
  over-removes wall and that is what was merging pockets, so `W:3.0` and `pierce=10*RES` were both
  swept against a geometric error and 18.4% is not a clean win. NEXT: joint (W, pierce) re-sweep on
  corrected axes — patch kept unapplied at `roompath_diagnostics/patch_21_43_transpose.diff`;
  worker prompt ready at `prompts/Modeller/DISC_Walker/RESUME_ROOMPATH_AXIS_RESWEEP.md` (Fable-class).
  19 witnesses on bim-ootb `review/roompath-redundancy`, worktree `/tmp/wt-roompath` live/clean/pushed
  — REUSE it. Nothing deployed; engine byte-unchanged. Blocks `datacentre_cabling.md` cable-pathing.
- ▶ **MODELLER** — dispatch from `prompts/MODELLER_MASTER.md` (⚠ landmine found compacting this file
  2026-08-02: that file is NOT on this branch, only on unmerged `fix/lod400-envelope-hardfail` — merge or
  recreate it before dispatching from it). ✅ LIVE-DEFECT CLOSED: Modeller drew bounding-box fallbacks on
  the live site for months (Git-LFS `mesh.db` unresolved on GH Pages, failure hidden behind
  DevTools-filtered `console.warn`) — fixed via per-resident geo files + `_assertRealGeoDb()` guard + sw
  v37→v38, bim-ootb #1090/#1091 merged. Curl-the-served-bytes lesson now standing in `feedback_terse`.
- `RESUME_MODELLER_LOD400_REAL_GEOMETRY.md §LOD400-ENVELOPE` (PR #56) — done: `rel_material_layer_set`
  edge + P10 gate, witness 8/8. OPEN: §LOD400-LAYERS-REAL (slice the envelope at authored thickness) —
  needs the one-mesh-per-element vs N-sub-instances call first. Old §LODHELL FINDING 1 verdict is
  SUPERSEDED, don't re-cite.
- `RESUME_HR_BIM_ASSET.md` §07-06c · `RESUME_WORLD_HISTORY_DEDUP_RESTORE.md` §07-06 · `PILL_DRAWER_REORGANIZATION.md` · `OPEN_BUTTON_IFC_BCF_MERGE.md` · `SPARSE_WALL_ROOM_INFERENCE.md` Ph1 · `XRAY_FIXTURE_CLASSIFICATION_FIX.md` · `FUNCTIONAL_SPACE_MGMT_NEXT_SESSION.md`.
- `PHOTOREAL_STILL_RENDER.md` — §MAXQ_OFFLINE_RUNNER 5/5, PR #1015 (viewer untouched; left: agent +
  Shift+Alt+C POST, read its 🧭 PICK-UP BRIEF) · §CINEMA_TURN_SLERP landed #1018 7/7, open: D2 walk-out
  corner whip (19.8°/frame, ungated) · §CINEMA_HALL_CANDIDATE unparked, recheck vs Clinic v3 **207 rooms**
  (not 118) · §MAXQ_SURFACELESS_FRAMEBUFFER **downgraded**. Also `ROOM_LENS_VISUAL_HIGHLIGHT_SPEC.md §25/§14`.
- **Fly-Tour** — `Viewer/FLY_TOUR_CORRIDOR_GRAPH.md`, read its last §-sections, do NOT re-derive (scrubber
  11/11). Next: ⛔ `§SCRUB_PREPARE_STALL` (1.67s, root-caused) · D2/D5/D6/D7 · `§OPENING_BEAT_SEEK_GAP`
  (gate invalid, needs a ratio).
- **`VIEWER_FIND_PANEL_ROOM_ACCURACY.md §14`** log-precision-first MUST land BEFORE live-diagnosing Terminal's "disciplines disappeared".
- ▶ **NEXT: `Viewer/FLY_TOUR_CORRIDOR_GRAPH.md §STAKEHOLDER_STROLL` S4** — glazing metric (windows
  TE 236/HO 131/CL 58/LTU 976; curtain wall HO 178/CL 31) → S5 jerk softener (95th-pct −50%, profile
  FIRST). ⚠ S2 forked `deploy/dev/room_graph.js` from bim-ootb `common/room_graph.js` — needs porting back.
- Small opens: Terminal Aras 03/04 raster refresh (Clinic/Terminal/LTU ship NO raster table — blocks G1) · `docs/userguide-roompath-fixed` no PR · HBA IoT 1/2/0 (CCTV dbl-click, camera-POV fly-to ⛔ needs facing vector, mobile card-stack) `RESUME_HBA_MOBILE_CARD_STACK.md` · Held: `PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md` · Kernel op-log T4+T5 BROWSER-GATED `KERNEL_HARDENING_BATCH1_SPEC.md §STATUS` · Modeller onboarding `ARC_GEO_FETCH_SPEC.md §NEXT` item 2 · ⛔ `DV_*_rules.sql` append-only exempt? `CODEBASE_QUALITY_AUDIT_2026-07-02.md §TRIAGE` · Modeller polish: PBR textures (9), SSAO (needs EffectComposer), ARC occupancy drift 99%→92-95% (`project_arc_meshreadpixels_branch_unmerged.md`).
- ▶ **KUL070 datacentre** — `prompts/datacentre_cabling.md` (cabling) / `prompts/IFC_LARGE_PRIVATE_STRESS_TEST.md`
  (ingestion, §KUL009-13). Ingestion CLOSED: the 62,500 "missing" elements were the wasm32 4GB ceiling
  (not the call stack) — 8-way split → 87,333-element DB, 0 orphans. ⚠ §KUL013: `center_*` is the
  placement ORIGIN not the AABB centre (median 11.31 m off) — `disc_walker.routeChains` misuses it, 0.00%
  vs 90.07% precision, needs its own session. Cabling: engineer confirmed all 3 pain points, wants
  auto-routing first. **⛔ NEXT SESSION PRECONDITION: review ROOMS pathing first** — cable pathing
  inherits the same A*/polyline/highlight engine and its unmeasured redundant-path defects (today 8/119
  runs traversable = 6.7%, not the 41% name-resolution figure). Ship F1/F2/F6 first — none need a
  pathing engine.

## Archive — DONE/shipped (one-line pointers only; detail lives in the named prompts file)
- ✅ §CINEMA_TURN_SLERP LANDED (#1018, 7/7) — look-back 180° snap fixed by rotating gaze direction — `PHOTOREAL_STILL_RENDER.md §CINEMA_TURN_SLERP`.
- ✅ R5-A SETTLED (07-26): the sandbox is LOCAL — OCI `sandbox/` frozen; `deploy/dev` on localhost IS the sandbox, DBs from `~/bim-ootb/buildings`. Don't re-open.
- ✅ §LODHELL + Modeller guide + stranded-branch sweep (07-27/28) #1051/#1062/#1065 — `RESUME_MODELLER_LOD400_REAL_GEOMETRY.md` §START HERE (⛔ 1 user design call left).
- ✅ Alt+C flicker + MaxQ salvage (07-25/26) #1004/#1005/#1011 — `PHOTOREAL_STILL_RENDER.md`.
- ✅ §TOUR_HIGHLIGHT_LANE → ZERO (07-26) #1012-#1014, Terminal 8/92→0/84 — T4/exits is its own track `§G1-EXTERIOR-DOOR-LANE`.
- ✅ §STAKEHOLDER_STROLL S1+S2+S3 (07-26) 28/28, 37/37, 55/55, gate G6 — `FLY_TOUR_CORRIDOR_GRAPH.md` §S1/§S2/§S3 (⚠ landmines there, do not re-derive).
- ✅ Room→Path LIVE (07-25/26) #1006-#1010, Hospital 69.4%→91.2% — `VIEWER_FIND_PANEL_ROOM_ACCURACY.md §17` · ✅ Occupant-pathfinder #997/#998.
- ✅ Blank Viewer landing card + local `.db` Open (07-27/28) #1068/#1070 — `Viewer/BLANK_VIEWER_LANDING_CARD.md`.
- ✅ §HOVER_NAME (12/12, #1085) · §CPE_ROOM_TITLE (#1089, gap closed #1092, user-confirmed on a real bake 07-30).
- ✅ §4D_FACADE_ORDER (07-31) #1098/#1100, sw v885→v887, user-confirmed — `RESUME_4D_TRUTH_AND_BE_HERE_WHEN.md` §TASK 1 CLOSED.
- ✅ §CACHE_KEY re-download (07-30, #1088) Hospital 251MB refetch per click → 0 network on load B, W-DB-CACHE-KEY 16/16 — `HISTORY_PERSIST_RECALL.md` §VERIFY-FIRST ITEM 1.
- ✅ §SEAM_IDENTITY_AUDIT F2 (07-31) #1106/#1109 — IDB version drift; F1/F3–F18 still open, un-triaged — `SEAM_IDENTITY_AUDIT.md`.
- ✅ O13 guide text (07-31) PR #64 — `RESUME_MODELLER_GUIDE_SCREENSHOT_FIX.md` (⚠ do not re-attempt either screenshot without reading it).
- ✅ 2026-08-02 batch LANDED #1129 `fc58210` — §4D_BAND_MONOTONIC (29,824→0 non-structure inversions), §CPE_DAY_COUNTER, §CPE_GHOST_PULL, room-title dwell/lead; sw v913, gantt cache 7, verified served.
- 🟡 P2P Material Receipt unblocked, signed M_MatchPO (07-23) PR #972 open; M_MatchInv NOT closed — `ERP_P2P_INVOICE_MATCH.md`.
- ⛔ Hospital **missing walls on one side** — unproven hypothesis was the re-fetch race; re-verify on a clean `§CACHE_HIT` now the re-fetch is gone.
- Older DONE: `prompts/archive/PROGRESS_DONE_ARCHIVE_pre_2026-07-23.md` / `_pre_2026-07-17.md` / `_pre_2026-07-05.md` / `_pre_2026-06-14.md`.

## OCI Deployment · Reference
Live: `bim-ootb-live` (landing+viewer+single DBs); viewer CODE is served from **GH Pages**, DBs+patches
from OCI `bim-ootb`. `deploy/dev/` canonical. SOP `deploy/OCI_UPLOAD.md` — **§RULES 6: patches go via
`scripts/oci_patch_gate.js`.** Docs: https://red1oon.github.io/BIMCompiler/ · `internal/OCI_SETUP.md`
