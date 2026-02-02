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

    // Structural constants - Watchdog-reviewed 2026-02-02

    /** Minimum opening width requiring lintel - ◆ RESEARCHED: JKR structural practice.
     *  Malaysian half-brick walls (100mm) cannot arch; lintels required for all openings > 600mm.
     *  UK 900mm rule assumes full-brick arching action — not applicable to Malaysian masonry. */
    private static final double MIN_OPENING_FOR_LINTEL = 0.6;  // 600mm per JKR

    /** Lintel depth 200mm - ◆ RESEARCHED: Malaysian RC practice per BS 8110 / EC2.
     *  Valid for spans ≤ 1.2m. Wider openings need parametric depth (span/5 minimum). */
    private static final double LINTEL_DEPTH = 0.2;

    /** Lintel bearing 150mm each side - ◆ RESEARCHED: BS 5628 clause 23.1.7 / EC6.
     *  Minimum bearing for lintels on masonry. */
    private static final double LINTEL_BEARING = 0.15;

    /** Column size 300mm - ○ ASSUMED: common Malaysian residential for ≤ 2 storeys.
     *  TODO: Move to profile config. Schools/8m spans need 400mm+. TERMINAL columns
     *  are 400-600mm+ — not applicable to residential. Needs structural calc per span. */
    private static final double COLUMN_SIZE = 0.3;

    // Phase 51: Maximum beam spans by construction type - ◆ RESEARCHED: Watchdog-verified
    /** Max beam span for RC masonry bearing system - ◆ RESEARCHED: MS 1195 / JKR standard details.
     *  Beams connect column-to-column; spans > 8m require steel or deeper sections. */
    public static final double MAX_BEAM_SPAN_MASONRY = 8.0;

    /** Max beam span for RC frame construction - ◆ RESEARCHED: Eurocode 2 / BS 8110. */
    public static final double MAX_BEAM_SPAN_FRAMED = 10.0;

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

    /**
     * Phase 51: Place two-way beam grid for large-span rooms.
     *
     * Generates beams between ADJACENT column positions only — never spanning past
     * an intermediate column. This produces a proper structural grid where every
     * beam connects column-to-column.
     *
     * Grid structure:
     * - X gridlines: room perimeter (minX, maxX) + interior column positions
     * - Y gridlines: room perimeter (minY, maxY) + interior column positions
     * - X-direction beams: placed at each Y gridline, spanning between adjacent X positions
     * - Y-direction beams: placed at each X gridline, spanning between adjacent Y positions
     *
     * @param roomBounds Room bounding box
     * @param beamMaxSpan Maximum span before intermediate columns (meters)
     * @param beamHeight Height above floor for beams (ceiling level)
     * @param roomName Room name for ID generation
     * @return List of beam instances for the structural grid
     */
    public List<BeamInstance> placeGridBeams(
            BoundingBox roomBounds,
            double beamMaxSpan,
            double beamHeight,
            String roomName) throws SQLException {

        List<BeamInstance> beams = new ArrayList<>();

        ComponentDefinition beamDef = library.getByName("Concrete-Rectangular Beam");

        double roomWidth = roomBounds.width();   // X dimension
        double roomDepth = roomBounds.depth();   // Y dimension
        double minX = roomBounds.minX();
        double maxX = roomBounds.maxX();
        double minY = roomBounds.minY();
        double maxY = roomBounds.maxY();

        // Phase 51: Calculate grid positions (perimeter + interior column lines)
        // X gridlines: perimeter + interior columns to break X spans
        List<Double> xGridlines = new ArrayList<>();
        xGridlines.add(minX);
        if (roomWidth > beamMaxSpan) {
            int numSpansX = (int) Math.ceil(roomWidth / beamMaxSpan);
            double spanX = roomWidth / numSpansX;
            for (int i = 1; i < numSpansX; i++) {
                xGridlines.add(minX + i * spanX);
            }
        }
        xGridlines.add(maxX);

        // Y gridlines: perimeter + interior columns to break Y spans
        List<Double> yGridlines = new ArrayList<>();
        yGridlines.add(minY);
        if (roomDepth > beamMaxSpan) {
            int numSpansY = (int) Math.ceil(roomDepth / beamMaxSpan);
            double spanY = roomDepth / numSpansY;
            for (int i = 1; i < numSpansY; i++) {
                yGridlines.add(minY + i * spanY);
            }
        }
        yGridlines.add(maxY);

        int beamIdx = 0;

        // X-direction beams (parallel to X axis) at each Y gridline
        // Each beam spans between adjacent X positions
        for (double gridY : yGridlines) {
            for (int i = 0; i < xGridlines.size() - 1; i++) {
                double startX = xGridlines.get(i);
                double endX = xGridlines.get(i + 1);
                double spanLength = endX - startX;
                double beamCenterX = (startX + endX) / 2;

                beams.add(new BeamInstance(
                    String.format("grid_beam_%s_y%.0f_x%d", roomName, gridY, i + 1),
                    beamDef,
                    new Point3D(beamCenterX, gridY, beamHeight),
                    spanLength,
                    LINTEL_DEPTH,                 // 200mm width
                    0.3,                          // 300mm beam depth
                    0,                            // rotation = 0 (parallel to X)
                    BeamType.FLOOR_BEAM
                ));
            }
        }

        // Y-direction beams (parallel to Y axis) at each X gridline
        // Each beam spans between adjacent Y positions
        for (double gridX : xGridlines) {
            for (int j = 0; j < yGridlines.size() - 1; j++) {
                double startY = yGridlines.get(j);
                double endY = yGridlines.get(j + 1);
                double spanLength = endY - startY;
                double beamCenterY = (startY + endY) / 2;

                beams.add(new BeamInstance(
                    String.format("grid_beam_%s_x%.0f_y%d", roomName, gridX, j + 1),
                    beamDef,
                    new Point3D(gridX, beamCenterY, beamHeight),
                    spanLength,
                    LINTEL_DEPTH,                 // 200mm width
                    0.3,                          // 300mm beam depth
                    Math.PI / 2,                  // rotation = 90 degrees (parallel to Y)
                    BeamType.FLOOR_BEAM
                ));
            }
        }

        if (!beams.isEmpty()) {
            double maxSpan = beams.stream().mapToDouble(BeamInstance::length).max().orElse(0);
            System.out.printf("[STRUCTURAL] Room %s: %d grid beams (%d X-dir, %d Y-dir), max span=%.1fm%n",
                roomName, beams.size(),
                yGridlines.size() * (xGridlines.size() - 1),
                xGridlines.size() * (yGridlines.size() - 1),
                maxSpan);
        }

        return beams;
    }

    /**
     * Phase 50B.1: Place grid columns at beam intersections for large-span rooms.
     */
    public List<ColumnInstance> placeGridColumns(
            BoundingBox roomBounds,
            double beamMaxSpan,
            double baseZ,
            double height,
            String roomName) throws SQLException {

        List<ColumnInstance> columns = new ArrayList<>();

        ComponentDefinition columnDef = library.getByName("Rectangular Column");
        if (columnDef == null) {
            return columns;
        }

        double roomWidth = roomBounds.width();
        double roomDepth = roomBounds.depth();
        double minX = roomBounds.minX();
        double minY = roomBounds.minY();

        // Calculate grid intersections
        int numSpansX = roomWidth > beamMaxSpan ? (int) Math.ceil(roomWidth / beamMaxSpan) : 1;
        int numSpansY = roomDepth > beamMaxSpan ? (int) Math.ceil(roomDepth / beamMaxSpan) : 1;

        double spanX = roomWidth / numSpansX;
        double spanY = roomDepth / numSpansY;

        int colIdx = 0;

        // Place columns at interior grid intersections (not at perimeter)
        for (int i = 1; i < numSpansX; i++) {
            for (int j = 1; j < numSpansY; j++) {
                double colX = minX + i * spanX;
                double colY = minY + j * spanY;

                columns.add(new ColumnInstance(
                    "grid_col_" + roomName + "_" + (++colIdx),
                    columnDef,
                    new Point3D(colX, colY, baseZ),
                    height,
                    COLUMN_SIZE,
                    COLUMN_SIZE,
                    ColumnType.INTERMEDIATE
                ));
            }
        }

        if (!columns.isEmpty()) {
            System.out.printf("[STRUCTURAL] Room %s: %d grid columns%n",
                roomName, columns.size());
        }

        return columns;
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
