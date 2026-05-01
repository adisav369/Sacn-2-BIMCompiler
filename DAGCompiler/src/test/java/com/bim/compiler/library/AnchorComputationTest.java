/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.library;

import com.bim.compiler.coordinate.LocalCoord;
import com.bim.compiler.coordinate.StoreyCoord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wall anchor computation — verifies computeZoneAnchor() produces correct
 * StoreyCoord for each wall direction with known room bounds.
 *
 * <p>Tests the method that converts (wall name + room bounds) → StoreyCoord anchor.
 * This is the entry point to the placement chain: if the anchor is wrong,
 * every child placement downstream is wrong.
 *
 * <p>Uses package-private access to BOMTierResolver.computeZoneAnchor().
 */
@DisplayName("Anchor Computation — Wall Anchor from Room Bounds")
class AnchorComputationTest {

    private static final double EPS = 1e-9;
    private static final double WALL_OFFSET = 0.5;

    // Room bounds: (0, 0) to (10, 8), floorZ = 3.0
    private static final double MIN_X = 0, MIN_Y = 0, MAX_X = 10, MAX_Y = 8;
    private static final double FLOOR_Z = 3.0;
    private static final double CX = 5.0;  // (0 + 10) / 2
    private static final double CY = 4.0;  // (0 + 8) / 2

    private final BOMTierResolver resolver = new BOMTierResolver();

    @Test
    @DisplayName("A1: North wall → centroid X, maxY - offset, π")
    void northWall_anchor() {
        StoreyCoord a = resolver.computeZoneAnchor(
            "north", WALL_OFFSET, MIN_X, MIN_Y, MAX_X, MAX_Y, FLOOR_Z);

        assertEquals(CX, a.x(), EPS);
        assertEquals(MAX_Y - WALL_OFFSET, a.y(), EPS);  // 8.0 - 0.5 = 7.5
        assertEquals(FLOOR_Z, a.z(), EPS);
        assertEquals(Math.PI, a.rotation(), EPS);
    }

    @Test
    @DisplayName("A2: South wall → centroid X, minY + offset, 0")
    void southWall_anchor() {
        StoreyCoord a = resolver.computeZoneAnchor(
            "south", WALL_OFFSET, MIN_X, MIN_Y, MAX_X, MAX_Y, FLOOR_Z);

        assertEquals(CX, a.x(), EPS);
        assertEquals(MIN_Y + WALL_OFFSET, a.y(), EPS);  // 0 + 0.5 = 0.5
        assertEquals(FLOOR_Z, a.z(), EPS);
        assertEquals(0.0, a.rotation(), EPS);
    }

    @Test
    @DisplayName("A3: East wall → maxX - offset, centroid Y, π/2")
    void eastWall_anchor() {
        StoreyCoord a = resolver.computeZoneAnchor(
            "east", WALL_OFFSET, MIN_X, MIN_Y, MAX_X, MAX_Y, FLOOR_Z);

        assertEquals(MAX_X - WALL_OFFSET, a.x(), EPS);  // 10 - 0.5 = 9.5
        assertEquals(CY, a.y(), EPS);
        assertEquals(FLOOR_Z, a.z(), EPS);
        assertEquals(Math.PI / 2, a.rotation(), EPS);
    }

    @Test
    @DisplayName("A4: West wall → minX + offset, centroid Y, -π/2")
    void westWall_anchor() {
        StoreyCoord a = resolver.computeZoneAnchor(
            "west", WALL_OFFSET, MIN_X, MIN_Y, MAX_X, MAX_Y, FLOOR_Z);

        assertEquals(MIN_X + WALL_OFFSET, a.x(), EPS);  // 0 + 0.5 = 0.5
        assertEquals(CY, a.y(), EPS);
        assertEquals(FLOOR_Z, a.z(), EPS);
        assertEquals(-Math.PI / 2, a.rotation(), EPS);
    }

    @Test
    @DisplayName("A5: Center → centroid, centroid, 0")
    void center_anchor() {
        StoreyCoord a = resolver.computeZoneAnchor(
            "center", WALL_OFFSET, MIN_X, MIN_Y, MAX_X, MAX_Y, FLOOR_Z);

        assertEquals(CX, a.x(), EPS);
        assertEquals(CY, a.y(), EPS);
        assertEquals(FLOOR_Z, a.z(), EPS);
        assertEquals(0.0, a.rotation(), EPS);
    }
}
