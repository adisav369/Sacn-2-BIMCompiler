# S249 — Building-Wide Issue Heatmap

# ⚠ DO NOT REMOVE
# Scope: Issue density heatmap overlay + matrix breakdown by storey × issue type.
# Read the log after every run.

## Status: READY — depends on S248 (QR tagging system)

## Goal
Red sphere in issues panel → click → building-wide colour overlay showing issue density per element/storey. Matrix panel breaks down by storey × issue type. Same UX pattern as clash matrix.

## Spec: `docs/SITE_TAGGING_SRS.md` §4

## ⚠ SAFETY — DO NOT BREAK EXISTING WORKFLOW
- Clash matrix, detection, fly-to, snag share, review status, HTML export are ALL WORKING IN PRODUCTION.
- This is a NEW module (`heatmap.js`) — do NOT modify `measure.js`.
- Heatmap overlay must be togglable — activate/deactivate cleanly without corrupting element materials.
- Store original materials before applying heatmap, restore on deactivate.

## Prerequisites
- S248 complete (issue queue with localStorage, issue types, cost weight)

## What Already Works
- Clash matrix UX pattern (`measure.js`) — spheres, grid, cell click, fly-to
- Issue queue in localStorage (S248)
- `rates.js` cost weights
- Element material access via Three.js mesh references

## Implementation

### 1. Heatmap Overlay
- Count issues per element GUID from localStorage
- Colour elements by density: 0=original, 1-2=light yellow, 3-5=orange, 6+=deep red
- Store original material references, swap on activate, restore on deactivate
- Elements with no issues remain unchanged

### 2. Matrix Panel (storey × issue type)
- Rows: storeys (from elements_meta)
- Columns: issue types (Defect, Non-Compliance, Safety, Incomplete, Clearance)
- Cell: count + colour gradient
- Click cell → filter issues list to that storey + type → fly-to first

### 3. Red Sphere Trigger
- Issues panel toolbar gets red sphere icon (same style as clash sphere)
- Click → heatmap activates + matrix opens
- Click again → deactivate, restore materials

### 4. Cost-Weighted Sort in Matrix
- Each cell sortable by cost weight (from `rates.js` via tagged element's ifc_class)
- Matrix header shows total cost exposure per storey

## Files
- `deploy/dev/heatmap.js` — NEW: overlay, matrix, sphere trigger
- `deploy/dev/index.html` — add `<script src="heatmap.js">`
- `deploy/dev/tagging.js` — expose issue query API for heatmap to consume

## Witnesses
- `§HEATMAP_ON` — activated, N issues across M storeys
- `§HEATMAP_OFF` — deactivated, materials restored
- `§HEATMAP_MATRIX` — matrix shown, storey × type counts
- `§HEATMAP_CELL` — cell clicked, storey + type, N issues

## Test
1. Create several issues across multiple storeys and types (S248)
2. Click red sphere → heatmap overlay visible, hot zones coloured
3. Matrix shows correct counts per cell
4. Click cell → issues filtered → fly-to first
5. Click sphere again → overlay off, original materials restored
6. Clash matrix still works independently (no interference)
