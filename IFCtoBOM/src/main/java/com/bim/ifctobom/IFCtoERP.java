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

    public record Result(int mepElements, int stagedTypes, int newProducts) {}
}
