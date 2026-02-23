package com.bim.ormsandbox.po;

import com.bim.orm.ModelQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Model layer for {@code ad_building_registry}.
 * Adds factory methods and DocStatus lifecycle.
 */
public class M_AdBuildingRegistry extends X_AdBuildingRegistry {

    private static final List<String> VALID_DOC_STATUSES = List.of("DR", "IP", "CO", "VO");

    public M_AdBuildingRegistry(Connection conn) { super(conn); }

    /** Load by building_id. Returns null if not found. */
    public static M_AdBuildingRegistry get(Connection conn, String buildingId)
            throws SQLException {
        M_AdBuildingRegistry reg = new M_AdBuildingRegistry(conn);
        return reg.load(buildingId) ? reg : null;
    }

    /** All active buildings, ordered by seq_no. */
    public static List<M_AdBuildingRegistry> getAll(Connection conn) throws SQLException {
        return new ModelQuery<>(conn, M_AdBuildingRegistry::new, Table_Name)
            .where(COLUMNNAME_is_active + " = ?", 1)
            .orderBy(COLUMNNAME_seq_no)
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
            throw new IllegalStateException("Invalid doc_status: " + ds);
    }
}
