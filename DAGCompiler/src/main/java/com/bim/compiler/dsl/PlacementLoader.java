package com.bim.compiler.dsl;

import com.bim.compiler.bom.walker.BOMWalker;
import com.bim.compiler.bom.walker.OrderLineWalker;
import com.bim.compiler.bom.walker.PlacementCollectorVisitor;
import com.bim.ormsandbox.po.MBOM;

import java.sql.*;
import java.util.*;

/**
 * BOM-driven element placement loader.
 *
 * <p>Walks BOMs via {@link BOMWalker} + {@link PlacementCollectorVisitor},
 * collecting {@link Placement} records from leaf nodes. Same tack convention
 * maths (§3.4) used for both EN-BLOC and WALK THRU modes.
 *
 * <p>BOMWalker fires onLeaf for each leaf node →
 * PlacementCollectorVisitor accumulates world coordinates through the
 * tack convention: each level's origin + line dx/dy/dz offsets summed to
 * produce world coordinates at leaf nodes.
 *
 * <h3>BIM COBOL verbs</h3>
 * <ul>
 *   <li>{@code EN-BLOC} (_s) — singularity. C_DocType AABB = M_Product AABB
 *       → exactly one BOM matches → take whole. One C_OrderLine, one
 *       spatial slot (compiler-internal: co_empty_space_line). No further decomposition.</li>
 *   <li>{@code WALK THRU} (_e) — structured UNIT BOMs. Walker enters each
 *       sub-assembly, accumulates tack coordinates through the hierarchy.
 *       C_OrderLine per slot, spatial data from M_BOM_Line dx/dy/dz.</li>
 * </ul>
 * <p>Same BOM data, same leaves. EN-BLOC resolves in one shot; WALK THRU
 * produces orderlines at each sub-assembly level.
 *
 * <p>Pattern: singleton lazy-load + cache, same as SlabSpecAD, FloorTypeAD.
 *
 * <h3>ANTI-DRIFT: BOM DB path</h3>
 * <p>Reads from {@code System.getProperty("bom.db")} — per-building compile DB
 * (e.g. {@code library/_SH_compile.db}). NO hardcoded "BOM.db". Set via Maven
 * {@code -Dbom.db=...} in run_RosettaStones.sh.
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
        if (hasOrderLineData()) {
            loadFromOrderLine();
        } else {
            loadFromBOM();
        }
    }

    /**
     * Check if C_OrderLine data exists in the compile DB.
     * If so, we use the OrderLine path instead of direct BOM walk.
     */
    private boolean hasOrderLineData() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"));
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM C_OrderLine")) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            // Table doesn't exist or empty — fall back to BOM path
            return false;
        }
    }

    /**
     * Walk C_OrderLine tree via OrderLineWalker + PlacementCollectorVisitor.
     *
     * <p>S60 path: BomDropper creates C_OrderLine tree in compile DB,
     * then OrderLineWalker walks it, loading MBOMLine attributes via
     * bom_child_id FK join-back. Same PlacementCollectorVisitor produces
     * identical Placements.
     *
     * @see com.bim.compiler.bom.BomDropper
     * @see OrderLineWalker
     */
    private void loadFromOrderLine() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"));
             Connection compConn = DriverManager.getConnection("jdbc:sqlite:library/ERP.db")) {
            OrderLineWalker walker = new OrderLineWalker(conn, compConn);

            // Load C_Order rows → map to building types via C_DocType_ID
            Map<String, String> docTypeToProject = loadDocTypeToProjectMap(conn);

            String sql = "SELECT C_Order_ID, C_DocType_ID FROM C_Order WHERE IsActive = 1";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String orderId = rs.getString("C_Order_ID");
                    String docTypeId = rs.getString("C_DocType_ID");
                    String buildingType = docTypeToProject.get(docTypeId);
                    if (buildingType == null) {
                        System.err.printf("[PlacementLoader] No C_DocType for %s — skipping%n", docTypeId);
                        continue;
                    }

                    // World origin from BUILDING BOM (same as loadFromBOM path)
                    double[] worldOrigin = loadWorldOriginForOrder(conn, orderId);

                    PlacementCollectorVisitor visitor = new PlacementCollectorVisitor(
                            conn, buildingType, worldOrigin);
                    walker.walkOrder(orderId, List.of(visitor), buildingType);

                    List<Placement> placements = visitor.getPlacements();
                    cache.computeIfAbsent(buildingType, k -> new ArrayList<>()).addAll(placements);

                    System.out.printf("[PlacementLoader] OrderLine %s (%s) → %d placements%n",
                            orderId, buildingType, placements.size());
                }
            }
        } catch (SQLException e) {
            System.err.println("[PlacementLoader] Failed to load from OrderLine: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Load C_DocType_ID → ProjectName mapping. */
    private static Map<String, String> loadDocTypeToProjectMap(Connection conn) throws SQLException {
        Map<String, String> map = new HashMap<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT C_DocType_ID, ProjectName FROM C_DocType")) {
            while (rs.next()) {
                map.put(rs.getString("C_DocType_ID"), rs.getString("ProjectName"));
            }
        }
        return map;
    }

    /** Load world origin for an order's root C_OrderLine's BUILDING BOM. */
    private static double[] loadWorldOriginForOrder(Connection conn, String orderId) throws SQLException {
        // Root C_OrderLine's family_ref = BUILDING bom_id
        String sql = "SELECT family_ref FROM C_OrderLine "
                   + "WHERE C_Order_ID = ? AND Parent_OrderLine_ID IS NULL AND IsActive = 1 LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String bomId = rs.getString("family_ref");
                    MBOM bom = new MBOM(conn);
                    if (bom.load(bomId)) {
                        return new double[]{bom.getOriginX(), bom.getOriginY(), bom.getOriginZ()};
                    }
                }
            }
        }
        return new double[]{0, 0, 0};
    }

    /**
     * Walk BOMs via BOMWalker + PlacementCollectorVisitor.
     *
     * <p>Legacy path: walks m_bom directly. Used when no C_OrderLine data
     * exists in the compile DB (backward compatibility).
     *
     * @see com.bim.designer.api.DesignerAPIImpl#bomDrop BOM explosion entry point
     */
    private void loadFromBOM() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + System.getProperty("bom.db"));
             Connection compConn = DriverManager.getConnection("jdbc:sqlite:library/ERP.db")) {
            Map<String, String> docSubTypeToProject = loadDocSubTypeMap(conn);
            BOMWalker walker = new BOMWalker(conn, compConn);

            // Load all BUILDING BOMs — the top-level finished goods BOM per building type
            List<MBOM> roots = MBOM.getByType(conn, "BUILDING");
            System.out.printf("[PlacementLoader] %d BUILDING BOMs%n", roots.size());

            for (MBOM bom : roots) {
                String docSubType = bom.getDocSubType();
                String buildingType = docSubTypeToProject.get(docSubType);
                if (buildingType == null) {
                    System.err.printf("[PlacementLoader] No C_DocType for doc_sub_type '%s' on BOM %s — skipping%n",
                        docSubType, bom.getBomId());
                    continue;
                }

                // World origin from BUILDING BOM (stored during BOM generation)
                double[] worldOrigin = new double[]{
                    bom.getOriginX(), bom.getOriginY(), bom.getOriginZ()
                };

                PlacementCollectorVisitor visitor = new PlacementCollectorVisitor(conn, buildingType, worldOrigin);
                walker.walkSelf(bom.getBomId(), List.of(visitor), buildingType);

                List<Placement> placements = visitor.getPlacements();
                cache.computeIfAbsent(buildingType, k -> new ArrayList<>()).addAll(placements);

                System.out.printf("[PlacementLoader] %s (%s) → %d placements, worldOrigin=(%.3f,%.3f,%.3f)%n",
                    bom.getBomId(), buildingType, placements.size(),
                    worldOrigin[0], worldOrigin[1], worldOrigin[2]);
            }
        } catch (SQLException e) {
            System.err.println("[PlacementLoader] Failed to load placements: " + e.getMessage());
        }
    }

    // loadUnitBoms removed — prefix-based lookup via MBOM.getByBomIdPrefix replaces category-based selection
    // loadWorldOrigin removed (R12) — world origin now stored in m_bom.origin_x/y/z by BOM generation

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
