package com.bim.designer.validation;

/**
 * IFC4X3 facility type discriminator for validation rule scoping.
 *
 * <p>Building rules are jurisdiction-scoped (WHERE jurisdiction = ?).
 * Infrastructure rules are provenance-scoped (WHERE provenance = ?).
 * The provenance column in AD_Val_Rule already encodes facility type:
 * Infra_Bridge, Infra_Road, Infra_Rail.
 *
 * // Implementing DISC_VALIDATION_DB_SRS.md §Infra — Witness: W-INFRA-FILTER-1
 */
public enum FacilityType {
    BUILDING(null),              // jurisdiction-scoped rules, exclude Infra_*
    BRIDGE("Infra_Bridge"),      // provenance-scoped
    ROAD("Infra_Road"),
    RAILWAY("Infra_Rail"),
    TUNNEL("Infra_Tunnel"),      // future — no rules yet
    DAM("Infra_Dam"),            // future — no rules yet
    PORT("Infra_Port");          // future — no rules yet

    private final String provenance;

    FacilityType(String provenance) { this.provenance = provenance; }

    public String provenance() { return provenance; }

    public boolean isInfrastructure() { return provenance != null; }
}
