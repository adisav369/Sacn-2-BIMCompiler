package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.util.*;

/**
 * Ceiling Height Sanity Check - Phase 72
 *
 * GEOMETRY IS MATHS: All validations are numerical proofs.
 *
 * Validates:
 * 1. CEILING_HEIGHT_MATHS: Height >= minimum for room type
 * 2. STOREY_CONSISTENCY: Heights consistent within storey
 *
 * Code references:
 * - UBBL By-Law 44: Minimum ceiling height 2.75m (habitable rooms)
 * - UBBL By-Law 44(2): Minimum 2.5m for corridors, bathrooms, utility
 * - MS 1184: Residential ceiling heights
 */
public class CeilingHeightCheck implements SanityCheck {

    // UBBL By-Law 44: Minimum ceiling heights (meters)
    private static final double MIN_HABITABLE_HEIGHT_M = 2.75;
    private static final double MIN_UTILITY_HEIGHT_M = 2.50;
    private static final double MIN_STORAGE_HEIGHT_M = 2.20;

    // Warning threshold (near minimum)
    private static final double NEAR_MINIMUM_THRESHOLD = 0.10; // 100mm

    // Room types with reduced height requirement
    private static final Set<String> UTILITY_TYPES = Set.of(
        "BATHROOM", "TOILET", "CORRIDOR", "STORAGE", "LOBBY", "STAIR"
    );

    private static final Set<String> STORAGE_TYPES = Set.of(
        "STORAGE", "UTILITY", "MECHANICAL"
    );

    @Override
    public String getId() { return "ceiling_height"; }

    @Override
    public String getName() { return "Ceiling Height"; }

    @Override
    public CheckResult execute(SanityModel model) {
        List<Element> spaces = model.getSpaces();

        if (spaces.isEmpty()) {
            return CheckResult.warning(getId(), getName())
                .summary("No rooms found - cannot validate ceiling heights")
                .build();
        }

        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> details = new ArrayList<>();

        double minHeight = Double.MAX_VALUE;
        double maxHeight = 0;
        int lowRooms = 0;

        // Group by storey for consistency check
        Map<String, List<Double>> storeyHeights = new HashMap<>();

        for (Element space : spaces) {
            if (space.bbox() == null) {
                warnings.add(String.format("%s: No geometry", getRoomName(space)));
                continue;
            }

            String roomName = getRoomName(space);
            String spaceType = model.inferSpaceType(space);
            double height = space.bbox().height();

            // Track min/max
            if (height < minHeight) minHeight = height;
            if (height > maxHeight) maxHeight = height;

            // Track by storey
            String storey = extractStorey(space);
            storeyHeights.computeIfAbsent(storey, k -> new ArrayList<>()).add(height);

            // Determine required height
            double requiredHeight = getRequiredHeight(space, spaceType, model);
            double heightMm = height * 1000;
            double requiredMm = requiredHeight * 1000;

            // Check height
            if (height < requiredHeight) {
                lowRooms++;
                failures.add(String.format("%s [%s]: Height %.0fmm < %.0fmm minimum",
                    roomName, spaceType, heightMm, requiredMm));
            } else if (height < requiredHeight + NEAR_MINIMUM_THRESHOLD) {
                warnings.add(String.format("%s [%s]: Height %.0fmm near minimum %.0fmm",
                    roomName, spaceType, heightMm, requiredMm));
            } else {
                details.add(String.format("OK: %s [%s] %.0fmm >= %.0fmm",
                    roomName, spaceType, heightMm, requiredMm));
            }
        }

        // Check storey consistency
        for (Map.Entry<String, List<Double>> entry : storeyHeights.entrySet()) {
            List<Double> heights = entry.getValue();
            if (heights.size() > 1) {
                double avg = heights.stream().mapToDouble(h -> h).average().orElse(0);
                for (double h : heights) {
                    if (Math.abs(h - avg) > 0.1) { // >100mm variance
                        warnings.add(String.format("Storey %s: Height variance detected (%.2fm vs avg %.2fm)",
                            entry.getKey(), h, avg));
                        break;
                    }
                }
            }
        }

        // Build result
        if (!failures.isEmpty()) {
            CheckResult.Builder result = CheckResult.fail(getId(), getName())
                .summary(String.format("Ceiling height violations: %d rooms below minimum", lowRooms));

            result.detail(String.format("REQUIREMENTS: Habitable >= %.2fm, Utility >= %.2fm (UBBL By-Law 44)",
                MIN_HABITABLE_HEIGHT_M, MIN_UTILITY_HEIGHT_M));

            for (String f : failures) {
                result.detail("FAIL: " + f);
            }
            for (String w : warnings) {
                result.detail("WARNING: " + w);
            }

            result.guidance("Increase ceiling height to meet code minimum");
            result.data("lowRooms", lowRooms);
            result.data("minHeight", minHeight);
            result.data("maxHeight", maxHeight);

            return result.build();
        }

        if (!warnings.isEmpty()) {
            CheckResult.Builder result = CheckResult.warning(getId(), getName())
                .summary(String.format("All %d rooms meet minimum, %d warnings", spaces.size(), warnings.size()));

            for (String w : warnings) {
                result.detail(w);
            }

            return result.build();
        }

        // All pass
        CheckResult.Builder result = CheckResult.pass(getId(), getName())
            .summary(String.format("All %d rooms meet ceiling height requirements", spaces.size()));

        result.detail(String.format("REQUIREMENTS: Habitable >= %.2fm, Utility >= %.2fm",
            MIN_HABITABLE_HEIGHT_M, MIN_UTILITY_HEIGHT_M));

        result.detail(String.format("RANGE: %.2fm - %.2fm", minHeight, maxHeight));

        // Summarize by storey
        for (Map.Entry<String, List<Double>> entry : storeyHeights.entrySet()) {
            double avg = entry.getValue().stream().mapToDouble(h -> h).average().orElse(0);
            result.detail(String.format("STOREY %s: %d rooms, avg %.2fm",
                entry.getKey(), entry.getValue().size(), avg));
        }

        result.data("roomCount", spaces.size());
        result.data("minHeight", minHeight);
        result.data("maxHeight", maxHeight);
        result.data("storeyCount", storeyHeights.size());

        return result.build();
    }

    /**
     * Get required ceiling height for room type.
     */
    private double getRequiredHeight(Element space, String spaceType, SanityModel model) {
        // Storage rooms have lowest requirement
        if (STORAGE_TYPES.contains(spaceType)) {
            return MIN_STORAGE_HEIGHT_M;
        }

        // Check room name for storage indicators
        String name = space.name() != null ? space.name().toLowerCase() : "";
        if (name.contains("stor") || name.contains("utility") ||
            name.contains("mechanical") || name.contains("plant")) {
            return MIN_STORAGE_HEIGHT_M;
        }

        // Utility rooms (bathrooms, corridors, etc.)
        if (UTILITY_TYPES.contains(spaceType)) {
            return MIN_UTILITY_HEIGHT_M;
        }

        // Check room name for utility indicators
        if (name.contains("toilet") || name.contains("tandas") ||
            name.contains("bath") || name.contains("bilik_air") ||
            name.contains("bilik_mandi") ||  // Malay: bathroom
            name.contains("corridor") || name.contains("koridor") ||
            name.contains("lobby") || name.contains("lobi") ||
            name.contains("stair") || name.contains("tangga") ||
            name.contains("porch") || name.contains("anjung")) {  // Porches
            return MIN_UTILITY_HEIGHT_M;
        }

        // Habitable rooms (default)
        return MIN_HABITABLE_HEIGHT_M;
    }

    /**
     * Extract storey from space GUID or name.
     */
    private String extractStorey(Element space) {
        String guid = space.guid();
        if (guid != null) {
            String upper = guid.toUpperCase();
            if (upper.contains("GROUND")) return "Ground";
            if (upper.contains("UPPER")) return "Upper";
            if (upper.contains("FIRST")) return "First";
            if (upper.contains("SECOND")) return "Second";
            // Extract floor number pattern
            if (upper.contains("_F1_") || upper.contains("_1F_")) return "1F";
            if (upper.contains("_F2_") || upper.contains("_2F_")) return "2F";
        }
        return "Unknown";
    }

    /**
     * Get readable room name.
     */
    private String getRoomName(Element space) {
        if (space.name() != null && !space.name().isEmpty()) {
            return space.name();
        }
        String guid = space.guid();
        if (guid != null && guid.startsWith("SPACE_")) {
            // SPACE_GROUND_CLASS_1 -> CLASS_1
            String afterSpace = guid.substring(6);
            int firstUnderscore = afterSpace.indexOf('_');
            if (firstUnderscore > 0) {
                return afterSpace.substring(firstUnderscore + 1);
            }
        }
        return guid != null ? guid : "unknown";
    }
}
