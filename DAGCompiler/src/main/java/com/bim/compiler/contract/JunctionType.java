package com.bim.compiler.contract;

/**
 * Type of junction point where elements meet.
 *
 * <p>Maps to IfcRelConnectsPathElements connection semantics.
 * <p>Theory: Graph theory (vertex degree), architectural junctions
 */
public enum JunctionType {
    /**
     * L-junction: 2 elements meeting at 90 degrees.
     * Typical wall corner.
     */
    CORNER,

    /**
     * T-junction: 3 elements meeting, one continuing straight.
     * Wall partition joining exterior wall.
     */
    T_JUNCTION,

    /**
     * Cross junction: 4 elements meeting.
     * Intersection of two walls.
     */
    CROSS,

    /**
     * Pipe/duct intersection point.
     * MEP elements crossing or branching.
     */
    INTERSECTION,

    /**
     * Dead end: single element terminating.
     * Wall end not connected to another wall.
     */
    TERMINATION,

    /**
     * In-line splice: 2 elements meeting end-to-end.
     * Long wall constructed from multiple segments.
     */
    SPLICE
}
