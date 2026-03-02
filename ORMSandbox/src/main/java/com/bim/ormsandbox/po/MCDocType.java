package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code C_DocType} (iDempiere: C_DocType).
 *
 * <p>5 entries: RE_SH (Sample House), RE_DX (Duplex), RE_TB (Terrace Block),
 * CO_TE (Airport Terminal), RE_ST (Standard/Demo — IsDefault for Residential).
 *
 * <p>DocBaseType drives template selection:
 * <ul>
 *   <li>RE → Residential template (M_BomCategory.doc_type='Residential')</li>
 *   <li>CO → Commercial template (future)</li>
 *   <li>IN → Industrial template (future)</li>
 * </ul>
 *
 * <p>DocSubType drives BOM scoping:
 * <ul>
 *   <li>SH → SH-specific BOMs (SH_BED_SET, SH_LIVING_SET, etc.)</li>
 *   <li>DX → DX-specific BOMs (DUPLEX_SET_STD, etc.)</li>
 *   <li>ST → generic/demo (fallback to any matching BOM)</li>
 * </ul>
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

    /** All active document types, ordered by DocBaseType + DocSubType. */
    public static List<MCDocType> getAll(Connection conn) throws SQLException {
        return new ModelQuery<>(conn, MCDocType::new, Table_Name)
            .where(COLUMNNAME_IsActive + " = ?", 1)
            .orderBy(COLUMNNAME_DocBaseType + ", " + COLUMNNAME_DocSubType)
            .list();
    }

    /** All active document types for a given base type (RE, CO, IN). */
    public static List<MCDocType> getByDocBaseType(Connection conn, String baseType) throws SQLException {
        return new ModelQuery<>(conn, MCDocType::new, Table_Name)
            .where(COLUMNNAME_DocBaseType + " = ?", baseType)
            .andWhere(COLUMNNAME_IsActive + " = ?", 1)
            .orderBy(COLUMNNAME_DocSubType)
            .list();
    }

    /** Find by DocSubType (SH, DX, TB, TE, ST). Returns first match or null. */
    public static MCDocType getByDocSubType(Connection conn, String subType) throws SQLException {
        return new ModelQuery<>(conn, MCDocType::new, Table_Name)
            .where(COLUMNNAME_DocSubType + " = ?", subType)
            .andWhere(COLUMNNAME_IsActive + " = ?", 1)
            .first()
            .orElse(null);
    }

    /** Find the default DocType for a given base type. Returns null if none marked default. */
    public static MCDocType getDefault(Connection conn, String baseType) throws SQLException {
        return new ModelQuery<>(conn, MCDocType::new, Table_Name)
            .where(COLUMNNAME_DocBaseType + " = ?", baseType)
            .andWhere(COLUMNNAME_IsDefault + " = ?", 1)
            .andWhere(COLUMNNAME_IsActive + " = ?", 1)
            .first()
            .orElse(null);
    }

    /**
     * Map c_order.building_type (UPPERCASE) to M_BomCategory.doc_type (Title Case).
     * Uses DocBaseType as the bridge: RE → Residential, CO → Commercial, IN → Industrial.
     */
    public String toDocTypeName() {
        return switch (getDocBaseType()) {
            case "RE" -> "Residential";
            case "CO" -> "Commercial";
            case "IN" -> "Industrial";
            default   -> "Residential";
        };
    }
}
