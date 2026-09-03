# ⚠ DO NOT REMOVE — FABLE 5 LANE / CARD 0: WHOLE-PROJECT ADVERSARIAL REVIEW (runs FIRST, gates H-1)
# WHO THIS IS FOR: a Fable 5 session ONLY, run BEFORE `prompts/FABLE5_MORDER_EQUIVALENCE.md`. This is the step-back
#   evaluation — hold the WHOLE project (codebase + ~60 memory lanes + the equivalence arc) in the 1M window and
#   reason over it as a whole. Output a written verdict that either CONFIRMS H-1 MOrder as the right keystone, or
#   REDIRECTS. The H-1 execution card does NOT run until this card's verdict says so.
# WHY FABLE 5 (not Opus): breadth × depth × catch-the-non-obvious-cross-cutting-risk over the entire project at once
#   is where the higher ceiling shows. This is the one synthesis task big enough to justify the premium read.
# THIS IS A REVIEW, NOT A BUILD: READ + WRITE-ONE-DOC only. No code changes, no witnesses re-run except to SPOT-CHECK
#   a claim (e.g. re-run one poc to confirm a ✅ is real). Bounded: it produces `docs/ERP_PROJECT_REVIEW.md` + a
#   go/no-go on H-1, then STOPS.
# NON-NEGOTIABLE: ADVERSARIAL + EVIDENCE-CITED + NON-INVENT. This is a skeptic's audit, not a summary and NOT hype
#   ([[feedback_no_hype]] · [[feedback_erp_perf_claims]]). Every claim/finding cites a file:line, a witness log, a
#   commit, or a matrix row. "Looks done" is not evidence — READ the log. If you can't substantiate a doubt, say so;
#   if you can't substantiate a claim the project makes, that IS a finding. Run spot-checks via
#   `bash build/erp/run_witness.sh scripts/poc_X.js` (NOT tee).

# READ FIRST — the whole picture, in this order:
#   1. MEMORY.md (the lane index) + CLAUDE.md (the protocol/thesis)  — what the project CLAIMS to be.
#   2. docs/ERP.md (blueprint) + docs/IDEMPIERE_2.md + docs/HolyGrail.md  — the thesis and its logic-admission model.
#   3. docs/ERP_COVERAGE_MATRIX.md + docs/ERP_MODEL_ARCHETYPE.md + prompts/HARDEN_MATRIX.md  — the equivalence arc
#      and its scoreboard (0✅/39🟡/3⛔ + "N of 40 oracle-equivalent"). This is the claim most worth auditing.
#   4. docs/ERP_BACKEND_SEPARATION.md + the build/erp/*.js engine modules  — is the architecture as described?
#   5. Recent commits (git log --oneline -40) + the §OUTSTANDING backlog in prompts/FRONTEND_LANE_MASTER.md  — what is
#      actually moving vs parked vs dead.

---

# CARD 0 — The whole-project review

## The job (one sentence)
Produce `docs/ERP_PROJECT_REVIEW.md`: an evidence-cited, adversarial assessment of the ENTIRE project that ends in
an explicit verdict on whether H-1 MOrder→equivalence is the right next keystone for the Fable 5 lane.

## The six questions to answer (each a section in the output doc, each finding evidence-cited)
1. **Is the thesis sound?** Restate the central claim (ONE owned AD model + signed op-log; UI = cheap swappable lens;
   the engine folds ANY source → renderable iDempiere, *oracle-equivalent*). Where is it strongest? Where is it
   thinnest / most hand-wavy? Name the single weakest link most likely to invalidate it.
2. **Is the equivalence tally HONEST?** Spot-check ≥3 claimed ✅ oracle-equivalent rows against their actual witness
   logs (re-run one). Is 🟡 inflated — does "surface touched" quietly read as "works"? Are the 3⛔ genuinely
   n/a-in-seed, or quietly dodged? Report the honest count, not the claimed one.
3. **Drift & dead lanes.** ~60 memory lanes. Which are alive, which are done-but-not-buried, which CONTRADICT each
   other, where has the shipped repo drifted from the docs/specs? Flag the top few that cost the most confusion.
4. **Architecture / separation.** Are the §0 seams (AD-declaration / interpreter / log-fold never merge) actually
   held in build/erp, or violated? (Distinct from the Sonnet convention-audit, which is the mechanical seam/UMD/
   Date.now grep — THIS is the judgment: is the 3-layer model the right architecture, and is it holding under load?)
5. **Highest-leverage next move.** Given all the above: IS H-1 MOrder the right keystone, or is something else higher
   leverage (e.g. a different equivalence target, closing a drift, the UI bridge, the paper)? Argue it.
6. **Verdict.** One of: `GO H-1` (proceed to FABLE5_MORDER_EQUIVALENCE.md as written) · `GO H-1 WITH CORRECTIONS:
   <list>` · `REDIRECT: <the higher-leverage target + why>`. This line is the deliverable that gates the next session.

## DELIVERABLE / STOP CONDITION
`docs/ERP_PROJECT_REVIEW.md` written with the six sections, every finding citing evidence, ending in the verdict
line. No code changed. Then STOP — do not start H-1 (or any redirect) in this session; the verdict hands off to the
next Fable 5 session. If a question needs a user decision that can't be EXTRACTED → `⛔ BLOCKED: <one question>` in
the doc and answer the rest.
