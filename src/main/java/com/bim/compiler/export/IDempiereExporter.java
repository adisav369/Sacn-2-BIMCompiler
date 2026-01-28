package com.bim.compiler.export;

import java.io.*;
import java.sql.*;
import java.util.*;

/**
 * iDempiere ERP Integration Exporter.
 *
 * Generates:
 * 1. Product mapping table (component → M_Product)
 * 2. idempiere_products.csv (for I_Product import)
 * 3. idempiere_bom.csv (for M_BOM import)
 */
public class IDempiereExporter {

    private final Connection conn;

    // Default product mappings (extracted from Terminal DB patterns)
    private static final Map<String, ProductMapping> PRODUCT_MAP = new HashMap<>();

    static {
        // Steel sections
        PRODUCT_MAP.put("RHS 150x100", new ProductMapping(
            "RHS-150x100x5", "RHS 150x100x5 Steel Section", "LM", "Steel", 14.4, 45.00
        ));
        PRODUCT_MAP.put("RHS 100x50", new ProductMapping(
            "RHS-100x50x4", "RHS 100x50x4 Steel Section", "LM", "Steel", 7.2, 28.00
        ));

        // Cladding
        PRODUCT_MAP.put("Metal Deck", new ProductMapping(
            "DECK-METAL-042", "Metal Deck Panel 0.42mm BMT", "M2", "Cladding", 4.5, 32.00
        ));

        // Concrete
        PRODUCT_MAP.put("Foundation Slab", new ProductMapping(
            "CONC-G30-M3", "Concrete Grade 30 Ready Mix", "M3", "Concrete", 2400.0, 380.00
        ));
        PRODUCT_MAP.put("Floor Slab", new ProductMapping(
            "CONC-G30-M3", "Concrete Grade 30 Ready Mix", "M3", "Concrete", 2400.0, 380.00
        ));
        PRODUCT_MAP.put("Floor Slab Level 1", new ProductMapping(
            "CONC-G30-M3", "Concrete Grade 30 Ready Mix", "M3", "Concrete", 2400.0, 380.00
        ));

        // Doors
        PRODUCT_MAP.put("Entry Door", new ProductMapping(
            "DOOR-900x2100", "Entry Door 900x2100mm", "EA", "Doors", 35.0, 850.00
        ));

        // Windows
        PRODUCT_MAP.put("Standard Window", new ProductMapping(
            "WINDOW-1200x1000", "Standard Window 1200x1000mm", "EA", "Windows", 25.0, 450.00
        ));

        // Landings
        PRODUCT_MAP.put("Stair Landing", new ProductMapping(
            "LANDING-CONC-150", "Concrete Stair Landing 150mm", "M2", "Concrete", 360.0, 180.00
        ));

        // Roofing
        PRODUCT_MAP.put("Gable Roof", new ProductMapping(
            "ROOF-TRIMDEK-042", "Trimdek Roof Sheet 0.42mm BMT", "M2", "Roofing", 4.5, 38.00
        ));

        // Stairs
        PRODUCT_MAP.put("Stair Flight", new ProductMapping(
            "STAIR-STEEL-STD", "Steel Stair Flight Assembly", "EA", "Stairs", 250.0, 2800.00
        ));

        // MEP - Sprinklers (Phase 14B)
        PRODUCT_MAP.put("Fire Sprinkler", new ProductMapping(
            "SPRINKLER-PENDANT", "Fire Sprinkler Pendant Head", "EA", "Fire Protection", 0.5, 85.00
        ));

        // MEP - Light Fixtures (Phase 14B)
        PRODUCT_MAP.put("Light Fixture", new ProductMapping(
            "LIGHT-RECESSED-LED", "LED Recessed Light Fixture 600x600", "EA", "Electrical", 3.5, 180.00
        ));

        // Default for unknown
        PRODUCT_MAP.put("DEFAULT", new ProductMapping(
            "MISC-ITEM", "Miscellaneous Item", "EA", "Misc", 1.0, 10.00
        ));
    }

    public IDempiereExporter(Connection conn) {
        this.conn = conn;
    }

    /**
     * Initialize iDempiere mapping tables.
     */
    public void initSchema() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS idempiere_bom_map");
            stmt.execute("DROP TABLE IF EXISTS idempiere_product_map");

            stmt.execute("""
                CREATE TABLE idempiere_product_map (
                    component_hash TEXT PRIMARY KEY,
                    ifc_class TEXT NOT NULL,
                    component_name TEXT NOT NULL,
                    m_product_value TEXT,
                    m_product_name TEXT,
                    c_uom_symbol TEXT,
                    productcategory TEXT,
                    unit_weight_kg REAL,
                    unit_cost REAL,
                    currency TEXT DEFAULT 'MYR'
                )
            """);

            stmt.execute("""
                CREATE TABLE idempiere_bom_map (
                    parent_assembly_type TEXT,
                    m_bom_name TEXT,
                    m_bom_description TEXT
                )
            """);
        }
    }

    /**
     * Populate product mapping from elements in database.
     */
    public void populateProductMap() throws SQLException {
        String sql = """
            SELECT DISTINCT m.guid, m.ifc_class, m.element_name, m.element_type
            FROM elements_meta m
            WHERE m.ifc_class NOT IN ('IfcElementAssembly')
        """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             PreparedStatement insert = conn.prepareStatement(
                 "INSERT OR REPLACE INTO idempiere_product_map VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
             )) {

            while (rs.next()) {
                String guid = rs.getString("guid");
                String ifcClass = rs.getString("ifc_class");
                String name = rs.getString("element_name");

                ProductMapping mapping = PRODUCT_MAP.getOrDefault(name, PRODUCT_MAP.get("DEFAULT"));

                insert.setString(1, guid);
                insert.setString(2, ifcClass);
                insert.setString(3, name);
                insert.setString(4, mapping.value);
                insert.setString(5, mapping.name);
                insert.setString(6, mapping.uom);
                insert.setString(7, mapping.category);
                insert.setDouble(8, mapping.weight);
                insert.setDouble(9, mapping.cost);
                insert.setString(10, "MYR");
                insert.execute();
            }
        }

        // Populate BOM mappings
        try (PreparedStatement insert = conn.prepareStatement(
            "INSERT INTO idempiere_bom_map VALUES (?, ?, ?)"
        )) {
            insert.setString(1, "WALL_PANEL");
            insert.setString(2, "BOM-WALL-PANEL");
            insert.setString(3, "Wall Panel Assembly - Steel Frame with Cladding");
            insert.execute();

            insert.setString(1, "STAIR_ASSEMBLY");
            insert.setString(2, "BOM-STAIR");
            insert.setString(3, "Stair Assembly - Steel Flight");
            insert.execute();
        }
    }

    /**
     * Export iDempiere product import CSV.
     */
    public void exportProductsCSV(String path) throws Exception {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            // iDempiere I_Product import header
            pw.println("Value,Name,UOM,ProductCategory,Weight,StandardCost,IsFinalDelivered");

            String sql = """
                SELECT DISTINCT m_product_value, m_product_name, c_uom_symbol,
                       productcategory, unit_weight_kg, unit_cost
                FROM idempiere_product_map
                ORDER BY productcategory, m_product_value
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    pw.printf("%s,%s,%s,%s,%.2f,%.2f,Y%n",
                        rs.getString("m_product_value"),
                        escapeCSV(rs.getString("m_product_name")),
                        rs.getString("c_uom_symbol"),
                        rs.getString("productcategory"),
                        rs.getDouble("unit_weight_kg"),
                        rs.getDouble("unit_cost")
                    );
                }
            }
        }
        System.out.println("  Exported: " + path);
    }

    /**
     * Export iDempiere BOM import CSV.
     */
    public void exportBOMCSV(String path) throws Exception {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            // iDempiere M_BOM import header
            pw.println("BOMName,BOMType,Product_Value,ComponentProduct_Value,QtyBOM,UOM");

            // Get assemblies with components
            String assemblySql = """
                SELECT a.assembly_guid, a.assembly_type, a.name,
                       a.total_width, a.total_height
                FROM element_assemblies a
            """;

            String componentSql = """
                SELECT c.component_guid, c.role, p.m_product_value, p.c_uom_symbol
                FROM assembly_components c
                JOIN idempiere_product_map p ON c.component_guid = p.component_hash
                WHERE c.assembly_guid = ?
                ORDER BY c.sequence
            """;

            try (Statement stmt = conn.createStatement();
                 ResultSet assemblies = stmt.executeQuery(assemblySql);
                 PreparedStatement compStmt = conn.prepareStatement(componentSql)) {

                while (assemblies.next()) {
                    String assemblyGuid = assemblies.getString("assembly_guid");
                    String assemblyType = assemblies.getString("assembly_type");
                    String assemblyName = assemblies.getString("name");

                    // Create BOM name from assembly
                    String bomName = "BOM-" + assemblyName.replace("_", "-");
                    String bomProduct = assemblyType + "-" + assemblyName;

                    compStmt.setString(1, assemblyGuid);
                    try (ResultSet components = compStmt.executeQuery()) {
                        while (components.next()) {
                            String productValue = components.getString("m_product_value");
                            String uom = components.getString("c_uom_symbol");

                            // Calculate quantity based on UOM
                            double qty = 1.0;
                            if (uom.equals("LM")) {
                                // Linear meter - calculate from assembly dimensions
                                qty = Math.max(assemblies.getDouble("total_width"),
                                    assemblies.getDouble("total_height"));
                            } else if (uom.equals("M2")) {
                                qty = assemblies.getDouble("total_width") *
                                    assemblies.getDouble("total_height");
                            }

                            pw.printf("%s,Make,%s,%s,%.2f,%s%n",
                                bomName, bomProduct, productValue, qty, uom);
                        }
                    }
                }
            }
        }
        System.out.println("  Exported: " + path);
    }

    /**
     * Export costed BOM with totals.
     */
    public void exportCostedBOM(String path) throws Exception {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("Component,IFCClass,Product,Qty,UOM,UnitCost,TotalCost,Currency");

            String sql = """
                SELECT m.element_name, m.ifc_class,
                       p.m_product_value, p.m_product_name, p.c_uom_symbol,
                       p.unit_cost,
                       r.maxX - r.minX as width,
                       r.maxY - r.minY as depth,
                       r.maxZ - r.minZ as height
                FROM elements_meta m
                JOIN idempiere_product_map p ON m.guid = p.component_hash
                JOIN elements_rtree r ON m.id = r.id
                WHERE m.ifc_class NOT IN ('IfcElementAssembly')
                ORDER BY m.ifc_class, m.element_name
            """;

            double grandTotal = 0;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String name = rs.getString("element_name");
                    String ifcClass = rs.getString("ifc_class");
                    String product = rs.getString("m_product_value");
                    String uom = rs.getString("c_uom_symbol");
                    double unitCost = rs.getDouble("unit_cost");
                    double width = rs.getDouble("width");
                    double depth = rs.getDouble("depth");
                    double height = rs.getDouble("height");

                    // Calculate quantity
                    double qty = switch (uom) {
                        case "LM" -> Math.max(Math.max(width, depth), height);
                        case "M2" -> {
                            double[] dims = {width, depth, height};
                            Arrays.sort(dims);
                            yield dims[1] * dims[2];  // Two largest
                        }
                        case "M3" -> width * depth * height;
                        default -> 1.0;
                    };

                    double total = qty * unitCost;
                    grandTotal += total;

                    pw.printf("%s,%s,%s,%.3f,%s,%.2f,%.2f,MYR%n",
                        name, ifcClass, product, qty, uom, unitCost, total);
                }
            }

            pw.println();
            pw.printf("GRAND TOTAL,,,,,,,%.2f,MYR%n", grandTotal);
        }
        System.out.println("  Exported: " + path);
    }

    /**
     * Export all iDempiere formats.
     */
    public void exportAll(String outputDir) throws Exception {
        new File(outputDir).mkdirs();

        initSchema();
        populateProductMap();

        exportProductsCSV(outputDir + "/idempiere_products.csv");
        exportBOMCSV(outputDir + "/idempiere_bom.csv");
        exportCostedBOM(outputDir + "/idempiere_costed_bom.csv");
    }

    private String escapeCSV(String s) {
        if (s.contains(",") || s.contains("\"")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // Product mapping record
    private record ProductMapping(
        String value,    // M_Product Value (SKU)
        String name,     // M_Product Name
        String uom,      // C_UOM symbol
        String category, // ProductCategory
        double weight,   // Unit weight in kg
        double cost      // Standard cost
    ) {}
}
