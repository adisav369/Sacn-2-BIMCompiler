package com.bim.compiler.validation;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.topology.FederationConstants;
import com.bim.compiler.topology.BIMObjectType;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates sprinkler head spacing.
 * From RULE 6 in SESSION_STATE.md:
 * - Standard: NFPA 13 max spacing 4.6m (light hazard)
 * - DB Finding: Min 0mm (clustered), avg 6490mm (some exceed max)
 */
public class SprinklerSpacingValidator implements IValidator {

    @Override
    public String getName() {
        return "Sprinkler Spacing";
    }

    @Override
    public boolean appliesTo(ISpatialElement element) {
        return element.getType() == BIMObjectType.IFC_FIRE_SUPPRESSION_TERMINAL;
    }

    @Override
    public ValidationResult validate(ISpatialElement element, List<ISpatialElement> allElements) {
        ValidationResult result = new ValidationResult();

        // Collect all sprinklers on the same floor (similar Z level)
        Point3D position = element.getPosition();
        List<ISpatialElement> nearbySprinklers = new ArrayList<>();

        for (ISpatialElement other : allElements) {
            if (other == element) continue;
            if (other.getType() != BIMObjectType.IFC_FIRE_SUPPRESSION_TERMINAL) continue;

            Point3D otherPos = other.getPosition();
            // Same floor = within 1m Z difference
            if (Math.abs(otherPos.z() - position.z()) < 1.0) {
                nearbySprinklers.add(other);
            }
        }

        if (nearbySprinklers.isEmpty()) {
            // Isolated sprinkler - might be coverage gap
            result.addInfo(element.getGuid(),
                "Isolated sprinkler head - no other heads on same floor");
            return result;
        }

        // Find nearest sprinkler
        double minDistance = Double.MAX_VALUE;
        String nearestGuid = null;

        for (ISpatialElement other : nearbySprinklers) {
            double distance = position.horizontalDistanceTo(other.getPosition());
            if (distance < minDistance) {
                minDistance = distance;
                nearestGuid = other.getGuid();
            }
        }

        // Check spacing
        if (minDistance > FederationConstants.MAX_SPRINKLER_SPACING) {
            result.addWarning(element.getGuid(),
                "Sprinkler spacing %.2fm to nearest head %s exceeds NFPA max 4.6m",
                minDistance, nearestGuid);
        } else if (minDistance < 0.5) {
            // Too close - might be duplicate or error
            result.addInfo(element.getGuid(),
                "Sprinkler head very close (%.2fm) to %s - verify placement",
                minDistance, nearestGuid);
        }

        return result;
    }
}
