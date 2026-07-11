<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# VIEWER FIND PANEL — PARTS AXIS LIVE VERIFICATION (2026-07-11, MANAGER-assigned)

```
# ⚠ DO NOT REMOVE
SCOPE: bim-ootb `viewer/navigate_find.js`'s "Parts" axis (Stairway/Lift Shaft/Plant Room, commit
`d04ddd5`, merged into `main` this session) was verified once already — by the worker who built it
(Playwright, real Duplex/SampleCastle data, §FILTER log lines cited in its commit message) and by
MANAGER independently reading the actual diff. That evidence stands and is NOT in question. What's
missing is a THIRD, cheap thing: a real, current-session, driven-from-the-actual-served-page
screenshot/log for the record — MANAGER tried this live in-session, failed 3 times to find the
right way to programmatically open the Find panel (`window.A.openFindPanel` doesn't exist under
that name; `_focusPanel('find')` didn't render a visible panel either), and correctly stopped
rather than keep trial-and-erroring — see `prompts/MANAGER.md`'s hardened conduct rule, 2026-07-11.
This file exists so a dispatched session picks this up cleanly instead of repeating the same 3
failed guesses. Read the log after every run. PUSH PAUSE IN EFFECT (CLAUDE.md §⏸) — commit locally
only if any real fix is needed, verify on localhost, do NOT push, do NOT open a PR.
```

## What's actually needed (small, bounded — do not scope-creep into a rebuild)
1. **Find the REAL way to open the Find panel from a fresh page load**, for both a human clicking
   through the UI and a Playwright/puppeteer script driving it headlessly. `viewer/panels.js` line
   ~1177 has `{ id: 'find', ..., fn: function() { if (A.openFindPanel) A.openFindPanel(''); } }` —
   `A` there is a closure-local variable, NOT `window.A` (confirmed this session — `window.A` is
   `undefined` on this page). Find what `A` actually resolves to in that closure (likely the scene/
   app module passed into panels.js's own IIFE/init call) and the correct global (if any) to call
   from outside, OR just drive it the way a real user would: find the actual pill/icon element in
   the DOM (search icon, per `icon: I.search.svg`) and `.click()` it in Playwright, same as a human.
2. Once open, cycle the axis toggle (`#find-axis-toggle`, `.click()` cycles storey→disc→room→
   material→phase→parts) to `parts`, confirm the tree lists Stairway/Lift Shaft/Plant Room with
   real counts on Duplex and/or SampleCastle (`http://localhost:PORT/viewer/viewer.html?db=
   buildings/{Name}_extracted.db&ghost=1` — direct building URL, NEVER the Hub picker, see memory
   `feedback_localhost_full_building_url_testing.md`), and capture ONE real screenshot + the exact
   `§LENS_PROBE`/`§LENS_GROUPS`/`§FILTER` console log lines as evidence, same standard as the
   Modeller-side screenshot already captured this session (`prompts/BUILDING_PARTS_TAXONOMY.md`).
3. That's it — this is a verification/evidence task, not new feature work. If the panel-open
   mechanism turns out to be genuinely broken (not just hard to drive headlessly), report that as a
   real finding and stop — don't silently fix a bug that isn't actually in scope here without
   flagging it first.

## Context already established, don't re-derive
- The Find panel itself has a KNOWN historical visibility bug (position:fixed + display:none
  restored in `deploy/dev/navigate_find.js` this session, scoreboard item 1, bim-ootb PR #728) —
  the panel is deliberately hidden until opened; a blank/invisible panel on load is EXPECTED
  behavior, not a bug, until something explicitly opens it.
- The underlying data/query logic (STAIR_LIKE/LIFT_KEYWORDS/PLANT_KEYWORDS) is shared verbatim with
  the Modeller's `building_parts_outliner.js` (`f10c5295`) and already witnessed 13/13 PASS in
  bim-compiler (`build/building_parts_taxonomy.js`) — do not re-verify the QUERY logic, only the
  UI wiring/panel-open mechanism on the Viewer side.

## DONE WHEN
One real screenshot + real console log lines proving the Parts axis renders and isolates correctly
from a fresh page load (not a synthetic DOM check), OR a clearly-reported genuine blocker if the
panel-open path turns out to be broken — either way, update this file with a dated RESULT section
so the next session doesn't re-attempt the same failed guesses.
