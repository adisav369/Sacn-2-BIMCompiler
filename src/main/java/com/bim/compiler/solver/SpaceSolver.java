package com.bim.compiler.solver;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.constraints.Constraint;

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
     */
    public record RoomConstraint(
        String name,
        int widthMeters,
        int depthMeters,
        List<String> adjacentTo,      // Must share wall with these rooms
        List<String> notAdjacentTo,   // Cannot share wall with these rooms
        String exteriorWall           // null, or "north"/"south"/"east"/"west"
    ) {
        public RoomConstraint(String name, int widthMeters, int depthMeters) {
            this(name, widthMeters, depthMeters, List.of(), List.of(), null);
        }
    }

    /**
     * Solved position for a room.
     */
    public record GridPosition(int x, int y) {}

    /**
     * Solution output.
     */
    public record SolvedLayout(
        Map<String, GridPosition> positions,
        int gridWidth,
        int gridHeight,
        boolean feasible,
        String failureReason,
        long solveTimeMs
    ) {
        public static SolvedLayout infeasible(String reason) {
            return new SolvedLayout(Map.of(), 0, 0, false, reason, 0);
        }

        public static SolvedLayout success(Map<String, GridPosition> positions,
                                           int gridWidth, int gridHeight, long solveTimeMs) {
            return new SolvedLayout(positions, gridWidth, gridHeight, true, null, solveTimeMs);
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
            int maxX = maxGridWidth - room.widthMeters();
            int maxY = maxGridHeight - room.depthMeters();

            if (maxX < 0 || maxY < 0) {
                return SolvedLayout.infeasible(
                    "Room '" + room.name() + "' (" + room.widthMeters() + "x" + room.depthMeters() +
                    "m) doesn't fit in grid (" + maxGridWidth + "x" + maxGridHeight + "m)");
            }

            IntVar x = model.intVar(room.name() + "_x", 0, maxX);
            IntVar y = model.intVar(room.name() + "_y", 0, maxY);
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

        // Solve
        Solver solver = model.getSolver();

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
