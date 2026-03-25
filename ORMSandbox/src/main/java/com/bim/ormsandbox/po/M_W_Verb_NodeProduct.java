package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code W_Verb_NodeProduct} — structured parameters per verb.
 */
public class M_W_Verb_NodeProduct extends X_W_Verb_NodeProduct {

    public M_W_Verb_NodeProduct(Connection conn) { super(conn); }

    /** All parameters for a given W_Verb_Node. */
    public static List<M_W_Verb_NodeProduct> getByNode(Connection conn, int nodeId)
            throws SQLException {
        return new ModelQuery<>(conn, M_W_Verb_NodeProduct::new, Table_Name)
            .where(COLUMNNAME_W_Verb_Node_ID + " = ?", nodeId)
            .orderBy(COLUMNNAME_Name)
            .list();
    }
}
