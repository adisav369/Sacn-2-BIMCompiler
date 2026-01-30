package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.Element;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.util.List;

/**
 * Check 1: Foundation Ground Level
 * Verifies the building sits on the ground (Z≈0).
 */
public class FoundationCheck implements SanityCheck {
    private static final double GROUND_TOLERANCE = 0.05; // 50mm

    @Override
    public String getId() { return "foundation_ground_level"; }

    @Override
    public String getName() { return "Foundation Ground Level"; }

    @Override
    public CheckResult execute(SanityModel model) {
        List<Element> foundations = model.getFoundations();

        if (foundations.isEmpty()) {
            // No explicit foundation - check lowest slab
            double minZ = Double.MAX_VALUE;
            for (Element elem : model.getAllElements()) {
                if (elem.isSlab() && elem.bbox() != null) {
                    minZ = Math.min(minZ, elem.bbox().minZ());
                }
            }

            if (minZ == Double.MAX_VALUE) {
                return CheckResult.fail(getId(), getName())
                    .summary("No foundation or slab found")
                    .detail("Building has no foundation elements")
                    .guidance("Add foundation slab at Z=0")
                    .build();
            }

            if (Math.abs(minZ) <= GROUND_TOLERANCE) {
                return CheckResult.pass(getId(), getName())
                    .summary(String.format("Lowest slab at ground level (Z=%.3fm)", minZ))
                    .data("minZ", minZ)
                    .build();
            } else {
                return CheckResult.fail(getId(), getName())
                    .summary(String.format("Lowest slab at Z=%.3fm (should be at Z=0)", minZ))
                    .detail(minZ > 0 ?
                        String.format("Building is floating %.1fm above ground", minZ) :
                        String.format("Building is buried %.1fm below ground", Math.abs(minZ)))
                    .guidance("Adjust foundation to Z=0")
                    .data("minZ", minZ)
                    .build();
            }
        }

        // Check explicit foundations
        double minFoundationZ = Double.MAX_VALUE;
        for (Element foundation : foundations) {
            if (foundation.bbox() != null) {
                minFoundationZ = Math.min(minFoundationZ, foundation.bbox().minZ());
            }
        }

        if (minFoundationZ == Double.MAX_VALUE) {
            return CheckResult.fail(getId(), getName())
                .summary("Foundation has no geometry")
                .guidance("Foundation elements need bounding boxes")
                .build();
        }

        if (Math.abs(minFoundationZ) <= GROUND_TOLERANCE) {
            return CheckResult.pass(getId(), getName())
                .summary(String.format("Foundation at ground level (Z=%.3fm)", minFoundationZ))
                .data("minZ", minFoundationZ)
                .build();
        } else if (minFoundationZ > 0) {
            return CheckResult.fail(getId(), getName())
                .summary(String.format("Foundation at Z=%.3fm (should be at Z=0)", minFoundationZ))
                .detail(String.format("Building is floating %.1fm above ground", minFoundationZ))
                .guidance("Move foundation to Z=0")
                .data("minZ", minFoundationZ)
                .build();
        } else {
            return CheckResult.fail(getId(), getName())
                .summary(String.format("Foundation at Z=%.3fm (should be at Z=0)", minFoundationZ))
                .detail(String.format("Foundation is buried %.1fm below ground", Math.abs(minFoundationZ)))
                .guidance("Adjust foundation top surface to Z=0")
                .data("minZ", minFoundationZ)
                .build();
        }
    }
}
