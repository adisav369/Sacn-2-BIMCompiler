package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;
import com.bim.orm.BIMLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * T2.3 CW routing: Tank → riser → floor header → fixtures (supply, pressure-driven).
 *
 * <p>Pattern: start at water tank room (CW service room), rise vertically through
 * each floor (PENETRATE slabs), run floor header to distribute, BRANCH + REDUCE
 * to each room for fixture connections (taps, basins, showers).
 *
 * <p>Standards: MS 1228 pipe sizing by fixture unit, UPC gradient rules.
 * Riser 50mm → main 32mm → branches 20mm.
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T2.3 — Witness: W-ROUTE-CW-1
public final class CwRouteBuilder implements DisciplineRouteBuilder {

    private static final String TAG = "ROUTE";
    private static final String DISC = "CW";

    /** CW riser — 50mm standard cold water riser. */
    static final double RISER_DIAMETER_MM = 50;
    /** CW main distribution — 32mm. */
    static final double MAIN_DIAMETER_MM = 32;
    /** CW branch to fixtures — 20mm. */
    static final double BRANCH_DIAMETER_MM = 20;
    /** Stock pipe length — 6000mm. */
    static final double STOCK_LENGTH_MM = 6000;

    @Override public String discipline() { return DISC; }
    @Override public String segmentProduct() { return "PipeSegment"; }
    @Override public double mainDiameterMm() { return MAIN_DIAMETER_MM; }
    @Override public double branchDiameterMm() { return BRANCH_DIAMETER_MM; }
    @Override public double stockLengthMm() { return STOCK_LENGTH_MM; }
    @Override public double insulationThicknessMm() { return 25; }  // condensation prevention
    @Override public java.util.Map<String, String> standardRefs() {
        return java.util.Map.of(
            "main_diameter_mm", "MS 1228 §5.3",
            "branch_diameter_mm", "MS 1228 §5.3");
    }

    @Override
    public RoutePlan plan(BuildingGeometry geo) {
        Point3D start = geo.serviceRoomPosition(DISC);
        List<BuildingGeometry.FloorLevel> floors = geo.floors();
        BIMLogger.fine(TAG, "CW plan: start={} floors={}", start, floors.size());

        List<CrawlOp> ops = new ArrayList<>();
        int roomCount = 0;

        // CW is pressure-driven: flows upward from tank, same riser pattern as FP
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
                ops.add(new PenetrateOp("SLAB", geo.slabThickness(floor.ref()), false));
            }

            // Bend to horizontal
            ops.add(new BendOp(90));
            if (f == 0) {
                ops.add(new ReduceOp(MAIN_DIAMETER_MM));
            }

            // Floor distribution header
            BuildingGeometry.RoomDimensions floorDims = geo.roomDimensions(floor.ref());
            double headerRun = floorDims != null ? floorDims.longestAxis() : 6000;
            if (headerRun > 0) {
                ops.add(new FollowOp(headerRun, STOCK_LENGTH_MM));
            }

            // Branch to rooms with fixtures
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
                "Tank→riser→header→fixtures (MS 1228)");
    }
}
