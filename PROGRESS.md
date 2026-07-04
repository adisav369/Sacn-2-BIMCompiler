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

## Fable5 follow-up + watchdog session 2026-07-04 — ALL MERGED/PUSHED
- Fable5's 3 items (T7 host wiring, teams_pill close, pos-pill-btn witness) — bim-ootb PR #639 MERGED.
- §PCLOSE-RACE (T7-RACE's TOCTOU shape found again in `erp_period_close.js`, fixed) — PR #640 MERGED.
- W-HBA-CLICKTHRU: first REAL click-through witness for HBA's 6 deep-links (found 3/6 dead — PR #641 MERGED);
  fixed 2 of them (missing AD_Window seed rows, PR #643 open); Tenancy/316 left to the Construction-window spec.
- HBA §2026-07-04 A/B/C ✅ DONE (design pass + build, same session): "Construction" AD_Window minted over
  M_Warehouse (proto-cloned from window 139) + Find-panel Room-lens "iDempiere ↗" link · Presence forward
  link into AD_User(108) · Leave TAKE now also compiles a real `S_ResourceUnAvailable` row ("Resource ↗" into
  window 236). Witnessed (10/10 new + 4 extended, full 41-file suite zero regression) + 2 live Playwright
  smokes (8/8 real HHS building, 9/9 fm_panel.html), bim-ootb PR #645 (`lane/hba-construction-clickthru`,
  awaiting merge). Doc updated: `docs/HRBIMAssetGuide.md`. Full detail: `prompts/RESUME_HR_BIM_ASSET.md`
  §2026-07-04 (closeout appended).

## Kernel op-log timebomb lane — T1/T2/T3/T6/T7 ✅ SHIPPED; T4+T5 remain
- Findings: `prompts/KERNEL_TIMEBOMB_AUDIT_2026-07-03.md`. Shipped: T3+T6 (#623 v10) · T2+T1 roster (#630 v11)
  · T1 PIN attrib (#634) · T7+4b incremental/shard (#636 v12, pre-merge adversarial review closed 3 findings).
  Batch-1 spec: `prompts/KERNEL_HARDENING_BATCH1_SPEC.md`; witness counts: FABLE5_WRAPUP statuses + memory.
- ⛔ T4+T5 (unify 3 kernel copies) BROWSER-GATED — analysis done (neither copy is a superset), needs the
  W-ONE-KERNEL building-load smoke. Deferred: commitGroup id-race retry. Lane status: batch-1 spec §STATUS.
- Modeller OPEN (unassigned): item 9 PBR textures; SSAO (needs EffectComposer vendored).

## Modeller: IFC-direct-open + Outliner unification (2026-07-04) — ✅ MERGED, both PRs landed on main
- Opens ARC-only `.ifc` directly (reuses Viewer's parse engine, always filtered to ARC — 0-mismatch parity
  proven on a real 3504-elem multi-disc IFC); `IFC/` + `IFC/BimDB/` folders live (SH+DX populated). One Disc
  tab (absent disciplines walk-clickable, no separate tab) + Find expands/highlights matches + always-visible
  Teams pill. All Playwright-verified live. Detail: `prompts/ARC_GEO_FETCH_SPEC.md §3D` + memory
  `project_modeller_arc_fetch_redesign.md`/`project_modeller_competitive_polish.md`.
- Walking tests DONE: `modeller/tests/witness_e2e_walk_ifcopen.js` (W-E2E-WALK-IFCOPEN, 18/18) drives real
  STR-surface + "▶▶ Walk ALL Disciplines" clicks across SampleHouse+Duplex (IFC-open) and SampleCastle
  (.db-open, the "more residents" leg). Found + FIXED along the way: `openIfcFile` never forked its own
  op-log key (unlike a `.db` resident's `_forkEditable`→`setModelKey`), so two IFC-opened buildings in the
  same tab silently shared one signed op-log. Fixed with a `mo_ifc_<name>` key fork before `_openBuffer`.
  **bim-ootb PR #642 MERGED to main.**
- 3D-Grid cross-resident pass DONE: `Bonsai.grid` is a DECOUPLED authoring grid — no production code wires an
  opened building's own STR-walked grid into it, so what generalizes IS the author-a-grid→drag→recompose
  mechanism after a real (not just Duplex) open. New `modeller/tests/witness_e2e_gridstretch_multi.js` found
  a real bug: on SampleCastle, `#b-clear` reset the scene+op-log but not `str_walker_outliner.js`'s own
  STR-walker module state (`ready`/skeleton), so a later grid-drag on an unrelated synthetic wall still fired
  SampleCastle's real STR re-walk, colliding on `kernel_ops.id` and rolling back (safe, but a spurious
  rejected-toast). Spec'd the fix decision in `prompts/GRID_CLEAR_STATE_LEAK_FIX.md` (option B — narrow
  reset: `STRWalkerOutliner.onClear()` resets `ready`/`lastEx`/`__dwBuf`/`__dwName`, wired into `#b-clear`).
  **FIXED + MERGED: bim-ootb PR #644** (`lane/grid-clear-state-leak-fix`, commit `2f9bd04`→squashed `587622c`).
  Independently re-verified (not just trusting the commit message): `witness_e2e_gridstretch_multi.js` 21/21,
  `witness_e2e_gridstretch.js` 7/7, `witness_e2e_walk_ifcopen.js` 18/18 — all green, no regressions.
- NEXT: continue `prompts/ARC_GEO_FETCH_SPEC.md §NEXT` item 2 — onboard Hospital/Clinic/LTU/HHS_Office as
  Modeller residents + migrate SH/DX/SC into the canonical `IFC/` folder.

## Codebase quality audit (2026-07-02) — TRIAGED 2026-07-03; §2/§5 DONE (bc #20, bim-ootb #618) → archived
- ⛔ BLOCKED (user call): are `migration/DV_*_rules.sql` mined-rule files EXEMPT from append-only, or enforce?
- OPEN: §1 refactors (spec-first, Sacred file), §2 dead-code removal, §3 shallow specs 27/29 (bim-ootb).
  Full triage: `prompts/CODEBASE_QUALITY_AUDIT_2026-07-02.md §TRIAGE`.

## Archive — DONE/shipped (one-line pointers; detail in cards + memory topic files)
- Fable5 wrap-up, all 6 items + pills consolidation — bc #37, ootb #633/#634/#635/#636/#637, bc #38 (2026-07-04; `prompts/archive/FABLE5_WRAPUP_2026-07-03.md` + `prompts/archive/PILLS_CONSOLIDATION_REVIEW_2026-07-03.md`)
- Unified docs pass leftovers — bc #35/#36, HBA BOM shot + anchors + branch dedupe (2026-07-03; `prompts/RESUME_UNIFIED_DOCS_PASS_2026-07-03.md`)
- HBA Stage 3 + C_Attendance retirement — bim-ootb PR #632, suite 40/40, §HBA_GOVERN live smoke (2026-07-03; `prompts/RESUME_HBA_ERP_STAGE3.md`)
- Modeller §NEEDS-DESIGN batch + item 10 T/S arms — bim-ootb #625/#627/#631, 30/30 + 8/8; spec bim-ootb `prompts/RESUME_MODELLER_POLISH3.md` (2026-07-03)
- HBA lane/hr-overlay sync+PR handoff — bim-ootb PR #628 `e42a96b` + closeout #629, 39/39 (2026-07-03; memory [[project_hba_erp_governed_display]])
- Ninja Create two-way engine + live export — `prompts/NINJA_MODE_PILL.md # DONE`, W-NINJA-{EXTRACT,CALLOUT,EXPORT,EXPORT-LIVE} + W-ASSET-STATUS (bim-ootb PR #301/#309, sw v673/v681, 2026-06-14)
- Reflexive AD self-edit — W-AD-{OPLOG-DISTRIB,SELFEDIT,SELFEDIT-LIVE} (bim-ootb PR #312 sw v683, 2026-06-14)
- Odoo red-band fold-gap re-audit — W-ODOO-QWEB 41/41 to-the-cent; server actions honestly deferred; migrate_status_panel live (2026-06-14)
- Pre-2026-06-14 DONE items (21 lines) → `prompts/archive/PROGRESS_DONE_ARCHIVE_pre_2026-06-14.md`
- Viewer S-series (S188–S286): browser viewer, DLOD, mobile perf, find/nav, multi-format import, cinematic — see MEMORY.md "Project — Shipped"

## OCI Deployment

- Live: `bim-ootb-live` (SYSNOVA landing + viewer + single DBs). Always upload here.
- Single DB per building: `buildings/{Name}_extracted.db` (metadata + geometry + bbox).
- `deploy/sandbox/` stale (last ~S225) — not used for deploy. `deploy/dev/` is canonical.
- Deploy SOP: `deploy/OCI_UPLOAD.md`

## Earlier Work (compressed)

- **S200-S210:** BIM OOTB browser viewer, OCI deployment, BOQ charts, health checks
- **S195-S198:** Direct DB streaming (replaced Blender .blend pipeline)
- **S188-S193:** RTree, nD engine, DLOD — all Blender-era, superseded by browser viewer
- **S165-S186:** GN instances, chunked loading, cockpit UI — GN HALTED, RTree won
- **2D Layout:** Phase A closed, Java pipeline 5/5, 13/13 conformity. Browser DXF viewer (S236).
- **DAGCompiler:** S190 fleet 21 buildings. S104 IFCtoERP complete.

## Reference

- Docs site: https://red1oon.github.io/BIMCompiler/
- Academic paper: `docs/SPATIAL_COMPILATION_PAPER.md`
- OCI setup: `internal/OCI_SETUP.md`
