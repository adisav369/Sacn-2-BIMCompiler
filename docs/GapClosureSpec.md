# Gap-Closure Spec — how a coverage gap becomes oracle-equivalent

**Governs:** `prompts/GAP_CLOSURE_LANE.md` (the operational backlog, repo-local) closing gaps in
[`ERP_COVERAGE_MATRIX.md`](ERP_COVERAGE_MATRIX.md). **Parent discipline:** `prompts/HARDEN_MATRIX.md`
(the equivalence arc) + [`ERP_MODEL_ARCHETYPE.md`](ERP_MODEL_ARCHETYPE.md) (the denominator). **Method is PoC-proven**
— the oracle-diff harness (`scripts/poc_*_harden.js`, the `W-FOLD-*` family) already ships green; this lane *executes*
it across the remaining gaps. Spec-first: **no witness without a spec section naming the rule it proves.**

## 1. Prime law
**A row is ✅ only by an oracle DIFF, never by a claim.** Oracle output is *extracted* from the live iDempiere
Postgres or the iDempiere checkout — never hand-authored. Every closure carries a load-bearing **§FALSIFIER**:
corrupt the rule and the diff *must* go non-zero, or the test proves nothing (a pass that can't fail is a tautology).

## 2. Gap taxonomy — classify the gap BEFORE folding (it sets the achievable verdict)
| Class | What it is | Achievable verdict | Oracle source |
|---|---|---|---|
| **Foldable-now** | logic + seed + oracle all present | ✅ **oracle-equivalent** (`maxDiff=0c`) | live PG / captured `fact_acct` |
| **Data-blocked** | logic known, but seed doesn't exercise it (no oracle to diff) | 🟡 + named-deferred — **not** ✅ | none yet — say so, don't fake it |
| **Rule-consistent** | enacted path with no seed oracle (e.g. MProduction GL) | **rule-consistent** (vs proven sub-rules + balance + falsifier) — explicitly **not** `== iDempiere` | derived sub-rules |
| **Deleted-by-architecture** | ZK/JDBC/OSGi/app-server tier | 🔵 — **no port**, leaves the denominator | n/a |
| **On-demand corpus** | infinite/long-tail (454 SvrProcess) | mechanism ✅, corpus named-deferred | per-case when used |

Do not promote a data-blocked or rule-consistent gap to ✅. Honesty about the verdict *is* the deliverable.

## 3. Oracle protocol — how `maxDiff=0c` is established, by gap shape
- **Persisted output** (e.g. `fact_acct`): diff engine output vs the real stored rows, per (account, record, line, side),
  cents. The `W-FOLD-*` template.
- **Live declarative** (membership/verdict — val-rule, reference, access, callout, logic): diff engine vs **live PG**
  via `docker exec postgres psql -U adempiere -d idempiere` (schema `adempiere`). The `poc_*_harden.js` template.
- **Transient scratch table** (the `T_*` reports — `T_Aging`, `T_TrialBalance`, …): the table is per-run, *not* a stored
  oracle. Two valid oracles, in preference order: **(a)** trigger the real iDempiere process and capture its `T_*` rows
  (strongest); **(b)** if (a) is impractical, take iDempiere's **own source view** (e.g. `rv_openitem`, whose `DaysDue`
  iDempiere computes) as the SOURCE, and diff the engine's fold against an **independently-coded SQL bucketer over the
  same view** — the two-independent-paths pattern of `W-FOLD-QTYONHAND`. Non-tautological iff the two bucketers are
  genuinely independent. **Label which oracle was used; (b) is "grounded on iDempiere's source view," not "== T_Aging".**
- **No oracle in seed**: stop — verdict is rule-consistent or data-blocked (§2), never a synthesized number.

## 4. Witness template (one per gap)
1. `build/erp/<rule>.js` — the engine fold (pure verb; INTEGER CENTS / BigInt HALF_UP for money; no `Date.now`/`random`).
2. `scripts/poc_fold_<rule>.js` — drives the fold, pulls the oracle (live PG or capture), diffs, prints `§<RULE> …
   maxDiff=…` and a `§FALSIFIER` line; run via `bash build/erp/run_witness.sh scripts/poc_fold_<rule>.js`.
3. `build/erp/poc_<rule>.log` — the saved run; **READ it** (exit code ≠ evidence).
4. `docs/ERP_COVERAGE_MATRIX.md` — flip the row's verdict only after the log shows `maxDiff=0c` (or label rule-consistent).

## 5. Definition of Done (per gap)
✅ requires ALL of: spec section · engine fold · `poc_fold_<rule>.js` · `poc_<rule>.log` with `maxDiff=0c` vs a real
oracle · a §FALSIFIER that fires · the matrix row flipped. Missing any one → it is **not** done; report it as 🟡/⛔ with
the one blocking fact. Per-gap report: rule · LOC (engine+witness) · diff result · new falsifier · gap-count delta.

## 6. Constraints (mirror of the lane card)
No porting of architecture-deleted tiers (🔵) · no pre-porting the 454 SvrProcess corpus (on-demand mechanism only) ·
no synthesized oracles · keep the `docs/ERP_BACKEND_SEPARATION.md` §0 seams (declaration / interpreter / log-fold never
merge). The objective is **per-deployment equivalence** — a tenant's *used* surface folds to the cent — not a 496-class
sweep. See [`MigrateComparisonPaper.md`](MigrateComparisonPaper.md) for the substrate-and-method (not feature-parity) frame.
