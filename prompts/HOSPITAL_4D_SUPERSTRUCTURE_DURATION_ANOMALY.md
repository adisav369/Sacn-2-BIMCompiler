# ⚠ DO NOT REMOVE — Hospital 4D Superstructure duration anomaly + share-button correction
# SCOPE: all items DONE/DIAGNOSED as of 2026-07-18 — see each item's ✅ header. Item 2's root cause
# (locale_loader.js productivity-map clobber) is found and evidenced but NOT YET FIXED — that needs
# a product decision (see "Fix NOT implemented" below) before a future session codes it. Read the
# log after every run. Read this whole file before touching code.

## Item 1 — ✅ DONE 2026-07-18: dropped `#tm-share`, PR #852
Removed button markup + pointerup listener in `viewer/time_machine.js` (branch
`fix/drop-tm-share-button`, worktree `/tmp/wt-drop-tm-share` off updated `main`@`a3fc220`).
`node --check` passes, `grep tm-share` returns nothing. Pushed + PR opened:
https://github.com/red1oon/bim-ootb/pull/852

### Original spec (kept for the record)
`bim-ootb` PR #850 (2026-07-18, `revert/tm-movie-export-and-gi-autoengage`) reverted the Movie
Export feature (see `prompts/archive/TM_MOVIE_EXPORT_RETIRED_2026-07-18.md` for the full story)
and, as part of that revert, restored the `#tm-share` button (`time_machine.js`, "Copy shareable
link") to its pre-Export-Movie state. **User correction: this was not requested — the share button
should be dropped, not restored.** The revert only needed to remove the Export Movie UI/mechanism;
bringing `#tm-share` back was this assistant's own unrequested addition, done without checking.
Next session: remove `#tm-share` (button markup ~`time_machine.js:1933`, listener
~`time_machine.js:2081-2089`) — do not replace it with anything unless told to; just drop it from
the panel row.

## Item 0 — ✅ VERIFIED 2026-07-18: NOT the BatchedMesh edit, safe to proceed to Item 2
A/B ran to completion (`witness_appearance_regression.js`, `/tmp/wt-tm-baseline` @ `a13bb0d` on
:8300 vs `/tmp/wt-tm-current` @ `a3fc220` on :8310, HHS_Office_Federated_extracted.db, identical
Jump-to-start → 3s Build → Stop burst on both): both landed on the **identical cursor**
`1780467199999` with **identical counts** — `visibleBatchSlots: 27/2716`, `visibleGuidMeshes: 0/0`
— and zero page errors on either side. Per this file's own decision rule (below), identical
counts at an identical cursor mean the regression is **not** in `renderAtTime`'s visibility logic
— the `BatchedMesh` frontier-box removal is cleared. The user's "appearance broken" / "schedule
hell on others too" report is a **separate, still-open question** — not yet re-diagnosed against
a live symptom, just decoupled from this session's own edit as prime suspect. If it resurfaces,
start fresh (don't assume it's the same root cause as Item 2's duration anomaly — that link was
never established either, only hypothesized).

## Item 0 (original spec, kept for the record) — element-appearance regression from THIS session's own edits
Before touching duration/rate logic at all: the user reported, in the SAME session that produced
this file, that "the appearance of elements are broken in this session" and, testing further,
"on others also worse schedule hell" (i.e. not Hospital-specific — seen on other buildings too).
This was raised immediately after item 2's "Superstructure built within an hour" observation, and
the user explicitly asked whether the two are connected ("So the duration anomaly is addressed") —
**that connection was never confirmed, only hypothesized.** A live A/B diagnostic (baseline
`a13bb0d`, pre-session, vs current `a3fc220`, post-revert PR #850) was set up but stopped
mid-run at the user's request ("just note in the prompts/# for it to verify first, closing") —
not completed, not ruled in or out.

**Prime suspect, if it turns out to be a real code regression and not just a Hospital data
quirk:** this session's own edit to the `BatchedMesh` frontier-visibility loop in `renderAtTime`
(`time_machine.js`, removing the yellow edge-box — see
`prompts/archive/TM_MOVIE_EXPORT_RETIRED_2026-07-18.md`). The edit looked structurally safe on
inspection (only removed box-creation statements inside an existing `if (frontier[bg])` block,
left `setVisibleAt`/`anyVis`/`_bmHasFrontier` untouched) and `node --check` passed, but it was
never verified LIVE against a real construction-reveal sequence, and it's the most invasive touch
to core visibility logic made this session. Do not assume it's guilty — confirm with the actual
A/B test (script scaffolding left at
`/tmp/claude-1000/-home-red1-bim-compiler/7b94e63d-d49b-4448-bcf1-9dc87c2f742e/scratchpad/
witness_appearance_regression.js`, not yet run to completion — that scratchpad may not survive to
a new session; treat it as a starting sketch, not a trusted artifact, and rebuild the comparison
if it's gone).

**How to verify (do this before anything else in this file):**
1. Two worktrees, same building, same op-log: one at `a13bb0d` (last commit before this session's
   PR #849/#850 touched `time_machine.js` at all), one at current `origin/main`.
2. Drive TM to the identical cursor position on both (Jump-to-start, then an identical short
   Build-then-Stop burst, or better — set `_cursor` directly to the same absolute timestamp on
   both if a reliable hook exists).
3. Count visible vs total guid-bearing meshes AND `BatchedMesh` visible-slot counts on both at
   that identical cursor. If current shows dramatically MORE visible/placed than baseline at the
   same cursor, that's the regression, and it directly explains "built within an hour" as a
   visibility bug, not a duration/rate bug — **fix that first, then re-observe whether item 2's
   complaint still exists at all before touching SEQUENCE_RULES/LABOR_RATES.**
4. If baseline and current show IDENTICAL counts at the same cursor, the regression is NOT in
   `renderAtTime`'s visibility logic, and item 2 below is a real, separate duration-generation
   question — proceed to item 2 as originally scoped.

## Item 2 — ✅ DIAGNOSED 2026-07-18 (root cause found, NOT yet fixed — needs a spec decision first)

**PRIMARY root cause — `locale_loader.js:190-192` top-level REPLACES instead of deep-merges
`LABOR_RATES[TRADE]`, silently dropping any IFC class the active locale file doesn't enumerate:**

```js
if (localeData.labor_rates && typeof LABOR_RATES !== 'undefined') {
  for (var key in localeData.labor_rates) {
    if (localeData.labor_rates.hasOwnProperty(key)) LABOR_RATES[key] = localeData.labor_rates[key];
```

Every `viewer/locales/*.js` file ships its OWN hand-authored, **partial** `labor_rates` block (meant
to attribute a display source string like "RS Means 2024", not to be a complete productivity table).
E.g. `en_US.js`'s `STEEL_ERECTOR.productivity` is only `{IfcBeam:6, IfcColumn:5}` — `IfcPlate` and
`IfcMember` are simply absent; `CONCRETE_GANG.productivity` is only `{IfcSlab:30}` — `IfcFooting`,
`IfcPile`, `IfcReinforcingBar`, `IfcRamp`, `IfcRampFlight` are absent. Because the loader does
`LABOR_RATES[key] = localeData.labor_rates[key]` (whole-object replace, not a merge into the nested
`.productivity` map), loading ANY locale wipes out the canonical `rates.js`/`sequence_rules.json`
entries for every class the locale file didn't bother to list. `injectGantt()`'s `getInstallSecs()`
then finds no productivity match for those classes and falls back to the generic **120-second
(2-minute) per-element default** — vs. their real canonical durations (IfcFooting 4800s → 120s =
**40x faster**, IfcPile 7200s → 120s = **60x faster**, IfcPlate 2400s → 120s = 20x, IfcMember 2880s →
120s = 24x). `locale_loader.js` runs **unconditionally** on every `viewer.html` load (`<script
src="locale_loader.js?v=7">`, self-triggering IIFE on `DOMContentLoaded` — not opt-in).

**Live-browser proof** (headless Chrome, `navigator.language=en-US` → `en_US.js` locale auto-loads —
this is Puppeteer's/most real users' default, NOT an edge case):
```
DROP CHECK: STEEL_ERECTOR_productivity: {IfcBeam:6, IfcColumn:5}   ← IfcPlate/IfcMember GONE
            CONCRETE_GANG_productivity: {IfcSlab:30}                ← IfcFooting/IfcPile/... GONE
            installSecs_IfcPlate: 120 (was 2400 canonical)
            installSecs_IfcMember: 120 (was 2880 canonical)
            installSecs_IfcFooting: 120 (was 4800 canonical)
            installSecs_IfcPile: 120 (was 7200 canonical)
```
**Only 4 of 18 locales happen to match the canonical numbers exactly** (`en_MY`, `ms_MY`, `th_TH`,
`zh_CN` — presumably the developer's own test locale + neighbours) — every other locale (`en_US`,
`en_GB`, `de_DE`, `fr_FR`, `es_ES`, `ja_JP`, `ko_KR`, `pt_BR`, `ar_SA`, `en_AU`, `af_ZA`, `id_ID`,
`bn_BD`, `bl_BD`) silently drops classes and collapses them to the 2-minute fallback. This is a
**GENERAL, locale-triggered defect** — confirmed to affect every building via the same shared
`locale_loader.js` code path, matching the user's own "worse on others too" observation exactly, not
a Hospital-specific data quirk.

**SECONDARY, compounding factor (real, smaller effect, independent of the above) — `schedule_gate.js`
has no cap on concurrent crews per trade:** the resource-concurrency key `el.resource + '|' +
Math.floor(el.top_z/3)` gives every distinct 3m Z-slice its OWN independent, uncapped "crew" per
trade — a tall/many-band building effectively gets one full STEEL_ERECTOR/CONCRETE_GANG crew per
floor-slice running in true parallel, with no global limit on how many crews of one trade can exist
at once. Verified via a Node replica of `injectGantt()`+`ScheduleGate.computeSchedule()` against
REAL element data (not synthetic) for 4 buildings, using CORRECT (non-locale-corrupted) canonical
rates:

| Building | Superstructure elements | parallel resource×band "crews" | compression (serial ÷ actual) |
|---|---|---|---|
| Hospital | 11,947 | 21 | 1.2x (whole-project: 1183 naive days → 366 actual, **3.2x**) |
| HHS Office | 2,412 | 7 | 1.5x (whole-project: 80.8 → 69.5 days) |
| Terminal | 35,061 | 20 | 1.0x (buckets so element-dense the queues stay long regardless) |
| Duplex | 33 | 4 | 1.0x (too small to show the effect) |

This alone does not collapse a phase to "an hour" — it's real but modest (1.2–3.2x) — but it
compounds with the locale bug above, and confirms the concurrency model has no realistic total-crew
cap (no real site runs 20+ independent full crews of one trade simultaneously). Worth a separate fix
decision (e.g. a fixed project-wide crew-pool per trade instead of one per Z-band) — but the locale
bug above is the dominant, order-of-magnitude driver of "built within an hour."

**What's NOT the cause (ruled out this session, with evidence — don't re-check):**
- Not Item 0's `BatchedMesh` edit (see Item 0 above — A/B identical, ruled out first as instructed).
- Not a stale `§GANTT_CACHE_HIT` — Hospital's `tasks` table is empty (0 rows, confirmed via direct
  query), so `_cap` is always null and the path is always `§GANTT_SOURCE generated` — no captured-4D
  overlay masking anything.
- Not `rates/sequence_rules.json` itself — its `LABOR_RATES`/`SEQUENCE_RULES` values are byte-for-byte
  identical to the hardcoded `rates.js` fallback (both say IfcColumn:6, IfcBeam:8, IfcSlab:35, etc.).
- Not a `rates/<locale>.json` template mismatch (the `loadRateTemplate()`/`initRateTemplate()` JSON
  pipeline) — that function is **only called from `boq_charts.html`/`mep_report.html`**, never from
  `viewer.html`/`time_machine.js`. `locale_loader.js` (a completely separate code path) is the actual
  culprit, not the rate-template system the doc's original "where to start" hints pointed at.

**Fix NOT implemented this session (Spec-First discipline — needs a decision, not invention):**
the natural fix is changing `locale_loader.js:191-192`'s loop to merge into
`LABOR_RATES[key].productivity` (only overriding classes the locale file actually lists) instead of
replacing the whole trade object — mirrors the pattern already used correctly elsewhere. But whether
locale files should be allowed to override productivity (schedule-affecting) at all, vs. only material
cost/currency (display-affecting), is a product decision, not something to invent solo — flag for the
user before implementing.

### Original diagnosis brief (kept for the record — superseded by the findings above)
User, live-observing Hospital's Time Machine playback: the Superstructure phase appears to
complete in about an hour of simulated time — "built within an hour all of a sudden" — which reads
as wrong for a building this size (Hospital: 63,182 elements, 101×151×43m, irregular multi-wing
footprint per earlier session records in `prompts/PHOTOREAL_STILL_RENDER.md`). Not yet diagnosed —
this file is the handoff, not the fix.

### Where to start (grounded pointers, not a prescribed path)
- Duration generation lives in `SEQUENCE_RULES`/`LABOR_RATES`
  (`viewer/rates/sequence_rules.json`, phases confirmed this session: `Substructure`,
  `Superstructure`, `Architecture`, `MEP Rough-in`, `MEP Final`, `Finishes`) + `injectGantt()` in
  `viewer/time_machine.js` (auto-injects durations from IFC classes + these two rule sources per
  the file's own header comment). Read `injectGantt` fully before assuming where the bug is — do
  not guess at the formula from memory.
- Check the PER-ELEMENT productivity rate the Superstructure phase's classes resolve to in
  `LABOR_RATES` — a single misconfigured/missing rate (e.g. a rate keyed to the wrong unit, or a
  fallback default that's too fast) could make every Superstructure element install near-
  instantly, collapsing the whole phase into a tiny time window regardless of element count.
- Check for a **parallelism/resource-pool** explanation before assuming it's a rate bug: this
  project's own Time Machine doc header says "Parallel trades: multiple elements active
  simultaneously" and "Round-the-clock 24/7, no weekends" — if Superstructure's resource pool is
  effectively unbounded (every steel/concrete element gets its own crew with no contention), a
  large element count could legitimately compress into a short WALL-clock phase even with
  realistic per-element durations. Distinguish "duration-per-element is wrong" from "concurrency
  model has no real limit" — these need different fixes.
- Cross-check the SAME phase logic against at least one OTHER building (the project's own
  discipline throughout this session: never conclude from one building). If another building's
  Superstructure phase paces normally, the bug is likely Hospital-specific data (e.g. an unusual
  `cls`/element-count distribution triggering an edge case), not the general formula.
- §CACHE_HIT / `§GANTT_CACHE_HIT` — TM caches a generated schedule per building
  (`cachePut('gantt', ...)`, `time_machine.js`). If Hospital's cached schedule predates a rate fix
  made elsewhere in the codebase's history, a stale cache could be showing an old, already-fixed
  anomaly. Rule this out early — `cacheDel`/`tmRefoldSchedule` invalidate it — before chasing a
  live bug that may already be fixed and just not regenerated.

### "Examine how 4D logic is generated if affects all others" — scope of the broader check
User wants to know whether whatever's causing this is a **general defect** (would affect every
building's Superstructure phase, or every phase generated by the same code path) or **Hospital-
specific** (a data quirk in this one building's IFC extraction). Don't stop at "found a fix for
Hospital" — confirm which category it is, since a general defect changes the durations shown for
every building's 4D playback, not just this one.

### What NOT to assume
- Don't assume it's a rate-table typo without reading the actual resolved rate for Hospital's real
  Superstructure element classes (query, don't guess — this project's PRIME RULE: extract, don't
  invent).
- Don't assume it's Hospital-specific without checking at least one other building first.
- Don't treat the TM cache as ground truth without confirming it isn't stale.

## Witness / log tags already available
`§GANTT_CACHE_HIT ops=<n>`, `§GANTT injected=<n> dbElements=<n> sceneMeshGUIDs=<n>, bands=<n>,
<n> days`, `§GANTT_SOURCE generated|cached` — all already fire on TM activation per this session's
own captured logs; read them first before adding new ones.

## Item 2 witness scripts (2026-07-18 — scratchpad, may not survive to a new session; rebuild from
this description if gone, same caveat as Item 0's script above)
- `witness_superstructure_duration.js` — Node replica of `injectGantt()` + real `schedule_gate.js`
  against REAL element data (better-sqlite3 direct DB read) for Hospital/HHS/Terminal/Duplex; produces
  the per-building compression-ratio table above. Uses CANONICAL (non-locale-corrupted) rates since it
  loads `rates.js` directly — this is what exposed the SECONDARY (concurrency-cap) finding.
- `witness_hospital_tm_live.js` — headless-Chrome, drives the REAL `viewer.html` + real
  `locale_loader.js`/`time_machine.js`, calls `window.tmGenerateTimeline()` directly (bypasses the
  mesh-streaming gate that made the natural `?tm=1` activation path time out on Hospital's 63K
  elements under headless swiftshader), then queries `kernel_ops` + `window.LABOR_RATES` directly —
  this is what exposed the PRIMARY (locale_loader replace-not-merge) finding and its live proof.
Both at `/tmp/claude-1000/-home-red1-bim-compiler/60d9b00e-e721-4cf2-9be7-2ca7cccba830/scratchpad/`.
