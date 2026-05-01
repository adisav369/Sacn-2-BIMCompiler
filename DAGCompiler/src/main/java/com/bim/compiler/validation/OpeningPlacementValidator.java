/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.validation;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.topology.BIMObjectType;

import java.util.List;

/**
 * Validates opening placement in walls.
 * From RELATIONSHIP PATTERNS in SESSION_STATE.md:
 * - Total Openings: 631
 * - Overlapping with Wall: 619 (98%)
 * - Fully contained in Wall: 349 (55%)
 *
 * From RULE 10:
 * - Standard: 665mm minimum from corner (UK), 700mm (US)
 * - DB Finding: NOT consistently enforced in this project
 */
public class OpeningPlacementValidator implements IValidator {

    @Override
    public String getName() {
        return "Opening Placement";
    }

    @Override
    public boolean appliesTo(ISpatialElement element) {
        return element.getType() == BIMObjectType.IFC_OPENING_ELEMENT;
    }

    @Override
    public ValidationResult validate(ISpatialElement element, List<ISpatialElement> allElements) {
        ValidationResult result = new ValidationResult();

        BoundingBox openingBox = element.getBoundingBox();
        boolean foundHostWall = false;
        boolean fullyContained = false;

        for (ISpatialElement other : allElements) {
            if (other.getType() != BIMObjectType.IFC_WALL) continue;

            BoundingBox wallBox = other.getBoundingBox();

            if (wallBox.overlaps(openingBox)) {
                foundHostWall = true;

                if (wallBox.contains(openingBox)) {
                    fullyContained = true;
                }
                break; // Found host wall
            }
        }

        if (!foundHostWall) {
            // 2% of openings don't overlap walls (per DB analysis)
            result.addWarning(element.getGuid(),
                "Opening does not overlap any wall - orphaned opening?");
        } else if (!fullyContained) {
            // 43% overlap but aren't fully contained (per DB analysis)
            // This is INFO, not warning - common for doors at wall edges
            result.addInfo(element.getGuid(),
                "Opening overlaps wall but extends beyond wall boundary");
        }

        return result;
    }
}
