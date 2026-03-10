package com.bim.compiler.dsl;

import com.bim.compiler.bom.walker.BOMWalker;
import com.bim.compiler.bom.walker.PlacementCollectorVisitor;
import com.bim.ormsandbox.po.MBOM;

import java.sql.*;
import java.util.*;

/**
 * BOM-driven element placement loader.
 *
 * <p>Walks BOMs via {@link BOMWalker} + {@link PlacementCollectorVisitor},
 * collecting {@link Placement} records from BUY leaves. Same tack convention
 * maths (§3.4) used for both EN-BLOC and WALK THRU modes.
 *
 * <p>BOMWalker fires onBuy for each BUY leaf →
 * PlacementCollectorVisitor accumulates world coordinates through the
 * tack convention: each level's origin + line dx/dy/dz offsets summed to
 * produce world coordinates at BUY leaves.
 *
 * <h3>BIM COBOL verbs</h3>
 * <ul>
 *   <li>{@code EN-BLOC} (_s) — singularity. C_DocType AABB = M_Product AABB
 *       → exactly one BOM matches → take whole. One C_OrderLine, one
 *       CO_EmptySpaceLine. No further decomposition.</li>
 *   <li>{@code WALK THRU} (_e) — structured UNIT BOMs. Walker enters each
 *       sub-assembly, accumulates tack coordinates through the hierarchy.
 *       C_OrderLine per slot, CO_EmptySpaceLine per slot.</li>
 * </ul>
 * <p>Same BOM data, same leaves. EN-BLOC resolves in one shot; WALK THRU
 * produces orderlines at each sub-assembly level.
 *
 * <p>Pattern: singleton lazy-load + cache, same as SlabSpecAD, FloorTypeAD.
 */
public class PlacementLoader {

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

    // Consumption registry — elements processed by StoreyCompiler.applyPlacementOverrides
    // are RELATIONAL source; emitGlobalPlacementElements (FLAT source) must skip them.
    // Key: buildingType + NUL + elementRef.
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

    /**
     * Walk BOMs via BOMWalker + PlacementCollectorVisitor.
     *
     * <p>Mode selection via {@code bom.mode} system property — maps to BIM COBOL verbs:
     * <ul>
     *   <li>{@code ENBLOC} (default) — <b>HelloWorld POC only.</b> BOM lines are
     *       already tacked. DocType flag: when AABB and DocType are consistent
     *       throughout, take each as-is without recalculating through layers.
     *       One C_OrderLine, one CO_EmptySpaceLine.
     *       Output suffix: _s (singular).</li>
     *   <li>{@code WALKTHRU} — the proper normal path. Recalculates by tacking
     *       through each BOM layer (UNIT → FLOOR → SET → BUY).
     *       C_OrderLine per slot, CO_EmptySpaceLine per slot.
     *       Precursor to production.
     *       Output suffix: _e (exploded).</li>
     * </ul>
     *
     * <p>Both produce the same result when the data stack is consistent.
     * EN-BLOC proves data correctness. WALK THRU proves the compilation mechanism.
     */
    private void loadFromBOM() {
        String mode = System.getProperty("bom.mode", "ENBLOC");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db");
             Connection compConn = DriverManager.getConnection("jdbc:sqlite:library/component_library.db")) {
            Map<String, String> docSubTypeToProject = loadDocSubTypeMap(conn);
            BOMWalker walker = new BOMWalker(conn);

            // Load all BUILDING BOMs — the top-level finished goods BOM per building type
            List<MBOM> roots = MBOM.getByType(conn, "BUILDING");
            System.out.printf("[PlacementLoader] %s — %d BUILDING BOMs%n", mode, roots.size());

            for (MBOM bom : roots) {
                String docSubType = bom.getDocSubType();
                String buildingType = docSubTypeToProject.get(docSubType);
                if (buildingType == null) {
                    System.err.printf("[PlacementLoader] No C_DocType for doc_sub_type '%s' on BOM %s — skipping%n",
                        docSubType, bom.getBomId());
                    continue;
                }

                // World origin from I_Element_Extraction (compile-time, not BOM.db)
                double[] worldOrigin = loadWorldOrigin(compConn, buildingType);

                PlacementCollectorVisitor visitor = new PlacementCollectorVisitor(conn, buildingType, worldOrigin);
                walker.walkSelf(bom.getBomId(), List.of(visitor), buildingType);

                List<Placement> placements = visitor.getPlacements();
                cache.computeIfAbsent(buildingType, k -> new ArrayList<>()).addAll(placements);

                System.out.printf("[PlacementLoader] %s (%s) → %d placements, worldOrigin=(%.3f,%.3f,%.3f) [%s]%n",
                    bom.getBomId(), buildingType, placements.size(),
                    worldOrigin[0], worldOrigin[1], worldOrigin[2], mode);
            }
        } catch (SQLException e) {
            System.err.println("[PlacementLoader] Failed to load placements: " + e.getMessage());
        }
    }

    // loadUnitBoms removed — prefix-based lookup via MBOM.getByBomIdPrefix replaces category-based selection

    /**
     * Compute building world origin from I_Element_Extraction (component_library.db).
     * Returns LFD corner = (min_x, min_y, min_z) across all elements for the building.
     * Returns (0,0,0) for generative buildings with no extraction data.
     */
    private static double[] loadWorldOrigin(Connection compConn, String buildingType) {
        try (PreparedStatement ps = compConn.prepareStatement(
                "SELECT MIN(min_x), MIN(min_y), MIN(min_z) " +
                "FROM I_Element_Extraction WHERE building_type = ?")) {
            ps.setString(1, buildingType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getObject(1) != null) {
                    return new double[]{rs.getDouble(1), rs.getDouble(2), rs.getDouble(3)};
                }
            }
        } catch (SQLException e) {
            System.err.printf("[PlacementLoader] Failed to load world origin for %s: %s%n",
                buildingType, e.getMessage());
        }
        return new double[]{0, 0, 0};
    }

    /** Load C_DocType.DocSubType → ProjectName mapping. */
    private static Map<String, String> loadDocSubTypeMap(Connection conn) throws SQLException {
        Map<String, String> map = new HashMap<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT DocSubType, ProjectName FROM C_DocType")) {
            while (rs.next()) {
                map.put(rs.getString("DocSubType"), rs.getString("ProjectName"));
            }
        }
        return map;
    }

    // Singleton for shared use
    private static PlacementLoader instance;
    public static PlacementLoader getInstance() {
        if (instance == null) instance = new PlacementLoader();
        return instance;
    }
    public static void resetInstance() { instance = null; }
}
