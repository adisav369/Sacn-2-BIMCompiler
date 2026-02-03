package com.bim.compiler.contract;

/**
 * Contract for components that must fit within parent assemblies.
 * Extends IPhysicalComponent with containment requirements.
 *
 * <p>Theory: IfcRelAggregates with geometric validation
 *
 * <p>This contract ensures that LOD400 components don't exceed
 * their parent assembly bounds, which would cause fabrication errors.
 *
 * <p>Example validation:
 * <pre>
 * IAssemblyMember stud = ...;
 * IBIMEntity wallFrame = registry.get(stud.fitsWithin());
 *
 * if (!wallFrame.bounds().contains(stud.bounds(), stud.fitTolerance())) {
 *     return new Violation(ERROR, "LOD400_FIT",
 *         "Stud exceeds wall frame bounds by " + overflow + "mm",
 *         stud.guid());
 * }
 * </pre>
 */
public interface IAssemblyMember extends IPhysicalComponent {

    /**
     * Parent assembly this component must fit within.
     * Returns assembly GUID for bounds checking.
     *
     * <p>Contract: The component's bounds (with fitTolerance) must be
     * contained within the parent assembly's bounds.
     *
     * @return GUID of parent assembly
     */
    String fitsWithin();

    /**
     * Fit tolerance - allowed gap between component and parent bounds.
     * Zero means exact fit; positive allows gap.
     *
     * <p>Example: Stud in wall frame allows 2mm gap = 0.002
     *
     * <p>Contract: Must be non-negative. Verification uses:
     * {@code parentBounds.contains(componentBounds.expand(-fitTolerance))}
     *
     * @return tolerance in meters
     */
    double fitTolerance();

    /**
     * Fastener specification for attaching this component to parent.
     *
     * <p>From AS 1684: stud-to-plate = 2x 75mm framing nails, skew-nailed.
     *
     * <p>Contract: Must match framing code requirements for the
     * component type and parent assembly type.
     *
     * @return fastener specification
     */
    FastenerSpec fastenerSpec();
}
