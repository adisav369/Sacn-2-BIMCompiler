package com.bim.compiler.validation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Phase 118: Deterministic spatial fingerprint of a compiled building.
 *
 * <p>Computes SHA256 hash of all element bounding boxes, sorted deterministically.
 * Two birds with one stone:
 * <ol>
 *   <li><b>Sizing verification</b> — encodes every element's bbox. Any dimension
 *       change (wall thickness, room size, furniture shift) changes the hash.</li>
 *   <li><b>Regression testing</b> — the hash is the "golden master". Commit the
 *       known-good digest. Any code change that moves geometry = test failure.</li>
 * </ol>
 *
 * <p>Coordinates are rounded to 1mm precision to absorb floating-point noise
 * while catching any real geometric change.
 *
 * <h3>Usage in E2E tests:</h3>
 * <pre>
 * String digest = SpatialDigest.compute("output/condo_mid.db");
 * assertEquals(expectedDigest, digest, "Spatial regression!");
 * </pre>
 */
public class SpatialDigest {

    /**
     * Compute SHA256 digest of all elements in an output DB.
     *
     * @param dbPath Path to output SQLite DB
     * @return 64-char hex SHA256 digest
     */
    public static String compute(String dbPath) {
        List<String> lines = new ArrayList<>();

        String sql = """
            SELECT em.ifc_class, em.element_name,
                   r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            ORDER BY em.ifc_class, em.element_name, r.minX, r.minY, r.minZ
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                // Round to 1mm precision (integer mm) to absorb float noise
                String line = String.format("%s|%s|%d|%d|%d|%d|%d|%d",
                    rs.getString("ifc_class"),
                    rs.getString("element_name"),
                    Math.round(rs.getDouble("minX") * 1000),
                    Math.round(rs.getDouble("maxX") * 1000),
                    Math.round(rs.getDouble("minY") * 1000),
                    Math.round(rs.getDouble("maxY") * 1000),
                    Math.round(rs.getDouble("minZ") * 1000),
                    Math.round(rs.getDouble("maxZ") * 1000));
                lines.add(line);
            }

        } catch (SQLException ex) {
            throw new RuntimeException("SpatialDigest failed on " + dbPath + ": " + ex.getMessage(), ex);
        }

        // Deterministic sort (SQL ORDER BY should be sufficient, but belt-and-suspenders)
        Collections.sort(lines);

        // SHA256
        String payload = String.join("\n", lines);
        return sha256(payload);
    }

    /**
     * Compute digest and print summary statistics.
     *
     * @param dbPath Path to output SQLite DB
     * @return DigestReport with hash, element count, and class breakdown
     */
    public static DigestReport computeWithReport(String dbPath) {
        List<String> lines = new ArrayList<>();
        int elementCount = 0;
        java.util.Map<String, Integer> classCounts = new java.util.TreeMap<>();

        String sql = """
            SELECT em.ifc_class, em.element_name,
                   r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            ORDER BY em.ifc_class, em.element_name, r.minX, r.minY, r.minZ
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                elementCount++;
                String ifcClass = rs.getString("ifc_class");
                classCounts.merge(ifcClass, 1, Integer::sum);

                String line = String.format("%s|%s|%d|%d|%d|%d|%d|%d",
                    ifcClass,
                    rs.getString("element_name"),
                    Math.round(rs.getDouble("minX") * 1000),
                    Math.round(rs.getDouble("maxX") * 1000),
                    Math.round(rs.getDouble("minY") * 1000),
                    Math.round(rs.getDouble("maxY") * 1000),
                    Math.round(rs.getDouble("minZ") * 1000),
                    Math.round(rs.getDouble("maxZ") * 1000));
                lines.add(line);
            }

        } catch (SQLException ex) {
            throw new RuntimeException("SpatialDigest failed: " + ex.getMessage(), ex);
        }

        Collections.sort(lines);
        String digest = sha256(String.join("\n", lines));

        return new DigestReport(digest, elementCount, classCounts);
    }

    /**
     * Compare two DBs and report whether they are spatially identical.
     *
     * @return true if both DBs produce the same spatial digest
     */
    public static boolean matches(String dbPathA, String dbPathB) {
        return compute(dbPathA).equals(compute(dbPathB));
    }

    /**
     * CO_EmptySpaceLine checksum — deterministic hash of the single level-0
     * acceptance line (owner-matched BOM → building AABB).
     *
     * <p>Hashes: bom_id | bom_line_role | bom_level | before(x,y,z) | next(x,y,z) | capacity
     * — all rounded to 1mm (integer mm).
     *
     * <p>When C_Order.c_bpartner == M_BOM.c_bpartner, the BOM is one intact
     * construct. This single line captures its spatial acceptance. If the
     * checksum changes, the BOM construct produced a different spatial
     * decomposition — fault is in BOM.db data, not Java code.
     *
     * @param dbPath Path to output SQLite DB with co_empty_space_line table
     * @return 16-char hex prefix of SHA256, or null if no level-0 line exists
     */
    public static String computeEmptySpaceChecksum(String dbPath) {
        List<String> lines = new ArrayList<>();

        // Level-0 only: the single owner-matched acceptance line
        String sql = """
            SELECT bom_line_seq, bom_id, bom_line_role, bom_level,
                   before_x_mm, before_y_mm, before_z_mm,
                   next_x_mm, next_y_mm, next_z_mm,
                   capacity_mm
            FROM co_empty_space_line
            WHERE bom_level = 0
            ORDER BY bom_line_seq
            """;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                String line = String.format("%s|%s|%d|%d|%d|%d|%d|%d|%d|%d",
                    rs.getString("bom_id"),
                    rs.getString("bom_line_role"),
                    rs.getInt("bom_level"),
                    Math.round(rs.getDouble("before_x_mm")),
                    Math.round(rs.getDouble("before_y_mm")),
                    Math.round(rs.getDouble("before_z_mm")),
                    Math.round(rs.getDouble("next_x_mm")),
                    Math.round(rs.getDouble("next_y_mm")),
                    Math.round(rs.getDouble("next_z_mm")),
                    Math.round(rs.getDouble("capacity_mm")));
                lines.add(line);
            }

        } catch (SQLException ex) {
            throw new RuntimeException("EmptySpaceChecksum failed on " + dbPath + ": " + ex.getMessage(), ex);
        }

        if (lines.isEmpty()) return null;
        return sha256(String.join("\n", lines)).substring(0, 16);
    }

    // =========================================================================
    // Records
    // =========================================================================

    public record DigestReport(
        String digest,
        int elementCount,
        java.util.Map<String, Integer> classCounts
    ) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("SpatialDigest: %s%n", digest));
            sb.append(String.format("Elements: %d%n", elementCount));
            sb.append("Classes:\n");
            for (var entry : classCounts.entrySet()) {
                sb.append(String.format("  %-30s %d%n", entry.getKey(), entry.getValue()));
            }
            return sb.toString();
        }
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("SHA-256 not available", ex);
        }
    }
}
