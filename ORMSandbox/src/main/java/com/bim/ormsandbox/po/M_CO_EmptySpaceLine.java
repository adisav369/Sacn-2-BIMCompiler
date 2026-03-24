package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model (DAO) layer for {@code co_empty_space_line}.
 *
 * @deprecated Compiler-internal table. WHERE concern lives in M_BOM_Line dx/dy/dz
 * (MANIFESTO.md §Three Concerns). Migrate consumers to BOM spatial queries.
 *
 * <p>Alignment record — WHERE the BOM box sits + orientation.
 * Does NOT repeat the BOM (that's intact on C_OrderLine.BOM.BOMLine).
 * Says: "this BOM construct goes HERE, facing THIS way."
 *
 * <p>For SH/DX: as few as ONE record per building — accepting the
 * top-level UNIT BOM into the full AABB. All children translate
 * deterministically from BOM offsets.
 */
@Deprecated
public class M_CO_EmptySpaceLine extends X_CO_EmptySpaceLine {

    public M_CO_EmptySpaceLine(Connection conn) { super(conn); }

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Create a line accepting a BOM into the parent CO_EmptySpace.
     * Sets before anchor = origin, next anchor = origin + BOM extent.
     * Caller must call save().
     */
    public static M_CO_EmptySpaceLine create(
            Connection conn, int coEmptyspaceId,
            String bomId, int bomLineSeq, String bomLineRole, int bomLevel,
            double beforeXMm, double beforeYMm, double beforeZMm,
            double nextXMm, double nextYMm, double nextZMm,
            double capacityMm, String locatorRef)
            throws SQLException {
        M_CO_EmptySpaceLine line = new M_CO_EmptySpaceLine(conn);
        line.setCoEmptyspaceId(coEmptyspaceId);
        line.setBomId(bomId);
        line.setBomLineSeq(bomLineSeq);
        line.setBomLineRole(bomLineRole);
        line.setBomLevel(bomLevel);
        line.setBeforeXMm(beforeXMm);
        line.setBeforeYMm(beforeYMm);
        line.setBeforeZMm(beforeZMm);
        line.setNextXMm(nextXMm);
        line.setNextYMm(nextYMm);
        line.setNextZMm(nextZMm);
        line.setCapacityMm(capacityMm);
        line.setFilledMm(capacityMm);  // single-choice: fully consumed
        line.setRemainingMm(0.0);
        line.setLocatorRef(locatorRef);
        line.setDocStatus("DR");
        return line;
    }

    /**
     * Create the trivial top-level acceptance line for SH/DX:
     * one line accepting the full UNIT BOM into the building AABB.
     * before = origin, next = origin + AABB, capacity = AABB width.
     */
    public static M_CO_EmptySpaceLine createTopLevel(
            Connection conn, int coEmptyspaceId,
            String unitBomId,
            double originXMm, double originYMm, double originZMm,
            double widthMm, double depthMm, double heightMm)
            throws SQLException {
        return create(conn, coEmptyspaceId,
            unitBomId, 0, "BUILDING", 0,
            originXMm, originYMm, originZMm,
            originXMm + widthMm, originYMm + depthMm, originZMm + heightMm,
            widthMm, "FLOAT");
    }

    // ── DocStatus transitions ────────────────────────────────────────────────

    /** DR → CO: BOM accepted into this location. */
    public void setComplete() {
        setDocStatus("CO");
    }

    // ── Query ────────────────────────────────────────────────────────────────

    /** All lines for a given CO_EmptySpace header. */
    public static List<M_CO_EmptySpaceLine> getByHeader(
            Connection conn, int coEmptyspaceId) throws SQLException {
        return new ModelQuery<>(conn, M_CO_EmptySpaceLine::new, Table_Name)
            .where(COLUMNNAME_co_emptyspace_id + " = ?", coEmptyspaceId)
            .orderBy(COLUMNNAME_bom_line_seq + " ASC")
            .list();
    }

    /** All lines for a storey within a CO_EmptySpace. */
    public static List<M_CO_EmptySpaceLine> getByStorey(
            Connection conn, int coEmptyspaceId, String storey) throws SQLException {
        return new ModelQuery<>(conn, M_CO_EmptySpaceLine::new, Table_Name)
            .where(COLUMNNAME_co_emptyspace_id + " = ?", coEmptyspaceId)
            .andWhere(COLUMNNAME_storey + " = ?", storey)
            .orderBy(COLUMNNAME_bom_line_seq + " ASC")
            .list();
    }
}
