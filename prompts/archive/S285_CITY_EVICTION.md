# S285 — City Mode: Bounded Working Set (Memory-Budget LRU Eviction)

> # ⚠ DO NOT REMOVE
> **Scope:** Bound the city scene's live memory so the viewer scales to a million
> elements from *arbitrary* heterogeneous IFCs, not just the 52 that happen to fit.
> The city is the workshop; buildings are arbitrary test inputs. The deliverable is a
> scale-robust viewer, proven by building-agnostic metrics — not per-building tuning.
> **Read the output log (`§CITY_*` console lines) after every run before any conclusion.**
> Honour this block until the spec is DONE.

## Problem (proven by Firefox log, 2026-05-29)
Click-streaming buildings in city mode accumulates scene geometry without bound:
`streamed` climbs 48k → 111k → 233k → … → **507,986** elements, and **nothing is
ever freed**. Firefox is OOM-killed on cumulative load. The only LRU that exists
(`_evictOldest` / `§CACHE_EVICT_LRU`, scene.js) is for the **IndexedDB file-blob
cache**, not live scene objects. `cityClear` (city.js:510) is all-or-nothing (the 🗑
button). There is no bounded working set.

This is the one hard scale gate. Heterogeneity is already solved empirically — every
archetype renders in full facade, ground-anchored, correct colors. Memory is the gate.

## What grows, what doesn't (verified in code)
| Resource | Owner | Grows with same-archetype repeats? | Bounded? |
|---|---|---|---|
| `meshCache[hash]` BufferGeometry | **shared**, hash-keyed | No (T27 & T51 Terminal reuse hashes) | Yes — unique geometry across archetypes |
| BVH trees (on shared geos) | shared | No | Yes (`§BVH_DEFERRED built` plateaus at 125,814) |
| **InstancedMesh** `instanceMatrix` | **per-building** (streaming.js:715) | — | **No** |
| **BatchedMesh** vert/index buffers | **per-building**, *copies* geo (streaming.js:770) | **Yes** — each repeat re-copies | **No** |
| fallback/merged **Mesh** geo | **per-building** (owns merged geo) | Yes | **No** |

**Conclusion:** the unbounded growth is entirely in **per-building scene objects**.
Shared geometry + BVH are bounded and reused across repeats. Therefore eviction needs
**no refcounting** — we only ever dispose resources a building *owns*, never shared geo.
This is the "works on all cases" guarantee: same-archetype repeats (T27/T51, T24/T48,
T25/T49, T13/T37) keep working because the shared geometry under them is never disposed.

## Design — simple, no-refcount, memory-budget LRU

### 1. Tag scene objects per building — ONE site, not six
At stream-COMPLETE (streaming.js:411, where `buildingsRendered.add(activeBuilding)`
already fires), traverse the scene once and stamp `userData.building = activeBuilding`
on every **untagged streamed** object (`isInstanced || isBatched || isMerged`, and
**not** `isBboxPlaceholder`). Prior buildings' objects are already tagged, so only the
just-streamed building's objects get this building's name. Single site → no drift across
the 6 creation paths.

### 2. Byte tally — computed fact, not a heuristic, cross-browser
`performance.memory` is **Firefox-unavailable** (log proves it), so the budget cannot
read live heap. Instead, at the same stream-complete pass, sum the **owned** buffer
bytes of the newly-tagged objects:
- **InstancedMesh:** `instanceMatrix.array.byteLength` (+ `instanceColor` if present).
  *Do NOT count `.geometry`* — it's shared in `meshCache` (bounded).
- **BatchedMesh:** sum `geometry.attributes.*.array.byteLength` + `geometry.index.array.byteLength`
  (it owns a copied merge) + per-instance matrix bytes.
- **fallback/merged Mesh:** its `geometry` buffer bytes (owns merged geo).

Store `A._cityBuildingBytes[name]`; maintain `A._cityResidentOrder = [names…]` in stream
order; `runningBytes = Σ`. This is deterministic and identical on every browser.
On Chromium, `§CITY_MEM` real heap **validates/calibrates** the owned-bytes proxy.

### 3. Memory budget (not a fixed count)
`A._cityMemBudgetMB` — default **1024 MB of owned buffers** (tunable). Owned-bytes is a
*proxy* for real heap (real heap also carries GPU mirrors, JS overhead, shared geo, BVH);
calibrate the proxy↔heap ratio once via Chromium `§CITY_MEM`, then set the budget so peak
real heap stays under a safe ceiling. Budget on *bytes*, never on a building count — a
count fails the "arbitrary buildings" requirement (one LTU ≠ one SampleHouse).

### 4. Eviction (on stream-complete, after tagging + tally)
```
while (runningBytes > BUDGET && residentOrder.length > 1) {
  victim = residentOrder.shift()          // oldest; never the just-streamed building
  evictBuilding(victim)
}
```
`evictBuilding(victim)`:
- `objs = collectMeshes(o => o.userData.building === victim && !o.userData.isBboxPlaceholder)`
- per obj: `scene.remove(obj)`; then
  - `isBatchedMesh` → `obj.dispose()` (frees owned buffers; also disposes its BVH)
  - `isInstancedMesh` → `obj.dispose()` (frees instanceMatrix GPU buffer). **Do NOT**
    `obj.geometry.dispose()` — shared.
  - plain/merged Mesh → `obj.geometry.dispose()` (owned merged geo).
  - **Never dispose `obj.material`** — `_getMaterial` returns shared cached materials
    (streaming.js:264). *(Note: `cityClear` line 521 disposes materials; safe only because
    it nukes everything. Partial eviction must not.)*
  - delete `A._instanceMeta[obj.id]`; delete `A.guidMap` keys `== obj.id` or prefixed
    `obj.id + '_'` (precedent: streaming.js:1191).
- `_cityRestoreBuildingBboxes(victim)` — bring the victim's bboxes back (see §5).
- `A.buildingsRendered.delete(victim)`; `delete A.savedStreams[victim]` (re-click re-streams clean);
  `runningBytes -= A._cityBuildingBytes[victim]`; `delete A._cityBuildingBytes[victim]`.
- Rebuild DLOD refs (`A.dlodEnable()` / `dlodTick`) — mesh set changed.
- `console.log('[S285] §CITY_EVICT building='+victim+' freedMB='+… +' residentNow='+… +' bytesNow='+…)`

### 5. Per-building bbox restore (new)
`_cityRestoreBboxes()` (city.js:38) restores **all** hidden bboxes — too coarse for
partial eviction. Two small changes:
- In `_cityHideBuildingBboxes` (city.js:17), stamp each pushed `_cityHidden` entry with
  `building: buildingName`.
- Add `A._cityRestoreBuildingBboxes(name)` — restore + remove only that building's entries.
  Keep `_cityRestoreBboxes()` (all) for `cityClear`.

## Files
- `bim-ootb/viewer/streaming.js` — stream-complete tag + tally + eviction trigger (≈ line 411).
- `bim-ootb/viewer/city.js` — `evictBuilding`, byte helpers, `_cityRestoreBuildingBboxes`,
  `_cityHidden` building stamp, `A._cityMemBudgetMB` default.
- `bim-ootb/viewer/sw.js` — bump `CACHE_VERSION` (next after v526).

## Witness claim (state BEFORE implementing)
> **W-CITY-EVICT:** Clicking through arbitrary buildings indefinitely, peak JS heap
> **plateaus** under a fixed budget instead of climbing monotonically; `§CITY_EVICT`
> fires when `runningBytes > BUDGET`; an evicted building's **bboxes reappear**; and
> **re-clicking an evicted building re-streams it cleanly** (full facade, ground-anchored,
> `§CONTRACT_CHECK orphans=0`).

## Building-agnostic acceptance metrics (Chromium, `§CITY_MEM` + `§CITY_EVICT`)
1. **Peak heap plateaus** — stream ≥ 20 buildings; `§CITY_MEM` after each must stabilise
   (not monotonically climb). Proves the bound holds regardless of which IFCs.
2. **Frame time stays interactive during stream** — no O(N-all-meshes) main-thread stall
   on evict (eviction touches only the victim's objects).
3. **Draw calls** — unchanged behaviour (already good via BatchedMesh).
4. **Correctness on repeats** — after evicting T27_Terminal while T51_Terminal resident,
   T51 still renders (shared geometry intact). Pick still resolves on resident buildings.

## DO — Testing & Logging
All test output saved to a log file; read the log before any conclusion (Log Mandate).

### Whitebox §-log (primary — per CLAUDE.md "§-log first")
- `§CITY_EVICT building=… freedMB=… residentNow=… bytesNow=…` on each eviction.
- Extend `§CITY_MEM` to also print `ownedMB=runningBytes/1MB` so proxy↔heap is comparable.
- Verify: `bytesNow ≤ BUDGET` after the evict loop; `residentNow` set never grows unbounded.

### Playwright (secondary — wiring only, NOT value verification)
| Test | What | §-tag |
|------|------|-------|
| evict.1 | `A.evictBuilding` + `A._cityRestoreBuildingBboxes` exist | `§PW_EVICT_WIRED` |
| evict.2 | tagging stamps `userData.building` on streamed objects | `§PW_EVICT_TAG` |
| evict.3 | over-budget stream removes oldest building's objects from scene | `§PW_EVICT_DROP` |
Run `node deploy/dev/tests/audit_specs.js` after Playwright changes — must exit 0.

## Out of scope (separate items)
- BVH re-scan thrash (`Object.keys(A.meshCache)` walks full cache per stream, flat
  `built`, ms→219s) — efficiency fix, build-only-new hashes. Track separately.
- Color-mode hang (per-mesh `material.clone()` at city scale).
- Offline download-page split-DB size undercount (LargeCity models a building as one
  file; should sum the meta+geo set). Cosmetic; streaming renders LTU in full facade.

## DONE appendix (fill on completion)
- [ ] W-CITY-EVICT witnessed — paste `§CITY_EVICT` + `§CITY_MEM` plateau lines.
- [ ] Repeat-archetype correctness — paste post-evict pick/render proof.
- [ ] sw.js CACHE_VERSION bumped; deployed; smoke-tested.
