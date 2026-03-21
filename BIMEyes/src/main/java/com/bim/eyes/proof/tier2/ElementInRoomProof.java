package com.bim.eyes.proof.tier2;

import com.bim.eyes.EyesConstants;
import com.bim.eyes.ProductCategory;
import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;
import com.bim.eyes.proof.RelationalData.*;
import java.util.*;

/** P21: Non-structural elements bbox contained within rooms. */
public final class ElementInRoomProof {
    private ElementInRoomProof() {}

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

            double exMinX = room.minX() - furn.minX();
            double exMaxX = furn.maxX() - room.maxX();
            double exMinY = room.minY() - furn.minY();
            double exMaxY = furn.maxY() - room.maxY();
            double maxExceedance = Math.max(0,
                Math.max(exMinX, Math.max(exMaxX, Math.max(exMinY, exMaxY))));

            if (maxExceedance <= EyesConstants.CONTAINMENT_TOLERANCE_M) {
                results.add(new ProofResult("P21_ELEMENT_IN_ROOM", ProofResult.Status.PROVEN,
                    furn.guid(), "%s bbox within room %s (exceed=%.4f)".formatted(
                        rule.elementRef(), rule.hostRef(), maxExceedance),
                    maxExceedance));
            } else {
                results.add(new ProofResult("P21_ELEMENT_IN_ROOM", ProofResult.Status.VIOLATED,
                    furn.guid(), "%s bbox exceeds room %s by %.4fm".formatted(
                        rule.elementRef(), rule.hostRef(), maxExceedance),
                    maxExceedance));
            }
        }

        if (!anyChecked) {
            results.add(new ProofResult("P21_ELEMENT_IN_ROOM", ProofResult.Status.SKIPPED,
                null, "no furniture rules with resolved rooms", 0));
        }
        return results;
    }
}
