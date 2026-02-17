package com.bim.compiler.geometry;

/**
 * Immutable 3D vector for direction representation.
 * Used for facing directions inferred from geometry.
 *
 * @param x X component
 * @param y Y component
 * @param z Z component
 */
public record Vector3D(double x, double y, double z) {

    public static final Vector3D X_AXIS = new Vector3D(1, 0, 0);
    public static final Vector3D Y_AXIS = new Vector3D(0, 1, 0);
    public static final Vector3D Z_AXIS = new Vector3D(0, 0, 1);
    public static final Vector3D NEG_X = new Vector3D(-1, 0, 0);
    public static final Vector3D NEG_Y = new Vector3D(0, -1, 0);

    /**
     * Vector length/magnitude.
     */
    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * Return normalized (unit length) vector.
     */
    public Vector3D normalize() {
        double len = length();
        if (len < 1e-10) return Z_AXIS; // Degenerate case
        return new Vector3D(x / len, y / len, z / len);
    }

    /**
     * Dot product with another vector.
     */
    public double dot(Vector3D other) {
        return x * other.x + y * other.y + z * other.z;
    }

    /**
     * Cross product with another vector.
     */
    public Vector3D cross(Vector3D other) {
        return new Vector3D(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        );
    }

    /**
     * Angle to another vector in radians.
     */
    public double angleTo(Vector3D other) {
        double d = this.normalize().dot(other.normalize());
        d = Math.max(-1, Math.min(1, d)); // Clamp for acos
        return Math.acos(d);
    }

    /**
     * Check if vector is primarily horizontal (XY plane).
     */
    public boolean isHorizontal() {
        return Math.abs(z) < 0.1 * length();
    }

    /**
     * Check if vector is primarily vertical (Z axis).
     */
    public boolean isVertical() {
        double horizontal = Math.sqrt(x * x + y * y);
        return horizontal < 0.1 * Math.abs(z);
    }

    @Override
    public String toString() {
        return String.format("<%+.3f, %+.3f, %+.3f>", x, y, z);
    }
}
