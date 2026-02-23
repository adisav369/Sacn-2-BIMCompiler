package com.bim.orm;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lightweight Persistent Object base class — SQLite edition.
 * Shared by all BIMCompiler modules (TopologyMaker, ORMSandbox, etc.).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Hold a JDBC Connection (injected, not owned — caller manages lifecycle)</li>
 *   <li>load(id): SELECT * FROM tableName WHERE pk = id → populate column map</li>
 *   <li>save(): INSERT OR IGNORE (new) or UPDATE dirty cols (existing)</li>
 *   <li>delete(): DELETE FROM tableName WHERE pk = id</li>
 * </ul>
 *
 * <p>Column values stored in LinkedHashMap&lt;String, Object&gt; — key = COLUMNNAME constant.
 * Dirty flags in LinkedHashSet&lt;String&gt; — only dirty columns written on UPDATE.
 *
 * <p>Primary key strategy:
 * <ul>
 *   <li>INTEGER AUTOINCREMENT columns — PK not included in INSERT; DB assigns; read back
 *       via {@link Statement#RETURN_GENERATED_KEYS}.</li>
 *   <li>TEXT PKs — set explicitly before save(); included in dirty set; written in INSERT.</li>
 * </ul>
 *
 * <p>Transaction ownership: BasePO never calls commit(). The orchestrator
 * (TopologyBatchProcess or equivalent) owns the transaction boundary. This matches
 * the iDempiere PO contract exactly.
 */
public abstract class BasePO {

    protected final Connection conn;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Set<String> dirty = new LinkedHashSet<>();
    /**
     * Explicit new-record flag — not derived from PK value presence.
     * <ul>
     *   <li>Starts {@code true} for every freshly constructed object.</li>
     *   <li>Set to {@code false} by {@link #loadFromResultSet} (loaded from DB).</li>
     *   <li>Set to {@code false} after a successful INSERT in {@link #save()}.</li>
     * </ul>
     * This is necessary because a TEXT PK (e.g. building_id = "TERRACE_007") is
     * non-blank before save() but the row does not yet exist in the DB.
     */
    private boolean isNewRecord = true;

    protected BasePO(Connection conn) {
        this.conn = conn;
    }

    protected abstract String getTableName();
    protected abstract String getPKColumnName();

    // ── Typed accessors ────────────────────────────────────────────────────────

    protected Object get_Value(String columnName) {
        return values.get(columnName);
    }

    protected String get_ValueAsString(String columnName) {
        Object v = values.get(columnName);
        return v == null ? null : v.toString();
    }

    protected int get_ValueAsInt(String columnName) {
        Object v = values.get(columnName);
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return 0; }
    }

    protected double get_ValueAsDouble(String columnName) {
        Object v = values.get(columnName);
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return 0.0; }
    }

    protected boolean get_ValueAsBoolean(String columnName) {
        Object v = values.get(columnName);
        if (v == null) return false;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        return Boolean.parseBoolean(v.toString());
    }

    protected void set_Value(String columnName, Object value) {
        values.put(columnName, value);
        dirty.add(columnName);
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    /** Load by TEXT primary key. Returns true if row found. */
    public boolean load(String id) throws SQLException {
        String sql = "SELECT * FROM " + getTableName()
                   + " WHERE " + getPKColumnName() + " = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return false;
                loadFromResultSet(rs);
                return true;
            }
        }
    }

    /** Load by INTEGER primary key. Returns true if row found. */
    public boolean load(int id) throws SQLException {
        String sql = "SELECT * FROM " + getTableName()
                   + " WHERE " + getPKColumnName() + " = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return false;
                loadFromResultSet(rs);
                return true;
            }
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    /**
     * Save to DB.
     *
     * <p>Calls {@link #beforeSave(boolean)} first — throw {@link IllegalStateException} to abort.
     *
     * <p>New record (isNew() = true):
     * <ul>
     *   <li>INSERT OR IGNORE — only dirty columns written.</li>
     *   <li>PK included in INSERT if it was explicitly set (TEXT PKs); excluded otherwise
     *       (INTEGER AUTOINCREMENT — DB assigns the key, which is read back).</li>
     * </ul>
     *
     * <p>Existing record: UPDATE SET dirty columns WHERE pk = value.
     *
     * <p>Calls {@link #afterSave(boolean)} on success, then clears dirty set.
     */
    public boolean save() throws SQLException {
        boolean newRecord = isNew();
        beforeSave(newRecord);

        if (newRecord) {
            List<String> cols = new ArrayList<>(dirty);
            if (cols.isEmpty()) {
                throw new IllegalStateException("Cannot save: no columns set on new record");
            }
            String placeholders = cols.stream().map(c -> "?")
                                      .collect(Collectors.joining(", "));
            String sql = "INSERT OR IGNORE INTO " + getTableName()
                       + " (" + String.join(", ", cols) + ")"
                       + " VALUES (" + placeholders + ")";

            try (PreparedStatement stmt = conn.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {
                for (int i = 0; i < cols.size(); i++) {
                    stmt.setObject(i + 1, values.get(cols.get(i)));
                }
                stmt.executeUpdate();
                // Read back generated key for INTEGER AUTOINCREMENT PKs (not in dirty)
                if (!dirty.contains(getPKColumnName())) {
                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        if (keys.next()) {
                            values.put(getPKColumnName(), keys.getInt(1));
                        }
                    }
                }
                isNewRecord = false;
            }
        } else {
            if (!dirty.isEmpty()) {
                List<String> cols = new ArrayList<>(dirty);
                String setClause = cols.stream()
                                       .map(c -> c + " = ?")
                                       .collect(Collectors.joining(", "));
                String sql = "UPDATE " + getTableName()
                           + " SET " + setClause
                           + " WHERE " + getPKColumnName() + " = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (int i = 0; i < cols.size(); i++) {
                        stmt.setObject(i + 1, values.get(cols.get(i)));
                    }
                    stmt.setObject(cols.size() + 1, values.get(getPKColumnName()));
                    stmt.executeUpdate();
                }
            }
        }

        afterSave(newRecord);
        dirty.clear();
        return true;
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public boolean delete() throws SQLException {
        Object pk = values.get(getPKColumnName());
        if (pk == null) return false;
        String sql = "DELETE FROM " + getTableName()
                   + " WHERE " + getPKColumnName() + " = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, pk);
            return stmt.executeUpdate() > 0;
        }
    }

    // ── Lifecycle hooks (override in M_ layer) ────────────────────────────────

    /** Override in M_ layer for pre-write validation. Throw to abort save(). */
    protected void beforeSave(boolean newRecord) {}

    /** Override in M_ layer for post-write side effects. */
    protected void afterSave(boolean newRecord) {}

    // ── State queries ─────────────────────────────────────────────────────────

    /**
     * True if this object has not yet been persisted to the DB.
     *
     * <p>Uses an explicit flag, not PK value presence — a TEXT PK may be set before
     * save() but the row does not yet exist. The flag starts {@code true}, is cleared
     * after a successful INSERT, and is set to {@code false} on load.
     */
    public boolean isNew() {
        return isNewRecord;
    }

    /** Number of dirty (modified-since-load) columns. Used in tests. */
    public int getDirtyColumnCount() {
        return dirty.size();
    }

    // ── Package-private — called only by ModelQuery ───────────────────────────

    /**
     * Populate this PO from a ResultSet row without a second SELECT.
     * Does NOT mark any columns dirty — dirty set stays empty after load.
     */
    void loadFromResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            values.put(meta.getColumnName(i), rs.getObject(i));
        }
        dirty.clear();
        isNewRecord = false;
    }
}
