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
     * @param buildingType  e.g. "SJTII_Terminal"
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
        BIMLogger.info(TAG, "{}: {} MEP elements → {} joint types, {} new M_Products",
                buildingType, mepElements.size(), staged, promoted);

        return new Result(mepElements.size(), staged, promoted);
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
                        UNIQUE(piece_type, diameter_mm, material)
                    )""");
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
            double diameter = estimateDiameter(e);
            double length = estimateLength(e);
            String material = extractMaterial(e.elementType);

            // Round diameter to nearest 5mm for grouping
            double roundedDia = Math.round(diameter / 5.0) * 5.0;
            String key = pieceType + "_" + (int) roundedDia + "MM"
                    + (material != null ? "_" + material : "");

            types.merge(key, new JointPieceType(
                    e.ifcClass, e.elementType, pieceType,
                    roundedDia, length, 0, material, buildingType, 1
            ), (a, b) -> new JointPieceType(
                    a.ifcClass, a.elementType, a.pieceType,
                    a.diameterMm, Math.max(a.lengthMm, b.lengthMm),
                    a.angleDeg, a.material, a.sourceBuilding,
                    a.count + b.count));

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
     * @param buildingType e.g. "Revit_MEP"
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
                String disc = discFromClass(e.ifcClass, e.elementType);
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
                        INSERT INTO M_BOM (bom_id, Value, Name, bom_type, source_building, BOMType, IsActive)
                        VALUES (?, ?, ?, 'MEP_RECIPE', ?, 'MEP', 'Y')""")) {
                    ps.setString(1, bomId); ps.setString(2, bomId);
                    ps.setString(3, disc + " archetype dz=" + String.format("%.3f", dzBand));
                    ps.setString(4, buildingType);
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
        Map<String, List<MepPosElement>> groups = new LinkedHashMap<>();
        for (MepPosElement e : elements) {
            String disc = discFromClass(e.ifcClass, e.elementType);
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
                        INSERT INTO M_BOM (bom_id, Value, Name, bom_type, source_building, BOMType, IsActive)
                        VALUES (?, ?, ?, 'MEP_RECIPE', ?, 'MEP', 'Y')""")) {
                    ps.setString(1, bomId); ps.setString(2, bomId);
                    ps.setString(3, disc + " run " + storeyCode + " #" + runNum);
                    ps.setString(4, buildingType);
                    ps.executeUpdate();
                } catch (SQLException ignored) {}
                runsBuilt++;

                // Shim at seq=10 (anchor)
                insertMBomLine(erpConn, bomId, discToShim(disc), 10, 0.0, 0.0, 0.0, 0.0, "EA", "FIXED", 1);
                shimAnchors++; linesWritten++;

                // First piece at seq=20 (at shim position, zero offset)
                MepPosElement first = chain.get(0);
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

                // Remaining pieces: centroid deltas + rotation hint at direction joints
                StringBuilder dxLog = new StringBuilder("→0");
                double cum = 0.0;
                for (int i = 1; i < chain.size(); i++) {
                    MepPosElement prev = chain.get(i - 1);
                    MepPosElement curr = chain.get(i);
                    double dx = curr.cx - prev.cx;
                    double dy = curr.cy - prev.cy;
                    double dz = curr.cz - prev.cz;

                    // Rotation at joints: atan2(perpendicular, along) in degrees
                    double along = switch (axis) {
                        case 'X' -> Math.abs(dx); case 'Y' -> Math.abs(dy); default -> Math.abs(dz);
                    };
                    double pB = switch (axis) { case 'X' -> dy; case 'Y' -> dx; default -> dx; };
                    double pC = switch (axis) { case 'X' -> dz; case 'Y' -> dz; default -> dy; };
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

    // ── recipe helpers ──────────────────────────────────────────────

    private static List<MepPosElement> readMepElementsWithPositions(Path refDb) throws SQLException {
        List<MepPosElement> result = new ArrayList<>();
        String url = "jdbc:sqlite:" + refDb;
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("""
                    SELECT m.ifc_class, m.element_type, m.storey,
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
                double cx = (minX + maxX) / 2.0;
                double cy = (minY + maxY) / 2.0;
                double cz = (minZ + maxZ) / 2.0;
                result.add(new MepPosElement(
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        cx, cy, cz, minX, maxX, minY, maxY, minZ, maxZ));
            }
        }
        return result;
    }

    /** Classify IFC class → discipline code (FP/ACMV/CW/SP/ELEC). */
    private static String discFromClass(String ifcClass, String elementType) {
        return switch (ifcClass) {
            case "IfcFireSuppressionTerminal" -> "FP";
            case "IfcDuctSegment", "IfcDuctFitting", "IfcAirTerminal" -> "ACMV";
            case "IfcLightFixture" -> "ELEC";
            case "IfcFlowTerminal" -> {
                String et = elementType != null ? elementType.toLowerCase() : "";
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

    /** Derive short BOM prefix from buildingType: "Revit_MEP" → "RM". */
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
                 component_type, c_uom_id, qty_type, rotation_rule, IsActive)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'LEAF', ?, ?, ?, 'Y')
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
        String sql = """
                INSERT OR REPLACE INTO _import_joint_piece_types
                (ifc_class, element_type, piece_type, diameter_mm, length_mm,
                 angle_deg, material, source_building, count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
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

    // ── records ─────────────────────────────────────────────────────

    record MepElement(String ifcClass, String elementType, String discipline,
                      double dx, double dy, double dz) {}

    record JointPieceType(String ifcClass, String elementType, String pieceType,
                          double diameterMm, double lengthMm, double angleDeg,
                          String material, String sourceBuilding, int count) {}

    /** MEP element with world centroid and AABB bounds (metres), for recipe building. */
    record MepPosElement(String ifcClass, String elementType, String storey,
                         double cx, double cy, double cz,
                         double minX, double maxX, double minY, double maxY,
                         double minZ, double maxZ) {}

    public record Result(int mepElements, int stagedTypes, int newProducts) {}

    public record RecipeResult(int runsBuilt, int linesWritten, int shimAnchors) {}
}
