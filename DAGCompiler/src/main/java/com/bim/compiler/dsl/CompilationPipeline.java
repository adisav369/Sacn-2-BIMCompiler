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
 * 9-step pipeline as typed {@link CompilerStage} chain:
 *   1. Metadata validation (referential integrity)
 *   2. Parse DSL → BuildingDefinition
 *   3. Compile → BuildingSpec
 *   4. Template composition (ST mode only — M_BomCategoryLine walk)
 *   5. Write to output DB
 *   6. VerbStage (BIM COBOL script hook — skipped if no .bimcobol file)
 *   7. SpatialDigest
 *   8. Geometry integrity check
 *   9. PlacementProver (critical proofs gate — skipped for ST mode)
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
        new TemplateStage(),      // Step 4 — ST mode only; skipped for all other DocSubTypes
        new WriteStage(),         // Step 5
        new VerbStage(),          // Step 6 — BIM COBOL script hook (skips if no .bimcobol file)
        new DigestStage(),        // Step 7
        new GeometryStage(),      // Step 8
        new ProveStage()          // Step 9
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
     * <p>Skipped for all non-ST buildings (doc_sub_type != 'ST').
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

            // Map c_order.building_type (RESIDENTIAL) → M_BomCategory.doc_type (Residential)
            String docType = toDocType(ctx.entry().type());

            try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db")) {
                BomTemplateComposer.CompositionReport report =
                    BomTemplateComposer.compose(bomConn, docType, widthMm, depthMm, heightMm, numUnits);
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

    /**
     * Map c_order.building_type (UPPERCASE) to M_BomCategory.doc_type (Title Case).
     * In iDempiere terms: C_Order.C_DocType_ID → C_DocType.Name.
     */
    static String toDocType(String buildingType) {
        if (buildingType == null) return "Residential";
        return switch (buildingType.toUpperCase()) {
            case "RESIDENTIAL"  -> "Residential";
            case "COMMERCIAL"   -> "Commercial";
            case "INDUSTRIAL"   -> "Industrial";
            default             -> "Residential";
        };
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

                // Copy c_order + c_orderline from BOM.db so output.db is self-contained
                copyCOrderToOutput(conn, ctx);

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

                // 2. Look up UNIT BOM: doc_sub_type (via c_bpartner) from registry, then m_bom
                String unitBomId = null;
                String docSubType = null;
                try (Connection libConn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db");
                     PreparedStatement ps = libConn.prepareStatement(
                         "SELECT c_bpartner FROM c_order WHERE building_id = ?")) {
                    ps.setString(1, buildingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) docSubType = rs.getString(1);
                    }
                    if (docSubType != null) {
                        Optional<X_M_BOM> opt = new ModelQuery<>(libConn, X_M_BOM::new, X_M_BOM.Table_Name)
                            .where("doc_sub_type = ? AND bom_category = 'UN'", docSubType).first();
                        if (opt.isPresent()) unitBomId = opt.get().getBomId();
                    }
                }
                // ST mode: no owner-specific UN BOM — derive UN BOM from template GF owner.
                // The GF level selection carries the correct owner (e.g. SH, DX).
                // Using GF owner to look up UN BOM is more reliable than AABB-fit across all owners.
                if (unitBomId == null && "ST".equals(docSubType)) {
                    BomTemplateComposer.CompositionReport tmplReport = ctx.compositionReport();
                    if (tmplReport != null) {
                        String gfOwner = tmplReport.selections().stream()
                            .filter(s -> "GF".equals(s.categoryId()) && s.selectedOwner() != null)
                            .map(BomTemplateComposer.NodeSelection::selectedOwner)
                            .findFirst().orElse(null);
                        if (gfOwner != null) {
                            try (Connection libConn2 = DriverManager.getConnection("jdbc:sqlite:library/BOM.db")) {
                                Optional<X_M_BOM> opt = new ModelQuery<>(libConn2, X_M_BOM::new, X_M_BOM.Table_Name)
                                    .where("doc_sub_type = ? AND bom_category = 'UN'", gfOwner).first();
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
                //    Owner-matched (doc_sub_type == UNIT BOM's doc_sub_type):
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

                        // L2: room-level children for non-ST buildings (LIVING, DINING, BEDROOM, BATHROOM…)
                        if (storey != null && childBomId != null
                                && !"ST".equals(ctx.entry().cbpartner())) {
                            addL2RoomLines(conn, bomConn, header, buildingId,
                                childBomId, storey,
                                originXMm, originYMm, beforeZ,
                                widthMm, depthMm, nextZ - beforeZ);
                        }
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

        /**
         * Write Level-2 ESLines: room-category children of a floor BOM.
         *
         * <p>Queries m_bom_line for the floor BOM, filters for room-category children
         * (bom_category IN 'LI','BD','KT','BT','DN'), and writes one L2 ESLine per room.
         * AABB is read from {@code ad_room_boundary} if available; falls back to storey AABB.
         *
         * <p>Called for non-ST buildings only (ST uses CompositionReport for L2).
         */
        private static void addL2RoomLines(
                Connection conn, Connection bomConn,
                M_CO_EmptySpace header, String buildingId,
                String floorBomId, String storey,
                double originXMm, double originYMm, double floorZMm,
                double widthMm, double depthMm, double heightMm) throws SQLException {

            // Room categories that get L2 ESLines
            String SQL_ROOM_CHILDREN = """
                SELECT mbl.child_product_id, mbl.role, mbl.sequence, mb2.bom_category
                FROM m_bom_line mbl
                LEFT JOIN m_bom mb2 ON mb2.bom_id = mbl.child_product_id
                WHERE mbl.bom_id = ? AND mbl.is_active = 1
                  AND mb2.bom_category IN ('LI','BD','KT','BT','DN')
                ORDER BY mbl.sequence
                """;

            String SQL_ROOM_AABB = """
                SELECT min_x_mm, max_x_mm, min_y_mm, max_y_mm
                FROM ad_room_boundary
                WHERE building_type = ? AND storey = ? AND room_type = ?
                LIMIT 1
                """;

            try (PreparedStatement psRoom = bomConn.prepareStatement(SQL_ROOM_CHILDREN)) {
                psRoom.setString(1, floorBomId);
                try (ResultSet rsRoom = psRoom.executeQuery()) {
                    int seq2 = 200; // L2 sequence base (above L1 which uses BOM sequence 1-100)
                    while (rsRoom.next()) {
                        String childBomId = rsRoom.getString("child_product_id");
                        String role       = rsRoom.getString("role");
                        String bomCat     = rsRoom.getString("bom_category");

                        // Look up room AABB from ad_room_boundary
                        String roomType = categoryToRoomType(bomCat);
                        double rMinX = originXMm, rMaxX = originXMm + widthMm;
                        double rMinY = originYMm, rMaxY = originYMm + depthMm;
                        boolean hasRealAabb = false;

                        try (PreparedStatement psAabb = bomConn.prepareStatement(SQL_ROOM_AABB)) {
                            psAabb.setString(1, buildingId);
                            psAabb.setString(2, storey);
                            psAabb.setString(3, roomType);
                            try (ResultSet rsAabb = psAabb.executeQuery()) {
                                if (rsAabb.next()
                                        && rsAabb.getObject("min_x_mm") != null
                                        && rsAabb.getObject("max_x_mm") != null) {
                                    rMinX = rsAabb.getDouble("min_x_mm");
                                    rMaxX = rsAabb.getDouble("max_x_mm");
                                    rMinY = rsAabb.getDouble("min_y_mm");
                                    rMaxY = rsAabb.getDouble("max_y_mm");
                                    hasRealAabb = true;
                                }
                            }
                        }

                        double roomW = Math.abs(rMaxX - rMinX);
                        double roomD = Math.abs(rMaxY - rMinY);

                        M_CO_EmptySpaceLine l2 = M_CO_EmptySpaceLine.create(
                            conn, header.getCoEmptyspaceId(),
                            childBomId, seq2++, role, 2,
                            rMinX, rMinY, floorZMm,
                            rMaxX, rMaxY, floorZMm + heightMm,
                            roomW, "FLOAT");
                        l2.setStorey(storey);
                        l2.setRoomName(role);
                        l2.save();

                        System.out.printf("[CO_EMPTY]   L2 cat=%-4s role=%-12s bom=%-30s room=%.0fx%.0f%s%n",
                            bomCat, role, childBomId, roomW, roomD,
                            hasRealAabb ? "" : " (fallback)");
                    }
                }
            }
        }

        /** Map BOM category code to ad_room_boundary room_type. */
        private static String categoryToRoomType(String bomCategory) {
            return switch (bomCategory) {
                case "LI" -> "LIVING";
                case "BD" -> "BEDROOM";
                case "KT" -> "KITCHEN";
                case "BT" -> "BATHROOM";
                case "DN" -> "DINING";
                default   -> bomCategory;  // pass-through for non-standard codes
            };
        }

        /**
         * Copy c_order row + c_orderline rows from BOM.db into output.db (including C_DocType_ID).
         * Makes output.db self-contained and traceable to the project config that produced it.
         */
        private static void copyCOrderToOutput(Connection outConn, CompilationContext ctx) {
            String buildingId = ctx.buildingId();
            try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db")) {
                // 1. Copy c_order row
                try (PreparedStatement ps = bomConn.prepareStatement(
                         "SELECT * FROM c_order WHERE building_id = ?")) {
                    ps.setString(1, buildingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            try (PreparedStatement ins = outConn.prepareStatement("""
                                    INSERT INTO c_order (
                                        building_id, building_name, building_type, dsl_content,
                                        output_db_path, reference_db_path, is_active, seq_no,
                                        expected_elements, spatial_digest, provenance, description,
                                        geometry_fail_threshold, doc_status, c_bpartner,
                                        aabb_width_mm, aabb_depth_mm, aabb_height_mm,
                                        empty_space_checksum, compiled_at, compiler_version,
                                        C_DocType_ID
                                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,datetime('now'),?,?)
                                    """)) {
                                ins.setString(1, rs.getString("building_id"));
                                ins.setString(2, rs.getString("building_name"));
                                ins.setString(3, rs.getString("building_type"));
                                ins.setString(4, rs.getString("dsl_content"));
                                ins.setString(5, rs.getString("output_db_path"));
                                ins.setString(6, rs.getString("reference_db_path"));
                                ins.setInt(7, rs.getInt("is_active"));
                                ins.setInt(8, rs.getInt("seq_no"));
                                ins.setObject(9, rs.getObject("expected_elements"));
                                ins.setString(10, rs.getString("spatial_digest"));
                                ins.setString(11, rs.getString("provenance"));
                                ins.setString(12, rs.getString("description"));
                                ins.setInt(13, rs.getInt("geometry_fail_threshold"));
                                ins.setString(14, "IP");  // compilation in progress
                                ins.setString(15, rs.getString("c_bpartner"));
                                ins.setObject(16, rs.getObject("aabb_width_mm"));
                                ins.setObject(17, rs.getObject("aabb_depth_mm"));
                                ins.setObject(18, rs.getObject("aabb_height_mm"));
                                ins.setString(19, rs.getString("empty_space_checksum"));
                                ins.setString(20, "BIM-Compiler-1.0");
                                ins.setString(21, rs.getString("C_DocType_ID"));
                                ins.executeUpdate();
                            }
                            System.out.printf("[C_ORDER] Copied c_order for %s to output.db%n", buildingId);
                        }
                    }
                }

                // 2. Copy c_orderline rows for this building's building_type
                String buildingType = ctx.entry().id();
                try (PreparedStatement ps = bomConn.prepareStatement(
                         "SELECT * FROM c_orderline WHERE building_type = ? AND is_active = 1")) {
                    ps.setString(1, buildingType);
                    try (ResultSet rs = ps.executeQuery()) {
                        int count = 0;
                        try (PreparedStatement ins = outConn.prepareStatement("""
                                INSERT INTO c_orderline (
                                    building_type, storey, element_ref, ifc_class, discipline,
                                    host_type, host_ref, position_rule, position_value, height_mm,
                                    family_ref, width_mm, height_extent_mm, depth_mm, orientation,
                                    geometry_hash, material_name, material_rgba, is_active,
                                    position_value_2, building_id, position_value_3
                                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                                """)) {
                            while (rs.next()) {
                                ins.setString(1, rs.getString("building_type"));
                                ins.setString(2, rs.getString("storey"));
                                ins.setString(3, rs.getString("element_ref"));
                                ins.setString(4, rs.getString("ifc_class"));
                                ins.setString(5, rs.getString("discipline"));
                                ins.setString(6, rs.getString("host_type"));
                                ins.setString(7, rs.getString("host_ref"));
                                ins.setString(8, rs.getString("position_rule"));
                                ins.setObject(9, rs.getObject("position_value"));
                                ins.setObject(10, rs.getObject("height_mm"));
                                ins.setString(11, rs.getString("family_ref"));
                                ins.setObject(12, rs.getObject("width_mm"));
                                ins.setObject(13, rs.getObject("height_extent_mm"));
                                ins.setObject(14, rs.getObject("depth_mm"));
                                ins.setString(15, rs.getString("orientation"));
                                ins.setString(16, rs.getString("geometry_hash"));
                                ins.setString(17, rs.getString("material_name"));
                                ins.setString(18, rs.getString("material_rgba"));
                                ins.setInt(19, rs.getInt("is_active"));
                                ins.setObject(20, rs.getObject("position_value_2"));
                                ins.setObject(21, rs.getObject("building_id"));
                                ins.setObject(22, rs.getObject("position_value_3"));
                                ins.executeUpdate();
                                count++;
                            }
                        }
                        System.out.printf("[C_ORDER] Copied %d c_orderline rows for %s to output.db%n",
                            count, buildingType);
                    }
                }
            } catch (SQLException e) {
                System.err.printf("[C_ORDER] WARN: Failed to copy c_order for %s: %s%n",
                    buildingId, e.getMessage());
            }
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

            // Write computed results back to output.db c_order
            updateCOrderComputedResults(dbPath, ctx.buildingId(),
                digestReport.digest(), ctx.elementCount(), esChecksum);
        }

        /**
         * UPDATE output.db c_order with computed spatial_digest, expected_elements, empty_space_checksum.
         * Also promotes doc_status from IP → CO (compilation complete, digest known).
         */
        private static void updateCOrderComputedResults(
                String dbPath, String buildingId,
                String spatialDigest, int elementCount, String esChecksum) {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                 PreparedStatement ps = conn.prepareStatement("""
                     UPDATE c_order SET
                         spatial_digest = ?,
                         expected_elements = ?,
                         empty_space_checksum = ?,
                         doc_status = 'CO'
                     WHERE building_id = ?
                     """)) {
                ps.setString(1, spatialDigest);
                ps.setInt(2, elementCount);
                ps.setString(3, esChecksum);
                ps.setString(4, buildingId);
                int updated = ps.executeUpdate();
                if (updated > 0) {
                    System.out.printf("[C_ORDER] Updated output.db c_order: digest=%s, elements=%d, checksum=%s%n",
                        spatialDigest != null ? spatialDigest.substring(0, 16) + "..." : "null",
                        elementCount, esChecksum);
                }
            } catch (SQLException e) {
                System.err.printf("[C_ORDER] WARN: Failed to update computed results for %s: %s%n",
                    buildingId, e.getMessage());
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
