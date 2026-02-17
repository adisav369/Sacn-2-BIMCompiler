package com.bim.compiler.model;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Vector3D;

/**
 * Element with orientation inferred from geometry.
 * From PATTERN G2: No rotation data stored, must infer from bbox shape.
 *
 * Inference rules:
 * - Wall/Door/Window: facing = perpendicular to thin dimension
 * - Pipe/Duct: direction = along longest dimension
 * - 82% of doors align thin axis with host wall
 */
public interface IOrientedElement extends ISpatialElement {

    /**
     * Get the inferred facing direction (for walls, doors, windows).
     * Perpendicular to the thin horizontal dimension.
     * From PATTERN G2: Door thin axis matches wall thin axis (82% aligned).
     *
     * @return facing direction as unit vector, or null if not applicable
     */
    default Vector3D getFacingDirection() {
        BoundingBox bbox = getBoundingBox();
        double w = bbox.width();
        double d = bbox.depth();

        // Facing is perpendicular to thin dimension
        if (w < d) {
            // Thin in X, facing along X axis
            return Vector3D.X_AXIS;
        } else if (d < w) {
            // Thin in Y, facing along Y axis
            return Vector3D.Y_AXIS;
        } else {
            // Square - no preferred direction
            return null;
        }
    }

    /**
     * Get the inferred linear direction (for pipes, ducts, beams).
     * Along the longest dimension.
     *
     * @return direction as unit vector
     */
    default Vector3D getLinearDirection() {
        BoundingBox bbox = getBoundingBox();
        double w = bbox.width();
        double d = bbox.depth();
        double h = bbox.height();

        double max = Math.max(w, Math.max(d, h));

        if (max == w) return Vector3D.X_AXIS;
        if (max == d) return Vector3D.Y_AXIS;
        return Vector3D.Z_AXIS;
    }

    /**
     * Get the rotation angle from the X-axis in the XY plane.
     * Useful for placing rotated elements.
     *
     * @return angle in radians (0 to 2π), or 0 if facing direction is null
     */
    default double getRotationAngle() {
        Vector3D facing = getFacingDirection();
        if (facing == null) return 0;

        return Math.atan2(facing.y(), facing.x());
    }

    /**
     * Check if this element is aligned with another (same facing direction).
     * From PATTERN G2: Used for door-wall alignment checks.
     *
     * @param other the other element
     * @return true if facing directions are parallel
     */
    default boolean isAlignedWith(IOrientedElement other) {
        Vector3D thisFacing = getFacingDirection();
        Vector3D otherFacing = other.getFacingDirection();

        if (thisFacing == null || otherFacing == null) {
            return false;
        }

        double dot = Math.abs(thisFacing.dot(otherFacing));
        return dot > 0.9; // Within ~25 degrees
    }

    /**
     * Check if this element is perpendicular to another.
     *
     * @param other the other element
     * @return true if facing directions are perpendicular
     */
    default boolean isPerpendicularTo(IOrientedElement other) {
        Vector3D thisFacing = getFacingDirection();
        Vector3D otherFacing = other.getFacingDirection();

        if (thisFacing == null || otherFacing == null) {
            return false;
        }

        double dot = Math.abs(thisFacing.dot(otherFacing));
        return dot < 0.1; // Within ~84 degrees of perpendicular
    }
}
