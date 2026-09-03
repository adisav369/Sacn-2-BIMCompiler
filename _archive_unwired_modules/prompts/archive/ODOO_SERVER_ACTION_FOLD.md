# ⚠ DO NOT REMOVE — ODOO SERVER-ACTION INTERPRETER (red-band fold-gap closure #2)
# Paste-to-start: `proceed with prompts/ODOO_SERVER_ACTION_FOLD.md`
# Scope: the ODOO side of "Server-action interpretation" — interpret Odoo's 64 server actions
#   (ir.actions.server) the way the iDempiere side already walks + replays AD_Workflow
#   (ad_workflow.js / W-WF-HARDEN, 11 real traces diff=0). Closes the second genuine code gap in the
#   🔴 fold-gap band of docs/migrate_status_panel.html.
# READ THE LOG after every run. ALL poc_* via `bash build/erp/run_witness.sh scripts/poc_X.js`.
# NON-NEGOTIABLE: spec-first · witness-led · NON-INVENT (classify the 64 from real rows; never invent
#   an action's effect) · deterministic · §FALSIFIER load-bearing · honest about the Python boundary.

---

## 0 · THE GAP (one sentence)
iDempiere's `AD_Workflow` automation is walked + replayed deterministically (`ad_workflow.js`,
W-WF-HARDEN) — but Odoo's **64 server actions (`ir.actions.server`) + 18 cron** have **no
interpreter**, so a migrated Odoo tenant's automation does nothing. This lane builds the interpreter
for the **declarative subset** and honestly defers the Python-`code` subset.

## 1 · WHAT ALREADY EXISTS (read first — do NOT re-derive)
- **iDempiere side DONE:** `build/erp/ad_workflow.js` — walks an `AD_Workflow` node graph and replays
  it over the kernel; **W-WF-HARDEN** = 11 real workflow traces, `diff=0`. The pattern to mirror:
  walk a declarative definition → emit kernel ops → fold.
- **Dispatch-spine pattern:** `build/erp/ad_process.js` (W-PROC) — `classname → handler-registry →
  prepare/validate/doIt`, 5 handlers registered, 22/476 procs dispatched, **454 named-deferred**
  (mechanism built, corpus unported). The Odoo server-action interpreter follows the SAME shape:
  a dispatch registry over action *types*, declarative types interpreted, code-type named-deferred.
- **Odoo migration generator:** `build/erp/gen_ad_odoo.js` — does **NOT** pull server actions today
  (verified 2026-06-14). The shard `12-odoo.db` has no `ir_act_server` rows.
- **Live Odoo 17 source:** server actions live in **`ir_act_server`** (64 rows), cron in **`ir_cron`**
  (18). Odoo's `state`/`usage` column types: `object_create`, `object_write`, `multi` (runs child
  actions), `code` (arbitrary Python), `webhook`, `mail`, etc. The "64 · 18" counts are from
  `MigrateComparisonPaper.md`.

## 2 · SPEC (write BEFORE any engine code)
Add `docs/ERP_BACKEND_SEPARATION.md §<n> — Odoo server-action interpreter` (or a new
`docs/OdooAutomationFold.md`; propose which, and the heading, first):
- **Classify the 64 by `state` FIRST** — this is the whole honesty of the lane. Extract the real
  distribution; do not assume it. Declarative types (`object_create` / `object_write` / `multi` over
  declarative children) are **deterministically interpretable** → emit signed kernel ops. `code` type
  = **arbitrary Python = NOT foldable** → named-deferred exactly like the 454 SvrProcess corpus
  (mechanism, not corpus). `webhook`/`mail` = side-effecting I/O, out of scope (name them).
- **Verb:** `interpretServerAction(actionDef, record, ctx)` — PURE for the declarative subset; returns
  the kernel ops it would commit (create/write field-maps), NOT a live side effect. `multi` recurses
  over its `child_ids` in sequence.

## 3 · STEPS
1. **Extract + classify** (extend the generator): pull `ir_act_server` (+ `ir_cron`) into a shard
   `server_action` table (id, name, model, state, code/field-binding payload, child_ids). Then log the
   **type histogram** — `§SRVACT-CLASSIFY object_write=N object_create=N multi=N code=N other=N`
   (sum = 64). This single line decides how much is interpretable vs deferred. NON-INVENT.
2. **Spec** §<n> (above) — heading proposed first.
3. **Engine** `build/erp/odoo_server_action.js` — a dispatch registry over `state`; declarative
   handlers emit kernel ops; `code` → named-deferred with a logged reason. Cite:
   `// Implementing <spec> §<n> — Witness: W-ODOO-SRVACT`.
4. **Witness** `scripts/poc_odoo_server_action.js` → **W-ODOO-SRVACT**: pick ONE real declarative
   `object_write`/`object_create` action from the shard, interpret it over a real migrated record,
   and assert the resulting record state == what the field-binding declares (diff to the cent if a
   posted value). `§FALSIFIER`: corrupt one field binding → the asserted post-state diverges (proves
   the interpreter actually reads the binding, not a hardcode). Log the deferred `code`-count so the
   honesty boundary is visible in the witness output.
5. **Matrix** — flip the `Workflow / automation` Odoo row in `docs/ERP_COVERAGE_MATRIX.md` +
   `MigrateComparisonPaper.md` from `· FOLD` (no interpreter) to `🟡` for the declarative subset, with
   the `code`-corpus named-deferred count. Update the 🔴 band of `docs/migrate_status_panel.html` (the
   item moves to 🟡/partial, or stays red with a count if the declarative subset is empty). Deploy with
   `mkdocs gh-deploy` from bim-compiler.

## 4 · DONE WHEN
- `§SRVACT-CLASSIFY` histogram logged (the 64 split by type, summing to 64).
- `W-ODOO-SRVACT` PASS on one real declarative action + falsifier fires; deferred `code`-count named.
- Matrix + panel + paper updated and published.
- ⛔ EXPECTED HONEST OUTCOME: if the histogram shows the 64 are mostly `code` (Python), say so plainly
  — the declarative subset may be small, and that's the truthful result, not a failure. Name the
  deferred corpus; do not synthesize interpretable actions to pad the count.

## OUTSTANDING
- [ ] Extract `ir_act_server`/`ir_cron` → shard + `§SRVACT-CLASSIFY` histogram (sum=64)
- [ ] Spec section (propose doc + heading first)
- [ ] `odoo_server_action.js` declarative-subset interpreter
- [ ] `poc_odoo_server_action.js` W-ODOO-SRVACT PASS + falsifier + deferred count
- [ ] Matrix + panel + paper updated, `mkdocs gh-deploy`
