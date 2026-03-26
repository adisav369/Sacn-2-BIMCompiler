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
 *   <li>BUILDING BOM — whole-building AABB, m_product_category_id=CO</li>
 *   <li>FLOOR BOMs — one per storey (7 for TE)</li>
 *   <li>DISCIPLINE SET BOMs — one per discipline per floor (e.g., TE_GF_ARC)</li>
 *   <li>LEAF lines — factored recipe lines with verb_ref + qty</li>
 * </ul>
 *
 * <p>Discipline assignment is authoritative from I_Element_Extraction.discipline
 * (populated from federated model metadata). The YAML disciplines section
 * declares what disciplines exist; it does NOT assign elements.
 *
 * <p>Offset chain (R16): BUILDING_origin + TACK_dx + 0 + LEAF_dx = element LBD.
 * Only BUILDING carries world origin; FLOOR/DISCIPLINE origins are (0,0,0).
 * DISCIPLINE is a logical grouping with zero spatial offset.
 * All dx/dy/dz are LBD-to-LBD (BOMBasedCompilation.md §4). Centroid =
 * LBD + (width/2, depth/2, height/2) — recovered at compile time.
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
                aabbW, aabbD, aabbH, null,
                allMinX, allMinY, allMinZ);

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
                    storeyInfo.productCategory(),
                    0, 0, 0);  // R16: child origin = 0; offset lives in MAKE line dx

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
                        discCode,
                        0, 0, 0);  // R16: child origin = 0; logical grouping

                // MAKE child: FLOOR → DISCIPLINE (zero offset — logical grouping)
                insertBomLine(bomConn, floorBomId, discBomId, "MAKE",
                        discCode, discSeq, "0",
                        0, 0, 0,
                        0, 0, 0,
                        null, null, 0, null, null, null);
                discSeq += 10;

                // ── F-2: Factored LEAF writes under discipline sub-BOM ────
                // FACTORIZE-v2: delegated to VerbFactorizer (reusable across all builders).
                // Group by product → VerbDetector cascade → factored or unfactored.
                VerbFactorizer.FactorResult fr = VerbFactorizer.factorize(
                        bomConn, discBomId, discElems, fMinX, fMinY, fMinZ, 10, true);
                totalLines += fr.linesWritten();
                if (fr.verbMatched() > 0 || fr.unfactored() > 0) {
                    System.out.printf("  [verb] %s/%s: %d verb patterns (%d instances), %d unfactored%n",
                            storeyName, discCode, fr.verbMatched(), fr.verbInstances(), fr.unfactored());
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
                                        String productCategory,
                                        double originX, double originY, double originZ)
            throws SQLException {
        String sql = """
                INSERT OR REPLACE INTO m_bom
                (bom_id, Value, bom_name, bom_type, group_by, entity_type,
                 doc_sub_type, m_product_category_id,
                 aabb_width_mm, aabb_depth_mm, aabb_height_mm,
                 origin_x, origin_y, origin_z, is_active)
                VALUES (?, ?, ?, ?, ?, 'D', ?, ?,
                        ?, ?, ?,
                        ?, ?, ?, 1)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bomId);
            stmt.setString(2, bomId);  // Value = bom_id
            stmt.setString(3, bomName);
            stmt.setString(4, bomType);
            stmt.setString(5, groupBy);
            stmt.setString(6, docSubType);
            stmt.setString(7, productCategory);
            stmt.setDouble(8, aabbW);
            stmt.setDouble(9, aabbD);
            stmt.setDouble(10, aabbH);
            stmt.setDouble(11, originX);
            stmt.setDouble(12, originY);
            stmt.setDouble(13, originZ);
            stmt.executeUpdate();
        }
    }

    /**
     * Insert a MAKE BOM line (no qty/verb_ref — structural hierarchy only).
     */
    private static void insertBomLine(Connection conn,
                                      String bomId, String childProductId, String componentType,
                                      String role, int sequence, String rotationRule,
                                      double dx, double dy, double dz,
                                      double allocW, double allocD, double allocH,
                                      String storey, String elementRef, int ordinal,
                                      String orientation,
                                      String materialName, String materialRgba)
            throws SQLException {
        // CP-4 §4a: compute geometric classification from allocated dimensions
        String archetype = VerbFactorizer.classifyArchetype(allocW, allocD, allocH);
        String scaleBand = VerbFactorizer.classifyScaleBand(allocW, allocD, allocH);

        String sql = """
                INSERT INTO m_bom_line
                (bom_id, M_BOM_ID, child_product_id, component_type, role, sequence,
                 rotation_rule, fit_priority, min_space_mm,
                 dx, dy, dz, is_active, entity_type, qty,
                 allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                 storey, element_ref, ordinal, orientation,
                 material_name, material_rgba,
                 shape_archetype, scale_band,
                 host_element_ref)
                VALUES (?, (SELECT M_BOM_ID FROM m_bom WHERE Value = ?), ?, ?, ?, ?,
                        ?, 20, 0,
                        ?, ?, ?, 1, 'D', 1,
                        ?, ?, ?,
                        ?, ?, ?, ?,
                        ?, ?,
                        ?, ?,
                        ?)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bomId);
            stmt.setString(2, bomId);  // M_BOM_ID subquery
            stmt.setString(3, childProductId);
            stmt.setString(4, componentType);
            stmt.setString(5, role);
            stmt.setInt(6, sequence);
            stmt.setString(7, rotationRule);
            stmt.setDouble(8, dx);
            stmt.setDouble(9, dy);
            stmt.setDouble(10, dz);
            stmt.setDouble(11, allocW);
            stmt.setDouble(12, allocD);
            stmt.setDouble(13, allocH);
            stmt.setString(14, storey);
            stmt.setString(15, elementRef);
            stmt.setInt(16, ordinal);
            stmt.setString(17, orientation);
            stmt.setString(18, materialName);
            stmt.setString(19, materialRgba);
            stmt.setString(20, archetype);
            stmt.setString(21, scaleBand);
            stmt.setString(22, null);  // MAKE lines have no host_element_ref
            stmt.executeUpdate();
        }
    }
}
