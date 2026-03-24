package com.bim.ifctobom;

import com.bim.ifctobom.ClassificationYaml.BuildingConfig;
import com.bim.ifctobom.ExtractionReader.ExtractionElement;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * CLI entry point for the IFC-to-BOM pipeline.
 *
 * <h3>Per-building BOM database convention</h3>
 * <p>Each building produces its own clean {@code *_BOM.db}:
 * <ul>
 *   <li>{@code SH_BOM.db} — Ifc4_SampleHouse (classify_sh.yaml)</li>
 *   <li>{@code DX_BOM.db} — Ifc2x3_Duplex (classify_dx.yaml)</li>
 *   <li>{@code TE_BOM.db} — SJTII_Terminal (classify_te.yaml, future)</li>
 * </ul>
 *
 * <p>The legacy monolithic {@code BOM.db} is archived ({@code library/archive/BOM.db}).
 * It contained contamination from unrelated buildings (TB-LKTN terrace, Terminal,
 * speculative high-rise templates). Each {@code *_BOM.db} is produced fresh from
 * its classification YAML + component_library.db — no cross-contamination.
 *
 * <p>The {@code --bom-db} flag defaults to {@code library/<PREFIX>_BOM.db} where
 * PREFIX is derived from the YAML building type.
 *
 * <p>Usage:
 * <pre>
 *   # Populate component_library.db (one-time, per building)
 *   java -cp ... com.bim.ifctobom.IFCtoBOMMain \
 *       --populate --classify IFCtoBOM/src/main/resources/classify_sh.yaml
 *
 *   # Run BOM pipeline (reads component_library.db, writes *_BOM.db)
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
        boolean populateOnly = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--populate" -> populateOnly = true;
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

        if (populateOnly) {
            runPopulate(yamlPath, compDbPath);
            return;
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

    /**
     * Populate component_library.db from reference DB — run once per building.
     *
     * <p>Steps: extract elements → create M_Product catalog → link M_Product_Image.
     * Guards: countUnlinkedProducts must be 0 (every product needs geometry).
     */
    private static void runPopulate(Path yamlPath, Path compDbPath) throws Exception {
        ClassificationYaml yaml = ClassificationYaml.load(yamlPath);
        BuildingConfig config = yaml.getBuilding();
        String buildingType = config.buildingType();

        System.out.printf("[populate] Building: %s (%s)%n", config.name(), buildingType);

        Connection compConn = DriverManager.getConnection("jdbc:sqlite:" + compDbPath);
        Connection discConn = DriverManager.getConnection("jdbc:sqlite:library/ERP.db");
        try {
            // 1. Extract + enrich in memory, fill geometry gaps in library
            Map<String, List<ExtractionElement>> storeyElements =
                    ExtractionPopulator.populate(compConn, buildingType);
            List<ExtractionElement> allElements = new ArrayList<>();
            storeyElements.values().forEach(allElements::addAll);
            System.out.printf("[populate] Extracted: %d elements%n", allElements.size());

            // 2. Create M_Product in catalog (INSERT OR IGNORE = reuse)
            int cataloged = ProductRegistrar.ensureProductCatalog(
                    compConn, discConn, allElements, buildingType);
            System.out.printf("[populate] Products cataloged: %d new%n", cataloged);

            // 3. Link M_Product → geometry_hash via M_Product_Image
            int images = ProductRegistrar.ensureProductImages(compConn, buildingType);
            System.out.printf("[populate] Images linked: %d new%n", images);

            // 4. Guard: every product must have geometry
            int unlinked = ProductRegistrar.countUnlinkedProducts(compConn, buildingType);
            if (unlinked > 0) {
                System.err.printf("[populate] FAIL — %d product(s) have no geometry_hash%n", unlinked);
                System.exit(1);
            }

            System.out.printf("[populate] %s complete — %d elements, %d products, %d images%n",
                    buildingType, allElements.size(), cataloged, images);
        } finally {
            compConn.close();
            discConn.close();
        }
    }

    private static void printUsage() {
        System.err.println("Usage: IFCtoBOMMain --classify <yaml> " +
                "[--populate] [--bom-db <path>] [--comp-db <path>] [--schema <sql>]");
    }
}
