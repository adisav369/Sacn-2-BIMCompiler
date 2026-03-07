package com.bim.compiler.dsl;

import java.sql.*;
import java.util.*;

/**
 * Phase B1: Metadata-driven element placement resolver.
 * Reads from m_bom_line in BOM.db (EXTRACTED BOMs with instance columns).
 * Each row = one element to emit at extracted reference coordinates.
 *
 * <p>P0.2: Switched from component_library.db to BOM.db.
 * m_bom_line now carries storey, element_ref, ordinal, orientation,
 * material_name, material_rgba — the 6 instance-specific columns backfilled
 * from IFC extraction archive. AABB is reconstructed: dx ± allocated_width_mm/2000.
 *
 * <p>Pattern: same lazy-load + cache as SlabSpecAD, FloorTypeAD.
 * The metadata IS the production list — compose functions read positions
 * from here instead of computing them from grid/solver.
 */
public class PlacementAD {

    public record Placement(
        String buildingType,
        String storey,
        String ifcClass,
        String elementRef,
        int ordinal,
        double minX, double maxX,
        double minY, double maxY,
        double minZ, double maxZ,
        String orientation,
        String discipline,
        String materialName,
        String materialRgba,
        String familyRef,
        String productId
    ) {
        double cx() { return (minX + maxX) / 2; }
        double cy() { return (minY + maxY) / 2; }
        double cz() { return (minZ + maxZ) / 2; }
        double dx() { return maxX - minX; }
        double dy() { return maxY - minY; }
        double dz() { return maxZ - minZ; }
    }

    // Cache: "building_type" → list of all placements
    private final Map<String, List<Placement>> cache = new HashMap<>();
    private boolean loaded = false;

    // Phase BOM-2c: Consumption registry — elements processed by StoreyCompiler.applyPlacementOverrides
    // are RELATIONAL source; emitGlobalPlacementElements (FLAT source) must skip them.
    // Key: buildingType + NUL + elementRef. Replaces the perStoreyClasses storey-name guard
    // which was an accident of naming mismatch, not an explicit design contract.
    // [EXTRACTED: Phase BOM-2c FLAT→RELATIONAL source contract]
    private final Set<String> consumed = new HashSet<>();

    /** Mark an element as consumed (processed) by StoreyCompiler.applyPlacementOverrides. */
    void markConsumed(String buildingType, String elementRef) {
        consumed.add(buildingType + "\u0000" + elementRef);
    }

    /** True if this element was consumed by the compiled (RELATIONAL) path and must not be re-emitted. */
    boolean isConsumed(String buildingType, String elementRef) {
        return consumed.contains(buildingType + "\u0000" + elementRef);
    }

    /**
     * Check if placement metadata exists for a building type.
     */
    boolean hasPlacement(String buildingType) {
        if (!loaded) load();
        return cache.containsKey(buildingType);
    }

    /**
     * Get all placements for a building type, storey, and IFC class.
     * Returns empty list if no metadata exists.
     */
    List<Placement> get(String buildingType, String storey, String ifcClass) {
        if (!loaded) load();
        List<Placement> all = cache.getOrDefault(buildingType, List.of());
        List<Placement> result = new ArrayList<>();
        for (Placement p : all) {
            if (p.storey().equals(storey) && p.ifcClass().equals(ifcClass)) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * Get all placements for a building type and storey (all classes).
     */
    List<Placement> getForStorey(String buildingType, String storey) {
        if (!loaded) load();
        List<Placement> all = cache.getOrDefault(buildingType, List.of());
        List<Placement> result = new ArrayList<>();
        for (Placement p : all) {
            if (p.storey().equals(storey)) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * Get all placements for a building type (all storeys, all classes).
     */
    public List<Placement> getAll(String buildingType) {
        if (!loaded) load();
        return cache.getOrDefault(buildingType, List.of());
    }

    private void load() {
        loaded = true;
        loadFromBOM();
    }

    // ── Discipline derivation ────────────────────────────────────────────
    // Static map: IFC class → discipline (STR, MEP, ARC).
    // Same classification used by IFC extraction discipline column.
    private static String deriveDiscipline(String ifcClass) {
        if (ifcClass == null) return "ARC";
        return switch (ifcClass) {
            case "IfcBeam", "IfcMember", "IfcPlate", "IfcSlab",
                 "IfcStairFlight", "IfcRailing" -> "STR";
            case "IfcFlowController", "IfcFlowFitting",
                 "IfcFlowSegment", "IfcFlowTerminal" -> "MEP";
            default -> "ARC";
        };
    }

    /**
     * Load all active EXTRACTED placements from m_bom_line in BOM.db.
     * AABB reconstructed: dx ± allocated_width_mm/2000.0 = minX/maxX.
     * Building type resolved via m_bom → C_DocType.ProjectName.
     */
    private void loadFromBOM() {
        String sql = """
            SELECT dt.ProjectName as building_type,
                   bl.storey,
                   bl.role as ifc_class,
                   bl.element_ref,
                   bl.ordinal,
                   (COALESCE(b.origin_x, 0) + bl.dx - bl.allocated_width_mm / 2000.0) as min_x,
                   (COALESCE(b.origin_x, 0) + bl.dx + bl.allocated_width_mm / 2000.0) as max_x,
                   (COALESCE(b.origin_y, 0) + bl.dy - bl.allocated_depth_mm / 2000.0) as min_y,
                   (COALESCE(b.origin_y, 0) + bl.dy + bl.allocated_depth_mm / 2000.0) as max_y,
                   (COALESCE(b.origin_z, 0) + bl.dz - bl.allocated_height_mm / 2000.0) as min_z,
                   (COALESCE(b.origin_z, 0) + bl.dz + bl.allocated_height_mm / 2000.0) as max_z,
                   bl.orientation,
                   bl.material_name,
                   bl.material_rgba,
                   bl.child_product_id
            FROM m_bom_line bl
            JOIN m_bom b ON bl.bom_id = b.bom_id
            JOIN C_DocType dt ON b.doc_sub_type = dt.DocSubType
            WHERE bl.is_active = 1
              AND b.bom_category = 'EXTRACTED'
              AND bl.storey IS NOT NULL
            ORDER BY dt.ProjectName, bl.storey, bl.role, bl.ordinal
            """;
        try (Connection conn = DriverManager.getConnection(
                "jdbc:sqlite:library/BOM.db");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String bt = rs.getString("building_type");
                String ifcClass = rs.getString("ifc_class");
                Placement p = new Placement(
                    bt,
                    rs.getString("storey"),
                    ifcClass,
                    rs.getString("element_ref"),
                    rs.getInt("ordinal"),
                    rs.getDouble("min_x"), rs.getDouble("max_x"),
                    rs.getDouble("min_y"), rs.getDouble("max_y"),
                    rs.getDouble("min_z"), rs.getDouble("max_z"),
                    rs.getString("orientation"),
                    deriveDiscipline(ifcClass),
                    rs.getString("material_name"),
                    rs.getString("material_rgba"),
                    null,  // familyRef — flat BOM has no family_ref
                    rs.getString("child_product_id")
                );
                cache.computeIfAbsent(p.buildingType(), k -> new ArrayList<>()).add(p);
            }
        } catch (SQLException e) {
            System.err.println("[PlacementAD] Failed to load placements from BOM: " + e.getMessage());
        }
    }

    // Singleton for shared use
    private static PlacementAD instance;
    public static PlacementAD getInstance() {
        if (instance == null) instance = new PlacementAD();
        return instance;
    }
    public static void resetInstance() { instance = null; }
}
