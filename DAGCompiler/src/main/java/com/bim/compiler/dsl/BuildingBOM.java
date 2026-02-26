package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Building BOM Expander - iDempiere M_BOM pattern for buildings.
 *
 * DSL Usage:
 * <pre>
 * BUILDING "MyProject" template:"CONDO_MID" {
 *     floors: 18                    // Override default
 *     typical_start: 3              // Override where typical begins
 *
 *     // Unit mix for typical floors
 *     TYPICAL_MIX {
 *         "2BR_A" at:A1
 *         "2BR_A" at:A3 mirror:true
 *         "1BR_B" at:B1
 *         "1BR_A" at:B3
 *     }
 * }
 * </pre>
 *
 * Expands to:
 * <pre>
 * B2 (BASEMENT_PARKING)
 * B1 (BASEMENT_PARKING)
 * Ground (GROUND_LOBBY)
 * Level_1..Level_12 (TYPICAL_CONDO) → units from mix
 * Amenity (AMENITY)
 * Penthouse (PENTHOUSE)
 * Roof (ROOF_MECH)
 * </pre>
 *
 * The 80/20: One DSL line generates 18 floors with all attributes.
 */
public class BuildingBOM {

    private static final String DB_PATH = "library/BOM.db";
    private static Connection connection = null;

    // =========================================================================
    // Public Records
    // =========================================================================

    /**
     * Building template from AD.
     */
    public record BuildingTemplate(
        String templateId,
        String templateName,
        String buildingType,
        int defaultFloors,
        int minFloors,
        int maxFloors,
        int typicalStart,
        int typicalEnd,
        String structuralSystem,
        String codeReference
    ) {}

    /**
     * Floor type definition.
     */
    public record FloorType(
        String floorTypeId,
        String floorName,
        String category,
        double floorHeight,
        double slabThickness,
        boolean belowGrade,
        boolean mechanical,
        boolean amenity,
        List<String> unitTypes,
        String structuralNotes,
        String mepNotes
    ) {}

    /**
     * BOM entry linking template to floor type.
     */
    public record BOMEntry(
        String templateId,
        String floorTypeId,
        String position,     // HEAD, STANDARD, TAIL
        int seqNo,
        Integer qtyFixed,
        Integer qtyMin,
        Integer qtyMax,
        String levelNamePattern
    ) {}

    /**
     * Expanded floor instance.
     */
    public record FloorInstance(
        String levelName,
        int levelIndex,          // 0-based from bottom
        String floorTypeId,
        String category,
        double baseZ,            // Elevation of floor slab top
        double floorHeight,
        double slabThickness,
        boolean belowGrade,
        boolean mechanical,
        String position          // HEAD, STANDARD, TAIL
    ) {}

    /**
     * Unit type definition.
     */
    public record UnitType(
        String unitTypeId,
        String unitName,
        String category,
        double typicalArea,
        int bedrooms,
        int bathrooms,
        boolean hasBalcony,
        Map<String, Double> roomLayout,
        Map<String, Double> mepLoad
    ) {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Get building template by ID.
     */
    public static BuildingTemplate getTemplate(String templateId) {
        try {
            Connection conn = getConnection();
            if (conn == null) return null;

            String sql = """
                SELECT template_id, template_name, building_type, default_floors,
                       min_floors, max_floors, typical_floor_start, typical_floor_end,
                       structural_system, code_reference
                FROM ad_building_template WHERE template_id = ? AND is_active = 1
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, templateId.toUpperCase());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return new BuildingTemplate(
                        rs.getString("template_id"),
                        rs.getString("template_name"),
                        rs.getString("building_type"),
                        rs.getInt("default_floors"),
                        rs.getInt("min_floors"),
                        rs.getInt("max_floors"),
                        rs.getInt("typical_floor_start"),
                        rs.getInt("typical_floor_end"),
                        rs.getString("structural_system"),
                        rs.getString("code_reference")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[BuildingBOM] getTemplate error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get all BOM entries for a template.
     */
    public static List<BOMEntry> getBOMEntries(String templateId) {
        List<BOMEntry> result = new ArrayList<>();
        try {
            Connection conn = getConnection();
            if (conn == null) return result;

            String sql = """
                SELECT template_id, floor_type_id, position, seq_no,
                       qty_fixed, qty_min, qty_max, level_name_pattern
                FROM ad_building_bom
                WHERE template_id = ? AND is_active = 1
                ORDER BY
                    CASE position WHEN 'HEAD' THEN 1 WHEN 'STANDARD' THEN 2 WHEN 'TAIL' THEN 3 END,
                    seq_no
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, templateId.toUpperCase());
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    result.add(new BOMEntry(
                        rs.getString("template_id"),
                        rs.getString("floor_type_id"),
                        rs.getString("position"),
                        rs.getInt("seq_no"),
                        rs.getObject("qty_fixed") != null ? rs.getInt("qty_fixed") : null,
                        rs.getObject("qty_min") != null ? rs.getInt("qty_min") : null,
                        rs.getObject("qty_max") != null ? rs.getInt("qty_max") : null,
                        rs.getString("level_name_pattern")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[BuildingBOM] getBOMEntries error: " + e.getMessage());
        }
        return result;
    }

    /**
     * Get floor type by ID.
     */
    public static FloorType getFloorType(String floorTypeId) {
        try {
            Connection conn = getConnection();
            if (conn == null) return null;

            String sql = """
                SELECT floor_type_id, floor_name, floor_category, floor_height,
                       slab_thickness, is_below_grade, is_mechanical, is_amenity,
                       unit_types, structural_notes, mep_notes
                FROM ad_floor_type WHERE floor_type_id = ? AND is_active = 1
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, floorTypeId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return new FloorType(
                        rs.getString("floor_type_id"),
                        rs.getString("floor_name"),
                        rs.getString("floor_category"),
                        rs.getDouble("floor_height"),
                        rs.getDouble("slab_thickness"),
                        rs.getInt("is_below_grade") == 1,
                        rs.getInt("is_mechanical") == 1,
                        rs.getInt("is_amenity") == 1,
                        parseJsonArray(rs.getString("unit_types")),
                        rs.getString("structural_notes"),
                        rs.getString("mep_notes")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[BuildingBOM] getFloorType error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get unit type by ID.
     */
    public static UnitType getUnitType(String unitTypeId) {
        try {
            Connection conn = getConnection();
            if (conn == null) return null;

            String sql = """
                SELECT unit_type_id, unit_name, unit_category, typical_area,
                       bedroom_count, bathroom_count, has_balcony, room_layout, mep_load
                FROM ad_unit_type WHERE unit_type_id = ? AND is_active = 1
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, unitTypeId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return new UnitType(
                        rs.getString("unit_type_id"),
                        rs.getString("unit_name"),
                        rs.getString("unit_category"),
                        rs.getDouble("typical_area"),
                        rs.getInt("bedroom_count"),
                        rs.getInt("bathroom_count"),
                        rs.getInt("has_balcony") == 1,
                        parseJsonMap(rs.getString("room_layout")),
                        parseJsonMap(rs.getString("mep_load"))
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[BuildingBOM] getUnitType error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Expand a building template into floor instances.
     * This is the core BOM explosion function.
     *
     * @param templateId Building template ID
     * @param totalFloors Total floors (null = use default)
     * @param typicalCount Override typical floor count (null = calculate)
     * @return List of floor instances from bottom to top
     */
    public static List<FloorInstance> expandBOM(String templateId, Integer totalFloors, Integer typicalCount) {
        List<FloorInstance> floors = new ArrayList<>();

        BuildingTemplate template = getTemplate(templateId);
        if (template == null) {
            System.err.println("[BuildingBOM] Template not found: " + templateId);
            return floors;
        }

        List<BOMEntry> entries = getBOMEntries(templateId);
        if (entries.isEmpty()) {
            System.err.println("[BuildingBOM] No BOM entries for: " + templateId);
            return floors;
        }

        // Calculate floor counts
        int total = totalFloors != null ? totalFloors : template.defaultFloors();
        total = Math.max(template.minFloors(), Math.min(template.maxFloors(), total));

        // Count fixed HEAD and TAIL floors
        int headCount = 0, tailCount = 0;
        for (BOMEntry e : entries) {
            if ("HEAD".equals(e.position()) && e.qtyFixed() != null) {
                headCount += e.qtyFixed();
            } else if ("TAIL".equals(e.position()) && e.qtyFixed() != null) {
                tailCount += e.qtyFixed();
            }
        }

        // Calculate STANDARD floor count
        int standardCount = typicalCount != null ? typicalCount : (total - headCount - tailCount);
        standardCount = Math.max(0, standardCount);

        // Expand floors
        double currentZ = 0;
        int levelIndex = 0;

        // HEAD floors (basement going down, then ground)
        List<BOMEntry> headEntries = entries.stream()
            .filter(e -> "HEAD".equals(e.position()))
            .sorted(Comparator.comparingInt(BOMEntry::seqNo))
            .toList();

        // First pass: calculate basement depth
        double basementDepth = 0;
        for (BOMEntry entry : headEntries) {
            FloorType ft = getFloorType(entry.floorTypeId());
            if (ft != null && ft.belowGrade()) {
                int qty = entry.qtyFixed() != null ? entry.qtyFixed() : 1;
                basementDepth += qty * ft.floorHeight();
            }
        }
        currentZ = -basementDepth;

        // Expand HEAD
        for (BOMEntry entry : headEntries) {
            FloorType ft = getFloorType(entry.floorTypeId());
            if (ft == null) continue;

            int qty = entry.qtyFixed() != null ? entry.qtyFixed() : 1;
            for (int i = 0; i < qty; i++) {
                String levelName = formatLevelName(entry.levelNamePattern(), ft.belowGrade() ? (qty - i) : (i + 1));
                floors.add(new FloorInstance(
                    levelName, levelIndex++, ft.floorTypeId(), ft.category(),
                    currentZ, ft.floorHeight(), ft.slabThickness(),
                    ft.belowGrade(), ft.mechanical(), "HEAD"
                ));
                currentZ += ft.floorHeight();
            }
        }

        // STANDARD floors
        BOMEntry standardEntry = entries.stream()
            .filter(e -> "STANDARD".equals(e.position()))
            .findFirst()
            .orElse(null);

        if (standardEntry != null && standardCount > 0) {
            FloorType ft = getFloorType(standardEntry.floorTypeId());
            if (ft != null) {
                for (int i = 0; i < standardCount; i++) {
                    String levelName = formatLevelName(standardEntry.levelNamePattern(), i + 1);
                    floors.add(new FloorInstance(
                        levelName, levelIndex++, ft.floorTypeId(), ft.category(),
                        currentZ, ft.floorHeight(), ft.slabThickness(),
                        ft.belowGrade(), ft.mechanical(), "STANDARD"
                    ));
                    currentZ += ft.floorHeight();
                }
            }
        }

        // TAIL floors
        List<BOMEntry> tailEntries = entries.stream()
            .filter(e -> "TAIL".equals(e.position()))
            .sorted(Comparator.comparingInt(BOMEntry::seqNo))
            .toList();

        for (BOMEntry entry : tailEntries) {
            FloorType ft = getFloorType(entry.floorTypeId());
            if (ft == null) continue;

            int qty = entry.qtyFixed() != null ? entry.qtyFixed() : 1;
            for (int i = 0; i < qty; i++) {
                String levelName = formatLevelName(entry.levelNamePattern(), i + 1);
                floors.add(new FloorInstance(
                    levelName, levelIndex++, ft.floorTypeId(), ft.category(),
                    currentZ, ft.floorHeight(), ft.slabThickness(),
                    ft.belowGrade(), ft.mechanical(), "TAIL"
                ));
                currentZ += ft.floorHeight();
            }
        }

        return floors;
    }

    /**
     * List all available templates.
     */
    public static List<String> listTemplates() {
        List<String> result = new ArrayList<>();
        try {
            Connection conn = getConnection();
            if (conn == null) return result;

            String sql = "SELECT template_id FROM ad_building_template WHERE is_active = 1 ORDER BY template_id";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    result.add(rs.getString("template_id"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[BuildingBOM] listTemplates error: " + e.getMessage());
        }
        return result;
    }

    public static boolean isAvailable() {
        try {
            Connection conn = getConnection();
            if (conn == null) return false;

            String sql = "SELECT COUNT(*) FROM ad_building_template";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public static void close() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) {}
            connection = null;
        }
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    private static synchronized Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return connection;
        }

        java.io.File dbFile = new java.io.File(DB_PATH);
        if (!dbFile.exists()) return null;

        connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        return connection;
    }

    private static String formatLevelName(String pattern, int index) {
        if (pattern == null) return "Level_" + index;
        if (pattern.contains("%d")) {
            return String.format(pattern, index);
        }
        return pattern;
    }

    private static List<String> parseJsonArray(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return result;

        json = json.replace("[", "").replace("]", "").replace("\"", "");
        for (String item : json.split(",")) {
            String s = item.trim();
            if (!s.isEmpty()) result.add(s);
        }
        return result;
    }

    private static Map<String, Double> parseJsonMap(String json) {
        Map<String, Double> result = new HashMap<>();
        if (json == null || json.isEmpty()) return result;

        json = json.replace("{", "").replace("}", "").replace("\"", "");
        for (String pair : json.split(",")) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                try {
                    result.put(kv[0].trim(), Double.parseDouble(kv[1].trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }

    // =========================================================================
    // Test Main
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=== Building BOM Test ===\n");

        if (!isAvailable()) {
            System.out.println("BOM tables not available");
            System.out.println("Run: python scripts/create_ad_building_bom.py");
            return;
        }

        System.out.println("Available templates: " + listTemplates());

        // Expand CONDO_MID with 18 floors
        System.out.println("\n--- CONDO_MID (18 floors) ---");
        List<FloorInstance> floors = expandBOM("CONDO_MID", 18, null);

        for (FloorInstance f : floors) {
            System.out.printf("%-12s [%d] Z=%.1f h=%.1f %s (%s)%n",
                f.levelName(), f.levelIndex(), f.baseZ(), f.floorHeight(),
                f.category(), f.position());
        }

        // Expand LANDED_2S
        System.out.println("\n--- LANDED_2S ---");
        floors = expandBOM("LANDED_2S", null, null);
        for (FloorInstance f : floors) {
            System.out.printf("%-12s [%d] Z=%.1f h=%.1f %s%n",
                f.levelName(), f.levelIndex(), f.baseZ(), f.floorHeight(), f.category());
        }

        close();
    }
}
