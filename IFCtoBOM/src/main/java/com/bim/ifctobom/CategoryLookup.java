package com.bim.ifctobom;

import java.sql.*;
import java.util.*;

/**
 * Resolves M_Product_Category TEXT codes (Value) to INTEGER PKs.
 *
 * <p>Loaded once from ERP.db at pipeline start. Passed to all BOM builders
 * so they can write INTEGER FKs to BOM.db m_bom.m_product_category_id.
 *
 * <p>Phase B of iDempiere PK conformance (DV027).
 */
// Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 — Witness: W-DV-CAT-PK
public class CategoryLookup {

    private final Map<String, Integer> map;

    private CategoryLookup(Map<String, Integer> map) {
        this.map = map;
    }

    /**
     * Load all category Value → ID mappings from ERP.db.
     */
    public static CategoryLookup load(Connection erpConn) throws SQLException {
        Map<String, Integer> m = new HashMap<>();
        try (Statement stmt = erpConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT M_Product_Category_ID, Value FROM M_Product_Category")) {
            while (rs.next()) {
                m.put(rs.getString("Value"), rs.getInt("M_Product_Category_ID"));
            }
        }
        return new CategoryLookup(m);
    }

    /**
     * Look up INTEGER PK for a category text code.
     * Returns 0 if not found (NULL-safe for optional categories).
     */
    public int getId(String value) {
        if (value == null) return 0;
        return map.getOrDefault(value, 0);
    }

    /**
     * Whether the given value exists in the lookup.
     */
    public boolean contains(String value) {
        return value != null && map.containsKey(value);
    }

    public int size() { return map.size(); }
}
