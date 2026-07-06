# ⚠ DO NOT REMOVE — Scope guard: FUNDAMENTALS WATCHDOG (overall dev-progress conformance review)
# Scope: a READ-ONLY audit. Review the OVERALL progress of the dev sessions/lanes and judge whether any of it is
#   BREAKING the project's fundamentals. Do NOT build, fix, or deploy — produce a conformance report and flag breaks.
#   Re-runnable every dev cycle (the standing Watchdog Protocol, CLAUDE.md), and after any coder task.
# NON-NEGOTIABLE: §-log FIRST (read the actual witness logs before any verdict — exit code/claim is NOT evidence) ·
#   evidence-led (every PASS/DRIFT/BREAK cites a file:line, a §-log line, or a commit) · non-invent (do not assume;
#   if you can't find evidence, the verdict is UNPROVEN, not PASS) · no code, no deploy.
# Read first: CLAUDE.md (PRIME RULE + Standing Rules + Watchdog Protocol + Sacred Files) · PROGRESS.md §Current State ·
#   prompts/FRONTEND_LANE_MASTER.md (the dev lane under review) · docs/LensFamily.md (the doctrine frame) +
#   docs/DistributedERP.md · the lens docs (POS/WMS/Social/Credit/Workforce/GuaranteedChannels) · prompts/POS_LENS_SESSION.md.

---

## ▶ WHY — what this session is for
The dev lanes are building fast (AD-gen, the engine seam, the lenses, the write-path). Speed is where fundamentals
quietly break. This session is the **conscience check**: does the work still obey the rules the whole project rests
on? It does NOT advance features — it certifies the features advanced honestly, or flags where they didn't.

## ▶ THE FUNDAMENTALS TO CHECK (each → verdict PASS / DRIFT / BREAK + evidence)

**A. Project prime rules (CLAUDE.md)**
1. **EXTRACT OR COMPILE ONLY — never invent.** No synthesized rows; `handAuthored=0`; no `Date.now`/`Math.random` in
   op/write paths; absent data → reported as a named gap, never filled with a guess. (Grep the new code; check the
   `§AD-GEN`/`§*` logs for invented values.)
2. **NEVER TOUCH PRODUCTION.** `deploy/live/*` untouched; all dev work in `deploy/dev/` only. `migration/*.sql` append-only.
3. **Three Concerns never merge** — WHAT (orders/products) / HOW (BOM/validation) / WHERE (output.db) stay separate.
4. **Spec-first · witness-led · §-log first · EXPLICIT GO before deploy.** Each new feature has a written spec section
   first, a witness that NAMES the issue it proves, a §-log line proving the claim, and nothing deployed without GO.

**B. The doctrine fundamentals (the lens-family frame, this session's docs)**
5. **Fold-not-fork.** Every lens/feature consumes the seam and the SAME verbs — it does NOT add a new verb or its own
   persistence/state. *The test:* does it write through the shared fold, or hold its own truth? Own state = a FORK =
   BREAK. (Check `newVerbs=[]` across folds; check no lens keeps a private store.)
6. **No reinvention — the horizontal is already inside.** Every lens field traces to an existing AD column
   (`C_BPartner`/`C_Order`/`M_Locator`/`R_Request`…); no new schema invented per use-case (`build/erp/verify.log` is
   the horizontal's proof).
7. **Sacred transaction / non-invent input.** No free human keying of value at TRANSACTION time (price from master,
   qty from scan, stock from fold); keying allowed ONLY at master authoring. No "free numbers." Provenance unbroken.
8. **No central control / secure the fact not the container / use what is guaranteed.** Ops are signed (integrity
   independent of channel); no new server dependency introduced where a guaranteed channel (email/SQLite/browser/
   native intent) would do; the fold tolerates the channel (idempotent/commutative).
9. **Consume the seam — NEVER fork a verb** (FRONTEND_LANE_MASTER). Browser files are UMD COPIES of
   `bim-compiler/scripts/`, not re-implementations; a missing verb is a NAMED finding back to the frozen engine, not a
   UI hack.

**C. Discipline / proof (Watchdog Protocol)**
10. **Every claim has a §-log line.** Read each lane's `# DONE` appendix — a claim with no proving §-log line is NOT
    done; flag it. No log line = not done.
11. **Open JOINT/FROZEN flags not resolved solo** — e.g. `§SEAM-FROZEN`, `gravityRank` vs D2 `menuGroup`
    co-ratification (PROGRESS). A lane resolving a joint-freeze alone = BREAK.
12. **Deploy hygiene** — OCI `--content-type` on every put, `sw.js` CACHE_VERSION + `?v=` bump, branch off
    `origin/main`, smoke + fetch-back. (Only if anything was deployed; nothing should be without GO.)

## ▶ METHOD (deterministic, read-only)
1. Read the fundamentals sources above. 2. Walk the dev surface: `git log`/`git diff` since the last review, the lane
   masters, the prompts' `# DONE` appendices, the new `scripts/`/`docs/` code, and the **witness `§`-logs in
   `build/erp/`** (read the log, do not trust the claim). 3. For EACH fundamental A1–C12, assign **PASS / DRIFT /
   BREAK / UNPROVEN** with concrete evidence (file:line · §-line · commit). 4. For every DRIFT/BREAK, write the
   minimal corrective and who owns it. Do NOT fix it here.

## ▶ DELIVERABLE
- A conformance report (write to `build/erp/fundamentals_watchdog.log` or `docs/FundamentalsReview-<date>.md`): a row
  per fundamental with verdict + evidence; a short **BREAKS** list at top (loud) and a **DRIFTS** list (watch).
- Update `PROGRESS.md` with the review date + the break count.
- Surface BREAKS to the user explicitly; recommend the bounded fix-session for each. If clean: say so, with the
  evidence that earned it (no rubber-stamp).

## ▶ DEFINITION OF DONE
Every fundamental A1–C12 has a verdict backed by cited evidence (no UNPROVEN left unexamined that could be checked);
BREAKS are named with owners and fixes; the report is written and PROGRESS updated. No code changed, nothing deployed.
