# ⚠ DO NOT REMOVE — Scope: S284c deep + complete offline-import test coverage
# Read the log after every run. §-tagged runtime logs are the only proof. No guessing.

## Why this prompt exists

The PWA + standalone HTML offline IFC-import path keeps breaking in ways that
ONLY surface at runtime, offline, in a real browser — never in unit checks:

- Hardcoded CDN fetch in `import_worker.js` (web-ifc JS + wasm from unpkg)
- DB-build sql.js loaded from `sql.js.org` CDN on the landing page
- A wasm Blob URL minted on the MAIN thread is NOT fetchable from a null-origin
  `file://` blob worker (latent bug shipped in PR #37, only caught when the
  sandbox finally had a local wasm to exercise)
- The landing page is ROOT scope; the SW is `viewer/` scope → the page is
  uncontrolled (`navigator.serviceWorker.controller === null`). The IMPORT WORKER
  is controlled (its script lives under `viewer/`), but main-thread fetches are
  NOT. Cache Storage (`caches.match`) is the only offline-safe main-thread read.

Each fix was real but PARTIAL — every time, one more network dependency hid
behind the last. **It is bound to hit again.** The job for this session is to
make the offline path provably airtight with deep, exhaustive tests so the next
regression fails CI instead of a user's browser.

## Current state (as of 2026-05-29, commit on main via PR #40)

WORKING + proven by `tests/specs/s284-offline-pwa-ifc.spec.js` (Playwright,
`context.setOffline(true)`):
- §IMPORT_SAVED elements=130 OFFLINE
- §WASM_LOCATE → lib/web-ifc.wasm (local, SW-cached)
- §WORKER_SRC local lib/web-ifc-api-iife.js
- §SQL_FROM_CACHE / §SQL_LOCAL (sql.js from viewer/lib + Cache Storage)

Files in the offline path (all on main):
- `viewer/import_worker.js` — web-ifc importScripts local-first (CDN fallback);
  wasm locateFile: in-worker base64 decode (standalone) → `lib/` (PWA)
- `index.html` `_loadSqlJs` + `_fetchLocalOrCache` — local sql.js + Cache Storage
- `index.html` — landing registers viewer SW (`§SW_REG_LANDING`)
- `viewer/sw.js` — `web-ifc-api-iife.js` + `web-ifc.wasm` in LOCAL_LIBS, v526
- `viewer/lib/web-ifc-api-iife.js` (6MB) + `web-ifc.wasm` (1.3MB) committed
- `viewer/viewer.html` — SW reg ?v=526

## DO — build the deep offline test matrix

Spec-first. Each test NAMES the network dependency it proves dead. Add to
`tests/specs/` and wire the offline ones into CI (`.github/workflows/ci.yml`
e2e job) so they gate merges. Run `node deploy/dev/tests/audit_specs.js`-equiv
(`tests/audit_*.js`) after spec changes.

1. **No-CDN static audit (fast-check, no browser).** New `tests/audit_no_cdn_import.js`:
   grep `import_worker.js`, `mesh_import_worker.js`, `ifc_export_worker.js`,
   `index.html` `_loadSqlJs`, `import_db_builder.js` for `unpkg.com`,
   `sql.js.org`, `jsdelivr`, `cdnjs`, `cdn.` in the IMPORT path. Any CDN URL that
   is not inside a documented `catch`/fallback branch → FAIL. This is the
   regression tripwire — it would have caught every past break at commit time.
   Add to CI fast-checks.

2. **PWA offline — cold cache, multiple buildings.** Extend
   `s284-offline-pwa-ifc.spec.js`: parametrize over ≥3 fixtures of different
   sizes/IFC schema versions (small Clinic, medium Vogel, a large one). Each:
   online load → precache → setOffline → import → §IMPORT_SAVED + element count
   matches a known witness. Assert ZERO `unpkg`/`sql.js.org` in §WASM_LOCATE and
   no `NetworkError`/`both async and sync` in console.

3. **PWA offline — never-visited-viewer path.** Prove the landing-only user
   (never opened the viewer) can still import offline after one online landing
   visit. (This is the real cold-start UX.)

4. **Standalone `file://` — network fully blocked.** Extend
   `test_s284b_sandbox.js`: use Playwright route interception to BLOCK unpkg +
   sql.js.org entirely (not just setOffline), open the packaged HTML from
   `file://`, drop IFC, assert §IMPORT_SAVED + §WASM_LOCATE shows in-worker blob.
   This proves true zero-CDN, not "offline but cache warm."

5. **mesh import worker** (OBJ/DAE/GLB/STL/FBX route) — does it pull any CDN?
   Audit + one offline mesh-import test. The IFC path is fixed; the mesh path
   may still have a hidden fetch.

6. **IFC export worker** (`ifc_export_worker.js`) — it also `importScripts`
   web-ifc. Prove export works offline (or document it as online-only with a
   §log + graceful message).

7. **Online regression** — golden-path stays 4/4; add an assertion that online
   import uses LOCAL `viewer/lib` (not CDN) so "works online" can't mask a
   reintroduced CDN dep.

## DON'T
- DON'T add value-verification via Playwright where a `§` log + Node check works.
  Browser tests here are justified ONLY because offline behavior is the issue.
- DON'T touch city files (`viewer/city.js`, city specs) — a separate concurrent
  session owns S285 city work on the SAME branch. Cross-committing already
  happened once (a `git add` swept shared `sw.js`); prefer a dedicated branch.
- DON'T bump CACHE_VERSION without syncing every `sw.js?v=N` registration
  (`viewer.html`, `erp.html`, `boq_charts.html`, `2d.html`) — they're currently
  inconsistent; T19a in `test_s262_crud.js` checks erp.html only.

## Witness claims
| ID | Claim | Proof |
|----|-------|-------|
| W-284c-1 | No CDN URL in IFC import path outside fallback | audit_no_cdn_import.js exit 0 |
| W-284c-2 | PWA imports IFC offline, ≥3 buildings | §IMPORT_SAVED ×3, element counts match |
| W-284c-3 | Landing-only user imports offline | §IMPORT_SAVED after landing-only visit |
| W-284c-4 | Standalone parses IFC with CDN route-blocked | §WASM_LOCATE in-worker blob + §IMPORT_SAVED |
| W-284c-5 | Mesh + export workers offline-audited | audit + §logs |
| W-284c-6 | Online import uses local lib, not CDN | golden-path §SQL_LOCAL/§WASM_LOCATE local |

## First action
Run the existing proof to confirm green baseline, then write audit (#1):
```
cd /home/red1/bim-ootb/tests && npx playwright test --project=desktop s284-offline-pwa-ifc
node tests/test_s284b_sandbox.js && node tests/test_s284b_full_package.js
```
Live production sanity (deployed v526):
```
node /tmp/live_offline_test.js   # online→offline import against red1oon.github.io
```
