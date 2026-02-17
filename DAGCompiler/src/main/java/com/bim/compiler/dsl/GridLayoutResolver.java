package com.bim.compiler.dsl;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves grid-based room positions to actual coordinates.
 *
 * Grid convention:
 *   - Grid cell = 1m × 1m base unit
 *   - Room size overrides default cell size
 *   - Rooms are placed based on grid position
 *   - Origin (A1) = (0, 0)
 *
 * Layout algorithm:
 *   1. First pass: calculate each room's actual position based on grid
 *   2. Rooms at same grid cell are sized according to their spec
 *   3. Adjacent rooms share walls
 */
public class GridLayoutResolver {

    // Track accumulated positions per grid cell
    private final Map<Integer, Double> columnWidths = new HashMap<>();
    private final Map<Integer, Double> rowHeights = new HashMap<>();

    /**
     * Resolve storey definition to room polygons.
     */
    public static Map<String, RoomPolygon> resolve(StoreyDefinition storey) {
        GridLayoutResolver resolver = new GridLayoutResolver();
        return resolver.resolveInternal(storey);
    }

    private Map<String, RoomPolygon> resolveInternal(StoreyDefinition storey) {
        // First pass: determine column widths and row heights
        for (RoomDefinition room : storey.rooms()) {
            int col = room.minGridX();
            int row = room.minGridY();

            // For spanning rooms, distribute width/depth
            int colSpan = room.maxGridX() - room.minGridX() + 1;
            int rowSpan = room.maxGridY() - room.minGridY() + 1;

            double widthPerCol = room.width() / colSpan;
            double depthPerRow = room.depth() / rowSpan;

            // Update column widths (take max for each column)
            for (int c = room.minGridX(); c <= room.maxGridX(); c++) {
                columnWidths.merge(c, widthPerCol, Math::max);
            }

            // Update row heights (take max for each row)
            for (int r = room.minGridY(); r <= room.maxGridY(); r++) {
                rowHeights.merge(r, depthPerRow, Math::max);
            }
        }

        // Calculate column X positions
        Map<Integer, Double> columnX = new HashMap<>();
        double x = 0;
        for (int c = 0; c <= columnWidths.keySet().stream().mapToInt(i -> i).max().orElse(0); c++) {
            columnX.put(c, x);
            x += columnWidths.getOrDefault(c, 1.0);
        }
        double totalWidth = x;

        // Calculate row Y positions
        Map<Integer, Double> rowY = new HashMap<>();
        double y = 0;
        for (int r = 0; r <= rowHeights.keySet().stream().mapToInt(i -> i).max().orElse(0); r++) {
            rowY.put(r, y);
            y += rowHeights.getOrDefault(r, 1.0);
        }
        double totalDepth = y;

        // Second pass: create polygons
        Map<String, RoomPolygon> polygons = new HashMap<>();

        for (RoomDefinition room : storey.rooms()) {
            double minX = columnX.get(room.minGridX());
            double minY = rowY.get(room.minGridY());

            // Calculate max based on actual room size
            double maxX = minX + room.width();
            double maxY = minY + room.depth();

            // For spanning rooms, extend to cover all grid cells
            if (room.isSpanning()) {
                int endCol = room.maxGridX();
                int endRow = room.maxGridY();

                // Max X is start of next column after endCol
                maxX = columnX.get(endCol) + columnWidths.getOrDefault(endCol, 1.0);
                // Max Y is start of next row after endRow
                maxY = rowY.get(endRow) + rowHeights.getOrDefault(endRow, 1.0);
            }

            RoomPolygon polygon = new RoomPolygon(
                room.name(),
                room.type(),
                minX, maxX, minY, maxY
            );

            polygons.put(room.name(), polygon);
        }

        return polygons;
    }
}
