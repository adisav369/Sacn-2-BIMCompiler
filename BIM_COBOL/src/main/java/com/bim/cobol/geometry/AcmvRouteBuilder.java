package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;
import com.bim.orm.BIMLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * T2.4 ACMV routing: AHU → main duct → branches → air terminals.
 *
 * <p>Pattern: start at AHU/plant room (ACMV service room), main duct runs
 * along ceiling void, BRANCH + REDUCE at each room for supply/return
 * air terminal distribution.
 *
 * <p>ACMV ducts are larger than pipes — 400mm main → 300mm branch → 200mm terminal.
 * No slab penetration (ducts run horizontally in ceiling void, risers are shafts).
 *
 * <p>Standards: MS 1525 duct sizing, ASHRAE 62.1 air changes per hour.
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T2.4 — Witness: W-ROUTE-ACMV-1
public final class AcmvRouteBuilder implements DisciplineRouteBuilder {

    private static final String TAG = "ROUTE";
    private static final String DISC = "ACMV";

    /** ACMV riser/shaft duct — 500mm. */
    static final double RISER_DIAMETER_MM = 500;
    /** ACMV main duct — 400mm. */
    static final double MAIN_DIAMETER_MM = 400;
    /** ACMV branch duct — 200mm. */
    static final double BRANCH_DIAMETER_MM = 200;
    /** Stock duct length — 3000mm (ducts are shorter than pipes). */
    static final double STOCK_LENGTH_MM = 3000;

    @Override public String discipline() { return DISC; }
    @Override public String segmentProduct() { return "DuctSegment"; }
    @Override public double mainDiameterMm() { return MAIN_DIAMETER_MM; }
    @Override public double branchDiameterMm() { return BRANCH_DIAMETER_MM; }
    @Override public double stockLengthMm() { return STOCK_LENGTH_MM; }

    @Override
    public RoutePlan plan(BuildingGeometry geo) {
        Point3D start = geo.serviceRoomPosition(DISC);
        List<BuildingGeometry.FloorLevel> floors = geo.floors();
        BIMLogger.fine(TAG, "ACMV plan: start={} floors={}", start, floors.size());

        List<CrawlOp> ops = new ArrayList<>();
        int roomCount = 0;

        // ACMV starts at AHU, rises through shaft to each floor
        CrawlState initial = new CrawlState(
                start, new Point3D(0, 0, 1), RISER_DIAMETER_MM, segmentProduct(), DISC);

        double currentZ = start.z() * 1000;

        for (int f = 0; f < floors.size(); f++) {
            BuildingGeometry.FloorLevel floor = floors.get(f);
            double ceilingZMm = geo.ceilingHeightMm(floor.ref());

            // Rise through shaft to ceiling void of this floor
            double riseDistance = ceilingZMm - currentZ;
            if (riseDistance > 0) {
                ops.add(new FollowOp(riseDistance, STOCK_LENGTH_MM));
                // Ducts don't penetrate slabs — they use shafts
                // But the shaft passes through the slab opening
                ops.add(new PenetrateOp("SLAB", geo.slabThickness(floor.ref()), false));
            }

            // Bend to horizontal, reduce to main duct size
            ops.add(new BendOp(90));
            if (f == 0) {
                ops.add(new ReduceOp(MAIN_DIAMETER_MM));
            }

            // Main duct run along ceiling void
            BuildingGeometry.RoomDimensions floorDims = geo.roomDimensions(floor.ref());
            double ductRun = floorDims != null ? floorDims.longestAxis() : 6000;
            if (ductRun > 0) {
                ops.add(new FollowOp(ductRun, STOCK_LENGTH_MM));
            }

            // Branch + reduce to each room for air terminals
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
                "AHU→duct→branches→terminals (ASHRAE 62.1)");
    }
}
