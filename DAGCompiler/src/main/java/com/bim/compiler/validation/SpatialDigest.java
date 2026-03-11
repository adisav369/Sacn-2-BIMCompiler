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
        // Sort by ROUND(mm) to absorb sub-mm float noise between extraction and compilation.
        String sql = """
            SELECT em.ifc_class,
                   r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ,
                   COALESCE(em.material_rgba, ''),
                   COALESCE(ei.geometry_hash, '')
            FROM elements_meta em
            JOIN elements_rtree r ON em.id = r.id
            LEFT JOIN element_instances ei ON ei.guid = em.guid
            ORDER BY em.ifc_class,
                     ROUND(r.minX * 1000), ROUND(r.minY * 1000), ROUND(r.minZ * 1000),
                     ROUND(r.maxX * 1000), ROUND(r.maxY * 1000), ROUND(r.maxZ * 1000)
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
     * Compute digest and print summary statistics (full mode with geometry_hash).
     *
     * @param dbPath Path to output SQLite DB
     * @return DigestReport with hash, element count, and class breakdown
     */
    public static DigestReport computeWithReport(String dbPath) {
        return computeWithReport(dbPath, true);
    }

    /**
     * Compute digest with optional geometry_hash inclusion.
     *
     * <p><b>Cross-mode comparison:</b> when comparing extraction (reference DB) against
     * compilation (output DB), geometry_hash MUST be excluded ({@code includeGeoHash=false}).
     * Extraction uses IFC mesh hashes; compilation uses LOD library mesh hashes.
     * They serve the same geometry but produce different hash strings by design.
     * Coordinates + material_rgba are sufficient for cross-mode spatial proof.
     *
     * <p><b>Same-pipeline regression:</b> include geometry_hash ({@code includeGeoHash=true})
     * to detect mesh substitution within the same compilation pipeline.
     *
     * @param dbPath         Path to output SQLite DB
     * @param includeGeoHash true for same-pipeline regression, false for cross-mode comparison
     * @return DigestReport with hash, element count, and class breakdown
     */
    public static DigestReport computeWithReport(String dbPath, boolean includeGeoHash) {
        String sql;
        if (includeGeoHash) {
            sql = """
                SELECT em.ifc_class,
                       r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ,
                       COALESCE(em.material_rgba, ''),
                       COALESCE(ei.geometry_hash, '')
                FROM elements_meta em
                JOIN elements_rtree r ON em.id = r.id
                LEFT JOIN element_instances ei ON ei.guid = em.guid
                ORDER BY em.ifc_class,
                     ROUND(r.minX * 1000), ROUND(r.minY * 1000), ROUND(r.minZ * 1000),
                     ROUND(r.maxX * 1000), ROUND(r.maxY * 1000), ROUND(r.maxZ * 1000)
                """;
        } else {
            sql = """
                SELECT em.ifc_class,
                       r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ,
                       COALESCE(em.material_rgba, '')
                FROM elements_meta em
                JOIN elements_rtree r ON em.id = r.id
                ORDER BY em.ifc_class,
                     ROUND(r.minX * 1000), ROUND(r.minY * 1000), ROUND(r.minZ * 1000),
                     ROUND(r.maxX * 1000), ROUND(r.maxY * 1000), ROUND(r.maxZ * 1000)
                """;
        }

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

                String coords;
                if (includeGeoHash) {
                    coords = String.format("%d|%d|%d|%d|%d|%d|%s|%s",
                        Math.round(rs.getDouble(2) * 1000),
                        Math.round(rs.getDouble(3) * 1000),
                        Math.round(rs.getDouble(4) * 1000),
                        Math.round(rs.getDouble(5) * 1000),
                        Math.round(rs.getDouble(6) * 1000),
                        Math.round(rs.getDouble(7) * 1000),
                        rs.getString(8),
                        rs.getString(9));
                } else {
                    coords = String.format("%d|%d|%d|%d|%d|%d|%s",
                        Math.round(rs.getDouble(2) * 1000),
                        Math.round(rs.getDouble(3) * 1000),
                        Math.round(rs.getDouble(4) * 1000),
                        Math.round(rs.getDouble(5) * 1000),
                        Math.round(rs.getDouble(6) * 1000),
                        Math.round(rs.getDouble(7) * 1000),
                        rs.getString(8));
                }

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
    // BOM-based digest (sole source after P0.2 — PlacementLoader reads BOM.db)
    // =========================================================================

    /**
     * Compute digest from m_bom_line (BOM.db), reconstructing AABB from centroid + allocated dims.
     *
     * <p>After the precision migration, {@code dx - allocated_width_mm/2000.0 == min_x} exactly.
     * Uses the same CLASS=X COUNT=N + coord line format as {@link #compute(String)}.
     *
     * <p>P0.2: material_rgba now read from m_bom_line (backfilled from IFC extraction archive).
     * computeFromPlacement() deleted — BOM is the sole source for EXTRACTED buildings.
     *
     * @param bomConn Connection to BOM.db
     * @param bomId   BOM identifier (e.g. "EXT_SH", "EXT_DX")
     * @return DigestReport with hash, element count, and class breakdown
     */
    public static DigestReport computeFromBOM(Connection bomConn, String bomId) {
        String sql = """
            SELECT role as ifc_class,
                   (dx - allocated_width_mm / 2000.0) as min_x,
                   (dx + allocated_width_mm / 2000.0) as max_x,
                   (dy - allocated_depth_mm / 2000.0) as min_y,
                   (dy + allocated_depth_mm / 2000.0) as max_y,
                   (dz - allocated_height_mm / 2000.0) as min_z,
                   (dz + allocated_height_mm / 2000.0) as max_z,
                   COALESCE(material_rgba, '') as material_rgba,
                   '' as geometry_hash
            FROM m_bom_line
            WHERE bom_id = ? AND is_active = 1
            ORDER BY role,
                     round((dx - allocated_width_mm / 2000.0) * 1000),
                     round((dy - allocated_depth_mm / 2000.0) * 1000),
                     round((dz - allocated_height_mm / 2000.0) * 1000)
            """;

        return computeFromResultSet(bomConn, sql, bomId, "BOM(" + bomId + ")");
    }

    /**
     * Shared digest computation from a parameterised query returning the standard
     * 9-column result set (ifc_class, min_x, max_x, min_y, max_y, min_z, max_z,
     * material_rgba, geometry_hash).
     */
    private static DigestReport computeFromResultSet(Connection conn, String sql,
                                                     String param, String label) {
        int elementCount = 0;
        java.util.Map<String, Integer> classCounts = new java.util.TreeMap<>();
        List<String> lines = new ArrayList<>();
        String currentClass = null;
        int classCount = 0;
        List<String> classCoords = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
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
            }
            if (currentClass != null) {
                lines.add("CLASS=" + currentClass + " COUNT=" + classCount);
                lines.addAll(classCoords);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("SpatialDigest failed on " + label + ": " + ex.getMessage(), ex);
        }

        String digest = sha256(String.join("\n", lines));
        return new DigestReport(digest, elementCount, classCounts);
    }

    /**
     * Compute digest by walking a BUILDING BOM tree — collects all BUY leaf
     * lines across all MAKE children, reconstructing world coordinates by
     * adding parent MAKE offsets to child BUY centroids.
     *
     * <p>For EXTRACTED buildings, the tree is 2 levels: BUILDING → FLOOR_STR → BUY.
     * Each MAKE line carries the floor-to-building offset (dx, dy, dz).
     * Each BUY line carries the element centroid relative to its floor.
     * World centroid = MAKE_offset + BUY_centroid.
     *
     * @param bomConn       Connection to BOM.db
     * @param buildingBomId Root BUILDING BOM id (e.g. "BUILDING_SH_STD")
     * @return DigestReport with hash, element count, and class breakdown
     */
    public static DigestReport computeFromBOMTree(Connection bomConn, String buildingBomId) {
        // Collect all BUY leaf lines with world coordinates reconstructed
        // from parent MAKE offsets + child centroid
        String sql = """
            SELECT child.role as ifc_class,
                   (parent.dx + child.dx - child.allocated_width_mm / 2000.0) as min_x,
                   (parent.dx + child.dx + child.allocated_width_mm / 2000.0) as max_x,
                   (parent.dy + child.dy - child.allocated_depth_mm / 2000.0) as min_y,
                   (parent.dy + child.dy + child.allocated_depth_mm / 2000.0) as max_y,
                   (parent.dz + child.dz - child.allocated_height_mm / 2000.0) as min_z,
                   (parent.dz + child.dz + child.allocated_height_mm / 2000.0) as max_z,
                   COALESCE(child.material_rgba, '') as material_rgba,
                   '' as geometry_hash
            FROM m_bom_line parent
            JOIN m_bom_line child ON child.bom_id = parent.child_product_id
            WHERE parent.bom_id = ?
              AND parent.component_type = 'MAKE' AND parent.is_active = 1
              AND child.component_type = 'BUY'  AND child.is_active = 1
            ORDER BY child.role,
                     round((parent.dx + child.dx - child.allocated_width_mm / 2000.0) * 1000),
                     round((parent.dy + child.dy - child.allocated_depth_mm / 2000.0) * 1000),
                     round((parent.dz + child.dz - child.allocated_height_mm / 2000.0) * 1000)
            """;

        return computeFromResultSet(bomConn, sql, buildingBomId, "BOMTree(" + buildingBomId + ")");
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
