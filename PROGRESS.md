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

## Fable5 follow-up (2026-07-04) — 3 items spec'd, NOT yet assigned (awaiting Graph Modeller review comment first)
- `prompts/FABLE5_FOLLOWUP_2026-07-04.md`: (1) **`ErpShard.maybeShard` has zero callers** — T7 sharding infra
  shipped but unwired, the scale-cliff risk it fixes is still live in prod — highest priority; (2) `teams_pill.js`
  standalone-fallback close button; (3) `pos_lens.js` `.pos-pill-btn` witness.
- ARC-mesh/readPixels stranded branch (bim-ootb #638): ported the non-redundant STR/canopy render +
  readPixels harness onto `main`, correctly discarded the branch's now-redundant mesh rewrite (`real_geometry.js`
  already superseded it); stranded branch deleted. One honest miss: `W-DW-DENSITY-TE` 8/8→7/8 (ARC occupancy
  99%→92-95%, unrelated pre-existing drift, low-priority follow-up).

## Kernel op-log timebomb lane — T1/T2/T3/T6/T7 ✅ SHIPPED; T4+T5 remain
- Findings: `prompts/KERNEL_TIMEBOMB_AUDIT_2026-07-03.md`. Shipped: T3+T6 (#623, kernel v10,
  W-PCLOSE-ARCHIVE 10/10 + W-CROSS-TAB-PERSIST 9/9) · T2 content sigs + T1 roster/key-epochs (#630, v11,
  W-CONTENT-SIGN 14/14 + W-ROSTER-VERIFY 17/17) · T1 employee PIN attribution (#634, W-T1-ATTRIB 16/16) ·
  T7+4b incremental+shard (#636, v12, W-T7-INC 35 🟢, pre-merge adversarial review closed 3 findings).
  Batch-1 spec: `prompts/KERNEL_HARDENING_BATCH1_SPEC.md`; detail: FABLE5_WRAPUP statuses + memory.
- ⛔ T4+T5 (unify 3 kernel copies) BROWSER-GATED — analysis done (neither copy is a superset), needs the
  W-ONE-KERNEL building-load smoke. Deferred: commitGroup id-race retry. Lane status: batch-1 spec §STATUS.
- Modeller OPEN (unassigned): item 9 PBR textures; SSAO (needs EffectComposer vendored).

## Codebase quality audit (2026-07-02) — TRIAGED 2026-07-03
- ✅ §5 self-XSS fixed BOTH repos (bim-ootb PR #618 sw v758 + bc PR #20; W-XSS-FILENAME 10/10 + 5/5, incl. the
  download-link sink the audit missed) · ✅ §2 doc-vs-code drift fixed, every number re-verified (bc PR #20).
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
