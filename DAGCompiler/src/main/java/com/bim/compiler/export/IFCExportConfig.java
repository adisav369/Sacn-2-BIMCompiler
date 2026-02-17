package com.bim.compiler.export;

import com.bim.compiler.topology.BIMConstants;
import com.bim.compiler.topology.ConnectionPattern;
import com.bim.compiler.topology.OpeningConstraints;

/**
 * Configuration for IFC export.
 * Bundles typology settings and extracted patterns so Python exporter
 * is not hardcoded and can work with any building typology.
 *
 * Pattern: Java owns the extracted patterns (G1-G12), passes to Python as config.
 * This keeps Python generic and allows different typologies (Terminal, Residential, etc.)
 */
public record IFCExportConfig(
    // Typology identification
    String typologyName,
    String projectName,
    String siteName,
    String buildingName,
    String defaultStoreyName,

    // Coordinate system (FACT 2)
    double offsetX,
    double offsetY,
    double offsetZ,
    String unit,

    // Connection patterns (G4)
    double wallJunctionOverlapMeters,
    double pipeInsertionDepthCW,
    double pipeInsertionDepthFP,
    double pipeInsertionDepthLPG,
    double pipeInsertionDepthSP,

    // Opening constraints (G7)
    double openingMaxWidthRatio,
    double openingMaxHeightRatio,
    double openingMinEdgeDistance,

    // Construction rules
    double tolerance,
    double mepStructureClearance
) {

    /**
     * Create default config for Terminal typology.
     * Uses values extracted from enhanced_federation_GI.db.
     */
    public static IFCExportConfig terminal() {
        return new IFCExportConfig(
            "Terminal",
            "Terminal Building",
            "Airport Site",
            "Terminal 1",
            "Ground Floor",

            BIMConstants.OFFSET_X,
            BIMConstants.OFFSET_Y,
            BIMConstants.OFFSET_Z,
            BIMConstants.UNIT,

            ConnectionPattern.WALL_JUNCTION_OVERLAP_METERS,
            ConnectionPattern.getPipeInsertionDepth(
                com.bim.compiler.topology.Discipline.CW),
            ConnectionPattern.getPipeInsertionDepth(
                com.bim.compiler.topology.Discipline.FP),
            ConnectionPattern.getPipeInsertionDepth(
                com.bim.compiler.topology.Discipline.LPG),
            ConnectionPattern.getPipeInsertionDepth(
                com.bim.compiler.topology.Discipline.SP),

            OpeningConstraints.MAX_WIDTH_RATIO,
            OpeningConstraints.MAX_HEIGHT_RATIO,
            OpeningConstraints.MIN_EDGE_DISTANCE,

            BIMConstants.TOLERANCE,
            BIMConstants.MEP_STRUCTURE_CLEARANCE
        );
    }

    /**
     * Create config for Residential typology (future use).
     * Different patterns for smaller buildings.
     */
    public static IFCExportConfig residential() {
        return new IFCExportConfig(
            "Residential",
            "Residential Building",
            "Development Site",
            "Building A",
            "Level 1",

            0.0, 0.0, 0.0,  // No global offset for residential
            "METERS",

            0.150,  // Wall junction overlap (single thickness)
            0.015, 0.015, 0.015, 0.015,  // Standard pipe insertion

            0.80,   // Larger openings allowed (windows)
            0.90,   // Taller openings allowed
            0.150,  // More edge distance

            0.005,
            0.050
        );
    }

    /**
     * Convert to JSON string for passing to Python exporter.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format("  \"typology\": \"%s\",\n", typologyName));
        sb.append(String.format("  \"project_name\": \"%s\",\n", projectName));
        sb.append(String.format("  \"site_name\": \"%s\",\n", siteName));
        sb.append(String.format("  \"building_name\": \"%s\",\n", buildingName));
        sb.append(String.format("  \"storey_name\": \"%s\",\n", defaultStoreyName));
        sb.append("  \"coordinate_system\": {\n");
        sb.append(String.format("    \"offset_x\": %.6f,\n", offsetX));
        sb.append(String.format("    \"offset_y\": %.6f,\n", offsetY));
        sb.append(String.format("    \"offset_z\": %.6f,\n", offsetZ));
        sb.append(String.format("    \"unit\": \"%s\"\n", unit));
        sb.append("  },\n");
        sb.append("  \"patterns\": {\n");
        sb.append("    \"G4_connection\": {\n");
        sb.append(String.format("      \"wall_junction_overlap\": %.6f,\n", wallJunctionOverlapMeters));
        sb.append(String.format("      \"pipe_insertion_CW\": %.6f,\n", pipeInsertionDepthCW));
        sb.append(String.format("      \"pipe_insertion_FP\": %.6f,\n", pipeInsertionDepthFP));
        sb.append(String.format("      \"pipe_insertion_LPG\": %.6f,\n", pipeInsertionDepthLPG));
        sb.append(String.format("      \"pipe_insertion_SP\": %.6f\n", pipeInsertionDepthSP));
        sb.append("    },\n");
        sb.append("    \"G7_opening\": {\n");
        sb.append(String.format("      \"max_width_ratio\": %.6f,\n", openingMaxWidthRatio));
        sb.append(String.format("      \"max_height_ratio\": %.6f,\n", openingMaxHeightRatio));
        sb.append(String.format("      \"min_edge_distance\": %.6f\n", openingMinEdgeDistance));
        sb.append("    }\n");
        sb.append("  },\n");
        sb.append("  \"rules\": {\n");
        sb.append(String.format("    \"tolerance\": %.6f,\n", tolerance));
        sb.append(String.format("    \"mep_structure_clearance\": %.6f\n", mepStructureClearance));
        sb.append("  }\n");
        sb.append("}");
        return sb.toString();
    }
}
