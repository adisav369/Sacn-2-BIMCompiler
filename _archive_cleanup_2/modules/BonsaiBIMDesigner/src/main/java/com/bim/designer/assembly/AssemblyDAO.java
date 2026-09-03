package com.bim.designer.assembly;

import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only DAO for assembly templates and material thermal data.
 * Reads component_library.db. Never writes.
 *
 * // Implementing ASSEMBLY_BUILDER_SRS.md §5.1 — Witness: W-ASM-DAO-1
 */
public class AssemblyDAO {

    private static final String TAG = "AssemblyDAO";

    private final Connection conn;

    public AssemblyDAO(Connection conn) {
        this.conn = conn;
    }

    // ── Records ──────────────────────────────────────────────────────

    /** Raw layer from material_layers table, thickness normalised to mm. */
    public record MaterialLayer(
            String layerSetName,
            int sequence,
            String materialName,
            double thicknessMm,
            boolean isVentilated
    ) {}

    /** An alternative material for a layer position. */
    public record AlternativeMaterial(
            String materialName,
            double conductivityWmK,
            String description
    ) {}

    // ── Queries ──────────────────────────────────────────────────────

    /**
     * Load all distinct layer set names for a category prefix.
     * @param categoryPrefix "Basic Wall" / "Basic Roof" / "Floor" / "Compound Ceiling"
     */
    public List<String> listLayerSets(String categoryPrefix) throws SQLException {
        String sql = "SELECT DISTINCT layer_set_name FROM material_layers "
                   + "WHERE layer_set_name LIKE ? ORDER BY layer_set_name";
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, categoryPrefix + ":%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            }
        }
        BIMLogger.info(TAG, "listLayerSets('{}') → {} templates", categoryPrefix, result.size());
        return result;
    }

    /**
     * Load full layer stack for a layer set, normalising thickness to mm.
     * Uses magnitude check: if MAX(thickness_m) < 1.0, values are in metres.
     */
    public List<MaterialLayer> loadLayers(String layerSetName) throws SQLException {
        String sql = "SELECT sequence, material_name, thickness_m, is_ventilated "
                   + "FROM material_layers WHERE layer_set_name = ? ORDER BY sequence";
        List<RawLayer> raw = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, layerSetName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    raw.add(new RawLayer(
                            rs.getInt("sequence"),
                            rs.getString("material_name"),
                            rs.getDouble("thickness_m"),
                            rs.getInt("is_ventilated") == 1
                    ));
                }
            }
        }

        if (raw.isEmpty()) return List.of();

        // Magnitude check: if max value < 1.0, data is in metres → multiply by 1000
        double maxThickness = raw.stream()
                .mapToDouble(r -> r.thickness)
                .max().orElse(0);
        boolean isMetres = maxThickness < 1.0;

        List<MaterialLayer> result = new ArrayList<>(raw.size());
        for (RawLayer r : raw) {
            double mm = isMetres ? r.thickness * 1000.0 : r.thickness;
            result.add(new MaterialLayer(layerSetName, r.sequence, r.materialName, mm, r.isVentilated));
        }
        return result;
    }

    private record RawLayer(int sequence, String materialName, double thickness, boolean isVentilated) {}

    /**
     * Load all thermal properties as a map.
     * @return material_name → conductivity_w_mk
     */
    public Map<String, Double> loadAllThermalProperties() throws SQLException {
        String sql = "SELECT material_name, conductivity_w_mk FROM ad_material_thermal";
        Map<String, Double> result = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString(1), rs.getDouble(2));
            }
        }
        return result;
    }

    /**
     * Load thermal conductivity for a single material.
     * @return conductivity in W/m·K, or null if unknown
     */
    public Double getThermalConductivity(String materialName) throws SQLException {
        String sql = "SELECT conductivity_w_mk FROM ad_material_thermal WHERE material_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, materialName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : null;
            }
        }
    }

    /**
     * Browse alternative materials. Phase 1: returns all materials from
     * ad_material_thermal (no role filtering yet — advisory only).
     */
    public List<AlternativeMaterial> browseAlternatives(String currentMaterial, String role)
            throws SQLException {
        String sql = "SELECT material_name, conductivity_w_mk, description "
                   + "FROM ad_material_thermal WHERE material_name != ? "
                   + "ORDER BY conductivity_w_mk";
        List<AlternativeMaterial> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currentMaterial);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new AlternativeMaterial(
                            rs.getString(1), rs.getDouble(2), rs.getString(3)));
                }
            }
        }
        return result;
    }
}
