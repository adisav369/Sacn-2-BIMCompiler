<!--
BIM OOTB / ERP OOTB — iDempiere 2.0: the plugin / extension architecture.
Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
SPDX-License-Identifier: MIT
-->

# Plugin Architecture — metadata-first, no new skill, host absorbs the glue

> **Status: DRAFT / SPEC, internal working concept, far-off, grail-gated.** Nothing is built.
> Spec-first; non-invent (extensions are DATA, not hardwired code); witness-led; **EXPLICIT GO before
> any build or deploy.** This doc owns the **extension/plugin** layer of the engine. It defers the
> *model* to [IDEMPIERE_2.md](IDEMPIERE_2.md) and all *rendering* to
> [IDEMPIERE_RENDERER_SPEC.md](IDEMPIERE_RENDERER_SPEC.md).
>
> Read first: [IDEMPIERE_2.md](IDEMPIERE_2.md) (the thin core: 5-table bridge + verbs + signed op-log) ·
> [PILL_MANIFEST_SPEC.md](PILL_MANIFEST_SPEC.md) (the live registry-as-data precedent) ·
> `bim-ootb/viewer/pill_builder.js` (S281 registry — the working shape to reuse) ·
> [ERP.md §0.14](ERP.md) (the closed verb set) · [DistributedERP.md](DistributedERP.md) (op-log doctrine).

---

## §0 The governing principle

A plugin author should write **the least possible definition, in a format they already know, and get
everything else for free.** The framework — not the plugin — carries lifecycle, wiring, persistence,
undo, signing, and UI mounting. Stated as three rules:

1. **Metadata-first.** The default extension is *data* (rows / JSON), not code. Code is the exception,
   reserved for genuinely new computation.
2. **No new skill.** Authoring uses **popular, already-known methods** — JSON, SQL rows, a plain JS
   function — never a bespoke DSL, annotation language, build tool, or framework a person must learn
   first. The host language (JS) *is* the extension language ([IDEMPIERE_2.md](IDEMPIERE_2.md) §core).
3. **The glue is the product.** Everything hard about "managing plugins" (loading, isolation,
   coupling, ordering, persistence, undo/redo, audit, version skew) is absorbed by the host. A plugin
   declares *what* it contributes; the host decides *how* it runs.

This is the deliberate inverse of OSGi: OSGi makes the *plugin* carry the modularity machinery
(manifests, Import/Export-Package, bundle lifecycle, classloaders). Here the *host* carries it, so the
plugin shrinks to a contribution.

## §1 The gap, restated

OSGi exists to give Java four things the language lacked: (a) module boundaries, (b) a service
registry for interface-decoupling, (c) dynamic lifecycle, (d) versioned dependency isolation
([IDEMPIERE_2.md](IDEMPIERE_2.md) §from-SAP/OSGi lineage). The *need* — extend without forking core,
loosely coupled, add/remove without rebuilding the world — is permanent. OSGi is one heavyweight,
Java-specific answer, and a famously painful one.

Two of OSGi's four reasons do not exist for us:
- **Module system:** JS has one (ES Modules + dynamic `import()`). The #1 reason OSGi was invented is moot.
- **Behaviour-as-data:** iDempiere needed OSGi partly because its AD was *incomplete* — real logic leaked
  into Java callouts/processes/model-validators, and *that code* is what needed an OSGi plugin
  mechanism. Push more behaviour into data and most "plugins" stop being code at all.

So the residual problem is small: a way to register the ~10% that must be code, and couple it loosely.

## §2 Prior art — has anyone solved this?

Yes — the **metadata-minimal-plugin + host-glue** model is well established and commercially proven.
The pieces are solved; only the *synthesis with our op-log core* is new. We adopt patterns, not code
(clean-room, [IDEMPIERE_2.md](IDEMPIERE_2.md) §Guardrails).

| System | What it proves | What we take |
|---|---|---|
| **VS Code extensions** | `package.json` `contributes` is *pure metadata* (commands, menus, views, settings); declarative **contribution points**; lazy **activation events**. The host owns every registry. | The contribution-point shape; lazy activation; "manifest, not framework." |
| **Eclipse extension points / `plugin.xml`** | The ancestor of the above — a **declarative layer on top of OSGi** (Equinox, which *is* iDempiere's runtime). Proves you can have metadata extension without exposing OSGi to the author. | Confirms the declarative layer is the part that matters; OSGi underneath was the avoidable cost. |
| **Salesforce / ServiceNow** | Enterprise-scale "clicks not code": objects, fields, flows as **metadata**; Apex only for residual logic. | Proof that behaviour-as-metadata + minimal code scales to real ERP breadth. |
| **Directus / Strapi** (headless, DB-backed) | **Introspect a SQL schema → generate the admin + API.** The database is the source of truth; an extension is a row or a small hook. | Most direct precedent given we are SQLite-centric — schema/data *is* the plugin surface. |
| **WordPress hooks / filters** | A lightweight **event-bus** plugin model: register a callback on a named hook. Massive ecosystem, minimal definition. | The event-bus coupling shape (our op-log is the typed, signed version). |
| **pytest / `pluggy`, Datasette plugins** | **Hook-specs**: tiny plugins, host does the work; Datasette is SQLite-centric like us. | The "small plugin, host-driven" contract; hookspec = our extension-point registry. |
| **ERPNext / Frappe DocType** | Everything is metadata ([IDEMPIERE_2.md](IDEMPIERE_2.md) §from-ERPNext). | Validates the dictionary/descriptor approach end-to-end. |

**What is NOT yet solved (our contribution, honestly bounded):** the *combination* of
metadata-minimal plugins **+ the op-log as the coupling bus + signed deterministic replay + SQLite-WASM
local-first**. Each ingredient is proven; nobody (that we can cite) ships all four together. The claim
is the *synthesis*, never "we invented metadata plugins" — see §10 witnesses.

## §3 The two-tier extension model

- **Tier 1 — Data overlays (~90%).** A new document type, field, validation, screen, menu item, report,
  rule, or chart is **rows / JSON entries**, folded by the existing engine. No code, no module, no
  lifecycle. This is where iDempiere needed *both* the AD *and* OSGi; we need only the data layer,
  because the engine is data-driven enough that there is nothing to make code-pluggable. Precedent
  already live: [PILL_MANIFEST_SPEC.md](PILL_MANIFEST_SPEC.md) (pills + the whole AD_Menu are data),
  the compiled AD manifest, `erp_rules.db`.

- **Tier 2 — Code plugins (~10%).** Genuinely new verbs/primitives, external integrations (payment,
  IFC/format parsers, an API), or a custom renderer. These ship as **ES modules** that *register*
  against named extension points and communicate **only through the op-log / kernel — never by calling
  each other directly** (§6). Tier 2 is the residue, deliberately kept thin.

The single rule across both: a Tier-2 plugin that could have been a Tier-1 overlay is a design failure.
Pressure is always toward data.

## §3.5 The minimal manifest — the accounting genome (what an ERP actually needs)

This is the floor of "as little as possible." An ERP does **not** need a plugin's code, screens, or
workflow. To fold a new thing into the system, it needs only two declarations — and they can live at
**a URL** the host fetches (the manifest *is* the plugin; the URL is just its locator — the same
URL-as-genome idea as [AnyAppMaker.md](AnyAppMaker.md)):

1. **The charge model — `DR/CR → Account ID`.** The posting rule: "when this event happens, debit
   account X, credit account Y." This is the irreducible thing that makes something an *ERP*
   transaction rather than a note. It is exactly iDempiere's posting model (a document → `Fact_Acct`
   lines via the accounting schema), reduced to its essence: a balanced pair of journal entries keyed
   to account ids. In 2.0 terms it is a verb that emits **`journal`** lines — nothing more.

2. **The master-data bindings.** Which masters the thing references and resolves accounts *through* —
   `C_BPartner` (receivable/payable account), `M_Product` / product category (revenue/COGS/inventory
   account), `C_Charge` (its own account), `C_UOM`, the chart of accounts. Master data is *where the
   Account IDs come from*: the partner/product/charge carry the account assignments, the event picks
   the pair. So "and other master data models" is not extra — it is half of the charge model, because
   without the masters there is no Account ID to post to.

So the **whole** minimal manifest is, conceptually:

```json
{
  "url": "https://.../my-thing.manifest.json",
  "masters":  ["C_BPartner", "M_Product", "C_Charge"],
  "charge":   [
    { "event": "complete", "dr": "{Product.RevenueAcct}",   "cr": "{BPartner.ReceivableAcct}" },
    { "event": "pay",      "dr": "{Bank.InTransitAcct}",     "cr": "{BPartner.ReceivableAcct}" }
  ]
}
```

That is the entire definition an author must supply for a new posting document type. The host glue (§5)
provides everything else: it resolves `{…}` against the bound masters, emits the balanced `journal`
ops, signs them, folds the GL, and renders the views — none of which the author writes. The accounts
*balance check* (ΣDR = ΣCR) is enforced by the fold, not the plugin. **Charge model + master bindings =
the accounting genome; the rest is glue.** Anything beyond these two is either a Tier-1 overlay (UI,
menu, report — data) or the rare Tier-2 code verb (genuinely new computation, §3).

### §3.5.1 Account-role resolution grammar (grounded in `ad_seed.db`, non-invent)

The `{Master.Role}` tokens are **not** invented — each resolves to a real account column already present
in `ad_seed.db` (the iDempiere accounting masters), and falls back to `c_acctschema_default` exactly as
iDempiere's posting layer does. An "Account ID" is a `C_ElementValue_ID` (the chart of accounts:
379 rows, e.g. `421 = Sideline Revenue`, `234 = Receivable`). Resolution is keyed by
`(master_id, c_acctschema_id)`.

| Token | Source table (verified) | Column | Fallback (`c_acctschema_default`) |
|---|---|---|---|
| `{BPartner.Receivable}` | `c_bp_customer_acct` | `c_receivable_acct` | `c_receivable_acct` |
| `{BPartner.Prepayment}` | `c_bp_customer_acct` | `c_prepayment_acct` | `c_prepayment_acct` |
| `{BPartner.Liability}` | `c_bp_group_acct` (vendor side) | `v_liability_acct` | `v_liability_acct` |
| `{Product.Revenue}` | `m_product_category_acct` (via product→category) | `p_revenue_acct` | `p_revenue_acct` |
| `{Product.COGS}` | `m_product_category_acct` | `p_cogs_acct` | `p_cogs_acct` |
| `{Product.Asset}` | `m_product_category_acct` | `p_asset_acct` | `p_asset_acct` |
| `{Charge.Expense}` | `c_charge_acct` | `ch_expense_acct` | `ch_expense_acct` |
| `{Charge.Revenue}` | `c_charge_acct` | `ch_revenue_acct` | `ch_revenue_acct` |
| `{Bank.InTransit}` | `c_acctschema_default` | — | `b_intransit_acct` |
| `{Tax.Due}` | `c_tax_acct` | (per tax) | `t_due_acct` |

The resolver is **host glue**, written once: `token → (table, column)` lookup by `(master_id,
acctschema)`, else the schema default. The manifest author writes only the token; they never touch a
column name or a join. This table *is* the account half of the Odoo↔iDempiere data dictionary
([IDEMPIERE_2.md](IDEMPIERE_2.md) §pivot) — here in its iDempiere-native form, every row witnessed
against `ad_seed.db`.

## §4 Authoring surface — popular methods only, no new skill

The contract: a plugin author touches **at most one of three familiar things**, and nothing else.

| Tier | Author writes | Format (already-known) | Author does NOT write |
|---|---|---|---|
| 1 — overlay | a manifest row / object | **JSON** (the `pill_builder.js` `actions[]` shape; or AD-style descriptor rows) | no build, no lifecycle, no SQL DDL |
| 1 — data | rows | **SQL `INSERT` / a CSV / a spreadsheet import** into the bridge tables | no schema design (the 5-table bridge is fixed) |
| 2 — code | one function | **a plain JS function** `(ctx, op) => op[]` registered at an extension point | no classloader, no manifest headers, no module wiring, no DI container |

Concretely, the **whole** of a Tier-1 overlay is a JSON object of the existing pill shape
(`{ id, name, key, icon, fn, panel?, hold? }`, [PILL_MANIFEST_SPEC.md](PILL_MANIFEST_SPEC.md) §0) — no
new fields invented. The **whole** of a Tier-2 plugin is a named function that takes context + an op
and returns op-group(s); registration is one line against a named point. There is no `.csproj`, no
`MANIFEST.MF`, no `plugin.xml`, no annotation processor, no codegen step. If an author has to *learn
something to start*, we have failed rule §0.2.

## §5 The glue — what the host absorbs (so the plugin doesn't)

Everything below is provided by the framework once, for every plugin, so no plugin reimplements it:

| Concern | OSGi makes the plugin handle it | Here the host handles it |
|---|---|---|
| **Loading / lifecycle** | bundle activator, start/stop | dynamic `import()` + register/unregister; activation on first use (VS Code-style) |
| **Persistence** | the plugin manages its store | all effects are **ops appended to the signed log**; state is the fold ([IDEMPIERE_2.md](IDEMPIERE_2.md)) |
| **Undo / redo / audit** | bespoke per plugin | free — reversal = appending the inverse op-group (W-CHAIN); audit = the log |
| **Coupling / wiring** | service registry, interface contracts | the **op-log bus** (§6) + the extension-point registry (data) |
| **Ordering / determinism** | manual | deterministic replay; ops carry recorded inputs, no `Date.now()`/`Math.random()` |
| **Signing / tamper-evidence** | not provided | every op hash-chained (W-SIGN) by the host, not the plugin |
| **UI mounting** | the plugin draws | the renderer mounts contributions ([IDEMPIERE_RENDERER_SPEC.md](IDEMPIERE_RENDERER_SPEC.md)) |
| **Isolation** | classloaders | extension-host boundary (§7) |

The plugin declares a contribution and (Tier 2) emits ops. The host does the rest. That asymmetry —
**fat host, thin plugin** — is the entire design.

## §6 Loose coupling = the op-log as the bus

This is how we get OSGi's service-registry decoupling without a service registry. Tier-2 plugins do
**not** hold references to one another and do **not** call each other's functions. They:

- **read** the fold (the current projection of the log), and
- **emit** ops into the kernel, which appends them to the signed log.

Two plugins interact only by one emitting an op the other consumes on replay. They are decoupled *by
construction* — the contract between them is the **op vocabulary** ([ERP.md §0.14](ERP.md)), not an
interface either imports. This is the WordPress-hook / event-sourcing pattern, with the bus being
*signed, replayable, and typed*. A plugin can be added or removed and the log still replays; a removed
plugin's past ops remain valid history.

Consequence: the closed verb set is the *real* extension API. Adding a Tier-2 plugin that needs a new
op = proposing a new verb, which is a **named, reviewed addition to the alphabet** — exactly the
`newVerbs=[…]` discipline from the Odoo fold. The bus stays small on purpose.

## §7 Isolation & security — the open gap, and the modern answer

This is the honest weak point and where prior art *does* give a solution.

**The gap:** a browser plugin runs JS in the page context; a misbehaving or hostile Tier-2 plugin can
touch globals or exfiltrate. OSGi/Java gives classloader isolation + a permissions model; we have none
by default. The signed chain protects *data* integrity, **not** *code* sandboxing.

**The solved answer (adopt, don't reinvent):** VS Code runs extensions in a **separate Extension Host**
— a Node child process on desktop, a **Web Worker** on the web — talking to the UI over a typed RPC /
message boundary; extensions cannot touch the DOM or the renderer directly. The browser-native
equivalent for us:

- Tier-2 plugins run in a **Web Worker** (or sandboxed iframe), never the main context.
- The only thing crossing the boundary is **ops** (structured-clone messages) — which is already our
  coupling model (§6), so isolation and coupling collapse into *one* mechanism.
- A **capability manifest** (which extension points / which op verbs a plugin may emit) gates what it
  can do — the metadata-permissions model, Salesforce-style, not a Java SecurityManager.

Honest status: untrusted third-party Tier-2 plugins need the Worker boundary + capability gating
*before* any marketplace ([AnyAppMaker.md](AnyAppMaker.md)). Tier-1 data overlays carry no code and so
carry no code-execution risk — only validation-of-data risk, handled by the same rule engine.

## §8 Versioning — where the complexity relocates (it does not vanish)

OSGi's hard part was package versioning. Ours becomes **versioning the op/verb vocabulary** — the schema
of the bus (§6). This is the real, irreducible maintenance cost and must be owned explicitly:

- The verb set and the bridge shape are a **published contract** with a version.
- Tier-1 overlays declare the manifest/descriptor schema version they target; the host migrates old
  overlays forward (data migration = a fold transform, the same machinery as the ERP migration).
- A removed/renamed verb is a breaking change → covered by replay against an oracle before release.

We trade OSGi's bundle-version hell for one well-scoped artifact: the verb vocabulary. Smaller surface,
but not free — discipline lives here.

## §9 Boundaries — what this doc does NOT cover

- **Rendering of contributions** → [IDEMPIERE_RENDERER_SPEC.md](IDEMPIERE_RENDERER_SPEC.md).
- **The model / verb set / fold** → [IDEMPIERE_2.md](IDEMPIERE_2.md), [ERP.md](ERP.md).
- **The live editable-rules write-loop** (rule-as-data edited at runtime) → grail, gated E3/E4
  ([HolyGrail.md](HolyGrail.md)).
- **A third-party marketplace / distribution / trust signing** → [AnyAppMaker.md](AnyAppMaker.md);
  this doc only states the §7 prerequisites.

## §10 Witnesses (§-log first; the only evidence)

No claim ships without a `§`-logged proof. Spec-first names them now:

- `§PLUGIN-OVERLAY tier=1 source=manifest.json handAuthoredCode=0 mounted=N` — a new affordance added as
  data only, zero new bar/host code (the S281 contract, [PILL_MANIFEST_SPEC.md](PILL_MANIFEST_SPEC.md)).
- `§PLUGIN-CODE tier=2 verb=<name> newVerb=Y|N coupling=oplog directCalls=0` — a code plugin touches the
  bus only; `directCalls=0` proves the no-direct-reference rule (§6).
- `§PLUGIN-REPLAY pluginAdded then removed → replay rebuildA==rebuildB agree=Y` — add/remove leaves the
  log replayable (determinism, §5).
- `§PLUGIN-ISOLATION tier=2 context=worker domTouch=0 caps=[…]` — code runs off the main context, only
  declared capabilities used (§7).
- `§PLUGIN-MINIMAL author wrote: lines=N files=1 newSkill=none` — the headline: the *authoring cost* of a
  representative plugin, proving §0 (compare blast radius vs an iDempiere OSGi bundle / an Odoo module).

## §11 Build order (each names its witness; NOTHING builds without GO)

- **P0** — formalize the Tier-1 overlay contract on the existing `pill_builder.js` shape; one new
  overlay added as pure JSON. `§PLUGIN-OVERLAY`, `§PLUGIN-MINIMAL`.
- **P1** — Tier-2 extension-point registry (data + a small dispatcher); one code plugin emitting ops via
  the kernel, zero direct calls. `§PLUGIN-CODE`, `§PLUGIN-REPLAY`.
- **P2** — Web Worker extension-host boundary + capability manifest for Tier-2. `§PLUGIN-ISOLATION`.
- **P3** — verb-vocabulary versioning + an overlay-schema migration fold (§8).
- (marketplace / third-party trust → deferred to [AnyAppMaker.md](AnyAppMaker.md), grail-gated.)

## §12 Guardrails (doctrine, not preference)

1. **Fat host, thin plugin.** Every concern a plugin would otherwise carry moves to the host. A plugin
   that must learn a framework to start violates §0.2.
2. **Data over code.** Tier 2 is the exception; anything expressible as a Tier-1 overlay must be one.
3. **Couple only through the op-log.** No plugin-to-plugin direct references (§6). The bus is the API.
4. **Popular methods only.** JSON / SQL rows / a plain JS function — no bespoke DSL, no new build step.
5. **Clean-room.** Adopt the *patterns* of VS Code / Directus / pluggy (§2); never copy their source.
6. **Witness everything.** No "it's pluggable / it's isolated / it's minimal" claim without the matching
   `§` line (§10).

## §13 Prototype readiness — the first test plugin against the real platform

This section makes the spec buildable. It states what the platform already provides, the single gap a
*posting* plugin needs, and a concrete first plugin — all grounded in `bim-ootb/viewer/` and `ad_seed.db`
(verified 2026-06-02). **Nothing here is built; EXPLICIT GO required (§0).**

### §13.0 What already exists (verified, file-cited)

| Capability | Where | Status for prototyping |
|---|---|---|
| Op-log (rich ops + verbs) | `kernel_ops.js:9-22`; verbs `CREATE_DOCUMENT/SET_STATUS/CREATE_LINE/ALLOCATE/MATCH` (`erp_kernel.js:72-80`) | ready |
| Signed hash-chain | `erp_signer.js` (ECDSA P-256), `kernel_ops.js sealChain` (`prev_hash\|canonical→op_hash→sig`) | ready |
| Deterministic fold | `erp_kernel.js replay()/applyOne()` → 5-table projection | ready |
| **Tier-1 extension point** | `pills.json` (declarative) → `erp_pills.js:75` `fetch('pills.json')` → `pill_builder.js actions[]` | **ready — a Tier-1 plugin needs zero engine change** |
| Master data access | `ad_data.js readRecords()` (SQL or bridge) | ready |
| **Account masters** | `ad_seed.db`: `C_ElementValue` (379), `c_bp_customer_acct` (36), `m_product_category_acct` (28), `c_charge_acct` (6), `c_acctschema_default` (2), `C_AcctSchema` (2) | **ready — the Account IDs the charge model resolves to all exist** |

### §13.1 The single gap for a *posting* plugin (named, not hidden)

The engine's `journal` projection today is a **settlement edge**, not double-entry:
`journal(id, batch_id, journal_id, source, metadata)` (`erp_kernel.js:40`) — **no DR/CR, no account_id.**
iDempiere's real shape exists only in the seed's `gl_journalline` (`account_id, amtacctdr, amtacctcr,
c_validcombination_id, …`), which the engine does not yet write. So a posting plugin requires **one
bounded host change** (glue, not plugin):

1. **Extend the `journal` projection** with `account_id, amtacctdr, amtacctcr` (mirror `gl_journalline`).
2. **Add a `POST` verb** to the kernel that takes a document + a charge rule, runs the §3.5.1 resolver
   against the bound masters, and emits **balanced** journal lines (ΣDR = ΣCR), signed + replayable like
   every other op.
3. **Reuse** the §3.5.1 resolver as host glue (no per-plugin account logic).

This is the honest prerequisite: until it lands, a *posting* plugin cannot be witnessed — only a Tier-1
overlay can. The gap is small and the resolution data is fully present; nothing is invented.

### §13.2 Two prototype tracks

- **Track A — Tier-1 overlay, provable today, zero engine change.** Add one pill as a pure `pills.json`
  row (e.g. a new report/affordance) and witness that the bar grows with `handAuthoredCode=0`. This
  proves the metadata-plugin claim (§0, §10 `§PLUGIN-OVERLAY` + `§PLUGIN-MINIMAL`) on the *current*
  platform with no risk.
- **Track B — Tier-2 posting plugin, after §13.1.** The accounting-genome plugin (§3.5) — the real test
  of the thesis. Worked example below.

### §13.3 The worked test plugin (Track B) — "Sales Invoice → Post"

A real document type using real seed masters. The author writes only this manifest:

```json
{
  "id": "post.salesinvoice",
  "doc_type": "C_Invoice",
  "acctschema": 101,
  "masters": ["C_BPartner", "M_Product"],
  "charge": [
    { "event": "complete",
      "dr": [{ "acct": "{BPartner.Receivable}", "amt": "doc.GrandTotal" }],
      "cr": [{ "acct": "{Product.Revenue}",     "amt": "line.LineNetAmt" }] }
  ]
}
```

Host glue does the rest: resolves `{BPartner.Receivable}` → `c_bp_customer_acct.c_receivable_acct`
(e.g. C_ElementValue `234`) and `{Product.Revenue}` → `m_product_category_acct.p_revenue_acct` (e.g.
`229`) for the invoice's partner/product, emits the balanced `journal` lines via the `POST` verb, signs
them, folds the trial balance. The author never wrote a column name, a join, a signing call, or a
balance check. (Tax/rounding deferred — first cut posts net; `{Tax.Due}` is a later charge row.)

### §13.4 Acceptance witnesses (the prototype is "done" when these log)

- `§PLUGIN-OVERLAY tier=1 source=pills.json handAuthoredCode=0 pills=N+1` — Track A.
- `§PLUGIN-MINIMAL author wrote: lines=N files=1 newSkill=none` — the authoring-cost headline.
- `§PLUGIN-POST doc=C_Invoice#<id> dr=[234:amt] cr=[229:amt] balanced=Y (ΣDR=ΣCR)` — Track B posts,
  balances, accounts resolved from real masters.
- `§PLUGIN-CODE tier=2 verb=POST coupling=oplog directCalls=0` — posts only through the op-log bus (§6).
- `§PLUGIN-REPLAY pluginAdded → replay rebuildA==rebuildB agree=Y` — deterministic after the plugin.

When §13.1 + Track A + Track B log the above against `ad_seed.db`, the manifest spec is proven on the
real platform and the architecture moves from spec to demonstrated.

### §13.5 Resolved against `ad_seed.db` (build session 2026-06-02)

The §13.3 worked example, pinned to verified seed rows. The headless POC lives in `scripts/` only
(`poc_post.js` + `post_resolver.js`); no viewer wiring (separate, GO-gated session).

**Document picked.** `C_Invoice#103` (DocumentNo `200002`, `IsSOTrx=Y`, `C_BPartner_ID=117`,
`GrandTotal=161.12`, acctschema **101**), 3 lines, ΣLineNetAmt `152.00`, tax `9.12` (`c_invoicetax`).

**Tax is not deferred — superseding §13.3's "post net first" hedge.** Every sales invoice in this seed
carries tax (gap 2.85–12.95; no zero-tax invoice exists), so a net-only entry would either fail the
ΣDR=ΣCR invariant or post the receivable below the document's real GrandTotal. The honest balanced entry
is the three-line one, with `{Tax.Due}` resolved from `c_tax_acct`:

```
DR  {BPartner.Receivable}  234   161.12   ← doc.GrandTotal
CR  {Product.Revenue}      229   152.00   ← Σ line.LineNetAmt
CR  {Tax.Due}              255     9.12   ← Σ c_invoicetax.TaxAmt
                          ΣDR 161.12 = ΣCR 161.12
```

**Resolver bindings (verified, non-invent — each token → real column → real account):**

| Token | Source column (key) | Value | Underlying `C_ElementValue` |
|---|---|---|---|
| `{BPartner.Receivable}` | `c_bp_customer_acct.c_receivable_acct` `(c_bpartner_id=117, c_acctschema_id=101)` | `234` | 518 `12110 Accounts Receivable - Trade` |
| `{Product.Revenue}` | `m_product_category_acct.p_revenue_acct` (via `m_product`→`m_product_category`, `c_acctschema_id=101`) | `229` | — |
| `{Tax.Due}` | `c_tax_acct.t_due_acct` `(c_tax_id=105, c_acctschema_id=101)` | `255` | 596 `21610 Tax due` |

Resolver returns the master **column value verbatim** (`234`/`229`/`255` — `C_ValidCombination` ids,
mirroring `gl_journalline`); `§PLUGIN-RESOLVE` additionally logs the underlying `C_ElementValue` for
traceability. Fallback key when a `(master, acctschema)` pair is absent: `c_acctschema_default`. Absent
value → report `absent`, never synthesized.

**Engine glue (§13.1).** `journal` projection gains `account_id, amtacctdr, amtacctcr` (NULL-default —
existing `ALLOCATE` writers unaffected; all replay equality is relational, not hash-literal). New `POST`
verb (`edgeMint`/`applyOne` cases) receives the **already-resolved, already-balanced** lines, asserts
ΣDR=ΣCR before emit, writes one `journal` row per line, and is replayable like every other op. No
per-plugin account logic in the verb; the doc-type specifics stay in the manifest.

### §13.6 Fact_Acct-gated manifest compilation — rolling POST out to all postable doc-types

§13.5 proved the mechanism on ONE doc-type (`ARI`, Sales Invoice). The remaining postable models each
carry their own posting rule, and those rules are **not in the master tables** — they are encoded in
iDempiere's `Doc_*` posting classes (`Doc_Invoice`, `Doc_Payment`, `Doc_InOut`, …). The masters supply
*which account*; the `Doc_*` code supplies the DR/CR *structure* (which roles debit, which credit, which
amount). Re-authoring that structure from reading the Java would be invention — and invention is the one
thing the platform forbids. This section states how each model's manifest is **compiled and verified
against iDempiere's own output**, never guessed.

**The oracle: `Fact_Acct`.** iDempiere already executed every posting rule and recorded the realized
result in `Fact_Acct` — `(ad_table_id, record_id, account_id, amtacctdr, amtacctcr, c_acctschema_id, …)`
per posted document. That table *is* the rules made data. It is the ground truth a manifest must reproduce.
(`Fact_Acct` is empty in the curated `ad_seed.db`; the real GardenWorld rows are extracted from the
operator's Postgres — the `Fact_Acct` import is the migrate-agent / R2 lane, `scripts/extract_fact_acct.sh`.)

**The compile-and-gate loop, per doc-type:**

1. **Extract** the doc-type's real `Fact_Acct` rows (the oracle), keyed by `(ad_table_id, record_id)`.
2. **Author** a small charge manifest — the DR/CR role structure only (~5–10 lines, like §13.5).
3. **Discover tokens, don't guess them.** Group the oracle's lines by `account_id` and trace each back to
   the master column that holds it (the account-master tables carry the same ids `Fact_Acct` posted).
   The set of `{Master.Role}` tokens a doc-type needs is *read off* the oracle, then added to the resolver.
4. **POST and diff to the cent.** Run the `POST` verb over the real documents; compare the emitted `journal`
   lines against `Fact_Acct` per `(account_id, DR/CR)` (`scripts/diff_oracle.js`). **Accept the manifest
   iff the fold equals the oracle exactly.** A mismatch means the manifest or a token is wrong — fix and
   re-gate; never adjust the oracle.

A manifest is thus a **falsifiable restatement** of a rule iDempiere already ran, accepted only when it
reproduces that rule's posted facts. No `Doc_*` port, no invented account, no "trust me."

**Scope — postable is a subset of DocBaseType.** The seed's ~30 `DocBaseType`s are not all postable:
orders (`SOO/POO/DOO/MQO`) carry no `Fact_Acct` in iDempiere and are excluded. The postable set is
invoices (`ARI/ARC/API/APC`), receipts/payments (`ARR/APP`), allocations, inventory moves
(`MMS/MMR/MMI/MMM`), and journals (`GLJ/GLD`). Each is one oracle-gated manifest; the `POST` verb and
resolver from §13.5 are reused unchanged — only manifests (data) and any newly-discovered tokens are added.

**Honest bound.** This proves **structural fidelity to the operator's posted history**, not a
from-scratch GAAP engine. Rules with no oracle coverage (a doc-type never posted in their data) cannot be
gated and are reported `uncovered`, never assumed. Edge behaviour (tax rounding, currency revaluation,
landed-cost, inter-org) surfaces as a visible cent-level mismatch to resolve — it is not hidden by the fold.

**Witnesses.**
- `§PLUGIN-ORACLE doc=<DocBaseType> docs=N foldRows=R oracleRows=R agree=Y (Σ|fold−Fact_Acct|=0c)` — the
  doc-type's manifest reproduces real `Fact_Acct` to the cent across all sampled documents.
- `§PLUGIN-TOKENS doc=<DocBaseType> discovered=[{Master.Role}…] source=Fact_Acct.account_id` — tokens read
  off the oracle, not authored.
- `§PLUGIN-COVERAGE postable=K gated=G uncovered=[…]` — honest accounting of which postable types have an
  oracle to gate against and which do not.

**Dependencies / lane.** Needs the real `Fact_Acct` import (migrate-agent / R2 lane) and reuses the
existing `diff_oracle.js` / `extract_fact_acct.sh` machinery. Engine-lane build, headless `scripts/`; a
SEPARATE, EXPLICIT-GO session after the `Fact_Acct` extract lands. Spec only here.

### §13.7 "Accts Posted" — the role-gated read-fold (the engine seam for the record panel)

The renderer's record panel (now that login is merged — PR #87, `erp/idmp_session.js`) wants three
affordances on a document: **edit the record**, a **DocAction** control (the gear), and — *only for an
accounting role* — **see the postings** ("Accts Posted"). The first two are renderer work over verbs that
already exist (`SET_STATUS` = DocAction, the write path = `dispatch`). The third is an **engine seam** and
is specified here so the gate is enforced in the engine, never in the panel.

**Posted-status is derived, not a column.** In the op-log model a record is *posted* iff a `POST` op
(§13.5) exists for it. The panel never reads a `Posted` boolean; it asks the engine for the fold.

**The call (an `ENGINE_CONTRACT.md read` with role ctx):**

```
readPostings(recordRef, ctx) -> { visible, posted, lines[], balanced, source, coverage, note, reason }
  recordRef = { doc_type | ad_table_id, record_id }
  ctx       = IdmpSession.buildContext(...) → uses ctx.role.id (+ ctx.client.id, ctx.org.id)
  source    = 'fact_acct' | 'oplog' | 'none'   — WHERE the fold came from
  coverage  = 'complete' | 'partial' | 'absent' — how much of the posted history is present
  note      = a human hint when coverage<complete (e.g. "install local data for full posted history")
```

**The gate (engine-resolved, not UI-trusted).** `idmp_session` folds user→role→client→org but does NOT
carry `isshowacct`; the engine resolves it itself: `SELECT isshowacct FROM AD_Role WHERE AD_Role_ID =
ctx.role.id` (verified values: GardenWorld Admin=Y, GardenWorld User=N).

- `isshowacct ≠ 'Y'` → return `{ visible:false, reason:'role-not-accounting' }` — **no `lines`**. Access
  enforced in the engine (an empty/forbidden result, never the data — same rule as `ENGINE_CONTRACT`
  WIRE-4 reads scoped to org access). The panel hides the Posted tab; it cannot leak the rows by mistake.
- `isshowacct = 'Y'` →
  - not posted (no `POST` op for the record) → `{ visible:true, posted:false }` — the panel shows "not
    posted"; if the doc is completable, the gear's DocAction can post it (`SET_STATUS`→ the §13.5 `POST`).
  - posted → `{ visible:true, posted:true, lines:[{account_id, value, name, amtacctdr, amtacctcr}],
    balanced:true }` — the balanced journal fold of the record's `POST` op, each account joined to its
    `C_ElementValue` (value/name) for display. Once §13.6 lands, the same fold is cent-gated against the
    operator's real `Fact_Acct`.

**Org/client scope.** Postings are filtered by `ctx`'s org access (one filter model: role→menu,
client/org→rows, role.isshowacct→accounting visibility) — a record outside the role's orgs returns
`{ visible:false, reason:'out-of-scope' }`, never rows.

**Data availability — graceful degrade, separation from the import lane (the contract's stub-not-guess
rule).** The full posted history lives in the operator's real `Fact_Acct`, which arrives via a SEPARATE
local-import session (S1). The panel must NOT hard-block on it; the engine reports what it HAS and the UI
reflects it honestly. `source`/`coverage` carry this, so the renderer never needs to know the import lane:

- `Fact_Acct` imported (§13.6 cent-gated) → `source:'fact_acct', coverage:'complete'` — the real, full fold.
- `Fact_Acct` absent but `POST` ops exist (resident, e.g. posted this session) → `source:'oplog',
  coverage:'partial', note:'showing op-log postings — run local install for the full posted history'`. The
  panel **renders what it has** and shows the note. *(Your "continue with what data there is" mode.)*
- nothing posted and no `Fact_Acct` → `source:'none', coverage:'absent', posted:false,
  note:'install local data first to see Accts Posted'`. The panel shows the honest status, not an error.
  *(Your "install local first" mode.)*

This is what lets **S3 ship before S1/S2** (CRUD-P + Receipt + the Accts-Posted *shell*); when real data
lands, the same tab lights up with `coverage:'complete'` — no panel change. Honest by construction: the UI
never invents a posting, and never hides that the history is partial.

**Witnesses.**
- `§POSTED-READ record=<doc>#<id> role=<id> isshowacct=Y posted=Y rows=N balanced=Y source=<src> coverage=<cov>`
  — accounting role sees the balanced fold, labelled by source/coverage.
- `§POSTED-GATE role=<GardenWorld User> isshowacct=N → visible=N reason=role-not-accounting rows=0` — a
  non-accounting role is refused at the engine, zero rows leaked.
- `§POSTED-COVERAGE source=none coverage=absent note=install-local-first` (and the `partial`/`complete`
  variants) — the degrade states are explicit, never a silent empty or a fabricated total.

**Lane.** Engine provides `readPostings` (headless, gate + fold, `scripts/`); the renderer renders the
Posted tab (shown iff `visible`), the gear→DocAction, and the editable form — renderer/login lane, rebased
onto the `erp/` folder home, EXPLICIT-GO. The gate and fold are the engine's; the UI only reflects them.

### §13.7a Built — resolved against `ad_seed.db` (build session 2026-06-03)

`readPostings(recordRef, ctx, dbs)` lives in `scripts/erp_postings.js`; witness `scripts/poc_postings.js`
(`build/erp/poc_postings.log`). `dbs = { ad, proj, fact }` — `ad` carries `ad_role`/`c_validcombination`/
`c_elementvalue` (the seed); `proj` is the `erp_kernel` projection (POST ops + `journal`); `fact` is the
`fact_acct` bundle. The exact §13.7 shape is returned: `{visible, posted, lines[], balanced, source,
coverage, note, reason}`.

- **Gate (engine-resolved):** `SELECT isshowacct FROM ad_role WHERE ad_role_id = ctx.role.id` on `dbs.ad`
  (verified: Admin `102`=Y, User `103`=N). `isshowacct ≠ 'Y'` → `{visible:false, reason:'role-not-accounting'}`,
  **no `lines`**, zero rows. Org scope: `recordRef.ad_org_id ∉ ctx.allowOrgs` → `{visible:false,
  reason:'out-of-scope'}` (mirror `ENGINE_CONTRACT` WIRE-4).
- **Fold:** the record's `POST` op's `journal` rows (`source='DOC:'+table+'#'+id`); each `account_id`
  (a `C_ValidCombination` id) joined `c_validcombination → C_ElementValue` for `{value, name}` (verified:
  `234→518 "12110 Accounts Receivable - Trade"`). `balanced = ΣamtacctDR == ΣamtacctCR` to the cent.
- **Degrade ladder (honest, non-invent):** real record-keyed `Fact_Acct` cent-equal to the fold →
  `source:'fact_acct', coverage:'complete'`; `POST` op only → `source:'oplog', coverage:'partial'`; nothing
  → `source:'none', coverage:'absent'`.

**⚠ Coverage bound — `complete` is NOT reachable on the bundled data, by design.** The shipped `fact_acct`
(`extract_fact_acct.sh`, `glassbowl_data.db`) is a **TOTALS** extract — it has **no `ad_table_id`/`record_id`**,
so no record maps to it; every real record folds to `partial` (posted this session) or `absent`. Per-record
`complete` needs the §13.6 **re-extract WITH the record-ref columns** — NOT done this session (the prompt's
"do not do speculatively"). The `complete` *branch* is exercised in the witness by a clearly-labelled
**shape fixture** (`fact=fixture realdata=N`) carrying `(ad_table_id, record_id)`, plus an **off-by-1c
discrimination** check proving the cent-gate falls back to `partial` on mismatch — never a tautology, never
presented as GardenWorld fact.

- **Witnesses:** `§POSTED-READ record=C_Invoice#103 role=102 isshowacct=Y posted=Y rows=3 balanced=Y
  source=oplog coverage=partial`; `§POSTED-GATE role=103 isshowacct=N → visible=N reason=role-not-accounting
  rows=0`; `§POSTED-COVERAGE` for `absent` / `partial` / `complete`(fixture) + the off-by-1c reject.

---

*Status: DRAFT v0.1, 2026-06-02 — extension/plugin layer of the iDempiere 2.0 lineage. Nothing built;
EXPLICIT GO required before any code or deploy. Siblings: IDEMPIERE_2.md (model) · IDEMPIERE_RENDERER_SPEC.md
(UI) · PILL_MANIFEST_SPEC.md (the live registry-as-data precedent). Prior art in §2 is cited as pattern,
never source. The synthesis claim (metadata plugins + op-log bus + signed replay + SQLite-WASM
local-first) is bounded as novel only where §2 shows no single prior system combines all four.*
