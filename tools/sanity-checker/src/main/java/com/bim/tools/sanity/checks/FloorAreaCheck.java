package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.util.*;

/**
 * Floor Area per Occupant Sanity Check - Phase 73
 *
 * GEOMETRY IS MATHS: All validations are numerical proofs.
 *
 * Validates:
 * 1. ROOM_AREA_MATHS: Floor area >= minimum for room type
 * 2. OCCUPANCY_LOAD: Area supports expected occupancy
 *
 * Code references:
 * - UBBL Schedule 4: Minimum room sizes
 * - MS 1184: Residential room requirements
 * - IBC 1004.5: Occupant load factors
 */
public class FloorAreaCheck implements SanityCheck {

    // UBBL Schedule 4 / MS 1184: Minimum floor areas (m²)
    private static final double MIN_BEDROOM_SINGLE_M2 = 9.3;
    private static final double MIN_BEDROOM_MASTER_M2 = 11.15;
    private static final double MIN_LIVING_ROOM_M2 = 11.0;
    private static final double MIN_KITCHEN_M2 = 4.5;
    private static final double MIN_BATHROOM_M2 = 2.5;
    private static final double MIN_TOILET_M2 = 1.2;

    // Public building minimums (UBBL)
    private static final double MIN_CLASSROOM_M2 = 45.0; // ~1.5m² per student × 30
    private static final double MIN_OFFICE_M2 = 9.0;

    // IBC 1004.5 occupant load factors (m² per person)
    private static final double CLASSROOM_FACTOR = 1.86; // 20 sq ft
    private static final double OFFICE_FACTOR = 9.3;     // 100 sq ft
    private static final double ASSEMBLY_FACTOR = 1.4;   // 15 sq ft (concentrated)

    @Override
    public String getId() { return "floor_area"; }

    @Override
    public String getName() { return "Floor Area Requirements"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        List<Element> spaces = model.getSpaces();

        if (spaces.isEmpty()) {
            return CheckResult.warning(getId(), getName())
                .summary("No rooms found - cannot validate floor areas")
                .build();
        }

        boolean isResidential = isResidentialBuilding(model);

        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> details = new ArrayList<>();

        int undersizedRooms = 0;
        double totalArea = 0;
        int totalOccupancy = 0;

        for (Element space : spaces) {
            if (space.bbox() == null) continue;

            String roomName = getRoomName(space);
            String spaceType = model.inferSpaceType(space);
            double area = space.bbox().areaXY();
            totalArea += area;

            // Get minimum area for room type
            RoomRequirement req = getRequirement(space, spaceType, model);

            if (area < req.minArea) {
                undersizedRooms++;
                failures.add(String.format("%s [%s]: %.1fm² < %.1fm² minimum",
                    roomName, spaceType, area, req.minArea));
            } else if (area < req.minArea * 1.1) {
                // Near minimum warning
                warnings.add(String.format("%s [%s]: %.1fm² near minimum %.1fm²",
                    roomName, spaceType, area, req.minArea));
            } else {
                // Calculate occupancy
                int occupancy = req.occupancyFactor > 0 ? (int) Math.floor(area / req.occupancyFactor) : 0;
                totalOccupancy += occupancy;

                if (occupancy > 0) {
                    details.add(String.format("OK: %s [%s] %.1fm² (capacity: %d persons)",
                        roomName, spaceType, area, occupancy));
                } else {
                    details.add(String.format("OK: %s [%s] %.1fm²",
                        roomName, spaceType, area));
                }
            }
        }

        // Build result
        if (!failures.isEmpty()) {
            CheckResult.Builder result = CheckResult.fail(getId(), getName())
                .summary(String.format("Floor area violations: %d rooms below minimum", undersizedRooms));

            result.detail(String.format("BUILDING_TYPE: %s",
                isResidential ? "Residential (MS 1184)" : "Public (UBBL Schedule 4)"));

            for (String f : failures) {
                result.detail("FAIL: " + f);
            }
            for (String w : warnings) {
                result.detail("WARNING: " + w);
            }

            result.guidance("Increase room size to meet code minimum (UBBL Schedule 4)");
            result.data("undersizedRooms", undersizedRooms);
            result.data("totalArea", totalArea);

            return result.build();
        }

        if (!warnings.isEmpty()) {
            CheckResult.Builder result = CheckResult.warning(getId(), getName())
                .summary(String.format("All rooms meet minimum, %d near minimum", warnings.size()));

            for (String w : warnings) {
                result.detail(w);
            }

            return result.build();
        }

        // All pass
        CheckResult.Builder result = CheckResult.pass(getId(), getName())
            .summary(String.format("All %d rooms meet floor area requirements", spaces.size()));

        result.detail(String.format("BUILDING_TYPE: %s",
            isResidential ? "Residential (MS 1184)" : "Public (UBBL Schedule 4)"));

        result.detail(String.format("TOTAL_AREA: %.1fm²", totalArea));

        if (totalOccupancy > 0) {
            result.detail(String.format("TOTAL_CAPACITY: %d persons (estimated)", totalOccupancy));
        }

        // Show room breakdown by type
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        Map<String, Double> typeAreas = new LinkedHashMap<>();
        for (Element space : spaces) {
            String spaceType = model.inferSpaceType(space);
            typeCounts.merge(spaceType, 1, Integer::sum);
            typeAreas.merge(spaceType, space.bbox() != null ? space.bbox().areaXY() : 0.0, Double::sum);
        }

        for (String type : typeCounts.keySet()) {
            result.detail(String.format("TYPE: %s × %d (%.1fm² total)",
                type, typeCounts.get(type), typeAreas.get(type)));
        }

        result.data("roomCount", spaces.size());
        result.data("totalArea", totalArea);
        result.data("totalOccupancy", totalOccupancy);

        return result.build();
    }

    /**
     * Get minimum area requirement for room type.
     * Priority: Database object_type > spaceType inference > name matching
     */
    private RoomRequirement getRequirement(Element space, String spaceType, SanityModel model) {
        String name = space.name() != null ? space.name().toLowerCase() : "";
        boolean isResidential = isResidentialBuilding(model);

        // Priority 1: Check if database object_type indicates exempt category
        String objectType = space.objectType();
        if (objectType != null) {
            String upper = objectType.toUpperCase();
            // Storage, mechanical, utility, corridor, stair, porch - no minimum
            if (upper.equals("STORAGE") || upper.equals("STORE") ||
                upper.equals("MECHANICAL") || upper.equals("UTILITY") ||
                upper.equals("CORRIDOR") || upper.equals("STAIR") ||
                upper.equals("PORCH") || upper.equals("LOBBY") ||
                upper.equals("ELEVATOR") || upper.equals("SHAFT")) {
                return new RoomRequirement(0, 0);
            }
        }

        // Priority 2: Use spaceType (from inference) if it's a definite exempt type
        if (spaceType.equals("STORAGE") || spaceType.equals("CORRIDOR") ||
            spaceType.equals("STAIR") || spaceType.equals("ELEVATOR") ||
            spaceType.equals("LOBBY") || spaceType.equals("PORCH") ||
            spaceType.equals("MECHANICAL") || spaceType.equals("UTILITY") ||
            spaceType.equals("SHAFT")) {
            return new RoomRequirement(0, 0);
        }

        // Priority 3: Type-specific minimums based on spaceType and name
        // Bathroom/toilet
        if (spaceType.equals("BATHROOM") || name.contains("bilik_mandi") ||
            name.contains("bath") || name.contains("shower")) {
            return new RoomRequirement(MIN_BATHROOM_M2, 0);
        }

        if (name.contains("toilet") || name.contains("tandas") || name.contains("wc")) {
            return new RoomRequirement(MIN_TOILET_M2, 0);
        }

        // Kitchen
        if (spaceType.equals("KITCHEN") || name.contains("dapur") || name.contains("kitchen")) {
            return new RoomRequirement(MIN_KITCHEN_M2, 0);
        }

        // Living room
        if (spaceType.equals("LIVING") || name.contains("living") ||
            name.contains("ruang_tamu") || name.contains("common")) {
            return new RoomRequirement(MIN_LIVING_ROOM_M2, 0);
        }

        // Bedroom (only if spaceType matches - don't rely on bilik_N name patterns)
        if (spaceType.equals("BEDROOM") || name.contains("bilik_tidur") ||
            name.contains("bilik_utama") || name.contains("bedroom")) {
            // Master bedroom vs single bedroom
            if (name.contains("utama") || name.contains("master")) {
                return new RoomRequirement(MIN_BEDROOM_MASTER_M2, 0);
            }
            return new RoomRequirement(MIN_BEDROOM_SINGLE_M2, 0);
        }

        // Classroom
        if (name.contains("class") || name.contains("kelas") || name.contains("classroom")) {
            return new RoomRequirement(MIN_CLASSROOM_M2, CLASSROOM_FACTOR);
        }

        // Office
        if (spaceType.equals("OFFICE") || name.contains("office") ||
            name.contains("pejabat") || name.contains("bilik_guru")) {
            return new RoomRequirement(MIN_OFFICE_M2, OFFICE_FACTOR);
        }

        // Assembly (hall, canteen)
        if (name.contains("dewan") || name.contains("hall") ||
            name.contains("kantin") || name.contains("canteen")) {
            return new RoomRequirement(20.0, ASSEMBLY_FACTOR); // Min 20m² for assembly
        }

        // Corridor (no minimum, just count)
        if (name.contains("koridor") || name.contains("corridor")) {
            return new RoomRequirement(0, 0);
        }

        // Porch/lobby (no minimum)
        if (name.contains("anjung") || name.contains("porch") ||
            name.contains("lobby") || name.contains("lobi")) {
            return new RoomRequirement(0, 0);
        }

        // Default for unknown rooms
        if (isResidential) {
            return new RoomRequirement(MIN_BEDROOM_SINGLE_M2, 0); // Treat as bedroom
        }
        return new RoomRequirement(MIN_OFFICE_M2, 0); // Treat as office
    }

    /**
     * Determine if building is residential.
     */
    private boolean isResidentialBuilding(SanityModel model) {
        int bedroomCount = 0;
        int classroomCount = 0;

        for (Element space : model.getSpaces()) {
            String name = space.name() != null ? space.name().toLowerCase() : "";
            if (name.contains("bilik") || name.contains("bedroom")) bedroomCount++;
            if (name.contains("class") || name.contains("kelas")) classroomCount++;
        }

        // More bedrooms than classrooms = residential
        return bedroomCount > classroomCount;
    }

    /**
     * Get readable room name.
     */
    private String getRoomName(Element space) {
        if (space.name() != null && !space.name().isEmpty()) {
            return space.name();
        }
        return space.guid() != null ? space.guid() : "unknown";
    }

    private record RoomRequirement(double minArea, double occupancyFactor) {}
}
