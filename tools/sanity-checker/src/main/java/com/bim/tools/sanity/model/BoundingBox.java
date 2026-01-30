package com.bim.tools.sanity.model;

/**
 * Axis-aligned bounding box for spatial checks.
 * Immutable value object.
 */
public record BoundingBox(
    double minX, double minY, double minZ,
    double maxX, double maxY, double maxZ
) {
    public double width() { return maxX - minX; }
    public double depth() { return maxY - minY; }
    public double height() { return maxZ - minZ; }

    public double centerX() { return (minX + maxX) / 2; }
    public double centerY() { return (minY + maxY) / 2; }
    public double centerZ() { return (minZ + maxZ) / 2; }

    /**
     * Check if this bbox intersects another in 3D.
     */
    public boolean intersects(BoundingBox other) {
        return minX <= other.maxX && maxX >= other.minX
            && minY <= other.maxY && maxY >= other.minY
            && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    /**
     * Check if this bbox intersects another in XY plane (2D).
     */
    public boolean intersectsXY(BoundingBox other) {
        return minX <= other.maxX && maxX >= other.minX
            && minY <= other.maxY && maxY >= other.minY;
    }

    /**
     * Check if this bbox contains another within tolerance.
     */
    public boolean contains(BoundingBox other, double tolerance) {
        return minX - tolerance <= other.minX
            && maxX + tolerance >= other.maxX
            && minY - tolerance <= other.minY
            && maxY + tolerance >= other.maxY
            && minZ - tolerance <= other.minZ
            && maxZ + tolerance >= other.maxZ;
    }

    /**
     * Check if this bbox contains another in XY plane within tolerance.
     */
    public boolean containsXY(BoundingBox other, double tolerance) {
        return minX - tolerance <= other.minX
            && maxX + tolerance >= other.maxX
            && minY - tolerance <= other.minY
            && maxY + tolerance >= other.maxY;
    }

    /**
     * Expand this bbox by the given amount in all directions.
     */
    public BoundingBox expand(double amount) {
        return new BoundingBox(
            minX - amount, minY - amount, minZ - amount,
            maxX + amount, maxY + amount, maxZ + amount
        );
    }

    /**
     * Get 2D area in XY plane.
     */
    public double areaXY() {
        return width() * depth();
    }

    /**
     * Calculate 2D intersection area in XY plane.
     */
    public double intersectionAreaXY(BoundingBox other) {
        double overlapX = Math.max(0, Math.min(maxX, other.maxX) - Math.max(minX, other.minX));
        double overlapY = Math.max(0, Math.min(maxY, other.maxY) - Math.max(minY, other.minY));
        return overlapX * overlapY;
    }

    /**
     * Check if a point is on the perimeter (edge) in XY plane.
     */
    public boolean isOnPerimeterXY(double x, double y, double tolerance) {
        boolean inX = x >= minX - tolerance && x <= maxX + tolerance;
        boolean inY = y >= minY - tolerance && y <= maxY + tolerance;
        boolean onXEdge = Math.abs(x - minX) <= tolerance || Math.abs(x - maxX) <= tolerance;
        boolean onYEdge = Math.abs(y - minY) <= tolerance || Math.abs(y - maxY) <= tolerance;
        return inX && inY && (onXEdge || onYEdge);
    }

    /**
     * Union of two bounding boxes.
     */
    public BoundingBox union(BoundingBox other) {
        return new BoundingBox(
            Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
            Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ)
        );
    }

    @Override
    public String toString() {
        return String.format("[(%.3f,%.3f,%.3f)-(%.3f,%.3f,%.3f)]",
            minX, minY, minZ, maxX, maxY, maxZ);
    }
}
