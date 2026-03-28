package com.bim.backoffice.report;

import com.bim.backoffice.dao.ScheduleDAO;
import com.bim.backoffice.dao.ScheduleDAO.GanttTask;
import com.bim.backoffice.dao.ScheduleDAO.ScheduleSummary;
import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BIM-RPT-12: 4D Construction Programme — Gantt, WBS, critical path.
 *
 * <p>Richer view of ScheduleDAO output: adds WBS structure by phase,
 * critical path highlighting, and phase-level summaries.
 *
 * // Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-12 — Witness: W-RPT-GANTT
 */
public class GanttReport {

    private static final String TAG = "GanttReport";

    public record WbsPhase(String phase, int taskCount, int totalDurationDays,
                           double totalLaborDays, List<GanttTask> tasks) {}

    public record GanttOutput(String buildingId, String projectStart, String projectFinish,
                              int totalDays, int totalTasks, double totalLaborDays,
                              int criticalPathTasks, List<WbsPhase> wbsPhases,
                              List<GanttTask> criticalPath,
                              String plainText) {}

    /**
     * Generate Gantt report with WBS breakdown and critical path.
     */
    public GanttOutput generate(Connection bomConn, Connection compLibConn,
                                String buildingId, String startDate) throws SQLException {
        ScheduleDAO dao = new ScheduleDAO();
        ScheduleSummary sched = dao.constructionSchedule(bomConn, compLibConn,
                buildingId, startDate);

        // Group tasks by phase for WBS breakdown
        Map<String, List<GanttTask>> byPhase = new LinkedHashMap<>();
        List<GanttTask> criticalPath = new ArrayList<>();

        for (GanttTask task : sched.tasks()) {
            byPhase.computeIfAbsent(task.phase(), k -> new ArrayList<>()).add(task);
            if (task.isCritical()) {
                criticalPath.add(task);
            }
        }

        // Build WBS phase summaries
        List<WbsPhase> wbsPhases = new ArrayList<>();
        for (var entry : byPhase.entrySet()) {
            List<GanttTask> phaseTasks = entry.getValue();
            int phaseDuration = phaseTasks.stream().mapToInt(GanttTask::durationDays).sum();
            double phaseLaborDays = phaseTasks.stream()
                    .mapToDouble(t -> (double) t.durationDays() * t.crewSize())
                    .sum();
            wbsPhases.add(new WbsPhase(entry.getKey(), phaseTasks.size(),
                    phaseDuration, phaseLaborDays, phaseTasks));
        }

        // Build plain text summary
        StringBuilder sb = new StringBuilder();
        sb.append("4D CONSTRUCTION PROGRAMME — ").append(buildingId).append("\n");
        sb.append("═".repeat(60)).append("\n\n");
        sb.append("Project Start:  ").append(sched.projectStartDate()).append("\n");
        sb.append("Project Finish: ").append(sched.projectFinishDate()).append("\n");
        sb.append("Total Duration: ").append(sched.totalDays()).append(" days\n");
        sb.append("Total Tasks:    ").append(sched.totalTasks()).append("\n");
        sb.append(String.format("Total Labor:    %.0f labor-days%n", sched.totalLaborDays()));
        sb.append("Critical Path:  ").append(criticalPath.size()).append(" tasks\n\n");

        sb.append("WBS BREAKDOWN BY PHASE\n");
        sb.append("─".repeat(60)).append("\n");
        for (WbsPhase phase : wbsPhases) {
            sb.append(String.format("  %-30s %d tasks, %d days, %.0f labor-days%n",
                    phase.phase(), phase.taskCount(), phase.totalDurationDays(),
                    phase.totalLaborDays()));
            for (GanttTask task : phase.tasks()) {
                sb.append(String.format("    %s %-40s %s → %s (%dd)%s%n",
                        task.taskId(), task.taskName(),
                        task.startDate(), task.finishDate(), task.durationDays(),
                        task.isCritical() ? " ★CRITICAL" : ""));
            }
        }

        sb.append("\nCRITICAL PATH\n");
        sb.append("─".repeat(60)).append("\n");
        for (GanttTask task : criticalPath) {
            sb.append(String.format("  %s %-40s %s → %s (%dd)%n",
                    task.taskId(), task.taskName(),
                    task.startDate(), task.finishDate(), task.durationDays()));
        }

        BIMLogger.info(TAG, "Gantt report: {} tasks, {} critical, {} days",
                sched.totalTasks(), criticalPath.size(), sched.totalDays());

        return new GanttOutput(buildingId, sched.projectStartDate(),
                sched.projectFinishDate(), sched.totalDays(), sched.totalTasks(),
                sched.totalLaborDays(), criticalPath.size(), wbsPhases,
                criticalPath, sb.toString());
    }
}
