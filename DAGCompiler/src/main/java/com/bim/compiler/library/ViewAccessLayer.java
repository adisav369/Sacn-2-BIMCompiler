package com.bim.compiler.library;

import java.sql.*;
import java.util.*;

/**
 * ViewAccessLayer — the compiler's only data access path.
 * Stateless. All cascade state managed by QualifiedBomCascade.
 * No MetadataMissingException. No null fallbacks. Empty = not ready.
 *
 * VIEW_CONTRACTS.md §7 — Phase 4b.
 * SQL constants are verbatim from §7; method signatures are fixed contract.
 */
public final class ViewAccessLayer implements AutoCloseable {

    private static String bomDbPath() { return System.getProperty("bom.db"); }

    private static final String QUALIFIED_BOM =
        "SELECT * FROM v_qualified_bom " +
        "WHERE bom_type = ? AND min_space_mm <= ? " +
        "ORDER BY fit_priority ASC, width_mm DESC";

    private static final String ROOM_BOUNDARY =
        "SELECT * FROM v_verified_room_boundary " +
        "WHERE building_type = ? AND room_type = ?";

    private static final String ELEMENT_RULE =
        "SELECT * FROM v_compilable_element_rule " +
        "WHERE element_ref = ? AND building_type = ?";

    private static final String PROVEN_GEOMETRY =
        "SELECT * FROM v_proven_geometry WHERE name LIKE ?";

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private final Connection conn;

    public ViewAccessLayer(String dbPath) throws SQLException {
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    public ViewAccessLayer() throws SQLException {
        this(bomDbPath());
    }

    /** §7 — exact signature. Returns empty list when view returns zero rows. */
    public List<QualifiedBom> getQualifiedBoms(String bomType, int availableSpaceMm) {
        return query(QUALIFIED_BOM, bomType, availableSpaceMm, QualifiedBom::from);
    }

    /** §7 — exact signature. Returns empty Optional when view returns zero rows. */
    public Optional<VerifiedRoomBoundary> getRoomBoundary(
            String buildingType, String roomType) {
        return queryOne(ROOM_BOUNDARY, buildingType, roomType,
            VerifiedRoomBoundary::from);
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private <T> List<T> query(String sql, Object p1, Object p2, RowMapper<T> mapper) {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, p1);
            stmt.setObject(2, p2);
            return collect(stmt, mapper);
        } catch (SQLException e) {
            return List.of();
        }
    }

    private <T> List<T> query(String sql, Object p1, RowMapper<T> mapper) {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, p1);
            return collect(stmt, mapper);
        } catch (SQLException e) {
            return List.of();
        }
    }

    private <T> Optional<T> queryOne(String sql, Object p1, Object p2, RowMapper<T> mapper) {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, p1);
            stmt.setObject(2, p2);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    private <T> List<T> collect(PreparedStatement stmt, RowMapper<T> mapper) throws SQLException {
        try (ResultSet rs = stmt.executeQuery()) {
            List<T> result = new ArrayList<>();
            while (rs.next()) {
                result.add(mapper.map(rs));
            }
            return result;
        }
    }

    @Override
    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
}
