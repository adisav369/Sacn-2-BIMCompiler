/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.export;

import com.bim.compiler.builder.PipeSpec;
import com.bim.compiler.builder.WallSpec;
import com.bim.compiler.builder.OpeningSpec;
import com.bim.compiler.geometry.BoundingBox;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports generated elements to IFC format.
 * Uses ifcopenshell Python via subprocess with CONFIG-DRIVEN approach.
 *
 * Architecture:
 * - Java owns extracted patterns (G1-G12 from SESSION_STATE.md)
 * - Java passes config to Python as JSON
 * - Python exporter is generic, works with any typology
 *
 * From Phase 7: IFC Export for round-trip validation.
 * Refactored: Config-driven for typology extensibility.
 */
public class IFCExporter {

    private static final String PYTHON_SCRIPT =
        "/home/red1/bim-compiler/scripts/export_to_ifc.py";
    private static final String PYTHON_PATH =
        "/home/red1/bim-compiler/venv/bin/python3";

    private final IFCExportConfig config;

    /**
     * Create exporter with Terminal typology config (default).
     */
    public IFCExporter() {
        this(IFCExportConfig.terminal());
    }

    /**
     * Create exporter with specific config.
     */
    public IFCExporter(IFCExportConfig config) {
        this.config = config;
    }

    /**
     * Export a wall to IFC file.
     *
     * @param spec the wall specification
     * @param outputPath the output .ifc file path
     * @return true if export succeeded
     */
    public boolean exportWall(WallSpec spec, String outputPath) {
        try {
            // Create JSON for wall spec (now includes openings)
            String json = wallSpecToJson(spec);

            // Write spec and config to temp files
            Path tempJson = Files.createTempFile("wall_", ".json");
            Path tempConfig = Files.createTempFile("config_", ".json");
            Files.writeString(tempJson, json);
            Files.writeString(tempConfig, config.toJson());

            // Call Python script with config
            ProcessBuilder pb = new ProcessBuilder(
                PYTHON_PATH, PYTHON_SCRIPT,
                "--wall", tempJson.toString(),
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
     * Export a pipe to IFC file.
     *
     * @param spec the pipe specification
     * @param outputPath the output .ifc file path
     * @return true if export succeeded
     */
    public boolean exportPipe(PipeSpec spec, String outputPath) {
        try {
            // Create JSON for pipe spec
            String json = pipeSpecToJson(spec);

            // Write spec and config to temp files
            Path tempJson = Files.createTempFile("pipe_", ".json");
            Path tempConfig = Files.createTempFile("config_", ".json");
            Files.writeString(tempJson, json);
            Files.writeString(tempConfig, config.toJson());

            // Call Python script with config
            ProcessBuilder pb = new ProcessBuilder(
                PYTHON_PATH, PYTHON_SCRIPT,
                "--pipe", tempJson.toString(),
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
     * Reimport an IFC file and extract bounding boxes.
     * Used for round-trip validation.
     *
     * @param ifcPath the IFC file to reimport
     * @return list of bounding boxes for elements in the file
     */
    public List<ReimportedElement> reimport(String ifcPath) {
        List<ReimportedElement> results = new ArrayList<>();

        try {
            ProcessBuilder pb = new ProcessBuilder(
                PYTHON_PATH, PYTHON_SCRIPT,
                "--reimport", ifcPath
            );
            pb.redirectErrorStream(false);

            Process process = pb.start();
            String output = readProcessOutput(process);
            String errors = readStream(process.getErrorStream());
            int exitCode = process.waitFor();

            if (!errors.isEmpty()) {
                System.err.println("Reimport warnings: " + errors);
            }

            if (exitCode != 0) {
                System.err.println("Reimport failed with exit code: " + exitCode);
                return results;
            }

            // Parse JSON output
            results = parseReimportJson(output);

        } catch (Exception e) {
            System.err.println("Reimport error: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }

    /**
     * Get the current export config.
     */
    public IFCExportConfig getConfig() {
        return config;
    }

    /**
     * Convert WallSpec to JSON for Python script.
     * Now includes openings for full export.
     */
    private String wallSpecToJson(WallSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format("  \"start\": {\"x\": %.6f, \"y\": %.6f, \"z\": %.6f},\n",
            spec.start().x(), spec.start().y(), spec.start().z()));
        sb.append(String.format("  \"end\": {\"x\": %.6f, \"y\": %.6f, \"z\": %.6f},\n",
            spec.end().x(), spec.end().y(), spec.end().z()));
        sb.append(String.format("  \"thickness\": %.6f,\n", spec.thickness().getMeters()));
        sb.append(String.format("  \"height\": %.6f,\n", spec.height()));

        // Export openings
        sb.append("  \"openings\": [");
        List<OpeningSpec> openings = spec.openings();
        for (int i = 0; i < openings.size(); i++) {
            OpeningSpec op = openings.get(i);
            if (i > 0) sb.append(",");
            sb.append("\n    {");
            sb.append(String.format("\"offset\": %.6f, ", op.offsetAlongWall()));
            sb.append(String.format("\"width\": %.6f, ", op.width()));
            sb.append(String.format("\"height\": %.6f, ", op.height()));
            sb.append(String.format("\"sill_height\": %.6f", op.offsetFromFloor()));
            sb.append("}");
        }
        if (!openings.isEmpty()) sb.append("\n  ");
        sb.append("]\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert PipeSpec to JSON for Python script.
     */
    private String pipeSpecToJson(PipeSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format("  \"start\": {\"x\": %.6f, \"y\": %.6f, \"z\": %.6f},\n",
            spec.start().x(), spec.start().y(), spec.start().z()));
        sb.append(String.format("  \"end\": {\"x\": %.6f, \"y\": %.6f, \"z\": %.6f},\n",
            spec.end().x(), spec.end().y(), spec.end().z()));
        sb.append(String.format("  \"diameter\": %.6f,\n", spec.diameterMeters()));
        sb.append(String.format("  \"discipline\": \"%s\"\n", spec.discipline().name()));
        sb.append("}");
        return sb.toString();
    }

    /**
     * Read process stdout.
     */
    private String readProcessOutput(Process process) throws IOException {
        return readStream(process.getInputStream());
    }

    /**
     * Read an input stream to string.
     */
    private String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Parse reimport JSON output to element list.
     */
    private List<ReimportedElement> parseReimportJson(String json) {
        List<ReimportedElement> results = new ArrayList<>();

        // Simple JSON parsing (avoid dependency on Jackson/Gson)
        json = json.trim();
        if (!json.startsWith("[")) {
            return results;
        }

        // Split by }, {
        String[] parts = json.split("\\},\\s*\\{");
        for (String part : parts) {
            part = part.replace("[", "").replace("]", "")
                       .replace("{", "").replace("}", "");

            String guid = extractJsonString(part, "guid");
            String type = extractJsonString(part, "type");
            String name = extractJsonString(part, "name");
            double minX = extractJsonDouble(part, "minX");
            double maxX = extractJsonDouble(part, "maxX");
            double minY = extractJsonDouble(part, "minY");
            double maxY = extractJsonDouble(part, "maxY");
            double minZ = extractJsonDouble(part, "minZ");
            double maxZ = extractJsonDouble(part, "maxZ");
            int vertexCount = (int) extractJsonDouble(part, "vertex_count");

            if (guid != null && type != null) {
                BoundingBox bbox = new BoundingBox(minX, maxX, minY, maxY, minZ, maxZ);
                results.add(new ReimportedElement(guid, type, name, bbox, vertexCount));
            }
        }

        return results;
    }

    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\\s*\"([^\"]*)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private double extractJsonDouble(String json, String key) {
        String pattern = "\"" + key + "\":\\s*([\\d.\\-eE]+)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Element reimported from IFC for validation.
     */
    public record ReimportedElement(
        String guid,
        String type,
        String name,
        BoundingBox bbox,
        int vertexCount
    ) {}
}
