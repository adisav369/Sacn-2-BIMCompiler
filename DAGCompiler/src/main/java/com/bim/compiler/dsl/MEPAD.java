package com.bim.compiler.dsl;

import com.bim.compiler.topology.Discipline;

import java.sql.*;
import java.util.*;

/**
 * MEP Application Dictionary - Metadata-driven MEP element lookup.
 *
 * Queries ad_element_mep, ad_ref_list, ad_fp_coverage tables.
 * All MEP behavior comes from database - no hardcoded element types.
 *
 * Usage:
 *   ElementMEP outlet = MEPAD.getElement("OUTLET");
 *   double coverage = MEPAD.getSprinklerCoverage("LIGHT");
 *   List<RefValue> systems = MEPAD.getRefValues("MEP_SYSTEM");
 */
public class MEPAD {

    // Phase 2b: reads from ERP.db (discipline metadata),
    // not bom.db (per-building) or component_library.db (LOD/geometry)
    private static final String DB_PATH = CompilerConfig.ERP_DB_PATH;
    private static Connection connection = null;
    private static boolean connectionFailed = false;

    // Caches
    private static final Map<String, ElementMEP> elementCache = new HashMap<>();
    private static final Map<String, List<RefValue>> refCache = new HashMap<>();
    private static final Map<String, FPCoverage> coverageCache = new HashMap<>();

    // =========================================================================
    // Public Records (DTOs)
    // =========================================================================

    /**
     * MEP Element definition from AD.
     */
    // Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 Step 5-6 — Witness: W-DV-DISC-ORG
    public record ElementMEP(
        String elementType,
        String ifcClass,
        Discipline discipline,
        String mepSystem,
        String hostType,
        String distRole,
        String circuitType,
        Double mountHeight,
        Map<String, Double> clearance,  // front, side
        List<Port> ports,
        Map<String, Object> properties,
        String codeRef
    ) {
        public record Port(String id, Double size) {}
    }

    /**
     * Reference value from AD.
     */
    public record RefValue(
        String refId,
        String valueId,
        String valueName,
        int seqNo,
        String colorCode,
        String codeRef,
        Map<String, Object> extra
    ) {}

    /**
     * Fire protection coverage rules.
     */
    public record FPCoverage(
        String hazardClass,
        double maxCoverageM2,
        double maxSpacingM,
        double minSpacingM,
        double wallDistanceM,
        double kFactor,
        String codeRef
    ) {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Get MEP element definition by type.
     */
    public static ElementMEP getElement(String elementType) {
        if (elementType == null) return null;
        String key = elementType.toUpperCase();

        if (elementCache.containsKey(key)) {
            return elementCache.get(key);
        }

        try {
            Connection conn = getConnection();
            if (conn == null) return null;

            String sql = """
                SELECT Value, ifc_class, discipline, mep_system, host_type,
                       dist_role, circuit_type, mount_height, clearance, ports, properties, code_ref
                FROM ad_element_mep WHERE Value = ? AND is_active = 1
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, key);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    ElementMEP elem = parseElementMEP(rs);
                    elementCache.put(key, elem);
                    return elem;
                }
            }
        } catch (SQLException e) {
            System.err.println("[MEPAD] Element lookup error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get all elements for a discipline.
     */
    public static List<ElementMEP> getElementsByDiscipline(Discipline discipline) {
        List<ElementMEP> result = new ArrayList<>();
        try {
            Connection conn = getConnection();
            if (conn == null) return result;

            String sql = "SELECT * FROM ad_element_mep WHERE discipline = ? AND is_active = 1";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, discipline.name());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    result.add(parseElementMEP(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[MEPAD] getElementsByDiscipline error: " + e.getMessage());
        }
        return result;
    }

    /**
     * Get reference values for a reference type.
     */
    public static List<RefValue> getRefValues(String refId) {
        if (refId == null) return List.of();
        String key = refId.toUpperCase();

        if (refCache.containsKey(key)) {
            return refCache.get(key);
        }

        List<RefValue> result = new ArrayList<>();
        try {
            Connection conn = getConnection();
            if (conn == null) return result;

            String sql = """
                SELECT ref_id, value_id, value_name, seq_no, color_code, code_reference, extra_json
                FROM ad_ref_list WHERE ref_id = ? AND is_active = 1 ORDER BY seq_no
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, key);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    result.add(new RefValue(
                        rs.getString("ref_id"),
                        rs.getString("value_id"),
                        rs.getString("value_name"),
                        rs.getInt("seq_no"),
                        rs.getString("color_code"),
                        rs.getString("code_reference"),
                        parseJson(rs.getString("extra_json"))
                    ));
                }
            }

            refCache.put(key, result);
        } catch (SQLException e) {
            System.err.println("[MEPAD] getRefValues error: " + e.getMessage());
        }
        return result;
    }

    /**
     * Get single reference value.
     */
    public static RefValue getRefValue(String refId, String valueId) {
        return getRefValues(refId).stream()
            .filter(v -> v.valueId().equals(valueId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get sprinkler coverage rules by hazard class.
     */
    public static FPCoverage getSprinklerCoverage(String hazardClass) {
        if (hazardClass == null) return null;
        String key = hazardClass.toUpperCase();

        if (coverageCache.containsKey(key)) {
            return coverageCache.get(key);
        }

        try {
            Connection conn = getConnection();
            if (conn == null) return null;

            String sql = """
                SELECT Value, max_coverage_m2, max_spacing_m, min_spacing_m,
                       wall_distance_m, k_factor, code_ref
                FROM ad_fp_coverage WHERE Value = ? AND is_active = 1
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, key);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    FPCoverage cov = new FPCoverage(
                        rs.getString("Value"),
                        rs.getDouble("max_coverage_m2"),
                        rs.getDouble("max_spacing_m"),
                        rs.getDouble("min_spacing_m"),
                        rs.getDouble("wall_distance_m"),
                        rs.getDouble("k_factor"),
                        rs.getString("code_ref")
                    );
                    coverageCache.put(key, cov);
                    return cov;
                }
            }
        } catch (SQLException e) {
            System.err.println("[MEPAD] getSprinklerCoverage error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Check if MEP AD tables are available.
     */
    public static boolean isAvailable() {
        try {
            Connection conn = getConnection();
            if (conn == null) return false;

            String sql = "SELECT COUNT(*) FROM ad_element_mep";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Get element count.
     */
    public static int getElementCount() {
        try {
            Connection conn = getConnection();
            if (conn == null) return 0;

            String sql = "SELECT COUNT(*) FROM ad_element_mep WHERE is_active = 1";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            // ignore
        }
        return 0;
    }

    public static void clearCache() {
        elementCache.clear();
        refCache.clear();
        coverageCache.clear();
    }

    public static void close() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException closeEx) { /* best-effort cleanup */ }
            connection = null;
        }
        connectionFailed = false;
        clearCache();
    }

    // =========================================================================
    // Private Implementation
    // =========================================================================

    private static synchronized Connection getConnection() throws SQLException {
        if (connectionFailed) return null;

        if (connection != null) {
            try {
                if (!connection.isClosed()) return connection;
            } catch (SQLException e) {
                connection = null;
            }
        }

        try {
            java.io.File dbFile = new java.io.File(DB_PATH);
            if (!dbFile.exists()) {
                connectionFailed = true;
                return null;
            }

            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);

            // Verify tables exist
            String sql = "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name LIKE 'ad_%'";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (!rs.next() || rs.getInt(1) < 3) {
                    connection.close();
                    connection = null;
                    connectionFailed = true;
                    return null;
                }
            }

            return connection;
        } catch (SQLException e) {
            connectionFailed = true;
            return null;
        }
    }

    private static ElementMEP parseElementMEP(ResultSet rs) throws SQLException {
        return new ElementMEP(
            rs.getString("Value"),
            rs.getString("ifc_class"),
            Discipline.fromString(rs.getString("discipline")),
            rs.getString("mep_system"),
            rs.getString("host_type"),
            rs.getString("dist_role"),
            rs.getString("circuit_type"),
            rs.getObject("mount_height") != null ? rs.getDouble("mount_height") : null,
            parseClearance(rs.getString("clearance")),
            parsePorts(rs.getString("ports")),
            parseJson(rs.getString("properties")),
            rs.getString("code_ref")
        );
    }

    private static Map<String, Double> parseClearance(String json) {
        Map<String, Double> result = new HashMap<>();
        if (json == null || json.isEmpty()) return result;

        // Simple JSON parse: {"front":0.5,"side":0.3}
        json = json.replace("{", "").replace("}", "").replace("\"", "");
        for (String pair : json.split(",")) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                try {
                    result.put(kv[0].trim(), Double.parseDouble(kv[1].trim()));
                } catch (NumberFormatException parseEx) { /* skip malformed numeric pair */ }
            }
        }
        return result;
    }

    private static List<ElementMEP.Port> parsePorts(String json) {
        List<ElementMEP.Port> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return result;

        // Simple JSON parse: [{"id":"IN","size":0.015}]
        json = json.replace("[", "").replace("]", "");
        for (String obj : json.split("\\},")) {
            obj = obj.replace("{", "").replace("}", "").replace("\"", "");
            String id = null;
            Double size = null;
            for (String pair : obj.split(",")) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    String k = kv[0].trim();
                    String v = kv[1].trim();
                    if ("id".equals(k)) id = v;
                    else if ("size".equals(k) && !v.equals("null")) {
                        try { size = Double.parseDouble(v); } catch (NumberFormatException parseEx) { /* keep default size */ }
                    }
                }
            }
            if (id != null) result.add(new ElementMEP.Port(id, size));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.isEmpty()) return result;

        // Simple JSON parse for flat objects
        json = json.replace("{", "").replace("}", "").replace("\"", "");
        for (String pair : json.split(",")) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                String k = kv[0].trim();
                String v = kv[1].trim();
                // Try to parse as number or boolean
                if ("true".equals(v)) result.put(k, true);
                else if ("false".equals(v)) result.put(k, false);
                else if ("null".equals(v)) result.put(k, null);
                else {
                    try { result.put(k, Double.parseDouble(v)); }
                    catch (NumberFormatException e) { result.put(k, v); }
                }
            }
        }
        return result;
    }

    // =========================================================================
    // Test Main
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=== MEPAD Test ===\n");

        if (!isAvailable()) {
            System.out.println("MEP AD tables not available");
            System.out.println("Run: python scripts/create_ad_mep_schema.py");
            return;
        }

        System.out.println("MEP AD: " + getElementCount() + " elements\n");

        // Test elements
        String[] types = {"OUTLET", "SPRINKLER", "TOILET", "LIGHT"};
        for (String type : types) {
            ElementMEP e = getElement(type);
            if (e != null) {
                System.out.printf("%s: %s, %s, host=%s, role=%s%n",
                    e.elementType(), e.ifcClass(), e.discipline(), e.hostType(), e.distRole());
            }
        }

        System.out.println("\n--- Reference Values ---");
        for (RefValue v : getRefValues("MEP_SYSTEM")) {
            System.out.printf("  %s: %s (%s)%n", v.valueId(), v.valueName(), v.colorCode());
        }

        System.out.println("\n--- FP Coverage ---");
        FPCoverage cov = getSprinklerCoverage("LIGHT");
        if (cov != null) {
            System.out.printf("LIGHT: max %.1f m², spacing %.1f-%.1f m%n",
                cov.maxCoverageM2(), cov.minSpacingM(), cov.maxSpacingM());
        }

        close();
    }
}
