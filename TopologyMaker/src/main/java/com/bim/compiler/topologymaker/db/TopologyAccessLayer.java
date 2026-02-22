package com.bim.compiler.topologymaker.db;

import com.bim.compiler.topologymaker.rule.UbblValidator;

import java.sql.*;
import java.util.*;

/**
 * Read-only access to topology catalog tables in library DB.
 * Follows the ViewAccessLayer pattern — AutoCloseable, Optional returns, no nulls.
 */
public final class TopologyAccessLayer implements AutoCloseable {

    private static final String LIBRARY_DB_PATH = "library/component_library.db";

    /**
     * Typology template row from ad_typology_pattern.
     *
     * @param typologyId    PK
     * @param gridStrategy  "STRIP_ZONES" | "COURTYARD" | "LINEAR"
     * @param ubblClass     e.g. "RESIDENTIAL_TYPE_C"
     * @param baseWidthMm   Template base site width in mm
     * @param baseDepthMm   Template base site depth in mm
     * @param zoneJson      Raw JSON zone definition string
     */
    public record TypologyPattern(
            String typologyId,
            String gridStrategy,
            String ubblClass,
            int baseWidthMm,
            int baseDepthMm,
            String zoneJson
    ) {}

    private final Connection conn;

    public TopologyAccessLayer(String dbPath) throws SQLException {
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    public TopologyAccessLayer() throws SQLException {
        this(LIBRARY_DB_PATH);
    }

    /**
     * Load a typology template by ID.
     *
     * @param typologyId  PK to look up
     * @return Optional containing the pattern, or empty if not found or table missing
     */
    public Optional<TypologyPattern> getTypology(String typologyId) {
        String sql = "SELECT typology_id, grid_strategy, ubbl_class, " +
                     "base_width_mm, base_depth_mm, zone_json " +
                     "FROM ad_typology_pattern WHERE typology_id = ? AND is_active = 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, typologyId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new TypologyPattern(
                    rs.getString("typology_id"),
                    rs.getString("grid_strategy"),
                    rs.getString("ubbl_class"),
                    rs.getInt("base_width_mm"),
                    rs.getInt("base_depth_mm"),
                    rs.getString("zone_json")
                ));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    /**
     * Load active UBBL rules for a given class (or all if ubblClass is null).
     * CEILING_MM rules are included but UbblValidator skips them at validation time.
     *
     * @param ubblClass  Class tag to match (e.g. "RESIDENTIAL_TYPE_C"); null = all active
     * @return List of rules, possibly empty
     */
    public List<UbblValidator.UbblRule> getUbblRules(String ubblClass) {
        // Rules in ad_spatial_rule are not filtered by ubbl_class column (that column holds
        // the UBBL reference clause), so we load all active rules.
        String sql = "SELECT rule_id, room_type, constraint_key, min_value_mm, ubbl_ref " +
                     "FROM ad_spatial_rule WHERE is_active = 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            List<UbblValidator.UbblRule> rules = new ArrayList<>();
            while (rs.next()) {
                rules.add(new UbblValidator.UbblRule(
                    rs.getString("rule_id"),
                    rs.getString("room_type"),   // may be null
                    rs.getString("constraint_key"),
                    rs.getDouble("min_value_mm"),
                    rs.getString("ubbl_ref")      // may be null
                ));
            }
            return rules;
        } catch (SQLException e) {
            return List.of();
        }
    }

    /** Check whether a given BOM ID already exists in ad_bom. */
    public boolean bomExists(String bomId) {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM ad_bom WHERE bom_id = ?")) {
            stmt.setString(1, bomId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /** Count rows in ad_bom with the given bom_type. */
    public int countBomsByType(String bomType) {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM ad_bom WHERE bom_type = ?")) {
            stmt.setString(1, bomType);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    @Override
    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
}
