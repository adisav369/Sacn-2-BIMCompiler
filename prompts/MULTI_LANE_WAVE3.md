# ⚠ DO NOT REMOVE — Scope guard / SESSION CARD: MULTI-LANE WAVE 3 (one session, multi-agent, user-authorized 2026-06-12)
# Paste-to-start: `use a workflow — proceed with prompts/MULTI_LANE_WAVE3.md`
# Scope: run the TWO queued lanes in parallel agents, then the (single-slot) deploy train, then ONE writer banks.
#   This card IS the user's multi-agent opt-in. Same DAG that worked in waves 1/2 (see prompts/MULTI_LANE_LAUNCH.md
#   # DONE appendices for the proven pattern + the traps it killed).
# READ THE LOG after every run (exit ≠ evidence). ALL poc_* via `bash build/erp/run_witness.sh scripts/poc_X.js`.
# HOUSE RULES the orchestration MUST encode (each has bitten):
#   - bim-ootb edits ONLY in a /tmp/wt-* worktree off FRESH origin/main; one worktree per lane.
#   - DEPLOYS SERIALIZE: one bim-ootb PR in flight; sw bump once per landing; after each squash VERIFY the diff
#     landed (`git show origin/main:<file>` — the #138/#265 orphan trap); Pages sw greps must be quote-agnostic.
#   - docs/ERP_COVERAGE_MATRIX.md + prompts/FRONTEND_LANE_MASTER.md have ONE writer (Phase 3); dev agents RETURN
#     re-verdict rows as data.
#   - Two bim-compiler lanes share one working tree → lane agents DO NOT git-commit; the Phase-3 writer commits
#     each lane's NAMED file list separately (tree is dirty — git add exact paths only, never -A).
#   - S274 GP.2 goto-timeout e2e flake on ERP-only diffs → rerun the failed job once.
#   - MODEL SPLIT (the point of this card): Lane A = Fable 5 (deep semantics judgment + 1M context); Lane B =
#     Sonnet/Opus (build + wiring). Do not swap them.

---

## LANE A — B-2 workflow oracle (Fable 5 · bim-compiler ONLY · headless · NO deploy)
Sub-agent prompt: **execute `prompts/FABLE5_WORKFLOW_ORACLE.md` verbatim, top to bottom** (model: fable/opus-tier).
The card is self-contained: entrance facts pre-verified (live PG `idempiere_test` carries REAL traces —
ad_wf_process=11 / ad_wf_activity=13 / ad_wf_eventaudit=13), technique = the B-1 LogicOracle headless-compiled-
classes precedent, witness W-WF-HARDEN with falsifiers + named skips. EXCEPTION to that card's §W-5: do NOT edit
the matrix/lane-master yourself — RETURN the re-verdict rows + # DONE lines as data (this wave's single-writer
rule supersedes; the card was written for a solo session). Closes the equivalence ledger: 42 → 43, ⬜ = none.
RETURN: witness §-lines verbatim · matrix re-verdict rows · # DONE appendix text for the card · uncommitted
bim-compiler file list · residuals/skips.

## LANE B — POS full loop (Sonnet/Opus · bim-compiler engine glue + bim-ootb worktree · deploys via the train)
Sub-agent prompt: **execute `prompts/POS_FULL_LOOP.md` verbatim** — §L-1 CRUD-on-POS-docs witness · §L-2 void/
reverse live (postings net 0c, on-hand restored) · §L-3 replenishment ENACTED to closure (suggest → PO CO →
receipt CO → on-hand +N to the unit → suggestion clears) — but STOP before its §L-4 deploy: commit on a branch
in /tmp/wt-posloop (NO PR, NO sw.js), the train does §L-4. newVerbs=[] is the gate; vendor/price from real seed
rows or an honest refusal. RETURN: branch · witness §-lines verbatim · matrix row deltas · deployNeeds (sw/?v=/
precache) · uncommitted bim-compiler files · residuals.

## PHASE 2 — deploy train (serial; Lane B only)
Fetch fresh origin/main (it moved: #272 landed sw v654) → rebase Lane B's branch → bump erp/sw.js (+?v=) with a
changelog line → re-run Lane B's live witnesses against the bumped worktree (ERP_ROOT pattern, read the logs) →
push → PR → `gh pr merge --auto --squash` → WAIT merged → orphan check → Pages live-verify (version + one
§-behavior probe).

## PHASE 3 — single-writer bank (one agent, after the train)
In bim-compiler: commit Lane A's files, then Lane B's bim-compiler files (separate commits, exact paths) ·
matrix: ad_workflow ⬜→✅ + ledger 42→43 headline + POS-loop row evidence · prompts/ERP_EXECUTION_ROADMAP.md B-2 ✅ ·
prompts/HARDEN_MATRIX.md remaining-⛔ line · # DONE appendices into FABLE5_WORKFLOW_ORACLE.md + POS_FULL_LOOP.md +
this card · FRONTEND_LANE_MASTER §OUTSTANDING handoff (mirror the 06-12b block) · PROGRESS §Current State ·
push the working branch. Every claim = a returned § line (Watchdog: no § line = not done).

## DONE WHEN
Lane A: W-WF-HARDEN green, ledger 43, ⬜=none, banked. Lane B: loop closed to the unit/cent, live-verified on
Pages, banked. Report = ✅ list + ⛔ questions (WORK-TO-ZERO). Anything user-blocked → `⛔ BLOCKED: <one question>`.

## NEXT AFTER THIS WAVE (not this session)
B-3 0-seed posting oracles (the headless-classes technique generating real posted BankTransfer/DepositBatch/
ProjectIssue/FA — its own Fable-5 card) · Phase C UI wiring C-1..C-4 (Sonnet) · POS next-increments
(returns-with-restock UI / §P-5 multi-station / receipt-URL / EOD email).

# DONE — 2026-06-12, WAVE 3 banked (Phase-3 single writer). Both lanes ✅, train merged-verified, 0 ⛔.

- **Lane A (Fable 5, FABLE5_WORKFLOW_ORACLE)** ✅ — B-2 workflow oracle: `§HARDEN surface=ad_workflow fixtures=11 diff=0 oracle=iDempiere-PG-trace` (11 REAL ad_wf_process / 13 activities / 13 eventaudits from live `idempiere_test`, captured verbatim → `build/erp/oracle/wf_oracle.json`); defs md5-set `§HARDEN-SRC kind=wf setdiff=0` (58/262/207/1); compiled-classes semantics arm `§HARDEN-STATE 6/6` + `§HARDEN-GATE diff=0` (`scripts/logic_oracle/WorkflowOracle.java`); 2 load-bearing §FALSIFIERs; 7 §HARDEN-SKIPS named (claim = 11 real processes diff=0, NOT corpus-wide). **Ledger 42→43, ⬜=NONE.** Log `build/erp/poc_wf_harden.log`; W-WF regression intact. Banked: bim-compiler f941f073.
- **Lane B (POS_FULL_LOOP)** ✅ — §L-1 CRUD `§POS-CRUD edit=description … statusOp=none verifyChain=ok` · §L-2 void `§POS-VOID postings-net=0c` + `onhand-restored=Y` + double-VO refused · §L-3 loop ENACTED `§POS-LOOP suggest qty=11 product=124` → vendor 114 (real m_product_po) → `po=CO` → `receipt=CO` → `onhand before=9 after=20` → `suggestions … cleared=Y`, newVerbs=[]. Banked: bim-compiler 23ae7807.
- **Train** ✅ — bim-ootb **PR #274** squash-merged (CI SUCCESS), **sw v655**, orphan check `origin/main:erp/sw.js = v655`, Pages live-verify v655 + pos_lens.js vendorOf/buildReplenishPO §-probe; post-merge W-POS-LIVE `§POS-CENT … maxDiff=0c` all 5 stages green. Rebase over #273 clean (viewer sw.js, no erp/sw.js conflict).
- **Phase 3 bank** ✅ — matrix: ⬜ row → B-2 ✅ row, headline FORTY-THREE + ⬜=NONE, POS §L-1..§L-3 addon rows · ERP_EXECUTION_ROADMAP B-2 ✅ tally 43 + HARDEN_MATRIX ⛔-REMAINING=NONE (Lane A on-disk edits verified, gitignored) · FABLE5_WORKFLOW_ORACLE + POS_FULL_LOOP # DONE appendices · FRONTEND_LANE_MASTER §OUTSTANDING handoff · PROGRESS §Current State.
- **NEXT (named sequel, not opened)** — B-3 0-seed posting oracles · Phase C UI wiring C-1..C-4 · POS next-increments (returns-with-restock / §P-5 multi-station / receipt-URL / EOD email). AD_Rule stays ⛔ n/a-in-seed (fact_reconciliation=0 re-verified).
