package com.bim.layout;

import com.bim.orm.BIMLogger;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * Reads an edited DXF floor plan and computes position deltas per element GUID.
 * Writes deltas back to M_BOM_Line.dx / M_BOM_Line.dy in the intent DB (_BOM.db).
 *
 * ARC elements only: IfcWall, IfcWallStandardCase, IfcDoor, IfcWindow, IfcSlab, IfcPlate.
 * MEP elements are read-only (RouteWalker generates them from rules, not M_BOM_Line offsets).
 *
 * Implements unified_mathematical_formulation.txt §2 Stage P (inverse):
 *   Δ_new = R(θ)⁻¹ · (T_new − T_parent)
 *
 * Witness: W-2D-ARC-ROUNDTRIP
 * Spec: 2D_007 §Task 2
 */
public class DXFSyncVerb {

    private static final String TAG = "DXFSyncVerb";

    /** Noise threshold: skip deltas smaller than 1mm in both axes. */
    private static final double THRESHOLD_M = 0.001;

    /** ARC ifc_classes eligible for roundtrip sync. */
    private static final Set<String> ARC_CLASSES = Set.of(
        "IfcWall", "IfcWallStandardCase", "IfcSlab",
        "IfcDoor", "IfcWindow", "IfcPlate"
    );

    /** Result of a sync operation. */
    public record SyncResult(int updated, List<String> guids) {}

    /**
     * @param editedDxf   path to the DXF that was edited by the architect
     * @param originalDb  path to the compiled output.db (to read original centroids)
     * @param bomDb       path to the intent DB (_BOM.db) — receives the Δ updates
     * @param pythonExe   path to python3 executable (default: "python3")
     * @return SyncResult: count of elements updated, list of GUIDs changed
     */
    public SyncResult sync(Path editedDxf, Path originalDb, Path bomDb,
                           String pythonExe) throws Exception {

        // Step 1: Read edited positions from DXF via Python helper
        Map<String, double[]> editedPositions = readDxfPositions(editedDxf, pythonExe);
        BIMLogger.info(TAG, "DXF positions read: {} GUIDs", editedPositions.size());

        if (editedPositions.isEmpty()) {
            return new SyncResult(0, List.of());
        }

        // Step 2: Read original centroids from output.db
        Map<String, OriginalElement> originals = readOriginalCentroids(originalDb);
        BIMLogger.info(TAG, "Original centroids: {} ARC elements", originals.size());

        // Step 3: Compute deltas per GUID
        Map<String, double[]> deltas = new LinkedHashMap<>();
        for (var entry : editedPositions.entrySet()) {
            String guid = entry.getKey();
            double[] edited = entry.getValue();
            OriginalElement orig = originals.get(guid);
            if (orig == null) {
                BIMLogger.warn(TAG, "GUID {} not found in output.db — skipping", guid);
                continue;
            }
            if (!ARC_CLASSES.contains(orig.ifcClass)) {
                continue; // MEP or non-ARC — skip
            }
            double dx = edited[0] - orig.cx;
            double dy = edited[1] - orig.cy;
            if (Math.abs(dx) < THRESHOLD_M && Math.abs(dy) < THRESHOLD_M) {
                continue; // noise — no meaningful change
            }
            deltas.put(guid, new double[]{dx, dy});
            BIMLogger.info(TAG, "SYNC {} dx={} dy={}", guid,
                String.format("%.3f", dx), String.format("%.3f", dy));
        }

        if (deltas.isEmpty()) {
            BIMLogger.info(TAG, "No meaningful position changes detected");
            return new SyncResult(0, List.of());
        }

        // Step 4: Write deltas to M_BOM_Line in bomDb
        int updated = writeDeltasToBom(deltas, originals, bomDb);

        return new SyncResult(updated, new ArrayList<>(deltas.keySet()));
    }

    /** Convenience overload with default python3 path. */
    public SyncResult sync(Path editedDxf, Path originalDb, Path bomDb) throws Exception {
        return sync(editedDxf, originalDb, bomDb, "python3");
    }

    // ── Step 1: call Python helper ──────────────────────────────────────────

    private Map<String, double[]> readDxfPositions(Path dxfPath, String pythonExe)
            throws Exception {
        // Locate dxf_read_positions.py relative to this class's source layout
        Path scriptDir = findPythonDir();
        Path script = scriptDir.resolve("dxf_read_positions.py");
        if (!Files.exists(script)) {
            throw new FileNotFoundException("dxf_read_positions.py not found at " + script);
        }

        ProcessBuilder pb = new ProcessBuilder(
            pythonExe, script.toString(), dxfPath.toAbsolutePath().toString());
        pb.redirectErrorStream(false);
        Process proc = pb.start();

        String stdout;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(proc.getInputStream()))) {
            stdout = br.lines().collect(java.util.stream.Collectors.joining("\n"));
        }
        String stderr;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(proc.getErrorStream()))) {
            stderr = br.lines().collect(java.util.stream.Collectors.joining("\n"));
        }
        int rc = proc.waitFor();
        if (rc != 0) {
            throw new RuntimeException("dxf_read_positions.py failed (rc=" + rc + "): " + stderr);
        }

        return parsePositionJson(stdout.trim());
    }

    /**
     * Locate the python/ dir: try 2D_Layout/python/ from CWD, then relative to classpath.
     */
    private Path findPythonDir() {
        // Try relative to CWD (normal run from project root)
        Path candidate = Path.of("2D_Layout/python");
        if (Files.isDirectory(candidate)) return candidate;
        // Try from 2D_Layout/ as CWD
        candidate = Path.of("python");
        if (Files.isDirectory(candidate)) return candidate;
        // Fallback: up from compiled classes
        candidate = Path.of(System.getProperty("user.dir"), "2D_Layout", "python");
        return candidate;
    }

    /**
     * Parse JSON {"guid": [cx, cy], ...} without external JSON library.
     * The format is simple enough for manual parsing.
     */
    static Map<String, double[]> parsePositionJson(String json) {
        Map<String, double[]> result = new LinkedHashMap<>();
        if (json == null || json.isBlank() || json.equals("{}")) return result;

        // Strip outer braces
        String inner = json.strip();
        if (inner.startsWith("{")) inner = inner.substring(1);
        if (inner.endsWith("}")) inner = inner.substring(0, inner.length() - 1);

        // Split by "], " pattern to get entries
        // Each entry: "guid": [x, y]
        int i = 0;
        while (i < inner.length()) {
            // Find key
            int q1 = inner.indexOf('"', i);
            if (q1 < 0) break;
            int q2 = inner.indexOf('"', q1 + 1);
            if (q2 < 0) break;
            String key = inner.substring(q1 + 1, q2);

            // Find [x, y]
            int bOpen = inner.indexOf('[', q2);
            if (bOpen < 0) break;
            int bClose = inner.indexOf(']', bOpen);
            if (bClose < 0) break;
            String coords = inner.substring(bOpen + 1, bClose).strip();
            String[] parts = coords.split(",");
            if (parts.length >= 2) {
                double cx = Double.parseDouble(parts[0].strip());
                double cy = Double.parseDouble(parts[1].strip());
                result.put(key, new double[]{cx, cy});
            }
            i = bClose + 1;
        }
        return result;
    }

    // ── Step 2: read original centroids from output.db ──────────────────────

    private record OriginalElement(
        String guid, String ifcClass, String elementName,
        double cx, double cy
    ) {}

    private Map<String, OriginalElement> readOriginalCentroids(Path dbPath)
            throws SQLException {
        Map<String, OriginalElement> map = new LinkedHashMap<>();
        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        try (Connection conn = DriverManager.getConnection(url)) {
            String sql = """
                SELECT m.guid, m.ifc_class, m.element_name,
                       (r.minX + r.maxX) / 2.0, (r.minY + r.maxY) / 2.0
                FROM elements_meta m
                JOIN elements_rtree r ON m.id = r.id
                """;
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String guid = rs.getString(1);
                    String ifcClass = rs.getString(2);
                    String elemName = rs.getString(3);
                    double cx = rs.getDouble(4);
                    double cy = rs.getDouble(5);
                    map.put(guid, new OriginalElement(guid, ifcClass, elemName, cx, cy));
                }
            }
        }
        return map;
    }

    // ── Step 4: write deltas to M_BOM_Line ──────────────────────────────────

    /**
     * Match each changed GUID to a M_BOM_Line row by element_ref + closest dx/dy,
     * then apply the position delta.
     *
     * The BOM doesn't store GUIDs directly. We match via:
     *   elements_meta.element_name → m_bom_line.element_ref (same string)
     *   elements_meta centroid → closest m_bom_line.dx/dy (parent-relative offset)
     */
    private int writeDeltasToBom(Map<String, double[]> deltas,
                                 Map<String, OriginalElement> originals,
                                 Path bomPath) throws SQLException {
        String url = "jdbc:sqlite:" + bomPath.toAbsolutePath();
        int count = 0;
        try (Connection conn = DriverManager.getConnection(url)) {
            conn.setAutoCommit(false);

            // Load all BOM lines indexed by element_ref
            Map<String, List<BomLineRow>> bomByRef = loadBomLines(conn);

            String updateSql = "UPDATE m_bom_line SET dx = dx + ?, dy = dy + ? "
                             + "WHERE M_BOM_Line_ID = ?";
            try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                for (var entry : deltas.entrySet()) {
                    String guid = entry.getKey();
                    double[] delta = entry.getValue();
                    OriginalElement orig = originals.get(guid);
                    if (orig == null) continue;

                    // Find matching BOM line by element_ref + closest position
                    List<BomLineRow> candidates = bomByRef.get(orig.elementName);
                    if (candidates == null || candidates.isEmpty()) {
                        BIMLogger.warn(TAG,
                            "No BOM line for element_ref='{}' — skipping GUID {}",
                            orig.elementName, guid);
                        continue;
                    }

                    BomLineRow best = findClosestBomLine(candidates, orig.cx, orig.cy);
                    upd.setDouble(1, delta[0]);
                    upd.setDouble(2, delta[1]);
                    upd.setInt(3, best.id);
                    upd.executeUpdate();
                    count++;

                    BIMLogger.info(TAG, "BOM_Line #{} dx+={} dy+={}",
                        best.id,
                        String.format("%.3f", delta[0]),
                        String.format("%.3f", delta[1]));
                }
            }
            conn.commit();
        }
        return count;
    }

    private record BomLineRow(int id, String elementRef, double dx, double dy) {}

    private Map<String, List<BomLineRow>> loadBomLines(Connection conn)
            throws SQLException {
        Map<String, List<BomLineRow>> map = new LinkedHashMap<>();
        String sql = "SELECT M_BOM_Line_ID, element_ref, dx, dy FROM m_bom_line";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                BomLineRow row = new BomLineRow(
                    rs.getInt(1), rs.getString(2),
                    rs.getDouble(3), rs.getDouble(4));
                map.computeIfAbsent(row.elementRef, k -> new ArrayList<>()).add(row);
            }
        }
        return map;
    }

    /**
     * Among BOM lines with the same element_ref, find the one whose dx/dy
     * is closest to the original element centroid. This is how we resolve
     * instance identity without a GUID column in the BOM.
     */
    private BomLineRow findClosestBomLine(List<BomLineRow> candidates,
                                          double origCx, double origCy) {
        BomLineRow best = candidates.get(0);
        double bestDist = Double.MAX_VALUE;
        for (BomLineRow row : candidates) {
            double dist = Math.hypot(row.dx - origCx, row.dy - origCy);
            if (dist < bestDist) {
                bestDist = dist;
                best = row;
            }
        }
        return best;
    }
}
