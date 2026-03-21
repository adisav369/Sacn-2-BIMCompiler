package com.bim.eyes.proof.tier2;

import com.bim.eyes.EyesConstants;
import com.bim.eyes.ProductCategory;
import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;
import com.bim.eyes.proof.RelationalData.*;
import java.util.*;

/** P08: Every FURNISHING element centroid is within its host room. */
public final class FurnitureInRoomProof {
    private FurnitureInRoomProof() {}

    public static List<ProofResult> prove(
            List<PlacementData> placements, Map<String, RoomData> rooms,
            List<ElementRule> rules) {
        List<ProofResult> results = new ArrayList<>();
        boolean anyChecked = false;

        for (ElementRule rule : rules) {
            if (!"ROOM".equals(rule.hostType())) continue;
            if (!ProductCategory.FURNISHING.equals(rule.productCategory())) continue;

            PlacementData furn = OpeningContainmentProof.findPlacement(placements, rule.elementRef(), rule.ifcClass());
            if (furn == null) continue;

            RoomData room = rooms.get(rule.hostRef());
            if (room == null) continue;

            anyChecked = true;

            boolean inside = furn.cx() >= room.minX() - EyesConstants.CONTAINMENT_TOLERANCE_M
                          && furn.cx() <= room.maxX() + EyesConstants.CONTAINMENT_TOLERANCE_M
                          && furn.cy() >= room.minY() - EyesConstants.CONTAINMENT_TOLERANCE_M
                          && furn.cy() <= room.maxY() + EyesConstants.CONTAINMENT_TOLERANCE_M;

            if (inside) {
                results.add(new ProofResult("P08_FURNITURE_IN_ROOM", ProofResult.Status.PROVEN,
                    furn.guid(), "centroid in room %s".formatted(rule.hostRef()), 0));
            } else {
                double dx = Math.max(0, Math.max(room.minX() - furn.cx(), furn.cx() - room.maxX()));
                double dy = Math.max(0, Math.max(room.minY() - furn.cy(), furn.cy() - room.maxY()));
                double dist = Math.sqrt(dx * dx + dy * dy);
                results.add(new ProofResult("P08_FURNITURE_IN_ROOM", ProofResult.Status.VIOLATED,
                    furn.guid(),
                    "centroid %.4fm outside room %s".formatted(dist, rule.hostRef()), dist));
            }
        }

        if (!anyChecked) {
            results.add(new ProofResult("P08_FURNITURE_IN_ROOM", ProofResult.Status.SKIPPED,
                null, "no furniture rules with resolved rooms", 0));
        }
        return results;
    }
}
