# ACDOCA Fold Plan — the SAP headline

> **The marquee claim, stated once.** *The Fold Engine folds SAP S/4HANA's Universal Journal (ACDOCA) into the
> same canonical model it folds iDempiere and Odoo into — to the cent, in a browser, with no HANA.* This page is
> the **doctrine and the plan** for that headline: defined ahead of the folding engineers, so the work behind it
> follows a fixed target, not a moving one.
>
> **Companions:** [Cross-ERP Rosetta Stone](ERPConceptRosetta.html) (the term matrix) ·
> [ERP Rosetta Stone (dev idiom)](ERPRosettaStone.md) · [HolyGrail](HolyGrail.md) (the migration solvent,
> the SAP boundary) · [Coverage Matrix](ERP_COVERAGE_MATRIX.md). **Execution card:** `prompts/SAP_FOLD_POC.md`
> (the headless witness POC) + `scripts/sap_adapter.js` (the blind SD→FI hypothesis already mapped to the six verbs).

---

## 1. Why ACDOCA is the headline (and not a stretch)

Every other migration target makes you *argue* that one universal journal is the right shape. **SAP already agreed.**
In the 2015 S/4HANA redesign SAP collapsed FI (`BSEG`), CO, Material Ledger and Asset Accounting into **one
line-item table — `ACDOCA`, the Universal Journal** — and made document lineage an explicit graph, **`VBFA` (document
flow)**. That is *exactly* the engine's own shape:

| SAP S/4 converged on | The Fold Engine has always been |
|---|---|
| **ACDOCA** — one append-style universal line-item journal | `journal` / `fact_acct` — one books table every doc posts into |
| **VBFA** — document flow as an explicit derivation graph | `kernel_ops` chain — the signed op-log, lineage by construction |
| Universal posting (one journal, many sources) | the closed verb set — `POST` folds every doc class into one journal |

So the test is **sharp, not lucky**: a system whose own engineers concluded "the future is one journal of effects +
an explicit flow graph" *should* fold cleanly under a model built on that premise. The headline writes itself —
**"we fold the Universal Journal SAP bet the company on"** — and it is falsifiable: if even SAP's *standard* flow
needs a verb outside the six, `newVerbs` will say so, loudly.

---

## 2. The fold target — ACDOCA → the canonical model

The standard Sell-side chain and its posting, mapped onto the canonical column (see the
[Cross-ERP Rosetta](ERPConceptRosetta.html) for the full term grid):

```
VBAK/VBAP  →  C_Order        (sales order)        ┐
LIKP/LIPS  →  M_InOut        (delivery)           │  the document chain — CREATE_DOCUMENT / CREATE_LINE / SET_STATUS
VBRK/VBRP  →  C_Invoice      (billing)            ┘
        ↓ posting
ACDOCA     →  fact_acct / journal                 the books — POST folds DR/CR, ΣDR==ΣCR
SKA1/SKB1  →  C_ElementValue                      the G/L account each line hits
VBFA       →  the kernel_ops chain                lineage / "what produced what" — the derivation spine (§0.13)
```

**The six verbs carry it** (the standing closed set, `newVerbs=[]` the acceptance bar):
`CREATE_DOCUMENT` · `CREATE_LINE` · `SET_STATUS` · `MATCH` (GR/IR → `M_MatchInv`) · `ALLOCATE` (clearing) · `POST`
(→ ACDOCA-shaped journal). `sap_adapter.js` already encodes this hypothesis blind; this plan is the path to
**proving it against real SAP output**.

---

## 3. The SOP, applied to SAP

The universal protocol (unchanged — SAP is a harder *instance*, not a new method):

1. **Re-key** `KNA1`/`LFA1`→`C_BPartner`, `MARA`→`M_Product`, `SKA1`→`C_ElementValue`, the SD docs→`C_Order`/`M_InOut`/`C_Invoice`.
2. **Project** SAP status management (`BSVZ`) onto the `DocStatus` / `C_DocType` FSM frame (`DR→…→CO`).
3. **Classify** each rule by op-log effect — DERIVE / VALIDATE / ACT. Standard customising is declarative → folds; ABAP user-exits/BAdIs are imperative → effect-captured or plugin.
4. **Absorb** the declarative config + the document effects via the six verbs; **route** Z-code (per-engagement custom ABAP) to plugins — never auto-import.
5. **Prove** by diffing the folded journal to **real ACDOCA** to the cent (`maxDiff=0c`), with `§FALSIFIER` and `newVerbs=[]` as the two canaries.

---

## 4. The honest boundary (read twice — the asymptote)

SAP is the **asymptote** of the thesis, and the difficulty is *not* that the model is alien (§1 shows it fits). The
real costs, named so the headline stays honest:

- **The oracle is costly.** Odoo gave us source + a live oracle (`odoodemo`); **SAP gives us output only** — no
  readable ABAP, and a real posted oracle (IDES / a productive S/4) is licensed and hard to obtain. The adapter is
  therefore **built blind from exported table rows**. *(Today: the `/DMO/` Flight reference model is the toe-in — a
  master + lifecycle stub with **no FI**; see `build/erp/14-sap-chain.json`. It proves the DDIC world re-keys into AD;
  it does **not** prove a posting cycle.)*
- **Z-code is out of scope by construction.** Customer ABAP (user-exits, enhancements) is per-engagement and
  imperative → plugin lane, not fold. We fold the **standard** SD→FI flow.
- **Breadth is not parity.** We aim for **one document cycle to the cent**, not "we did SAP." The IMG config sprawl
  is folded only where the standard flow touches it.

A leg that needs one of these is marked ⛔ with the missing fact — never green-washed (the lane discipline).

---

## 5. The plan — R → E → V, one leg at a time

| Phase | Goal | Acceptance (the witness) | Status |
|---|---|---|---|
| **S-0 · Oracle** | Obtain real posted SAP output: `ACDOCA` + the SD chain (`VBAK…VBRP`) + `VBFA`, exported rows (IDES, a productive sandbox, or a vendor extract). | `§SAP-ORACLE present` (today: `§SAP-ORACLE unavailable`, honestly gated). The **gating** leg — resolve before any fold claim. | ⛔ data-blocked |
| **S-1 · Masters** | Re-key `KNA1/LFA1 · MARA · SKA1` into `C_BPartner/M_Product/C_ElementValue`; counts == source. | shard counts == SAP `SE16` counts; §FALSIFIER (short shard → gap detected). | 🟡 stub (`/DMO/` masters only) |
| **S-2 · One O2C cycle** | Fold `VBAK→LIKP→VBRK` and **post to an ACDOCA-shaped journal**; diff to real `ACDOCA`. | **`§ACDOCA-FOLD maxDiff=0c`** vs exported ACDOCA for one billing doc; `newVerbs=[]`; `VBFA` reproduced as the `kernel_ops` chain. **This is the headline witness.** | ⛔ pending S-0 |
| **S-3 · Clearing / reverse** | GR/IR (`MATCH`) + payment clearing (`ALLOCATE`) + reversal annihilate-to-zero. | `maxDiff=0c` vs ACDOCA clearing lines; reversal nets every account to 0. | ⛔ pending S-0 |
| **S-4 · Scorecard row** | SAP added to the [critic scorecard](ERP_COVERAGE_MATRIX.md) on the honest axes — surpass / match / trail, each citing its witness. | one row, no hype. | — |

**The one thing that unblocks everything is S-0** — a real ACDOCA export. Until then the adapter stays a *blind,
falsifiable hypothesis* (`sap_adapter.js`), the `/DMO/` stub holds the master-mapping demonstration, and this plan
states the headline as a **target**, not a claim. The moment S-0 lands, S-2 is the marquee: the Universal Journal,
folded to the cent, serverless.

---

## 6. What a folding engineer does next

1. Read `prompts/SAP_FOLD_POC.md` (the witness POC) + `scripts/sap_adapter.js` (the verb hypothesis) + `scripts/poc_sap_fold.js` (the gated runner).
2. Pursue **S-0**: any path to a real exported ACDOCA + SD chain + VBFA (the artifact, not the system).
3. Fold **S-2** through the existing six verbs; if a seventh is needed, **stop and log it** — that is the discovery, not a failure.
4. Diff to ACDOCA to the cent; add the scorecard row; cross-link back here.

The doctrine is fixed; the folding follows. ACDOCA is not a reach — it is SAP meeting us where the engine already stood.
