package com.bim.compiler.contract;

import java.util.List;

/**
 * Layer 5: Semantic Contract - Domain-specific validation rules.
 * Enforces building codes, standards, and design rules.
 *
 * <p>Maps to: IfcConstraint, bSDD (buildingSMART Data Dictionary) constraints
 * <p>Theory: Specification pattern (DDD)
 *
 * <p>This is the top layer of the contract hierarchy. Elements implementing
 * this interface can validate themselves against domain rules during
 * compilation and witness generation.
 *
 * <p>Example validations:
 * <ul>
 *   <li>Bathroom must have ventilation (UBBL requirement)</li>
 *   <li>Stair riser must be &le; 196mm (IRC R311.7)</li>
 *   <li>Fire travel distance must be &le; 30m (UBBL 166)</li>
 *   <li>Accessible toilet door must be &ge; 900mm (MS 1184)</li>
 * </ul>
 *
 * @see <a href="https://standards.buildingsmart.org/IFC/RELEASE/IFC4/ADD2_TC1/HTML/schema/ifcconstraintresource/lexical/ifcconstraint.htm">IFC4 IfcConstraint</a>
 */
public interface IValidatable extends IAggregatable {

    /**
     * Validate this element against domain rules.
     * Called during compilation and witness generation.
     *
     * <p>Contract: Must check all applicable rules for this element type.
     * Returns empty list if all validations pass, otherwise returns
     * list of violations.
     *
     * <p>Violations are categorized by severity:
     * <ul>
     *   <li>ERROR: Blocks compilation</li>
     *   <li>WARNING: Generates witness note</li>
     *   <li>INFO: Informational only</li>
     * </ul>
     *
     * <p>Example implementation:
     * <pre>
     * public List&lt;Violation&gt; validate(ValidationContext ctx) {
     *     List&lt;Violation&gt; violations = new ArrayList&lt;&gt;();
     *
     *     if (riserHeight &gt; 0.196) {
     *         violations.add(Violation.error("IRC_R311.7",
     *             "Riser height " + riserHeight + "m exceeds maximum 0.196m",
     *             guid()));
     *     }
     *
     *     return violations;
     * }
     * </pre>
     *
     * @param ctx Validation context with building info and registry access
     * @return List of violations (empty if valid)
     */
    List<Violation> validate(ValidationContext ctx);
}
