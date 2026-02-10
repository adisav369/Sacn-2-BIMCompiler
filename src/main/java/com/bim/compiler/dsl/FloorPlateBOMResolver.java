package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Phase 95A: Data-driven floor plate resolver using ad_bom / ad_bom_child / ad_bom_child_param.
 *
 * Loads a floor plate BOM tree (e.g. TYPICAL_CONDO_FLOOR) and resolves spatial rules
 * to produce zone bounds on a discrete cell grid. Rules:
 *   band    — explicit x/y band indices
 *   at      — position within parent (north/south)
 *   fill_center — remaining cells after at-rules
 *   side    — adjacent bays on one side of core
 *   fill_remaining — remaining bays after side/core, split into units
 *   carve   — steal a cell from another zone
 *
 * Pattern follows FurnitureBOMResolver: load BOM tree, walk children, resolve positions.
 */
public class FloorPlateBOMResolver {

    private static final String LIB_PATH = "library/component_library.db";

    // --- Records ---

    /** Grid definition: axis labels and cumulative positions. */
    public record GridInfo(List<String> xAxes, List<Double> xPositions,
                           List<String> yAxes, List<Double> yPositions) {
        public int xBands() { return xAxes.size() - 1; }
        public int yBands() { return yAxes.size() - 1; }
        public double getX(int idx) { return xPositions.get(idx); }
        public double getY(int idx) { return yPositions.get(idx); }
        public int xIndex(String axis) { return xAxes.indexOf(axis); }
        public int yIndex(String axis) {
            // Try exact match first, then numeric
            int idx = yAxes.indexOf(axis);
            if (idx >= 0) return idx;
            // Handle "1" matching axis label "1"
            return -1;
        }
    }

    /** BOM tree node. */
    record FloorBOMNode(String bomId, List<FloorBOMChild> children) {}

    /** BOM child with spatial params. */
    record FloorBOMChild(int id, String role, String childBomId,
                         Map<String, String> params) {
        String param(String key) { return params.getOrDefault(key, null); }
        String param(String key, String def) { return params.getOrDefault(key, def); }
        int paramInt(String key, int def) {
            String v = params.get(key);
            if (v == null) return def;
            try { return Integer.parseInt(v); } catch (NumberFormatException e) { return def; }
        }
    }

    /** Resolved zone bounds — output of the resolver. */
    public record ZoneBounds(String role, String spaceType, String gridLabel,
                             int xStart, int xEnd, int yStart, int yEnd,
                             double minX, double minY, double maxX, double maxY,
                             String roomName) {
        /** Backward-compatible constructor without roomName. */
        public ZoneBounds(String role, String spaceType, String gridLabel,
                          int xStart, int xEnd, int yStart, int yEnd,
                          double minX, double minY, double maxX, double maxY) {
            this(role, spaceType, gridLabel, xStart, xEnd, yStart, yEnd,
                 minX, minY, maxX, maxY, null);
        }
        @Override
        public String toString() {
            return String.format("%-16s %-18s grid=%-6s cells=[%d,%d]-[%d,%d]  (%.1f,%.1f)-(%.1f,%.1f)  room=%s",
                role, spaceType, gridLabel, xStart, yStart, xEnd, yEnd, minX, minY, maxX, maxY,
                roomName != null ? roomName : "-");
        }
    }

    // --- BOM Tree ---

    private final Map<String, FloorBOMNode> bomTree = new HashMap<>();

    public FloorPlateBOMResolver() {
        loadBOMTree();
    }

    private void loadBOMTree() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + LIB_PATH)) {
            // Load all floor-plate BOM children
            String sql = """
                SELECT bc.bom_child_id, bc.bom_id, bc.role, bc.child_bom_id, bc.sequence
                FROM ad_bom_child bc
                JOIN ad_bom b ON bc.bom_id = b.bom_id
                WHERE bc.bom_id IN ('TYPICAL_CONDO_FLOOR', 'CORE_ASSEMBLY')
                  AND bc.is_active = 1
                ORDER BY bc.bom_id, bc.sequence
                """;

            List<int[]> childIds = new ArrayList<>(); // [childId]
            Map<Integer, String[]> rawChildren = new LinkedHashMap<>(); // id -> [bomId, role, childBomId]

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    int childId = rs.getInt("bom_child_id");
                    rawChildren.put(childId, new String[]{
                        rs.getString("bom_id"),
                        rs.getString("role"),
                        rs.getString("child_bom_id")
                    });
                    bomTree.computeIfAbsent(rs.getString("bom_id"),
                        k -> new FloorBOMNode(k, new ArrayList<>()));
                }
            }

            // Load params for each child
            for (var entry : rawChildren.entrySet()) {
                int childId = entry.getKey();
                String[] raw = entry.getValue();
                String bomId = raw[0], role = raw[1], childBomId = raw[2];

                Map<String, String> params = new HashMap<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT param_key, param_value FROM ad_bom_child_param WHERE bom_child_id = ? AND is_active = 1")) {
                    ps.setInt(1, childId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            params.put(rs.getString("param_key"), rs.getString("param_value"));
                        }
                    }
                }

                FloorBOMChild child = new FloorBOMChild(childId, role, childBomId, params);
                FloorBOMNode node = bomTree.get(bomId);
                if (node != null) node.children().add(child);
            }

            System.out.printf("[FLOOR-BOM] Loaded %d BOM nodes, %d children%n",
                bomTree.size(), rawChildren.size());

        } catch (SQLException e) {
            System.err.println("[FLOOR-BOM] Failed to load: " + e.getMessage());
        }
    }

    // --- Resolution ---

    /**
     * Resolve a floor plate BOM into zone bounds.
     * @param bomId root BOM ID (e.g. "TYPICAL_CONDO_FLOOR")
     * @param grid  grid definition with axis labels and positions
     * @return list of resolved zone bounds
     */
    public List<ZoneBounds> resolveFloorPlate(String bomId, GridInfo grid) {
        FloorBOMNode root = bomTree.get(bomId);
        if (root == null) {
            System.err.println("[FLOOR-BOM] BOM not found: " + bomId);
            return List.of();
        }

        int xBands = grid.xBands();
        int yBands = grid.yBands();

        // Cell ownership grid: [x][y] = role or null
        String[][] cellOwner = new String[xBands][yBands];
        List<ZoneBounds> result = new ArrayList<>();

        // Track resolved zone extents for carve references
        Map<String, int[]> zoneExtents = new HashMap<>(); // role -> [xStart, xEnd, yStart, yEnd]

        // Pass 1: Find CORE (explicit band)
        FloorBOMChild coreChild = findByRule(root, "band");
        int coreXStart = -1, coreXEnd = -1, coreYStart = -1, coreYEnd = -1;
        if (coreChild != null) {
            coreXStart = grid.xIndex(coreChild.param("x_band_start"));
            coreXEnd = grid.xIndex(coreChild.param("x_band_end"));
            coreYStart = grid.yIndex(coreChild.param("y_band_start"));
            coreYEnd = grid.yIndex(coreChild.param("y_band_end"));

            // Mark cells
            for (int x = coreXStart; x < coreXEnd; x++) {
                for (int y = coreYStart; y < coreYEnd; y++) {
                    cellOwner[x][y] = coreChild.role();
                }
            }
            zoneExtents.put(coreChild.role(), new int[]{coreXStart, coreXEnd, coreYStart, coreYEnd});

            // Resolve core internals if nested
            if (coreChild.childBomId() != null) {
                FloorBOMNode coreNode = bomTree.get(coreChild.childBomId());
                if (coreNode != null) {
                    result.addAll(resolveCoreInternals(coreNode, grid,
                        coreXStart, coreXEnd, coreYStart, coreYEnd));
                }
            }
        }

        // Pass 2: SIDE rules (corridor adjacent to core)
        for (FloorBOMChild child : root.children()) {
            if (!"side".equals(child.param("spatial_rule"))) continue;
            String side = child.param("side");
            int widthBays = child.paramInt("width_bays", 1);
            String spaceType = child.param("space_type", child.role());

            int xStart, xEnd;
            if ("west".equals(side)) {
                xEnd = coreXStart;
                xStart = coreXStart - widthBays;
            } else {
                xStart = coreXEnd;
                xEnd = coreXEnd + widthBays;
            }

            for (int x = xStart; x < xEnd; x++) {
                for (int y = 0; y < yBands; y++) {
                    cellOwner[x][y] = child.role();
                }
            }

            String gridLabel = grid.xAxes.get(xStart) + grid.yAxes.get(0) + "-"
                             + grid.xAxes.get(xEnd) + grid.yAxes.get(yBands);
            result.add(new ZoneBounds(child.role(), spaceType, gridLabel,
                xStart, xEnd, 0, yBands,
                grid.getX(xStart), grid.getY(0), grid.getX(xEnd), grid.getY(yBands),
                child.param("room_name")));
            zoneExtents.put(child.role(), new int[]{xStart, xEnd, 0, yBands});
        }

        // Pass 3: FILL_REMAINING rules (units)
        for (FloorBOMChild child : root.children()) {
            if (!"fill_remaining".equals(child.param("spatial_rule"))) continue;
            String side = child.param("side");
            String spaceType = child.param("space_type", child.role());
            int splitCount = child.paramInt("split_count", 1);
            int splitAtCell = child.paramInt("split_at_cell", yBands / 2);

            // Find remaining unowned x-bands on this side
            int xStart, xEnd;
            if ("west".equals(side)) {
                xStart = 0;
                xEnd = coreXStart;
                // Exclude already-owned bands (corridor)
                while (xEnd > xStart && allOwned(cellOwner, xEnd - 1, yBands)) xEnd--;
            } else {
                xStart = coreXEnd;
                xEnd = xBands;
                while (xStart < xEnd && allOwned(cellOwner, xStart, yBands)) xStart++;
            }

            // Mark cells
            for (int x = xStart; x < xEnd; x++) {
                for (int y = 0; y < yBands; y++) {
                    if (cellOwner[x][y] == null) {
                        cellOwner[x][y] = child.role();
                    }
                }
            }
            zoneExtents.put(child.role(), new int[]{xStart, xEnd, 0, yBands});

            // Split into sub-zones
            if (splitCount <= 1) {
                String gridLabel = grid.xAxes.get(xStart) + grid.yAxes.get(0) + "-"
                                 + grid.xAxes.get(xEnd) + grid.yAxes.get(yBands);
                result.add(new ZoneBounds(child.role(), spaceType, gridLabel,
                    xStart, xEnd, 0, yBands,
                    grid.getX(xStart), grid.getY(0), grid.getX(xEnd), grid.getY(yBands),
                    child.param("room_name")));
            } else {
                for (int i = 0; i < splitCount; i++) {
                    int yS = (i == 0) ? 0 : splitAtCell;
                    int yE = (i == 0) ? splitAtCell : yBands;
                    String suffix = "_" + (i + 1);
                    // Phase 95B: room_name_1, room_name_2, etc.
                    String roomName = child.param("room_name_" + (i + 1));
                    String gridLabel = grid.xAxes.get(xStart) + grid.yAxes.get(yS) + "-"
                                     + grid.xAxes.get(xEnd) + grid.yAxes.get(yE);
                    result.add(new ZoneBounds(child.role() + suffix, spaceType, gridLabel,
                        xStart, xEnd, yS, yE,
                        grid.getX(xStart), grid.getY(yS), grid.getX(xEnd), grid.getY(yE),
                        roomName));
                }
            }
        }

        // Pass 4: CARVE rules (toilet carved from unit zone)
        for (FloorBOMChild child : root.children()) {
            if (!"carve".equals(child.param("spatial_rule"))) continue;
            String carveFrom = child.param("carve_from");
            String carveSide = child.param("carve_side", "west");
            int carveYCell = child.paramInt("carve_y_cell", 0);
            String spaceType = child.param("space_type", child.role());

            int[] parentExtent = zoneExtents.get(carveFrom);
            if (parentExtent == null) continue;

            int xStart, xEnd;
            if ("west".equals(carveSide)) {
                xStart = parentExtent[0];
                xEnd = parentExtent[0] + 1; // 1 bay wide
            } else {
                xStart = parentExtent[1] - 1;
                xEnd = parentExtent[1];
            }
            int yStart = carveYCell;
            int yEnd = carveYCell + 1;

            // Mark cell
            for (int x = xStart; x < xEnd; x++) {
                for (int y = yStart; y < yEnd; y++) {
                    cellOwner[x][y] = child.role();
                }
            }

            String gridLabel = grid.xAxes.get(xStart) + grid.yAxes.get(yStart) + "-"
                             + grid.xAxes.get(xEnd) + grid.yAxes.get(yEnd);
            result.add(new ZoneBounds(child.role(), spaceType, gridLabel,
                xStart, xEnd, yStart, yEnd,
                grid.getX(xStart), grid.getY(yStart), grid.getX(xEnd), grid.getY(yEnd),
                child.param("room_name")));
        }

        return result;
    }

    /**
     * Resolve core internals: at-north, at-south, fill-center.
     */
    private List<ZoneBounds> resolveCoreInternals(
            FloorBOMNode coreNode, GridInfo grid,
            int coreXStart, int coreXEnd, int coreYStart, int coreYEnd) {

        List<ZoneBounds> result = new ArrayList<>();
        int coreCells = coreYEnd - coreYStart;

        // Track which core y-cells are claimed
        boolean[] claimed = new boolean[coreCells];

        // Pass A: at-rules (north/south)
        for (FloorBOMChild child : coreNode.children()) {
            if (!"at".equals(child.param("spatial_rule"))) continue;
            String at = child.param("at");
            int heightCells = child.paramInt("height_cells", 1);
            String spaceType = child.param("space_type", child.role());
            // Phase 103: width_cells constrains X-span (stairs stay narrow in wider core)
            int widthCells = child.paramInt("width_cells", coreXEnd - coreXStart);
            int effectiveXEnd = coreXStart + Math.min(widthCells, coreXEnd - coreXStart);

            int yStart, yEnd;
            if ("north".equals(at)) {
                // North = low Y index (top of grid = first cells)
                yStart = coreYStart;
                yEnd = coreYStart + heightCells;
                for (int i = 0; i < heightCells; i++) claimed[i] = true;
            } else {
                // South = high Y index (bottom of grid = last cells)
                yStart = coreYEnd - heightCells;
                yEnd = coreYEnd;
                for (int i = coreCells - heightCells; i < coreCells; i++) claimed[i] = true;
            }

            String gridLabel = grid.xAxes.get(coreXStart) + grid.yAxes.get(yStart) + "-"
                             + grid.xAxes.get(effectiveXEnd) + grid.yAxes.get(yEnd);
            result.add(new ZoneBounds(child.role(), spaceType, gridLabel,
                coreXStart, effectiveXEnd, yStart, yEnd,
                grid.getX(coreXStart), grid.getY(yStart),
                grid.getX(effectiveXEnd), grid.getY(yEnd),
                child.param("room_name")));
        }

        // Pass B: fill_center — remaining unclaimed cells
        for (FloorBOMChild child : coreNode.children()) {
            if (!"fill_center".equals(child.param("spatial_rule"))) continue;
            String spaceType = child.param("space_type", child.role());

            // Find first and last unclaimed cells
            int firstFree = -1, lastFree = -1;
            for (int i = 0; i < coreCells; i++) {
                if (!claimed[i]) {
                    if (firstFree < 0) firstFree = i;
                    lastFree = i;
                }
            }

            if (firstFree >= 0) {
                int yStart = coreYStart + firstFree;
                int yEnd = coreYStart + lastFree + 1;

                String gridLabel = grid.xAxes.get(coreXStart) + grid.yAxes.get(yStart) + "-"
                                 + grid.xAxes.get(coreXEnd) + grid.yAxes.get(yEnd);
                result.add(new ZoneBounds(child.role(), spaceType, gridLabel,
                    coreXStart, coreXEnd, yStart, yEnd,
                    grid.getX(coreXStart), grid.getY(yStart),
                    grid.getX(coreXEnd), grid.getY(yEnd),
                    child.param("room_name")));
            }
        }

        return result;
    }

    // --- Helpers ---

    private FloorBOMChild findByRule(FloorBOMNode node, String rule) {
        for (FloorBOMChild child : node.children()) {
            if (rule.equals(child.param("spatial_rule"))) return child;
        }
        return null;
    }

    /** Check if all y-cells in column x are already owned. */
    private boolean allOwned(String[][] cellOwner, int x, int yBands) {
        for (int y = 0; y < yBands; y++) {
            if (cellOwner[x][y] == null) return false;
        }
        return true;
    }

    // --- Phase 95B: Pipeline Integration ---

    /**
     * Resolve floor plate BOM into a map of room name → bounds [minX, minY, maxX, maxY].
     * Only zones with room_name params are included.
     */
    public Map<String, double[]> resolveRoomBoundsMap(String bomId, GridInfo grid) {
        List<ZoneBounds> zones = resolveFloorPlate(bomId, grid);
        Map<String, double[]> map = new LinkedHashMap<>();
        for (ZoneBounds z : zones) {
            if (z.roomName() != null) {
                map.put(z.roomName(), new double[]{z.minX(), z.minY(), z.maxX(), z.maxY()});
            }
        }
        return map;
    }

    /**
     * Phase 102B: Resolve floor plate BOM into a map of spaceType → bounds.
     * Used when a room has no explicit bounds but the building has a floor BOM
     * that defines a zone for that space type (e.g. TOILET_BLOCK).
     */
    public Map<String, double[]> resolveTypeBoundsMap(String bomId, GridInfo grid) {
        List<ZoneBounds> zones = resolveFloorPlate(bomId, grid);
        Map<String, double[]> map = new LinkedHashMap<>();
        for (ZoneBounds z : zones) {
            if (z.spaceType() != null && !map.containsKey(z.spaceType())) {
                map.put(z.spaceType(), new double[]{z.minX(), z.minY(), z.maxX(), z.maxY()});
            }
        }
        return map;
    }

    /**
     * Convert a BuildingDefinition.GridDef to a FloorPlateBOMResolver.GridInfo
     * by computing cumulative positions from axis spacing.
     * Only axes with corresponding spacing are included — extra trailing axes are trimmed.
     */
    public static GridInfo gridInfoFromGridDef(BuildingDefinition.GridDef gridDef) {
        // Effective axis count = spacing count + 1
        int xCount = (gridDef.xSpacing() != null ? gridDef.xSpacing().size() : 0) + 1;
        int yCount = (gridDef.ySpacing() != null ? gridDef.ySpacing().size() : 0) + 1;

        // Trim axes to match spacing
        List<String> xAxes = gridDef.xAxes().subList(0, Math.min(xCount, gridDef.xAxes().size()));
        List<String> yAxes = gridDef.yAxes().subList(0, Math.min(yCount, gridDef.yAxes().size()));

        // Compute cumulative X positions
        List<Double> xPositions = new ArrayList<>();
        double pos = 0;
        xPositions.add(pos);
        if (gridDef.xSpacing() != null) {
            for (double sp : gridDef.xSpacing()) {
                pos += sp;
                xPositions.add(pos);
            }
        }

        // Compute cumulative Y positions
        List<Double> yPositions = new ArrayList<>();
        pos = 0;
        yPositions.add(pos);
        if (gridDef.ySpacing() != null) {
            for (double sp : gridDef.ySpacing()) {
                pos += sp;
                yPositions.add(pos);
            }
        }

        return new GridInfo(xAxes, xPositions, yAxes, yPositions);
    }
}
