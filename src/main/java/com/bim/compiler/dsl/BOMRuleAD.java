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
        int minQty,
        Integer maxQty,          // null = unlimited
        int priority,
        String codeId,
        String clause,
        String notes
    ) {
        /**
         * Calculate quantity for this rule given room properties.
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
                    // If CFM not provided, estimate from area
                    double requiredCfm = cfm > 0 ? cfm : areaM2 * 1.0;  // ~1 CFM/ft² default
                    qty = (int) Math.ceil(requiredCfm / calcBase);
                }
                case "PER_OCCUPANT" -> {
                    // If occupancy not provided, estimate from area
                    // 2.5m²/person for cafeteria seating (IBC Table 1004.5: 1.39m² for assembly
                    // with unconcentrated tables, but 2.5m² is more realistic for school canteen)
                    int occ = occupancy > 0 ? occupancy : (int) Math.ceil(areaM2 / 2.5);
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
         */
        public String formatCalculation(double areaM2, int qty) {
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
