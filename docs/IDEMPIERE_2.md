/**
 * BIM OOTB / ERP OOTB — iDempiere 2.0: the engine-as-data model synthesis.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */

# iDempiere 2.0 — the model, learning from the best

> **Status: DRAFT / SPEC, internal working concept, far-off, grail-gated.** "iDempiere 2.0"
> is an *internal* name for a lineage, not a shipped product name (see §Guardrails — trademark).
> This doc owns the **model / engine** layer. The **UI / renderer** layer is owned by
> [IDEMPIERE_RENDERER_SPEC.md](IDEMPIERE_RENDERER_SPEC.md) — this doc defers all rendering to it.
> Grounding: [HolyGrail.md](HolyGrail.md) · [ERP.md](ERP.md) · [DistributedERP.md](DistributedERP.md) ·
> [ERPMaker.md](ERPMaker.md) · [AnyAppMaker.md](AnyAppMaker.md).

## What it is — and what it is NOT

iDempiere 2.0 is **what iDempiere reached for and could not get to from inside Java**: the engine
as *data*, not code. iDempiere 1.0 proved the *idea* that an ERP can be metadata-driven (the
Application Dictionary, `AD_Rule`/`AD_Val_Rule`); it could not complete it because the
deterministic core was welded to the JVM / OSGi / `trx` / side-effects ([HolyGrail.md](HolyGrail.md)).
2.0 finishes the idea by re-basing that core onto a signed op-log and a small verb set.

| iDempiere 2.0 IS | iDempiere 2.0 is NOT |
|---|---|
| the **thin, general** core: 5-table bridge + verbs + signed op-log; state = the fold | iDempiere's 925-table schema, v2, with more tables bolted on |
| an **abstraction** that iDempiere 1.0 is one *instance* of | a fork or successor of the iDempiere codebase |
| derived from the **domain** (double-entry 1494, the universal document lifecycle) | derived by copying any ERP's source or schema |
| a lingua franca other ERPs map onto (the pivot) | a cage every ERP must distort itself to fit |

The single invariant: **keep the core thin and general.** Every feature pressure tests it. The day
the core stops being "verbs + a thin bridge + a log" and becomes "a big schema," 2.0 has rotted back
into 1.0's bloat — the exact thing it exists to delete.

## The method — how it learns without copying

The discipline that makes "learn from the best" safe and honest, agreed across the design sessions:

1. **Model the domain from first principles** — as we did with BIM. The source of truth is the
   domain (accounting, the document state machine), not a competitor's model.
2. **Competitor = oracle + gap-checklist, never source.** A foreign ERP reveals a gap ("we have no
   analytic accounting") and supplies the *executed-rows oracle* to verify against ([ERP.md §0.12,
   §0.17](ERP.md)). We then model the general primitive ourselves and diff against their output.
   We learn *behaviour*, never copy *expression* (idea/method is free; source is not — and copying
   GPL/LGPL source would contaminate the MIT corpus).
3. **Absorb, selective, generalize.** When a foreign concept is *more general* than ours, fold it in
   and our old form becomes a special case. When it is ERP-specific cruft, quarantine it as an
   adapter detail. The diff-oracle protects correctness; only this discipline protects parsimony.
4. **BI is a fold projection, not a feature to copy** (see §BI below).
5. **Clean-room identity as well as code** (see §Guardrails).

## The core model (the thin pivot)

Recap of the invariant core (full detail in [ERP.md](ERP.md) / [DistributedERP.md](DistributedERP.md)):

- **State = a fold over a signed, append-only op-log.** No mutable scalar of record; the
  authoritative number is *derived* (`QtyOnHand` = Σ movements; a balance = Σ journal lines).
- **The 5-table bridge** — `documents` / `document_lines` / `journal` + `containers` / `items` —
  the minimal universal shape every ERP folds into.
- **A closed verb set** (`GUARD` / `GENERATE` / `MATCH`, `completeOrder` / `createShipment` /
  `createInvoice` / `allocate` / `post` …, [ERP.md §0.14](ERP.md)) — behaviour as data over a
  trusted alphabet, not arbitrary code. This is also what makes AI authoring and the marketplace
  sandboxable ([AnyAppMaker.md](AnyAppMaker.md)).
- **The signed hash-chain** (W-CHAIN / W-SIGN) — tamper-evident, replayable, reversible (reversal =
  appending the inverse op-group), period-close = balance-brought-forward checkpoint.
- **Rules as data** ([ERP.md §0.5, §0.10](ERP.md)) — per-cell decision tables, not Rete/DSL; the
  host language (JS) *is* the rule language; editing a rule edits the running system (the grail,
  [ERP.md §0.4](ERP.md), gated behind E3/E4).

## What it learns from the best — the synthesis

Each row: the idea worth taking, its **generalized** form in 2.0, and how it is verified. Concept
absorbed, never code.

### From Odoo
- **Polished view types** (form / list / kanban / pivot / graph / calendar) → in 2.0 these are
  *fold projections*, not a separate BI engine (§BI). *Renderer concern → defer to RENDERER_SPEC.*
- **Line-level reconciliation** → adopt as the **general** allocation primitive; iDempiere's
  header allocation (`C_AllocationHdr`) becomes a *projection* of it. (The §0.17/§0.19 matcher
  finding — generalize, don't special-case.)
- **Analytic accounting** (cost/profit dimensions orthogonal to the chart of accounts) → a genuinely
  general idea: journal lines carry orthogonal *dimension tags*; reporting folds by any dimension.
  Absorb as a general tagging primitive on `journal`.
- **App modularity / "install an app"** → the OOTB **bundle / genome** model ([AnyAppMaker.md](AnyAppMaker.md)).
- **Studio-style low-code customization** → in 2.0 this IS the grail (edit rules live), not a bolt-on.

### From ERPNext / Frappe
- **DocType — everything is metadata** → the cleanest external proof of the engine-as-data thesis;
  validates the dictionary/descriptor approach (and makes ERPNext the *easiest* future spoke).
- **Minimal lifecycle** (`docstatus` ∈ draft / submitted / cancelled) → confirms the transition
  table generalizes: a 3-state machine is a degenerate case of the DocAction corpus.
- **"Everything is a document"** uniformity → reinforces the documents/lines/journal core.

### From SAP (the asymptote — learn the rigor, copy nothing)
- **The document principle / everything posts** → the ledger-first discipline; the fold is the GL.
- **Document flow** (the linked-document chain view) → 2.0 already *has* this for free: it is the
  op-log lineage graph (Glassbowl renders it; Time Machine plays it).
- **Org-structure modelling** (company code / plant / storage) → generalize to an org+location
  hierarchy — which meets BIM's **spatial location**, the isolation primitive of the distributed
  model ([DistributedERP.md](DistributedERP.md) §2). Where atoms have a location, contention dissolves.

### From the world at large
- **Git** → the doctrine's own analogy: signed, hash-chained, forkable, host-disposable. 2.0's
  op-log is git for ERP transactions ([DistributedERP.md](DistributedERP.md) §0).
- **Event sourcing / CQRS** → named prior art for "state = fold over an event log; read projections
  separate from the write log." 2.0 is this, with signing + a closed verb set added.
- **Double-entry bookkeeping (1494)** → the original "the fact is a fold." The deepest first principle.
- **Local-first software / CRDTs** → offline-first, sync-at-business-time ([LocalFirstPriorArt.md](LocalFirstPriorArt.md)).
- **The spreadsheet** → the most-adopted business tool ever; its lesson is *direct manipulation +
  immediate recalculation + the user is the programmer.* The grail + "make the job as play" channel this.
- **LLVM / SQLite / the web platform** → the positioning: be *infrastructure / a grammar* others
  build verticals on, not an app.

## BI as a fold projection — the gift

Odoo's real value-add beyond the ERP core is its presentation/BI layer. In 2.0 that is **not a
feature to reproduce** — it falls out of the architecture, because state is already a `GROUP BY`
over the ledger:

- a **pivot** is a fold projection; a **dashboard** is a saved fold; a **kanban** is a fold grouped
  by a status column; a **report** is a fold with a filter.

The data is already ledger-shaped for aggregation — arguably *more* natural for BI than a mutable-row
store that bolts BI on top. Seeds exist (`ad_charts.js`, the Glassbowl data-bars + gravity ranking).
We derive views from the fold; we do not copy widgets. *(Rendering of these views → RENDERER_SPEC.)*

## The pivot architecture — migration Switzerland

2.0 as lingua franca: every ERP maps bidirectionally to the thin core via one **model dictionary**
each. Consequences:

- **N dictionaries, not N²** — iDempiere↔2.0, Odoo↔2.0, ERPNext↔2.0 — never every pair directly.
- **An exit path / portability layer** — iDempiere→2.0→Odoo and back; you are the neutral migrator,
  not "replace your ERP." Far less threatening to communities ([ERPMaker.md](ERPMaker.md) migration row).
- **Honest limit:** reading *out* (extract) is proven; writing *back in* to a live foreign ERP is a
  separate write-adapter, and round-trips are **verified with named loss**, not lossless — the five
  structural divergences (`build/erp/odoo_fold.log`) bound it. A migration bridge, not a transparent sync.

## Boundaries — what this doc does NOT cover

- **UI / rendering** → [IDEMPIERE_RENDERER_SPEC.md](IDEMPIERE_RENDERER_SPEC.md) (other session).
- **The engine↔UI interface** → [ENGINE_CONTRACT.md](ENGINE_CONTRACT.md) — the single seam
  (`read`/`dispatch`/`manifest`/`verbs`/`verify` + role context); engine-owned, renderer-referenced.
- **The live write-loop / editable rules reflowing records** → grail, gated behind E3/E4 ([HolyGrail.md](HolyGrail.md)).
- **Full feature breadth parity** → a campaign, one diff-oracle per cell ([ERP.md §0.17](ERP.md)); 2.0
  ships the *durable, ownable core + an exit path*, not a clone of any ERP's full app suite.
- **Per-customer migration** (their data + Z-customizations) → per-engagement; the method generalizes,
  the customizations do not come free.

## Guardrails (doctrine, not preference)

1. **Keep the pivot thin — generalize, don't accrete.** Each absorbed gap must reveal a *more general
   primitive* (fold it in) or be *quarantined* as an adapter detail. No per-ERP cruft in the core.
2. **Competitor = oracle + checklist, not source.** Clean-room: learn behaviour, never copy code.
   Idea/method is free; expression (their source/schema-as-written) is not — and copying copyleft
   source would contaminate the MIT moat.
3. **Clean identity too.** "iDempiere 2.0" is an *internal* concept name; ship under our own mark.
   Reference other ERPs only in factual interop language ("migrates from / compatible with"), never
   adopt their logos. Get IP counsel before any branding that touches another project's name/logo.
4. **BI is a projection, not a feature.** Derive views from the fold; don't reproduce widgets.
5. **Witness everything.** No parity claim without a `§`-logged diff-oracle to the cent. Under-promise
   exactness; lead with witnessed flows.

---

*Status: DRAFT v0.1, 2026-06-02 — model/engine synthesis. Sibling (UI): IDEMPIERE_RENDERER_SPEC.md
(in progress, other session). Every "extract" claim here is witnessed in a dated log or marked
aspirational; nothing is asserted that the source data does not support. Grounding: HolyGrail.md ·
ERP.md §0.4/§0.5/§0.10/§0.12/§0.14/§0.17/§0.19 · DistributedERP.md §0/§2 · LocalFirstPriorArt.md ·
ERPMaker.md · AnyAppMaker.md · build/erp/odoo_fold.log.*
