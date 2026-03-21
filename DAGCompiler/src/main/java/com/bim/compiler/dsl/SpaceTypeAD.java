package com.bim.compiler.dsl;

import com.bim.compiler.dsl.SpaceTypeRegistry.*;
import com.bim.ormsandbox.po.M_AdSpaceType;

import java.sql.*;
import java.util.*;

/**
 * Application Dictionary Space Type Lookup.
 *
 * Phase 1 of Metadata-Driven Architecture:
 * Queries ad_space_type* tables in component_library.db following
 * the iDempiere AD pattern.
 *
 * Falls back gracefully if database unavailable.
 *
 * Usage:
 *   SpaceTypeConfig config = SpaceTypeAD.get("BEDROOM");
 *   String canonical = SpaceTypeAD.resolveAlias("BT"); // Returns "BEDROOM"
 */
public class SpaceTypeAD {

    private static String dbPath() { return System.getProperty("bom.db"); }
    private static Connection connection = null;
    private static boolean connectionFailed = false;

    // Cache for performance
    private static final Map<String, SpaceTypeConfig> cache = new HashMap<>();
    private static final Map<String, String> aliasCache = new HashMap<>();

    /**
     * Get space type configuration by name from AD database.
     *
     * @param typeName The space type name (e.g., "BEDROOM", "BT")
     * @return SpaceTypeConfig or null if not found
     */
    public static SpaceTypeConfig get(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return null;
        }

        String normalized = typeName.toUpperCase().trim();

        // Check cache first
        if (cache.containsKey(normalized)) {
            return cache.get(normalized);
        }

        // Resolve alias if needed
        String canonical = resolveAlias(normalized);
        if (canonical != null && cache.containsKey(canonical)) {
            return cache.get(canonical);
        }

        // Try database lookup
        try {
            Connection conn = getConnection();
            if (conn == null) {
                return null;
            }

            String lookupId = canonical != null ? canonical : normalized;
            SpaceTypeConfig config = querySpaceType(conn, lookupId);

            if (config != null) {
                cache.put(lookupId, config);
                if (canonical != null && !canonical.equals(normalized)) {
                    cache.put(normalized, config);
                }
            }

            return config;

        } catch (SQLException e) {
            System.err.println("[SpaceTypeAD] Database error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Resolve an alias to its canonical space type ID.
     *
     * @param alias The alias to resolve (e.g., "BT", "MR")
     * @return Canonical space type ID or null if not an alias
     */
    public static String resolveAlias(String alias) {
        if (alias == null || alias.isEmpty()) {
            return null;
        }

        String normalized = alias.toUpperCase().trim();

        // Check cache first
        if (aliasCache.containsKey(normalized)) {
            return aliasCache.get(normalized);
        }

        try {
            Connection conn = getConnection();
            if (conn == null) {
                return null;
            }

            String canonical = M_AdSpaceType.resolveAlias(conn, normalized);
            if (canonical != null) {
                aliasCache.put(normalized, canonical);
                return canonical;
            }

        } catch (SQLException e) {
            System.err.println("[SpaceTypeAD] Alias lookup error: " + e.getMessage());
        }

        return null;
    }

    /**
     * Check if AD tables are available.
     */
    public static boolean isAvailable() {
        try {
            Connection conn = getConnection();
            if (conn == null) {
                return false;
            }

            return !M_AdSpaceType.listActive(conn).isEmpty();

        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Get count of space types in AD.
     */
    public static int getSpaceTypeCount() {
        try {
            Connection conn = getConnection();
            if (conn == null) {
                return 0;
            }

            return M_AdSpaceType.listActive(conn).size();

        } catch (SQLException e) {
            System.err.println("[SpaceTypeAD] Count error: " + e.getMessage());
        }

        return 0;
    }

    /**
     * Clear caches (for testing or config reload).
     */
    public static void clearCache() {
        cache.clear();
        aliasCache.clear();
    }

    /**
     * Close database connection.
     */
    public static void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException closeEx) {
                // best-effort cleanup
            }
            connection = null;
        }
        connectionFailed = false;
        clearCache();
    }

    // =========================================================================
    // Private Implementation
    // =========================================================================

    /**
     * Get database connection with lazy initialization.
     */
    private static synchronized Connection getConnection() throws SQLException {
        if (connectionFailed) {
            return null;
        }

        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    return connection;
                }
            } catch (SQLException e) {
                // Connection is invalid, reset
                connection = null;
            }
        }

        // Try to establish connection
        try {
            // Check if database file exists
            java.io.File dbFile = new java.io.File(dbPath());
            if (!dbFile.exists()) {
                System.err.println("[SpaceTypeAD] Database not found: " + dbPath());
                connectionFailed = true;
                return null;
            }

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath());

            // Verify AD tables exist
            if (!verifyAdTables(connection)) {
                System.err.println("[SpaceTypeAD] AD tables not found in database");
                connection.close();
                connection = null;
                connectionFailed = true;
                return null;
            }

            return connection;

        } catch (SQLException e) {
            System.err.println("[SpaceTypeAD] Connection failed: " + e.getMessage());
            connectionFailed = true;
            return null;
        }
    }

    /**
     * Verify AD tables exist in database.
     */
    private static boolean verifyAdTables(Connection conn) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM sqlite_master
            WHERE type='table' AND name IN ('ad_space_type', 'ad_space_type_alias', 'ad_space_type_mep')
            """;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() && rs.getInt(1) == 3;
        }
    }

    /**
     * Query space type from database.
     */
    private static SpaceTypeConfig querySpaceType(Connection conn, String typeId) throws SQLException {
        String sql = """
            SELECT
                s.space_type_id, s.category, s.omniclass_code, s.wall_rule,
                s.is_sleeping_room, s.is_open_plan, s.is_exterior,
                s.min_area, s.min_dimension, s.requires_window, s.requires_egress,
                s.structural_grid, s.beam_max_span,
                m.plumbing_fixtures, m.requires_stack, m.requires_vent, m.water_supply,
                m.light_points, m.power_points, m.switch_points,
                m.requires_dedicated, m.circuit_type,
                m.requires_exhaust, m.exhaust_cfm, m.allows_aircon, m.ventilation_type
            FROM ad_space_type s
            LEFT JOIN ad_space_type_mep m ON s.space_type_id = m.space_type_id
            WHERE s.space_type_id = ? AND s.is_active = 1
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, typeId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return parseSpaceTypeConfig(rs, typeId);
            }
        }

        return null;
    }

    /**
     * Parse ResultSet into SpaceTypeConfig.
     */
    private static SpaceTypeConfig parseSpaceTypeConfig(ResultSet rs, String typeId) throws SQLException {
        // Parse validation config
        ValidationConfig validation = new ValidationConfig(
            rs.getDouble("min_area"),
            rs.getDouble("min_dimension"),
            rs.getInt("requires_window") == 1,
            rs.getInt("requires_egress") == 1
        );

        // Parse MEP config
        MEPConfig mep = parseMEPConfig(rs);

        // Parse structural config
        StructuralConfig structural = new StructuralConfig(
            rs.getInt("structural_grid") == 1,
            rs.getDouble("beam_max_span")
        );

        // Get aliases for this type
        List<String> aliases = getAliasesForType(typeId);

        return new SpaceTypeConfig(
            rs.getString("space_type_id"),
            rs.getString("category"),
            rs.getString("omniclass_code"),
            rs.getString("wall_rule"),
            validation,
            mep,
            structural,
            rs.getInt("is_sleeping_room") == 1,
            rs.getInt("is_open_plan") == 1,
            rs.getInt("is_exterior") == 1,
            null,  // zonesAllowed - not in AD yet
            aliases
        );
    }

    /**
     * Parse MEP configuration from ResultSet.
     */
    private static MEPConfig parseMEPConfig(ResultSet rs) throws SQLException {
        // Parse plumbing fixtures from JSON
        String fixturesJson = rs.getString("plumbing_fixtures");
        List<String> fixtures = parseFixturesJson(fixturesJson);

        PlumbingConfig plumbing = new PlumbingConfig(
            fixtures,
            rs.getInt("requires_stack") == 1,
            rs.getInt("requires_vent") == 1,
            rs.getString("water_supply") != null ? rs.getString("water_supply") : "none"
        );

        ElectricalConfig electrical = new ElectricalConfig(
            rs.getInt("light_points"),
            rs.getInt("power_points"),
            rs.getInt("switch_points"),
            rs.getInt("requires_dedicated") == 1,
            rs.getString("circuit_type") != null ? rs.getString("circuit_type") : "general"
        );

        HVACConfig hvac = new HVACConfig(
            rs.getInt("requires_exhaust") == 1,
            rs.getInt("exhaust_cfm"),
            rs.getInt("allows_aircon") == 1,
            rs.getString("ventilation_type") != null ? rs.getString("ventilation_type") : "natural"
        );

        return new MEPConfig(plumbing, electrical, hvac);
    }

    /**
     * Parse fixtures JSON array to List.
     * Uses simple parsing without external JSON library.
     */
    private static List<String> parseFixturesJson(String json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        return parseFixturesSimple(json);
    }

    /**
     * Simple JSON array parsing without Jackson dependency.
     */
    private static List<String> parseFixturesSimple(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return result;
        }

        // Remove brackets and split
        String content = json.trim();
        if (content.startsWith("[")) {
            content = content.substring(1);
        }
        if (content.endsWith("]")) {
            content = content.substring(0, content.length() - 1);
        }

        for (String part : content.split(",")) {
            String item = part.trim();
            // Remove quotes
            if (item.startsWith("\"")) {
                item = item.substring(1);
            }
            if (item.endsWith("\"")) {
                item = item.substring(0, item.length() - 1);
            }
            if (!item.isEmpty()) {
                result.add(item);
            }
        }

        return result;
    }

    /**
     * Get all aliases for a space type.
     */
    private static List<String> getAliasesForType(String typeId) {
        List<String> aliases = new ArrayList<>();

        try {
            Connection conn = getConnection();
            if (conn == null) {
                return aliases;
            }

            aliases.addAll(M_AdSpaceType.getAliasesForType(conn, typeId));

        } catch (SQLException e) {
            // Ignore - aliases are optional
        }

        return aliases;
    }

    /**
     * Main method for testing.
     */
    public static void main(String[] args) {
        System.out.println("=== SpaceTypeAD Test ===\n");

        if (!isAvailable()) {
            System.out.println("AD tables not available");
            System.out.println("Run: python scripts/create_ad_space_type_schema.py");
            System.out.println("Run: python scripts/convert_spacetypes_to_ad.py");
            return;
        }

        System.out.println("AD available: " + getSpaceTypeCount() + " space types\n");

        // Test direct lookup
        String[] testTypes = {"BEDROOM", "BATHROOM", "KITCHEN", "CLASSROOM", "ASSEMBLY_HALL"};
        for (String type : testTypes) {
            SpaceTypeConfig config = get(type);
            if (config != null) {
                System.out.printf("%s: %s, %s, minArea=%.1f%n",
                    config.name(), config.category(), config.wallRule(), config.validation().minArea());
            } else {
                System.out.println(type + ": NOT FOUND");
            }
        }

        System.out.println("\n--- Alias Tests ---");
        String[] testAliases = {"BT", "MR", "WC", "DAPUR", "DEWAN"};
        for (String alias : testAliases) {
            String canonical = resolveAlias(alias);
            System.out.printf("%s -> %s%n", alias, canonical != null ? canonical : "(not alias)");
        }

        close();
    }
}
