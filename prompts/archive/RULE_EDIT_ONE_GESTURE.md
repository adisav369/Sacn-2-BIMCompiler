# ✅ DONE + STRETCH DONE + LIVE (2026-06-05) — L2 PR #137 (sw v580) + L1 lifecycle PR #140 (sw v581),
#   both merged + fetch-back-verified. STRETCH: rule promoted L2→L1 — "may this Order Complete iff
#   GrandTotal ≤ T", a guard on the wfmc CO transition (26 real Odoo orders, 12→21 reflow). One RULES
#   registry in rule_fold.js, ⚖ pill switches L2/L1. §RULE-EDIT-POC PASS both layers, 0 pageerror.
# ── original handoff (L2) record below ──
# ✅ DONE (2026-06-05) — bim-ootb PR #137 (CI green; awaiting merge to publish). Witness real-browser
#   tests/poc_rule_edit.js → §RULE-EDIT-POC PASS: §RULE-FOLD T=100 affected=23 · §RULE-EDIT T:100→500
#   affected=11 chainOk=Y reversible=Y · §RULE-EDIT-ORACLE rebuilt==live K=12 chainOk=Y · §RULE-EDIT
#   T:500→100 reversible=Y · §RULE-GESTURE PASS N=35 K0=23 K1=12 signed=Y (ECDSA). Impl: erp/rule_fold.js
#   (window.RuleFold, ⚖ Rule pill) + gen_ad_odoo.js §5b (real list_price→M_ProductPrice) + kernel_ops.js
#   (optional deterministic ts) + spec erp/docs/RULE_EDIT_SPEC.md. erp/ only, worktree off origin/main, sw v580.
#
# ⚠ DO NOT REMOVE — The ONE Gesture: §RULE-EDIT on migrated Odoo data (NEW SESSION handoff, 2026-06-05)
# SCOPE: demonstrate the Holy Grail in a single gesture — **edit one rule → watch K records re-fold live,
#   signed + reversible** — on the migrated **Odoo Client-12** tenant. ONE rule, ONE gesture, the whole thesis.
#   §-log first (READ the log after every run). Spec-first, witness-led, NON-INVENT. Honour this block until DONE.
# SPIRIT (read FIRST): docs/HolyGrail.md (the grail + the E3 rung) · docs/IDEMPIERE_2.md §"The logic-admission
#   model" (L1 lifecycle / L2 guards as data; the closed-verb membrane) · docs/ERP.md §0.4/§0.5/§0.18.

## ▶ WHY THIS IS THE PARETO FIRST BUILD
HolyGrail is half-reached: rules are extracted to data (R0✅), the engine renders itself read-only (R1✅), the
write seam is built but **dry-run** (E2✅). The load-bearing next rung is **E3 — the signed write goes live +
an acceptance oracle.** This prompt does a **SCOPED E3 for exactly one rule** — the smallest cut that makes the
entire paradigm visible in one gesture, and it does it on **foreign (Odoo) data**, which is the punchline no
incumbent can match: *edit a rule, watch foreign-ERP records reflow, reversibly, with no server.*

## ▶ THE WITNESS (this is the contract — build until this §-line is green)
```
§RULE-EDIT tenant=Odoo(12) rule=<name> edit=<param:from→to> population=<N> affected=K \
           refold=ok signedOp=<op_uuid> chainOk=Y reversible=Y
```
- `population=N` — the real records the rule ranges over (≥20; recommend the 35 Odoo products).
- `affected=K` — how many crossed the rule boundary when the param changed (K must be > 0 and < N to be a real reflow).
- `refold=ok` — the DERIVED classification re-folded from the changed rule (NOT a per-record rewrite — see design).
- `signedOp` + `chainOk=Y` — the rule edit is ONE op on the signed op-log; replay ×2 hashes equal.
- `reversible=Y` — appending the inverse rule-op flips the K records back; a second `§RULE-EDIT … affected=K` proves it.

## ▶ WHAT ALREADY EXISTS (consume it — do NOT rebuild)
- **The tenant demo (LIVE):** `bim-ootb/erp/idempiere.html?seed=ad_seed.db&shard=12-odoo.db&login=Odoo&window=140`
  → Client 12 "Odoo", 35 real products (4 categories), the SO S00023. Shard-in is the load path (`?shard=`).
  Generator: `bim-ootb/erp/tests/gen_ad_odoo.js` (live odoodemo pull; 7-field enforced).
- **The engine + signer, already loaded in idempiere.html:** `window.ERPKernel` (erp_kernel.js — `dispatch/replay/
  query/register`), `window.ERPSeam` (erp_seam.js), `window.ERP` (kanban_host.js — `dispatch/read/verify/ctx`, the
  signed write path), `window.ERPSigner` (erp_signer.js — W-SIGN, ECDSA P-256). The Kanban pill already commits a
  REAL signed `SET_STATUS` through this seam — that is the proven write path to copy.
- **The fold/derived-view machinery:** `_groupRecs`/`_renderHeatMap` in idempiere.html (the heat map is already a
  live fold of a population by a column — the natural surface to show K reflowing).
- **The rules layer:** `AD_Val_Rule` (L2 guard, validation-rule-as-data) is iDempiere's own rules-as-data; the
  glassbowl CRUD/Validation overlay (`build/erp/` → BIMCompiler) carries it. You do NOT need glassbowl — the gesture
  lives in idempiere.html where the Odoo tenant renders and the seam is already published.

## ▶ THE GESTURE — design (engine-as-data; one rule op, K derived results re-fold)
The elegance: editing a rule is ONE signed op; the "K records reflow" is the **re-FOLD of a derived classification**,
not K writes. Pure: the rule is data, the result is a fold over it.
1. **Population + attribute (real, non-invent).** Use the 35 Odoo products over a real NUMERIC attribute. The slim
   `M_Product` shard has no price column — so SUB-TASK 1 is to carry `list_price` (real Odoo data, already pulled in
   `gen_ad_odoo.js`) into the shard (add a column to the shard's M_Product, or a small `M_ProductPrice` row set).
   Keep it real: every price is the recorded Odoo `list_price`. (Fallback population if you prefer: category counts —
   but price-threshold over 35 products gives the most dramatic, faithful reflow.)
2. **Rule as DATA (L2 guard).** Express ONE rule with an editable parameter, stored as a row (an `AD_Val_Rule`-style
   record or a decision-table cell) — e.g. *"a product qualifies as PREMIUM iff `list_price ≥ T`."* `T` is the editable param.
3. **Derived view.** Fold the population by the rule → a count/colored set (reuse `_renderHeatMap`/a count badge):
   "PREMIUM: K of 35". This is the live surface.
4. **The edit = ONE signed op.** Changing `T` (e.g. 100 → 500) dispatches a single op through `window.ERP`/the seam
   recording the rule-param change on the op-log (a generic CONFIG/SET_RULE op — record it so it is auditable; do NOT
   fork a transactional verb). Then re-fold → the badge/heat updates → `K` products cross the boundary, live.
5. **Reversible.** Append the inverse rule-op (`T` 500 → 100) → the K products flip back. Prove with a second witness line.

## ▶ SUB-TASKS (sequence; each ends with a §-witness, READ the log)
1. **Data prep** — carry real `list_price` for the 35 products into `12-odoo.db` (regenerate via `gen_ad_odoo.js`);
   witness `§RULE-DATA products=35 priced=35 min=<x> max=<y>` (all real Odoo prices, 0 invented).
2. **Rule-as-data + derived fold** — define the PREMIUM rule row + render "PREMIUM: K of N" over the population;
   witness `§RULE-FOLD rule=premium T=<t> population=35 affected=K`.
3. **Signed edit op** — wire the `T`-change to ONE signed op via the existing seam (mirror the Kanban write path);
   `window.ERP.verify()` after → `chainOk`. Witness the `§RULE-EDIT …` line (param change → re-fold → K → signedOp/chainOk).
4. **Reverse** — inverse op restores; second `§RULE-EDIT … reversible=Y`.
5. **Acceptance oracle (the E3 half)** — replay the op-log into a fresh projection and assert the rebuilt PREMIUM set
   == the live one (rebuilt == traced). Witness `§RULE-EDIT-ORACLE rebuilt==live K=<k> chainOk=Y`.
6. **Browser witness** — `bim-ootb/erp/tests/poc_rule_edit.js` (Playwright, the harness in `tests/poc_*.js`): drive
   the gesture, assert the §-lines + 0 pageerror + a screenshot of K visibly changing. Run `node tests/audit_specs.js` (exit 0).

## ▶ GUARDRAILS (non-negotiable)
- **NON-INVENT:** every price/record is a recorded Odoo row; absent → honest absent, never synthesized.
- **Spec-first:** write the rule + op + witness spec (append a §-section here or a sibling spec) BEFORE code.
- **§-log first:** read the log after every run; exit code is not evidence.
- **Consume the seam, never fork a verb:** the rule-edit op flows through `window.ERP`/`ERPKernel`; reuse the signer
  + replay. The closed-verb membrane (IDEMPIERE_2 logic-admission) holds — the rule is DATA, the op is recorded.
- **Deterministic:** NO `Date.now()`/`Math.random()` in op paths (pass ts/ids in — they break replay). Money/qty via
  BigDecimal (`site/bigdecimal.js`), never raw JS Number.
- **Honesty boundary:** the witness attests *the rule edit + the re-folded derived classification, signed and
  reversible* — NOT a GL posting (Completed ≠ posted, §I-K/§13.6). State exactly what reflowed.

## ▶ DEPLOY (after green)
bim-ootb PR off `origin/main`, **erp/ only**; the tree is shared+dirty — use a **worktree** (never branch-hop dirty),
realign local main after merge. SW bump (`erp/sw.js`). Force-add the regenerated `12-odoo.db` shard. Fetch-back-verify
the live demo URL. Standing "deploy after test" applies (no extra GO needed for the POC).

## ▶ POINTERS
- Grail + E3/§RULE-EDIT: `docs/HolyGrail.md` (the §RULE-EDIT witness, the E3 rung, "make the validation layer editable
  and re-folding"). Logic layers: `docs/IDEMPIERE_2.md §"The logic-admission model"` (L1 lifecycle, L2 guards, the
  membrane). Live: https://red1oon.github.io/BIMCompiler/  ·  Tenant demo + shard: `prompts/MIGRATE_ERP_PICKER.md`
  (project memory `project_migrate_erp_picker`). Write path to copy: idempiere.html `openKanbanFor` → `window.ERP.dispatch`.
- STRETCH (only after the gesture is green): promote the rule from L2 (a price guard) to **L1 — a lifecycle rule**
  ("when may this Order complete"), the most valuable rule a user edits, which seeds lifecycle-as-data in AD.
```
§RULE-EDIT is the whole paradigm shift made visible in one gesture. Ship that gesture; everything else is scale.
```
