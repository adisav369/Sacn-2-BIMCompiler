# ⚠ DO NOT REMOVE — ERP init-bubble must be INSTANT (the sharding promise) · SCOPE + read the log after every run
# SCOPE: erp.html's first paint. The "init bubble" (Phase-1 constellation from initbubble.json) should appear
#   INSTANTLY (<300ms, ideally first frame) but currently lags ~1s. The 12.7MB ad_seed.db must NOT block it.
#   Shipping code in /home/red1/bim-ootb/erp/ ONLY; isolated worktree off origin/main → PR → CI → squash-merge.
# DOCTRINE: §-log first (READ §BENCH before any conclusion — exit code/visual ≠ evidence), NON-INVENT, whitebox
#   witness NAMES the issue, EXPLICIT GO before deploy, bump erp/sw.js CACHE_VERSION + any ?v=.

## ▶ SYMPTOM (user, 2026-06-07, verbatim intent)
"the leak in initial ERP.html load still lag a sec when it supposed to show init bubble json. Like it to be
instant if can. Its sharding that was promised." → the init bubble (initbubble.json globe) takes ~1s to appear;
it should be instant. "Sharding was promised" = first paint should come from a TINY shard, deferring the big DB.

## ▶ WHAT EXISTS TODAY (observed — erp.html, verify line numbers, they drift)
- **Phase 1 "Instant constellation"** (`erp.html` ~L77–L123): `fetch('initbubble.json?v=24')` → render a globe in
  the claimed `<300ms`. This IS the init bubble. It logs around `§BENCH initbubble …` / `§INSTANT`.
- **Phase 2** (~L203–L315): `loadScript('lib/sql-wasm-fts5.js')` → `initSqlJs` (WASM) → fetch the DB
  (`?db=` url, else IDB cache `ad_seed_v13`, else network `ad_seed.db` ~12.7MB) → `idbPut`. Logs `§BENCH db_fetch=
  …ms source=idb_cache|network|url db_size=…`.
- The SW (`erp/sw.js`) is **network-first for .html/.js** and precaches `initbubble.json`; `.db` skips the SW.

## ▶ THE ONE THING TO DO FIRST — MEASURE (don't guess the bottleneck)
Add/read the §BENCH timeline and find where the ~1s goes BEFORE changing anything. Candidate culprits to confirm
or kill with a number each:
1. **Head is script-blocked.** erp.html loads ~30 synchronous `<script src>` (icons, pills, kernel, seam, kanban,
   rule_fold, bigdecimal, qrcode…) in `<head>`. If Phase-1 runs after they parse/execute, the bubble waits on JS
   it doesn't need. → measure time-to-first-`§INSTANT` vs DOMContentLoaded; if blocked, `defer`/`async` everything
   not needed for the bubble, or inline a tiny Phase-1 bootstrap that runs BEFORE the script wall.
2. **SW network-first round-trip on initbubble.json.** Network-first means even a cached bubble waits for a network
   timeout/200. → measure `§BENCH initbubble` fetch ms cold vs warm; if the SW adds latency, serve initbubble.json
   (and the Phase-1 assets) **cache-first / stale-while-revalidate**.
3. **Bubble competes with the WASM/DB load.** If Phase 2 (`loadScript` sql-wasm + the 12.7MB fetch) is kicked off
   before/with Phase 1, it steals bandwidth/main-thread. → ensure Phase 1 paints, THEN Phase 2 starts (rAF/idle).
4. **initbubble.json itself is large / `?v=24` cache-busts every deploy.** → check its size; if big, that's the
   shard to shrink. The cache-bust param forces a refetch each version — fine if cache-first + small.

## ▶ "SHARDING THAT WAS PROMISED" — the target architecture
First paint should depend ONLY on a tiny, instantly-available shard (initbubble.json, already the intent), with the
full AD dictionary (ad_seed.db, 12.7MB) streamed/loaded AFTER the bubble is on screen and never on the paint path.
Confirm initbubble.json is that shard and is genuinely decoupled; if the bubble currently awaits SQL/DB init in any
way (e.g. `ADUI.init` at ~L109 gating the globe), break that dependency so the globe renders from JSON alone.

## ▶ WITNESS (build until green — name the issue)
`erp/tests/poc_init_instant.js` (whitebox, Playwright with the Performance API / console §BENCH): cold load (clear
caches) AND warm load (SW+IDB primed) → assert **time from navigationStart to the bubble's first paint ≤ 300ms**
(tighten toward instant), and that `ad_seed.db` fetch starts AFTER the bubble paints (not before). Emit
`§INIT-INSTANT cold=<ms> warm=<ms> bubblePaint=<ms> dbStartsAfterBubble=Y firstPaintBlockedBy=<none|scripts|sw|db>`
and `§INIT-INSTANT-RESULT PASS/FAIL`. READ the log; the number is the proof, not the screenshot.

## ▶ DEPLOY
Worktree off fresh origin/main → fix → witness green → PR → CI → squash-merge → bump `erp/sw.js` CACHE_VERSION
(+ `initbubble.json?v=` / any touched `?v=`) → verify the live first-paint number on GH Pages. Concurrent viewer
lane is active → sw.js is the conflict magnet (take the HIGHER version, keep all changelogs).
