package com.bim.ifctobom;

import java.nio.file.*;

/**
 * CLI entry point for the IFC-to-BOM pipeline.
 *
 * <p>Usage:
 * <pre>
 *   java -cp ... com.bim.ifctobom.IFCtoBOMMain \
 *       --classify IFCtoBOM/src/main/resources/classify_sh.yaml \
 *       [--bom-db library/SH_BOM.db] \
 *       [--comp-db library/component_library.db] \
 *       [--schema library/schema_snapshot_bom.sql]
 * </pre>
 */
public class IFCtoBOMMain {

    public static void main(String[] args) throws Exception {
        Path yamlPath = null;
        Path bomDbPath = Path.of("library/SH_BOM.db");
        Path compDbPath = Path.of("library/component_library.db");
        Path schemaPath = Path.of("library/schema_snapshot_bom.sql");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--classify" -> yamlPath = Path.of(args[++i]);
                case "--bom-db"   -> bomDbPath = Path.of(args[++i]);
                case "--comp-db"  -> compDbPath = Path.of(args[++i]);
                case "--schema"   -> schemaPath = Path.of(args[++i]);
                default -> {
                    System.err.println("Unknown option: " + args[i]);
                    printUsage();
                    System.exit(1);
                }
            }
        }

        if (yamlPath == null) {
            System.err.println("Missing required --classify <yaml>");
            printUsage();
            System.exit(1);
        }

        if (!Files.exists(yamlPath)) {
            System.err.println("[ERROR] YAML not found: " + yamlPath);
            System.exit(1);
        }
        if (!Files.exists(compDbPath)) {
            System.err.println("[ERROR] component_library.db not found: " + compDbPath);
            System.exit(1);
        }

        var result = IFCtoBOMPipeline.run(yamlPath, bomDbPath, compDbPath, schemaPath);

        System.out.printf("%n=== IFCtoBOM Complete ===%n");
        System.out.printf("Building:    %s%n", result.buildingType());
        System.out.printf("Products:    %d registered%n", result.productsRegistered());
        System.out.printf("Structural:  %d lines%n", result.structuralLines());
        System.out.printf("Room BOMs:   %d lines%n", result.roomLines());
        System.out.printf("Total:       %d lines%n", result.totalLines());
        System.out.printf("AABB:        %.0f x %.0f x %.0f mm%n",
                result.aabbWidthMm(), result.aabbDepthMm(), result.aabbHeightMm());
        System.out.printf("Hash:        %s%n", result.integrityHash());
        System.out.printf("Output:      %s%n", bomDbPath);
    }

    private static void printUsage() {
        System.err.println("Usage: IFCtoBOMMain --classify <yaml> " +
                "[--bom-db <path>] [--comp-db <path>] [--schema <sql>]");
    }
}
