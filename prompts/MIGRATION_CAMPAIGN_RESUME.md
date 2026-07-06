# ⚠ DO NOT REMOVE — Scope guard / resume card
# Scope: the MIGRATION-SOLVENT CAMPAIGN — prove the iDempiere-2.0 thin core (5-table bridge + 6 verbs +
#        op-log) folds foreign ERPs with NOTHING invented, ONE diff-oracle at a time, in sequence:
#        iDempiere (done) → Odoo (mostly done) → SAP standard → SAP custom. Each stage is its own
#        falsifier, gated by its own static oracle (§0.12). This card RESUMES the campaign from the
#        Odoo state below up to TACKLING SAP.
# NON-NEGOTIABLE (carry every turn): spec-first; witness-led (each test NAMES the issue it proves/
#        disproves); §-log first (READ the log before any conclusion); deterministic / NON-INVENT (every
#        id/qty/amount a RECORDED INPUT from the export — no Date.now/Math.random; STATIC oracle, no live
#        system at replay); CLEAN-ROOM (learn the foreign ERP's BEHAVIOUR by running it + diffing executed
#        rows; never copy its LGPL/closed source into the MIT tree — a needed-but-absent capability is a
#        NAMED finding, code-or-data classified, NOT a fabrication); engine changes are deliberate +
#        witnessed; EXPLICIT GO before any deploy.
# Read first: prompts/ODOO_FOLD_POC.md (the falsifier method) · prompts/SAP_FOLD_POC.md (the SAP asymptote)
#        · docs/HolyGrail.md §"migration solvent" (the thesis + the honest boundary) · docs/ERP.md
#        §0.12/§0.17/§0.19 · build/erp/odoo_fold.log (the full Odoo witness trail) · scripts/erp_kernel.js
#        + erp_engine.js (the 6 verbs + the matcher to reuse) · scripts/odoo_adapter.js (the pattern).

---

# Migration-Solvent Campaign — resume (state as of 2026-06-03)

## ▶ WHERE WE ARE — Odoo is the second abstraction, all but one scenario folded

The op-log kernel has SIX verbs: `CREATE_DOCUMENT · CREATE_LINE · SET_STATUS · POST · ALLOCATE · MATCH`
(scripts/erp_kernel.js). The campaign tests whether a foreign ERP's executed output reproduces using ONLY
those six + a PURE adapter (`odoo_adapter.js` — the data dictionary: schema map + state map). **`newVerbs=[]`
= thesis holds; non-empty / new-behaviour = BOUNDED, a valid reportable result.**

**iDempiere** — DONE+witnessed (migrate_pg_to_sqlite + compile_rules + diff_oracle + verify_migration).

**Odoo 17 (`odoodemo`)** — 4 scenarios driven via RPC to completion, frozen as static oracles, folded.
All witnesses in `build/erp/odoo_fold.log`; oracles `build/erp/odoo_oracle*.json` (tracked); runners
`scripts/poc_odoo_fold{,_3way,_partial,_f8}.js` (tracked):
| Scenario | Witness | Result |
|---|---|---|
| Sell-side O2C (SO→deliver→invoice→post→pay→reconcile) | `§ODOO-FOLD PASS` | PASS · newVerbs=[] |
| Buy-side 3-way full (PO→receipt→bill→post→MATCH) | `§ODOO-FOLD-3WAY PASS` | PASS · newVerbs=[] · all 6 verbs now exercised |
| f7 partial-receipt (ordered 20/recv 12/bill 12) | `§ODOO-FOLD-PARTIAL PASS` | PASS · decomposes = exact-match leg + FK-directed remainder (§0.17) |
| f8 bill≠receipt (recv 12/bill 8) | `§ODOO-FOLD-F8 PASS` | PASS (was BOUNDED) · Stage-1 fix landed · partial-qty matcher |
| f2 partial PAYMENT (pay 3000 of 5002.50) | `§ODOO-FOLD-PAYPART PASS` | PASS · same ALLOCATE, residual 2002.50 · newVerbs=[] · no engine change |
| f1 account DERIVATION (derive 400000/251000/121000) | `§ODOO-FOLD-ACCTDERIV PASS` | PASS · resolver derives accts from config · newVerbs=[] · host glue not engine |

**STAGE 1 + STAGE 2 (f2 + f1) DONE (2026-06-03) — Odoo Definition-of-Done MET + EXCEEDED.** The f8 finding
(the one engine gap) is CLOSED: `erp_engine.match` gained opt-in `opts.partial=true` (pair `min(qty)` + carry
remainder; exact-qty fast path + `[[idL,idR]]` shape preserved; partial mode returns `[{l,r,qty}]`). f8
flips BOUNDED→PASS, `poc_odoo_fold_f8.js` now exercises the engine. f2 partial PAYMENT folded with the
SAME ALLOCATE verb at the smaller amount (residual=total−allocated reproduces Odoo to the cent),
`newVerbs=[]` AND no engine change — driven by `scripts/drive_odoo_paypart.py` (JSON-RPC), oracle
`build/erp/odoo_oracle_paypart.json`, runner `scripts/poc_odoo_fold_paypart.js` (+ adapter
`buildPayPartEvents`). No regression: `build/erp/f8_fix.log` (11 runners, 0🔴).

**f1 was the standing honest bound — NOW CLOSED (`§ODOO-FOLD-ACCTDERIV PASS`).** Account *determination*
(which GL account) used to come from Odoo as host data. The resolver now DERIVES it from extracted config
(product income template→category fallback; tax repartition account; partner receivable), matching Odoo's
posting to the account (400000/251000/121000), balanced + cent-perfect. Still host GLUE not engine — POST
owns only `ΣDR==ΣCR` (§13.1) — and the determination logic was learned clean-room from config STRUCTURE,
never Odoo's source. Claim raised: "reproduces GIVEN accounts" → "DERIVES the accounts"; `newVerbs=[]`.
(`scripts/drive_odoo_acctcfg.py` → `build/erp/odoo_oracle_acctderiv.json`; runner `poc_odoo_fold_acctderiv.js`.)

## ▶ THE PATH — ordered work to resume, up to tackling SAP

**Stage 1 — close the Odoo engine gap (small, do first). ✅ DONE 2026-06-03.**
- ~~Implement the f8 fix~~: LANDED in `scripts/erp_engine.match` (opt-in `opts.partial=true`, pair min-qty +
  carry remainder; exact-qty fast path + return shape preserved). `poc_odoo_fold_f8.js` flips BOUNDED→PASS,
  `newVerbs=[]`; no regression across 10 runners (`build/erp/f8_fix.log`). Witness `§MATCH-PARTIAL pairs=1
  matchedQty=8 remainder=4`.

**Stage 2 — finish the Odoo bounds (each its own oracle + witness; non-invent).**
- **Partial PAYMENT** (the f2 partial): ✅ DONE 2026-06-03 — `§ODOO-FOLD-PAYPART PASS`. Drove SO `S00027` →
  invoice 5002.50 → pay 3000 (`scripts/drive_odoo_paypart.py`), oracle `build/erp/odoo_oracle_paypart.json`;
  `poc_odoo_fold_paypart.js` folds the partial `ALLOCATE` → residual 2002.50 reproduces Odoo, `newVerbs=[]`,
  no engine change. **Odoo Definition-of-Done is now MET (f8 fix + partial payment).** Remaining items optional:
- **f1 account determination**: ✅ DONE 2026-06-03 — `§ODOO-FOLD-ACCTDERIV PASS`. Extracted Odoo's
  determination CONFIG (`scripts/drive_odoo_acctcfg.py` → `build/erp/odoo_oracle_acctderiv.json`: product
  income template→category fallback, tax repartition account, partner receivable); `resolveAccounts`/
  `buildDerivedPost` (adapter) DERIVE the accounts → match Odoo's posting to the account (400000/251000/
  121000), balanced + cent-perfect, `newVerbs=[]`, host glue not engine. Claim raised to "derives the accounts."
- **Multi-currency** + **anglo-saxon COGS interim** (stock valuation at receipt) — STILL OPTIONAL, untested:
  name as found; likely a POST with more resolved lines, not a new verb — but PROVE it, don't assume. `§ODOO-FINDINGS-…`.

**Stage 3 — TACKLE SAP (the asymptote — a NEW session; follow `prompts/SAP_FOLD_POC.md`). SKELETON READY 2026-06-03.**
- Honest claim up front (HolyGrail §boundary): SAP = "the standard flows + extractable config, with
  Z-customisations per engagement" — NOT "eat any SAP instance." The campaign is iDempiere→Odoo→SAP
  standard→SAP custom, each gated by its own oracle.
- **✅ ALLOWED HALF DONE (clean-room, blind):** `scripts/sap_adapter.js` = the schema/state-map HYPOTHESIS
  (VBAK/VBAP→C_Order · LIKP/LIPS→M_InOut · VBRK/VBRP→C_Invoice · BKPF/BSEG|ACDOCA→journal · VBFA=derivation
  spine · BSEG/BSAD clearing=ALLOCATE/MATCH; `normalizeGLLine` SHKZG/DRCRK S/H→dr/cr; `buildSapEvents` VBFA-led;
  6 NAMED_DIVERGENCES standard-vs-Z). Gated runner `scripts/poc_sap_fold.js` → `§SAP-ORACLE unavailable` /
  `§SAP-FOLD BLOCKED` (exit 0; no fabricated rows). Template `build/erp/sap_oracle.template.json`. Log `build/erp/sap_fold.log`.
- **GATE (the real blocker, still open):** need a real SAP source + an oracle to diff (a sandbox/IDES dump, or
  the standard SD/FI flows). Until a real export exists, SAP stays SPEC — do NOT fold against an invented oracle.
  Every field in `sap_adapter.js` is a HYPOTHESIS to VERIFY against the real dump.
- **Next-session method (the runner activates with NO further wiring):** obtain a real export → fill
  `build/erp/sap_oracle.json` from it (NON-INVENT, shape = the template) → re-run `poc_sap_fold.js` → it folds
  through the 6 verbs (+ the f8 partial-qty matcher) + diffs → `§SAP-FOLD verbs used=[…] newVerbs=[…]` + `acdoca-as-fold`.
- The Odoo campaign is the dress rehearsal: the adapter pattern, the RPC-drive-then-freeze discipline, and
  the verb set are all proven. SAP's value is finding which Z-behaviour does NOT fold — that is the finding.

## ▶ RESUME RECIPE — bring Odoo back up (it was STOPPED at session close)
```
docker start odoo-db odoo            # DB odoodemo already initialised + purchase module installed — do NOT re-init
# wait ~8s for registry; verify:
docker exec odoo-db psql -U odoo -d odoodemo -c "SELECT name,state FROM sale_order WHERE name='S00023';"
# RPC: http://localhost:8069  db=odoodemo  login=admin  pw=admin  (xmlrpc, allow_none=True)
# re-run the folds (engine reproduces from the TRACKED oracles — no live Odoo needed for replay):
node scripts/poc_odoo_fold.js && node scripts/poc_odoo_fold_3way.js && node scripts/poc_odoo_fold_partial.js && node scripts/poc_odoo_fold_f8.js
```
Driven records already in the instance: SO `S00023` (sell), PO `P00011` (3-way full), `P00012` (f7 partial),
`P00013` (f8 bill≠receipt). Teardown to start clean: `docker rm -f odoo odoo-db` then re-create per
`build/erp/odoo_fold.log` launch recipe. The iDempiere `postgres` container is SEPARATE — never touch it.

## ▶ ARTIFACTS (tracked unless noted)
- Adapter: `scripts/odoo_adapter.js` (SCHEMA_MAP 8 models + STATE_MAP + buildEvents/buildBuyEvents/buildPayPartEvents + resolveAccounts/buildDerivedPost).
- Engine: `scripts/erp_engine.js` `match()` — opt-in `opts.partial=true` partial-quantity matcher (f8 fix).
- RPC drivers (JSON-RPC): `scripts/drive_odoo_paypart.py` (partial-payment chain) · `scripts/drive_odoo_acctcfg.py` (f1 determination config).
- Runners: `scripts/poc_odoo_fold{,_3way,_partial,_f8,_paypart,_acctderiv}.js`.
- Oracles (static, §0.12): `build/erp/odoo_oracle.json` + `odoo_oracle_p2p{,_partial,_f8}.json` + `odoo_oracle_paypart.json` + `odoo_oracle_acctderiv.json`.
- SAP (skeleton, gated): adapter HYPOTHESIS `scripts/sap_adapter.js` · gated runner `scripts/poc_sap_fold.js` · oracle template `build/erp/sap_oracle.template.json` · log `build/erp/sap_fold.log`.
- Witness log (gitignored build artifact — regenerate by running the runners): `build/erp/odoo_fold.log`.
- Docs updated to witnessed state: `PROGRESS.md`, `docs/HolyGrail.md` §migration, `docs/ERPMaker.md`.

## ▶ DEFINITION OF DONE for this path
Odoo: f8 fix landed (PASS, no regression) + partial-payment folded + (optional) f1 account-derivation;
all bounds either folded or NAMED with classification (verb / matcher-behaviour / adapter-data). Then SAP
begins in its own session against a REAL source — never before. Update PROGRESS + HolyGrail scope each step.
