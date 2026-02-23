package com.bim.orm;

import java.sql.*;
import java.util.*;

/**
 * Fluent query builder for PO-backed tables — the OQL layer.
 * Analogous to {@code org.compiere.model.Query} in iDempiere.
 *
 * <p>Table names and column names are supplied via {@code COLUMNNAME_*} constants
 * from X_ classes, eliminating raw string literals in query code.
 *
 * <p>Only for {@code ad_*} tables with a PK and a save() lifecycle.
 * Never apply to {@code v_*} views — use ViewAccessLayer for those.
 *
 * <p>Usage — single table:
 * <pre>{@code
 * List<M_AdElementRule> rules =
 *     new ModelQuery<>(conn, M_AdElementRule::new,
 *                      X_AdElementRule.Table_Name)
 *         .where(X_AdElementRule.COLUMNNAME_building_id + " = ?", buildingId)
 *         .orderBy(X_AdElementRule.COLUMNNAME_id)
 *         .list();
 * }</pre>
 *
 * <p>Usage — with JOIN (select only primary table columns via alias):
 * <pre>{@code
 * List<M_AdRoomBoundary> rooms =
 *     new ModelQuery<>(conn, M_AdRoomBoundary::new,
 *                      X_AdRoomBoundary.Table_Name + " rb")
 *         .addJoin(X_AdBuildingRegistry.Table_Name + " br",
 *             "br." + X_AdBuildingRegistry.COLUMNNAME_building_id +
 *             " = rb." + X_AdRoomBoundary.COLUMNNAME_building_type)
 *         .where("rb." + X_AdRoomBoundary.COLUMNNAME_building_type + " = ?",
 *                buildingType)
 *         .list();
 * }</pre>
 *
 * @param <T> A concrete BasePO subclass (M_ layer)
 */
public final class ModelQuery<T extends BasePO> {

    @FunctionalInterface
    public interface POFactory<T> {
        T create(Connection conn);
    }

    private final Connection conn;
    private final POFactory<T> factory;
    private final String fromClause;
    private final List<String> joinClauses = new ArrayList<>();
    private final List<String> whereParts  = new ArrayList<>();
    private final List<Object> params      = new ArrayList<>();
    private String orderByClause = null;
    private int limitValue = 0;

    public ModelQuery(Connection conn, POFactory<T> factory, String tableName) {
        this.conn = conn;
        this.factory = factory;
        this.fromClause = tableName;
    }

    /** Add an INNER JOIN clause. {@code joinCondition} should use COLUMNNAME constants. */
    public ModelQuery<T> addJoin(String joinTable, String joinCondition) {
        joinClauses.add("JOIN " + joinTable + " ON " + joinCondition);
        return this;
    }

    /** Add a LEFT JOIN — for optional relationships. */
    public ModelQuery<T> addLeftJoin(String joinTable, String joinCondition) {
        joinClauses.add("LEFT JOIN " + joinTable + " ON " + joinCondition);
        return this;
    }

    /**
     * Set the WHERE clause, replacing any previous one.
     * Use {@code ?} for parameters.
     */
    public ModelQuery<T> where(String condition, Object... bindParams) {
        whereParts.clear();
        params.clear();
        whereParts.add(condition);
        for (Object p : bindParams) params.add(p);
        return this;
    }

    /** Append an AND condition to the WHERE clause. */
    public ModelQuery<T> andWhere(String condition, Object... bindParams) {
        whereParts.add(condition);
        for (Object p : bindParams) params.add(p);
        return this;
    }

    /** ORDER BY clause — use COLUMNNAME constants to avoid raw string literals. */
    public ModelQuery<T> orderBy(String columnExpression) {
        this.orderByClause = columnExpression;
        return this;
    }

    /** Limit result set size — useful for existence checks via first(). */
    public ModelQuery<T> setLimit(int n) {
        this.limitValue = n;
        return this;
    }

    /** Execute and return all matching rows as PO objects. */
    public List<T> list() throws SQLException {
        String sql = buildSQL();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rs.next()) {
                    T po = factory.create(conn);
                    po.loadFromResultSet(rs);
                    result.add(po);
                }
                return result;
            }
        }
    }

    /** Execute and return the first matching row, or empty if none. */
    public Optional<T> first() throws SQLException {
        List<T> rows = setLimit(1).list();
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Count matching rows without loading PO objects. */
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + fromClause
                   + buildJoins() + buildWhere();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ── SQL assembly ──────────────────────────────────────────────────────────

    private String buildSQL() {
        // For aliased tables ("ad_typology_pattern tp"), select alias.*
        // For plain tables ("ad_typology_pattern"), select table.*
        String selectTarget = fromClause.contains(" ")
            ? fromClause.split("\\s+")[1] + ".*"
            : fromClause + ".*";

        StringBuilder sb = new StringBuilder("SELECT ")
            .append(selectTarget)
            .append(" FROM ").append(fromClause)
            .append(buildJoins())
            .append(buildWhere());
        if (orderByClause != null) sb.append(" ORDER BY ").append(orderByClause);
        if (limitValue > 0)        sb.append(" LIMIT ").append(limitValue);
        return sb.toString();
    }

    private String buildJoins() {
        return joinClauses.isEmpty() ? "" : " " + String.join(" ", joinClauses);
    }

    private String buildWhere() {
        return whereParts.isEmpty() ? "" :
               " WHERE " + String.join(" AND ", whereParts);
    }

    private void bindParams(PreparedStatement stmt) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            stmt.setObject(i + 1, params.get(i));
        }
    }
}
