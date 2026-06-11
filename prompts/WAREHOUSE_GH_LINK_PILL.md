# ⚠ DO NOT REMOVE — Scope guard / SESSION CARD: WH db → GH Pages + WH pill + POS pill + deep-link share + home nav
# Scope: FIVE dictated items (user, 2026-06-12 — POC-demo polish, WORK-TO-ZERO applies):
#   1. Serve warehouse_gardenworld.db from GH Pages → SHORT shareable URL (currently long OCI URL).
#   2. Warehouse pill on idempiere.html pill bar (second door beside the POS cart).
#   3. POS pill deploy — feat/pos-lens is BUILT+WITNESSED but ⛔ HELD; this session = the GO.
#   4. Both POS and WH have deep links for sharing; maintain the share icon in idempiere.html
#      (header-level share = current window/record link; POS share = ?lens=pos deep link;
#       WH share = viewer short URL). All via the existing navigator.share/clipboard pattern.
#   5. Home navigation chain — each sub-surface has a ⌂ home icon back to its parent:
#        erp.html ← idempiere.html (⌂ #idmp-home-btn ALREADY EXISTS — do not touch)
#        idempiere.html ← POS overlay (⌂ inside the POS panel header closes overlay)
#        idempiere.html ← WH viewer tab (⌂ in viewer HUD via ?home= param)
# READ THE LOG after every run (exit ≠ evidence). ALL poc_* via `bash build/erp/run_witness.sh scripts/poc_X.js`
#   from /home/red1/bim-compiler. EXTRACT don't invent. Witness-led: claim first, then implement.
# HOUSE RULES (each has bitten before):
#   - ~/bim-ootb is BLOCKED by a PreToolUse hook — work in a /tmp/wt-* worktree off FRESH origin/main.
#   - ONE PR in flight repo-wide. Another lane (posting-config train) may land first — rebase, and on
#     erp/sw.js conflict KEEP BOTH precache hunks, take the HIGHER CACHE_VERSION.
#   - After squash-merge VERIFY the diff landed: `git show origin/main:<file>` (PR #138/#265 orphan trap).
#   - Pills: Lucide line icons from erp/icons.js ONLY (no unicode glyphs); reuse the pill-registry
#     pattern the existing pills use (pills_idmp.json + idmp_pills.js + idempiere.html binding).
#   - Pages CI minifies sw.js — live-verify greps must be quote-agnostic (`CACHE_VERSION="vNNN"`).
#   - POS pill source is feat/pos-lens (bim-ootb 0dc3752) — cherry-pick or merge its diff, do NOT
#     re-implement (it is already built + witnessed: W-POS-RING/W-POS-WR/W-POS-BACKFLUSH/W-POS-REPLENISH).

---

## FACTS (verified 2026-06-12 — do not re-derive)

### Warehouse db
- File: `build/erp/warehouse_gardenworld.db` (61,440 B) — md5 520be9bebaa9d2941af0c11f63c1ebf7
- Currently at OCI COMMON bucket (long URL, the only OCI location):
  `https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb/o/buildings/warehouse_gardenworld.db`
- Target short URL (post-merge): `viewer/viewer.html?db=../buildings/warehouse_gardenworld.db`
  (config.js resolves `?db=` verbatim via fetch; `../buildings/` from viewer/ = repo root buildings/)
- Viewer pill gate already wired for warehouse (DATA-GATED on locator-GUID bins, `§WH PILL gate=on`).

### POS pill (feat/pos-lens — BUILT, HELD)
- Branch: `feat/pos-lens` commit 0dc3752 in bim-ootb.
- Adds: `erp/pos_lens.js` + `erp/pos_core.js` (UMD) + `pos` entry in pills_idmp.json +
  binding in idmp_pills.js + `openPosFor` in idempiere.html + `<script src="pos_lens.js?v=1">`.
- Witnesses green: W-POS-RING · W-POS-WR · W-POS-BACKFLUSH · W-POS-REPLENISH (headless, bim-compiler)
  + W-POS-LIVE (bim-ootb, poc_pos_live.js). DO NOT re-implement — cherry-pick or merge the diff.
- POS deep link param: `?lens=pos` — post-login, open the POS overlay automatically (see §STEP 3).

### Share icon (existing pattern — extend, don't fork)
- `ICONS.share` is already in `erp/icons.js` (3-circle share glyph, verbatim Lucide).
- AcctsPosted + PostingPreview panels already use `onShare: navigator.share() || status(fallback)`.
- idempiere.html header (`#idmp-header .hbtns`) has two buttons: `#idmp-switch-btn` + `#idmp-home-btn`.
  Add a third: `#idmp-share-btn` — shares the CURRENT window/record as a `?window=<id>&record=<pk>`
  deep link (same `_pendingWid`/`_pendingRid` params the page already resolves on load).
- POS share = `navigator.share({ url: idempiere.html?lens=pos })` or clipboard fallback.
- WH share = `navigator.share({ url: viewer/viewer.html?db=../buildings/warehouse_gardenworld.db })`.
- All three use the SAME one-liner pattern. No new abstraction.

### Home navigation chain (verified 2026-06-12)
- `idempiere.html #idmp-home-btn` (⌂) → `erp.html` — ALREADY EXISTS (idempiere.html line 299 / 1434).
  DO NOT TOUCH — it works.
- POS overlay → no home button yet. The overlay is opened via `cfg.overlay('POS — name', wrap)`
  inside idempiere.html. "Home" from the POS = close the overlay (return to idempiere AD main).
  Implementation: add a `⌂` button to the POS overlay's wrap header (inside pos_lens.js `open()`).
- WH viewer tab → no home button yet. Viewer opens in `_blank`; `ICONS.home` exists in panels.js
  (line 31, verbatim Lucide). Add `?home=../erp/idempiere.html` to the WH pill's `window.open` URL;
  viewer reads `_params.get('home')` from config.js and renders a small back button in the HUD.
  Repo path: `viewer/config.js` (reads params) + `viewer/main.js` or `viewer/panels.js` (renders btn).

### Icon for warehouse pill
- `box` is already in `erp/icons.js` (copied from panels.js — the Lucide `package` 3D-cube glyph).
  Use `box` as the warehouse pill icon — it IS the package/warehouse semantic, already approved.
  (Spec originally named `warehouse` as preferred but `box` is the panels.js-approved fallback and
  avoids adding an unverifiable SVG path.)

---

## STEP 1 — warehouse db to GH Pages

1. Worktree off fresh `origin/main` (`git worktree add /tmp/wt-wh-pill origin/main`).
2. Copy the byte-identical db:
   ```
   cp bim-compiler/build/erp/warehouse_gardenworld.db /tmp/wt-wh-pill/buildings/warehouse_gardenworld.db
   md5sum /tmp/wt-wh-pill/buildings/warehouse_gardenworld.db   # must == 520be9bebaa9d2941af0c11f63c1ebf7
   ```
3. Verify `.gitignore` does not exclude `buildings/*.db` (check that existing `buildings/Duplex_extracted.db`
   etc. ARE tracked — they should be).
4. Do NOT add the db to any sw.js precache list.

### Witness W-WH-GH
Copy `bim-compiler/scripts/poc_wh_live_pages.js` → `bim-compiler/scripts/poc_wh_gh_link.js`.
Change the DB const to the new short URL (post-merge live Pages URL). After Pages deploy, run it and
READ the log — expect: `§BBOX_CLEARED`, no `§BLOB_MISS`, `§WH PILL gate=on`.
§FALSIFIER: point at a wrong path → viewer reports 404 honestly (no silent cube fallback).

---

## STEP 2 — warehouse pill (icons.js + pills_idmp.json + idmp_pills.js + idempiere.html)

`box` icon is already in icons.js — no new SVG needed.

### pills_idmp.json — add after the existing `pos` entry (or at order 2.5):
```json
{ "id": "warehouse", "name": "Warehouse Walk", "key": "", "icon": "box", "order": 2.5,
  "note": "Opens the warehouse walk viewer (§S-1 compiled GardenWorld db, short GH Pages URL). Static demo door — no data gate (the walk pill inside the viewer already gates on locator bins). share = navigator.share the viewer URL." }
```

### idmp_pills.js — add in the `IdmpPillActions` block (same pattern as all others):
```js
warehouse: function () {
  var url = '../viewer/viewer.html?db=../buildings/warehouse_gardenworld.db';
  window.open(url, '_blank');
  console.log('§WH-PILL opened url=' + url);
},
warehouseShare: function () {
  var url = location.origin + location.pathname.replace('idempiere.html','') +
            'viewer/viewer.html?db=../buildings/warehouse_gardenworld.db';
  if (navigator.share) navigator.share({ title: 'Warehouse Walk', url: url });
  else { try { navigator.clipboard.writeText(url); } catch(e){} }
  console.log('§WH-SHARE url=' + url);
}
```

### Witness W-WH-PILL (mirrors poc_pos_live.js pattern):
`§WH-PILL present icon=box` · pointerup fires `IdmpPillActions.warehouse` · opens URL (no 404) ·
§FALSIFIER: pill absent from a page that doesn't register it.
Run `bash build/erp/run_witness.sh scripts/poc_wh_pill.js` + READ the log.

---

## STEP 3 — POS pill deploy (cherry-pick feat/pos-lens diff) + ?lens=pos deep link

### 3a. Merge/cherry-pick
From the worktree, merge the diff from feat/pos-lens. The files added are:
- `erp/pos_lens.js` (the lens UI — dumb terminal, EXTRACT don't rewrite)
- `erp/pos_core.js` (engine glue UMD — already in bim-compiler/build/erp/pos_core.js)
- Pills manifest entry `pos` (shopping-cart icon, `showWhen: pos-station`)
- `idmp_pills.js` binding for `pos` + `posShare`
- `idempiere.html`: `<script src="pos_lens.js?v=1">` + `openPosFor` binding

Do NOT re-implement. If cherry-pick conflicts, resolve by keeping feat/pos-lens's version of those files.

### 3b. ?lens=pos deep link
idempiere.html already resolves `?window=<id>` and `?record=<pk>` post-login via `_pendingWid`/`_pendingRid`.
Add the same pattern for `?lens=pos`:
```js
var _pendingLens = _params.get('lens') || null;   // near _pendingWid declaration (line ~370)
// in doLogin(), after the menu builds (same location as _pendingWid resolution):
if (_pendingLens === 'pos') { _pendingLens = null; IdmpPillActions.pos && IdmpPillActions.pos(); }
```

### 3c. posShare binding in idmp_pills.js:
```js
posShare: function () {
  var url = location.href.split('?')[0] + '?lens=pos';
  if (navigator.share) navigator.share({ title: 'POS — Garden User · Store', url: url });
  else { try { navigator.clipboard.writeText(url); } catch(e){} }
  console.log('§POS-SHARE url=' + url);
}
```

### Witness W-POS-LIVE (re-run, not re-write):
`bash build/erp/run_witness.sh scripts/poc_pos_live.js` — must still exit 0 after the cherry-pick.
READ the log. Also verify `?lens=pos` opens the POS surface in a local browser smoke test.

---

## STEP 4 — share icon in idempiere.html header

The header `.hbtns` span currently has two buttons: `#idmp-switch-btn` + `#idmp-home-btn`.
Add a third button using `ICONS.share`:

```html
<button id="idmp-share-btn" title="Share this page"></button>
```

Wire in JS (near the switch/home-btn event listeners):
```js
document.getElementById('idmp-share-btn').innerHTML = _svg('share');
document.getElementById('idmp-share-btn').addEventListener('pointerup', function () {
  var url = location.href.split('?')[0];
  var q = [];
  if (_curWin) q.push('window=' + _curWin.id);
  if (_curRec) q.push('record=' + _curRec);
  if (q.length) url += '?' + q.join('&');
  var title = (_curWin ? _curWin.name + ' — ' : '') + 'iDempiere-like';
  if (navigator.share) navigator.share({ title: title, url: url });
  else { try { navigator.clipboard.writeText(url); } catch(e){} }
  console.log('§IDMP-SHARE url=' + url);
});
```

(Read idempiere.html first to confirm the variable names `_curWin`/`_curRec` — adjust if different.)

No witness needed for share button alone — it is a UI wire (Playwright-tier); `§IDMP-SHARE` §-log line
in the console is the whitebox proof (read it in the browser DevTools after a manual tap).

---

## STEP 5 — home navigation (⌂ in POS overlay + ⌂ in WH viewer)

### 5a. POS overlay home button
Inside `pos_lens.js` `open()`, the overlay `wrap` div is built before calling `cfg.overlay(title, wrap)`.
Add a home button to the top of `wrap`:
```js
var homeBtn = document.createElement('button');
homeBtn.title = 'Back to iDempiere';
homeBtn.innerHTML = _svg('home');   // ICONS.home is already in erp/icons.js
homeBtn.style.cssText = 'position:absolute;top:8px;right:42px;background:none;border:none;' +
  'color:#7fd6e0;cursor:pointer;padding:4px;opacity:.7';
homeBtn.addEventListener('pointerup', function () { cfg.close && cfg.close(); });
wrap.style.position = 'relative';
wrap.insertBefore(homeBtn, wrap.firstChild);
```
(Read pos_lens.js `open()` first to confirm how `cfg.overlay` / `cfg.close` are exposed; adjust
 if the overlay API uses a different close method — e.g. the overlay element's own close button.)
No separate witness needed — `§POS-HOME` console.log in the handler; verify by tapping in the browser.

### 5b. WH viewer home button (via ?home= param)
**WH pill URL** — change the `window.open` call to include the home param:
```js
// in idmp_pills.js IdmpPillActions.warehouse:
var homeUrl = encodeURIComponent(location.origin +
  location.pathname.replace(/[^/]*$/, '') + 'idempiere.html');
var url = '../viewer/viewer.html?db=../buildings/warehouse_gardenworld.db&home=' + homeUrl;
window.open(url, '_blank');
```

**viewer/config.js** — read the param (add after the existing `A.RECORD_ID` line):
```js
A.HOME_URL = _params.get('home') || null;
```

**viewer/main.js or panels.js** — render the button once the viewer is ready (after the HUD mounts).
`ICONS.home` is already in panels.js line 31, so use `A.icon('home', {...})`:
```js
if (A.HOME_URL) {
  var homeBtn = A.icon('home', { size: 22, title: 'Back to iDempiere',
    onClick: function () { location.href = A.HOME_URL; }
  });
  homeBtn.style.cssText = 'position:fixed;top:10px;left:10px;z-index:900;' +
    'background:rgba(0,0,0,0.45);border-radius:8px;padding:5px;cursor:pointer';
  document.body.appendChild(homeBtn);
  console.log('§WH-HOME rendered href=' + A.HOME_URL);
}
```
(Read main.js + panels.js first to find the correct post-HUD-mount hook and confirm `A.icon` signature.)
Witness line `§WH-HOME rendered href=...` appears in the console — read it in the browser DevTools.

---

## STEP 7 — deploy train (serial, standard)

1. Rebase off fresh `origin/main` inside the worktree.
2. Bump `erp/sw.js` CACHE_VERSION (next integer). Also bump `?v=` query on any new/changed `.js` files
   in idempiere.html `<script>` tags. `buildings/warehouse_gardenworld.db` is DATA — do NOT precache it
   in sw.js (data-fetched only; precaching it would bloat the install payload).
3. Re-run W-WH-PILL + W-POS-LIVE on the bumped tree → both exit 0, READ logs.
4. `git add` the touched files; commit; `gh pr create`; `gh pr merge --auto --squash`.
5. Orphan check: `git show origin/main:erp/sw.js` (CACHE_VERSION visible) + `git show origin/main:buildings/warehouse_gardenworld.db` (exists, ~61 KB).
6. Wait for Pages deploy; run W-WH-GH against the LIVE short URL → READ log.

---

## STEP 8 — one canonical copy + bookkeeping

- After W-WH-GH passes LIVE: delete the OCI object `buildings/warehouse_gardenworld.db` from the
  COMMON bucket. Verify the old OCI URL now 404s; the new short URL still passes.
- Update `bim-compiler/scripts/poc_wh_live_pages.js` DB const to the new short URL.
- Update `prompts/FRONTEND_LANE_MASTER.md` SESSION HANDOFF block: replace the OCI deep link with
  the short one; mark both original dictated items `✅ DONE (W-WH-GH / W-WH-PILL)`.
- Update `docs/ERPUserGuide.md` §7 WH section: replace the OCI URL example with the short one,
  add a note that the POS pill and WH pill are both live.

---

## DONE WHEN
- W-WH-GH §-lines read from log (short URL serves live) ✅
- W-WH-PILL exit 0 (warehouse pill present, opens viewer) ✅
- W-POS-LIVE exit 0 (POS pill present after cherry-pick) ✅
- `?lens=pos` lands on POS surface post-login (browser smoke) ✅
- `§IDMP-SHARE` visible in console on header share tap ✅
- `§POS-HOME` in console when ⌂ tapped in POS overlay (browser smoke) ✅
- `§WH-HOME rendered href=…` in console when viewer opens with ?home= (browser smoke) ✅
- idempiere.html `#idmp-home-btn → erp.html` unchanged and still works ✅
- OCI duplicate deleted, old URL 404s ✅
- lane-master + poc_wh_live_pages.js + ERPUserGuide updated ✅

Anything needing a user fact → `⛔ BLOCKED: <one question>`, move on.
