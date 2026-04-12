package com.bim.ifctobom;

import com.bim.orm.BIMLogger;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * Extracts MEP joint piece vocabulary from RosettaStone extracted DBs
 * and writes to ERP.db.
 *
 * <h3>Implementing DISC_VALIDATION_DB_SRS.md §6.12.2 — Witness: W-J1-EXTRACT</h3>
 *
 * <p>Phase J1: Joint piece vocabulary extraction from IFC RosettaStones.
 * Phase J2: Shim product definitions per discipline x host surface.
 *
 * <p>Each MEP IFC element maps to a joint piece type:
 * <ul>
 *   <li>IfcPipeSegment → PIPE_STRAIGHT (diameter from AABB)</li>
 *   <li>IfcPipeFitting → PIPE_TEE / PIPE_ELBOW / PIPE_REDUCER / PIPE_CROSS / PIPE_COUPLING</li>
 *   <li>IfcDuctSegment → DUCT_STRAIGHT</li>
 *   <li>IfcDuctFitting → DUCT_TEE / DUCT_ELBOW / DUCT_TRANSITION / DUCT_CROSS / DUCT_TAKEOFF</li>
 *   <li>IfcFireSuppressionTerminal → SPRINKLER_HEAD</li>
 *   <li>IfcAirTerminal → AIR_DIFFUSER / AIR_GRILLE</li>
 *   <li>IfcFlowTerminal → PLUMBING_FIXTURE</li>
 *   <li>IfcFlowController → VALVE</li>
 *   <li>IfcLightFixture → LIGHT_FIXTURE</li>
 *   <li>IfcFlowSegment → PIPE_STRAIGHT (IFC2x3 generic)</li>
 *   <li>IfcFlowFitting → PIPE_FITTING (IFC2x3 generic)</li>
 * </ul>
 *
 * <p>Toolbox grows across fleet — each building adds new types via INSERT OR IGNORE.
 * Reads *_extracted.db (never modifies). Writes to ERP.db only.
 *
 * <h3>Cardinal Rules — MEP_RECIPE Design</h3>
 * <ol>
 *   <li><b>MEP_RECIPE is abstract and reusable.</b> Each archetype encodes a geometric
 *       PATTERN — standoff from host surface (coverage topology) or chain offset sequence
 *       (routing topology). A recipe is valid for any building of the same type, not
 *       tied to a specific building instance. Never add per-instance data to a recipe.</li>
 *   <li><b>Validation Rules determine final expression.</b> The recipe is the input.
 *       DV rules (AD_Rule / M_BOM Validation Layer) are what resolve a recipe into the
 *       specific quantities, tolerances, and placements for a given project. Do not
 *       bake project-specific logic into recipe rows.</li>
 *   <li><b>Coverage topology:</b> group by (disc, ifcClass, dz_band). One archetype
 *       per band — the shim line (seq=10, dz=0) plus the element line (seq=20,
 *       dz=element_cz − ceiling_Z). All elements in the same band share one archetype.</li>
 *   <li><b>Routing topology:</b> one recipe per spatial chain (storey + disc + run).
 *       The chain captures the routing path; individual element types within the chain
 *       are separate lines (seq=20,30,...). Chain identity survives building updates.</li>
 *   <li><b>INSERT OR IGNORE on re-extract.</b> Re-running joint-extract on a building
 *       must not duplicate recipes. The bom_id is the idempotency key.</li>
 * </ol>
 */
public class IFCtoERP {

    private static final String TAG = "IFCtoERP";

    /**
     * Resolve buildingType → C_BPartner_ID.
     * buildingType IS C_BPartner.Value (iDempiere: lookup by Value, use _ID as FK).
     */
    static int resolveBPartnerId(Connection erpConn, String buildingType) throws SQLException {
        try (PreparedStatement ps = erpConn.prepareStatement(
                "SELECT C_BPartner_ID FROM C_BPartner WHERE Value = ?")) {
            ps.setString(1, buildingType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    BIMLogger.fine(TAG, "C_BPartner: {}[{}]", buildingType, id);
                    return id;
                }
            }
        }
        BIMLogger.warn(TAG, "{}: no C_BPartner_ID resolved — MEP recipes will have NULL partner", buildingType);
        return 0;
    }

    /** Last MEP-SPACE inference result — used by linkRecipesToSpaces(). */
    private static volatile MepSpaceResult lastSpaceResult = MepSpaceResult.EMPTY;

    /** MEP IFC classes we extract joint pieces from. */
    private static final Set<String> MEP_CLASSES = Set.of(
            "IfcPipeSegment", "IfcPipeFitting",
            "IfcDuctSegment", "IfcDuctFitting",
            "IfcFireSuppressionTerminal", "IfcAirTerminal",
            "IfcFlowTerminal", "IfcFlowController", "IfcLightFixture",
            "IfcFlowSegment", "IfcFlowFitting"  // IFC2x3 generic
    );

    /**
     * Run joint piece extraction for a building.
     *
     * @param erpConn       writable connection to ERP.db
     * @param buildingType  e.g. "Terminal"
     * @return result with counts
     */
    public static Result extract(Connection erpConn, String buildingType) throws SQLException {
        Path refDb = Path.of("DAGCompiler/lib/input", buildingType + "_extracted.db");
        if (!Files.exists(refDb)) {
            BIMLogger.warn(TAG, "{}: reference DB not found: {} — skipping", buildingType, refDb);
            return new Result(0, 0, 0);
        }

        // Create staging table
        createStagingTable(erpConn);

        // Read MEP elements from extracted DB (read-only)
        List<MepElement> mepElements = readMepElements(refDb);
        BIMLogger.fine(TAG, "{}: read {} MEP elements from {}", buildingType, mepElements.size(), refDb);
        if (mepElements.isEmpty()) {
            BIMLogger.info(TAG, "{}: no MEP elements — skipping", buildingType);
            return new Result(0, 0, 0);
        }

        // Log per-class breakdown for forensics
        Map<String, Integer> classCounts = new LinkedHashMap<>();
        for (MepElement e : mepElements) {
            classCounts.merge(e.ifcClass, 1, Integer::sum);
        }
        BIMLogger.fine(TAG, "{}: MEP class breakdown: {}", buildingType, classCounts);

        // S148: MEP-SPACE — infer room function from furniture containment, map MEP elements to spaces
        // Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 — grep 'MEP-SPACE' for session pickup
        lastSpaceResult = emitMepSpaceLog(refDb, buildingType);

        // Classify and aggregate into joint piece types
        Map<String, JointPieceType> types = classifyElements(mepElements, buildingType);

        // Log piece type breakdown
        Map<String, Integer> pieceTypeCounts = new LinkedHashMap<>();
        for (JointPieceType t : types.values()) {
            pieceTypeCounts.merge(t.pieceType, t.count, Integer::sum);
        }
        BIMLogger.fine(TAG, "{}: piece type breakdown: {}", buildingType, pieceTypeCounts);

        // Write staging rows
        int staged = writeStagingRows(erpConn, types);
        BIMLogger.fine(TAG, "{}: {} joint types staged to _import_joint_piece_types", buildingType, staged);

        // Promote to M_Product (INSERT OR IGNORE — toolbox grows)
        int promoted = promoteToProduct(erpConn);
        // Backfill M_Product_Category_ID for newly promoted products
        // (ProductRegistrar's backfill ran before IFCtoERP — these products missed it)
        // Implementing DISC_VALIDATION_DB_SRS.md §6.4 — Witness: W-DISC-CAT
        try (Statement stmt = erpConn.createStatement()) {
            stmt.executeUpdate("""
                    UPDATE M_Product
                    SET M_Product_Category_ID = (
                        SELECT c.M_Product_Category_ID
                        FROM M_Product_Category c
                        WHERE c.IFC_Class = M_Product.ifc_class
                        LIMIT 1
                    )
                    WHERE M_Product_Category_ID IS NULL
                      AND ifc_class IS NOT NULL
                    """);
        }
        BIMLogger.info(TAG, "{}: {} MEP elements → {} joint types, {} new M_Products",
                buildingType, mepElements.size(), staged, promoted);

        // C: Extract anchor points for RouteWalker (§6.12.3 §1)
        extractAnchors(erpConn, buildingType, refDb);

        // B: Seed pattern table (idempotent — INSERT OR IGNORE)
        seedMepPatterns(erpConn);

        return new Result(mepElements.size(), staged, promoted);
    }

    /**
     * Extract MEP anchor points from extracted DB and write to ad_mep_anchor.
     * Implementing DISC_VALIDATION_DB_SRS.md §6.12.3 §1 — Witness: W-PATTERN-CW/W-PATTERN-SP pre-check
     */
    private static void extractAnchors(Connection erpConn, String buildingType, Path refDb)
            throws SQLException {
        // Create table if not yet created by migration
        try (Statement stmt = erpConn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ad_mep_anchor (
                        anchor_id    TEXT PRIMARY KEY,
                        source_building TEXT NOT NULL,
                        anchor_type  TEXT NOT NULL CHECK(anchor_type IN ('METER','FIXTURE','VALVE','GENERIC')),
                        x_m          REAL NOT NULL,
                        y_m          REAL NOT NULL,
                        z_m          REAL NOT NULL,
                        storey       TEXT,
                        ifc_guid     TEXT
                    )""");
        }

        // Collect storey floor Z values from IfcSlab for METER boundary detection
        Set<Double> slabZValues = new LinkedHashSet<>();
        String refUrl = "jdbc:sqlite:" + refDb;
        try (Connection refConn = DriverManager.getConnection(refUrl);
             Statement stmt = refConn.createStatement()) {
            ResultSet rs = stmt.executeQuery("""
                    SELECT MIN(r.minZ) as floor_z FROM elements_meta em
                    JOIN elements_rtree r ON em.id = r.id
                    WHERE em.ifc_class = 'IfcSlab'
                    GROUP BY em.storey
                    """);
            while (rs.next()) slabZValues.add(rs.getDouble(1));
        } catch (SQLException ignored) { /* no slabs — skip METER detection */ }

        // Read anchor candidate elements
        String insertSql = """
                INSERT OR IGNORE INTO ad_mep_anchor
                (anchor_id, source_building, anchor_type, x_m, y_m, z_m, storey, ifc_guid)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        int meterCount = 0, fixtureCount = 0, valveCount = 0, genericCount = 0, skippedCount = 0;
        int anchorSeq = 0;

        try (Connection refConn = DriverManager.getConnection(refUrl);
             Statement stmt = refConn.createStatement();
             PreparedStatement ins = erpConn.prepareStatement(insertSql)) {

            ResultSet rs = stmt.executeQuery("""
                    SELECT em.guid, em.ifc_class, em.element_type, em.element_name,
                           em.storey, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
                    FROM elements_meta em
                    JOIN elements_rtree r ON em.id = r.id
                    WHERE em.ifc_class IN (
                        'IfcFlowController','IfcFlowTerminal',
                        'IfcFlowSegment','IfcFlowFitting')
                    """);

            while (rs.next()) {
                String guid     = rs.getString(1);
                String ifcClass = rs.getString(2);
                String elType   = rs.getString(3);
                String elName   = rs.getString(4);
                String storey   = rs.getString(5);
                double minX = rs.getDouble(6),  maxX = rs.getDouble(7);
                double minY = rs.getDouble(8),  maxY = rs.getDouble(9);
                double minZ = rs.getDouble(10), maxZ = rs.getDouble(11);

                String anchorType = classifyAnchorType(
                        ifcClass, elType, elName, minZ, maxZ, slabZValues);

                if (anchorType == null) { skippedCount++; continue; } // ELEC — skip

                double dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;
                double maxDim = Math.max(dx, Math.max(dy, dz));
                double minDim = Math.min(dx, Math.min(dy, dz));
                boolean elongated = minDim > 0 && maxDim > 3.0 * minDim;

                if (elongated) {
                    // Emit two anchors at AABB ends along dominant axis
                    double[] start = dominantStart(dx, dy, dz, minX, maxX, minY, maxY, minZ, maxZ);
                    double[] end   = dominantEnd(dx, dy, dz, minX, maxX, minY, maxY, minZ, maxZ);
                    writeAnchor(ins, buildingType + "_" + (++anchorSeq) + "_A", buildingType,
                            anchorType, start[0], start[1], start[2], storey, guid);
                    writeAnchor(ins, buildingType + "_" + (++anchorSeq) + "_B", buildingType,
                            anchorType, end[0], end[1], end[2], storey, guid);
                } else {
                    // Emit one anchor at AABB centre
                    writeAnchor(ins, buildingType + "_" + (++anchorSeq), buildingType,
                            anchorType,
                            (minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0,
                            storey, guid);
                }

                switch (anchorType) {
                    case "METER"   -> meterCount++;
                    case "FIXTURE" -> fixtureCount++;
                    case "VALVE"   -> valveCount++;
                    default        -> genericCount++;
                }
            }
        }

        BIMLogger.info(TAG, "[IFCtoERP] {}: anchors extracted — METER={}, FIXTURE={}, VALVE={}, GENERIC={}, SKIPPED={}",
                buildingType, meterCount, fixtureCount, valveCount, genericCount, skippedCount);

        if (fixtureCount == 0 || meterCount == 0) {
            BIMLogger.warn(TAG, "[IFCtoERP] {}: WARNING — FIXTURE={} METER={}: RouteWalker has no anchor pair to connect",
                    buildingType, fixtureCount, meterCount);
        }
    }

    /** Classify a MEP element into an anchor type (null = skip). */
    private static String classifyAnchorType(String ifcClass, String elType, String elName,
                                              double minZ, double maxZ, Set<Double> slabZs) {
        if ("IfcFlowController".equals(ifcClass)) return "VALVE";

        if ("IfcFlowTerminal".equals(ifcClass)) {
            String et = (elType != null ? elType : "").toLowerCase();
            String en = (elName != null ? elName : "").toLowerCase();
            // Skip light fixtures — ELEC, not a pipe anchor
            if (et.contains("light") || et.contains("lamp") || et.contains("fixture")
                    || en.contains("light") || en.contains("lamp")) {
                return null;
            }
            return "FIXTURE";
        }

        // IfcFlowSegment / IfcFlowFitting: check storey boundary for METER candidate
        double cz = (minZ + maxZ) / 2.0;
        for (double slabZ : slabZs) {
            if (Math.abs(cz - slabZ) <= 0.200) return "METER";
        }
        return "GENERIC";
    }

    private static double[] dominantStart(double dx, double dy, double dz,
                                           double minX, double maxX, double minY, double maxY,
                                           double minZ, double maxZ) {
        if (dx >= dy && dx >= dz) return new double[]{minX, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0};
        if (dy >= dx && dy >= dz) return new double[]{(minX + maxX) / 2.0, minY, (minZ + maxZ) / 2.0};
        return new double[]{(minX + maxX) / 2.0, (minY + maxY) / 2.0, minZ};
    }

    private static double[] dominantEnd(double dx, double dy, double dz,
                                         double minX, double maxX, double minY, double maxY,
                                         double minZ, double maxZ) {
        if (dx >= dy && dx >= dz) return new double[]{maxX, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0};
        if (dy >= dx && dy >= dz) return new double[]{(minX + maxX) / 2.0, maxY, (minZ + maxZ) / 2.0};
        return new double[]{(minX + maxX) / 2.0, (minY + maxY) / 2.0, maxZ};
    }

    private static void writeAnchor(PreparedStatement ins, String anchorId, String sourceBuilding,
                                     String anchorType, double x, double y, double z,
                                     String storey, String guid) throws SQLException {
        ins.setString(1, anchorId);
        ins.setString(2, sourceBuilding);
        ins.setString(3, anchorType);
        ins.setDouble(4, x);
        ins.setDouble(5, y);
        ins.setDouble(6, z);
        ins.setString(7, storey);
        ins.setString(8, guid);
        ins.executeUpdate();
    }

    /**
     * Seed ad_mep_pattern table with mined topology patterns.
     * Patterns mined from Terminal (TE) per 00q-B analysis.
     * Called from extract() — INSERT OR IGNORE is idempotent across re-runs.
     * Note: DV_RM_rules.sql is regenerated by extract_validation_rules.sh on each pipeline run,
     * so pattern seed lives here in Java for persistence.
     * Implementing DISC_VALIDATION_DB_SRS.md §6.12.3 §2
     */
    private static void seedMepPatterns(Connection erpConn) throws SQLException {
        try (Statement stmt = erpConn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ad_mep_pattern (
                        pattern_id       TEXT NOT NULL,
                        discipline       TEXT NOT NULL,
                        building_type    TEXT NOT NULL,
                        sequence         INTEGER NOT NULL,
                        from_node_type   TEXT NOT NULL,
                        to_node_type     TEXT NOT NULL,
                        direction_axis   TEXT NOT NULL,
                        piece_type       TEXT NOT NULL,
                        offset_rule      TEXT,
                        gradient         REAL,
                        notes            TEXT,
                        source_building  TEXT,
                        PRIMARY KEY (pattern_id, sequence)
                    )""");
        }

        // Patterns mined from Terminal_Extracted.db:
        //   CW: 619 IfcPipeSegment + 638 IfcPipeFitting; axis X=639, Y=356, Z=262;
        //       7 IfcFlowController + 57 IfcValve; 106 IfcFlowTerminal fixtures
        //   SP: 455 IfcPipeSegment + 372 IfcPipeFitting; axis X=364, Y=289, Z=174;
        //       62 vertical stacks; 150 IfcFlowTerminal; gradient 0.005–0.023 (below MS 1228 §5.3)
        String[][] patterns = {
            // CW_TERMINAL_01 — Cold Water supply (Terminal)
            {"CW_TERMINAL_01","CW","TERMINAL","10","METER","JUNCTION","X","PIPE_STRAIGHT",
             "DIRECT",  "null", "Main distribution from meter; 7 IfcFlowController+57 IfcValve=64 nodes",
             "Terminal"},
            {"CW_TERMINAL_01","CW","TERMINAL","20","JUNCTION","JUNCTION","X","PIPE_STRAIGHT",
             "DIRECT",  "null", "Horizontal main run; 639 X-dominant IfcPipeSegment CW",
             "Terminal"},
            {"CW_TERMINAL_01","CW","TERMINAL","30","JUNCTION","JUNCTION","Y","PIPE_STRAIGHT",
             "DIRECT",  "null", "Cross-branch Y run; 356 Y-dominant IfcPipeSegment CW",
             "Terminal"},
            {"CW_TERMINAL_01","CW","TERMINAL","40","JUNCTION","FIXTURE","Z","PIPE_STRAIGHT",
             "DIRECT",  "null", "Drop to fixture; 262 Z-dominant IfcPipeSegment, 106 IfcFlowTerminal CW",
             "Terminal"},
            // SP_TERMINAL_01 — Sanitary/Sewage drainage (Terminal)
            {"SP_TERMINAL_01","SP","TERMINAL","10","FIXTURE","JUNCTION","X","PIPE_STRAIGHT",
             "DIRECT",  "0.025","150 IfcFlowTerminal SP (floor traps 46, sinks 20, toilets 16, etc.)",
             "Terminal"},
            {"SP_TERMINAL_01","SP","TERMINAL","20","JUNCTION","JUNCTION","X","PIPE_STRAIGHT",
             "GRADIENT","0.025","TENTATIVE: TE observed gradient 0.005-0.023 < MS 1228 §5.3 min 0.025",
             "Terminal"},
            {"SP_TERMINAL_01","SP","TERMINAL","30","JUNCTION","JUNCTION","Y","PIPE_STRAIGHT",
             "GRADIENT","0.025","289 Y-dominant IfcPipeSegment SP (cross-branch collection)",
             "Terminal"},
            {"SP_TERMINAL_01","SP","TERMINAL","40","JUNCTION","STACK","Z","PIPE_STRAIGHT",
             "DIRECT",  "null", "62 vertical stack IfcPipeSegment SP (Z-dominant, length > 500mm)",
             "Terminal"},
            {"SP_TERMINAL_01","SP","TERMINAL","50","STACK","GENERIC","X","PIPE_STRAIGHT",
             "GRADIENT","0.025","TENTATIVE: sub-slab discharge run; supporting elements < 5 in TE",
             "Terminal"},
        };

        String sql = """
                INSERT OR IGNORE INTO ad_mep_pattern
                (pattern_id, discipline, building_type, sequence,
                 from_node_type, to_node_type, direction_axis, piece_type,
                 offset_rule, gradient, notes, source_building)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement ps = erpConn.prepareStatement(sql)) {
            for (String[] p : patterns) {
                ps.setString(1, p[0]);
                ps.setString(2, p[1]);
                ps.setString(3, p[2]);
                ps.setInt(4, Integer.parseInt(p[3]));
                ps.setString(5, p[4]);
                ps.setString(6, p[5]);
                ps.setString(7, p[6]);
                ps.setString(8, p[7]);
                ps.setString(9, p[8]);
                ps.setObject(10, "null".equals(p[9]) ? null : Double.parseDouble(p[9]));
                ps.setString(11, p[10]);
                ps.setString(12, p[11]);
                ps.executeUpdate();
            }
        }
    }

    /**
     * Create shim M_Products — one per discipline x host surface.
     * Implementing §6.12.2 Phase J2.
     */
    public static int createShimProducts(Connection erpConn) throws SQLException {
        String[][] shims = {
                // value, name, product_type, ifc_class (host), mount, offset_mm, height_mm
                {"FP_CEILING_SHIM",   "Fire Protection Ceiling Shim",  "SHIM", "IfcCovering", "BOTTOM", "5",    null},
                {"FP_WALL_SHIM",      "Fire Protection Wall Shim",     "SHIM", "IfcWall",     "SIDE",   "0",    "1200"},
                {"ELEC_CEILING_SHIM", "Electrical Ceiling Shim",       "SHIM", "IfcCovering", "BOTTOM", "5",    null},
                {"ELEC_WALL_SHIM",    "Electrical Wall Shim",          "SHIM", "IfcWall",     "SIDE",   "0",    "1200"},
                {"CW_CEILING_SHIM",   "Cold Water Ceiling Shim",       "SHIM", "IfcCovering", "BOTTOM", "5",    null},
                {"CW_WALL_SHIM",      "Cold Water Wall Shim",          "SHIM", "IfcWall",     "SIDE",   "0",    "1000"},
                {"SP_FLOOR_SHIM",     "Sanitary Floor Shim",           "SHIM", "IfcSlab",     "TOP",    "0",    null},
                {"SP_WALL_SHIM",      "Sanitary Wall Shim",            "SHIM", "IfcWall",     "SIDE",   "0",    "600"},
                {"ACMV_CEILING_SHIM", "HVAC Ceiling Shim",             "SHIM", "IfcCovering", "BOTTOM", "5",    null},
                {"LPG_WALL_SHIM",     "Gas Piping Wall Shim",          "SHIM", "IfcWall",     "SIDE",   "0",    "500"},
                {"LPG_FLOOR_SHIM",    "Gas Piping Floor Shim",         "SHIM", "IfcSlab",     "TOP",    "0",    null},
        };

        // Shim attribute table (host_ifc_class, mount, offset_mm, height_mm)
        try (Statement stmt = erpConn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS _shim_attributes (
                        product_value    TEXT PRIMARY KEY,
                        host_ifc_class   TEXT NOT NULL,
                        mount            TEXT NOT NULL CHECK(mount IN ('BOTTOM','SIDE','TOP')),
                        offset_mm        REAL DEFAULT 0,
                        height_mm        REAL
                    )""");
        }

        String productSql = """
                INSERT OR IGNORE INTO M_Product
                (product_id, Value, Name, product_type, width, depth, height,
                 ifc_class, extracted_from, is_active)
                VALUES (?, ?, ?, 'SHIM', 0.01, 0.01, 0.005, ?, 'SHIM_DEFINITION', 1)
                """;
        String shimSql = """
                INSERT OR IGNORE INTO _shim_attributes
                (product_value, host_ifc_class, mount, offset_mm, height_mm)
                VALUES (?, ?, ?, ?, ?)
                """;

        int count = 0;
        try (PreparedStatement pStmt = erpConn.prepareStatement(productSql);
             PreparedStatement sStmt = erpConn.prepareStatement(shimSql)) {
            for (String[] s : shims) {
                String value = s[0], name = s[1], hostClass = s[3],
                        mount = s[4], offset = s[5], height = s[6];

                // M_Product row
                pStmt.setString(1, value);
                pStmt.setString(2, value);
                pStmt.setString(3, name);
                pStmt.setString(4, hostClass);
                int rows = pStmt.executeUpdate();
                if (rows > 0) {
                    count++;
                    BIMLogger.fine(TAG, "SHIM created: {} host={} mount={} offset={}mm height={}",
                            value, hostClass, mount, offset, height);
                }

                // Shim attributes
                sStmt.setString(1, value);
                sStmt.setString(2, hostClass);
                sStmt.setString(3, mount);
                sStmt.setDouble(4, Double.parseDouble(offset));
                if (height != null) {
                    sStmt.setDouble(5, Double.parseDouble(height));
                } else {
                    sStmt.setNull(5, Types.REAL);
                }
                sStmt.executeUpdate();
            }
        }
        BIMLogger.info(TAG, "{} shim M_Products created ({} total defined)", count, shims.length);
        return count;
    }

    // ── internals ───────────────────────────────────────────────────

    private static void createStagingTable(Connection erpConn) throws SQLException {
        try (Statement stmt = erpConn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS _import_joint_piece_types (
                        id INTEGER PRIMARY KEY,
                        ifc_class TEXT NOT NULL,
                        element_type TEXT,
                        piece_type TEXT NOT NULL,
                        diameter_mm REAL,
                        length_mm REAL,
                        angle_deg REAL,
                        material TEXT,
                        source_building TEXT,
                        count INTEGER DEFAULT 1,
                        discipline TEXT,
                        UNIQUE(piece_type, diameter_mm, material)
                    )""");
            // G1 migration: add discipline column to pre-existing table (ALTER TABLE IF NOT EXISTS)
            // SQLite does not support IF NOT EXISTS on ALTER TABLE — catch and ignore duplicate column error
            try { stmt.execute("ALTER TABLE _import_joint_piece_types ADD COLUMN discipline TEXT"); }
            catch (SQLException ignored) { /* column already exists — expected on re-runs */ }
        }
    }

    private static List<MepElement> readMepElements(Path refDb) throws SQLException {
        List<MepElement> result = new ArrayList<>();
        String url = "jdbc:sqlite:" + refDb;
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            // Read-only — never modify extraction DBs
            ResultSet rs = stmt.executeQuery("""
                    SELECT m.ifc_class, m.element_type, m.discipline,
                           r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
                    FROM elements_meta m
                    JOIN elements_rtree r ON m.id = r.id
                    WHERE m.ifc_class IN (
                        'IfcPipeSegment','IfcPipeFitting',
                        'IfcDuctSegment','IfcDuctFitting',
                        'IfcFireSuppressionTerminal','IfcAirTerminal',
                        'IfcFlowTerminal','IfcFlowController','IfcLightFixture',
                        'IfcFlowSegment','IfcFlowFitting')
                    """);
            while (rs.next()) {
                double minX = rs.getDouble(4), maxX = rs.getDouble(5);
                double minY = rs.getDouble(6), maxY = rs.getDouble(7);
                double minZ = rs.getDouble(8), maxZ = rs.getDouble(9);
                double dx = (maxX - minX) * 1000; // metres → mm
                double dy = (maxY - minY) * 1000;
                double dz = (maxZ - minZ) * 1000;
                result.add(new MepElement(
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        dx, dy, dz));
            }
        }
        return result;
    }

    /**
     * Classify MEP elements into joint piece types with representative dimensions.
     * Key = piece_type + rounded diameter + material
     */
    private static Map<String, JointPieceType> classifyElements(
            List<MepElement> elements, String buildingType) {
        Map<String, JointPieceType> types = new LinkedHashMap<>();

        for (MepElement e : elements) {
            String pieceType = classifyPieceType(e.ifcClass, e.elementType);
            if (pieceType == null) continue; // ELEC light fixture — skip (Task D)
            double diameter = estimateDiameter(e);
            double length = estimateLength(e);
            String material = extractMaterial(e.elementType);
            // A1: discipline from elements_meta first, fallback to heuristic
            String discipline = discFromClass(e.ifcClass, e.elementType, e.discipline);

            // Round diameter to nearest 5mm for grouping
            double roundedDia = Math.round(diameter / 5.0) * 5.0;
            String key = pieceType + "_" + (int) roundedDia + "MM"
                    + (material != null ? "_" + material : "");

            types.merge(key, new JointPieceType(
                    e.ifcClass, e.elementType, pieceType,
                    roundedDia, length, 0, material, buildingType, 1, discipline
            ), (a, b) -> new JointPieceType(
                    a.ifcClass, a.elementType, a.pieceType,
                    a.diameterMm, Math.max(a.lengthMm, b.lengthMm),
                    a.angleDeg, a.material, a.sourceBuilding,
                    a.count + b.count, a.discipline));

        }
        return types;
    }

    /**
     * Classify IFC class + element_type into a piece type name.
     * Element types from TE follow pattern: "familyName:typeName"
     * e.g. "jkrME_pip-ft_tee_threaded:Poly Steel"
     */
    static String classifyPieceType(String ifcClass, String elementType) {
        String etLower = elementType != null ? elementType.toLowerCase() : "";

        return switch (ifcClass) {
            case "IfcPipeSegment" -> "PIPE_STRAIGHT";
            case "IfcPipeFitting" -> classifyPipeFitting(etLower);
            case "IfcDuctSegment" -> "DUCT_STRAIGHT";
            case "IfcDuctFitting" -> classifyDuctFitting(etLower);
            case "IfcFireSuppressionTerminal" -> classifySprinkler(etLower);
            case "IfcAirTerminal" -> classifyAirTerminal(etLower);
            case "IfcFlowTerminal" -> classifyFlowTerminal(etLower);
            case "IfcFlowController" -> "VALVE";
            case "IfcLightFixture" -> "LIGHT_FIXTURE";
            case "IfcFlowSegment" -> "FLOW_SEGMENT";       // IFC2x3 generic
            case "IfcFlowFitting" -> "FLOW_FITTING";       // IFC2x3 generic
            default -> "MEP_ELEMENT";
        };
    }

    private static String classifyPipeFitting(String et) {
        if (et.contains("tee"))        return "PIPE_TEE";
        if (et.contains("elbow"))      return "PIPE_ELBOW";
        if (et.contains("transition")) return "PIPE_REDUCER";
        if (et.contains("cross"))      return "PIPE_CROSS";
        if (et.contains("coupling"))   return "PIPE_COUPLING";
        if (et.contains("flange"))     return "PIPE_FLANGE";
        return "PIPE_FITTING";
    }

    private static String classifyDuctFitting(String et) {
        if (et.contains("tee"))        return "DUCT_TEE";
        if (et.contains("elbow"))      return "DUCT_ELBOW";
        if (et.contains("transition")) return "DUCT_TRANSITION";
        if (et.contains("cross"))      return "DUCT_CROSS";
        if (et.contains("takeoff"))    return "DUCT_TAKEOFF";
        if (et.contains("wye"))        return "DUCT_WYE";
        return "DUCT_FITTING";
    }

    private static String classifySprinkler(String et) {
        if (et.contains("hose"))  return "HOSE_REEL";
        if (et.contains("smoke")) return "SMOKE_DETECTOR";
        return "SPRINKLER_HEAD";
    }

    private static String classifyAirTerminal(String et) {
        if (et.contains("diffuser")) return "AIR_DIFFUSER";
        if (et.contains("grille") || et.contains("grill")) return "AIR_GRILLE";
        return "AIR_TERMINAL";
    }

    private static String classifyFlowTerminal(String et) {
        // G3: light fixture names → null (ELEC, not a pipe anchor — keeps CW recipe count honest)
        if (et.contains("light") || et.contains("lamp") || et.contains("luminaire"))
            return null;
        if (et.contains("toilet") || et.contains("wc"))       return "TOILET";
        if (et.contains("urinal"))                              return "URINAL";
        if (et.contains("sink") || et.contains("basin"))       return "SINK";
        if (et.contains("shower"))                              return "SHOWER";
        if (et.contains("tap") || et.contains("faucet"))       return "TAP";
        if (et.contains("trap"))                                return "FLOOR_TRAP";
        if (et.contains("bidet"))                               return "BIDET_SPRAY";
        if (et.contains("interceptor") || et.contains("grease")) return "GREASE_TRAP";
        if (et.contains("inspection"))                          return "INSPECTION_CHAMBER";
        return "PLUMBING_FIXTURE";
    }

    /**
     * Estimate pipe/duct diameter from AABB.
     * For pipes: the two smallest AABB dimensions approximate diameter.
     * For fittings: similar heuristic.
     */
    private static double estimateDiameter(MepElement e) {
        double[] dims = {e.dx, e.dy, e.dz};
        Arrays.sort(dims);
        // Diameter ~ average of two smallest dims (cross-section)
        return (dims[0] + dims[1]) / 2.0;
    }

    /** Estimate length from AABB — the largest dimension. */
    private static double estimateLength(MepElement e) {
        return Math.max(e.dx, Math.max(e.dy, e.dz));
    }

    /**
     * Extract material from element_type string.
     * TE pattern: "familyName:materialOrType" → take after last colon,
     * clean up to standard material name.
     */
    static String extractMaterial(String elementType) {
        if (elementType == null || elementType.isBlank()) return null;
        int colon = elementType.lastIndexOf(':');
        if (colon < 0 || colon == elementType.length() - 1) return null;
        String raw = elementType.substring(colon + 1).trim();

        // Normalize known materials
        String lower = raw.toLowerCase();
        if (lower.contains("poly steel"))   return "POLY_STEEL";
        if (lower.contains("upvc"))         return "UPVC";
        if (lower.contains("hdpe"))         return "HDPE";
        if (lower.contains("carbon steel")) return "CARBON_STEEL";
        if (lower.contains("abs"))          return "ABS";
        if (lower.contains("vcp"))          return "VCP";
        if (lower.contains("standard"))     return null; // generic, no material
        // For non-pipe elements (light fixtures, terminals), skip material
        if (raw.length() > 30) return null;
        return raw.toUpperCase().replaceAll("[^A-Z0-9_]", "_");
    }

    // ── MEP BOM recipe building (J1 tack offsets) ───────────────────

    /**
     * Build shim-rooted M_BOM recipes in ERP.db from spatial chain detection.
     * Implementing DISC_VALIDATION_DB_SRS.md §6.12.2 §7 — Witness: W-J1-TACK
     *
     * @param erpConn      writable connection to ERP.db
     * @param buildingType e.g. "HospitalAuckland"
     * @return RecipeResult with run/line/shim counts
     */
    public static RecipeResult buildMepBomRecipes(
            Connection erpConn, String buildingType) throws SQLException {
        // Chain geometry correctness — Witness: W-J1-GEO
        // Implementing DISC_VALIDATION_DB_SRS.md §6.12.2 §7

        // DDL — extend existing M_BOM/M_BOM_Line with tack + geometry columns (ALTER TABLE, idempotent)
        try (Statement stmt = erpConn.createStatement()) {
            for (String alter : new String[]{
                    "ALTER TABLE M_BOM ADD COLUMN bom_type TEXT",
                    "ALTER TABLE M_BOM ADD COLUMN source_building TEXT",
                    "ALTER TABLE M_BOM_Line ADD COLUMN dx REAL DEFAULT 0",
                    "ALTER TABLE M_BOM_Line ADD COLUMN dy REAL DEFAULT 0",
                    "ALTER TABLE M_BOM_Line ADD COLUMN dz REAL DEFAULT 0",
                    "ALTER TABLE M_BOM_Line ADD COLUMN c_uom_id TEXT DEFAULT 'EA'",
                    "ALTER TABLE M_BOM_Line ADD COLUMN qty_type TEXT DEFAULT 'FIXED'",
                    "ALTER TABLE M_BOM_Line ADD COLUMN rotation_rule REAL DEFAULT 0.0"}) {
                try { stmt.execute(alter); }
                catch (SQLException ignored) {} // column already exists
            }
        }

        Path refDb = Path.of("DAGCompiler/lib/input", buildingType + "_extracted.db");
        if (!Files.exists(refDb)) {
            BIMLogger.warn(TAG, "{}: reference DB not found for recipe build — skipping", buildingType);
            return new RecipeResult(0, 0, 0);
        }

        String prefix = buildBomPrefix(buildingType);
        int bpartnerId = resolveBPartnerId(erpConn, buildingType);

        // 1. Read MEP elements with world positions from extracted DB (read-only)
        List<MepPosElement> elements = readMepElementsWithPositions(refDb);
        if (elements.isEmpty()) {
            BIMLogger.info(TAG, "{}: no MEP elements with positions — skipping recipe build", buildingType);
            return new RecipeResult(0, 0, 0);
        }
        BIMLogger.fine(TAG, "{}: {} MEP elements loaded for recipe build", buildingType, elements.size());

        // 2. Coverage topology detection — W-J1-GEO §7
        // Buildings with >80% null storey (terminals, warehouses) have no routing topology.
        // Write one archetype per (disc, ifcClass, dz_band) instead of spatial chains.
        long nullStoreyCount = elements.stream()
                .filter(e -> e.storey == null || e.storey.isBlank()).count();
        boolean coverageTopology = (nullStoreyCount * 100 / elements.size()) > 80;

        // 3. Load structural AABBs for penetration gap detection (routing mode only)
        List<double[]> structuralAabbs = coverageTopology
                ? List.of() : loadStructuralAabbs(refDb);

        // 4. Idempotency: delete stale recipes before rebuild
        int runsBuilt = 0, linesWritten = 0, shimAnchors = 0;
        Map<String, Integer> runCounters = new LinkedHashMap<>();
        try (PreparedStatement delLines = erpConn.prepareStatement(
                "DELETE FROM M_BOM_Line WHERE M_BOM_ID IN " +
                "(SELECT M_BOM_ID FROM M_BOM WHERE source_building = ?)")) {
            delLines.setString(1, buildingType);
            BIMLogger.fine(TAG, "[GEO] {}: deleted {} stale M_BOM_Line rows", buildingType, delLines.executeUpdate());
        }
        try (PreparedStatement delBoms = erpConn.prepareStatement(
                "DELETE FROM M_BOM WHERE source_building = ?")) {
            delBoms.setString(1, buildingType);
            BIMLogger.fine(TAG, "[GEO] {}: deleted {} stale M_BOM rows", buildingType, delBoms.executeUpdate());
        }

        // ── COVERAGE TOPOLOGY BRANCH ─────────────────────────────────
        if (coverageTopology) {
            double ceilingZ = structuralAabbs.isEmpty()
                    ? elements.stream().mapToDouble(e -> e.cz).max().orElse(0.0)
                    : loadStructuralAabbs(refDb).stream().mapToDouble(a -> a[5]).max().orElse(0.0);
            System.out.printf("[IFCtoERP][GEO] %s: COVERAGE TOPOLOGY — %d%% null storey, ceiling_Z=%.3fm%n",
                    buildingType, nullStoreyCount * 100 / elements.size(), ceilingZ);

            // Group by (disc, ifcClass, dz_standoff in 50mm bands)
            Map<String, List<MepPosElement>> archetypes = new LinkedHashMap<>();
            for (MepPosElement e : elements) {
                String disc = discFromClass(e.ifcClass, e.elementType, e.discipline);
                double dz = Math.round((e.cz - ceilingZ) * 20.0) / 20.0; // 50mm band
                archetypes.computeIfAbsent(disc + "|" + e.ifcClass + "|" + dz,
                        k -> new ArrayList<>()).add(e);
            }

            for (var entry : archetypes.entrySet()) {
                String[] kp = entry.getKey().split("\\|", 3);
                String disc = kp[0];
                double dzBand = Double.parseDouble(kp[2]);
                MepPosElement rep = entry.getValue().get(0);
                String pid = resolveProductIdFor(erpConn, rep);
                if (pid == null) continue;

                int archNum = runCounters.merge(prefix + "_" + disc, 1, Integer::sum);
                String bomId = prefix + "_" + disc + "_ARCH_" + archNum;
                try (PreparedStatement ps = erpConn.prepareStatement("""
                        INSERT INTO M_BOM (bom_id, Value, Name, bom_type, source_building, C_BPartner_ID, BOMType, IsActive)
                        VALUES (?, ?, ?, 'MEP_RECIPE', ?, ?, 'MEP', 'Y')""")) {
                    ps.setString(1, bomId); ps.setString(2, bomId);
                    ps.setString(3, disc + " archetype dz=" + String.format("%.3f", dzBand));
                    ps.setString(4, buildingType);
                    if (bpartnerId > 0) ps.setInt(5, bpartnerId); else ps.setNull(5, java.sql.Types.INTEGER);
                    ps.executeUpdate();
                } catch (SQLException ignored) {}

                insertMBomLine(erpConn, bomId, discToShim(disc), 10, 0.0, 0.0, 0.0, 0.0, "EA", "FIXED", 1);
                insertMBomLine(erpConn, bomId, pid, 20, 0.0, 0.0, dzBand, 0.0, "EA", "FIXED", 1);
                linesWritten += 2; shimAnchors++; runsBuilt++;
            }
            System.out.printf("[IFCtoERP] %s: %d archetypes (coverage topology), ceiling_Z=%.3fm%n",
                    prefix, archetypes.size(), ceilingZ);
            BIMLogger.info(TAG, "{}: coverage topology — {} archetypes, {} lines",
                    buildingType, runsBuilt, linesWritten);
            return new RecipeResult(runsBuilt, linesWritten, shimAnchors);
        }

        // ── ROUTING TOPOLOGY BRANCH ──────────────────────────────────
        // Group by (storey, disc)
        // Implementing DISC_VALIDATION_DB_SRS.md §11.4 G3 — Witness: W-TE-DISC
        Map<String, List<MepPosElement>> groups = new LinkedHashMap<>();
        for (MepPosElement e : elements) {
            String disc = discFromClass(e.ifcClass, e.elementType, e.discipline);
            groups.computeIfAbsent(e.storey + "|" + disc, k -> new ArrayList<>()).add(e);
        }

        for (var entry : groups.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            String storey = parts[0], disc = parts[1];
            String storeyCode = toStoreyCode(storey);
            List<MepPosElement> group = new ArrayList<>(entry.getValue());

            // FP pendant sprinklers hang vertically — force Z regardless of variance
            char axis = "FP".equals(disc) ? 'Z' : dominantAxisOf(group);
            Comparator<MepPosElement> sorter = switch (axis) {
                case 'X' -> Comparator.comparingDouble(e -> e.cx);
                case 'Y' -> Comparator.comparingDouble(e -> e.cy);
                default  -> Comparator.comparingDouble(e -> e.cz);
            };
            group.sort(sorter);

            // Detect chains with penetration gap merging (wall/slab gaps up to 600mm)
            List<List<MepPosElement>> rawChains = detectChains(group, axis, structuralAabbs);

            // Split mega-chains at direction changes
            String groupLabel = prefix + "_" + disc + "_" + storeyCode;
            List<List<MepPosElement>> splitChains = new ArrayList<>();
            for (List<MepPosElement> raw : rawChains)
                splitChains.addAll(splitByDirectionChange(raw, axis, 2.0, groupLabel));

            // Geometry guards: discard degenerate and scattered chains — W-J1-GEO
            int chainsFull = 0;
            for (List<MepPosElement> chain : splitChains) {
                if (chain.size() < 2) continue;

                // Guard 1: total offset < 10mm → degenerate, do not write
                double totalOffset = 0.0;
                for (int i = 1; i < chain.size(); i++) {
                    MepPosElement p = chain.get(i - 1), c = chain.get(i);
                    totalOffset += Math.abs(c.cx - p.cx) + Math.abs(c.cy - p.cy) + Math.abs(c.cz - p.cz);
                }
                if (totalOffset < 0.010) {
                    System.out.printf("[IFCtoERP][GEO] %s: DISCARD degenerate — %d pieces, total offset=%.3fm%n",
                            groupLabel, chain.size(), totalOffset);
                    continue;
                }

                // Guard 2: collinearity R² < 0.5 → scattered, do not write
                double r2 = collinearityR2(chain);
                if (r2 < 0.5) {
                    System.out.printf("[IFCtoERP][GEO] %s: DISCARD collinearity R²=%.2f — %d pieces (scattered)%n",
                            groupLabel, r2, chain.size());
                    continue;
                }
                if (r2 < 0.8) {
                    System.out.printf("[IFCtoERP][GEO] %s: WARN collinearity R²=%.2f — %d pieces (approx linear)%n",
                            groupLabel, r2, chain.size());
                }
                chainsFull++;

                int runNum = runCounters.merge(prefix + "_" + disc + "_" + storeyCode, 1, Integer::sum);
                String bomId = prefix + "_" + disc + "_" + storeyCode + "_RUN_" + runNum;

                try (PreparedStatement ps = erpConn.prepareStatement("""
                        INSERT INTO M_BOM (bom_id, Value, Name, bom_type, source_building, C_BPartner_ID, BOMType, IsActive)
                        VALUES (?, ?, ?, 'MEP_RECIPE', ?, ?, 'MEP', 'Y')""")) {
                    ps.setString(1, bomId); ps.setString(2, bomId);
                    ps.setString(3, disc + " run " + storeyCode + " #" + runNum);
                    ps.setString(4, buildingType);
                    if (bpartnerId > 0) ps.setInt(5, bpartnerId); else ps.setNull(5, java.sql.Types.INTEGER);
                    ps.executeUpdate();
                } catch (SQLException ignored) {}
                runsBuilt++;

                // Shim at seq=10 (anchor) — world position stored as origin for convergence proof
                MepPosElement first = chain.get(0);
                insertMBomLine(erpConn, bomId, discToShim(disc), 10, 0.0, 0.0, 0.0, 0.0, "EA", "FIXED", 1);
                shimAnchors++; linesWritten++;

                // §6.12.4: Store shim world position for convergence proof
                try (PreparedStatement originPs = erpConn.prepareStatement(
                        "UPDATE M_BOM SET origin_x=?, origin_y=?, origin_z=? WHERE Value=?")) {
                    originPs.setDouble(1, first.cx);
                    originPs.setDouble(2, first.cy);
                    originPs.setDouble(3, first.cz);
                    originPs.setString(4, bomId);
                    originPs.executeUpdate();
                } catch (SQLException ignored) {}

                // First piece at seq=20 (at shim position, zero offset)
                String pid0 = resolveProductIdFor(erpConn, first);
                if (pid0 != null) {
                    boolean isStraight0 = pid0.contains("STRAIGHT");
                    int qty0 = isStraight0
                            ? (int)(Math.abs(axis == 'X' ? first.maxX - first.minX
                                           : axis == 'Y' ? first.maxY - first.minY
                                           : first.maxZ - first.minZ) * 1000) : 1;
                    insertMBomLine(erpConn, bomId, pid0, 20, 0.0, 0.0, 0.0, 0.0,
                            isStraight0 ? "MM" : "EA", isStraight0 ? "VARIABLE" : "FIXED", qty0);
                    linesWritten++;
                }

                // Remaining pieces: cumulative offsets from first piece (parent-relative)
                // Walker reads dx/dy/dz as offset from parent anchor (shim), not from previous sibling
                StringBuilder dxLog = new StringBuilder("→0");
                double cum = 0.0;
                for (int i = 1; i < chain.size(); i++) {
                    MepPosElement curr = chain.get(i);
                    // Cumulative from first piece — parent-relative offset
                    double dx = curr.cx - first.cx;
                    double dy = curr.cy - first.cy;
                    double dz = curr.cz - first.cz;

                    // Rotation at joints: piece-to-piece delta for direction change detection
                    MepPosElement prev = chain.get(i - 1);
                    double ddx = curr.cx - prev.cx;
                    double ddy = curr.cy - prev.cy;
                    double ddz = curr.cz - prev.cz;
                    double along = switch (axis) {
                        case 'X' -> Math.abs(ddx); case 'Y' -> Math.abs(ddy); default -> Math.abs(ddz);
                    };
                    double pB = switch (axis) { case 'X' -> ddy; case 'Y' -> ddx; default -> ddx; };
                    double pC = switch (axis) { case 'X' -> ddz; case 'Y' -> ddz; default -> ddy; };
                    double perp = Math.sqrt(pB * pB + pC * pC);
                    double rotDeg = (along > 0.0) ? Math.toDegrees(Math.atan2(perp, along)) : 0.0;

                    String pid = resolveProductIdFor(erpConn, curr);
                    if (pid != null) {
                        boolean isStraight = pid.contains("STRAIGHT");
                        int qty = isStraight
                                ? (int)(Math.abs(axis == 'X' ? curr.maxX - curr.minX
                                               : axis == 'Y' ? curr.maxY - curr.minY
                                               : curr.maxZ - curr.minZ) * 1000) : 1;
                        insertMBomLine(erpConn, bomId, pid, (i + 2) * 10, dx, dy, dz, rotDeg,
                                isStraight ? "MM" : "EA", isStraight ? "VARIABLE" : "FIXED", qty);
                        linesWritten++;
                    }
                    cum += (axis == 'X' ? dx : axis == 'Y' ? dy : dz);
                    dxLog.append("→").append(String.format("%.3f", cum));
                }

                BIMLogger.info(TAG, "{}: {} pieces, axis={}, dx={}", bomId, chain.size(), axis, dxLog);
                System.out.printf("[IFCtoERP] %s: %d pieces, axis=%c, dx=%s%n",
                        bomId, chain.size(), axis, dxLog);
            }

            if (chainsFull == 0 && !group.isEmpty()) {
                BIMLogger.warn(TAG, "[GEO] {}/{}/{}: {} elements — all chains degenerate or scattered",
                        buildingType, storey, disc, group.size());
            }
        }

        BIMLogger.info(TAG, "{}: recipe build complete — {} MEP runs, {} lines, {} shim anchors",
                buildingType, runsBuilt, linesWritten, shimAnchors);
        return new RecipeResult(runsBuilt, linesWritten, shimAnchors);
    }

    /**
     * Link MEP recipes to room types via spatial proximity.
     * Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 — Witness: W-SPACE-LINK
     *
     * <p>For each MEP_RECIPE M_BOM, find the last piece's position, determine
     * which room AABB contains it, and write target_space_type_id + anchor_end.
     *
     * <p>Must be called AFTER extract() (which populates lastSpaceResult)
     * and AFTER buildMepBomRecipes() (which creates the recipes).
     */
    public static int linkRecipesToSpaces(Connection erpConn, String buildingType)
            throws SQLException {
        MepSpaceResult spaceResult = lastSpaceResult;
        if (spaceResult.roomCapabilities().isEmpty() || spaceResult.roomAabbs().isEmpty()) {
            BIMLogger.info("MEP-SPACE-LINK", "{}: no space inference — skipping recipe linkage", buildingType);
            return 0;
        }

        // DV040+DV041: ensure columns exist
        try (Statement stmt = erpConn.createStatement()) {
            try { stmt.execute("ALTER TABLE M_BOM ADD COLUMN target_space_type_id TEXT"); }
            catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE M_BOM ADD COLUMN anchor_end TEXT"); }
            catch (SQLException ignored) {}
        }

        // Load discipline→capability mapping from ad_discipline_capability (DV041)
        Map<String, String> discToCap = new LinkedHashMap<>();
        try (Statement stmt = erpConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT discipline, capability FROM ad_discipline_capability")) {
            while (rs.next()) discToCap.put(rs.getString(1), rs.getString(2));
        } catch (SQLException e) {
            // Table may not exist yet — fall back to hardcoded
            discToCap.put("CW", "PLUMBABLE"); discToCap.put("SP", "PLUMBABLE");
            discToCap.put("ELEC", "ELECTRIFIED"); discToCap.put("FP", "FIRE_PROTECTED");
            discToCap.put("ACMV", "VENTILATED"); discToCap.put("LPG", "GAS_SERVED");
        }
        BIMLogger.info("MEP-SPACE-LINK", "{}: discipline→capability: {}", buildingType, discToCap);

        // For each MEP_RECIPE: extract discipline from BOM name, find required capability,
        // match to nearest room that HAS that capability
        int linked = 0, noRoom = 0, noCap = 0;
        try (PreparedStatement bomPs = erpConn.prepareStatement(
                "SELECT M_BOM_ID, Value FROM M_BOM WHERE bom_type='MEP_RECIPE' AND source_building=?");
             PreparedStatement linePs = erpConn.prepareStatement(
                "SELECT dx, dy, dz, child_product_id FROM M_BOM_Line WHERE M_BOM_ID=? ORDER BY sequence DESC LIMIT 1");
             PreparedStatement updatePs = erpConn.prepareStatement(
                "UPDATE M_BOM SET target_space_type_id=?, anchor_end=? WHERE M_BOM_ID=?")) {

            bomPs.setString(1, buildingType);
            try (ResultSet bomRs = bomPs.executeQuery()) {
                while (bomRs.next()) {
                    int bomId = bomRs.getInt(1);
                    String bomValue = bomRs.getString(2);

                    // Extract discipline from BOM name: D_CW_U_RUN_7 → CW
                    String disc = extractDisciplineFromBomValue(bomValue);
                    String requiredCap = discToCap.getOrDefault(disc, "ELECTRIFIED");

                    // Get last piece position
                    linePs.setInt(1, bomId);
                    try (ResultSet lineRs = linePs.executeQuery()) {
                        if (!lineRs.next()) continue;
                        double dx = lineRs.getDouble(1);
                        double dy = lineRs.getDouble(2);
                        double dz = lineRs.getDouble(3);
                        String lastProduct = lineRs.getString(4);

                        // Find nearest room whose capabilities include requiredCap
                        String room = findCapableRoom(spaceResult, dx, dy, dz, requiredCap);
                        if (room == null) {
                            noRoom++;
                            BIMLogger.geo("MEP-SPACE-LINK",
                                "recipe={} disc={} cap={} pos=({:.3f},{:.3f},{:.3f}) → NO ROOM",
                                bomValue, disc, requiredCap, dx, dy, dz);
                            continue;
                        }

                        Set<String> roomCaps = spaceResult.roomCapabilities().getOrDefault(room, Set.of());
                        if (!roomCaps.contains(requiredCap)) {
                            noCap++;
                            continue;
                        }

                        // anchor_end from discipline
                        String anchorEnd = switch (disc) {
                            case "SP" -> "STACK";
                            case "ELEC" -> "PANEL";
                            case "FP" -> "PANEL";
                            default -> "RISER";
                        };

                        // Write capability (not room name) as target_space_type_id
                        updatePs.setString(1, requiredCap);
                        updatePs.setString(2, anchorEnd);
                        updatePs.setInt(3, bomId);
                        updatePs.executeUpdate();
                        linked++;

                        String concreteType = spaceResult.roomSpaceTypes().getOrDefault(room, "?");
                        BIMLogger.info("MEP-SPACE-LINK",
                            "recipe={} disc={} capability={} room={} concrete_type={} anchor_end={}",
                            bomValue, disc, requiredCap, room, concreteType, anchorEnd);
                    }
                }
            }
        }

        // ── CONVERGENCE PROOF: for each MEP_RECIPE, find nearest terminal ──
        // Absolute position = origin (shim world pos) + last piece offset.
        // Compare against terminal world positions. This proves pipes end at fixtures.
        int converged = 0;
        List<Terminal> terminals = spaceResult.terminals();
        try (PreparedStatement bomPs2 = erpConn.prepareStatement(
                "SELECT M_BOM_ID, Value, origin_x, origin_y, origin_z FROM M_BOM "
                + "WHERE bom_type='MEP_RECIPE' AND source_building=?");
             PreparedStatement linePs2 = erpConn.prepareStatement(
                "SELECT dx, dy, dz FROM M_BOM_Line WHERE M_BOM_ID=? ORDER BY sequence DESC LIMIT 1")) {

            bomPs2.setString(1, buildingType);
            try (ResultSet bomRs = bomPs2.executeQuery()) {
                while (bomRs.next()) {
                    int bomId = bomRs.getInt(1);
                    String bomValue = bomRs.getString(2);
                    double ox = bomRs.getDouble(3), oy = bomRs.getDouble(4), oz = bomRs.getDouble(5);
                    String disc = extractDisciplineFromBomValue(bomValue);
                    String reqCap = discToCap.getOrDefault(disc, "ELECTRIFIED");

                    linePs2.setInt(1, bomId);
                    try (ResultSet lr = linePs2.executeQuery()) {
                        if (!lr.next()) continue;
                        // Absolute world position of last piece = shim origin + cumulative offset
                        double wx = ox + lr.getDouble(1);
                        double wy = oy + lr.getDouble(2);
                        double wz = oz + lr.getDouble(3);

                        // Find nearest terminal — full 3D distance (XY proximity alone
                        // doesn't prove connection; a pipe 3m above a sink isn't serving it)
                        Terminal nearest3d = null;
                        double nearestDist3d = Double.MAX_VALUE;
                        Terminal nearestXY = null;
                        double nearestDistXY = Double.MAX_VALUE;
                        for (Terminal t : terminals) {
                            if (!reqCap.equals(t.capability)) continue;
                            double dxy = Math.sqrt((wx-t.cx)*(wx-t.cx) + (wy-t.cy)*(wy-t.cy));
                            double d3d = Math.sqrt((wx-t.cx)*(wx-t.cx) + (wy-t.cy)*(wy-t.cy) + (wz-t.cz)*(wz-t.cz));
                            if (d3d < nearestDist3d) { nearestDist3d = d3d; nearest3d = t; }
                            if (dxy < nearestDistXY) { nearestDistXY = dxy; nearestXY = t; }
                        }

                        if (nearest3d != null) {
                            double dz = Math.abs(wz - nearest3d.cz);
                            String verdict;
                            if (nearestDist3d < 1.0) { verdict = "CONVERGED"; converged++; }
                            else if (nearestDistXY < 1.0 && dz > 1.0) { verdict = "XY_ONLY (needs vertical drop)"; }
                            else if (nearestDist3d < 3.0) { verdict = "NEAR"; converged++; }
                            else { verdict = "FAR"; }
                            BIMLogger.info("MEP-CONVERGE",
                                "recipe={} disc={} endpoint=({:.3f},{:.3f},{:.3f}) terminal={} at ({:.3f},{:.3f},{:.3f}) dist_3d={:.3f}m dist_xy={:.3f}m dz={:.3f}m → {}",
                                bomValue, disc, wx, wy, wz,
                                nearest3d.fixtureType, nearest3d.cx, nearest3d.cy, nearest3d.cz,
                                nearestDist3d, nearestDistXY, dz, verdict);
                        }
                    }
                }
            }
        }

        BIMLogger.info("MEP-SPACE-LINK",
                "{}: {} linked, {} converged (<3m to terminal), {} no room, {} no capability",
                buildingType, linked, converged, noRoom, noCap);
        System.out.printf("[MEP-SPACE-LINK] %s: %d linked, %d converged, %d no room, %d no cap%n",
                buildingType, linked, converged, noRoom, noCap);
        return linked;
    }

    /**
     * Fixture Gap Analysis — emits what each room needs but doesn't have.
     * Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 §6 — Witness: W-SPACE-COVER
     *
     * <p>For each room with a concrete space type (KITCHEN, BATHROOM, etc.),
     * reads ad_space_type_mep_bom for required fixtures. Checks if each fixture
     * has a corresponding MEP recipe targeting that room. Emits:
     * <ul>
     *   <li>SATISFIED — recipe exists serving this fixture type</li>
     *   <li>GAP — no recipe found. Logs placement_rule and computed target position</li>
     * </ul>
     *
     * <p>When gaps are found, emits actionable INSERT statements that a script
     * or user can apply to seed the missing fixture targets.
     *
     * @return number of gaps found (0 = all fixtures covered)
     */
    public static int emitFixtureGapAnalysis(Connection erpConn, String buildingType) {
        MepSpaceResult spaceResult = lastSpaceResult;
        if (spaceResult.roomSpaceTypes().isEmpty()) return 0;

        int gaps = 0, satisfied = 0;
        StringBuilder insertScript = new StringBuilder();
        insertScript.append("-- Fixture gap analysis for ").append(buildingType).append("\n");
        insertScript.append("-- Generated by IFCtoERP.emitFixtureGapAnalysis()\n");
        insertScript.append("-- Apply to ERP.db or use BonsaiBIMDesigner Outliner to set targets\n\n");

        for (Map.Entry<String, String> entry : spaceResult.roomSpaceTypes().entrySet()) {
            String room = entry.getKey();
            String concreteType = entry.getValue();
            // Skip generic/unknown rooms — no schedule to check
            if ("UNKNOWN".equals(concreteType) || "HABITABLE".equals(concreteType)
                    || "EMPTY".equals(concreteType)) continue;

            // Map inferred type to ad_space_type schedule key
            String scheduleKey = switch (concreteType) {
                case "TOILET" -> "BATHROOM"; // toilet rooms use bathroom schedule
                default -> concreteType;
            };

            double[] aabb = spaceResult.roomAabbs().get(room);
            if (aabb == null) continue;
            double roomW = aabb[1] - aabb[0]; // maxX - minX
            double roomD = aabb[3] - aabb[2]; // maxY - minY
            double roomH = aabb[5] - aabb[4]; // maxZ - minZ

            // Read required fixtures from schedule
            try (PreparedStatement ps = erpConn.prepareStatement(
                    "SELECT mep_product_id, placement_rule, host_surface, anchor_end "
                    + "FROM ad_space_type_mep_bom WHERE space_type_id=? ORDER BY mep_product_id")) {
                ps.setString(1, scheduleKey);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String fixtureId = rs.getString(1);
                        String placementRule = rs.getString(2);
                        String hostSurface = rs.getString(3);
                        String anchorEnd = rs.getString(4);

                        // Check if any MEP recipe's convergence covers this fixture type
                        boolean found = false;
                        for (Terminal t : spaceResult.terminals()) {
                            if (t.room != null && t.room.equals(room)) {
                                String canonFix = classifyFixtureName(t.fixtureType.toLowerCase());
                                // Loose match: SINK covers SINK, WC covers TOILET, etc.
                                if (fixtureMatchesSchedule(canonFix, fixtureId)
                                        || fixtureMatchesSchedule(t.fixtureType, fixtureId)) {
                                    found = true;
                                    break;
                                }
                            }
                        }

                        if (found) {
                            satisfied++;
                            BIMLogger.info("MEP-GAP", "room={} type={} fixture={} → SATISFIED",
                                    room, concreteType, fixtureId);
                        } else {
                            gaps++;
                            // Compute target position from placement_rule + room AABB
                            double[] target = computePlacementTarget(aabb, placementRule, hostSurface, erpConn);

                            // White-box: log the metadata source, not just the result
                            BIMLogger.info("MEP-GAP",
                                "room={} type={} fixture={} rule={} host={} anchor={} → GAP "
                                + "target=({:.3f},{:.3f},{:.3f}) source=ad_placement_offset",
                                room, concreteType, fixtureId, placementRule, hostSurface, anchorEnd,
                                target[0], target[1], target[2]);

                            // Rosetta Stone check: if a terminal exists for this fixture,
                            // compare metadata target vs IFC reference position
                            for (Terminal t : spaceResult.terminals()) {
                                if (fixtureMatchesSchedule(t.fixtureType, fixtureId)
                                        && t.room != null && t.room.equals(room)) {
                                    double drift = Math.sqrt(
                                        (target[0]-t.cx)*(target[0]-t.cx)
                                        + (target[1]-t.cy)*(target[1]-t.cy)
                                        + (target[2]-t.cz)*(target[2]-t.cz));
                                    BIMLogger.info("MEP-GAP",
                                        "  ROSETTA CHECK: metadata=({:.3f},{:.3f},{:.3f}) vs IFC=({:.3f},{:.3f},{:.3f}) drift={:.3f}m",
                                        target[0], target[1], target[2], t.cx, t.cy, t.cz, drift);
                                    break;
                                }
                            }

                            // Emit actionable INSERT for the user/script
                            insertScript.append(String.format(
                                "-- %s needs %s at %s (%s surface, anchor=%s)%n",
                                room, fixtureId, placementRule, hostSurface, anchorEnd));
                            insertScript.append(String.format(
                                "-- Target position: (%.3f, %.3f, %.3f) within room AABB%n",
                                target[0], target[1], target[2]));
                            insertScript.append(String.format(
                                "INSERT INTO fixture_target (room, fixture_id, placement_rule, "
                                + "host_surface, anchor_end, target_x, target_y, target_z, building_type) "
                                + "VALUES ('%s', '%s', '%s', '%s', '%s', %.4f, %.4f, %.4f, '%s');%n%n",
                                room, fixtureId, placementRule, hostSurface, anchorEnd,
                                target[0], target[1], target[2], buildingType));
                        }
                    }
                }
            } catch (SQLException e) {
                BIMLogger.warn("MEP-GAP", "{}: schedule lookup failed for {}: {}",
                        buildingType, scheduleKey, e.getMessage());
            }
        }

        System.out.printf("[MEP-GAP] %s: %d satisfied, %d gaps%n", buildingType, satisfied, gaps);
        if (gaps > 0) {
            System.out.printf("[MEP-GAP] %s: gap script:%n%s", buildingType, insertScript);
        }

        return gaps;
    }

    /** Check if a fixture classification matches a schedule entry. */
    private static boolean fixtureMatchesSchedule(String fixture, String scheduleId) {
        if (fixture == null || scheduleId == null) return false;
        String f = fixture.toUpperCase(), s = scheduleId.toUpperCase();
        if (f.equals(s)) return true;
        // WC/LAVATORY → TOILET, SINK → SINK, etc.
        if (("WC".equals(f) || "WATER_CLOSET".equals(f)) && "TOILET".equals(s)) return true;
        if ("LAVATORY".equals(f) && "SINK".equals(s)) return true;
        if ("RECEPTACLE".equals(f) && s.startsWith("OUTLET")) return true;
        if (f.startsWith("ELEC_OUTLET") && s.startsWith("OUTLET")) return true;
        if (f.startsWith("ELEC_SWITCH") && "SWITCH".equals(s)) return true;
        return false;
    }

    /** Cached placement offset rules from ad_placement_offset. Loaded once per run. */
    private static Map<String, double[]> placementOffsetCache = null;

    /** Load ad_placement_offset into cache: rule → [from_edge_x, from_edge_y, z_offset, x_ref, y_ref, z_rule] */
    private static Map<String, double[]> loadPlacementOffsets(Connection erpConn) {
        if (placementOffsetCache != null) return placementOffsetCache;
        placementOffsetCache = new LinkedHashMap<>();
        try (Statement stmt = erpConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                "SELECT placement_rule, from_edge_x, from_edge_y, z_offset, x_ref, y_ref, z_rule "
                + "FROM ad_placement_offset")) {
            while (rs.next()) {
                String xRef = rs.getString(5);
                String yRef = rs.getString(6);
                String zRule = rs.getString(7);
                placementOffsetCache.put(rs.getString(1), new double[]{
                    rs.getDouble(2), rs.getDouble(3), rs.getDouble(4),
                    "MIN".equals(xRef) ? 0 : "MAX".equals(xRef) ? 2 : 1,  // 0=MIN, 1=CENTER, 2=MAX
                    "MIN".equals(yRef) ? 0 : "MAX".equals(yRef) ? 2 : 1,
                    "FLOOR".equals(zRule) ? 0 : "CEILING".equals(zRule) ? 1 : 0.5 // 0=FLOOR, 1=CEILING, 0.5=MID
                });
            }
        } catch (SQLException ignored) {}
        return placementOffsetCache;
    }

    /**
     * Compute placement target position from placement_rule + room AABB.
     * Reads offsets from ad_placement_offset (DV042) — modellers edit per standards.
     * No hardcoded distances. Code is pure metadata reader.
     */
    static double[] computePlacementTarget(double[] aabb, String rule, String surface,
                                            Connection erpConn) {
        double minX = aabb[0], maxX = aabb[1];
        double minY = aabb[2], maxY = aabb[3];
        double minZ = aabb[4], maxZ = aabb[5];
        double cx = (minX + maxX) / 2.0;
        double cy = (minY + maxY) / 2.0;

        Map<String, double[]> offsets = loadPlacementOffsets(erpConn);
        double[] off = offsets.get(rule);
        if (off == null) off = offsets.getOrDefault("AUTO", new double[]{0, 0, 0, 1, 1, 0.5});

        // X position: from edge offset applied to x_ref (MIN/CENTER/MAX)
        double x = switch ((int) off[3]) {
            case 0 -> minX + off[0];    // MIN + offset
            case 2 -> maxX - off[0];    // MAX - offset (symmetric)
            default -> cx;              // CENTER
        };
        // Y position: from edge offset applied to y_ref
        double y = switch ((int) off[4]) {
            case 0 -> minY + off[1];    // MIN + offset
            case 2 -> maxY - off[1];    // MAX - offset
            default -> cy;              // CENTER
        };
        // Z position: z_rule datum + offset
        double z = switch ((int) Math.round(off[5] * 2)) {
            case 0 -> minZ + off[2];    // FLOOR + offset
            case 2 -> maxZ - off[2];    // CEILING - offset
            default -> (minZ + maxZ) / 2.0 + off[2]; // MID + offset
        };

        return new double[]{x, y, z};
    }

    /** Extract discipline code from BOM Value: D_CW_U_RUN_7 → CW, D_SP_L1_RUN_3 → SP. */
    private static String extractDisciplineFromBomValue(String bomValue) {
        // Pattern: PREFIX_DISC_... where DISC is 2-4 chars after first underscore
        String[] parts = bomValue.split("_");
        if (parts.length >= 2) return parts[1]; // D_CW_... → CW
        return "CW"; // fallback
    }

    /** Find nearest room whose capabilities include the required one.
     *  Uses 3m XY padding + full Z range (pipes at ceiling, furniture at floor). */
    private static String findCapableRoom(MepSpaceResult spaceResult,
                                           double px, double py, double pz,
                                           String requiredCap) {
        String best = null;
        double bestDist = Double.MAX_VALUE;
        double PAD_XY = 3.0; // metres — generous for ceiling-level pipes

        for (Map.Entry<String, double[]> e : spaceResult.roomAabbs().entrySet()) {
            String room = e.getKey();
            Set<String> caps = spaceResult.roomCapabilities().getOrDefault(room, Set.of());
            if (!caps.contains(requiredCap)) continue;

            double[] a = e.getValue(); // [minX, maxX, minY, maxY, minZ, maxZ]
            // XY containment with padding; Z ignored (pipes at ceiling, furniture at floor)
            if (px >= a[0] - PAD_XY && px <= a[1] + PAD_XY &&
                py >= a[2] - PAD_XY && py <= a[3] + PAD_XY) {
                double dcx = px - (a[0] + a[1]) / 2.0;
                double dcy = py - (a[2] + a[3]) / 2.0;
                double dist = dcx * dcx + dcy * dcy;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = room;
                }
            }
        }
        return best;
    }

    // ── recipe helpers ──────────────────────────────────────────────

    private static List<MepPosElement> readMepElementsWithPositions(Path refDb) throws SQLException {
        List<MepPosElement> result = new ArrayList<>();
        String url = "jdbc:sqlite:" + refDb;
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            // G3 fix: read discipline column to pass through to discFromClass(3-arg)
            ResultSet rs = stmt.executeQuery("""
                    SELECT m.ifc_class, m.element_type, m.storey, m.discipline,
                           r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
                    FROM elements_meta m
                    JOIN elements_rtree r ON m.id = r.id
                    WHERE m.ifc_class IN (
                        'IfcPipeSegment','IfcPipeFitting',
                        'IfcDuctSegment','IfcDuctFitting',
                        'IfcFireSuppressionTerminal','IfcAirTerminal',
                        'IfcFlowTerminal','IfcFlowController','IfcLightFixture',
                        'IfcFlowSegment','IfcFlowFitting')
                    """);
            while (rs.next()) {
                double minX = rs.getDouble(5), maxX = rs.getDouble(6);
                double minY = rs.getDouble(7), maxY = rs.getDouble(8);
                double minZ = rs.getDouble(9), maxZ = rs.getDouble(10);
                double cx = (minX + maxX) / 2.0;
                double cy = (minY + maxY) / 2.0;
                double cz = (minZ + maxZ) / 2.0;
                result.add(new MepPosElement(
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        cx, cy, cz, minX, maxX, minY, maxY, minZ, maxZ,
                        rs.getString(4)));
            }
        }
        return result;
    }

    /**
     * Classify IFC class → discipline, preferring extracted discipline from elements_meta.
     * Implementing DISC_VALIDATION_DB_SRS.md §6.12.3 §5 — G3 fix — Witness: W-G3-FIX
     */
    static String discFromClass(String ifcClass, String elementType, String extractedDiscipline) {
        // If extraction DB already classified it, trust it (G3 fix)
        if (extractedDiscipline != null && !extractedDiscipline.isBlank()
                && !extractedDiscipline.equalsIgnoreCase("MEP")) {
            return extractedDiscipline; // CW, SP, FP, ACMV, ELEC, LPG from elements_meta
        }
        // Fallback: keyword heuristic
        return discFromClass(ifcClass, elementType);
    }

    /** Classify IFC class → discipline code (FP/ACMV/CW/SP/ELEC). */
    private static String discFromClass(String ifcClass, String elementType) {
        return switch (ifcClass) {
            case "IfcFireSuppressionTerminal" -> "FP";
            case "IfcDuctSegment", "IfcDuctFitting", "IfcAirTerminal" -> "ACMV";
            case "IfcLightFixture" -> "ELEC";
            case "IfcFlowTerminal" -> {
                // Implementing DISC_VALIDATION_DB_SRS.md §11.4 G3 — Witness: W-TE-DISC
                String et = elementType != null ? elementType.toLowerCase() : "";
                if (et.contains("light") || et.contains("lamp") || et.contains("fixture")
                        || et.contains("luminaire") || et.contains("pendant")) yield "ELEC";
                yield (et.contains("drain") || et.contains("waste") || et.contains("soil")
                       || et.contains("inspection") || et.contains("interceptor")) ? "SP" : "CW";
            }
            case "IfcPipeSegment", "IfcPipeFitting", "IfcFlowController" -> {
                String et = elementType != null ? elementType.toLowerCase() : "";
                yield (et.contains("soil") || et.contains("drain") || et.contains("sp_"))
                        ? "SP" : "CW";
            }
            default -> "CW"; // IfcFlowSegment, IfcFlowFitting → CW as default
        };
    }

    /** Derive short BOM prefix from buildingType: "HospitalAuckland" → "RM". */
    private static String buildBomPrefix(String buildingType) {
        StringBuilder sb = new StringBuilder();
        for (String part : buildingType.split("_")) {
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0)));
        }
        return sb.toString();
    }

    /** Convert storey name to short code: "Level 1" → "L1", "Roof Level" → "RL". */
    private static String toStoreyCode(String storey) {
        if (storey == null || storey.isBlank()) return "L0";
        String s = storey.trim();
        // "Level N" → "LN"
        if (s.matches("(?i)level\\s+\\d+")) {
            return "L" + s.replaceAll("(?i)level\\s+", "");
        }
        // Collect uppercase letters + digits
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isUpperCase(c) || Character.isDigit(c)) sb.append(c);
        }
        String code = sb.toString();
        return code.isEmpty() ? s.replaceAll("[^A-Za-z0-9]", "").toUpperCase() : code;
    }

    /** Determine dominant axis (max centroid variance) for a group. */
    private static char dominantAxisOf(List<MepPosElement> group) {
        if (group.isEmpty()) return 'X';
        double sumX = 0, sumY = 0, sumZ = 0;
        for (MepPosElement e : group) { sumX += e.cx; sumY += e.cy; sumZ += e.cz; }
        double n = group.size();
        double meanX = sumX / n, meanY = sumY / n, meanZ = sumZ / n;
        double varX = 0, varY = 0, varZ = 0;
        for (MepPosElement e : group) {
            varX += (e.cx - meanX) * (e.cx - meanX);
            varY += (e.cy - meanY) * (e.cy - meanY);
            varZ += (e.cz - meanZ) * (e.cz - meanZ);
        }
        if (varX >= varY && varX >= varZ) return 'X';
        if (varY >= varX && varY >= varZ) return 'Y';
        return 'Z';
    }

    /**
     * Detect connected chains: AABB face gap < 50mm = touching; up to 600mm = penetration gap
     * (pipe through wall/slab — verified against structural AABB). W-J1-GEO §7
     */
    private static List<List<MepPosElement>> detectChains(
            List<MepPosElement> sorted, char axis, List<double[]> structuralAabbs) {
        List<List<MepPosElement>> chains = new ArrayList<>();
        if (sorted.isEmpty()) return chains;

        List<MepPosElement> current = new ArrayList<>();
        current.add(sorted.get(0));

        for (int i = 1; i < sorted.size(); i++) {
            MepPosElement prev = sorted.get(i - 1);
            MepPosElement next = sorted.get(i);
            double gap = switch (axis) {
                case 'X' -> Math.max(0.0, next.minX - prev.maxX);
                case 'Y' -> Math.max(0.0, next.minY - prev.maxY);
                default  -> Math.max(0.0, next.minZ - prev.maxZ);
            };
            boolean connected = gap <= 0.05
                    || (gap <= 0.60 && isPenetrationGap(prev, next, axis, structuralAabbs));
            if (connected) {
                current.add(next);
            } else {
                chains.add(current);
                current = new ArrayList<>();
                current.add(next);
            }
        }
        chains.add(current);
        return chains;
    }

    /**
     * Returns true if the gap between prev and next on the dominant axis is covered by a
     * structural AABB (wall, slab, column) — meaning this is a building penetration, not a break.
     * W-J1-GEO: gap up to 600mm tolerated when structure accounts for it.
     */
    private static boolean isPenetrationGap(
            MepPosElement prev, MepPosElement next, char axis, List<double[]> structuralAabbs) {
        double gapStart = switch (axis) {
            case 'X' -> prev.maxX; case 'Y' -> prev.maxY; default -> prev.maxZ; };
        double gapEnd = switch (axis) {
            case 'X' -> next.minX; case 'Y' -> next.minY; default -> next.minZ; };
        if (gapEnd <= gapStart) return false; // overlapping, not a gap
        int lo = axis == 'X' ? 0 : axis == 'Y' ? 2 : 4; // AABB index: [minX,maxX,minY,maxY,minZ,maxZ]
        for (double[] aabb : structuralAabbs) {
            if (aabb[lo] <= gapEnd && aabb[lo + 1] >= gapStart) return true;
        }
        return false;
    }

    /**
     * Collinearity score: fraction of total centroid variance explained by the dominant axis.
     * 1.0 = perfect line, 0.33 = scattered equally in 3D. Threshold: 0.5 = plausibly linear.
     * W-J1-GEO: discard chains with R² < 0.5 (scattered, not a routing run).
     */
    private static double collinearityR2(List<MepPosElement> chain) {
        if (chain.size() < 2) return 1.0;
        double sumX = 0, sumY = 0, sumZ = 0;
        for (MepPosElement e : chain) { sumX += e.cx; sumY += e.cy; sumZ += e.cz; }
        double n = chain.size(), mX = sumX / n, mY = sumY / n, mZ = sumZ / n;
        double vX = 0, vY = 0, vZ = 0;
        for (MepPosElement e : chain) {
            vX += (e.cx - mX) * (e.cx - mX);
            vY += (e.cy - mY) * (e.cy - mY);
            vZ += (e.cz - mZ) * (e.cz - mZ);
        }
        double total = vX + vY + vZ;
        return total < 1e-9 ? 1.0 : Math.max(vX, Math.max(vY, vZ)) / total;
    }

    /** Load structural element AABBs from extracted DB for penetration gap detection. W-J1-GEO */
    private static List<double[]> loadStructuralAabbs(Path refDb) {
        List<double[]> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + refDb);
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("""
                    SELECT r.minX,r.maxX,r.minY,r.maxY,r.minZ,r.maxZ
                    FROM elements_meta m JOIN elements_rtree r ON m.id=r.id
                    WHERE m.ifc_class IN
                    ('IfcWall','IfcWallStandardCase','IfcSlab','IfcColumn','IfcBeam','IfcMember')
                    """);
            while (rs.next())
                result.add(new double[]{rs.getDouble(1),rs.getDouble(2),rs.getDouble(3),
                        rs.getDouble(4),rs.getDouble(5),rs.getDouble(6)});
        } catch (SQLException e) {
            BIMLogger.warn(TAG, "Could not load structural AABBs from {}: {}", refDb.getFileName(), e.getMessage());
        }
        return result;
    }

    /**
     * Split a chain at direction changes: when perpendicular displacement > ratio × along displacement.
     * Implements §6.12.2 §7 direction-change sub-chain detection — Witness: W-J1-CHAIN-FIX
     * GEO forensic logging: each split emits [GEO] line for spatial audit.
     */
    private static List<List<MepPosElement>> splitByDirectionChange(
            List<MepPosElement> chain, char axis, double ratio, String logLabel) {
        List<List<MepPosElement>> result = new ArrayList<>();
        if (chain.isEmpty()) return result;

        List<MepPosElement> current = new ArrayList<>();
        current.add(chain.get(0));

        for (int i = 1; i < chain.size(); i++) {
            MepPosElement prev = chain.get(i - 1);
            MepPosElement curr = chain.get(i);

            double along = switch (axis) {
                case 'X' -> Math.abs(curr.cx - prev.cx);
                case 'Y' -> Math.abs(curr.cy - prev.cy);
                default  -> Math.abs(curr.cz - prev.cz);
            };
            double perpB = switch (axis) {
                case 'X' -> curr.cy - prev.cy;
                case 'Y' -> curr.cx - prev.cx;
                default  -> curr.cx - prev.cx;
            };
            double perpC = switch (axis) {
                case 'X' -> curr.cz - prev.cz;
                case 'Y' -> curr.cz - prev.cz;
                default  -> curr.cy - prev.cy;
            };
            double perpendicular = Math.sqrt(perpB * perpB + perpC * perpC);

            if (along > 0.0 && perpendicular > ratio * along) {
                // Direction change — close current sub-chain, start new one
                result.add(current);
                BIMLogger.fine(TAG, "[GEO] {}: direction-change split idx={} along={:.3f}m perp={:.3f}m",
                        logLabel, i, along, perpendicular);
                System.out.printf("[IFCtoERP][GEO] %s: split@idx=%d along=%.4fm perp=%.4fm ratio=%.2f%n",
                        logLabel, i, along, perpendicular, perpendicular / along);
                current = new ArrayList<>();
            }
            current.add(curr);
        }
        result.add(current);
        return result;
    }

    /** Map discipline code → canonical shim product value. */
    private static String discToShim(String disc) {
        return switch (disc) {
            case "FP"   -> "FP_CEILING_SHIM";
            case "ACMV" -> "ACMV_CEILING_SHIM";
            case "ELEC" -> "ELEC_CEILING_SHIM";
            case "SP"   -> "SP_FLOOR_SHIM";
            default     -> "CW_CEILING_SHIM"; // CW and others
        };
    }

    /** Resolve product_id from ERP.db for a given MEP element. Returns null if not found. */
    private static String resolveProductIdFor(Connection erpConn, MepPosElement e)
            throws SQLException {
        String pieceType = classifyPieceType(e.ifcClass, e.elementType);
        // Reconstruct the key used in classifyElements/promoteToProduct
        double dx = (e.maxX - e.minX) * 1000.0; // metres → mm
        double dy = (e.maxY - e.minY) * 1000.0;
        double dz = (e.maxZ - e.minZ) * 1000.0;
        double[] dims = {dx, dy, dz};
        Arrays.sort(dims);
        double diameter = (dims[0] + dims[1]) / 2.0;
        double roundedDia = Math.round(diameter / 5.0) * 5.0;
        String material = extractMaterial(e.elementType);
        String productId = pieceType + "_" + (int) roundedDia + "MM"
                + (material != null ? "_" + material : "");

        // Verify it exists in M_Product
        try (PreparedStatement ps = erpConn.prepareStatement(
                "SELECT 1 FROM M_Product WHERE product_id = ?")) {
            ps.setString(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? productId : null;
            }
        }
    }

    private static void insertMBomLine(Connection erpConn, String bomId,
            String childProductId, int seq, double dx, double dy, double dz,
            double rotationDeg, String uomId, String qtyType, int qty)
            throws SQLException {
        int intBomId = -1;
        try (PreparedStatement lookup = erpConn.prepareStatement(
                "SELECT M_BOM_ID FROM M_BOM WHERE bom_id = ?")) {
            lookup.setString(1, bomId);
            try (ResultSet rs = lookup.executeQuery()) {
                if (rs.next()) intBomId = rs.getInt(1);
            }
        }
        if (intBomId < 0) return;

        try (PreparedStatement ps = erpConn.prepareStatement("""
                INSERT INTO M_BOM_Line
                (M_BOM_ID, bom_id, child_product_id, sequence, qty, dx, dy, dz,
                 c_uom_id, qty_type, rotation_rule, IsActive)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Y')
                """)) {
            ps.setInt(1, intBomId);
            ps.setString(2, bomId);
            ps.setString(3, childProductId);
            ps.setInt(4, seq);
            ps.setInt(5, qty);
            ps.setDouble(6, dx);
            ps.setDouble(7, dy);
            ps.setDouble(8, dz);
            ps.setString(9, uomId);
            ps.setString(10, qtyType);
            ps.setDouble(11, rotationDeg);
            ps.executeUpdate();
        }
    }

    private static int writeStagingRows(Connection erpConn,
                                        Map<String, JointPieceType> types) throws SQLException {
        // A1: include discipline column (added via G1 ALTER TABLE migration)
        String sql = """
                INSERT OR REPLACE INTO _import_joint_piece_types
                (ifc_class, element_type, piece_type, diameter_mm, length_mm,
                 angle_deg, material, source_building, count, discipline)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        int count = 0;
        try (PreparedStatement stmt = erpConn.prepareStatement(sql)) {
            for (var entry : types.entrySet()) {
                JointPieceType t = entry.getValue();
                stmt.setString(1, t.ifcClass);
                stmt.setString(2, t.elementType);
                stmt.setString(3, t.pieceType);
                stmt.setDouble(4, t.diameterMm);
                stmt.setDouble(5, t.lengthMm);
                stmt.setDouble(6, t.angleDeg);
                stmt.setString(7, t.material);
                stmt.setString(8, t.sourceBuilding);
                stmt.setInt(9, t.count);
                stmt.setString(10, t.discipline);
                stmt.executeUpdate();
                count++;
            }
        }
        return count;
    }

    /**
     * Promote staged joint piece types to M_Product.
     * INSERT OR IGNORE — existing products untouched, toolbox grows.
     */
    private static int promoteToProduct(Connection erpConn) throws SQLException {
        // Build product_id from piece_type + diameter + material
        String sql = """
                INSERT OR IGNORE INTO M_Product
                (product_id, Value, Name, product_type, width, depth, height,
                 ifc_class, extracted_from, is_active)
                SELECT
                    piece_type || '_' || CAST(CAST(diameter_mm AS INTEGER) AS TEXT) || 'MM'
                        || CASE WHEN material IS NOT NULL THEN '_' || material ELSE '' END,
                    piece_type || '_' || CAST(CAST(diameter_mm AS INTEGER) AS TEXT) || 'MM'
                        || CASE WHEN material IS NOT NULL THEN '_' || material ELSE '' END,
                    piece_type || ' ' || CAST(CAST(diameter_mm AS INTEGER) AS TEXT) || 'mm'
                        || CASE WHEN material IS NOT NULL THEN ' ' || material ELSE '' END,
                    piece_type,
                    diameter_mm / 1000.0,
                    diameter_mm / 1000.0,
                    CASE WHEN length_mm > 0 THEN length_mm / 1000.0 ELSE diameter_mm / 1000.0 END,
                    ifc_class,
                    'JOINT_PIECE_EXTRACTION',
                    1
                FROM _import_joint_piece_types
                GROUP BY piece_type, CAST(diameter_mm AS INTEGER), material
                """;
        try (Statement stmt = erpConn.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }

    // ── S148: MEP-SPACE diagnostic logging ────────────────────────────

    /** Result of MEP-SPACE inference — room→spaceType + room→AABB for downstream linkage. */
    /** Plumbing/electrical terminal with position — extracted from IFC for convergence proof. */
    record Terminal(String fixtureType, String capability, double cx, double cy, double cz, String room) {}

    /** Room capabilities inferred from fixture presence — abstract, not room-name-dependent.
     *  The crawler uses capabilities (PLUMBABLE, ELECTRIFIED, etc.) not concrete names (KITCHEN). */
    record MepSpaceResult(
            Map<String, String> roomSpaceTypes,           // room → concrete type (KITCHEN, BATHROOM) — for compliance logging only
            Map<String, Set<String>> roomCapabilities,    // room → {PLUMBABLE, ELECTRIFIED, ...} — for crawler linkage
            Map<String, double[]> roomAabbs,              // room → [minX,maxX,minY,maxY,minZ,maxZ]
            List<Terminal> terminals                       // all extracted terminals with positions
    ) {
        static MepSpaceResult EMPTY = new MepSpaceResult(Map.of(), Map.of(), Map.of(), List.of());
    }

    /**
     * Infer room function from furniture containment, map MEP terminals to spaces.
     * Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 — Witness: W-SPACE-LINK
     *
     * <p>Read-only against extracted DB. Logs + returns room→spaceType map.
     * Room AABB computed from furniture elements in each IfcSpace.
     * MEP terminals mapped by centroid-in-AABB test against room envelopes.
     */
    private static MepSpaceResult emitMepSpaceLog(Path refDb, String buildingType) {
        String url = "jdbc:sqlite:" + refDb;
        try (Connection conn = DriverManager.getConnection(url)) {
            // 1. Build room AABBs from furniture containment
            Map<String, double[]> roomAabb = new LinkedHashMap<>(); // room → [minX,maxX,minY,maxY,minZ,maxZ]
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                    SELECT ss.name,
                           MIN(r.minX), MAX(r.maxX), MIN(r.minY), MAX(r.maxY),
                           MIN(r.minZ), MAX(r.maxZ), COUNT(*)
                    FROM rel_contained_in_space rcs
                    JOIN spatial_structure ss ON rcs.space_guid = ss.guid
                    JOIN elements_meta m ON m.guid = rcs.element_guid
                    JOIN elements_rtree r ON r.id = m.id
                    WHERE ss.type = 'IfcSpace'
                    GROUP BY ss.name
                    """)) {
                while (rs.next()) {
                    String room = rs.getString(1);
                    roomAabb.put(room, new double[]{
                        rs.getDouble(2), rs.getDouble(3),
                        rs.getDouble(4), rs.getDouble(5),
                        rs.getDouble(6), rs.getDouble(7)
                    });
                }
            }
            if (roomAabb.isEmpty()) {
                BIMLogger.info("MEP-SPACE", "{}: no room AABBs from furniture containment", buildingType);
                return MepSpaceResult.EMPTY;
            }

            // 2. Read MEP terminals + controllers with positions and element_name
            record MepCandidate(String ifcClass, String elementName, String fixtureType,
                                double cx, double cy, double cz) {}
            List<MepCandidate> candidates = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                    SELECT m.ifc_class, m.element_name,
                           (r.minX + r.maxX) / 2.0, (r.minY + r.maxY) / 2.0, (r.minZ + r.maxZ) / 2.0
                    FROM elements_meta m
                    JOIN elements_rtree r ON m.id = r.id
                    WHERE m.ifc_class IN ('IfcFlowTerminal', 'IfcFlowController')
                    """)) {
                while (rs.next()) {
                    String name = rs.getString(2) != null ? rs.getString(2) : "";
                    String fixture = classifyFixtureName(name.toLowerCase());
                    candidates.add(new MepCandidate(
                        rs.getString(1), name, fixture,
                        rs.getDouble(3), rs.getDouble(4), rs.getDouble(5)));
                }
            }

            // 3. Read pipe segments with positions and system type
            record PipeCandidate(String pipeSystem, double cx, double cy, double cz) {}
            List<PipeCandidate> pipes = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                    SELECT m.element_name,
                           (r.minX + r.maxX) / 2.0, (r.minY + r.maxY) / 2.0, (r.minZ + r.maxZ) / 2.0
                    FROM elements_meta m
                    JOIN elements_rtree r ON m.id = r.id
                    WHERE m.ifc_class IN ('IfcFlowSegment', 'IfcFlowFitting')
                    """)) {
                while (rs.next()) {
                    String name = rs.getString(1) != null ? rs.getString(1) : "";
                    String system = classifyPipeSystem(name.toLowerCase());
                    pipes.add(new PipeCandidate(system, rs.getDouble(2), rs.getDouble(3), rs.getDouble(4)));
                }
            }

            // 4. Map candidates to rooms by centroid-in-AABB (with 0.5m padding for near-wall fixtures)
            double PAD = 0.5; // metres — fixtures may be slightly outside furniture envelope
            Map<String, List<String>> roomFixtures = new LinkedHashMap<>();
            Map<String, Map<String, Integer>> roomPipes = new LinkedHashMap<>();
            int unmappedFixtures = 0, unmappedPipes = 0;

            for (MepCandidate c : candidates) {
                String room = findContainingRoom(roomAabb, c.cx, c.cy, c.cz, PAD);
                if (room != null) {
                    roomFixtures.computeIfAbsent(room, k -> new ArrayList<>()).add(c.fixtureType);
                } else {
                    unmappedFixtures++;
                }
            }

            for (PipeCandidate p : pipes) {
                String room = findContainingRoom(roomAabb, p.cx, p.cy, p.cz, PAD);
                if (room != null) {
                    roomPipes.computeIfAbsent(room, k -> new LinkedHashMap<>())
                             .merge(p.pipeSystem, 1, Integer::sum);
                } else {
                    unmappedPipes++;
                }
            }

            // 5. Infer space type + capabilities from fixture set, emit log, collect mapping
            Map<String, String> roomSpaceTypes = new LinkedHashMap<>();
            Map<String, Set<String>> roomCapabilities = new LinkedHashMap<>();
            for (Map.Entry<String, double[]> entry : roomAabb.entrySet()) {
                String room = entry.getKey();
                List<String> fixtures = roomFixtures.getOrDefault(room, List.of());
                Map<String, Integer> pipeCounts = roomPipes.getOrDefault(room, Map.of());

                // Count fixture types
                Map<String, Integer> fixtureCounts = new LinkedHashMap<>();
                for (String f : fixtures) fixtureCounts.merge(f, 1, Integer::sum);

                String spaceType = inferSpaceType(fixtureCounts);
                Set<String> caps = inferCapabilities(fixtureCounts, pipeCounts);
                roomSpaceTypes.put(room, spaceType);
                roomCapabilities.put(room, caps);

                BIMLogger.info("MEP-SPACE", "room={} space_type={} capabilities={} fixtures={} pipes={}",
                    room, spaceType, caps, fixtureCounts, pipeCounts);
            }

            // Also log spaces with NO room AABB (empty rooms, hallways)
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                    SELECT ss.name FROM spatial_structure ss
                    WHERE ss.type = 'IfcSpace'
                    ORDER BY ss.name
                    """)) {
                while (rs.next()) {
                    String room = rs.getString(1);
                    if (!roomAabb.containsKey(room)) {
                        BIMLogger.info("MEP-SPACE", "room={} space_type=EMPTY fixtures=(none) pipes=(none)", room);
                    }
                }
            }

            // 6. Build terminal list with positions, capabilities, and room assignment
            // These are the physical endpoints that pipe recipes must converge on.
            List<Terminal> terminals = new ArrayList<>();
            for (MepCandidate c : candidates) {
                String room = findContainingRoom(roomAabb, c.cx, c.cy, c.cz, 3.0); // generous for unmapped
                String cap = fixtureToCapability(c.fixtureType);
                terminals.add(new Terminal(c.fixtureType, cap, c.cx, c.cy, c.cz, room));
            }

            // GEO forensic: log every terminal with position + room + capability
            for (Terminal t : terminals) {
                BIMLogger.info("MEP-TERMINAL", "fixture={} cap={} room={} pos=({:.3f},{:.3f},{:.3f})",
                    t.fixtureType, t.capability, t.room != null ? t.room : "UNMAPPED",
                    t.cx, t.cy, t.cz);
            }

            BIMLogger.info("MEP-SPACE", "{}: rooms={} terminals={} fixtures_mapped={} unmapped={} pipes_mapped={} unmapped={}",
                buildingType, roomAabb.size(), terminals.size(),
                candidates.size() - unmappedFixtures, unmappedFixtures,
                pipes.size() - unmappedPipes, unmappedPipes);

            return new MepSpaceResult(roomSpaceTypes, roomCapabilities, roomAabb, terminals);

        } catch (SQLException e) {
            BIMLogger.warn("MEP-SPACE", "{}: diagnostic query failed: {}", buildingType, e.getMessage());
            return MepSpaceResult.EMPTY;
        }
    }

    /** Classify fixture from element_name for MEP-SPACE logging. */
    private static String classifyFixtureName(String nameLower) {
        if (nameLower.contains("water closet"))     return "WC";
        if (nameLower.contains("lavatory"))          return "LAVATORY";
        if (nameLower.contains("sink"))              return "SINK";
        if (nameLower.contains("shower"))            return "SHOWER";
        if (nameLower.contains("bath tub") || nameLower.contains("bathtub")) return "BATHTUB";
        if (nameLower.contains("refrigerator"))      return "FRIDGE";
        if (nameLower.contains("range"))             return "RANGE";
        if (nameLower.contains("microwave"))         return "MICROWAVE";
        if (nameLower.contains("receptacle"))        return "ELEC_OUTLET";
        if (nameLower.contains("light switch") || nameLower.contains("lighting switch")) return "ELEC_SWITCH";
        if (nameLower.contains("pendant") || nameLower.contains("sconce")) return "LIGHT";
        if (nameLower.contains("panelboard"))        return "ELEC_PANEL";
        if (nameLower.contains("telephone"))         return "TELECOM";
        if (nameLower.contains("fire alarm"))        return "FIRE_ALARM";
        if (nameLower.contains("smoke detector"))    return "SMOKE_DET";
        if (nameLower.contains("valve"))             return "VALVE";
        if (nameLower.contains("backflow"))          return "BACKFLOW";
        if (nameLower.contains("roof drain"))        return "ROOF_DRAIN";
        return "OTHER";
    }

    /** Classify pipe system from element_name for MEP-SPACE logging. */
    private static String classifyPipeSystem(String nameLower) {
        if (nameLower.contains("cold water"))        return "CW";
        if (nameLower.contains("hot water"))         return "HW";
        if (nameLower.contains("waste"))             return "WASTE";
        if (nameLower.contains("pvc"))               return "PVC";
        if (nameLower.contains("conduit"))           return "CONDUIT";
        if (nameLower.contains("duct"))              return "DUCT";
        if (nameLower.contains("mechanical pipe"))   return "MECH";
        return "OTHER";
    }

    /** Map fixture type to abstract capability — used by terminal extraction. */
    private static String fixtureToCapability(String fixtureType) {
        return switch (fixtureType) {
            case "SINK", "WC", "LAVATORY", "SHOWER", "BATHTUB", "FRIDGE",
                 "FLOOR_TRAP", "ROOF_DRAIN" -> "PLUMBABLE";
            case "ELEC_OUTLET", "ELEC_SWITCH", "LIGHT", "ELEC_PANEL",
                 "TELECOM", "FIRE_ALARM", "SMOKE_DET" -> "ELECTRIFIED";
            case "SPRINKLER" -> "FIRE_PROTECTED";
            case "EXHAUST", "SUPPLY_DIFFUSER" -> "VENTILATED";
            case "RANGE", "GAS_HEATER" -> "GAS_SERVED";
            default -> "ELECTRIFIED"; // conservative default
        };
    }

    /** Find which room AABB contains a point (with padding). Returns null if no match. */
    private static String findContainingRoom(Map<String, double[]> roomAabb,
                                              double cx, double cy, double cz, double pad) {
        String best = null;
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<String, double[]> e : roomAabb.entrySet()) {
            double[] a = e.getValue();
            // Padded AABB containment test
            if (cx >= a[0] - pad && cx <= a[1] + pad &&
                cy >= a[2] - pad && cy <= a[3] + pad &&
                cz >= a[4] - pad && cz <= a[5] + pad) {
                // If multiple rooms match (overlapping with padding), pick closest to center
                double dcx = cx - (a[0] + a[1]) / 2.0;
                double dcy = cy - (a[2] + a[3]) / 2.0;
                double dist = dcx * dcx + dcy * dcy; // XY distance only — same storey
                if (dist < bestDist) {
                    bestDist = dist;
                    best = e.getKey();
                }
            }
        }
        return best;
    }

    /** Infer space type from fixture set — abstract, building-agnostic. */
    private static String inferSpaceType(Map<String, Integer> fixtureCounts) {
        boolean hasSink = fixtureCounts.containsKey("SINK");
        boolean hasWC = fixtureCounts.containsKey("WC");
        boolean hasLavatory = fixtureCounts.containsKey("LAVATORY");
        boolean hasShower = fixtureCounts.containsKey("SHOWER");
        boolean hasBathtub = fixtureCounts.containsKey("BATHTUB");
        boolean hasFridge = fixtureCounts.containsKey("FRIDGE");
        boolean hasRange = fixtureCounts.containsKey("RANGE");

        // Kitchen: has sink + cooking appliance (range/microwave/fridge)
        if (hasSink && (hasFridge || hasRange)) return "KITCHEN";

        // Bathroom: has WC or shower/bathtub, or multiple lavatories (L2 bathrooms)
        if (hasWC && (hasLavatory || hasShower || hasBathtub)) return "BATHROOM";
        if (hasShower || hasBathtub) return "BATHROOM";
        if (hasLavatory && fixtureCounts.getOrDefault("LAVATORY", 0) >= 2) return "BATHROOM";

        // Toilet/WC room: WC only, no bathing
        if (hasWC) return "TOILET";

        // Single lavatory room (powder room)
        if (hasLavatory) return "TOILET";

        // Laundry: has sink but no cooking (different from kitchen)
        if (hasSink) return "UTILITY";

        // Has only electrical + pass-through infrastructure (no actual plumbing fixtures)
        if (!fixtureCounts.isEmpty()) {
            boolean onlyElecOrInfra = fixtureCounts.keySet().stream()
                .allMatch(k -> k.startsWith("ELEC_") || k.equals("LIGHT") ||
                          k.equals("SMOKE_DET") || k.equals("TELECOM") ||
                          k.equals("FIRE_ALARM") ||
                          k.equals("VALVE") || k.equals("BACKFLOW"));  // pipe infrastructure, not room fixtures
            if (onlyElecOrInfra) return "HABITABLE";  // bedroom/living — has power but no plumbing
        }

        return "UNKNOWN";
    }

    /**
     * Infer abstract capabilities from fixture and pipe presence.
     * The crawler uses these — never concrete room names.
     * Capabilities mirror discipline requirements: a CW pipe needs a PLUMBABLE room.
     */
    private static Set<String> inferCapabilities(Map<String, Integer> fixtureCounts,
                                                  Map<String, Integer> pipeCounts) {
        Set<String> caps = new LinkedHashSet<>();

        // PLUMBABLE: room has plumbing fixtures (sink, WC, shower, floor trap)
        boolean hasPlumbing = fixtureCounts.keySet().stream()
                .anyMatch(k -> k.equals("SINK") || k.equals("WC") || k.equals("LAVATORY")
                        || k.equals("SHOWER") || k.equals("BATHTUB") || k.equals("FRIDGE"));
        boolean hasPlumbingPipes = pipeCounts.containsKey("CW") || pipeCounts.containsKey("HW")
                || pipeCounts.containsKey("WASTE");
        if (hasPlumbing || hasPlumbingPipes) caps.add("PLUMBABLE");

        // ELECTRIFIED: room has any electrical fixture
        boolean hasElec = fixtureCounts.keySet().stream()
                .anyMatch(k -> k.startsWith("ELEC_") || k.equals("LIGHT")
                        || k.equals("TELECOM") || k.equals("FIRE_ALARM"));
        boolean hasElecPipes = pipeCounts.containsKey("CONDUIT");
        if (hasElec || hasElecPipes) caps.add("ELECTRIFIED");

        // FIRE_PROTECTED: room has sprinklers or smoke detectors
        if (fixtureCounts.containsKey("SPRINKLER") || fixtureCounts.containsKey("SMOKE_DET")
                || fixtureCounts.containsKey("FIRE_ALARM")) {
            caps.add("FIRE_PROTECTED");
        }

        // VENTILATED: room has mechanical ventilation
        if (fixtureCounts.containsKey("EXHAUST") || fixtureCounts.containsKey("SUPPLY_DIFFUSER")
                || pipeCounts.containsKey("DUCT") || pipeCounts.containsKey("MECH")) {
            caps.add("VENTILATED");
        }

        // GAS_SERVED: room has gas appliance
        if (fixtureCounts.containsKey("RANGE") || fixtureCounts.containsKey("GAS_HEATER")) {
            caps.add("GAS_SERVED");
        }

        return caps;
    }

    // ── records ─────────────────────────────────────────────────────

    record MepElement(String ifcClass, String elementType, String discipline,
                      double dx, double dy, double dz) {}

    record JointPieceType(String ifcClass, String elementType, String pieceType,
                          double diameterMm, double lengthMm, double angleDeg,
                          String material, String sourceBuilding, int count, String discipline) {}

    /** MEP element with world centroid and AABB bounds (metres), for recipe building. */
    record MepPosElement(String ifcClass, String elementType, String storey,
                         double cx, double cy, double cz,
                         double minX, double maxX, double minY, double maxY,
                         double minZ, double maxZ, String discipline) {}

    public record Result(int mepElements, int stagedTypes, int newProducts) {}

    public record RecipeResult(int runsBuilt, int linesWritten, int shimAnchors) {}
}
