# ⚠ DO NOT REMOVE — Scope guard / RESUME CARD for the ERP-backend session
# Lane: ERP BACKEND behavioural coverage + substrate hardening. The history timeline works; backend lane is
#       headless-disjoint from it (don't disrupt it). The 🟡→✅ live-wiring (`AD_BEHAVIOR_HANDOFF.md` +
#       `AD_RENDER_HANDOFF.md`) was PARKED through Track A — it is now the recommended NEXT focus, but CONFIRM
#       with the user before advancing it (it was parked by prior decision).
# NON-NEGOTIABLE: EXTRACT, DON'T INVENT — counts/grammars/rules come from `build/erp/ad_full.db` (927 tables,
#       lowercase) + the iDempiere checkout `~/idempiere-dev-setup/idempiere`; never hand-author a rule the AD
#       defines. Spec-first; whitebox §-log FIRST (READ the log; exit code ≠ evidence); deterministic on every
#       fold/replay path (recorded ids/ts, INTEGER CENTS, no Date.now/Math.random); MECHANISM not CORPUS (build
#       the dispatch/derivation spine + a few real handlers, name the unported remainder honestly). Each lane
#       closes by RE-VERDICTING its matrix rows.
# Read first: docs/ERP_COVERAGE_MATRIX.md (scoreboard) · docs/ERP_BACKEND_SEPARATION.md (the §0 design the
#       engines obey) · docs/HolyGrail.md §"hard parts" · docs/DistributedERP.md §8 (accounting = reconciliation).

---

# RESUME — START HERE (2026-06-09)

> **⛳ THIS CARD IS CLOSED — Track A is DONE. The live successor is `prompts/HARDEN_MATRIX.md`** (the equivalence
> arc: coverage-🟡 → oracle-diffed-✅, anchored on the MOrder archetype). Read that card to continue; everything
> below is preserved as the Track-A ledger it builds on.

**STATE — in sync, branch `feat/erp-substrate-phase012` (CICD merges to master; verified ahead=0 behind=0 2026-06-09):**
- Scoreboard `docs/ERP_COVERAGE_MATRIX.md` = **0✅ / 37🟡 / 3⛔** (dropped ELEVEN ⛔ from 14).
- **TRACK A COMPLETE** (§0 design + A-1…A-6) **+ option-(a) minor SQL/FK surfaces** — see DONE LEDGER below.
- The **3 remaining ⛔ are ALL n/a-in-seed, not gaps:** AD_Rule (4 SQL ruletype-Q `Fact_Reconciliation` rules —
  verified **not** Groovy; target `fact_acct`/`fact_reconciliation` empty + Postgres-only SQL) + the 2 empty
  `*_Access` tables (0 rows). **Every interpretable surface WITH SEED DATA is now 🟡 — zero real interpreter gaps.**
- **NEW since this card: a SECOND axis** (`docs/ERP_COVERAGE_MATRIX.md §Second axis — EQUIVALENCE`) banks the
  **first oracle-✅** (TB-read folds real GardenWorld `fact_acct`, 300 rows, `maxDiff=0c`). Coverage-🟡 ≠ equivalence;
  hardening each 🟡 to oracle-diffed-✅ is the `HARDEN_MATRIX.md` arc.

**NEXT — go to `prompts/HARDEN_MATRIX.md` (H-1 MOrder keystone). Two background lanes remain, neither is the default:**
- **(A) PARKED UI bridge — wire ONE engine into the live screen → first 🟡→✅** (`AD_BEHAVIOR_HANDOFF.md` /
  `AD_RENDER_HANDOFF.md`). The *live-UI* third axis; **still parked**, CONFIRM before advancing. Note equivalence
  (the HARDEN arc) reaches ✅ WITHOUT the UI — so this is no longer "the only path to ✅".
- **(B) Track B substrate hardening §H-7…§H-11** (`prompts/SERVERLESS_HARDENING_RESUME.md`) — parallel durability
  axis; lower marginal value (flank already defended by §H-1…§H-6 + the DR/TCO work).

---

# DONE LEDGER — Track A + minor surfaces (all ✅, every witness exit 0, on the pushed branch)

**§0 SEPARATION DESIGN SPEC ✅** → `docs/ERP_BACKEND_SEPARATION.md` — the 3-layer invariant (DECLARATION /
INTERPRETATION / LOG-FOLD, "Three Concerns never merge") + per-concern seam map; the two risk seams PINNED:
**posting↔workflow** (GL fold ignorant of WF) and **callout↔val-rule** (DERIVE vs VALIDATE). Every engine below obeys it.

| Lane | Module (build/erp/) | Witness (scripts/poc_*.js → build/erp/*.log) | Result |
|---|---|---|---|
| A-1 posting | *(already built)* `post_resolver.js` + `poc_post.js` | `poc_post_derive` — `§POST_DERIVE C_Invoice#100 ΣDR=ΣCR=50.35 bal=0` | re-verdict: derivation re-proven on canonical db; 1/20 Doc_*, 19 deferred |
| A-2 val-rule | `ad_valrule.js` (W-VALRULE) | `poc_valrule` — 332 rules, 327 interpretable; §FALSIFIER paid-invoice excluded | ⛔→🟡 (14→13) |
| A-3 callout | `ad_callout.js` (W-CALLOUT) | `poc_callout` — C_OrderLine 124 derives LineNetAmt/Price == stored | ⛔→🟡 (13→12) |
| A-4 model-val | `ad_modelval.js` (W-MODELVAL) | `poc_modelval` — 11 timings; qty≤0 + 0-line order BLOCKED | ⛔→🟡 ×2 (12→10) |
| A-5 DocType FSM | `ad_docfsm.js` (W-DOCFSM) | `poc_docfsm` — reaches 11/12 statuses (was 2); illegal transitions rejected | ⛔→🟡 (10→9) |
| A-6 workflow | `ad_workflow.js` (W-WF) | `poc_wf` — walk 50000>50002>50001 + split-node routing | ⛔→🟡 ×2 (9→7) |
| (a) Tab query | `ad_tabquery.js` (W-TABQUERY) | `poc_tabquery` — WhereClause filter (SO→4, PO excluded) + OrderBy sort | ⛔→🟡 ×2 (7→5) |
| (a) Reference | `ad_reference.js` (W-REFERENCE) | `poc_reference` — AD_Ref_Table FK membership + ValueFormat mask | ⛔→🟡 ×2 (5→3) |

Pattern (every module): self-contained IIFE, no kernel dep, mechanism-not-corpus port reading real `ad_full.db`
rows, every value EXTRACTED not invented, a `§`-witness + `§FALSIFIER`, re-verdicts its matrix rows. Prior
engines it sits beside: `ad_evaluator.js` (W-LOGIC-EVAL) · `ad_access.js` (W-ACCESS) · `ad_process.js` (W-PROC).

**Why A-1/refold mattered (the fold thesis):** the serverless claim rests on *"accounting is the reconciliation
engine"* (DistributedERP §8) — postings must be DERIVED from editable config (not frozen) so a rule edit + refold
reflows the GL. `poc_post.js`/`poc_post_derive.js` satisfy that backend prereq; the on-screen reflow = the parked
UI bridge (the HolyGrail §RULE-EDIT grail, already proven on Odoo via `RULE_EDIT_ONE_GESTURE.md`).

---

# TRACK B — substrate hardening Tier-2 (option B; driver: `prompts/SERVERLESS_HARDENING_RESUME.md`)
§H-7 scheduled-jobs-without-a-server (idempotent first-edge-past-boundary fold) · §H-8 anti-backdating (signed
period-lock) · §H-9 multi-currency/FX revaluation · §H-10 SoD/maker-checker (now UNGATED — A-6 workflow done) ·
§H-11 live divergence detection (signed-tip heartbeat). Same §-witness+§FALSIFIER shape as §H-1…§H-6. Scoped-OUT:
intercompany consolidation + 500-user RBAC.

# METHOD (every lane)
1. Spec in the relevant driver before code (`prompts/APP_COVERAGE_LANE.md` for coverage; `SERVERLESS_HARDENING_RESUME.md` for Track B).
2. `// Implementing ERP_COVERAGE_MATRIX.md §<surface> — Witness: W-<NAME>` pre-flight citation.
3. Whitebox §-log = the proof; node POC against the REAL kernel/db. READ the log.
4. Close the loop: re-verdict the matrix rows + update the headline.

# STOP CONDITION
Each started lane has a §-witness (exit 0) + re-verdicted matrix rows. Backend can only move ⛔→🟡; **✅ needs the
UI bridge** — if option A is chosen, that ceiling lifts. If a lane needs a user decision that can't be EXTRACTED →
`⛔ BLOCKED: <the one question>` and move on. Don't disrupt the history-timeline feature (headless-disjoint).
