package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;
import com.bim.orm.BIMLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * LPG routing: Meter → riser → kitchen → gas points.
 *
 * <p>Pattern: start at gas meter room (LPG service room, typically ground floor
 * external wall), rise vertically to each floor, BRANCH to kitchen/utility rooms
 * for gas point connections.
 *
 * <p>LPG is the smallest MEP system — fewest elements, simplest topology.
 * External wall clearance is closest (106mm from TE extraction).
 *
 * <p>Standards: MS 830 gas installation.
 * Riser 32mm → main 25mm → branches 20mm.
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T2.1–T2.4 — Witness: W-ROUTE-LPG-1
public final class LpgRouteBuilder implements DisciplineRouteBuilder {

    private static final String TAG = "ROUTE";
    private static final String DISC = "LPG";

    /** LPG riser — 32mm. */
    static final double RISER_DIAMETER_MM = 32;
    /** LPG main distribution — 25mm. */
    static final double MAIN_DIAMETER_MM = 25;
    /** LPG branch to gas points — 20mm. */
    static final double BRANCH_DIAMETER_MM = 20;
    /** Stock pipe length — 6000mm. */
    static final double STOCK_LENGTH_MM = 6000;

    @Override public String discipline() { return DISC; }
    @Override public String segmentProduct() { return "PipeSegment"; }
    @Override public double mainDiameterMm() { return MAIN_DIAMETER_MM; }
    @Override public double branchDiameterMm() { return BRANCH_DIAMETER_MM; }
    @Override public double stockLengthMm() { return STOCK_LENGTH_MM; }

    @Override
    public RoutePlan plan(BuildingGeometry geo) {
        Point3D start = geo.serviceRoomPosition(DISC);
        List<BuildingGeometry.FloorLevel> floors = geo.floors();
        BIMLogger.fine(TAG, "LPG plan: start={} floors={}", start, floors.size());

        List<CrawlOp> ops = new ArrayList<>();
        int roomCount = 0;

        CrawlState initial = new CrawlState(
                start, new Point3D(0, 0, 1), RISER_DIAMETER_MM, segmentProduct(), DISC);

        double currentZ = start.z() * 1000;

        for (int f = 0; f < floors.size(); f++) {
            BuildingGeometry.FloorLevel floor = floors.get(f);
            double ceilingZMm = geo.ceilingHeightMm(floor.ref());

            // Rise to ceiling void of this floor
            double riseDistance = ceilingZMm - currentZ;
            if (riseDistance > 0) {
                ops.add(new FollowOp(riseDistance, STOCK_LENGTH_MM));
                // LPG does not penetrate slabs — runs along external wall
                ops.add(new PenetrateOp("WALL", geo.slabThickness(floor.ref()), false));
            }

            // Bend to horizontal
            ops.add(new BendOp(90));
            if (f == 0) {
                ops.add(new ReduceOp(MAIN_DIAMETER_MM));
            }

            // Main run along external wall to kitchen/utility
            BuildingGeometry.RoomDimensions floorDims = geo.roomDimensions(floor.ref());
            double mainRun = floorDims != null ? floorDims.longestAxis() : 6000;
            if (mainRun > 0) {
                ops.add(new FollowOp(mainRun, STOCK_LENGTH_MM));
            }

            // Branch to kitchen/utility rooms
            List<BuildingGeometry.RoomTarget> rooms = geo.roomsOnFloor(floor.ref());
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

            if (f < floors.size() - 1) {
                ops.add(new BendOp(-90));
            }

            currentZ = ceilingZMm;
        }

        return new RoutePlan(initial, ops, floors.size(), roomCount,
                "Meter→riser→kitchen→gas points (MS 830)");
    }
}
