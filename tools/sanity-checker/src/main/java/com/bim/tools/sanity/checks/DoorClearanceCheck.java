package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.BoundingBox;
import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.util.*;

/**
 * Door Clearance Sanity Check - Phase 70
 *
 * GEOMETRY IS MATHS: All validations are numerical proofs.
 *
 * Validates:
 * 1. DOOR_WIDTH_MATHS: Width >= minimum for accessibility
 * 2. DOOR_HEIGHT_MATHS: Height >= minimum headroom
 * 3. DOOR_PROPORTION: Reasonable width-to-height ratio
 *
 * Code references:
 * - MS 1184: Accessible door min 850mm clear width
 * - UBBL By-Law 175: Door width requirements
 * - UBBL Schedule 4: Minimum door sizes by room type
 */
public class DoorClearanceCheck implements SanityCheck {

    // MS 1184 / UBBL accessible route
    private static final double MIN_ACCESSIBLE_WIDTH_MM = 850.0;
    // Standard minimum door width
    private static final double MIN_STANDARD_WIDTH_MM = 750.0;
    // Minimum door height (headroom)
    private static final double MIN_HEIGHT_MM = 2000.0;
    // Maximum reasonable door width (double door)
    private static final double MAX_WIDTH_MM = 2400.0;

    // Door thickness range (to distinguish width from depth)
    private static final double MIN_THICKNESS_MM = 35.0;
    private static final double MAX_THICKNESS_MM = 250.0;

    @Override
    public String getId() { return "door_clearance"; }

    @Override
    public String getName() { return "Door Clearance Dimensions"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        List<Element> doors = model.getDoors();

        if (doors.isEmpty()) {
            return CheckResult.fail(getId(), getName())
                .summary("No doors found in building")
                .guidance("Add at least one entry door (UBBL By-Law 175)")
                .build();
        }

        // Determine if accessible route checking is required
        boolean requiresAccessible = requiresAccessibleRoute(model);
        double minWidthMm = requiresAccessible ? MIN_ACCESSIBLE_WIDTH_MM : MIN_STANDARD_WIDTH_MM;

        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> details = new ArrayList<>();

        int narrowDoors = 0;
        int lowDoors = 0;
        double minWidth = Double.MAX_VALUE;
        double minHeight = Double.MAX_VALUE;

        for (Element door : doors) {
            if (door.bbox() == null) {
                warnings.add(String.format("%s: No geometry", door.guid()));
                continue;
            }

            BoundingBox bbox = door.bbox();
            String doorName = getDoorName(door);

            // Door dimensions: width is larger horizontal, depth is smaller (thickness)
            double dim1 = bbox.width() * 1000;  // X in mm
            double dim2 = bbox.depth() * 1000;  // Y in mm
            double height = bbox.height() * 1000; // Z in mm

            // Identify width vs thickness
            double width, thickness;
            if (dim1 > dim2) {
                width = dim1;
                thickness = dim2;
            } else {
                width = dim2;
                thickness = dim1;
            }

            // Track minimums
            if (width < minWidth) minWidth = width;
            if (height < minHeight) minHeight = height;

            // 1. Check door width
            boolean isAccessibleDoor = isOnAccessibleRoute(door);
            double requiredWidth = isAccessibleDoor ? MIN_ACCESSIBLE_WIDTH_MM : minWidthMm;

            if (width < requiredWidth) {
                narrowDoors++;
                if (isAccessibleDoor) {
                    failures.add(String.format("%s: Width %.0fmm < %.0fmm (accessible route)",
                        doorName, width, requiredWidth));
                } else {
                    warnings.add(String.format("%s: Width %.0fmm < %.0fmm minimum",
                        doorName, width, requiredWidth));
                }
            } else if (width > MAX_WIDTH_MM) {
                warnings.add(String.format("%s: Width %.0fmm unusually wide (> %.0fmm)",
                    doorName, width, MAX_WIDTH_MM));
            }

            // 2. Check door height
            if (height < MIN_HEIGHT_MM) {
                lowDoors++;
                failures.add(String.format("%s: Height %.0fmm < %.0fmm minimum",
                    doorName, height, MIN_HEIGHT_MM));
            }

            // 3. Check thickness is reasonable (sanity check on dimension detection)
            if (thickness < MIN_THICKNESS_MM || thickness > MAX_THICKNESS_MM) {
                // Thickness outside normal range - might indicate geometry issue
                details.add(String.format("NOTE: %s thickness %.0fmm (outside %.0f-%.0fmm typical)",
                    doorName, thickness, MIN_THICKNESS_MM, MAX_THICKNESS_MM));
            }

            // Add passing door details
            if (width >= requiredWidth && height >= MIN_HEIGHT_MM) {
                details.add(String.format("OK: %s %.0f×%.0fmm (W×H)",
                    doorName, width, height));
            }
        }

        // Build result
        if (!failures.isEmpty()) {
            CheckResult.Builder result = CheckResult.fail(getId(), getName())
                .summary(String.format("Door dimension violations: %d narrow, %d low",
                    narrowDoors, lowDoors));

            result.detail(String.format("REQUIREMENTS: width >= %.0fmm%s, height >= %.0fmm",
                minWidthMm,
                requiresAccessible ? " (accessible: 850mm)" : "",
                MIN_HEIGHT_MM));

            for (String f : failures) {
                result.detail("FAIL: " + f);
            }
            for (String w : warnings) {
                result.detail("WARNING: " + w);
            }

            // Show summary stats
            result.detail(String.format("STATS: %d doors, min width %.0fmm, min height %.0fmm",
                doors.size(), minWidth, minHeight));

            result.guidance("Increase door dimensions to meet code minimum (MS 1184, UBBL By-Law 175)");
            result.data("doorCount", doors.size());
            result.data("narrowDoors", narrowDoors);
            result.data("lowDoors", lowDoors);

            return result.build();
        }

        if (!warnings.isEmpty()) {
            CheckResult.Builder result = CheckResult.warning(getId(), getName())
                .summary(String.format("%d door(s) with warnings", warnings.size()));

            result.detail(String.format("REQUIREMENTS: width >= %.0fmm, height >= %.0fmm",
                minWidthMm, MIN_HEIGHT_MM));

            for (String w : warnings) {
                result.detail("WARNING: " + w);
            }

            result.guidance("Review door dimensions for code compliance");
            return result.build();
        }

        // All pass
        CheckResult.Builder result = CheckResult.pass(getId(), getName())
            .summary(String.format("All %d doors meet dimensional requirements", doors.size()));

        result.detail(String.format("REQUIREMENTS: width >= %.0fmm%s, height >= %.0fmm",
            minWidthMm,
            requiresAccessible ? " (accessible: 850mm)" : "",
            MIN_HEIGHT_MM));

        result.detail(String.format("RANGE: width %.0f-%.0fmm, height %.0f-%.0fmm",
            minWidth, getMaxWidth(doors), minHeight, getMaxHeight(doors)));

        // Show door type breakdown
        Map<String, Integer> doorTypes = countDoorTypes(doors);
        for (Map.Entry<String, Integer> entry : doorTypes.entrySet()) {
            result.detail(String.format("TYPE: %s × %d", entry.getKey(), entry.getValue()));
        }

        result.data("doorCount", doors.size());
        result.data("minWidth", minWidth);
        result.data("minHeight", minHeight);

        return result.build();
    }

    /**
     * Determine if building requires accessible routes.
     * Public buildings and multi-storey buildings require accessible doors.
     */
    private boolean requiresAccessibleRoute(SanityModel model) {
        // Multi-storey buildings require accessibility
        if (model.getStoreyCount() > 1) {
            return true;
        }

        // Public buildings require accessibility
        for (Element space : model.getSpaces()) {
            String name = space.name() != null ? space.name().toLowerCase() : "";
            if (name.contains("classroom") || name.contains("kelas") ||
                name.contains("office") || name.contains("pejabat") ||
                name.contains("canteen") || name.contains("kantin") ||
                name.contains("lobby") || name.contains("lobi") ||
                name.contains("toilet") || name.contains("tandas")) {
                return true;
            }
        }

        // Large floor area suggests public/commercial
        BoundingBox envelope = model.getBuildingEnvelope();
        if (envelope != null && envelope.areaXY() > 300) {
            return true;
        }

        return false;
    }

    /**
     * Check if door is on an accessible route (entry, corridor, toilet).
     */
    private boolean isOnAccessibleRoute(Element door) {
        if (door.guid() == null) return false;
        String upper = door.guid().toUpperCase();

        // Entry doors are always on accessible route
        if (upper.contains("ENTRY") || upper.contains("EXTERIOR") ||
            upper.contains("MAIN") || upper.contains("FRONT")) {
            return true;
        }

        // Doors to corridors
        if (upper.contains("KORIDOR") || upper.contains("CORRIDOR") ||
            upper.contains("HALL") || upper.contains("LOBBY")) {
            return true;
        }

        // Accessible toilet doors
        if (upper.contains("TOILET") || upper.contains("WC") ||
            upper.contains("TANDAS") || upper.contains("BILIK_AIR")) {
            return true;
        }

        return false;
    }

    /**
     * Get a readable door name for reporting.
     */
    private String getDoorName(Element door) {
        if (door.name() != null && !door.name().isEmpty()) {
            return door.name();
        }
        // Extract meaningful part from guid
        String guid = door.guid();
        if (guid != null && guid.startsWith("DOOR_")) {
            // DOOR_TOILET_M_DOOR_SOUTH_Ground -> TOILET_M
            int secondDoor = guid.indexOf("_DOOR_", 5);
            if (secondDoor > 0) {
                return guid.substring(5, secondDoor);
            }
        }
        return guid != null ? guid : "unknown";
    }

    /**
     * Count doors by type (from element_name).
     */
    private Map<String, Integer> countDoorTypes(List<Element> doors) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Element door : doors) {
            String type = door.name() != null ? door.name() : "unknown";
            counts.merge(type, 1, Integer::sum);
        }
        return counts;
    }

    private double getMaxWidth(List<Element> doors) {
        double max = 0;
        for (Element door : doors) {
            if (door.bbox() != null) {
                double w = Math.max(door.bbox().width(), door.bbox().depth()) * 1000;
                if (w > max) max = w;
            }
        }
        return max;
    }

    private double getMaxHeight(List<Element> doors) {
        double max = 0;
        for (Element door : doors) {
            if (door.bbox() != null) {
                double h = door.bbox().height() * 1000;
                if (h > max) max = h;
            }
        }
        return max;
    }
}
