# The Lens Family — one horizontal ERP, many vertical lenses

*No reinvention: **the ERP is already inside.** iDempiere's full Application Dictionary is migrated raw into SQLite
(Step 0, verified — 0 mismatches, no column-strip), so every entity a business needs already exists. We do not
rebuild ERP; we **surface** it. The **horizontal** is the proven 20-year iDempiere/ADempiere model; the **lenses are
the verticals** that ride it. This doc is the hub for the family and the frame that ties it together.*

---

## The horizontal — already inside (EXTRACT, not invent)

- The full iDempiere AD — `C_BPartner` (incl. `SO_CreditLimit`), `C_Order`, `C_Invoice`, `C_Payment`,
  `C_AllocationLine`, `C_PaymentTerm`, `M_Product`, `M_Locator`, `M_Movement`, BOMs … — migrated raw into SQLite and
  **verified** (`build/erp/verify.log`, per-table counts vs Postgres = 0 mismatches). The model is *inside*.
- **AD-gen** (`scripts/gen_ad.js`) generates that dictionary from *any* source and the renderer draws it — the
  horizontal is renderable, not just stored.
- The **fold** (signed op-log) + the **guaranteed channels** (email / Drive / native intents) = the serverless
  substrate the horizontal runs on, with no server to host.
- **Every lens field traces to an AD column already present.** No new schema per use-case. That is what "no
  reinvention" means concretely.

## The verticals — the lenses (each a view, a provider, or a stub over the one horizontal)

| Lens | Edge it serves | Doc |
|---|---|---|
| **POS** | in-person retail / restaurant sale (scan-serialized; expiry, split, loyalty) | [POSLens](POSLens.md) |
| **WMS · Logistics · Robots** | movement: pick/putaway, spatial floor, delivery/GPS/route, actuators | [WMSLens](WMSLens.md) |
| **Social Platform** | on-the-move: chat/email/feed UI, native-intent action icons (*a TikTok of ERP*) | [SocialPlatformLens](SocialPlatformLens.md) |
| **Credit Ledger** | receivables / *udhar* = AR fold (`SO_CreditLimit`, `C_PaymentTerm`); bankable history | [CreditLedgerLens](CreditLedgerLens.md) |
| **Workforce** | attendance (rolling-QR clock-in) & task management (`AD_User`, `R_Request`) | [WorkforceLens](WorkforceLens.md) |
| **Guaranteed Channels** | the transport & payment pipes (email / Drive / USSD / national rails) | [GuaranteedChannels](GuaranteedChannels.md) |
| **Doctrine** | the distributed / serverless truths the family rests on | [DistributedERP](DistributedERP.md) |

Implementation work-order for the first vertical: `prompts/POS_LENS_SESSION.md` (gated on the front-end write-path).

## The horizontal consolidates — one log, no silos, the long tail is the asset

The lenses **fan out** (verticals, at the edge, writing signed acts); the horizontal **folds in** (the one log,
reading, consolidating). Because *every* lens — POS, WMS, Credit, Workforce, Social — writes to the **same** signed
log, the horizontal accumulates the **complete, cross-lens history of everything the business does.** Three
consequences:

- **No silos.** The classic ERP failure is modules with separate databases you cannot cross-cut. Here there is one
  log, so *any* cross-lens question folds: labor-cost-per-sale (POS × Workforce), end-to-end shrinkage (WMS × POS),
  customer profitability (Credit × POS), waste-vs-yield (POS × backflush). **The BI / data-warehouse is the fold
  itself** — no ETL, no second store.
- **The long tail is the asset.** Years of granular, signed acts across every lens are the audit trail, the analytics
  substrate, and the **bankable credit history** ([CreditLedgerLens §5](CreditLedgerLens.md)) — all growing from one
  consolidated fold, none of it able to lie (sacred provenance). The longer it runs, the more valuable it gets.
- **Bounded by period-close.** The tail stays bounded without losing history: the **period-close signed checkpoint**
  consolidates it into balance-brought-forward (DistributedERP compaction; the volume POC stayed flat across 100×
  history), while the cold tail remains reconstructible. Consolidation scales.

So the horizontal is both the **schema substrate** (the entities, already inside) *and* the **consolidated memory**
(the one log). Verticals act; the horizontal remembers — everything, in one place, signed.

## Why a vertical is cheap (the meta-principles)

- **One model, many lenses — fold-not-fork.** A lens consumes the seam and the same verbs; it never forks the model.
  The test: does it write through the same fold, or need its own state? Fold → it's a lens; own state → it's a fork.
- **A lens is a view, a provider, or a stub** over the horizontal — *glue, not a new engine.*
- **Use what is guaranteed; ride the killer apps.** The device is the app suite (dialer, maps, camera, wallet); the
  ERP is glue; the server deletes itself.
- **One source act, the rest is a fold** (GIGO solved); **the transaction is sacred** (provenance unbroken); **no
  central control** (the fact is a fold over a signed sequence, computable by any node).

## Why it matters strategically

- **No reinvention** → the moat was never rebuilding twenty years of ERP logic (inherited free, MIT) — it's the thin
  verticals plus the serverless-fold doctrine over a *proven* horizontal.
- **The horizontal is universal** → therefore *any* vertical is reachable as a lens. A new business is a new lens,
  not a new schema. That is the AnyAppMaker / ERPMaker claim, grounded: the dictionary already has the entities;
  AD-gen renders them; a lens shapes them for the use-case.
- **The staircase is one identity, one model.** A user starts at one vertical (the POS front door) and grows into the
  others — WMS, credit, delivery, social — *without re-platforming*, because they were always lenses on the same
  horizontal that was inside from the start.

---

*Family:* [POSLens](POSLens.md) · [WMSLens](WMSLens.md) · [SocialPlatformLens](SocialPlatformLens.md) ·
[CreditLedgerLens](CreditLedgerLens.md) · [GuaranteedChannels](GuaranteedChannels.md) ·
[DistributedERP](DistributedERP.md).
