# ⚠ DO NOT REMOVE — scope: consolidation + perf/mem STUDY only, no code changes without a written
spec section signed off after this study. Read the log after every run you do perform.

# CPE (Alt+C) + 4D Schedule — consolidated perf/memory study

## Goal
Both lanes below have been built incrementally across many separate `prompts/#.md` files, each solving
one feature at a time. Read them ALL TOGETHER (not one at a time) and look for: (a) duplicated or
overlapping machinery that could be one shared piece instead of several near-identical ones, (b) memory/
perf costs that are individually "fine" but stack on large buildings, (c) a concrete plan to make baking/
activation on LTU-scale buildings (LTU_AHouse, 122,667 elements) meaningfully faster. Output is a written
finding + refactor proposal, not code — this is Spec-First like everything else in this repo.

## Files to read (verify this list is still current — `ls prompts/CPE_*.md prompts/CINEMA_*.md
prompts/4D_*.md prompts/TM_*.md prompts/GANTT_*.md` before trusting it, files get added over time)

**Alt+C / Cinema Path Editor (CPE) family:**
- `CINEMA_PATH_EDITOR.md` — the main spec/handoff, largest file, read its §SESSION HANDOFF first
- `CPE_POV_WALK_PATHING.md` — walk finger mode (just shipped + a roll-snap fix 2026-08-11)
- `CPE_WALK_GAMEPAD_NAV.md`
- `CPE_WALK_WEBXR_FINDPANEL.md` / `CPE_WALK_WEBXR_VR.md`
- `CINEMA_DELIGHT_BATCH.md` / `CINEMA_FIND_TO_FILM.md`

**4D Schedule family:**
- `4D_SCHEDULE_PERFECTION.md` — the main lane (auto-schedule/CPM generator), read its §SESSION CLOSEOUT first
- `4D_CAPTURE_AND_FALLBACK.md`
- `GANTT_ACCURACY.md`
- `TM_SCHEDULE_EDITOR.md`
- `TM_INCREMENTAL_RENDER_PERF.md` — already perf-focused, likely the most directly relevant one
- `TM_4D5D_VARIANCE_LANE.md`
- `TM_STREAM_REBUILD_COALESCE.md`
- Skim `TM_S4_SHOPFLOOR_BUILD.md` / `TM_SHOPFLOOR_COSTING_SPEC.md` / `PP_ORDER_ZOOM_TM_SPEC.md` too —
  unclear if they share the same underlying TM/schedule machinery or are a separate ERP-shopfloor
  overlay; figure that out as part of the study, don't assume either way.

## Known numbers already on record — extract these, don't re-derive them from scratch
- CPE replan (`_replanFilm`): 291ms typical, up to 1218ms on Hospital-class buildings
  (`CPE_POV_WALK_PATHING.md` §CPE_REPLAN_SLOW / `CINEMA_PATH_EDITOR.md` §CPE_PANEL_PERF).
- TM cold activation: ~2.1s on first Play (`CINEMA_PATH_EDITOR.md` §CPE_PANEL_PERF item 1) — a
  pre-arm idea is already named there, unstarted.
- DLOD flip-storm during a continuously-moving camera (walk / any non-idle vfCam): flips_mean=2671,
  FPS→53 (`CINEMA_PATH_EDITOR.md` §CPE_PANEL_PERF item 3) — flagged as a known landmine, deliberately
  untouched in every lane so far.
- TM incremental render: ~2ms delta-path vs ~23ms full render at 16k objects
  (`TM_INCREMENTAL_RENDER_PERF.md`) — LTU_AHouse is 122,667 elements, ~7.7x that object count; whether
  the delta-path still holds its advantage at that scale is exactly the kind of question this study
  should answer with a real measurement, not an assumption.
- The big one, NOT CPE/4D-specific but the dominant cost on any large building: `sql.js` loads the
  ENTIRE db file into WASM linear memory, no paging — LTU_AHouse alone is ~440MB permanently resident
  (`meta.db` 43MB + `geo.db` 397MB) for the life of the tab (memory `project_ltu_ahouse_memory_architecture`).
  CPE/4D's own state (film plans, schedule cursors, DLOD culling structures) sits on TOP of that
  baseline — worth measuring how much CPE/4D itself adds vs. how much is just the sql.js floor, since
  a refactor inside CPE/4D can't fix the floor.

## What "study well" means here
- Read every file above fully, not just its closure/handoff section — the perf numbers and landmines
  quoted above are scattered through each file's dated history, not summarized in one place yet.
- Look specifically for: two lanes independently reinventing the same mechanism (e.g. both CPE and TM
  have their own render-triggering / cursor-driving conventions — is there duplicated logic that could
  be one shared primitive?), any per-frame or per-activation cost that scales with element count rather
  than staying flat, and any of the several "known landmine, deliberately untouched" notes across these
  files that keep recurring — a landmine mentioned in 3 different files by 3 different sessions is a
  refactor candidate, not a coincidence.
- Ground every finding in a real measurement (§-log numeric truth, same FUNDAMENTAL LAW as the rest of
  this repo — no screenshots, no "should be faster," a number before and a number after) run against
  LTU_AHouse specifically, not just the small residential buildings (Duplex/SampleHouse) most of these
  lanes were originally witnessed against.

## Deliverable
A new `prompts/CPE_4D_PERF_MEM_FINDINGS.md` (or append here if it stays short) with: a ranked list of
refactor/consolidation opportunities, each citing which files/functions it touches and the measured
before-number; and an explicit list of what was investigated and found to be NOT a lever (so the next
session doesn't re-walk the same ground). No code changes in this pass — hand the findings to a
follow-up session with a written spec once the user picks which lever(s) to act on.

## ✅ EXECUTED same session (user: "proceed to do the fixes") — see FINDINGS §3b for the ledger
R5 merged (#1306, §PERF_INCR_DEFER) · R3 merged (#1305, §CPE_REPLAN_LAZY, equivalence 0.0) ·
R2 auto-merge armed (#1304, chunked kernel_ops writer, 4 witness suites green) · R1 implemented +
A/B-witnessed on `fix/maxq-bake-staging-keep` (§MAXQ_STAGE_KEEP — staging 2× per bake vs 29×,
frontier lines byte-identical) · R4 ⛔ BLOCKED both halves on documented doctrine (G-CPE-SOLE-OWNER;
§Z_STACK_XRAY_STAGING "nothing survives TM off") — user ruling needed, recorded in FINDINGS §3b.

## ✅ DONE 2026-08-12 → `prompts/CPE_4D_PERF_MEM_FINDINGS.md`
Study complete, no code changed. 9 ranked levers (R1 bake staging churn — decomposed to named code;
R2 land `fix/gantt-refold-hang`; R3 §CPE_REPLAN_LAZY; R4 TM activation pre-arm + x-ray cache;
R5 stream-rebuild coalesce; R6 memory/cache-revalidation + a NEW blocker: the re-extracted LTU split
pair is unstreamable on a fresh cache, `elements_meta` lost its `building` column; R7 one support
predicate; R8 one date-layout cursor; R9 DLOD flip-storm, still last). Corrections to this file's
own premises are in the findings §1 — notably the delta path WAS measured at LTU (2.0 ms, holds),
and the 440MB floor is now ~210MB (2026-08-10 re-extract). Live measurement was stopped mid-study on
the user's directive (real Hospital bake running); the bake-heaviness question was answered from
code instead (findings §2: per-frame staging teardown/rebuild + 250 ms settle + 4096² shadow ×~40
renders/frame + per-frame-unique sun + #1300's per-frame traverse).

## ▶ RESUME 2026-08-17 — R1-R5 done since 2026-08-12 (FINDINGS §3b), R6(b) ALSO already done
(PR bim-ootb#1328, `§SINGLE_BLD_FALLBACK` in `streaming.js` — untracked in FINDINGS.md, worth a
one-line correction there next time it's touched). **R6(a) — cache revalidation — is the one
piece of R6 still genuinely unbuilt**, confirmed by direct code read: `scene.js A.cachedFetch`
(:1116) is unconditional cache-first — a cache HIT (:1133) returns the stored blob immediately,
zero network check, ever. This is why R6's own text warned "returning users never fetch a
re-uploaded DB" — that is exactly what's live today, still, for every building.

### §R6a SPEC — cache revalidation, Spec-First before implementing
**Mechanism:** on a `cachedFetch` cache HIT, before returning the stored blob, fire a small
`fetch(url, {method:'HEAD'})` (via a real IndexedDB-stored comparator, not a body download) and
compare against a value stored alongside the blob at write time. If they match (or the HEAD
fails/times out — **fail open**, never block/break offline use), return the cached blob exactly as
today. If they differ, treat it as a genuine cache miss and fall through to the existing
fetch+cache-write path unchanged.

**Comparator: ETag primary, Content-Length secondary.** Verified live (`curl -I` against the OCI
bucket, `Clinic_extracted.db`): `access-control-allow-origin: *` and
`access-control-expose-headers` includes both `etag` and `content-length` — a cross-origin HEAD
from `red1oon.github.io` can read both. ETag is the more precise signal (OCI's etag is tied to
`version-id`, changes on every real re-upload even at identical byte size); Content-Length is the
fallback for any server/path that doesn't set one (e.g. a relative same-origin GH-Pages path,
unverified whether it always sets a custom ETag). **If NEITHER header is present on the HEAD
response, skip revalidation entirely** — same as today, no regression.

**Storage: a NEW `revalidation` IndexedDB object store, not a reuse of `timestamps`.**
`timestamps`' value is a plain number consumed by `_evictOldest`'s numeric sort
(`entries.sort((a,b) => a.ts - b.ts)`) — changing its shape to an object would silently break LRU
eviction (NaN comparisons). `A.openCacheDB` is already versioned (`indexedDB.open(NAME, 2)`,
`onupgradeneeded` already added `timestamps` at v2) — bump to **v3**, add the `revalidation` store
in the SAME `onupgradeneeded` branch, write `{etag, contentLength}` in the SAME transaction as the
existing `dbs`/`timestamps` write in `cachedFetch`'s cache-write path (:1229-1263). Any profile
that falls back to `_openCacheDbUnversioned` (already-higher stored version, `VersionError` path)
never gets the new store — for those, `revalidation.get(key)` returns undefined, which is the same
"skip, trust cache" fail-open branch, so that path is unaffected too.

**Timeout:** `AbortController`, 4s — a hung/offline network must not add latency to a cache hit
beyond a bounded window; on abort, fail open exactly like a HEAD failure.

**Logging (this project's own FUNDAMENTAL LAW: numeric §-tag proof, not assumption):**
`§CACHE_REVALIDATE_OK url=… etag=match|contentLength=match` (cache used, fast path),
`§CACHE_REVALIDATE_STALE url=… old=… new=…` (cache discarded, real re-fetch follows — this is the
line that proves the whole feature works, since it's currently IMPOSSIBLE for this to ever fire),
`§CACHE_REVALIDATE_SKIP reason=no-etag-or-length|network-fail|timeout|no-store` (fail-open path,
expected on older profiles and offline use).

**Witness:** a scripted test that (1) primes the cache with a fake stored ETag that does NOT match
the real server's current ETag, calls `cachedFetch` on a small real file, and asserts
`§CACHE_REVALIDATE_STALE` fires + the blob returned matches a fresh fetch, not the stale cached
one; (2) primes with the CORRECT current ETag and asserts `§CACHE_REVALIDATE_OK` fires + zero
bytes downloaded (network call count for the body fetch = 0, only the HEAD ran); (3) simulates a
HEAD failure (bad URL / offline) and asserts the stale cached blob is still returned (fail-open,
no exception, no user-visible break).

**Explicitly NOT in scope for this pass:** the OPFS/sqlite-wasm paging structural lever (R6's "(b)
THEN" — the 210MB floor itself). This pass only stops SERVING STALE DATA to returning users; it
does not reduce how much memory a fresh load uses.

### ✅ §R6a SHIPPED 2026-08-17 — PR bim-ootb#1418 MERGED+LIVE, R6 fully closed
Implemented exactly as specced above: `openCacheDB` bumped v2→v3 (new `revalidation` store, both
create sites), `cachedFetch`'s cache-hit branch now calls `A._revalidateCache` before returning,
the write path stores `{etag, contentLength}` in the same transaction as the blob, `_evictOldest`
cleans up the comparator on eviction too. `sw.js` `CACHE_VERSION` bumped in the SAME PR this time
(v1054→v1055) — learned from the earlier same-day miss on PR #1409 where forgetting this made a
real fix invisible to every already-installed user.

**Live-verified headless, both comparator paths:**
- Content-Length path (local file, 4-call sequence): call 1 real miss + `§CACHE_WRITE_OK`; call 2
  unchanged server-side → `§CACHE_REVALIDATE_OK contentLength=match`, cache used, no re-download;
  call 3 comparator corrupted to a wrong value directly in IDB → `§CACHE_REVALIDATE_STALE
  old=999999999 new=5675`, real re-fetch (`§CACHE_WRITE_OK` fires again), comparator self-heals to
  the correct value; call 4 `window.fetch` monkey-patched so HEAD throws → `§CACHE_REVALIDATE_SKIP
  reason=network-fail`, did NOT throw, returned the (still valid) cached blob — fail-open confirmed.
- ETag path (real cross-origin OCI object, `Hospital_meta.db.sql`): `§CACHE_REVALIDATE_OK
  etag=match` on the second call — confirms OCI's `access-control-expose-headers` genuinely lets a
  `red1oon.github.io` origin read `etag`/`content-length` off a HEAD, not just a same-origin one.

**R6 is now fully closed, both halves.** No further action needed on this lever; FINDINGS.md §R6
corrected with a pointer here rather than duplicated.

## Lane status: R1-R6 all shipped and live. R7 (support-predicate consolidation), R8
(date-layout-cursor consolidation), R9 (DLOD flip-storm, deliberately last) remain unstarted —
next session picks one if the user wants to continue this lane; none are costed as urgent.

---

## §R10 §MAXQ_FRAME_BUDGET — the only lever that moves the bake clock (SPEC ONLY, 2026-08-30, awaiting user go)

**User:** *"Will rebuild with the new code to see the intended 'Reveal' round on HHS_Office for
speed. Can we still reduce mem and increase frame speed or wait for the background ops?"*
Specs first, **not to be built yet.**

### Where the time actually goes — MEASURED, already on record
Hospital, 3,447 frames, `perFrameMs=1989` (`CINEMA_PATH_EDITOR.md` §SESSION_2026-08-30):
- §STILL_REFINE ~1,200 ms = **62%**
- §PHOTO_AO ~450 ms = **23%**
- everything else = the 15% tail.

Reading the code for what those numbers ARE: `effects.js:4811` folds TAA until `accumulateIndex >= 16`
and `effects.js:3738` sets `STILL_AO_FRAMES = 24`. **So every exported frame costs 16 + 24 = 40 full
composer renders.** 3,447 × 40 = **137,880 composer renders** for one Hospital film.

**This is why the session record already says "do not expect HUD or smoothing work to move the bake
clock."** Nothing outside those two constants is worth touching for speed.

### The proposal — R10, quality-per-second, measured not guessed
Make the two budgets bake-time settings instead of stills constants, then **measure the quality cost
of each step down** rather than picking a number:
- sweep TAA 16 → 12 → 8 → 4 and AO 24 → 16 → 12 → 8 on ONE frame, one page-load per condition
  (**§SESSION_2026-08-30 dead-end 5: same-page A/B is invalid for stills** — the second Alt+S logs
  `avgRenderMs=0.7` against the first's `94.5`, the AO phase does no real work twice);
- score each against the 40-render reference with a numeric image metric (per-pixel RMS + a
  high-frequency/noise term), never by eye;
- ship the lowest pair whose RMS stays under a stated threshold, as a named preset.

**Arithmetic, not a promise:** 40 → 20 renders/frame halves the bake. Hospital 1 h 54 m → ~57 m,
HHS_Office (1,661 frames last bake) proportionally. Whether 20 is visually acceptable is exactly
what the sweep decides — that number is the *ceiling* on the win, not a claim about quality.

### Memory — the honest read for HHS_Office
- **HHS_Office_Federated is 6,839 elements** (cached run), against Hospital's 63,182 — **9.2× smaller**.
  It is the fastest fleet member to iterate on, which is the right call for seeing the Reveal round.
- The measured memory profile is Terminal's, not HHS's: heap 1,226 MB, geometry attributes 469 MB
  (position 198.6, normal 198.6, index 71.8), 17.35M verts / 864 geometries, textures only 4.1 MB.
  **At 6,839 elements HHS is nowhere near that**, so memory is very unlikely to be its limiter — it
  should be measured (`§MEM_PROBE`) before any memory work is aimed at it.
- Two memory routes are already CLOSED and must not be re-walked (§SESSION_2026-08-30): dropping the
  `normal` attribute to save 199 MB is **WITHDRAWN** (it breaks §TRIPLANAR's `vTriWorldNormal` and all
  texturing collapses), and the 129.6 MB "weldable" figure is an **upper bound** that assumes normals
  can merge — flat surfaces need split normals, so the real saving is far smaller.
- The genuine floor is not CPE/4D's: `sql.js` loads the entire DB into WASM linear memory with no
  paging. A refactor inside CPE/4D cannot move it.

### Ordering — the direct answer to "or wait for the background ops?"
**Do not wait.** They are independent and they do different things:
- **§MAXQ_BACKGROUND** does not make a single frame faster. It buys back *your attention*, not time.
- **§R10** is the only thing that shortens the bake, and it is the smaller change — two constants, a
  sweep, and an image metric, versus a new scheduler with two unverified browser behaviours.
- R10 first also makes the background work cheaper to test, because every probe run is shorter.

### Still unstarted in this lane, unchanged
R7 (support-predicate consolidation), R8 (date-layout-cursor consolidation), R9 (DLOD flip-storm,
deliberately last). None are costed as urgent and none of them move the bake clock either.
