/**
 * BIM OOTB / ERP OOTB — ERPMaker: zero-install ERP from your own data.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */

# ERPMaker — the adoption grail

> **Status: SPEC, focused, the feasible first term of [AnyAppMaker.md](AnyAppMaker.md).**
> Read AnyAppMaker for the horizon; this is the bounded product that is buildable now,
> because the rail it needs already exists for ERP.

## The problem it attacks — the first mile, not the engine

No one abandons iDempiere, Odoo, or ERPNext because business rules are not live-editable.
They abandon them at the **install**: PostgreSQL, Java/OSGi or Frappe/bench, Docker,
dependency hell, a server to provision, a consultant to engage. *Time-to-first-running-
screen* is days to weeks. That is where the mass market dies, and it is a different wall
from the one [HolyGrail.md](HolyGrail.md) addresses.

ERPMaker removes that wall: **choose an industry, give your Excel, scan your business
card — get a running ERP, seeded with your own data, as one offline HTML file, in
minutes, with no server.**

## Why narrow beats "any app" — the rail already exists here

The property that makes AI authoring safe is a **closed verb set + a diff-oracle**
([ERP.md §0.5, §0.14, §0.17](ERP.md)). For ERP those are *witnessed today*: the verbs
(O2C / P2P / GL — GUARD, GENERATE, MATCH, allocate, post …) are proven against the
GardenWorld oracle, and the genome (the ERP-OOTB bundle) exists. For an arbitrary app
they do not yet exist. So ERPMaker is the **feasible subset** where the doctrine is
already proven — the first clean abstraction, productized.

## The three inputs are extraction, not invention

Consistent with the PRIME RULE — the user does not *describe* their business, they *show*
it, and we **fold** what they show into the universal 5-table bridge.

| Input | What it seeds | Mechanism |
|---|---|---|
| **Industry** | which genome to fork | selects a pre-witnessed bundle (retail / F&B / services) |
| **Excel** | products, customers, price list, opening balances — **and the schema, and the acceptance oracle** | migration-as-fold ([HolyGrail.md](HolyGrail.md), *the migration solvent*) |
| **B-card scan** | the org / legal-entity master | reuses the existing scan path (W-QR-INPUT) |

**The Excel is the test suite.** Their own historical totals become the witnesses: *"your
invoice #1023 totals $452.10 — the generated app must reproduce it to the cent."* This
dissolves the *safe-but-wrong* risk (a conforming but financially-wrong app), because the
generated ERP is verified against the operator's real books, not imagined samples. The
migration corpus and the acceptance oracle are the same artifact.

## The pipeline

```
industry → fork genome
Excel    → ingest → map (AI-assisted: their columns → dictionary fields)
B-card   → scan → org master
              ↓
         fold facts into the 5-table bridge (op-log)
              ↓
         oracle-gate against the operator's own historical totals (§0.17)
              ↓
         birth: package to offline HTML (S284), signed (W-SIGN)
```

The column-mapping step is the ontology gap surfaced as a spreadsheet — AI-assisted,
error-prone, and **round-trip-checked**: the folded data must reproduce the Excel's own
totals, or the mapping is wrong. Failures return to the user as plain questions in their
own vocabulary ("is this column the unit price or the line total?"), never as traces.

## Why this is the wedge against iDempiere / Odoo / ERPNext

- **Install → zero.** A browser tab, then a downloadable HTML; no Postgres, no Docker, no
  server, no consultant.
- **Demo data → your data.** Not GardenWorld; their own products and balances on first
  open.
- **The cardinal property survives.** Offline, signed, auditable, no runtime dependency
  on the platform ([DistributedERP.md](DistributedERP.md) §0, §6). The platform supplies
  the genome and the maker once (MIT, forkable); after that the operator owns the app.

## Migration targets — the initial two

Source adapters are earned per engine, against a *real* instance, one diff-oracle at a
time. Two are named; their state is asymmetric and must not be overstated.

| Target | State | Evidence / gate |
|---|---|---|
| **iDempiere** | **migrator built + witnessed** | `scripts/migrate_pg_to_sqlite.js` (raw PG→SQLite, byte-identical round-trip) + `scripts/compile_rules.js` (→ `erp_rules.db`) + `scripts/diff_oracle.js` (vs GardenWorld oracle) + `scripts/verify_migration.js` (acceptance gate). Outputs `build/erp/ad_full.db` (925 tbl), `erp_rules.db`. Source: Docker `postgres`/`idempiere`. For ERPMaker this is *wiring*, not building. |
| **Odoo** | **fold WITNESSED (2026-06-03) — `§ODOO-FOLD PASS`, `newVerbs=[]`** | Odoo 17 (`odoodemo`) stood up; one sell-side O2C chain (SO `S00023` → delivery → invoice → GL post → payment → reconcile) driven via RPC to completion, frozen as a static oracle (`build/erp/odoo_oracle.{json,db}`, §0.12). A pure adapter (`scripts/odoo_adapter.js`) folded it through the **existing** verbs: all 5 hops mapped, effects reproduce Odoo to the cent, replay exact (`scripts/poc_odoo_fold.js`, log `build/erp/odoo_fold.log`). Falsifier survived: nothing invented. **Bound (named):** ONE sell-side chain; account-determination is host data (POST owns only ΣDR==ΣCR, §13.1); 3-way `MATCH` + partial reconcile are the next fold. Migrator now unblocked. |

The ordering is the doctrine's, not a preference: **source → falsifier → migrator →
oracle**, never migrator-first. iDempiere is the proven first abstraction; Odoo is the
next campaign term and does not begin until a real instance exists to extract and the
fold is witnessed.

## Relation to the Holy Grail — the other half, not a higher one

[HolyGrail.md](HolyGrail.md) closes with: *"Migration removes the barrier to leaving; the
grail (editable rules, live) supplies the reason to land. Both halves, or the solvent has
nothing to pour into."* ERPMaker **is the barrier-remover, productized** — the adoption
half of the same grail, co-equal with the editable-rules capability half, and the better
*first* product because adoption-failure is the mass-market killer. It is downstream of
the engine-as-data work (it needs the verbs, the fold, the offline womb), so it is not
"higher" technically; it is the half that gets anyone onto the engine at all.

## Honest boundaries

- **Excel is messy.** Multi-tab, merged cells, free-text, no normalization. The
  ingest/mapping is real, bounded work — its correctness is the round-trip check, not a
  guarantee.
- **One genome per industry, pre-witnessed.** Launch quality = how many industry genomes
  are proven against an oracle. Start with one or two you can witness, not a dropdown of
  twenty — the §0.17 breadth campaign, applied to verticals.
- **Migration scope earned one oracle at a time.** iDempiere is tractable (known
  DocAction + GardenWorld oracle); other sources are a campaign in sequence, same as the
  HolyGrail migration boundary.
- **Read/seed can ship before live rule-edit.** ERPMaker delivers a *seeded, running,
  verified* ERP without waiting for the E3/E4 live-edit loop; the editable-rules grail
  lands on top of it later.

## The witness that would prove it

```
§ERPMAKER  industry=retail  ingest=<real_smb.xlsx rows=N>
           fold→bridge  oracle: FY invoices reproduced to the cent  pass=K fail=0
           birth=BIM-ERP-OOTB.html  opens_on=file://  signed=Y
```

Spec-first: this witness is named before code. No claim of "minutes to a running ERP"
until a real SMB spreadsheet folds and reproduces its own books, to the cent, in a
file:// HTML.

---

*Grounding: [HolyGrail.md](HolyGrail.md) (migration solvent) · [ERP.md](ERP.md) §0.5 ·
§0.10 · §0.14 · §0.17 · [DistributedERP.md](DistributedERP.md) §0 · §6 · S284 · W-SIGN /
W-QR-INPUT. Horizon: [AnyAppMaker.md](AnyAppMaker.md).*
