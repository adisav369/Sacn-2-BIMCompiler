package com.bim.backoffice.report;

import com.bim.backoffice.dao.CostDAO;
import com.bim.backoffice.dao.ScheduleDAO;
import com.bim.backoffice.dao.ScheduleDAO.GanttTask;
import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BIM-RPT-15: Cash Flow Forecast — monthly inflow/outflow with retention.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Get ScheduleSummary with dated tasks</li>
 *   <li>For each month in project timeline: sum cost of tasks in that month</li>
 *   <li>Apply MY retention schedule: 5% withheld to 50% completion, 2.5% to DLP</li>
 *   <li>Output: month, inflow, outflow, retention, net, cumulative</li>
 * </ol>
 *
 * // Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-15 — Witness: W-RPT-CASHFLOW
 */
public class CashFlowReport {

    private static final String TAG = "CashFlowReport";

    /** Retention rate before 50% completion (MY standard). */
    private static final double RETENTION_RATE_EARLY = 0.05;
    /** Retention rate after 50% completion until DLP (MY standard). */
    private static final double RETENTION_RATE_LATE = 0.025;

    public record CashFlowMonth(String month, double outflow, double retention,
                                double netOutflow, double cumulativeOutflow,
                                double cumulativeCost, double completionPct) {}

    public record CashFlowOutput(String buildingId, String projectStart, String projectFinish,
                                 int totalMonths, double totalCost,
                                 double totalRetention, double peakOutflow,
                                 List<CashFlowMonth> months,
                                 String plainText) {}

    /**
     * Generate cash flow forecast from CostDAO x ScheduleDAO.
     */
    public CashFlowOutput generate(Connection bomConn, Connection compLibConn,
                                   String buildingId, String startDate) throws SQLException {
        // Get cost breakdown for total and per-task cost allocation
        CostDAO costDao = new CostDAO();
        CostDAO.CostSummary cost = costDao.costBreakdown(bomConn, compLibConn, buildingId);
        double totalProjectCost = cost.grandTotal();

        // Get schedule for task dates
        ScheduleDAO schedDao = new ScheduleDAO();
        ScheduleDAO.ScheduleSummary sched = schedDao.constructionSchedule(
                bomConn, compLibConn, buildingId, startDate);

        if (sched.tasks().isEmpty() || totalProjectCost <= 0) {
            return new CashFlowOutput(buildingId, startDate, startDate, 0, 0, 0, 0,
                    List.of(), "No tasks or zero cost — no cash flow to project.");
        }

        // Allocate cost proportionally across tasks by duration
        int totalTaskDays = sched.tasks().stream().mapToInt(GanttTask::durationDays).sum();

        // Build monthly cost map: month -> outflow
        Map<YearMonth, Double> monthlyCost = new LinkedHashMap<>();
        LocalDate projStart = LocalDate.parse(sched.projectStartDate());
        LocalDate projFinish = LocalDate.parse(sched.projectFinishDate());

        for (GanttTask task : sched.tasks()) {
            // Cost proportional to duration
            double taskCost = totalTaskDays > 0
                    ? totalProjectCost * ((double) task.durationDays() / totalTaskDays) : 0;

            // Spread task cost across months it spans
            LocalDate taskStart = LocalDate.parse(task.startDate());
            LocalDate taskFinish = LocalDate.parse(task.finishDate());
            YearMonth startMonth = YearMonth.from(taskStart);
            YearMonth endMonth = YearMonth.from(taskFinish);

            if (startMonth.equals(endMonth)) {
                monthlyCost.merge(startMonth, taskCost, Double::sum);
            } else {
                // Split proportionally across months
                long totalSpanDays = taskFinish.toEpochDay() - taskStart.toEpochDay();
                if (totalSpanDays <= 0) totalSpanDays = 1;

                YearMonth current = startMonth;
                while (!current.isAfter(endMonth)) {
                    LocalDate monthStart = current.equals(startMonth)
                            ? taskStart : current.atDay(1);
                    LocalDate monthEnd = current.equals(endMonth)
                            ? taskFinish : current.atEndOfMonth();
                    long daysInMonth = monthEnd.toEpochDay() - monthStart.toEpochDay() + 1;
                    double monthShare = taskCost * ((double) daysInMonth / totalSpanDays);
                    monthlyCost.merge(current, monthShare, Double::sum);
                    current = current.plusMonths(1);
                }
            }
        }

        // Build cash flow table with retention
        List<CashFlowMonth> cashFlowMonths = new ArrayList<>();
        double cumulativeOutflow = 0;
        double cumulativeCost = 0;
        double totalRetention = 0;
        double peakOutflow = 0;

        for (var entry : monthlyCost.entrySet()) {
            double outflow = entry.getValue();
            cumulativeCost += outflow;
            double completionPct = totalProjectCost > 0
                    ? cumulativeCost / totalProjectCost : 0;

            // MY retention schedule: 5% to 50% completion, 2.5% thereafter
            double retentionRate = completionPct < 0.5
                    ? RETENTION_RATE_EARLY : RETENTION_RATE_LATE;
            double retention = outflow * retentionRate;
            totalRetention += retention;

            double netOutflow = outflow - retention;
            cumulativeOutflow += netOutflow;
            if (netOutflow > peakOutflow) peakOutflow = netOutflow;

            cashFlowMonths.add(new CashFlowMonth(
                    entry.getKey().toString(), outflow, retention,
                    netOutflow, cumulativeOutflow, cumulativeCost, completionPct));
        }

        // Build plain text
        StringBuilder sb = new StringBuilder();
        sb.append("CASH FLOW FORECAST — ").append(buildingId).append("\n");
        sb.append("═".repeat(80)).append("\n\n");
        sb.append(String.format("Project:        %s → %s%n", sched.projectStartDate(), sched.projectFinishDate()));
        sb.append(String.format("Total Cost:     RM %,.2f%n", totalProjectCost));
        sb.append(String.format("Total Months:   %d%n", cashFlowMonths.size()));
        sb.append(String.format("Total Retention: RM %,.2f%n", totalRetention));
        sb.append(String.format("Peak Monthly:   RM %,.2f%n%n", peakOutflow));

        sb.append(String.format("%-10s %15s %12s %15s %15s %8s%n",
                "Month", "Outflow", "Retention", "Net Outflow", "Cumulative", "Compl%"));
        sb.append("─".repeat(80)).append("\n");
        for (CashFlowMonth m : cashFlowMonths) {
            sb.append(String.format("%-10s %,15.2f %,12.2f %,15.2f %,15.2f %7.1f%%%n",
                    m.month(), m.outflow(), m.retention(),
                    m.netOutflow(), m.cumulativeOutflow(),
                    m.completionPct() * 100));
        }
        sb.append("─".repeat(80)).append("\n");
        sb.append(String.format("%-10s %,15.2f %,12.2f %,15.2f%n",
                "TOTAL", totalProjectCost, totalRetention,
                totalProjectCost - totalRetention));

        BIMLogger.info(TAG, "Cash flow: {} months, RM {:.2f} total, RM {:.2f} retention",
                cashFlowMonths.size(), totalProjectCost, totalRetention);

        return new CashFlowOutput(buildingId, sched.projectStartDate(),
                sched.projectFinishDate(), cashFlowMonths.size(),
                totalProjectCost, totalRetention, peakOutflow,
                cashFlowMonths, sb.toString());
    }
}
