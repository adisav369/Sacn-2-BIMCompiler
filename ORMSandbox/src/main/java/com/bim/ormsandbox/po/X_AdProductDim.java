package com.bim.ormsandbox.po;

import java.sql.Connection;

/**
 * @deprecated Renamed to {@link X_MProduct}. Table is now {@code M_Product}.
 *             Update all references: X_AdProductDim → X_MProduct.
 */
@Deprecated
public class X_AdProductDim extends X_MProduct {
    public X_AdProductDim(Connection conn) { super(conn); }
}
