package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;
import com.bim.orm.BIMLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * T2.1 FP routing: Riser → floor header → branches → sprinkler grid.
 *
 * <p>Pattern: start at pump room (FP service room), rise vertically through
 * each floor (PENETRATE slabs), run floor header along longest axis,
 * BRANCH + REDUCE to each room for sprinkler distribution.
 *
 * <p>Standards: NFPA 13 §8 spacing, {@code ad_fp_coverage} hazard class.
 * Riser 50mm → main 32mm → branches 25mm (extracted from TE).
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T2.1 — Witness: W-ROUTE-FP-1
public final class FpRouteBuilder implements DisciplineRouteBuilder {

    private static final String TAG = "ROUTE";
    private static final String DISC = "FP";

    /** FP riser diameter — 50mm standard fire riser. */
    static final double RISER_DIAMETER_MM = 50;
    /** FP main header — 32mm distribution. */
    static final double MAIN_DIAMETER_MM = 32;
    /** FP branch to room — 25mm sprinkler branch. */
    static final double BRANCH_DIAMETER_MM = 25;
    /** Stock pipe length — 6000mm standard. */
    static final double STOCK_LENGTH_MM = 6000;

    @Override public String discipline() { return DISC; }
    @Override public String segmentProduct() { return "PipeSegment"; }
    @Override public double mainDiameterMm() { return MAIN_DIAMETER_MM; }
    @Override public double branchDiameterMm() { return BRANCH_DIAMETER_MM; }
    @Override public double stockLengthMm() { return STOCK_LENGTH_MM; }

    @Override
    public DisciplineRouteResult buildRoute(BuildingGeometry geo) {
        Point3D start = geo.serviceRoomPosition(DISC);
        List<BuildingGeometry.FloorLevel> floors = geo.floors();
        BIMLogger.fine(TAG, "FP route: start={} floors={}", start, floors.size());

        List<CrawlOp> ops = new ArrayList<>();
        int roomCount = 0;

        // Initial state: at pump room, direction UP for riser
        CrawlState initial = new CrawlState(
                start, new Point3D(0, 0, 1), RISER_DIAMETER_MM, segmentProduct(), DISC);

        double currentZ = start.z() * 1000; // convert to mm

        for (int f = 0; f < floors.size(); f++) {
            BuildingGeometry.FloorLevel floor = floors.get(f);
            double floorZMm = floor.zMm();

            // Rise to this floor (if above current Z)
            double riseDistance = floorZMm - currentZ;
            if (riseDistance > 0) {
                ops.add(new FollowOp(riseDistance, STOCK_LENGTH_MM));
                // Penetrate slab (fire-rated for FP)
                double slabMm = geo.slabThickness(floor.ref());
                ops.add(new PenetrateOp("SLAB", slabMm, true));
            }

            // At floor level: bend 90° to horizontal, reduce to main diameter
            ops.add(new BendOp(90));
            if (f == 0) {
                // First floor: reduce from riser to main diameter
                ops.add(new ReduceOp(MAIN_DIAMETER_MM));
            }

            // Floor header: follow longest axis
            List<BuildingGeometry.RoomTarget> rooms = geo.roomsOnFloor(floor.ref());
            BuildingGeometry.RoomDimensions floorDims = geo.roomDimensions(floor.ref());
            double headerRun = floorDims != null ? floorDims.longestAxis() : 6000;
            if (headerRun > 0) {
                ops.add(new FollowOp(headerRun, STOCK_LENGTH_MM));
            }

            // Branch to each room
            if (!rooms.isEmpty()) {
                List<List<CrawlOp>> branchRoutes = new ArrayList<>();
                for (BuildingGeometry.RoomTarget room : rooms) {
                    BuildingGeometry.RoomDimensions roomDims = geo.roomDimensions(room.ref());
                    double roomRun = roomDims != null ? roomDims.longestAxis() : 3000;
                    List<CrawlOp> branchOps = new ArrayList<>();
                    if (roomRun > 0) {
                        branchOps.add(new FollowOp(roomRun, STOCK_LENGTH_MM));
                    }
                    branchRoutes.add(branchOps);
                    roomCount++;
                }
                ops.add(new BranchOp(branchRoutes, BRANCH_DIAMETER_MM));
            }

            // Bend back to vertical for next floor riser
            if (f < floors.size() - 1) {
                ops.add(new BendOp(-90));
            }

            currentZ = floorZMm;
        }

        CrawlRouter.RouteResult result = CrawlRouter.execute(initial, ops);
        BIMLogger.fine(TAG, "FP route done: segments={} fittings={} rooms={}",
                result.segments().size(), result.fittings().size(), roomCount);

        return new DisciplineRouteResult(DISC, result, floors.size(), roomCount,
                "Riser→header→branches→sprinklers (NFPA 13)");
    }
}
