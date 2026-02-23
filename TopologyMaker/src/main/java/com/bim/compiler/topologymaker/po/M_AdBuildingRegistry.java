package com.bim.compiler.topologymaker.po;

import java.sql.Connection;
import java.util.List;

/**
 * Model + business logic layer for {@code ad_building_registry}.
 *
 * <p>Implements the DocStatus lifecycle — mirrors the iDempiere C_Order pattern:
 * <ul>
 *   <li>DR (Draft) — initial state for generative buildings</li>
 *   <li>IP (In Progress) — compilation started, violations detected</li>
 *   <li>CO (Complete) — fully compiled, no violations</li>
 *   <li>VO (Void) — cancelled, inactive</li>
 * </ul>
 *
 * <p>Adds:
 * <ul>
 *   <li>{@link #completeIt()} — DR/IP → CO transition</li>
 *   <li>{@link #voidIt()} — any → VO transition, sets is_active=false</li>
 *   <li>{@link #beforeSave(boolean)} — default doc_status and provenance for new records</li>
 * </ul>
 */
public class M_AdBuildingRegistry extends X_AdBuildingRegistry {

    private static final List<String> VALID_DOC_STATUSES = List.of("DR", "IP", "CO", "VO");

    public M_AdBuildingRegistry(Connection conn) { super(conn); }

    /**
     * Transition from DR or IP to CO.
     * Returns null on success (iDempiere convention), or an error message.
     */
    public String completeIt() {
        String ds = getDocStatus();
        if (!"IP".equals(ds) && !"DR".equals(ds))
            return "Cannot complete: current status is " + ds;
        setDocStatus("CO");
        return null;
    }

    /**
     * Transition to VO and deactivate.
     * Returns null on success, or an error message if already voided.
     */
    public String voidIt() {
        if ("VO".equals(getDocStatus()))
            return "Already voided";
        setDocStatus("VO");
        setIsActive(false);
        return null;
    }

    @Override
    protected void beforeSave(boolean newRecord) {
        // Apply defaults on new records before validation
        if (newRecord) {
            if (getDocStatus() == null) setDocStatus("DR");
            if (getProvenance() == null) setProvenance("GENERATIVE");
        }
        // doc_status CHECK constraint mirror: DR|IP|CO|VO
        String ds = getDocStatus();
        if (!VALID_DOC_STATUSES.contains(ds))
            throw new IllegalStateException("Invalid doc_status: " + ds);
    }
}
