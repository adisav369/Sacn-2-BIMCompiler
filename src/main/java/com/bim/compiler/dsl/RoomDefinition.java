package com.bim.compiler.dsl;

import java.util.ArrayList;
import java.util.List;

/**
 * Room definition from DSL.
 *
 * Example:
 *   BEDROOM "master" at:A1 size:5x3m {
 *       DOOR south to:corridor
 *       WINDOW north
 *   }
 *
 * Grid convention:
 *   - Columns: A, B, C, ... (X axis)
 *   - Rows: 1, 2, 3, ... (Y axis)
 *   - A1 = (0,0), B2 = (1,1), etc.
 */
public record RoomDefinition(
    RoomType type,
    String name,
    GridCell gridStart,
    GridCell gridEnd,      // null if single cell
    double width,          // meters
    double depth,          // meters
    List<DoorDefinition> doors,
    List<WindowDefinition> windows
) {
    /**
     * Grid cell reference (e.g., A1, B2)
     */
    public record GridCell(int col, int row) {
        public static GridCell parse(String cell) {
            // A1 -> col=0, row=0
            // B2 -> col=1, row=1
            cell = cell.toUpperCase().trim();
            char colChar = cell.charAt(0);
            int col = colChar - 'A';
            int row = Integer.parseInt(cell.substring(1)) - 1;
            return new GridCell(col, row);
        }

        @Override
        public String toString() {
            return "" + (char)('A' + col) + (row + 1);
        }
    }

    /**
     * Check if this is a spanning room (e.g., corridor A2-B2)
     */
    public boolean isSpanning() {
        return gridEnd != null;
    }

    /**
     * Get the minimum X coordinate (grid units)
     */
    public int minGridX() {
        if (gridEnd == null) return gridStart.col();
        return Math.min(gridStart.col(), gridEnd.col());
    }

    /**
     * Get the maximum X coordinate (grid units)
     */
    public int maxGridX() {
        if (gridEnd == null) return gridStart.col();
        return Math.max(gridStart.col(), gridEnd.col());
    }

    /**
     * Get the minimum Y coordinate (grid units)
     */
    public int minGridY() {
        if (gridEnd == null) return gridStart.row();
        return Math.min(gridStart.row(), gridEnd.row());
    }

    /**
     * Get the maximum Y coordinate (grid units)
     */
    public int maxGridY() {
        if (gridEnd == null) return gridStart.row();
        return Math.max(gridStart.row(), gridEnd.row());
    }

    /**
     * Builder for easier construction during parsing
     */
    public static class Builder {
        private RoomType type;
        private String name;
        private GridCell gridStart;
        private GridCell gridEnd;
        private double width;
        private double depth;
        private final List<DoorDefinition> doors = new ArrayList<>();
        private final List<WindowDefinition> windows = new ArrayList<>();

        public Builder type(RoomType type) { this.type = type; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder gridStart(GridCell cell) { this.gridStart = cell; return this; }
        public Builder gridEnd(GridCell cell) { this.gridEnd = cell; return this; }
        public Builder width(double width) { this.width = width; return this; }
        public Builder depth(double depth) { this.depth = depth; return this; }
        public Builder addDoor(DoorDefinition door) { doors.add(door); return this; }
        public Builder addWindow(WindowDefinition window) { windows.add(window); return this; }

        public RoomDefinition build() {
            return new RoomDefinition(type, name, gridStart, gridEnd, width, depth, doors, windows);
        }
    }
}
