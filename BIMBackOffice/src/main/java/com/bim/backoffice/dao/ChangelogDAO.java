package com.bim.backoffice.dao;

import com.bim.backoffice.model.DesignBBox;
import com.bim.orm.BIMLogger;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DAO for the audit trail — bim_changelog in work_output.db.
 *
 * <p>Logs every design change with old/new values for undo and
 * multi-user conflict detection. The interceptor pattern wraps
 * {@link WorkOutputDAO#save} without modifying it.
 *
 * // Implementing TIER1_SRS.md §3.3 — Witness: W-AUDIT-DAO-1
 */
public class ChangelogDAO {

    private static final String TAG = "ChangelogDAO";

    private final Connection conn;  // work_output.db

    public ChangelogDAO(Connection conn) {
        this.conn = conn;
    }

    // ── Record types ────────────────────────────────────────────────

    public record ChangeEntry(long changelogId, String buildingId,
                              String variantId, String userId,
                              String timestamp, String action,
                              String entityType, String entityId,
                              String fieldName, String oldValue,
                              String newValue, String bomId) {}

    // ── Schema init ─────────────────────────────────────────────────

    /**
     * Create bim_changelog table. Safe to call repeatedly.
     */
    public void initSchema() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS bim_changelog (
                    changelog_id    INTEGER PRIMARY KEY AUTOINCREMENT,
                    building_id     TEXT NOT NULL,
                    variant_id      TEXT,
                    user_id         TEXT DEFAULT 'local',
                    timestamp       TEXT NOT NULL DEFAULT (datetime('now')),
                    action          TEXT NOT NULL,
                    entity_type     TEXT NOT NULL,
                    entity_id       TEXT NOT NULL,
                    field_name      TEXT,
                    old_value       TEXT,
                    new_value       TEXT,
                    bom_id          TEXT,
                    CONSTRAINT ck_action CHECK (action IN
                        ('SAVE','PLACE','MOVE','RESIZE','DELETE','PROMOTE','UNDO'))
                )
                """);
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_changelog_building
                    ON bim_changelog(building_id, timestamp)
                """);
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_changelog_entity
                    ON bim_changelog(entity_type, entity_id)
                """);
        }
    }

    // ── Write ───────────────────────────────────────────────────────

    /**
     * Log a single field change.
     */
    public void logChange(String buildingId, String variantId,
                          String action, String entityType, String entityId,
                          String fieldName, String oldValue, String newValue,
                          String bomId) throws SQLException {
        String sql = """
                INSERT INTO bim_changelog
                    (building_id, variant_id, user_id, timestamp,
                     action, entity_type, entity_id,
                     field_name, old_value, new_value, bom_id)
                VALUES (?, ?, 'local', ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingId);
            ps.setString(2, variantId);
            ps.setString(3, Instant.now().toString());
            ps.setString(4, action);
            ps.setString(5, entityType);
            ps.setString(6, entityId);
            ps.setString(7, fieldName);
            ps.setString(8, oldValue);
            ps.setString(9, newValue);
            ps.setString(10, bomId);
            ps.executeUpdate();
        }
    }

    /**
     * Log a batch of changes from a save() — diff old vs new bboxes.
     * Detects PLACE (new), DELETE (removed), MOVE (position diff),
     * RESIZE (dimension diff).
     */
    public void logSave(String buildingId, String variantId,
                        List<DesignBBox> oldBboxes, List<DesignBBox> newBboxes)
            throws SQLException {
        Map<String, DesignBBox> oldMap = oldBboxes.stream()
                .collect(Collectors.toMap(DesignBBox::bomId, Function.identity(),
                        (a, b) -> b));
        Map<String, DesignBBox> newMap = newBboxes.stream()
                .collect(Collectors.toMap(DesignBBox::bomId, Function.identity(),
                        (a, b) -> b));

        // Detect PLACEd (in new, not in old)
        for (var entry : newMap.entrySet()) {
            if (!oldMap.containsKey(entry.getKey())) {
                logChange(buildingId, variantId, "PLACE",
                        "C_OrderLine", entry.getKey(),
                        null, null, formatBBox(entry.getValue()),
                        entry.getKey());
            }
        }

        // Detect DELETEd (in old, not in new)
        for (var entry : oldMap.entrySet()) {
            if (!newMap.containsKey(entry.getKey())) {
                logChange(buildingId, variantId, "DELETE",
                        "C_OrderLine", entry.getKey(),
                        null, formatBBox(entry.getValue()), null,
                        entry.getKey());
            }
        }

        // Detect MOVE and RESIZE (in both)
        for (var entry : newMap.entrySet()) {
            DesignBBox oldBB = oldMap.get(entry.getKey());
            if (oldBB == null) continue;
            DesignBBox newBB = entry.getValue();

            // Position change = MOVE
            if (oldBB.minX() != newBB.minX() || oldBB.minY() != newBB.minY()
                    || oldBB.minZ() != newBB.minZ()) {
                logChange(buildingId, variantId, "MOVE",
                        "C_OrderLine", entry.getKey(),
                        "position",
                        String.format("%.0f,%.0f,%.0f", oldBB.minX(), oldBB.minY(), oldBB.minZ()),
                        String.format("%.0f,%.0f,%.0f", newBB.minX(), newBB.minY(), newBB.minZ()),
                        entry.getKey());
            }

            // Dimension change = RESIZE
            double oldW = oldBB.maxX() - oldBB.minX();
            double oldD = oldBB.maxY() - oldBB.minY();
            double oldH = oldBB.maxZ() - oldBB.minZ();
            double newW = newBB.maxX() - newBB.minX();
            double newD = newBB.maxY() - newBB.minY();
            double newH = newBB.maxZ() - newBB.minZ();

            if (oldW != newW || oldD != newD || oldH != newH) {
                logChange(buildingId, variantId, "RESIZE",
                        "C_OrderLine", entry.getKey(),
                        "dimensions",
                        String.format("%.0f,%.0f,%.0f", oldW, oldD, oldH),
                        String.format("%.0f,%.0f,%.0f", newW, newD, newH),
                        entry.getKey());
            }
        }
    }

    // ── Read ────────────────────────────────────────────────────────

    /**
     * Changelog for a building, newest first.
     */
    public List<ChangeEntry> getChangelog(String buildingId, int limit) throws SQLException {
        String sql = """
                SELECT changelog_id, building_id, variant_id, user_id,
                       timestamp, action, entity_type, entity_id,
                       field_name, old_value, new_value, bom_id
                FROM bim_changelog
                WHERE building_id = ?
                ORDER BY changelog_id DESC
                LIMIT ?
                """;
        return queryEntries(sql, buildingId, limit);
    }

    /**
     * Changes between two variants.
     */
    public List<ChangeEntry> getChangesBetween(String buildingId,
                                                String fromVariantId,
                                                String toVariantId) throws SQLException {
        String sql = """
                SELECT changelog_id, building_id, variant_id, user_id,
                       timestamp, action, entity_type, entity_id,
                       field_name, old_value, new_value, bom_id
                FROM bim_changelog
                WHERE building_id = ?
                  AND changelog_id BETWEEN
                      (SELECT MIN(changelog_id) FROM bim_changelog WHERE variant_id = ?)
                  AND (SELECT MAX(changelog_id) FROM bim_changelog WHERE variant_id = ?)
                ORDER BY changelog_id
                """;
        List<ChangeEntry> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingId);
            ps.setString(2, fromVariantId);
            ps.setString(3, toVariantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(readEntry(rs));
                }
            }
        }
        return result;
    }

    // ── Undo ────────────────────────────────────────────────────────

    /**
     * Undo the most recent N changes for a building.
     * Logs the undo itself as action='UNDO'.
     *
     * @return number of changes reverted
     */
    public int undoChanges(String buildingId, int count) throws SQLException {
        List<ChangeEntry> recent = getChangelog(buildingId, count);
        int reverted = 0;

        for (ChangeEntry entry : recent) {
            if ("UNDO".equals(entry.action)) continue; // skip undo-of-undo

            // Log the reversal
            logChange(buildingId, null, "UNDO",
                    entry.entityType, entry.entityId,
                    entry.fieldName, entry.newValue, entry.oldValue,
                    entry.bomId);
            reverted++;
        }

        BIMLogger.info(TAG, "Undo {} changes for building {}", reverted, buildingId);
        return reverted;
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private List<ChangeEntry> queryEntries(String sql, String buildingId, int limit)
            throws SQLException {
        List<ChangeEntry> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buildingId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(readEntry(rs));
                }
            }
        }
        return result;
    }

    private ChangeEntry readEntry(ResultSet rs) throws SQLException {
        return new ChangeEntry(
                rs.getLong("changelog_id"),
                rs.getString("building_id"),
                rs.getString("variant_id"),
                rs.getString("user_id"),
                rs.getString("timestamp"),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getString("field_name"),
                rs.getString("old_value"),
                rs.getString("new_value"),
                rs.getString("bom_id"));
    }

    private String formatBBox(DesignBBox bb) {
        return String.format("%.0f,%.0f,%.0f→%.0f,%.0f,%.0f",
                bb.minX(), bb.minY(), bb.minZ(),
                bb.maxX(), bb.maxY(), bb.maxZ());
    }
}
