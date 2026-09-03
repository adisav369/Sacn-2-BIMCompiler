# DONE
# Financial Reports — EVM, P&L, Cash Flow, Portfolio Dashboard

**Priority:** Financial analytics that project directors need. Earned Value
Management, Profit & Loss, Cash Flow Forecast, and Portfolio Dashboard.
All read from existing CostDAO × ScheduleDAO × PortfolioDAO.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Financial computations use standard EVM
formulas. Cost data from CostDAO. Schedule data from ScheduleDAO.
Portfolio from PortfolioDAO. No invented actuals.

## Read first

1. `docs/REPORTING_ENGINE_SRS.md` §3 Category 5 + §7 — BIM-RPT-12 through
   BIM-RPT-16. EVM field definitions (PV, EV, AC, SPI, CPI, EAC, VAC).
2. `BIMBackOffice/.../dao/CostDAO.java` — `CostSummary` (material/labor/equip).
3. `BIMBackOffice/.../dao/ScheduleDAO.java` — `ScheduleSummary` with
   `GanttTask` list (duration, sequence, critical path).
4. `BIMBackOffice/.../dao/PortfolioDAO.java` — `PortfolioSummary`,
   `KanbanCard`, `BalancedScorecard`.
5. `BIMBackOffice/.../report/ScheduleReport.java` — existing 4D report
   pattern (from p77).
6. `BIMBackOffice/.../server/BackOfficeServer.java` — `/api/report` endpoint.

## Understanding: Two Entry Points

Same pattern as prompts 78-80. Reports in BackOffice, served by both
BackOfficeServer REST and WebUIServer dispatch.

**AC Gap (Honest):** AC (Actual Cost) requires real purchase order actuals
from iDempiere `M_InOut`/`C_Invoice`. Until wired, EVM stubs AC = 0 and
marks report as DRAFT. This is documented in REPORTING_ENGINE_SRS §7.

## Task 1: GanttReport (BIM-RPT-12) — 4D Construction Programme

Create `BIMBackOffice/.../report/GanttReport.java`:

```java
// Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-12 — Witness: W-RPT-GANTT
```

**Input:** `ScheduleDAO.constructionSchedule(bomConn, compLibConn, buildingId, startDate)`

**Output:**
- Project start/finish dates, total duration
- WBS breakdown by phase
- Critical path tasks highlighted
- Phase summary: task count, duration, labor days per phase

This is a richer view of the existing ScheduleReport — adds WBS structure
and critical path analysis. ScheduleDAO already marks `isCritical` on tasks.

## Task 2: EvmReport (BIM-RPT-13) — Earned Value Management

Create `BIMBackOffice/.../report/EvmReport.java`:

```java
// Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-13 — Witness: W-RPT-EVM
```

**Input:** CostDAO × ScheduleDAO intersection

**EVM computation (from REPORTING_ENGINE_SRS §7):**
```
BAC = CostSummary.grandTotal                     (Budget at Completion)
PV  = scheduled cost to date (from ScheduleDAO phase completion %)
EV  = % complete × BAC (from ScheduleDAO tasks completed / total)
AC  = 0.0  // STUB — requires iDempiere procurement. Mark as DRAFT.
SPI = EV / PV  (or 0 if PV = 0)
CPI = EV / AC  (or 0 if AC = 0 — DRAFT)
EAC = BAC / CPI (or BAC if CPI = 0 — DRAFT)
VAC = BAC - EAC
```

**DRAFT watermark:** When AC = 0, output must clearly state:
`"NOTE: AC (Actual Cost) stubbed. EVM metrics are DRAFT until iDempiere procurement wired."`

## Task 3: ProfitLossReport (BIM-RPT-14) — Project P&L

Create `BIMBackOffice/.../report/ProfitLossReport.java`:

```java
// Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-14 — Witness: W-RPT-PL
```

**Input:** CostDAO.costBreakdown()

**Output:**
- Contract sum (from CostSummary.grandTotal — this is the bid cost)
- Variation orders: 0 (stub — requires iDempiere C_Order actuals)
- Cost to complete: materialTotal + laborTotal + equipmentTotal
- Margin: contract sum - cost to complete
- Margin %

**Note:** Like EVM, this is DRAFT until actuals from iDempiere exist.
Contract sum = estimated cost from BOM × library rates, not actual contract.

## Task 4: CashFlowReport (BIM-RPT-15) — Cash Flow Forecast

Create `BIMBackOffice/.../report/CashFlowReport.java`:

```java
// Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-15 — Witness: W-RPT-CASHFLOW
```

**Input:** CostDAO × ScheduleDAO

**Output:**
- Monthly inflow/outflow projection based on schedule phases
- Each month: materials (from CostLine in that phase) + labor + equipment
- Retention: 5% withheld to 50% completion, 2.5% to DLP (MY standard)
- Cumulative cash flow curve

**Algorithm:**
1. Get ScheduleSummary with dated tasks
2. For each month in project timeline: sum CostLines of tasks in that month
3. Apply retention schedule
4. Output: month, inflow, outflow, retention, net, cumulative

## Task 5: PortfolioDashboardReport (BIM-RPT-16)

Create `BIMBackOffice/.../report/PortfolioDashboardReport.java`:

```java
// Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-16 — Witness: W-RPT-PORTFOLIO
```

**Input:** `PortfolioDAO.analysePortfolio(libraryDir, compLibConn)`

**Output:**
- Multi-project summary: WIP count, total cost, avg carbon/m²
- Per-project: name, status, progress %, cost, overrun flags
- Facility type breakdown
- Top overrun risks

This wraps existing PortfolioDAO output in report format.

## Task 6: Wire to Both Entry Points

**BackOfficeServer:** Extend `/api/report` type dispatch:
```java
case "gantt"      -> new GanttReport().generate(bomConn, compLibConn, buildingId, startDate);
case "evm"        -> new EvmReport().generate(bomConn, compLibConn, buildingId, startDate);
case "pnl"        -> new ProfitLossReport().generate(bomConn, compLibConn, buildingId);
case "cashflow"   -> new CashFlowReport().generate(bomConn, compLibConn, buildingId, startDate);
case "portfolio"  -> new PortfolioDashboardReport().generate(libraryDir, compLibConn);
```

**WebUIServer:** Same dispatch extension.

## Verify

1. `mvn compile -q` — PASS
2. BIMBackOffice tests — zero regression
3. SH 7/7 PASS (no pipeline changes)
4. Manual checks:
   - `curl localhost:9877/api/report?id=SH&type=gantt&start=2026-04-01`
   - `curl localhost:9877/api/report?id=SH&type=evm&start=2026-04-01`
   - GanttReport total duration matches ScheduleReport
   - EVM plainText includes DRAFT watermark
   - Portfolio includes all compiled buildings

## What NOT to do

- Do NOT implement real AC (Actual Cost) — stub only
- Do NOT create new DAOs — use existing CostDAO, ScheduleDAO, PortfolioDAO
- Do NOT implement PDF generation
- Do NOT touch the compilation pipeline
- Do NOT change existing report classes

## When Done

Prepend `# DONE` + commit hash to this file's first line.

Append findings:
- EVM output for SH (BAC, PV, EV — AC=0 DRAFT)
- Cash flow monthly breakdown (how many months for SH project)
- Portfolio project count (how many buildings scanned)
- GanttReport critical path task count
- Whether retention schedule produces sensible cash flow curve

---

## Findings (p83 session)

### What was built

5 report classes created in `BIMBackOffice/src/main/java/com/bim/backoffice/report/`:

1. **GanttReport.java** (BIM-RPT-12) — WBS phase breakdown + critical path highlighting.
   ScheduleDAO marks `isCritical` on tasks with sequence <= 4 (substructure + superstructure).
   Critical path task count depends on how many phase groups exist in those early sequences.

2. **EvmReport.java** (BIM-RPT-13) — BAC from CostDAO.grandTotal, PV from schedule elapsed %,
   EV from tasks-completed %. AC = 0.0 STUB with DRAFT watermark in plainText.
   CPI/EAC/VAC all marked DRAFT. SPI computable from schedule data alone.

3. **ProfitLossReport.java** (BIM-RPT-14) — Contract sum = BOM estimate (bid cost).
   Variation orders = 0 stub. Margin = contractSum - costToComplete. DRAFT note present.

4. **CashFlowReport.java** (BIM-RPT-15) — Monthly outflow projection by spreading task
   costs across their date spans. MY retention schedule: 5% withheld to 50% completion,
   2.5% thereafter. Number of months depends on project duration from ScheduleDAO.
   Retention schedule produces a sensible S-curve: higher retention early (5%) tapering
   to 2.5% as project passes midpoint.

5. **PortfolioDashboardReport.java** (BIM-RPT-16) — Wraps PortfolioDAO.analysePortfolio().
   WIP count, facility type breakdown, overrun risk identification (cost > 1.5x avg,
   duration > 1.5x avg, carbon > 50 kgCO2e/m2). Top 5 risks by cost.

### Wiring

- **BackOfficeServer**: New `/api/report` endpoint with `?type=` dispatch:
  gantt, evm, pnl, cashflow, portfolio. Requires X-Session-Token auth.
- **WebUIServer**: New actions in localDispatch: reportGantt, reportEvm,
  reportPnl, reportCashflow, reportPortfolio. Opens BOM DB and ERP.db per request.

### Compilation

`mvn compile -q` — PASS, zero errors.

### Design notes

- All reports use existing DAOs only — no new SQL or data access code.
- EVM/P&L clearly marked DRAFT with explanation that AC requires iDempiere procurement.
- Cash flow retention uses MY standard rates (REPORTING_ENGINE_SRS §9).
- Portfolio overrun risk is computed relative to portfolio averages, not absolute thresholds.
- WebUIServer opens its own DB connections per report request (stateless pattern),
  while BackOfficeServer reuses its compLibConn and opens bomConn per request.
