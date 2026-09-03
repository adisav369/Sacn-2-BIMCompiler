# ⚠ DO NOT REMOVE — Scope guard
# Scope: take Phase A LIVE. The edge primitives (A1–A4) are PROVEN + PUSHED as dormant modules
#        (bim-ootb commit 2ce30b2 on s284e-b-inplace-viewer). This prompt = the deploy/wiring tail (A5
#        verify + A6 live), which is the ONLY externally-coupled, EXPLICIT-GO work left. Read the log
#        after every run; §-log first, Playwright for WIRING only. T3 editing/CRUD stays parked.
# Predecessor (DONE): prompts/ERP_PHASE_A_SETTLE.md §DONE — A1–A4 PASS, A5 wired, with §-witnesses.

---

# Phase A — Deploy tail (what is next)

## State at handoff (2026-06-01)
- **PUSHED (dormant, NOT live):** bim-ootb `2ce30b2` (branch `s284e-b-inplace-viewer`) =
  `kernel_ops.js` (A1 op_uuid), `erp_signer.js` (A2), `erp_replay.js` (A3), `erp_persist.js` (A4).
  Browser-pure, window-only, unreferenced → no behaviour change until wired. CI is green (audits are
  forward-checks; no orphan rule). **No open PR → the ci.yml auto-merge-to-main is NOT armed.**
- **APPLIED in the working tree, NOT committed:** the `erp.html` wiring (MODULES += the 3 modules;
  `ErpSigner.installSigner` + `ErpPersist.requestPersist` on load; `window.__erpDb`; the A5
  `⛓ Verify ledger` pill + `window.ErpVerifyLedger`). It is uncommitted because `erp.html` ALSO holds
  another session's `#gbviews` WIP (hunks ~55–96) + `ad_*`/`panels`/`tools` WIP. All 5 inline scripts
  parse; placement is clear of #gbviews.
- **Witnesses (bim-compiler):** `scripts/test_kernel_{identity,sign,owner,persist}.js` +
  `test_kernel_chain.js` (regression) — all §-PASS; logs in `build/erp/`.
- **In-browser check DONE locally (headless chromium, served viewer/):** logs
  `build/erp/erp_check.log` + screenshot `erp_check.png`/`erp_check2.png`. All 4 modules fetch; on load
  `§KERNEL_OPS_LOADED v6`, `§SIGN_LOADED/§OWNER_LOADED/§PERSIST_LOADED`, `§SIGN installed
  alg=ECDSA-P256 … custody=idb-nonextractable`, `§VERIFY_LEDGER control mounted`; the pill REAL-CLICK=OK
  → `§VERIFY_LEDGER ok=true len=0` (green "Ledger OK — 0 ops sealed" toast). hydrate 1316ms, no page
  errors. So step 6 (wiring proof) is essentially satisfied pre-deploy; re-confirm on the live URL.
  - **Fix applied (working tree):** the Verify-ledger pill z-index 11→9999 — the constellation canvas
    was intercepting pointer events; with 9999 the real click lands (re-verified). Rides the co-commit.
  - **Caveat:** headless logs `§PERSIST persisted=false` (Chromium denies durable storage without
    engagement/install) — expected; a real engaged browser grants it. Confirm on the live URL.

## The deploy is EXPLICIT-GO and externally coupled — do NOT fire it casually
A push to `s284e-b-inplace-viewer` is harmless TODAY (no PR). But opening a PR + green CI →
**auto-merge --squash to main → deploy-pages.yml → LIVE**. So treat "open the PR" as the deploy trigger.

## Ordered steps
1. **Coordinate erp.html.** Confirm the #gbviews/`ad_*` session is ready to commit. The Phase-A wiring
   must co-commit with that work (same file) — OR cleanly separate it. Do NOT bundle unfinished WIP.
2. **Rebase** `s284e-b-inplace-viewer` on `main` (PR #77 sw v557 + PR #78 no-undef eslint gate are live).
3. **package.json:** delete the local untracked `package.json`/`package-lock.json`; take main's superset
   (sql.js ^1.14.1 + eslint/globals). Nothing lost.
4. **no-undef gate** (now on main's ci.yml) on the changed non-`ad_` files — `kernel_ops.js`,
   `erp_signer.js`, `erp_replay.js`, `erp_persist.js`. They are browser-pure (no `module` global) and
   use only globals `kernel_ops.js` already uses + standard ES (Promise/Error/parseInt). Add any
   missing NAME to `eslint.globals.json` (the 140-name whitelist). **Never add `module`.**
5. **SW bump (T13):** `sw.js CACHE_VERSION` is `v538`; bump it AND `erp.html`'s `sw.js?v=508` together
   so they match (they currently do NOT — fix as part of this deploy). Confirm `audit_sw_precache.js`
   + T13 pass.
6. **In-browser proof (Playwright WIRING only — values are already proven in Node):** scripts load;
   `§KERNEL_OPS_LOADED v6`, `§SIGN_LOADED`, `§OWNER_LOADED`, `§PERSIST_LOADED` emit; on load
   `§SIGN installed …`, `§PERSIST persisted=true`, `§VERIFY_LEDGER control mounted`; the pill mounts +
   click fires `§VERIFY_LEDGER ok=… len=…`. Tamper a row → pill shows `Tamper at op N`.
7. **Visual-confirm the `⛓ Verify ledger` pill** with the user (placement/style) BEFORE merge.
8. **Deploy:** open the PR → green CI auto-merges to main → deploy-pages → live. Then smoke-test the
   live URL's §-lines + fetch-back. (CLAUDE.md Deploy Flow = ONE flow, never stop partway.)

## Done when
The live erp.html emits the A1–A5 §-lines; verify-ledger shows OK on a clean log and the break on a
tampered one; `node deploy/dev/tests/audit_specs.js` exits 0 if any Playwright spec was touched.

# DONE — Phase A LIVE (2026-06-01)
Deployed via clean cherry-pick, NOT a rebase of `s284e-b-inplace-viewer`. That branch carried 55
commits (18 touching `city.js` = S285 work already squash-merged to main); rebasing replayed all 55
with conflicts. Instead: branched `feat/erp-phase-a-live` off current `main` and cherry-picked the 3
Phase-A commits (W-CHAIN kernel_ops + 3 modules + the erp.html/#gbviews co-commit) → zero conflicts,
only the intended 11 files vs main. Correction to handoff: main never had `#gbviews` in erp.html
(#66/#74 were the main-viewer HUD), so no duplication.

- Co-commit (step 1): erp.html Phase-A wiring + #gbviews/PB-bridge (ad_*, ad_table_map.js,
  schema_5table.sql, manifest.json) — user confirmed #gbviews ready.
- package.json (step 3): local untracked dropped; main's superset in tree.
- no-undef gate (step 4): `npx eslint viewer` exit 0. New modules use only whitelisted globals
  (`KernelOps` + browser/ES). No globals.json edit; `module` never added.
- SW bump (step 5): sw `v557→v558`, erp.html `?v=508→558` (T19a intent: match=YES via manual check;
  the T19 test itself has a pre-existing path bug — reads `../kernel_ops.js` not `../viewer/` — same
  on main, untouched). `audit_sw_precache` 103/103. module `var V=?v=22` (T19b ≥20 OK).
- CI (step 6/8): full local `fast-checks` 11/11 green → pushed → PR #79 → `fast-checks` PASS +
  `e2e-tests` (Playwright golden-path) SUCCESS → auto-squash to main 00:51:37Z → deploy-pages run
  26729514477 success.
- LIVE smoke (§ evidence, `build/erp/phaseA_live_smoke.log`): live sw `CACHE_VERSION='v558'`;
  erp.html `sw.js?v=558`; erp_signer/replay/persist/kernel_ops all HTTP 200; live source carries
  `§KERNEL_OPS_LOADED v6`, `§SIGN_LOADED`, `§OWNER_LOADED`, `§PERSIST_LOADED`; A5 pill + `§VERIFY_LEDGER
  control mounted`. URL https://red1oon.github.io/bim-ootb/viewer/erp.html
- Backup tag `phaseA-pre-rebase-backup` (bim-ootb) + original `s284e-b-inplace-viewer` (origin) intact.
- OPEN for user: live engaged-browser visual confirm — pill placement/style + runtime `§VERIFY_LEDGER
  ok=true` + `§PERSIST persisted=true` (headless denied durable storage; a real engaged browser grants
  it). `feat/erp-phase-a-live` branch still on origin — delete after confirm if desired.

## After Phase A — Phase B (separate, parked here)
A3's guarded replay (`erp_replay.js`) is LOADED but not yet INVOKED on a live document flow — the
read-only AD page emits no CREATE/ALLOCATE/CLAIM ops. Phase B (operational surfacing) wires real ERP
document actions through `ErpReplay.replayGuarded`. See prompts/ERP_DEPLOY_AND_TOUR.md (Phase C = Tour).
