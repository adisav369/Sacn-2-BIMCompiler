package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code PP_Order_Node} — verb invocations for a building.
 *
 * <p>FIRST PRINCIPLE (§11.9): PP_Order_Node = HOW. Production operations.
 */
public class M_PP_Order_Node extends X_PP_Order_Node {

    public M_PP_Order_Node(Connection conn) { super(conn); }

    /** Load by PK. Returns null if not found. */
    public static M_PP_Order_Node get(Connection conn, int id) throws SQLException {
        M_PP_Order_Node r = new M_PP_Order_Node(conn);
        return r.load(id) ? r : null;
    }

    /** All active verb nodes for a building, ordered by SeqNo. */
    public static List<M_PP_Order_Node> getByOrder(Connection conn, String cOrderId)
            throws SQLException {
        return new ModelQuery<>(conn, M_PP_Order_Node::new, Table_Name)
            .where(COLUMNNAME_C_Order_ID + " = ?", cOrderId)
            .andWhere(COLUMNNAME_IsActive + " = ?", 1)
            .orderBy(COLUMNNAME_SeqNo)
            .list();
    }

    /** All verb nodes targeting a specific ESLine (S_Resource). */
    public static List<M_PP_Order_Node> getByResource(Connection conn, int sResourceId)
            throws SQLException {
        return new ModelQuery<>(conn, M_PP_Order_Node::new, Table_Name)
            .where(COLUMNNAME_S_Resource_ID + " = ?", sResourceId)
            .andWhere(COLUMNNAME_IsActive + " = ?", 1)
            .orderBy(COLUMNNAME_SeqNo)
            .list();
    }
}
