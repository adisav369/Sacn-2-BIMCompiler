package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code c_orderline} — WHAT to build (order topics ONLY).
 *
 * <p>Adds factory methods for building-level lookups. All queries use
 * WHAT-only columns. No placement or material column access.
 *
 * <p>FIRST PRINCIPLE (§11.9): Placement → PP_Order_Node. Material → M_Product.
 */
public class MOrderLine extends X_C_OrderLine {

    public MOrderLine(Connection conn) { super(conn); }

    /** Load by integer PK. Returns null if not found. */
    public static MOrderLine get(Connection conn, int id) throws SQLException {
        MOrderLine r = new MOrderLine(conn);
        return r.load(id) ? r : null;
    }

    /**
     * All active order lines for a building, ordered by C_OrderLine_ID.
     */
    public static List<MOrderLine> getByBuilding(Connection conn, String cOrderId)
            throws SQLException {
        return new ModelQuery<>(conn, MOrderLine::new, Table_Name)
            .where(COLUMNNAME_C_Order_ID + " = ?", cOrderId)
            .andWhere(COLUMNNAME_IsActive + " = ?", 1)
            .orderBy(COLUMNNAME_C_OrderLine_ID)
            .list();
    }

    /** Order lines for a specific storey within a building. */
    public static List<MOrderLine> getByBuildingStorey(
            Connection conn, String cOrderId, String storey) throws SQLException {
        return new ModelQuery<>(conn, MOrderLine::new, Table_Name)
            .where(COLUMNNAME_C_Order_ID + " = ?", cOrderId)
            .andWhere(COLUMNNAME_Storey + " = ?", storey)
            .andWhere(COLUMNNAME_IsActive + " = ?", 1)
            .orderBy(COLUMNNAME_C_OrderLine_ID)
            .list();
    }

    // ── getFloorRules REMOVED — used host_type filter (placement column) ──
    // Floor rules are a PP_Order_Node concern, not c_orderline.
    // ── Nullable getters REMOVED — placement/material data not on this PO ──
}
