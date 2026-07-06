# ⚠ DO NOT REMOVE — RESUME CARD: the WHOLE-history cross-page TIME-scroller
# Scope: build the unified, timestamped, cross-page history — ONE log every surface contributes to, surfaced as
#        a cross-page bar, scrubbed by TIME: long-press-back = step by DAY → land on a STRETCH (a day/session
#        bucket) that is SCROLLABLE across ALL timelines. NON-DISRUPTIVE + ADDITIVE: each page KEEPS its own
#        local bar + recording; this adds the AGGREGATE on top — no page loses its scrubber.
# Discipline: whitebox §-log FIRST (READ the log after every run; exit code is NOT evidence). Deterministic —
#        the real recorded `ts` (Date.now at record-time), INTEGER ms, no invented timestamps. EXTRACT, don't
#        invent. Build on the SHARED primitives (history_tap.js field/currentView/applyView + history_bar.js
#        _mirror) — do NOT fork them. Honour until ✅ DONE.
# Lane: bim-ootb (common/ · erp/ · viewer/). Editing ~/bim-ootb is hook-blocked → work in a /tmp/wt-* worktree,
#        deploy via the owning app's sw.js CACHE_VERSION bump. The ERP app owns its own erp-ootb- caches.

---

## ✅ DONE — W1·W2·W3 ALL SHIPPED (2026-06-10, bim-ootb PR #224 → origin/main a7f35f1, sw viewer v631 / erp v618)
- **W1 unify-log** ✅ — new `common/whole_history.js` = the ONE shared writer/reader; shape `{page,ts,label,kind,ref}`
  (back-compat w/ landing `{source,type,label}`). Mirrors wired: `history_bar._mirror`→`WholeHistory.record` ·
  `idmp_history.push` · glassbowl + gravity `recordView`. Witness `tests/poc_whole_log.js §WHOLE-LOG …ordered=Y`.
- **W2 cross-page-bar** ✅ — clock launcher (bottom-left, self-contained Lucide) → overlay WHOLE|THIS + aggregate;
  foreign click → `bim.wholeRestore` handoff + deep-link (rootPrefix URL), `consumeRestore(page,fn)` once on load.
  Witness `poc_whole_bar.js` + `poc_whole_bar_dom.js` (§WHOLE-BAR / §WHOLE-BAR-DOM PASS).
- **W3 time-scroller** ✅ — day buckets; `‹day` long-press-back steps DAY→DAY (clamps) → that day's STRETCH,
  horizontally scrollable. Witness `poc_whole_time.js` + `poc_whole_time_dom.js` (§WHOLE-TIME PASS).
- Real-app smoke: glassbowl/gravity/idempiere mount launcher, 0 page errors; audits scripts 81/0 + precache 100/0.
- FIXED en route: `ts|0` int32 truncation → `Math.floor`; `no-undef` gate (viewer/) needs `window.WholeHistory.x`.
- Memory: [[project_whole_history_timeline]].

## ⏭ OUTSTANDING (next session — none blocking; all deferred/scoped-out, not gaps)
1. **Thumbnails = press 3rd-richness (§LOCKED-5)** — a preview image per moment (dot→chip→THUMB). The one
   intentionally-deferred piece; was never in W1–W3 scope. Needs a cheap capture (canvas/SVG snapshot) per page,
   stored as a small dataURL on the entry's `ref` (cap size!), shown on bloom/press in the W2/W3 chips.
2. **Landing launcher** — `index.html` was left with its OWN chip strip (NOT the shared launcher): its standalone
   packager inlines `<script src>` and a `../common/` ref risks breaking offline packaging. Unify only if the
   packager is taught to inline/skip `common/whole_history.js`.
3. **Viewer building-restore is best-effort** — cross-page restore TO viewer calls `A.cityLoadBuilding(name)`
   (city-context only); a robust name→db reopen on a bare viewer load isn't wired (deep-link still re-opens viewer).
4. **Mobile placement** — launcher bottom-left vs ERP pill dock bottom-right / viewer bar bottom-center: no overlap
   by design, verified headless @420px only — confirm on a real device + check safe-area inset.

---

## WHERE WE ARE (verified 2026-06-10 against origin/main — BUILT vs DESIGN, so a new session doesn't re-derive)

**BUILT — per-page LOCAL bars (each a separate store):**
- viewer — `common/history_bar.js` (branch TREE #5 + 5-stop KNOB #4 + cross-branch COMBINE #6, all witnessed) via
  the `viewer/universal_history.js` adapter.
- iDempiere — `erp/idmp_history.js`: doc-nav moments `{windowId, tabIdx, table, recordId}` + (sw v614, PR #220)
  the doc-events **KNOB** (off/low/mid/high/max) reading the sniffer through `common/history_tap.js` STOPS.
- glassbowl / gravity — `erp/glassbowl.html` + `erp/glassbowl_gravity.html` `#scrub`: **VIEW-logs** (NOT doc-nav)
  — `captureView()` records camera/orbit state (`yaw/pitch/focus/trace/dossier/show…` ; gravity `lens/measure/
  depth`), `applyView()` restores, drag-to-scrub timeline. **These are a DIFFERENT feature (view-history), not
  duplicates of the iDempiere bar — do NOT delete them; they FEED the aggregate, they don't get replaced.**

**BUILT — the SUBSTRATE (but viewer-only today):**
- A shared cross-tab log `bim.docHistory` (localStorage) + a `bim_history` BroadcastChannel. `history_bar.js
  _mirror()` writes DOC-class milestones to it; **only `viewer/universal_history.js` currently reads it.**
- Entries are already TIMESTAMPED — `history_bar.js _now() = Date.now()`, stamped onto every node as `entry.ts`.

**NOT BUILT (this lane delivers it):**
- The cross-page AGGREGATE view ("what page was I on before").
- The TIME scroller (day-step / stretch / scroll-across-all-timelines).
- The ERP + glassbowl/gravity pages FEEDING the shared log (only the viewer mirrors today).
- (Out of scope here: thumbnails = the press 3rd-richness level, deferred §LOCKED-5; keep deferred.)

## THE MODEL (the user's words, refreshed — this is the TARGET)
- "see only that page history" = today's per-page bar — KEEP it.
- "whole history → see what page I was on before" = ONE aggregate of every surface's entries, each tagged by page.
- "press-back-LONG → day going backwards → jump to the stretch, scrollable for all timelines" = long-press-back
  steps by **DAY**; landing on a day opens its **STRETCH** = every page's entries in that window, time-ordered + scrollable.
- Clarified affordances (already built, don't re-ask): **hover** = instant `title` tooltip · **press** = bloom dot→chip ·
  **thumbnail** = future/deferred.

## BUILD ORDER — three witnessed, additive steps

### W1 — UNIFY THE LOG (every surface contributes; nothing local breaks)
- Settle the shared entry shape: `{page, ts, label, kind, ref}` where `page ∈ {viewer,idempiere,glassbowl,gravity}`
  and `ref` is the page's own re-open key (viewer: building+view · idempiere: {windowId,tabIdx,table,recordId} ·
  glassbowl/gravity: the captured view).
- Wire each surface to ALSO mirror its entries into `bim.docHistory` (ADDITIVE, best-effort, read-only — never
  replace the local bar):
  - viewer: already mirrors DOC milestones via `_mirror` — just add `page:'viewer'` to the shape.
  - iDempiere: `idmp_history.push()` → also mirror `{page:'idempiere', ts, label, ref:{window,tab,table,recordId}}`.
  - glassbowl + gravity: `recordView()` → also mirror `{page, ts, label:viewLabel(v), ref:v}`.
- Witness (`poc_whole_log.js`): drive 2+ pages (cross-tab via the BroadcastChannel), dump `bim.docHistory` →
  entries from BOTH pages, time-ordered by `ts`, correctly page-tagged. `§WHOLE-LOG pages=[…] entries=N ordered=Y`.

### W2 — CROSS-PAGE BAR (the aggregate)
- A shared reader (e.g. `common/whole_history.js`) that reads `bim.docHistory` + re-renders on the channel/storage
  event, drawing the aggregate strip: each entry shows its **page** + label; click a FOREIGN-page entry = deep-link
  to that page (URL) + carry its `ref` so the page restores that moment read-only.
- Surface it as a toggle/pill: "whole" vs "this page" (reuse the pill registry on ERP, the bar on viewer). Honour
  clean-Lucide-only, no unicode glyphs. [[feedback_pill_icon_consistency]]
- Witness (`poc_whole_bar.js`): aggregate shows e.g. "glassbowl orbit → idempiere Order 1023 → gravity lens";
  clicking the glassbowl entry navigates + restores it. `§WHOLE-BAR shown=N foreignClick=ok`.

### W3 — TIME SCROLLER (day / stretch / scroll-all)
- Bucket entries by DAY from `ts`. The scroller's **long-press-back** steps DAY→DAY (not entry→entry); landing on
  a day expands its **STRETCH** = all entries that day across all pages, scrollable horizontally.
- Reuse the KNOB's press model (long-press = the time-jump gesture); detents/SFX optional, mute-aware.
- Witness (`poc_whole_time.js`): seed entries across ≥2 days (inject `ts` via args — NO Date.now in the test
  record path), long-press-back crosses a day boundary, the day's stretch lists every page's entries, scroll works.
  `§WHOLE-TIME days=[…] jumpedBackDay=ok stretchEntries=N scrollable=Y`.

## INVARIANTS (must hold)
- **Additive.** Every page keeps its LOCAL bar + recording; the aggregate is a READER layered on top. No bar is removed.
- **Read-only restore.** Cross-page restore = navigate + best-effort view restore; NEVER mutate any kernel op-log.
- **Deterministic.** `ts` is the real recorded `Date.now()` (already stamped); the witness injects test timestamps
  via `args`, never calls `Date.now()` in the record path.
- **Shared engine, no fork.** Build on `history_tap.js` (`field`/`currentView`/`applyView`) + `history_bar.js`
  (`_mirror`, `sharedKey`, `channel`). glassbowl/gravity view-logs become FIELDS/contributors, not rewrites.
- **§-log first.** Each W# gets a whitebox §-witness; READ the log before any conclusion.

## STARTING POINTS (real anchors)
- `common/history_bar.js` — `_mirror()` · `sharedKey:'bim.docHistory'` · `channel:'bim_history'` · `_now()`/`entry.ts`
  (the substrate to generalise).
- `viewer/universal_history.js` — the only current `bim.docHistory` consumer (the reader to generalise into W2).
- `erp/idmp_history.js` — `push()` + the v614 knob (mirror the moment; knob/press model to reuse in W3).
- `erp/glassbowl.html` `recordView()`/`captureView()`/`viewLabel()`; `erp/glassbowl_gravity.html` same (the
  view-log contributors).
- Knob/press spec: `prompts/HISTORY_KNOB_SIGNAL_TAP.md` §LOCKED-KNOB. ERP-tap port pattern: `prompts/HISTORY_TAP_TO_IDEMPIERE.md`.

## DELIVERABLE
One cross-page, timestamped history the user scrubs by TIME: see every surface they were on, long-press-back by
day, open a day's stretch scrollable across all timelines — each page still keeping its own local bar. Three
§-witnessed steps (W1 unify-log → W2 cross-page-bar → W3 time-scroller), additive, shared-engine, read-only.

## STOP CONDITION
Each W# has a `§WHOLE-…` PASS witness; the aggregate spans ≥2 pages; long-press-back crosses day boundaries; no
local bar lost; separation/read-only invariants intact. If a step needs a user decision that can't be EXTRACTED
→ `⛔ BLOCKED: <the one question>` and move to the next step.
