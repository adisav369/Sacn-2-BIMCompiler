package com.bim.compiler.drawing2d.model;

/**
 * A structural grid line derived from wall alignment.
 *
 * axis: "x" (vertical grid line at X position) or "y" (horizontal grid line at Y position).
 * position: coordinate in metres along the axis.
 * label: grid label string ("A", "B", "1", "2", etc.).
 */
public record GridLine(String label, String axis, double position) {

    /** Grid label sort: numeric labels before alphabetic, then by value. */
    public int sortKey() {
        try {
            return Integer.parseInt(label);
        } catch (NumberFormatException e) {
            return 1000 + label.charAt(0);
        }
    }
}
