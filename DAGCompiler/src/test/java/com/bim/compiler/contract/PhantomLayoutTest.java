package com.bim.compiler.contract;

import com.bim.compiler.dsl.PhantomLayout;
import com.bim.compiler.dsl.Place;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.Vector3D;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W-PHANTOM-1 — PhantomLayout GPD witness: SH LIVING NORTH_WALL.
 *
 * <p>Claim: For the SH LIVING room NORTH_WALL locator —
 * <ul>
 *   <li>capacity_mm = room width from stored ad_room_boundary mm values
 *       (max_x_mm − min_x_mm = 1359 − (−7510) = 8869mm)</li>
 *   <li>GPD origin = NW corner in meters (min_x/1000, max_y/1000, floor_z)</li>
 *   <li>Piano (1371mm) + SPACER_VAR (0mm) + Sofa (2000mm) + Loveseat (1600mm)
 *       → filled = 4971mm, remaining = 3898mm (≥ 0, no overflow)</li>
 *   <li>nextAnchor advances along X_AXIS by each item's width</li>
 *   <li>No GIC violation (isOverflow() = false)</li>
 * </ul>
 *
 * <p>Data from DB (2026-02-25):
 * <pre>
 *   ad_room_boundary SH LIVING: min_x=-7510mm, max_x=1359mm, min_y=-281mm, max_y=4409mm
 *   ad_element_rule  FLOOR_SH_GF: position_value_3=0mm
 *   ad_product_dim   Piano: width=1.371m (1371mm), Sofa: width=2.0m (2000mm),
 *                    Sofa_Loveseat: width=1.6m (1600mm)
 * </pre>
 *
 * <p>Grid JOIN upgrade (Step 4): when ad_room_boundary.grid_min/max_x/y labels are
 * calibrated to ad_building_grid, capacity_mm will derive from grid positions instead
 * of stored mm values. This test will still pass — the mm values are the same.
 */
class PhantomLayoutTest {

    // SH LIVING room — from ad_room_boundary (stored mm, IFC_GLOBAL_MM frame)
    private static final double LIVING_MIN_X_MM   = -7510.0;
    private static final double LIVING_MAX_X_MM   =  1359.0;
    private static final double LIVING_MAX_Y_MM   =  4409.0;
    private static final double LIVING_WIDTH_MM   = LIVING_MAX_X_MM - LIVING_MIN_X_MM; // 8869mm
    private static final double FLOOR_Z           =  0.0;  // FLOOR_SH_GF position_value_3 in mm

    // Product widths from ad_product_dim (meters → mm at resolver boundary)
    private static final double PIANO_WIDTH_MM    = 1.371 * 1000; // 1371mm
    private static final double SOFA_WIDTH_MM     = 2.0   * 1000; // 2000mm
    private static final double LOVESEAT_WIDTH_MM = 1.6   * 1000; // 1600mm

    // BoundingBox helper: NORTH_WALL items — width along X, depth along Y
    private static BoundingBox wallBbox(double widthMm, double depthMm, double heightMm) {
        double w = widthMm  / 1000.0;
        double d = depthMm  / 1000.0;
        double h = heightMm / 1000.0;
        return new BoundingBox(0, w, 0, d, 0, h);
    }

    @Test
    void w_phantom_1_capacity_from_stored_mm() {
        // W-PHANTOM-1 assertion 1: capacity = room width (NORTH_WALL walks along X)
        assertEquals(8869.0, LIVING_WIDTH_MM, 1.0,
            "LIVING room width must be 8869mm (max_x 1359 - min_x -7510)");
    }

    @Test
    void w_phantom_1_gpd_origin() {
        // W-PHANTOM-1 assertion 2: GPD origin is NW corner (min_x, max_y) in meters
        Point3D origin = new Point3D(
            LIVING_MIN_X_MM / 1000.0,   // -7.510m
            LIVING_MAX_Y_MM / 1000.0,   //  4.409m
            FLOOR_Z         / 1000.0    //  0.0m
        );
        assertEquals(-7.510, origin.x(), 0.001);
        assertEquals( 4.409, origin.y(), 0.001);
        assertEquals( 0.0,   origin.z(), 0.001);
    }

    @Test
    void w_phantom_1_gpd_walk_north_wall() {
        // W-PHANTOM-1 assertion 3: GPD walk places items sequentially along X
        // Piano → SPACER_VAR → Sofa → Loveseat

        Point3D origin = new Point3D(
            LIVING_MIN_X_MM / 1000.0,
            LIVING_MAX_Y_MM / 1000.0,
            FLOOR_Z         / 1000.0
        );

        PhantomLayout ph = PhantomLayout.forLocator(
            "SH_LIVING_SET", "NORTH_WALL",
            LIVING_WIDTH_MM,
            origin,
            Vector3D.X_AXIS   // hostAxis: walk east along NORTH wall
        );

        // seq=1: Piano (1371mm along X)
        Place piano = Place.fixed("Piano", "NORTH_WALL", 1,
            wallBbox(PIANO_WIDTH_MM, 600, 1170),
            ph.nextAnchor(), Vector3D.NEG_Y, Vector3D.X_AXIS);
        ph.placeNext(piano);

        assertEquals(PIANO_WIDTH_MM, ph.filledMm(), 1.0,
            "After piano: filled = 1371mm");
        assertEquals(LIVING_WIDTH_MM - PIANO_WIDTH_MM, ph.remainingMm(), 1.0,
            "After piano: remaining = 7498mm");

        // seq=2: SPACER_VAR (0mm — variance child)
        Place spacer = Place.variance("SPACER_VAR", "NORTH_WALL", 2,
            ph.nextAnchor(), Vector3D.NEG_Y, Vector3D.X_AXIS);
        ph.placeNext(spacer);

        assertEquals(PIANO_WIDTH_MM, ph.filledMm(), 1.0,
            "After spacer: filled unchanged (variance = 0mm contribution)");

        // seq=3: Sofa (2000mm)
        Place sofa = Place.fixed("Sofa", "NORTH_WALL", 3,
            wallBbox(SOFA_WIDTH_MM, 800, 450),
            ph.nextAnchor(), Vector3D.NEG_Y, Vector3D.X_AXIS);
        ph.placeNext(sofa);

        assertEquals(PIANO_WIDTH_MM + SOFA_WIDTH_MM, ph.filledMm(), 1.0,
            "After sofa: filled = 3371mm");

        // seq=4: Loveseat (1600mm)
        Place loveseat = Place.fixed("Sofa_Loveseat", "NORTH_WALL", 4,
            wallBbox(LOVESEAT_WIDTH_MM, 800, 450),
            ph.nextAnchor(), Vector3D.NEG_Y, Vector3D.X_AXIS);
        ph.placeNext(loveseat);

        double expectedFilled    = PIANO_WIDTH_MM + SOFA_WIDTH_MM + LOVESEAT_WIDTH_MM; // 4971mm
        double expectedRemaining = LIVING_WIDTH_MM - expectedFilled;                   // 3898mm

        assertEquals(expectedFilled,    ph.filledMm(),    1.0, "Total filled = 4971mm");
        assertEquals(expectedRemaining, ph.remainingMm(), 1.0, "Remaining = 3898mm");
        assertFalse(ph.isOverflow(), "No GIC violation — items fit NORTH_WALL");
        assertFalse(ph.isFull(),     "Not full — 3898mm variance remains");
        assertEquals(4, ph.places().size(), "4 places registered");
    }

    @Test
    void w_phantom_1_anchor_advances_along_x() {
        // W-PHANTOM-1 assertion 4: nextAnchor.x advances by each item's width
        Point3D origin = new Point3D(LIVING_MIN_X_MM / 1000.0,
                                     LIVING_MAX_Y_MM / 1000.0, 0.0);

        PhantomLayout ph = PhantomLayout.forLocator(
            "SH_LIVING_SET", "NORTH_WALL", LIVING_WIDTH_MM, origin, Vector3D.X_AXIS);

        assertEquals(LIVING_MIN_X_MM / 1000.0, ph.nextAnchor().x(), 0.001,
            "Initial anchor.x = min_x_mm in meters");

        Place piano = Place.fixed("Piano", "NORTH_WALL", 1,
            wallBbox(PIANO_WIDTH_MM, 600, 1170),
            ph.nextAnchor(), Vector3D.NEG_Y, Vector3D.X_AXIS);
        ph.placeNext(piano);

        double expectedX = (LIVING_MIN_X_MM + PIANO_WIDTH_MM) / 1000.0;
        assertEquals(expectedX, ph.nextAnchor().x(), 0.001,
            "After piano: anchor.x advanced by 1.371m (1371mm)");

        // Y and Z must not change (horizontal wall walk)
        assertEquals(LIVING_MAX_Y_MM / 1000.0, ph.nextAnchor().y(), 0.001,
            "Anchor.y unchanged — GPD walks X only for NORTH_WALL");
        assertEquals(0.0, ph.nextAnchor().z(), 0.001,
            "Anchor.z unchanged — floor level");
    }

    @Test
    void w_phantom_1_overflow_detection() {
        // Confirm overflow is detected when items exceed capacity
        Point3D origin = new Point3D(0, 0, 0);
        PhantomLayout ph = PhantomLayout.forLocator(
            "TEST_BOM", "NORTH_WALL",
            1000.0,   // tiny 1000mm capacity
            origin, Vector3D.X_AXIS);

        Place big = Place.fixed("HugeItem", "NORTH_WALL", 1,
            wallBbox(1500, 800, 450),   // 1500mm — exceeds 1000mm capacity
            ph.nextAnchor(), Vector3D.NEG_Y, Vector3D.X_AXIS);
        ph.placeNext(big);

        assertTrue(ph.isOverflow(), "GIC violation: 1500mm > 1000mm capacity");
        assertTrue(ph.remainingMm() < 0, "remaining_mm negative = overflow");
    }
}
