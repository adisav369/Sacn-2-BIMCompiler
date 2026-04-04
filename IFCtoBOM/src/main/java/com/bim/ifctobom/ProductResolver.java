package com.bim.ifctobom;

import java.sql.*;
import java.util.*;

/**
 * Resolves raw IFC element names to abstract product identifiers.
 * // Implementing BBC.md §9 Data Flywheel — Witness: W-PRODUCT-ABSTRACT
 *
 * <h3>Resolution cascade</h3>
 * <ol>
 *   <li>Priority 2: element_name LIKE pattern → qualified type prefix (e.g., DOOR_INT)</li>
 *   <li>Priority 1: ifc_class exact match → base type prefix (e.g., DOOR)</li>
 *   <li>Fallback: {@link ProductRegistrar#deriveProductType} → ELEMENT</li>
 * </ol>
 *
 * <p>The resolved prefix is combined with dimension band to produce the full
 * abstract product_id: {@code DOOR_INT_810x2110}.
 *
 * <h3>Alias source</h3>
 * <p>Aliases are loaded from {@code ad_element_product_alias} in ERP.db
 * (migration DV034). Loaded once into an in-memory cache at pipeline start.
 *
 * <h3>DETERMINISTIC — no invention</h3>
 * <p>Every abstract name is derived from IFC data (class, element_name, AABB).
 * No human-crafted IDs. The alias table is mined from real buildings.
 */
public class ProductResolver {

    /** An alias rule loaded from ad_element_product_alias. */
    private record AliasRule(String abstractType, String matchField,
                             String matchValue, int priority) {}

    private final List<AliasRule> nameRules;   // priority 2: element_name LIKE
    private final Map<String, String> classMap; // priority 1: ifc_class → abstract_type

    private ProductResolver(List<AliasRule> nameRules, Map<String, String> classMap) {
        this.nameRules = nameRules;
        this.classMap = classMap;
    }

    /**
     * Load alias rules from ERP.db into an in-memory resolver.
     * Call once at pipeline start; pass the resolver into deriveRows.
     *
     * @param discConn read connection to ERP.db
     * @return resolver instance (empty if table doesn't exist yet)
     */
    public static ProductResolver load(Connection discConn) throws SQLException {
        List<AliasRule> nameRules = new ArrayList<>();
        Map<String, String> classMap = new LinkedHashMap<>();

        // Graceful: table may not exist on first run before migration
        try (ResultSet tables = discConn.getMetaData().getTables(
                null, null, "ad_element_product_alias", null)) {
            if (!tables.next()) {
                System.out.println("  [ProductResolver] ad_element_product_alias not found — using fallback naming");
                return new ProductResolver(nameRules, classMap);
            }
        }

        String sql = """
                SELECT abstract_type, match_field, match_value, priority
                FROM ad_element_product_alias
                WHERE is_active = 1
                ORDER BY priority ASC, alias_id ASC
                """;
        try (Statement stmt = discConn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String abstractType = rs.getString(1);
                String matchField = rs.getString(2);
                String matchValue = rs.getString(3);
                int priority = rs.getInt(4);
                if ("ifc_class".equals(matchField)) {
                    classMap.putIfAbsent(matchValue, abstractType);
                } else if ("element_name".equals(matchField)) {
                    nameRules.add(new AliasRule(abstractType, matchField, matchValue, priority));
                }
            }
        }
        System.out.printf("  [ProductResolver] Loaded %d class aliases, %d name patterns%n",
                classMap.size(), nameRules.size());
        return new ProductResolver(nameRules, classMap);
    }

    /**
     * Resolve a raw IFC element to an abstract product_id.
     *
     * @param elementRef  raw element reference (e.g., "Doors_IntSgl:810x2110mm")
     * @param ifcClass    IFC class (e.g., "IfcDoor")
     * @param orientation wall orientation (EW/NS/POINT) or null
     * @param widthM      AABB width in metres
     * @param depthM      AABB depth in metres
     * @param heightM     AABB height in metres
     * @return abstract product_id (e.g., "DOOR_INT_810x2110")
     */
    public String resolve(String elementRef, String ifcClass, String orientation,
                          double widthM, double depthM, double heightM) {
        // Step 1: Try element_name LIKE patterns (priority 2 — most specific)
        String prefix = matchByName(elementRef);

        // Step 2: Fall back to ifc_class exact match (priority 1 — base type)
        if (prefix == null && ifcClass != null) {
            prefix = classMap.get(ifcClass);
        }

        // Step 3: Ultimate fallback — deriveProductType
        if (prefix == null) {
            prefix = ProductRegistrar.deriveProductType(ifcClass);
        }

        // Append orientation qualifier for walls (if not already in prefix)
        if (orientation != null && prefix.startsWith("WALL") && !prefix.contains("_EW") && !prefix.contains("_NS")) {
            prefix = prefix + "_" + orientation;
        }

        // Build dimension band suffix
        String dims = formatDims(ifcClass, widthM, depthM, heightM);

        return prefix + "_" + dims;
    }

    /**
     * Match element_name against LIKE patterns. Returns abstract_type or null.
     * Patterns are pre-sorted by priority; first match wins.
     */
    private String matchByName(String elementRef) {
        if (elementRef == null || nameRules.isEmpty()) return null;
        for (AliasRule rule : nameRules) {
            if (sqlLike(elementRef, rule.matchValue)) {
                return rule.abstractType;
            }
        }
        return null;
    }

    /**
     * Java implementation of SQL LIKE with % wildcards.
     * Case-insensitive to match SQLite default collation.
     */
    static boolean sqlLike(String value, String pattern) {
        // Convert SQL LIKE pattern to regex
        String regex = "(?i)" + pattern
                .replace(".", "\\.")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("%", ".*")
                .replace("_", ".");
        return value.matches(regex);
    }

    /**
     * Format AABB dimensions as the product_id suffix.
     *
     * <p>Convention:
     * <ul>
     *   <li>Walls: thickness × height (width is length, varies per instance)</li>
     *   <li>Doors/Windows: width × height (depth is frame thickness, irrelevant for identity)</li>
     *   <li>Beams/Columns: profile dims (W × D × length)</li>
     *   <li>Everything else: W × D × H (3-axis)</li>
     * </ul>
     *
     * <p>All in mm, rounded to nearest integer.
     */
    private static String formatDims(String ifcClass, double w, double d, double h) {
        int wMm = toMm(w);
        int dMm = toMm(d);
        int hMm = toMm(h);

        if (ifcClass == null) return wMm + "x" + dMm + "x" + hMm;

        return switch (ifcClass) {
            // Walls: thickness (min of w,d) × height. Length varies per instance.
            case "IfcWall", "IfcWallStandardCase" -> {
                int thickness = Math.min(wMm, dMm);
                yield thickness + "x" + hMm;
            }
            // Doors/Windows: opening width × height. Depth is frame thickness.
            case "IfcDoor", "IfcWindow" -> wMm + "x" + hMm;
            // Slabs/Roofs: thickness only (area varies per instance)
            case "IfcSlab", "IfcRoof" -> {
                int thickness = hMm;
                yield thickness + "t";
            }
            // Coverings: thickness only
            case "IfcCovering" -> hMm + "t";
            // Default: 3-axis
            default -> wMm + "x" + dMm + "x" + hMm;
        };
    }

    private static int toMm(double metres) {
        return (int) Math.round(metres * 1000);
    }
}
