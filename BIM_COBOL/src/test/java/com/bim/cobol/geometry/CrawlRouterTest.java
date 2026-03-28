package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Witness tests for CrawlRouter + 5 CrawlOps.
 *
 * <p>W-BEND-1, W-BRANCH-1, W-REDUCE-1, W-PENETRATE-1 (T1.1–T1.4).
 * Plus FollowOp regression (CrawlRouter path for W-FOLLOW-1).
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T1.1–T1.4
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CrawlRouterTest {

    private static final Point3D ORIGIN = new Point3D(0, 0, 0);
    private static final Point3D X_DIR = new Point3D(1, 0, 0);

    private static CrawlState fpState(double diameterMm) {
        return new CrawlState(ORIGIN, X_DIR, diameterMm, "PipeSegment", "FP");
    }

    // ── FollowOp regression ───────────────────────────────────────

    /**
     * FollowOp: 6000mm run with 6000mm stock → 1 segment, 0 fittings.
     */
    @Test
    @Order(1)
    void followOp_exact_fit() {
        CrawlRouter.RouteResult r = CrawlRouter.execute(
                fpState(25), List.of(new FollowOp(6000, 6000)));

        assertEquals(1, r.segments().size(), "1 segment for exact fit");
        assertEquals(0, r.fittings().size(), "FOLLOW = zero fittings");
        assertEquals(6000, r.totalLengthMm(), 0.1);
    }

    /**
     * FollowOp: 8868mm run with 6000mm stock → 2 segments.
     */
    @Test
    @Order(2)
    void followOp_two_segments() {
        CrawlRouter.RouteResult r = CrawlRouter.execute(
                fpState(25), List.of(new FollowOp(8868, 6000)));

        assertEquals(2, r.segments().size(), "ceil(8868/6000) = 2");
        assertEquals(0, r.fittings().size());
    }

    /**
     * FollowOp: 8868mm run with 3000mm stock → 3 segments.
     */
    @Test
    @Order(3)
    void followOp_three_segments() {
        CrawlRouter.RouteResult r = CrawlRouter.execute(
                fpState(25), List.of(new FollowOp(8868, 3000)));

        assertEquals(3, r.segments().size(), "ceil(8868/3000) = 3");
    }

    // ── W-BEND-1: angle + diameter → correct elbow ────────────────

    /**
     * W-BEND-1a: 90° bend → ELBOW_90 fitting, direction changed.
     */
    @Test
    @Order(10)
    void w_bend_1a_90_degree() {
        CrawlRouter.RouteResult r = CrawlRouter.execute(
                fpState(32), List.of(new BendOp(90)));

        assertEquals(0, r.segments().size(), "BEND produces no segments");
        assertEquals(1, r.fittings().size(), "BEND produces 1 fitting");

        CrawlRouter.FittingSpec fit = r.fittings().get(0);
        assertEquals("ELBOW_90", fit.type());
        assertEquals(32, fit.inletDiameterMm());
        assertEquals(90, fit.angleDeg());

        // Direction should have turned 90° from (1,0,0) → (0,1,0)
        Point3D finalDir = r.finalState().direction();
        assertEquals(0, finalDir.x(), 0.001, "x should be ~0 after 90° turn");
        assertEquals(1, finalDir.y(), 0.001, "y should be ~1 after 90° turn");
    }

    /**
     * W-BEND-1b: 45° bend → ELBOW_45 fitting.
     */
    @Test
    @Order(11)
    void w_bend_1b_45_degree() {
        CrawlRouter.RouteResult r = CrawlRouter.execute(
                fpState(25), List.of(new BendOp(45)));

        CrawlRouter.FittingSpec fit = r.fittings().get(0);
        assertEquals("ELBOW_45", fit.type());
        assertEquals(45, fit.angleDeg());
    }

    /**
     * W-BEND-1c: FOLLOW + BEND + FOLLOW compose correctly.
     */
    @Test
    @Order(12)
    void w_bend_1c_follow_bend_follow() {
        CrawlRouter.RouteResult r = CrawlRouter.execute(
                fpState(25), List.of(
                        new FollowOp(3000, 3000),
                        new BendOp(90),
                        new FollowOp(2000, 2000)));

        assertEquals(2, r.segments().size(), "1 segment before + 1 after bend");
        assertEquals(1, r.fittings().size(), "1 elbow at bend");
        assertEquals(5000, r.totalLengthMm(), 0.1);
    }

    // ── W-BRANCH-1: main → 2 sub-routes, tee at branch point ─────

    /**
     * W-BRANCH-1a: single branch → TEE fitting + sub-route segments.
     */
    @Test
    @Order(20)
    void w_branch_1a_single_branch() {
        List<CrawlOp> branchRoute = List.of(new FollowOp(2000, 2000));
        CrawlRouter.RouteResult r = CrawlRouter.execute(
                fpState(32), List.of(
                        new FollowOp(3000, 3000),
                        new BranchOp(List.of(branchRoute), 25)));

        // 1 main segment + 1 branch segment
        assertEquals(2, r.segments().size());
        // 1 TEE fitting
        assertEquals(1, r.fittings().size());
        assertEquals("TEE", r.fittings().get(0).type());
        assertEquals(32, r.fittings().get(0).inletDiameterMm(), "main diameter");
        assertEquals(25, r.fittings().get(0).outletDiameterMm(), "branch diameter");
    }

    /**
     * W-BRANCH-1b: two branches → WYE fitting.
     */
    @Test
    @Order(21)
    void w_branch_1b_double_branch() {
        List<CrawlOp> branch1 = List.of(new FollowOp(1500, 1500));
        List<CrawlOp> branch2 = List.of(new FollowOp(2000, 2000));
        CrawlRouter.RouteResult r = CrawlRouter.execute(
                fpState(32), List.of(
                        new BranchOp(List.of(branch1, branch2), 25)));

        assertEquals(2, r.segments().size(), "1 segment per branch");
        assertEquals(1, r.fittings().size());
        assertEquals("WYE", r.fittings().get(0).type());
    }

    /**
     * W-BRANCH-1c: main crawl continues after branch (state unchanged).
     */
    @Test
    @Order(22)
    void w_branch_1c_main_continues() {
        CrawlRouter.RouteResult r = CrawlRouter.execute(
                fpState(32), List.of(
                        new FollowOp(3000, 3000),
                        new BranchOp(List.of(List.of(new FollowOp(1000, 1000))), 25),
                        new FollowOp(2000, 2000)));

        // 1 main before branch + 1 branch + 1 main after branch
        assertEquals(3, r.segments().size());
        assertEquals(1, r.fittings().size(), "1 TEE at branch point");
        // Final state should reflect main crawl (3000 + 2000 = 5m along X)
        assertEquals(5.0, r.finalState().position().x(), 0.01);
        assertEquals(32, r.finalState().diameterMm(), "main diameter unchanged");
    }

    // ── W-REDUCE-1: 50mm→25mm produces reducer ───────────────────

    /**
     * W-REDUCE-1a: 50→25mm produces REDUCER fitting, state diameter changes.
     */
    @Test
    @Order(30)
    void w_reduce_1a_basic() {
        CrawlRouter.RouteResult r = CrawlRouter.execute(
                fpState(50), List.of(new ReduceOp(25)));

        assertEquals(0, r.segments().size(), "REDUCE produces no segments");
        assertEquals(1, r.fittings().size());

        CrawlRouter.FittingSpec fit = r.fittings().get(0);
        assertEquals("REDUCER", fit.type());
        assertEquals(50, fit.inletDiameterMm());
        assertEquals(25, fit.outletDiameterMm());
        assertEquals(25, r.finalState().diameterMm(), "diameter changed to 25");
    }

    /**
     * W-REDUCE-1b: FOLLOW + REDUCE + FOLLOW — segments have correct diameters.
     */
    @Test
    @Order(31)
    void w_reduce_1b_follow_reduce_follow() {
        CrawlRouter.RouteResult r = CrawlRouter.execute(
                fpState(50), List.of(
                        new FollowOp(3000, 3000),
                        new ReduceOp(25),
                        new FollowOp(2000, 2000)));

        assertEquals(2, r.segments().size());
        assertEquals(50, r.segments().get(0).diameterMm(), "before reducer: 50mm");
        assertEquals(25, r.segments().get(1).diameterMm(), "after reducer: 25mm");
        assertEquals(1, r.fittings().size());
    }

    // ── W-PENETRATE-1: sleeve at floor crossing, fire collar if rated ──

    /**
     * W-PENETRATE-1a: non-fire-rated slab → 1 sleeve, no fire collar.
     */
    @Test
    @Order(40)
    void w_penetrate_1a_slab_no_fire() {
        CrawlRouter.RouteResult r = CrawlRouter.execute(
                fpState(25), List.of(new PenetrateOp("SLAB", 150, false)));

        assertEquals(0, r.segments().size());
        assertEquals(1, r.fittings().size(), "1 sleeve only");
        assertEquals("SLEEVE_SLAB", r.fittings().get(0).type());
        // Position should advance by barrier thickness
        assertEquals(0.15, r.finalState().position().x(), 0.001);
    }

    /**
     * W-PENETRATE-1b: fire-rated wall → 1 sleeve + 1 fire collar.
     */
    @Test
    @Order(41)
    void w_penetrate_1b_wall_fire_rated() {
        CrawlRouter.RouteResult r = CrawlRouter.execute(
                fpState(25), List.of(new PenetrateOp("WALL", 200, true)));

        assertEquals(0, r.segments().size());
        assertEquals(2, r.fittings().size(), "1 sleeve + 1 fire collar");
        assertEquals("SLEEVE_WALL", r.fittings().get(0).type());
        assertEquals("FIRE_COLLAR", r.fittings().get(1).type());
    }

    /**
     * W-PENETRATE-1c: full route — FOLLOW + PENETRATE + FOLLOW crosses barrier.
     */
    @Test
    @Order(42)
    void w_penetrate_1c_follow_penetrate_follow() {
        CrawlRouter.RouteResult r = CrawlRouter.execute(
                fpState(25), List.of(
                        new FollowOp(3000, 3000),
                        new PenetrateOp("SLAB", 150, true),
                        new FollowOp(2000, 2000)));

        assertEquals(2, r.segments().size(), "1 before + 1 after penetration");
        assertEquals(2, r.fittings().size(), "sleeve + fire collar");
        assertEquals(5000, r.totalLengthMm(), 0.1, "3000 + 2000 segments");
    }

    // ── CONNECTS_TO edges ────────────────────────────────────────

    /**
     * FOLLOW 2 segments → 1 CONNECTS_TO edge (segment_0 → segment_1).
     */
    @Test
    @Order(45)
    void edges_follow_two_segments() {
        CrawlRouter.RouteResult r = CrawlRouter.execute(
                fpState(25), List.of(new FollowOp(8868, 6000)));

        assertEquals(2, r.segments().size());
        assertEquals(1, r.edges().size(), "2 segments → 1 edge");
        assertEquals("SEGMENT_TO_SEGMENT", r.edges().get(0).edgeType());
    }

    /**
     * FOLLOW + BEND + FOLLOW → edges: seg→fitting, fitting→seg.
     */
    @Test
    @Order(46)
    void edges_follow_bend_follow() {
        CrawlRouter.RouteResult r = CrawlRouter.execute(
                fpState(25), List.of(
                        new FollowOp(3000, 3000),
                        new BendOp(90),
                        new FollowOp(2000, 2000)));

        // 3 elements: seg, fitting, seg → 2 edges
        assertEquals(2, r.edges().size());
        assertEquals("SEGMENT_TO_FITTING", r.edges().get(0).edgeType());
        assertEquals("FITTING_TO_SEGMENT", r.edges().get(1).edgeType());
    }

    /**
     * P17 SystemConnectedProof readiness: composite route graph is fully connected.
     * Every element connects to the previous — no disconnected subgraphs.
     */
    @Test
    @Order(47)
    void edges_composite_fully_connected() {
        CrawlRouter.RouteResult r = CrawlRouter.execute(
                fpState(50), List.of(
                        new FollowOp(6000, 6000),
                        new BendOp(90),
                        new FollowOp(3000, 3000),
                        new ReduceOp(25),
                        new FollowOp(2000, 2000),
                        new PenetrateOp("SLAB", 150, false),
                        new FollowOp(1000, 1000)));

        // 4 segments + 3 fittings (elbow, reducer, sleeve) = 7 elements → 6 edges
        int totalElements = r.segments().size() + r.fittings().size();
        assertEquals(totalElements - 1, r.edges().size(),
                "fully connected: N elements → N-1 edges");

        // Each edge's toIndex = fromIndex + 1 (insertion order = connection order)
        for (int i = 0; i < r.edges().size(); i++) {
            CrawlRouter.ConnectionEdge e = r.edges().get(i);
            assertEquals(e.fromIndex() + 1, e.toIndex(),
                    "edge " + i + ": sequential connection");
        }
    }

    // ── Composite route (all 5 ops) ───────────────────────────────

    /**
     * Full crawl: FOLLOW → BEND → FOLLOW → BRANCH → REDUCE → FOLLOW → PENETRATE → FOLLOW.
     */
    @Test
    @Order(50)
    void composite_all_five_ops() {
        CrawlRouter.RouteResult r = CrawlRouter.execute(
                fpState(50), List.of(
                        new FollowOp(6000, 6000),         // 1 segment
                        new BendOp(90),                     // 1 elbow
                        new FollowOp(3000, 3000),          // 1 segment
                        new BranchOp(                       // 1 tee + 1 branch segment
                                List.of(List.of(new FollowOp(1500, 1500))), 25),
                        new ReduceOp(25),                   // 1 reducer
                        new FollowOp(2000, 2000),          // 1 segment
                        new PenetrateOp("SLAB", 150, true), // 1 sleeve + 1 fire collar
                        new FollowOp(1000, 1000)           // 1 segment
                ));

        assertEquals(5, r.segments().size(),
                "5 segments: 1+1+1(branch)+1+1 from FOLLOW ops");
        // Fittings: elbow + tee + reducer + sleeve + fire_collar = 5
        assertEquals(5, r.fittings().size());
        assertEquals(25, r.finalState().diameterMm(), "reduced from 50 to 25");
        // Edges: 10 elements (5 seg + 5 fit) → 9 edges
        assertFalse(r.edges().isEmpty(), "CONNECTS_TO edges must be emitted");
    }
}
