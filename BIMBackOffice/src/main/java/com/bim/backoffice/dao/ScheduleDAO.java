package com.bim.backoffice.dao;

import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO for 4D Construction Scheduling — verb-ordered Gantt from BOM × M_Product.
 *
 * <p>Borrows construction sequence rules from IfcOpenShell Federation addon
 * (schedule/schedule_generator.py) and re-grounds them on the ERP product table.
 * Each M_Product carries construction_phase + construction_sequence + labor data.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Load product schedule data from component_library.db</li>
 *   <li>Iterate BOM lines, group by (phase, sequence, discipline)</li>
 *   <li>Calculate durations: qty / productivity_rate</li>
 *   <li>Calculate dates using Malaysian calendar (Mon-Sat working)</li>
 *   <li>Return Gantt tasks ordered by sequence → phase → discipline</li>
 * </ol>
 *
 * // Implementing TIER1_SRS.md §4D — Witness: W-4D-SCHED-1
 */
public class ScheduleDAO {

    private static final String TAG = "ScheduleDAO";

    // ── Record types ────────────────────────────────────────────────

    public record GanttTask(String taskId, String taskName, String phase,
                            String discipline, int sequence, int durationDays,
                            String startDate, String finishDate,
                            String laborResource, int crewSize,
                            String dependency, double qty, String uom,
                            boolean isCritical) {}

    public record ScheduleSummary(String buildingId, String projectStartDate,
                                  String projectFinishDate, int totalDays,
                                  int totalTasks, double totalLaborDays,
                                  List<GanttTask> tasks,
                                  Map<String, Integer> tasksByPhase,
                                  Map<String, Double> laborDaysByResource) {}

    // ── Queries ─────────────────────────────────────────────────────

    /**
     * Generate construction schedule for a compiled building.
     * Groups BOM lines by (construction_phase, construction_sequence),
     * calculates durations, chains phases via predecessor logic.
     */
    public ScheduleSummary constructionSchedule(Connection bomConn,
                                                 Connection compLibConn,
                                                 String buildingId,
                                                 String projectStartDate)
            throws SQLException {
        Map<String, ProductSchedule> schedMap = loadScheduleMap(compLibConn);

        // Phase 1: aggregate BOM lines into task groups
        String sql = """
                SELECT bl.child_product_id, bl.qty, b.m_product_category_id, b.bom_name
                FROM m_bom_line bl
                JOIN m_bom b ON bl.M_BOM_ID = b.M_BOM_ID
                WHERE bl.child_product_id IS NOT NULL AND bl.is_active = 1
                ORDER BY bl.bom_child_id
                """;

        // Group key: phase + sequence + discipline
        Map<String, TaskAccum> accum = new LinkedHashMap<>();

        try (PreparedStatement ps = bomConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String productId = rs.getString("child_product_id");
                int qty = rs.getInt("qty");
                String rawDisc = rs.getString("m_product_category_id");
                String discipline = rawDisc != null ? rawDisc : "GEN";

                ProductSchedule ps2 = schedMap.get(productId);
                if (ps2 == null || ps2.phase == null) continue;

                String key = String.format("%03d|%s|%s", ps2.sequence, ps2.phase, discipline);
                final String disc = discipline;
                accum.computeIfAbsent(key, k -> new TaskAccum(
                        ps2.phase, disc, ps2.sequence,
                        ps2.laborResource, ps2.crewSize,
                        ps2.productivityRate, ps2.costUom));
                accum.get(key).totalQty += qty;
                accum.get(key).elementCount++;
            }
        }

        // Phase 2: sort by sequence key, then calculate durations and chain dates
        var sortedAccum = new java.util.TreeMap<>(accum);

        LocalDate start = LocalDate.parse(projectStartDate);
        LocalDate currentDate = start;
        int prevSequence = -1;
        String prevTaskId = null;

        List<GanttTask> tasks = new ArrayList<>();
        Map<String, Integer> tasksByPhase = new LinkedHashMap<>();
        Map<String, Double> laborDaysByResource = new LinkedHashMap<>();
        double totalLaborDays = 0;
        int taskNum = 0;

        for (var entry : sortedAccum.entrySet()) {
            TaskAccum ta = entry.getValue();
            taskNum++;

            // Duration = qty / productivity (minimum 1 day)
            double durationRaw = ta.productivityRate > 0
                    ? ta.totalQty / ta.productivityRate : 1;
            int durationDays = Math.max(1, (int) Math.ceil(durationRaw));

            // If new sequence group, chain from previous finish
            String dependency = null;
            if (ta.sequence > prevSequence && prevTaskId != null) {
                dependency = prevTaskId;
            }

            // Calculate working dates (Mon-Sat, skip Sunday)
            LocalDate taskStart = currentDate;
            LocalDate taskFinish = addWorkingDays(taskStart, durationDays);

            String taskId = "T" + String.format("%03d", taskNum);
            double laborDays = durationDays * ta.crewSize;
            totalLaborDays += laborDays;

            tasks.add(new GanttTask(
                    taskId,
                    ta.phase + " — " + ta.discipline + " (" + ta.elementCount + " elements)",
                    ta.phase, ta.discipline, ta.sequence, durationDays,
                    taskStart.toString(), taskFinish.toString(),
                    ta.laborResource, ta.crewSize, dependency,
                    ta.totalQty, ta.costUom,
                    ta.sequence <= 4  // substructure + superstructure = critical path
            ));

            tasksByPhase.merge(ta.phase, 1, Integer::sum);
            laborDaysByResource.merge(ta.laborResource, laborDays, Double::sum);

            // Advance date for next sequence group
            if (ta.sequence > prevSequence) {
                currentDate = taskFinish;
                prevSequence = ta.sequence;
            }
            prevTaskId = taskId;
        }

        LocalDate projectFinish = currentDate;
        int totalDays = (int) (projectFinish.toEpochDay() - start.toEpochDay());

        BIMLogger.info(TAG, "Schedule: {} tasks, {} working days, {:.0f} labor-days",
                tasks.size(), totalDays, totalLaborDays);

        return new ScheduleSummary(buildingId, projectStartDate,
                projectFinish.toString(), totalDays, tasks.size(),
                totalLaborDays, tasks, tasksByPhase, laborDaysByResource);
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private record ProductSchedule(String phase, int sequence, String laborResource,
                                   int crewSize, double productivityRate,
                                   String costUom) {}

    private static class TaskAccum {
        final String phase, discipline, laborResource, costUom;
        final int sequence, crewSize;
        final double productivityRate;
        double totalQty = 0;
        int elementCount = 0;

        TaskAccum(String phase, String discipline, int sequence,
                  String laborResource, int crewSize,
                  double productivityRate, String costUom) {
            this.phase = phase;
            this.discipline = discipline;
            this.sequence = sequence;
            this.laborResource = laborResource;
            this.crewSize = crewSize;
            this.productivityRate = productivityRate;
            this.costUom = costUom;
        }
    }

    private Map<String, ProductSchedule> loadScheduleMap(Connection compLibConn)
            throws SQLException {
        Map<String, ProductSchedule> map = new LinkedHashMap<>();
        String sql = """
                SELECT Value, construction_phase, construction_sequence,
                       labor_resource, labor_crew_size, productivity_rate, cost_uom
                FROM M_Product
                WHERE is_active = 1 AND construction_phase IS NOT NULL
                """;
        try (PreparedStatement ps = compLibConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("Value"),
                        new ProductSchedule(
                                rs.getString("construction_phase"),
                                rs.getInt("construction_sequence"),
                                rs.getString("labor_resource"),
                                rs.getInt("labor_crew_size"),
                                rs.getDouble("productivity_rate"),
                                rs.getString("cost_uom")));
            }
        }
        return map;
    }

    /**
     * Add working days (Malaysian calendar: Mon-Sat working, Sun off).
     */
    public static LocalDate addWorkingDays(LocalDate start, int workingDays) {
        LocalDate date = start;
        int added = 0;
        while (added < workingDays) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return date;
    }
}
