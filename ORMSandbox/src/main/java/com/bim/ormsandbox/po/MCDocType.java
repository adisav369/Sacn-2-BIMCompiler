package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code C_DocType} (iDempiere: C_DocType).
 *
 * <p>iDempiere BIM has one DocType: ConstructionOrder. Building routing uses
 * m_bom.m_product_category_id (RE/CO/IN/ST) and doc_sub_type (SH/DX/TE).
 *
 * <p>W018: DocBaseType + DocSubType dropped from C_DocType.
 * doc_sub_type is the single FK to m_bom BUILDING records.
 * m_product_category_id is read from m_bom when needed.
 *
 * @see <a href="docs/ConstructionAsERP.md">Construction as ERP — §11.36</a>
 */
public class MCDocType extends X_C_DocType {

    public MCDocType(Connection conn) { super(conn); }

    /** Load by C_DocType_ID (RE_SH, RE_DX, etc.). Returns null if not found or inactive. */
    public static MCDocType get(Connection conn, String docTypeId) throws SQLException {
        MCDocType dt = new MCDocType(conn);
        return dt.load(docTypeId) && dt.isActive() ? dt : null;
    }

    /** All active document types, ordered by doc_sub_type. */
    public static List<MCDocType> getAll(Connection conn) throws SQLException {
        return new ModelQuery<>(conn, MCDocType::new, Table_Name)
            .where(COLUMNNAME_IsActive + " = ?", 1)
            .orderBy(COLUMNNAME_doc_sub_type)
            .list();
    }

    /** Find by doc_sub_type (SH, DX, TB, TE). Returns first match or null. */
    public static MCDocType getByDocSubType(Connection conn, String subType) throws SQLException {
        return new ModelQuery<>(conn, MCDocType::new, Table_Name)
            .where(COLUMNNAME_doc_sub_type + " = ?", subType)
            .andWhere(COLUMNNAME_IsActive + " = ?", 1)
            .first()
            .orElse(null);
    }

    /**
     * Resolve M_Product_Category from m_bom BUILDING record via doc_sub_type FK.
     * Returns RE/CO/IN/ST, or null if no BUILDING BOM found.
     */
    // Implementing DISC_VALIDATION_DB_SRS.md §10.4.5 — Witness: W-TREE-1
    public String resolveProductCategory() throws SQLException {
        String dst = getDocSubType();
        if (dst == null) return null;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT mpc.Value FROM m_bom b " +
                "LEFT JOIN M_Product_Category mpc ON b.m_product_category_id = mpc.M_Product_Category_ID " +
                "WHERE b.bom_id NOT IN (SELECT child_product_id FROM m_bom_line WHERE is_active = 1) " +
                "AND b.doc_sub_type = ? AND b.is_active = 1 LIMIT 1")) {
            ps.setString(1, dst);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * Map M_Product_Category to the M_BomCategory.doc_type used for template tree lookup.
     * ST shares the RE structural grammar, so both map to 'RE'.
     * Reads m_product_category_id from m_bom via doc_sub_type FK.
     */
    public String toDocTypeName() throws SQLException {
        String cat = resolveProductCategory();
        if (cat == null) return "RE"; // fallback
        return switch (cat) {
            case "RE" -> "RE";
            case "ST" -> "RE";  // ST uses RE template tree
            case "CO" -> "CO";
            case "IN" -> "IN";
            default   -> "RE";
        };
    }
}
