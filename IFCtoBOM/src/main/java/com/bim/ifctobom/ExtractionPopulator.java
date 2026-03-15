package com.bim.ifctobom;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Populates {@code I_Element_Extraction} in component_library.db from a
 * Rosetta Stone reference DB ({@code *_extracted.db}).
 *
 * <h3>DETERMINISTIC — no invention</h3>
 * <p>Every value written comes from the reference DB or is derived by
 * a deterministic function of that data. No human-crafted IDs, no manual
 * migration SQL. The only human-crafted artifact is the classification YAML.
 *
 * <p>Replaces:
 * <ul>
 *   <li>{@code tools/placement_extractor.py} — Python extraction (now Java)</li>
 *   <li>{@code migration/migration_P02_SH_product_link.sql} — manual M_Product_ID
 *       mapping (now automatic: M_Product_ID = element_ref)</li>
 * </ul>
 *
 * <h3>Data chain</h3>
 * <pre>
 *   reference DB (elements_meta + elements_rtree)
 *     → I_Element_Extraction (component_library.db)
 *       → M_Product_ID = element_ref  (deterministic — no invention)
 *         → child_product_id on BOM LEAF lines
 *           → BOMWalker resolves geometry at compile time
 * </pre>
 *
 * <p>Convention: reference DB path = {@code DAGCompiler/lib/input/{building_type}_extracted.db}
 */
public class ExtractionPopulator {

    /**
     * Populate I_Element_Extraction from reference DB for a building type.
     * Deletes existing rows for this building type first (idempotent).
     *
     * @param compConn     writable connection to component_library.db
     * @param buildingType building type identifier (e.g., "Ifc2x3_Duplex")
     * @return number of rows inserted
     */
    public static int populate(Connection compConn, String buildingType) throws SQLException {
        Path refDb = Path.of("DAGCompiler/lib/input", buildingType + "_extracted.db");
        if (!Files.exists(refDb)) {
            System.err.printf("  [ExtractionPopulator] Reference DB not found: %s — skipping extraction%n", refDb);
            return 0;
        }
        return populate(compConn, buildingType, refDb);
    }

    /**
     * Populate I_Element_Extraction from a specific reference DB.
     */
    public static int populate(Connection compConn, String buildingType, Path refDb)
            throws SQLException {

        // Read elements from reference DB
        List<RawElement> rawElements = readReferenceDb(refDb);
        if (rawElements.isEmpty()) {
            System.err.printf("  [ExtractionPopulator] No elements in %s%n", refDb);
            return 0;
        }

        // Derive element_ref (strip instance ID suffix)
        // Group by (ifc_class, storey), assign ordinals by position
        // Set M_Product_ID = element_ref (deterministic)
        List<ExtractionRow> rows = deriveRows(rawElements, buildingType);

        // Ensure I_Element_Extraction table exists
        ensureExtractionTable(compConn);

        // Delete existing and insert fresh (idempotent)
        deleteExisting(compConn, buildingType);
        int inserted = insertRows(compConn, rows);

        // Deactivate REBAR elements (deferred to IfcOpenShell Python)
        deactivateRebar(compConn, buildingType);

        // Fill geometry gaps: import missing geometries from reference DB
        // into component_geometries + I_Geometry_Map for element_refs that have
        // no geometry in the library. This ensures every product can resolve geometry.
        int geoInserted = fillGeometryGaps(compConn, rows, buildingType, refDb);

        System.out.printf("  [ExtractionPopulator] %s: %d elements → %d distinct products, %d geometry gaps filled%n",
                buildingType, inserted,
                rows.stream().map(r -> r.mProductId).distinct().count(),
                geoInserted);

        return inserted;
    }

    // ── Internal types ──────────────────────────────────────────────────────

    private record RawElement(
            String ifcClass, String elementName, String storey, String discipline,
            double minX, double maxX, double minY, double maxY, double minZ, double maxZ
    ) {}

    private record ExtractionRow(
            String buildingType, String storey, String ifcClass,
            String elementRef, int ordinal,
            double minX, double maxX, double minY, double maxY, double minZ, double maxZ,
            String orientation, String discipline, String source,
            String mProductId
    ) {}

    // ── Read reference DB ───────────────────────────────────────────────────

    private static List<RawElement> readReferenceDb(Path refDb) throws SQLException {
        List<RawElement> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + refDb)) {
            String sql = """
                    SELECT m.ifc_class, m.element_name, m.storey, m.discipline,
                           r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
                    FROM elements_meta m
                    JOIN elements_rtree r ON m.id = r.id
                    ORDER BY m.ifc_class, m.storey, r.minX, r.minY, r.minZ
                    """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    result.add(new RawElement(
                            rs.getString(1), rs.getString(2),
                            rs.getString(3), rs.getString(4),
                            rs.getDouble(5), rs.getDouble(6),
                            rs.getDouble(7), rs.getDouble(8),
                            rs.getDouble(9), rs.getDouble(10)
                    ));
                }
            }
        }
        return result;
    }

    // ── Derive extraction rows ──────────────────────────────────────────────

    private static List<ExtractionRow> deriveRows(List<RawElement> raw, String buildingType) {
        // Group by (ifc_class, storey) for ordinal assignment
        Map<String, List<RawElement>> groups = new LinkedHashMap<>();
        for (RawElement e : raw) {
            String storey = e.storey() != null ? e.storey() : "Unknown";
            // TE: federated model has NULL storeys — resolve by Z-centroid band
            if ("SJTII_Terminal".equals(buildingType) && "Unknown".equals(storey)) {
                storey = resolveStoreyByZBand(e);
            }
            String key = e.ifcClass() + "|" + storey;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }

        List<ExtractionRow> result = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            String ifcClass = parts[0];
            String storey = parts[1];
            List<RawElement> elems = entry.getValue();

            // Already sorted by position (ORDER BY in SQL)
            int ordinal = 1;
            for (RawElement e : elems) {
                String elementRef = deriveElementRef(e.elementName(), e.ifcClass());
                String orientation = classifyOrientation(e, ifcClass);
                String discipline = e.discipline() != null ? e.discipline() : "ARC";
                // M_Product_ID = element_ref — deterministic, no invention
                String mProductId = elementRef;

                result.add(new ExtractionRow(
                        buildingType, storey, ifcClass, elementRef, ordinal,
                        e.minX(), e.maxX(), e.minY(), e.maxY(), e.minZ(), e.maxZ(),
                        orientation, discipline,
                        "[EXTRACTED: " + buildingType + "]",
                        mProductId
                ));
                ordinal++;
            }
        }
        return result;
    }

    /**
     * Derive element_ref from IFC element name.
     * Strips the trailing instance ID (e.g., ":285330") and normalises.
     * Mirrors the logic from placement_extractor.py._derive_element_ref().
     */
    static String deriveElementRef(String name, String ifcClass) {
        if (name == null || name.isBlank()) return ifcClass;

        String[] parts = name.split(":");
        if (parts.length >= 2) {
            String last = parts[parts.length - 1].strip();
            // Strip if last segment is purely numeric (instance ID)
            if (!last.isEmpty() && last.chars().allMatch(Character::isDigit)) {
                parts = Arrays.copyOf(parts, parts.length - 1);
            }
        }
        String ref = String.join(":", parts).strip();

        // Truncate very long names
        if (ref.length() > 100) {
            ref = ref.substring(0, 97) + "...";
        }
        return ref.isEmpty() ? ifcClass : ref;
    }

    /**
     * Classify wall orientation from bounding box dimensions.
     * Returns "EW", "NS", "POINT", or null for non-wall elements.
     */
    private static String classifyOrientation(RawElement e, String ifcClass) {
        if (!"IfcWall".equals(ifcClass) && !"IfcPlate".equals(ifcClass)
                && !"IfcWallStandardCase".equals(ifcClass)) {
            return null;
        }
        double dx = e.maxX() - e.minX();
        double dy = e.maxY() - e.minY();
        if (dx > dy * 3) return "EW";
        if (dy > dx * 3) return "NS";
        return "POINT";
    }

    /**
     * Resolve storey from Z-centroid for buildings with missing storey data.
     * Thresholds from TE_001_storey_normalisation.sql.
     */
    private static String resolveStoreyByZBand(RawElement e) {
        double zCentroid = (e.minZ() + e.maxZ()) / 2.0;
        if (zCentroid < 0.0)  return "Foundation";
        if (zCentroid < 4.5)  return "Ground Floor";
        if (zCentroid < 8.0)  return "Level 1";
        if (zCentroid < 11.5) return "Level 2";
        if (zCentroid < 15.0) return "Level 3";
        if (zCentroid < 18.0) return "Level 4";
        return "Roof";
    }

    /**
     * Deactivate REBAR elements post-insert.
     * REBAR is deferred to IfcOpenShell Python — not processed in Java pipeline.
     */
    private static void deactivateRebar(Connection conn, String buildingType) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE I_Element_Extraction SET is_active = 0 " +
                "WHERE building_type = ? AND discipline = 'REB'")) {
            stmt.setString(1, buildingType);
            int count = stmt.executeUpdate();
            if (count > 0) {
                System.out.printf("  [ExtractionPopulator] Deactivated %d REBAR elements for %s%n",
                        count, buildingType);
            }
        }
    }

    // ── Geometry map ─────────────────────────────────────────────────────────

    /**
     * Fill geometry gaps: for element_refs in extraction that have no geometry
     * in I_Geometry_Map, import the actual geometry from the reference DB.
     *
     * <p>This imports both the mesh blob (into component_geometries) and the
     * element→geometry link (into I_Geometry_Map). Deterministic — geometry
     * comes from the reference DB, not invented.
     */
    private static int fillGeometryGaps(Connection compConn, List<ExtractionRow> rows,
                                         String buildingType, Path refDb)
            throws SQLException {
        // Find element_refs that exist in extraction but NOT in I_Geometry_Map
        Set<String> existingRefs = new HashSet<>();
        try (PreparedStatement stmt = compConn.prepareStatement(
                "SELECT DISTINCT element_ref FROM I_Geometry_Map WHERE building_type = ?")) {
            stmt.setString(1, buildingType);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) existingRefs.add(rs.getString(1));
            }
        }

        // Find missing refs and their ifc_class
        Map<String, String> missingRefToClass = new LinkedHashMap<>();
        for (ExtractionRow r : rows) {
            if (!existingRefs.contains(r.elementRef)) {
                missingRefToClass.putIfAbsent(r.elementRef, r.ifcClass);
            }
        }
        if (missingRefToClass.isEmpty()) return 0;

        // Read geometry from reference DB for missing element_refs
        // Map: element_ref → geometry_hash (first instance)
        Map<String, String> refToGeoHash = new LinkedHashMap<>();
        Set<String> neededHashes = new HashSet<>();

        try (Connection refConn = DriverManager.getConnection("jdbc:sqlite:" + refDb)) {
            String sql = """
                    SELECT m.element_name, m.ifc_class, ei.geometry_hash
                    FROM elements_meta m
                    JOIN element_instances ei ON m.guid = ei.guid
                    WHERE ei.geometry_hash IS NOT NULL
                    """;
            try (Statement stmt = refConn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    String cls = rs.getString(2);
                    String geoHash = rs.getString(3);
                    String ref = deriveElementRef(name, cls);
                    if (missingRefToClass.containsKey(ref)) {
                        refToGeoHash.putIfAbsent(ref, geoHash);
                        neededHashes.add(geoHash);
                    }
                }
            }

            // Import missing geometry blobs into component_geometries
            if (!neededHashes.isEmpty()) {
                // Find which hashes already exist
                Set<String> existingHashes = new HashSet<>();
                try (Statement stmt = compConn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT geometry_hash FROM component_geometries")) {
                    while (rs.next()) existingHashes.add(rs.getString(1));
                }

                Set<String> toImport = new HashSet<>(neededHashes);
                toImport.removeAll(existingHashes);

                if (!toImport.isEmpty()) {
                    String selectSql = "SELECT geometry_hash, vertices, faces, vertex_count, face_count "
                            + "FROM base_geometries WHERE geometry_hash = ?";
                    String insertSql = """
                            INSERT OR IGNORE INTO component_geometries
                            (geometry_hash, vertices, faces, normals, vertex_count, face_count)
                            VALUES (?, ?, ?, NULL, ?, ?)
                            """;
                    try (PreparedStatement selStmt = refConn.prepareStatement(selectSql);
                         PreparedStatement insStmt = compConn.prepareStatement(insertSql)) {
                        for (String hash : toImport) {
                            selStmt.setString(1, hash);
                            try (ResultSet rs = selStmt.executeQuery()) {
                                if (rs.next()) {
                                    insStmt.setString(1, rs.getString(1));
                                    insStmt.setBytes(2, rs.getBytes(2));
                                    insStmt.setBytes(3, rs.getBytes(3));
                                    insStmt.setInt(4, rs.getInt(4));
                                    insStmt.setInt(5, rs.getInt(5));
                                    insStmt.executeUpdate();
                                }
                            }
                        }
                    }
                    System.out.printf("  [ExtractionPopulator] Imported %d geometry blobs from reference DB%n",
                            toImport.size());
                }
            }
        }

        // Insert I_Geometry_Map entries for missing refs
        String insertGeo = """
                INSERT OR IGNORE INTO I_Geometry_Map
                (building_type, element_ref, ifc_class, geometry_hash, source, provenance)
                VALUES (?, ?, ?, ?, ?, 'EXTRACTED')
                """;
        int count = 0;
        try (PreparedStatement stmt = compConn.prepareStatement(insertGeo)) {
            for (var entry : missingRefToClass.entrySet()) {
                String geoHash = refToGeoHash.get(entry.getKey());
                if (geoHash == null) {
                    System.err.printf("  [ExtractionPopulator] No geometry in reference DB for %s (%s)%n",
                            entry.getKey(), entry.getValue());
                    continue;
                }
                stmt.setString(1, buildingType);
                stmt.setString(2, entry.getKey());
                stmt.setString(3, entry.getValue());
                stmt.setString(4, geoHash);
                stmt.setString(5, "[EXTRACTED: " + buildingType + "]");
                stmt.executeUpdate();
                count++;
            }
        }
        if (count > 0) {
            System.out.printf("  [ExtractionPopulator] Filled %d I_Geometry_Map gaps from reference DB%n", count);
        }
        return count;
    }

    // ── SQL operations ──────────────────────────────────────────────────────

    private static void ensureExtractionTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS I_Element_Extraction (
                        placement_id  INTEGER PRIMARY KEY AUTOINCREMENT,
                        building_type TEXT NOT NULL,
                        storey        TEXT NOT NULL,
                        ifc_class     TEXT NOT NULL,
                        element_ref   TEXT NOT NULL,
                        ordinal       INTEGER DEFAULT 1,
                        min_x         REAL NOT NULL,
                        max_x         REAL NOT NULL,
                        min_y         REAL NOT NULL,
                        max_y         REAL NOT NULL,
                        min_z         REAL NOT NULL,
                        max_z         REAL NOT NULL,
                        orientation   TEXT,
                        discipline    TEXT DEFAULT 'ARC',
                        source        TEXT,
                        is_active     INTEGER DEFAULT 1,
                        material_name TEXT,
                        material_rgba TEXT,
                        M_Product_ID  TEXT,
                        UNIQUE(building_type, storey, ifc_class, ordinal)
                    )
                    """);
        }
    }

    private static void deleteExisting(Connection conn, String buildingType) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM I_Element_Extraction WHERE building_type = ?")) {
            stmt.setString(1, buildingType);
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                System.out.printf("  [ExtractionPopulator] Deleted %d existing rows for %s%n",
                        deleted, buildingType);
            }
        }
    }

    private static int insertRows(Connection conn, List<ExtractionRow> rows) throws SQLException {
        String sql = """
                INSERT INTO I_Element_Extraction
                (building_type, storey, ifc_class, element_ref, ordinal,
                 min_x, max_x, min_y, max_y, min_z, max_z,
                 orientation, discipline, source, is_active, M_Product_ID)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                """;
        int count = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (ExtractionRow r : rows) {
                stmt.setString(1, r.buildingType);
                stmt.setString(2, r.storey);
                stmt.setString(3, r.ifcClass);
                stmt.setString(4, r.elementRef);
                stmt.setInt(5, r.ordinal);
                stmt.setDouble(6, r.minX);
                stmt.setDouble(7, r.maxX);
                stmt.setDouble(8, r.minY);
                stmt.setDouble(9, r.maxY);
                stmt.setDouble(10, r.minZ);
                stmt.setDouble(11, r.maxZ);
                stmt.setString(12, r.orientation);
                stmt.setString(13, r.discipline);
                stmt.setString(14, r.source);
                stmt.setString(15, r.mProductId);
                stmt.executeUpdate();
                count++;
            }
        }
        return count;
    }
}
