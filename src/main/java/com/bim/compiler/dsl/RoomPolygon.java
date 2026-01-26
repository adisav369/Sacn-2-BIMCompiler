package com.bim.compiler.dsl;

import com.bim.compiler.geometry.Point3D;
import java.util.List;

/**
 * A room's 2D footprint polygon (at Z=0).
 * Vertices in counter-clockwise order.
 *
 * For crude DSL: always rectangular.
 */
public record RoomPolygon(
    String roomName,
    RoomType roomType,
    double minX,
    double maxX,
    double minY,
    double maxY
) {
    /**
     * Get width (X dimension).
     */
    public double width() {
        return maxX - minX;
    }

    /**
     * Get depth (Y dimension).
     */
    public double depth() {
        return maxY - minY;
    }

    /**
     * Get center point.
     */
    public Point3D center() {
        return new Point3D((minX + maxX) / 2, (minY + maxY) / 2, 0);
    }

    /**
     * Get corner points (counter-clockwise from SW).
     */
    public List<Point3D> corners() {
        return List.of(
            new Point3D(minX, minY, 0),  // SW
            new Point3D(maxX, minY, 0),  // SE
            new Point3D(maxX, maxY, 0),  // NE
            new Point3D(minX, maxY, 0)   // NW
        );
    }

    /**
     * Check if this polygon shares an edge with another.
     */
    public boolean sharesEdge(RoomPolygon other) {
        // Check if they share a vertical edge (same X, overlapping Y)
        if (Math.abs(this.maxX - other.minX) < 0.001 ||
            Math.abs(this.minX - other.maxX) < 0.001) {
            return this.minY < other.maxY && this.maxY > other.minY;
        }

        // Check if they share a horizontal edge (same Y, overlapping X)
        if (Math.abs(this.maxY - other.minY) < 0.001 ||
            Math.abs(this.minY - other.maxY) < 0.001) {
            return this.minX < other.maxX && this.maxX > other.minX;
        }

        return false;
    }

    /**
     * Get the direction from this room to another (if adjacent).
     */
    public Direction directionTo(RoomPolygon other) {
        if (Math.abs(this.maxX - other.minX) < 0.001) return Direction.EAST;
        if (Math.abs(this.minX - other.maxX) < 0.001) return Direction.WEST;
        if (Math.abs(this.maxY - other.minY) < 0.001) return Direction.NORTH;
        if (Math.abs(this.minY - other.maxY) < 0.001) return Direction.SOUTH;
        return null;
    }

    /**
     * Get midpoint of wall in given direction.
     */
    public Point3D wallMidpoint(Direction dir) {
        return switch (dir) {
            case NORTH -> new Point3D((minX + maxX) / 2, maxY, 0);
            case SOUTH -> new Point3D((minX + maxX) / 2, minY, 0);
            case EAST -> new Point3D(maxX, (minY + maxY) / 2, 0);
            case WEST -> new Point3D(minX, (minY + maxY) / 2, 0);
        };
    }

    /**
     * Get wall length in given direction.
     */
    public double wallLength(Direction dir) {
        return switch (dir) {
            case NORTH, SOUTH -> width();
            case EAST, WEST -> depth();
        };
    }
}
