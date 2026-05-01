/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.validation;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.topology.FederationConstants;
import com.bim.compiler.topology.BIMObjectType;

import java.util.List;

/**
 * Flags elements that span multiple stories.
 * From FACT 5 in SESSION_STATE.md:
 * - IfcReinforcingBar: 590 elements, avg 27m height (continuous rebar)
 * - IfcSlab: 236 elements, avg 30m (ramp slabs)
 * - IfcColumn: 90 elements, avg 13-22m (full-height columns)
 * - IfcWall: 85 elements, avg 11-22m (atrium/curtain walls)
 *
 * Threshold: 8m (2 floors at 4m floor-to-floor)
 *
 * This is INFORMATIONAL, not an error - multi-story elements are valid.
 */
public class MultiStoryElementValidator implements IValidator {

    @Override
    public String getName() {
        return "Multi-Story Element";
    }

    @Override
    public boolean appliesTo(ISpatialElement element) {
        // Apply to types known to span floors
        BIMObjectType type = element.getType();
        return type == BIMObjectType.IFC_REINFORCING_BAR
            || type == BIMObjectType.IFC_SLAB
            || type == BIMObjectType.IFC_COLUMN
            || type == BIMObjectType.IFC_WALL
            || type == BIMObjectType.IFC_BEAM
            || type == BIMObjectType.IFC_MEMBER;
    }

    @Override
    public ValidationResult validate(ISpatialElement element, List<ISpatialElement> allElements) {
        ValidationResult result = new ValidationResult();

        BoundingBox bbox = element.getBoundingBox();
        double height = bbox.height();

        if (height > FederationConstants.MULTI_STORY_THRESHOLD) {
            int floors = (int) Math.ceil(height / FederationConstants.FLOOR_TO_FLOOR);

            result.addInfo(element.getGuid(),
                "%s spans %.1fm (approx %d floors) - multi-story element",
                element.getType().name(),
                height,
                floors);
        }

        return result;
    }
}
