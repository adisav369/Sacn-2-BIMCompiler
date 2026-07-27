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
- `RESUME_HR_BIM_ASSET.md` §07-06c · `RESUME_WORLD_HISTORY_DEDUP_RESTORE.md` §07-06 · `PILL_DRAWER_REORGANIZATION.md` · `OPEN_BUTTON_IFC_BCF_MERGE.md` · `SPARSE_WALL_ROOM_INFERENCE.md` Ph1 · `XRAY_FIXTURE_CLASSIFICATION_FIX.md` · `FUNCTIONAL_SPACE_MGMT_NEXT_SESSION.md`.
- ✅ **Modeller stranded-branch sweep DONE 07-27, all 4 retired** — §STOREY-ZBAND was NOT stranded; 3
  branches were already landed/re-homed (#590/#711, `viewer/lib/room_walker.js`, #642), deleted. ONE real
  leftover shipped: IFC-open rendered ZERO ARC geometry — **#1062 merged**. ⚠ verify by CONTENT not
  `git cherry` (patch-id gave false "undelivered" on all 4). Do NOT re-attempt `_hostAxis`/R-DOOR-SCORE.
- `ROOM_LENS_VISUAL_HIGHLIGHT_SPEC.md` §25/§14 · `PHOTOREAL_STILL_RENDER.md` §CINEMA_ORBIT_V2 #931/#933 · §MAXQ_SURFACELESS_FRAMEBUFFER **DOWNGRADED** · **§MAXQ_OFFLINE_RUNNER 5/5, PR #1015 — viewer UNTOUCHED; left: agent + Shift+Alt+C POST. Read its 🧭 PICK-UP BRIEF.**
  ✅ **§CINEMA_TURN_SLERP LANDED (PR #1018, 7/7)** — look-back was a ONE-FRAME 180° snap (look-at lerped THROUGH
  the camera); fixed by rotating the gaze direction. #1017/`feat/cinema-exit-breathe` CLOSED (stale main, retime
  superseded by §CINEMA_TIMING_672). Left open in `PHOTOREAL_STILL_RENDER.md §CINEMA_TURN_SLERP`: **D2** walk-out
  corner whip (19.8°/frame, printed every run, not gated) · §CINEMA_HALL_CANDIDATE — now UNPARKED (S1 done), and
  ⚠ re-read it against Clinic's v3 **207-room** set, not the pre-S1 118-room 323m² figure.
- **Fly-Tour lane — ALL detail in `Viewer/FLY_TOUR_CORRIDOR_GRAPH.md`, read its last §-sections, do NOT
  re-derive** (scrubber #999/#1000/#1002 07-25, 11/11). Next: ⛔ `§SCRUB_PREPARE_STALL` (1.67s,
  ROOT-CAUSED = eager `cinemaLookDist` raycast in `_tourPrepare`) · D2/D5/D6/D7 ·
  `§OPENING_BEAT_SEEK_GAP` (**gate invalid, needs a ratio**).
- **`VIEWER_FIND_PANEL_ROOM_ACCURACY.md §14`** log-precision-first (self-diagnosing computed-vs-rendered +
  coverage logs) MUST land BEFORE live-diagnosing Terminal's "disciplines disappeared".
- ▶ **NEXT: `Viewer/FLY_TOUR_CORRIDOR_GRAPH.md §STAKEHOLDER_STROLL` S4** (S1+S2+S3 DONE 07-26,
  Archive) — S4 glazing metric (windows TE 236/HO 131/CL 58/LTU 976; curtain wall HO 178/CL 31)
  → S5 jerk softener (95th-pct jerk −50%, measure profile FIRST).
  ⚠ **S2 FORKED `deploy/dev/room_graph.js`** from bim-ootb `common/room_graph.js` (was byte-identical)
  — shared engine code, needs porting back; unlike S3's tour-local change.
- ✅ **R5-A deploy leg SETTLED (user 07-26): the sandbox is LOCAL** — OCI `sandbox/` stays frozen (68 days
  stale, unmaintained), `deploy/dev` on localhost IS the sandbox, DBs from `~/bim-ootb/buildings`. Don't re-open.
- Small opens: Terminal Aras 03/04 raster refresh (Clinic/Terminal/LTU ship NO raster table — blocks G1); `docs/userguide-roompath-fixed` no PR · HBA IoT 1/2/0 (CCTV dbl-click, camera-POV fly-to ⛔ needs facing vector, mobile card-stack) `RESUME_HBA_MOBILE_CARD_STACK.md` · Held: Modeller prefab dialogue `PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md`.
- Kernel op-log T4+T5 BROWSER-GATED `KERNEL_HARDENING_BATCH1_SPEC.md §STATUS` · Modeller onboarding `ARC_GEO_FETCH_SPEC.md §NEXT` item 2 · ⛔ `DV_*_rules.sql` append-only exempt? `CODEBASE_QUALITY_AUDIT_2026-07-02.md §TRIAGE` · Modeller polish: PBR textures (9), SSAO (needs EffectComposer), ARC occupancy drift 99%→92-95% (`project_arc_meshreadpixels_branch_unmerged.md`).

## Archive — DONE/shipped (one-line pointers; detail in cards + memory topic files)
- ✅ §LODHELL 1-2-3 CLOSED (07-27) — **all detail `RESUME_MODELLER_LOD400_REAL_GEOMETRY.md`
  §LODHELL-ROOTCAUSE/-FIX**; W-LODHELL-CLASSIFY 5/5, `f7d00240b` + bim-ootb **#1051 merged**.
  SampleCastle's boxiness is its OWN source (46.4% literal 12-tri), renderer clean (3225/3225). The 65
  "missing" walls are **VOID-CONSUMED** (correct, 74/74 fillings present) — now classified, all fails
  printed, P5 honest + new P9, red §PROOF exits ≠0; dead no-boolean tier DELETED not wired.
  ⛔ OPEN design call: only 9/74 fills can ride (65 hosts aren't scene features) — non-rendered
  logical anchor for a void-consumed host? Not scoped.
- ✅ Alt+C flicker + MaxQ salvage (07-25/26) #1004/#1005/#1011, user-CONFIRMED live — `PHOTOREAL_STILL_RENDER.md`.
- ✅ §TOUR_HIGHLIGHT_LANE → ZERO (07-26): #1012 §TOUR-POLYLINE (Terminal 8/92→2/91) + #1013 T2/T3
  NO-CHANGE ×7 + #1014 `exit` was a LIFT-door filter → removed, 2/91→**0/84**; T4/exits = its OWN
  track (`§G1-EXTERIOR-DOOR-LANE`), NOT a blocker.
- ✅ §STAKEHOLDER_STROLL S1+S2+S3 SHIPPED (07-26) — **all detail in `FLY_TOUR_CORRIDOR_GRAPH.md`
  §S1/§S2/§S3, read it there.** S1 injector 28/28 (+W-R5A 10/10); S3 scene budget 55/55 (5 bldgs);
  S2 lift shafts 37/37. New gate G6 = a route that existed pre-change must still exist.
  ⚠ spec's "Hospital 22" is PRE-S1, use 18 · ⚠ JKR/LTU coverage gap closed 07-26 (JKR was in NO
  witness, its 75-component graph islanded the 2 largest rooms → route LOST to legacy; fixed by
  **§SCENE-COMPONENT**) · JKR strolls 1 storey and LTU has no ascent — data, not bugs.
- ✅ Room→Path FIXED + LIVE (07-25/26) #1006-#1010, 11/11, Hospital pathability 69.4%→91.2%
  (`VIEWER_FIND_PANEL_ROOM_ACCURACY.md §17`) · ✅ Occupant-pathfinder CLOSED (07-25) #997/#998
  `Modeller/DISC_Walker/OCCUPANT_PATHFINDER.md`.
- ✅ Room-injector self-heal Stages 1-4 (07-21/22) #947-#967 · ✅ nav-DLOD perf LTU 122k (07-23)
  #973-#977 `FLY_TOUR_DLOD_SCALE.md §18-§19` · ✅ R room-cycle + Home fill-frame #969 · ✅ Branch
  hygiene (07-25).
- 🟡 P2P Material Receipt UNBLOCKED, signed M_MatchPO (07-23), PR #972 open; M_MatchInv NOT closed — `ERP_P2P_INVOICE_MATCH.md §Fix 07-23`.
- ✅ Blank Viewer landing card + local `.db` Open (07-27/28) — bim-compiler `40e333efd` pushed +
  **bim-ootb PORT PR #1068 open** (`fix/blank-viewer-landing-card`, `c7a6ce0`, two separate codebases —
  see `Viewer/BLANK_VIEWER_LANDING_CARD.md` §bim-ootb PORT). ⛔ open thread: user saw an idempiere-seed-db
  status message while at the Viewer; grep of `deploy/dev` + `~/bim-ootb/viewer` found only lazy
  (`only-when-opened`) triggers, real source not located — need the exact page/text from the user.
- Older DONE: `prompts/archive/PROGRESS_DONE_ARCHIVE_pre_2026-07-23.md` / `_pre_2026-07-17.md` / `_pre_2026-07-05.md` / `_pre_2026-06-14.md`.

## OCI Deployment · Reference
Live: `bim-ootb-live` (landing+viewer+single DBs); viewer CODE is served from **GH Pages**, DBs+patches
from OCI `bim-ootb`. `deploy/dev/` canonical. SOP `deploy/OCI_UPLOAD.md` — **§RULES 6: patches go via
`scripts/oci_patch_gate.js`.** Docs: https://red1oon.github.io/BIMCompiler/ · `internal/OCI_SETUP.md`
