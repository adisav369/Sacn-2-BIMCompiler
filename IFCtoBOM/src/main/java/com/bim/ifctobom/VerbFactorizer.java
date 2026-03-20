package com.bim.ifctobom;

import com.bim.ifctobom.ExtractionReader.ExtractionElement;

import java.sql.*;
import java.util.*;

/**
 * Reusable verb factorization for BOM builders.
 *
 * <p>Extracts the "group by product → VerbDetector cascade → write factored or
 * unfactored LEAF lines + MA rows" pattern from {@link DisciplineBomBuilder}
 * into a single reusable method callable from all BOM builders.
 *
 * <p>Used by:
 * <ul>
 *   <li>{@link DisciplineBomBuilder} — CO/IN path (discipline sub-BOMs)</li>
 *   <li>{@link StructuralBomBuilder} — RE path (storey-level structural BOMs)</li>
 *   <li>{@link ScopeBomBuilder} — RE path (scope space SET BOMs)</li>
 * </ul>
 *
 * <p><b>Backward compatible:</b> Groups smaller than {@code VerbDetector.MIN_GROUP}
 * (4 elements) fall through to per-instance unfactored writes — identical output
 * to the pre-factorization code path. SH (55), FK (82), DX (1099) are unaffected.
 *
 * <p><b>Guard:</b> {@code BomValidator.checkExtractionReconciliation} uses
 * {@code SUM(qty)} to verify total instances match extraction count.
 *
 * @since FACTORIZE-v2 (2026-03-20) — extracted from DisciplineBomBuilder §F-2
 * @see VerbDetector
 */
public class VerbFactorizer {

    /**
     * Result of factorizing a list of elements under a parent BOM.
     */
    public record FactorResult(
            /** Total BOM lines written (factored + unfactored). */
            int linesWritten,
            /** Number of product groups that matched a verb pattern. */
            int verbMatched,
            /** Total element instances covered by verb patterns. */
            int verbInstances,
            /** Total element instances written as unfactored per-instance lines. */
            int unfactored
    ) {}

    /**
     * Group elements by product, run VerbDetector cascade, write factored or
     * unfactored LEAF lines + MA rows under the given parent BOM.
     *
     * <p>This is the canonical factorization entry point. The parent BOM can be
     * a FLOOR STR BOM, a SET BOM, or a DISCIPLINE sub-BOM — the factorization
     * logic is identical regardless of hierarchy level.
     *
     * @param conn        writable BOM DB connection
     * @param parentBomId BOM to write LEAF lines into
     * @param elements    elements to factorize (already filtered for this parent)
     * @param parentMinX  parent AABB min X (for LBD offset computation)
     * @param parentMinY  parent AABB min Y
     * @param parentMinZ  parent AABB min Z
     * @param startSeq    starting sequence number for BOM lines (typically 10)
     * @return factorization result with counts
     */
    /**
     * Factorize with MA (Material Allocation) rows for identity-based SpatialDiff.
     * Used by CO buildings (TE) where the compiler's geometry resolution supports
     * GUID-based element_refs. RE buildings should use {@code factorize()} without
     * MA rows — the RE compilation path uses positional matching.
     *
     * @param writeMaRows  if true, write MA rows mapping verb expansion qi → IFC GUID.
     *                     Set false for RE buildings where element_ref must remain
     *                     as product name for geometry lookup.
     */
    public static FactorResult factorize(Connection conn, String parentBomId,
                                          List<ExtractionElement> elements,
                                          double parentMinX, double parentMinY, double parentMinZ,
                                          int startSeq, boolean writeMaRows) throws SQLException {
        return doFactorize(conn, parentBomId, elements, parentMinX, parentMinY, parentMinZ,
                startSeq, writeMaRows);
    }

    /**
     * Factorize without MA rows (default for RE buildings).
     * Equivalent to {@code factorize(conn, parentBomId, elements, ..., false)}.
     */
    public static FactorResult factorize(Connection conn, String parentBomId,
                                          List<ExtractionElement> elements,
                                          double parentMinX, double parentMinY, double parentMinZ,
                                          int startSeq) throws SQLException {
        return doFactorize(conn, parentBomId, elements, parentMinX, parentMinY, parentMinZ,
                startSeq, false);
    }

    private static FactorResult doFactorize(Connection conn, String parentBomId,
                                          List<ExtractionElement> elements,
                                          double parentMinX, double parentMinY, double parentMinZ,
                                          int startSeq, boolean writeMaRows) throws SQLException {
        // Group by child_product_id
        Map<String, List<ExtractionElement>> byProduct = new LinkedHashMap<>();
        for (ExtractionElement e : elements) {
            byProduct.computeIfAbsent(e.mProductId(), k -> new ArrayList<>()).add(e);
        }

        int leafSeq = startSeq;
        int verbMatched = 0, verbInstances = 0, unfactored = 0, linesWritten = 0;

        for (Map.Entry<String, List<ExtractionElement>> prodEntry : byProduct.entrySet()) {
            List<ExtractionElement> group = prodEntry.getValue();
            ExtractionElement first = group.get(0);

            // Material uniformity guard: factored lines store only the first
            // element's material_rgba. If elements in the group have mixed materials
            // (including NULL vs non-NULL), factorization would lose provenance.
            // LAST_MILE checklist #1: "same position, size, and material."
            // Reject non-uniform groups → they fall through to unfactored.
            String verbRef = null;
            boolean materialUniform = true;
            if (group.size() >= 4) {  // only worth checking for groups that could factorize
                String firstMat = first.materialRgba();
                for (int mi = 1; mi < group.size(); mi++) {
                    String mat = group.get(mi).materialRgba();
                    if (!java.util.Objects.equals(firstMat, mat)) {
                        materialUniform = false;
                        break;
                    }
                }
            }
            if (materialUniform) {
                verbRef = VerbDetector.detect(group, parentMinX, parentMinY, parentMinZ);
            }

            if (verbRef != null) {
                verbMatched++;
                verbInstances += group.size();

                // Factored recipe line: verb_ref + qty=N, origin = group LBD minimum
                double gMinX = group.stream().mapToDouble(ExtractionElement::minX).min().orElse(parentMinX);
                double gMinY = group.stream().mapToDouble(ExtractionElement::minY).min().orElse(parentMinY);
                double gMinZ = group.stream().mapToDouble(ExtractionElement::minZ).min().orElse(parentMinZ);

                double dx = gMinX - parentMinX;
                double dy = gMinY - parentMinY;
                double dz = gMinZ - parentMinZ;

                String rotationRule = first.orientation() != null ? first.orientation() : "0";

                insertLeafLine(conn, parentBomId, first.mProductId(), first.ifcClass(),
                        leafSeq, rotationRule, dx, dy, dz,
                        first.widthMm(), first.depthMm(), first.heightMm(),
                        first.storey(), first.elementRef(), first.ordinal(),
                        first.orientation(), first.materialName(), first.materialRgba(),
                        group.size(), verbRef);

                // CP-1: MA (Material Allocation) — per-instance IFC GUIDs
                if (writeMaRows) {
                    int[] expansionOrder = VerbDetector.computeExpansionOrder(
                            verbRef, group, parentMinX, parentMinY, parentMinZ);
                    insertMaRows(conn, parentBomId, leafSeq, group, expansionOrder);
                }

                leafSeq += 10;
                linesWritten++;
            } else {
                // Unfactored: one line per element (small groups or non-uniform)
                unfactored += group.size();
                for (ExtractionElement e : group) {
                    double dx = e.minX() - parentMinX;
                    double dy = e.minY() - parentMinY;
                    double dz = e.minZ() - parentMinZ;

                    String rotationRule = e.orientation() != null ? e.orientation() : "0";
                    // CP-1: For CO buildings (writeMaRows=true), use IFC GUID as
                    // element_ref for identity-based SpatialDiff matching.
                    // For RE buildings, element_ref stays as product name (geometry lookup key).
                    String elemRef;
                    if (writeMaRows && e.guid() != null && !e.guid().isEmpty()) {
                        elemRef = e.guid();
                    } else {
                        elemRef = e.elementRef();
                    }

                    insertLeafLine(conn, parentBomId, e.mProductId(), e.ifcClass(),
                            leafSeq, rotationRule, dx, dy, dz,
                            e.widthMm(), e.depthMm(), e.heightMm(),
                            e.storey(), elemRef, e.ordinal(),
                            e.orientation(), e.materialName(), e.materialRgba(),
                            1, null);

                    // CP-1: MA for unfactored lines (qi=0, single instance).
                    // Required for CO buildings where SpatialDiff uses GUID-based matching.
                    if (writeMaRows && e.guid() != null && !e.guid().isEmpty()) {
                        insertMaRow(conn, parentBomId, leafSeq, 0, e.guid());
                    }

                    leafSeq += 10;
                    linesWritten++;
                }
            }
        }

        return new FactorResult(linesWritten, verbMatched, verbInstances, unfactored);
    }

    // ── SQL helpers ──────────────────────────────────────────────────────────

    /**
     * Insert a LEAF BOM line with qty and verb_ref support.
     * Canonical insert — used by all BOM builders via factorize().
     */
    private static void insertLeafLine(Connection conn,
                                        String bomId, String childProductId, String role,
                                        int sequence, String rotationRule,
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
                VALUES (?, ?, 'LEAF', ?, ?,
                        ?, 20, 0,
                        ?, ?, ?, 1, 'D', ?, ?,
                        ?, ?, ?,
                        ?, ?, ?, ?,
                        ?, ?)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bomId);
            stmt.setString(2, childProductId);
            stmt.setString(3, role);
            stmt.setInt(4, sequence);
            stmt.setString(5, rotationRule);
            stmt.setDouble(6, dx);
            stmt.setDouble(7, dy);
            stmt.setDouble(8, dz);
            stmt.setInt(9, qty);
            stmt.setString(10, verbRef);
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

    // ── CP-1: Material Allocation (iDempiere M_InOutLineMA pattern) ──────

    /**
     * Write MA rows for a factored BOM line (qty > 1).
     * Maps each element to its verb-expansion qi using the sort order from VerbDetector.
     */
    private static void insertMaRows(Connection conn, String bomId, int sequence,
                                      List<ExtractionElement> elements,
                                      int[] expansionOrder) throws SQLException {
        for (int i = 0; i < elements.size(); i++) {
            String guid = elements.get(i).guid();
            if (guid != null && !guid.isEmpty()) {
                insertMaRow(conn, bomId, sequence, expansionOrder[i], guid);
            }
        }
    }

    /**
     * Write a single MA row.
     */
    private static void insertMaRow(Connection conn, String bomId, int sequence,
                                     int qi, String guid) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR IGNORE INTO m_bom_line_ma (bom_id, sequence, qi, guid) VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, bomId);
            stmt.setInt(2, sequence);
            stmt.setInt(3, qi);
            stmt.setString(4, guid);
            stmt.executeUpdate();
        }
    }
}
