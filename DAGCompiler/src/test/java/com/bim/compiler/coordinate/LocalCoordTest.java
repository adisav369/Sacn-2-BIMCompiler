/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.coordinate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure math verification of LocalCoord — the BOM child offset accumulation chain.
 *
 * <p>Tests the single extraction point ({@link LocalCoord#fromBOMChild}) and the
 * accumulation formula ({@link LocalCoord#toWorld(StoreyCoord)}). No DB, no files,
 * no side effects — deterministic numerical checks only.
 *
 * <p>Tolerance: 1e-9 (pure math, no float32 involvement).
 */
@DisplayName("LocalCoord — Geometry Translation Math")
class LocalCoordTest {

    private static final double EPS = 1e-9;

    // ── toWorld accumulation tests ────────────────────────────────────────────

    @Test
    @DisplayName("T1: Zero offset → world = anchor")
    void zeroOffset_returnsAnchor() {
        StoreyCoord anchor = new StoreyCoord(5.0, 3.0, 1.0, 0);
        LocalCoord offset = LocalCoord.zero();
        WorldCoord w = offset.toWorld(anchor);

        assertEquals(5.0, w.x(), EPS);
        assertEquals(3.0, w.y(), EPS);
        assertEquals(1.0, w.z(), EPS);
        assertEquals(0.0, w.rotation(), EPS);
    }

    @Test
    @DisplayName("T2: dx-only with zero rotation → world.x shifted")
    void dxOnly_zeroRotation_shiftsX() {
        StoreyCoord anchor = new StoreyCoord(2.0, 3.0, 0, 0);
        LocalCoord offset = new LocalCoord(1.5, 0, 0, 0);
        WorldCoord w = offset.toWorld(anchor);

        assertEquals(3.5, w.x(), EPS);  // 2.0 + 1.5
        assertEquals(3.0, w.y(), EPS);  // unchanged
    }

    @Test
    @DisplayName("T3: dx-only with π rotation → world.x shifted NEGATIVELY")
    void dxOnly_piRotation_flipsX() {
        StoreyCoord anchor = new StoreyCoord(5.0, 3.0, 0, Math.PI);
        LocalCoord offset = new LocalCoord(1.0, 0, 0, 0);
        WorldCoord w = offset.toWorld(anchor);

        // cos(π) = -1, sin(π) ≈ 0
        assertEquals(4.0, w.x(), EPS);  // 5.0 + 1.0 * cos(π) = 5.0 - 1.0
        assertEquals(3.0, w.y(), 1e-6); // 3.0 + 1.0 * sin(π) ≈ 3.0
    }

    @Test
    @DisplayName("T4: dx+dy with π/2 rotation → axes swapped")
    void dxDy_halfPiRotation_swapsAxes() {
        StoreyCoord anchor = new StoreyCoord(0, 0, 0, Math.PI / 2);
        LocalCoord offset = new LocalCoord(2.0, 1.0, 0, 0);
        WorldCoord w = offset.toWorld(anchor);

        // cos(π/2) ≈ 0, sin(π/2) = 1
        // world.x = 0 + 2*cos(π/2) - 1*sin(π/2) = 0 - 1 = -1
        // world.y = 0 + 2*sin(π/2) + 1*cos(π/2) = 2 + 0 = 2
        assertEquals(-1.0, w.x(), 1e-6);
        assertEquals( 2.0, w.y(), 1e-6);
    }

    @Test
    @DisplayName("T5: dz adds to parent.z (no rotation effect)")
    void dz_addsToParentZ() {
        StoreyCoord anchor = new StoreyCoord(0, 0, 3.0, Math.PI / 4);
        LocalCoord offset = new LocalCoord(0, 0, 1.5, 0);
        WorldCoord w = offset.toWorld(anchor);

        assertEquals(4.5, w.z(), EPS);  // 3.0 + 1.5
    }

    @Test
    @DisplayName("T6: rotation accumulates")
    void rotation_accumulates() {
        StoreyCoord anchor = new StoreyCoord(0, 0, 0, Math.PI / 4);
        LocalCoord offset = new LocalCoord(0, 0, 0, Math.PI / 4);
        WorldCoord w = offset.toWorld(anchor);

        assertEquals(Math.PI / 2, w.rotation(), EPS);  // π/4 + π/4
    }

    // ── fromBOMChild extraction tests ─────────────────────────────────────────

    @Test
    @DisplayName("T7: fromBOMChild with semantic rotation_rule resolves correctly")
    void fromBOMChild_semanticRotation_resolvesCorrectly() {
        // FACE_INTO_ROOM against south wall → rotation = 0 (south = 0)
        LocalCoord south = LocalCoord.fromBOMChild(1.0, 0.5, 0.3, "FACE_INTO_ROOM", "south");
        assertEquals(0, south.rotation(), EPS);

        // FACE_INTO_ROOM against north wall → rotation = π
        LocalCoord north = LocalCoord.fromBOMChild(1.0, 0.5, 0.3, "FACE_INTO_ROOM", "north");
        assertEquals(Math.PI, north.rotation(), EPS);

        // FACE_INTO_ROOM against east wall → rotation = π/2
        LocalCoord east = LocalCoord.fromBOMChild(1.0, 0.5, 0.3, "FACE_INTO_ROOM", "east");
        assertEquals(Math.PI / 2, east.rotation(), EPS);

        // PARALLEL_TO_WALL against south wall → rotation = π/2
        LocalCoord parallel = LocalCoord.fromBOMChild(0, 0, 0, "PARALLEL_TO_WALL", "south");
        assertEquals(Math.PI / 2, parallel.rotation(), EPS);
    }

    @Test
    @DisplayName("T8: fromBOMChild with literal radian string parses correctly")
    void fromBOMChild_literalRadians_parsesCorrectly() {
        LocalCoord lc = LocalCoord.fromBOMChild(0, 0, 0, "3.14159265", null);
        assertEquals(3.14159265, lc.rotation(), EPS);

        LocalCoord half = LocalCoord.fromBOMChild(0, 0, 0, "1.5708", null);
        assertEquals(1.5708, half.rotation(), EPS);
    }

    @Test
    @DisplayName("T9: fromBOMChild with null rotation_rule → 0")
    void fromBOMChild_nullRotation_returnsZero() {
        LocalCoord lc = LocalCoord.fromBOMChild(1.0, 2.0, 3.0, null, "south");
        assertEquals(0.0, lc.rotation(), EPS);
        assertEquals(1.0, lc.dx(), EPS);
        assertEquals(2.0, lc.dy(), EPS);
        assertEquals(3.0, lc.dz(), EPS);
    }

    @Test
    @DisplayName("T10: fromBOMChild with unknown semantic → 0 (fail-safe)")
    void fromBOMChild_unknownSemantic_returnsZero() {
        LocalCoord lc = LocalCoord.fromBOMChild(0, 0, 0, "UNKNOWN_RULE", "south");
        assertEquals(0.0, lc.rotation(), EPS);
    }

    // ── resolveRotation edge cases ────────────────────────────────────────────

    @Test
    @DisplayName("T7b: FACE_AWAY_FROM_WALL treated same as FACE_INTO_ROOM")
    void faceAwayFromWall_sameAsFaceIntoRoom() {
        assertEquals(
            LocalCoord.resolveRotation("FACE_INTO_ROOM", "north"),
            LocalCoord.resolveRotation("FACE_AWAY_FROM_WALL", "north"),
            EPS);
    }

    @Test
    @DisplayName("T7c: Semantic rotation without wall context → 0")
    void semanticWithoutWallContext_returnsZero() {
        assertEquals(0.0, LocalCoord.resolveRotation("FACE_INTO_ROOM", null), EPS);
        assertEquals(0.0, LocalCoord.resolveRotation("PARALLEL_TO_WALL", null), EPS);
    }

    // ── cardinalToRadians tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("cardinalToRadians covers all 4 walls")
    void cardinalToRadians_allFourWalls() {
        assertEquals(0.0,             LocalCoord.cardinalToRadians("south"), EPS);
        assertEquals(Math.PI,         LocalCoord.cardinalToRadians("north"), EPS);
        assertEquals(-Math.PI / 2,    LocalCoord.cardinalToRadians("west"),  EPS);
        assertEquals(Math.PI / 2,     LocalCoord.cardinalToRadians("east"),  EPS);
    }

    @Test
    @DisplayName("cardinalToRadians is case-insensitive")
    void cardinalToRadians_caseInsensitive() {
        assertEquals(Math.PI, LocalCoord.cardinalToRadians("NORTH"), EPS);
        assertEquals(Math.PI, LocalCoord.cardinalToRadians("North"), EPS);
    }

    // ── radiansToCardinal tests ──────────────────────────────────────────────

    @Test
    @DisplayName("radiansToCardinal is inverse of cardinalToRadians")
    void radiansToCardinal_inversePair() {
        assertEquals("south", LocalCoord.radiansToCardinal(0));
        assertEquals("north", LocalCoord.radiansToCardinal(Math.PI));
        assertEquals("west",  LocalCoord.radiansToCardinal(-Math.PI / 2));
        assertEquals("east",  LocalCoord.radiansToCardinal(Math.PI / 2));
    }

    @Test
    @DisplayName("radiansToCardinal tolerates small floating-point drift")
    void radiansToCardinal_floatingPointTolerance() {
        assertEquals("north", LocalCoord.radiansToCardinal(Math.PI + 0.005));
        assertEquals("east",  LocalCoord.radiansToCardinal(Math.PI / 2 - 0.005));
    }

    @Test
    @DisplayName("radiansToCardinal falls back to south for unknown angles")
    void radiansToCardinal_unknownAngle_returnsSouth() {
        assertEquals("south", LocalCoord.radiansToCardinal(0.7));
        assertEquals("south", LocalCoord.radiansToCardinal(2.0));
    }

    // ── oppositeCardinal tests ───────────────────────────────────────────────

    @Test
    @DisplayName("oppositeCardinal covers all 4 directions")
    void oppositeCardinal_allFour() {
        assertEquals("south", LocalCoord.oppositeCardinal("north"));
        assertEquals("north", LocalCoord.oppositeCardinal("south"));
        assertEquals("west",  LocalCoord.oppositeCardinal("east"));
        assertEquals("east",  LocalCoord.oppositeCardinal("west"));
    }

    @Test
    @DisplayName("oppositeCardinal is case-insensitive")
    void oppositeCardinal_caseInsensitive() {
        assertEquals("south", LocalCoord.oppositeCardinal("NORTH"));
        assertEquals("east",  LocalCoord.oppositeCardinal("West"));
    }
}
