package com.bim.compiler.library;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.library.ComponentLibrary.*;

import java.sql.SQLException;
import java.util.*;

/**
 * Phase 23: Places structural elements (columns, beams/lintels) from library.
 *
 * Placement rules:
 * - Columns at building corners (4 minimum)
 * - Columns at wall T-junctions (where interior walls meet exterior)
 * - Lintels over openings > 900mm (doors, windows)
 *
 * From library:
 * - M_Rectangular Column: 0.45 x 0.80 x 4.00m (scalable height)
 * - M_Concrete-Rectangular Beam: 5.75 x 0.55 x 0.75m (scalable length)
 */
public class StructuralPlacer {

    private final ComponentLibrary library;

    // Structural constants
    private static final double MIN_OPENING_FOR_LINTEL = 0.9;  // 900mm
    private static final double LINTEL_DEPTH = 0.2;            // 200mm standard
    private static final double LINTEL_BEARING = 0.15;         // 150mm bearing each side
    private static final double COLUMN_SIZE = 0.3;             // 300mm square for residential

    public StructuralPlacer(ComponentLibrary library) {
        this.library = library;
    }

    /**
     * Place columns for a building storey.
     *
     * @param corners List of corner points (building perimeter corners)
     * @param tJunctions List of T-junction points (interior wall meets exterior)
     * @param baseZ Floor elevation
     * @param height Storey height
     * @return List of column instances
     */
    public List<ColumnInstance> placeColumns(
            List<Point3D> corners,
            List<Point3D> tJunctions,
            double baseZ,
            double height) throws SQLException {

        List<ColumnInstance> columns = new ArrayList<>();

        ComponentDefinition columnDef = library.getByName("Rectangular Column");

        if (columnDef == null) {
            System.out.println("[STRUCTURAL] No column definition found in library");
            return columns;
        }

        // Place columns at corners
        int idx = 0;
        for (Point3D corner : corners) {
            columns.add(new ColumnInstance(
                "corner_col_" + (++idx),
                columnDef,
                new Point3D(corner.x(), corner.y(), baseZ),
                height,
                COLUMN_SIZE,
                COLUMN_SIZE,
                ColumnType.CORNER
            ));
        }

        // Place columns at T-junctions
        for (Point3D junction : tJunctions) {
            columns.add(new ColumnInstance(
                "tjunc_col_" + (++idx),
                columnDef,
                new Point3D(junction.x(), junction.y(), baseZ),
                height,
                COLUMN_SIZE,
                COLUMN_SIZE,
                ColumnType.T_JUNCTION
            ));
        }

        return columns;
    }

    /**
     * Find corner points of the building from room bounds.
     */
    public List<Point3D> findCorners(double minX, double minY, double maxX, double maxY, double z) {
        return List.of(
            new Point3D(minX, minY, z),  // SW
            new Point3D(maxX, minY, z),  // SE
            new Point3D(maxX, maxY, z),  // NE
            new Point3D(minX, maxY, z)   // NW
        );
    }

    /**
     * Find T-junction points where interior walls meet exterior walls.
     */
    public List<Point3D> findTJunctions(
            List<WallInfo> interiorWalls,
            double bldgMinX, double bldgMinY,
            double bldgMaxX, double bldgMaxY,
            double z) {

        List<Point3D> junctions = new ArrayList<>();
        double tolerance = 0.01;

        for (WallInfo wall : interiorWalls) {
            // Check if wall endpoints touch building perimeter
            if (Math.abs(wall.x1() - bldgMinX) < tolerance ||
                Math.abs(wall.x1() - bldgMaxX) < tolerance) {
                junctions.add(new Point3D(wall.x1(), wall.y1(), z));
            }
            if (Math.abs(wall.x2() - bldgMinX) < tolerance ||
                Math.abs(wall.x2() - bldgMaxX) < tolerance) {
                junctions.add(new Point3D(wall.x2(), wall.y2(), z));
            }
            if (Math.abs(wall.y1() - bldgMinY) < tolerance ||
                Math.abs(wall.y1() - bldgMaxY) < tolerance) {
                junctions.add(new Point3D(wall.x1(), wall.y1(), z));
            }
            if (Math.abs(wall.y2() - bldgMinY) < tolerance ||
                Math.abs(wall.y2() - bldgMaxY) < tolerance) {
                junctions.add(new Point3D(wall.x2(), wall.y2(), z));
            }
        }

        // Remove duplicates
        return junctions.stream()
            .distinct()
            .filter(p -> !isCorner(p, bldgMinX, bldgMinY, bldgMaxX, bldgMaxY))
            .toList();
    }

    private boolean isCorner(Point3D p, double minX, double minY, double maxX, double maxY) {
        double tolerance = 0.01;
        boolean atMinX = Math.abs(p.x() - minX) < tolerance;
        boolean atMaxX = Math.abs(p.x() - maxX) < tolerance;
        boolean atMinY = Math.abs(p.y() - minY) < tolerance;
        boolean atMaxY = Math.abs(p.y() - maxY) < tolerance;
        return (atMinX || atMaxX) && (atMinY || atMaxY);
    }

    /**
     * Place lintels over openings (doors, windows).
     *
     * @param openings List of openings with position and size
     * @param baseZ Floor elevation
     * @return List of lintel/beam instances
     */
    public List<BeamInstance> placeLintels(List<OpeningInfo> openings, double baseZ) throws SQLException {
        List<BeamInstance> beams = new ArrayList<>();

        ComponentDefinition beamDef = library.getByName("Concrete-Rectangular Beam");

        if (beamDef == null) {
            System.out.println("[STRUCTURAL] No beam definition found in library");
            return beams;
        }

        int idx = 0;
        for (OpeningInfo opening : openings) {
            // Only place lintel for openings > 900mm
            if (opening.width() < MIN_OPENING_FOR_LINTEL) {
                continue;
            }

            // Lintel spans opening plus bearing on each side
            double lintelLength = opening.width() + 2 * LINTEL_BEARING;
            double lintelZ = baseZ + opening.height(); // Top of opening

            // Center lintel over opening
            double lintelX, lintelY;
            double rotation = 0;

            if (opening.wall().equals("north") || opening.wall().equals("south")) {
                // Horizontal opening - lintel runs east-west
                lintelX = opening.x() + opening.width() / 2;
                lintelY = opening.y();
                rotation = 0;
            } else {
                // Vertical opening - lintel runs north-south
                lintelX = opening.x();
                lintelY = opening.y() + opening.width() / 2;
                rotation = Math.PI / 2;
            }

            beams.add(new BeamInstance(
                "lintel_" + (++idx),
                beamDef,
                new Point3D(lintelX, lintelY, lintelZ),
                lintelLength,
                LINTEL_DEPTH,
                0.1,  // 100mm height
                rotation,
                BeamType.LINTEL
            ));
        }

        return beams;
    }

    // =========================================================================
    // Data types
    // =========================================================================

    public record WallInfo(double x1, double y1, double x2, double y2, boolean isInterior) {}

    public record OpeningInfo(
        String name,
        String wall,       // north, south, east, west
        double x, double y,
        double width,
        double height
    ) {}

    public enum ColumnType {
        CORNER,
        T_JUNCTION,
        INTERMEDIATE
    }

    public enum BeamType {
        LINTEL,
        FLOOR_BEAM,
        TIE_BEAM
    }

    public record ColumnInstance(
        String id,
        ComponentDefinition definition,
        Point3D basePosition,
        double height,
        double width,
        double depth,
        ColumnType type
    ) {
        public String geometryHash() {
            return definition != null ? definition.geometryHash() : null;
        }
    }

    public record BeamInstance(
        String id,
        ComponentDefinition definition,
        Point3D position,
        double length,
        double width,
        double height,
        double rotation,
        BeamType type
    ) {
        public String geometryHash() {
            return definition != null ? definition.geometryHash() : null;
        }
    }
}
