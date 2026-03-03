package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code PP_Order_NodeProduct} — structured parameters per verb.
 */
public class M_PP_Order_NodeProduct extends X_PP_Order_NodeProduct {

    public M_PP_Order_NodeProduct(Connection conn) { super(conn); }

    /** All parameters for a given PP_Order_Node. */
    public static List<M_PP_Order_NodeProduct> getByNode(Connection conn, int nodeId)
            throws SQLException {
        return new ModelQuery<>(conn, M_PP_Order_NodeProduct::new, Table_Name)
            .where(COLUMNNAME_PP_Order_Node_ID + " = ?", nodeId)
            .orderBy(COLUMNNAME_Name)
            .list();
    }
}
