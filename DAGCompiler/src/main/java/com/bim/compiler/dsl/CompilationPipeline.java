package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingRegistry.BuildingEntry;
import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.validation.GeometryIntegrityChecker;
import com.bim.compiler.validation.PlacementProver;
import com.bim.compiler.validation.SpatialDigest;

import com.bim.orm.BIMLogger;
import com.bim.orm.ModelQuery;
import com.bim.ormsandbox.po.BomTemplateComposer;
import com.bim.ormsandbox.po.MBomCategory;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Single compilation pipeline — one engine, N buildings.
 *
 * <h3>ANTI-DRIFT: BOM DB path</h3>
 * <p>All BOM DB access uses {@code System.getProperty("bom.db")} — NO hardcoded path.
 * The shell script passes {@code -Dbom.db=library/_SH_compile.db} (or _DX_, _TE_).
 * There is NO monolithic "BOM.db". Each building gets a strongly-typed temp compile DB.
 * <p>Before modifying, read the analysis doc for the target building:
 * SH → DATA_MODEL.md, DX → DuplexAnalysis.md, TE → TerminalAnalysis.md.
 *
 * <p>9-step pipeline as typed {@link CompilerStage} chain:
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
        boolean proverSkipped
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
        // Auto-create timestamped log file for this run
        String logTag = entry.name() + "_" + entry.provenance().toLowerCase();
        BIMLogger.initForRun(logTag);

        CompilationContext ctx = new CompilationContext(entry);

        BIMLogger.info("PIPELINE", "=".repeat(70));
        BIMLogger.info("PIPELINE", "PIPELINE: {} [{}]", entry.name(), entry.provenance());
        BIMLogger.info("PIPELINE", "=".repeat(70));

        for (int i = 0; i < STAGES.size(); i++) {
            CompilerStage stage = STAGES.get(i);
            BIMLogger.info("PIPELINE", "-".repeat(70));
            BIMLogger.stage(i + 1, stage.name(), "starting");
            if (stage.shouldSkip(ctx)) {
                BIMLogger.info("PIPELINE", "[SKIP] {}", stage.name());
                continue;
            }
            stage.execute(ctx);
        }

        BIMLogger.info("PIPELINE", "=".repeat(70));
        BIMLogger.info("PIPELINE", "PIPELINE COMPLETE: {} — {} elements", entry.name(), ctx.elementCount());
        BIMLogger.info("PIPELINE", "=".repeat(70));
        String logPath = BIMLogger.getLogFilePath();
        if (logPath != null) {
            BIMLogger.info("PIPELINE", "Log saved: {}", logPath);
        }
        BIMLogger.close();
        return ctx.toResult();
    }

    // =====================================================================
    // Stage implementations (pipeline-internal)
    // =====================================================================

    /**
     * Template composition stage — activated when DocSubType='ST'.
     *
     * <p>When the three-key match (AABB + M_Product_Category + DocSubType) finds no
     * BUILDING BOM, the template path takes over. It queries M_BomCategory
     * WHERE doc_type='RE' AND doc_sub_type='ST' to find Standard Template
     * entries (ST-SH, ST-DX), AABB-matches to pick the right one, then
     * delegates to {@link BomTemplateComposer} which walks the
     * M_BomCategoryLine template tree to select best-fit BOMs at every level.
     *
     * <p>The composition report is stored in context so WriteStage can populate
     * Spatial slot cache at L2+ (room level from template leaf selections — compiler-internal).
     *
     * <p>Skipped for all non-ST buildings — those use the direct BUILDING BOM path.
     *
     * @see BomTemplateComposer
     * @see <a href="scripts/run_RosettaStones.sh">Prime Rule data set</a>
     */
    private static class TemplateStage implements CompilerStage {
        @Override public String name() { return "TEMPLATE COMPOSITION"; }

        /**
         * TemplateStage is skipped when M_Product_Category is not 'ST'.
         * ST = Standard Template: m_product_category_id='ST' on BUILDING BOM.
         * Template entries: C_DocType ST_SH/ST_DX have doc_sub_type=TE/SH etc.
         */
        @Override
        public boolean shouldSkip(CompilationContext ctx) {
            return !"ST".equals(ctx.entry().mProductCategoryId());
        }

        @Override
        public void execute(CompilationContext ctx) throws Exception {
            // ── AABB resolution for ST entries ──────────────────────────
            //
            // ST entries have AABB=0 from BuildingRegistry because the LEFT JOIN
            // to m_bom matches on m_product_category_id + doc_sub_type, and no BUILDING
            // BOM has doc_sub_type='ST'. So we resolve the AABB from M_BomCategory:
            //
            //   M_BomCategory WHERE doc_type='RE' AND doc_sub_type='ST'
            //   → Two records: ST-SH (16868×8668×3945) and ST-DX (9215×26565×7885)
            //   → AABB alone distinguishes which building variant to compose
            //
            // In production, C_Order.AABB comes from the user/designer. Here in
            // the test harness, we resolve from M_BomCategory as the source of truth.
            int widthMm, depthMm, heightMm;
            try (Connection bomConn2 = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"))) {
                MBomCategory tplCat = MBomCategory.getByDocTypeAndSubType(
                    bomConn2, ctx.entry().mProductCategoryId(), ctx.entry().docSubType());
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

            try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"))) {
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
     * Passes M_Product_Category through (RE/CO/IN). ST is a DocSubType, resolved by BomTemplateComposer via AABB match.
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

        /**
         * CO mode: skip — BOM is source of truth, StoreyCompiler not needed.
         *
         * <p>StoreyCompiler generates bay slabs and calls
         * {@link PlacementLoader#markConsumed markConsumed()} with non-unique
         * element_ref (product type name), which causes extracted BOM slabs to
         * be dropped in {@code emitGlobalPlacementElements}.  For CO buildings
         * the BOM already contains all structural slabs with correct positions
         * from the extraction — no compiler-generated slabs needed.
         *
         * <p>Creates a minimal {@link BuildingSpec} so WriteStage can still run
         * the extracted placement path ({@code emitGlobalPlacementElements}).
         *
         * @see <a href="docs/TerminalAnalysis.md">Spec 2 (REVISED)</a>
         */
        @Override
        public boolean shouldSkip(CompilationContext ctx) {
            if ("CO".equals(ctx.entry().mProductCategoryId())) {
                ctx.setSpec(new BuildingSpec(ctx.entry().projectName(), List.of(), null));
                return true;
            }
            return false;
        }

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

                // Implementing BBC.md §14.3 IDV-1 V.5 — Witness: W-TIER2-ORDERLINE
                // Copy BOM tree C_OrderLine from compile DB → output DB (was discarded before S91)
                copyCOrderLineToOutput(conn, ctx);

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

                // Room slots from M_BOM_Line dx/dy/dz (MANIFESTO.md §Three Concerns)
                List<SpatialStructureBuilder.RoomSlot> roomSlots = computeRoomSlots(conn, ctx);

                // Gap #8 + #5 + #6: Spatial structure (storeys, spaces, containment)
                // Delegated to SpatialStructureBuilder (shared with BUILD SPATIAL STRUCTURE verb)
                SpatialStructureBuilder ssb = new SpatialStructureBuilder(conn, roomSlots);
                SpatialStructureBuilder.Result ssResult = ssb.buildAll();
                if (ssResult.storeysAdded() > 0)
                    System.out.printf("[SPATIAL] Gap #8: added %d missing storeys from elements_meta%n", ssResult.storeysAdded());
                if (ssResult.spacesEmitted() > 0)
                    System.out.printf("[SPATIAL] Emitted %d IfcSpace rows from room slots%n", ssResult.spacesEmitted());
                System.out.printf("[CONTAIN] Spatial containment: %d storey-level, %d room-level%n",
                    ssResult.storeyContained(), ssResult.roomContained());
            }
        }

        /**
         * Compute room-level spatial slots from M_BOM_Line dx/dy/dz
         * (MANIFESTO.md §Three Concerns — WHERE = M_BOM_Line).
         *
         * <p>Walks the BUILDING BOM hierarchy to find room-category children,
         * resolves AABB from ad_room_boundary, and returns in-memory RoomSlots
         * for SpatialStructureBuilder (IfcSpace emission + containment).
         */
        private static List<SpatialStructureBuilder.RoomSlot> computeRoomSlots(
                Connection conn, CompilationContext ctx) {
            List<SpatialStructureBuilder.RoomSlot> slots = new ArrayList<>();
            String buildingId = ctx.buildingId();
            BuildingSpec spec = ctx.spec();

            try {
                // 1. Get building AABB from compiled R*Tree (meters → mm)
                double minX, minY, minZ, maxX, maxY, maxZ;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(
                         "SELECT MIN(minX), MIN(minY), MIN(minZ), MAX(maxX), MAX(maxY), MAX(maxZ) FROM elements_rtree")) {
                    if (!rs.next() || rs.getObject(1) == null) {
                        System.out.println("[ROOM_SLOTS] No elements in R*Tree — skipping room slots");
                        return slots;
                    }
                    minX = rs.getDouble(1); minY = rs.getDouble(2); minZ = rs.getDouble(3);
                    maxX = rs.getDouble(4); maxY = rs.getDouble(5); maxZ = rs.getDouble(6);
                }

                double originXMm = minX * 1000.0;
                double originYMm = minY * 1000.0;
                double originZMm = minZ * 1000.0;
                double widthMm   = (maxX - minX) * 1000.0;
                double depthMm   = (maxY - minY) * 1000.0;

                // 2. Look up BUILDING BOM by DocSubType
                String bldgBomId = null;
                String docSubType = ctx.entry().docSubType();
                try (Connection libConn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"))) {
                    if (docSubType != null) {
                        MBOM bldgBom = MBOM.getBuildingBom(libConn, docSubType);
                        if (bldgBom != null) bldgBomId = bldgBom.getBomId();
                    }
                    if (bldgBomId == null && "ST".equals(ctx.entry().mProductCategoryId())) {
                        BomTemplateComposer.CompositionReport tmplReport = ctx.compositionReport();
                        if (tmplReport != null) {
                            String gfOwner = tmplReport.selections().stream()
                                .filter(s -> "GF".equals(s.categoryId()) && s.selectedOwner() != null)
                                .map(BomTemplateComposer.NodeSelection::selectedOwner)
                                .findFirst().orElse(null);
                            if (gfOwner != null) {
                                MBOM ownerBldg = MBOM.getBuildingBom(libConn, gfOwner);
                                if (ownerBldg != null) bldgBomId = ownerBldg.getBomId();
                            }
                        }
                    }
                }
                if (bldgBomId == null) {
                    System.out.printf("[ROOM_SLOTS] No BUILDING BOM found for %s — skipping%n", buildingId);
                    return slots;
                }

                // 3. Walk BUILDING BOM children, compute storey Z positions, collect L2 rooms
                try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"))) {
                    List<MBOMLine> children = new ModelQuery<>(bomConn, MBOMLine::new, MBOMLine.Table_Name)
                        .where("bom_id = ? AND is_active = 1", bldgBomId)
                        .orderBy("sequence").list();

                    int storeyIdx = 0;
                    double anchorZ = originZMm;
                    for (MBOMLine po : children) {
                        String childBomId = po.getChildProductId();
                        String role = po.getRole();
                        double dzM = po.getDz();

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

                        // L2: room-level children for non-ST buildings
                        if (storey != null && childBomId != null
                                && !"ST".equals(docSubType)) {
                            collectRoomSlots(slots, bomConn, buildingId,
                                childBomId, docSubType, storey,
                                originXMm, originYMm, beforeZ,
                                widthMm, depthMm, nextZ - beforeZ);
                        }
                    }
                }

                // L2 slots from template composition (ST mode only)
                BomTemplateComposer.CompositionReport report = ctx.compositionReport();
                if ("ST".equals(docSubType) && report != null) {
                    for (BomTemplateComposer.NodeSelection sel : report.selections()) {
                        if (!sel.isLeaf() || sel.selectedBomId() == null) continue;
                        slots.add(new SpatialStructureBuilder.RoomSlot(
                            null, null, sel.categoryId(),
                            originXMm, originYMm, originZMm,
                            originXMm + sel.allocW(), originYMm + sel.allocD(), originZMm + sel.allocH()));
                    }
                }

                System.out.printf("[ROOM_SLOTS] %s: %d room slots computed from M_BOM_Line%n",
                    buildingId, slots.size());

            } catch (SQLException e) {
                System.err.printf("[ROOM_SLOTS] WARN: Failed to compute room slots for %s: %s%n",
                    buildingId, e.getMessage());
            }
            return slots;
        }

        /** Room-content roles have storeys; structural roles don't. */
        private static boolean isRoomContent(String role) {
            return role != null && (role.contains("LEVEL") || role.contains("GROUND_FLOOR"));
        }

        /**
         * Collect room-level slots: room-category children of a floor BOM.
         * AABB from ad_room_boundary if available; falls back to storey AABB.
         */
        private static void collectRoomSlots(
                List<SpatialStructureBuilder.RoomSlot> slots,
                Connection bomConn, String buildingId,
                String floorBomId, String docSubType, String storey,
                double originXMm, double originYMm, double floorZMm,
                double widthMm, double depthMm, double heightMm) throws SQLException {

            String SQL_ROOM_CHILDREN = """
                SELECT mbl.child_product_id, mbl.role, mbl.sequence, mb2.m_product_category_id
                FROM m_bom_line mbl
                LEFT JOIN m_bom mb2 ON mb2.Value = mbl.child_product_id
                WHERE mbl.bom_id = ? AND mbl.is_active = 1
                  AND mb2.m_product_category_id IN ('LI','BD','KT','BT','DN')
                ORDER BY mbl.sequence
                """;

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
                                  && b.getProductCategory() != null)
                        .map(MBOM::getBomId)
                        .findFirst().orElse(floorBomId);
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
                    while (rsRoom.next()) {
                        String role   = rsRoom.getString("role");
                        String bomCat = rsRoom.getString("m_product_category_id");

                        String roomType = categoryToRoomType(bomCat);
                        double rMinX = originXMm, rMaxX = originXMm + widthMm;
                        double rMinY = originYMm, rMaxY = originYMm + depthMm;

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
                                }
                            }
                        }

                        slots.add(new SpatialStructureBuilder.RoomSlot(
                            storey, role, role,
                            rMinX, rMinY, floorZMm,
                            rMaxX, rMaxY, floorZMm + heightMm));
                    }
                }
            }
        }

        /** Map BOM category code to ad_room_boundary room_type. */
        private static String categoryToRoomType(String productCategory) {
            return switch (productCategory) {
                case "LI" -> "LIVING";
                case "BD" -> "BEDROOM";
                case "KT" -> "KITCHEN";
                case "BT" -> "BATHROOM";
                case "DN" -> "DINING";
                default   -> productCategory;
            };
        }

        /**
         * Create C_Order in output.db from BuildingEntry (C_DocType domain config).
         * C_Order is transactional — created fresh each compile. Not copied from BOM.db.
         * C_OrderLine is NOT pre-populated — generated at compile time from BOM explosion.
         *
         * <p>Source: C_DocType in BOM.db (constant domain config).
         * Target: c_order in output.db (transactional, self-contained).
         *
         * <p>Direct SQL — same pattern as copyCOrderLineToOutput().
         * RegisterBuildingVerb remains for interactive Designer mode (BIM_COBOL on classpath).
         */
        // Implementing BBC.md §14.3 — Witness: W-TIER2-ORDER
        private static void copyCOrderToOutput(Connection outConn, CompilationContext ctx) {
            String buildingId = ctx.buildingId();
            var entry = ctx.entry();

            String sql = """
                INSERT OR IGNORE INTO c_order (
                    Value, Name, DSLContent,
                    OutputDbPath, ReferenceDbPath, IsActive, SeqNo,
                    ExpectedElements, Provenance, Description,
                    GeometryFailThreshold, DocStatus,
                    AabbWidthMm, AabbDepthMm, AabbHeightMm,
                    CompiledAt, CompilerVersion, C_DocType_ID
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,datetime('now'),?,?)
                """;

            try (PreparedStatement ps = outConn.prepareStatement(sql)) {
                ps.setString(1, buildingId);                          // Value
                ps.setString(2, entry.name());                        // Name
                ps.setString(3, entry.dslContent() != null ? entry.dslContent() : "");  // DSLContent
                ps.setString(4, entry.outputDbPath());                // OutputDbPath
                ps.setString(5, entry.referenceDbPath());             // ReferenceDbPath
                ps.setInt(6, entry.isActive() ? 1 : 0);              // IsActive
                ps.setInt(7, entry.seqNo());                          // SeqNo
                ps.setInt(8, entry.expectedElements());               // ExpectedElements
                ps.setString(9, entry.provenance());                  // Provenance
                ps.setString(10, entry.description());                // Description
                ps.setInt(11, entry.geometryFailThreshold());         // GeometryFailThreshold
                ps.setString(12, "IP");                               // DocStatus
                ps.setObject(13, entry.aabbWidthMm() > 0 ? entry.aabbWidthMm() : null);   // AabbWidthMm
                ps.setObject(14, entry.aabbDepthMm() > 0 ? entry.aabbDepthMm() : null);   // AabbDepthMm
                ps.setObject(15, entry.aabbHeightMm() > 0 ? entry.aabbHeightMm() : null); // AabbHeightMm
                ps.setString(16, "BIM-Compiler-1.0");                 // CompilerVersion
                ps.setString(17, entry.docTypeId());                  // C_DocType_ID
                ps.executeUpdate();

                System.out.printf("[C_ORDER] Created C_Order for %s (DocType=%s, DocStatus=IP) in output.db%n",
                    buildingId, entry.docTypeId());
            } catch (Exception e) {
                System.err.printf("[C_ORDER] WARN: Failed to create C_Order for %s: %s%n",
                    buildingId, e.getMessage());
            }
        }

        /**
         * Copy C_OrderLine BOM tree from compile DB → output DB.
         * BomDropper creates C_OrderLine in compile DB (C_Order_ID = docTypeId).
         * Output DB uses C_Order_ID = projectName (buildingId). Remap during copy.
         */
        private static void copyCOrderLineToOutput(Connection outConn, CompilationContext ctx) {
            String compileDbPath = System.getProperty("bom.db");
            if (compileDbPath == null) return;

            String srcOrderId = ctx.entry().docTypeId();     // compile DB key
            String dstOrderId = ctx.buildingId();            // output DB key

            try (Connection compileDb = DriverManager.getConnection("jdbc:sqlite:" + compileDbPath)) {
                String select = """
                    SELECT C_OrderLine_ID, Parent_OrderLine_ID, Line, family_ref, host_type,
                           m_product_category_id, bom_child_id, dx, dy, dz,
                           aabb_width_mm, aabb_depth_mm, aabb_height_mm,
                           M_Product_ID, Discipline, AD_Org_ID, Qty, locator_ref, is_reference_class
                    FROM C_OrderLine WHERE C_Order_ID = ? ORDER BY C_OrderLine_ID
                    """;

                String insert = """
                    INSERT INTO c_orderline
                    (C_OrderLine_ID, C_Order_ID, Parent_OrderLine_ID, Line, family_ref, host_type,
                     m_product_category_id, bom_child_id, dx, dy, dz,
                     aabb_width_mm, aabb_depth_mm, aabb_height_mm,
                     M_Product_ID, Discipline, AD_Org_ID, Qty, locator_ref, is_reference_class)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;

                int copied = 0;
                try (PreparedStatement ps = compileDb.prepareStatement(select)) {
                    ps.setString(1, srcOrderId);
                    try (ResultSet rs = ps.executeQuery();
                         PreparedStatement ins = outConn.prepareStatement(insert)) {
                        while (rs.next()) {
                            ins.setInt(1, rs.getInt(1));           // C_OrderLine_ID
                            ins.setString(2, dstOrderId);          // C_Order_ID (remapped)
                            ins.setObject(3, rs.getObject(2));     // Parent_OrderLine_ID
                            ins.setInt(4, rs.getInt(3));           // Line
                            ins.setString(5, rs.getString(4));     // family_ref
                            ins.setString(6, rs.getString(5));     // host_type
                            ins.setString(7, rs.getString(6));     // m_product_category_id
                            ins.setObject(8, rs.getObject(7));     // bom_child_id
                            ins.setDouble(9, rs.getDouble(8));     // dx
                            ins.setDouble(10, rs.getDouble(9));    // dy
                            ins.setDouble(11, rs.getDouble(10));   // dz
                            ins.setObject(12, rs.getObject(11));   // aabb_width_mm
                            ins.setObject(13, rs.getObject(12));   // aabb_depth_mm
                            ins.setObject(14, rs.getObject(13));   // aabb_height_mm
                            ins.setString(15, rs.getString(14));   // M_Product_ID
                            ins.setString(16, rs.getString(15));   // Discipline
                            ins.setObject(17, rs.getObject(16));   // AD_Org_ID
                            ins.setInt(18, rs.getInt(17));         // Qty
                            ins.setString(19, rs.getString(18));   // locator_ref
                            ins.setObject(20, rs.getObject(19));   // is_reference_class
                            ins.executeUpdate();
                            copied++;
                        }
                    }
                }
                if (copied > 0) {
                    System.out.printf("[C_ORDERLINE] Copied %d rows to output.db (order %s → %s)%n",
                        copied, srcOrderId, dstOrderId);
                }
            } catch (Exception e) {
                System.err.printf("[C_ORDERLINE] WARN: Failed to copy C_OrderLine: %s%n", e.getMessage());
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

            // Write computed results back to output.db c_order
            updateCOrderComputedResults(dbPath, ctx.buildingId(),
                digestReport.digest(), ctx.elementCount());
        }

        /**
         * UPDATE output.db c_order with computed spatial_digest + expected_elements.
         * Also promotes doc_status from IP → CO (compilation complete, digest known).
         */
        private static void updateCOrderComputedResults(
                String dbPath, String buildingId,
                String spatialDigest, int elementCount) {

            // Build COMPLETE BUILDING verb line
            String verbLine = String.format(
                "COMPLETE BUILDING %s DIGEST %s ELEMENTS %d",
                buildingId,
                spatialDigest != null ? spatialDigest : "null",
                elementCount);

            // Dispatch via SPI (BIM_COBOL on classpath)
            VerbExecutor executor = ServiceLoader.load(VerbExecutor.class)
                    .findFirst().orElse(null);
            if (executor != null) {
                try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"));
                     Connection outConn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
                    VerbExecutor.ExecutionReport report = executor.execute(
                        bomConn, outConn, buildingId, List.of(verbLine));
                    if (report.allPass()) {
                        System.out.printf("[C_ORDER] Updated output.db c_order: digest=%s, elements=%d%n",
                            spatialDigest != null ? spatialDigest.substring(0, 16) + "..." : "null",
                            elementCount);
                    } else {
                        System.err.printf("[C_ORDER] WARN: COMPLETE BUILDING failed for %s: %s%n",
                            buildingId, report.details());
                    }
                } catch (Exception e) {
                    System.err.printf("[C_ORDER] WARN: Failed to update computed results for %s: %s%n",
                        buildingId, e.getMessage());
                }
            } else {
                System.err.printf("[C_ORDER] WARN: No VerbExecutor on classpath — cannot complete %s%n",
                    buildingId);
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

            PlacementProver.ProofReport proofReport = PlacementProver.proveFromDB(
                ctx.entry().outputDbPath(), ctx.entry().projectName());
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
        }
    }

    private static int queryInt(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
