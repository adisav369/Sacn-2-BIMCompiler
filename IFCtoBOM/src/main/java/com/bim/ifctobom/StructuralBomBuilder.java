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
 *   <li>LBD offsets (dx/dy/dz) relative to floor LBD (§4 tack convention)</li>
 *   <li>TACK children linking floors to BUILDING BOM</li>
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

        // ── Compute building origin (LBD corner) from all elements ───────────
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
                aabbW, aabbD, aabbH,
                allMinX, allMinY, allMinZ);

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
                    floorAabbW, floorAabbD, floorAabbH,
                    0, 0, 0);  // R16: child origin = 0; offset lives in MAKE line dx
            // Set bom_category separately
            updateBomCategory(bomConn, floorBomId, storeyInfo.bomCategory());

            // ── Insert element lines (skip scope-assigned elements) ──────────
            // FACTORIZE-v2: verb-compressed LEAF writes via VerbFactorizer.
            // Groups by product → VerbDetector cascade → factored or unfactored.
            // Small groups (<4) fall through to per-instance — backward compatible.
            Set<String> excluded = excludeByStorey.getOrDefault(storeyName, Set.of());
            List<ExtractionElement> floorElems = new ArrayList<>();
            for (ExtractionElement e : elems) {
                if (!excluded.isEmpty()
                        && excluded.contains(ScopeBomBuilder.elementKey(e))) {
                    continue;  // assigned to a SET BOM
                }
                floorElems.add(e);
            }

            VerbFactorizer.FactorResult fr = VerbFactorizer.factorize(
                    bomConn, floorBomId, floorElems, fMinX, fMinY, fMinZ, 10);
            totalLines += fr.linesWritten();
            if (fr.verbMatched() > 0) {
                System.out.printf("  [verb] %s STR: %d verb patterns (%d instances), %d unfactored%n",
                        storeyName, fr.verbMatched(), fr.verbInstances(), fr.unfactored());
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

        // ASSUMPTION: Every storey name in the extraction DB has a matching key
        // in the YAML storeys map. Elements in unmapped storeys are silently
        // dropped from all BOMs — they appear in storeyElements but are never
        // iterated by any builder. If you see this warning, either add the
        // storey to the classify YAML or verify the extraction storey names.
        Set<String> mapped = config.storeys().keySet();
        Set<String> found = storeyElements.keySet();
        int droppedElements = 0;
        for (String s : found) {
            if (!mapped.contains(s)) {
                int count = storeyElements.get(s).size();
                droppedElements += count;
                System.err.printf("  [WARN] Unmapped storey in %s: %s (%d elements dropped)%n",
                        config.buildingType(), s, count);
            }
        }
        if (droppedElements > 0) {
            System.err.printf("  [WARN] Total elements dropped from unmapped storeys: %d%n",
                    droppedElements);
        }

        return new BuildResult(totalLines, aabbW, aabbD, aabbH, floorBomIds);
    }

    // ── SQL helpers ──────────────────────────────────────────────────────────

    private static void insertBomHeader(Connection conn, String bomId, String bomName,
                                        String bomType, String groupBy,
                                        String docSubType, String docBaseType,
                                        double aabbW, double aabbD, double aabbH,
                                        double originX, double originY, double originZ)
            throws SQLException {
        String sql = """
                INSERT OR REPLACE INTO m_bom
                (bom_id, bom_name, bom_type, group_by, entity_type,
                 doc_sub_type, doc_base_type,
                 aabb_width_mm, aabb_depth_mm, aabb_height_mm,
                 origin_x, origin_y, origin_z, is_active)
                VALUES (?, ?, ?, ?, 'D', ?, ?,
                        ?, ?, ?,
                        ?, ?, ?, 1)
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
            stmt.setDouble(10, originX);
            stmt.setDouble(11, originY);
            stmt.setDouble(12, originZ);
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
        insertBomLine(conn, bomId, childProductId, componentType, role, sequence,
                rotationRule, dx, dy, dz, allocW, allocD, allocH,
                storey, elementRef, ordinal, orientation, materialName, materialRgba, 1);
    }

    private static void insertBomLine(Connection conn,
                                      String bomId, String childProductId, String componentType,
                                      String role, int sequence, String rotationRule,
                                      double dx, double dy, double dz,
                                      double allocW, double allocD, double allocH,
                                      String storey, String elementRef, int ordinal,
                                      String orientation,
                                      String materialName, String materialRgba,
                                      int qty)
            throws SQLException {
        String sql = """
                INSERT INTO m_bom_line
                (bom_id, child_product_id, component_type, role, sequence,
                 rotation_rule, fit_priority, min_space_mm,
                 dx, dy, dz, is_active, entity_type, qty,
                 allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                 storey, element_ref, ordinal, orientation,
                 material_name, material_rgba)
                VALUES (?, ?, ?, ?, ?,
                        ?, 20, 0,
                        ?, ?, ?, 1, 'D', ?,
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
            stmt.setInt(10, qty);
            stmt.setDouble(11, allocW);
            stmt.setDouble(12, allocD);
            stmt.setDouble(13, allocH);
            stmt.setString(14, storey);
            stmt.setString(15, elementRef);
            stmt.setInt(16, ordinal);
            stmt.setString(17, orientation);
            stmt.setString(18, materialName);
            stmt.setString(19, materialRgba);
            stmt.executeUpdate();
        }
    }
}
