package com.bim.ifctobom;

import com.bim.eyes.ProductCategory;
import com.bim.eyes.shape.Fingerprint;
import com.bim.eyes.shape.ShapeArchetype;
import com.bim.eyes.shape.ShapeClassifier;
import com.bim.eyes.shape.ScaleBand;
import com.bim.ifctobom.ExtractionReader.ExtractionElement;
import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.*;

/**
 * FL-5: Shape-aware advisories from BIMEyes geometric comprehension.
 *
 * <p>For each extracted element, computes shape archetype from AABB dimensions
 * and checks consistency with the declared IFC class. Writes mismatches to
 * W_Validation_Advisory with layer='SHAPE'.
 *
 * <p>Examples of flagged mismatches:
 * <ul>
 *   <li>"This IfcWall is ELONGATED — shaped like a beam, not a wall"</li>
 *   <li>"This IfcColumn is PLANAR — shaped like a wall, not a column"</li>
 *   <li>"This element is classified ARC but its geometry is MEP_ROUTING"</li>
 * </ul>
 *
 * // Implementing ACTION_ROADMAP.md §FL-5 — Witness: W-FL-SHAPE-1, W-FL-SHAPE-2
 */
public class ShapeAdvisoryWriter {

    private static final String TAG = "ShapeAdvisory";

    private ShapeAdvisoryWriter() {}

    /**
     * Analyze elements for shape-class mismatches and write advisories.
     *
     * @param discConn     connection to disc_validation.db (with W_Validation_Advisory)
     * @param elements     all extracted elements for this building
     * @param buildingType building type prefix (e.g. "SH", "DX")
     * @return report with mismatch counts
     */
    public static Report analyze(Connection discConn,
                                  List<ExtractionElement> elements,
                                  String buildingType) throws SQLException {
        // Clear previous SHAPE advisories for this building (idempotent re-run)
        try (PreparedStatement del = discConn.prepareStatement(
                "DELETE FROM W_Validation_Advisory WHERE building_type = ? AND layer = 'SHAPE'")) {
            del.setString(1, buildingType);
            del.executeUpdate();
        }

        List<Mismatch> mismatches = new ArrayList<>();
        int checked = 0;

        for (ExtractionElement e : elements) {
            double w = e.widthMm(), d = e.depthMm(), h = e.heightMm();
            if (w <= 0 || d <= 0 || h <= 0) continue;
            checked++;

            // Build a lightweight Fingerprint for class consistency check
            double[] dims = {w, d, h};
            Arrays.sort(dims);
            double S = dims[0], M = dims[1], L = dims[2];
            double planarity = L > 0.01 ? S / L : 0;
            double elongation = L > 0.01 ? M / L : 0;
            double squareness = M > 0.01 ? S / M : 0;
            double volumeM3 = (w * d * h) / 1e9;
            ShapeArchetype archetype = ShapeClassifier.classifyArchetype(w, d, h);
            ScaleBand scaleBand = ShapeClassifier.classifyScaleBand(w, d, h);

            Fingerprint fp = new Fingerprint(
                    e.guid() != null ? e.guid() : e.elementRef(),
                    e.ifcClass(), e.elementRef(),
                    S, M, L, planarity, elongation, squareness,
                    volumeM3, 0, archetype, scaleBand);

            // Check 1: IFC class vs shape archetype consistency
            String violation = fp.verifyClassConsistency();
            if (violation != null) {
                mismatches.add(new Mismatch(e.elementRef(), e.ifcClass(),
                        "CLASS_SHAPE", archetype.name(), violation));
            }

            // Check 2: Discipline mismatch — geometry suggests different domain
            String expectedCategory = ProductCategory.resolve(e.ifcClass());
            if (expectedCategory != null) {
                String expectedDiscipline = ProductCategory.deriveDiscipline(expectedCategory);
                String shapeDiscipline = inferDisciplineFromShape(archetype, scaleBand);
                if (shapeDiscipline != null && !shapeDiscipline.equals(expectedDiscipline)) {
                    mismatches.add(new Mismatch(e.elementRef(), e.ifcClass(),
                            "DISCIPLINE_SHAPE",
                            archetype.name() + "/" + scaleBand.name(),
                            String.format("%s classified as %s (%s) but geometry suggests %s " +
                                    "(archetype=%s, scale=%s, %.0f×%.0f×%.0fmm)",
                                    e.ifcClass(), expectedCategory, expectedDiscipline,
                                    shapeDiscipline, archetype, scaleBand, w, d, h)));
                }
            }
        }

        // Write advisories
        if (!mismatches.isEmpty()) {
            writeAdvisories(discConn, buildingType, mismatches);
        }

        BIMLogger.info(TAG, "{}: {}/{} elements checked, {} shape mismatches",
                buildingType, checked, elements.size(), mismatches.size());

        return new Report(buildingType, elements.size(), checked, mismatches);
    }

    /**
     * Infer discipline from shape alone — returns null if ambiguous.
     * Only returns a definitive answer for clear-cut cases.
     */
    private static String inferDisciplineFromShape(ShapeArchetype archetype, ScaleBand band) {
        // ELONGATED + FITTING = almost certainly MEP (pipe/conduit)
        if (archetype == ShapeArchetype.ELONGATED && band == ScaleBand.FITTING) return "MEP";
        // COMPACT + FITTING = MEP terminal (outlets, sensors)
        if (archetype == ShapeArchetype.COMPACT && band == ScaleBand.FITTING) return "MEP";
        // COMPACT + TINY = MEP terminal
        if (archetype == ShapeArchetype.COMPACT && band == ScaleBand.TINY) return "MEP";
        // PLANAR + ARCHITECTURAL = structural (walls, slabs)
        if (archetype == ShapeArchetype.PLANAR && band == ScaleBand.ARCHITECTURAL) return "STR";
        // Too ambiguous for other combinations
        return null;
    }

    private static void writeAdvisories(Connection discConn, String buildingType,
                                         List<Mismatch> mismatches) throws SQLException {
        String insert = """
                INSERT INTO W_Validation_Advisory
                (building_type, element_ref, layer, severity, rule_name, message, suggestion)
                VALUES (?, ?, 'SHAPE', ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = discConn.prepareStatement(insert)) {
            for (Mismatch m : mismatches) {
                // SUGGESTION severity — shape mismatches are advisory, not blocking
                ps.setString(1, buildingType);
                ps.setString(2, m.elementRef());
                ps.setString(3, "SUGGESTION");
                ps.setString(4, "SHAPE_" + m.checkType() + ":" + m.ifcClass());
                ps.setString(5, m.diagnostic());
                ps.setString(6, suggestCorrection(m));
                ps.addBatch();
            }
            ps.executeBatch();
        }

        BIMLogger.info(TAG, "Wrote {} SHAPE advisories for {}", mismatches.size(), buildingType);
    }

    private static String suggestCorrection(Mismatch m) {
        return switch (m.checkType()) {
            case "CLASS_SHAPE" -> String.format(
                    "Element geometry is %s — verify IFC class %s is correct",
                    m.shapeInfo(), m.ifcClass());
            case "DISCIPLINE_SHAPE" -> String.format(
                    "Geometry suggests different discipline — review %s classification",
                    m.ifcClass());
            default -> null;
        };
    }

    /** One shape mismatch finding. */
    public record Mismatch(
            String elementRef, String ifcClass,
            String checkType, String shapeInfo,
            String diagnostic
    ) {}

    /** Analysis report. */
    public record Report(
            String buildingType, int totalElements, int checked,
            List<Mismatch> mismatches
    ) {
        public int mismatchCount() { return mismatches.size(); }
        public boolean hasMismatches() { return !mismatches.isEmpty(); }

        public void print() {
            System.out.printf("[Shape] %s: %d/%d elements checked, %d mismatches%n",
                    buildingType, checked, totalElements, mismatches.size());
            for (int i = 0; i < Math.min(5, mismatches.size()); i++) {
                Mismatch m = mismatches.get(i);
                System.out.printf("[Shape]   %s %s: %s%n",
                        m.elementRef, m.ifcClass, m.diagnostic);
            }
            if (mismatches.size() > 5) {
                System.out.printf("[Shape]   ... and %d more%n", mismatches.size() - 5);
            }
        }
    }
}
