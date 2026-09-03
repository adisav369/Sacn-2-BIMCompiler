# ⚠ DO NOT REMOVE — Order of play (session index) (refreshed 2026-06-02)
# Scope: the ORDER of play (TEMPORAL view) for the converged BIM↔ERP roadmap. Each phase has its own bounded
#        prompt (one task per session). For the STRUCTURAL view — which lanes run concurrently + the contracts
#        that couple them — see the sibling `docs/CONCURRENT_LANES_ROADMAP.md`.
#        Spec-first; witness-led; §-log first (READ the log before any conclusion);
#        EXPLICIT GO before any deploy. Honest framing is NON-NEGOTIABLE — see feedback_erp_perf_claims below.

---

# Where we are (status, 2026-06-02)

- **Foundation falsifiers GREEN** (PROGRESS.md was stale — now corrected): `poc_showstopper` `§SHOW PASS`,
  `poc_volume` `§VOL PASS`, `poc_email_dr` `§EMAIL-DR PASS`. The op-log kernel's hard parts (atomicity,
  period-close checkpoint = balance-b/f, OLTP physics+CAS, DR) hold on the real op shape. Only remaining
  falsifiers are migration-solvent on FOREIGN ERPs (`ODOO_FOLD_POC.md`, `SAP_FOLD_POC.md`) — gated on real dumps.
- **Published:** `docs/OpLogERP.md` LIVE (https://red1oon.github.io/BIMCompiler/OpLogERP/) — the technical abstract.
  READMEs made fair (bim-compiler `full`; bim-ootb PR #81 merged). The unifying film: https://youtu.be/hnLYNcRihzs
- **Glassbowl** is the live ERP-engine-as-data surface; CRUD + Process (signed write-loop) DONE on glassbowl.html.

# The honest-framing rule (carry into ALL docs/prompts) — feedback_erp_perf_claims
- NOT "invented" → assembled/forked; a novel COMBINATION of precedented primitives (Datomic/event-sourcing,
  QLDB/immudb, Durable Objects, local-first). NOT "100× faster than iDempiere" (no such benchmark — the 100×
  is self-scaling history). "extracted/forked from the iDempiere model," not a code port. "engine/architecture,"
  not a shipped accounting product. Strongest claim = offline + per-op verifiability (structural, irrefutable).

# Order of play (one bounded session each)

| # | Prompt | What | Gate |
|---|--------|------|------|
| ~~1·R1~~ | ✅ DONE+LIVE | **▤ Report verb — Receipt fold** shipped (`report_overlay.js`, sw v8, commit 96d3b7f0). Witness `§REPORT-RECEIPT` ALL PASS: folded subtotal==SUM(linenetamt), subtotal+tax==grandtotal 0c, non-fin honest. Spec `docs/CRUD_P_R_REPORT_SPEC.md`. | — |
| **1·R2** | `CRUD_P_R_REPORT.md` §R2 | **Trial Balance / P&L.** fact_acct=0 in this extract → **extract real GardenWorld `fact_acct` (+c_elementvalue/c_period/c_acctschema) from Docker Postgres** into the bundle, then fold. R2a fallback = the 4 real `gl_journalline` rows (185=185). | Docker PG up |
| **1·R3** | `CRUD_P_R_REPORT.md` §R3 | **Definition-as-data** — drive R1/R2 layout from `ad_printformat*` / `PA_Report*` rows (not the literal REPORT_MAP). | after R2 |
| **2** | `PILLS_TO_GLASSBOWL.md` | **Functional pills** — port the PillBuilder registry to glassbowl/gravity; pills carry ops (Report/Process/Shard). | step-0 locate the registry |
| **3** | `BIM_ERP_FOLD.md` | **The keystone** — Hospital 4D(tasks)+5D(qto_cache) → C_Project → C_Order Project Order; the ring operates on it. | needs Phase 1 (Report) + 5D cost surface |
| **3-pre** | `SETTINGS_5D_COST.md` | 5D Cost schedule provider in Settings (sibling to the 4D one) — the input surface the fold reads. | precondition of Phase 3 |

Reporting is the substance (Phase 1), pills surface it (Phase 2), the fold sources it from a real building (Phase 3).
"Wow, it's all there" lands because by Phase 3 the receipt + cost report are already one pill-tap away.

# Posting → Accts-Posted → Render chain (SEQUENCE — one session at a time, NOT concurrent)

These are RELATED BY DEPENDENCY, not overlap. Different lanes (migrate / engine / renderer); each hands
the next a PROVEN SEAM via ENGINE_CONTRACT, so run them SERIALLY — at most one active. Do NOT merge them
(that re-entangles the lanes); do NOT run them in parallel (hard data deps make it impossible anyway).

| Step | Lane | Session | Hands off | Depends on |
|---|---|---|---|---|
| **S1** | migrate | ShowMe-migrate import (`MIGRATE_SHOWME_OVERLAY.md`) + R2 `fact_acct` extract — COLLAPSE into one Docker pull | real `Fact_Acct` + masters resident | Docker PG up |
| **S2** | engine | Posting rollout — `PLUGIN_ARCHITECTURE.md §13.6` (Fact_Acct-gated POST for all postable doc-types) **+ §13.7** (`readPostings` role-gated read) as ONE engine session | the `readPostings` seam | S1 |
| **S3** | renderer | `IDEMPIERE_RECORD_PANEL.md` — reuse Glassbowl CRUD-P-R in idempiere chrome, Accts-Posted = Report role-gated | the shipped UI | **none — ships independently.** CRUD-P + Receipt resident; Accts-Posted *degrades gracefully* (`source/coverage` from §13.7): shows op-log postings + "install local for full history", or "install local first" if none. S1/S2 just light it up to `coverage:complete` — no panel change. |
| — | engine (DONE) | `done/ENGINE_POST_PROTOTYPE.md` — §13.1 closed, POST verb proven (commit `aec7ca49`, branch `full`) | §13.5/13.6/13.7 specs | — |

~3 sequential sessions, not 5. The contract is what makes serial safe — no live coordination.

**Within the render step, 3 lanes run CONCURRENTLY (not serial) — UI_OVERLAY_GOVERNANCE §lane-separation.**
Once the **host contract** (key vocabulary + exposed nav/projection globals) is pinned up front, these build
in parallel, coupled only by key (+ the read seam's graceful-degrade — nobody blocks on data):
- **Backend** — engine verbs/read/`readPostings` (`PLUGIN_ARCHITECTURE §13.5–13.7`).
- **Frontend/host** — `IDEMPIERE_RECORD_PANEL.md`: chrome + **tag elements + expose globals** (one pass,
  serves all overlays) + mount CRUD/Report.
- **TourGuide (overlay-aspect)** — `IDEMPIERE_TOUR_GUIDE.md`: reuse glassbowl `help_overlay`+`HelpO2C`,
  read-only, attaches by the same keys. (Migrate-install first-mile tour = separate, `MIGRATE_SHOWME_OVERLAY.md`.)

# Still TODO (not a prompt yet)
- `docs/ERP.md` new § (one-engine thesis + CRUD-P-R Report + BIM→ProjectOrder vertical) — the narrative home,
  sibling to §0.17/§0.19. Pointer from HolyGrail.md ("the BIM fold = the migration solvent turned inward").
- Fix PROGRESS.md stale falsifier line (3 done+green, 2 data-blocked).

# Parked / separate tracks
- Overlay port to Gravity (`GUIDE_SHOWME_PROCESS.md`) — still valid; independent of this reporting/fold arc.
- ODOO_FOLD / SAP_FOLD — foreign-ERP migration falsifiers, gated on acquiring real demo dumps.
