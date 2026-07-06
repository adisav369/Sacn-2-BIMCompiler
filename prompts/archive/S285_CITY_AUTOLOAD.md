# ⚠ DO NOT REMOVE — S285 City Auto-Load (scope + log mandate)

**Scope (this task only):** Make the city viewer auto-stream buildings on HTML load
(like the single-building viewer auto-loads its `?db=`), and remove the Clear (🗑)
button. NO gate/instancing/distance-eviction/worker in THIS task — those are the
documented next steps. Edit `bim-ootb/viewer/city.js` ONLY.

**Read the log after every run.** Verify via §-logs + the Node eviction test, not the
browser (WebGL headless unavailable here). Do NOT deploy/push — no deploy instruction.

---

## Witness claims

- **W-AUTOLOAD** — On city `initCity` (desktop only), after `§CITY_READY`, the stream set is
  chosen by a **RAY-BLAST from the camera POV** (`A._cityRayBlast`): a 6×4 grid of rays first-hit
  the bbox layer, resolve the hit building (`userData.instanceBuilding[instanceId]`), and order
  unique buildings nearest-first into `A._cityPendingQueue`; `A._cityStreamNext()` is kicked. No
  click. First-hit = free occlusion (a building behind another isn't hit → not streamed). Falls
  back to `A._cityNearestFirst()` (centre distance) if the blast is empty. Proof: `§RAYBLAST
  rays=24 hits=M buildings=K ms=…` then `§AUTOLOAD queued=N nearest=<name>`.
- **W-FOLLOW** — On camera-stop (`A.controls` `'end'`, hooked once via `_cityFollowHooked`), a
  re-blast re-queues the now-visible, not-yet-loaded buildings (nearest-first) → the streamed set
  follows the view. Eviction of buildings that left the view is the NEXT task.
- **W-RAYORDER** — `_cityOrderBlastHits` dedupes by min distance, orders nearest-first, ignores
  null/no-building hits; un-hit (occluded/off-screen) buildings are absent. Proof: test T9.
- **W-WAVEFRONT-STOP** — Under auto-load, `_cityStreamNext` STOPS pulling from the queue
  once `_cityResidentBytes() >= 0.85 × budget`, so only the **nearest wave-front** loads and
  eviction is never forced (keeps NEAREST resident; no churn-to-farthest, no OOM). Proof:
  `§AUTOLOAD_STOP residentMB=… budgetMB=…`. Marquee path (flag off) is byte-identical to
  before — `_cityStreamNext` shared logic unchanged when `_cityAutoLoad` is falsy.
- **W-NOCLEAR** — No `#city-clear-btn` DOM created. `A.cityClear` function still exists
  (it is the cascade engine reused by eviction + queue reset). Proof: grep shows the button
  block gone, the function present.
- **W-MOBILE-DEFER** — On mobile (`A._isMobile`) auto-load does NOT run; click-to-stream
  stays (mobile = demo, desktop-first). Proof: auto-load block guarded by `!A._isMobile`.
- **W-EVICTION-INTACT** — `node tests/test_s285_eviction.js` still passes (no regression to
  tag/budget/evict).

## Design notes (why this is safe without the ARC gate yet)

Current streaming is still all-disciplines per building (the geo ARC-only gate is the NEXT
task). So per-building resident is heavy. To avoid (a) OOM and (b) the LRU-churn failure
where draining the whole nearest-first queue ends with the FARTHEST buildings resident, the
auto-load **stops at the budget wave-front** instead of queueing-and-evicting through all.
That keeps the nearest cluster resident and triggers no eviction.

## NEXT (not this task — see [[S285_CITY_EVICTION]] + city design thread)

1. geo.db ARC-only gate (meta stays full) — makes each building ~10× lighter.
2. Per-archetype ARC instancing OR camera-following wave (bbox/shell everywhere + budget-sized
   ARC-geo bubble).
3. Distance eviction (keep nearest, evict farthest) calibrated to the witnessed ~290k knee —
   makes the wave camera-following and replaces the wave-front stop.
4. ARC-first stream order + sneak remaining disciplines (perceptual trick; impact-tested).
