# ⚠ DO NOT REMOVE
# Scope: S201 — BOQ Charts Browser Parity with Federation Excel
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: TODO

## Goal

Make `deploy/boq_charts.html` produce the SAME sheets and charts as the Federation
Python scripts (`boq/comprehensive_boq_export.py`, `schedule/excel_export.py`).

The browser chart page (S200) has basic charts. The Federation scripts produce
professional Excel with 6+ sheets, pie charts, bar charts, Gantt, cost breakdown
by Material/Labour/Equipment, CIDB 2024 rates, and summary dashboards.

## Reference Files

| File | Role |
|------|------|
| `deploy/boq_charts.html` | Current browser charts (S200 — basic) |
| `deploy/boq_export.py` | Federation comprehensive BOQ (copied from Bonsai) |
| `deploy/schedule_export.py` | Federation schedule export with Gantt (copied from Bonsai) |
| `deploy/schedule_generator.py` | Federation schedule generator (copied from Bonsai) |

## What the Federation Scripts Produce

### `comprehensive_boq_export.py` — 6 sheets:
1. **Executive Summary** — project overview, grand totals, key metrics
2. **Material BOQ** — per IFC class: qty, unit, rate, total (CIDB 2024)
3. **Labour BOQ** — per trade: crew size, productivity, days, cost
4. **Equipment BOQ** — crane, pump, scaffold allocation per IFC class
5. **Cost Summary** — Material + Labour + Equipment breakdown per discipline
6. **Charts** — embedded pie (cost split), bar (discipline breakdown), stacked (M/L/E)

### `schedule_export.py` — 4 sheets:
1. **Schedule** — full task list with WBS, dates, duration, resources
2. **Gantt Chart** — conditional-formatted timeline bars
3. **Resource Summary** — labour + equipment utilisation
4. **Statistics** — critical path, total float, phase durations

## What to Do

1. Read the Python scripts — extract every sheet structure, column layout, chart type
2. Replicate each sheet as a Chart.js chart + HTML table in `boq_charts.html`
3. The "Save Excel" button must produce an XLSX with the same 10 sheets
4. Charts on screen are interactive (hover, click); charts in Excel are static images
5. Use the same CIDB 2024 rate tables already in `boq_charts.html`
6. Keep it in the same new-tab architecture — viewer stays untouched

## Exit Criteria

1. `boq_charts.html` shows all charts from both Federation scripts
2. "Save Excel" produces XLSX with same sheet names and column layouts
3. RM totals match between browser and Python scripts for same building
4. Light/dark theme works on all charts
5. Terminal building as test case — compare browser vs Python output
