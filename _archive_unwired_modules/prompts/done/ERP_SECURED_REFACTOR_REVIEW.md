# ⚠ DO NOT REMOVE — Scope guard
# Scope: ERP OOTB — REVIEW + PLAN only (no implementation this prompt).
#        Assess how the secured/distributed thinking impacts/refactors the PRESENT PWA ERP.
#        Theory: docs/DistributedERP.md + docs/ERP.md §0.20 + docs/LocalFirstPriorArt.md
# Read the log after every run. Exit code is not evidence.
# Spec-first: produce a written refactor spec; implement nothing until a § task is approved.
# Output a # DONE appendix — every claim cited to a doc § or a code file:line.

---

# ERP Secured/Distributed — Impact Review of the Present PWA ERP

## Why this prompt exists

A 2026-05-31 design dialogue (this session) worked the *secured / durable / multi-user* axis end to end
and produced a tested architecture. Before any code moves, a fresh session must **read that thinking, hold
it against the PWA ERP we actually have today, and write a refactor plan** — what changes, what already
fits, what is deferred, and the honest residuals. **Do not implement in this prompt.** Review → spec → stop.

## Step 1 — Read the new thinking (in this order)

1. `docs/DistributedERP.md` — the tested contention map (THE backbone). Note especially:
   - Physics partitions the data: branch → van → box-in-hand (scan = physical single-writer).
   - The guard set (G-IDENTITY / EXCLUSIVE-DISPATCH / SINGLE-WRITER / RESERVATION / ORDERED-HANDOFF /
     LEASE-EXPIRY / READ-ANYWHERE-WRITE-OWNER) — *avoid by ownership primary, total-order fallback*.
   - The one real-time op-class = customer-global entitlements; lifecycle issue=URL/QR(signed) →
     carry=phone signed op-log → persist/recover=user's own email/social (§4.2b) → claim=online CAS /
     offline value-tiered → reconcile=ledger.
   - Determinism is load-bearing (dumb async post office; nondeterministic values captured as op inputs).
   - Capstone: accounting (double-entry) IS the reconciliation engine.
2. `docs/ERP.md §0.20` — the phase frame + witnesses W-CHAIN / W-SIGN / W-PERSIST / W-OWNER; UI POC frozen.
3. `docs/LocalFirstPriorArt.md` — how Replicache/ElectricSQL/PowerSync/LiveStore/CRDTs do it + our
   deterministic-verbs-both-sides lever. (Context, not a to-do.)

## Step 2 — Inventory the PRESENT PWA ERP (what exists today)

Locate and read (ERP code lives in `bim-ootb/viewer/`, NOT deploy/dev):
- `erp.html` + `ad_*.js` (renderer), `initbubble.json`, `ad_seed.db`.
- The kernel: `kernel_ops` usage, `erp_kernel.js` / `erp_engine.js` / `erp_runtime.js` (whatever the
  current names are — verify, don't assume). The compiled AD manifest path (`docs/ERP.md §13`).
- How ops are committed/replayed today; whether replay-hash == live-hash is exercised (§0.16/§0.19).
Produce a short **as-is map**: where state lives, who writes it, how it persists, what (if anything) is
signed/ordered/recovered today.

## Step 3 — Gap & impact analysis (the core deliverable)

For each item below, classify: **already fits / small change / real refactor / deferred**, with a code
file:line or doc § citation. Be honest — no overclaiming (carry the whole session's discipline).

1. **Identity** — is the PK a UUID with FKs referencing it (G-IDENTITY), or still SeqNo/PK? What breaks on
   merge today? (PackIn-clash lineage, `docs/ERP.md` history.)
2. **Single-writer / ownership** — does the model express owner/dispatch, or assume one global writer?
3. **Op-log integrity** — is `kernel_ops` hash-chained/signed (W-CHAIN/W-SIGN) or plain? Smallest step to
   per-op `prev_hash` + `verifyChain()`.
4. **Persistence/recovery** — does anything ride the user's email/social as the durable signed log (§4.2b),
   or only IndexedDB (evictable)? `navigator.storage.persist()` status.
5. **Customer-entitlement op-class** — is there any loyalty/credit/gift surface? If built later, where does
   URL-issue + phone-carry + CAS/reconcile attach?
6. **Two-domain split** — is there any seam between an instant UI domain and an (async) authority, or is it
   monolithic single-user today?

## Step 4 — Write the refactor spec (output)

- A prioritised, witness-led task list (reuse W-CHAIN/W-SIGN/W-PERSIST/W-OWNER naming; add new W-* as
  needed). Each task: the issue it proves/disproves + acceptance witness (a §-log line or replay-hash check).
- Mark what stays **frozen** (UI POC) and what is **out of scope** (the BIM viewer, deploy/dev).
- Call out the honest residuals carried from `DistributedERP.md §8` (lease-expiry, in-transit, barcode≠crypto,
  key custody, multi-till) and which the PWA must guard vs defer.
- **Do NOT touch `CLAUDE.md`** (the proposed 2nd mantra awaits the user's chosen wording — see
  `DistributedERP.md §9`).

## Guardrails

- Spec-first: this prompt produces a *plan*, not edits. Stop after Step 4 and present it.
- Never invent: every gap/claim cites a doc § or a code file:line. If you can't cite it, you didn't verify it.
- Watchdog: end with a `# DONE` appendix — each claim has its citation; no citation = flag it, don't assert.
