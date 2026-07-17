# ⚠ DO NOT REMOVE — Scope guard / RESUME CARD: HARDEN THE COVERAGE MATRIX (coverage → equivalence)
# Lane: take the ERP coverage matrix from "surface INTERPRETED" (37🟡 = engine touches the AD) to "behaviourally
#       EQUIVALENT" (engine output == real iDempiere output, oracle-diffed). Anchor on the MOrder archetype + its
#       document-family deltas — NOT a 496-class sweep. UI stays PARKED; equivalence is UI-INDEPENDENT (it diffs
#       engine vs iDempiere, not engine vs screen).
# NON-NEGOTIABLE: EXTRACT, DON'T INVENT — fixtures + oracle outputs come from the iDempiere checkout
#       (~/idempiere-dev-setup/idempiere) and build/erp/ad_full.db; never hand-author an expected output. Spec-first;
#       whitebox §-log FIRST (READ the log; exit code ≠ evidence); deterministic (recorded ids/ts, INTEGER CENTS,
#       no Date.now/Math.random); keep the §0 SEPARATION seams (one concern = one module; AD-declaration / interpreter
#       / log-fold never merge); MECHANISM not CORPUS where a corpus is infinite (prove the diff harness + the
#       archetype + a few deltas; name the unported remainder). A row is hardened only by an oracle DIFF, not a claim.
# READ FIRST — take it ALL in, in this order:
#   0. docs/MigrateComparisonPaper.md#status — the 4-state honesty panel (🟢 folds-today · 🟠 extraction · 🔴 fold-gap
#                                          · 🔵 deleted-by-architecture); the at-a-glance frame for everything below.
#   1. docs/ERP_COVERAGE_MATRIX.md      — the scoreboard (0✅/37🟡/3⛔; 🟡 = surface-touched, NOT proven-equivalent).
#   2. docs/ERP_MODEL_ARCHETYPE.md      — THE DENOMINATOR: MOrder = the core; the ~25 completeIt classes = deltas;
#                                          the master-data tail = AD_Column + light invariants. This is what to harden.
#   3. docs/ERP_BACKEND_SEPARATION.md   — the §0 seams the hardening must NOT violate.
#   4. prompts/ERP_BACKEND_GAP.md       — Track A (the interpreters) is DONE; this card is its equivalence sequel.
#   5. scripts/poc_odoo_fold*.js + scripts/poc_sap_*.js + build/erp/odoo_oracle*.json — THE ORACLE-DIFF TEMPLATE
#                                          already in the repo (engine fold vs captured reference output, maxDiff=0c).
#                                          Hardening = extending exactly this discipline to the MODEL layer.
#   6. prompts/SERVERLESS_HARDENING_RESUME.md — §H Tier-2 (substrate hardening) runs in parallel, separate axis.

---

# Harden the matrix — from coverage to equivalence

> **▶ OPERATIONAL BACKLOG:** the concrete, prioritized gap list + verified recon + per-gap output contract lives in
> `prompts/GAP_CLOSURE_LANE.md` (governed by the method spec `docs/GapClosureSpec.md`). This card is the WHY +
> denominator + seams; that card is the WHAT-NEXT. Start a gap-closure session there.

## The situation (synthesis — why this card exists)
The interpreter-coverage ladder is CLOSED: every behavioural surface with seed data now has an engine that reads
the AD and produces a verdict (Lanes 1–3 + A-1…A-6, matrix 0✅/37🟡/3⛔). **But none of it is oracle-diffed** — 🟡
means the engine *touches* the surface on sample rows, NOT that it produces *the same answer iDempiere would*. That
is the gap this card closes. **Coverage ≠ correctness.** The only existing equivalence proofs are the Odoo/SAP fold
POCs (engine fold == captured oracle, maxDiff=0c) — the right pattern, never extended to the model layer.

**Two axes, kept distinct (do not conflate):**
- **Coverage** (existing matrix verdict ✅/🟡/⛔) — is the surface interpreted? CLOSED.
- **Equivalence** (this card adds it) — does the engine output == iDempiere output for real inputs? NOT STARTED.
- (A third axis, **live-UI**, is the parked `AD_BEHAVIOR_HANDOFF` — out of scope here.)

## What "HARDENED" means — the new metric
Add an **Oracle column** to `docs/ERP_COVERAGE_MATRIX.md`: `⬜ not-diffed · ✅ oracle-equivalent · n/a (no oracle in
seed)`. A row is **hardened** when K real fixtures show **iDempiere-output == engine-output** with a `§`-log diff
(ΣDR=ΣCR + per-account/per-field maxDiff=0, or field-state/doc-status identical). Add a SECOND headline tally:
**"N of 40 oracle-equivalent."** The existing coverage tally stays; this is additive, not a re-verdict.

## ⚠ THE ORACLE ALREADY EXISTS (verified 2026-06-09 — do NOT build it from scratch)
- **Posting oracle = real GardenWorld `fact_acct`** (300 rows, client 11, balanced 46574.97) is **already captured
  locally in `build/erp/glassbowl_data.db`** via `scripts/extract_fact_acct.sh` (Docker Postgres `idempiere_test` →
  sqlite). `scripts/test_report_fin.js` already proves **TB-read equivalence to the cent** (`§REPORT-FIN-RECON …
  maxDiff=0c`). So the **trial-balance/report layer is the FIRST hardened ✅** — banked in the matrix already.
- **The real gap is GRANULARITY + SURFACE, not "no oracle":**
  1. The captured `fact_acct` has **no `record_id`/`ad_table_id`** → can prove the *aggregate journal* matches but NOT
     *per-document derivation*. **H-1 step 1 = extend `extract_fact_acct.sh`** to pull `record_id, ad_table_id,
     line_id` and re-capture, then diff `post_resolver`'s derived lines per document vs the real fact_acct lines.
  2. The **declarative + event surfaces** (logic/access/valrule/callout/modelval/FSM) have **no oracle at all** — they
     diff against iDempiere *Java semantics* (`GridField`/`MRole`/`MValRule`/`Doc_*`), which needs either a running
     instance (the same Docker `idempiere_test`) or captured per-surface fixtures.
- **NON-INVENT reminder:** never synthesize an oracle. If the Docker instance isn't up, a surface stays ⬜, not ✅.

## The oracle harness (extend the EXISTING capture — `extract_fact_acct.sh` is the template)
Following the Odoo/SAP pattern (capture once → diff deterministically), per surface type:
- **Posting** → capture real `Fact_Acct` lines from iDempiere for K documents → diff our `post_resolver` derivation.
- **Logic/ReadOnly/Mandatory** → capture `GridField` displayed/readonly/mandatory state for K (record, context) → diff `ad_evaluator`.
- **Access** → capture `MRole.getWindow/Process/FormAccess` + `canView` for K (role, target) → diff `ad_access`.
- **Val-rule** → capture the rows a `MValRule` SQL admits for K → diff `ad_valrule`.
- **DocAction** → capture the legal next-status set + the resulting status per (C_DocType, action) → diff our FSM.
Store oracle outputs as versioned fixtures (e.g. `build/erp/oracle/<surface>_oracle.json`) extracted from the
checkout — NEVER hand-authored. If iDempiere can't be run headlessly here, capture the fixtures from the AD/seed
the same way the matrix counts were taken, and say so.

## ORDER — anchor on the archetype, then walk the deltas (from ERP_MODEL_ARCHETYPE.md)
### H-1 ⭐ MOrder to equivalence — the keystone ("got MOrder, got the core")
Make the MOrder surface table in `ERP_MODEL_ARCHETYPE.md` GREEN by oracle-diff: its `beforeSave` invariants
(pricelist/warehouse/bpartner/credit) via `ad_modelval`, its FULL DocAction set (not just CO) via the FSM, and
`Doc_Order` posting via `post_resolver`. Witness `§HARDEN surface=MOrder.<x> fixtures=K diff=0 oracle=iDempiere`.
One archetype, proven — this is the highest-value single result in the whole arc.

### H-2 Walk the 25-delta table — deepest-delta-first
For each document-family class, diff only its DELTA from MOrder: `MInOut` (in-transit locator), `MPayment`
(allocation), `MProduction` (BOM explosion), `MInventory` (count), `MAllocationHdr` (headerless) FIRST — these
carry genuine document-specific logic; the rest are the trade pattern with a different line table + `Doc_*` poster.
`§HARDEN surface=<MClass> deltaFrom=MOrder fixtures=K diff=0`.

**✅ H-2 DONE 2026-06-11 in TWO sessions:** the deep deltas (`prompts/FABLE5_H2_DELTAS.md` — MInOut/MInvoice/
MPayment save+FSM + the inventory-family FSM) **and the WHOLE isomorph tail (`prompts/H2_ISOMORPH_TAIL.md` —
Journal/Batch, Allocation, Cash, BankStatement blocks + the 11-class generic-block tail, 10 witnesses)**. NO
DocAction table remains unwalked; the 0-seed classes (BankTransfer/DepositBatch/ProjectIssue/FA×5) are
source-parse-only with stored-replay honestly ⛔. Matrix ledger = **41 oracle-equivalent**. Both cards carry
§-lined `# DONE` appendices.

**✅ B-3 POSTING band DONE 2026-07-17 (W-POST-B3, `prompts/FABLE5_B3_POSTING_ORACLE.md`):** the 0-seed classes'
POSTING (the last un-oracled accounting surface) is closed — 2 ∅-by-design (no Doc_ in the factory: BankTransfer/
DepositBatch) + **6 G-seed classes oracled `maxDiff=0c`** against the REAL compiled posters driven in iDempiere's
own OSGi test harness over a GardenWorld-model seed on a scratch clone (`scripts/generate_post_oracle.sh` →
`build/erp/oracle/post_b3_fixture.json`; USER RULING 2026-07-17 sanctioned seed-INPUT prep). `derivePostings`
gained the 6 per-class manifests. Matrix ledger = **49 oracle-equivalent**. Log: `build/erp/poc_post_b3.log`.

### H-3 Spot-harden the declarative engines
`ad_evaluator`/`ad_access`/`ad_valrule`/`ad_reference` are 🟡 on parse; oracle-diff a SAMPLE of each against
`GridField`/`MRole`/`MValRule` outputs — confirm the verdict matches, not just that it parses. The master-data
tail needs little beyond this (it is AD_Column + the `ad_modelval` hook).

**✅ DONE (2026-06-10) — the 3 SQL-grounded declarative engines are ORACLE-EQUIVALENT.** Oracle = the live
iDempiere Postgres (docker `postgres`/`idempiere`, GardenWorld client 11 — the SAME seed `ad_full.db` was
extracted from). Diff harness pattern (reused across all 3): drive the engine to produce a membership/verdict
set over our SQLite, run the equivalent query on Postgres, diff the sets; a load-bearing §FALSIFIER each.
- **AD_Val_Rule** (`scripts/poc_valrule_harden.js` → `build/erp/poc_valrule_harden.log`) — 10/10 rules, every
  token-substituted where-clause's row-membership == Postgres, **diff=0**; §FALSIFIER (flipped operator) diff=8.
- **AD_Ref_Table** (`scripts/poc_reference_harden.js` → `…poc_reference_harden.log`) — 12/12 refs, FK resolution
  (fkTable,keyCol) + FULL keyCol id-set (incl. 26,519-row ad_column) == Postgres, **diff=0**; §FALSIFIER diff=27125.
- **AD role/access (MRole)** (`scripts/poc_access_harden.js` → `…poc_access_harden.log`) — 5 roles × {win,proc,form}
  = 15/15 access maps == `MRole.getXxxAccess` SQL, **diff=0**; `canView` switch == bitmask-intersection over 42
  combos, 0 mismatch; §FALSIFIER (+1 bogus grant) diff=1.
- **AD_Column.Callout derive** (`scripts/poc_callout_harden.js` → `…poc_callout_harden.log`) — over the full 27
  c_orderline population vs Postgres stored: `CalloutOrder.product` PriceActual/PriceList == price-list **27/27**;
  `CalloutOrder.amt` LineNetAmt=round(price×qty) == stored **26/27** (1 NAMED residual: line 119 price-drift, the
  doc-109 pattern, contract intact); §FALSIFIER corrupt-qty diverges. Matrix bumped 16→**20** oracle-equivalent.
- **⛔ REMAINING: NONE (closed 2026-06-12).** `ad_evaluator` FELL to B-1 (W-LOGIC-HARDEN, 2751 fixtures diff=0
  vs real compiled SimpleBooleanParser+EvaluationVisitor — `prompts/ERP_EXECUTION_ROADMAP.md` B-1). `ad_workflow`
  FELL to B-2 (W-WF-HARDEN, `scripts/poc_wf_harden.js` → `build/erp/poc_wf_harden.log`: 11 REAL PG traces
  `§HARDEN surface=ad_workflow fixtures=11 diff=0 oracle=iDempiere-PG-trace` + real compiled StateEngine mutators
  + DocAction std-user gate via `scripts/logic_oracle/WorkflowOracle.java` — the LogicOracle technique one level
  up; small K named in §HARDEN-SKIPS). (`ad_modelval` + `ad_docfsm` FELL 2026-06-11 to the
  H-1/H-2 source-parse + stored-state oracle pattern — see the model-layer rows in the matrix; no longer ⛔.)

## HONEST RESIDUALS (name, don't fake)
- The 3⛔ are n/a-in-seed (empty `fact_acct`/`fact_reconciliation`, empty `*_Access`) — **no oracle exists**; mark
  Oracle = n/a, never synthesize one.
- Where a fixture can't be captured from this checkout, log the gap; a missing oracle is an honest ⬜, not a ✅.
- "Mechanism not corpus" still holds: prove the harness + MOrder + the ~5 deep deltas; the long tail of trade-pattern
  isomorphs is a delta-diff each, named as remaining count — don't claim them un-diffed.

## THE FORK (decided for this arc, stated so it's deliberate)
This card CHOOSES equivalence = product-grade rigour. If the goal later flips to **the paper**, the very same
captured fixtures become the paper's equivalence evidence ("engine == iDempiere, maxDiff=0c on K real documents") —
so the work is not wasted either way. Don't silently expand into re-implementing iDempiere; harden what's claimed.

## DELIVERABLE / STOP CONDITION
Matrix gains the Oracle column + the "N of 40 oracle-equivalent" tally; the MOrder archetype table is GREEN by
diff; the delta table carries real diff results; the harness is reusable. Each surface hardened has a `§HARDEN …
diff=0` line (read the log). Unstarted surfaces stay ⬜. If a surface needs a user decision that can't be EXTRACTED
→ `⛔ BLOCKED: <the one question>` and move on. Keep the separation seams intact; UI bridge stays parked.
