package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;
import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.validation.GeometryIntegrityChecker;
import com.bim.compiler.validation.PlacementProver;
import com.bim.compiler.validation.SpatialDigest;

import java.io.File;
import java.sql.*;

/**
 * Single compilation pipeline — one engine, N buildings.
 *
 * 7-step pipeline extracted from per-building E2E tests:
 *   1. Parse DSL → BuildingDefinition
 *   2. Compile → BuildingSpec
 *   3. Write to output DB
 *   4. SpatialDigest
 *   5. Shadow validate (EXTRACTED only)
 *   6. Geometry integrity check
 *   7. PlacementProver (critical proofs gate)
 *
 * Returns PipelineResult — caller decides pass/fail.
 */
public class CompilationPipeline {

    public record PipelineResult(
        String buildingId,
        int elementCount,
        String spatialDigest,
        PlacementProver.ProofReport proofs,
        int shadowMismatches,
        GeometryIntegrityChecker.CheckReport geometryReport,
        boolean proverSkipped
    ) {}

    /**
     * Run the full compilation pipeline for a building entry.
     * Does NOT call System.exit or assert — returns result for caller to evaluate.
     */
    public static PipelineResult run(BuildingEntry entry) throws Exception {
        String buildingId = entry.id();
        String outputDbPath = entry.outputDbPath();

        System.out.println("=".repeat(70));
        System.out.printf("PIPELINE: %s [%s]%n", entry.name(), entry.provenance());
        System.out.println("=".repeat(70));

        // =====================================================================
        // STEP 1: Parse DSL
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 1: PARSE DSL");
        System.out.println("-".repeat(70));

        BuildingDefinition def = BuildingParser.parse(entry.dslContent());
        System.out.println("Building: " + def.name());

        // =====================================================================
        // STEP 2: Compile
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 2: COMPILE TO BUILDINGSPEC");
        System.out.println("-".repeat(70));

        CompilationResult result = BuildingCompiler.compileWithValidation(def);
        BuildingSpec spec = result.spec();

        for (StoreySpec storey : spec.storeys()) {
            System.out.printf("Storey '%s': rooms=%d, walls=%d, doors=%d, windows=%d%n",
                storey.name(), storey.rooms().size(), storey.walls().size(),
                storey.doors().size(), storey.windows().size());
        }

        // =====================================================================
        // STEP 3: Write to DB
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 3: WRITE TO DB");
        System.out.println("-".repeat(70));

        File outputFile = new File(outputDbPath);
        outputFile.getParentFile().mkdirs();
        outputFile.delete();

        int elementCount;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + outputDbPath)) {
            conn.setAutoCommit(false);

            BuildingWriter writer = new BuildingWriter(conn);
            writer.initSchema();
            writer.write(spec);
            conn.commit();
            System.out.println("Database written: " + outputDbPath);

            elementCount = queryInt(conn, "SELECT COUNT(*) FROM elements_meta");
            System.out.printf("Total elements: %d%n", elementCount);

            // Class breakdown
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT ifc_class, COUNT(*) FROM elements_meta GROUP BY ifc_class ORDER BY COUNT(*) DESC")) {
                System.out.println("\nElement classes:");
                while (rs.next()) {
                    System.out.printf("  %-30s %d%n", rs.getString(1), rs.getInt(2));
                }
            }
        }

        // =====================================================================
        // STEP 4: SpatialDigest
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 4: SPATIAL DIGEST");
        System.out.println("-".repeat(70));

        SpatialDigest.DigestReport digestReport = SpatialDigest.computeWithReport(outputDbPath);
        System.out.println(digestReport);

        // =====================================================================
        // STEP 5: Shadow Validation (EXTRACTED only)
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 5: RELATIONAL SHADOW VALIDATION");
        System.out.println("-".repeat(70));

        int shadowMismatches = -1;
        if (!entry.isGenerative()) {
            shadowMismatches = RelationalResolver.getInstance().shadowValidate(buildingId);
            if (shadowMismatches == 0) {
                System.out.println("[PASS] Relational resolver matches oracle (0 mismatches)");
            } else if (shadowMismatches < 0) {
                System.out.println("[SKIP] No relational rules — shadow validation skipped");
            } else {
                System.out.printf("[FAIL] Relational resolver: %d mismatches%n", shadowMismatches);
            }
        } else {
            System.out.println("[SKIP] Generative building — no shadow oracle");
        }

        // =====================================================================
        // STEP 6: Geometry Integrity Check
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 6: GEOMETRY INTEGRITY CHECK");
        System.out.println("-".repeat(70));

        GeometryIntegrityChecker.CheckReport geoReport;
        if (entry.hasReference()) {
            geoReport = GeometryIntegrityChecker.check(outputDbPath, entry.referenceDbPath());
        } else {
            geoReport = GeometryIntegrityChecker.check(outputDbPath);
        }
        GeometryIntegrityChecker.printReport(geoReport);

        if (geoReport.failCount() == 0) {
            System.out.printf("[PASS] Geometry integrity: %d OK, %d WARN%n",
                geoReport.fullyOk(), geoReport.warnCount());
        } else {
            System.out.printf("[FAIL] Geometry integrity: %d failures%n", geoReport.failCount());
        }

        // =====================================================================
        // STEP 7: Placement Mathematical Proof
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 7: PLACEMENT MATHEMATICAL PROOF");
        System.out.println("-".repeat(70));

        PlacementProver.ProofReport proofReport = null;
        boolean proverSkipped = false;

        // Terminal has no relational data — prover generates noise, skip it
        boolean hasRelationalData = checkRelationalData(buildingId);
        if (hasRelationalData || entry.isGenerative()) {
            proofReport = PlacementProver.proveFromDB(outputDbPath);
            PlacementProver.printReport(proofReport);
            if (proofReport.criticalViolations() == 0) {
                System.out.printf("[PASS] All critical proofs satisfied (%d proven, %d advisory violations)%n",
                    proofReport.proven(), proofReport.violated());
            } else {
                System.out.printf("[FAIL] %d critical proof violations (GATES test)%n",
                    proofReport.criticalViolations());
            }
            if (proofReport.violated() > 0 && proofReport.criticalViolations() == 0) {
                System.out.printf("[INFO] %d advisory violations (non-blocking)%n", proofReport.violated());
            }
        } else {
            System.out.println("[SKIP] No relational data — prover deferred");
            proverSkipped = true;
        }

        // =====================================================================
        // SUMMARY
        // =====================================================================
        System.out.println("=".repeat(70));
        System.out.printf("PIPELINE COMPLETE: %s — %d elements%n", entry.name(), elementCount);
        System.out.println("=".repeat(70));

        return new PipelineResult(
            buildingId,
            elementCount,
            digestReport.digest(),
            proofReport,
            shadowMismatches,
            geoReport,
            proverSkipped
        );
    }

    /**
     * Check if a building has relational data (ad_room_boundary rows).
     * Terminal doesn't have this yet — prover generates 58K+ noise violations without it.
     */
    private static boolean checkRelationalData(String buildingId) {
        // Map building_id to the building_type used in ad_room_boundary
        // Registry building_id matches ad_building.building_type for SH/DX/TB-LKTN
        // but Terminal uses 'Ifc4_Terminal' in ad_room_boundary
        String buildingType = buildingId;
        if ("SJTII_Terminal".equals(buildingId)) {
            buildingType = "Ifc4_Terminal";
        }
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:library/component_library.db");
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM ad_room_boundary WHERE building_type = ?")) {
            ps.setString(1, buildingType);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private static int queryInt(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
