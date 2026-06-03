# ⚠ DO NOT REMOVE — Engine issues that GATE the full-ERP write path (DISCUSSION work-order)
# SCOPE: the engine-level issues that surface when the browser ERP moves from READ-ONLY to a
#   full-fledged ERP (real writes: New/Save/Delete, kanban drag→dispatch, posting, install).
#   This is a DISCUSSION prompt for a NEW session — its OUTCOME determines HOW (and whether) each
#   write is wired. Do NOT wire production writes before the matching decision below is made.
# STATE (2026-06-03): the POC has read-only lenses + the pill rail LIVE on localhost; Wave 2 wired
#   POC-DEMO writes (honest-disabled where blocked). Those demo writes are NOT production — they are
#   the thing this prompt exists to make real (or to consciously keep as demo).
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
are genuinely parallel — not by luck, by the seam. **So the true first move is FORK 0: co-ratify the seam
(`§SEAM-FROZEN`, ENGINE_CONTRACT §1/§6.1) — every lane writes against it.** I-A + I-F come next, then fan out.

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

## §2.1 PARALLELIZATION — what needs single-minded focus vs. a separate agent
Not all of these are peers. Two are **forks you decide first** (cheap to decide, expensive to get wrong — they
gate the rest); then ~3 lanes are **genuinely independent agents**; the rest are **coupled** or **guardrails**.

**DECIDE FIRST — single-minded, you not an agent (the two forks everything hangs off):**
- **I-A target** — standalone distributed op-log *or* writes into the operator's live iDempiere? This fork
  decides I-B (New gating), I-F (production schema), I-G (whose acct-schema).
- **I-F canonical op-log schema** — `op_uuid` live table vs signed `id/prev_hash/op_hash` kernel. "Signed over
  the wrong table is worse than unsigned" — resolve before any agent writes signed ops. (master §6: "decide I-4
  before round 2".)

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
| **4** | I-C + I-J(tax) validation/callouts | **CREATE `ERP_CALLOUT_PORT.md`** | **gap** | `00e_validation` is BIM AD_Val_Rule, NOT the 284 procedural-Java ERP callouts. Extract-validate vs the iDempiere oracle (ERP.md §18.10); fan-out per callout family. |
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
