/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.validation;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.WallThickness;

import java.util.List;

/**
 * Validates wall thickness against standard values.
 * From FACT 3 in SESSION_STATE.md:
 * - ONLY 4 VALUES exist: 150mm, 230mm, 250mm, 300mm
 * - 150mm: 306 walls (92%)
 * - 250mm: 18 walls (5%)
 * - 230mm: 6 walls (2%)
 * - 300mm: 3 walls (1%)
 */
public class WallThicknessValidator implements IValidator {

    @Override
    public String getName() {
        return "Wall Thickness";
    }

    @Override
    public boolean appliesTo(ISpatialElement element) {
        return element.getType() == BIMObjectType.IFC_WALL;
    }

    @Override
    public ValidationResult validate(ISpatialElement element, List<ISpatialElement> allElements) {
        ValidationResult result = new ValidationResult();

        BoundingBox bbox = element.getBoundingBox();

        // Wall thickness is the smallest horizontal dimension
        double thickness = estimateWallThickness(bbox);

        WallThickness standard = WallThickness.fromMeasurement(thickness);

        if (standard == null) {
            result.addWarning(element.getGuid(),
                "Wall thickness %.0fmm is non-standard (expected 150/230/250/300mm)",
                thickness * 1000);
        }

        return result;
    }

    /**
     * Estimate wall thickness from bounding box.
     * Walls are typically elongated in one horizontal direction,
     * so thickness is the smaller of width/depth.
     */
    private double estimateWallThickness(BoundingBox bbox) {
        double w = bbox.width();
        double d = bbox.depth();

        // Thickness is the smaller horizontal dimension
        return Math.min(w, d);
    }
}
