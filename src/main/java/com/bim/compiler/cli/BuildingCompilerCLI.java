package com.bim.compiler.cli;

import com.bim.compiler.dsl.BuildingCompiler;
import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.dsl.BuildingDefinition;
import com.bim.compiler.dsl.BuildingParser;
import com.bim.compiler.dsl.BuildingWriter;
import com.bim.compiler.dsl.WitnessGenerator;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Generic CLI for compiling any BIM DSL file to database.
 *
 * Usage:
 *   mvn exec:java -Dexec.mainClass="com.bim.compiler.cli.BuildingCompilerCLI" \
 *     -Dexec.args="input.bim output.db"
 *
 * Or with witness generation:
 *   mvn exec:java -Dexec.mainClass="com.bim.compiler.cli.BuildingCompilerCLI" \
 *     -Dexec.args="input.bim output.db output_witness.json"
 */
public class BuildingCompilerCLI {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: BuildingCompilerCLI <input.bim> <output.db> [witness.json]");
            System.err.println();
            System.err.println("Examples:");
            System.err.println("  mvn exec:java -Dexec.mainClass=\"com.bim.compiler.cli.BuildingCompilerCLI\" \\");
            System.err.println("    -Dexec.args=\"examples/TB-LKTN-2S.bim output/house.db\"");
            System.err.println();
            System.err.println("  mvn exec:java -Dexec.mainClass=\"com.bim.compiler.cli.BuildingCompilerCLI\" \\");
            System.err.println("    -Dexec.args=\"examples/Sekolah-Kebangsaan.bim output/school.db output/school_witness.json\"");
            System.exit(1);
        }

        String dslPath = args[0];
        String dbPath = args[1];
        String witnessPath = args.length >= 3 ? args[2] : null;

        System.out.println("=".repeat(70));
        System.out.println("BIM COMPILER - Generic DSL Processor");
        System.out.println("=".repeat(70));
        System.out.println("Input:  " + dslPath);
        System.out.println("Output: " + dbPath);
        if (witnessPath != null) {
            System.out.println("Witness: " + witnessPath);
        }
        System.out.println("=".repeat(70));

        // Read DSL
        if (!Files.exists(Path.of(dslPath))) {
            System.err.println("ERROR: Input file not found: " + dslPath);
            System.exit(1);
        }

        String dsl = Files.readString(Path.of(dslPath));
        System.out.println("\n[1/4] Parsing DSL (" + dsl.length() + " chars)...");

        // Parse
        BuildingDefinition def = BuildingParser.parse(dsl);
        System.out.println("      Building: " + def.name());
        System.out.println("      Storeys: " + def.storeys().size());

        // Compile
        System.out.println("\n[2/4] Compiling to BuildingSpec...");
        CompilationResult result = BuildingCompiler.compileWithValidation(def);
        BuildingSpec spec = result.spec();

        int totalRooms = spec.storeys().stream()
            .mapToInt(s -> s.rooms().size())
            .sum();
        System.out.println("      Rooms: " + totalRooms);
        System.out.println("      Walls: " + spec.storeys().stream()
            .mapToInt(s -> s.walls().size()).sum());

        // Write to DB
        System.out.println("\n[3/4] Writing to database...");
        new File(dbPath).getParentFile().mkdirs();
        new File(dbPath).delete();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            conn.setAutoCommit(false);
            BuildingWriter writer = new BuildingWriter(conn);
            writer.initSchema();
            writer.write(spec);
            conn.commit();
        }

        System.out.println("      Database written: " + dbPath);

        // Generate witness if requested
        if (witnessPath != null) {
            System.out.println("\n[4/4] Generating witness...");
            if (WitnessGenerator.generateWitness(spec, null, Path.of(witnessPath), dsl, dbPath)) {
                System.out.println("      Witness written: " + witnessPath);
            }
        } else {
            System.out.println("\n[4/4] Witness generation skipped (no output path provided)");
        }

        System.out.println("\n" + "=".repeat(70));
        System.out.println("SUCCESS");
        System.out.println("=".repeat(70));
        System.out.println("\nNext steps:");
        System.out.println("  1. View in Blender:");
        System.out.println("     python scripts/export_to_gltf.py " + dbPath);
        System.out.println("     # Then open viewer/index.html and drag-drop the .glb file");
        System.out.println();
        System.out.println("  2. Generate 2D drawings:");
        System.out.println("     python scripts/export_2d_drawings.py " + dbPath);
        System.out.println("     # Then open output/drawings/drawing_index.html");
        System.out.println();
        if (witnessPath != null) {
            System.out.println("  3. Check witness claims:");
            System.out.println("     python -m json.tool " + witnessPath);
        }
        System.out.println("=".repeat(70));
    }
}
