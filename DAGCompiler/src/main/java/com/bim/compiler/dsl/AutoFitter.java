package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * AutoFitter - LEGO-style fitting using AD dimensions.
 *
 * Replaces complex constraint solving with simple lookups:
 * 1. Product knows its dimensions (from ad_product_dim)
 * 2. Space knows its dimensions (from ad_space_dim)
 * 3. Fitting = "does product fit in space?" (simple comparison)
 * 4. Placement = "where in space?" (rules from AD)
 *
 * NO LOOPS. NO CONSTRAINT SOLVING. JUST LOOKUPS.
 *
 * Usage:
 * <pre>
 * // What products does a bathroom need?
 * List<ProductPlacement> placements = AutoFitter.fitSpace("BATHROOM", 3.0, 2.5);
 *
 * // Returns: toilet at (2.5, 0.4), sink at (0, 1.2), light at (1.5, 1.25, ceiling)
 * </pre>
 */
public class AutoFitter {

    private static final String DB_PATH = "library/BOM.db";
    private static Connection connection = null;

    // =========================================================================
    // Public Records
    // =========================================================================

    /**
     * Product dimensions from AD.
     */
    public record ProductDim(
        String productId,
        String productType,
        double width,
        double depth,
        double height,
        double clearFront,
        double clearBack,
        double clearLeft,
        double clearRight,
        String fitsIn,          // JSON list of space types
        String requiresHost,    // WALL, CEILING, FLOOR, null
        Double qtyPerArea,      // For auto-quantity
        Integer qtyPerRoom,
        Double maxSpacing
    ) {
        /** Total width including clearances */
        public double totalWidth() { return width + clearLeft + clearRight; }

        /** Total depth including clearances */
        public double totalDepth() { return depth + clearFront + clearBack; }

        /** Check if this product fits in given space type */
        public boolean fitsInSpace(String spaceType) {
            if (fitsIn == null) return true;
            return fitsIn.contains(spaceType);
        }
    }

    /**
     * Space dimensions from AD.
     */
    public record SpaceDim(
        String spaceType,
        double minWidth,
        double minDepth,
        double minArea,
        double minHeight,
        String requiredProducts,    // JSON list
        String optionalProducts,    // JSON list
        String doorOnWall,
        String windowOnWall
    ) {}

    /**
     * Calculated product placement.
     */
    public record ProductPlacement(
        String productId,
        String productType,
        double x,               // Position in space
        double y,
        double z,
        String hostFace,        // NORTH, SOUTH, EAST, WEST, FLOOR, CEILING
        double rotation,        // Degrees
        String note
    ) {}

    /**
     * Fitted space result.
     */
    public record FittedSpace(
        String spaceType,
        double width,
        double depth,
        double height,
        List<ProductPlacement> placements,
        List<String> warnings
    ) {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Fit all required products into a space.
     * No loops - just apply rules from AD.
     *
     * @param spaceType Space type from AD (BEDROOM, BATHROOM, etc.)
     * @param width Available width in meters
     * @param depth Available depth in meters
     * @return Fitted space with all placements
     */
    public static FittedSpace fitSpace(String spaceType, double width, double depth) {
        return fitSpace(spaceType, width, depth, 2.7); // Default ceiling height
    }

    public static FittedSpace fitSpace(String spaceType, double width, double depth, double height) {
        List<ProductPlacement> placements = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. Get space definition
        SpaceDim space = getSpaceDim(spaceType);
        if (space == null) {
            warnings.add("Unknown space type: " + spaceType);
            return new FittedSpace(spaceType, width, depth, height, placements, warnings);
        }

        // 2. Check minimum dimensions
        if (width < space.minWidth()) {
            warnings.add(String.format("Width %.2fm < min %.2fm", width, space.minWidth()));
        }
        if (depth < space.minDepth()) {
            warnings.add(String.format("Depth %.2fm < min %.2fm", depth, space.minDepth()));
        }
        if (width * depth < space.minArea()) {
            warnings.add(String.format("Area %.2fm² < min %.2fm²", width * depth, space.minArea()));
        }

        // 3. Parse required products
        List<String> required = parseJsonList(space.requiredProducts());
        List<String> optional = parseJsonList(space.optionalProducts());

        // 4. Place each required product (deterministic rules)
        for (String productId : required) {
            ProductDim product = getProductDim(productId);
            if (product == null) {
                warnings.add("Unknown product: " + productId);
                continue;
            }

            ProductPlacement placement = placeProduct(product, spaceType, width, depth, height, placements);
            if (placement != null) {
                placements.add(placement);
            } else {
                warnings.add("Could not place: " + productId);
            }
        }

        // 5. Place optional products if space allows
        for (String productId : optional) {
            ProductDim product = getProductDim(productId);
            if (product == null) continue;

            // Check if there's room
            if (hasRoomFor(product, width, depth, placements)) {
                ProductPlacement placement = placeProduct(product, spaceType, width, depth, height, placements);
                if (placement != null) {
                    placements.add(placement);
                }
            }
        }

        // 6. Auto-place quantity-based products (outlets, lights, sprinklers)
        placeQuantityProducts(spaceType, width, depth, height, placements, warnings);

        return new FittedSpace(spaceType, width, depth, height, placements, warnings);
    }

    /**
     * Fit space with MEP profile (BUDGET/STANDARD/PREMIUM).
     *
     * Uses MEPBomAD for quantities instead of hardcoded rules.
     * DSL: ROOM "bath" type:BATHROOM mep_profile:"PREMIUM" { }
     *
     * @param spaceType Room type (BATHROOM, BEDROOM, etc.)
     * @param width Room width in meters
     * @param depth Room depth in meters
     * @param height Ceiling height in meters
     * @param mepProfile BUDGET, STANDARD, or PREMIUM
     * @return FittedSpace with products placed per profile
     */
    public static FittedSpace fitSpaceWithProfile(String spaceType, double width, double depth,
                                                   double height, String mepProfile) {
        List<ProductPlacement> placements = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        double roomArea = width * depth;

        // 1. Get MEP BOM from AD
        List<MEPBomAD.MEPPlacement> mepList = MEPBomAD.getMEPForRoom(spaceType, mepProfile, roomArea);

        if (mepList.isEmpty()) {
            warnings.add("No MEP BOM for space type: " + spaceType);
            // Fall back to original method
            return fitSpace(spaceType, width, depth, height);
        }

        // 2. Get space definition for validation
        SpaceDim space = getSpaceDim(spaceType);
        if (space != null) {
            if (width < space.minWidth()) {
                warnings.add(String.format("Width %.2fm < min %.2fm", width, space.minWidth()));
            }
            if (depth < space.minDepth()) {
                warnings.add(String.format("Depth %.2fm < min %.2fm", depth, space.minDepth()));
            }
        }

        // 3. Place fixtures (plumbing) from space definition first
        if (space != null) {
            List<String> required = parseJsonList(space.requiredProducts());
            for (String productId : required) {
                ProductDim product = getProductDim(productId);
                if (product == null) continue;
                ProductPlacement placement = placeProduct(product, spaceType, width, depth, height, placements);
                if (placement != null) {
                    placements.add(placement);
                }
            }
        }

        // 4. Place MEP products from BOM with quantities
        double conduitTotal = 0;
        for (MEPBomAD.MEPPlacement mep : mepList) {
            // Skip plumbing fixtures (handled above)
            if (isPlumbingFixture(mep.productId())) continue;

            for (int i = 0; i < mep.quantity(); i++) {
                ProductPlacement placement = placeMEPProduct(
                    mep.productId(), mep.placementRule(), mep.hostSurface(),
                    width, depth, height, i, mep.quantity(), placements
                );
                if (placement != null) {
                    placements.add(placement);
                }
            }
        }

        // 5. Calculate conduit budget vs actual
        double conduitBudget = MEPBomAD.getConduitBudget(spaceType, mepProfile);
        // Simplified: actual = budget (real routing would calculate actual runs)
        double conduitActual = conduitBudget;

        if (conduitActual > conduitBudget * 1.2) {
            warnings.add(String.format("CONDUIT BUDGET EXCEEDED: %.0fm actual > %.0fm budget",
                conduitActual, conduitBudget));
        }

        // 6. Log profile used
        warnings.add(0, String.format("[MEP_PROFILE=%s] %d products, %.0fm conduit budget",
            mepProfile, placements.size(), conduitBudget));

        return new FittedSpace(spaceType, width, depth, height, placements, warnings);
    }

    private static boolean isPlumbingFixture(String productId) {
        return productId.equals("TOILET") || productId.equals("SINK") ||
               productId.equals("FLOOR_TRAP") || productId.equals("WASHING_TAP");
    }

    /**
     * Place MEP product based on placement rule.
     */
    private static ProductPlacement placeMEPProduct(String productId, String placementRule, String hostSurface,
                                                     double width, double depth, double height,
                                                     int index, int total, List<ProductPlacement> existing) {
        double x, y, z;
        String hostFace;
        double rotation = 0;

        // Determine position based on placement rule
        switch (placementRule) {
            case "CEILING_CENTER" -> {
                x = width / 2;
                y = depth / 2;
                z = height - 0.1;  // 100mm below ceiling
                hostFace = "CEILING";
            }
            case "CEILING_GRID" -> {
                // Distribute across ceiling in grid pattern
                int cols = (int) Math.ceil(Math.sqrt(total * width / depth));
                int rows = (int) Math.ceil((double) total / cols);
                int col = index % cols;
                int row = index / cols;
                x = width * (col + 0.5) / cols;
                y = depth * (row + 0.5) / rows;
                z = height - 0.1;
                hostFace = "CEILING";
            }
            case "WALL_ENTRY" -> {
                // Near door (assume south wall)
                x = 0.15;  // 150mm from corner
                y = 0.05;  // On south wall
                z = 1.1;   // Switch height
                hostFace = "SOUTH";
            }
            case "WALL_SPACED" -> {
                // Distribute along walls
                double perimeter = 2 * (width + depth);
                double spacing = perimeter / (total + 1);
                double pos = spacing * (index + 1);

                if (pos < width) {
                    // South wall
                    x = pos; y = 0.05; hostFace = "SOUTH";
                } else if (pos < width + depth) {
                    // East wall
                    x = width - 0.05; y = pos - width; hostFace = "EAST";
                } else if (pos < 2 * width + depth) {
                    // North wall
                    x = width - (pos - width - depth); y = depth - 0.05; hostFace = "NORTH";
                } else {
                    // West wall
                    x = 0.05; y = depth - (pos - 2 * width - depth); hostFace = "WEST";
                }
                z = 0.3;  // Outlet height
            }
            case "WALL_HIGH" -> {
                // High on wall (aircon)
                x = width / 2;
                y = 0.05;
                z = height - 0.4;
                hostFace = "SOUTH";
            }
            case "WALL_SINK", "COUNTER_BACK", "COUNTER_SINK" -> {
                // Near sink/counter (assume west wall)
                x = 0.05;
                y = depth * 0.3;
                z = productId.contains("20A") ? 1.0 : 0.3;
                hostFace = "WEST";
            }
            case "WALL_EXIT" -> {
                // Near exits
                x = index == 0 ? 0.15 : width - 0.15;
                y = depth / 2;
                z = 2.1;
                hostFace = index == 0 ? "WEST" : "EAST";
            }
            case "WALL_COOKER" -> {
                // Above cooker (north wall typically)
                x = width / 2;
                y = depth - 0.1;
                z = height - 0.3;
                hostFace = "NORTH";
            }
            default -> {
                // Default: center of room
                x = width / 2;
                y = depth / 2;
                z = hostSurface.equals("CEILING") ? height - 0.1 : 0.3;
                hostFace = hostSurface.equals("CEILING") ? "CEILING" : "SOUTH";
            }
        }

        return new ProductPlacement(productId, getProductType(productId),
            x, y, z, hostFace, rotation, placementRule);
    }

    private static String getProductType(String productId) {
        if (productId.contains("OUTLET") || productId.contains("SWITCH") ||
            productId.contains("LIGHT") || productId.contains("AIRCON") ||
            productId.contains("DATA")) {
            return "ELECTRICAL";
        }
        if (productId.contains("SPRINKLER") || productId.contains("EMERGENCY")) {
            return "SPRINKLER";
        }
        if (productId.contains("EXHAUST")) {
            return "MECHANICAL";
        }
        return "FIXTURE";
    }

    /**
     * Get product dimensions from AD.
     */
    public static ProductDim getProductDim(String productId) {
        try {
            Connection conn = getConnection();
            if (conn == null) return null;

            String sql = """
                SELECT product_id, product_type, width, depth, height,
                       clear_front, clear_back, clear_left, clear_right,
                       fits_in, requires_host, qty_per_area, qty_per_room, max_spacing
                FROM ad_product_dim WHERE product_id = ? AND is_active = 1
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, productId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return new ProductDim(
                        rs.getString("product_id"),
                        rs.getString("product_type"),
                        rs.getDouble("width"),
                        rs.getDouble("depth"),
                        rs.getDouble("height"),
                        rs.getDouble("clear_front"),
                        rs.getDouble("clear_back"),
                        rs.getDouble("clear_left"),
                        rs.getDouble("clear_right"),
                        rs.getString("fits_in"),
                        rs.getString("requires_host"),
                        rs.getObject("qty_per_area") != null ? rs.getDouble("qty_per_area") : null,
                        rs.getObject("qty_per_room") != null ? rs.getInt("qty_per_room") : null,
                        rs.getObject("max_spacing") != null ? rs.getDouble("max_spacing") : null
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[AutoFitter] getProductDim error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get space dimensions from AD.
     */
    public static SpaceDim getSpaceDim(String spaceType) {
        try {
            Connection conn = getConnection();
            if (conn == null) return null;

            String sql = """
                SELECT space_type, min_width, min_depth, min_area, min_height,
                       required_products, optional_products, door_on_wall, window_on_wall
                FROM ad_space_dim WHERE space_type = ? AND is_active = 1
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, spaceType.toUpperCase());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return new SpaceDim(
                        rs.getString("space_type"),
                        rs.getDouble("min_width"),
                        rs.getDouble("min_depth"),
                        rs.getDouble("min_area"),
                        rs.getDouble("min_height"),
                        rs.getString("required_products"),
                        rs.getString("optional_products"),
                        rs.getString("door_on_wall"),
                        rs.getString("window_on_wall")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[AutoFitter] getSpaceDim error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Calculate how many of a product fit in a space.
     */
    public static int calculateQuantity(String productId, double area) {
        ProductDim product = getProductDim(productId);
        if (product == null) return 0;

        if (product.qtyPerRoom() != null) {
            return product.qtyPerRoom();
        }

        if (product.qtyPerArea() != null && product.qtyPerArea() > 0) {
            return Math.max(1, (int) Math.ceil(area * product.qtyPerArea()));
        }

        if (product.maxSpacing() != null && product.maxSpacing() > 0) {
            // Calculate based on spacing requirement
            double perimeter = 2 * (Math.sqrt(area) + Math.sqrt(area)); // Approximate
            return Math.max(1, (int) Math.ceil(perimeter / product.maxSpacing()));
        }

        return 1;
    }

    // =========================================================================
    // Private Placement Logic (Rule-based, no loops)
    // =========================================================================

    /**
     * Place a product in a space using deterministic rules.
     */
    private static ProductPlacement placeProduct(ProductDim product, String spaceType,
            double width, double depth, double height, List<ProductPlacement> existing) {

        String host = product.requiresHost();

        if ("CEILING".equals(host)) {
            // Center on ceiling
            return new ProductPlacement(
                product.productId(), product.productType(),
                width / 2, depth / 2, height,
                "CEILING", 0, "centered"
            );
        }

        if ("FLOOR".equals(host)) {
            // Floor-mounted: back wall, centered or offset
            return placeOnFloor(product, width, depth, existing);
        }

        if ("WALL".equals(host)) {
            // Wall-mounted: find appropriate wall
            return placeOnWall(product, spaceType, width, depth, existing);
        }

        // Freestanding: center of room
        return new ProductPlacement(
            product.productId(), product.productType(),
            width / 2, depth / 2, 0,
            "FLOOR", 0, "freestanding"
        );
    }

    private static ProductPlacement placeOnFloor(ProductDim product, double width, double depth,
            List<ProductPlacement> existing) {
        // Toilets go at back wall, offset from corner
        if (product.productId().contains("TOILET")) {
            double x = width - product.clearRight() - product.width() / 2;
            double y = product.depth() / 2;
            return new ProductPlacement(
                product.productId(), product.productType(),
                x, y, 0, "SOUTH", 0, "back wall"
            );
        }

        // Showers go in corner
        if (product.productId().contains("SHOWER")) {
            return new ProductPlacement(
                product.productId(), product.productType(),
                product.width() / 2, product.depth() / 2, 0,
                "CORNER_SW", 0, "corner"
            );
        }

        // Default: back wall center
        return new ProductPlacement(
            product.productId(), product.productType(),
            width / 2, product.depth() / 2, 0,
            "SOUTH", 0, "floor center"
        );
    }

    private static ProductPlacement placeOnWall(ProductDim product, String spaceType,
            double width, double depth, List<ProductPlacement> existing) {
        // Sinks go on wall opposite door
        if (product.productId().contains("SINK")) {
            double x = width / 2;
            double y = depth - product.depth() / 2;  // North wall
            double z = 0.85;  // Counter height
            return new ProductPlacement(
                product.productId(), product.productType(),
                x, y, z, "NORTH", 0, "opposite door"
            );
        }

        // Doors go on shortest wall by default
        if (product.productType().equals("DOOR")) {
            // Entry wall (assume south)
            return new ProductPlacement(
                product.productId(), product.productType(),
                width / 2, 0, 0, "SOUTH", 0, "entry"
            );
        }

        // Windows go on exterior wall
        if (product.productType().equals("WINDOW")) {
            return new ProductPlacement(
                product.productId(), product.productType(),
                width / 2, depth, 0.9,  // Sill height
                "NORTH", 0, "exterior"
            );
        }

        // Switches near door
        if (product.productId().contains("SWITCH")) {
            return new ProductPlacement(
                product.productId(), product.productType(),
                0.15, 0.15, 1.2, "SOUTH", 0, "near door"
            );
        }

        // Outlets spaced along walls
        if (product.productId().contains("OUTLET")) {
            int existingOutlets = (int) existing.stream()
                .filter(p -> p.productId().contains("OUTLET")).count();
            double spacing = width / 2;
            double x = 0.3 + existingOutlets * spacing;
            return new ProductPlacement(
                product.productId(), product.productType(),
                Math.min(x, width - 0.3), depth / 2, 0.3,
                existingOutlets % 2 == 0 ? "WEST" : "EAST", 0, "wall outlet"
            );
        }

        // Default: center of north wall
        return new ProductPlacement(
            product.productId(), product.productType(),
            width / 2, depth, 1.5, "NORTH", 0, "wall mount"
        );
    }

    private static void placeQuantityProducts(String spaceType, double width, double depth, double height,
            List<ProductPlacement> placements, List<String> warnings) {
        double area = width * depth;

        // Lights (qty_per_area based)
        int lightCount = calculateQuantity("ELEC_LIGHT", area);
        ProductDim light = getProductDim("ELEC_LIGHT");
        if (light != null && lightCount > 0) {
            // Grid layout for multiple lights
            int cols = (int) Math.ceil(Math.sqrt(lightCount * width / depth));
            int rows = (int) Math.ceil((double) lightCount / cols);
            double dx = width / (cols + 1);
            double dy = depth / (rows + 1);

            int placed = 0;
            for (int r = 0; r < rows && placed < lightCount; r++) {
                for (int c = 0; c < cols && placed < lightCount; c++) {
                    placements.add(new ProductPlacement(
                        "ELEC_LIGHT", "ELECTRICAL",
                        dx * (c + 1), dy * (r + 1), height,
                        "CEILING", 0, "light grid"
                    ));
                    placed++;
                }
            }
        }

        // Sprinklers (if applicable)
        ProductDim sprinkler = getProductDim("FP_SPRINKLER");
        if (sprinkler != null && sprinkler.fitsInSpace(spaceType)) {
            int sprinklerCount = calculateQuantity("FP_SPRINKLER", area);
            if (sprinklerCount > 0) {
                // Simple center placement for small rooms
                placements.add(new ProductPlacement(
                    "FP_SPRINKLER", "FP",
                    width / 2, depth / 2, height,
                    "CEILING", 0, "sprinkler"
                ));
            }
        }
    }

    private static boolean hasRoomFor(ProductDim product, double width, double depth,
            List<ProductPlacement> existing) {
        // Simple check: is there enough total area?
        double usedArea = existing.stream()
            .mapToDouble(p -> {
                ProductDim pd = getProductDim(p.productId());
                return pd != null ? pd.totalWidth() * pd.totalDepth() : 0;
            })
            .sum();

        double available = width * depth - usedArea;
        double needed = product.totalWidth() * product.totalDepth();
        return available >= needed;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static synchronized Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) return connection;

        java.io.File dbFile = new java.io.File(DB_PATH);
        if (!dbFile.exists()) return null;

        connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        return connection;
    }

    private static List<String> parseJsonList(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return result;

        json = json.replace("[", "").replace("]", "").replace("\"", "");
        for (String item : json.split(",")) {
            String s = item.trim();
            if (!s.isEmpty()) result.add(s);
        }
        return result;
    }

    public static void close() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) {}
            connection = null;
        }
    }

    // =========================================================================
    // Test
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=== AutoFitter Test ===\n");

        // Fit a bathroom
        System.out.println("--- BATHROOM 3.0m x 2.5m ---");
        FittedSpace bathroom = fitSpace("BATHROOM", 3.0, 2.5);

        for (ProductPlacement p : bathroom.placements()) {
            System.out.printf("  %s at (%.2f, %.2f, %.2f) on %s%n",
                p.productId(), p.x(), p.y(), p.z(), p.hostFace());
        }
        if (!bathroom.warnings().isEmpty()) {
            System.out.println("  Warnings: " + bathroom.warnings());
        }

        // Fit a bedroom
        System.out.println("\n--- BEDROOM 4.0m x 3.5m ---");
        FittedSpace bedroom = fitSpace("BEDROOM", 4.0, 3.5);

        for (ProductPlacement p : bedroom.placements()) {
            System.out.printf("  %s at (%.2f, %.2f, %.2f) on %s%n",
                p.productId(), p.x(), p.y(), p.z(), p.hostFace());
        }

        // Fit a small bathroom (should warn)
        System.out.println("\n--- BATHROOM 1.2m x 1.5m (small) ---");
        FittedSpace small = fitSpace("BATHROOM", 1.2, 1.5);
        System.out.println("  Warnings: " + small.warnings());

        close();
    }
}
