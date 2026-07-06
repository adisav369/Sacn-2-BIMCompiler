# ⚠ DO NOT REMOVE — Scope guard
# Scope: a SANDBOX, headless §-witness POC that proves the three "showstopper" workarounds written into
#        docs/HolyGrail.md §"The hard parts, worked through" are real and deterministic — BEFORE the CRUD
#        roadmap reaches them (thinking ahead of the map). It is `scripts/poc_showstopper.js` ONLY, run in
#        Node, logging to build/erp/poc_showstopper.log. It REUSES the proven primitives; it invents no crypto.
# NON-NEGOTIABLE: Spec-first; witness-led (each test NAMES the issue it proves/disproves); §-log first; the
#        Log Mandate (save every run to the log, READ the log before any conclusion — exit code is not evidence);
#        deterministic (ids/timestamps/balances are recorded INPUTS — NO Date.now()/Math.random(), replay-exact).
# HANDS-OFF: do NOT touch the other session's live CRUD files — build/erp/crud_overlay.js, build/erp/crud_ops.json,
#        build/erp/glassbowl.html, build/erp/help_overlay.js. This POC only READS their op-SHAPE to stay aligned.
#        NO deploy. NO mkdocs. This is a witness, not a feature.
# Read first: docs/HolyGrail.md §"The hard parts, worked through" (the assumptions under test) · docs/DistributedERP.md
#        §0 (the four truths) / §1–§6 (90-10, physics partitions, the one CAS op-class, the dumb facilitator) /
#        §19.6 · docs/ERP.md §18.8 (the document-event op-GROUP is the atomic unit), §0.21 (identity-as-input) ·
#        scripts/poc_chain.js (chainOne/verifyChain/canonical/sha256 — REUSE) · scripts/poc_distributed.js
#        (totalOrder/replay/projectionHash + owner-gate + CAS — REUSE) · scripts/poc_sign.js (deterministic signer
#        — REUSE for the checkpoint signature) · scripts/erp_kernel.js (the real apply/op-group) · build/erp/
#        crud_overlay.js CORE.buildOp (the op SHAPE the editing layer emits: CRUD_CREATE/UPDATE/DELETE + DOC_ACTION).

---

# Showstopper Workarounds — Sandbox POC (checkpoint · atomicity · OLTP)

## Why now (think ahead of the map)
The CRUD session has reached **E2 (dry-run) + Process/DocAction**, mounted in tandem with Help (`§CRUD-PROC
pass=27`). Its roadmap is E3 (signed write) → E4 (owner-gate/CAS). The HolyGrail "hard parts" section just
committed three *assumptions* one rung PAST E4 — **log compaction via the period-close checkpoint (balance b/f),
atomicity as the op-group, and OLTP as physics-partition + one CAS.** This POC de-risks those assumptions now,
on the SAME op shapes the CRUD layer emits, so when the write-loop arrives the checkpoint rung is already proven.
It is the cheapest place to be wrong: a Node witness, no UI, no deploy.

## The three assumptions under test (each a HolyGrail claim → a witness)
1. **Atomicity is structural** — a document-event is one **op-group** (`ERP.md §18.8`); it folds in WHOLE or NONE;
   a torn/dropped op in the group fails the group hash → the group is rejected, projection unchanged. No partial state.
2. **Compaction = balance brought forward** — at period close a **signed checkpoint** (closing balances +
   chain-head fingerprint) becomes a NEW GENESIS; the closed period's ops are ARCHIVED (cold), not deleted; the
   live chain verifies only back to the checkpoint; re-folding the archive MUST equal the signed balances to the
   cent, and tampering an archived op is still caught. *(The hash-chain showstopper, resolved as accounting.)*
2b. **The optional reinforcements** — a Merkle root keeps per-op membership-provable in 32 bytes; a period RE-OPEN
   is a supersede op that re-chains (the iDempiere period-reopen wrinkle).
3. **OLTP needs no lock manager** — two writers on DISJOINT aggregates (physics-partitioned) both apply and union
   trivially; the ONE genuinely shared thing (last unit / entitlement) is the single **CAS** op-class (first-in-
   order wins); and these invariants HOLD ACROSS the checkpoint boundary (an entitlement claimed pre-close stays
   claimed in the opening state, carried by the checkpoint — not re-derivable into a double-claim).

## Reuse, do not reinvent (non-invent the crypto AND the kernel)
- **Chain + tamper-evidence:** `poc_chain.js` `canonical()/sha256()/chainOne()/verifyChain()` — import or copy verbatim.
- **Ordered replay + projection hash + owner-gate + CAS:** `poc_distributed.js` `totalOrder()/replay()/projectionHash()`
  — extend `replay()` with the kernel verbs you need (`SELL` backflush, `COMPLETE_ORDER` fan-out), do NOT fork a parallel order/merge.
- **Signature:** `poc_sign.js`'s deterministic signer for the checkpoint (sign the canonical of {balances, head}).
- **Op SHAPE:** mirror `crud_overlay.js` CORE.buildOp — ops carry `{ op_type, id (edge-minted uuid, INPUT), parameters,
  ts (INPUT) }`; a DOC_ACTION/COMPLETE_ORDER expands to an op-GROUP (the fan-out). Balances are FOLDED from the ops,
  never asserted (extract-only).

## Witnesses (headless §-log first; the ONLY evidence — read the log, never the exit code)
Each line is the witness for its claim; a missing or red line = not proven.

**S2 — Atomicity (the op-group):**
- `§SHOW-ATOMIC group=COMPLETE_ORDER#101 ops=[SHIP,INVOICE] applied=whole projDelta=all` — the group applies as a unit.
- `§SHOW-ATOMIC torn group=#101 droppedOp=INVOICE groupHash=FAIL rejected=Y projUnchanged=Y` — drop/alter one op in
  the group → group hash mismatch → whole group rejected, projection byte-identical to pre-attempt (no half-state).

**S3 — Compaction = balance b/f (the headline):**
- `§SHOW-CKPT period=1 ops=N head=<h12>… balances=<canon> signed=Y` — close P1: fold balances, take chain head, SIGN.
- `§SHOW-CKPT compact archived=N liveGenesis=<ckptHash12> liveChainLen=M (M≪N) verifyLiveToCkpt=ok` — P2 chains off the
  checkpoint; `verifyChain` runs only over the live (post-ckpt) ops; live length ≪ full length.
- `§SHOW-CKPT audit refoldArchived balances=<canon> == signedCkpt agree=Y` — re-fold the archived P1 ops; MUST equal
  the signed checkpoint balances to the cent.
- `§SHOW-CKPT tamper archivedOp=k refold≠signedCkpt detected=Y` — alter one archived op → refold diverges from the
  signed balances → tampering caught even though that op is no longer in the live chain.
- `§SHOW-CKPT merkle root=<r12>… membership op=<x> proofLen=L verified=Y` *(optional 2b)* — Merkle membership proof.
- `§SHOW-CKPT reopen period=1 supersede=<op> rechain=ok newHead=<h12>…` *(optional 2b)* — period re-open as a supersede op.

**S4 — OLTP (physics + one CAS, across the checkpoint):**
- `§SHOW-OLTP disjoint writers=[tillA,tillB] aggregates=disjoint bothApplied=Y unionHash=stable` — disjoint writers
  union with no contention (reuse the merge/replay; assert identical projection hash on both replicas).
- `§SHOW-OLTP cas shared=lastUnit first=tillA wins=Y second=tillB rejected=Y` — the one shared case → CAS set-if-unset.
- `§SHOW-OLTP cas-across-ckpt entitlement claimed@P1 carried=Y doubleClaim@P2 rejected=Y` — the claim survives the
  checkpoint in the opening state; a P2 re-claim is rejected against the carried-forward state (no double-spend via compaction).

**S5 — Determinism (the spine):**
- `§SHOW-DET rebuild ckptHashA==ckptHashB projHashA==projHashB agree=Y` — rebuild the WHOLE scenario in a fresh
  run → byte-identical checkpoint hash and post-compaction projection hash (holder-irrelevant, `§7`).

## Build order (each step names its witness; nothing here deploys)
- **S1 — Fixture.** A two-period op-log built from CRUD-shaped ops: P1 = `CREATE C_Order#101` → `COMPLETE_ORDER#101`
  (op-group: SHIP+INVOICE) → `SELL`×k (backflush) → `CLAIM entitlement` → close; P2 = more SELL/ALLOCATE off the
  checkpoint. Ids/ts/amounts are hard-coded INPUTS. Reuse `poc_chain.commit` for chaining.
- **S2 — Atomicity** witness (whole / torn group).
- **S3 — Checkpoint/compaction** witness (close+sign → compact → audit-refold → tamper-detect; +optional merkle/reopen).
- **S4 — OLTP** witness (disjoint + CAS + CAS-across-checkpoint), composing `poc_distributed` guards.
- **S5 — Determinism** witness (rebuild → identical hashes).
- **Run:** `node scripts/poc_showstopper.js 2>&1 | tee build/erp/poc_showstopper.log` → READ the log → write the
  `# DONE` appendix: every HolyGrail claim ↔ its `§SHOW-…` line. No log line = not done; flag it.

## Acceptance
- All `§SHOW-*` green; `§SHOW-DET agree=Y`; the log self-proves each of the three assumptions.
- **The load-bearing one is S3** — if re-folding the archived period does NOT reconcile to the signed checkpoint to
  the cent, the *balance-b/f compaction assumption is wounded* and HolyGrail must be corrected. That is the falsifier;
  run it honestly.

## Guardrails
- Reuse `poc_chain` / `poc_distributed` / `poc_sign` primitives; if a needed verb is missing, ADD it to the POC's
  local `replay`, NOT to the live kernel (kernel changes are a separate, spec'd task).
- Non-invent: every balance is a fold over the ops; every id/ts/signature input is a fixture, never generated.
- HANDS-OFF the live CRUD/glassbowl files; this POC is standalone and additive.
- Read the log after the run; cite `file:line` / the HolyGrail § before any claim of "proven".

## Status
- SPEC (this file), 2026-06-01. Author of the assumptions: docs/HolyGrail.md §"The hard parts, worked through".
- Coder session executes S1–S5, produces `build/erp/poc_showstopper.log` + the `# DONE` ledger. No deploy.

---

# DONE — 2026-06-01 · `scripts/poc_showstopper.js` · `build/erp/poc_showstopper.log` · `§SHOW PASS` exit 0

Each HolyGrail §"The hard parts, worked through" claim ↔ the `§SHOW-…` log line that proves it. The op shape
is the REAL one (`require('../build/erp/crud_overlay.js')` CORE.buildOp → CRUD_CREATE/DOC_ACTION op-groups);
crypto is reused verbatim from poc_chain (chain) + poc_sign (ECDSA checkpoint); balances in integer cents.

**Claim 2 — Atomicity = the document-event op-group (HolyGrail §"2. Atomicity"; ERP.md §18.8):**
- `§SHOW-ATOMIC group=COMPLETE_ORDER#p1-e1 … applied=whole … balSum=0.00` — the COMPLETE_ORDER op-group
  (DOC_ACTION+SHIP+INVOICE+Dr-AR+Cr-Rev) folds in WHOLE; books balance.
- `§SHOW-ATOMIC torn group=p1-e1 … groupHash=FAIL rejected=Y proj==asIfNeverAppended=Y balSum=0.00` — one
  forged op → the group fails its own hash → the WHOLE group is rejected, ledger identical to never-appended
  (all-or-NONE, no half-state).
- `§SHOW-ATOMIC contrast naivePerOp torn group → balSum=99.99 (≠0 ⇒ half-state)` — proves WHY atomicity is
  load-bearing: a per-op apply would half-post the journal and unbalance the books.

**Claim 1 — Compaction = period-close signed checkpoint = balance b/f (HolyGrail §"1. Compacting…"):**
- `§SHOW-CKPT period=1 … balances={"AR":7500,"Cash":35000,"Revenue":-42500} signed=Y` — close P1: fold the
  closing balances, take the chain head, sign with the controller key (verifies; books balance Σ=0).
- `§SHOW-CKPT compact … liveGenesis=3c6720b1… liveChainLen=4 (≪ 26) verifyLiveToCkpt=ok` — the closed period
  is archived; the live tab = checkpoint + open period only; the live chain verifies off the checkpoint head.
- `§SHOW-CKPT b/f … compactClose=… == fullClose=… lossless=Y maxDiff=0c` — fold(checkpoint + P2) equals
  fold(full P1+P2 from genesis) to the cent: compaction is lossless, balance brought forward.
- `§SHOW-CKPT audit refoldArchived … agree=Y maxDiff=0c head==signed=Y` — **THE FALSIFIER (load-bearing):**
  re-folding the cold archive reconciles to the signed checkpoint TO THE CENT, and the recomputed chain head
  matches the signed head. **Balance-b/f HOLDS — HolyGrail §1 is NOT wounded.**
- `§SHOW-CKPT tamper archivedOp=p1-e3-jAR chainBreaksAt=p1-e3-jAR refold≠signedCkpt=Y detected=Y` — forging
  one archived op is caught two ways at once: the chain breaks at exactly that op, AND the refold diverges
  from the signed balances ("fold to the signed balance, to the cent, or fraud is proven").

**Claim 3 — OLTP = physics partitions writers + one CAS, across the checkpoint (HolyGrail §"3. OLTP"):**
- `§SHOW-OLTP disjoint writers=[tillA,tillB] … noPkClash=Y … unionHash=stable` — two physics-partitioned
  writers union with no contention; both replay orders yield the identical projection hash (no lock manager).
- `§SHOW-OLTP cas shared=LASTUNIT first=tillA wins=Y second=tillB rejected=Y` — the one genuinely shared
  thing is a single CAS op-class: first in total order wins, the loser is rejected.
- `§SHOW-OLTP cas-across-ckpt claimed@P1=tillA carried=Y doubleClaim@P2(tillB) rejected=Y verdict(full)==verdict(compact)=Y`
  — the claim is carried in the checkpoint's opening state; a next-period re-claim is rejected, and the CAS
  verdict is identical whether you replay the full log or the compacted (checkpoint + open) log. **Isolation
  holds across the checkpoint boundary.**

**Spine — Determinism (DistributedERP.md §7):**
- `§SHOW-DET rebuild ckptHashA==ckptHashB projHashA==projHashB agree=Y` — rebuilding the whole scenario yields
  byte-identical checkpoint and post-compaction projection hashes (holder-irrelevant).

**Verdict:** all three "hard parts" hold on the real op shape; the load-bearing S3 reconciles to the cent.
HolyGrail §"The hard parts, worked through" stands as written. Next in the family (each its own session,
gated on its oracle): ODOO_FOLD_POC → SAP_FOLD_POC → EMAIL_DR_POC.
