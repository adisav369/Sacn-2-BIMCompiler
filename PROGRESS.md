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

▶ **PUSH PAUSE LIFTED (2026-07-17, user: "push permission is ON")** — push freely (normal
fast-forward/PRs, verification habits unchanged) until the user pauses again. `CLAUDE.md` §⏸ PUSH PAUSE.

▶ **In-flight work is NOT listed here — read it from git, it is authoritative and never stale.**
Branch state is deliberately not hand-copied into this file (every hand-written copy has been wrong:
"10 commits" when it was 336, "55 .txt" when it was 6). To see what is in flight, in `bim-ootb`:
`gh pr list --state open` · unmerged-with-no-PR: `git for-each-ref --format='%(refname:short)' refs/heads/ | while read b; do n=$(git rev-list --count origin/main..$b); [ "$n" -gt 0 ] && echo "$n $b"; done | sort -rn`
**Verified 2026-07-20: 0 commits exist only on this disk** (48 were single-copy, now pushed; 1 —
`feat/component-catalog-dedup` — awaits an LFS-quota window). ~40 branches carry unmerged work with
no open PR (largest: `lane/hr-overlay` 68, `lane/teams-overlay` 56) — **backed up, but undelivered**.

## OPEN — to be assigned to sessions (user dispatches from this list, check before starting cold)
- `prompts/RESUME_HR_BIM_ASSET.md` §2026-07-06c — A/B/C bugs + E decision.
- `prompts/RESUME_WORLD_HISTORY_DEDUP_RESTORE.md` §2026-07-06 — G6, Ph3, Pt1 parked.
- `prompts/PILL_DRAWER_REORGANIZATION.md` — first-touch flicker.
- `prompts/OPEN_BUTTON_IFC_BCF_MERGE.md` — not started.
- DiscWalk: §STOREY-ZBAND fix is DONE+GREEN (07-13, `witness_dw_storey_band.js`, fleet-clean) but
  STRANDED — pushed `bim-ootb:fix/xray-fixture-classification`, 5 commits ahead of main, no PR ever
  opened, 9 days idle. Land it (open PR, re-verify fresh, merge) — see
  `prompts/Modeller/DISC_Walker/RESUME_DISC_WALKER_ENVELOPE_BOUND.md`'s 2026-07-22 entry point (bottom
  of file) for the exact steps. Do NOT re-attempt `_hostAxis` swap or R-DOOR-SCORE (both disproven).
- `prompts/SPARSE_WALL_ROOM_INFERENCE.md` Phase 1 — sparse-wall fusion (HHS), 4-step follow-up.
  Related, not duplicate: HHS's room compile is ALSO just stale (pre-dates a shipped fix) —
  `prompts/Viewer/ROOM_INJECTOR_NEEDLE.md` §ROOM_WALKER_VERSION_STAMP, next line below.
- `prompts/Modeller/DISC_Walker/XRAY_FIXTURE_CLASSIFICATION_FIX.md` — SampleCastle walls
  misclassified as glowing fixtures, root-caused, POC-gated.
- `prompts/FUNCTIONAL_SPACE_MGMT_NEXT_SESSION.md` — HHS's 2 remaining islands (storey='Unknown').
- `prompts/ROOM_LENS_VISUAL_HIGHLIGHT_SPEC.md` §25 — large-group Find-panel filter-cheap opt;
  §14 — Hospital's real per-tab-switch number never captured (263MB DB wouldn't stream in sandbox).
- `prompts/PHOTOREAL_STILL_RENDER.md` — Alt+C cinema-orbit: §CINEMA_ORBIT_V2 (#907/921/923/925)
  live-tested 2026-07-21, found real (not the already-witnessed) bugs — bim-ootb PR #931 (ghost/
  x-ray stuck through orbit) + PR #933 (candidate-skip, fixes Duplex-class only) shipped. Room-compile
  self-heal blocker (below) is now CLOSED — this should render correctly on Hospital/Terminal/HHS now.
- **NEXT session, resume from `prompts/Modeller/DISC_Walker/VIEWER_FIND_PANEL_ROOM_ACCURACY.md §14`**
  — log-precision-first task (self-diagnosing computed-vs-rendered + black-box coverage logs) MUST
  land BEFORE live-browser-diagnosing Terminal's open "disciplines disappeared" question.
- HBA IoT items 1/2/0 (CCTV double-click, camera-POV fly-to ⛔ needs facing vector, mobile
  card-stack) — `prompts/RESUME_HBA_MOBILE_CARD_STACK.md` (bim-ootb).
- Held (prove smallest piece first): Modeller prefab dialogue — `prompts/PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md`.
- Kernel op-log T4+T5 (unify 3 kernel copies) — BROWSER-GATED. `prompts/KERNEL_HARDENING_BATCH1_SPEC.md §STATUS`.
- Modeller onboarding — Hospital/Clinic/LTU/HHS_Office + SH/DX/SC into `IFC/`. `prompts/ARC_GEO_FETCH_SPEC.md §NEXT` item 2.
- ⛔ BLOCKED: `migration/DV_*_rules.sql` EXEMPT from append-only, or enforce? `prompts/CODEBASE_QUALITY_AUDIT_2026-07-02.md §TRIAGE`.
- Modeller polish: PBR textures (item 9); SSAO (needs EffectComposer). ARC occupancy drift (99%→92-95%,
  `W-DW-DENSITY-TE` D3) unexplained, low-priority — `project_arc_meshreadpixels_branch_unmerged.md`.

## Archive — DONE/shipped (one-line pointers; detail in cards + memory topic files)
- ✅ Room-injector self-heal Stages 1-4 CLOSED fleet-wide (07-21/22), #947/#954/#959/#961/#964/#967.
  Log-precision follow-up NOT done, see OPEN. `Viewer/ROOM_INJECTOR_NEEDLE.md`.
- 🟡 P2P Material Receipt UNBLOCKED, real signed M_MatchPO proven (2026-07-23), bim-ootb PR #972 (open) —
  extracted from real iDempiere Java first; InOutGenerate/InvoiceGenerate's SO-only gates confirmed
  faithful, not bugs. M_MatchInv (Vendor Invoice side) NOT closed — `ERP_P2P_INVOICE_MATCH.md §Fix 07-23`.
- 2026-07-17 through 2026-07-20 DONE items (GEOMETRY_TRUTH_CHAIN, SampleCastle blocky, Posting-tail,
  ERPUserGuide S&D, ERP multi-user concurrency, ERP O2C Sales cycle, TM DLOD Phase 3, Fly Tour route
  cache): `prompts/archive/PROGRESS_DONE_ARCHIVE_pre_2026-07-23.md`.
- ✅ nav-DLOD root-caused + real perf fix, LTU_AHouse 122k (2026-07-23), bim-ootb PR #973-#977 — bbox
  mode's empty-Set filter had silently blocked nav-DLOD; ~4.5→~11.5fps mean during flight, measured not
  felt. §17 (BVH-occl-query) separate, parked. `Viewer/FLY_TOUR_DLOD_SCALE.md §18-§19`.
- ✅ R room-cycle + Home fill-frame shortcuts (2026-07-22/23), bim-ootb PR #969 MERGED — `R` cycles to
  Nth-largest room (no schema change), `Home` resets cycle + tight exterior fill-frame, same keypress,
  verified not to regress existing `Home` claims. 15/15 witnesses, real HHS fixture.
  `Viewer/ROOM_CYCLE_HOME_SHORTCUTS.md`.
- 2026-07-05 through 2026-07-16 DONE items: `prompts/archive/PROGRESS_DONE_ARCHIVE_pre_2026-07-17.md`.
- Pre-2026-07-05: `prompts/archive/PROGRESS_DONE_ARCHIVE_pre_2026-07-05.md` / `_pre_2026-06-14.md`.
  Viewer S-series/DAGCompiler: MEMORY.md "Project — Shipped".

## OCI Deployment
Live: `bim-ootb-live` (SYSNOVA landing+viewer+single DBs, `buildings/{Name}_extracted.db`). Always
upload here — `deploy/dev/` is canonical, `deploy/sandbox/` stale. SOP: `deploy/OCI_UPLOAD.md`.

## Reference
Docs: https://red1oon.github.io/BIMCompiler/ · `docs/SPATIAL_COMPILATION_PAPER.md` · `internal/OCI_SETUP.md`
