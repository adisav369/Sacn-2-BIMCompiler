package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;
import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL implementation of {@link BuildingGeometry} — queries c_orderline in the compile DB.
 *
 * <p>All geometry comes from ARC-compiled data. No invention. Positions are in mm
 * (c_orderline.dx/dy/dz), converted to Point3D meters where needed.
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.10 — SqlBuildingGeometry
public class SqlBuildingGeometry implements BuildingGeometry {

    private static final String TAG = "CRAWL";
    private static final double DEFAULT_SLAB_THICKNESS_MM = 150.0;

    private final Connection compileDb;

    public SqlBuildingGeometry(Connection compileDb) {
        this.compileDb = compileDb;
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
        List<FloorLevel> floors = new ArrayList<>();
        try (PreparedStatement ps = compileDb.prepareStatement(
                "SELECT family_ref, dz FROM C_OrderLine"
                        + " WHERE host_type = 'IfcBuildingStorey' AND IsActive = 1"
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
        BIMLogger.fine(TAG, "floors: {} levels found", floors.size());
        return floors;
    }

    @Override
    public List<RoomTarget> roomsOnFloor(String floorRef) {
        List<RoomTarget> rooms = new ArrayList<>();
        try (PreparedStatement ps = compileDb.prepareStatement(
                "SELECT ol.family_ref, ol.dx, ol.dy, ol.dz, ol.Discipline"
                        + " FROM C_OrderLine ol"
                        + " JOIN C_OrderLine parent ON ol.Parent_OrderLine_ID = parent.C_OrderLine_ID"
                        + " WHERE parent.family_ref = ? AND ol.host_type = 'IfcSpace'"
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
                        + " WHERE family_ref LIKE ? AND host_type = 'IfcSlab'"
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
}
