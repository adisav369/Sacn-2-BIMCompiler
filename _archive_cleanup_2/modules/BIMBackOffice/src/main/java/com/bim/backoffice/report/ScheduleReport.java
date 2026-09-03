package com.bim.backoffice.report;

import com.bim.backoffice.dao.ScheduleDAO;
import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;

/**
 * Schedule Report — 4D topological sort → timeline.
 *
 * <p>Ported from Federation addon 4D timeline tab. Reads ScheduleDAO
 * for Gantt tasks, formats as structured JSON + plain text.
 *
 * // Porting Federation addon 4D timeline tab — Witness: W-RPT-SCHED-1
 */
public class ScheduleReport {

    private static final String TAG = "ScheduleReport";

    // ── Record types ────────────────────────────────────────────────

    public record ReportOutput(String reportId, String title, String buildingId,
                               String generatedDate, TimelineSummary summary,
                               List<PhaseGroup> phases, String plainText) {}

    public record TimelineSummary(String projectStart, String projectFinish,
                                  int totalDays, int totalTasks,
                                  double totalLaborDays, int criticalPathTasks) {}

    public record PhaseGroup(String phase, List<TaskLine> tasks,
                             int phaseDays, int taskCount) {}

    public record TaskLine(String taskId, String taskName, String discipline,
                           int durationDays, String startDate, String finishDate,
                           String laborResource, int crewSize,
                           boolean isCritical) {}

    // ── Generate ────────────────────────────────────────────────────

    public ReportOutput generate(Connection bomConn, Connection compLibConn,
                                 String buildingId, String projectStartDate) {
        ScheduleDAO dao = new ScheduleDAO();
        ScheduleDAO.ScheduleSummary sched;
        try {
            sched = dao.constructionSchedule(bomConn, compLibConn,
                    buildingId, projectStartDate);
        } catch (Exception e) {
            BIMLogger.warn(TAG, "generate: {}", e.getMessage());
            return new ReportOutput("BIM-RPT-SCHED", "Construction Schedule",
                    buildingId, LocalDate.now().toString(),
                    new TimelineSummary(projectStartDate, projectStartDate, 0, 0, 0, 0),
                    List.of(), "Error: " + e.getMessage());
        }

        // Group by phase
        Map<String, List<ScheduleDAO.GanttTask>> byPhase = new LinkedHashMap<>();
        for (var task : sched.tasks()) {
            byPhase.computeIfAbsent(task.phase() != null ? task.phase() : "Unphased",
                    k -> new ArrayList<>()).add(task);
        }

        List<PhaseGroup> phases = new ArrayList<>();
        int criticalCount = 0;
        for (var entry : byPhase.entrySet()) {
            List<TaskLine> tasks = new ArrayList<>();
            int phaseDays = 0;
            for (var t : entry.getValue()) {
                tasks.add(new TaskLine(t.taskId(), t.taskName(), t.discipline(),
                        t.durationDays(), t.startDate(), t.finishDate(),
                        t.laborResource(), t.crewSize(), t.isCritical()));
                phaseDays = Math.max(phaseDays, t.durationDays());
                if (t.isCritical()) criticalCount++;
            }
            phases.add(new PhaseGroup(entry.getKey(), tasks, phaseDays, tasks.size()));
        }

        TimelineSummary summary = new TimelineSummary(
                sched.projectStartDate(), sched.projectFinishDate(),
                sched.totalDays(), sched.totalTasks(),
                sched.totalLaborDays(), criticalCount);

        String plainText = formatPlainText(buildingId, summary, phases);
        return new ReportOutput("BIM-RPT-SCHED",
                "4D Construction Schedule — " + buildingId,
                buildingId, LocalDate.now().toString(), summary, phases, plainText);
    }

    private String formatPlainText(String buildingId, TimelineSummary s,
                                   List<PhaseGroup> phases) {
        StringBuilder sb = new StringBuilder();
        sb.append("4D CONSTRUCTION SCHEDULE — ").append(buildingId).append('\n');
        sb.append("Generated: ").append(LocalDate.now()).append('\n');
        sb.append("═".repeat(70)).append('\n');
        sb.append(String.format("Period: %s → %s (%d days)%n",
                s.projectStart(), s.projectFinish(), s.totalDays()));
        sb.append(String.format("Tasks: %d | Labor-days: %.0f | Critical: %d%n",
                s.totalTasks(), s.totalLaborDays(), s.criticalPathTasks()));
        sb.append("─".repeat(70)).append('\n');

        for (var phase : phases) {
            sb.append(String.format("%n▸ %s (%d tasks, ~%d days)%n",
                    phase.phase(), phase.taskCount(), phase.phaseDays()));
            for (var t : phase.tasks()) {
                sb.append(String.format("  %s%-30s %3dd  %s → %s  %s%n",
                        t.isCritical() ? "★ " : "  ",
                        truncate(t.taskName(), 30), t.durationDays(),
                        t.startDate(), t.finishDate(), t.discipline()));
            }
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        return (s != null && s.length() > max) ? s.substring(0, max - 2) + ".." : (s != null ? s : "");
    }
}
