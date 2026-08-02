# PROGRESS — Current Development State

> **Rule:** PROGRESS.md is a thin status file. No specs here — specs live in `docs/` and `prompts/`. Keep this file under 80 lines.

## Session 2026-08-02 — 4D ordering CONFIRMED live, movie-maker batch shipped
**User confirmed on a real Hospital bake:** no roof before walls, no upper deck before lower, to Day 282.
Ordering half of the 4D lane is CLOSED (§4D_BAND_MONOTONIC #1129 + §4D_ROOF_LOAD_PATH #1120 + cache bump #1123).

**Shipped to `origin/main`:** §CPE_DAY_COUNTER_POS (#1130, sw v914) — Day # counter corner is a panel
option, top right default, now visible in Preview (its live hooks had never been wired). §CPE_GAZE_ACQUIRE
(#1131, sw v915) — gaze acquires fast and settles: 90° in 0.90s vs the flat cap's 2.00s, 135→41 dps,
settled gaze bit-identical. G-SH-4 witness bound now read from the shipped curve, not a literal 45 (#1132).

**NOT merged, deliberately:** `feat/element-cpm` — support invariant 6,778→0 and floating 0, but band
inversions regress 29,824→34,595, and the band ordering was just user-confirmed. Root cause MEASURED and
new (`audit_support_roleblind.js` + `audit_rank_vs_support.js`): the storey-LABEL ladder contradicts
gravity in 1,735 of 81,722 support edges (2.1%); by element ELEVATION, **zero**. Band-monotonic-by-label
and nothing-before-its-carrier cannot both be zero for any engine. Next: move BOTH trade and band gates
onto the elevation key. Full write-up `prompts/GANTT_ACCURACY.md` §ELEMENT_CPM.

**Open, specced, not built:** §CPE_ROOM_TITLE_MULTI (`prompts/CINEMA_PATH_EDITOR.md`) — caption several
rooms in view with a level prefix instead of one ray-picked room. User's own framing: less room for error.

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

▶ **In-flight work is NOT listed here — read it from git; every hand-written copy has been wrong ("10
commits" when it was 336).** `gh pr list --state open` · unmerged-no-PR: `for b in $(git for-each-ref
--format='%(refname:short)' refs/heads/); do n=$(git rev-list --count origin/main..$b); [ "$n" -gt 0 ] &&
echo "$n $b"; done | sort -rn`. 0 commits only-on-this-disk (both repos, re-verified 07-30). Undelivered:
`lane/hr-overlay`, `lane/teams-overlay`.

## OPEN — to be assigned to sessions (user dispatches from this list, check before starting cold)
- ▶▶ **4D SUPPORT INVARIANT — `prompts/GANTT_ACCURACY.md` §ELEMENT_CPM (last section).**
  ⚖ **RULING GIVEN 2026-08-02: support wins.** Built on `feat/element-cpm`: support **6,778 → 0**,
  floating 0 — but band inversions regress 29,824 → 34,595, so it is **NOT merged** (the band ordering
  is user-confirmed live). **4 engine shapes measured and rejected — do not retry; tables in §ROOT
  CAUSE + §ELEMENT_CPM.** ⭐ Root cause is NEW and decisive (`audit_rank_vs_support.js`): the storey-
  LABEL ladder contradicts gravity in **1,735 of 81,722** support edges (2.1%); by element ELEVATION,
  **ZERO**. Band-by-label and nothing-before-its-carrier cannot both be zero for ANY engine.
  **NEXT = move BOTH the trade gate and the band gate onto the elevation key** (band alone is not
  enough: the trade gate still keys on the label, so 23,121 elements sit in two groupings and the
  barrier deadlocks). Engine, support extraction and crew pool are built and reusable.
- ▶ **ROOM PATHING — `prompts/Modeller/DISC_Walker/VIEWER_FIND_PANEL_ROOM_ACCURACY.md`; start at
  §21.33** (then §21.14 for setup/fixtures only).
  **ROOT CAUSE FOUND AND REMOVED (§21.26/§21.27): the raster had NO DOOR VOIDS** — `_rasterizeWalls`
  stamps wall bounding boxes and never subtracts the opening, so 99% (Clinic) / 100% (LTU) of door
  centres sat on solid masonry. Doorways did not exist in the geometry. §SPINE-RASTER carves them;
  gate passes 99%/100% → 0%/0%, no leak signature. One fact that retro-explains §21.21–§21.25.
  ✅ §DOOR-APERTURE replaces the proximity door↔pocket guess: over-claims 36%/12% → **0**, interior
  doors 2/254 → 192/252 and 11/606 → 404/606. §21.21's *prescribed* fix did not exist (verified).
  ❌ DISPROVEN: §21.23's "one defect, three symptoms" — fixing adjacency did NOT connect the spine.
  ❌ RETRACTED: §21.26's `rotation_z` claim (it is RADIANS; non-issue). Do not re-open either.
  Corridor-by-shape, by `hallway_backbone.js`, and by betweenness all tried — betweenness is best
  (1 component, 92%/100% leaf attachment) but all three were measured on the UNCARVED substrate.
  ⚠ **§21.33 SUPERSEDES everything above — resume from `§21.33 NEXT`, and SKIP §21.24–§21.30's
  numbers, they were taken on a broken raster.** Two defects found and FIXED 2026-08-02: (a) the carve
  admitted 1,595 non-door-hosted voids on LTU, and (b) `§SEAL-DOORS-FIRST` re-closed every door as a
  +1.2 m plug across a ~0.2 m wall. **§PRECARVE** derives enclosure from the pre-carve mask, exactly:
  retention 84%/43% → **100%/100%**. Aperture provenance is REFUTED as the cause (tier B vs C =
  295→295); §21.28's cause (2) is struck for good.
  ❌ **§21.32's `cur` 26.1% was PHANTOM** — the over-linking sweep (§21.33 §O2) shows the 20.1-pt gain
  appears only when the width cap is lifted entirely, i.e. it rests on 25 m/55 m atrium+facade voids.
  Third phantom-adjacency result in this lane. Do not resurrect `cur`.
  ✅ **Default is now `W:3.0`** (admit unhosted voids ≤3 m; p90 of attributed widths is 3.60 m, cliff
  is >6 m): **Clinic 55/208 @ 49.5%, LTU 60/308 @ 45.3%** — 235 LTU stranded rooms recovered
  legitimately. `W:6.0` (53/297 @ 42.7%) is the aggressive alternative, a ⛔ USER RULING not a
  measurement. Standing gates all green: §T1 fidelity · §T2 tier-C · §T4 no-leak · §T5 retention 100%.
  NEXT = (1) re-classify stranded on the W:3.0 substrate (Clinic 55/208, LTU 60/308) on causes (1)/(3),
  (2) the 101/30 `noVoid` fusions from WALL-EXTRACTION gaps, never examined. Funnel residuals (§21.18) LAST.
  15 witnesses + `roompath_diagnostics/` on bim-ootb `review/roompath-redundancy` @ `b669a59`
  (worktree `/tmp/wt-roompath` is live, clean and pushed — REUSE it, don't add a second).
  **Nothing deployed; engine byte-unchanged; room compile byte-identical (every addition opt-in).**
  ⛔ Still keeps `prompts/datacentre_cabling.md` §NEXT_SESSION's cable-pathing precondition UP.
- ▶ **MODELLER — dispatch from `prompts/MODELLER_MASTER.md` (new 2026-07-30), NOT from the 15 scattered
  files.** It triages all of them (3,742 lines), maps 14 objectives (O1–O14) to their owning file, and
  carries an empty §OPEN LIST for a Fable5 harvest pass to fill; the 3 architecture calls it names need
  Sonnet. ✅ **LIVE-DEFECT CLOSED, deployed and verified:** the Modeller drew bounding boxes on the live
  site for months — `modeller/mesh.db` is Git-LFS-tracked and GitHub Pages doesn't resolve LFS, so the
  browser got HTTP 200 + a 134-byte stub; with no mesh store the hard-fail guard was skipped and every
  element fell back to `boxArrays(rawBox)`, logged only via the DevTools-hidden `console.warn`. Fixed
  by per-resident geo files on object storage (Duplex 1.3MB vs a shared 120MB; all 8 residents resolve
  100% of their hashes) + `_assertRealGeoDb()` refusing non-SQLite bytes and naming an LFS stub
  (guard witnessed 4/4 on real live bytes) + service-worker cache v37→v38, confirmed `v38` serving live.
  bim-ootb #1090 + #1091 both merged. ⚠ `modeller/mesh.db` is now dead weight in git — nothing fetches it.
  **Standing lesson (now in `feedback_terse`): for any "the live page looks wrong" report, curl the
  served bytes FIRST — a 200 is not evidence, and a silent substitution is its own bug.**
- **`prompts/RESUME_MODELLER_LOD400_REAL_GEOMETRY.md §LOD400-ENVELOPE`** (BIMCompiler PR #56) — LOD400
  means fabrication level; an authored 7-layer wall shipping as one 12-triangle box is a fallback.
  Duplex source carries 91 `IfcMaterialLayerSetUsage`, SampleCastle 412. DONE: `rel_material_layer_set`
  (the element→layer-set edge that never existed) + P10 `LOD400_ENVELOPE` gate, red §PROOF ⇒ exit 1,
  witness 8/8. OPEN: §LOD400-LAYERS-REAL (slice the envelope at the authored thicknesses) — needs the
  one-mesh-per-element vs N-sub-instances call first. ⚠ The old "GIGO / source is plain" verdict in
  §LODHELL FINDING 1 is SUPERSEDED — do not re-cite it.
- `RESUME_HR_BIM_ASSET.md` §07-06c · `RESUME_WORLD_HISTORY_DEDUP_RESTORE.md` §07-06 · `PILL_DRAWER_REORGANIZATION.md` · `OPEN_BUTTON_IFC_BCF_MERGE.md` · `SPARSE_WALL_ROOM_INFERENCE.md` Ph1 · `XRAY_FIXTURE_CLASSIFICATION_FIX.md` · `FUNCTIONAL_SPACE_MGMT_NEXT_SESSION.md`.
- `ROOM_LENS_VISUAL_HIGHLIGHT_SPEC.md` §25/§14 · `PHOTOREAL_STILL_RENDER.md` §CINEMA_ORBIT_V2 #931/#933 · §MAXQ_SURFACELESS_FRAMEBUFFER **DOWNGRADED** · **§MAXQ_OFFLINE_RUNNER 5/5, PR #1015 — viewer UNTOUCHED; left: agent + Shift+Alt+C POST. Read its 🧭 PICK-UP BRIEF.**
  ✅ **§CINEMA_TURN_SLERP LANDED (#1018, 7/7)** — look-back was a one-frame 180° snap; fixed by rotating the
  gaze direction. Open in `PHOTOREAL_STILL_RENDER.md §CINEMA_TURN_SLERP`: **D2** walk-out corner whip
  (19.8°/frame, ungated) · §CINEMA_HALL_CANDIDATE UNPARKED — ⚠ re-read vs Clinic v3 **207 rooms**, not 118.
- **Fly-Tour — ALL detail in `Viewer/FLY_TOUR_CORRIDOR_GRAPH.md`, read its last §-sections, do NOT re-derive**
  (scrubber 11/11). Next: ⛔ `§SCRUB_PREPARE_STALL` (1.67s, ROOT-CAUSED) · D2/D5/D6/D7 · `§OPENING_BEAT_SEEK_GAP`
  (**gate invalid, needs a ratio**).
- **`VIEWER_FIND_PANEL_ROOM_ACCURACY.md §14`** log-precision-first MUST land BEFORE live-diagnosing Terminal's "disciplines disappeared".
- ▶ **NEXT: `Viewer/FLY_TOUR_CORRIDOR_GRAPH.md §STAKEHOLDER_STROLL` S4** — glazing metric (windows
  TE 236/HO 131/CL 58/LTU 976; curtain wall HO 178/CL 31) → S5 jerk softener (95th-pct −50%, profile FIRST).
  ⚠ **S2 FORKED `deploy/dev/room_graph.js`** from bim-ootb `common/room_graph.js` — shared engine, needs
  porting back (unlike S3's tour-local change).
- ✅ **R5-A SETTLED (user 07-26): the sandbox is LOCAL** — OCI `sandbox/` frozen; `deploy/dev` on localhost IS the sandbox, DBs from `~/bim-ootb/buildings`. Don't re-open.
- Small opens: Terminal Aras 03/04 raster refresh (Clinic/Terminal/LTU ship NO raster table — blocks G1) · `docs/userguide-roompath-fixed` no PR · HBA IoT 1/2/0 (CCTV dbl-click, camera-POV fly-to ⛔ needs facing vector, mobile card-stack) `RESUME_HBA_MOBILE_CARD_STACK.md` · Held: `PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md` · Kernel op-log T4+T5 BROWSER-GATED `KERNEL_HARDENING_BATCH1_SPEC.md §STATUS` · Modeller onboarding `ARC_GEO_FETCH_SPEC.md §NEXT` item 2 · ⛔ `DV_*_rules.sql` append-only exempt? `CODEBASE_QUALITY_AUDIT_2026-07-02.md §TRIAGE` · Modeller polish: PBR textures (9), SSAO (needs EffectComposer), ARC occupancy drift 99%→92-95% (`project_arc_meshreadpixels_branch_unmerged.md`).

- ▶ **KUL070 datacentre — 2GB IFC lane CLOSED, cabling lane OPEN. `prompts/datacentre_cabling.md` owns
  cabling; `prompts/IFC_LARGE_PRIVATE_STRESS_TEST.md` owns ingestion (§KUL009-§KUL013).** Ingestion done:
  the 62,500 "missing" elements were the **wasm32 4GB ceiling** (not the call stack) — 8-way split →
  **87,333-element DB, 0 orphans**, and `extractIFCtoDB.py` now writes `elements_meta.building` +
  `project_metadata` at source (§KUL001 retired). ⚠ §KUL013: `center_*` is the placement ORIGIN, median
  11.31 m off the AABB centre — **`disc_walker.routeChains` misuses it, 0.00% vs 90.07% precision; needs
  its own session.** Cabling: engineer confirmed all 3 pain points, wants **auto-routing first**.
  **⛔ NEXT SESSION PRECONDITION (user): review ROOMS pathing first** — cable pathing inherits the same
  A*/polyline/highlight engine and its unmeasured redundant-path defects. Then the gate: rebuild as
  authored-ports ∪ corrected-geometric and re-run §RUN_READY (**today only 8 of 119 runs traversable =
  6.7%**, NOT the 41% name-resolution figure). Ship F1/F2/F6 first — none need a pathing engine.
  (⚠ this file is 100+ lines, over its 80 budget — compaction left to the owners of the 07-2x entries
  below rather than risk clobbering concurrent edits.)

## Archive — DONE/shipped (one-line pointers only; detail lives in the named prompts file)
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
