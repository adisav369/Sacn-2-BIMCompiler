# POS Addon Spec — the 2012 Unicenta loop, rebuilt as folds

*Spec for the first op-log-native **addon**: a browser Point-of-Sale on the migrated tenant, with
BOM **backflush** and **replenishment** — the same loop RED1 shipped in the 2012 era as the
[Unicenta POS plugin](https://wiki.idempiere.org/en/Plugin:_Unicenta_POS) +
[AutoBOMOrder](https://wiki.idempiere.org/en/Plugin:_AutoBOMOrder) for iDempiere, with the middleware
deleted. Doctrine: [The POS Lens](POSLens.md). Roadmap home: item 1 of the
[Migrate & Compare paper's roadmap](MigrateComparisonPaper.md#v-roadmap).*

> **Status: §P-1..§P-4 BUILT + WITNESSED (2026-06-11, `prompts/POS_LENS_SESSION.md` `# DONE`).**
> Engine side: `build/erp/pos_core.js` (pure fold glue over the existing verbs, newVerbs=[]) —
> W-POS-RING · W-POS-WR · W-POS-BACKFLUSH · W-POS-REPLENISH all green (`build/erp/poc_pos_*.log`).
> Lens side: `erp/pos_lens.js` + the `pos` pill (showWhen:pos-station) on idempiere.html —
> W-POS-LIVE green on localhost (bim-ootb branch `feat/pos-lens`, deploy awaiting explicit GO).
> §P-5 multi-station stays out of scope (named below). The matrix "POS lens" row stays PENDING:
> §5's bar is a LIVE ring folded to the cent — the to-the-cent leg is proven headless on the
> acct-linked db (W-POS-WR), but the live ad_seed.db lacks posting linkage (the same data-gate as
> Posting-Preview; lighting it = `prompts/MIGRATE_POSTING_CONFIG.md`).
> Clean-room rule: Unicenta is defunct and the plugin code private — behaviour is spec'd from the
> public wiki + RED1's own design; **no code is copied**.

---

## 1. What "addon" means here

In 2012 the integration needed a fat Java POS, ActiveMQ queues per station, and sync plugins on both
ends. The premise was right — *the POS is dumb: record the sale, take payment, send the order; the
ERP holds the intelligence* — but the plumbing was the product. An addon on the browser kernel keeps
the premise and deletes the plumbing:

| 2012 (Unicenta ⇄ iDempiere) | Addon (this spec) |
|---|---|
| Fat Java POS client per station | One **lens** (HTML/JS surface) over the resident tenant db |
| ActiveMQ queue per station, POSSync/OrderSync | The **signed op-log** — ops are the queue, replay is the sync |
| Station = AD_Org/POS Locator config | Same — `c_pos` row per station (already in the seed) |
| AutoBOMOrder plugin backflushes on sale | `erp_engine.explodeBOM` — already witnessed (W-FOLD-BACKFLUSH) |
| ReplenishReport → PO | `poc_replenish.js` formula fold — already witnessed (W-FOLD-REPLENISH) |
| Products/customers sync ERP→POS | Nothing to sync — the lens reads the same db the ERP renders |

An addon therefore touches **four registries and zero engines**:

1. a **pill** in the pill registry (the only way UI enters — `pills_idmp.json` + `IdmpPillActions`),
2. **AD windows** it may deep-link (already in the dictionary — see §2),
3. **process handlers** in the `ad_process.js` registry (`registerHandler(classname, fn, meta)`),
4. **ops** through the kernel (`kernel_ops.commitGroup`) — handlers never write tables directly.

If an addon needs a fifth thing — its own persistence, a new verb, a forked engine — the rails were
built wrong: **stop and report to the engine lane** (the FOLD-not-FORK red flag in
`prompts/POS_LENS_SESSION.md`).

## 2. Substrate inventory — what already exists (EXTRACT, don't rebuild)

Everything below is shipped and witnessed as of 2026-06-11; the addon *consumes* it.

**The POS dictionary ships in the browser seed** (full-width `ad_seed.db`, bim-ootb PR #265 — the
old column-slice had these tables but the windows were unreachable):

| Asset | Rows | Detail |
|---|---|---|
| `c_pos` | 1 | "Garden User - Store": doctype 135, keylayout 100, warehouse 104, pricelist 101, cashbook, cashdrawer |
| `c_poskey` / `c_poskeylayout` | 163 / 5 | the ring-up key grid, real GardenWorld layouts |
| `u_posterminal` | 1 | extended terminal config (37 cols) |
| AD windows | — | 338 POS Terminal · 339 POS Key Layout · 200008 POS Payment · 200009 POS Tender Type · Sales Order tab 200016 "POS Payment" |
| `C_DocType` 135 | — | DocBaseType `SOO`, **DocSubTypeSO `WR`** — iDempiere's on-the-fly shipment+invoice POS order |

**The fold verbs** (`scripts/erp_engine.js` — pure, host-injected, no DB/clock):

| Verb | Witness | What it proves |
|---|---|---|
| `buildDoc(spec, parent, lines)` | W-FOLD-BUILDDOC | the one archetype create-verb; `createShipment`/`createInvoice` are *specs* of it, not code |
| `explodeBOM(bomOf, productId, qty)` | W-FOLD-BACKFLUSH (`build/erp/poc_backflush.log`) | recursive recipe explosion on the REAL GardenWorld Patio recipe; multi-path accumulation (cap-screw 8+8=16); flat-explosion falsifier |
| `movementSign` / `qtyOnHand(events, opts)` | W-FOLD-QTYONHAND | on-hand is a FOLD of the movement ledger (trailing-char polarity), never a trusted column |
| replenishment formula | W-FOLD-REPLENISH (`poc_replenish.js`) | the `ReplenishReport:294-327` port — `available = onhand − reserved + ordered`, type 1/2 → `QtyToOrder` → PO via `buildDoc`; **oracle-equivalent to iDempiere's own formula SQL, per product, to the unit** |

**The spine** the addon rides: `ad_docfsm.js` (`legalActionsOrder` knows `docsubtypeso`; WR completes
CO→CO), `doc_poster.derivePostings` + `post_resolver` (frozen, oracle-anchored postings),
`kernel_ops.commitGroup` (atomic signed op groups, hash-chained), `ad_process.js`
`registerHandler` (the addon's classname entry-point), and the pill registry
(`pills_idmp.json` + `idmp_pills.js`, Lucide line icons only).

**Conceptual spine:** [POSLens.md](POSLens.md) §1 (one clean act in), §5 (scan/QR door), §6
(backflush), §7 (shrinkage), §11 (honest scope at time of writing — this spec supersedes its
"to build" list where §2 above says witnessed).

## 3. The addon, end to end (the 2012 loop as folds)

```
ring items (c_poskey grid / QR scan)
  → C_Order (doctype 135 WR, c_pos defaults: warehouse, pricelist, BP-cash)
  → tender (POS Payment — cash first; tender types from window 200009's dictionary)
  → Complete = ONE signed op group:
        CREATE_DOCUMENT C_Order + lines            (buildDoc)
        CREATE_DOCUMENT M_InOut C- + lines          (buildDoc spec createShipment)
        CREATE_DOCUMENT C_Invoice + lines           (buildDoc spec createInvoice)
        CONSUME leaf components                     (explodeBOM — only when the product is a BOM)
        DOC_ACTION CO on each                       (ad_docfsm dispatch)
  → postings = derivePostings on each doc (preview before, fold after — same engine)
  → on-hand falls (qtyOnHand fold over the new movements)
  → replenishment fold sees available ≤ Level_Min → suggests/creates the PO (W-FOLD-REPLENISH)
```

No step is new machinery. The *sequence* is the addon.

### §P-1 POS surface (the lens)

- Pill `pos` in `pills_idmp.json` (icon: Lucide `shopping-cart` family; `showWhen` gated on a
  `c_pos` row existing for the logged-in org — data-gated like the Posting-Preview pill).
- Surface renders FROM the dictionary: the key grid from `c_poskey`/`c_poskeylayout` (163 real
  keys), prices from the `c_pos.m_pricelist_id` list, BP defaulting to `c_bpartnercashtrx_id`.
  Zero per-product code — a new product is a row, not a feature.
- Money math via `site/bigdecimal.js` (never raw JS Number — standing rule).
- Witness **W-POS-RING**: `§POS ring product=<id> qty=<n> price=<pricelist-row>` — every ringed
  line traces to a `c_poskey`→`m_product`→`m_productprice` row. §FALSIFIER: ringing a product
  absent from the price list must refuse (no invented price).

### §P-2 Complete = the signed group (the sacred transaction)

- One `commitGroup` carrying order+shipment+invoice+backflush+doc-actions. All-or-none; gid
  idempotent; hash-chained (POSLens §4 unbroken provenance).
- The WR semantics come from the dictionary (`docsubtypeso='WR'`), not from POS code — the same
  order completed from the ERP window must produce the same group.
- Witness **W-POS-WR**: group replay == the engine's own `createShipment`/`createInvoice` specs;
  postings == `derivePostings` to the cent; §FALSIFIER: tampered op breaks `verifyChain`.

### §P-3 Backflush (AutoBOMOrder reborn)

- On Complete, for each line whose product is a BOM (`pp_product_bom` lookup — host-injected
  `bomOf`), `explodeBOM` yields leaf consumption; emit the consumption movements in the SAME group.
- Already proven on the real recipe (W-FOLD-BACKFLUSH); the addon's witness **W-POS-BACKFLUSH**
  only needs to prove the *wiring*: ring Patio Set ×1 → the §-log shows the same leaf dict the
  headless witness derived. §FALSIFIER: a non-BOM product must emit zero consumption ops.

### §P-4 Replenishment (the loop closes)

- After the group commits, run the W-FOLD-REPLENISH fold over the new ledger state; render the
  suggestion (product, warehouse, QtyToOrder) in the POS surface; one tap = the PO group
  (`buildDoc`, IsSOTrx=N) — *suggest by default, auto-create behind a `c_pos`-level flag*.
- Witness **W-POS-REPLENISH**: sell below `Level_Min` → suggestion appears with the
  formula-derived qty; §FALSIFIER: selling a non-replenish product never suggests.

### §P-5 Multi-station ring ("a ring of dumb POS stations around one ERP")

- Each station = a `c_pos` row (org-scoped), its ops a signed channel — the 2012 ActiveMQ
  queue-per-station, now just the op-log's own grouping. Offline-first: ops seal locally,
  sync rides the existing §0.20 sync FSM / relay when present. **Out of scope for the first
  build session; named so it is never re-invented.**

## 4. Honest gaps (named, not hidden)

- **WR completeIt enactment**: `ad_docfsm` proves the *status* walk; the order→ship→invoice
  *enactment* exists as engine verbs + witnesses, but the live wiring (UI Complete → group) is
  exactly what §P-2 builds. GardenWorld has no production docs, so backflush stays
  **rule-consistent** (two independent implementations agree + falsifier), not fact_acct-diffed.
- **Tender types beyond cash** (card processors) — dictionary rows exist; processors are not
  in scope (the 2012 plugin was cash-first too).
- **Seed scale**: 1 `c_pos` row / 163 keys is a demo shop; a real tenant brings its own rows via
  the install lifecycle (NEW_CLIENT_MGMT — closed for Odoo + iDempiere).
- **Gate** (unchanged from the work-order): build AFTER the write-path lane is green; a lens can
  only ride rails that exist.

## 5. Done-when

Each § above is ✅ (witness lines + on-screen verify + deploy) or ⛔ with the one blocking fact;
matrix gains a "POS lens" row only when W-POS-WR folds a live ring to the cent.
