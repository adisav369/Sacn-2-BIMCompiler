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
 * DAO for 7D Facility Management — maintenance schedule + lifecycle cost
 * from BOM × M_Product attributes.
 *
 * <p>Same split-DB join pattern as {@link SustainabilityDAO}: load product
 * attributes from component_library.db, iterate BOM lines, compute rollups.
 *
 * // Implementing TIER1_SRS.md §2.3 — Witness: W-7D-FM-1
 */
public class FacilityMgmtDAO {

    private static final String TAG = "FacilityMgmtDAO";

    // ── Record types ────────────────────────────────────────────────

    public record MaintenanceItem(String bomId, String productName,
                                  String location, String discipline,
                                  int intervalMonths, int lifespanYears,
                                  int qtyInBuilding) {}

    public record MaintenanceSchedule(String buildingId,
                                      List<MaintenanceItem> items,
                                      int totalAssets,
                                      Map<String, Integer> byInterval,
                                      double annualMaintenanceEvents) {}

    public record LifecycleItem(String productName, String material,
                                int lifespanYears, int qty,
                                double replacementCostUnit,
                                double totalReplacementCost) {}

    public record LifecycleSummary(String buildingId,
                                   List<LifecycleItem> items,
                                   double totalReplacementCost,
                                   Map<Integer, Double> costByDecade) {}

    public record AssetRecord(String guid, String type, String location,
                              String floor, String system, String manufacturer,
                              int intervalMonths) {}

    // ── Queries ─────────────────────────────────────────────────────

    /**
     * Generate maintenance schedule for a compiled building.
     * Groups by product × discipline, computes annual events.
     */
    public MaintenanceSchedule maintenanceSchedule(Connection bomConn,
                                                    Connection compLibConn,
                                                    String buildingId) throws SQLException {
        Map<String, ProductFM> fmMap = loadFMMap(compLibConn);

        String sql = """
                SELECT bl.child_product_id, bl.qty, mpc.Value AS m_product_category_id, b.bom_name
                FROM m_bom_line bl
                JOIN m_bom b ON bl.M_BOM_ID = b.M_BOM_ID
                LEFT JOIN M_Product_Category mpc ON b.m_product_category_id = mpc.M_Product_Category_ID
                WHERE bl.child_product_id IS NOT NULL AND bl.is_active = 1
                """;

        List<MaintenanceItem> items = new ArrayList<>();
        Map<String, Integer> byInterval = new LinkedHashMap<>();
        int totalAssets = 0;
        double annualEvents = 0;

        try (PreparedStatement ps = bomConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String productId = rs.getString("child_product_id");
                int qty = rs.getInt("qty");
                String discipline = rs.getString("m_product_category_id");
                String location = rs.getString("bom_name");
                if (discipline == null) discipline = "UNKNOWN";

                ProductFM fm = fmMap.get(productId);
                if (fm == null || fm.intervalMonths <= 0) continue;

                totalAssets += qty;
                double events = (12.0 / fm.intervalMonths) * qty;
                annualEvents += events;

                items.add(new MaintenanceItem(
                        productId, productId, location, discipline,
                        fm.intervalMonths, fm.lifespanYears, qty));

                String key = fm.intervalMonths + "mo";
                byInterval.merge(key, qty, Integer::sum);
            }
        }

        BIMLogger.info(TAG, "Maintenance schedule: {} assets, {:.0f} annual events",
                totalAssets, annualEvents);

        return new MaintenanceSchedule(buildingId, items, totalAssets,
                byInterval, annualEvents);
    }

    /**
     * Lifecycle cost projection over a given horizon.
     * Groups replacement cost by decade.
     */
    public LifecycleSummary lifecycleCost(Connection bomConn,
                                           Connection compLibConn,
                                           String buildingId,
                                           int horizonYears) throws SQLException {
        Map<String, ProductFM> fmMap = loadFMMap(compLibConn);

        String sql = """
                SELECT bl.child_product_id, bl.qty
                FROM m_bom_line bl
                WHERE bl.child_product_id IS NOT NULL AND bl.is_active = 1
                """;

        List<LifecycleItem> items = new ArrayList<>();
        Map<Integer, Double> costByDecade = new LinkedHashMap<>();
        double totalReplacementCost = 0;

        // Initialize decades
        for (int decade = 10; decade <= horizonYears; decade += 10) {
            costByDecade.put(decade, 0.0);
        }

        try (PreparedStatement ps = bomConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String productId = rs.getString("child_product_id");
                int qty = rs.getInt("qty");

                ProductFM fm = fmMap.get(productId);
                if (fm == null || fm.lifespanYears <= 0) continue;

                // How many replacements within the horizon?
                int replacements = (horizonYears / fm.lifespanYears);
                if (replacements < 1 && fm.lifespanYears < horizonYears) replacements = 1;
                // Use carbon as rough cost proxy (carbon_kg_per_unit from sister DAO)
                // In production, M_Product would have a cost column
                double unitCost = fm.carbonPerUnit;  // placeholder: use carbon as proxy
                double totalCost = unitCost * qty * replacements;
                totalReplacementCost += totalCost;

                items.add(new LifecycleItem(
                        productId, fm.productType, fm.lifespanYears,
                        qty, unitCost, totalCost));

                // Assign to decade buckets
                for (int yr = fm.lifespanYears; yr <= horizonYears; yr += fm.lifespanYears) {
                    int decade = ((yr - 1) / 10 + 1) * 10;
                    if (decade > horizonYears) decade = horizonYears;
                    costByDecade.merge(decade, unitCost * qty, Double::sum);
                }
            }
        }

        return new LifecycleSummary(buildingId, items, totalReplacementCost, costByDecade);
    }

    /**
     * Asset register — COBie-compatible list of maintainable items.
     */
    public List<AssetRecord> assetRegister(Connection bomConn,
                                            Connection compLibConn,
                                            String buildingId) throws SQLException {
        Map<String, ProductFM> fmMap = loadFMMap(compLibConn);

        String sql = """
                SELECT bl.child_product_id, bl.bom_id, bl.storey,
                       mpc.Value AS m_product_category_id, b.bom_name
                FROM m_bom_line bl
                JOIN m_bom b ON bl.M_BOM_ID = b.M_BOM_ID
                LEFT JOIN M_Product_Category mpc ON b.m_product_category_id = mpc.M_Product_Category_ID
                WHERE bl.child_product_id IS NOT NULL AND bl.is_active = 1
                ORDER BY bl.storey, mpc.Value
                """;

        List<AssetRecord> records = new ArrayList<>();
        try (PreparedStatement ps = bomConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String productId = rs.getString("child_product_id");
                String bomId = rs.getString("bom_id");
                String storey = rs.getString("storey");
                String discipline = rs.getString("m_product_category_id");
                String location = rs.getString("bom_name");

                ProductFM fm = fmMap.get(productId);
                int interval = fm != null ? fm.intervalMonths : 0;

                records.add(new AssetRecord(
                        productId, // guid placeholder
                        fm != null ? fm.productType : "UNKNOWN",
                        location != null ? location : bomId,
                        storey != null ? storey : "GF",
                        discipline != null ? discipline : "UNKNOWN",
                        null, // manufacturer not yet in schema
                        interval));
            }
        }
        return records;
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private record ProductFM(String productType, int intervalMonths,
                             int lifespanYears, double carbonPerUnit) {}

    private Map<String, ProductFM> loadFMMap(Connection compLibConn) throws SQLException {
        Map<String, ProductFM> map = new LinkedHashMap<>();
        String sql = """
                SELECT Value, product_type, maintenance_interval_months,
                       lifespan_years, carbon_kg_per_unit
                FROM M_Product
                WHERE is_active = 1
                """;
        try (PreparedStatement ps = compLibConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("Value"),
                        new ProductFM(
                                rs.getString("product_type"),
                                rs.getInt("maintenance_interval_months"),
                                rs.getInt("lifespan_years"),
                                rs.getDouble("carbon_kg_per_unit")));
            }
        }
        return map;
    }
}
