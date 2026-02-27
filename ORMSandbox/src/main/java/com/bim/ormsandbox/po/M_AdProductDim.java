package com.bim.ormsandbox.po;

import java.sql.Connection;

/**
 * @deprecated Renamed to {@link MProduct}. Table is now {@code M_Product}.
 *             Replace all usages: M_AdProductDim → MProduct.
 *             Static methods: use MProduct.get() / MProduct.getAll() / MProduct.getByType().
 */
@Deprecated
public class M_AdProductDim extends MProduct {
    public M_AdProductDim(Connection conn) { super(conn); }
}
