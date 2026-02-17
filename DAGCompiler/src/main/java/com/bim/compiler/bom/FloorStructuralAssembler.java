package com.bim.compiler.bom;

import java.sql.*;
import java.util.*;

/**
 * Floor Structural BOM Assembler (Phase 81B)
 *
 * Groups structural elements (beams, columns, slabs) under floor-level assemblies.
 *
 * Structure:
 *   FLOOR_STRUCTURAL_Ground
 *   ├── SLAB_Ground (floor slab)
 *   ├── BEAM_GRID_* (all beams on this floor)
 *   └── COLUMN_* (columns on this floor)
 *
 * Benefits:
 * - Outliner: Collapse structural elements per floor
 * - BOM rollup: Steel/concrete quantities per floor
 * - En-bloc selection: Select floor structural package
 */
public class FloorStructuralAssembler {

    private final Connection conn;

    public FloorStructuralAssembler(Connection conn) {
        this.conn = conn;
    }

    /**
     * Create floor-level structural assemblies for all storeys.
     */
    public AssemblyResult assembleFloorStructure() throws SQLException {
        int floorsProcessed = 0;
        int beamsLinked = 0;
        int columnsLinked = 0;
        int slabsLinked = 0;

        // Get all unique storeys
        Set<String> storeys = new LinkedHashSet<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT DISTINCT storey FROM elements_meta WHERE storey IS NOT NULL ORDER BY storey")) {
            while (rs.next()) {
                storeys.add(rs.getString("storey"));
            }
        }

        for (String storey : storeys) {
            String assemblyGuid = "FLOOR_STRUCTURAL_" + storey.toUpperCase().replace(" ", "_");

            // Check if assembly already exists
            if (assemblyExists(assemblyGuid)) continue;

            // Get structural elements for this storey
            List<ElementInfo> beams = getElementsByStoreyAndClass(storey, "IfcBeam");
            List<ElementInfo> columns = getElementsByStoreyAndClass(storey, "IfcColumn");
            List<ElementInfo> slabs = getElementsByStoreyAndClass(storey, "IfcSlab");

            // Skip if no structural elements
            if (beams.isEmpty() && columns.isEmpty() && slabs.isEmpty()) continue;

            // Calculate assembly bounds from components
            BoundsInfo bounds = calculateBounds(beams, columns, slabs);

            // Create floor structural assembly
            createAssembly(assemblyGuid, "FLOOR_STRUCTURAL",
                "Floor Structure " + storey, storey, bounds);

            int seq = 1;

            // Link slabs
            for (ElementInfo slab : slabs) {
                linkComponent(assemblyGuid, slab.guid, "SLAB", seq++);
                slabsLinked++;
            }

            // Link beams
            for (ElementInfo beam : beams) {
                linkComponent(assemblyGuid, beam.guid, "BEAM", seq++);
                beamsLinked++;
            }

            // Link columns
            for (ElementInfo column : columns) {
                linkComponent(assemblyGuid, column.guid, "COLUMN", seq++);
                columnsLinked++;
            }

            floorsProcessed++;
        }

        return new AssemblyResult(floorsProcessed, beamsLinked, columnsLinked, slabsLinked);
    }

    public record AssemblyResult(
        int floorsProcessed,
        int beamsLinked,
        int columnsLinked,
        int slabsLinked
    ) {
        public void print() {
            System.out.println("=== Floor Structural Assembly Result ===");
            System.out.println("  Floors processed: " + floorsProcessed);
            System.out.println("  Beams linked:     " + beamsLinked);
            System.out.println("  Columns linked:   " + columnsLinked);
            System.out.println("  Slabs linked:     " + slabsLinked);
        }
    }

    private record ElementInfo(String guid, double minX, double maxX,
                                double minY, double maxY, double minZ, double maxZ) {}

    private record BoundsInfo(double minX, double maxX, double minY, double maxY,
                               double minZ, double maxZ, double width, double depth, double height) {}

    private boolean assemblyExists(String assemblyGuid) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT 1 FROM element_assemblies WHERE assembly_guid = ?")) {
            ps.setString(1, assemblyGuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<ElementInfo> getElementsByStoreyAndClass(String storey, String ifcClass)
            throws SQLException {
        List<ElementInfo> elements = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement("""
            SELECT m.guid, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            WHERE m.storey = ? AND m.ifc_class = ?
            """)) {
            ps.setString(1, storey);
            ps.setString(2, ifcClass);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    elements.add(new ElementInfo(
                        rs.getString("guid"),
                        rs.getDouble("minX"), rs.getDouble("maxX"),
                        rs.getDouble("minY"), rs.getDouble("maxY"),
                        rs.getDouble("minZ"), rs.getDouble("maxZ")
                    ));
                }
            }
        }

        return elements;
    }

    private BoundsInfo calculateBounds(List<ElementInfo>... elementLists) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (List<ElementInfo> list : elementLists) {
            for (ElementInfo e : list) {
                minX = Math.min(minX, e.minX); maxX = Math.max(maxX, e.maxX);
                minY = Math.min(minY, e.minY); maxY = Math.max(maxY, e.maxY);
                minZ = Math.min(minZ, e.minZ); maxZ = Math.max(maxZ, e.maxZ);
            }
        }

        if (minX == Double.MAX_VALUE) {
            return new BoundsInfo(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        return new BoundsInfo(minX, maxX, minY, maxY, minZ, maxZ,
            maxX - minX, maxY - minY, maxZ - minZ);
    }

    private void createAssembly(String assemblyGuid, String type, String name,
                                 String storey, BoundsInfo bounds) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO element_assemblies
            (assembly_guid, assembly_type, ifc_class, name, total_width, total_depth, total_height, storey)
            VALUES (?, ?, 'IfcElementAssembly', ?, ?, ?, ?, ?)
            """)) {
            ps.setString(1, assemblyGuid);
            ps.setString(2, type);
            ps.setString(3, name);
            ps.setDouble(4, bounds.width);
            ps.setDouble(5, bounds.depth);
            ps.setDouble(6, bounds.height);
            ps.setString(7, storey);
            ps.execute();
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

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: FloorStructuralAssembler <database.db>");
            return;
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + args[0])) {
            conn.setAutoCommit(false);

            FloorStructuralAssembler assembler = new FloorStructuralAssembler(conn);
            AssemblyResult result = assembler.assembleFloorStructure();
            result.print();

            conn.commit();
        }
    }
}
