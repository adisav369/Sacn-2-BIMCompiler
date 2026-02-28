package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;
import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.validation.GeometryIntegrityChecker;
import com.bim.compiler.validation.PlacementProver;
import com.bim.compiler.validation.SpatialDigest;

import com.bim.orm.ModelQuery;
import com.bim.ormsandbox.po.BomTemplateComposer;
import com.bim.ormsandbox.po.M_CO_EmptySpace;
import com.bim.ormsandbox.po.M_CO_EmptySpaceLine;
import com.bim.ormsandbox.po.X_M_BOM;
import com.bim.ormsandbox.po.X_M_BOMLine;

import java.io.File;
import java.sql.*;
import java.util.List;
import java.util.Optional;

/**
 * Single compilation pipeline — one engine, N buildings.
 *
 * 8-step pipeline as typed {@link CompilerStage} chain:
 *   1. Metadata validation (referential integrity)
 *   2. Parse DSL → BuildingDefinition
 *   3. Compile → BuildingSpec
 *   4. Template composition (ST mode only — M_BomCategoryLine walk)
 *   5. Write to output DB
 *   6. SpatialDigest
 *   7. Geometry integrity check
 *   8. PlacementProver (critical proofs gate — skipped for ST mode)
 *
 * Returns PipelineResult — caller decides pass/fail.
 */
public class CompilationPipeline {

    public record PipelineResult(
        String buildingId,
        int elementCount,
        String spatialDigest,
        PlacementProver.ProofReport proofs,
        GeometryIntegrityChecker.CheckReport geometryReport,
        boolean proverSkipped,
        String emptySpaceChecksum
    ) {}

    private static final List<CompilerStage> STAGES = List.of(
        new MetadataValidator(),  // Step 1 — validate data before use
        new ParseStage(),         // Step 2
        new CompileStage(),       // Step 3
        new TemplateStage(),      // Step 4 — ST mode only; skipped for all other c_bpartners
        new WriteStage(),         // Step 5
        new DigestStage(),        // Step 6
        new GeometryStage(),      // Step 7
        new ProveStage()          // Step 8
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

    /**
     * Template composition stage — ST mode only.
     *
     * <p>Runs {@link BomTemplateComposer} against the building AABB to select
     * best-fit BOMs from the entire catalog using the M_BomCategoryLine template tree.
     * Stores the {@link BomTemplateComposer.CompositionReport} in the context so
     * WriteStage can use it to populate CO_EmptySpaceLines at L2+ (room level).
     *
     * <p>Skipped for all non-ST buildings (c_bpartner != 'ST').
     */
    private static class TemplateStage implements CompilerStage {
        @Override public String name() { return "TEMPLATE COMPOSITION"; }

        @Override
        public boolean shouldSkip(CompilationContext ctx) {
            return !"ST".equals(ctx.entry().cbpartner());
        }

        @Override
        public void execute(CompilationContext ctx) throws Exception {
            int widthMm  = (int) ctx.entry().aabbWidthMm();
            int depthMm  = (int) ctx.entry().aabbDepthMm();
            int heightMm = (int) ctx.entry().aabbHeightMm();
            // POC: numUnits=1 (single-unit). Future: add num_units column to c_order.
            int numUnits = 1;

            try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db")) {
                BomTemplateComposer.CompositionReport report =
                    BomTemplateComposer.compose(bomConn, widthMm, depthMm, heightMm, numUnits);
                ctx.setCompositionReport(report);

                System.out.printf("[TEMPLATE] %s: %d selections, %d gaps, complete=%s%n",
                    ctx.buildingId(), report.selections().size(), report.gaps().size(),
                    report.isComplete());
                for (BomTemplateComposer.NodeSelection sel : report.selections()) {
                    System.out.printf("[TEMPLATE]   L%d %-4s → %-30s owner=%-4s alloc=%dx%dx%d%s%n",
                        sel.level(), sel.categoryId(),
                        sel.selectedBomId() != null ? sel.selectedBomId() : "<none>",
                        sel.selectedOwner() != null ? sel.selectedOwner() : "-",
                        sel.allocW(), sel.allocD(), sel.allocH(),
                        "NONE".equals(sel.mirroringRule()) ? "" : " mirror=" + sel.mirroringRule());
                }
                if (!report.gaps().isEmpty()) {
                    for (String gap : report.gaps()) {
                        System.err.printf("[TEMPLATE] GAP: %s%n", gap);
                    }
                }
            }
        }
    }

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
                populateCoEmptySpace(conn, ctx);
            }
        }

        /**
         * Populate co_empty_space (header) + co_empty_space_line (acceptance + per-storey).
         * Three-level output:
         *   Level 0: top-level UNIT BOM acceptance into building AABB
         *   Level 1: per-child decomposition (FLOOR_SLAB, LEVEL, ROOF) with storey names
         *   Level 2: template leaf selections (LI/BD/KT/BT/DN room BOMs) — ST mode only
         *
         * ST mode: UN BOM is resolved via findBestFitAnyOwner (no owner-specific UN exists).
         * L2 lines are added from the CompositionReport stored in ctx by TemplateStage.
         *
         * Uses DAO (M_CO_EmptySpace / M_CO_EmptySpaceLine) — no raw JDBC for writes.
         */
        private static void populateCoEmptySpace(Connection conn, CompilationContext ctx) {
            String buildingId = ctx.buildingId();
            BuildingSpec spec = ctx.spec();
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

                // 2. Look up UNIT BOM: c_bpartner from registry, then m_bom
                String unitBomId = null;
                String cbpartner = null;
                try (Connection libConn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db");
                     PreparedStatement ps = libConn.prepareStatement(
                         "SELECT c_bpartner FROM c_order WHERE building_id = ?")) {
                    ps.setString(1, buildingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) cbpartner = rs.getString(1);
                    }
                    if (cbpartner != null) {
                        Optional<X_M_BOM> opt = new ModelQuery<>(libConn, X_M_BOM::new, X_M_BOM.Table_Name)
                            .where("c_bpartner = ? AND bom_category = 'UN'", cbpartner).first();
                        if (opt.isPresent()) unitBomId = opt.get().getBomId();
                    }
                }
                // ST mode: no owner-specific UN BOM — derive UN BOM from template GF owner.
                // The GF level selection carries the correct owner (e.g. SH, DX).
                // Using GF owner to look up UN BOM is more reliable than AABB-fit across all owners.
                if (unitBomId == null && "ST".equals(ctx.entry().cbpartner())) {
                    BomTemplateComposer.CompositionReport tmplReport = ctx.compositionReport();
                    if (tmplReport != null) {
                        String gfOwner = tmplReport.selections().stream()
                            .filter(s -> "GF".equals(s.categoryId()) && s.selectedOwner() != null)
                            .map(BomTemplateComposer.NodeSelection::selectedOwner)
                            .findFirst().orElse(null);
                        if (gfOwner != null) {
                            try (Connection libConn2 = DriverManager.getConnection("jdbc:sqlite:library/BOM.db")) {
                                Optional<X_M_BOM> opt = new ModelQuery<>(libConn2, X_M_BOM::new, X_M_BOM.Table_Name)
                                    .where("c_bpartner = ? AND bom_category = 'UN'", gfOwner).first();
                                if (opt.isPresent()) {
                                    unitBomId = opt.get().getBomId();
                                    System.out.printf("[CO_EMPTY] ST mode: selected UN BOM %s via GF owner %s%n",
                                        unitBomId, gfOwner);
                                }
                            }
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

                // 4. Level-0 acceptance: UNIT BOM → full building AABB (single line).
                //    Owner-matched (c_bpartner == UNIT BOM's c_bpartner):
                //    the BOM IS the complete intact construct.
                //    EmptySpaceChecksum hashes this single line for verification.
                M_CO_EmptySpaceLine topLine = M_CO_EmptySpaceLine.createTopLevel(
                    conn, header.getCoEmptyspaceId(),
                    unitBomId,
                    originXMm, originYMm, originZMm,
                    widthMm, depthMm, heightMm);
                topLine.save();

                // 5. Level-1 per-storey decomposition (structural tiers).
                //    Both modes are important: single level-0 for hash verification,
                //    level-1 for structural capacity audit trail.
                try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db")) {
                    List<X_M_BOMLine> children = new ModelQuery<>(bomConn, X_M_BOMLine::new, X_M_BOMLine.Table_Name)
                        .where("bom_id = ? AND is_active = 1", unitBomId)
                        .orderBy("sequence").list();

                    int storeyIdx = 0;
                    double anchorZ = originZMm;
                    for (X_M_BOMLine po : children) {
                        String childBomId = po.getChildProductId(); // NORM-2: MAKE rows use child_product_id
                        String role = po.getRole();
                        int seq = po.getSequence();
                        double dzM = po.getDz();
                        String locatorRef = po.getLocatorRef();

                        if (dzM > 0) anchorZ = originZMm + dzM * 1000.0;

                        double beforeZ = anchorZ;
                        double nextZ;
                        String storey = null;

                        if (isRoomContent(role)) {
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
                            nextZ = beforeZ;
                        }

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

                // L2 lines from template composition (ST mode only).
                // When composition is complete (no gaps), mark header CO immediately —
                // ProveStage is skipped for ST mode (no relational placement rules to prove).
                BomTemplateComposer.CompositionReport report = ctx.compositionReport();
                if ("ST".equals(ctx.entry().cbpartner()) && report != null) {
                    int seq2 = 0;
                    for (BomTemplateComposer.NodeSelection sel : report.selections()) {
                        if (!sel.isLeaf() || sel.selectedBomId() == null) continue;
                        M_CO_EmptySpaceLine l2 = M_CO_EmptySpaceLine.create(
                            conn, header.getCoEmptyspaceId(),
                            sel.selectedBomId(), seq2++, sel.categoryId(), 2,
                            originXMm, originYMm, originZMm,
                            originXMm + sel.allocW(), originYMm + sel.allocD(), originZMm + sel.allocH(),
                            sel.allocW(), "FLOAT");
                        l2.save();
                        System.out.printf("[CO_EMPTY]   L2 cat=%-4s bom=%-30s alloc=%dx%dx%d%n",
                            sel.categoryId(), sel.selectedBomId(),
                            sel.allocW(), sel.allocD(), sel.allocH());
                    }
                    if (report.isComplete()) {
                        header.setComplete();  // IP → CO (template proof: all leaf nodes selected)
                        header.save();
                        System.out.printf("[CO_EMPTY] ST mode: composition complete — marking CO%n");
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
            String dbPath = ctx.entry().outputDbPath();
            SpatialDigest.DigestReport digestReport = SpatialDigest.computeWithReport(dbPath);
            ctx.setDigestReport(digestReport);
            System.out.println(digestReport);

            // CO_EmptySpaceLine checksum — single line for owner-matched builds
            String esChecksum = SpatialDigest.computeEmptySpaceChecksum(dbPath);
            ctx.setEmptySpaceChecksum(esChecksum);
            if (esChecksum != null) {
                System.out.printf("EmptySpaceChecksum: %s%n", esChecksum);
            }
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
            // ST mode: template-driven builds have no relational placement rules — prover n/a
            if ("ST".equals(ctx.entry().cbpartner())) {
                System.out.println("[SKIP] ST mode — template proof already applied in WriteStage");
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
