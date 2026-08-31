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

### ✅ §R10 SHIPPED 2026-08-30 — bim-ootb PR #1588 MERGED + LIVE (sw v1111), verified by fetching back

**`MAXQ_STILL_BUDGET = { taa: 8, ao: 12 }` — 20 composer renders per baked frame, was 40.**
Live check: `CACHE_VERSION = 'v1111'` and `MAXQ_STILL_BUDGET = { taa: 8` both present in the
deployed files. Alt+S stills are UNCHANGED at 40 — the override is bake-only and released on all
5 exit paths.

#### RESULT — HHS_Office_Federated, one SEEDED load per condition, scored against a CONTROL
| taa | ao | renders | RMS vs control | verdict | proj ms/frame | vs 1989 |
|---|---|---|---|---|---|---|
| 12 | 16 | 28 | 0.21 | AT THE FLOOR | 1539 | 77% |
| **8** | **12** | **20** | **0.24** | **AT THE FLOOR — SHIPPED** | **1164** | **59%** |
| 8 | 8 | 16 | 0.37 | AT THE FLOOR | 1089 | 55% |
| 4 | 8 | 12 | 21.33 | **DISTINGUISHABLE** | 789 | 40% |

Noise floor **RMS 0.21** (0-255 luma). 40 renders and 20 renders are the same image to within a
fifth of one luma level; 4/8 sits 100× above the floor, which is what a real difference looks like.
8/8 also measured at the floor — **8/12 shipped anyway for one AO step of margin**, because this is
ONE pose on ONE building and interior corners are what AO carries. Margin costs 75 ms/frame.

#### Hospital projection — the honest arithmetic, NOT a measured bake
The user's observed wall clock was **~3 h** for 3,447 frames. Only the FRAME LOOP shrinks:
- frame loop today: 3,447 × 1.989 s = **1 h 54 m**
- frame loop at 8/12: 3,447 × 1.164 s = **1 h 7 m**
- the remaining ~1 h (staging, stitch, any §MAXQ_HIDDEN_PAUSE) is **unchanged**
→ **~3 h becomes ~2 h 10-15 m.** A ~47-minute saving, not a halving of the wall clock. The 1,164 ms
comes from the user's OWN measured budget (75.0 ms per TAA render, 18.75 per AO render, 339 tail),
never from the headless probe — swiftshader ms mean nothing for an RTX 4060.

#### THREE MEASUREMENT METHODS FAILED FIRST — do NOT re-walk them
1. **Synthetic camera pose → empty frustum.** A bbox-derived interior eye-level pose sees nothing on
   HHS. Five conditions scored `meanRGB 0.00` with an IDENTICAL md5 — a perfect "no quality loss"
   that meant nothing. The tell was in the log all along: `§PHOTO_AO avgRenderMs=23` against
   **2520.8** for a fold that really renders. A witness that invents its own camera can invent its
   own emptiness — use the viewer's own framing and GATE on a non-black pre-fold frame.
2. **One page load per condition → the scene RESEEDS.** A control at IDENTICAL settings across two
   loads differed by **RMS 32.19**, 22.86% of pixels off by >8 — larger than every effect being
   measured. §SESSION_2026-08-30 dead-end 5 ("one condition per page load") is right for
   §PHOTO_GRADE and WRONG here; a documented rule's trade-off must be re-checked for each new
   measurement.
3. **All conditions on one page load → only the FIRST fold does real AO work.** `avgRenderMs` 768.9,
   then 4.8 / 9.6 / 14.9 / 14.2 / 1.1; the same-settings control landed 30 luma from row 1.

**THE FIX:** seed `Math.random` before any page script. `effects.js` makes **13 `Math.random()`
calls** while staging — staffage species and placement, and the 410→200 night-light subset. That is
the ONLY reason two loads differ. Seeded, the warm loads agree to **RMS 0.21-0.37**.

**Known harness artifact:** the FIRST load in a fresh browser is cold-cache and differs by
**RMS 26.24** from a warm load at its OWN settings. The reference row is excluded from scoring.

**Scorer bug worth remembering:** its first cut took the **MAX** warm pair as the noise floor, which
let the 4/8 outlier define the floor and then wave itself through (it recommended 4/8 at RMS 21.33).
The floor must be the **CLOSEST** warm pair — the smallest difference the harness can still resolve.

#### Files
`witness_maxq_frame_budget.js` (states its own verdict, shells out to the scorer) +
`score_frame_budget.py` (RMS + pairwise matrix + floor gate), both in bim-ootb.

---

## §R11 / §R12 — first-Alt+S latency and the real memory picture (SPEC ONLY, 2026-09-01, awaiting go)

**User, on v1111, Hospital:** *"we need to reduce its mem hog, as it seems slow to come on first
time, but also when testing here with X section cut in joint action."*

**Source: the user's OWN pasted console from a real session on their RTX 4060** — better evidence
than any headless probe, and it is what this section is built on. Nothing here is re-derived.

### §R11 — why the FIRST Alt+S is slow, MEASURED
| | first Alt+S | second Alt+S |
|---|---|---|
| `§MEP_SMOOTH_NORMALS` | **8,923.6 ms** | — (once per session, `A._mepSmoothDone`) |
| `§STILL_REFINE done elapsedMs` | **17,933** | **6,868** |
| `§PHOTO_AO done totalMs` | 576 | 625 |
| **total** | **≈ 27 s** | **≈ 7 s** |

**The 20-second gap is ALL one-time work sitting on the Alt+S critical path:**
1. **`§MEP_SMOOTH_NORMALS ms=8923.6`** — the curve smoothing (geoms=1705, ranges=14621,
   vertsSmoothed=23,735,190, vertsKeptHard=8,874,063). Biggest single item, ~45% of the gap.
2. **Assets arriving DURING the fold, which restart it.** Run 1 logs `§STILL_REFINE_RESTART
   cam-moved — accumulation restarted` **twice**, with `§LAYER2_HDRI_READY` and `§GROUND_MAP
   key=earth` landing in between. Every restart throws away the samples accumulated so far.
3. **First-press-only inits:** `§PHOTO_AO_INIT_OK` (lazy N8AO), `§MIRROR_ROOM_PROBE built` (later
   presses log `reused`).

**✅ SHIPPED 2026-09-01 — bim-ootb PR #1590 MERGED + LIVE (sw v1112), witness 6/6.** Verified by
fetching the deployed files back: `CACHE_VERSION = 'v1112'`, `PHOTO_PREWARM` in `effects.js`,
`_photoPrewarm` in `streaming.js`. `A._photoPrewarm()` is called from the point streaming.js's own
comment already calls "the model is fully streamed here", runs at idle (`requestIdleCallback`
timeout 8000), and does mepSmooth + HDRI + ground-texture warm. The staging path KEEPS its own call
as a fallback — degrade, don't disable. §PHOTO_AO deliberately untouched (576 ms of a 27 s press).
Witness order proof: `§MEP_SMOOTH_NORMALS ms=1339.4` at index 0, `§PHOTO_PREWARM did=[mepSmooth,
hdri,groundTex]` at 1, `§STILL_REFINE start` at 3 with nothing left to do. G-PW-2 and G-PW-3 are
load-bearing TOGETHER — "prewarm fired" alone would also pass if the pass simply ran TWICE, which is
worse than the original bug.
**HONEST LIMIT:** the HDRI is STARTED earlier, not guaranteed FINISHED. The witness presses Alt+S
immediately and `§LAYER2_HDRI_READY` still lands after `§STILL_REFINE start` there; a real user
takes seconds so it should resolve first, but the guarantee is "started sooner", not "done".
**NOT YET CONFIRMED ON HOSPITAL** — the 8,923.6 ms figure is from the user's log; the next real
Hospital session's `§PHOTO_PREWARM` line is what proves the saving landed.

**Original proposal (kept for the record):** move all of it off the Alt+S press and onto idle-after-streaming. The smoothing pass
is already once-per-session and idempotent behind its own guard, so this is a scheduling change, not
a behaviour change. Same for pre-warming the HDRI, the ground map, the N8AO pass and the mirror
probe. **Expected: first Alt+S ≈ 27 s → ≈ 7-9 s, matching subsequent presses.** §PHOTO_AO is NOT
worth touching — 576 ms of a 27 s press.

### §R12 — the memory picture, and what it is NOT
`§NIGHT_MEM_WITNESS heapMB` across three full Alt+S cycles: **1565.9 → 1572.0 → 1572.9 → 1578.3 →
1599.8**. So ~**17 MB retained per cycle on a ~1.57 GB baseline — about 1% per press.**

**The staging is NOT the hog, and two suspicions were checked and cleared:**
- **`windowLights` growing 3775 → 4536 → 4755 is NOT a leak.** Skyline box sizes are randomised per
  rebuild (`winCount = floor((bw*bh)/14)` with random `bw`/`bh`), so the count legitimately differs.
  The skyline group and its Points cloud are disposed at `effects.js:632-635`.
- **`§ALTS_MEM_HOG` (2026-08-16) is live, not pending.** `_disposePhotoProps()` really is called on
  the real-exit path (`effects.js:3684`). `glowMatKeys` cycles 98 → 0 → 98 → 0 in the log, which is
  the clean-up working.

**The baseline is the hog, and it is the model, not the still:** 63,182 elements,
`§SPLIT_GEO_LOADED size=229MB` resident in WASM linear memory for the life of the tab,
`§CONTRACT_CHECK batch=38169 instanced=25013`. For scale, the measured Terminal profile is heap
1,226 MB with **geometry attributes 469 MB** (position 198.6, normal 198.6, index 71.8).

**Proposal: MEASURE Hospital before proposing any fix.** Run the same `§MEM_PROBE` breakdown that
Terminal has, so the 1.57 GB is split into geometry attributes / textures / WASM DB / everything
else. Aiming memory work at a 1%-per-press creep while a 229 MB DB and ~470 MB of attributes sit
underneath it would be the square peg this file's own §0a warns about.
**Closed, do not reopen:** dropping the `normal` attribute is WITHDRAWN (it breaks §TRIPLANAR's
`vTriWorldNormal` and all texturing collapses), and the 129.6 MB "weldable" figure is an upper bound.

### ⛔ The section-cut case is NOT measured — the log cannot answer it
The third Alt+S, the one taken after `§SECTION ON axis=Y range=[-45.3, 90.5]`, ends with
`§STILL_REFINE soft-cancel (camera move) elapsedMs=7887` — **it was interrupted, so it never
produced a completed timing.** The repeated `[GridScissors] §GRID_SCISSORS skipped — overlay not
active` lines are no-ops and cost nothing. To answer "slow with X section cut" a run is needed where
the fold is left alone until `§STILL_REFINE done`, with and without the cut, same pose.
