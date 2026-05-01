/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.validation;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.Discipline;
import com.bim.compiler.topology.PipeDiameterRange;

import java.util.List;

/**
 * Validates pipe diameters against discipline-specific ranges.
 * From FACT 4 in SESSION_STATE.md:
 * - CW:  4-141mm,  55 unique sizes
 * - FP:  3-168mm,  32 unique sizes
 * - LPG: 3-383mm,  9 unique sizes
 * - SP:  3-6696mm, 99 unique sizes
 */
public class PipeDiameterValidator implements IValidator {

    @Override
    public String getName() {
        return "Pipe Diameter";
    }

    @Override
    public boolean appliesTo(ISpatialElement element) {
        BIMObjectType type = element.getType();
        Discipline discipline = element.getDiscipline();

        return (type == BIMObjectType.IFC_PIPE_SEGMENT || type == BIMObjectType.IFC_PIPE_FITTING)
            && PipeDiameterRange.isPipingDiscipline(discipline);
    }

    @Override
    public ValidationResult validate(ISpatialElement element, List<ISpatialElement> allElements) {
        ValidationResult result = new ValidationResult();

        Discipline discipline = element.getDiscipline();
        PipeDiameterRange.Range range = PipeDiameterRange.getRange(discipline);

        if (range == null) {
            return result; // Not a piping discipline
        }

        // Estimate diameter from bbox (min of width/depth for horizontal pipe)
        BoundingBox bbox = element.getBoundingBox();
        double diameterEstimate = estimatePipeDiameter(bbox);

        if (!range.contains(diameterEstimate)) {
            result.addError(element.getGuid(),
                "Pipe diameter %.0fmm outside %s range (%.0f-%.0fmm)",
                diameterEstimate * 1000,
                discipline.name(),
                range.minMm(),
                range.maxMm());
        }

        return result;
    }

    /**
     * Estimate pipe diameter from bounding box.
     * Assumes pipe is roughly cylindrical - diameter is the smaller
     * of the two non-length dimensions.
     */
    private double estimatePipeDiameter(BoundingBox bbox) {
        double w = bbox.width();
        double d = bbox.depth();
        double h = bbox.height();

        // The diameter is the minimum of the two smaller dimensions
        // (the largest dimension is the pipe length)
        double max = Math.max(w, Math.max(d, h));

        if (max == w) {
            return Math.min(d, h);
        } else if (max == d) {
            return Math.min(w, h);
        } else {
            return Math.min(w, d);
        }
    }
}
