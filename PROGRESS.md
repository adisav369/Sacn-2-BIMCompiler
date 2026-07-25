# PROGRESS — Current Development State

> **Rule:** PROGRESS.md is a thin status file. No specs here — specs live in `docs/` and `prompts/`. Keep this file under 80 lines.

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

⚠ `~/bim-ootb` main checkout = `73d3676`, **38 behind**, `merge origin/main` CONFLICTS (dlod_nav/main/
sw.js, tried+aborted 07-26); its 2 local commits are NOT unique (`86b096b`=#971) so nothing is at risk —
**never measure from it**, use a fresh `origin/main` worktree.

▶ **In-flight work is NOT listed here — read it from git; every hand-written copy has been wrong ("10
commits" when it was 336).** `gh pr list --state open` · unmerged-no-PR: `git for-each-ref
--format='%(refname:short)' refs/heads/ | while read b; do n=$(git rev-list --count origin/main..$b);
[ "$n" -gt 0 ] && echo "$n $b"; done | sort -rn`. **Verified 2026-07-26: 0 commits only on this disk**
(both repos). Unmerged-no-PR branches are backed up but undelivered (`lane/hr-overlay`, `lane/teams-overlay`).

## OPEN — to be assigned to sessions (user dispatches from this list, check before starting cold)
- `RESUME_HR_BIM_ASSET.md` §07-06c (A/B/C+E) · `RESUME_WORLD_HISTORY_DEDUP_RESTORE.md` §07-06 (G6/Ph3/Pt1
  parked) · `PILL_DRAWER_REORGANIZATION.md` (first-touch flicker) · `OPEN_BUTTON_IFC_BCF_MERGE.md`.
- DiscWalk §STOREY-ZBAND DONE+GREEN but STRANDED on `bim-ootb:fix/xray-fixture-classification` (5 ahead,
  no PR) — land it, steps in `RESUME_DISC_WALKER_ENVELOPE_BOUND.md` §2026-07-22. Do NOT re-attempt
  `_hostAxis` swap or R-DOOR-SCORE (disproven).
- `SPARSE_WALL_ROOM_INFERENCE.md` Ph1 (HHS sparse-wall fusion; its room compile is ALSO stale) ·
  `XRAY_FIXTURE_CLASSIFICATION_FIX.md` (SampleCastle walls as fixtures, root-caused) ·
  `FUNCTIONAL_SPACE_MGMT_NEXT_SESSION.md` (HHS's 2 islands, storey='Unknown').
- `ROOM_LENS_VISUAL_HIGHLIGHT_SPEC.md` §25/§14 · `PHOTOREAL_STILL_RENDER.md` §CINEMA_ORBIT_V2 #931/#933,
  §MAXQ_SURFACELESS_FRAMEBUFFER **DOWNGRADED** (unattended LTU Alt+C = 360/360 clean, 0 GL errors).
- **Fly-Tour scrubber lane — 3 PRs SHIPPED 2026-07-25, all MERGED, suite 9/9→11/11.** #999
  `§TOUR_TIMELINE_SCRUB` (`pose = f(T)`, drift 0) · #1000 `§SCRUB_PANEL_DRAG` (draggable panel) ·
  #1002 `§SCRUB_BAR_LIFECYCLE` (bar returns after canvas interrupt; closes watchdog D1+D3).
  **ALL detail + open items are in `Viewer/FLY_TOUR_CORRIDOR_GRAPH.md` — read its last 6
  §-sections, do NOT re-derive; both `§WATCHDOG-TOUR-SCRUB` reviews are there.** Next: ⛔
  `§SCRUB_PREPARE_STALL` (1.67s hitch, ROOT-CAUSED = one `cinemaLookDist` raycast/waypoint made
  eager by `_tourPrepare`) · D2/D5/D6/D7 · `§OPENING_BEAT_SEEK_GAP` (**gate invalid, needs a ratio**).
- **ALSO resume from `prompts/Modeller/DISC_Walker/VIEWER_FIND_PANEL_ROOM_ACCURACY.md §14`**
  — log-precision-first task (self-diagnosing computed-vs-rendered + black-box coverage logs) MUST
  land BEFORE live-browser-diagnosing Terminal's open "disciplines disappeared" question.
- Small opens (ex-§TOUR_HIGHLIGHT_LANE): Terminal Aras 03/04 raster refresh; room injector overwrites named
  IFC spaces with `≈ Level N Rk` on Duplex (names vanish from Find); `docs/userguide-roompath-fixed` = guide pics, no PR.
- HBA IoT 1/2/0 (CCTV dbl-click, camera-POV fly-to ⛔ needs facing vector, mobile card-stack) —
  `prompts/RESUME_HBA_MOBILE_CARD_STACK.md` (bim-ootb).
- Held (prove smallest piece first): Modeller prefab dialogue — `PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md`.
- Kernel op-log T4+T5 BROWSER-GATED `KERNEL_HARDENING_BATCH1_SPEC.md §STATUS` · Modeller onboarding
  `ARC_GEO_FETCH_SPEC.md §NEXT` item 2 · ⛔ `DV_*_rules.sql` append-only exempt? `CODEBASE_QUALITY_AUDIT_2026-07-02.md §TRIAGE`.
- Modeller polish: PBR textures (9); SSAO (needs EffectComposer). ARC occupancy drift 99%→92-95%
  low-pri — `project_arc_meshreadpixels_branch_unmerged.md`.

## Archive — DONE/shipped (one-line pointers; detail in cards + memory topic files)
- ✅ Alt+C flicker + MaxQ salvage (07-25/26), bim-ootb #1004/#1005/#1011, user-CONFIRMED live
  ("no more flicker"). `PHOTOREAL_STILL_RENDER.md` bottom sections.
- ✅ §TOUR_HIGHLIGHT_LANE → ZERO (07-26): #1012 `§TOUR-POLYLINE` (tour flies the A* on-floor polyline,
  Terminal 8/92→2/91) + #1013 T2/T3 closed NO-CHANGE on 7 buildings. T4 ⛔ G1: `exit` node = a LIFT-door name
  filter, Terminal's 5 "exits" are elevators — wiring `escapeRoute()` today routes egress TO A LIFT. Never
  synthesise exits; measured door-side test only — `OCCUPANT_PATHFINDER.md §G1-EXIT-IS-A-LIFT-DOOR`.
- ✅ Room→Path FIXED + LIVE (07-25/26), bim-ootb #1006-#1010, 11/11 witnesses, Hospital
  pathability 69.4%→91.2%. `VIEWER_FIND_PANEL_ROOM_ACCURACY.md §17`.
- ✅ Occupant-pathfinder CLOSED (07-25) #997/#998 (§BRIDGE-ROUTED-LEGAL + OCI patch gate, `OCI_UPLOAD.md`
  §RULES 6); F1-F4 logged — `Modeller/DISC_Walker/OCCUPANT_PATHFINDER.md`.
- ✅ Room-injector self-heal Stages 1-4 fleet-wide (07-21/22) #947-#967 `ROOM_INJECTOR_NEEDLE.md` · ✅
  nav-DLOD perf LTU 122k (07-23) #973-#977 `FLY_TOUR_DLOD_SCALE.md §18-§19` · ✅ R room-cycle + Home
  fill-frame (07-22/23) #969 `ROOM_CYCLE_HOME_SHORTCUTS.md`.
- ✅ Branch hygiene (07-25): bim-ootb 347 local / 424 remote, 0 merged-PR branches left.
- 🟡 P2P Material Receipt UNBLOCKED, signed M_MatchPO (07-23), PR #972 open; M_MatchInv NOT closed —
  `ERP_P2P_INVOICE_MATCH.md §Fix 07-23`.
- Older DONE: `prompts/archive/PROGRESS_DONE_ARCHIVE_pre_2026-07-23.md` / `_pre_2026-07-17.md` /
  `_pre_2026-07-05.md` / `_pre_2026-06-14.md`. Viewer S-series/DAGCompiler: MEMORY.md "Project — Shipped".

## OCI Deployment · Reference
Live: `bim-ootb-live` (landing+viewer+single DBs); viewer CODE is served from **GH Pages**, DBs+patches
from OCI `bim-ootb`. `deploy/dev/` canonical. SOP `deploy/OCI_UPLOAD.md` — **§RULES 6: patches go via
`scripts/oci_patch_gate.js`.** Docs: https://red1oon.github.io/BIMCompiler/ · `internal/OCI_SETUP.md`
