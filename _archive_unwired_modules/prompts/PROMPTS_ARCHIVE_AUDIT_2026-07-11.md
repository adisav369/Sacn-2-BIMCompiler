<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# PROMPTS/DOCS ARCHIVE AUDIT — 2026-07-11

```
# ⚠ DO NOT REMOVE
SCOPE: Housekeeping only — no product code changes. `prompts/` has 214 top-level `.md` files (119
already in `prompts/archive/`). User directive 2026-07-11: "review docs/md, archive stale, maintain
prompts/# for present work.. keep to such doc trail." This doc IS the doc trail for that directive —
update it in place as you work, don't just report back verbally. Read the log after every run (N/A
here, no code — but still verify every claim of "done/superseded" against real evidence, not vibes).
```

## Method (do not skip the cross-reference step — it already caught one false positive)
A naive `git log -1` per file, filtered to "last touched before the 2026-07-07 00:07:42 bulk
migration commit," is NOT sufficient evidence of staleness on its own — that migration touched
~180 files in one commit (a `prompts/` reinstitution event, see memory
`feedback_prompts_migrating_check_other_repos.md`), so "untouched since 07-07" just means nobody's
needed the file since, not that it's dead. **Case in point:** `prompts/CODEBASE_QUALITY_AUDIT_
2026-07-02.md` is in the date-based candidate list below but is STILL live-referenced from
`PROGRESS.md` ("⛔ BLOCKED (user call): ... Full triage: prompts/CODEBASE_QUALITY_AUDIT_2026-07-02.md
§TRIAGE") — archiving it would have orphaned an open pointer. **For every candidate: grep `PROGRESS.md`
+ `MEMORY.md` + every other live `prompts/*.md` for a reference to the filename before touching it.**
If referenced anywhere live, it is NOT a candidate — skip it, don't archive.

## Candidates (36 files, last touched before the 07-07 bulk migration — starting list only, verify each)
```
prompts/GANTT_ACCURACY.md                          prompts/ACCTS_POSTED_PANEL.md
prompts/WAREHOUSE_GH_LINK_PILL.md                   prompts/USER_GUIDE_REVAMP.md
prompts/HARDEN_MATRIX.md                            prompts/WH_ROBOTICS_LANE.md
prompts/BIM_TO_PROJECT.md                           prompts/CODEBASE_QUALITY_AUDIT_2026-07-02.md
prompts/KERNEL_TIMEBOMB_AUDIT_2026-07-03.md         prompts/KERNEL_HARDENING_BATCH1_SPEC.md
prompts/RESUME_ATTENDANCE_SRESOURCE_RETARGET.md     prompts/WATCHDOG_BIM_ERP_SOURCE_OF_TRUTH.md
prompts/RESUME_HBA_ERP_STAGE3.md                    prompts/CI_GATE_FIRST_REAL_RUN_FINDINGS_2026-07-03.md
prompts/ANALYSIS_SIDECAR.md                         prompts/BIM_PROJECT_FINANCE_LANE.md
prompts/BLANK_SCREEN_IDLE.md                        prompts/COMBINED_ERP_LANE.md
prompts/ERP_BOTTOM_BAR_AND_LIFECYCLE.md             prompts/FIND_VIEW_HISTORY.md
prompts/HANDOFF_ghost_xray_rooms.md                 prompts/HISTORY_SCRUB_FIX.md
prompts/INVESTIGATE_WORKTREE_ENFORCEMENT.md         prompts/MOBILE_CARDS.md
prompts/MOBILE_META_SPLIT_FIX.md                    prompts/MOBILE_PERF.md
prompts/RESUME_DISTRIBUTED_BRANCHES.md              prompts/RESUME_HBA_ERP_GOVERNED_DISPLAY.md
prompts/RESUME_MODELLER_ARC_ANCHOR_PLACEMENT.md     prompts/RESUME_TEAMS_OVERLAY.md
prompts/RESUME_TEAMS_UI_CONSISTENCY.md              prompts/SIDECAR_LIFECYCLE.md
prompts/SpatialERP_POC.md                           prompts/UI_UX_LANE.md
prompts/UNIVERSAL_HISTORY.md                        prompts/RESUME_UNIFIED_DOCS_PASS_2026-07-03.md
prompts/FRONTEND_LANE_MASTER.md
```
`FRONTEND_LANE_MASTER.md` is a near-certain archive: `CLAUDE.md`'s own WORK-TO-ZERO section already
states its `§NEW BACKLOG` DRAINED 2026-07-08 (every item ✅) and says "do NOT re-walk" — confirm that
line still reads that way, then archive per the SAME pattern already used for its `§OUTSTANDING` band
(`prompts/archive/FRONTEND_LANE_MASTER_OUTSTANDING_drained_2026-06-20.md`).
`RESUME_TEAMS_OVERLAY.md`/`RESUME_TEAMS_UI_CONSISTENCY.md` — memory `project_teams_e2e_no_ui_finding.md`
says "Teams overlay dual-user E2E, Viewer scope retired" — check if that means these two are fully
superseded or only partially.

## Per-file verdict criteria
- **Archive** if: the file's own content declares itself DONE/SHIPPED/RETIRED/SUPERSEDED, AND it has
  zero live references from `PROGRESS.md`/`MEMORY.md`/other active `prompts/*.md`.
- **Leave in place, flag here instead** if: genuinely ambiguous (e.g. "parked" language, no clear
  done/not-done signal) — do not guess. Add a one-line note in the table below and move on; a human
  or a future targeted session decides, not a blind archive pass.
- **Archive mechanics**: `git mv prompts/X.md prompts/archive/X.md` (preserves history, matches
  existing convention — do NOT `rm` + re-add). Add a one-line pointer to this doc's log below.

## `docs/*.md` — lighter pass, same rule
Check `docs/*.md` too (smaller directory, likely less churn) — same criteria, same archive mechanics
(`docs/archive/` if that doesn't already exist, create it following the `prompts/archive/` pattern).
Don't force a big pass here if nothing looks stale — report "checked, nothing to archive" as a real
finding, not a gap.

## Log (append here as you go — this IS the report, not a separate message back)
- 2026-07-11: doc created, candidate list assembled from `git log` + cross-ref against
  `CODEBASE_QUALITY_AUDIT_2026-07-02.md` false-positive catch. Audit not yet started.
- 2026-07-11 (audit executed, same day): all 37 listed candidates cross-referenced (grep of `PROGRESS.md`
  + the memory dir + every live `prompts/**/*.md`, excluding `prompts/archive/` + this doc + self-refs),
  then each undecided file's own head/tail read for a DONE/RETIRED/SUPERSEDED self-declaration.
  **Verdict: 9 ARCHIVED · 22 KEPT · 6 FLAGGED.** Note: the doc's header says "36 files" but its own code
  block lists 37 — all 37 were processed.

### Method note — memory-topic-file references are NON-blocking (precedent, verified)
A reference from a memory *topic* file (e.g. `project_wh_robotics_lane.md`) does NOT keep a prompt live:
20+ files already in `prompts/archive/` are still pointed at by memory topic files (`ERP_AD_UI.md`,
`CRUD_EDIT_PERSIST.md`, `IMPORT_EXPAND_POC.md`, `HISTORY_WHOLE_TIMELINE.md`, …) — that pointer style is
the accepted convention (archived files stay greppable by name). Blocking references = `PROGRESS.md`,
`MEMORY.md` itself (index — referenced NONE of the 37), or an active non-candidate `prompts/**/*.md`.
References from `prompts/done/*.md` and from fellow candidates archived in the same pass are non-blocking.

### ARCHIVED (9) — `git mv prompts/X.md prompts/archive/X.md`, all staged as renames
| File | Evidence (self-declaration + reference state) |
|---|---|
| `RESUME_ATTENDANCE_SRESOURCE_RETARGET.md` | Line 1: "⚠ DEPRECATED — merged into prompts/RESUME_HBA_ERP_STAGE3.md, 2026-07-03". Zero live refs. |
| `RESUME_HBA_ERP_STAGE3.md` | Header: "✅✅✅ ALL DONE 2026-07-03 (Fable5) — bim-ootb PR #632… See §DONE appendix". Only refs: memory topic + the deprecated file above (archived together, pointer resolves within archive/). |
| `USER_GUIDE_REVAMP.md` | "DONE WHEN" checklist all ✅ incl. "mkdocs gh-deploy run, site live ✅". Zero refs anywhere. |
| `WAREHOUSE_GH_LINK_PILL.md` | Acceptance/witness checklist all ✅ (W-WH-GH, W-WH-PILL, W-POS-LIVE, §IDMP-SHARE, …, "lane-master + poc + ERPUserGuide updated ✅"). Only ref: memory topic `project_multi_lane_launch.md` (non-blocking). |
| `MOBILE_CARDS.md` | §6 Status: "✅ DONE (witness) — branch feat/mobile-cards, sw v585→v586" + logged §MOBILE-VIEW PASS + screenshots. Zero refs. |
| `BLANK_SCREEN_IDLE.md` | "## SPEC — ✅ DONE (witness below)" + Witness (PASS) via `tests/probe_idle_blank.js`. Zero refs. |
| `RESUME_UNIFIED_DOCS_PASS_2026-07-03.md` | Header: "DONE, bc PR #33 merged" + "✅ CLOSED 2026-07-03 (bc PR #35): all three leftovers done". Zero refs. |
| `FRONTEND_LANE_MASTER.md` | Per this doc's own instruction: CLAUDE.md WORK-TO-ZERO line CONFIRMED still reading "§NEW BACKLOG DRAINED 2026-07-08 (every top-level item ✅)… Do NOT re-walk". Same treatment as the §OUTSTANDING band (already in archive/). PROGRESS.md's "Full detail:" pointer + CLAUDE.md's mention BOTH updated to the `prompts/archive/` path (see pointer-updates note below). Its ~30 other prompt-file mentions are historical provenance notes ("from FRONTEND_LANE_MASTER §2 Item C"), not open-work pointers. |
| `COMBINED_ERP_LANE.md` | Superseded by explicit declaration in FRONTEND_LANE_MASTER.md header: "THIS SUPERSEDES … COMBINED_ERP_LANE.md (kept only for detail; act from HERE)". Superseder is drained + archived in this same pass — both now live side-by-side in archive/, so the "kept for detail" pointer still resolves. Other refs: `prompts/done/BACKEND_LANE_S2.md` only (non-blocking). |

### KEPT LIVE (22) — reason each is NOT archivable
| File | Why kept |
|---|---|
| `CODEBASE_QUALITY_AUDIT_2026-07-02.md` | The known false positive: live `PROGRESS.md` ⛔ BLOCKED pointer ("Full triage: … §TRIAGE"). |
| `KERNEL_HARDENING_BATCH1_SPEC.md` | Live `PROGRESS.md` pointer: deferred "Kernel op-log T4+T5" item cites "Spec: prompts/KERNEL_HARDENING_BATCH1_SPEC.md §STATUS". |
| `KERNEL_TIMEBOMB_AUDIT_2026-07-03.md` | Referenced by the live KERNEL_HARDENING_BATCH1_SPEC.md + FABLE5_FOLLOWUP_2026-07-04.md + RESUME_SESSION_2026-07-03_WATCHDOG.md. |
| `HARDEN_MATRIX.md` | Referenced by 8+ active prompts (MULTI_LANE_LAUNCH, FOLD_MODEL_LOGIC, FABLE5_*, POS_GAP_CLOSE, MULTI_LANE_WAVE3, …). |
| `WATCHDOG_BIM_ERP_SOURCE_OF_TRUTH.md` | Self-declared "standing watchdog charge, not a one-off task… STANDING RULE stays active"; MEMORY.md carries the matching STANDING WATCHDOG entry. Never "done" by design. |
| `CI_GATE_FIRST_REAL_RUN_FINDINGS_2026-07-03.md` | Contains an OPEN deferred user decision ("The decision this needs (user, not a coding call) — deferred"); MEMORY.md `project_ci_system_is_real_red_x.md` says "bundled, not fixed". |
| `GANTT_ACCURACY.md` | Live ref from `prompts/TM_SCHEDULE_EDITOR.md`; only section A marked DONE (S253e), B–D never closed. |
| `WH_ROBOTICS_LANE.md` | §OUTSTANDING all unchecked (§R-1..R-4, PR, sw bump); active lane in MEMORY.md User section. |
| `BIM_TO_PROJECT.md` | Open lane spec, no DONE; successor/sibling finance lane is active and its memory (`project_bim_to_project.md`) is a named read-first source. |
| `BIM_PROJECT_FINANCE_LANE.md` | Active lane: "## Read first (each session opening this lane)"; F-cards individually ✅ but lane explicitly session-reentrant. |
| `ANALYSIS_SIDECAR.md` | Referenced as spec by the live MOBILE_PERF.md work-order (and by SIDECAR_LIFECYCLE.md). |
| `SIDECAR_LIFECYCLE.md` | Open gap work-order ("Close that gap" — OPFS cache invalidation), no DONE signal. Unreferenced but open work, not stale. |
| `ERP_BOTTOM_BAR_AND_LIFECYCLE.md` | Self-status "TRIAGE / SPEC ONLY… No implementation in this pass" — an un-executed plan (§A→§B→§C), still the organising doc. |
| `HISTORY_SCRUB_FIX.md` | Referenced by the live ERP_BOTTOM_BAR_AND_LIFECYCLE.md (+ INVESTIGATE_WORKTREE_ENFORCEMENT.md). |
| `UNIVERSAL_HISTORY.md` | Referenced by ERP_BOTTOM_BAR_AND_LIFECYCLE.md + HISTORY_SCRUB_FIX.md (both kept). |
| `HANDOFF_ghost_xray_rooms.md` | Referenced by INVESTIGATE_WORKTREE_ENFORCEMENT.md; its "ONE issue left = room compile accuracy" is the ancestor of the CURRENTLY ACTIVE room-accuracy lane (ROOM015-020 commits on this very branch). |
| `MOBILE_PERF.md` | Self-declared standing live work-order: "the ONE place… a mobile-perf session works FROM HERE". |
| `MOBILE_META_SPLIT_FIX.md` | No DONE signal; memory `project_mobile_meta_split.md` sits in MEMORY.md's Project — Active band. |
| `RESUME_DISTRIBUTED_BRANCHES.md` | Live resume card: "## ▶ RESUME HERE (next dedicated session)"; STATUS = spec + first engine slice only. |
| `RESUME_HBA_ERP_GOVERNED_DISPLAY.md` | Live ref from active `prompts/Viewer/HBA/RESUME_HR_BIM_ASSET.md` (+ from the kept CODEBASE_QUALITY_AUDIT + WATCHDOG files). |
| `RESUME_TEAMS_UI_CONSISTENCY.md` | Internally R1–R6 all ✅, BUT live-referenced as the working spec by active `RESUME_GUIDES_AND_ICONS_UNIFY.md` §C ("Teams overlay spec RESUME_TEAMS_UI_CONSISTENCY.md §R1/§2 … icons MISSING and must be ADDED"). |
| `UI_UX_LANE.md` | Tail carries unchecked ROUND-3 resume items (B–E); live ref from `prompts/POS_SHOWCASE_LANE.md`. |

### FLAGGED AMBIGUOUS (6) — left in place, human/targeted-session call
| File | Why ambiguous (one line each) |
|---|---|
| `ACCTS_POSTED_PANEL.md` | Panel built + fully witnessed, but its mount+deploy was "Deferred to GO (master §7/§8)" — FLM's backlog drained (implying item C shipped) yet this file was never closed with its promised # DONE appendix. |
| `RESUME_TEAMS_OVERLAY.md` | Phase F "fully DRAINED" internally, but the file carries an explicitly OPEN production deploy gate ("NOT pushed to production… stays that way until the Modeller lane's in-flight work completes") — it is still the entry point for that pending action. |
| `RESUME_MODELLER_ARC_ANCHOR_PLACEMENT.md` | Primary fix "✅ FIX DONE… PR #613 MERGED", but "Secondary findings… remain PARKED/unclaimed" — parked language = do-not-guess per this doc's criteria. |
| `SpatialERP_POC.md` | Old POC spec with no self-close; likely superseded by the later AD-in-browser ERP line (it itself superseded `done/iDempiereOOTB.md`), but nothing declares it done/superseded. |
| `FIND_VIEW_HISTORY.md` | No # DONE appendix, yet UNIVERSAL_HISTORY.md's scope treats the Find-lens view-history scrubber as already built ("turn the NEW … scrubber into…") — looks shipped-without-close-out. |
| `INVESTIGATE_WORKTREE_ENFORCEMENT.md` | Deliverable (PreToolUse block-shared-tree hook) EXISTS and is verified per CLAUDE.md ("BLOCKED by a PreToolUse hook, verified 2026-06-06"), but the file itself has no DONE close-out. |

### Pointer updates made alongside the FLM archive (both files were already dirty from concurrent
### sessions — edits left UNCOMMITTED for those lanes to carry; paths only, no content change)
- `CLAUDE.md` WORK-TO-ZERO line: `prompts/FRONTEND_LANE_MASTER.md` → `prompts/archive/FRONTEND_LANE_MASTER.md` (+ "(archived 2026-07-11, prompts-audit)").
- `PROGRESS.md` ~line 102 "Full detail:" pointer: same path fix.
- NOT updated (historical provenance mentions, fine to point at the old path — file greppable in archive/):
  the ~30 other prompt files that cite FRONTEND_LANE_MASTER as origin/provenance.

### `docs/*.md` — lighter pass result: CHECKED, NOTHING TO ARCHIVE (a real finding, not a gap)
All 31 top-level `docs/*.md` were last touched 2026-07-03..09 — i.e. during/after the deliberate
unified-docs cleanup pass (bc PR #33 + #35, see the just-archived RESUME_UNIFIED_DOCS_PASS doc) — and
they ARE the live mkdocs site (protected by the no-shrink deploy guard; removing a page is a deploy
event, not a file-move). Grepped all 31 for DEPRECATED/SUPERSEDED/RETIRED/OBSOLETE: 6 hits, ALL content
mentions about *other* things ("superseded by nD engine", archive-table rows, "walk-in modal retired"),
zero self-declarations. `docs/archive/` already exists and holds the prior cleanups. `docs/internal/`
(50+ working docs) was NOT swept — the directive scoped the lighter pass to top-level `docs/*.md`;
flag for a future targeted pass if wanted.

### Post-move sanity + commit
- Post-move grep re-run: no live `prompts/*.md` (non-archive, non-done) references a now-archived file
  except (a) provenance mentions of FRONTEND_LANE_MASTER (accepted, above) and (b) the flagged
  `ACCTS_POSTED_PANEL.md`'s "master §7/§8" GO-gate pointer — noted in its flag row.
- Prompts top-level count 215 → 206; archive 117 → 126.
- Committed LOCALLY only (renames + this doc; `CLAUDE.md`/`PROGRESS.md` pointer edits left uncommitted
  with the other sessions' dirt). NOT pushed — LFS quota hard block (CLAUDE.md 2026-07-11) + task
  directive "do NOT push".
