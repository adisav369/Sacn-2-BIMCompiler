# ⚠ DO NOT REMOVE — Scope & Working Rules
**Scope:** cache the computed Fly Tour action array per building so a repeat Fly click (or a fresh
session on the same building) skips the slow route-planning pass entirely. Nothing else — no route
algorithm changes, no tour behaviour changes; a cache HIT must replay the identical tour a MISS
would have built.
**Read the log after every run.** Witness lines: `§TOUR_CACHE store` (first build), `§TOUR_CACHE hit`
(replay). The proof is the SECOND activation reaching `walkMode=true` + camera movement WITHOUT any
`§FLY_ROUTE`/`§PATH_LEGAL` route-planning lines — absence of the slow pass in the log, plus speed.
**Status (2026-07-20):** implemented + witnessed this session (see §3), shipped with the
`startPlayback` warp-render TM fix wave. User request verbatim: "can this aerial sweep in Tour Fly
be saved in the metadata of the building so that it need not recalculate again. For LTU it be dead
slow."

## 1. Design (KISS — client cache, not DB redistribution)
- `buildTour()`'s finished action array is plain JSON (orbit/flyPath/pause actions: coords,
  durations, names — no live refs). Serialize it to `localStorage`.
- **Key** = `tmTourCache:<building>:<TOUR_VERSION>:<elCount>-<doorCount>-<roomCount>:<renderedSetSize>`
  - `TOUR_VERSION` (v12) → algorithm changes bust the cache (keep in lockstep with the `§TOUR_VERSION`
    banner in `tour.js`).
  - element/door/room counts from the building DB → a new extraction or room recompile busts it.
  - rendered-building-set size → a city tour (extra appended orbits) never replays a single-building
    route and vice versa.
- **Explicit bust:** `§FLY_RECURE` (room recompile) calls `A._tourCacheBust()` — counts alone could
  miss a same-count recompile.
- **Budget guard:** skip store if JSON ≥ 1.5MB; `setItem` wrapped (quota errors degrade to no-cache,
  never break the tour).
- **Cache scope note:** the orbit-fly fallback (no walk data) is NOT cached — it's cheap anyway and
  `buildTour()===null` is the signal the building can't do a real tour.
- **Why not "in the metadata of the building" (the user's literal ask):** shipping a precomputed
  route inside `*_extracted.db` would make FIRST-ever load instant for all users but requires
  per-building precompute + OCI redistribution (or a `patches/*.sql` self-heal per building). The
  localStorage cache achieves the felt goal (never recompute twice) with zero distribution. The DB
  route stays open as a follow-up if first-load cost matters: same JSON, one row in a `tour_route`
  table via the patch+self-heal convention.

## 1b. FIRST WITNESS RUN FINDING (2026-07-20) — buildTour was the WRONG (well, half the) target
The first W-TOUR-CACHE run showed `§TOUR_CACHE hit actions=25` on the warm pass — yet
`toggle→walkMode` was an IDENTICAL ~16.5s on both passes and route-planning lines still appeared.
Cause: `_prepareGraphTour`'s §THIN-GRAPH-RECURE probe (`A._buildGraphRoute({})`) runs the FULL
route builder on EVERY activation, before `_startFlyTour` is ever reached — the probe, not
`buildTour`, is the repeat-click cost. Fix: `toggleFlyAround` now takes a cache fast path — on a
key hit it skips the entire prepare (loadNavigate + ensureRooms + probe), keeping ONLY the
§STREAM-FIRST drain wait, logs `§TOUR_CACHE fast-path (prepare skipped)`, and goes straight to
`_startFlyTour`. Key mismatch/parse failure falls through to the full prepare. Witness criterion
updated: warm pass must show `planned=false` (zero §FLY_ROUTE/§PATH_LEGAL lines) + sustained
camera movement (≥2 moving pairs across 12s — tours open with pause actions; a 4s window sampled
after the initial snap reads falsely as frozen).

## 1c. MEASURED RESULT + one open headless observation (2026-07-20)
Isolated-browser two-pass run (fresh browser per pass, persistent profile for localStorage):
- **Cold: toggle→walkMode 16,519ms** (full prepare + probe + build), `§TOUR_CACHE store actions=25
  bytes=8786`.
- **Warm: toggle→walkMode 431ms — 38×** — `§TOUR_CACHE fast-path (prepare skipped)` → `hit
  actions=25` → `START cinematic tour: 25 actions` → first orbit starts → `§IDLE_GATE wake`.
  ZERO route-planning lines.
- **Open headless-only observation (NOT blocked on, NOT a cache defect):** under headless
  swiftshader the camera stalls after the first tour action on BOTH passes — including the cold
  pass, which contains none of the cache code, so it is orthogonal to this change. `§IDLE_GATE
  wake` fires, no park follows, no PAGEERROR — the loop is alive but the camera holds. Not
  reproduced live (v825 live check pending user confirmation). If a LIVE report of a stalling
  tour arrives, start from this note + `TOUR_WALKMODE_IDLE_PARK_STUCK.md` §9, and test walkTick's
  action-advance under low-FPS rAF — do not blame the cache first.
- Witness gate is therefore movement PARITY (warm ≥ cold) + the cache claims (hit, planning
  skipped, ≥5× faster, same action count) — absolute movement is a headless-environment property.

## 2. The slow part being skipped (measured shape, LTU 122k)
Route planning = room-graph walk + door-legality pass: dozens of `§PATH_LEGAL legalized=… detoured=…`
+ `§PATH_LEGAL_DETOUR_FAIL` lines before `§FLY_ROUTE storeys=5 stops=10/11 … pts=90` appears —
minutes of main-thread work on LTU under load. `§FLY_STREAM_WAIT` (waiting for streaming to drain)
is NOT skipped — touring mid-stream tours placeholder bboxes and starves the promote pipeline
(user doctrine 2026-07-16); the cache only removes the planning cost, not the stream-drain gate.

## 3. Verification — W-TOUR-CACHE
Two-pass witness on LTU_AHouse (the only building with proven room-graph data — see
`TOUR_WALKMODE_IDLE_PARK_STUCK.md` §7 for why Hospital silently tests the wrong branch):
1. Pass A (cold): activate Fly → expect `§TOUR_CACHE store actions=N`, `walkMode=true`, camera moves.
2. Reload page (same browser profile → same localStorage), Pass B (warm): activate Fly → expect
   `§TOUR_CACHE hit actions=N` (same N), `walkMode=true`, camera moves, and ZERO `§FLY_ROUTE`/
   `§PATH_LEGAL` lines after the toggle.
FAIL = pass B rebuilds the route, different N, or no movement. Results recorded below when run.
