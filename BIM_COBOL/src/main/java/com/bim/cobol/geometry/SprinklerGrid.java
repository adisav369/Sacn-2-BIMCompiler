package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry — generates sprinkler head grid positions within a rectangular room AABB.
 *
 * <p>All coordinates in meters. First head placed at half-spacing offset from room min corner;
 * subsequent heads at spacing intervals. Rooms narrower than spacing get a single centered head.
 */
public final class SprinklerGrid {

    private SprinklerGrid() {}

    /**
     * Generate sprinkler head positions within a rectangular room.
     *
     * @param roomMinX  room AABB min X (meters)
     * @param roomMaxX  room AABB max X (meters)
     * @param roomMinY  room AABB min Y (meters)
     * @param roomMaxY  room AABB max Y (meters)
     * @param ceilingZ  ceiling height (meters)
     * @param spacingM  grid spacing between heads (meters)
     * @return list of head positions in world coordinates
     */
    public static List<Point3D> generate(double roomMinX, double roomMaxX,
                                         double roomMinY, double roomMaxY,
                                         double ceilingZ, double spacingM) {
        double widthM = roomMaxX - roomMinX;
        double heightM = roomMaxY - roomMinY;
        double half = spacingM / 2.0;

        // X axis: if room narrower than spacing, center single head
        int nx;
        double startX, stepX;
        if (widthM < spacingM) {
            nx = 1;
            startX = roomMinX + widthM / 2.0;
            stepX = 0;
        } else {
            nx = (int) Math.floor((widthM - half) / spacingM) + 1;
            startX = roomMinX + half;
            stepX = spacingM;
        }

        // Y axis: same logic
        int ny;
        double startY, stepY;
        if (heightM < spacingM) {
            ny = 1;
            startY = roomMinY + heightM / 2.0;
            stepY = 0;
        } else {
            ny = (int) Math.floor((heightM - half) / spacingM) + 1;
            startY = roomMinY + half;
            stepY = spacingM;
        }

        List<Point3D> heads = new ArrayList<>(nx * ny);
        for (int ix = 0; ix < nx; ix++) {
            for (int iy = 0; iy < ny; iy++) {
                heads.add(new Point3D(
                        startX + ix * stepX,
                        startY + iy * stepY,
                        ceilingZ));
            }
        }
        return heads;
    }
}
