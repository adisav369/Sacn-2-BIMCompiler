package com.bim.ifctobom;

import com.bim.ifctobom.ClassificationYaml.BuildingConfig;
import com.bim.ifctobom.ClassificationYaml.DisciplineConfig;
import com.bim.ifctobom.ClassificationYaml.StoreyConfig;
import com.bim.ifctobom.ExtractionReader.ExtractionElement;
import com.bim.ifctobom.StructuralBomBuilder.BuildResult;

import java.sql.*;
import java.util.*;

/**
 * Creates discipline-stratified BOMs for CO (Commercial) buildings.
 *
 * <p>Hierarchy: BUILDING → FLOOR → DISCIPLINE → LEAF
 * <ul>
 *   <li>BUILDING BOM — whole-building AABB, doc_base_type=CO</li>
 *   <li>FLOOR BOMs — one per storey (7 for TE)</li>
 *   <li>DISCIPLINE SET BOMs — one per discipline per floor (e.g., TE_GF_ARC)</li>
 *   <li>LEAF lines — factored recipe lines with verb_ref + qty</li>
 * </ul>
 *
 * <p>Discipline assignment is authoritative from I_Element_Extraction.discipline
 * (populated from federated model metadata). The YAML disciplines section
 * declares what disciplines exist; it does NOT assign elements.
 *
 * <p>Offset chain: BUILDING_origin + FLOOR_offset + 0 + LEAF_offset = centroid.
 * DISCIPLINE is a logical grouping with zero spatial offset.
 *
 * <h3>FACTORIZE-v1 F-2: Verb Pattern Compression (2026-03-17)</h3>
 * <p>Elements are grouped by (discipline, child_product_id). For each group,
 * VerbDetector runs the cascade: TILE > ROUTE > FRAME > SPRAY. If a pattern
 * matches, one recipe line is written with verb_ref + qty=N + origin dx/dy/dz.
 * Unmatched groups fall through to per-instance lines (qty=1, no verb_ref).
 *
 * <p><b>Non-Disturbance:</b> SH/DX groups are too small for pattern detection
 * (MIN_GROUP=4) and fall through to unfactored writes — identical output,
 * no regression. TE groups with 33K+ elements compress to verb recipes.
 *
 * <p><b>Guard:</b> BomValidator checkExtractionReconciliation uses SUM(qty)
 * to verify total instances match extraction count.
 */
public class DisciplineBomBuilder {

    /**
     * Build discipline-stratified BOMs for a CO building.
     * Returns the same {@link BuildResult} as {@link StructuralBomBuilder}
     * for pipeline compatibility.
     */
    public static BuildResult build(Connection bomConn, BuildingConfig config,
                                    Map<String, List<ExtractionElement>> storeyElements)
            throws SQLException {

        String prefix = config.prefix();
        String buildingBomId = config.buildingBomId();

        // ── Compute building AABB from all elements ───────────────────────
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

        // ── Create BUILDING ───────────────────────────────────────────────
        ProductRegistrar.ensureAssemblyStub(bomConn, buildingBomId, "BUILDING");
        insertBomHeader(bomConn, buildingBomId,
                prefix + " " + config.name(),
                "BUILDING", "BUILDING",
                config.docSubType(), config.docBaseType(),
                aabbW, aabbD, aabbH, null);

        // ── Process each storey ───────────────────────────────────────────
        int totalLines = 0;
        List<String> floorBomIds = new ArrayList<>();

        for (Map.Entry<String, StoreyConfig> storeyEntry : config.storeys().entrySet()) {
            String storeyName = storeyEntry.getKey();
            StoreyConfig storeyInfo = storeyEntry.getValue();

            List<ExtractionElement> elems = storeyElements.get(storeyName);
            if (elems == null || elems.isEmpty()) continue;

            String floorBomId = prefix + "_" + storeyInfo.code();
            floorBomIds.add(floorBomId);

            // ── Compute floor AABB ────────────────────────────────────────
            double fMinX = elems.stream().mapToDouble(ExtractionElement::minX).min().orElse(0);
            double fMaxX = elems.stream().mapToDouble(ExtractionElement::maxX).max().orElse(0);
            double fMinY = elems.stream().mapToDouble(ExtractionElement::minY).min().orElse(0);
            double fMaxY = elems.stream().mapToDouble(ExtractionElement::maxY).max().orElse(0);
            double fMinZ = elems.stream().mapToDouble(ExtractionElement::minZ).min().orElse(0);
            double fMaxZ = elems.stream().mapToDouble(ExtractionElement::maxZ).max().orElse(0);

            double floorW = (fMaxX - fMinX) * 1000;
            double floorD = (fMaxY - fMinY) * 1000;
            double floorH = (fMaxZ - fMinZ) * 1000;

            // ── Create FLOOR BOM ──────────────────────────────────────────
            ProductRegistrar.ensureAssemblyStub(bomConn, floorBomId, "FLOOR");
            insertBomHeader(bomConn, floorBomId,
                    prefix + " " + storeyName,
                    "FLOOR", "STOREY",
                    null, null,
                    floorW, floorD, floorH,
                    storeyInfo.bomCategory());

            // ── Group elements by discipline ──────────────────────────────
            Map<String, List<ExtractionElement>> byDiscipline = new LinkedHashMap<>();
            for (ExtractionElement e : elems) {
                String disc = e.discipline() != null ? e.discipline() : "ARC";
                byDiscipline.computeIfAbsent(disc, k -> new ArrayList<>()).add(e);
            }

            // ── Create DISCIPLINE sub-BOMs ────────────────────────────────
            int discSeq = 100;
            for (Map.Entry<String, List<ExtractionElement>> discEntry : byDiscipline.entrySet()) {
                String discCode = discEntry.getKey();
                List<ExtractionElement> discElems = discEntry.getValue();

                String discBomId = floorBomId + "_" + discCode;

                // Discipline AABB
                double dMinX = discElems.stream().mapToDouble(ExtractionElement::minX).min().orElse(0);
                double dMaxX = discElems.stream().mapToDouble(ExtractionElement::maxX).max().orElse(0);
                double dMinY = discElems.stream().mapToDouble(ExtractionElement::minY).min().orElse(0);
                double dMaxY = discElems.stream().mapToDouble(ExtractionElement::maxY).max().orElse(0);
                double dMinZ = discElems.stream().mapToDouble(ExtractionElement::minZ).min().orElse(0);
                double dMaxZ = discElems.stream().mapToDouble(ExtractionElement::maxZ).max().orElse(0);

                double discW = (dMaxX - dMinX) * 1000;
                double discD = (dMaxY - dMinY) * 1000;
                double discH = (dMaxZ - dMinZ) * 1000;

                ProductRegistrar.ensureAssemblyStub(bomConn, discBomId, "SET");
                insertBomHeader(bomConn, discBomId,
                        prefix + " " + storeyName + " " + discCode,
                        "SET", discCode,
                        null, null,
                        discW, discD, discH,
                        discCode);

                // MAKE child: FLOOR → DISCIPLINE (zero offset — logical grouping)
                insertBomLine(bomConn, floorBomId, discBomId, "MAKE",
                        discCode, discSeq, "0",
                        0, 0, 0,
                        0, 0, 0,
                        null, null, 0, null, null, null);
                discSeq += 10;

                // ── F-2: Factored LEAF writes under discipline sub-BOM ────
                // Group by child_product_id → VerbDetector cascade → recipe lines.
                // Verb pattern (BIM_COBOL verb) computes instance positions at
                // compile time. Unmatched groups fall through to per-instance writes.
                Map<String, List<ExtractionElement>> byProduct = new LinkedHashMap<>();
                for (ExtractionElement e : discElems) {
                    byProduct.computeIfAbsent(e.mProductId(), k -> new ArrayList<>()).add(e);
                }

                int leafSeq = 10;
                for (Map.Entry<String, List<ExtractionElement>> prodEntry : byProduct.entrySet()) {
                    String productId = prodEntry.getKey();
                    List<ExtractionElement> group = prodEntry.getValue();
                    ExtractionElement first = group.get(0);

                    // Attempt verb detection (BIM_COBOL verb pattern)
                    String verbRef = VerbDetector.detect(group, fMinX, fMinY, fMinZ);

                    if (verbRef != null) {
                        // ── Factored recipe line: verb_ref + qty=N, origin = group minimum ──
                        double gMinX = group.stream().mapToDouble(ExtractionElement::centroidX).min().orElse(fMinX);
                        double gMinY = group.stream().mapToDouble(ExtractionElement::centroidY).min().orElse(fMinY);
                        double gMinZ = group.stream().mapToDouble(ExtractionElement::centroidZ).min().orElse(fMinZ);

                        double dx = gMinX - fMinX;
                        double dy = gMinY - fMinY;
                        double dz = gMinZ - fMinZ;

                        String rotationRule = first.orientation() != null ? first.orientation() : "0";

                        insertBomLine(bomConn, discBomId, productId, "LEAF",
                                first.ifcClass(), leafSeq, rotationRule,
                                dx, dy, dz,
                                first.widthMm(), first.depthMm(), first.heightMm(),
                                first.storey(), first.elementRef(), first.ordinal(),
                                first.orientation(),
                                first.materialName(), first.materialRgba(),
                                group.size(), verbRef);
                        leafSeq += 10;
                        totalLines++;
                    } else {
                        // ── Unfactored: one line per element (SH/DX small groups) ──
                        for (ExtractionElement e : group) {
                            double dx = e.centroidX() - fMinX;
                            double dy = e.centroidY() - fMinY;
                            double dz = e.centroidZ() - fMinZ;

                            String rotationRule = e.orientation() != null ? e.orientation() : "0";

                            insertBomLine(bomConn, discBomId, e.mProductId(), "LEAF",
                                    e.ifcClass(), leafSeq, rotationRule,
                                    dx, dy, dz,
                                    e.widthMm(), e.depthMm(), e.heightMm(),
                                    e.storey(), e.elementRef(), e.ordinal(), e.orientation(),
                                    e.materialName(), e.materialRgba());
                            leafSeq += 10;
                            totalLines++;
                        }
                    }
                }
            }

            // ── MAKE child: BUILDING → FLOOR ──────────────────────────────
            double makeDx = fMinX - allMinX;
            double makeDy = fMinY - allMinY;
            double makeDz = fMinZ - allMinZ;

            insertBomLine(bomConn, buildingBomId, floorBomId, "MAKE",
                    storeyInfo.role(), storeyInfo.seq(), "0",
                    makeDx, makeDy, makeDz,
                    0, 0, 0,
                    null, null, 0, null, null, null);
        }

        return new BuildResult(totalLines, aabbW, aabbD, aabbH, floorBomIds);
    }

    // ── SQL helpers (same pattern as StructuralBomBuilder) ────────────────

    private static void insertBomHeader(Connection conn, String bomId, String bomName,
                                        String bomType, String groupBy,
                                        String docSubType, String docBaseType,
                                        double aabbW, double aabbD, double aabbH,
                                        String bomCategory)
            throws SQLException {
        String sql = """
                INSERT OR REPLACE INTO m_bom
                (bom_id, bom_name, bom_type, group_by, entity_type,
                 doc_sub_type, doc_base_type, bom_category,
                 aabb_width_mm, aabb_depth_mm, aabb_height_mm,
                 origin_x, origin_y, origin_z, is_active)
                VALUES (?, ?, ?, ?, 'D', ?, ?, ?,
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
            stmt.setString(7, bomCategory);
            stmt.setDouble(8, aabbW);
            stmt.setDouble(9, aabbD);
            stmt.setDouble(10, aabbH);
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
                storey, elementRef, ordinal, orientation, materialName, materialRgba, 1, null);
    }

    private static void insertBomLine(Connection conn,
                                      String bomId, String childProductId, String componentType,
                                      String role, int sequence, String rotationRule,
                                      double dx, double dy, double dz,
                                      double allocW, double allocD, double allocH,
                                      String storey, String elementRef, int ordinal,
                                      String orientation,
                                      String materialName, String materialRgba,
                                      int qty, String verbRef)
            throws SQLException {
        String sql = """
                INSERT INTO m_bom_line
                (bom_id, child_product_id, component_type, role, sequence,
                 rotation_rule, fit_priority, min_space_mm,
                 dx, dy, dz, is_active, entity_type, qty, verb_ref,
                 allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                 storey, element_ref, ordinal, orientation,
                 material_name, material_rgba)
                VALUES (?, ?, ?, ?, ?,
                        ?, 20, 0,
                        ?, ?, ?, 1, 'D', ?, ?,
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
            stmt.setString(11, verbRef);
            stmt.setDouble(12, allocW);
            stmt.setDouble(13, allocD);
            stmt.setDouble(14, allocH);
            stmt.setString(15, storey);
            stmt.setString(16, elementRef);
            stmt.setInt(17, ordinal);
            stmt.setString(18, orientation);
            stmt.setString(19, materialName);
            stmt.setString(20, materialRgba);
            stmt.executeUpdate();
        }
    }
}
