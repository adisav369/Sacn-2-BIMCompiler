package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code c_order}.
 * Adds factory methods and DocStatus lifecycle.
 */
public class MOrder extends X_C_Order {

    private static final List<String> VALID_DOC_STATUSES = List.of("DR", "IP", "CO", "VO");

    public MOrder(Connection conn) { super(conn); }

    /** Load by C_Order_ID. Returns null if not found. */
    public static MOrder get(Connection conn, String cOrderId)
            throws SQLException {
        MOrder reg = new MOrder(conn);
        return reg.load(cOrderId) ? reg : null;
    }

    /** All active buildings, ordered by SeqNo. */
    public static List<MOrder> getAll(Connection conn) throws SQLException {
        return new ModelQuery<>(conn, MOrder::new, Table_Name)
            .where(COLUMNNAME_IsActive + " = ?", 1)
            .orderBy(COLUMNNAME_SeqNo)
            .list();
    }

    /** Transition DR/IP → CO. Returns null on success, error message on failure. */
    public String completeIt() {
        String ds = getDocStatus();
        if (!"IP".equals(ds) && !"DR".equals(ds))
            return "Cannot complete: current status is " + ds;
        setDocStatus("CO");
        return null;
    }

    /** Transition any → VO + deactivate. Returns null on success. */
    public String voidIt() {
        if ("VO".equals(getDocStatus())) return "Already voided";
        setDocStatus("VO");
        setIsActive(false);
        return null;
    }

    @Override
    protected void beforeSave(boolean newRecord) {
        if (newRecord) {
            if (getDocStatus() == null) setDocStatus("DR");
            if (getProvenance() == null) setProvenance("GENERATIVE");
        }
        String ds = getDocStatus();
        if (!VALID_DOC_STATUSES.contains(ds))
            throw new IllegalStateException("Invalid DocStatus: " + ds);
    }
}
