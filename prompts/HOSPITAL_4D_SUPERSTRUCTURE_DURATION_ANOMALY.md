# ⚠ DO NOT REMOVE — Hospital 4D Superstructure duration anomaly + share-button correction
# SCOPE: two items for a fresh session to pick up. Read the log after every run. Read this whole
# file before touching code — item 1 is a quick correction, item 2 is a real investigation with
# no assumed root cause yet.

## Item 1 — correction: drop `#tm-share`, do not restore it
`bim-ootb` PR #850 (2026-07-18, `revert/tm-movie-export-and-gi-autoengage`) reverted the Movie
Export feature (see `prompts/archive/TM_MOVIE_EXPORT_RETIRED_2026-07-18.md` for the full story)
and, as part of that revert, restored the `#tm-share` button (`time_machine.js`, "Copy shareable
link") to its pre-Export-Movie state. **User correction: this was not requested — the share button
should be dropped, not restored.** The revert only needed to remove the Export Movie UI/mechanism;
bringing `#tm-share` back was this assistant's own unrequested addition, done without checking.
Next session: remove `#tm-share` (button markup ~`time_machine.js:1933`, listener
~`time_machine.js:2081-2089`) — do not replace it with anything unless told to; just drop it from
the panel row.

## Item 0 — VERIFY THIS FIRST: element-appearance regression from THIS session's own edits
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

## Item 2 — Hospital: why does Superstructure complete "within an hour"?
(Only proceed here after Item 0 is resolved — this may turn out to be the same bug.)
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
