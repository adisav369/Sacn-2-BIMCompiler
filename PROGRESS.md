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
- DiscWalk: `prompts/Modeller/DISC_Walker/RESUME_DISC_WALKER_ENVELOPE_BOUND.md` §STOREY-UNKNOWN
  (source of truth) + `DISC_WALKER_BRANCH_CLOSEOUT.md` (3 stale PRs #722/#724/#725 + guide-
  screenshot camera bug). Do NOT re-attempt `_hostAxis` swap or R-DOOR-SCORE (both disproven).
- `prompts/SPARSE_WALL_ROOM_INFERENCE.md` Phase 1 — sparse-wall fusion (HHS), 4-step follow-up.
- `prompts/Modeller/DISC_Walker/XRAY_FIXTURE_CLASSIFICATION_FIX.md` — SampleCastle walls
  misclassified as glowing fixtures, root-caused, POC-gated.
- `prompts/FUNCTIONAL_SPACE_MGMT_NEXT_SESSION.md` — HHS's 2 remaining islands (storey='Unknown').
- `prompts/ROOM_LENS_VISUAL_HIGHLIGHT_SPEC.md` §25 — large-group Find-panel filter-cheap opt;
  §14 — Hospital's real per-tab-switch number never captured (263MB DB wouldn't stream in sandbox).
- `prompts/PHOTOREAL_STILL_RENDER.md` — Time Machine high-quality movie export, explicit
  next-session ask, not started.
- HBA IoT items 1/2/0 (CCTV double-click, camera-POV fly-to ⛔ needs facing vector, mobile
  card-stack) — `prompts/RESUME_HBA_MOBILE_CARD_STACK.md` (bim-ootb).
- Held (prove smallest piece first): Modeller prefab dialogue — `prompts/PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md`.
- Kernel op-log T4+T5 (unify 3 kernel copies) — BROWSER-GATED. `prompts/KERNEL_HARDENING_BATCH1_SPEC.md §STATUS`.
- Modeller onboarding — Hospital/Clinic/LTU/HHS_Office + SH/DX/SC into `IFC/`. `prompts/ARC_GEO_FETCH_SPEC.md §NEXT` item 2.
- ⛔ BLOCKED: `migration/DV_*_rules.sql` EXEMPT from append-only, or enforce? `prompts/CODEBASE_QUALITY_AUDIT_2026-07-02.md §TRIAGE`.
- Modeller polish: PBR textures (item 9); SSAO (needs EffectComposer). ARC occupancy drift (99%→92-95%,
  `W-DW-DENSITY-TE` D3) unexplained, low-priority — `project_arc_meshreadpixels_branch_unmerged.md`.

## Archive — DONE/shipped (one-line pointers; detail in cards + memory topic files)
- ✅ GEOMETRY_TRUTH_CHAIN S0–S3 (2026-07-20) — §DB_IDENTITY+§RENDER_FIDELITY, bim-ootb PR #908
  UNMERGED, all 8 residents REAL/100% geo coverage. `prompts/GEOMETRY_TRUTH_CHAIN.md`.
- ✅ SampleCastle blocky — CLOSED 2026-07-20 (RESOLVED→RETRACTED→re-closed same day, chain kept for
  the lesson): root cause = stale client IndexedDB cache, not a code/data regression.
  `prompts/Modeller/DISC_Walker/EMBED_8_ARC_BUILDINGS_MESH_DB.md` §CLOSED.
- ✅ Posting-tail CLOSED, 20/20 factory posters resolved (2026-07-17/18), ledger stays 52.
  `prompts/FABLE5_B3_POSTING_ORACLE.md` + `prompts/HARDEN_MATRIX.md §W-POST-TAIL`.
- ✅ ERPUserGuide core S&D chapter (2026-07-18). `docs/ERPUserGuide.md`.
- ✅ ERP multi-user concurrency PoC (2026-07-19/20) — real two/ten-tab witnesses, N-User spec.
  `prompts/ERP_MULTIUSER_CONCURRENCY_POC.md`.
- ✅ TM DLOD Phase 3 — view-based box-proxy for Time Machine on large buildings (2026-07-20, bim-ootb
  #918-#922), live + user-accepted on real LTU hardware. `prompts/TM_DLOD_SCALE.md`.
- ✅ Fly Tour route cache re-fixed (2026-07-20) — cache's own un-evicted stale keys filled
  localStorage quota, killing the 41× win; self-heal shipped bim-ootb PR #926, W-TOUR-CACHE-EVICT
  PASS. `prompts/done/TOUR_ROUTE_CACHE.md` §4. ⛔ Follow-up GATED at §8, not started: general-nav
  DLOD/occlusion for ≥LTU-scale buildings — this problem class has failed 4× before (S258/259/261/
  262, hysteresis alone insufficient); Fable is authorized to investigate+report only, no
  implementation without user sign-off. `prompts/Viewer/FLY_TOUR_DLOD_SCALE.md`.
- 2026-07-05 through 2026-07-16 DONE items: `prompts/archive/PROGRESS_DONE_ARCHIVE_pre_2026-07-17.md`.
- Pre-2026-07-05: `prompts/archive/PROGRESS_DONE_ARCHIVE_pre_2026-07-05.md` / `_pre_2026-06-14.md`.
  Viewer S-series/DAGCompiler: MEMORY.md "Project — Shipped".

## OCI Deployment
- Live: `bim-ootb-live` (SYSNOVA landing + viewer + single DBs). Always upload here.
- Single DB per building: `buildings/{Name}_extracted.db` (metadata + geometry + bbox).
- `deploy/sandbox/` stale (last ~S225) — not used for deploy. `deploy/dev/` is canonical.
- Deploy SOP: `deploy/OCI_UPLOAD.md`

## Reference
- Docs site: https://red1oon.github.io/BIMCompiler/
- Academic paper: `docs/SPATIAL_COMPILATION_PAPER.md`
- OCI setup: `internal/OCI_SETUP.md`
