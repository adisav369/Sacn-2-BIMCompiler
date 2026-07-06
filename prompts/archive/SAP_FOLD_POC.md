# ⚠ DO NOT REMOVE — Scope guard
# Scope: a SANDBOX, headless §-witness POC that tests the migration-solvent claim at its ASYMPTOTE — SAP.
#        Same thesis as the Odoo fold (docs/HolyGrail.md §migration solvent), but SAP is the HARD case and the
#        prompt is honest about WHY: SAP is closed ABAP (no readable source — ORACLE-ONLY), its oracle (IDES) is
#        licensed/hard to obtain, and customer Z-code is per-engagement, out of scope. The POC proves (or bounds)
#        the fold for the STANDARD SD order-to-cash + FI posting flow ONLY, from EXPORTED tables.
# NON-NEGOTIABLE: Spec-first; witness-led; §-log first; Log Mandate; deterministic (the oracle is STATIC exported
#        SAP rows — no live SAP at replay; ids/ts/amounts are recorded INPUTS).
# HONEST FRAME — read this twice: the extract-only method does NOT need SAP's source. It needs SAP's *output* (the
#        executed table rows) to verify the verbs reproduce it. Odoo gave us source+oracle; SAP gives us ORACLE ONLY,
#        so the adapter is built BLIND from output. That is the real difficulty here — not whether the model fits.
# Read first: prompts/ODOO_FOLD_POC.md (the sibling, easier case — do that FIRST) · docs/HolyGrail.md (migration
#        solvent, the SAP boundary: "standard flows + extractable config; Z-customisations per engagement") ·
#        docs/ERP.md §0.13 (the two spines — derivation/settlement) / §0.17 (diff-oracle, contained-set) / §0.12
#        (static oracle) · scripts/diff_oracle.js (the harness to mirror) · scripts/erp_engine.js (verb set).

---

# SAP Fold — Sandbox Migration POC (the asymptote test)

## Why SAP is the asymptote — and why the model nonetheless fits
The asymptote is NOT that SAP's model is alien — it is that the **oracle is costly** (IDES is licensed), the
**adapter is built blind** (no ABAP source, only output), and **Z-code is per-engagement** (excluded here). Ironically
the *model fits unusually well*: SAP S/4HANA's **ACDOCA (the Universal Journal)** is already a single, append-style
line-item journal — i.e. *the fold over a sequence*, the exact shape of our `journal` + op-log; and **VBFA (the
document flow)** is the **derivation spine** (§0.13) made an explicit table. So the test is sharp: a system whose own
post-2015 redesign converged on "one journal + an explicit document-flow graph" SHOULD fold cleanly — does the
STANDARD flow, from exported rows, reproduce under our verbs, or does even the standard flow need invention?

## Step 0 — the oracle (the gating difficulty; resolve before any code)
Obtain ONE static export of a completed STANDARD SD order-to-cash chain (no Z-modifications). Source options, in order
of preference: a partner/sandbox SAP IDES export; an S/4HANA trial; or a documented standard-flow dataset. Export the
rows of one chain to `build/erp/sap_oracle.{db,json}`:
- **SD:** sales order `VBAK`/`VBAP` → delivery `LIKP`/`LIPS` (goods issue) → billing `VBRK`/`VBRP`.
- **FI:** accounting documents `BKPF`/`BSEG`, and in S/4HANA the **`ACDOCA`** universal-journal lines.
- **Document flow:** `VBFA` (the predecessor/successor graph) — the explicit derivation spine; and **`BSEG`/`BSAD`
  clearing** for settlement (the payment/clearing = our ALLOCATE/MATCH).
- Log: `§SAP-ORACLE order=<VBELN> delivery=<VBELN> billing=<VBELN> acdoca_lines=N clearing=<BELNR> rows=N`.
- (Table/field names are the STARTING MAP; the coder VERIFIES against the actual export — do not trust this list.)
- **If no oracle can be obtained, STOP and report `§SAP-ORACLE unavailable` — do NOT fabricate SAP rows.** A POC with
  no oracle is not a POC; an honest "blocked on oracle access" is the correct outcome, not invented data.

## Step 1 — the adapter (blind, from output only)
`scripts/sap_adapter.js` — pure mapping, inferred from the exported rows (no ABAP to read):
- **State/flow map:** SAP processing-status + `VBFA` edges → the generic transition cells. Order→delivery =
  CREATE_SHIPMENT; delivery goods-issue = the stock movement; billing = CREATE_INVOICE + POST; `BSEG` clearing =
  ALLOCATE/MATCH. (SAP status lives in status objects `JEST`/`JSTO` + doc fields — infer from the data.)
- **Schema map:** `VBAK/VBAP`→documents(SALES_ORDER)+lines; `VBRK/VBRP`→documents(AR_INVOICE)+lines;
  `BKPF/BSEG`/`ACDOCA`→`journal`; `MSEG`/goods-issue→items movements. Output = the kernel/CRUD op shape.

## Step 2 — fold + verify (mirror diff_oracle.js)
Drive the adapted ops through the EXISTING verbs; diff the canonical projection against the SAP oracle (effect-set
multiset diff): billed amount, the FI posting lines (debit/credit), the clearing/allocation, delivered qty.

## Witnesses (§-log first)
- `§SAP-FOLD chain order→delivery→billing→fi-posting→clearing mapped=5/5 missing=0` — adapter maps every hop (VBFA-led).
- `§SAP-FOLD verbs used=[…] newVerbs=[…]` — name any verb the STANDARD SAP flow needs that iDempiere/Odoo did not.
- `§SAP-FOLD diff matched=K missed=0 extra=0 vs sap_oracle agree=Y` — canonical effects reproduce the SAP-executed
  result, INCLUDING the FI posting (the ACDOCA/BSEG debit-credit lines), to the cent.
- `§SAP-FOLD acdoca-as-fold lines=N reproduced=Y` — the SPECIFIC, strong claim: our `journal` fold reproduces SAP's
  Universal-Journal lines (the "ACDOCA is already our shape" hypothesis, confirmed or refuted on real rows).
- `§SAP-FINDINGS …` — every divergence NAMED (e.g. SAP condition technique for pricing; new-GL parallel ledgers;
  account determination; CO-PA) — and explicitly which are *standard* (in scope) vs *Z/config* (out of scope).

## Acceptance / the falsifier (and the honest boundary)
- **Strong result:** `newVerbs=[]`, `diff agree=Y`, `acdoca-as-fold reproduced=Y` — the standard SAP O2C+FI folds with
  the existing verbs + a blind adapter. (This would be the single strongest external validation of the thesis, because
  it is the asymptote.)
- **Bounded result (still valuable):** a NAMED set of standard-flow verbs/handlers is required — report which, and
  whether adapter-shaped (data) or genuine new behaviour (code). Update HolyGrail's SAP boundary with the real scope.
- **Out of scope, stated, not hidden:** customer Z-code, the SAP condition/pricing engine internals, CO-PA, parallel
  ledgers beyond the leading ledger. The claim is and remains "standard flows + extractable config; Z per engagement."

## Guardrails
- Do the **Odoo fold first** (`ODOO_FOLD_POC.md`) — it de-risks the adapter pattern on an open system before the blind one.
- Reuse the verb set + `diff_oracle` harness; the engine does not change — only `sap_adapter.js` is new.
- Non-invent, hard rule: NO synthesised SAP rows. Absent value → "absent in dataset". No oracle → `§SAP-ORACLE
  unavailable` and stop. Static oracle only; deterministic replay.
- HANDS-OFF the live CRUD/glassbowl files. NO deploy.

## Status
- SPEC + SKELETON PREPARED, 2026-06-03. The asymptote test; gated on oracle access (the real blocker). Sibling: `prompts/ODOO_FOLD_POC.md` (DONE — 6 chains folded).
- **Step 1 done (clean-room, blind): `scripts/sap_adapter.js`** — the SAP↔iDempiere schema/state-map HYPOTHESIS
  (VBAK/VBAP→C_Order, LIKP/LIPS→M_InOut, VBRK/VBRP→C_Invoice, BKPF/BSEG|ACDOCA→journal, VBFA=derivation spine,
  BSEG/BSAD clearing=ALLOCATE/MATCH), `normalizeGLLine` (SHKZG/DRCRK S/H → amtacctdr/amtacctcr), `buildSapEvents`
  (VBFA-led), 6 NAMED_DIVERGENCES pre-classified (standard vs Z/out-of-scope). Every field is TO-VERIFY on a real export.
- **Step 2 runner done + GATED: `scripts/poc_sap_fold.js`** — no `build/erp/sap_oracle.json` → prints the hypothesis +
  planned witnesses and STOPS at `§SAP-ORACLE unavailable` / `§SAP-FOLD BLOCKED` (exit 0, graceful; no fold claimed,
  no rows fabricated). Activates automatically when a real oracle (shape: `build/erp/sap_oracle.template.json`) drops in.
- **Step 0 BLOCKED:** no IDES/S/4HANA export obtained yet — the real blocker. Witness `build/erp/sap_fold.log`.
- Coder (next session): obtain a real export → fill `sap_oracle.json` from it (NON-INVENT) → re-run → produce the
  `§SAP-FOLD` verdict + a `# DONE` ledger. No deploy.
