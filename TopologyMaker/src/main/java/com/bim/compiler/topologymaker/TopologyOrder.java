package com.bim.compiler.topologymaker;

/**
 * A topology generation request — analogous to C_Order in iDempiere.
 *
 * @param orderId     Unique identifier; becomes the building_id in ad_building_registry
 * @param typologyId  FK to ad_typology_pattern.typology_id (e.g. "TERRACE_MY_1S")
 * @param site        Site brief (dimensions, bedroom count, porch flag)
 * @param status      Initial DocStatus — should be DR or IP
 */
public record TopologyOrder(
        String orderId,
        String typologyId,
        SiteEnvelope site,
        DocStatus status
) {
    public TopologyOrder {
        if (orderId == null || orderId.isBlank())
            throw new IllegalArgumentException("orderId must not be blank");
        if (typologyId == null || typologyId.isBlank())
            throw new IllegalArgumentException("typologyId must not be blank");
        if (site == null) throw new IllegalArgumentException("site must not be null");
        if (status == null) throw new IllegalArgumentException("status must not be null");
    }
}
