# The Credit Ledger Lens — *udhar*, the fold that runs the corner shop

*The single most-used feature of the apps the un-served already love (Khatabook, OkCredit — tens of millions of
shopkeepers): "who owes me how much." A model addition to [Distributed ERP](DistributedERP.md) — receivables as a
fold — and the value path that makes a free ledger sustainable. Sibling to [POSLens](POSLens.md),
[WMSLens](WMSLens.md), [SocialPlatformLens](SocialPlatformLens.md).*

---

## 0. Why this document

A corner shop runs on **credit** — the customer takes goods now and pays later (the *udhar* / tab / *bahi khata*).
The khata apps proved this is the *daily* reason a shopkeeper opens anything: not inventory, not reports —
**collections.** We modeled inventory and sales but not **receivables.** This doc adds them — and adds nothing new
to the engine, because credit, like everything else, is just a fold.

## 1. Credit is a fold — never a stored balance

A customer's balance is **not a cell you mutate.** It is `Σ(credit sales to them) − Σ(their payments)` — the same
root truth as `QtyOnHand` (the fact is a fold over a signed sequence). "Ramesh owes RM240" is *derived* from his
signed orderlines and payment ops, never held as a guarded scalar. So there is no balance to corrupt, no
race to update, no reconciliation to run — the number is recomputed from the log on demand, by any node.

A **credit sale** is the ordinary sale (sacred, master-referenced, scan-born) carrying a *counterparty identity* and
a *terms* note. A **payment** is its own op that the fold nets against the customer's open lines. Aging — "how long
overdue" — is a fold too: bucket the unpaid lines by date. Nothing here is a new verb.

## 1a. Integrated into the ERP — a substrate of its sales, not a parallel ledger

The credit ledger is **not** a khata bolted beside the POS; it is **a fold over the sales the POS already makes** —
the ERP's own Accounts Receivable, surfaced simply. It reuses the iDempiere model verbatim (EXTRACT, not invent):

- **`C_BPartner.SO_CreditLimit`** — the customer's allowed tab (already in the AD dictionary);
- **`C_PaymentTerm`** — when a credit sale is due (drives the aging buckets, §4);
- **`C_Order → C_Invoice → C_Payment → C_AllocationLine`** — the settlement chain (the very lifecycle the Glassbowl
  trace already walks). **Receivable = open invoices − allocations, folded per partner. No new table.**

So a **credit sale at the POS** is an ordinary sale that carries a `C_PaymentTerm` instead of cash tender; the
**balance** is the AR fold; the **payment** clears it through the allocation chain. The shopkeeper's "who owes how
much" and the accountant's Accounts-Receivable aging are **the same fold, two lenses** — *udhar*-simple for the
corner shop, full AR for when it grows, never a re-platform.

**The credit-limit guard (a sacred-transaction check at the till).** Before a credit sale completes, fold the
partner's open AR + this cart and compare to `SO_CreditLimit`: under → allow; over → warn or block (a *named
exception*, not a silent overrun). Both numbers are derived/real — the limit from the partner master, the balance
from the fold — so the check is non-invent.
`§POS-CREDIT partner=… open=… cart=… limit=… decision=allow|warn|block`.

This is what makes the lens **integratable to the POS and part of the ERP**: not a side-ledger to reconcile — it
*is* the ERP's receivable, computed from the sales substrate, shown at whatever altitude the user needs.

## 2. The two-sided credit fact — the parties, again

Credit is the purest case of *the transaction is the parties.* A tab is a **mutual** fact: the shop says "given on
credit," the customer acknowledges "I owe." A unilateral "you owe me" with no counterparty act is a *claim*, not a
debt — which is exactly why a disputed tab is settled by the customer's acknowledgment (a signed party-act: a tap, a
QR, a reply), not by the shop's word alone. Bind the credit line to the customer's identity at the point of sale (a
scanned member QR, a phone number, a chat reply) and the debt is two-sided and self-evident. *No central control:*
the balance is whatever both parties' folded records agree on.

## 3. Collection — ride the guaranteed channels

The shopkeeper's job is *getting paid*, and that is all native-intent + the country's payment rail (see
[GuaranteedChannels](GuaranteedChannels.md)):
- **Reminder** → `wa.me` / `sms:` with a one-line balance and a **pay-link** (the khata-app pattern, validated).
- **Collection** → a **pay-QR on the country's rail** (UPI / M-Pesa / PIX / DuitNow) — the customer scans, pays once.
- **Fold-back** → the payment confirmation appends a payment op that nets the open lines; the balance drops itself.

The payer is the serializer (*they pay once* — §POSLens), so a double-collection can't hide: it shows as an
over-payment/credit on the customer's two-sided record. The reminder is a *push to a feed card* (a TikTok-of-ERP
exception: "Ramesh — RM240, 18 days — 📞 💬 [Remind]"), not a report to go hunting for.

## 4. The aging fold — perpetual, no day of reckoning

Because the balance is always derived, the shopkeeper is **never blind to who owes what** — at zero effort, updated
by every sale and payment. There is no monthly "do the books" ritual; the overdue list is a standing fold surfaced
as exceptions. Frequency of *acting* (reminding) is a choice; *visibility* is constant. Same property as perpetual
inventory: the floor is "I know the number," never "I have no idea who owes me."

## 5. The bankable ledger — the value path (horizon, not build-now)

Here is the strategic mine. Khatabook keeps the ledger **free** and monetizes by **lending** — underwriting loans on
the shop's transaction history. Your ledger is a *better* credit substrate than theirs for one reason: it is
**signed, non-invent, tamper-evident.** A khata entry can be typed to flatter the books; a sacred-transaction fold
**cannot** — every figure traces to a scan, a master reference, or a counter-signed payment, with theft and dupes
self-evident. So a shop's own fold is an **un-fakeable credit history.**

That makes the sovereign ledger *bankable*: shared **by the owner's consent** with a lender, it underwrites credit
without anyone hosting the data — the lender verifies the signatures, not a database. The value paths that opens —
working-capital loans, BNPL for the shop's own customers, inventory insurance — are how a free, MIT, no-server tool
**sustains itself**, and they are precisely where provenance stops being a principle and becomes *money.* This is a
horizon, named so the doctrine carries it: **the fold is a credit record; its sacredness is its collateral.** Do not
build lending now — build the receivables fold and the reminders; the bankability is the reason to keep it sacred.

## 6. Honest scope

- **Build (model addition):** the receivables/payment fold + per-customer ledger + aging — a genuine gap, but a fold,
  not a new engine. Gated on the write-path like the other lenses.
- **Ride (existing):** reminders + collection via [GuaranteedChannels](GuaranteedChannels.md) (native intents +
  country payment rail) — no new infra.
- **Horizon:** lending / BNPL / insurance on the sovereign, signed ledger — the sustainability path; build only when
  the fold and a real shop's history exist, never before.

The promise: **the corner shop's real daily need — "who owes me, remind them, get paid" — is one more fold over the
same signed log, collected through channels the shop already uses; and because the fold can't lie, the same ledger
that runs the shop can one day finance it.**

---

*Companions:* [POSLens](POSLens.md) · [WMSLens](WMSLens.md) · [SocialPlatformLens](SocialPlatformLens.md) ·
[GuaranteedChannels](GuaranteedChannels.md) · [DistributedERP](DistributedERP.md).
*Prior art mined:* [Khatabook](https://khatabook.com/blog/khatabook-app-features/) ·
[OkCredit](https://okcredit.com/) (free ledger, WhatsApp/SMS reminders, collection QR, lending on ledger data).
