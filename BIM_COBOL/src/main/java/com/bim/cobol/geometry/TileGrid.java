package com.bim.cobol.geometry;

import com.bim.compiler.geometry.Point3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry — generates tile positions on a rectangular grid.
 *
 * <p>All coordinates in meters. Each panel defines an origin, grid dimensions (nx × ny),
 * and step sizes. Positions are generated at origin + (i × stepX, j × stepY, 0).
 *
 * <p>19 TILE formulas replace 33,324 flat plate coordinates in the Terminal model.
 */
public final class TileGrid {

    private TileGrid() {}

    /** Single panel: origin + i×stepX, j×stepY grid. */
    public record Panel(String name, double originX, double originY, double originZ,
                        int nx, int ny, double stepX, double stepY) {
        public int count() { return nx * ny; }
    }

    /** Result carrying all generated positions grouped by panel. */
    public record TileResult(List<Panel> panels, List<Point3D> positions) {
        public int totalCount() { return positions.size(); }
    }

    /**
     * Generate grid positions for a single panel.
     *
     * @param originX  panel origin X (meters)
     * @param originY  panel origin Y (meters)
     * @param originZ  panel origin Z (meters)
     * @param nx       number of tiles along X (≥ 1)
     * @param ny       number of tiles along Y (≥ 1)
     * @param stepX    spacing between tiles along X (meters)
     * @param stepY    spacing between tiles along Y (meters)
     * @return list of tile positions in world coordinates
     */
    public static List<Point3D> generate(double originX, double originY, double originZ,
                                          int nx, int ny, double stepX, double stepY) {
        List<Point3D> positions = new ArrayList<>(nx * ny);
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                positions.add(new Point3D(
                        originX + i * stepX,
                        originY + j * stepY,
                        originZ));
            }
        }
        return positions;
    }

    /**
     * Generate grid positions for multiple panels (additive).
     *
     * @param panels list of panel definitions
     * @return TileResult with per-panel metadata and all positions concatenated
     */
    public static TileResult generateMulti(List<Panel> panels) {
        List<Point3D> all = new ArrayList<>();
        for (Panel p : panels) {
            all.addAll(generate(p.originX(), p.originY(), p.originZ(),
                    p.nx(), p.ny(), p.stepX(), p.stepY()));
        }
        return new TileResult(panels, all);
    }
}
