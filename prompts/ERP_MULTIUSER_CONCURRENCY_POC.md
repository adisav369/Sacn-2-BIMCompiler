# ⚠ DO NOT REMOVE — Scope & Protocol
**Scope:** SPEC ONLY — no code in this document or produced by writing it. This PoC proves ONE
thing not yet proven anywhere in the repo: that the *live product UI* (`erp/glassbowl.html` /
`erp/idempiere.html`, the real `crud_overlay.js` write path) survives **two real, concurrent
browser sessions** editing ERP data — not two identities simulated sequentially inside one Node
process (which is what every existing `poc_*.js` concurrency witness already does, see §Current
State). **Log Mandate:** every witness run in this lane writes a `.log` file and the log is READ
before any pass/fail conclusion is drawn — exit code alone is never evidence (CLAUDE.md, Universal
Session Protocol). Honour this block until every item in §Spec is `✅ DONE (witness)` or
`⛔ BLOCKED: <one question>`.

---

## §Scope

**Proves:** whether the deployed `crud_overlay.js` write path (`erp/glassbowl.html`,
`erp/idempiere.html` — both load it, `bim-ootb erp/idempiere.html:603`,
`erp/glassbowl.html:880`) holds its documented guarantees when driven by **two real Playwright
`BrowserContext`s** (= two real users/devices, isolated storage per Playwright/Chromium semantics)
or **two real tabs of one `BrowserContext`** (= one user, two windows, shared `IndexedDB`) —
mirroring the exact two-shape split `teams/tests/witness_e2e_dual_presence_modeller.js` already
uses for presence (§SEP-* / §SAME-*), applied here to **data writes** instead of presence dots.

**Does NOT cover** (named so no session re-derives these as "missing"):
- The *engine-level* determinism/merge/CAS proofs — those are DONE and are not re-litigated here
  (`poc_distributed.js`, `poc_crud_ownergate.js`, `poc_quorum_cas.js`, `poc_blackout_resume.js`,
  `test_kernel_sync.js`/`test_kernel_rebase.js`/`test_kernel_relay.js`/`test_kernel_replica.js` —
  all §Current State below). This PoC is the *missing UI-driven layer on top of* that proven
  engine, per the project's own "engine-proven ≠ UI-proven" lesson
  (`feedback_test_real_user_path_not_seams.md`, confirmed twice already — Walk tool, then Teams).
- Cross-device sync over a real network relay (`erp_relay_server.js`, `teams/erp/erp_sync.js`) —
  those are engine-proven (§Current State) but **not wired into `crud_overlay.js`'s IndexedDB
  sidecar at all**; wiring them is a separate, larger feature, named as an open question below
  (§Open Questions Q3), not attempted here.
- New accounting/BOM/geometry values — non-invent; this PoC edits fields already exercised by
  `poc_crud_ownergate.js` (`c_order.grandtotal`) and does not synthesize new business data.
- Login/auth hardening, RBAC, or any change to `AD_Role`/`AD_User` — out of scope; the PoC uses
  real existing demo users from `SES.buildContext`'s user list as-is.

---

## §Current State (EXTRACTED)

**The doctrine (read, not re-derived):** `docs/DistributedERP.md` §4 defines the guard set —
`G-IDENTITY`, `G-EXCLUSIVE-DISPATCH`, `G-SINGLE-WRITER`, `G-RESERVATION`, `G-ORDERED-HANDOFF`,
`G-LEASE-EXPIRY`, `G-READ-ANYWHERE-WRITE-OWNER` — and §9 is the adversarial suite (durability,
forgery, freshness/double-spend, ownership/contention, determinism, the irreducible floor,
privacy), each row citing a Node-run witness. §6 names the "dumb post office" (facilitator: accept
+ order + persist + relay, no business logic). `docs/internal/ERP.md` §0.20/§0.21 wired
G-IDENTITY (edge-minted UUID as an op input) into the kernel, 2026-06-01.

**Engine-level concurrency witnesses that exist and pass (all Node.js, in-process, no real
browser — this is the gap this PoC closes):**

| Witness | File | What it proves |
|---|---|---|
| W-OWNER / Merge / G-IDENTITY | `scripts/poc_distributed.js` | two devices' logs union with no PK clash; owner-gate + CAS at the op-class level |
| **W-CRUD-GATE** | `scripts/poc_crud_ownergate.js` | the LIVE UI write funnel's owner-gate/CAS (`CORE.gateOp` in `build/erp/crud_overlay.js:413-429`) rejects a wrong-owner or stale-CAS `c_order` edit BEFORE the seal, sidecar unchanged; landed live via bim-compiler commit `5ceb0c11d` ("erp(crud): SO full document process in UI... W-CRUD-GATE", 2026-06-13) |
| W-SYNC-FSM / W-REBASE | `scripts/test_kernel_sync.js`, `scripts/test_kernel_rebase.js` | schema+chain gates on sync; SQLSync-style rebase (rewind→apply-server-order→replay-pending→re-seal); "two devices → IDENTICAL chain tip" |
| W-RELAY | `scripts/test_kernel_relay.js` | idempotent HTTP push/pull convergence over a dumb facilitator (`build/erp/erp_relay_server.js`) |
| W-REPLICA | `scripts/test_kernel_replica.js` | 3 real static hosts (GH/OCI/local) replay to an identical, signed chain tip |
| §ORDER-HONEST | `scripts/poc_blackout_resume.js` | net books reconstructible from disjoint per-branch logs after a lost sequencer (`maxDiff=0c`) |
| §CAS-SLIVER / `poc_quorum_cas.js` | `scripts/poc_quorum_cas.js` | the one residual (cross-branch CAS arbitration order) bounded to a quorum-RTT window |
| §DETECT/§ATTRIBUTABLE | `scripts/poc_equivocation.js` | a facilitator handing different clients different op orders is caught and attributed |
| W-ERP-SYNC | `bim-ootb teams/erp/erp_sync.js` + `teams/tests/poc_teams_erp_sync.js` | one-branch-per-role ERP sync over the Teams transport (§S8) — engine only, **not wired into `crud_overlay.js`** |

**The ONE real two-browser-context witness in either repo, and what it does NOT prove:**
`teams/tests/witness_e2e_dual_presence_modeller.js` (bim-ootb, PR #661) drives two REAL Playwright
`BrowserContext`s (and, in a second scenario, two tabs of one context) against the live Modeller
`#b-teams`/`#teams-pill` UI. It proves two things relevant here and one hard limit:
1. `§BC-CONTROL`: **`BroadcastChannel` never crosses two separate `BrowserContext`s**, even
   same-origin — a raw platform fact, proven with no app code (memory:
   `project_teams_e2e_no_ui_finding.md` Finding 3).
2. Two tabs of the SAME context DO share `BroadcastChannel` — the one case it covers.
3. **It is presence-only** ("who's here" dots) — it makes zero data writes and does not touch
   `crud_overlay.js`, `kernel_ops`, or IndexedDB. No witness in either repo exercises a real
   second `BrowserContext` performing a CRUD write.

**Honest gap this PoC closes.** Every "two devices/two users" claim in the doctrine above is
proven by one Node.js process holding two in-memory data structures and calling functions on them
in sequence — never by two independent browser processes racing against a shared store. That
distinction matters concretely here, because of a mechanism this session found by reading the live
write path (not previously logged as a finding anywhere):

**`build/erp/crud_overlay.js` `_sidePersist()` (~line 1632) is an unconditional whole-blob
overwrite, not a CAS.** The sidecar op-log lives in a **separate in-memory `sql.js` `Database`**
per page-load (`withSidecar`, ~line 1644, hydrates once from IndexedDB when the page opens), and
every commit calls `_sidePersist()`, which does:
```
var buf = SIDE.export().buffer;                       // whole-DB export, not incremental
db.transaction(SIDE_STORE, 'readwrite')
  .objectStore(SIDE_STORE).put(buf, SIDE_KEY);          // unconditional PUT — no version/CAS check
```
`SIDE_DBNAME='glassbowl_kernel_ops'`, `SIDE_KEY='kernel_ops.db'` are **fixed** — same-origin tabs
share this one IndexedDB record. `_serializeCommit` (~line 1723, a `Promise` chain) only serializes
commits **within one JS execution context** — it cannot and does not coordinate across tabs/
processes. So: if Tab A and Tab B both hydrate `SIDE` from the same starting blob, A commits +
persists (blob v1 contains A's op), then B — still holding its OWN in-memory `SIDE` from before A's
write — commits + persists (blob v2, derived from B's stale state, containing B's op but NOT A's)
→ **B's blob write silently overwrites A's**, even though both commits individually passed
`verifyChain` and the T4 owner-gate/CAS (which only checks the *logical* record fields inside each
process's own already-loaded view, never the physical IndexedDB generation). This is a **plausible,
previously-unwitnessed bug shape**, not a confirmed one — §Spec S3/S4 below test it. It is exactly
the "last-write-wins at the storage layer" failure the doctrine's root truth (§0 `DistributedERP.md`)
is designed to avoid at the *fact* level (`QtyOnHand=Σ movements`) — but the *op-log itself*, at the
IndexedDB blob layer, has no equivalent fold-not-overwrite protection today.

**`cas: "claimed_by"` is a LEASE column, not a version column (EXTRACTED 2026-07-19, resolves the
former Q3).** `crud_ops.json:72-73` declares `c_order` as `ownerGated:true, "cas":"claimed_by"`. But
the op-class semantics of `claimed_by` are defined in `bim-ootb erp/erp_replay.js:61-64`:
```js
} else if (op.op_type === 'CLAIM') {
  var cur = <SELECT claimed_by FROM documents WHERE uuid=?>;
  if (cur) { rejected.push({ why: 'already claimed by ' + cur }); return; }   // first claimant wins
  db.run('UPDATE documents SET claimed_by=? WHERE uuid=?', [op.actor, op.target]);
}
```
`claimed_by` is set ONCE, at claim time, and does **not** change when a field like `grandtotal` is
edited. That is **pessimistic locking** — `G-RESERVATION` / `G-SINGLE-WRITER` (`DistributedERP.md`
§4) — an exclusive writer lease. Compare-and-swap requires a value that changes on EVERY write (a
version counter / timestamp / op-hash); a lease token cannot detect "someone modified this since you
read it", only "someone else owns this". **Consequences, both settled by this extraction, not
assumed:**
1. The column is **per-USER by construction** — the claimant is `op.actor` = `APP.actor` =
   the `AD_User` id (`crud_overlay.js sessionActor()` ~line 1273). Two tabs of ONE login are the
   same claimant by definition, so the gate cannot distinguish them. The two-tabs case is not a
   gap in the design — it is **outside the design**; a single user in two concurrent sessions was
   never modelled.
2. `claimed_by` guards **WHO writes. Nothing in the system guards WHICH VERSION they wrote over.**
   Case 1 (§Concurrency Model) is therefore covered by nothing at all, and the `"cas"` key name in
   `crud_ops.json` is a **mislabel** that makes T4 read as if it covers Case 2 when it structurally
   cannot.

**Multi-user login is real, not stubbed** — this makes the PoC expressible today with no product
change: `erp/idempiere.html` `loginStep1()` (~line 1048) lets a user pick from a real `AD_User`
list (`SES.buildContext`); `applySession()` (~line 1093) sets `window.APP.actor = _session.user.id`
per page-load. `crud_overlay.js sessionActor()` (~line 1273) reads exactly that. So two tabs CAN
log in as two genuinely different real `AD_User` rows and carry two different `APP.actor` values —
nobody has run this.

---

## §Concurrency Model

Derived from the write path actually read above, not from generic CRDT theory. Three real cases:

**Case 1 — Two tabs, one `BrowserContext` (one user, two windows), same order, non-conflicting
fields.** Both hydrate `SIDE` from the same IndexedDB blob at open. Both edit *different* columns
of the same `c_order` row and save. Expected-safe per doctrine (disjoint writes "union trivially",
`DistributedERP.md` §3) — but the blob-overwrite mechanism above means the SECOND tab's persist
physically replaces the FIRST tab's blob wholesale, which contains the first tab's committed op
only if B's in-memory `SIDE` already contained it (it doesn't, unless B reloaded after A's commit).
**This is the case S3 tests** — a plausible silent-loss bug hiding inside a "should be fine"
scenario.

**Case 2 — Two tabs, one `BrowserContext`, same order, conflicting field (both change
`grandtotal`).** Same blob mechanism as Case 1. The T4 owner-gate/CAS is declared on `c_order`
(`crud_ops.json:72-73`) but **cannot fire here, and this is now known by extraction rather than
predicted**: per §Current State, `claimed_by` is a *lease*, per-USER, set once at claim time. Both
tabs carry the same `APP.actor`, so the owner check passes trivially (`actor === owner`), and the
"CAS" comparison reads a column that did not change between A's write and B's — so it also passes.
**There is no version/generation value anywhere in the write path for a real CAS to compare.** The
expected outcome is therefore (b) — silent acceptance, last persist wins — and S4's job is to
*witness* that, not to discover it. **This is the case S4 tests.**

**Case 3 — Two separate `BrowserContext`s (two real users/devices), same order.** Separate
IndexedDB per context (Playwright/Chromium isolation — the same isolation `§BC-CONTROL` already
proved for `BroadcastChannel`). There is **no sync path between them at all** today — `erp_sync.js`
/ `erp_relay_server.js` are not wired to `crud_overlay.js`'s sidecar. So two real users editing the
"same" order in two real sessions produce two **permanently divergent** sidecars — not a race, a
total silent fork, with no reconciliation until a human manually re-syncs (which no UI affordance
exists for on this path). **This is the case S5 names** (an honest gap, not a bug — the doctrine
never claimed offline-cross-device sync was wired here; it claimed it was *engine-proven
elsewhere* — the PoC's job is to make that gap visible in the actual product, not to fix it).

**Offline edit + reconnect** — not applicable in the sync-FSM sense (`erp_sequencer`/`rebase`)
because that machinery isn't reachable from the live UI (§Current State). The closest live
analogue is Case 3: a session that never talks to another session is indistinguishable from "went
offline and never reconnected." No reconnect path exists to test.

**Last-write-wins vs merge** — the doctrine's answer (`DistributedERP.md` §0 root truth) is
*neither*: derived facts (`QtyOnHand`) fold; the op-log itself unions and total-orders. That answer
is proven at the engine level (`poc_distributed.js`) but, per the mechanism found above, the live
IndexedDB sidecar currently behaves as **whole-blob last-write-wins** for anything not captured in
one tab's own in-memory state — the opposite of the doctrine, at the one seam nobody had looked at.

---

## §Spec

Numbered, testable, each a single falsifiable statement.

- **S1.** Two Playwright `BrowserContext`s can each independently log in
  (`loginStep0`→`loginStep1`→`loginStep2`) as two different real `AD_User` rows on
  `erp/idempiere.html`, each ending with a distinct `window.APP.actor`.
- **S2.** Two tabs of the SAME `BrowserContext`, both opened on `erp/glassbowl.html`, both
  hydrate `SIDE` from the identical starting IndexedDB blob (same op count, same tip hash) before
  either edits anything.
- **S3.** Two tabs of one `BrowserContext` (Case 1: same order, DIFFERENT non-conflicting fields,
  e.g. tab A edits `documentno`-adjacent free field, tab B edits `grandtotal`) each independently
  save. AFTER both saves, a fresh third tab (or a reload of either) reading the sidecar shows
  **BOTH** edits present. (Falsifies to: one edit is silently missing — the blob-clobber bug named
  in §Current State.)
- **S4.** Two tabs of one `BrowserContext` (Case 2: same order, SAME field `grandtotal`, tab A
  saves first, tab B — still holding its pre-A in-memory state — saves second) — tab B's save is
  **accepted with no rejection** (`§CRUD-GATE` either absent or `verdict=PASS`), and the final
  persisted `grandtotal` is B's value. **Predicted by extraction, not open:** `claimed_by` is a
  per-user lease that does not change per edit (§Current State), so neither the owner check nor the
  "CAS" check can fire between two tabs of one login. S4 exists to convert that reasoning into a
  witnessed fact. (Falsifies to: a rejection DOES appear — meaning some guard not found in the read
  is active, and the §Current State extraction is wrong and must be corrected.)
- **S5.** Two separate `BrowserContext`s (Case 3: two real users/devices), each editing the same
  order independently, produce two sidecars whose tips diverge (`verifyChain` tip A ≠ tip B) with
  **no error, warning, or UI affordance surfaced to either user** — naming the cross-device fork as
  a real, currently-silent gap (not asserting it should be fixed here).
- **S6.** The existing single-process witness `scripts/poc_crud_ownergate.js` (G3/G4/G5) continues
  to PASS unmodified — this PoC is additive, it does not touch `crud_overlay.js` gate logic, so the
  already-proven single-process owner-gate/CAS behaviour must not regress.

---

## §Witnesses

| Spec | Witness | `§` log tags + expected values |
|---|---|---|
| S1 | `W-MULTIUSER-LOGIN` | `§E2E-LOGIN ctx=1 user=<AD_User.Name> actor=<id>` and `§E2E-LOGIN ctx=2 user=<AD_User.Name> actor=<id>`, asserted `actor` values differ |
| S2 | `W-SIDECAR-HYDRATE` | `§E2E-HYDRATE tab=A tip=<hash> ops=<n>` / `§E2E-HYDRATE tab=B tip=<hash> ops=<n>` — assert equal before either edits |
| S3 | `W-CRUD-BLOB-RACE-DISJOINT` | `§E2E-SAVE tab=A field=<col> committed=<bool>` / `...tab=B...`; then `§E2E-VERIFY-FINAL fields_present=<col1,col2> expected=<col1,col2>` — issue proved/disproved: does the second tab's `_sidePersist` blob-overwrite silently drop the first tab's already-committed, chain-verified op |
| S4 | `W-CRUD-BLOB-RACE-CONFLICT` | `§E2E-SAVE tab=A grandtotal=<v1> committed=true` then `§E2E-SAVE tab=B grandtotal=<v2> committed=<bool> gateVerdict=<PASS|REJECT|absent>`; final `§E2E-VERIFY-FINAL grandtotal=<v>` — issue proved/disproved: does same-actor same-field cross-tab conflict get caught by anything, or silently resolve to whichever tab persisted last |
| S5 | `W-CROSS-DEVICE-FORK` | `§E2E-TIP ctx=1 tip=<hash>` / `§E2E-TIP ctx=2 tip=<hash>` after both edit independently — asserted to DIFFER, with `§E2E-NO-SURFACE` confirming neither page emitted any error/toast/warning event (`overlay:committed` or console `warn`) referencing the divergence |
| S6 | (regression, no new witness) | `node scripts/poc_crud_ownergate.js` exit 0, all G1-G5 lines unchanged |

Each witness script: `⚠ DO NOT REMOVE` header naming the issue it proves/disproves (per
`CLAUDE.md` "Tests expose issues"), runs via `bash build/erp/run_witness.sh scripts/<name>.js`
(full output → `build/erp/<name>.log`, log read before any pass/fail claim — Log Mandate), and per
project convention a `🟢`/`🔴` `verdict()` line per assertion, `process.exit(fails ? 1 : 0)`.

---

## §Test Harness

Reuse, don't invent: `teams/tests/witness_e2e_dual_presence_modeller.js` (bim-ootb) is the existing,
proven pattern for exactly this shape of test and should be copied/adapted, not redesigned:
- A throwaway `http.createServer` serving the repo root (no external test server dependency).
- `require('playwright')`, with the same fallback the existing witness uses
  (`require('/home/red1/bim-ootb/tests/node_modules/playwright')`) since Playwright is not a
  top-level dependency everywhere in the tree.
- `browser.newContext()` **twice** for S1/S5 (two real separate users/devices — Playwright's
  per-context storage partition IS the isolation mechanism, already proven by `§BC-CONTROL` to
  match real separate-profile behaviour for `BroadcastChannel`, and by extension for IndexedDB,
  which is likewise partitioned per storage origin+context in Chromium).
- One `context.newPage()` **twice** for S2/S3/S4 (two tabs, one user, shared IndexedDB — the
  `§SAME-*` shape in the existing witness).
- Real UI interaction only, per the project's own prior finding
  (`feedback_test_real_user_path_not_seams.md`): `page.click`/`page.fill` on the actual login flow,
  edit ring, and Save button — **no `page.evaluate()` reaching into `crud_overlay.js` internals**
  to fake a commit. The whole point of this PoC is that the previous concurrency proofs were all
  internals-only.
- Read final state via the SAME `§`-log convention `crud_overlay.js` already emits
  (`§CRUD-PERSIST`, `§CRUD-GATE`, `§CRUD process committed`) captured with
  `page.on('console', ...)`, matching `docs/archive/TestArchitecture.md` §Browser Testing (`§`-tag
  is primary evidence; Playwright drives the click path, does not replace the log read).
- No new test framework, no new server, no new browser-automation dependency — every piece above
  already exists in the repo today.

---

## §Remedy Direction (PROPOSAL — not extracted, not yet specced as work)

⚠ Everything in this section is **proposed standard practice, not repo fact** — none of it exists in
the tree today. It is recorded here so the witness results land against a named direction instead of
an open question. No implementation until S3/S4 report.

**The failure class, named precisely:** `_sidePersist()` is a read-modify-write of the ENTIRE
database file. That is the textbook **lost-update anomaly** at the worst possible granularity. A
traditional RDBMS cannot exhibit it — its unit of write is a row inside a shared file, coordinated by
a lock manager + WAL. Concurrency control (OCC via a `Version`/`Updated` column — which **iDempiere's
own `PO.save()` does**, and iDempiere is this project's declared oracle — or pessimistic
`SELECT ... FOR UPDATE`) sits ABOVE that. **No amount of row-level CAS fixes a whole-file
overwrite** — perfect OCC on `grandtotal` still loses Case 1, because the loss is below the row layer.

1. **Append ops as individual IndexedDB records; stop persisting a whole-DB blob.** Today: one store,
   one fixed key (`kernel_ops.db`), `put()` of a full export. Instead: an op store keyed by
   sequence/hash, written with `add()`. Tab A writes `#41a`, Tab B writes `#41b` — different keys,
   **neither can physically clobber the other**. Hydration = read all op records + replay (what the
   kernel already does). The whole-DB export survives only as a compaction snapshot carrying a
   generation number. This makes the clobber *structurally impossible* rather than *defended
   against*, and it is exactly what `DistributedERP.md` §0 already mandates ("the op-log unions and
   total-orders") — the storage layer is currently contradicting the project's own doctrine.
2. **Cross-tab coordination via primitives already in the browser** (no new dependency):
   `navigator.locks.request()` (Web Locks API) as a real cross-tab mutex around commit — the native
   equivalent of a row lock; plus `BroadcastChannel` to notify sibling tabs to refresh their `SIDE`.
   `§BC-CONTROL` already proved `BroadcastChannel` works WITHIN one `BrowserContext` (Case 1/2 scope)
   and correctly does NOT cross contexts — which is why it is useless for Case 3/S5, which needs the
   relay.
3. **Un-conflate the two concepts.** Keep `claimed_by` as the inter-USER exclusive lease it actually
   is (add `G-LEASE-EXPIRY` if a claim can go stale). Add a SEPARATE real version/op-hash column for
   stale-write detection, and rename the `crud_ops.json` `"cas"` key so it stops pointing at a lease
   column — that mislabel is what made T4 read as covering Case 2 when it structurally cannot.

Payoff order: **(1) is the fix** — it eliminates the failure class. (2) is correctness+UX on top.
(3) is cleanup that stops the next reader being misled the same way.

---

## §Open Questions

- ⛔ BLOCKED: Should S4's finding (if it lands on outcome (b) — same-actor same-field cross-tab
  conflict is silently accepted with no rejection) be filed as a bug fix (extend the T4 gate to
  cover intra-actor multi-session CAS, e.g. a session/tab-id component in the CAS key) or accepted
  as a documented limitation of the current single-actor-per-login model? This PoC's job is to name
  the finding, not decide the remedy — needs a user call once the witness result is in hand.
- ⛔ BLOCKED: Is real cross-device sync for the live CRUD sidecar (wiring `teams/erp/erp_sync.js` /
  `erp_relay_server.js` into `crud_overlay.js`'s IndexedDB path, closing Case 3/S5's silent fork) an
  actual roadmap item, or is single-device-only intentional for this surface today? The engine-side
  transport exists and is proven (W-ERP-SYNC) but wiring it into the live write path is a
  nontrivial feature, not a PoC-sized task — needs a priority call, not assumed here.
- ✅ RESOLVED 2026-07-19 (was: is `claimed_by` per-SESSION or per-USER?) — **per-USER, and it is a
  lease, not a CAS**. Settled by extraction from `erp_replay.js:61-64` + `crud_ops.json:72-73`, see
  §Current State. No user decision was needed; the code says it. Two tabs of one login are the same
  claimant by construction, and no version column exists anywhere in the write path.

---

## §Results 2026-07-19 — S2/S3/S4 witnessed, REAL two-tab browser run

**Witness:** `scripts/witness_e2e_crud_blob_race.js` (bim-compiler) — a REAL two-Playwright-tab,
one-`BrowserContext` run against the LIVE product UI (`bim-ootb/erp/idempiere.html`, real
`crud_overlay.js` write path, real `ad_seed.db` row `c_order` id=101 / documentno 80001). Login via
the app's own `?login=SuperUser` zero-click entry (`_tryAutoLogin`); record opened via the app's own
`?window=143&record=101` deep link (`_landOnRecord`); edits driven by `page.fill()` on the real
`data-col` inputs + `page.click()` on the real, visible `#idmp-toolbar` Save button. NO
`page.evaluate()` faked any commit — the only `evaluate()` calls read already-persisted IndexedDB
state via the app's own published `window.__crud.kernelDb()`/`core.tipValues()` API (the same calls
`idempiere.html` itself makes). Log: `build/erp/witness_e2e_crud_blob_race.log` (1173 lines) — READ
before trusting this summary (Log Mandate). Run: `bash build/erp/run_witness.sh
scripts/witness_e2e_crud_blob_race.js` — exit 1 (1/8 assertions 🔴, all three below EXPECTED-shaped 🔴s
per the file's own header, not a harness defect).

**S2 / W-SIDECAR-HYDRATE — CONFIRMED clean (no bug here).**
```
§E2E-HYDRATE tab=A tip=GENESIS ops=0
§E2E-HYDRATE tab=B tip=GENESIS ops=0
🟢 S2 tab A and tab B hydrate the IDENTICAL starting blob (same tip, same op count)
```

**S3 / W-CRUD-BLOB-RACE-DISJOINT — BUG CONFIRMED, worse than the falsification wording implied.**
Tab A edited `description`, tab B (already hydrated before A's save) edited `grandtotal` — a
DIFFERENT column. Both individually reported a successful signed commit:
```
§CRUD-PERSIST key=c_order id=101 op=CRUD_UPDATE cols=description ... sealed=1 verifyChain=ok   (tab A)
§CRUD-PERSIST key=c_order id=101 op=CRUD_UPDATE cols=grandtotal  ... sealed=1 verifyChain=ok   (tab B)
§E2E-VERIFY-FINAL fields_present=grandtotal expected=description,grandtotal values={"grandtotal":4242.42}
🔴 S3 ISSUE CONFIRMED: the predicted blob-clobber occurred — description LOST (final ops=1)
```
A fresh THIRD tab's read-the-tip shows **`description` is gone entirely** — not merely "grandtotal
wins on conflict" (there was no conflict, different columns) but **A's whole committed, chain-verified
op vanished** because B's `_sidePersist()` exported B's own stale in-memory `SIDE` (which never
contained A's op) and overwrote the IndexedDB blob wholesale. Both tabs' own consoles reported
`committed:true` / `verifyChain:ok` — **the UI told both users their save succeeded, and one of them
was wrong**, with no error surfaced to either. This is the disjoint-field case the spec called
"plausible, previously-unwitnessed" — now witnessed.

**S4 / W-CRUD-BLOB-RACE-CONFLICT — CONFIRMED exactly as predicted by extraction.**
```
§E2E-SAVE tab=A grandtotal=1111.11 committed=true gateVerdict=PASS
§E2E-SAVE tab=B grandtotal=2222.22 committed=true gateVerdict=PASS
§E2E-VERIFY-FINAL grandtotal=2222.22 ops=1 tip=2306ba51de2f7cc751bb111b5cde6465311cea529a0dd60e1341936bd79927cc
🟢 S4 final persisted grandtotal = B's value — last-tab-to-save silently wins
```
The owner/CAS gate ran for both (`ownerGated=Y`) and PASSed both — no rejection anywhere, matching
the §Current State extraction (`claimed_by` is a per-user lease, both tabs are the same actor, no
version column exists to CAS against). Final state: A's `1111.11` is gone, B's `2222.22` silently
won, `ops=1` (not 2 — A's row was never in B's exported blob at all, same underlying mechanism as S3).

**S6 regression — PASS, unmodified.** `node scripts/poc_crud_ownergate.js` exit 0, G1-G5 lines
unchanged (`build/erp/poc_crud_ownergate.log`); this PoC touched no product code.

**Net verdict: the predicted bug is REAL, witnessed live in the real browser UI, not just read from
the source.** Both S3 and S4 land on the "issue confirmed" branch, not the disproof branch — S3's
finding is strictly worse than what was predicted (total op loss, not just column-level clobber,
since the mechanism operates at the whole-DB-export granularity regardless of which columns
changed). §Open Questions' two ⛔ items (fix-vs-document Case 2; wire cross-device sync) remain
genuinely open — this session only witnessed, per its own boundary, and made no product change.

---

## §Results 2026-07-19 (cont.) — S1/S5 witnessed, REAL two-BrowserContext run

**Witness:** `scripts/witness_e2e_multiuser_login_fork.js` (bim-compiler) — two genuinely SEPARATE
Playwright `BrowserContext`s (not two tabs of one, per §Test Harness) against the LIVE product UI
(`bim-ootb/erp/idempiere.html`). S1 drives the REAL click-through login funnel
(`#idmp-login-clients`/`#idmp-login-users` rows + the real `#idmp-login-ok` button) — no `?login=`
shortcut — to two real, distinct `AD_User` rows in client 11 GardenWorld (`GardenAdmin`=101,
`GardenUser`=102 — the only two GardenWorld users with `hasRoles=true`, verified via sqlite3 against
`ad_seed.db` before writing the file; the other three GardenWorld users have zero `ad_user_roles`
rows and render as disabled/unclickable). S5 reuses the same real deep-link + `page.fill()`/
`page.click()` Save pattern S3/S4 already proved clean (`?login=SuperUser&window=143&record=101`,
same actor in both contexts, deliberately — isolates the storage-partition variable under test from
the unrelated owner-gate-reject confound a non-owner actor would introduce; S1 already separately
proves distinct-actor login works). Log: `build/erp/witness_e2e_multiuser_login_fork.log` (615
lines) — READ in full before this summary (Log Mandate). Run: `bash build/erp/run_witness.sh
scripts/witness_e2e_multiuser_login_fork.js` — **exit 0, 11/11 🟢**.

**S1 / W-MULTIUSER-LOGIN — CONFIRMED clean.**
```
§E2E-LOGIN ctx=1 user=GardenAdmin actor=101
§E2E-LOGIN ctx=2 user=GardenUser actor=102
🟢 S1 ctx=1 reached a real logged-in actor (GardenAdmin) — actor=101
🟢 S1 ctx=2 reached a real logged-in actor (GardenUser) — actor=102
🟢 S1 ctx=1 and ctx=2 actor values DIFFER (two genuinely distinct real AD_User identities) — ctx1.actor=101 ctx2.actor=102
```
Two separate `BrowserContext`s independently drove `loginStep0`(tenant)→`loginStep1`(user)→
`loginStep2`(role/org)→real Log-In-button click, each landing on its own `window.APP.actor`
(confirmed also via the app's own `§ACTOR login user=101 app.actor=101 client=11 org=0` /
`§ACTOR login user=102 app.actor=102 client=11 org=11` lines). Multi-user login is real and reachable
by the actual click path, not just the `?login=` demo shortcut.

**S5 / W-CROSS-DEVICE-FORK — CONFIRMED exactly as predicted (Case 3, §Concurrency Model).**
```
§E2E-SAVE ctx=1 grandtotal=7777.77 committed=true
§E2E-SAVE ctx=2 grandtotal=8888.88 committed=true
§E2E-TIP ctx=1 tip=0dc04c3b8e95cd7417c5ad4cafbb20013020178fd0b1ef1c6ef1b4742b2cda0c ops=1 grandtotal=7777.77
§E2E-TIP ctx=2 tip=43aaf1c33d65eb8011476a22b63d28b0fbdb20fbe43dc9c6935db5dee9274c42 ops=1 grandtotal=8888.88
🟢 S5 ctx=1 and ctx=2 sidecar tips DIVERGE (predicted: no sync path exists between separate contexts)
🟢 S5 each context privately kept ITS OWN edit (A=7777.77, B=8888.88) — a true silent fork, not a merge or a shared last-write-wins
§E2E-NO-SURFACE ctx=1 warn/err=[] toasts=["UPDATE Order — saved (signed)"]
§E2E-NO-SURFACE ctx=2 warn/err=[] toasts=["UPDATE Order — saved (signed)"]
🟢 S5 §E2E-NO-SURFACE — no console warning/error on EITHER page references divergence/conflict/staleness
🟢 S5 §E2E-NO-SURFACE — no toast shown to EITHER user references divergence/conflict/staleness
🟢 S5 §E2E-NO-SURFACE — BOTH users instead saw the NORMAL success toast ("saved (signed)")
```
Both contexts' own `§CRUD-GATE` line read `verdict=PASS actor=100 owner=100 cas=claimed_by` — the
gate ran and passed cleanly on both (same actor, same owner, no rejection). Each context sealed its
own chain (`§KRN_CHAIN verify OK len=1`), persisted its own 20.0KB sidecar blob, and each read back
ONLY its own edit — `ops=1` on both sides, never 2, confirming there is truly no reconciliation, not
merely a race. Zero console `warning`/`error` on either page (empty arrays, not filtered-to-empty),
zero `pageerror` events, and the only toast either user saw was the normal
`"UPDATE Order — saved (signed)"` success toast — the UI told both users their private, now-permanently-
diverged edit succeeded, identically to what a real conflict-free save looks like. This is the silent
fork named in §Concurrency Model Case 3, now witnessed live rather than read from the source.

**Net verdict: both S1 and S5 land exactly on their predicted branch — no disproof.** S1 shows the
real login path reaches two genuinely distinct identities; S5 shows two real devices editing the same
order fork permanently and silently, with the UI actively telling both users "saved" and nothing else.
Together with the already-witnessed S2/S3/S4, all four Case 1/2/3 concurrency shapes named in
§Concurrency Model are now witnessed live in the real product UI, not just read from source. This
session made no product change and did not touch `prompts/ERP_OPLOG_APPEND_ONLY_FIX.md` (owned by a
concurrent session) — §Open Questions' two ⛔ items remain open, now with S5's live confirmation behind
the second one.
