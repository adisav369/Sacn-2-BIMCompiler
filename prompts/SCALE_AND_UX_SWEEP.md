# SESSION PROMPT — Scale-verification + UX-honesty sweep on everything shipped 2026-07-05

```
# ⚠ DO NOT REMOVE
SCOPE: this bundles the watchdog's own concerns from a review pass over PRs #656/#658/#660/#661/#662/#664 and
the parametric recon — it is deliberately ONE session, ONE pass, not a fan-out (nothing here needs parallel
agents; several items depend on findings from earlier ones in the same list). Read the log after every run.
This is a VERIFICATION + SMALL-FIX session, not a big-build session — if any item turns out to need a large
build, STOP, write a spec, don't just build it inline.
```

## WHY THIS SESSION EXISTS
Every witness for today's shipped work (#656 grid green/orange, #658 Save/auto-heal, #661 Teams presence) ran
against small fixtures (Duplex, SampleHouse, synthetic pairs) — none were checked at Terminal's ~48k-element
scale. This project has been burned by exactly this gap three times before (Outliner O(rows×ops) stall, LOD400
load stall, Terminal walk-all-disciplines flash — all passed small-fixture witnesses first, all broke only at
Terminal scale). Treat this as the load-bearing reason this session exists, not a formality.

## §1 — SCALE CHECK (do this first, it's cheap and highest blast-radius if skipped)
1. **Grid green/orange preview (#656):** open Terminal in the Modeller, drag a gridline, measure frame time
   during the live `gmTint`/classification recompute. Does it stay responsive, or does per-element
   governed/interior reclassification on every drag-frame degrade at 48k elements? If slow, is there an
   existing threshold pattern to reuse (`DW_ALL_PROXY_THRESHOLD`, `SHADOW_MAX_ELEMENTS`, `viewer/dlod.js`
   `MIN_ELEMENTS`) rather than inventing a new one?
2. **Save/auto-heal (#658):** construct (or find) a Terminal-scale scenario with several real ORANGE findings,
   run Save, measure the auto-heal loop's wall-clock (per-finding re-verify + the final full re-evaluate). Does
   it stay within a reasonable UI-blocking budget, or does it need the same async/batched treatment
   `walkall_terminal_scale`'s fix (flash 39.3s→1.4s, chain group-commit) already proved works for this class
   of problem?
3. **Teams presence (#661):** the re-render-on-every-heartbeat pattern — bounded by peer count, not element
   count, so likely fine, but confirm: does `renderPresence()` do anything O(peers × elements) (e.g. per-row
   Outliner dots) that could still degrade on a large building with many peers?
4. Log actual numbers (`§SCALE_CHECK feature=... elements=... ms=...`), not "felt fine." If anything crosses an
   existing threshold's ballpark, name it — don't silently patch without recording what was slow and why.

## §2 — TEAMS PRESENCE REAL-USEFULNESS DECISION — ✅ RESOLVED, see `prompts/FRONTEND_LANE_MASTER.md`
The decision (and its reasoning) is recorded in `FRONTEND_LANE_MASTER.md`'s entry for this file — that's the
one place it's written, don't duplicate it here again. Short version: option (a), ship honestly-labeled as-is,
no server-relay build now. Go read the master entry before proceeding, then continue.

## §3 — SMALL UX FIXES (each independent, do in one pass, log evidence for each)
1. **Green/orange toggle discoverability:** it's a genuinely novel interaction with zero onboarding today. Add
   at minimum a first-use hint or a line in the in-app guide (the SAME guide file already touched in #661 —
   check it doesn't re-introduce the Teams/HBA confusion while editing nearby text).
2. **Save-blocked UX:** when Save returns an Error (residual RED), does the UI point the user AT the offending
   element (select/highlight/fly-to), or does it just say "blocked" with no next action? If the latter, this is
   a real gap — a blocked Save with no way to find what's blocking it is a dead end for a real user. Fix if
   confirmed missing; if it already points at the element, say so and cite the code path, don't just assume.
3. **UBBL demo indicator surfacing — ✅ RESOLVED, see `prompts/FRONTEND_LANE_MASTER.md`** (this file's own
   entry). Don't duplicate the reasoning here — short version: reuse the existing gate's toast+`_emis`
   highlight, bake the disclaimer into the toast text, applies once `UBBL_RULES_GATE.md` is actually built
   (wasn't yet as of last check).
4. **HBA mobile stack CCTV rAF bug** (noted, not fixed, in #662): `hba_iot.js`'s scanline animation is dead
   (`if(!_pane)return` runs before `_pane=pane`). Small, isolated, fix it — one-line-shaped per the original
   report.
5. **Terminal double-label landmine** (from the parametric recon): `ad_element_placement.building_type` has
   `SJTII_Terminal` (81% material-populated, real `ad_building` row) vs. orphaned `TERMINAL` (1.6% populated, no
   matching row) for the SAME physical building. Reconcile or at minimum document which is canonical and why
   the orphan exists — don't leave two labels for one building silently coexisting.
6. **Duplicate pill registration audit (RENEWED 2026-07-05, do this before §5 of PILL_DRAWER_REORGANIZATION.md
   builds on top of it):** `viewer/panels.js:768` registers `shadow` directly via `R.register({id:'shadow',...})`
   — a SECOND, independent registration of the same pill id already covered by the canonical `_actions` array at
   `panels.js:1197`. Two competing wire-ups for one icon is a real, concrete candidate for the "pill icons
   sometimes misbehave" complaint (user-observed, not hypothetical) — likely a stray pre-consolidation
   registration that PR #635 ("ONE canonical common/pill_builder.js — retire the silent erp/viewer fork") missed.
   **Audit EVERY pill id for the same double-registration shape**, not just shadow — grep every direct
   `R.register(` call against the `_actions` array's `id:` list, flag every collision, and remove the stale one
   (keep whichever is the canonical `_actions`-array path, matching #635's own precedent). Fix before any drawer
   work touches these icons, or the bug just moves into the new structure.

## §4 — Q1/Q4 SMALL AGGREGATION FIXES (both have a proven pattern already in the codebase — copy it, don't invent)
1. **Q1 (LOD touch-up axes):** add the missing `GROUP BY type_id` aggregation over
   `component_definitions.local_min/max` and persist per-class min/max — the raw rows already exist
   (129 `IfcDoor` rows spanning 0.147–1.86m local width), only the aggregate is missing.
2. **Q4 (dining-set grouping):** extend `placeAssembly` (`modeller.html:2690`) to use the already-proven
   `commitSeedGroup` pattern (`modeller.html:3403`, currently only used by `_commitDiscWalk`) so a multi-leaf
   furniture-set drop commits as one linked group instead of N unlinked `GEOM_INSERT` rows.
Both are small, both unblock real items in `PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md` (§1, §3) — but this session
should STOP at "the fix landed and is witnessed," not go on to build the LOD touch-up editor or the escalating
lasso themselves — those are separate, bigger, not-yet-specced items.

## DONE WHEN
1. §1's 3 scale checks all have real `§SCALE_CHECK` numbers logged, at Terminal scale, not assumed fine.
2. §2 is DECIDED (option a, above) — implement the honest guide-text documentation of the limitation; do NOT
   build a server-relay in this session, and do NOT wire ERP presence on the assumption cross-device presence
   works. This item is no longer blocking — resume/continue if this session paused on it.
3. §3's 6 items (now incl. the duplicate-pill-registration audit) each resolved or explicitly named as needing
   a separate bigger session, with evidence either way. Item 6 specifically gates
   `prompts/PILL_DRAWER_REORGANIZATION.md` — that session should not start building drawers on icons still
   carrying an unresolved double-registration.
4. §4's 2 small fixes land with their own witnesses, and this session does NOT scope-creep into the bigger
   LOD-touch-up-editor or escalating-lasso builds those fixes unblock.

## WATCHDOG NOTE
Tracked from `prompts/FRONTEND_LANE_MASTER.md §NEW BACKLOG`. § log line per claim, no exceptions — this session
exists because prior claims (the #657 "already fixed" entry) turned out false when checked; hold this one to
the same bar it was created to enforce on others.

## ✅ DONE 2026-07-05 — closed out, see `FRONTEND_LANE_MASTER.md`'s own entry for the full summary + § evidence
All 4 DONE-WHEN conditions met: §1 measured (2 of 3 real degradations found, not fixed — see
`prompts/SCALE_CHECK_TERMINAL_FINDINGS_2026-07-05.md`, spun off as 3 new not-yet-started backlog items: grid-tint
perf restructure, save/auto-heal escalation design call, STR-rewalk id-collision race); §2 implemented (option a,
guide text); §3's 6 items all resolved with evidence (5 fixed/decided, 1 confirmed out-of-scope-here); §4's 2
aggregation fixes landed with witnesses, no scope-creep into the bigger editor/lasso builds. bim-ootb work on
`lane/watchdog-scale-ux-sweep` (pushed, not merged), bim-compiler Q1 migration on `lane/q1-component-dimension-range`
(pushed, not merged — pending reconciliation with an unrelated pre-existing edit to the same db file).
