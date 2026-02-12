package com.bim.compiler.contract;

import java.util.List;

/**
 * Phase 118: OSGi-inspired construction worker interface.
 *
 * <p>Each worker handles one theme (furniture, fixtures, MEP, etc.),
 * anchors to one face of the room, and returns placed elements.
 * Workers don't know each other — they communicate only through
 * the room envelope and spatial reservation system.
 *
 * <h3>Construction Site Metaphor</h3>
 * <pre>
 * 1. Worker ARRIVES with their theme ID
 * 2. Worker READS the room envelope (bounds, faces, reserved zones)
 * 3. Worker PLACES elements by face alignment + dimension properties
 * 4. Worker RESERVES their clearance envelope for next worker
 * 5. Worker LEAVES — no coupling to other workers
 * </pre>
 *
 * <h3>Dispatch</h3>
 * Workers are registered in {@code ad_room_slot} by theme ID and room type.
 * The slot dispatcher calls workers in priority order, passing reserved zones
 * from prior workers.
 *
 * @see com.bim.compiler.contract.CatalogContract
 */
public interface BundleWorker {

    /**
     * Theme ID matching ad_room_slot.assembly_id.
     * Examples: "BED_SET", "BATHROOM_WC_SET", "KITCHEN_COUNTER_SET", "MEP_CEILING_SET"
     */
    String themeId();

    /**
     * Which face this worker anchors to.
     * Values: "BACK", "FRONT", "LEFT", "RIGHT", "TOP", "BOTTOM"
     *
     * <p>Face convention (from PREFAB_ARCHITECTURE.md):
     * FRONT = -Y (approach side), BACK = +Y (wall side),
     * LEFT = -X, RIGHT = +X, TOP = +Z, BOTTOM = -Z
     */
    String anchorFace();

    /**
     * Execute placement within room envelope.
     *
     * @param room     Room envelope with bounds, faces, and reserved zones
     * @param ctx      Shared context (storey Z, ceiling Z, construction system)
     * @return List of placed elements in world coordinates
     */
    List<PlacedElement> execute(RoomEnvelope room, PlacementContext ctx);

    // =========================================================================
    // Records — uniform data contracts for all workers
    // =========================================================================

    /**
     * Lightweight opening info — decoupled from OpeningSpec.
     * Any worker that needs opening awareness (e.g. wall scoring) uses this.
     */
    record OpeningInfo(String type, String wall, double width) {}

    /**
     * Room envelope — what every worker receives as input.
     * Standardized workspace with bounds, faces, and spatial reservations.
     */
    record RoomEnvelope(
        String roomName,
        String roomType,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ,
        List<OpeningInfo> openings,
        List<double[]> reservedZones  // prior workers' claimed bounding boxes [minX,minY,minZ,maxX,maxY,maxZ]
    ) {
        public double width()  { return maxX - minX; }
        public double depth()  { return maxY - minY; }
        public double height() { return maxZ - minZ; }
    }

    /**
     * Shared context passed to all workers for a storey.
     */
    record PlacementContext(
        double storeyZ,
        double ceilingZ,
        double slabThickness,
        String constructionSystem
    ) {}

    /**
     * Uniform output from every worker — one placed element.
     */
    record PlacedElement(
        String name,
        String ifcClass,
        double x, double y, double z,
        double width, double depth, double height,
        double rotation,
        String geometryHash,
        String role,
        String assemblyId
    ) {}
}
