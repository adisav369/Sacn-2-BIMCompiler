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
    public String getId() { return "foundation"; }

    @Override
    public String getName() { return "Foundation Ground Level"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        // This check doesn't use AD thresholds - context ignored
        List<Element> foundations = model.getFoundations();

        if (foundations.isEmpty()) {
            // No explicit foundation - check lowest slab's top surface
            double lowestSlabTop = Double.MAX_VALUE;
            for (Element elem : model.getAllElements()) {
                if (elem.isSlab() && elem.bbox() != null) {
                    // For ground floor slab, check if top is near Z=0
                    if (elem.bbox().maxZ() <= 0.5) { // Only consider slabs near ground
                        lowestSlabTop = Math.min(lowestSlabTop, elem.bbox().maxZ());
                    }
                }
            }

            if (lowestSlabTop == Double.MAX_VALUE) {
                return CheckResult.fail(getId(), getName())
                    .summary("No foundation or ground slab found")
                    .detail("Building has no foundation elements")
                    .guidance("Add foundation slab at Z=0")
                    .build();
            }

            if (Math.abs(lowestSlabTop) <= GROUND_TOLERANCE) {
                return CheckResult.pass(getId(), getName())
                    .summary(String.format("Ground slab top at Z=%.3fm", lowestSlabTop))
                    .data("slabTopZ", lowestSlabTop)
                    .build();
            } else {
                return CheckResult.fail(getId(), getName())
                    .summary(String.format("Ground slab top at Z=%.3fm (should be at Z=0)", lowestSlabTop))
                    .detail(lowestSlabTop > 0 ?
                        String.format("Floor level is %.0fmm above ground", lowestSlabTop * 1000) :
                        String.format("Floor level is %.0fmm below ground", Math.abs(lowestSlabTop) * 1000))
                    .guidance("Adjust slab so top surface is at Z=0")
                    .data("slabTopZ", lowestSlabTop)
                    .build();
            }
        }

        // Check explicit foundations
        // Convention: Foundation TOP surface (maxZ) should be at Z=0 (floor level)
        // Foundation bottom (minZ) can be below ground (slab thickness)
        double foundationTopZ = Double.NEGATIVE_INFINITY;
        double foundationBottomZ = Double.MAX_VALUE;
        for (Element foundation : foundations) {
            if (foundation.bbox() != null) {
                foundationTopZ = Math.max(foundationTopZ, foundation.bbox().maxZ());
                foundationBottomZ = Math.min(foundationBottomZ, foundation.bbox().minZ());
            }
        }

        if (foundationTopZ == Double.NEGATIVE_INFINITY) {
            return CheckResult.fail(getId(), getName())
                .summary("Foundation has no geometry")
                .guidance("Foundation elements need bounding boxes")
                .build();
        }

        double thickness = foundationTopZ - foundationBottomZ;

        if (Math.abs(foundationTopZ) <= GROUND_TOLERANCE) {
            return CheckResult.pass(getId(), getName())
                .summary(String.format("Foundation top at ground level (Z=%.3fm, thickness=%.0fmm)",
                    foundationTopZ, thickness * 1000))
                .data("topZ", foundationTopZ)
                .data("bottomZ", foundationBottomZ)
                .data("thickness", thickness)
                .build();
        } else if (foundationTopZ > GROUND_TOLERANCE) {
            return CheckResult.fail(getId(), getName())
                .summary(String.format("Foundation top at Z=%.3fm (should be at Z=0)", foundationTopZ))
                .detail(String.format("Floor level is %.0fmm above ground", foundationTopZ * 1000))
                .guidance("Adjust foundation so top surface is at Z=0")
                .data("topZ", foundationTopZ)
                .build();
        } else {
            return CheckResult.fail(getId(), getName())
                .summary(String.format("Foundation top at Z=%.3fm (should be at Z=0)", foundationTopZ))
                .detail(String.format("Floor level is %.0fmm below ground", Math.abs(foundationTopZ) * 1000))
                .guidance("Adjust foundation so top surface is at Z=0")
                .data("topZ", foundationTopZ)
                .build();
        }
    }
}
