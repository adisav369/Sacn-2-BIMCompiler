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
     * Activate validation for a jurisdiction.
     * Loads applicable AD_Val_Rule set from validation.db.
     *
     * @param jurisdiction ISO country code: "MY", "US", "UK", "AU", "SG"
     * @param valConn      connection to validation.db
     */
    void activate(String jurisdiction, Connection valConn);

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

    /** @return true if a jurisdiction is currently active */
    boolean isActive();

    /** @return the active jurisdiction, or null if deactivated */
    String getJurisdiction();
}
