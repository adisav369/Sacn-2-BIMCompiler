# Local-First Prior Art — How Others Did It, Their Weaknesses, Our Workarounds

**Scope.** Research companion to `ERP.md §0.20` (secured/durable next phase). For each production
local-first system: *how it works*, *its documented weakness*, and *the workaround our architecture
(`kernel_ops` deterministic semantic verbs + two-domain async split + native SQL + BIM/ERP unification)
opens*. Researched 2026-05-31; sources at end. Non-invent: weaknesses are each system's *own documented*
trade-offs, not our characterisation.

---

## The one lever that changes everything: **deterministic replay of semantic verbs**

Every system below pays a tax our prime directive (deterministic, non-invent) waives for free. Their
common problem: **the server and the client run *different code*** (or different merge rules), so they must
either (a) hand-code conflict logic per mutation, (b) overwrite the user's optimistic state when the server
disagrees, or (c) implement business logic *twice* (client + backend). Because `kernel_ops` verbs are
**deterministic and replayable** (proven: `replay-hash == live-hash`, §0.16/§0.19), the **same kernel runs
in both domains** (`ERP.md §701` — "the same core runs under sql.js (browser) and server"). Server result
== client result *by construction*. That single property neutralises the central weakness of Replicache,
PowerSync, and ElectricSQL at once.

---

## Per-system: mechanism → weakness → our workaround

### 1. Replicache — server re-runs mutations as authority
- **How:** client mutators run optimistically; push endpoint **re-executes** them on the canonical store;
  pull **rebases** (rewind to last confirmed state → apply server patch → replay pending mutations).
- **Documented weakness:** the docs state the server *"is not necessarily expected to compute the same
  result"* as the client → on rebase, **optimistic changes can be overwritten** (flicker / lost work);
  mutators must be re-execution-safe (run ≥3×); conflict logic is **hand-coded per mutator**; **no
  offline-only mode** — a backend is mandatory.
- **Our workaround:** determinism removes the divergence — our verbs compute the *same* effect on both
  sides, so there is **no "server disagrees → overwrite"** case and **no per-mutator conflict code**. And
  we *do* have a true offline-only mode (single-user, zero server) that Replicache structurally cannot.

### 2. ElectricSQL — retreated to read-path-only sync
- **How:** syncs data **out of** Postgres to clients via HTTP "Shapes" (read path). **Writes are NOT in the
  sync engine** — you send them through "your existing REST API."
- **Documented weakness:** this is an explicit *retreat* — the blog admits bidirectional sync's
  "conflict resolution / consistency / operational complexity" is "fundamentally difficult," so they
  **sidestepped writes entirely.** Result: **two disjoint paths** (sync for reads, your API for writes),
  no unified model; requires Postgres + SUPERUSER, shadow tables, triggers, high memory.
- **Our workaround:** the op-log is **symmetric** — the ops you *push* are the same ops that drive *reads*
  (one model, the log). We never need a separate write API, and there's no Postgres coupling (sql.js *is*
  the store).

### 3. PowerSync — server authority, writes through your backend
- **How:** Postgres→SQLite via logical replication; op-history kept in the **PowerSync Service** (not your
  DB); **writes routed through your own backend** so you apply business logic / authz / validation; causal
  consistency.
- **Documented weakness:** requires the PowerSync Service **plus** your backend; you must **implement the
  write-side business logic and conflict resolution yourself** — i.e. **the same rules twice** (client
  optimistic + server authoritative), a known source of drift.
- **Our workaround:** the "business logic on the write path" *is* our deterministic kernel — **authored
  once, run on both sides.** No double-implementation, no client/server rule drift (the thing PowerSync
  makes you maintain by hand).

### 4. LiveStore — nearest neighbour (event-sourced, SQLite-materialised)
- **How:** events in an eventlog → **materialised into reactive client SQLite** via materialisers;
  git-inspired sync; pluggable sync backend (Cloudflare / Electric / S2).
- **Documented weakness:** **BETA (v0.4)**; requires a sync-provider backend (no hosted service);
  *"doesn't sync with existing databases," "doesn't scale for unbounded data," "no P2P,"* and explicitly
  **"not batteries-included — no built-in auth or file uploads."**
- **Our position (not a workaround — an honest delta):** conceptually almost identical at the data layer,
  so we claim **no technique novelty**. Differentiators are *application-level*: we span **BIM geometry +
  ERP** under one log, ship a real **domain reduction** (iDempiere AD → 5 tables + verbs), and are
  **batteries-included** (AD-derived rules/validation). LiveStore is infrastructure; we are an application
  on the same idea. Its "no auth / doesn't scale unbounded" gaps confirm these are **industry-wide**, not
  ours alone.

### 5. CRDTs (Automerge 3.0 / Yjs) — guaranteed convergence
- **How:** mathematical merge → all replicas converge with no central referee.
- **Documented weakness (from the CRDT literature):** *"CRDTs can only handle invariants that do **not**
  depend on the most up-to-date version"* → they **cannot enforce business invariants** (transactional
  edits, permissions); the field says use **OT or server-authoritative sequencing** for those. Also: **no
  built-in access control**, tombstone/GC overhead, and *"hard to use beyond what the library anticipated."*
- **Our workaround:** we already chose the right tool (§0.18c) — a **semantic op-CRDT (CmRDT) carrying ERP
  intent**, with a **designated-owner node at document-handoff seams** (§18.7). That *is* the
  "server-authoritative sequencing for invariants" the literature prescribes. We do **not** put money-
  touching invariants under generic LWW (which silently loses an allocation). So the #1 CRDT weakness is
  sidestepped by design, not patched.

---

## Cross-cutting weaknesses (RxDB's "downsides" list) — ours vs theirs

| Industry weakness (RxDB) | Their exposure | Our angle |
|---|---|---|
| **Initial full-dataset download** | must replicate whole dataset to client | **Already solved** — split-DB streaming (positions/metadata/geometry tiers; `initbubble.json` 2KB) → lazy fetch, never download all |
| **Non-persistent storage** (Safari evicts after 7 days) | universal browser gap | Mitigate same way: `navigator.storage.persist()` + the signed op-log spilled to **any durable replica** — cloud *or* the user's own email/social (`DistributedERP.md §5.2b`). **The source of truth is the signed log itself, not a host** — determinism replays any replica to identical state, so the local copy is disposable (W-PERSIST). Shared gap, no magic |
| **Conflict resolution complexity** | hand-rolled per collection | Semantic op-CRDT + owner node (§18.7) — concentrated at few seams, not per-collection |
| **Eventual consistency unsafe for finance** | "risk in banking/financial apps" | money-touching invariants are **serialised at the one total-order / CAS seam** (`DistributedERP.md §5-6`) + enforced by the deterministic kernel on replay — **not** routed through a re-running server; single-user offline is safe (one writer). We do **not** pretend offline multi-writer finance is safe |
| **Schema migration to N offline clients** | "weeks-long unpredictable windows" | Partial lever: compiled-AD **manifest** (recompile UI structure) + **forward-only rules / frozen-effects** replay (§0.16). **Shared hard problem — honest about it** |
| **No SQL joins / relational limits** | document-CRDT systems can't join | **We are stronger here** — sql.js is real SQLite: full joins + FK (the AD/BOM spine). Native relational, not a document store |

---

## Summary — where we genuinely win, and where we just share the pain

**Real workarounds (our architecture removes their tax):**
1. **Deterministic semantic verbs → one kernel both sides** — kills Replicache's overwrite-on-rebase,
   PowerSync's double-implementation, and ElectricSQL's split read/write model in one stroke.
2. **Symmetric op-log** — reads and writes are the same ops; no separate write API.
3. **Semantic op-CRDT + owner node** — enforces business invariants CRDTs structurally cannot.
4. **Native SQL + relational** — joins/FK that document-CRDT systems lack.
5. **Split streaming** — instant first paint vs full-dataset download.

**Shared weaknesses — no magic, be honest (these become the §0.20 phase work):**
- Browser-storage eviction → `persist()` + cloud-of-truth (W-PERSIST).
- Cross-client schema migration → compiled-AD manifest + forward-only rules (partial).
- Tamper-evidence is *nobody's* default → our distinctive add: hash-chain/sign the log (W-CHAIN/W-SIGN).
- Secured multi-user **needs a trust anchor** — every system above has a server; ours is the async
  **server domain** (§0.20), reached only when single-user/offline no longer suffices.

**Sources:** [RxDB — downsides of offline-first](https://rxdb.info/downsides-of-offline-first.html) ·
[Replicache — how it works](https://doc.replicache.dev/concepts/how-it-works) ·
[ElectricSQL — local-first with your existing API](https://electric.ax/blog/2024/11/21/local-first-with-your-existing-api) ·
[PowerSync vs ElectricSQL](https://powersync.com/blog/electricsql-vs-powersync) ·
[LiveStore](https://livestore.dev/) ·
[CRDT survey (Weidner)](https://mattweidner.com/2023/09/26/crdt-survey-4.html) · [Automerge 3.0](https://automerge.org/blog/automerge-2/).
