package com.bim.compiler.topologymaker.po;

import com.bim.compiler.topologymaker.db.TopologyAccessLayer;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Model + business logic layer for {@code ad_typology_pattern}.
 *
 * <p>Adds:
 * <ul>
 *   <li>{@link #get(Connection, String)} — factory: load by PK, return null if inactive</li>
 *   <li>{@link #beforeSave(boolean)} — validate zone_json + dimensions</li>
 *   <li>{@link #toPattern()} — bridge to TopologyAccessLayer.TypologyPattern DTO</li>
 * </ul>
 */
public class M_AdTypologyPattern extends X_AdTypologyPattern {

    public M_AdTypologyPattern(Connection conn) { super(conn); }

    /**
     * Factory: load by typology_id. Returns null if not found or not active.
     *
     * @param conn       Open connection (caller manages lifecycle)
     * @param typologyId PK to look up
     * @return Loaded M_ object, or null
     */
    public static M_AdTypologyPattern get(Connection conn, String typologyId)
            throws SQLException {
        M_AdTypologyPattern p = new M_AdTypologyPattern(conn);
        return p.load(typologyId) && p.isActive() ? p : null;
    }

    @Override
    protected void beforeSave(boolean newRecord) {
        String zj = getZoneJson();
        if (zj == null || zj.isBlank())
            throw new IllegalStateException("zone_json must not be blank");
        if (getBaseWidthMm() <= 0)
            throw new IllegalStateException("base_width_mm must be positive");
        if (getBaseDepthMm() <= 0)
            throw new IllegalStateException("base_depth_mm must be positive");
    }

    /**
     * Convert this PO to the outbound DTO used by the strategy layer.
     * This is the bridge between the PO layer and GridStrategy callers.
     */
    public TopologyAccessLayer.TypologyPattern toPattern() {
        return new TopologyAccessLayer.TypologyPattern(
            getTypologyId(),
            getGridStrategy(),
            getUbblClass(),
            getBaseWidthMm(),
            getBaseDepthMm(),
            getZoneJson()
        );
    }
}
