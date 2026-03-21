package com.bim.eyes.proof.tier2;

import com.bim.eyes.EyesConstants;
import com.bim.eyes.ProductCategory;
import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;
import com.bim.eyes.proof.RelationalData.*;
import java.util.*;

/** P15: Every MEP_ROUTING element bbox is within its host room bbox. */
public final class PipeInHostProof {
    private PipeInHostProof() {}

    public static List<ProofResult> prove(
            List<PlacementData> placements, Map<String, RoomData> rooms,
            List<ElementRule> rules) {
        List<ProofResult> results = new ArrayList<>();
        boolean anyChecked = false;

        for (ElementRule rule : rules) {
            if (!"ROOM".equals(rule.hostType())) continue;
            if (!ProductCategory.MEP_ROUTING.equals(rule.productCategory())) continue;

            PlacementData pipe = OpeningContainmentProof.findPlacement(placements, rule.elementRef(), rule.ifcClass());
            if (pipe == null) continue;

            RoomData room = rooms.get(rule.hostRef());
            if (room == null) continue;

            anyChecked = true;

            double exMinX = room.minX() - pipe.minX();
            double exMaxX = pipe.maxX() - room.maxX();
            double exMinY = room.minY() - pipe.minY();
            double exMaxY = pipe.maxY() - room.maxY();
            double maxExceedance = Math.max(0,
                Math.max(exMinX, Math.max(exMaxX, Math.max(exMinY, exMaxY))));

            if (maxExceedance <= EyesConstants.CONTAINMENT_TOLERANCE_M) {
                results.add(new ProofResult("P15_PIPE_IN_HOST", ProofResult.Status.PROVEN,
                    pipe.guid(), "%s in room %s (exceed=%.4f)".formatted(
                        rule.elementRef(), rule.hostRef(), maxExceedance),
                    maxExceedance));
            } else {
                results.add(new ProofResult("P15_PIPE_IN_HOST", ProofResult.Status.VIOLATED,
                    pipe.guid(), "%s exceeds room %s by %.4fm".formatted(
                        rule.elementRef(), rule.hostRef(), maxExceedance),
                    maxExceedance));
            }
        }

        if (!anyChecked) {
            results.add(new ProofResult("P15_PIPE_IN_HOST", ProofResult.Status.SKIPPED,
                null, "no pipe rules with resolved rooms", 0));
        }
        return results;
    }
}
