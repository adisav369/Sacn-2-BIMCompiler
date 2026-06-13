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

### Full listing — block-for-block against `org.compiere.model.MOrder.completeIt()`

Every effect is an op appended to the signed log; status and the books are a *fold* of those ops. Commits
whole or none. (This is the listing the [Migrate & Compare paper](MigrateComparisonPaper.md#gap-in-code)
points here for — its home is this dictionary.)

```js
// MOrder.completeIt() folded onto the signed op-log. Java: MOrder.completeIt (~250 LOC, getX/setX/saveEx/SQL).
// Every effect is an OP appended to the log; status & books are a FOLD of those ops. Commits WHOLE or NONE.
async function completeIt(db, order) {
  const lines = getLines(db, order);                                   // C_OrderLine rows — DATA (the X_ layer)
  const entry = descriptorFor('c_order');                             // crud_ops.json field descriptor — DATA

  // [Java] "Just prepare" / re-check → CO iff prereqs met, else InProgress
  const out = CrudOverlay.docActionOutcome(entry, order);            // {action,from,to,outcome,unmet}
  if (out.to !== 'CO') return { status: out.to, unmet: out.unmet };

  // [Java] fireDocValidate(TIMING_BEFORE_COMPLETE) — real MOrder.hasLines / total≥0, first error aborts
  let v = AdModelVal.fireHooks('BEFORE_COMPLETE', { table: 'C_Order', record: order, lineCount: lines.length });
  if (!v.ok) return { status: 'IN', blocked: v.blocked, msg: v.error };

  // [Java] implicit approval
  if (order.IsApproved !== 'Y') order.IsApproved = 'Y';

  // [Java] CO must be legal for this C_DocType at this status, then DR/IP → CO
  if (!AdDocFsm.legalActions(db, order.C_DocType_ID, order.DocStatus).includes('CO'))
    return { status: 'IN', msg: 'CO not legal from ' + order.DocStatus };
  const toStatus = AdDocFsm.transition('CO', order.DocStatus);       // → 'CO'

  // [Java] createCounterDoc / createShipment / createInvoice — the archetype RECURSES (buildDoc = the same verb)
  const childOps = [];
  // TODO (H-1): if (autoGenerateInOut(dt, order))   childOps.push(...(await buildDoc(db,'M_InOut', order,lines,true)).ops);
  // TODO (H-1): if (autoGenerateInvoice(dt, order)) childOps.push(...(await buildDoc(db,'C_Invoice',order,lines)).ops);

  // [Java] posting (Doc_Order at post time): derive DR/CR; accounts resolved from AD acct-config (DATA); ΣDR==ΣCR
  const acct = acctSchema(db, order.AD_Client_ID);
  const post = postRecipe('C_Order', order, lines).map(l => ({
    account_id: post_resolver.resolve(db, l.token, order.id, acct),   // account-token → real account
    amtacctdr:  l.dr || 0, amtacctcr: l.cr || 0 }));

  // [Java] setDocStatus(Completed) + the implicit Postgres txn → ONE signed, hash-chained op-group
  const action = CrudOverlay.buildOp('process', entry, { ...order, DocStatus: toStatus }, order, { id: order.id });
  const group  = CrudOverlay.buildDocActionGroup(action);            // [DOC_ACTION, …]
  group.push({ op_type: 'POST', parameters: { id: order.id, lines: post } }, ...childOps);
  const res = await KernelOps.commitGroup(db, group, { baseTs: order.ts });   // all-or-none
  if (!res.committed) return { status: 'IN', msg: 'torn group: ' + res.reason };

  // [Java] fireDocValidate(TIMING_AFTER_COMPLETE)
  v = AdModelVal.fireHooks('AFTER_COMPLETE', { table: 'C_Order', record: order, lineCount: lines.length });
  if (!v.ok) return { status: 'IN', msg: v.error };

  return { status: 'CO', gid: res.gid, tip: res.tip };               // Completed, signed, replayable
}
```

**Shipped primitives it stands on** (real source — `feat/erp-substrate-phase012`):

- `KernelOps.commitGroup` — atomic all-or-none, hash-chained, signed op-group → [build/erp/kernel_ops.js](https://github.com/red1oon/BIMCompiler/blob/feat/erp-substrate-phase012/build/erp/kernel_ops.js)
- `AdModelVal.fireHooks` — BEFORE/AFTER_COMPLETE validators (port of `fireDocValidate`) → [build/erp/ad_modelval.js](https://github.com/red1oon/BIMCompiler/blob/feat/erp-substrate-phase012/build/erp/ad_modelval.js)
- `AdDocFsm.legalActions` / `transition` — the legal-action FSM (port of `DocumentEngine`) → [build/erp/ad_docfsm.js](https://github.com/red1oon/BIMCompiler/blob/feat/erp-substrate-phase012/build/erp/ad_docfsm.js)
- `post_resolver.resolve` — account-token → real account from AD acct-config → [scripts/post_resolver.js](https://github.com/red1oon/BIMCompiler/blob/feat/erp-substrate-phase012/scripts/post_resolver.js)
- `CrudOverlay.docActionOutcome` / `buildOp` / `buildDocActionGroup` — prepare outcome + op-group staging → [build/erp/crud_overlay.js](https://github.com/red1oon/BIMCompiler/blob/feat/erp-substrate-phase012/build/erp/crud_overlay.js)

**Still to fold** (named, not built — the H-1 work): `buildDoc('M_InOut'/'C_Invoice', …)` auto-ship/auto-invoice
recursion, `createCounterDoc` (intercompany), reservation edge cases, landed cost. Tracked in
`prompts/HARDEN_MATRIX.md`. Witness **W-FOLD-COMPLETE** `maxDiff=0c`.

**What it demonstrates:** (1) **~50 JS vs ~250 Java** — the getX/setX/saveEx, SQL and try-catch boilerplate
drops; only the business decisions remain. (2) **"MOrder + deltas" is right here** —
`createShipment`/`createInvoice` are `buildDoc('M_InOut'/'C_Invoice')`, the *same* archetype verb recursing one
level down; MInOut's only real delta is `reserveStock`+locator. (3) **Both folds in one function** — the body is
*code* (Java→compact verbs); what it *emits* is *ops* = *data*; `DocStatus` and the trial balance are a *fold*
over that data.

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

### Non-repetition — one construct + ~25 deltas

In iDempiere each of ~496 `M*` classes carries its *own* `completeIt`/`prepareIt`/`createFacts`, wrapped in
`getX`/`setX`/`saveEx`/JDBC boilerplate — that boilerplate *is* the repetition. In the Fold Engine the boilerplate
is gone (it was ORM plumbing), and the document lifecycle is **one shared construct** — `buildDoc` / `completeIt` /
`postRecipe` / `commitGroup` — parametrized by doc type. The fold witnesses prove the reuse: they tag
**`newVerbs=0`** (`W-FOLD-INVOICE`, `W-FOLD-REPLENISH`, `W-FOLD-MATCHINV`, `W-FOLD-MOVEMENT` each folded a *new*
document class **without adding a verb**). [`ERP_MODEL_ARCHETYPE.md`](ERP_MODEL_ARCHETYPE.md) puts the number on
it: **496 `M*` / 735k LOC collapse to "MOrder archetype + ~25 deltas"**.

**Honest boundary:** not literally zero-per-class. ~25 real deltas remain — `MInOut` (locator + reserve),
`MPayment` (allocation), `MMovement` (intercompany leg), `MProduction`/`MInventory` (BOM explode/consume). But
those are *small parametric branches on the shared verb*, not reimplemented classes: the skeleton is shared and
non-repetitive; the genuine per-class business delta stays, tiny.

The data shape collapses the same way: ~1000 `M*` tables map onto a **5-table runtime** — `containers`,
`items`, `documents`, `document_lines`, **`journal`** + a `doc_type` discriminator ([`ERP.md`](ERP.md) §0,
witness §5TBL). **Two "journals", don't conflate them:** `kernel_ops` is the *event* journal (the signed op-log
/ audit trail); **`journal`** is the *accounting* journal — the universal books table every doc posts into
regardless of class, instead of a per-class `Doc_X.createFacts`. *"Journal IS the books — if the journal
balances, the books are correct; everything else is UI."* So the construct lives in **one place**, not 496.

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

**`Query()` has no op equivalent — by design.** A `Query` is a *read*, and reads never enter the op-log (the
CQRS split: **writes → ops, reads → SQL over the projection**). So `Query()` is just a `SELECT`; no op, no
signing, no log entry. The AD-context form (Tab `WhereClause` + `OrderBy`) is `AdTabQuery` — still a `SELECT`.
The only reads inside the *write* path are FK lookups (`ad_reference`/`ad_valrule`) that *inform* an op but
aren't ops.

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

---

## 8. "Is this just OO again?" — paradigm placement

A fair challenge: messaging, encapsulation, one shared construct — isn't that OO rebuilt? The answer turns on
*which* OO you mean.

**Textbook (Java/C++) OO — no, it's the opposite.** Classes, inheritance, encapsulated *mutable* objects: the
fold engine has none. Data (ops, journal, projection) is separated from behavior (verbs); 496 stateful classes
collapse to one construct + data. That's the **data-oriented** camp (Hickey: "the database as a value") — a
deliberate move *away* from the mutable object graph.

**Kay's original OO — yes, arguably more faithful than Java.** Alan Kay, who coined the term: *"OOP to me means
only messaging, local retention and protection and hiding of state-process, and extreme late-binding of all
things."* The fold engine hits all three — an **op is a message**; state is never reached into (*tell, don't
ask* — stronger than `getX/setX`, which expose state); the "class" is **late-bound from AD data**, not compiled.
Java is *class*-oriented; Kay called class/inheritance "the lesser idea." So the op-log drifts *back* toward
what OO was for, minus the class explosion. Lineage: **actors / Erlang / Smalltalk**, not Java.

**And it's *more abstract* than Java can be — because types became data.** In Java, `Invoice` is a *class* —
code, fixed at compile time. Here, `Invoice` is a `doc_type` value + AD dictionary rows — *data*. ~1000 `M*`
tables reduce to a **5-table runtime** precisely because the specific types stopped being schema and became
data over an abstract shape ([`ERP.md`](ERP.md) §0: "the schema stops growing with the feature list", 98.4% of
business edges mappable). Java's type system is a *compile-time* layer — types are not runtime values;
iDempiere already pushes toward types-as-data through the AD but still **compiles `X_` classes**, so it's only
half-abstract. The fold engine keeps types as data all the way down — strictly more abstract than either.

**The cost, honestly.** Extreme abstraction trades away the compiler's static guarantees: Java *checks* that
`MOrder.getC_BPartner_ID()` exists; a data-driven engine surfaces that error at fold/run time. The fold engine
pays it back not with a type-checker but with the **witness + §FALSIFIER + oracle-diff discipline** — every
surface diffed to `maxDiff=0c` against real iDempiere. Static safety → proven-equivalence safety. (And the 1.6%
of edges that don't fit the 5 shapes is named, not hidden.)

**The limit — one table.** Push the abstraction all the way and the count goes to *one*: the event log itself.
The 5-table runtime isn't the floor — it's a **projection** of `kernel_ops` (§1). The single source of truth is
the log; the tables are disposable views folded from it, kept only so reads are fast SQL instead of re-folding
history per query (in practice: log + snapshots/compaction). So "how many tables" stops being an ontological
question — it's **one log of facts, plus however many views you find convenient.** At the limit, an ERP is *a
list of what happened + functions that fold it into answers*; every table, screen, report, and the books are
views.

And this isn't exotic — it's **500 years old.** Double-entry bookkeeping (Pacioli, 1494) is append-only event
sourcing: the **journal** is the immutable log; the ledger and balance sheet are *folds* of it. "Journal IS the
books" (§4) is literally that. The fold engine just generalizes the accounting journal's discipline — never
erase, only append, derive everything — from the books to the *whole* ERP.
