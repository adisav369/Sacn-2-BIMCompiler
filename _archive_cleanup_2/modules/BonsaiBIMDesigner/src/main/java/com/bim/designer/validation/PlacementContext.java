package com.bim.designer.validation;

/**
 * PlacementContext — abstract container constraint for element placement.
 *
 * <p>A PlacementContext answers two questions:
 * <ol>
 *   <li><b>Fit:</b> Can this element (W×D×H) fit at this position?</li>
 *   <li><b>Elevation:</b> What is the reference Z at this (X,Y) position?</li>
 * </ol>
 *
 * <p>Concrete implementations:
 * <ul>
 *   <li>{@link RoomContext} — rectangular AABB room. Z = storey level.
 *       Used for buildings: furniture fits inside room bounds.</li>
 *   <li>{@link AlignmentContext} — corridor along a polyline over terrain.
 *       Z = terrain elevation + super-elevation at station.
 *       Used for infrastructure: courses/sleepers/piers fit along alignment.</li>
 * </ul>
 *
 * <p>The Designer's snap() and placeItem() use PlacementContext to determine
 * whether an element fits and where its Z reference is. Building mode creates
 * RoomContext from DesignBBox. Infrastructure mode creates AlignmentContext
 * from terrain + alignment data.
 *
 * // Implementing INFRA_DESIGNER_SRS.md §1+§3 — Witness: W-CONTEXT-1
 */
public interface PlacementContext {

    /** @return true if an element of (w,d,h) mm fits at position (x,y) within this context */
    boolean fits(double widthMm, double depthMm, double heightMm,
                 double xMm, double yMm);

    /** @return the reference Z elevation (mm) at position (x,y) within this context */
    double elevationAt(double xMm, double yMm);

    /** @return the context bounding box — overall extent of the placement area */
    Bounds bounds();

    /** @return human-readable context type: "ROOM", "ALIGNMENT", "TERRAIN" */
    String contextType();

    /** Bounding box for the placement context. */
    record Bounds(double minX, double minY, double minZ,
                  double maxX, double maxY, double maxZ) {

        public double width()  { return maxX - minX; }
        public double depth()  { return maxY - minY; }
        public double height() { return maxZ - minZ; }
    }
}
