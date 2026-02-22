package com.bim.compiler.bom;

import com.bim.compiler.contract.IAssembler;
import java.sql.*;
import java.util.*;

/**
 * Wall + Opening Assembly POC (Phase 81B)
 *
 * Links doors and windows to their parent wall assemblies in the BOM hierarchy.
 *
 * Result structure:
 *   WALL_PANEL_ASSEMBLY
 *   ├── FRAME_* (existing)
 *   ├── CLADDING_* (existing)
 *   └── DOOR_* or WINDOW_* (NEW: linked as OPENING)
 *
 * Benefits:
 * - Outliner shows openings under their walls
 * - Move wall → openings move with it
 * - Change opening type in library → recompile → all updated
 */
public class WallOpeningAssembler implements IAssembler {

    private final Connection conn;

    // Tolerance for geometric matching (meters)
    private static final double MATCH_TOLERANCE = 0.3;

    public WallOpeningAssembler(Connection conn) {
        this.conn = conn;
    }

    /**
     * Link all doors and windows to their wall assemblies.
     * Call this after BuildingWriter has written all elements.
     */
    public AssemblyResult linkOpeningsToWalls() throws SQLException {
        int doorsLinked = 0;
        int windowsLinked = 0;
        int notMatched = 0;

        // Get all wall assemblies with their bounds
        List<WallAssemblyInfo> walls = getWallAssemblies();

        // Get all doors
        List<OpeningInfo> doors = getOpenings("IfcDoor");
        for (OpeningInfo door : doors) {
            WallAssemblyInfo matchedWall = findMatchingWall(door, walls);
            if (matchedWall != null) {
                linkOpeningToWall(matchedWall, door, doorsLinked + windowsLinked + 1);
                doorsLinked++;
            } else {
                notMatched++;
            }
        }

        // Get all windows
        List<OpeningInfo> windows = getOpenings("IfcWindow");
        for (OpeningInfo window : windows) {
            WallAssemblyInfo matchedWall = findMatchingWall(window, walls);
            if (matchedWall != null) {
                linkOpeningToWall(matchedWall, window, doorsLinked + windowsLinked + 1);
                windowsLinked++;
            } else {
                notMatched++;
            }
        }

        return new AssemblyResult(doorsLinked, windowsLinked, notMatched, walls.size());
    }

    /**
     * IAssembler contract — delegates to linkOpeningsToWalls().
     * [EXTRACTED: ARCHITECTURE.md §Contracts]
     */
    @Override
    public IAssembler.AssemblyOutcome assemble() throws SQLException {
        AssemblyResult r = linkOpeningsToWalls();
        List<String> msgs = new ArrayList<>();
        msgs.add("=== Wall + Opening Assembly Result ===");
        msgs.add("  Walls:           " + r.totalWalls());
        msgs.add("  Doors linked:    " + r.doorsLinked());
        msgs.add("  Windows linked:  " + r.windowsLinked());
        msgs.add("  Not matched:     " + r.notMatched());
        if (r.notMatched() > 0) msgs.add("  (Not matched = floating elements or exterior)");
        return IAssembler.AssemblyOutcome.of(r.doorsLinked() + r.windowsLinked(), msgs);
    }

    /**
     * Result of assembly operation.
     */
    public record AssemblyResult(
        int doorsLinked,
        int windowsLinked,
        int notMatched,
        int totalWalls
    ) {
        public void print() {
            System.out.println("=== Wall + Opening Assembly Result ===");
            System.out.println("  Walls:           " + totalWalls);
            System.out.println("  Doors linked:    " + doorsLinked);
            System.out.println("  Windows linked:  " + windowsLinked);
            System.out.println("  Not matched:     " + notMatched);
            if (notMatched > 0) {
                System.out.println("  (Not matched = floating elements or exterior)");
            }
        }
    }

    /**
     * Wall assembly info with bounding box.
     */
    private record WallAssemblyInfo(
        String assemblyGuid,
        String name,
        String storey,
        double minX, double maxX,
        double minY, double maxY,
        double minZ, double maxZ,
        int existingComponents  // Count of existing components
    ) {
        boolean overlapsXY(OpeningInfo opening) {
            // Check if opening overlaps wall in XY plane (within tolerance)
            boolean xOverlap = opening.maxX >= minX - MATCH_TOLERANCE &&
                               opening.minX <= maxX + MATCH_TOLERANCE;
            boolean yOverlap = opening.maxY >= minY - MATCH_TOLERANCE &&
                               opening.minY <= maxY + MATCH_TOLERANCE;
            return xOverlap && yOverlap;
        }

        boolean sameSorey(OpeningInfo opening) {
            return storey.equalsIgnoreCase(opening.storey);
        }
    }

    /**
     * Door or window opening info.
     */
    private record OpeningInfo(
        String guid,
        String ifcClass,
        String storey,
        double minX, double maxX,
        double minY, double maxY,
        double minZ, double maxZ
    ) {}

    /**
     * Get all wall assemblies from database.
     */
    private List<WallAssemblyInfo> getWallAssemblies() throws SQLException {
        List<WallAssemblyInfo> walls = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT
                ea.assembly_guid,
                ea.name,
                ea.storey,
                MIN(r.minX) as minX, MAX(r.maxX) as maxX,
                MIN(r.minY) as minY, MAX(r.maxY) as maxY,
                MIN(r.minZ) as minZ, MAX(r.maxZ) as maxZ,
                (SELECT COUNT(*) FROM assembly_components ac WHERE ac.assembly_guid = ea.assembly_guid) as comp_count
            FROM element_assemblies ea
            JOIN assembly_components ac ON ea.assembly_guid = ac.assembly_guid
            JOIN elements_meta m ON ac.component_guid = m.guid
            JOIN elements_rtree r ON m.id = r.id
            WHERE ea.assembly_type = 'WALL_PANEL'
            GROUP BY ea.assembly_guid
            """)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    walls.add(new WallAssemblyInfo(
                        rs.getString("assembly_guid"),
                        rs.getString("name"),
                        rs.getString("storey"),
                        rs.getDouble("minX"), rs.getDouble("maxX"),
                        rs.getDouble("minY"), rs.getDouble("maxY"),
                        rs.getDouble("minZ"), rs.getDouble("maxZ"),
                        rs.getInt("comp_count")
                    ));
                }
            }
        }

        return walls;
    }

    /**
     * Get openings (doors or windows) NOT already linked as OPENING in assembly_components.
     * Phase 88: Skip openings already linked by BuildingWriter direct path.
     */
    private List<OpeningInfo> getOpenings(String ifcClass) throws SQLException {
        List<OpeningInfo> openings = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT
                m.guid,
                m.ifc_class,
                m.storey,
                r.minX, r.maxX,
                r.minY, r.maxY,
                r.minZ, r.maxZ
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            WHERE m.ifc_class = ?
              AND m.guid NOT IN (
                  SELECT component_guid FROM assembly_components WHERE role = 'OPENING'
              )
            """)) {
            ps.setString(1, ifcClass);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    openings.add(new OpeningInfo(
                        rs.getString("guid"),
                        rs.getString("ifc_class"),
                        rs.getString("storey"),
                        rs.getDouble("minX"), rs.getDouble("maxX"),
                        rs.getDouble("minY"), rs.getDouble("maxY"),
                        rs.getDouble("minZ"), rs.getDouble("maxZ")
                    ));
                }
            }
        }

        return openings;
    }

    /**
     * Find the wall assembly that matches this opening.
     * Match criteria: same storey AND geometric overlap in XY plane.
     */
    private WallAssemblyInfo findMatchingWall(OpeningInfo opening, List<WallAssemblyInfo> walls) {
        WallAssemblyInfo bestMatch = null;
        double bestOverlap = 0;

        for (WallAssemblyInfo wall : walls) {
            if (!wall.sameSorey(opening)) continue;
            if (!wall.overlapsXY(opening)) continue;

            // Calculate overlap area for ranking
            double overlapX = Math.min(wall.maxX, opening.maxX) - Math.max(wall.minX, opening.minX);
            double overlapY = Math.min(wall.maxY, opening.maxY) - Math.max(wall.minY, opening.minY);
            double overlap = Math.max(0, overlapX) * Math.max(0, overlapY);

            if (overlap > bestOverlap) {
                bestOverlap = overlap;
                bestMatch = wall;
            }
        }

        return bestMatch;
    }

    /**
     * Link opening to wall assembly as a component.
     */
    private void linkOpeningToWall(WallAssemblyInfo wall, OpeningInfo opening, int sequence)
            throws SQLException {

        // Calculate local offset (opening position relative to wall origin)
        double localX = opening.minX - wall.minX;
        double localY = opening.minY - wall.minY;
        double localZ = opening.minZ - wall.minZ;

        // Insert into assembly_components
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT OR IGNORE INTO assembly_components
            (assembly_guid, component_guid, role, local_x, local_y, local_z, sequence)
            VALUES (?, ?, 'OPENING', ?, ?, ?, ?)
            """)) {
            ps.setString(1, wall.assemblyGuid);
            ps.setString(2, opening.guid);
            ps.setDouble(3, localX);
            ps.setDouble(4, localY);
            ps.setDouble(5, localZ);
            ps.setInt(6, wall.existingComponents + sequence);
            ps.execute();
        }
    }

    /**
     * Print sample wall+opening tree for verification.
     */
    public void printSampleTree(int count) throws SQLException {
        System.out.println("\n=== Sample Wall + Opening Trees ===");

        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT DISTINCT ea.assembly_guid
            FROM element_assemblies ea
            JOIN assembly_components ac ON ea.assembly_guid = ac.assembly_guid
            WHERE ac.role = 'OPENING'
            LIMIT ?
            """)) {
            ps.setInt(1, count);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String assemblyGuid = rs.getString("assembly_guid");
                    BOMAssembly.BOMNode tree = BOMAssembly.buildTree(conn, assemblyGuid);
                    if (tree != null) {
                        tree.printTree("");
                        System.out.println();
                    }
                }
            }
        }
    }

    // =========================================================================
    // Demo / Test
    // =========================================================================

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: WallOpeningAssembler <database.db>");
            System.out.println("  Links doors and windows to their wall assemblies");
            return;
        }

        String dbPath = args[0];

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            conn.setAutoCommit(false);

            WallOpeningAssembler assembler = new WallOpeningAssembler(conn);
            AssemblyResult result = assembler.linkOpeningsToWalls();
            result.print();

            conn.commit();

            // Print sample trees
            assembler.printSampleTree(3);
        }
    }
}
