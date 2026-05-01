/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.drawing2d.model;

/**
 * Paper sheet definition — size, margins, scale, title block.
 *
 * All dimensions in millimetres (paper space). The drawing engine
 * converts world metres → paper mm using the scale factor.
 *
 * Default: A3 landscape (420×297mm) at 1:100.
 */
public class DrawingSheet {

    private final double widthMm;
    private final double heightMm;
    private final double marginLeft;
    private final double marginRight;
    private final double marginTop;
    private final double marginBottom;
    private final double titleBlockWidthMm;
    private final int scale;

    public DrawingSheet(double widthMm, double heightMm,
                        double marginLeft, double marginRight,
                        double marginTop, double marginBottom,
                        double titleBlockWidthMm, int scale) {
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.marginLeft = marginLeft;
        this.marginRight = marginRight;
        this.marginTop = marginTop;
        this.marginBottom = marginBottom;
        this.titleBlockWidthMm = titleBlockWidthMm;
        this.scale = scale;
    }

    /** A3 landscape, JKR/TB-LKTN standard margins, 1:100. */
    public static DrawingSheet a3Landscape() {
        return new DrawingSheet(420, 297, 25, 10, 10, 10, 120, 100);
    }

    /** Content zone width: paper width minus margins, title block, and annotation zones. */
    public double contentWidth(double levelZoneMm, double heightZoneMm) {
        return widthMm - marginLeft - marginRight - levelZoneMm - heightZoneMm - titleBlockWidthMm;
    }

    /** Convert world metres to model-space mm (world_m × 1000 / scale × scale = world_m × 1000). */
    public double toModelSpace(double worldMetres) {
        return worldMetres * 1000.0;
    }

    /** Convert paper mm to model-space mm. */
    public double paperToModel(double paperMm) {
        return paperMm * scale;
    }

    // Accessors
    public double widthMm()          { return widthMm; }
    public double heightMm()         { return heightMm; }
    public double marginLeft()       { return marginLeft; }
    public double marginRight()      { return marginRight; }
    public double marginTop()        { return marginTop; }
    public double marginBottom()     { return marginBottom; }
    public double titleBlockWidthMm(){ return titleBlockWidthMm; }
    public int scale()               { return scale; }
}
