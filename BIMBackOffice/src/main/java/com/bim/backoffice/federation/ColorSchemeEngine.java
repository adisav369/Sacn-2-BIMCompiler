package com.bim.backoffice.federation;

import com.bim.backoffice.dao.CostDAO;
import com.bim.backoffice.dao.ScheduleDAO;
import com.bim.backoffice.dao.SustainabilityDAO;
import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.util.*;

/**
 * Color Scheme Engine — ported from Federation addon visualization_manager.py
 * + bbox_visualization.py + color_palette.py.
 *
 * <p>Produces JSON color maps {@code { element_id: "#rrggbb" }} for the WebUI
 * to apply to 3D elements. Four dimension-based coloring schemes:
 * <ol>
 *   <li><b>Discipline</b> — AD_Org_ID → fixed color (bbox_visualization.py DISCIPLINE_COLORS)</li>
 *   <li><b>4D Phase</b> — construction sequence → gradient (color_palette.py "Construction Phase")</li>
 *   <li><b>5D Cost</b> — price bracket → heat map</li>
 *   <li><b>6D Carbon</b> — embodied carbon → green-to-red gradient</li>
 * </ol>
 *
 * // Porting Federation addon bbox_visualization.py + color_palette.py — Witness: W-COLOR-1
 */
public class ColorSchemeEngine {

    private static final String TAG = "ColorSchemeEngine";

    // ── Record types ────────────────────────────────────────────────

    public record ColorEntry(String elementId, String hex) {}

    public record ColorMap(String scheme, String buildingId,
                           Map<String, String> colors, int elementCount) {}

    // ── Discipline colors — ported from bbox_visualization.py ──────
    // Key = discipline code (AD_Org_ID name), Value = hex color

    private static final Map<String, String> DISCIPLINE_COLORS = Map.ofEntries(
            Map.entry("ACMV",         "#00bfff"),  // Cyan/Blue
            Map.entry("FP",           "#ff0000"),  // Red
            Map.entry("ELEC",         "#ffff00"),  // Yellow
            Map.entry("PLUMB",        "#0066ff"),  // Dark Blue (water)
            Map.entry("GAS",          "#cc00cc"),  // Purple/Magenta
            Map.entry("ICT",          "#00ff00"),  // Green (data/comms)
            Map.entry("FURN",         "#996633"),  // Brown/wood (furniture)
            Map.entry("CW",           "#00ffff"),  // Cyan
            Map.entry("SP",           "#ff8000"),  // Orange
            Map.entry("ARC",          "#808080"),  // Gray
            Map.entry("ARCHITECTURE", "#808080"),
            Map.entry("STR",          "#996633"),  // Brown (concrete)
            Map.entry("STRUCTURE",    "#996633"),
            Map.entry("REB",          "#e68033"),  // Rust/orange (reinforcement)
            Map.entry("GEO",          "#2666a6"),  // Blue water
            Map.entry("BOOM",         "#f28c1a"),  // High-viz orange
            Map.entry("FAC",          "#999999"),  // Concrete gray
            Map.entry("IOT",          "#33cc4d"),  // Monitoring green
            Map.entry("DEFAULT",      "#b3b3b3")   // Light gray
    );

    // ── Construction phase colors — ported from color_palette.py ───

    private static final Map<String, String> PHASE_COLORS = Map.ofEntries(
            Map.entry("Foundation",   "#665c4d"),
            Map.entry("Structure",    "#b3a699"),
            Map.entry("Envelope",     "#d9bfa6"),
            Map.entry("Interior",     "#e6d9cc"),
            Map.entry("MEP Rough",    "#809ab3"),
            Map.entry("Finishes",     "#f2e6d9"),
            Map.entry("Phase 1",      "#cc6666"),
            Map.entry("Phase 2",      "#66b366"),
            Map.entry("Phase 3",      "#6666cc"),
            Map.entry("Phase 4",      "#cccc66"),
            Map.entry("Temporary",    "#ff8000"),
            Map.entry("Existing",     "#999999"),
            Map.entry("Demolish",     "#ff0000"),
            Map.entry("New Work",     "#00ff00"),
            Map.entry("Future",       "#8080ff"),
            Map.entry("Complete",     "#4d4d4d")
    );

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Color by discipline — each BOM line colored by its discipline code.
     * Reads m_bom_line rows with discipline info from BOM DB.
     */
    public ColorMap colorByDiscipline(Connection bomConn, String buildingId) {
        Map<String, String> colors = new LinkedHashMap<>();
        String sql = "SELECT bl.child_product_id, b.m_product_category_id " +
                     "FROM m_bom_line bl JOIN m_bom b ON bl.M_BOM_ID = b.M_BOM_ID " +
                     "WHERE bl.is_active = 1";
        try (var stmt = bomConn.prepareStatement(sql);
             var rs = stmt.executeQuery()) {
            while (rs.next()) {
                String elementId = rs.getString("child_product_id");
                String discipline = rs.getString("m_product_category_id");
                String hex = disciplineColor(discipline);
                colors.put(elementId, hex);
            }
        } catch (Exception e) {
            BIMLogger.warn(TAG, "colorByDiscipline: {}", e.getMessage());
        }
        return new ColorMap("discipline", buildingId, colors, colors.size());
    }

    /**
     * Color by 4D construction phase — elements colored by schedule sequence.
     * Uses ScheduleDAO to get Gantt tasks, maps product → phase color.
     */
    public ColorMap colorByPhase(Connection bomConn, Connection compLibConn,
                                 String buildingId, String projectStartDate) {
        Map<String, String> colors = new LinkedHashMap<>();
        try {
            ScheduleDAO dao = new ScheduleDAO();
            var summary = dao.constructionSchedule(bomConn, compLibConn,
                    buildingId, projectStartDate);
            // Build phase → color; assign gradient by sequence position
            int totalTasks = summary.tasks().size();
            for (int i = 0; i < totalTasks; i++) {
                var task = summary.tasks().get(i);
                // Use phase name if in PHASE_COLORS, else gradient by position
                String hex = PHASE_COLORS.get(task.phase());
                if (hex == null) {
                    hex = sequenceGradient(i, totalTasks);
                }
                colors.put(task.taskId(), hex);
            }
        } catch (Exception e) {
            BIMLogger.warn(TAG, "colorByPhase: {}", e.getMessage());
        }
        return new ColorMap("4D-phase", buildingId, colors, colors.size());
    }

    /**
     * Color by 5D cost band — elements colored by cost bracket (heat map).
     * Low cost = cool blue, high cost = hot red.
     */
    public ColorMap colorByCost(Connection bomConn, Connection compLibConn,
                                String buildingId) {
        Map<String, String> colors = new LinkedHashMap<>();
        try {
            CostDAO dao = new CostDAO();
            var summary = dao.costBreakdown(bomConn, compLibConn, buildingId);
            if (summary.lines().isEmpty()) return new ColorMap("5D-cost", buildingId, colors, 0);

            // Find cost range for normalization
            double minCost = summary.lines().stream()
                    .mapToDouble(CostDAO.CostLine::totalCost).min().orElse(0);
            double maxCost = summary.lines().stream()
                    .mapToDouble(CostDAO.CostLine::totalCost).max().orElse(1);
            double range = maxCost - minCost;
            if (range < 0.01) range = 1.0;

            for (var line : summary.lines()) {
                double t = (line.totalCost() - minCost) / range; // 0..1
                String hex = heatMapColor(t);
                colors.put(line.bomId(), hex);
            }
        } catch (Exception e) {
            BIMLogger.warn(TAG, "colorByCost: {}", e.getMessage());
        }
        return new ColorMap("5D-cost", buildingId, colors, colors.size());
    }

    /**
     * Color by 6D carbon rating — elements colored by embodied carbon.
     * Low carbon = green, high carbon = red.
     */
    public ColorMap colorByCarbon(Connection bomConn, Connection compLibConn,
                                  String buildingId) {
        Map<String, String> colors = new LinkedHashMap<>();
        try {
            SustainabilityDAO dao = new SustainabilityDAO();
            var summary = dao.carbonFootprint(bomConn, compLibConn, buildingId);
            if (summary.lines().isEmpty()) return new ColorMap("6D-carbon", buildingId, colors, 0);

            double minC = summary.lines().stream()
                    .mapToDouble(SustainabilityDAO.CarbonLine::totalCarbon).min().orElse(0);
            double maxC = summary.lines().stream()
                    .mapToDouble(SustainabilityDAO.CarbonLine::totalCarbon).max().orElse(1);
            double range = maxC - minC;
            if (range < 0.01) range = 1.0;

            for (var line : summary.lines()) {
                double t = (line.totalCarbon() - minC) / range; // 0..1
                // Green (low carbon) → Red (high carbon)
                String hex = carbonGradient(t);
                colors.put(line.bomId(), hex);
            }
        } catch (Exception e) {
            BIMLogger.warn(TAG, "colorByCarbon: {}", e.getMessage());
        }
        return new ColorMap("6D-carbon", buildingId, colors, colors.size());
    }

    // ── Static accessors ────────────────────────────────────────────

    /** Get the full discipline color palette (for legend rendering). */
    public static Map<String, String> getDisciplineColors() {
        return DISCIPLINE_COLORS;
    }

    /** Get the full phase color palette (for legend rendering). */
    public static Map<String, String> getPhaseColors() {
        return PHASE_COLORS;
    }

    /** Look up discipline color, falling back to DEFAULT. */
    public static String disciplineColor(String discipline) {
        if (discipline == null) return DISCIPLINE_COLORS.get("DEFAULT");
        return DISCIPLINE_COLORS.getOrDefault(discipline.toUpperCase(),
                DISCIPLINE_COLORS.get("DEFAULT"));
    }

    // ── Color math ──────────────────────────────────────────────────

    /** Sequence gradient: blue → cyan → green → yellow → red across 0..totalTasks. */
    static String sequenceGradient(int index, int total) {
        if (total <= 1) return "#4d88ff";
        double t = (double) index / (total - 1); // 0..1
        return heatMapColor(t);
    }

    /** Heat map: blue (t=0) → cyan → green → yellow → red (t=1). */
    static String heatMapColor(double t) {
        t = Math.max(0, Math.min(1, t));
        double r, g, b;
        if (t < 0.25) {
            r = 0; g = t * 4; b = 1;
        } else if (t < 0.5) {
            r = 0; g = 1; b = 1 - (t - 0.25) * 4;
        } else if (t < 0.75) {
            r = (t - 0.5) * 4; g = 1; b = 0;
        } else {
            r = 1; g = 1 - (t - 0.75) * 4; b = 0;
        }
        return String.format("#%02x%02x%02x",
                (int)(r * 255), (int)(g * 255), (int)(b * 255));
    }

    /** Carbon gradient: green (t=0) → yellow → red (t=1). */
    static String carbonGradient(double t) {
        t = Math.max(0, Math.min(1, t));
        double r, g;
        if (t < 0.5) {
            r = t * 2;
            g = 1.0;
        } else {
            r = 1.0;
            g = 1.0 - (t - 0.5) * 2;
        }
        return String.format("#%02x%02x%02x", (int)(r * 255), (int)(g * 255), 0);
    }
}
