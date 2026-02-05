package com.bim.compiler.bom;

import java.sql.*;
import java.util.*;

/**
 * Stair Assembly Enhancer (Phase 81B)
 *
 * Enhances existing stair assemblies by adding landings and railings.
 * Uses library prefabs from TERMINAL db where available.
 *
 * Structure:
 *   STAIR_A_ASSEMBLY
 *   ├── STAIRFLIGHT_A (existing - from library)
 *   ├── LANDING_A_TOP (new)
 *   ├── LANDING_A_BOTTOM (new)
 *   ├── RAILING_A_LEFT (new - from library if available)
 *   └── RAILING_A_RIGHT (new - from library if available)
 *
 * Benefits:
 * - Complete stair BOM for procurement
 * - Prefab package: all stair components move together
 * - Library-driven: change railing type → recompile → all updated
 */
public class StairAssemblyEnhancer {

    private final Connection conn;

    public StairAssemblyEnhancer(Connection conn) {
        this.conn = conn;
    }

    /**
     * Enhance all stair assemblies with landings and railings.
     */
    public EnhanceResult enhanceStairAssemblies() throws SQLException {
        int stairsEnhanced = 0;
        int landingsLinked = 0;
        int railingsLinked = 0;

        // Find existing stair assemblies
        List<StairAssemblyInfo> stairAssemblies = getStairAssemblies();

        for (StairAssemblyInfo stair : stairAssemblies) {
            int componentsBefore = getComponentCount(stair.assemblyGuid);

            // Find and link landings near this stair
            List<ElementInfo> landings = findNearbyLandings(stair);
            for (ElementInfo landing : landings) {
                if (!isAlreadyLinked(stair.assemblyGuid, landing.guid)) {
                    linkComponent(stair.assemblyGuid, landing.guid, "LANDING",
                        componentsBefore + landingsLinked + 1);
                    landingsLinked++;
                }
            }

            // Find and link railings near this stair
            List<ElementInfo> railings = findNearbyRailings(stair);
            for (ElementInfo railing : railings) {
                if (!isAlreadyLinked(stair.assemblyGuid, railing.guid)) {
                    linkComponent(stair.assemblyGuid, railing.guid, "RAILING",
                        componentsBefore + landingsLinked + railingsLinked + 1);
                    railingsLinked++;
                }
            }

            int componentsAfter = getComponentCount(stair.assemblyGuid);
            if (componentsAfter > componentsBefore) {
                stairsEnhanced++;
            }
        }

        return new EnhanceResult(stairAssemblies.size(), stairsEnhanced, landingsLinked, railingsLinked);
    }

    public record EnhanceResult(
        int totalStairs,
        int stairsEnhanced,
        int landingsLinked,
        int railingsLinked
    ) {
        public void print() {
            System.out.println("=== Stair Assembly Enhancement Result ===");
            System.out.println("  Total stair assemblies: " + totalStairs);
            System.out.println("  Stairs enhanced:        " + stairsEnhanced);
            System.out.println("  Landings linked:        " + landingsLinked);
            System.out.println("  Railings linked:        " + railingsLinked);
        }
    }

    private record StairAssemblyInfo(
        String assemblyGuid,
        String name,
        String storey,
        double minX, double maxX,
        double minY, double maxY,
        double minZ, double maxZ
    ) {}

    private record ElementInfo(
        String guid,
        double minX, double maxX,
        double minY, double maxY,
        double minZ, double maxZ
    ) {}

    /**
     * Get existing stair assemblies with their bounds.
     */
    private List<StairAssemblyInfo> getStairAssemblies() throws SQLException {
        List<StairAssemblyInfo> stairs = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT
                ea.assembly_guid,
                ea.name,
                ea.storey,
                MIN(r.minX) as minX, MAX(r.maxX) as maxX,
                MIN(r.minY) as minY, MAX(r.maxY) as maxY,
                MIN(r.minZ) as minZ, MAX(r.maxZ) as maxZ
            FROM element_assemblies ea
            JOIN assembly_components ac ON ea.assembly_guid = ac.assembly_guid
            JOIN elements_meta m ON ac.component_guid = m.guid
            JOIN elements_rtree r ON m.id = r.id
            WHERE ea.assembly_type = 'STAIR_ASSEMBLY'
            GROUP BY ea.assembly_guid
            """)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stairs.add(new StairAssemblyInfo(
                        rs.getString("assembly_guid"),
                        rs.getString("name"),
                        rs.getString("storey"),
                        rs.getDouble("minX"), rs.getDouble("maxX"),
                        rs.getDouble("minY"), rs.getDouble("maxY"),
                        rs.getDouble("minZ"), rs.getDouble("maxZ")
                    ));
                }
            }
        }

        return stairs;
    }

    /**
     * Find landings near this stair (within tolerance).
     */
    private List<ElementInfo> findNearbyLandings(StairAssemblyInfo stair) throws SQLException {
        List<ElementInfo> landings = new ArrayList<>();
        double tolerance = 2.0;  // 2m tolerance for nearby elements

        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT m.guid, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            WHERE m.ifc_class = 'IfcSlab' AND m.element_type = 'LANDING'
              AND r.maxX >= ? AND r.minX <= ?
              AND r.maxY >= ? AND r.minY <= ?
            """)) {
            ps.setDouble(1, stair.minX - tolerance);
            ps.setDouble(2, stair.maxX + tolerance);
            ps.setDouble(3, stair.minY - tolerance);
            ps.setDouble(4, stair.maxY + tolerance);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    landings.add(new ElementInfo(
                        rs.getString("guid"),
                        rs.getDouble("minX"), rs.getDouble("maxX"),
                        rs.getDouble("minY"), rs.getDouble("maxY"),
                        rs.getDouble("minZ"), rs.getDouble("maxZ")
                    ));
                }
            }
        }

        return landings;
    }

    /**
     * Find railings near this stair (within tolerance).
     */
    private List<ElementInfo> findNearbyRailings(StairAssemblyInfo stair) throws SQLException {
        List<ElementInfo> railings = new ArrayList<>();
        double tolerance = 1.0;  // 1m tolerance for railings

        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT m.guid, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            WHERE m.ifc_class = 'IfcRailing'
              AND r.maxX >= ? AND r.minX <= ?
              AND r.maxY >= ? AND r.minY <= ?
            """)) {
            ps.setDouble(1, stair.minX - tolerance);
            ps.setDouble(2, stair.maxX + tolerance);
            ps.setDouble(3, stair.minY - tolerance);
            ps.setDouble(4, stair.maxY + tolerance);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    railings.add(new ElementInfo(
                        rs.getString("guid"),
                        rs.getDouble("minX"), rs.getDouble("maxX"),
                        rs.getDouble("minY"), rs.getDouble("maxY"),
                        rs.getDouble("minZ"), rs.getDouble("maxZ")
                    ));
                }
            }
        }

        return railings;
    }

    private int getComponentCount(String assemblyGuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT COUNT(*) FROM assembly_components WHERE assembly_guid = ?")) {
            ps.setString(1, assemblyGuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private boolean isAlreadyLinked(String assemblyGuid, String componentGuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT 1 FROM assembly_components WHERE assembly_guid = ? AND component_guid = ?")) {
            ps.setString(1, assemblyGuid);
            ps.setString(2, componentGuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void linkComponent(String assemblyGuid, String componentGuid,
                                String role, int sequence) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT OR IGNORE INTO assembly_components
            (assembly_guid, component_guid, role, local_x, local_y, local_z, sequence)
            VALUES (?, ?, ?, 0, 0, 0, ?)
            """)) {
            ps.setString(1, assemblyGuid);
            ps.setString(2, componentGuid);
            ps.setString(3, role);
            ps.setInt(4, sequence);
            ps.execute();
        }
    }

    /**
     * Print stair assembly trees for verification.
     */
    public void printStairTrees() throws SQLException {
        List<StairAssemblyInfo> stairs = getStairAssemblies();

        System.out.println("\n=== Stair Assembly Trees ===");
        for (StairAssemblyInfo stair : stairs) {
            BOMAssembly.BOMNode tree = BOMAssembly.buildTree(conn, stair.assemblyGuid);
            if (tree != null) {
                tree.printTree("");
                System.out.println();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: StairAssemblyEnhancer <database.db>");
            return;
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + args[0])) {
            conn.setAutoCommit(false);

            StairAssemblyEnhancer enhancer = new StairAssemblyEnhancer(conn);
            EnhanceResult result = enhancer.enhanceStairAssemblies();
            result.print();

            conn.commit();

            // Print trees
            enhancer.printStairTrees();
        }
    }
}
