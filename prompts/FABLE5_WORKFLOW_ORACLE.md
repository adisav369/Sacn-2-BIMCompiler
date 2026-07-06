# ⚠ DO NOT REMOVE — Scope guard / FABLE-5 KEYSTONE CARD: B-2 workflow oracle — close the LAST ⬜
# Scope: oracle-diff `build/erp/ad_workflow.js` (the node-walk engine, W-WF) against REAL iDempiere workflow
#   traces + the REAL compiled workflow classes run headless. This is roadmap B-2 (`prompts/ERP_EXECUTION_ROADMAP.md`)
#   — the ONLY remaining ⬜ in the equivalence ledger (42 oracle-equivalent; ad_workflow closes it → 43, ⬜=none).
# MODEL LANE: Fable 5 (deep reasoning + 1M context: org.compiere.wf Java + ad_full.db wf tables + captured
#   traces + our engine held at once). Headless, bim-compiler ONLY — NO bim-ootb, NO deploy, NO sw.
# READ THE LOG after every run (exit ≠ evidence). ALL poc_* via `bash build/erp/run_witness.sh scripts/poc_X.js`.
# NON-NEGOTIABLE: EXTRACT, DON'T INVENT — the oracle is the live PG's own trace + the real compiled classes;
#   a verdict you cannot ground is an HONEST SKIP (named, counted), never a fixture. NEVER synthesize activities
#   (the old B-2 ⛔ wording) — you don't need to: real traces exist (verified below). Spec-first; §FALSIFIER
#   load-bearing; deterministic op paths. docs/ERP_BACKEND_SEPARATION.md seams stay intact.
# READ FIRST (in order):
#   1. prompts/ERP_EXECUTION_ROADMAP.md §PHASE B B-2 + § DONE tally (B-1 ✅ 2026-06-12 is the direct precedent).
#   2. scripts/poc_logic_harden.js + scripts/logic_oracle/LogicOracle.java — THE TECHNIQUE THIS CARD GENERALIZES:
#      B-1 drove the REAL compiled SimpleBooleanParser+EvaluationVisitor headless (minus only the CLogger static,
#      omission named in the header) over live-PG record context → 2751 fixtures diff=0. Same move, one level up.
#   3. build/erp/ad_workflow.js — the engine under test (A-6 node-walk, W-WF, `build/erp/poc_wf.log`).
#   4. iDempiere source ~/idempiere-dev-setup/idempiere — org.compiere.wf: MWorkflow / MWFProcess / MWFActivity /
#      MWFNode / MWFNodeNext / MWFEventAudit + org.compiere.process.StateEngine (the state machine the walk must
#      reproduce). Compiled classes: org.adempiere.base/target/classes (same path B-1 used; ANTLR jar precedent
#      in LogicOracle's build line).
#   5. prompts/HARDEN_MATRIX.md — what "hardened" means (oracle DIFF, not a claim; Oracle column re-verdict rules).

---

## FACTS (verified live 2026-06-12 — do not re-derive)
- Docker container `postgres`, db **`idempiere_test`** (GardenWorld client 11 — the same instance every §H-3/B-1
  harden diffed against): **`ad_wf_process` = 11 · `ad_wf_activity` = 13 · `ad_wf_eventaudit` = 13** — REAL traces
  written by real iDempiere when GardenWorld documents were processed. The old entrance blocker is GONE.
- `fact_reconciliation` = 0 in the same db → AD_Rule stays honestly ⛔ n/a-in-seed. OUT OF SCOPE — do not chase it.
- The seed (`ad_full.db` / ad_seed) carries the workflow DEFINITIONS (ad_workflow / ad_wf_node / ad_wf_nodenext —
  what A-6 walks); the live PG additionally carries the RUNTIME trace tables. Definitions diff = md5-set check
  (the B-1 §HARDEN-SRC pattern); runtime trace = the new oracle.
- Corpus is SMALL (11 processes / 13 activities). State the K honestly everywhere — "11 real processes, diff=0"
  is the claim; node types the trace never exercises are NAMED SKIPS, not wins.

## THE WORK
### §W-1 Capture the oracle (extend the proven extract pattern — never hand-author)
Pull from `idempiere_test`, versioned fixtures (e.g. `build/erp/oracle/wf_oracle.json` or a small sqlite):
`ad_workflow`+`ad_wf_node`+`ad_wf_nodenext`+`ad_wf_nextcondition` (definitions, INT-typed) and
`ad_wf_process`+`ad_wf_activity`+`ad_wf_eventaudit` (the trace: per process — node sequence, WFState transitions,
timestamps for ORDER only, the document each rode). Also capture the referenced doc rows' relevant columns (the
condition context). Definitions md5-set vs our `ad_full.db` (`§HARDEN-SRC kind=wf … setdiff=0` — the B-1 line).

### §W-2 Diff the node-walk (the core)
For each of the 11 captured processes: replay `ad_workflow.js` over the SAME workflow definition + the SAME
document context → assert (a) identical node SEQUENCE as `ad_wf_eventaudit`, (b) identical transitions taken
(MWFNodeNext conditions resolve the same way), (c) identical terminal WFState per `StateEngine` semantics
(`ad_wf_process.wfstate` / activity states). Any divergence = a NAMED finding (engine bug or semantics gap) —
report, don't paper over. `§HARDEN surface=ad_workflow fixtures=11 diff=0 oracle=iDempiere-PG-trace`.

### §W-3 The semantics arm (the Fable-5 judgment call — only as far as honesty requires)
Where (b) needs the REAL evaluator (transition conditions, split-AND/join, StateEngine legal-transition table):
`scripts/logic_oracle/WorkflowOracle.java` mirroring LogicOracle.java — drive the real compiled
StateEngine/MWFNodeNext condition evaluation headless. Name every omitted static (CLogger-class drags) in the
header, exactly as LogicOracle does. If a class genuinely cannot run headless without forking semantics, that
sub-surface is an honest skip with the reason — the trace diff (§W-2) still stands on its own.

### §W-4 Witness + falsifiers
`scripts/poc_wf_harden.js` → W-WF-HARDEN via run_witness.sh → `build/erp/poc_wf_harden.log`, exit 0 AND read:
- `§HARDEN-SRC` definition md5-sets setdiff=0 · `§HARDEN surface=ad_workflow fixtures=11 diff=0` (+ per-process lines)
- `§HARDEN-FALSIFIER` at least two, load-bearing: (1) flip ONE captured condition/context value → the walk takes a
  different transition on BOTH sides (engine==oracle, both diverge from the unflipped walk); (2) drop a node from
  the definition → replay FAILS loudly, never silently skips.
- `§HARDEN-SKIPS` — every node type / WF feature the 13 activities never exercise, named + counted.

### §W-5 Bank (same session — this card is its own single writer)
- `docs/ERP_COVERAGE_MATRIX.md`: Oracle column `ad_workflow` ⬜→✅ row (full evidence format, cite the log);
  the ⬜ "Remaining declarative surfaces" line → **NONE remaining**; ledger headline **42 → 43 oracle-equivalent**.
- `prompts/ERP_EXECUTION_ROADMAP.md` B-2 → ✅ DONE (witness + log path). `prompts/HARDEN_MATRIX.md` §H-3
  remaining-⛔ line → updated. `prompts/FRONTEND_LANE_MASTER.md` §OUTSTANDING handoff block (mirror the
  06-12b format). PROGRESS.md §Current State. This card's `# DONE` appendix — every claim = a § line (Watchdog).
- Commit bim-compiler (witness + oracle fixtures + WorkflowOracle.java + bank docs; prompts/ is gitignored except
  FRONTEND_LANE_MASTER — card edits live on disk, note it in the commit message). Push the working branch.

## STOP CONDITION / HONESTY RAILS
- DONE = ledger 43, ⬜=none, W-WF-HARDEN green with falsifiers + named skips; or the walk diverges → the finding
  itself is the deliverable (a real engine gap beats a fake green). A step needing an un-EXTRACTABLE user fact →
  `⛔ BLOCKED: <the one question>`, move on.
- Do NOT expand into B-3 (0-seed posting oracles) here — it is the NAMED SEQUEL (same headless-compiled-classes
  technique pointed at generating real posted BankTransfer/DepositBatch/ProjectIssue/FA docs on a scratch PG copy);
  it gets its own card after this one banks.

# DONE — 2026-06-12, Fable 5 lane (MULTI_LANE_WAVE3 Lane A). Every claim = a § line in
# `build/erp/poc_wf_harden.log` (exit 0, log READ, 0 FINDING). Run: `bash build/erp/run_witness.sh scripts/poc_wf_harden.js`.

- **§W-1 oracle captured, never hand-authored** — `§HARDEN-CAPTURE fixtures=11 processes (13 activities, 13 eventaudits) → build/erp/oracle/wf_oracle.json` (versioned); entrance re-verified live: `§HARDEN-ORACLE … ad_wf_process=11 ad_wf_activity=13 ad_wf_eventaudit=13` (postgres/idempiere_test).
- **Definitions identical both schemas** — `§HARDEN-SRC kind=wf setdiff=0` (wf_def 58 + wf_node 262 + wf_nodenext 207 + wf_nextcond 1, each `setdiff=0` — the B-1 md5-set pattern).
- **Document context EXTRACTED** — `§HARDEN-CTX C_Order at-start defaults EXTRACTED: ad_full.db DocStatus=DR/DocAction=CO · live PG DocStatus=DR/DocAction=CO` (AD_Column defaultvalues, identical both schemas); traced doc current row `§HARDEN-CTX traced doc c_order=200002 … docstatus=CO`.
- **§W-2 THE DIFF** — `§HARDEN surface=ad_workflow fixtures=11 diff=0 oracle=iDempiere-PG-trace`: all 11 `§HARDEN-PROC … diff=0` lines — 10× wf131 BP-Approval `path=[244] states=[OS] events=[SC] terminal=OS trace=OS` (UserWindow suspend) + 1× wf116 Process_Order `path=[183→185→186] states=[CC,CC,CC] events=[PX,PX,PX] terminal=CC trace=CC` (std-user transition taken, XOR split, seqno order). Threaded doc anchor: `§HARDEN-DOC replayed C_Order docstatus=CO == live c_order(200002).docstatus=CO`.
- **§W-3 semantics arm = REAL compiled classes** (`scripts/logic_oracle/WorkflowOracle.java`, LogicOracle technique; omissions named in its header): `§HARDEN-STATE hops=3 (engine) + 3 illegal probes — compiled StateEngine setState agrees on 6/6` (REAL mutators, not just the legal table — probed: CLogger loads headless here, so NO omission needed on the STATE arm) + `§HARDEN-GATE stduser-gate verdicts=1 diff=0 oracle=compiled-DocAction-constants` (isValidFor:215-243 verbatim). MWFNodeNext.isValidFor-compiled + MWFNextCondition.evaluate = named omissions (drag PO/Env/DB).
- **§W-4 falsifiers, load-bearing** — `§HARDEN-FALSIFIER flip DocAction CO→-- : engine base=[183→185→186] flipped=[183→184] · oracle first-valid base=183→185 flipped=183→184` (BOTH sides take the OTHER transition) + `§HARDEN-FALSIFIER drop node=185 : replay abort=node-missing:185 terminal=CA … diff FLAGS it=Y` (LOUD, per MWFActivity.run:948-952).
- **Named skips (small-K honesty)** — 7 `§HARDEN-SKIPS` lines: actions exercised {Z,D,W} of 8 seed types (F/X/P/R/C unexercised — replay THROWS on them); 1 conditioned transition (nodenext 100, wf 115, untraced); AND-split/join absent (262/262 X/X); transitioncode 0 non-null; 56/58 workflows untraced; StateEngine abort/terminate/resume only via compiled probes; claim = "11 real processes, diff=0", NOT corpus-wide.
- **Engine change** — `build/erp/ad_workflow.js` gained the `replay` arm (MWF*/StateEngine line-cited port: std-user gate, first-valid-by-seqno XOR routing per MWFNode.loadNext:270, ON→OR→CC/OS activity states, PX/SC event types, checkCloseActivities process aggregation, docstatus threading via `ad_docfsm.transition`). Old API untouched — W-WF regression green (`bash build/erp/run_witness.sh scripts/poc_wf.js` exit 0, 0 🔴, `§WF_COVERAGE workflows=58 nodes=262 nexts=207` intact).
- **§W-5 banked (wave exception honoured)** — `prompts/ERP_EXECUTION_ROADMAP.md` B-2 → ✅ + tally 42→**43, ⬜=NONE**; `prompts/HARDEN_MATRIX.md` §H-3 remaining-⛔ → NONE (all gitignored, on-disk). `docs/ERP_COVERAGE_MATRIX.md` + `prompts/FRONTEND_LANE_MASTER.md` NOT touched (single Phase-3 writer owns them — re-verdict rows returned as data). NOTHING committed (shared dirty tree); files to add: `build/erp/ad_workflow.js` · `scripts/poc_wf_harden.js` · `scripts/logic_oracle/WorkflowOracle.java` · `build/erp/oracle/wf_oracle.json`.
- **Residual / sequel** — B-3 (0-seed posting oracles) stays the NAMED SEQUEL, not opened here; AD_Rule stays ⛔ n/a-in-seed (`fact_reconciliation=0` re-verified).
