# ⚠ DO NOT REMOVE — Scope guard
# Lane: HARDEN the serverless-enterprise claims (durability / distributed / DR-TCO / schema-evolution) by
#       turning each remaining honest caveat into a WITNESS bound to the real core (build/erp/*.js), AND
#       continue the deferred iDempiere ERP-LOGIC migration coverage. Read the log after every run (exit
#       code is NOT evidence). EXTRACT/COMPILE ONLY — every claim traces to an AD row, a Java class, or a
#       §-tagged kernel run. Witness-led, deterministic (recorded ts/ids, integer cents, no Date.now in folds).
# Read first: docs/MigrateComparisonPaper.md (now carries the DR/TCO §), docs/HolyGrail.md "hard parts",
#       docs/DistributedERP.md §6/§9, memory [[project_erp_dr_tco]] + [[project_erp_sync_fsm]].

---

# Serverless-enterprise hardening — continue to zero

## Where this stands (2026-06-08, session close)
The "outsider/skeptic" durability thread was answered with code, all on the REAL kernel (build/erp/kernel_ops.js
v8 commit/seal/verify/replay + erp_period_close.foldBalances + erp_sequencer + erp_sync_fsm), all 🟢:
- **W-BLACKOUT** (`scripts/poc_blackout_resume.js`) — 50 branches, total blackout + relay drive lost → rebuild
  from edges → identical signed tip, books maxDiff=0c; `§ORDER-HONEST` corrects the §6/§9-A total-order overclaim.
- **W-TCO-HARDENED** (`scripts/poc_tco_skeptic.js`) — apple-to-apple DR/TCO, 4 skeptic objections answered
  (244× weekly-incr / 30× floor, additive restore, billable inventory, double-sale bounded).
- **W-PROVISIONAL** (`scripts/poc_provisional_clear.js`) — scan-clears-the-sale (bank-AR model): double-CLAIM →
  one cleared + one reversed→receivable; Sales = one unit, ΣDR==ΣCR. Upgrades the TCO double-sale caveat to "demonstrated".
- **W-SCHEMA-VERSION** (`scripts/poc_schema_version.js`) — evolve rules without restating history: effects-frozen,
  version-pin (+ restate-falsifier), closed-period frozen, signed UPCAST op. Answers DistributedERP §9-E.
- MigrateComparisonPaper updated (cost box + DR/TCO § + GAPS #7) and **DEPLOYED** (gh-pages, mkdocs).

## OUTSTANDING (work top-to-bottom)

### §H-1 — quorum-CAS witness (the ONE trade still only *bounded*, not minimised)  ✅ DONE (W-QUORUM-CAS)
Put a measured NUMBER on the double-sale/entitlement window: commit a contended-class decision to ≥2 owns-nothing
replicas BEFORE ack → window = quorum-RTT. Bind to erp_relay_server / erp_replica_client. Witness §QUORUM-CAS.
> **`scripts/poc_quorum_cas.js` 🟢 ALL PASS** (log `build/erp/poc_quorum_cas.log`). N=5 REAL `erp_relay_server`
> replicas, quorum k=3. Window = **quorum-RTT = 18ms** (3rd-nearest; measured 20.0ms on the real relay),
> independent of N and the 240ms tail (`§QUORUM-RTT-MEASURED`/`§WINDOW-NUMBER`). `§INTERSECTION-NO-SPLIT`:
> two disjoint local quorums intersect at r2 → **exactly ONE owner, oversell=0** even inside the window.
> `§FALSIFIER` (k=1) → both ack → oversell=1 (quorum is load-bearing). `§LEDGER` foldBalances → one unit, ΣDR==ΣCR.
> The W-TCO "bounded, not minimised" trade is now MINIMISED to a measured, fleet-independent quorum-RTT.

### §H-2 — doctrine docs still carry the overclaim W-BLACKOUT corrected  ✅ DONE (local edits; deploy-pending)
`docs/DistributedERP.md` §6/§9-A ("total order reconstructible from signed logs") and `docs/HolyGrail.md`
"back up the recipe 1TB→500MB" are now INCONSISTENT with the deployed paper's `§ORDER-HONEST`. Tighten both to:
disjoint folds commute (net reproducible) BUT cross-branch CAS order is not reconstructible — the named sliver.
> **DONE locally** (NOT deployed — honouring the doc-deploy gate): DistributedERP §6 + the failure-table row now
> read "net books reproducible (folds commute, maxDiff=0c) BUT cross-branch CAS order not reconstructible —
> `§CAS-SLIVER`, minimised live to a quorum-RTT window (`poc_quorum_cas.js`)". HolyGrail backup §347 gains the
> two honest caveats (net-vs-order reconstructibility; ≈500MB is the *full-replica* figure, edge ≈13MB). Deploy
> with the next mkdocs doc batch.

### §H-3 — O.c shorthand byte-measure (also seeds the coverage-audit §B)  ✅ DONE (W-OC-BYTES)
Measure real bytes/op: full payload vs `O.c`(macro-opcode)+inputs, with the compression ladder (don't-store-hashes
=fold, binary, per-period Merkle sig). The opcode table IS the de-interleaved DocAction transition table → feeds §M-1.
> **`scripts/poc_oc_bytes.js` 🟢 ALL PASS** (log `build/erp/poc_oc_bytes.log`). Ladder (B/op): L0 full **548** →
> L1 O.c opcode+inputs 335.7 → L2 drop-hashes 191.7 → L3 binary 87.0 → L4 per-period-sig **23.0** = **23.8× smaller**.
> `§LOSSLESS`: O.c-decoded re-folds to identical books (maxDiff=0c) via the SHIPPED foldBalances. `§FALSIFIER`:
> flip one opcode → different books → the opcode carries the effect. `§OPCODE-TABLE`: the opcode set IS the
> de-interleaved DocAction recipe → one artifact shared with §M-1.

## THE OTHER LANE — iDempiere ERP-LOGIC migration (the session's ORIGINAL prompt, NOT done)

The whole-codebase ERP-logic migration question maps to EXISTING specs/prompts (none invented):
- **`prompts/ERP_RULES_AND_PROCESSES.md`** — THE coverage audit (have we covered all the bases?). §A code
  (DocAction/Doc_*/SvrProcess/callouts/validators/workflow) + §B AD-as-data (AD_Process/Workflow/Rule/Val_Rule/
  C_DocType FSM…). 3-leg method (count in build/erp/ad_full.db → Java class → engine fold → ✅/🟡/⛔ matrix).
  **STILL UNEXECUTED — this is the honest open deliverable.**
- **`docs/HolyGrail.md`** "Abstracting the DocAction corpus" — the de-interleaving model (transition table +
  guards + shared verbs + per-doc recipe; reversal-family → one inverse-op handler). The theory for the audit's §A.
- **`docs/MigrateComparisonPaper.md`** §"Realistic conversion estimate" — the irreducible buckets (~200K LOC:
  M*/Doc_*/acct/wf/process/callouts/validators) ARE the audit surface, now with measured LOC.
- **`prompts/ERP_RAW_MIGRATION.md`** (raw PG→SQLite) · **`prompts/ERP_CALLOUT_PORT.md`** (callouts) ·
  **`docs/DocAction_SRS.md`** (the DocAction spec) · **`prompts/MIGRATION_CAMPAIGN_RESUME.md`** +
  **`prompts/ODOO_FOLD_*` / `prompts/SAP_FOLD_POC.md`** (the foreign-ERP fold campaign).

### §M-1 — START the coverage audit (ERP_RULES_AND_PROCESSES.md)  ✅ DONE (docs/ERP_COVERAGE_MATRIX.md)
Query `build/erp/ad_full.db` (~925 tables) for each §B surface count; map each §A code surface to its build/erp/*.js
fold (or "absent"); deliver the COVERAGE MATRIX + ranked GAP list. This is the real answer to "covered all bases?".
> **DELIVERED — `docs/ERP_COVERAGE_MATRIX.md`** (5 parallel audit agents, every count from a real `sqlite3` query
> against ad_full.db / `find|wc -l` against the idempiere checkout). Headline: **0 ✅ / 12 🟡 / 28 ⛔** across §A+§B
> (40 surfaces). The honest "no, not all bases" answer: the engine folds a thin STATIC slice (the `CO` DocAction
> transition, the double-entry posting fold, a fixed receipt/TB/P&L set) for ~5–7 hand-authored demo tables in
> `crud_ops.json`; the behavioural surface (SvrProcess 54k LOC / workflow 7k / callouts 10k / validators / the
> **~3,000-row logic-expression evaluator** / the **~4,200-row security layer**) has NO engine home. Ranked GAP
> list inside (each with the smallest `§`-log witness). Feeds MigrateComparisonPaper §estimate with measured AD counts.

## ENTERPRISE HARDENING — Phase 2 (spec'd 2026-06-08, next witnesses)

These are the enterprise challenges the current substrate **names but does not witness** — surfaced by an audit
of the server-side roadmap vs. existing POCs (none invented; each maps to a PROSE/ABSENT residual in
`docs/DistributedERP.md` §9 or an acknowledged gap). Spec-first, witness-led, bound to the REAL core
(`build/erp/kernel_ops.js` + `erp_snapshot_sign.js` + `erp_period_close.foldBalances` + the relay/sequencer),
deterministic (recorded ts, integer cents). Same shape as §H-1/§H-3: each `§` names the issue it settles + a
load-bearing `§FALSIFIER`. **Tier-1 first (#H-4, #H-5) — these are where an expert reviewer pushes hardest and
where one file flips "we hand-wave it" → "proven."** When one lands, update the `docs/DistributedERP.md` §9
residual row from PROSE → witnessed (deploy-doc batch).

### §H-4 — Right-to-erasure on an immutable log (crypto-shredding) — W-ERASE  ✅ DONE (2026-06-09, 🟢 ALL PASS)
> `scripts/poc_erase.js` + `build/erp/erp_pii_vault.js` (per-subject AES-GCM envelope), bound to kernel_ops +
> foldBalances. §ENVELOPE/§ERASE/§BOOKS-INTACT(maxDiff=0c)/§AUDIT-RESIDUAL/§FALSIFIER all green; drop the key →
> chain still verifies, tip identical, PII irrecoverable. Pushed on `feat/erp-substrate-phase012` (`6673c09f`).
> §9 residual flip (new erasure/compliance row) deferred to the deploy-doc batch.
**Issue:** GDPR/CCPA "delete this person's PII" vs. append-only + sign-everything — you cannot unlink an op
without breaking the hash chain. The sharpest contradiction in the model; today ABSENT.
**Model:** PII rides in a **per-data-subject encrypted envelope** inside `op.parameters` (the ciphertext is the
recorded input, §7); non-PII (account/cents) stays in clear and folds normally. Erasure = destroy the subject
key. The op + `prev_hash`/`op_hash` + signature are over ciphertext+metadata → unchanged → **chain still
verifies, tip unchanged**; plaintext is unrecoverable; the **ledger is untouched** (it folds account/cents, not PII).
Bind: kernel_ops, a subject-keyed vault (AES-GCM via webcrypto), foldBalances.
- `§ENVELOPE` — PII = per-subject ciphertext in the op; non-PII clear → folds normally.
- `§ERASE` — drop the subject key → decrypt throws (irrecoverable); `verifyChain.ok` still true; tip identical.
- `§BOOKS-INTACT` — foldBalances byte-identical before/after erasure (maxDiff=0c).
- `§AUDIT-RESIDUAL` — the op + hash remain (an event provably happened) but the person is unidentifiable →
  "tombstone the identity, keep the accounting fact" (the honest GDPR posture, not faux-deletion).
- `§FALSIFIER` — PII stored in cleartext → erasure is impossible without rewriting the chain (the bug shredding fixes).

### §H-5 — Relay equivocation / Byzantine facilitator detection — W-EQUIVOCATION  ✅ DONE (2026-06-09, 🟢 ALL PASS)
> `scripts/poc_equivocation.js`, bound to kernel chain + erp_snapshot_sign (per-client keys). §TWO-VIEWS/
> §DIVERGENT-TIP/§DETECT/§ATTRIBUTABLE/§FALSIFIER green; gossiped signed tips detect+attribute relay equivocation,
> silent without gossip. Pushed (`6673c09f`). §9 residual flip (§6 + §9-E order-preserving assumption) deploy-doc-batched.
**Issue:** we proved the relay can't *forge* effects (signed), never that it can't *equivocate* — hand client A
order `[a,b]` and client B `[b,a]` over the same seq window → divergent folds = split-brain **caused by the
"dumb" sequencer we told everyone to trust.** The honest-but-dumb assumption is what a distributed-systems
skeptic attacks (DistributedERP §6/§9-E assume order-preserving by design).
**Model:** each client periodically **signs its observed period-tip** (seq-range → chain tip, via
erp_snapshot_sign) and gossips it (peer / the user's own channel). Two clients handed different orders over the
same range produce mismatched signed tips → equivocation **detected and attributable to the relay** (clients
sign what they *saw*; the mismatch can't be forged onto a client).
- `§TWO-VIEWS` — a relay hands A `[a,b]` and B `[b,a]` over one seq window.
- `§DIVERGENT-TIP` — A and B compute DIFFERENT chain tips for the same seq range.
- `§DETECT` — exchanging signed tips flags the mismatch (honest relay → identical tips; this → mismatch).
- `§ATTRIBUTABLE` — each signed tip proves what that client saw → the equivocation pins on the relay, unforgeable.
- `§FALSIFIER` — without tip-gossip the divergence is SILENT (both think they're canonical) → gossip is load-bearing.

### §H-6 — Key lifecycle: rotation / revoke / offboarding — W-ROTATE  ✅ DONE (2026-06-09, 🟢 ALL PASS)
> `scripts/poc_rotate.js` + `build/erp/erp_key_epochs.js` (epoch schedule derived from signed ROTATE/REVOKE ops),
> bound to kernel chain + erp_snapshot_sign. §ROTATE-OP/§HISTORY-VALID/§FUTURE-GATED/§REVOKE/§FALSIFIER green +
> deterministic tip; rotate/revoke without re-signing history. Pushed (`6673c09f`). §9-B residual flip deploy-doc-batched.
>
> **TIER-1 COMPLETE (§H-4/§H-5/§H-6 all 🟢, pushed).** NEXT = the Tier-2 backlog below (spec when picked up):
> §H-7 scheduled-jobs-without-a-server · §H-8 anti-backdating · §H-9 multi-currency/FX · §H-10 SoD/maker-checker
> (gated on workflow engine) · §H-11 live divergence detection. Scoped-OUT remain: intercompany + 500-user RBAC.
**Issue:** the whole trust model is "the user holds the keys," yet rotation/compromise/offboarding is only named
as "the one irreducible anchor" (DistributedERP §9-B) — never witnessed. Everything else rests on this.
**Model:** a signed `ROTATE` op (counter-signed by the **outgoing** key) installs a new key at seq S; a key-epoch
map (seq-range → valid pubkey) means history verifies under the key valid at its seq — **no re-signing the past.**
- `§ROTATE-OP` — ROTATE counter-signed by the outgoing key installs the new key at seq S.
- `§HISTORY-VALID` — ops `< S` still verify under the old key (past untouched).
- `§FUTURE-GATED` — ops `≥ S` signed by the old key REJECT; by the new key ACCEPT.
- `§REVOKE` — a REVOKE(key) op makes that key's **future** ops reject (compromise/offboarding); its **past** ops
  stay valid (it really did author them).
- `§FALSIFIER` — a single fixed key → compromise = forge forever, OR rotate = re-sign all history (the cost rotation removes).

### Tier-2 backlog (named now, spec when tier-1 lands)
- **§H-7 scheduled jobs without a server** — *who fires nightly depreciation/dunning/recurring-invoice/auto-reconcile?*
  Witness: the first edge online past the period boundary deterministically + **idempotently** folds the batch
  (N edges firing = one effect). Answers the most-exposed "no server" claim.
- **§H-8 anti-backdating / period-cutoff** — `ts` is an edge-recorded input the kernel only reads → backdating
  after close is today an accounting residual, not prevented. Witness: a signed period-lock rejects any op whose
  recorded date `<` the sealed cutoff.
- **§H-9 multi-currency / FX revaluation** — postings are single-currency cents; FX is named only as a
  nondeterministic edge-recorded verb. Witness: per-currency fold + period-end revaluation booking realized/unrealized G&L.
- **§H-10 SoD / maker-checker** — four-eyes on a contended posting; **gated on the workflow engine** (coverage-matrix ⛔).
- **§H-11 live divergence detection** — convergence is proven offline (replay-hash equality); a stuck/diverged
  replica is not detected in real time. Witness: periodic signed-tip heartbeat → an outlier tip alerts.
- **Scoped-OUT (decisions, not gaps):** intercompany/group consolidation + 500-user RBAC — explicitly out of
  scope per `docs/SpatialERP_POC.md` "honest assessment". Name as decisions; do not present as oversights.

## HOUSEKEEPING / loose ends
- Branch `feat/erp-substrate-phase012` is LOCAL (commits 1a989612 + the two new POCs). gh-pages is live but its
  source commit isn't on origin — decide whether to push the branch.
- All session POCs are node + the REAL kernel; none touched live deploy. Logs are .gitignored (regenerable).
