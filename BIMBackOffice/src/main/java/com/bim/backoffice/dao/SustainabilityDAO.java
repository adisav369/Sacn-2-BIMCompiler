package com.bim.backoffice.dao;

import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO for 6D Sustainability — embodied carbon rollup from BOM × M_Product.
 *
 * <p>Joins m_bom_line (qty) × M_Product (carbon_kg_per_unit) across the
 * split-DB architecture: BOM.db has the BOM tree, component_library.db has
 * the product attributes.
 *
 * <p>Since SQLite cannot JOIN across databases in JDBC, this DAO reads
 * product carbon data into memory (small: ~700 products) then iterates
 * BOM lines to compute rollups.
 *
 * // Implementing TIER1_SRS.md §1.3 — Witness: W-6D-CARBON-1
 */
public class SustainabilityDAO {

    private static final String TAG = "SustainabilityDAO";

    // ── Record types ────────────────────────────────────────────────

    public record CarbonLine(String bomId, String productName, String material,
                             int qty, double carbonPerUnit, double totalCarbon,
                             String recyclability, String eolStrategy) {}

    public record CarbonSummary(double totalCarbonKg, double carbonPerSqM,
                                double grossFloorAreaSqM,
                                List<CarbonLine> lines,
                                Map<String, Double> byDiscipline,
                                Map<String, Double> byMaterial) {}

    public record MaterialPassportLine(String material, double totalMassKg,
                                       double totalCarbonKg, String avgRecyclability,
                                       int productCount) {}

    // ── Queries ─────────────────────────────────────────────────────

    /**
     * Rollup embodied carbon for a building.
     * Two-phase: load product carbon map from compLib, then iterate BOM lines.
     */
    public CarbonSummary carbonFootprint(Connection bomConn, Connection compLibConn,
                                          String buildingId) throws SQLException {
        // Phase 1: load carbon data from component_library.db
        Map<String, ProductCarbon> carbonMap = loadCarbonMap(compLibConn);

        // Phase 2: iterate BOM lines, join in memory
        String sql = """
                SELECT bl.bom_id, bl.child_product_id, bl.qty,
                       b.m_product_category_id
                FROM m_bom_line bl
                JOIN m_bom b ON bl.M_BOM_ID = b.M_BOM_ID
                WHERE bl.child_product_id IS NOT NULL
                  AND bl.is_active = 1
                ORDER BY bl.bom_child_id
                """;

        List<CarbonLine> lines = new ArrayList<>();
        Map<String, Double> byDiscipline = new LinkedHashMap<>();
        Map<String, Double> byMaterial = new LinkedHashMap<>();
        double totalCarbon = 0;

        try (PreparedStatement ps = bomConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String bomLineId = rs.getString("bom_id");
                String productId = rs.getString("child_product_id");
                int qty = rs.getInt("qty");
                String discipline = rs.getString("m_product_category_id");
                if (discipline == null) discipline = "UNKNOWN";

                ProductCarbon pc = carbonMap.get(productId);
                if (pc == null) continue;

                double lineCarbon = qty * pc.carbonPerUnit;
                totalCarbon += lineCarbon;

                lines.add(new CarbonLine(
                        bomLineId, productId, pc.productType,
                        qty, pc.carbonPerUnit, lineCarbon,
                        pc.recyclability, pc.eolStrategy));

                byDiscipline.merge(discipline, lineCarbon, Double::sum);
                byMaterial.merge(pc.productType, lineCarbon, Double::sum);
            }
        }

        // Compute gross floor area from FLOOR-type BOMs
        double gfa = computeFloorArea(bomConn);
        double carbonPerSqM = gfa > 0 ? totalCarbon / gfa : 0;

        BIMLogger.info(TAG, "Carbon footprint: {:.1f} kgCO2e, {:.1f} kgCO2e/m², {} lines",
                totalCarbon, carbonPerSqM, lines.size());

        return new CarbonSummary(totalCarbon, carbonPerSqM, gfa,
                lines, byDiscipline, byMaterial);
    }

    /**
     * Single-element carbon lookup for the properties popup.
     */
    public CarbonLine elementCarbon(Connection compLibConn, String productId) throws SQLException {
        String sql = """
                SELECT Value, product_type, carbon_kg_per_unit,
                       recyclability, eol_strategy
                FROM M_Product WHERE Value = ?
                """;
        try (PreparedStatement ps = compLibConn.prepareStatement(sql)) {
            ps.setString(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new CarbonLine(
                            productId, rs.getString("Value"),
                            rs.getString("product_type"),
                            1, rs.getDouble("carbon_kg_per_unit"),
                            rs.getDouble("carbon_kg_per_unit"),
                            rs.getString("recyclability"),
                            rs.getString("eol_strategy"));
                }
            }
        }
        return null;
    }

    /**
     * Material passport — circular economy inventory grouped by material type.
     */
    public List<MaterialPassportLine> materialPassport(Connection bomConn,
                                                        Connection compLibConn) throws SQLException {
        Map<String, ProductCarbon> carbonMap = loadCarbonMap(compLibConn);

        String sql = """
                SELECT bl.child_product_id, bl.qty
                FROM m_bom_line bl
                WHERE bl.child_product_id IS NOT NULL AND bl.is_active = 1
                """;

        // Aggregate by material type
        Map<String, double[]> agg = new LinkedHashMap<>();  // [totalCarbon, count]
        Map<String, String> recyclabilityMap = new LinkedHashMap<>();

        try (PreparedStatement ps = bomConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String productId = rs.getString("child_product_id");
                int qty = rs.getInt("qty");
                ProductCarbon pc = carbonMap.get(productId);
                if (pc == null) continue;

                double lineCarbon = qty * pc.carbonPerUnit;
                agg.computeIfAbsent(pc.productType, k -> new double[2]);
                agg.get(pc.productType)[0] += lineCarbon;
                agg.get(pc.productType)[1] += qty;
                recyclabilityMap.putIfAbsent(pc.productType, pc.recyclability);
            }
        }

        List<MaterialPassportLine> result = new ArrayList<>();
        for (var entry : agg.entrySet()) {
            result.add(new MaterialPassportLine(
                    entry.getKey(),
                    0, // mass not tracked yet (would need density × volume)
                    entry.getValue()[0],
                    recyclabilityMap.get(entry.getKey()),
                    (int) entry.getValue()[1]));
        }
        return result;
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private record ProductCarbon(String productType, double carbonPerUnit,
                                 String recyclability, String eolStrategy) {}

    private Map<String, ProductCarbon> loadCarbonMap(Connection compLibConn) throws SQLException {
        Map<String, ProductCarbon> map = new LinkedHashMap<>();
        String sql = """
                SELECT Value, product_type, carbon_kg_per_unit,
                       recyclability, eol_strategy
                FROM M_Product
                WHERE carbon_kg_per_unit > 0 AND is_active = 1
                """;
        try (PreparedStatement ps = compLibConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("Value"),
                        new ProductCarbon(
                                rs.getString("product_type"),
                                rs.getDouble("carbon_kg_per_unit"),
                                rs.getString("recyclability"),
                                rs.getString("eol_strategy")));
            }
        }
        return map;
    }

    private double computeFloorArea(Connection bomConn) throws SQLException {
        // Sum floor AABB areas (width × depth in mm → m²)
        String sql = """
                SELECT SUM(aabb_width_mm * aabb_depth_mm / 1000000.0)
                FROM m_bom
                WHERE bom_type = 'FLOOR' AND is_active = 1
                """;
        try (PreparedStatement ps = bomConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getDouble(1);
        }
        return 0;
    }
}
