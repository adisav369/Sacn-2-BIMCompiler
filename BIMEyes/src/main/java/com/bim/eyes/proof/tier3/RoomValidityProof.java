package com.bim.eyes.proof.tier3;

import com.bim.eyes.ProductCategory;
import com.bim.eyes.proof.ProofResult;
import com.bim.eyes.shape.ShapeArchetype;
import com.bim.eyes.shape.ShapeClassifier;

import java.sql.*;
import java.util.*;

/**
 * P25: ROOM_VALIDITY — every room (IfcSpace) has walls + floor + ceiling + door.
 * // Implementing EYES_SRS.md §4.4 — Witness: W-ROOM-VALID
 *
 * <p>Advisory proof. Queries IfcSpace from elements_meta as room AABBs,
 * then checks that each room contains the minimum required components.
 */
public final class RoomValidityProof {
    private RoomValidityProof() {}

    private static final double Z_TOLERANCE = 0.5; // 500mm

    /** Element within a room, classified by category + archetype. */
    private record RoomElement(String guid, String ifcClass, String productCategory,
            ShapeArchetype archetype, double cx, double cy, double cz,
            double dx, double dy, double dz) {}

    public static List<ProofResult> prove(Connection conn) {
        List<ProofResult> results = new ArrayList<>();

        // Step 1: Query IfcSpace elements as room AABBs
        List<double[]> roomBboxes = new ArrayList<>(); // [minX, maxX, minY, maxY, minZ, maxZ]
        List<String> roomGuids = new ArrayList<>();

        // Step 2: Query all non-space elements with classification
        List<RoomElement> elements = new ArrayList<>();

        try (Statement stmt = conn.createStatement()) {
            // Load rooms (IfcSpace)
            try (ResultSet rs = stmt.executeQuery("""
                    SELECT em.guid, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
                    FROM elements_meta em
                    JOIN elements_rtree r ON em.id = r.id
                    WHERE em.ifc_class = 'IfcSpace'
                    """)) {
                while (rs.next()) {
                    roomGuids.add(rs.getString("guid"));
                    roomBboxes.add(new double[]{
                        rs.getDouble("minX"), rs.getDouble("maxX"),
                        rs.getDouble("minY"), rs.getDouble("maxY"),
                        rs.getDouble("minZ"), rs.getDouble("maxZ")
                    });
                }
            }
        } catch (SQLException e) {
            results.add(new ProofResult("P25_ROOM_VALIDITY", ProofResult.Status.SKIPPED,
                null, "DB read failed: " + e.getMessage(), 0));
            return results;
        }

        if (roomBboxes.isEmpty()) {
            results.add(new ProofResult("P25_ROOM_VALIDITY", ProofResult.Status.SKIPPED,
                null, "no IfcSpace elements found", 0));
            return results;
        }

        try (Statement stmt = conn.createStatement()) {
            // Load all non-space elements
            try (ResultSet rs = stmt.executeQuery("""
                    SELECT em.guid, em.ifc_class,
                           r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
                    FROM elements_meta em
                    JOIN elements_rtree r ON em.id = r.id
                    WHERE em.ifc_class != 'IfcSpace'
                    """)) {
                while (rs.next()) {
                    String ifcClass = rs.getString("ifc_class");
                    String category = ProductCategory.resolve(ifcClass);
                    double dx = rs.getDouble("maxX") - rs.getDouble("minX");
                    double dy = rs.getDouble("maxY") - rs.getDouble("minY");
                    double dz = rs.getDouble("maxZ") - rs.getDouble("minZ");
                    double wMm = dx * 1000, dMm = dy * 1000, hMm = dz * 1000;
                    ShapeArchetype arch = ShapeClassifier.classifyArchetype(wMm, dMm, hMm);

                    elements.add(new RoomElement(
                        rs.getString("guid"), ifcClass, category, arch,
                        (rs.getDouble("minX") + rs.getDouble("maxX")) / 2,
                        (rs.getDouble("minY") + rs.getDouble("maxY")) / 2,
                        (rs.getDouble("minZ") + rs.getDouble("maxZ")) / 2,
                        dx, dy, dz));
                }
            }
        } catch (SQLException e) {
            results.add(new ProofResult("P25_ROOM_VALIDITY", ProofResult.Status.SKIPPED,
                null, "DB read failed: " + e.getMessage(), 0));
            return results;
        }

        // Step 3: For each room, find contained elements and check components
        for (int r = 0; r < roomBboxes.size(); r++) {
            double[] room = roomBboxes.get(r);
            String roomGuid = roomGuids.get(r);
            double rMinX = room[0], rMaxX = room[1];
            double rMinY = room[2], rMaxY = room[3];
            double rMinZ = room[4], rMaxZ = room[5];

            int wallCount = 0;
            int floorCount = 0;
            int ceilingCount = 0;
            int doorCount = 0;

            for (RoomElement el : elements) {
                // Centroid within room AABB?
                if (el.cx() < rMinX || el.cx() > rMaxX) continue;
                if (el.cy() < rMinY || el.cy() > rMaxY) continue;
                // Z: element centroid within room Z range (with tolerance)
                if (el.cz() < rMinZ - Z_TOLERANCE || el.cz() > rMaxZ + Z_TOLERANCE) continue;

                // Classify contribution
                if (ProductCategory.STRUCTURAL_PLANAR.equals(el.productCategory())) {
                    if (el.archetype() == ShapeArchetype.PLANAR && el.dz() > el.dx() && el.dz() > el.dy()) {
                        // Vertical planar = wall (height > width and depth)
                        wallCount++;
                    } else if (el.archetype() == ShapeArchetype.PLANAR) {
                        // Horizontal planar — check if floor or ceiling
                        if (Math.abs(el.cz() - rMinZ) < Z_TOLERANCE) {
                            floorCount++;
                        }
                        if (Math.abs(el.cz() - rMaxZ) < Z_TOLERANCE) {
                            ceilingCount++;
                        }
                    }
                }

                if (ProductCategory.OPENING.equals(el.productCategory())
                        && el.archetype() == ShapeArchetype.PLANAR
                        && "IfcDoor".equals(el.ifcClass())) {
                    doorCount++;
                }
            }

            // Build diagnostic
            List<String> missing = new ArrayList<>();
            if (wallCount < 2) missing.add("walls(%d<2)".formatted(wallCount));
            if (floorCount < 1) missing.add("floor(%d<1)".formatted(floorCount));
            if (ceilingCount < 1) missing.add("ceiling(%d<1)".formatted(ceilingCount));
            if (doorCount < 1) missing.add("door(%d<1)".formatted(doorCount));

            if (missing.isEmpty()) {
                results.add(new ProofResult("P25_ROOM_VALIDITY", ProofResult.Status.PROVEN,
                    roomGuid, "walls=%d floor=%d ceiling=%d door=%d".formatted(
                        wallCount, floorCount, ceilingCount, doorCount), 0));
            } else {
                results.add(new ProofResult("P25_ROOM_VALIDITY", ProofResult.Status.VIOLATED,
                    roomGuid, "missing: %s".formatted(String.join(", ", missing)),
                    missing.size()));
            }
        }
        return results;
    }
}
