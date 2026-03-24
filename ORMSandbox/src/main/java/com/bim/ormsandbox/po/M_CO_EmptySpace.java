package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Model (DAO) layer for {@code co_empty_space}.
 *
 * @deprecated Compiler-internal table. WHERE concern lives in M_BOM_Line dx/dy/dz
 * (MANIFESTO.md §Three Concerns). Migrate consumers to BOM spatial queries.
 *
 * <p>Construction site header. One record per C_Order (building).
 * For SH/DX this is trivially one record per compile — the full
 * building AABB accepted with a single top-level BOM.
 *
 * <p>IsAvailable is the quality gate:
 * <ul>
 *   <li>Start: 1 (space unproven)</li>
 *   <li>After tests GREEN: 0 (space confirmed consumed)</li>
 *   <li>Reprocess: reset to 1</li>
 * </ul>
 */
@Deprecated
public class M_CO_EmptySpace extends X_CO_EmptySpace {

    public M_CO_EmptySpace(Connection conn) { super(conn); }

    // ── Factory ─────────────────────────────────────────────────────────────

    /**
     * Create a new CO_EmptySpace from a building's AABB.
     * Starts at DR/available=1. Caller must call save().
     */
    public static M_CO_EmptySpace create(
            Connection conn, String cOrderId,
            double originXMm, double originYMm, double originZMm,
            double widthMm, double depthMm, double heightMm)
            throws SQLException {
        M_CO_EmptySpace es = new M_CO_EmptySpace(conn);
        es.setCOrderId(cOrderId);
        es.setOriginXMm(originXMm);
        es.setOriginYMm(originYMm);
        es.setOriginZMm(originZMm);
        es.setAabbWidthMm(widthMm);
        es.setAabbDepthMm(depthMm);
        es.setAabbHeightMm(heightMm);
        es.setIsAvailable(true);
        es.setDocStatus(DOCSTATUS_Draft);
        return es;
    }

    // ── DocStatus transitions ───────────────────────────────────────────────

    /** DR → IP: compilation started. */
    public void setProcessing() {
        setDocStatus(DOCSTATUS_InProgress);
    }

    /** IP → CO + available=0: compilation passed all tests. */
    public void setComplete() {
        setDocStatus(DOCSTATUS_Complete);
        setIsAvailable(false);
    }

    /** Any → RE: BOM doesn't fit AABB. available stays 1. */
    public void setRejected() {
        setDocStatus(DOCSTATUS_Rejected);
    }

    /** Reset for reprocess: available=1, status=DR. */
    public void resetForReprocess() {
        setDocStatus(DOCSTATUS_Draft);
        setIsAvailable(true);
    }

    // ── Query ───────────────────────────────────────────────────────────────

    /** Get the CO_EmptySpace for a building (most recent non-void). */
    public static Optional<M_CO_EmptySpace> getForBuilding(
            Connection conn, String cOrderId) throws SQLException {
        return new ModelQuery<>(conn, M_CO_EmptySpace::new, Table_Name)
            .where(COLUMNNAME_c_order_id + " = ?", cOrderId)
            .andWhere(COLUMNNAME_doc_status + " != ?", DOCSTATUS_Rejected)
            .orderBy(COLUMNNAME_co_emptyspace_id + " DESC")
            .first();
    }
}
