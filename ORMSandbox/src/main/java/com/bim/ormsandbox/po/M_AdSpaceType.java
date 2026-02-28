package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model layer for {@code ad_space_type}.
 *
 * <p>Also provides static accessors for {@code ad_space_type_alias} (2-column table,
 * no separate PO warranted): {@link #resolveAlias}, {@link #loadAllAliases},
 * {@link #getAliasesForType}.
 */
public class M_AdSpaceType extends X_AdSpaceType {

    private static final String ALIAS_TABLE = "ad_space_type_alias";

    public M_AdSpaceType(Connection conn) { super(conn); }

    // ── ad_space_type queries ─────────────────────────────────────────────────

    /**
     * All active space types, ordered by space_type_id.
     * Use {@code .size()} for count, {@code .isEmpty()} for availability check.
     */
    public static List<M_AdSpaceType> listActive(Connection conn) throws SQLException {
        return new ModelQuery<>(conn, M_AdSpaceType::new, Table_Name)
            .where(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_space_type_id)
            .list();
    }

    /**
     * Look up a single space type by ID. Returns null if not found or inactive.
     */
    public static M_AdSpaceType getById(Connection conn, String spaceTypeId) throws SQLException {
        return new ModelQuery<>(conn, M_AdSpaceType::new, Table_Name)
            .where(COLUMNNAME_space_type_id + " = ?", spaceTypeId)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .first()
            .orElse(null);
    }

    /**
     * True if a space type with the given ID exists and is active.
     * Used by ADSession.SpaceTypeLookup.isValid().
     */
    public static boolean existsActive(Connection conn, String spaceTypeId) throws SQLException {
        return new ModelQuery<>(conn, M_AdSpaceType::new, Table_Name)
            .where(COLUMNNAME_space_type_id + " = ?", spaceTypeId)
            .andWhere(COLUMNNAME_is_active + " = ?", 1)
            .first()
            .isPresent();
    }

    // ── ad_space_type_alias queries ───────────────────────────────────────────

    /**
     * Resolve an alias to its canonical space_type_id.
     * Returns null if the alias is not found (caller decides fallback).
     * Used by SpaceTypeAD.resolveAlias() and ADSession.SpaceTypeLookup.resolveAlias().
     */
    public static String resolveAlias(Connection conn, String alias) throws SQLException {
        String sql = "SELECT space_type_id FROM " + ALIAS_TABLE + " WHERE alias = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, alias);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * Load the full alias → space_type_id map in one query.
     * Values are upper-cased to match the convention in MEPBOMResolver and SpaceDimResolver.
     * Used by MEPBOMResolver.loadFromLibrary() and SpaceDimResolver.loadAliases().
     */
    public static Map<String, String> loadAllAliases(Connection conn) throws SQLException {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT alias, space_type_id FROM " + ALIAS_TABLE;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                map.put(rs.getString("alias").toUpperCase(),
                        rs.getString("space_type_id").toUpperCase());
            }
        }
        return map;
    }

    /**
     * All aliases registered for a given space_type_id.
     * Used by SpaceTypeAD.getAliases().
     */
    public static List<String> getAliasesForType(Connection conn, String spaceTypeId)
            throws SQLException {
        List<String> result = new ArrayList<>();
        String sql = "SELECT alias FROM " + ALIAS_TABLE + " WHERE space_type_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, spaceTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getString("alias"));
            }
        }
        return result;
    }
}
