package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;
import com.bim.orm.BIMLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * T2.3 SP routing: Fixtures → waste → stack → drain (gravity, DOWNWARD).
 *
 * <p><b>SP is special:</b> all other disciplines flow outward/upward from a source.
 * SP flows <b>downward by gravity</b>. Route starts at top floor and descends.
 * Minimum gradient 1:40 for 100mm pipe (per MS 1228, UPC).
 *
 * <p>Pattern: collect from rooms on each floor → waste pipe (horizontal, gradient) →
 * drop to stack (vertical) → PENETRATE slabs → drain at ground.
 *
 * <p>Standards: MS 1228 minimum gradient 1:40 / 1:60, UPC gradient rules.
 * Stack 100mm → waste 75mm → branch 50mm.
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T2.3 — Witness: W-ROUTE-SP-1
public final class SpRouteBuilder implements DisciplineRouteBuilder {

    private static final String TAG = "ROUTE";
    private static final String DISC = "SP";

    /** SP stack (vertical drain) — 100mm. */
    static final double STACK_DIAMETER_MM = 100;
    /** SP waste (horizontal, gradient) — 75mm. */
    static final double WASTE_DIAMETER_MM = 75;
    /** SP branch to fixtures — 50mm. */
    static final double BRANCH_DIAMETER_MM = 50;
    /** Stock pipe length — 6000mm. */
    static final double STOCK_LENGTH_MM = 6000;

    @Override public String discipline() { return DISC; }
    @Override public String segmentProduct() { return "PipeSegment"; }
    @Override public double mainDiameterMm() { return WASTE_DIAMETER_MM; }
    @Override public double branchDiameterMm() { return BRANCH_DIAMETER_MM; }
    @Override public double stockLengthMm() { return STOCK_LENGTH_MM; }

    @Override
    public RoutePlan plan(BuildingGeometry geo) {
        Point3D start = geo.serviceRoomPosition(DISC);
        List<BuildingGeometry.FloorLevel> floors = geo.floors();
        BIMLogger.fine(TAG, "SP plan: start={} floors={}", start, floors.size());

        List<CrawlOp> ops = new ArrayList<>();
        int roomCount = 0;

        // SP flows DOWN — start at top floor, descend via stack
        // Direction is DOWN (0,0,-1) for the vertical stack
        List<BuildingGeometry.FloorLevel> topDown = new ArrayList<>(floors);
        Collections.reverse(topDown);

        // Start at wet core position, ceiling void of top floor
        double startZMm = topDown.isEmpty() ? start.z() * 1000 : geo.ceilingHeightMm(topDown.get(0).ref());
        CrawlState initial = new CrawlState(
                new Point3D(start.x(), start.y(), startZMm / 1000.0),
                new Point3D(0, 0, -1), STACK_DIAMETER_MM, segmentProduct(), DISC);

        double currentZ = startZMm;

        for (int f = 0; f < topDown.size(); f++) {
            BuildingGeometry.FloorLevel floor = topDown.get(f);
            double ceilingZMm = geo.ceilingHeightMm(floor.ref());

            // Drop to ceiling void of this floor (if below current Z)
            double dropDistance = currentZ - ceilingZMm;
            if (dropDistance > 0) {
                ops.add(new FollowOp(dropDistance, STOCK_LENGTH_MM));
                // SP can penetrate slabs (drainage risers)
                double slabMm = geo.slabThickness(floor.ref());
                ops.add(new PenetrateOp("SLAB", slabMm, false));
            }

            // Bend to horizontal for waste collection on this floor
            ops.add(new BendOp(90));
            if (f == 0) {
                ops.add(new ReduceOp(WASTE_DIAMETER_MM));
            }

            // Waste pipe run (horizontal, with gradient)
            BuildingGeometry.RoomDimensions floorDims = geo.roomDimensions(floor.ref());
            double wasteRun = floorDims != null ? floorDims.longestAxis() : 6000;
            if (wasteRun > 0) {
                ops.add(new FollowOp(wasteRun, STOCK_LENGTH_MM));
            }

            // Branch from each room's fixtures to the waste pipe
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

            // Bend back to vertical (downward) for next floor
            if (f < topDown.size() - 1) {
                ops.add(new BendOp(-90));
            }

            currentZ = ceilingZMm;
        }

        return new RoutePlan(initial, ops, floors.size(), roomCount,
                "Fixtures→waste→stack→drain [gravity] (MS 1228)");
    }
}
