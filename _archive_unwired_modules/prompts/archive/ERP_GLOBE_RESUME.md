# ⚠ DO NOT REMOVE
**Scope:** erp.html "instant globe" + idempiere.html deep-link work (bim-ootb/erp/). Resume card for a
new session. **Read the §-log after every run; exit code is not evidence.** Edit the ERP app ONLY in a
fresh `/tmp/wt-*` worktree off `origin/main` (a PreToolUse hook blocks `~/bim-ootb/` edits). Honour until
every loose end below is ✅ or ⛔.

---

## WHAT SHIPPED 2026-06-10 (all on bim-ootb `main`, GH Pages, sw **v622**)
- **#227 (v621) §IDLE-GATE** — loose end #1 DONE. Globe rAF redrew at 60fps with no idle/visibility gate
  (battery hog). `ad_graph.js _animate` now ALWAYS reschedules rAF (no freeze risk) but returns early on
  `document.hidden` + throttles the canvas draw to ~18fps when idle, 60fps while active. `active = fly||drag||
  |momentum|>1e-4||_focusPulseT>0||(now-_lastActivity)<1200`; `_lastActivity` stamped in pointerdown/wheel/
  touchstart/touchmove. New `getDrawCount()` witness. §IDLE-GATE PASS: idle 60→15fps (4× save), interaction 60fps.
  `ad_graph.js?v=26`.
- **#228 (v622) §IDEMPIERE-ROUTE cleanup** — loose end #2 DONE + fixes user's "back-from-idempiere shows old
  bottom-button format" AND "Share icon gone". The emoji bottom bar (`_renderBottomNav`) + in-app grid stayed
  reachable via `openWindow` (FTS/recent/`?window=` deep-link restore) → repainted the deprecated UI (and
  obscured the clean pill bar incl. Share). Fix: `showMenu` always renders globe-only; `openWindow` forwards to
  `_openInIdempiere`; erp.html `_restoreDeepLink` forwards `?window=/?record=` → idempiere.html; `_renderBottomNav`
  + `_navHandler` DELETED; `_currentScreen` never set to 'window' → grid island unreachable. erp.html = bubbles+pill
  ONLY. **idempiere.html UNTOUCHED** (its Accts-Posted `onShare` is the share affordance there — separate, intact).
  §T1 clean-globe (bottomNav 0 children, Share pill present), §T2 `?window=143`→idempiere.html, §IDLE-GATE regression PASS.
  `ad_ui.js?v=28`. **Dead-code left (unreachable):** in-app grid island `_renderWindow/_loadTabRecords/_openAccordionPanel/
  _renderKPICards/_renderMenuNodes/_showRecordList/_createNewRecord/_showCharts/_showMore` — never entered (kept to
  keep the diff safe); source-delete is optional hygiene.

## WHAT SHIPPED PRIOR SESSION (sw **v620**)
- **#222 (v616) §INSTANT NO-FREEZE** — the globe froze ~1–5s after the bubble burst. Cause (measured):
  FTS5 index built 10,561 rows in ONE synchronous main-thread block (1061ms). Fix: `erp_search.buildIndexChunked`
  (one table/macrotask + BEGIN/COMMIT, 1006→338ms), deferred off hydrate via `requestIdleCallback`; stopped the
  `_dbReady→ADGraph.init` rebuild so the `initFromBubbles` skeleton stays authoritative. Witness: 0 main-thread stalls.
- **#223 (v617) drill-DB reattach** — v616 regressed (tap→blank). `initFromBubbles` nulls the graph's `_db`;
  re-attach with `graphHydrate(_db)` right after the two post-hydrate `initFromBubbles` calls. Witness: §BUILD_ENTITY records=18.
- **#225 (v619) §IDEMPIERE-ROUTE** — globe is now a LAUNCHER. **Long-press a bubble → real `idempiere.html`**
  (`_openInIdempiere` in ad_ui.js builds `idempiere.html?window=<id>[&record=<pk>]` + navigates). idempiere.html
  gained `?record=<pk>` deep-link (`_pendingRid`→`_landOnRecord`, survives login; "NOT in scoped set"→honest blank
  on wrong client/role). **Tap still drills record-bubbles** (`showEntity`, unchanged). §PRICE-LABEL: M_ProductPrice
  bubbles → "Product · <PriceList Year>" (entity label cap 16→24). Help card gained the long-press/login note.
- **#226 (v620) §HELP-SPLIT** — the **lifebelt (lifeBuoy)** was hijacked for the bubble HelpGuide. Restored:
  `(?)` `guide` pill (circleHelp) → `_helpGuide` (bubble navigation); lifebelt `help` pill → new `_shortcutsPanel`
  (keyboard/gesture legend, its original BIM `showCommandPalette` role).

**DONE + verified (no action):** Leaf (record) bubble long-press → single record (form via `?record=`);
table bubble long-press → window grid. Globe + idempiere core are precached → **offline-able** (SQLite-WASM + IndexedDB seed).

---

## LOOSE ENDS (work top-to-bottom)

### ✅ 1. CPU idle-gate — DONE 2026-06-10 (#227 v621, §IDLE-GATE PASS). See "WHAT SHIPPED" above.
### ✅ 2. Hard-remove dormant in-app table view — DONE 2026-06-10 (#228 v622). Made unreachable (bottom-nav DELETED,
###    openWindow + deep-link forward to idempiere.html). Optional residual: source-delete the now-dead grid island.
### ✅ "Share icon gone" — INVESTIGATED + RESOLVED by #228. Share pill always renders on the clean erp.html bar
###    (it sits behind the ⋯ collapsed dock); the old grid bug had been obscuring it. idempiere.html's share =
###    contextual Accts-Posted `onShare` (line ~982), not a pill — UNTOUCHED.

<details><summary>Original loose-end #1 detail (for reference)</summary>

### 1. ⚡ CPU idle-gate (HIGH — measured battery hog, NOT yet fixed)
`ad_graph.js _animate` clears+redraws the whole canvas **60fps with NO idle gate and NO visibilitychange gate**;
node pulse keeps it "dirty" forever. Measured: **60.3 fps idle**, JS heap 35.6MB (+ sql.js wasm ~15–25MB for the
12.7MB seed; RAM bounded, not leaking). `_autoSpin=0` (no spin) — pure waste redrawing a static scene.
- **Low-risk fix (recommended, keeps the "alive" pulse):** in `_animate` (reschedule at **ad_graph.js:1224**),
  always reschedule rAF, but `if (document.hidden) return;` before draw, and throttle the draw to ~18fps when idle:
  `active = _flyTarget || _dragging || Math.abs(_momentumY)>1e-4 || Math.abs(_momentumX)>1e-4 || (now-_lastActivity<1200)`;
  set `_lastActivity = performance.now()` at the top of `_onPointerDown` (1390), `_onWheel` (1601), `_onTouchStart`
  (1613), `_onTouchMove` (1623). No loop-stop = no freeze risk.
- **Max-battery alt (behaviour change):** fully STOP the loop when idle + resume on interaction — but the perpetual
  pulse freezes when untouched. **Ask the user** (aesthetic vs battery) before choosing the full-stop.
- Witness: re-run `/tmp/test_cpu.js`-style idle frame-count → expect ~18fps idle / 60fps while interacting; drill + zoom still smooth.

### 2. Hard-remove the dormant in-app table view (the "archive" step the user agreed to)
Drill now routes to idempiere.html, so the in-app record UI in `ad_ui.js` is UNREACHABLE: `_openAccordionPanel`
(+ its `_onKeyDown`/accordion helpers) and the in-app `openWindow`/grid renderer. Remove them. **KEEP** `showEntity`
(tap→bubbles), `_openInIdempiere`, `_recPk`. Verify nothing else calls the removed fns (grep first).

</details>

### 3. Offline-hardening (deferred this session, per user "note to assign a dedicated session")
4 non-critical chrome scripts aren't precached → fail offline: `help_overlay.js`, `help_idmp.js` (in `erp/` scope —
**easy add** to `PRECACHE_ASSETS`), and `../common/whole_history.js` + `../common/history_tap.js` (from #224 — **OUT of
`erp/` service-worker scope**, cache.add can't reach above scope → needs a deliberate approach: relocate/copy into
`erp/`, or a second SW scope). Records/login already work offline without them.

### 4. Strip debug aids (optional, harmless)
Added for testing, shipped: `ad_graph.js` `ADGraph._debugNodes()` accessor + `§BUILD_ENTITY` extra `nameCol=`/`labels=[…]`
log fields. Strip if undesired (they aid future tests).

### 5. ⛔ DECISION — long-press on EMPTY space → smart search
Currently intentional (`§TAP longPressEmpty → search`). User noticed it; undecided whether to keep or make it inert.

---

## HOW-TO (this surface, learned the hard way)
- **Edit in a fresh `/tmp/wt-*` worktree off `origin/main`** (hook blocks `~/bim-ootb/`). **Re-apply hunks with Edit;
  do NOT copy whole files** — other sessions advance `main` under you (this session: #224 whole-history landed mid-work).
- **`sw.js` is the conflict magnet** — bump `CACHE_VERSION` (now v620) with a prepended note; take the HIGHER version on conflict.
- **Bump `?v=` in erp.html** for every changed JS (ad_graph/ad_ui/erp_pills) + `pills.json?v=` inside erp_pills.js,
  else the service worker serves the stale cached asset (this caused a "deprecated table on long-press" false alarm).
- Tests used: `/tmp/test_drill.js`, `/tmp/test_longpress2.js`, `/tmp/test_prices.js`, `/tmp/test_help3.js`, `/tmp/test_cpu.js`
  (Playwright via `/home/red1/bim-ootb/tests/node_modules/playwright`; serve the worktree's `erp/` with a python http.server
  that sets `.wasm`/`.js`/`.db` MIME). Witness via §-log, not pixels.
- Worktrees from this session (all branches MERGED, safe to `git worktree remove`): `/tmp/wt-erp` (old base, STALE ?v —
  don't deploy from it), `/tmp/wt-deploy` (#225), `/tmp/wt-help` (#226). For new work start a brand-new worktree off `origin/main`.
