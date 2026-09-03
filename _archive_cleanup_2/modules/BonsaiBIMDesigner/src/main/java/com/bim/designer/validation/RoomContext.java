package com.bim.designer.validation;

/**
 * RoomContext — rectangular AABB container for building elements.
 *
 * <p>A room has fixed bounds (minX/Y/Z to maxX/Y/Z) from the DesignBBox.
 * Elevation is constant: storey Z level. Fit check is simple AABB containment.
 *
 * <p>This is the existing building-mode placement model, now expressed as a
 * PlacementContext for uniform treatment alongside infrastructure contexts.
 *
 * // Implementing INFRA_DESIGNER_SRS.md §5.2 — Witness: W-CONTEXT-ROOM-1
 */
public class RoomContext implements PlacementContext {

    private final Bounds bounds;
    private final double storeyZ;

    /**
     * @param minX  room min X (mm)
     * @param minY  room min Y (mm)
     * @param minZ  room min Z (mm) — storey level
     * @param maxX  room max X (mm)
     * @param maxY  room max Y (mm)
     * @param maxZ  room max Z (mm) — storey level + ceiling height
     */
    public RoomContext(double minX, double minY, double minZ,
                       double maxX, double maxY, double maxZ) {
        this.bounds = new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        this.storeyZ = minZ;
    }

    @Override
    public boolean fits(double widthMm, double depthMm, double heightMm,
                        double xMm, double yMm) {
        return xMm >= bounds.minX() && (xMm + widthMm) <= bounds.maxX()
            && yMm >= bounds.minY() && (yMm + depthMm) <= bounds.maxY()
            && heightMm <= bounds.height();
    }

    @Override
    public double elevationAt(double xMm, double yMm) {
        return storeyZ;  // constant — room floor level
    }

    @Override
    public Bounds bounds() { return bounds; }

    @Override
    public String contextType() { return "ROOM"; }
}
