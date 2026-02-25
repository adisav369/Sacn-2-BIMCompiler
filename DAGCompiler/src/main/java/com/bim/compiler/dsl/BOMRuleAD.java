package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Phase 60: Authority Data queries for BOM (Bill of Materials) rules.
 *
 * BOM Types:
 * - MANDATORY: Always required (qty=1 unless overridden)
 * - OPTIONAL: User-specified in DSL
 * - VARIABLE: Calculated from room properties (area, occupancy, CFM)
 *
 * Calculation Rules:
 * - PER_AREA: qty = ceil(room_area / base_value)
 * - PER_LUX: qty = ceil(room_area * target_lux / lumens_per_fixture)
 * - PER_CFM: qty = ceil(required_cfm / cfm_per_unit)
 * - PER_OCCUPANT: qty = ceil(occupancy / seats_per_unit)
 * - PER_LINEAR: qty = ceil(perimeter / spacing)
 * - FIXED: qty = base_value (constant)
 */
public class BOMRuleAD {

    private static final String DB_PATH = "database/authority_data.db";

    /**
     * BOM rule from authority data.
     * All formula parameters stored in DB, no hardcoding in Java.
     */
    public record BOMRule(
        int ruleId,
        String spaceType,
        String elementType,      // SPRINKLER, LIGHT, DIFFUSER, FURNITURE, FIXTURE
        String elementSubtype,   // pendant, supply, canteen_table, etc.
        String bomType,          // MANDATORY, OPTIONAL, VARIABLE
        String componentName,    // Library component pattern
        String calcRule,         // PER_AREA, PER_LUX, PER_CFM, PER_OCCUPANT, FIXED
        double calcBase,         // Divisor or base value
        String calcParam,        // Additional parameter (e.g., target lux)
        Double calcOccupancyDensity,  // m²/person for PER_OCCUPANT (from DB)
        Double calcCfmDensity,        // CFM/m² for PER_CFM (from DB)
        String calcFormula,           // Human-readable formula for audit
        int minQty,
        Integer maxQty,          // null = unlimited
        int priority,
        String codeId,
        String clause,
        String notes
    ) {
        /**
         * Calculate quantity for this rule given room properties.
         * Uses formula parameters from database - no hardcoded values.
         *
         * @param areaM2 Room area in square meters
         * @param occupancy Room occupancy (0 if not specified)
         * @param cfm Required CFM (0 if not applicable)
         * @param perimeterM Room perimeter in meters (0 if not applicable)
         * @return Calculated quantity, clamped to min/max
         */
        public int calculateQuantity(double areaM2, int occupancy, double cfm, double perimeterM) {
            int qty;

            switch (calcRule) {
                case "PER_AREA" -> {
                    qty = (int) Math.ceil(areaM2 / calcBase);
                }
                case "PER_LUX" -> {
                    // calcBase = lumens per fixture, calcParam = target lux
                    double targetLux = calcParam != null ? Double.parseDouble(calcParam) : 300;
                    double totalLumens = areaM2 * targetLux;
                    qty = (int) Math.ceil(totalLumens / calcBase);
                }
                case "PER_CFM" -> {
                    // Use cfmDensity from DB, or provided CFM value
                    double cfmDensity = calcCfmDensity != null ? calcCfmDensity : 1.0;
                    double requiredCfm = cfm > 0 ? cfm : areaM2 * cfmDensity;
                    qty = (int) Math.ceil(requiredCfm / calcBase);
                }
                case "PER_OCCUPANT" -> {
                    // Use occupancyDensity from DB to estimate occupancy
                    double occDensity = calcOccupancyDensity != null ? calcOccupancyDensity : 2.5;
                    int occ = occupancy > 0 ? occupancy : (int) Math.ceil(areaM2 / occDensity);
                    qty = (int) Math.ceil((double) occ / calcBase);
                }
                case "PER_LINEAR" -> {
                    // If perimeter not provided, estimate as sqrt(area)*4
                    double perim = perimeterM > 0 ? perimeterM : Math.sqrt(areaM2) * 4;
                    qty = (int) Math.ceil(perim / calcBase);
                }
                case "FIXED" -> {
                    qty = (int) calcBase;
                }
                default -> {
                    qty = 1;
                }
            }

            // Clamp to min/max
            qty = Math.max(qty, minQty);
            if (maxQty != null) {
                qty = Math.min(qty, maxQty);
            }

            return qty;
        }

        /**
         * Format calculation for logging/witness.
         * Uses calc_formula from DB if available, otherwise generates dynamically.
         */
        public String formatCalculation(double areaM2, int qty) {
            // If formula stored in DB, use it with result
            if (calcFormula != null && !calcFormula.isEmpty()) {
                return calcFormula + " = " + qty;
            }
            // Fallback to dynamic formatting
            return switch (calcRule) {
                case "PER_AREA" -> String.format("ceil(%.1fm² / %.1f) = %d", areaM2, calcBase, qty);
                case "PER_LUX" -> String.format("ceil(%.1fm² × %s lux / %.0f lm) = %d",
                    areaM2, calcParam, calcBase, qty);
                case "PER_CFM" -> String.format("ceil(CFM / %.0f) = %d", calcBase, qty);
                case "PER_OCCUPANT" -> String.format("ceil(occupancy / %.0f) = %d", calcBase, qty);
                case "FIXED" -> String.format("FIXED = %d", qty);
                default -> String.format("%s = %d", calcRule, qty);
            };
        }
    }

    /**
     * Resolved BOM item with calculated quantity.
     */
    public record ResolvedBOMItem(
        BOMRule rule,
        int quantity,
        String calculation,      // Human-readable calculation
        String roomName,
        double roomAreaM2
    ) {}

    private final Connection conn;
    private final Map<String, List<BOMRule>> ruleCache = new HashMap<>();

    public BOMRuleAD() throws SQLException {
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        loadAllRules();
    }

    public BOMRuleAD(String dbPath) throws SQLException {
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        loadAllRules();
    }

    /**
     * Load all rules into cache for fast lookup.
     */
    private void loadAllRules() throws SQLException {
        String sql = """
            SELECT rule_id, space_type, element_type, element_subtype, bom_type,
                   component_name, calc_rule, calc_base, calc_param,
                   calc_occupancy_density, calc_cfm_density, calc_formula,
                   min_qty, max_qty, priority, code_id, clause, notes
            FROM ad_bom_rule
            ORDER BY priority, rule_id
            """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                BOMRule rule = new BOMRule(
                    rs.getInt("rule_id"),
                    rs.getString("space_type"),
                    rs.getString("element_type"),
                    rs.getString("element_subtype"),
                    rs.getString("bom_type"),
                    rs.getString("component_name"),
                    rs.getString("calc_rule"),
                    rs.getDouble("calc_base"),
                    rs.getString("calc_param"),
                    rs.getObject("calc_occupancy_density") != null ? rs.getDouble("calc_occupancy_density") : null,
                    rs.getObject("calc_cfm_density") != null ? rs.getDouble("calc_cfm_density") : null,
                    rs.getString("calc_formula"),
                    rs.getInt("min_qty"),
                    rs.getObject("max_qty") != null ? rs.getInt("max_qty") : null,
                    rs.getInt("priority"),
                    rs.getString("code_id"),
                    rs.getString("clause"),
                    rs.getString("notes")
                );

                String key = rule.spaceType().toUpperCase();
                ruleCache.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
            }
        }

        System.out.printf("[BOMRuleAD] Loaded %d rules for %d space types%n",
            ruleCache.values().stream().mapToInt(List::size).sum(),
            ruleCache.size());
    }

    /**
     * Get all BOM rules for a space type.
     */
    public List<BOMRule> getRulesForSpace(String spaceType) {
        return ruleCache.getOrDefault(spaceType.toUpperCase(), List.of());
    }

    /**
     * Get rules for a specific element type within a space.
     */
    public List<BOMRule> getRulesForElement(String spaceType, String elementType) {
        return getRulesForSpace(spaceType).stream()
            .filter(r -> r.elementType().equalsIgnoreCase(elementType))
            .toList();
    }

    /**
     * Resolve BOM for a room - calculate all quantities.
     *
     * @param spaceType Room type (CANTEEN, OFFICE, etc.)
     * @param roomName Room name for logging
     * @param areaM2 Room area in square meters
     * @param occupancy Specified occupancy (0 = calculate from area)
     * @return List of resolved BOM items with quantities
     */
    public List<ResolvedBOMItem> resolveRoom(String spaceType, String roomName,
                                              double areaM2, int occupancy) {
        List<ResolvedBOMItem> resolved = new ArrayList<>();

        for (BOMRule rule : getRulesForSpace(spaceType)) {
            int qty = rule.calculateQuantity(areaM2, occupancy, 0, 0);
            String calc = rule.formatCalculation(areaM2, qty);

            resolved.add(new ResolvedBOMItem(rule, qty, calc, roomName, areaM2));
        }

        return resolved;
    }

    /**
     * Print resolved BOM for a room.
     */
    public void printResolvedBOM(String spaceType, String roomName, double areaM2, int occupancy) {
        List<ResolvedBOMItem> items = resolveRoom(spaceType, roomName, areaM2, occupancy);

        System.out.printf("\n=== BOM: %s \"%s\" (%.1fm²) ===%n", spaceType, roomName, areaM2);

        for (ResolvedBOMItem item : items) {
            BOMRule r = item.rule();
            String code = r.codeId() != null ? r.codeId() + " " + r.clause() : "";
            System.out.printf("  %d× %-20s [%s] %s %s%n",
                item.quantity(),
                r.componentName(),
                r.bomType(),
                item.calculation(),
                code);
        }
    }

    // =========================================================================
    // Phase 85: BOM Placement Parameters — metadata-driven Z positioning
    // =========================================================================

    /**
     * Placement parameters loaded from m_bom_line + m_attribute.
     * Resolves element Z position from BOM metadata instead of hardcoded offsets.
     *
     * z_rule values: BELOW_SLAB, AT_CEILING, ABOVE_FLOOR, AT_FLOOR, BETWEEN_FLOORS
     */
    public record BOMPlacementParams(
        String zRule,        // BELOW_SLAB, AT_CEILING, etc.
        double zOffset,      // meters from reference surface
        double spacing,      // grid spacing (HEAD)
        double diameter,     // pipe diameter
        double dropOffset,   // branch drop from main to head
        String routing       // MANHATTAN, DIRECT
    ) {
        /** Resolve Z coordinate given storey geometry. */
        public double resolveZ(double baseZ, double storeyHeight, double slabThickness) {
            return switch (zRule != null ? zRule : "") {
                case "BELOW_SLAB" -> baseZ + storeyHeight - slabThickness - zOffset;
                case "AT_CEILING" -> baseZ + storeyHeight - zOffset;
                case "ABOVE_FLOOR" -> baseZ + zOffset;
                case "AT_FLOOR" -> baseZ;
                case "BETWEEN_FLOORS" -> baseZ; // riser spans full height
                default -> baseZ + storeyHeight - zOffset;
            };
        }
    }

    private static final String LIBRARY_DB_PATH = "library/BOM.db";

    /**
     * Load placement parameters for a BOM child role from component_library.db.
     *
     * @param bomId  e.g. "FP_PIPE_ASSEMBLY"
     * @param role   e.g. "HEAD", "MAIN", "BRANCH", "RISER"
     * @return BOMPlacementParams with all resolved values, or defaults if not found
     */
    public static BOMPlacementParams loadPlacementParams(String bomId, String role) {
        String sql = """
            SELECT c.z_rule, p.param_key, p.param_value
            FROM m_bom_line c
            LEFT JOIN m_attribute p ON c.bom_child_id = p.bom_child_id AND p.is_active = 1
            WHERE c.bom_id = ? AND c.role = ? AND c.is_active = 1
            """;

        String zRule = null;
        double zOffset = 0.0;
        double spacing = Double.NaN;
        double diameter = 0.0;
        double dropOffset = 0.0;
        String routing = "MANHATTAN";

        try (Connection libConn = DriverManager.getConnection("jdbc:sqlite:" + LIBRARY_DB_PATH);
             PreparedStatement ps = libConn.prepareStatement(sql)) {

            ps.setString(1, bomId);
            ps.setString(2, role);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (zRule == null) {
                        zRule = rs.getString("z_rule");
                    }
                    String key = rs.getString("param_key");
                    String val = rs.getString("param_value");
                    if (key == null) continue;

                    switch (key) {
                        case "z_offset" -> zOffset = Double.parseDouble(val);
                        case "spacing" -> spacing = Double.parseDouble(val);
                        case "diameter" -> diameter = Double.parseDouble(val);
                        case "drop_offset" -> dropOffset = Double.parseDouble(val);
                        case "routing" -> routing = val;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[BOMRuleAD] Failed to load placement params for " + bomId + "/" + role + ": " + e.getMessage());
        }

        if (Double.isNaN(spacing))
            throw new IllegalStateException(
                "No spacing param found for " + bomId + "/" + role + " in m_attribute");

        return new BOMPlacementParams(zRule, zOffset, spacing, diameter, dropOffset, routing);
    }

    public void close() throws SQLException {
        if (conn != null) {
            conn.close();
        }
    }

    /**
     * Test BOM resolution standalone.
     */
    public static void main(String[] args) throws SQLException {
        BOMRuleAD bom = new BOMRuleAD();

        // Test canteen (84m²)
        bom.printResolvedBOM("CANTEEN", "kantin", 84.0, 0);

        // Test classroom (56m², 30 students)
        bom.printResolvedBOM("CLASSROOM", "class_1", 56.0, 30);

        // Test office (36m²)
        bom.printResolvedBOM("OFFICE", "bilik_guru", 36.0, 0);

        // Test bathroom (6m²)
        bom.printResolvedBOM("BATHROOM", "toilet_1", 6.0, 0);

        bom.close();
    }
}
