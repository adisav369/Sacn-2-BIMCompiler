package com.bim.compiler.bom.walker;

// Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 — Witness: W-SPACE-SCHEDULE
// DAO for the generative MEP walker: reads schedule + offsets + capabilities.
// The walker calls this DAO, never raw SQL.

import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the space schedule chain for the generative MEP walker.
 *
 * <p>Joins three AD tables in one query:
 * <ul>
 *   <li>{@code ad_space_type_mep_bom} — what devices each space type needs</li>
 *   <li>{@code ad_placement_offset} — where to place them (edge offsets, z rules)</li>
 *   <li>{@code ad_discipline_capability} — which discipline maps to which capability</li>
 * </ul>
 *
 * <p>The walker sees only abstract tokens: SPACE → CAPABILITY → SCHEDULE → OFFSET → POSITION.
 * Concrete names (KITCHEN, SINK) live in metadata rows — the walker never interprets them.
 *
 * @see PlacementCollectorVisitor
 * @see MEPDevicePlacer
 */
public class SpaceScheduleDAO {

    /** One scheduled device with its placement offset resolved. */
    public record ScheduleEntry(
        String deviceId,        // mep_product_id (e.g. "SINK" — walker doesn't interpret)
        String placementRule,   // placement_rule (e.g. "WALL_SIDE")
        String hostSurface,     // host_surface (e.g. "WALL", "FLOOR", "CEILING")
        String anchorEnd,       // anchor_end (e.g. "RISER", "STACK", "PANEL")
        int qtyNormal,          // qty_normal from schedule
        int qtyMin,             // qty_min (code minimum)
        int qtyMax,             // qty_max (code maximum)
        double perAreaNormal,   // per_area_normal (0 = use fixed qty)
        double edgeX,           // from_edge_x (metres) from ad_placement_offset
        double edgeY,           // from_edge_y (metres)
        double zOffset,         // z_offset (metres)
        String zRule,           // FLOOR, CEILING, MID
        String xRef,            // CENTER, MIN, MAX
        String yRef,            // CENTER, MIN, MAX
        String standard         // building code reference (provenance)
    ) {}

    // ── Schedule Query ──────────────────────────────────────────────────────

    private static final String SCHEDULE_SQL = """
        SELECT s.mep_product_id, s.placement_rule, s.host_surface, s.anchor_end,
               s.qty_normal, s.qty_min, s.qty_max, s.per_area_normal,
               p.from_edge_x, p.from_edge_y, p.z_offset, p.z_rule,
               p.x_ref, p.y_ref, p.standard
        FROM ad_space_type_mep_bom s
        LEFT JOIN ad_placement_offset p ON s.placement_rule = p.placement_rule
        WHERE s.space_type_id = ?
        ORDER BY s.mep_product_id
        """;

    /**
     * All scheduled devices for a space type, with placement offsets resolved.
     *
     * @param conn ERP.db connection
     * @param spaceTypeId abstract space type (e.g. "BATHROOM")
     * @return schedule entries with offsets; empty list if no schedule exists
     */
    public static List<ScheduleEntry> getSchedule(Connection conn, String spaceTypeId)
            throws SQLException {
        List<ScheduleEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SCHEDULE_SQL)) {
            ps.setString(1, spaceTypeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new ScheduleEntry(
                        rs.getString("mep_product_id"),
                        rs.getString("placement_rule"),
                        rs.getString("host_surface"),
                        rs.getString("anchor_end"),
                        rs.getInt("qty_normal"),
                        rs.getInt("qty_min"),
                        rs.getInt("qty_max"),
                        rs.getDouble("per_area_normal"),
                        rs.getDouble("from_edge_x"),
                        rs.getDouble("from_edge_y"),
                        rs.getDouble("z_offset"),
                        rs.getString("z_rule"),
                        rs.getString("x_ref"),
                        rs.getString("y_ref"),
                        rs.getString("standard")
                    ));
                }
            }
        }
        BIMLogger.fine("COMPILE", "SpaceScheduleDAO.getSchedule({}) → {} entries",
            spaceTypeId, entries.size());
        return entries;
    }

    // ── Capability Query ────────────────────────────────────────────────────

    /**
     * Capability required by a discipline. Reads {@code ad_discipline_capability}.
     *
     * @param conn ERP.db connection
     * @param discipline discipline code (e.g. "CW", "SP", "ELEC")
     * @return capability token (e.g. "PLUMBABLE"), or null if unmapped
     */
    public static String getRequiredCapability(Connection conn, String discipline)
            throws SQLException {
        String sql = "SELECT capability FROM ad_discipline_capability WHERE discipline = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, discipline);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    // ── Ceiling Height ───────────────────────────────────────────────────────

    /** Minimum BOM AABB height (mm) before ceiling height override kicks in.
     *  Any room whose furniture AABB is shorter than this gets the metadata ceiling height. */
    private static final int MIN_ROOM_HEIGHT_MM = 2400;

    /**
     * Default ceiling height for a space type, in metres.
     *
     * <p>When the SET BOM's AABB height is furniture extent (e.g. 812mm sofa),
     * the walker calls this to get the actual room ceiling height from metadata
     * ({@code ad_space_type.default_ceiling_height_mm}).
     *
     * @param conn ERP.db connection
     * @param spaceType abstract space type (e.g. "BATHROOM")
     * @return ceiling height in metres; defaults to 2.7m if column missing or NULL
     */
    public static double getCeilingHeightM(Connection conn, String spaceType)
            throws SQLException {
        String sql = "SELECT default_ceiling_height_mm FROM ad_space_type WHERE Value = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, spaceType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int mm = rs.getInt(1);
                    if (mm > 0) return mm / 1000.0;
                }
            }
        }
        return 2.7; // residential default
    }

    /**
     * Whether the given BOM AABB height (mm) is below the threshold for
     * ceiling height override. When true, the walker should use
     * {@link #getCeilingHeightM} instead of the BOM AABB height.
     */
    public static boolean needsCeilingOverride(int aabbHeightMm) {
        return aabbHeightMm > 0 && aabbHeightMm < MIN_ROOM_HEIGHT_MM;
    }

    // ── Product Dimensions ─────────────────────────────────────────────────

    /**
     * Read M_Product dimensions (width, depth, height) in metres.
     *
     * <p>Used by the generative walker to size Placement AABBs correctly
     * (instead of hardcoded ±0.05m). Returns null if product not found
     * or dimensions are zero.
     *
     * @param conn ERP.db connection
     * @param productId product_id or Value to look up
     * @return double[3] = {width, depth, height} in metres, or null
     */
    public static double[] getProductDimensions(Connection conn, String productId)
            throws SQLException {
        String sql = "SELECT width, depth, height FROM M_Product WHERE product_id = ? OR Value = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, productId);
            ps.setString(2, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double w = rs.getDouble(1);
                    double d = rs.getDouble(2);
                    double h = rs.getDouble(3);
                    if (w > 0 && d > 0 && h > 0) return new double[]{w, d, h};
                }
            }
        }
        return null;
    }

    // ── Space Type by Capability ────────────────────────────────────────────

    /**
     * All space types with a given capability flag set.
     *
     * <p>Capability columns on {@code ad_space_type}: is_plumbable, is_electrified,
     * is_fire_protected, is_ventilated, is_gas_served.
     *
     * @param conn ERP.db connection
     * @param capability abstract capability (e.g. "PLUMBABLE")
     * @return list of space type Values (e.g. ["BATHROOM","KITCHEN","UTILITY",...])
     */
    public static List<String> getSpaceTypesByCapability(Connection conn, String capability)
            throws SQLException {
        // Map capability token to column name — no dynamic SQL, explicit mapping
        String column = switch (capability) {
            case "PLUMBABLE"       -> "is_plumbable";
            case "ELECTRIFIED"     -> "is_electrified";
            case "FIRE_PROTECTED"  -> "is_fire_protected";
            case "VENTILATED"      -> "is_ventilated";
            case "GAS_SERVED"      -> "is_gas_served";
            default -> null;
        };
        if (column == null) {
            BIMLogger.warn("COMPILE", "SpaceScheduleDAO: unknown capability '{}'", capability);
            return List.of();
        }

        String sql = "SELECT Value FROM ad_space_type WHERE " + column + " = 1 AND is_active = 1 ORDER BY Value";
        List<String> types = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) types.add(rs.getString(1));
            }
        }
        BIMLogger.fine("COMPILE", "SpaceScheduleDAO.getSpaceTypesByCapability({}) → {} types: {}",
            capability, types.size(), types);
        return types;
    }

    // ── Position Computation ────────────────────────────────────────────────

    /**
     * Compute device position from room AABB + schedule entry offsets.
     * Pure maths — same logic as S9 generative proof but reusable.
     *
     * <p>AABB layout: {@code [minX, maxX, minY, maxY, minZ, maxZ]}.
     *
     * @param roomAabb 6-element AABB of the room
     * @param entry schedule entry with resolved offsets
     * @return double[3] = {x, y, z} in metres
     */
    public static double[] computePosition(double[] roomAabb, ScheduleEntry entry) {
        double minX = roomAabb[0], maxX = roomAabb[1];
        double minY = roomAabb[2], maxY = roomAabb[3];
        double minZ = roomAabb[4], maxZ = roomAabb[5];

        // Default: room center
        double px = (minX + maxX) / 2.0;
        double py = (minY + maxY) / 2.0;
        double pz = (minZ + maxZ) / 2.0;

        // Apply metadata offsets from ad_placement_offset
        if (entry.edgeX() > 0 || entry.edgeY() > 0 || entry.zOffset() > 0
                || entry.zRule() != null) {
            px = switch (entry.xRef() != null ? entry.xRef() : "CENTER") {
                case "MIN" -> minX + entry.edgeX();
                case "MAX" -> maxX - entry.edgeX();
                default -> px; // CENTER
            };
            py = switch (entry.yRef() != null ? entry.yRef() : "CENTER") {
                case "MIN" -> minY + entry.edgeY();
                case "MAX" -> maxY - entry.edgeY();
                default -> py; // CENTER
            };
            pz = switch (entry.zRule() != null ? entry.zRule() : "MID") {
                case "FLOOR"   -> minZ + entry.zOffset();
                case "CEILING" -> maxZ - entry.zOffset();
                default -> pz; // MID
            };
        }

        return new double[]{px, py, pz};
    }

    /**
     * Resolve effective quantity from order coverage level and schedule entry.
     *
     * <p>Order qty interpretation (§6.12.4 §8):
     * <ul>
     *   <li>99 / blank → use {@code qty_normal} from schedule</li>
     *   <li>0 → use {@code qty_max} (fill to maximum)</li>
     *   <li>N → cap total devices at N (but never below {@code qty_min})</li>
     * </ul>
     *
     * @param orderQty order-level coverage qty (99=standard, 0=max, N=budget cap)
     * @param entry schedule entry
     * @param roomAreaM2 room floor area in m² (for per_area rules)
     * @return effective device count for this entry in this room
     */
    public static int resolveQty(int orderQty, ScheduleEntry entry, double roomAreaM2) {
        int qty;
        if (entry.perAreaNormal() > 0) {
            // Per-area rule: compute from room area
            qty = (int) Math.ceil(roomAreaM2 * entry.perAreaNormal());
        } else if (orderQty == 0) {
            qty = entry.qtyMax();
        } else if (orderQty == 99 || orderQty < 0) {
            qty = entry.qtyNormal();
        } else {
            // Budget cap: use order qty but respect code minimum
            qty = orderQty;
        }
        // Always respect code minimum
        return Math.max(entry.qtyMin(), qty);
    }

    private SpaceScheduleDAO() {} // static utility
}
