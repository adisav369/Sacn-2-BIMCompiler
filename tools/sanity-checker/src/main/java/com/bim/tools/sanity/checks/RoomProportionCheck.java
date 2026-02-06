package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Check 5: Room Proportions
 * Verifies rooms have sensible shapes (not impossibly narrow).
 */
public class RoomProportionCheck implements SanityCheck {

    // Aspect ratio thresholds (min dimension / max dimension)
    private static final double FAIL_RATIO = 0.15;
    private static final double WARN_RATIO = 0.25;

    // Minimum dimension thresholds (meters)
    private static final double FAIL_MIN_DIM = 0.6;
    private static final double WARN_MIN_DIM = 0.9;

    // Space-type specific minimum ratios
    private record SpaceTypeThreshold(double minRatio, double minDim) {}

    @Override
    public String getId() { return "room_proportions"; }

    @Override
    public String getName() { return "Room Proportions"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        List<Element> spaces = model.getSpaces();

        if (spaces.isEmpty()) {
            // Check if we have implicit rooms (from wall patterns)
            Set<String> implicitRooms = model.getImplicitSpaceNames();
            implicitRooms.remove("exterior");

            if (!implicitRooms.isEmpty()) {
                return CheckResult.warning(getId(), getName())
                    .summary(String.format("Cannot check proportions for %d inferred rooms",
                        implicitRooms.size()))
                    .detail("Rooms inferred from wall naming: " + String.join(", ", implicitRooms))
                    .detail("Room proportion check requires explicit IfcSpace elements with geometry")
                    .guidance("Add IfcSpace elements to enable proportion validation")
                    .build();
            }

            return CheckResult.warning(getId(), getName())
                .summary("No rooms to check")
                .detail("No IfcSpace elements found in building")
                .build();
        }

        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> passDetails = new ArrayList<>();

        for (Element space : spaces) {
            if (space.bbox() == null) continue;

            double width = space.bbox().width();
            double depth = space.bbox().depth();
            double minDim = Math.min(width, depth);
            double maxDim = Math.max(width, depth);
            double aspectRatio = maxDim > 0 ? minDim / maxDim : 0;

            String name = space.name() != null ? space.name() : space.guid();
            String spaceType = model.inferSpaceType(space);
            SpaceTypeThreshold threshold = getThreshold(spaceType);

            String dimensions = String.format("%.1f×%.1fm", width, depth);
            String ratioStr = String.format("ratio %.2f", aspectRatio);

            // Check minimum dimension first
            if (minDim < FAIL_MIN_DIM) {
                failures.add(String.format("\"%s\" too narrow: %s (min dimension %.1fm < %.1fm)",
                    name, dimensions, minDim, FAIL_MIN_DIM));
                continue;
            }

            if (minDim < threshold.minDim) {
                warnings.add(String.format("\"%s\" narrow: %s (min dimension %.1fm)",
                    name, dimensions, minDim));
                continue;
            }

            // Check aspect ratio
            // Phase 64: Use space-type specific thresholds, not global FAIL_RATIO
            // This allows long corridors (e.g., 32m × 3m = ratio 0.09) which are normal for schools
            if (aspectRatio < threshold.minRatio) {
                // Only fail if below HALF the space-type threshold (truly impossible)
                double hardFailRatio = threshold.minRatio / 2;
                if (aspectRatio < hardFailRatio) {
                    failures.add(String.format("\"%s\" has impossible proportions: %s (%s) - this is a slot, not a room",
                        name, dimensions, ratioStr));
                } else {
                    warnings.add(String.format("\"%s\" unusual proportions: %s (%s)",
                        name, dimensions, ratioStr));
                }
            } else {
                if ("CORRIDOR".equals(spaceType)) {
                    passDetails.add(String.format("%s: %s (%s) [CORRIDOR - acceptable]",
                        name, dimensions, ratioStr));
                } else {
                    passDetails.add(String.format("%s: %s (%s)",
                        name, dimensions, ratioStr));
                }
            }
        }

        if (!failures.isEmpty()) {
            CheckResult.Builder result = CheckResult.fail(getId(), getName())
                .summary(String.format("%d room(s) have impossible proportions", failures.size()));

            for (String f : failures) {
                result.detail(f);
            }
            for (String w : warnings) {
                result.detail("[WARNING] " + w);
            }

            result.guidance("Adjust room dimensions to have minimum width of 0.6m and sensible aspect ratio");
            return result.build();
        }

        if (!warnings.isEmpty()) {
            CheckResult.Builder result = CheckResult.warning(getId(), getName())
                .summary(String.format("%d room(s) have unusual proportions", warnings.size()));

            for (String w : warnings) {
                result.detail(w);
            }

            result.guidance("Consider adjusting room dimensions for better usability");
            return result.build();
        }

        CheckResult.Builder result = CheckResult.pass(getId(), getName())
            .summary("All rooms have sensible proportions");

        for (String p : passDetails) {
            result.detail(p);
        }

        result.data("roomCount", spaces.size());
        return result.build();
    }

    private SpaceTypeThreshold getThreshold(String spaceType) {
        return switch (spaceType) {
            // Phase 64: School corridors can be 32m+ long with 3m width
            // Allow aspect ratios down to 0.05 (= 20:1, e.g., 60m × 3m)
            case "CORRIDOR" -> new SpaceTypeThreshold(0.05, 0.9);  // Long corridors expected
            case "STORAGE" -> new SpaceTypeThreshold(0.20, 0.6);   // Can be narrow
            case "BATHROOM" -> new SpaceTypeThreshold(0.30, 1.2);  // Needs room to move
            case "BEDROOM", "LIVING", "KITCHEN" -> new SpaceTypeThreshold(0.40, 2.0);
            case "PORCH" -> new SpaceTypeThreshold(0.25, 1.0);
            default -> new SpaceTypeThreshold(WARN_RATIO, WARN_MIN_DIM);
        };
    }
}
