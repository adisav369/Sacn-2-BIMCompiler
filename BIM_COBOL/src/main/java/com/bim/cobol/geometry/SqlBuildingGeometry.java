package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;
import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * SQL implementation of {@link BuildingGeometry} — queries c_orderline in the compile DB.
 *
 * <p>All geometry comes from ARC-compiled data. No invention. Positions are in mm
 * (c_orderline.dx/dy/dz), converted to Point3D meters where needed.
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.10 — SqlBuildingGeometry BOM host_type fix
public class SqlBuildingGeometry implements BuildingGeometry {

    private static final String TAG = "CRAWL";
    private static final double DEFAULT_SLAB_THICKNESS_MM = 150.0;
    private static final double DEFAULT_STOREY_HEIGHT_MM = 3500.0;
    private static final double MEP_CLEARANCE_MM = 50.0;

    private final Connection compileDb;
    private final Map<String, double[]> storeyZBands;

    public SqlBuildingGeometry(Connection compileDb) {
        this(compileDb, null);
    }

    public SqlBuildingGeometry(Connection compileDb, Map<String, double[]> storeyZBands) {
        this.compileDb = compileDb;
        this.storeyZBands = storeyZBands;
    }

    @Override
    public Point3D serviceRoomPosition(String disciplineCategory) {
        // §3.6.2: service room matched by m_product_category_id
        try (PreparedStatement ps = compileDb.prepareStatement(
                "SELECT dx, dy, dz FROM C_OrderLine"
                        + " WHERE m_product_category_id = ? AND IsActive = 1"
                        + " LIMIT 1")) {
            ps.setString(1, disciplineCategory);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Point3D pos = new Point3D(
                            rs.getDouble("dx") / 1000.0,
                            rs.getDouble("dy") / 1000.0,
                            rs.getDouble("dz") / 1000.0);
                    BIMLogger.fine(TAG, "serviceRoomPosition: cat={} pos={}", disciplineCategory, pos);
                    return pos;
                }
            }
        } catch (SQLException e) {
            BIMLogger.warn(TAG, "serviceRoomPosition query failed: {}", e.getMessage());
        }
        BIMLogger.fine(TAG, "serviceRoomPosition: cat={} not found, returning origin", disciplineCategory);
        return new Point3D(0, 0, 0);
    }

    @Override
    public List<FloorLevel> floors() {
        // Fix DISC_VALIDATION_DB_SRS §10.4.12 Gap 1 — ceiling Z from elements_rtree (absolute)
        // Same approach as P115 StoreyZBandProof.computeStoreyZBands()
        if (storeyZBands != null && !storeyZBands.isEmpty()) {
            // Use absolute Z from walked placements (meters → mm)
            List<FloorLevel> floors = new ArrayList<>();
            for (Map.Entry<String, double[]> entry : storeyZBands.entrySet()) {
                double minZMm = entry.getValue()[0] * 1000.0; // meters to mm
                floors.add(new FloorLevel(entry.getKey(), minZMm));
            }
            floors.sort(Comparator.comparingDouble(FloorLevel::zMm));
            BIMLogger.fine(TAG, "floors: {} levels from storeyZBands (absolute Z)", floors.size());
            return floors;
        }

        // Fallback: c_orderline FLOOR rows (BOM-relative offsets)
        List<FloorLevel> floors = new ArrayList<>();
        try (PreparedStatement ps = compileDb.prepareStatement(
                "SELECT family_ref, dz FROM C_OrderLine"
                        + " WHERE host_type = 'FLOOR' AND IsActive = 1"
                        + " ORDER BY dz ASC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    floors.add(new FloorLevel(
                            rs.getString("family_ref"),
                            rs.getDouble("dz")));
                }
            }
        } catch (SQLException e) {
            BIMLogger.warn(TAG, "floors query failed: {}", e.getMessage());
        }
        BIMLogger.fine(TAG, "floors: {} levels found (c_orderline fallback)", floors.size());
        return floors;
    }

    @Override
    public List<RoomTarget> roomsOnFloor(String floorRef) {
        List<RoomTarget> rooms = new ArrayList<>();
        try (PreparedStatement ps = compileDb.prepareStatement(
                "SELECT ol.family_ref, ol.dx, ol.dy, ol.dz, ol.Discipline"
                        + " FROM C_OrderLine ol"
                        + " JOIN C_OrderLine parent ON ol.Parent_OrderLine_ID = parent.C_OrderLine_ID"
                        + " WHERE parent.family_ref = ? AND ol.host_type = 'ROOM'"
                        + " AND ol.IsActive = 1")) {
            ps.setString(1, floorRef);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rooms.add(new RoomTarget(
                            rs.getString("family_ref"),
                            new Point3D(
                                    rs.getDouble("dx") / 1000.0,
                                    rs.getDouble("dy") / 1000.0,
                                    rs.getDouble("dz") / 1000.0),
                            rs.getString("Discipline")));
                }
            }
        } catch (SQLException e) {
            BIMLogger.warn(TAG, "roomsOnFloor query failed: {}", e.getMessage());
        }
        BIMLogger.fine(TAG, "roomsOnFloor: floor={} rooms={}", floorRef, rooms.size());
        return rooms;
    }

    @Override
    public RoomDimensions roomDimensions(String roomRef) {
        try (PreparedStatement ps = compileDb.prepareStatement(
                "SELECT aabb_width_mm, aabb_depth_mm, aabb_height_mm FROM C_OrderLine"
                        + " WHERE family_ref = ? AND IsActive = 1 LIMIT 1")) {
            ps.setString(1, roomRef);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    RoomDimensions dims = new RoomDimensions(
                            (int) rs.getDouble("aabb_width_mm"),
                            (int) rs.getDouble("aabb_depth_mm"),
                            (int) rs.getDouble("aabb_height_mm"));
                    BIMLogger.fine(TAG, "roomDimensions: room={} w={} d={} h={}",
                            roomRef, dims.widthMm(), dims.depthMm(), dims.heightMm());
                    return dims;
                }
            }
        } catch (SQLException e) {
            BIMLogger.warn(TAG, "roomDimensions query failed: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public double slabThickness(String floorRef) {
        // Look for STR slab element on this floor
        try (PreparedStatement ps = compileDb.prepareStatement(
                "SELECT aabb_height_mm FROM C_OrderLine"
                        + " WHERE family_ref LIKE ? AND host_type = 'LEAF'"
                        + " AND Discipline = 'STR' AND family_ref LIKE '%SLAB%'"
                        + " AND IsActive = 1 LIMIT 1")) {
            ps.setString(1, "%" + floorRef + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double thickness = rs.getDouble("aabb_height_mm");
                    if (thickness > 0) {
                        BIMLogger.fine(TAG, "slabThickness: floor={} thickness={:.0f}mm (from STR)",
                                floorRef, thickness);
                        return thickness;
                    }
                }
            }
        } catch (SQLException e) {
            BIMLogger.warn(TAG, "slabThickness query failed: {}", e.getMessage());
        }
        BIMLogger.fine(TAG, "slabThickness: floor={} default={:.0f}mm", floorRef, DEFAULT_SLAB_THICKNESS_MM);
        return DEFAULT_SLAB_THICKNESS_MM;
    }

    private static final double DEFAULT_WALL_THICKNESS_MM = 200;

    @Override
    public double wallThickness(String floorRef) {
        try (PreparedStatement ps = compileDb.prepareStatement(
                "SELECT MIN(CASE WHEN aabb_width_mm < aabb_depth_mm"
                        + " THEN aabb_width_mm ELSE aabb_depth_mm END) AS wall_thickness"
                        + " FROM C_OrderLine"
                        + " WHERE family_ref LIKE ? AND host_type = 'LEAF'"
                        + " AND family_ref LIKE '%Wall%'"
                        + " AND IsActive = 1")) {
            ps.setString(1, "%" + floorRef + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double thickness = rs.getDouble("wall_thickness");
                    if (thickness > 0) {
                        BIMLogger.fine(TAG, "wallThickness: floor={} thickness={:.0f}mm (from ARC)",
                                floorRef, thickness);
                        return thickness;
                    }
                }
            }
        } catch (SQLException e) {
            BIMLogger.warn(TAG, "wallThickness query failed: {}", e.getMessage());
        }
        BIMLogger.fine(TAG, "wallThickness: floor={} default={:.0f}mm", floorRef, DEFAULT_WALL_THICKNESS_MM);
        return DEFAULT_WALL_THICKNESS_MM;
    }

    @Override
    public double ceilingHeightMm(String floorRef) {
        // Implementing DISC_VALIDATION_DB_SRS §10.4.12 Gap 1 — ceiling void routing
        // ceilingHeight = nextFloor.zMm - slabThickness(nextFloor) - clearanceMm
        List<FloorLevel> allFloors = floors();
        for (int i = 0; i < allFloors.size(); i++) {
            if (allFloors.get(i).ref().equals(floorRef)) {
                double thisFloorZ = allFloors.get(i).zMm();
                double ceilingZ;
                if (i + 1 < allFloors.size()) {
                    // Next floor exists — ceiling = next floor Z - slab thickness - clearance
                    FloorLevel nextFloor = allFloors.get(i + 1);
                    ceilingZ = nextFloor.zMm() - slabThickness(nextFloor.ref()) - MEP_CLEARANCE_MM;
                } else {
                    // Top floor — use default storey height above this floor
                    ceilingZ = thisFloorZ + DEFAULT_STOREY_HEIGHT_MM - slabThickness(floorRef) - MEP_CLEARANCE_MM;
                }
                BIMLogger.fine(TAG, "ceilingHeightMm: floor={} ceiling={:.0f}mm (floorZ={:.0f}mm)",
                        floorRef, ceilingZ, thisFloorZ);
                return ceilingZ;
            }
        }
        // Fallback: not found
        BIMLogger.fine(TAG, "ceilingHeightMm: floor={} not found, using default", floorRef);
        return DEFAULT_STOREY_HEIGHT_MM - DEFAULT_SLAB_THICKNESS_MM - MEP_CLEARANCE_MM;
    }
}
