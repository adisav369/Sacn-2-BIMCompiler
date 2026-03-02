package com.bim.compiler.validation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic spatial fingerprint of a compiled building.
 *
 * <p>Computes SHA256 hash of all element bounding boxes across every IFC class
 * (walls, slabs, roof, doors, windows, furniture, MEP — everything in elements_rtree).
 *
 * <h3>Formula (name-agnostic, count-verified):</h3>
 * <pre>
 *   For each ifc_class (alphabetical):
 *     "CLASS={ifc_class} COUNT={n}"          — count enforced per class
 *     For each element in class (minX, minY, minZ order):
 *       "{minX_mm}|{maxX_mm}|{minY_mm}|{maxY_mm}|{minZ_mm}|{maxZ_mm}|{material_rgba}|{geometry_hash}"
 *   sha256(all lines joined by "\n")
 * </pre>
 *
 * <p><b>Why no element_name in the hash:</b> compiled element names differ from
 * extracted IFC names (e.g. "IfcWall_NS_001" vs "Basic Wall:Interior - 135 Partition").
 * Including names would make EXTRACTED ≠ GENERATIVE even for identical geometry.
 * Excluding names lets the digest serve as a cross-mode truth test.
 *
 * <p><b>Why COUNT per class:</b> adding or removing any element changes its class
 * COUNT, which changes the hash — even if all remaining bbox values are unchanged.
 * This is the "sum of counts" invariant from COBOL-style batch verification.
 *
 * <p><b>Why material_rgba:</b> elements with identical geometry but different materials
 * (e.g. painted vs unpainted wall) must produce different digests for visual fidelity.
 * NULL material_rgba is COALESCEd to empty string for deterministic hashing.
 *
 * <p><b>Why geometry_hash:</b> prevents element substitution — two elements with same
 * ifc_class and AABB but different mesh geometry (LOD_ library components) produce
 * different digests. JOINs to {@code element_instances} via guid.
 *
 * <p>Coordinates are rounded to 1 mm precision to absorb floating-point noise
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
        // SQL: all classes alphabetical; within each class, elements by coordinate position.
        // element_name deliberately excluded — names differ between EXTRACTED and GENERATIVE.
        String sql = """
            SELECT em.ifc_class,
                   r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ,
                   COALESCE(em.material_rgba, ''),
                   COALESCE(ei.geometry_hash, '')
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            LEFT JOIN element_instances ei ON ei.guid = em.guid
            ORDER BY em.ifc_class, r.minX, r.minY, r.minZ
            """;

        List<String> lines = new ArrayList<>();
        String currentClass = null;
        int classCount = 0;
        List<String> classCoords = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                String ifcClass = rs.getString(1);
                String coords = String.format("%d|%d|%d|%d|%d|%d|%s|%s",
                    Math.round(rs.getDouble(2) * 1000),
                    Math.round(rs.getDouble(3) * 1000),
                    Math.round(rs.getDouble(4) * 1000),
                    Math.round(rs.getDouble(5) * 1000),
                    Math.round(rs.getDouble(6) * 1000),
                    Math.round(rs.getDouble(7) * 1000),
                    rs.getString(8),
                    rs.getString(9));

                if (!ifcClass.equals(currentClass)) {
                    if (currentClass != null) {
                        lines.add("CLASS=" + currentClass + " COUNT=" + classCount);
                        lines.addAll(classCoords);
                    }
                    currentClass = ifcClass;
                    classCount = 0;
                    classCoords = new ArrayList<>();
                }
                classCount++;
                classCoords.add(coords);
            }
            // flush last class
            if (currentClass != null) {
                lines.add("CLASS=" + currentClass + " COUNT=" + classCount);
                lines.addAll(classCoords);
            }

        } catch (SQLException ex) {
            throw new RuntimeException("SpatialDigest failed on " + dbPath + ": " + ex.getMessage(), ex);
        }

        return sha256(String.join("\n", lines));
    }

    /**
     * Compute digest and print summary statistics.
     *
     * @param dbPath Path to output SQLite DB
     * @return DigestReport with hash, element count, and class breakdown
     */
    public static DigestReport computeWithReport(String dbPath) {
        String sql = """
            SELECT em.ifc_class,
                   r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ,
                   COALESCE(em.material_rgba, ''),
                   COALESCE(ei.geometry_hash, '')
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            LEFT JOIN element_instances ei ON ei.guid = em.guid
            ORDER BY em.ifc_class, r.minX, r.minY, r.minZ
            """;

        int elementCount = 0;
        java.util.Map<String, Integer> classCounts = new java.util.TreeMap<>();
        List<String> lines = new ArrayList<>();
        String currentClass = null;
        int classCount = 0;
        List<String> classCoords = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                String ifcClass = rs.getString(1);
                elementCount++;
                classCounts.merge(ifcClass, 1, Integer::sum);

                String coords = String.format("%d|%d|%d|%d|%d|%d|%s|%s",
                    Math.round(rs.getDouble(2) * 1000),
                    Math.round(rs.getDouble(3) * 1000),
                    Math.round(rs.getDouble(4) * 1000),
                    Math.round(rs.getDouble(5) * 1000),
                    Math.round(rs.getDouble(6) * 1000),
                    Math.round(rs.getDouble(7) * 1000),
                    rs.getString(8),
                    rs.getString(9));

                if (!ifcClass.equals(currentClass)) {
                    if (currentClass != null) {
                        lines.add("CLASS=" + currentClass + " COUNT=" + classCount);
                        lines.addAll(classCoords);
                    }
                    currentClass = ifcClass;
                    classCount = 0;
                    classCoords = new ArrayList<>();
                }
                classCount++;
                classCoords.add(coords);
            }
            if (currentClass != null) {
                lines.add("CLASS=" + currentClass + " COUNT=" + classCount);
                lines.addAll(classCoords);
            }

        } catch (SQLException ex) {
            throw new RuntimeException("SpatialDigest failed: " + ex.getMessage(), ex);
        }

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
