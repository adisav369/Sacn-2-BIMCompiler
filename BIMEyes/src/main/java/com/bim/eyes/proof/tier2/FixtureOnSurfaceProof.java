package com.bim.eyes.proof.tier2;

import com.bim.eyes.EyesConstants;
import com.bim.eyes.proof.PlacementData;
import com.bim.eyes.proof.ProofResult;
import com.bim.eyes.proof.RelationalData.*;
import java.util.*;

/** P09: Every hosted fixture shares at least one face with its host surface. */
public final class FixtureOnSurfaceProof {
    private FixtureOnSurfaceProof() {}

    private static final Set<String> FIXTURE_CLASSES = Set.of("IfcSanitaryTerminal", "IfcFlowTerminal");

    public static List<ProofResult> prove(
            List<PlacementData> placements, Map<String, WallFaceData> wallFaces,
            List<ElementRule> rules) {
        List<ProofResult> results = new ArrayList<>();
        boolean anyChecked = false;

        for (ElementRule rule : rules) {
            if (!"WALL".equals(rule.hostType())) continue;
            if (!FIXTURE_CLASSES.contains(rule.ifcClass())) continue;

            PlacementData fixture = OpeningContainmentProof.findPlacement(placements, rule.elementRef(), rule.ifcClass());
            if (fixture == null) continue;

            WallFaceData wall = wallFaces.get(rule.hostRef());
            if (wall == null) continue;

            anyChecked = true;

            double gap;
            if (wall.runsNS()) {
                gap = Math.min(
                    Math.abs(fixture.minX() - wall.wallX()),
                    Math.abs(fixture.maxX() - wall.wallX()));
            } else {
                gap = Math.min(
                    Math.abs(fixture.minY() - wall.wallY()),
                    Math.abs(fixture.maxY() - wall.wallY()));
            }

            if (gap <= EyesConstants.CONTAINMENT_TOLERANCE_M) {
                results.add(new ProofResult("P09_FIXTURE_ON_SURFACE", ProofResult.Status.PROVEN,
                    fixture.guid(), "gap=%.4fm to wall %s".formatted(gap, rule.hostRef()), gap));
            } else {
                results.add(new ProofResult("P09_FIXTURE_ON_SURFACE", ProofResult.Status.VIOLATED,
                    fixture.guid(),
                    "gap=%.4fm from wall %s (>%.3f)".formatted(gap, rule.hostRef(), EyesConstants.CONTAINMENT_TOLERANCE_M),
                    gap));
            }
        }

        if (!anyChecked) {
            results.add(new ProofResult("P09_FIXTURE_ON_SURFACE", ProofResult.Status.SKIPPED,
                null, "no fixture rules with resolved walls", 0));
        }
        return results;
    }
}
