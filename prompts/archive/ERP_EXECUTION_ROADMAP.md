# ⚠ DO NOT REMOVE — Scope guard / EXECUTION ROADMAP: the ERP arc, sequenced for a fresh session
# Lane: this is the EXECUTION CARD over the status ledger `docs/ERP_COVERAGE_MATRIX.md` — open it, pick the
#       first un-entered phase step whose entrance criterion holds, execute, witness, re-verdict. One bounded
#       step per session. READ THE LOG after every run (exit code ≠ evidence); ALL poc_* runs via
#       `bash build/erp/run_witness.sh scripts/poc_X.js` — never tee to context. EXTRACT, DON'T INVENT:
#       fixtures/oracles from `build/erp/ad_full.db` + `~/idempiere-dev-setup/idempiere` + the live Docker PG;
#       a missing oracle is an honest ⬜/⛔, never a synthesized ✅. Keep `docs/ERP_BACKEND_SEPARATION.md` seams.
# READ FIRST: docs/ERP_COVERAGE_MATRIX.md (scoreboard) · docs/ERP_MODEL_ARCHETYPE.md (denominator) ·
#       prompts/HARDEN_MATRIX.md (equivalence discipline — the oracle-diff template every B-step reuses).

## § DONE (the standing tally — 2026-06-12)
Coverage **7✅/32🟡/3⛔ of 42** (AD_Process flipped ✅ 2026-06-11, W-AD-PROC-LIVE #267 v650) + equivalence ledger
**43 oracle-equivalent, ⬜=NONE** (41 at c204fc88 + B-1 logic evaluator W-LOGIC-HARDEN + B-2 workflow W-WF-HARDEN,
both 2026-06-12; HARDEN_MATRIX H-1+H-2+tail
DRAINED, no DocAction table unwalked) + reporting lane DRAINED (BS/IS/CF + PrintFormat `maxDiff=0c`, 527117eb) +
UI bridge B-1→B-4 LIVE (bim-ootb #264 sw v647: W-AD-{ACCESS,DISPLAYLOGIC,DOCFSM,MODELVAL-prefix,MENU-PRF}-LIVE).
Track A interpreters DONE (`prompts/ERP_BACKEND_GAP.md` ledger). **Fable-5 keystone cards are SPENT** — H-1/H-2
detail lives in `prompts/FABLE5_MORDER_EQUIVALENCE.md` / `FABLE5_H2_DELTAS.md` / `H2_ISOMORPH_TAIL.md` (# DONE
appendices); do NOT re-run them.

## § PHASE B — remaining hardening (equivalence axis; Sonnet/Opus; one step = one session)
B-1 **Logic-evaluator oracle-diff** — ✅ DONE 2026-06-12 (**W-LOGIC-HARDEN**, `scripts/poc_logic_harden.js` →
    `build/erp/poc_logic_harden.log` exit 0, 0 FINDING): `§HARDEN surface=ad_evaluator fixtures=2751 diff=0
    oracle=iDempiere-PG verdicts{T=985,F=1766} oracle_errors=0` (display 2211 + readonly 463 + mandatory 77) vs the
    REAL compiled SimpleBooleanParser+EvaluationVisitor driven headless by `scripts/logic_oracle/LogicOracle.java`
    over live-PG record context; expr md5-sets ad_full.db==PG (754/124/25, setdiff=0); `§HARDEN-FALSIFIER` flips
    both sides. Honest skips: 3 `@SQL=` · 542 window/login-ctx · 32 no-pk · 236 zero-row. Matrix Oracle column
    re-verdicted (ledger 41→42; ⬜ now = ad_workflow only).
B-2 **Workflow oracle** — ✅ DONE 2026-06-12 (**W-WF-HARDEN**, `scripts/poc_wf_harden.js` →
    `build/erp/poc_wf_harden.log` exit 0, 0 FINDING): all 11 REAL PG traces (idempiere_test) replay EXACTLY —
    `§HARDEN surface=ad_workflow fixtures=11 diff=0 oracle=iDempiere-PG-trace` (sequence + transitions +
    activity/process WFStates + eventtypes; 10× wf131 BP-Approval suspend-at-UserWindow OS/SC + 1× wf116
    Process_Order 183→185→186 CC/PX, threaded docstatus ends CO == live row). Definitions md5-set
    `§HARDEN-SRC kind=wf setdiff=0` (58 wf/262 node/207 next/1 cond). Semantics arm
    `scripts/logic_oracle/WorkflowOracle.java` (LogicOracle technique): REAL compiled StateEngine mutators
    6/6 hops+illegal-probes, std-user gate == verbatim isValidFor over compiled DocAction constants. Oracle
    fixture versioned `build/erp/oracle/wf_oracle.json`. 2 `§HARDEN-FALSIFIER` (flip DocAction CO→'--' reroutes
    183→184 BOTH sides; dropped node 185 → LOUD CA abort) + 7 `§HARDEN-SKIPS` lines (K=11 honesty: actions
    F/X/P/R/C unexercised, 1 conditioned transition untraced wf115, AND-split absent, 56/58 wfs untraced).
    **Ledger 42 → 43 oracle-equivalent; ⬜ = NONE.** New replay arm in `build/erp/ad_workflow.js` (W-WF
    regression poc_wf.js still green). Replay-on-engine + B-1 evaluator = the full declarative stack diffed.
B-3 **0-seed posting oracles (named in the isomorph tail).** Entrance: a seed/capture containing posted
    BankTransfer/DepositBatch/ProjectIssue/FA docs. Until then they stay source-parse-only ⛔ (honest).
B-4 **Track B substrate §H-7…§H-11** (`prompts/SERVERLESS_HARDENING_RESUME.md`): scheduled-jobs fold · signed
    period-lock anti-backdating · FX revaluation · SoD maker-checker (UNGATED since A-6) · signed-tip divergence
    heartbeat. Same §-witness+§FALSIFIER shape as §H-1…§H-6. Parallel axis — pick up when B-1..B-3 are blocked.

## § PHASE C — UI wiring continuations (live-UI axis; flips 🟡→✅; pattern = UI_UNPARK_RESUME.md # DONE)
Method per step: sync engine from `build/erp/` (diff first) → wire ONE seam → `§`-witness on localhost
(ERP_ROOT worktree serve, see scripts/poc_ad_docfsm_live.js) → on-screen verify → PR → sw bump → matrix re-verdict.
C-1 **ReadOnly/Mandatory logic DOM** — glassbowl `applyAdLogic` already disables/marks (`§AD-LOGIC-LIVE`); add the
    idempiere.html form arm + a live witness asserting input.disabled / the * marker → flips AD_Column·ReadOnlyLogic
    /MandatoryLogic + AD_Field twins (matrix rows 152/153/158/159).
C-2 **Tab WhereClause/OrderBy live** — idempiere `renderActiveTab` row query through `ad_tabquery.applyWhere/
    orderedKeys` (witness: the SO tab excludes PO order 104 in the live grid) → flips AD_Tab·Where/OrderBy.
    NOTE the M_InOut seed gap: all 4 M_InOut windows filter on a MovementType column ABSENT from ad_seed.db —
    a seed-regen item, name it, don't code around it.
C-3 **Val-rule + callout on live fields** — FK picker filtered by `ad_valrule` (§VALRULE row-set on screen) +
    field-change firing `ad_callout.dispatch` derives into sibling inputs → flips AD_Val_Rule + AD_Column·Callout.
C-4 **AccessLevel/EntityType live read-gate** — `gateRecord`/`entityTypeAllowed` into the record-open path
    (witness: wrong-org record blanked for GardenUser) → flips AccessLevel + AD_EntityType.
C-5 **B-5 process dispatch** — ✅ DONE 2026-06-11 (W-AD-PROC-LIVE, bim-ootb PR #267 sw v650): seed-gate fell
    (FULL-WIDTH seed PR #265) → P/R leaves + procSet-gated `?process=` deep link route through `AdProcess.dispatch`,
    param dialog + prepare gate live, honest absent-handler card. Residual ~~`fact_acct` seed-regen~~ ✅ CLOSED
    2026-06-12 (MIGRATE_POSTING_CONFIG, PR #271 sw v653): live `§AD-PROC-LIVE proc=310 "Trial Balance" ok=Y rows=21`.
C-6 **docstatus-select bug** — ✅ DONE 2026-06-11 (W-CRUD-DOCSTATUS, bim-ootb PR #268 sw v651): BOTH arms fixed via
    PURE CORE seams (CORE.listOptions renders the record's CURRENT value selected; CORE.splitStatusChange strips
    docstatus from CRUD_UPDATE — explicit change routes through DOC_ACTION SET_STATUS, requires-gated like Process ▶).
    `scripts/poc_crud_docstatus.js` + poc_crud_persist/poc_crud_group regressions green.

## § DEFERRED (out of scope, one-line reasons — do not re-open without the user)
454-classname SvrProcess corpus (mechanism proven, corpus infinite) · 13 remaining `T_*` folds (each needs its own
witness+seed) · F/I/T menu gating (seed ad_menu carries no workflow/task/info id column) · AD_Rule + 2 empty
`*_Access` (n/a-in-seed) · NinjaExcel (separate session, never feeds the matrix) · pixel-faithful PrintFormat
(stated non-goal) · group-by/avg report formats (no seed format exercises them).
