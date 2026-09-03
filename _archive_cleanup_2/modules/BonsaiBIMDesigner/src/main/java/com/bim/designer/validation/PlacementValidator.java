package com.bim.designer.validation;

import java.sql.Connection;

/**
 * PlacementValidator — DocValidate §9.4, OSGi-style ModelValidator.
 *
 * <p>Loosely coupled, highly cohesive. This component:
 * <ul>
 *   <li>Accepts geometry constructs with semantics (category, dimensions, position)</li>
 *   <li>Applies validation rules from validation.db (AD_Val_Rule)</li>
 *   <li>Returns PASS/BLOCK/ADJUST — never invents geometry, only constrains it</li>
 * </ul>
 *
 * <p>Separation layer: the validator does not know where geometry came from
 * (compiler, generative path, Blender bridge, manual edit). It receives a
 * {@link PlacementRequest}, checks it against rules for the given jurisdiction,
 * and returns a {@link ValidationVerdict}.
 *
 * <p>The verbs it knows are construction verbs: SNAP, SCREW, JOIN, TILE, ROUTE —
 * these constrain how elements attach and space. DocValidate doesn't invent
 * placements; it validates that the verb-driven placement is code-compliant.
 *
 * <p>Activation: like iDempiere OSGi, the validator activates per jurisdiction.
 * {@code activate("MY")} loads UBBL rules. {@code activate("US")} loads IRC.
 * {@code deactivate()} = extracted buildings compile without validation.
 */
public interface PlacementValidator {

    /**
     * Activate validation for a jurisdiction (building mode).
     * Loads applicable AD_Val_Rule set from validation.db.
     * Equivalent to {@code activate(jurisdiction, FacilityType.BUILDING, valConn)}.
     *
     * @param jurisdiction ISO country code: "MY", "US", "UK", "AU", "SG"
     * @param valConn      connection to validation.db
     */
    void activate(String jurisdiction, Connection valConn);

    /**
     * Activate validation for a jurisdiction + facility type.
     * Building mode: loads rules WHERE jurisdiction = ? AND provenance NOT LIKE 'Infra_%'.
     * Infrastructure mode: loads rules WHERE provenance = ? AND is_active = 1.
     *
     * @param jurisdiction ISO country code (used for BUILDING; ignored for infra)
     * @param facilityType facility type discriminator
     * @param valConn      connection to validation.db
     */
    void activate(String jurisdiction, FacilityType facilityType, Connection valConn);

    /**
     * Deactivate validation. Extracted buildings skip all checks.
     */
    void deactivate();

    /**
     * Validate a placement request against active rules.
     *
     * <p>The request carries semantic geometry (what it is, how big, where).
     * The validator returns a verdict:
     * <ul>
     *   <li>PASS — placement is code-compliant</li>
     *   <li>BLOCK — placement violates a rule (message says which)</li>
     *   <li>ADJUST — placement can be corrected (adjusted values provided)</li>
     * </ul>
     *
     * <p>When deactivated, always returns PASS.
     *
     * @param request the semantic placement to validate
     * @return verdict with rule reference, actual vs required values
     */
    ValidationVerdict validate(PlacementRequest request);

    /**
     * Load mined dimension rules from ERP.db (DV010).
     * These are DIMENSION_RANGE rules: typical W/D/H per ifc_class from 20+ buildings.
     * Fires as advisory WARNING when an element's dimensions deviate significantly
     * from observed patterns. Must be called after activate().
     *
     * @param discConn connection to ERP.db
     */
    void activateMinedRules(Connection discConn);

    /** @return true if a jurisdiction is currently active */
    boolean isActive();

    /** @return the active jurisdiction, or null if deactivated */
    String getJurisdiction();

    /** @return the number of active rules for the current jurisdiction, or 0 if inactive */
    int getRuleCount();

    /**
     * Get the minimum required value for a named rule parameter and category.
     * Used by click-to-fix to determine target dimension.
     *
     * @param category BOM category (BEDROOM, KITCHEN, etc.)
     * @param paramName rule parameter name (min_dim_mm, min_area_m2, etc.)
     * @return the required minimum, or -1 if no rule matches
     */
    double getMinimumForRule(String category, String paramName);
}
