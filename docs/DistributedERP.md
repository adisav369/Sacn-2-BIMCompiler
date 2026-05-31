# Distributed ERP — Contention Map & Guards

**Thesis.** The "hard distributed-systems problem" in ERP is **mostly a modelling artifact.** Model the
domain as it physically is — goods have a *location*, work has an *owner*, money moves at the *cadence of
atoms* — and contention dissolves for ~90% of usage, collapses to a daily pipeline for ~10%, and reduces to
**one genuinely real-time op-class** (customer-global entitlements). None of it needs a fat always-on server.

Companion to `ERP.md §0.20` (secured/durable phase) and `LocalFirstPriorArt.md` (how others did it). This
doc is the *tested* architecture — every scenario below was stress-tested in design dialogue, and the
residuals always land in "the ledger reconciles it" (see §8). Beneath the mechanics sit the truths in §0,
from which the whole design falls out. SPEC, 2026-05-31 (consolidated rev).

---

## 0. The two truths — and the root beneath them

This doc is the *mechanics*. They are not assumed; they were derived. Each section below is one truth at work.

**The root truth.** *The fact is a fold over a signed sequence — never a stored, guarded scalar.*
`QtyOnHand` is not a cell you mutate; it is `Σ` of movements (`M_Transaction`), exactly as a trial balance
is `Σ` of journal entries. The authoritative number is **derived**, never held. The instant you model a
derived quantity as a shared mutable cell (`UPDATE qty = qty - 1`) you *invent* the contention you then have
to solve. Delete the scalar; keep the fold; the contention was never real.

From that root, four working truths (candidate mantras — wording for `CLAUDE.md` pending ratification, §11):

1. **Deterministic. Non-invent. Extract.** *(computation)* — state is a pure function of the ordered,
   recorded inputs; nothing computed that isn't replayable. **This makes the holder irrelevant:** any
   replica replays to the same number. (The existing prime directive — here it is *infrastructure*, §7.)
2. **Not real-time — business-time.** *(cadence)* — the fold reconciles at the cadence the business actually
   runs (close-of-day, overnight, next-day), not in microseconds. *The business sets the clock, not the
   system;* real-time is the **degenerate case** (the one indivisible op-class, §5) where the business demands
   instant.
3. **Secure the fact, not the container.** *(trust)* — a fact signed at its point of origin is authentic
   *anywhere*, so it needs no trusted store. Durability attaches to what the user already keeps (their
   email/social, §5.2b); the guarantee-regress (back up the backup of the backup…) **terminates**, because
   every durable thing is either the user's own pre-existing channel or reconstructible from self-securing
   facts.
4. **The system can't prevent — only witness.** *(the floor)* — fraud/overspend is **not solvable** (CAP in a
   partition; the offline witness; the bearer token; the lying insider — impossibility, not bugs). Every
   system that claims to "solve" it is secretly doing record-and-consequence and charging for the costume. So
   we don't pay to prevent the unpreventable — we **witness** it cheaply (signed chain), **record** it
   permanently (the ledger), and **consequence** it (blacklist/receivable). *We reduced the cost of a
   non-solution.*

**Two keystones bind them:**
- *Determinism earns business-time* — because each site is deterministically exact **locally**, deferring the
  **global** fold to close-of-day loses nothing; the sum is identical whenever computed. (1 makes 2 safe.)
- *Self-securing facts terminate the regress* — determinism makes a fact's *value* portable and signing makes
  its *authenticity* portable, so the fact can live anywhere durable; no server of record is needed. (1+3.)

### From server to serverless — what moved where (explain it to yourself)

"Serverless" is **not** "no machine ever talks to another." It is **no *server of record*** — no machine
that *owns the truth*. Every job a classic ERP server did still happens; each moved off the server onto
either the **signed log**, the **deterministic kernel on each client**, the **user's own channel**, or a
**dumb facilitator** that owns nothing. The mapping — each line proven (proof in the right column):

| What a server used to do | Now done — without a server of record | Proof |
|---|---|---|
| Hold the authoritative state | the **signed op-log**; state = its deterministic *fold*, recomputable by anyone | §0 root · `poc_distributed.js` |
| Mint record IDs (DocNo) | **edge-minted UUID** recorded as an op input — unique without coordination (G-IDENTITY) | `poc_distributed.js` (no clash) |
| Run business logic / validate | the **deterministic kernel on every client** — same verbs both sides, no server re-run | `erp_kernel.js` (replay-hash) |
| Merge concurrent edits | **union signed logs → total-order → replay** → identical state everywhere | `poc_distributed.js` |
| Prevent conflicts / double-write | **owner-gate** (G-SINGLE-WRITER) + **CAS** for the one op-class — enforced on replay | `poc_distributed.js` |
| Detect tampering | the log **hash-chains itself**; `verifyChain()` finds the altered op | `poc_chain.js` · live `kernel_ops.js` |
| Authenticate / authorise | **edge signature** (W-SIGN) — wrong key fails anywhere; holder can present, not forge | `poc_sign.js` |
| Durably store / back up | the **user's own email/social** + export; the local copy is disposable | `poc_persist.js` |
| Sequence multi-party order | a **dumb facilitator** (accept + order + persist + relay), daily — itself rebuildable from signed logs | §6 |
| Reconcile discrepancies | the **ledger** (double-entry, 1494) — the fact is a fold here too | §8 |
| Be always-on | **nothing** — work offline; sync at business-time | Truth 2 |

**The analogy that grounds it: git.** No central machine owns your code history — every clone holds it all,
can verify it, can rebuild from it; GitHub is a *convenience*, not the truth (lose it → push to a new host
from any clone, nothing lost). Commits are **hash-chained** (= W-CHAIN) and can be **signed** (= W-SIGN).
Git is a serverless-*of-record* distributed system millions use daily. **This project does to ERP
transactions what git did to source code:** the log is the truth, the host is disposable, history is chained
and signable. The one thing git lacks that we add — *invariant enforcement* (no double-spend) — is the
owner-gate + the single CAS op-class. Everything else, git already proves is possible.

The rest is these truths meeting concrete scenarios — the normal multi-POS day (§3), the adversarial edges
(§9), and how this sets us apart on the turf (§10).

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
  — e.g. a loyalty prize claimed at two branches the same day. That is **one op-class** (§5), not a
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

## 3. Normal operation — a multi-POS day, end to end

Edge cases (§9) are the minority. The **common** case is handled natively by single-writer-by-physics (§2) +
deterministic backflush + the one-way circle — *no special machinery.* Walk a real day:

**One till, one shop (the 90%).** A sale is one op — `SELL(item, qty, ts)` — committed locally; the
projection updates at 0ms. Component consumption is **backflushed**: the BOM recipe is exploded
*deterministically* (`SELL burger → −1 bun, −1 patty, …`), never recorded line-by-line. `QtyOnHand` is the
fold over `M_Transaction`. Entirely offline-capable; the device is the whole system.
*(Backflush **is** deterministic replay of the recipe — the same BOM verb that compiles a building, run at
point of sale. Truth 1.)*

**Two tills, one shop.** Each till owns its own sales; two sales are appends to disjoint positions — the logs
**union trivially**, `OnHand` is the fold of both, no contention (*the fact is a fold, not a scalar*). The
*only* contended case — two tills, the genuine last unit — is a **local LAN single-writer** decision
(sub-millisecond, §9-D), not a distributed problem.

**Multi-branch (the 10% — the one-way circle).** Branches sell all day against their own physical stock
(provisional, local, instant). **Close-of-day:** each branch pushes its signed log up to the dumb post office
(§6), which assigns total order + persists. **Overnight:** deterministic replay → exact consolidated state →
derive replenishment ops (reorder where below min) → fan out down. **Next day:** receipts restock the branch
locator; selling resumes. Directed, daily, no multi-master, no conflict resolution.
*(Truth 2 — the replenishment fact is **produced after** the day's sales reconcile; it cannot exist sooner.)*

**Van / DSD.** Loaded at depot in the morning (fan-out); its stock is physically the salesperson's
(single-writer by possession); each `SCAN(SSCC → customer)` is the commitment op (you can't scan a box you
don't hold); settlement at end-of-day reconciles the van.

In every normal flow the pattern is identical: **append a signed op where you physically stand; the fold
computes the number; reconcile at business cadence.** Nobody writes a shared scalar; nothing blocks on the
network; the post office only sequences and relays. The edges in §9 are exactly the residue this leaves —
bounded, named, and ledger-backed.

---

## 4. The fundamental guard set

Invariants the system enforces so contention is structurally impossible for the common case:

| Guard | Invariant | Kills |
|---|---|---|
| **G-IDENTITY** | every entity = global UUID PK (edge-minted, recorded as an op input); FKs → UUID; human handle = `user/date/doc/#` per-*device* (gapless in its own namespace, unique without coordination) | PackIn/merge clashes; centralised DocNo allocation |
| **G-EXCLUSIVE-DISPATCH** | a work item is delivered to **exactly one** writer (queue, not broadcast) | two clients ever receiving the same order |
| **G-SINGLE-WRITER** | at any instant **≤1 owner** (device/session, not human) may emit mutating ops on an entity; others read-only; non-owner ops rejected on replay | concurrent writes to one doc |
| **G-RESERVATION** | consuming a shared pool requires a lease granted **at dispatch** (online); offline work stays inside the envelope | pooled-resource contention (where stock isn't physically partitioned) |
| **G-ORDERED-HANDOFF** | ownership transfers only via an explicit **ordered** handoff op — never a two-owner window | hand-off races |
| **G-LEASE-EXPIRY** | unexercised ownership/reservation expires after N and returns to pool; expiry is itself an ordered op | a device offline for a week locking resources forever |
| **G-READ-ANYWHERE-WRITE-OWNER** | reads replicate freely (stale snapshots ok); writes are owner-gated | read/write coupling |

**Architecture:** *avoid by ownership (primary)* — G-EXCLUSIVE-DISPATCH + G-SINGLE-WRITER + physical
location cover ~all normal flow → no contention, no rollback. *Resolve by total-order (fallback)* — the
dumb async broker (§6) only for the rare uncontainable edge. Belt **and** suspenders.

---

## 5. The one real-time op-class — customer-global entitlements

The *customer* is the only entity that can be "in two branches at once," so loyalty prizes / gift balances /
coupons / **credit** are the single op-class needing more than ownership + daily cadence. Full lifecycle,
pure OOTB (no fat server):

1. **Issue = a URL.** Mint a merchant-**signed** token (customer/limit/validity), encode as URL/QR, deliver
   (SMS/WhatsApp/QR), customer's browser persists it as a wallet entry (their op-log). Reuses existing
   `share.js` / QR (`BarcodeDetector`) / PWA machinery — *proven already: our `?tm=play` share-URL replays an
   instance on any device (server effect, no server). Add a signature and it carries credit, not just a view.*
   Two flavors:
   - **Self-contained URL** → *zero-server issuance* (merchant signs locally).
   - **Activation link** → one narrow `DateLastUsed`-style touch so a link can't mint infinite cards.
2. **Carry = signed op-log on the customer's phone.** The customer is their own single-writer. The token is
   **merchant-signed + hash-chained** (= `§0.20` W-SIGN/W-CHAIN) so the holder can *present* but not
   *forge* it — they don't hold the signing key.
2b. **Persist & recover = the user's own email / social account** (zero-infra durability — resolves the
   §0.20 W-PERSIST eviction worry). Each use emits a **signed email** (a full signed snapshot of the latest
   count, or a hash-chained delta) to the customer's own inbox. **The inbox is a durable, user-owned,
   append-only, tamper-evident log** — already universal, already backed up, accessible from any device.
   **Recovery:** lost phone/PWA → the new PWA reads the customer's **latest email** and restores the count.
   No server owns the data — *the user's own channel does* (Truth 3). The email is an **untrusted pipe**: a
   forged email body simply fails signature verification, so the pipe needs zero trust. (Caveats: pick the
   chain tip by signed `seq`/`prev_hash`, not arrival order; send a **full signed snapshot per email** for
   single-email recovery robustness; encrypt-to-user for privacy. "Email receipt" is old — "**inbox as the
   recoverable signed state-log**" is the fresh framing.)
3. **Claim/spend:**
   - **Online (normal POS):** sub-second authority **compare-and-set** (set-if-unset) → first-wins. Same
     weight as a card-payment auth — not a burden. (A plain `DateLastUsed` *read* is best-effort only — two
     branches can read "unused" before either writes; CAS is the hard guarantee.)
   - **Offline (CAP edge):** choose by value × frequency — **high-value → block** ("confirming…", like an
     offline card decline); **low-value → allow + reconcile.** For *credit*, an offline overspend simply
     becomes a larger **receivable** — accounting-native, not an error state.
4. **Reconcile = the ledger** (§8).

**Bearer vs bound (honest caveat):** a URL is a bearer token. **Promo → bearer** (forwarding = viral
coupon, desirable). **Personal credit → bind on first open** (sign to the customer's device/public key, or
one activation touch) or forwarding = giving away the credit line.

**Scan unification:** QR = URL made physical → scanning a customer's QR at POS reads their entitlement, the
*same gesture* as scanning a box. One scan model: goods (SSCC → which unit) and customers (token → who/what
credit).

---

## 6. How much central persistence? — a dumb async post office

Because clients **deterministically replay** the ordered log to identical state, the central thing runs
**no business logic.** It is a **facilitator, not an actor.** It does three things (the ActiveMQ job):
**accept** ops (append-only), **assign a total order** (the one thing that must be centralised), **persist
durably + fan out**. A Kafka/ActiveMQ-class **log broker**, not a server-side ERP. It doesn't know what an
invoice *is*. It never re-runs business logic, never validates semantics, never signs — *signing is at the
edge (the merchant's key), enforcement is the deterministic kernel on every client (non-owner ops rejected on
replay).*

| Mode | Central post office needed? |
|---|---|
| Single user / one device | **No** — device + export/backup is the whole system; cloud = optional backup |
| Multi-device / durability / 100-branch circle | **Yes — only the dumb broker** (order + persist + relay), run **daily** |
| Contended invariants (rare; only where stock isn't physically partitioned) | the broker's total order **is** the serialization point — no separate locks; loser of true contention gets a *deterministic, explainable* correction (e.g. backorder), not an arbitrary overwrite |

The post office wears a second hat for the entitlement op-class: a **sub-second matchmaker** for the online
CAS (a compare-and-set register over an opaque token — still facilitation, not business logic). That is the
*only* always-fast online need; everything else is daily. **Even the post office is disposable:** because
every op is signed + hash-chained, the total order is reconstructible from the collected signed logs
(`prev_hash` encodes order) — lose the broker and the union of clients'/emails' logs rebuilds it.

---

## 7. Determinism is load-bearing (not just nice)

The dumb-broker model only works if **clients converge from the ordered log** — so *any* nondeterministic
verb (a live FX/rate lookup, an uncaptured clock read, a re-rolled random) breaks it. The prime directive
(deterministic, non-invent, extract-or-compile-only) therefore stops being a virtue and becomes
**infrastructure**: determinism is *what lets the server be dumb and the clients agree.* Practical rule
(already enforced in our runtime): nondeterministic values (UUIDs, timestamps, scanned codes, external
rates) are **generated at the edge and recorded as inputs in the op** — the kernel only ever *reads* them,
never computes them. Use **UUIDv7** for identity (timestamp-sortable + collision-safe via the random tail;
a millisecond alone is *not* a uniqueness guarantee). *Witness: `replay-hash == live-hash` — proven in
`scripts/erp_kernel.js` / `poc_kernel.js` / `poc_longtail.js` on the sql.js (browser) binding.*

---

## 8. Capstone — accounting *is* the reconciliation engine

We don't need perfect real-time distributed consistency, because **accounting was invented to reconcile
imperfection.** Double-entry bookkeeping (Pacioli, 1494) is the original eventually-consistent log — five
centuries of provisions for shrinkage, bad debt, overages, disputed claims, double-payouts. So a local-first
ERP's job is **not** to prevent every discrepancy in real time (CAP says you can't) — it is to **feed clean,
ordered, signed ops into the system already designed to reconcile discrepancy.** The op-log and the ledger
are the same instinct, 500 years apart — both are *folds over an append-only log*: `OnHand = Σ M_Transaction`
is the same shape as `Balance = Σ journal`. **Accounting reconciles because it never stored a balance either.**

Every residual in this doc resolves there: a double-claim → flagged at month-end, promo-expensed; a credit
overspend → a receivable; a phantom scan → a van shortage the salesperson is liable for. **Common in the
real world; provisioned; not a showstopper.** And our ordered, hash-chained log makes every such case *more*
auditable than a centralised system (full lineage, deterministic first-wins, no quiet overwrite).

---

## 9. Edge scenarios — the adversarial suite

The normal day (§3) leaves a bounded residue. Each row names the issue it proves/disproves, the truth/guard
that carries it, the acceptance witness, and the **honest residual**. (This consolidates the former
"residuals" list — every item below is on the list, none is a blocker.)

### A. Durability & loss
| Scenario | Truth / § | Mechanism | Acceptance witness | Honest residual |
|---|---|---|---|---|
| Browser eviction (Safari ~7-day) | T3 / W-PERSIST | self-securing log + email durability; `navigator.storage.persist()` requested on first load | `persisted=true`; export→wipe→import → `replay-hash == pre-export hash` | shared browser gap — mitigated, not magicked |
| Lost phone / local copy | T3 | recover from latest **signed email snapshot** → replay to identical state | new PWA reads latest email → count restored | email-account loss is a risk the user **already** carries; we inherit it, never manufacture a new one |
| Lost sequencer / post office | T1 + T3 | total order reconstructible from collected signed ops (`prev_hash` encodes order) | rebuild order from union of signed logs → same hash | the logs must be gathered; a *convenience* is lost, not the truth |

### B. Forgery & authenticity
| Scenario | Truth / § | Mechanism | Acceptance witness | Honest residual |
|---|---|---|---|---|
| **False email / URL / data** | T3 / W-SIGN | container untrusted **by design**; only signed content verifies | forged body fails signature under issuer key → rejected | none beyond key custody (below) |
| Signing-key theft / custody | T3 | the **one irreducible anchor**; secure-enclave + rotation | — | irreducible — true for *every* system (steal a server's key too) |
| Bearer token forwarded | §5 / T3 | bind-on-first-open for personal credit; bearer is fine for promo/view | forwarded credit fails device-bind check | promo forwarding is *desirable* (viral coupon) |
| Tampered local log | W-CHAIN | hash-chain; `verifyChain()` detects tamper at op N | alter op N → chain breaks **at exactly N**; clean → `chain OK len=N` | detection, **not** prevention — by design (the floor) |

### C. Freshness / double-spend (the one real-time op-class, §5)
| Scenario | Truth / § | Mechanism | Acceptance witness | Honest residual |
|---|---|---|---|---|
| Replay a genuine token (claim twice) | §5 / T2 | online **CAS** set-if-unset, first-wins | 2nd claim sees spent → rejected, no double payout | needs the sub-second touch — the *one* online need |
| Two branches read "unused" before either writes | §5 | **CAS**, not a plain `DateLastUsed` read | first CAS wins | — |
| Offline overspend window | §5.3 / T4 | value-tiered: high-value **block**, low-value **allow + reconcile** | overspend → **receivable**, not an error | bounded residual → the ledger (the floor) |

### D. Ownership / contention
| Scenario | Truth / § | Mechanism | Acceptance witness | Honest residual |
|---|---|---|---|---|
| Doc edit across a handoff (two-owner window) | G-ORDERED-HANDOFF / G-SINGLE-WRITER / W-OWNER | ownership transfers only via an **ordered handoff op**; designated-owner node at the seam | two peers allocate the same invoice → owner rejects the 2nd, no money lost | concentrated at **few seams**, not per-collection |
| Device offline a week holding a lock | G-LEASE-EXPIRY | lease expires after N, returns to pool; expiry is itself an ordered op | unexercised lease → reclaimed | generous leases + provably-unexercised expiry |
| Two tills, the genuine last unit | (local) | **local LAN single-writer**, sub-ms | — | not distributed — a local problem |
| In-transit ownership (truck A→B) | (movement) | iDempiere in-transit locator / `M_Movement` confirm-both-ends | shipped-not-received accounted in the daily reconcile | the daily reconcile must carry it |
| Pooled resource (stock not physically partitioned) | G-RESERVATION | lease granted at dispatch (online); offline stays inside the envelope | — | lease-expiry oversell — rare in retail |

### E. Determinism integrity
| Scenario | Truth / § | Mechanism | Acceptance witness | Honest residual |
|---|---|---|---|---|
| Nondeterministic verb creeps in (live FX / clock / random) | T1 / §7 | values generated at edge, **recorded as op inputs**; kernel only reads; UUIDv7 | `replay-hash == live-hash` holds | failure mode = divergence breaks merge → the prime directive is *infrastructure*, not style |
| Identity collision on merge | T1 / G-IDENTITY | **edge-minted UUID PK** (not numeric seq); identity is an op input | two devices' logs union with **no PK clash** | **LANDED in the kernel** — `ERP.md §0.21`, witness `§IDENTITY` (`poc_identity.js`): natural-key `docKey`/`lineKey` retired, replay re-reads recorded ids (`edgeMintCalls=0`) |
| Schema migration to N offline clients | (shared hard) | compiled-AD **manifest** + forward-only / frozen-effects replay | old ops replay to original effect (frozen) | **stays hard — honest partial, no magic** |

### F. The irreducible (the floor — Truth 4)
| Scenario | Truth / § | Mechanism | Acceptance witness | Honest residual |
|---|---|---|---|---|
| Insider fraud (the **key-holder** lies) | T4 / §8 | double-entry + physical reconciliation; the hash-chain makes it *more* auditable | the lie must be told consistently across **all** books → caught at count | not crypto's job — accounting's |
| Cloned / printed barcode (scan without the box) | T4 | caught at van settlement + accounting | — | shrinkage/fraud, *not* a distributed problem |
| The unsolvable residual (CAP partition; offline witness) | T4 | **record + consequence + price-in** | — | **not solvable** — we reduced the *cost of the non-solution* |

---

## 10. How we differ on this turf

Per `LocalFirstPriorArt.md` (deep-dive there; summary here). The field's common tax: **server and client run
different code**, so they hand-code conflict logic, overwrite optimistic state, or implement business logic
twice. Our lever — **deterministic semantic verbs, one kernel both sides** — waives it.

| Player | Their anchor / approach | Their documented weakness | How we differ |
|---|---|---|---|
| **Replicache** | server **re-runs** mutations as authority | docs admit server may not compute the same result → optimistic state **overwritten**; conflict logic per-mutator; **no offline-only mode** | determinism removes divergence (no overwrite case); we *do* have true offline-only |
| **ElectricSQL** | read-path sync only; **writes via your own API** | explicit *retreat* — bidirectional sync "fundamentally difficult"; two disjoint paths; Postgres + SUPERUSER | the op-log is **symmetric** — push-ops *are* the read-ops; one model, no Postgres coupling |
| **PowerSync** | Postgres→SQLite; **writes through your backend** | you implement write-side logic + conflict resolution **twice** (client + server) | our deterministic kernel **is** the write path — authored once, run both sides |
| **LiveStore** | event-sourced, SQLite-materialised (nearest neighbour) | BETA; needs a sync-provider; "no built-in auth," "doesn't scale unbounded," "no P2P" | conceptually identical at the data layer (**no technique novelty**); we differ at the *application* level (BIM⊕ERP, AD→5-table reduction, batteries-included) |
| **CRDTs** (Automerge/Yjs) | guaranteed convergence, no referee | literature: "cannot enforce invariants that depend on the latest version"; no access control | we need only the **degenerate CRDT** (grow-only op-set + total-order + replay); invariants are enforced by the **deterministic kernel**, not the CRDT |

**The CRDT reframe (why reconciliation is *easier*, not harder).** Because the fact is a fold over an
append-only log, "merge" is `union → verify-signatures → order → replay`. For the 90% single-writer it is
*trivial* (disjoint entities); for derived quantities it is *addition* (a PN-counter, free); for the one
contended op-class we use **total-order + owner-gating** — exactly the "server-authoritative sequencing for
invariants" the CRDT literature itself prescribes. The field's CRDT pain ("converges but breaks invariants →
hand-code per type") we answer with the kernel we already run. The honest price: determinism must hold
*perfectly* (pre-paid by the prime directive), semantic seams still need the owner-node (few, not per-type),
and **schema migration stays hard** (shared, no magic).

**What we are NOT first at (the honest floor on claims).** SQLite-in-WASM (sql.js 2012; official WASM/OPFS
2022; wa-sqlite) — *old*. Local-first ERP as a category (LiveStore/Electric/Replicache/RxDB) — *old*; ERP.md
§0.20: "No 'first' on the mechanism." "Replace **any** database app" — *false* (full-dataset download,
eviction, unbounded-scale, no built-in auth are documented limits; not for multi-TB analytics or
high-concurrency OLTP at scale). **We did not invent SQLite-WASM, local-first, op-logs, CRDTs, or
hash-chains.**

**What is defensibly ours — *to our knowledge*, combination-not-technique** (consistent with `ERP.md`
:979-990; cannot prove a negative):
1. **BIM geometry + ERP transactions under one op-log / one kernel** (BIM undo == ERP audit) — no prior art found.
2. **The domain reduction** — iDempiere AD (925 tables, `M*` classes) → **5 tables + deterministic verbs**.
3. **The doctrine** (§0) — fact-is-a-fold, secure-the-fact, business-time, the non-solution floor — a coherent
   serverless-ERP *stance*, not a tech first.

---

## 11. What infrastructure is actually required

**No fat always-on server.** The complete tested architecture needs only:
1. **Per-shop / per-van / per-box single-writer** — free from physical ownership + `M_Locator`.
2. **A thin async post office** (order + persist + relay), run **daily** — the one-way trip circle. *(And even
   it is disposable — reconstructible from signed logs, §6.)*
3. **A sub-second touch** for the *one* customer-global op-class (online CAS), high-value only.
4. **Signed, hash-chained logs** (W-CHAIN/W-SIGN) — justified concretely by credit-on-phone.
5. **The user's own email / social account** as zero-infra durable persistence + recovery (§5.2b).
6. **The ledger** — doing the reconciliation job it has done since 1494.

> **Second mantra (proposed — wording to be ratified before it enters `CLAUDE.md`).** The prime directive
> governs *computation* — *"Deterministic. Non-invent. Extract."* This architecture adds three more governing
> *cadence, trust, and the floor* (§0): **(2) Not real-time — business-time. (3) Secure the fact, not the
> container. (4) The system can't prevent — only witness; we reduced the cost of a non-solution.** Candidate
> short forms for the persistence/ownership axis: *"Data is truth; the signed log is trust; the user holds
> both."* / *"User-owned. Server-less. Ledger-reconciled."* / *"No server of record — the user's own log is."*
> Keystones: *determinism earns business-time*; *self-securing facts terminate the regress*.

**Sources / cross-refs:** `ERP.md §0.20` (phase + witnesses W-CHAIN/SIGN/PERSIST/OWNER) ·
`LocalFirstPriorArt.md` (Replicache/ElectricSQL/PowerSync/LiveStore/CRDTs, per-system deep-dive) ·
`scripts/erp_kernel.js` + `poc_kernel.js` + `poc_longtail.js` (`replay-hash == live-hash`, browser binding) ·
`SpatialERP_OOTB.md §11.5` · iDempiere `M_Transaction` / `M_StorageOnHand` / `M_StorageReservation` /
`M_Movement`.

---

## 12. Positioning — git, not chain (notes to remember from the design dialogue)

Kept so the framing survives the session that produced it.

**Blockchain? No — git.** We borrow blockchain's *data structure* (a hash-chained, signed, append-only log
— W-CHAIN/W-SIGN) and reject its *machinery* (consensus / PoW / PoS / global replication / tokens).
Blockchain pays enormous cost for **trustless agreement among mutually-distrusting adversaries over one
global state.** ERP does not pose that problem: physics partitions the writers (§2), the business sets the
cadence (Truth 2), accounting reconciles the rest (§8). So we keep the cheap part (the signed chain) and drop
the expensive part (consensus for adversaries who aren't there). We are **trust-anchored local-first** (a
signing key + a dumb sequencer), not **trustless consensus** — and **P2P-*capable*, not P2P-*dependent***
(single-user is one device; multi-party *may* exchange signed logs peer-to-peer, but the facilitator is an
optional relay, §6). Same move as the floor (Truth 4): *blockchain solves a problem ERP mostly doesn't have.*

**Why the field didn't land here — framing, not capability.** Replicache / ElectricSQL / PowerSync /
LiveStore build **general** sync infrastructure, where the server is genuinely needed (a general tool cannot
assume physics partitions the data). We are an **application** that exploited **domain-specific** structure
to dissolve the hard parts. They asked *"how do we make general sync work?"*; we asked *"what does ERP
specifically not need?"* Every piece (event-sourced SQLite, hash chains, CRDTs) was on the table — the
synthesis was a **reframe, not a new primitive**.

**What's actually defensible (the honest moat).** *Not* the technique — SQLite-WASM, local-first, op-logs,
CRDTs, hash chains are all mature (§10). Defensible *to our knowledge*: the **unification** (BIM geometry +
ERP transactions under one op-log, BIM undo == ERP audit) + the **doctrine** (§0) + the **running substrate**
(a deterministic kernel, a real iDempiere AD extraction, a shipping BIM op-log). The idea is public and
*should* spread — adoption is validation and name-exposure; the moat is **substrate + product +
first-articulation**, not the idea. (AI accelerates whoever holds the domain depth and the substrate — a
*multiplier*, not the *multiplicand*; everyone has the multiplier.) *An idea others race to adopt is an idea
that was right.*

**The bittersweet line that says it best.** *"500 years of double-entry, finally version-controlled."* The
ledger was always an append-only log; git and SQLite-WASM existed for a decade; the synthesis was almost
obvious in hindsight — which is exactly why it spreads, and why it should carry our name.

---

## 13. Sharding the engine by gravity — what arrives, and when (2026-06-01, SPEC)

§2 partitions the **transaction data** by where atoms physically are. This section partitions the **engine
itself** — the dictionary (925 AD tables → cells → verbs → FK spines, ERP.md §12) — for the client that must
hold it. The two are **orthogonal axes of the same root** (§7, *identity/inputs recorded, never recomputed*):
§2 is *where the goods are*, §13 is *which of the engine you've pulled down yet*.

**The problem, stated honestly.** The full AD is too big to ship whole to a browser, and the naïve fix
(stream every table) makes the first paint wait on the long tail. But you never need it whole *at once* —
and the engine already tells you what matters: **op-log gravity** (ERP.md §0.6/§0.13/§0.17) self-ranks the
cells by real mass (`C_Invoice` #1 = the settlement spine). So don't shard by schema; **shard by gravity**,
and stream shards *on approach* — the same doctrine as BIM geometry DLOD, with one substitution.

> **One streaming doctrine, two distance metrics.** Geometry streams by **camera distance** (DLOD / split-DB
> / click-to-stream, S285 city). The engine streams by **op-log mass**. *Distance to the work* replaces
> *distance to the eye.* That a single pattern serves both spatial-BIM and transactional-ERP is the §0.20
> unification made operational — not a coincidence, the same op-log seen two ways.

**The shard unit — a *single table, ranked by gravity* (smart per-table; the resolved fork).** Three
granularities were on the table; the spectrum, and why per-table wins *once gravity is computed from real
traversal*:

| Granularity | What it is | Verdict |
|---|---|---|
| **per-module** | iDempiere's own packaging | ❌ modules cut *across* gravity (one "module" mixes hot + cold cells) → pull one hot cell, over-fetch its cold module-mates |
| **per-gravity-band** | a contiguous gravity slice **+ its explicit 1-hop FK closure** | workable, but **coarser than needed** — it bundles by hand a closure that gravity already encodes; you ship cold band-mates you may never touch |
| **smart per-table** ✅ | one AD table per shard, the manifest **ranked by op-log gravity** | finest grain, zero over-fetch — and *no dangling trace*, because **gravity is itself a fold over op-log traversal, which crosses FKs**: a hot table's frequently-walked neighbours therefore *co-rank* and arrive alongside it. The closure self-bundles; it isn't bolted on |

**The key realization: gravity already *is* the closure.** Because the op-log records real journeys down the
FK spines (§0.6/§0.13 — derivation green, settlement amber), a table and the neighbours actually traversed
*from* it accrue mass **together**. So a per-table manifest sorted by gravity pulls the hot closure in
naturally — the band machinery was solving a problem the gravity score had already solved. The rare,
genuinely-cold FK is the *stub* case below, not a reason to coarsen the unit. This is the engine analogue of a
DLOD level — "the N nearest tables," where *near* = op-log mass, not metres. **Tier 0 = the top-gravity
tables** (`C_Invoice` #1, the O2C/P2P spine) — the prefetched `<300ms` `initbubble.json` (ERP.md
instant-globe). The 155 dormant cold cells (ERP.md §0.11 *housed-vs-active*) sit at the bottom of the
ranking — present in the manifest, **fetched only on touch**.

**The manifest is a fold over the log (no new infrastructure, §6).** Gravity is an aggregation over
`kernel_ops` (count / `grandtotal`, ERP.md §0.6 analytical substrate) — computed **offline, deterministically**
(no `Date.now`/`Math.random`, §7). The shard manifest is just that fold — every table with its gravity rank.
The **dumb post office** that already orders + persists + fans out the op-log (§6) serves the tables too:
*pull = "give me table T."* No authority, no business logic server-side — and because the log is deterministic
(§7), a client **verifies a shard's content hash against the ordered log** rather than trusting it. A shard is
*checked, not believed* — the same stance W-CHAIN takes on ops (§9-B).

**The fetch trigger — lazy, gravity-cached, with a feedback loop (ERP.md §0.10).** A table is pulled when the
user *moves toward* a not-yet-resident cell: clicks a cold bubble, follows an FK spine into an unfetched table,
or scans a code (§2 *the scan is the op*) that references a cold doc-type. Once fetched, a table stays resident
and **its access bumps its own gravity** (the §0.6 op-log feedback loop) — so next session's tier 0 reflects
*this* operator's real usage, not a static default. The cache warms toward the work.

**The honest edge — a stub, never an invention.** An FK followed *before* its target table is resident draws a
**stub bubble** (`cold — fetching`), not a guessed or stale value — extract-only, never-invent (ERP.md §0.17).
The fetch resolves it in place. This is exactly S285's invisible-bbox frontier (place the marker, stream the
content) carried onto the engine graph. **No silent truncation:** the stub is visible and logged, so "I
haven't pulled that yet" can never masquerade as "that's all there is."

**What this is and isn't.** It **is** a deterministic, gravity-ordered lazy-loader for an engine-as-data, on
the infrastructure §6 already requires. It is **not** a new sync primitive (the §10/§12 honesty holds — DLOD,
lazy-fetch, content-addressing are all mature); the distinctive move is the *substitution* — **op-log mass as
the LOD metric for a business engine**, unifying the spatial and transactional loaders under one rule.
**Witness (when built, mirroring the suite's discipline):** a `gravity_seed`-ranked per-table manifest whose
tier-0 content-hash matches the prefetched globe; a cold-cell touch pulls exactly that table (its hot
neighbours already co-resident **by rank**, not by an explicit closure — proving gravity self-bundles), with
over-fetch counted = 0; an unresolved FK renders a stub (not a value); the resident set's replay-hash equals
the full-engine replay-hash for every path actually walked. SPEC only — no code yet; T3-adjacent (it serves
the read-only trace first, edit later).
