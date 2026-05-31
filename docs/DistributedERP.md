# Distributed ERP — Contention Map & Guards

**Thesis.** The "hard distributed-systems problem" in ERP is **mostly a modelling artifact.** Model the
domain as it physically is — goods have a *location*, work has an *owner*, money moves at the *cadence of
atoms* — and contention dissolves for ~90% of usage, collapses to a daily pipeline for ~10%, and reduces to
**one genuinely real-time op-class** (customer-global entitlements). None of it needs a fat always-on server.

Companion to `ERP.md §0.20` (secured/durable phase) and `LocalFirstPriorArt.md` (how others did it). This
doc is the *tested* architecture — every scenario below was stress-tested in design dialogue, and the
residuals always land in "the ledger reconciles it" (see §Capstone). SPEC, 2026-05-31.

---

## 1. The 90/10 reframe

- **~90% of ERP is single-writer/owned** — a sales order belongs to a rep, a PO to a buyer, a pick to a
  worker. No concurrency challenge at all. The real businesses *dispatch* work to exactly one person.
- **~10% is the "100 branches → central" case** — and it is **not** multi-master concurrency. It is a
  **one-way trip circle**: branches → central (sales/orders up) → `QtyOnHand` + replenishment → branches
  (stock/POS down). Overnight, directed. An op-log handles this natively as **deterministic fan-in →
  deterministic fan-out** (push branch logs up, total-order + replay → exact state, derive replenishment
  ops, push down). No conflict resolution.
- **The only genuine all-round-sync need** is a single indivisible thing claimed in real time across sites
  — e.g. a loyalty prize claimed at two branches the same day. That is **one op-class** (§4), not a
  property of the whole system.

---

## 2. Physics partitions the data — the granularity ladder

Contention over goods is solved not by an algorithm but by **atoms having a location.** Single-writer is
enforced by the physical world at every granularity:

| Granularity | Owner | Why no contention |
|---|---|---|
| **Branch** | the shop | a shop's stock is physically *in that shop* (`M_StorageOnHand` per `M_Locator`/`Org`) — another branch cannot ship it |
| **Van** (DSD / van-sales) | the salesperson | each van loaded at depot in the morning (fan-out); its stock is physically the salesperson's |
| **Box-in-hand** | whoever holds it | **you cannot scan a box that isn't physically there** — the scan *is* proof-of-possession and the commitment op; two people can't scan the same physical unit |

**Cadence matches physics.** Goods move next-day at fastest, so the data only needs **daily** consistency.
Real-time global inventory sync solves a problem physics doesn't pose — you can't get a unit from another
branch faster than a transfer anyway. Overnight batch is therefore the *correct* cadence, not a tolerated
limitation: the book catches up with the atoms at the rate the atoms travel.

**The scan is the op.** `SCAN(unit_SSCC → customer, qty, ts)` — the barcode/SSCC is the unit's natural
**UUID** (global identity handed over by GS1; FKs reference it). Scan data is captured as an *input* in the
op, never recomputed → replay stays deterministic.

---

## 3. The fundamental guard set

Invariants the system enforces so contention is structurally impossible for the common case:

| Guard | Invariant | Kills |
|---|---|---|
| **G-IDENTITY** | every entity = global UUID PK; FKs → UUID; human handle = `user/date/doc/#` per-*device* (gapless in its own namespace, unique without coordination) | PackIn/merge clashes; centralised DocNo allocation |
| **G-EXCLUSIVE-DISPATCH** | a work item is delivered to **exactly one** writer (queue, not broadcast) | two clients ever receiving the same order |
| **G-SINGLE-WRITER** | at any instant **≤1 owner** (device/session, not human) may emit mutating ops on an entity; others read-only; non-owner ops rejected on replay | concurrent writes to one doc |
| **G-RESERVATION** | consuming a shared pool requires a lease granted **at dispatch** (online); offline work stays inside the envelope | pooled-resource contention (where stock isn't physically partitioned) |
| **G-ORDERED-HANDOFF** | ownership transfers only via an explicit **ordered** handoff op — never a two-owner window | hand-off races |
| **G-LEASE-EXPIRY** | unexercised ownership/reservation expires after N and returns to pool; expiry is itself an ordered op | a device offline for a week locking resources forever |
| **G-READ-ANYWHERE-WRITE-OWNER** | reads replicate freely (stale snapshots ok); writes are owner-gated | read/write coupling |

**Architecture:** *avoid by ownership (primary)* — G-EXCLUSIVE-DISPATCH + G-SINGLE-WRITER + physical
location cover ~all normal flow → no contention, no rollback. *Resolve by total-order (fallback)* — the
dumb async broker (§5) only for the rare uncontainable edge. Belt **and** suspenders.

---

## 4. The one real-time op-class — customer-global entitlements

The *customer* is the only entity that can be "in two branches at once," so loyalty prizes / gift balances /
coupons / **credit** are the single op-class needing more than ownership + daily cadence. Full lifecycle,
pure OOTB (no fat server):

1. **Issue = a URL.** Mint a merchant-**signed** token (customer/limit/validity), encode as URL/QR, deliver
   (SMS/WhatsApp/QR), customer's browser persists it as a wallet entry (their op-log). Reuses existing
   `share.js` / QR (`BarcodeDetector`) / PWA machinery. Two flavors:
   - **Self-contained URL** → *zero-server issuance* (merchant signs locally).
   - **Activation link** → one narrow `DateLastUsed`-style touch so a link can't mint infinite cards.
2. **Carry = signed op-log on the customer's phone.** The customer is their own single-writer. The token is
   **merchant-signed + hash-chained** (= `§0.20` W-SIGN/W-CHAIN) so the holder can *present* but not
   *forge* it — they don't hold the signing key.
3. **Claim/spend:**
   - **Online (normal POS):** sub-second authority **compare-and-set** (set-if-unset) → first-wins. Same
     weight as a card-payment auth — not a burden. (A plain `DateLastUsed` *read* is best-effort only — two
     branches can read "unused" before either writes; CAS is the hard guarantee.)
   - **Offline (CAP edge):** choose by value × frequency — **high-value → block** ("confirming…", like an
     offline card decline); **low-value → allow + reconcile.** For *credit*, an offline overspend simply
     becomes a larger **receivable** — accounting-native, not an error state.
4. **Reconcile = the ledger** (§Capstone).

**Bearer vs bound (honest caveat):** a URL is a bearer token. **Promo → bearer** (forwarding = viral
coupon, desirable). **Personal credit → bind on first open** (sign to the customer's device/public key, or
one activation touch) or forwarding = giving away the credit line.

**Scan unification:** QR = URL made physical → scanning a customer's QR at POS reads their entitlement, the
*same gesture* as scanning a box. One scan model: goods (SSCC → which unit) and customers (token → who/what
credit).

---

## 5. How much central persistence? — a dumb async post office

Because clients **deterministically replay** the ordered log to identical state, the central thing runs
**no business logic.** It does three things (the ActiveMQ job): **accept** ops (append-only), **assign a
total order** (the one thing that must be centralised), **persist durably + fan out**. A Kafka/ActiveMQ-class
**log broker**, not a server-side ERP. It doesn't know what an invoice *is*.

| Mode | Central post office needed? |
|---|---|
| Single user / one device | **No** — device + export/backup is the whole system; cloud = optional backup |
| Multi-device / durability / 100-branch circle | **Yes — only the dumb broker** (order + persist + relay), run **daily** |
| Contended invariants (rare; only where stock isn't physically partitioned) | the broker's total order **is** the serialization point — no separate locks; loser of true contention gets a *deterministic, explainable* correction (e.g. backorder), not an arbitrary overwrite |

The post office wears a second hat for the entitlement op-class: a **sub-second matchmaker** for the online
CAS. That is the *only* always-fast online need; everything else is daily.

---

## 6. Determinism is load-bearing (not just nice)

The dumb-broker model only works if **clients converge from the ordered log** — so *any* nondeterministic
verb (a live FX/rate lookup, an uncaptured clock read, a re-rolled random) breaks it. The prime directive
(deterministic, non-invent, extract-or-compile-only) therefore stops being a virtue and becomes
**infrastructure**: determinism is *what lets the server be dumb and the clients agree.* Practical rule
(already enforced in our runtime): nondeterministic values (UUIDs, timestamps, scanned codes, external
rates) are **generated at the edge and recorded as inputs in the op** — the kernel only ever *reads* them,
never computes them. Use **UUIDv7** for identity (timestamp-sortable + collision-safe via the random tail;
a millisecond alone is *not* a uniqueness guarantee).

---

## 7. Capstone — accounting *is* the reconciliation engine

We don't need perfect real-time distributed consistency, because **accounting was invented to reconcile
imperfection.** Double-entry bookkeeping (Pacioli, 1494) is the original eventually-consistent log — five
centuries of provisions for shrinkage, bad debt, overages, disputed claims, double-payouts. So a local-first
ERP's job is **not** to prevent every discrepancy in real time (CAP says you can't) — it is to **feed clean,
ordered, signed ops into the system already designed to reconcile discrepancy.** The op-log and the ledger
are the same instinct, 500 years apart.

Every residual in this doc resolves there: a double-claim → flagged at month-end, promo-expensed; a credit
overspend → a receivable; a phantom scan → a van shortage the salesperson is liable for. **Common in the
real world; provisioned; not a showstopper.** And our ordered, hash-chained log makes every such case *more*
auditable than a centralised system (full lineage, deterministic first-wins).

---

## 8. Honest residuals (bounded, on the list — not blockers)

- **Lease-expiry oversell** — only where stock is *not* physically partitioned (rare in retail); mitigate
  with generous leases + provably-unexercised expiry + physical count reconciliation.
- **In-transit ownership** — who holds units while the truck rolls (A→B). iDempiere's in-transit locator /
  `M_Movement` confirm-both-ends handles it; the daily reconcile must account for shipped-not-yet-received.
- **Barcode ≠ cryptographic possession** — a printed/cloned code can be scanned without the box. A
  fraud/shrinkage problem, caught at van settlement + accounting; *not* a real-time/distributed problem.
- **Signing-key custody** — the merchant's signing key is the one irreducible trust anchor (for zero-server,
  the merchant's OOTB instance signs locally). This is the "secured" core; everything else is URL + browser.
- **Within-shop multi-till** — two tills, last unit: a *local* LAN single-writer problem, sub-ms, not
  distributed.

---

## 9. What infrastructure is actually required

**No fat always-on server.** The complete tested architecture needs only:
1. **Per-shop / per-van / per-box single-writer** — free from physical ownership + `M_Locator`.
2. **A thin async post office** (order + persist + relay), run **daily** — the one-way trip circle.
3. **A sub-second touch** for the *one* customer-global op-class (online CAS), high-value only.
4. **Signed, hash-chained logs** (W-CHAIN/W-SIGN) — justified concretely by credit-on-phone.
5. **The ledger** — doing the reconciliation job it has done since 1494.

**Sources / cross-refs:** `ERP.md §0.20` (phase + witnesses W-CHAIN/SIGN/PERSIST/OWNER) ·
`LocalFirstPriorArt.md` (Replicache/ElectricSQL/PowerSync/LiveStore/CRDTs) ·
`SpatialERP_OOTB.md §11.5` · iDempiere `M_StorageOnHand` / `M_StorageReservation` / `M_Movement`.
