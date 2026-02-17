package com.bim.compiler.contract;

/**
 * Contract for elements that are hosted by other elements.
 * Specializes the hostedBy() relationship from IRelatable with host type awareness.
 *
 * <p>Maps to: IfcRelFillsElement (doors in walls), IfcRelContainedInSpatialStructure (fixtures in rooms)
 *
 * <p>Host Types:
 * <ul>
 *   <li>WALL - doors, windows, outlets, switches</li>
 *   <li>CEILING - lights, smoke detectors, sprinklers</li>
 *   <li>FLOOR - floor drains, floor outlets</li>
 *   <li>ROOM - freestanding fixtures (toilet, sink)</li>
 * </ul>
 *
 * @see IRelatable#hostedBy()
 */
public interface IHostable extends IBIMEntity {

    /**
     * Type of element that hosts this one.
     */
    HostType hostType();

    /**
     * GUID of the hosting element.
     * Must reference a valid element of the correct hostType().
     *
     * @return host element GUID, never null for hosted elements
     */
    String hostGuid();

    /**
     * Face of host where this element attaches.
     *
     * @return attachment face
     */
    AttachmentFace attachmentFace();

    /**
     * Offset from host surface in meters.
     * Positive = away from host surface.
     *
     * @return offset distance (0.0 for flush mount)
     */
    default double surfaceOffset() {
        return 0.0;
    }

    /**
     * Whether this element creates an opening in its host.
     * True for doors/windows in walls, false for surface-mounted elements.
     */
    default boolean createsOpening() {
        return false;
    }

    /**
     * Types of elements that can act as hosts.
     */
    enum HostType {
        WALL,       // Vertical surface (doors, windows, outlets)
        CEILING,    // Overhead surface (lights, sprinklers)
        FLOOR,      // Ground surface (floor drains)
        ROOM,       // Spatial container (freestanding fixtures)
        BEAM,       // Structural beam (hangers, supports)
        COLUMN      // Structural column (brackets)
    }

    /**
     * Face of host element for attachment.
     */
    enum AttachmentFace {
        TOP,        // Top surface (floor-mounted)
        BOTTOM,     // Bottom surface (ceiling-mounted)
        NORTH,      // North-facing wall surface
        SOUTH,      // South-facing wall surface
        EAST,       // East-facing wall surface
        WEST,       // West-facing wall surface
        INTERIOR,   // Interior side of exterior wall
        EXTERIOR,   // Exterior side of exterior wall
        CENTER      // Centered in opening (doors, windows)
    }
}
