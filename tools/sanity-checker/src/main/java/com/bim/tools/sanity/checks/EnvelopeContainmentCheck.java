package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.BoundingBox;
import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Check 7: Envelope Containment
 * Verifies all rooms are inside the building envelope.
 */
public class EnvelopeContainmentCheck implements SanityCheck {

    private static final double TOLERANCE = 0.05; // 50mm for wall thickness

    @Override
    public String getId() { return "envelope_containment"; }

    @Override
    public String getName() { return "Envelope Containment"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        List<Element> spaces = model.getSpaces();
        BoundingBox envelope = model.getBuildingEnvelope();

        if (envelope == null) {
            return CheckResult.fail(getId(), getName())
                .summary("Cannot determine building envelope")
                .guidance("Building needs walls to establish envelope")
                .build();
        }

        if (spaces.isEmpty()) {
            // Report the building envelope even if no rooms to check
            return CheckResult.warning(getId(), getName())
                .summary("No rooms to check - envelope containment cannot be verified")
                .detail(String.format("Building envelope: %.1fm × %.1fm (X: %.1f to %.1f, Y: %.1f to %.1f)",
                    envelope.width(), envelope.depth(),
                    envelope.minX(), envelope.maxX(),
                    envelope.minY(), envelope.maxY()))
                .detail("Envelope containment check requires explicit IfcSpace elements")
                .guidance("Add IfcSpace elements to enable containment validation")
                .build();
        }

        List<String> violations = new ArrayList<>();
        List<String> porchExceptions = new ArrayList<>();

        for (Element space : spaces) {
            if (space.bbox() == null) continue;

            String name = space.name() != null ? space.name() : space.guid();
            String spaceType = model.inferSpaceType(space);

            // PORCH is allowed to extend beyond main envelope
            if ("PORCH".equals(spaceType)) {
                if (!envelope.containsXY(space.bbox(), TOLERANCE)) {
                    porchExceptions.add(String.format("%s extends beyond main envelope (acceptable for porch)",
                        name));
                }
                continue;
            }

            if (!envelope.containsXY(space.bbox(), TOLERANCE)) {
                String violation = analyzeViolation(name, space.bbox(), envelope);
                violations.add(violation);
            }
        }

        if (!violations.isEmpty()) {
            CheckResult.Builder result = CheckResult.fail(getId(), getName())
                .summary(String.format("%d room(s) extend outside building envelope",
                    violations.size()));

            for (String v : violations) {
                result.detail(v);
            }

            result.detail(String.format("Building envelope: (%.1f,%.1f) to (%.1f,%.1f)",
                envelope.minX(), envelope.minY(), envelope.maxX(), envelope.maxY()));

            result.guidance("Adjust room positions or extend building walls");
            return result.build();
        }

        CheckResult.Builder result = CheckResult.pass(getId(), getName())
            .summary("All rooms inside building envelope");

        if (!porchExceptions.isEmpty()) {
            for (String p : porchExceptions) {
                result.detail(p);
            }
        }

        result.data("roomCount", spaces.size());
        return result.build();
    }

    private String analyzeViolation(String roomName, BoundingBox room, BoundingBox envelope) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("\"%s\" extends outside building:", roomName));

        double beyondWest = envelope.minX() - room.minX();
        double beyondEast = room.maxX() - envelope.maxX();
        double beyondSouth = envelope.minY() - room.minY();
        double beyondNorth = room.maxY() - envelope.maxY();

        if (beyondWest > TOLERANCE) {
            sb.append(String.format(" %.1fm beyond west wall,", beyondWest));
        }
        if (beyondEast > TOLERANCE) {
            sb.append(String.format(" %.1fm beyond east wall,", beyondEast));
        }
        if (beyondSouth > TOLERANCE) {
            sb.append(String.format(" %.1fm beyond south wall,", beyondSouth));
        }
        if (beyondNorth > TOLERANCE) {
            sb.append(String.format(" %.1fm beyond north wall,", beyondNorth));
        }

        String result = sb.toString();
        if (result.endsWith(",")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }
}
