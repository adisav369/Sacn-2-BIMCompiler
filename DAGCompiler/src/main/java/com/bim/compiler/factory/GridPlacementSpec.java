/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.factory;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.Discipline;

/**
 * Specification for grid-based library placement.
 * Used for sprinkler grids, light grids, etc.
 */
public class GridPlacementSpec extends ElementSpec {

    private String libraryComponentId;
    private BoundingBox area;
    private double spacingX;
    private double spacingY;
    private double attachmentZ;  // Ceiling or floor height
    private String attachmentFace;

    public GridPlacementSpec() {
        super();
    }

    public static GridBuilder gridBuilder() { return new GridBuilder(); }

    public String getLibraryComponentId() { return libraryComponentId; }
    public BoundingBox getArea() { return area; }
    public double getSpacingX() { return spacingX; }
    public double getSpacingY() { return spacingY; }
    public double getSpacing() { return spacingX; }  // Convenience for uniform spacing
    public double getAttachmentZ() { return attachmentZ; }
    public String getAttachmentFace() { return attachmentFace; }

    @Override
    public boolean isParametric() { return false; }

    @Override
    public boolean isLibraryBased() { return true; }

    public static class GridBuilder {
        private final GridPlacementSpec spec = new GridPlacementSpec();

        public GridBuilder id(String id) { spec.id = id; return this; }
        public GridBuilder type(BIMObjectType type) { spec.type = type; return this; }
        public GridBuilder discipline(Discipline discipline) { spec.discipline = discipline; return this; }
        public GridBuilder libraryComponentId(String id) { spec.libraryComponentId = id; return this; }
        public GridBuilder area(BoundingBox area) { spec.area = area; return this; }
        public GridBuilder spacing(double spacing) {
            spec.spacingX = spacing;
            spec.spacingY = spacing;
            return this;
        }
        public GridBuilder spacingX(double spacingX) { spec.spacingX = spacingX; return this; }
        public GridBuilder spacingY(double spacingY) { spec.spacingY = spacingY; return this; }
        public GridBuilder attachmentZ(double z) { spec.attachmentZ = z; return this; }
        public GridBuilder attachmentFace(String face) { spec.attachmentFace = face; return this; }

        public GridPlacementSpec build() { return spec; }
    }
}
