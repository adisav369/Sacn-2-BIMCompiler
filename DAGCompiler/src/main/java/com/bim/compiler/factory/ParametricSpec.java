/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.factory;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.Discipline;

/**
 * Specification for parametric elements with dynamic dimensions.
 * Used for walls, slabs, pipes, ducts.
 */
public class ParametricSpec extends ElementSpec {

    private Point3D start;
    private Point3D end;
    private double width;
    private double height;
    private double thickness;

    public ParametricSpec() {
        super();
    }

    public ParametricSpec(String id, BIMObjectType type, Discipline discipline,
                          Point3D start, Point3D end, double width, double height) {
        super(id, type, discipline);
        this.start = start;
        this.end = end;
        this.width = width;
        this.height = height;
    }

    // Builder pattern for convenience
    public static Builder builder() { return new Builder(); }

    public Point3D getStart() { return start; }
    public Point3D getEnd() { return end; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public double getThickness() { return thickness; }

    @Override
    public boolean isParametric() { return true; }

    @Override
    public boolean isLibraryBased() { return false; }

    public static class Builder {
        private final ParametricSpec spec = new ParametricSpec();

        public Builder id(String id) { spec.id = id; return this; }
        public Builder type(BIMObjectType type) { spec.type = type; return this; }
        public Builder discipline(Discipline discipline) { spec.discipline = discipline; return this; }
        public Builder start(Point3D start) { spec.start = start; return this; }
        public Builder end(Point3D end) { spec.end = end; return this; }
        public Builder width(double width) { spec.width = width; return this; }
        public Builder height(double height) { spec.height = height; return this; }
        public Builder thickness(double thickness) { spec.thickness = thickness; return this; }

        public ParametricSpec build() { return spec; }
    }
}
