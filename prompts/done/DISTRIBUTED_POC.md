# ⚠ DO NOT REMOVE — Scope guard
# Scope: Distributed ERP — sandbox POCs that prove the §9 edge-catalog witnesses.
#        DETERMINISTIC, IN-PROCESS, NO REAL SERVER. Simulate "server conditions" with N
#        in-memory sql.js DBs (= N devices) in one Node process. Testing server conditions
#        WITHOUT a server is itself evidence the server is not load-bearing.
# Spec-first: each POC is pinned to one DistributedERP.md §9 row + the issue it proves/disproves.
# Read the log after every run (`| tee build/erp/<poc>.log`). Exit code is not evidence.
# Idiom: scripts/poc_*.js — `verdict(ok,label,detail)` with 🟢/🔴, §-tagged console.log, sql.js binding.
# Honour until every row below is 🟢 or explicitly DEFERRED.

---

# Distributed ERP — POC Plan

**Principle.** No real server. Each POC is a single Node process; "devices/branches" are separate
in-memory `sql.js` databases; partition/merge/total-order are modelled as plain data moved between them.
The proof rides the existing harness (`poc_kernel.js` / `poc_longtail.js` already show
`replay-hash == live-hash` on the sql.js browser binding). Reference: `docs/DistributedERP.md` §0 (truths),
§3 (normal multi-POS), §9 (the witnesses below), §10 (why this differs from the field).

**Determinism rule (load-bearing, §7).** No `Date.now()` / `Math.random()` inside a verb. Timestamps,
UUIDs, scanned codes are **edge-minted and recorded as op inputs**; the kernel only reads them. POCs pass
these in as fixture inputs so every run is byte-identical and replay-hashes compare.

## Witness → POC map (priority order; each names the issue it proves)

| # | Witness (§9 row) | POC file | Proves / disproves | Real-world? |
|---|---|---|---|---|
| 1 | **W-CHAIN** (§9-B tampered log) | `scripts/poc_chain.js` | per-op `prev_hash` chain; `verifyChain()` reports tamper **at exactly op N**; clean → `chain OK len=N`. NEW — nothing in the codebase chains today | in-process |
| 2 | **Merge / G-IDENTITY** (§9-E) | `scripts/poc_distributed.js` | 2 sql.js DBs, edge-minted UUID PKs, union logs → **no PK clash**, `replay-hash` equal both sides (merge = union+order+replay) | in-process |
| 3 | **W-OWNER + CAS** (§9-C/D) | extend `poc_distributed.js` | two peers allocate the same invoice → owner-gate drops the 2nd on replay (no money lost); CAS set-if-unset → first-wins | in-process |
| 4 | **W-SIGN** (§9-B) | `scripts/poc_sign.js` | op signed by key A fails verification under key B; signed chain verifies | in-process (Node/Web crypto) |
| 5 | **W-PERSIST + email-recovery** (§9-A) | `scripts/poc_persist.js` | export→wipe→import → `replay-hash == pre`; signed-snapshot→mailbox→restore by signed `seq` → equal | in-process |
| 6 | lease-expiry / value-tiering (§9-C/D) | `scripts/poc_policy.js` | ts-as-input: unexercised lease reclaimed; high-value blocks / low-value allows+reconciles | in-process |

## Deferred to operational validation (only AFTER the logic above is 🟢; §-log-first/Playwright-second)
- Real IndexedDB eviction behaviour (browser / Playwright).
- Real email send/receive round-trip (integration).
- Real network partition / latency timing.
- **Schema migration to N offline clients** — stays the honest hard one (§9-E); no POC makes it cheap.

## Acceptance
A row is 🟢 when its POC prints the witness §-line and `…PASS` with `fails=0`, and the log (not the exit
code) is read to confirm. On 🔴, fix the POC or the model, rerun, re-read the log — never assert from inline
output. When a witness POC passes, it doubles as the **spec for the production change** (e.g. `poc_chain.js`
defines the `prev_hash` column + `verifyChain()` to add to `bim-ootb/viewer/kernel_ops.js`).

# DONE
- (append per run: which row, the §-witness line, PASS/FAIL, log path)
- **#1 W-CHAIN — PASS** (`scripts/poc_chain.js`, log `build/erp/poc_chain.log`, exit 0, fails=0).
  Witness lines: `§CHAIN built len=5 tip=a3f120e4338c…` · `§CHAIN verify clean ok=true len=5` ·
  `§CHAIN tamper op=3 detected=true brokeAt=3 why=payload altered` · `§CHAIN localised tamper@5 brokeAt=5` ·
  `§CHAIN PASS`. Proves: clean→`chain OK len=N`; any altered op→detected at **exactly** that op; deterministic
  across rebuilds (same ops→same tip, holder-irrelevant). **Spec for production:** add `prev_hash`+`op_hash`
  columns + `verifyChain()` to `bim-ootb/viewer/kernel_ops.js` (browser port uses async `crypto.subtle`).
- **#2 Merge/G-IDENTITY + #3 W-OWNER/CAS — PASS** (`scripts/poc_distributed.js`, log
  `build/erp/poc_distributed.log`, exit 0, fails=0). Witness lines: `§DIST merge devices=2 ops=8` ·
  `§DIST replay hashA=8b36ee6b824b… hashB=8b36ee6b824b…` (holder-irrelevant) · `§DIST owner-gate
  INV.status=allocated rejected=1 why="non-owner (B≠A)"` · `§DIST CAS token.claimed_by=A losers=1` ·
  `§DIST PASS`. Proves (no server): edge-minted UUIDs union clash-free; both devices replay the merged
  log to the SAME projection hash; owner-gate (G-SINGLE-WRITER) and CAS reject the loser deterministically;
  numeric-seq PKs would collide (contrast). **Spec for production:** UUID PK in `kernel_ops` (retire
  natural-key `docKey`/`lineKey` in `scripts/erp_kernel.js`); owner-gate + CAS in the replay path.
  - **↳ LANDED (2026-06-01, the UUID-PK half) — `§IDENTITY PASS`** (`scripts/poc_identity.js`, log
    `build/erp/poc_identity.log`, exit 0, fails=0; spec `docs/ERP.md §0.21` D1–D4). The property is now
    wired into the REAL `scripts/erp_kernel.js` (not a model): `kernel_ops` PK `INTEGER AUTOINCREMENT`
    → edge-minted `op_uuid` TEXT; `docKey`/`lineKey` retired as the kernel's identity source, folded
    into a single `edgeMint` that runs ONCE at first apply and is recorded — the replay path re-reads
    `op.uuid` (`§IDENTITY no-recompute … edgeMintCalls=0`). Witness lines: `§IDENTITY merge devices=2
    ops=4 … 4/4 distinct` · `§IDENTITY replay hashA=c56782b1 hashB=c56782b1` (holder-irrelevant) ·
    `§IDENTITY no-recompute liveHash==replayHash edgeMintCalls=0 recordedDocGuid=DOC:M_InOut@from101`
    (source-derived guid byte-stable → §ORACLE-SUITE/§LONGTAIL unbroken) · `§IDENTITY newdoc
    stored==passed edgeMintCalls=0` (D4 New-doc seam honours a `crypto.randomUUID` minted at the edge).
    **No regression:** `§KERNEL PASS` · `§LONGTAIL … replay=EXACT` · `§ORACLE-SUITE 5/6 PASS` ·
    `§DIST PASS` all still green. **Still parked (the merge-completeness + #3 halves):** owner-gate +
    CAS in the replay path, and making `CREATE_LINE`'s parent a recorded `document_uuid` input (today
    it links via replay-state `currentDoc`, so the merge witness orders self-contained ops) — both are
    Phase-A #3 follow-ons, not blockers.
- **#4 W-SIGN — PASS** (`scripts/poc_sign.js`, log `build/erp/poc_sign.log`, exit 0, fails=0).
  Witness lines: `§SIGN issuer-signed … verify ok=true` · `§SIGN wrong-key … brokeAt=1 why=signature` ·
  `§SIGN forge(present-but-not-forge) … brokeAt=3 why=signature` · `§SIGN layering chain-stable=true
  sig-nondeterministic=true` · `§SIGN PASS`. Proves: ECDSA P-256 signature gates authenticity over the
  deterministic W-CHAIN hash — wrong key fails; a holder who edits + re-chains + re-signs with their own
  key is rejected at exactly the first forged op ("present but not forge", §5.2); the chain hash stays
  deterministic even though signatures vary (clean layering). **Spec for production:** sign each
  `kernel_ops` op's `op_hash` with an edge key via `crypto.subtle` (ECDSA P-256); `verifyChain()` also
  checks signature.
- **#5 W-PERSIST + email-recovery — PASS** (`scripts/poc_persist.js`, log `build/erp/poc_persist.log`,
  exit 0, fails=0). Witness lines: `§PERSIST export→wipe→import … pre==post:true` · `§PERSIST recover
  pickedSeq=3 … rec==tip:true` · single-snapshot suffices · `§PERSIST forged seq=4 admitted=false`.
  Proves: the local copy is disposable (real `db.export()`→wipe→import round-trips to the same hash); the
  inbox is the recoverable signed log — recovery picks the tip by signed `seq` (not arrival order), a single
  latest signed snapshot suffices (§5.2b), and a forged snapshot is rejected (falls back to latest valid).
  **Spec for production:** `navigator.storage.persist()` on first ERP load (`erp.html` doesn't today —
  only `scene.js` does); signed full-snapshot email emit + recovery picker.
- **#6 lease-expiry + value-tiering — PASS** (`scripts/poc_policy.js`, log `build/erp/poc_policy.log`,
  exit 0, fails=0; one red on first run = a wrong assertion constant in the POC — read the log, fixed
  expectation 6→7, model behaviour was correct). Witness lines: `§POLICY expire now=1400 ops=1 reclaimed=4
  pool=7` (ordered, deterministic from passed-in `now`) · `§POLICY tier offline-hi=BLOCK
  offline-lo=ALLOW(receivable) online=ALLOW(CAS)` · `§POLICY PASS`. Proves G-LEASE-EXPIRY (unexercised lease
  reclaimed as an ordered op) + §5.3 value-tiering (offline high→block, low→allow+receivable, online→CAS),
  residual → the ledger.

## STATUS — all 6 in-process witnesses 🟢 (the doctrine's assumptions hold serverless)
W-CHAIN · Merge/G-IDENTITY · W-OWNER/CAS · W-SIGN · W-PERSIST/email-recovery · lease-expiry/value-tiering.
Remaining = the explicitly DEFERRED operational checks (real eviction / real email / real network) + the
honest hard one (schema migration). None blocks; each in-process POC doubles as the spec for its production
change in `bim-ootb/viewer/kernel_ops.js` / `scripts/erp_kernel.js`.
