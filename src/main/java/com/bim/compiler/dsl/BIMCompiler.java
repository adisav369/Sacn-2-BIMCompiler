package com.bim.compiler.dsl;

import com.bim.compiler.builder.WallBuilder;
import com.bim.compiler.builder.WallSpec;
import com.bim.compiler.export.IFCExportConfig;
import com.bim.compiler.export.IFCExporter;
import com.bim.compiler.model.ISpatialElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main entry point for the BIM Compiler.
 *
 * Compiles DSL text files to IFC:
 *   DSL → Parse → Layout → Compile → Build → Export
 *
 * Usage: java BIMCompiler input.bim output.ifc
 */
public class BIMCompiler {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java BIMCompiler <input.bim> <output.ifc>");
            System.out.println();
            System.out.println("Compiles BIM DSL files to IFC format.");
            System.exit(1);
        }

        String inputPath = args[0];
        String outputPath = args[1];

        try {
            BIMCompiler compiler = new BIMCompiler();
            compiler.compile(inputPath, outputPath);
        } catch (Exception e) {
            System.err.println("Compilation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void compile(String inputPath, String outputPath) throws IOException {
        System.out.println("======================================================================");
        System.out.println("BIM COMPILER");
        System.out.println("======================================================================");
        System.out.println("Input:  " + inputPath);
        System.out.println("Output: " + outputPath);
        System.out.println();

        // Step 1: Read DSL file
        System.out.println("Step 1: Reading DSL file...");
        String dsl = Files.readString(Path.of(inputPath));
        System.out.println("  Read " + dsl.length() + " characters");

        // Step 2: Parse DSL
        System.out.println("Step 2: Parsing DSL...");
        StoreyDefinition storey = DSLParser.parse(dsl);
        System.out.println("  Storey: " + storey.name());
        System.out.println("  Height: " + storey.height() + "m");
        System.out.println("  Rooms:  " + storey.rooms().size());
        for (RoomDefinition room : storey.rooms()) {
            System.out.println("    - " + room.type() + " \"" + room.name() + "\" " +
                room.width() + "x" + room.depth() + "m");
        }

        // Step 3: Resolve layout
        System.out.println("Step 3: Resolving layout...");
        Map<String, RoomPolygon> layout = GridLayoutResolver.resolve(storey);
        for (Map.Entry<String, RoomPolygon> entry : layout.entrySet()) {
            RoomPolygon p = entry.getValue();
            System.out.println(String.format("    %s: [%.1f,%.1f]-[%.1f,%.1f]",
                entry.getKey(), p.minX(), p.minY(), p.maxX(), p.maxY()));
        }

        // Step 4: Validate against building codes
        System.out.println("Step 4: Validating against IRC 2021...");
        int warnings = validateRooms(layout, storey);
        if (warnings > 0) {
            System.out.println("  " + warnings + " code warning(s) - see above");
        } else {
            System.out.println("  All rooms pass code requirements");
        }

        // Step 5: Compile to construction
        System.out.println("Step 5: Compiling to construction...");
        ConstructionSpec spec = RoomCompiler.compile(layout, storey);
        System.out.println("  Walls:  " + spec.walls().size());
        System.out.println("  Spaces: " + spec.spaces().size());

        // Step 6: Build geometry
        System.out.println("Step 6: Building geometry...");
        WallBuilder wallBuilder = new WallBuilder();
        List<ISpatialElement> elements = new ArrayList<>();

        for (WallSpec wall : spec.walls()) {
            ISpatialElement built = wallBuilder.build(wall);
            elements.add(built);
            System.out.println(String.format("    Wall: %.1fm, %d openings",
                wall.length(), wall.openings().size()));
        }

        // Step 7: Export to IFC
        System.out.println("Step 7: Exporting to IFC...");
        DSLExporter exporter = new DSLExporter(IFCExportConfig.terminal());
        boolean success = exporter.exportStorey(spec, outputPath);

        if (success) {
            System.out.println();
            System.out.println("======================================================================");
            System.out.println("COMPILATION SUCCESSFUL");
            System.out.println("Output: " + outputPath);
            System.out.println("======================================================================");
        } else {
            System.err.println("Export failed!");
            System.exit(1);
        }
    }

    /**
     * Validate rooms against building code requirements.
     * Returns number of warnings generated.
     */
    private int validateRooms(Map<String, RoomPolygon> layout, StoreyDefinition storey) {
        int warningCount = 0;

        for (RoomDefinition room : storey.rooms()) {
            RoomPolygon polygon = layout.get(room.name());
            if (polygon == null) continue;

            RoomRequirements reqs = RoomRequirements.forType(room.type());
            if (reqs == null) continue;

            // Calculate actual dimensions
            double width = polygon.maxX() - polygon.minX();
            double depth = polygon.maxY() - polygon.minY();
            double area = width * depth;
            double minDim = Math.min(width, depth);

            // Validate
            RoomRequirements.ValidationResult result = reqs.validate(room.name(), area, minDim);

            if (result.hasWarnings()) {
                System.out.print("  WARNING: " + result.warnings());
                warningCount++;
            }
        }

        return warningCount;
    }
}
