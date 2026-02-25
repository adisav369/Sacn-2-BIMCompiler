package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;
import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.validation.GeometryIntegrityChecker;
import com.bim.compiler.validation.PlacementProver;
import com.bim.compiler.validation.SpatialDigest;

import com.bim.ormsandbox.po.M_CO_EmptySpace;
import com.bim.ormsandbox.po.M_CO_EmptySpaceLine;

import java.io.File;
import java.sql.*;
import java.util.List;

/**
 * Single compilation pipeline — one engine, N buildings.
 *
 * 7-step pipeline as typed {@link CompilerStage} chain:
 *   1. Metadata validation (referential integrity)
 *   2. Parse DSL → BuildingDefinition
 *   3. Compile → BuildingSpec
 *   4. Write to output DB
 *   5. SpatialDigest
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

    private static final List<CompilerStage> STAGES = List.of(
        new MetadataValidator(),  // Step 1 — validate data before use
        new ParseStage(),         // Step 2
        new CompileStage(),       // Step 3
        new WriteStage(),         // Step 4
        new DigestStage(),        // Step 5
        new GeometryStage(),      // Step 6
        new ProveStage()          // Step 7
    );

    /**
     * Run the full compilation pipeline for a building entry.
     * Does NOT call System.exit or assert — returns result for caller to evaluate.
     */
    public static PipelineResult run(BuildingEntry entry) throws Exception {
        CompilationContext ctx = new CompilationContext(entry);

        System.out.println("=".repeat(70));
        System.out.printf("PIPELINE: %s [%s]%n", entry.name(), entry.provenance());
        System.out.println("=".repeat(70));

        for (int i = 0; i < STAGES.size(); i++) {
            CompilerStage stage = STAGES.get(i);
            System.out.println("\n" + "-".repeat(70));
            System.out.printf("STEP %d: %s%n", i + 1, stage.name());
            System.out.println("-".repeat(70));
            if (stage.shouldSkip(ctx)) {
                System.out.println("[SKIP] " + stage.name());
                continue;
            }
            stage.execute(ctx);
        }

        System.out.println("=".repeat(70));
        System.out.printf("PIPELINE COMPLETE: %s — %d elements%n", entry.name(), ctx.elementCount());
        System.out.println("=".repeat(70));
        return ctx.toResult();
    }

    // =====================================================================
    // Stage implementations (pipeline-internal)
    // =====================================================================

    private static class ParseStage implements CompilerStage {
        @Override public String name() { return "PARSE DSL"; }

        @Override
        public void execute(CompilationContext ctx) throws Exception {
            BuildingDefinition def = BuildingParser.parse(ctx.entry().dslContent());
            ctx.setDefinition(def);
            System.out.println("Building: " + def.name());
        }
    }

    private static class CompileStage implements CompilerStage {
        @Override public String name() { return "COMPILE TO BUILDINGSPEC"; }

        @Override
        public void execute(CompilationContext ctx) throws Exception {
            CompilationResult result = BuildingCompiler.compileWithValidation(ctx.definition());
            BuildingSpec spec = result.spec();
            ctx.setSpec(spec);

            for (StoreySpec storey : spec.storeys()) {
                System.out.printf("Storey '%s': rooms=%d, walls=%d, doors=%d, windows=%d%n",
                    storey.name(), storey.rooms().size(), storey.walls().size(),
                    storey.doors().size(), storey.windows().size());
            }
        }
    }

    private static class WriteStage implements CompilerStage {
        @Override public String name() { return "WRITE TO DB"; }

        @Override
        public void execute(CompilationContext ctx) throws Exception {
            String outputDbPath = ctx.entry().outputDbPath();
            File outputFile = new File(outputDbPath);
            outputFile.getParentFile().mkdirs();
            outputFile.delete();

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + outputDbPath)) {
                conn.setAutoCommit(false);

                BuildingWriter writer = new BuildingWriter(conn);
                writer.initSchema();
                writer.write(ctx.spec());
                conn.commit();
                System.out.println("Database written: " + outputDbPath);

                ctx.setElementCount(queryInt(conn, "SELECT COUNT(*) FROM elements_meta"));
                System.out.printf("Total elements: %d%n", ctx.elementCount());

                // Class breakdown
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(
                         "SELECT ifc_class, COUNT(*) FROM elements_meta GROUP BY ifc_class ORDER BY COUNT(*) DESC")) {
                    System.out.println("\nElement classes:");
                    while (rs.next()) {
                        System.out.printf("  %-30s %d%n", rs.getString(1), rs.getInt(2));
                    }
                }

                // Phase X-Ray: populate spatial containment (storey → element mapping)
                try (Statement stmt = conn.createStatement()) {
                    int contained = stmt.executeUpdate("""
                        INSERT OR IGNORE INTO rel_contained_in_space (element_guid, space_guid)
                        SELECT em.guid, ss.guid
                        FROM elements_meta em
                        JOIN spatial_structure ss ON em.storey = ss.name
                        WHERE em.ifc_class IN (
                            'IfcFurniture','IfcFurnishingElement',
                            'IfcFlowTerminal','IfcFan','IfcAirTerminal',
                            'IfcFlowFitting','IfcFlowSegment',
                            'IfcLightFixture','IfcSprinkler',
                            'IfcSanitaryTerminal','IfcWasteTerminal')
                          AND ss.type = 'IfcBuildingStorey'
                        """);
                    System.out.printf("[CONTAIN] Spatial containment: %d elements linked to storeys%n", contained);
                }
                conn.commit();

                // Phase X-Ray: BBox scan — log WARN for any furniture with placeholder geometry
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("""
                         SELECT em.ifc_class, COUNT(*) as cnt
                         FROM elements_meta em
                         JOIN element_instances ei ON em.guid = ei.guid
                         JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
                         WHERE em.ifc_class IN ('IfcFurniture','IfcFurnishingElement','IfcSanitaryTerminal')
                           AND bg.vertex_count <= 8
                         GROUP BY em.ifc_class
                         """)) {
                    while (rs.next()) {
                        System.out.printf("[X-RAY] WARN: %s has %d BBox-only elements (vertex_count<=8) — LOD mesh missing%n",
                            rs.getString("ifc_class"), rs.getInt("cnt"));
                    }
                }

                // CO_EmptySpace: building AABB from R*Tree + UNIT BOM acceptance + per-storey lines
                populateCoEmptySpace(conn, ctx.buildingId(), ctx.spec());
            }
        }

        /**
         * Populate co_empty_space (header) + co_empty_space_line (acceptance + per-storey).
         * Phase 4: Three-level output:
         *   Level 0: top-level UNIT BOM acceptance into building AABB
         *   Level 1: per-child decomposition (FLOOR_SLAB, LEVEL, ROOF) with storey names
         *
         * Uses DAO (M_CO_EmptySpace / M_CO_EmptySpaceLine) — no raw JDBC for writes.
         */
        private static void populateCoEmptySpace(Connection conn, String buildingId, BuildingSpec spec) {
            try {
                // 1. Get building AABB from compiled R*Tree (meters → mm)
                double minX, minY, minZ, maxX, maxY, maxZ;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(
                         "SELECT MIN(minX), MIN(minY), MIN(minZ), MAX(maxX), MAX(maxY), MAX(maxZ) FROM elements_rtree")) {
                    if (!rs.next() || rs.getObject(1) == null) {
                        System.out.println("[CO_EMPTY] No elements in R*Tree — skipping CO_EmptySpace");
                        return;
                    }
                    minX = rs.getDouble(1); minY = rs.getDouble(2); minZ = rs.getDouble(3);
                    maxX = rs.getDouble(4); maxY = rs.getDouble(5); maxZ = rs.getDouble(6);
                }

                double originXMm = minX * 1000.0;
                double originYMm = minY * 1000.0;
                double originZMm = minZ * 1000.0;
                double widthMm   = (maxX - minX) * 1000.0;
                double depthMm   = (maxY - minY) * 1000.0;
                double heightMm  = (maxZ - minZ) * 1000.0;

                // 2. Look up UNIT BOM: bom_owner from registry, then m_bom from BOM.db
                String unitBomId = null;
                String bomOwner = null;
                try (Connection libConn = DriverManager.getConnection("jdbc:sqlite:library/component_library.db");
                     PreparedStatement ps = libConn.prepareStatement(
                         "SELECT bom_owner FROM ad_building_registry WHERE building_id = ?")) {
                    ps.setString(1, buildingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) bomOwner = rs.getString(1);
                    }
                }
                if (bomOwner != null) {
                    try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db");
                         PreparedStatement ps = bomConn.prepareStatement(
                             "SELECT bom_id FROM m_bom WHERE bom_owner = ? AND bom_category = 'UN'")) {
                        ps.setString(1, bomOwner);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) unitBomId = rs.getString(1);
                        }
                    }
                }
                if (unitBomId == null) {
                    System.out.printf("[CO_EMPTY] No UNIT BOM found for %s — skipping%n", buildingId);
                    return;
                }

                // 3. Create header via DAO — starts at DR/available=1
                M_CO_EmptySpace header = M_CO_EmptySpace.create(
                    conn, buildingId,
                    originXMm, originYMm, originZMm,
                    widthMm, depthMm, heightMm);
                header.setProcessing();  // DR → IP (compilation in progress)
                header.save();

                // 4. Create level-0 acceptance line: full UNIT BOM into building AABB
                M_CO_EmptySpaceLine topLine = M_CO_EmptySpaceLine.createTopLevel(
                    conn, header.getCoEmptyspaceId(),
                    unitBomId,
                    originXMm, originYMm, originZMm,
                    widthMm, depthMm, heightMm);
                topLine.save();

                // 5. Per-storey decomposition: walk UNIT BOM children from BOM.db
                try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db");
                     PreparedStatement ps = bomConn.prepareStatement(
                         "SELECT child_bom_id, role, sequence, dz, locator_ref " +
                         "FROM m_bom_line WHERE bom_id = ? AND is_active = 1 ORDER BY sequence")) {
                    ps.setString(1, unitBomId);
                    int storeyIdx = 0;
                    try (ResultSet rs = ps.executeQuery()) {
                        double anchorZ = originZMm;
                        while (rs.next()) {
                            String childBomId = rs.getString("child_bom_id");
                            String role = rs.getString("role");
                            int seq = rs.getInt("sequence");
                            double dzM = rs.getDouble("dz");
                            String locatorRef = rs.getString("locator_ref");

                            // dz advances the anchor (stored in metres in m_bom_line)
                            if (dzM > 0) anchorZ = originZMm + dzM * 1000.0;

                            double beforeZ = anchorZ;
                            double nextZ;
                            String storey = null;

                            if (isRoomContent(role)) {
                                // GROUND_FLOOR / LEVEL_1 / LEVEL_2 → match to StoreySpec
                                if (spec != null && storeyIdx < spec.storeys().size()) {
                                    StoreySpec matched = spec.storeys().get(storeyIdx);
                                    storey = matched.name();
                                    nextZ = beforeZ + matched.height() * 1000.0;
                                    anchorZ = nextZ;
                                    storeyIdx++;
                                } else {
                                    nextZ = beforeZ;
                                }
                            } else {
                                // GROUND_SLAB, UPPER_SLAB, ROOF → structural, zero extent
                                nextZ = beforeZ;
                            }

                            // Create level-1 CO_EmptySpaceLine
                            M_CO_EmptySpaceLine childLine = M_CO_EmptySpaceLine.create(
                                conn, header.getCoEmptyspaceId(),
                                childBomId != null ? childBomId : role, seq, role, 1,
                                originXMm, originYMm, beforeZ,
                                originXMm + widthMm, originYMm + depthMm, nextZ,
                                widthMm, locatorRef != null ? locatorRef : "FLOAT");
                            childLine.setStorey(storey);
                            childLine.save();

                            System.out.printf("[CO_EMPTY]   L1 seq=%d role=%-18s bom=%s before_z=%.0f next_z=%.0f storey=%s%n",
                                seq, role, childBomId, beforeZ, nextZ, storey);
                        }
                    }
                }

                conn.commit();

                System.out.printf("[CO_EMPTY] %s: AABB=%.0fx%.0fx%.0fmm, UNIT=%s, status=IP%n",
                    buildingId, widthMm, depthMm, heightMm, unitBomId);

            } catch (SQLException e) {
                System.err.printf("[CO_EMPTY] WARN: Failed to populate CO_EmptySpace for %s: %s%n",
                    buildingId, e.getMessage());
            }
        }

        /** Room-content roles have storeys; structural roles don't. */
        private static boolean isRoomContent(String role) {
            return role != null && (role.contains("LEVEL") || role.contains("GROUND_FLOOR"));
        }
    }

    private static class DigestStage implements CompilerStage {
        @Override public String name() { return "SPATIAL DIGEST"; }

        @Override
        public void execute(CompilationContext ctx) throws Exception {
            SpatialDigest.DigestReport digestReport = SpatialDigest.computeWithReport(ctx.entry().outputDbPath());
            ctx.setDigestReport(digestReport);
            System.out.println(digestReport);
        }
    }

    private static class GeometryStage implements CompilerStage {
        @Override public String name() { return "GEOMETRY INTEGRITY CHECK"; }

        @Override
        public void execute(CompilationContext ctx) throws Exception {
            GeometryIntegrityChecker.CheckReport geoReport;
            if (ctx.entry().hasReference()) {
                geoReport = GeometryIntegrityChecker.check(ctx.entry().outputDbPath(), ctx.entry().referenceDbPath());
            } else {
                geoReport = GeometryIntegrityChecker.check(ctx.entry().outputDbPath());
            }
            ctx.setGeometryReport(geoReport);
            GeometryIntegrityChecker.printReport(geoReport);

            if (geoReport.failCount() == 0) {
                System.out.printf("[PASS] Geometry integrity: %d OK, %d WARN%n",
                    geoReport.fullyOk(), geoReport.warnCount());
            } else {
                System.out.printf("[FAIL] Geometry integrity: %d failures%n", geoReport.failCount());
            }
        }
    }

    private static class ProveStage implements CompilerStage {
        @Override public String name() { return "PLACEMENT MATHEMATICAL PROOF"; }

        @Override
        public void execute(CompilationContext ctx) throws Exception {
            // Skip when no relational data and not generative — prover generates noise
            if (!ctx.hasRelationalData() && !ctx.entry().isGenerative()) {
                System.out.println("[SKIP] No relational data — prover deferred");
                ctx.setProverSkipped(true);
                return;
            }

            PlacementProver.ProofReport proofReport = PlacementProver.proveFromDB(ctx.entry().outputDbPath());
            ctx.setProofReport(proofReport);
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

            // ── IsAvailable quality gate: IP → CO or IP → RE ──────────────
            try (Connection outConn = DriverManager.getConnection(
                    "jdbc:sqlite:" + ctx.entry().outputDbPath())) {
                outConn.setAutoCommit(false);
                java.util.Optional<M_CO_EmptySpace> opt =
                    M_CO_EmptySpace.getForBuilding(outConn, ctx.buildingId());
                if (opt.isPresent()) {
                    M_CO_EmptySpace header = opt.get();
                    if (proofReport.criticalViolations() == 0) {
                        header.setComplete();    // IP → CO, is_available = 0
                        System.out.printf("[CO_EMPTY] %s: IP → CO (is_available=0, proven)%n",
                            ctx.buildingId());
                    } else {
                        header.setRejected();    // IP → RE, is_available stays 1
                        System.out.printf("[CO_EMPTY] %s: IP → RE (%d critical violations)%n",
                            ctx.buildingId(), proofReport.criticalViolations());
                    }
                    header.save();
                    outConn.commit();
                }
            } catch (SQLException e) {
                System.err.printf("[CO_EMPTY] WARN: quality gate update failed for %s: %s%n",
                    ctx.buildingId(), e.getMessage());
            }
        }
    }

    private static int queryInt(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
