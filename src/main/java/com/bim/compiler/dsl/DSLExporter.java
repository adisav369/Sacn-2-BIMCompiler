package com.bim.compiler.dsl;

import com.bim.compiler.builder.OpeningSpec;
import com.bim.compiler.builder.WallSpec;
import com.bim.compiler.export.IFCExportConfig;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exports DSL compilation results to IFC.
 * Extends basic IFCExporter with IfcSpace support.
 */
public class DSLExporter {

    private static final String PYTHON_SCRIPT =
        "/home/red1/bim-compiler/scripts/export_dsl_to_ifc.py";
    private static final String PYTHON_PATH =
        "/home/red1/bim-compiler/venv/bin/python3";

    private final IFCExportConfig config;

    public DSLExporter(IFCExportConfig config) {
        this.config = config;
    }

    /**
     * Export complete storey to IFC.
     */
    public boolean exportStorey(ConstructionSpec spec, String outputPath) {
        try {
            // Create JSON for entire storey
            String json = constructionSpecToJson(spec);

            // Write to temp file
            Path tempJson = Files.createTempFile("storey_", ".json");
            Path tempConfig = Files.createTempFile("config_", ".json");
            Files.writeString(tempJson, json);
            Files.writeString(tempConfig, config.toJson());

            // Call Python script
            ProcessBuilder pb = new ProcessBuilder(
                PYTHON_PATH, PYTHON_SCRIPT,
                "--storey", tempJson.toString(),
                "--output", outputPath,
                "--config", tempConfig.toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);
            int exitCode = process.waitFor();

            // Cleanup
            Files.deleteIfExists(tempJson);
            Files.deleteIfExists(tempConfig);

            if (exitCode != 0) {
                System.err.println("IFC export failed: " + output);
                return false;
            }

            System.out.println(output);
            return true;

        } catch (Exception e) {
            System.err.println("IFC export error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Convert ConstructionSpec to JSON.
     */
    private String constructionSpecToJson(ConstructionSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format("  \"storey_name\": \"%s\",\n", spec.storeyName()));
        sb.append(String.format("  \"storey_height\": %.6f,\n", spec.storeyHeight()));

        // Walls
        sb.append("  \"walls\": [\n");
        for (int i = 0; i < spec.walls().size(); i++) {
            WallSpec wall = spec.walls().get(i);
            if (i > 0) sb.append(",\n");
            sb.append(wallToJson(wall, spec.storeyHeight()));
        }
        sb.append("\n  ],\n");

        // Spaces
        sb.append("  \"spaces\": [\n");
        for (int i = 0; i < spec.spaces().size(); i++) {
            ConstructionSpec.SpaceSpec space = spec.spaces().get(i);
            if (i > 0) sb.append(",\n");
            sb.append(spaceToJson(space));
        }
        sb.append("\n  ],\n");

        // Sprinkler grids
        sb.append("  \"sprinkler_grids\": [\n");
        for (int i = 0; i < spec.sprinklerGrids().size(); i++) {
            ConstructionSpec.SprinklerGridSpec grid = spec.sprinklerGrids().get(i);
            if (i > 0) sb.append(",\n");
            sb.append(sprinklerGridToJson(grid));
        }
        sb.append("\n  ]\n");

        sb.append("}");
        return sb.toString();
    }

    private String wallToJson(WallSpec wall, double storeyHeight) {
        StringBuilder sb = new StringBuilder();
        sb.append("    {\n");
        sb.append(String.format("      \"start\": {\"x\": %.6f, \"y\": %.6f, \"z\": 0.0},\n",
            wall.start().x(), wall.start().y()));
        sb.append(String.format("      \"end\": {\"x\": %.6f, \"y\": %.6f, \"z\": 0.0},\n",
            wall.end().x(), wall.end().y()));
        sb.append(String.format("      \"thickness\": %.6f,\n", wall.thickness().getMeters()));
        sb.append(String.format("      \"height\": %.6f,\n", storeyHeight));

        // Openings
        sb.append("      \"openings\": [");
        for (int i = 0; i < wall.openings().size(); i++) {
            OpeningSpec op = wall.openings().get(i);
            if (i > 0) sb.append(",");
            sb.append("\n        {");
            sb.append(String.format("\"offset\": %.6f, ", op.offsetAlongWall()));
            sb.append(String.format("\"width\": %.6f, ", op.width()));
            sb.append(String.format("\"height\": %.6f, ", op.height()));
            sb.append(String.format("\"sill_height\": %.6f, ", op.offsetFromFloor()));
            sb.append(String.format("\"type\": \"%s\"", op.type().name()));
            sb.append("}");
        }
        if (!wall.openings().isEmpty()) sb.append("\n      ");
        sb.append("]\n");
        sb.append("    }");
        return sb.toString();
    }

    private String spaceToJson(ConstructionSpec.SpaceSpec space) {
        StringBuilder sb = new StringBuilder();
        sb.append("    {\n");
        sb.append(String.format("      \"name\": \"%s\",\n", space.name()));
        sb.append(String.format("      \"type\": \"%s\",\n", space.type().name()));
        sb.append(String.format("      \"omniclass\": \"%s\",\n", space.type().getOmniClassCode()));
        sb.append(String.format("      \"min_x\": %.6f,\n", space.minX()));
        sb.append(String.format("      \"max_x\": %.6f,\n", space.maxX()));
        sb.append(String.format("      \"min_y\": %.6f,\n", space.minY()));
        sb.append(String.format("      \"max_y\": %.6f,\n", space.maxY()));
        sb.append(String.format("      \"height\": %.6f\n", space.height()));
        sb.append("    }");
        return sb.toString();
    }

    private String sprinklerGridToJson(ConstructionSpec.SprinklerGridSpec grid) {
        StringBuilder sb = new StringBuilder();
        sb.append("    {\n");
        sb.append(String.format("      \"room_name\": \"%s\",\n", grid.roomName()));
        sb.append(String.format("      \"min_x\": %.6f,\n", grid.area().minX()));
        sb.append(String.format("      \"max_x\": %.6f,\n", grid.area().maxX()));
        sb.append(String.format("      \"min_y\": %.6f,\n", grid.area().minY()));
        sb.append(String.format("      \"max_y\": %.6f,\n", grid.area().maxY()));
        sb.append(String.format("      \"spacing\": %.6f,\n", grid.spacing()));
        sb.append(String.format("      \"attachment_z\": %.6f\n", grid.attachmentZ()));
        sb.append("    }");
        return sb.toString();
    }

    private String readProcessOutput(Process process) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
