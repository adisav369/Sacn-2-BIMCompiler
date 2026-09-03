package com.bim.backoffice.report;

import com.bim.backoffice.dao.CostDAO;
import com.bim.backoffice.dao.ScheduleDAO;
import com.bim.backoffice.dao.ScheduleDAO.GanttTask;
import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * BIM-RPT-13: Earned Value Management — PV, EV, AC, SPI, CPI, EAC, VAC.
 *
 * <p>Intersects CostDAO (BAC = grand total) with ScheduleDAO (% complete).
 * AC is stubbed at 0 until iDempiere procurement is wired — report is DRAFT.
 *
 * // Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-13 — Witness: W-RPT-EVM
 */
public class EvmReport {

    private static final String TAG = "EvmReport";

    private static final String DRAFT_NOTE =
            "NOTE: AC (Actual Cost) stubbed. EVM metrics are DRAFT until iDempiere procurement wired.";

    public record EvmOutput(String buildingId, String reportDate,
                            double bac, double pv, double ev, double ac,
                            double spi, double cpi, double eac, double vac,
                            double percentComplete, boolean isDraft,
                            String draftNote, String plainText) {}

    /**
     * Generate EVM report from CostDAO x ScheduleDAO intersection.
     */
    public EvmOutput generate(Connection bomConn, Connection compLibConn,
                              String buildingId, String startDate) throws SQLException {
        // BAC from CostDAO
        CostDAO costDao = new CostDAO();
        CostDAO.CostSummary cost = costDao.costBreakdown(bomConn, compLibConn, buildingId);
        double bac = cost.grandTotal();

        // Schedule for % complete calculation
        ScheduleDAO schedDao = new ScheduleDAO();
        ScheduleDAO.ScheduleSummary sched = schedDao.constructionSchedule(
                bomConn, compLibConn, buildingId, startDate);

        // Calculate % complete: tasks completed (past finish date) / total tasks
        LocalDate today = LocalDate.now();
        long completedTasks = sched.tasks().stream()
                .filter(t -> !LocalDate.parse(t.finishDate()).isAfter(today))
                .count();
        double percentComplete = sched.totalTasks() > 0
                ? (double) completedTasks / sched.totalTasks() : 0;

        // PV: scheduled cost to date (based on schedule phase completion)
        double scheduledPct = 0;
        if (sched.totalDays() > 0) {
            long daysElapsed = today.toEpochDay() - LocalDate.parse(startDate).toEpochDay();
            scheduledPct = Math.min(1.0, Math.max(0.0, (double) daysElapsed / sched.totalDays()));
        }
        double pv = bac * scheduledPct;

        // EV: % complete x BAC
        double ev = percentComplete * bac;

        // AC: STUB — 0 until iDempiere procurement wired
        double ac = 0.0;

        // Derived metrics
        double spi = pv > 0 ? ev / pv : 0;
        double cpi = ac > 0 ? ev / ac : 0;  // 0 because AC is stubbed
        double eac = cpi > 0 ? bac / cpi : bac;  // BAC when CPI = 0 (DRAFT)
        double vac = bac - eac;

        // Build plain text
        StringBuilder sb = new StringBuilder();
        sb.append("EARNED VALUE MANAGEMENT — ").append(buildingId).append("\n");
        sb.append("═".repeat(60)).append("\n\n");
        sb.append("⚠ DRAFT — ").append(DRAFT_NOTE).append("\n\n");
        sb.append(String.format("BAC (Budget at Completion):     RM %,.2f%n", bac));
        sb.append(String.format("PV  (Planned Value):            RM %,.2f%n", pv));
        sb.append(String.format("EV  (Earned Value):             RM %,.2f%n", ev));
        sb.append(String.format("AC  (Actual Cost):              RM %,.2f  [STUB]%n", ac));
        sb.append("\n");
        sb.append(String.format("SPI (Schedule Performance):     %.2f%n", spi));
        sb.append(String.format("CPI (Cost Performance):         %.2f  [DRAFT — AC=0]%n", cpi));
        sb.append(String.format("EAC (Estimate at Completion):   RM %,.2f  [DRAFT]%n", eac));
        sb.append(String.format("VAC (Variance at Completion):   RM %,.2f  [DRAFT]%n", vac));
        sb.append("\n");
        sb.append(String.format("Percent Complete:               %.1f%%%n", percentComplete * 100));
        sb.append(String.format("Completed Tasks:                %d / %d%n",
                completedTasks, sched.totalTasks()));
        sb.append(String.format("Schedule Days Elapsed:          %.0f / %d%n",
                scheduledPct * sched.totalDays(), sched.totalDays()));

        String reportDate = today.toString();

        BIMLogger.info(TAG, "EVM report: BAC={:.2f}, PV={:.2f}, EV={:.2f}, AC={:.2f} (DRAFT)",
                bac, pv, ev, ac);

        return new EvmOutput(buildingId, reportDate, bac, pv, ev, ac,
                spi, cpi, eac, vac, percentComplete, true, DRAFT_NOTE,
                sb.toString());
    }
}
