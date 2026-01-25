package com.bim.compiler.geometry;

import com.bim.compiler.topology.BIMConstants;

/**
 * Immutable 3D point in world coordinates.
 * All values in METERS (from CODE PATTERN 1).
 *
 * @param x X coordinate in meters (offset-relative)
 * @param y Y coordinate in meters (offset-relative)
 * @param z Z coordinate in meters (offset-relative)
 */
public record Point3D(double x, double y, double z) {

    /**
     * Calculate distance to another point.
     * @param other the other point
     * @return distance in meters
     */
    public double distanceTo(Point3D other) {
        double dx = other.x - this.x;
        double dy = other.y - this.y;
        double dz = other.z - this.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Calculate horizontal (XY plane) distance to another point.
     * Useful for sprinkler spacing checks.
     * @param other the other point
     * @return horizontal distance in meters
     */
    public double horizontalDistanceTo(Point3D other) {
        double dx = other.x - this.x;
        double dy = other.y - this.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Check if this point is within tolerance of another.
     * Uses TOLERANCE from BIMConstants (5mm).
     * @param other the other point
     * @return true if within tolerance
     */
    public boolean isNear(Point3D other) {
        return distanceTo(other) <= BIMConstants.TOLERANCE;
    }

    /**
     * Convert from offset-relative to IFC absolute coordinates.
     * From FACT 2: add global offset.
     * @return point in IFC absolute coordinates
     */
    public Point3D toAbsolute() {
        return new Point3D(
            x + BIMConstants.OFFSET_X,
            y + BIMConstants.OFFSET_Y,
            z + BIMConstants.OFFSET_Z
        );
    }

    /**
     * Convert from IFC absolute to offset-relative coordinates.
     * From FACT 2: subtract global offset.
     * @return point in offset-relative coordinates
     */
    public Point3D toRelative() {
        return new Point3D(
            x - BIMConstants.OFFSET_X,
            y - BIMConstants.OFFSET_Y,
            z - BIMConstants.OFFSET_Z
        );
    }

    @Override
    public String toString() {
        return String.format("(%.3f, %.3f, %.3f)", x, y, z);
    }
}
