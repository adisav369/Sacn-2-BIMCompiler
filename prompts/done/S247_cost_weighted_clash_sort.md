# S247 — Cost-Weighted Clash Sorting

# ⚠ DO NOT REMOVE
# Scope: Add cost-weighted sort to clash detection panel.
# Read the log after every run.

## Status: READY

## Goal
Sort clash list by cost weight so expensive clashes surface first. A clash between IfcColumn ($1,250) and IfcEnergyConversionDevice ($8,500) ranks above two IfcPipeSegments ($48 each).

## Spec: `docs/SITE_TAGGING_SRS.md` §5

## What Already Works
- Clash detection with R-tree spatial index (`measure.js`)
- Clash list sorted by overlap descending, Accepted last
- `rates.js` loaded on every page — `RATES` object keyed by IFC class, `RATES_DEFAULT` fallback
- `getRate(ifcClass)` helper returns rate value

## Implementation

### 1. Compute cost weight per clash pair
In `measure.js` where clash rows are built, add:
```javascript
var weightA = (RATES[c[2]] || RATES_DEFAULT).rate;
var weightB = (RATES[c[3]] || RATES_DEFAULT).rate;
var pairWeight = weightA + weightB;
```
Store `pairWeight` as an extra field on each clash row (index 9 or append).

### 2. Add sort toggle to clash panel header
Three buttons: **[Cost ↓] [Severity] [Storey]**
- Cost: sort by `pairWeight` descending
- Severity: sort by `overlap` descending (current default)
- Storey: group by storey, then overlap within each

### 3. Display cost in clash list rows
Show cost weight in the row — e.g. `RM 9,750` next to severity colour dot.
Use `rates.js` currency formatting if available.

### 4. Clash export — add cost column
In `_exportClashReport`, add Cost (RM) column to the HTML table using same weight calculation.

## ⚠ SAFETY — DO NOT BREAK EXISTING CLASH/SNAG WORKFLOW
- The clash detection, matrix, fly-to, snag share deep-link, review status (Reviewed/Resolved/Accepted), and HTML export are ALL WORKING IN PRODUCTION.
- Read `docs/CLASH_DETECTION.md` §3.1–§3.7 before touching anything.
- Cost sort is ADDITIVE — default sort remains severity (overlap descending). Cost is an additional toggle.
- Do NOT change the clash row data structure (indices 0–8). Append cost weight as index 9.
- Do NOT change `_exportClashReport` HTML structure — ADD a column, don't reorganise.
- Run existing clash flow end-to-end after changes: matrix → cell click → fly-to → review status → export → deep-link share. ALL must still work.

## Files to Edit
- `deploy/dev/measure.js` — sort logic, panel header, row display, export
- No other files needed — `rates.js` already loaded

## Witnesses
- `§CLASH_SORT_COST` — log when cost sort applied, show top pair weight
- `§CLASH_EXPORT_COST` — log cost column added to export

## Test
1. Load HospitalGarage (63K elements) or Terminal (48K)
2. Open clash matrix, click a cell
3. Click [Cost ↓] — verify expensive pairs float to top
4. Click export — verify Cost column in HTML report
5. Verify unlisted IFC classes get RATES_DEFAULT (500)
