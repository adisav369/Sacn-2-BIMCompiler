package com.bim.compiler.validation;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.topology.FederationConstants;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.Discipline;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Validates MEP-to-Structure clearance.
 * From RULE 1 in SESSION_STATE.md:
 * - Standard: 50mm minimum clearance between MEP and structure
 * - DB Finding: Min gap 0mm (violations exist!), avg 614mm
 */
public class MepStructureClearanceValidator implements IValidator {

    private static final Set<BIMObjectType> STRUCTURAL_TYPES = EnumSet.of(
        BIMObjectType.IFC_BEAM,
        BIMObjectType.IFC_COLUMN,
        BIMObjectType.IFC_SLAB,
        BIMObjectType.IFC_MEMBER
    );

    @Override
    public String getName() {
        return "MEP-Structure Clearance";
    }

    @Override
    public boolean appliesTo(ISpatialElement element) {
        return element.getDiscipline().isMEP();
    }

    @Override
    public ValidationResult validate(ISpatialElement element, List<ISpatialElement> allElements) {
        ValidationResult result = new ValidationResult();

        BoundingBox mepBox = element.getBoundingBox();
        BoundingBox searchBox = mepBox.expand(FederationConstants.MEP_STRUCTURE_CLEARANCE);

        for (ISpatialElement other : allElements) {
            if (!STRUCTURAL_TYPES.contains(other.getType())) {
                continue;
            }
            if (other.getDiscipline() != Discipline.STR) {
                continue;
            }

            BoundingBox structBox = other.getBoundingBox();

            // Check if within clearance zone
            if (!searchBox.overlaps(structBox)) {
                continue;
            }

            // Calculate actual clearance (minimum distance between boxes)
            double clearance = minBoxDistance(mepBox, structBox);

            if (clearance < 0) {
                // Overlapping - could be intentional (pipe through slab)
                result.addWarning(element.getGuid(),
                    "MEP element overlaps structure %s (penetration or clash)",
                    other.getGuid());
            } else if (clearance < FederationConstants.MEP_STRUCTURE_CLEARANCE) {
                result.addWarning(element.getGuid(),
                    "MEP element within %.0fmm of structure %s (min 50mm required)",
                    clearance * 1000, other.getGuid());
            }
        }

        return result;
    }

    /**
     * Calculate minimum distance between two bounding boxes.
     * Returns negative value if boxes overlap.
     */
    private double minBoxDistance(BoundingBox a, BoundingBox b) {
        double dx = Math.max(0, Math.max(a.minX() - b.maxX(), b.minX() - a.maxX()));
        double dy = Math.max(0, Math.max(a.minY() - b.maxY(), b.minY() - a.maxY()));
        double dz = Math.max(0, Math.max(a.minZ() - b.maxZ(), b.minZ() - a.maxZ()));

        if (dx == 0 && dy == 0 && dz == 0) {
            // Boxes overlap - return negative overlap depth
            double overlapX = Math.min(a.maxX(), b.maxX()) - Math.max(a.minX(), b.minX());
            double overlapY = Math.min(a.maxY(), b.maxY()) - Math.max(a.minY(), b.minY());
            double overlapZ = Math.min(a.maxZ(), b.maxZ()) - Math.max(a.minZ(), b.minZ());
            return -Math.min(overlapX, Math.min(overlapY, overlapZ));
        }

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
