# ERP Rosetta Stone — iDempiere (Java/ORM) → the Fold Engine

> **What this is.** A translation table for a legacy iDempiere/Compiere developer. You know
> `PO.get/set/save`, `Query`, `MOrder.completeIt()`, `Doc_Order`. This page maps each idiom to its
> exact counterpart in the browser **Fold Engine** (`build/erp/*.js` + the `kernel_ops` log), so you can
> read the new code against the mental model you already have.
>
> **Not** the BIM [Red Pill Rosetta](RedPillRosetta.md) (that one is about pristine-IFC re-model truth).
> This is the *ERP dictionary*: iDempiere Java ⇄ AD-as-data fold.
>
> **Companions:** [Coverage Matrix](ERP_COVERAGE_MATRIX.md) (which surfaces are proven equivalent) ·
> [Fold-Engine Code Quality](FoldEngineQuality.md) (how the witnesses are graded) ·
> [Migrate & Compare](MigrateComparisonPaper.md) (the thesis & honesty panel).

---

## 1. The one mental shift (read this first)

| | iDempiere (today) | Fold Engine |
|---|---|---|
| **System of record** | the **database** — rows are the truth, updated in place | the **op-log** (`kernel_ops`) — an append-only, signed, hash-chained list of operations |
| **The SQLite tables** | *are* the truth | are a **projection** — re-materialised by *folding* the op-log |
| **A write** | mutate a `PO`, `save()` runs `INSERT/UPDATE` | **emit a signed op**; the fold rewrites the row |
| **A read** | `PO`/`Query` over the live tables | **plain SQL** over the folded projection |
| **The model class** | generated `X_*` Java per table | **none** — the AD metadata + one interpreter replace it |

So: **`get()` reads the projection. `set()+save()` becomes "append an op."** The database is no longer
where truth lives — it is the folded *view* of the log. This is event sourcing / CQRS (see §7) applied to
the iDempiere model. Nothing exotic; the novelty is *which* model gets folded.

---

## 2. The Rosetta table — your idioms, one-to-one

| Legacy iDempiere Java | Fold Engine (SQLite/JS) | Where |
|---|---|---|
| `MOrder o = new MOrder(ctx, id, trx)` | `db.exec('SELECT * FROM c_order WHERE c_order_id=?',[id])[0]` — no object wrapper required | the folded projection |
| `o.getC_BPartner_ID()` / `o.get_Value("…")` | `row.c_bpartner_id` — read the column off the result row | — |
| `o.setC_BPartner_ID(123)` | stage on the entry, but the durable form is an op: `CRUD_UPDATE {table:'C_Order', id, set:{C_BPartner_ID:123}}` | `crud_overlay.js` |
| `o.save()` (validate → beforeSave → INSERT/UPDATE → afterSave) | `KernelOps.commitOp(db,'CRUD_UPDATE'\|'CRUD_CREATE', params)` → op appended (signed+chained), fold applies it. Field rules (type/readonly/required/default/valrule) gate **before** apply; `AdModelVal.fireHooks('BEFORE_SAVE',…)` runs the validators | `kernel_ops.js`, `crud_overlay.js`, `ad_modelval.js` |
| `new Query(ctx,"C_OrderLine","C_Order_ID=?",trx).list()` | `db.exec('SELECT * FROM c_orderline WHERE c_order_id=?',[id])` — you write the SQL | — |
| TableDir / Search FK lookup (`MLookup`) | `AdReference.readRefTable(refId)` → FK table+key; `AdReference.fkExists(refId,id)` → membership; `AdValRule` applies the `@token@` WHERE filter | `ad_reference.js`, `ad_valrule.js` |
| Field callout (`CalloutOrder.product/.amt`) | `AdCallout` dispatch — `class.method` → JS registry, fires on field change → derived siblings | `ad_callout.js` |
| `o.processIt(ACTION_Complete)` / `MOrder.completeIt()` | a fold recipe: `KernelOps.commitGroup(db, [DOC_ACTION CO, {POST}, …childOps])` — one signed op-group, whole-or-none | `erp_engine.completeIt`, `kernel_ops.commitGroup` |
| `new MInOut(...).save()` / `new MInvoice(...).save()` inside `completeIt` | `buildDoc('M_InOut',…)` / `buildDoc('C_Invoice',…)` — **the same parametric verb**, recursed (not a class each) | `erp_engine.buildDoc` |
| `Doc_Order.createFacts(MAcctSchema)` (posting) | `post_resolver` / `postRecipe('C_Order',order,lines)` → DR/CR derived from AD config, ΣDR==ΣCR | `erp_engine.js`, `post_resolver.js` |
| `MFactAcct` rows | `fact_acct` rows in the projection, produced by the `POST` op's fold | — |
| `reverseCorrectIt()` / `voidIt()` | `reversePosting` pure verb (swap Dr↔Cr) inside a `DOC_ACTION` group; FSM CO→RE | `erp_engine.reversePosting`, `ad_docfsm.js` |
| `MStorageOnHand` current qty | `qtyOnHand` = `Σ(movementSign(type)×|qty|)` folded from `m_transaction` | `erp_engine.qtyOnHand/movementSign` |
| Model Validator (`ModelValidationEngine`) | `AdModelVal.fireHooks(timing,…)` — BEFORE/AFTER × NEW/SAVE/DELETE + PREPARE/COMPLETE/VOID | `ad_modelval.js` |
| DocAction state machine (`DocumentEngine`) | `AdDocFsm.legalActions / transition` (data-driven FSM) | `ad_docfsm.js` |

**Real op-types the kernel accepts** (so you can read a log): `CRUD_CREATE`, `CRUD_UPDATE`, `CRUD_DELETE`
(the `PO.save` grain) · `CREATE_DOCUMENT`, `CREATE_LINE` (`buildDoc`) · `DOC_ACTION`, `SET_STATUS` (doc
actions) · `POST` (posting) · `CONSUME`, `ENACT_MOVE` (inventory/production).

---

## 3. Worked example — `completeIt` line-by-line

Your legacy call:

```java
MOrder o = new MOrder(ctx, C_Order_ID, trx);
o.processIt(DocAction.ACTION_Complete);   // MOrder.completeIt(): validators → create MInOut + MInvoice → Doc_Order posts
o.save();
```

The Fold-Engine equivalent (this is real engine shape — see `scripts/poc_fold_complete.js`, W-FOLD-COMPLETE):

```js
if (CrudOverlay.docActionOutcome(entry, order).to !== 'CO') return { status };       // FSM: is CO legal here?
if (!AdModelVal.fireHooks('BEFORE_COMPLETE', { … }).ok)      return { blocked };      // the SAME validators
const childOps = [ ...buildDoc('M_InOut',   …),                                       // ship  ┐ same verb,
                   ...buildDoc('C_Invoice', …) ];                                     // invoice┘ recursed
const post  = postRecipe('C_Order', order, lines);          // DR/CR derived from AD config, ΣDR==ΣCR
const group = [ { DOC_ACTION:'CO' }, { op:'POST', post }, ...childOps ];
return KernelOps.commitGroup(db, group);                    // ONE signed, hash-chained op-group (whole-or-none)
```

What maps to what:

- `o.processIt` legality → `CrudOverlay.docActionOutcome` (the `ad_docfsm` legal-action set). iDempiere's
  `DocumentEngine.getValidActions` parsed at runtime; **proven equivalent** (W-MORDER-FSM).
- `MOrder.beforeSave` / doc validators → `AdModelVal.fireHooks` — a non-null error **aborts**, same contract
  as `save()` returning false (W-MODELVAL, W-MORDER-SAVE: `MOrder.java:1183-1396` as 11 cited hooks).
- `new MInOut(...).save()` + `new MInvoice(...).save()` → `buildDoc(...)` **twice** — one parametric verb, not
  two model classes. The fan-out lines == real `m_inoutline` / `c_invoiceline` (W-FOLD-COMPLETE).
- `Doc_Order.createFacts` → `postRecipe` — the invoice journal == `fact_acct(318)` to the cent, `maxDiff=0c`.
- `o.save()` (one JDBC commit) → `commitGroup` (one **signed** op-group). Either *all* of CO+POST+ship+invoice
  fold, or none — the torn-group gate (W-CHAIN). iDempiere gives you a DB transaction; the Fold Engine gives
  you a transaction **plus** a tamper-evident, signable, distributable record of *what* happened.

---

## 4. The ORM question — there is no `X_*`

In iDempiere, `X_C_Order` is that table's rules **compiled into Java**: typed getters/setters, FK references,
`beforeSave`. Change them → regenerate the class → redeploy.

The Fold Engine keeps those same rules as **AD data** and interprets them with one engine:

| What the `X_*`/`M*` class carried | Where it lives now (as data) |
|---|---|
| column type / default / readonly / mandatory | `ad_column` (+ `ad_field` display) → `crud_overlay` |
| FK reference / lookup | `ad_reference` (`readRefTable`/`fkExists`) |
| lookup filter (validation rule) | `ad_val_rule` (`ad_valrule` token-substitute + WHERE) |
| field-change derivation (callout) | `ad_callout` |
| `beforeSave`/`afterSave` invariants | `ad_modelval` timing hooks |
| DocAction state machine | `ad_docfsm` |

**Consequence:** no codegen, no column-sync, no app-server restart. Editing the dictionary is a *data* edit;
the renderer re-paints from the rows. (The one cost: a genuinely new field needs a real SQLite column to hold
its value — still data, still no codegen.) Each of these interpreters is **oracle-diffed to `diff=0`** against
the live iDempiere Postgres — see the Coverage Matrix `W-*-HARDEN` rows.

---

## 5. Write path in detail — why "save" became "append"

```
set field ─▶ CrudOverlay (AD_Column rules) ─▶ AdModelVal BEFORE_* hooks ─▶ commitOp/commitGroup
                                                                              │
                              append to kernel_ops (op_uuid, op_type, params) │  ← signed (W-SIGN)
                              prev_hash / op_hash = SHA-256(prev | op)        │  ← chained (W-CHAIN)
                                                                              ▼
                                                          FOLD ─▶ SQLite projection rows
```

- **Atomic group:** `commitGroup` wraps N ops in one SQL transaction; an `expectedHash` mismatch commits
  **nothing** (torn-group rejection).
- **Distribution:** because the log is append-only + signed, an admin's change propagates by **shipping the
  appended ops** ("mail the log"); another node replays/folds them to the identical state. The op-log *is* the
  wire — there is no server of record. *(End-to-end AD-distribution demo is an owed witness:
  `W-AD-OPLOG-DISTRIB`, see `FRONTEND_LANE_MASTER.md §OUTSTANDING`.)*

---

## 6. Read path — it's just SQL

There is no query DSL to learn. The folded projection is ordinary SQLite, so a legacy `Query` becomes the SQL
you already know:

```js
// iDempiere: new Query(ctx,"C_Invoice","IsPaid='N' AND C_BPartner_ID=?",trx).setParameters(bp).list()
db.exec("SELECT * FROM c_invoice WHERE ispaid='N' AND c_bpartner_id=?", [bp]);
```

Lookups resolve through the AD reference layer (§4) rather than `MLookup`, but the underlying read is the same
`SELECT … WHERE … IN (…)`.

---

## 7. Prior art — what's standard, what's ours

The substrate is well-trodden; cite it, don't reinvent it:

- **Martin Kleppmann — "Turning the Database Inside Out"** ([video](https://www.youtube.com/watch?v=fU9hR3kiOK0) ·
  [transcript](https://martin.kleppmann.com/2014/09/18/turning-database-inside-out-at-strange-loop.html)) — the
  database as a *derived view of an append-only log*. Our `kernel_ops` → SQLite projection, exactly.
- **Greg Young — CQRS & Event Sourcing** ([transcript](https://www.kurrent.io/blog/transcript-of-greg-youngs-talk-at-code-on-the-beach-2014-cqrs-and-event-sourcing)) —
  append-only, immutable, deterministic replay with an audit log. Our signed op-group + fold.
- **Rich Hickey — "The Database as a Value"** ([video](https://www.infoq.com/presentations/Datomic-Database-Value)) —
  *accumulate facts, don't update places*; query as of any point in time. Our fold-to-any-point + history scrubber.
- **Martin Fowler — [Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html) ·
  [CQRS](https://martinfowler.com/bliki/CQRS.html)** — the short written reference.

**What's standard:** event sourcing, CQRS, the immutable log, the projection. **What's ours:** folding
iDempiere's `M*` model — `completeIt`, `Doc_*` posting, the DocAction FSM, the AD interpreters — to
*oracle-equivalence* (`maxDiff=0c` vs real iDempiere output), serverless, in a browser. We stood on proven
ground; the ERP fold is the new work. See the [Coverage Matrix](ERP_COVERAGE_MATRIX.md) for the exact tally.
