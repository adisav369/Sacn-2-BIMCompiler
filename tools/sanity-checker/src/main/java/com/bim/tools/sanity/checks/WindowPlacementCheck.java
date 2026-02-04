package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.BoundingBox;
import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Check 3: Window Placement
 * Verifies all windows are on exterior walls only.
 */
public class WindowPlacementCheck implements SanityCheck {

    @Override
    public String getId() { return "window_placement"; }

    @Override
    public String getName() { return "Window Placement"; }

    @Override
    public CheckResult execute(SanityModel model) {
        List<Element> windows = model.getWindows();

        if (windows.isEmpty()) {
            // No windows is unusual but not necessarily wrong
            return CheckResult.warning(getId(), getName())
                .summary("No windows found in building")
                .guidance("Buildings typically have windows for light and ventilation")
                .build();
        }

        List<Element> exteriorWalls = model.getExteriorWalls();
        List<Element> interiorWalls = model.getInteriorWalls();

        Set<String> exteriorWallGuids = new HashSet<>();
        for (Element w : exteriorWalls) {
            exteriorWallGuids.add(w.guid());
        }

        List<String> interiorWindowProblems = new ArrayList<>();
        int exteriorWindowCount = 0;

        for (Element window : windows) {
            // Phase 64: First check if window guid indicates exterior direction
            // This is more reliable than geometry-based containment for library windows
            if (isExteriorWindowByGuid(window)) {
                exteriorWindowCount++;
                continue;
            }

            Element containingWall = model.findContainingWall(window);

            if (containingWall == null) {
                // Window not in any wall - check if it's on perimeter directly
                if (isOnBuildingPerimeter(window, model.getBuildingEnvelope())) {
                    exteriorWindowCount++;
                } else {
                    interiorWindowProblems.add(String.format("%s: not attached to any wall",
                        window.name() != null ? window.name() : window.guid()));
                }
            } else if (exteriorWallGuids.contains(containingWall.guid())) {
                exteriorWindowCount++;
            } else {
                // Window in interior wall - find adjacent rooms
                String windowId = window.name() != null ? window.name() : window.guid();
                String wallId = containingWall.name() != null ? containingWall.name() : containingWall.guid();

                List<Element> adjacentSpaces = findAdjacentSpaces(containingWall, model);
                String roomNames = adjacentSpaces.isEmpty() ? "unknown rooms" :
                    adjacentSpaces.stream()
                        .map(s -> s.name() != null ? s.name() : s.guid())
                        .reduce((a, b) -> "\"" + a + "\" and \"" + b + "\"")
                        .orElse("unknown");

                String dims = String.format("%.0f×%.0fmm",
                    Math.min(window.width(), window.depth()) * 1000,
                    window.height() * 1000);

                interiorWindowProblems.add(String.format("%s (%s) is between %s",
                    windowId, dims, roomNames));
            }
        }

        if (interiorWindowProblems.isEmpty()) {
            return CheckResult.pass(getId(), getName())
                .summary(String.format("All %d windows on exterior walls", windows.size()))
                .data("windowCount", windows.size())
                .build();
        }

        CheckResult.Builder result = CheckResult.fail(getId(), getName())
            .summary(String.format("%d window(s) on interior wall", interiorWindowProblems.size()));

        for (String problem : interiorWindowProblems) {
            result.detail(problem);
        }

        result.guidance("Move windows to exterior walls or remove them");
        result.data("totalWindows", windows.size());
        result.data("interiorWindows", interiorWindowProblems.size());

        return result.build();
    }

    private boolean isOnBuildingPerimeter(Element window, BoundingBox envelope) {
        if (window.bbox() == null || envelope == null) return false;

        double tolerance = 0.15; // 150mm
        double cx = window.bbox().centerX();
        double cy = window.bbox().centerY();

        return envelope.isOnPerimeterXY(cx, cy, tolerance);
    }

    /**
     * Phase 64: Check if window guid indicates it's on an exterior wall.
     * Window guids contain wall direction: WINDOW_<ROOM>_WINDOW_<DIRECTION>_<STOREY>
     * Example: WINDOW_CLASS_1_WINDOW_NORTH_Ground
     */
    private boolean isExteriorWindowByGuid(Element window) {
        String guid = window.guid();
        if (guid == null) return false;

        String upper = guid.toUpperCase();
        // Window must indicate a cardinal direction AND not be interior
        boolean hasDirection = upper.contains("_NORTH") || upper.contains("_SOUTH") ||
                               upper.contains("_EAST") || upper.contains("_WEST");
        boolean isInterior = upper.contains("INTERIOR");

        return hasDirection && !isInterior;
    }

    private List<Element> findAdjacentSpaces(Element wall, SanityModel model) {
        List<Element> adjacent = new ArrayList<>();
        if (wall.bbox() == null) return adjacent;

        BoundingBox expanded = wall.bbox().expand(0.1); // 100mm tolerance
        for (Element space : model.getSpaces()) {
            if (space.bbox() != null && expanded.intersectsXY(space.bbox())) {
                // Phase 64: Only include spaces on same floor (Z range overlap)
                // Prevents multi-storey confusion where rooms on different floors
                // have same XY coordinates
                if (wall.bbox().intersectsZ(space.bbox())) {
                    adjacent.add(space);
                }
            }
        }
        return adjacent;
    }
}
