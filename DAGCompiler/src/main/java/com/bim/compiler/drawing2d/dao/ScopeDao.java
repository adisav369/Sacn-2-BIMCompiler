package com.bim.compiler.drawing2d.dao;

import com.bim.compiler.drawing2d.model.DrawingScope;
import com.bim.compiler.drawing2d.model.DrawingScope.StoreyInfo;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/**
 * Queries a compiled building DB to detect the drawing scope.
 *
 * Three concerns:
 * 1. Habitable storeys (exclude Roof, T/FDN, Foundation, Unknown)
 * 2. MEP disciplines present (PLMB, ELEC, HVAC via IFC class patterns)
 * 3. Mesh and roof availability (for section cuts and roof plans)
 *
 * Every query result is logged with § prefix for forensic traceability.
 * Implementing 2D_ARCHITECTURAL_LAYOUT.md §2 Step 1-2.
 */
public class ScopeDao {

    /** Storeys excluded from habitable floor plans. */
    private static final Set<String> EXCLUDED_STOREYS = Set.of(
        "Roof", "T/FDN", "Foundation", "Unknown"
    );

    /** IFC classes that indicate habitable storey occupancy. */
    private static final String STOREY_SQL = """
        SELECT m.storey, MIN(r.minZ) AS floor_z, COUNT(*) AS n
        FROM elements_meta m
        JOIN elements_rtree r ON m.id = r.id
        WHERE m.ifc_class IN ('IfcWall', 'IfcDoor', 'IfcWindow')
          AND m.storey IS NOT NULL
        GROUP BY m.storey
        ORDER BY floor_z
        """;

    /** IFC class patterns that map to MEP disciplines. */
    private static final Map<String, String> MEP_CLASS_TO_DISC = Map.of(
        "IfcPipeSegment",      "PLMB",
        "IfcPipeFitting",      "PLMB",
        "IfcFlowTerminal",     "PLMB",
        "IfcFlowSegment",      "PLMB",
        "IfcFlowFitting",      "PLMB",
        "IfcFlowController",   "PLMB",
        "IfcCableSegment",     "ELEC",
        "IfcSwitchingDevice",  "ELEC",
        "IfcElectricAppliance","ELEC",
        "IfcDuctSegment",      "HVAC"
    );

    /** Query distinct IFC classes that may indicate MEP disciplines. */
    private static final String MEP_SQL = """
        SELECT DISTINCT ifc_class FROM elements_meta
        WHERE ifc_class IN (%s)
        """;

    /** Check if any triangle meshes exist. */
    private static final String MESH_SQL =
        "SELECT COUNT(*) FROM base_geometries WHERE vertices IS NOT NULL";

    /**
     * Check for roof-level elements: IfcRoof anywhere, or IfcSlab on a
     * non-habitable upper storey. Both patterns occur in real IFC files.
     */
    private static final String ROOF_SQL = """
        SELECT COUNT(*) FROM elements_meta
        WHERE ifc_class = 'IfcRoof'
           OR (ifc_class = 'IfcSlab' AND storey IN ('Roof', 'Unknown'))
           OR (ifc_class = 'IfcSlab' AND storey NOT IN ('T/FDN', 'Foundation'))
        """;

    private final Path dbPath;

    public ScopeDao(Path dbPath) {
        this.dbPath = Objects.requireNonNull(dbPath);
    }

    /**
     * Detect the full drawing scope from the building DB.
     *
     * @param log  callback for forensic log lines (§SCOPE prefix added by caller)
     * @return detected scope
     */
    public DrawingScope detect(LogCallback log) throws SQLException {
        String url = "jdbc:sqlite:" + dbPath;
        try (Connection conn = DriverManager.getConnection(url)) {

            // 1. Habitable storeys
            List<StoreyInfo> storeys = detectStoreys(conn, log);

            // 2. MEP disciplines
            Set<String> disciplines = detectDisciplines(conn, log);

            // 3. Mesh availability
            boolean hasMesh = queryCount(conn, MESH_SQL) > 0;
            log.log("mesh=" + hasMesh);

            // 4. Roof elements
            boolean hasRoof = queryCount(conn, ROOF_SQL) > 0;
            log.log("roof_elements=" + hasRoof);

            DrawingScope scope = new DrawingScope(storeys, disciplines, hasMesh, hasRoof);
            log.log("storeys=" + scope.storeyCount()
                + " disciplines=" + disciplines
                + " mesh=" + hasMesh
                + " roof=" + hasRoof);
            return scope;
        }
    }

    private List<StoreyInfo> detectStoreys(Connection conn, LogCallback log)
            throws SQLException {
        List<StoreyInfo> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(STOREY_SQL);
             ResultSet rs = ps.executeQuery()) {
            int ordinal = 0;
            while (rs.next()) {
                String name = rs.getString("storey");
                double floorZ = rs.getDouble("floor_z");
                int count = rs.getInt("n");

                if (EXCLUDED_STOREYS.contains(name)) {
                    log.log("storey_skip=" + name + " (excluded)");
                    continue;
                }

                String code = storeyCode(ordinal);
                result.add(new StoreyInfo(name, floorZ, code));
                log.log("storey=" + name + " floor_z=" + floorZ
                    + " elements=" + count + " code=" + code);
                ordinal++;
            }
        }
        return List.copyOf(result);
    }

    private Set<String> detectDisciplines(Connection conn, LogCallback log)
            throws SQLException {
        Set<String> disciplines = new LinkedHashSet<>();
        // Always ARC if we have storeys
        disciplines.add("ARC");

        // Check for MEP classes
        String placeholders = String.join(",",
            MEP_CLASS_TO_DISC.keySet().stream()
                .map(k -> "'" + k + "'").toList());
        String sql = String.format(MEP_SQL, placeholders);

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String ifcClass = rs.getString(1);
                String disc = MEP_CLASS_TO_DISC.get(ifcClass);
                if (disc != null) {
                    disciplines.add(disc);
                    log.log("mep_class=" + ifcClass + " → " + disc);
                }
            }
        }

        // Also check the discipline column directly
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT discipline FROM elements_meta WHERE discipline IS NOT NULL");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String disc = rs.getString(1);
                if ("MEP".equals(disc)) {
                    // Generic MEP — check if we already have a specific discipline
                    if (!disciplines.contains("PLMB") && !disciplines.contains("ELEC")
                            && !disciplines.contains("HVAC")) {
                        disciplines.add("PLMB");  // default MEP → PLMB
                        log.log("discipline=MEP → default PLMB");
                    }
                } else if (!"ARC".equals(disc)) {
                    disciplines.add(disc);
                }
            }
        }

        return Set.copyOf(disciplines);
    }

    private int queryCount(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Map storey ordinal (0-based, sorted by floor Z) to a short code.
     * 0 → GF, 1 → FF, 2 → 2F, 3 → 3F, etc.
     */
    static String storeyCode(int ordinal) {
        return switch (ordinal) {
            case 0 -> "GF";
            case 1 -> "FF";
            default -> ordinal + "F";
        };
    }

    /** Callback for forensic log lines. */
    @FunctionalInterface
    public interface LogCallback {
        void log(String message);
    }
}
