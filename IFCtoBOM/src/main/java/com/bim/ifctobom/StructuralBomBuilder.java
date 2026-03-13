package com.bim.ifctobom;

import com.bim.ifctobom.ClassificationYaml.BuildingConfig;
import com.bim.ifctobom.ClassificationYaml.StoreyConfig;
import com.bim.ifctobom.ExtractionReader.ExtractionElement;

import java.sql.*;
import java.util.*;

/**
 * Creates structural BOMs from IFC extraction data.
 *
 * <p>Direct port of {@code RosettaStoneExtract.py extract_building()}:
 * <ul>
 *   <li>BUILDING BOM header with whole-building AABB</li>
 *   <li>FLOOR STR BOMs per storey with element BOM lines</li>
 *   <li>Centroid offsets (dx/dy/dz) relative to floor origin (§1.2)</li>
 *   <li>MAKE children linking floors to BUILDING BOM</li>
 * </ul>
 */
public class StructuralBomBuilder {

    /**
     * Build result containing counts and computed values.
     */
    public record BuildResult(
            int totalLines,
            double aabbWidthMm, double aabbDepthMm, double aabbHeightMm,
            List<String> floorBomIds
    ) {}

    /**
     * Build structural BOMs from extraction data.
     *
     * @param bomConn     writable connection to output BOM DB
     * @param config      building classification from YAML
     * @param storeyElements extraction elements grouped by storey
     * @param excludeByStorey element keys assigned to scope spaces (per storey),
     *                        skipped from FLOOR STR BOMs. May be empty.
     * @return build result with counts
     */
    public static BuildResult build(Connection bomConn, BuildingConfig config,
                                    Map<String, List<ExtractionElement>> storeyElements,
                                    Map<String, Set<String>> excludeByStorey)
            throws SQLException {

        String prefix = config.prefix();
        String buildingBomId = config.buildingBomId();

        // ── Compute building origin (LFD corner) from all elements ───────────
        List<ExtractionElement> allElements = new ArrayList<>();
        storeyElements.values().forEach(allElements::addAll);

        if (allElements.isEmpty()) {
            return new BuildResult(0, 0, 0, 0, List.of());
        }

        double allMinX = allElements.stream().mapToDouble(ExtractionElement::minX).min().orElse(0);
        double allMinY = allElements.stream().mapToDouble(ExtractionElement::minY).min().orElse(0);
        double allMinZ = allElements.stream().mapToDouble(ExtractionElement::minZ).min().orElse(0);
        double allMaxX = allElements.stream().mapToDouble(ExtractionElement::maxX).max().orElse(0);
        double allMaxY = allElements.stream().mapToDouble(ExtractionElement::maxY).max().orElse(0);
        double allMaxZ = allElements.stream().mapToDouble(ExtractionElement::maxZ).max().orElse(0);

        double aabbW = (allMaxX - allMinX) * 1000;
        double aabbD = (allMaxY - allMinY) * 1000;
        double aabbH = (allMaxZ - allMinZ) * 1000;

        // ── Create BUILDING M_Product assembly stub ──────────────────────────
        ProductRegistrar.ensureAssemblyStub(bomConn, buildingBomId, "BUILDING");

        // ── Create BUILDING BOM header ───────────────────────────────────────
        insertBomHeader(bomConn, buildingBomId,
                prefix + " " + config.name(),
                "BUILDING", "BUILDING",
                config.docSubType(), config.docBaseType(),
                aabbW, aabbD, aabbH);

        // ── Process each storey ──────────────────────────────────────────────
        int totalLines = 0;
        List<String> floorBomIds = new ArrayList<>();

        for (Map.Entry<String, StoreyConfig> storeyEntry : config.storeys().entrySet()) {
            String storeyName = storeyEntry.getKey();
            StoreyConfig storeyInfo = storeyEntry.getValue();

            List<ExtractionElement> elems = storeyElements.get(storeyName);
            if (elems == null || elems.isEmpty()) continue;

            String floorBomId = prefix + "_" + storeyInfo.code() + "_STR";
            floorBomIds.add(floorBomId);

            // ── Compute floor AABB ───────────────────────────────────────────
            double fMinX = elems.stream().mapToDouble(ExtractionElement::minX).min().orElse(0);
            double fMaxX = elems.stream().mapToDouble(ExtractionElement::maxX).max().orElse(0);
            double fMinY = elems.stream().mapToDouble(ExtractionElement::minY).min().orElse(0);
            double fMaxY = elems.stream().mapToDouble(ExtractionElement::maxY).max().orElse(0);
            double fMinZ = elems.stream().mapToDouble(ExtractionElement::minZ).min().orElse(0);
            double fMaxZ = elems.stream().mapToDouble(ExtractionElement::maxZ).max().orElse(0);

            double floorAabbW = (fMaxX - fMinX) * 1000;
            double floorAabbD = (fMaxY - fMinY) * 1000;
            double floorAabbH = (fMaxZ - fMinZ) * 1000;

            // ── Create floor M_Product assembly stub ─────────────────────────
            ProductRegistrar.ensureAssemblyStub(bomConn, floorBomId, "FLOOR");

            // ── Create FLOOR STR BOM header ──────────────────────────────────
            insertBomHeader(bomConn, floorBomId,
                    prefix + " " + storeyName + " Structured",
                    "FLOOR", "STOREY",
                    null, null,  // floor BOMs don't carry doc type
                    floorAabbW, floorAabbD, floorAabbH);
            // Set bom_category separately
            updateBomCategory(bomConn, floorBomId, storeyInfo.bomCategory());

            // ── Insert element lines (skip scope-assigned elements) ──────────
            Set<String> excluded = excludeByStorey.getOrDefault(storeyName, Set.of());
            int seq = 10;
            for (ExtractionElement e : elems) {
                if (!excluded.isEmpty()
                        && excluded.contains(ScopeBomBuilder.elementKey(e))) {
                    continue;  // assigned to a SET BOM
                }

                // Centroid offset from floor origin — parent-relative (§1.2)
                double dx = e.centroidX() - fMinX;
                double dy = e.centroidY() - fMinY;
                double dz = e.centroidZ() - fMinZ;

                String rotationRule = e.orientation() != null ? e.orientation() : "0";

                insertBomLine(bomConn, floorBomId, e.mProductId(), "LEAF",
                        e.ifcClass(), seq, rotationRule,
                        dx, dy, dz,
                        e.widthMm(), e.depthMm(), e.heightMm(),
                        e.storey(), e.elementRef(), e.ordinal(), e.orientation(),
                        e.materialName(), e.materialRgba());
                seq += 10;
                totalLines++;
            }

            // ── Add MAKE child to BUILDING BOM ───────────────────────────────
            // MAKE dx/dy/dz = floor-to-building offset (always >= 0)
            double makeDx = fMinX - allMinX;
            double makeDy = fMinY - allMinY;
            double makeDz = fMinZ - allMinZ;

            insertBomLine(bomConn, buildingBomId, floorBomId, "MAKE",
                    storeyInfo.role(), storeyInfo.seq(), "0",
                    makeDx, makeDy, makeDz,
                    0, 0, 0,  // MAKE children don't carry allocated size
                    null, null, 0, null, null, null);
        }

        // Warn about unmapped storeys
        Set<String> mapped = config.storeys().keySet();
        Set<String> found = storeyElements.keySet();
        for (String s : found) {
            if (!mapped.contains(s)) {
                System.err.printf("  [WARN] Unmapped storey in %s: %s%n",
                        config.buildingType(), s);
            }
        }

        return new BuildResult(totalLines, aabbW, aabbD, aabbH, floorBomIds);
    }

    // ── SQL helpers ──────────────────────────────────────────────────────────

    private static void insertBomHeader(Connection conn, String bomId, String bomName,
                                        String bomType, String groupBy,
                                        String docSubType, String docBaseType,
                                        double aabbW, double aabbD, double aabbH)
            throws SQLException {
        String sql = """
                INSERT OR REPLACE INTO m_bom
                (bom_id, bom_name, bom_type, group_by, entity_type,
                 doc_sub_type, doc_base_type,
                 aabb_width_mm, aabb_depth_mm, aabb_height_mm,
                 origin_x, origin_y, origin_z, is_active)
                VALUES (?, ?, ?, ?, 'D', ?, ?,
                        ?, ?, ?,
                        0.0, 0.0, 0.0, 1)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bomId);
            stmt.setString(2, bomName);
            stmt.setString(3, bomType);
            stmt.setString(4, groupBy);
            stmt.setString(5, docSubType);
            stmt.setString(6, docBaseType);
            stmt.setDouble(7, aabbW);
            stmt.setDouble(8, aabbD);
            stmt.setDouble(9, aabbH);
            stmt.executeUpdate();
        }
    }

    private static void updateBomCategory(Connection conn, String bomId, String bomCategory)
            throws SQLException {
        String sql = "UPDATE m_bom SET bom_category = ? WHERE bom_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bomCategory);
            stmt.setString(2, bomId);
            stmt.executeUpdate();
        }
    }

    private static void insertBomLine(Connection conn,
                                      String bomId, String childProductId, String componentType,
                                      String role, int sequence, String rotationRule,
                                      double dx, double dy, double dz,
                                      double allocW, double allocD, double allocH,
                                      String storey, String elementRef, int ordinal,
                                      String orientation,
                                      String materialName, String materialRgba)
            throws SQLException {
        String sql = """
                INSERT INTO m_bom_line
                (bom_id, child_product_id, component_type, role, sequence,
                 rotation_rule, fit_priority, min_space_mm,
                 dx, dy, dz, is_active, entity_type,
                 allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                 storey, element_ref, ordinal, orientation,
                 material_name, material_rgba)
                VALUES (?, ?, ?, ?, ?,
                        ?, 20, 0,
                        ?, ?, ?, 1, 'D',
                        ?, ?, ?,
                        ?, ?, ?, ?,
                        ?, ?)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bomId);
            stmt.setString(2, childProductId);
            stmt.setString(3, componentType);
            stmt.setString(4, role);
            stmt.setInt(5, sequence);
            stmt.setString(6, rotationRule);
            stmt.setDouble(7, dx);
            stmt.setDouble(8, dy);
            stmt.setDouble(9, dz);
            stmt.setDouble(10, allocW);
            stmt.setDouble(11, allocD);
            stmt.setDouble(12, allocH);
            stmt.setString(13, storey);
            stmt.setString(14, elementRef);
            stmt.setInt(15, ordinal);
            stmt.setString(16, orientation);
            stmt.setString(17, materialName);
            stmt.setString(18, materialRgba);
            stmt.executeUpdate();
        }
    }
}
