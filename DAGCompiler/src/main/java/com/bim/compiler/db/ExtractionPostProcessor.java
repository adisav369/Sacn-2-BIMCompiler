/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.db;

import com.bim.compiler.util.BIMLogger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.util.*;

/**
 * Post-processes an extracted DB (from extractIFCtoDB.py or extractIFCtoDB_open.py):
 *
 * <ol>
 *   <li><b>Unit scale fix</b> — scales vertices, transforms, rtree from mm to metres
 *       (Hell_GeometryScale fix). Reads unit_scale from project_metadata or detects
 *       from data range.</li>
 *   <li><b>Discipline refinement</b> — upgrades generic "MEP" to VENT/HVAC/HEAT/PLB/SAN
 *       using element_type / material_name keyword heuristics.</li>
 *   <li><b>Forensic logging</b> — structured abstract via BIMLogger.</li>
 * </ol>
 *
 * <p>This is the Java equivalent of the Python fix_vertex_scale() +
 * infer_discipline() in extractIFCtoDB_open.py.  It runs on the DB,
 * not the IFC — no ifcopenshell dependency.
 *
 * <p>Usage:
 * <pre>
 *   ExtractionPostProcessor pp = new ExtractionPostProcessor(dbPath);
 *   pp.run();   // applies all fixes + prints forensic abstract
 *   pp.close();
 * </pre>
 *
 * // Implementing Hell_GeometryScale.md — Witness: W-HELL-GEOSCALE
 */
public class ExtractionPostProcessor implements AutoCloseable {

    private static final String TAG = "EXTPP";

    private final String dbPath;
    private final Connection conn;

    // Forensic counters
    private int totalElements;
    private int verticesScaled;
    private int transformsScaled;
    private int disciplineUpgrades;
    private double unitScale = 1.0;
    private final Map<String, Integer> byDiscipline = new LinkedHashMap<>();
    private final Map<String, Integer> byClass = new LinkedHashMap<>();
    private final Map<String, Integer> byStorey = new LinkedHashMap<>();
    private final Map<String, double[]> discBbox = new LinkedHashMap<>(); // disc → [minX,maxX,minY,maxY,minZ,maxZ]
    private final Map<String, int[]> discCoherence = new LinkedHashMap<>(); // disc → [inside, outside]
    // disc → list of "ifc_class: N (cx_range, cy_range)" for outliers
    private final Map<String, List<String>> discOutlierDetail = new LinkedHashMap<>();
    private double[] worldMin;
    private double[] worldMax;

    public ExtractionPostProcessor(String dbPath) throws SQLException {
        this.dbPath = dbPath;
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    /**
     * Run all post-processing steps and print forensic abstract.
     */
    public void run() throws SQLException {
        BIMLogger.info(TAG, "Post-processing: {}", dbPath);

        detectUnitScale();
        if (needsScaling()) {
            fixVertexScale();
            fixTransformScale();
            fixRtreeScale();
            fixProjectMetadata();
        }
        refineDisciplines();
        collectForensics();
        printAbstract();
    }

    // =========================================================================
    // Step 1: Unit scale detection
    // =========================================================================

    private void detectUnitScale() throws SQLException {
        // Try project_metadata first (set by extractIFCtoDB_open.py)
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT value FROM project_metadata WHERE key = 'unit_scale'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                unitScale = Double.parseDouble(rs.getString(1));
                BIMLogger.info(TAG, "unit_scale from metadata: {}", unitScale);
                return;
            }
        } catch (SQLException e) {
            // project_metadata table may not exist
        }

        // Fallback: detect from data range
        // If max(center_x) > 100, data is likely in mm (buildings don't exceed 100m)
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                "SELECT MAX(ABS(center_x)), MAX(ABS(center_y)), MAX(ABS(center_z)) " +
                "FROM element_transforms")) {
            if (rs.next()) {
                double maxAbs = Math.max(rs.getDouble(1),
                                Math.max(rs.getDouble(2), rs.getDouble(3)));
                if (maxAbs > 500.0) {
                    // Very likely mm — typical building is 0–50m but 0–50,000mm
                    unitScale = 0.001;
                    BIMLogger.warn(TAG,
                        "unit_scale not in metadata; detected mm (max_abs={:.1f}), using 0.001",
                        maxAbs);
                } else {
                    unitScale = 1.0;
                    BIMLogger.info(TAG, "unit_scale not in metadata; data range OK (max_abs={:.1f}m)", maxAbs);
                }
            }
        }
    }

    private boolean needsScaling() {
        return Math.abs(unitScale - 1.0) > 1e-9;
    }

    // =========================================================================
    // Step 2: Vertex scale fix (Hell_GeometryScale)
    // =========================================================================

    private void fixVertexScale() throws SQLException {
        BIMLogger.stage(1, "VertexScale", String.format("scale=%.6f", unitScale));

        List<String> hashes = new ArrayList<>();
        List<byte[]> blobs = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                "SELECT geometry_hash, vertices, vertex_count FROM base_geometries")) {
            while (rs.next()) {
                byte[] vblob = rs.getBytes("vertices");
                int vcount = rs.getInt("vertex_count");
                if (vblob == null || vcount == 0) continue;
                hashes.add(rs.getString("geometry_hash"));
                blobs.add(vblob);
                counts.add(vcount);
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE base_geometries SET vertices = ? WHERE geometry_hash = ?")) {
            conn.setAutoCommit(false);

            for (int i = 0; i < hashes.size(); i++) {
                byte[] vblob = blobs.get(i);
                ByteBuffer buf = ByteBuffer.wrap(vblob).order(ByteOrder.LITTLE_ENDIAN);
                ByteBuffer out = ByteBuffer.allocate(vblob.length).order(ByteOrder.LITTLE_ENDIAN);

                int nFloats = vblob.length / 4;
                for (int j = 0; j < nFloats; j++) {
                    float val = buf.getFloat();
                    out.putFloat((float)(val * unitScale));
                }

                ps.setBytes(1, out.array());
                ps.setString(2, hashes.get(i));
                ps.addBatch();
                verticesScaled++;
            }
            ps.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
        }

        BIMLogger.info(TAG, "Scaled {} vertex blobs x {}", verticesScaled, unitScale);
    }

    private void fixTransformScale() throws SQLException {
        BIMLogger.stage(2, "TransformScale", String.format("scale=%.6f", unitScale));

        try (Statement stmt = conn.createStatement()) {
            transformsScaled = stmt.executeUpdate(String.format(
                "UPDATE element_transforms SET " +
                "center_x = center_x * %f, " +
                "center_y = center_y * %f, " +
                "center_z = center_z * %f",
                unitScale, unitScale, unitScale));
        }

        BIMLogger.info(TAG, "Scaled {} transforms", transformsScaled);
    }

    private void fixRtreeScale() throws SQLException {
        BIMLogger.stage(3, "RtreeScale", String.format("scale=%.6f", unitScale));

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(String.format(
                "UPDATE elements_rtree SET " +
                "minX = minX * %f, maxX = maxX * %f, " +
                "minY = minY * %f, maxY = maxY * %f, " +
                "minZ = minZ * %f, maxZ = maxZ * %f",
                unitScale, unitScale, unitScale, unitScale, unitScale, unitScale));
        }

        BIMLogger.info(TAG, "Scaled rtree bboxes");
    }

    private void fixProjectMetadata() throws SQLException {
        // Rescale camera metadata if present
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(String.format(
                "UPDATE project_metadata SET value = CAST(ROUND(CAST(value AS REAL) * %f, 3) AS TEXT) " +
                "WHERE key IN ('view_center_x','view_center_y','view_center_z','view_distance')",
                unitScale));
        }
        // Record that scaling was applied
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO project_metadata (key, value) VALUES (?, ?)")) {
            ps.setString(1, "unit_scale_applied");
            ps.setString(2, String.valueOf(unitScale));
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // Step 3: Discipline refinement (MEP → sub-discipline)
    // =========================================================================

    /**
     * Swedish/German/English keyword → discipline mapping.
     * Order matters — first match wins.
     */
    private static final String[][] DISCIPLINE_HINTS = {
        // Ventilation / Duct
        {"kanal",   "VENT"},   // Swedish: duct/channel
        {"spiro",   "VENT"},   // Spiro spiral duct
        {"duct",    "VENT"},
        {"air",     "VENT"},
        {"fläkt",   "VENT"},   // Swedish: fan
        // Sanitary / Drainage
        {"avlopp",  "SAN"},    // Swedish: drain
        {"sanit",   "SAN"},
        {"drain",   "SAN"},
        // Plumbing / Water
        {"vatten",  "PLB"},    // Swedish: water
        {"plumb",   "PLB"},
        {"copper",  "PLB"},
        {"pex",     "PLB"},
        // Heating
        {"värme",   "HEAT"},   // Swedish: heat
        {"radi",    "HEAT"},   // radiator
        {"heat",    "HEAT"},
        // Cooling / HVAC
        {"kyla",    "HVAC"},   // Swedish: cooling
        {"cool",    "HVAC"},
        {"kyl",     "HVAC"},
        // Electrical
        {"lampa",   "ELEC"},   // Swedish: lamp
        {"light",   "ELEC"},
        {"elec",    "ELEC"},
        {"cable",   "ELEC"},
        // Fire protection
        {"brand",   "FP"},     // Swedish: fire
        {"fire",    "FP"},
        {"sprinkl", "FP"},
    };

    private void refineDisciplines() throws SQLException {
        BIMLogger.stage(4, "DisciplineRefine", "upgrading generic MEP");

        // Fetch all MEP-tagged elements with their element_type and material_name
        List<Object[]> candidates = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                "SELECT guid, element_type, material_name FROM elements_meta " +
                "WHERE discipline = 'MEP'")) {
            while (rs.next()) {
                candidates.add(new Object[] {
                    rs.getString("guid"),
                    rs.getString("element_type"),
                    rs.getString("material_name")
                });
            }
        }

        if (candidates.isEmpty()) {
            BIMLogger.info(TAG, "No generic MEP elements to refine");
            return;
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE elements_meta SET discipline = ? WHERE guid = ?")) {
            conn.setAutoCommit(false);

            for (Object[] row : candidates) {
                String guid = (String) row[0];
                String elemType = (String) row[1];
                String material = (String) row[2];

                String refined = inferSubDiscipline(elemType, material);
                if (refined != null) {
                    ps.setString(1, refined);
                    ps.setString(2, guid);
                    ps.addBatch();
                    disciplineUpgrades++;
                    BIMLogger.fine(TAG, "MEP→{} guid={} type={} mat={}",
                        refined, guid, elemType, material);
                }
            }

            ps.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
        }

        BIMLogger.info(TAG, "Refined {}/{} MEP elements to sub-disciplines",
            disciplineUpgrades, candidates.size());
    }

    private String inferSubDiscipline(String elementType, String materialName) {
        String[] texts = {
            elementType != null ? elementType.toLowerCase() : "",
            materialName != null ? materialName.toLowerCase() : ""
        };
        for (String text : texts) {
            if (text.isEmpty()) continue;
            for (String[] hint : DISCIPLINE_HINTS) {
                if (text.contains(hint[0])) {
                    return hint[1];
                }
            }
        }
        return null;  // stay as MEP
    }

    // =========================================================================
    // Forensic data collection
    // =========================================================================

    private void collectForensics() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Total elements
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM elements_meta")) {
                if (rs.next()) totalElements = rs.getInt(1);
            }

            // By discipline
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT discipline, COUNT(*) FROM elements_meta " +
                    "GROUP BY discipline ORDER BY COUNT(*) DESC")) {
                while (rs.next()) {
                    byDiscipline.put(rs.getString(1), rs.getInt(2));
                }
            }

            // By IFC class
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT ifc_class, COUNT(*) FROM elements_meta " +
                    "GROUP BY ifc_class ORDER BY COUNT(*) DESC")) {
                while (rs.next()) {
                    byClass.put(rs.getString(1), rs.getInt(2));
                }
            }

            // By storey
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT storey, COUNT(*) FROM elements_meta " +
                    "GROUP BY storey ORDER BY COUNT(*) DESC")) {
                while (rs.next()) {
                    byStorey.put(rs.getString(1), rs.getInt(2));
                }
            }

            // World extent
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT MIN(minX), MAX(maxX), MIN(minY), MAX(maxY), " +
                    "MIN(minZ), MAX(maxZ) FROM elements_rtree")) {
                if (rs.next() && rs.getObject(1) != null) {
                    worldMin = new double[] { rs.getDouble(1), rs.getDouble(3), rs.getDouble(5) };
                    worldMax = new double[] { rs.getDouble(2), rs.getDouble(4), rs.getDouble(6) };
                }
            }

            // Per-discipline bounding box (for coherence check)
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT em.discipline, " +
                    "MIN(et.center_x), MAX(et.center_x), " +
                    "MIN(et.center_y), MAX(et.center_y), " +
                    "MIN(et.center_z), MAX(et.center_z) " +
                    "FROM element_transforms et " +
                    "JOIN elements_meta em ON em.guid = et.guid " +
                    "GROUP BY em.discipline ORDER BY em.discipline")) {
                while (rs.next()) {
                    discBbox.put(rs.getString(1), new double[] {
                        rs.getDouble(2), rs.getDouble(3),
                        rs.getDouble(4), rs.getDouble(5),
                        rs.getDouble(6), rs.getDouble(7)
                    });
                }
            }

            // Per-discipline element count inside vs outside ARC envelope
            // Uses ARC bbox + 10% margin as the reference envelope
            double[] arcBox = discBbox.get("ARC");
            if (arcBox != null) {
                double mx = (arcBox[1] - arcBox[0]) * 0.1; // 10% margin X
                double my = (arcBox[3] - arcBox[2]) * 0.1; // 10% margin Y
                double envMinX = arcBox[0] - mx, envMaxX = arcBox[1] + mx;
                double envMinY = arcBox[2] - my, envMaxY = arcBox[3] + my;

                String sql = String.format(
                    "SELECT em.discipline, " +
                    "SUM(CASE WHEN et.center_x >= %f AND et.center_x <= %f " +
                    "         AND et.center_y >= %f AND et.center_y <= %f THEN 1 ELSE 0 END), " +
                    "SUM(CASE WHEN et.center_x < %f OR et.center_x > %f " +
                    "          OR et.center_y < %f OR et.center_y > %f THEN 1 ELSE 0 END) " +
                    "FROM element_transforms et " +
                    "JOIN elements_meta em ON em.guid = et.guid " +
                    "GROUP BY em.discipline ORDER BY em.discipline",
                    envMinX, envMaxX, envMinY, envMaxY,
                    envMinX, envMaxX, envMinY, envMaxY);
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        discCoherence.put(rs.getString(1), new int[] {
                            rs.getInt(2), rs.getInt(3)
                        });
                    }
                }

                // Outlier detail: per-discipline breakdown by ifc_class + coordinate range
                for (var entry : discCoherence.entrySet()) {
                    if (entry.getValue()[1] == 0) continue;
                    String disc = entry.getKey();
                    List<String> details = new ArrayList<>();

                    // Class breakdown of outliers
                    String detailSql = String.format(
                        "SELECT em.ifc_class, COUNT(*), " +
                        "ROUND(MIN(et.center_x),1), ROUND(MAX(et.center_x),1), " +
                        "ROUND(MIN(et.center_y),1), ROUND(MAX(et.center_y),1), " +
                        "ROUND(MIN(et.center_z),1), ROUND(MAX(et.center_z),1) " +
                        "FROM element_transforms et " +
                        "JOIN elements_meta em ON em.guid = et.guid " +
                        "WHERE em.discipline = '%s' " +
                        "  AND (et.center_x < %f OR et.center_x > %f " +
                        "       OR et.center_y < %f OR et.center_y > %f) " +
                        "GROUP BY em.ifc_class ORDER BY COUNT(*) DESC",
                        disc, envMinX, envMaxX, envMinY, envMaxY);
                    try (ResultSet rs = stmt.executeQuery(detailSql)) {
                        while (rs.next()) {
                            details.add(String.format("%s: %d  X[%.0f..%.0f] Y[%.0f..%.0f] Z[%.0f..%.0f]",
                                rs.getString(1), rs.getInt(2),
                                rs.getDouble(3), rs.getDouble(4),
                                rs.getDouble(5), rs.getDouble(6),
                                rs.getDouble(7), rs.getDouble(8)));
                        }
                    }

                    // Center vs rtree consistency check on outliers
                    String matchSql = String.format(
                        "SELECT COUNT(*), " +
                        "SUM(CASE WHEN ABS(et.center_x - (r.minX+r.maxX)/2.0) > 1.0 " +
                        "          OR ABS(et.center_y - (r.minY+r.maxY)/2.0) > 1.0 THEN 1 ELSE 0 END) " +
                        "FROM element_transforms et " +
                        "JOIN elements_meta em ON em.guid = et.guid " +
                        "JOIN elements_rtree r ON em.id = r.id " +
                        "WHERE em.discipline = '%s' " +
                        "  AND (et.center_x < %f OR et.center_x > %f " +
                        "       OR et.center_y < %f OR et.center_y > %f)",
                        disc, envMinX, envMaxX, envMinY, envMaxY);
                    try (ResultSet rs = stmt.executeQuery(matchSql)) {
                        if (rs.next()) {
                            int total = rs.getInt(1);
                            int mismatch = rs.getInt(2);
                            details.add(String.format("center↔rtree: %d/%d match, %d mismatch",
                                total - mismatch, total, mismatch));
                        }
                    }

                    discOutlierDetail.put(disc, details);
                }
            }
        }
    }

    // =========================================================================
    // Forensic abstract
    // =========================================================================

    private void printAbstract() {
        BIMLogger.info(TAG, "");
        BIMLogger.info(TAG, "========================================================================");
        BIMLogger.info(TAG, "  EXTRACTION POST-PROCESSOR FORENSIC ABSTRACT");
        BIMLogger.info(TAG, "========================================================================");
        BIMLogger.info(TAG, "  DB:          {}", dbPath);
        BIMLogger.info(TAG, "  Elements:    {}", totalElements);
        BIMLogger.info(TAG, "  Unit scale:  {} ({})",
            unitScale, needsScaling() ? "SCALED to metres" : "already metres");

        if (needsScaling()) {
            BIMLogger.info(TAG, "  Vertices scaled:    {}", verticesScaled);
            BIMLogger.info(TAG, "  Transforms scaled:  {}", transformsScaled);
        }

        BIMLogger.info(TAG, "  Discipline upgrades: {} (MEP→sub-discipline)", disciplineUpgrades);

        BIMLogger.info(TAG, "");
        BIMLogger.info(TAG, "  By discipline ({}):", byDiscipline.size());
        for (var e : byDiscipline.entrySet()) {
            BIMLogger.info(TAG, "    {:10s} {:6d}", e.getKey(), e.getValue());
        }

        BIMLogger.info(TAG, "");
        BIMLogger.info(TAG, "  By IFC class ({}):", byClass.size());
        for (var e : byClass.entrySet()) {
            BIMLogger.info(TAG, "    {:40s} {:6d}", e.getKey(), e.getValue());
        }

        BIMLogger.info(TAG, "");
        BIMLogger.info(TAG, "  By storey ({}):", byStorey.size());
        for (var e : byStorey.entrySet()) {
            BIMLogger.info(TAG, "    {:30s} {:6d}", e.getKey(), e.getValue());
        }

        if (worldMin != null) {
            BIMLogger.info(TAG, "");
            BIMLogger.info(TAG, "  World extent (after scale):");
            BIMLogger.info(TAG, "    X: {:.3f} -> {:.3f} m", worldMin[0], worldMax[0]);
            BIMLogger.info(TAG, "    Y: {:.3f} -> {:.3f} m", worldMin[1], worldMax[1]);
            BIMLogger.info(TAG, "    Z: {:.3f} -> {:.3f} m", worldMin[2], worldMax[2]);
        }

        // Coordinate coherence check: compare each discipline bbox against ARC envelope
        if (!discBbox.isEmpty()) {
            BIMLogger.info(TAG, "");
            BIMLogger.info(TAG, "  Coordinate coherence (per-discipline bbox):");
            BIMLogger.info(TAG, "    {:10s} {:>10s} {:>10s} {:>10s} {:>10s} {:>10s} {:>10s}  {}",
                "DISC", "minX", "maxX", "minY", "maxY", "minZ", "maxZ", "VERDICT");

            double[] arcBox = discBbox.get("ARC");
            for (var e : discBbox.entrySet()) {
                double[] bb = e.getValue();
                String verdict = "OK";
                if (arcBox != null && !e.getKey().equals("ARC")) {
                    double margin = Math.max(
                        arcBox[1] - arcBox[0], arcBox[3] - arcBox[2]) * 0.5;
                    boolean xOut = bb[0] < arcBox[0] - margin || bb[1] > arcBox[1] + margin;
                    boolean yOut = bb[2] < arcBox[2] - margin || bb[3] > arcBox[3] + margin;
                    if (xOut || yOut) {
                        verdict = "OUTSIDE_ARC";
                    }
                    // Check for mm-scale (any axis > 500 when ARC is < 500)
                    double arcSpan = Math.max(arcBox[1] - arcBox[0], arcBox[3] - arcBox[2]);
                    double discSpan = Math.max(bb[1] - bb[0], bb[3] - bb[2]);
                    if (arcSpan < 500 && discSpan > arcSpan * 5) {
                        verdict = "SUSPECT_SCALE";
                    }
                }
                BIMLogger.info(TAG, "    {:10s} {:10.1f} {:10.1f} {:10.1f} {:10.1f} {:10.1f} {:10.1f}  {}",
                    e.getKey(), bb[0], bb[1], bb[2], bb[3], bb[4], bb[5], verdict);
            }
        }

        // Per-discipline element outlier counts
        if (!discCoherence.isEmpty()) {
            BIMLogger.info(TAG, "");
            BIMLogger.info(TAG, "  Element coherence vs ARC envelope (+10% margin):");
            BIMLogger.info(TAG, "    {:10s} {:>8s} {:>8s} {:>6s}", "DISC", "INSIDE", "OUTSIDE", "%OUT");
            for (var e : discCoherence.entrySet()) {
                int inside = e.getValue()[0];
                int outside = e.getValue()[1];
                int total = inside + outside;
                double pct = total > 0 ? (outside * 100.0 / total) : 0;
                String flag = pct > 5 ? "  <-- INVESTIGATE" : "";
                BIMLogger.info(TAG, "    {:10s} {:8d} {:8d} {:5.1f}%{}",
                    e.getKey(), inside, outside, pct, flag);
            }
        }

        // Outlier detail per discipline
        if (!discOutlierDetail.isEmpty()) {
            for (var e : discOutlierDetail.entrySet()) {
                BIMLogger.info(TAG, "");
                BIMLogger.info(TAG, "  Outlier detail [{}]:", e.getKey());
                for (String line : e.getValue()) {
                    BIMLogger.info(TAG, "    {}", line);
                }
            }
        }

        BIMLogger.info(TAG, "========================================================================");
    }

    // =========================================================================
    // Public accessors for test verification
    // =========================================================================

    public double getUnitScale()          { return unitScale; }
    public int getTotalElements()         { return totalElements; }
    public int getVerticesScaled()        { return verticesScaled; }
    public int getDisciplineUpgrades()    { return disciplineUpgrades; }
    public Map<String, Integer> getByDiscipline() { return Collections.unmodifiableMap(byDiscipline); }
    public double[] getWorldMin()         { return worldMin; }
    public double[] getWorldMax()         { return worldMax; }

    @Override
    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    // =========================================================================
    // CLI entry point
    // =========================================================================

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: ExtractionPostProcessor <extracted.db>");
            System.exit(1);
        }
        BIMLogger.initForRun("extpp");
        try (ExtractionPostProcessor pp = new ExtractionPostProcessor(args[0])) {
            pp.run();
        }
    }
}
