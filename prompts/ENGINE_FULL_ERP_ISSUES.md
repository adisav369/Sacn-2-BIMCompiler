# ⚠ DO NOT REMOVE — Engine issues that GATE the full-ERP write path (DISCUSSION work-order)
# SCOPE: the engine-level issues that surface when the browser ERP moves from READ-ONLY to a
#   full-fledged ERP (real writes: New/Save/Delete, kanban drag→dispatch, posting, install).
#   This is a DISCUSSION prompt for a NEW session — its OUTCOME determines HOW (and whether) each
#   write is wired. Do NOT wire production writes before the matching decision below is made.
# STATE (2026-06-03): SPINE DECIDED = delegate-to-install (§0.0). Browser-side TOP items DONE + verified +
#   localhost-tested: I-K op-group atomicity (kernel commitGroup + UI commitProcess) and I-J rate-as-op-input
#   guard, both in site/kernel_ops.js v8 (+ build/erp mirror). Witnesses GREEN (poc_opgroup/poc_crud_group/
#   poc_rate_input/poc_chain/poc_kernel) + a real-browser probe on http://localhost:8848/glassbowl.html
#   (build/erp/probe_localhost.log). Benchmark vs Postgres-15 (iDempiere's engine): build/erp/bench_oplog_pg.log.
#   NEXT SESSION: prompts/ENGINE_WRITE_PATH_NEXT.md. NOT deployed beyond localhost (EXPLICIT GO pending).
# NON-NEGOTIABLE: non-invent (no fabricated IDs/DocNos/accounts); honest (a "saved" doc must not be
#   presentable as valid in the operator's real iDempiere unless it actually is); §-log + witness any
#   claim. Each decision below names its wiring consequence.
# READ FIRST: docs/ENGINE_CONTRACT.md §1/§2 · docs/DistributedERP.md (single-writer, period-close
#   checkpoint, signed log) · docs/HolyGrail.md (compaction/atomicity/OLTP "hard parts") ·
#   docs/PLUGIN_ARCHITECTURE.md §13 (posting genome) · prompts/FRONTEND_LANE_MASTER.md §6 (I-1…I-5
#   spike-measured) · scripts/compile_rules.js output (284 callout hand-port backlog) ·
#   scripts/erp_postings.js (fact_acct TOTALS limit) · site/kernel_ops.js (the signed kernel;
#   mirrored to build/erp/kernel_ops.js) · site/crud_overlay.js (the E2-dry-run→E3-signed write loop).

---

## §0.0 THE SPINE DECISION — this is not "9 issues", it's ONE boundary (mostly already decided)
The real top-line is **not** "resolve 9 engine issues." It is: **standalone-signed-log vs delegate-to-install** —
**DECIDED 2026-06-03: delegate-to-install + mirror NOW; standalone stays a swappable layer for later** (doctrine
support: IDEMPIERE_2.md §"pivot architecture / migration Switzerland" — write-back to a live foreign ERP is a
**separate write-adapter, a bridge not transparent sync**). Hold that, and the spine bifurcates the whole list:

**The browser NEVER authoritatively writes the operator's data — their install commits** (with ITS sequences,
callouts, acct-schemas). So the "write into their existing numbered iDempiere" branch **is not a browser write
path at all** — it's a sync/push the install commits. A big chunk of the scary list therefore lives at the
**INSTALL / MIGRATE boundary, not the browser kernel:**

| Issue | Side of the boundary |
|---|---|
| I-B *hard case* (their `AD_Sequence`/DocNo) | **install-side** (browser edge-mints only for standalone) |
| I-C callouts (their procedural rules) | **install-side / oracle-validation** — NOT a browser commit-gate |
| I-G their acct-schema · I-H migrate their data | **install-side** |

**What's left for the browser = the standalone signed-log set, which the doctrine already answers:** append
`CREATE`/`SET_STATUS`/edit ops with **edge-mint UUID**, **replay-fold** the view, **seal-from-tip**,
**single-writer-by-physics**, **rate-as-op-input**. Small, mostly-solved.

**Log-first correctness (the corollary):** "edits reset on reload" is **correct behavior**, not a bug — you
persist the **log** and replay it on boot, never the edited projection. The muddle to avoid is doing **both**
(mutating records *and* committing ops). *(NB: cast `site/crud_overlay.js` already appears clean — op-log
persisted to its own IDB key `:428`, baseline immutable, CRUD verbs dry; the "doing both" muddle was NOT found
in the live path — verify which file before treating it as a task.)* Hold log-first and I-A "durability"
mostly dissolves (the log is email-anchorable, DistributedERP §9-A); "persist the DB" stops being a task.

**⇒ The two that are genuinely the browser's to get right in BOTH framings — TOP priority:**
1. **I-K op-group atomicity** (multi-op document actions, all-or-none).
2. **I-J rate-as-op-input determinism** (multi-currency conversion frozen into the op).
Everything else is mostly *"which side of the boundary,"* not *"unsolved."* Agent V (callouts) + half of Agent P
are **install/oracle lane, NOT browser-POC blockers.**

**The one caveat that keeps the fork alive:** if the product wants the browser as the **primary writer for
greenfield tenants (no install at all)**, then the standalone branch **IS the whole product** → I-D scale +
I-E in-browser merge return as **first-class**. Same fork — just decide it consciously.

## §0 The frame — what "full ERP" actually changes
Read-only is solved: fold the AD, render, scope by role/client/org. The hard part is the WRITE: the
moment a user clicks Save, eight server-side guarantees that iDempiere gives for free are suddenly OUR
problem. The decisions below are about which of those we (a) reproduce client-side, (b) defer to the
operator's installed iDempiere (the "system of record" doctrine), or (c) honestly stub for the POC.
The governing principle (DistributedERP.md): **the browser GUIDES + REFLECTS + STREAMS; it is NEVER
the system of record.** Most decisions resolve cleanly once that is held.

## §0.1 LAYER MAP — these issues are NOT flat; each lives in a named layer (think THIS first)
We do **not** need to invent a layered architecture — it exists in two governing docs + the doctrine. The
mistake would be solving I-A…I-J as a flat list. Place each in its layer; the **layer boundary is the
non-collision contract** that makes them separate agents (§2.1):

```
┌─ UI concern-layers (UI_OVERLAY_GOVERNANCE.md — keyed overlays over tagged elements) ─┐
│   Help · CRUD-ring · VALIDATION ←I-C, I-J(tax) · Access ←role · i18n ←I-J(_trl, DONE) │
└──────────────────────────── only contract: the element key ─────────────────────────┘
                    ▼  the FIVE calls (the only coupling)
┌─ THE SEAM (ENGINE_CONTRACT.md §1) — read·write·manifest·verify(+ctx: identity/role/scope) ┐
│   write(ctx, ops) ←I-B (identity/DocNo) · manifest ←sharding · ctx ←I-A target/role        │
└─ ⚠ §6.1 §SEAM-FROZEN NOT yet co-ratified → this is FORK 0, ahead of I-A/I-F ───────────────┘
                    ▼  behind the seam — UI never sees these
┌─ ENGINE internals (DistributedERP §0: op-log → projection → kernel verbs → guards → ledger) ┐
│   op-log seal/checkpoint ←I-D,I-I · op-log schema ←I-F · projection/posting fold ←I-G       │
│   determinism (rate-as-input) ←I-J(currency) · merge/CAS ←I-E · migration ←I-H (cross-cut)  │
└─ persistence below: IndexedDB + email-durability ←I-A ──────────────────────────────────────┘
```
**The consequence:** anything **behind the seam** (I-D/I-F/I-G/I-I, determinism) is reworkable by one engine
agent with ZERO UI risk — the five calls don't change. Anything in a **concern-overlay** (I-C validation,
I-B's New form) is a separate keyed layer that doesn't touch the other overlays. *That* is why §2.1's lanes
are genuinely parallel — not by luck, by the seam. **So the true first move is FORK 0: co-ratify the WRITE
seam — specifically `write(ctx, ops)` + the manifest `gravityRank↔menuGroup` name (ENGINE_CONTRACT §6.1's
joint re-freeze).** The **read/host seam is already frozen and in use** (other session: `§SEAM-FROZEN` 31/31,
IdmpHost) — do NOT re-ratify the read calls; only the write path is open. I-A + I-F come next, then fan out.

## §1 ISSUES — each is an OPEN DECISION (Problem → Why → Options → Decision needed)

### I-A. The browser is not the system of record (durability)
- **Problem:** writes land in IndexedDB/localStorage — evictable, single-device, ~1 GB cap. Not durable.
- **Why it matters:** a real ERP's transactions cannot live somewhere the browser can garbage-collect.
- **Doctrine (ALREADY ANSWERED):** DistributedERP.md **Truth 3** (secure the fact, not the container) +
  **§5.2b** (the user's own email/social inbox = durable, append-only, tamper-evident log) + **§9-A** durability
  table (export→wipe→import → `replay-hash == pre-export hash`; lost-device → recover from latest signed email
  snapshot). The honest residual is named there: shared-browser eviction is *mitigated, not eliminated.* So
  "browser ≠ system of record" is not a gap — it's the design: **no server of record; the user's own log is.**
- **Options:** (1) writes are DEMO only, clearly labelled, never claimed durable; (2) writes commit to the
  signed op-log + are PUSHED to the operator's install (the durable store) when paired; (3) full local-first
  with periodic sync to install.
- **Decision needed → wiring:** if (1), Save just updates the projection + a labelled demo op-log; if (2/3),
  we need the install-pairing channel (QR/host) before Save is "real." **Default lean: (1) for POC, (2) is the product.**

### I-B. New/Delete need values the browser cannot invent (sequences, DocNo, defaults)
- **Problem:** a new record needs a next PK, a DocumentNo from an AD sequence, and AD_Column default values
  — iDempiere generates these server-side. The browser cannot fabricate them (non-invent).
- **Why:** invented IDs/DocNos = silent corruption + a record the real ERP will reject.
- **Doctrine (PARTLY ANSWERED — and the prompt understated this):** DistributedERP.md **§6.1** ("The
  centralized-ID problem, in one place") splits ID allocation into three and shows only ONE part needs a
  coordinator: (1) **PK = edge-minted UUIDv7**, recorded as an op *input* — so it is NON-INVENT by §7's rule
  (generated at the edge, the kernel only *reads* it; G-IDENTITY, witnessed `§IDENTITY`/poc_identity.js). (2)
  **gapless human DocNo = a per-device namespace** `user/date/doc/#` — gapless in its own namespace, unique
  without coordination; no shared `AD_Sequence` counter. (3) **total order = the dumb facilitator** (§6), the
  only centralised part, itself rebuildable from signed logs. ⇒ For the **greenfield/distributed product, New
  is honest** (edge-mint + per-device DocNo, no fabrication). **The genuine residual is narrower than "can't":**
  it bites only when writing into the operator's **existing** numbered iDempiere — there *their* `AD_Sequence`
  is authoritative and an edge-minted number would diverge from their gapless series. So: New is blocked *only*
  in the "write into their live install" case, not in principle.
- **Options:** (1) DISABLE New/Delete (Save-edit only) for the POC; (2) **edge-mint UUID + per-device DocNo
  (§6.1)** — honest for the distributed/standalone product; (3) fetch the next sequence/defaults from the paired
  install before New (required only for writing into the operator's existing numbered instance).
- **Decision needed → wiring:** which target — *standalone distributed* (⇒ §6.1 path, New is real) or *into the
  operator's live iDempiere* (⇒ need their sequence, New stays gated). **Default lean: (1) for the demo into a
  real instance; (2) is the product path and is already specced.**
- **⚠ New ALSO depends on I-C (not just I-B) — don't let it look more done than it is.** Edge-mint solves
  *identity* (PK/DocNo). A new record still needs **field defaults + derived values**: the **declarative** ones
  (`AD_Column.DefaultValue`/`@ctx@`) are capturable now (`site/crud_overlay.js` `defaultsFor` reads them), but
  **procedurally-derived** defaults (callout-filled) are **I-C**. So "New is LIVE" is honest on *identity* but
  **not "produces a valid doc" until I-C (field-tier callouts) lands.** Couple I-B→I-C in the CRUD wiring.

### I-C. Business-rule / callout bypass
- **Problem:** iDempiere has 284 callouts + validation rules that are procedural Java (hand-port backlog,
  `compile_rules` — NOT auto-translated). Browser writes bypass them.
- **Why:** a "saved" doc may violate rules the operator's instance enforces → invalid data.
- **Doctrine (NOT in DistributedERP — don't claim it is):** DistributedERP.md only asserts the *shape* ("run
  business logic → the deterministic kernel on every client," server→serverless table; §7). It does NOT address
  the **procedural-callout port backlog** — that lives in **ERP.md §18.10** (iDempiere's Java is the *oracle*:
  extract/validate each handler against it, never port blindly) + `compile_rules`. Real open work, not resolved
  by the distributed doctrine.
- **Options:** (1) enforce only the DECLARATIVE rules already compiled (AD_Val_Rule/mandatory/refs), defer
  procedural callouts to the install; (2) hand-port the high-frequency callouts; (3) POC writes labelled
  "unvalidated."
- **Decision needed → wiring:** which validation tier runs client-side before a commit is allowed.

### I-D. Op-log scale — O(n²) seal + projection bloat (master §6 I-2/I-3)
- **Problem (CONFIRMED IN CODE, 2026-06-03):** `commitOp` calls `sealChain(db)` after **every** op
  (`site/kernel_ops.js:93`), and `sealChain` (`:137`) re-`SELECT`s the **whole** `kernel_ops` table ordered by
  id and re-hashes **every** row — even already-sealed ones (comment at `:134` admits "for the WHOLE log"). So a
  session of N writes is **O(n²) cumulative** — not a projection of a worry, the actual call path. Measured:
  signed verify 4.6→26.6ms@N=300; projection 52→336 KB / 600 ops. Comfy at hundreds, degrades past thousands;
  a real ERP does thousands/day.
- **Why:** linear-per-op re-seal × growing log = quadratic; the write path stalls at real volume.
- **TEST ALREADY EXISTS — `scripts/spike_writepath.js` (ran 2026-06-03, N=1000):** drives the REAL write path
  (`ERPSeam.dispatch`→kernel→`sealChain`) headless and meters clock + bloat. Measured: `§METER-SEAL`
  seal_ms `13.9@50 → 42.1@1000` (O(n)/persist → O(n²) cumulative); `§BLOAT` projection `52KB→992KB`; its ISSUES
  LEDGER prints I-1…I-5 with numbers = the witness for master §6. **This is the regression gate:** apply the
  seal-from-tip fix → re-run the SAME script → seal_ms must flatten. `poc_volume_ceiling.js` finds the hard wall.
- **Doctrine (NOT in DistributedERP — it's the OLTP/compaction docs):** DistributedERP.md §13 shards the
  *dictionary* by gravity, not the *op-log seal* — different axis. The actual fix is **ERP.md §18.9** (aggregates
  = checkpointed projections: `state = checkpoint_at_N + Σ since N`) + **HolyGrail.md** (period-close signed
  checkpoint = balance-brought-forward; volume POC stayed flat across 100× per LensFamily.md §). 
- **Cheap fix available NOW, independent of checkpointing:** `sealChain` need only seal **from the last sealed
  tip forward** (rows already carrying `op_hash` with a matching `prev` are immutable by the chain's own
  guarantee) — that alone turns the per-write cost O(n)→O(1) amortised and is a pure kernel change. The
  period-close checkpoint then bounds the *projection* fold separately.
- **Decision needed → wiring:** land the incremental seal (cheap) + checkpoint (specced) BEFORE writes scale, or
  cap the POC to hundreds of ops and **log the ceiling** (no silent growth). **This + I-B are the two that turn
  "full ERP" from a UI problem into a real engine problem.**

### I-E. Single-writer / concurrency
- **Problem:** the op-log is single-writer (G-SINGLE-WRITER). Multi-user editing isn't in the device POC.
- **Why:** real ERP = many users; concurrent edits need merge/CAS.
- **Doctrine (FULLY ANSWERED — this is the SPINE of DistributedERP.md):** §1 the 90/10 reframe (~90% is
  single-writer-by-physics; ~10% is a one-way overnight circle, not multi-master), §2 physics partitions by
  location/owner/box-in-hand, §4 the full guard set (G-IDENTITY, G-EXCLUSIVE-DISPATCH, G-SINGLE-WRITER,
  G-RESERVATION, G-ORDERED-HANDOFF, G-LEASE-EXPIRY), §6 the dumb post-office total-order, §9-D the
  ownership/contention adversarial rows, §5 the single real-time op-class (CAS for customer-global
  entitlements). Merge = `union → verify sigs → total-order → replay` (poc_distributed.js). So concurrency is
  not an open question — it's the most-worked part of the doctrine; what's missing is only the *in-browser
  implementation* of that merge.
- **Options:** (1) device-local single-writer only (POC); (2) the DistributedERP per-edge signed logs + merge
  (the doctrine's answer, specced + POC'd server-side, not yet in-browser).
- **Decision needed → wiring:** scope the POC to single-writer explicitly; product = the §6 distributed merge.

### I-F. I-4 op-log schema reconciliation
- **Problem:** live `erp_kernel.kernel_ops` (`op_uuid` PK) ≠ signed `kernel_ops.js` (`id/prev_hash/op_hash/sig`).
- **POC decision MADE:** writes use the deployed signed `kernel_ops.js`. **Production decision still open:** when
  migrating the operator's real op history, reconcile to ONE schema first ("signed-over-the-wrong-table is
  worse than unsigned"). 
- **Decision needed → wiring:** confirm the signed schema is the canonical one and define the migration map.

### I-G. Accounting `complete` unreachable + posting genome coverage
- **Problem:** `readPostings` `complete` needs record-keyed `fact_acct` (§13.6 re-extract; bundled is TOTALS).
  Posting (§13) is proven for sales-invoice-class; full doc-type/tax/period/multi-currency coverage is partial.
- **Doctrine (frame in DistributedERP §8, mechanics in ERP.md):** DistributedERP.md **§8** is the *stance* —
  accounting (double-entry, 1494) **is** the reconciliation engine; we feed clean signed ops into the thing
  already built to reconcile imperfection, so per-record perfection isn't the bar. But the concrete `fact_acct`
  TOTALS limit + §13.6 re-extract + the posting genome live in **ERP.md / PLUGIN_ARCHITECTURE.md §13**, not here.
- **Decision needed → wiring:** which doc types get a real POST manifest before "posted" is claimed; when to do
  the §13.6 re-extract.

### I-H. Schema migration (the honest hard one)
- **Problem:** evolving the AD schema or op-log schema over signed history can't rewrite the past.
- **Doctrine (NAMED AS OPEN in DistributedERP):** §9-E row "Schema migration to N offline clients" (compiled-AD
  manifest + forward-only / frozen-effects replay; honest residual: "an open problem across the category;
  partial mitigation only") and §10's closing cost line ("schema migration remains an open problem (shared
  across the category)"). So the doctrine doesn't claim to solve it — it flags it as a known shared limit. Treat
  it as such; don't let a working prompt assume it's handled.
- **Decision needed → wiring:** migration strategy (forward-only ops, versioned projections) before any
  long-lived signed data.

### I-I. Perf tail (master §6 I-1, I-5)
- I-1 dispatch double-hashes/write (drift 1.57×) → incremental hash. I-5 re-fold full GROUP BY (watch 10k+).
- **Decision needed:** fold incrementally vs accept at POC scale.

### I-J. Other AD schemas the write path touches (AcctSchema · CurrencyRates · Localization)
These aren't new mechanisms — they fold onto the issues above, except currency rate, which is a determinism trap:
- **`C_AcctSchema` (Accounting Schema)** → folds into **I-G**. fact_acct rows are *per acct-schema*; a real
  instance may run **parallel schemas** (e.g. local GAAP + IFRS), so one document posts N times. The posting
  genome (PLUGIN_ARCHITECTURE.md §13) must read `C_AcctSchema` + `C_ValidCombination` + the account element —
  "posted" is per-schema, not global. **Scope I-G to one acct-schema for the POC; name the multi-schema gap.**
- **`C_Conversion_Rate` (Currency Rates)** → **NEW engine issue, determinism-critical.** DistributedERP.md §7 /
  §9-E name "**live FX/rate lookup**" as *the* canonical nondeterminism breaker: if a multi-currency op converts
  using a rate read at *replay* time, two replays diverge → `replay-hash != live-hash`, merge breaks. **Rule
  (non-invent + §7):** the conversion rate in force at the edge must be **captured as an op INPUT** (frozen into
  the op), never looked up later — same discipline as UUID/timestamp/scan. Until that's wired, **multi-currency
  writes stay disabled** (single-currency POC is honest; multi-currency is a determinism hazard, not just a UI gap).
- **Localization** → splits in two: (a) **UI translation** (`AD_Language` / `_trl` tables) is already handled
  via the `_TRL` keyed layer — **done, not an engine issue**; (b) **legal/tax localization** (`C_Tax`,
  `C_TaxCategory`, `C_Country`, country charts of accounts) is **procedural + jurisdictional → folds into I-C
  (callout/validation bypass) and I-G (tax postings)**. Don't claim a write is fiscally valid for a jurisdiction
  whose tax rules aren't extracted.

## §2 DECISION MATRIX (fill in the new session — each row unblocks its wiring)
| # | Issue | POC lean | Product answer | Wiring it unblocks |
|---|-------|----------|----------------|--------------------|
| I-A | durability | demo-labelled | commit→push to install | Save = real vs demo |
| I-B | New/DocNo | disable New | seq/defaults from install | New/Delete enabled |
| I-C | callouts | declarative-only | port hot callouts | commit-gate validation |
| I-D | op-log scale | cap@hundreds | rolling seal + checkpoint | high-volume writes |
| I-E | concurrency | single-writer | distributed merge | multi-user |
| I-F | I-4 schema | signed kernel_ops.js | + migration map | signed writes over real history |
| I-G | posting | sales-invoice class | full manifests + §13.6 | "Posted" beyond demo |
| I-H | schema migration | — | forward-only + versioned | long-lived data |
| I-I | fold/hash perf | accept | incremental | 10k+ rows |

### I-K. Op-group atomicity — a write is rarely ONE op (all-or-none)
- **Problem:** a real ERP write is usually a **document-event = many ops**: `Complete = DOC_ACTION + SHIP +
  INVOICE + Dr-AR + Cr-Rev`, all-or-none. `CRUD_OVERLAY.md` Req 3 says "every change is ONE op" — true for a
  field edit, **wrong for a document action**, which must fold WHOLE or NONE.
- **Doctrine (ANSWERED — but state it explicitly):** ERP.md §18.8 (the op-group is the atomic unit) + witness
  `scripts/poc_showstopper.js` **S2 `§SHOW-ATOMIC`**: a torn op fails the group hash → the **whole** group is
  rejected (none of its ops apply), books stay balanced (Σ=0). So the guarantee exists; it's just **implicit
  under HolyGrail's "hard parts."** A full-ERP write-path doc must name it.
- **STATUS — CAST CODE VERIFIED 2026-06-03:** `commitProcess` (`site/crud_overlay.js:494`) commits **ONE**
  `SET_STATUS` op (comment `:107` "One op per CRUD action") — a **status flip only** (DR→CO/IP via
  `kernelParamsFor`), NOT the consequence set (no SHIP/INVOICE/postings). The kernel has **no group primitive**
  (`commitOp` is the only commit, auto-seals `:93`). So: **no torn-group bug today — but only because the real
  multi-op Complete isn't implemented; "Completed" in the UI ≠ the books moved.** The §SHOW-ATOMIC guarantee is
  proven in the POC but **not on the live write path** (I-F split again).
- **Decision needed → wiring (two layers):** (a) **kernel** — add `commitGroup(ops[])`: N ops under ONE group
  hash, all-or-none, **sealed once per group** (this also kills the per-op I-D reseal — one change, both wins);
  (b) **UI** — `commitProcess` builds the full op-group from the doc descriptor, not one `SET_STATUS`. Maps to
  the **docValidate tier** (IDEMPIERE_2.md §validation-stack) + WfMC DocAction.
- **✅ KERNEL DONE — Phase 2, 2026-06-03 (verified independently).** `commitGroup`/`sealFrom` + `gid` column in
  `site/kernel_ops.js` (v7, mirrored to `build/erp/`, byte-identical). Witnesses GREEN: `poc_opgroup.js` →
  torn=0 rows Σ=0 / clean=whole / verifyChain rejects torn group WHOLE / idempotent; `spike_writepath.js` →
  `§METER-SEAL-FROM` FLAT (commitGroup_ms 0.27@50→0.33@1000, the **I-D win**); `poc_chain.js` PASS (W-CHAIN
  intact). **Open: (a) UI Phase 3** — wire `commitProcess` to build the real op-group; **(b)** `verifyChain`
  full-log walk still O(n) (verify_ms 10→17 — on-demand, not per-write; address with the period-close checkpoint).

## §2.1 PARALLELIZATION — what needs single-minded focus vs. a separate agent
Not all of these are peers. Two are **forks you decide first** (cheap to decide, expensive to get wrong — they
gate the rest); then ~3 lanes are **genuinely independent agents**; the rest are **coupled** or **guardrails**.

**THE SPINE (§0.0) — DECIDED 2026-06-03:** I-A target = **delegate-to-install + mirror NOW** (browser never
authoritatively writes the operator's data); **standalone is a swappable layer, deferred.** This **moves
I-B-hard / I-C / I-G / I-H to the install/oracle lane** — NOT browser-POC blockers. (Greenfield-no-install would
flip standalone back to first-class — deferred, not chosen.)

**DECIDE FIRST — single-minded, you not an agent:**
- **I-F canonical op-log schema** — `op_uuid` live table vs signed `id/prev_hash/op_hash` kernel. "Signed over
  the wrong table is worse than unsigned" — resolve before any agent writes signed ops. (master §6: "decide I-4
  before round 2".) + FORK 0 = ratify the **write seam** (§0.1).

**TOP browser work (genuinely the browser's, both framings — do these first):**
- **I-K op-group atomicity** (multi-op DOC_ACTION all-or-none) + **I-J rate-as-op-input determinism.** These two
  sit in Agent E's lane and gate any honest document write.

**THEN — independent agents, each owns a file-cluster + its own witness (safe to run in parallel):**
- **Agent E (engine perf)** = **I-D + I-I** — `kernel_ops.js` incremental seal-from-tip + period-close
  checkpoint + incremental fold/hash. Pure engine, one file-cluster, witness `§SEAL ms-flat-over-N`. Cleanest
  standalone.
- **Agent V (validation)** = **I-C + I-J(localization-tax)** — extract/validate callouts + tax rules against the
  iDempiere oracle (ERP.md §18.10). Long, **fan-out-able** (one sub-agent per callout family). Independent.
- **Agent P (posting/data)** = **I-G + I-J(acct-schema)** — §13.6 record-keyed `fact_acct` re-extract + posting
  manifests, scoped to one acct-schema first. Data+posting lane, independent.
- **Agent D (distributed, later/big)** = **I-E** — in-browser per-edge log merge (specced + POC'd server-side).
  Product phase, not needed for the POC; fully independent.

**COUPLED — do together, after the fork:** **I-A → I-B** (target decides New). One agent once I-A is decided.
Multi-currency (**I-J/C_Conversion_Rate**) couples to Agent P **and** Agent E's determinism rule — rate-as-input.

**GUARDRAIL — not an agent task, a constraint to respect:** **I-H schema migration** (shared open problem, §9-E
/§10) — don't assign "solve it"; assign "don't break forward-only/frozen-effects."

**Caveat (file contention is real):** Agent E (`kernel_ops.js`) and any New/Save wiring (`crud_overlay.js`) touch
the **same write path** — sequence them on those files or use worktree isolation; "independent" is by *concern*,
not always by *file*.

## §2.2 SESSION ASSIGNMENTS — which prompt owns each lane (pick one, attend it)
Mapped to EXISTING prompts where one fits (reassign, don't duplicate); **CREATE** flags the genuine gaps.
Attend in dependency order: the two forks first, then the independent lanes fan out.

| Order | Lane (issues) | Attend this session | State | Note |
|---|---|---|---|---|
| **1 (fork)** | I-A target + I-F schema | **`FRONTEND_LANE_MASTER.md` §3/§6** (the I-4 gate) | exists | A **decision** session (you, ~30 min) — unblocks everything. master §6 already frames I-4 + persist; add the I-A target call. |
| **2** | I-D + I-I engine perf (incremental seal + checkpoint) | **`ERP_KERNEL_MONSTERS.md`** (add a "seal-from-tip + period-close" task) | exists, needs task | Cleanest standalone; pure `site/kernel_ops.js`. Witness `§SEAL ms-flat-over-N`. |
| **3** | I-G + I-J(acct-schema) posting | **`CRUD_P_R_REPORT.md`** (report fold) + **`ACCTS_POSTED_PANEL.md`** (read) | exists | The §13.6 **record-keyed `fact_acct` re-extract** is the data prereq — check `ERP_RAW_MIGRATION.md` owns it, else **CREATE**. |
| **4** | I-C + I-J(tax) validation/callouts | **`ERP_CALLOUT_PORT.md`** (created 2026-06-03) | exists | **Re-scoped: a TIER-TRIAGE, not a flat port.** iDempiere's 4-tier stack (IDEMPIERE_2.md §validation-stack) → only field-level `CalloutEngine` is a UI overlay; row/doc/posting tiers route to Agent E / DocAction / Agent P. Triage first, port field-tier as witness. |
| **5 (later)** | I-E distributed merge (in-browser) | **`DISTRIBUTED_POC.md`** → graduate POC to in-browser | exists | Product phase; not needed for the write POC. |
| guardrail | I-H schema migration | — (constraint, not a session) | — | Respect forward-only/frozen-effects in every lane above. |

**Also needs review (stale-path contamination, verified 2026-06-03):** `CRUD_OVERLAY.md` (corrected — see its
CORRELATION block) **and `ERP_PHASE_A_SETTLE.md`** both cite `bim-ootb/viewer/kernel_ops.js`, which does not
exist (→ `site/kernel_ops.js`). `erp_replay.js` (owner-gate/CAS) is cited by both but **exists nowhere** — locate
or build before any "invoke A3 guards" task.

## §3 OUTCOME → ACTION
Resolve §2 row-by-row. Each resolution is a citation for the corresponding write-wiring change
(`// Implementing ENGINE_FULL_ERP_ISSUES.md §I-x — Decision: …`). Until a row is resolved, its write stays
honest-disabled / demo-labelled. The witness for "done" is per row (e.g. §WRITE-CRUD chainOk=Y +
durable-path-named; §SEQ next-from-install; §SEAL rolling ms-flat-over-N).

## # DISCUSSION LOG (append decisions here in the new session)

### 2026-06-03 — cross-check vs DistributedERP.md + working prompts + layer map
- **Doctrine already answers** I-A (Truth-3/§5.2b/§9-A), **I-B (§6.1 the centralized-ID problem** — New is
  honest for standalone via edge-mint UUID + per-device DocNo; only blocked when writing into the operator's
  numbered instance), I-E (the whole §1–§9 spine), I-H (named open at §9-E/§10). **NOT in doctrine:** I-C
  (callout port → ERP.md §18.10), I-D fix (→ ERP.md §18.9 + HolyGrail period-close).
- **I-D confirmed in code:** `commitOp`→`sealChain` re-seals the WHOLE log per op (`site/kernel_ops.js:93`/`137`)
  → O(n²). Cheap fix = seal-from-tip (no checkpoint needed for the per-write win).
- **Added I-J** (AcctSchema→I-G; **CurrencyRate = NEW determinism trap, rate-as-op-input**; Localization splits
  i18n-DONE / tax→I-C+I-G).
- **Working-prompt corrections:** `CRUD_OVERLAY.md` got a CORRELATION block (stale `bim-ootb/viewer/*` paths;
  `erp_replay.js` cited but **exists nowhere**; New is LIVE not disabled). `ERP_PHASE_A_SETTLE.md` has the same
  stale path — flagged.
- **Layer map (§0.1):** issues are layer-localized; the **seam (ENGINE_CONTRACT §1)** is the non-collision
  contract. **FORK 0 = co-ratify `§SEAM-FROZEN`** (not yet done, §6.1), then I-A target + I-F schema, then fan out.
- **Session assignments (§2.2):** lanes mapped to existing prompts; **gap = ERP callout port has no prompt
  (CREATE `ERP_CALLOUT_PORT.md`)**.
- **OPEN for next discussion:** architecture deep-dive — SQLite-WASM-at-scale prior art + how iDempiere's
  codebase layers callouts/ModelValidator/AcctSchema (the oracle to extract from).

### 2026-06-03 (cont.) — architecture research + layer-design closeout
- **Layer design is NOT missing — it exists on both axes:** seam (ENGINE_CONTRACT §1), UI concern-layers
  (UI_OVERLAY_GOVERNANCE), engine internals (DistributedERP §0). Don't redesign; map issues onto them (§0.1).
- **SQLite-WASM at scale (storage axis) researched** → added to LocalFirstPriorArt.md §"other axis": **Notion**
  is the existence proof of our doctrine (SQLite-WASM projection + Postgres as system-of-record = I-A answered
  at scale). Hard facts: **~2 GB cap** (→ gravity-shard not optional), **OPFS = 1 txn at a time** (single-writer
  holds at storage too, ↑I-E), **multi-MB JS strings = WASM-bridge cost** (= I-D projection bloat root cause),
  **SAHPool VFS 3–4× + 8–16 MB page cache** (free Agent-E wins), **COOP/COEP headers + Safari<17** (deploy gate).
  SQLite is the right tier (not DuckDB=OLAP, not PGlite=Java-not-PLpgSQL).
- **iDempiere oracle layering** → added to IDEMPIERE_2.md §"validation stack": 4-tier `ModelValidationEngine`
  (Callout field / modelChange row / docValidate doc / FactsValidator posting-per-AcctSchema). **Key planning
  lever:** the "284 callouts" (I-C) are NOT one bucket — only field-tier is a UI overlay; the other 3 are
  engine-tier owned by Agent E / DocAction / Agent P.
- **Re-scoped & CREATED `ERP_CALLOUT_PORT.md`** as a **tier-triage** (classify by tier → route engine tiers to
  their lanes → port field-tier first as witness), not a flat port. §2.2 row 4 updated; the gap is closed.

### 2026-06-03 (cont.) — three nuances from the other session (folded in, verified)
- **(1) New depends on I-C, not just I-B** — edge-mint solves identity; declarative defaults captured, but
  **procedural callout-derived** values are I-C. New is honest on *identity*, not *valid doc* until I-C. Coupled
  in §I-B + the CRUD_OVERLAY correlation. (Verified: `defaultsFor` reads only declarative `AD_Column.DefaultValue`.)
- **(2) FORK 0 names the WRONG seam if left as "the seam"** — the **read/host seam is already frozen** (other
  session: `§SEAM-FROZEN` 31/31, IdmpHost). Only the **write seam** (`write(ctx,ops)`) + the manifest
  **`gravityRank↔menuGroup`** name (ENGINE_CONTRACT §6.1 joint re-freeze) is unratified. §0.1 corrected — don't
  re-ratify the read calls.
- **(3) Op-group atomicity now has its own row (§I-K)** — a write is rarely one op (`Complete =
  DOC_ACTION+SHIP+INVOICE+Dr-AR+Cr-Rev`, all-or-none). Answered (ERP.md §18.8, `poc_showstopper §SHOW-ATOMIC`)
  but was implicit under HolyGrail; now explicit + flagged against CRUD_OVERLAY Req 3 ("every change is ONE op").
- **Net (agreed):** sequencing holds — FORK 0 (write seam) → I-A target + I-F schema → Agent E/V/P fan out.

### 2026-06-03 (cont.) — the SPINE reframe (other session closing) + verification
- **Reframe ACCEPTED, folded into §0.0:** the doc's top-line is NOT "9 issues" — it's ONE boundary,
  **standalone-log vs delegate-to-install**, leaning **delegate** (IDEMPIERE_2.md §migration-Switzerland:
  write-back = separate adapter, bridge not transparent sync). ⇒ **I-B-hard / I-C / I-G / I-H move to the
  install/oracle lane** (not browser-POC blockers). Browser write path shrinks to the doctrine-answered
  standalone set. **TOP browser work = I-K op-group atomicity + I-J rate-as-op-input** (survive both framings).
  Greenfield-no-install caveat keeps the fork alive (then I-D scale + I-E merge return first-class).
- **Log-first corollary ACCEPTED:** "edits reset on reload" = correct (persist the LOG, replay on boot); don't
  persist the projection; don't do both. "Persist the DB" stops being a task; I-A durability mostly dissolves.
- **⚠ ONE claim NOT verified (flagged, non-invent):** other session said Wave 2 "mutates `_records` AND commits
  ops." **Not found in the cast live path** — `site/crud_overlay.js:428` persists the op-log to its own IDB key,
  `glassbowl_data.db` is immutable, CRUD verbs are dry (only DOC_ACTION commits one SET_STATUS). `_records`
  mutation appears only in `site/archive/*` + a test. **Pin down which file before treating the muddle as a task.**

### 2026-06-03 — DECISION (user): delegate-to-install NOW; I-K + I-J = immediate work; standalone = swappable later
- **The fork is closed:** delegate-to-install + mirror is THE architecture now. Standalone-browser-primary is a
  **swappable layer deferred** (not cancelled — greenfield revives it). I-B-hard/I-C/I-G/I-H = install/oracle lane.
- **Immediate browser work = I-K (op-group atomicity) + I-J (rate-as-op-input)** — both in Agent E's lane.
- **First bounded task (proposed):** kernel `commitGroup(ops[])` — N ops, ONE group hash, all-or-none, **sealed
  once per group** (buys I-K atomicity AND the I-D per-op-reseal win in one change). Witness: `spike_writepath.js`
  seal_ms flattens + a torn-group test mirroring `poc_showstopper §SHOW-ATOMIC` (whole group rejected). Then
  `commitProcess` builds the real op-group instead of one `SET_STATUS`.

---

## §I-K SPEC — commitGroup (PHASE-1 spec; kernel change GATED on review — NOT yet implemented)
<!-- // Implementing ENGINE_FULL_ERP_ISSUES.md §I-K — Witness: W-OPGROUP -->
<!-- Sources (non-invent — every rule below is EXTRACTED, not fabricated):
       · atomicity rule  → scripts/poc_showstopper.js S2 §SHOW-ATOMIC (groupIntact/fold, :148-165)
       · seal-from-tip   → §I-D + site/kernel_ops.js sealChain :134-153 (the whole-log re-hash)
       · live commit gap → site/kernel_ops.js commitOp :60-83 (one op, auto-seal :93); no group primitive
       · UI consumer     → site/crud_overlay.js commitProcess :494 (commits ONE SET_STATUS today) -->

### Problem (the gap this closes)
A document action (`COMPLETE_ORDER = DOC_ACTION + SHIP + INVOICE + Dr-AR + Cr-Rev`) is N ops that must fold
**WHOLE or NONE**. Today the kernel exposes only `commitOp(db, opType, params, …)` — **one op per call**, each
auto-sealing the whole log (`site/kernel_ops.js:93`). There is **no group primitive**, so a multi-op action can
only be committed op-by-op: if it tears between ops, partial ops are already committed + sealed → the ledger is
half-posted (books unbalanced, Σ≠0). The witness `scripts/poc_opgroup.js` (W-OPGROUP) demonstrates this gap.

### Signature
```
commitGroup(db, opsArray, groupMeta) -> { gid, ids:[...], op_hashes:[...], tip, sealed, committed:bool }
```
- `db` — sql.js database (same as `commitOp`).
- `opsArray` — ordered `[{ op_type, params, inputGuids?, outputGuid?, op_uuid? }, …]`, N ≥ 1. Order is the
  intra-group total order; it is preserved on commit and on replay.
- `groupMeta` — `{ gid?, expectedHash? }`. `gid` is an edge-minted group id (same G-IDENTITY discipline as
  `op_uuid` — a recorded INPUT, NOT recomputed on replay; minted by caller, or here at commit time when absent).
  `expectedHash` (optional) lets the caller assert the group hash it computed at the edge — a torn payload makes
  the kernel-recomputed hash diverge → the whole group is rejected before any row is committed.

### Group-hash / all-or-none semantics (EXTRACTED from poc_showstopper S2)
The atomicity rule is poc_showstopper's `groupIntact` (`:148`) + `fold` (`:158-165`): a group applies only if
**every** op in it re-hashes correctly against the running `prev`; if any op fails, **none** of the group's ops
apply (`rejected.push(gid)`), and the projection is **identical to as-if-the-group-was-never-appended**
(`proj==asIfNeverAppended`, balSum stays 0). `commitGroup` mirrors this at COMMIT time, not just at fold time:
1. **Stage, do not commit:** compute, for the N ops in order off the current sealed tip, each `prev_hash`/
   `op_hash` (the same `_sha256(prev + '|' + _canonical(op))` chain as `sealChain`). Derive `groupHash =
   sha256(tip | op_hash[0] | … | op_hash[N-1])` — ONE hash binding the whole group to the tip.
2. **All-or-none gate:** if `expectedHash` is supplied and `groupHash !== expectedHash` → **reject the whole
   group, commit nothing** (return `{ committed:false, reason:'group hash mismatch' }`). This is the torn-group
   rejection (poc_showstopper's `groupHash=FAIL → rejected`).
3. **Atomic commit:** wrap the N `INSERT`s in **one SQL transaction** (`BEGIN … COMMIT`/`ROLLBACK`). Any error
   on any op → `ROLLBACK` → zero rows land (no half-group). This is the "WHOLE or NONE" at the storage tier
   that poc_showstopper proves at the fold tier.
4. **Group binding column:** every committed op records its `gid` (and optionally `group_hash`) so the fold can
   re-segment groups exactly as poc_showstopper's `groupsOf`/`groupIntact` do. ⚠ The live schema lacks these
   columns today — see §I-F surprise below.

### Sealed ONCE per group (the I-D win — named)
poc_showstopper's gap (§I-D, `site/kernel_ops.js:134-153`): `sealChain` re-`SELECT`s and re-hashes the **WHOLE**
log on every `commitOp`. Committing an N-op group via N `commitOp` calls = **N whole-log re-seals** = the O(n²)
cost. `commitGroup` seals **once, from the last sealed tip forward**, over only the N new rows:
- read the current tip (`prev_hash`/`op_hash` of the highest already-sealed `id`), not the whole log;
- hash forward over only the N staged ops, writing their `prev_hash`/`op_hash`/`sig` in the same transaction;
- already-sealed rows are immutable by the chain's own guarantee, so they are **not** re-read or re-hashed.
This turns per-group seal cost from O(log-length) to O(N-in-group) — the same change that flattens
`spike_writepath.js`'s `§METER-SEAL seal_ms`. **One change, both wins** (I-K atomicity + I-D seal).

### Idempotency
- Re-invoking `commitGroup` with a `gid` already present in `kernel_ops` is a **no-op** (returns the existing
  `{gid, ids, tip}`), not a duplicate append — the gid is the idempotency key (replay-safe, retry-safe).
- The seal is idempotent on already-sealed rows (re-running over them yields the same hashes), preserving
  `sealChain`'s current idempotency contract.

### verifyChain over a group
`verifyChain` keeps walking the flat log in `id` order (unchanged per-op link/hash check). Its ONE added rule
mirrors poc_showstopper's `groupIntact`: a verifier MAY treat a `gid` as atomic — if **any** op of a `gid`
fails its link/hash, the WHOLE group is reported broken (`brokeAt = first op of gid`, `why:'group torn'`), and a
group-aware fold must skip every op of that `gid` (never apply a surviving sibling — that is the half-state
poc_showstopper's `naiveFold` shows UNBALANCES). The per-op chain stays the substrate; the group is the atomic
fold unit layered on top.

### Phase-2 edit points (site/kernel_ops.js — NOT changed in Phase 1)
- `:9-22` `TABLE_SQL` + `:36-44` idempotent `ALTER` block — add `gid TEXT` (and optionally `group_hash TEXT`)
  columns (idempotent ALTER, same pattern as the existing op_uuid/prev_hash adds). **This is the I-F dependency.**
- `:60-83` `commitOp` — leave as the single-op path; add `commitGroup` beside it (do NOT route single ops
  through a transaction needlessly).
- `:90-108` `_persistToIdb` / `:137` `sealChain` — add a `sealFrom(db, tip)` incremental seal used by
  `commitGroup`; leave `sealChain` (full re-seal) for post-compaction correctness.
- `:158` `verifyChain` — add the group-torn rule above.
- `:309-320` the `window.KernelOps` export — add `commitGroup` (and `sealFrom` if extracted).
- **build mirror:** `build/erp/kernel_ops.js` must receive the identical change (Sacred mirror).
- **UI (separate task, site/crud_overlay.js):** `commitProcess` (`:494`) builds the full op-group from the doc
  descriptor and calls `commitGroup`, instead of one `SET_STATUS` via `commitOp` (`:499`).

---

### 2026-06-03 — I-K KERNEL LANDED (Phase 2, agent + watchdog-verified)
- **DONE:** `commitGroup`/`sealFrom` + `gid` column in `site/kernel_ops.js` (v7, mirrored byte-identical to
  `build/erp/`). I-F resolved for this path: signed `kernel_ops.js` ratified canonical; `gid` added additively.
- **Witnessed (re-run independently, NOT trusted from the agent):** `poc_opgroup.js` PASS (torn=0 rows Σ=0 ·
  clean=whole Σ=0 · verifyChain rejects torn group WHOLE · idempotent); `spike_writepath.js` `§METER-SEAL-FROM`
  FLAT (commitGroup_ms 0.27@50→0.33@1000 vs old 3.7× — **I-D flattened**); `poc_chain.js` PASS (W-CHAIN intact).
- **Still open:** Phase 3 = wire `crud_overlay.js commitProcess` to build the real op-group (not one
  `SET_STATUS`) — the docValidate-tier UI step. And `verifyChain` full-log O(n) walk (verify_ms 10→17, on-demand
  not per-write) → period-close checkpoint.
- **Noted (pre-existing, not mine):** `scripts/test_kernel_sign.js` requires stale `bim-ootb/viewer/erp_signer.js`
  (the known `bim-ootb/viewer/*` staleness) — broken before this change; W-SIGN verified directly instead.

### 2026-06-03 — I-K PHASE 3 LANDED (UI write path → commitGroup; non-invent verified)
- **DONE:** `site/crud_overlay.js` `commitProcess` now commits the doc action via `commitGroup` (pure
  `buildDocActionGroup(op)` → `[statusOp]` today, extensible to N), mirrored byte-identical to `build/erp/`.
  Live write is now ATOMIC-ready + gets the seal-from-tip I-D win on the real UI path.
- **NON-INVENT honored + witnessed:** the consequence set (ship/invoice/Dr-AR/Cr-Rev) was NOT fabricated —
  it is a clearly-marked DELEGATED extension point (install-side: I-C procedural callouts + I-G §13.6 postings,
  per §0.0). Witness `poc_crud_group.js`: `§CRUDGROUP non-invent fabricatedConsequence=none`.
- **Witnessed (re-run independently):** `poc_crud_group.js` PASS (viaGroup=Y, gid stamped, sealed-from-tip,
  verifyChain ok, allShareGid=Y); `poc_opgroup.js` PASS; `poc_chain.js` PASS.
- **Still open:** the delegated consequence ops arrive only via the install / §13.6 re-extract (NOT browser
  work). I-J (rate-as-op-input) is the remaining top browser item. No deploy yet (EXPLICIT GO pending).

### 2026-06-03 — I-J LANDED + LOCALHOST TEST + POSTGRES BENCHMARK
- **I-J DONE (verified):** `assertRateAsInput` guard in `site/kernel_ops.js` v8 (mirror identical), wired into
  `commitGroup` staging — a conversion-bearing op (params.convertedAmt or params.fx) MUST carry rate/rateDate/
  rateSource or the WHOLE group rejects (multi-currency stays disabled until rate-as-input). Witness
  `poc_rate_input.js`: C1 single-cur honest, C2 rate-as-input hash==, C3 live-lookup hash!= (the hazard), C4
  guard rejects. No regressions.
- **LOCALHOST TEST (real Chromium, `build/erp/probe_localhost.log`):** served `site/` at :8848; loaded
  glassbowl.html — kernel v8, NO pageerrors. In-browser `commitGroup` folded a 2-op group all-or-none (shared
  gid, sealed once, verifyChain ok); rate guard rejected conversion-without-rate (0 rows); Edit-mode armed
  (`§CRUD mode=on rings=5`). PROBE PASS. The deployed write path works in a real browser.
- **BENCHMARK vs Postgres-15 (iDempiere's engine), `scripts/bench_oplog_pg.js` → `build/erp/bench_oplog_pg.log`:**
  op-log PRIMITIVE only (1000 ops, one atomic commit; TEMP table, real idempiere data untouched). sql.js
  (browser, in-mem, **incl. sha256 chain**) = 208ms; Postgres (durable WAL, **storage only, no hashing**) =
  5.2ms; PG per-durable-commit floor ~0.16ms/txn. **HONEST READING (no hype, per [erp_perf_claims]):** mature
  Postgres is FASTER at the raw primitive — the browser's edge is **locality (no server / offline / instant
  local write), NOT speed.** iDempiere's full `completeIt()` (callouts + ModelValidator + multi-table posting,
  Java) sits ON TOP of the PG storage cost and was NOT measured (no Tomcat; delegated to the install, §0.0).
  Caveat: not identical work (sql.js hashes, PG didn't); PG durability config (synchronous_commit) affects the
  fsync floor — don't over-read the per-commit number.
- **DONE this session:** I-K (kernel+UI) ✅ · I-J (guard) ✅ · localhost-tested ✅ · benchmark ✅. **Browser-side
  write-path TOP work complete.** NEXT: `prompts/ENGINE_WRITE_PATH_NEXT.md`.

### 2026-06-03 — BigDecimal ENFORCED + DEPLOYED to branch (PR #8)
- **BigDecimal enforced (feedback_numbers_via_bigdecimal):** `bigdecimal.js` (exact decimal == java.math.BigDecimal,
  proven) is now LOADED in `glassbowl.html` (site/ + build/erp/) BEFORE the kernel — it was present in the repo
  but NOT loaded. The I-J conversion math (`scripts/poc_rate_input.js convCents`) now uses
  `BigDecimal.multiply().setScale(0,HALF_UP)`, never raw float (also sharpens C3: divergence is the RATE, not
  float noise). Verified in real Chromium: `window.BigDecimal` present, `10000×0.92=9200` exact, no page errors.
  Kernel `Number(...)` calls are counts/IDs (not money) → correctly left. **Follow-up:** audit broader money
  folds (erp_postings, reports) for raw-Number math — queued in ENGINE_WRITE_PATH_NEXT.md.
- **DEPLOYED to branch (NOT merged — CICD's job):** branch `feat/erp-write-path-ik-ij`, **PR #8 → `full`**
  (https://github.com/red1oon/BIMCompiler/pull/8). 11 files (build/erp kernel+crud+glassbowl, scripts witnesses,
  this doc, docs/IDEMPIERE_2 + LocalFirstPriorArt). Pre-commit compile gate passed. `site/` is the gitignored
  publish mirror — tracked source = `build/erp/`. NOT yet OCI-deployed to bim-ootb-live (separate EXPLICIT-GO).
- **DONE this session (final):** I-K (kernel+UI) ✅ · I-J (guard) ✅ · BigDecimal enforced ✅ · localhost-tested ✅
  · benchmark ✅ · branched+PR ✅. Resume: `prompts/ENGINE_WRITE_PATH_NEXT.md`.

---

## §I-L SPEC — Money-fold BigDecimal audit (NEXT item 7; read-side folds)
<!-- // Implementing ENGINE_FULL_ERP_ISSUES.md §I-L — Witness: W-MONEY-FOLD (feedback_numbers_via_bigdecimal) -->
<!-- Sources (non-invent — every divergence below is MEASURED, not asserted; probe logged before the fix):
       · the offending fold  → build/erp/report_overlay.js round2()/raw-Number reduce (:34/:54/:75/:82-83/:98-102)
       · the exact oracle    → build/erp/bigdecimal.js (== java.math.BigDecimal, proven 446/446)
       · scope decision      → erp_postings.js already integer-cent (cents()=Math.round(n*100), exact ≤2^53c) -->

### The gap (MEASURED, not assumed — /tmp probe 2026-06-04)
`report_overlay.js` is PURE READ (no kernel write, nothing hashed) so this is a **correctness** issue, not a
replay-determinism one — but a financial *report* that doesn't match iDempiere/Postgres to the cent is wrong on
its own terms. Its folds (`foldReceipt`/`foldTrialBalance`/`foldPnL`) accumulate with raw `Number` `+`/`reduce`
and round with `round2(n)=Math.round((n+EPSILON)*100)/100`. Empirically this matches golden ONLY for
**positive, sub-$9e13, clean-2dp** data. It DIVERGES from `java.math.BigDecimal` on the values double-entry is
built from:
- **Negative half-cents** — `Math.round` rounds .5 toward **+∞**; Java `HALF_UP` rounds **away-from-zero**.
  `-1.005 → round2 -1.00` vs exact `-1.01`. Hits every `dr−cr` balance, P&L `net`, tax-as-remainder, credit/refund.
- **Balance masking** — a TB line with a 3rd-decimal credit: balance should be `-0.01`, `round2` says `0.00` →
  a real imbalance is hidden (the worst failure for a trial balance whose whole job is to prove ΣDr==ΣCr).
- **Magnitude > 2^53 cents (~$9e13)** — float drops cents on large totals (`…999.99 + 0.02 → …000.00` vs `.01`).
- (NOT a divergence — reported honestly: sub-cent *dust* accumulation `100000×0.001` stayed exact; `round2`'s
  +EPSILON rescues small positives. Don't over-claim "round2 is broken" — it's broken for SIGNED + LARGE.)

### The fix (additive, read-side, no API break)
Route every monetary accumulation in the three CORE folds through `BigDecimal`: source raw row values via
`bd(v)=BigDecimal.of(String(v))` (String so a non-integer float never enters `of(Number)`, which throws by
contract), accumulate exact, `.setScale(2, HALF_UP)` at the single money leaf (`money2`). `balanced` becomes
an **exact** `diff.isZero()` (no float compare). Money leaf is carried as an **exact 2dp STRING** (not Number)
so the value is exact even past 2^53 cents; `render()` formats it via `Number(x).toFixed(2)` unchanged.
`round2` stays only for non-money use (qty/price display).

### Scope (swept)
- **CONVERT:** `report_overlay.js` `foldReceipt` (subtotal/tax/total), `foldTrialBalance` (dr/cr/balance/totals/
  balanced), `foldPnL` (net/revenue/expense/netIncome). Mirror `build/erp/`→`site/` (mirror was STALE — site
  lacked TB+PnL; re-synced).
- **LEAVE (audited exact):** `erp_postings.js` accumulates **integer cents** (`cents()`→`Math.round(n*100)`,
  `dr===cr`) — exact ≤2^53c; the only residual is `cents()`'s own per-value boundary, noted not fixed (its inputs
  are 2dp op-log amounts). Kernel `Number()` = counts/IDs. `crud_overlay.js:87` `Number(val)` = field-input
  capture (flagged residual: a money field typed in the New form enters the op as a Number — write-path item,
  not this read-side audit).

### Witness — `scripts/poc_money_fold.js` (W-MONEY-FOLD)
Names the issue it proves: *raw-Number+round2 financial folds diverge from java.math.BigDecimal on signed/large
money; the BigDecimal folds reproduce golden exactly.* For each MEASURED vector above: (1) the inlined OLD raw
fold DIVERGES from golden (proves the bug was real, non-invent), (2) the LIVE BigDecimal fold == golden string,
(3) `foldTrialBalance.balanced` is exact (no spurious balance). Golden = independent BigDecimal string math
(the proven oracle). `§MONEY-FOLD PASS`.

### # DISCUSSION LOG — 2026-06-04 — §I-L money-fold audit
- **Probed first (non-invent):** found `round2` is faithful for small positives (its +EPSILON rescues the
  classic 1.005/2.675 cases) — so the honest gap is **signed + large**, not "round2 is broken." Witness vectors
  are the measured divergences only.
- **erp_postings left as-is:** integer-cent strategy is already exact for its purpose; converting it would be
  churn without a witnessed divergence. Documented, not touched.

### 2026-06-04 — §I-L LANDED (read-side money folds → BigDecimal; witnessed)
- **DONE:** `build/erp/report_overlay.js` `foldReceipt`/`foldTrialBalance`/`foldPnL` now accumulate in
  `BigDecimal` off the RAW rows, carry the money leaf as an exact 2dp STRING, and report `balanced` via exact
  `isZero`. Mirror `site/report_overlay.js` re-synced (was STALE — lacked TB+PnL; now md5-identical).
- **Witness `scripts/poc_money_fold.js` → `§MONEY-FOLD PASS` (11/11):** the inlined OLD raw-Number+round2 fold
  DIVERGES from java.math.BigDecimal on signed sub-cent (S: TB balance 0.00 vs −0.01, receipt tax, P&L net) and
  magnitude >2^53c (M: …000.00 vs …000.01); the LIVE folds == proven golden; `balanced` exact (B). Divergences
  MEASURED (probe logged), never invented; one S-vector was corrected after the witness caught its float noise
  rounded correctly by luck (vector fixed, assertion never).
- **Regression:** `scripts/test_report_overlay.js` + `scripts/test_report_fin.js` updated to the exact-STRING
  contract (cent-exact compare, no `round2`-on-string) → both ALL PASS. Values unchanged (same TB Dr=Cr=46574.97
  balanced=Y, receipt 95.00/3657.50). Logs in `build/erp/poc_money_fold.log` + `test_report_*.log`.
- **Residual (flagged, not this lane):** `crud_overlay.js:87` `Number(val)` coerces a New-form money field into
  the op as a JS Number — write-path input capture, routes to the write lane, not this read-side fold.

---

## §I-D-CKPT SPEC — period-close signed checkpoint in the LIVE kernel (I-D residual; verify/fold bound)
<!-- // Implementing ENGINE_FULL_ERP_ISSUES.md §I-D — Witness: W-CHECKPOINT -->
<!-- Sources (non-invent — every rule below is EXTRACTED, not fabricated):
       · the precise rule   → ERP.md §18.9 "aggregates are checkpointed projections": state = checkpoint_at_N + Σ(since N)
       · the ritual         → HolyGrail.md §1 "balance brought forward": at close write ONE signed checkpoint carrying
                              (a) closing balances + (b) the chain-head fingerprint, signed by the controller; it becomes
                              a new genesis; the closed period is archived (cold), not deleted; re-add the archive and it
                              MUST fold to the signed balance to the cent or fraud is proven.
       · the concept proof  → poc_volume.js §VOL-BOOTSTRAP (fold-from-checkpoint FLAT) + poc_showstopper.js S3 §SHOW-CKPT
                              (signed checkpoint verifies under controller key; archive reconciles to the cent) — BUT both
                              on FREE-STANDING primitives, NOT the live kernel.
       · the live gap       → site/kernel_ops.js verifyChain :349 SELECTs the WHOLE log ORDER BY id and re-hashes EVERY
                              row from GENESIS, every call (verify_ms 10→17 @1000, on-demand). NO checkpoint primitive
                              exists in the kernel (grep checkpoint build/erp/kernel_ops.js → 0 hits). -->

### The gap this closes (MEASURED, not assumed)
The checkpoint *concept* is proven (poc_volume §VOL-BOOTSTRAP flat; poc_showstopper S3 §SHOW-CKPT signs+reconciles)
— but **only on free-standing fixtures**. The **live kernel** `verifyChain` (and any cold fold built on it) walks
from `GENESIS` over the **entire** `kernel_ops` log on every call → cost ∝ **total history**, not ∝ the open
period. A real ERP appends thousands of ops/day; the on-demand "is the chain trusted?" check grows without bound.
ERP.md §18.9 + HolyGrail §1 give the fix verbatim: at period close, record ONE **signed checkpoint** (head
fingerprint + closing balances); the next period chains off it; verify/fold start FROM the checkpoint, so cost is
bounded by the **open period**, flat across periods.

### The fix (additive, kernel-only, opt-in — zero behaviour change to existing callers)
1. **`kernel_checkpoints` table** (idempotent `CREATE TABLE IF NOT EXISTS`; never touches `kernel_ops` or
   `_canonical`, so the existing chain stays byte-identical and all 5 guardrail witnesses stay GREEN):
   `id` · `at_op_id` (the sealed tip op id at close) · `head_hash` (the op_hash AT `at_op_id` = the chain head
   carried forward = the open period's starting `prev`) · `balances` (optional JSON closing balances — "balance
   brought forward", the §18.9 `checkpoint_at_N`) · `balances_digest` (sha256 of canonical balances, '' if none) ·
   `sig` (edge signature over the checkpoint payload, NULL if no signer) · `timestamp`.
2. **`closePeriod(db, {balances?, asOf?, gid?})`** — read the current sealed tip via `_lastSealedTip`; the signed
   payload = `head_hash | at_op_id | balances_digest` (so a tampered head OR tampered balances breaks the sig).
   Sign with the installed `_signer` if present (HolyGrail "signed by the controller"); else W-CHAIN-only anchor.
   Insert one checkpoint row. NON-INVENT: balances are the caller's folded numbers (the report/ledger lane
   supplies them) — the kernel NEVER computes or fabricates balances; it only anchors+signs what it is handed.
   Returns `{ checkpointId, atOpId, headHash, balancesDigest, sig, signed }`.
3. **`verifyChain(db, opts)`** — **opt-in** `opts.fromCheckpoint:true`: load the latest checkpoint, verify its
   signature (if a signer is set — a forged/missing sig ⇒ reject before walking), set `prev = checkpoint.head_hash`
   and walk ONLY ops with `id > at_op_id`. The **default call (no opts) is byte-identical to today** — full walk
   from GENESIS (the cold-archive audit path; backward compatible). Returns the same shape + `fromCheckpoint:true`,
   `checkpointId`, `scannedFrom`.
4. **`latestCheckpoint(db)`** — helper returning the highest-id checkpoint or null.

### Bound semantics + the HONEST trade
- **The win:** bounded `verifyChain` cost ∝ **open-period length**, FLAT as total history grows; full verify ∝
  total history. MEASURED in the witness across K periods (the I-D residual closed for verify).
- **The trade (stated, not hidden):** bounded verify TRUSTS the signed checkpoint anchor — it does NOT re-walk the
  cold archive. That is the *point* (§18.9 cache the log can regenerate), made safe by the **signature** over the
  head. The cold archive is re-proven only by a FULL verify (or archive re-fold to the signed balance, HolyGrail
  §1 "to the cent or fraud is proven"). Both paths stay available; bounded is the hot path, full is the audit path.
- **No deletion:** `closePeriod` records the anchor; it does NOT delete/archive the cold rows (freeing the tab is a
  separate persistence/compaction concern — `compact()`'s lane). The bound is on verify COST (start the walk at the
  checkpoint), achieved without losing data. Honest residual: tab-size reduction (cold eviction) is future work.

### Witness — `scripts/poc_checkpoint.js` (W-CHECKPOINT), names the issue it proves
*Issue: the LIVE kernel re-verifies the whole log every call (∝ total history); a signed period-close checkpoint
must bound verify to the open period (flat across periods) WITHOUT losing tamper-evidence.* On the real
`build/erp/kernel_ops.js` (sql.js, ECDSA signer from poc_sign):
- **C1 BOUND (the I-D win, MEASURED):** commit K periods of P ops each; full `verifyChain` ms GROWS with total
  history; `verifyChain({fromCheckpoint})` ms stays FLAT (∝ P) across all K. FALSIFIER: not-flat ⇒ fail.
- **C2 OPEN-PERIOD TAMPER still caught:** tamper an op in the open period → bounded verify reports `ok=false`
  `brokeAt` = exact op (group-torn rule preserved).
- **C3 ARCHIVE RE-FOLD reconciles (HolyGrail §1):** full verify over the whole log == the signed checkpoint head;
  tamper a COLD (pre-checkpoint) op → full verify catches it AND head ≠ signed head ⇒ fraud proven.
- **C4 FORGED CHECKPOINT rejected:** alter the checkpoint head/balances/sig → bounded verify rejects the anchor
  (signature fails) before trusting it.
- **C5 DETERMINISM:** rebuild the same scenario twice → byte-identical checkpoint head + balances_digest.
- **C6 NON-REGRESSION:** default `verifyChain(db)` (no opts) byte-identical to pre-change (the 5 guardrail
  witnesses re-run GREEN). `§CHECKPOINT PASS`. (9 verdict checks: C0, C1-bound, C1-sanity, C2, C3-anchor,
  C3-cold, C4, C5, C6.)

### 2026-06-04 — §I-D-CKPT LANDED (period-close signed checkpoint in the live kernel; witnessed)
- **DONE (build/erp/kernel_ops.js v9, additive only):** new `kernel_checkpoints` table (idempotent CREATE,
  never touches `kernel_ops`/`_canonical`) + `closePeriod(db,{balances})` (signs the anchor
  `head_hash|at_op_id|balances_digest` with the installed edge signer) + `latestCheckpoint(db)` + `verifyChain`
  gains **opt-in** `opts.fromCheckpoint` (loads the latest checkpoint, VERIFIES its signature, then walks ONLY
  ops `id > at_op_id` from `prev = head_hash`). Default `verifyChain(db)` is **unchanged** (full walk from
  GENESIS). Exports + version bumped v8→v9 (`W-CHECKPOINT`).
- **Witness `scripts/poc_checkpoint.js` → `§CHECKPOINT PASS` (9/9 checks green):** on the REAL kernel (sql.js + ECDSA
  P-256 controller). **MEASURED I-D win** (`§CHECKPOINT-BOUND`, K=4 periods × P=120 docs): full verify
  `24.1→44.8→72.7→93.0 ms` (GROWS ∝ total history) vs bounded `25.4→24.9→23.2→23.2 ms` (FLAT ∝ open period),
  4.0× speedup at period 4. C2 open-period tamper still caught at the exact op (group head, `why=group torn`);
  C3 signed head == sealed op_hash at close + cold-archive tamper proven by FULL verify while bounded trusts
  the signed anchor (**the stated trade**, honest); C4 forged anchor rejected (`why=checkpoint signature`); C5
  deterministic head+digest across rebuilds (ECDSA sig varies by design, not compared); C6 default path
  unchanged. Log: `build/erp/poc_checkpoint.log`.
- **NON-REGRESSION:** all 5 guardrail witnesses re-run GREEN (poc_opgroup/poc_crud_group/poc_rate_input/
  poc_chain/poc_kernel) — the change is purely additive.
- **HONEST RESIDUALS (named, not hidden):** (a) the bound is on verify COST (start the walk at the checkpoint);
  `closePeriod` does NOT delete/evict the cold rows — tab-size reduction stays `compact()`-lane future work.
  (b) the **balances fold** (replay/projection) is bounded in poc_volume already; this lane bounds the kernel's
  **verify** specifically — a checkpoint-anchored kernel *replay* bound is a follow-on. (c) bounded verify TRUSTS
  the signed anchor (does not re-walk the archive) — safe only because the anchor is signed (C4); the cold
  archive is re-proven only by FULL verify / archive re-fold (C3).
- **⚠ MIRROR DRIFT FOUND (pre-existing, NOT caused here; flagged for the deploy lane):** `build/erp` kernel is
  v9 but the env mirrors are stale — `site/kernel_ops.js`=**v6**, `docs/kernel_ops.js`=**v6**,
  `deploy/dev/kernel_ops.js`=**v4** (contradicts the handoff's "site v8 byte-identical mirror" claim — site is
  not v8 on disk). Each env's other files (crud_overlay/glassbowl.html) sit at their own drift, so a lone-kernel
  sync would make an untested mixed state. Mirror reconciliation = a full per-env publish, GO-gated (deploy
  flow publishes from `deploy/dev/`; localhost serves `site/`). **NOT synced in this no-deploy engine task.**
- **NOT committed / NOT deployed** — working-tree only (`build/erp/kernel_ops.js` + new `scripts/poc_checkpoint.js`
  + this doc). EXPLICIT GO pending for OCI; commit/PR at the user's call (PR #8 branch).
