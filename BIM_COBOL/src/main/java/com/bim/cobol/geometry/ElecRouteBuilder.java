package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;
import com.bim.orm.BIMLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * T2.2 ELEC routing: DB → cable tray → light fixture grid per room.
 *
 * <p>Pattern: start at DB room (ELEC service room), rise vertically through
 * each floor (PENETRATE slabs), run cable tray along ceiling, BRANCH to
 * each room for light fixture circuits.
 *
 * <p>Standards: MS 1525 lighting power density, IES lux levels.
 * Conduit riser 25mm → cable tray 20mm → branch 16mm (extracted from TE).
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.11 T2.2 — Witness: W-ROUTE-ELEC-1
public final class ElecRouteBuilder implements DisciplineRouteBuilder {

    private static final String TAG = "ROUTE";
    private static final String DISC = "ELEC";

    /** ELEC riser conduit — 25mm. */
    static final double RISER_DIAMETER_MM = 25;
    /** Cable tray main — 20mm. */
    static final double MAIN_DIAMETER_MM = 20;
    /** Branch conduit — 16mm. */
    static final double BRANCH_DIAMETER_MM = 16;
    /** Stock conduit length — 6000mm. */
    static final double STOCK_LENGTH_MM = 6000;

    @Override public String discipline() { return DISC; }
    @Override public String segmentProduct() { return "CableTray"; }
    @Override public double mainDiameterMm() { return MAIN_DIAMETER_MM; }
    @Override public double branchDiameterMm() { return BRANCH_DIAMETER_MM; }
    @Override public double stockLengthMm() { return STOCK_LENGTH_MM; }
    @Override public java.util.Map<String, String> standardRefs() {
        return java.util.Map.of(
            "main_diameter_mm", "MS 1525 §7.3",
            "branch_diameter_mm", "MS 1525 §7.3",
            "stock_length_mm", "MS 1525 §7.3");
    }

    @Override
    public RoutePlan plan(BuildingGeometry geo) {
        Point3D start = geo.serviceRoomPosition(DISC);
        List<BuildingGeometry.FloorLevel> floors = geo.floors();
        BIMLogger.fine(TAG, "ELEC plan: start={} floors={}", start, floors.size());

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
                ops.add(new PenetrateOp("SLAB", geo.slabThickness(floor.ref()), false));
            }

            // Bend to horizontal, reduce to cable tray diameter
            ops.add(new BendOp(90));
            if (f == 0) {
                ops.add(new ReduceOp(MAIN_DIAMETER_MM));
            }

            // Cable tray run along ceiling
            BuildingGeometry.RoomDimensions floorDims = geo.roomDimensions(floor.ref());
            double trayRun = floorDims != null ? floorDims.longestAxis() : 6000;
            if (trayRun > 0) {
                ops.add(new FollowOp(trayRun, STOCK_LENGTH_MM));
            }

            // Branch to each room for light fixture circuits
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
                "DB→tray→fixtures (MS 1525)");
    }
}
