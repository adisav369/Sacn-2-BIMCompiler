package com.bim.compiler.export;

import java.io.*;
import java.sql.*;
import java.util.*;

/**
 * Bill of Materials (BOM) Exporter for iDempiere ERP.
 *
 * Extracts component data from generated model DB and produces:
 * 1. Detailed BOM (all components with dimensions)
 * 2. Aggregated BOM (grouped by material, summed quantities)
 * 3. Cut List (linear members with lengths for fabrication)
 */
public class BOMExporter {

    private final String dbPath;
    private final String projectName;

    public BOMExporter(String dbPath, String projectName) {
        this.dbPath = dbPath;
        this.projectName = projectName;
    }

    /**
     * Export all BOM formats to specified output directory.
     */
    public void exportAll(String outputDir) throws Exception {
        new File(outputDir).mkdirs();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            exportDetailedBOM(conn, outputDir + "/" + projectName + "_bom_detailed.csv");
            exportAggregatedBOM(conn, outputDir + "/" + projectName + "_bom_aggregated.csv");
            exportCutList(conn, outputDir + "/" + projectName + "_cut_list.csv");
            exportAssemblyBOM(conn, outputDir + "/" + projectName + "_bom_assemblies.csv");
        }
    }

    /**
     * Detailed BOM: Every component with full dimensions.
     * Format: iDempiere Product Import compatible
     */
    private void exportDetailedBOM(Connection conn, String path) throws Exception {
        String sql = """
            SELECT m.guid, m.element_name, m.ifc_class, m.element_type,
                   r.maxX - r.minX as width,
                   r.maxY - r.minY as depth,
                   r.maxZ - r.minZ as height
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            WHERE m.ifc_class NOT IN ('IfcElementAssembly')
            ORDER BY m.ifc_class, m.element_type, m.element_name
        """;

        try (PrintWriter pw = new PrintWriter(new FileWriter(path));
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            // iDempiere Import Header
            pw.println("ProductCode,Name,Description,IFCClass,Type,Width_mm,Depth_mm,Height_mm,Qty,UOM");

            while (rs.next()) {
                String guid = rs.getString("guid");
                String name = rs.getString("element_name");
                String ifcClass = rs.getString("ifc_class");
                String type = rs.getString("element_type");
                double width = rs.getDouble("width") * 1000;  // m to mm
                double depth = rs.getDouble("depth") * 1000;
                double height = rs.getDouble("height") * 1000;

                String uom = determineUOM(ifcClass, width, depth, height);
                String desc = formatDescription(ifcClass, type, width, depth, height);

                pw.printf("%s,%s,\"%s\",%s,%s,%.0f,%.0f,%.0f,1,%s%n",
                    guid, name, desc, ifcClass, type, width, depth, height, uom);
            }
        }

        System.out.println("  Exported: " + path);
    }

    /**
     * Aggregated BOM: Components grouped by material/size, quantities summed.
     * This is what purchasing needs.
     */
    private void exportAggregatedBOM(Connection conn, String path) throws Exception {
        String sql = """
            SELECT m.element_name, m.ifc_class, m.element_type,
                   ROUND(r.maxX - r.minX, 3) as width,
                   ROUND(r.maxY - r.minY, 3) as depth,
                   ROUND(r.maxZ - r.minZ, 3) as height,
                   COUNT(*) as qty
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            WHERE m.ifc_class NOT IN ('IfcElementAssembly')
            GROUP BY m.element_name, m.ifc_class, m.element_type,
                     ROUND(r.maxX - r.minX, 3),
                     ROUND(r.maxY - r.minY, 3),
                     ROUND(r.maxZ - r.minZ, 3)
            ORDER BY m.ifc_class, m.element_name
        """;

        try (PrintWriter pw = new PrintWriter(new FileWriter(path));
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            pw.println("Material,IFCClass,Type,Size_mm,Qty,UOM,TotalLength_m,TotalArea_m2");

            while (rs.next()) {
                String name = rs.getString("element_name");
                String ifcClass = rs.getString("ifc_class");
                String type = rs.getString("element_type");
                double width = rs.getDouble("width");
                double depth = rs.getDouble("depth");
                double height = rs.getDouble("height");
                int qty = rs.getInt("qty");

                String size = formatSize(width * 1000, depth * 1000, height * 1000);
                String uom = determineUOM(ifcClass, width * 1000, depth * 1000, height * 1000);

                // Calculate totals based on component type
                double totalLength = 0;
                double totalArea = 0;

                if (ifcClass.equals("IfcMember")) {
                    // Linear member - length is the longest dimension
                    double length = Math.max(Math.max(width, depth), height);
                    totalLength = length * qty;
                } else if (ifcClass.equals("IfcPlate") || ifcClass.equals("IfcSlab")) {
                    // Sheet/slab - area calculation
                    // Area is the two largest dimensions
                    double[] dims = {width, depth, height};
                    Arrays.sort(dims);
                    totalArea = dims[1] * dims[2] * qty;
                }

                pw.printf("%s,%s,%s,%s,%d,%s,%.3f,%.3f%n",
                    name, ifcClass, type, size, qty, uom, totalLength, totalArea);
            }
        }

        System.out.println("  Exported: " + path);
    }

    /**
     * Cut List: Linear members only, for fabrication shop.
     * Shows member profile, cut length, and quantity.
     */
    private void exportCutList(Connection conn, String path) throws Exception {
        String sql = """
            SELECT m.element_name as profile, m.element_type,
                   CASE
                       WHEN (r.maxX - r.minX) >= (r.maxY - r.minY)
                        AND (r.maxX - r.minX) >= (r.maxZ - r.minZ)
                       THEN r.maxX - r.minX
                       WHEN (r.maxY - r.minY) >= (r.maxZ - r.minZ)
                       THEN r.maxY - r.minY
                       ELSE r.maxZ - r.minZ
                   END as cut_length,
                   COUNT(*) as qty
            FROM elements_meta m
            JOIN elements_rtree r ON m.id = r.id
            WHERE m.ifc_class = 'IfcMember'
            GROUP BY m.element_name, m.element_type,
                     CASE
                         WHEN (r.maxX - r.minX) >= (r.maxY - r.minY)
                          AND (r.maxX - r.minX) >= (r.maxZ - r.minZ)
                         THEN r.maxX - r.minX
                         WHEN (r.maxY - r.minY) >= (r.maxZ - r.minZ)
                         THEN r.maxY - r.minY
                         ELSE r.maxZ - r.minZ
                     END
            ORDER BY m.element_name, cut_length DESC
        """;

        try (PrintWriter pw = new PrintWriter(new FileWriter(path));
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            pw.println("Profile,Type,CutLength_mm,Qty,TotalLength_m,StockLength_6m,Waste_%");

            double grandTotalLength = 0;
            int grandTotalPieces = 0;

            while (rs.next()) {
                String profile = rs.getString("profile");
                String type = rs.getString("element_type");
                double cutLength = rs.getDouble("cut_length");
                int qty = rs.getInt("qty");

                double cutLengthMm = cutLength * 1000;
                double totalLength = cutLength * qty;
                grandTotalLength += totalLength;
                grandTotalPieces += qty;

                // Calculate stock requirements (assuming 6m standard lengths)
                double stockLength = 6.0;
                int piecesPerStock = (int) Math.floor(stockLength / cutLength);
                int stockRequired = (int) Math.ceil((double) qty / piecesPerStock);
                double wastePercent = ((stockRequired * stockLength - totalLength) / (stockRequired * stockLength)) * 100;

                pw.printf("%s,%s,%.0f,%d,%.3f,%d,%.1f%n",
                    profile, type, cutLengthMm, qty, totalLength, stockRequired, wastePercent);
            }

            // Summary row
            pw.println();
            pw.printf("TOTAL,,%d pieces,%.3f m total length%n", grandTotalPieces, grandTotalLength);
        }

        System.out.println("  Exported: " + path);
    }

    /**
     * Assembly BOM: Hierarchical view showing assemblies and their components.
     * For manufacturing/prefab workflow.
     */
    private void exportAssemblyBOM(Connection conn, String path) throws Exception {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {

            pw.println("AssemblyName,AssemblyType,AssemblySize_mm,ComponentGUID,ComponentRole,Sequence");

            String assemblySql = """
                SELECT a.assembly_guid, a.name, a.assembly_type,
                       a.total_width, a.total_depth, a.total_height
                FROM element_assemblies a
                ORDER BY a.name
            """;

            String componentSql = """
                SELECT c.component_guid, c.role, c.sequence
                FROM assembly_components c
                WHERE c.assembly_guid = ?
                ORDER BY c.sequence
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet assemblies = stmt.executeQuery(assemblySql);
                 PreparedStatement compStmt = conn.prepareStatement(componentSql)) {

                while (assemblies.next()) {
                    String assemblyGuid = assemblies.getString("assembly_guid");
                    String name = assemblies.getString("name");
                    String type = assemblies.getString("assembly_type");
                    double w = assemblies.getDouble("total_width") * 1000;
                    double d = assemblies.getDouble("total_depth") * 1000;
                    double h = assemblies.getDouble("total_height") * 1000;

                    String size = String.format("%.0fx%.0fx%.0f", w, d, h);

                    // Get components
                    compStmt.setString(1, assemblyGuid);
                    try (ResultSet components = compStmt.executeQuery()) {
                        boolean first = true;
                        while (components.next()) {
                            String compGuid = components.getString("component_guid");
                            String role = components.getString("role");
                            int seq = components.getInt("sequence");

                            if (first) {
                                pw.printf("%s,%s,%s,%s,%s,%d%n",
                                    name, type, size, compGuid, role, seq);
                                first = false;
                            } else {
                                pw.printf(",,,,%s,%s,%d%n", compGuid, role, seq);
                            }
                        }
                    }
                }
            }
        }

        System.out.println("  Exported: " + path);
    }

    private String determineUOM(String ifcClass, double w, double d, double h) {
        return switch (ifcClass) {
            case "IfcMember" -> "EA";      // Each (linear cut to length)
            case "IfcPlate" -> "M2";       // Square meters
            case "IfcSlab" -> "M3";        // Cubic meters (concrete)
            case "IfcRoof" -> "M2";        // Square meters
            default -> "EA";
        };
    }

    private String formatDescription(String ifcClass, String type, double w, double d, double h) {
        return switch (ifcClass) {
            case "IfcMember" -> String.format("Steel frame member %.0fx%.0f L=%.0fmm",
                Math.min(w, d), Math.max(Math.min(w, d), Math.min(d, h)), Math.max(Math.max(w, d), h));
            case "IfcPlate" -> String.format("Sheet cladding %.0fx%.0fmm",
                Math.max(w, d), Math.max(Math.max(w, d), h));
            case "IfcSlab" -> String.format("Concrete slab %.0fx%.0fx%.0fmm", w, d, h);
            case "IfcRoof" -> String.format("Roof panel %.0fx%.0fmm", w, d);
            default -> type;
        };
    }

    private String formatSize(double w, double d, double h) {
        double[] dims = {w, d, h};
        Arrays.sort(dims);
        return String.format("%.0fx%.0fx%.0f", dims[0], dims[1], dims[2]);
    }

    // =========================================================================
    // Test
    // =========================================================================

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("BOM EXPORT TEST");
        System.out.println("=".repeat(60));

        String dbPath = args.length > 0 ? args[0] : "output/sekolah_kebangsaan.db";
        String projectName = args.length > 1 ? args[1] : "sekolah_kebangsaan";

        if (!new File(dbPath).exists()) {
            System.out.println("ERROR: " + dbPath + " not found. Run ShedTest first.");
            System.exit(1);
        }

        String outputDir = args.length > 2 ? args[2] : "output/bom";
        BOMExporter exporter = new BOMExporter(dbPath, projectName);
        exporter.exportAll(outputDir);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("BOM EXPORT COMPLETE");
        System.out.println("=".repeat(60));

        // Display sample output
        System.out.println("\nSample from aggregated BOM:");
        System.out.println("-".repeat(60));

        try (BufferedReader br = new BufferedReader(new FileReader(outputDir + "/" + projectName + "_bom_aggregated.csv"))) {
            String line;
            int count = 0;
            while ((line = br.readLine()) != null && count < 10) {
                System.out.println(line);
                count++;
            }
        }

        System.out.println("\nSample from cut list:");
        System.out.println("-".repeat(60));

        try (BufferedReader br = new BufferedReader(new FileReader(outputDir + "/" + projectName + "_cut_list.csv"))) {
            String line;
            int count = 0;
            while ((line = br.readLine()) != null && count < 10) {
                System.out.println(line);
                count++;
            }
        }
    }
}
