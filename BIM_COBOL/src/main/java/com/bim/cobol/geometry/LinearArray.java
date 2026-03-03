package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry — generates positions for a 1D linear array (rebar, ties, etc.).
 *
 * <p>Input parameters (hostLength, spacing, cover) are in millimeters.
 * Output positions are in meters. Conversion happens internally.
 *
 * <p>Count formula: floor((hostLength - 2 × cover) / spacing) + 1
 */
public final class LinearArray {

    private LinearArray() {}

    /** Axis direction for the array. */
    public enum Direction { X, Y, Z }

    /** Array parameters and computed results. */
    public record ArrayResult(int count, double coverMm, double spacingMm,
                              double hostLengthMm, List<Point3D> positions) {}

    /**
     * Compute element array along a single axis.
     *
     * @param startX       start position X (meters)
     * @param startY       start position Y (meters)
     * @param startZ       start position Z (meters)
     * @param hostLengthMm host element length (mm)
     * @param spacingMm    spacing between elements (mm)
     * @param coverMm      concrete cover at each end (mm)
     * @param direction    axis along which to array
     * @return ArrayResult with count, positions, and input parameters
     */
    public static ArrayResult generate(double startX, double startY, double startZ,
                                        double hostLengthMm, double spacingMm,
                                        double coverMm, Direction direction) {
        int count = (int) Math.floor((hostLengthMm - 2.0 * coverMm) / spacingMm) + 1;
        if (count < 1) count = 1;

        List<Point3D> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double offsetM = (coverMm + i * spacingMm) / 1000.0;
            double x = startX;
            double y = startY;
            double z = startZ;
            switch (direction) {
                case X -> x += offsetM;
                case Y -> y += offsetM;
                case Z -> z += offsetM;
            }
            positions.add(new Point3D(x, y, z));
        }
        return new ArrayResult(count, coverMm, spacingMm, hostLengthMm, positions);
    }
}
