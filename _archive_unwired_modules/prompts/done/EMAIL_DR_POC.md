# ⚠ DO NOT REMOVE — Scope guard
# Scope: a SANDBOX, headless §-witness POC that stress-tests the "inbox as recoverable signed state-log" idea
#        (docs/DistributedERP.md §5.2b, Truth 3 "secure the fact, not the container") under a DISASTER MATRIX —
#        going PAST poc_persist.js (which proves the happy path). The headline target is the RESIDUAL REGRESS:
#        the inbox recovers the DATA, but the signing/decryption KEY lives on the lost device. This POC proves
#        the data-recovery is unconditional, and pins EXACTLY where key-recovery requires a trust anchor.
# NON-NEGOTIABLE: Spec-first; witness-led (each test NAMES the disaster it survives or the regress it exposes);
#        §-log first; Log Mandate (save run, READ log before conclusions); deterministic (keys/shares/seq/ts are
#        recorded INPUTS — NO Date.now()/Math.random(); the mailbox is a STATIC fixture, no live email).
# HONEST FRAME: this is a FALSIFIER. "Total loss without key recovery → recovery FAILS" is a REQUIRED witness,
#        not a bug to hide — it is the true floor. The POC's value is proving which key-recovery schemes close the
#        regress AND naming the trust each one adds (no scheme is zero-trust; that is the finding).
# Read first: docs/DistributedERP.md §0 (Truth 3 + the regress-termination keystone) / §5.2b (inbox-as-log, the
#        caveats: pick tip by signed seq/prev_hash, full snapshot per email, encrypt-to-user) / §6 (the dumb post
#        office as the send path) · scripts/poc_persist.js (REUSE: signHash/verifyHash, projectionHash, the signed-
#        snapshot recovery + shuffle + forged-rejected) · scripts/poc_chain.js (chain/verifyChain for key-rotation
#        continuity) · the corporate-node variant (company-controlled Workspace escrows the key).

---

# Email Disaster Recovery — Sandbox POC (the inbox-as-log, stress-tested; the key-regress, pinned)

## Why this POC (past the happy path)
`poc_persist.js` proves: wipe → restore from latest signed snapshot; shuffled mailbox → pick tip by signed seq;
forged snapshot → rejected. Good — but that all assumes **the key survives.** The genuinely open question
(flagged in the HolyGrail appraisal) is the residual regress: *the inbox durably recovers the facts; the KEY does
not live in the inbox.* This POC runs the full disaster matrix and answers, honestly: where does recovery hold
unconditionally, where does it need a trust anchor, and what trust does each anchor add? It composes with the
period checkpoint — the emailed snapshot IS the balance-b/f checkpoint, so this is also the checkpoint's durability.

## The two-layer claim under test
- **Layer A — DATA recovery (should be unconditional):** given the latest VALID signed snapshot in ANY reachable
  channel, the new device restores state, replay-hash == pre-disaster. The channel is an untrusted, swappable pipe.
- **Layer B — KEY recovery (the residual):** if the signing/decryption key is gone, Layer A's snapshots are
  unverifiable/undecryptable. Key recovery is NOT free — it requires a trust anchor. The POC tests three real
  anchors and names the trust each introduces.

## Disaster matrix — the witnesses (§-log first; the only evidence)

**Layer A — channel/data disasters (recovery holds WITH the key):**
- `§EMAIL-DR wipe device→restore latest-snapshot replay-hash==pre agree=Y` — baseline (re-assert poc_persist).
- `§EMAIL-DR mailbox-shuffle tip-by-signed-seq correct=Y` — arrival order ignored; signed `seq`/`prev_hash` wins.
- `§EMAIL-DR forged-snapshot rejected=Y fallback=latest-valid` — bad signature dropped, recover from prior valid.
- `§EMAIL-DR mailbox-purge keep=latest-only single-email-recovery=Y` — provider deleted older mail; the full-
  snapshot-per-email caveat means the LATEST alone suffices.
- `§EMAIL-DR provider-migration export@A→import@B verify=ok no-retrust=Y` — move the signed log to a new provider;
  it still verifies (the channel is a commodity; Truth 3).

**Layer B — the key regress (the headline; honest falsifier):**
- `§EMAIL-DR total-loss device+key gone, NO key-recovery → recover=FAIL reason=unverifiable` — REQUIRED witness:
  without a key anchor, the elegant idea has a hole. State it.
- `§EMAIL-DR key-recovery scheme=shamir k=2 n=3 channels=[email,email2,printed-card] reconstructed=Y verify=ok
  trust=own-k-channels` — split the key across the user's OWN channels; k-of-n recovers; names the trust.
- `§EMAIL-DR key-recovery scheme=corporate-escrow node=workspace reissue=Y rotation-op=signed verify=ok
  trust=employer-admin` — the company-controlled Workspace re-issues the key; a SIGNED key-rotation op (old key
  vouches the new) preserves history; names the trust (the admin).
- `§EMAIL-DR key-recovery scheme=platform-passkey synced=Y device-loss-survived=Y trust=platform-keychain` — the
  key is a WebAuthn passkey synced by the platform (iCloud/Google); device loss survived; names the trust.

**Key-rotation continuity (so recovery never invalidates history):**
- `§EMAIL-DR rotation old→new signed-handover verifyChain=ok historyInvalidated=N` — a rotation op signed by the
  OLD key authorising the NEW key; the chain verifies across the boundary (SSH/git signed-key-rotation pattern).

**Determinism:**
- `§EMAIL-DR rebuild recoveredProjHashA==B agree=Y` — same fixtures → byte-identical recovered state.

## Build order (each names its witness; no deploy)
- **S1 — Fixture.** A wallet/ledger projection + a chain of signed full-snapshot "emails" (seq, prev_hash, body,
  sig), an edge-minted keypair (INPUT), a Shamir split (INPUT shares), a corporate-escrow keypair, a passkey stub.
  Reuse `poc_persist` sign/verify/projectionHash.
- **S2 — Layer A** witnesses (wipe / shuffle / forged / purge / provider-migration).
- **S3 — Layer B** witnesses: first the NO-recovery failure (honest), then the three anchors (shamir / corporate /
  passkey), each with the trust it adds in the log.
- **S4 — Key-rotation continuity** (signed old→new handover; verifyChain across it).
- **S5 — Determinism** (rebuild → identical recovered hash).
- **Run:** `node scripts/poc_email_dr.js 2>&1 | tee build/erp/poc_email_dr.log` → READ the log → `# DONE` ledger
  (each claim ↔ its §EMAIL-DR line; the NO-recovery line is a PASS of an honest negative, not a failure).

## Acceptance / the honest finding
- **Layer A holds unconditionally** (data recovers from any reachable valid snapshot) — confirm or refute.
- **Layer B floor, stated:** there is NO key recovery without a trust anchor; the POC proves which anchors work and
  pins the trust each adds (own-k-channels / employer-admin / platform-keychain). The refined doctrine line to
  carry back to DistributedERP.md §0/§5.2b: *"the regress terminates for the FACT unconditionally; for the KEY it
  terminates only at a chosen anchor — and naming that anchor is the true floor, not a gap to paper over."*
- Update §5.2b with the result (which schemes, which trust) — extract-only, no overclaim.

## Guardrails
- Reuse `poc_persist`/`poc_chain` primitives; invent no crypto. Keys/shares/seq/ts are fixtures (INPUTS).
- The SEND path (emitting the snapshot) is out of scope here (recovery READS existing snapshots); assert only that
  the snapshot blob is transport-agnostic (mailto / share-sheet / the §6 facilitator can all carry it).
- Static mailbox fixture; no live email/provider; deterministic replay.
- HANDS-OFF the live CRUD/glassbowl files. NO deploy.

## Status
- SPEC, 2026-06-01. Fourth of the prove/falsify family: SHOWSTOPPER (internal), ODOO_FOLD + SAP_FOLD (migration),
  EMAIL_DR (durability/key). Author of the idea under test: docs/DistributedERP.md §5.2b + Truth 3.
- Coder executes S1–S5, produces `build/erp/poc_email_dr.log` + the `# DONE` ledger. No deploy.

---

# DONE — 2026-06-01 · `scripts/poc_email_dr.js` · `build/erp/poc_email_dr.log` · `§EMAIL-DR PASS` exit 0

Reuses poc_persist sign/verify/projectionHash + poc_chain chain semantics; AES-256-GCM for encrypt-to-user;
Shamir over GF(256) (Rijndael field — the published scheme) with FIXED deterministic coefficients as INPUTS.
The private key is modelled as the ONE secret: it signs (authenticity) AND derives `encKey = sha256(privDer)`
(confidentiality) — so "encrypt-to-user" makes each emailed snapshot ciphertext, which is what creates the floor.

**Layer A — DATA recovery holds UNCONDITIONALLY (given the key); the channel is a commodity pipe:**
- `§EMAIL-DR wipe … replay-hash==pre agree=Y` — restore from the latest signed snapshot, hash matches pre-disaster.
- `§EMAIL-DR mailbox-shuffle arrival=3,1,4,2 tip-by-signed-seq=4 correct=Y` — arrival order ignored; signed seq wins.
- `§EMAIL-DR forged-snapshot … rejected=Y fallback=latest-valid` — bad signature dropped, recover from prior valid.
- `§EMAIL-DR mailbox-purge keep=latest-only single-email-recovery=Y` — full-snapshot-per-email ⇒ the latest alone suffices.
- `§EMAIL-DR provider-migration export@A→import@B verify=ok no-retrust=Y` — move providers, still verifies (Truth 3).

**Layer B — the KEY regress (the honest floor + the anchors that close it):**
- `§EMAIL-DR total-loss … recover=FAIL reason=undecryptable` — **REQUIRED NEGATIVE (passes):** with no key anchor
  the facts are present in the inbox but the ciphertext cannot be opened. The elegant idea has this hole; state it.
- `§EMAIL-DR … scheme=shamir k=2 n=3 channels=[email,email2,printed-card] reconstructed=Y verify=ok trust=own-k-channels`
  — split across the user's OWN channels; any 2-of-3 reconstructs the key byte-exact → decrypts. Trust: the user holds k.
- `§EMAIL-DR … scheme=corporate-escrow node=workspace reissue=Y verify=ok trust=employer-admin` — company Workspace
  escrows a copy and returns it. Trust: the employer admin (who could therefore also impersonate).
- `§EMAIL-DR … scheme=platform-passkey synced=Y device-loss-survived=Y trust=platform-keychain` — platform-synced
  passkey survives device loss. Trust: the platform keychain (iCloud/Google).
- `§EMAIL-DR rotation old→new signed-handover verifyChain=ok historyInvalidated=N` — a ROTATE op signed by the OLD
  key authorises the NEW; the chain verifies across the boundary (the SSH/git signed-key-rotation pattern), history intact.
- `§EMAIL-DR rebuild … agree=Y` — deterministic recovered state.

**The refined doctrine line (to carry back to DistributedERP.md §0/§5.2b, extract-only, no overclaim):**
> *The regress terminates for the FACT unconditionally — any reachable valid signed snapshot recovers the data.
> For the KEY it terminates only at a chosen anchor (own-k-channels / employer-admin / platform-keychain), and
> NAMING that anchor — and the trust it adds — is the true floor, not a gap to paper over.*
