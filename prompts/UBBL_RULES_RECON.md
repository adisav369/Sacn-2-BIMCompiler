# RECON TASK (not a build spec) — scope real UBBL/regulatory rule sources

```
# ⚠ DO NOT REMOVE
SCOPE: RECON ONLY. Do not write any gate/check code from this task — the output is a spec, not an
implementation. Confirmed 2026-07-05: zero real UBBL references anywhere in either repo (a prior grep hit was
a false positive on "bubble"). This is a genuinely unscoped, unbuilt area.
```

## WHY THIS IS RECON-FIRST, NOT BUILD-FIRST
Per project doctrine (Spec-First: no implementation without a written spec section) and per the "audit
landmines, calibrate research depth to scope" standing feedback — UBBL (Uniform Building By-Laws) is a real
regulatory domain (setback, plot ratio, egress width, corridor/stair minimums, fire-rated separations, etc.),
almost certainly larger in scope than the existing geometry-only gate (`clash`/`door-crush`/`clearance`/
`abuts-realign` in `sdg_gate.js`). Bolting it on as a fifth check without recon risks either (a) inventing rule
values that aren't real (violates PRIME RULE: extract, never invent) or (b) scoping it as "small" by accident
and getting blindsided mid-build.

## WHAT TO ACTUALLY DO
1. Find what building-class/regulatory data this project already has access to that's ADJACENT to UBBL —
   check `duplex_rules.db`, `terminal_rules.db`, and the residential-clearance mining work
   (`project_dx_mep_residential_standard`) for anything that already encodes statutory (not just measured)
   minimums, vs. purely empirical/mined clearances. Do not assume overlap — confirm or rule it out.
2. Identify a real, citable public source for UBBL rule text/values (the actual by-law document or an
   official summary) — earnest-effort sourcing appropriate to what this will initially be used for (flag to the
   user whether this is a real compliance-checking feature or a demo/mockup-level indicator, per the standing
   "calibrate research depth to scope" rule — do NOT default to a deep multi-hour research pass without
   confirming which one this is first).
3. Scope the rule SHAPE: what does one rule need to express to be checkable against extracted BIM geometry
   (e.g. "corridor width >= X" needs a corridor path/width measurement that may not exist yet as an extractable
   fact) — flag which UBBL rule categories are even MEASURABLE from what the compiler currently extracts, vs.
   which would need new extraction work first.
4. Write the actual spec (a follow-on prompts/ file) ONLY after 1-3 are answered — this task's job is answering
   them, not writing the check logic.

## DONE WHEN
1. A clear answer on whether any existing rules DB already encodes statutory (not measured) minimums.
2. A real, cited UBBL source identified, scoped to the confirmed depth (mockup-level vs. real-compliance-level
   — user's call, ask if unclear rather than assuming).
3. A short list of which UBBL categories are checkable today vs. blocked on missing extraction, with the
   blocking extraction gap named concretely per category.
4. A follow-on `prompts/UBBL_RULES_GATE.md` (or similarly named) build spec, written from what 1-3 established —
   not invented ahead of the recon.

## WATCHDOG NOTE
Tracked from `prompts/FRONTEND_LANE_MASTER.md §NEW BACKLOG`. This is a recon task — its "done" claims are about
what was FOUND, not what was built; the closing session still needs to cite sources/file:line for every claim,
not assert findings without a trail.
