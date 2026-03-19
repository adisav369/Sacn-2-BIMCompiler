package com.bim.backoffice.model;

/**
 * A bounding box from the design-mode layout generator.
 *
 * <p>Carries enough metadata for future commit migration into
 * {@code m_bom} / {@code m_bom_line} rows and Blender Outliner entries.
 * See BIM_Designer.md §17.5 for the full contract.
 *
 * @param bomId        unique ID within this layout (e.g. "FLOOR_GF", "ROOM_LI_01")
 * @param name         human label (e.g. "Ground Floor", "Living Room")
 * @param bomType      BUILDING | FLOOR | ROOM
 * @param category     null | LIVING | KITCHEN | BEDROOM | BATHROOM | CORRIDOR | BALCONY
 * @param ifcClass     IfcBuilding | IfcBuildingStorey | IfcSpace
 * @param storey       GF | FF | S2 ... | null (for BUILDING level)
 * @param parentBomId  parent in BOM tree (null for root)
 * @param minX         millimetres, site-local origin
 * @param minY         millimetres
 * @param minZ         millimetres
 * @param maxX         millimetres
 * @param maxY         millimetres
 * @param maxZ         millimetres
 */
public record DesignBBox(
        String bomId,
        String name,
        String bomType,
        String category,
        String ifcClass,
        String storey,
        String parentBomId,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ
) {}
