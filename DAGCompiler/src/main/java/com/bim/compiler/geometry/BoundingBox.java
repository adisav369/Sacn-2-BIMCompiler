package com.bim.compiler.geometry;

import com.bim.compiler.topology.BIMConstants;

/**
 * Axis-aligned bounding box in world coordinates.
 * All values in METERS (from CODE PATTERN 1).
 * Maps directly to elements_rtree table in database.
 *
 * @param minX minimum X coordinate
 * @param maxX maximum X coordinate
 * @param minY minimum Y coordinate
 * @param maxY maximum Y coordinate
 * @param minZ minimum Z coordinate
 * @param maxZ maximum Z coordinate
 */
public record BoundingBox(
    double minX, double maxX,
    double minY, double maxY,
    double minZ, double maxZ
) {

    /**
     * Width (X dimension) in meters.
     */
    public double width() {
        return maxX - minX;
    }

    /**
     * Depth (Y dimension) in meters.
     */
    public double depth() {
        return maxY - minY;
    }

    /**
     * Height (Z dimension) in meters.
     */
    public double height() {
        return maxZ - minZ;
    }

    /**
     * Center point of the bounding box.
     * From FACT 6: Transform = BBox center (no offset).
     */
    public Point3D center() {
        return new Point3D(
            (minX + maxX) / 2.0,
            (minY + maxY) / 2.0,
            (minZ + maxZ) / 2.0
        );
    }

    /**
     * Check if this bbox overlaps another.
     * Used for spatial relationship queries (FACT 9, RELATIONSHIP PATTERNS).
     * @param other the other bounding box
     * @return true if bboxes overlap
     */
    public boolean overlaps(BoundingBox other) {
        return minX <= other.maxX && maxX >= other.minX
            && minY <= other.maxY && maxY >= other.minY
            && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    /**
     * Check if this bbox contains another completely.
     * Used for opening-in-wall queries.
     * @param other the other bounding box
     * @return true if other is fully contained
     */
    public boolean contains(BoundingBox other) {
        return minX <= other.minX && maxX >= other.maxX
            && minY <= other.minY && maxY >= other.maxY
            && minZ <= other.minZ && maxZ >= other.maxZ;
    }

    /**
     * Calculate vertical gap to another bbox above this one.
     * Used for MEP-structure clearance checks (RULE 1).
     * @param above the bbox above this one
     * @return vertical gap in meters (negative if overlapping)
     */
    public double verticalGapTo(BoundingBox above) {
        return above.minZ - this.maxZ;
    }

    /**
     * Check if element spans multiple stories.
     * From FACT 5: Elements with Z-span > 8m span floors.
     * @return true if height exceeds multi-story threshold
     */
    public boolean isMultiStory() {
        return height() > BIMConstants.MULTI_STORY_THRESHOLD;
    }

    /**
     * Get smallest dimension (for wall thickness, pipe diameter).
     * Used by WallThickness.fromMeasurement().
     */
    public double minDimension() {
        return Math.min(width(), Math.min(depth(), height()));
    }

    /**
     * Expand bbox by a margin (for clearance checks).
     * @param margin expansion in meters
     * @return expanded bounding box
     */
    public BoundingBox expand(double margin) {
        return new BoundingBox(
            minX - margin, maxX + margin,
            minY - margin, maxY + margin,
            minZ - margin, maxZ + margin
        );
    }

    @Override
    public String toString() {
        return String.format("BBox[(%.2f,%.2f,%.2f)-(%.2f,%.2f,%.2f)]",
            minX, minY, minZ, maxX, maxY, maxZ);
    }
}
