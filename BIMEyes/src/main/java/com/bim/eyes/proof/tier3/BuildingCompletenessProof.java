package com.bim.eyes.proof.tier3;

import com.bim.eyes.ProductCategory;
import com.bim.eyes.proof.ProofResult;

import java.sql.*;
import java.util.*;

/**
 * P26: BUILDING_COMPLETENESS — building has rooms, roof, external door, circulation.
 * // Implementing EYES_SRS.md §4.5 — Witness: W-BLDG-COMPLETE
 *
 * <p>Advisory proof. Checks aggregate building-level completeness:
 * rooms exist, roof exists, external door exists, stairs if multi-storey.
 */
public final class BuildingCompletenessProof {
    private BuildingCompletenessProof() {}

    public static List<ProofResult> prove(Connection conn) {
        List<ProofResult> results = new ArrayList<>();

        try {
            // Step 1: Count distinct storeys
            Set<String> storeys = new HashSet<>();
            // Step 2: Count rooms (IfcSpace)
            int roomCount = 0;
            // Step 3: Check categories
            boolean hasRoof = false;
            boolean hasDoor = false;
            boolean hasCirculation = false;

            // Building AABB for perimeter detection
            double bMinX = Double.MAX_VALUE, bMaxX = -Double.MAX_VALUE;
            double bMinY = Double.MAX_VALUE, bMaxY = -Double.MAX_VALUE;

            // Door AABBs for perimeter check
            List<double[]> doorBboxes = new ArrayList<>();

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT em.ifc_class, em.storey,
                            r.minX, r.maxX, r.minY, r.maxY
                     FROM elements_meta em
                     JOIN elements_rtree r ON em.id = r.id
                     """)) {
                while (rs.next()) {
                    String ifcClass = rs.getString("ifc_class");
                    String storey = rs.getString("storey");
                    if (storey != null && !storey.isEmpty()) {
                        storeys.add(storey);
                    }

                    double minX = rs.getDouble("minX"), maxX = rs.getDouble("maxX");
                    double minY = rs.getDouble("minY"), maxY = rs.getDouble("maxY");

                    // Update building AABB
                    if (minX < bMinX) bMinX = minX;
                    if (maxX > bMaxX) bMaxX = maxX;
                    if (minY < bMinY) bMinY = minY;
                    if (maxY > bMaxY) bMaxY = maxY;

                    String category = ProductCategory.resolve(ifcClass);

                    if ("IfcSpace".equals(ifcClass)) {
                        roomCount++;
                    }

                    if (ProductCategory.ENVELOPE.equals(category)
                            && "IfcRoof".equals(ifcClass)) {
                        hasRoof = true;
                    }

                    if (ProductCategory.OPENING.equals(category)
                            && "IfcDoor".equals(ifcClass)) {
                        hasDoor = true;
                        doorBboxes.add(new double[]{minX, maxX, minY, maxY});
                    }

                    if (ProductCategory.CIRCULATION.equals(category)) {
                        hasCirculation = true;
                    }
                }
            }

            // Step 3b: Check if any door is at building perimeter
            // (door AABB touches building AABB face within 500mm)
            boolean hasExternalDoor = false;
            double perimeterTol = 0.5; // 500mm
            for (double[] door : doorBboxes) {
                if (Math.abs(door[0] - bMinX) < perimeterTol
                        || Math.abs(door[1] - bMaxX) < perimeterTol
                        || Math.abs(door[2] - bMinY) < perimeterTol
                        || Math.abs(door[3] - bMaxY) < perimeterTol) {
                    hasExternalDoor = true;
                    break;
                }
            }

            // Build results
            List<String> missing = new ArrayList<>();

            // Rooms
            if (roomCount >= 1) {
                results.add(new ProofResult("P26_BUILDING_COMPLETENESS", ProofResult.Status.PROVEN,
                    null, "%d rooms found".formatted(roomCount), 0));
            } else {
                missing.add("rooms(0)");
            }

            // Roof
            if (hasRoof) {
                results.add(new ProofResult("P26_BUILDING_COMPLETENESS", ProofResult.Status.PROVEN,
                    null, "roof present", 0));
            } else {
                missing.add("roof");
            }

            // External door
            if (hasExternalDoor) {
                results.add(new ProofResult("P26_BUILDING_COMPLETENESS", ProofResult.Status.PROVEN,
                    null, "external door present", 0));
            } else if (hasDoor) {
                // Doors exist but none at perimeter — still flag as advisory
                missing.add("external_door(doors exist but none at perimeter)");
            } else {
                missing.add("door(none)");
            }

            // Circulation (only required if multi-storey)
            boolean multiStorey = storeys.size() > 1;
            if (multiStorey) {
                if (hasCirculation) {
                    results.add(new ProofResult("P26_BUILDING_COMPLETENESS", ProofResult.Status.PROVEN,
                        null, "circulation present (%d storeys)".formatted(storeys.size()), 0));
                } else {
                    missing.add("circulation(%d storeys, no stairs/ramps)".formatted(storeys.size()));
                }
            }

            if (!missing.isEmpty()) {
                results.add(new ProofResult("P26_BUILDING_COMPLETENESS", ProofResult.Status.VIOLATED,
                    null, "missing: %s".formatted(String.join(", ", missing)),
                    missing.size()));
            }

            if (results.isEmpty()) {
                results.add(new ProofResult("P26_BUILDING_COMPLETENESS", ProofResult.Status.SKIPPED,
                    null, "no elements found", 0));
            }

        } catch (SQLException e) {
            results.add(new ProofResult("P26_BUILDING_COMPLETENESS", ProofResult.Status.SKIPPED,
                null, "DB read failed: " + e.getMessage(), 0));
        }

        return results;
    }
}
