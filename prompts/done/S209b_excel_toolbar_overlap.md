# S209b — Excel Export Button Triggers 📊 4D/5D Instead

## Status: DONE

## Bug
On mobile, tapping "Export Excel" inside the Issues panel opens `boq_charts.html` (the 📊 4D/5D button) instead. The 3D model appears to "clear" because the page navigates to boq_charts.html in a new tab, and the original tab reloads boq_charts as a DB (recursive URL loop).

## Console Log Proof
```
§CACHE_HIT .../boq_charts.html?db=.../boq_charts.html?db=.../SampleHouse_extracted.db
```
The boq_charts URL nests recursively — proves 📊 is being triggered, not Export Excel.

## Root Cause
On mobile (@media max-width:600px), the Issues panel sits at `top:8px; left:8px; right:8px` (full width). The toolbar (#search-box) with the 📊 button sits at `top:48px; right:8px; width:48vw; z-index:12`.

When the Issues panel is short (few or no issues), the 📊 button is visible below it. The user taps where they think "Export Excel" is but hits 📊 instead. z-index 50 on the panel doesn't help because the toolbar is NOT behind the panel — it's below it.

## What Was Tried (all failed)
1. z-index 50 on issues panel — toolbar is below, not behind
2. Opaque background — same issue, toolbar not overlapped
3. pointer-events: auto — panel already has it
4. event.stopPropagation() on button — event starts on 📊, not propagating from Export

## Fix Options
1. **Hide toolbar when issues panel is open** — toggle #search-box display:none in `toggleIssues()`. Simple, clean.
2. **Make issues panel min-height cover the toolbar** — CSS min-height to push past the toolbar area.
3. **Move Export Excel button to a different position** — e.g. inside the issues list, not the toolbar area.

## Files
- `deploy/sandbox/index.html` — issues panel CSS + toolbar CSS
- `deploy/sandbox/issues.js` — `toggleIssues()` function (line ~164)
- `deploy/sandbox/tools.js` — `export4D5D()` opens boq_charts.html

## Also Fix
The `boq_charts.html` recursive URL: `tools.js` line 118 builds `base + boq_charts.html?db=dbParam` where `dbParam` is the full OCI URL from query string. When `boq_charts.html` loads and parses `?db=`, it gets the boq_charts URL as the DB path. Need to pass only the DB filename, not the full URL.

## Excel Export Code
`excel.js` — `exportIssuesExcel()` is synchronous (no async/await). Uses cached issues from `_cachedIssues` (loaded when panel opens). Calls `XLSX.writeFile()` directly. The function itself works — it's never being called because the wrong button gets the tap.

## Test
`node deploy/sandbox/test_all.js` — 93/93 pass. But the z-index test only checks panels are above z=20. Need to add: "when issues panel is open, toolbar buttons must not be clickable."

## WARNINGS — Do Not Break
- `excel.js` export function is NOW WORKING and synchronous. Do NOT make it async.
- Do NOT change IndexedDB version (currently v2). Changing it broke export before.
- Do NOT touch `walk.js` orientation code — S208 fix is stable.
- Do NOT change `XLSX.writeFile()` to blob/share/window.open — all caused page navigation.
- Run `node deploy/sandbox/test_all.js` before AND after any change. Must stay 93/93.
- Deploy to BOTH buckets (bim-ootb + bim-ootb-full/sandbox/).
- The user clears cache — do not blame caching.
