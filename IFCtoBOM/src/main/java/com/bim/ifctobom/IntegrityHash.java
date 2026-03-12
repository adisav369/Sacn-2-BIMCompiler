package com.bim.ifctobom;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SHA-256 integrity hash of extracted BOM line fingerprints.
 * Direct port of {@code RosettaStoneExtract.compute_integrity_hash()}.
 *
 * <p>Fingerprint: sorted (bom_id, child_product_id, dx, dy, dz)
 * for all BUY lines in FLOOR/STOREY STR BOMs.
 */
public class IntegrityHash {

    public static final String SYSCONFIG_KEY = "RSTB_INTEGRITY_HASH";

    /**
     * Compute integrity hash from current BOM DB contents.
     *
     * @param bomConn connection to BOM DB
     * @return hex SHA-256 digest
     */
    public static String compute(Connection bomConn) throws SQLException {
        String sql = """
                SELECT l.bom_id, l.child_product_id,
                       round(l.dx, 6), round(l.dy, 6), round(l.dz, 6)
                FROM m_bom_line l
                JOIN m_bom b ON l.bom_id = b.bom_id
                WHERE b.bom_type = 'FLOOR' AND l.component_type = 'BUY'
                  AND b.group_by = 'STOREY' AND b.bom_id LIKE '%_STR'
                ORDER BY l.bom_id, l.child_product_id,
                         round(l.dx,6), round(l.dy,6), round(l.dz,6)
                """;

        List<String> fingerprints = new ArrayList<>();
        try (Statement stmt = bomConn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                fingerprints.add(String.format("%s,%s,%s,%s,%s",
                        rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5)));
            }
        }

        String joined = String.join("|", fingerprints);
        String hash = sha256(joined);
        return hash;
    }

    /**
     * Compute and store the integrity hash in ad_sysconfig.
     *
     * @return hex SHA-256 digest
     */
    public static String computeAndStore(Connection bomConn) throws SQLException {
        String hash = compute(bomConn);

        // Delete existing entry
        try (Statement stmt = bomConn.createStatement()) {
            stmt.executeUpdate(
                    "DELETE FROM ad_sysconfig WHERE config_key='" + SYSCONFIG_KEY + "'");
        }

        // Insert new entry
        String sql = """
                INSERT INTO ad_sysconfig (config_key, config_value, description, is_active)
                VALUES (?, ?, 'SHA-256 of extracted BOM line fingerprints', 1)
                """;
        try (PreparedStatement stmt = bomConn.prepareStatement(sql)) {
            stmt.setString(1, SYSCONFIG_KEY);
            stmt.setString(2, hash);
            stmt.executeUpdate();
        }

        return hash;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
