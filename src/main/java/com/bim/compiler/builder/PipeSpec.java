package com.bim.compiler.builder;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.Discipline;
import com.bim.compiler.topology.PipeDiameterRange;

/**
 * Specification for generating a pipe segment.
 *
 * From FACT 4: Pipe diameters vary by discipline:
 *   - CW: 4-155mm
 *   - FP: 3-184mm
 *   - LPG: 3-383mm
 *   - SP: 3-6696mm
 *
 * From PATTERN G4: Pipe-to-fitting insertion depth:
 *   - CW: 17mm, FP: 14mm, LPG: 18mm, SP: 42mm
 *
 * From PATTERN G5: Pipes extrude along longest bbox dimension
 * From PATTERN G10: FP has most fittings (1.18/pipe), SP fewest (0.82/pipe)
 *
 * @param start start point (center of pipe)
 * @param end end point (center of pipe)
 * @param diameterMeters pipe diameter in meters
 * @param discipline pipe discipline (CW, FP, LPG, or SP)
 * @param startTermination how the pipe starts (OPEN, CAPPED, FITTING)
 * @param endTermination how the pipe ends (OPEN, CAPPED, FITTING)
 */
public record PipeSpec(
    Point3D start,
    Point3D end,
    double diameterMeters,
    Discipline discipline,
    TerminationType startTermination,
    TerminationType endTermination
) {

    /**
     * Termination types for pipe ends.
     */
    public enum TerminationType {
        /** Open end (no termination) */
        OPEN,
        /** Capped/closed end */
        CAPPED,
        /** Connected to fitting */
        FITTING
    }

    /**
     * Get pipe length.
     */
    public double length() {
        return start.distanceTo(end);
    }

    /**
     * Get diameter in millimeters.
     */
    public double diameterMm() {
        return diameterMeters * 1000;
    }

    /**
     * Get unit direction vector along pipe.
     */
    public Point3D direction() {
        double len = length();
        if (len < 0.001) return new Point3D(1, 0, 0);
        return new Point3D(
            (end.x() - start.x()) / len,
            (end.y() - start.y()) / len,
            (end.z() - start.z()) / len
        );
    }

    /**
     * Check if diameter is valid for this discipline.
     * Uses PipeDiameterRange from FACT 4.
     */
    public boolean isValidDiameter() {
        return PipeDiameterRange.isValid(discipline, diameterMeters);
    }

    /**
     * Get the bounding box for this pipe.
     * Pipe is a cylinder aligned along start→end.
     */
    public BoundingBox toBoundingBox() {
        double radius = diameterMeters / 2;

        double minX = Math.min(start.x(), end.x()) - radius;
        double maxX = Math.max(start.x(), end.x()) + radius;
        double minY = Math.min(start.y(), end.y()) - radius;
        double maxY = Math.max(start.y(), end.y()) + radius;
        double minZ = Math.min(start.z(), end.z()) - radius;
        double maxZ = Math.max(start.z(), end.z()) + radius;

        return new BoundingBox(minX, maxX, minY, maxY, minZ, maxZ);
    }

    /**
     * Extract pipe spec from an existing pipe element.
     *
     * @param pipe the existing pipe element
     * @return the pipe specification
     */
    public static PipeSpec extractFrom(ISpatialElement pipe) {
        if (pipe.getType() != BIMObjectType.IFC_PIPE_SEGMENT) {
            throw new IllegalArgumentException("Element is not a pipe: " + pipe.getType());
        }

        BoundingBox bbox = pipe.getBoundingBox();
        Discipline discipline = pipe.getDiscipline();

        // Determine pipe orientation from bbox dimensions
        double w = bbox.width();
        double d = bbox.depth();
        double h = bbox.height();

        Point3D start, end;
        double diameter;

        // Longest dimension is the pipe direction
        if (w >= d && w >= h) {
            // X-aligned pipe
            diameter = Math.min(d, h);
            double centerY = (bbox.minY() + bbox.maxY()) / 2;
            double centerZ = (bbox.minZ() + bbox.maxZ()) / 2;
            start = new Point3D(bbox.minX(), centerY, centerZ);
            end = new Point3D(bbox.maxX(), centerY, centerZ);
        } else if (d >= w && d >= h) {
            // Y-aligned pipe
            diameter = Math.min(w, h);
            double centerX = (bbox.minX() + bbox.maxX()) / 2;
            double centerZ = (bbox.minZ() + bbox.maxZ()) / 2;
            start = new Point3D(centerX, bbox.minY(), centerZ);
            end = new Point3D(centerX, bbox.maxY(), centerZ);
        } else {
            // Z-aligned pipe (vertical)
            diameter = Math.min(w, d);
            double centerX = (bbox.minX() + bbox.maxX()) / 2;
            double centerY = (bbox.minY() + bbox.maxY()) / 2;
            start = new Point3D(centerX, centerY, bbox.minZ());
            end = new Point3D(centerX, centerY, bbox.maxZ());
        }

        // Default terminations - could be enhanced with fitting detection
        return new PipeSpec(start, end, diameter, discipline,
            TerminationType.OPEN, TerminationType.OPEN);
    }
}
