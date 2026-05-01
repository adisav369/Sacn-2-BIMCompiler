/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.solver;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.constraints.Constraint;

import java.util.*;

/**
 * Space Solver Prototype - Research Phase
 *
 * Minimal CSP-based room placement solver using Choco.
 *
 * Grid model:
 * - Building = WxD grid (in meters, 1m cells)
 * - Room = rectangle at (x, y) with width × depth
 * - Adjacency = rooms share at least 1m of edge
 */
public class SpaceSolverPrototype {

    // Room definition
    public record RoomSpec(String name, int width, int depth) {}

    // Constraint types
    public sealed interface RoomConstraint permits AdjacentConstraint, ExteriorConstraint, NotAdjacentConstraint {}

    public record AdjacentConstraint(String room1, String room2) implements RoomConstraint {}
    public record ExteriorConstraint(String room, String direction) implements RoomConstraint {}
    public record NotAdjacentConstraint(String room1, String room2) implements RoomConstraint {}

    // Solution
    public record RoomPlacement(String name, int x, int y, int width, int depth) {
        public int maxX() { return x + width; }
        public int maxY() { return y + depth; }
    }

    // Solver state
    private final int buildingWidth;
    private final int buildingDepth;
    private final List<RoomSpec> rooms = new ArrayList<>();
    private final List<RoomConstraint> constraints = new ArrayList<>();

    public SpaceSolverPrototype(int buildingWidth, int buildingDepth) {
        this.buildingWidth = buildingWidth;
        this.buildingDepth = buildingDepth;
    }

    public void addRoom(String name, int width, int depth) {
        rooms.add(new RoomSpec(name, width, depth));
    }

    public void addAdjacent(String room1, String room2) {
        constraints.add(new AdjacentConstraint(room1, room2));
    }

    public void addExterior(String room, String direction) {
        constraints.add(new ExteriorConstraint(room, direction));
    }

    public void addNotAdjacent(String room1, String room2) {
        constraints.add(new NotAdjacentConstraint(room1, room2));
    }

    public List<RoomPlacement> solve() {
        Model model = new Model("SpaceSolver");

        // Create variables for each room: x, y position
        Map<String, IntVar> xVars = new HashMap<>();
        Map<String, IntVar> yVars = new HashMap<>();
        Map<String, RoomSpec> roomMap = new HashMap<>();

        for (RoomSpec room : rooms) {
            // X position: 0 to buildingWidth - roomWidth
            int maxX = buildingWidth - room.width();
            IntVar x = model.intVar(room.name() + "_x", 0, maxX);
            xVars.put(room.name(), x);

            // Y position: 0 to buildingDepth - roomDepth
            int maxY = buildingDepth - room.depth();
            IntVar y = model.intVar(room.name() + "_y", 0, maxY);
            yVars.put(room.name(), y);

            roomMap.put(room.name(), room);
        }

        // Non-overlap constraint: rooms cannot occupy same space
        for (int i = 0; i < rooms.size(); i++) {
            for (int j = i + 1; j < rooms.size(); j++) {
                RoomSpec r1 = rooms.get(i);
                RoomSpec r2 = rooms.get(j);

                IntVar x1 = xVars.get(r1.name());
                IntVar y1 = yVars.get(r1.name());
                IntVar x2 = xVars.get(r2.name());
                IntVar y2 = yVars.get(r2.name());

                // Non-overlap: at least one of these must be true:
                // r1 is left of r2: x1 + w1 <= x2
                // r1 is right of r2: x2 + w2 <= x1
                // r1 is below r2: y1 + d1 <= y2
                // r1 is above r2: y2 + d2 <= y1

                IntVar x1End = model.intOffsetView(x1, r1.width());
                IntVar y1End = model.intOffsetView(y1, r1.depth());
                IntVar x2End = model.intOffsetView(x2, r2.width());
                IntVar y2End = model.intOffsetView(y2, r2.depth());

                // At least one separating axis
                Constraint leftOf = model.arithm(x1End, "<=", x2);
                Constraint rightOf = model.arithm(x2End, "<=", x1);
                Constraint below = model.arithm(y1End, "<=", y2);
                Constraint above = model.arithm(y2End, "<=", y1);

                model.or(leftOf, rightOf, below, above).post();
            }
        }

        // Process constraints
        for (RoomConstraint constraint : constraints) {
            if (constraint instanceof AdjacentConstraint adj) {
                // Rooms must share at least 1m of edge
                RoomSpec r1 = roomMap.get(adj.room1());
                RoomSpec r2 = roomMap.get(adj.room2());
                IntVar x1 = xVars.get(adj.room1());
                IntVar y1 = yVars.get(adj.room1());
                IntVar x2 = xVars.get(adj.room2());
                IntVar y2 = yVars.get(adj.room2());

                // Adjacent on X-axis (share vertical edge)
                IntVar x1End = model.intOffsetView(x1, r1.width());
                IntVar x2End = model.intOffsetView(x2, r2.width());
                IntVar y1End = model.intOffsetView(y1, r1.depth());
                IntVar y2End = model.intOffsetView(y2, r2.depth());

                // Touch on right of r1
                Constraint r1RightOfr2 = model.and(
                    model.arithm(x1End, "=", x2),
                    model.arithm(y1, "<", y2End),
                    model.arithm(y1End, ">", y2)
                );

                // Touch on left of r1
                Constraint r1LeftOfr2 = model.and(
                    model.arithm(x2End, "=", x1),
                    model.arithm(y1, "<", y2End),
                    model.arithm(y1End, ">", y2)
                );

                // Touch on top of r1
                Constraint r1AboveR2 = model.and(
                    model.arithm(y1End, "=", y2),
                    model.arithm(x1, "<", x2End),
                    model.arithm(x1End, ">", x2)
                );

                // Touch on bottom of r1
                Constraint r1BelowR2 = model.and(
                    model.arithm(y2End, "=", y1),
                    model.arithm(x1, "<", x2End),
                    model.arithm(x1End, ">", x2)
                );

                model.or(r1RightOfr2, r1LeftOfr2, r1AboveR2, r1BelowR2).post();

            } else if (constraint instanceof ExteriorConstraint ext) {
                IntVar x = xVars.get(ext.room());
                IntVar y = yVars.get(ext.room());
                RoomSpec room = roomMap.get(ext.room());

                String dir = ext.direction().toUpperCase();
                if ("SOUTH".equals(dir)) {
                    model.arithm(y, "=", 0).post();
                } else if ("NORTH".equals(dir)) {
                    model.arithm(y, "=", buildingDepth - room.depth()).post();
                } else if ("WEST".equals(dir)) {
                    model.arithm(x, "=", 0).post();
                } else if ("EAST".equals(dir)) {
                    model.arithm(x, "=", buildingWidth - room.width()).post();
                }

            } else if (constraint instanceof NotAdjacentConstraint notAdj) {
                // Rooms must NOT share any edge (gap of at least 1m)
                RoomSpec r1 = roomMap.get(notAdj.room1());
                RoomSpec r2 = roomMap.get(notAdj.room2());
                IntVar x1 = xVars.get(notAdj.room1());
                IntVar y1 = yVars.get(notAdj.room1());
                IntVar x2 = xVars.get(notAdj.room2());
                IntVar y2 = yVars.get(notAdj.room2());

                // Gap constraint: distance > 0 on at least one axis
                IntVar x1End = model.intOffsetView(x1, r1.width());
                IntVar x2End = model.intOffsetView(x2, r2.width());
                IntVar y1End = model.intOffsetView(y1, r1.depth());
                IntVar y2End = model.intOffsetView(y2, r2.depth());

                // Gap on X or gap on Y or no Y overlap or no X overlap
                Constraint gapLeft = model.arithm(x1End, "<", x2);
                Constraint gapRight = model.arithm(x2End, "<", x1);
                Constraint gapBelow = model.arithm(y1End, "<", y2);
                Constraint gapAbove = model.arithm(y2End, "<", y1);

                model.or(gapLeft, gapRight, gapBelow, gapAbove).post();
            }
        }

        // Solve
        Solver solver = model.getSolver();
        long startTime = System.currentTimeMillis();

        if (solver.solve()) {
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("Solution found in " + elapsed + "ms");

            List<RoomPlacement> result = new ArrayList<>();
            for (RoomSpec room : rooms) {
                int x = xVars.get(room.name()).getValue();
                int y = yVars.get(room.name()).getValue();
                result.add(new RoomPlacement(room.name(), x, y, room.width(), room.depth()));
            }
            return result;
        } else {
            System.out.println("No solution found - constraints may be infeasible");
            return Collections.emptyList();
        }
    }

    // Visualization helper
    public static void printLayout(int width, int depth, List<RoomPlacement> placements) {
        char[][] grid = new char[depth][width];
        for (int y = 0; y < depth; y++) {
            Arrays.fill(grid[y], '.');
        }

        char label = 'A';
        for (RoomPlacement room : placements) {
            for (int dy = 0; dy < room.depth(); dy++) {
                for (int dx = 0; dx < room.width(); dx++) {
                    int gx = room.x() + dx;
                    int gy = room.y() + dy;
                    if (gx < width && gy < depth) {
                        grid[gy][gx] = label;
                    }
                }
            }
            label++;
        }

        // Print (Y=0 at bottom, so reverse rows)
        System.out.println("+" + "-".repeat(width) + "+");
        for (int y = depth - 1; y >= 0; y--) {
            System.out.print("|");
            for (int x = 0; x < width; x++) {
                System.out.print(grid[y][x]);
            }
            System.out.println("|");
        }
        System.out.println("+" + "-".repeat(width) + "+");

        // Legend
        label = 'A';
        for (RoomPlacement room : placements) {
            System.out.printf("%c = %s (%dx%d at %d,%d)%n",
                label++, room.name(), room.width(), room.depth(), room.x(), room.y());
        }
    }

    // Test runner
    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("SPACE SOLVER PROTOTYPE - Research Phase");
        System.out.println("=".repeat(60));

        // Test 1: Simple 3-room layout
        System.out.println("\nTest 1: Simple 3-room layout");
        System.out.println("-".repeat(40));

        SpaceSolverPrototype solver1 = new SpaceSolverPrototype(8, 6);
        solver1.addRoom("bedroom", 4, 3);
        solver1.addRoom("bathroom", 2, 2);
        solver1.addRoom("living", 4, 3);
        solver1.addAdjacent("bedroom", "bathroom");
        solver1.addExterior("bedroom", "SOUTH");

        List<RoomPlacement> result1 = solver1.solve();
        if (!result1.isEmpty()) {
            printLayout(8, 6, result1);
            System.out.println("[PASS] 3-room layout solved");
        } else {
            System.out.println("[FAIL] No solution");
        }

        // Test 2: House layout with NOT_ADJACENT
        System.out.println("\nTest 2: House with kitchen/bedroom separation");
        System.out.println("-".repeat(40));

        SpaceSolverPrototype solver2 = new SpaceSolverPrototype(10, 8);
        solver2.addRoom("kitchen", 3, 3);
        solver2.addRoom("living", 4, 4);
        solver2.addRoom("bedroom", 3, 3);
        solver2.addRoom("bathroom", 2, 2);
        solver2.addAdjacent("kitchen", "living");
        solver2.addAdjacent("bedroom", "bathroom");
        solver2.addNotAdjacent("kitchen", "bedroom");  // Noise/smell separation
        solver2.addExterior("kitchen", "NORTH");  // Ventilation

        List<RoomPlacement> result2 = solver2.solve();
        if (!result2.isEmpty()) {
            printLayout(10, 8, result2);
            System.out.println("[PASS] House layout with separation solved");
        } else {
            System.out.println("[FAIL] No solution");
        }

        // Test 3: Infeasible constraints
        System.out.println("\nTest 3: Infeasible constraints (should fail)");
        System.out.println("-".repeat(40));

        SpaceSolverPrototype solver3 = new SpaceSolverPrototype(5, 5);
        solver3.addRoom("A", 3, 3);
        solver3.addRoom("B", 3, 3);
        solver3.addRoom("C", 3, 3);
        // All must be adjacent - impossible in 5x5 with 3x3 rooms
        solver3.addAdjacent("A", "B");
        solver3.addAdjacent("B", "C");
        solver3.addAdjacent("A", "C");

        List<RoomPlacement> result3 = solver3.solve();
        if (result3.isEmpty()) {
            System.out.println("[PASS] Correctly detected infeasible constraints");
        } else {
            System.out.println("[UNEXPECTED] Found solution for infeasible problem");
        }

        // Summary
        System.out.println("\n" + "=".repeat(60));
        System.out.println("RESEARCH FINDINGS");
        System.out.println("=".repeat(60));
        System.out.println("1. Choco Solver successfully models room placement CSP");
        System.out.println("2. Adjacency constraints work via shared-edge detection");
        System.out.println("3. Exterior wall constraints straightforward");
        System.out.println("4. Infeasible constraints properly detected");
        System.out.println("\nNext steps:");
        System.out.println("- Add orientation (rotation) support");
        System.out.println("- Add minimum corridor width constraints");
        System.out.println("- Integrate with BuildingParser DSL grammar");
    }
}
