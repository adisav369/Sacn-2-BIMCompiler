# ⚠ DO NOT REMOVE — Read the log after every run.

# Fix: Pointed Element Appearing Off (dev only, live is OK)

## Symptom
On the dev site, mouse hover and click pick sense elements **offset** from where the cursor actually points. Live site is correct. Affects ALL buildings — not DB-specific.

## Root Cause
Dev `index.html` has `<div id="dev-banner">` at the top pushing the canvas down ~30px. The mouse-to-NDC conversion uses `window.innerWidth/innerHeight` which assumes canvas fills the full window. The banner breaks that assumption — the ray is cast offset from where the user points.

Live has no banner, so coordinates match.

## Prior Fix History (different bug — bbox centre vs hit point)
- `6827c2f7` — highlight at hit.point not bbox centre
- `98147c74` — localToWorld positioning
- `642f5b7e` — S250 regressed (reverted to geometry-local)
- `02374b31` — fixed again with per-element DB query

Those fixes are still intact. The current bug is the **banner offset**, not highlight placement.

## Locations to Fix
Search: `e.clientX / window.innerWidth` and `e.clientY / window.innerHeight`

1. `picking.js` ~line 183 — click pick
2. `tools.js` ~line 417 — hover highlight
3. `measure.js` — check for same pattern

Fix pattern — use canvas rect instead of window:
```js
const rect = A.canvas.getBoundingClientRect();
A.mouse.x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
A.mouse.y = -((e.clientY - rect.top) / rect.height) * 2 + 1;
```

## Verify
1. Dev site with banner: pick lands exactly on pointed element
2. Live site without banner: still works (rect = window when no banner)
3. Console `§BBOX_DEBUG` — delta near zero on both sites

## Do NOT
- Remove or change the dev banner
- Touch highlight positioning code (localToWorld, ifc2three — those are correct)
- Change databases or deployment
- Drift into other issues
