package com.bim.compiler.solver;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.constraints.Constraint;
import com.bim.compiler.util.OutlierLogger;

import java.util.*;

/**
 * Space Solver - Production Implementation
 *
 * Constraint-based room placement using Choco CSP solver.
 * Takes room constraints, returns valid positions for BuildingCompiler.
 */
public class SpaceSolver {

    /**
     * Input constraint for a single room.
     * Phase 47D: Added zone bounds for unit partitioning.
     */
    public record RoomConstraint(
        String name,
        int widthMeters,
        int depthMeters,
        List<String> adjacentTo,      // Must share wall with these rooms
        List<String> notAdjacentTo,   // Cannot share wall with these rooms
        String exteriorWall,          // null, or "north"/"south"/"east"/"west"
        Integer zoneMinX,             // Phase 47D: Zone constraint (null = no constraint)
        Integer zoneMaxX,             // Phase 47D: Zone constraint (null = no constraint)
        Integer zoneMinY,             // Phase 47D: Zone constraint (null = no constraint)
        Integer zoneMaxY              // Phase 47D: Zone constraint (null = no constraint)
    ) {
        public RoomConstraint(String name, int widthMeters, int depthMeters) {
            this(name, widthMeters, depthMeters, List.of(), List.of(), null, null, null, null, null);
        }

        // Backward-compatible constructor without zone bounds
        public RoomConstraint(String name, int widthMeters, int depthMeters,
                              List<String> adjacentTo, List<String> notAdjacentTo, String exteriorWall) {
            this(name, widthMeters, depthMeters, adjacentTo, notAdjacentTo, exteriorWall, null, null, null, null);
        }

        // Phase 47D: Constructor with zone bounds
        public RoomConstraint withZoneBounds(Integer minX, Integer maxX, Integer minY, Integer maxY) {
            return new RoomConstraint(name, widthMeters, depthMeters, adjacentTo, notAdjacentTo,
                                      exteriorWall, minX, maxX, minY, maxY);
        }
    }

    /**
     * Solved position for a room.
     */
    public record GridPosition(int x, int y) {}

    /**
     * Constraint type for relaxation ordering.
     * Phase 25: Order determines which constraints are dropped first.
     */
    public enum ConstraintType {
        NOT_ADJACENT,  // Drop first - separation constraints
        ALIGNS,        // Then alignment
        EXTERIOR,      // Then exterior wall
        ADJACENT       // Adjacent last - most important for connectivity
    }

    /**
     * Solution output.
     * Phase 25: Added droppedConstraints for relaxation tracking.
     */
    public record SolvedLayout(
        Map<String, GridPosition> positions,
        int gridWidth,
        int gridHeight,
        boolean feasible,
        String failureReason,
        long solveTimeMs,
        List<String> droppedConstraints  // Phase 25: Constraints dropped during relaxation
    ) {
        public static SolvedLayout infeasible(String reason) {
            return new SolvedLayout(Map.of(), 0, 0, false, reason, 0, List.of());
        }

        public static SolvedLayout success(Map<String, GridPosition> positions,
                                           int gridWidth, int gridHeight, long solveTimeMs) {
            return new SolvedLayout(positions, gridWidth, gridHeight, true, null, solveTimeMs, List.of());
        }

        public static SolvedLayout successWithDropped(Map<String, GridPosition> positions,
                                                       int gridWidth, int gridHeight,
                                                       long solveTimeMs, List<String> dropped) {
            return new SolvedLayout(positions, gridWidth, gridHeight, true, null, solveTimeMs, dropped);
        }
    }

    /**
     * Solve room placement given constraints.
     *
     * @param constraints Room constraints
     * @param maxGridWidth Maximum building width in meters
     * @param maxGridHeight Maximum building depth in meters
     * @return Solved layout with positions, or infeasible result
     */
    public SolvedLayout solve(List<RoomConstraint> constraints, int maxGridWidth, int maxGridHeight) {
        if (constraints.isEmpty()) {
            return SolvedLayout.infeasible("No rooms to place");
        }

        Model model = new Model("SpaceSolver");
        long startTime = System.currentTimeMillis();

        // Create variables for each room
        Map<String, IntVar> xVars = new HashMap<>();
        Map<String, IntVar> yVars = new HashMap<>();
        Map<String, RoomConstraint> roomMap = new HashMap<>();

        for (RoomConstraint room : constraints) {
            // Phase 47D: Apply zone bounds if present, otherwise use full grid
            int xMin = room.zoneMinX() != null ? room.zoneMinX() : 0;
            int xMax = room.zoneMaxX() != null
                ? Math.min(room.zoneMaxX() - room.widthMeters(), maxGridWidth - room.widthMeters())
                : maxGridWidth - room.widthMeters();
            int yMin = room.zoneMinY() != null ? room.zoneMinY() : 0;
            int yMax = room.zoneMaxY() != null
                ? Math.min(room.zoneMaxY() - room.depthMeters(), maxGridHeight - room.depthMeters())
                : maxGridHeight - room.depthMeters();

            if (xMax < xMin || yMax < yMin) {
                return SolvedLayout.infeasible(
                    "Room '" + room.name() + "' (" + room.widthMeters() + "x" + room.depthMeters() +
                    "m) doesn't fit in zone [" + xMin + "-" + (room.zoneMaxX() != null ? room.zoneMaxX() : maxGridWidth) +
                    ", " + yMin + "-" + (room.zoneMaxY() != null ? room.zoneMaxY() : maxGridHeight) + "]");
            }

            IntVar x = model.intVar(room.name() + "_x", xMin, xMax);
            IntVar y = model.intVar(room.name() + "_y", yMin, yMax);
            xVars.put(room.name(), x);
            yVars.put(room.name(), y);
            roomMap.put(room.name(), room);
        }

        // Non-overlap constraints
        for (int i = 0; i < constraints.size(); i++) {
            for (int j = i + 1; j < constraints.size(); j++) {
                RoomConstraint r1 = constraints.get(i);
                RoomConstraint r2 = constraints.get(j);
                addNonOverlapConstraint(model, xVars, yVars, r1, r2);
            }
        }

        // Process room constraints
        for (RoomConstraint room : constraints) {
            // Adjacent constraints
            for (String adjName : room.adjacentTo()) {
                RoomConstraint adj = roomMap.get(adjName);
                if (adj == null) {
                    return SolvedLayout.infeasible(
                        "Room '" + room.name() + "' has adjacent constraint to unknown room '" + adjName + "'");
                }
                addAdjacentConstraint(model, xVars, yVars, room, adj);
            }

            // Not-adjacent constraints
            for (String notAdjName : room.notAdjacentTo()) {
                RoomConstraint notAdj = roomMap.get(notAdjName);
                if (notAdj == null) {
                    return SolvedLayout.infeasible(
                        "Room '" + room.name() + "' has not_adjacent constraint to unknown room '" + notAdjName + "'");
                }
                addNotAdjacentConstraint(model, xVars, yVars, room, notAdj);
            }

            // Exterior wall constraint
            if (room.exteriorWall() != null) {
                addExteriorConstraint(model, xVars, yVars, room, maxGridWidth, maxGridHeight);
            }
        }

        // Solve with pinned search strategy for determinism
        // DETERMINISM FIX: Explicitly set search strategy (Watchdog ruling 2026-02-02)
        // Choco's default strategy is version-dependent; pinning ensures reproducible layouts
        Solver solver = model.getSolver();
        IntVar[] allVars = new IntVar[xVars.size() + yVars.size()];
        int idx = 0;
        for (RoomConstraint room : constraints) {
            allVars[idx++] = xVars.get(room.name());
            allVars[idx++] = yVars.get(room.name());
        }
        solver.setSearch(Search.inputOrderLBSearch(allVars));

        if (solver.solve()) {
            long elapsed = System.currentTimeMillis() - startTime;

            Map<String, GridPosition> positions = new HashMap<>();
            for (RoomConstraint room : constraints) {
                int x = xVars.get(room.name()).getValue();
                int y = yVars.get(room.name()).getValue();
                positions.put(room.name(), new GridPosition(x, y));
            }

            return SolvedLayout.success(positions, maxGridWidth, maxGridHeight, elapsed);
        } else {
            return SolvedLayout.infeasible("No solution found - constraints may be contradictory");
        }
    }

    /**
     * Phase 25: Solve with automatic constraint relaxation.
     * If initial solve fails, progressively relax constraints in priority order.
     *
     * Relaxation order (first to drop):
     * 1. NOT_ADJACENT - separation requirements
     * 2. ALIGNS - vertical alignment
     * 3. EXTERIOR - specific wall placement
     * 4. ADJACENT - connectivity (last resort)
     */
    public SolvedLayout solveWithRelaxation(List<RoomConstraint> constraints,
                                            int maxGridWidth, int maxGridHeight) {
        // First try strict solve
        SolvedLayout strict = solve(constraints, maxGridWidth, maxGridHeight);
        if (strict.feasible()) {
            return strict;
        }

        // Log the conflict
        OutlierLogger.logUnsatisfiable(
            formatConstraintSummary(constraints),
            "Constraint solving",
            "Attempting relaxation");

        List<String> droppedConstraints = new ArrayList<>();
        List<RoomConstraint> relaxedConstraints = new ArrayList<>(constraints);
        long totalTime = 0;

        // Relaxation order
        ConstraintType[] relaxOrder = {
            ConstraintType.NOT_ADJACENT,
            ConstraintType.ALIGNS,
            ConstraintType.EXTERIOR,
            ConstraintType.ADJACENT
        };

        for (ConstraintType typeToRelax : relaxOrder) {
            List<RoomConstraint> afterRelax = relaxConstraintType(relaxedConstraints, typeToRelax);

            // Check if anything was actually relaxed
            if (afterRelax.equals(relaxedConstraints)) {
                continue; // No constraints of this type to relax
            }

            String relaxedDesc = typeToRelax.name() + " constraints";
            droppedConstraints.add(relaxedDesc);
            relaxedConstraints = afterRelax;

            long startTime = System.currentTimeMillis();
            SolvedLayout result = solve(relaxedConstraints, maxGridWidth, maxGridHeight);
            totalTime += System.currentTimeMillis() - startTime;

            if (result.feasible()) {
                OutlierLogger.log(OutlierLogger.OutlierCategory.UNSATISFIABLE,
                    "Dropped: " + String.join(", ", droppedConstraints),
                    "Constraint relaxation",
                    "Solved with relaxed constraints",
                    "Original constraints were over-constrained");

                return SolvedLayout.successWithDropped(
                    result.positions(), maxGridWidth, maxGridHeight,
                    totalTime, droppedConstraints);
            }
        }

        // Cannot solve even fully relaxed
        OutlierLogger.log(OutlierLogger.OutlierCategory.UNSATISFIABLE,
            "All constraints relaxed",
            "Constraint solving",
            "FAILED - cannot solve even with full relaxation",
            "Layout is fundamentally impossible - review room sizes and grid dimensions");

        return SolvedLayout.infeasible(
            "Cannot solve even with full relaxation - review room sizes");
    }

    /**
     * Remove constraints of a specific type from all rooms.
     * Phase 47D: Preserves zone bounds during relaxation.
     */
    private List<RoomConstraint> relaxConstraintType(List<RoomConstraint> constraints,
                                                      ConstraintType type) {
        List<RoomConstraint> relaxed = new ArrayList<>();

        for (RoomConstraint room : constraints) {
            switch (type) {
                case NOT_ADJACENT -> relaxed.add(new RoomConstraint(
                    room.name(), room.widthMeters(), room.depthMeters(),
                    room.adjacentTo(), List.of(), room.exteriorWall(),
                    room.zoneMinX(), room.zoneMaxX(), room.zoneMinY(), room.zoneMaxY())); // Clear notAdjacentTo
                case ADJACENT -> relaxed.add(new RoomConstraint(
                    room.name(), room.widthMeters(), room.depthMeters(),
                    List.of(), room.notAdjacentTo(), room.exteriorWall(),
                    room.zoneMinX(), room.zoneMaxX(), room.zoneMinY(), room.zoneMaxY())); // Clear adjacentTo
                case EXTERIOR -> relaxed.add(new RoomConstraint(
                    room.name(), room.widthMeters(), room.depthMeters(),
                    room.adjacentTo(), room.notAdjacentTo(), null,
                    room.zoneMinX(), room.zoneMaxX(), room.zoneMinY(), room.zoneMaxY())); // Clear exteriorWall
                default -> relaxed.add(room); // Keep unchanged
            }
        }

        return relaxed;
    }

    /**
     * Format constraint summary for logging.
     */
    private String formatConstraintSummary(List<RoomConstraint> constraints) {
        StringBuilder sb = new StringBuilder();
        int adjacentCount = 0, notAdjacentCount = 0, exteriorCount = 0;

        for (RoomConstraint room : constraints) {
            adjacentCount += room.adjacentTo().size();
            notAdjacentCount += room.notAdjacentTo().size();
            if (room.exteriorWall() != null) exteriorCount++;
        }

        sb.append(constraints.size()).append(" rooms, ");
        sb.append(adjacentCount).append(" adjacent, ");
        sb.append(notAdjacentCount).append(" not_adjacent, ");
        sb.append(exteriorCount).append(" exterior");

        return sb.toString();
    }

    private void addNonOverlapConstraint(Model model, Map<String, IntVar> xVars,
                                         Map<String, IntVar> yVars,
                                         RoomConstraint r1, RoomConstraint r2) {
        IntVar x1 = xVars.get(r1.name());
        IntVar y1 = yVars.get(r1.name());
        IntVar x2 = xVars.get(r2.name());
        IntVar y2 = yVars.get(r2.name());

        IntVar x1End = model.intOffsetView(x1, r1.widthMeters());
        IntVar y1End = model.intOffsetView(y1, r1.depthMeters());
        IntVar x2End = model.intOffsetView(x2, r2.widthMeters());
        IntVar y2End = model.intOffsetView(y2, r2.depthMeters());

        Constraint leftOf = model.arithm(x1End, "<=", x2);
        Constraint rightOf = model.arithm(x2End, "<=", x1);
        Constraint below = model.arithm(y1End, "<=", y2);
        Constraint above = model.arithm(y2End, "<=", y1);

        model.or(leftOf, rightOf, below, above).post();
    }

    private void addAdjacentConstraint(Model model, Map<String, IntVar> xVars,
                                       Map<String, IntVar> yVars,
                                       RoomConstraint r1, RoomConstraint r2) {
        IntVar x1 = xVars.get(r1.name());
        IntVar y1 = yVars.get(r1.name());
        IntVar x2 = xVars.get(r2.name());
        IntVar y2 = yVars.get(r2.name());

        IntVar x1End = model.intOffsetView(x1, r1.widthMeters());
        IntVar x2End = model.intOffsetView(x2, r2.widthMeters());
        IntVar y1End = model.intOffsetView(y1, r1.depthMeters());
        IntVar y2End = model.intOffsetView(y2, r2.depthMeters());

        // Touch on right of r1 (r1 left of r2)
        Constraint touchRight = model.and(
            model.arithm(x1End, "=", x2),
            model.arithm(y1, "<", y2End),
            model.arithm(y1End, ">", y2)
        );

        // Touch on left of r1 (r1 right of r2)
        Constraint touchLeft = model.and(
            model.arithm(x2End, "=", x1),
            model.arithm(y1, "<", y2End),
            model.arithm(y1End, ">", y2)
        );

        // Touch on top of r1 (r1 below r2)
        Constraint touchAbove = model.and(
            model.arithm(y1End, "=", y2),
            model.arithm(x1, "<", x2End),
            model.arithm(x1End, ">", x2)
        );

        // Touch on bottom of r1 (r1 above r2)
        Constraint touchBelow = model.and(
            model.arithm(y2End, "=", y1),
            model.arithm(x1, "<", x2End),
            model.arithm(x1End, ">", x2)
        );

        model.or(touchRight, touchLeft, touchAbove, touchBelow).post();
    }

    private void addNotAdjacentConstraint(Model model, Map<String, IntVar> xVars,
                                          Map<String, IntVar> yVars,
                                          RoomConstraint r1, RoomConstraint r2) {
        IntVar x1 = xVars.get(r1.name());
        IntVar y1 = yVars.get(r1.name());
        IntVar x2 = xVars.get(r2.name());
        IntVar y2 = yVars.get(r2.name());

        IntVar x1End = model.intOffsetView(x1, r1.widthMeters());
        IntVar x2End = model.intOffsetView(x2, r2.widthMeters());
        IntVar y1End = model.intOffsetView(y1, r1.depthMeters());
        IntVar y2End = model.intOffsetView(y2, r2.depthMeters());

        // Gap required - strict inequality means at least 1m gap or no overlap in other axis
        Constraint gapLeft = model.arithm(x1End, "<", x2);
        Constraint gapRight = model.arithm(x2End, "<", x1);
        Constraint gapBelow = model.arithm(y1End, "<", y2);
        Constraint gapAbove = model.arithm(y2End, "<", y1);

        model.or(gapLeft, gapRight, gapBelow, gapAbove).post();
    }

    private void addExteriorConstraint(Model model, Map<String, IntVar> xVars,
                                       Map<String, IntVar> yVars,
                                       RoomConstraint room, int maxWidth, int maxHeight) {
        IntVar x = xVars.get(room.name());
        IntVar y = yVars.get(room.name());

        switch (room.exteriorWall().toLowerCase()) {
            case "south" -> model.arithm(y, "=", 0).post();
            case "north" -> model.arithm(y, "=", maxHeight - room.depthMeters()).post();
            case "west" -> model.arithm(x, "=", 0).post();
            case "east" -> model.arithm(x, "=", maxWidth - room.widthMeters()).post();
        }
    }

    /**
     * Convert solved position to grid reference (A1, B2, etc.)
     */
    public static String toGridRef(GridPosition pos) {
        char col = (char) ('A' + pos.x());
        int row = pos.y() + 1;
        return "" + col + row;
    }

    /**
     * Print ASCII visualization of solved layout.
     */
    public static void printLayout(SolvedLayout layout, List<RoomConstraint> constraints) {
        if (!layout.feasible()) {
            System.out.println("No layout - " + layout.failureReason());
            return;
        }

        char[][] grid = new char[layout.gridHeight()][layout.gridWidth()];
        for (int y = 0; y < layout.gridHeight(); y++) {
            Arrays.fill(grid[y], '.');
        }

        char label = 'A';
        Map<String, Character> labels = new HashMap<>();

        for (RoomConstraint room : constraints) {
            GridPosition pos = layout.positions().get(room.name());
            labels.put(room.name(), label);

            for (int dy = 0; dy < room.depthMeters(); dy++) {
                for (int dx = 0; dx < room.widthMeters(); dx++) {
                    int gx = pos.x() + dx;
                    int gy = pos.y() + dy;
                    if (gx < layout.gridWidth() && gy < layout.gridHeight()) {
                        grid[gy][gx] = label;
                    }
                }
            }
            label++;
        }

        // Print (Y=0 at bottom)
        System.out.println("+" + "-".repeat(layout.gridWidth()) + "+");
        for (int y = layout.gridHeight() - 1; y >= 0; y--) {
            System.out.print("|");
            for (int x = 0; x < layout.gridWidth(); x++) {
                System.out.print(grid[y][x]);
            }
            System.out.println("|");
        }
        System.out.println("+" + "-".repeat(layout.gridWidth()) + "+");

        // Legend
        for (RoomConstraint room : constraints) {
            GridPosition pos = layout.positions().get(room.name());
            System.out.printf("%c = %s (%dx%dm at %d,%d → %s)%n",
                labels.get(room.name()), room.name(),
                room.widthMeters(), room.depthMeters(),
                pos.x(), pos.y(), toGridRef(pos));
        }

        System.out.printf("Solve time: %dms%n", layout.solveTimeMs());
    }
}
