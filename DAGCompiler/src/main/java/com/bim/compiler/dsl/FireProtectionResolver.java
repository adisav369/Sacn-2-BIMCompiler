package com.bim.compiler.dsl;

import com.bim.compiler.contract.IFireProtected;
import com.bim.compiler.contract.IFireProtected.FireProtectionElement;
import com.bim.compiler.contract.IFireProtected.FireProtectionValidation;

import java.util.*;

/**
 * FireProtectionResolver - Auto-fulfills FP requirements from AD triggers.
 *
 * ENFORCEMENT PATTERN:
 * 1. Query AD triggers for building parameters
 * 2. If trigger fires → generate sprinkler placements
 * 3. Return SprinklerSpecs for BuildingCompiler to include
 *
 * MATHS-DRIVEN:
 * - Coverage: ceil(floor_area / 18.6) = head_count
 * - Spacing: grid = sqrt(coverage) ≈ 4.3m (< 4.6m max)
 * - Wall offset: 2.3m from walls (NFPA 13)
 *
 * Usage:
 * <pre>
 *   FireProtectionResolver resolver = new FireProtectionResolver(dbPath);
 *   List<SprinklerSpec> sprinklers = resolver.resolveSprinklers(
 *       storeyName, rooms, buildingHeight, totalFloorArea, "R", "MALAYSIA"
 *   );
 * </pre>
 */
public class FireProtectionResolver {

    private static final String DB_PATH = System.getProperty("bom.db");

    private final FireProtectionAD fpAD;
    private final ADSession.FireProtectionADFacade sessionFP;  // Phase 57B
    private final String jurisdiction;

    // NFPA 13 Light Hazard (from ad_fp_coverage)
    private static final double MAX_COVERAGE_M2 = 18.6;
    private static final double MAX_SPACING_M = 4.6;
    private static final double WALL_OFFSET_M = 2.3;

    public FireProtectionResolver(String dbPath, String jurisdiction) {
        // Phase 57B: Use ADSession if available (shared connection + cache)
        ADSession session = BuildingCompiler.getSession();
        if (session != null) {
            this.sessionFP = session.fireProtection();
            this.fpAD = null;  // Don't create separate connection
        } else {
            this.sessionFP = null;
            this.fpAD = new FireProtectionAD(dbPath);
        }
        this.jurisdiction = jurisdiction;
    }

    public FireProtectionResolver(String jurisdiction) {
        this(DB_PATH, jurisdiction);
    }

    /**
     * Phase 57B: Check if sprinklers required via session or direct AD.
     */
    private boolean isSprinklerRequired(int storeys, double heightM, double floorAreaM2, String occupancyGroup) {
        if (sessionFP != null) {
            return sessionFP.isSprinklerRequired(storeys, heightM, floorAreaM2, occupancyGroup, jurisdiction);
        }
        return fpAD.isSprinklerRequired(storeys, heightM, floorAreaM2, occupancyGroup, jurisdiction);
    }

    /**
     * Phase 57B: Get sprinkler coverage via session or direct MEPAD.
     */
    private MEPAD.FPCoverage getSprinklerCoverage() {
        ADSession session = BuildingCompiler.getSession();
        if (session != null) {
            return session.mep().getSprinklerCoverage("LIGHT");
        }
        return MEPAD.getSprinklerCoverage("LIGHT");
    }

    // =========================================================================
    // Main Resolution Method
    // =========================================================================

    /**
     * Resolve sprinklers for a storey based on AD triggers.
     *
     * CONTRACT ENFORCEMENT:
     * - If trigger fires, sprinklers are AUTOMATICALLY generated
     * - User doesn't need to specify in DSL
     * - AD metadata drives the behavior
     *
     * @param storeyName Name of storey
     * @param rooms List of rooms with bounds
     * @param buildingHeight Total building height in meters
     * @param totalFloorArea Total floor area in m²
     * @param occupancyGroup IBC occupancy (R, A, B, E, etc.)
     * @return List of SprinklerSpec to add to storey
     */
    public List<BuildingSpecs.SprinklerSpec> resolveSprinklers(
            String storeyName,
            List<RoomBounds> rooms,
            double buildingHeight,
            double totalFloorArea,
            String occupancyGroup) {

        // Step 1: Check if sprinklers required (AD trigger)
        // Phase 57B: Uses session when available
        boolean required = isSprinklerRequired(
            calculateStoreys(buildingHeight),
            buildingHeight,
            totalFloorArea,
            occupancyGroup
        );

        if (!required) {
            return List.of();  // No sprinklers needed
        }

        // Step 2: Get coverage rules from MEPAD
        // Phase 57B: Use session's MEP facade when available (cached)
        MEPAD.FPCoverage coverage = getSprinklerCoverage();
        double coveragePerHead = coverage != null ? coverage.maxCoverageM2() : MAX_COVERAGE_M2;
        double maxSpacing = coverage != null ? coverage.maxSpacingM() : MAX_SPACING_M;

        // Step 3: Generate sprinklers for each room (MATHS)
        List<BuildingSpecs.SprinklerSpec> sprinklers = new ArrayList<>();

        for (RoomBounds room : rooms) {
            List<BuildingSpecs.SprinklerSpec> roomSprinklers =
                generateRoomSprinklers(room, coveragePerHead, maxSpacing, storeyName);
            sprinklers.addAll(roomSprinklers);
        }

        return sprinklers;
    }

    /**
     * Generate sprinklers for a single room using MATHS.
     *
     * ALGORITHM:
     * 1. Calculate grid spacing = min(max_spacing, sqrt(coverage))
     * 2. Calculate grid count: ceil(width/spacing) × ceil(depth/spacing)
     * 3. Center grid in room with wall offsets
     */
    private List<BuildingSpecs.SprinklerSpec> generateRoomSprinklers(
            RoomBounds room,
            double coveragePerHead,
            double maxSpacing,
            String storeyName) {

        List<BuildingSpecs.SprinklerSpec> result = new ArrayList<>();

        double roomWidth = room.maxX - room.minX;
        double roomDepth = room.maxY - room.minY;
        double roomArea = roomWidth * roomDepth;

        // Skip very small rooms (closets, etc.)
        if (roomArea < 4.0) {
            return result;
        }

        // MATHS: Calculate optimal grid spacing
        double optimalSpacing = Math.sqrt(coveragePerHead);
        double spacing = Math.min(maxSpacing, optimalSpacing);

        // MATHS: Grid count with wall offsets
        double usableWidth = roomWidth - 2 * WALL_OFFSET_M;
        double usableDepth = roomDepth - 2 * WALL_OFFSET_M;

        if (usableWidth < 0 || usableDepth < 0) {
            // Room too small for wall offsets - place single head at center
            result.add(new BuildingSpecs.SprinklerSpec(
                room.name + "_sprinkler_1",
                room.name,
                room.minX + roomWidth / 2,
                room.minY + roomDepth / 2,
                room.ceilingZ,
                "pendant",
                spacing
            ));
            return result;
        }

        int numX = Math.max(1, (int) Math.ceil(usableWidth / spacing));
        int numY = Math.max(1, (int) Math.ceil(usableDepth / spacing));

        // MATHS: Center the grid
        double startX = room.minX + WALL_OFFSET_M + (usableWidth - (numX - 1) * spacing) / 2;
        double startY = room.minY + WALL_OFFSET_M + (usableDepth - (numY - 1) * spacing) / 2;

        // Generate grid
        int index = 0;
        for (int ix = 0; ix < numX; ix++) {
            for (int iy = 0; iy < numY; iy++) {
                double x = startX + ix * spacing;
                double y = startY + iy * spacing;

                result.add(new BuildingSpecs.SprinklerSpec(
                    room.name + "_sprinkler_" + (++index),
                    room.name,
                    x, y, room.ceilingZ,
                    "pendant",
                    spacing
                ));
            }
        }

        return result;
    }

    // =========================================================================
    // Validation (Contract Enforcement)
    // =========================================================================

    /**
     * Validate that fire protection requirements are met.
     *
     * MATHS PROOF:
     * - actual_heads >= required_heads
     * - spacing within limits
     */
    public FireProtectionValidation validate(
            List<BuildingSpecs.SprinklerSpec> sprinklers,
            double floorArea,
            double buildingHeight,
            String occupancyGroup) {

        int storeys = calculateStoreys(buildingHeight);

        boolean required = fpAD.isSprinklerRequired(
            storeys, buildingHeight, floorArea, occupancyGroup, jurisdiction);

        if (!required) {
            return FireProtectionValidation.notRequired();
        }

        // Calculate required heads
        int requiredHeads = (int) Math.ceil(floorArea / MAX_COVERAGE_M2);
        int actualHeads = sprinklers.size();

        // Validate coverage
        List<String> violations = new ArrayList<>();

        if (actualHeads < requiredHeads) {
            violations.add(String.format(
                "INSUFFICIENT_COVERAGE: %d heads < %d required (ceil(%.0f/%.1f))",
                actualHeads, requiredHeads, floorArea, MAX_COVERAGE_M2));
        }

        // Validate spacing (if we have positions)
        SpacingAnalysis spacing = analyzeSpacing(sprinklers);

        if (spacing.minSpacing > 0 && spacing.minSpacing < 1.8) {
            violations.add(String.format(
                "SPACING_TOO_CLOSE: %.2fm < 1.8m min (NFPA 13)",
                spacing.minSpacing));
        }

        if (spacing.maxSpacing > MAX_SPACING_M) {
            violations.add(String.format(
                "SPACING_TOO_FAR: %.2fm > %.1fm max (NFPA 13)",
                spacing.maxSpacing, MAX_SPACING_M));
        }

        if (violations.isEmpty()) {
            return FireProtectionValidation.valid(
                floorArea, requiredHeads, actualHeads,
                1.8, MAX_SPACING_M, spacing.minSpacing, spacing.maxSpacing);
        }

        return FireProtectionValidation.invalid(floorArea, requiredHeads, actualHeads, violations);
    }

    private SpacingAnalysis analyzeSpacing(List<BuildingSpecs.SprinklerSpec> sprinklers) {
        if (sprinklers.size() < 2) {
            return new SpacingAnalysis(0, 0);
        }

        double minSpacing = Double.MAX_VALUE;
        double maxSpacing = 0;

        for (int i = 0; i < sprinklers.size(); i++) {
            var s1 = sprinklers.get(i);
            for (int j = i + 1; j < sprinklers.size(); j++) {
                var s2 = sprinklers.get(j);

                // Only compare same floor (Z within 0.5m)
                if (Math.abs(s1.z() - s2.z()) > 0.5) continue;

                double dist = Math.sqrt(
                    Math.pow(s2.x() - s1.x(), 2) +
                    Math.pow(s2.y() - s1.y(), 2)
                );

                // Only consider adjacent heads
                if (dist < MAX_SPACING_M * 1.5) {
                    minSpacing = Math.min(minSpacing, dist);
                    maxSpacing = Math.max(maxSpacing, dist);
                }
            }
        }

        return new SpacingAnalysis(
            minSpacing == Double.MAX_VALUE ? 0 : minSpacing,
            maxSpacing
        );
    }

    private record SpacingAnalysis(double minSpacing, double maxSpacing) {}

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private int calculateStoreys(double buildingHeight) {
        return Math.max(1, (int) Math.ceil(buildingHeight / 3.0));
    }

    // =========================================================================
    // Supporting Records
    // =========================================================================

    /**
     * Room bounds for sprinkler generation.
     */
    public record RoomBounds(
        String name,
        double minX, double minY,
        double maxX, double maxY,
        double ceilingZ
    ) {}

    // =========================================================================
    // Test Entry Point
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=== FireProtectionResolver Test ===\n");

        FireProtectionResolver resolver = new FireProtectionResolver("MALAYSIA");

        // Test 1: School (1056m², should trigger)
        System.out.println("--- TEST 1: School (1056m²) ---");
        List<RoomBounds> schoolRooms = List.of(
            new RoomBounds("class_1", 0, 0, 8, 7, 3.0),
            new RoomBounds("class_2", 8, 0, 16, 7, 3.0),
            new RoomBounds("class_3", 16, 0, 24, 7, 3.0),
            new RoomBounds("hall", 0, 10, 20, 20, 3.0)
        );

        List<BuildingSpecs.SprinklerSpec> schoolSprinklers =
            resolver.resolveSprinklers("Ground", schoolRooms, 6.4, 1056.0, "E");

        System.out.printf("Generated: %d sprinklers%n", schoolSprinklers.size());

        FireProtectionValidation validation =
            resolver.validate(schoolSprinklers, 1056.0, 6.4, "E");
        System.out.printf("Validation: %s%n", validation.valid() ? "PASS" : "FAIL");
        System.out.printf("Required: %d, Actual: %d%n",
            validation.requiredHeads(), validation.actualHeads());

        // Test 2: Small house (60m², should NOT trigger)
        System.out.println("\n--- TEST 2: Small House (60m²) ---");
        List<RoomBounds> houseRooms = List.of(
            new RoomBounds("living", 0, 0, 5, 4, 2.8),
            new RoomBounds("bedroom", 5, 0, 9, 4, 2.8)
        );

        List<BuildingSpecs.SprinklerSpec> houseSprinklers =
            resolver.resolveSprinklers("Ground", houseRooms, 5.6, 60.0, "R");

        System.out.printf("Generated: %d sprinklers (expected: 0)%n", houseSprinklers.size());

        // Test 3: High-rise (54m, should trigger)
        System.out.println("\n--- TEST 3: High-rise (54m) ---");
        List<RoomBounds> condoRooms = List.of(
            new RoomBounds("living", 0, 0, 6, 5, 3.0),
            new RoomBounds("master", 6, 0, 10, 5, 3.0)
        );

        List<BuildingSpecs.SprinklerSpec> condoSprinklers =
            resolver.resolveSprinklers("Level_10", condoRooms, 54.0, 1500.0, "R");

        System.out.printf("Generated: %d sprinklers%n", condoSprinklers.size());

        System.out.println("\n=== FireProtectionResolver Test Complete ===");
    }
}
