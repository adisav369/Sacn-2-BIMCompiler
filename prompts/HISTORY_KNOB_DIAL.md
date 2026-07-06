# ⚠ DO NOT REMOVE — SCOPE + WORKING RULES
# Scope: rework the in-app HISTORY UI on bim-ootb (viewer + erp/idempiere.html). Shared code in
#   bim-ootb/common/, viewer in bim-ootb/viewer/, ERP in bim-ootb/erp/. Work in a /tmp/wt-* worktree off
#   fresh origin/main (hook blocks editing ~/bim-ootb directly). §-log first (whitebox), Playwright second.
#   Clean Lucide line icons only [[feedback_pill_icon_consistency]]. Honour until ✅ DONE.
# WORKING STYLE (the user, 2026-06-10): NO decision trees / option menus — chat ONE thing at a time, build
#   → show → next. Test on LOCALHOST (give clickable http:// links via a go.html launcher), NOT deploy-per-change.

# HISTORY UI REWORK — the AMP-KNOB IS SCRAPPED (agreed with user 2026-06-10, in conversation)
# The shipped amp-knob dial (PR #230) was rejected: "hard to control / orange halo useless / no hover".
# This file REPLACES the old knob spec. New model = TWO clearly-separated histories, no knob at all.

## STATE / WHERE THINGS ARE
- **Merged on bim-ootb main:** PR #230 (the amp-knob — now being removed) + PR #231 (a test-selector fix).
- **WIP worktree `/tmp/wt-knob2` (branch `feat/knob-rework`), UNCOMMITTED, PARTIAL** — revisit against this
  plan; some of it (the "always-open dial" rewrite of history_knob.js) is now obsolete (knob is dead):
  - `common/whole_history.js` — added `{launcher:false}` mount option (suppress the bottom-left clock). ✅ keep
  - `viewer/universal_history.js` — mounts WholeHistory with `launcher:false`. ✅ keep
  - `viewer/panels.js` — added `worldHist` icon (two overlapping circles) + a **W pill** (tap → overlay). ✅ keep
  - `common/history_knob.js` — rewritten to an "always-open dial"; **knob is scrapped → DELETE this file**.
  - ERP side: nothing done yet.
- Local test: `python3 -m http.server` in the worktree + a `go.html` launcher with clickable `http://` links;
  symlink `~/bim-ootb/viewer/buildings` into the worktree → test split building `?db=buildings/Hospital_extracted.db`
  (auto-detects `_meta`+`_geo` siblings). SW caches on localhost → DevTools "Update on reload".

## THE TWO HISTORIES (the whole design)

### ① World history (cross-page) → ONE "W" pill in the pill registry
- It's the EXISTING overlay `common/whole_history.js` ("History — across pages": **Whole | This page** toggle,
  a **day strip**, one **text card per moment** = which building / which doc / which page). Tap a card =
  READ-ONLY jump, works cross-page (incl. restoring into the BIM viewer — already proven).
- **Move its launcher into the pill registry as a single "W" pill.** Icon = **two overlapping outline circles**.
  Suppress the standalone bottom-left clock (`WholeHistory.mount({launcher:false})`).
- **Tap W** = open the overlay.
- **Long-press W** = a small **drawer** with TWO items: **Z** (the per-page timeline — icon = three small
  overlapping dots, middle one FILLED black) + a **bomb** (clear history → **warning dialog** first).
- **Keep the clean TEXT cards — NO thumbnails** (user: "dont see its extra use").
- Visible **everywhere incl. the landing page** (World history is useful from anywhere; "W bar more visible,
  clickable = immediate impact"). W may keep a keyboard shortcut.

### ② Per-page micro-timeline (the bottom "Z" bar) → stripped to just dots
- **Page-only** — no world view, just THIS page's own history. (3.3)
- **No knob, no intensity, no depth.** Every meaningful act = ONE dot. BIM: any scene change / pick / section /
  grid. Doc(ERP): any new field, grid↔form view, tab switch. (3.2)
- Layout = **‹ (left)  · dots ·  › (right)** — keep simple. (3.5) ‹/› step older/newer, READ-ONLY restore.
- **Click a dot** = jump there. **Hover a dot** = the action name (tooltip).
- **Focus model** (3.4): the bar is focusable; **blue left-side highlight** when focused; **←/→ step ONLY while
  it has focus**; Tab brings focus to it; when another panel is focused, the arrows go to that panel instead.
- Icon (in the W long-press drawer) = three small overlapping dots, middle black. Shortcut stays **Z**.
- Appears **only inside a building/doc**, NOT on the landing page (nothing to step through there).

### Deleting outright: the amp-knob, Off/Low/High/Max depth, the orange halo, thumbnails.
### Keyboard: **bomb (clear) has NO shortcut** (accidental wipe). Z keeps Z. W may keep W.

## FUNCTIONAL FIXES (must land with the rework)
- **"Scene doesn't change on step" bug:** read-only nav must restore the scene. Only `pick`/`view` moments
  carry a `viewState` stamp today; OP moments (e.g. BUILDING_OPEN) don't → stepping onto them does nothing.
  FIX: stamp EVERY moment (incl. ops) — add `viewState: _tapView()` to the op `HB.push` in
  `viewer/universal_history.js _recordOp`. (`_restoreView` already applies `entry.viewState`.)
- **Duplicate "Opened…" pile-up:** the timeline persists in localStorage keyed per building
  (`bim.hist.tree.<db-url>`) and a fresh BUILDING_OPEN is appended every reload. De-dup: skip recording a
  BUILDING_OPEN when the current tip is already a BUILDING_OPEN of the same building.
- **Clear history lives in localStorage, NOT the SW cache** — "clear cache" never wipes it (this confused the
  user). The bomb clears it. Manual one-liner meanwhile:
  `Object.keys(localStorage).filter(k=>k.startsWith('bim.hist.tree')).forEach(k=>localStorage.removeItem(k)); location.reload()`
- **Recording default ON everywhere incl. mobile** — viewer currently defaults to `off` on mobile → no dots
  collect. Make recording always-on (depth concept is gone anyway).

## SURFACES / INTEGRATION NOTES (from code recon)
- Overlay: `common/whole_history.js` — API `mount/open/close/toggleOpen/setMode`; `{launcher:false}` added.
- Viewer bottom bar: `common/history_bar.js` — already has the READ-ONLY view layer from #230
  (`_viewCursor`, `viewStepBack/Front`, `viewJumpTo`, `restoreView` cfg, dots → `viewJumpTo`). Strip the knob
  mount; render `‹ dots ›` + focus + arrows; gate to in-doc; recording always-on.
- Viewer adapter: `viewer/universal_history.js` — `restoreView` wired; ADD op viewState stamp; clock launcher
  already off.
- Viewer pills: `viewer/panels.js` — `ICONS` (lines ~9–57) + inline `_actions` (lines ~1041+). W pill + icon
  added (tap only); long-press drawer (Z + bomb) TODO. Long-press helper: `pill_builder.js _revealChip(btn,id,
  iconSvg,onTap)` reveals ONE chip — for a 2-item drawer reveal two or build a tiny custom drawer.
- ERP bottom bar: `erp/idmp_history.js` — own dots bar (already read-only); remove the knob mount; same
  `‹ dots ›` model; record field/grid↔form/tab moments.
- ERP pills: `erp/pills_idmp.json` (manifest) + `erp/idmp_pills.js` (binds fn BY ID to `window.IdmpPillActions`)
  + `erp/icons.js` (add `worldHist` two-circles icon, verbatim SVG). In `erp/idempiere.html`: add
  `IdmpPillActions.worldhist = ()=>WholeHistory.toggleOpen()` (block at ~line 1273) and set the
  `WholeHistory.mount` (~line 1386) to `{launcher:false}`.
- Icons to add (clean Lucide line): `worldHist` (two overlapping outline circles), `docHist` (three small
  overlapping dots, middle FILLED black), `bomb` (clear). Add to BOTH `viewer/panels.js` ICONS + `erp/icons.js`.
- Warning dialog: no custom framework — use `window.confirm()` or a small dismissible card (pattern:
  `erp/erp_pills.js _helpGuide`).
- **DELETE** `common/history_knob.js` + its includes in `viewer/viewer.html` (`?v=…`) and
  `erp/idempiere.html`, + its tests `common/tests/{poc_knob.js,knob_harness.html}` and
  `viewer/tests/{poc_knob_viewer.js,knob_viewer_harness.html}`. Update the sw.js version notes accordingly.

## DECISIONS LOG — user's own words (2026-06-10 conversation), nothing paraphrased away
- "the look is not that well, the orange highlight not useful, there is no hover to say what each value is.
  And hard to control, rework it." → killed the amp-knob.
- "i did touch the back and front ticks it does move the dots but the scene does not change" → the op
  viewState bug (fix above).
- "the knob should not close when still using it... how do we close it?" → "i think let it start open.. it is
  not that big.." → no dot/bloom/close; always-open (then superseded entirely by: no knob).
- "and we cannot see ALL history there" → that's what the World overlay is for.
- "ClearCache does not clear history.. ideas?" → it's in localStorage, not the SW cache (bomb clears it).
- "i see the mobile style overlay as nice and it is in desktop too" (+ screenshot of "History — across pages")
  → adopt that overlay as the home of history.
- "Are those supposed to be thumbnails? ... no knob, just thumbnails ... browse not playback" → THEN later:
  "No thumbnails, dont see its extra use." → FINAL = no thumbnails; keep the clean text cards.
- "1. That major history icon should be in pill registry. Legal, consistency and clean. How about 'W' shortcut
  for 'World'? Can the icon be 2 overlapping circles with outline?"
- "2. the cross to BIM also works ... broad changes.. i.e. which building, which doc. Accepted."
- "3. the Z bottom timeline.. simple no knob.. just a series of dots clicking on any dot lead to the within doc
  history. Icon is three smaller overlapping circle dots in middle is black?"
- "3.2 No intensity just simply any change in scene, act. Same as in Doc, any new field, grid/form view, tab."
- "3.3 the micro history only that page line has no world view.. just its history that page only."
- "3.4 just keyboard arrows as shortcuts when the Z is in focus ... arrow keys left and right will immediate
  switch scene/doc action. when lost focus ... arrows are taken to that panel. Tab may bring back to Z.. thus
  some blue side highlight has to be used to indicate it is in focus. Otherwise just chose a dot. Hover may say
  the action. No thumbnails."
- "3.5 On the micro dots just have < on left and > on right.. keep simple."
- "Only have the W icon on the pill. Long press it drawer allows Z and the bomb icon to clear with warning
  dialog.. no shortcut as can accidentally hit." → bomb=no shortcut; Z still works.
- "on main landing page, at bottom it shows a series of bars... do u think that is useful?" → No — Z bar is
  per-doc; hide it on landing; only the W pill shows there. "W bar is more visible, and it clickable is
  immediate impact."

## STOP CONDITION
W pill (two circles) in the registry on BOTH viewer + ERP + landing → tap opens the across-pages overlay
(launcher clock retired); long-press W → drawer with Z (three-dots) + bomb (warning-dialog clear). Bottom Z bar =
`‹ dots ›`, page-only, in-doc only, every act = a dot, click=jump, hover=name, ‹/›+arrows step (arrows only while
focused, blue focus highlight, Tab focuses it). Scene restores on every step (op viewState stamp). Duplicate opens
de-duped. Recording on incl. mobile. Knob/depth/halo/thumbnails deleted. Witnessed §-log; localhost-tested with a
clickable go.html; then sw bump + PR + verify live. If a piece needs a user fact that can't be EXTRACTED →
`⛔ BLOCKED: <one question>` and move on.

## DEPLOY
source = bim-ootb (these are bim-ootb-native files, NOT bim-compiler/build/erp). Worktree off fresh origin/main →
bump `viewer/sw.js` + `erp/sw.js` CACHE_VERSION (history modules are network-fetched, not precached, so the `?v=`
query bump forces refetch; the CACHE_VERSION bump refreshes the precached HTML) → PR → auto-merge → verify live on
red1oon.github.io/bim-ootb/.

## ✅ DONE (PR #236, merged + LIVE 2026-06-10, sw viewer v633 / erp v630)
The amp-knob is SCRAPPED and the TWO-history model shipped on **BIM viewer + idempiere.html + landing**:
- **viewer**: W pill (`worldHist` icon) + `docHist`/`bomb` in `viewer/panels.js`; `common/history_bar.js?v=4` =
  `‹ dots ›` (read-only step, click-jump, hover, focusable→blue-edge + ←/→); `universal_history.js?v=13` (op
  viewState stamp, BUILDING_OPEN de-dup via new `HistoryBar.tipInfo()`, recording always-on incl. mobile);
  `whole_history.js?v=2` `{launcher:false}`; `w`/`z` shortcuts in scene.js.
- **idempiere.html**: W pill via `pills_idmp.json?v=27` + `erp/icons.js` (`worldHist`/`docHist`/`bomb` added);
  `idmp_history.js?v=7` reworked to `‹ dots ›`; long-press drawer `_worldDrawer` in `erp/idmp_pills.js`.
- DELETED: `common/history_knob.js` + tests (poc_knob, knob_harness, poc_knob_viewer, knob_viewer_harness,
  poc_idmp_history_knob). NEW witness `viewer/tests/poc_histbar_viewer.js` 15/15. eslint.globals.json +WholeHistory.

## §FOLLOWUP — Glassbowl + Gravity not yet done (for the NEXT session)
`erp/glassbowl.html` + `erp/glassbowl_gravity.html` STILL mount the OLD WholeHistory **clock launcher** (not the W
pill) and their `#scrub` bars were not reworked. To finish the "W pill everywhere" stop-condition:
- **Copy the idempiere wiring (PR #236 is the template):** bind a `worldhist` action → `WholeHistory.toggleOpen()`,
  set `WholeHistory.mount({launcher:false})`, long-press drawer = Z + bomb — reuse `erp/idmp_pills.js _worldDrawer`
  verbatim (it's the reusable 2-chip drawer).
- **Icons already exist in `erp/icons.js`** (`worldHist`, `docHist`, `bomb`) — REUSE, don't re-add.
- **First check:** do glassbowl/gravity run the PillBuilder registry like idempiere, or do they hand-roll their bar?
  If no pill rail, decide where the W pill hangs (or give the launcher a new home) before wiring.
- Bump `erp/sw.js` (currently v630) when it lands. Witness: a `poc_*` §-log proving the W pill mounts + the
  drawer opens on each surface, mirroring `erp/tests/poc_idmp_pills.js`.
- NOT a regression to chase here: `erp/tests/poc_pill_consistency.js` fails in its **erp.html** block (`#erp-shortcuts`
  dialog intercepts a click) — that surface is untouched by this rework and the test isn't in CI.
