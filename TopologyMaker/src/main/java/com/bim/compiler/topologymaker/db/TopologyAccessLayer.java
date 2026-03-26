package com.bim.compiler.topologymaker.db;

import com.bim.compiler.topologymaker.po.M_AdTypologyPattern;
import com.bim.compiler.topologymaker.rule.UbblValidator;

import java.sql.*;
import java.util.*;

/**
 * Read-only access to topology catalog tables in library DB.
 *
 * <p>Typology reads delegate to {@link M_AdTypologyPattern} PO objects.
 * UBBL rule reads and BOM existence checks use raw JDBC
 * (ad_spatial_rule and m_bom are outside the current PO phase scope).
 *
 * <p>Follows the ViewAccessLayer pattern — AutoCloseable, Optional returns, no nulls.
 */
public final class TopologyAccessLayer implements AutoCloseable {

    private static String dbPath() { return System.getProperty("bom.db"); }

    /**
     * Typology template row from ad_typology_pattern — outbound DTO.
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
        this(dbPath());
    }

    /**
     * Load a typology template by ID, delegating to {@link M_AdTypologyPattern}.
     *
     * @param typologyId  PK to look up
     * @return Optional containing the TypologyPattern DTO, or empty if not found or inactive
     */
    public Optional<TypologyPattern> getTypology(String typologyId) {
        try {
            M_AdTypologyPattern m = M_AdTypologyPattern.get(conn, typologyId);
            return m != null ? Optional.of(m.toPattern()) : Optional.empty();
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
        String sql = "SELECT rule_id, room_type, constraint_key, min_value_mm, ubbl_ref " +
                     "FROM ad_ubbl_rule WHERE is_active = 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            List<UbblValidator.UbblRule> rules = new ArrayList<>();
            while (rs.next()) {
                rules.add(new UbblValidator.UbblRule(
                    rs.getString("rule_id"),
                    rs.getString("room_type"),
                    rs.getString("constraint_key"),
                    rs.getDouble("min_value_mm"),
                    rs.getString("ubbl_ref")
                ));
            }
            return rules;
        } catch (SQLException e) {
            return List.of();
        }
    }

    /** Check whether a given BOM ID already exists in m_bom (BOM.db). */
    public boolean bomExists(String bomId) {
        // Phase E: text lookup via Value column (INTEGER PK migration)
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM m_bom WHERE Value = ?")) {
            stmt.setString(1, bomId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Look up the prefab BOM ID for a room type within a typology.
     *
     * <p>Queries {@code ad_room_slot} for {@code slot_name='PREFAB'} entries scoped
     * to the given typology. Replaces the hardcoded switch in TopologyBatchProcess.
     *
     * @param roomType   Room type code (e.g. "BEDROOM", "BATHROOM")
     * @param typologyId Building type / typology ID (e.g. "TERRACE_MY_1S")
     * @return assembly_id from ad_room_slot, or {@code "LIVING_PREFAB_MY"} as fallback
     */
    public String getPrefabBomForRoom(String roomType, String typologyId) {
        String sql = "SELECT assembly_id FROM ad_room_slot " +
                     "WHERE room_type = ? AND building_type = ? AND slot_name = 'PREFAB' " +
                     "LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, roomType);
            stmt.setString(2, typologyId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString("assembly_id");
                    return id != null ? id : "LIVING_PREFAB_MY";
                }
            }
        } catch (SQLException ex) {
            // fallback below
        }
        return "LIVING_PREFAB_MY";
    }

    /** Count rows in m_bom with the given bom_type (BOM.db). */
    public int countBomsByType(String bomType) {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM m_bom WHERE bom_type = ?")) {
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
