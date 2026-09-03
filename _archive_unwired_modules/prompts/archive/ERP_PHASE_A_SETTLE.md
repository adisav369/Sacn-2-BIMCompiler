# ⚠ DO NOT REMOVE — Scope guard
# Scope: SETTLE the still-parked Phase A — wire the PROVEN POC primitives into the DEPLOYED ERP UI
#        (`bim-ootb/viewer/kernel_ops.js` + `erp.html`). Done IN ORDER, phased: A1→A6. Read-only ERP
#        otherwise; T3 editing/CRUD stays parked. This prompt settles Phase A only — Phase B (operational
#        surfacing) and Phase C (the Tour) are separate (see prompts/ERP_DEPLOY_AND_TOUR.md).
# Specs FIRST: each POC under scripts/ IS the spec for its production change — port it, don't reinvent.
#        Read: docs/ERP.md §0.16 (the live kernel + replay), §0.20 (the secured/durable phase frame),
#        §0.21 (G-IDENTITY landed in the prototype — the identity spec to PORT), §9 (DistributedERP edge
#        suite), prompts/DISTRIBUTED_POC.md (# DONE ledger: each witness + its "Spec for production" line).
# Witness-led: each item's acceptance = a LIVE `§`-line in the deployed page mirroring its POC's verdict.
#        §-log first (whitebox console.log `§`-tags read by the coder); Playwright for WIRING ONLY
#        (scripts load, controls exist, db returns rows) — never for value verification.
# Log Mandate: save every run to a log; READ the log before conclusions. Exit code is not evidence.
# COORDINATION (critical): the ERP kernel + page live in the SEPARATE repo `bim-ootb/viewer` — another
#        session edits bim-ootb (toast/sw.js). BEFORE any bim-ootb edit: `git status` in bim-ootb, avoid
#        their files, and on deploy bump `sw.js CACHE_VERSION` + `index.html ?v=N` together (T13 checks).
#        W-CHAIN is ALREADY LIVE in `bim-ootb/viewer/kernel_ops.js` v5 (`sealChain`/`verifyChain`/`setSigner`).
# Output a # DONE appendix — every claim cited to a live `§` log line, a doc `§`, or a code `file:line`.

---

# Phase A — Settle the deployed-UI wiring (in order)

## State at start (verified 2026-06-01 against `bim-ootb/viewer/kernel_ops.js` + `erp.html`)
- **#2 G-IDENTITY landed in the PROTOTYPE only.** Implemented in `scripts/erp_kernel.js`, witnessed by
  `scripts/poc_identity.js` (`§IDENTITY PASS`); spec `docs/ERP.md §0.21`. The **live** kernel is NOT ported —
  verified: `kernel_ops.js:9` is `id INTEGER PRIMARY KEY`. A1 ports it. The prototype is the spec.
- **W-CHAIN is done & live.** `bim-ootb/viewer/kernel_ops.js` has `prev_hash`/`op_hash`/`sig` columns
  (`:16-18`), `sealChain` (`:125`), `verifyChain` (`:146`), `setSigner` (`:107`); it seals on the
  **persistence seam** (`_persistToIdb`, `:78-81`), not the hot path, using `crypto.subtle` SHA-256. **It
  seals/verifies in `id` order** (`sealChain` updates `WHERE id=?`; `verifyChain` `ORDER BY id`). A2 supplies
  a real signer; A5 surfaces `verifyChain`. Neither re-implements W-CHAIN.
- **Verified gaps:** `erp.html` does NOT call `navigator.storage.persist()` (A4) and does NOT call
  `setSigner()` (A2); no owner-gate/CAS exists in the kernel (A3).
- **Parked (this prompt settles, in order): A1 port-identity · A2 W-SIGN · A3 owner-gate+CAS · A4 W-PERSIST ·
  A5 verify-ledger UI · A6 deploy.**
- **⚠ Live collision:** `bim-ootb/viewer/erp.html` is currently MODIFIED in the toast/sw.js session's working
  tree (along with `ad_ui.js`/`ad_data.js`/`ad_parser.js`/`panels.js`/`tools.js`). A2/A4/A5 all edit
  `erp.html`. Before any edit: `git status` in bim-ootb, confirm with that session, and keep A's `erp.html`
  changes minimal + non-overlapping. The kernel changes (A1/A3) are isolated to `kernel_ops.js` (not in their
  dirty set) and are lower-risk to land first.

## Dependency order (why this sequence)
Identity must be UUID in the LIVE kernel **before** signing seals over op content (a later id-scheme change
would re-seal everything); owner-gate/CAS sits in the replay path that identity feeds; persistence and the
verify-UI sit on top; deploy is last and is the only externally-coupled step. Hence A1→A6, settled in order.

---

## A1 — Port G-IDENTITY into the LIVE kernel (`bim-ootb/viewer/kernel_ops.js`)
**Spec:** `docs/ERP.md §0.21` (D1–D4) + `scripts/erp_kernel.js` (the landed reference) + `scripts/poc_identity.js`
(the witness shape). **Verified state:** the live kernel uses `id INTEGER PRIMARY KEY` (`:9`) and W-CHAIN
seals/verifies in **`id` order**. So do NOT make `op_uuid` the PK (a UUID PK would sort lexically and break
the chain's insertion order). Instead: **add an `op_uuid TEXT` column** (edge-minted `crypto.randomUUID()` in
the page, recorded as an op input — the cross-device, clash-free identity), and **keep `id INTEGER PRIMARY
KEY` as the local total-order** that `sealChain`/`verifyChain`/replay continue to use. Identity is supplied
and recorded (never recomputed on replay); entity references key off `op_uuid`/recorded guids, not a
recomputed natural key. `commitOp` (`:48`) mints `op_uuid` when absent; replay/merge re-read it.
**Witness (mirror `poc_identity.js`):** live `§IDENTITY` — two simulated devices' ops union with no `op_uuid`
clash; replaying the merged log yields the same projection hash; replay re-reads recorded ids (no recompute).
**Done when:** the page logs the `§IDENTITY` lines, AND `sealChain`/`verifyChain` still pass in `id` order
(W-CHAIN unbroken), AND replay holds `replay-hash == live-hash`.

## A2 — W-SIGN (live): real edge signer on the existing chain
**Spec:** `scripts/poc_sign.js` (ECDSA P-256 over the W-CHAIN hash). **Do:** mint/store an edge keypair with
Web Crypto (`crypto.subtle`, ECDSA P-256), key custody at the edge (IndexedDB, **non-extractable** CryptoKey);
call `KernelOps.setSigner({sign, verify})` so the already-live `sealChain` signs each op's `op_hash` and
`verifyChain` checks the signature. **Witness (mirror `poc_sign.js`):** live `§KRN_CHAIN`/`§SIGN` — the chain
verifies under the device key; a wrong-key or edited op fails at exactly that op; the chain hash stays
deterministic while signatures vary (clean layering). **Done when:** `verifyChain()` returns OK on a clean
signed log and pinpoints the first forged op on a tampered one.

## A3 — Owner-gate + CAS in the replay path
**Spec:** `scripts/poc_distributed.js` (G-SINGLE-WRITER + set-if-unset CAS). **Do:** port the replay guards
into the browser kernel — non-owner ops rejected on replay; the one entitlement op-class uses set-if-unset
CAS. Identity (A1) is the owner key the gate reads. **Witness (mirror `poc_distributed.js`):** live `§OWNER`
/`§CAS` — two peers allocate the same document → the 2nd is rejected on replay, one allocation survives, no
value lost; two peers claim the same token → first-in-order wins. **Done when:** the rejection is logged and
the surviving projection is correct.

## A4 — W-PERSIST (live) on `erp.html` load
**Spec:** `scripts/poc_persist.js`. **Do:** call `navigator.storage.persist()` on **erp.html load** (today
only `scene.js` requests it — `erp.html` never does). Wire an export→import round-trip; stub the
signed-email-snapshot emit as the recovery hook. **Witness (mirror `poc_persist.js`):** live `§PERSIST` —
`persisted=true` logged on load; export→wipe→import → `replay-hash == pre-export hash`. **Done when:** the
round-trip hash matches and `persisted=true` appears in the log.

## A5 — "Verify ledger" UI action
**Spec:** surface A2's `verifyChain()` in the page. **Do:** a Settings/▸ "Verify ledger" control that runs
`verifyChain()` and shows `chain OK len=N` or `tamper at op N`. **Witness:** clicking it on a clean log shows
OK; on a hand-mutated row it shows the break at the right op (Playwright may confirm the control EXISTS and
fires; the OK/break VALUES are read from the `§`-line, not asserted in Playwright). **Done when:** both states
render correctly from real kernel state.

## A6 — Deploy (CLAUDE.md Deploy Flow; coordinate first)
**Do, as ONE flow (never stop partway):** `git status` in bim-ootb and avoid the toast/sw.js session's files →
edit `bim-ootb/viewer/{kernel_ops.js,erp.html}` → syntax check → verify all `§` tags exist → save a test log →
bump `sw.js CACHE_VERSION` + `index.html ?v=N` (must match, T13) → upload per `deploy/OCI_UPLOAD.md §RULES`
(every `oci os object put` includes `--content-type`) or the GH-Actions path the ERP page uses → smoke-test
the URLs → fetch back and verify content → confirm the page loads the new files. **Witness:** the deployed
page emits the A1–A5 `§`-lines live; `node deploy/dev/tests/audit_specs.js` exits 0 if any Playwright spec
was touched. **EXPLICIT GO before deploy.**

---

## Guardrails
- Spec-first; witness-led; read the log. Never invent — every claim cites a `§` log line / doc `§` / file:line.
- Port the proven POCs; do not re-implement or fork `kernel_ops.js` semantics. W-CHAIN already exists — build ON it.
- Read-only ERP; T3 editing/CRUD parked. Identity is an INPUT, never recomputed on replay (§0.21).
- Determinism holds: edge values (uuid, signature inputs, timestamps) are recorded inputs; no `Date.now`/`Math.random` in the replay path.
- Coordinate with the toast/sw.js bim-ootb session before any edit; bump sw version on deploy.
- Don't touch CLAUDE.md (2nd mantra wording pending the user).

# DONE
- (append per item: A#, the live `§`-witness line, PASS/FAIL, log path, file:line of the change)

## A1 — Port G-IDENTITY into the live kernel — **PASS** (2026-06-01, kernel-only, NOT deployed)
**Change** (`bim-ootb/viewer/kernel_ops.js`, clean in bim-ootb tree — no collision with the toast/sw.js session):
- `:9-10` — `TABLE_SQL`: added `op_uuid TEXT` column; `id INTEGER PRIMARY KEY` kept as the local
  total-order (NOT made the PK — a UUID PK would sort lexically and break W-CHAIN's id-order seal).
- `:35` — `ensureTable`: idempotent `ALTER TABLE … ADD COLUMN op_uuid TEXT` for pre-existing DBs.
- `:55-72` — `commitOp(db,opType,params,inputGuids,outputGuid,opUuid)`: 6th optional arg; honours a
  caller-supplied (edge-minted) op_uuid verbatim (D4 New-doc seam) else mints `crypto.randomUUID()`
  at COMMIT time. op_uuid is NOT in `_canonical` → W-CHAIN hash byte-identical. All 30 existing
  callers pass ≤5 args → backward-compatible.
- `:207-214` — `replayOps`: selects + returns `op_uuid` (replay RE-READS identity, never recomputes).
- banner → `v6 (W-CHAIN/W-SIGN/G-IDENTITY)` (proves the new code is the loaded code).
**Witness** `scripts/test_kernel_identity.js` (drives the REAL kernel via require-cache-bust per device;
harness mirrors `test_kernel_chain.js`). Log `build/erp/test_kernel_identity.log`:
- `§IDENTITY merge devices=2 ops=4 ids=1,2,1,2 uuids=be872fb2,c0d2f48c,247175a4,55f5fefb` — id collides
  (2 distinct of 4); op_uuid 4/4 distinct (the old rowid-only identity lost half the merged ops).
- `§IDENTITY replay ops=4 liveHash=d73800442998 replayHash=d73800442998 deviceB=d73800442998` — **replay-hash
  == live-hash**; two replays + the record agree; two devices' replay identical (holder-irrelevant).
- `§IDENTITY newdoc … storedUuid=edge-5d716168-29` — edge-minted uuid honoured verbatim (D4).
- `§KRN_CHAIN sealed=2 … verify OK len=2` — **W-CHAIN unbroken in id order**.
- `§IDENTITY PASS — 0 red.`
**Regression** `scripts/test_kernel_chain.js` → `§KRN_TEST PASS` (seal/verify/tamper-at-op-N all green on
the edited kernel) — log `build/erp/test_kernel_chain.log`.
**Done-criteria** (prompt §A1): §IDENTITY lines logged ✓ · sealChain/verifyChain pass in id order ✓ ·
replay-hash == live-hash ✓. **NOT deployed** — A6 deploy is the only externally-coupled step (explicit GO).

## A2 — W-SIGN: real edge signer on the live chain — **PASS** (2026-06-01, NOT deployed)
**Change** — NEW file `bim-ootb/viewer/erp_signer.js` (untracked → NO collision with the toast/sw.js
session's dirty `erp.html`/`ad_*`/`panels`/`tools`):
- `mintKeypair()` — `crypto.subtle.generateKey` ECDSA P-256, **private key non-extractable** (edge
  custody); public key exportable (shareable verifier).
- `makeSigner(keyPair)` — the kernel's `{sign(hashHex)->Promise<sigHex>, verify(hashHex,sigHex)->
  Promise<bool>}` contract; signs over the op_hash hex bytes (mirrors poc_sign `Buffer.from(opHash)`).
- `loadOrMint(dbName)` — IndexedDB custody: mint once, reuse the same CryptoKeyPair on reload.
- `installSigner(KernelOps)` — the page's one call: load-or-mint then `KernelOps.setSigner(...)`.
- W-CHAIN code itself UNCHANGED — the signer plugs into the existing `_signer` seam.
**Witness** `scripts/test_kernel_sign.js` (drives REAL kernel_ops.js + erp_signer.js; fresh kernel
instance per db for the _tableCreated single-flag, as in A1). Log `build/erp/test_kernel_sign.log`:
- `§SIGN issuer-signed sealed=3 verify ok=true len=3` — signed log verifies under the edge key.
- `§SIGN wrong-key verify ok=false brokeAt=1 why=signature` — fails under a different public key.
- `§SIGN forge(present-but-not-forge) ok=false brokeAt=3 why=signature` — holder edits op3 + re-signs
  with their OWN key (op1/op2 stay genuinely issuer-signed); rejected at **exactly op 3**.
- `§SIGN layering chain-stable=true sig-nondeterministic=true` — re-sealing the same rows yields an
  IDENTICAL op_hash but a DIFFERENT sig (signature outside the deterministic hash).
- `§SIGN custody … ok=true` — loadOrMint reuses the persisted key across reloads.
- `§SIGN installSigner-e2e verify ok=true` — page entry point wires the key into the kernel.
- `§SIGN PASS — 6/6.`
**Done-criteria** (prompt §A2): verifyChain OK on a clean signed log ✓ · pinpoints the first forged op
on a tampered one ✓.
**⚠ DEFERRED (the erp.html collision point):** the one-line page wiring — `<script src="erp_signer.js">`
+ `ErpSigner.installSigner(window.KernelOps)` on erp.html load — is NOT yet applied. It is batched with
A4's `navigator.storage.persist()` call and A5's verify-ledger control into a SINGLE coordinated
erp.html touch, to be done after the toast/sw.js session commits/coordinates (prompt §A coordination
rule). The signer module is fully proven standalone above; only the page include awaits that window.

## A3 — Owner-gate + CAS in the replay path — **PASS** (2026-06-01, NOT deployed)
**⚠ DIVERGENCE FROM PROMPT (deliberate, flagged):** the prompt scoped A3 to `kernel_ops.js`, but the
standing rule (MEMORY / CLAUDE.md: *kernel_ops.js is BIM-SHARED, NOT ERP; separation of concern*)
overrides. Owner/status/claimed_by are ERP-projection concerns, so A3 landed in a NEW ERP module —
which is ALSO collision-free (untracked, like erp_signer.js), preserving the prompt's safety intent.
**`kernel_ops.js` was NOT touched by A3.**
**Change** — NEW file `bim-ootb/viewer/erp_replay.js` (untracked → no collision):
- `replayGuarded(db, orderedOps)` folds the kernel op-log into a `documents` projection under the
  guards (mirrors poc_distributed.replay): CREATE = INSERT OR IGNORE; ALLOCATE = owner-gate
  (G-SINGLE-WRITER, only `actor === documents.owner`); CLAIM = set-if-unset CAS (first-in-order wins).
- `mergeLogs(...)` unions device logs by `(timestamp, op_uuid)` — A1's identity makes the union
  clash-free. `normalize(row)` flattens kernel_ops rows (parameters string OR object). The gate READS
  the recorded owner/actor — never recomputes identity (§0.21).
**Witness** `scripts/test_kernel_owner.js` (commits ERP ops through the REAL kernel → real A1 op_uuids;
then merges + guarded-replays via erp_replay). Log `build/erp/test_kernel_owner.log`:
- `§OWNER merge devices=2 ops=8 … 8/8 distinct` + `CONTRAST: local ids collide (5 distinct of 8)` —
  op_uuid keeps all 8 ops across the merge; the local `id` would have lost 3.
- `§OWNER replay hashA=8b36ee6b824b hashB=8b36ee6b824b` — both devices → SAME projection (holder-irrelevant).
- `§OWNER owner-gate INV.status=allocated rejected=1 why="non-owner (B≠A)"` — one allocation, no value lost.
- `§CAS token.claimed_by=A losers=1 why="already claimed by A"` — first-in-order wins.
- `§OWNER PASS`.
**Done-criteria** (prompt §A3): rejection logged ✓ · surviving projection correct ✓.
**Page wiring** (load `erp_replay.js` + invoke guarded replay on the ERP log) is part of the same
DEFERRED, batched erp.html touch as A2/A4/A5.

## Cross-session coordination state (verified 2026-06-01 — read before A6)
- **main moved twice (both merged/live):** PR #77 (isMobile fix, sw v557) + PR #78 (new CI **no-undef
  eslint gate on `viewer/*.js`**, 140-name `eslint.globals.json` whitelist; `viewer/ad_*.js` EXCLUDED).
  Branch `s284e-b-inplace-viewer` is NOT yet rebased on main — do that (deliberately; dirty tree has
  3 stashes + 5 ERP WIP files) before A6.
- **package.json:** main now tracks a SUPERSET (sql.js ^1.14.1 + eslint/globals). The local untracked
  `package.json`/`package-lock.json` must be deleted to take main's on rebase (nothing lost). USER/
  ERP-owner action — not done here.
- **A6 gate impact on THIS phase's files:** the new `erp_signer.js` + `erp_replay.js` are non-`ad_` →
  SUBJECT to no-undef. Both were made **browser-pure** (window-only, no `module` global) — their
  globals (crypto/TextEncoder/Uint8Array/Array/window/console/indexedDB) are the SAME set `kernel_ops.js`
  uses (already gate-passing); residual standard-ES globals (Promise/Error/parseInt) are universal.
  `kernel_ops.js` A1 edit adds NO new global (`crypto.randomUUID` reuses the existing `crypto`).
  **At A6, after rebase, run the eslint gate on the 3 changed/new non-ad_ files; add any missing name
  to `eslint.globals.json` (do NOT add `module`).**

## A4 — W-PERSIST (live) — **PASS** (module proven 2026-06-01, NOT deployed)
**Change** — NEW file `bim-ootb/viewer/erp_persist.js` (browser-pure, window-only):
- `requestPersist()` → `navigator.storage.persist()` (erp.html never called it before); logs
  `§PERSIST persisted=<granted|unsupported|error>`.
- `roundTrip(SQL, db)` / `exportBytes` / `importBytes` — the disposable-container round-trip.
- `emitSnapshot(snapJson, seq, signer, sink)` — the STUB recovery hook (edge-signs a full snapshot;
  the full seq-not-arrival / forgery-rejecting recovery stays proven in poc_persist.js).
**Witness** `scripts/test_kernel_persist.js` (drives the REAL erp_persist.js + erp_signer.js). Log
`build/erp/test_kernel_persist.log`:
- `§PERSIST persisted=true` — durable-storage path.
- `§PERSIST … preHash=7be66961e209 … postHash=7be66961e209` — export→wipe→import round-trips (hash equal).
- `§PERSIST snapshot … sigVerifies=true` — recovery hook edge-signs the snapshot.
- `§PERSIST PASS`.
**Done-criteria** (prompt §A4): round-trip hash matches ✓ · persisted=true logged ✓ (the on-LOAD
emit happens in-page — verified at A6).

## A5 — "Verify ledger" UI — **WIRED** (2026-06-01, runtime check pending A6)
**Change** — erp.html trailing script block: a bottom-left `⛓ Verify ledger` pill + `window.ErpVerifyLedger()`
that runs `KernelOps.verifyChain(window.__erpDb)` → toast `Ledger OK — N ops` / `Tamper at op N`,
logging `§VERIFY_LEDGER ok=… len=…|brokeAt=… why=…`. Logic already proven (test_kernel_chain/sign).
**Verification:** the OK/break VALUES are proven in Node; the in-page control (mounts, fires, shows
the right state) is a Playwright WIRING check at A6 (per CLAUDE.md §Browser Testing). Pill placement is
unobtrusive (clear of #gbviews + offline badge) but **awaits user visual confirmation at A6**.

## Batched erp.html wiring — DONE in working tree (NOT deployed, runtime-unverified-in-browser)
The single coordinated erp.html touch is applied (additive, clear of the prior-WIP #gbviews hunks 55–96;
all 5 inline <script> blocks parse, 0 syntax errors):
- `MODULES += erp_signer.js, erp_replay.js, erp_persist.js` (`:173-175`).
- post-load: `ErpSigner.installSigner(window.KernelOps)` (A2) + `ErpPersist.requestPersist()` (A4) (`:217-222`).
- db-ready: `window.__erpDb = db` (A5 reads it) (`:325`).
- trailing block: A5 Verify-ledger pill + `window.ErpVerifyLedger` (`:409-449`).
- **A3 note:** `erp_replay.js` is LOADED + available; invoking the guarded replay on a LIVE ERP
  document flow (CREATE/ALLOCATE/CLAIM ops) is Phase B (operational surfacing) — the read-only AD page
  emits no such ops yet. A3's guards are fully proven standalone (test_kernel_owner.js).

## Remaining → A6 (deploy, EXPLICIT GO)
Rebase `s284e-b-inplace-viewer` on main + drop local package.json (take main's superset) → run eslint
no-undef gate on the 3 new/changed non-ad_ files → bump `sw.js CACHE_VERSION` + the erp.html `sw.js?v=`
(currently `?v=508`) together → deploy → Playwright WIRING check (scripts load; pill mounts/fires;
in-page §-lines emit: §SIGN installed / §PERSIST persisted=true / §VERIFY_LEDGER) → visual-confirm the
pill. NONE of this is done — A6 is the only externally-coupled step and needs the user's GO.
