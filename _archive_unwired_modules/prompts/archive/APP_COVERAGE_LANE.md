# ⚠ DO NOT REMOVE — Scope guard
# Lane: CLOSE the highest-leverage ⛔/🟡 rows in docs/ERP_COVERAGE_MATRIX.md — move the browser ERP engine from
#       "static demo slice" toward real AD-driven behaviour. THREE sequenced builds, evaluator FIRST (one build
#       flips 7 surfaces). EXTRACT, DON'T INVENT: the grammar comes from iDempiere's Evaluator.java, the
#       expressions/counts from build/erp/ad_full.db (927 tables, lowercase) — never hand-author a rule that the
#       AD already defines. Spec before code; witness-led (whitebox §-log first, per docs/TestArchitecture.md);
#       read the LOG after every run (exit code is NOT evidence). Honour this block until each lane has a witness
#       and its matrix rows are re-verdicted.
# Read first: docs/ERP_COVERAGE_MATRIX.md (the gap list + the smallest-witness column — this prompt IS its top
#       ranked gaps) · docs/IDEMPIERE_2.md#the-logic-admission-model (which layers reduce to data) · the engine
#       it extends: build/erp/crud_overlay.js (validateField/defaultsFor/buildOp, descriptor-driven from crud_ops.json).
# Why now: the coverage audit (§M-1) scored 0 COVERED / 12 PARTIAL / 28 GAP of 40 surfaces. The engine has NO
#       logic-expression evaluator and NO security layer at all — the two pillars between "demo" and "ERP". The
#       evaluator is the single highest-leverage build: ONE Evaluator unblocks 7 matrix rows / ~3,000 AD rows.

---

# App-coverage lane — evaluator → security → SvrProcess (close the matrix gaps)

## LANE 1 — AD logic-expression evaluator  (W-LOGIC-EVAL)  ⭐ DO THIS FIRST

**Issue it settles.** 7 matrix surfaces are ⛔/🟡 for one root cause: the engine evaluates no AD logic expression.
A single evaluator (iDempiere centralises this in `org.compiere.util.Evaluator`, 197 LOC, consumed by
`GridField`) unblocks them all:

| matrix surface | AD count | today |
|---|---|---|
| AD_Field · DisplayLogic | 2588 | ⛔ |
| AD_Column · ReadOnlyLogic | 289 | ⛔ |
| AD_Field · ReadOnlyLogic | 52 | ⛔ |
| AD_Tab · ReadOnlyLogic | 42 | ⛔ |
| AD_Tab · DisplayLogic | 35 | ⛔ |
| AD_Column · MandatoryLogic | 29 | ⛔ |
| AD_Field · MandatoryLogic | 14 | ⛔ |

**Spec (grammar = EXTRACT from Evaluator.java, do NOT invent).** The AD logic-expression grammar:
`@ColumnName@=value`, `@#GlobalCtx@` / `@$Accounting@` context refs, comparison ops `= ! < > ^` (and `~` not-null),
boolean `&` / `|`, parens, `|literal|` quoting, negation. Evaluate against `(record, context)` → boolean.
**NON-NEGOTIABLE:** parse + evaluate the SAME grammar iDempiere does (cite Evaluator.java line ranges in the
header); feed it the REAL `displaylogic`/`readonlylogic`/`mandatorylogic` strings from `ad_full.db` (and the
shipping `~/bim-ootb/erp/ad_seed.db`), NOT new hand-authored strings.

**Build.** `build/erp/ad_evaluator.js` (the parser/evaluator, self-contained, no kernel dependency). Wire
`crud_overlay.js` to call it: a field's visible/readonly/mandatory state = evaluate its AD logic against the
current record + context, replacing the flat `f.readonly`/`f.required` booleans for fields that carry a logic
string. Keep the flat path as the fallback when a field has no logic.

**Witness (whitebox §-log first).**
- `§LOGIC_EVAL attr=<display|readonly|mandatory> expr="@DocStatus@!DR" ctx={DocStatus:CO} result=true` — one line
  per evaluated field; prove the engine reaches the right verdict on a SAMPLE of real AD expressions (≥1 per
  operator class: equality, not-equal, context-ref, AND, OR, negation).
- `§LOGIC_COVERAGE evaluated=<n> of <total-with-logic>` — count how many of the 2588+289+… AD rows the evaluator
  parses without error (target: 100% parse; verdict per-row display/readonly/mandatory correct on the sample).
- `§FALSIFIER` — toggle one context var (e.g. DocStatus DR→CO) → a field's verdict FLIPS (show/hide, ro/rw). A
  static engine cannot produce this; the flip proves the expression is genuinely interpreted, not a relabel.
- **Re-verdict the 7 matrix rows** ⛔→✅ (or 🟡 with the named residual) in docs/ERP_COVERAGE_MATRIX.md, citing
  the §-log. This is the deliverable: the matrix headline moves from 0✅ toward 7✅.

## LANE 2 — Security layer  (W-ACCESS)  — after Lane 1

**Issue.** ~4,200 AD rows of access control, all ⛔: `AccessLevel` (1076 tables), `AD_Role` (5),
`AD_Window/Process/Form/Column/Record_Access` (1303/1309/145/0/0), `AD_EntityType` (12). The engine has NO role
check. This is the SUBSTRATE-adjacent twin of the hardening lane's op-level authz (`prompts/SERVERLESS_HARDENING_RESUME.md`
§H-? / `poc_distributed.js` owner-gating) — keep them consistent.
**Spec.** A role context `{AD_Role_ID, orgs[], clients[]}`; gate window/process/form visibility + record-org scope
against the `*_Access` rows + `AccessLevel` bits (org/client/system). EXTRACT the access rows from ad_full.db.
**Witness.** `§ACCESS_DENY window=<id> role=<n> reason=<no-grant|wrong-org>` and the dual `§ACCESS_OK …`; falsifier
= a restricted role sees a window it lacks a grant for → must DENY. Re-verdict the 6 security rows.

## LANE 3 — SvrProcess runner  (W-PROC)  — after Lane 2

**Issue.** 476 `AD_Process` (337 with a Classname) / 54k LOC, ⛔ — no classname dispatch / process runner.
**Spec (scoped).** Do NOT port 476 Java classes. Build the DISPATCH spine: read `AD_Process` + `AD_Process_Para`,
resolve a small registry of JS-implemented handlers (start with the report/fold ones already in `report_overlay.js`
+ 2–3 doc-action procs), run with validated params, log the run. The deliverable is the *mechanism* (AD-row →
handler → result), not corpus completeness; name the unported remainder honestly.
**Witness.** `§PROC run AD_Process_ID=<n> classname=<X> params=<k> rows=<n>`; falsifier = an unregistered
classname → explicit "absent handler" (not a silent no-op). Re-verdict AD_Process 🟡→ (partial+, with the count
of dispatched vs total).

## METHOD (all lanes)
1. Spec section in this file before code (cite the iDempiere source + the AD query).
2. `// Implementing ERP_COVERAGE_MATRIX.md §<surface> — Witness: W-<NAME>` pre-flight citation in the code.
3. Whitebox `§`-log is the proof; Playwright only for wiring (script loads, button exists). Read the log.
4. Close the loop: re-verdict the matrix rows + update the headline tally. A lane isn't done until the matrix moves.

## STOP CONDITION
Lane 1 lands W-LOGIC-EVAL and 7 matrix rows move off ⛔. Then Lanes 2–3 as budget allows. Session end = each
started lane has a witness + re-verdicted matrix rows; unstarted lanes stay named here. If a lane needs a user
decision that can't be extracted, mark `⛔ BLOCKED: <the one question>` and move on.

---

# DONE — all three lanes closed (2026-06-09, each 🟢 ALL PASS exit 0, pushed `feat/erp-substrate-phase012`)
- ✅ **Lane 1 W-LOGIC-EVAL** — `build/erp/ad_evaluator.js` (grammar from Evaluator.java→LogicEvaluator→SimpleBoolean.g4)
  + `crud_overlay.effectiveFlags`; `scripts/poc_logic_eval.js` §LOGIC_COVERAGE 3044/3044 parse, §FALSIFIER flips. 7 rows ⛔→🟡. (`e34c700e`)
- ✅ **Lane 2 W-ACCESS** — `build/erp/ad_access.js` (MRole getWindow/Process/FormAccess + canView/AccessLevel) +
  `scripts/poc_access.js` §ACCESS_OK/DENY/COVERAGE(1303 win+1309 proc+145 form+6 lvl, 5 roles)/§FALSIFIER. 6 rows ⛔→🟡. (`3060aae8`)
- ✅ **Lane 3 W-PROC** — `build/erp/ad_process.js` (ad_process+para → classname → handler registry → validate → run) +
  `scripts/poc_proc.js` §PROC/§PROC_PARAM_VALIDATE/§PROC_COVERAGE(5 registered, 22/476 dispatched, 454 named-deferred)/§FALSIFIER. SvrProcess ⛔→🟡. (`fdabaa6f`)

**Matrix headline 0✅/12🟡/28⛔ → 0✅/26🟡/14⛔** (13 rows off ⛔). All ✅-by-headless, NOT live-UI — honestly 🟡.

**NEXT (different lane — do NOT do it here):** the 🟡→✅ live-render wiring is `prompts/AD_BEHAVIOR_HANDOFF.md`,
owned by the UI / pill session in **bim-ootb** (`erp/idempiere.html`), sequenced AFTER that lane's next sw bump.
Tier-2/SvrProcess-corpus stays named-deferred (mechanism proven; 454 classes unported by design).
