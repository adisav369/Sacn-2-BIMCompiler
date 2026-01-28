package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingCompiler.*;
import com.bim.compiler.dsl.BuildingDefinition.*;
import com.google.gson.*;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * Phase 20: Intent Compiler
 *
 * Consumes JSON specs from intent_resolver.py and generates buildings.
 * Does NOT modify existing pipeline — pure addition.
 *
 * Flow: spec.json → DSL string → BuildingParser → BuildingCompiler → DB → IFC
 */
public class IntentCompiler {

    /**
     * Convert JSON spec to DSL string.
     */
    public String specToDSL(Path specJson) throws IOException {
        String json = Files.readString(specJson);
        JsonObject spec = JsonParser.parseString(json).getAsJsonObject();

        StringBuilder dsl = new StringBuilder();

        // Building name from type
        String buildingType = spec.get("building_type").getAsString();
        String buildingName = buildingType + "_from_intent";

        dsl.append(String.format("BUILDING \"%s\" {\n", buildingName));

        // Storeys
        JsonArray storeys = spec.getAsJsonArray("storeys");
        for (JsonElement storeyEl : storeys) {
            JsonObject storey = storeyEl.getAsJsonObject();

            int level = storey.get("level").getAsInt();
            String name = storey.get("name").getAsString();

            dsl.append(String.format("    STOREY \"%s\" level:%d height:2.8m {\n", name, level));

            // Rooms
            JsonArray rooms = storey.getAsJsonArray("rooms");
            for (JsonElement roomEl : rooms) {
                JsonObject room = roomEl.getAsJsonObject();

                String roomType = room.get("type").getAsString();
                String roomName = room.get("name").getAsString();
                double width = room.get("width").getAsDouble();
                double depth = room.get("depth").getAsDouble();

                dsl.append(String.format("        %s \"%s\" size:%.1fx%.1fm {\n",
                    roomType, roomName, width, depth));

                // Constraints
                if (room.has("constraints")) {
                    JsonObject constraints = room.getAsJsonObject("constraints");

                    if (constraints.has("adjacent")) {
                        JsonElement adj = constraints.get("adjacent");
                        if (adj.isJsonArray()) {
                            for (JsonElement a : adj.getAsJsonArray()) {
                                dsl.append(String.format("            adjacent: %s\n", a.getAsString()));
                            }
                        } else {
                            dsl.append(String.format("            adjacent: %s\n", adj.getAsString()));
                        }
                    }

                    if (constraints.has("exterior")) {
                        dsl.append(String.format("            exterior: %s\n",
                            constraints.get("exterior").getAsString()));
                    }

                    if (constraints.has("above")) {
                        dsl.append(String.format("            above: %s\n",
                            constraints.get("above").getAsString()));
                    }

                    if (constraints.has("stack")) {
                        dsl.append(String.format("            stack: %s\n",
                            constraints.get("stack").getAsString()));
                    }
                }

                dsl.append("        }\n");
            }

            dsl.append("    }\n");
        }

        // Roof
        if (spec.has("roof")) {
            JsonObject roof = spec.getAsJsonObject("roof");
            int pitch = roof.has("pitch") ? roof.get("pitch").getAsInt() : 15;
            dsl.append(String.format("    ROOF pitch:%ddeg\n", pitch));
        }

        dsl.append("}\n");

        return dsl.toString();
    }

    /**
     * Compile JSON spec to DB + optional IFC.
     */
    public void compile(Path specJson, Path outputDir) throws Exception {
        // Step 1: Generate DSL
        String dsl = specToDSL(specJson);

        // Step 2: Write .bim file
        Path bimPath = outputDir.resolve("building.bim");
        Files.writeString(bimPath, dsl);
        System.out.println("Generated: " + bimPath);

        // Step 3: Parse
        BuildingDefinition def = BuildingParser.parse(dsl);

        // Step 4: Compile
        BuildingSpec spec = BuildingCompiler.compile(def);

        // Step 5: Write to DB
        Path dbPath = outputDir.resolve("building.db");
        Files.deleteIfExists(dbPath);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            BuildingWriter writer = new BuildingWriter(conn);
            writer.initSchema();
            writer.write(spec);

            Path jsonPath = outputDir.resolve("building.json");
            writer.exportJson(spec, jsonPath.toString());
        }

        System.out.println("Generated: " + dbPath);

        // Verify
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            var stmt = conn.createStatement();
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM elements_meta");
            rs.next();
            System.out.println("Elements in DB: " + rs.getInt(1));
        }
    }

    // =========================================================================
    // CLI
    // =========================================================================

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: IntentCompiler <spec.json> [output_dir]");
            System.out.println("\nExample:");
            System.out.println("  java IntentCompiler output/spec.json output/");
            System.exit(1);
        }

        Path specJson = Path.of(args[0]);
        Path outputDir = args.length > 1 ? Path.of(args[1]) : Path.of("output");

        Files.createDirectories(outputDir);

        System.out.println("=".repeat(60));
        System.out.println("INTENT COMPILER");
        System.out.println("=".repeat(60));
        System.out.println("Input: " + specJson);
        System.out.println("Output: " + outputDir);
        System.out.println();

        IntentCompiler compiler = new IntentCompiler();

        // Show generated DSL
        String dsl = compiler.specToDSL(specJson);
        System.out.println("Generated DSL:");
        System.out.println("-".repeat(60));
        System.out.println(dsl);
        System.out.println("-".repeat(60));

        // Compile
        compiler.compile(specJson, outputDir);

        System.out.println("\n[DONE] Ready for IFC export:");
        System.out.println("  python scripts/export_building_to_ifc.py " +
            outputDir + "/building.db " + outputDir + "/building.ifc");
    }
}
