# ⚠ DO NOT REMOVE — Adversarial Equivalence Audit (refute the "== iDempiere to the cent" claim before it ships)
# SCOPE: a READ-ONLY skeptic's pass over the ERP equivalence + coverage claims. For each claim the engine makes
#   ("oracle-equivalent", "rule-consistent", "recipe-equivalent", or a 🟡 coverage verdict), TRY TO REFUTE IT.
#   Default a claim to SOFT until you have independently re-confirmed it from a re-run log or a fresh query.
#   The deliverable is a VERDICT per claim + a ranked list of the weakest ones — NOT new folds, NOT more porting.
# WHY NOW: the equivalence axis just grew to 20 oracle-equivalent (+1 recipe +2 rule-consistent) and is the
#   headline of MigrateComparisonPaper. The biggest project risk is no longer "build more" — it is that ONE of
#   these claims is subtly tautological / unfalsified-in-seed / mis-tiered, and an external skeptic finds it first.
#   This audit is that skeptic, run by us, before publication.
# NON-NEGOTIABLE:
#   - READ-ONLY w.r.t. every other lane. DO NOT edit `scripts/poc_*.js`, `scripts/erp_engine.js`,
#     `scripts/post_resolver.js`, `build/erp/*.js`, `docs/ERP_COVERAGE_MATRIX.md`, `docs/ERP_MODEL_ARCHETYPE.md`,
#     `docs/FoldEngineQuality.md`, `docs/MigrateComparisonPaper.md`, or anything in `bim-ootb/`. These are owned by
#     the FOLD (frozen), H-3 declarative, and UI-bridge lanes. You FLAG; the owning lane fixes.
#   - EXTRACT / NON-INVENT. Every verdict traces to a re-run `§`-log line or a `sqlite3`/`psql` query you ran and
#     quote. No verdict from memory or from the prose of the doc under audit. READ the log before concluding.
#   - Deterministic, integer cents, no Date.now/Math.random in any probe you write.
#   - The audit writes ONLY to its own report (§5): `build/erp/AUDIT_EQUIVALENCE.md` + `build/erp/audit_*.log`.
#   Honour until the # DONE appendix gives every claim a verdict with evidence.

---

## 0. POSTURE (verified-skeptic, not cheerleader)
The docs assert; this audit disbelieves until re-proven. For each claim you are the opposing reviewer whose job is
to make it FAIL. A claim survives only if you tried the specific refutations below and none landed. "It passes"
(exit 0) is NOT a verdict — exit 0 only says the script's own assertions held; the audit questions whether those
assertions are the RIGHT ones. (Mirrors the project Log Mandate: exit code is not evidence.)

**SCOPE OF THE PRODUCT BEING AUDITED (the line the audit must respect).** This engine targets the **main ERP
operations** — the `MOrder` document archetype + its ~25 deltas: the order→ship→invoice→match→pay→allocate trade
loop, inventory (movement/on-hand/replenish/production/count), and GL (posting/journal/trial-balance) — plus the
declarative behaviour that governs those documents (display/readonly/mandatory logic, access, validation, field
callouts). The **454-proc SvrProcess corpus, the 139-atom callout tail, the per-model override remainder, and the
workflow corpus are edge/specialty surfaces — an intentional non-goal**, gated on per-item demand + an oracle, NOT
a sequencing step (the bloat-reduction thesis: delivery/definition, not feature parity).

**Out of scope (do not flag as gaps):** that edge corpus above — the dispatch *mechanism* is proven; porting the
volume is the deliberate non-goal. The 3⛔ n/a-in-seed surfaces (AD_Rule Fact_Reconciliation + 2 empty `*_Access`)
— no oracle exists, honestly closed. **Auditing whether the main-vs-edge line and these labels are HONESTLY drawn
is IN scope** (an edge mis-sold as core, or a corpus gap hidden, is a finding); demanding the edge be built is not.

## 1. THE SIX REFUTATIONS (each names the failure mode it hunts)
| Check | The failure it tries to expose |
|---|---|
| **§AUDIT-TAUTOLOGY** | the "oracle" is NOT independent of the derivation — we diff our output against numbers we (directly or transitively) fed in. The grail is two INDEPENDENT producers of the same value (e.g. our fold vs real `fact_acct` posted by iDempiere's Java). Flag any claim where the oracle is our own engine, a copied column, or the same SQL run twice. |
| **§AUDIT-FALSIFIER** | the `§FALSIFIER` is weak/decorative — re-run it, confirm the claimed `maxDiff`, and judge whether the mutation actually exercises the LOAD-BEARING rule or just perturbs an unrelated number. A falsifier that would still fire if the rule were wrong-but-differently proves little. |
| **§AUDIT-UNFALSIFIED-IN-SEED** | `maxDiff=0c` only because the seed has no HARD case. For each fold name the ONE input that would break it (a >2dp cost, an FX rate≠0.85, a multi-line tax split, an IPV with onHand>qty, a rate≠1 journal) and check whether the seed actually contains it. "Proven" on easy data is really "unfalsified-in-seed" — a weaker claim the paper must not overstate. |
| **§AUDIT-TIER** | a claim is mis-tiered — a "rule-consistent" or "recipe-equivalent" result is dressed as "oracle-equivalent", or a ✅ silently leans on a `named-deferred` residual that removes the load-bearing part (e.g. GL value-half deferred while the row still reads ✅). Confirm each tier label is the honest one. |
| **§AUDIT-PROVENANCE** | the oracle data isn't real / isn't what it claims — confirm `fact_acct` (and the H-3 Postgres oracles) trace to the captured GardenWorld client-11 seed, not a synthesized or hand-edited fixture; confirm `extract_*` is additive and the H-1 trial balance (46574.97) is the same anchor throughout. |
| **§AUDIT-COVERAGE** | a 🟡 surface only PARSES, not INTERPRETS — spot-check a sample of the 37🟡 actually produce a correct verdict over real rows (not just "the file loads"); confirm the 3⛔ are genuinely oracle-less, not dodged. |

## 2. METHOD (read-only; re-run, parse, classify)
1. **Enumerate the claims** from the committed witness set + the matrix equivalence axis (read the docs for the LIST
   only, never trust their verdict). Fold witnesses: `poc_fold_complete poc_money_post poc_alloc_post poc_alloc_fx
   poc_qtyonhand poc_replenish poc_invoice_complete poc_invoice_post_ap poc_movement poc_movement_fx poc_matchinv
   poc_matchinv_fx poc_post_harden poc_reverse poc_gljournal poc_production poc_inventory poc_backflush`; H-3
   declarative: `poc_valrule_harden poc_reference_harden poc_access_harden poc_callout_harden`.
2. **Re-run each** to a fresh `build/erp/audit_<name>.log`; parse its `§FOLD-COMPLETE`/`§HARDEN`/`§FALSIFIER` lines.
   Confirm the numbers the doc cites MATCH the live log (drift = finding).
3. **Apply the six refutations** to each claim. Where a refutation needs evidence the witness doesn't give, write a
   SMALL read-only probe (`build/erp/audit_<topic>.js`, node + better-sqlite3/sql.js over `glassbowl_data.db` or a
   `psql` query against the live Postgres oracle) — quote its output. NEVER mutate a db; copy if you must enact.
4. **Classify** each claim: **SOLID** (independent oracle + load-bearing falsifier + a hard case exists in seed) /
   **SOFT** (true but unfalsified-in-seed, or oracle weakly independent — honest but the paper should hedge) /
   **OVERSTATED** (mis-tiered, tautological, or the falsifier doesn't bite — must be re-worded or demoted).
5. Adversarial discipline: when unsure, default **SOFT**, and state the single piece of evidence that would promote
   it to SOLID. A claim you cannot independently re-confirm is SOFT by definition, not SOLID-by-trust.

## 3. PRIORITISED TARGETS (deepest-suspicion-first — these are where a soft claim most likely hides)
- **GL_Journal (`poc_gljournal`)** — was flagged "near-tautological" then rescued by inter-org balancing. RE-TEST:
  is `amtacct = round(amtsource × rate)` diffed against a stored value we also could have just copied? Is the per-org
  Due-To/From the ONLY non-trivial derivation? §AUDIT-TAUTOLOGY hardest here.
- **The rule-consistent tier (`poc_production`, `poc_inventory`)** — NO `fact_acct` oracle by their own admission.
  Confirm they are NOT counted in the "oracle-equivalent" tally anywhere, and that the qty-spine they fold through is
  itself oracle-anchored (else it's rules-checking-rules). §AUDIT-TIER + §AUDIT-TAUTOLOGY.
- **The recipe tier (`poc_backflush`)** — oracle = an independent path-enumeration we also wrote. Is that genuinely
  independent of `explodeBOM`, or two spellings of the same recursion? §AUDIT-TAUTOLOGY.
- **The FX folds (`poc_alloc_fx`, `poc_movement_fx`, `poc_matchinv_fx`)** — passed partly because rate 0.85 / the EUR
  cost are clean. §AUDIT-UNFALSIFIED-IN-SEED: does the seed contain ANY FX leg whose HALF_UP rounding is non-exact
  (a half-cent)? If every leg converts exactly, the HALF_UP path is asserted-but-unexercised — say so.
- **The "named post-posting drift" excuses (doc 109 in `poc_post_harden`/`poc_fold_complete`; line 119 in
  `poc_callout_harden`)** — is "the source was edited after posting" a VERIFIED fact (a dated source-vs-posting diff)
  or a convenient explanation for a miss? Demand the evidence; if absent, it's an OVERSTATED ✅, not a named residual.
- **The H-3 declarative diffs (`poc_*_harden`)** — they diff our SQLite engine vs live Postgres. §AUDIT-TAUTOLOGY:
  is our side a genuine REIMPLEMENTATION (token substitution / access bitmask / FK resolution in JS) or does it hand
  the SAME SQL to both engines (then it only proves SQLite==Postgres, not engine==iDempiere)? This is the subtlest one.

## 4. PROBES YOU MAY WRITE (own files only)
`build/erp/audit_*.js` (node) and ad-hoc `sqlite3`/`psql` one-liners. Each probe: deterministic, read-only, prints
the numbers that decide a verdict. Reuse the FOLD discipline (alias columns, integer cents, no Date.now). The live
Postgres oracle (docker `postgres`/`idempiere_test`, client 11) is fair game for an INDEPENDENT second read where a
SQLite-only check would be circular — state when you use it.

## 5. OUTPUT — `build/erp/AUDIT_EQUIVALENCE.md` (this audit's OWN file; touch nothing the other lanes own)
- A table: claim | tier-as-stated | §refutations-applied | **verdict (SOLID/SOFT/OVERSTATED)** | evidence (log line
  or query) | the one fix that would harden it.
- A scoreboard: N SOLID / N SOFT / N OVERSTATED of the audited claims.
- A RANKED "harden-first" list — the OVERSTATED ones, then the SOFT ones whose promotion is cheap — each as a
  one-line hand-off the owning lane (FOLD / H-3 / paper) can action. This is a FLAG list, not a fix list.
- An explicit "what an external skeptic would still attack" paragraph — the residual exposure after this pass.

## 6. STOP CONDITION
Every equivalence + sampled-coverage claim has a verdict backed by a re-run log line or a quoted query; the
OVERSTATED/SOFT ones are ranked with a cheap-fix hand-off; `AUDIT_EQUIVALENCE.md` written; ZERO edits to any file
owned by another lane (FOLD/H-3/UI/docs). If a verdict needs a user fact that can't be EXTRACTED (e.g. "is doc 109's
source-edit dated after the posting?" with no extractable timestamp) → record it `⛔ BLOCKED: <the one question>`
and move to the next claim. Report only the verdict scoreboard + the harden-first list + the ⛔ questions.

---

# DONE — Audit complete (2026-06-10, `feat/erp-substrate-phase012`)

**Deliverable:** `build/erp/AUDIT_EQUIVALENCE.md` (full per-claim grid + evidence). 22 witnesses re-run to `build/erp/audit_*.log` (all exit 0). Zero edits to FOLD/H-3/UI/doc-owned files.

## Scoreboard (22 claims)
- **SOLID 18** — all 14 fold (Class A: derivation vs real `fact_acct`) except matchinv_fx, + production/inventory/backflush (correctly tiered, NOT in the 20), + `callout_harden` (the one genuine H-3 reimpl).
- **SOFT 2** — `matchinv_fx` (FX HALF_UP unexercised at clean 0.85, admitted), `reference_harden` (membership leg = SQLite==Postgres; resolution leg genuine).
- **OVERSTATED 2** — `access_harden` (grant-map diff runs IDENTICAL `WHERE isactive='Y'` SQL on both DBs ⇒ SQLite==Postgres, not MRole reproduction), `valrule_harden` (5/10 fixtures static/token-free ⇒ SQLite==Postgres; substitution trivial on the other 5).

## The one surviving finding — F-TIER-1 (harden-first #1, cheap, highest leverage)
The single `✅ oracle-equivalent` badge conflates **Class A** (~16 fold-vs-`fact_acct`, independent product — the grail, holds) with **Class B** (4 H-3 declarative — 2 OVERSTATED, 1 SOFT, 1 SOLID). Recommend `ERP_COVERAGE_MATRIX.md:57` / `MigrateComparisonPaper.md:491` print **two sub-tiers** instead of one "20". This is a wording/labelling fix for the *paper/H-3 lanes* — NOT a fold defect.

## Refuted on re-check (the user's nudges were right)
- **Provenance PASS** — fold oracle is live-re-verifiable: `glassbowl_data.db.fact_acct` == `idempiere_test.fact_acct` (client 11: 300 rows, ΣDr=ΣCr=46574.97, table 224 = 8/370.00) EXACTLY. (My first pass wrongly queried db `idempiere`, which has 0 `fact_acct` rows — that DB is the H-3 *declarative* oracle, GL unposted. **Source-of-truth = `idempiere_test`.** If you want the two oracle DBs aligned, a seed pull/repost into `idempiere` would do it — but H-3 declarative doesn't need the GL, so it's optional.)
- **doc-109 / callout-119 drift excuses VERIFIED** — `c_invoice 109.updated=2004-01-04` (header carries the edit date; the line's stayed 2003-12-30) and `fact_acct` holds BOTH the 254.00 and 215.90 postings on line 127 (re-post after edit, in the ledger). `c_orderline 119.updated=2021-10-16`, priceactual refined to 2.975, stale linenetamt 89.4. The matrix narratives are accurate.

## Money-math substrate (re: BigDecimal)
Fold GL math = **exact integer-cents + BigInt HALF_UP off the TEXT-preserved rate**; FX/matchinv witnesses import `bigdecimal.js`. No float drift on any posting. Only `poc_callout_harden.js:60` uses raw `Number`×100 rounding for a price-derive *comparison* (negligible; not a GL posting).

## Harden-first hand-offs (FLAG list — owning lane fixes)
1. **[paper, cheap]** split the "20" badge into Class A / Class B sub-tiers (F-TIER-1).
2. **[H-3/access, cheap]** relabel access_harden grant-map leg as config-fidelity, not MRole reproduction.
3. **[H-3/valrule, cheap→med]** qualify "10/10 non-tautological" → ≤5 token rules; optionally add `@SQL=`/nested-token rules.
4. **[H-3/reference, cheap]** foreground the FK-resolution reimpl; name the membership leg as data-fidelity.
5. **[fold/matchinv_fx, med]** add one FX leg whose `amtsource×0.85` hits a half-cent → SOFT→SOLID.
6. **[paper, cheap]** document that fold oracle = `idempiere_test`, H-3 oracle = `idempiere` (don't point a fold re-check at `idempiere`).

## On the held Phase-3 (erp_preview.js consuming doc_poster) — AUDIT VERDICT: GO
The Gap-A hand-off (`doc_poster.js` + `poc_doc_poster.js`, `§DOC-POSTER …maxDiff=0c …§FALSIFIER 5035c`) reuses the EXACT oracle-diff + load-bearing falsifier of `poc_fold_complete` — consistent with the SOLID fold tier; the one shared-file edit (`post_resolver.js` UMD tail) was proven non-regressing by re-running 6 FOLD witnesses. Phase 3 is additive, headless, worktree-isolated (`/tmp/wt-preview`), no deploy — it does not touch any audited equivalence claim. **GO**, with ONE guard: the preview must NOT badge the `basis='order'` draft-projection branch as oracle-equivalent (it has no `fact_acct` oracle — already honestly labeled in `doc_poster.js`; keep it labeled "projection" in the UI/witness too).

---

# CONTINUE — resume the AUDIT ROLE (next session)

**The audit role is STANDING, not one-shot.** A new session resumes here: same posture (verified-skeptic, default SOFT, every verdict traces to a re-run `§`-log line or a quoted query), same non-negotiables (READ-ONLY w.r.t. every other lane — FLAG don't fix; write ONLY `build/erp/AUDIT_EQUIVALENCE.md` + `build/erp/audit_*.log`).

## State of the deliverable (read it first)
`build/erp/AUDIT_EQUIVALENCE.md` is the living report. Current scoreboard: **18 SOLID / 2 SOFT / 2 OVERSTATED** of 22 equivalence claims, **+1 surviving cross-cutting finding F-TIER-1**, **+** the POSTING_PREVIEW_PANEL lane audited SOLID (§6). Two first-pass findings (fold-oracle provenance, doc-109/callout-119 "edit" excuses) were **REFUTED on re-check** and are recorded as PASS — do NOT re-raise them; the evidence is in §4/§A.

## Standing facts (carry these — they cost a prior mistake)
- **Posted-GL source of truth = docker `idempiere_test`** (client 11 `fact_acct` = 300 rows / `ΣDr=ΣCr=46574.97`, matches `glassbowl_data.db` exactly). The `idempiere` DB has the AD dictionary but **0 `fact_acct`** — NEVER point a fold/financial diff at it (that error produced a false provenance gap first pass).
- The "20 oracle-equivalent" tally mixes two classes — **Class A** (fold vs independent product `fact_acct`) is strong; the **4 H-3 declarative** rows are largely config-read-back / SQLite==Postgres (F-TIER-1). Hold that line on any NEW equivalence claim: a fold-vs-`fact_acct` claim is SOLID-eligible; a "same SQL on two DBs over copied data" claim is NOT — flag it.

## What to audit NEXT (as each lands — re-run, refute, verdict)
1. **Reporting lane** (`prompts/REPORTING_LANE.md`, `docs/ReportingFold.md`): when `W-PA-REPORT` / `W-PRINTFORMAT` land, audit them as **Class A** (fold vs the independent `idempiere_test` Financial-Report / `PrintData` oracle) — confirm the oracle is truly independent (not our own SQL run twice), the §FALSIFIER is load-bearing, the seed carries a HARD case, and `foldPnL`/Cash-Flows aren't quietly over-tiered. Matrix surfaces `PA_Report`/`AD_PrintFormat` are 🟡-pending — verify they only join the equivalence axis on a green `maxDiff=0c`.
2. **POSTING_PREVIEW §8 follow-ups** (Reverse/Void preview, USD↔EUR schema toggle, other panels): audit each like §6 — pure-preview (db byte-unchanged), Class-A inheritance, UI zero-leak on gate.
3. **Harden-first list (re-check if the owning lanes actioned it):** F-TIER-1 badge split (paper/matrix) · `access_harden`/`valrule_harden` OVERSTATED relabels · `reference_harden` foregrounding · `matchinv_fx` SOFT→SOLID (a half-cent FX leg) · `callout_harden` residual pin. Mark each fixed/still-open with evidence.
4. **Any new fold/H-3 witness** committed since: re-run to `build/erp/audit_<name>.log`, apply the six refutations (§1), classify SOLID/SOFT/OVERSTATED, append to the report.

## Method reminder (unchanged)
Re-run the witness yourself (exit 0 ≠ evidence — read the `§` lines). Independent oracle? Load-bearing falsifier? Hard case in seed? Honest tier label? When unsure → SOFT, and state the one piece of evidence that would promote it. Append verdicts to `AUDIT_EQUIVALENCE.md`; never edit another lane's files. Record un-extractable questions as `⛔ BLOCKED: <the one question>` and move on.
