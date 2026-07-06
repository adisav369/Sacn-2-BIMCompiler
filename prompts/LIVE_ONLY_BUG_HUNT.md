# ⚠ DO NOT REMOVE — Scope guard / SESSION CARD: LIVE-ONLY BUG HUNT (the headless blind-spot sweep), queued 2026-06-13
# Paste-to-start: `proceed with prompts/LIVE_ONLY_BUG_HUNT.md`
# READ THE LOG after every run (exit ≠ evidence). ALL poc_* via `bash build/erp/run_witness.sh scripts/poc_X.js`.
# NON-NEGOTIABLE: spec-first · witness-led · deterministic NON-INVENT · EXTRACT the suspects (grep), never guess them ·
#   every live witness is FALSIFIER-FIRST (must go RED on the CURRENT shipped code if the bug is real) ·
#   bim-ootb edits ONLY in /tmp/wt-* off FRESH origin/main, ONE PR per fix-cluster, sw bump once, orphan-check the squash.

## WHY THIS CARD EXISTS (read before doubting it)
A headless witness is GREEN because it runs in a harness that **substitutes** real browser primitives with
fakes (in-memory sql.js for IndexedDB, no Service Worker, no BarcodeDetector, a static file server for GH
Pages). A whole bug CLASS is therefore *structurally invisible* to headless and *only* appears live:

  > **A bug is "live-only" iff it depends on a primitive the headless harness replaces.**

The 2026-06-13 archetype (WH×POS pick lane, bim-ootb PR #283): `viewer/wh_walk.js` opened IndexedDB
`bim_ootb_cache` at hardcoded **version 1**, drifting BELOW `scene.js`'s **version 2** (`APP.openCacheDB`)
→ `VersionError` → the §S-2b sidecar was NEVER read → the walk offered `pos-docs=0`. The headless witness
(W-WH-POS-PICK) folded an in-memory sql.js db — there is no scene.js, no IDB version, no drift — so it
passed. The SAME drift had already bitten and been fixed once in `kernel_ops.js` (§KRN_PERSIST_FIX). It
was not "missed by intelligence"; it was **outside the harness's reach**. This card is the standing sweep
that converts *headless-green* into *live-proven*, falsifier-first, so this class stops shipping.

## THE METHOD (deterministic, three steps, repeat per suspect)
1. **ENUMERATE the substitutions** — what does the headless harness fake? (the list below, EXTRACTED, not
   invented — re-confirm each against the current harness; harnesses drift too).
2. **GREP production for dependence on the REAL primitive** — for each substitution, find the shipped code
   that needs the real thing. Those call-sites are the suspect set. Record file:line for every hit.
3. **LIVE-WITNESS it falsifier-first** — write/extend a Puppeteer witness on the SERVED pages (same-origin,
   real SW, real IDB) that exercises the suspect. The witness MUST go **RED on the current shipped code**
   if the bug is real (§FALSIFIER). If it is GREEN on unfixed code → either there is no bug or the witness
   never reached the seam: prove which by a probe before declaring "clean." Then fix → re-witness → bank.

## THE SUBSTITUTION LIST (EXTRACT/confirm each from the real harness before trusting it)
For each: the fake the harness uses · the grep seed for the suspect set · the live-only failure mode.

- **IndexedDB version + store ownership.** Fake: in-memory `sql.js` Database; OR a one-off
  `indexedDB.open(name)` in the witness. Real: `scene.js` owns `bim_ootb_cache` at **v2**
  (`A.openCacheDB`, store `dbs`+`timestamps`); other DBs (`erp_cache`, `bim.*`) have their own owners.
  - GREP: `grep -rn "indexedDB.open(" viewer erp common` → ANY call passing an explicit version that is
    not the canonical opener is a drift risk (open at a LOWER version than the owner → `VersionError`;
    HIGHER → silent upgrade that may not create the store the owner expects). Also
    `grep -rn "createObjectStore\|objectStoreNames" viewer erp common` to map who creates which store.
  - FAILURE MODE: `VersionError` → `onerror` → silent null → a read/persist that never happens.
  - FIX PATTERN: route every open through the owner (`APP.openCacheDB()`), else versionless
    `indexedDB.open(name)` (current version) — the `kernel_ops.js §KRN_PERSIST_FIX` / `wh_walk.js
    _openCacheDB()` idiom. NEVER hardcode a version outside the owner.

- **Service Worker: CACHE_VERSION, PRECACHE set, cache-first staleness, skipWaiting/clients.claim.**
  Fake: none — the file server serves fresh bytes, no SW installs. Real: a versioned SW caches a precache
  manifest; cache-first assets go stale until the version bumps; navigation is SWR.
  - GREP: `grep -rn "?v=" erp/*.html viewer/*.html` for asset refs → cross-check each against
    `PRECACHE_ASSETS` in the matching `sw.js`. A `?v=` bump with no precache entry / no CACHE_VERSION bump
    = a live staleness bug (old asset served from cache). Merge collisions on `sw.js` (the conflict magnet,
    CLAUDE.md) = double-check CACHE_VERSION is the HIGHEST and BOTH precache hunks survived.
  - FAILURE MODE: returning user runs an old asset against new siblings → "works for me, broken in prod".
  - WITNESS: load served page twice across a simulated version bump; assert the new asset is the one run.

- **Cross-page / cross-tab shared-state seams.** Fake: a single in-process db handle threaded between
  "pages". Real: page A persists to IDB, page B opens a NEW context and reads it (the §S-2b sidecar
  pattern: idempiere.html persists `idmp_kanban_proj`, viewer/wh_walk.js folds it).
  - GREP: `grep -rn "idmp_kanban_proj\|getItem\|setItem\|localStorage\|sessionStorage\|BroadcastChannel\|postMessage" viewer erp common`
    → every key shared across two pages is a live-only integration (version, store name, blob schema,
    write-back idempotency must all agree across the seam).
  - FAILURE MODE: A writes, B can't read (key/store/version mismatch) OR B never writes back → stale offer.
  - WITNESS: TWO `browser.newPage()` on the SAME origin — A produces, B consumes, reload empties (the
    poc_wh_pos_pick_live.js shape is the template).

- **Capability-gated APIs absent in headless.** Fake: the witness drives the manual fallback. Real device
  has/has-not the API. Suspects: `BarcodeDetector`, `getUserMedia`, `SubtleCrypto`
  (`crypto.subtle.digest`), `navigator.clipboard`, `SharedArrayBuffer`/COOP-COEP, `OffscreenCanvas`.
  - GREP: `grep -rn "BarcodeDetector\|getUserMedia\|crypto.subtle\|navigator.clipboard\|SharedArrayBuffer\|OffscreenCanvas" viewer erp common`
  - FAILURE MODE: the witness exercised the FALLBACK; the PRIMARY path (only taken on a real device) is
    unproven — or the absent-API guard is wrong (throws instead of degrading). e.g. sha256 image key
    (`SubtleCrypto`) is the live primary, the content-length stub was the headless proxy.
  - WITNESS: where the API exists in headless Chromium (SubtleCrypto does), drive the PRIMARY path; where
    it doesn't (BarcodeDetector), assert the guard DEGRADES (not throws) and the fallback reaches the
    same gate — and explicitly LOG that the primary stays device-unverified (no silent "covered").

- **PWA resume / boot-from-stale-state.** Fake: fresh context every run. Real: `pwa_last_db` /
  sessionStorage resume keys survive across opens and can point at dead URLs (the v647 self-heal bug).
  - GREP: `grep -rn "pwa_last_db\|pwa_resume\|consumeRestore\|RESTORE_KEY\|__swApplied" viewer common`
  - FAILURE MODE: a bare open resumes a deleted/stale target → bricked page. WITNESS: seed the resume key
    to a dead value, open bare, assert the self-heal redirect (`§PWA_RESUME_CLEAR`) fires ONCE.

- **Network reality: CORS, cache headers, ranged reads, base paths, MIME.** Fake: a permissive local
  static server (`MIME` map in the witness). Real: GH Pages / OCI with `nosniff`, no SharedArrayBuffer,
  relative `../buildings/` base, content-type required (OCI MIME rule).
  - GREP: `grep -rn "fetch(\|\.db\b\|buildings/" viewer erp common` for asset URLs that are absolute,
    OCI-era, or assume a base that differs live (the #272 in-repo move; the §mobile-meta-split note:
    geo-range streaming is IMPOSSIBLE on GH Pages — don't re-attempt).
  - FAILURE MODE: a fetch that 200s locally 404s/blocks live. WITNESS: serve from the path layout the
    real deploy uses; a `?db=` that 404s = the worktree predates the in-repo move (re-base).

- **Undefined-reference (no-undef) gate — the cheap whole-class catch.** Fake: `node --check` (syntax
  only) passes; lazy-loaded UMD globals are never resolved. Real: CI runs eslint no-undef.
  - RUN FIRST, it's free: `cd /tmp/wt-* && npx eslint viewer erp common` (exit 0 required). Any
    `'X' is not defined no-undef` = a lazy global (e.g. `POSCore`, `InOutConfirm`) missing from
    `eslint.globals.json` OR a genuine typo'd reference. Declare the real ones, fix the typos.
  - This gate would have caught the PR #283 lint failure pre-push. Make it step 0 of EVERY bim-ootb train.

## W-1 the audit pass (produce the suspect ledger, don't fix yet)
- For each substitution above: run its GREP, record every file:line hit in a table
  (`build/erp/LIVE_BLINDSPOT_AUDIT.md` — suspect · seam · why-headless-misses-it · proposed live witness).
- Cross-reference: which suspects already have a LIVE witness (poc_*_live.js) vs only a headless one?
  `grep -rn "puppeteer\|browser.newPage\|served" scripts/poc_*live*.js` — the gap = unguarded live surface.
- HONEST OUTPUT: a count — "N suspects, M already live-witnessed, K unguarded". No silent truncation.

## W-2..W-N one live witness per unguarded suspect (falsifier-first)
- For each K unguarded suspect, in priority order (shared-state seams + IDB first — highest blast radius):
  - Write `scripts/poc_<suspect>_live.js` on the SERVED pages (template: poc_wh_pos_pick_live.js — static
    server, real SW+IDB, §-log-first, dispatchEvent for fixed-overlay clicks, versionless IDB probes).
  - PROVE IT RED FIRST: run it against the CURRENT shipped code. If the suspect is a real bug it goes RED
    (§FALSIFIER satisfied). If GREEN: add a probe that dumps the seam's live state (the §SIDECARDIAG idiom)
    to prove the witness actually REACHED the seam before you record "no bug here".
  - Fix the smallest correct thing (prefer reusing an existing fix idiom — §KRN_PERSIST_FIX, _openCacheDB).
  - Re-run → GREEN. Read the log. Regression: the headless witness for the same surface must still pass.

## W-FINAL the train + bank
- ONE bim-ootb PR per coherent fix-cluster (don't mix IDB fixes with SW fixes). eslint step 0, witnesses
  green, sw bump once, `gh pr merge --auto --squash`, VERIFY the squash landed (orphan check).
- Bank: the audit ledger (`build/erp/LIVE_BLINDSPOT_AUDIT.md`) committed; each fix's live witness named in
  its spec §STATUS; memory `project_no_undef_gate.md` + the relevant feature memory updated with the
  live-only lesson; `prompts/FRONTEND_LANE_MASTER.md §OUTSTANDING` row per still-unguarded suspect.

## DONE WHEN
Every substitution in the list has been grepped to a suspect ledger; every unguarded suspect either has a
GREEN live witness (with a proven §FALSIFIER) or is named ⛔ with the one fact blocking it. The standing
rule lands: **`npx eslint` + a same-origin live witness are mandatory before any bim-ootb train** — the two
cheap gates that would have caught PR #283 before push. Report only the ✅ live-witnessed list + the ⛔ facts.

## NOTE ON "could Fable5 not find it?"
It could have — if asked to live-verify. It was told not to (the card delegated live-verify; user
2026-06-13 "need not test"). The fix is not "smarter model"; it is a STANDING harness reach: a model only
catches what its witnesses can execute. This card makes the live layer executable and falsifier-checked so
the blind spot is a gate, not a hope. Run it whenever a lane shipped headless-only.
