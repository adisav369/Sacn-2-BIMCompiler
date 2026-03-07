package com.bim.compiler.dsl;

import com.bim.compiler.bom.walker.BOMWalker;
import com.bim.compiler.bom.walker.PlacementCollectorVisitor;
import com.bim.ormsandbox.po.MBOM;

import java.sql.*;
import java.util.*;

/**
 * BOM-driven element placement loader.
 *
 * <p>Walks EXTRACTED BOMs via {@link BOMWalker} + {@link PlacementCollectorVisitor},
 * collecting {@link Placement} records from BUY leaves. Same tack convention
 * maths (§3.4) used for both flat EXTRACTED and structured hierarchy walks.
 *
 * <p>Replaces the former flat-SQL approach. Same Placement record, same API.
 * Different engine: BOMWalker fires onBuy for each BUY leaf →
 * PlacementCollectorVisitor accumulates world coordinates through the
 * tack convention: each level's origin + line dx/dy/dz offsets summed to
 * produce world coordinates at BUY leaves.
 *
 * <p>For EXTRACTED BOMs (flat, all BUY), walkSelf produces the same result
 * as the old flat SQL — but through the same code path that will walk
 * structured hierarchies (UNIT → FLOOR → SET → BUY) for the _e path.
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
     * <p>Mode selection via {@code bom.mode} system property:
     * <ul>
     *   <li>{@code EXTRACTED} (default) — walks flat EXTRACTED BOMs (EXT_SH, EXT_DX).
     *       EN-BLOC singularity: single C_OrderLine, single CO_EmptySpaceLine.</li>
     *   <li>{@code STRUCTURED} — walks UNIT-level BOMs (UNIT_SH_STD, UNIT_DUPLEX_STD).
     *       EXPLODE: C_OrderLine per slot, CO_EmptySpaceLine per slot.
     *       Hierarchy: UNIT → FLOOR → SET → BUY.</li>
     * </ul>
     *
     * <p>Same BOMWalker code, same PlacementCollectorVisitor, same tack convention.
     * Different root BOM selection → different tree shape → same result when
     * structured BOMs contain all elements.
     */
    private void loadFromBOM() {
        String mode = System.getProperty("bom.mode", "EXTRACTED");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:library/BOM.db")) {
            Map<String, String> docSubTypeToProject = loadDocSubTypeMap(conn);
            BOMWalker walker = new BOMWalker(conn);

            List<MBOM> roots;
            if ("STRUCTURED".equals(mode)) {
                roots = loadUnitBoms(conn);
                System.out.printf("[PlacementLoader] Mode=STRUCTURED — walking %d UNIT BOMs%n", roots.size());
            } else {
                roots = MBOM.getByCategory(conn, "EXTRACTED");
                System.out.printf("[PlacementLoader] Mode=EXTRACTED — walking %d EXTRACTED BOMs%n", roots.size());
            }

            for (MBOM bom : roots) {
                String docSubType = bom.getDocSubType();
                String buildingType = docSubTypeToProject.get(docSubType);
                if (buildingType == null) {
                    System.err.printf("[PlacementLoader] No C_DocType for doc_sub_type '%s' on BOM %s — skipping%n",
                        docSubType, bom.getBomId());
                    continue;
                }

                PlacementCollectorVisitor visitor = new PlacementCollectorVisitor(conn, buildingType);
                walker.walkSelf(bom.getBomId(), List.of(visitor), buildingType);

                List<Placement> placements = visitor.getPlacements();
                cache.computeIfAbsent(buildingType, k -> new ArrayList<>()).addAll(placements);

                System.out.printf("[PlacementLoader] %s (%s) → %d placements via BOMWalker [%s]%n",
                    bom.getBomId(), buildingType, placements.size(), mode);
            }
        } catch (SQLException e) {
            System.err.println("[PlacementLoader] Failed to load placements: " + e.getMessage());
        }
    }

    /** Find all active UNIT-level BOMs (structured hierarchy roots, bom_category='UN'). */
    private static List<MBOM> loadUnitBoms(Connection conn) throws SQLException {
        return MBOM.getByCategory(conn, "UN");
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
