# GLASSBOWL_SWUPDATE.md — "new version ready" toast (SPEC)

# ⚠ DO NOT REMOVE
> **Scope:** End the "please hard-refresh" era. Listen to the service worker's own
> `updatefound` signal and surface a small non-blocking TOAST ("Glassbowl updated — tap to
> refresh"); on tap, the waiting worker takes over and the page reloads ONCE. Applies to BOTH
> `glassbowl.html` (via generator) and `glassbowl_gravity.html` (hand-paste, sync-comment).
> **Pure front-end, no data, no T3, no writes.** Keep the 109 `§GLASSBOWL-WIRING` checks green
> + gen `§GLASSBOWL/§LIFECYCLE/§ORBIT` PASS `hand=0`. READ THE LOG after every run. Honour until DONE.

## The mechanism (why it works — no staleness-guessing needed)
The browser re-fetches `sw.js` on navigation and byte-compares it to the installed copy. Bumping
`CACHE_VERSION` changes a byte → the browser installs the new worker and parks it `waiting`
(old one still controls the tab). `registration.updatefound` + the new worker reaching state
`installed` WHILE `navigator.serviceWorker.controller` exists = "new code available, old showing".
That parked worker IS the signal — we only have to listen and offer the swap.

## Behaviour
1. On register, watch `reg.addEventListener('updatefound')` → the `reg.installing` worker's
   `statechange` → when `state==='installed' && navigator.serviceWorker.controller` → show toast.
2. Toast: a small fixed pill, bottom-centre, non-blocking, dismissible (✕). Tap body = "refresh".
3. On tap: `reg.waiting.postMessage({type:'skipWaiting'})`; `sw.js` `message` handler calls
   `self.skipWaiting()`; page listens once for `controllerchange` → single guarded `location.reload()`.
4. NEVER auto-reload silently (could interrupt a trace/edit) — reload only on the user's tap.
5. Soft, optional: reuse `beep()` (mute-honouring) for a quiet tick when the toast appears.

## sw.js change
Add a `message` handler: `if(e.data && e.data.type==='skipWaiting') self.skipWaiting();`
(The existing `install→skipWaiting` stays; this handles the WAITING worker swap-on-demand.)
Bump `CACHE_VERSION` so the very next deploy actually trips `updatefound` for returning users.

## Witness — W-SWUPDATE (front-end wiring; the real proof is the live byte-diff)
- The page registers the SW and attaches an `updatefound` listener; a toast element (`#swToast`)
  with a refresh action + ✕ exists; tapping it posts `skipWaiting` and guards a single reload
  (a `__reloaded` one-shot so it can't loop). `§SWUPDATE wired=Y toast=1 reload-guard=Y`.
- Test (Playwright, wiring-level): `#swToast` exists in DOM (hidden at rest); a simulated
  "installed+controller" path reveals it; clicking it calls `postMessage` (spy) exactly once and
  sets the reload guard WITHOUT a real navigation in the harness. NAME the issue each check proves.
- Honest note: true cross-version `updatefound` can only be proven by an actual v→v+1 deploy
  (two byte-different `sw.js`). The test proves the WIRING; the live deploy proves the SIGNAL.

## Discipline
- Additive; all 109 prior checks stay green. EXTRACT-only spirit (no invented state).
- Edit `scripts/system_explorer.js` (VIEWER_JS) for glassbowl.html; hand-paste the SAME snippet
  into `build/erp/glassbowl_gravity.html` with the SYNC-POINT comment; edit `build/erp/sw.js`.
- **NO DEPLOY** — regenerate + test GREEN locally; HOLD live push for explicit go.
- Loop: spec-cite → implement → regenerate (`node scripts/system_explorer.js 2>&1 | tee
  build/erp/system_explorer.log`) → extend `deploy/dev/tests/test_glassbowl.js` (check names issue)
  → run GREEN → read §-log.
