package com.bim.cobol.forge;

import java.util.Map;

/**
 * A single computed geometry piece with BOM traceability and fabrication data.
 *
 * // Implementing GEOMETRY_FORGE_SRS.md §5.2
 */
public record GeometryRecord(
    String bomLineId,       // traces to M_BOM_Line
    String productId,       // traces to M_Product
    double lengthMm,
    double widthMm,
    double depthMm,
    Map<String, Double> fabrication,  // cut_angle_top, cut_angle_bottom, notch_depth, etc.
    double placementX,      // world coordinates (meters)
    double placementY,
    double placementZ,
    double rotation         // degrees
) {}
