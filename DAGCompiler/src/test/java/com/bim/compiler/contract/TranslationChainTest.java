/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.contract;

import com.bim.compiler.library.BOMTierResolver;
import com.bim.compiler.library.BOMTierResolver.PlacedFurniture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Translation Chain — end-to-end verification from BOM data → BOMTierResolver → PlacedFurniture.
 *
 * <p>Tests the full placement chain with known BOM records and hand-calculated
 * expected positions. Catches rearranging, forgetting, and omission bugs that
 * output-only tests (G8, F4) cannot detect.
 *
 * <p>Uses the actual library DB (same pattern as existing contract tests).
 * Tolerance: 10mm (0.01m) — matches F4 contract.
 */
@DisplayName("Translation Chain — BOM → PlacedFurniture")
class TranslationChainTest {

    private static final double TOL = 0.01; // 10mm in metres
    private static BOMTierResolver resolver;

    @BeforeAll
    static void init() {
        resolver = new BOMTierResolver();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC1: SH Piano — known BOM child (dx=-3.749, dy=-0.200) on NORTH_WALL
    //
    // Room: ROOM_Ground_Floor_1 bounds (-7.510, -0.281) to (1.359, 4.409) in m
    //   roomW=8.869, roomD=4.690, area=41.6 (> 2.0 threshold)
    //
    // BOM: SH_LIVING_SET, Piano child has wall_rule=NORTH_WALL
    //   → resolvedWall="north" (explicit wall_rule)
    //   → anchor = computeZoneAnchor("north", 0.5, ...)
    //     cx = (-7.510 + 1.359)/2 = -3.0755
    //     y = 4.409 - 0.5 = 3.909
    //     rotation = π (north wall)
    //   → expandBOMNode: offset=(-3.749, -0.200, 0.0, rot=0)
    //     cos(π)=-1, sin(π)≈0
    //     world.x = -3.0755 + (-3.749)*(-1) = 0.6735
    //     world.y = 3.909 + (-0.200)*(-1) = 4.109
    //     world.z = 0.0
    //
    // Expected: Piano at approximately (0.67, 4.11, 0.0)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC1: SH Piano — NORTH_WALL anchor + BOM offset → correct world position")
    void tc1_shPiano_chainProducesCorrectWorldPosition() {
        // ROOM_Ground_Floor_1 bounds in metres
        double minX = -7.510, minY = -0.281, maxX = 1.359, maxY = 4.409;
        double floorZ = 0.0;

        List<PlacedFurniture> placed = resolver.resolveForRoom(
            minX, minY, maxX, maxY,
            floorZ, "ROOM_Ground_Floor_1", "LIVING",
            List.of(), "SH_LIVING_SET");

        // Find the Piano
        PlacedFurniture piano = placed.stream()
            .filter(pf -> "PIANO".equals(pf.role()))
            .findFirst()
            .orElse(null);

        assertNotNull(piano, "Piano not found in SH_LIVING_SET placement");
        assertEquals(0.674, piano.x(), TOL, "Piano world X");
        assertEquals(4.109, piano.y(), TOL, "Piano world Y");
        assertEquals(0.0,   piano.z(), TOL, "Piano world Z");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC2: DX Kitchen Base_Cabinet — floorZ accumulation
    //
    // Room: ROOM_A102 bounds (0.571, -17.043) to (3.516, -13.100) in m
    //   roomW=2.945, roomD=3.943, area=11.6
    //
    // BOM: KITCHEN_CABINET_SET, UPPER_CABINET child has dz=1.0
    //   When called with floorZ=3.0 (upper storey), the upper cabinet
    //   should be at z = 3.0 + 1.0 = 4.0
    //
    // Expected: Upper cabinet Z ≈ 4.0
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC2: DX Kitchen Upper_Cabinet — floorZ + dz accumulation")
    void tc2_dxKitchenCabinet_upperFloorZCorrect() {
        // ROOM_A102 bounds in metres
        double minX = 0.571, minY = -17.043, maxX = 3.516, maxY = -13.100;
        double floorZ = 3.0;  // simulate upper floor

        List<PlacedFurniture> placed = resolver.resolveForRoom(
            minX, minY, maxX, maxY,
            floorZ, "ROOM_A102", "KITCHEN",
            List.of(), "KITCHEN_CABINET_SET");

        // Find first UPPER_CABINET (dz=1.0 in BOM)
        PlacedFurniture upper = placed.stream()
            .filter(pf -> "UPPER_CABINET".equals(pf.role()))
            .findFirst()
            .orElse(null);

        assertNotNull(upper, "Upper cabinet not found in KITCHEN_CABINET_SET");
        assertEquals(4.0, upper.z(), TOL,
            "Upper cabinet Z should be floorZ(3.0) + dz(1.0) = 4.0");

        // BASE_CABINET should be at floorZ (dz=0)
        PlacedFurniture base = placed.stream()
            .filter(pf -> "BASE_CABINET".equals(pf.role()))
            .findFirst()
            .orElse(null);

        assertNotNull(base, "Base cabinet not found in KITCHEN_CABINET_SET");
        assertEquals(3.0, base.z(), TOL,
            "Base cabinet Z should be floorZ(3.0) + dz(0) = 3.0");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TC3: OPPOSITE_WORK child — anchor flips to opposite wall
    //
    // BOM: BED_SET_MASTER
    //   BED (primary): back_to_wall=true, dx=0, dy=0 → placed at work wall
    //   TALL_CABINET_A: opposite_wall=true → placed at opposite wall
    //
    // Room: synthetic 6m × 5m room, no openings
    //   workWall = "north" (longest wall with no openings)
    //   BED anchor: north wall → y near maxY - 0.5
    //   TALL_CABINET_A: OPPOSITE_WORK → y near minY + 0.5 (south wall)
    //
    // This tests that OPPOSITE_WORK flips the anchor ONCE, not double-negated.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TC3: OPPOSITE_WORK — anchor flips to opposite wall once")
    void tc3_oppositeWork_anchorFlipsOnce() {
        // Symmetric room: 6m wide × 5m deep, no openings
        double minX = 0, minY = 0, maxX = 6.0, maxY = 5.0;
        double floorZ = 0.0;

        List<PlacedFurniture> placed = resolver.resolveForRoom(
            minX, minY, maxX, maxY,
            floorZ, "TestRoom", "BEDROOM",
            List.of(), "BED_SET_MASTER");

        // Find the BED (placed at work wall)
        PlacedFurniture bed = placed.stream()
            .filter(pf -> "BED".equals(pf.role()))
            .findFirst()
            .orElse(null);

        // Find the TALL_CABINET_A (opposite_wall=true → OPPOSITE_WORK)
        PlacedFurniture cabinet = placed.stream()
            .filter(pf -> "TALL_CABINET_A".equals(pf.role()))
            .findFirst()
            .orElse(null);

        assertNotNull(bed, "BED not found in BED_SET_MASTER placement");
        assertNotNull(cabinet, "TALL_CABINET_A (OPPOSITE_WORK) not found");

        // Bed and cabinet should be on opposite walls — their Y coords should
        // be near opposite ends of the room (one near minY, one near maxY)
        double bedY = bed.y();
        double cabinetY = cabinet.y();
        double separation = Math.abs(bedY - cabinetY);

        // Room depth is 5.0m, wall offset is 0.5m each side → max separation ≈ 4.0m
        assertTrue(separation > 2.0,
            String.format("BED (y=%.3f) and TALL_CABINET_A (y=%.3f) should be on opposite walls (sep=%.3f)",
                bedY, cabinetY, separation));

        // Both should be within the room bounds
        assertTrue(bed.y() >= minY && bed.y() <= maxY,
            "BED Y should be within room bounds");
        assertTrue(cabinet.y() >= minY && cabinet.y() <= maxY,
            "TALL_CABINET_A Y should be within room bounds");
    }
}
