package com.bim.ifctobom;

import com.bim.eyes.shape.ShapeClassifier;
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
                        group.size(), verbRef, first.hostElementRef());

                // CP-1: MA (Material Allocation) — per-instance IFC GUIDs
                if (writeMaRows) {
                    int[] expansionOrder = VerbDetector.computeExpansionOrder(
                            verbRef, group, parentMinX, parentMinY, parentMinZ);
                    insertMaRows(conn, parentBomId, leafSeq, group, expansionOrder);
                }

                // ASI: Per-instance dimension variants (BBC.md §3.5.1)
                // Nominal = first element dims (on BOM line). Variants = ASI rows.
                if (group.size() > 1) {
                    writeASI(conn, parentBomId, leafSeq, group,
                            first.widthMm(), first.depthMm(), first.heightMm());
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
                            1, null, e.hostElementRef());

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

    // ── Shape classification (CP-4 §4a) ─────────────────────────────────────

    /**
     * Classify shape archetype from AABB dimensions.
     * Delegates to canonical {@link ShapeClassifier} — single source of truth.
     * // Implementing BBC.md §2.2.1 — Witness: W-ARCHETYPE-BOM
     */
    static String classifyArchetype(double widthMm, double depthMm, double heightMm) {
        return ShapeClassifier.classifyArchetype(widthMm, depthMm, heightMm).name();
    }

    /**
     * Classify scale band from AABB dimensions.
     * Delegates to canonical {@link ShapeClassifier} — single source of truth.
     */
    static String classifyScaleBand(double widthMm, double depthMm, double heightMm) {
        return ShapeClassifier.classifyScaleBand(widthMm, depthMm, heightMm).name();
    }

    // ── SQL helpers ──────────────────────────────────────────────────────────

    /**
     * Insert a LEAF BOM line with qty, verb_ref, shape_archetype, and scale_band.
     * Canonical insert — used by all BOM builders via factorize().
     *
     * <p>CP-4 §4a: shape_archetype and scale_band are computed from allocated
     * dimensions at insert time. Every LEAF row carries its geometric identity.
     */
    private static void insertLeafLine(Connection conn,
                                        String bomId, String childProductId, String role,
                                        int sequence, String rotationRule,
                                        double dx, double dy, double dz,
                                        double allocW, double allocD, double allocH,
                                        String storey, String elementRef, int ordinal,
                                        String orientation,
                                        String materialName, String materialRgba,
                                        int qty, String verbRef,
                                        String hostElementRef)
            throws SQLException {
        // CP-4 §4a: compute geometric classification from allocated dimensions
        String archetype = classifyArchetype(allocW, allocD, allocH);
        String scaleBand = classifyScaleBand(allocW, allocD, allocH);

        String sql = """
                INSERT INTO m_bom_line
                (bom_id, M_BOM_ID, child_product_id, component_type, role, sequence,
                 rotation_rule, fit_priority, min_space_mm,
                 dx, dy, dz, is_active, entity_type, qty, verb_ref,
                 allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                 storey, element_ref, ordinal, orientation,
                 material_name, material_rgba,
                 shape_archetype, scale_band,
                 host_element_ref)
                VALUES (?, (SELECT M_BOM_ID FROM m_bom WHERE Value = ?), ?, 'LEAF', ?, ?,
                        ?, 20, 0,
                        ?, ?, ?, 1, 'D', ?, ?,
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
            stmt.setString(21, archetype);
            stmt.setString(22, scaleBand);
            stmt.setString(23, hostElementRef);
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
                "INSERT OR IGNORE INTO m_bom_line_ma (bom_id, M_BOM_ID, sequence, qi, guid) VALUES (?, (SELECT M_BOM_ID FROM m_bom WHERE Value = ?), ?, ?, ?)")) {
            stmt.setString(1, bomId);
            stmt.setString(2, bomId);  // M_BOM_ID subquery
            stmt.setInt(3, sequence);
            stmt.setInt(4, qi);
            stmt.setString(5, guid);
            stmt.executeUpdate();
        }
    }

    // ── ASI: Per-Instance Attribute Set (BBC.md §3.5.1) ──────────────

    /**
     * Seed M_AttributeSet taxonomy for a factored group.
     *
     * <p>Extraction confirms the taxonomy — which attributes are instance-varying
     * (IsInstanceAttribute=1) vs fixed per product type. Like discovering that
     * T-shirts come in sizes S/M/L/XL — the taxonomy is "size varies", not the
     * specific sizes themselves.
     *
     * <p>The actual ASI values are filled by the designer (generative path) or
     * carried in CLUSTER verb_ref dims (extraction path). Extraction doesn't write
     * M_AttributeSetInstance rows — it only confirms the M_AttributeSet definition.
     *
     * <p>Reports dimension variance as diagnostic for pattern promotion analysis.
     */
    static void writeASI(Connection conn, String bomId, int sequence,
                         List<ExtractionElement> elements,
                         double nomW, double nomD, double nomH) throws SQLException {
        // Ensure ASI taxonomy tables exist and are seeded (idempotent)
        ensureASITables(conn);

        String attrSetId = resolveAttributeSet(elements.get(0).ifcClass());
        // IsInstanceAttribute=0 → no per-instance variation expected
        if ("BIM_Component".equals(attrSetId) || "BIM_Fitting".equals(attrSetId)) return;

        // Count how many instances differ from nominal — diagnostic only
        int variantCount = 0;
        for (ExtractionElement e : elements) {
            if (Math.abs(e.widthMm() - nomW) > 1.0
                    || Math.abs(e.depthMm() - nomD) > 1.0
                    || Math.abs(e.heightMm() - nomH) > 1.0) {
                variantCount++;
            }
        }

        if (variantCount > 0) {
            System.out.printf("  [ASI] %s seq=%d: %d/%d instances have dimension variants (%s)%n",
                bomId, sequence, variantCount, elements.size(), attrSetId);
        }
    }

    /** Resolve M_AttributeSet_ID from IFC class. */
    static String resolveAttributeSet(String ifcClass) {
        if (ifcClass == null) return "BIM_Component";
        return switch (ifcClass) {
            case "IfcPipeSegment", "IfcPipeFitting" -> "BIM_Pipe";
            case "IfcCableSegment", "IfcCableCarrierSegment" -> "BIM_Conduit";
            case "IfcDuctSegment", "IfcDuctFitting" -> "BIM_Duct";
            case "IfcWall", "IfcWallStandardCase", "IfcCurtainWall" -> "BIM_Wall";
            case "IfcSlab", "IfcRoof", "IfcPlate" -> "BIM_Slab";
            case "IfcBeam" -> "BIM_Beam";
            case "IfcColumn" -> "BIM_Column";
            default -> "BIM_Component";
        };
    }

    /** Compute median of an array. */
    static double median(double[] arr) {
        double[] sorted = arr.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static void ensureASITables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS M_AttributeSet (
                    M_AttributeSet_ID TEXT PRIMARY KEY,
                    Name TEXT NOT NULL,
                    IsInstanceAttribute INTEGER NOT NULL DEFAULT 0,
                    Description TEXT
                )""");
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS M_AttributeSetInstance (
                    M_AttributeSetInstance_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    M_AttributeSet_ID TEXT REFERENCES M_AttributeSet(M_AttributeSet_ID),
                    Description TEXT
                )""");
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS M_AttributeInstance (
                    M_AttributeInstance_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    M_AttributeSetInstance_ID INTEGER NOT NULL
                        REFERENCES M_AttributeSetInstance(M_AttributeSetInstance_ID),
                    Name TEXT NOT NULL,
                    Value TEXT NOT NULL,
                    ValueType TEXT NOT NULL DEFAULT 'NUM',
                    UNIQUE(M_AttributeSetInstance_ID, Name)
                )""");
            // Seed attribute sets
            stmt.executeUpdate("""
                INSERT OR IGNORE INTO M_AttributeSet VALUES
                    ('BIM_Pipe','Pipe',1,'Cross-section product, length/angle vary'),
                    ('BIM_Conduit','Conduit',1,'Length varies'),
                    ('BIM_Duct','Duct',1,'Length/cross-section vary'),
                    ('BIM_Wall','Wall',1,'Length/height vary'),
                    ('BIM_Slab','Slab',1,'Area varies'),
                    ('BIM_Beam','Beam',1,'Span varies'),
                    ('BIM_Column','Column',1,'Height varies'),
                    ('BIM_Component','Component',0,'Identical everywhere'),
                    ('BIM_Fitting','Fitting',0,'Identical per type')
                """);
        }
    }

}
