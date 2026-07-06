# ⚠ DO NOT REMOVE — RESUME CARD (act-from-here): "Dashboard BI low-hanging fruit"
# PRIME RULE: EXTRACT/COMPILE ONLY. Every number a real fold of the open window's records. NON-INVENT, no LLM.
#   Whitebox §-log FIRST (read the log; exit code ≠ evidence). Money via KanbanLens.fmtMoney. iDempiere LAW: the
#   Dashboard is a ⋯-rail LENS, never native chrome; Lucide icons only. Edit in a /tmp/wt-* worktree off
#   origin/main (editing ~/bim-ootb is hook-blocked). Bump erp/sw.js CACHE_VERSION each deploy. Reply SIMPLE +
#   TERSE [[feedback_terse]] — plain words, no jargon.

## ANSWER TO "all use present tabs?" — YES, no new tab. All four live on the GRAPH tab (+ existing List/drill).

## WHERE WE ARE (shipped this lane, all LIVE on GH Pages, sw v750)
Dashboard "future tense" + BI shipped 2026-06-22:
- #488 FORECAST run-rate on time-axis (Graph S-curve + Timeline dashed future). W-DASH-FORECAST 5/5.
- #489 EVM VARIANCE S-curve on a Project window (planned vs committed + Δ; Hospital +35%, == DB Σ). W-DASH-VARIANCE 3/3.
- #490 PIVOT HEATMAP (cells tinted by share, darker=higher). W-PIVOT-HEATMAP 3/3.
- #491 SPIDER WEB (Graph "Web" toggle: pies → one concentration radar, spokes=live pies). W-DASH-WEB 4/4.
- ERPUserGuide "Forecast — reading the future" + 2 figs (dashboard_forecast_timeline.png, dashboard_web.png) published.

## ✅ ALL DONE / LIVE 2026-06-22 (PR #493, sw v751, W-DASH-BI 6/6; witness erp/tests/poc_dashboard_bi.js)
1. ✅ KPI STRIP — `_buildKpiStrip` count·total·avg·biggest·smallest at top of Graph tab; honest "—" when no amtCol.
2. ✅ UP/DOWN vs last month — `_periodDelta` ▲/▼ % on the headline measure (amount, else count); green up/red down.
3. ✅ TOP / BOTTOM N — `_rankDrill` Explore chips "Top 5 / Bottom 5 by <amt>" drill the LIST via `_drillByKeys`.
4. ✅ STACKED BAR — `_renderStackedBar` per-month status mix (date×status) SVG card beside the S-curve.
   Witness cross-checks: KPI total==S-curve cumLast; KPI rows==List DOM count; stack seg Σ==record count; Top
   max==KPI biggest, Bottom min==KPI smallest. Regression export 14/14 + forecast/web/variance all GREEN.
   (Garden demo has only 4 SO → Top/Bottom 5 honestly returns 4; cap=min(5,N).)
   NEXT BI candidates if resumed: KPI strip on other tabs (subtab-focus), or a real Top-N on a >5-row tenant.

## NEXT (ARCHIVED — all shipped above) — work top-to-bottom (cheapest first; each a real fold, each on the Graph tab)
1. **KPI STRIP** (do first — easiest, biggest gap). A row at the top of the Graph tab: count · total · average ·
   biggest · smallest of the open window's records. Pure folds (_amtSum + length + Math.min/max over _records;
   money via KanbanLens.fmtMoney). Honest "—" when there's no amount column. §-log the five numbers; witness
   asserts they == an independent compute over the same records. Seam: openDashboard show('graph') / _buildOverview
   top (gbox), reuse _amountColOf.
2. **UP/DOWN vs LAST PERIOD.** Beside the total on the KPI strip: ▲/▼ + % vs the previous month. Reuse the by-month
   fold already in _cumByMonth/_groupByMonth (idempiere.html). Green up / red down (status palette). NON-INVENT:
   last full month vs the one before. Skip honestly if <2 months. §-log {thisMo, lastMo, pct}.
3. **TOP / BOTTOM N.** An Explore chip "Top 5 / Bottom 5 by <amount>" → narrows into the LIST via the existing
   _drillByKeys / _lpDrill drill (no new surface). Just a sort of a fold you already build (_groupRecsBy or raw
   _records by amtCol). §-log the ranked keys.
4. **STACKED BAR** (a bit more work, high value). One cut split by another — e.g. orders by month, colored by
   status. The Pivot already cross-folds (pivot_lens build: 'rowV|colV'→cell). Draw it as a stacked bar card on the
   Graph tab; reuse _renderBars + KanbanLens.statusColor. Start with date×status. §-log segments per bar.

## DOCTRINE (keep)
- NON-INVENT: every number traces to a real record fold; no synthetic rows, no LLM, no prediction/ML (that fights
  the doctrine and is NOT low-hanging). Honest empty/"—" bottoms.
- No new tabs — these all enrich the GRAPH tab + reuse the List drill. Resting view stays clean.
- Witness = whitebox §-log (Playwright boots idempiere.html, reads §-lines + DOM). Pattern: erp/tests/
  poc_dashboard_forecast.js / poc_dashboard_web.js / poc_dashboard_variance.js (oracle a number via independent
  compute, e.g. sqlite3 CLI, and DIFF it). Run existing poc_dashboard_export.js (14/14) as regression each time.
- Ship flow: worktree off origin/main → edit → node syntax-check inline scripts → witness GREEN → bump sw →
  commit/push/PR → gh pr merge --auto --squash → verify live sw + code → remove worktree.

## POSITIONING (if asked "beating SAP?")
On the dashboard/edge-BI: yes — instant, offline, zero-install, every number traceable, reversible. Big ERPs still
win on scale + ML forecasting + deep planning; don't chase those. Our edge = immediacy + trust. [[feedback_no_hype]]

# Whitebox first [[feedback_whitebox_deduce_not_browser]]. Simple + terse replies [[feedback_terse]].
