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

## Test
- `node deploy/sandbox/test_all.js` — 169/169 must pass (§12 covers S210)
- Open 📊 from viewer, verify 9 charts render, click Save 5D BOQ
- Excel must have WP sheets with embedded chart images
- USD column must show next to RM in Overview

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
