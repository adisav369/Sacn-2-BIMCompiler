package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;

import java.util.List;

/**
 * Read-only query interface over ARC-compiled c_orderline data.
 *
 * <p>The crawl is deterministic — every direction comes from geometry that ARC
 * already compiled. CrawlRouter never invents a direction. It reads positions
 * from c_orderline and navigates toward them. The BOM says WHAT to route.
 * The ARC output says WHERE.
 *
 * <p>Direction at each step:
 * <ul>
 *   <li><b>Start</b> → service room position from category match query</li>
 *   <li><b>Riser</b> → vertical (0,0,1), stop at each floor Z</li>
 *   <li><b>Floor header</b> → longest axis of floor AABB</li>
 *   <li><b>Branch to room</b> → normalise(room.pos - current.pos)</li>
 *   <li><b>Follow in room</b> → longest axis of room AABB</li>
 *   <li><b>Penetrate</b> → vertical through slab, thickness from STR or default</li>
 * </ul>
 */
// Implementing DISC_VALIDATION_DB_SRS §10.4.10 — BuildingGeometry query interface
public interface BuildingGeometry {

    /** Service room position for a discipline category (§3.6.2 category match). */
    Point3D serviceRoomPosition(String disciplineCategory);

    /** All floor levels ordered by Z ascending. */
    List<FloorLevel> floors();

    /** Rooms on a floor that need service routing. */
    List<RoomTarget> roomsOnFloor(String floorRef);

    /** AABB dimensions of a room (from c_orderline or m_bom). */
    RoomDimensions roomDimensions(String roomRef);

    /** Slab thickness for a floor (from STR data or default 150mm). */
    double slabThickness(String floorRef);

    /** Wall thickness for a floor (from ARC wall data or default 200mm). */
    double wallThickness(String floorRef);

    // Implementing DISC_VALIDATION_DB_SRS §10.4.12 Gap 1 — ceiling void routing
    // ceilingHeight = nextFloor.zMm - slabThickness(nextFloor) - clearanceMm

    /** Ceiling void Z for a floor = slab bottom of floor above minus clearance. */
    double ceilingHeightMm(String floorRef);

    /** Floor level: reference name + Z position. */
    record FloorLevel(String ref, double zMm) {}

    /** Room requiring service routing: reference, position, discipline. */
    record RoomTarget(String ref, Point3D position, String discipline) {}

    /** Room AABB dimensions in mm. */
    record RoomDimensions(int widthMm, int depthMm, int heightMm) {
        /** Longest horizontal axis — used for FOLLOW run length. */
        public int longestAxis() { return Math.max(widthMm, depthMm); }
    }
}
