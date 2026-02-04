package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Phase 61: Room Sizing Resolution - same pattern as BOM resolution.
 *
 * Given user intent (qty, area, type), resolves optimal room dimensions
 * within available bounds, respecting code constraints.
 *
 * Constraints from ad_room_sizing:
 * - min_width: Code minimum (egress, accessibility)
 * - max_aspect: Maximum length:width ratio (no bowling alleys)
 * - preferred_aspect: Target aspect (squarish preferred)
 * - min_area: Code minimum floor area
 *
 * Resolution algorithm:
 * 1. Calculate dimensions for target area with preferred aspect
 * 2. Check against min width, adjust if needed
 * 3. Check aspect ratio, adjust if needed
 * 4. Fit into available bounds
 * 5. Return resolved dimensions or feedback if impossible
 */
public class RoomSizingResolver {

    private static final String DB_PATH = "database/authority_data.db";

    /**
     * Room sizing rule from AD table.
     */
    public record SizingRule(
        String spaceType,
        Double minAreaM2,
        Double maxAreaM2,
        Double minWidthM,
        Double minDepthM,
        Double maxAspectRatio,
        Double preferredAspect,
        Double areaPerOccupantM2,
        Double clearanceM,
        String codeId,
        String clause,
        String notes
    ) {}

    /**
     * Resolved room dimensions.
     */
    public record ResolvedRoom(
        String name,
        String spaceType,
        double width,
        double depth,
        double area,
        double aspectRatio,
        int occupancy,
        boolean meetsCode,
        List<String> warnings,
        String calculation
    ) {
        public String toAnnotation() {
            String warn = warnings.isEmpty() ? "" : " WARNING: " + String.join("; ", warnings);
            return String.format("RESOLVED: %.2fm × %.2fm = %.1fm² (aspect %.2f:1, occ %d)%s",
                width, depth, area, aspectRatio, occupancy, warn);
        }
    }

    /**
     * Layout result for multiple rooms.
     */
    public record LayoutResult(
        List<ResolvedRoom> rooms,
        int requestedQty,
        int resolvedQty,
        double totalArea,
        String feedback
    ) {
        public boolean isComplete() {
            return resolvedQty >= requestedQty;
        }
    }

    private final Connection conn;
    private final Map<String, SizingRule> ruleCache = new HashMap<>();

    public RoomSizingResolver() throws SQLException {
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        loadRules();
    }

    private void loadRules() throws SQLException {
        String sql = """
            SELECT space_type, min_area_m2, max_area_m2, min_width_m, min_depth_m,
                   max_aspect_ratio, preferred_aspect, area_per_occupant_m2, clearance_m,
                   code_id, clause, notes
            FROM ad_room_sizing
            """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                SizingRule rule = new SizingRule(
                    rs.getString("space_type"),
                    getDouble(rs, "min_area_m2"),
                    getDouble(rs, "max_area_m2"),
                    getDouble(rs, "min_width_m"),
                    getDouble(rs, "min_depth_m"),
                    getDouble(rs, "max_aspect_ratio"),
                    getDouble(rs, "preferred_aspect"),
                    getDouble(rs, "area_per_occupant_m2"),
                    getDouble(rs, "clearance_m"),
                    rs.getString("code_id"),
                    rs.getString("clause"),
                    rs.getString("notes")
                );
                ruleCache.put(rule.spaceType().toUpperCase(), rule);
            }
        }

        System.out.printf("[RoomSizingResolver] Loaded %d sizing rules%n", ruleCache.size());
    }

    private Double getDouble(ResultSet rs, String col) throws SQLException {
        double val = rs.getDouble(col);
        return rs.wasNull() ? null : val;
    }

    /**
     * Get sizing rule for a space type.
     */
    public SizingRule getRule(String spaceType) {
        return ruleCache.get(spaceType.toUpperCase());
    }

    /**
     * Input mode for room sizing - either/or pattern (like Libero Mfg BOM).
     */
    public enum InputMode {
        BY_AREA,       // User specifies area, system calculates dimensions
        BY_DIMENSIONS  // User specifies width×depth, system validates
    }

    /**
     * Room sizing input - supports either area OR dimensions.
     */
    public record RoomInput(
        String name,
        String spaceType,
        InputMode mode,
        Double area,       // For BY_AREA mode
        Double width,      // For BY_DIMENSIONS mode
        Double depth       // For BY_DIMENSIONS mode
    ) {
        public static RoomInput byArea(String name, String spaceType, double area) {
            return new RoomInput(name, spaceType, InputMode.BY_AREA, area, null, null);
        }

        public static RoomInput byDimensions(String name, String spaceType, double width, double depth) {
            return new RoomInput(name, spaceType, InputMode.BY_DIMENSIONS, null, width, depth);
        }
    }

    /**
     * Resolve room from input (either area OR dimensions).
     */
    public ResolvedRoom resolveRoom(RoomInput input) {
        if (input.mode() == InputMode.BY_DIMENSIONS) {
            return resolveRoomByDimensions(input.spaceType(), input.width(), input.depth(), input.name());
        } else {
            return resolveRoom(input.spaceType(), input.area(), input.name());
        }
    }

    /**
     * Resolve room given explicit dimensions - validate against rules.
     */
    public ResolvedRoom resolveRoomByDimensions(String spaceType, double width, double depth, String roomName) {
        SizingRule rule = getRule(spaceType);
        List<String> warnings = new ArrayList<>();

        double area = width * depth;
        double aspectRatio = Math.max(depth / width, width / depth);

        // Default constraints
        double minWidth = 2.0;
        double maxAspect = 2.0;
        double areaPerOcc = 2.0;

        if (rule != null) {
            minWidth = rule.minWidthM() != null ? rule.minWidthM() : minWidth;
            maxAspect = rule.maxAspectRatio() != null ? rule.maxAspectRatio() : maxAspect;
            areaPerOcc = rule.areaPerOccupantM2() != null ? rule.areaPerOccupantM2() : areaPerOcc;

            // Validate against code
            if (rule.minAreaM2() != null && area < rule.minAreaM2()) {
                warnings.add(String.format("Area %.1fm² < code min %.1fm² (%s %s)",
                    area, rule.minAreaM2(), rule.codeId(), rule.clause()));
            }

            if (width < minWidth) {
                warnings.add(String.format("Width %.1fm < code min %.1fm (%s %s)",
                    width, minWidth, rule.codeId(), rule.clause()));
            }

            if (rule.minDepthM() != null && depth < rule.minDepthM()) {
                warnings.add(String.format("Depth %.1fm < code min %.1fm",
                    depth, rule.minDepthM()));
            }

            if (aspectRatio > maxAspect) {
                warnings.add(String.format("Aspect %.1f:1 > max %.1f:1 (too elongated)",
                    aspectRatio, maxAspect));
            }
        }

        int occupancy = (int) Math.floor(area / areaPerOcc);
        boolean meetsCode = warnings.isEmpty();
        String calculation = String.format("USER: %.1fm × %.1fm = %.1fm²", width, depth, area);

        return new ResolvedRoom(
            roomName, spaceType, width, depth, area, depth / width,
            occupancy, meetsCode, warnings, calculation
        );
    }

    /**
     * Calculate optimal dimensions for a room given target area.
     *
     * @param spaceType Room type
     * @param targetAreaM2 Target area in square meters
     * @param roomName Room name for output
     * @return Resolved room with optimal dimensions
     */
    public ResolvedRoom resolveRoom(String spaceType, double targetAreaM2, String roomName) {
        SizingRule rule = getRule(spaceType);
        List<String> warnings = new ArrayList<>();

        // Default constraints if no rule
        double minWidth = 2.0;
        double maxAspect = 2.0;
        double prefAspect = 1.2;
        double areaPerOcc = 2.0;

        if (rule != null) {
            minWidth = rule.minWidthM() != null ? rule.minWidthM() : minWidth;
            maxAspect = rule.maxAspectRatio() != null ? rule.maxAspectRatio() : maxAspect;
            prefAspect = rule.preferredAspect() != null ? rule.preferredAspect() : prefAspect;
            areaPerOcc = rule.areaPerOccupantM2() != null ? rule.areaPerOccupantM2() : areaPerOcc;

            // Check minimum area
            if (rule.minAreaM2() != null && targetAreaM2 < rule.minAreaM2()) {
                warnings.add(String.format("Area %.1fm² < code min %.1fm² (%s %s)",
                    targetAreaM2, rule.minAreaM2(), rule.codeId(), rule.clause()));
            }
        }

        // Calculate dimensions with preferred aspect ratio
        // area = width × depth, aspect = depth / width
        // So: width = sqrt(area / aspect), depth = width × aspect
        double width = Math.sqrt(targetAreaM2 / prefAspect);
        double depth = width * prefAspect;

        // Enforce minimum width
        if (width < minWidth) {
            width = minWidth;
            depth = targetAreaM2 / width;
            warnings.add(String.format("Width constrained to min %.1fm", minWidth));
        }

        // Check aspect ratio
        double actualAspect = Math.max(depth / width, width / depth);
        if (actualAspect > maxAspect) {
            // Recalculate to fit max aspect
            // If depth/width > maxAspect: reduce depth, increase width
            if (depth > width) {
                depth = width * maxAspect;
            } else {
                width = depth * maxAspect;
            }
            double newArea = width * depth;
            warnings.add(String.format("Aspect capped at %.1f:1, area reduced to %.1fm²",
                maxAspect, newArea));
            targetAreaM2 = newArea;
        }

        // Recalculate actual values
        double actualArea = width * depth;
        actualAspect = depth / width;
        int occupancy = (int) Math.floor(actualArea / areaPerOcc);

        boolean meetsCode = warnings.isEmpty();
        String calculation = String.format("sqrt(%.1f/%.1f)=%.2f, ×%.1f=%.2f",
            targetAreaM2, prefAspect, width, prefAspect, depth);

        return new ResolvedRoom(
            roomName, spaceType, width, depth, actualArea, actualAspect,
            occupancy, meetsCode, warnings, calculation
        );
    }

    /**
     * Resolve layout for multiple rooms of same type within available bounds.
     *
     * @param spaceType Room type
     * @param requestedQty Number of rooms requested
     * @param targetAreaEach Target area per room
     * @param availableWidth Available width in building
     * @param availableDepth Available depth in building
     * @param namePrefix Prefix for room names
     * @return Layout result with resolved rooms and feedback
     */
    public LayoutResult resolveLayout(String spaceType, int requestedQty, double targetAreaEach,
                                       double availableWidth, double availableDepth,
                                       String namePrefix) {

        List<ResolvedRoom> rooms = new ArrayList<>();
        double totalAvailable = availableWidth * availableDepth;
        double totalRequested = requestedQty * targetAreaEach;

        // First, resolve one room to get dimensions
        ResolvedRoom template = resolveRoom(spaceType, targetAreaEach, "template");
        double roomWidth = template.width();
        double roomDepth = template.depth();

        // Calculate how many fit in each direction
        // Try both orientations
        int fitAcross1 = (int) Math.floor(availableWidth / roomWidth);
        int fitDeep1 = (int) Math.floor(availableDepth / roomDepth);
        int total1 = fitAcross1 * fitDeep1;

        int fitAcross2 = (int) Math.floor(availableWidth / roomDepth);
        int fitDeep2 = (int) Math.floor(availableDepth / roomWidth);
        int total2 = fitAcross2 * fitDeep2;

        // Choose better orientation
        int maxFit = Math.max(total1, total2);
        int actualQty = Math.min(maxFit, requestedQty);

        // Generate rooms
        for (int i = 0; i < actualQty; i++) {
            String name = namePrefix + "_" + (i + 1);
            rooms.add(resolveRoom(spaceType, targetAreaEach, name));
        }

        // Generate feedback
        String feedback;
        if (actualQty >= requestedQty) {
            feedback = String.format("All %d rooms fit (%.1fm² of %.1fm² used)",
                requestedQty, actualQty * template.area(), totalAvailable);
        } else if (actualQty > 0) {
            double reducedArea = totalAvailable / requestedQty * 0.85;  // 85% efficiency
            feedback = String.format(
                "Only %d of %d rooms fit at %.1fm² each. " +
                "Suggest: reduce to %.1fm²/room for all %d, or accept %d rooms.",
                actualQty, requestedQty, targetAreaEach,
                reducedArea, requestedQty, actualQty);
        } else {
            feedback = String.format(
                "Cannot fit any %.1fm² %s rooms in %.1fm × %.1fm space. " +
                "Min dimensions: %.1fm × %.1fm",
                targetAreaEach, spaceType, availableWidth, availableDepth,
                template.width(), template.depth());
        }

        double totalArea = rooms.stream().mapToDouble(ResolvedRoom::area).sum();
        return new LayoutResult(rooms, requestedQty, actualQty, totalArea, feedback);
    }

    /**
     * Generate DSL annotation for a resolved room.
     */
    public String toDSLAnnotation(ResolvedRoom room) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s \"%s\" {\n", room.spaceType(), room.name()));
        sb.append(String.format("    // %s\n", room.toAnnotation()));
        sb.append(String.format("    dimensions: %.2fm × %.2fm\n", room.width(), room.depth()));
        sb.append(String.format("    area: %.1fm²\n", room.area()));
        sb.append(String.format("    occupancy: %d\n", room.occupancy()));
        if (!room.warnings().isEmpty()) {
            for (String w : room.warnings()) {
                sb.append(String.format("    // WARNING: %s\n", w));
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    public void close() throws SQLException {
        if (conn != null) conn.close();
    }

    /**
     * Test room sizing resolution.
     */
    public static void main(String[] args) throws SQLException {
        RoomSizingResolver resolver = new RoomSizingResolver();

        System.out.println("=== Either/Or Input Mode (like Libero Mfg BOM) ===\n");

        // Mode A: BY_AREA - user specifies area, system calculates dimensions
        System.out.println("--- BY_AREA: User specifies area ---");
        RoomInput byArea = RoomInput.byArea("class_1", "CLASSROOM", 56.0);
        ResolvedRoom r1 = resolver.resolveRoom(byArea);
        System.out.println(resolver.toDSLAnnotation(r1));

        // Mode B: BY_DIMENSIONS - user specifies width×depth, system validates
        System.out.println("--- BY_DIMENSIONS: User specifies 7m × 8m ---");
        RoomInput byDim = RoomInput.byDimensions("class_2", "CLASSROOM", 7.0, 8.0);
        ResolvedRoom r2 = resolver.resolveRoom(byDim);
        System.out.println(resolver.toDSLAnnotation(r2));

        // Invalid dimensions (too narrow)
        System.out.println("--- BY_DIMENSIONS: Invalid 3m × 15m (too narrow) ---");
        RoomInput invalid = RoomInput.byDimensions("bad_class", "CLASSROOM", 3.0, 15.0);
        ResolvedRoom r3 = resolver.resolveRoom(invalid);
        System.out.println(resolver.toDSLAnnotation(r3));

        System.out.println("\n=== Standard Resolution ===\n");

        // Classroom 56m²
        ResolvedRoom classroom = resolver.resolveRoom("CLASSROOM", 56.0, "class_1");
        System.out.println(resolver.toDSLAnnotation(classroom));

        // Small bathroom
        ResolvedRoom bathroom = resolver.resolveRoom("BATHROOM", 4.0, "toilet_1");
        System.out.println(resolver.toDSLAnnotation(bathroom));

        // Large canteen
        ResolvedRoom canteen = resolver.resolveRoom("CANTEEN", 120.0, "main_canteen");
        System.out.println(resolver.toDSLAnnotation(canteen));

        System.out.println("\n=== Layout Resolution (4 classrooms in 30m × 20m) ===\n");

        LayoutResult layout = resolver.resolveLayout(
            "CLASSROOM", 4, 56.0,
            30.0, 20.0,
            "class"
        );

        System.out.printf("Requested: %d, Resolved: %d\n", layout.requestedQty(), layout.resolvedQty());
        System.out.printf("Feedback: %s\n\n", layout.feedback());

        for (ResolvedRoom room : layout.rooms()) {
            System.out.printf("  %s: %.1fm × %.1fm = %.1fm²\n",
                room.name(), room.width(), room.depth(), room.area());
        }

        System.out.println("\n=== Constrained Layout (4 classrooms in 15m × 12m) ===\n");

        LayoutResult constrained = resolver.resolveLayout(
            "CLASSROOM", 4, 56.0,
            15.0, 12.0,  // Too small for 4 classrooms
            "class"
        );

        System.out.printf("Requested: %d, Resolved: %d\n",
            constrained.requestedQty(), constrained.resolvedQty());
        System.out.printf("Feedback: %s\n", constrained.feedback());

        resolver.close();
    }
}
