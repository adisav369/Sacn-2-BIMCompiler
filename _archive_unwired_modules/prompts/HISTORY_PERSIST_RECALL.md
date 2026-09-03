# ⚠ DO NOT REMOVE — History PERSIST & RECALL across page-leave / tab-close / building-return
# Scope: BUILD (discuss CLOSED 2026-06-08 → see §LOCKED). The history/undo timeline
#        (common/history_bar.js, LIVE) keeps its full per-element trail IN MEMORY — so leaving the
#        building page (→ landing index.html, → ERP idempiere.html) or closing the tab DROPS it. The
#        user wants: **return to the same building later and recall the history.** Decisions are made;
#        confirm the two §VERIFY-FIRST items, then implement the §LOCKED spine + witness + PR.
#        Whitebox §-log is the witness; save every run to a log and READ it before concluding.
#        Edit shipping code ONLY in /home/red1/bim-ootb/. Honour until ✅ DONE.

## ▶ INTENT (user, verbatim sense)
"When we leave a building page to go to the main landing HTML, or to iDempiere, or even close the
tab — but then return to the building — can we still recall back the history? Discuss that, and see
what user experience we can have."

Related, already-agreed deferral (same lane): **camera-stops with no selection are NOT recorded**
(by design — §6b IGNORE bucket). If "record where I parked the camera" comes up, decide it here too.

## ▶ LOCKED (decided with user 2026-06-08 — BUILD TO THIS, the discuss phase is closed)
1. **Silent rehydrate (B) + per-building key (C) = the spine.** On building-open, the timeline line just
   appears already populated from the persisted signed log, keyed by `A.activeBuilding`. NO resume toast
   (option A dropped); fold any "N steps · last: <label>" hint INTO the line, not a dialog.
2. **The signed kernel log ALWAYS records.** The depth toggle's `off`/hide ONLY hides the bar (the view);
   it NEVER stops `kernel_ops` from committing+sealing. "Hide" ≠ "stop the truth." (Verified: `kernel_ops.js`
   has zero `depth`/`HistoryBar` reference — the gate lives only in `common/history_bar.js:80` `push()`.)
3. **No auto-prune window.** Drop the "keep 2 SESSION_STARTs" concern for this scope — the log accumulates
   until the user clears cache. Clearing the cache is the ONLY purge. (500 steps ≈ ~0.5 MB RAM, ~0.3 MB in
   the DB — negligible on mobile; the only real mobile cost is the EXISTING whole-DB `_persistToIdb` blob
   write, which #189 does NOT add to — the new view-stream persist is text, a few hundred KB.)
4. **Clear = two distinct controls.** "Clear view trail" = cosmetic (extend `clear()` at `history_bar.js:174`
   to drop the persisted view stream for this building). "Reset signed log" = a NEW GENESIS (you don't delete
   a hash-chained append-only log, you re-genesis it) — separate, confirm-gated. Surface both; don't conflate.
5. **Thumbnail film-strip is IN SCOPE (promoted from §FUTURE), bounded:** snapshot canvas → ~96×64 crop →
   memory-only LRU (~20), shown in the BLOOMED chip on DESKTOP ONLY; mobile stays text. **EPHEMERAL by design
   — NEVER persisted, NEVER enters the kernel, no mobile path.** So it touches none of the persistence/mobile
   surface above. ONE verify item: capture timing under the S286 idle-render gate — `preserveDrawingBuffer:false`
   + on-demand render means `toDataURL()` can be blank; grab the snapshot right after a render (or in the
   render loop), confirm a non-blank dataURL via `§HIST_THUMB w=.. h=.. bytes=..` before wiring the chip.

## ▶ LOCKED-II — Thumbnails · Navigation · Cross-app (shaped with user 2026-06-08)
Framing: this is the **powerful-demo POC** — a serverless, signed history that survives tab-close,
rehydrates on return, scrubs across time AND apps. Build it to make the user fall off the seat.

### Thumbnails (refines §LOCKED-5)
- **Own ON/OFF toggle**, sibling to the existing full/half/off depth toggle — reuse that toggle pattern,
  clean Lucide icon (icons.js), no new gesture.
- **Layout:** ON = a thumbnail RAIL *above* the dot-line; the **dot-line STAYS below** (line never
  disappears); each thumb aligns directly over its keyframe dot. Sparse keyframes → uncrowded rail.
  OFF = plain dot-line.
- **Keyframe trigger — deterministic, NOT pixel-diff:** snap on `op` (mutation) always; snap on a
  camera/view move ONLY when SIGNIFICANT — camera position+target delta crosses a threshold (e.g.
  > ~15% scene-bbox diagonal OR > ~20° orbit; a maths delta, tunable BY EYE in the witness via
  `§HIST_THUMB`). Skip bare picks and slight nudges.
- **Ephemeral, always:** memory-only LRU (~20), downscale to ~96×64 BEFORE encode, NEVER persisted,
  NEVER in the kernel, NEVER inside the DB blob. They regenerate fresh and vanish on reload — fine.
- Scroll horizontally when wide; **virtualize** (render only the visible window). DOM apps
  (glassbowl/idempiere — no canvas) degrade to a **text chip, no thumb**.

### Navigation (kills scroll-forever)
- **Day/session dividers** on the line — derived from `ts` day-change + SESSION_START — with SIMPLE
  relative labels: **Today / Yesterday / "N days ago"** (absolute date for old ones). [decider's call]
- **Press-hold-back day-scrub drawer:**
  - **Tap** back = undo one step (unchanged).
  - **Press-and-hold** back → a drawer of **24 h day-dots** appears and a count ticks (−1d, −2d, …).
    **COUNT-ONLY:** the timeline does NOT move and **NO restore / NO thumbnail fires on the tick** (off
    the hot path).
  - **Commit ONLY** when the user **drags/swipes onto a day-dot** → restore to that day's first entry.
    **Plain release (no drag) = nothing happens** (safe cancel).
  - Drawer 24 h dots **scroll** if many; user can keep scrolling further back. **Going too far is
    harmless — doing ANY event creates a fresh dot and snaps back to the present tip.**
  - **Empty days still show** (fixed-clock rewind shows every 24 h bucket); landing on an empty day =
    blank timeline + a **"no activity" message in the STATUS BAR** (not inline). Cap the auto-tick at
    the oldest real day.
  - **Desktop:** mouse-down-hold → drawer → move to a dot → release to select; right-click opens the
    drawer statically.
- The depth toggle **"half" (doc)** doubles as the coarse **milestone index**. Bloom-overview (fit-all)
  + text/NL search = **fast-follows**, not this PR.

### Cross-app crossbar
- Same-origin (viewer/landing/`erp/*` one host). The union log `bim.docHistory` ALREADY flows to the
  landing — **keep it milestones-only there; the deep per-building scrub rehydrates on RE-ENTRY, not on
  the landing** (don't bloat the synchronous localStorage log).
- **Landing pale bar** (`index.html:410` `renderDocHistory` chips) is **display-only today (no onclick
  — verified)**. Make chips **CLICKABLE → `openViewer(building)`**, and pass a **deep-link param** so the
  viewer rehydrate (the spine) restores the exact view. **Data gap:** `bim.docHistory` entries hold only
  `{ts,source,type,label}` — **add the building id + representative file to the `BUILDING_OPEN` entry**.
  Reskinning the landing bar into the shared HistoryBar component = a **consistency follow-up** (defer).
- **Cross-app proof PAIR for THIS PR: viewer ↔ glassbowl_gravity** (both have a WebGL canvas → thumbs
  work). Other ERP pages + full cross-app click-to-restore = the deferred ERP-wiring follow-up.
- **Cross-TAB jump (decided):** clicking a history spot that maps to a tab WE opened (window handle in
  `openTabs`, `index.html`) → `win.focus()` switches to it + post a "restore spot Y" BroadcastChannel
  message → it restores there. Tabs the user opened independently (no handle) **cannot be switched to**
  (browsers block a background tab self-focusing) — that's the user's choice; keep the tab open and it
  works. The SW `clients.focus()` arbitrary-tab route = a flagged FOLLOW-UP, not this PR.

### Clear — levels, by intent (history is SPARED by cache-clear — see §LOCKED-III)
- **Clear view trail** — cosmetic (dots + landing bar); signed log untouched.
- **Reset signed log** — deliberate NEW GENESIS (you don't delete a hash chain), confirm-gated. The ONLY
  routine way to wipe history.
- **Geometry cache clear** — wipes the building-DB blobs (`bim_ootb_cache`; buildings re-fetch from OCI)
  but **SPARES `bim_history`** — history survives. (Only a browser-level "clear all site data" wipes
  everything, and that's not the routine dev action — use the in-app geometry clear instead.)

### §LOCKED-V — Silos + one union: the this/all scope toggle (decided 2026-06-08)
- Each surface keeps its OWN siloed trail (viewer = per building `A.activeBuilding`; ERP = per doc/app).
  Each tab shows its own dots — this is the per-building rehydrate already built+witnessed for the viewer.
- The "whole journey across every surface" = the existing shared union log `bim.docHistory` (every tab +
  the landing read it, BroadcastChannel-synced, same-origin). Identical on every tab.
- **New: a `this ⇄ all` SCOPE TOGGLE on the bar** (sibling to the all/doc/off depth toggle):
  - **this** = render the silo (`_stream`) — current behaviour.
  - **all** = render the union (`bim.docHistory`) — read-only breadcrumbs across all surfaces.
  - Persisted (`bim.hist.scope`). Flip to "all" on ANY tab → see everything, everywhere.
- **Foreign-entry click (Q1) — read-only recall, never dead.** Clicking an entry whose building/surface
  isn't the current one: (a) if an OWNED tab has it → `win.focus()` + restore there; (b) else → DON'T
  restore — show the action read-only in the status/footprint readout (`Clinic · Find walls`) so the user
  reads + remembers; optional faint `(open to restore)`. The entry already carries label+source — just a
  format branch.
- **Union grouping (Q2) — group-by-building.** When the union is long, collapse it to one chip per
  building/surface (`🏠 Clinic · 7`); tap to expand that surface's entries. Combined with day-dividers +
  long-press-back (time axis), "Clinic, yesterday" is two taps. Opening a building from the landing is
  still the primary focus action (per-building rehydrate isolates it automatically).
- **ERP bar placement:** center the timeline in the SAME slot on every ERP tab (the cleared ~40px center
  strip), so position is consistent whatever you open.

### §PARKED — Granularity knob via the `§`-log SIGNAL TAP (lateral idea, 2026-06-08, NOT yet built)
Instead of instrumenting each action with a `push()` (the "emit" problem — why X/C aren't recorded), TAP
the one signal that already carries everything: the **`§`-log stream** (Log Mandate makes every action emit
a `§…` line — confirmed pervasive: `§KBD_ROUTE`, `§FOCUS_ELEM`, `§FILTER_*`, `§PHASE_LENS`, `§XRAY_*`…).
- **The granularity knob = how WIDE a `§`-pattern net you cast.** Low → only `§KERNEL_OP`/`§BUILDING_OPEN`;
  turn up → add `§KBD_ROUTE`, `§FOCUS_ELEM`, `§FILTER_*`…; max → nearly every `§`. Continuous, not a whitelist.
- **Dissolves the toggle problem:** X (`§KBD_ROUTE Alt+X`) and C already emit `§` → captured for FREE, zero
  wiring; every future feature too.
- **Two-tier architecture this implies:** kernel-op channel (structured, has replay data) = the REPLAYABLE/
  undo spine; `§`-stream tap (broad, zero-instrument) = the READ-ONLY "what did I do" net, governed by the knob.
- **Honest cons (banked):** `§` lines are human text not an API → brittle parse; labels scraped from prose;
  a `§` line may lack restore payload (→ read-only breadcrumb). De-brittle later with a one-line convention
  `§EVT kind|label` (still just a console.log, no bus). Supersedes the manual 6-toggle allow-list wiring.

### §LOCKED-III — History persistence is DECOUPLED from the geometry cache (decided 2026-06-08)
**User intent:** "spare the history log during clear cache — our testing clears cache / hard-resets often;
let it run until we decide to clear it manually. It's cheap." So:
- History (signed op-log + view stream + milestones) lives in its OWN durable IDB store **`bim_history`**,
  per-building keyed — SEPARATE from `bim_ootb_cache` (the heavy geometry blobs).
- Routine **geometry cache clears / dev hard-resets (targeting `bim_ootb_cache`) do NOT touch
  `bim_history`.** History accrues over time so the demo shows a long trail; it's cheap (text only).
- History is wiped ONLY by the explicit MANUAL "Reset signed log" (new genesis).
- **Win:** persisting the log to its own small store means we no longer re-export the whole building DB to
  save an op — kills the MB-scale `_persistToIdb` write AND sidesteps the v1/v2 `VersionError` bug. The
  whole-DB persist bug is still fixed for GEOMETRY-refresh survival (GRID_MOVE), but it's now SECONDARY to
  recall: geometry mutations are **REPLAYED from the signed log onto fresh geometry on rehydrate**
  (event-sourcing — the log is truth, geometry is derived).
- **The spine rehydrates from `bim_history`**, independent of whether geometry was cached or re-fetched.

### §LOCKED-IV — Status-bar history-footprint readout (decided 2026-06-08)
- When the depth toggle Z is **on** (depth ≠ off), show a **subtle GREY footprint readout in the
  status-bar prefix** — e.g. `⛁ 42 KB · 318 steps` — so the user sees the cost they're accruing (we
  decided to let it run forever, §LOCKED-III — this proves it stays cheap and warns if it grows).
- Computed from OUR OWN data, deterministic + cheap: `JSON.stringify(_stream).length` + the thumbnail
  LRU byte total. NOT `navigator.storage.estimate()` (that's whole-origin, not history-specific) — though
  a quota % MAY be shown as a secondary hint.
- Update on push / persist (debounced); hidden entirely when Z is off. Grey = ambient info, never alarms.

### Cost guards (recap — the heavy ops to keep OFF the path)
- Keep view-stream + thumbnails in their OWN small key — **NEVER inside the whole-DB `_persistToIdb`
  blob** (the MB-scale geometry+kernel file re-written every 2 s).
- **No restore / no thumbnail on the countdown tick** — commit-only.
- **Virtualize** the long-list render (visible window only).

## ▶ WHAT PERSISTS TODAY vs WHAT DOESN'T (grounded — verify, don't trust this blindly)
LIVE state of the shipped system (HISTORY_SCRUB_FIX §1–§9, see [[project-history-shared-module]]):

- **Signed kernel log — ALREADY PERSISTED.** `viewer/kernel_ops.js:80` `_persistToIdb(db)` exports the
  whole sql.js DB (incl. `kernel_ops`) to IndexedDB `bim_ootb_cache`, **hash-chain SEALED first**
  (W-CHAIN, tamper-evident at rest). So `ELEMENT_PICK` / `GRID_MOVE` / `BUILDING_OPEN` (the kernel
  ops) survive a refresh AT THE DB LEVEL.
- **History VIEW cache (`_stream` in `common/history_bar.js`) — IN-MEMORY, LOST on close.** It is
  DERIVED from the kernel (§9 storage tiers: "rebuildable from the kernel, except view-only lens-nav
  which is ephemeral"). It is NOT persisted today.
- **Cross-app doc log `bim.docHistory` (localStorage) — PERSISTED across tabs AND visits.** Holds
  MAIN/DOC milestones (`BUILDING_OPEN`, …). The landing already renders this union (§8). Survives
  tab close.
- **Depth choice `bim.universalHist.depth` (localStorage) — PERSISTED.**
- Cross-tab live sync: `BroadcastChannel('bim_history')` + the `storage` event (same-origin: viewer,
  landing, ERP all under bim-ootb).

## ▶ THE CORE QUESTION → likely a REHYDRATION, not a new store
The signed truth (kernel_ops) is already persisted. So "recall history on return" is mostly:
**on building-open, rebuild the timeline FROM the persisted kernel_ops (verify the chain first), and
present a resume UX.** Two things to settle by INVESTIGATION before any code:

1. **Does building-open actually reload the IDB-cached DB (with kernel_ops), or re-fetch a FRESH copy
   from OCI (overwriting the cache → kernel_ops gone)?** The user's session log shows `§CACHE_HIT
   Terminal_meta.db` + `§GEO_CACHE_CHECK hit=true` (DBs DO load from cache), but CONFIRM the cached
   blob is the one carrying the session's `kernel_ops`, and that a re-open reads it back. If a fresh
   OCI copy wins, kernel persistence must move to a SEPARATE per-building store (don't bloat the geo DB).
2. **View-nav steps (Find axis/group/item) are pushed to the timeline but are NOT kernel ops** —
   they're view entries. A kernel-only rehydrate would restore picks + milestones but LOSE the Find
   breadcrumb trail (§9 calls lens-nav "ephemeral"). Decide: persist the full view trail too (a small
   per-building view-cache, localStorage/IDB) or accept Find-nav as ephemeral.

## ▶ STORAGE TIERS (from §9) — where persistence fits, and the bloat guard
- **Kernel log** — persisted, signed, tiny (op_type + small params + hash chain). NEVER put images.
- **History view cache** — small text (label, guids, kind). DERIVED; safe to clear; rebuildable.
  THIS is the tier to (optionally) persist per-building for the full-trail recall.
- **Thumbnails** (if ever) — ephemeral, memory-only, capped LRU. Never persisted (bloat guard).
- Constraints: localStorage ≈ 5–10 MB, synchronous, string (the §9 panel showed it at ~17 KB — lots
  of headroom for text trails). IndexedDB for anything bigger / the DB blob. The "one live SIGNED
  WASM kernel shared across tabs" (SharedArrayBuffer) tier is **localhost-only** — GH Pages can't send
  COOP/COEP (same wall as geo-range streaming). Persist-across-visits does NOT need SAB; localStorage
  + IDB suffice and work on GH Pages.

## ▶ UX — IN SCOPE (decided; build) vs DEFERRED (don't build, don't drop)
Building identity = `A.activeBuilding` (e.g. "Ifc2x3_Duplex_Federated") — key the history by it.
IN SCOPE (full shaped set — see §LOCKED + §LOCKED-II for detail):
- **B. Silent rehydrate** — the scrub bar just opens already populated; user scrubs back at will. No prompt.
- **C. Per-building history** — switching buildings shows THAT building's trail; the bar is keyed by building.
- **Clear controls** — three levels: clear view trail / reset signed log / full cache. See §LOCKED-4 + §LOCKED-II.
- **Thumbnails** — own ON/OFF toggle, rail above + dot-line below, sparse camera-delta keyframes, ephemeral LRU.
- **Navigation** — day/session dividers + press-hold-back day-scrub drawer (count-only, drag-to-commit). §LOCKED-II.
- **D-lite: landing chip → open + restore** — make the pale-bar chips clickable → open building + deep-link view.
- **Cross-app PAIR** — viewer ↔ glassbowl_gravity carry one trail (both have a canvas).
DEFERRED (later PRs — not in #189, not dropped):
- **A. Resume toast** — dropped in favour of B; any "N steps · last:<label>" hint folds into the line.
- **D-full: reskin landing bar into the shared component** + click-restore on ALL erp pages (ERP wiring).
- **E. Glassbowl cross-visit scrubber** — multi-visit bloom; **bloom-overview (fit-all) + text/NL search**.
- **Full view-nav trail persistence** — Find breadcrumbs surviving visits; v1 is kernel-derived only.
- **ERP wiring (beyond the pair)** — shared module gets the `persistKey`/`building` hooks; wiring iDempiere is the hand-off.
- **Retention/prune window + privacy opt-out** — none for now (§LOCKED-3: accumulate until clear-cache).

## ▶ DECISIONS — ALL MADE (see §LOCKED; recorded here for trace)
1. Auto-restore silently (B). ✅
2. Kernel-derived only (picks+milestones); full view-nav trail deferred. ✅
3. Per-building scope (C), key = `A.activeBuilding`; city / multi-building view deferred. ✅
4. Landing/ERP deep-link restore (D) deferred to a follow-up. ✅
5. No retention window; clear-cache is the only purge + the two clear controls (§LOCKED-3/4). ✅
6. `off`/hide hides the bar only; signed log always records (§LOCKED-2). ✅
7. Thumbnail film-strip IN, bounded + ephemeral (§LOCKED-5). ✅

## ▶ WHERE TO BUILD (keep the shared-module shape)
Persistence belongs IN `common/history_bar.js` so the viewer AND ERP both inherit it — extend
`HistoryBar.configure({ persistKey, building })`: on `open`/configure, LOAD this source+building's
stored stream; on `push`, SAVE (debounced) to localStorage/IDB; keep the signed kernel as the source
of truth and the view cache as the rebuildable convenience layer. Viewer adapter
(`viewer/universal_history.js`) passes the building key; restore stays owner-local. Do NOT touch
`navigate_find.js` (separate refactor lane). ERP wires the same `persistKey` against its own data.

## ▶ WITNESS (whitebox §-log first, leak-safe headless)
- Drive picks + a grid move → close the page → re-open the SAME building → the timeline REHYDRATES
  (same steps, same labels); `verifyChain ok=true` on the persisted kernel (`§HIST_CHAIN_OK`).
- Switch to a DIFFERENT building → its own (empty or prior) trail; switch back → first building's trail.
- Toggle depth `off`, drive a pick, toggle back on → the pick is STILL in the signed log (hide ≠ stop):
  `§HIST_DROP reason=off` on the view BUT `§KERNEL_OP committed` for the same action.
- Clear view trail → bar empties, signed chain intact. Reset signed log → new genesis, `verifyChain ok=true`.
- Thumbnail (desktop): a SIGNIFICANT camera move or an op yields a non-blank crop (`§HIST_THUMB w=96
  h=64 bytes>0`); a slight nudge / bare pick does NOT (`§HIST_THUMB skip reason=below-threshold`). The
  rail shows above, the dot-line stays below, each thumb over its keyframe dot; LRU cap holds (~20);
  thumbnails ON/OFF toggle flips the rail; MOBILE shows text, no thumbnail path taken.
- Day dividers render with the right relative labels (Today/Yesterday/N-days-ago).
- Press-hold-back: drawer + count appear; **on tick NO move + NO restore + NO thumbnail** (`§HIST_SCRUB
  tick=-2d preview=off`); **drag to a day-dot commits** a restore (`§HIST_SCRUB commit day=-2d`); **plain
  release = no-op** (`§HIST_SCRUB cancel`); scroll past oldest is bounded; doing any event returns to the
  present tip (`§HIST_SCRUB back-to-present`); an empty day shows blank + status-bar "no activity".
- Landing chip CLICK opens the building AND restores the view (deep-link); `BUILDING_OPEN` entry carries
  building id + representative.
- Cross-app PAIR: viewer → glassbowl_gravity → back carries ONE trail with thumbs from both; DOM apps
  degrade to text chips.
- Cross-tab still syncs (`BroadcastChannel`); the landing union still renders; depth choice still persists.
- No regression: run `tests/run_regression.sh` (the 6 viewer probes) — all green; no PAGEERROR.

## ▶ VERIFY-FIRST (the two grounded unknowns — confirm BEFORE writing feature code)
1. **Load-path:** does building re-open READ BACK the IDB-cached DB carrying `kernel_ops`, or refetch a
   FRESH OCI copy and overwrite the cache (→ kernel gone)? If fresh wins, persist the view stream + a
   per-building kernel snapshot in a SEPARATE store — do NOT bloat the geo DB. (`§GEO_CACHE_CHECK hit`
   exists; confirm the cached blob is the one carrying the session's ops and that re-open reads it.)
2. **Thumbnail capture timing** under the S286 idle-render gate (§LOCKED-5) — non-blank dataURL or bust.

## ▶ DELIVERABLE
Discuss phase is CLOSED (see §LOCKED + §DECISIONS-ALL-MADE). Confirm the two §VERIFY-FIRST items, THEN
implement in the shared module + witness + PR. Frame the demo around the novel bit: a **serverless,
SIGNED history that survives tab-close and rehydrates on return** — across landing ↔ viewer ↔ ERP, no
backend; the append-only chain you can re-genesis but never silently edit.
