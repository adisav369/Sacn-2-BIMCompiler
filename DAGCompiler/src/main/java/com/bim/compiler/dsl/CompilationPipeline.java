package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;
import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.validation.GeometryIntegrityChecker;
import com.bim.compiler.validation.PlacementProver;
import com.bim.compiler.validation.SpatialDigest;

import com.bim.orm.ModelQuery;
import com.bim.ormsandbox.po.BomTemplateComposer;
import com.bim.ormsandbox.po.MBomCategory;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.M_CO_EmptySpace;
import com.bim.ormsandbox.po.M_CO_EmptySpaceLine;
import com.bim.ormsandbox.po.MBOMLine;

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
     * Template composition stage — activated when DocSubType='ST'.
     *
     * <p>This is the WALK THRU path of the Prime Rule. When the three-key match
     * (AABB + DocBaseType + DocSubType) finds no BUILDING BOM, the template path
     * takes over. It queries M_BomCategory WHERE doc_type='RE' AND doc_sub_type='ST'
     * to find Standard Template entries (ST-SH, ST-DX), AABB-matches to pick the
     * right one, then delegates to {@link BomTemplateComposer} which walks the
     * M_BomCategoryLine template tree to select best-fit BOMs at every level.
     *
     * <p>The composition report is stored in context so WriteStage can populate
     * CO_EmptySpaceLines at L2+ (room level from template leaf selections).
     *
     * <p>Skipped for all non-ST buildings — those take the EN-BLOC singularity path.
     *
     * @see BomTemplateComposer
     * @see <a href="scripts/run_RosettaStones.sh">Prime Rule data set</a>
     */
    private static class TemplateStage implements CompilerStage {
        @Override public String name() { return "TEMPLATE COMPOSITION"; }

        /**
         * TemplateStage is skipped when DocSubType is not 'ST'.
         * ST is a DocSubType value (NOT DocBaseType). DocBaseType = RE/CO/IN only.
         * Template entries: C_DocType ST_SH/ST_DX (DocBaseType='RE', DocSubType='ST').
         */
        @Override
        public boolean shouldSkip(CompilationContext ctx) {
            return !"ST".equals(ctx.entry().docSubType());
        }

        @Override
        public void execute(CompilationContext ctx) throws Exception {
            // ── AABB resolution for ST entries ──────────────────────────
            //
            // ST entries have AABB=0 from BuildingRegistry because the LEFT JOIN
            // to m_bom matches on doc_base_type + doc_sub_type, and no BUILDING
            // BOM has doc_sub_type='ST'. So we resolve the AABB from M_BomCategory:
            //
            //   M_BomCategory WHERE doc_type='RE' AND doc_sub_type='ST'
            //   → Two records: ST-SH (16868×8668×3945) and ST-DX (9215×26565×7885)
            //   → AABB alone distinguishes which building variant to compose
            //
            // In production, C_Order.AABB comes from the user/designer. Here in
            // the test harness, we resolve from M_BomCategory as the source of truth.
            int widthMm, depthMm, heightMm;
            try (Connection bomConn2 = DriverManager.getConnection("jdbc:sqlite:library/BOM.db")) {
                MBomCategory tplCat = MBomCategory.getByDocTypeAndSubType(
                    bomConn2, ctx.entry().docBaseType(), ctx.entry().docSubType());
                if (tplCat != null && tplCat.getAabbWidthMm() > 0) {
                    widthMm  = (int) tplCat.getAabbWidthMm();
                    depthMm  = (int) tplCat.getAabbDepthMm();
                    heightMm = (int) tplCat.getAabbHeightMm();
                    System.out.printf("[TEMPLATE] AABB from M_BomCategory %s: %dx%dx%d%n",
                        tplCat.getCategoryId(), widthMm, depthMm, heightMm);
                } else {
                    // Fallback: use BuildingEntry AABB (non-zero when C_Order has AABB)
                    widthMm  = (int) ctx.entry().aabbWidthMm();
                    depthMm  = (int) ctx.entry().aabbDepthMm();
                    heightMm = (int) ctx.entry().aabbHeightMm();
                }
            }
            // POC: numUnits=1 (single-unit). Future: add num_units column to C_DocType.
            int numUnits = 1;

            // Map C_DocType_ID prefix → M_BomCategory.doc_type for template tree lookup
            String docType = toDocType(ctx.entry().docTypeId());

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
     * Map C_DocType_ID prefix to M_BomCategory.doc_type (short code).
     * Passes DocBaseType through (RE/CO/IN). ST is a DocSubType, resolved by BomTemplateComposer via AABB match.
     */
    static String toDocType(String docTypeId) {
        if (docTypeId == null) return "RE";
        return docTypeId.substring(0, Math.min(2, docTypeId.length())).toUpperCase();
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

                // Create C_Order in output.db from C_DocType config (transactional, fresh each compile)
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

                // Phase X-Ray containment moved to populateSpaceContainment() (Gap #6)

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

                // Gap #8: Ensure every elements_meta storey has a matching IfcBuildingStorey
                normalizeStoreyNames(conn);

                // Gap #5: Emit IfcSpace rows from L2 co_empty_space_line entries
                emitIfcSpaceFromL2(conn);

                // Gap #6: Populate rel_contained_in_space by centroid-in-space AABB matching
                populateSpaceContainment(conn);
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

                // 2. Look up BUILDING BOM by DocSubType — the top-level finished goods BOM.
                String bldgBomId = null;
                String docSubType = ctx.entry().docSubType();
                try (Connection libConn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db")) {
                    if (docSubType != null) {
                        MBOM bldgBom = MBOM.getBuildingBom(libConn, docSubType);
                        if (bldgBom != null) bldgBomId = bldgBom.getBomId();
                    }
                    // ST mode: no owner-specific BUILDING BOM — derive via template GF owner.
                    if (bldgBomId == null && "ST".equals(docSubType)) {
                        BomTemplateComposer.CompositionReport tmplReport = ctx.compositionReport();
                        if (tmplReport != null) {
                            String gfOwner = tmplReport.selections().stream()
                                .filter(s -> "GF".equals(s.categoryId()) && s.selectedOwner() != null)
                                .map(BomTemplateComposer.NodeSelection::selectedOwner)
                                .findFirst().orElse(null);
                            if (gfOwner != null) {
                                MBOM ownerBldg = MBOM.getBuildingBom(libConn, gfOwner);
                                if (ownerBldg != null) {
                                    bldgBomId = ownerBldg.getBomId();
                                    System.out.printf("[CO_EMPTY] ST mode: selected BUILDING BOM %s via GF owner %s%n",
                                        bldgBomId, gfOwner);
                                }
                            }
                        }
                    }
                }
                if (bldgBomId == null) {
                    System.out.printf("[CO_EMPTY] No BUILDING BOM found for %s — skipping%n", buildingId);
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
                    bldgBomId,
                    originXMm, originYMm, originZMm,
                    widthMm, depthMm, heightMm);
                topLine.save();

                // 5. Level-1 per-storey decomposition (structural tiers).
                //    Generic BOM traversal: walk UNIT BOM children, use role to
                //    identify room-content tiers (storeys) vs structural elements.
                //    The BOM structure itself determines L1/L2 — no building-type checks.
                try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db")) {
                    List<MBOMLine> children = new ModelQuery<>(bomConn, MBOMLine::new, MBOMLine.Table_Name)
                        .where("bom_id = ? AND is_active = 1", bldgBomId)
                        .orderBy("sequence").list();

                    int storeyIdx = 0;
                    double anchorZ = originZMm;
                    for (MBOMLine po : children) {
                        String childBomId = po.getChildProductId();
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

                        // L2: room-level children for non-ST buildings
                        if (storey != null && childBomId != null
                                && !"ST".equals(ctx.entry().docSubType())) {
                            addL2RoomLines(conn, bomConn, header, buildingId,
                                childBomId, docSubType, storey,
                                originXMm, originYMm, beforeZ,
                                widthMm, depthMm, nextZ - beforeZ);
                        }
                    }
                }

                // L2 lines from template composition (ST mode only).
                // When composition is complete (no gaps), mark header CO immediately —
                // ProveStage is skipped for ST mode (no relational placement rules to prove).
                BomTemplateComposer.CompositionReport report = ctx.compositionReport();
                if ("ST".equals(ctx.entry().docSubType()) && report != null) {
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
                    buildingId, widthMm, depthMm, heightMm, bldgBomId);

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
                String floorBomId, String docSubType, String storey,
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

            // DAO fallback: if floorBomId (e.g. SH_GF_STR flat) has no room-category
            // children, look up the structured FLOOR BOM by doc_sub_type (e.g. FLOOR_SH_GF_STD).
            String resolvedFloorBomId = floorBomId;
            boolean hasRoomChildren;
            try (PreparedStatement ps = bomConn.prepareStatement(SQL_ROOM_CHILDREN)) {
                ps.setString(1, floorBomId);
                try (ResultSet rs = ps.executeQuery()) { hasRoomChildren = rs.next(); }
            }
            if (!hasRoomChildren && docSubType != null) {
                try {
                    List<MBOM> floorBoms = MBOM.getByType(bomConn, "FLOOR");
                    resolvedFloorBomId = floorBoms.stream()
                        .filter(b -> docSubType.equals(b.getDocSubType())
                                  && b.getBomCategory() != null)
                        .map(MBOM::getBomId)
                        .findFirst().orElse(floorBomId);
                    if (!resolvedFloorBomId.equals(floorBomId)) {
                        System.out.printf("[CO_EMPTY]   L2 fallback: %s → %s (structured FLOOR BOM)%n",
                            floorBomId, resolvedFloorBomId);
                    }
                } catch (SQLException e) {
                    // fall through with original floorBomId
                }
            }

            String SQL_ROOM_AABB = """
                SELECT min_x_mm, max_x_mm, min_y_mm, max_y_mm
                FROM ad_room_boundary
                WHERE building_type = ? AND storey = ? AND room_type = ?
                LIMIT 1
                """;

            try (PreparedStatement psRoom = bomConn.prepareStatement(SQL_ROOM_CHILDREN)) {
                psRoom.setString(1, resolvedFloorBomId);
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
         * Gap #8: Ensure every storey referenced in elements_meta has a matching
         * IfcBuildingStorey in spatial_structure.
         *
         * <p>For extracted buildings, elements_meta storey names come from IFC (e.g. "Level 1",
         * "Level 2") while DSL-generated storey names may differ (e.g. "Ground", "Upper").
         * This adds missing storeys so that containment joins work correctly.
         */
        private static void normalizeStoreyNames(Connection conn) throws SQLException {
            // Find building GUID (should be exactly one IfcBuilding)
            String buildingGuid = null;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT guid FROM spatial_structure WHERE type = 'IfcBuilding' LIMIT 1")) {
                if (rs.next()) buildingGuid = rs.getString(1);
            }
            if (buildingGuid == null) return;

            // Find storeys in elements_meta not yet in spatial_structure
            int added = 0;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT DISTINCT em.storey
                     FROM elements_meta em
                     WHERE em.storey IS NOT NULL
                       AND em.storey NOT IN (SELECT name FROM spatial_structure WHERE type = 'IfcBuildingStorey')
                     """)) {
                while (rs.next()) {
                    String storey = rs.getString(1);
                    String storeyGuid = "STOREY_" + storey.toUpperCase().replace(" ", "_").replace("/", "_");
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR IGNORE INTO spatial_structure VALUES (?, 'IfcBuildingStorey', ?, ?, NULL, NULL)")) {
                        ps.setString(1, storeyGuid);
                        ps.setString(2, storey);
                        ps.setString(3, buildingGuid);
                        ps.execute();
                        added++;
                    }
                }
            }
            if (added > 0) {
                conn.commit();
                System.out.printf("[SPATIAL] Gap #8: added %d missing storeys from elements_meta%n", added);
            }
        }

        /**
         * Gap #5: Emit IfcSpace rows in spatial_structure from L2 co_empty_space_line entries.
         *
         * <p>Each L2 ESLine represents a room. We create an IfcSpace in spatial_structure
         * parented under the corresponding IfcBuildingStorey. The space GUID follows the
         * pattern SPACE_{STOREY}_{ROOM_NAME} for consistency with the DSL path.
         */
        private static void emitIfcSpaceFromL2(Connection conn) throws SQLException {
            int spaceCount = 0;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT DISTINCT esl.storey, esl.room_name, esl.bom_line_role
                     FROM co_empty_space_line esl
                     WHERE esl.bom_level = 2
                       AND esl.storey IS NOT NULL
                       AND (esl.room_name IS NOT NULL OR esl.bom_line_role IS NOT NULL)
                     """)) {
                while (rs.next()) {
                    String storey = rs.getString("storey");
                    String roomName = rs.getString("room_name");
                    if (roomName == null) roomName = rs.getString("bom_line_role");
                    if (roomName == null) continue;

                    String storeyGuid = "STOREY_" + storey.toUpperCase().replace(" ", "_");
                    String spaceGuid = "SPACE_" + storey.toUpperCase().replace(" ", "_")
                                     + "_" + roomName.toUpperCase().replace(" ", "_");

                    // Check storey exists in spatial_structure (skip if not)
                    try (PreparedStatement check = conn.prepareStatement(
                            "SELECT 1 FROM spatial_structure WHERE guid = ?")) {
                        check.setString(1, storeyGuid);
                        try (ResultSet crs = check.executeQuery()) {
                            if (!crs.next()) continue; // storey not in output — skip
                        }
                    }

                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT OR IGNORE INTO spatial_structure VALUES (?, ?, ?, ?, ?, ?)")) {
                        ps.setString(1, spaceGuid);
                        ps.setString(2, "IfcSpace");
                        ps.setString(3, roomName);
                        ps.setString(4, storeyGuid);
                        ps.setString(5, null); // object_type
                        ps.setString(6, roomTypeToPredefined(roomName));
                        ps.execute();
                        spaceCount++;
                    }
                }
            }
            if (spaceCount > 0) {
                conn.commit();
                System.out.printf("[SPATIAL] Emitted %d IfcSpace rows from L2 ESLines%n", spaceCount);
            }
        }

        /** Map room name/role to IFC PredefinedType for IfcSpace. */
        private static String roomTypeToPredefined(String roomName) {
            if (roomName == null) return null;
            String upper = roomName.toUpperCase();
            if (upper.contains("LIVING") || upper.contains("LI"))     return "LIVING";
            if (upper.contains("BEDROOM") || upper.contains("BD"))    return "BEDROOM";
            if (upper.contains("KITCHEN") || upper.contains("KT"))    return "KITCHEN";
            if (upper.contains("BATH") || upper.contains("BT"))       return "BATHROOM";
            if (upper.contains("DINING") || upper.contains("DN"))     return "DINING";
            if (upper.contains("GARAGE"))                             return "GARAGE";
            if (upper.contains("CORRIDOR") || upper.contains("HALL")) return "CORRIDOR";
            return null;
        }

        /**
         * Gap #6: Populate rel_contained_in_space using centroid-in-AABB matching.
         *
         * <p>Two passes:
         * <ol>
         *   <li>Storey-level: elements matched to IfcBuildingStorey by storey name (existing logic)</li>
         *   <li>Room-level: elements matched to IfcSpace by centroid within L2 ESLine AABB.
         *       When an element centroid falls inside multiple overlapping room AABBs (e.g.
         *       full-floor fallback rooms), the <b>smallest AABB wins</b> (by floor area),
         *       with {@code esl.line_id} as deterministic tiebreaker.</li>
         * </ol>
         *
         * <p>Room-level match overrides storey-level (INSERT OR REPLACE).
         */
        private static void populateSpaceContainment(Connection conn) throws SQLException {
            // Pass 1: storey-level containment (same as original Phase X-Ray)
            int storeyContained;
            try (Statement stmt = conn.createStatement()) {
                storeyContained = stmt.executeUpdate("""
                    INSERT OR IGNORE INTO rel_contained_in_space (element_guid, space_guid)
                    SELECT em.guid, ss.guid
                    FROM elements_meta em
                    JOIN spatial_structure ss ON em.storey = ss.name
                    WHERE ss.type = 'IfcBuildingStorey'
                    """);
            }

            // Pass 2: room-level containment — match element centroids to L2 ESLine AABBs
            // The AABB in co_empty_space_line is in mm; elements_rtree is in meters.
            // Smallest-AABB-wins: ROW_NUMBER by floor area ASC, line_id ASC for determinism.
            int roomContained;
            try (Statement stmt = conn.createStatement()) {
                roomContained = stmt.executeUpdate("""
                    INSERT OR REPLACE INTO rel_contained_in_space (element_guid, space_guid)
                    SELECT element_guid, space_guid
                    FROM (
                        SELECT em.guid AS element_guid,
                               'SPACE_' || REPLACE(UPPER(esl.storey),' ','_')
                                 || '_' || REPLACE(UPPER(COALESCE(esl.room_name, esl.bom_line_role)),' ','_') AS space_guid,
                               ROW_NUMBER() OVER (
                                   PARTITION BY em.guid
                                   ORDER BY (esl.next_x_mm - esl.before_x_mm) * (esl.next_y_mm - esl.before_y_mm) ASC,
                                            esl.line_id ASC
                               ) AS rn
                        FROM elements_meta em
                        JOIN elements_rtree er ON em.id = er.id
                        JOIN co_empty_space_line esl ON esl.bom_level = 2
                          AND esl.storey IS NOT NULL
                          AND (esl.room_name IS NOT NULL OR esl.bom_line_role IS NOT NULL)
                        JOIN spatial_structure ss
                          ON ss.guid = 'SPACE_' || REPLACE(UPPER(esl.storey),' ','_')
                                         || '_' || REPLACE(UPPER(COALESCE(esl.room_name, esl.bom_line_role)),' ','_')
                        WHERE ((er.minX + er.maxX) / 2.0) * 1000.0 BETWEEN esl.before_x_mm AND esl.next_x_mm
                          AND ((er.minY + er.maxY) / 2.0) * 1000.0 BETWEEN esl.before_y_mm AND esl.next_y_mm
                          AND ((er.minZ + er.maxZ) / 2.0) * 1000.0 BETWEEN esl.before_z_mm AND esl.next_z_mm
                    ) WHERE rn = 1
                    """);
            }
            conn.commit();
            System.out.printf("[CONTAIN] Spatial containment: %d storey-level, %d room-level%n",
                storeyContained, roomContained);
        }

        /**
         * Create C_Order in output.db from BuildingEntry (C_DocType domain config).
         * C_Order is transactional — created fresh each compile. Not copied from BOM.db.
         * C_OrderLine is NOT pre-populated — generated at compile time from BOM explosion.
         *
         * <p>Source: C_DocType in BOM.db (constant domain config).
         * Target: c_order in output.db (transactional, self-contained).
         */
        private static void copyCOrderToOutput(Connection outConn, CompilationContext ctx) {
            String buildingId = ctx.buildingId();
            var entry = ctx.entry();
            try (PreparedStatement ins = outConn.prepareStatement("""
                    INSERT INTO c_order (
                        C_Order_ID, Name, DSLContent,
                        OutputDbPath, ReferenceDbPath, IsActive, SeqNo,
                        ExpectedElements, Provenance, Description,
                        GeometryFailThreshold, DocStatus,
                        AabbWidthMm, AabbDepthMm, AabbHeightMm,
                        CompiledAt, CompilerVersion, C_DocType_ID
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,datetime('now'),?,?)
                    """)) {
                ins.setString(1, buildingId);              // C_Order_ID = ProjectName
                ins.setString(2, entry.name());            // Name
                ins.setString(3, entry.dslContent());      // DSLContent
                ins.setString(4, entry.outputDbPath());    // OutputDbPath
                ins.setString(5, entry.referenceDbPath()); // ReferenceDbPath
                ins.setInt(6, entry.isActive() ? 1 : 0);  // IsActive
                ins.setInt(7, entry.seqNo());              // SeqNo
                ins.setInt(8, entry.expectedElements());   // ExpectedElements
                ins.setString(9, entry.provenance());      // Provenance
                ins.setString(10, entry.description());    // Description
                ins.setInt(11, entry.geometryFailThreshold());
                ins.setString(12, "IP");                   // DocStatus = In Progress
                ins.setObject(13, entry.aabbWidthMm() > 0 ? entry.aabbWidthMm() : null);
                ins.setObject(14, entry.aabbDepthMm() > 0 ? entry.aabbDepthMm() : null);
                ins.setObject(15, entry.aabbHeightMm() > 0 ? entry.aabbHeightMm() : null);
                ins.setString(16, "BIM-Compiler-1.0");     // CompilerVersion
                ins.setString(17, entry.docTypeId());      // C_DocType_ID
                ins.executeUpdate();

                System.out.printf("[C_ORDER] Created C_Order for %s (DocType=%s) in output.db%n",
                    buildingId, entry.docTypeId());
            } catch (SQLException e) {
                System.err.printf("[C_ORDER] WARN: Failed to create C_Order for %s: %s%n",
                    buildingId, e.getMessage());
            }
            // NOTE: c_orderline NOT populated here — C_OrderLine generated at compile time
            // from BOM explosion, not copied from BOM.db (redundant data removed).
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
                         SpatialDigest = ?,
                         ExpectedElements = ?,
                         EmptySpaceChecksum = ?,
                         DocStatus = 'CO'
                     WHERE C_Order_ID = ?
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
            if ("ST".equals(ctx.entry().docSubType())) {
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
