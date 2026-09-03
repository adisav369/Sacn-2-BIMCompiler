# S210 — Snag button persists after walk mode exit

## Problem
Snag button stays visible after exiting walk mode.

**Sequence:** Walk mode → pick element → snag button appears → exit walk mode → snag button still showing.

## Root cause
`picking.js` line 153 shows snag button only when `walkModeActive` is true at pick time. But `walk.js stopWalkMode()` does not hide the snag button when walk mode ends. Nothing triggers `snag-btn-row.style.display = 'none'` on walk exit.

## Fix
In `walk.js stopWalkMode()`, hide `snag-btn-row`:
```js
const snagRow = document.getElementById('snag-btn-row');
if (snagRow) snagRow.style.display = 'none';
```

The MutationObserver in `sitecam.js` will pick up the style change and hide the fixed button too.

## Files
- `deploy/dev/walk.js` — `stopWalkMode()`, add snag-btn-row hide
- No changes to sitecam.js or picking.js needed

## Acceptance
- Enter walk → pick element → snag visible → exit walk → snag hidden
