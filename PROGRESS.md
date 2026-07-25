# PROGRESS — Current Development State

> **Rule:** PROGRESS.md is a thin status file. No specs here — specs live in `docs/` and `prompts/`. Keep this file under 80 lines.

## Current State
**Gate:** `./scripts/run_RosettaStones.sh` — S190 fleet: 116/157 PASS, 4 ALL GREEN (BR,MO,RL,WI). 21 buildings. 9-gate system.
| PFX | EL | GATES | Notes |
|-----|----|-------|-------|
| BR | 33 | 9/9 | ALL GREEN |
| MO | 2791 | 9/9 | ALL GREEN |
| RL | 1 | 9/9 | ALL GREEN |
| WI | 1 | 9/9 | ALL GREEN |
| DX | 1169 | 8/9 | MetadataMissing (IfcOpeningElement) |
| SH | 65 | 8/9 | MetadataMissing (generative MEP) |
| TE | 48428 | 8/10 | C8 mesh diversity, GEO no pairs (federated) |

**Pipeline:** 11 stages. 77 verbs. 7403 products (ERP.db). 4-DB architecture.

▶ **PUSH PAUSE LIFTED (2026-07-17)** — push freely; `CLAUDE.md` §⏸ PUSH PAUSE.

▶ **In-flight work is NOT listed here — read it from git, it is authoritative and never stale.**
Every hand-written copy has been wrong ("10 commits" when it was 336). In `bim-ootb`:
`gh pr list --state open` · unmerged-with-no-PR: `git for-each-ref --format='%(refname:short)' refs/heads/ | while read b; do n=$(git rev-list --count origin/main..$b); [ "$n" -gt 0 ] && echo "$n $b"; done | sort -rn`
**Verified 2026-07-25: 0 commits exist only on this disk.** Branches carrying unmerged work with no
open PR remain **backed up, but undelivered** (largest: `lane/hr-overlay`, `lane/teams-overlay`).

## OPEN — to be assigned to sessions (user dispatches from this list, check before starting cold)
- `RESUME_HR_BIM_ASSET.md` §07-06c (A/B/C bugs + E) · `RESUME_WORLD_HISTORY_DEDUP_RESTORE.md` §07-06
  (G6/Ph3/Pt1 parked) · `PILL_DRAWER_REORGANIZATION.md` (first-touch flicker) ·
  `OPEN_BUTTON_IFC_BCF_MERGE.md` (not started).
- DiscWalk §STOREY-ZBAND DONE+GREEN but STRANDED on `bim-ootb:fix/xray-fixture-classification`
  (5 ahead, no PR). Land it — steps in `Modeller/DISC_Walker/RESUME_DISC_WALKER_ENVELOPE_BOUND.md`
  §2026-07-22. Do NOT re-attempt `_hostAxis` swap or R-DOOR-SCORE (both disproven).
- `SPARSE_WALL_ROOM_INFERENCE.md` Ph1 — HHS sparse-wall fusion; HHS room compile is ALSO stale.
- `Modeller/DISC_Walker/XRAY_FIXTURE_CLASSIFICATION_FIX.md` (SampleCastle walls as fixtures,
  root-caused) · `FUNCTIONAL_SPACE_MGMT_NEXT_SESSION.md` (HHS's 2 islands, storey='Unknown').
- `prompts/ROOM_LENS_VISUAL_HIGHLIGHT_SPEC.md` §25 filter-cheap opt; §14 Hospital per-tab-switch number.
- `PHOTOREAL_STILL_RENDER.md` — Alt+C §CINEMA_ORBIT_V2, PR #931/#933; self-heal blocker now CLOSED.
- **`§TOUR_TIMELINE_SCRUB` ✅ SHIPPED 2026-07-25** — bim-ootb PR #999 MERGED (verified), `e8689e9`,
  `tour.js` v17 / `sw.js` v847. Deterministic `pose = f(T)` timeline: `_tourPrepare` eager chain,
  `tourSeek(T,soft)`, all four knob groups, linear thumb. 9/9 numeric witnesses on real LTU_AHouse
  (drift 0, overlay state identical). Spec + `§WATCHDOG-TOUR-SCRUB` review in
  `prompts/Viewer/FLY_TOUR_CORRIDOR_GRAPH.md`. **NEXT there = its `▶ NEXT SESSION` block (usage +
  testing review: Duplex legacy tour, touch, `deploy/dev` port) and the scoped
  `§OPENING_BEAT_SEEK_GAP` (39.8m playback-vs-seek gap on §HL-FIRST's opening orbit — pre-existing,
  not a scrubber bug, but it sits on the first beat a presenter scrubs back to).**
- **ALSO resume from `prompts/Modeller/DISC_Walker/VIEWER_FIND_PANEL_ROOM_ACCURACY.md §14`**
  — log-precision-first task (self-diagnosing computed-vs-rendered + black-box coverage logs) MUST
  land BEFORE live-browser-diagnosing Terminal's open "disciplines disappeared" question.
- HBA IoT 1/2/0 (CCTV dbl-click, camera-POV fly-to ⛔ needs facing vector, mobile card-stack) —
  `prompts/RESUME_HBA_MOBILE_CARD_STACK.md` (bim-ootb).
- Held (prove smallest piece first): Modeller prefab dialogue — `prompts/PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md`.
- Kernel op-log T4+T5 — BROWSER-GATED, `KERNEL_HARDENING_BATCH1_SPEC.md §STATUS` · Modeller onboarding,
  `ARC_GEO_FETCH_SPEC.md §NEXT` item 2 · ⛔ `migration/DV_*_rules.sql` append-only exempt or enforce?
  `CODEBASE_QUALITY_AUDIT_2026-07-02.md §TRIAGE`.
- Modeller polish: PBR textures (9); SSAO (needs EffectComposer). ARC occupancy drift 99%→92-95%
  unexplained, low-pri — `project_arc_meshreadpixels_branch_unmerged.md`.

## Archive — DONE/shipped (one-line pointers; detail in cards + memory topic files)
- ✅ Occupant-pathfinder lane CLOSED (2026-07-25), bim-ootb #997 §BRIDGE-ROUTED-LEGAL (A*-gated room→
  spine bridges; Hospital 59.6→61.7%, other 6 buildings unchanged) + #998 §PATCH-PROVENANCE-GATE
  (mechanical pre-upload check, now `deploy/OCI_UPLOAD.md` §RULES 6). Hospital raster patch LIVE on
  OCI under both filenames, fetch-back verified. Option "push room compilation to OCI" DELETED — the
  client self-heal already does 142→214 in 444ms (LTU, the largest at 126k, is 760ms). F1-F4 follow-ups
  logged. `Modeller/DISC_Walker/OCCUPANT_PATHFINDER.md`.
- ✅ Branch hygiene (07-25): bim-ootb 507→347 local / 590→424 remote, 0 merged-PR branches left.
- ✅ Room-injector self-heal Stages 1-4 CLOSED fleet-wide (07-21/22), #947-#967. Log-precision
  follow-up NOT done, see OPEN. `Viewer/ROOM_INJECTOR_NEEDLE.md`.
- 🟡 P2P Material Receipt UNBLOCKED, real signed M_MatchPO (07-23), PR #972 open; M_MatchInv NOT
  closed — `ERP_P2P_INVOICE_MATCH.md §Fix 07-23`.
- 2026-07-17→20 DONE items: `prompts/archive/PROGRESS_DONE_ARCHIVE_pre_2026-07-23.md`.
- ✅ nav-DLOD root-caused + perf fix, LTU_AHouse 122k (07-23), PR #973-#977, ~4.5→~11.5fps measured.
  §17 BVH-occl-query parked. `Viewer/FLY_TOUR_DLOD_SCALE.md §18-§19`.
- ✅ R room-cycle + Home fill-frame (07-22/23), PR #969 merged, 15/15 witnesses.
  `Viewer/ROOM_CYCLE_HOME_SHORTCUTS.md`.
- Older DONE items: `prompts/archive/PROGRESS_DONE_ARCHIVE_pre_2026-07-17.md` / `_pre_2026-07-05.md` /
  `_pre_2026-06-14.md`. Viewer S-series/DAGCompiler: MEMORY.md "Project — Shipped".

## OCI Deployment · Reference
Live: `bim-ootb-live` (SYSNOVA landing+viewer+single DBs). `deploy/dev/` canonical, `deploy/sandbox/`
stale. SOP: `deploy/OCI_UPLOAD.md` — **§RULES 6: patches go through `scripts/oci_patch_gate.js`.**
Docs: https://red1oon.github.io/BIMCompiler/ · `docs/SPATIAL_COMPILATION_PAPER.md` · `internal/OCI_SETUP.md`
