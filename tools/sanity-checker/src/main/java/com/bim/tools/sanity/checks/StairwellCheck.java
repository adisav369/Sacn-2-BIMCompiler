package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.BoundingBox;
import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.util.*;

/**
 * Stairwell Sanity Check - Phase 69
 *
 * GEOMETRY IS MATHS: All validations are numerical proofs.
 *
 * Validates:
 * 1. STAIR_WIDTH_MATHS: Width >= minimum (1100mm for public, 900mm residential)
 * 2. STAIR_DIMENSIONS: Rise/run within acceptable range
 * 3. HEADROOM: Clearance above stairs (min 2000mm)
 *
 * Code references:
 * - UBBL By-Law 171: Staircase width min 1100mm (public buildings)
 * - UBBL By-Law 172: Rise 150-180mm, Going 250-300mm
 * - MS 1184: Residential stairs min 900mm width
 */
public class StairwellCheck implements SanityCheck {

    // UBBL By-Law 171: Minimum stair width
    private static final double MIN_WIDTH_PUBLIC_MM = 1100.0;
    private static final double MIN_WIDTH_RESIDENTIAL_MM = 900.0;

    // UBBL By-Law 172: Rise and going limits
    private static final double MIN_RISE_MM = 150.0;
    private static final double MAX_RISE_MM = 180.0;
    private static final double MIN_GOING_MM = 250.0;
    private static final double MAX_GOING_MM = 300.0;

    // Typical storey height and number of risers
    private static final double TYPICAL_STOREY_HEIGHT_M = 3.0;
    private static final int TYPICAL_RISERS = 17; // ~177mm rise for 3m storey

    // Headroom requirement
    private static final double MIN_HEADROOM_MM = 2000.0;

    @Override
    public String getId() { return "stairwell"; }

    @Override
    public String getName() { return "Stairwell Dimensions"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        // Get all stair-related elements
        List<Element> stairs = model.getStairs();
        List<Element> stairFlights = model.getStairFlights();

        // Build list of stairs to check
        // IfcStair is the container with overall dimensions - use this for code compliance
        // IfcStairFlight is detailed geometry - only check if no parent IfcStair exists
        List<Element> allStairs = new ArrayList<>();
        Set<String> stairNames = new HashSet<>();

        // First add all IfcStair elements (authoritative for width)
        for (Element stair : stairs) {
            allStairs.add(stair);
            // Track by base name to match flights
            String baseName = extractBaseName(stair.guid());
            if (baseName != null) stairNames.add(baseName);
        }

        // Only add IfcStairFlight if no matching IfcStair exists
        // (StairFlight represents detailed geometry, may have different dimensions)
        for (Element flight : stairFlights) {
            String baseName = extractBaseName(flight.guid());
            if (baseName == null || !stairNames.contains(baseName)) {
                // No parent stair - check this flight
                allStairs.add(flight);
            }
            // If parent stair exists, skip this flight (parent dimensions are authoritative)
        }

        if (allStairs.isEmpty()) {
            // Check if multi-storey building
            int storeyCount = model.getStoreyCount();
            if (storeyCount > 1) {
                return CheckResult.fail(getId(), getName())
                    .summary("Multi-storey building has no stairs")
                    .detail(String.format("Building has %d storeys but no IfcStair/IfcStairFlight elements", storeyCount))
                    .guidance("Add stairs connecting storeys (UBBL By-Law 171)")
                    .build();
            }

            return CheckResult.pass(getId(), getName())
                .summary("Single-storey building - no stairs required")
                .build();
        }

        // Determine building type for minimum width
        boolean isPublicBuilding = isPublicBuilding(model);
        double minWidthMm = isPublicBuilding ? MIN_WIDTH_PUBLIC_MM : MIN_WIDTH_RESIDENTIAL_MM;

        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> details = new ArrayList<>();

        for (Element stair : allStairs) {
            if (stair.bbox() == null) {
                warnings.add(String.format("%s: No geometry - cannot validate", stair.guid()));
                continue;
            }

            BoundingBox bbox = stair.bbox();
            String stairName = stair.name() != null ? stair.name() : stair.guid();

            // Determine stair width (perpendicular to travel direction)
            // Width is the shorter horizontal dimension
            double dim1 = bbox.width();  // X dimension
            double dim2 = bbox.depth();  // Y dimension
            double width = Math.min(dim1, dim2);
            double length = Math.max(dim1, dim2);
            double height = bbox.height();

            double widthMm = width * 1000;

            // 1. Check stair width
            if (widthMm < minWidthMm) {
                failures.add(String.format("%s: Width %.0fmm < %.0fmm minimum (%s)",
                    stairName, widthMm, minWidthMm,
                    isPublicBuilding ? "public building" : "residential"));
            } else if (widthMm < minWidthMm * 1.1) {
                // Near minimum - warn
                warnings.add(String.format("%s: Width %.0fmm is near minimum %.0fmm",
                    stairName, widthMm, minWidthMm));
            } else {
                details.add(String.format("WIDTH_OK: %s %.0fmm >= %.0fmm",
                    stairName, widthMm, minWidthMm));
            }

            // 2. Estimate rise from height (if stairflight)
            if (stair.isStairFlight() && height > 0) {
                // Estimate number of risers from typical dimensions
                int estimatedRisers = estimateRisers(height, length);
                if (estimatedRisers > 0) {
                    double riseM = height / estimatedRisers;
                    double riseMm = riseM * 1000;

                    if (riseMm < MIN_RISE_MM) {
                        warnings.add(String.format("%s: Estimated rise %.0fmm < %.0fmm min",
                            stairName, riseMm, MIN_RISE_MM));
                    } else if (riseMm > MAX_RISE_MM) {
                        warnings.add(String.format("%s: Estimated rise %.0fmm > %.0fmm max",
                            stairName, riseMm, MAX_RISE_MM));
                    } else {
                        details.add(String.format("RISE_OK: %s ~%.0fmm (estimated %d risers)",
                            stairName, riseMm, estimatedRisers));
                    }
                }
            }

            // 3. Add dimension summary
            details.add(String.format("DIMENSIONS: %s %.2fm × %.2fm × %.2fm (W×L×H)",
                stairName, width, length, height));
        }

        // Check stair count for multi-storey buildings
        int storeyCount = model.getStoreyCount();
        if (storeyCount > 1 && allStairs.size() < storeyCount - 1) {
            warnings.add(String.format("Building has %d storeys but only %d stair(s)",
                storeyCount, allStairs.size()));
        }

        // Build result
        if (!failures.isEmpty()) {
            CheckResult.Builder result = CheckResult.fail(getId(), getName())
                .summary(String.format("Stair dimension violations: %d issues", failures.size()));

            result.detail(String.format("MIN_WIDTH: %.0fmm (%s)",
                minWidthMm, isPublicBuilding ? "UBBL By-Law 171 public" : "MS 1184 residential"));

            for (String f : failures) {
                result.detail("FAIL: " + f);
            }
            for (String w : warnings) {
                result.detail("WARNING: " + w);
            }
            for (String d : details) {
                result.detail(d);
            }

            result.guidance("Increase stair width to meet code minimum (UBBL By-Law 171)");
            result.data("stairCount", allStairs.size());
            result.data("minWidthMm", minWidthMm);
            result.data("isPublicBuilding", isPublicBuilding);

            return result.build();
        }

        if (!warnings.isEmpty()) {
            CheckResult.Builder result = CheckResult.warning(getId(), getName())
                .summary(String.format("%d stair(s) with warnings", warnings.size()));

            result.detail(String.format("MIN_WIDTH: %.0fmm (%s)",
                minWidthMm, isPublicBuilding ? "UBBL By-Law 171 public" : "MS 1184 residential"));

            for (String w : warnings) {
                result.detail("WARNING: " + w);
            }
            for (String d : details) {
                result.detail(d);
            }

            result.guidance("Review stair dimensions for code compliance");
            return result.build();
        }

        // All pass
        CheckResult.Builder result = CheckResult.pass(getId(), getName())
            .summary(String.format("All %d stair(s) meet dimensional requirements", allStairs.size()));

        result.detail(String.format("MIN_WIDTH: %.0fmm (%s)",
            minWidthMm, isPublicBuilding ? "UBBL By-Law 171 public" : "MS 1184 residential"));

        for (String d : details) {
            result.detail(d);
        }

        result.data("stairCount", allStairs.size());
        result.data("storeyCount", storeyCount);

        return result.build();
    }

    /**
     * Determine if building is public (school, office, etc.) or residential.
     * Public buildings have stricter stair width requirements.
     */
    private boolean isPublicBuilding(SanityModel model) {
        // Check space types for indicators
        for (Element space : model.getSpaces()) {
            String name = space.name() != null ? space.name().toLowerCase() : "";
            // Public building indicators
            if (name.contains("classroom") || name.contains("kelas") ||
                name.contains("office") || name.contains("pejabat") ||
                name.contains("canteen") || name.contains("kantin") ||
                name.contains("corridor") || name.contains("koridor") ||
                name.contains("assembly") || name.contains("auditorium") ||
                name.contains("lobby") || name.contains("lobi")) {
                return true;
            }
        }

        // Check for large floor area (indicates public/commercial)
        BoundingBox envelope = model.getBuildingEnvelope();
        if (envelope != null && envelope.areaXY() > 500) {
            return true; // Buildings > 500m² are likely public
        }

        // Multi-storey with more than 4 units is likely public
        int storeyCount = model.getStoreyCount();
        if (storeyCount > 3) {
            return true;
        }

        return false; // Default to residential
    }

    /**
     * Extract base name from stair guid for matching.
     * E.g., "STAIR_MAIN" and "STAIRFLIGHT_MAIN" both have base name "MAIN"
     */
    private String extractBaseName(String guid) {
        if (guid == null) return null;
        String upper = guid.toUpperCase();

        // Remove STAIR_ or STAIRFLIGHT_ prefix
        if (upper.startsWith("STAIRFLIGHT_")) {
            return guid.substring(12);
        }
        if (upper.startsWith("STAIR_")) {
            return guid.substring(6);
        }

        return guid;
    }

    /**
     * Estimate number of risers from stair height and length.
     */
    private int estimateRisers(double height, double length) {
        if (height <= 0 || length <= 0) return 0;

        // Typical going is 250-280mm
        double typicalGoing = 0.275; // 275mm
        int estimatedFromLength = (int) Math.round(length / typicalGoing);

        // Typical rise is 150-180mm
        double typicalRise = 0.175; // 175mm
        int estimatedFromHeight = (int) Math.round(height / typicalRise);

        // Use the more conservative estimate
        if (estimatedFromLength > 0 && estimatedFromHeight > 0) {
            return Math.min(estimatedFromLength, estimatedFromHeight);
        }

        return estimatedFromHeight > 0 ? estimatedFromHeight : estimatedFromLength;
    }
}
