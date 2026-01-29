package com.bim.compiler.library;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.library.ComponentLibrary.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 24: Places HVAC diffusers (supply/return/exhaust) from library.
 *
 * Engineering basis:
 * - ASHRAE 62.1 for ventilation rates (ACH by room type)
 * - CFM = (Room Volume m³ × ACH) / 60 × 35.31 (convert m³/min to CFM)
 * - Diffuser sizing from Terminal empirical data:
 *   - 600×600mm: ~300 CFM (most common in Terminal)
 *   - 1200×600mm: ~500 CFM (larger public areas)
 *   - Linear slot: ~150 CFM/meter
 *
 * Supply/Return balance: 1:1 ratio (supply slightly higher for positive pressure)
 */
public class HVACPlacer {

    private final ComponentLibrary library;

    // ASHRAE 62.1 minimum air changes per hour by space type
    // Reference: ASHRAE Standard 62.1-2019 Table 6-1
    public enum VentilationRate {
        BEDROOM(0.35),           // Residential sleeping area
        LIVING(0.35),            // Residential living area
        BATHROOM(6.0),           // Exhaust-dominated (50 CFM min per IBC)
        KITCHEN(2.0),            // With local exhaust (range hood)
        OFFICE(4.0),             // General office space
        CORRIDOR(0.5),           // Circulation, minimal
        RETAIL(6.0),             // High occupancy retail
        DEPARTURE_LOUNGE(8.0),   // High occupancy airport (Terminal observed)
        GATE(6.0),               // Airport gate area
        CONCOURSE(4.0),          // Airport circulation
        DEFAULT(4.0);            // General commercial default

        private final double achMin;

        VentilationRate(double achMin) {
            this.achMin = achMin;
        }

        public double getACH() {
            return achMin;
        }

        public static VentilationRate fromRoomType(String roomType) {
            if (roomType == null) return DEFAULT;
            return switch (roomType.toUpperCase()) {
                case "BEDROOM", "MASTER", "ENSUITE" -> BEDROOM;
                case "LIVING", "LOUNGE", "FAMILY" -> LIVING;
                case "BATHROOM", "TOILET", "WC" -> BATHROOM;
                case "KITCHEN" -> KITCHEN;
                case "OFFICE", "STUDY" -> OFFICE;
                case "CORRIDOR", "HALLWAY", "PASSAGE" -> CORRIDOR;
                case "RETAIL", "SHOP" -> RETAIL;
                case "DEPARTURE_LOUNGE" -> DEPARTURE_LOUNGE;
                case "GATE" -> GATE;
                case "CONCOURSE" -> CONCOURSE;
                default -> DEFAULT;
            };
        }
    }

    // Diffuser types and CFM ratings (from Terminal empirical data)
    public enum DiffuserType {
        SUPPLY_600x600("jkrME_air-tm_diffuser_supply_600 x 600", 300, "supply"),
        RETURN_600x600("jkrME_air-tm_diffuser_return_600 x 600", 300, "return"),
        EXHAUST_600x600("jkrME_air-tm_diffuser_exhaust_600 x 600", 250, "exhaust"),
        RETURN_GRILLE_1200x600("Ceiling Mounted Return Air Grille", 500, "return"),
        SUPPLY_LINEAR_SLOT("Supply Diffuser with Plenum - Linear Slot", 200, "supply"),
        EXHAUST_GRILLE_250x250("M_Exhaust air grill_with insect net", 100, "exhaust");

        private final String libraryName;
        private final int cfmRating;
        private final String function;

        DiffuserType(String libraryName, int cfmRating, String function) {
            this.libraryName = libraryName;
            this.cfmRating = cfmRating;
            this.function = function;
        }

        public String getLibraryName() { return libraryName; }
        public int getCfmRating() { return cfmRating; }
        public String getFunction() { return function; }
    }

    // Constants
    private static final double M3_TO_CFM = 35.31;  // 1 m³/min = 35.31 CFM
    private static final double CEILING_OFFSET = 0.05;  // 50mm below ceiling

    public HVACPlacer(ComponentLibrary library) {
        this.library = library;
    }

    /**
     * Calculate required CFM for a room.
     *
     * @param roomVolume Room volume in cubic meters
     * @param roomType Room type for ACH lookup
     * @return Required CFM
     */
    public double calculateRequiredCFM(double roomVolume, String roomType) {
        VentilationRate rate = VentilationRate.fromRoomType(roomType);
        // CFM = Volume (m³) × ACH / 60 (to get m³/min) × 35.31 (to CFM)
        return roomVolume * rate.getACH() / 60.0 * M3_TO_CFM;
    }

    /**
     * Place supply diffusers for a room.
     *
     * @param roomMinX Room bounds
     * @param roomMinY Room bounds
     * @param roomMaxX Room bounds
     * @param roomMaxY Room bounds
     * @param floorZ Floor elevation
     * @param ceilingZ Ceiling elevation
     * @param roomType Room type for CFM calculation
     * @return List of diffuser instances
     */
    public List<DiffuserInstance> placeSupplyDiffusers(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, double ceilingZ,
            String roomType) throws SQLException {

        double roomWidth = roomMaxX - roomMinX;
        double roomDepth = roomMaxY - roomMinY;
        double roomHeight = ceilingZ - floorZ;
        double roomVolume = roomWidth * roomDepth * roomHeight;

        double requiredCFM = calculateRequiredCFM(roomVolume, roomType);

        // Select diffuser type based on room size
        DiffuserType diffuserType = selectSupplyDiffuser(roomWidth, roomDepth);
        int cfmPerDiffuser = diffuserType.getCfmRating();

        // Calculate number of diffusers needed
        int diffuserCount = Math.max(1, (int) Math.ceil(requiredCFM / cfmPerDiffuser));

        // Get diffuser definition from library
        ComponentDefinition diffuserDef = library.getByNameContaining(diffuserType.getLibraryName());
        if (diffuserDef == null) {
            System.out.println("[HVAC] No supply diffuser found in library: " + diffuserType.getLibraryName());
            return List.of();
        }

        // Place diffusers on grid
        return placeDiffuserGrid(
            diffuserDef, diffuserCount, diffuserType,
            roomMinX, roomMinY, roomMaxX, roomMaxY,
            ceilingZ - CEILING_OFFSET
        );
    }

    /**
     * Place return diffusers for a room.
     * Return CFM should match supply CFM for balanced airflow.
     */
    public List<DiffuserInstance> placeReturnDiffusers(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, double ceilingZ,
            String roomType,
            double supplyCFM) throws SQLException {

        double roomWidth = roomMaxX - roomMinX;
        double roomDepth = roomMaxY - roomMinY;

        // For bathrooms/kitchens, return is reduced (exhaust-dominated)
        double targetReturnCFM = supplyCFM;
        if (roomType != null) {
            String type = roomType.toUpperCase();
            if (type.equals("BATHROOM") || type.equals("KITCHEN")) {
                targetReturnCFM = supplyCFM * 0.5;  // Half for exhaust areas
            }
        }

        // Select return diffuser
        DiffuserType diffuserType = selectReturnDiffuser(roomWidth, roomDepth);

        // Calculate count based on matching supply CFM (not supply count)
        int returnCount = Math.max(1, (int) Math.ceil(targetReturnCFM / diffuserType.getCfmRating()));

        ComponentDefinition diffuserDef = library.getByNameContaining(diffuserType.getLibraryName());
        if (diffuserDef == null) {
            System.out.println("[HVAC] No return diffuser found in library: " + diffuserType.getLibraryName());
            return List.of();
        }

        // Place on grid, offset from supply (opposite corners)
        return placeDiffuserGrid(
            diffuserDef, returnCount, diffuserType,
            roomMinX, roomMinY, roomMaxX, roomMaxY,
            ceilingZ - CEILING_OFFSET
        );
    }

    /**
     * Place exhaust diffuser for bathroom/kitchen.
     * Single exhaust per room, centered.
     */
    public List<DiffuserInstance> placeExhaustDiffuser(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double ceilingZ) throws SQLException {

        ComponentDefinition exhaustDef = library.getByNameContaining("exhaust");
        if (exhaustDef == null) {
            exhaustDef = library.getByNameContaining("Exhaust");
        }
        if (exhaustDef == null) {
            System.out.println("[HVAC] No exhaust diffuser found in library");
            return List.of();
        }

        // Center on ceiling
        double centerX = (roomMinX + roomMaxX) / 2;
        double centerY = (roomMinY + roomMaxY) / 2;

        List<DiffuserInstance> diffusers = new ArrayList<>();
        diffusers.add(new DiffuserInstance(
            "exhaust_1",
            exhaustDef,
            new Point3D(centerX, centerY, ceilingZ - CEILING_OFFSET),
            0,  // rotation
            DiffuserType.EXHAUST_600x600,
            250  // CFM rating
        ));

        return diffusers;
    }

    /**
     * Complete HVAC placement for a room.
     * Returns supply + return (+ exhaust for bathrooms/kitchens).
     */
    public HVACLayout placeRoomHVAC(
            double roomMinX, double roomMinY,
            double roomMaxX, double roomMaxY,
            double floorZ, double ceilingZ,
            String roomType) throws SQLException {

        List<DiffuserInstance> supply = placeSupplyDiffusers(
            roomMinX, roomMinY, roomMaxX, roomMaxY, floorZ, ceilingZ, roomType);

        // Calculate supply CFM for balanced return
        double supplyCFM = supply.stream().mapToDouble(DiffuserInstance::cfmRating).sum();

        List<DiffuserInstance> returns = placeReturnDiffusers(
            roomMinX, roomMinY, roomMaxX, roomMaxY, floorZ, ceilingZ, roomType, supplyCFM);

        List<DiffuserInstance> exhaust = List.of();
        String type = roomType != null ? roomType.toUpperCase() : "";
        if (type.equals("BATHROOM") || type.equals("KITCHEN") || type.equals("TOILET")) {
            exhaust = placeExhaustDiffuser(roomMinX, roomMinY, roomMaxX, roomMaxY, ceilingZ);
        }

        // Calculate totals
        double roomVolume = (roomMaxX - roomMinX) * (roomMaxY - roomMinY) * (ceilingZ - floorZ);
        double requiredCFM = calculateRequiredCFM(roomVolume, roomType);
        double returnCFM = returns.stream().mapToDouble(DiffuserInstance::cfmRating).sum();

        return new HVACLayout(
            supply, returns, exhaust,
            requiredCFM, supplyCFM, returnCFM
        );
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private DiffuserType selectSupplyDiffuser(double roomWidth, double roomDepth) {
        double area = roomWidth * roomDepth;
        if (area > 50) {
            // Large room - use linear slot for better throw
            return DiffuserType.SUPPLY_LINEAR_SLOT;
        }
        // Standard 600x600 for most rooms
        return DiffuserType.SUPPLY_600x600;
    }

    private DiffuserType selectReturnDiffuser(double roomWidth, double roomDepth) {
        double area = roomWidth * roomDepth;
        if (area > 50) {
            return DiffuserType.RETURN_GRILLE_1200x600;
        }
        return DiffuserType.RETURN_600x600;
    }

    private List<DiffuserInstance> placeDiffuserGrid(
            ComponentDefinition def, int count, DiffuserType type,
            double minX, double minY, double maxX, double maxY,
            double z) {

        List<DiffuserInstance> diffusers = new ArrayList<>();

        double width = maxX - minX;
        double depth = maxY - minY;

        // Calculate grid layout
        int cols, rows;
        if (count == 1) {
            cols = rows = 1;
        } else if (count == 2) {
            if (width > depth) {
                cols = 2; rows = 1;
            } else {
                cols = 1; rows = 2;
            }
        } else {
            // Distribute evenly
            cols = (int) Math.ceil(Math.sqrt(count * width / depth));
            rows = (int) Math.ceil((double) count / cols);
        }

        double spacingX = width / (cols + 1);
        double spacingY = depth / (rows + 1);

        int idx = 0;
        for (int r = 0; r < rows && idx < count; r++) {
            for (int c = 0; c < cols && idx < count; c++) {
                double x = minX + spacingX * (c + 1);
                double y = minY + spacingY * (r + 1);

                diffusers.add(new DiffuserInstance(
                    type.getFunction() + "_" + (++idx),
                    def,
                    new Point3D(x, y, z),
                    0,  // rotation
                    type,
                    type.getCfmRating()
                ));
            }
        }

        return diffusers;
    }

    // =========================================================================
    // Output types
    // =========================================================================

    public record DiffuserInstance(
        String id,
        ComponentDefinition definition,
        Point3D position,
        double rotation,
        DiffuserType type,
        int cfmRating
    ) {
        public String geometryHash() {
            return definition != null ? definition.geometryHash() : null;
        }

        public String function() {
            return type.getFunction();
        }
    }

    public record HVACLayout(
        List<DiffuserInstance> supply,
        List<DiffuserInstance> returns,
        List<DiffuserInstance> exhaust,
        double requiredCFM,
        double supplyCFM,
        double returnCFM
    ) {
        public List<DiffuserInstance> allDiffusers() {
            List<DiffuserInstance> all = new ArrayList<>();
            all.addAll(supply);
            all.addAll(returns);
            all.addAll(exhaust);
            return all;
        }

        public int totalCount() {
            return supply.size() + returns.size() + exhaust.size();
        }

        public boolean isBalanced() {
            // Within 10% is considered balanced
            if (supplyCFM == 0) return false;
            double ratio = returnCFM / supplyCFM;
            return ratio >= 0.9 && ratio <= 1.1;
        }
    }
}
