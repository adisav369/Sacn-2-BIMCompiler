package com.bim.compiler.contract;

/**
 * Role of a component within an assembly.
 * Critical for determining boundary behavior and ownership semantics.
 *
 * <p>This enum is key to solving the "boundary ownership" problem.
 * Components marked as BOUNDARY or SHARED should reference shared
 * elements from the registry rather than being created by the assembly.
 *
 * <p>Maps to implicit semantics in IfcRelAggregates and IfcElementAssembly.
 * <p>Theory: Part-whole relationships in mereology (Casati & Varzi 1999)
 */
public enum ComponentRole {
    /**
     * Fully owned by parent assembly, never shared.
     * These components are created by the assembly and exist only
     * within that assembly's context.
     *
     * <p>Examples: intermediate studs, middle noggings, internal blocking
     */
    INTERNAL,

    /**
     * At assembly boundary, potentially shared with adjacent assemblies.
     * These components should be obtained from SharedElementRegistry
     * rather than created directly.
     *
     * <p>Examples: corner studs, end studs at wall junctions,
     * boundary plates at floor edges
     */
    BOUNDARY,

    /**
     * Explicitly shared with other assemblies by design.
     * Always obtained from SharedElementRegistry, never created
     * by individual assemblies.
     *
     * <p>Examples: party wall studs, shared columns at unit boundaries,
     * common MEP risers
     */
    SHARED,

    /**
     * Placed by parent assembly but independent entity.
     * The assembly provides location/hosting but the component
     * has its own identity and lifecycle.
     *
     * <p>Examples: doors in walls, fixtures in rooms,
     * outlets on wall surfaces
     */
    HOSTED
}
