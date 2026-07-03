# The POS Lens — the doctrine, made a shop you can run today

*Extracted from my iDempiere Unicenta POS plugin, ported to the browser and the signed op-log. This is the
worked example of [Distributed ERP](DistributedERP.md) — the smallest complete proof that the doctrine is not
theory: a corner shop you can run from a phone, where nothing in the books can lie.*

---

## Why this document

Years ago I wrote a plugin that fed [Unicenta POS into iDempiere](https://wiki.idempiere.org/en/Plugin:_Unicenta_POS)
— a fork of Openbravo POS, synced to the ERP over [Apache ActiveMQ](https://wiki.idempiere.org/en/How_to_setup_POS_ACTIVEMQ),
with [AutoBOMOrder](https://wiki.idempiere.org/en/Plugin:_AutoBOMOrder) backflushing recipes and generating
replenishment. The premise was always the same: *the POS should be dumb* — record the sale, take payment, send
the order; let the ERP hold the intelligence. My stated roadmap was "a ring of dumb POS stations around one
iDempiere ERP."

The browser makes that roadmap free. What needed ActiveMQ middleware and a fat Java client to stay thin is now
a **lens** — a view over the one model, dispatching verbs, holding no truth. The same idea, with the plumbing
deleted. This document explains the concept; the implementation work-order lives in `prompts/POS_LENS_SESSION.md`.

## 1. One source act, and the rest is a fold

GIGO — *garbage in, garbage out* — is treated everywhere as inevitable, so every ERP fights it downstream with
validation rules, approval chains, reconciliation runs, and a controller to police entries. That whole apparatus
exists to catch garbage *after* it is already in.

The POS lens makes the apparatus unnecessary by fixing the **input**. Collapse everything the system will ever
ingest to a **single authentic act** — the scan, the transaction, the meeting of a party with an item — and lock
that act to non-invent. Then there is exactly one place garbage could enter, that place is sealed, and everything
else is a deterministic fold over the acts. It is no longer "garbage in, garbage out"; it is **one clean act in,
all truth out**. Replenishment, perpetual stock, theft-to-the-unit, the day's report — none of them take input.
They only fold the one thing that does.

This is also *why* it needs no central authority. The only reason an ERP requires a central controller is to
police GIGO. Remove the garbage at the source and the policeman has nothing to do. Clean-by-construction input is
what *earns* the serverless, no-central-control architecture — it is the dividend of fixing the act.

## 2. The transaction is the truth — the parties

The terminal does not send a "sale." It sends **orderlines** — the irreducible facts: *this item, this quantity,
this station.* An order, an invoice, a stock movement, a replenishment PO are not things that get stored and
guarded; they are **views computed** from the stream of orderlines. (This is the root truth of Distributed ERP:
*the fact is a fold over a signed sequence, never a stored scalar.*)

So there is **no central source of truth and no centralised control.** Truth is the fold over the signed sequence,
and any node holding the log can compute it — which means no one node *is* the authority. The "ring of stations
around an ERP" is not a hub; it is peers sharing one signed log.

And the truth-bearing unit is the **transaction itself — the parties.** A one-sided record is not half a
transaction; it is an *assertion looking for a counterparty.* A real transaction is the parties meeting, and it
does not exist unless both are in it. That is why a fabricated charge has nowhere to hide: it points at an event
that never happened — no second customer-act, no second exchange — and its hollowness is exactly what exposes it.

## 3. Reality is the serializer — so no one has to be

The classic objection to "no central control" is contention: two tills sell the last unit at the same second.
But **there is no conflict to resolve, because reality already serialized it.** A physical item can be in exactly
one cart because it can be in exactly one hand. The *scan is the lock, and physics holds it.* Two sales cannot
scan the same item — there is one item and one scan.

Payment serializes the same way, by the **counterparty.** You cannot charge a present customer twice for one
haircut, because it is their money and they pay once. The payer is the lock on the payment, exactly as the shelf
is the lock on the goods. Every transaction has a natural serializer built into the world, and it is *never* the
shop's server — it is the other party, who will not ratify the duplicate.

So no-central-control loses nothing: each conflict has a counterparty who refuses the dupe, and the only genuinely
contended case (two tills, the real last unit) is a sub-millisecond local decision, not a distributed problem.

## 4. The sacred transaction — unbroken provenance

Nothing in a transaction is ever typed from nothing. Every field has a provenance, and there are exactly two
legal sources:

- **Master-data references** (the *what* and the *how-much*): the orderline does not carry a price, it carries a
  reference that resolves to a sealed master price. You cannot scan a blank and key a price, because price was
  never an input — it is looked up.
- **Party-acts** (the *that-it-happened*): the scan, the count, the tender. Real events, not keystrokes.

The forbidden zone — the only thing this outlaws — is **free human keying of value.** There are **no free numbers**:
price comes from the master, sale quantity from the scan, on-hand from the fold, replenishment from the fold,
receipts from the scan. The one number a human still supplies, the occasional physical count, only ever
*reconciles* against the derived figure; it never silently *sets* it, and a variance becomes a signed adjustment
with a name on it.

The result is one **unbroken provenance chain**:

> source sheet → master import (`handAuthored=0`, every row traced) → catalog → orderline (master-reference +
> party-act) → signed op → fold → report → receipt.

To forge a transaction you would have to break a *signed* link. There is no point in the chain where a value
enters from nothing — which is what makes "the transaction is the truth" *enforceable* rather than merely true.

**A clarification, not a contradiction.** Keying a price or quantity while *authoring the master* is legitimate:
the owner is the authority for their own goods, declaring them once — the legitimate *origin* of provenance.
Keying at *transaction* time is forbidden: a sale scans and pulls the sealed price. **You key the master, never
the sale.**

## 5. Scan and QR — literacy-free, non-invent, and the zero-install door

The most accessible interface on earth is also the most non-invent one, and that is no coincidence. A QR scan
*removes the keyboard* — there is nothing to mis-key, because the value arrives pre-formed from the source. The
non-literate street vendor and the "you cannot key a price" rule are the **same requirement**. Alipay proved this
at the scale of a billion people, including the rural and non-literate — the demand was never hypothetical; it was
measured.

The QR is also the **zero-install door** — a side entrance into the whole system that skips the enterprise front
door entirely. No app store, no account, no training: a sticker on a wall is the client. That frictionless reach
is only *safe* because of §4 — a QR is trusted only if it carries a signed, master-referenced payload, so a
swapped or fabricated code has no valid provenance and the verb rejects it. **The QR carries the transaction; the
signature makes the QR sacred.** Point, scan, done — and nothing false can ride in.

## 6. Backflush — sell a burger, the kitchen reorders itself

Ring one burger. That single act folds down the recipe and decrements **bun ×1, patty ×1, sauce ×Xg**, and when
any component crosses its minimum, *each* throws its own replenishment. Nobody counts a bun; nobody — and this is
the point — counts grams of sauce. The explosion is **recursive**: the bun may be its own recipe, the sauce a
sub-recipe, so one sale at the till cascades to flour grams in the back room. (Backflush *is* deterministic replay
of the BOM — the same verb that compiles a building, run at point of sale.)

At the granularity of grams there is no manual alternative — you cannot hand-count sauce, you cannot hold a daily
reorder meeting across every ingredient in every recipe. Backflush is not the *smarter* way; it is the *only
feasible* one.

And it yields the number food service lives or dies on. Backflush gives **theoretical** consumption (what the
recipe says you should have used); the occasional count gives **actual**; the gap is *theoretical-vs-actual food
cost* — over-portioning, waste, theft — surfaced to the gram, for free. The system's job is not to watch the
cook's hand; it is to compute the theoretical so precisely that the gap to actual becomes the signal.

## 6a. POS features beyond the sale — each a fold, not a module

The features a shop expects aren't modules to bolt on — they're folds over the same signed log, and one of them is
maintenance-free in a way only this architecture allows.

- **Expiry / FEFO (the ⏳ icon on the pill).** Expiry is a master/lot attribute (Aisle/Bin/**Lot**). Scanning a lot
  picks its expiry; **FEFO** (first-expiring-first-out) sells the oldest lot first; near-expiry raises a discount
  prompt; expired *refuses the sale* (a named exception, never a silent pass). Spoilage is a `MOVE(item →
  waste-locator)` that folds into the theoretical-vs-actual variance (§6) — waste surfaces to the unit, like theft.
  Essential for grocery, food, and pharma.
- **Split bill.** One sale, **N payment ops.** The orderlines stay a single sacred transaction; the tender splits
  into multiple signed payments (by item, seat, or share) that net against it — each payer serializes their own
  portion (§3). No new verb; the two-sided fold already handles multi-tender.
- **Loyalty — and it is *maintenance-free*, because it lives in the buyer's phone.** This is the doctrine's gift: the
  shop runs **no loyalty database.** A customer's loyalty *is a fold over their own signed receipts* — the receipt
  URLs they already collect (§ the lens loop, "receipt URL to the buyer") **are their loyalty card.** Points = `Σ`
  over the customer's receipts, held on *their* phone, sovereign and portable. At the till the customer presents their
  QR/identity; the shop reads and credits their fold; redemption is a two-sided signed act (customer presents, shop
  honors). Nothing for the shop to host, back up, or migrate — the record is the customer's, distributed across every
  buyer's phone, and it can even travel *across* merchants who honor the same fold.

The pattern: **a POS "feature" is a new fold or a new icon over the one model — never a new system.** Expiry is a lot
attribute folded into picking; split is multi-tender folded into payment; loyalty is the buyer folding the receipts
they already hold.

## 7. Theft and shrinkage surface exactly — and no daily count is needed

Ten items. The cashier sells seven (seven signed transactions) and steals three (no transaction). Replenishment,
driven by the fold, reorders **seven** — it never knew the stolen three left, because theft leaves no record. So
the shortfall is *self-announcing*: book stock (10 − 7 = 3) versus physical count (0) is a 3-unit gap, computed to
the unit, and it does not wash out when the seven arrive. The thief's escape routes close the same way: ring the
three and pocket the cash, and the *drawer* is short; ring them with no payment, and the orderline is *unmatched*.
This is double-entry enforced at the shelf edge — the missing entry always has a missing partner pointing at it.

Because no one keys stock, there is **no lever to falsify the book** — that is what makes the gap unhideable. And
the book is always known, continuously, at zero effort, because every sale already updated it. So the physical
count stops being a chore and becomes an audit you run when you like. **No daily count is necessary**: this is
perpetual inventory — the discipline big retailers paid ERP fortunes for — handed to the corner shop in exchange
for the one act they already perform. Frequency only buys *attribution* (how finely you can point the finger),
never *visibility*. The floor is "I know the number," never "I have no idea." The honest boundary: the fold tells
you the shortfall is exactly three; *whether* it was theft, breakage, or a miscount still needs a human — but the
quantum is unhideable and bracketed to a shift.

## 8. Trust is a dial — solo, then staffed

The lone owner is not cheating themselves; they want a working system *now*. For them the lock-and-enforce rigor
is pointless friction — they want the *convenience* (perpetual stock, auto-replenish, the nightly summary), not
the *policing*. So the system ships in **trust-yourself mode**: author freely, scan stock as it arrives (receiving
*is* the onboarding), no qualify, no lock.

The day they hire a cashier, they flip on **sacred mode**: the master locks, sale-time keying is refused, theft
surfaces to the unit — *because now there is someone who could.* Enforcement is a **dial set by headcount**, not a
fixed overhead. That is "no central control" applied to the security model itself: the policy fits the actual
trust situation, chosen by the owner, never imposed uniformly.

## 9. The lone-owner loop, end to end

A complete shop, in hand, with zero keying of value and zero counting:

1. **Onboard** — snap a photo (human identity, the tile and the receipt), scan the QR (machine key), key price and
   opening quantity *as the owner authoring their own master*, or just scan stock as it arrives. ~10 seconds an item.
   Or import the whole catalog from a spreadsheet — two on-ramps to one master.
2. **Sell** — tap or scan the item; the price comes from the sealed master; tender; one signed orderline appended.
3. **Receipt URL to the buyer** — the counterparty's signed copy and their **e-invoice** (MyInvois / EU mandate)
   in one artifact: the signed orderline *is* the e-invoice.
4. **End-of-day email to self** — fold the day (sales, stock, reorder, variance) and mail it. No dashboard, no login.

Scan in as it arrives → sell → buyer gets a URL → you get a nightly email. No server, no central authority, no
clue lost.

## 10. The lineage — what moved

| Then (my iDempiere plugin) | Now (the lens) |
|---|---|
| ActiveMQ queues per station | one signed op-log, merged; offline-capable |
| Unicenta (fat Java/OSGi client) | a thin browser lens dispatching verbs |
| AutoBOMOrder + Replenishment Report | the fold (recursive backflush + reorder, derived) |
| "send the completed order to the ERP" | append orderlines where you physically stand |
| a ring of stations around one ERP | peers sharing one log, no hub |

The plugin's contributors — Ghintech, and Eduardo Gil who later improved the Unicenta integration — pushed it
toward a *fatter* client. The lens deliberately goes the other way, back to the minimalist fork: the terminal
records, takes payment, and sends; every rule resolves in the fold. The browser is what finally lets the terminal
be as dumb as it always should have been.

## 11. Honest scope — what is proven, what is to build

- **Proven and real:** the iDempiere Unicenta POS plugin (deployed, documented, in production use historically),
  and the Distributed ERP doctrine it illustrates (the falsifier POCs in `scripts/poc_*.js`). The arithmetic in
  this document — backflush, shrinkage, the fold — is the iDempiere logic I worked in and noticed.
- ~~**Specified, not yet built:** the browser POS *lens* itself.~~ **SUPERSEDED — the lens is LIVE.**
  Shipped on idempiere.html: §P-1..§P-4 sale loop bim-ootb PR #269 (sw v652) with the to-the-cent posting ring
  closed on the default seed PR #271 (v653, `§POS-CENT maxDiff=0c`); the full CRUD/void/replenish loop PR #274
  (v655); the DIY killer demo — album cards, floating payment panel, Import pill (snap+scan+price → §P-9
  register), DEMO payment QR, §P-13 hold/recall — PR #276 (v656); sha256 content-addressed photos + tamper-gated
  read path PR #277 (v657). Engine ledger: `docs/ERP_COVERAGE_MATRIX.md` third-axis POS rows (W-POS-* witnesses).
  The deliver-later sale (§P-12 seam: shipment born DR, the pick moves on-hand) is engine-green
  (W-POS-DELIVERLATER); its walk-side §S-2 selector wiring is the open tail.

The promise, stated plainly: **one incorruptible source act, and the rest is a fold — so the system cannot output
garbage because it cannot ingest it, and it needs no central authority because there is nothing left to police.**

---

*Sources for the original plugin:* [Plugin: Unicenta POS](https://wiki.idempiere.org/en/Plugin:_Unicenta_POS) ·
[Unicenta oPos to iDempiere](https://wiki.idempiere.org/en/Unicenta_oPos_to_iDempiere) ·
[Plugin: Wanda POS](https://wiki.idempiere.org/en/Plugin:_Wanda_POS) ·
[How to setup POS ACTIVEMQ](https://wiki.idempiere.org/en/How_to_setup_POS_ACTIVEMQ) ·
[Plugin: AutoBOMOrder](https://wiki.idempiere.org/en/Plugin:_AutoBOMOrder).

*Companions:* [WMSLens.md](WMSLens.md) (movement edge) · [SocialPlatformLens.md](SocialPlatformLens.md) (on-the-move edge) · [DistributedERP.md](DistributedERP.md) (the doctrine).
