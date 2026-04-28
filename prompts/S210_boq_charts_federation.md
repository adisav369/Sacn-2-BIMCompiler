# S210 — BOQ Charts: Federation Structure + USD Conversion

## ⚠ DO NOT REMOVE
Scope: Enhance `deploy/boq_charts.html` with Work Package structure and USD conversion.
Read the log after every run. Do NOT modify the HTML page structure — it is proven and stable.

## Status: IN PROGRESS — dev deployed, production landing fixed

## Context
The 📊 4D/5D page (`boq_charts.html`) works — 9 charts render, Excel export saves correctly.
But the saved Excel files lack the full structure of the original Federation BOQ which has:
- Work Packages (WP) grouping — not just flat discipline/class/storey
- Embedded graphics (chart images in Excel sheets)
- Summary overview with totals

The HTML page itself is SUPER OK — DO NOT CHANGE the page layout, CSS, or chart rendering.
Only enhance the Excel export and add USD conversion to the Overview section.

## What to Add

### 1. USD Conversion Rate on Overview
- Add USD/RM conversion rate display next to RM totals on the Overview section
- Rate should be configurable (default: 1 USD = 4.45 RM as of 2026)
- Show both RM and USD columns in the summary table

### 2. Work Package Structure in Excel Export
The original Federation BOQ (see `scripts/nD_engine.py` and `docs/4D5DAnalysis.md`) organises by:
- Work Packages (WP-01 Structure, WP-02 Architecture, WP-03 MEP, etc.)
- Each WP has sub-sections by trade
- Each trade has line items with quantities, rates, totals
- Summary sheet with WP roll-ups

The current Excel export is flat — discipline → class → storey → qty. Need to restructure into
WP hierarchy matching the Federation pattern.

### 3. Embedded Charts in Excel
Use SheetJS image embedding to include the 9 chart images as PNGs in the Excel file.
Each chart → `canvas.toDataURL('image/png')` → SheetJS image insert.

## Reference
- Federation BOQ structure: `scripts/nD_engine.py` §WORK_PACKAGES
- 4D/5D analysis: `docs/4D5DAnalysis.md`
- Current boq_charts: `deploy/boq_charts.html` (DO NOT change HTML/CSS/chart rendering)
- SheetJS image docs: https://docs.sheetjs.com/docs/demos/net/embed

## Files to Change
- `deploy/boq_charts.html` — Excel export functions ONLY (not chart rendering or page layout)

## WARNINGS — Do Not Break
- DO NOT change the HTML page layout, CSS, or chart rendering — it is proven and stable
- DO NOT change the 9 chart definitions or logChart() calls
- DO NOT change the DB loading or SQL queries
- Run `node deploy/sandbox/test_all.js` — must be 149/149
- Deploy to BOTH buckets after changes
- The test suite verifies boq_charts.html exists and has Chart.js — do not break that

## DO — Testing & Logging

All test output to `deploy/dev/tests/log/`.

### Existing coverage
- **test_all.js §10h**: Downloads Duplex DB, verifies `elements_meta` has data for all 9 charts
- **test_all.js §12**: S210 deployment safety (landing, dev env, boq_charts)
- **Playwright 05-charts**: 6 tests — page load, canvases=9, no NaN/NUM!, WP listed, currency, no errors
- **Playwright 06-excel**: 4D/5D Excel download, chart button URL, z-index

### Gaps to fill in a dedicated session

| Test | Where | What | §-tag |
|------|-------|------|-------|
| Chart data renders (not "No data") | 05-charts | Wait for WASM, assert `#info` shows element count | `§PW_CHART_RENDER` — DONE (S227b) |
| WP structure in Excel | NEW test | Download 5D Excel, verify sheet names contain "PACKAGE" | `§PW_CHART_WP_SHEETS` |
| USD column present | 05-charts extend | After load, check visible text has "USD" or "$" | `§PW_CHART_USD` |
| Chart image in Excel | NEW test | Download 5D, verify file size > 50KB (images embedded) | `§PW_CHART_IMAGES` |
| 4D Excel download | 06-excel | Already tested but SKIP on headless — investigate | `§PW_EXCEL_5D` |

### test_all.js §12 (existing) — verify dev boq_charts
Already checks: ExcelJS CDN, USD_RATE, WORK_PACKAGES, PACKAGE 1, per-discipline sheets,
chart image embedding, save5D/save4D async, header fill per-cell. All PASS.

## DONE (this session)
- [x] bim-ootb-dev bucket created, CORS configured
- [x] landing.html fixed: rtree_browser_demo.html → sandbox/index.html (was broken 20hrs)
- [x] landing.html: ntfy.sh health check added (push alert on broken viewer)
- [x] landing2.html: dev landing, Watch Demo link, prod DB refs, DEV banner
- [x] deploy/dev/boq_charts.html: ExcelJS, WP PACKAGE 1-5, USD columns, chart images
- [x] deploy/dev/sitecam.js: toolbar hidden (!important) during camera
- [x] OCI_SETUP.md: dev bucket, oci_safe_delete() guard, stale refs cleaned
- [x] OCI_UPLOAD.md: dev section, promotion workflow
- [x] MOBILE_DEPLOY.md: §0.1 Dev Environment
- [x] test_all.js: 169/169 — §12 S210 deployment safety tests
- [x] 4 orphan root JS files deleted from bim-ootb-full

## DONE (S210b — this session)
- [x] Walk arrow (drive-thru-btn) hidden during camera — added to hide/restore list, bijection verified
- [x] WhatsApp share closes preview+camera — both paths now symmetric (Web Share + wa.me)
- [x] Text tool removed — replaced with 🎤 Voice (MediaRecorder, audio/webm, auto-stop on close)
- [x] Voice blobs attached to Web Share API files
- [x] Button swapped at init (data-tool=text → voice, label → 🎤 Voice)
- [x] Pie charts perfect circles — aspectRatio:1, legend font:13px
- [x] Excel pie embed 420×420 (square), Charts sheet isPie detection

## DONE (S210c — this session)
- [x] Walk arrow: A._driveBtn.remove() (object ref, not getElementById on dynamic element)
- [x] Walk arrow: re-create via startDriveThru() on camera close (guarded by _driveBtnWasActive)
- [x] Share: navigator.canShare(shareData) check before file share
- [x] Share: three-tier fallback: files → text-share → wa.me
- [x] Double save removed: _compositePhoto no longer writes to IndexedDB on snap
- [x] Single save point: _saveIssueToLog at share/download only
- [x] Status toggle: _renderIssueList() called after toggle (🔴→✅ instant update)
- [x] deploy/dev/issues.js: dev override created
- [x] Test harness: deploy/dev/s210_test.js — behavioral tests, 17/17 PASS
- [x] Deployed: sitecam.js + issues.js to bim-ootb-dev/sandbox/, content verified

## REMAINING
- [ ] 5D Executive Summary: charts placed right side but sizing needs user verification
- [ ] 4D Dashboard: charts placed right side, sizing needs verification
- [ ] Walk mode: view auto-rotates away from front door (device orientation — separate scope)
- [ ] Promote dev files to production after all verified

## S235 — Chart Visual Parity with Federation (boq_export.py)

### Problem
The browser pie charts (Chart.js) don't match the Federation original (openpyxl PieChart).
Three issues:

1. **Pie is not a full circle** — the pie sits inside a separate smaller circle within the
   chart canvas, leaving dead space. Chart.js defaults to padding around the pie.
2. **Labels too small and not black** — Federation uses `Font(size=16, bold=True)` for titles
   and `Font(size=14)` for data. Browser uses `#ddd` at 13-14px.
3. **White background on Excel export** — `prepareChartsForExcel()` adds white background
   styling that the Federation original never needed because openpyxl charts are native Excel
   objects with no background.

### OCI Live Bucket Cache Mystery (S235 investigation — unresolved)

**Problem:** `bim-ootb-live` does not serve updated files to the browser after upload,
even though `curl` confirms the new content and MD5 matches. `bim-ootb-dev` updates
instantly. Same region, same account, same bucket config (verified via `oci os bucket get`).
The old `bim-ootb-full` bucket had the same slowness (which is why it was abandoned).

**What was tried (all failed to make the browser see the new file):**
1. Hard refresh (Ctrl+Shift+R) — no effect
2. Delete object + re-upload — no effect
3. `--cache-control "no-cache, no-store, must-revalidate"` on upload — header appears in
   `curl -I` response but browser still shows old content
4. Uploading to both root `boq_charts.html` AND `sandbox/boq_charts.html` — both confirmed
   correct via curl, browser still shows old
5. Incognito window — not tested (user declined further debugging)

**What was confirmed:**
- `curl` and `diff` prove the server has the correct file at both paths
- Bucket configs are identical (versioning disabled, Standard tier, same compartment)
- No CDN, no `x-cache` header — both buckets show `x-api-id: native`
- Only difference: live created Apr 27, dev created Apr 22
- OCI docs say `Cache-Control` header "has no effect on Object Storage behavior" — it only
  passes through to the client. OCI doesn't cache internally based on it.

**Root cause hypothesis:** Browser-level heuristic caching. Without `Cache-Control`,
browsers cache based on `Last-Modified` using the formula `(now - last-modified) × 10%`.
If the original file was uploaded hours ago, the browser may cache it for hours.
But this doesn't explain why dev works — unless the user's browser has different history
for dev vs live URLs.

**Workaround:** Test chart changes on dev bucket, which updates instantly. Only promote
to live when ready, and expect delays. Consider adding `?v=N` cache buster to the
`tools.js` URL that opens `boq_charts.html`.

**Action for next session:** Add `?v=${Date.now()}` to the chartsUrl in `tools.js`
line 126/129 so boq_charts always loads fresh. This is the same pattern `index.html`
uses for JS modules (`?v=N`). Also add `--cache-control "no-cache"` to ALL upload
commands in `internal/OCI_SETUP.md`.

### S226 _TRL Refactor Damage — FIXED (S235)
The S226 localisation refactor replaced hardcoded chart titles with `_TRL.*` variables.
During that string substitution, it accidentally:
1. **Dropped `msCanvas.height = 250`** (chart 8) and **`ganttCanvas.height = 350`** (chart 9)
2. **Redesigned chart 8** from individual milestone bars (Start/End per phase) to a
   phase-span stacked Gantt — which duplicated chart 9's pattern and was never requested

Both heights and the original milestone design were restored from `deploy/sandbox/boq_charts.html`
(commit `84acafdb`, S225 promote-to-prod — the last known-good version).

**Lesson for future _TRL/refactor work:** When replacing hardcoded strings with variables,
do a line-by-line diff BEFORE and AFTER. Only string values should change — any structural
change (removed lines, redesigned data, changed chart type) is accidental and must be reverted.
The sandbox version is the regression baseline.

### Cache Warning for Pie Fix
The pie chart fix (legend position, padding, font sizes) will hit the same OCI live
cache problem. **Test on dev bucket only.** Do not waste time debugging live cache.
When the pie fix is verified on dev, promote and add `?v=${Date.now()}` to the URL
(see §OCI Live Bucket Cache Mystery above).

### Why S210b Failed
S210b claimed "Pie charts perfect circles — aspectRatio:1, legend font:13px" and marked it DONE.
This was wrong. `aspectRatio:1` makes the **canvas** square, but the **pie circle inside**
is still shrunk because `legend:{position:'right'}` steals ~40% of the canvas width.
The pie looked like a small circle floating in a big square. Do NOT repeat this approach.

### Root Cause (maths)
Chart.js `type:'pie'` has a default `layout.padding` and the legend takes space from the canvas.
The pie radius is computed as: `min(canvas.width, canvas.height) / 2 - padding - legend`.
With `legend:{position:'right'}`, the pie gets ~60% of the canvas width.

### Fix — Match Federation Exactly

**1. Pie fills the canvas** — remove padding, use `radius:'100%'` (or close to it):
```javascript
options: {
  aspectRatio: 1,
  layout: { padding: 0 },
  plugins: {
    legend: {
      position: 'bottom',        // not 'right' — stops legend stealing width
      labels: { color: '#000', font: { size: 14, weight: 'bold' } }
    }
  }
}
```
The Federation pie is `width=16, height=10` — landscape rectangle with legend below.
Chart.js with `position:'bottom'` gives the same layout: full-width circle above, legend below.

**2. Labels large and black** — match Federation `Font(size=16, bold=True)`:
```javascript
// Title
plugins: { title: { display: true, text: 'Cost Breakdown by Discipline',
                     color: '#000', font: { size: 18, weight: 'bold' } } }
// Data labels (use chartjs-plugin-datalabels or built-in tooltip)
```

**3. No white background** — the on-screen dark theme is fine. For Excel export,
Chart.js `canvas.toDataURL()` captures whatever background the canvas has.
Use `Chart.js plugin beforeDraw` to fill white ONLY during Excel capture:
```javascript
// In prepareChartsForExcel() — set a temporary plugin
ch.options.plugins.customCanvasBackgroundColor = { color: '#fff' };
// Plugin (register once):
Chart.register({
  id: 'customCanvasBackgroundColor',
  beforeDraw: (chart) => {
    const bg = chart.config.options.plugins.customCanvasBackgroundColor;
    if (bg && bg.color) {
      const ctx = chart.ctx;
      ctx.save();
      ctx.fillStyle = bg.color;
      ctx.fillRect(0, 0, chart.width, chart.height);
      ctx.restore();
    }
  }
});
```
This way: dark theme on screen, white background only in the exported PNG.

### Why Milestone + Gantt rows are too tall
S226 (_TRL localisation) accidentally dropped fixed canvas heights that S210 had:
```
// S210 original (compact):
msCanvas.height = 250;      // chart 8 — Milestone Timeline
ganttCanvas.height = 350;   // chart 9 — Strategic Gantt
```
S226 replaced hardcoded titles with `_TRL.t_gantt` etc. and the `height` lines were
lost as collateral. Without them, Chart.js auto-sizes and the bars accumulate downward,
making both charts far too tall.

**Fix:** Restore both `canvas.height` lines. They were proven in S210 original.
The S210 Milestone chart also used individual milestone bars (Start/End per phase),
not phase-span Gantt. The current phase-span redesign may be fine but the missing
`height=250` is why it's too tall.

**4. Use the log file to verify.** Every Excel export auto-downloads a `.log` file
(`_downloadLog('5D')` / `_downloadLog('4D')`). The log contains `§CHART_PREPARE`,
`§CHART_TEST`, `§TRL_VERIFY`, `§MATHS_VERIFY` lines with canvas sizes, data counts,
and pass/fail per chart. After any pie fix, export Excel and READ THE LOG — it will
show the actual canvas dimensions. If the pie is still small, the log will show
`chart=0 ... WxH` proving the canvas is wrong. No guessing — log is evidence.

**5. Just copy what works** — the Federation `boq_export.py` line 568-578 produces
a perfect chart because openpyxl delegates to Excel's native renderer. The browser
equivalent is to set Chart.js options to maximum simplicity:
- `layout.padding: 0`
- `plugins.legend.position: 'bottom'`
- `datasets[0].borderWidth: 1` (thin segment borders like Excel)
- Font sizes: 16+ bold for titles, 14 for labels
- No animation on export (`animation: false` during capture)

### 5D/4D button enlarges page (no restore on error)
`prepareChartsForExcel()` resizes all canvases to 800-1100px for image capture.
`restoreChartsAfterExcel()` shrinks them back. But there is no `try/finally` —
if anything throws between prepare and restore (450 lines of Excel generation),
the page stays permanently enlarged.

**Fix:** Wrap the body of `save5D()` and `save4D()` in `try { ... } finally { restoreChartsAfterExcel(saved); }`.

### S235 Fixes Applied (chart 8, 9, pie)
- Chart 8: restored to individual milestone bars (Start/End per phase) from sandbox.
  `msCanvas.height = 250`. S226 _TRL refactor had replaced with phase-span Gantt duplicate.
- Chart 9: `ganttCanvas.height = 350` restored. S226 dropped it.
- Pie 1 & 3: `position:'bottom'`, `layout:{padding:0}`, `borderWidth:1`.
  `position:'right'` was stealing 40% canvas width — pie was a small circle in a big square.
- Source: `deploy/sandbox/boq_charts.html` lines 703-742 (S225 promote-to-prod baseline).
- OCI dev bucket cache may delay visibility — use `?v=` cache buster to verify.
